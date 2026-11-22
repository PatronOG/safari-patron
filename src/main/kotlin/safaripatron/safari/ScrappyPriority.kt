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
        food in available -> Emphasis.AVAILABLE
        else -> Emphasis.NORMAL
    }
}

/** Run-local evidence survives entity tracking changes; deductions never become direct evidence. */
internal class ScrappyFoodMemory {
    enum class Source { HUNTER, SKIN, REMAINING }
    data class Choice(val food: ScrappyFood, val source: Source)
    data class Observation(val key: String, val position: ScrappyGuideMatcher.Point,
                           val hunterFood: ScrappyFood? = null, val skinFood: ScrappyFood? = null,
                           val conflictingHunter: Boolean = false)
    private class Spawn(var position: ScrappyGuideMatcher.Point, var learned: Choice? = null, var conflict: Boolean = false)
    private val spawns = LinkedHashMap<Int, Spawn>()
    private val entities = HashMap<String, Int>()
    private var nextId = 0

    fun update(observations: List<Observation>): Map<String, Choice> {
        val current = LinkedHashMap<String, Int>()
        val used = HashSet<Int>()
        for (observation in observations.distinctBy { it.key }) {
            val position = observation.position
            if (!position.x.isFinite() || !position.y.isFinite() || !position.z.isFinite()) {
                entities.remove(observation.key)?.let { id ->
                    spawns.remove(id)
                    entities.entries.removeIf { it.value == id }
                }
                continue
            }
            val previous = entities[observation.key]?.takeIf { it !in used && it in spawns }
            val id = previous ?: spawns.entries.firstOrNull {
                it.key !in used && it.value.position.distanceSquared(position) <= 0.25
            }?.key ?: (nextId++).also { spawns[it] = Spawn(position) }
            val spawn = spawns.getValue(id)
            if (spawn.position.distanceSquared(position) > 0.25) {
                spawn.learned = null
                spawn.conflict = false
            }
            spawn.position = position
            when {
                observation.hunterFood != null -> {
                    spawn.learned = Choice(observation.hunterFood, Source.HUNTER)
                    spawn.conflict = false
                }
                observation.conflictingHunter -> { spawn.learned = null; spawn.conflict = true }
                observation.skinFood != null && !spawn.conflict && spawn.learned?.source != Source.HUNTER -> {
                    spawn.learned = Choice(observation.skinFood, Source.SKIN)
                    spawn.conflict = false
                }
            }
            entities[observation.key] = id
            current[observation.key] = id
            used.add(id)
        }

        val choices = spawns.mapNotNull { (id, spawn) -> spawn.learned?.let { id to it } }.toMap().toMutableMap()
        if (spawns.size == 3 && choices.size == 2 && choices.values.map { it.food }.toSet().size == 2) {
            val unknown = spawns.entries.single { it.key !in choices }
            if (!unknown.value.conflict) {
                val remaining = ScrappyFood.entries.single { food -> choices.values.none { it.food == food } }
                choices[unknown.key] = Choice(remaining, Source.REMAINING)
            }
        }
        return current.mapNotNull { (key, id) -> choices[id]?.let { key to it } }.toMap()
    }

    fun clear() { spawns.clear(); entities.clear(); nextId = 0 }
}
