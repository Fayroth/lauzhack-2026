package ch.lauzhack.energyguard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Energy gauge: 270 degree arc, green to red. [energy] in 0..100. Grey while [calibrating]. */
@Composable
fun EnergyGauge(energy: Int, label: String, size: Dp = 360.dp, calibrating: Boolean = false) {
    val animated by animateFloatAsState(energy / 100f, tween(600), label = "energy")
    val color = if (calibrating) Color(0xFF616161) else energyColor(energy)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = 36.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color(0x22FFFFFF), startAngle = 135f, sweepAngle = 270f, useCenter = false, style = stroke)
            drawArc(color, startAngle = 135f, sweepAngle = 270f * animated, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (calibrating) "..." else "$energy%", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 28.sp, color = Color.White)
        }
    }
}

fun energyColor(energy: Int): Color = when {
    energy >= 75 -> Color(0xFF4CAF50)
    energy >= 50 -> Color(0xFFCDDC39)
    energy >= 25 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}
