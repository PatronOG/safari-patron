package safaripatron.mixin

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.sugar.Local
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.material.FluidState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import safaripatron.visual.ClearWater

/** Clear water with Sodium, whose fluid mesher doesn't use the vanilla one. Skipped when Sodium isn't installed. */
@Pseudo
@Mixin(targets = ["net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer"], remap = false)
class SodiumWaterMixin {
    @ModifyExpressionValue(method = ["updateQuad"], at = [At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/api/util/ColorARGB;toABGR(I)I")], require = 0)
    private fun safaripatronClearSurface(color: Int, @Local(argsOnly = true) fluid: FluidState): Int =
        ClearWater.surfaceColor(color, fluid.`is`(FluidTags.WATER))
}
