package ch.lauzhack.energyguard.ui.brand

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.ui.Warm
import ch.lauzhack.energyguard.ui.energyColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/*
 * EnergyGuard logo: a horizontal battery filled with liquid that rocks like water in a bottle,
 * with a wave that builds on one side and rolls back. Drawn on a 64x64 grid.
 * The color follows the user's state (honey when fresh ... rose when exhausted, calm blue while calibrating).
 */

// Inner (liquid) area of the battery, on the 64x64 grid.
private const val IX = 9.5f
private const val IY = 21.5f
private const val IW = 38f
private const val IH = 21f
private const val LEVEL = IY + IH * 0.58f // fixed fill level

/** One full loop of the animation; a multiple of both the rocking (2.6 s) and ripple (0.8 s) periods. */
private const val LOOP_S = 10.4f

/** Liquid surface height at [x] and time [t] (seconds). */
private fun surface(x: Float, level: Float, t: Float): Float {
    val u = (x - (IX + IW / 2)) / (IW / 2)                 // -1..1 across the battery
    val tilt = sin(2 * PI * t / 2.6).toFloat()             // the liquid rocks side to side
    val pos = 0.5f * tilt                                  // the big wave travels with it
    val d = u - pos
    val dir = if (cos(2 * PI * t / 2.6) >= 0) 1f else -1f
    val width = if (d * dir > 0) 0.22f else 0.55f          // steep front, long back: a breaking wave
    val crest = (4.5f + 3f * abs(tilt)) * exp(-(d * d) / (width * width))
    val e = u - pos + 0.6f * dir
    val trough = 1.8f * exp(-(e * e) / 0.08f)
    val ripple = 0.45f * sin(7 * u - 2 * PI * t / 0.8).toFloat()
    return level - tilt * 2.5f * u - crest + trough + ripple
}

private fun surfacePath(level: Float, t: Float, closeToBottom: Boolean): Path = Path().apply {
    var x = IX - 1
    moveTo(x, surface(x, level, t))
    while (x < IX + IW + 1) {
        x += 0.5f
        lineTo(x, surface(x, level, t))
    }
    if (closeToBottom) {
        lineTo(IX + IW + 1, IY + IH + 2)
        lineTo(IX - 1, IY + IH + 2)
        close()
    }
}

/** Animated logo for the app header, tinted by the current energy level. */
@Composable
fun EnergyGuardLogo(fatigue: FatigueState, modifier: Modifier = Modifier) {
    val target = if (fatigue.calibrated) energyColor(fatigue.energy) else Warm.Calm
    val color by animateColorAsState(target, tween(1200), label = "logoColor")
    val t by rememberInfiniteTransition(label = "liquid").animateFloat(
        initialValue = 0f,
        targetValue = LOOP_S,
        animationSpec = infiniteRepeatable(tween((LOOP_S * 1000).toInt(), easing = LinearEasing)),
        label = "t",
    )
    Canvas(modifier) {
        val s = size.minDimension / 64f
        scale(s, s, pivot = Offset.Zero) {
            val inner = Path().apply { addRoundRect(RoundRect(IX, IY, IX + IW, IY + IH, CornerRadius(4.5f))) }
            clipPath(inner) {
                drawPath(surfacePath(LEVEL - 1.2f, t - 0.25f, true), color.copy(alpha = 0.35f))
                drawPath(
                    surfacePath(LEVEL, t, true),
                    Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color), startY = IY, endY = IY + IH),
                )
                // Foam line along the surface.
                drawPath(surfacePath(LEVEL, t, false), Color.White.copy(alpha = 0.55f), style = Stroke(1.6f, cap = StrokeCap.Round))
            }
            // Battery body and terminal.
            drawRoundRect(color, Offset(6f, 18f), Size(45f, 28f), CornerRadius(7.5f), style = Stroke(4f))
            drawRoundRect(color, Offset(52.5f, 26.5f), Size(5f, 11f), CornerRadius(2.2f))
        }
    }
}

/**
 * Static version (one frame, brand green) for the window / dock icon, which cannot animate.
 * [background] fills a rounded square behind it so it shows on light and dark docks.
 */
fun energyGuardIcon(color: Color = Warm.Brand, background: Color? = Warm.Night): ImageVector =
    ImageVector.Builder("EnergyGuardIcon", 64.dp, 64.dp, 64f, 64f).apply {
        if (background != null) path(fill = SolidColor(background)) { roundRect(0f, 0f, 64f, 64f, 14f) }
        val t = 0.65f // a frame where the wave is nicely visible
        path(fill = SolidColor(color), fillAlpha = 0.9f) {
            var x = IX + 1.5f
            moveTo(x, surface(x, LEVEL, t).coerceAtLeast(IY + 1))
            while (x < IX + IW - 1.5f) {
                x += 0.5f
                lineTo(x, surface(x, LEVEL, t).coerceAtLeast(IY + 1))
            }
            lineTo(IX + IW - 1.5f, IY + IH - 1.5f)
            lineTo(IX + 1.5f, IY + IH - 1.5f)
            close()
        }
        path(stroke = SolidColor(color), strokeLineWidth = 4f) { roundRect(6f, 18f, 51f, 46f, 7.5f) }
        path(fill = SolidColor(color)) { roundRect(52.5f, 26.5f, 57.5f, 37.5f, 2.2f) }
    }.build()

private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float) {
    moveTo(l + radius, t)
    lineTo(r - radius, t); quadTo(r, t, r, t + radius)
    lineTo(r, b - radius); quadTo(r, b, r - radius, b)
    lineTo(l + radius, b); quadTo(l, b, l, b - radius)
    lineTo(l, t + radius); quadTo(l, t, l + radius, t)
    close()
}
