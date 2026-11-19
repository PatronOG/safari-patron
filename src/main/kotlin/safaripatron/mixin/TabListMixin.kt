package safaripatron.mixin

import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import safaripatron.party.TabList

/**
 * Sadece okur: sunucu değişimini ve tab list'e eklenen / çıkarılan oyuncuları [TabList]'e bildirir.
 * Hypixel sunucu değiştirirken eski oyuncuları listede bırakabildiği için warp kontrolü sadece yeni
 * sunucunun gönderdiği oyuncuları sayar. TAIL: paket ana thread'de işlendikten sonra (bir kere) çalışır.
 */
@Mixin(ClientPacketListener::class)
class TabListMixin {
    @Inject(method = ["handleLogin"], at = [At("TAIL")])
    private fun safariPatronLogin(packet: ClientboundLoginPacket, info: CallbackInfo) {
        TabList.onServerSwitch()
    }

    @Inject(method = ["handleRespawn"], at = [At("TAIL")])
    private fun safariPatronRespawn(packet: ClientboundRespawnPacket, info: CallbackInfo) {
        TabList.onServerSwitch()
    }

    @Inject(method = ["handlePlayerInfoUpdate"], at = [At("TAIL")])
    private fun safariPatronInfoUpdate(packet: ClientboundPlayerInfoUpdatePacket, info: CallbackInfo) {
        if (ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER !in packet.actions()) return
        TabList.onAdded(packet.newEntries().map { it.profileId() })
    }

    @Inject(method = ["handlePlayerInfoRemove"], at = [At("TAIL")])
    private fun safariPatronInfoRemove(packet: ClientboundPlayerInfoRemovePacket, info: CallbackInfo) {
        TabList.onRemoved(packet.profileIds())
    }
}
