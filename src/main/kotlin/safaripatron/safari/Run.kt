package safaripatron.safari

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
        checkAnnouncements()
    }

    private fun checkAnnouncements() {
        for (biome in Biome.entries) {
            if (biome.critters.all { it.name in caught }) announce("biome:$biome", "${biome.label} Done!", biome.color)
        }
        if (caught.size >= Critters.TOTAL) {
            announce("all", "Everything Done!", 0xFFFFAA00.toInt())
        } else if (caught.size == Critters.TOTAL - 1 && Critters.MACAW !in caught) {
            announce("allButMacaw", "Everything except Macaw done!", 0xFFFFAA00.toInt())
        }
    }

    private fun announce(key: String, text: String, color: Int) {
        if (!announced.add(key)) return
        if (Config.s.biomeAnnouncements) Party.chat(text)
        if (Config.s.screenAnnouncements) Toasts.show(text, color, 4000)
    }
}
