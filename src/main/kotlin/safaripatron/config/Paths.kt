package safaripatron.config

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Every file of the mod lives in its own folder, config/safari-patron/.
 * Files from before 1.2.0 (config/safari-patron.json, config/safari-patron-profit.json) are moved there once,
 * after a copy is kept in config/safari-patron/backup-1.1.2/.
 */
object Paths {
    private val log = LoggerFactory.getLogger("safari-patron/files")
    private val configDir: Path = FabricLoader.getInstance().configDir

    val dir: Path = configDir.resolve("safari-patron")
    val settings: Path = dir.resolve("settings.json")
    val profit: Path = dir.resolve("profit.json")
    /** 1.2.0's single history file; moved into [historyDir] on the first start of 1.3.0. */
    val history: Path = dir.resolve("history.json")
    val historyDir: Path = dir.resolve("history")
    val honeyhives: Path = dir.resolve("honeyhives.json")

    private val OLD_FILES = listOf("safari-patron.json" to settings, "safari-patron-profit.json" to profit)
    private var migrated = false

    /** Creates the folder and moves the old files in (once). */
    fun migrate() {
        if (migrated) return
        migrated = true
        runCatching { Files.createDirectories(dir) }.onFailure { log.warn("Could not create {}: {}", dir, it.message) }
        for ((oldName, target) in OLD_FILES) {
            val old = configDir.resolve(oldName)
            if (!Files.exists(old)) continue
            runCatching {
                val backup = dir.resolve("backup-1.1.2")
                Files.createDirectories(backup)
                Files.copy(old, backup.resolve(oldName), StandardCopyOption.REPLACE_EXISTING)
                if (Files.exists(target)) Files.delete(old) // the new file is newer; the old one stays in the backup
                else Files.move(old, target)
                log.info("Moved {} to {}", oldName, target)
            }.onFailure { log.warn("Could not move {}: {}", oldName, it.message) }
        }
    }

    /** Writes through a temporary file so a crash mid-write never leaves a broken file. */
    fun write(path: Path, text: String) {
        runCatching {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
            Files.writeString(tmp, text)
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { runCatching { Files.writeString(path, text) } }
    }

    fun read(path: Path): String? = runCatching { if (Files.exists(path)) Files.readString(path) else null }.getOrNull()
}
