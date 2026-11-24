package safaripatron.mixin

import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import safaripatron.cinematic.SparklingCinematic
import safaripatron.config.Config

/** Cinematic Hides Held Item on "Throws locked": right clicks do nothing while the cinematic plays, so nothing is thrown by mistake. */
@Mixin(Minecraft::class)
class CinematicUseLockMixin {
    @Inject(method = ["startUseItem"], at = [At("HEAD")], cancellable = true)
    private fun safaripatronLockUse(ci: CallbackInfo) {
        if (Config.s.sparklingCinematicHand == "lock" && SparklingCinematic.active) ci.cancel()
    }
}
