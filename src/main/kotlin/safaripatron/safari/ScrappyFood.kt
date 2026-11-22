// ScrappyFood.kt
// Module: Hunter-based Scrappy food identification and spatial association
// Build: Kotlin 2.4.20 / JVM 25

package safaripatron.safari

import kotlin.math.abs

internal enum class ScrappyFood(val hunter: String, val label: String, val itemId: String, val hunterTexture: String) {
    FISH("Hunter Luigi", "Flavor-packed Fish", "FLAVOR_PACKED_FISH", "4538c727edceb5e29cc53d2b5c4231802ac5d7b9899d423ace81e7366e2e7ebc"),
    LILY_PAD("Hunter Jasper", "Lush Lily Pad", "LUSH_LILY_PAD", "e4d0b57123433985923d6085dbea9ef760c2dbedd9c65ee9ba85a26148582459"),
    WHEAT("Hunter Reeves", "Wholesale Wheat", "WHOLESALE_WHEAT", "eacd215ccde2f677c7c144e2b698ff33ea06a87aaf468d05d1f0dc5ec2bdbfe8");

    companion object {
        /** NPC skin identities recorded beside the verified Hunter name tags during feeding checks. */
        fun fromHunterTexture(hash: String?): ScrappyFood? = entries.firstOrNull { it.hunterTexture == hash }

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
