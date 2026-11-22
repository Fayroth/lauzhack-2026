package ch.lauzhack.energyguard.app

import ch.lauzhack.energyguard.coach.CoachMessage
import ch.lauzhack.energyguard.coach.Intervention
import ch.lauzhack.energyguard.engine.BaselineSummary
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.ui.DashboardState
import kotlinx.serialization.Serializable

/**
 * Everything the 3-tab app (Today / Journal / Profile) needs, for both shells. Superset of [DashboardState].
 * Pure Kotlin, produced by [AppStore]. Contract (field meanings, constants): docs/API.md "App model".
 */
data class AppState(
    val dashboard: DashboardState = DashboardState(),
    val concentration: Concentration = Concentration.from(FatigueState.Initial),
    val energy: EnergyState = EnergyState(),
    val recovery: Int = 0,
    val coach: CoachNote? = null,
    val stats: DayStats = DayStats(day = ""),
    val journal: List<JournalEntry> = emptyList(), // newest first
    val profile: Profile = Profile(),
    val settings: Settings = Settings(),
    val history: List<DaySummary> = emptyList(),   // past days, newest first, max 7
    val demo: Boolean = false,
    val energyHistory: EnergyHistory = EnergyHistory(), // M4.5: today's battery curve (in memory only)
)

/**
 * Battery through the day (Today chart), in memory only: one sample per [STEP_MIN] min of app time (accelerated in
 * demo), oldest first, the first one at app start / day start / demo replay. [startMs] = wall clock of the first one.
 */
data class EnergyHistory(val startMs: Long = 0, val values: List<Int> = emptyList()) {
    companion object {
        const val STEP_MIN = 5
        const val STEP_MS = STEP_MIN * 60_000L
        const val MAX_SAMPLES = 24 * 60 / STEP_MIN
    }
}

/** Live concentration: 100 - fatigue score, with the engine's level and top-3 reasons. */
data class Concentration(
    val value: Int,
    val level: Level,
    val reasons: List<String>,
    val calibrated: Boolean,
) {
    companion object {
        fun from(f: FatigueState) = Concentration(
            value = (100 - f.score).coerceIn(0, 100),
            level = f.level,
            reasons = f.reasons.take(3),
            calibrated = f.calibrated,
        )
    }
}

enum class BreakSource { USER, AUTO }

/** Energy battery (slow, over the day). [startFromSleep] = what the battery started at this morning. */
data class EnergyState(
    val battery: Int = 100,
    val onBreak: Boolean = false,
    val breakSource: BreakSource? = null,
    val startFromSleep: Int = 100,
)

/** Today's counters. Privacy counters are always 0: by design nothing of the kind ever leaves the device. */
@Serializable
data class DayStats(
    val day: String,                // "YYYY-MM-DD", local
    val focusedMs: Long = 0,
    val breaksTaken: Int = 0,
    val breaksNeeded: Int = 0,
    val longestFocusMs: Long = 0,
    val currentFocusMs: Long = 0,
    val phishingBlocked: Int = 0,
    val llmCalls: Int = 0,
    val framesProcessed: Long = 0,
    val imagesSent: Int = 0,
    val audioSent: Int = 0,
    val keysSent: Int = 0,
)

/**
 * One journal check-in. Every field is optional. [note] is PRIVATE: it never leaves the device
 * (never sent to the LLM, never in the companion JSON). [concentration] = our Concentration.value at that moment.
 */
@Serializable
data class JournalEntry(
    val id: Long,
    val ts: Long,
    val kss: Int? = null,        // Karolinska Sleepiness Scale 1..9 (1 = extremely alert)
    val stress: Int? = null,     // 1..5
    val caffeine: Int? = null,   // cups today
    val note: String? = null,
    val concentration: Int,
) {
    val isEmpty get() = kss == null && stress == null && caffeine == null && note == null

    /** Same entry with every out-of-range value dropped (untrusted file / UI input). */
    fun sanitized() = copy(
        kss = kss?.takeIf { it in KSS_RANGE },
        stress = stress?.takeIf { it in STRESS_RANGE },
        caffeine = caffeine?.takeIf { it in CAFFEINE_RANGE },
        note = note?.trim()?.take(MAX_NOTE_CHARS)?.takeIf { it.isNotEmpty() },
        concentration = concentration.coerceIn(0, 100),
    )

    companion object {
        val KSS_RANGE = 1..9
        val STRESS_RANGE = 1..5
        val CAFFEINE_RANGE = 0..20
        const val MAX_NOTE_CHARS = 1000
        const val MAX_ENTRIES = 500
    }
}

enum class ThemeMode { DARK, LIGHT }

@Serializable
data class Settings(
    val sleepHours: Double = DEFAULT_SLEEP_HOURS,
    val sleepDay: String? = null,  // day the sleep hours were entered; ignored at start if not today
    val theme: ThemeMode = ThemeMode.DARK,
    val hapticsOn: Boolean = true,
    val coachOnline: Boolean = true,
) {
    fun sanitized() = copy(
        sleepHours = if (sleepHours.isFinite()) sleepHours.coerceIn(MIN_SLEEP_HOURS, MAX_SLEEP_HOURS) else DEFAULT_SLEEP_HOURS,
        sleepDay = sleepDay?.takeIf { DAY.matches(it) },
    )

    companion object {
        const val DEFAULT_SLEEP_HOURS = 7.0
        const val MIN_SLEEP_HOURS = 3.0
        const val MAX_SLEEP_HOURS = 9.0
        internal val DAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    }
}

/** One past day, for the History (last 7 days). Numbers only. */
@Serializable
data class DaySummary(
    val day: String,
    val focusedMs: Long = 0,
    val breaksTaken: Int = 0,
    val longestFocusMs: Long = 0,
    val phishingBlocked: Int = 0,
    val llmCalls: Int = 0,
    val framesProcessed: Long = 0,
    val battery: Int = 100,       // battery at the end of the day (or at the last save)
    val minBattery: Int = 100,
    val recovery: Int = 0,
    val sleepHours: Double = Settings.DEFAULT_SLEEP_HOURS,
    val avgConcentration: Int? = null,
    val checkIns: Int = 0,
) {
    fun sanitized() = copy(
        focusedMs = focusedMs.coerceAtLeast(0),
        breaksTaken = breaksTaken.coerceAtLeast(0),
        longestFocusMs = longestFocusMs.coerceAtLeast(0),
        phishingBlocked = phishingBlocked.coerceAtLeast(0),
        llmCalls = llmCalls.coerceAtLeast(0),
        framesProcessed = framesProcessed.coerceAtLeast(0),
        battery = battery.coerceIn(0, 100),
        minBattery = minBattery.coerceIn(0, 100),
        recovery = recovery.coerceIn(0, 100),
        sleepHours = if (sleepHours.isFinite()) sleepHours.coerceIn(0.0, 24.0) else Settings.DEFAULT_SLEEP_HOURS,
        avgConcentration = avgConcentration?.coerceIn(0, 100),
        checkIns = checkIns.coerceAtLeast(0),
    )
}

/** Profile tab: the personal baseline learned during calibration. */
data class Profile(val baseline: BaselineSummary = BaselineSummary.Empty)

/** Last coach message with its "AI" / "offline" tag. */
/** [ts] = when the note arrived (store clock), null if unknown (phone: not on the wire). */
data class CoachNote(val text: String, val kind: Intervention, val fromLlm: Boolean, val ts: Long? = null) {
    val tag: String get() = if (fromLlm) "AI" else "offline"

    companion object {
        fun from(m: CoachMessage) = CoachNote(m.text, m.kind, m.fromLlm)
    }
}
