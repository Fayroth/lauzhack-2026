package ch.lauzhack.energyguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.ui.brand.EnergyGuardLogo
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgIcon
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgTheme
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.Icon
import ch.lauzhack.energyguard.ui.components.Pill
import ch.lauzhack.energyguard.ui.components.PlaceholderScreen
import ch.lauzhack.energyguard.ui.today.TodayActions
import ch.lauzhack.energyguard.ui.today.TodayScreen

/** The 3 tabs; keys 1 / 2 / 3 select them (main.kt). */
enum class Tab(val label: String, val icon: EgIcon) {
    TODAY("Today", EgIcon.TODAY),
    JOURNAL("Journal", EgIcon.JOURNAL),
    PROFILE("Profile", EgIcon.PROFILE),
}

/**
 * Desktop shell (M2): navigation rail on the left, header / tab content / footer on the right, all driven by [state].
 * [overlay] is drawn over everything (break alert). Theme = Settings.theme (the old Warm palette follows it).
 */
@Composable
fun DesktopShell(
    state: AppState,
    tab: Tab,
    onTab: (Tab) -> Unit,
    today: TodayActions,
    onToggleTheme: () -> Unit,
    footer: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val light = state.settings.theme == ThemeMode.LIGHT
    SideEffect { Warm.light = light } // break overlay + inbox window still read Warm
    DesignScale { ShellContent(state, tab, onTab, today, onToggleTheme, footer, overlay) }
}

/** Design size of the content area (1440x900 window minus the title bar). */
private val DESIGN_W = 1440.dp
private val DESIGN_H = 860.dp

/**
 * Lays the UI out as if the window were [DESIGN_W] x [DESIGN_H] and scales it uniformly to the real window
 * (smaller laptop screens shrink, a maximized window on a projector grows), so proportions never break.
 */
@Composable
private fun DesignScale(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val f = minOf(maxWidth / DESIGN_W, maxHeight / DESIGN_H).coerceIn(0.5f, 2f)
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density * f, d.fontScale)) { content() }
    }
}

@Composable
private fun ShellContent(
    state: AppState,
    tab: Tab,
    onTab: (Tab) -> Unit,
    today: TodayActions,
    onToggleTheme: () -> Unit,
    footer: String?,
    overlay: @Composable BoxScope.() -> Unit,
) {
    EgTheme(state.settings.theme) {
        val c = Eg.colors
        Box(Modifier.fillMaxSize().background(c.background)) {
            Row(Modifier.fillMaxSize()) {
                Rail(tab, onTab)
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = EgSpace.l, vertical = EgSpace.m)) {
                    Header(state, onToggleTheme)
                    Spacer(Modifier.height(EgSpace.m))
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (tab) {
                            Tab.TODAY -> TodayScreen(state, today)
                            Tab.JOURNAL -> PlaceholderScreen(
                                EgIcon.JOURNAL, "Journal",
                                "Quick check-ins (sleepiness, stress, coffee) next to your measured concentration.",
                            )
                            Tab.PROFILE -> PlaceholderScreen(
                                EgIcon.PROFILE, "Profile",
                                "Your personal baseline: what normal looks like for your eyes, mouse and typing.",
                            )
                        }
                    }
                    if (footer != null) {
                        Spacer(Modifier.height(EgSpace.xs))
                        Text(footer, style = EgType.label, color = c.muted)
                    }
                }
            }
            overlay()
        }
    }
}

@Composable
private fun Rail(tab: Tab, onTab: (Tab) -> Unit) {
    val c = Eg.colors
    NavigationRail(
        Modifier.fillMaxHeight().width(104.dp),
        containerColor = c.surface,
    ) {
        Spacer(Modifier.height(EgSpace.l))
        Column(verticalArrangement = Arrangement.spacedBy(EgSpace.s)) {
            Tab.entries.forEach { t ->
                val selected = t == tab
                NavigationRailItem(
                    selected = selected,
                    onClick = { onTab(t) },
                    icon = { Icon(t.icon, if (selected) c.accent else c.muted, size = 28.dp) },
                    label = { Text(t.label, style = EgType.label) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedTextColor = c.text, unselectedTextColor = c.muted,
                        indicatorColor = c.accent.copy(alpha = if (c.isDark) 0.18f else 0.12f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun Header(state: AppState, onToggleTheme: () -> Unit) {
    val c = Eg.colors
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        EnergyGuardLogo(state.dashboard.fatigue, Modifier.height(24.dp).width(44.dp))
        Spacer(Modifier.width(12.dp))
        Text("EnergyGuard", style = EgType.title, color = c.text)
        Spacer(Modifier.weight(1f))
        if (state.demo) {
            Pill("DEMO", c.accent, filled = true)
            Spacer(Modifier.width(EgSpace.xs))
        }
        Row(
            Modifier.background(c.raised, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EgIcon.LOCK, c.muted, size = 16.dp)
            Spacer(Modifier.width(EgSpace.xs))
            Text("Processed locally", style = EgType.label, color = c.muted)
        }
        Spacer(Modifier.width(EgSpace.xs))
        val light = state.settings.theme == ThemeMode.LIGHT
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(c.raised).clickable(onClick = onToggleTheme)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (light) EgIcon.MOON else EgIcon.SUN, c.muted, size = 16.dp)
            Spacer(Modifier.width(EgSpace.xs))
            Text(if (light) "Dark mode" else "Light mode", style = EgType.label, color = c.muted)
        }
    }
}
