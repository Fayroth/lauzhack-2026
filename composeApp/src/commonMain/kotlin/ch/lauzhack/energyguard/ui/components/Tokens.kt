package ch.lauzhack.energyguard.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.resources.Res
import ch.lauzhack.energyguard.resources.bricolage_grotesque_bold
import ch.lauzhack.energyguard.resources.bricolage_grotesque_medium
import ch.lauzhack.energyguard.resources.geist_medium
import ch.lauzhack.energyguard.resources.geist_regular
import ch.lauzhack.energyguard.resources.geist_semibold
import org.jetbrains.compose.resources.Font

/**
 * Design tokens, locked design "C2" (M4.5): dark, premium, calm; three equal rings are the signature.
 * Brand lime = Energy ring, logo, primary button, live dot. Ice = Recovery ring. State colours (FRESH / OK / TIRED /
 * EXHAUSTED) = Concentration ring, state chip and reason numbers, nothing else. No other colours.
 */
@Immutable
data class EgColors(
    val isDark: Boolean,
    val background: Color,
    val cardTop: Color,     // card fill: vertical gradient cardTop -> cardBottom (flat white in light)
    val cardBottom: Color,
    val border: Color,      // 1 px card / pill border
    val highlight: Color,   // 1 px top inner highlight of cards (dark only)
    val raised: Color,      // chips, secondary buttons, segmented control
    val track: Color,       // empty ring / bar track
    val tick: Color,        // ring tick marks, chart grid
    val text: Color,
    val muted: Color,       // secondary text
    val label: Color,       // small labels
    val accent: Color,      // brand lime
    val onAccent: Color,    // text on lime (black)
    val ice: Color,         // Recovery ring
    val spark: Color,       // neutral sparklines (mouse, keys)
    val navBar: Color,      // Android bottom bar
    val fresh: Color,       // state colours as text (darker in light for 4.5:1)
    val ok: Color,
    val tired: Color,
    val exhausted: Color,
    val freshFill: Color,   // state colours as fills (chip background, under dark text)
    val okFill: Color,
    val tiredFill: Color,
    val exhaustedFill: Color,
) {
    /** Kept for the old palette mapping (break overlay, inbox): a flat card colour. */
    val surface: Color get() = cardTop

    fun level(level: Level): Color = when (level) {
        Level.FRESH -> fresh
        Level.OK -> ok
        Level.TIRED -> tired
        Level.EXHAUSTED -> exhausted
    }

    fun levelFill(level: Level): Color = when (level) {
        Level.FRESH -> freshFill
        Level.OK -> okFill
        Level.TIRED -> tiredFill
        Level.EXHAUSTED -> exhaustedFill
    }

    /** A 0..100 value (concentration) on the same scale as the engine's levels (bounds 25 / 50 / 75). */
    fun forValue(value: Int): Color = when {
        value >= 75 -> fresh
        value >= 50 -> ok
        value >= 25 -> tired
        else -> exhausted
    }
}

private val Fresh = Color(0xFF3DD68C)
private val Ok = Color(0xFF5EC8D8)
private val Tired = Color(0xFFFFB547)
private val Exhausted = Color(0xFFFF5C5C)

/** Dark is the default (projector). */
val DarkColors = EgColors(
    isDark = true,
    background = Color(0xFF09090B),
    cardTop = Color(0xFF16161A),
    cardBottom = Color(0xFF101013),
    border = Color.White.copy(alpha = 0.06f),
    highlight = Color.White.copy(alpha = 0.05f),
    raised = Color(0xFF131316),
    track = Color.White.copy(alpha = 0.06f),
    tick = Color.White.copy(alpha = 0.10f),
    text = Color(0xFFF4F4F5),
    muted = Color(0xFFA1A1AA),
    label = Color(0xFF8E8E98),
    accent = Color(0xFFC8F25C),
    onAccent = Color(0xFF09090B),
    ice = Color(0xFF9DB8FF),
    spark = Color(0xFF71717A),
    navBar = Color(0xFF0D0D10),
    fresh = Fresh, ok = Ok, tired = Tired, exhausted = Exhausted,
    freshFill = Fresh, okFill = Ok, tiredFill = Tired, exhaustedFill = Exhausted,
)

/** Light: same structure; state colours darkened for text (>= 4.5:1 on white), the bright ones stay as fills. */
val LightColors = EgColors(
    isDark = false,
    background = Color(0xFFF7F7F5),
    cardTop = Color(0xFFFFFFFF),
    cardBottom = Color(0xFFFFFFFF),
    border = Color(0xFFEAEAE7),
    highlight = Color.Transparent,
    raised = Color(0xFFF7F7F5),
    track = Color.Black.copy(alpha = 0.06f),
    tick = Color.Black.copy(alpha = 0.10f),
    text = Color(0xFF0B0B0D),
    muted = Color(0xFF5F5F68),
    label = Color(0xFF5F5F68),
    accent = Color(0xFF6B8E00),
    onAccent = Color(0xFF09090B),
    ice = Color(0xFF4C6FD8),
    spark = Color(0xFF71717A),
    navBar = Color(0xFFFFFFFF),
    fresh = Color(0xFF1B804F), ok = Color(0xFF217B89), tired = Color(0xFFA36100), exhausted = Color(0xFFD32F2F),
    freshFill = Fresh, okFill = Ok, tiredFill = Tired, exhaustedFill = Exhausted,
)

/** The two bundled families (Compose resources, OFL): Bricolage Grotesque for display, Geist for everything else. */
@Immutable
data class EgFonts(val display: FontFamily, val text: FontFamily)

@Composable
private fun loadFonts() = EgFonts(
    display = FontFamily(
        Font(Res.font.bricolage_grotesque_medium, FontWeight.Medium),
        Font(Res.font.bricolage_grotesque_bold, FontWeight.Bold),
    ),
    text = FontFamily(
        Font(Res.font.geist_regular, FontWeight.Normal),
        Font(Res.font.geist_medium, FontWeight.Medium),
        Font(Res.font.geist_semibold, FontWeight.SemiBold),
    ),
)

val LocalEgFonts = staticCompositionLocalOf { EgFonts(FontFamily.Default, FontFamily.Default) }

/**
 * Type scale. Bricolage: wordmark, screen titles, ring names, coach sentence. Geist: the rest, numbers tabular.
 * Nothing under 13 sp.
 */
object EgType {
    private val display: FontFamily @Composable get() = LocalEgFonts.current.display
    private val sans: FontFamily @Composable get() = LocalEgFonts.current.text

    @Composable private fun d(size: TextUnit, weight: FontWeight, line: TextUnit) =
        TextStyle(fontFamily = display, fontSize = size, fontWeight = weight, lineHeight = line)

    @Composable private fun t(size: TextUnit, weight: FontWeight, line: TextUnit, tabular: Boolean = false) =
        TextStyle(fontFamily = sans, fontSize = size, fontWeight = weight, lineHeight = line, fontFeatureSettings = if (tabular) "tnum" else null)

    // Bricolage
    val wordmark: TextStyle @Composable get() = d(22.sp, FontWeight.Bold, 28.sp)
    val screenTitle: TextStyle @Composable get() = d(32.sp, FontWeight.Bold, 38.sp)
    val ringName: TextStyle @Composable get() = d(20.sp, FontWeight.Medium, 24.sp)
    val coach: TextStyle @Composable get() = d(24.sp, FontWeight.Medium, 31.sp)
    val heading: TextStyle @Composable get() = d(24.sp, FontWeight.Bold, 30.sp)

    // Geist
    val gauge: TextStyle @Composable get() = t(44.sp, FontWeight.SemiBold, 48.sp, tabular = true)
    val value: TextStyle @Composable get() = t(40.sp, FontWeight.SemiBold, 46.sp, tabular = true)
    val stat: TextStyle @Composable get() = t(20.sp, FontWeight.SemiBold, 26.sp, tabular = true)
    val title: TextStyle @Composable get() = t(17.sp, FontWeight.SemiBold, 24.sp)
    val lead: TextStyle @Composable get() = t(18.sp, FontWeight.Normal, 26.sp)
    val body: TextStyle @Composable get() = t(16.sp, FontWeight.Normal, 24.sp, tabular = true)
    val bodyStrong: TextStyle @Composable get() = t(16.sp, FontWeight.SemiBold, 24.sp, tabular = true)
    val label: TextStyle @Composable get() = t(14.sp, FontWeight.Medium, 20.sp, tabular = true)
    val small: TextStyle @Composable get() = t(13.sp, FontWeight.Medium, 18.sp, tabular = true)
}

/** 8 dp grid; radius: hero 32, cards 28, pills 999. */
object EgSpace {
    val xs = 8.dp
    val s = 16.dp
    val m = 24.dp
    val l = 32.dp
    val xl = 48.dp
    val cardPadding = 24.dp
    val radius = 28.dp
    val heroRadius = 32.dp
    val touch = 44.dp
}

/** Value animations (rings, bars). */
const val EG_ANIM_MS = 400

val LocalEgColors = staticCompositionLocalOf { DarkColors }

/** Current tokens: `Eg.colors.accent`, ... */
object Eg {
    val colors: EgColors
        @Composable get() = LocalEgColors.current
}

fun colorsFor(mode: ThemeMode): EgColors = if (mode == ThemeMode.LIGHT) LightColors else DarkColors

/** Root theme: [EgColors], the bundled fonts, and a matching Material 3 scheme + typography (text fields, sliders). */
@Composable
fun EgTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val c = colorsFor(mode)
    val fonts = loadFonts()
    val base = if (c.isDark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.background, onBackground = c.text,
            surface = c.cardBottom, onSurface = c.text, surfaceVariant = c.raised, onSurfaceVariant = c.muted,
            outline = c.border, outlineVariant = c.border,
            secondaryContainer = c.accent.copy(alpha = 0.14f), onSecondaryContainer = c.text,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.background, onBackground = c.text,
            surface = c.cardTop, onSurface = c.text, surfaceVariant = c.raised, onSurfaceVariant = c.muted,
            outline = c.border, outlineVariant = c.border,
            secondaryContainer = c.accent.copy(alpha = 0.14f), onSecondaryContainer = c.text,
        )
    }
    val t = Typography()
    fun TextStyle.geist() = copy(fontFamily = fonts.text)
    val typography = Typography(
        displayLarge = t.displayLarge.copy(fontFamily = fonts.display), displayMedium = t.displayMedium.copy(fontFamily = fonts.display),
        displaySmall = t.displaySmall.copy(fontFamily = fonts.display), headlineLarge = t.headlineLarge.copy(fontFamily = fonts.display),
        headlineMedium = t.headlineMedium.copy(fontFamily = fonts.display), headlineSmall = t.headlineSmall.copy(fontFamily = fonts.display),
        titleLarge = t.titleLarge.geist(), titleMedium = t.titleMedium.geist(), titleSmall = t.titleSmall.geist(),
        // Nothing under 13 sp (M3 defaults go down to 11 sp: floating text field labels, supporting text).
        bodyLarge = t.bodyLarge.geist(), bodyMedium = t.bodyMedium.geist(), bodySmall = t.bodySmall.geist().copy(fontSize = 13.sp),
        labelLarge = t.labelLarge.geist(), labelMedium = t.labelMedium.geist().copy(fontSize = 13.sp),
        labelSmall = t.labelSmall.geist().copy(fontSize = 13.sp),
    )
    CompositionLocalProvider(LocalEgColors provides c, LocalEgFonts provides fonts) {
        MaterialTheme(colorScheme = base, typography = typography, content = content)
    }
}
