package ch.lauzhack.energyguard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A tab that is not built yet: same card style, centered. */
@Composable
fun PlaceholderScreen(icon: EgIcon, title: String, description: String, modifier: Modifier = Modifier) {
    val c = Eg.colors
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EgCard(Modifier.widthIn(max = 560.dp)) {
            androidx.compose.foundation.layout.Column(
                Modifier.widthIn(min = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EgSpace.s),
            ) {
                Icon(icon, c.accent, size = 48.dp)
                Text(title, style = EgType.value, color = c.text)
                Text(description, style = EgType.lead, color = c.muted, textAlign = TextAlign.Center)
                Pill("Coming in the next build", c.accent)
            }
        }
    }
}
