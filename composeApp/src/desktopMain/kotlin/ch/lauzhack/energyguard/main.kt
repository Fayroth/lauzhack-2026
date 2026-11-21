package ch.lauzhack.energyguard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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
 */
fun main() = application {
    val engine = remember { FatigueEngine() }
    val vision = remember {
        if (System.getenv("EG_VISION") == "ws") WebSocketVisionSource() else MockVisionSource()
    }
    val input = remember {
        if (System.getenv("EG_INPUT") == "real") JNativeHookInputSource() else MockInputSource()
    }
    val hapticTrigger = remember { HapticTrigger() }
    val haptics = remember {
        if (System.getenv("EG_HAPTICS") == "mx") MxMaster4HapticOutput().also { it.pulse(HapticPattern.GENTLE) } // startup pulse = "mouse connected"
        else MockHapticOutput()
    }
    val guardrail = remember { Guardrail() }
    // UI calls LocalGuard.current(action): checked against the latest fatigue, mouse vibrates on every Block.
    val guard: (GuardedAction) -> Verdict = remember {
        { action ->
            guardrail.check(action, engine.state.value).also { if (it is Verdict.Block) haptics.pulse(HapticPattern.WARNING) }
        }
    }

    var lastVision by remember { mutableStateOf<VisionFrame?>(null) }
    var lastInput by remember { mutableStateOf<InputStats?>(null) }
    val fatigue by engine.state.collectAsState()

    LaunchedEffect(vision) {
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
    LaunchedEffect(input) {
        input.stats()
            .catch { System.err.println("[input] source stopped: ${it.message}") }
            .collect { lastInput = it; engine.onInput(it, System.currentTimeMillis()) }
    }
    LaunchedEffect(fatigue.level) {
        hapticTrigger.onLevel(fatigue.level, System.currentTimeMillis())?.let { haptics.pulse(it) }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "EnergyGuard",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        CompositionLocalProvider(LocalGuard provides guard) {
            App(DashboardState(fatigue, lastVision, lastInput))
        }
    }
}
