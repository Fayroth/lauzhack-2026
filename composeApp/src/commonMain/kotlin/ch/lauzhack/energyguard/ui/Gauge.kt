package ch.lauzhack.energyguard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Energy gauge: 270 degree arc, honey to rose, with a soft glow. [energy] in 0..100. Calm blue while [calibrating]. */
@Composable
fun EnergyGauge(energy: Int, label: String, size: Dp = 360.dp, calibrating: Boolean = false) {
    val animated by animateFloatAsState(if (calibrating) 1f else energy / 100f, tween(800), label = "energy")
    val color = if (calibrating) Warm.Calm.copy(alpha = 0.35f) else energyColor(energy)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).padding(24.dp)) {
            val stroke = Stroke(width = 30.dp.toPx(), cap = StrokeCap.Round)
            val glow = Stroke(width = 54.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Warm.Track, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = stroke)
            // Soft halo under the arc: the "warm" part, without a gradient wash over the whole screen.
            drawArc(color.copy(alpha = 0.16f), startAngle = 135f, sweepAngle = 270f * animated, useCenter = false, style = glow)
            drawArc(color, startAngle = 135f, sweepAngle = 270f * animated, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (calibrating) "..." else "$energy%",
                fontSize = 92.sp, fontWeight = FontWeight.SemiBold,
                color = if (calibrating) Warm.Calm else color,
            )
            Text(label, fontSize = 26.sp, color = Warm.Text)
        }
    }
}

fun energyColor(energy: Int): Color = when {
    energy >= 75 -> Warm.Honey
    energy >= 50 -> Warm.Apricot
    energy >= 25 -> Warm.Coral
    else -> Warm.Rose
}

/**
 * Energy over time as a line, so the jury sees the drop happen instead of a number changing.
 * [history] holds one energy value (0..100) per second, oldest first. The x axis is fixed to
 * [capacity] samples so the line grows from the left, then scrolls.
 */
@Composable
fun EnergyTrend(history: List<Int>, modifier: Modifier = Modifier, capacity: Int = 300) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Threshold guides at 25 / 50 / 75 % energy, no box around the chart.
        listOf(25, 50, 75).forEach { e ->
            val y = h * (1 - e / 100f)
            drawLine(Warm.Track, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }
        if (history.size < 2) return@Canvas
        val step = w / (capacity - 1).coerceAtLeast(1)
        val start = (capacity - history.size).coerceAtLeast(0) * step
        val points = history.mapIndexed { i, e -> Offset(start + i * step, h * (1 - e.coerceIn(0, 100) / 100f)) }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val color = energyColor(history.last())
        // Soft fill under the line, fading to nothing at the bottom.
        val area = Path().apply {
            addPath(path)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color.copy(alpha = 0.25f), radius = 11.dp.toPx(), center = points.last())
        drawCircle(color, radius = 5.dp.toPx(), center = points.last())
    }
}
