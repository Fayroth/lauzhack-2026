package ch.lauzhack.energyguard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.CoachNote
import ch.lauzhack.energyguard.model.Level

/** Card surface: 16 dp radius, 24 dp padding, no border; a soft shadow in light mode, a lighter surface in dark mode. */
@Composable
fun EgCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Eg.colors
    val shape = RoundedCornerShape(EgSpace.radius)
    Column(
        modifier
            .shadow(
                if (c.isDark) 0.dp else 6.dp, shape, clip = false,
                ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.10f),
            )
            .background(c.surface, shape)
            .padding(EgSpace.cardPadding),
        content = content,
    )
}

/** Card title row: title on the left, optional trailing content (chip, tag) on the right. */
@Composable
fun CardTitle(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = EgType.title, color = Eg.colors.muted)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** Small rounded pill (tags, badges). */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, dot: Boolean = false, filled: Boolean = false) {
    val c = Eg.colors
    Row(
        modifier
            .background(if (filled) color else color.copy(alpha = if (c.isDark) 0.16f else 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = EgType.label, color = if (filled) c.onAccent else color)
    }
}

/** Level of the user, in its state color. Animates color on level changes. */
@Composable
fun LevelChip(level: Level, modifier: Modifier = Modifier, calibrating: Boolean = false) {
    val c = Eg.colors
    val color by animateColorAsState(if (calibrating) c.muted else c.level(level), tween(EG_ANIM_MS), label = "levelChip")
    Row(
        modifier
            .background(color.copy(alpha = if (c.isDark) 0.16f else 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(EgSpace.xs))
        Text(if (calibrating) "Calibrating" else levelLabel(level), style = EgType.title, color = color)
    }
}

/** Primary (accent, filled) or secondary (tonal) button. */
@Composable
fun EgButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    val c = Eg.colors
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = EgSpace.m, vertical = 12.dp),
        elevation = null,
        colors = if (primary) {
            ButtonDefaults.buttonColors(c.accent, c.onAccent, disabledContainerColor = c.raised, disabledContentColor = c.muted)
        } else {
            ButtonDefaults.buttonColors(c.raised, c.text, disabledContainerColor = c.raised, disabledContentColor = c.muted)
        },
    ) { Text(text, style = EgType.bodyStrong) }
}

/**
 * A titled card with one big value (48 sp). [unit] is drawn smaller next to the value ("82" "%").
 * [content] goes under the value (bar, breakdown, button).
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = Eg.colors.text,
    trailing: @Composable RowScope.() -> Unit = {},
    beside: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    EgCard(modifier) {
        CardTitle(title, trailing)
        Spacer(Modifier.size(EgSpace.xs))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(value)
                    if (unit != null) withStyle(SpanStyle(fontSize = EgType.title.fontSize, color = Eg.colors.muted)) { append(unit) }
                },
                style = EgType.value, color = valueColor,
            )
            beside()
        }
        content()
    }
}

/** Bottom-row tile: big value with small units, label under it. */
@Composable
fun StatTile(
    parts: List<DurationPart>,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Eg.colors.text,
    labelMaxLines: Int = 1,
) {
    val c = Eg.colors
    EgCard(modifier) {
        Text(
            buildAnnotatedString {
                parts.forEachIndexed { i, p ->
                    if (i > 0) append(" ")
                    append(p.number)
                    if (p.unit.isNotEmpty()) withStyle(SpanStyle(fontSize = EgType.title.fontSize, color = c.muted)) { append(p.unit) }
                }
            },
            style = EgType.value, color = valueColor, maxLines = 1,
        )
        Text(label, style = EgType.label, color = c.muted, maxLines = labelMaxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Eg.colors.text, labelMaxLines: Int = 1) =
    StatTile(listOf(DurationPart(value, "")), label, modifier, valueColor, labelMaxLines)

/** Last coach note with its "AI" / "offline" tag; calm empty state when there is none. */
@Composable
fun CoachCard(note: CoachNote?, modifier: Modifier = Modifier, format: (String) -> String = { it }, maxLines: Int = 2) {
    val c = Eg.colors
    EgCard(modifier) {
        CardTitle("Coach") {
            if (note != null) Pill(note.tag, if (note.fromLlm) c.accent else c.muted)
        }
        Spacer(Modifier.size(EgSpace.xs))
        if (note == null) {
            Text("No advice needed. You're doing fine.", style = EgType.lead, color = c.muted)
        } else {
            Text(format(note.text), style = EgType.lead, color = c.text, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Vertical stack of plain-word lines with a small colored bullet. */
@Composable
fun BulletList(lines: List<String>, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(EgSpace.xs)) {
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(line, style = EgType.body, color = Eg.colors.text)
            }
        }
    }
}
