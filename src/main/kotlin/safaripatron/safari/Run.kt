package safaripatron.safari

import safaripatron.config.BestTime
import safaripatron.config.Config
import safaripatron.data.Biome
import safaripatron.data.Critters
import safaripatron.hud.Toasts
import safaripatron.party.Party

/** Bir Safari run'ının verisi. Her yeni warp'ta sıfırlanır. */
object Run {
    enum class State { IDLE, RUNNING, ENDED }

    var state = State.IDLE
        private set
    private var startedAt = 0L
    /** Her yeni run'da değişen damga (yapıların run'a özel durumunu sıfırlamak için). */
    var startStamp = 0L
        private set
    private var endedAt = 0L

    /** Partinin bu run'da yakaladığı benzersiz türler. */
    val caught = LinkedHashSet<String>()
    /** Sparklings caught by the party this run: (player, critter). */
    val sparklingsCaught = ArrayList<Pair<String, String>>()
    /** Oyuncu → bölge → yakaladığı benzersiz türler. Ekleme sırası korunur. */
    val byPlayer = LinkedHashMap<String, MutableMap<Biome, MutableSet<String>>>()
    private val announced = HashSet<String>()
    private var hotspotAnnounced = false

    /** true the first time in a run (so the hotspot is only posted once per run). */
    fun markHotspotAnnounced(): Boolean {
        if (hotspotAnnounced) return false
        hotspotAnnounced = true
        return true
    }

    val running get() = state == State.RUNNING

    val elapsedMs: Long
        get() = when (state) {
            State.RUNNING -> System.currentTimeMillis() - startedAt
            State.ENDED -> endedAt - startedAt
            State.IDLE -> 0L
        }

    fun start() {
        state = State.RUNNING
        startedAt = System.currentTimeMillis()
        startStamp++
        caught.clear(); byPlayer.clear(); announced.clear(); sparklingsCaught.clear()
        hotspotAnnounced = false
    }

    fun end() {
        if (state != State.RUNNING) return
        state = State.ENDED
        endedAt = System.currentTimeMillis()
    }

    fun caughtIn(biome: Biome) = biome.critters.count { it.name in caught }

    /** Bir yakalamayı kaydeder ve gerekirse anons yapar. */
    fun record(player: String, critterName: String) {
        val critter = Critters.named(critterName) ?: return
        caught.add(critter.name)
        byPlayer.getOrPut(player) { LinkedHashMap() }.getOrPut(critter.biome) { LinkedHashSet() }.add(critter.name)
        checkAnnouncements(player)
    }

    /**
     * The players of this run for a party PB: everyone who caught something, in order, with the biome of
     * their first catch; then party members who caught nothing seen by you (biome unknown).
     */
    private fun partySnapshot(): MutableList<safaripatron.config.PartyMember> {
        val out = ArrayList<safaripatron.config.PartyMember>()
        for ((name, biomes) in byPlayer) out.add(safaripatron.config.PartyMember(name, biomes.keys.firstOrNull()?.name))
        val connection = net.minecraft.client.Minecraft.getInstance().connection
        for (uuid in safaripatron.party.Party.members) {
            val name = connection?.getPlayerInfo(uuid)?.profile?.name ?: continue
            if (out.none { it.name.equals(name, ignoreCase = true) }) out.add(safaripatron.config.PartyMember(name, null))
        }
        return out
    }

    /** finisher = whoever caught the critter that completed it. */
    private fun checkAnnouncements(finisher: String) {
        for (biome in Biome.entries) {
            if (biome.critters.all { it.name in caught }) announce("biome:$biome", "${biome.label} Done!", biome.color, finisher)
        }
        if (caught.size >= Critters.TOTAL) {
            announce("all", "Everything Done!", 0xFFFFAA00.toInt(), finisher)
        } else if (caught.size == Critters.TOTAL - 1 && Critters.MACAW !in caught) {
            announce("allButMacaw", "Everything except Macaw done!", 0xFFFFAA00.toInt(), finisher)
        }
    }

    private fun announce(key: String, text: String, color: Int, finisher: String) {
        if (!announced.add(key)) return
        var full = text
        var short = text
        if (Config.s.biomeTimes && state == State.RUNNING) {
            val ms = elapsedMs
            val best = Config.s.bestTimes[key]
            // Biome PBs name who finished it; "Everything" is the whole party's time, so no name
            val named = key.startsWith("biome:")
            fun by(b: BestTime): String { val who: String? = b.by; return if (named && !who.isNullOrBlank()) " by $who" else "" }
            full += " Completed in ${time(ms)}"
            short += " ${time(ms)}"
            if (best == null || best.ms <= 0 || ms < best.ms) {
                if (best != null && best.ms > 0) full += " (New PB! Old: ${time(best.ms)}${by(best)})" else full += " (New PB!)"
                short += " (PB!)"
                Config.s.bestTimes[key] = BestTime(ms, finisher, if (key.startsWith("biome:")) null else partySnapshot())
                Config.save()
            } else {
                full += " (PB ${time(best.ms)}${by(best)})"
            }
        }
        if (Config.s.biomeAnnouncements) Party.chat(full)
        if (Config.s.biomeScreen) Toasts.show(Toasts.Kind.ANNOUNCE, short, color, 4000)
    }

    /** 2:31 or 1:02:31 */
    fun time(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}
