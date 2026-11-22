package safaripatron.resources

/** Possession and confirmed placements are independent from predicted floor contents. */
class ResourceNeeds {
    private val placed = HashSet<DropKind>()
    private var held: Map<DropKind, Int> = emptyMap()
    fun reset() { placed.clear(); held = emptyMap() }
    fun inventory(counts: Map<DropKind, Int>) { held = counts.filterValues { it > 0 } }
    fun placed(kind: DropKind) { if (kind.gem) placed.add(kind) }
    fun hasGem(kind: DropKind) = kind.gem && (kind in placed || (held[kind] ?: 0) > 0)
    fun needed(kind: DropKind, caught: Set<String>): Boolean = when {
        kind.gem -> !hasGem(kind) && "Gemzie" !in caught
        kind in setOf(DropKind.WORM, DropKind.BERRY, DropKind.SEEDS) -> !caught.containsAll(listOf("Bluebird", "Parakeet", "Macaw"))
        kind == DropKind.INCENSE -> (held[kind] ?: 0) < 4 && "Doomspiral" !in caught
        kind == DropKind.COIN -> (held[kind] ?: 0) == 0 && "Gimmiegold" !in caught
        kind == DropKind.ICEBREAKER -> (held[kind] ?: 0) == 0 && "Wumpa" !in caught
        else -> false
    }
}

enum class DropEmphasis { HIDDEN, NORMAL, NEEDED }
object DropVisibility {
    fun emphasis(kind: DropKind?, needs: ResourceNeeds, caught: Set<String>, hideGems: Boolean,
                 prioritize: Boolean, rewards: Boolean, foodOnly: Boolean): DropEmphasis {
        if (kind == null) return DropEmphasis.NORMAL
        if (hideGems && needs.hasGem(kind) || !rewards && kind == DropKind.REWARD ||
            foodOnly && kind !in setOf(DropKind.WORM, DropKind.BERRY, DropKind.SEEDS)) return DropEmphasis.HIDDEN
        return if (prioritize && needs.needed(kind, caught)) DropEmphasis.NEEDED else DropEmphasis.NORMAL
    }
}
