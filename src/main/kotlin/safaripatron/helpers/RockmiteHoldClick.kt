package safaripatron.helpers

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import safaripatron.config.Config
import safaripatron.data.Biome
import safaripatron.esp.CritterId
import safaripatron.mixin.AttackInvoker
import safaripatron.safari.Run
import safaripatron.safari.Safari
import safaripatron.safari.ChatParser
import java.util.UUID

object RockmiteHoldClick {
    private const val MOUND = "5dbaab74d1acd0abe9d04abe9928725de5d4495fcb63b647228caf6944c20800"
    private val clock = MoundClickClock()
    private var world: Any? = null
    private var run = -1L
    private val finished = HashSet<UUID>()
    private val attempts = ArrayDeque<Pair<UUID, Long>>()
    private val sites by lazy {
        RockmiteHoldClick::class.java.getResourceAsStream("/assets/safari-patron/mound_sites.txt")!!.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { line ->
                val p = line.split(' ').map(String::toInt); Triple(p[0], p[1], p[2])
            }.toSet()
        }
    }
    private fun mound(entity: Entity, client: Minecraft): Boolean {
        if (entity.isRemoved || entity.isInvisible || client.level?.getEntity(entity.id) !== entity) return false
        if (entity is Display.ItemDisplay) return CritterId.texture(entity.itemStack) == MOUND
        if (entity.type != EntityTypes.INTERACTION) return false
        val level = client.level ?: return false
        val pos = entity.blockPosition()
        val occupied = level.entitiesForRendering().any { other ->
            !other.isRemoved && other is LivingEntity && other !is Player && other !is ArmorStand &&
                kotlin.math.abs(other.x - entity.x) <= .35 && kotlin.math.abs(other.z - entity.z) <= .35 &&
                kotlin.math.abs(other.y - entity.y) <= 1.0
        }
        return MoundTargetRules.matches(entity.x, entity.y, entity.z, entity.boundingBox.xsize, entity.boundingBox.ysize,
            Triple(pos.x, pos.y, pos.z) in sites,
            level.getFluidState(pos).isEmpty && level.getFluidState(pos.below()).isEmpty, occupied)
    }
    fun prepare(client: Minecraft) {
        if (world !== client.level || run != Run.startStamp) {
            world = client.level; run = Run.startStamp; finished.clear(); attempts.clear(); clock.tick(false)
        }
        val player = client.player
        val target = (client.hitResult as? EntityHitResult)?.entity
        val active = MoundClickConditions(Config.s.rockmiteHoldClick,
            Safari.inSafari && Run.running && Safari.biome == Biome.CAVERN,
            client.mouseHandler.isLeftPressed, client.isWindowActive && client.mouseHandler.isMouseGrabbed && player?.isAlive == true,
            client.gui.screen() != null, player?.isUsingItem == true,
            client.gameMode?.isDestroying == true, target != null && target.uuid !in finished && mound(target, client)).allowed
        val click = clock.tick(active)
        if (!active) return
        while (client.options.keyAttack.consumeClick()) { }
        if (click && target != null) {
            val now = System.currentTimeMillis()
            attempts.removeAll { now - it.second > 2500 }
            attempts.addLast(target.uuid to now)
            while (attempts.size > 64) attempts.removeFirst()
            (client as AttackInvoker).safariPatronAttack()
        }
    }
    fun onMessage(raw: String) {
        if (!Safari.inSafari || world !== Minecraft.getInstance().level || run != Run.startStamp) return
        if (raw.lines().map(ChatParser::clean).none {
            it.startsWith("The mound falls apart, but nothing is inside") ||
                it.startsWith("The mound fell apart, revealing a Rockmite hidden inside")
        }) return
        val now = System.currentTimeMillis()
        attempts.removeAll { now - it.second > 2500 }
        attempts.map { it.first }.distinct().singleOrNull()?.let(finished::add)
        attempts.clear()
    }
}
