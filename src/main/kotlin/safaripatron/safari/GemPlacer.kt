// GemPlacer.kt
// Module: Crosshair-gated Cavern gem placement
// Build: Kotlin 2.4.20 / Minecraft 26.3 / Fabric 0.19.5

package safaripatron.safari

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import safaripatron.config.Config
import safaripatron.data.Biome

internal class GemPlacer(podiums: Map<BlockPos, CavernGem>) {
    private val podiums = podiums.mapKeys { it.key.immutable() }
    private val state = GemPlacementState()
    private var levelMarker: Any? = null
    private var runMarker = -1L
    private var tick = 0L

    init {
        require(this.podiums.size == CavernGem.entries.size &&
            this.podiums.values.toSet() == CavernGem.entries.toSet()) {
            "Each Cavern gem must have exactly one podium"
        }
    }

    private data class Target(val gem: CavernGem, val hit: BlockHitResult)

    fun tick(client: Minecraft) {
        tick++
        if (levelMarker !== client.level || runMarker != Run.startStamp) {
            state.reset()
            levelMarker = client.level
            runMarker = Run.startStamp
        }

        val target = target(client)
        state.lookAt(target?.gem)
        if (target == null) return
        val player = client.player ?: return
        val gameMode = client.gameMode ?: return
        val inventory = player.inventory
        val ids = (0..8).map { slot ->
            val stack = inventory.getItem(slot)
            if (stack.isEmpty) null else stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "")
        }
        when (val action = state.nextAction(ids, inventory.selectedSlot, tick,
            GemMode.fromSetting(Config.s.cavernGemMode), Config.s.cavernGemPlacementDelay) ?: return) {
            is GemPlacementState.Action.Select -> { inventory.selectedSlot = action.slot; return }
            is GemPlacementState.Action.Place -> if (inventory.selectedSlot != action.slot) return
        }

        // The selected slot has settled and the crosshair was rechecked this tick.
        val animation = player.mainHandItem.interactAnimation
        val result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, target.hit)
        if (result is InteractionResult.Success && result.swingSource() == InteractionResult.SwingSource.PREDICTED)
            player.swing(InteractionHand.MAIN_HAND, animation, false)
    }

    private fun target(client: Minecraft): Target? {
        if (!Config.s.cavernGemPlacer || !Safari.inSafari || !Run.running || Safari.biome != Biome.CAVERN ||
            client.gui.screen() != null || !client.isWindowActive) return null
        val player = client.player ?: return null
        val level = client.level ?: return null
        if (!player.isAlive || player.isSpectator || player.isUsingItem ||
            client.options.keyAttack.isDown || (Config.s.cavernGemMode == "auto" && client.options.keyUse.isDown)) return null

        val crosshair = client.hitResult as? BlockHitResult ?: return null
        if (crosshair.type != HitResult.Type.BLOCK) return null
        val pos = crosshair.blockPos
        val gem = podiums[pos] ?: return null
        val block = when (gem) {
            CavernGem.LIME -> Blocks.VERDANT_FROGLIGHT
            CavernGem.PURPLE -> Blocks.PEARLESCENT_FROGLIGHT
            CavernGem.ORANGE -> Blocks.OCHRE_FROGLIGHT
        }
        if (!level.isLoaded(pos) || !level.getBlockState(pos).`is`(block) ||
            !level.getBlockState(pos.above()).isAir || !player.isWithinBlockInteractionRange(pos, 0.0)) return null

        // Recheck the current rotation so a stale rendered crosshair cannot trigger a placement.
        val hit = player.pick(player.blockInteractionRange(), 1f, false) as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK || hit.blockPos != pos) return null
        return Target(gem, hit)
    }

    fun onMessage(raw: String) {
        val client = Minecraft.getInstance()
        if (!Safari.inSafari || levelMarker !== client.level || runMarker != Run.startStamp) return
        val text = raw.replace(Regex("§[0-9A-FK-ORa-fk-or]"), "").trim()
        val gem = CavernGem.entries.firstOrNull { text == "You placed the ${it.label} on its podium!" }
        if (gem != null) state.placed(gem)
    }
}
