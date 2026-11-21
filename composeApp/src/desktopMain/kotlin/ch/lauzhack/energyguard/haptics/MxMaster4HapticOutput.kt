package ch.lauzhack.energyguard.haptics

import org.hid4java.HidDevice
import org.hid4java.HidManager
import org.hid4java.HidServicesSpecification
import java.awt.Toolkit
import java.util.concurrent.Executors

/**
 * Real MX Master 4 haptics over HID (hid4java), enabled with env var EG_HAPTICS=mx.
 *
 * Byte layout copied from the MX Master 4 WebHID library (logitech-haptic-driver.js,
 * https://github.com/mario-gutierrez/mx-master-4-webhid, MIT): report 0x11 + 19-byte payload
 * [deviceIndex=0x02, 0x0B, 0x4E, effectId, 0...]. Like the JS driver, no HID++ feature-index lookup:
 * 0x0B4E is hardcoded.
 *
 * On Windows the mouse exposes several HID collections; only the vendor one (usage page >= 0xFF00) accepts
 * this report, the standard mouse collection is locked by the OS. We try the vendor collections in order.
 *
 * Sends on a background thread and never throws: if the mouse is absent or a write fails,
 * it falls back to the mock (console) + a system beep.
 */
class MxMaster4HapticOutput(
    private val gentleEffect: Int = envInt("EG_HAPTIC_GENTLE", DEFAULT_GENTLE_EFFECT),
    private val warningEffect: Int = envInt("EG_HAPTIC_WARNING", DEFAULT_WARNING_EFFECT),
) : HapticOutput {

    private val fallback = MockHapticOutput()
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mx-haptics").apply { isDaemon = true }
    }
    private var device: HidDevice? = null // only touched on the worker thread

    override fun pulse(pattern: HapticPattern) {
        val effect = when (pattern) {
            HapticPattern.GENTLE -> gentleEffect
            HapticPattern.WARNING -> warningEffect
        }
        worker.execute {
            val ok = try {
                send(effect)
            } catch (t: Throwable) { // includes UnsatisfiedLinkError if the native hidapi cannot load
                System.err.println("[haptic] MX Master 4 error: ${t.message}")
                closeDevice()
                false
            }
            if (ok) {
                println("[haptic] MX Master 4: $pattern (effect $effect)")
            } else {
                fallback.pulse(pattern)
                Toolkit.getDefaultToolkit().beep()
            }
        }
    }

    /** Sends one haptic report; reconnects once if the cached device stopped accepting writes. */
    private fun send(effect: Int): Boolean {
        device?.let { if (write(it, effect)) return true }
        closeDevice()
        for (candidate in vendorCollections()) {
            if (!candidate.isClosed || candidate.open()) {
                if (write(candidate, effect)) {
                    device = candidate
                    return true
                }
                candidate.close()
            }
        }
        System.err.println("[haptic] MX Master 4 not found (046D:B042/B048, vendor HID collection)")
        return false
    }

    private fun write(d: HidDevice, effect: Int): Boolean {
        // Same as the JS driver: 19-byte payload, report ID 0x11 prepended by hid4java.
        val payload = ByteArray(REPORT_LENGTH)
        payload[0] = DEVICE_INDEX.toByte()                       // Byte 0: device index
        payload[1] = (HAPTIC_FEATURE_ID shr 8).toByte()          // Byte 1-2: feature ID (big endian)
        payload[2] = (HAPTIC_FEATURE_ID and 0xFF).toByte()
        payload[3] = effect.toByte()                             // Byte 3: effect ID
        return d.write(payload, REPORT_LENGTH, REPORT_ID.toByte()) > 0
    }

    private fun vendorCollections(): List<HidDevice> =
        services.attachedHidDevices.filter {
            it.vendorId and 0xFFFF == VENDOR_ID &&
                it.productId and 0xFFFF in PRODUCT_IDS &&
                it.usagePage and 0xFFFF >= VENDOR_USAGE_PAGE_MIN
        }

    private fun closeDevice() {
        runCatching { device?.close() }
        device = null
    }

    private companion object {
        const val VENDOR_ID = 0x046D
        val PRODUCT_IDS = setOf(0xB042, 0xB048) // MX Master 4, MX Master 4 for Business
        const val VENDOR_USAGE_PAGE_MIN = 0xFF00
        const val HAPTIC_FEATURE_ID = 0x0B4E
        const val REPORT_ID = 0x11
        const val REPORT_LENGTH = 19
        const val DEVICE_INDEX = 2

        // TODO(Zyad): fill in with the effect numbers tested on the WebHID demo page.
        const val DEFAULT_GENTLE_EFFECT = 0
        const val DEFAULT_WARNING_EFFECT = 14

        val services by lazy {
            val spec = HidServicesSpecification().apply { isAutoStart = false }
            HidManager.getHidServices(spec)
        }

        fun envInt(name: String, default: Int): Int =
            System.getenv(name)?.toIntOrNull()?.takeIf { it in 0..255 } ?: default
    }
}
