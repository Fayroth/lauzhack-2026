package ch.lauzhack.energyguard.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.ui.components.CoachCard
import ch.lauzhack.energyguard.ui.components.Divider
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgButton
import ch.lauzhack.energyguard.ui.components.EgCard
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.GlowDot
import ch.lauzhack.energyguard.ui.components.QuietStatText
import ch.lauzhack.energyguard.ui.factorize
import ch.lauzhack.energyguard.ui.today.BreakButton
import ch.lauzhack.energyguard.ui.today.EnergyChartCard
import ch.lauzhack.energyguard.ui.today.LiveSignalsCard
import ch.lauzhack.energyguard.ui.today.RingsRow
import ch.lauzhack.energyguard.ui.today.SignalsSnapshot
import ch.lauzhack.energyguard.ui.today.StateLine
import ch.lauzhack.energyguard.ui.today.TodayActions
import ch.lauzhack.energyguard.ui.today.coachTime
import ch.lauzhack.energyguard.ui.today.quietStats

/**
 * Today on a phone (C2): one scrolling column in the desktop's language. Hero card with the three rings side by side
 * (~104 dp, the middle one "Focus"), state chip + sentence + reasons, then the break button (demo) or "Breaks start on
 * your PC" (live, read-only); Coach, Energy through the day, Live signals, stats as a quiet 2-column grid.
 */
@Composable
fun PhoneTodayScreen(
    state: AppState,
    signals: SignalsSnapshot,
    link: PhoneLink,
    actions: TodayActions,
    onReplay: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    val c = Eg.colors
    val readOnly = link is PhoneLink.Live
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = EgSpace.s, vertical = EgSpace.xs),
        verticalArrangement = Arrangement.spacedBy(EgSpace.s),
    ) {
        when (link) {
            PhoneLink.NotPaired -> ConnectCard(onConnect)
            is PhoneLink.Connecting -> StatusCard("Connecting to your PC (${link.pc})…", c.ice)
            is PhoneLink.Offline -> StatusCard("Can't reach your PC (${link.pc}). Retrying · demo data meanwhile.", c.tiredFill)
            is PhoneLink.Live -> Unit
        }
        notice?.let { StatusCard(it, c.accent) }
        EgCard(Modifier.fillMaxWidth(), hero = true, padding = 20.dp) {
            RingsRow(
                state, ringSize = 104.dp, captions = false, concentrationName = "Focus", stroke = 8.dp,
                numberStyle = EgType.value.copy(fontSize = 24.sp, lineHeight = 28.sp),
                nameStyle = EgType.ringName.copy(fontSize = 16.sp, lineHeight = 20.sp),
                unitStyle = EgType.small,
            )
            Spacer(Modifier.height(18.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            StateLine(state, stacked = true)
            Spacer(Modifier.height(16.dp))
            if (readOnly) {
                Text(
                    "Breaks start on your PC", style = EgType.label, color = c.label, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(top = 12.dp),
                )
            } else {
                BreakButton(state, actions, Modifier.fillMaxWidth())
            }
        }
        CoachCard(state.coach, Modifier.fillMaxWidth(), format = ::factorize, maxLines = 6, time = coachTime(state))
        EnergyChartCard(state, Modifier.fillMaxWidth(), chartHeight = 120.dp)
        LiveSignalsCard(signals, Modifier.fillMaxWidth(), state.concentration.level, fill = false)
        StatsGrid(state)
        if (!readOnly) {
            TextButton(onClick = onReplay, Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                Text("Replay the demo", style = EgType.bodyStrong, color = c.accent)
            }
        }
        Spacer(Modifier.height(EgSpace.xs))
    }
}

/** Not paired yet: the one call to action, on top of the demo data. */
@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    val c = Eg.colors
    EgCard(Modifier.fillMaxWidth()) {
        Text("Connect to your PC", style = EgType.heading, color = c.text)
        Spacer(Modifier.height(EgSpace.xs))
        Text(
            "See your PC's live energy here: webcam, mouse and keyboard stay on the PC, only numbers come over Wi-Fi. Demo data until then.",
            style = EgType.body, color = c.muted,
        )
        Spacer(Modifier.height(EgSpace.s))
        EgButton("Connect", onConnect, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatusCard(text: String, color: Color) {
    val c = Eg.colors
    EgCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowDot(color, 8.dp)
            Spacer(Modifier.width(12.dp))
            Text(text, style = EgType.body, color = c.text)
        }
    }
}

/** The quiet stats as a 2-column grid, no boxes. */
@Composable
private fun StatsGrid(state: AppState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        quietStats(state).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { QuietStatText(it, Modifier.weight(1f), labelMaxLines = 2) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
