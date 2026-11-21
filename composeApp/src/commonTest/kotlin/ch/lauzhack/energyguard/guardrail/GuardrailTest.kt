package ch.lauzhack.energyguard.guardrail

import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GuardrailTest {
    private val guardrail = Guardrail()

    private fun state(level: Level, score: Int) = FatigueState(score, level, emptyList(), calibrated = true)
    private val fresh = state(Level.FRESH, 10)
    private val ok = state(Level.OK, 40)
    private val tired = state(Level.TIRED, 65)
    private val exhausted = state(Level.EXHAUSTED, 90)

    /** The demo inbox phishing email (docs/API.md). */
    private val demoPhishing = GuardedAction.OpenLink(
        displayText = "https://www.paypal.com/signin",
        href = "https://paypa1-secure.com/signin",
        context = "URGENT: your account will be suspended in 24 hours\nDear customer, we noticed unusual activity...",
    )
    private val legit = GuardedAction.OpenLink(displayText = "EPFL news", href = "https://actu.epfl.ch/news/")

    private fun link(href: String, text: String = "click here", context: String = "") = GuardedAction.OpenLink(text, href, context)

    @Test fun demoPhishingIsBlockedWhenTired() {
        val v = guardrail.check(demoPhishing, tired)
        assertIs<Verdict.Block>(v)
        assertTrue("35%" in v.reason, v.reason)
        assertTrue(v.signals.any { "says paypal.com" in it && "paypa1-secure.com" in it }, v.signals.toString())
        assertTrue(v.signals.any { "imitates paypal.com" in it }, v.signals.toString())
        assertTrue(v.signals.any { "Pressure words" in it }, v.signals.toString())
        assertIs<Verdict.Block>(guardrail.check(demoPhishing, exhausted))
    }

    @Test fun samePhishingOnlyWarnsWhenFresh() {
        assertIs<Verdict.Warn>(guardrail.check(demoPhishing, fresh))
        assertIs<Verdict.Warn>(guardrail.check(demoPhishing, ok))
    }

    @Test fun legitLinkIsAllowedAtEveryLevel() {
        for (s in listOf(fresh, ok, tired, exhausted)) assertEquals(Verdict.Allow, guardrail.check(legit, s))
        assertEquals(Verdict.Allow, guardrail.check(link("https://www.paypal.com/myaccount", "https://www.paypal.com"), tired))
        assertEquals(Verdict.Allow, guardrail.check(link("https://github.com/Fayroth"), tired))
    }

    @Test fun unknownLinkWarnsWhenTiredAndPassesWhenFresh() {
        val unknown = link("https://example.org/article", "Read the article")
        assertEquals(Verdict.Allow, guardrail.check(unknown, fresh))
        assertIs<Verdict.Warn>(guardrail.check(unknown, tired))
    }

    @Test fun strongSignalsAreDetected() {
        val strongLinks = listOf(
            link("https://xn--pypal-4ve.com/login"),                    // punycode
            link("http://192.168.4.20/login"),                          // raw IP
            link("https://rnicrosoft.com/reset"),                       // rn -> m
            link("https://g00gle.com"),                                 // 0 -> o
            link("https://paypal.com.secure-login.xyz/"),               // brand in a subdomain
            link("https://www.paypal.com@evil.example/"),               // user-info trick
            link("https://evil.example/", text = "www.epfl.ch/moodle"), // text != href
            link("https://evil.example/", text = "Log in to PostFinance"),
        )
        for (l in strongLinks) {
            assertIs<Verdict.Block>(guardrail.check(l, tired), l.href)
            assertIs<Verdict.Warn>(guardrail.check(l, fresh), l.href)
        }
    }

    @Test fun weakSignalsOnlyMatterWhenTired() {
        val weakLinks = listOf(
            link("https://bit.ly/3xYz"),
            link("http://example.org/"),
            link("https://cheap-deals.xyz/"),
            link("https://example.org/", context = "Act now, final notice!"),
        )
        for (l in weakLinks) {
            assertEquals(Verdict.Allow, guardrail.check(l, fresh), l.href)
            assertIs<Verdict.Warn>(guardrail.check(l, tired), l.href)
        }
    }

    @Test fun noFalseAlarmOnFileNamesOrShortBrandsInWords() {
        assertEquals(Verdict.Allow, guardrail.check(link("https://www.epfl.ch/files/x", text = "report.pdf"), tired))
        // "ubs" inside "subscribe" is not an impersonation: only the weak "unknown domain" signal remains.
        assertIs<Verdict.Warn>(guardrail.check(link("https://subscribe-news.example/"), tired))
        assertEquals(Verdict.Allow, guardrail.check(link("https://subscribe-news.example/"), fresh))
    }

    @Test fun paymentWarnsOnlyWhenTired() {
        val pay = GuardedAction.Payment("CH93 0076 2011 6238 5295 7", 120.0)
        assertEquals(Verdict.Allow, guardrail.check(pay, fresh))
        assertIs<Verdict.Warn>(guardrail.check(pay, tired))
    }
}
