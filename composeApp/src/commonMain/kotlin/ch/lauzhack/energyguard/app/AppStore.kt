package ch.lauzhack.energyguard.app

import ch.lauzhack.energyguard.coach.CoachCheckIn
import ch.lauzhack.energyguard.coach.CoachMessage
import ch.lauzhack.energyguard.engine.BaselineSummary
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.model.SleepPrior
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.ui.DashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Single state holder of the 3-tab app (contract: docs/API.md "App model"). Fed by the shell from the engine, the coach,
 * guard Block events and user actions; exposes [state]. Pure Kotlin, no clock of its own except the injected [clock].
 *
 * Not thread-safe, like FatigueEngine: call everything from one thread (Compose main thread). Ktor threads post to it.
 *
 * @param dayKey local "YYYY-MM-DD" of an epoch ms ([localDayKey] on the platforms).
 * @param demo battery time accelerated ×[EnergyBattery.DEMO_TIME_SCALE] (same model).
 * @param persistHistory false = daily history is neither loaded nor written (demo data is not real data).
 * @param syncSleepPrior keep Settings.sleepHours and the global SleepPrior (header slider) in sync. Off in tests.
 */
class AppStore(
    private val clock: () -> Long,
    private val dayKey: (Long) -> String,
    private val persistence: AppPersistence = InMemoryPersistence(),
    private val demo: Boolean = false,
    private val persistHistory: Boolean = !demo,
    private val syncSleepPrior: Boolean = true,
) {
    private val timeScale = if (demo) EnergyBattery.DEMO_TIME_SCALE else 1.0

    private var settings: Settings
    private var journal: List<JournalEntry>
    private var history: List<DaySummary>
    private var stats: DayStats
    private var battery: Double
    private var minBattery: Double
    private var dashboard = DashboardState()
    private var fatigue = FatigueState.Initial
    private var level = Level.FRESH
    private var profile = Profile()
    private var coach: CoachNote? = null
    private var breakSource: BreakSource? = null
    private var lastTickTs: Long? = null
    private var lastActivityTs: Long
    private var lastSaveTs: Long
    private var concentrationSum = 0L
    private var concentrationSamples = 0
    private var llmTodayBase = 0      // today's LLM calls before this app start (restored) or 0
    private var llmCountAtDayStart = 0
    private var llmCount = 0

    private val _state: MutableStateFlow<AppState>
    val state: StateFlow<AppState>

    init {
        val now = clock()
        val today = dayKey(now)
        settings = persistence.loadSettings().sanitized().let {
            // Sleep hours are about last night: yesterday's value does not apply today.
            if (it.sleepDay == today) it else it.copy(sleepHours = Settings.DEFAULT_SLEEP_HOURS, sleepDay = null)
        }
        if (syncSleepPrior) SleepPrior.hours = settings.sleepHours
        journal = persistence.loadJournal()
        val saved = if (persistHistory) persistence.loadHistory() else emptyList()
        history = saved.filter { it.day < today }.sortedByDescending { it.day }.take(MAX_HISTORY_DAYS)
        val resumed = saved.firstOrNull { it.day == today }
        battery = resumed?.battery?.toDouble() ?: EnergyBattery.startFromSleep(settings.sleepHours)
        minBattery = resumed?.minBattery?.toDouble() ?: battery
        stats = DayStats(
            day = today,
            focusedMs = resumed?.focusedMs ?: 0,
            breaksTaken = resumed?.breaksTaken ?: 0,
            longestFocusMs = resumed?.longestFocusMs ?: 0,
            phishingBlocked = resumed?.phishingBlocked ?: 0,
            framesProcessed = resumed?.framesProcessed ?: 0,
        )
        llmTodayBase = resumed?.llmCalls ?: 0
        lastActivityTs = now
        lastSaveTs = now
        lastTickTs = now
        _state = MutableStateFlow(snapshot())
        state = _state.asStateFlow()
    }

    // ---------------------------------------------------------------- feeds (shell -> store)

    /** Every vision frame (processed locally). A face counts as presence. */
    fun onVision(frame: VisionFrame) {
        stats = stats.copy(framesProcessed = stats.framesProcessed + 1)
        if (frame.face) markActive(frame.ts)
    }

    /** Every input window; [ts] = same clock as the engine. Counts only, never key contents. */
    fun onInput(stats: InputStats, ts: Long) {
        if (EnergyBattery.isActive(stats)) markActive(ts)
    }

    fun onFatigue(state: FatigueState, ts: Long, baseline: BaselineSummary? = null) {
        fatigue = state
        level = state.level
        baseline?.let { profile = Profile(it) }
        publish()
    }

    /** From FrugalEnergyCoach.lastMessage + llmCallCount (cumulative since app start). */
    fun onCoach(message: CoachMessage?, llmCallCount: Int) {
        coach = message?.let(CoachNote::from)
        llmCount = llmCallCount
        publish()
    }

    /** One guard Block verdict (inbox or Chrome extension). */
    fun onGuardBlock() {
        stats = stats.copy(phishingBlocked = stats.phishingBlocked + 1)
        publish()
    }

    /** The snapshot the current dashboard renders (mirrored into AppState.dashboard). */
    fun setDashboard(d: DashboardState) {
        if (d == dashboard) return
        dashboard = d
        publish()
    }

    /** Call about once a second: battery, focus time, auto-break, day change, autosave. */
    fun tick(now: Long = clock()) {
        rollDayIfNeeded(now)
        if (syncSleepPrior) {
            val slider = SleepPrior.hours
            if (slider.isFinite() && slider.coerceIn(Settings.MIN_SLEEP_HOURS, Settings.MAX_SLEEP_HOURS) != settings.sleepHours) {
                setSleepHours(slider)
            }
        }
        val dt = lastTickTs?.let { (now - it).coerceIn(0, EnergyBattery.MAX_STEP_MS) } ?: 0L
        lastTickTs = now

        if (breakSource == null && now - lastActivityTs >= EnergyBattery.IDLE_BREAK_MS) beginBreak(BreakSource.AUTO)
        val onBreak = breakSource != null
        battery = EnergyBattery.step(battery, (dt * timeScale).toLong(), level, onBreak)
        minBattery = min(minBattery, battery)
        if (!onBreak && dt > 0) {
            val current = stats.currentFocusMs + dt
            stats = stats.copy(
                focusedMs = stats.focusedMs + dt,
                currentFocusMs = current,
                longestFocusMs = max(stats.longestFocusMs, current),
            )
        }
        if (fatigue.calibrated) {
            concentrationSum += (100 - fatigue.score).coerceIn(0, 100)
            concentrationSamples++
        }
        if (persistHistory && now - lastSaveTs >= SAVE_EVERY_MS) saveHistory(now)
        publish()
    }

    // ---------------------------------------------------------------- user actions

    /** "I'm on a break" (until [endBreak]). An automatic break becomes a user break, counted once. */
    fun startBreak() {
        if (breakSource == null) beginBreak(BreakSource.USER) else breakSource = BreakSource.USER
        publish()
    }

    /** "I'm back": ends any break and counts as activity (so the auto-break does not fire again right away). */
    fun endBreak() {
        breakSource = null
        lastActivityTs = max(lastActivityTs, clock())
        publish()
    }

    /**
     * Adds a check-in (all optional, out-of-range values dropped). Returns null if nothing valid was given.
     * [concentration] = the value to record with it; null = ours (the phone passes the PC's while paired).
     */
    fun addJournalEntry(
        kss: Int? = null,
        stress: Int? = null,
        caffeine: Int? = null,
        note: String? = null,
        concentration: Int? = null,
    ): JournalEntry? {
        val now = clock()
        val id = max(now, (journal.maxOfOrNull { it.id } ?: Long.MIN_VALUE) + 1)
        val measured = (concentration ?: (100 - fatigue.score)).coerceIn(0, 100)
        val entry = JournalEntry(id, now, kss, stress, caffeine, note, concentration = measured).sanitized()
        if (entry.isEmpty) return null
        journal = (listOf(entry) + journal).take(JournalEntry.MAX_ENTRIES)
        persistence.saveJournal(journal)
        publish()
        return entry
    }

    fun deleteJournalEntry(id: Long) {
        val next = journal.filterNot { it.id == id }
        if (next.size == journal.size) return
        journal = next
        persistence.saveJournal(journal)
        publish()
    }

    /** Today's latest check-in with a KSS, stress or caffeine value, as numbers only (EnergyCoach input). Never the note. */
    fun coachCheckIn(): CoachCheckIn? {
        for (e in journal) {
            if (dayKey(e.ts) != stats.day) return null
            if (e.kss != null || e.stress != null || e.caffeine != null) return CoachCheckIn(e.kss, e.stress, e.caffeine)
        }
        return null
    }

    /** Any settings change; a new sleep value shifts the battery (see [setSleepHours]). */
    fun updateSettings(transform: (Settings) -> Settings) {
        val next = transform(settings).sanitized()
        if (next.sleepHours != settings.sleepHours) {
            setSleepHours(next.sleepHours)
            settings = next.copy(sleepHours = settings.sleepHours, sleepDay = settings.sleepDay)
        } else {
            settings = next
        }
        persistence.saveSettings(settings)
        publish()
    }

    /** Last night's sleep (3..9 h). The battery moves by the difference of the two starting points. */
    fun setSleepHours(hours: Double) {
        if (!hours.isFinite()) return
        val h = hours.coerceIn(Settings.MIN_SLEEP_HOURS, Settings.MAX_SLEEP_HOURS)
        val delta = EnergyBattery.startFromSleep(h) - EnergyBattery.startFromSleep(settings.sleepHours)
        battery = (battery + delta).coerceIn(0.0, 100.0)
        minBattery = min(minBattery + delta, battery).coerceIn(0.0, 100.0)
        settings = settings.copy(sleepHours = h, sleepDay = stats.day)
        if (syncSleepPrior) SleepPrior.hours = h
        persistence.saveSettings(settings)
        publish()
    }

    /** Demo F5: battery and today's counters from zero. The LLM count stays real (it is the frugality proof). */
    fun resetRun() {
        val now = clock()
        battery = EnergyBattery.startFromSleep(settings.sleepHours)
        minBattery = battery
        llmTodayBase = currentStats().llmCalls
        stats = DayStats(day = stats.day)
        llmCountAtDayStart = llmCount
        breakSource = null
        lastActivityTs = now
        lastTickTs = now
        concentrationSum = 0
        concentrationSamples = 0
        publish()
    }

    /** Save today's summary now (app exit). Settings and journal are already saved on every change. */
    fun flush() {
        if (persistHistory) saveHistory(clock())
    }

    // ---------------------------------------------------------------- internals

    private fun markActive(ts: Long) {
        lastActivityTs = max(lastActivityTs, ts)
        if (breakSource == BreakSource.AUTO) {
            breakSource = null
            publish()
        }
    }

    private fun beginBreak(source: BreakSource) {
        breakSource = source
        stats = stats.copy(breaksTaken = stats.breaksTaken + 1, currentFocusMs = 0)
    }

    /** Local day changed while running: archive today, start new counters. The battery continues (no sleep happened). */
    private fun rollDayIfNeeded(now: Long) {
        val today = dayKey(now)
        if (today == stats.day) return
        val summary = summary()
        history = (listOf(summary) + history.filter { it.day != summary.day }).take(MAX_HISTORY_DAYS)
        stats = DayStats(day = today)
        llmTodayBase = 0
        llmCountAtDayStart = llmCount
        minBattery = battery
        concentrationSum = 0
        concentrationSamples = 0
        if (persistHistory) saveHistory(now)
    }

    private fun saveHistory(now: Long) {
        lastSaveTs = now
        persistence.saveHistory(listOf(summary()) + history)
    }

    private fun currentStats(): DayStats {
        val llmToday = llmTodayBase + (llmCount - llmCountAtDayStart).coerceAtLeast(0)
        return stats.copy(llmCalls = llmToday, breaksNeeded = RecoveryScore.breaksNeeded(stats.focusedMs))
    }

    /** Latest entry of today with a KSS or stress value. Journal is newest first: stop at the first older day. */
    private fun todayCheckIn(): JournalEntry? {
        for (e in journal) {
            if (dayKey(e.ts) != stats.day) return null
            if (e.kss != null || e.stress != null) return e
        }
        return null
    }

    private fun recovery(s: DayStats): Int =
        RecoveryScore.compute(settings.sleepHours, s.breaksTaken, s.breaksNeeded, todayCheckIn())

    private fun summary(): DaySummary {
        val s = currentStats()
        return DaySummary(
            day = s.day,
            focusedMs = s.focusedMs,
            breaksTaken = s.breaksTaken,
            longestFocusMs = s.longestFocusMs,
            phishingBlocked = s.phishingBlocked,
            llmCalls = s.llmCalls,
            framesProcessed = s.framesProcessed,
            battery = battery.roundToInt(),
            minBattery = minBattery.roundToInt(),
            recovery = recovery(s),
            sleepHours = settings.sleepHours,
            avgConcentration = if (concentrationSamples > 0) (concentrationSum / concentrationSamples).toInt() else null,
            checkIns = journal.count { dayKey(it.ts) == s.day },
        )
    }

    private fun snapshot(): AppState {
        val s = currentStats()
        return AppState(
            dashboard = dashboard,
            concentration = Concentration.from(fatigue),
            energy = EnergyState(
                battery = battery.roundToInt(),
                onBreak = breakSource != null,
                breakSource = breakSource,
                startFromSleep = EnergyBattery.startFromSleep(settings.sleepHours).roundToInt(),
            ),
            recovery = recovery(s),
            coach = coach,
            stats = s,
            journal = journal,
            profile = profile,
            settings = settings,
            history = history,
            demo = demo,
        )
    }

    private fun publish() {
        _state.value = snapshot()
    }

    companion object {
        const val MAX_HISTORY_DAYS = 7
        const val SAVE_EVERY_MS = 60_000L
    }
}
