package ch.lauzhack.energyguard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.coach.CoachMessage
import ch.lauzhack.energyguard.companion.CompanionJson
import ch.lauzhack.energyguard.companion.CompanionState
import ch.lauzhack.energyguard.companion.clampAppFields
import ch.lauzhack.energyguard.companion.phoneSignals
import ch.lauzhack.energyguard.companion.toAppState
import ch.lauzhack.energyguard.companion.toCompanionState
import ch.lauzhack.energyguard.ui.phone.PhoneLink
import ch.lauzhack.energyguard.ui.phone.PhoneProfileScreen
import ch.lauzhack.energyguard.ui.phone.PhoneShell
import ch.lauzhack.energyguard.ui.today.SignalsSnapshot
import ch.lauzhack.energyguard.ui.today.TodayActions
import ch.lauzhack.energyguard.ui.today.liveSignals
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the phone shell offscreen at 412x915 dp (a common Android phone) in both themes.
 * Live states go through the real companion JSON round trip (PC AppState -> JSON -> phone AppState), so the
 * picture is what a paired phone shows. The full scroll height is rendered too ("-full"), to check every card.
 */
class PhoneRenderTest {
    private val pc = ShellRenderTest().pcState().let { s ->
        // On the PC, AppState.coach and dashboard.coach come from the same EnergyCoach message; the wire carries the latter.
        s.copy(dashboard = s.dashboard.copy(coach = s.coach?.let { CoachMessage(it.text, it.fromLlm, it.kind) }))
    }

    /** PC state -> JSON -> what the phone builds from it. */
    private fun overTheWire(theme: ThemeMode): Pair<AppState, SignalsSnapshot> {
        val json = CompanionJson.encodeToString(CompanionState.serializer(), pc.copy(demo = false).toCompanionState(1L))
        val s = CompanionJson.decodeFromString(CompanionState.serializer(), json).clampAppFields()
        return s.toAppState(AppState(settings = pc.settings.copy(theme = theme)), "2026-11-21") to s.phoneSignals()
    }

    private fun render(name: String, h: Int = 915, content: @Composable () -> Unit) {
        val bytes = ImageComposeScene(412, h, Density(1f)).use { scene ->
            scene.setContent(content)
            scene.render(0)
            scene.render(2_000_000_000L)
            scene.render(4_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/screens/$name.png").apply { parentFile.mkdirs(); writeBytes(bytes) }
        assertTrue(out.length() > 10_000, "$name rendered")
    }

    private fun live(name: String, theme: ThemeMode, h: Int = 915) {
        val (state, signals) = overTheWire(theme)
        render(name, h) {
            PhoneShell(state, signals, PhoneLink.Live("192.168.1.20:8767"), Tab.TODAY, {}, TodayActions(), {}) {}
        }
    }

    @Test fun todayLiveDark() = live("phone-today-dark", ThemeMode.DARK)
    @Test fun todayLiveLight() = live("phone-today-light", ThemeMode.LIGHT)
    @Test fun todayLiveDarkFull() = live("phone-today-dark-full", ThemeMode.DARK, h = 2400)

    @Test fun todayNotConnected() = notConnected("phone-today-not-connected")
    @Test fun todayNotConnectedFull() = notConnected("phone-today-not-connected-full", h = 2600)

    private fun notConnected(name: String, h: Int = 915) {
        val demo = pc.copy(settings = pc.settings.copy(theme = ThemeMode.DARK))
        render(name, h) {
            PhoneShell(demo, demo.liveSignals(), PhoneLink.NotPaired, Tab.TODAY, {}, TodayActions(), {}) {}
        }
    }

    @Test fun todayOfflineLight() {
        val demo = pc.copy(settings = pc.settings.copy(theme = ThemeMode.LIGHT))
        render("phone-today-offline-light") {
            PhoneShell(demo, demo.liveSignals(), PhoneLink.Offline("192.168.1.20:8767"), Tab.TODAY, {}, TodayActions(), {}) {}
        }
    }

    @Test fun journalEmpty() {
        val demo = pc.copy(settings = pc.settings.copy(theme = ThemeMode.DARK))
        render("phone-journal-empty") {
            PhoneShell(demo, demo.liveSignals(), PhoneLink.NotPaired, Tab.JOURNAL, {}, TodayActions(), {}) {}
        }
    }

    @Test fun journalWithEntries() = journal("phone-journal-entries")
    @Test fun journalWithEntriesFull() = journal("phone-journal-entries-full", h = 2600)

    /** Paired: the PC's Today, the phone's own journal. Light theme to check both palettes on the phone. */
    private fun journal(name: String, h: Int = 915) {
        val (state, signals) = overTheWire(ThemeMode.LIGHT)
        val shown = ShellRenderTest().withJournal(state, count = 5)
        render(name, h) {
            PhoneShell(shown, signals, PhoneLink.Live("192.168.1.20:8767"), Tab.JOURNAL, {}, TodayActions(), {}) {}
        }
    }

    @Test fun profileNotPaired() {
        val demo = pc.copy(settings = pc.settings.copy(theme = ThemeMode.DARK))
        render("phone-profile-not-paired") {
            PhoneShell(demo, demo.liveSignals(), PhoneLink.NotPaired, Tab.PROFILE, {}, TodayActions(), {}) {
                PhoneProfileScreen(PhoneLink.NotPaired, "192.168.1.20", 8767, null, ThemeMode.DARK, {}, {}, sleepHours = 6.0, onSleepHours = {})
            }
        }
    }

    @Test fun profileLight() {
        val (state, signals) = overTheWire(ThemeMode.LIGHT)
        val link = PhoneLink.Live("192.168.1.20:8767")
        render("phone-profile-live-light") {
            PhoneShell(state, signals, link, Tab.PROFILE, {}, TodayActions(), {}) {
                PhoneProfileScreen(link, "192.168.1.20", 8767, null, ThemeMode.LIGHT, {}, {}, sleepHours = state.settings.sleepHours)
            }
        }
    }
}
