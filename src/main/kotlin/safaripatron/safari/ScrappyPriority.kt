// ScrappyPriority.kt
// Module: Inventory-aware Scrappy emphasis and run-local food memory
// Build: Kotlin 2.4.20 / JVM 25

package safaripatron.safari

internal object ScrappyPriority {
    enum class Emphasis { NORMAL, AVAILABLE, HIDDEN, UNKNOWN }

    fun evaluate(enabled: Boolean, caught: Boolean, sparkling: Boolean, food: ScrappyFood?,
                 available: Set<ScrappyFood>): Emphasis = when {
        !enabled || sparkling -> Emphasis.NORMAL
        caught -> Emphasis.HIDDEN
        food == null -> Emphasis.UNKNOWN
        available.isEmpty() -> Emphasis.NORMAL
        food in available -> Emphasis.AVAILABLE
        else -> Emphasis.HIDDEN
    }
}

/** Keeps verified associations while a nearby Hunter tag is not loaded; never infers an unseen Hunter. */
internal class ScrappyFoodMemory {
    private data class Known(val position: ScrappyGuideMatcher.Point, val food: ScrappyFood)
    private val known = HashMap<String, Known>()

    fun resolve(key: String, position: ScrappyGuideMatcher.Point, observed: ScrappyFood?, nearbyGuide: Boolean): ScrappyFood? {
        if (!position.x.isFinite() || !position.y.isFinite() || !position.z.isFinite()) {
            known.remove(key)
            return null
        }
        if (observed != null) {
            known[key] = Known(position, observed)
            return observed
        }
        val previous = known[key] ?: return null
        if (nearbyGuide || previous.position.distanceSquared(position) > 0.25) {
            known.remove(key)
            return null
        }
        return previous.food
    }

    fun clear() = known.clear()
}
