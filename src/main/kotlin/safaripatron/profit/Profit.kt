package safaripatron.profit

import net.minecraft.client.Minecraft
import safaripatron.config.Config
import safaripatron.data.Critters
import safaripatron.safari.ChatParser

/**
 * Session and total profit: shards (captures and loot shares), Rainbow Feathers and the Safari Essence
 * from each SAFARI REWARD SUMMARY, valued at Bazaar prices.
 *
 * Items are stored, not coins, so the values follow the current Bazaar price.
 * A session starts when a run starts; a new one begins after 30 minutes away (or with "New session").
 */
object Profit {
    private const val SESSION_GAP_MS = 30 * 60 * 1000L
    private const val SUMMARY = "SAFARI REWARD SUMMARY"

    private val shardRe = Regex("""(?:(\d+)x |\ban? )([A-Z][A-Za-z' ]*?) Shard\b""")
    private val featherRe = Regex("""(?:(\d+)x |\ban? )Rainbow Feather\b""")
    private val essenceRe = Regex("""^\+([0-9,]+) Safari Essence$""")
    private const val MACAW_BONUS = "MACAW! You found a bonus Rainbow Feather!"

    /** Items of the run in progress (not saved; a restart mid-run loses only this run's part). */
    val run = LinkedHashMap<String, Long>()
    private var finishAt = 0L
    private var runStartAt = 0L
    private var lastSave = 0L

    private val d get() = Config.s.profit

    // ---------- chat ----------

    fun onMessage(raw: String) {
        if (!Config.s.profitTracker) return
        for (line in ChatParser.clean(raw).split('\n').map { it.trim() }) onLine(line)
    }

    private fun onLine(line: String) {
        if (line.isEmpty()) return
        when {
            line == SUMMARY -> finishAt = System.currentTimeMillis() + 2000 // essence line follows; finish shortly
            finishAt > 0 && essenceRe.matches(line) ->
                essenceRe.matchEntire(line)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()?.let { add(Bazaar.ESSENCE, it) }
            line == MACAW_BONUS -> add(Bazaar.FEATHER, 1)
            line.startsWith("CAPTURE!") || line.startsWith("LOOT SHARE!") -> {
                for (m in shardRe.findAll(line)) {
                    val name = m.groupValues[2].trim()
                    if (Critters.named(name) == null) continue
                    add(Bazaar.shardId(name), m.groupValues[1].toLongOrNull() ?: 1)
                }
                featherRe.find(line)?.let { add(Bazaar.FEATHER, it.groupValues[1].toLongOrNull() ?: 1) }
            }
        }
    }

    private fun add(id: String, count: Long) {
        if (count <= 0) return
        run.merge(id, count) { a, b -> a + b }
        Bazaar.ensureFresh()
    }

    // ---------- run / session ----------

    /** A Safari run started: the session (and its clock) starts here, not at the first catch. */
    fun onRunStart() {
        if (!Config.s.profitTracker) return
        val now = System.currentTimeMillis()
        if (d.sessionStart == 0L || now - d.lastActive > SESSION_GAP_MS) newSession(save = false)
        runStartAt = now
        d.lastActive = now
        Bazaar.ensureFresh()
    }

    fun tick(client: Minecraft, inSafari: Boolean) {
        if (!Config.s.profitTracker) return
        val now = System.currentTimeMillis()
        if (finishAt in 1..now) { finishAt = 0; finishRun() }
        if (!inSafari || client.player == null || d.sessionStart == 0L) return
        d.lastActive = now
        Bazaar.ensureFresh()
        if (now - lastSave > 60_000) { lastSave = now; Config.save() }
    }

    /** Reward summary: the run's items move into the session and the total, and a new run begins. */
    private fun finishRun() {
        for ((id, n) in run) {
            d.sessionItems.merge(id, n) { a, b -> a + b }
            d.totalItems.merge(id, n) { a, b -> a + b }
        }
        run.clear()
        if (runStartAt > 0) d.sessionRunMs += System.currentTimeMillis() - runStartAt
        runStartAt = 0
        d.sessionRuns++
        d.totalRuns++
        Config.save()
    }

    fun newSession(save: Boolean = true) {
        d.sessionStart = System.currentTimeMillis()
        d.lastActive = d.sessionStart
        d.sessionRuns = 0
        d.sessionRunMs = 0
        d.sessionItems.clear()
        if (save) Config.save()
    }

    fun resetTotal() {
        d.totalItems.clear(); d.totalRuns = 0
        Config.save()
    }

    // ---------- values ----------

    fun value(items: Map<String, Long>): Double {
        val offer = Config.s.profitSellOffer
        return items.entries.sumOf { (id, n) -> n * Bazaar.price(id, offer) }
    }

    enum class Kind { SHARD, ESSENCE, FEATHER }
    fun kind(id: String) = when (id) { Bazaar.ESSENCE -> Kind.ESSENCE; Bazaar.FEATHER -> Kind.FEATHER; else -> Kind.SHARD }

    /** Session items so far: finished runs plus the run in progress. */
    fun sessionItems(): Map<String, Long> {
        val all = LinkedHashMap(d.sessionItems)
        for ((id, n) in run) all.merge(id, n) { a, b -> a + b }
        return all
    }

    fun totalItems(): Map<String, Long> {
        val all = LinkedHashMap(d.totalItems)
        for ((id, n) in run) all.merge(id, n) { a, b -> a + b }
        return all
    }

    fun valueOf(items: Map<String, Long>, kind: Kind) = value(items.filterKeys { kind(it) == kind })

    fun itemValue(id: String, n: Long) = n * Bazaar.price(id, Config.s.profitSellOffer)

    /** "Hideonwall Shard", "Rainbow Feather", "Safari Essence" */
    fun itemName(id: String) = when (id) {
        Bazaar.ESSENCE -> "Safari Essence"
        Bazaar.FEATHER -> "Rainbow Feather"
        else -> (Critters.all.firstOrNull { Bazaar.shardId(it.name) == id }?.name
            ?: id.removePrefix("SHARD_").lowercase().split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }) + " Shard"
    }

    val avgRunMs get() = if (d.sessionRuns > 0) d.sessionRunMs / d.sessionRuns else 0L

    val runValue get() = value(run)
    val finishedValue get() = value(d.sessionItems)
    val sessionValue get() = finishedValue + runValue
    val totalValue get() = value(d.totalItems) + runValue
    val perRun get() = if (d.sessionRuns > 0) finishedValue / d.sessionRuns else 0.0
    val sessionMs get() = if (d.sessionStart == 0L) 0L else (d.lastActive - d.sessionStart).coerceAtLeast(0)
    val perHour get() = sessionMs.takeIf { it >= 60_000 }?.let { sessionValue / (it / 3_600_000.0) } ?: 0.0

    fun coins(v: Double): String = when {
        v >= 1e9 -> "%.2fB".format(v / 1e9)
        v >= 1e6 -> "%.2fM".format(v / 1e6)
        v >= 1e3 -> "%.1fk".format(v / 1e3)
        else -> "%.0f".format(v)
    }

    /** 12.3M — one decimal, for numbers that change often */
    fun coinsShort(v: Double): String = when {
        v >= 1e9 -> "%.1fB".format(v / 1e9)
        v >= 1e6 -> "%.1fM".format(v / 1e6)
        v >= 1e3 -> "%.0fk".format(v / 1e3)
        else -> "%.0f".format(v)
    }

    /** 4:12 */
    fun clock(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

    fun duration(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }
}
