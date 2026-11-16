package safaripatron.esp

import com.google.gson.GsonBuilder
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import safaripatron.config.Paths
import safaripatron.data.Critter

/**
 * Box size of every critter, for the ESP and the Capsule Helper's hit check (/sp boxes).
 * Defaults are the sizes measured in game; players can change each one. The box keeps its bottom
 * and center, only width and height change. Changes are saved in config/safari-patron/boxes.json.
 */
object BoxTuner {
    class Size(var w: Double = 0.6, var h: Double = 0.6)

    class Data {
        /** Sizes the player changed (critter name -> size). */
        var sizes: MutableMap<String, Size> = LinkedHashMap()
        var forEsp = true
        var forCapsule = true
    }

    /** Measured in game (width x height in blocks); Doomspiral standing (it is smaller while digging). */
    private val DEFAULTS: Map<String, Size> = mapOf(
        "Foxtrot" to Size(0.60, 0.70),
        "Bluebird" to Size(0.50, 0.90),
        "Honeybug" to Size(0.55, 0.50),
        "Treefrog" to Size(0.50, 0.50),
        "Woodchucker" to Size(0.90, 2.70),
        "Fluffling" to Size(1.30, 1.25),
        "Hideonfloor" to Size(1.00, 1.00),
        "Parakeet" to Size(0.50, 0.90),
        "Macaw" to Size(0.50, 0.90),
        "Cavernfish" to Size(0.50, 0.40),
        "Flitter" to Size(0.60, 0.60),
        "Shyworm" to Size(0.60, 0.60),
        "Driftling" to Size(0.60, 0.60),
        "Chuckwalla" to Size(0.60, 0.60),
        "Rockmite" to Size(0.60, 0.60),
        "Scrappy" to Size(0.70, 0.65),
        "Snoozle" to Size(0.71, 0.66),
        "Gemzie" to Size(0.40, 0.80),
        "Strongarm" to Size(0.70, 1.90),
        "Tepid" to Size(0.50, 0.40),
        "Polaris" to Size(1.40, 1.40),
        "Shuddersquid" to Size(0.80, 0.80),
        "Billygoat" to Size(0.90, 1.30),
        "Mantis Shrimp" to Size(0.60, 0.60),
        "Nozzlenose" to Size(0.90, 0.60),
        "Troodon" to Size(0.60, 0.60),
        "Wumpa" to Size(1.95, 2.20),
        "Areita" to Size(0.70, 0.50),
        "Bloodbat" to Size(0.50, 0.90),
        "Duplico" to Size(1.10, 1.10),
        "Gazer" to Size(0.60, 0.60),
        "Litterbug" to Size(0.40, 0.30),
        "Solsnatcher" to Size(0.90, 0.50),
        "Gimmiegold" to Size(0.60, 0.60),
        "Hideonwall" to Size(1.00, 1.00),
        "Hideyho" to Size(0.60, 1.80),
        "Doomspiral" to Size(0.90, 2.90),
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = Paths.dir.resolve("boxes.json")
    var data = Data()
        private set
    private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        data = Paths.read(file)?.let { runCatching { gson.fromJson(it, Data::class.java) }.getOrNull() } ?: Data()
        @Suppress("SENSELESS_COMPARISON")
        if (data.sizes == null) data.sizes = LinkedHashMap()
    }

    fun save() = Paths.write(file, gson.toJson(data))

    /** The player's own size for this critter, null if it uses the default. */
    fun get(c: Critter): Size? { load(); return data.sizes[c.name] }

    fun defaultOf(c: Critter): Size = DEFAULTS[c.name]?.let { Size(it.w, it.h) } ?: Size()

    /** What the critter uses now: the player's size or the default. */
    fun current(c: Critter): Size = get(c)?.let { Size(it.w, it.h) } ?: defaultOf(c)

    fun set(c: Critter, s: Size) { load(); data.sizes[c.name] = s; save() }

    fun reset(c: Critter) { load(); data.sizes.remove(c.name); save() }

    fun resetAll() { load(); data.sizes.clear(); save() }

    fun setUse(esp: Boolean? = null, capsule: Boolean? = null) {
        load()
        esp?.let { data.forEsp = it }
        capsule?.let { data.forCapsule = it }
        save()
    }

    /** The box to draw / hit: the critter's size when turned on for this use, otherwise the entity's own box. */
    fun box(e: Entity, c: Critter, capsule: Boolean): AABB {
        load()
        val base = CritterId.box(e)
        if (!(if (capsule) data.forCapsule else data.forEsp)) return base
        val s = data.sizes[c.name] ?: DEFAULTS[c.name] ?: return base
        val cx = (base.minX + base.maxX) / 2
        val cz = (base.minZ + base.maxZ) / 2
        return AABB(cx - s.w / 2, base.minY, cz - s.w / 2, cx + s.w / 2, base.minY + s.h, cz + s.w / 2)
    }
}
