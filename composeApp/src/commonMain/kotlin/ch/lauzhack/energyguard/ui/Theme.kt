package ch.lauzhack.energyguard.ui

import androidx.compose.ui.graphics.Color

/**
 * "Evening" palette: a soft indigo night instead of pure black, and energy that goes from
 * sunlit honey (fresh) to deep rose (exhausted) instead of a traffic light. Warm and calm,
 * but every step stays clearly distinct on a projector.
 */
object Warm {
    val Night = Color(0xFF1D1A2C)      // page background
    val Surface = Color(0xFF282439)    // cards
    val Track = Color(0xFF353049)      // empty part of the gauge / chart grid
    val Text = Color(0xFFF4EEE6)       // warm white
    val Muted = Color(0xFFA9A3BD)      // secondary text
    val Faint = Color(0xFF6E6886)      // hints, "no data"

    val Honey = Color(0xFFF7C35F)      // 75-100 % energy
    val Apricot = Color(0xFFF29E5C)    // 50-75 %
    val Coral = Color(0xFFEE6F5E)      // 25-50 %
    val Rose = Color(0xFFE04E78)       // 0-25 %
    val Sage = Color(0xFF93CFA0)       // a signal that looks normal
    val Calm = Color(0xFFA8B8F0)       // calibration / informational
}
