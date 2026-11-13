package safaripatron.sparkling

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.slf4j.LoggerFactory
import safaripatron.config.Config
import safaripatron.esp.CritterId
import safaripatron.safari.ChatParser

/**
 * Reads the in-game "Sparkling Critterdex" menu while it is open and syncs the sparklings list with it.
 *
 * Every critter is listed by name, caught or not. Not-caught ones use Hypixel's black "?" head
 * (texture in [UNKNOWN_TEXTURES]); anything else (a critter head, or a head without a texture) is caught.
 */
object Critterdex {
    private const val TITLE = "Sparkling Critterdex"
    private val log = LoggerFactory.getLogger("safari-patron/critterdex")

    /** Texture hash of the black "?" head used for sparklings that are not caught yet. */
    private val UNKNOWN_TEXTURES = setOf("f769bbb9fb2316808131e6c2d02ce14e3aab674deb4b558bb265c3141d199b08")

    private var tick = 0
    private var lastLogged = ""

    class Entry(val critter: String, val texture: String?)
    /** Last page that was read, for /sp critterdex. */
    var lastScan: List<Entry> = emptyList()
        private set

    fun tick(client: Minecraft) {
        val screen = client.gui.screen() as? AbstractContainerScreen<*> ?: return
        if (!screen.title.string.contains(TITLE, ignoreCase = true)) return
        if (++tick % 10 != 0) return // items arrive a moment after the menu opens

        val slots = screen.menu.slots
        val containerSlots = slots.take((slots.size - 36).coerceAtLeast(0)) // skip the player inventory
        val entries = containerSlots.mapNotNull { slot ->
            val stack = slot.item
            if (stack.isEmpty) return@mapNotNull null
            val critter = ChatParser.findCritter(ChatParser.clean(stack.hoverName.string)) ?: return@mapNotNull null
            Entry(critter, CritterId.texture(stack))
        }.distinctBy { it.critter }
        if (entries.isEmpty()) return
        lastScan = entries

        // Write each new page to the log once (helps when Hypixel changes the heads)
        val dump = entries.joinToString(", ") { "${it.critter}=${it.texture}" }
        if (dump != lastLogged) { lastLogged = dump; log.info("Critterdex page: {}", dump) }

        val missing = entries.filter { it.texture in UNKNOWN_TEXTURES }.map { it.critter }
        val caught = entries.filter { it.texture !in UNKNOWN_TEXTURES }.map { it.critter }

        val owned = Config.s.sparklingCollection
        var changed = owned.addAll(caught)
        if (owned.removeAll(missing.toSet())) changed = true
        if (changed) Config.save()
    }
}
