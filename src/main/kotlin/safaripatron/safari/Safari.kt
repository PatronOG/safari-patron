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
 * Saat (Sparkling Mutuals ile aynı): run, oyunun son yüklediği dünyanın yüklendiği andan başlar (scoreboard'u
 * beklemez). Safari'den çıkınca bu an unutulur; o andan sonra başlayan run (ör. sayaç sıfırlanması) başladığı andan sayılır.
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
            // Leaving the Safari always means another server: drop out right away, so panels and ESP go at once
            // instead of waiting for the new scoreboard. If the new world is a Safari again, it starts a new run.
            if (inSafari) leave()
            worldChangedAt = System.currentTimeMillis()
            freshWorld = true; freshWorldTicks = 0
            lastPos = null
        }

        val lines = Sidebar.lines(client)
        // Warp sırasında scoreboard bir süre boş gelebilir: o an karar verme.
        if (lines.isNotEmpty()) {
            val here = lines.any { locationPattern.containsMatchIn(it) }
            val newCaptured = lines.firstNotNullOfOrNull { capturedPattern.find(it)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() }
            // Back into a run that was already going (rejoin after a disconnect): no warp check for it
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
        // The clock starts when the Safari world loaded; with no world change waiting, from now
        val at = if (worldChangedAt in 1..now) worldChangedAt else now
        worldChangedAt = 0L
        Config.s.runCounter++
        Run.start(at)
        SparklingFinder.clear()
        safaripatron.party.HotspotAssign.onRunStart()
        safaripatron.history.History.onRunStart()
        safaripatron.profit.Profit.onRunStart(at)
        // Only a real arrival is a warp to check (not a rejoin mid-run or a run found late)
        if (reason == "enter" || reason == "new world" || reason == "teleport") Party.onRunStart(at)
    }

    /** /sp newrun: a fresh run from now. */
    fun manualRun() {
        entryArmedAt = 0L
        worldChangedAt = 0L
        Config.s.runCounter++
        Run.start()
        SparklingFinder.clear()
        safaripatron.history.History.onRunStart()
    }

    private fun leave() {
        inSafari = false
        worldChangedAt = 0L
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
                safaripatron.party.HotspotAssign.onOwnHotspot(name, selfName)
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
