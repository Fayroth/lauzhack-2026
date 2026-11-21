package ch.lauzhack.energyguard.ui

import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.VisionFrame

/** Everything the dashboard needs to render one frame. */
data class DashboardState(
    val fatigue: FatigueState = FatigueState.Initial,
    val vision: VisionFrame? = null,
    val input: InputStats? = null,
)
