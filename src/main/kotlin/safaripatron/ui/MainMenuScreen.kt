package safaripatron.ui

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import safaripatron.SafariPatron
import safaripatron.hud.HudEditScreen

/** Opens menu screens from commands (after the chat screen has closed). */
object MenuNav {
    fun open(screen: Screen) {
        val client = Minecraft.getInstance()
        client.execute { client.setScreenAndShow(screen) }
    }
    fun openMain() = open(MainMenuScreen())
    fun openSettings(category: String? = null) = open(SettingsScreen(category, MainMenuScreen()))
    fun openHud() {
        val client = Minecraft.getInstance()
        val parent = client.gui.screen()
        client.execute { client.setScreenAndShow(HudEditScreen(parent)) }
    }
}

/** /sp — main menu. */
class MainMenuScreen : Screen(Component.literal("Safari Patron")) {
    private val hits = ArrayList<Ui.Hit>()
    private val version = FabricLoader.getInstance().getModContainer(SafariPatron.MOD_ID)
        .map { it.metadata.version.friendlyString }.orElse("")

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        super.extractRenderState(g, mx, my, delta)
        hits.clear()

        val w = minOf(width - 20, 380)
        val headerH = 48
        val bigH = 36
        val cardH = 40
        val gap = 6
        val h = 10 + headerH + gap + bigH + gap + cardH * 2 + gap + 18 + 6
        val x0 = (width - w) / 2
        val y0 = ((height - h) / 2).coerceAtLeast(4)
        Ui.rounded(g, x0, y0, w, h, Ui.WINDOW)

        val ix = x0 + 8
        val iw = w - 16
        var y = y0 + 8

        // Header
        Ui.box(g, ix, y, iw, headerH, Ui.HEADER, Ui.HEADER_BORDER)
        Ui.logo(g, ix + 7, y + 6, 36)
        Ui.text(g, font, "Safari Patron", ix + 50, y + 9, Ui.WHITE, 1.6f, true)
        val titleW = (font.width("Safari Patron") * 1.6f).toInt()
        if (version.isNotEmpty()) Ui.pill(g, font, "v$version", ix + 56 + titleW, y + 10, Ui.PILL_GOLD, Ui.PILL_GOLD_TEXT)
        Ui.text(g, font, "QoL Critter Safari mod for Hypixel Skyblock", ix + 50, y + 30, Ui.MUTED)
        val closeX = ix + iw - 12
        val closeHover = Ui.inside(mx, my, closeX - 3, y + 3, 12, 12)
        g.text(font, "✕", closeX, y + 5, if (closeHover) Ui.WHITE else Ui.FAINT, false)
        hits.add(Ui.Hit(closeX - 3, y + 3, 12, 12) { onClose() })
        y += headerH + gap

        // Settings (big) + Move panels
        val bigW = (iw - gap) * 2 / 3
        val smallW = iw - gap - bigW
        val hoverSettings = Ui.inside(mx, my, ix, y, bigW, bigH)
        Ui.box(g, ix, y, bigW, bigH, if (hoverSettings) 0xFF26401C.toInt() else Ui.FEATURE, Ui.FEATURE_BORDER)
        Ui.text(g, font, "⚙", ix + 10, y + 11, Ui.GREEN, 1.4f)
        Ui.text(g, font, "Settings", ix + 30, y + 8, Ui.WHITE, 1.2f)
        Ui.text(g, font, "Every option in one place", ix + 30, y + 22, Ui.MUTED)
        g.text(font, "→", ix + bigW - 14, y + 14, Ui.GREEN, false)
        hits.add(Ui.Hit(ix, y, bigW, bigH) { MenuNav.open(SettingsScreen(null, this)) })

        val px = ix + bigW + gap
        val hoverPanels = Ui.inside(mx, my, px, y, smallW, bigH)
        Ui.box(g, px, y, smallW, bigH, if (hoverPanels) Ui.CARD_HOVER else Ui.CARD, if (hoverPanels) Ui.BORDER_HOVER else Ui.BORDER)
        Ui.text(g, font, "✥", px + 8, y + 7, Ui.GOLD)
        Ui.text(g, font, Ui.fit(font, "Move panels", smallW - 30), px + 20, y + 7, Ui.GOLD)
        Ui.text(g, font, "/sp hud", px + 8, y + 22, Ui.FAINT)
        hits.add(Ui.Hit(px, y, smallW, bigH) { MenuNav.open(HudEditScreen(this)) })
        y += bigH + gap

        // Category cards: 3 x 2
        val cw = (iw - gap * 2) / 3
        SettingsModel.cardIds.forEachIndexed { i, id ->
            val c = SettingsModel.byId(id)
            val cx = ix + (i % 3) * (cw + gap)
            val cy = y + (i / 3) * (cardH + gap)
            val hover = Ui.inside(mx, my, cx, cy, cw, cardH)
            Ui.box(g, cx, cy, cw, cardH, if (hover) Ui.CARD_HOVER else Ui.CARD, if (hover) Ui.BORDER_HOVER else Ui.BORDER)
            Ui.text(g, font, c.icon, cx + 7, cy + 6, c.iconColor, 1.3f)
            c.enabled?.let { en ->
                val on = en()
                val label = if (on) "On" else "Off"
                val pw = font.width(label) + 8
                Ui.pill(g, font, label, cx + cw - pw - 5, cy + 5, if (on) Ui.PILL_ON else Ui.PILL_OFF, if (on) Ui.PILL_ON_TEXT else Ui.PILL_OFF_TEXT)
            }
            Ui.text(g, font, Ui.fit(font, c.title, cw - 14), cx + 7, cy + 19, Ui.WHITE)
            Ui.text(g, font, Ui.fit(font, c.summary, cw - 14), cx + 7, cy + 29, Ui.MUTED)
            hits.add(Ui.Hit(cx, cy, cw, cardH) { MenuNav.open(SettingsScreen(id, this)) })
        }
        y += cardH * 2 + gap * 2

        val credit = "Made by PatronOG"
        g.text(font, credit, x0 + (w - font.width(credit)) / 2, y + 2, Ui.FAINT, false)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            hits.lastOrNull { it.contains(event.x(), event.y()) }?.let { it.action(); return true }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun isPauseScreen() = false
}
