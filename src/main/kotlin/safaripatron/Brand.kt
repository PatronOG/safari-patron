package safaripatron

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import safaripatron.config.Config

/**
 * The [✦Safari Patron✦] chat prefix. Every character gets its own RGB color, which gives
 * gradient and "metallic" (dark → light → dark) looks in chat.
 */
object Brand {
    enum class Style(val id: String, val stops: IntArray) {
        SAFARI("safari", intArrayOf(0x5FD35F, 0xE3C35A)),
        GOLD("gold", intArrayOf(0xB8860B, 0xFFF2B0, 0xB8860B)),
        EMERALD("emerald", intArrayOf(0x1E8F4E, 0xB6FFC9, 0x1E8F4E)),
        SUNSET("sunset", intArrayOf(0xFF7A3D, 0xFFD166)),
        AURORA("aurora", intArrayOf(0x4FD1C5, 0x9F7AEA)),
        ;

        companion object {
            fun of(id: String?) = entries.firstOrNull { it.id.equals(id, true) } ?: SAFARI
        }
    }

    private const val BRACKET = 0x8A8A8A
    private const val INNER = "✦Safari Patron✦"

    private fun lerp(a: Int, b: Int, t: Double): Int {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** Color at position t (0..1) along the style's color stops. */
    fun colorAt(style: Style, t: Double): Int {
        val stops = style.stops
        if (stops.size == 1) return stops[0]
        val scaled = t.coerceIn(0.0, 1.0) * (stops.size - 1)
        val i = scaled.toInt().coerceAtMost(stops.size - 2)
        return lerp(stops[i], stops[i + 1], scaled - i)
    }

    fun gradient(text: String, style: Style): MutableComponent {
        val out = Component.empty()
        val n = text.length
        text.forEachIndexed { i, c ->
            val t = if (n <= 1) 0.0 else i.toDouble() / (n - 1)
            out.append(Component.literal(c.toString()).withColor(colorAt(style, t)))
        }
        return out
    }

    fun prefix(style: Style = Style.of(Config.s.prefixStyle)): MutableComponent =
        Component.empty()
            .append(Component.literal("[").withColor(BRACKET))
            .append(gradient(INNER, style))
            .append(Component.literal("]").withColor(BRACKET))

    /** Prefix + a space + message (the message may use § color codes). */
    fun msg(text: String): MutableComponent = prefix().append(Component.literal(" §f$text"))
}
