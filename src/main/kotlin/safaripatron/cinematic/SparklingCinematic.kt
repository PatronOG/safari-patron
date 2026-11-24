package safaripatron.cinematic

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import safaripatron.SafariPatron
import safaripatron.data.Critters
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Sparkling Finder Cinematic: a "SPARKLING SPOTTED" scene when the finder spots a sparkling.
 * Letterbox bars, the title letter by letter, a slot machine over the critters of that biome with their models,
 * the sparkling's own 3D model landing in the middle and an extra effect per rarity.
 * The HUD steps aside while it plays. The game never stops; nothing is sent to the server.
 */
object SparklingCinematic {
    private val LOG = org.slf4j.LoggerFactory.getLogger("safari-patron/cinematic")
    private const val GOLD = 0xFFD84A
    /** Height of each letterbox bar, as a share of the screen height. */
    private const val BAR = .11f
    private val TITLE_FONT = net.minecraft.network.chat.FontDescription.Resource(
        net.minecraft.resources.Identifier.fromNamespaceAndPath("safari-patron", "title"))
    // Tiny5 is drawn on an 8 pixel grid; twice that keeps every font pixel two GUI pixels wide
    private const val TITLE_SCALE = 2f
    /** Where the model sits (share of the screen height) and how big it is, in GUI pixels. */
    private const val MODEL_Y = .48f
    private const val MODEL_SIZE = 115f
    private const val TITLE = "✦ SPARKLING SPOTTED ✦"
    private const val SLOT_START = 500L
    private const val LAND = 1800L
    private const val FADE = 4200L
    private const val END = 4800L
    /** How long the model swells before it pops at the end, in ms. */
    private const val POP_OUT = 180L

    private var startedAt = 0L
    private var final = ""
    private var rarityColor = GOLD
    private var rarity: safaripatron.data.Rarity? = null

    /** A critter's rarity color for its name. */
    private fun rarityOf(name: String) =
        (Critters.all.firstOrNull { it.name.equals(name, true) }?.rarity?.color ?: 0xFFBBBBBB.toInt()) and 0xFFFFFF
    /** The critters of the biome the slot machine rolls through. */
    private var names: List<String> = emptyList()
    /** When each slot name shows (ms after start), worked out once so the roll slows down smoothly. */
    private val slotTimes = ArrayList<Long>()
    private var landed = false
    /** The last slot name a tick sound was played for. */
    private var lastTick = -1
    /** The model has popped away at the end. */
    private var popped = false
    /** Each biome critter's model, so the slot machine shows the model with every name it rolls through. */
    private var rollModels: Map<String, List<Live>> = emptyMap()
    /** What the model came from, and the last thing that went wrong (logged; the test tools show them). */
    var source = ""
        private set
    var lastError: String? = null

    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float,
                    val decay: Float, val size: Float, val color: Int, val star: Boolean, val gravity: Float,
                    val drag: Float = .96f, val front: Boolean = false, val floor: Float = -1f, val coin: Boolean = false,
                    val fly: Boolean = false, val twinkle: Boolean = false, val phase: Float = 0f)
    private val particles = ArrayList<P>()
    private var lastFrame = 0L

    val active get() = startedAt != 0L && now() - startedAt < END
    private fun now() = System.currentTimeMillis()

    fun init() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath(SafariPatron.MOD_ID, "sparkling_cinematic"),
        ) { g, _ -> render(g) }
        // While it plays, the rest of the HUD steps aside: chat (unless you open it), hotbar, bars,
        // scoreboard and titles (our own panels check for it themselves)
        val hidden = listOf(
            VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
            VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR,
            VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
            VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.SPECTATOR_TOOLTIP, VanillaHudElements.MOB_EFFECTS,
            VanillaHudElements.BOSS_BAR, VanillaHudElements.SCOREBOARD, VanillaHudElements.OVERLAY_MESSAGE,
            VanillaHudElements.TITLE_AND_SUBTITLE, VanillaHudElements.CHAT, VanillaHudElements.PLAYER_LIST,
        )
        for (id in hidden) {
            runCatching {
                HudElementRegistry.replaceElement(id) { original ->
                    HudElement { g, delta ->
                        val chatOpen = id == VanillaHudElements.CHAT && Minecraft.getInstance().gui.screen() is net.minecraft.client.gui.screens.ChatScreen
                        if (!active || chatOpen) original.extractRenderState(g, delta)
                    }
                }
            }.onFailure { LOG.warn("Sparkling cinematic: couldn't hide HUD element {}", id, it) }
        }
    }

    /** The critter's real entities, drawn by the game's own renderer, with their offset from the critter's center. */
    class Live(val entity: Entity, val dx: Double, val dy: Double, val dz: Double)
    private var living: List<Live> = emptyList()

    /**
     * The real critter as you see it: a mob itself, or every head-wearing armor stand and item display
     * that builds it (Shyworm's segments, Gimmiegold's item).
     */
    fun entitiesAround(e: Entity?): List<Live> {
        val level = Minecraft.getInstance().level ?: return emptyList()
        if (e == null) return emptyList()
        if (e !is ArmorStand && e !is Display.ItemDisplay) return listOf(Live(e, 0.0, 0.0, 0.0))
        val pieces = level.entitiesForRendering().filter { o ->
            !o.isRemoved && kotlin.math.abs(o.x - e.x) < 3 && kotlin.math.abs(o.z - e.z) < 3 && kotlin.math.abs(o.y - e.y) < 3 &&
                when (o) {
                    is ArmorStand -> safaripatron.esp.CritterId.texture(o.getItemBySlot(EquipmentSlot.HEAD)) != null
                    is Display.ItemDisplay -> safaripatron.esp.CritterId.texture(o.itemStack) != null
                    else -> false
                }
        }.ifEmpty { listOf(e) }
        val cx = pieces.sumOf { it.x } / pieces.size
        val cy = pieces.minOf { it.y }
        val cz = pieces.sumOf { it.z } / pieces.size
        return pieces.map { Live(it, it.x - cx, it.y - cy, it.z - cz) }
    }

    /**
     * Draws [e] centered on (cx, cy) at [perBlock] pixels per block, turned by [spin] degrees, the same way
     * the inventory draws your player (InventoryScreen.extractEntityInInventoryFollowsMouse).
     */
    private val CAMERA: org.joml.Quaternionfc = org.joml.Quaternionf().rotateX(Math.toRadians(-15.0).toFloat())
    private fun rad(d: Float) = Math.toRadians(d.toDouble()).toFloat()

    private fun drawEntity(g: GuiGraphicsExtractor, e: Entity, cx: Int, cy: Int, perBlock: Float, spin: Float, box: Int,
                           tweak: CritterTweaks.Tweak? = null): Boolean {
        // Seen a little from above
        val pose = org.joml.Quaternionf().rotateZ(PI.toFloat()).mul(CAMERA).rotateY(rad(spin))
        if (tweak != null) pose.rotateY(rad(tweak.yaw)).rotateX(rad(tweak.pitch)).rotateZ(rad(tweak.roll))
        return drawPosed(g, e, cx, cy, perBlock, pose, null, box)
    }

    /**
     * Draws [e] turned by [pose] so the point [pivotY] blocks up its middle (half its height when null)
     * lands on (cx, cy) and everything turns around that point.
     */
    private fun drawPosed(g: GuiGraphicsExtractor, e: Entity, cx: Int, cy: Int, perBlock: Float, pose: org.joml.Quaternionf,
                          pivotY: Float?, box: Int, headPivot: Boolean = false): Boolean = try {
        // A mannequin (Hideyho) only puts on its downloaded skin when it ticks; screen-only copies never do
        if (e is net.minecraft.client.entity.ClientMannequin && e.skin === net.minecraft.client.entity.ClientMannequin.DEFAULT_SKIN) e.tick()
        val state = Minecraft.getInstance().entityRenderDispatcher.getRenderer(e).createRenderState(e, 1f)
        state.shadowPieces.clear()
        state.outlineColor = 0
        // Keep the critter's own scale (Hypixel resizes some): the fitting below measures the scaled size too
        if (state is net.minecraft.client.renderer.entity.state.LivingEntityRenderState) {
            // Head straight ahead and standing still, whatever it was doing when it was captured
            state.xRot = 0f; state.yRot = 0f
            state.walkAnimationPos = 0f; state.walkAnimationSpeed = 0f
            // Fish lie on their side out of water
            state.isInWater = true
        }
        // Fluffling lies on its back
        if (state is net.minecraft.client.renderer.entity.state.PandaRenderState) { state.lieOnBackAmount = 1f; state.sitAmount = 0f; state.rollAmount = 0f }
        // An armor stand's worn head sits at the same share of its height at any size (small, or resized by Hypixel)
        val pivot = if (headPivot) state.boundingBoxHeight * HEAD_SHARE else pivotY ?: (state.boundingBoxHeight / 2f)
        val translation = org.joml.Vector3f(0f, pivot, 0f)
        g.entity(state, perBlock, translation, pose, org.joml.Quaternionf(CAMERA), cx - box, cy - box, cx + box, cy + box)
        true
    } catch (t: Throwable) {
        if (lastError == null) LOG.warn("Sparkling cinematic: entity draw failed for {}", e.type, t)
        lastError = "drawing the model: ${t.javaClass.simpleName} ${t.message ?: ""}".trim()
        false
    }

    /**
     * Draws a critter made of [pieces] so it fits in [size] pixels around (cx, cy), turned by [spin] degrees.
     * Each piece keeps its place around the center while the whole thing turns. [clip] > 0 limits each
     * piece's drawing box (gallery cells). False when nothing could be drawn.
     */
    /** Display entities count as 0 x 0 in the game, but what they show is about a block. */
    private fun h(e: Entity) = if (e is Display) 1f else e.bbHeight
    private fun w(e: Entity) = if (e is Display) 1f else e.bbWidth

    // Shyworm's still wave, set in the preview: segment spacing and wave height in blocks,
    // how far along the wave each segment sits, and where on the wave the head starts
    private const val WORM_GAP = .7
    private const val WORM_HEIGHT = .45
    private const val WORM_PHASE = .6
    private const val WORM_START = 2.9
    /**
     * Middle of an armor stand's worn head as a share of its height: the neck is 1.44 blocks up a 1.975 block
     * stand and a worn skull (1.1875 times a head) reaches about 0.3 blocks above it.
     */
    private const val HEAD_SHARE = 1.735f / 1.975f

    fun drawPieces(g: GuiGraphicsExtractor, pieces: List<Live>, cx: Int, cy: Int, size: Float, spin: Float, clip: Int,
                   tweak: CritterTweaks.Tweak? = null, name: String? = null): Boolean {
        val alive = pieces.filter { !it.entity.isRemoved }
        if (alive.isEmpty() || size <= 1f) return false
        if (name.equals("Shyworm", true) && alive.size > 1) return drawWorm(g, alive, cx, cy, size, spin, clip, tweak)
        val height = alive.maxOf { it.dy + h(it.entity) }.toFloat().coerceAtLeast(.5f)
        val width = alive.maxOf { kotlin.math.sqrt(it.dx * it.dx + it.dz * it.dz) * 2 + w(it.entity) }.toFloat().coerceAtLeast(.5f)
        val perBlock = size / maxOf(height, width) * (tweak?.scale ?: 1f)
        // Room for the model turning (its corners reach further than its width) and for the hand fixes
        val box = if (clip > 0) clip else (maxOf(height, width) * perBlock * 1.6f).toInt() + 12
        val rad = Math.toRadians(spin.toDouble())
        val centerY = cy + height * perBlock / 2f - (tweak?.lift ?: 0f) * perBlock
        var drawn = false
        // Back pieces first, so the front ones end up on top
        for (l in alive.sortedBy { -(it.dx * kotlin.math.sin(rad) + it.dz * kotlin.math.cos(rad)) }) {
            val ox = (l.dx * kotlin.math.cos(rad) + l.dz * kotlin.math.sin(rad)) * perBlock
            val oy = -(l.dy + h(l.entity) / 2) * perBlock
            if (drawEntity(g, l.entity, (cx + ox + (tweak?.side ?: 0f) * perBlock).toInt(), (centerY + oy).toInt(), perBlock, spin, box, tweak)) drawn = true
        }
        return drawn
    }

    /**
     * Shyworm on a still wave: the captured pieces were frozen mid-animation, so they are laid out here
     * instead, the mouth head in front looking along the row, each segment leaning with the wave. It never spins (a spinning long row swings
     * its tail off line); only the hand-set Turn applies.
     */
    private fun drawWorm(g: GuiGraphicsExtractor, alive: List<Live>, cx: Int, cy: Int, size: Float, spin: Float, clip: Int,
                         tweak: CritterTweaks.Tweak?): Boolean {
        val textures = alive.map { (it.entity as? ArmorStand)?.let { a -> safaripatron.esp.CritterId.texture(a.getItemBySlot(EquipmentSlot.HEAD)) } }
        val body = textures.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val mouth = alive.indices.firstOrNull { textures[it] != body }
        val ordered = (listOfNotNull(mouth) + alive.indices.filter { it != mouth }).map { alive[it] }
        val n = ordered.size
        val width = ((n - 1) * WORM_GAP + .7).toFloat()
        val height = (WORM_HEIGHT * 2 + 1).toFloat()
        val perBlock = size / maxOf(width, height) * (tweak?.scale ?: 1f)
        val box = if (clip > 0) clip else (maxOf(width, height) * perBlock * .5f + perBlock).toInt() + 12
        val layout = org.joml.Quaternionf().rotateZ(PI.toFloat()).mul(CAMERA)
        if (tweak != null) layout.rotateY(rad(tweak.yaw)).rotateX(rad(tweak.pitch)).rotateZ(rad(tweak.roll))
        val centerY = cy - (tweak?.lift ?: 0f) * perBlock
        val centerX = cx + (tweak?.side ?: 0f) * perBlock
        class Seg(val e: Entity, val sx: Float, val sy: Float, val depth: Float, val pose: org.joml.Quaternionf)
        val segs = ordered.mapIndexed { i, l ->
            val w = WORM_START - i * WORM_PHASE
            val y = WORM_HEIGHT * kotlin.math.sin(w)
            val slope = WORM_HEIGHT * WORM_PHASE * kotlin.math.cos(w) / WORM_GAP
            val at = layout.transform(org.joml.Vector3f((((n - 1) / 2.0 - i) * WORM_GAP).toFloat(), y.toFloat(), 0f))
            val pose = org.joml.Quaternionf(layout).rotateZ(kotlin.math.atan(slope).toFloat())
            // The mouth looks ahead, along the row
            if (i == 0 && mouth != null) pose.rotateY(rad(90f))
            Seg(l.entity, centerX + at.x * perBlock, centerY + at.y * perBlock, at.z, pose)
        }
        // Pulled to the middle of its box, however the wave and the turn place the row
        val offX = (segs.minOf { it.sx } + segs.maxOf { it.sx }) / 2 - centerX
        val offY = (segs.minOf { it.sy } + segs.maxOf { it.sy }) / 2 - centerY
        var drawn = false
        // Back pieces first, so the front ones end up on top
        for (s in segs.sortedByDescending { it.depth }) {
            if (drawPosed(g, s.e, (s.sx - offX).toInt(), (s.sy - offY).toInt(), perBlock, s.pose, null, box, headPivot = s.e is ArmorStand)) drawn = true
        }
        return drawn
    }

    /** Plays the scene for [species]; [live] is the critter as it stands in the world, when it is near. */
    fun start(species: String, live: List<Live> = emptyList()) {
        val critter = Critters.all.firstOrNull { it.name.equals(species, true) }
        final = critter?.name ?: species
        names = critter?.let { c -> Critters.all.filter { it.biome == c.biome }.map { it.name } } ?: listOf(final)
        rarityColor = rarityOf(final)
        rarity = critter?.rarity
        bolt = null
        epicBurst = false
        // A saved model wins: it shows even when the critter is far away, and it holds still
        val saved = try { CritterModels.pieces(final) } catch (t: Throwable) {
            lastError = "loading the saved model: ${t.javaClass.simpleName} ${t.message ?: ""}".trim()
            LOG.warn("Sparkling cinematic: saved model failed", t)
            emptyList()
        }
        val fresh = if (saved.isEmpty() && live.isEmpty()) CritterGallery.freshMob(final) else emptyList()
        living = saved.ifEmpty { live }.ifEmpty { fresh }
        source = when {
            saved.isNotEmpty() -> "saved model (${saved.size} piece${if (saved.size == 1) "" else "s"})"
            live.isNotEmpty() -> "live critter nearby (${live.size} piece${if (live.size == 1) "" else "s"})"
            fresh.isNotEmpty() -> "the game's own mob model"
            else -> "icon (no saved model, none nearby)"
        }
        rollModels = names.associateWith { n ->
            if (n.equals(final, true)) living
            else Critters.all.firstOrNull { it.name == n }?.let { c -> runCatching { CritterGallery.model(c).pieces }.getOrNull() } ?: emptyList()
        }
        slotTimes.clear()
        lastTick = -1
        popped = false
        var t = SLOT_START
        while (t < LAND) {
            slotTimes.add(t)
            val p = (t - SLOT_START).toFloat() / (LAND - SLOT_START)
            t += (45 + p * p * 260).toLong()
        }
        particles.clear()
        landed = false
        startedAt = now()
        lastFrame = startedAt
    }

    /** The name shown by the slot machine at [t]; never the final one until it lands. */
    private fun slotName(t: Long): String {
        if (names.size <= 1) return final
        val others = names.filter { it != final }
        val i = slotTimes.indexOfLast { it <= t }.coerceAtLeast(0)
        return others[(i + final.length) % others.size]
    }

    private fun burst(cx: Float, cy: Float, n: Int, color: Int) {
        repeat(n) {
            val a = Math.random() * 2 * PI
            val v = 1.2f + Math.random().toFloat() * 3.2f
            particles.add(P(cx, cy, (cos(a) * v).toFloat(), (sin(a) * v * .7).toFloat(), 1f,
                .010f + Math.random().toFloat() * .010f, 1f + Math.random().toFloat() * 1.5f, color, Math.random() < .35, .03f))
        }
    }

    private fun argb(rgb: Int, alpha: Float) = ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (rgb and 0xFFFFFF)
    private fun easeOut(x: Float) = 1 - (1 - x.coerceIn(0f, 1f)).pow(3)

    private fun render(g: GuiGraphicsExtractor) {
        if (!active) return
        try {
            draw(g)
        } catch (t: Throwable) {
            if (lastError == null) LOG.warn("Sparkling cinematic: drawing failed", t)
            lastError = "drawing: ${t.javaClass.simpleName} ${t.message ?: ""}".trim()
            startedAt = 0L
        }
    }

    private fun draw(g: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        if (client.gui.hud.isHidden) return
        val font = client.font
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        val t = now() - startedAt
        val frames = ((now() - lastFrame) / 16f).coerceIn(0f, 4f)
        lastFrame = now()

        // A tick with every name the roll lands on, climbing in pitch and getting louder as it slows down
        if (t in SLOT_START until LAND) {
            val i = slotTimes.indexOfLast { it <= t }
            if (i >= 0 && i != lastTick) {
                lastTick = i
                val k = if (slotTimes.size > 1) i / (slotTimes.size - 1f) else 1f
                client.player?.let { p ->
                    p.playSound(SoundEvents.NOTE_BLOCK_HAT.value(), .6f + .3f * k, 1f + .4f * k)
                    p.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .35f + .45f * k, .6f + 1.2f * k)
                }
            }
        }
        if (!landed && t >= LAND) {
            landed = true
            burst(w / 2f, h * MODEL_Y, 70, GOLD)
            burst(w / 2f, h * MODEL_Y, 25, rarityColor)
            client.player?.playSound(SoundEvents.PLAYER_LEVELUP, 1f, 2f)
        }
        val fade = if (t < FADE) 1f else 1f - (t - FADE).toFloat() / (END - FADE)
        // A shake right after the landing, a big one for a Legendary
        val legendary = rarity == safaripatron.data.Rarity.LEGENDARY
        val shakeFor = if (legendary) 600 else 350
        val shake = if (t in LAND..(LAND + shakeFor)) (1 - (t - LAND) / shakeFor.toFloat()) * (if (legendary) 7f else 2.5f) else 0f
        val sx = ((Math.random() - .5) * shake).toFloat()
        val sy = ((Math.random() - .5) * shake).toFloat()

        // Dim + letterbox bars
        g.fill(0, 0, w, h, argb(0x000000, .35f * min(1f, t / 300f) * fade))
        val bar = (h * BAR * easeOut(t / 450f) * fade).toInt()
        g.fill(0, 0, w, bar, 0xFF000000.toInt())
        g.fill(0, h - bar, w, h, 0xFF000000.toInt())
        rarityBehind(g, t, w, h, bar, fade)
        sparkles(g, t, w, h, bar, frames, fade)

        g.pose().pushMatrix()
        g.pose().translate(sx, sy)

        // Title, letter by letter, in its own font (Tiny5; the stars come from the game's font)
        // Right under the top bar
        val titleY = (h * BAR).toInt() + 6
        val letters = TITLE.map { net.minecraft.network.chat.Component.literal(it.toString()).withStyle { st -> st.withFont(TITLE_FONT) } }
        g.pose().pushMatrix()
        g.pose().translate(w / 2f, titleY.toFloat())
        g.pose().scale(TITLE_SCALE, TITLE_SCALE)
        var x = -letters.sumOf { font.width(it) } / 2f
        for ((i, c) in letters.withIndex()) {
            val k = ((t - 150 - i * 40) / 250f).coerceIn(0f, 1f)
            if (k > 0f) {
                // A light sweep runs over the title once
                val lit = t in (1150L + i * 35)..(1310L + i * 35)
                g.text(font, c, x.toInt(), (-8 * (1 - easeOut(k))).toInt(), argb(if (lit) 0xFFFFFF else GOLD, k * fade), true)
            }
            x += font.width(c)
        }
        g.pose().popMatrix()

        // While the names roll, each name shows its own model; every switch gives a little bounce
        if (t in SLOT_START until LAND) {
            val n = slotName(t)
            val since = t - (slotTimes.lastOrNull { it <= t } ?: SLOT_START)
            val bounce = 1f + .15f * (1f - (since / 120f).coerceIn(0f, 1f))
            val appear = easeOut(((t - SLOT_START) / 250f))
            rollModels[n]?.let { drawPieces(g, it, w / 2, (h * MODEL_Y).toInt(), MODEL_SIZE * .85f * bounce * appear, 0f, 0, CritterTweaks.get(n), n) }
        }
        // The real critter, drawn by the game itself
        var drawnLive = false
        if (t >= LAND) {
            val k = ((t - LAND) / 700f).coerceIn(0f, 1f)
            // No shrinking away at the end (a tiny model breaks into loose bits): it swells a little, then pops
            val out = ((t - FADE) / POP_OUT.toFloat()).coerceIn(0f, 1f)
            val pop = easeOut(k) * (1 + .25f * sin(k * PI.toFloat())) * (1 + .12f * out)
            if (out >= 1f && !popped) {
                popped = true
                burst(w / 2f, h * MODEL_Y, 45, GOLD)
                burst(w / 2f, h * MODEL_Y, 20, rarityColor)
            }
            // Spins in, comes to rest facing you (its front comes from the built-in turn fixes), then sways a little
            val settle = ((t - LAND) / 1400f).coerceIn(0f, 1f)
            val spin = (1 - easeOut(settle)) * 720f + sin((t - LAND) / 900f) * 15f * easeOut(((t - LAND - 900) / 800f).coerceIn(0f, 1f))
            if (!popped) drawnLive = drawPieces(g, living, w / 2, (h * MODEL_Y).toInt(), MODEL_SIZE * pop, spin, 0, CritterTweaks.get(final), final)
        }
        // No model at all: the critter's icon instead
        if (t >= LAND && !drawnLive && !popped) {
            val k = ((t - LAND) / 600f).coerceIn(0f, 1f)
            val pop = easeOut(k) * (1 + .25f * sin(k * PI.toFloat()))
            val spin = cos(((t - LAND) / 1000f) * 2.2f + (1 - easeOut(k)) * 4 * PI.toFloat())
            g.pose().pushMatrix()
            g.pose().translate(w / 2f, h * MODEL_Y)
            g.pose().scale(pop * 5.5f * spin, pop * 5.5f)
            safaripatron.hud.CritterIcons.drawCentered(g, final)
            g.pose().popMatrix()
        }

        // Slot machine, then the final name
        val nameY = h * .75f
        if (t >= SLOT_START) {
            g.pose().pushMatrix()
            g.pose().translate(w / 2f, nameY)
            g.pose().scale(2.5f, 2.5f)
            if (t < LAND) {
                val n = slotName(t)
                g.text(font, n, -font.width(n) / 2, 0, argb(rarityOf(n), fade), true)
            } else {
                var nx = -font.width(final) / 2f
                for ((i, ch) in final.withIndex()) {
                    val s = ch.toString()
                    val k = ((t - LAND - 120 - i * 40) / 250f).coerceIn(0f, 1f)
                    if (k > 0f) g.text(font, s, nx.toInt(), (-6 * (1 - easeOut(k))).toInt(), argb(rarityColor, k * fade), true)
                    nx += font.width(s)
                }
            }
            g.pose().popMatrix()
        }

        g.pose().popMatrix()
        orbit(g, t, w, h, fade, front = true)
        rarityFront(g, t, w, h, bar, fade)
    }

    /**
     * Stars circling the model. The half going round the back is drawn before the model so it hides them,
     * the half coming round the front after it.
     */
    private fun orbit(g: GuiGraphicsExtractor, t: Long, w: Int, h: Int, fade: Float, front: Boolean) {
        if (t !in LAND..FADE) return
        val o = (t - LAND) / 1000f * 1.8f
        for (i in 0 until 8) {
            val a = o + i * PI.toFloat() / 4
            val z = sin(a)
            if ((z > 0f) != front) continue
            star(g, w / 2f + cos(a) * MODEL_SIZE * .75f, h * MODEL_Y + z * 16, 1.2f, argb(0xFFE98A, (.35f + .5f * (z + 1) / 2) * fade))
        }
    }

    /** The back half of the circling stars and every spark, drawn before the model so it covers them. */
    private fun sparkles(g: GuiGraphicsExtractor, t: Long, w: Int, h: Int, bar: Int, frames: Float, fade: Float) {
        orbit(g, t, w, h, fade, front = false)
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            repeat(frames.toInt().coerceAtLeast(1)) {
                if (p.fly) {
                    // Fireflies drift about lazily
                    val sec = now() / 1000f
                    p.x += sin(sec * 1.7f + p.phase) * .6f
                    p.y += cos(sec * 1.3f + p.phase) * .4f
                } else {
                    p.x += p.vx; p.y += p.vy; p.vx *= p.drag; p.vy = p.vy * p.drag + p.gravity
                }
                p.life -= p.decay
                // Gold pieces bounce off the bottom bar with a glint
                if (p.floor > 0f && p.y > p.floor && p.vy > 0f) {
                    p.y = p.floor; p.vy *= -.45f
                    if (p.vy < -1f) bounceGlints.add(floatArrayOf(p.x, p.y))
                }
            }
            if (p.life <= 0f) { iter.remove(); continue }
            if (!p.front) drawParticle(g, p, fade)
        }
        for ((x, y) in bounceGlints) particles.add(P(x, y, 0f, 0f, 1f, .06f, 1.5f, 0xFFE98A, true, 0f))
        bounceGlints.clear()
    }

    private val bounceGlints = ArrayList<FloatArray>()

    private fun drawParticle(g: GuiGraphicsExtractor, p: P, fade: Float) {
        var alpha = min(1f, p.life)
        // Fireflies blink; crystal sparks flare up and die down
        if (p.fly) alpha *= .5f + .5f * sin(now() / 1000f * 6f + p.phase * 3f)
        if (p.twinkle) alpha *= sin(p.life * PI.toFloat())
        val c = argb(p.color, alpha * fade)
        when {
            // A spinning coin: narrows and widens as it turns
            p.coin -> {
                val turn = kotlin.math.abs(cos(p.y / 6f + p.x))
                val half = max(.5f, p.size * turn / 2)
                g.fill((p.x - half).toInt(), (p.y - p.size / 2).toInt(), (p.x + half).toInt() + 1, (p.y + p.size / 2).toInt(), c)
            }
            p.star -> star(g, p.x, p.y, p.size, c)
            else -> g.fill(p.x.toInt(), p.y.toInt(), (p.x + p.size).toInt() + 1, (p.y + p.size).toInt() + 1, c)
        }
    }

    // ----- One extra effect per rarity, on top of the landing burst -----

    /** A lightning bolt on screen (Epic): its points and when it goes out. */
    private var bolt: Pair<FloatArray, Long>? = null
    /** Epic's swirl runs this long after the landing, then bursts out. */
    private const val EPIC_PULL = 900L
    private var epicBurst = false

    /** Effects behind the model: Rare's light beams, the flash and Epic's pull. */
    private fun rarityBehind(g: GuiGraphicsExtractor, t: Long, w: Int, h: Int, bar: Int, fade: Float) {
        val r = rarity ?: return
        if (t < SLOT_START) return
        val cx = w / 2f
        val cy = h * MODEL_Y
        val since = t - LAND
        val col = rarityColor
        // A flash of the rarity's color on the landing
        if ((r == safaripatron.data.Rarity.RARE || r == safaripatron.data.Rarity.LEGENDARY) && since in 0L..250L)
            g.fill(0, bar, w, h - bar, argb(col, .35f * (1 - since / 250f) * fade))
        if (r == safaripatron.data.Rarity.EPIC && since >= EPIC_PULL && !epicBurst) {
            epicBurst = true
            burst(cx, cy, 60, col)
            burst(cx, cy, 25, 0xE8A0FF)
        }
        // Rare: blue beams turning slowly behind the model
        if (r == safaripatron.data.Rarity.RARE && since >= 0) {
            val show = min(1f, since / 500f) * fade
            val turn = since / 1000f * .9f
            val reach = max(w, h) * .55f
            for (i in 0 until 8) {
                val a = turn + i * PI.toFloat() / 4
                var d = MODEL_SIZE * .35f
                while (d < reach) {
                    val x = cx + cos(a) * d
                    val y = cy + sin(a) * d
                    if (y > bar && y < h - bar) {
                        val s = (2 + d / 60).toInt()
                        g.fill((x - s / 2).toInt(), (y - s / 2).toInt(), (x + s / 2).toInt() + 1, (y + s / 2).toInt() + 1,
                            argb(col, .16f * show * (1 - d / reach)))
                    }
                    d += 3f
                }
            }
        }
        // Epic: once the model has landed (no spoiler while the names roll), sparks swirl into it, then burst out
        if (r == safaripatron.data.Rarity.EPIC && since in 0L until EPIC_PULL) {
            repeat(3) {
                val a = Math.random() * 2 * PI
                val d = 140 + Math.random() * 80
                val x = (cx + cos(a) * d).toFloat()
                val y = (cy + sin(a) * d * .6).toFloat()
                // Toward the middle, and a bit sideways so they swirl
                val speed = 3.2f
                val vx = (-cos(a) * speed - sin(a) * speed * .8).toFloat()
                val vy = ((-sin(a) * speed + cos(a) * speed * .8) * .6).toFloat()
                particles.add(P(x, y, vx, vy, 1f, .024f, 1.5f, if (Math.random() < .7) col else 0xE8A0FF, Math.random() < .3, 0f, .975f))
            }
        }
    }

    /** Effects in front of the model: Uncommon's fireflies, Rare's crystal sparks, Epic's lightning and glow (Legendary's gold is spawned here but drawn behind). */
    private fun rarityFront(g: GuiGraphicsExtractor, t: Long, w: Int, h: Int, bar: Int, fade: Float) {
        val r = rarity ?: return
        val since = t - LAND
        if (since < 0) return
        val cx = w / 2f
        val cy = h * MODEL_Y
        val col = rarityColor
        when (r) {
            // Common: nothing extra
            safaripatron.data.Rarity.COMMON -> {}
            // Uncommon: green fireflies drift and blink around the model
            safaripatron.data.Rarity.UNCOMMON -> if (since < 3000 && t < FADE && Math.random() < .15) {
                particles.add(P(cx + (Math.random().toFloat() - .5f) * MODEL_SIZE * 2.4f, cy + (Math.random().toFloat() - .5f) * MODEL_SIZE * 1.3f,
                    0f, 0f, 1f, .008f, 2f, if (Math.random() < .7) col else 0xB6FFB6, false, 0f, 1f,
                    front = Math.random() < .5, fly = true, phase = (Math.random() * 6).toFloat()))
            }
            // Rare: besides the flash and the beams, blue crystal sparks flare up around the model
            safaripatron.data.Rarity.RARE -> if (since < 3000 && t < FADE && Math.random() < .2) {
                val a = Math.random() * 2 * PI
                val d = MODEL_SIZE * (.55f + Math.random().toFloat() * .55f)
                particles.add(P((cx + cos(a) * d).toFloat(), (cy + sin(a) * d * .6f).toFloat(), 0f, 0f, 1f, .03f, 2f,
                    if (Math.random() < .6) 0xB0C0FF else 0xFFFFFF, true, 0f, 1f, front = Math.random() < .5, twinkle = true))
            }
            // Epic: after the swirl bursts out, lightning and the screen edges glowing
            safaripatron.data.Rarity.EPIC -> if (since >= EPIC_PULL) {
                val since = since - EPIC_PULL
                // Edges of the picture glow, breathing
                val glow = (.22f + .08f * sin(since / 160f)) * fade
                for (i in 0 until 8) {
                    val c = argb(col, glow * (1 - i / 8f))
                    g.fill(i * 2, bar, i * 2 + 2, h - bar, c)
                    g.fill(w - i * 2 - 2, bar, w - i * 2, h - bar, c)
                    g.fill(0, bar + i * 2, w, bar + i * 2 + 2, c)
                    g.fill(0, h - bar - i * 2 - 2, w, h - bar - i * 2, c)
                }
                // Now and then a bolt strikes from the top bar toward the model
                if (since < 2000 && bolt == null && Math.random() < .03) {
                    val pts = ArrayList<Float>()
                    var x = cx + (Math.random().toFloat() - .5f) * MODEL_SIZE * 2.5f
                    var y = bar.toFloat()
                    pts.add(x); pts.add(y)
                    while (y < cy - MODEL_SIZE * .4f) {
                        x += (Math.random().toFloat() - .5f) * 22f
                        y += 12f
                        pts.add(x); pts.add(y)
                    }
                    bolt = pts.toFloatArray() to now() + 110
                }
                bolt?.let { (pts, until) ->
                    if (now() > until) bolt = null
                    else for (i in 0 until pts.size / 2 - 1) line(g, pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3], argb(0xF0C0FF, .95f * fade))
                }
            }
            // Legendary, all behind the model: gold bursting out of the model like a fountain, then raining down and bouncing off the bottom bar
            safaripatron.data.Rarity.LEGENDARY -> {
                if (since in 50L until 900L && t < FADE) repeat(2) {
                    if (Math.random() < .8) particles.add(P(cx + (Math.random().toFloat() - .5f) * 20f, cy - MODEL_SIZE * .15f,
                        (Math.random().toFloat() - .5f) * 3f, -(4f + Math.random().toFloat() * 3f), 1.4f, .004f, 4f,
                        if (Math.random() < .6) col else 0xFFE98A, false, .12f, 1f,
                        floor = (h - bar - 4).toFloat(), coin = true))
                }
                if (since in 200L until 3000L && t < FADE) repeat(if (Math.random() < .45) 1 else 0) {
                    particles.add(P(Math.random().toFloat() * w, bar.toFloat(), (Math.random().toFloat() - .5f) * .4f,
                        1.5f + Math.random().toFloat() * 1.5f, 1.4f, .005f, 4f, if (Math.random() < .6) col else 0xFFE98A,
                        false, .08f, 1f, floor = (h - bar - 4).toFloat(), coin = true))
                }
            }
        }
        // Sparks that belong in front of the model
        for (p in particles) if (p.front) drawParticle(g, p, fade)
    }

    /** A line of little squares from (x0, y0) to (x1, y1). */
    private fun line(g: GuiGraphicsExtractor, x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        val n = max(1, (kotlin.math.hypot(x1 - x0, y1 - y0) / 1.5f).toInt())
        for (i in 0..n) {
            val x = (x0 + (x1 - x0) * i / n).toInt()
            val y = (y0 + (y1 - y0) * i / n).toInt()
            g.fill(x, y, x + 2, y + 2, color)
        }
    }

    private fun star(g: GuiGraphicsExtractor, x: Float, y: Float, s: Float, c: Int) {
        val a = max(1, s.toInt())
        g.fill((x - a / 2f).toInt(), (y - a * 2).toInt(), (x + a / 2f).toInt() + 1, (y + a * 2).toInt(), c)
        g.fill((x - a * 2).toInt(), (y - a / 2f).toInt(), (x + a * 2).toInt(), (y + a / 2f).toInt() + 1, c)
    }
}
