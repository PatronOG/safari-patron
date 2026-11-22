// ScrappyFoodHints.kt
// Module: Food item icons above Scrappies before feeding
// Build: Kotlin 2.4.20 / Minecraft 26.3 / Fabric 0.19.5

package safaripatron.safari

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntityAttachment
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.armadillo.Armadillo
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.phys.Vec3
import safaripatron.config.Config
import safaripatron.data.Biome
import safaripatron.esp.CritterId
import safaripatron.render.WorldRender
import safaripatron.sparkling.SparklingFinder
import kotlin.math.sqrt

object ScrappyFoodHints {
    private const val RANGE_SQUARED = 128.0 * 128.0
    private val scrappyName = Regex("\\bScrappy\\b", RegexOption.IGNORE_CASE)
    private data class Hint(val body: Armadillo, val food: ScrappyFood?, val nameTag: ArmorStand?)
    private var hints = emptyList<Hint>()
    private val foodMemory = ScrappyFoodMemory()
    private var availableFoods = emptySet<ScrappyFood>()
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

    private fun enabled() = (Config.s.scrappyFoodHints || Config.s.scrappyFoodPriority) &&
        Safari.inSafari && Run.running && Safari.biome == Biome.CAVERN

    fun tick(client: Minecraft) {
        val level = client.level
        if (lastLevel !== level || runMarker != Run.startStamp) {
            hints = emptyList()
            foodMemory.clear()
            availableFoods = emptySet()
            lastLevel = level
            runMarker = Run.startStamp
            ticks = 0
        }
        if (!enabled() || level == null || client.player == null) { hints = emptyList(); return }
        if (ticks++ % 5 != 0) return
        val inventory = client.player!!.inventory
        val carriedIds = (0..35).mapNotNull { slot ->
            inventory.getItem(slot).get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "")
        }.toSet()
        availableFoods = ScrappyFood.entries.filter { it.itemId in carriedIds }.toSet()

        val entities = level.entitiesForRendering().toList()
        val scrappies = entities.filterIsInstance<Armadillo>().filter {
            !it.isRemoved && it.isAlive && CritterId.identify(it)?.name == "Scrappy"
        }
        val stands = entities.filterIsInstance<ArmorStand>().filterNot { it.isRemoved }
        val namedGuides = stands.mapNotNull { stand ->
            val food = ScrappyFood.fromHunterName(stand.customName?.string) ?: return@mapNotNull null
            ScrappyGuideMatcher.Guide(point(stand.position()), food)
        }
        // NPC skins remain identifiable even when their name tag is not drawn or loaded.
        // Prefer the explicit Hunter name whenever that tag is available.
        val skinGuides = entities.filterIsInstance<Player>().filter { it !== client.player && !it.isRemoved }
            .mapNotNull { npc ->
                val position = point(npc.position())
                if (namedGuides.any { it.pos.distanceSquared(position) <= 9.0 }) return@mapNotNull null
                val food = ScrappyFood.fromHunterTexture(CritterId.playerTexture(npc)) ?: return@mapNotNull null
                ScrappyGuideMatcher.Guide(position, food)
            }
        val guides = namedGuides + skinGuides
        val matches = ScrappyGuideMatcher.match(
            scrappies.map { ScrappyGuideMatcher.Subject(it.id, point(it.position())) }, guides)
        val nameTags = stands.filter { scrappyName.containsMatchIn(ChatParser.clean(it.customName?.string ?: "")) }
        hints = scrappies.map { body ->
            val position = point(body.position())
            val hasGuide = guides.any { it.pos.distanceSquared(position) <= 16.0 }
            val food = foodMemory.resolve(body.uuid.toString(), position, matches[body.id], hasGuide)
            Hint(body, food, nameTagFor(body, nameTags))
        }
    }

    internal fun emphasis(body: Entity): ScrappyPriority.Emphasis {
        val active = Config.s.scrappyFoodPriority && enabled() && lastLevel === Minecraft.getInstance().level && runMarker == Run.startStamp
        if (!active) return ScrappyPriority.Emphasis.NORMAL
        val hint = hints.firstOrNull { it.body === body }
        val sparkling = SparklingFinder.sparks.any { it.body === body } ||
            SparklingFinder.species(hint?.nameTag?.customName?.string) == "Scrappy"
        return ScrappyPriority.evaluate(true, "Scrappy" in Run.caught, sparkling, hint?.food, availableFoods)
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
            if (body.isRemoved || !body.isAlive || (body.isInvisible && !Config.s.scrappyFoodPriority) ||
                body.distanceToSqr(player) > RANGE_SQUARED) continue
            val emphasis = emphasis(body)
            if (emphasis == ScrappyPriority.Emphasis.HIDDEN) continue
            val distance = sqrt(body.distanceToSqr(player)).toInt()
            val far = distance > 12
            val food = hint.food
            val raised = Vec3(body.x, body.boundingBox.maxY + 1.5, body.z)
            if (far && Config.s.scrappyFoodPriority) {
                val meal = when (food) { ScrappyFood.WHEAT -> "Wheat"; ScrappyFood.LILY_PAD -> "Lily Pad"; ScrappyFood.FISH -> "Fish"; null -> "Food unknown" }
                val color = if (emphasis == ScrappyPriority.Emphasis.AVAILABLE) 0xFF55FF55.toInt() else 0xFFAAAAAA.toInt()
                out.label("Scrappy · $meal · ${distance}m", raised.add(0.0, -0.2, 0.0), color, bold = true)
            }
            if (!Config.s.scrappyFoodHints || food == null || !body.isScared) continue
            val marker = hint.nameTag?.takeUnless { it.isRemoved }
            val anchor = if (far) raised else if (marker == null) Vec3(body.x, body.boundingBox.maxY + 0.8, body.z)
            else {
                val attachment = marker.attachments.getNullable(EntityAttachment.NAME_TAG, 0, marker.yRot)
                    ?: Vec3(0.0, marker.bbHeight.toDouble(), 0.0)
                marker.position().add(attachment).add(0.0, 0.5, 0.0)
            }
            out.icon(icons.getValue(food), anchor)
        }
    }
}
