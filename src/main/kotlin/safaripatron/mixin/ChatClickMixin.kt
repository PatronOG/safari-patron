package safaripatron.mixin

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.input.MouseButtonInfo
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import safaripatron.safari.HideyhoClick

/**
 * Fare tıklamasını en alt seviyede (diğer modlardan ve ekrandan önce) yakalar.
 * Sadece Hideyho [Sure] teklifi bekliyorken ve sohbet açıkken sol tıklamayı [Sure]'a çevirir;
 * diğer bütün durumlarda tıklamaya dokunmaz.
 */
@Mixin(value = [MouseHandler::class], priority = 100)
class ChatClickMixin {
    @Inject(method = ["onButton"], at = [At("HEAD")], cancellable = true)
    private fun safariPatronChatClick(window: Long, button: MouseButtonInfo, action: Int, info: CallbackInfo) {
        // Sol tık değeri sabitten okunur (26.2 GLFW: 0, 26.3 SDL: 1). Basma ve bırakma ikisi de
        // buraya gelir; ilki teklifi kullanıp temizlediği için ikincisi hiçbir şey yapmaz.
        if (button.button() != InputConstants.MOUSE_BUTTON_LEFT) return
        if (Minecraft.getInstance().gui.screen() !is ChatScreen) return
        if (HideyhoClick.onChatClick()) info.cancel()
    }
}
