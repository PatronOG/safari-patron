package safaripatron.history

import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import safaripatron.config.Paths
import safaripatron.data.Biome
import safaripatron.data.Critters
import safaripatron.safari.ChatParser
import safaripatron.safari.Safari
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

/** One finished Safari run, as saved in config/safari-patron/history/. */
class RunRecord {
    var id = 0
    /** When the reward summary came (epoch ms). */
    var endedAt = 0L
    /** Safari arrival to reward summary (before 2.2: ticket to summary); 0 = not timed. */
    var ms = 0L
    /** You (whose game this is). */
    var self = ""
    /** Unique species the party caught / you caught. */
    var party = 0
    var mine = 0
    /** Critters you caught (scoreboard "Captured Mobs" when known). */
    var caught = 0
    /** Coins at the Instant Sell prices of that moment (before 2.2: at whatever Price setting was on). */
    var value = 0.0
    /** Coins at the Sell Offer prices of that moment. */
    var offer = 0.0
    /** 0 = older run with one value, 1 = some item had no price yet, 2 = both values are done. */
    var priced = 0
    var items: MutableMap<String, Long> = LinkedHashMap()
    var sparklings: MutableList<SparklingRecord> = ArrayList()
    /** Player -> the species they caught, in order. */
    var players: MutableMap<String, MutableList<String>> = LinkedHashMap()
    /** Time from the run's start until each biome was done: FOREST, CAVERN, ICY, HAUNTED, allButMacaw, all. */
    var biomeMs: MutableMap<String, Long> = LinkedHashMap()
    var hotspot: String? = null
    /** Fastest timed run so far when it finished. */
    var pb = false

    /** Coins at Sell Offer ([sellOffer]) or Instant Sell prices. */
    fun valueAt(sellOffer: Boolean) = if (sellOffer) offer else value
}

class SparklingRecord(var player: String = "", var critter: String = "")

/** Shards you got per critter: how many catches gave shards and how many shards in total. */
class CritterStat(var events: Long = 0, var shards: Long = 0) {
    val average get() = if (events > 0) shards.toDouble() / events else 0.0
}

/** history.json from 1.2.0: every run in one file. Only read once, to move it into history/. */
class HistoryData {
    var version = 1
    var nextId = 1
    var runs: MutableList<RunRecord> = ArrayList()
    var critters: MutableMap<String, CritterStat> = LinkedHashMap()
    var bestMs = 0L
}

/** One file of history/: up to [History.CHUNK] runs, ids [firstId]..[lastId]. */
class ChunkInfo(var file: String = "", var firstId: Int = 0, var lastId: Int = 0, var count: Int = 0)

/** history/index.json: the list of run files and the all-time totals. */
class HistoryIndex {
    var version = 2
    var nextId = 1
    var nextChunk = 1
    var bestMs = 0L
    var critters: MutableMap<String, CritterStat> = LinkedHashMap()
    var chunks: MutableList<ChunkInfo> = ArrayList()
    var totals = Totals()
    /** Every run has both values (Instant Sell and Sell Offer), from 2.2 on. */
    var valuesDone = false
}

class ChunkFile(var runs: MutableList<RunRecord> = ArrayList())

/**
 * Run history: built from chat while a run is going (ticket, catches, loot shares, sparklings, hotspot)
 * and saved when the SAFARI REWARD SUMMARY is counted by the profit tracker.
 * Independent of the run panel's timer, which other events can restart.
 *
 * Saved in config/safari-patron/history/: runs-0001.json, runs-0002.json … with [CHUNK] runs each,
 * and index.json with the all-time totals. Only the newest file is loaded at start and only it is written
 * after a run; older files are loaded when the Run History asks for them. Files are written in the background.
 */
object History {
    private val log = LoggerFactory.getLogger("safari-patron/history")
    private val gson = GsonBuilder().create()
    const val CHUNK = 1000
    const val MAX_RUNS = 20_000
    private const val MIN_RUN_MS = 10_000L
    private const val MAX_RUN_MS = 3_600_000L

    private val dir = Paths.historyDir
    private val indexFile = dir.resolve("index.json")
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "safari-patron-history").apply { isDaemon = true } }

    var index = HistoryIndex()
        private set
    /** The loaded runs, oldest first: the newest files of history/. */
    val runs: MutableList<RunRecord> = ArrayList()
    /** Index in [HistoryIndex.chunks] of the oldest loaded file. */
    private var loadedFrom = 0
    /** Goes up whenever [runs] or the totals change, so screens know when to work their numbers out again. */
    var version = 0
        private set

    val totals get() = index.totals
    val totalRuns get() = index.chunks.sumOf { it.count }
    /** Older runs are saved but not loaded. */
    val moreToLoad get() = loadedFrom > 0
    val olderCount get() = index.chunks.take(loadedFrom).sumOf { it.count }

    /** The run being built. */
    private var current: RunRecord? = null

    /** Every run has both values (see [fixValues]). */
    val valuesDone get() = index.valuesDone

    /** This run's Hunting Hotspot biome (e.g. "Haunted"), if it was seen. */
    fun currentHotspot(): String? = current?.hotspot
    private val caughtSet = LinkedHashSet<String>()
    private var captures = 0

    // ---------- files ----------

    fun init() {
        val raw = Paths.read(indexFile)
        val loaded = raw?.let { runCatching { gson.fromJson(it, HistoryIndex::class.java) }.getOrNull() }
        when {
            loaded != null -> { index = fixNulls(loaded); loadNewest() }
            raw != null -> rebuildFromFiles()
            Files.exists(Paths.history) -> migrateV1()
            else -> index = HistoryIndex()
        }
        if (totalRuns > 0) log.info("Loaded {} of {} runs", runs.size, totalRuns)
    }

    @Suppress("SENSELESS_COMPARISON")
    private fun fixNulls(i: HistoryIndex): HistoryIndex {
        if (i.critters == null) i.critters = LinkedHashMap()
        if (i.chunks == null) i.chunks = ArrayList()
        i.chunks.removeAll { it == null }
        if (i.totals == null) i.totals = Totals()
        i.totals.fixNulls()
        return i
    }

    /** The newest file, and the one before it when the newest has fewer than 50 runs (for graphs and "Last 50"). */
    private fun loadNewest() {
        runs.clear()
        loadedFrom = index.chunks.size
        if (loadedFrom == 0) return
        loadOlder()
        if (runs.size < 50) loadOlder()
    }

    private fun readChunk(info: ChunkInfo): List<RunRecord> {
        val raw = Paths.read(dir.resolve(info.file)) ?: return emptyList()
        val file = runCatching { gson.fromJson(raw, ChunkFile::class.java) }.getOrNull() ?: return emptyList()
        @Suppress("SENSELESS_COMPARISON")
        return (file.runs ?: ArrayList()).filter { it != null }.onEach { r ->
            if (r.items == null) r.items = LinkedHashMap()
            if (r.sparklings == null) r.sparklings = ArrayList()
            if (r.players == null) r.players = LinkedHashMap()
            if (r.biomeMs == null) r.biomeMs = LinkedHashMap()
        }.sortedBy { it.id }
    }

    /** Loads the next older file; false if there is none. */
    fun loadOlder(): Boolean {
        if (loadedFrom <= 0) return false
        loadedFrom--
        runs.addAll(0, readChunk(index.chunks[loadedFrom]))
        version++
        return true
    }

    /** Loads older files until the oldest loaded run ended before [epochMs] (or everything is loaded). */
    fun ensureSince(epochMs: Long) {
        while (moreToLoad && (runs.firstOrNull()?.endedAt ?: 0L) >= epochMs) loadOlder()
    }

    /** Loads older files until at least [count] runs are loaded. */
    fun ensureCount(count: Int) {
        while (moreToLoad && runs.size < count) loadOlder()
    }

    /** Loads older files until run [id] is loaded. */
    fun ensureId(id: Int) {
        while (moreToLoad && (runs.firstOrNull()?.id ?: 0) > id) loadOlder()
    }

    /** A loaded run by id. */
    fun byId(id: Int): RunRecord? {
        val i = runs.binarySearchBy(id) { it.id }
        return if (i >= 0) runs[i] else null
    }

    private fun write(name: String, text: String) {
        val path = dir.resolve(name)
        writer.execute { Paths.write(path, text) }
    }

    private fun saveIndex() = write("index.json", gson.toJson(index))

    /** Writes one file of history/ (it has to be loaded). The JSON is made here, the writing is done in the background. */
    private fun saveChunk(i: Int) {
        val info = index.chunks.getOrNull(i) ?: return
        if (i < loadedFrom) return
        val list = runs.filter { it.id in info.firstId..info.lastId }
        write(info.file, gson.toJson(ChunkFile(ArrayList(list))))
    }

    fun save() = saveIndex()

    private fun chunkOf(id: Int) = index.chunks.indexOfFirst { id in it.firstId..it.lastId }

    private fun newChunk(firstId: Int): ChunkInfo {
        val info = ChunkInfo(String.format(java.util.Locale.ROOT, "runs-%04d.json", index.nextChunk++), firstId, firstId, 0)
        index.chunks.add(info)
        return info
    }

    /** Adds a finished run: into the newest file (a new one every [CHUNK] runs), the totals, and saves both. */
    private fun append(rec: RunRecord, save: Boolean = true) {
        var info = index.chunks.lastOrNull()
        if (info == null || info.count >= CHUNK) info = newChunk(rec.id)
        info.lastId = rec.id
        info.count++
        runs.add(rec)
        index.totals.add(rec)
        // Over the limit: the oldest file goes (the all-time totals keep counting its runs)
        while (totalRuns > MAX_RUNS && index.chunks.size > 1) {
            val old = index.chunks.removeAt(0)
            runs.removeAll { it.id <= old.lastId }
            loadedFrom = (loadedFrom - 1).coerceAtLeast(0)
            val path = dir.resolve(old.file)
            writer.execute { runCatching { Files.deleteIfExists(path) } }
        }
        version++
        if (save) { saveChunk(index.chunks.size - 1); saveIndex() }
    }

    /**
     * Writes [all] runs again as new files and works the totals out again. Used to move 1.2.0's history.json
     * into history/ and when index.json is broken. Everything stays loaded.
     */
    fun rewrite(all: List<RunRecord>) {
        val oldFiles = index.chunks.map { it.file }
        index.chunks.clear()
        index.nextChunk = 1
        index.totals = Totals()
        runs.clear()
        loadedFrom = 0
        for (f in oldFiles) { val path = dir.resolve(f); writer.execute { runCatching { Files.deleteIfExists(path) } } }
        for (r in all.sortedBy { it.id }) append(r, save = false)
        for (i in index.chunks.indices) saveChunk(i)
        saveIndex()
        version++
    }

    /** 1.2.0 kept every run in history.json: split it into history/ and keep a copy in backup-2.0.0/. */
    private fun migrateV1() {
        val raw = Paths.read(Paths.history) ?: return
        val old = runCatching { gson.fromJson(raw, HistoryData::class.java) }.getOrNull() ?: HistoryData()
        @Suppress("SENSELESS_COMPARISON")
        val list = (old.runs ?: ArrayList()).filter { it != null }
        index = HistoryIndex().also {
            it.nextId = maxOf(old.nextId, (list.maxOfOrNull { r -> r.id } ?: 0) + 1)
            it.bestMs = old.bestMs
            @Suppress("SENSELESS_COMPARISON")
            if (old.critters != null) it.critters = old.critters
        }
        rewrite(list)
        val from = Paths.history
        writer.execute {
            runCatching {
                val backup = Paths.dir.resolve("backup-2.0.0")
                Files.createDirectories(backup)
                Files.copy(from, backup.resolve("history.json"), StandardCopyOption.REPLACE_EXISTING)
                Files.delete(from)
            }.onFailure { log.warn("Could not move history.json: {}", it.message) }
        }
        log.info("Moved {} runs from history.json into history/", list.size)
    }

    /** index.json could not be read: build it again from the run files that are there. */
    private fun rebuildFromFiles() {
        val files = runCatching {
            Files.list(dir).use { s -> s.map { it.fileName.toString() }.filter { it.matches(Regex("runs-\\d+\\.json")) }.sorted().toList() }
        }.getOrDefault(emptyList())
        val all = files.flatMap { readChunk(ChunkInfo(it)) }
        index = HistoryIndex().also { i ->
            i.nextId = (all.maxOfOrNull { it.id } ?: 0) + 1
            i.bestMs = all.filter { it.ms > 0 }.minOfOrNull { it.ms } ?: 0L
        }
        log.warn("history/index.json was broken: rebuilt from {} runs", all.size)
        rewrite(all)
    }

    // ---------- building a run ----------

    private fun begin() {
        current = RunRecord()
        caughtSet.clear()
        captures = 0
    }

    /** A run that had its summary but whose items were not counted yet when the next run started. */
    private var ending: RunRecord? = null
    private var endingCaught: Set<String> = emptySet()

    /** A run started (same moment as the run clock). A run still open without a summary is dropped. */
    fun onRunStart() {
        val rec = current
        if (rec != null && rec.endedAt > 0) { ending = rec; endingCaught = LinkedHashSet(caughtSet) }
        else if (rec != null && caughtSet.isNotEmpty()) log.info("New run before a reward summary: unfinished run not saved")
        begin()
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
        val stat = index.critters.getOrPut(name) { CritterStat() }
        stat.events++
        stat.shards += count
    }

    private fun open(): RunRecord = current ?: run { begin(); current!! }

    private fun add(player: String, critter: String) {
        val name = Critters.named(critter)?.name ?: return
        val rec = open()
        val list = rec.players.getOrPut(player) { ArrayList() }
        if (name !in list) list.add(name)
        caughtSet.add(name)
    }

    /** SAFARI REWARD SUMMARY: the run ends here (its items are added a moment later by [finish]). */
    fun onSummary(now: Long) {
        val rec = open()
        // One clock: the run's own time, and the biome times exactly as they were announced
        safaripatron.safari.Run.end(now)
        val ms = safaripatron.safari.Run.finalMs
        rec.ms = if (ms in MIN_RUN_MS..MAX_RUN_MS) ms else 0L
        rec.biomeMs = if (rec.ms > 0) LinkedHashMap(safaripatron.safari.Run.doneMs) else LinkedHashMap()
        rec.caught = Safari.capturedMobs?.takeIf { it > 0 } ?: captures
        rec.endedAt = now
    }

    /** The profit tracker counted the run's items: save the run. */
    fun finish(items: Map<String, Long>, self: String?) {
        // The next run may already have started (fast warp): finish the one that ended, keep the new one going
        val early = ending
        ending = null
        val rec: RunRecord
        val caught: Set<String>
        if (early != null) { rec = early; caught = endingCaught; endingCaught = emptySet() }
        else { rec = current ?: return; current = null; caught = LinkedHashSet(caughtSet); caughtSet.clear() }
        if (items.isEmpty() && caught.isEmpty()) return
        if (rec.endedAt == 0L) rec.endedAt = System.currentTimeMillis()
        rec.id = index.nextId++
        rec.self = self ?: ""
        rec.party = caught.size
        rec.mine = self?.let { rec.players[it]?.size } ?: 0
        rec.items = LinkedHashMap(items)
        // Both prices are kept: History shows either one, achievements go by Sell Offer
        rec.value = safaripatron.profit.Profit.value(items, offer = false)
        rec.offer = safaripatron.profit.Profit.value(items, offer = true)
        rec.priced = if (safaripatron.profit.Profit.allPriced(items)) 2 else 1
        if (rec.ms > 0 && (index.bestMs <= 0 || rec.ms < index.bestMs)) { index.bestMs = rec.ms; rec.pb = true }
        append(rec)
        fixValues()
        safaripatron.achievements.Achievements.onRun(rec)
        log.info("Saved run #{} ({} uniques, {})", rec.id, rec.party, if (rec.ms > 0) safaripatron.safari.Run.time(rec.ms) else "not timed")
    }

    /**
     * Fills in run values that are missing: a run saved while an item had no price yet, and (once, when
     * the prices are in) the second value of runs from before 2.2, which kept only one. An old run's value
     * is taken to be at the Price setting on now; the other one comes from today's prices.
     * Returns true if anything changed.
     */
    fun fixValues(): Boolean {
        if (!safaripatron.profit.Bazaar.ready()) return false
        // Older runs are only on disk: load them all once to give every run both values
        if (!index.valuesDone) ensureCount(Int.MAX_VALUE)
        val sellOfferOn = safaripatron.config.Config.s.profitSellOffer
        val changed = HashSet<Int>()
        for (r in runs) {
            if (r.priced == 2 || r.items.isEmpty()) continue
            val instant = safaripatron.profit.Profit.value(r.items, offer = false)
            val offer = safaripatron.profit.Profit.value(r.items, offer = true)
            val oldValue = r.value; val oldOffer = r.offer
            if (r.priced == 0) {
                // Keep what was saved for the setting it was most likely made with
                if (sellOfferOn) { r.offer = oldValue.takeIf { it > 0 } ?: offer; r.value = instant }
                else { r.value = oldValue.takeIf { it > 0 } ?: instant; r.offer = offer }
                r.priced = 2
            } else {
                r.value = instant; r.offer = offer
                if (safaripatron.profit.Profit.allPriced(r.items)) r.priced = 2
            }
            if (r.value == oldValue && r.offer == oldOffer && r.priced != 2) continue
            index.totals.valueChanged(r, oldValue, oldOffer)
            changed.add(chunkOf(r.id))
        }
        val first = !index.valuesDone
        index.valuesDone = true
        if (changed.isEmpty() && !first) return false
        version++
        for (i in changed) saveChunk(i)
        saveIndex()
        safaripatron.achievements.Achievements.onValuesChanged()
        return true
    }

    /** Expected shards per catch of a critter: your own average once there are 5 catches, else 1. */
    fun shardsPerCatch(critter: String): Pair<Double, Boolean> {
        val stat = index.critters[critter]
        return if (stat != null && stat.events >= 5) stat.average to true else 1.0 to false
    }
}
