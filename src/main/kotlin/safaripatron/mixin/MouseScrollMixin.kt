package safaripatron.mixin

import net.minecraft.client.MouseHandler
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import safaripatron.hud.Hud

/**
 * Mouse wheel at the lowest level, before any screen or mod (REI's item list sits on the same side):
 * over the Total Profit Tracker in an open inventory it scrolls the panel; anywhere else nothing changes.
 */
@Mixin(value = [MouseHandler::class], priority = 100)
class MouseScrollMixin {
    @Inject(method = ["onScroll"], at = [At("HEAD")], cancellable = true)
    private fun safaripatronScroll(window: Long, horizontal: Double, vertical: Double, ci: CallbackInfo) {
        if (Hud.scrollTotalPanel(vertical)) ci.cancel()
    }
}
