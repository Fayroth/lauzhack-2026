package ch.lauzhack.energyguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.model.Level
import kotlin.math.round

/** Root composable shared by Desktop and Android (owner: Mehdi). */
@Composable
fun App(state: DashboardState) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        DashboardScreen(state)
    }
}

@Composable
fun DashboardScreen(state: DashboardState) {
    val f = state.fatigue
    Row(
        Modifier.fillMaxSize().background(Color(0xFF121212)).padding(48.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("EnergyGuard", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(24.dp))
            EnergyGauge(f.energy, if (f.calibrated) f.level.pretty() else "Calibrating...")
            Spacer(Modifier.height(16.dp))
            f.reasons.forEach { Text("- $it", fontSize = 24.sp, color = Color(0xFFFFB74D)) }
        }
        Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Live signals", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            val v = state.vision
            Signal("Face", if (v == null) "-" else if (v.face) "yes (${v.source})" else "no")
            Signal("EAR", v?.ear?.fmt() ?: "-")
            Signal("PERCLOS", v?.perclos?.let { "${(it * 100).fmt(0)}%" } ?: "-")
            Signal("Blinks / min", v?.blinkCount?.toString() ?: "-")
            Signal("Last blink", v?.lastBlinkMs?.let { "$it ms" } ?: "-")
            val i = state.input
            Signal("Mouse jitter", i?.jitter?.fmt() ?: "-")
            Signal("Overshoot", i?.overshootRatio?.fmt() ?: "-")
            Signal("Backspace", i?.backspaceRatio?.let { "${(it * 100).fmt(0)}%" } ?: "-")
            Spacer(Modifier.height(16.dp))
            Text("All signals computed locally. 0 images leave the PC.", fontSize = 16.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun Signal(name: String, value: String) {
    Row(Modifier.width(380.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, fontSize = 22.sp, color = Color.LightGray)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

private fun Level.pretty() = name.lowercase().replaceFirstChar { it.uppercase() }

private fun Double.fmt(decimals: Int = 2): String {
    var m = 1.0
    repeat(decimals) { m *= 10 }
    val r = round(this * m) / m
    return if (decimals == 0) r.toLong().toString() else r.toString()
}
