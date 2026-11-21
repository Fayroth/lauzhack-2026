package ch.lauzhack.energyguard.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.CoachNote
import ch.lauzhack.energyguard.app.Concentration
import ch.lauzhack.energyguard.app.DayStats
import ch.lauzhack.energyguard.app.EnergyState
import ch.lauzhack.energyguard.app.Settings
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.coach.Intervention
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.ui.today.TodayActions
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the desktop shell offscreen (1440x860, the 1440x900 window minus the title bar) in both themes and a few
 * states: a smoke test that every screen composes, plus PNGs in build/screens/ to check against the visual rules
 * (copied to docs/screens/ by hand).
 */
class ShellRenderTest {
    private val tired = FatigueState(68, Level.TIRED, listOf("eyes closing +347%", "long blinks +80%", "slower typing +32%"), calibrated = true)

    private fun sample(theme: ThemeMode, fatigue: FatigueState = tired, onBreak: Boolean = false, coach: Boolean = true) = AppState(
        dashboard = DashboardState(fatigue = fatigue),
        concentration = Concentration.from(fatigue),
        energy = EnergyState(battery = 62, onBreak = onBreak, startFromSleep = 90),
        recovery = 71,
        coach = if (coach) CoachNote("Your eyes are closing 4x more than this morning. Take 5 minutes away from the screen.", Intervention.BREAK, fromLlm = true) else null,
        stats = DayStats(day = "2026-11-21", focusedMs = 4_320_000, breaksTaken = 1, breaksNeeded = 1, longestFocusMs = 2_580_000, phishingBlocked = 1, llmCalls = 2),
        settings = Settings(sleepHours = 6.0, theme = theme),
        demo = true,
    )

    private fun render(name: String, state: AppState, tab: Tab = Tab.TODAY, w: Int = 1440, h: Int = 860) {
        val bytes = ImageComposeScene(w, h, Density(1f)).use { scene ->
            scene.setContent { DesktopShell(state, tab, {}, TodayActions(), {}, footer = "Phone: 192.168.1.20:8767 · code 123456") }
            scene.render(0)
            scene.render(2_000_000_000L) // let tab indicator / value animations settle
            scene.render(4_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/screens/$name.png").apply { parentFile.mkdirs(); writeBytes(bytes) }
        assertTrue(out.length() > 10_000, "$name rendered")
    }

    @Test fun todayDark() = render("today-dark", sample(ThemeMode.DARK))
    @Test fun todayLight() = render("today-light", sample(ThemeMode.LIGHT))
    @Test fun todayFreshOnBreak() = render(
        "today-dark-fresh-break",
        sample(ThemeMode.DARK, FatigueState(12, Level.FRESH, emptyList(), calibrated = true), onBreak = true, coach = false),
    )
    @Test fun todayCalibrating() = render("today-dark-calibrating", sample(ThemeMode.DARK, FatigueState.Initial, coach = false))
    @Test fun todaySmallWindow() = render("today-dark-1280x680", sample(ThemeMode.DARK), w = 1280, h = 680)
    @Test fun journalPlaceholder() = render("journal-light", sample(ThemeMode.LIGHT), Tab.JOURNAL)
}
