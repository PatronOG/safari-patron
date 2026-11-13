package safaripatron.mixin

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import safaripatron.hud.Hud

/** Mouse wheel inside an inventory or chest: over the Total Profit Tracker it scrolls the panel. */
@Mixin(AbstractContainerScreen::class)
abstract class ContainerScrollMixin {
    @Inject(method = ["mouseScrolled"], at = [At("HEAD")], cancellable = true)
    private fun safaripatronScroll(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double, cir: CallbackInfoReturnable<Boolean>) {
        if (Hud.scrollTotalPanelAt(mouseX, mouseY, scrollY)) cir.returnValue = true
    }
}
