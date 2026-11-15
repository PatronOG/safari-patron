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
import safaripatron.achievements.Achievement
import safaripatron.achievements.Achievements
import safaripatron.achievements.Tier
import safaripatron.history.Totals
import safaripatron.profit.Profit
import safaripatron.safari.Run
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** /sp history — every saved run: summary cards, filters, the list and the selected run's details. */
class HistoryScreen(private val parent: Screen? = null, start: String = "history") : Screen(Component.literal("Safari Patron Run History")) {
    private enum class Filter(val label: String) { ALL("All"), SPARKLING("✦ Sparkling"), PB("★ PB"), DYE("♦ Dye"), TODAY("Today"), WEEK("This week") }
    private enum class Sort(val label: String) { NEWEST("Newest"), FASTEST("Fastest"), PROFIT("Most profit") }
    private enum class Tab { HISTORY, STATS, ACHIEVEMENTS }
    private enum class Range(val label: String) { ALL("All time"), WEEK("This week"), TODAY("Today"), LAST50("Last 50 runs") }

    private var tab = when (start) { "stats" -> Tab.STATS; "achievements" -> Tab.ACHIEVEMENTS; else -> Tab.HISTORY }
    private var range = Range.ALL

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

    /** The list for the current filter and sort; worked out again only when one of them, the day or the history changes. */
    private var listCache: List<RunRecord> = emptyList()
    private var listKey: Any? = null

    private fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun runs(): List<RunRecord> {
        val today = LocalDate.now(zone)
        // Today / This week need every run of those days loaded
        when (filter) {
            Filter.TODAY -> History.ensureSince(startOf(today))
            Filter.WEEK -> History.ensureSince(startOf(today.minusDays(6)))
            else -> {}
        }
        val key = listOf(filter, sort, today, History.version)
        if (key == listKey) return listCache
        listKey = key
        listCache = computeRuns(today)
        return listCache
    }

    private fun computeRuns(today: LocalDate): List<RunRecord> {
        val weekStart = today.minusDays(6)
        val all = History.runs.filter { r ->
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
        val w = minOf(width - 16, 760)
        val h = minOf(height - 12, 360)
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
        for ((label, target) in listOf<Pair<String, Tab?>>("History" to Tab.HISTORY, "Stats" to Tab.STATS, "Achievements" to Tab.ACHIEVEMENTS)) {
            val tw = font.width(label)
            val on = target == tab
            val hover = target != null && Ui.inside(mx, my, tx - 2, y + 2, tw + 4, 16)
            g.text(font, label, tx, y + 6, when { on -> Ui.WHITE; target == null -> Ui.FAINT; hover -> Ui.TEXT; else -> Ui.MUTED }, false)
            if (on) g.fill(tx, y + 16, tx + tw, y + 17, Ui.GREEN)
            if (target != null) hits.add(Ui.Hit(tx - 2, y + 2, tw + 4, 16) { tab = target })
            tx += tw + if (target == null) { g.text(font, "soon", tx + tw + 3, y + 6, 0xFF4F5C48.toInt(), false); font.width("soon") + 13 } else 10
        }
        val closeHover = Ui.inside(mx, my, ix + iw - 16, y + 3, 14, 14)
        g.text(font, "✕", ix + iw - 12, y + 6, if (closeHover) Ui.WHITE else Ui.FAINT, false)
        hits.add(Ui.Hit(ix + iw - 16, y + 3, 14, 14) { onClose() })
        y += 24
        if (tab == Tab.STATS) { drawStats(g, mx, my, ix, y, iw, y0 + h - 6); return }
        if (tab == Tab.ACHIEVEMENTS) { drawAchievements(g, mx, my, ix, y, iw, y0 + h - 6); return }

        // Summary cards
        // All time, from the saved totals (older runs do not have to be loaded)
        val t = History.totals
        val sparklings = t.sparklings
        val cards = listOf(
            Triple("Runs", "${t.runs}", Ui.TEXT),
            Triple("Played", Profit.duration(t.sumMs), Ui.TEXT),
            Triple("Total Profit", Profit.coins(t.sumValue), Ui.GOLD),
            Triple("Fastest Run", if (t.fastestMs > 0) Run.time(t.fastestMs) else "—", Ui.GREEN),
            Triple("Sparklings", "$sparklings", Ui.GOLD),
        )
        val subs = listOf(null, null, null, if (t.fastestMs > 0) "Run #${t.fastestId}" else null,
            if (sparklings > 0) "1 per ${String.format(Locale.ROOT, "%.1f", t.runs.toDouble() / sparklings)} runs" else null)
        val cw = (iw - 4 * 4) / 5
        cards.forEachIndexed { i, (label, value, color) ->
            val cx = ix + i * (cw + 4)
            Ui.box(g, cx, y, cw, 28, Ui.CARD, Ui.BORDER)
            g.text(font, Ui.fit(font, label, cw - 8), cx + 5, y + 5, Ui.DIM, false)
            val shownValue = Ui.fit(font, value, cw - 10)
            g.text(font, shownValue, cx + 5, y + 16, color, true)
            // Small note on the right, only when it fits beside the value (full size: scaled text gets blurry)
            subs[i]?.let { sub -> if (font.width(shownValue) + font.width(sub) + 14 <= cw) g.text(font, sub, cx + cw - 5 - font.width(sub), y + 16, Ui.FAINT, false) }
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
        val more = History.moreToLoad
        val rows = list.size + if (more) 1 else 0
        maxScroll = (rows * rowH - (bottom - top)).coerceAtLeast(0)
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
        // Last row: load the next older file of runs
        if (more) {
            val ry = top + list.size * rowH - scroll
            if (ry + rowH >= top && ry <= bottom) {
                val hover = Ui.inside(mx, my, x + 1, ry, w - 2, rowH) && my in top until bottom
                if (hover) g.fill(x + 1, ry, x + w - 1, ry + rowH, Ui.CARD_HOVER)
                val label = "Load older runs (${"%,d".format(Locale.ROOT, History.olderCount)} more) ›"
                g.text(font, label, x + (w - font.width(label)) / 2, ry + 2, if (hover) Ui.WHITE else Ui.GREEN, false)
                hits.add(Ui.Hit(x + 1, maxOf(ry, top), w - 2, minOf(ry + rowH, bottom) - maxOf(ry, top)) { History.loadOlder() })
            }
        }
        g.disableScissor()
        if (list.isEmpty()) {
            val msg = if (History.totalRuns == 0) "No runs yet. Finish a run to see it here." else "No loaded runs match this filter."
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
        val shown = (if (list.isEmpty()) "0 runs" else "${list.size} run${if (list.size == 1) "" else "s"}") +
            if (more) " · latest ${"%,d".format(Locale.ROOT, History.runs.size)} of ${"%,d".format(Locale.ROOT, History.totalRuns)} loaded" else ""
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
        val r = History.byId(selectedId)
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

        if (visible()) Ui.text(g, font, "Run #${r.id}", ix, cy, Ui.WHITE, 1f, true)
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

    // ---------------- Stats tab ----------------

    /** The range's runs (loaded, oldest first: for the graphs and "Last 10"), its totals and the last 10's totals. */
    private class StatsView(val runs: List<RunRecord>, val totals: Totals, val last10: Totals)
    private var statsCache: StatsView? = null
    private var statsKey: Any? = null

    private fun stats(): StatsView {
        val today = LocalDate.now(zone)
        when (range) {
            Range.WEEK -> History.ensureSince(startOf(today.minusDays(6)))
            Range.TODAY -> History.ensureSince(startOf(today))
            else -> History.ensureCount(50)
        }
        val key = listOf(range, today, History.version)
        statsCache?.let { if (key == statsKey) return it }
        val runs = History.runs
        fun day(r: RunRecord) = Instant.ofEpochMilli(r.endedAt).atZone(zone).toLocalDate()
        val list = when (range) {
            Range.ALL -> ArrayList(runs)
            Range.WEEK -> runs.filter { !day(it).isBefore(today.minusDays(6)) }
            Range.TODAY -> runs.filter { day(it) == today }
            Range.LAST50 -> runs.takeLast(50)
        }
        // All time comes from the saved totals, so it counts the runs that are not loaded too
        val totals = if (range == Range.ALL) History.totals else Totals.of(list)
        val view = StatsView(list, totals, Totals.of(list.takeLast(10)))
        statsKey = key
        statsCache = view
        return view
    }

    private var tooltip: String? = null

    private fun drawStats(g: GuiGraphicsExtractor, mx: Int, my: Int, ix: Int, top: Int, iw: Int, bottom: Int) {
        tooltip = null
        val view = stats()
        val runs = view.runs
        val t = view.totals
        var y = top

        // Range chips and a summary
        var fx = ix
        for (r in Range.entries) {
            val fw = font.width(r.label) + 10
            val on = r == range
            val hover = Ui.inside(mx, my, fx, y, fw, 13)
            Ui.box(g, fx, y, fw, 13, if (on) Ui.PILL_ON else if (hover) Ui.CARD_HOVER else Ui.CARD, if (on) Ui.FEATURE_BORDER else Ui.BORDER)
            g.text(font, r.label, fx + 5, y + 3, if (on) Ui.PILL_ON_TEXT else Ui.MUTED, false)
            hits.add(Ui.Hit(fx, y, fw, 13) { range = r })
            fx += fw + 3
        }
        val summary = "${t.runs} run${if (t.runs == 1) "" else "s"} · ${Profit.duration(t.sumMs)} played"
        g.text(font, summary, ix + iw - font.width(summary), y + 3, Ui.DIM, false)
        y += 17

        if (t.runs == 0) {
            val msg = if (History.totalRuns == 0) "No runs yet. Finish a run to see your stats." else "No runs in this range."
            g.text(font, msg, ix + (iw - font.width(msg)) / 2, y + 40, Ui.DIM, false)
            return
        }

        val leftW = (iw * 0.55f).toInt()
        val rightX = ix + leftW + 6
        val rightW = iw - leftW - 6
        drawCompare(g, view, ix, y, leftW)
        val graphH = ((bottom - y - 38 - 8) / 2).coerceAtLeast(50)
        drawTimeGraph(g, runs, ix, y + 38, leftW, graphH)
        drawProfitGraph(g, runs, ix, y + 38 + graphH + 4, leftW, bottom - (y + 38 + graphH + 4))

        var ry = y
        ry = drawClear(g, mx, my, t, rightX, ry, rightW) + 4
        ry = drawSparklings(g, t, rightX, ry, rightW) + 4
        ry = drawRecords(g, mx, my, t, rightX, ry, rightW, bottom) + 4
        drawPlayedWith(g, t, rightX, ry, rightW, bottom)

        tooltip?.let { t ->
            val tw = font.width(t) + 8
            val tx = (mx + 8).coerceAtMost(width - tw - 2)
            val ty = (my - 14).coerceAtLeast(2)
            Ui.box(g, tx, ty, tw, 13, 0xF0100A14.toInt(), Ui.FEATURE_BORDER)
            g.text(font, t, tx + 4, ty + 3, Ui.TEXT, false)
        }
    }

    private fun card(g: GuiGraphicsExtractor, title: String, x: Int, y: Int, w: Int, h: Int) {
        Ui.box(g, x, y, w, h, Ui.CARD, Ui.BORDER)
        g.text(font, title, x + 6, y + 4, Ui.WHITE, false)
    }

    /** Last 10 runs against the whole range: run time, profit, your uniques, Everything cleared. */
    private fun drawCompare(g: GuiGraphicsExtractor, view: StatsView, x: Int, y: Int, w: Int) {
        Ui.box(g, x, y, w, 34, Ui.CARD, Ui.BORDER)
        g.text(font, "Last 10 runs", x + 6, y + 4, Ui.DIM, false)
        g.text(font, "vs ${range.label.lowercase()}", x + 10 + font.width("Last 10 runs"), y + 4, Ui.FAINT, false)
        val last = view.last10
        val all = view.totals
        val items = listOf(
            // label, value now, change, lower is better, format
            Quad("Run time", last.averageMs, all.averageMs, true) { v: Double -> Run.time(v.toLong()) },
            Quad("Profit/run", last.averageValue, all.averageValue, false) { v: Double -> Profit.coins(v) },
            Quad("Your uniques", last.averageMine, all.averageMine, false) { v: Double -> String.format(Locale.ROOT, "%.1f", v) },
            Quad("Cleared", last.clearedPercent, all.clearedPercent, false) { v: Double -> "${v.toInt()}%" },
        )
        val cw = (w - 12) / items.size
        items.forEachIndexed { i, q ->
            val cx = x + 6 + i * cw
            Ui.text(g, font, q.label, cx, y + 15, Ui.FAINT, 0.75f)
            if (q.now.isNaN()) { g.text(font, "—", cx, y + 23, Ui.DIM, false); return@forEachIndexed }
            val value = q.fmt(q.now)
            g.text(font, value, cx, y + 23, Ui.TEXT, false)
            if (!q.base.isNaN() && all.runs > last.runs) {
                val diff = q.now - q.base
                if (kotlin.math.abs(diff) > 1e-9) {
                    val better = if (q.lowerBetter) diff < 0 else diff > 0
                    val arrow = if (diff < 0) "▼" else "▲"
                    val d = q.fmt(kotlin.math.abs(diff))
                    Ui.text(g, font, "$arrow $d", cx + font.width(value) + 3, y + 24, if (better) Ui.GREEN else 0xFFE05555.toInt(), 0.75f)
                }
            }
        }
    }

    private class Quad(val label: String, val now: Double, val base: Double, val lowerBetter: Boolean, val fmt: (Double) -> String)

    /** Run time of the last 30 timed runs, with the average as a dashed line. */
    private fun drawTimeGraph(g: GuiGraphicsExtractor, runs: List<RunRecord>, x: Int, y: Int, w: Int, h: Int) {
        card(g, "Run time · last 30 runs", x, y, w, h)
        val pts = runs.filter { it.ms > 0 }.takeLast(30)
        if (pts.size < 2) { g.text(font, "Not enough timed runs yet", x + 6, y + 20, Ui.FAINT, false); return }
        val gx = x + 30; val gy = y + 15; val gw = w - 38; val gh = h - 26
        val lo = pts.minOf { it.ms } * 0.95; val hi = pts.maxOf { it.ms } * 1.05
        fun px(i: Int) = gx + gw * i / (pts.size - 1)
        fun py(v: Double) = gy + gh - ((v - lo) / (hi - lo) * gh).toInt()
        for (k in 0..2) {
            val v = lo + (hi - lo) * k / 2
            val yy = py(v)
            g.fill(gx, yy, gx + gw, yy + 1, 0xFF26301F.toInt())
            Ui.text(g, font, Run.time(v.toLong()), x + 4, yy - 3, Ui.FAINT, 0.75f)
        }
        val avg = pts.map { it.ms }.average()
        val ay = py(avg)
        var dx = gx
        while (dx < gx + gw) { g.fill(dx, ay, minOf(dx + 3, gx + gw), ay + 1, Ui.GOLD); dx += 6 }
        Ui.text(g, font, "avg ${Run.time(avg.toLong())}", gx + gw - (font.width("avg ${Run.time(avg.toLong())}") * 0.75f).toInt(), ay - 7, Ui.GOLD, 0.75f)
        for (i in 0 until pts.size - 1) line(g, px(i), py(pts[i].ms.toDouble()), px(i + 1), py(pts[i + 1].ms.toDouble()), Ui.GREEN)
        pts.forEachIndexed { i, r -> val c = if (r.pb) Ui.GOLD else Ui.GREEN; g.fill(px(i) - 1, py(r.ms.toDouble()) - 1, px(i) + 2, py(r.ms.toDouble()) + 2, c) }
        Ui.text(g, font, "#${pts.first().id}", gx, y + h - 8, Ui.FAINT, 0.75f)
        val lastLabel = "#${pts.last().id}"
        Ui.text(g, font, lastLabel, gx + gw - (font.width(lastLabel) * 0.75f).toInt(), y + h - 8, Ui.FAINT, 0.75f)
    }

    /** A 1-pixel line from fills. */
    private fun line(g: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        val steps = maxOf(kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1), 1)
        for (s in 0..steps) {
            val xx = x1 + (x2 - x1) * s / steps
            val yy = y1 + (y2 - y1) * s / steps
            g.fill(xx, yy, xx + 1, yy + 1, color)
        }
    }

    /** Profit of the last 30 runs; sparkling and Emerald Dye runs coloured, very high bars cut with their value on top. */
    private fun drawProfitGraph(g: GuiGraphicsExtractor, runs: List<RunRecord>, x: Int, y: Int, w: Int, h: Int) {
        card(g, "Profit per run · last 30 runs", x, y, w, h)
        val pts = runs.takeLast(30)
        if (pts.isEmpty()) return
        val gx = x + 30; val gy = y + 15; val gw = w - 38; val gh = h - 34
        val sorted = pts.map { it.value }.sorted()
        val median = sorted[sorted.size / 2]
        val cap = maxOf(median * 3, sorted.first().coerceAtLeast(1.0), 1.0)
        for (k in 0..2) {
            val v = cap * k / 2
            val yy = gy + gh - (gh * k / 2)
            g.fill(gx, yy, gx + gw, yy + 1, 0xFF26301F.toInt())
            Ui.text(g, font, Profit.coins(v), x + 4, yy - 3, Ui.FAINT, 0.75f)
        }
        val slot = gw.toFloat() / 30
        val bw = (slot - 2).toInt().coerceAtLeast(1)
        pts.forEachIndexed { i, r ->
            val dye = (r.items[safaripatron.profit.Bazaar.DYE] ?: 0L) > 0
            val color = when { dye -> 0xFF3FA34D.toInt(); r.sparklings.isNotEmpty() -> Ui.GOLD; else -> 0xFF6E8F5A.toInt() }
            val bh = (gh * (r.value / cap).coerceAtMost(1.0)).toInt().coerceAtLeast(1)
            val bx = gx + (slot * (30 - pts.size + i)).toInt() + 1
            g.fill(bx, gy + gh - bh, bx + bw, gy + gh, color)
            if (r.value > cap) Ui.text(g, font, Profit.coins(r.value), bx - 4, gy - 1, 0xFF55AA55.toInt(), 0.6f)
        }
        // Legend
        var lx = x + 6
        val ly = y + h - 10
        for ((c, t) in listOf(0xFF6E8F5A.toInt() to "Run", Ui.GOLD to "✦ Sparkling", 0xFF3FA34D.toInt() to "♦ Dye")) {
            g.fill(lx, ly + 1, lx + 5, ly + 6, c)
            Ui.text(g, font, t, lx + 7, ly, Ui.DIM, 0.75f)
            lx += 12 + (font.width(t) * 0.75f).toInt() + 8
        }
    }

    /** Average and best time per biome, and how often it was cleared. Returns the bottom. */
    private fun drawClear(g: GuiGraphicsExtractor, mx: Int, my: Int, t: Totals, x: Int, y: Int, w: Int): Int {
        val rows = Biome.entries.map { Triple(it.label, it.color, it.name) } + Triple("Everything", Ui.GOLD, "all")
        val h = 26 + rows.size * 10 + 4
        card(g, "Clear Stats", x, y, w, h)
        val cAvg = x + w - 100; val cBest = x + w - 62; val cClr = x + w - 8
        fun right(t: String, rx: Int, ry: Int, c: Int) = g.text(font, t, rx - font.width(t), ry, c, false)
        right("Avg", cAvg, y + 15, Ui.FAINT); right("Best", cBest, y + 15, Ui.FAINT); right("Cleared", cClr, y + 15, Ui.FAINT)
        if (Ui.inside(mx, my, cClr - font.width("Cleared"), y + 14, font.width("Cleared"), 10)) tooltip = "Runs where the biome was fully caught"
        rows.forEachIndexed { i, (label, color, key) ->
            val ry = y + 26 + i * 10
            if (key == "all") g.fill(x + 6, ry - 1, x + w - 6, ry, Ui.DIVIDER)
            g.text(font, label, x + 6, ry, color, false)
            val b = t.biome(key)
            right(if (b.timed > 0) Run.time(b.average) else "—", cAvg, ry, Ui.TEXT)
            right(if (b.best > 0) Run.time(b.best) else "—", cBest, ry, Ui.GREEN)
            val cleared = b.cleared
            val pct = "${cleared * 100 / t.runs}%"
            right(pct, cClr, ry, Ui.DIM)
            if (Ui.inside(mx, my, cClr - font.width(pct), ry - 1, font.width(pct), 10))
                tooltip = if (key == "all") "Runs where all ${Critters.TOTAL} critters were caught ($cleared/${t.runs})"
                    else "Runs where every $label critter was caught ($cleared/${t.runs})"
        }
        return y + h
    }

    private fun drawSparklings(g: GuiGraphicsExtractor, t: Totals, x: Int, y: Int, w: Int): Int {
        val h = 16 + 4 * 10 + 4
        card(g, "Sparklings", x, y, w, h)
        val count = t.sparklings
        val yours = t.yourSparklings
        val topBiome = t.sparklingBiomes.entries.maxByOrNull { it.value }
            ?.let { e -> Biome.entries.firstOrNull { it.name == e.key }?.let { it to e.value } }
        val current = t.dry
        val longest = t.longestDry
        fun kv(i: Int, k: String, v: String, c: Int, sub: String? = null) {
            val ry = y + 16 + i * 10
            g.text(font, k, x + 6, ry, Ui.DIM, false)
            val subW = sub?.let { (font.width(it) * 0.75f).toInt() + 4 } ?: 0
            g.text(font, v, x + w - 6 - subW - font.width(v), ry, c, false)
            sub?.let { Ui.text(g, font, it, x + w - 6 - subW + 4, ry + 1, Ui.FAINT, 0.75f) }
        }
        kv(0, "Sparklings", "$count", Ui.GOLD,
            if (count > 0) "1 per ${String.format(Locale.ROOT, "%.1f", t.runs.toDouble() / count)} runs" else null)
        kv(1, "Yours / party", "$yours / ${count - yours}", Ui.TEXT)
        kv(2, "Top biome", topBiome?.let { "${it.first.label} · ${it.second}" } ?: "—", topBiome?.first?.color ?: Ui.DIM)
        kv(3, "Dry streak", "$current runs", Ui.TEXT, "longest $longest")
        return y + h
    }

    private var playedKey: Totals? = null
    private var playedVersion = -1
    private var playedTop: List<safaripatron.history.PlayerTotals> = emptyList()

    /** The players you ran with most in the range: runs together and the sparklings they caught in them. */
    private fun drawPlayedWith(g: GuiGraphicsExtractor, t: Totals, x: Int, y: Int, w: Int, bottom: Int) {
        val h = bottom - y
        if (h < 26) return
        card(g, "Played With", x, y, w, h)
        if (t.players.isEmpty()) { g.text(font, "No party runs yet", x + 6, y + 16, Ui.FAINT, false); return }
        val max = ((h - 18) / 10).coerceAtLeast(1)
        if (playedKey !== t || playedVersion != History.version) {
            playedKey = t; playedVersion = History.version
            playedTop = t.players.values.sortedByDescending { it.runs }.take(40)
        }
        playedTop.take(max).forEachIndexed { i, p ->
            val ry = y + 16 + i * 10
            val count = p.runs
            val spark = p.sparklings
            val sparkText = "✦ $spark"
            val runsText = "$count run${if (count == 1) "" else "s"} · "
            val right = x + w - 6
            g.text(font, sparkText, right - font.width(sparkText), ry, if (spark > 0) Ui.GOLD else Ui.FAINT, false)
            g.text(font, runsText, right - font.width(sparkText) - font.width(runsText), ry, Ui.DIM, false)
            val nameW = w - 12 - font.width(sparkText) - font.width(runsText) - 4
            g.text(font, Ui.fit(font, p.name, nameW), x + 6, ry, Ui.TEXT, false)
        }
    }

    private fun drawRecords(g: GuiGraphicsExtractor, mx: Int, my: Int, t: Totals, x: Int, y: Int, w: Int, bottom: Int): Int {
        val h = (16 + 4 * 11 + 2).coerceAtMost(bottom - y)
        card(g, "Records", x, y, w, h)
        val busiest = t.days.entries.maxByOrNull { it.value.runs }
        val busiestDay = busiest?.key?.toLongOrNull()?.let { LocalDate.ofEpochDay(it) }
        /** [run] is the id the row opens (0 = none). */
        data class Row(val label: String, val value: String, val color: Int, val sub: String?, val link: String, val run: Int)
        val rows = listOf(
            Row("Fastest run", if (t.fastestMs > 0) Run.time(t.fastestMs) else "—", Ui.GREEN, null,
                if (t.fastestMs > 0) "#${t.fastestId}" else "", if (t.fastestMs > 0) t.fastestId else 0),
            Row("Most profit", if (t.richestId > 0) Profit.coins(t.richestValue) else "—", Ui.GOLD, null,
                if (t.richestId > 0) "#${t.richestId}" else "", t.richestId),
            Row("Most sparklings", if (t.sparklyCount > 0) "✦ ${t.sparklyCount}" else "—", Ui.GOLD,
                if (t.sparklyCount > 0) "(${t.sparklyYours} yours)" else null, if (t.sparklyCount > 0) "#${t.sparklyId}" else "",
                if (t.sparklyCount > 0) t.sparklyId else 0),
            Row("Runs in a day", busiest?.value?.runs?.toString() ?: "—", Ui.TEXT, null,
                busiestDay?.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) ?: "", busiest?.value?.lastId ?: 0),
        )
        rows.forEachIndexed { i, row ->
            val ry = y + 16 + i * 11
            if (ry + 9 > y + h) return@forEachIndexed
            val linkW = font.width(row.link + " ›")
            val hover = row.run > 0 && Ui.inside(mx, my, x + 2, ry - 1, w - 4, 11)
            if (hover) g.fill(x + 2, ry - 1, x + w - 2, ry + 10, Ui.CARD_HOVER)
            g.text(font, row.label, x + 6, ry, Ui.DIM, false)
            var vx = x + w - 6 - linkW - 6
            row.sub?.let { s -> val sw = (font.width(s) * 0.75f).toInt(); Ui.text(g, font, s, vx - sw, ry + 1, Ui.FAINT, 0.75f); vx -= sw + 3 }
            g.text(font, row.value, vx - font.width(row.value), ry, row.color, false)
            if (row.link.isNotEmpty()) g.text(font, row.link + " ›", x + w - 6 - linkW, ry, if (hover) Ui.WHITE else Ui.FAINT, false)
            if (row.run > 0) hits.add(Ui.Hit(x + 2, ry - 1, w - 4, 11) { openRun(row.run) })
        }
        return y + h
    }

    /** Shows a run in the History tab: older files loaded if needed, filters cleared so it is in the list. */
    private fun openRun(id: Int) {
        History.ensureId(id)
        tab = Tab.HISTORY
        filter = Filter.ALL
        sort = Sort.NEWEST
        selectedId = id
        val index = runs().indexOfFirst { it.id == id }
        scroll = (index * rowH - rowH * 3).coerceAtLeast(0)
    }

    // ---------- Achievements tab ----------

    private enum class AchSort(val label: String) { RARITY("Rarity"), PROGRESS("Progress"), RECENT("Recent") }
    private var achTier: Tier? = null
    private var achSort = AchSort.RARITY
    private var achScroll = 0
    private var achMax = 0
    private val achDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** 0..1 progress of a locked achievement (for sorting and its bar), null = no bar. */
    private fun achProgress(a: Achievement): Double? {
        val p = runCatching { a.progress?.invoke() }.getOrNull() ?: return null
        if (p.second <= 0) return null
        return (p.first / p.second).coerceIn(0.0, 1.0)
    }

    private fun achLabel(a: Achievement): String? {
        runCatching { a.progressText?.invoke() }.getOrNull()?.let { return it }
        val p = runCatching { a.progress?.invoke() }.getOrNull() ?: return null
        fun n(v: Double) = if (p.second >= 1_000_000) Profit.coins(v) else "%,d".format(Locale.ROOT, v.toLong())
        return "${n(minOf(p.first, p.second))} / ${n(p.second)}"
    }

    private fun drawAchievements(g: GuiGraphicsExtractor, mx: Int, my: Int, ix: Int, top: Int, iw: Int, bottom: Int) {
        tooltip = null
        val all = Achievements.all
        var y = top

        // Points and unlocked
        val pts = Achievements.points
        val maxPts = Achievements.maxPoints
        val unlocked = all.count { Achievements.isUnlocked(it.id) }
        fun bar(x: Int, w: Int, frac: Double, color: Int) {
            g.fill(x, y + 11, x + w, y + 14, 0xFF0F140C.toInt())
            g.fill(x, y + 11, x + (w * frac.coerceIn(0.0, 1.0)).toInt(), y + 14, color)
        }
        g.text(font, "Points", ix, y, Ui.DIM, false)
        val ptsText = "$pts / ${"%,d".format(Locale.ROOT, maxPts)}"
        g.text(font, ptsText, ix + 150 - font.width(ptsText), y, Ui.GOLD, false)
        bar(ix, 150, pts.toDouble() / maxPts, Ui.GOLD)
        g.text(font, "Unlocked", ix + 162, y, Ui.DIM, false)
        val unText = "$unlocked / ${all.size}"
        g.text(font, unText, ix + 262 - font.width(unText), y, Ui.TEXT, false)
        bar(ix + 162, 100, unlocked.toDouble() / all.size, Ui.GREEN)
        Achievements.data.unlocked.entries.maxByOrNull { it.value.at }?.let { (id, u) ->
            val a = Achievements.byId(id) ?: return@let
            val date = Instant.ofEpochMilli(u.at).atZone(zone).toLocalDate().format(achDate)
            val text = "Latest: ${a.tier.code}${a.name} §8· $date"
            g.text(font, Ui.fit(font, text, iw - 280), ix + iw - minOf(font.width(text), iw - 280), y + 4, Ui.FAINT, false)
        }
        y += 19

        // Rarity chips (counts on hover) and sort
        var fx = ix
        val chips = listOf<Pair<String, Tier?>>("All" to null) + Tier.entries.map { it.label to it }
        for ((label, tier) in chips) {
            val fw = font.width(label) + (if (tier != null) 16 else 10)
            val on = tier == achTier
            val hover = Ui.inside(mx, my, fx, y, fw, 13)
            Ui.box(g, fx, y, fw, 13, if (on) Ui.PILL_ON else if (hover) Ui.CARD_HOVER else Ui.CARD, if (on) Ui.FEATURE_BORDER else Ui.BORDER)
            var lx = fx + 5
            if (tier != null) { g.fill(lx, y + 5, lx + 3, y + 8, tier.color); lx += 6 }
            g.text(font, label, lx, y + 3, if (on) Ui.PILL_ON_TEXT else tier?.color ?: Ui.MUTED, false)
            if (hover) {
                val list = if (tier == null) all else all.filter { it.tier == tier }
                tooltip = "${list.count { Achievements.isUnlocked(it.id) }} / ${list.size} unlocked" + (tier?.let { " · ${it.points} points each" } ?: "")
            }
            hits.add(Ui.Hit(fx, y, fw, 13) { achTier = tier; achScroll = 0 })
            fx += fw + 3
        }
        val sortLabel = "Sort: ${achSort.label} ▾"
        val sw = font.width(sortLabel) + 10
        val sx = ix + iw - sw
        if (sx > fx + 4) {
            val sortHover = Ui.inside(mx, my, sx, y, sw, 13)
            Ui.box(g, sx, y, sw, 13, if (sortHover) Ui.CARD_HOVER else Ui.CARD, Ui.FEATURE_BORDER)
            g.text(font, sortLabel, sx + 5, y + 3, Ui.TEXT, false)
            hits.add(Ui.Hit(sx, y, sw, 13) { achSort = AchSort.entries[(achSort.ordinal + 1) % AchSort.entries.size]; achScroll = 0 })
        }
        y += 17

        // Cards, two columns, scrolling
        val shown = all.filter { achTier == null || it.tier == achTier }.let { list ->
            fun done(a: Achievement) = Achievements.isUnlocked(a.id)
            when (achSort) {
                AchSort.RARITY -> list.sortedWith(compareBy<Achievement>({ it.tier.ordinal }, { !done(it) }))
                AchSort.PROGRESS -> list.sortedWith(compareBy<Achievement>({ done(it) }, { -(if (it.hidden) 0.0 else achProgress(it) ?: 0.0) }, { it.tier.ordinal }))
                AchSort.RECENT -> list.sortedWith(compareBy<Achievement>({ -(Achievements.data.unlocked[it.id]?.at ?: 0L) }, { it.tier.ordinal }))
            }
        }
        val cols = 2
        val gap = 4
        val cw = (iw - gap * (cols - 1) - 6) / cols
        val ch = 48
        val rows = (shown.size + cols - 1) / cols
        val viewH = bottom - y
        achMax = (rows * (ch + gap) - gap - viewH).coerceAtLeast(0)
        achScroll = achScroll.coerceIn(0, achMax)
        g.enableScissor(ix, y, ix + iw, bottom)
        shown.forEachIndexed { i, a ->
            val cx = ix + (i % cols) * (cw + gap)
            val cy = y + (i / cols) * (ch + gap) - achScroll
            if (cy + ch < y || cy > bottom) return@forEachIndexed
            val unlock = Achievements.data.unlocked[a.id]
            val done = unlock != null
            val secret = a.hidden && !done
            Ui.box(g, cx, cy, cw, ch, Ui.CARD, if (done) Ui.FEATURE_BORDER else Ui.BORDER)
            g.fill(cx, cy, cx + 2, cy + ch, if (done) a.tier.color else (0x80 shl 24) or (a.tier.color and 0xFFFFFF))
            // Everything at full size (scaled pixel text gets blurry), with a shadow on the important parts
            val tierText = "${a.tier.label.uppercase()} · ${a.tier.points}"
            val tierW = font.width(tierText)
            g.text(font, tierText, cx + cw - 6 - tierW, cy + 5, a.tier.color, true)
            val icon = if (done) "★" else if (secret) "?" else "☆"
            g.text(font, icon, cx + 6, cy + 5, if (done) a.tier.color else Ui.DIM, true)
            g.text(font, Ui.fit(font, if (secret) "???" else a.name, cw - 30 - tierW), cx + 17, cy + 5, if (done) Ui.WHITE else Ui.TEXT, true)
            val desc = if (secret) "Hidden achievement. Keep playing to find it." else a.desc
            var dy = cy + 17
            for (line in Ui.wrap(font, desc, cw - 12, 2)) { g.text(font, line, cx + 6, dy, if (done) Ui.MUTED else Ui.DIM, false); dy += 10 }
            val ly = cy + ch - 11
            if (done) {
                val date = Instant.ofEpochMilli(unlock!!.at).atZone(zone).toLocalDate().format(achDate)
                g.text(font, "Unlocked $date", cx + 6, ly, Ui.DIM, false)
                if (unlock.run > 0) {
                    val link = "Run #${unlock.run} ›"
                    val lw = font.width(link)
                    val lx = cx + cw - 6 - lw
                    val hover = Ui.inside(mx, my, lx - 2, ly - 2, lw + 4, 11) && my in y until bottom
                    g.text(font, link, lx, ly, if (hover) Ui.WHITE else Ui.GREEN, false)
                    val run = unlock.run
                    if (cy >= y && ly + 9 <= bottom) hits.add(Ui.Hit(lx - 2, ly - 2, lw + 4, 11) { openRun(run) })
                }
            } else if (!secret) {
                val frac = achProgress(a)
                val label = achLabel(a)
                if (frac != null && label != null) {
                    val lw = font.width(label)
                    val bw = (cw - 18 - lw).coerceAtLeast(20)
                    g.fill(cx + 6, ly + 2, cx + 6 + bw, ly + 6, 0xFF0F140C.toInt())
                    g.fill(cx + 6, ly + 2, cx + 6 + (bw * frac).toInt(), ly + 6, (0xC0 shl 24) or (a.tier.color and 0xFFFFFF))
                    g.text(font, label, cx + cw - 6 - lw, ly, Ui.DIM, false)
                } else g.text(font, "Locked", cx + 6, ly, Ui.FAINT, false)
            }
        }
        g.disableScissor()
        if (achMax > 0) {
            val trackH = bottom - y
            val barH = (trackH * trackH / (trackH + achMax)).coerceAtLeast(10)
            val barY = y + (trackH - barH) * achScroll / achMax
            g.fill(ix + iw - 3, barY, ix + iw - 1, barY + barH, Ui.BORDER_HOVER)
        }

        tooltip?.let { t ->
            val tw = font.width(t) + 8
            val tx = (mx + 8).coerceAtMost(width - tw - 2)
            val ty = (my - 14).coerceAtLeast(2)
            Ui.box(g, tx, ty, tw, 13, 0xF0100A14.toInt(), Ui.FEATURE_BORDER)
            g.text(font, t, tx + 4, ty + 3, Ui.TEXT, false)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            hits.lastOrNull { it.contains(event.x(), event.y()) }?.let { it.action(); return true }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (tab == Tab.ACHIEVEMENTS) { achScroll = (achScroll - (scrollY * 30).toInt()).coerceIn(0, achMax); return true }
        if (tab != Tab.HISTORY) return true
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
