package ch.lauzhack.energyguard.ui

import ch.lauzhack.energyguard.ui.components.EgIcon

/** The 3 tabs of both shells: desktop rail (keys 1 / 2 / 3, main.kt) and the phone's bottom bar. */
enum class Tab(val label: String, val icon: EgIcon) {
    TODAY("Today", EgIcon.TODAY),
    JOURNAL("Journal", EgIcon.JOURNAL),
    PROFILE("Profile", EgIcon.PROFILE),
}
