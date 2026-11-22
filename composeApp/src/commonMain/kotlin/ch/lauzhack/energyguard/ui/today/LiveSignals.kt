package ch.lauzhack.energyguard.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.ui.components.Sparkline
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.engine.BaselineSummary
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.ui.components.CardTitle
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgCard
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgType
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** One row of the Live signals card: [text] is null when there is no data (grey dot, "-"). */
data class SignalLine(val text: String?, val flag: String? = null, val flagWarn: Boolean = false) {
    val live get() = text != null
}

/**
 * What the Live signals card shows, as numbers (already rounded). Built on the device that has the sensors ([of]);
 * the PC also sends it to the phone (companion JSON `signals`, docs/API.md), so both draw the same rows.
 * Null = no data. Never images, audio or key contents: rates, ratios and timings only.
 */
@Serializable
data class SignalsSnapshot(
    val source: String? = null,       // card subtitle, e.g. "Logitech webcam · IR" (see [sensingSource])
    val face: Boolean? = null,        // null = no vision frame, false = no face in view
    val blinksPerMin: Int? = null,
    val perclosPct: Int? = null,
    val eyesClosed: Boolean? = null,
    val jitterX: Double? = null,      // mouse jitter / personal baseline; null while there is no baseline
    val jitterPerS: Double? = null,   // raw jitter, shown while there is no baseline yet
    val precisionPct: Int? = null,    // 100 / overshoot ratio
    val typing: Boolean? = null,      // null = no input stats, false = no key in the window
    val msPerKey: Int? = null,
    val backspacePct: Int? = null,
) {
    /** Phone side: the network is untrusted, out-of-range values become "no data". */
    fun clamped() = copy(
        source = source?.filter { it >= ' ' }?.take(MAX_SOURCE_CHARS),
        blinksPerMin = blinksPerMin?.takeIf { it in 0..300 },
        perclosPct = perclosPct?.takeIf { it in 0..100 },
        jitterX = jitterX?.takeIf { it.isFinite() && it in 0.0..100.0 },
        jitterPerS = jitterPerS?.takeIf { it.isFinite() && it in 0.0..10_000.0 },
        precisionPct = precisionPct?.takeIf { it in 0..100 },
        msPerKey = msPerKey?.takeIf { it in 1..60_000 },
        backspacePct = backspacePct?.takeIf { it in 0..100 },
    )

    companion object {
        private const val MAX_SOURCE_CHARS = 40

        fun of(v: VisionFrame?, i: InputStats?, baseline: BaselineSummary, demo: Boolean): SignalsSnapshot {
            val base = baseline.mouseJitter?.takeIf { it > 0 && it.isFinite() }
            val jitter = i?.jitter?.takeIf { it.isFinite() }
            val eyes = v?.takeIf { it.face }
            val keys = i?.takeIf { it.keyCount > 0 }
            return SignalsSnapshot(
                source = sensingSource(v?.source, demo),
                face = v?.face,
                blinksPerMin = eyes?.blinkCount,
                perclosPct = eyes?.perclos?.takeIf { it.isFinite() }?.let { (it * 100).roundToInt() },
                eyesClosed = eyes?.eyesClosed,
                jitterX = if (jitter != null && base != null) oneDecimal(jitter / base) else null,
                jitterPerS = jitter?.let(::oneDecimal),
                precisionPct = i?.overshootRatio?.takeIf { it.isFinite() && it >= 1.0 }?.let { (100 / it).roundToInt() },
                typing = i?.let { it.keyCount > 0 },
                msPerKey = keys?.meanInterKeyMs?.takeIf { it.isFinite() && it > 0 }?.roundToInt(),
                backspacePct = keys?.backspaceRatio?.takeIf { it.isFinite() }?.let { (it * 100).roundToInt() },
            )
        }
    }
}

/** The signals of the device running the store (desktop sensors, or the phone's demo replay). */
fun AppState.liveSignals() = SignalsSnapshot.of(dashboard.vision, dashboard.input, profile.baseline, demo)

/** Card subtitle: which perception path feeds the eye metrics (VisionFrame.source, docs/API.md). */
fun sensingSource(source: String?, demo: Boolean): String = when {
    demo -> "Demo data"
    source == "logiinsight-ir" -> "Logitech webcam · IR"
    source?.startsWith("logiinsight") == true -> "Logitech webcam"
    source == "mediapipe" -> "MediaPipe"
    else -> "Webcam"
}

/** Eyes: blink rate, PERCLOS and an open / closed flag. Null text if no frame or no face. */
fun eyesLine(s: SignalsSnapshot): SignalLine {
    val face = s.face ?: return SignalLine(null)
    if (!face) return SignalLine(null, flag = "no face")
    val parts = listOfNotNull(
        s.blinksPerMin?.let { "$it blinks/min" },
        s.perclosPct?.let { "PERCLOS $it %" },
    )
    val closed = s.eyesClosed == true
    return SignalLine(parts.joinToString(" · ").ifEmpty { null }, flag = if (closed) "closed" else "open", flagWarn = closed)
}

/** Mouse: jitter vs the personal baseline (raw /s while calibrating) and click precision (100 / overshoot). */
fun mouseLine(s: SignalsSnapshot): SignalLine {
    val jitter = s.jitterX?.let { "Jitter ${oneDecimalText(it)}× usual" } ?: s.jitterPerS?.let { "Jitter ${oneDecimalText(it)}/s" }
    val precision = s.precisionPct?.let { "precision $it %" }
    val ratio = s.jitterX
    return SignalLine(
        listOfNotNull(jitter, precision).joinToString(" · ").ifEmpty { null },
        flag = if (ratio != null && ratio >= SHAKY_RATIO) "shaky" else null, flagWarn = true,
    )
}

/** Keyboard: timings and counts only, never key contents. Null text while not typing. */
fun keyboardLine(s: SignalsSnapshot): SignalLine {
    if (s.typing != true) return SignalLine(null, flag = if (s.typing == false) "not typing" else null)
    val parts = listOfNotNull(
        s.msPerKey?.let { "$it ms/key" },
        s.backspacePct?.let { "backspace $it %" },
    )
    return SignalLine(parts.joinToString(" · ").ifEmpty { null })
}

private const val SHAKY_RATIO = 1.5

private fun oneDecimal(x: Double): Double = (x * 10).roundToInt() / 10.0

private fun oneDecimalText(x: Double): String {
    val t = (x * 10).roundToInt()
    return "${t / 10}.${t % 10}"
}

/**
 * "The webcam is the eyes of the AI": the raw signals feeding the score, refreshed once per second
 * (vision arrives at ~10 Hz, too fast to read). Numbers only; keyboard = timings, never contents.
 * C2: Eyes / Mouse / Keys = name + value on the right, a sparkline of the last ~60 s under each
 * (eyes in the state colour of [level], the others neutral).
 */
@Composable
fun LiveSignalsCard(signals: SignalsSnapshot, modifier: Modifier = Modifier, level: Level = Level.FRESH, fill: Boolean = true) {
    val c = Eg.colors
    val (s, history) = sampledEverySecond(signals)
    EgCard(modifier) {
        CardTitle("Live signals") {
            Text(s.source ?: "-", style = EgType.small, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        val row = if (fill) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(58.dp)
        SignalRow("Eyes", eyesLine(s), history.eyes, c.level(level), row)
        SignalRow("Mouse", mouseLine(s), history.mouse, c.spark, row)
        SignalRow("Keys", keyboardLine(s), history.keys, c.spark, row)
    }
}

@Composable
private fun SignalRow(label: String, line: SignalLine, values: List<Double>, color: Color, modifier: Modifier) {
    val c = Eg.colors
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = EgType.bodyStrong, color = if (line.live) c.text else c.muted)
            Spacer(Modifier.width(EgSpace.s))
            Spacer(Modifier.weight(1f))
            val warn = line.flagWarn && line.live && line.flag != null
            Text(
                buildAnnotatedString {
                    append(line.text ?: line.flag ?: "–")
                    if (warn) withStyle(SpanStyle(color = c.tired)) { append(" · ${line.flag}") }
                },
                style = EgType.label, color = if (line.live) c.text else c.label,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(6.dp))
        Sparkline(if (line.live) values else emptyList(), if (line.live) color else c.track, Modifier.fillMaxWidth().height(18.dp))
    }
}

/** The last [HISTORY_S] once-a-second values of each row (null = gap, not drawn). */
private data class SignalHistory(val eyes: List<Double> = emptyList(), val mouse: List<Double> = emptyList(), val keys: List<Double> = emptyList()) {
    fun push(s: SignalsSnapshot) = SignalHistory(
        eyes.add(s.perclosPct?.toDouble() ?: s.blinksPerMin?.toDouble()),
        mouse.add(s.jitterX ?: s.jitterPerS),
        keys.add(s.msPerKey?.toDouble()),
    )

    private fun List<Double>.add(v: Double?) = if (v == null || !v.isFinite()) this else (this + v).takeLast(HISTORY_S)
}

private const val HISTORY_S = 60

/** [value] as of the last whole second (the card ticks at 1 Hz whatever the source rate) + the sparkline history. */
@Composable
private fun sampledEverySecond(value: SignalsSnapshot): Pair<SignalsSnapshot, SignalHistory> {
    val latest by rememberUpdatedState(value)
    var shown by remember { mutableStateOf(value) }
    var history by remember { mutableStateOf(SignalHistory().push(value)) }
    LaunchedEffect(Unit) {
        while (true) {
            shown = latest
            history = history.push(latest)
            delay(1_000)
        }
    }
    // A source appearing / disappearing shows at once; only changing numbers are throttled.
    return (if (shown.presence() != value.presence()) value else shown) to history
}

private fun SignalsSnapshot.presence() = Triple(face, typing, jitterPerS != null || precisionPct != null)
