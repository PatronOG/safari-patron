package safaripatron.party

import net.azureaaron.hmapi.events.HypixelPacketEvents
import net.azureaaron.hmapi.network.HypixelNetworking
import net.azureaaron.hmapi.network.packet.s2c.HelloS2CPacket
import net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket
import net.azureaaron.hmapi.network.packet.v2.s2c.PartyInfoS2CPacket
import net.minecraft.client.Minecraft
import safaripatron.config.Config
import safaripatron.hud.Toasts
import safaripatron.safari.ChatParser
import java.util.UUID

/**
 * Parti üyeleri (Hypixel Mod API), parti chatine mesaj kuyruğu ve warp kontrolü.
 * Mesajlar Hypixel'in spam korumasına takılmamak için aralıklı gönderilir.
 */
object Party {
    private val log = org.slf4j.LoggerFactory.getLogger("safari-patron/party")
    var members: Set<UUID> = emptySet()
        private set
    var known = false
        private set

    private var requestedAt = 0L
    private val queue = ArrayDeque<String>()
    private var lastSentAt = 0L
    private const val SEND_GAP_MS = 1500L

    /**
     * Warp kontrolü: Safari onaylandığı an (run saati dünya değişiminden sayılsa da) bakar. Herkes oradaysa "4/4"
     * hemen gider; biri yoksa tab list'in dolması için 1 sn daha bekler, hâlâ yoksa "Someone missing!" der.
     */
    private var warpCheckStart = 0L
    private var warpDecideAt = 0L
    private const val WARP_GRACE_MS = 1000L

    private val rosterChange = listOf(
        "joined the party", "has left the party", "has been removed from the party", "has disbanded the party",
        "You left the party", "have been kicked from the party", "The party was disbanded", "You have joined",
        "to Party Leader", "The party was transferred", "You are not currently in a party", "You are not in a party",
    )

    fun init() {
        HypixelPacketEvents.HELLO.register(::onPacket)
        HypixelPacketEvents.PARTY_INFO.register(::onPacket)
    }

    private fun onPacket(packet: HypixelS2CPacket) {
        Minecraft.getInstance().execute {
            when (packet) {
                is HelloS2CPacket -> request()
                is PartyInfoS2CPacket -> {
                    known = true
                    members = if (packet.inParty) packet.members?.keys?.toSet().orEmpty() else emptySet()
                }
                else -> {}
            }
        }
    }

    fun request(force: Boolean = false) {
        val now = System.currentTimeMillis()
        // Hypixel bu isteği sınırlıyor (RATE_LIMITED); zorunlu isteklerde bile 2 sn'den sık sorma
        if (requestedAt != 0L && now - requestedAt < (if (force) 2000 else 5000)) return
        requestedAt = now
        runCatching { HypixelNetworking.sendPartyInfoC2SPacket(2) }
    }

    /** Parti değişikliği mesajlarında üye listesini tazeler. */
    fun onChatLine(raw: String) {
        val l = ChatParser.clean(raw)
        if (rosterChange.any { l.contains(it, ignoreCase = true) }) {
            if (RosterChange.matches(l)) known = false
            request(force = true)
        }
    }

    val inParty get() = members.size > 1

    /** Parti chatine mesaj sıraya koyar (partide değilsen hiçbir şey göndermez). */
    fun chat(message: String) {
        if (known && !inParty) return
        queue.addLast(message)
    }

    /** [at] = the run's start (when the Safari world loaded); called when the Safari is confirmed. */
    fun onRunStart(at: Long) {
        if (!known) request(force = true)
        warpCheckStart = at
        warpDecideAt = System.currentTimeMillis() + WARP_GRACE_MS
    }

    fun tick(client: Minecraft) {
        val connection = client.connection
        if (connection == null) { queue.clear(); members = emptySet(); known = false; warpCheckStart = 0L; return }
        val now = System.currentTimeMillis()

        if (warpCheckStart != 0L) {
            val deadline = now >= warpDecideAt
            if (warpCheck(client, deadline)) warpCheckStart = 0L
        }

        if (queue.isNotEmpty() && now - lastSentAt >= SEND_GAP_MS) {
            val msg = queue.removeFirst()
            if (!known || inParty) {
                connection.sendCommand("pc $msg")
                lastSentAt = now
            }
        }
    }

    /** true = kontrol bitti (duyuru yapıldı ya da gerek yok). */
    private fun warpCheck(client: Minecraft, deadline: Boolean): Boolean {
        if (!Config.s.warpCheck && !Config.s.warpScreen) return true
        if (!known) {
            // Party not known yet (Mod API answer on its way): wait a few seconds more for it, then give up
            if (System.currentTimeMillis() >= warpDecideAt + 4000) return true
            request()
            return false
        }
        if (!inParty) return true
        val connection = client.connection ?: return true
        // Whole tab list (may still hold players of the last server, e.g. someone kicked on the way)
        val whole = HashSet<UUID>()
        connection.onlinePlayers.forEach { whole.add(it.profile.id) }
        connection.listedOnlinePlayers.forEach { whole.add(it.profile.id) }
        // Only players this server added, when the hook works; else the whole list as before
        val present = if (TabList.active) HashSet(TabList.players()) else whole
        client.player?.uuid?.let { present.add(it); whole.add(it) }

        val total = members.size
        val here = members.count { it in present }
        if (here >= total || deadline) {
            // One line per warp, to check this in the log: "fresh" is what counts, "whole" is the old way
            log.info("Warp check: {}/{} here (fresh list: {}, whole tab list: {}){}", here, total,
                if (TabList.active) here else "off", members.count { it in whole },
                if (members.count { it in whole } != here) " - left-over names not counted" else "")
        }
        if (here >= total) {
            if (Config.s.warpCheck) chat("[✦SP✦] $here/$total Players in Safari.")
            if (Config.s.warpScreen) Toasts.show(Toasts.Kind.WARP, "$here/$total Players in Safari", 0xFF55FF55.toInt(), 3000)
            return true
        }
        if (!deadline) return false
        if (Config.s.warpCheck) chat("[✦SP✦] Someone missing!")
        if (Config.s.warpScreen) Toasts.show(Toasts.Kind.WARP, "Someone missing! ($here/$total)", 0xFFFF5555.toInt(), 6000)
        return true
    }
}
