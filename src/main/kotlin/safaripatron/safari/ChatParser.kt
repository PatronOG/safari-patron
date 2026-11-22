package safaripatron.safari

import safaripatron.data.Critters

/** Hypixel sohbet mesajlarını tanır. Bütün kalıplar renk kodları ve ikonlar temizlendikten sonra uygulanır. */
object ChatParser {
    private val formatting = Regex("§.")
    // Hypixel'in özel font ikonları (private use) ve görünmez biçim karakterleri
    private val glyphs = Regex("[\\p{Cf}\\p{Co}]")
    private const val RANK = """(?:\[[^\]\r\n]+]\s*)?"""
    private const val IGN = """([A-Za-z0-9_]{1,16})"""

    fun clean(text: String) = text.replace(formatting, "").replace(glyphs, "").trim()

    // CAPTURE! You caught a Foxtrot and gained a Foxtrot Shard!
    // CAPTURE! You caught a Foxtrot and gained 2x Foxtrot Shard!
    // (Hideyho gibi özel critter'larda "found the ..." olabiliyor)
    // CAPTURE! You found the Hideyho, and as a reward it gave you 3x Hideyho Shard!
    private val capture = Regex("""^CAPTURE! You (?:caught|found|captured|snagged) (?:an? |the )?(.+?),? and (?:gained|as a reward)\b.*$""")

    // LOOT SHARE! You received a Strongarm Shard from Steve catching a Strongarm!
    // LOOT SHARE! You received 3x Hideyho Shard from Steve finding the Hideyho!
    private val lootShare = Regex("""^LOOT SHARE! You received .+? from $RANK$IGN (?:catching|finding|capturing|snagging) (?:an? |the )?(.+?)!$""")

    // SPARKLING! Alex caught a SPARKLING Foxtrot!
    private val sparkling = Regex("""^SPARKLING! $RANK$IGN (?:caught|found|captured) (?:an? |the )?SPARKLING (.+?)!$""")

    /** Kalıp tutmazsa yedek: satırda geçen bilinen bir critter adını bul (uzun isimler önce). */
    private val namesLongestFirst = Critters.all.map { it.name }.sortedByDescending { it.length }
    fun findCritter(text: String): String? = namesLongestFirst.firstOrNull { text.contains(it, ignoreCase = true) }
    private val lootFrom = Regex("""\bfrom $RANK$IGN\b""")

    // [MVP+] Alex entered Critter Safari!
    private val entered = Regex("""^$RANK$IGN entered Critter Safari!$""")

    // HOTSPOT! Your Hunting Hotspot is the Haunted Biome!
    private val hotspot = Regex("""^HOTSPOT! Your Hunting Hotspot is the (\w+) Biome!?$""", RegexOption.IGNORE_CASE)

    sealed interface Event
    data class Capture(val critter: String) : Event
    data class LootShare(val player: String, val critter: String) : Event
    data class SparklingCaught(val player: String, val critter: String) : Event
    data class Entered(val leader: String) : Event
    data object RewardSummary : Event
    data class Hotspot(val biome: String) : Event

    /** Bir mesaj birden fazla satır içerebilir (çizgili çerçeveler); her satıra ayrı bakılır. */
    fun parse(raw: String): List<Event> {
        val out = ArrayList<Event>()
        for (line in raw.split('\n')) {
            val l = clean(line)
            if (l.isEmpty()) continue
            if (l.startsWith("CAPTURE!")) {
                val name = capture.matchEntire(l)?.groupValues?.get(1)?.let { findCritter(it) } ?: findCritter(l)
                if (name != null) out.add(Capture(name))
                continue
            }
            if (l.startsWith("LOOT SHARE!")) {
                val m = lootShare.matchEntire(l)
                val player = m?.groupValues?.get(1) ?: lootFrom.find(l)?.groupValues?.get(1)
                val name = m?.groupValues?.get(2)?.let { findCritter(it) } ?: findCritter(l)
                if (player != null && name != null) out.add(LootShare(player, name))
                continue
            }
            sparkling.matchEntire(l)?.let { m -> findCritter(m.groupValues[2])?.let { out.add(SparklingCaught(m.groupValues[1], it)) }; continue }
            entered.matchEntire(l)?.let { out.add(Entered(it.groupValues[1])); continue }
            hotspot.matchEntire(l)?.let { out.add(Hotspot(it.groupValues[1])); continue }
            if (l.contains("SAFARI REWARD SUMMARY", ignoreCase = true)) out.add(RewardSummary)
        }
        return out
    }
}
