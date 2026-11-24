package safaripatron.cinematic

import com.google.gson.GsonBuilder
import net.minecraft.client.Minecraft
import net.minecraft.nbt.TagParser
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import org.slf4j.LoggerFactory
import safaripatron.config.Paths
import java.nio.file.Files

/**
 * Saved, still critter models for the sparkling cinematic: the built-in ones in the jar, or your own capture.
 * A capture (the `/sp capture` tool of test builds) writes every entity that builds a critter (with the game's own save format, so head textures,
 * item display sizes and mob colors are all kept) to config/safari-patron/models/<critter>.json.
 * The cinematic rebuilds them as entities that only exist on your screen, so the model shows even when the
 * critter is far away or already caught, and it never moves.
 */
object CritterModels {
    private val log = LoggerFactory.getLogger("safari-patron/models")
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val dir get() = Paths.dir.resolve("models")

    class SavedPart(val dx: Double = 0.0, val dy: Double = 0.0, val dz: Double = 0.0, val nbt: String = "")
    class SavedModel(val critter: String = "", val parts: List<SavedPart> = emptyList())

    private fun fileName(critter: String) = critter.lowercase().replace(' ', '_') + ".json"

    /** Tags that tie an entity to its place in the world; a screen-only copy doesn't need them. */
    private val DROP = listOf("UUID", "Pos", "Motion", "Passengers", "CustomName", "CustomNameVisible", "leash", "Glowing",
        // A bee angry at capture time would stay angry (red eyes) in every copy
        "anger_end_time", "angry_at", "AngerTime", "AngryAt")

    /** Saves [pieces] (from [SparklingCinematic.entitiesAround]) as [critter]'s model; returns the file name. */
    fun save(critter: String, pieces: List<SparklingCinematic.Live>): String? {
        val level = Minecraft.getInstance().level ?: return null
        val parts = pieces.mapNotNull { p ->
            try {
                // A player-shaped critter (Hideyho) can't be rebuilt as a player: save a mannequin wearing its skin
                (p.entity as? net.minecraft.world.entity.player.Player)?.let { player ->
                    return@mapNotNull mannequin(player)?.let { SavedPart(p.dx, p.dy, p.dz, it) }
                }
                val out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess())
                p.entity.saveWithoutId(out)
                val tag = out.buildResult()
                for (k in DROP) tag.remove(k)
                tag.putString("id", EntityType.getKey(p.entity.type).toString())
                SavedPart(p.dx, p.dy, p.dz, tag.toString())
            } catch (t: Throwable) {
                log.warn("Could not save a piece of {}", critter, t)
                null
            }
        }
        if (parts.isEmpty()) return null
        return try {
            Files.createDirectories(dir)
            val name = fileName(critter)
            Files.writeString(dir.resolve(name), gson.toJson(SavedModel(critter, parts)))
            cache.remove(critter.lowercase())
            name
        } catch (t: Throwable) {
            log.warn("Could not write the model of {}", critter, t)
            null
        }
    }

    /** SNBT of a mannequin (an entity that wears any skin) dressed like [player], or null without a skin. */
    private fun mannequin(player: net.minecraft.world.entity.player.Player): String? {
        val profile = Minecraft.getInstance().connection?.getPlayerInfo(player.uuid)?.profile ?: player.gameProfile
        val skin = profile.properties().get("textures").firstOrNull() ?: return null
        val signature = skin.signature()?.let { ",signature:\"$it\"" } ?: ""
        return "{id:\"minecraft:mannequin\",hide_description:1b,Rotation:[${player.yRot}f,0.0f]," +
            "profile:{properties:[{name:\"textures\",value:\"${skin.value()}\"$signature}]}}"
    }

    private fun parse(text: String?): SavedModel? =
        text?.let { runCatching { gson.fromJson(it, SavedModel::class.java) }.getOrNull() }?.takeIf { it.parts.isNotEmpty() }

    /** Your own capture in config/safari-patron/models. */
    private fun readSaved(critter: String): SavedModel? =
        parse(runCatching { dir.resolve(fileName(critter)).takeIf { Files.exists(it) }?.let { Files.readString(it) } }.getOrNull())

    /** The model that ships with the mod. */
    private fun readBuiltIn(critter: String): SavedModel? = parse(runCatching {
        CritterModels::class.java.getResourceAsStream("/assets/safari-patron/critter_models/${fileName(critter)}")?.use { it.readBytes().decodeToString() }
    }.getOrNull())

    private fun read(critter: String): SavedModel? = readSaved(critter) ?: readBuiltIn(critter)

    fun has(critter: String) = read(critter) != null

    private class Built(val level: Any, val pieces: List<SparklingCinematic.Live>)
    private val cache = HashMap<String, Built>()

    /** The saved model rebuilt as screen-only entities, or empty when there is none or it can't be loaded. */
    fun pieces(critter: String): List<SparklingCinematic.Live> {
        val client = Minecraft.getInstance()
        val level = client.level ?: return emptyList()
        val player = client.player ?: return emptyList()
        cache[critter.lowercase()]?.takeIf { it.level === level }?.let { return it.pieces }
        // Your capture first; if nothing of it can be shown (an old player save, an invisible box), the built-in one
        val built = build(readSaved(critter), critter, level, player).ifEmpty { build(readBuiltIn(critter), critter, level, player) }
        cache[critter.lowercase()] = Built(level, built)
        return built
    }

    private fun build(model: SavedModel?, critter: String, level: net.minecraft.client.multiplayer.ClientLevel,
                      player: net.minecraft.world.entity.player.Player): List<SparklingCinematic.Live> {
        if (model == null) return emptyList()
        return model.parts.mapNotNull { p ->
            try {
                val tag = TagParser.parseCompoundFully(p.nbt)
                val input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag)
                val type = EntityType.by(input).orElse(null)
                    ?: run { SparklingCinematic.lastError = "unknown entity type in the saved model"; return@mapNotNull null }
                // Players can't be rebuilt, and an interaction box draws nothing
                if (type == net.minecraft.world.entity.EntityTypes.PLAYER || type == net.minecraft.world.entity.EntityTypes.INTERACTION) return@mapNotNull null
                val e: Entity = EntityType.create(type, input, level, EntitySpawnReason.LOAD).orElse(null)
                    ?: run { SparklingCinematic.lastError = "the game could not rebuild a ${EntityType.getKey(type)}"; return@mapNotNull null }
                CritterGallery.giveId(e)
                // Lit like where you stand
                e.setPos(player.x + p.dx, player.y + p.dy, player.z + p.dz)
                // Item displays build what they draw (size, turn) on their first ticks
                if (e is Display) repeat(3) { e.tick() }
                SparklingCinematic.Live(e, p.dx, p.dy, p.dz)
            } catch (t: Throwable) {
                log.warn("Could not load a piece of {}", critter, t)
                SparklingCinematic.lastError = "loading a piece: ${t.javaClass.simpleName} ${t.message ?: ""}".trim()
                null
            }
        }
    }
}
