package safaripatron.profit

import safaripatron.config.Config
import safaripatron.data.Biome
import safaripatron.history.History
import java.util.Locale

/**
 * /sp topshards: each biome's most profitable shards to catch.
 * Ranked by what one catch is worth to you: Bazaar price x the shards you get per catch on average
 * (learned from your own catches; 1 until a critter has 5 catches, shown with "~").
 */
object TopShards {
    const val COUNT = 5

    class Entry(val name: String, val color: String, val price: Double, val perCatch: Double, val learned: Boolean) {
        val worth get() = price * perCatch
    }

    fun top(biome: Biome, n: Int = COUNT): List<Entry> {
        val offer = Config.s.profitSellOffer
        return biome.critters.map { c ->
            val id = Bazaar.shardId(c.name)
            val (avg, learned) = History.shardsPerCatch(c.name)
            Entry(c.name, Profit.itemColor(id), Bazaar.price(id, offer), avg, learned)
        }.filter { it.price > 0 }.sortedByDescending { it.worth }.take(n)
    }

    /** "§9Hideonwall §6~744.0k" */
    fun format(e: Entry): String = "${e.color}${e.name} §6${if (e.learned) "" else "~"}${Profit.coins(e.worth)}"

    /** Hover text: price, shards per catch and how it was learned. */
    fun details(e: Entry): String = "${e.color}${e.name}§7: ${Profit.coins(e.price)} each × " +
        String.format(Locale.ROOT, "%.1f", e.perCatch) + " per catch" + if (e.learned) "" else " §8(estimate)"
}
