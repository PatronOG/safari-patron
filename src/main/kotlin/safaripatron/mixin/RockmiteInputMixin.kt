package safaripatron.mixin

import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import safaripatron.helpers.RockmiteHoldClick

@Mixin(Minecraft::class)
class RockmiteInputMixin {
    @Inject(method = ["handleKeybinds"], at = [At("HEAD")], require = 1)
    private fun safariPatronMoundInput(info: CallbackInfo) { RockmiteHoldClick.prepare(Minecraft.getInstance()) }
}
