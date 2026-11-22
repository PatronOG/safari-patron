package safaripatron.safari

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket
import safaripatron.Brand
import safaripatron.config.Config
import safaripatron.data.Biome

/**
 * Accepts Hideyho's authenticated offer after 300 ms when enabled. A left click anywhere
 * in open chat can also pick the pending Sure action.
 */
object HideyhoClick {
    private const val VALID_MS = 30_000L
    private val sureWord = Regex("(?i)\\bSure\\b")

    private var pending: ClickEvent? = null
    private var offeredAt = 0L
    private var connection: Any? = null
    private var lastText = ""
    private var lastAt = 0L
    private val autoSure = HideyhoAutoSureState()
    private var lastLevel: Any? = null
    private var lastConnection: Any? = null
    private var runMarker = -1L

    private fun inHaunted() = Safari.inSafari && Run.running && Safari.biome == Biome.HAUNTED

    private fun clear() {
        pending = null
        autoSure.clear()
    }

    private fun sync(client: Minecraft) {
        if (lastLevel !== client.level || lastConnection !== client.connection || runMarker != Run.startStamp) {
            clear()
            lastText = ""
            lastAt = 0L
            lastLevel = client.level
            lastConnection = client.connection
            runMarker = Run.startStamp
        }
        if (!inHaunted()) clear()
    }

    fun init() {
        // Fallback in case the low-level mixin is not reached
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is ChatScreen) {
                ScreenMouseEvents.allowMouseClick(screen).register { _, click ->
                    // Left click is 0 on 26.2 (GLFW) and 1 on 26.3 (SDL), so always compare with the constant
                    if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && fire()) false else true
                }
            }
        }
    }

    /** Every incoming chat message (as a Component) is passed here. */
    fun onMessage(message: Component) {
        val client = Minecraft.getInstance()
        sync(client)
        if (!inHaunted() || client.connection == null) return
        val text = ChatParser.clean(message.string)
        val now = System.currentTimeMillis()
        autoSure.onMessage(text, now)
        if (HideyhoDialogMessages.isHiding(text) || HideyhoDialogMessages.isOtherNpc(text)) {
            clear()
            return
        }
        if (!text.contains("Select an option", ignoreCase = true)) return
        // The same message can arrive from more than one listener
        if (text == lastText && now - lastAt < 1000) return
        lastText = text; lastAt = now

        val button = findSure(message) ?: return
        if (!autoSure.schedule(now)) return
        pending = button
        offeredAt = now
        connection = client.connection
        if (!Config.s.hideyhoAutoSure) client.player?.sendSystemMessage(
            Brand.msg("Click anywhere for §a[Sure]"))
    }

    fun tick(client: Minecraft) {
        sync(client)
        val now = System.currentTimeMillis()
        if (pending != null && now - offeredAt !in 0L..VALID_MS) clear()
        if (!Config.s.hideyhoAutoSure || pending == null || !autoSure.isDue(now) || !client.isWindowActive) return
        val screen = client.gui.screen()
        if (screen == null || screen is ChatScreen) fire()
    }

    /** Finds the click action of the [Sure] button (shortest matching text, inherited styles included). */
    private fun findSure(message: Component): ClickEvent? {
        var best: ClickEvent? = null
        var bestLen = Int.MAX_VALUE
        fun visit(node: Component, inherited: Style) {
            val style = node.style.applyTo(inherited)
            val event = style.clickEvent
            val text = ChatParser.clean(node.string)
            if (sureWord.containsMatchIn(text) && text.length < bestLen &&
                (event is ClickEvent.RunCommand || event is ClickEvent.Custom || event is ClickEvent.SuggestCommand)) {
                best = event; bestLen = text.length
            }
            node.siblings.forEach { visit(it, style) }
        }
        visit(message, Style.EMPTY)
        return best
    }

    /** Called from the mixin on a left click while chat is open. true = the click was turned into [Sure]. */
    fun onChatClick(): Boolean = pending != null && fire()

    private fun fire(): Boolean {
        val event = pending ?: return false
        val client = Minecraft.getInstance()
        sync(client)
        if (pending == null) return false
        val active = client.connection
        if (active == null || active !== connection ||
            System.currentTimeMillis() - offeredAt !in 0L..VALID_MS) {
            clear(); return false
        }
        clear()
        // Close the chat so it doesn't stay open after the click
        client.execute { if (client.gui.screen() is ChatScreen) client.gui.setScreen(null) }
        when (event) {
            is ClickEvent.RunCommand -> active.sendCommand(event.command().removePrefix("/"))
            is ClickEvent.Custom -> active.send(ServerboundCustomClickActionPacket(event.id(), event.payload()))
            is ClickEvent.SuggestCommand -> active.sendCommand(event.command().removePrefix("/"))
            else -> return false
        }
        return true
    }
}
