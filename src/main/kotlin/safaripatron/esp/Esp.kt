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
            out.box(BoxTuner.box(e, critter, capsule = false), critter.espColor)
        }
    }
}
