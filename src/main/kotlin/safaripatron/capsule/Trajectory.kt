package safaripatron.capsule

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import safaripatron.config.Config
import safaripatron.esp.CritterId
import safaripatron.render.WorldRender

/**
 * Critter Capsule uçuş modeli:
 *   p(n) = göz + SPEED·n·yön + (0, ½·G·n·(n−1), 0), sürtünme yok.
 * SPEED ve G kendi 96 atışlık kalibrasyonumuzla düzeltildi
 * (pitch −86°…+66°, ~36 bloğa kadar uçuşta ort. sapma 0.02 blok).
 */
object CapsuleModel {
    const val SPEED = 0.8482482249942948
    const val G = -0.019915
    const val START_N = 2.0
    /** Doğrulanmış bölge (~36 blok yatay). Sonrası sarı = tahmin. */
    const val CALIBRATED_END_N = 65.0
    const val MAX_N = 80.0

    fun point(eye: Vec3, dir: Vec3, n: Double) = Vec3(
        eye.x + SPEED * n * dir.x,
        eye.y + SPEED * n * dir.y + 0.5 * G * n * (n - 1),
        eye.z + SPEED * n * dir.z,
    )

    fun isCapsule(stack: ItemStack?): Boolean {
        val id = stack?.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "")
        return id == "CRITTER_CAPSULE" || id == "MASTERFUL_CRITTER_CAPSULE"
    }
}

object Trajectory {
    private const val GREEN = 0xFF55FF55.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val YELLOW = 0xFFFFD24A.toInt()
    private const val RED = 0xFFFF5555.toInt()
    private const val STEP = 0.25

    private fun holding(p: Player) = CapsuleModel.isCapsule(p.mainHandItem)

    fun collect(client: Minecraft, out: WorldRender.Batch) {
        if (!Config.s.capsuleTrajectory) return
        val player = client.player ?: return
        val level = client.level ?: return
        if (!holding(player)) return

        val partial = client.deltaTracker.getGameTimeDeltaPartialTick(false)
        val eye = player.getEyePosition(partial)
        val dir = player.lookAngle.normalize()

        // Hedefler: tanınan critter'lar (ESP ile aynı kutular) + isabet payı
        val targets = ArrayList<AABB>()
        for (e in level.entitiesForRendering()) {
            if (e === player || e.distanceToSqr(player) > 64.0 * 64.0) continue
            val critter = CritterId.identify(e) ?: continue
            targets.add(safaripatron.esp.BoxTuner.box(e, critter, capsule = true).inflate(Config.s.hitMargin))
        }

        val points = ArrayList<Pair<Vec3, Boolean>>()
        var prev = eye
        var n = CapsuleModel.START_N
        var hitBox: AABB? = null
        var blockHit: Vec3? = null
        while (n <= CapsuleModel.MAX_N) {
            val cur = CapsuleModel.point(eye, dir, n)
            val calibrated = n <= CapsuleModel.CALIBRATED_END_N
            val clip = level.clip(ClipContext(prev, cur, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            val blocked = clip.type != HitResult.Type.MISS
            val segEnd = if (blocked) clip.location else cur

            var best = Double.MAX_VALUE
            var bestPoint: Vec3? = null
            var bestBox: AABB? = null
            for (t in targets) {
                val hit = t.clip(prev, segEnd)
                if (hit.isPresent) {
                    val d = hit.get().distanceToSqr(prev)
                    if (d < best) { best = d; bestPoint = hit.get(); bestBox = t }
                }
            }
            if (bestPoint != null) { hitBox = bestBox; points.add(bestPoint to calibrated); break }
            if (blocked) { blockHit = clip.location; points.add(clip.location to calibrated); break }
            points.add(cur to calibrated)
            prev = cur
            n += STEP
        }

        val hit = hitBox != null
        for ((a, b) in points.zipWithNext()) {
            out.line(a.first, b.first, if (hit) GREEN else if (b.second) WHITE else YELLOW)
        }
        hitBox?.let { out.box(it, GREEN) }
        blockHit?.let { p ->
            val s = 0.12
            out.box(AABB(p.x - s, p.y - s, p.z - s, p.x + s, p.y + s, p.z + s), RED)
        }
    }
}
