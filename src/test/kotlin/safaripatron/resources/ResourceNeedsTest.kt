package safaripatron.resources

import kotlin.test.*

class ResourceNeedsTest {
    private fun emphasis(kind: DropKind?, needs: ResourceNeeds, foodOnly: Boolean = false, rewards: Boolean = true,
                         caught: Set<String> = emptySet(), rules: NeedRules = NeedRules()) =
        DropVisibility.emphasis(kind, needs, caught, true, rewards, foodOnly, rules)
    @Test fun acquiringOneGemColorTurnsThatColorGreenOnly() {
        val needs = ResourceNeeds(); needs.inventory(mapOf(DropKind.PURPLE to 1))
        assertEquals(DropEmphasis.NORMAL, emphasis(DropKind.PURPLE, needs))
        assertEquals(DropEmphasis.NEEDED, emphasis(DropKind.LIME, needs))
        assertEquals(DropEmphasis.NEEDED, emphasis(DropKind.ORANGE, needs))
    }
    @Test fun gemsCanStayPurpleUntilGemzie() {
        val needs = ResourceNeeds(); needs.inventory(mapOf(DropKind.PURPLE to 1))
        val rules = NeedRules(gems = "always")
        assertEquals(DropEmphasis.NEEDED, emphasis(DropKind.PURPLE, needs, rules = rules))
        assertEquals(DropEmphasis.NORMAL, emphasis(DropKind.PURPLE, needs, caught = setOf("Gemzie"), rules = rules))
    }
    @Test fun placedColorStaysAcquiredAfterConsumption() {
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
        assertEquals(DropEmphasis.UNKNOWN, emphasis(null, ResourceNeeds(), foodOnly = true, rewards = false))
    }
    @Test fun foodOnlyKeepsAllThreeFoodsAndExcludesKnownOtherRewards() {
        for (kind in listOf(DropKind.WORM, DropKind.BERRY, DropKind.SEEDS))
            assertEquals(DropEmphasis.NEEDED, emphasis(kind, ResourceNeeds(), foodOnly = true))
        assertEquals(DropEmphasis.HIDDEN, emphasis(DropKind.REWARD, ResourceNeeds(), foodOnly = true))
        assertEquals(DropEmphasis.HIDDEN, emphasis(DropKind.COIN, ResourceNeeds(), foodOnly = true))
    }
    @Test fun birdFoodStaysPurpleByDefault() {
        val birds = setOf("Macaw", "Parakeet", "Bluebird")
        assertEquals(DropEmphasis.NEEDED, emphasis(DropKind.SEEDS, ResourceNeeds(), caught = birds))
        assertEquals(DropEmphasis.NORMAL, emphasis(DropKind.SEEDS, ResourceNeeds(), caught = birds, rules = NeedRules(food = "allcaught")))
    }
    @Test fun incenseTurnsGreenAtFourOrDoomspiral() {
        val needs = ResourceNeeds(); needs.inventory(mapOf(DropKind.INCENSE to 3))
        assertEquals(DropEmphasis.NEEDED, emphasis(DropKind.INCENSE, needs))
        needs.inventory(mapOf(DropKind.INCENSE to 4))
        assertEquals(DropEmphasis.NORMAL, emphasis(DropKind.INCENSE, needs))
        assertEquals(DropEmphasis.NORMAL, emphasis(DropKind.INCENSE, ResourceNeeds(), caught = setOf("Doomspiral")))
    }
    @Test fun coinAndIcebreakerRules() {
        val needs = ResourceNeeds(); needs.inventory(mapOf(DropKind.COIN to 1, DropKind.ICEBREAKER to 1))
        assertEquals(DropEmphasis.NORMAL, emphasis(DropKind.COIN, needs))
        assertEquals(DropEmphasis.NEEDED, emphasis(DropKind.COIN, needs, rules = NeedRules(coin = "always")))
        assertEquals(DropEmphasis.NEEDED, emphasis(DropKind.ICEBREAKER, needs))
        assertEquals(DropEmphasis.NORMAL, emphasis(DropKind.ICEBREAKER, needs, rules = NeedRules(icebreaker = "one")))
    }
    @Test fun disablingPriorityKeepsEveryDropGreen() {
        assertEquals(DropEmphasis.NORMAL, DropVisibility.emphasis(DropKind.COIN, ResourceNeeds(), emptySet(), false, true, false))
    }
}
