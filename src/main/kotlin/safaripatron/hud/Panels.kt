package safaripatron.hud

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import safaripatron.data.Biome
import safaripatron.data.Critter
import safaripatron.data.Critters
import safaripatron.safari.Run

/** İki taşınabilir panelin çizimi. g == null ise sadece boyut ölçülür. */
object Panels {
    private const val PAD = 4
    private const val LH = 10
    private const val GAP = 4
    private const val LABEL_W = 52
    private const val BAR_W = 60
    private const val BG = 0x90000000.toInt()
    private const val TITLE = 0xFFFFFF55.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GRAY = 0xFFAAAAAA.toInt()
    private const val BAR_BG = 0xFF3A3A3A.toInt()

    class Size(val w: Int, val h: Int)

    // ---------------- Panel 1: Run ----------------
    /** primary = oyuncunun ilk yakaladığı bölge (dağıldığı yer); ismi bu bölgenin renginde yazılır. */
    class PlayerRow(val name: String, val primary: Biome?, val parts: List<Pair<Biome, Int>>) {
        fun valueText() = parts.joinToString(", ") { "${it.first.label} ${it.second}" }
    }

    class RunData(
        val elapsedMs: Long,
        val party: Int,
        val biomes: List<Pair<Biome, Int>>,
        val players: List<PlayerRow>,
        val self: String?,
    )

    fun runData(self: String?): RunData {
        val selfFirst = Run.byPlayer.entries.sortedBy { if (it.key == self) 0 else 1 }
        val players = selfFirst.mapNotNull { (name, biomes) ->
            val parts = Biome.entries.mapNotNull { b -> biomes[b]?.size?.takeIf { it > 0 }?.let { b to it } }
            // byPlayer bölgeleri ekleme sırasıyla tutuyor: ilk anahtar = ilk yakalama bölgesi
            val primary = biomes.entries.firstOrNull { it.value.isNotEmpty() }?.key
            if (parts.isEmpty()) null else PlayerRow(name, primary, parts)
        }
        return RunData(Run.elapsedMs, Run.caught.size, Biome.entries.map { it to Run.caughtIn(it) }, players, self)
    }

    fun demoRun(self: String?) = RunData(113_000, 6, listOf(Biome.FOREST to 2, Biome.CAVERN to 1, Biome.ICY to 1, Biome.HAUNTED to 2),
        listOf(PlayerRow(self ?: "You", Biome.ICY, listOf(Biome.ICY to 1)),
            PlayerRow("Partner", Biome.FOREST, listOf(Biome.FOREST to 2, Biome.CAVERN to 1, Biome.HAUNTED to 2))), self)

    private fun time(ms: Long): String {
        val s = ms / 1000
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    fun drawRun(g: GuiGraphicsExtractor?, font: Font, x: Int, y: Int, d: RunData): Size {
        val title = "Critter Safari "
        val timeText = time(d.elapsedMs)
        val valueW = font.width("37/37")
        var w = PAD + LABEL_W + BAR_W + 8 + valueW + PAD
        w = maxOf(w, PAD + font.width(title + timeText) + PAD)
        for (p in d.players) w = maxOf(w, PAD + font.width(p.name) + 12 + font.width(p.valueText()) + PAD)
        var h = PAD + LH + LH + GAP + 4 * LH + PAD
        if (d.players.isNotEmpty()) h += GAP + d.players.size * LH
        val gx = g ?: return Size(w, h)

        gx.fill(x, y, x + w, y + h, BG)
        var cy = y + PAD
        gx.text(font, title, x + PAD, cy, TITLE, true)
        gx.text(font, timeText, x + PAD + font.width(title), cy, WHITE, true)
        cy += LH

        fun row(label: String, color: Int, have: Int, total: Int, barColor: Int) {
            gx.text(font, label, x + PAD, cy, color, true)
            val bx = x + PAD + LABEL_W
            gx.fill(bx, cy + 3, bx + BAR_W, cy + 6, BAR_BG)
            val fillW = if (total == 0) 0 else (BAR_W * have / total)
            if (fillW > 0) gx.fill(bx, cy + 3, bx + fillW, cy + 6, barColor)
            val v = "$have/$total"
            gx.text(font, v, x + w - PAD - font.width(v), cy, color, true)
            cy += LH
        }

        row("Uniques", WHITE, d.party, Critters.TOTAL, WHITE)
        cy += GAP
        for ((b, n) in d.biomes) row(b.label, b.color, n, b.critters.size, b.color)

        if (d.players.isNotEmpty()) {
            cy += GAP
            for (p in d.players) {
                gx.text(font, p.name, x + PAD, cy, p.primary?.color ?: WHITE, true)
                // Değerler sağa yaslı; her bölge kendi renginde, aralarındaki virgüller gri
                var vx = x + w - PAD - font.width(p.valueText())
                p.parts.forEachIndexed { i, (b, n) ->
                    if (i > 0) { gx.text(font, ", ", vx, cy, GRAY, true); vx += font.width(", ") }
                    val t = "${b.label} $n"
                    gx.text(font, t, vx, cy, b.color, true)
                    vx += font.width(t)
                }
                cy += LH
            }
        }
        return Size(w, h)
    }

    // ---------------- Panel 2: Bölge ----------------
    class BiomeData(val biome: Biome, val remaining: List<Critter>)

    fun biomeData(biome: Biome): BiomeData =
        BiomeData(biome, biome.critters.filter { it.name !in Run.caught }
            .sortedWith(compareBy<Critter> { it.rarity.ordinal }.thenBy { biome.critters.indexOf(it) }))

    fun demoBiome() = BiomeData(Biome.ICY, Biome.ICY.critters.sortedBy { it.rarity.ordinal })

    fun drawBiome(g: GuiGraphicsExtractor?, font: Font, x: Int, y: Int, d: BiomeData): Size {
        val title = if (d.remaining.isEmpty()) "${d.biome.label} Biome — Done!" else "${d.biome.label} Biome — ${d.remaining.size} left"
        var w = PAD + font.width(title) + PAD
        for (c in d.remaining) w = maxOf(w, PAD + CritterIcons.advance + font.width(c.name) + PAD)
        val rowHeight = CritterIcons.rowHeight
        val h = PAD + LH + d.remaining.size * rowHeight + PAD
        val gx = g ?: return Size(w, h)

        gx.fill(x, y, x + w, y + h, BG)
        var cy = y + PAD
        gx.text(font, title, x + PAD, cy, d.biome.color, true)
        cy += LH
        for (c in d.remaining) {
            CritterIcons.draw(gx, c.name, x + PAD, cy)
            gx.text(font, c.name, x + PAD + CritterIcons.advance, cy + (rowHeight - LH) / 2, c.rarity.color, true)
            cy += rowHeight
        }
        return Size(w, h)
    }
}
