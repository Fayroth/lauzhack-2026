package ch.lauzhack.energyguard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ch.lauzhack.energyguard.coach.desktopEnergyCoach
import ch.lauzhack.energyguard.coach.localTimeHHmm
import ch.lauzhack.energyguard.demo.DemoClock
import ch.lauzhack.energyguard.demo.DemoScript
import ch.lauzhack.energyguard.engine.FatigueEngine
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
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.ui.App
import ch.lauzhack.energyguard.ui.DashboardState
import ch.lauzhack.energyguard.ui.brand.energyGuardIcon
import ch.lauzhack.energyguard.vision.MockVisionSource
import ch.lauzhack.energyguard.vision.WebSocketVisionSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch

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
    val coach = remember { desktopEnergyCoach() } // not keyed on demoRun: the rate limit and the call counter stay real
    val guardrail = remember { Guardrail() }
    // UI calls LocalGuard.current(action): checked against the latest fatigue, mouse vibrates on every Block.
    val guard: (GuardedAction) -> Verdict = remember(engine) {
        { action ->
            guardrail.check(action, engine.state.value).also { if (it is Verdict.Block) haptics.pulse(HapticPattern.WARNING) }
        }
    }

    var lastVision by remember { mutableStateOf<VisionFrame?>(null) }
    var lastInput by remember { mutableStateOf<InputStats?>(null) }
    val fatigue by engine.state.collectAsState()
    val coachMessage by coach.lastMessage.collectAsState()
    val llmCalls by coach.llmCallCount.collectAsState()

    LaunchedEffect(vision, engine) {
        vision.frames()
            .catch { System.err.println("[vision] source stopped: ${it.message}") } // never crash the app
            .collect { lastVision = it; engine.onVision(it) }
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
            .collect { lastInput = it; engine.onInput(it, System.currentTimeMillis()) }
    }
    LaunchedEffect(fatigue.level) {
        hapticTrigger.onLevel(fatigue.level, System.currentTimeMillis())?.let { haptics.pulse(it) }
    }

    LaunchedEffect(engine) { // sequential collect: an in-flight LLM call (<= 5 s, off the UI thread) is never cancelled
        coach.reset()
        engine.state.collect { s ->
            coach.onFatigue(s, localTimeHHmm())?.let { println("[coach] ${if (it.fromLlm) "llm" else "template"} ${it.kind}: ${it.text}") }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = if (demo) "EnergyGuard (demo mode)" else "EnergyGuard",
        icon = rememberVectorPainter(remember { energyGuardIcon() }),
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
        onKeyEvent = {
            if (demo && it.type == KeyEventType.KeyDown && it.key == Key.F5) {
                demoClock.restart(); demoRun++; true
            } else false
        },
    ) {
        CompositionLocalProvider(LocalGuard provides guard) {
            Box {
                App(
                    DashboardState(
                        fatigue, lastVision, lastInput, coachMessage, llmCalls,
                        calibrationMs = if (demo) DemoScript.CALIBRATION_MS else FatigueEngine.DEFAULT_CALIBRATION_MS,
                        windowMs = if (demo) DemoScript.WINDOW_MS else FatigueEngine.DEFAULT_WINDOW_MS,
                        runId = demoRun,
                    )
                )
                if (demo) Text("DEMO", Modifier.align(Alignment.BottomEnd).padding(12.dp), fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}
