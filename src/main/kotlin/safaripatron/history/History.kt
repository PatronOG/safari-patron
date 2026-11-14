package safaripatron.history

import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import safaripatron.config.Paths
import safaripatron.data.Biome
import safaripatron.data.Critters
import safaripatron.safari.ChatParser
import safaripatron.safari.Safari

/** One finished Safari run, as saved in config/safari-patron/history.json. */
class RunRecord {
    var id = 0
    /** When the reward summary came (epoch ms). */
    var endedAt = 0L
    /** Ticket to reward summary; 0 = not timed (no ticket seen). */
    var ms = 0L
    /** You (whose game this is). */
    var self = ""
    /** Unique species the party caught / you caught. */
    var party = 0
    var mine = 0
    /** Critters you caught (scoreboard "Captured Mobs" when known). */
    var caught = 0
    /** Coins at the prices of that moment. */
    var value = 0.0
    var items: MutableMap<String, Long> = LinkedHashMap()
    var sparklings: MutableList<SparklingRecord> = ArrayList()
    /** Player -> the species they caught, in order. */
    var players: MutableMap<String, MutableList<String>> = LinkedHashMap()
    /** Time from the ticket until each biome was done: FOREST, CAVERN, ICY, HAUNTED, allButMacaw, all. */
    var biomeMs: MutableMap<String, Long> = LinkedHashMap()
    var hotspot: String? = null
    /** Fastest timed run so far when it finished. */
    var pb = false
    /** A sample run added with /sp history demo (removed with /sp history demo clear). */
    var demo = false
}

class SparklingRecord(var player: String = "", var critter: String = "")

/** Shards you got per critter: how many catches gave shards and how many shards in total. */
class CritterStat(var events: Long = 0, var shards: Long = 0) {
    val average get() = if (events > 0) shards.toDouble() / events else 0.0
}

class HistoryData {
    var version = 1
    var nextId = 1
    var runs: MutableList<RunRecord> = ArrayList()
    var critters: MutableMap<String, CritterStat> = LinkedHashMap()
    var bestMs = 0L
}

/**
 * Run history: built from chat while a run is going (ticket, catches, loot shares, sparklings, hotspot)
 * and saved when the SAFARI REWARD SUMMARY is counted by the profit tracker.
 * Independent of the run panel's timer, which other events can restart.
 */
object History {
    private val log = LoggerFactory.getLogger("safari-patron/history")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private const val MAX_RUNS = 5000
    private const val MIN_RUN_MS = 10_000L
    private const val MAX_RUN_MS = 3_600_000L

    var data = HistoryData()
        private set

    /** The run being built. */
    private var current: RunRecord? = null
    private var startAt = 0L
    private val caughtSet = LinkedHashSet<String>()
    private var captures = 0

    fun init() {
        val raw = Paths.read(Paths.history)
        data = runCatching { raw?.let { gson.fromJson(it, HistoryData::class.java) } }.getOrNull() ?: HistoryData()
        @Suppress("SENSELESS_COMPARISON")
        run {
            if (data.runs == null) data.runs = ArrayList()
            if (data.critters == null) data.critters = LinkedHashMap()
            data.runs.removeAll { it == null }
        }
        if (raw != null) log.info("Loaded {} runs", data.runs.size)
    }

    fun save() = Paths.write(Paths.history, gson.toJson(data))

    // ---------- building a run ----------

    private fun begin(at: Long) {
        current = RunRecord()
        startAt = at
        caughtSet.clear()
        captures = 0
    }

    /** The Safari Manager took the ticket. A run still open without a summary is dropped. */
    fun onTicket(now: Long) {
        if (current != null && caughtSet.isNotEmpty()) log.info("New ticket before a reward summary: unfinished run not saved")
        begin(now)
    }

    fun onEvent(event: ChatParser.Event, self: String) {
        when (event) {
            is ChatParser.Capture -> { captures++; add(self, event.critter) }
            is ChatParser.LootShare -> add(event.player, event.critter)
            is ChatParser.SparklingCaught -> {
                open().sparklings.add(SparklingRecord(event.player, Critters.named(event.critter)?.name ?: event.critter))
                add(event.player, event.critter)
            }
            is ChatParser.Hotspot -> open().hotspot = event.biome.lowercase().replaceFirstChar { it.uppercase() }
            else -> {}
        }
    }

    /** Shards from one CAPTURE / LOOT SHARE line (for the expected value per catch). */
    fun onShards(critter: String, count: Long) {
        if (count <= 0) return
        val name = Critters.named(critter)?.name ?: return
        val stat = data.critters.getOrPut(name) { CritterStat() }
        stat.events++
        stat.shards += count
    }

    private fun open(): RunRecord = current ?: run { begin(0L); current!! }

    private fun add(player: String, critter: String) {
        val name = Critters.named(critter)?.name ?: return
        val rec = open()
        val list = rec.players.getOrPut(player) { ArrayList() }
        if (name !in list) list.add(name)
        if (!caughtSet.add(name) || startAt <= 0L) return
        // A biome (or everything) is done: remember how long it took from the ticket
        val elapsed = System.currentTimeMillis() - startAt
        for (b in Biome.entries) {
            if (b.name !in rec.biomeMs && b.critters.all { it.name in caughtSet }) rec.biomeMs[b.name] = elapsed
        }
        if (caughtSet.size >= Critters.TOTAL) rec.biomeMs.putIfAbsent("all", elapsed)
        else if (caughtSet.size == Critters.TOTAL - 1 && Critters.MACAW !in caughtSet) rec.biomeMs.putIfAbsent("allButMacaw", elapsed)
    }

    /** SAFARI REWARD SUMMARY: the run ends here (its items are added a moment later by [finish]). */
    fun onSummary(now: Long) {
        val rec = open()
        val ms = if (startAt > 0) now - startAt else 0L
        rec.ms = if (ms in MIN_RUN_MS..MAX_RUN_MS) ms else 0L
        rec.caught = Safari.capturedMobs?.takeIf { it > 0 } ?: captures
        rec.endedAt = now
    }

    /** The profit tracker counted the run's items: save the run. */
    fun finish(items: Map<String, Long>, value: Double, self: String?) {
        val rec = current ?: return
        current = null
        if (items.isEmpty() && caughtSet.isEmpty()) return
        if (rec.endedAt == 0L) rec.endedAt = System.currentTimeMillis()
        rec.id = data.nextId++
        rec.self = self ?: ""
        rec.party = caughtSet.size
        rec.mine = self?.let { rec.players[it]?.size } ?: 0
        rec.items = LinkedHashMap(items)
        rec.value = value
        if (rec.ms > 0 && (data.bestMs <= 0 || rec.ms < data.bestMs)) { data.bestMs = rec.ms; rec.pb = true }
        data.runs.add(rec)
        while (data.runs.size > MAX_RUNS) data.runs.removeAt(0)
        caughtSet.clear()
        startAt = 0L
        save()
        log.info("Saved run #{} ({} uniques, {})", rec.id, rec.party, if (rec.ms > 0) safaripatron.safari.Run.time(rec.ms) else "not timed")
    }

    // ---------- sample runs for trying the history screen ----------

    /** Adds two finished sample runs (every biome done, a sparkling and an Emerald Dye; the first one marked fastest). */
    fun addDemo(self: String) {
        val now = System.currentTimeMillis()
        val mates = listOf(self, "z1rr31", "anayinam")
        fun sample(ms: Long, pb: Boolean, ago: Long, sparkling: SparklingRecord, biomeMs: List<Long>): RunRecord {
            val rec = RunRecord()
            rec.id = data.nextId++
            rec.demo = true
            rec.self = self
            rec.endedAt = now - ago
            rec.ms = ms
            rec.pb = pb
            // Every species, shared out between three players
            Critters.all.forEachIndexed { i, c -> rec.players.getOrPut(mates[i % 3]) { ArrayList() }.add(c.name) }
            rec.party = Critters.TOTAL
            rec.mine = rec.players[self]?.size ?: 0
            rec.caught = 41
            Biome.entries.forEachIndexed { i, b -> rec.biomeMs[b.name] = biomeMs[i] }
            rec.biomeMs["allButMacaw"] = biomeMs.max() - 9_000
            rec.biomeMs["all"] = biomeMs.max()
            rec.hotspot = "Haunted"
            rec.sparklings.add(sparkling)
            // One shard of every critter, plus a feather, the essence and an Emerald Dye
            rec.items = LinkedHashMap<String, Long>().apply {
                for (c in Critters.all) put(safaripatron.profit.Bazaar.shardId(c.name), 1L)
                put(safaripatron.profit.Bazaar.FEATHER, 1L)
                put(safaripatron.profit.Bazaar.ESSENCE, 412L)
                put(safaripatron.profit.Bazaar.DYE, 1L)
            }
            rec.value = safaripatron.profit.Profit.value(rec.items)
            return rec
        }
        data.runs.add(sample(161_000, true, 10 * 60_000, SparklingRecord(self, "Wumpa"), listOf(48_000, 92_000, 131_000, 161_000)))
        data.runs.add(sample(215_000, false, 4 * 60_000, SparklingRecord("z1rr31", "Doomspiral"), listOf(61_000, 118_000, 167_000, 215_000)))
        save()
    }

    /** Removes the sample runs; returns how many. */
    fun clearDemo(): Int {
        val before = data.runs.size
        data.runs.removeAll { it.demo }
        save()
        return before - data.runs.size
    }

    /** Expected shards per catch of a critter: your own average once there are 5 catches, else 1. */
    fun shardsPerCatch(critter: String): Pair<Double, Boolean> {
        val stat = data.critters[critter]
        return if (stat != null && stat.events >= 5) stat.average to true else 1.0 to false
    }
}
