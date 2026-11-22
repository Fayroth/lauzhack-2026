package ch.lauzhack.energyguard.app

import java.io.File
import java.util.Calendar
import java.util.Locale

actual fun textFilesIn(dir: String): TextFiles = AndroidTextFiles(File(dir))

/** java.time needs API 26 (minSdk is 24): Calendar instead. */
actual fun localDayKey(epochMs: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = epochMs }
    return String.format(Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

actual fun localClockTime(epochMs: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = epochMs }
    return String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

/** App-private `filesDir` (not readable by other apps, allowBackup=false). rename() replaces atomically on Linux. */
private class AndroidTextFiles(private val dir: File) : TextFiles {
    override fun read(name: String): String? = File(dir, name).takeIf { it.isFile }?.readText()

    override fun write(name: String, text: String) {
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(File(dir, name))) error("rename failed")
    }

    override fun backup(name: String) {
        val f = File(dir, name)
        if (f.isFile) f.copyTo(File(dir, "$name.bad"), overwrite = true)
    }
}
