package ch.lauzhack.energyguard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ch.lauzhack.energyguard.app.AppStore
import ch.lauzhack.energyguard.app.InMemoryPersistence
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.app.desktopPersistence
import ch.lauzhack.energyguard.app.localDayKey
import ch.lauzhack.energyguard.coach.desktopEnergyCoach
import ch.lauzhack.energyguard.coach.localTimeHHmm
import ch.lauzhack.energyguard.companion.CompanionServer
import ch.lauzhack.energyguard.companion.CompanionState
import ch.lauzhack.energyguard.companion.toCompanionState
import kotlinx.coroutines.launch
import ch.lauzhack.energyguard.demo.DemoClock
import ch.lauzhack.energyguard.demo.DemoScript
import ch.lauzhack.energyguard.engine.FatigueEngine
import ch.lauzhack.energyguard.guard.GuardServer
import ch.lauzhack.energyguard.guardrail.GuardedAction
import ch.lauzhack.energyguard.guardrail.Guardrail
import ch.lauzhack.energyguard.guardrail.LocalGuard
import ch.lauzhack.energyguard.guardrail.Verdict
import ch.lauzhack.energyguard.haptics.HapticPattern
import ch.lauzhack.energyguard.haptics.MockHapticOutput
import ch.lauzhack.energyguard.haptics.MxMaster4HapticOutput
import ch.lauzhack.energyguard.input.JNativeHookInputSource
import ch.lauzhack.energyguard.input.MockInputSource
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.SleepPrior
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.ui.BreakAlertOverlay
import ch.lauzhack.energyguard.ui.DashboardState
import ch.lauzhack.energyguard.ui.DemoInbox
import ch.lauzhack.energyguard.ui.DesktopShell
import ch.lauzhack.energyguard.ui.Shortcut
import ch.lauzhack.energyguard.ui.Tab
import ch.lauzhack.energyguard.ui.components.TextFieldFocus
import ch.lauzhack.energyguard.ui.shortcutFor
import ch.lauzhack.energyguard.ui.today.TodayActions
import ch.lauzhack.energyguard.ui.brand.energyGuardIcon
import ch.lauzhack.energyguard.vision.MockVisionSource
import ch.lauzhack.energyguard.vision.WebSocketVisionSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import java.util.concurrent.atomic.AtomicReference

/** Vision panel shows "-" if no frame arrived for this long (sidecar down/restarting). */
private const val VISION_STALE_MS = 2_000L

/**
 * Desktop entry point: wires sources -> FatigueEngine -> UI + haptics.
 * Vision source: mock by default, real sidecar with env var EG_VISION=ws.
 * Input source: mock by default, real JNativeHook mouse/keyboard hooks with env var EG_INPUT=real.
 * One clock: every timestamp fed to the engine is System.currentTimeMillis() (WS frames are stamped on arrival).
 * All collection runs on the Compose main thread, so the (non-thread-safe) engine sees one caller.
 * Haptics: console mock by default, real MX Master 4 vibration with env var EG_HAPTICS=mx.
 * Coach: EnergyCoach on level changes (OpenRouter if OPENROUTER_API_KEY is set, else offline templates; EG_COACH=offline forces templates).
 * Demo mode (EG_DEMO=1): scripted fatigue ramp into the same engine (demo/DemoMode.kt), F5 replays it, "DEMO" marker.
 * Inbox: second window "EnergyGuard — Inbox" (guardrail demo, ui/DemoInbox.kt), open at start in demo mode, F2 on the dashboard opens it.
 * Chrome guard (#43): POST http://127.0.0.1:8766/guard for extension/ (guard/GuardServer.kt), same `guard` function as LocalGuard.
 * Phone companion (#48, EG_COMPANION=1 only): read-only GET http://<LAN IP>:8767/state with a pairing code (companion/CompanionServer.kt),
 * fed a snapshot of the dashboard state after each recomposition. Off by default: nothing more listens.
 * App model (M1): AppStore (app/AppStore.kt) is fed the same events (engine, coach, guard blocks, 1 s tick) and persists
 * journal/settings/history in ~/.energyguard/ (in memory in demo mode).
 * UI (M2): DesktopShell (ui/DesktopShell.kt) renders AppStore.state only: rail Today / Journal / Profile (keys 1 / 2 / 3,
 * ignored like F2 / F5 while a text field has focus: ui/Shortcuts.kt),
 * theme = Settings.theme (header toggle -> store.updateSettings). Break alert overlay drawn on top, as before.
 */
fun main() = application {
    val demo = remember { System.getenv("EG_DEMO") == "1" }
    val demoClock = remember { DemoClock() }
    var demoRun by remember { mutableStateOf(0) } // F5 in demo mode: fresh engine + haptic cooldown, script from 0
    val engine = remember(demoRun) { if (demo) DemoScript.newEngine() else FatigueEngine() }
    val vision = remember {
        if (demo) MockVisionSource(demoClock::tiredness)
        else if (System.getenv("EG_VISION") == "ws") WebSocketVisionSource() else MockVisionSource()
    }
    val input = remember {
        if (demo) MockInputSource(demoClock::tiredness)
        else if (System.getenv("EG_INPUT") == "real") JNativeHookInputSource() else MockInputSource()
    }
    val hapticTrigger = remember(demoRun) { HapticTrigger() }
    val haptics = remember {
        if (System.getenv("EG_HAPTICS") == "mx") MxMaster4HapticOutput().also { it.pulse(HapticPattern.GENTLE) } // startup pulse = "mouse connected"
        else MockHapticOutput()
    }
    val store = remember {
        AppStore(System::currentTimeMillis, ::localDayKey, if (demo) InMemoryPersistence() else desktopPersistence(), demo = demo)
    }
    DisposableEffect(store) { onDispose { store.flush() } }
    val mainScope = rememberCoroutineScope() // guard Block events from Ktor threads -> store on the main thread
    val coach = remember { desktopEnergyCoach() } // not keyed on demoRun: the rate limit and the call counter stay real
    val guardrail = remember { Guardrail() }
    // ONE guard function for the inbox (LocalGuard) and the Chrome extension (GuardServer): checked against the latest
    // fatigue of the current engine (F5 swaps it), mouse vibrates on every Block. Called from the UI thread or Ktor threads:
    // Guardrail is pure, StateFlow.value is thread-safe.
    val currentEngine by rememberUpdatedState(engine)
    val guard: (GuardedAction) -> Verdict = remember {
        { action ->
            guardrail.check(action, currentEngine.state.value).also {
                if (it is Verdict.Block) {
                    if (store.state.value.settings.hapticsOn) haptics.pulse(HapticPattern.WARNING)
                    mainScope.launch { store.onGuardBlock() }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        val server = GuardServer.fromEnv(guard) { currentEngine.state.value.level }.also { it.start() }
        onDispose { server.stop() }
    }

    // Phone companion: Ktor threads only read this snapshot; the UI thread writes it (SideEffect below).
    val companionSnapshot = remember { AtomicReference<CompanionState?>(null) }
    val companion = remember { CompanionServer.fromEnv(companionSnapshot::get) } // null unless EG_COMPANION=1
    DisposableEffect(companion) {
        companion?.start()
        onDispose { companion?.stop() }
    }

    var showInbox by remember { mutableStateOf(demo) }
    var tab by remember { mutableStateOf(Tab.TODAY) }
    val appState by store.state.collectAsState()

    var lastVision by remember { mutableStateOf<VisionFrame?>(null) }
    var lastInput by remember { mutableStateOf<InputStats?>(null) }
    val fatigue by engine.state.collectAsState()
    val coachMessage by coach.lastMessage.collectAsState()
    val llmCalls by coach.llmCallCount.collectAsState()

    LaunchedEffect(vision, engine) {
        vision.frames()
            .catch { System.err.println("[vision] source stopped: ${it.message}") } // never crash the app
            .collect { lastVision = it; engine.onVision(it); store.onVision(it) }
    }
    LaunchedEffect(vision) {
        while (true) { // sidecar down: clear the stale vision panel; the engine already runs on input only
            delay(500)
            lastVision?.let { if (System.currentTimeMillis() - it.ts > VISION_STALE_MS) lastVision = null }
        }
    }
    LaunchedEffect(input, engine) {
        input.stats()
            .catch { System.err.println("[input] source stopped: ${it.message}") }
            .collect {
                val now = System.currentTimeMillis()
                lastInput = it; engine.onInput(it, now); store.onInput(it, now)
            }
    }
    LaunchedEffect(fatigue.level) {
        hapticTrigger.onLevel(fatigue.level, System.currentTimeMillis())?.let { if (store.state.value.settings.hapticsOn) haptics.pulse(it) }
    }
    LaunchedEffect(engine) { engine.state.collect { store.onFatigue(it, System.currentTimeMillis(), engine.baselineSummary()) } }
    LaunchedEffect(coachMessage, llmCalls) { store.onCoach(coachMessage, llmCalls) }
    LaunchedEffect(store) {
        while (true) { store.tick(); delay(1_000) }
    }

    LaunchedEffect(engine) { // sequential collect: an in-flight LLM call (<= 5 s, off the UI thread) is never cancelled
        coach.reset()
        engine.state.collect { s ->
            val focusMin = (store.state.value.stats.currentFocusMs / 60_000).toInt() // breaks taken in the app count
            coach.onFatigue(s, localTimeHHmm(), SleepPrior.hours, focusMin)?.let { println("[coach] ${if (it.fromLlm) "llm" else "template"} ${it.kind}: ${it.text}") }
        }
    }

    // Both windows share LocalGuard: the inbox checks links against the same live fatigue + haptics.
    CompositionLocalProvider(LocalGuard provides guard) {
        Window(
            onCloseRequest = ::exitApplication,
            title = if (demo) "EnergyGuard (demo mode)" else "EnergyGuard",
            icon = rememberVectorPainter(remember { energyGuardIcon() }),
            state = rememberWindowState(position = WindowPosition(Alignment.Center), size = defaultWindowSize()),
            onKeyEvent = {
                if (it.type != KeyEventType.KeyDown || it.isCtrlPressed || it.isAltPressed) return@Window false
                when (shortcutFor(it.key, TextFieldFocus.active)) { // typing in a text field: no shortcuts
                    Shortcut.REPLAY_DEMO -> if (demo) { demoClock.restart(); demoRun++; store.resetRun(); true } else false
                    Shortcut.OPEN_INBOX -> { showInbox = true; true }
                    Shortcut.TAB_TODAY -> { tab = Tab.TODAY; true }
                    Shortcut.TAB_JOURNAL -> { tab = Tab.JOURNAL; true }
                    Shortcut.TAB_PROFILE -> { tab = Tab.PROFILE; true }
                    null -> false
                }
            },
        ) {
            run {
                val dashboard = DashboardState(
                    fatigue, lastVision, lastInput, coachMessage, llmCalls,
                    calibrationMs = if (demo) DemoScript.CALIBRATION_MS else FatigueEngine.DEFAULT_CALIBRATION_MS,
                    windowMs = if (demo) DemoScript.WINDOW_MS else FatigueEngine.DEFAULT_WINDOW_MS,
                    runId = demoRun,
                )
                SideEffect {
                    store.setDashboard(dashboard)
                    if (companion != null) companionSnapshot.set(store.state.value.toCompanionState(System.currentTimeMillis()))
                }
                DesktopShell(
                    state = appState,
                    tab = tab,
                    onTab = { tab = it },
                    today = remember(store) { TodayActions(store::startBreak, store::endBreak, store::setSleepHours) },
                    onToggleTheme = {
                        store.updateSettings { s -> s.copy(theme = if (s.theme == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT) }
                    },
                    footer = companion?.pairingLabel,
                ) {
                    // Demo mode calibrates in 15 s: shorter alert escalation too. F5 replay: fresh alert state.
                    key(demoRun) { BreakAlertOverlay(fatigue, coachMessage, demo) }
                }
            }
        }
        if (showInbox) {
            Window(
                onCloseRequest = { showInbox = false },
                title = "EnergyGuard — Inbox",
                icon = rememberVectorPainter(remember { energyGuardIcon() }),
                state = rememberWindowState(width = 1280.dp, height = 800.dp),
            ) { DemoInbox() }
        }
    }
}

/** 1440x900 (design size), shrunk to the usable screen (taskbar excluded) on small or scaled displays, e.g. 1920x1200 at 125 %. */
private fun defaultWindowSize(): DpSize {
    val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds // in dp on Windows / macOS
    return DpSize(minOf(1440, screen.width).dp, minOf(900, screen.height - 24).dp) // the UI scales to fit (DesktopShell)
}
