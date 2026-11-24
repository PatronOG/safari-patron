package safaripatron.mixin

import net.minecraft.client.renderer.GameRenderer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import safaripatron.cinematic.SparklingCinematic
import safaripatron.config.Config

/** Cinematic Hides Held Item: no hand or held item on screen while the sparkling cinematic plays (26.3). */
@Mixin(GameRenderer::class)
class HeldItemMixin {
    @Inject(method = ["renderItemInHand"], at = [At("HEAD")], cancellable = true)
    private fun safaripatronHideHeldItem(ci: CallbackInfo) {
        if (Config.s.sparklingCinematicHand != "off" && SparklingCinematic.active) ci.cancel()
    }
}
