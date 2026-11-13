package safaripatron.visual

import net.minecraft.client.Minecraft
import safaripatron.config.Config
import safaripatron.safari.Safari

/**
 * Clear water in the Safari: no underwater fog or blue overlay, and see-through water surfaces.
 * Ported from Sparkling Mutuals (CC0-1.0). Chunk-building threads only read the volatile snapshot.
 */
object ClearWater {
    @Volatile var active = false
        private set
    @Volatile private var opacity = 30

    /** Water surface color with the chosen opacity (only while active). */
    @JvmStatic
    fun surfaceColor(color: Int, water: Boolean): Int {
        if (!water || !active) return color
        val alpha = (opacity.coerceIn(0, 100) * 255 + 50) / 100
        return (minOf(color ushr 24, alpha) shl 24) or (color and 0xFFFFFF)
    }

    fun tick(client: Minecraft) {
        val next = Config.s.clearWater && Safari.inSafari
        val nextOpacity = Config.s.clearWaterOpacity
        val changed = next != active || (next && nextOpacity != opacity)
        opacity = nextOpacity
        active = next
        // Transparency is baked into chunk meshes: rebuild once when it changes
        val level = client.level
        if (changed && level != null) {
            client.levelRenderer.invalidateCompiledGeometry(level, client.options, client.gameRenderer.mainCamera(), client.blockColors)
        }
    }

    fun reset() { active = false }
}
