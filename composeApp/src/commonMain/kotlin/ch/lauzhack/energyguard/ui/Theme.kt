package ch.lauzhack.energyguard.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

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
 * Dark "evening" palette: a soft indigo night instead of pure black, energy from sunlit honey (fresh)
 * to red (exhausted) instead of a traffic light.
 */
val EveningPalette = Palette(
    night = Color(0xFF1D1A2C), mauve = Color(0xFF2A2140), deep = Color(0xFF16142A),
    surface = Color(0xFF282439), track = Color(0xFF353049),
    text = Color(0xFFF4EEE6), muted = Color(0xFFA9A3BD), faint = Color(0xFF6E6886),
    honey = Color(0xFFF7C35F), apricot = Color(0xFFF29E5C), coral = Color(0xFFEE6F5E), rose = Color(0xFFE5484D),
    sage = Color(0xFF93CFA0), calm = Color(0xFFA8B8F0), brand = Color(0xFF4CC38A),
)

/**
 * Light "health" palette (white with a hint of green). Same energy scale, darkened so every color
 * keeps >= 4:1 contrast on the white background (checked against WCAG; big text needs 3:1).
 */
val DaylightPalette = Palette(
    night = Color(0xFFF7FBF8), mauve = Color(0xFFEAF4EE), deep = Color(0xFFFAFCFB),
    surface = Color(0xFFFFFFFF), track = Color(0xFFE3ECE6),
    text = Color(0xFF1B2A22), muted = Color(0xFF55655C), faint = Color(0xFF6F7D75),
    honey = Color(0xFFA86A00), apricot = Color(0xFFC2571A), coral = Color(0xFFC8402F), rose = Color(0xFFB3261E),
    sage = Color(0xFF2B7A4B), calm = Color(0xFF3D57B8), brand = Color(0xFF1F7A47),
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
