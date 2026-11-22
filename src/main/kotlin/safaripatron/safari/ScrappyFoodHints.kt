// ScrappyFoodHints.kt
// Module: Food item icons above Scrappies before feeding
// Build: Kotlin 2.4.20 / Minecraft 26.3 / Fabric 0.19.5

package safaripatron.safari

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntityAttachment
import net.minecraft.world.entity.animal.armadillo.Armadillo
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.phys.Vec3
import safaripatron.config.Config
import safaripatron.data.Biome
import safaripatron.esp.CritterId
import safaripatron.render.WorldRender

object ScrappyFoodHints {
    private const val RANGE_SQUARED = 64.0 * 64.0
    private val scrappyName = Regex("\\bScrappy\\b", RegexOption.IGNORE_CASE)
    private data class Hint(val body: Armadillo, val food: ScrappyFood, val nameTag: ArmorStand?)
    private var hints = emptyList<Hint>()
    private var lastLevel: Any? = null
    private var runMarker = -1L
    private var ticks = 0
    private val icons by lazy {
        ScrappyFood.entries.associateWith { food ->
            val item = when (food) {
                ScrappyFood.FISH -> Items.COD
                ScrappyFood.LILY_PAD -> Items.LILY_PAD
                ScrappyFood.WHEAT -> Items.WHEAT
            }
            ItemStack(item).apply {
                set(DataComponents.CUSTOM_NAME, Component.literal(food.label))
                set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { putString("id", food.itemId) }))
            }
        }
    }

    private fun enabled() = Config.s.scrappyFoodHints && Safari.inSafari && Run.running && Safari.biome == Biome.CAVERN

    fun tick(client: Minecraft) {
        val level = client.level
        if (lastLevel !== level || runMarker != Run.startStamp) {
            hints = emptyList()
            lastLevel = level
            runMarker = Run.startStamp
            ticks = 0
        }
        if (!enabled() || level == null || client.player == null) { hints = emptyList(); return }
        if (ticks++ % 5 != 0) return

        val entities = level.entitiesForRendering().toList()
        val scrappies = entities.filterIsInstance<Armadillo>().filter {
            !it.isRemoved && it.isAlive && CritterId.identify(it)?.name == "Scrappy"
        }
        val stands = entities.filterIsInstance<ArmorStand>().filterNot { it.isRemoved }
        val guides = stands.mapNotNull { stand ->
            val food = ScrappyFood.fromHunterName(stand.customName?.string) ?: return@mapNotNull null
            ScrappyGuideMatcher.Guide(point(stand.position()), food)
        }
        val matches = ScrappyGuideMatcher.match(
            scrappies.map { ScrappyGuideMatcher.Subject(it.id, point(it.position())) }, guides)
        val nameTags = stands.filter { scrappyName.containsMatchIn(ChatParser.clean(it.customName?.string ?: "")) }
        hints = scrappies.mapNotNull { body ->
            val food = matches[body.id] ?: return@mapNotNull null
            Hint(body, food, nameTagFor(body, nameTags))
        }
    }

    private fun point(pos: Vec3) = ScrappyGuideMatcher.Point(pos.x, pos.y, pos.z)

    private fun nameTagFor(body: Armadillo, stands: List<ArmorStand>): ArmorStand? = stands.filter {
        val dx = it.x - body.x; val dz = it.z - body.z
        dx * dx + dz * dz <= 0.25 && it.y - body.y in 0.0..3.0
    }.minByOrNull { it.distanceToSqr(body) }

    fun collect(client: Minecraft, out: WorldRender.Batch) {
        if (!enabled() || lastLevel !== client.level || runMarker != Run.startStamp) return
        val player = client.player ?: return
        for (hint in hints) {
            val body = hint.body
            if (body.isRemoved || !body.isAlive || body.isInvisible || !body.isScared ||
                body.distanceToSqr(player) > RANGE_SQUARED) continue
            val marker = hint.nameTag?.takeUnless { it.isRemoved }
            val anchor = if (marker == null) Vec3(body.x, body.boundingBox.maxY + 0.8, body.z)
            else {
                val attachment = marker.attachments.getNullable(EntityAttachment.NAME_TAG, 0, marker.yRot)
                    ?: Vec3(0.0, marker.bbHeight.toDouble(), 0.0)
                marker.position().add(attachment).add(0.0, 0.5, 0.0)
            }
            out.icon(icons.getValue(hint.food), anchor)
        }
    }
}
