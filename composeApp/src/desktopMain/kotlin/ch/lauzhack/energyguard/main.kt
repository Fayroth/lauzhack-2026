package ch.lauzhack.energyguard

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
import ch.lauzhack.energyguard.haptics.HapticPattern
import ch.lauzhack.energyguard.haptics.MockHapticOutput
import ch.lauzhack.energyguard.input.JNativeHookInputSource
import ch.lauzhack.energyguard.input.MockInputSource
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.ui.App
import ch.lauzhack.energyguard.ui.DashboardState
import ch.lauzhack.energyguard.vision.MockVisionSource
import ch.lauzhack.energyguard.vision.WebSocketVisionSource

/**
 * Desktop entry point: wires sources -> FatigueEngine -> UI + haptics.
 * Vision source: mock by default, real sidecar with env var EG_VISION=ws.
 * Input source: mock by default, real JNativeHook mouse/keyboard hooks with env var EG_INPUT=real.
 */
fun main() = application {
    val engine = remember { FatigueEngine() }
    val vision = remember {
        if (System.getenv("EG_VISION") == "ws") WebSocketVisionSource() else MockVisionSource()
    }
    val input = remember {
        if (System.getenv("EG_INPUT") == "real") JNativeHookInputSource() else MockInputSource()
    }
    val haptics = remember { MockHapticOutput() }

    var lastVision by remember { mutableStateOf<VisionFrame?>(null) }
    var lastInput by remember { mutableStateOf<InputStats?>(null) }
    val fatigue by engine.state.collectAsState()

    LaunchedEffect(vision) { vision.frames().collect { lastVision = it; engine.onVision(it) } }
    LaunchedEffect(input) { input.stats().collect { lastInput = it; engine.onInput(it, System.currentTimeMillis()) } }
    LaunchedEffect(fatigue.level) {
        when (fatigue.level) {
            Level.TIRED -> haptics.pulse(HapticPattern.GENTLE)
            Level.EXHAUSTED -> haptics.pulse(HapticPattern.WARNING)
            else -> Unit
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "EnergyGuard",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        App(DashboardState(fatigue, lastVision, lastInput))
    }
}
