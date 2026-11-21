package ch.lauzhack.energyguard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.model.Level

/**
 * Concentration gauge: a 270° arc in the level colour, value 0..100 in 96 sp in the middle (the level name is the
 * heading next to it, so no chip here).
 * While [calibrating], the arc stays empty and the middle shows [calibrationSecondsLeft].
 * Value, sweep and color animate over [EG_ANIM_MS].
 */
@Composable
fun Gauge(
    value: Int,
    level: Level,
    modifier: Modifier = Modifier,
    calibrating: Boolean = false,
    calibrationSecondsLeft: Int = 0,
    stroke: Dp = 20.dp,
) {
    val c = Eg.colors
    val target = if (calibrating) 0f else value.coerceIn(0, 100) / 100f
    val sweep by animateFloatAsState(target, tween(EG_ANIM_MS), label = "gaugeSweep")
    val number by animateIntAsState(if (calibrating) calibrationSecondsLeft else value.coerceIn(0, 100), tween(EG_ANIM_MS), label = "gaugeNumber")
    val color by animateColorAsState(if (calibrating) c.accent else c.level(level), tween(EG_ANIM_MS), label = "gaugeColor")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(size.width - w, size.height - w)
            val topLeft = Offset(inset, inset)
            drawArc(c.track, 135f, 270f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (sweep > 0.002f) drawArc(color, 135f, 270f * sweep, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (calibrating) "${number}s" else "$number", style = EgType.gauge, color = if (calibrating) c.accent else c.text)
            Text(if (calibrating) "getting to know you" else "out of 100", style = EgType.label, color = c.muted)
        }
    }
}

/** Horizontal energy battery (0..100): accent colour, TIRED amber below [BATTERY_LOW] (level colours are for state only). */
@Composable
fun BatteryBar(value: Int, modifier: Modifier = Modifier, color: Color = batteryColor(value), height: Dp = 24.dp) {
    val c = Eg.colors
    val fraction by animateFloatAsState(value.coerceIn(0, 100) / 100f, tween(EG_ANIM_MS), label = "battery")
    val animatedColor by animateColorAsState(color, tween(EG_ANIM_MS), label = "batteryColor")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(8.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(8.dp.toPx())
            drawRoundRect(c.track, cornerRadius = r)
            if (fraction > 0.005f) drawRoundRect(animatedColor, size = Size(size.width * fraction, size.height), cornerRadius = r)
        }
    }
}

/** Battery below this (%) switches from the accent to the TIRED amber. */
const val BATTERY_LOW = 30

@Composable
fun batteryColor(value: Int): Color = if (value < BATTERY_LOW) Eg.colors.tired else Eg.colors.accent
