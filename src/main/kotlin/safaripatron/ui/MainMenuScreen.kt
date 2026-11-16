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
import safaripatron.achievements.Achievements
import safaripatron.history.History
import safaripatron.safari.Run

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
class MainMenuScreen(private val parent: Screen? = null) : Screen(Component.literal("Safari Patron")) {
    private val hits = ArrayList<Ui.Hit>()
    private val version = FabricLoader.getInstance().getModContainer(SafariPatron.MOD_ID)
        .map { it.metadata.version.friendlyString }.orElse("")

    /** Longer text for the main menu cards (the settings list keeps the short summaries). */
    private val cardText = mapOf(
        "sparkling" to "Finder with waypoints, alert and your Sparkling collection",
        "esp" to "Highlights critters, floor drops and critter hiding spots",
        "party" to "Party chat alerts, hotspot, biome and PB announcements",
        "capsule" to "Tools for capsules, skips and timers",
        "contest" to "Timer, ticket progress, honeyhives and alerts",
        "profit" to "Session and all-time profit from every drop",
    )

    private class Card(
        val icon: String, val iconColor: Int, val title: String, val desc: String,
        val stat: String? = null, val statColor: Int = Ui.GREEN,
        val right: ((GuiGraphicsExtractor, Int, Int) -> Unit)? = null,
        val action: () -> Unit,
    )

    private fun historyCards(): List<Card> {
        val t = History.totals
        val today = java.time.LocalDate.now().toEpochDay().toString()
        val todayRuns = t.days[today]?.runs ?: 0
        val unlocked = Achievements.all.count { Achievements.isUnlocked(it.id) }
        return listOf(
            Card("☰", Ui.GREEN, "Run History", "Every run with its time, loot and party stats",
                if (t.runs > 0) "${"%,d".format(java.util.Locale.ROOT, t.runs)} runs · $todayRuns today" else "No runs yet", Ui.GREEN,
                action = { MenuNav.open(HistoryScreen(this, "history")) }),
            Card("▲", 0xFF55FFFF.toInt(), "Stats", "Graphs, records, clear stats and party history stats",
                if (t.fastestMs > 0) "Fastest ${Run.time(t.fastestMs)} · avg ${Run.time(t.averageMs.toLong())}" else "No timed runs yet",
                0xFF55FFFF.toInt(), action = { MenuNav.open(HistoryScreen(this, "stats")) }),
            Card("★", 0xFFFFAA00.toInt(), "Achievements", "${Achievements.all.size} achievements across ${safaripatron.achievements.Tier.entries.size} rarities",
                "$unlocked/${Achievements.all.size} · ${Achievements.points} points", 0xFFFFAA00.toInt(),
                action = { MenuNav.open(HistoryScreen(this, "achievements")) }),
        )
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        super.extractRenderState(g, mx, my, delta)
        hits.clear()

        val w = minOf(width - 16, 560)
        val gap = 6
        val ix0 = (width - w) / 2 + 8
        val iw = w - 16
        val cw = (iw - gap * 2) / 3
        val headerH = 52
        val bigH = 40

        // Feature cards (settings categories) and the three history cards
        val feature = SettingsModel.cardIds.map { id ->
            val c = SettingsModel.byId(id)
            val single = c.items().singleOrNull() as? SettingsModel.Toggle
            Card(c.icon, c.iconColor, c.title, cardText[id] ?: c.summary,
                right = { gg, rx, ry ->
                    if (single != null) Ui.switch(gg, rx - 21, ry + 1, single.get())
                    else c.enabled?.let { en ->
                        val on = en()
                        val label = if (on) "On" else "Off"
                        Ui.pill(gg, font, label, rx - font.width(label) - 8, ry, if (on) Ui.PILL_ON else Ui.PILL_OFF, if (on) Ui.PILL_ON_TEXT else Ui.PILL_OFF_TEXT)
                    }
                },
                action = { if (single != null) single.set(!single.get()) else MenuNav.open(SettingsScreen(id, this)) })
        }
        val rows = feature.chunked(3) + listOf(historyCards())

        // Two lines of description, or one if the screen is short
        fun rowH(row: List<Card>, lines: Int) = 26 + row.maxOf { minOf(lines, Ui.wrap(font, it.desc, cw - 12, lines).size) } * 10 +
            (if (row.any { it.stat != null }) 11 else 0) + 5
        fun total(lines: Int) = 8 + headerH + gap + bigH + gap + rows.sumOf { rowH(it, lines) + gap } + 14 + 6
        val lines = if (total(2) <= height - 8) 2 else 1
        val h = total(lines)
        val x0 = (width - w) / 2
        val y0 = ((height - h) / 2).coerceAtLeast(4)
        Ui.rounded(g, x0, y0, w, h, Ui.WINDOW)
        val ix = ix0
        var y = y0 + 8

        // Header
        Ui.box(g, ix, y, iw, headerH, Ui.HEADER, Ui.HEADER_BORDER)
        Ui.logo(g, ix + 7, y + 6, 40)
        Ui.text(g, font, "Safari Patron", ix + 55, y + 9, Ui.WHITE, 2f, true)
        val titleW = font.width("Safari Patron") * 2
        if (version.isNotEmpty()) Ui.pill(g, font, "v$version", ix + 62 + titleW, y + 12, Ui.PILL_GOLD, Ui.PILL_GOLD_TEXT)
        Ui.text(g, font, "QoL Critter Safari mod for Hypixel Skyblock", ix + 55, y + 33, Ui.MUTED)
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
        Ui.text(g, font, "⚙", ix + 10, y + 12, Ui.GREEN, 2f)
        Ui.text(g, font, "Settings", ix + 36, y + 9, Ui.WHITE, 1f, true)
        Ui.text(g, font, Ui.fit(font, "Every option of every feature, in one place", bigW - 60), ix + 36, y + 22, Ui.MUTED)
        g.text(font, "→", ix + bigW - 14, y + 16, Ui.GREEN, false)
        hits.add(Ui.Hit(ix, y, bigW, bigH) { MenuNav.open(SettingsScreen(null, this)) })

        val px = ix + bigW + gap
        val hoverPanels = Ui.inside(mx, my, px, y, smallW, bigH)
        Ui.box(g, px, y, smallW, bigH, if (hoverPanels) Ui.CARD_HOVER else Ui.CARD, if (hoverPanels) Ui.BORDER_HOVER else Ui.BORDER)
        Ui.text(g, font, Ui.fit(font, "✥ Move panels", smallW - 14), px + 7, y + 5, Ui.GOLD, 1f, true)
        Ui.text(g, font, Ui.fit(font, "Drag and resize every panel", smallW - 14), px + 7, y + 17, Ui.MUTED)
        Ui.text(g, font, "/sp hud", px + 7, y + 28, Ui.FAINT)
        hits.add(Ui.Hit(px, y, smallW, bigH) { MenuNav.open(HudEditScreen(this)) })
        y += bigH + gap

        // Cards: 3 per row; every card of a row has the row's height
        for (row in rows) {
            val rh = rowH(row, lines)
            row.forEachIndexed { i, c ->
                val cx = ix + i * (cw + gap)
                val hover = Ui.inside(mx, my, cx, y, cw, rh)
                Ui.box(g, cx, y, cw, rh, if (hover) Ui.CARD_HOVER else Ui.CARD, if (hover) Ui.BORDER_HOVER else Ui.BORDER)
                Ui.text(g, font, c.icon, cx + 6, y + 5, c.iconColor, 1f, true)
                if (c.right != null) c.right.invoke(g, cx + cw - 5, y + 4)
                else g.text(font, "›", cx + cw - 10, y + 5, if (hover) Ui.WHITE else Ui.GREEN, false)
                Ui.text(g, font, Ui.fit(font, c.title, cw - 12), cx + 6, y + 16, Ui.WHITE, 1f, true)
                var ly = y + 28
                for (line in Ui.wrap(font, c.desc, cw - 12, lines)) { g.text(font, line, cx + 6, ly, Ui.MUTED, false); ly += 10 }
                c.stat?.let { Ui.text(g, font, Ui.fit(font, it, cw - 12), cx + 6, y + rh - 14, c.statColor, 1f, true) }
                hits.add(Ui.Hit(cx, y, cw, rh, c.action))
            }
            y += rh + gap
        }

        val credit = "Made by PatronOG"
        g.text(font, credit, x0 + (w - font.width(credit)) / 2, y + 2, Ui.FAINT, false)

        // Update checker switch, bottom right
        val on = safaripatron.config.Config.s.updateChecker
        val label = "Update checker"
        val sx = ix + iw - 20
        val lx = sx - 5 - font.width(label)
        val hoverUpd = Ui.inside(mx, my, lx - 2, y, ix + iw - lx + 2, 12)
        g.text(font, label, lx, y + 2, if (hoverUpd) Ui.WHITE else Ui.FAINT, false)
        Ui.switch(g, sx, y + 1, on)
        hits.add(Ui.Hit(lx - 2, y, ix + iw - lx + 2, 12) {
            safaripatron.config.Config.s.updateChecker = !on
            safaripatron.config.Config.save()
        })
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            hits.lastOrNull { it.contains(event.x(), event.y()) }?.let { it.action(); return true }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun isPauseScreen() = false

    override fun onClose() {
        // Opened from Mod Menu: go back to the mod list
        if (parent != null) minecraft.gui.setScreen(parent) else super.onClose()
    }
}
