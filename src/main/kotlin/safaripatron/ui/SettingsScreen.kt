package safaripatron.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import safaripatron.Brand
import safaripatron.config.Config

/** Settings: categories on the left, rows on the right, search on top. */
class SettingsScreen(initial: String?, private val parent: Screen?) : Screen(Component.literal("Safari Patron Settings")) {
    private var selected = SettingsModel.byId(initial)
    private val hits = ArrayList<Ui.Hit>()
    private lateinit var search: EditBox
    private var scroll = 0
    private var maxScroll = 0

    // Layout (computed in init and each frame)
    private var x0 = 0; private var y0 = 0; private var w = 0; private var h = 0

    private val navW = 96
    private val topH = 24
    private val rowH = 26

    private fun layout() {
        w = minOf(width - 20, 430)
        h = minOf(height - 16, 270)
        x0 = (width - w) / 2
        y0 = ((height - h) / 2).coerceAtLeast(4)
    }

    override fun init() {
        layout()
        val old = if (::search.isInitialized) search.value else ""
        // Sits inside the search box drawn in extractRenderState (box at x0 + w - 128, y0 + 5, 120 x 16)
        search = EditBox(font, x0 + w - 122, y0 + 9, 112, 10, Component.literal("Search settings"))
        search.setBordered(false)
        search.setMaxLength(60)
        search.value = old
        search.setResponder { scroll = 0 }
        addRenderableWidget(search)
    }

    private class Row(val item: SettingsModel.Item, val category: SettingsModel.Category)

    private fun rows(): List<Row> {
        val q = search.value.trim()
        if (q.isEmpty()) return selected.items().map { Row(it, selected) }
        return SettingsModel.categories.flatMap { c ->
            c.items().filter { it.name.contains(q, true) || it.desc.contains(q, true) || c.title.contains(q, true) }
                .map { Row(it, c) }
        }
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        layout()
        hits.clear()
        Ui.rounded(g, x0, y0, w, h, Ui.WINDOW)

        // Top bar: back, title, search
        val backHover = Ui.inside(mx, my, x0 + 6, y0 + 6, 40, 14)
        g.text(font, "← Back", x0 + 8, y0 + 9, if (backHover) Ui.WHITE else Ui.GREEN, false)
        hits.add(Ui.Hit(x0 + 6, y0 + 6, 40, 14) { onClose() })
        Ui.text(g, font, "Settings", x0 + 54, y0 + 7, Ui.WHITE, 1.2f)
        val sx = x0 + w - 128
        Ui.box(g, sx, y0 + 5, 120, 16, 0xFF0F140C.toInt(), Ui.BORDER)
        if (search.value.isEmpty() && !search.isFocused) g.text(font, "Search settings", sx + 6, y0 + 9, Ui.FAINT, false)

        // Left: categories
        val top = y0 + topH + 4
        val bottom = y0 + h - 8
        val nx = x0 + 8
        Ui.box(g, nx, top, navW, bottom - top, Ui.CARD, Ui.BORDER)
        val searching = search.value.isNotBlank()
        SettingsModel.categories.forEachIndexed { i, c ->
            val cy = top + 4 + i * 17
            val isSel = !searching && c === selected
            val hover = Ui.inside(mx, my, nx + 3, cy, navW - 6, 15)
            if (isSel || hover) Ui.rounded(g, nx + 3, cy, navW - 6, 15, if (isSel) Ui.SELECTED else Ui.CARD_HOVER)
            g.text(font, c.icon, nx + 8, cy + 4, c.iconColor, false)
            g.text(font, Ui.fit(font, c.title, navW - 30), nx + 20, cy + 4, if (isSel) Ui.WHITE else Ui.MUTED, false)
            hits.add(Ui.Hit(nx + 3, cy, navW - 6, 15) { selected = c; scroll = 0; search.value = "" })
        }

        // Right: rows
        val rx = nx + navW + 6
        val rw = x0 + w - 8 - rx
        Ui.box(g, rx, top, rw, bottom - top, Ui.CARD, Ui.BORDER)
        val heading = if (searching) "Results for \"${search.value.trim()}\"" else selected.title
        g.text(font, Ui.fit(font, heading, rw - 16), rx + 8, top + 7, Ui.GREEN, false)
        g.fill(rx + 1, top + 19, rx + rw - 1, top + 20, Ui.DIVIDER)

        val listTop = top + 21
        val listBottom = bottom - 2
        val list = rows()
        maxScroll = (list.size * rowH - (listBottom - listTop)).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, maxScroll)

        g.enableScissor(rx + 1, listTop, rx + rw - 1, listBottom)
        list.forEachIndexed { i, row ->
            val ry = listTop + i * rowH - scroll
            if (ry + rowH < listTop || ry > listBottom) return@forEachIndexed
            drawRow(g, row, rx, ry, rw, mx, my, searching, listTop, listBottom)
            if (i < list.size - 1) g.fill(rx + 8, ry + rowH - 1, rx + rw - 8, ry + rowH, Ui.DIVIDER)
        }
        g.disableScissor()
        if (list.isEmpty()) g.text(font, "No settings found", rx + 8, listTop + 8, Ui.DIM, false)

        // Scroll bar
        if (maxScroll > 0) {
            val trackH = listBottom - listTop
            val barH = (trackH * trackH / (trackH + maxScroll)).coerceAtLeast(12)
            val barY = listTop + (trackH - barH) * scroll / maxScroll
            g.fill(rx + rw - 4, barY, rx + rw - 2, barY + barH, Ui.BORDER_HOVER)
        }

        super.extractRenderState(g, mx, my, delta)
    }

    private fun drawRow(
        g: GuiGraphicsExtractor, row: Row, rx: Int, ry: Int, rw: Int, mx: Int, my: Int,
        searching: Boolean, clipTop: Int, clipBottom: Int,
    ) {
        val item = row.item
        val hover = Ui.inside(mx, my, rx + 2, ry, rw - 4, rowH) && my in clipTop until clipBottom
        fun hit(action: () -> Unit) {
            val top = maxOf(ry, clipTop); val bot = minOf(ry + rowH, clipBottom)
            if (bot > top) hits.add(Ui.Hit(rx + 2, top, rw - 4, bot - top, action))
        }
        if (hover && item !is SettingsModel.Info) Ui.rounded(g, rx + 2, ry, rw - 4, rowH - 1, Ui.CARD_HOVER)
        val textW = rw - 70
        val desc = if (searching) "${row.category.title} · ${item.desc}" else item.desc

        when (item) {
            is SettingsModel.Toggle -> {
                g.text(font, Ui.fit(font, item.name, textW), rx + 10, ry + 5, Ui.WHITE, false)
                g.text(font, Ui.fit(font, desc, textW), rx + 10, ry + 15, Ui.DIM, false)
                Ui.switch(g, rx + rw - 34, ry + 8, item.get())
                hit { item.set(!item.get()) }
            }
            is SettingsModel.Action -> {
                g.text(font, Ui.fit(font, item.name, textW), rx + 10, ry + 5, Ui.WHITE, false)
                g.text(font, Ui.fit(font, desc, textW), rx + 10, ry + 15, Ui.DIM, false)
                val bw = font.width(item.label) + 12
                val bx = rx + rw - bw - 12
                Ui.box(g, bx, ry + 6, bw, 14, if (hover) 0xFF26401C.toInt() else Ui.CARD, Ui.FEATURE_BORDER)
                g.text(font, item.label, bx + 6, ry + 9, Ui.PILL_ON_TEXT, false)
                hit { item.run() }
            }
            is SettingsModel.Prefix -> {
                val current = Brand.Style.of(Config.s.prefixStyle) == item.style
                g.text(font, Brand.prefix(item.style).visualOrderText, rx + 10, ry + 9, Ui.WHITE, false)
                val label = item.style.id
                g.text(font, label, rx + rw - 30 - font.width(label), ry + 9, if (current) Ui.WHITE else Ui.DIM, false)
                if (current) g.text(font, "✔", rx + rw - 22, ry + 9, Ui.GREEN, false)
                hit { Config.s.prefixStyle = item.style.id; Config.save() }
            }
            is SettingsModel.Info -> {
                g.text(font, item.name, rx + 10, ry + 9, 0xFFCFDAC6.toInt(), false)
                val d = Ui.fit(font, desc, rw - 30 - font.width(item.name))
                g.text(font, d, rx + rw - 12 - font.width(d), ry + 9, Ui.DIM, false)
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            hits.lastOrNull { it.contains(event.x(), event.y()) }?.let { it.action(); return true }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scroll = (scroll - (scrollY * rowH).toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun isPauseScreen() = false

    override fun onClose() {
        Config.save()
        if (parent != null) minecraft.gui.setScreen(parent) else super.onClose()
    }
}
