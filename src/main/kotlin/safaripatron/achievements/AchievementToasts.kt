package safaripatron.achievements

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import safaripatron.config.Config
import safaripatron.config.Settings
import safaripatron.hud.HudElement
import safaripatron.hud.Panels

/** "LEGENDARY ACHIEVEMENT · +50 / Shiny Collector / description": stacked boxes at the top of the screen, movable in /sp hud. */
object AchievementToasts : HudElement {
    private class Toast(val header: String, val title: String, val desc: String, val color: Int, val start: Long, val until: Long)

    private val list = ArrayList<Toast>()
    private const val SHOW_MS = 5_000L
    private const val PAD = 6
    private const val GAP = 4
    private const val TITLE_SCALE = 1.5f

    override val name = "Achievement Panel"
    override val pos get() = Config.s.achievementPanel
    override val defaultPos get() = Settings.DEFAULT_ACHIEVEMENT
    override val centered get() = true
    override val overScreens get() = true

    override fun visible(): Boolean {
        val now = System.currentTimeMillis()
        list.removeIf { it.until <= now }
        return list.isNotEmpty()
    }

    fun show(a: Achievement) {
        val now = System.currentTimeMillis()
        // One after another when several come at once
        val start = maxOf(now, (list.maxOfOrNull { it.until } ?: now) - SHOW_MS + 600)
        list.add(Toast("${a.tier.label.uppercase()} ACHIEVEMENT · +${a.tier.points}", a.name, a.desc, a.tier.color, start, start + SHOW_MS))
    }

    fun showSummary(count: Int, points: Int) {
        val now = System.currentTimeMillis()
        list.add(Toast("ACHIEVEMENTS · +$points", "$count unlocked", "From your past runs · /sp history", 0xFFFFAA00.toInt(), now, now + SHOW_MS + 2000))
    }

    override fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size {
        val now = System.currentTimeMillis()
        val items = if (preview && !visible())
            listOf(Toast("LEGENDARY ACHIEVEMENT · +50", "Shiny Collector", "Register 20 sparklings in your Critterdex.", Tier.LEGENDARY.color, 0, Long.MAX_VALUE))
        else list.filter { it.start <= now }
        if (items.isEmpty()) return Panels.Size(0, 0)
        val lh = font.lineHeight
        val titleH = (lh * TITLE_SCALE).toInt()
        val boxH = PAD + lh + 2 + titleH + 2 + lh + PAD
        fun boxW(t: Toast) = PAD * 2 + 4 + maxOf(font.width(t.header), (font.width(t.title) * TITLE_SCALE).toInt(), font.width(t.desc))
        val w = items.maxOf { boxW(it) }
        val h = items.size * boxH + (items.size - 1) * GAP
        if (g == null) return Panels.Size(w, h)

        var top = 0
        for (t in items) {
            val a = if (preview) 1.0 else minOf(1.0, (now - t.start) / 200.0, (t.until - now) / 500.0).coerceIn(0.0, 1.0)
            val alpha = (a * 255).toInt().coerceAtLeast(5)
            fun c(rgb: Int) = (alpha shl 24) or (rgb and 0xFFFFFF)
            val bw = boxW(t)
            val x0 = (w - bw) / 2
            g.fill(x0, top, x0 + bw, top + boxH, ((a * 0xC8).toInt() shl 24) or 0x0C100A)
            g.fill(x0, top, x0 + 3, top + boxH, c(t.color))
            g.fill(x0 + 3, top, x0 + bw, top + 1, c(t.color))
            g.fill(x0 + 3, top + boxH - 1, x0 + bw, top + boxH, c(t.color))
            val tx = x0 + PAD + 4
            var y = top + PAD
            g.text(font, t.header, tx, y, c(t.color), true)
            y += lh + 2
            g.pose().pushMatrix()
            g.pose().translate(tx.toFloat(), y.toFloat())
            g.pose().scale(TITLE_SCALE, TITLE_SCALE)
            g.text(font, t.title, 0, 0, c(0xFFFFFF), true)
            g.pose().popMatrix()
            y += titleH + 2
            g.text(font, t.desc, tx, y, c(0xAAAAAA), true)
            top += boxH + GAP
        }
        return Panels.Size(w, h)
    }
}
