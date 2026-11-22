/*
 * Floor drop tespiti Nebulune'ün SafariESP modülünden uyarlandı
 * (Copyright (c) 2025, Starred — BSD 3-Clause License, licenses/Nebulune-BSD-3-Clause.txt).
 */
package safaripatron.esp

import net.minecraft.client.Minecraft
import safaripatron.config.Config
import safaripatron.data.Critters
import safaripatron.render.WorldRender
import safaripatron.safari.Safari
import safaripatron.safari.ScrappyFoodHints
import safaripatron.safari.ScrappyPriority

object Esp {
    private const val RANGE_SQ = 128.0 * 128.0

    fun tick(client: Minecraft) {
        CritterId.tick()
        safaripatron.resources.FloorDrops.tick(client)
    }

    fun collect(client: Minecraft, out: WorldRender.Batch) {
        if (!Safari.inSafari) return
        val level = client.level ?: return
        val player = client.player ?: return

        safaripatron.resources.FloorDrops.collect(client, out)

        if (!Config.s.esp) return
        val playerBiome = Safari.biome
        for (e in level.entitiesForRendering()) {
            if (e === player || e.distanceToSqr(player) > RANGE_SQ) continue
            val critter = CritterId.identify(e) ?: continue
            if (critter.biome != playerBiome) continue
            val emphasis = if (critter.name == "Scrappy") ScrappyFoodHints.emphasis(e) else ScrappyPriority.Emphasis.NORMAL
            if (emphasis == ScrappyPriority.Emphasis.HIDDEN) continue
            val color = when (emphasis) {
                ScrappyPriority.Emphasis.AVAILABLE -> 0xFF55FF55.toInt()
                ScrappyPriority.Emphasis.UNKNOWN -> 0xFFAAAAAA.toInt()
                else -> critter.espColor
            }
            out.box(BoxTuner.box(e, critter, capsule = false), color)
        }
    }
}
