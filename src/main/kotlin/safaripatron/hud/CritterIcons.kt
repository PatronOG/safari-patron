// CritterIcons.kt
// Module: Bundled critter sprites for the biome and collection lists
// Build: Kotlin 2.4.20 / Minecraft 26.2 / Fabric 0.19.5

package safaripatron.hud

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import safaripatron.config.Config
import safaripatron.data.Critters
import java.nio.ByteBuffer
import kotlin.math.roundToInt

internal object CritterIcons {
    const val SIZE = 12
    val advance get() = if (Config.s.critterListIcons) SIZE + 3 else 0
    val rowHeight get() = if (Config.s.critterListIcons) SIZE + 2 else 10
    private data class Icon(val texture: Identifier, val width: Int, val height: Int)
    private val icons by lazy {
        Critters.all.associate { critter ->
            val path = "textures/critters/${critter.name.lowercase().replace(' ', '_')}.png"
            val header = requireNotNull(javaClass.getResourceAsStream("/assets/safari-patron/$path")).use { it.readNBytes(24) }
            require(header.size == 24)
            val width = ByteBuffer.wrap(header, 16, 8).int
            val height = ByteBuffer.wrap(header, 20, 4).int
            require(width in 1..256 && height in 1..256)
            critter.name to Icon(Identifier.fromNamespaceAndPath("safari-patron", path), width, height)
        }
    }

    fun draw(g: GuiGraphicsExtractor, name: String, x: Int, y: Int) {
        if (!Config.s.critterListIcons) return
        val icon = icons[name] ?: return
        val scale = SIZE.toDouble() / maxOf(icon.width, icon.height)
        val width = (icon.width * scale).roundToInt().coerceAtLeast(1)
        val height = (icon.height * scale).roundToInt().coerceAtLeast(1)
        g.blit(RenderPipelines.GUI_TEXTURED, icon.texture, x + (SIZE - width) / 2, y + (SIZE - height) / 2,
            0f, 0f, width, height, icon.width, icon.height, icon.width, icon.height)
    }
}
