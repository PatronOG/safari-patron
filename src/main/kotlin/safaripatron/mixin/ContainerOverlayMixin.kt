package safaripatron.mixin

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import safaripatron.hud.Hud

/**
 * Draws the profit panels on top of an open inventory or chest, so they stay sharp and in place
 * instead of being dimmed behind the screen. Render only; no input or item changes.
 */
@Mixin(AbstractContainerScreen::class)
abstract class ContainerOverlayMixin {
    @Inject(method = ["extractTooltip"], at = [At("HEAD")])
    private fun safaripatronOverlay(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ci: CallbackInfo) {
        Hud.renderOverScreen(graphics)
    }
}
