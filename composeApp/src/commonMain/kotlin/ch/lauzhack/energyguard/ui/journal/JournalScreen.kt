package ch.lauzhack.energyguard.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.JournalEntry
import ch.lauzhack.energyguard.app.localClockTime
import ch.lauzhack.energyguard.app.localDayKey
import ch.lauzhack.energyguard.ui.components.CardTitle
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgButton
import ch.lauzhack.energyguard.ui.components.EgCard
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.LockedNote
import ch.lauzhack.energyguard.ui.components.Pill
import ch.lauzhack.energyguard.ui.components.ScalePicker
import ch.lauzhack.energyguard.ui.components.Stepper
import ch.lauzhack.energyguard.ui.components.tracksTextFocus

/** User actions of the Journal tab, wired to AppStore.addJournalEntry / deleteJournalEntry by the shell. */
class JournalActions(
    val onSave: (kss: Int?, stress: Int?, caffeine: Int?, note: String?) -> Unit = { _, _, _, _ -> },
    val onDelete: (id: Long) -> Unit = {},
)

/** What the form starts with (empty in the app; render tests prefill it). */
data class JournalDraft(val kss: Int? = null, val stress: Int? = null, val caffeine: Int? = null, val note: String = "")

private const val DAY_MS = 86_400_000L

/**
 * Tab 2 "Journal" (M4), shared by both shells: check-in form (KSS, stress, caffeine, private note, all optional)
 * and the history next to what we measured. [wide] = desktop (form left, history right), else one column (phone).
 * [savedLabel] = where entries live, under the Save button ("Saved on this phone"); null = nothing.
 */
@Composable
fun JournalScreen(
    state: AppState,
    actions: JournalActions,
    wide: Boolean,
    modifier: Modifier = Modifier,
    savedLabel: String? = null,
    draft: JournalDraft = JournalDraft(),
) {
    val today = state.stats.day
    if (wide) {
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(EgSpace.s)) {
            Column(Modifier.weight(0.46f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                CheckInForm(actions, savedLabel, draft, wide = true)
            }
            LazyColumn(Modifier.weight(0.54f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EgSpace.s)) {
                history(state.journal, today, actions, wide = true)
            }
        }
    } else {
        LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = EgSpace.s, vertical = EgSpace.xs),
            verticalArrangement = Arrangement.spacedBy(EgSpace.s),
        ) {
            item { CheckInForm(actions, savedLabel, draft, wide = false) }
            history(state.journal, today, actions, wide = false)
        }
    }
}

@Composable
private fun CheckInForm(actions: JournalActions, savedLabel: String?, draft: JournalDraft, wide: Boolean) {
    val c = Eg.colors
    var kss by remember { mutableStateOf(draft.kss) }
    var stress by remember { mutableStateOf(draft.stress) }
    var caffeine by remember { mutableStateOf(draft.caffeine) }
    var note by remember { mutableStateOf(draft.note) }
    val gap = if (wide) EgSpace.m else EgSpace.s
    EgCard(Modifier.fillMaxWidth()) {
        CardTitle("How do you feel?") { Pill("All optional", c.muted) }
        Spacer(Modifier.height(gap))

        Question("Sleepiness", kss?.let { "$it · ${KSS_LABELS[it]}" })
        ScalePicker(kss, 1..9, { kss = it }, "1 very alert", "9 fighting sleep")
        Spacer(Modifier.height(gap))

        Question("Stress", stress?.let { "$it · ${STRESS_LABELS[it]}" })
        ScalePicker(stress, 1..5, { stress = it }, "1 calm", "5 overwhelmed")
        Spacer(Modifier.height(gap))

        Question("Caffeine today", null)
        Stepper(caffeine, 0..JournalEntry.CAFFEINE_RANGE.last, { caffeine = it }, unit = { if (it == 1) "cup" else "cups" })
        Spacer(Modifier.height(gap))

        OutlinedTextField(
            note, { note = it.take(JournalEntry.MAX_NOTE_CHARS) },
            Modifier.fillMaxWidth().tracksTextFocus(),
            label = { Text("Note") },
            placeholder = { Text("What's going on? (optional)") },
            minLines = if (wide) 3 else 2, maxLines = 4,
        )
        Spacer(Modifier.height(EgSpace.xs))
        LockedNote("Private: stays on this device, never sent to the AI.")
        Spacer(Modifier.height(gap))

        EgButton(
            "Save check-in",
            {
                actions.onSave(kss, stress, caffeine, note.trim().ifEmpty { null })
                kss = null; stress = null; caffeine = null; note = ""
            },
            Modifier.fillMaxWidth(),
            enabled = canSave(kss, stress, caffeine, note),
        )
        if (savedLabel != null) {
            Spacer(Modifier.height(EgSpace.xs))
            Text(savedLabel, style = EgType.label, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Question name on the left, the chosen value in words on the right. */
@Composable
private fun Question(title: String, chosen: String?) {
    val c = Eg.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = EgType.bodyStrong, color = c.text)
        Spacer(Modifier.weight(1f))
        if (chosen != null) Text(chosen, style = EgType.body, color = c.accent)
    }
    Spacer(Modifier.height(EgSpace.xs))
}

/** Comparison card, then the entries newest first (or a calm empty card). */
private fun LazyListScope.history(entries: List<JournalEntry>, today: String, actions: JournalActions, wide: Boolean) {
    item { ComparisonCard(entries) }
    if (entries.isEmpty()) {
        item {
            EgCard(Modifier.fillMaxWidth()) {
                Text("No check-ins yet", style = EgType.title, color = Eg.colors.text)
                Spacer(Modifier.height(EgSpace.xs))
                Text(
                    "Rate how you feel a few times a day: we put it next to what the camera, mouse and keyboard measured.",
                    style = EgType.body, color = Eg.colors.muted,
                )
            }
        }
    } else {
        items(entries, key = { it.id }) { EntryCard(it, today, actions, wide) }
    }
}

@Composable
private fun ComparisonCard(entries: List<JournalEntry>) {
    val c = Eg.colors
    EgCard(Modifier.fillMaxWidth()) {
        CardTitle("You vs our measure")
        Spacer(Modifier.height(EgSpace.xs))
        Text(comparisonText(entries), style = EgType.lead, color = c.text)
        Spacer(Modifier.height(EgSpace.xs))
        Text("Your sleepiness (1-9) against our fatigue score at the same moment.", style = EgType.label, color = c.muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(e: JournalEntry, today: String, actions: JournalActions, wide: Boolean) {
    val c = Eg.colors
    val day = localDayKey(e.ts)
    val isYesterday = localDayKey(e.ts + DAY_MS) == today
    val whenLabel = whenText(day, localClockTime(e.ts), today, yesterday = if (isYesterday) day else "")
    val chips = entryChips(e)
    EgCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(whenLabel, style = EgType.bodyStrong, color = c.text)
            if (wide) {
                Spacer(Modifier.width(EgSpace.s))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(EgSpace.xs)) {
                    chips.forEach { Pill(it, c.accent) }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            EgButton("Delete", { actions.onDelete(e.id) }, primary = false)
        }
        if (!wide && chips.isNotEmpty()) {
            Spacer(Modifier.height(EgSpace.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(EgSpace.xs), verticalArrangement = Arrangement.spacedBy(EgSpace.xs)) {
                chips.forEach { Pill(it, c.accent) }
            }
        }
        Spacer(Modifier.height(EgSpace.xs))
        Text(measuredLine(e), style = EgType.body, color = c.text)
        e.note?.let {
            Spacer(Modifier.height(EgSpace.xs))
            Text(it, style = EgType.body, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
