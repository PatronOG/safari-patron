package safaripatron.safari

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import safaripatron.data.Biome
import safaripatron.hud.Toasts
import safaripatron.config.Config
import safaripatron.data.AreaMap
import safaripatron.party.Party
import safaripatron.sparkling.SparklingFinder

/**
 * Safari'de olup olmadığımızı, hangi bölgede olduğumuzu ve run'ın ne zaman başlayıp bittiğini takip eder.
 *
 * Yeni run başlangıcı (art arda run'lar dahil):
 *  1. Safari dışından Safari'ye girildiğinde,
 *  2. Safari'deyken oyun yeni bir dünya yüklediğinde (yeni instance),
 *  3. "{lider} entered Critter Safari!" mesajından sonraki 20 sn içinde büyük bir ışınlanma olduğunda,
 *  4. Scoreboard'daki "Captured Mobs" sayısı 0'a düştüğünde,
 *  5. Run bitmişken (ödül özetinden sonra) Safari'de yeni bir yakalama geldiğinde.
 * Run bitişi: "SAFARI REWARD SUMMARY" mesajı ya da Safari'den çıkış.
 *
 * Saat: run, oyunun Safari dünyasını yüklediği andan başlar (scoreboard'u beklemez), 1. ve 2. durum bu anı kullanır.
 * 4. ve 5. durum (geç başlayan run) ve /sp newrun süresiz sayılır: kaydedilir ama PB ve süre vermez.
 */
object Safari {
    var inSafari = false
        private set
    var biome: Biome? = null
        private set
    var capturedMobs: Int? = null
        private set

    private var lastLevel: Any? = null
    private var freshWorld = false
    private var freshWorldTicks = 0
    private var outsideTicks = 0
    private var entryArmedAt = 0L
    /** When the game last loaded a new world: the start of the run clock when it turns out to be the Safari. */
    private var worldChangedAt = 0L
    /** How long after the world change the Safari may still be confirmed and keep that start time. */
    private const val ARRIVAL_WINDOW_MS = 30_000L
    private var lastPos: Vec3? = null

    private val capturedPattern = Regex("""^Captured Mobs:\s*([\d,]+)""", RegexOption.IGNORE_CASE)
    private val locationPattern = Regex("""(^|\s)Critter Safari$""", RegexOption.IGNORE_CASE)

    fun tick(client: Minecraft) {
        runPending()
        val level = client.level
        val player = client.player
        if (level == null || player == null) {
            if (inSafari) leave()
            lastLevel = null; lastPos = null
            return
        }
        if (level !== lastLevel) {
            lastLevel = level
            worldChangedAt = System.currentTimeMillis()
            freshWorld = true; freshWorldTicks = 0
            lastPos = null
        }

        val lines = Sidebar.lines(client)
        // Warp sırasında scoreboard bir süre boş gelebilir: o an karar verme.
        if (lines.isNotEmpty()) {
            val here = lines.any { locationPattern.containsMatchIn(it) }
            val newCaptured = lines.firstNotNullOfOrNull { capturedPattern.find(it)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() }
            // Back into a run that was already going (rejoin after a disconnect): its start was missed
            val midRun = (newCaptured ?: 0) > 0
            if (here) {
                outsideTicks = 0
                if (!inSafari) { inSafari = true; startRun(if (midRun) "rejoin" else "enter") }
                else if (freshWorld) startRun(if (midRun) "rejoin" else "new world")
            } else if (inSafari && ++outsideTicks >= 20) {
                leave()
            }
            freshWorld = false

            if (inSafari && newCaptured == 0 && (capturedMobs ?: 0) > 0 && Run.caught.isNotEmpty()) startRun("counter reset")
            capturedMobs = newCaptured
        } else if (freshWorld && ++freshWorldTicks > 200) {
            freshWorld = false
        }

        // Işınlanma ile yeni run (aynı sunucuda kalınan art arda run'lar)
        val pos = player.position()
        val prev = lastPos
        if (inSafari && prev != null && entryArmedAt != 0L &&
            System.currentTimeMillis() - entryArmedAt < 20_000 && prev.distanceToSqr(pos) > 16.0 * 16.0) {
            startRun("teleport")
        }
        lastPos = pos
        if (entryArmedAt != 0L && System.currentTimeMillis() - entryArmedAt >= 20_000) entryArmedAt = 0L

        // Orta alan ve başlangıç gemisi hiçbir bölge sayılmaz (panel 2 ve ESP gizlenir)
        biome = if (inSafari) AreaMap.biomeAt(player.x, player.y, player.z) else null
    }

    private fun startRun(reason: String) {
        entryArmedAt = 0L
        val now = System.currentTimeMillis()
        val arrived = reason == "enter" || reason == "new world" || reason == "teleport"
        val fromWorld = arrived && worldChangedAt > 0 && now - worldChangedAt in 0..ARRIVAL_WINDOW_MS
        val at = if (fromWorld) worldChangedAt else now
        // A run started late (first catch, counter reset, rejoin) has missed its real start: no time for it
        val timed = arrived
        worldChangedAt = 0L
        Config.s.runCounter++
        Run.start(at, timed)
        SparklingFinder.clear()
        safaripatron.history.History.onRunStart()
        safaripatron.profit.Profit.onRunStart(at)
        if (timed) Party.onRunStart(at) // a late or rejoined run is not a fresh warp
    }

    /** /sp newrun: a fresh run from now, not timed. */
    fun manualRun() {
        entryArmedAt = 0L
        Config.s.runCounter++
        Run.start(timed = false)
        SparklingFinder.clear()
        safaripatron.history.History.onRunStart()
    }

    private fun leave() {
        inSafari = false
        biome = null
        outsideTicks = 0
        Run.end()
        SparklingFinder.clear()
    }

    fun onChat(event: ChatParser.Event, selfName: String) {
        when (event) {
            is ChatParser.Capture -> {
                if (inSafari && !Run.running) startRun("capture")
                Run.record(selfName, event.critter)
            }
            is ChatParser.LootShare -> {
                if (inSafari && !Run.running) startRun("capture")
                Run.record(event.player, event.critter)
            }
            is ChatParser.SparklingCaught -> {
                SparklingFinder.caught(event.critter, byMe = event.player.equals(selfName, ignoreCase = true))
                // All-time sparkling collection (you or your party)
                if (Config.s.sparklingCollection.add(event.critter)) Config.save()
                sparklingInterval(event.critter)
                if (Run.running) {
                    Run.sparklingsCaught.add(event.player to event.critter)
                    Run.record(event.player, event.critter)
                }
            }
            is ChatParser.Entered -> { entryArmedAt = System.currentTimeMillis(); Party.request() }
            is ChatParser.Hotspot -> {
                val name = event.biome.lowercase().replaceFirstChar { it.uppercase() }
                // Once per run, even if the message shows up twice
                if ((Config.s.hotspotAnnounce || Config.s.hotspotScreen) && Run.markHotspotAnnounced()) {
                    if (Config.s.hotspotAnnounce) Party.chat("[✦SP✦] Hotspot: $name")
                    if (Config.s.hotspotScreen) {
                        val color = Biome.entries.firstOrNull { it.label.equals(name, true) }?.color ?: 0xFFFFAA00.toInt()
                        Toasts.show(Toasts.Kind.ANNOUNCE, "Hotspot: $name", color, 4000)
                    }
                }
            }
            ChatParser.RewardSummary -> {
                Run.end() // usually already ended a moment ago by the profit tracker, with the same time
                SparklingFinder.clear()
            }
        }
    }

    /** "Last Sparkling (Polaris) was 14 runs, 1 day, 3 hours, 52 minutes ago." Sparklings count for the whole party. */
    private fun sparklingInterval(critter: String) {
        val s = Config.s
        val now = System.currentTimeMillis()
        val previous = s.lastSparklingCritter
        if (s.sparklingInterval && s.lastSparklingAt > 0) {
            val runs = (s.runCounter - s.lastSparklingRun).coerceAtLeast(0)
            val mins = (now - s.lastSparklingAt) / 60_000
            val days = mins / 1440
            val hours = mins / 60 % 24
            val parts = buildList {
                add(plural(runs.toLong(), "run"))
                if (days > 0) add(plural(days, "day"))
                if (days > 0 || hours > 0) add(plural(hours, "hour"))
                add(plural(mins % 60, "minute"))
            }
            // Sent a moment later so it lands under the SPARKLING! box instead of inside it
            delayed(400) {
                Minecraft.getInstance().player?.sendSystemMessage(safaripatron.Brand.msg(
                    "§7Last Sparkling §f($previous) §7was captured §f${parts.joinToString("§7, §f")} §7ago."))
            }
        }
        s.lastSparklingCritter = critter
        s.lastSparklingAt = now
        s.lastSparklingRun = s.runCounter
        Config.save()
    }

    private fun plural(n: Long, word: String) = "$n $word" + if (n == 1L) "" else "s"

    private val pending = ArrayList<Pair<Long, () -> Unit>>()

    private fun delayed(ms: Long, action: () -> Unit) { pending.add(System.currentTimeMillis() + ms to action) }

    /** Runs delayed actions that are due (called every tick). */
    private fun runPending() {
        if (pending.isEmpty()) return
        val now = System.currentTimeMillis()
        val due = pending.filter { it.first <= now }
        pending.removeAll(due.toSet())
        due.forEach { it.second() }
    }
}
