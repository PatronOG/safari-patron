package safaripatron.profit

import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Bazaar prices from Hypixel's public endpoint (no API key, nothing sent to the game server).
 * Refreshed at most every 10 minutes, and only while something asks for a price.
 */
object Bazaar {
    private const val URL = "https://api.hypixel.net/v2/skyblock/bazaar"
    private const val REFRESH_MS = 10 * 60 * 1000L
    const val FEATHER = "RAINBOW_FEATHER"
    const val ESSENCE = "ESSENCE_SAFARI"
    /** Not a Bazaar item: priced from the Auction House (lowest BIN). */
    const val DYE = "DYE_EMERALD"

    private val log = LoggerFactory.getLogger("safari-patron/bazaar")
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    /** id -> [instant sell, sell offer] */
    @Volatile private var prices: Map<String, DoubleArray> = emptyMap()
    @Volatile private var fetching = false
    private var lastFetch = 0L
    /** When prices last arrived, and whether the most recent lookup failed. */
    @Volatile private var lastSuccess = 0L
    @Volatile private var lastFailed = false
    private const val STALE_MS = 30 * 60 * 1000L

    fun shardId(critter: String) = "SHARD_" + critter.uppercase().replace(' ', '_').replace("'", "")

    fun ready() = prices.isNotEmpty()

    /**
     * A warning for the profit panels, or null while prices are fine:
     * no prices could be loaded at all, or the last good prices are over 30 minutes old.
     */
    fun warning(): String? {
        if (prices.isEmpty()) return if (lastFailed) "⚠ Prices unavailable (Bazaar offline)" else null
        val age = System.currentTimeMillis() - lastSuccess
        if (lastFailed && age > STALE_MS) return "⚠ Prices are ${age / 60_000} min old (Bazaar offline)"
        return null
    }

    /** Price of one item, or 0 if unknown. offer = sell offer price instead of instant sell. */
    fun price(id: String, offer: Boolean): Double {
        if (id == DYE) return Auction.lowestBin(id)
        ensureFresh()
        return prices[id]?.getOrNull(if (offer) 1 else 0)?.takeIf { it.isFinite() && it > 0 } ?: 0.0
    }

    fun ensureFresh() {
        val now = System.currentTimeMillis()
        if (fetching || now - lastFetch < REFRESH_MS) return
        fetching = true
        lastFetch = now
        val request = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(20))
            .header("User-Agent", "safari-patron").GET().build()
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { res, err ->
            try {
                if (err != null || res.statusCode() != 200) {
                    log.warn("Bazaar lookup failed: {}", err?.message ?: "HTTP ${res.statusCode()}")
                    lastFailed = true
                    lastFetch = System.currentTimeMillis() - REFRESH_MS + 60_000L // retry in a minute
                    return@whenComplete
                }
                val products = JsonParser.parseString(res.body()).asJsonObject.getAsJsonObject("products")
                val out = HashMap<String, DoubleArray>()
                for ((id, el) in products.entrySet()) {
                    // Only what the profit tracker needs
                    if (!id.startsWith("SHARD_") && id != FEATHER && id != ESSENCE) continue
                    val q = el.asJsonObject.getAsJsonObject("quick_status") ?: continue
                    out[id] = doubleArrayOf(q.get("sellPrice").asDouble, q.get("buyPrice").asDouble)
                }
                if (out.isNotEmpty()) {
                    prices = out
                    lastSuccess = System.currentTimeMillis()
                    lastFailed = false
                } else lastFailed = true
            } catch (e: Exception) {
                log.warn("Bazaar response unreadable: {}", e.message)
                lastFailed = true
                lastFetch = System.currentTimeMillis() - REFRESH_MS + 60_000L // retry in a minute
            } finally {
                fetching = false
            }
        }
    }
}
