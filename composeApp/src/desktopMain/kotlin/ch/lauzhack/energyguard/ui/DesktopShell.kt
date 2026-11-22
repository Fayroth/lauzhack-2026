package ch.lauzhack.energyguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.ui.brand.Wordmark
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgIcon
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgTheme
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.Icon
import ch.lauzhack.energyguard.ui.components.Pill
import ch.lauzhack.energyguard.ui.journal.JournalActions
import ch.lauzhack.energyguard.ui.journal.JournalScreen
import ch.lauzhack.energyguard.ui.profile.ProfileScreen
import ch.lauzhack.energyguard.ui.today.TodayActions
import ch.lauzhack.energyguard.ui.today.TodayScreen
import ch.lauzhack.energyguard.ui.today.liveSignals

/**
 * Desktop shell (C2): header (wordmark | segmented tabs | status pill + theme toggle) over the tab content, all driven
 * by [state]. [overlay] is drawn over everything (break alert). Theme = Settings.theme (the old Warm palette follows).
 * [pairing] = the phone companion's "address · code" line (Profile tab only), null when the companion is off.
 */
@Composable
fun DesktopShell(
    state: AppState,
    tab: Tab,
    onTab: (Tab) -> Unit,
    today: TodayActions,
    onToggleTheme: () -> Unit,
    pairing: String? = null,
    journal: JournalActions = JournalActions(),
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val light = state.settings.theme == ThemeMode.LIGHT
    SideEffect { Warm.light = light } // break overlay + inbox window still read Warm
    DesignScale { ShellContent(state, tab, onTab, today, journal, onToggleTheme, pairing, overlay) }
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
    journal: JournalActions,
    onToggleTheme: () -> Unit,
    pairing: String?,
    overlay: @Composable BoxScope.() -> Unit,
) {
    EgTheme(state.settings.theme) {
        val c = Eg.colors
        Box(Modifier.fillMaxSize().background(c.background)) {
            Column(Modifier.fillMaxSize().padding(horizontal = EgSpace.l, vertical = 20.dp)) {
                Header(state, tab, onTab, onToggleTheme)
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (tab) {
                        Tab.TODAY -> TodayScreen(state, today)
                        Tab.JOURNAL -> JournalScreen(state, journal, wide = true)
                        Tab.PROFILE -> ProfileScreen(state, today.onSleepHours, onToggleTheme, pairing)
                    }
                }
            }
            overlay()
        }
    }
}

@Composable
private fun Header(state: AppState, tab: Tab, onTab: (Tab) -> Unit, onToggleTheme: () -> Unit) {
    val c = Eg.colors
    Box(Modifier.fillMaxWidth().height(EgSpace.touch + 8.dp)) {
        Wordmark(Modifier.align(Alignment.CenterStart), size = 30.dp)
        SegmentedTabs(tab, onTab, Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            val source = state.liveSignals().source
            Pill(if (state.demo) "Demo data · on-device" else "Live · ${source ?: "webcam"} · on-device", dot = c.accent)
            Spacer(Modifier.width(12.dp))
            val light = state.settings.theme == ThemeMode.LIGHT
            Box(
                Modifier.size(EgSpace.touch).clip(CircleShape).background(c.raised).border(1.dp, c.border, CircleShape)
                    .clickable(onClick = onToggleTheme),
                contentAlignment = Alignment.Center,
            ) { Icon(if (light) EgIcon.MOON else EgIcon.SUN, c.muted, size = 20.dp) }
        }
    }
}

/** Today / Journal / Profile as a pill segmented control; selected = light pill with dark text. Keys 1 / 2 / 3 too. */
@Composable
private fun SegmentedTabs(tab: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = Eg.colors
    Row(
        modifier.background(c.raised, CircleShape).border(1.dp, c.border, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { t ->
            val selected = t == tab
            Box(
                Modifier.height(EgSpace.touch).clip(CircleShape)
                    .background(if (selected) c.text else c.raised)
                    .selectable(selected, role = Role.Tab) { onTab(t) }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.label, style = EgType.bodyStrong, color = if (selected) c.background else c.muted)
            }
        }
    }
}
