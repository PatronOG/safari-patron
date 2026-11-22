package safaripatron.resources

/** When a still-needed resource turns from purple back to an ordinary green drop. */
data class NeedRules(
    /** "have" = once you hold or place that color; "always" = only when Gemzie is caught. */
    val gems: String = "have",
    /** "always" = bird food stays purple; "allcaught" = until Bluebird, Parakeet and Macaw are caught. */
    val food: String = "always",
    /** "four" = at 4 Soothing Incense; "always" = only when Doomspiral is caught. */
    val incense: String = "four",
    /** "one" = once you hold a Shining Coin or Gimmiegold is caught; "always" = always purple. */
    val coin: String = "one",
    /** "always" = always purple; "one" = once you hold an Icebreaker or Wumpa is caught. */
    val icebreaker: String = "always",
)

/** Possession and confirmed placements are independent from predicted floor contents. */
class ResourceNeeds {
    private val placed = HashSet<DropKind>()
    private var held: Map<DropKind, Int> = emptyMap()
    fun reset() { placed.clear(); held = emptyMap() }
    fun inventory(counts: Map<DropKind, Int>) { held = counts.filterValues { it > 0 } }
    fun placed(kind: DropKind) { if (kind.gem) placed.add(kind) }
    fun hasGem(kind: DropKind) = kind.gem && (kind in placed || (held[kind] ?: 0) > 0)
    private fun count(kind: DropKind) = held[kind] ?: 0
    fun needed(kind: DropKind, caught: Set<String>, rules: NeedRules = NeedRules()): Boolean = when {
        kind.gem -> "Gemzie" !in caught && (rules.gems == "always" || !hasGem(kind))
        kind in setOf(DropKind.WORM, DropKind.BERRY, DropKind.SEEDS) ->
            rules.food == "always" || !caught.containsAll(listOf("Bluebird", "Parakeet", "Macaw"))
        kind == DropKind.INCENSE -> "Doomspiral" !in caught && (rules.incense == "always" || count(kind) < 4)
        kind == DropKind.COIN -> rules.coin == "always" || count(kind) == 0 && "Gimmiegold" !in caught
        kind == DropKind.ICEBREAKER -> rules.icebreaker == "always" || count(kind) == 0 && "Wumpa" !in caught
        else -> false
    }
}

/** NEEDED = purple, NORMAL = green, UNKNOWN = contents not worked out yet (red, no hologram). */
enum class DropEmphasis { HIDDEN, NORMAL, NEEDED, UNKNOWN }
object DropVisibility {
    fun emphasis(kind: DropKind?, needs: ResourceNeeds, caught: Set<String>, prioritize: Boolean,
                 rewards: Boolean, foodOnly: Boolean, rules: NeedRules = NeedRules()): DropEmphasis {
        if (kind == null) return DropEmphasis.UNKNOWN
        if (!rewards && kind == DropKind.REWARD ||
            foodOnly && kind !in setOf(DropKind.WORM, DropKind.BERRY, DropKind.SEEDS)) return DropEmphasis.HIDDEN
        return if (prioritize && needs.needed(kind, caught, rules)) DropEmphasis.NEEDED else DropEmphasis.NORMAL
    }
}
