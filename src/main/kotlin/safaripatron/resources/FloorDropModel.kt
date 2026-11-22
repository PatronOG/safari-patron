package safaripatron.resources

import safaripatron.data.Biome
import kotlin.math.floor

enum class DropKind(val label: String, val biome: Biome?, val itemId: String = "", val color: Int = 0xFFAAAAAA.toInt()) {
    LIME("Lime Gem", Biome.CAVERN, "LIME_GEM", 0xFF80FF40.toInt()),
    PURPLE("Purple Gem", Biome.CAVERN, "PURPLE_GEM", 0xFFC080FF.toInt()),
    ORANGE("Orange Gem", Biome.CAVERN, "ORANGE_GEM", 0xFFFFAA40.toInt()),
    WORM("Wriggleworm", Biome.FOREST, "WRIGGLEWORM"),
    BERRY("Yogi Berry", Biome.FOREST, "YOGI_BERRY"),
    SEEDS("Bag of Seeds", Biome.FOREST, "BAG_OF_SEEDS"),
    INCENSE("Soothing Incense", Biome.HAUNTED, "SOOTHING_INCENSE"),
    COIN("Shining Coin", Biome.HAUNTED, "SHINING_COIN"),
    ICEBREAKER("Icebreaker", Biome.ICY, "ICEBREAKER"),
    REWARD("XP / Essence / Shard", null);

    val gem get() = this == LIME || this == PURPLE || this == ORANGE
    val texture get() = "textures/drops/${name.lowercase()}.png"
    companion object {
        fun fromItem(id: String, name: String): DropKind? = entries.firstOrNull {
            it != REWARD && (it.itemId == id || it.label == name)
        }
        fun pickup(text: String): DropKind? {
            if (!text.startsWith("FLOOR DROP! You found ")) return null
            return entries.firstOrNull { it != REWARD &&
                Regex("\\b${Regex.escape(it.label)}\\b(?! Shard)").containsMatchIn(text) }
                ?: REWARD.takeIf { (text.contains("Safari Essence") || text.contains("Hunting XP") || text.contains(" Shard")) &&
                    (text.endsWith("on the ground!") || text.contains(" on the ground! (")) }
        }
    }
}

data class DropPoint(val x: Double, val y: Double, val z: Double) {
    fun distanceSquared(other: DropPoint) = (x-other.x)*(x-other.x)+(y-other.y)*(y-other.y)+(z-other.z)*(z-other.z)
    val block get() = Triple(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
}
data class FloorDisplay(val id: Int, val point: DropPoint, val description: String)
data class FloorGroup(val id: Int, val point: DropPoint, val biome: Biome?, var live: Boolean = true,
                      var kind: DropKind? = null, var collected: Boolean = false)

/** Numeric order is evidence for a layout, never a server-confirmed reward field. */
class FloorDropModel {
    data class Layout(val base: Int, val coins: Int, val ice: Int) {
        fun kind(id: Int): DropKind? {
            val delta = id.toLong() - base
            if (delta < 0) return null
            if (delta >= (20 + coins + ice) * 3L) return DropKind.REWARD
            if (delta % 3 != 0L) return null
            val slot = (delta / 3).toInt()
            return when {
                slot < 6 -> listOf(DropKind.LIME, DropKind.PURPLE, DropKind.ORANGE)[slot % 3]
                slot < 15 -> listOf(DropKind.WORM, DropKind.BERRY, DropKind.SEEDS)[(slot - 6) / 3]
                slot < 20 -> DropKind.INCENSE
                slot < 20 + coins -> DropKind.COIN
                else -> DropKind.ICEBREAKER
            }
        }
    }
    private val groups = sortedMapOf<Int, FloorGroup>()
    private val actual = HashMap<Int, DropKind>()
    private var overflow = false
    var layouts: List<Layout> = emptyList()
        private set
    val knownGroups get() = groups.values.toList()

    fun reset() { groups.clear(); actual.clear(); layouts = emptyList(); overflow = false }

    fun scan(displays: List<FloorDisplay>, biomeAt: (DropPoint) -> Biome?): List<FloorGroup> {
        groups.values.forEach { it.live = false }
        val strings = displays.filter { it.description == "minecraft:string" || it.description.startsWith("minecraft:string:") }
        val byId = strings.associateBy { it.id }
        val consumed = HashSet<Int>()
        for (first in strings.sortedBy { it.id }) {
            if (first.id in consumed || first.id > Int.MAX_VALUE - 2) continue
            val triple = listOf(first, byId[first.id + 1] ?: continue, byId[first.id + 2] ?: continue)
            if (triple.any { it.id in consumed } || triple.map { it.description }.distinct().size != 1 ||
                triple.any { a -> triple.any { b -> a.point.distanceSquared(b.point) > 2.25 } }) continue
            consumed.addAll(triple.map { it.id })
            val point = DropPoint(triple.sumOf { it.point.x } / 3, triple.sumOf { it.point.y } / 3, triple.sumOf { it.point.z } / 3)
            if (groups.size >= 256 && first.id !in groups) { overflow = true; continue }
            groups.getOrPut(first.id) { FloorGroup(first.id, point, biomeAt(point)) }.live = true
        }
        infer()
        return groups.values.filter { it.live && !it.collected }
    }

    /** A caller must bind the pickup to an unambiguous recent interaction first. */
    fun picked(id: Int, kind: DropKind) {
        val group = groups[id] ?: return
        group.collected = true
        actual[id] = kind
        infer()
    }

    private fun infer() {
        groups.values.forEach { it.kind = null }
        val first = groups.values.firstOrNull() ?: return
        // A lone biome cannot distinguish a resource prefix from ordinary reward drops.
        if (overflow || groups.values.mapNotNull { it.biome }.distinct().size < 2 || groups.size < 6) {
            layouts = emptyList(); return
        }
        layouts = buildList {
            for (coins in 3..6) for (ice in 4..5) for (slot in 0 until 20 + coins + ice) {
                val base = first.id.toLong() - slot * 3L
                if (base !in 0..Int.MAX_VALUE.toLong()) continue
                val layout = Layout(base.toInt(), coins, ice)
                val firstKind = layout.kind(first.id)
                if (firstKind == null || firstKind == DropKind.REWARD || firstKind.biome != first.biome) continue
                if (groups.values.all { group ->
                    val kind = layout.kind(group.id)
                    kind != null && (kind == DropKind.REWARD || kind.biome == group.biome) &&
                        (actual[group.id] == null || actual[group.id] == kind)
                }) add(layout)
            }
        }
        for (group in groups.values) group.kind = layouts.map { it.kind(group.id) }.distinct().singleOrNull()
    }
}
