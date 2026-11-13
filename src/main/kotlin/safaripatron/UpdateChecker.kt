package safaripatron

import com.google.gson.JsonParser
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import safaripatron.config.Config
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Asks GitHub once per game start whether a newer release exists and, if so, says so in chat
 * after joining a server. Never downloads anything. Can be turned off in the menu.
 */
object UpdateChecker {
    private const val API = "https://api.github.com/repos/PatronOG/safari-patron/releases/latest"
    private const val RELEASES = "https://github.com/PatronOG/safari-patron/releases/latest"

    @Volatile private var latest: String? = null
    @Volatile private var url: String = RELEASES
    private var notified = false

    fun init() {
        if (Config.s.updateChecker) fetch()
        ClientPlayConnectionEvents.JOIN.register { _, _, client ->
            // Give the server a few seconds so the message isn't buried by join spam
            Thread {
                Thread.sleep(5000)
                client.execute { notifyIfNewer(client) }
            }.apply { isDaemon = true; name = "Safari Patron update notice" }.start()
        }
    }

    private fun fetch() {
        val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
        val request = HttpRequest.newBuilder(URI.create(API))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "safari-patron/${current()}")
            .GET().build()
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept { res ->
            if (res.statusCode() != 200) return@thenAccept
            runCatching {
                val json = JsonParser.parseString(res.body()).asJsonObject
                latest = json.get("tag_name")?.asString
                json.get("html_url")?.asString?.let { url = it }
            }
        }.exceptionally { null } // offline or GitHub unreachable: stay quiet
    }

    private fun notifyIfNewer(client: Minecraft) {
        if (notified || !Config.s.updateChecker) return
        val tag = latest ?: return
        val player = client.player ?: return
        if (compare(numbers(tag), numbers(current())) <= 0) return
        notified = true
        val link = Component.literal("§a§n[Download]").withStyle {
            it.withClickEvent(ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(HoverEvent.ShowText(Component.literal("Open the release page on GitHub")))
        }
        player.sendSystemMessage(
            Brand.msg("Update available: §7${plain(current())} §f→ §a${plain(tag)} ").append(link))
    }

    private fun current(): String = FabricLoader.getInstance().getModContainer(SafariPatron.MOD_ID)
        .map { it.metadata.version.friendlyString }.orElse("0")

    /** "v1.0.2+26.3" -> "1.0.2" */
    private fun plain(v: String) = v.removePrefix("v").substringBefore('+').substringBefore('-')

    private fun numbers(v: String) = plain(v).split('.').map { it.toIntOrNull() ?: 0 }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}
