package ch.lauzhack.energyguard.coach

import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MIN = 60_000L

/** Fake LLM: records every prompt, answers [reply] (or throws / hangs). */
private class FakeLlm(var reply: String = "Concentration 38%: stand up and stretch for two minutes.") : LlmClient {
    val prompts = mutableListOf<String>()
    var fail = false
    var hangMs = 0L
    override suspend fun complete(system: String, user: String): String {
        prompts += system + "\n" + user
        if (hangMs > 0) delay(hangMs)
        if (fail) error("no network")
        return reply
    }
}

private fun state(level: Level, score: Int, vararg reasons: String) =
    FatigueState(score = score, level = level, reasons = reasons.toList(), calibrated = true)

class EnergyCoachTest {
    private var now = 1_000_000L

    private fun TestScope.coach(llm: LlmClient?) =
        FrugalEnergyCoach(llm, clock = { now }, dispatcher = StandardTestDispatcher(testScheduler))

    @Test
    fun noCallWithoutLevelChange() = runTest {
        val llm = FakeLlm()
        val c = coach(llm)
        assertNull(c.onFatigue(state(Level.FRESH, 5), "16:20"))         // first state: just remembered
        repeat(20) { assertNull(c.onFatigue(state(Level.FRESH, 10 + it), "16:20")) }
        assertNull(c.onFatigue(state(Level.OK, 35), "16:21"))           // FRESH -> OK: silent, budget kept
        assertNull(c.onFatigue(state(Level.OK, 40), "16:21"))
        assertEquals(0, llm.prompts.size)
        assertEquals(0, c.llmCallCount.value)
    }

    @Test
    fun levelChangeCallsLlmOnceAndCounts() = runTest {
        val llm = FakeLlm()
        val c = coach(llm)
        c.onFatigue(state(Level.OK, 40), "16:20")
        val msg = assertNotNull(c.onFatigue(state(Level.TIRED, 62, "shaky mouse +40%"), "16:20"))
        assertTrue(msg.fromLlm)
        assertEquals(llm.reply, msg.text)
        assertEquals(1, c.llmCallCount.value)
        assertEquals(msg, c.lastMessage.value)
    }

    @Test
    fun rateLimitOneCallPerTenMinutes() = runTest {
        val llm = FakeLlm()
        val c = coach(llm)
        c.onFatigue(state(Level.OK, 40), "16:20")
        assertTrue(c.onFatigue(state(Level.TIRED, 60), "16:20")!!.fromLlm)
        now += 2 * MIN
        val second = c.onFatigue(state(Level.EXHAUSTED, 85), "16:22")!!
        assertFalse(second.fromLlm)                                     // inside the window: template
        assertTrue(second.text.isNotBlank())
        assertEquals(1, llm.prompts.size)
        now += 9 * MIN
        assertTrue(c.onFatigue(state(Level.TIRED, 65), "16:31")!!.fromLlm) // window over
        assertEquals(2, c.llmCallCount.value)
    }

    @Test
    fun fallbackWithoutKeyErrorOrTimeout() = runTest {
        val none = coach(null)
        none.onFatigue(state(Level.OK, 40), "16:20")
        val m1 = none.onFatigue(state(Level.TIRED, 60, "eyes closing +40%"), "16:20")!!
        assertFalse(m1.fromLlm)
        assertTrue("16:20" in m1.text && "40%" in m1.text, m1.text)

        val failing = FakeLlm().apply { fail = true }
        val c2 = coach(failing)
        c2.onFatigue(state(Level.OK, 40), "16:20")
        assertFalse(c2.onFatigue(state(Level.TIRED, 60), "16:20")!!.fromLlm)
        assertEquals(0, c2.llmCallCount.value)                          // no message came back: not counted

        val slow = FakeLlm().apply { hangMs = 30_000 }
        val c3 = coach(slow)
        c3.onFatigue(state(Level.OK, 40), "16:20")
        assertFalse(c3.onFatigue(state(Level.TIRED, 60), "16:20")!!.fromLlm) // 5 s timeout (virtual time)
        assertEquals(0, c3.llmCallCount.value)
    }

    @Test
    fun promptContainsNumbersAndEngineReasonsOnly() = runTest {
        val llm = FakeLlm()
        val c = coach(llm)
        c.onFatigue(state(Level.OK, 40), "16:20", sleepPriorHours = 5.5)
        c.onFatigue(
            state(Level.TIRED, 62, "eyes closing +40%", "Dear Bob, my password is hunter2!", "shaky mouse +25%"),
            "16:20", sleepPriorHours = 5.5,
        )
        val prompt = llm.prompts.single()
        assertTrue("concentration: 38/100" in prompt, prompt)
        assertTrue("level: TIRED (was OK)" in prompt)
        assertTrue("eyes closing +40%" in prompt && "shaky mouse +25%" in prompt)
        assertTrue("sleep last night: 5.5 h" in prompt)
        assertFalse("password" in prompt || "hunter2" in prompt || "Bob" in prompt) // free text never reaches the LLM
        val userPart = prompt.substringAfter(FrugalEnergyCoach.SYSTEM_PROMPT + "\n")
        userPart.lines().forEach { assertTrue(Regex("^[a-z ]+: [A-Za-z0-9 %+.,:/()_\\-]+$").matches(it), it) }
    }

    @Test
    fun choosesBreakStandUpOrRecovered() = runTest {
        val c = coach(null)
        c.onFatigue(state(Level.OK, 40), "16:20")
        assertEquals(Intervention.STAND_UP, c.onFatigue(state(Level.TIRED, 60, "shaky mouse +40%"), "16:20")!!.kind)
        assertEquals(Intervention.BREAK, c.onFatigue(state(Level.EXHAUSTED, 85, "shaky mouse +60%"), "16:21")!!.kind)
        assertEquals(Intervention.RECOVERED, c.onFatigue(state(Level.OK, 40), "16:30")!!.kind)
        assertEquals(Intervention.BREAK, c.onFatigue(state(Level.TIRED, 60, "eyes closing +40%"), "16:31")!!.kind)
        c.onFatigue(state(Level.OK, 40), "16:32")
        now += 50 * MIN                                                 // 50 min without a break: get up
        assertEquals(Intervention.STAND_UP, c.onFatigue(state(Level.TIRED, 60, "eyes closing +40%"), "17:22")!!.kind)
    }

    @Test
    fun llmOutputIsSanitized() {
        assertEquals("Stand up now.", FrugalEnergyCoach.sanitizeOutput("\n  **\"Stand up now.\"**\nSecond line"))
        assertNull(FrugalEnergyCoach.sanitizeOutput("  \n "))
        assertEquals(160, FrugalEnergyCoach.sanitizeOutput("a".repeat(500))!!.length)
    }

    @Test
    fun appFocusStreakAndSleepReachTheCoach() = runTest {
        val llm = FakeLlm()
        val c = coach(null)
        c.onFatigue(state(Level.OK, 40), "16:20")
        // TIRED with an eye reason would be a BREAK, but the app says 50 min without a break: stand up
        val msg = assertNotNull(c.onFatigue(state(Level.TIRED, 60, "eyes closing +40%"), "16:20", sleepPriorHours = 5.0, minutesSinceBreak = 50))
        assertEquals(Intervention.STAND_UP, msg.kind)
        assertTrue("50 min without a break" in msg.text, msg.text)

        val c2 = coach(llm)
        c2.onFatigue(state(Level.OK, 40), "16:20")
        c2.onFatigue(state(Level.TIRED, 60, "eyes closing +40%"), "16:20", sleepPriorHours = 5.0, minutesSinceBreak = 3)
        assertTrue("sleep last night: 5.0 h" in llm.prompts.single(), llm.prompts.single())
        assertTrue("minutes since last break: 3" in llm.prompts.single())
    }
}
