package safaripatron.ui

import safaripatron.Brand
import safaripatron.config.Config
import safaripatron.config.Settings
import safaripatron.contest.Contest
import safaripatron.sparkling.SparklingFinder

/** Everything the menu can show, grouped by category. */
object SettingsModel {
    sealed class Item(val name: String, val desc: String)
    class Toggle(name: String, desc: String, val get: () -> Boolean, val set: (Boolean) -> Unit) : Item(name, desc)
    class Action(name: String, desc: String, val label: String, val run: () -> Unit) : Item(name, desc)
    class Prefix(val style: Brand.Style) : Item(style.id, "Chat prefix style")
    class Info(name: String, desc: String) : Item(name, desc)
    /** Dropdown. single = pick exactly one (closes on pick), otherwise several at once. */
    class Multi(name: String, desc: String, val options: List<Option>, val single: Boolean = false) : Item(name, desc)
    /** One line of free text, edited in place. */
    class Text(name: String, desc: String, val get: () -> String, val set: (String) -> Unit) : Item(name, desc)
    class Option(val label: String, val get: () -> Boolean, val set: (Boolean) -> Unit)

    class Category(
        val id: String,
        val title: String,
        val icon: String,
        val iconColor: Int,
        val summary: String,
        /** On/Off pill on the main menu card; null = no pill. */
        val enabled: (() -> Boolean)?,
        val items: () -> List<Item>,
    )

    private val s get() = Config.s

    private fun toggle(name: String, desc: String, get: () -> Boolean, set: (Boolean) -> Unit) =
        Toggle(name, desc, get) { set(it); Config.save() }

    private fun option(label: String, get: () -> Boolean, set: (Boolean) -> Unit) =
        Option(label, get) { set(it); Config.save() }

    private fun choice(name: String, desc: String, values: List<String>, labels: (String) -> String,
                       get: () -> String, set: (String) -> Unit) =
        Multi(name, desc, values.map { v -> Option(labels(v), { get() == v }) { if (it) { set(v); Config.save() } } }, single = true)

    private fun text(name: String, desc: String, get: () -> String, set: (String) -> Unit) =
        Text(name, desc, get) { set(it); Config.save() }

    private fun messageLabel(v: String) = if (v == "other") "Custom" else v

    val categories: List<Category> = listOf(
        Category("sparkling", "Sparkling", "✦", 0xFFFFB627.toInt(), "Finder, panel, sharing", { s.sparklingFinder }) {
            listOf(
                toggle("Sparkling finder", "Alert, box and waypoint", { s.sparklingFinder }) { s.sparklingFinder = it },
                toggle("Sound", "Ding when a sparkling is found", { s.sparklingSound }) { s.sparklingSound = it },
                toggle("Auto share to party", "Sends its location to party chat", { s.sparklingAutoShare }) { s.sparklingAutoShare = it },
                toggle("Last sparkling message", "Runs and time since the last one", { s.sparklingInterval }) { s.sparklingInterval = it },
                toggle("Sparklings panel", "Caught in this biome, all time", { s.sparklingCollectionHud }) { s.sparklingCollectionHud = it },
                toggle("Total sparklings panel", "All 37, in the middle area", { s.sparklingTotalHud }) { s.sparklingTotalHud = it },
                choice("Panel list", "What the sparklings panels list", listOf("all", "missing"),
                    { if (it == "missing") "Missing only" else "All" },
                    { if (s.sparklingCollectionMissingOnly) "missing" else "all" }) { s.sparklingCollectionMissingOnly = it == "missing" },
                Info("Critterdex sync", "Open the Sparkling Critterdex"),
                Action("Reset sparklings", "Forget all caught sparklings", "Reset") {
                    s.sparklingCollection.clear(); Config.save()
                },
                Action("Clear panel", "Remove tracked sparklings", "Clear") { SparklingFinder.clear() },
            )
        },
        Category("esp", "ESP", "◈", 0xFF5DCAA5.toInt(), "Critters, drops, nests", { s.esp }) {
            listOf(
                toggle("Critter ESP", "Boxes around critters", { s.esp }) { s.esp = it },
                toggle("Only current biome", "Hide ESP for other biomes", { s.espOnlyInBiome }) { s.espOnlyInBiome = it },
                toggle("Floor drops", "Mark floor drop spots", { s.floorDrops }) { s.floorDrops = it },
                toggle("Bee nests", "Honeybug nests in Forest", { s.beeNests }) { s.beeNests = it },
                toggle("Snoozle walls", "Breakable walls in Cavern", { s.snoozleWalls }) { s.snoozleWalls = it },
                toggle("Clear water", "See through Safari water", { s.clearWater }) { s.clearWater = it },
                choice("Water opacity", "With clear water on", listOf("0", "30", "60"), { "$it%" },
                    { s.clearWaterOpacity.toString() }) { s.clearWaterOpacity = it.toInt() },
            )
        },
        Category("party", "Party", "⚑", 0xFFC9A0F0.toInt(), "Warp check, hotspot",
            { s.warpCheck || s.hotspotAnnounce || s.biomeAnnouncements || s.warpScreen || s.hotspotScreen || s.biomeScreen }) {
            listOf(
                Info("Party chat", "Sent to your party"),
                toggle("Warp check", "4/4 Players in Safari.", { s.warpCheck }) { s.warpCheck = it },
                toggle("Hotspot sharing", "Hotspot: Haunted", { s.hotspotAnnounce }) { s.hotspotAnnounce = it },
                toggle("Biome announcements", "Forest Done! and more", { s.biomeAnnouncements }) { s.biomeAnnouncements = it },
                toggle("Completion times", "Time and PB after Done!", { s.biomeTimes }) { s.biomeTimes = it },
                Multi("Show on screen", "Only you see these", listOf(
                    option("Warp check", { s.warpScreen }) { s.warpScreen = it },
                    option("Hotspot", { s.hotspotScreen }) { s.hotspotScreen = it },
                    option("Biome announcements", { s.biomeScreen }) { s.biomeScreen = it },
                )),
            )
        },
        Category("capsule", "Capsule Helper", "➶", 0xFF85B7EB.toInt(), "Path and hit indicator", { s.capsuleTrajectory }) {
            listOf(
                toggle("Capsule Helper", "Path and hit indicator", { s.capsuleTrajectory }) { s.capsuleTrajectory = it },
            )
        },
        Category("contest", "Miria's Contest", "✪", 0xFFFFCC66.toInt(), "Timer, ticket, alerts", { s.contest }) {
            buildList {
                add(toggle("Contest tracker", "Timer, bracket, score and ticket", { s.contest }) { s.contest = it })
                add(toggle("Contest panel", "Time, bracket, score, ticket", { s.contestHud }) { s.contestHud = it })
                add(choice("Ticket", "Which ticket counts as done", listOf("basic", "first"),
                    { if (it == "first") "First-Class" else "Basic" },
                    { if (s.contestFirstClass) "first" else "basic" }) { s.contestFirstClass = it == "first" })
                add(toggle("Ticket message", "When your ticket is earned", { s.contestTicketAnnounce }) { s.contestTicketAnnounce = it })
                add(choice("Ticket text", "Sent to party chat", Contest.TICKET_CHOICES, ::messageLabel,
                    { s.contestTicketChoice }) { s.contestTicketChoice = it })
                if (s.contestTicketChoice == "other")
                    add(text("Custom ticket text", "Your own message", { s.contestTicketCustom }) { s.contestTicketCustom = it })
                add(toggle("Start message", "When a new contest starts", { s.contestStartAnnounce }) { s.contestStartAnnounce = it })
                add(choice("Start text", "Sent to party chat", Contest.START_CHOICES, ::messageLabel,
                    { s.contestStartChoice }) { s.contestStartChoice = it })
                if (s.contestStartChoice == "other")
                    add(text("Custom start text", "Your own message", { s.contestStartCustom }) { s.contestStartCustom = it })
                add(toggle("Incomplete warnings", "If your ticket isn't done", { s.contestWarn }) { s.contestWarn = it })
                add(Multi("Warn at", "Minutes left", listOf(
                    option("5 minutes", { s.contestWarn5 }) { s.contestWarn5 = it },
                    option("3 minutes", { s.contestWarn3 }) { s.contestWarn3 = it },
                    option("1 minute", { s.contestWarn1 }) { s.contestWarn1 = it },
                )))
                add(toggle("Warning title", "Big INCOMPLETE! text on screen", { s.contestWarnTitle }) { s.contestWarnTitle = it })
                add(toggle("Warning sound", "Bell sound", { s.contestWarnSound }) { s.contestWarnSound = it })
            }
        },
        Category("profit", "Profit", "♦", 0xFF8FD16A.toInt(), "Session and total", { s.profitTracker }) {
            listOf(
                toggle("Profit tracker", "Shards, feathers and essence", { s.profitTracker }) { s.profitTracker = it },
                toggle("Session profit panel", "Shown in the Safari", { s.profitHud }) { s.profitHud = it },
                toggle("Total profit panel", "All-time, outside the Safari", { s.totalProfitHud }) { s.totalProfitHud = it },
                choice("Price", "Bazaar price used for values", listOf("instant", "offer"),
                    { if (it == "offer") "Sell Offer" else "Instant Sell" },
                    { if (s.profitSellOffer) "offer" else "instant" }) { s.profitSellOffer = it == "offer" },
                Action("New session", "Reset your session profit.", "Reset") {
                    safaripatron.profit.Profit.newSession()
                },
                Action("Reset total", "Forget all-time profit", "Reset") { safaripatron.profit.Profit.resetTotal() },
            )
        },
        Category("chat", "Chat prefix", "✎", 0xFF8FD16A.toInt(), "Gradient styles", null) {
            Brand.Style.entries.map { Prefix(it) }
        },
        Category("panels", "Panels", "✥", 0xFFE3C35A.toInt(), "Move and resize", null) {
            listOf(
                Action("Move panels", "Opens the panel editor", "Open") { MenuNav.openHud() },
                Action("Reset all panels", "Back to default places", "Reset") {
                    s.runPanel.set(Settings.DEFAULT_RUN); s.biomePanel.set(Settings.DEFAULT_BIOME)
                    s.sparklingPanel.set(Settings.DEFAULT_SPARKLING)
                    s.warpPanel.set(Settings.DEFAULT_WARP); s.announcePanel.set(Settings.DEFAULT_ANNOUNCE)
                    s.collectionPanel.set(Settings.DEFAULT_COLLECTION); s.contestPanel.set(Settings.DEFAULT_CONTEST)
                    s.totalPanel.set(Settings.DEFAULT_TOTAL); s.profitPanel.set(Settings.DEFAULT_PROFIT); s.totalProfitPanel.set(Settings.DEFAULT_TOTAL_PROFIT)
                    Config.save()
                },
            )
        },
        Category("commands", "Commands", "☰", 0xFFCFDAC6.toInt(), "All /sp commands", null) {
            listOf(
                Info("/sp", "Open this menu"),
                Info("/sp help", "List all commands in chat"),
                Info("/sp hud", "Move the panels"),
                Info("/sp capsule", "Toggle Capsule Helper"),
                Info("/sp contest", "Toggle Miria's Contest tracker"),
                Info("/sp resetsession", "Reset your session profit"),
                Info("/sp sessionprofit", "Session profit in chat"),
                Info("/sp totalprofit", "Total Safari profit in chat"),
                Info("/sp sparkling", "Toggle sparkling finder"),
                Info("/sp esp", "Toggle critter ESP"),
                Info("/sp warp", "Toggle warp check"),
                Info("/sp hotspot", "Toggle hotspot sharing"),
                Info("/sp announce", "Toggle biome announcements"),
                Info("/sp prefix <style>", "Change the chat prefix colors"),
                Info("/sp status", "Show mod status"),
                Info("/sp newrun", "Reset the run"),
            )
        },
    )

    fun byId(id: String?) = categories.firstOrNull { it.id == id } ?: categories.first()

    /** Main menu cards (Panels is reached through its own card). */
    val cardIds = listOf("sparkling", "esp", "party", "capsule", "contest", "profit")
}
