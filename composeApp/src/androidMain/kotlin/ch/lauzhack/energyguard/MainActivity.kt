package ch.lauzhack.energyguard

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.coach.FrugalEnergyCoach
import ch.lauzhack.energyguard.demo.DemoScript
import ch.lauzhack.energyguard.demo.mockInputStats
import ch.lauzhack.energyguard.demo.mockVisionFrame
import ch.lauzhack.energyguard.haptics.HapticOutput
import ch.lauzhack.energyguard.haptics.VibratorHapticOutput
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.ui.App
import ch.lauzhack.energyguard.ui.DashboardState
import ch.lauzhack.energyguard.ui.Warm
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/** The shared dashboard is laid out for a 1280x800 window; on the phone it is scaled down to fit, same layout. */
private val DESIGN_WIDTH = 1280.dp
private val DESIGN_HEIGHT = 800.dp

/**
 * Android app (issue #41, JetBrains "2 platforms"): runs the SAME shared logic as desktop, live, on the phone.
 * commonMain: DemoScript + sample builders -> FatigueEngine -> FrugalEnergyCoach (offline templates, no key on the
 * phone) -> HapticTrigger -> shared App() dashboard. Android-specific: this activity and phone vibration.
 * No camera, WebSocket or JNativeHook here (desktop only). "Replay" restarts the script (desktop: F5).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // demo on a table: never dims
        val haptics = VibratorHapticOutput(this)
        setContent { AndroidDemoApp(haptics) }
    }
}

@Composable
private fun AndroidDemoApp(haptics: HapticOutput) {
    var run by remember { mutableStateOf(0) } // Replay: fresh engine + haptic cooldown, script from 0
    val engine = remember(run) { DemoScript.newEngine() }
    val hapticTrigger = remember(run) { HapticTrigger() }
    val coach = remember { FrugalEnergyCoach(llm = null, clock = System::currentTimeMillis) } // templates only
    var lastVision by remember { mutableStateOf<VisionFrame?>(null) }
    var lastInput by remember { mutableStateOf<InputStats?>(null) }
    val fatigue by engine.state.collectAsState()
    val coachMessage by coach.lastMessage.collectAsState()
    val llmCalls by coach.llmCallCount.collectAsState()

    // Same rates as the desktop mock sources: vision 10 Hz, input 1 Hz, one clock, all on the main thread.
    LaunchedEffect(engine) {
        val start = System.currentTimeMillis()
        var tick = 0L
        while (true) {
            val now = System.currentTimeMillis()
            val tired = DemoScript.tiredness(now - start)
            mockVisionFrame(now, tired).also { lastVision = it; engine.onVision(it) }
            if (tick % 10 == 0L) mockInputStats(tired).also { lastInput = it; engine.onInput(it, now) }
            tick++
            delay(100)
        }
    }
    LaunchedEffect(fatigue.level) {
        hapticTrigger.onLevel(fatigue.level, System.currentTimeMillis())?.let { haptics.pulse(it) }
    }
    LaunchedEffect(engine) {
        coach.reset()
        engine.state.collect { s -> coach.onFatigue(s, localTimeHHmm()) }
    }

    Box(Modifier.fillMaxSize().background(Warm.Night).safeDrawingPadding()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scale = minOf(maxWidth / DESIGN_WIDTH, maxHeight / DESIGN_HEIGHT)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * scale, fontScale = 1f)) {
                Box(Modifier.size(DESIGN_WIDTH, DESIGN_HEIGHT).align(Alignment.Center)) {
                    App(
                        DashboardState(
                            fatigue, lastVision, lastInput, coachMessage, llmCalls,
                            calibrationMs = DemoScript.CALIBRATION_MS,
                            windowMs = DemoScript.WINDOW_MS,
                            runId = run,
                        )
                    )
                }
            }
        }
        OutlinedButton(onClick = { run++ }, Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text("Replay", fontSize = 14.sp, color = Warm.Muted)
        }
        Text("DEMO", Modifier.align(Alignment.BottomEnd).padding(12.dp), fontSize = 14.sp, color = Color.Gray)
    }
}

/** "HH:mm" local time for the coach (java.time needs API 26, minSdk is 24). */
private fun localTimeHHmm(): String {
    val c = Calendar.getInstance()
    return String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}
