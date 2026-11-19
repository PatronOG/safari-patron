package safaripatron.achievements

import com.google.gson.GsonBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.sounds.SoundEvents
import org.slf4j.LoggerFactory
import safaripatron.Brand
import safaripatron.config.Config
import safaripatron.config.Paths
import safaripatron.data.Biome
import safaripatron.data.Critters
import safaripatron.history.History
import safaripatron.history.RunRecord
import safaripatron.profit.Bazaar
import safaripatron.profit.Profit
import safaripatron.safari.Run

/** Rarity of an achievement: Hypixel's item rarity colors, and its points. */
enum class Tier(val label: String, val code: String, val color: Int, val points: Int) {
    COMMON("Common", "§f", 0xFFFFFFFF.toInt(), 5),
    UNCOMMON("Uncommon", "§a", 0xFF55FF55.toInt(), 10),
    RARE("Rare", "§9", 0xFF5555FF.toInt(), 20),
    EPIC("Epic", "§5", 0xFFAA00AA.toInt(), 35),
    LEGENDARY("Legendary", "§6", 0xFFFFAA00.toInt(), 50),
    MYTHIC("Mythic", "§d", 0xFFFF55FF.toInt(), 75),
    DIVINE("Divine", "§b", 0xFF55FFFF.toInt(), 100),
    CELESTIAL("Celestial", "§3", 0xFF00AAAA.toInt(), 150),
    IMPOSSIBLE("Impossible", "§c", 0xFFFF5555.toInt(), 250),
}

/**
 * One achievement. [progress] = (have, goal) for a bar, or null; [best] = text instead of numbers (times).
 * Everything is worked out from data the mod already keeps: Run History, the profit totals, PBs and the Critterdex.
 */
class Achievement(
    val id: String,
    val name: String,
    val desc: String,
    val tier: Tier,
    val hidden: Boolean = false,
    /** Unlocked by a finished run (checked run by run, also for old runs). */
    val perRun: ((RunRecord) -> Boolean)? = null,
    /** Unlocked by totals (runs, coins, PBs, Critterdex...). */
    val total: (() -> Boolean)? = null,
    val progress: (() -> Pair<Double, Double>?)? = null,
    val progressText: (() -> String?)? = null,
)

class Unlock(var at: Long = 0L, var run: Int = 0)

class AchievementData {
    var version = 1
    var unlocked: MutableMap<String, Unlock> = LinkedHashMap()
    /** Old runs were checked once (done when the achievements first load). */
    var retroDone = false
    /** Runs in a row with Everything Done, and the longest such streak. */
    var allStreak = 0
    var bestAllStreak = 0
    /** Runs in a row without a sparkling (before the latest run). */
    var dry = 0
    /** Best single run so far, for progress bars. */
    var maxSparklings = 0
    var maxFeathers = 0L
    var maxValue = 0.0
    /** Sparkling achievements that once also counted the Critterdex were checked again against the history. */
    var sparklingRecheck = false
}

object Achievements {
    private val log = LoggerFactory.getLogger("safari-patron/achievements")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = Paths.dir.resolve("achievements.json")

    var data = AchievementData()
        private set
    /** Goes up when something is unlocked, so the screen can redraw its lists. */
    var version = 0
        private set

    private val BIG_FOUR = setOf("Hideyho", "Wumpa", "Doomspiral", Critters.MACAW)
    private const val M = 1_000_000.0
    private const val B = 1_000_000_000.0

    // ---------- what counts ----------

    private fun self(): String? = Minecraft.getInstance().player?.name?.string

    private fun isSelf(r: RunRecord, name: String) = name.equals(r.self, ignoreCase = true)

    /** Runs: the run history, or the profit tracker's count when that is higher (it is older). */
    private fun runs() = maxOf(History.totals.runs, Profit.data.totalRuns)

    /** Achievements count coins at Sell Offer prices, whatever the Price setting. */
    private fun totalCoins() = Profit.value(Profit.data.totalItems, offer = true)

    private fun dyes() = Profit.data.totalItems[Bazaar.DYE] ?: 0L

    /** Fastest time of a PB key, 0 if none. */
    private fun best(key: String) = Config.s.bestTimes[key]?.ms?.takeIf { it > 0 } ?: 0L

    /** Everything except Macaw: its own PB, or Everything's (which is never slower). */
    private fun bestButMacaw() = listOf(best("allButMacaw"), best("all")).filter { it > 0 }.minOrNull() ?: 0L

    private fun dex() = Config.s.sparklingCollection.size

    private fun mostRunsWithOne() = History.totals.players.values.maxOfOrNull { it.runs } ?: 0

    private fun under(ms: () -> Long, goalMs: Long) = Pair<() -> Boolean, () -> String?>(
        { ms().let { it in 1 until goalMs } },
        { ms().takeIf { it > 0 }?.let { "Best ${Run.time(it)}" } },
    )

    private fun timeProgress(ms: () -> Long, goalMs: Long): () -> Pair<Double, Double>? = {
        ms().takeIf { it > 0 }?.let { goalMs.toDouble() / it to 1.0 }
    }

    private fun yourCritters(r: RunRecord) = r.players.entries.firstOrNull { isSelf(r, it.key) }?.value.orEmpty()

    val all: List<Achievement> by lazy {
        fun timeAch(id: String, name: String, desc: String, tier: Tier, ms: () -> Long, goal: Long): Achievement {
            val (done, text) = under(ms, goal)
            return Achievement(id, name, desc, tier, total = done, progress = timeProgress(ms, goal),
                progressText = { text()?.let { "$it · goal ${Run.time(goal)}" } })
        }
        listOf(
            // Common
            Achievement("first_steps", "First Steps", "Finish your first Safari run.", Tier.COMMON,
                total = { runs() >= 1 }),
            Achievement("something_shiny", "Something Shiny", "See a sparkling caught in your party.", Tier.COMMON,
                perRun = { it.sparklings.isNotEmpty() }),
            Achievement("first_pb", "First Personal Best", "Set your first personal best.", Tier.COMMON,
                total = { Config.s.bestTimes.values.any { it.ms > 0 } }),
            Achievement("full_house", "Full House", "Catch all ${Critters.TOTAL} critters in one run with your party.", Tier.COMMON,
                perRun = { it.party >= Critters.TOTAL }),
            Achievement("coins_100m", "Just the Beginning", "Earn 100M coins from the Safari.", Tier.COMMON,
                total = { totalCoins() >= 100 * M }, progress = { totalCoins() to 100 * M }),
            // Uncommon
            Achievement("runs_50", "Regular", "Finish 50 runs.", Tier.UNCOMMON,
                total = { runs() >= 50 }, progress = { runs().toDouble() to 50.0 }),
            Achievement("team_player", "Team Player", "Do 10 runs with the same player.", Tier.UNCOMMON,
                total = { mostRunsWithOne() >= 10 }, progress = { mostRunsWithOne().toDouble() to 10.0 }),
            Achievement("marathon", "Marathon", "Finish 20 runs in one day.", Tier.UNCOMMON,
                total = { (History.totals.days.values.maxOfOrNull { it.runs } ?: 0) >= 20 },
                progress = { (History.totals.days.values.maxOfOrNull { it.runs } ?: 0).toDouble() to 20.0 }),
            Achievement("hotspot_hunter", "Hotspot Hunter", "Catch the critter that finishes the Hunting Hotspot biome.",
                Tier.UNCOMMON, hidden = true),
            timeAch("under_four", "Under Four", "Finish Everything except Macaw in under 4:00.", Tier.UNCOMMON, ::bestButMacaw, 240_000),
            // Rare
            Achievement("coins_500m", "Halfway There", "Earn 500M coins from the Safari.", Tier.RARE,
                total = { totalCoins() >= 500 * M }, progress = { totalCoins() to 500 * M }),
            Achievement("runs_250", "Veteran", "Finish 250 runs.", Tier.RARE,
                total = { runs() >= 250 }, progress = { runs().toDouble() to 250.0 }),
            Achievement("my_shiny", "My Shiny!", "Catch a sparkling yourself.", Tier.RARE,
                perRun = { r -> r.sparklings.any { isSelf(r, it.player) } }),
            Achievement("six_figures", "Six Figures?", "Earn over 6M coins in a single run.", Tier.RARE,
                perRun = { it.offer > 6 * M }, progress = { data.maxValue to 6 * M }),
            timeAch("sub_3", "Sub 3", "Finish Everything in under 3:00.", Tier.RARE, { best("all") }, 180_000),
            // Epic
            Achievement("daily_ranger", "Daily Ranger", "Do runs on 7 different days.", Tier.EPIC,
                total = { History.totals.days.size >= 7 }, progress = { History.totals.days.size.toDouble() to 7.0 }),
            Achievement("besto_friendo", "Besto Friendo", "Do 100 runs with the same player.", Tier.EPIC,
                total = { mostRunsWithOne() >= 100 }, progress = { mostRunsWithOne().toDouble() to 100.0 }),
            Achievement("runs_500", "Safari Legend", "Finish 500 runs.", Tier.EPIC,
                total = { runs() >= 500 }, progress = { runs().toDouble() to 500.0 }),
            Achievement("coins_1b", "First Billion!", "Earn 1B coins from the Safari.", Tier.EPIC,
                total = { totalCoins() >= B }, progress = { totalCoins() to B }),
            Achievement("so_close", "So Close", "Finish a run at 36/37, missing a critter other than Macaw.", Tier.EPIC,
                hidden = true, perRun = { r ->
                    r.party == Critters.TOTAL - 1 && r.players.values.any { Critters.MACAW in it }
                }),
            // Legendary
            Achievement("double_trouble", "Double Trouble", "See 2 sparklings caught in a single run.", Tier.LEGENDARY,
                perRun = { it.sparklings.size >= 2 }, progress = { data.maxSparklings.toDouble() to 2.0 }),
            Achievement("dex_20", "Shiny Collector", "Register 20 sparklings in your Critterdex.", Tier.LEGENDARY,
                total = { dex() >= 20 }, progress = { dex().toDouble() to 20.0 }),
            Achievement("dry_streak", "Dry Streak", "Finish 50 runs in a row without a sparkling.", Tier.LEGENDARY,
                total = { History.totals.longestDry >= 50 }, progress = { History.totals.longestDry.toDouble() to 50.0 }),
            Achievement("worth_the_wait", "Worth the Wait", "See a sparkling caught after 50 runs without one.", Tier.LEGENDARY),
            Achievement("big_four_one", "One of the Big 4", "See a sparkling Hideyho, Wumpa, Doomspiral or Macaw caught.",
                Tier.LEGENDARY, perRun = { r -> r.sparklings.any { it.critter in BIG_FOUR } },
                total = { Config.s.sparklingCollection.any { it in BIG_FOUR } }),
            // Mythic
            Achievement("emerald_luck", "Emerald Luck", "Find an Emerald Dye.", Tier.MYTHIC,
                total = { dyes() >= 1 }),
            Achievement("perfectionist", "Perfectionist", "Finish Everything in 5 runs in a row.", Tier.MYTHIC,
                progress = { data.bestAllStreak.toDouble() to 5.0 }),
            Achievement("runs_1000", "Safari King", "Finish 1,000 runs.", Tier.MYTHIC,
                total = { runs() >= 1000 }, progress = { runs().toDouble() to 1000.0 }),
            Achievement("everywhere", "I'm Everywhere", "Catch a critter in every biome during a 4-player run.", Tier.MYTHIC,
                hidden = true, perRun = { r ->
                    r.players.size >= 4 && yourCritters(r).mapNotNull { Critters.named(it)?.biome }.toSet().size == Biome.entries.size
                }),
            // Divine
            timeAch("sub_2", "Sub 2", "Finish Everything in under 2:00.", Tier.DIVINE, { best("all") }, 120_000),
            Achievement("coins_3b", "Three Billion", "Earn 3B coins from the Safari.", Tier.DIVINE,
                total = { totalCoins() >= 3 * B }, progress = { totalCoins() to 3 * B }),
            Achievement("birds_of_a_feather", "Birds of a Feather", "Get 4 Rainbow Feathers in a single run.", Tier.DIVINE,
                perRun = { (it.items[Bazaar.FEATHER] ?: 0L) >= 4 }, progress = { data.maxFeathers.toDouble() to 4.0 }),
            // Celestial
            Achievement("two_dyes", "Can I craft a sword now?", "Find 2 Emerald Dyes.", Tier.CELESTIAL,
                total = { dyes() >= 2 }, progress = { dyes().toDouble() to 2.0 }),
            Achievement("dex_33", "Here Comes the Hard Part", "Register 33 sparklings in your Critterdex.", Tier.CELESTIAL,
                total = { dex() >= 33 }, progress = { dex().toDouble() to 33.0 }),
            Achievement("big_four_hunter", "Big Four Hunter", "Catch a sparkling Hideyho, Wumpa, Doomspiral or Macaw yourself.",
                Tier.CELESTIAL, perRun = { r -> r.sparklings.any { it.critter in BIG_FOUR && isSelf(r, it.player) } }),
            // Impossible
            Achievement("dex_master", "Critterdex Master", "Complete your Sparkling Critterdex.", Tier.IMPOSSIBLE,
                total = { Critters.all.all { it.name in Config.s.sparklingCollection } },
                progress = { dex().toDouble() to Critters.TOTAL.toDouble() }),
            timeAch("lightning_run", "Lightning Run", "Finish Everything in under 1:45.", Tier.IMPOSSIBLE, { best("all") }, 105_000),
            Achievement("shiny_storm", "Shiny Storm", "See 4 sparklings caught in a single run.", Tier.IMPOSSIBLE,
                perRun = { it.sparklings.size >= 4 }, progress = { data.maxSparklings.toDouble() to 4.0 }),
        )
    }

    fun byId(id: String) = all.firstOrNull { it.id == id }
    fun isUnlocked(id: String) = id in data.unlocked
    val points get() = all.filter { isUnlocked(it.id) }.sumOf { it.tier.points }
    val maxPoints get() = all.sumOf { it.tier.points }

    // ---------- files ----------

    fun init() {
        data = Paths.read(file)?.let { runCatching { gson.fromJson(it, AchievementData::class.java) }.getOrNull() } ?: AchievementData()
        @Suppress("SENSELESS_COMPARISON")
        if (data.unlocked == null) data.unlocked = LinkedHashMap()
        data.unlocked.keys.retainAll(all.map { it.id }.toSet())
    }

    fun save() = Paths.write(file, gson.toJson(data))

    // ---------- checking ----------

    /** Run-by-run bookkeeping (streaks and bests) and the per-run achievements. Returns what this run unlocked. */
    private fun applyRun(r: RunRecord, out: MutableList<Pair<Achievement, Int>>) {
        val d = data
        if ("all" in r.biomeMs) { d.allStreak++; d.bestAllStreak = maxOf(d.bestAllStreak, d.allStreak) } else d.allStreak = 0
        d.maxSparklings = maxOf(d.maxSparklings, r.sparklings.size)
        d.maxFeathers = maxOf(d.maxFeathers, r.items[Bazaar.FEATHER] ?: 0L)
        d.maxValue = maxOf(d.maxValue, r.offer)
        val wasDry = d.dry
        d.dry = if (r.sparklings.isEmpty()) d.dry + 1 else 0

        fun hit(id: String) { byId(id)?.let { a -> if (!isUnlocked(a.id) && out.none { it.first === a }) out.add(a to r.id) } }
        for (a in all) if (a.perRun?.invoke(r) == true) hit(a.id)
        if (d.bestAllStreak >= 5) hit("perfectionist")
        if (r.sparklings.isNotEmpty() && wasDry >= 50) hit("worth_the_wait")
    }

    private fun checkTotals(out: MutableList<Pair<Achievement, Int>>) {
        for (a in all) {
            if (isUnlocked(a.id) || out.any { it.first === a }) continue
            if (runCatching { a.total?.invoke() == true }.getOrDefault(false)) out.add(a to 0)
        }
    }

    /** A run was saved to the history. */
    fun onRun(r: RunRecord) {
        if (!data.retroDone) return // the first check goes over every run, this one too
        val out = ArrayList<Pair<Achievement, Int>>()
        applyRun(r, out)
        checkTotals(out)
        unlock(out, quiet = false)
        save()
    }

    /** Achievements that go by a run's coins: checked again over every run when run values were filled in. */
    private val valueIds = listOf("six_figures")

    /** History filled in run values (a missing price, or the Sell Offer value of older runs). */
    fun onValuesChanged() {
        if (!data.retroDone) return // the first check goes over every run anyway
        data.maxValue = History.runs.maxOfOrNull { it.offer } ?: 0.0
        val out = ArrayList<Pair<Achievement, Int>>()
        for (id in valueIds) {
            val a = byId(id) ?: continue
            if (isUnlocked(id)) continue
            History.runs.firstOrNull { a.perRun?.invoke(it) == true }?.let { out.add(a to it.id) }
        }
        unlock(out, quiet = false)
        version++
        save()
    }

    /** Hotspot Hunter: a biome was finished, [finisher] caught its last critter. */
    fun onBiomeDone(biome: Biome, finisher: String) {
        if (isUnlocked("hotspot_hunter")) return
        val hotspot = History.currentHotspot() ?: return
        if (!hotspot.equals(biome.label, ignoreCase = true) && !hotspot.equals(biome.name, ignoreCase = true)) return
        if (!finisher.equals(self(), ignoreCase = true)) return
        byId("hotspot_hunter")?.let { unlock(listOf(it to 0), quiet = false); save() }
    }

    private var nextCheck = 0L
    private var joinedAt = 0L

    /**
     * Every 5 seconds in game: totals that change outside runs (PBs, Critterdex, coins).
     * The first time, once prices are known (or after a minute), every old run is checked quietly.
     */
    fun tick(client: Minecraft) {
        if (client.player == null) { joinedAt = 0L; return }
        val now = System.currentTimeMillis()
        if (joinedAt == 0L) joinedAt = now
        if (now < nextCheck) return
        nextCheck = now + 5_000L
        if (!data.retroDone) {
            if (Bazaar.ready() || now - joinedAt > 60_000L) retro()
            return
        }
        if (!data.sparklingRecheck) recheckSparklings()
        if (!History.valuesDone) History.fixValues()
        val out = ArrayList<Pair<Achievement, Int>>()
        checkTotals(out)
        if (out.isNotEmpty()) { unlock(out, quiet = false); save() }
    }

    /**
     * Something Shiny, My Shiny! and Big Four Hunter used to count the Critterdex too; now only the run history
     * counts (who caught each sparkling). Once: take them back if no saved run earned them.
     */
    private fun recheckSparklings() {
        History.ensureCount(Int.MAX_VALUE)
        for (id in listOf("something_shiny", "my_shiny", "big_four_hunter")) {
            if (!isUnlocked(id)) continue
            val a = byId(id) ?: continue
            if (History.runs.none { r -> a.perRun?.invoke(r) == true }) {
                data.unlocked.remove(id)
                log.info("Took back {}: no saved run earned it", id)
            }
        }
        data.sparklingRecheck = true
        version++
        save()
    }

    /** Checks every saved run and the totals once, and unlocks what they already earned, with one message. */
    private fun retro() {
        History.ensureCount(Int.MAX_VALUE)
        val d = data
        d.allStreak = 0; d.bestAllStreak = 0; d.dry = 0; d.maxSparklings = 0; d.maxFeathers = 0; d.maxValue = 0.0
        val out = ArrayList<Pair<Achievement, Int>>()
        for (r in History.runs) applyRun(r, out)
        checkTotals(out)
        d.retroDone = true
        d.sparklingRecheck = true
        unlock(out, quiet = true)
        save()
        log.info("Checked {} old runs: {} achievements", History.runs.size, out.size)
        if (out.isNotEmpty()) {
            val pts = out.sumOf { it.first.tier.points }
            val names = out.joinToString("\n") { (a, _) -> "${a.tier.code}${a.name}" }
            val msg = Brand.msg("§e✦ ${out.size} achievement${if (out.size == 1) "" else "s"} unlocked from your past runs §7(+$pts points). §8/sp history")
                .withStyle { it.withHoverEvent(HoverEvent.ShowText(Component.literal(names))) }
            Minecraft.getInstance().player?.sendSystemMessage(msg)
            if (toastsFor(Tier.COMMON)) AchievementToasts.showSummary(out.size, pts)
        }
    }

    private fun toastsFor(tier: Tier): Boolean {
        val min = Config.s.achievementToasts
        if (min == "off") return false
        val minTier = Tier.entries.firstOrNull { it.name.equals(min, ignoreCase = true) } ?: Tier.COMMON
        return tier.ordinal >= minTier.ordinal
    }

    private fun unlock(list: List<Pair<Achievement, Int>>, quiet: Boolean) {
        if (list.isEmpty()) return
        val now = System.currentTimeMillis()
        for ((a, run) in list) data.unlocked[a.id] = Unlock(now, run)
        version++
        if (quiet) return
        val player = Minecraft.getInstance().player
        var sound = false
        for ((a, _) in list) {
            val hover = "${a.tier.code}§l${a.tier.label.uppercase()} §7· +${a.tier.points} points\n§7${a.desc}"
            val msg = Brand.msg("§e✦ Achievement unlocked: ").append(
                Component.literal("${a.tier.code}${a.name}").withStyle { it.withHoverEvent(HoverEvent.ShowText(Component.literal(hover))) })
            player?.sendSystemMessage(msg)
            if (toastsFor(a.tier)) AchievementToasts.show(a)
            if (a.tier.ordinal >= Tier.LEGENDARY.ordinal) sound = true
        }
        if (sound && Config.s.achievementSound)
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f))
    }
}
