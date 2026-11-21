package ch.lauzhack.energyguard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.ui.brand.EnergyGuardLogo
import ch.lauzhack.energyguard.model.Level
import kotlinx.coroutines.delay
import kotlin.time.TimeSource


/** 5 minutes of history at one sample per second. */
private const val EnergyHistorySize = 300

/** Root composable shared by Desktop and Android (owner: Mehdi). */
@Composable
fun App(state: DashboardState) {
    // Demo mode calibrates in 15 s instead of 60 s: use that to shorten the alert escalation too.
    val demo = state.calibrationMs < 60_000
    MaterialTheme(
        colorScheme = if (Warm.light) lightColorScheme(background = Warm.Night, surface = Warm.Surface)
        else darkColorScheme(background = Warm.Night, surface = Warm.Surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            DashboardScreen(state)
            key(state.runId) { BreakAlertOverlay(state.fatigue, state.coach, demo) } // F5 replay: fresh alert state
            KssPrompt(state, demo)
        }
    }
}

@Composable
fun DashboardScreen(state: DashboardState) {
    Column(Modifier.fillMaxSize().background(moodBackground(state.fatigue)).padding(horizontal = 48.dp, vertical = 32.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            EnergyGuardLogo(state.fatigue, Modifier.height(32.dp).width(56.dp))
            Spacer(Modifier.width(16.dp))
            Text("EnergyGuard", fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Warm.Text)
            Spacer(Modifier.width(24.dp))
            SleepSlider()
            Spacer(Modifier.weight(1f))
            Text("Everything stays on this computer", fontSize = 18.sp, color = Warm.Brand)
            Spacer(Modifier.width(16.dp))
            ThemeToggle()
        }
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            // Left: the gauge, what the jury looks at first.
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GaugePanel(state)
            }
            // Right: one tile per signal source.
            Column(
                Modifier.weight(1.1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                SignalTile("Eyes", sourceLabel(state), eyeMetrics(state.vision), Modifier.fillMaxWidth())
                SignalTile("Mouse", "precision & tremor", mouseMetrics(state.input), Modifier.fillMaxWidth())
                SignalTile("Keyboard", "rhythm only, never keys", keyboardMetrics(state.input), Modifier.fillMaxWidth())
                FrugalityStrip(state, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GaugePanel(state: DashboardState) {
    val f = state.fatigue
    // Keyed on runId: an F5 demo replay starts with an empty trend and a fresh countdown.
    val history = key(state.runId) { rememberEnergyHistory(f) }
    if (!f.calibrated) {
        val secondsLeft = key(state.runId) { calibrationCountdown(state.calibrationMs) }
        EnergyGauge(energy = 100, label = "Calibrating", size = 320.dp, calibrating = true)
        Spacer(Modifier.height(24.dp))
        Text(
            "Getting to know you... ${secondsLeft} s",
            fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Warm.Calm,
        )
        Text("Just work as usual", fontSize = 22.sp, color = Warm.Muted)
        return
    }
    EnergyGauge(f.energy, f.level.pretty(), size = 320.dp, tint = stateColor(f))
    Text("over the ${windowLabel(state.windowMs)}", fontSize = 16.sp, color = Warm.Faint)
    Spacer(Modifier.height(8.dp))
    Text(levelHint(f.level), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = stateColor(f), textAlign = TextAlign.Center)
    // Top 3 reasons, so the jury sees WHY the gauge moved.
    f.reasons.take(3).forEach { Text(readableReason(it), fontSize = 21.sp, color = Warm.Muted) }
    Spacer(Modifier.height(16.dp))
    // Legend beside the chart, not under it, so nothing is cut off at 1280x800 with 3 reasons.
    Row(verticalAlignment = Alignment.Bottom) {
        EnergyTrend(history, Modifier.width(380.dp).height(80.dp))
        Spacer(Modifier.width(16.dp))
        Text("energy\nlast 5 min", fontSize = 16.sp, color = Warm.Faint)
    }
    KssSummary(Modifier.padding(top = 8.dp))
}

/** "last minute" (real engine, 60 s window) or "last 10 s" (demo mode). */
internal fun windowLabel(windowMs: Long): String {
    val s = windowMs / 1000
    return when {
        s == 60L -> "last minute"
        s % 60 == 0L -> "last ${s / 60} min"
        else -> "last $s s"
    }
}

/**
 * The engine says "eyes closing +347%"; on screen a factor reads faster: "eyes closing ×4.5 vs usual".
 * Display only, the engine's strings are unchanged. Reasons without a signed percentage pass through.
 */
private val signedPercent = Regex(""" ([+-]\d+)%$""")

/** Same idea inside free text (the coach's message): every "+347%" becomes "×4.5 vs usual". */
private val signedPercentAnywhere = Regex("""([+-])(\d+)\s?%""")

internal fun factorize(text: String): String = signedPercentAnywhere.replace(text) { m ->
    val pct = m.groupValues[2].toInt() * (if (m.groupValues[1] == "-") -1 else 1)
    "×${(1 + pct / 100.0).coerceAtLeast(0.0).fmt(1)} vs usual"
}

internal fun readableReason(reason: String): String {
    val match = signedPercent.find(reason) ?: return reason
    val factor = 1 + match.groupValues[1].toInt() / 100.0
    return reason.substring(0, match.range.first) + " ×${factor.coerceAtLeast(0.0).fmt(1)} vs usual"
}

/** Samples the energy once per second (after calibration) and keeps the last [EnergyHistorySize] values. */
@Composable
private fun rememberEnergyHistory(f: FatigueState): List<Int> {
    val current by rememberUpdatedState(f)
    var history by remember { mutableStateOf(listOf<Int>()) }
    LaunchedEffect(Unit) {
        while (true) {
            if (current.calibrated) history = (history + current.energy).takeLast(EnergyHistorySize)
            delay(1000)
        }
    }
    return history
}

/**
 * Background that follows the energy, like WHOOP tints its screens by recovery: night mauve on the
 * gauge side with a hint of the energy color (honey when fresh, rose when exhausted), fading to deep
 * indigo behind the tiles. Kept subtle so text contrast holds on a projector; the tint eases in over 2 s.
 */
@Composable
private fun moodBackground(f: FatigueState): Brush {
    val target = if (f.calibrated) stateColor(f) else Warm.Calm
    val tint by animateColorAsState(target, tween(2000), label = "mood")
    return Brush.horizontalGradient(listOf(lerp(Warm.Mauve, tint, 0.14f), Warm.Deep))
}

/** Seconds left in calibration ([calibrationMs] from the engine), counted from when the countdown is first shown. */
@Composable
private fun calibrationCountdown(calibrationMs: Long): Int {
    val total = (calibrationMs / 1000).toInt()
    val start = remember { TimeSource.Monotonic.markNow() }
    var left by remember { mutableStateOf(total) }
    LaunchedEffect(start) {
        while (true) {
            left = (total - start.elapsedNow().inWholeSeconds.toInt()).coerceAtLeast(0)
            delay(250)
        }
    }
    return left
}

private fun sourceLabel(state: DashboardState): String = when {
    state.vision == null -> "no camera yet"
    !state.vision.face -> "no face in view"
    else -> "via ${state.vision.source}"
}

private fun levelHint(level: Level): String = when (level) {
    Level.FRESH -> "Fully charged"
    Level.OK -> "Steady"
    Level.TIRED -> "Running low, a break would help"
    Level.EXHAUSTED -> "Time to rest"
}

private fun Level.pretty() = name.lowercase().replaceFirstChar { it.uppercase() }
