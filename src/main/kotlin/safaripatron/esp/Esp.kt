/*
 * Floor drop tespiti Nebulune'ün SafariESP modülünden uyarlandı
 * (Copyright (c) 2025, Starred — BSD 3-Clause License, licenses/Nebulune-BSD-3-Clause.txt).
 */
package safaripatron.esp

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Display
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import safaripatron.config.Config
import safaripatron.data.Critters
import safaripatron.render.WorldRender
import safaripatron.safari.Safari

object Esp {
    private const val FLOOR_COLOR = 0xFF00FF00.toInt()
    private const val RANGE_SQ = 128.0 * 128.0
    private var floorDrops: List<BlockPos> = emptyList()
    private var tick = 0

    fun tick(client: Minecraft) {
        CritterId.tick()
        if (!Safari.inSafari) { floorDrops = emptyList(); return }
        if (++tick % 10 != 0) return
        if (!Config.s.floorDrops) { floorDrops = emptyList(); return }
        val level = client.level ?: return
        val playerBiome = Safari.biome
        // Aynı blokta 3+ "string" item display = floor drop
        val counts = HashMap<BlockPos, Int>()
        for (e in level.entitiesForRendering()) {
            if (e is Display.ItemDisplay && e.itemStack.`is`(Items.STRING)) {
                if (Critters.biomeAt(e.x, e.z) != playerBiome) continue
                counts.merge(e.blockPosition(), 1, Int::plus)
            }
        }
        floorDrops = counts.filterValues { it >= 3 }.keys.toList()
    }

    fun collect(client: Minecraft, out: WorldRender.Batch) {
        if (!Safari.inSafari) return
        val level = client.level ?: return
        val player = client.player ?: return

        if (Config.s.floorDrops) for (pos in floorDrops) {
            out.box(AABB(pos.x.toDouble(), pos.y + 1.0, pos.z.toDouble(), pos.x + 1.0, pos.y + 1.0, pos.z + 1.0), FLOOR_COLOR)
        }

        if (!Config.s.esp) return
        val playerBiome = Safari.biome
        for (e in level.entitiesForRendering()) {
            if (e === player || e.distanceToSqr(player) > RANGE_SQ) continue
            val critter = CritterId.identify(e) ?: continue
            if (critter.biome != playerBiome) continue
            out.box(BoxTuner.box(e, critter, capsule = false), critter.espColor)
        }
    }
}
