package ch.lauzhack.energyguard.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import ch.lauzhack.energyguard.ui.components.DarkColors
import ch.lauzhack.energyguard.ui.components.EgColors
import ch.lauzhack.energyguard.ui.components.LightColors

/** One full set of app colors. */
data class Palette(
    val night: Color,    // page background (flat)
    val mauve: Color,    // background, gauge side (left)
    val deep: Color,     // background, tiles side (right)
    val surface: Color,  // cards
    val track: Color,    // empty part of the gauge / chart grid
    val text: Color,
    val muted: Color,    // secondary text
    val faint: Color,    // hints, "no data"
    val honey: Color,    // 75-100 % energy
    val apricot: Color,  // 50-75 %
    val coral: Color,    // 25-50 %
    val rose: Color,     // 0-25 % and EXHAUSTED: a clear red
    val sage: Color,     // a signal that looks normal
    val calm: Color,     // calibration / informational
    val brand: Color,    // structure only (logo, header): never an energy level
)

/**
 * Dark palette, mapped onto the M2 design tokens (ui/components/Tokens.kt) so the break overlay, the inbox and the
 * Android dashboard match the new shell: flat background, energy scale = level colors (green, teal, amber, red).
 */
val EveningPalette = DarkColors.toPalette(faint = Color(0xFF7A8292))

/** Light palette, same mapping (state colors already darkened for contrast on white). */
val DaylightPalette = LightColors.toPalette(faint = Color(0xFF6B7482))

private fun EgColors.toPalette(faint: Color) = Palette(
    night = background, mauve = background, deep = background,
    surface = surface, track = track,
    text = text, muted = muted, faint = faint,
    honey = fresh, apricot = ok, coral = tired, rose = exhausted,
    sage = fresh, calm = accent, brand = accent,
)

/**
 * The app's colors. Every value reads the current palette through Compose state, so flipping [light]
 * recomposes the whole UI (dashboard, alerts, inbox) without passing a theme around.
 */
object Warm {
    var light by mutableStateOf(false)
    private val p: Palette get() = if (light) DaylightPalette else EveningPalette

    val Night: Color get() = p.night
    val Mauve: Color get() = p.mauve
    val Deep: Color get() = p.deep
    val Surface: Color get() = p.surface
    val Track: Color get() = p.track
    val Text: Color get() = p.text
    val Muted: Color get() = p.muted
    val Faint: Color get() = p.faint
    val Honey: Color get() = p.honey
    val Apricot: Color get() = p.apricot
    val Coral: Color get() = p.coral
    val Rose: Color get() = p.rose
    val Sage: Color get() = p.sage
    val Calm: Color get() = p.calm
    val Brand: Color get() = p.brand
}
