package ch.lauzhack.energyguard.app

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId

actual fun textFilesIn(dir: String): TextFiles = DesktopTextFiles(File(dir))

actual fun localDayKey(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** Desktop store: `~/.energyguard/` (journal, settings, history). Local only. */
fun desktopPersistence(): AppPersistence =
    JsonFilePersistence(textFilesIn(File(System.getProperty("user.home"), ".energyguard").path))

private class DesktopTextFiles(private val dir: File) : TextFiles {
    override fun read(name: String): String? = File(dir, name).takeIf { it.isFile }?.readText()

    override fun write(name: String, text: String) {
        dir.mkdirs()
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun backup(name: String) {
        val f = File(dir, name)
        if (f.isFile) f.copyTo(File(dir, "$name.bad"), overwrite = true)
    }
}
