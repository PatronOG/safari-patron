package safaripatron.ui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import safaripatron.SafariPatron

/** Safari Patron menu palette and small drawing helpers (forest green + gold). */
object Ui {
    const val WINDOW = 0xF212170F.toInt()
    const val CARD = 0xFF1A2115.toInt()
    const val CARD_HOVER = 0xFF212B1B.toInt()
    const val BORDER = 0xFF33402B.toInt()
    const val BORDER_HOVER = 0xFF5A7048.toInt()
    const val HEADER = 0xFF1B2416.toInt()
    const val HEADER_BORDER = 0xFF2F4A22.toInt()
    const val FEATURE = 0xFF1F3317.toInt()
    const val FEATURE_BORDER = 0xFF5FA63A.toInt()
    const val DIVIDER = 0xFF26301F.toInt()
    const val SELECTED = 0xFF24371A.toInt()

    const val WHITE = 0xFFFFFFFF.toInt()
    const val TEXT = 0xFFE8EFE0.toInt()
    const val MUTED = 0xFFA9B8A0.toInt()
    const val DIM = 0xFF8F9A88.toInt()
    const val FAINT = 0xFF6F7F66.toInt()
    const val GREEN = 0xFF8FD16A.toInt()
    const val GOLD = 0xFFE3C35A.toInt()

    const val PILL_GOLD = 0xFFC9A43A.toInt()
    const val PILL_GOLD_TEXT = 0xFF2A1F05.toInt()
    const val PILL_ON = 0xFF2B4A1D.toInt()
    const val PILL_ON_TEXT = 0xFF9FE07A.toInt()
    const val PILL_OFF = 0xFF2A2F27.toInt()
    const val PILL_OFF_TEXT = DIM
    const val SWITCH_ON = 0xFF5FA63A.toInt()
    const val SWITCH_OFF = 0xFF3A4234.toInt()

    private val LOGO = Identifier.fromNamespaceAndPath(SafariPatron.MOD_ID, "textures/gui/logo.png")

    /** Rectangle with 1px cut corners (reads as slightly rounded at GUI scale). */
    fun rounded(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        if (w <= 2 || h <= 2) { g.fill(x, y, x + w, y + h, color); return }
        g.fill(x + 1, y, x + w - 1, y + h, color)
        g.fill(x, y + 1, x + w, y + h - 1, color)
    }

    fun box(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, fill: Int, border: Int) {
        rounded(g, x, y, w, h, border)
        rounded(g, x + 1, y + 1, w - 2, h - 2, fill)
    }

    fun text(g: GuiGraphicsExtractor, font: Font, s: String, x: Int, y: Int, color: Int, scale: Float = 1f, shadow: Boolean = false) {
        if (scale == 1f) { g.text(font, s, x, y, color, shadow); return }
        g.pose().pushMatrix()
        g.pose().translate(x.toFloat(), y.toFloat())
        g.pose().scale(scale, scale)
        g.text(font, s, 0, 0, color, shadow)
        g.pose().popMatrix()
    }

    /** Cuts the text with "…" so it fits in maxW pixels. */
    fun fit(font: Font, s: String, maxW: Int): String {
        if (font.width(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && font.width("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /** Splits text into lines of at most maxW pixels (by words); the last allowed line is cut with "…". */
    fun wrap(font: Font, s: String, maxW: Int, maxLines: Int): List<String> {
        val lines = ArrayList<String>()
        var cur = ""
        for (word in s.split(' ')) {
            val next = if (cur.isEmpty()) word else "$cur $word"
            if (font.width(next) <= maxW || cur.isEmpty()) cur = next
            else { lines.add(cur); cur = word }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        // A color code keeps going on the next line, like it does in chat
        var carry = ""
        for (i in lines.indices) {
            lines[i] = carry + lines[i]
            carry = lastColor(lines[i])
        }
        if (lines.size <= maxLines) return lines.map { fit(font, it, maxW) }
        val kept = lines.take(maxLines).toMutableList()
        kept[maxLines - 1] = fit(font, lines.drop(maxLines - 1).joinToString(" "), maxW)
        return kept
    }

    /** The last color code in [s] ("§c"), or "" when there is none or it was reset with §r. */
    private fun lastColor(s: String): String {
        var i = s.length - 2
        while (i >= 0) {
            if (s[i] == '§') {
                val c = s[i + 1].lowercaseChar()
                if (c == 'r') return ""
                if (c in '0'..'9' || c in 'a'..'f') return "§$c"
            }
            i--
        }
        return ""
    }

    /** Small pill label; returns its width. */
    fun pill(g: GuiGraphicsExtractor, font: Font, s: String, x: Int, y: Int, bg: Int, fg: Int): Int {
        val w = font.width(s) + 8
        rounded(g, x, y, w, 11, bg)
        g.text(font, s, x + 4, y + 2, fg, false)
        return w
    }

    fun switch(g: GuiGraphicsExtractor, x: Int, y: Int, on: Boolean) {
        rounded(g, x, y, 20, 10, if (on) SWITCH_ON else SWITCH_OFF)
        val kx = if (on) x + 11 else x + 1
        rounded(g, kx, y + 1, 8, 8, WHITE)
    }

    fun logo(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int) {
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0f, 0f, size, size, 128, 128, 128, 128, -1)
    }

    class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit) {
        fun contains(px: Double, py: Double) = px >= x && px < x + w && py >= y && py < y + h
    }

    fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h
}
