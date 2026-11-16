package safaripatron.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import safaripatron.data.Critter
import safaripatron.data.Critters
import safaripatron.esp.BoxTuner
import safaripatron.esp.CritterId
import java.util.Locale

/**
 * /sp boxes: set the box width and height of every critter (ESP and Capsule Helper).
 * The panel sits on the right so the critter stays visible on the left; changes show at once.
 */
class BoxScreen : Screen(Component.literal("Critter boxes")) {
    private val hits = ArrayList<Ui.Hit>()
    private var selected: Critter? = null
    private var target: Entity? = null
    private var scroll = 0
    private var maxScroll = 0
    private var note: String? = null
    private var noteUntil = 0L
    private var resetConfirmUntil = 0L

    override fun init() {
        super.init()
        if (selected == null) pickTarget()
    }

    /** The critter under the crosshair, or the nearest one within 16 blocks. */
    private fun pickTarget() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        val looked = mc.crosshairPickEntity?.takeIf { CritterId.identify(it) != null }
        val e = looked ?: level.entitiesForRendering()
            .filter { it !== player && it.distanceToSqr(player) < 16.0 * 16.0 && CritterId.identify(it) != null }
            .minByOrNull { it.distanceToSqr(player) }
        if (e != null) {
            target = e
            selected = CritterId.identify(e)
        } else flash("No critter nearby")
    }

    private fun flash(s: String) { note = s; noteUntil = System.currentTimeMillis() + 2500 }

    private fun sizeOf(c: Critter): BoxTuner.Size = BoxTuner.current(c)

    private fun change(c: Critter, dw: Double, dh: Double) {
        val s = sizeOf(c)
        val w = ((s.w + dw) * 100).let { Math.round(it) / 100.0 }.coerceIn(0.05, 5.0)
        val h = ((s.h + dh) * 100).let { Math.round(it) / 100.0 }.coerceIn(0.05, 5.0)
        BoxTuner.set(c, BoxTuner.Size(w, h))
    }

    private fun f(v: Double) = String.format(Locale.ROOT, "%.2f", v)

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        super.extractRenderState(g, mx, my, delta)
        hits.clear()
        val pw = 230
        val x0 = width - pw - 6
        val y0 = 6
        val ph = height - 12
        Ui.rounded(g, x0, y0, pw, ph, Ui.WINDOW)
        val ix = x0 + 8
        val iw = pw - 16
        var y = y0 + 8

        g.text(font, "Critter boxes", ix, y, Ui.GOLD, true)
        val closeX = ix + iw - 8
        g.text(font, "✕", closeX, y, if (Ui.inside(mx, my, closeX - 2, y - 2, 12, 12)) Ui.WHITE else Ui.FAINT, false)
        hits.add(Ui.Hit(closeX - 2, y - 2, 12, 12) { onClose() })
        y += 14

        // Where the sizes are used
        fun toggle(label: String, on: Boolean, set: (Boolean) -> Unit) {
            g.text(font, label, ix, y + 2, Ui.TEXT, false)
            Ui.switch(g, ix + iw - 22, y + 1, on)
            hits.add(Ui.Hit(ix, y, iw, 12) { set(!on) })
            y += 14
        }
        toggle("Use for ESP boxes", BoxTuner.data.forEsp) { BoxTuner.setUse(esp = it) }
        toggle("Use for Capsule Helper hits", BoxTuner.data.forCapsule) { BoxTuner.setUse(capsule = it) }
        y += 2

        fun button(label: String, bx: Int, by: Int, bw: Int, color: Int = Ui.TEXT, action: () -> Unit) {
            val hover = Ui.inside(mx, my, bx, by, bw, 13)
            Ui.box(g, bx, by, bw, 13, if (hover) Ui.CARD_HOVER else Ui.CARD, if (hover) Ui.BORDER_HOVER else Ui.BORDER)
            g.text(font, label, bx + (bw - font.width(label)) / 2, by + 3, color, false)
            hits.add(Ui.Hit(bx, by, bw, 13, action))
        }

        // Selected critter and its size
        button("◎ Pick the critter I look at", ix, y, iw) { pickTarget() }
        y += 17
        val c = selected
        if (c == null) {
            g.text(font, "Select a critter below", ix, y, Ui.DIM, false)
            y += 14
        } else {
            g.text(font, c.name, ix, y, c.biome.color, true)
            val tuned = BoxTuner.get(c) != null
            val tag = if (tuned) "custom" else "default"
            g.text(font, tag, ix + iw - font.width(tag), y, if (tuned) Ui.GREEN else Ui.FAINT, false)
            y += 13
            val s = sizeOf(c)
            fun row(label: String, value: Double, dw: Double, dh: Double) {
                g.text(font, label, ix, y + 3, Ui.DIM, false)
                val bx = ix + 50
                button("−−", bx, y, 22) { change(c, -dw * 5, -dh * 5) }
                button("−", bx + 24, y, 18) { change(c, -dw, -dh) }
                val v = f(value)
                g.text(font, v, bx + 66 - font.width(v) / 2, y + 3, Ui.WHITE, true)
                button("+", bx + 90, y, 18) { change(c, dw, dh) }
                button("++", bx + 110, y, 22) { change(c, dw * 5, dh * 5) }
                y += 16
            }
            row("Width", s.w, 0.02, 0.0)
            row("Height", s.h, 0.0, 0.02)
            g.text(font, "− / + : 0.02 · −− / ++ : 0.10", ix, y, Ui.FAINT, false)
            y += 12
            button("Reset to default", ix, y, iw, Ui.MUTED) { BoxTuner.reset(c) }
            y += 17
        }

        // Reset all (asks once more)
        val confirming = System.currentTimeMillis() < resetConfirmUntil
        button(if (confirming) "Confirm?" else "Reset all to default", ix, y, iw, if (confirming) 0xFFFF5555.toInt() else Ui.MUTED) {
            if (System.currentTimeMillis() < resetConfirmUntil) { BoxTuner.resetAll(); resetConfirmUntil = 0L; flash("Every critter is back to default") }
            else resetConfirmUntil = System.currentTimeMillis() + 3000
        }
        y += 17
        if (note != null && System.currentTimeMillis() < noteUntil) g.text(font, note!!, ix, y, Ui.GREEN, false)
        y += 12

        // Every critter, scrolling
        val top = y
        val bottom = y0 + ph - 8
        val rowH = 11
        maxScroll = (Critters.all.size * rowH - (bottom - top)).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, maxScroll)
        g.enableScissor(ix, top, ix + iw, bottom)
        Critters.all.forEachIndexed { i, cr ->
            val ry = top + i * rowH - scroll
            if (ry + rowH < top || ry > bottom) return@forEachIndexed
            val on = cr == selected
            val hover = Ui.inside(mx, my, ix, ry, iw, rowH) && my in top until bottom
            if (on || hover) g.fill(ix, ry, ix + iw, ry + rowH, if (on) Ui.SELECTED else Ui.CARD_HOVER)
            g.text(font, Ui.fit(font, cr.name, iw - 80), ix + 3, ry + 2, cr.biome.color, false)
            // Changed sizes in white, defaults in gray
            val s = BoxTuner.get(cr)
            val d = s ?: BoxTuner.defaultOf(cr)
            val v = "${f(d.w)} × ${f(d.h)}"
            g.text(font, v, ix + iw - 3 - font.width(v), ry + 2, if (s != null) Ui.WHITE else Ui.DIM, false)
            if (ry >= top && ry + rowH <= bottom) hits.add(Ui.Hit(ix, ry, iw, rowH) { selected = cr })
        }
        g.disableScissor()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            hits.lastOrNull { it.contains(event.x(), event.y()) }?.let { it.action(); return true }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scroll = (scroll - (scrollY * 22).toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun isPauseScreen() = false
}
