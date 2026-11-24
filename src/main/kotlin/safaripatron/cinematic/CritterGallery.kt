package safaripatron.cinematic

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.animal.fish.TropicalFish
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.item.DyeColor
import org.slf4j.LoggerFactory
import safaripatron.data.Critter
import java.util.Optional

/**
 * Where each critter's 3D model comes from, for the sparkling cinematic (and the critter gallery in test builds).
 *  1. A saved model: built into the jar for every critter, or your own capture (exact Hypixel look, works anywhere).
 *  2. The real critter, if it is loaded nearby.
 *  3. For the 28 critters that are plain mobs: a screen-only copy of that mob, colored like Hypixel's.
 */
object CritterGallery {
    private val log = LoggerFactory.getLogger("safari-patron/gallery")

    /** Mob type and its setup; the colors follow the critter recognition rules in CritterId. */
    private class Mob(val type: EntityType<*>, val setup: (Entity) -> Unit = {})

    private fun parrot(v: Parrot.Variant) = Mob(EntityTypes.PARROT) {
        (it as? safaripatron.mixin.ParrotVariantInvoker)?.safariPatronSetVariant(v)
    }
    private fun fish(pattern: TropicalFish.Pattern, base: DyeColor, color: DyeColor) = Mob(EntityTypes.TROPICAL_FISH) {
        val packed = (pattern.packedId and 0xFFFF) or ((base.id and 0xFF) shl 16) or ((color.id and 0xFF) shl 24)
        (it as? safaripatron.mixin.TropicalFishVariantInvoker)?.safariPatronSetPackedVariant(packed)
    }
    private fun shulker(color: DyeColor) = Mob(EntityTypes.SHULKER) {
        (it as? safaripatron.mixin.ShulkerColorInvoker)?.safariPatronSetColor(Optional.of(color))
    }

    private val MOBS: Map<String, Mob> = mapOf(
        "Foxtrot" to Mob(EntityTypes.FOX),
        "Bluebird" to parrot(Parrot.Variant.BLUE),
        "Honeybug" to Mob(EntityTypes.BEE),
        "Treefrog" to Mob(EntityTypes.FROG),
        "Woodchucker" to Mob(EntityTypes.CREAKING),
        "Fluffling" to Mob(EntityTypes.PANDA),
        "Hideonfloor" to shulker(DyeColor.GREEN),
        "Parakeet" to parrot(Parrot.Variant.GREEN),
        "Macaw" to parrot(Parrot.Variant.RED_BLUE),
        "Cavernfish" to fish(TropicalFish.Pattern.CLAYFISH, DyeColor.GRAY, DyeColor.BROWN),
        "Rockmite" to Mob(EntityTypes.SILVERFISH),
        "Scrappy" to Mob(EntityTypes.ARMADILLO),
        "Snoozle" to Mob(EntityTypes.SNIFFER),
        "Gemzie" to Mob(EntityTypes.VEX),
        "Strongarm" to Mob(EntityTypes.SNOW_GOLEM),
        "Tepid" to fish(TropicalFish.Pattern.SNOOPER, DyeColor.WHITE, DyeColor.WHITE),
        "Polaris" to Mob(EntityTypes.POLAR_BEAR),
        "Shuddersquid" to Mob(EntityTypes.GLOW_SQUID),
        "Billygoat" to Mob(EntityTypes.GOAT),
        "Nozzlenose" to Mob(EntityTypes.DOLPHIN),
        "Wumpa" to Mob(EntityTypes.RAVAGER),
        "Areita" to Mob(EntityTypes.CAVE_SPIDER),
        "Bloodbat" to Mob(EntityTypes.BAT),
        "Litterbug" to Mob(EntityTypes.ENDERMITE),
        "Solsnatcher" to Mob(EntityTypes.PHANTOM),
        "Hideonwall" to shulker(DyeColor.PURPLE),
        "Doomspiral" to Mob(EntityTypes.WARDEN),
    )

    /**
     * Screen-only copies are never added to the world, so the server never numbers them; the game needs a
     * number to draw an entity. Negative numbers never clash with the real ones.
     */
    private var nextId = -1_000_000
    fun giveId(e: Entity) { e.setId(nextId--) }

    private class Fresh(val level: Any, val pieces: List<SparklingCinematic.Live>)
    private val fresh = HashMap<String, Fresh>()

    /** A screen-only copy of the mob this critter is, or empty for the head and item display critters. */
    fun freshMob(critter: String): List<SparklingCinematic.Live> {
        val client = Minecraft.getInstance()
        val level = client.level ?: return emptyList()
        val player = client.player ?: return emptyList()
        val mob = MOBS.entries.firstOrNull { it.key.equals(critter, true) }?.value ?: return emptyList()
        fresh[critter.lowercase()]?.takeIf { it.level === level }?.let { return it.pieces }
        val pieces = try {
            val e = mob.type.create(level, EntitySpawnReason.LOAD)
            if (e == null) emptyList() else {
                giveId(e)
                mob.setup(e)
                e.setPos(player.x, player.y, player.z)
                listOf(SparklingCinematic.Live(e, 0.0, 0.0, 0.0))
            }
        } catch (t: Throwable) {
            log.warn("Could not make a {} for the gallery", critter, t)
            emptyList()
        }
        fresh[critter.lowercase()] = Fresh(level, pieces)
        return pieces
    }

    /** The nearest loaded entity that is this critter. */
    private fun nearby(c: Critter): Entity? {
        val client = Minecraft.getInstance()
        val player = client.player ?: return null
        val level = client.level ?: return null
        return level.entitiesForRendering().filter { safaripatron.esp.CritterId.identify(it) == c }.minByOrNull { it.distanceToSqr(player) }
    }

    class Model(val pieces: List<SparklingCinematic.Live>, val source: String)

    /** Saved model first, then the real critter nearby, then the game's own mob. */
    fun model(c: Critter): Model {
        val saved = runCatching { CritterModels.pieces(c.name) }.getOrDefault(emptyList())
        if (saved.isNotEmpty()) return Model(saved, "saved")
        nearby(c)?.let { e -> SparklingCinematic.entitiesAround(e).takeIf { it.isNotEmpty() }?.let { return Model(it, "live") } }
        val mob = freshMob(c.name)
        if (mob.isNotEmpty()) return Model(mob, "game model")
        return Model(emptyList(), "no model")
    }
}
