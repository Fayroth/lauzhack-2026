package ch.lauzhack.energyguard.ui.components

import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.model.Level

/**
 * Design tokens of the app (M2), shared by the desktop shell and Android (M3).
 * Rules: one accent color; level colors only for state (FRESH green, OK teal, TIRED amber, EXHAUSTED red);
 * 8 dp grid, 24 dp card padding, 16 dp radius, no borders (elevation instead); nothing under 14 sp.
 */
@Immutable
data class EgColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,   // cards
    val raised: Color,    // chips, tracks inside cards
    val track: Color,     // empty part of gauge / battery
    val text: Color,
    val muted: Color,
    val accent: Color,    // the only non-state color: selection, primary button, LLM counter
    val onAccent: Color,
    val fresh: Color,
    val ok: Color,
    val tired: Color,
    val exhausted: Color,
) {
    fun level(level: Level): Color = when (level) {
        Level.FRESH -> fresh
        Level.OK -> ok
        Level.TIRED -> tired
        Level.EXHAUSTED -> exhausted
    }

    /** A 0..100 value (concentration, battery) on the same scale as the engine's levels (bounds 25 / 50 / 75). */
    fun forValue(value: Int): Color = when {
        value >= 75 -> fresh
        value >= 50 -> ok
        value >= 25 -> tired
        else -> exhausted
    }
}

/** Dark is the default (projector). Near-black blue-grey, not pure black. */
val DarkColors = EgColors(
    isDark = true,
    background = Color(0xFF0F1217),
    surface = Color(0xFF181C23),
    raised = Color(0xFF222731),
    track = Color(0xFF2B313C),
    text = Color(0xFFF1F3F6),
    muted = Color(0xFFA0A8B6),
    accent = Color(0xFF8B95FF),
    onAccent = Color(0xFF0F1217),
    fresh = Color(0xFF3DD68C),
    ok = Color(0xFF2EC4C4),
    tired = Color(0xFFF5B545),
    exhausted = Color(0xFFFF5A5F),
)

/** Light: state colors darkened to keep >= 3:1 on white for big text. */
val LightColors = EgColors(
    isDark = false,
    background = Color(0xFFF3F5F8),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFEDF0F4),
    track = Color(0xFFE2E6EC),
    text = Color(0xFF131720),
    muted = Color(0xFF5A6372),
    accent = Color(0xFF4B55D1),
    onAccent = Color(0xFFFFFFFF),
    fresh = Color(0xFF1A9A5B),
    ok = Color(0xFF0F8A8C),
    tired = Color(0xFFB06D00),
    exhausted = Color(0xFFD32F3A),
)

/** Type scale (strict): gauge 96, card values 48, titles 20 semibold, body 16, labels 14. One family. */
object EgType {
    private val family = FontFamily.Default
    val gauge = TextStyle(fontFamily = family, fontSize = 96.sp, fontWeight = FontWeight.SemiBold, lineHeight = 100.sp)
    val value = TextStyle(fontFamily = family, fontSize = 48.sp, fontWeight = FontWeight.SemiBold, lineHeight = 56.sp)
    val title = TextStyle(fontFamily = family, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
    val lead = TextStyle(fontFamily = family, fontSize = 20.sp, fontWeight = FontWeight.Normal, lineHeight = 28.sp)
    val body = TextStyle(fontFamily = family, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val label = TextStyle(fontFamily = family, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
}

/** 8 dp grid. */
object EgSpace {
    val xs = 8.dp
    val s = 16.dp
    val m = 24.dp
    val l = 32.dp
    val xl = 48.dp
    val cardPadding = 24.dp
    val radius = 16.dp
}

/** Value animations (gauge, battery): 300-500 ms. */
const val EG_ANIM_MS = 450

val LocalEgColors = staticCompositionLocalOf { DarkColors }

/** Current tokens: `Eg.colors.accent`, ... */
object Eg {
    val colors: EgColors
        @Composable get() = LocalEgColors.current
}

fun colorsFor(mode: ThemeMode): EgColors = if (mode == ThemeMode.LIGHT) LightColors else DarkColors

/** Root theme: provides [EgColors] and a matching Material 3 scheme (rail, buttons, slider). */
@Composable
fun EgTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val c = colorsFor(mode)
    val scheme = if (c.isDark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.raised, onSurfaceVariant = c.muted,
            secondaryContainer = c.accent.copy(alpha = 0.18f), onSecondaryContainer = c.text,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.raised, onSurfaceVariant = c.muted,
            secondaryContainer = c.accent.copy(alpha = 0.14f), onSecondaryContainer = c.text,
        )
    }
    CompositionLocalProvider(LocalEgColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
