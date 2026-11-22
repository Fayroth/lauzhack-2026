package ch.lauzhack.energyguard.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.ui.components.Divider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.ui.Tab
import ch.lauzhack.energyguard.ui.brand.EnergyGuardLogo
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgTheme
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.Icon
import ch.lauzhack.energyguard.ui.components.Pill
import ch.lauzhack.energyguard.ui.journal.JournalActions
import ch.lauzhack.energyguard.ui.journal.JournalScreen
import ch.lauzhack.energyguard.ui.today.SignalsSnapshot
import ch.lauzhack.energyguard.ui.today.TodayActions

/** The phone's link to the PC, as the header chip and the Today / Profile tabs show it. [pc] = "192.168.1.20:8767". */
sealed interface PhoneLink {
    data object NotPaired : PhoneLink
    data class Connecting(val pc: String) : PhoneLink
    data class Live(val pc: String) : PhoneLink
    data class Offline(val pc: String) : PhoneLink
}

/**
 * Phone shell (M3, C2 style): header (logo + tab title, "Live from your PC" / "Demo" pill), tab content, C2 bottom bar.
 * Shared (commonMain) so the render test draws exactly what the phone shows; the Android-only parts (system back,
 * edge-to-edge, saved pairing, polling) live in androidMain/ui/AndroidShell.kt. [profile] = the Profile tab content.
 * Insets: the header pads the status bar, the bottom bar pads the navigation bar (edge-to-edge on Android, 0 on desktop).
 */
@Composable
fun PhoneShell(
    state: AppState,
    signals: SignalsSnapshot,
    link: PhoneLink,
    tab: Tab,
    onTab: (Tab) -> Unit,
    today: TodayActions,
    onReplay: () -> Unit,
    notice: String? = null,
    journal: JournalActions = JournalActions(),
    profile: @Composable () -> Unit,
) {
    EgTheme(state.settings.theme) {
        val c = Eg.colors
        Scaffold(
            containerColor = c.background,
            contentWindowInsets = WindowInsets(0),
            topBar = { PhoneHeader(state, link, tab) },
            bottomBar = { PhoneNavBar(tab, onTab) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.TODAY -> PhoneTodayScreen(state, signals, link, today, onReplay, onConnect = { onTab(Tab.PROFILE) }, notice = notice)
                    // Entries live on the phone (its own AppStore); the PC companion stays read-only.
                    Tab.JOURNAL -> JournalScreen(state, journal, wide = false, Modifier.imePadding(), savedLabel = "Saved on this phone")
                    Tab.PROFILE -> profile()
                }
            }
        }
    }
}

@Composable
private fun PhoneHeader(state: AppState, link: PhoneLink, tab: Tab) {
    val c = Eg.colors
    Row(
        Modifier.fillMaxWidth().background(c.background).windowInsetsPadding(WindowInsets.statusBars).height(68.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EnergyGuardLogo(size = 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(tab.label, style = EgType.screenTitle.copy(fontSize = 26.sp, lineHeight = 32.sp), color = c.text)
        Spacer(Modifier.weight(1f))
        if (link is PhoneLink.Live) Pill("Live from your PC", dot = c.accent)
        else Pill("Demo", dot = c.accent)
    }
}

/** C2 bottom bar: #0D0D10, selected item = lime-tinted pill with a lime icon; the whole cell is the touch target. */
@Composable
private fun PhoneNavBar(tab: Tab, onTab: (Tab) -> Unit) {
    val c = Eg.colors
    Column(Modifier.fillMaxWidth().background(c.navBar)) {
        Divider()
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(76.dp)) {
            Tab.entries.forEach { t ->
                val selected = t == tab
                Column(
                    Modifier.weight(1f).fillMaxHeight().selectable(selected, role = Role.Tab) { onTab(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.width(64.dp).height(32.dp)
                            .background(if (selected) c.accent.copy(alpha = 0.14f) else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(t.icon, if (selected) c.accent else c.muted, size = 22.dp) }
                    Spacer(Modifier.height(4.dp))
                    Text(t.label, style = EgType.small, color = if (selected) c.text else c.muted)
                }
            }
        }
    }
}
