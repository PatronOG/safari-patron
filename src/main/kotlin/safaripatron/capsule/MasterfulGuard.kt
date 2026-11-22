// MasterfulGuard.kt
// Module: Apply the optional species filter to player right clicks
// Build: Kotlin 2.4.20 / Minecraft 26.2 / Fabric 0.19.5

package safaripatron.capsule

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import safaripatron.config.Config
import safaripatron.safari.Run
import safaripatron.safari.Safari

object MasterfulGuard {
    fun active(client: Minecraft): Boolean {
        if (!Config.s.masterfulTargetFilter || !Safari.inSafari || !Run.running || client.gui.screen() != null) return false
        val player = client.player ?: return false
        if (!player.isAlive || player.isSpectator) return false
        return player.mainHandItem.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "") == "MASTERFUL_CRITTER_CAPSULE"
    }

    fun shouldBlock(client: Minecraft): Boolean = active(client) &&
        !MasterfulTargetPolicy.permits(true, true, Trajectory.predict(client)?.target?.name)
}
