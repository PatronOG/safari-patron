package safaripatron.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

/**
 * On-screen placement of a HUD element: top-left corner as a fraction of the screen (0..1) and a scale.
 * x < 0 means "centered horizontally".
 */
class PanelPos(var x: Float = 0f, var y: Float = 0f, var scale: Float = 1f) {
    fun copy() = PanelPos(x, y, scale)
    fun set(o: PanelPos) { x = o.x; y = o.y; scale = o.scale }
}

/** Settings saved to config/safari-patron.json. */
class Settings {
    // Capsule
    var capsuleTrajectory = false
    /** Not shown in the menu; per-critter values will replace it in a later update. */
    var hitMargin = 0.1

    // Sparkling
    var sparklingFinder = true
    var sparklingSound = true

    // ESP
    var esp = true
    var espOnlyInBiome = true
    var floorDrops = true
    var beeNests = true
    var snoozleWalls = true

    // Party
    var warpCheck = true
    var hotspotAnnounce = true
    var biomeAnnouncements = true
    var screenAnnouncements = false

    // Chat prefix color style (safari, gold, emerald, sunset, aurora)
    var prefixStyle = "safari"

    // HUD elements
    var runPanel = PanelPos(0.01f, 0.28f)
    var biomePanel = PanelPos(0.01f, 0.60f)
    var sparklingPanel = PanelPos(-1f, 0.12f)
    var notifications = PanelPos(-1f, 0.36f)

    companion object {
        val DEFAULT_RUN = PanelPos(0.01f, 0.28f)
        val DEFAULT_BIOME = PanelPos(0.01f, 0.60f)
        val DEFAULT_SPARKLING = PanelPos(-1f, 0.12f)
        val DEFAULT_NOTIFICATIONS = PanelPos(-1f, 0.36f)
    }
}

object Config {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path = FabricLoader.getInstance().configDir.resolve("safari-patron.json")

    var s = Settings()
        private set

    fun load() {
        s = runCatching {
            if (Files.exists(path)) gson.fromJson(Files.readString(path), Settings::class.java) else null
        }.getOrNull() ?: Settings()
        // Older files may lack new fields or have scale 0
        for (p in listOf(s.runPanel, s.biomePanel, s.sparklingPanel, s.notifications)) {
            if (p.scale !in 0.5f..2f) p.scale = 1f
        }
        save()
    }

    fun save() {
        runCatching { Files.writeString(path, gson.toJson(s)) }
    }
}
