package safaripatron.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import safaripatron.Brand
import safaripatron.config.Config

/** Settings: categories on the left, rows on the right, search on top. */
class SettingsScreen(initial: String?, private val parent: Screen?) : Screen(Component.literal("Safari Patron Settings")) {
    private var selected = SettingsModel.byId(initial).takeIf { it in SettingsModel.categories } ?: SettingsModel.categories.first()
    private val hits = ArrayList<Ui.Hit>()
    private lateinit var search: EditBox
    /** Shared text field for Text rows; moved onto the row being edited. */
    private lateinit var textBox: EditBox
    private var editingKey: String? = null
    private var editingSet: ((String) -> Unit)? = null
    private var editingSeen = false
    private var scroll = 0
    private var maxScroll = 0

    // Open dropdown (Multi item) and where its button was drawn this frame
    // Items are rebuilt every frame, so the open dropdown is remembered by name
    private var openKey: String? = null
    /** The key row waiting for a key press ("category:name", since rows are rebuilt every frame) and how to set it. */
    private var listening: String? = null
    private var listeningSet: ((String) -> Unit)? = null
    /** The reset button waiting for its second click, and since when. */
    private var pendingConfirm: String? = null
    private var confirmAt = 0L
    private var armedBefore: String? = null
    private val CONFIRM_MS = 3000L
    /** Width of the − value + control. */
    private val STEPPER_W = 76
    private val KEY_W = 90
    private var openMulti: SettingsModel.Multi? = null
    private var multiAnchor: IntArray? = null
    private var popupRect: IntArray? = null

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
        textBox = EditBox(font, 0, 0, 100, 10, Component.literal("Text"))
        textBox.setBordered(false)
        textBox.setMaxLength(240)
        textBox.visible = false
        textBox.setResponder { v -> editingSet?.invoke(v) }
        addRenderableWidget(textBox)
        editingKey = null
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
        multiAnchor = null
        popupRect = null
        openMulti = null
        editingSeen = false
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
        val note = if (searching) null else selected.note
        if (note != null) {
            val nx = rx + 8 + font.width(heading) + 6
            g.text(font, Ui.fit(font, note, rx + rw - 8 - nx), nx, top + 7, 0xFFFF5555.toInt(), false)
        }
        g.fill(rx + 1, top + 19, rx + rw - 1, top + 20, Ui.DIVIDER)

        val listTop = top + 21
        val listBottom = bottom - 2
        val list = rows()
        // Rows grow by a line when their description needs two
        val heights = list.map { rowHeight(it, rw, searching) }
        maxScroll = (heights.sum() - (listBottom - listTop)).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, maxScroll)

        g.enableScissor(rx + 1, listTop, rx + rw - 1, listBottom)
        var ry = listTop - scroll
        list.forEachIndexed { i, row ->
            val rh = heights[i]
            if (ry + rh >= listTop && ry <= listBottom) {
                drawRow(g, row, rx, ry, rw, rh, mx, my, searching, listTop, listBottom)
                if (i < list.size - 1) g.fill(rx + 8, ry + rh - 1, rx + rw - 8, ry + rh, Ui.DIVIDER)
            }
            ry += rh
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

        // The edited row scrolled away or the category changed
        if (editingKey != null && !editingSeen) stopEditing()
        super.extractRenderState(g, mx, my, delta)
        drawPopup(g, mx, my, top, bottom)
    }

    /** The open dropdown's option list, drawn on top of everything. */
    private fun drawPopup(g: GuiGraphicsExtractor, mx: Int, my: Int, top: Int, bottom: Int) {
        if (openKey == null) return
        val multi = openMulti
        val a = multiAnchor
        if (multi == null || a == null) { openKey = null; return } // scrolled away or category changed
        val optH = 14
        val pw = maxOf(a[2], multi.options.maxOf { font.width(it.label) } + 26)
        val ph = multi.options.size * optH + 4
        val px = a[0] + a[2] - pw
        val py = if (a[1] + a[3] + 1 + ph <= bottom) a[1] + a[3] + 1 else a[1] - ph - 1
        popupRect = intArrayOf(px, py, pw, ph)
        Ui.box(g, px, py, pw, ph, 0xFF10160D.toInt(), Ui.FEATURE_BORDER)
        multi.options.forEachIndexed { i, o ->
            val oy = py + 2 + i * optH
            val on = o.get()
            val hover = Ui.inside(mx, my, px + 1, oy, pw - 2, optH)
            if (hover) g.fill(px + 1, oy, px + pw - 1, oy + optH, Ui.CARD_HOVER)
            // Check box
            Ui.box(g, px + 5, oy + 3, 8, 8, if (on) Ui.SWITCH_ON else 0xFF0F140C.toInt(), if (on) Ui.SWITCH_ON else Ui.BORDER_HOVER)
            if (on) g.text(font, if (multi.single) "●" else "✔", px + 5, oy + 3, Ui.WHITE, false)
            g.text(font, o.label, px + 18, oy + 3, if (on) Ui.WHITE else Ui.DIM, false)
            hits.add(Ui.Hit(px + 1, oy, pw - 2, optH) {
                if (multi.single) { o.set(true); openKey = null } else o.set(!on)
            })
        }
    }

    private fun multiSummary(m: SettingsModel.Multi): String {
        val on = m.options.filter { it.get() }
        if (m.single) return on.firstOrNull()?.label ?: "—"
        return when (on.size) {
            0 -> "None"
            m.options.size -> "All"
            1 -> on[0].label
            else -> "${on.size} selected"
        }
    }

    private fun descOf(row: Row, searching: Boolean) =
        if (searching) "${row.category.title} · ${row.item.desc}" else row.item.desc

    /** Width the description can use, left of the row's switch or button; null = one-line row. */
    private fun descWidth(row: Row, rw: Int): Int? = when (val item = row.item) {
        is SettingsModel.Toggle -> rw - 70
        is SettingsModel.Action -> rw - (font.width(item.label) + 12) - 30
        is SettingsModel.Multi -> rw - maxOf(font.width(multiSummary(item)) + font.width("▼") + 20, 80) - 30
        is SettingsModel.Text -> rw - minOf(150, rw / 2) - 30
        is SettingsModel.Stepper -> rw - STEPPER_W - 30
        is SettingsModel.Key -> rw - KEY_W - 30
        else -> null
    }

    private fun descLines(row: Row, rw: Int, searching: Boolean): List<String> {
        val w = descWidth(row, rw) ?: return emptyList()
        return Ui.wrap(font, descOf(row, searching), w, 3)
    }

    private fun rowHeight(row: Row, rw: Int, searching: Boolean) =
        rowH + (descLines(row, rw, searching).size - 1).coerceAtLeast(0) * 10

    private fun drawRow(
        g: GuiGraphicsExtractor, row: Row, rx: Int, ry: Int, rw: Int, rh: Int, mx: Int, my: Int,
        searching: Boolean, clipTop: Int, clipBottom: Int,
    ) {
        val item = row.item
        val hover = Ui.inside(mx, my, rx + 2, ry, rw - 4, rh) && my in clipTop until clipBottom
        fun hit(action: () -> Unit) {
            val top = maxOf(ry, clipTop); val bot = minOf(ry + rh, clipBottom)
            if (bot > top) hits.add(Ui.Hit(rx + 2, top, rw - 4, bot - top, action))
        }
        if (hover && item !is SettingsModel.Info) Ui.rounded(g, rx + 2, ry, rw - 4, rh - 1, Ui.CARD_HOVER)
        val textW = rw - 70
        val desc = descOf(row, searching)
        /** The description, on up to two lines. */
        fun drawDesc() {
            var dy = ry + 15
            for (line in descLines(row, rw, searching)) { g.text(font, line, rx + 10, dy, Ui.DIM, false); dy += 10 }
        }

        when (item) {
            is SettingsModel.Toggle -> {
                g.text(font, Ui.fit(font, item.name, textW), rx + 10, ry + 5, Ui.WHITE, false)
                drawDesc()
                Ui.switch(g, rx + rw - 34, ry + 8, item.get())
                hit { item.set(!item.get()) }
            }
            is SettingsModel.Action -> {
                g.text(font, Ui.fit(font, item.name, textW), rx + 10, ry + 5, Ui.WHITE, false)
                drawDesc()
                val key = "${row.category.id}:${item.name}"
                if (pendingConfirm == key && System.currentTimeMillis() - confirmAt > CONFIRM_MS) pendingConfirm = null
                val confirming = pendingConfirm == key
                val label = if (confirming) "Confirm?" else item.label
                val bw = font.width(label) + 12
                val bx = rx + rw - bw - 12
                if (confirming) {
                    Ui.box(g, bx, ry + 6, bw, 14, if (hover) 0xFF5A1E1E.toInt() else 0xFF3A1616.toInt(), 0xFFE05555.toInt())
                    g.text(font, label, bx + 6, ry + 9, 0xFFFF8080.toInt(), false)
                } else {
                    Ui.box(g, bx, ry + 6, bw, 14, if (hover) 0xFF26401C.toInt() else Ui.CARD, Ui.FEATURE_BORDER)
                    g.text(font, label, bx + 6, ry + 9, Ui.PILL_ON_TEXT, false)
                }
                hit {
                    val now = System.currentTimeMillis()
                    if (!item.confirm || (armedBefore == key && now - confirmAt <= CONFIRM_MS)) item.run()
                    else { pendingConfirm = key; confirmAt = now }
                }
            }
            is SettingsModel.Prefix -> {
                val current = Brand.Style.of(Config.s.prefixStyle) == item.style
                g.text(font, Brand.prefix(item.style).visualOrderText, rx + 10, ry + 9, Ui.WHITE, false)
                val label = item.style.id
                g.text(font, label, rx + rw - 30 - font.width(label), ry + 9, if (current) Ui.WHITE else Ui.DIM, false)
                if (current) g.text(font, "✔", rx + rw - 22, ry + 9, Ui.GREEN, false)
                hit { Config.s.prefixStyle = item.style.id; Config.save() }
            }
            is SettingsModel.Multi -> {
                val key = "${row.category.id}:${item.name}"
                val open = openKey == key
                if (open) openMulti = item
                val label = multiSummary(item)
                val arrow = if (open) "▲" else "▼"
                val bw = maxOf(font.width(label) + font.width(arrow) + 20, 80)
                val bx = rx + rw - bw - 12
                val tw = rw - bw - 30
                g.text(font, Ui.fit(font, item.name, tw), rx + 10, ry + 5, Ui.WHITE, false)
                drawDesc()
                Ui.box(g, bx, ry + 6, bw, 14, if (open || hover) 0xFF26401C.toInt() else Ui.CARD, if (open) Ui.GREEN else Ui.FEATURE_BORDER)
                g.text(font, label, bx + 6, ry + 9, Ui.PILL_ON_TEXT, false)
                g.text(font, arrow, bx + bw - 6 - font.width(arrow), ry + 9, Ui.PILL_ON_TEXT, false)
                if (open && ry + 6 >= clipTop && ry + 20 <= clipBottom) multiAnchor = intArrayOf(bx, ry + 6, bw, 14)
                hit { openKey = if (open) null else key }
            }
            is SettingsModel.Text -> {
                val key = "${row.category.id}:${item.name}"
                val editing = editingKey == key
                val bw = minOf(150, rw / 2)
                val bx = rx + rw - bw - 12
                val tw = rw - bw - 30
                g.text(font, Ui.fit(font, item.name, tw), rx + 10, ry + 5, Ui.WHITE, false)
                drawDesc()
                Ui.box(g, bx, ry + 6, bw, 14, 0xFF0F140C.toInt(), if (editing) Ui.GREEN else Ui.FEATURE_BORDER)
                val inView = ry + 6 >= clipTop && ry + 20 <= clipBottom
                if (editing && inView) {
                    editingSeen = true
                    textBox.x = bx + 4; textBox.y = ry + 9; textBox.width = bw - 8
                    textBox.visible = true
                } else {
                    val v = item.get()
                    g.text(font, Ui.fit(font, v.ifEmpty { "Click to type" }, bw - 10), bx + 5, ry + 9,
                        if (v.isEmpty()) Ui.FAINT else Ui.WHITE, false)
                    hit { startEditing(key, item) }
                }
            }
            is SettingsModel.Stepper -> {
                g.text(font, Ui.fit(font, item.name, rw - STEPPER_W - 30), rx + 10, ry + 5, Ui.WHITE, false)
                drawDesc()
                val v = item.get().coerceIn(item.min, item.max)
                val bx = rx + rw - STEPPER_W - 12
                val by = ry + 6
                val inView = by >= clipTop && by + 14 <= clipBottom
                /** One of the − / + buttons; dimmed at the end of the range. */
                fun button(x: Int, label: String, enabled: Boolean, delta: Int) {
                    val over = enabled && Ui.inside(mx, my, x, by, 14, 14)
                    Ui.box(g, x, by, 14, 14, if (over) 0xFF26401C.toInt() else Ui.CARD, if (enabled) Ui.FEATURE_BORDER else Ui.FAINT)
                    g.text(font, label, x + 7 - font.width(label) / 2, by + 3, if (enabled) Ui.PILL_ON_TEXT else Ui.FAINT, false)
                    if (enabled && inView) hits.add(Ui.Hit(x, by, 14, 14) { item.set((v + delta).coerceIn(item.min, item.max)) })
                }
                val vw = STEPPER_W - 32
                Ui.box(g, bx + 16, by, vw, 14, 0xFF0F140C.toInt(), Ui.FEATURE_BORDER)
                val label = item.format(v)
                g.text(font, label, bx + 16 + (vw - font.width(label)) / 2, by + 3, Ui.WHITE, false)
                button(bx, "-", v > item.min, -1)
                button(bx + STEPPER_W - 14, "+", v < item.max, +1)
            }
            is SettingsModel.Key -> {
                g.text(font, Ui.fit(font, item.name, rw - KEY_W - 30), rx + 10, ry + 5, Ui.WHITE, false)
                drawDesc()
                val key = "${row.category.id}:${item.name}"
                val waiting = listening == key
                val bx = rx + rw - KEY_W - 12
                Ui.box(g, bx, ry + 6, KEY_W, 14, if (waiting || hover) 0xFF26401C.toInt() else Ui.CARD, if (waiting) Ui.GREEN else Ui.FEATURE_BORDER)
                val label = if (waiting) "Press a key..." else Ui.fit(font, safaripatron.input.Keys.label(item.get()).string, KEY_W - 10)
                g.text(font, label, bx + (KEY_W - font.width(label)) / 2, ry + 9, if (waiting) Ui.WHITE else Ui.PILL_ON_TEXT, false)
                hit { if (waiting) stopListening() else { listening = key; listeningSet = item.set } }
            }
            is SettingsModel.Info -> {
                g.text(font, item.name, rx + 10, ry + 9, 0xFFCFDAC6.toInt(), false)
                val d = Ui.fit(font, desc, rw - 30 - font.width(item.name))
                g.text(font, d, rx + rw - 12 - font.width(d), ry + 9, Ui.DIM, false)
            }
        }
    }

    private fun startEditing(key: String, item: SettingsModel.Text) {
        editingKey = key
        editingSet = null           // don't save while the old value is loaded
        textBox.value = item.get()
        editingSet = item.set
        textBox.visible = true
        setFocused(textBox)
        textBox.isFocused = true
    }

    private fun stopEditing() {
        editingKey = null
        editingSet = null
        textBox.isFocused = false
        textBox.visible = false
        if (focused === textBox) setFocused(null)
    }

    private fun stopListening() { listening = null; listeningSet = null }

    override fun keyPressed(event: KeyEvent): Boolean {
        val set = listeningSet ?: return super.keyPressed(event)
        stopListening()
        // Esc clears the key; any other key becomes it
        set(if (event.key() == InputConstants.KEY_ESCAPE) "" else InputConstants.getKey(event).name)
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        listeningSet?.let { set ->
            // A side, middle or right mouse button becomes the key; a left click stops waiting
            stopListening()
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
                set(InputConstants.Type.MOUSE.getOrCreate(event.button()).name)
                return true
            }
            return true
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && editingKey != null) {
            // Clicks inside the field go to the field (cursor placement); anywhere else ends editing
            if (textBox.isMouseOver(event.x(), event.y())) return super.mouseClicked(event, doubleClick)
            stopEditing()
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            // Any click disarms a waiting reset; only a second click on that same button runs it
            armedBefore = pendingConfirm
            pendingConfirm = null
            // A click outside the open dropdown closes it
            val pr = popupRect
            val a = multiAnchor
            if (openKey != null && pr != null) {
                val inPopup = event.x() >= pr[0] && event.x() < pr[0] + pr[2] && event.y() >= pr[1] && event.y() < pr[1] + pr[3]
                val onButton = a != null && event.x() >= a[0] && event.x() < a[0] + a[2] && event.y() >= a[1] && event.y() < a[1] + a[3]
                if (!inPopup && !onButton) { openKey = null; return true }
            }
            hits.lastOrNull { it.contains(event.x(), event.y()) }?.let { it.action(); return true }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        openKey = null
        pendingConfirm = null
        scroll = (scroll - (scrollY * rowH).toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun isPauseScreen() = false

    override fun onClose() {
        Config.save()
        if (parent != null) minecraft.gui.setScreen(parent) else super.onClose()
    }
}
