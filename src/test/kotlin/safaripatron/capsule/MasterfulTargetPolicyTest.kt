// MasterfulTargetPolicyTest.kt
// Module: The five requested Masterful species and opt-in behavior
// Build: Kotlin 2.4.20 / JVM 25 / JUnit 4

package safaripatron.capsule

import safaripatron.data.Critters
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MasterfulTargetPolicyTest {
    @Test fun allFiveRequestedTargetsPermitAThrow() {
        for (name in listOf("Doomspiral", "Wumpa", "Macaw", "Gemzie", "Scrappy"))
            assertTrue(MasterfulTargetPolicy.permits(true, true, name), name)
    }

    @Test fun everyOtherSafariSpeciesIsBlocked() {
        val expected = setOf("Doomspiral", "Wumpa", "Macaw", "Gemzie", "Scrappy")
        for (critter in Critters.all.filterNot { it.name in expected })
            assertFalse(MasterfulTargetPolicy.permits(true, true, critter.name), critter.name)
    }

    @Test fun theFilterNeedsAPredictedPermittedTarget() {
        assertFalse(MasterfulTargetPolicy.permits(true, true, null))
        assertFalse(MasterfulTargetPolicy.permits(true, true, "Unknown"))
    }

    @Test fun ordinaryCapsulesAreNeverFiltered() {
        assertTrue(MasterfulTargetPolicy.permits(true, false, "Litterbug"))
        assertTrue(MasterfulTargetPolicy.permits(true, false, null))
    }

    @Test fun disablingTheFilterAllowsAllNormalUses() {
        assertTrue(MasterfulTargetPolicy.permits(false, true, "Litterbug"))
        assertTrue(MasterfulTargetPolicy.permits(false, true, null))
    }
}
