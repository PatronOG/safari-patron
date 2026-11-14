package safaripatron.profit

import safaripatron.config.Config
import safaripatron.data.Biome

/** /sp topshards: each biome's most profitable shards, ranked by their Bazaar price per shard. */
object TopShards {
    const val COUNT = 5

    class Entry(val name: String, val color: String, val price: Double)

    fun top(biome: Biome, n: Int = COUNT): List<Entry> {
        val offer = Config.s.profitSellOffer
        return biome.critters.map { c ->
            val id = Bazaar.shardId(c.name)
            Entry(c.name, Profit.itemColor(id), Bazaar.price(id, offer))
        }.filter { it.price > 0 }.sortedByDescending { it.price }.take(n)
    }

    /** "§9Hideonwall §6124.0k" */
    fun format(e: Entry): String = "${e.color}${e.name} §6${Profit.coins(e.price)}"

    /** Hover text: the price and which Bazaar price it is. */
    fun details(e: Entry): String = "${e.color}${e.name} Shard\n§7${if (Config.s.profitSellOffer) "Sell Offer" else "Instant Sell"}: §6${Profit.coins(e.price)}"
}
