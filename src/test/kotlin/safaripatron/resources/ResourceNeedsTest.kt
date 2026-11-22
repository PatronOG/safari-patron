package safaripatron.resources

import kotlin.test.*

class ResourceNeedsTest {
    private fun emphasis(kind: DropKind?, needs: ResourceNeeds, foodOnly: Boolean = false, rewards: Boolean = true) =
        DropVisibility.emphasis(kind, needs, emptySet(), true, true, rewards, foodOnly)
    @Test fun acquiringOneGemColorHidesThatColorOnly() {
        val needs = ResourceNeeds(); needs.inventory(mapOf(DropKind.PURPLE to 1))
        assertEquals(DropEmphasis.HIDDEN, emphasis(DropKind.PURPLE, needs))
        assertEquals(DropEmphasis.NEEDED, emphasis(DropKind.LIME, needs))
        assertEquals(DropEmphasis.NEEDED, emphasis(DropKind.ORANGE, needs))
    }
    @Test fun placedColorRemainsHiddenAfterConsumption() {
        val needs = ResourceNeeds(); needs.inventory(mapOf(DropKind.PURPLE to 1)); needs.placed(DropKind.PURPLE); needs.inventory(emptyMap())
        assertTrue(needs.hasGem(DropKind.PURPLE))
    }
    @Test fun lostUnplacedGemBecomesNeededAgain() {
        val needs = ResourceNeeds(); needs.inventory(mapOf(DropKind.PURPLE to 1)); needs.inventory(emptyMap())
        assertEquals(DropEmphasis.NEEDED, emphasis(DropKind.PURPLE, needs))
    }
    @Test fun newRunClearsAcquiredAndPlacedColors() {
        val needs = ResourceNeeds(); needs.placed(DropKind.LIME); needs.inventory(mapOf(DropKind.PURPLE to 1)); needs.reset()
        assertFalse(needs.hasGem(DropKind.LIME)); assertFalse(needs.hasGem(DropKind.PURPLE))
    }
    @Test fun unknownDropsSurviveFoodAndRewardFilters() {
        assertEquals(DropEmphasis.NORMAL, emphasis(null, ResourceNeeds(), foodOnly = true, rewards = false))
    }
    @Test fun foodOnlyKeepsAllThreeFoodsAndExcludesKnownOtherRewards() {
        for (kind in listOf(DropKind.WORM, DropKind.BERRY, DropKind.SEEDS))
            assertEquals(DropEmphasis.NEEDED, emphasis(kind, ResourceNeeds(), foodOnly = true))
        assertEquals(DropEmphasis.HIDDEN, emphasis(DropKind.REWARD, ResourceNeeds(), foodOnly = true))
        assertEquals(DropEmphasis.HIDDEN, emphasis(DropKind.COIN, ResourceNeeds(), foodOnly = true))
    }
    @Test fun carryingFoodDoesNotHideRemainingFoodOpportunities() {
        val needs = ResourceNeeds(); needs.inventory(mapOf(DropKind.SEEDS to 1))
        assertEquals(DropEmphasis.NEEDED, emphasis(DropKind.SEEDS, needs))
    }
    @Test fun completedBirdsOnlyRemoveEmphasisNotVisibility() {
        assertEquals(DropEmphasis.NORMAL, DropVisibility.emphasis(DropKind.SEEDS, ResourceNeeds(),
            setOf("Macaw", "Parakeet", "Bluebird"), true, true, true, false))
    }
    @Test fun disablingPriorityPreservesUncollectedMarkers() {
        assertEquals(DropEmphasis.NORMAL, DropVisibility.emphasis(DropKind.COIN, ResourceNeeds(), emptySet(), true, false, true, false))
    }
}
