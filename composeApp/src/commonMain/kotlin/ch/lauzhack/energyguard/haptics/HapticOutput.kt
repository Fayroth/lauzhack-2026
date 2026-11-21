package ch.lauzhack.energyguard.haptics

enum class HapticPattern { GENTLE, WARNING }

/** MX Master 4 haptic feedback (owner: Zyad). SDK unknown at start, hence the interface. */
interface HapticOutput {
    fun pulse(pattern: HapticPattern)
}

/** Prints instead of vibrating. Replace with the real MX Master 4 bridge. */
class MockHapticOutput : HapticOutput {
    override fun pulse(pattern: HapticPattern) {
        println("[haptic] $pattern")
    }
}
