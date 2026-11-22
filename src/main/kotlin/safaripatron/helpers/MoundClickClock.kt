package safaripatron.helpers

import kotlin.math.abs
import kotlin.math.floor

/** Twelve ordinary attack attempts per second, without accumulated catch-up bursts. */
class MoundClickClock {
    private var credit = 8
    fun tick(active: Boolean): Boolean {
        if (!active) { credit = 8; return false }
        credit += 12
        if (credit < 20) return false
        credit -= 20
        return true
    }
}

data class MoundClickConditions(val enabled: Boolean, val cavern: Boolean, val held: Boolean,
    val focused: Boolean, val screenOpen: Boolean, val usingItem: Boolean, val breakingBlock: Boolean,
    val targetMound: Boolean) {
    val allowed get() = enabled && cavern && held && focused && !screenOpen && !usingItem && !breakingBlock && targetMound
}

object MoundTargetRules {
    fun matches(x: Double, y: Double, z: Double, width: Double, height: Double,
                catalogSite: Boolean, dry: Boolean, occupied: Boolean): Boolean {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || !width.isFinite() || !height.isFinite() ||
            y > 65 || !dry || occupied || abs(x - (floor(x) + .5)) >= .05 || abs(z - (floor(z) + .5)) >= .05) return false
        return catalogSite || width in .35..1.10 && height in .25.. .95 && height <= width + .1
    }
}
