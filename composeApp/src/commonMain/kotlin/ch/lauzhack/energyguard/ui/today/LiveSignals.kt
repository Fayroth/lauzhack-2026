package ch.lauzhack.energyguard.ui.today

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.engine.BaselineSummary
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.ui.components.CardTitle
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgCard
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** One row of the Live signals card: [text] is null when there is no data (grey dot, "-"). */
data class SignalLine(val text: String?, val flag: String? = null, val flagWarn: Boolean = false) {
    val live get() = text != null
}

/** Card subtitle: which perception path feeds the eye metrics (VisionFrame.source, docs/API.md). */
fun sensingSource(source: String?, demo: Boolean): String = when {
    demo -> "Demo data"
    source == "logiinsight-ir" -> "Logitech webcam · IR"
    source?.startsWith("logiinsight") == true -> "Logitech webcam"
    source == "mediapipe" -> "MediaPipe"
    else -> "Webcam"
}

/** Eyes: blink rate, PERCLOS and an open / closed flag. Null text if no frame or no face. */
fun eyesLine(v: VisionFrame?): SignalLine {
    if (v == null) return SignalLine(null)
    if (!v.face) return SignalLine(null, flag = "no face")
    val parts = listOfNotNull(
        v.blinkCount?.let { "$it blinks/min" },
        v.perclos?.takeIf { it.isFinite() }?.let { "PERCLOS ${(it * 100).roundToInt()} %" },
    )
    val closed = v.eyesClosed == true
    return SignalLine(parts.joinToString(" · ").ifEmpty { null }, flag = if (closed) "closed" else "open", flagWarn = closed)
}

/** Mouse: jitter vs the personal baseline (raw /s while calibrating) and click precision (100 / overshoot). */
fun mouseLine(i: InputStats?, baseline: BaselineSummary): SignalLine {
    if (i == null) return SignalLine(null)
    val base = baseline.mouseJitter?.takeIf { it > 0 && it.isFinite() }
    val jitter = when {
        !i.jitter.isFinite() -> null
        base != null -> "Jitter ${oneDecimal(i.jitter / base)}× usual"
        else -> "Jitter ${oneDecimal(i.jitter)}/s"
    }
    val precision = i.overshootRatio.takeIf { it.isFinite() && it >= 1.0 }?.let { "precision ${(100 / it).roundToInt()} %" }
    val ratio = base?.let { i.jitter / it }
    return SignalLine(
        listOfNotNull(jitter, precision).joinToString(" · ").ifEmpty { null },
        flag = if (ratio != null && ratio >= SHAKY_RATIO) "shaky" else null, flagWarn = true,
    )
}

/** Keyboard: timings and counts only, never key contents. Null text while not typing. */
fun keyboardLine(i: InputStats?): SignalLine {
    if (i == null || i.keyCount <= 0) return SignalLine(null, flag = if (i != null) "not typing" else null)
    val parts = listOfNotNull(
        i.meanInterKeyMs.takeIf { it.isFinite() && it > 0 }?.let { "${it.roundToInt()} ms/key" },
        i.backspaceRatio.takeIf { it.isFinite() }?.let { "backspace ${(it * 100).roundToInt()} %" },
    )
    return SignalLine(parts.joinToString(" · ").ifEmpty { null })
}

private const val SHAKY_RATIO = 1.5

private fun oneDecimal(x: Double): String {
    val t = (x * 10).roundToInt()
    return "${t / 10}.${t % 10}"
}

/**
 * "The webcam is the eyes of the AI": the raw signals feeding the score, refreshed once per second
 * (vision arrives at ~10 Hz, too fast to read). Numbers only; keyboard = timings, never contents.
 */
@Composable
fun LiveSignalsCard(
    vision: VisionFrame?,
    input: InputStats?,
    baseline: BaselineSummary,
    demo: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Eg.colors
    val v = sampledEverySecond(vision)
    val i = sampledEverySecond(input)
    EgCard(modifier) {
        CardTitle("Live signals") {
            Text(sensingSource(vision?.source, demo), style = EgType.label, color = c.muted)
        }
        Spacer(Modifier.size(4.dp))
        Column {
            SignalRow("Eyes", eyesLine(v))
            SignalRow("Mouse", mouseLine(i, baseline))
            SignalRow("Keyboard", keyboardLine(i))
        }
    }
}

@Composable
private fun SignalRow(label: String, line: SignalLine) {
    val c = Eg.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LiveDot(line.live)
        Spacer(Modifier.width(12.dp))
        Text(label, style = EgType.bodyStrong, color = c.text, modifier = Modifier.width(88.dp))
        Text(
            line.text ?: "-", style = EgType.body, color = if (line.live) c.text else c.muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        line.flag?.let {
            Spacer(Modifier.width(EgSpace.xs))
            Text(it, style = EgType.label, color = if (line.flagWarn && line.live) c.tired else c.muted)
        }
    }
}

/** Accent dot breathing slowly while data flows; flat grey when there is none. */
@Composable
private fun LiveDot(live: Boolean) {
    val c = Eg.colors
    val pulse by rememberInfiniteTransition(label = "liveDot").animateFloat(
        1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "liveDotAlpha",
    )
    Box(Modifier.size(10.dp).alpha(if (live) pulse else 1f).background(if (live) c.accent else c.muted.copy(alpha = 0.5f), CircleShape))
}

/** [value] as of the last whole second: the card ticks at 1 Hz whatever the source rate. */
@Composable
private fun <T> sampledEverySecond(value: T): T {
    val latest by rememberUpdatedState(value)
    var shown by remember { mutableStateOf(value) }
    LaunchedEffect(Unit) {
        while (true) {
            shown = latest
            delay(1_000)
        }
    }
    // Data appearing / disappearing shows at once; only changing numbers are throttled.
    return if ((shown == null) != (value == null)) value else shown
}
