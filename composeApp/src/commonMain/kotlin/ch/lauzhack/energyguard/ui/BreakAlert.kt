package ch.lauzhack.energyguard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * Escalating break alert (#8), like a car's drowsiness assistant: it starts quietly and insists if ignored.
 *  - HINT: small "Break soon?" pill as soon as the user is TIRED.
 *  - CARD: the break card, once TIRED has been ignored for [cardAfterMs], or right away when EXHAUSTED.
 *  - FULL: full-screen "Stop", once EXHAUSTED has been ignored for [fullAfterMs], or at once on a micro-sleep.
 * "Dismiss" ends the episode (until the user recovers to FRESH/OK and gets tired again);
 * "Snooze" hides everything for [snoozeMs], then the escalation starts again from HINT.
 * A micro-sleep always goes FULL (safety), except for [awakeGraceMs] after "I'm awake".
 * Pure and clock-free (time is passed in), so it is unit-tested.
 */
enum class AlertStage { NONE, HINT, CARD, FULL }

class BreakAlertLogic(
    private val cardAfterMs: Long = 3 * 60_000L,
    private val fullAfterMs: Long = 2 * 60_000L,
    private val snoozeMs: Long = 10 * 60_000L,
    private val awakeGraceMs: Long = 30_000L,
) {
    private var tiredSince: Long? = null
    private var exhaustedSince: Long? = null
    private var dismissed = false
    private var snoozedUntil = Long.MIN_VALUE
    private var awakeUntil = Long.MIN_VALUE

    fun stage(level: Level, microSleep: Boolean, nowMs: Long): AlertStage {
        if (microSleep && nowMs >= awakeUntil) return AlertStage.FULL
        if (level < Level.TIRED) { // recovered: reset the episode
            tiredSince = null; exhaustedSince = null; dismissed = false
            return AlertStage.NONE
        }
        if (dismissed || nowMs < snoozedUntil) return AlertStage.NONE
        val tired = tiredSince ?: nowMs.also { tiredSince = it }
        if (level == Level.EXHAUSTED) {
            val exhausted = exhaustedSince ?: nowMs.also { exhaustedSince = it }
            return if (nowMs - exhausted >= fullAfterMs) AlertStage.FULL else AlertStage.CARD
        }
        exhaustedSince = null
        return if (nowMs - tired >= cardAfterMs) AlertStage.CARD else AlertStage.HINT
    }

    fun dismiss() { dismissed = true }

    fun snooze(nowMs: Long) {
        snoozedUntil = nowMs + snoozeMs
        tiredSince = snoozedUntil; exhaustedSince = null // after the snooze, start again from HINT
    }

    /** From the HINT pill: the user asks for the card now. */
    fun openCard(nowMs: Long) { tiredSince = nowMs - cardAfterMs }

    fun awake(nowMs: Long) { awakeUntil = nowMs + awakeGraceMs }

    companion object {
        /** Short delays so the whole escalation fits in a 2-minute demo. */
        fun forDemo() = BreakAlertLogic(cardAfterMs = 15_000, fullAfterMs = 15_000, snoozeMs = 30_000)
    }
}

/** The engine reports a micro-sleep as the reason "eyes closed 2.3 s" (see FatigueEngine.describe). */
internal fun microSleepReason(f: FatigueState): String? = f.reasons.firstOrNull { it.startsWith("eyes closed ") }

/** Default wording; the EnergyCoach message (#21) can replace these later. */
internal fun breakTitle(level: Level): String =
    if (level == Level.EXHAUSTED) "Time to stop for a while" else "Time for a 5-minute break"

internal fun breakDetail(f: FatigueState): String {
    val why = f.reasons.firstOrNull()?.let { readableReason(it) }
    val advice = if (f.level == Level.EXHAUSTED) "Step away from the screen, drink some water." else "Look away, stretch, breathe."
    return if (why != null) "${why.replaceFirstChar { it.uppercase() }}. $advice" else advice
}

/** All three stages, drawn over the dashboard. Call it inside the root Box. */
@Composable
fun BoxScope.BreakAlertOverlay(f: FatigueState, demo: Boolean) {
    val logic = remember(demo) { if (demo) BreakAlertLogic.forDemo() else BreakAlertLogic() }
    val clock = remember { TimeSource.Monotonic.markNow() }
    var now by remember { mutableLongStateOf(0L) }
    var clicks by remember { mutableLongStateOf(0L) } // read below so a click recomputes the stage at once
    LaunchedEffect(Unit) {
        while (true) {
            now = clock.elapsedNow().inWholeMilliseconds
            delay(500)
        }
    }
    fun nowMs() = clock.elapsedNow().inWholeMilliseconds
    val microSleep = microSleepReason(f)
    val stage = if (clicks < 0 || !f.calibrated) AlertStage.NONE else logic.stage(f.level, microSleep != null, now)
    val accent = energyColor(f.energy)

    // 1. HINT: a small pill at the top, click it to open the card.
    AnimatedVisibility(
        stage == AlertStage.HINT,
        Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
        enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Row(
            Modifier
                .background(Warm.Surface, RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.16f), RoundedCornerShape(50))
                .clickable { logic.openCard(nowMs()); clicks++ }
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoffeeCup(accent, 28.dp)
            Spacer(Modifier.width(12.dp))
            Text("Break soon?", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Warm.Text)
        }
    }

    // 2. CARD: slides up from the bottom.
    AnimatedVisibility(
        stage == AlertStage.CARD,
        Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
        enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
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
            Box(Modifier.width(8.dp).fillMaxHeight().background(accent, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(24.dp))
            CoffeeCup(accent, 56.dp)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(breakTitle(f.level), fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Warm.Text)
                Text(breakDetail(f), fontSize = 22.sp, color = Warm.Muted)
            }
            Spacer(Modifier.width(24.dp))
            OutlinedButton(onClick = { logic.snooze(nowMs()); clicks++ }) {
                Text("Snooze", fontSize = 20.sp, color = Warm.Text)
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = { logic.dismiss(); clicks++ },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Warm.Deep),
            ) { Text("I'll take it", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        }
    }

    // 3. FULL: dims the whole screen. Micro-sleep has its own wording and an "I'm awake" button.
    AnimatedVisibility(stage == AlertStage.FULL, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Warm.Deep.copy(alpha = 0.92f)).clickable(enabled = false) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                CoffeeCup(Warm.Rose, 120.dp)
                Text(
                    if (microSleep != null) "Your eyes just closed" else "Stop. Take a break.",
                    fontSize = 64.sp, fontWeight = FontWeight.SemiBold, color = Warm.Text, textAlign = TextAlign.Center,
                )
                Text(
                    if (microSleep != null) "${readableReason(microSleep).replaceFirstChar { it.uppercase() }}. That's a micro-sleep: don't push through it."
                    else "You've been exhausted for a while. Rest now, the work will still be there.",
                    fontSize = 26.sp, color = Warm.Muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 900.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (microSleep != null) {
                        OutlinedButton(onClick = { logic.awake(nowMs()); clicks++ }) { Text("I'm awake", fontSize = 22.sp, color = Warm.Text) }
                    }
                    Button(
                        onClick = { logic.dismiss(); logic.awake(nowMs()); clicks++ },
                        colors = ButtonDefaults.buttonColors(containerColor = Warm.Rose, contentColor = Warm.Deep),
                    ) { Text("I'm taking a break", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** Simple coffee-cup line icon (the universal "take a break" sign in cars). */
@Composable
fun CoffeeCup(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 32f
        val stroke = Stroke(width = 2.6f * s, cap = StrokeCap.Round)
        // Cup body, handle and saucer.
        drawRoundRect(color, Offset(5f * s, 12f * s), Size(17f * s, 13f * s), CornerRadius(4f * s), style = stroke)
        drawArc(color, -90f, 180f, false, Offset(19f * s, 14f * s), Size(8f * s, 8f * s), style = stroke)
        drawLine(color, Offset(3f * s, 29f * s), Offset(24f * s, 29f * s), strokeWidth = 2.6f * s, cap = StrokeCap.Round)
        // Steam.
        listOf(10f, 16f).forEach { x ->
            drawLine(color.copy(alpha = 0.7f), Offset(x * s, 4f * s), Offset(x * s, 8f * s), strokeWidth = 2.2f * s, cap = StrokeCap.Round)
        }
    }
}
