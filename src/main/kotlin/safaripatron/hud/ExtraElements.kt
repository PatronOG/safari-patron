package safaripatron.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import safaripatron.config.Config
import safaripatron.config.Settings
import safaripatron.contest.Contest
import safaripatron.data.Biome
import safaripatron.safari.Safari

private const val PAD = 4
private const val LH = 10
private const val BG = 0x90000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val GRAY = 0xFF777777.toInt()
private const val GREEN = 0xFF55FF55.toInt()
private const val GOLD = 0xFFFFCC66.toInt()
private const val RED = 0xFFFF5555.toInt()

/** The Bazaar warning (prices unavailable / old) as an extra red row, if there is one. */
private fun withPriceWarning(rows: List<Pair<String, Int>>): List<Pair<String, Int>> =
    safaripatron.profit.Bazaar.warning()?.let { rows + (it to RED) } ?: rows

/** Draws a title + rows box. rows: (text, color). */
private fun drawBox(g: GuiGraphicsExtractor?, font: Font, title: String, titleColor: Int, rows: List<Pair<String, Int>>): Panels.Size {
    var w = PAD + font.width(title) + PAD
    for ((t, _) in rows) w = maxOf(w, PAD + font.width(t) + PAD)
    val h = PAD + LH + rows.size * LH + PAD
    val gx = g ?: return Panels.Size(w, h)
    gx.fill(0, 0, w, h, BG)
    var y = PAD
    gx.text(font, title, PAD, y, titleColor, true)
    y += LH
    for ((t, c) in rows) { gx.text(font, t, PAD, y, c, true); y += LH }
    return Panels.Size(w, h)
}

/** Which critters of the current biome you (or your party) have ever caught in sparkling form. */
object CollectionElement : HudElement {
    override val name = "Sparklings panel"
    override val pos get() = Config.s.collectionPanel
    override val defaultPos get() = Settings.DEFAULT_COLLECTION
    override fun visible() = Config.s.sparklingCollectionHud && Safari.inSafari && Safari.biome != null

    override fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size {
        val live = Safari.biome
        val biome = if (preview && (live == null || !visible())) Biome.FOREST else live ?: return Panels.Size(0, 0)
        val owned = Config.s.sparklingCollection
        val critters = biome.critters.sortedBy { it.rarity.ordinal }
        val have = critters.count { it.name in owned }
        val missingOnly = Config.s.sparklingCollectionMissingOnly
        val rows = critters.filter { !missingOnly || it.name !in owned }.map { c ->
            if (c.name in owned) "✔ ${c.name}" to GREEN else "✘ ${c.name}" to GRAY
        }.ifEmpty { listOf("All caught!" to GREEN) }
        return drawBox(g, font, "✦ Sparklings · ${biome.label} $have/${critters.size}", biome.color, rows)
    }
}

/** Miria's Contest: time left, bracket, score and ticket. */
object ContestElement : HudElement {
    override val name = "Contest panel"
    override val pos get() = Config.s.contestPanel
    override val defaultPos get() = Settings.DEFAULT_CONTEST
    override fun visible() = Config.s.contest && Config.s.contestHud && Contest.inSkyblock(Minecraft.getInstance())

    override fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size {
        val sample = preview && !visible()
        val gap = !sample && Contest.betweenContests()
        val seconds = if (sample) 754 else if (gap) Contest.secondsToNextStart() else Contest.secondsToEnd()
        val tier = if (sample) "Rare" else Contest.currentTier().ifBlank { "—" }
        val points = if (sample) 1840 else Contest.currentPoints()
        val done = !sample && Contest.isComplete()
        val ticket = if (Config.s.contestFirstClass) "First-Class" else "Basic"
        val rows = buildList {
            add("${if (gap) "Next contest" else "Time left"}: ${Contest.format(seconds)}" to WHITE)
            add("Bracket: $tier" to WHITE)
            add("Score: ${points.takeIf { it >= 0 }?.let { "%,d".format(it) } ?: "—"}" to WHITE)
            add("$ticket ticket: ${if (done) "Earned" else "Incomplete"}" to if (done) GREEN else WHITE)
            if (sample || safaripatron.contest.Honeyhives.enabled()) {
                val hives = safaripatron.contest.Honeyhives
                val total = hives.hives.size
                val ready = if (sample) 14 else hives.readyCount()
                val next = if (sample || ready > 0) null else hives.nextRefillMs()
                add("Honeyhives: $ready/$total ready" + (next?.let { " · next ${(it + 59_999) / 60_000}m" } ?: "") to
                    if (ready > 0) GOLD else GRAY)
            }
        }
        return drawBox(g, font, "Miria's Contest", GOLD, rows)
    }
}

/** All 37 sparklings in four biome columns; only in the middle area (in the Safari, not inside a biome). */
object TotalElement : HudElement {
    override val name = "Total sparklings panel"
    override val pos get() = Config.s.totalPanel
    override val defaultPos get() = Settings.DEFAULT_TOTAL
    override val centered get() = true
    override fun visible() = Config.s.sparklingTotalHud && Safari.inSafari && Safari.biome == null

    private const val COL_GAP = 10

    override fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size {
        val owned = Config.s.sparklingCollection
        val missingOnly = Config.s.sparklingCollectionMissingOnly
        val total = Biome.entries.sumOf { it.critters.size }
        val have = Biome.entries.sumOf { b -> b.critters.count { it.name in owned } }
        val title = "✦ Sparklings · Total $have/$total"

        // One column per biome: header + rows
        val columns = Biome.entries.map { b ->
            val list = b.critters.sortedBy { it.rarity.ordinal }
            val got = list.count { it.name in owned }
            val rows = list.filter { !missingOnly || it.name !in owned }.map { c ->
                if (c.name in owned) "✔ ${c.name}" to GREEN else "✘ ${c.name}" to GRAY
            }.ifEmpty { listOf("All caught!" to GREEN) }
            Triple("${b.label} $got/${list.size}" to b.color, rows, maxOf(font.width("${b.label} $got/${list.size}"),
                rows.maxOf { font.width(it.first) }))
        }
        val colsW = columns.sumOf { it.third } + COL_GAP * (columns.size - 1)
        val w = PAD + maxOf(colsW, font.width(title)) + PAD
        val h = PAD + LH + 2 + LH + columns.maxOf { it.second.size } * LH + PAD
        val gx = g ?: return Panels.Size(w, h)

        gx.fill(0, 0, w, h, BG)
        gx.text(font, title, (w - font.width(title)) / 2, PAD, 0xFFFFB627.toInt(), true)
        var x = PAD + (w - PAD * 2 - colsW) / 2
        for ((header, rows, colW) in columns) {
            var y = PAD + LH + 2
            gx.text(font, header.first, x, y, header.second, true)
            y += LH
            for ((t, c) in rows) { gx.text(font, t, x, y, c, true); y += LH }
            x += colW + COL_GAP
        }
        return Panels.Size(w, h)
    }
}

/** Session profit, shown in the Safari. */
object ProfitElement : HudElement {
    override val name = "Session profit panel"
    override val pos get() = Config.s.profitPanel
    override val defaultPos get() = Settings.DEFAULT_PROFIT
    override fun visible() = Config.s.profitTracker && Config.s.profitHud && Safari.inSafari
    override val overScreens get() = true

    override fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size {
        val p = safaripatron.profit.Profit
        val d = safaripatron.profit.Profit.data
        val sample = preview && !visible()
        val rows: List<Pair<String, Int>>
        val title: String
        if (sample) {
            title = "Session Profit · 12:36"
            rows = listOf("Shard Profits: 6.1M" to WHITE, "Essence Profits: 2.4M" to WHITE, "Rainbow Feather: 0.7M" to WHITE,
                "Total: 9.2M (3 runs · avg 4:12)" to GOLD, "Profit Per Hour: 43.8M/h" to GREEN)
        } else {
            val items = p.sessionItems()
            val shards = p.valueOf(items, safaripatron.profit.Profit.Kind.SHARD)
            val essence = p.valueOf(items, safaripatron.profit.Profit.Kind.ESSENCE)
            val feather = p.valueOf(items, safaripatron.profit.Profit.Kind.FEATHER)
            val dyes = items[safaripatron.profit.Bazaar.DYE] ?: 0L
            val dye = p.valueOf(items, safaripatron.profit.Profit.Kind.DYE)
            val runs = d.sessionRuns
            // Live session clock: starts with the ticket, pauses after 30 s away
            title = if (d.sessionStart == 0L) "Session Profit · waiting for a run" else "Session Profit · ${p.clock(p.sessionMs)}"
            rows = buildList {
                add("Shard Profits: ${p.coins(shards)}" to WHITE)
                add("Essence Profits: ${p.coins(essence)}" to WHITE)
                add("Rainbow Feather: ${p.coins(feather)}" to WHITE)
                // Only once one dropped; "?" while the Auction House price is unknown
                if (dyes > 0) add("Emerald Dye: ${dyes}x (${if (dye > 0) p.coins(dye) else "?"})" to 0xFF55AA55.toInt())
                add("Total: ${p.coins(shards + essence + feather + dye)} ($runs run${if (runs == 1) "" else "s"}" +
                    (if (runs > 0) " · avg ${p.clock(p.avgRunMs)})" else ")") to GOLD)
                add("Profit Per Hour: ${p.coinsShort(p.perHour)}/h" to GREEN)
            }
        }
        val shown = if (!sample && !safaripatron.profit.Bazaar.ready() && safaripatron.profit.Bazaar.warning() == null) "$title · loading prices…" else title
        return drawBox(g, font, shown, GREEN, if (sample) rows else withPriceWarning(rows))
    }
}

/** All-time Safari profit item by item; shown in SkyBlock outside the Safari. */
object TotalProfitElement : HudElement {
    override val name = "Total profit panel"
    override val pos get() = Config.s.totalProfitPanel
    override val defaultPos get() = Settings.DEFAULT_TOTAL_PROFIT
    override fun visible() = Config.s.profitTracker && Config.s.totalProfitHud && !Safari.inSafari &&
        Contest.inSkyblock(Minecraft.getInstance()) && safaripatron.profit.Profit.data.totalItems.isNotEmpty() &&
        (Config.s.totalProfitWhere != "entrance" || atEntrance())

    private var entranceCheckedAt = 0L
    private var entrance = false

    /** Scoreboard location is the Critter Safari Entrance (checked twice a second). */
    private fun atEntrance(): Boolean {
        val now = System.currentTimeMillis()
        if (now - entranceCheckedAt >= 500) {
            entranceCheckedAt = now
            // Letters only: Hypixel sometimes splits sidebar lines with extra symbols
            entrance = safaripatron.safari.Sidebar.lines(Minecraft.getInstance())
                .any { line -> line.filter { it.isLetter() }.lowercase().contains("crittersafarientrance") }
        }
        return entrance
    }
    override val overScreens get() = true

    /** At most this many item rows; the rest scroll with the mouse wheel while an inventory is open. */
    const val MAX_ROWS = 10
    var scroll = 0
    private var itemCount = 0

    /** Mouse wheel over the panel (inventory open): true if it scrolled. */
    fun scrollBy(steps: Int): Boolean {
        val max = (itemCount - MAX_ROWS).coerceAtLeast(0)
        val next = (scroll + steps).coerceIn(0, max)
        if (next == scroll) return max > 0
        scroll = next
        return true
    }

    override fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size {
        val p = safaripatron.profit.Profit
        val sample = preview && !visible()
        // (count, item id, coins)
        val items: List<Triple<Long, String, Double>> = if (sample) listOf(
            Triple(64L, "SHARD_WUMPA", 2.1e6), Triple(12L, safaripatron.profit.Bazaar.FEATHER, 1.3e6),
            Triple(48210L, safaripatron.profit.Bazaar.ESSENCE, 0.9e6),
        ) else p.totalItems().entries.map { (id, n) -> Triple(n, id, p.itemValue(id, n)) }.sortedByDescending { it.third }
        val total = items.sumOf { it.third }
        val runs = if (sample) 128 else safaripatron.profit.Profit.data.totalRuns
        itemCount = items.size
        scroll = scroll.coerceIn(0, (items.size - MAX_ROWS).coerceAtLeast(0))
        val shown = items.drop(scroll).take(MAX_ROWS)
        val above = scroll
        val below = items.size - scroll - shown.size
        // §7 count (gray) · item in its rarity color · §6 coins (orange)
        val rows = buildList {
            if (above > 0) add("▲ $above more" to GRAY)
            shown.forEach { (n, id, v) ->
                // No Bazaar price for this item (yet): "?" instead of a misleading 0
                val worth = if (sample || v > 0) "§6${p.coins(v)}" else "§c?"
                add("§7${"%,d".format(n)}x ${p.itemColor(id)}${p.itemName(id)} $worth" to WHITE)
            }
            if (below > 0) add("▼ $below more (scroll)" to GRAY)
            add("Total Runs: $runs" to GOLD)
            add("Total Profit: ${p.coins(total)} coins" to GOLD)
        }
        val title = if (!sample && !safaripatron.profit.Bazaar.ready() && safaripatron.profit.Bazaar.warning() == null)
            "Total Profit Tracker · loading prices…" else "Total Profit Tracker"
        return drawBox(g, font, title, GREEN, if (sample) rows else withPriceWarning(rows))
    }
}

/**
 * The most valuable shards to catch: in a biome its top 3 or 5, in the middle area every biome's.
 * Ranked by what one catch is worth to you: Bazaar price x the shards you get per catch on average
 * (learned from your own catches; 1 until a critter has 5 catches, marked "~").
 */
object TopShardsElement : HudElement {
    override val name = "Top shards panel"
    override val pos get() = Config.s.topShardsPanel
    override val defaultPos get() = Settings.DEFAULT_TOP_SHARDS
    override fun visible() = Config.s.topShardsHud && Safari.inSafari

    private const val COL_GAP = 12

    private class Entry(val name: String, val color: String, val price: Double, val perCatch: Double, val learned: Boolean) {
        val worth get() = price * perCatch
    }

    private fun top(biome: Biome): List<Entry> {
        val offer = Config.s.profitSellOffer
        return biome.critters.map { c ->
            val id = safaripatron.profit.Bazaar.shardId(c.name)
            val (avg, learned) = safaripatron.history.History.shardsPerCatch(c.name)
            Entry(c.name, safaripatron.profit.Profit.itemColor(id), safaripatron.profit.Bazaar.price(id, offer), avg, learned)
        }.sortedByDescending { it.worth }.take(Config.s.topShardsCount)
    }

    private fun row(e: Entry, details: Boolean): String {
        val p = safaripatron.profit.Profit
        if (e.price <= 0) return "${e.color}${e.name} §c?"
        val worth = (if (e.learned) "" else "~") + p.coins(e.worth)
        return "${e.color}${e.name} §6$worth" + if (details) " §7(${p.coins(e.price)}×${String.format(java.util.Locale.ROOT, "%.1f", e.perCatch)})" else ""
    }

    private fun sample(biome: Biome) = when (biome) {
        Biome.ICY -> listOf(Entry("Strongarm", "§f", 182_400.0, 3.0, true), Entry("Polaris", "§a", 121_000.0, 2.0, true), Entry("Tepid", "§f", 98_300.0, 1.0, false))
        Biome.FOREST -> listOf(Entry("Macaw", "§6", 410_000.0, 1.0, false), Entry("Fluffling", "§9", 96_000.0, 2.0, true), Entry("Parakeet", "§9", 88_000.0, 1.0, false))
        Biome.CAVERN -> listOf(Entry("Gemzie", "§5", 260_000.0, 1.0, false), Entry("Rockmite", "§9", 75_000.0, 2.0, true), Entry("Scrappy", "§9", 64_000.0, 1.0, false))
        Biome.HAUNTED -> listOf(Entry("Doomspiral", "§6", 530_000.0, 1.0, false), Entry("Hideonwall", "§9", 150_000.0, 1.0, true), Entry("Gimmiegold", "§9", 120_000.0, 2.0, true))
    }.take(Config.s.topShardsCount)

    override fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size {
        val sample = preview && !visible()
        val biome = if (sample) Biome.ICY else Safari.biome
        if (biome != null) {
            val list = if (sample) sample(biome) else top(biome)
            val rows = list.map { row(it, true) to WHITE }.ifEmpty { listOf("No prices yet" to GRAY) }
            return drawBox(g, font, "${biome.label} · Top Shards", biome.color, if (sample) rows else withPriceWarning(rows))
        }
        // Middle area: one column per biome
        val title = "Top Shards"
        val columns = Biome.entries.map { b ->
            val rows = top(b).map { row(it, false) }
            val header = b.label
            Triple(b, rows, maxOf(font.width(header), rows.maxOfOrNull { font.width(it) } ?: 0))
        }
        val colsW = columns.sumOf { it.third } + COL_GAP * (columns.size - 1)
        val w = PAD + maxOf(colsW, font.width(title)) + PAD
        val h = PAD + LH + 2 + LH + (columns.maxOfOrNull { it.second.size } ?: 0) * LH + PAD
        val gx = g ?: return Panels.Size(w, h)
        gx.fill(0, 0, w, h, BG)
        gx.text(font, title, (w - font.width(title)) / 2, PAD, GOLD, true)
        var x = PAD + (w - PAD * 2 - colsW) / 2
        for ((b, rows, colW) in columns) {
            var y = PAD + LH + 2
            gx.text(font, b.label, x, y, b.color, true)
            y += LH
            for (t in rows) { gx.text(font, t, x, y, WHITE, true); y += LH }
            x += colW + COL_GAP
        }
        return Panels.Size(w, h)
    }
}
