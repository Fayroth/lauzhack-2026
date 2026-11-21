package ch.lauzhack.energyguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.ui.App
import ch.lauzhack.energyguard.ui.DashboardState

/** Android companion (P2 "energy weather"). For now: shared dashboard with a static fake state. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(DashboardState(FatigueState(score = 35, level = Level.OK, reasons = listOf("demo"), calibrated = true)))
        }
    }
}
