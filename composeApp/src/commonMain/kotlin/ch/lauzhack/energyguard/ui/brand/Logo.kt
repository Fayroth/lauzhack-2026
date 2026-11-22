package ch.lauzhack.energyguard.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.ui.components.DarkColors
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgType

/*
 * EnergyGuard logo (C2): a rounded lime shield with a ring cut out of its middle (the ring = our signature), drawn on
 * a 64x64 grid. The cut-out is real (BlendMode.Clear on an offscreen layer), so it shows whatever is behind.
 */

private const val RING_X = 32f
private const val RING_Y = 29f
private const val RING_R = 10.5f
private const val RING_W = 5f

/** Shield outline on the 64 grid: flat rounded top, straight sides, a soft point at the bottom. */
private fun shieldPath(): Path = Path().apply {
    moveTo(18f, 6f)
    lineTo(46f, 6f)
    quadraticTo(54f, 6f, 54f, 14f)
    lineTo(54f, 30f)
    cubicTo(54f, 45f, 43f, 54f, 33.5f, 58.5f)
    quadraticTo(32f, 59.2f, 30.5f, 58.5f)
    cubicTo(21f, 54f, 10f, 45f, 10f, 30f)
    lineTo(10f, 14f)
    quadraticTo(10f, 6f, 18f, 6f)
    close()
}

/** The shield mark, in the brand lime of the current theme. */
@Composable
fun EnergyGuardLogo(modifier: Modifier = Modifier, size: Dp = 28.dp, color: Color = Eg.colors.accent) {
    Canvas(modifier.size(size).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val s = this.size.minDimension / 64f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(shieldPath(), color)
            drawCircle(Color.Black, RING_R, Offset(RING_X, RING_Y), style = Stroke(RING_W), blendMode = BlendMode.Clear)
        }
    }
}

/** Shield + "EnergyGuard" in Bricolage 700. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        EnergyGuardLogo(size = size)
        Spacer(Modifier.width(10.dp))
        Text("EnergyGuard", style = EgType.wordmark, color = Eg.colors.text, maxLines = 1)
    }
}

/**
 * Static version for the window / dock icon: lime shield on a near-black rounded square; the ring is painted in the
 * square's colour (an ImageVector cannot clear).
 */
fun energyGuardIcon(color: Color = DarkColors.accent, background: Color = DarkColors.background): ImageVector =
    ImageVector.Builder("EnergyGuardIcon", 64.dp, 64.dp, 64f, 64f).apply {
        path(fill = SolidColor(background)) { roundRect(0f, 0f, 64f, 64f, 14f) }
        path(fill = SolidColor(color)) {
            moveTo(18f, 6f); lineTo(46f, 6f); quadTo(54f, 6f, 54f, 14f); lineTo(54f, 30f)
            curveTo(54f, 45f, 43f, 54f, 33.5f, 58.5f); quadTo(32f, 59.2f, 30.5f, 58.5f)
            curveTo(21f, 54f, 10f, 45f, 10f, 30f); lineTo(10f, 14f); quadTo(10f, 6f, 18f, 6f); close()
        }
        path(stroke = SolidColor(background), strokeLineWidth = RING_W) {
            moveTo(RING_X + RING_R, RING_Y)
            arcTo(RING_R, RING_R, 0f, true, true, RING_X - RING_R, RING_Y)
            arcTo(RING_R, RING_R, 0f, true, true, RING_X + RING_R, RING_Y)
            close()
        }
    }.build()

private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float) {
    moveTo(l + radius, t)
    lineTo(r - radius, t); quadTo(r, t, r, t + radius)
    lineTo(r, b - radius); quadTo(r, b, r - radius, b)
    lineTo(l + radius, b); quadTo(l, b, l, b - radius)
    lineTo(l, t + radius); quadTo(l, t, l + radius, t)
    close()
}
