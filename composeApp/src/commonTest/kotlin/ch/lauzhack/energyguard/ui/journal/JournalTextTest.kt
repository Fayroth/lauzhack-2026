package ch.lauzhack.energyguard.ui.journal

import ch.lauzhack.energyguard.app.JournalEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalTextTest {
    private fun entry(kss: Int?, concentration: Int, id: Long = 1) = JournalEntry(id, id, kss = kss, concentration = concentration)

    @Test fun pearsonNeedsThreeVariedPoints() {
        assertNull(pearson(listOf(3.0, 7.0), listOf(20.0, 70.0)))
        assertNull(pearson(listOf(5.0, 5.0, 5.0), listOf(20.0, 40.0, 60.0)))
        assertTrue(pearson(listOf(2.0, 5.0, 8.0), listOf(15.0, 45.0, 80.0))!! > 0.99)
        assertTrue(pearson(listOf(2.0, 5.0, 8.0), listOf(80.0, 45.0, 15.0))!! < -0.99)
    }

    @Test fun comparisonOnlyFromTenRatedCheckIns() {
        val nine = (1..9).map { entry(kss = it, concentration = 100 - it * 10, id = it.toLong()) }
        val noKss = (20..30).map { entry(kss = null, concentration = 50, id = it.toLong()) }
        assertEquals("Compare after 10 check-ins (9/10)", comparisonText(nine + noKss), "entries without a KSS don't count")
        assertEquals("Compare after 10 check-ins (0/10)", comparisonText(emptyList()))
        // Sleepier (higher KSS) = lower concentration = higher fatigue score: we agree, r = +1.
        val ten = nine + entry(kss = 9, concentration = 5, id = 10)
        assertTrue(comparisonText(ten).startsWith("r = "), comparisonText(ten))
        assertTrue(comparisonText(ten).endsWith("strong match"))
        val flat = (1..10).map { entry(kss = 5, concentration = 50, id = it.toLong()) }
        assertEquals("Not enough variety yet to compare (10 check-ins)", comparisonText(flat))
    }

    @Test fun historyLines() {
        assertEquals("You said 7/9 · we measured 68", measuredLine(entry(7, 68)))
        assertEquals("We measured 68", measuredLine(entry(null, 68)))
        val e = JournalEntry(1, 1, kss = 7, stress = 2, caffeine = 0, note = "secret", concentration = 68)
        assertEquals(listOf("Sleepy 7/9", "Stress 2/5", "No coffee"), entryChips(e))
        assertEquals("1 cup", cupsText(1))
        assertEquals("3 cups", cupsText(3))
    }

    @Test fun whenLabels() {
        assertEquals("Today 14:32", whenText("2026-11-22", "14:32", "2026-11-22", "2026-11-21"))
        assertEquals("Yesterday 09:10", whenText("2026-11-21", "09:10", "2026-11-22", "2026-11-21"))
        assertEquals("20 Nov 08:00", whenText("2026-11-20", "08:00", "2026-11-22", ""))
    }

    @Test fun saveNeedsSomething() {
        assertFalse(canSave(null, null, null, "   "))
        assertTrue(canSave(null, null, 0, ""), "0 cups is an answer")
        assertTrue(canSave(5, null, null, ""))
        assertTrue(canSave(null, null, null, "tired"))
    }

    @Test fun everyScaleValueHasWords() {
        assertEquals((1..9).toSet(), KSS_LABELS.keys)
        assertEquals("very alert", KSS_LABELS[1])
        assertEquals("fighting sleep", KSS_LABELS[9])
        assertEquals((1..5).toSet(), STRESS_LABELS.keys)
    }
}
