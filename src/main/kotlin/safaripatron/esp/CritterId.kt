/*
 * Critter tanıma kuralları Nebulune'ün SafariESP modülünden uyarlandı.
 * Nebulune — Copyright (c) 2025, Starred — BSD 3-Clause License (licenses/Nebulune-BSD-3-Clause.txt).
 * Değişiklikler: Athen/Kontext bağımlılıkları kaldırıldı, dokular base64 yerine doku hash'i ile karşılaştırılıyor.
 */
package safaripatron.esp

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.animal.fish.TropicalFish
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Shulker
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.phys.AABB
import safaripatron.data.Biome
import safaripatron.data.Critter
import safaripatron.data.Critters
import java.util.Base64

object CritterId {
    private object Tex {
        const val FLITTER = "a89a76deedd42b410344100df2fa79b6eeac7e6f287745d656179368340ffade"
        const val SHYWORM = "b4287b8a0a642dac535a6ee29459efd17d5cee1eb359e436bc8e7abba3da14b7"
        const val DRIFTLING = "f4c4f8e5fce1ec2d299cb8a395792ecddc497a1d8af86faaa5e20373016c7225"
        const val CHUCKWALLA = "fc63cd0d480971a7beae5fd503e5d51658cd906330843cbad92018f5b98b4fe5"
        const val ROCKMITE = "5dbaab74d1acd0abe9d04abe9928725de5d4495fcb63b647228caf6944c20800"
        const val GAZER = "407b3c3d2c3fe259d69207a14ca5cd99713c7096ba122bb40326f3489e5d0d6c"
        const val GIMMIEGOLD = "8b329e108ac28b0bec8d47b7cdce253df1db80b46052b5915d963e1bcbab0db4"
        const val HIDEYHO = "3504f1f2327a5110e643bb8667082512815fa434a29ed37f4ca83bb16d2db533"
        const val MANTIS_SHRIMP = "9924c105aa431dabd47952dc1dddd6f751f883423f4db1487d9bacc2cfe99c7a"
        const val TROODON = "53de4135a3b19a2187029c86a0020e58c907c7bdd4e37b7643f120e16a0aa9ab"
    }

    // ---- Doku okuma ----
    private val texturePattern = Regex("textures\\.minecraft\\.net/texture/([a-fA-F0-9]{64})")
    private val textureCache = HashMap<String, String?>()

    private fun hash(encoded: String?): String? {
        if (encoded == null) return null
        return textureCache.getOrPut(encoded) {
            runCatching {
                texturePattern.find(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))?.groupValues?.get(1)?.lowercase()
            }.getOrNull()
        }.also { if (textureCache.size > 512) textureCache.clear() }
    }

    private fun texture(profile: GameProfile?): String? =
        hash(profile?.properties()?.get("textures")?.firstOrNull()?.value())

    fun texture(stack: ItemStack): String? = texture(stack.get(DataComponents.PROFILE)?.partialProfile())

    private fun playerTexture(p: Player): String? =
        texture(Minecraft.getInstance().connection?.getPlayerInfo(p.uuid)?.profile ?: p.gameProfile)

    private fun shulkerColor(e: Entity): String? = when (e) {
        is Shulker -> e.color?.name
        is Display.BlockDisplay -> (e.blockState.block as? ShulkerBoxBlock)?.color?.name
        is Display.ItemDisplay -> (Block.byItem(e.itemStack.item) as? ShulkerBoxBlock)?.color?.name
        else -> null
    }

    // ---- Kurallar (bölge bazında, Nebulune ile aynı sıra) ----
    private fun forest(e: Entity): String? = when {
        e.type == EntityTypes.FOX -> "Foxtrot"
        e is Parrot && e.variant.name == "BLUE" -> "Bluebird"
        e.type == EntityTypes.BEE -> "Honeybug"
        e.type == EntityTypes.FROG -> "Treefrog"
        e.type == EntityTypes.CREAKING -> "Woodchucker"
        e.type == EntityTypes.PANDA -> "Fluffling"
        shulkerColor(e) == "GREEN" -> "Hideonfloor"
        e is Parrot && e.variant.name == "GREEN" -> "Parakeet"
        e is Parrot && e.variant.name == "RED_BLUE" -> "Macaw"
        else -> null
    }

    private fun cavern(e: Entity): String? = when {
        e is TropicalFish && e.pattern.name == "CLAYFISH" && e.baseColor.name == "GRAY" && e.patternColor.name == "BROWN" -> "Cavernfish"
        e is Display.ItemDisplay && texture(e.itemStack) == Tex.FLITTER -> "Flitter"
        e is ArmorStand && headTexture(e) == Tex.SHYWORM -> "Shyworm"
        e is ArmorStand && headTexture(e) == Tex.DRIFTLING -> "Driftling"
        e is Display.ItemDisplay && texture(e.itemStack) == Tex.CHUCKWALLA -> "Chuckwalla"
        e.type == EntityTypes.SILVERFISH && !e.isInvisible && e.passengers.isEmpty() -> "Rockmite"
        e is Display.ItemDisplay && texture(e.itemStack) == Tex.ROCKMITE -> "Rockmite"
        e.type == EntityTypes.ARMADILLO -> "Scrappy"
        e.type == EntityTypes.SNIFFER -> "Snoozle"
        e.type == EntityTypes.VEX -> "Gemzie"
        else -> null
    }

    private fun icy(e: Entity): String? = when {
        e.type == EntityTypes.SNOW_GOLEM -> "Strongarm"
        e is TropicalFish && e.pattern.name == "SNOOPER" && e.baseColor.name == "WHITE" && e.patternColor.name == "WHITE" -> "Tepid"
        e.type == EntityTypes.POLAR_BEAR -> "Polaris"
        e.type == EntityTypes.GLOW_SQUID -> "Shuddersquid"
        e.type == EntityTypes.GOAT -> "Billygoat"
        e is Display.ItemDisplay && texture(e.itemStack) == Tex.MANTIS_SHRIMP -> "Mantis Shrimp"
        e.type == EntityTypes.DOLPHIN -> "Nozzlenose"
        e is Display.ItemDisplay && texture(e.itemStack) == Tex.TROODON -> "Troodon"
        e.type == EntityTypes.RAVAGER -> "Wumpa"
        else -> null
    }

    private fun haunted(e: Entity): String? = when {
        e.type == EntityTypes.CAVE_SPIDER -> "Areita"
        e.type == EntityTypes.BAT -> "Bloodbat"
        e.type == EntityTypes.INTERACTION -> "Duplico"
        e is ArmorStand && headTexture(e) == Tex.GAZER -> "Gazer"
        e.type == EntityTypes.ENDERMITE -> "Litterbug"
        e.type == EntityTypes.PHANTOM -> "Solsnatcher"
        e is Display.ItemDisplay && texture(e.itemStack) == Tex.GIMMIEGOLD -> "Gimmiegold"
        shulkerColor(e) == "PURPLE" -> "Hideonwall"
        e is Player && playerTexture(e) == Tex.HIDEYHO -> "Hideyho"
        e.type == EntityTypes.WARDEN -> "Doomspiral"
        else -> null
    }

    private fun headTexture(a: ArmorStand): String? {
        val head = a.getItemBySlot(EquipmentSlot.HEAD)
        return if (head.isEmpty) null else texture(head)
    }

    private fun isFloorDrop(e: Entity) = e is Display.ItemDisplay && e.itemStack.`is`(Items.STRING)

    // ---- Önbellek: pozitif sonuçlar kalıcı, negatifler 2 sn'de bir yeniden denenir (kafa sonradan takılabilir) ----
    private class Entry(val identity: Int, val critter: Critter?, val tick: Long)
    private val cache = HashMap<Int, Entry>()
    private var tick = 0L

    fun tick() {
        tick++
        if (tick % 100 == 0L) {
            val level = Minecraft.getInstance().level
            cache.entries.removeIf { level?.getEntity(it.key)?.isRemoved != false }
        }
    }

    fun clear() = cache.clear()

    /** Entity bir Safari critter'ı ise onu döner. Bölge, entity'nin bulunduğu çeyreğe göre seçilir. */
    fun identify(e: Entity): Critter? {
        if (e.isRemoved || isFloorDrop(e)) return null
        val id = System.identityHashCode(e)
        cache[e.id]?.let { if (it.identity == id && (it.critter != null || tick - it.tick < 40)) return it.critter }
        val biome = Critters.biomeAt(e.x, e.z)
        val name = when (biome) {
            Biome.FOREST -> forest(e)
            Biome.CAVERN -> cavern(e)
            Biome.ICY -> icy(e)
            Biome.HAUNTED -> haunted(e)
            null -> null
        }
        val critter = name?.let { Critters.named(it) }
        cache[e.id] = Entry(id, critter, tick)
        return critter
    }

    /** ESP kutusu ve isabet hesabı için kullanılan kutu (Nebulune ile aynı ölçüler). */
    fun box(e: Entity): AABB = when {
        e is Display && shulkerColor(e) != null ->
            AABB(e.x - 0.5, e.y, e.z - 0.5, e.x + 0.5, e.y + 1.0, e.z + 0.5)
        e is Display ->
            AABB(e.x - 0.3, e.y - 0.45, e.z - 0.3, e.x + 0.3, e.y + 0.15, e.z + 0.3)
        e is ArmorStand ->
            AABB(e.x - 0.3, e.y + 1.35, e.z - 0.3, e.x + 0.3, e.y + 1.95, e.z + 0.3)
        else -> e.boundingBox
    }
}
