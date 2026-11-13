package safaripatron.contest

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import safaripatron.config.Config
import safaripatron.party.Party
import safaripatron.safari.Sidebar
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.round

/**
 * Miria's Contest tracker: timer, bracket and score from the sidebar, ticket and start announcements,
 * 5/3/1 minute warnings. Ported from SafariContest by JohnCEO and JohnKuudra (CC0-1.0).
 *
 * Contests follow the local clock: they start at :15, :35 and :55 and last 20 minutes with a 30 s end gap.
 */
object Contest {
    private const val FIRST_START_MINUTE = 15
    private const val WINDOW_SECONDS = 1200
    private const val END_GAP_SECONDS = 30
    private const val STALE_GUARD_SECONDS = 10

    private val TIER_ROW = Regex("""^\s*([A-Za-z]+)\s+with\s+(\S+)""")
    private val MIRIA_HEADER = Regex("^miria['’]s contest(?=\\s|:|$)", RegexOption.IGNORE_CASE)
    private val CONTEST_WORD = Regex("\\bcontest\\b", RegexOption.IGNORE_CASE)

    val EARNED_TIERS = setOf("UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL")
    private val FIRST_CLASS_TIERS = setOf("LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL")

    val TICKET_CHOICES = listOf("Ticket earned.", "r", "other")
    val START_CHOICES = listOf("New Miria's Contest started.", "!dt contest", "other")

    private var tickCounter = 0
    private var windowKey = -1L
    private var secondsIntoWindow = 0
    private var complete = false
    private var completedWindow = -1L
    private var points = -1
    private var tier = ""
    private var selectedFirstClass = false
    private var previousWindow: Long? = null
    private var scope: String? = null
    private val warnedMinutes = linkedSetOf<Int>()

    /** Progress belongs to one player; it's kept when switching back within the same contest. */
    private data class Progress(val window: Long, val complete: Boolean, val completedWindow: Long,
        val points: Int, val tier: String, val warnings: Set<Int>)
    private val retained = HashMap<String, Progress>()

    private val s get() = Config.s

    fun selectScope(owner: String?) {
        if (scope == owner) return
        val current = absoluteWindow(LocalDateTime.now())
        scope?.takeIf { windowKey == current }?.let {
            retained[it] = Progress(windowKey, complete, completedWindow, points, tier, warnedMinutes.toSet())
        }
        retained.entries.removeIf { it.value.window != current }
        scope = owner
        complete = false; completedWindow = -1L; windowKey = -1L; points = -1; tier = ""; warnedMinutes.clear()
        owner?.let(retained::get)?.let { saved ->
            windowKey = saved.window; complete = saved.complete; completedWindow = saved.completedWindow
            points = saved.points; tier = saved.tier; warnedMinutes.addAll(saved.warnings)
        }
    }

    fun tick(client: Minecraft) {
        val player = client.player ?: return
        if (client.level == null) return
        if (++tickCounter < 10) return
        tickCounter = 0

        selectScope(player.uuid.toString())
        rollWindow()
        if (selectedFirstClass != s.contestFirstClass) {
            selectedFirstClass = s.contestFirstClass
            complete = false; completedWindow = -1L; warnedMinutes.clear()
        }
        if (!s.contest) { previousWindow = windowKey; return }

        val skyblock = inSkyblock(client)
        val key = player.uuid.toString()

        // Start message: only when a new window is seen within its first 10 seconds (no catch-up)
        val before = previousWindow
        previousWindow = windowKey
        if (before != null && before != windowKey && secondsIntoWindow in 0..9 && skyblock &&
            s.contestStartAnnounce && s.contestStartSent[key] != windowKey) {
            Party.chat(startMessage())
            s.contestStartSent[key] = windowKey
            prune(s.contestStartSent); Config.save()
        }

        if (skyblock) {
            readSidebar(client)
            announceTicket(key)
            maybeWarn(client)
        }
    }

    private fun rollWindow() {
        val time = LocalDateTime.now()
        val shifted = Math.floorMod(time.toLocalTime().toSecondOfDay() - FIRST_START_MINUTE * 60, 86400)
        secondsIntoWindow = shifted % WINDOW_SECONDS
        val key = absoluteWindow(time)
        if (key == windowKey) return
        windowKey = key
        complete = false; warnedMinutes.clear(); points = -1; tier = ""
    }

    fun absoluteWindow(time: LocalDateTime): Long =
        Math.floorDiv(time.toEpochSecond(ZoneOffset.UTC) - FIRST_START_MINUTE * 60, WINDOW_SECONDS.toLong())

    fun secondsToEnd() = max(0, WINDOW_SECONDS - END_GAP_SECONDS - secondsIntoWindow)
    fun secondsToNextStart() = WINDOW_SECONDS - secondsIntoWindow
    fun betweenContests() = secondsToEnd() == 0
    fun currentTier() = tier
    fun currentPoints() = points
    fun isComplete() = selectedFirstClass == s.contestFirstClass && complete &&
        completedWindow == absoluteWindow(LocalDateTime.now())

    fun format(seconds: Int) = String.format("%d:%02d", seconds / 60, seconds % 60)

    fun earned(tier: String, firstClass: Boolean, points: Int) =
        if (firstClass) tier.uppercase() in FIRST_CLASS_TIERS || points >= 5000 else tier.uppercase() in EARNED_TIERS

    fun inSkyblock(client: Minecraft) = Sidebar.title(client).uppercase().contains("SKYBLOCK")

    /** The two rows right under "Miria's Contest" in the sidebar. */
    private fun miriaLines(lines: List<String>): List<String> {
        val header = lines.indexOfFirst(MIRIA_HEADER::containsMatchIn)
        return if (header < 0) emptyList() else lines.drop(header + 1).take(2).takeWhile { !CONTEST_WORD.containsMatchIn(it) }
    }

    private fun readSidebar(client: Minecraft) {
        val match = miriaLines(Sidebar.lines(client)).firstNotNullOfOrNull { line ->
            TIER_ROW.find(line)?.takeIf { it.groupValues[1].uppercase().let { t -> t == "COMMON" || t in EARNED_TIERS } }
        } ?: return
        val observedPoints = parsePoints(match.groupValues[2])
        // Points only go up within a contest; reconnect placeholders must not replace them
        if (observedPoints < 0 || observedPoints < points) return
        tier = match.groupValues[1]; points = observedPoints
        if (!complete && secondsIntoWindow >= STALE_GUARD_SECONDS && earned(tier, selectedFirstClass, points)) {
            complete = true
            completedWindow = windowKey
        }
    }

    private fun announceTicket(player: String) {
        if (!s.contestTicketAnnounce || !complete || completedWindow != windowKey || betweenContests()) return
        if (secondsIntoWindow < STALE_GUARD_SECONDS) return
        val key = "$player.${if (selectedFirstClass) 1 else 0}"
        if (s.contestTicketSent[key] == windowKey) return
        // Ticket messages wait for a confirmed party
        if (!Party.known || !Party.inParty) return
        Party.chat(ticketMessage())
        s.contestTicketSent[key] = windowKey
        prune(s.contestTicketSent); Config.save()
    }

    private fun prune(map: MutableMap<String, Long>) {
        val window = absoluteWindow(LocalDateTime.now())
        map.entries.removeIf { it.value < window - 2 }
    }

    private fun maybeWarn(client: Minecraft) {
        if (complete || !s.contestWarn || betweenContests()) return
        val minutes = listOfNotNull(5.takeIf { s.contestWarn5 }, 3.takeIf { s.contestWarn3 }, 1.takeIf { s.contestWarn1 })
        val remaining = secondsToEnd()
        val due = minutes.filter { remaining <= it * 60 && it !in warnedMinutes }
        if (due.isEmpty()) return
        warnedMinutes.addAll(due)
        if (s.contestWarnTitle) {
            val hud = client.gui.hud
            hud.setTimes(5, 60, 15)
            hud.setTitle(Component.literal("§c§lINCOMPLETE!"))
            hud.setSubtitle(Component.literal(
                "§e${format(remaining)} left to reach ${if (selectedFirstClass) "Legendary" else "Uncommon"}"))
        }
        if (s.contestWarnSound) client.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 1f, 1f))
    }

    fun ticketMessage() = resolve(s.contestTicketChoice, s.contestTicketCustom, TICKET_CHOICES, "Ticket earned.")
    fun startMessage() = resolve(s.contestStartChoice, s.contestStartCustom, START_CHOICES, "New Miria's Contest started.")

    /** "other" uses the custom text; blank custom text falls back to the default. */
    private fun resolve(choice: String, custom: String, choices: List<String>, default: String): String {
        val raw = if (choice == "other") custom else choice.takeIf { it in choices } ?: default
        return raw.filter { !it.isISOControl() && it != '§' }.trim().take(240).ifBlank { default }
    }

    private fun parsePoints(token: String): Int {
        var text = token.trim().lowercase().replace(",", "")
        val scale = when {
            text.endsWith("k") -> { text = text.dropLast(1); 1000.0 }
            text.endsWith("m") -> { text = text.dropLast(1); 1_000_000.0 }
            else -> 1.0
        }
        return text.toDoubleOrNull()?.let { round(it * scale).toInt() } ?: -1
    }

    fun reset() {
        selectScope(null)
        previousWindow = null
    }
}
