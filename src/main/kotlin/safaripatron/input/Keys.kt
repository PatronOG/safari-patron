package safaripatron.input

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import safaripatron.config.Config

/**
 * Keys set in our own settings menu (not in Minecraft's Controls). Each one is a key mapping the game
 * itself watches, so keyboard keys and mouse buttons both work and nothing fires while a menu or chat is open.
 */
object Keys {
    /** Turns the Capsule Helper on or off. */
    private val capsuleHelper = KeyMapping("key.safari-patron.capsule_helper", InputConstants.Type.KEYBOARD,
        InputConstants.UNKNOWN.value, KeyMapping.Category.MISC)

    fun init() {
        apply(capsuleHelper, Config.s.capsuleHelperKey)
        ClientTickEvents.END_CLIENT_TICK.register {
            var pressed = false
            while (capsuleHelper.consumeClick()) pressed = !pressed
            if (pressed) {
                Config.s.capsuleTrajectory = !Config.s.capsuleTrajectory
                Config.save()
            }
        }
    }

    /** The key saved as [name] ("" or unknown = none). */
    fun keyOf(name: String): InputConstants.Key =
        if (name.isBlank()) InputConstants.UNKNOWN else runCatching { InputConstants.getKey(name) }.getOrDefault(InputConstants.UNKNOWN)

    /** What the menu shows for a saved key: its name in the game's language, or None. */
    fun label(name: String): Component {
        val key = keyOf(name)
        return if (key == InputConstants.UNKNOWN) Component.literal("None") else key.displayName
    }

    /** Sets the Capsule Helper key ("" = none) and saves it. */
    fun setCapsuleHelper(name: String) {
        Config.s.capsuleHelperKey = name
        Config.save()
        apply(capsuleHelper, name)
    }

    private fun apply(mapping: KeyMapping, name: String) {
        mapping.setKey(keyOf(name))
        KeyMapping.resetMapping()
    }
}
