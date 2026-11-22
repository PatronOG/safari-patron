// GemPlacementState.kt
// Module: Cavern gem selection and interaction limits
// Build: Kotlin 2.4.20 / JVM 25

package safaripatron.safari

internal enum class CavernGem(val itemId: String, val label: String) {
    LIME("LIME_GEM", "Lime Gem"),
    PURPLE("PURPLE_GEM", "Purple Gem"),
    ORANGE("ORANGE_GEM", "Orange Gem"),
}

internal enum class GemMode {
    SELECT, AUTO;
    companion object { fun fromSetting(value: String?) = if (value == "auto") AUTO else SELECT }
}

/** One interaction per continuous look, with a cooldown shared by all three podiums. */
internal class GemPlacementState {
    sealed interface Action {
        data class Select(val slot: Int) : Action
        data class Place(val slot: Int) : Action
    }
    private var lookingAt: CavernGem? = null
    private var aimSince: Long? = null
    private var preparedSlot: Int? = null
    private var readyAt = 0L
    private var policy: Pair<GemMode, Boolean>? = null
    private var attempted = false
    private var nextAttemptTick = 0L
    private val placed = mutableSetOf<CavernGem>()

    fun lookAt(gem: CavernGem?) {
        if (gem != lookingAt) {
            lookingAt = gem
            aimSince = null
            attempted = false
            preparedSlot = null
        }
    }

    fun nextAction(hotbarIds: List<String?>, selectedSlot: Int, tick: Long,
                   mode: GemMode = GemMode.SELECT, delayedPlacement: Boolean = true): Action? {
        val currentPolicy = mode to delayedPlacement
        if (currentPolicy != policy) {
            aimSince = null
            preparedSlot = null
            attempted = false
            policy = currentPolicy
        }
        val gem = lookingAt ?: return null
        if (mode == GemMode.AUTO && aimSince == null) aimSince = tick
        if (attempted || gem in placed || (mode == GemMode.AUTO && tick < nextAttemptTick)) return null

        val slot = if (selectedSlot in 0..8 && hotbarIds.getOrNull(selectedSlot) == gem.itemId) selectedSlot
        else (0..8).firstOrNull { hotbarIds.getOrNull(it) == gem.itemId }
        if (slot == null) { preparedSlot = null; return null }

        if (mode == GemMode.SELECT) {
            attempted = true
            preparedSlot = null
            return if (selectedSlot != slot) Action.Select(slot) else null
        }

        if (delayedPlacement && tick - requireNotNull(aimSince) < 6L) return null
        if (preparedSlot != slot || selectedSlot != slot) {
            preparedSlot = slot
            readyAt = tick + if (delayedPlacement) 4L else 0L
            if (selectedSlot != slot) return Action.Select(slot)
        }
        if (tick < readyAt) return null

        attempted = true
        nextAttemptTick = tick + 10
        return Action.Place(slot)
    }

    fun placed(gem: CavernGem) {
        placed.add(gem)
    }

    fun reset() {
        lookingAt = null
        aimSince = null
        attempted = false
        preparedSlot = null
        readyAt = 0L
        policy = null
        nextAttemptTick = 0L
        placed.clear()
    }
}
