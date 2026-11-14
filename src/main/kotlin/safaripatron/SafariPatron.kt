package safaripatron

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import safaripatron.config.Config
import safaripatron.contest.Contest
import safaripatron.esp.Esp
import safaripatron.esp.Structures
import safaripatron.hud.Hud
import safaripatron.party.Party
import safaripatron.render.WorldRender
import safaripatron.safari.ChatParser
import safaripatron.safari.HideyhoClick
import safaripatron.safari.Safari
import safaripatron.sparkling.SparklingFinder

/**
 * Safari Patron — Hypixel SkyBlock Critter Safari yardımcısı.
 * Sadece okur ve gösterir; oyuncu adına tıklamaz, yürümez, nişan almaz.
 * (Tek otomatik eylem: ayarlardan kapatılabilen parti chati anonsları.)
 */
object SafariPatron : ClientModInitializer {
    const val MOD_ID = "safari-patron"

    override fun onInitializeClient() {
        Config.load()
        WorldRender.init()
        Hud.init()
        Hud.initScroll()
        Party.init()
        Structures.init()
        HideyhoClick.init()
        Commands.init()
        UpdateChecker.init()
        safaripatron.history.History.init()
        safaripatron.contest.Honeyhives.init()
        safaripatron.profit.Profit.init()

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> Contest.reset(); safaripatron.visual.ClearWater.reset() }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            Safari.tick(client)
            Esp.tick(client)
            SparklingFinder.tick(client)
            Party.tick(client)
            Contest.tick(client)
            safaripatron.contest.Honeyhives.tick(client)
            safaripatron.sparkling.Critterdex.tick(client)
            safaripatron.profit.Profit.tick(client, Safari.inSafari)
            safaripatron.visual.ClearWater.tick(client)
        }

        // Hypixel bazı NPC mesajlarını oyuncu sohbeti olarak gönderebiliyor; Hideyho seçenekleri için onu da dinle
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ ->
            Minecraft.getInstance().execute { HideyhoClick.onMessage(message) }
        }
        // Başka modlar (sohbet düzenleyiciler) mesajı değiştirmeden önceki hâlini de yakala
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            if (!overlay) Minecraft.getInstance().execute { HideyhoClick.onMessage(message) }
            true
        }

        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay) return@register
            val raw = message.string
            val client = Minecraft.getInstance()
            client.execute {
                HideyhoClick.onMessage(message)
                Party.onChatLine(raw)
                safaripatron.profit.Profit.onMessage(raw)
                safaripatron.contest.Honeyhives.onMessage(raw)
                val self = client.player?.name?.string ?: return@execute
                for (event in ChatParser.parse(raw)) {
                    safaripatron.history.History.onEvent(event, self)
                    Safari.onChat(event, self)
                }
            }
        }
    }
}
