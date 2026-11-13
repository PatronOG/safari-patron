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
 * Elements that only show up sometimes (sparkling, warp check, announcements) are shown as previews.
 * Center guides: panels snap to the middle of the screen and the guide line lights up.
 */
class HudEditScreen(private val parent: Screen? = null) : Screen(Component.literal("Safari Patron HUD")) {
    private val rects = HashMap<HudElement, IntArray>()
    private var dragging: HudElement? = null
    private var offX = 0.0
    private var offY = 0.0
    private var snapX = false
    private var snapY = false

    private fun IntArray.contains(px: Double, py: Double) = px >= this[0] && px < this[0] + this[2] && py >= this[1] && py < this[1] + this[3]
    private fun hovered(mx: Double, my: Double) = Hud.elements.lastOrNull { rects[it]?.contains(mx, my) == true }

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        super.extractRenderState(g, mx, my, delta)
        Hud.ensureLayout()
        g.fill(0, 0, width, height, 0x50000000)

        // Center guides: faint normally, green while a dragged panel is snapped to them
        val cx = width / 2
        val cy = height / 2
        val drag = dragging
        g.fill(cx, 0, cx + 1, height, if (drag != null && snapX) 0xFF55FF55.toInt() else 0x30FFFFFF)
        g.fill(0, cy, width, cy + 1, if (drag != null && snapY) 0xFF55FF55.toInt() else 0x30FFFFFF)

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

        // Position readout of the dragged panel's center
        val dr = drag?.let { rects[it] }
        if (dr != null) {
            val px = dr[0] + dr[2] / 2
            val py = dr[1] + dr[3] / 2
            val info = "Center  x: $px / $width" + (if (snapX) " ✔" else "") + "   y: $py / $height" + (if (snapY) " ✔" else "")
            val iw = font.width(info)
            g.fill((width - iw) / 2 - 4, height - 38, (width + iw) / 2 + 4, height - 26, 0xA0000000.toInt())
            g.text(font, info, (width - iw) / 2, height - 36, if (snapX || snapY) 0xFF55FF55.toInt() else 0xFFFFFFFF.toInt(), true)
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
                e.pos.set(Hud.defaultPos(e))
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
            var nx = (event.x() - offX).coerceIn(0.0, (width - r[2]).toDouble().coerceAtLeast(0.0))
            var ny = (event.y() - offY).coerceIn(0.0, (height - r[3]).toDouble().coerceAtLeast(0.0))
            // Snap the panel's center to the screen center (within 4 px)
            snapX = kotlin.math.abs(nx + r[2] / 2.0 - width / 2.0) <= SNAP
            snapY = kotlin.math.abs(ny + r[3] / 2.0 - height / 2.0) <= SNAP
            if (snapX) nx = width / 2.0 - r[2] / 2.0
            if (snapY) ny = height / 2.0 - r[3] / 2.0
            e.pos.x = if (e.centered) ((nx + r[2] / 2.0) / width).toFloat() else (nx / width).toFloat()
            e.pos.y = (ny / height).toFloat()
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging != null) { dragging = null; snapX = false; snapY = false; Config.save(); return true }
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

    private companion object { const val SNAP = 4.0 }

    override fun onClose() {
        Config.save()
        if (parent != null) minecraft.gui.setScreen(parent) else super.onClose()
    }
}
