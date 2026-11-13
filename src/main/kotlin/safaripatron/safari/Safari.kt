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
            freshWorld = true; freshWorldTicks = 0
            lastPos = null
        }

        val lines = Sidebar.lines(client)
        // Warp sırasında scoreboard bir süre boş gelebilir: o an karar verme.
        if (lines.isNotEmpty()) {
            val here = lines.any { locationPattern.containsMatchIn(it) }
            if (here) {
                outsideTicks = 0
                if (!inSafari) { inSafari = true; startRun("enter") }
                else if (freshWorld) startRun("new world")
            } else if (inSafari && ++outsideTicks >= 20) {
                leave()
            }
            freshWorld = false

            val newCaptured = lines.firstNotNullOfOrNull { capturedPattern.find(it)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() }
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

    private fun startRun(@Suppress("UNUSED_PARAMETER") reason: String) {
        entryArmedAt = 0L
        Config.s.runCounter++
        Run.start()
        SparklingFinder.clear()
        Party.onRunStart()
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
                    if (Config.s.hotspotAnnounce) Party.chat("[✦Safari Patron✦] Hotspot: $name")
                    if (Config.s.hotspotScreen) {
                        val color = Biome.entries.firstOrNull { it.label.equals(name, true) }?.color ?: 0xFFFFAA00.toInt()
                        Toasts.show(Toasts.Kind.ANNOUNCE, "Hotspot: $name", color, 4000)
                    }
                }
            }
            ChatParser.RewardSummary -> {
                Run.end()
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
