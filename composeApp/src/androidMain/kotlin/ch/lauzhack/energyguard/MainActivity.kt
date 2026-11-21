package ch.lauzhack.energyguard

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.SideEffect
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
import ch.lauzhack.energyguard.app.AppStore
import ch.lauzhack.energyguard.app.JsonFilePersistence
import ch.lauzhack.energyguard.app.localDayKey
import ch.lauzhack.energyguard.app.textFilesIn
import ch.lauzhack.energyguard.coach.FrugalEnergyCoach
import ch.lauzhack.energyguard.companion.CompanionBanner
import ch.lauzhack.energyguard.companion.LiveCompanion
import ch.lauzhack.energyguard.companion.PairingPrefs
import ch.lauzhack.energyguard.companion.PairingScreen
import ch.lauzhack.energyguard.companion.PairingTarget
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
 * Phone companion (#48): once paired with a PC running EG_COMPANION=1, the phone mirrors the PC's LIVE state instead
 * (read-only poll of GET /state, companion/PhoneCompanion.kt); connection lost -> banner + this demo replay as fallback.
 * App model (M1): an AppStore (journal + settings in filesDir, no history: the phone only runs the demo engine) is fed
 * by the local demo replay. The dashboard still renders DashboardState exactly as before.
 */
class MainActivity : ComponentActivity() {
    private lateinit var store: AppStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // demo on a table: never dims
        val haptics = VibratorHapticOutput(this)
        val prefs = PairingPrefs(this)
        store = AppStore(
            System::currentTimeMillis, ::localDayKey, JsonFilePersistence(textFilesIn(filesDir.absolutePath)),
            demo = true, persistHistory = false,
        )
        setContent { PhoneRoot(haptics, prefs, store) }
    }

    override fun onStop() {
        super.onStop()
        store.flush()
    }
}

/** Pairing screen -> live mirror of the PC; "Demo without PC" -> the local demo replay. */
@Composable
private fun PhoneRoot(haptics: HapticOutput, prefs: PairingPrefs, store: AppStore) {
    var target by remember { mutableStateOf(prefs.load()) }
    var pairing by remember { mutableStateOf(target == null) }
    var demoOnly by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pairAgain = { pairing = true; demoOnly = false }
    when {
        demoOnly -> AndroidDemoApp(haptics, store, banner = null, onPair = pairAgain)
        pairing || target == null -> PairingScreen(
            lastHost = prefs.lastHost(), lastPort = prefs.lastPort(), error = error,
            onConnect = { t: PairingTarget -> prefs.save(t); target = t; error = null; pairing = false },
            onDemo = { demoOnly = true },
        )
        else -> LiveCompanion(
            target!!, haptics,
            onStop = { reason -> prefs.forgetCode(); error = reason.message; pairing = true },
            dashboard = { state, banner ->
                Box(Modifier.fillMaxSize().background(Warm.Night).safeDrawingPadding()) {
                    ScaledDashboard(state)
                    banner?.let { CompanionBanner(it, Color(0xFF2E7D32), Modifier.align(Alignment.TopCenter).padding(8.dp)) }
                    OutlinedButton(onClick = pairAgain, Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                        Text("Change PC", fontSize = 14.sp, color = Warm.Muted)
                    }
                    Text("LIVE from your PC", Modifier.align(Alignment.BottomEnd).padding(12.dp), fontSize = 14.sp, color = Color(0xFF81C784))
                }
            },
            offline = { banner -> AndroidDemoApp(haptics, store, banner, onPair = pairAgain) },
        )
    }
}

/** The shared App(), laid out at 1280x800 and scaled to the phone. */
@Composable
private fun ScaledDashboard(state: DashboardState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(maxWidth / DESIGN_WIDTH, maxHeight / DESIGN_HEIGHT)
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, fontScale = 1f)) {
            Box(Modifier.size(DESIGN_WIDTH, DESIGN_HEIGHT).align(Alignment.Center)) { App(state) }
        }
    }
}

@Composable
private fun AndroidDemoApp(haptics: HapticOutput, store: AppStore, banner: String?, onPair: () -> Unit) {
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
            mockVisionFrame(now, tired).also { lastVision = it; engine.onVision(it); store.onVision(it) }
            if (tick % 10 == 0L) {
                mockInputStats(tired).also { lastInput = it; engine.onInput(it, now); store.onInput(it, now) }
                store.tick(now)
            }
            tick++
            delay(100)
        }
    }
    LaunchedEffect(fatigue.level) {
        hapticTrigger.onLevel(fatigue.level, System.currentTimeMillis())?.let { if (store.state.value.settings.hapticsOn) haptics.pulse(it) }
    }
    LaunchedEffect(engine) {
        coach.reset()
        engine.state.collect { s -> coach.onFatigue(s, localTimeHHmm()) }
    }
    LaunchedEffect(engine) { engine.state.collect { store.onFatigue(it, System.currentTimeMillis(), engine.baselineSummary()) } }
    LaunchedEffect(coachMessage, llmCalls) { store.onCoach(coachMessage, llmCalls) }
    LaunchedEffect(run) { if (run > 0) store.resetRun() }

    Box(Modifier.fillMaxSize().background(Warm.Night).safeDrawingPadding()) {
        val dashboard = DashboardState(
            fatigue, lastVision, lastInput, coachMessage, llmCalls,
            calibrationMs = DemoScript.CALIBRATION_MS,
            windowMs = DemoScript.WINDOW_MS,
            runId = run,
        )
        SideEffect { store.setDashboard(dashboard) }
        ScaledDashboard(dashboard)
        banner?.let { CompanionBanner(it, Color(0xFFC62828), Modifier.align(Alignment.TopCenter).padding(8.dp)) }
        Row(Modifier.align(Alignment.BottomStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { run++ }) { Text("Replay", fontSize = 14.sp, color = Warm.Muted) }
            OutlinedButton(onClick = onPair) { Text("Connect to PC", fontSize = 14.sp, color = Warm.Muted) }
        }
        Text("DEMO", Modifier.align(Alignment.BottomEnd).padding(12.dp), fontSize = 14.sp, color = Color.Gray)
    }
}

/** "HH:mm" local time for the coach (java.time needs API 26, minSdk is 24). */
private fun localTimeHHmm(): String {
    val c = Calendar.getInstance()
    return String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}
