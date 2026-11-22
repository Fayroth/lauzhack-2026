package ch.lauzhack.energyguard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ch.lauzhack.energyguard.HapticTrigger
import ch.lauzhack.energyguard.app.AppStore
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.coach.FrugalEnergyCoach
import ch.lauzhack.energyguard.model.SleepPrior
import ch.lauzhack.energyguard.companion.PairingPrefs
import ch.lauzhack.energyguard.companion.phoneSignals
import ch.lauzhack.energyguard.companion.rememberCompanionLink
import ch.lauzhack.energyguard.companion.toAppState
import ch.lauzhack.energyguard.demo.DemoScript
import ch.lauzhack.energyguard.demo.mockInputStats
import ch.lauzhack.energyguard.demo.mockVisionFrame
import ch.lauzhack.energyguard.haptics.HapticOutput
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.ui.phone.PhoneProfileScreen
import ch.lauzhack.energyguard.ui.phone.PhoneShell
import ch.lauzhack.energyguard.ui.today.TodayActions
import ch.lauzhack.energyguard.ui.today.liveSignals
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

private const val RECOVERED_NOTICE_MS = 30_000L

/**
 * Android shell (M3): the shared [PhoneShell] (bottom bar Today / Journal / Profile) fed by either
 * - **Live**: the paired PC's read-only `GET /state` (companion/PhoneCompanion.kt), Today read-only; or
 * - **Demo**: the phone's own [AppStore] driven by the demo script (no PC paired, or the PC unreachable).
 * One [HapticTrigger] on the shown level (the PC's when live): 1 buzz on TIRED, 3 on EXHAUSTED.
 * System back: previous tab, then exit. Edge-to-edge: [onTheme] restyles the system bars (MainActivity).
 */
@Composable
fun AndroidShell(haptics: HapticOutput, prefs: PairingPrefs, store: AppStore, onTheme: (ThemeMode) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var previous by rememberSaveable { mutableStateOf<Tab?>(null) }
    val go: (Tab) -> Unit = { t -> if (t != tab) { previous = tab; tab = t } }
    BackHandler(enabled = previous != null) { tab = previous ?: Tab.TODAY; previous = null }

    var target by remember { mutableStateOf(prefs.load()) }
    var error by remember { mutableStateOf<String?>(null) }
    val companion = rememberCompanionLink(target) { reason ->
        prefs.forgetCode(); error = reason.message; target = null; go(Tab.PROFILE)
    }

    val local by store.state.collectAsState()
    var run by remember { mutableIntStateOf(0) } // "Replay the demo": fresh engine + haptic cooldown, script from 0
    val live = companion.state
    if (live == null) DemoReplay(store, run)

    val shown = live?.toAppState(local, local.stats.day) ?: local
    val signals = live?.phoneSignals() ?: local.liveSignals()

    // Haptics follow what is shown. A new source (PC <-> demo) or a replay starts a fresh cooldown.
    val trigger = remember(live != null, run) { HapticTrigger() }
    val level = shown.concentration.level
    LaunchedEffect(level, trigger) {
        trigger.onLevel(level, System.currentTimeMillis())?.let { if (local.settings.hapticsOn) haptics.pulse(it) }
    }

    // "You can go back": the PC went from TIRED/EXHAUSTED back to FRESH/OK.
    var wasLow by remember { mutableStateOf(false) }
    var recoveredAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(level, live != null) {
        if (live == null) { wasLow = false; recoveredAt = null; return@LaunchedEffect }
        if (level >= Level.TIRED) { wasLow = true; recoveredAt = null }
        else if (wasLow) { wasLow = false; recoveredAt = System.currentTimeMillis() }
    }
    LaunchedEffect(recoveredAt) { if (recoveredAt != null) { delay(RECOVERED_NOTICE_MS); recoveredAt = null } }

    val theme = local.settings.theme
    LaunchedEffect(theme) { onTheme(theme) }

    PhoneShell(
        state = shown,
        signals = signals,
        link = companion.link,
        tab = tab,
        onTab = go,
        today = remember(store) { TodayActions(store::startBreak, store::endBreak, store::setSleepHours) },
        onReplay = { run++ },
        notice = if (recoveredAt != null) "Your PC says you're recharged: you can go back" else null,
    ) {
        PhoneProfileScreen(
            link = companion.link,
            savedHost = prefs.lastHost(),
            savedPort = prefs.lastPort(),
            error = error,
            theme = theme,
            onConnect = { t -> prefs.save(t); target = t; error = null; go(Tab.TODAY) },
            onToggleTheme = {
                store.updateSettings { s -> s.copy(theme = if (s.theme == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT) }
            },
            modifier = Modifier.imePadding(),
        )
    }
}

/**
 * The phone's own demo (issue #41, JetBrains "2 platforms"): the SAME shared logic as desktop, live on the phone.
 * DemoScript + sample builders -> FatigueEngine -> FrugalEnergyCoach (offline templates, no key on the phone) -> AppStore.
 * Same rates as the desktop mock sources (vision 10 Hz, input 1 Hz), one clock, all on the main thread.
 */
@Composable
private fun DemoReplay(store: AppStore, run: Int) {
    val engine = remember(run) { DemoScript.newEngine() }
    val coach = remember { FrugalEnergyCoach(llm = null, clock = System::currentTimeMillis) } // templates only
    var lastVision by remember { mutableStateOf<VisionFrame?>(null) }
    var lastInput by remember { mutableStateOf<InputStats?>(null) }
    val fatigue by engine.state.collectAsState()
    val coachMessage by coach.lastMessage.collectAsState()
    val llmCalls by coach.llmCallCount.collectAsState()

    LaunchedEffect(engine) {
        val start = System.currentTimeMillis()
        var tick = 0L
        while (true) {
            val now = System.currentTimeMillis()
            val tired = DemoScript.tiredness(now - start)
            mockVisionFrame(now, tired).also { lastVision = it; engine.onVision(it); store.onVision(it) }
            if (tick % 10 == 0L) {
                mockInputStats(tired).also { lastInput = it; engine.onInput(it, now); store.onInput(it, now) }
                store.tick(now)
            }
            tick++
            delay(100)
        }
    }
    LaunchedEffect(engine) {
        coach.reset()
        engine.state.collect { s ->
            coach.onFatigue(s, localTimeHHmm(), SleepPrior.hours, (store.state.value.stats.currentFocusMs / 60_000).toInt())
        }
    }
    LaunchedEffect(engine) { engine.state.collect { store.onFatigue(it, System.currentTimeMillis(), engine.baselineSummary()) } }
    LaunchedEffect(coachMessage, llmCalls) { store.onCoach(coachMessage, llmCalls) }
    LaunchedEffect(run) { if (run > 0) store.resetRun() }

    val dashboard = DashboardState(
        fatigue, lastVision, lastInput, coachMessage, llmCalls,
        calibrationMs = DemoScript.CALIBRATION_MS, windowMs = DemoScript.WINDOW_MS, runId = run,
    )
    SideEffect { store.setDashboard(dashboard) }
}

/** "HH:mm" local time for the coach (java.time needs API 26, minSdk is 24). */
private fun localTimeHHmm(): String {
    val c = Calendar.getInstance()
    return String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}
