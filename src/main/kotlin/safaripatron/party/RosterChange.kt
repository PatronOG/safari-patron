package safaripatron.party

/** Chat lines that mean the party changed (someone joined, left, was kicked, the party was disbanded...). */
object RosterChange {
    private val memberChange = Regex("^(?:\\[[^]\\r\\n]+]\\s*)?[A-Za-z0-9_]{1,16} (?:joined the party|has left the party|has been removed from the party|has disbanded the party)[.!]?$", RegexOption.IGNORE_CASE)
    fun matches(text: String): Boolean {
        if (':' in text || text.startsWith("Party >") || text.startsWith("Guild >")) return false
        return memberChange.matches(text) || listOf("You left the party", "You have been kicked from the party",
            "The party was disbanded", "You have joined ", "You are not currently in a party", "You are not in a party",
            "The party was transferred").any { text.startsWith(it, true) }
    }
}
