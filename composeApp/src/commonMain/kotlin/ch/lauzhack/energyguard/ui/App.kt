package ch.lauzhack.energyguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/** Must match FatigueEngine's default calibrationMs. Only used for the countdown text. */
private const val CALIBRATION_SECONDS = 60

/** 5 minutes of history at one sample per second. */
private const val EnergyHistorySize = 300

/** Root composable shared by Desktop and Android (owner: Mehdi). */
@Composable
fun App(state: DashboardState) {
    MaterialTheme(colorScheme = darkColorScheme(background = Warm.Night, surface = Warm.Surface)) {
        DashboardScreen(state)
    }
}

@Composable
fun DashboardScreen(state: DashboardState) {
    Column(Modifier.fillMaxSize().background(Warm.Night).padding(horizontal = 44.dp, vertical = 30.dp)) {
        // Header
        Row(verticalAlignment = Alignment.Bottom) {
            Text("EnergyGuard", fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Warm.Text)
            Spacer(Modifier.width(20.dp))
            Text("Looking after your energy, quietly", fontSize = 21.sp, color = Warm.Muted)
            Spacer(Modifier.weight(1f))
            Text("Everything stays on this computer", fontSize = 18.sp, color = Warm.Sage)
        }
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            // Left: the gauge, what the jury looks at first.
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GaugePanel(state.fatigue)
            }
            // Right: one tile per signal source.
            Column(
                Modifier.weight(1.1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                SignalTile("Eyes", sourceLabel(state), eyeMetrics(state.vision), Modifier.fillMaxWidth())
                SignalTile("Mouse", "precision & tremor", mouseMetrics(state.input), Modifier.fillMaxWidth())
                SignalTile("Keyboard", "rhythm only, never keys", keyboardMetrics(state.input), Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GaugePanel(f: FatigueState) {
    val history = rememberEnergyHistory(f)
    if (!f.calibrated) {
        val secondsLeft = calibrationCountdown()
        EnergyGauge(energy = 100, label = "Calibrating", size = 360.dp, calibrating = true)
        Spacer(Modifier.height(20.dp))
        Text(
            "Getting to know you... ${secondsLeft} s",
            fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Warm.Calm,
        )
        Text("Just work as usual", fontSize = 22.sp, color = Warm.Muted)
        return
    }
    EnergyGauge(f.energy, f.level.pretty(), size = 360.dp)
    Text("over the last minute", fontSize = 16.sp, color = Warm.Faint)
    Spacer(Modifier.height(8.dp))
    Text(levelHint(f.level), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = energyColor(f.energy), textAlign = TextAlign.Center)
    // Top 3 reasons, so the jury sees WHY the gauge moved.
    f.reasons.take(3).forEach { Text(it, fontSize = 21.sp, color = Warm.Muted) }
    Spacer(Modifier.height(12.dp))
    EnergyTrend(history, Modifier.width(420.dp).height(90.dp))
    Text("your energy, last 5 minutes", fontSize = 16.sp, color = Warm.Faint)
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

/** Seconds left in calibration, counted from the first time the dashboard is shown uncalibrated. */
@Composable
private fun calibrationCountdown(): Int {
    val start = remember { TimeSource.Monotonic.markNow() }
    var left by remember { mutableStateOf(CALIBRATION_SECONDS) }
    LaunchedEffect(start) {
        while (true) {
            left = (CALIBRATION_SECONDS - start.elapsedNow().inWholeSeconds.toInt()).coerceAtLeast(0)
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
