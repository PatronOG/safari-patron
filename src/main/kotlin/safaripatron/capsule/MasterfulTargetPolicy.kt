// MasterfulTargetPolicy.kt
// Module: Optional five-species Masterful target allowlist
// Build: Kotlin 2.4.20 / JVM 25

package safaripatron.capsule

internal object MasterfulTargetPolicy {
    private val allowed = setOf("Doomspiral", "Wumpa", "Macaw", "Gemzie", "Scrappy")

    fun permits(enabled: Boolean, masterful: Boolean, predictedTarget: String?): Boolean =
        !enabled || !masterful || predictedTarget in allowed
}
