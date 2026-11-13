package safaripatron.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.ScreenEffectRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import safaripatron.visual.ClearWater

/** Clear water: no blue underwater screen overlay while in the Safari (26.2). */
@Mixin(ScreenEffectRenderer::class)
class WaterOverlayMixin {
    @WrapOperation(method = ["submit"], at = [At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitWater(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V")])
    private fun safaripatronNoOverlay(client: Minecraft, pose: PoseStack, buffers: SubmitNodeCollector, original: Operation<Void>) {
        if (!ClearWater.active) original.call(client, pose, buffers)
    }
}
