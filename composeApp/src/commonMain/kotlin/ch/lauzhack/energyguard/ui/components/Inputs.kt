package ch.lauzhack.energyguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Touch height of every control (>= 48 dp on a phone) and the button corner of [EgButton]. */
private val CONTROL_HEIGHT = 48.dp
private val CONTROL_SHAPE = RoundedCornerShape(12.dp)

/**
 * A numbered scale (KSS 1..9, stress 1..5): one equal-width cell per value, the selected one in the accent colour.
 * Optional: tapping the selected value clears it (null). [lowLabel] / [highLabel] are written under both ends.
 */
@Composable
fun ScalePicker(
    value: Int?,
    range: IntRange,
    onChange: (Int?) -> Unit,
    lowLabel: String,
    highLabel: String,
    modifier: Modifier = Modifier,
) {
    val c = Eg.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EgSpace.xs)) {
            range.forEach { v ->
                val selected = v == value
                Box(
                    Modifier.weight(1f).height(CONTROL_HEIGHT).clip(CONTROL_SHAPE)
                        .background(if (selected) c.accent else c.raised)
                        .selectable(selected, role = Role.RadioButton) { onChange(if (selected) null else v) },
                    contentAlignment = Alignment.Center,
                ) { Text("$v", style = EgType.bodyStrong, color = if (selected) c.onAccent else c.text) }
            }
        }
        Spacer(Modifier.height(EgSpace.xs))
        Row(Modifier.fillMaxWidth()) {
            Text(lowLabel, style = EgType.label, color = c.muted)
            Spacer(Modifier.weight(1f))
            Text(highLabel, style = EgType.label, color = c.muted, textAlign = TextAlign.End)
        }
    }
}

/**
 * − value + for a small count (cups of coffee). null = not set ("–"); − from [range].first clears it, + from null
 * starts at [range].first + 1.
 */
@Composable
fun Stepper(value: Int?, range: IntRange, onChange: (Int?) -> Unit, unit: (Int) -> String, modifier: Modifier = Modifier) {
    val c = Eg.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        EgButton(
            "−", { onChange(value?.let { if (it <= range.first) null else it - 1 }) },
            Modifier.height(CONTROL_HEIGHT), primary = false, enabled = value != null,
        )
        Text(
            if (value == null) "–" else "$value ${unit(value)}", style = EgType.title,
            color = if (value == null) c.muted else c.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        EgButton(
            "+", { onChange(if (value == null) range.first + 1 else (value + 1).coerceAtMost(range.last)) },
            Modifier.height(CONTROL_HEIGHT), primary = false, enabled = value == null || value < range.last,
        )
    }
}

/** A small lock + one muted line ("Private: stays on this device"). */
@Composable
fun LockedNote(text: String, modifier: Modifier = Modifier) {
    val c = Eg.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(EgIcon.LOCK, c.muted, size = 16.dp)
        Spacer(Modifier.width(EgSpace.xs))
        Text(text, style = EgType.label, color = c.muted)
    }
}
