package safaripatron.config

import com.google.gson.GsonBuilder

/**
 * On-screen placement of a HUD element: top-left corner as a fraction of the screen (0..1) and a scale.
 * x < 0 means "centered horizontally".
 */
class PanelPos(var x: Float = 0f, var y: Float = 0f, var scale: Float = 1f) {
    fun copy() = PanelPos(x, y, scale)
    fun set(o: PanelPos) { x = o.x; y = o.y; scale = o.scale }
}

/** Settings saved to config/safari-patron/settings.json. */
/** Profit tracker data: items, not coins, so values follow the Bazaar. */
class ProfitData {
    var sessionStart = 0L
    var lastActive = 0L
    var sessionRuns = 0
    /** Summed length of the session's finished runs (for the average run time). */
    var sessionRunMs = 0L
    /** Runs whose length is known (the average divides by this, not by every run). */
    var sessionTimedRuns = 0
    /** Time actually spent in the Safari this session (pauses after 1 minute away). */
    var sessionActiveMs = 0L
    var sessionItems: MutableMap<String, Long> = LinkedHashMap()
    var totalRuns = 0
    var totalItems: MutableMap<String, Long> = LinkedHashMap()
    /** The last session that had something in it, for /sp sessionprofit after a reset. */
    var prevSessionItems: MutableMap<String, Long> = LinkedHashMap()
    var prevSessionMs = 0L
    var prevSessionRuns = 0
}

/** Fastest completion time of a biome (or everything) and who finished it. */
/** A personal best; for Everything / Everything except Macaw also the party of that run. */
class BestTime(var ms: Long = 0L, var by: String = "", var party: MutableList<PartyMember>? = null)

/** A player of a PB run and the biome of their first catch in it (null = unknown). */
class PartyMember(var name: String = "", var biome: String? = null)

class Settings {
    // Capsule
    var capsuleTrajectory = true
    /** Not shown in the menu; per-critter values will replace it in a later update. */
    var hitMargin = 0.1

    // Sparkling
    var sparklingFinder = true
    var sparklingSound = true
    var sparklingAutoShare = false
    /** Sparkling collection panel: which critters' sparkling form has been caught (by you or your party), all time. */
    var sparklingCollectionHud = true
    /** true = list only the ones still missing (shorter panel). */
    var sparklingCollectionMissingOnly = false
    /** All 37 in four columns, only in the middle area between the biomes. */
    var sparklingTotalHud = true
    var sparklingCollection: MutableSet<String> = linkedSetOf()

    // ESP
    var esp = true
    var floorDrops = true
    var beeNests = true
    var snoozleWalls = true

    // Party
    // Party chat
    var warpCheck = true
    var hotspotAnnounce = true
    var biomeAnnouncements = true

    // Show on screen (works even when the party message is off)
    var warpScreen = true
    var hotspotScreen = false
    var biomeScreen = false

    // Miria's Contest
    var contest = true
    var contestHud = true
    /** false = Basic ticket (Uncommon), true = First-Class ticket (Legendary / 5,000 points). */
    var contestFirstClass = false
    var contestStartAnnounce = true
    var contestStartChoice = "New Miria's Contest started."
    var contestStartCustom = ""
    var contestTicketAnnounce = true
    var contestTicketChoice = "Ticket earned."
    var contestTicketCustom = ""
    var contestWarn = true
    var contestWarnTitle = true
    var contestWarnSound = true
    var contestWarn5 = true
    var contestWarn3 = false
    var contestWarn1 = false
    /** Announced contest windows, so a restart doesn't send twice: key -> window. */
    var contestStartSent: MutableMap<String, Long> = HashMap()
    var contestTicketSent: MutableMap<String, Long> = HashMap()

    // General
    var updateChecker = true

    /** See-through water in the Safari (no fog/overlay, transparent surface). */
    var clearWater = true
    var clearWaterOpacity = 30

    // Profit tracker
    var profitTracker = true
    var profitHud = true
    /** Item-by-item all-time panel, shown in SkyBlock outside the Safari. */
    var totalProfitHud = true
    /** false = Instant Sell price, true = Sell Offer price. */
    var profitSellOffer = false
    var profit = ProfitData()

    // Run stats
    /** "Last Sparkling (X) was N runs, ... ago" when a sparkling is caught. */
    var sparklingInterval = true
    var lastSparklingCritter = ""
    var lastSparklingAt = 0L
    var lastSparklingRun = 0
    /** Counts every Safari run ever started (for the sparkling message). */
    var runCounter = 0
    /** "Completed in 2:31 (PB 2:05 by IGN)" after biome announcements. */
    var biomeTimes = true
    var bestTimes: MutableMap<String, BestTime> = HashMap()

    // Chat prefix color style (safari, gold, emerald, sunset, aurora)
    var prefixStyle = "safari"

    // HUD elements
    var runPanel = PanelPos(0.01f, 0.28f)
    var biomePanel = PanelPos(0.01f, 0.60f)
    var sparklingPanel = PanelPos(-1f, 0.12f)
    var warpPanel = PanelPos(-1f, 0.30f)
    var announcePanel = PanelPos(-1f, 0.40f)
    var collectionPanel = PanelPos(0.83f, 0.30f)
    var contestPanel = PanelPos(0.01f, 0.05f)
    var totalPanel = PanelPos(-1f, 0.10f)
    var profitPanel = PanelPos(0.187f, 0.032f)
    var totalProfitPanel = PanelPos(0.167f, 0.262f)
    /** Where the total profit panel shows outside the Safari: "all" or "entrance" (Critter Safari Entrance). */
    var totalProfitWhere = "entrance"
    /** Items the total profit panel shows at once: 5, 10, 15 or 20. */
    var totalProfitRows = 15
    /** Honeyhive waypoints and timers (part of Miria's Contest). */
    var honeyhives = true
    var honeyhivesHideWhenDone = true

    /** 1 = centered panels store their center x (not their left edge). */
    var hudVersion = 0
    /** false on a fresh install: panels are placed at their default spots once the screen size is known. */
    var layoutPlaced = false

    companion object {
        val DEFAULT_RUN = PanelPos(0.01f, 0.28f)
        val DEFAULT_BIOME = PanelPos(0.01f, 0.60f)
        val DEFAULT_SPARKLING = PanelPos(-1f, 0.12f)
        val DEFAULT_WARP = PanelPos(-1f, 0.30f)
        val DEFAULT_ANNOUNCE = PanelPos(-1f, 0.40f)
        val DEFAULT_COLLECTION = PanelPos(0.83f, 0.30f)
        val DEFAULT_CONTEST = PanelPos(0.01f, 0.05f)
        val DEFAULT_TOTAL = PanelPos(-1f, 0.10f)
        val DEFAULT_PROFIT = PanelPos(0.187f, 0.032f)
        val DEFAULT_TOTAL_PROFIT = PanelPos(0.167f, 0.262f)
    }
}

object Config {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    var s = Settings()
        private set

    fun load() {
        Paths.migrate()
        val raw = Paths.read(Paths.settings)
        s = runCatching { raw?.let { gson.fromJson(it, Settings::class.java) } }.getOrNull() ?: Settings()
        // Files from before 1.1.0 keep the panel spots their owner chose
        if (raw != null && !raw.contains("\"layoutPlaced\"")) s.layoutPlaced = true
        // Older files may lack new fields or have scale 0
        // Gson leaves fields missing from older files as null
        @Suppress("SENSELESS_COMPARISON")
        run {
            if (s.collectionPanel == null) s.collectionPanel = Settings.DEFAULT_COLLECTION.copy()
            if (s.contestPanel == null) s.contestPanel = Settings.DEFAULT_CONTEST.copy()
            if (s.totalPanel == null) s.totalPanel = Settings.DEFAULT_TOTAL.copy()
            if (s.profitPanel == null) s.profitPanel = Settings.DEFAULT_PROFIT.copy()
            if (s.totalProfitPanel == null) s.totalProfitPanel = Settings.DEFAULT_TOTAL_PROFIT.copy()
            if (s.profit == null) s.profit = ProfitData()
            if (s.profit.sessionItems == null) s.profit.sessionItems = LinkedHashMap()
            if (s.profit.totalItems == null) s.profit.totalItems = LinkedHashMap()
            if (s.bestTimes == null) s.bestTimes = HashMap()
            if (s.lastSparklingCritter == null) s.lastSparklingCritter = ""
            if (s.sparklingCollection == null) s.sparklingCollection = linkedSetOf()
            if (s.contestStartSent == null) s.contestStartSent = HashMap()
            if (s.contestTicketSent == null) s.contestTicketSent = HashMap()
            if (s.contestStartChoice == null) s.contestStartChoice = "New Miria's Contest started."
            if (s.contestStartCustom == null) s.contestStartCustom = ""
            if (s.contestTicketChoice == null) s.contestTicketChoice = "Ticket earned."
            if (s.contestTicketCustom == null) s.contestTicketCustom = ""
            if (s.totalProfitWhere == null) s.totalProfitWhere = "entrance"
            if (s.totalProfitRows !in listOf(5, 10, 15, 20)) s.totalProfitRows = 15
        }
        for (p in listOf(s.runPanel, s.biomePanel, s.sparklingPanel, s.warpPanel, s.announcePanel, s.collectionPanel, s.contestPanel, s.totalPanel, s.profitPanel, s.totalProfitPanel)) {
            if (p.scale !in 0.5f..2f) p.scale = 1f
        }
        // 1.0.1: the sparkling panel is now anchored by its center; a moved one goes back to default once
        if (s.hudVersion < 1) {
            if (s.sparklingPanel.x >= 0f) s.sparklingPanel.set(Settings.DEFAULT_SPARKLING)
            s.hudVersion = 1
        }
        save()
    }

    fun save() {
        Paths.write(Paths.settings, gson.toJson(s))
    }
}
