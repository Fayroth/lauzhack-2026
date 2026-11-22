package ch.lauzhack.energyguard.coach

import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

/** What the coach knows when a level transition happens. Numbers + short engine reason strings only (docs/API.md). */
data class CoachInput(
    val state: FatigueState,       // score, level, reasons
    val previousLevel: Level,      // the transition that triggered the call
    val sleepPriorHours: Double?,  // "sleep last night" (null if unknown)
    val localTime: String,         // "HH:mm", local
    val minutesSinceBreak: Int? = null, // since session start or the last recovery (null if unknown)
    val checkIn: CoachCheckIn? = null,  // today's latest journal check-in (M4), numbers only
)

/** What the coach may know of the journal: three numbers. No text field on purpose, so the private note can't get in. */
data class CoachCheckIn(val kss: Int?, val stress: Int?, val caffeine: Int?)

/** BREAK = 5 min away from the screen, STAND_UP = stand up / walk a bit (#29), RECOVERED = concentration came back. */
enum class Intervention { BREAK, STAND_UP, RECOVERED }

data class CoachMessage(
    val text: String,              // one short intervention
    val fromLlm: Boolean,          // false = offline template fallback
    val kind: Intervention = Intervention.BREAK,
)

interface EnergyCoach {
    suspend fun advise(input: CoachInput): CoachMessage
    val llmCallCount: StateFlow<Int>   // shown by the frugality counter
}

/** One chat completion. Throws on any error; the coach turns errors into the offline template. */
fun interface LlmClient {
    suspend fun complete(system: String, user: String): String
}

/**
 * Frugal generative coach (issue #21): one short intervention, only on level transitions that involve TIRED/EXHAUSTED,
 * at most one LLM call per [minIntervalMs], [timeoutMs] timeout, offline template on no key / error / timeout / rate limit.
 * [llm] = null → templates only. [clock] = epoch ms (one clock with the engine). Not thread-safe: call from one coroutine.
 */
class FrugalEnergyCoach(
    private val llm: LlmClient?,
    private val clock: () -> Long,
    private val minIntervalMs: Long = 10 * 60_000L,
    private val timeoutMs: Long = 5_000L,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default, // network off the UI thread
) : EnergyCoach {
    private val _llmCallCount = MutableStateFlow(0)
    override val llmCallCount: StateFlow<Int> = _llmCallCount.asStateFlow()
    private val _lastMessage = MutableStateFlow<CoachMessage?>(null)
    val lastMessage: StateFlow<CoachMessage?> = _lastMessage.asStateFlow()

    private var level: Level? = null
    private var breakSinceMs = clock()
    private var lastAttemptMs: Long? = null

    /** Forget the level and the break timer (demo F5). The rate limit and the call counter are kept: they are real. */
    fun reset() {
        level = null
        breakSinceMs = clock()
    }

    /**
     * Feed every FatigueState. Returns a message only when the level changed and TIRED/EXHAUSTED is involved
     * (FRESH <-> OK is silent so the LLM budget is kept for what matters), otherwise null.
     * [minutesSinceBreak]: the app's real focus streak (AppStore: reset by every break, user or automatic). When null, the
     * coach falls back to its own estimate (since start or the last recovery to OK/FRESH).
     */
    suspend fun onFatigue(
        state: FatigueState,
        localTime: String,
        sleepPriorHours: Double? = null,
        minutesSinceBreak: Int? = null,
        checkIn: CoachCheckIn? = null,
    ): CoachMessage? {
        val previous = level
        level = state.level
        if (previous == null || previous == state.level) return null
        val now = clock()
        val minutes = minutesSinceBreak?.coerceAtLeast(0) ?: ((now - breakSinceMs) / 60_000).toInt()
        if (state.level < previous && state.level <= Level.OK) breakSinceMs = now // concentration came back = they rested
        if (previous < Level.TIRED && state.level < Level.TIRED) return null
        return advise(CoachInput(state, previous, sleepPriorHours, localTime, minutes, checkIn))
    }

    override suspend fun advise(input: CoachInput): CoachMessage {
        val kind = chooseIntervention(input)
        val template = CoachMessage(templateText(input, kind), fromLlm = false, kind = kind)
        val message = callLlm(input, kind)?.let { CoachMessage(it, fromLlm = true, kind = kind) } ?: template
        _lastMessage.value = message
        return message
    }

    private suspend fun callLlm(input: CoachInput, kind: Intervention): String? {
        val client = llm ?: return null
        val now = clock()
        if (lastAttemptMs?.let { now - it < minIntervalMs } == true) return null // rate limited
        lastAttemptMs = now // a failed attempt also uses the slot: never hammer a dead network
        val raw = try {
            withContext(dispatcher) { withTimeout(timeoutMs) { client.complete(SYSTEM_PROMPT, buildPrompt(input, kind)) } }
        } catch (e: TimeoutCancellationException) {
            println("[coach] LLM timeout, template used"); return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[coach] LLM unavailable (${e::class.simpleName}), template used"); return null // never log the request
        }
        _llmCallCount.value++
        return sanitizeOutput(raw)
    }

    companion object {
        const val SYSTEM_PROMPT =
            "You are EnergyGuard, a warm, concise energy coach for someone working at a computer. " +
                "Write ONE sentence of at most 22 words, in English, addressed to the user. " +
                "Recommend exactly the given intervention, mention one of the numbers, no emojis, no quotes, no medical claims."

        private val SAFE_REASON = Regex("^[a-z0-9 %+.\\-]{1,40}$")
        private val EYE_WORDS = listOf("eye", "blink", "looking away")

        /** Engine reasons only ("eyes closing +40%"): anything else (free text, names, punctuation) is dropped. */
        fun safeReasons(reasons: List<String>): List<String> =
            reasons.map { it.trim().lowercase() }.filter { SAFE_REASON.matches(it) }.take(3)

        fun chooseIntervention(input: CoachInput): Intervention {
            val level = input.state.level
            if (level < input.previousLevel && level <= Level.OK) return Intervention.RECOVERED
            val reasons = safeReasons(input.state.reasons)
            val microSleep = reasons.any { it.startsWith("eyes closed") }
            if (level == Level.EXHAUSTED || microSleep) return Intervention.BREAK
            val sittingLong = (input.minutesSinceBreak ?: 0) >= 45
            val topIsBody = reasons.firstOrNull()?.let { r -> EYE_WORDS.none { it in r } } ?: false
            return if (sittingLong || topIsBody) Intervention.STAND_UP else Intervention.BREAK
        }

        /** The user message: one "key: value" line per number. No images, audio or keystroke content can get in here. */
        fun buildPrompt(input: CoachInput, kind: Intervention): String = buildString {
            val s = input.state
            appendLine("level: ${s.level} (was ${input.previousLevel})")
            appendLine("concentration: ${s.concentration}/100")
            safeReasons(s.reasons).takeIf { it.isNotEmpty() }?.let { appendLine("signals: ${it.joinToString(", ")}") }
            appendLine("local time: ${safeTime(input.localTime)}")
            input.minutesSinceBreak?.let { appendLine("minutes since last break: $it") }
            input.sleepPriorHours?.let { appendLine("sleep last night: ${(it * 10).roundToInt() / 10.0} h") }
            input.checkIn?.let { c -> // re-validated: only in-range integers reach the prompt
                c.kss?.takeIf { it in 1..9 }?.let { appendLine("self-rated sleepiness (KSS 1-9): $it") }
                c.stress?.takeIf { it in 1..5 }?.let { appendLine("self-rated stress (1-5): $it") }
                c.caffeine?.takeIf { it in 0..20 }?.let { appendLine("caffeine today: $it cups") }
            }
            append("intervention: ").append(
                when (kind) {
                    Intervention.BREAK -> "BREAK (5 minutes away from the screen)"
                    Intervention.STAND_UP -> "STAND_UP (stand up and walk for 2 minutes)"
                    Intervention.RECOVERED -> "RECOVERED (concentration is back, encourage tackling the hard task now)"
                }
            )
        }

        fun templateText(input: CoachInput, kind: Intervention): String {
            val s = input.state
            val time = safeTime(input.localTime)
            val why = safeReasons(s.reasons).firstOrNull()?.let { ", $it" } ?: ""
            return when (kind) {
                Intervention.BREAK -> "Concentration ${s.concentration}% at $time$why: take a 5-minute break away from the screen."
                Intervention.STAND_UP -> {
                    val sat = input.minutesSinceBreak?.takeIf { it > 0 }?.let { ", $it min without a break" } ?: ""
                    "Concentration ${s.concentration}% at $time$sat$why: stand up and walk for 2 minutes."
                }
                Intervention.RECOVERED -> "Concentration back to ${s.concentration}%: nice recovery, good moment for the hard task."
            }
        }

        /** LLM output is untrusted: first non-blank line, no markdown/quotes, max 160 chars; null if nothing left. */
        fun sanitizeOutput(raw: String): String? {
            val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
            val clean = line.replace(Regex("[\"*`#_<>]"), "").replace(Regex("\\s+"), " ").trim()
            if (clean.isEmpty()) return null
            return if (clean.length <= 160) clean else clean.take(159).trimEnd() + "…"
        }

        private fun safeTime(t: String): String = t.takeIf { Regex("^\\d{2}:\\d{2}$").matches(it) } ?: "now"
    }
}
