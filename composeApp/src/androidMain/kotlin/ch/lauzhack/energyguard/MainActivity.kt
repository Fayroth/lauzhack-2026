package ch.lauzhack.energyguard

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ch.lauzhack.energyguard.app.AppStore
import ch.lauzhack.energyguard.app.JsonFilePersistence
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.app.localDayKey
import ch.lauzhack.energyguard.app.textFilesIn
import ch.lauzhack.energyguard.companion.PairingPrefs
import ch.lauzhack.energyguard.haptics.VibratorHapticOutput
import ch.lauzhack.energyguard.ui.AndroidShell

/**
 * Android app (JetBrains "2 platforms"): the shared phone shell (ui/AndroidShell.kt) on the shared logic.
 * Live mirror of a paired PC (EG_COMPANION=1, read-only) or the phone's own demo replay.
 * App model (M1): an AppStore (journal + settings in filesDir, no history: the phone only runs the demo engine).
 * Android-specific: this activity (edge-to-edge, portrait, screen kept on) and phone vibration.
 */
class MainActivity : ComponentActivity() {
    private lateinit var store: AppStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(ThemeMode.DARK)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // demo on a table: never dims
        val haptics = VibratorHapticOutput(this)
        val prefs = PairingPrefs(this)
        store = AppStore(
            System::currentTimeMillis, ::localDayKey, JsonFilePersistence(textFilesIn(filesDir.absolutePath)),
            demo = true, persistHistory = false,
        )
        setContent { AndroidShell(haptics, prefs, store, onTheme = ::applySystemBars) }
    }

    /** Edge-to-edge, transparent bars; icons light on the dark theme, dark on the light one. */
    private fun applySystemBars(theme: ThemeMode) {
        val style = if (theme == ThemeMode.LIGHT) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        else SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onStop() {
        super.onStop()
        store.flush()
    }
}
