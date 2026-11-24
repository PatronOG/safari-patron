package safaripatron.resources

import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Display
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import safaripatron.SafariPatron
import safaripatron.config.Config
import safaripatron.data.Critters
import safaripatron.data.Biome
import safaripatron.render.WorldRender
import safaripatron.safari.ChatParser
import safaripatron.safari.Run
import safaripatron.safari.Safari

object FloorDrops {
    private val model = FloorDropModel()
    private val needs = ResourceNeeds()
    private var world: Any? = null
    private var run = -1L
    private var ticks = 0
    private var visible: List<FloorGroup> = emptyList()
    private val aimed = ArrayDeque<Pair<Int, Long>>()

    fun init() {
        UseBlockCallback.EVENT.register { _, level, _, hit ->
            if (level.isClientSide) remember(DropPoint(hit.location.x, hit.location.y, hit.location.z))
            InteractionResult.PASS
        }
        AttackBlockCallback.EVENT.register { _, level, _, pos, _ ->
            if (level.isClientSide) remember(DropPoint(pos.x + .5, pos.y + .8, pos.z + .5))
            InteractionResult.PASS
        }
        UseEntityCallback.EVENT.register { _, level, _, entity, _ ->
            if (level.isClientSide) remember(DropPoint(entity.x, entity.y, entity.z))
            InteractionResult.PASS
        }
    }

    private fun remember(point: DropPoint) {
        if (!Safari.inSafari) return
        val candidates = visible.filter { it.point.distanceSquared(point) <= 2.25 }
        if (candidates.size != 1) return
        val now = System.currentTimeMillis()
        aimed.removeAll { now - it.second > 2500 }
        aimed.addLast(candidates.single().id to now)
        while (aimed.size > 16) aimed.removeFirst()
    }

    fun tick(client: Minecraft) {
        if (world !== client.level || run != Run.startStamp || !Safari.inSafari) {
            model.reset(); needs.reset(); aimed.clear(); visible = emptyList()
            world = client.level; run = Run.startStamp
        }
        if (!Safari.inSafari || !Run.running) return
        val player = client.player ?: return
        val counts = HashMap<DropKind, Int>()
        fun count(stack: ItemStack) {
            if (stack.isEmpty) return
            val id = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "").orEmpty()
            val kind = DropKind.fromItem(id, ChatParser.clean(stack.hoverName.string)) ?: return
            counts.merge(kind, stack.count, Int::plus)
        }
        for (slot in 0 until player.inventory.containerSize) count(player.inventory.getItem(slot))
        count(player.containerMenu.carried)
        needs.inventory(counts)
        if (++ticks % 5 != 0) return
        val level = client.level ?: return
        val displays = level.entitiesForRendering().filterIsInstance<Display.ItemDisplay>()
            .filter { !it.isRemoved && it.itemStack.`is`(Items.STRING) }
            .map { FloorDisplay(it.id, DropPoint(it.x, it.y, it.z),
                "minecraft:string:" + ChatParser.clean(it.itemStack.hoverName.string)) }.toList()
        visible = model.scan(displays) { Critters.biomeAt(it.x, it.z) }
    }

    fun onMessage(raw: String) {
        if (!Safari.inSafari || world !== Minecraft.getInstance().level || run != Run.startStamp) return
        for (line in raw.lines().map(ChatParser::clean)) {
            DropKind.entries.firstOrNull { it.gem && line == "You placed the ${it.label} on its podium!" }?.let(needs::placed)
            val kind = DropKind.pickup(line) ?: continue
            val now = System.currentTimeMillis()
            aimed.removeAll { now - it.second > 2500 }
            val ids = aimed.map { it.first }.distinct()
            if (ids.size == 1) model.picked(ids.single(), kind)
            aimed.clear()
        }
    }

    private const val GREEN = 0xFF00FF00.toInt()
    private const val PURPLE = 0xFFB04CFF.toInt()
    private const val RED = 0xFFFF5555.toInt()
    /** A Shining Coin you still need is gold instead of purple. */
    private const val GOLD = 0xFFFFAA00.toInt()

    fun collect(client: Minecraft, out: WorldRender.Batch) {
        if (!Config.s.floorDrops || !Safari.inSafari || !Run.running) return
        val player = client.player ?: return
        val s = Config.s
        val rules = NeedRules(s.floorGemRule, s.floorFoodRule, s.floorIncenseRule, s.floorCoinRule, s.floorIceRule)
        for (drop in visible) {
            if (drop.collected || drop.biome != Safari.biome ||
                drop.point.distanceSquared(DropPoint(player.x, player.y, player.z)) > 128.0 * 128) continue
            val kind = drop.kind
            val emphasis = DropVisibility.emphasis(kind, needs, Run.caught, s.floorResourcePriority,
                s.floorRewardDrops, s.floorFoodOnly && Safari.biome == Biome.FOREST, rules)
            val color = when (emphasis) {
                DropEmphasis.HIDDEN -> continue
                DropEmphasis.NEEDED -> if (kind == DropKind.COIN) GOLD else PURPLE
                DropEmphasis.UNKNOWN -> RED
                DropEmphasis.NORMAL -> GREEN
            }
            val (x, y, z) = drop.point.block
            out.box(AABB(x.toDouble(), y + 1.01, z.toDouble(), x + 1.0, y + 1.01, z + 1.0), color)
            // Like Scrappy's food: the item floats above the drop, facing the camera
            if (s.floorDropContents && kind != null && kind != DropKind.REWARD)
                out.billboard(Identifier.fromNamespaceAndPath(SafariPatron.MOD_ID, kind.texture), Vec3(x + .5, y + 1.3, z + .5))
        }
    }
}
