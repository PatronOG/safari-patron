package safaripatron.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import safaripatron.data.Biome
import safaripatron.data.Critters
import safaripatron.history.History
import safaripatron.history.RunRecord
import safaripatron.profit.Profit
import safaripatron.safari.Run
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** /sp history — every saved run: summary cards, filters, the list and the selected run's details. */
class HistoryScreen(private val parent: Screen? = null) : Screen(Component.literal("Safari Patron Run History")) {
    private enum class Filter(val label: String) { ALL("All"), SPARKLING("✦ Sparkling"), PB("★ PB"), DYE("♦ Dye"), TODAY("Today"), WEEK("This week") }
    private enum class Sort(val label: String) { NEWEST("Newest"), FASTEST("Fastest"), PROFIT("Most profit") }

    private val hits = ArrayList<Ui.Hit>()
    private var filter = Filter.ALL
    private var sort = Sort.NEWEST
    private var scroll = 0
    private var selectedId = -1
    private var listRect = intArrayOf(0, 0, 0, 0)
    private var maxScroll = 0
    /** The details panel scrolls on its own when the run has more than fits. */
    private var detailScroll = 0
    private var detailMax = 0
    private var detailRect = intArrayOf(0, 0, 0, 0)
    private var detailRunId = -1
    /** Last try at pricing runs saved with a total of 0 (prices arrive a few seconds after joining). */
    private var lastFix = 0L

    private val rowH = 12
    private val zone = ZoneId.systemDefault()
    private val whenFmt = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH)

    private fun runs(): List<RunRecord> {
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays(6)
        val all = History.data.runs.filter { r ->
            val day by lazy { Instant.ofEpochMilli(r.endedAt).atZone(zone).toLocalDate() }
            when (filter) {
                Filter.ALL -> true
                Filter.SPARKLING -> r.sparklings.isNotEmpty()
                Filter.PB -> r.pb
                Filter.DYE -> (r.items[safaripatron.profit.Bazaar.DYE] ?: 0L) > 0
                Filter.TODAY -> day == today
                Filter.WEEK -> !day.isBefore(weekStart)
            }
        }
        return when (sort) {
            Sort.NEWEST -> all.sortedByDescending { it.id }
            Sort.FASTEST -> all.filter { it.ms > 0 }.sortedBy { it.ms } + all.filter { it.ms <= 0 }.sortedByDescending { it.id }
            Sort.PROFIT -> all.sortedByDescending { it.value }
        }
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        super.extractRenderState(g, mx, my, delta)
        hits.clear()
        val now = System.currentTimeMillis()
        if (now - lastFix > 3000) { lastFix = now; History.fixZeroValues() }
        val w = minOf(width - 16, 640)
        val h = minOf(height - 12, 340)
        val x0 = (width - w) / 2
        val y0 = ((height - h) / 2).coerceAtLeast(4)
        Ui.rounded(g, x0, y0, w, h, Ui.WINDOW)

        // Header: title, tabs, close
        val ix = x0 + 6
        val iw = w - 12
        var y = y0 + 6
        Ui.box(g, ix, y, iw, 20, Ui.HEADER, Ui.HEADER_BORDER)
        Ui.text(g, font, "✦ Run History", ix + 6, y + 6, Ui.GREEN)
        var tx = ix + 12 + font.width("✦ Run History") + 10
        for ((label, soon) in listOf("History" to false, "Stats" to true, "Achievements" to true)) {
            val tw = font.width(label)
            g.text(font, label, tx, y + 6, if (soon) Ui.FAINT else Ui.WHITE, false)
            if (!soon) g.fill(tx, y + 16, tx + tw, y + 17, Ui.GREEN)
            tx += tw + if (soon) { g.text(font, "soon", tx + tw + 3, y + 6, 0xFF4F5C48.toInt(), false); font.width("soon") + 13 } else 10
        }
        val closeHover = Ui.inside(mx, my, ix + iw - 16, y + 3, 14, 14)
        g.text(font, "✕", ix + iw - 12, y + 6, if (closeHover) Ui.WHITE else Ui.FAINT, false)
        hits.add(Ui.Hit(ix + iw - 16, y + 3, 14, 14) { onClose() })
        y += 24

        // Summary cards
        val all = History.data.runs
        val timed = all.filter { it.ms > 0 }
        val fastest = timed.minByOrNull { it.ms }
        val sparklings = all.sumOf { it.sparklings.size }
        val cards = listOf(
            Triple("Runs", "${all.size}", Ui.TEXT),
            Triple("Played", Profit.duration(timed.sumOf { it.ms }), Ui.TEXT),
            Triple("Total Profit", Profit.coins(all.sumOf { it.value }), Ui.GOLD),
            Triple("Fastest Run", fastest?.let { Run.time(it.ms) } ?: "—", Ui.GREEN),
            Triple("Sparklings", "$sparklings", Ui.GOLD),
        )
        val subs = listOf(null, null, null, fastest?.let { "Run #${it.id}" },
            if (sparklings > 0) "1 per ${String.format(Locale.ROOT, "%.1f", all.size.toDouble() / sparklings)} runs" else null)
        val cw = (iw - 4 * 4) / 5
        cards.forEachIndexed { i, (label, value, color) ->
            val cx = ix + i * (cw + 4)
            Ui.box(g, cx, y, cw, 28, Ui.CARD, Ui.BORDER)
            g.text(font, Ui.fit(font, label, cw - 8), cx + 4, y + 3, Ui.DIM, false)
            Ui.text(g, font, Ui.fit(font, value, ((cw - 8) / 1.2f).toInt()), cx + 4, y + 13, color, 1.2f)
            subs[i]?.let { s -> Ui.text(g, font, Ui.fit(font, s, ((cw - 8) / 0.75f).toInt()), cx + cw - 4 - (font.width(s) * 0.75f).toInt().coerceAtMost(cw - 8), y + 4, Ui.FAINT, 0.75f) }
        }
        y += 32

        // Filters and sort
        var fx = ix
        for (f in Filter.entries) {
            val fw = font.width(f.label) + 10
            val on = f == filter
            val hover = Ui.inside(mx, my, fx, y, fw, 13)
            Ui.box(g, fx, y, fw, 13, if (on) Ui.PILL_ON else if (hover) Ui.CARD_HOVER else Ui.CARD, if (on) Ui.FEATURE_BORDER else Ui.BORDER)
            g.text(font, f.label, fx + 5, y + 3, if (on) Ui.PILL_ON_TEXT else Ui.MUTED, false)
            hits.add(Ui.Hit(fx, y, fw, 13) { filter = f; scroll = 0 })
            fx += fw + 3
        }
        val sortLabel = "Sort: ${sort.label} ▾"
        val sw = font.width(sortLabel) + 10
        val sx = ix + iw - sw
        val sortHover = Ui.inside(mx, my, sx, y, sw, 13)
        Ui.box(g, sx, y, sw, 13, if (sortHover) Ui.CARD_HOVER else Ui.CARD, Ui.FEATURE_BORDER)
        g.text(font, sortLabel, sx + 5, y + 3, Ui.TEXT, false)
        hits.add(Ui.Hit(sx, y, sw, 13) { sort = Sort.entries[(sort.ordinal + 1) % Sort.entries.size]; scroll = 0 })
        y += 17

        // Body: list (left) and details (right)
        val bodyBottom = y0 + h - 6
        val detailW = 180
        val lw = iw - detailW - 4
        drawList(g, mx, my, ix, y, lw, bodyBottom - y)
        drawDetails(g, ix + lw + 4, y, detailW, bodyBottom - y)
    }

    // Column layout of the list: x offsets inside a row, from the widest text each column can hold
    private var cols = IntArray(9)
    private val headers = listOf("Run", "When", "Time", "Biomes", "You", "Party", "Caught", "Profit", "")
    private val widest = listOf("#0000", "30 Nov 23:59", "59:59", "", "37/37", "37/37", "000", "999.9M", "✦★♦")

    /** Columns share the list's width: each as wide as its widest text, the space left spread between them. */
    private fun layoutColumns(listW: Int) {
        val widths = headers.indices.map { i ->
            val content = if (i == 3) 4 * 9 - 2 else font.width(widest[i])
            maxOf(font.width(headers[i]), content)
        }
        val gaps = headers.size - 1
        val gap = ((listW - 10 - widths.sum()) / gaps).coerceIn(8, 28)
        var x = 6
        for (i in headers.indices) {
            cols[i] = x
            x += widths[i] + gap
        }
    }

    private fun drawList(g: GuiGraphicsExtractor, mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) {
        Ui.box(g, x, y, w, h, 0xFF161C12.toInt(), Ui.DIVIDER)
        layoutColumns(w)
        headers.forEachIndexed { i, t -> if (cols[i] < w - 8) g.text(font, t, x + cols[i], y + 3, Ui.DIM, false) }
        g.fill(x + 1, y + 13, x + w - 1, y + 14, Ui.DIVIDER)

        val list = runs()
        val top = y + 15
        val bottom = y + h - 13
        listRect = intArrayOf(x, top, w, bottom - top)
        maxScroll = (list.size * rowH - (bottom - top)).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, maxScroll)
        if (selectedId < 0 || list.none { it.id == selectedId }) selectedId = list.firstOrNull()?.id ?: -1

        g.enableScissor(x + 1, top, x + w - 1, bottom)
        list.forEachIndexed { i, r ->
            val ry = top + i * rowH - scroll
            if (ry + rowH < top || ry > bottom) return@forEachIndexed
            val sel = r.id == selectedId
            val hover = Ui.inside(mx, my, x + 1, ry, w - 2, rowH) && my in top until bottom
            if (sel || hover) g.fill(x + 1, ry, x + w - 1, ry + rowH, if (sel) Ui.SELECTED else Ui.CARD_HOVER)
            val ty = ry + 2
            fun col(i: Int, s: String, c: Int) { if (cols[i] < w - 8) g.text(font, s, x + cols[i], ty, c, false) }
            col(0, "#${r.id}", Ui.MUTED)
            col(1, whenFmt.format(Instant.ofEpochMilli(r.endedAt).atZone(zone)), Ui.MUTED)
            col(2, if (r.ms > 0) Run.time(r.ms) else "—", if (r.pb) Ui.GOLD else Ui.TEXT)
            if (cols[3] < w - 8) {
                val done = doneBiomes(r)
                Biome.entries.forEachIndexed { b, biome ->
                    val bx = x + cols[3] + b * 9
                    g.fill(bx, ty + 2, bx + 7, ty + 5, if (biome in done) biome.color else 0xFF2A3324.toInt())
                }
            }
            col(4, "${r.mine}/${Critters.TOTAL}", 0xFF8FE8E8.toInt())
            col(5, "${r.party}/${Critters.TOTAL}", Ui.TEXT)
            col(6, "${r.caught}", Ui.TEXT)
            col(7, Profit.coins(r.value), Ui.GOLD)
            if (cols[8] < w - 8) {
                var mx2 = x + cols[8]
                if (r.sparklings.isNotEmpty()) { g.text(font, "✦", mx2, ty, Ui.GOLD, false); mx2 += font.width("✦") + 1 }
                if (r.pb) { g.text(font, "★", mx2, ty, Ui.GREEN, false); mx2 += font.width("★") + 1 }
                if ((r.items[safaripatron.profit.Bazaar.DYE] ?: 0L) > 0) g.text(font, "♦", mx2, ty, 0xFF55AA55.toInt(), false)
            }
            hits.add(Ui.Hit(x + 1, maxOf(ry, top), w - 2, minOf(ry + rowH, bottom) - maxOf(ry, top)) { selectedId = r.id })
        }
        g.disableScissor()
        if (list.isEmpty()) {
            val msg = if (History.data.runs.isEmpty()) "No runs yet. Finish a run to see it here." else "No runs match this filter."
            g.text(font, msg, x + (w - font.width(msg)) / 2, top + 20, Ui.DIM, false)
        }
        // Scroll bar
        if (maxScroll > 0) {
            val trackH = bottom - top
            val barH = (trackH * trackH / (trackH + maxScroll)).coerceAtLeast(10)
            val barY = top + (trackH - barH) * scroll / maxScroll
            g.fill(x + w - 3, barY, x + w - 1, barY + barH, Ui.BORDER_HOVER)
        }
        g.fill(x + 1, bottom, x + w - 1, bottom + 1, Ui.DIVIDER)
        val shown = if (list.isEmpty()) "0 runs" else "${list.size} run${if (list.size == 1) "" else "s"}"
        g.text(font, shown, x + 4, bottom + 3, Ui.FAINT, false)
        val hint = "Click a run for details"
        g.text(font, hint, x + w - 4 - font.width(hint), bottom + 3, Ui.FAINT, false)
    }

    private fun doneBiomes(r: RunRecord): Set<Biome> {
        val caught = r.players.values.flatten().toHashSet()
        return Biome.entries.filter { b -> b.name in r.biomeMs || b.critters.all { it.name in caught } }.toSet()
    }

    private fun drawDetails(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        Ui.box(g, x, y, w, h, Ui.CARD, Ui.BORDER)
        val r = History.data.runs.firstOrNull { it.id == selectedId }
        if (r == null) {
            g.text(font, "Select a run", x + 6, y + 6, Ui.DIM, false)
            return
        }
        if (r.id != detailRunId) { detailRunId = r.id; detailScroll = 0 }
        detailRect = intArrayOf(x, y, w, h)
        val ix = x + 6
        val right = x + w - 8
        val top = y + 2
        val bottom = y + h - 2
        val start = y + 5 - detailScroll
        var cy = start
        g.enableScissor(x + 1, top, x + w - 1, bottom)
        fun visible() = cy + 9 > top && cy < bottom
        fun line(left: String, lc: Int, value: String = "", vc: Int = Ui.TEXT) {
            if (visible()) {
                g.text(font, Ui.fit(font, left, right - ix - 4 - font.width(value)), ix, cy, lc, false)
                if (value.isNotEmpty()) g.text(font, value, right - font.width(value), cy, vc, false)
            }
            cy += 10
        }
        fun divider(title: String) {
            cy += 2
            if (cy in top until bottom) g.fill(ix, cy, right, cy + 1, Ui.DIVIDER)
            cy += 3
            line(title, Ui.DIM)
        }

        if (visible()) Ui.text(g, font, "Run #${r.id}", ix, cy, Ui.WHITE, 1.2f)
        cy += 13
        line(dayFmt.format(Instant.ofEpochMilli(r.endedAt).atZone(zone)), Ui.DIM)
        line(if (r.ms > 0) Run.time(r.ms) + (if (r.pb) "  Fastest!" else "") else "Not timed", if (r.pb) Ui.GREEN else Ui.TEXT,
            Profit.coins(r.value), Ui.GOLD)
        r.hotspot?.let { line("Hotspot: $it", Ui.MUTED) }

        divider("Biomes")
        val keys = Biome.entries.map { it.name to it }
        for ((key, biome) in keys) {
            val t = r.biomeMs[key]
            line(biome.label, biome.color, t?.let { Run.time(it) } ?: "—", if (t != null) Ui.TEXT else Ui.FAINT)
        }
        r.biomeMs["all"]?.let { line("Everything", Ui.GOLD, Run.time(it)) }
            ?: r.biomeMs["allButMacaw"]?.let { line("All but Macaw", Ui.GOLD, Run.time(it)) }

        if (r.players.isNotEmpty()) {
            divider("Party · uniques")
            val sorted = r.players.entries.sortedWith(compareBy<Map.Entry<String, MutableList<String>>>({ if (it.key == r.self) 0 else 1 }, { -it.value.size }))
            for ((name, list) in sorted) line(name, if (name == r.self) 0xFF55FFFF.toInt() else Ui.TEXT, "${list.size}", 0xFF8FE8E8.toInt())
        }

        if (r.sparklings.isNotEmpty()) {
            divider("Sparklings")
            for (s in r.sparklings) line("✦ ${s.critter}", Ui.GOLD, s.player, Ui.MUTED)
        }

        if (r.items.isNotEmpty()) {
            divider("Loot")
            val loot = r.items.entries.sortedByDescending { (id, n) -> Profit.itemValue(id, n) }
            for ((id, n) in loot) {
                val name = "${"%,d".format(Locale.ROOT, n)}x ${Profit.itemName(id)}"
                line(name, legacyColor(Profit.itemColor(id)), Profit.coins(Profit.itemValue(id, n)), Ui.GOLD)
            }
        }
        g.disableScissor()

        // Scroll: how far the content runs past the panel, and a bar when it does
        val contentH = cy - start + 4
        val viewH = bottom - top
        detailMax = (contentH - viewH).coerceAtLeast(0)
        detailScroll = detailScroll.coerceIn(0, detailMax)
        if (detailMax > 0) {
            val barH = (viewH * viewH / (viewH + detailMax)).coerceAtLeast(10)
            val barY = top + (viewH - barH) * detailScroll / detailMax
            g.fill(x + w - 4, barY, x + w - 2, barY + barH, Ui.BORDER_HOVER)
        }
    }

    /** §a -> its RGB, for drawing a color code as a plain color. */
    private fun legacyColor(code: String): Int = when (code.lastOrNull()) {
        'a' -> 0xFF55FF55.toInt(); '9' -> 0xFF5555FF.toInt(); '5' -> 0xFFAA00AA.toInt(); '6' -> 0xFFFFAA00.toInt()
        'd' -> 0xFFFF55FF.toInt(); '2' -> 0xFF00AA00.toInt(); else -> Ui.TEXT
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            hits.lastOrNull { it.contains(event.x(), event.y()) }?.let { it.action(); return true }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val d = detailRect
        if (mouseX >= d[0] && mouseX < d[0] + d[2] && mouseY >= d[1] && mouseY < d[1] + d[3]) {
            detailScroll = (detailScroll - (scrollY * 20).toInt()).coerceIn(0, detailMax)
            return true
        }
        scroll = (scroll - (scrollY * rowH * 3).toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun isPauseScreen() = false

    override fun onClose() {
        if (parent != null) minecraft.gui.setScreen(parent) else super.onClose()
    }
}
