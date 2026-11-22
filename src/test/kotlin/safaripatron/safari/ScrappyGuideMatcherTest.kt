// ScrappyGuideMatcherTest.kt
// Module: Hunter association checks using measured Safari runs
// Build: Kotlin 2.4.20 / JVM 25 / JUnit 4

package safaripatron.safari

import safaripatron.safari.ScrappyGuideMatcher.Guide
import safaripatron.safari.ScrappyGuideMatcher.Point
import safaripatron.safari.ScrappyGuideMatcher.Subject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScrappyGuideMatcherTest {
    @Test
    fun namedHuntersIdentifyTheObservedFoods() {
        assertEquals(ScrappyFood.FISH, ScrappyFood.fromHunterName("Hunter Luigi"))
        assertEquals(ScrappyFood.LILY_PAD, ScrappyFood.fromHunterName("Hunter Jasper"))
        assertEquals(ScrappyFood.WHEAT, ScrappyFood.fromHunterName("Hunter Reeves"))
    }

    @Test
    fun formattingAndNpcPrefixAreRemoved() {
        assertEquals(ScrappyFood.LILY_PAD, ScrappyFood.fromHunterName("§e\uE087Hunter Jasper\u200B"))
        assertEquals(ScrappyFood.WHEAT, ScrappyFood.fromHunterName(" [NPC] Hunter Reeves "))
        assertEquals(ScrappyFood.FISH, ScrappyFood.fromHunterName("hunter luigi"))
    }

    @Test
    fun unknownNamesAndDialogueDoNotProduceAFood() {
        assertNull(ScrappyFood.fromHunterName(null))
        assertNull(ScrappyFood.fromHunterName("Hunter Brian"))
        assertNull(ScrappyFood.fromHunterName("Hunter Luigi: Try some food!"))
        assertNull(ScrappyFood.fromHunterName("Not Hunter Luigi"))
    }

    @Test
    fun firstMeasuredRunMatchesAllThreeConfirmedFeedings() {
        val subjects = listOf(
            Subject(1277, Point(-103.5, 57.0, 63.5)),
            Subject(1280, Point(-99.5, 95.0, 20.5)),
            Subject(1283, Point(-61.5, 86.0, 81.5)),
        )
        val guides = listOf(
            Guide(Point(-104.5, 57.0, 62.5), ScrappyFood.WHEAT),
            Guide(Point(-99.5, 95.0, 19.5), ScrappyFood.FISH),
            Guide(Point(-61.5, 86.0, 80.5), ScrappyFood.LILY_PAD),
        )
        assertEquals(mapOf(1277 to ScrappyFood.WHEAT, 1280 to ScrappyFood.FISH, 1283 to ScrappyFood.LILY_PAD),
            ScrappyGuideMatcher.match(subjects, guides))
    }

    @Test
    fun secondMeasuredRunStillMatchesAfterPositionsAndIdsChange() {
        val subjects = listOf(
            Subject(4133, Point(-111.5, 54.0, 81.5)),
            Subject(4136, Point(-73.5, 86.0, 91.5)),
            Subject(4130, Point(-132.5, 54.0, 82.5)),
        )
        val guides = listOf(
            Guide(Point(-132.5, 54.0, 84.5), ScrappyFood.WHEAT),
            Guide(Point(-72.5, 86.0, 92.5), ScrappyFood.LILY_PAD),
            Guide(Point(-112.5, 54.0, 81.5), ScrappyFood.FISH),
        )
        assertEquals(mapOf(4133 to ScrappyFood.FISH, 4136 to ScrappyFood.LILY_PAD, 4130 to ScrappyFood.WHEAT),
            ScrappyGuideMatcher.match(subjects, guides))
    }

    @Test
    fun thirdRunReevesPredictionMatchesItsConfirmedFeeding() {
        assertEquals(mapOf(2080 to ScrappyFood.WHEAT), ScrappyGuideMatcher.match(
            listOf(Subject(2080, Point(-140.5, 90.0, 68.5))),
            listOf(Guide(Point(-140.5, 90.0, 69.5), ScrappyFood.WHEAT))))
    }

    @Test
    fun missingHunterDoesNotGuessAFood() {
        assertTrue(ScrappyGuideMatcher.match(listOf(Subject(1, Point(0.0, 0.0, 0.0))), emptyList()).isEmpty())
    }

    @Test
    fun hunterOnAnotherFloorIsNotAssociated() {
        assertTrue(ScrappyGuideMatcher.match(
            listOf(Subject(1, Point(0.0, 0.0, 0.0))),
            listOf(Guide(Point(0.0, 10.0, 0.0), ScrappyFood.FISH))).isEmpty())
    }

    @Test
    fun aGuideIsNotBorrowedFromACloserScrappy() {
        assertEquals(mapOf(1 to ScrappyFood.FISH), ScrappyGuideMatcher.match(
            listOf(Subject(1, Point(0.0, 0.0, 0.0)), Subject(2, Point(2.0, 0.0, 0.0))),
            listOf(Guide(Point(0.5, 0.0, 0.0), ScrappyFood.FISH))))
    }

    @Test
    fun equallyCloseHuntersWithDifferentFoodsAreAmbiguous() {
        assertTrue(ScrappyGuideMatcher.match(
            listOf(Subject(1, Point(0.0, 0.0, 0.0))),
            listOf(Guide(Point(-1.0, 0.0, 0.0), ScrappyFood.FISH),
                Guide(Point(1.0, 0.0, 0.0), ScrappyFood.WHEAT))).isEmpty())
    }

    @Test
    fun oneHunterEquallyCloseToTwoScrappiesIsAmbiguous() {
        assertTrue(ScrappyGuideMatcher.match(
            listOf(Subject(1, Point(-1.0, 0.0, 0.0)), Subject(2, Point(1.0, 0.0, 0.0))),
            listOf(Guide(Point(0.0, 0.0, 0.0), ScrappyFood.FISH))).isEmpty())
    }

    @Test
    fun duplicateNameplatesForTheSameFoodAreHarmless() {
        assertEquals(mapOf(1 to ScrappyFood.FISH), ScrappyGuideMatcher.match(
            listOf(Subject(1, Point(0.0, 0.0, 0.0))),
            listOf(Guide(Point(-1.0, 0.0, 0.0), ScrappyFood.FISH),
                Guide(Point(1.0, 0.0, 0.0), ScrappyFood.FISH))))
    }

    @Test
    fun guideRangeIsLimitedToTheLocalScrappy() {
        val subjects = listOf(Subject(1, Point(0.0, 0.0, 0.0)))
        assertEquals(mapOf(1 to ScrappyFood.FISH), ScrappyGuideMatcher.match(subjects,
            listOf(Guide(Point(4.0, 0.0, 0.0), ScrappyFood.FISH))))
        assertTrue(ScrappyGuideMatcher.match(subjects,
            listOf(Guide(Point(4.01, 0.0, 0.0), ScrappyFood.FISH))).isEmpty())
    }

    @Test
    fun invalidCoordinatesDoNotCreateHints() {
        assertTrue(ScrappyGuideMatcher.match(
            listOf(Subject(1, Point(0.0, 0.0, 0.0))),
            listOf(Guide(Point(Double.NaN, 0.0, 0.0), ScrappyFood.FISH))).isEmpty())
    }
}
