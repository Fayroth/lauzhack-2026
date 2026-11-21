package ch.lauzhack.energyguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Line icons drawn on a 24-unit grid (no icon dependency). */
enum class EgIcon { TODAY, JOURNAL, PROFILE, LOCK, SUN, MOON }

@Composable
fun Icon(icon: EgIcon, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val line = Stroke(width = 2f * u, cap = StrokeCap.Round)
        when (icon) {
            EgIcon.TODAY -> { // a small gauge
                drawArc(color, 135f, 270f, false, Offset(3 * u, 3 * u), Size(18 * u, 18 * u), style = line)
                needle(color, u, angleDeg = -45.0)
            }
            EgIcon.JOURNAL -> { // a notebook with lines
                drawRoundRect(color, Offset(5 * u, 3 * u), Size(14 * u, 18 * u), CornerRadius(2.5f * u), style = line)
                listOf(8f, 12f, 16f).forEach { y -> drawLine(color, Offset(9 * u, y * u), Offset(15 * u, y * u), 2f * u, StrokeCap.Round) }
            }
            EgIcon.PROFILE -> { // head + shoulders
                drawCircle(color, 4 * u, Offset(12 * u, 8 * u), style = line)
                drawArc(color, 200f, 140f, false, Offset(4 * u, 14 * u), Size(16 * u, 12 * u), style = line)
            }
            EgIcon.LOCK -> {
                drawRoundRect(color, Offset(5 * u, 11 * u), Size(14 * u, 10 * u), CornerRadius(2 * u), style = line)
                drawArc(color, 180f, 180f, false, Offset(8 * u, 4 * u), Size(8 * u, 10 * u), style = line)
            }
            EgIcon.SUN -> {
                drawCircle(color, 4 * u, Offset(12 * u, 12 * u), style = line)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    drawLine(
                        color,
                        Offset((12 + 7 * cos(a)).toFloat() * u, (12 + 7 * sin(a)).toFloat() * u),
                        Offset((12 + 9.5 * cos(a)).toFloat() * u, (12 + 9.5 * sin(a)).toFloat() * u),
                        2f * u, StrokeCap.Round,
                    )
                }
            }
            EgIcon.MOON -> { // crescent = disc minus an offset disc, outlined
                val disc = Path().apply { addOval(Rect(Offset(12 * u, 12 * u), 8 * u)) }
                val bite = Path().apply { addOval(Rect(Offset(17 * u, 8 * u), 7 * u)) }
                drawPath(Path.combine(PathOperation.Difference, disc, bite), color, style = line)
            }
        }
    }
}

private fun DrawScope.needle(color: Color, u: Float, angleDeg: Double) {
    val a = angleDeg * PI / 180
    drawLine(color, Offset(12 * u, 12 * u), Offset((12 + 6 * cos(a)).toFloat() * u, (12 + 6 * sin(a)).toFloat() * u), 2f * u, StrokeCap.Round)
}
