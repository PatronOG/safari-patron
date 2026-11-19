package safaripatron.profit

import net.minecraft.client.Minecraft
import safaripatron.config.Config
import safaripatron.config.ProfitData
import safaripatron.data.Critters
import safaripatron.safari.ChatParser
import safaripatron.safari.Run

/**
 * Session and total profit: shards (captures and loot shares), Rainbow Feathers and the Safari Essence
 * from each SAFARI REWARD SUMMARY, valued at Bazaar prices.
 *
 * Items are stored, not coins, so the values follow the current Bazaar price.
 * The session starts when the Safari Manager takes your ticket ("I already saw your ticket..."), i.e. when a run
 * really starts. Its clock pauses after 30 seconds outside the Safari and resumes on the next run.
 * Every game start begins a fresh session; Reset (or /sp resetsession) starts one by hand. The total is kept.
 */
object Profit {
    private const val SUMMARY = "SAFARI REWARD SUMMARY"

    private val shardRe = Regex("""(?:(\d+)x |\ban? )([A-Z][A-Za-z' ]*?) Shard\b""")
    private val featherRe = Regex("""(?:(\d+)x |\ban? )Rainbow Feather\b""")
    private val essenceRe = Regex("""^\+([0-9,]+) Safari Essence$""")
    private const val MACAW_BONUS = "MACAW! You found a bonus Rainbow Feather!"
    /** WOW! [MVP+] PatronOG found an Emerald Dye! (a broadcast: only your own name counts) */
    private val dyeRe = Regex("""^WOW! (?:\[[^\]]+]\s*)*([A-Za-z0-9_]{1,16}) found an? Emerald Dye!$""")
    private const val MANAGER = "[NPC] Safari Manager: "
    /** What the Safari Manager says when he takes a ticket and the run starts (he has other lines too). */
    private val TICKET_LINES = setOf(
        "I already saw your ticket, so you're free to go.",
        "Looks good to me. Have fun out there!",
    )
    /** Manager lines that are known and not a run start (no log noise for these). */
    private val OTHER_MANAGER_LINES = setOf(
        "Your friends will need to show me their tickets, though.",
    )
    private const val PAUSE_AFTER_MS = 30_000L

    /** Items of the run in progress (not saved; a restart mid-run loses only this run's part). */
    val run = LinkedHashMap<String, Long>()
    private var finishAt = 0L
    /** Length of the run that just ended, waiting for finishRun (0 = not timed). */
    private var endedRunMs = 0L
    private const val MIN_RUN_MS = 10_000L
    private const val MAX_RUN_MS = 3_600_000L
    private val log = org.slf4j.LoggerFactory.getLogger("safari-patron/profit")
    private var lastTick = 0L
    /** Time outside the Safari not yet counted; counted only if you come back within a minute. */
    private var awayMs = 0L
    private var lastSave = 0L

    /** Saved in its own file (config/safari-patron/profit.json) so updates and settings resets never touch it. */
    private val file = safaripatron.config.Paths.profit
    private val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
    var data = ProfitData()
        private set
    private val d get() = data

    private fun load() {
        data = runCatching {
            if (java.nio.file.Files.exists(file)) gson.fromJson(java.nio.file.Files.readString(file), ProfitData::class.java) else null
        }.getOrNull() ?: run {
            // First start with the separate file: take over what older versions kept in safari-patron.json
            val old = Config.s.profit
            ProfitData().apply { totalItems.putAll(old.totalItems); totalRuns = old.totalRuns }
        }
        @Suppress("SENSELESS_COMPARISON")
        run {
            if (data.sessionItems == null) data.sessionItems = LinkedHashMap()
            if (data.totalItems == null) data.totalItems = LinkedHashMap()
            if (data.prevSessionItems == null) data.prevSessionItems = LinkedHashMap()
        }
    }

    fun save() {
        safaripatron.config.Paths.write(file, gson.toJson(data))
    }

    // ---------- chat ----------

    fun onMessage(raw: String) {
        if (!Config.s.profitTracker) return
        for (line in ChatParser.clean(raw).split('\n').map { it.trim() }) onLine(line)
    }

    private fun onLine(line: String) {
        if (line.isEmpty()) return
        when {
            isTicket(line) -> onRunStart() // the session is normally already going from the Safari arrival
            line == SUMMARY -> {
                val now = System.currentTimeMillis()
                // The run ends right here, not when the items are counted a moment later (same clock as everything)
                safaripatron.history.History.onSummary(now)
                endedRunMs = Run.finalMs
                if (endedRunMs > 0) log.info("Run length (Safari arrival to reward summary): {}", Run.timeTenths(endedRunMs))
                else log.info("Reward summary of an untimed run")
                finishAt = now + 2000 // essence line follows; finish shortly
            }
            finishAt > 0 && essenceRe.matches(line) ->
                essenceRe.matchEntire(line)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()?.let { add(Bazaar.ESSENCE, it) }
            line == MACAW_BONUS -> add(Bazaar.FEATHER, 1)
            line.startsWith("WOW!") -> dyeRe.matchEntire(line)?.let { m ->
                val self = Minecraft.getInstance().player?.name?.string
                if (self != null && m.groupValues[1].equals(self, ignoreCase = true)) add(Bazaar.DYE, 1)
            }
            line.startsWith("CAPTURE!") || line.startsWith("LOOT SHARE!") -> {
                for (m in shardRe.findAll(line)) {
                    val name = m.groupValues[2].trim()
                    if (Critters.named(name) == null) continue
                    val count = m.groupValues[1].toLongOrNull() ?: 1
                    add(Bazaar.shardId(name), count)
                    safaripatron.history.History.onShards(name, count)
                }
                featherRe.find(line)?.let { add(Bazaar.FEATHER, it.groupValues[1].toLongOrNull() ?: 1) }
            }
        }
    }

    /** A run-start line of the Safari Manager; unknown Manager lines are logged so new ones can be added. */
    private fun isTicket(line: String): Boolean {
        if (!line.startsWith(MANAGER)) return false
        // Same text whatever apostrophe the server uses
        val text = line.removePrefix(MANAGER).trim().replace('\u2019', '\'')
        if (text in TICKET_LINES) return true
        if (text !in OTHER_MANAGER_LINES) log.info("Unknown Safari Manager line: {}", text)
        return false
    }

    private fun add(id: String, count: Long) {
        if (count <= 0) return
        if (d.sessionStart == 0L) onRunStart() // ticket message missed: start here instead
        run.merge(id, count) { a, b -> a + b }
        Bazaar.ensureFresh()
    }

    // ---------- run / session ----------

    /** Game start: a fresh session (the total profit is kept). */
    fun init() {
        load()
        // Old copy in safari-patron.json: emptied once moved, so a reset can never bring it back
        val old = Config.s.profit
        if (old.totalItems.isNotEmpty() || old.sessionItems.isNotEmpty() || old.totalRuns > 0) {
            Config.s.profit = ProfitData()
            Config.save()
        }
        archiveSession()
        d.sessionStart = 0L
        d.sessionRuns = 0
        d.sessionRunMs = 0
        d.sessionTimedRuns = 0
        d.sessionActiveMs = 0
        d.sessionItems.clear()
        save()
    }

    /** Keep the session that is about to be cleared, if it had anything, for /sp sessionprofit. */
    private fun archiveSession() {
        if (d.sessionItems.isEmpty() && d.sessionRuns == 0 && run.isEmpty()) return
        d.prevSessionItems = LinkedHashMap(sessionItems())
        d.prevSessionMs = sessionMs
        d.prevSessionRuns = d.sessionRuns
    }

    /** A Safari run started: the session (and its clock) starts here (the Safari arrival), not at the first catch. */
    fun onRunStart(at: Long = System.currentTimeMillis()) {
        if (!Config.s.profitTracker) return
        val now = System.currentTimeMillis()
        if (d.sessionStart == 0L) { d.sessionStart = at; awayMs = 0 }
        d.lastActive = now
        Bazaar.ensureFresh()
    }

    fun tick(client: Minecraft, inSafari: Boolean) {
        if (!Config.s.profitTracker) return
        val now = System.currentTimeMillis()
        if (finishAt in 1..now) { finishAt = 0; finishRun() }
        val dt = if (lastTick == 0L) 0L else (now - lastTick).coerceIn(0, 1000)
        lastTick = now
        if (client.player == null || d.sessionStart == 0L) return
        if (!inSafari) { awayMs += dt; return }
        // Back in the Safari: a short trip out still counts, a longer one was a pause
        if (awayMs in 1..PAUSE_AFTER_MS) d.sessionActiveMs += awayMs
        awayMs = 0
        d.sessionActiveMs += dt
        d.lastActive = now
        Bazaar.ensureFresh()
        if (now - lastSave > 60_000) { lastSave = now; save() }
    }

    /** Reward summary: the run's items move into the session and the total, and a new run begins. */
    private fun finishRun() {
        val runItems = LinkedHashMap(run)
        safaripatron.history.History.finish(runItems, value(runItems), Minecraft.getInstance().player?.name?.string)
        for ((id, n) in run) {
            d.sessionItems.merge(id, n) { a, b -> a + b }
            d.totalItems.merge(id, n) { a, b -> a + b }
        }
        run.clear()
        val ms = endedRunMs
        endedRunMs = 0L
        if (ms in MIN_RUN_MS..MAX_RUN_MS) { d.sessionRunMs += ms; d.sessionTimedRuns++ }
        d.sessionRuns++
        d.totalRuns++
        save()
    }

    /** Reset: everything back to zero and the clock stopped until the next run's ticket. */
    fun newSession(save: Boolean = true) {
        archiveSession()
        d.sessionStart = 0L
        d.lastActive = 0L
        run.clear()
        endedRunMs = 0L
        d.sessionRuns = 0
        d.sessionRunMs = 0
        d.sessionTimedRuns = 0
        d.sessionActiveMs = 0
        awayMs = 0
        d.sessionItems.clear()
        if (save) save()
    }

    fun resetTotal() {
        d.totalItems.clear(); d.totalRuns = 0
        d.prevSessionItems.clear(); d.prevSessionMs = 0; d.prevSessionRuns = 0
        save()
    }

    // ---------- values ----------

    fun value(items: Map<String, Long>): Double {
        val offer = Config.s.profitSellOffer
        return items.entries.sumOf { (id, n) -> n * Bazaar.price(id, offer) }
    }

    enum class Kind { SHARD, ESSENCE, FEATHER, DYE }
    fun kind(id: String) = when (id) {
        Bazaar.ESSENCE -> Kind.ESSENCE; Bazaar.FEATHER -> Kind.FEATHER; Bazaar.DYE -> Kind.DYE; else -> Kind.SHARD
    }

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
        Bazaar.DYE -> "Emerald Dye"
        else -> (Critters.all.firstOrNull { Bazaar.shardId(it.name) == id }?.name
            ?: id.removePrefix("SHARD_").lowercase().split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }) + " Shard"
    }

    val avgRunMs get() = if (d.sessionTimedRuns > 0) d.sessionRunMs / d.sessionTimedRuns else 0L

    val runValue get() = value(run)
    val finishedValue get() = value(d.sessionItems)
    val sessionValue get() = finishedValue + runValue
    val totalValue get() = value(d.totalItems) + runValue
    val perRun get() = if (d.sessionRuns > 0) finishedValue / d.sessionRuns else 0.0
    val sessionMs get() = d.sessionActiveMs + (if (awayMs <= PAUSE_AFTER_MS) awayMs else 0L)
    val perHour get() = perHour(sessionValue, sessionMs)
    fun perHour(value: Double, ms: Long) = if (ms >= 1000) value / (ms / 3_600_000.0) else 0.0

    /** Chat lines for /sp sessionprofit and /sp totalprofit (null text part = nothing yet). */
    fun sessionLine(): String {
        val empty = d.sessionItems.isEmpty() && d.sessionRuns == 0 && run.isEmpty()
        if (empty && d.prevSessionItems.isNotEmpty()) {
            val v = value(d.prevSessionItems)
            return "Session Total Profit: ${coins(v)} (${coinsShort(perHour(v, d.prevSessionMs))}/h) §7(previous session)"
        }
        return "Session Total Profit: ${coins(sessionValue)} (${coinsShort(perHour)}/h)"
    }

    fun totalLine() = "Total Safari Profit: ${coins(totalValue)} in ${d.totalRuns} run${if (d.totalRuns == 1) "" else "s"}."

    /** Always one decimal: 12.3M, 900.0k */
    fun coins(v: Double): String = when {
        v >= 1e9 -> "%.1fB".format(v / 1e9)
        v >= 1e6 -> "%.1fM".format(v / 1e6)
        v >= 1e3 -> "%.1fk".format(v / 1e3)
        else -> "%.0f".format(v)
    }

    /** Legacy color code of an item's rarity (shards follow their critter). */
    fun itemColor(id: String): String = when (id) {
        Bazaar.FEATHER -> "§d"
        Bazaar.ESSENCE -> "§d"
        Bazaar.DYE -> "§2"
        else -> when (Critters.all.firstOrNull { Bazaar.shardId(it.name) == id }?.rarity) {
            safaripatron.data.Rarity.UNCOMMON -> "§a"
            safaripatron.data.Rarity.RARE -> "§9"
            safaripatron.data.Rarity.EPIC -> "§5"
            safaripatron.data.Rarity.LEGENDARY -> "§6"
            else -> "§f"
        }
    }

    /** 12.3M — one decimal, for numbers that change often */
    fun coinsShort(v: Double): String = when {
        v >= 1e9 -> "%.1fB".format(v / 1e9)
        v >= 1e6 -> "%.1fM".format(v / 1e6)
        v >= 1e3 -> "%.1fk".format(v / 1e3)
        else -> "%.0f".format(v)
    }

    /** 4:12 */
    fun clock(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    fun duration(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }
}
