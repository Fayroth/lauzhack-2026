package ch.lauzhack.energyguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.model.SleepPrior

/** "Slept 6.5 h" + slider (3..9 h, half-hour steps). Writes SleepPrior, which the engine reads every frame. */
@OptIn(ExperimentalMaterial3Api::class) // thumb / track slots of Slider
@Composable
fun SleepSlider(modifier: Modifier = Modifier) {
    var hours by remember { mutableFloatStateOf(SleepPrior.hours.toFloat()) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Slept", fontSize = 18.sp, color = Warm.Muted)
        Spacer(Modifier.width(8.dp))
        Slider(
            value = hours,
            onValueChange = { hours = it; SleepPrior.hours = it.toDouble() },
            valueRange = 3f..9f,
            steps = 11, // half hours
            modifier = Modifier.width(170.dp),
            // Knob in the color of the chosen night: red for a short one, honey for a full one.
            thumb = { Box(Modifier.size(22.dp).clip(CircleShape).background(sleepColor(hours))) },
            track = { st -> SleepTrack((st.value - 3f) / 6f) },
        )
        Spacer(Modifier.width(8.dp))
        Text(hoursLabel(hours), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Warm.Text)
    }
}

/** 3 h (short night) .. 9 h (full night) on the energy scale: red .. honey. */
internal fun sleepColor(h: Float) = energyColor(((h - 3f) / 6f * 100).toInt().coerceIn(0, 100))

/** The slider bar: the energy gradient (red → coral → apricot → honey) up to the knob, the plain track after it. */
@Composable
private fun SleepTrack(fraction: Float) {
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Warm.Track, cornerRadius = r)
        val brush = Brush.horizontalGradient(listOf(Warm.Rose, Warm.Coral, Warm.Apricot, Warm.Honey), startX = 0f, endX = size.width)
        drawRoundRect(brush, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}

internal fun hoursLabel(h: Float): String {
    val halves = kotlin.math.round(h * 2).toInt()
    return if (halves % 2 == 0) "${halves / 2} h" else "${halves / 2}.5 h"
}
