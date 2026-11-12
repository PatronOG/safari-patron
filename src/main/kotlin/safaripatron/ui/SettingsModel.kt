package safaripatron.ui

import safaripatron.Brand
import safaripatron.config.Config
import safaripatron.config.Settings
import safaripatron.sparkling.SparklingFinder

/** Everything the menu can show, grouped by category. */
object SettingsModel {
    sealed class Item(val name: String, val desc: String)
    class Toggle(name: String, desc: String, val get: () -> Boolean, val set: (Boolean) -> Unit) : Item(name, desc)
    class Action(name: String, desc: String, val label: String, val run: () -> Unit) : Item(name, desc)
    class Prefix(val style: Brand.Style) : Item(style.id, "Chat prefix style")
    class Info(name: String, desc: String) : Item(name, desc)

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

    val categories: List<Category> = listOf(
        Category("sparkling", "Sparkling", "✦", 0xFFFFB627.toInt(), "Finder, panel, sharing", { s.sparklingFinder }) {
            listOf(
                toggle("Sparkling finder", "Alert, box and waypoint when one appears", { s.sparklingFinder }) { s.sparklingFinder = it },
                toggle("Sound", "Ding when a sparkling is found", { s.sparklingSound }) { s.sparklingSound = it },
                Action("Clear panel", "Remove all tracked sparklings now", "Clear") { SparklingFinder.clear() },
            )
        },
        Category("esp", "ESP", "◈", 0xFF5DCAA5.toInt(), "Critters, drops, nests", { s.esp }) {
            listOf(
                toggle("Critter ESP", "Boxes around critters", { s.esp }) { s.esp = it },
                toggle("Only current biome", "Hide ESP for other biomes", { s.espOnlyInBiome }) { s.espOnlyInBiome = it },
                toggle("Floor drops", "Mark floor drop spots", { s.floorDrops }) { s.floorDrops = it },
                toggle("Bee nests", "Honeybug nests in Forest", { s.beeNests }) { s.beeNests = it },
                toggle("Snoozle walls", "Breakable walls in Cavern", { s.snoozleWalls }) { s.snoozleWalls = it },
            )
        },
        Category("party", "Party", "⚑", 0xFFC9A0F0.toInt(), "Warp check, hotspot",
            { s.warpCheck || s.hotspotAnnounce || s.biomeAnnouncements }) {
            listOf(
                toggle("Warp check", "4/4 Players in Safari.", { s.warpCheck }) { s.warpCheck = it },
                toggle("Hotspot sharing", "Hotspot: Haunted", { s.hotspotAnnounce }) { s.hotspotAnnounce = it },
                toggle("Biome announcements", "Forest Done! and more", { s.biomeAnnouncements }) { s.biomeAnnouncements = it },
                toggle("Show on screen", "Also show announcements on screen", { s.screenAnnouncements }) { s.screenAnnouncements = it },
            )
        },
        Category("capsule", "Capsule Helper", "➶", 0xFF85B7EB.toInt(), "Path and hit indicator", { s.capsuleTrajectory }) {
            listOf(
                toggle("Capsule Helper", "Path and hit indicator", { s.capsuleTrajectory }) { s.capsuleTrajectory = it },
            )
        },
        Category("chat", "Chat prefix", "✎", 0xFF8FD16A.toInt(), "Gradient styles", null) {
            Brand.Style.entries.map { Prefix(it) }
        },
        Category("panels", "Panels", "✥", 0xFFE3C35A.toInt(), "Move and resize", null) {
            listOf(
                Action("Move panels", "Drag, scroll to resize, right click to reset", "Open") { MenuNav.openHud() },
                Action("Reset all panels", "Put every panel back to its default place", "Reset") {
                    s.runPanel.set(Settings.DEFAULT_RUN); s.biomePanel.set(Settings.DEFAULT_BIOME)
                    s.sparklingPanel.set(Settings.DEFAULT_SPARKLING); s.notifications.set(Settings.DEFAULT_NOTIFICATIONS)
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
    val cardIds = listOf("sparkling", "esp", "party", "capsule", "chat", "commands")
}
