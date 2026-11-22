package ch.lauzhack.energyguard.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.Settings
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.ui.components.CardTitle
import ch.lauzhack.energyguard.ui.components.Divider
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgCard
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.Pill
import ch.lauzhack.energyguard.ui.components.SettingRow

/** Settings switches of the Profile tab, wired to AppStore.updateSettings by the shells (all persisted). */
class SettingsActions(
    val onHaptics: (Boolean) -> Unit = {},
    val onCoachOnline: (Boolean) -> Unit = {},
    val onLightMode: (Boolean) -> Unit = {},
)

/**
 * Settings card (M5), both shells: haptics, AI coach online / offline, light mode.
 * [coachUnavailable] = why the AI coach can't go online ("no key"): the switch is then off and disabled.
 * [hapticsWhat] = what vibrates ("Mouse" on the PC, "Phone" on Android).
 */
@Composable
fun SettingsCard(
    settings: Settings,
    actions: SettingsActions,
    coachUnavailable: String?,
    hapticsWhat: String,
    modifier: Modifier = Modifier,
) {
    EgCard(modifier) {
        CardTitle("Settings")
        Spacer(Modifier.height(EgSpace.xs))
        SettingRow(
            "Haptic alerts", settings.hapticsOn, actions.onHaptics,
            subtitle = if (settings.hapticsOn) "$hapticsWhat vibrates when you get tired" else "Off · no vibration",
        )
        Divider()
        val coachOn = settings.coachOnline && coachUnavailable == null
        SettingRow(
            "AI coach", coachOn, actions.onCoachOnline,
            subtitle = when {
                coachUnavailable != null -> "offline ($coachUnavailable) · templates only"
                coachOn -> "online · numbers only, max 1 call / 10 min"
                else -> "offline · templates only, nothing sent"
            },
            enabled = coachUnavailable == null,
        )
        Divider()
        SettingRow("Light mode", settings.theme == ThemeMode.LIGHT, actions.onLightMode)
    }
}

/**
 * Last 7 days (M5): average concentration per day as lime bars (today full lime, other days dimmed), the breaks
 * taken under each day. "Sample data" pill when the bars are [WeekView.sample]. [compact] = phone (shorter labels).
 */
@Composable
fun Last7DaysCard(state: AppState, modifier: Modifier = Modifier, chartHeight: Dp = 120.dp, compact: Boolean = false) {
    val c = Eg.colors
    val week = lastSevenDays(state)
    EgCard(modifier) {
        CardTitle("Last 7 days") { if (week.sample) Pill("Sample data") }
        Spacer(Modifier.height(4.dp))
        Text("Average concentration per day · breaks taken under each day", style = EgType.small, color = c.label)
        Spacer(Modifier.height(EgSpace.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
            week.bars.forEach { d -> DayColumn(d, chartHeight, compact, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DayColumn(d: DayBar, chartHeight: Dp, compact: Boolean, modifier: Modifier) {
    val c = Eg.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.avg?.toString() ?: "–", style = EgType.label, color = if (d.today) c.text else c.muted, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(chartHeight), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight().background(c.track.copy(alpha = c.track.alpha * 0.6f), BAR_SHAPE))
            val f = (d.avg ?: 0).coerceIn(0, 100) / 100f
            if (f > 0f) {
                Box(
                    Modifier.fillMaxWidth(0.62f).fillMaxHeight(f)
                        .background(if (d.today) c.accent else c.accent.copy(alpha = if (c.isDark) 0.38f else 0.45f), BAR_SHAPE),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (d.today) "Today" else d.weekday, maxLines = 1,
            style = if (d.today) EgType.small.copy(fontWeight = FontWeight.Bold) else EgType.small,
            color = if (d.today) c.text else c.label,
        )
        val breaks = if (compact) "${d.breaks}" else if (d.breaks == 1) "1 break" else "${d.breaks} breaks"
        Text(breaks, style = EgType.small, color = c.label, maxLines = 1)
    }
}

private val BAR_SHAPE = RoundedCornerShape(8.dp)
