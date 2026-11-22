// ScrappyFood.kt
// Module: Hunter-based Scrappy food identification and spatial association
// Build: Kotlin 2.4.20 / JVM 25

package safaripatron.safari

import kotlin.math.abs

internal enum class ScrappyFood(val hunter: String, val label: String, val itemId: String) {
    FISH("Hunter Luigi", "Flavor-packed Fish", "FLAVOR_PACKED_FISH"),
    LILY_PAD("Hunter Jasper", "Lush Lily Pad", "LUSH_LILY_PAD"),
    WHEAT("Hunter Reeves", "Wholesale Wheat", "WHOLESALE_WHEAT");

    companion object {
        fun fromHunterName(raw: String?): ScrappyFood? {
            val name = raw?.let(ChatParser::clean)?.removePrefix("[NPC] ")?.trim() ?: return null
            return entries.firstOrNull { it.hunter.equals(name, ignoreCase = true) }
        }
    }
}

/** A Hunter must be the nearest guide to a Scrappy and must not belong to a closer Scrappy. */
internal object ScrappyGuideMatcher {
    data class Point(val x: Double, val y: Double, val z: Double) {
        fun distanceSquared(other: Point): Double {
            val dx = x - other.x; val dy = y - other.y; val dz = z - other.z
            return dx * dx + dy * dy + dz * dz
        }
    }

    data class Subject(val id: Int, val pos: Point)
    data class Guide(val pos: Point, val food: ScrappyFood)

    private const val MAX_DISTANCE_SQUARED = 16.0
    private const val TIE_EPSILON = 0.0001

    fun match(subjects: List<Subject>, guides: List<Guide>): Map<Int, ScrappyFood> {
        val result = LinkedHashMap<Int, ScrappyFood>()
        for (subject in subjects) {
            val candidates = guides.map { it to subject.pos.distanceSquared(it.pos) }
                .filter { it.second <= MAX_DISTANCE_SQUARED }
            val nearest = candidates.minByOrNull { it.second } ?: continue
            if (candidates.any { it.first.food != nearest.first.food && abs(it.second - nearest.second) <= TIE_EPSILON }) continue
            if (subjects.any { it.id != subject.id && it.pos.distanceSquared(nearest.first.pos) <= nearest.second + TIE_EPSILON }) continue
            result[subject.id] = nearest.first.food
        }
        return result
    }
}
