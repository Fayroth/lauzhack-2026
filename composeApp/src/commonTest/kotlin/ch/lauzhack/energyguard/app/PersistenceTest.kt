package ch.lauzhack.energyguard.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** In-memory [TextFiles] so the JSON logic is tested on every platform (real files: desktopTest JsonFileStoreTest). */
class FakeFiles : TextFiles {
    val files = mutableMapOf<String, String>()
    var failWrites = false
    override fun read(name: String) = files[name]
    override fun write(name: String, text: String) {
        if (failWrites) error("disk full")
        files[name] = text
    }
    override fun backup(name: String) { files[name]?.let { files["$name.bad"] = it } }
}

class PersistenceTest {
    private val clock = TestClock()
    private val logs = mutableListOf<String>()

    @Test fun roundTripThroughTheStore() {
        val files = FakeFiles()
        val store = clock.store(JsonFilePersistence(files) { logs += it })
        store.setSleepHours(6.0)
        store.updateSettings { it.copy(theme = ThemeMode.LIGHT, coachOnline = false) }
        store.addJournalEntry(kss = 3, stress = 2, caffeine = 1, note = "private")
        store.onGuardBlock()
        store.run(clock, 20 * MIN) { store.onVision(ch.lauzhack.energyguard.model.VisionFrame(ts = it, face = true)) }
        store.flush()

        val again = clock.store(JsonFilePersistence(files) { logs += it }).state.value
        assertEquals(6.0, again.settings.sleepHours)
        assertEquals(ThemeMode.LIGHT, again.settings.theme)
        assertEquals(false, again.settings.coachOnline)
        assertEquals(listOf("private"), again.journal.map { it.note })
        assertEquals(1, again.stats.phishingBlocked, "same-day restart resumes today's counters")
        assertEquals(20 * MIN, again.stats.focusedMs)
        assertEquals(store.state.value.energy.battery, again.energy.battery, "and the battery")
        assertTrue(logs.isEmpty(), logs.toString())
    }

    @Test fun missingFilesGiveDefaults() {
        val s = clock.store(JsonFilePersistence(FakeFiles()) { logs += it }).state.value
        assertEquals(Settings(), s.settings)
        assertTrue(s.journal.isEmpty())
        assertTrue(s.history.isEmpty())
        assertEquals(100, s.energy.battery)
        assertTrue(logs.isEmpty())
    }

    @Test fun corruptFilesGiveDefaultsAndABackup() {
        val files = FakeFiles()
        files.files[JsonFilePersistence.SETTINGS] = "{not json"
        files.files[JsonFilePersistence.JOURNAL] = """{"version":1,"entries":[{"id":"x"}]}"""
        files.files[JsonFilePersistence.HISTORY] = "\u0000\u0001garbage"
        val s = clock.store(JsonFilePersistence(files) { logs += it }).state.value
        assertEquals(Settings(), s.settings)
        assertTrue(s.journal.isEmpty())
        assertTrue(s.history.isEmpty())
        assertEquals("{not json", files.files["settings.json.bad"])
        assertEquals(3, logs.size, logs.toString())
    }

    @Test fun badValuesInAValidFileAreSanitized() {
        val files = FakeFiles()
        files.files[JsonFilePersistence.JOURNAL] = """{"version":1,"entries":[
            {"id":1,"ts":10,"kss":42,"stress":3,"concentration":250,"extra":"ignored"},
            {"id":2,"ts":20,"kss":0,"concentration":50}]}"""
        files.files[JsonFilePersistence.SETTINGS] = """{"version":2,"settings":{"sleepHours":-3,"theme":"PURPLE","sleepDay":"../etc"}}"""
        files.files[JsonFilePersistence.HISTORY] = """{"days":[{"day":"2026-11-20","battery":900},{"day":"<script>"}]}"""
        val s = clock.store(JsonFilePersistence(files) { logs += it }).state.value
        val e = s.journal.single()
        assertEquals(null, e.kss)
        assertEquals(3, e.stress)
        assertEquals(100, e.concentration)
        assertEquals(Settings.DEFAULT_SLEEP_HOURS, s.settings.sleepHours)
        assertEquals(ThemeMode.DARK, s.settings.theme, "unknown enum -> default")
        assertEquals(listOf("2026-11-20"), s.history.map { it.day })
        assertEquals(100, s.history.single().battery)
    }

    @Test fun writeErrorsNeverCrash() {
        val files = FakeFiles().apply { failWrites = true }
        val store = clock.store(JsonFilePersistence(files) { logs += it })
        store.addJournalEntry(kss = 5)
        store.flush()
        assertEquals(1, store.state.value.journal.size, "kept in memory")
        assertEquals(2, logs.size)
    }
}
