package safaripatron.mixin

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.sugar.Local
import net.minecraft.client.renderer.block.FluidRenderer
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.material.FluidState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import safaripatron.visual.ClearWater

/** Clear water: see-through water surfaces (vanilla renderer). */
@Mixin(FluidRenderer::class)
class WaterSurfaceMixin {
    @ModifyExpressionValue(method = ["tesselate"], at = [At(value = "INVOKE", target = "Lnet/minecraft/client/color/block/BlockTintSource;colorInWorld(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I")])
    private fun safaripatronClearSurface(color: Int, @Local(argsOnly = true) fluid: FluidState): Int =
        ClearWater.surfaceColor(color, fluid.`is`(FluidTags.WATER))
}
