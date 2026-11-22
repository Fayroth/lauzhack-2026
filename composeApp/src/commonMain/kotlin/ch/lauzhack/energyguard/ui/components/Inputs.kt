package ch.lauzhack.energyguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Touch height of every control (>= 44 dp) and the C2 pill shape. */
private val CONTROL_HEIGHT = 48.dp
private val CONTROL_SHAPE = CircleShape

/**
 * A numbered scale (KSS 1..9, stress 1..5): one equal-width pill per value; selected = lime with black text,
 * others = raised chip with a 1 px border.
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
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Every cell >= 44 dp wide: on a phone KSS 1..9 does not fit one row, so it wraps into rows of 5.
            val values = range.toList()
            val oneRow = (maxWidth - EgSpace.xs * (values.size - 1)) / values.size >= EgSpace.touch
            val perRow = if (oneRow) values.size else 5
            Column(verticalArrangement = Arrangement.spacedBy(EgSpace.xs)) {
                values.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EgSpace.xs)) {
                        row.forEach { v ->
                            val selected = v == value
                            Box(
                                Modifier.weight(1f).height(CONTROL_HEIGHT).clip(CONTROL_SHAPE)
                                    .background(if (selected) c.accent else c.raised)
                                    .border(1.dp, if (selected) c.accent else c.border, CONTROL_SHAPE)
                                    .selectable(selected, role = Role.RadioButton) { onChange(if (selected) null else v) },
                                contentAlignment = Alignment.Center,
                            ) { Text("$v", style = EgType.bodyStrong, color = if (selected) c.onAccent else c.text) }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) } // last row keeps the cell width
                    }
                }
            }
        }
        Spacer(Modifier.height(EgSpace.xs))
        Row(Modifier.fillMaxWidth()) {
            Text(lowLabel, style = EgType.small, color = c.label)
            Spacer(Modifier.weight(1f))
            Text(highLabel, style = EgType.small, color = c.label, textAlign = TextAlign.End)
        }
    }
}

/**
 * − value + for a small count (cups of coffee). null = not set (shows "0", muted); − from [range].first clears it, + from null
 * starts at [range].first + 1.
 */
@Composable
fun Stepper(value: Int?, range: IntRange, onChange: (Int?) -> Unit, unit: (Int) -> String, modifier: Modifier = Modifier) {
    val c = Eg.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        EgButton(
            "−", { onChange(value?.let { if (it <= range.first) null else it - 1 }) },
            Modifier.size(CONTROL_HEIGHT), primary = false, enabled = value != null, contentPadding = 0.dp,
        )
        Text(
            if (value == null) "0 ${unit(0)}" else "$value ${unit(value)}", style = EgType.bodyStrong,
            color = if (value == null) c.muted else c.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        EgButton(
            "+", { onChange(if (value == null) range.first + 1 else (value + 1).coerceAtMost(range.last)) },
            Modifier.size(CONTROL_HEIGHT), primary = false, enabled = value == null || value < range.last, contentPadding = 0.dp,
        )
    }
}

/** A small lock + one muted line ("Private: stays on this device"). */
@Composable
fun LockedNote(text: String, modifier: Modifier = Modifier) {
    val c = Eg.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(EgIcon.LOCK, c.label, size = 16.dp)
        Spacer(Modifier.width(EgSpace.xs))
        Text(text, style = EgType.small, color = c.label)
    }
}

/**
 * C2 switch: 52x32 pill track, lime with a black knob when on, raised with a border and a muted knob when off.
 * Drawing only: put it in a [SettingRow] (the whole row is the touch target).
 */
@Composable
fun EgSwitch(checked: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val c = Eg.colors
    val track by animateColorAsState(if (checked && enabled) c.accent else c.raised, tween(EG_ANIM_MS / 2), label = "switchTrack")
    val knobX by animateDpAsState(if (checked) 24.dp else 4.dp, tween(EG_ANIM_MS / 2), label = "switchKnob")
    Box(
        modifier.width(52.dp).height(32.dp).clip(CircleShape).background(track)
            .then(if (checked && enabled) Modifier else Modifier.border(1.dp, c.border, CircleShape)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = knobX).size(24.dp)
                .background(if (checked && enabled) c.onAccent else if (enabled) c.muted else c.track, CircleShape),
        )
    }
}

/**
 * One settings line: title (+ optional secondary line) on the left, a switch on the right. The whole row toggles
 * (Role.Switch, >= 48 dp). [enabled] = false greys the switch and ignores taps (e.g. "offline (no key)").
 */
@Composable
fun SettingRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val c = Eg.colors
    Row(
        modifier.fillMaxWidth().height(if (subtitle != null) 64.dp else CONTROL_HEIGHT)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = EgType.bodyStrong, color = if (enabled) c.text else c.muted, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = EgType.small, color = c.label, maxLines = 1)
        }
        Spacer(Modifier.width(EgSpace.s))
        EgSwitch(checked, enabled)
    }
}
