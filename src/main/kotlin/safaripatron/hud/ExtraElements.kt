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
        val rows = listOf(
            "${if (gap) "Next contest" else "Time left"}: ${Contest.format(seconds)}" to WHITE,
            "Bracket: $tier" to WHITE,
            "Score: ${points.takeIf { it >= 0 }?.let { "%,d".format(it) } ?: "—"}" to WHITE,
            "$ticket ticket: ${if (done) "Earned" else "Incomplete"}" to if (done) GREEN else WHITE,
        )
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
            val runs = d.sessionRuns
            // Live session clock: starts with the ticket, pauses after 30 s away
            title = if (d.sessionStart == 0L) "Session Profit · waiting for a run" else "Session Profit · ${p.clock(p.sessionMs)}"
            rows = listOf(
                "Shard Profits: ${p.coins(shards)}" to WHITE,
                "Essence Profits: ${p.coins(essence)}" to WHITE,
                "Rainbow Feather: ${p.coins(feather)}" to WHITE,
                "Total: ${p.coins(shards + essence + feather)} ($runs run${if (runs == 1) "" else "s"}" +
                    (if (runs > 0) " · avg ${p.clock(p.avgRunMs)})" else ")") to GOLD,
                "Profit Per Hour: ${p.coinsShort(p.perHour)}/h" to GREEN,
            )
        }
        val shown = if (!sample && !safaripatron.profit.Bazaar.ready()) "$title · loading prices…" else title
        return drawBox(g, font, shown, GREEN, rows)
    }
}

/** All-time Safari profit item by item; shown in SkyBlock outside the Safari. */
object TotalProfitElement : HudElement {
    override val name = "Total profit panel"
    override val pos get() = Config.s.totalProfitPanel
    override val defaultPos get() = Settings.DEFAULT_TOTAL_PROFIT
    override fun visible() = Config.s.profitTracker && Config.s.totalProfitHud && !Safari.inSafari &&
        Contest.inSkyblock(Minecraft.getInstance()) && safaripatron.profit.Profit.data.totalItems.isNotEmpty()
    override val overScreens get() = true

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
        // §7 count (gray) · item in its rarity color · §6 coins (orange)
        val rows = items.map { (n, id, v) -> "§7${"%,d".format(n)}x ${p.itemColor(id)}${p.itemName(id)} §6${p.coins(v)}" to WHITE } +
            listOf("Total Runs: $runs" to GOLD, "Total Profit: ${p.coins(total)} coins" to GOLD)
        return drawBox(g, font, "Total Profit Tracker", GREEN, rows)
    }
}
