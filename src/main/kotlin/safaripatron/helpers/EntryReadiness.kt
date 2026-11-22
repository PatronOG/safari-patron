package safaripatron.helpers

import java.util.UUID

data class EntryReadiness(val enabled: Boolean, val shift: Boolean, val rosterKnown: Boolean,
    val self: UUID, val members: Set<UUID>, val present: Set<UUID>, val detectionReady: Boolean,
    val locationReady: Boolean = true) {
    val missing get() = (members + self) - present
    val reason: String? get() = when {
        !enabled || shift -> null
        !locationReady -> "Waiting for server location"
        !rosterKnown -> "Loading party members"
        self !in present -> "Waiting for this server's player list"
        missing.isNotEmpty() -> "${missing.size} party member(s) missing"
        !detectionReady -> "Waiting for the loaded-entity scan"
        else -> null
    }
}

/** Readiness of observations already delivered to this client, not unseen chunks. */
class ScanStability {
    private var world: Any? = null
    private var signature: Set<Pair<Int, String?>> = emptySet()
    private var changedAt = 0L
    private var sampledAt = 0L
    private var samples = 0
    private var pending = true
    fun reset() { world = null; signature = emptySet(); samples = 0; pending = true; sampledAt = 0; changedAt = 0 }
    fun observe(level: Any, values: Set<Pair<Int, String?>>, unresolved: Boolean, now: Long) {
        if (world !== level || signature != values || pending != unresolved) { changedAt = now; samples = 0 }
        world = level; signature = values; pending = unresolved; sampledAt = now; samples++
    }
    fun ready(level: Any?, now: Long) = level != null && world === level && !pending && samples >= 2 &&
        now - changedAt >= 500 && now - sampledAt in 0..1000
}

object EntryRecognition {
    private val ticket = Regex("^(?:(?:Basic|Economy|Premium|First-Class) )?(?:Critter )?Safari Ticket$", RegexOption.IGNORE_CASE)
    fun ticket(id: String, name: String) = id.startsWith("CRITTER_SAFARI_TICKET") || ticket.matches(name)
    fun menu(title: String, serverSlot: Boolean, nameTag: Boolean) =
        title.equals("Critter Safari Entry", true) && serverSlot && nameTag
    fun manager(name: String) = name.removePrefix("[NPC] ").equals("Safari Manager", true)
    private val memberChange = Regex("^(?:\\[[^]\\r\\n]+]\\s*)?[A-Za-z0-9_]{1,16} (?:joined the party|has left the party|has been removed from the party|has disbanded the party)[.!]?$", RegexOption.IGNORE_CASE)
    fun rosterChange(text: String): Boolean {
        if (':' in text || text.startsWith("Party >") || text.startsWith("Guild >")) return false
        return memberChange.matches(text) || listOf("You left the party", "You have been kicked from the party",
            "The party was disbanded", "You have joined ", "You are not currently in a party", "You are not in a party",
            "The party was transferred").any { text.startsWith(it, true) }
    }
}
