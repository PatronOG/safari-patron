package safaripatron.capsule

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import safaripatron.config.Config
import safaripatron.data.Biome
import safaripatron.render.WorldRender
import safaripatron.safari.Run
import safaripatron.safari.Safari

/**
 * Prefire spots: where to stand and where to look to throw a Masterful Critter Capsule at a critter
 * before it shows up. Only shows the spots; aiming and throwing stay with the player.
 * Both marks turn green when you stand on the spot / look at the aim point.
 */
object Prefire {
    /** [stand] = block your feet are in; [aim] = block to look at. */
    class Spot(val critter: String, val biome: Biome, val stand: IntArray, val aim: IntArray)

    val spots = listOf(
        Spot("Doomspiral", Biome.HAUNTED, intArrayOf(11, 48, -29), intArrayOf(-8, 51, -25)),
    )

    /**
     * Doomspiral rises for 134 ticks (6.7 s) after it appears and can only be caught once it stands.
     * A capsule from the stand spot flies ~21 ticks, so the best throw is 116 ticks after it appears
     * (measured in game: throws from 194 ticks after "the ground shifts" hit, earlier ones flew over it).
     */
    private const val THROW_AFTER_TICKS = 116
    private const val THROW_NOW_TICKS = 12

    private const val GREEN = 0xFF55FF55.toInt()
    private const val GOLD = 0xFFFFAA00.toInt()
    private const val AIM_RADIUS = 0.3

    private fun holdingMasterful(): Boolean {
        val stack = Minecraft.getInstance().player?.mainHandItem ?: return false
        return stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "") == "MASTERFUL_CRITTER_CAPSULE"
    }

    // ---------- countdown ----------

    private var tick = 0L
    private var emergedAt = -1L
    private var emergedId = -1
    private var dinged = false
    private var onSpotNow = false

    private fun doomspiralSpot() = spots.first { it.critter == "Doomspiral" }

    private fun onSpot(player: net.minecraft.world.entity.player.Player, s: Spot): Boolean {
        val dx = player.x - (s.stand[0] + 0.5)
        val dz = player.z - (s.stand[2] + 0.5)
        return dx * dx + dz * dz < 0.3 * 0.3 && kotlin.math.abs(player.y - s.stand[1]) < 0.6
    }

    /** Watches for Doomspiral starting to rise (in Haunted, Prefire spots on) and starts the countdown. */
    fun tick(client: Minecraft) {
        tick++
        val player = client.player
        val level = client.level
        if (player == null || level == null || !Config.s.prefireSpots || !Safari.inSafari || Safari.biome != Biome.HAUNTED) {
            emergedAt = -1L; return
        }
        val s = doomspiralSpot()
        onSpotNow = onSpot(player, s)
        val rising = level.entitiesForRendering().firstOrNull { e ->
            e.pose == net.minecraft.world.entity.Pose.EMERGING && e.distanceToSqr(player) < 64.0 * 64.0 &&
                safaripatron.esp.CritterId.identify(e)?.name == "Doomspiral"
        }
        if (rising != null && rising.id != emergedId) {
            emergedId = rising.id
            emergedAt = tick
            dinged = false
        }
        if (emergedAt < 0) return
        val left = THROW_AFTER_TICKS - (tick - emergedAt)
        if (left <= 0 && !dinged) {
            dinged = true
            if (Config.s.prefireSound && holdingMasterful())
                client.soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL, 1.6f, 1f))
        }
        if (left < -THROW_NOW_TICKS) emergedAt = -1L
    }

    /** "Throw in 4.2s" under the crosshair while holding a Masterful Capsule, then "Throw Now". */
    fun drawHud(g: net.minecraft.client.gui.GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, width: Int, height: Int) {
        if (emergedAt < 0 || !holdingMasterful()) return
        val left = THROW_AFTER_TICKS - (tick - emergedAt)
        val cx = width / 2
        val y = height / 2 + 14
        val (text, color) = if (left <= 0) "Throw Now" to GREEN
            else String.format(java.util.Locale.ROOT, "Throw in %.1fs", left / 20.0) to GOLD
        g.pose().pushMatrix()
        g.pose().translate(cx.toFloat(), y.toFloat())
        g.pose().scale(2f, 2f)
        g.text(font, text, -font.width(text) / 2, 0, color, true)
        g.pose().popMatrix()
        if (!onSpotNow) {
            val warn = "Not on the spot"
            g.text(font, warn, cx - font.width(warn) / 2, y + 20, 0xFFFF5555.toInt(), true)
        }
    }

    fun collect(client: Minecraft, out: WorldRender.Batch) {
        if (!Config.s.prefireSpots || !Safari.inSafari) return
        val player = client.player ?: return
        if (!holdingMasterful()) return
        for (s in spots) {
            if (Safari.biome != s.biome || s.critter in Run.caught) continue
            val (sx, sy, sz) = Triple(s.stand[0], s.stand[1], s.stand[2])
            val center = Vec3(sx + 0.5, sy.toDouble(), sz + 0.5)
            val dx = player.x - center.x
            val dz = player.z - center.z
            val onSpot = dx * dx + dz * dz < 0.3 * 0.3 && kotlin.math.abs(player.y - sy) < 0.6
            val standColor = if (onSpot) GREEN else GOLD
            out.box(AABB(sx.toDouble(), sy.toDouble(), sz.toDouble(), sx + 1.0, sy + 0.05, sz + 1.0), standColor)
            out.box(AABB(sx + 0.35, sy.toDouble(), sz + 0.35, sx + 0.65, sy + 0.05, sz + 0.65), standColor)
            out.label(if (onSpot) "✔ Stand here" else "Stand here", center.add(0.0, 0.6, 0.0), standColor, bold = true)
            out.label("${s.critter} prefire", center.add(0.0, 0.6, 0.0), 0xFFAAAAAA.toInt(), line = 1)

            // Aim point: green while your crosshair line passes through it
            val aim = Vec3(s.aim[0] + 0.5, s.aim[1] + 0.5, s.aim[2] + 0.5)
            val eye = player.getEyePosition(client.deltaTracker.getGameTimeDeltaPartialTick(false))
            val look = player.lookAngle.normalize()
            val toAim = aim.subtract(eye)
            val along = toAim.dot(look)
            val offLine = if (along > 0) toAim.subtract(look.scale(along)).length() else Double.MAX_VALUE
            val onAim = offLine < AIM_RADIUS
            val aimColor = if (onAim) GREEN else GOLD
            val r = AIM_RADIUS
            out.box(AABB(aim.x - r, aim.y - r, aim.z - r, aim.x + r, aim.y + r, aim.z + r), aimColor)
            out.line(aim.add(-0.6, 0.0, 0.0), aim.add(0.6, 0.0, 0.0), aimColor)
            out.line(aim.add(0.0, -0.6, 0.0), aim.add(0.0, 0.6, 0.0), aimColor)
            out.line(aim.add(0.0, 0.0, -0.6), aim.add(0.0, 0.0, 0.6), aimColor)
            out.label(if (onAim) "✔ Aim here" else "Aim here", aim.add(0.0, 0.8, 0.0), aimColor, bold = true)
        }
    }
}
