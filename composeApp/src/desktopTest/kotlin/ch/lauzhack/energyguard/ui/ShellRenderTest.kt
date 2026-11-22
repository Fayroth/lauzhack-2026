package ch.lauzhack.energyguard.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.CoachNote
import ch.lauzhack.energyguard.app.Concentration
import ch.lauzhack.energyguard.app.DayStats
import ch.lauzhack.energyguard.app.EnergyHistory
import ch.lauzhack.energyguard.app.EnergyState
import ch.lauzhack.energyguard.app.Profile
import ch.lauzhack.energyguard.app.Settings
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.coach.Intervention
import ch.lauzhack.energyguard.engine.BaselineSummary
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgTheme
import ch.lauzhack.energyguard.ui.journal.JournalActions
import ch.lauzhack.energyguard.ui.journal.JournalDraft
import ch.lauzhack.energyguard.ui.journal.JournalScreen
import ch.lauzhack.energyguard.ui.today.TodayActions
import ch.lauzhack.energyguard.app.JournalEntry
import ch.lauzhack.energyguard.app.localDayKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the desktop shell offscreen (1440x860, the 1440x900 window minus the title bar) in both themes and a few
 * states: a smoke test that every screen composes, plus PNGs in build/screens/ to check against the visual rules
 * (copied to docs/screens/ by hand).
 */
private const val HOUR_MS = 3_600_000L

class ShellRenderTest {
    private val tired = FatigueState(68, Level.TIRED, listOf("eyes closing +347%", "long blinks +80%", "slower typing +32%"), calibrated = true)

    private val vision = VisionFrame(ts = 0, face = true, blinkCount = 22, perclos = 0.18, eyesClosed = false, source = "logiinsight-rgb")
    private val input = InputStats(30_000, 4200.0, 6, overshootRatio = 1.42, jitter = 2.0, keyCount = 55, backspaceRatio = 0.12, meanInterKeyMs = 268.0)

    private fun sample(
        theme: ThemeMode,
        fatigue: FatigueState = tired,
        onBreak: Boolean = false,
        coach: Boolean = true,
        battery: Int = 62,
        signals: Boolean = true,
    ) = AppState(
        dashboard = if (signals) DashboardState(fatigue = fatigue, vision = vision, input = input) else DashboardState(fatigue = fatigue),
        concentration = Concentration.from(fatigue),
        energy = EnergyState(battery = battery, onBreak = onBreak, startFromSleep = 90),
        recovery = 71,
        coach = if (coach) CoachNote("Your eyes are closing 4x more than this morning. Take 5 minutes away from the screen.", Intervention.BREAK, fromLlm = true, ts = System.currentTimeMillis()) else null,
        stats = DayStats(day = "2026-11-21", focusedMs = 4_320_000, breaksTaken = 1, breaksNeeded = 1, longestFocusMs = 2_580_000, phishingBlocked = 1, llmCalls = 2),
        settings = Settings(sleepHours = 6.0, theme = theme),
        profile = Profile(BaselineSummary(calibrated = true, mouseJitter = 1.2)),
        demo = true,
        energyHistory = EnergyHistory(0, energyCurve(90, battery)),
    )

    /** A plausible morning: slow decline, a small rebound after a break, then a steeper drop. */
    private fun energyCurve(from: Int, to: Int, n: Int = 60): List<Int> = (0 until n).map { i ->
        val t = i / (n - 1.0)
        val base = from + (to - from) * t * t.coerceAtLeast(0.6)
        val rebound = if (t in 0.45..0.6) 4 * kotlin.math.sin((t - 0.45) / 0.15 * Math.PI) else 0.0
        (base + rebound).toInt().coerceIn(0, 100)
    }.toMutableList().also { it[it.lastIndex] = to }

    /** The TIRED sample, also mirrored to the phone by PhoneRenderTest. */
    internal fun pcState() = sample(ThemeMode.DARK)

    private fun render(name: String, state: AppState, tab: Tab = Tab.TODAY, w: Int = 1440, h: Int = 860, pairing: String? = "Phone: 192.168.1.20:8767 · code 123456") {
        val bytes = ImageComposeScene(w, h, Density(1f)).use { scene ->
            scene.setContent { DesktopShell(state, tab, {}, TodayActions(), {}, pairing = pairing) }
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
    @Test fun todayCalibrating() = render("today-dark-calibrating", sample(ThemeMode.DARK, FatigueState.Initial, coach = false, signals = false))
    @Test fun todayExhaustedLowBattery() = render(
        "today-dark-exhausted",
        sample(ThemeMode.DARK, FatigueState(86, Level.EXHAUSTED, listOf("eyes closing +520%", "shaky mouse +70%"), calibrated = true), battery = 24),
    )
    @Test fun todaySmallWindow() = render("today-dark-1280x680", sample(ThemeMode.DARK), w = 1280, h = 680)

    @Test fun profileDark() = render("profile-dark", sample(ThemeMode.DARK).let { it.copy(profile = Profile(baseline())) }, Tab.PROFILE)
    @Test fun profileLightCompanionOff() = render(
        "profile-light", sample(ThemeMode.LIGHT).let { it.copy(profile = Profile(baseline())) }, Tab.PROFILE, pairing = null,
    )

    private fun baseline() = BaselineSummary(
        calibrated = true, blinkRatePerMin = 14.0, blinkMs = 180.0, perclos = 0.06, mouseJitter = 1.2, interKeyMs = 205.0, backspaceRatio = 0.05,
    )
    /** A few days of check-ins, newest first, with today = the real local day (so "Today" / "Yesterday" labels show). */
    internal fun withJournal(s: AppState, count: Int = 12): AppState {
        val now = System.currentTimeMillis()
        val entries = (0 until count).map { i ->
            val kss = listOf(7, 6, 3, 4, 8, 2, 5, 6, 3, 7, 4, 5)[i % 12]
            JournalEntry(
                id = now - i * 3 * HOUR_MS, ts = now - i * 3 * HOUR_MS,
                kss = kss, stress = if (i % 3 == 0) null else (kss + 1) / 2, caffeine = if (i % 2 == 0) i % 4 else null,
                note = if (i == 0) "Long meeting after lunch, eyes heavy." else null,
                concentration = (100 - kss * 10 + (i % 3) * 4).coerceIn(0, 100),
            )
        }
        return s.copy(journal = entries, stats = s.stats.copy(day = localDayKey(now)))
    }

    @Test fun journalDark() = render("journal-dark", withJournal(sample(ThemeMode.DARK)), Tab.JOURNAL)
    @Test fun journalLight() = render("journal-light", withJournal(sample(ThemeMode.LIGHT), count = 4), Tab.JOURNAL)
    @Test fun journalEmptyDark() = render("journal-dark-empty", sample(ThemeMode.DARK), Tab.JOURNAL)

    /** The form filled in (selected states), at the size the shell gives it. */
    @Test fun journalDraftDark() {
        val bytes = ImageComposeScene(1270, 760, Density(1f)).use { scene ->
            scene.setContent {
                EgTheme(ThemeMode.DARK) {
                    Box(Modifier.fillMaxSize().background(Eg.colors.background)) {
                        JournalScreen(withJournal(sample(ThemeMode.DARK), 3), JournalActions(), wide = true, draft = JournalDraft(7, 2, 3, "Coffee after lunch"))
                    }
                }
            }
            scene.render(0)
            scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        assertTrue(File("build/screens/journal-dark-draft.png").apply { parentFile.mkdirs(); writeBytes(bytes) }.length() > 10_000)
    }
}
