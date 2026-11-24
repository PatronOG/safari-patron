package safaripatron.hud

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import safaripatron.SafariPatron
import safaripatron.config.Config
import safaripatron.config.PanelPos
import safaripatron.config.Settings
import safaripatron.data.Biome
import safaripatron.safari.Run
import safaripatron.safari.Safari
import safaripatron.sparkling.SparklingFinder

/** Short-lived on-screen notifications. Each kind has its own panel. */
object Toasts {
    enum class Kind { WARP, ANNOUNCE }
    class Toast(val text: String, val color: Int, val until: Long, val start: Long, val kind: Kind)
    val list = ArrayList<Toast>()

    fun show(kind: Kind, text: String, color: Int, ms: Long) {
        val now = System.currentTimeMillis()
        list.add(Toast(text, color, now + ms, now, kind))
    }

    fun active(kind: Kind): List<Toast> {
        val now = System.currentTimeMillis()
        list.removeIf { it.until <= now }
        return list.filter { it.kind == kind }
    }
}

/**
 * A movable, scalable HUD element. draw() always draws at (0,0) in unscaled units;
 * the caller applies position and scale. preview = draw sample content for the HUD editor.
 */
interface HudElement {
    val name: String
    val pos: PanelPos
    val defaultPos: PanelPos
    /** Draws (or only measures when g == null) and returns the unscaled size. */
    fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size
    /** Whether there is something to show right now (outside the editor). */
    fun visible(): Boolean
    /** true = pos.x is the panel's center, so it stays centered when its width changes. */
    val centered: Boolean get() = false
    /** true = drawn on top of inventories/chests too, instead of behind their dimmed background. */
    val overScreens: Boolean get() = false
}

object RunElement : HudElement {
    override val name = "Run panel"
    override val pos get() = Config.s.runPanel
    override val defaultPos get() = Settings.DEFAULT_RUN
    override fun visible() = Safari.inSafari && Run.running
    override fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size {
        val self = Minecraft.getInstance().player?.name?.string
        val data = if (preview && !visible()) Panels.demoRun(self) else Panels.runData(self)
        return Panels.drawRun(g, font, 0, 0, data)
    }
}

object BiomeElement : HudElement {
    override val name = "Biome panel"
    override val pos get() = Config.s.biomePanel
    override val defaultPos get() = Settings.DEFAULT_BIOME
    override fun visible() = Safari.inSafari && Run.running && Safari.biome != null
    override fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size {
        val biome = Safari.biome
        val data = if (preview && (!visible() || biome == null)) Panels.demoBiome() else Panels.biomeData(biome ?: Biome.ICY)
        return Panels.drawBiome(g, font, 0, 0, data)
    }
}

/** Stays on screen until the sparkling is caught. */
object SparklingElement : HudElement {
    override val name = "Sparkling panel"
    override val pos get() = Config.s.sparklingPanel
    override val defaultPos get() = Settings.DEFAULT_SPARKLING
    override fun visible() = Config.s.sparklingFinder && SparklingFinder.sparks.isNotEmpty()
    override val centered get() = true

    private const val TITLE_SCALE = 2.2f
    private const val NAME_SCALE = 1.6f

    override fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size {
        val names = if (preview && !visible()) listOf("Litterbug") else SparklingFinder.sparks.map { it.species }
        val foundAt = SparklingFinder.sparks.firstOrNull()?.foundAt ?: 0L
        val main = names.firstOrNull() ?: "Litterbug"
        val extra = names.drop(1).map { "✦ $it" }
        val title = "✦ SPARKLING ✦"

        var w = maxOf(font.width(title) * TITLE_SCALE, font.width(main) * NAME_SCALE).toInt()
        for (e in extra) w = maxOf(w, font.width(e))
        w += 40
        val h = (font.lineHeight * (TITLE_SCALE + NAME_SCALE) + 12 + extra.size * (font.lineHeight + 2)).toInt()
        if (g == null) return Panels.Size(w, h)

        val alphaF = if (preview) 1.0 else ((System.currentTimeMillis() - foundAt) / 250.0).coerceIn(0.0, 1.0)
        val alpha = (alphaF * 255).toInt().coerceAtLeast(5)
        fun c(rgb: Int) = (alpha shl 24) or (rgb and 0xFFFFFF)
        g.fill(0, 0, w, h, ((alphaF * 0x90).toInt() shl 24))
        g.fill(0, 0, w, 1, c(0xFFAA00))
        g.fill(0, h - 1, w, h, c(0xFFAA00))

        var y = 6f
        fun centered(text: String, scale: Float, color: Int) {
            g.pose().pushMatrix()
            g.pose().translate(w / 2f, y)
            g.pose().scale(scale, scale)
            g.text(font, text, -font.width(text) / 2, 0, color, true)
            g.pose().popMatrix()
            y += font.lineHeight * scale + 2
        }
        centered(title, TITLE_SCALE, c(0xFFAA00))
        centered(main, NAME_SCALE, c(0xFFFF55))
        for (e in extra) centered(e, 1f, c(0xFFD27F))
        return Panels.Size(w, h)
    }
}

/** Stacked notification boxes of one kind, centered on the panel's center. */
abstract class ToastElement(private val kind: Toasts.Kind, private val sample: String) : HudElement {
    override fun visible() = Toasts.active(kind).isNotEmpty()
    override val centered get() = true

    private val SCALE = 2f
    private val PAD_X = 16
    private val PAD_Y = 8
    private val GAP = 6

    override fun draw(g: GuiGraphicsExtractor?, font: Font, preview: Boolean): Panels.Size {
        val now = System.currentTimeMillis()
        val items = if (preview && !visible())
            listOf(Toasts.Toast(sample, 0xFF55FF55.toInt(), Long.MAX_VALUE, 0, kind))
        else Toasts.active(kind)
        if (items.isEmpty()) return Panels.Size(0, 0)
        val lineH = (font.lineHeight * SCALE).toInt()
        val boxH = lineH + PAD_Y * 2
        val w = items.maxOf { (font.width(it.text) * SCALE).toInt() + PAD_X * 2 }
        val h = items.size * boxH + (items.size - 1) * GAP
        if (g == null) return Panels.Size(w, h)

        var top = 0
        for (t in items) {
            val a = if (preview) 1.0 else minOf(1.0, (now - t.start) / 200.0, (t.until - now) / 500.0).coerceIn(0.0, 1.0)
            val alpha = (a * 255).toInt().coerceAtLeast(5)
            val color = (alpha shl 24) or (t.color and 0xFFFFFF)
            val boxW = (font.width(t.text) * SCALE).toInt() + PAD_X * 2
            val x0 = (w - boxW) / 2
            g.fill(x0, top, x0 + boxW, top + boxH, ((a * 0xA0).toInt() shl 24))
            g.fill(x0, top, x0 + boxW, top + 2, color)
            g.fill(x0, top + boxH - 2, x0 + boxW, top + boxH, color)
            g.pose().pushMatrix()
            g.pose().translate(w / 2f, (top + PAD_Y + 1).toFloat())
            g.pose().scale(SCALE, SCALE)
            g.text(font, t.text, -font.width(t.text) / 2, 0, color, true)
            g.pose().popMatrix()
            top += boxH + GAP
        }
        return Panels.Size(w, h)
    }
}

/** 4/4 Players in Safari / Someone missing! */
object WarpElement : ToastElement(Toasts.Kind.WARP, "4/4 Players in Safari") {
    override val name = "Warp check panel"
    override val pos get() = Config.s.warpPanel
    override val defaultPos get() = Settings.DEFAULT_WARP
}

/** Forest Done!, Everything Done!, Hotspot: Haunted ... */
object AnnounceElement : ToastElement(Toasts.Kind.ANNOUNCE, "Forest Done!") {
    override val name = "Biome announce panel"
    override val pos get() = Config.s.announcePanel
    override val defaultPos get() = Settings.DEFAULT_ANNOUNCE
}

object Hud {
    val elements: List<HudElement> = listOf(RunElement, BiomeElement, SparklingElement, WarpElement, AnnounceElement,
        CollectionElement, TotalElement, ContestElement, ProfitElement, TotalProfitElement,
        safaripatron.achievements.AchievementToasts)

    /**
     * Default spots as panel centers on a 960 x 540 GUI screen (what /sp hud shows while dragging).
     * Turned into positions with each panel's own size, so they land the same on any screen size.
     */
    private val DEFAULT_CENTERS: Map<HudElement, Pair<Int, Int>> = mapOf(
        RunElement to (106 to 66),
        BiomeElement to (49 to 186),
        CollectionElement to (297 to 60),
        TotalProfitElement to (211 to 164),
        SparklingElement to (479 to 87),
        AnnounceElement to (480 to 220),
        WarpElement to (480 to 220),
        ProfitElement to (746 to 71),
        ContestElement to (894 to 75),
        TotalElement to (794 to 475),
        safaripatron.achievements.AchievementToasts to (480 to 40),
    )

    /** An element's default position for the current screen size. */
    fun defaultPos(e: HudElement): PanelPos {
        val client = Minecraft.getInstance()
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val center = DEFAULT_CENTERS[e]
        if (center == null || width <= 0 || height <= 0) return e.defaultPos.copy()
        val size = e.draw(null, client.font, true)
        val cx = center.first / 960f * width
        val cy = center.second / 540f * height
        val x = if (e.centered) cx / width
            else ((cx - size.w / 2f) / width).coerceIn(0f, ((width - size.w).toFloat() / width).coerceAtLeast(0f))
        val y = ((cy - size.h / 2f) / height).coerceIn(0f, ((height - size.h).toFloat() / height).coerceAtLeast(0f))
        return PanelPos(x, y, 1f)
    }

    /** Every panel back to its default spot. */
    fun resetAll() {
        for (e in elements) e.pos.set(defaultPos(e))
        Config.save()
    }

    /** Fresh install: place the panels once the screen size is known. */
    fun ensureLayout() {
        if (Config.s.layoutPlaced) return
        val window = Minecraft.getInstance().window
        if (window.guiScaledWidth <= 0 || window.guiScaledHeight <= 0) return
        Config.s.layoutPlaced = true
        resetAll()
    }

    fun init() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath(SafariPatron.MOD_ID, "hud"),
        ) { g, _ -> render(g) }
    }

    /** Scaled on-screen rectangle (x, y, w, h) of an element of unscaled size [size]. */
    fun rect(pos: PanelPos, size: Panels.Size, width: Int, height: Int, centered: Boolean = false): IntArray {
        val w = (size.w * pos.scale).toInt()
        val h = (size.h * pos.scale).toInt()
        val maxX = (width - w).coerceAtLeast(0)
        val x = when {
            pos.x < 0 -> (width - w) / 2
            centered -> (pos.x * width - w / 2f).toInt().coerceIn(0, maxX)
            else -> (pos.x * width).toInt().coerceIn(0, maxX)
        }
        val y = (pos.y * height).toInt().coerceIn(0, (height - h).coerceAtLeast(0))
        return intArrayOf(x, y, w, h)
    }

    /** Draws an element at its saved position and scale; returns its on-screen rectangle. */
    fun drawAt(g: GuiGraphicsExtractor, font: Font, e: HudElement, preview: Boolean, width: Int, height: Int): IntArray {
        val size = e.draw(null, font, preview)
        val r = rect(e.pos, size, width, height, e.centered)
        if (size.w <= 0 || size.h <= 0) return r
        g.pose().pushMatrix()
        g.pose().translate(r[0].toFloat(), r[1].toFloat())
        g.pose().scale(e.pos.scale, e.pos.scale)
        e.draw(g, font, preview)
        g.pose().popMatrix()
        return r
    }

    private fun render(g: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        ensureLayout()
        if (client.gui.hud.isHidden) return
        if (client.gui.screen() is HudEditScreen) return
        // All our panels step aside while the sparkling cinematic plays
        if (safaripatron.cinematic.SparklingCinematic.active) return
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val container = client.gui.screen() is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>
        for (e in elements) if (e.visible() && !(container && e.overScreens)) drawAt(g, client.font, e, false, width, height)
        if (!container) safaripatron.capsule.Prefire.drawHud(g, client.font, width, height)
    }

    /** Called from an open inventory/chest (ContainerOverlayMixin): panels that stay sharp on top of it. */
    fun renderOverScreen(g: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        if (client.gui.hud.isHidden) return
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        overRects.clear()
        for (e in elements) if (e.overScreens && e.visible()) overRects[e] = drawAt(g, client.font, e, false, width, height)
    }

    /** Where the over-screen panels were drawn last frame (for mouse scrolling). */
    val overRects = HashMap<HudElement, IntArray>()

    private val SCROLL_FIRST = Identifier.fromNamespaceAndPath(SafariPatron.MOD_ID, "scroll_first")

    /**
     * Three independent ways the mouse wheel reaches the Total Profit Tracker in an inventory (mouse hook,
     * this Fabric screen event, the inventory's own mouseScrolled); whichever runs first scrolls it and uses
     * the wheel up, so the others (and REI underneath) don't see it. The screen event listener runs in a
     * phase before every other mod's listener.
     */
    fun initScroll() {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) {
                val ev = net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseScroll(screen)
                ev.addPhaseOrdering(SCROLL_FIRST, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE)
                ev.register(SCROLL_FIRST) { _, mx, my, _, vertical -> !scrollTotalPanelAt(mx, my, vertical) }
            }
        }
    }

    /** Mouse wheel from the low-level mouse hook (window pixels): true if it scrolled the panel. */
    fun scrollTotalPanel(vertical: Double): Boolean {
        val window = Minecraft.getInstance().window
        if (window.screenWidth <= 0 || window.screenHeight <= 0) return false
        val mouse = Minecraft.getInstance().mouseHandler
        val mx = mouse.xpos() * window.guiScaledWidth / window.screenWidth
        val my = mouse.ypos() * window.guiScaledHeight / window.screenHeight
        return scrollTotalPanelAt(mx, my, vertical)
    }

    /** Mouse wheel at GUI coordinates [mx], [my]: true if it was over the total profit panel and scrolled it. */
    fun scrollTotalPanelAt(mx: Double, my: Double, vertical: Double): Boolean {
        val client = Minecraft.getInstance()
        val e = TotalProfitElement
        val inContainer = client.gui.screen() is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>
        if (vertical == 0.0 || !inContainer || !e.visible() || client.gui.hud.isHidden) return false
        val window = client.window
        val r = rect(e.pos, e.draw(null, client.font, false), window.guiScaledWidth, window.guiScaledHeight, e.centered)
        val over = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]
        if (!over) return false
        e.scrollBy(if (vertical > 0) -1 else 1)
        return true
    }
}
