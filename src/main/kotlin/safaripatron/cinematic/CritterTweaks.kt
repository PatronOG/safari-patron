package safaripatron.cinematic

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import safaripatron.config.Paths
import java.nio.file.Files

/**
 * Per-critter turn and size fixes for the 3D models: built into the jar, and your own on top of them.
 * Built-in fixes ship in critter_models/tweaks.json; yours are saved to config/safari-patron/models/tweaks.json.
 */
object CritterTweaks {
    /** [lift]: how far the model moves up in its box, [side]: how far right, both in blocks of the model. */
    class Tweak(var yaw: Float = 0f, var pitch: Float = 0f, var roll: Float = 0f, var scale: Float = 1f, var lift: Float = 0f,
                var side: Float = 0f) {
        fun isDefault() = yaw == 0f && pitch == 0f && roll == 0f && scale == 1f && lift == 0f && side == 0f
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val type = object : TypeToken<LinkedHashMap<String, Tweak>>() {}.type
    private val file get() = Paths.dir.resolve("models").resolve("tweaks.json")
    private val tweaks: LinkedHashMap<String, Tweak> by lazy { load() }

    private fun load(): LinkedHashMap<String, Tweak> {
        val out = LinkedHashMap<String, Tweak>()
        runCatching {
            CritterTweaks::class.java.getResourceAsStream("/assets/safari-patron/critter_models/tweaks.json")
                ?.use { it.readBytes().decodeToString() }?.let { out.putAll(gson.fromJson<LinkedHashMap<String, Tweak>>(it, type)) }
        }
        runCatching {
            if (Files.exists(file)) out.putAll(gson.fromJson<LinkedHashMap<String, Tweak>>(Files.readString(file), type))
        }
        return out
    }

    fun get(critter: String): Tweak? = tweaks[critter.lowercase()]

    fun change(critter: String, yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f, scale: Float = 0f, lift: Float = 0f, side: Float = 0f) {
        val t = tweaks.getOrPut(critter.lowercase()) { Tweak() }
        t.yaw = (t.yaw + yaw + 360f) % 360f
        t.pitch = (t.pitch + pitch + 360f) % 360f
        t.roll = (t.roll + roll + 360f) % 360f
        t.scale = ((t.scale + scale) * 100).let { Math.round(it) / 100f }.coerceIn(.3f, 3f)
        t.lift = ((t.lift + lift) * 100).let { Math.round(it) / 100f }.coerceIn(-3f, 3f)
        t.side = ((t.side + side) * 100).let { Math.round(it) / 100f }.coerceIn(-3f, 3f)
        save()
    }

    fun reset(critter: String) { tweaks.remove(critter.lowercase()); save() }

    private fun save() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(tweaks.filterValues { !it.isDefault() }))
        }
    }
}
