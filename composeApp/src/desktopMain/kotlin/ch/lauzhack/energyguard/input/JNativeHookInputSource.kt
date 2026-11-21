package ch.lauzhack.energyguard.input

import ch.lauzhack.energyguard.model.InputStats
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Real [InputSource]: global mouse/keyboard hooks (JNativeHook), aggregated by [InputStatsCalculator].
 * Hook callbacks only enqueue (timestamp, position or "correction key" flag); key contents are never stored.
 * Enable with env var EG_INPUT=real. If the hook cannot start, logs to stderr and emits nothing.
 */
class JNativeHookInputSource(
    private val windowMs: Long = 30_000,
    private val emitEveryMs: Long = 5_000,
) : InputSource {

    private val incoming = ConcurrentLinkedQueue<InputEvent>()
    private val hookLogger: Logger = Logger.getLogger(GlobalScreen::class.java.`package`.name)
    @Volatile private var correctionHeld = false

    private val mouseListener = object : NativeMouseInputListener {
        override fun nativeMouseMoved(e: NativeMouseEvent) { incoming.add(MouseMove(nowMs(), e.x, e.y)) }
        override fun nativeMouseDragged(e: NativeMouseEvent) { incoming.add(MouseMove(nowMs(), e.x, e.y)) }
        override fun nativeMousePressed(e: NativeMouseEvent) { incoming.add(MouseClick(nowMs(), e.x, e.y)) }
        override fun nativeMouseClicked(e: NativeMouseEvent) {}
        override fun nativeMouseReleased(e: NativeMouseEvent) {}
    }

    private val keyListener = object : NativeKeyListener {
        override fun nativeKeyPressed(e: NativeKeyEvent) {
            val correction = isCorrection(e)
            if (correction && correctionHeld) return
            if (correction) correctionHeld = true
            incoming.add(KeyPress(nowMs(), correction))
        }
        override fun nativeKeyReleased(e: NativeKeyEvent) {
            if (isCorrection(e)) correctionHeld = false
        }
        override fun nativeKeyTyped(e: NativeKeyEvent) {}
    }

    override fun stats(): Flow<InputStats> = flow {
        if (!start()) return@flow
        val calculator = InputStatsCalculator(windowMs)
        try {
            while (true) {
                delay(emitEveryMs)
                var event = incoming.poll()
                while (event != null) {
                    calculator.add(event)
                    event = incoming.poll()
                }
                emit(calculator.snapshot(nowMs()))
            }
        } finally {
            stop()
        }
    }.flowOn(Dispatchers.Default)

    private fun start(): Boolean {
        hookLogger.level = Level.WARNING
        hookLogger.useParentHandlers = false
        return try {
            if (!GlobalScreen.isNativeHookRegistered()) GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeMouseListener(mouseListener)
            GlobalScreen.addNativeMouseMotionListener(mouseListener)
            GlobalScreen.addNativeKeyListener(keyListener)
            true
        } catch (e: NativeHookException) {
            System.err.println("[input] JNativeHook could not start: ${e.message}")
            false
        } catch (e: LinkageError) {
            System.err.println("[input] JNativeHook native library could not load: ${e.message}")
            false
        }
    }

    private fun isCorrection(e: NativeKeyEvent): Boolean =
        e.keyCode == NativeKeyEvent.VC_BACKSPACE || e.keyCode == NativeKeyEvent.VC_DELETE

    private fun stop() {
        GlobalScreen.removeNativeMouseListener(mouseListener)
        GlobalScreen.removeNativeMouseMotionListener(mouseListener)
        GlobalScreen.removeNativeKeyListener(keyListener)
        runCatching { GlobalScreen.unregisterNativeHook() }
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L
}
