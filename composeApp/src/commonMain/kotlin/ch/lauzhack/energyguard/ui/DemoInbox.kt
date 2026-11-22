package ch.lauzhack.energyguard.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgTheme
import ch.lauzhack.energyguard.ui.components.EgType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.guardrail.GuardedAction
import ch.lauzhack.energyguard.guardrail.LocalGuard
import ch.lauzhack.energyguard.guardrail.Verdict

/** One fake email. A link is optional: [linkText] is what the reader sees, [href] where it really goes. */
data class DemoEmail(
    val from: String,
    val subject: String,
    val time: String,
    val bodyBefore: String,
    val linkText: String? = null,
    val href: String? = null,
    val bodyAfter: String = "",
) {
    /** Passed to the Guardrail as `context` (subject + full body) so urgency words count. */
    val context: String get() = subject + "\n" + bodyBefore + " " + (linkText ?: "") + "\n" + bodyAfter
}

/** The demo inbox. Phishing + legit emails are exactly the ones in docs/API.md "Guardrail" (the tests use them). */
val DemoEmails = listOf(
    DemoEmail(
        from = "PayPal Security <service@paypa1-secure.com>",
        subject = "URGENT: your account will be suspended in 24 hours",
        time = "14:02",
        bodyBefore = "Dear customer,\n\nWe detected unusual activity on your account. " +
            "To avoid suspension, confirm your identity within 24 hours:",
        linkText = "https://www.paypal.com/signin",
        href = "https://paypa1-secure.com/signin",
        bodyAfter = "Thank you,\nPayPal Security Team",
    ),
    DemoEmail(
        from = "EPFL Mediacom <news@epfl.ch>",
        subject = "This week on campus",
        time = "11:30",
        bodyBefore = "Hello,\n\nNew research stories, events and campus life are online. Read them here:",
        linkText = "EPFL news",
        href = "https://actu.epfl.ch/news/",
        bodyAfter = "Have a good week,\nEPFL Mediacom",
    ),
    DemoEmail(
        from = "LauzHack Team <hello@lauzhack.com>",
        subject = "Dinner at 19:00 in the BC hall",
        time = "10:15",
        bodyBefore = "Hi hackers,\n\nDinner is served at 19:00 in the BC hall. Vegetarian options available. " +
            "Don't forget to drink water and take breaks!",
        bodyAfter = "The LauzHack Team",
    ),
    DemoEmail(
        from = "Sarah M. <sarah.m@epfl.ch>",
        subject = "Notes from Thursday's lecture",
        time = "Yesterday",
        bodyBefore = "Hey,\n\nI attached my notes from Thursday. The exercise session moved to Wednesday 10:15, room CO 2.",
        bodyAfter = "See you there,\nSarah",
    ),
)

/** What fills the reading pane. */
private sealed interface InboxScreen {
    data object Reading : InboxScreen
    data class PageOpened(val href: String) : InboxScreen
    data class Warning(val verdict: Verdict.Warn, val href: String) : InboxScreen
    data class Blocked(val verdict: Verdict.Block) : InboxScreen
}

// C2 tokens only: links in ice, Warn in the TIRED colour, Block on the EXHAUSTED fill with dark text.
private val LinkBlue: Color @Composable get() = Eg.colors.ice
private val BlockRed: Color @Composable get() = Eg.colors.exhaustedFill
private val WarnAmber: Color @Composable get() = Eg.colors.tired
private val OnBlock: Color @Composable get() = Eg.colors.onAccent

/**
 * Demo inbox for the guardrail scene (#13). Every link click goes through `LocalGuard.current`:
 * Allow -> "page opened", Warn -> confirm dialog, Block -> full red screen (main.kt vibrates the mouse).
 */
@Composable
fun DemoInbox() {
    val guard = LocalGuard.current
    var selected by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf<InboxScreen>(InboxScreen.Reading) }

    fun onLink(email: DemoEmail) {
        val text = email.linkText ?: return
        val href = email.href ?: return
        screen = when (val v = guard(GuardedAction.OpenLink(text, href, email.context))) {
            Verdict.Allow -> InboxScreen.PageOpened(href)
            is Verdict.Warn -> InboxScreen.Warning(v, href)
            is Verdict.Block -> InboxScreen.Blocked(v)
        }
    }

    EgTheme(if (Warm.light) ThemeMode.LIGHT else ThemeMode.DARK) {
        Box(Modifier.fillMaxSize().background(Warm.Night)) {
            Row(Modifier.fillMaxSize()) {
                // Left: the email list.
                Column(Modifier.weight(0.38f).fillMaxHeight().background(Warm.Deep).padding(vertical = 24.dp)) {
                    Text("Inbox", style = EgType.screenTitle, color = Warm.Text,
                        modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(16.dp))
                    DemoEmails.forEachIndexed { i, email ->
                        EmailRow(email, isSelected = i == selected) {
                            selected = i
                            screen = InboxScreen.Reading
                        }
                    }
                }
                // Right: reading pane.
                Box(Modifier.weight(0.62f).fillMaxHeight().padding(40.dp)) {
                    when (val s = screen) {
                        is InboxScreen.PageOpened -> PageOpenedView(s.href) { screen = InboxScreen.Reading }
                        else -> EmailView(DemoEmails[selected]) { onLink(DemoEmails[selected]) }
                    }
                }
            }
            when (val s = screen) {
                is InboxScreen.Warning -> WarnDialog(
                    s.verdict,
                    onBack = { screen = InboxScreen.Reading },
                    onOpenAnyway = { screen = InboxScreen.PageOpened(s.href) },
                )
                is InboxScreen.Blocked -> BlockScreen(s.verdict) { screen = InboxScreen.Reading }
                else -> {}
            }
        }
    }
}

@Composable
private fun EmailRow(email: DemoEmail, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(if (isSelected) Eg.colors.raised else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(email.from.substringBefore(" <"), style = EgType.bodyStrong.copy(fontSize = 21.sp, lineHeight = 28.sp),
                color = Warm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(email.time, fontSize = 18.sp, color = Warm.Muted)
        }
        Text(email.subject, fontSize = 20.sp, color = Warm.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmailView(email: DemoEmail, onLinkClick: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(email.subject, style = EgType.screenTitle, color = Warm.Text)
        Spacer(Modifier.height(12.dp))
        Text("From: ${email.from}", fontSize = 22.sp, color = Warm.Muted)
        Spacer(Modifier.height(32.dp))
        Text(email.bodyBefore, fontSize = 26.sp, color = Warm.Text, lineHeight = 36.sp)
        if (email.linkText != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                email.linkText, fontSize = 28.sp, color = LinkBlue, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onLinkClick).padding(vertical = 8.dp),
            )
        }
        if (email.bodyAfter.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(email.bodyAfter, fontSize = 26.sp, color = Warm.Text, lineHeight = 36.sp)
        }
    }
}

@Composable
private fun PageOpenedView(href: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Page opened", style = EgType.screenTitle.copy(fontSize = 44.sp, lineHeight = 50.sp), color = Warm.Brand)
        Spacer(Modifier.height(16.dp))
        Text(href, fontSize = 26.sp, color = Warm.Muted)
        Spacer(Modifier.height(40.dp))
        BigButton("Back to inbox", Eg.colors.raised, onBack, textColor = Warm.Text)
    }
}

@Composable
private fun Signals(signals: List<String>, color: Color) {
    signals.forEach { Text("•  $it", fontSize = 26.sp, color = color, lineHeight = 34.sp, modifier = Modifier.padding(vertical = 4.dp)) }
}

/** Warn: "are you sure?" over a dark scrim. */
@Composable
private fun WarnDialog(v: Verdict.Warn, onBack: () -> Unit, onOpenAnyway: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 900.dp)
                .background(Brush.verticalGradient(listOf(Eg.colors.cardTop, Eg.colors.cardBottom)), RoundedCornerShape(EgSpace.radius))
                .border(1.dp, Eg.colors.border, RoundedCornerShape(EgSpace.radius)).padding(40.dp)
        ) {
            Text("Are you sure?", fontSize = 26.sp, color = WarnAmber, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(v.reason, style = EgType.screenTitle.copy(fontSize = 38.sp, lineHeight = 46.sp), color = Warm.Text)
            Spacer(Modifier.height(20.dp))
            Signals(v.signals, Warm.Text)
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("Go back", Warm.Brand, onBack)
                BigButton("Open anyway", Eg.colors.raised, onOpenAnyway, textColor = Warm.Muted)
            }
        }
    }
}

/** Block: full red screen, readable from the back of the room. Only way out is "Go back". */
@Composable
private fun BlockScreen(v: Verdict.Block, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(BlockRed).clickable(enabled = false) {}.padding(64.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("BLOCKED BY ENERGYGUARD", style = EgType.ringName.copy(fontSize = 28.sp, lineHeight = 34.sp), color = OnBlock.copy(alpha = 0.8f))
        Spacer(Modifier.height(16.dp))
        Text(v.reason, style = EgType.screenTitle.copy(fontSize = 52.sp, lineHeight = 62.sp), color = OnBlock)
        Spacer(Modifier.height(32.dp))
        Signals(v.signals, OnBlock)
        Spacer(Modifier.height(48.dp))
        BigButton("Go back", OnBlock, onBack, textColor = BlockRed)
    }
}

@Composable
private fun BigButton(label: String, color: Color, onClick: () -> Unit, textColor: Color = Eg.colors.onAccent) {
    Button(
        onClick = onClick, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier.border(1.dp, Eg.colors.border, CircleShape),
    ) {
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = textColor, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
