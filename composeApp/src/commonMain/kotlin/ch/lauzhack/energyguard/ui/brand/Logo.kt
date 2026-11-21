package ch.lauzhack.energyguard.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.ui.Warm

/**
 * EnergyGuard logo: a smiling computer screen flexing two little arms ("your computer has your back").
 * Line icon on a 64x64 grid, round caps, constant stroke, two colors (screen in [screen], arms in [arms]).
 * Pure vector (no image file), so it works on Desktop and Android and stays sharp from 16 to 256 px.
 * [background], if set, fills a rounded square behind it: used for the window/dock icon, where the
 * desktop behind it can be light or dark.
 */
fun energyGuardLogo(screen: Color, arms: Color, background: Color? = null): ImageVector {
    return ImageVector.Builder("EnergyGuardLogo", 64.dp, 64.dp, 64f, 64f).apply {
        if (background != null) {
            path(fill = SolidColor(background)) { roundedRect(0f, 0f, 64f, 64f, 14f) }
        }
        // Screen, stand and base.
        line(screen) { roundedRect(17f, 13f, 47f, 39f, 4f) }
        line(screen) { moveTo(32f, 39f); lineTo(32f, 46f); moveTo(25f, 47f); lineTo(39f, 47f) }
        // Face: two eyes and a smile, so it reads as friendly even at small sizes.
        line(screen) { moveTo(27f, 23f); lineTo(27f, 23.5f); moveTo(37f, 23f); lineTo(37f, 23.5f) }
        line(screen) { moveTo(26.5f, 29f); quadTo(32f, 34f, 37.5f, 29f) }
        // Left arm: upper arm out to the elbow, forearm up, bicep bump on top, fist.
        line(arms) {
            moveTo(17f, 31f); lineTo(10f, 31f); quadTo(6f, 31f, 6f, 27f); lineTo(6f, 22f)
            moveTo(17f, 27f); quadTo(13f, 22.5f, 9.5f, 25.5f)
        }
        path(fill = SolidColor(arms)) { circle(6f, 18.5f, 4f) }
        // Right arm: mirror image.
        line(arms) {
            moveTo(47f, 31f); lineTo(54f, 31f); quadTo(58f, 31f, 58f, 27f); lineTo(58f, 22f)
            moveTo(47f, 27f); quadTo(51f, 22.5f, 54.5f, 25.5f)
        }
        path(fill = SolidColor(arms)) { circle(58f, 18.5f, 4f) }
    }.build()
}

/** The logo in the app's colors: brand green screen, warm white arms. */
@Composable
fun EnergyGuardLogo(modifier: Modifier = Modifier) {
    val vector = remember { energyGuardLogo(screen = Warm.Brand, arms = Warm.Text) }
    Image(rememberVectorPainter(vector), contentDescription = "EnergyGuard logo", modifier = modifier)
}

/** One stroked shape of the logo: same width, round caps and joins everywhere. */
private fun ImageVector.Builder.line(color: Color, block: PathBuilder.() -> Unit) = path(
    stroke = SolidColor(color), strokeLineWidth = 3.5f,
    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
)

private fun PathBuilder.roundedRect(l: Float, t: Float, r: Float, b: Float, radius: Float) {
    moveTo(l + radius, t)
    lineTo(r - radius, t); quadTo(r, t, r, t + radius)
    lineTo(r, b - radius); quadTo(r, b, r - radius, b)
    lineTo(l + radius, b); quadTo(l, b, l, b - radius)
    lineTo(l, t + radius); quadTo(l, t, l + radius, t)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    moveTo(cx - radius, cy)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + radius, y1 = cy)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - radius, y1 = cy)
    close()
}
