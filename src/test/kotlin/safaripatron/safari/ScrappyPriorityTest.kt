// ScrappyPriorityTest.kt
// Module: Carried-food highlights, unique catches and verified association lifetime
// Build: Kotlin 2.4.20 / JVM 25 / JUnit 4

package safaripatron.safari

import safaripatron.safari.ScrappyPriority.Emphasis
import safaripatron.safari.ScrappyGuideMatcher.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScrappyPriorityTest {
    private val commonFoods = setOf(ScrappyFood.WHEAT, ScrappyFood.LILY_PAD)
    private fun style(food: ScrappyFood?, caught: Boolean = false, sparkling: Boolean = false,
                      foods: Set<ScrappyFood> = commonFoods, enabled: Boolean = true) =
        ScrappyPriority.evaluate(enabled, caught, sparkling, food, foods)

    @Test fun wheatAndLilyHighlightTwoChoicesWithoutHidingFish() {
        assertEquals(Emphasis.AVAILABLE, style(ScrappyFood.WHEAT))
        assertEquals(Emphasis.AVAILABLE, style(ScrappyFood.LILY_PAD))
        assertEquals(Emphasis.NORMAL, style(ScrappyFood.FISH))
    }

    @Test fun carryingOnlyFishHighlightsOnlyItsMatchingScrappy() {
        val fish = setOf(ScrappyFood.FISH)
        assertEquals(Emphasis.AVAILABLE, style(ScrappyFood.FISH, foods = fish))
        assertEquals(Emphasis.NORMAL, style(ScrappyFood.WHEAT, foods = fish))
        assertEquals(Emphasis.NORMAL, style(ScrappyFood.LILY_PAD, foods = fish))
    }

    @Test fun emptyInventoryDoesNotHideEveryKnownScrappy() {
        for (food in ScrappyFood.entries) assertEquals(Emphasis.NORMAL, style(food, foods = emptySet()))
    }

    @Test fun unknownFoodRemainsVisibleInsteadOfBeingGuessed() {
        assertEquals(Emphasis.UNKNOWN, style(null))
    }

    @Test fun npcSkinsIdentifyFoodWithoutAnyHunterNameTags() {
        val subjects = listOf(ScrappyGuideMatcher.Subject(1, Point(0.0, 0.0, 0.0)),
            ScrappyGuideMatcher.Subject(2, Point(20.0, 0.0, 0.0)), ScrappyGuideMatcher.Subject(3, Point(40.0, 0.0, 0.0)))
        val skins = listOf("eacd215ccde2f677c7c144e2b698ff33ea06a87aaf468d05d1f0dc5ec2bdbfe8",
            "e4d0b57123433985923d6085dbea9ef760c2dbedd9c65ee9ba85a26148582459",
            "4538c727edceb5e29cc53d2b5c4231802ac5d7b9899d423ace81e7366e2e7ebc")
        val guides = skins.mapIndexed { index, skin ->
            ScrappyGuideMatcher.Guide(Point(index * 20.0 + 1.0, 0.0, 1.0), requireNotNull(ScrappyFood.fromHunterTexture(skin)))
        }
        val matches = ScrappyGuideMatcher.match(subjects, guides)
        assertEquals(Emphasis.AVAILABLE, style(matches[1]))
        assertEquals(Emphasis.AVAILABLE, style(matches[2]))
        assertEquals(Emphasis.NORMAL, style(matches[3]))
    }

    @Test fun unrecognizedNpcSkinsCannotInventFoodAssociations() {
        assertNull(ScrappyFood.fromHunterTexture(null))
        assertNull(ScrappyFood.fromHunterTexture("different-skin"))
    }

    @Test fun uniqueCatchStopsHighlightingOtherNormalScrappies() {
        for (food in ScrappyFood.entries) assertEquals(Emphasis.HIDDEN, style(food, caught = true))
        assertEquals(Emphasis.HIDDEN, style(null, caught = true))
    }

    @Test fun sparklingIsNotHiddenByFoodAvailabilityOrAUniqueCatch() {
        assertEquals(Emphasis.NORMAL, style(ScrappyFood.FISH, caught = true, sparkling = true))
        assertEquals(Emphasis.NORMAL, style(null, caught = true, sparkling = true))
    }

    @Test fun disablingPriorityRestoresTheNormalEsp() {
        assertEquals(Emphasis.NORMAL, style(ScrappyFood.FISH, caught = true, enabled = false))
    }

}
