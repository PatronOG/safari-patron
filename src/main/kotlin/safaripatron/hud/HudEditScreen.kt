package safaripatron.hud

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import safaripatron.config.Config
import kotlin.math.roundToInt

/**
 * /sp hud — move (drag), resize (scroll) and reset (right click) every HUD element.
 * Elements that only show up sometimes (sparkling panel, notifications) are shown as previews.
 */
class HudEditScreen(private val parent: Screen? = null) : Screen(Component.literal("Safari Patron HUD")) {
    private val rects = HashMap<HudElement, IntArray>()
    private var dragging: HudElement? = null
    private var offX = 0.0
    private var offY = 0.0

    private fun IntArray.contains(px: Double, py: Double) = px >= this[0] && px < this[0] + this[2] && py >= this[1] && py < this[1] + this[3]
    private fun hovered(mx: Double, my: Double) = Hud.elements.lastOrNull { rects[it]?.contains(mx, my) == true }

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        super.extractRenderState(g, mx, my, delta)
        g.fill(0, 0, width, height, 0x50000000)

        for (e in Hud.elements) rects[e] = Hud.drawAt(g, font, e, true, width, height)

        val hover = dragging ?: hovered(mx.toDouble(), my.toDouble())
        for (e in Hud.elements) {
            val r = rects[e] ?: continue
            val active = e === hover
            outline(g, r, if (active) 0xFFFFFF55.toInt() else 0x80FFFFFF.toInt())
            val tag = "${e.name} · ${(e.pos.scale * 100).roundToInt()}%"
            val ty = if (r[1] >= 11) r[1] - 10 else r[1] + r[3] + 2
            g.fill(r[0], ty - 1, r[0] + font.width(tag) + 4, ty + 9, 0xA0000000.toInt())
            g.text(font, tag, r[0] + 2, ty, if (active) 0xFFFFFF55.toInt() else 0xFFDDDDDD.toInt(), false)
        }

        val help = "Drag to move · Scroll to resize · Right click to reset · ESC to save"
        g.fill((width - font.width(help)) / 2 - 4, height - 24, (width + font.width(help)) / 2 + 4, height - 12, 0xA0000000.toInt())
        g.text(font, help, (width - font.width(help)) / 2, height - 22, 0xFFFFFFFF.toInt(), true)
    }

    private fun outline(g: GuiGraphicsExtractor, r: IntArray, color: Int) {
        val (x, y, w, h) = r
        g.fill(x - 1, y - 1, x + w + 1, y, color)
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, color)
        g.fill(x - 1, y, x, y + h, color)
        g.fill(x + w, y, x + w + 1, y + h, color)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val e = hovered(event.x(), event.y())
        if (e != null) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                val r = rects[e]!!
                dragging = e
                offX = event.x() - r[0]
                offY = event.y() - r[1]
                return true
            }
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                e.pos.set(e.defaultPos)
                Config.save()
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val e = dragging
        val r = e?.let { rects[it] }
        if (e != null && r != null && width > 0 && height > 0) {
            val nx = (event.x() - offX).coerceIn(0.0, (width - r[2]).toDouble().coerceAtLeast(0.0))
            val ny = (event.y() - offY).coerceIn(0.0, (height - r[3]).toDouble().coerceAtLeast(0.0))
            e.pos.x = (nx / width).toFloat()
            e.pos.y = (ny / height).toFloat()
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging != null) { dragging = null; Config.save(); return true }
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val e = hovered(mouseX, mouseY) ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val step = if (scrollY > 0) 0.1f else if (scrollY < 0) -0.1f else 0f
        e.pos.scale = ((e.pos.scale + step) * 10).roundToInt().div(10f).coerceIn(0.5f, 2f)
        Config.save()
        return true
    }

    override fun isPauseScreen() = false

    override fun onClose() {
        Config.save()
        if (parent != null) minecraft.gui.setScreen(parent) else super.onClose()
    }
}
