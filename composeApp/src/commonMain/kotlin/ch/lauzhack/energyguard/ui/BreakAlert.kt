package ch.lauzhack.energyguard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * When to show the "take a break" card (#8). Pure, so it is unit-tested without a UI.
 *  - shown while the level is TIRED or EXHAUSTED;
 *  - "Dismiss" hides it until the user recovers (level back to FRESH/OK) and gets tired again;
 *  - "Snooze" hides it for [snoozeMs], then it comes back if still tired.
 */
class BreakAlertLogic(private val snoozeMs: Long = 10 * 60_000L) {
    private var dismissed = false
    private var snoozedUntil = Long.MIN_VALUE

    fun isVisible(level: Level, nowMs: Long): Boolean {
        val tired = level >= Level.TIRED
        if (!tired) dismissed = false // recovered: next tired episode alerts again
        return tired && !dismissed && nowMs >= snoozedUntil
    }

    fun dismiss() { dismissed = true }

    fun snooze(nowMs: Long) { snoozedUntil = nowMs + snoozeMs }
}

/** Default wording; the EnergyCoach message (#21) can replace [title]/[detail] later. */
internal fun breakTitle(level: Level): String =
    if (level == Level.EXHAUSTED) "Time to stop for a while" else "Time for a 5-minute break"

internal fun breakDetail(f: FatigueState): String {
    val why = f.reasons.firstOrNull()?.let { readableReason(it) }
    val advice = if (f.level == Level.EXHAUSTED) "Step away from the screen, drink some water." else "Look away, stretch, breathe."
    return if (why != null) "${why.replaceFirstChar { it.uppercase() }}. $advice" else advice
}

/** The card itself, sliding up from the bottom of the screen. Put it in a Box over the dashboard. */
@Composable
fun BreakAlertOverlay(f: FatigueState, modifier: Modifier = Modifier) {
    val logic = remember { BreakAlertLogic() }
    val clock = remember { TimeSource.Monotonic.markNow() }
    var now by remember { mutableLongStateOf(0L) }
    var version by remember { mutableLongStateOf(0L) } // bumps on a click so visibility recomputes at once
    LaunchedEffect(Unit) {
        while (true) {
            now = clock.elapsedNow().inWholeMilliseconds
            delay(1000)
        }
    }
    val visible = version >= 0 && f.calibrated && logic.isVisible(f.level, now)
    val accent = energyColor(f.energy)

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Row(
            Modifier
                .widthIn(max = 1000.dp)
                .height(IntrinsicSize.Min) // lets the accent bar match the card's height
                .background(Warm.Surface, RoundedCornerShape(24.dp))
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Accent bar in the energy color: the card reads as part of the same "mood" as the gauge.
            Box(Modifier.width(8.dp).fillMaxHeight().background(accent, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(breakTitle(f.level), fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Warm.Text)
                Text(breakDetail(f), fontSize = 22.sp, color = Warm.Muted)
            }
            Spacer(Modifier.width(24.dp))
            OutlinedButton(onClick = { logic.snooze(clock.elapsedNow().inWholeMilliseconds); version++ }) {
                Text("Snooze 10 min", fontSize = 20.sp, color = Warm.Text)
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = { logic.dismiss(); version++ },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Warm.Deep),
            ) {
                Text("Dismiss", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
