// GemPlacementStateTest.kt
// Module: Manual-default gem selection and optional delayed placement checks
// Build: Kotlin 2.4.20 / JVM 25 / JUnit 4

package safaripatron.safari

import safaripatron.safari.GemPlacementState.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GemPlacementStateTest {
    private val state = GemPlacementState()
    private val hotbar = listOf(null, "PURPLE_GEM", null, "LIME_GEM", "ORANGE_GEM")
    private fun auto(slot: Int, tick: Long, delay: Boolean = true) = state.nextAction(hotbar, slot, tick, GemMode.AUTO, delay)

    @Test fun defaultModeOnlySelectsAndNeverRightClicks() {
        state.lookAt(CavernGem.LIME)
        assertEquals(Action.Select(3), state.nextAction(hotbar, 0, 0))
        for (tick in 1L..100L) assertNull(state.nextAction(hotbar, 3, tick))
    }

    @Test fun allColorsSelectTheirMatchingHotbarSlot() {
        for ((gem, slot) in listOf(CavernGem.LIME to 3, CavernGem.PURPLE to 1, CavernGem.ORANGE to 4)) {
            state.reset(); state.lookAt(gem)
            assertEquals(Action.Select(slot), state.nextAction(hotbar, 0, 0))
        }
    }

    @Test fun alreadyHeldGemIsUntouchedInManualMode() {
        state.lookAt(CavernGem.LIME)
        assertNull(state.nextAction(hotbar, 3, 0))
        assertNull(state.nextAction(hotbar, 3, 50))
    }

    @Test fun manualModeDoesNotFightLaterSlotChangesDuringTheSameLook() {
        state.lookAt(CavernGem.LIME)
        state.nextAction(hotbar, 0, 0)
        assertNull(state.nextAction(hotbar, 1, 1))
        state.lookAt(null); state.lookAt(CavernGem.LIME)
        assertEquals(Action.Select(3), state.nextAction(hotbar, 1, 2))
    }

    @Test fun automaticModeWaitsBeforeSelectingAndAgainBeforePlacing() {
        state.lookAt(CavernGem.LIME)
        for (tick in 0L..5L) assertNull(auto(0, tick))
        assertEquals(Action.Select(3), auto(0, 6))
        for (tick in 7L..9L) assertNull(auto(3, tick))
        assertEquals(Action.Place(3), auto(3, 10))
        assertNull(auto(3, 100))
    }

    @Test fun automaticDelayCanBeDisabled() {
        state.lookAt(CavernGem.LIME)
        assertEquals(Action.Select(3), auto(0, 0, false))
        assertEquals(Action.Place(3), auto(3, 1, false))
    }

    @Test fun heldGemCanBePlacedImmediatelyWhenDelayIsDisabled() {
        state.lookAt(CavernGem.LIME)
        assertEquals(Action.Place(3), auto(3, 0, false))
    }

    @Test fun heldGemStillWaitsWhenDelayIsEnabled() {
        state.lookAt(CavernGem.LIME)
        for (tick in 0L..9L) assertNull(auto(3, tick))
        assertEquals(Action.Place(3), auto(3, 10))
    }

    @Test fun lookingAwayDuringTheFirstWaitCancelsSelection() {
        state.lookAt(CavernGem.LIME); auto(0, 0)
        state.lookAt(null)
        assertNull(auto(0, 6))
        state.lookAt(CavernGem.LIME)
        for (tick in 7L..12L) assertNull(auto(0, tick))
        assertEquals(Action.Select(3), auto(0, 13))
    }

    @Test fun lookingAwayAfterSelectionCancelsTheAutomaticClick() {
        state.lookAt(CavernGem.LIME); auto(0, 0); auto(0, 6)
        state.lookAt(null)
        assertNull(auto(3, 10))
        state.lookAt(CavernGem.LIME)
        for (tick in 13L..22L) assertNull(auto(3, tick))
        assertEquals(Action.Place(3), auto(3, 23))
    }

    @Test fun switchingBackToManualModeCancelsAPendingAutomaticClick() {
        state.lookAt(CavernGem.LIME); auto(0, 0); auto(0, 6)
        assertNull(state.nextAction(hotbar, 3, 12, GemMode.SELECT))
        assertNull(state.nextAction(hotbar, 3, 100, GemMode.SELECT))
    }

    @Test fun changingPodiumsCancelsThePreviousPlacement() {
        state.lookAt(CavernGem.LIME); auto(0, 0); auto(0, 6)
        state.lookAt(CavernGem.ORANGE)
        for (tick in 7L..12L) assertNull(auto(3, tick))
        assertEquals(Action.Select(4), auto(3, 13))
        for (tick in 14L..16L) assertNull(auto(4, tick))
        assertEquals(Action.Place(4), auto(4, 17))
    }

    @Test fun noMatchingGemDoesNotConsumeTheSelection() {
        state.lookAt(CavernGem.LIME)
        assertNull(state.nextAction(listOf("PURPLE_GEM", "ORANGE_GEM"), 0, 0))
        assertEquals(Action.Select(3), state.nextAction(hotbar, 0, 1))
    }

    @Test fun itemsOutsideTheHotbarCannotBeSelected() {
        state.lookAt(CavernGem.LIME)
        assertNull(state.nextAction(List(9) { null } + "LIME_GEM", 9, 0))
    }

    @Test fun removingTheGemBeforeUseCancelsTheClick() {
        state.lookAt(CavernGem.LIME); auto(0, 0); auto(0, 6)
        assertNull(state.nextAction(emptyList(), 3, 12, GemMode.AUTO))
        for (tick in 13L..16L) assertNull(auto(3, tick))
        assertEquals(Action.Place(3), auto(3, 17))
    }

    @Test fun automaticCooldownDoesNotDelayManualSlotSelection() {
        state.lookAt(CavernGem.LIME); auto(0, 0); auto(0, 6); auto(3, 10)
        state.lookAt(CavernGem.ORANGE)
        assertNull(auto(3, 13))
        assertEquals(Action.Select(4), state.nextAction(hotbar, 3, 13, GemMode.SELECT))
    }

    @Test fun manuallyChangingSlotsRestartsThePlacementWait() {
        state.lookAt(CavernGem.LIME); auto(0, 0); auto(0, 6)
        assertEquals(Action.Select(3), auto(1, 10))
        for (tick in 11L..13L) assertNull(auto(3, tick))
        assertEquals(Action.Place(3), auto(3, 14))
    }

    @Test fun disablingDelayDuringAWaitAppliesTheNewPolicy() {
        state.lookAt(CavernGem.LIME); auto(0, 0)
        assertEquals(Action.Select(3), auto(0, 2, false))
        assertEquals(Action.Place(3), auto(3, 3, false))
    }

    @Test fun successAndNewRunStateAreRespectedInBothModes() {
        state.placed(CavernGem.LIME); state.lookAt(CavernGem.LIME)
        assertNull(state.nextAction(hotbar, 0, 0)); assertNull(auto(0, 0))
        state.reset(); state.lookAt(CavernGem.LIME)
        assertEquals(Action.Select(3), state.nextAction(hotbar, 0, 0))
    }

    @Test fun unknownOrMissingConfigurationFallsBackToManualSelection() {
        assertEquals(GemMode.SELECT, GemMode.fromSetting(null))
        assertEquals(GemMode.SELECT, GemMode.fromSetting("invalid"))
        assertEquals(GemMode.AUTO, GemMode.fromSetting("auto"))
    }
}
