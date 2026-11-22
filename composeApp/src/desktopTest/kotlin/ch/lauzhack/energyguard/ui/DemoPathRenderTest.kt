package ch.lauzhack.energyguard.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.Concentration
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.coach.CoachMessage
import ch.lauzhack.energyguard.coach.Intervention
import ch.lauzhack.energyguard.guardrail.Guardrail
import ch.lauzhack.energyguard.guardrail.LocalGuard
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.ui.today.TodayActions
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * M6 demo hardening: the demo-path screens the shell test does not cover (break card, full-screen stop, inbox block,
 * live mode with no sensor data), at the design size and at a 1280x720 projector (1280x690 content), both themes.
 * PNGs in build/screens/demo-*.png, checked by eye for cut-off text and overlaps.
 */
class DemoPathRenderTest {
    private val tiredCoach = CoachMessage("Your eyes are closing 4x more than this morning. Take 5 minutes away from the screen.", fromLlm = false, kind = Intervention.BREAK)
    private val exhaustedMicroSleep = FatigueState(88, Level.EXHAUSTED, listOf("eyes closed 3.4 s", "eyes closing +520%"), calibrated = true)

    private fun state(theme: ThemeMode, f: FatigueState? = null): AppState {
        val s = ShellRenderTest().pcState()
        val withF = if (f == null) s else s.copy(dashboard = s.dashboard.copy(fatigue = f), concentration = Concentration.from(f))
        return withF.copy(settings = withF.settings.copy(theme = theme))
    }

    private fun png(name: String, w: Int, h: Int, block: ImageComposeScene.() -> Unit) {
        val bytes = ImageComposeScene(w, h, Density(1f)).use { scene ->
            scene.block()
            scene.render(0)
            scene.render(2_000_000_000L)
            scene.render(4_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/screens/$name.png").apply { parentFile.mkdirs(); writeBytes(bytes) }
        assertTrue(out.length() > 10_000, "$name rendered")
    }

    private fun shell(name: String, state: AppState, w: Int, h: Int, coach: CoachMessage? = tiredCoach) = png(name, w, h) {
        setContent {
            DesktopShell(state, Tab.TODAY, {}, TodayActions(), {}) { BreakAlertOverlay(state.dashboard.fatigue, coach, demo = true) }
        }
    }

    @Test fun breakCardDark() = shell("demo-break-card-dark", state(ThemeMode.DARK), 1440, 860)
    @Test fun breakCardLight720() = shell("demo-break-card-light-1280x690", state(ThemeMode.LIGHT), 1280, 690)
    @Test fun microSleepFullLight() = shell("demo-microsleep-light", state(ThemeMode.LIGHT, exhaustedMicroSleep), 1440, 860)
    @Test fun microSleepFull720() = shell("demo-microsleep-dark-1280x690", state(ThemeMode.DARK, exhaustedMicroSleep), 1280, 690)

    /** Live mode (no EG_DEMO), sidecar not running, nothing typed: the signals card must say so cleanly. */
    @Test fun liveNoData() {
        val f = FatigueState(10, Level.FRESH, emptyList(), calibrated = true)
        val s = state(ThemeMode.DARK, f).let { it.copy(demo = false, coach = null, dashboard = DashboardState(fatigue = f)) }
        shell("demo-live-no-data", s, 1440, 860, coach = null)
    }

    /** The inbox, then a click on the PayPal link while TIRED -> red Block screen. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun inbox(name: String, light: Boolean, w: Int, h: Int, clickAt: Offset?) = png(name, w, h) {
        Warm.light = light
        val guardrail = Guardrail()
        val tired = FatigueState(68, Level.TIRED, listOf("eyes closing +347%"), calibrated = true)
        setContent { CompositionLocalProvider(LocalGuard provides { guardrail.check(it, tired) }) { DemoInbox() } }
        if (clickAt != null) {
            render(0)
            sendPointerEvent(PointerEventType.Move, clickAt)
            sendPointerEvent(PointerEventType.Press, clickAt)
            sendPointerEvent(PointerEventType.Release, clickAt)
        }
    }

    @Test fun inboxDark() = inbox("demo-inbox-dark", false, 1280, 770, null)
    @Test fun inboxBlockedDark() = inbox("demo-inbox-blocked-dark", false, 1280, 770, LINK)
    @Test fun inboxBlockedLight720() = inbox("demo-inbox-blocked-light-1280x690", true, 1280, 690, LINK)

    private companion object {
        val LINK = Offset(700f, 375f) // the PayPal link in the reading pane (first email selected)
    }
}
