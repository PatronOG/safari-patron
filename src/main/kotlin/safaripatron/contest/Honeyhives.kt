package safaripatron.contest

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Display
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import safaripatron.config.Config
import safaripatron.config.Paths
import safaripatron.render.WorldRender
import safaripatron.safari.ChatParser
import kotlin.math.abs

/**
 * Torrhus Canyon honeyhives, part of Miria's Contest.
 * While a contest is on (and your ticket isn't done), every full hive gets a waypoint. Looting one
 * (click + "HIVE!") puts it on its one-hour refill; "QUEEN BEE!" keeps it full. The countdown the server
 * shows above a hive ("Refill in: 23m 10s", "Click to loot!") corrects the timer when you are near.
 * Refill times are kept per player in config/safari-patron/honeyhives.json.
 */
object Honeyhives {
    private val log = LoggerFactory.getLogger("safari-patron/honeyhives")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private const val REFILL_MS = 3_600_000L
    private const val CLICK_WINDOW_MS = 5_000L
    private const val RANGE = 160.0
    private const val LABEL_RANGE = 48.0
    private const val READY_COLOR = 0xFFFFC800.toInt()
    private const val WAIT_COLOR = 0xFF9A8A5A.toInt()
    private val countdown = Regex("(?i)^Refill in: (?:(\\d+)m\\s*)?(?:(\\d+)s)?$")

    class Hive(val pos: BlockPos) {
        val key = "${pos.x},${pos.y},${pos.z}"
    }

    val hives: List<Hive> by lazy {
        runCatching {
            Honeyhives::class.java.getResourceAsStream("/assets/safari-patron/honeyhives.txt")!!.bufferedReader().use { r ->
                r.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { line ->
                    val p = line.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
                    if (p.size >= 3) Hive(BlockPos(p[0], p[1], p[2])) else null
                }
            }
        }.getOrElse { log.warn("Honeyhive list unreadable: {}", it.message); emptyList() }
    }
    private val byKey by lazy { hives.associateBy { it.key } }

    /** player uuid -> hive key -> when it is full again (epoch ms). Missing = full. */
    private var store: MutableMap<String, MutableMap<String, Long>> = HashMap()
    private var owner = "unknown"
    private val refill get() = store.getOrPut(owner) { HashMap() }

    private var lastClick: Pair<String, Long>? = null
    private var lastLoot: Pair<String, Long>? = null
    private var tickCounter = 0

    fun enabled() = Config.s.contest && Config.s.honeyhives

    fun init() {
        load()
        UseBlockCallback.EVENT.register { _, level, _, hit -> clicked(level, hit.blockPos); InteractionResult.PASS }
        AttackBlockCallback.EVENT.register { _, level, _, pos, _ -> clicked(level, pos); InteractionResult.PASS }
    }

    private fun load() {
        val raw = Paths.read(Paths.honeyhives) ?: return
        runCatching {
            val out = HashMap<String, MutableMap<String, Long>>()
            for ((who, el) in JsonParser.parseString(raw).asJsonObject.entrySet()) {
                val map = HashMap<String, Long>()
                for ((key, t) in el.asJsonObject.entrySet()) map[key] = t.asLong
                out[who] = map
            }
            store = out
        }.onFailure { log.warn("Honeyhive timers unreadable: {}", it.message) }
    }

    private fun save() = Paths.write(Paths.honeyhives, gson.toJson(store))

    private fun clicked(level: Level, pos: BlockPos) {
        if (!enabled() || !level.isClientSide) return
        val hive = byKey["${pos.x},${pos.y},${pos.z}"] ?: return
        lastClick = hive.key to System.currentTimeMillis()
    }

    /** Chat: "HIVE! ..." after looting the clicked hive, "QUEEN BEE! ..." when it stays full. */
    fun onMessage(raw: String) {
        if (!enabled()) return
        val now = System.currentTimeMillis()
        for (line in ChatParser.clean(raw).split('\n').map { it.trim() }) {
            when {
                line.startsWith("HIVE!") -> {
                    val key = lastClick?.takeIf { now - it.second in 0..CLICK_WINDOW_MS }?.first ?: continue
                    lastClick = null
                    lastLoot = key to now
                    refill[key] = now + REFILL_MS
                    save()
                }
                line.startsWith("QUEEN BEE!") -> {
                    val key = (lastLoot ?: lastClick)?.takeIf { now - it.second in 0..CLICK_WINDOW_MS }?.first ?: continue
                    lastLoot = null
                    if (refill.remove(key) != null) save()
                }
            }
        }
    }

    fun tick(client: Minecraft) {
        val player = client.player ?: return
        owner = player.uuid.toString()
        if (!enabled() || ++tickCounter % 20 != 0) return
        if (hives.none { player.distanceToSqr(it.pos.x + .5, it.pos.y + .5, it.pos.z + .5) <= 96.0 * 96.0 }) return
        readLabels(client)
    }

    /** The server's countdown above each hive near you. */
    private fun readLabels(client: Minecraft) {
        val level = client.level ?: return
        val player = client.player ?: return
        val now = System.currentTimeMillis()
        var changed = false
        for (entity in level.entitiesForRendering()) {
            if (entity.isRemoved || player.distanceToSqr(entity) > 96.0 * 96.0) continue
            val text = (if (entity is Display.TextDisplay) entity.text.string else entity.customName?.string) ?: continue
            for (line in ChatParser.clean(text).replace(' ', ' ').lines().map { it.trim() }) {
                val ready = line.equals("Click to loot!", ignoreCase = true)
                val left = countdownMs(line)
                if (!ready && left == null) continue
                val hive = hives.minByOrNull { h ->
                    val dx = h.pos.x + .5 - entity.x; val dz = h.pos.z + .5 - entity.z
                    dx * dx + dz * dz
                } ?: continue
                if (abs(entity.x - hive.pos.x - .5) > 1.5 || abs(entity.z - hive.pos.z - .5) > 1.5 ||
                    entity.y !in (hive.pos.y - 1.0)..(hive.pos.y + 4.0)) continue
                // Just looted: give the old label a moment to update
                if (lastLoot?.let { it.first == hive.key && now - it.second < 3000 } == true) continue
                if (ready) {
                    if (refill.remove(hive.key) != null) changed = true
                } else {
                    val at = now + left!!
                    val old = refill[hive.key]
                    if (old == null || abs(old - at) > 2000) { refill[hive.key] = at; changed = true }
                }
            }
        }
        if (changed) save()
    }

    private fun countdownMs(text: String): Long? {
        val m = countdown.matchEntire(text) ?: return null
        if (m.groupValues[1].isEmpty() && m.groupValues[2].isEmpty()) return null
        val ms = (m.groupValues[1].toLongOrNull() ?: 0) * 60_000 + (m.groupValues[2].toLongOrNull() ?: 0) * 1000
        return ms.takeIf { it in 0..REFILL_MS }
    }

    fun isReady(hive: Hive, now: Long = System.currentTimeMillis()) = (refill[hive.key] ?: 0L) <= now
    fun readyCount(): Int { val now = System.currentTimeMillis(); return hives.count { isReady(it, now) } }
    /** Time until the next hive is full again, or null if none is waiting. */
    fun nextRefillMs(): Long? {
        val now = System.currentTimeMillis()
        return hives.mapNotNull { refill[it.key]?.takeIf { at -> at > now }?.minus(now) }.minOrNull()
    }

    /** Waypoints show during a contest, and (by default) only until your ticket is done. */
    fun showWaypoints() = enabled() && !Contest.betweenContests() &&
        !(Config.s.honeyhivesHideWhenDone && Contest.isComplete())

    fun collect(client: Minecraft, out: WorldRender.Batch) {
        if (!showWaypoints()) return
        val level = client.level ?: return
        val player = client.player ?: return
        val now = System.currentTimeMillis()
        for (hive in hives) {
            val pos = hive.pos
            val distSq = player.distanceToSqr(pos.x + .5, pos.y + .5, pos.z + .5)
            if (distSq > RANGE * RANGE) continue
            // A loaded spot that is no bee nest is not a hive (anymore)
            if (level.isLoaded(pos)) {
                val state = level.getBlockState(pos)
                if (!state.`is`(Blocks.BEE_NEST) && !state.`is`(Blocks.BEEHIVE)) continue
            }
            val top = Vec3(pos.x + .5, pos.y + 1.4, pos.z + .5)
            if (isReady(hive, now)) {
                out.box(AABB(pos).inflate(0.01), READY_COLOR)
                out.label("Honeyhive", top, READY_COLOR, bold = true)
            } else if (distSq <= LABEL_RANGE * LABEL_RANGE) {
                val left = ((refill[hive.key] ?: now) - now).coerceAtLeast(0)
                out.label("${(left + 59_999) / 60_000}m", top, WAIT_COLOR)
            }
        }
    }
}
