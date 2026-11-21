package ch.lauzhack.energyguard.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Local-only storage for journal, settings and daily history (docs/API.md "Persistence"). Nothing is ever sent.
 * Implementations never throw: missing / corrupt data -> defaults.
 */
interface AppPersistence {
    fun loadSettings(): Settings
    fun saveSettings(settings: Settings)
    fun loadJournal(): List<JournalEntry>
    fun saveJournal(entries: List<JournalEntry>)
    fun loadHistory(): List<DaySummary>
    fun saveHistory(days: List<DaySummary>)
}

/** Desktop demo mode and tests: nothing touches the disk. */
class InMemoryPersistence(
    var settings: Settings = Settings(),
    var journal: List<JournalEntry> = emptyList(),
    var history: List<DaySummary> = emptyList(),
) : AppPersistence {
    override fun loadSettings() = settings
    override fun saveSettings(settings: Settings) { this.settings = settings }
    override fun loadJournal() = journal
    override fun saveJournal(entries: List<JournalEntry>) { journal = entries }
    override fun loadHistory() = history
    override fun saveHistory(days: List<DaySummary>) { history = days }
}

/** A directory of small text files. Platform part (expect/actual): plain java.io on desktop and Android. */
interface TextFiles {
    /** null when the file does not exist. May throw on I/O errors (the caller catches). */
    fun read(name: String): String?
    /** Atomic replace (temp file + rename). May throw (the caller catches). */
    fun write(name: String, text: String)
    /** Keep a copy of a corrupt file as `<name>.bad` before it gets overwritten. May throw. */
    fun backup(name: String)
}

/** Files in [dir], created on first write. Desktop: `~/.energyguard/`, Android: `filesDir`. */
expect fun textFilesIn(dir: String): TextFiles

/** "YYYY-MM-DD" in the device's local time zone. */
expect fun localDayKey(epochMs: Long): String

@Serializable private data class SettingsFile(val version: Int = 1, val settings: Settings = Settings())
@Serializable private data class JournalFile(val version: Int = 1, val entries: List<JournalEntry> = emptyList())
@Serializable private data class HistoryFile(val version: Int = 1, val days: List<DaySummary> = emptyList())

/** JSON files via kotlinx.serialization. Corrupt or unreadable -> defaults (+ `.bad` copy), never a crash. */
class JsonFilePersistence(private val files: TextFiles, private val log: (String) -> Unit = ::println) : AppPersistence {
    override fun loadSettings(): Settings =
        load(SETTINGS, SettingsFile()) { JSON.decodeFromString(SettingsFile.serializer(), it) }.settings.sanitized()

    override fun saveSettings(settings: Settings) =
        save(SETTINGS) { JSON.encodeToString(SettingsFile.serializer(), SettingsFile(settings = settings)) }

    override fun loadJournal(): List<JournalEntry> =
        load(JOURNAL, JournalFile()) { JSON.decodeFromString(JournalFile.serializer(), it) }.entries
            .map { it.sanitized() }.filterNot { it.isEmpty }
            .distinctBy { it.id }.sortedByDescending { it.ts }.take(JournalEntry.MAX_ENTRIES)

    override fun saveJournal(entries: List<JournalEntry>) =
        save(JOURNAL) { JSON.encodeToString(JournalFile.serializer(), JournalFile(entries = entries)) }

    override fun loadHistory(): List<DaySummary> =
        load(HISTORY, HistoryFile()) { JSON.decodeFromString(HistoryFile.serializer(), it) }.days
            .filter { Settings.DAY.matches(it.day) }.map { it.sanitized() }
            .distinctBy { it.day }.sortedByDescending { it.day }.take(MAX_DAYS)

    override fun saveHistory(days: List<DaySummary>) =
        save(HISTORY) { JSON.encodeToString(HistoryFile.serializer(), HistoryFile(days = days.take(MAX_DAYS))) }

    private fun <T> load(name: String, default: T, decode: (String) -> T): T {
        val text = try {
            files.read(name) ?: return default
        } catch (e: Exception) {
            log("[store] could not read $name (${e::class.simpleName}), using defaults")
            return default
        }
        return try {
            decode(text)
        } catch (e: Exception) { // SerializationException / IllegalArgumentException: corrupt or edited by hand
            log("[store] $name is corrupt (${e::class.simpleName}), using defaults; copy kept as $name.bad")
            runCatching { files.backup(name) }
            default
        }
    }

    private fun save(name: String, encode: () -> String) {
        try {
            files.write(name, encode())
        } catch (e: Exception) {
            log("[store] could not save $name (${e::class.simpleName})")
        }
    }

    companion object {
        const val SETTINGS = "settings.json"
        const val JOURNAL = "journal.json"
        const val HISTORY = "history.json"
        const val MAX_DAYS = 8 // 7 past days + today in progress
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    }
}
