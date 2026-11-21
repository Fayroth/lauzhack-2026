package ch.lauzhack.energyguard.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The real desktop files (atomic replace, `.bad` copy) in a temp dir, never in ~/.energyguard. */
class JsonFileStoreTest {
    private val dir: File = Files.createTempDirectory("eg-store").toFile()

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    @Test fun roundTripOnDisk() {
        val p = JsonFilePersistence(textFilesIn(File(dir, "sub").path)) // created on first write
        p.saveSettings(Settings(sleepHours = 6.5, sleepDay = "2026-11-21", hapticsOn = false))
        p.saveJournal(listOf(JournalEntry(id = 1, ts = 1, kss = 2, note = "n", concentration = 70)))
        p.saveHistory(listOf(DaySummary(day = "2026-11-20", focusedMs = 1234)))
        p.saveSettings(Settings(sleepHours = 8.0, sleepDay = "2026-11-21")) // replace an existing file

        val q = JsonFilePersistence(textFilesIn(File(dir, "sub").path))
        assertEquals(8.0, q.loadSettings().sleepHours)
        assertEquals("n", q.loadJournal().single().note)
        assertEquals(1234, q.loadHistory().single().focusedMs)
        assertFalse(File(dir, "sub/settings.json.tmp").exists(), "temp file renamed away")
    }

    @Test fun journalSurvivesARestart() {
        val now = System.currentTimeMillis()
        val first = AppStore({ now }, ::localDayKey, JsonFilePersistence(textFilesIn(dir.path)), syncSleepPrior = false)
        val recoveryBefore = first.state.value.recovery
        first.addJournalEntry(kss = 8, stress = 4, caffeine = 2, note = "rough night")
        assertTrue(first.state.value.recovery < recoveryBefore, "a sleepy check-in lowers recovery")

        val second = AppStore({ now + 60_000 }, ::localDayKey, JsonFilePersistence(textFilesIn(dir.path)), syncSleepPrior = false)
        val e = second.state.value.journal.single()
        assertEquals(8, e.kss)
        assertEquals("rough night", e.note)
        assertEquals(first.state.value.recovery, second.state.value.recovery, "same day: the check-in still counts")
    }

    @Test fun corruptFileOnDisk() {
        File(dir, "journal.json").writeText("{\"version\":1,\"entries\":[")
        val logs = mutableListOf<String>()
        val p = JsonFilePersistence(textFilesIn(dir.path)) { logs += it }
        assertTrue(p.loadJournal().isEmpty())
        assertTrue(File(dir, "journal.json.bad").isFile)
        assertEquals(1, logs.size)
    }

    @Test fun dayKeyIsIsoLocalDate() {
        assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(localDayKey(System.currentTimeMillis())))
    }
}
