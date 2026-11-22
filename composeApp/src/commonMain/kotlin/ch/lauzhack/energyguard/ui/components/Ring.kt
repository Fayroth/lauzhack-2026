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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The C2 signature ring: track, progress arc from 12 o'clock with round caps, a soft fake glow (the same arc drawn
 * [GLOW_PASSES] times, wider and fainter each time, so the alphas stack into a smooth falloff with no visible band;
 * no blur API; fainter on the light theme), 60 tick marks outside (every 5th longer), the number in the centre and a
 * small unit under it. Value, sweep and colour animate over [EG_ANIM_MS].
 *
 * [value] in 0..[max]; [number] overrides the centre text (calibration countdown "12s").
 */
@Composable
fun Ring(
    value: Int,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    max: Int = 100,
    number: String? = null,
    stroke: Dp = 14.dp,
    ticks: Boolean = true,
    numberStyle: TextStyle = EgType.gauge,
    unitStyle: TextStyle = EgType.label,
) {
    val c = Eg.colors
    val target = (value.coerceIn(0, max).toFloat() / max).coerceIn(0f, 1f)
    val sweep by animateFloatAsState(target, tween(EG_ANIM_MS), label = "ringSweep")
    val shown by animateIntAsState(value.coerceIn(0, max), tween(EG_ANIM_MS), label = "ringNumber")
    val arc by animateColorAsState(color, tween(EG_ANIM_MS), label = "ringColor")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)
            val w = stroke.toPx()
            val tickLong = if (ticks) r * 0.075f else 0f
            val tickShort = tickLong * 0.5f
            val gap = if (ticks) r * 0.06f else 0f
            val ringR = r - tickLong - gap - w * 1.1f // room for the widest glow pass
            if (ticks) {
                val tw = (r * 0.012f).coerceAtLeast(1f)
                for (i in 0 until 60) {
                    val a = i * 2 * PI / 60 - PI / 2
                    val len = if (i % 5 == 0) tickLong else tickShort
                    val outer = r - (tickLong - len) / 2
                    val inner = outer - len
                    drawLine(
                        c.tick,
                        Offset(center.x + (cos(a) * inner).toFloat(), center.y + (sin(a) * inner).toFloat()),
                        Offset(center.x + (cos(a) * outer).toFloat(), center.y + (sin(a) * outer).toFloat()),
                        tw, StrokeCap.Round,
                    )
                }
            }
            val topLeft = Offset(center.x - ringR, center.y - ringR)
            val arcSize = Size(ringR * 2, ringR * 2)
            drawCircle(c.track, ringR, center, style = Stroke(w))
            if (sweep > 0.003f) {
                val deg = 360f * sweep
                val glow = if (c.isDark) GLOW_ALPHA else GLOW_ALPHA * 0.6f
                for (i in 0 until GLOW_PASSES) { // widest first: 2.2x .. 1.15x the stroke
                    val width = w * (2.2f - i * (2.2f - 1.15f) / (GLOW_PASSES - 1))
                    drawArc(arc.copy(alpha = glow), -90f, deg, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
                }
                drawArc(arc, -90f, deg, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number ?: "$shown", style = numberStyle, color = c.text, maxLines = 1)
            Text(unit, style = unitStyle, color = c.label, maxLines = 1)
        }
    }
}

/** Glow passes of [Ring] and the alpha of each (they stack: ~0.13 next to the arc, 0.025 at the outer edge). */
private const val GLOW_PASSES = 5
private const val GLOW_ALPHA = 0.026f

/**
 * Sparkline of the last values (oldest first): a 2 dp line, no axes. Values are scaled to their own min..max
 * (flat when constant). Nothing drawn with fewer than 2 points.
 */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier, stroke: Dp = 2.dp) {
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(color.copy(alpha = 0.35f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
            return@Canvas
        }
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        val w = stroke.toPx()
        val h = size.height - w
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = if (hi - lo < 1e-9) size.height / 2 else w / 2 + (h * (1 - (v - lo) / span)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, color, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/**
 * Energy through the day: a lime line over an area fading to transparent, 0..100 scale with three faint grid lines.
 * [values] oldest first.
 */
@Composable
fun AreaChart(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    val c = Eg.colors
    Canvas(modifier) {
        val w = 2.5.dp.toPx()
        for (g in listOf(0.25f, 0.5f, 0.75f)) {
            drawLine(c.tick.copy(alpha = c.tick.alpha * 0.6f), Offset(0f, size.height * g), Offset(size.width, size.height * g), 1.dp.toPx())
        }
        val pts = if (values.size == 1) listOf(values[0], values[0]) else values
        if (pts.isEmpty()) return@Canvas
        fun x(i: Int) = size.width * i / (pts.size - 1)
        fun y(v: Int) = w / 2 + (size.height - w) * (1 - v.coerceIn(0, 100) / 100f)
        val line = Path().apply {
            pts.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(pts.size - 1), size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(w, cap = StrokeCap.Round))
        drawCircle(color, w * 1.6f, Offset(x(pts.size - 1), y(pts.last())))
    }
}
