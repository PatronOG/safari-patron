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
    /** A block to look at; [step] = which throw it is for (0 = first). */
    class Aim(val pos: IntArray, val label: String, val step: Int = 0)

    /**
     * [stand] = block your feet are in. [anyCapsule]: also shown with a normal Critter Capsule.
     * [aimsOnSpot]: aim points only show while you stand on the spot.
     */
    class Spot(
        val critter: String, val biome: Biome, val stand: IntArray, val aims: List<Aim>,
        val anyCapsule: Boolean = false, val aimsOnSpot: Boolean = false,
        val enabled: () -> Boolean = { true },
    )

    val spots = listOf(
        Spot("Doomspiral", Biome.HAUNTED, intArrayOf(11, 48, -29), listOf(Aim(intArrayOf(-8, 51, -25), "Aim here")),
            enabled = { Config.s.doomspiralTimer }),
        // Wumpa skip: a normal capsule first, the Masterful one right after it
        Spot("Wumpa", Biome.ICY, intArrayOf(-95, 79, -67), listOf(
            Aim(intArrayOf(-117, 89, -68), "1 · Capsule", 0),
            Aim(intArrayOf(-111, 87, -75), "2 · Masterful", 1),
        ), anyCapsule = true, aimsOnSpot = true, enabled = { Config.s.wumpaSkip }),
    )

    private const val DIM = 0xFF777777.toInt()
    /** Wumpa skip: the Masterful throw comes this long after the first capsule (Helpers → Masterful delay). */
    private val masterfulDelayTicks get() = safaripatron.config.Config.s.masterfulDelayTenths.coerceIn(5, 30) * 2

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

    private fun holdingAnyCapsule() = CapsuleModel.isCapsule(Minecraft.getInstance().player?.mainHandItem)

    // Two-throw spots (Wumpa): which throw is next, and "Masterful now!" after the first one
    private var step = 0
    private var stepSpot: Spot? = null
    /** Tick of the first throw on a two-throw spot, -1 = none. */
    private var firstThrowAt = -1L
    private var masterfulDinged = false

    /** A capsule thrown while standing on a two-throw spot moves it to the next aim point. */
    fun init() {
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register { player, level, hand ->
            if (level.isClientSide && player === Minecraft.getInstance().player &&
                CapsuleModel.isCapsule(player.getItemInHand(hand))) {
                val s = spots.firstOrNull { it.aims.size > 1 && it.enabled() && Safari.biome == it.biome && onSpot(player, it) }
                if (s != null && step == 0) {
                    step = 1
                    stepSpot = s
                    firstThrowAt = tick
                    masterfulDinged = false
                }
            }
            net.minecraft.world.InteractionResult.PASS
        }
    }

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

    private fun look(client: Minecraft, player: net.minecraft.world.entity.player.Player, aim: Vec3): Boolean {
        val eye = player.getEyePosition(client.deltaTracker.getGameTimeDeltaPartialTick(false))
        val look = player.lookAngle.normalize()
        val toAim = aim.subtract(eye)
        val along = toAim.dot(look)
        return along > 0 && toAim.subtract(look.scale(along)).length() < AIM_RADIUS
    }

    private fun onSpot(player: net.minecraft.world.entity.player.Player, s: Spot): Boolean {
        // Anywhere on the stand block counts
        val dx = player.x - (s.stand[0] + 0.5)
        val dz = player.z - (s.stand[2] + 0.5)
        return kotlin.math.abs(dx) <= 0.5 && kotlin.math.abs(dz) <= 0.5 && kotlin.math.abs(player.y - s.stand[1]) < 0.6
    }

    /** Watches for Doomspiral starting to rise (in Haunted, Prefire spots on) and starts the countdown. */
    fun tick(client: Minecraft) {
        tick++
        val player = client.player
        val level = client.level
        if (player == null || level == null || !Safari.inSafari) {
            emergedAt = -1L; step = 0; stepSpot = null; firstThrowAt = -1L; return
        }
        stepSpot?.let { if (!onSpot(player, it)) { step = 0; stepSpot = null; firstThrowAt = -1L } }
        // Wumpa skip: bell when the 1-second wait after the first capsule is over
        if (firstThrowAt >= 0) {
            val left = masterfulDelayTicks - (tick - firstThrowAt)
            if (left <= 0 && !masterfulDinged) {
                masterfulDinged = true
                if (Config.s.prefireSound)
                    client.soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1.6f, 1f))
            }
            if (left < -THROW_NOW_TICKS) firstThrowAt = -1L
        }
        if (Safari.biome != Biome.HAUNTED || !Config.s.doomspiralTimer) { emergedAt = -1L; return }
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
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1.6f, 1f))
        }
        if (left < -THROW_NOW_TICKS) emergedAt = -1L
    }

    /** "Throw in 4.2s" under the crosshair while holding a Masterful Capsule, then "Throw Now". */
    fun drawHud(g: net.minecraft.client.gui.GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, width: Int, height: Int) {
        if (firstThrowAt >= 0 && holdingAnyCapsule()) {
            val left = masterfulDelayTicks - (tick - firstThrowAt)
            val (text, color) = if (left <= 0) "Throw Now" to GREEN
                else String.format(java.util.Locale.ROOT, "Masterful in %.1fs", left / 20.0) to GOLD
            g.pose().pushMatrix()
            g.pose().translate((width / 2).toFloat(), (height / 2 + 14).toFloat())
            g.pose().scale(2f, 2f)
            g.text(font, text, -font.width(text) / 2, 0, color, true)
            g.pose().popMatrix()
            return
        }
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
        if (!Safari.inSafari) return
        val player = client.player ?: return
        val anyCapsule = holdingAnyCapsule()
        val masterful = holdingMasterful()
        for (s in spots) {
            if (!s.enabled() || Safari.biome != s.biome || s.critter in Run.caught) continue
            if (!(masterful || (s.anyCapsule && anyCapsule))) continue
            val (sx, sy, sz) = Triple(s.stand[0], s.stand[1], s.stand[2])
            val center = Vec3(sx + 0.5, sy.toDouble(), sz + 0.5)
            val on = onSpot(player, s)
            val standColor = if (on) GREEN else GOLD
            out.box(AABB(sx.toDouble(), sy.toDouble(), sz.toDouble(), sx + 1.0, sy + 0.05, sz + 1.0), standColor)
            out.box(AABB(sx + 0.35, sy.toDouble(), sz + 0.35, sx + 0.65, sy + 0.05, sz + 0.65), standColor)
            out.label(if (on) "✔ Stand here" else "Stand here", center.add(0.0, 0.6, 0.0), standColor, bold = true)
            val name = if (s.aims.size > 1) "${s.critter} skip" else "${s.critter} prefire"
            out.label(name, center.add(0.0, 0.6, 0.0), 0xFFAAAAAA.toInt(), line = 1)
            if (s.aimsOnSpot && !on) continue

            // Aim points: the next one in gold (green while you look at it), the others gray
            val next = if (stepSpot === s) step else 0
            for (a in s.aims) {
                val aim = Vec3(a.pos[0] + 0.5, a.pos[1] + 0.5, a.pos[2] + 0.5)
                val active = a.step == next
                val onAim = active && look(client, player, aim)
                val color = when { onAim -> GREEN; active -> GOLD; else -> DIM }
                val r = AIM_RADIUS
                out.box(AABB(aim.x - r, aim.y - r, aim.z - r, aim.x + r, aim.y + r, aim.z + r), color)
                out.line(aim.add(-0.6, 0.0, 0.0), aim.add(0.6, 0.0, 0.0), color)
                out.line(aim.add(0.0, -0.6, 0.0), aim.add(0.0, 0.6, 0.0), color)
                out.line(aim.add(0.0, 0.0, -0.6), aim.add(0.0, 0.0, 0.6), color)
                out.label(if (onAim) "✔ ${a.label}" else a.label, aim.add(0.0, 0.8, 0.0), color, bold = active)
            }
        }
    }
}
