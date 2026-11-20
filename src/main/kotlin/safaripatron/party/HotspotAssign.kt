package safaripatron.party

import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import safaripatron.config.Config
import safaripatron.data.Biome
import safaripatron.hud.Toasts
import safaripatron.safari.ChatParser

/**
 * Hotspot assignment (Party → Hotspot assignment): the one player who leads the party collects everyone's
 * Hunting Hotspot from party chat and posts where each player should go:
 * `[✦SP✦] Acrohh: Cavern ✦ | Jxnos: Forest | PatronOG: Icy ✦ | Rudolf2305: Haunted` (✦ = their hotspot).
 *
 * - Hotspots come from our `[✦SP✦] Hotspot: Icy`, other mods (`[SM] Hotspot: Icy`, ...) and what players type
 *   themselves (`c`, `cave`, `my hotspot is icy`, one-letter typos).
 * - As soon as everyone in the party has one, or after Assignment wait: who said nothing gets a biome left over.
 * - Two or more on one biome: the one who lost the most hotspot clashes (saved) goes; the others go to the
 *   biomes left over and their count goes up, the winner's back to 0.
 * Only reads chat and sends one party message (can be turned off); nothing is done in the game for anyone.
 */
object HotspotAssign {
    private val log = LoggerFactory.getLogger("safari-patron/hotspot")

    /** Hotspot of each player this run (lower-case name → biome), first report counts. */
    private val reports = LinkedHashMap<String, Biome>()
    /** Display spelling of the names seen. */
    private val names = HashMap<String, String>()
    /** When our own hotspot came: Assignment wait counts from here. 0 = not yet this run. */
    private var hotspotAt = 0L
    private var done = false

    /** A new run: start collecting again. */
    fun onRunStart() {
        reports.clear(); names.clear(); hotspotAt = 0L; done = false
    }

    /** Our own HOTSPOT! message. */
    fun onOwnHotspot(biomeText: String, self: String) {
        val biome = Biome.entries.firstOrNull { it.label.equals(biomeText, true) || it.name.equals(biomeText, true) } ?: return
        put(self, biome)
        if (hotspotAt == 0L) hotspotAt = System.currentTimeMillis()
    }

    // "Party > [MVP+] Name: text"
    val partyLine = Regex("""^Party > (?:\[[^\]]+] )?(\w{1,16}): (.*)$""")

    /** Every chat line: party messages may hold someone's hotspot. */
    fun onChatLine(raw: String) {
        if (!Config.s.hotspotAssign || done || hotspotAt == 0L && reports.isEmpty() && !safaripatron.safari.Run.running) return
        val m = partyLine.matchEntire(ChatParser.clean(raw)) ?: return
        val biome = parseReport(m.groupValues[2]) ?: return
        put(m.groupValues[1], biome)
    }

    private fun put(name: String, biome: Biome) {
        val key = name.lowercase()
        names.putIfAbsent(key, name)
        if (reports.putIfAbsent(key, biome) == null) log.info("Hotspot of {}: {}", name, biome.label)
    }

    fun tick(client: Minecraft) {
        if (done || hotspotAt == 0L || !Config.s.hotspotAssign || !Party.inParty) return
        val connection = client.connection ?: return
        val self = client.player?.name?.string ?: return
        // Party members in this Safari, by name (the tab list has their names)
        val present = Party.members.mapNotNull { connection.getPlayerInfo(it)?.profile?.name }.toMutableSet()
        present.add(self)
        val everyone = present.all { it.lowercase() in reports }
        val waited = System.currentTimeMillis() - hotspotAt >= Config.s.hotspotAssignWait.coerceIn(5, 60) * 1000L
        if (!everyone && !waited) return
        done = true
        present.forEach { names.putIfAbsent(it.lowercase(), it) }
        val plan = plan(present.map { it.lowercase() }, reports, Config.s.hotspotDebt)
        Config.save()
        val text = message(plan, reports, names)
        log.info("Hotspot assignment ({}): {}", if (everyone) "everyone reported" else "after the wait", text)
        Party.chat("[✦SP✦] $text")
        plan[self.lowercase()]?.let { Toasts.show(Toasts.Kind.ANNOUNCE, "Go: ${it.label}", it.color, 5000) }
    }

    /**
     * Who goes where ([players] and the keys are lower-case names). Clash losers' counts in [debt] go up, a clash
     * winner's goes back to 0.
     */
    fun plan(players: List<String>, reports: Map<String, Biome>, debt: MutableMap<String, Int>): Map<String, Biome> {
        val plan = LinkedHashMap<String, Biome>()
        val moved = ArrayList<String>()
        for (b in Biome.entries) {
            val group = players.filter { reports[it] == b }
                .sortedWith(compareByDescending<String> { debt[it] ?: 0 }.thenBy { it })
            if (group.isEmpty()) continue
            plan[group.first()] = b
            if (group.size > 1) {
                debt.remove(group.first())
                group.drop(1).forEach { debt[it] = (debt[it] ?: 0) + 1; moved.add(it) }
            }
        }
        // Clash losers first, then those who said nothing, onto the biomes left over
        val silent = players.filter { it !in reports }.sorted()
        val free = Biome.entries.filter { it !in plan.values }.toMutableList()
        for (p in moved + silent) {
            if (free.isEmpty()) break
            plan[p] = free.removeAt(0)
        }
        return plan
    }

    /**
     * "Acrohh: Cavern ✦ | Jxnos: Forest | ..." by name, so everyone finds themselves in the same place every
     * run (✦ = at their hotspot).
     */
    fun message(plan: Map<String, Biome>, reports: Map<String, Biome>, names: Map<String, String>): String =
        plan.entries.sortedBy { (names[it.key] ?: it.key).lowercase() }.joinToString(" | ") { (key, b) ->
            "${names[key] ?: key}: ${b.label}" + if (reports[key] == b) " ✦" else ""
        }

    private val prefix = Regex("""^\[(✦SP✦|SP|SM|SU|SafariUtils|CritterMod|CM)]\s*""", RegexOption.IGNORE_CASE)
    private val filler = setOf("my", "hunting", "hotspot", "is", "the", "biome", "i", "got", "have", "im", "in", "at", "its", "it")
    private val aliases = mapOf(
        Biome.FOREST to listOf("forest", "for"), Biome.CAVERN to listOf("cavern", "cav", "cave"),
        Biome.ICY to listOf("icy", "ice"), Biome.HAUNTED to listOf("haunted", "haunt"),
    )
    private val letters = mapOf("f" to Biome.FOREST, "c" to Biome.CAVERN, "i" to Biome.ICY, "h" to Biome.HAUNTED)

    /** The biome a party message gives as someone's hotspot, or null when it says something else. */
    fun parseReport(message: String): Biome? {
        var body = message.trim()
        val tag = prefix.find(body)
        if (tag != null) body = body.removeRange(tag.range)
        body = body.substringBefore('|').trim().lowercase() // "[SM] Hotspot: Icy | Hotspot wins: ..."
        letters[body]?.let { return it }
        val words = body.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        val meaningful = words.filter { it !in filler }
        // One word ("cave"), or a sentence that says it's the hotspot ("my hotspot is cave")
        if (meaningful.size != 1 && "hotspot" !in words) return null
        val found = aliases.filter { (_, names) -> meaningful.any { w -> names.any { close(w, it) } } }.keys
        return found.singleOrNull()
    }

    /** Same word, or (4+ letters) one letter wrong, missing, extra or two swapped. */
    private fun close(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 4 || b.length < 4 || kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            val diff = a.indices.filter { a[it] != b[it] }
            return diff.size == 1 || diff.size == 2 && diff[1] == diff[0] + 1 && a[diff[0]] == b[diff[1]] && a[diff[1]] == b[diff[0]]
        }
        val (long, short) = if (a.length > b.length) a to b else b to a
        return long.indices.any { long.removeRange(it, it + 1) == short }
    }
}
