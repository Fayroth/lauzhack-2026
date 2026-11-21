package ch.lauzhack.energyguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Frugality counter (#14), the AXA "frugal AI" proof on screen. Matches the pitch:
 * "AI only on level changes, max 1 LLM call per 10 min, counter shows real calls, 0 images leave the PC".
 *  - LLM calls: the coach's real call count (failed attempts and offline templates don't count).
 *  - Images / audio sent, keystrokes recorded: 0 by design (only numbers leave the vision sidecar,
 *    the input collector only keeps timings).
 *  - Frames processed locally: counted here, one per vision update; resets on an F5 replay.
 */
@Composable
fun FrugalityStrip(state: DashboardState, modifier: Modifier = Modifier) {
    val frames = key(state.runId) { rememberFrameCount(state) }
    Row(
        modifier
            .background(Warm.Surface, RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Counter("LLM calls", state.llmCallCount.toString(), highlight = true)
        Counter("frames on this PC", groupThousands(frames))
        Counter("images sent", "0")
        Counter("audio sent", "0")
        Counter("keys recorded", "0")
    }
}

@Composable
private fun rememberFrameCount(state: DashboardState): Long {
    var count by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.vision) { if (state.vision != null) count++ } // each frame has its own ts
    return count
}

@Composable
private fun Counter(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = if (highlight) Warm.Brand else Warm.Text)
        Text(label, fontSize = 14.sp, color = Warm.Muted)
    }
}

/** 12345 -> "12 345" (thin, readable from far away; no locale API in common code). */
internal fun groupThousands(n: Long): String =
    n.toString().reversed().chunked(3).joinToString(" ").reversed()

/** Small "Light" / "Dark" switch for the header. */
@Composable
fun ThemeToggle(modifier: Modifier = Modifier) {
    Text(
        if (Warm.light) "Dark mode" else "Light mode",
        fontSize = 16.sp,
        color = Warm.Muted,
        modifier = modifier
            .clip(RoundedCornerShape(50)) // the click ripple takes the pill shape, not a rectangle
            .background(Warm.Surface)
            .clickable { Warm.light = !Warm.light }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
