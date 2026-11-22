package ch.lauzhack.energyguard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.CoachNote
import ch.lauzhack.energyguard.model.Level

/**
 * C2 card: vertical gradient fill (cardTop -> cardBottom), 1 px border, 1 px top inner highlight (dark).
 * [hero] = 32 dp radius + soft drop shadow; others 28 dp. [padding] 24 dp by default.
 */
@Composable
fun EgCard(
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    padding: Dp = EgSpace.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Eg.colors
    val radius = if (hero) EgSpace.heroRadius else EgSpace.radius
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .then(
                if (hero) Modifier.shadow(
                    24.dp, shape, clip = false,
                    ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.5f else 0.06f),
                    spotColor = Color.Black.copy(alpha = if (c.isDark) 0.6f else 0.10f),
                ) else Modifier,
            )
            .background(Brush.verticalGradient(listOf(c.cardTop, c.cardBottom)), shape)
            .border(1.dp, c.border, shape)
            .topHighlight(c.highlight, radius)
            .padding(padding),
        content = content,
    )
}

/** 1 px line along the top edge, following the rounded corners (only the top part of the outline is drawn). */
private fun Modifier.topHighlight(color: Color, radius: Dp) = if (color.alpha == 0f) this else drawWithContent {
    drawContent()
    val r = radius.toPx()
    val w = 1.dp.toPx()
    clipRect(bottom = r * 0.9f) {
        drawRoundRect(
            color, topLeft = Offset(w * 1.5f, w * 1.5f), size = Size(size.width - w * 3, size.height - w * 3),
            cornerRadius = CornerRadius(r - w * 1.5f), style = Stroke(w),
        )
    }
}

/** Card title row: title on the left, optional trailing content on the right. */
@Composable
fun CardTitle(title: String, leading: @Composable RowScope.() -> Unit = {}, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Text(title, style = EgType.title, color = Eg.colors.text)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/**
 * Small pill (999 radius). Default = raised chip (#131316) with secondary text. [color] tints it (14 % fill, coloured
 * text) in dark; in light the text stays dark for contrast. [filled] = solid colour with dark text (state chip).
 */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color? = null, dot: Color? = null, filled: Boolean = false) {
    val c = Eg.colors
    val bg = when {
        filled && color != null -> color
        color != null -> color.copy(alpha = 0.14f)
        else -> c.raised
    }
    val fg = when {
        filled -> c.onAccent
        color != null && c.isDark -> color
        color != null -> c.text
        else -> c.muted
    }
    Row(
        modifier
            .background(bg, CircleShape)
            .then(if (color == null) Modifier.border(1.dp, c.border, CircleShape) else Modifier)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            GlowDot(dot, 8.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = EgType.label, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A dot with a soft halo (two wider translucent circles, no blur API): the live dot. */
@Composable
fun GlowDot(color: Color, size: Dp = 8.dp, glow: Boolean = true) {
    Box(Modifier.size(size).drawWithContent {
        if (glow) {
            drawCircle(color.copy(alpha = 0.12f), radius = this.size.minDimension * 1.15f)
            drawCircle(color.copy(alpha = 0.2f), radius = this.size.minDimension * 0.8f)
        }
        drawCircle(color, radius = this.size.minDimension / 2)
    })
}

/**
 * State chip: filled state colour, dark text ("Tired"). Muted raised chip while calibrating, with the countdown
 * when [secondsLeft] is known ("Calibrating… 12 s").
 */
@Composable
fun LevelChip(level: Level, modifier: Modifier = Modifier, calibrating: Boolean = false, secondsLeft: Int? = null) {
    val c = Eg.colors
    val fill by animateColorAsState(if (calibrating) c.raised else c.levelFill(level), tween(EG_ANIM_MS), label = "levelChip")
    Box(
        modifier.background(fill, CircleShape)
            .then(if (calibrating) Modifier.border(1.dp, c.border, CircleShape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            when {
                calibrating && secondsLeft != null -> "Calibrating… $secondsLeft s"
                calibrating -> "Calibrating"
                else -> levelLabel(level)
            },
            style = EgType.bodyStrong,
            color = if (calibrating) c.muted else c.onAccent, maxLines = 1,
        )
    }
}

/** Primary = lime pill with black text; secondary = raised pill (#131316) with a 1 px border. >= 44 dp high. */
@Composable
fun EgButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    contentPadding: Dp = 22.dp,
) {
    val c = Eg.colors
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = EgSpace.touch)
            .then(if (!primary || !enabled) Modifier.border(1.dp, c.border, CircleShape) else Modifier),
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = contentPadding, vertical = if (contentPadding == 0.dp) 0.dp else 10.dp),
        elevation = null,
        colors = if (primary) {
            ButtonDefaults.buttonColors(c.accent, c.onAccent, disabledContainerColor = c.raised, disabledContentColor = c.label)
        } else {
            ButtonDefaults.buttonColors(c.raised, c.text, disabledContainerColor = c.raised, disabledContentColor = c.label)
        },
    ) { Text(text, style = EgType.bodyStrong, maxLines = 1) }
}

/** A 4-point sparkle (coach icon), lime. */
@Composable
fun Sparkle(color: Color, size: Dp = 18.dp) {
    Box(Modifier.size(size).drawWithContent {
        val s = this.size.minDimension
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(s / 2, 0f)
            quadraticTo(s * 0.56f, s * 0.44f, s, s / 2)
            quadraticTo(s * 0.56f, s * 0.56f, s / 2, s)
            quadraticTo(s * 0.44f, s * 0.56f, 0f, s / 2)
            quadraticTo(s * 0.44f, s * 0.44f, s / 2, 0f)
            close()
        }
        drawPath(p, color)
    })
}

/**
 * Coach card: lime sparkle, "AI · 23:38" / "offline", the sentence in Bricolage 24, the frugality footer.
 * Calm empty state when there is no advice.
 */
@Composable
fun CoachCard(
    note: CoachNote?,
    modifier: Modifier = Modifier,
    format: (String) -> String = { it },
    maxLines: Int = 4,
    time: String? = null,
) {
    val c = Eg.colors
    EgCard(modifier) {
        CardTitle(
            "Coach",
            leading = { Sparkle(c.accent); Spacer(Modifier.width(10.dp)) },
        ) {
            if (note != null) {
                Text(
                    if (note.fromLlm && time != null) "AI · $time" else note.tag,
                    style = EgType.label, color = if (note.fromLlm) c.text else c.label,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (note == null) {
            Text("No advice needed. You're doing fine.", style = EgType.coach, color = c.muted)
        } else {
            Text(format(note.text), style = EgType.coach, color = c.text, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f)) // footer at the bottom when the card is stretched (desktop row)
        Spacer(Modifier.height(EgSpace.s))
        Text("Sent numbers only · max 1 AI call per 10 min", style = EgType.small, color = c.label)
    }
}

/** Vertical stack of plain-word lines with a small coloured bullet. */
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

/** A 1 px horizontal divider in the border colour. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Eg.colors.border))
}

/** A stat for the quiet stats line: number (white, 20 sp) + label (secondary). */
data class QuietStat(val number: String, val label: String)

@Composable
fun QuietStatText(stat: QuietStat, modifier: Modifier = Modifier, labelMaxLines: Int = 1) {
    val c = Eg.colors
    Row(modifier, verticalAlignment = if (labelMaxLines > 1) Alignment.Top else Alignment.Bottom) {
        Text(stat.number, style = EgType.stat, color = c.text, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(
            stat.label, style = EgType.label, color = c.muted, maxLines = labelMaxLines,
            modifier = Modifier.padding(top = if (labelMaxLines > 1) 4.dp else 0.dp, bottom = 2.dp),
        )
    }
}
