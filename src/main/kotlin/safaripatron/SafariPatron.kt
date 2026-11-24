package safaripatron

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import safaripatron.config.Config
import safaripatron.contest.Contest
import safaripatron.esp.Esp
import safaripatron.esp.Structures
import safaripatron.hud.Hud
import safaripatron.party.Party
import safaripatron.render.WorldRender
import safaripatron.safari.ChatParser
import safaripatron.safari.CavernGem
import safaripatron.safari.GemPlacer
import safaripatron.safari.HideyhoClick
import safaripatron.safari.Safari
import safaripatron.safari.ScrappyFoodHints
import safaripatron.sparkling.SparklingFinder

/**
 * Safari Patron — Hypixel SkyBlock Critter Safari yardımcısı.
 * Bölge, yaratık ve koşu bilgilerini gösterir; parti sohbeti anonsları yapar.
 * İsteğe bağlı yardımcılar (gem, Hideyho, Rockmite, giriş koruması) ayarlardan açılıp kapanır.
 */
object SafariPatron : ClientModInitializer {
    const val MOD_ID = "safari-patron"

    private val gemPlacer = GemPlacer(mapOf(
        BlockPos(-140, 61, 56) to CavernGem.PURPLE,
        BlockPos(-137, 61, 51) to CavernGem.LIME,
        BlockPos(-140, 61, 46) to CavernGem.ORANGE,
    ))

    override fun onInitializeClient() {
        Config.load()
        WorldRender.init()
        Hud.init()
        safaripatron.cinematic.SparklingCinematic.init()
        Hud.initScroll()
        Party.init()
        Structures.init()
        safaripatron.resources.FloorDrops.init()
        HideyhoClick.init()
        Commands.init()
        UpdateChecker.init()
        safaripatron.history.History.init()
        safaripatron.achievements.Achievements.init()
        safaripatron.capsule.Prefire.init()
        safaripatron.contest.Honeyhives.init()
        safaripatron.profit.Profit.init()

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> Contest.reset(); safaripatron.visual.ClearWater.reset() }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            Safari.tick(client)
            gemPlacer.tick(client)
            Esp.tick(client)
            ScrappyFoodHints.tick(client)
            HideyhoClick.tick(client)
            SparklingFinder.tick(client)
            Party.tick(client)
            safaripatron.party.HotspotAssign.tick(client)
            Contest.tick(client)
            safaripatron.contest.Honeyhives.tick(client)
            safaripatron.sparkling.Critterdex.tick(client)
            safaripatron.profit.Profit.tick(client, Safari.inSafari)
            safaripatron.profit.Bazaar.tick(client)
            safaripatron.achievements.Achievements.tick(client)
            safaripatron.capsule.Prefire.tick(client)
            safaripatron.visual.ClearWater.tick(client)
        }

        // Hypixel bazı NPC mesajlarını oyuncu sohbeti olarak gönderebiliyor; Hideyho seçenekleri için onu da dinle
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ ->
            Minecraft.getInstance().execute {
                HideyhoClick.onMessage(message)
                gemPlacer.onMessage(message.string)
            }
        }
        // Başka modlar (sohbet düzenleyiciler) mesajı değiştirmeden önceki hâlini de yakala
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            if (!overlay) Minecraft.getInstance().execute {
                HideyhoClick.onMessage(message)
                gemPlacer.onMessage(message.string)
            }
            true
        }

        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay) return@register
            val raw = message.string
            val client = Minecraft.getInstance()
            client.execute {
                HideyhoClick.onMessage(message)
                gemPlacer.onMessage(raw)
                safaripatron.resources.FloorDrops.onMessage(raw)
                safaripatron.helpers.RockmiteHoldClick.onMessage(raw)
                Party.onChatLine(raw)
                safaripatron.party.HotspotAssign.onChatLine(raw)
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
