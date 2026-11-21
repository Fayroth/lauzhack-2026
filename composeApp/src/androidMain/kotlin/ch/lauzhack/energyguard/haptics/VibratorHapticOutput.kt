package ch.lauzhack.energyguard.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Android side of [HapticOutput]: the phone vibrates where the desktop MX Master 4 would (GENTLE = 1 buzz, WARNING = 3). */
class VibratorHapticOutput(context: Context) : HapticOutput {
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    override fun pulse(pattern: HapticPattern) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val timings = when (pattern) {
            HapticPattern.GENTLE -> longArrayOf(0, 250)
            HapticPattern.WARNING -> longArrayOf(0, 300, 150, 300, 150, 300)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, -1)
        }
    }
}
