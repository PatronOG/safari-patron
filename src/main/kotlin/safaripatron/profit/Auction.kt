package safaripatron.profit

import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Auction House prices (lowest BIN) for the few profit items that are not on the Bazaar, such as the Emerald Dye.
 * From SkyCofl's public API (https://sky.coflnet.com, no key). Asked only for items the tracker has seen,
 * at most every 30 minutes per item.
 */
object Auction {
    private const val URL = "https://sky.coflnet.com/api/item/price/%s/bin"
    private const val REFRESH_MS = 30 * 60 * 1000L
    private const val RETRY_MS = 2 * 60 * 1000L

    private val log = LoggerFactory.getLogger("safari-patron/auction")
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    private val prices = ConcurrentHashMap<String, Double>()
    private val nextFetch = ConcurrentHashMap<String, Long>()
    private val fetching = ConcurrentHashMap.newKeySet<String>()

    /** Lowest BIN of an item, or 0 while unknown. */
    fun lowestBin(id: String): Double {
        ensureFresh(id)
        return prices[id] ?: 0.0
    }

    private fun ensureFresh(id: String) {
        val now = System.currentTimeMillis()
        if (id in fetching || now < (nextFetch[id] ?: 0L)) return
        fetching.add(id)
        nextFetch[id] = now + REFRESH_MS
        val request = HttpRequest.newBuilder(URI.create(URL.format(id))).timeout(Duration.ofSeconds(20))
            .header("User-Agent", "safari-patron").GET().build()
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { res, err ->
            try {
                if (err != null || res.statusCode() != 200) {
                    log.warn("Auction price lookup for {} failed: {}", id, err?.message ?: "HTTP ${res.statusCode()}")
                    nextFetch[id] = System.currentTimeMillis() + RETRY_MS
                    return@whenComplete
                }
                val lowest = JsonParser.parseString(res.body()).asJsonObject.get("lowest")?.asDouble ?: 0.0
                if (lowest.isFinite() && lowest > 0) prices[id] = lowest
                else nextFetch[id] = System.currentTimeMillis() + RETRY_MS
            } catch (e: Exception) {
                log.warn("Auction price for {} unreadable: {}", id, e.message)
                nextFetch[id] = System.currentTimeMillis() + RETRY_MS
            } finally {
                fetching.remove(id)
            }
        }
    }
}
