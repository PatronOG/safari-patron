package safaripatron.history

import safaripatron.data.Biome
import safaripatron.data.Critters
import java.time.Instant
import java.time.ZoneId

/** Time and clears of one biome (or "all" for Everything) over many runs. */
class BiomeTotals(var sumMs: Long = 0L, var timed: Int = 0, var best: Long = 0L, var cleared: Int = 0) {
    val average get() = if (timed > 0) sumMs / timed else 0L
}

class PlayerTotals(var name: String = "", var runs: Int = 0, var sparklings: Int = 0)

class DayTotals(var runs: Int = 0, var lastId: Int = 0)

/**
 * Everything the Stats tab and the History cards show, added up run by run.
 * The all-time totals are kept in history/index.json, so the old runs never have to be loaded to show them.
 */
class Totals {
    var runs = 0
    var timed = 0
    var sumMs = 0L
    var sumValue = 0.0
    var sumMine = 0L

    var fastestId = 0
    var fastestMs = 0L
    var richestId = 0
    var richestValue = 0.0
    var sparklyId = 0
    var sparklyCount = 0
    var sparklyYours = 0

    var sparklings = 0
    var yourSparklings = 0
    /** Biome name -> sparklings caught there. */
    var sparklingBiomes: MutableMap<String, Int> = LinkedHashMap()
    /** Runs since the last sparkling, and the longest such streak. */
    var dry = 0
    var longestDry = 0

    /** FOREST, CAVERN, ICY, HAUNTED and "all". */
    var biomes: MutableMap<String, BiomeTotals> = LinkedHashMap()
    /** Local day (epoch day) -> runs that day. */
    var days: MutableMap<String, DayTotals> = LinkedHashMap()
    /** Lower-case name -> runs together and their sparklings (never you). */
    var players: MutableMap<String, PlayerTotals> = LinkedHashMap()

    /** Gson leaves fields missing from an old file null. */
    @Suppress("SENSELESS_COMPARISON")
    fun fixNulls(): Totals {
        if (sparklingBiomes == null) sparklingBiomes = LinkedHashMap()
        if (biomes == null) biomes = LinkedHashMap()
        if (days == null) days = LinkedHashMap()
        if (players == null) players = LinkedHashMap()
        return this
    }

    fun add(r: RunRecord) {
        runs++
        sumValue += r.value
        sumMine += r.mine
        if (r.ms > 0) {
            timed++
            sumMs += r.ms
            if (fastestMs <= 0 || r.ms < fastestMs) { fastestMs = r.ms; fastestId = r.id }
        }
        if (richestId == 0 || r.value > richestValue) { richestValue = r.value; richestId = r.id }

        // Biomes: time to clear and how often
        val caught = r.players.values.flatten().toHashSet()
        for (b in Biome.entries) {
            val t = biomes.getOrPut(b.name) { BiomeTotals() }
            r.biomeMs[b.name]?.let { ms -> t.sumMs += ms; t.timed++; if (t.best <= 0 || ms < t.best) t.best = ms }
            if (b.name in r.biomeMs || b.critters.all { it.name in caught }) t.cleared++
        }
        val all = biomes.getOrPut("all") { BiomeTotals() }
        r.biomeMs["all"]?.let { ms -> all.sumMs += ms; all.timed++; all.cleared++; if (all.best <= 0 || ms < all.best) all.best = ms }

        // Sparklings
        val yours = r.sparklings.count { it.player.equals(r.self, ignoreCase = true) }
        sparklings += r.sparklings.size
        yourSparklings += yours
        for (s in r.sparklings) Critters.named(s.critter)?.biome?.let { sparklingBiomes.merge(it.name, 1, Int::plus) }
        if (r.sparklings.isEmpty()) { dry++; longestDry = maxOf(longestDry, dry) } else dry = 0
        if (r.sparklings.isNotEmpty() && r.sparklings.size >= sparklyCount) {
            sparklyCount = r.sparklings.size; sparklyYours = yours; sparklyId = r.id
        }

        // Runs per day
        val day = Instant.ofEpochMilli(r.endedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay().toString()
        val d = days.getOrPut(day) { DayTotals() }
        d.runs++
        d.lastId = r.id

        // Played with
        val here = HashSet<String>()
        for (name in r.players.keys) {
            if (name.equals(r.self, ignoreCase = true)) continue
            val key = name.lowercase()
            if (!here.add(key)) continue
            val p = players.getOrPut(key) { PlayerTotals() }
            p.name = name
            p.runs++
        }
        for (s in r.sparklings) {
            if (s.player.equals(r.self, ignoreCase = true)) continue
            players[s.player.lowercase()]?.let { it.sparklings++ }
        }
    }

    /** A run's value was worked out again (it was saved as 0 while no prices were known). */
    fun valueChanged(r: RunRecord, old: Double) {
        sumValue += r.value - old
        if (r.value > richestValue) { richestValue = r.value; richestId = r.id }
    }

    fun biome(key: String): BiomeTotals = biomes[key] ?: BiomeTotals()

    val averageMs get() = if (timed > 0) sumMs.toDouble() / timed else Double.NaN
    val averageValue get() = if (runs > 0) sumValue / runs else Double.NaN
    val averageMine get() = if (runs > 0) sumMine.toDouble() / runs else Double.NaN
    val clearedPercent get() = if (runs > 0) biome("all").cleared * 100.0 / runs else Double.NaN

    companion object {
        /** Totals of the given runs (oldest first). */
        fun of(runs: List<RunRecord>): Totals = Totals().also { t -> runs.forEach { t.add(it) } }
    }
}
