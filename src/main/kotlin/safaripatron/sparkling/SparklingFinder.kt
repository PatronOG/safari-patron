package safaripatron.sparkling

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import safaripatron.config.Config
import safaripatron.esp.CritterId
import safaripatron.safari.ChatParser
import safaripatron.safari.Safari
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Oyunun zaten gösterdiği "SPARKLING <isim>" yazılarını okur.
 * Bulunan sparkling SADECE "SPARKLING! ... caught a SPARKLING X!" mesajı gelince listeden çıkar
 * (kapsül değince yazı bir anlığına "CAPTURING" olsa da panel kaybolmaz).
 * İlk görüldüğü bloğa köşeleri vurgulanan bir waypoint konur.
 */
object SparklingFinder {
    private const val GOLD = 0xFFFFAA00.toInt()
    private const val WAYPOINT = 0xFFFFE066.toInt()

    class Spark(key: UUID, val species: String, val foundAt: Long, var firstBlock: BlockPos) {
        /** Aynı sparkling'e ait bütün yazı entity'leri (Hypixel bazen birden fazla yazı entity'si kullanıyor). */
        val keys = HashSet<UUID>().apply { add(key) }
        var label: Entity? = null
        var body: Entity? = null
        var lastPos: Vec3 = Vec3.ZERO
        /** false = oyun artık bu entity'yi yüklemiyor (uzaktasın); son bilinen konum gösterilir. */
        var live = true
    }

    val sparks = ArrayList<Spark>()

    private val pattern = Regex("""\bSPARKLING\s+([A-Za-z][A-Za-z ]*[A-Za-z])""", RegexOption.IGNORE_CASE)

    fun species(name: String?): String? {
        if (name == null) return null
        val clean = ChatParser.clean(name)
        if (clean.startsWith("CAPTURING", ignoreCase = true)) return null
        return pattern.find(clean)?.groupValues?.get(1)?.trim()
    }

    /** Yakalanan sparkling'lerin yazısı bir an daha görünebilir; tekrar eklenmesin diye 10 sn hatırlanır. */
    private class Caught(val key: UUID, val species: String, val pos: Vec3, val at: Long)
    private val recentlyCaught = ArrayList<Caught>()

    fun clear() { sparks.clear(); recentlyCaught.clear(); othersCaught.clear(); waiting.clear() }

    /** Partideki başkasının yakaladığı türler: o türün yazısı kaybolunca sparkling listeden çıkar. */
    private val othersCaught = ArrayList<Pair<String, Long>>()

    /**
     * "SPARKLING! X caught a SPARKLING Foxtrot!" mesajı.
     *  - Sen yakaladıysan: o türün sparkling'i (panel + waypoint) hemen kalkar.
     *  - Başkası yakaladıysa: senin gördüğün sparkling hâlâ duruyorsa kalır; yazısı kaybolursa kalkar.
     */
    fun caught(species: String, byMe: Boolean) {
        val now = System.currentTimeMillis()
        if (!byMe) {
            othersCaught.add(species to now)
            sparks.removeIf { s -> s.species.equals(species, true) && !s.live }
            return
        }
        val removed = sparks.filter { it.species.equals(species, ignoreCase = true) }
        sparks.removeAll(removed.toSet())
        for (r in removed) for (k in r.keys) recentlyCaught.add(Caught(k, r.species, r.lastPos, now))
    }

    fun tick(client: Minecraft) {
        val level = client.level
        val player = client.player
        if (!Config.s.sparklingFinder || level == null || player == null || !Safari.inSafari) {
            if (level == null || !Safari.inSafari) sparks.clear()
            return
        }

        val all = level.entitiesForRendering().filter { !it.isRemoved && it !== player }
        val seen = HashSet<UUID>()
        for (e in all) {
            val sp = species(e.customName?.string) ?: continue
            // Aynı yazı entity'si ya da aynı türün yakındaki yeni yazısı (yazı yeniden oluşturulduysa) = aynı sparkling
            var spark = sparks.firstOrNull { e.uuid in it.keys }
                ?: sparks.firstOrNull { it.species.equals(sp, true) && it.lastPos.distanceToSqr(e.position()) < 12.0 * 12.0 }
            if (spark == null) {
                val now = System.currentTimeMillis()
                recentlyCaught.removeIf { now - it.at > 10_000 }
                if (recentlyCaught.any { it.key == e.uuid || (it.species.equals(sp, true) && it.pos.distanceToSqr(e.position()) < 12.0 * 12.0) }) continue
                val body = findBody(e, all)
                // Some nametags (e.g. Litterbug) ride the critter and report a bogus position (y≈1) for
                // the first ticks. Wait for a real body or a sane position before announcing.
                val pos = body?.position() ?: labelPos(e)
                if (body == null && pos.y < level.minY + 8) {
                    val waited = waiting.merge(e.uuid, 1, Int::plus) ?: 1
                    if (waited < 40) continue
                }
                waiting.remove(e.uuid)
                spark = Spark(e.uuid, sp, System.currentTimeMillis(), BlockPos.containing(pos))
                sparks.add(spark)
                announce(player, spark)
            } else if (spark.body == null && System.currentTimeMillis() - spark.foundAt < 3000) {
                // Body found shortly after: move the waypoint onto the critter
                findBody(e, all)?.let { spark.firstBlock = it.blockPosition() }
            }
            spark.keys.add(e.uuid)
            seen.add(e.uuid)
            spark.label = e
            spark.body = findBody(e, all) ?: spark.body?.takeIf { !it.isRemoved }
            spark.lastPos = spark.body?.position() ?: labelPos(e)
            spark.live = true
        }

        // Görünmeyenler listede kalır (son bilinen konumla); sadece yakalama mesajı ya da run sonu siler.
        for (s in sparks) {
            if (s.keys.any { it in seen }) continue
            s.live = false
            s.label = null; s.body = null
        }
        // Başkası yakaladıysa ve yazısı da kaybolduysa kaldır
        val now = System.currentTimeMillis()
        othersCaught.removeIf { now - it.second > 15_000 }
        sparks.removeIf { s -> !s.live && othersCaught.any { it.first.equals(s.species, true) } }
    }

    private val waiting = HashMap<UUID, Int>()

    /** Party-chat share links: id → (species, block). */
    private val shares = HashMap<Int, Pair<String, BlockPos>>()
    private var nextShareId = 1

    /** A nametag riding the critter reports its vehicle's position. */
    private fun labelPos(e: Entity): Vec3 = e.vehicle?.position() ?: e.position()

    private fun announce(player: net.minecraft.world.entity.player.Player, s: Spark) {
        val p = s.firstBlock
        val dist = sqrt(p.distToCenterSqr(player.position()))
        player.sendSystemMessage(Component.literal(
            "§6✦ §e§lSPARKLING §f${s.species} §7— ${dist.roundToInt()} blocks §8(${p.x}, ${p.y}, ${p.z})"))
        val id = nextShareId++
        shares[id] = s.species to p
        if (shares.size > 50) shares.keys.minOrNull()?.let { shares.remove(it) }
        player.sendSystemMessage(
            Component.empty()
                .append(Component.literal("§f[Share to §9PARTY §fchat]")
                    .withStyle { it.withClickEvent(ClickEvent.RunCommand("/sp share party $id")) })
                .append(Component.literal(" §7or "))
                .append(Component.literal("§f[Share to §eALL §fchat]")
                    .withStyle { it.withClickEvent(ClickEvent.RunCommand("/sp share all $id")) }))
        if (Config.s.sparklingSound) player.playSound(SoundEvents.PLAYER_LEVELUP, 1f, 1.6f)
    }

    fun hasShares() = shares.isNotEmpty()

    /** /sp share <party|all> <id> (from the clickable chat buttons): posts the sparkling to party or all chat. */
    fun share(id: Int, toParty: Boolean) {
        val (species, p) = shares[id] ?: return
        val channel = if (toParty) "pc" else "ac"
        Minecraft.getInstance().connection?.sendCommand(
            "$channel [✦Safari Patron✦] A SPARKLING $species was spotted! x: ${p.x}, y: ${p.y}, z: ${p.z}")
    }

    /** İsim yazısı ayrı bir entity; critter hemen altında. Yatayda 2.5 blok içindeki en yakın critter. */
    private fun findBody(label: Entity, all: List<Entity>): Entity? {
        label.vehicle?.let { if (CritterId.identify(it) != null) return it }
        val ly = labelPos(label).y
        val checkY = ly > (label.level().minY + 8).toDouble()
        return all.asSequence()
            .filter { it !== label && CritterId.identify(it) != null }
            .map { it to ((it.x - label.x) * (it.x - label.x) + (it.z - label.z) * (it.z - label.z)) }
            .filter { (b, d2) -> d2 < 2.5 * 2.5 && (!checkY || b.y in ly - 4.0..ly + 1.0) }
            .minByOrNull { it.second }?.first
    }

    fun collect(out: safaripatron.render.WorldRender.Batch) {
        if (!Config.s.sparklingFinder) return
        for (s in sparks) {
            // İlk görüldüğü blok: köşeleri vurgulanmış waypoint + üstünde SPARKLING yazısı
            out.corners(AABB(s.firstBlock), WAYPOINT)
            val top = Vec3(s.firstBlock.x + 0.5, s.firstBlock.y + 1.9, s.firstBlock.z + 0.5)
            out.label("✦ SPARKLING ✦", top, GOLD, bold = true, line = 0)
            out.label(s.species, top, 0xFFFFFF55.toInt(), bold = false, line = 1)
            if (!s.live) continue
            val body = s.body?.takeIf { !it.isRemoved }
            val box = if (body != null) CritterId.box(body).inflate(0.05) else {
                val l = s.label ?: continue
                AABB(l.x - .3, l.y, l.z - .3, l.x + .3, l.y + .6, l.z + .3)
            }
            out.box(box, GOLD)
        }
    }

    fun distanceSq(s: Spark): Double {
        val p = Minecraft.getInstance().player ?: return 0.0
        return s.lastPos.distanceToSqr(p.position())
    }
}
