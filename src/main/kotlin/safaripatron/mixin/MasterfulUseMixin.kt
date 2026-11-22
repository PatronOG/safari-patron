// MasterfulUseMixin.kt
// Module: Cancel a Masterful right click when the predicted species is not permitted
// Build: Kotlin 2.4.20 / Minecraft 26.3 / Fabric 0.19.5

package safaripatron.mixin

import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import safaripatron.capsule.MasterfulGuard

@Mixin(Minecraft::class)
class MasterfulUseMixin {
    @Inject(method = ["startUseItem"], at = [At("HEAD")], cancellable = true, require = 1)
    private fun safariPatronMasterfulUse(info: CallbackInfo) {
        if (MasterfulGuard.shouldBlock(Minecraft.getInstance())) info.cancel()
    }
}
