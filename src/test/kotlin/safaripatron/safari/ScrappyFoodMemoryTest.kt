// ScrappyFoodMemoryTest.kt
// Module: Food deduction, observed evidence and server entity tracking regressions
// Build: Kotlin 2.4.20 / JVM 25 / JUnit 4

package safaripatron.safari

import safaripatron.safari.ScrappyFoodMemory.Choice
import safaripatron.safari.ScrappyFoodMemory.Observation
import safaripatron.safari.ScrappyFoodMemory.Source
import safaripatron.safari.ScrappyGuideMatcher.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScrappyFoodMemoryTest {
    private val a = Point(0.0, 0.0, 0.0)
    private val b = Point(20.0, 0.0, 0.0)
    private val c = Point(40.0, 0.0, 0.0)
    private fun observation(key: String, pos: Point, food: ScrappyFood? = null) = Observation(key, pos, hunterFood = food)

    @Test fun aNewSpawnHasNoInventedFood() {
        assertNull(ScrappyFoodMemory().update(listOf(observation("a", a)))["a"])
    }

    @Test fun twoDifferentObservedFoodsDetermineTheThirdInTheSameScan() {
        for (missing in ScrappyFood.entries) {
            val known = ScrappyFood.entries.filter { it != missing }
            val result = ScrappyFoodMemory().update(listOf(observation("c", c),
                observation("a", a, known[0]), observation("b", b, known[1])))
            assertEquals(Choice(missing, Source.REMAINING), result["c"])
            assertEquals(Source.HUNTER, result["a"]?.source)
            assertEquals(Source.HUNTER, result["b"]?.source)
        }
    }

    @Test fun oneKnownFoodDoesNotResolveTwoUnknownSpawns() {
        val result = ScrappyFoodMemory().update(listOf(observation("a", a, ScrappyFood.WHEAT), observation("b", b), observation("c", c)))
        assertNull(result["b"]); assertNull(result["c"])
    }

    @Test fun duplicateObservedFoodsDoNotCountAsTwoDifferentFoods() {
        val result = ScrappyFoodMemory().update(listOf(observation("a", a, ScrappyFood.WHEAT),
            observation("b", b, ScrappyFood.WHEAT), observation("c", c)))
        assertNull(result["c"])
    }

    @Test fun anUnseenThirdSpawnDoesNotCreateAGhostMarker() {
        val result = ScrappyFoodMemory().update(listOf(observation("a", a, ScrappyFood.WHEAT), observation("b", b, ScrappyFood.LILY_PAD)))
        assertEquals(setOf("a", "b"), result.keys)
    }

    @Test fun learningAtDifferentTimesWorksWhenOtherEntitiesUnload() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(observation("c", c)))
        memory.update(listOf(observation("a", a, ScrappyFood.WHEAT)))
        memory.update(listOf(observation("b", b, ScrappyFood.LILY_PAD)))
        assertEquals(Choice(ScrappyFood.FISH, Source.REMAINING), memory.update(listOf(observation("c", c)))["c"])
    }

    @Test fun verifiedNpcSkinsCanSupplyDirectEvidenceWithoutNameTags() {
        val result = ScrappyFoodMemory().update(listOf(Observation("a", a, skinFood = ScrappyFood.WHEAT),
            Observation("b", b, skinFood = ScrappyFood.LILY_PAD), observation("c", c)))
        assertEquals(Source.SKIN, result["a"]?.source)
        assertEquals(Choice(ScrappyFood.FISH, Source.REMAINING), result["c"])
    }

    @Test fun aHunterNameOverridesADifferentSkinFallback() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(Observation("a", a, skinFood = ScrappyFood.FISH)))
        assertEquals(Choice(ScrappyFood.WHEAT, Source.HUNTER), memory.update(listOf(observation("a", a, ScrappyFood.WHEAT)))["a"])
        assertEquals(ScrappyFood.WHEAT, memory.update(listOf(Observation("a", a, skinFood = ScrappyFood.FISH)))["a"]?.food)
    }

    @Test fun aConfirmedNameCanCorrectAContradictedDeduction() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(observation("a", a, ScrappyFood.WHEAT), observation("b", b, ScrappyFood.LILY_PAD), observation("c", c)))
        assertEquals(Choice(ScrappyFood.WHEAT, Source.HUNTER), memory.update(listOf(observation("c", c, ScrappyFood.WHEAT)))["c"])
    }

    @Test fun deducedFoodNeverFeedsBackAsDirectEvidence() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(observation("a", a, ScrappyFood.WHEAT), observation("b", b, ScrappyFood.LILY_PAD), observation("c", c)))
        memory.update(listOf(observation("b", b, ScrappyFood.FISH)))
        assertEquals(Choice(ScrappyFood.LILY_PAD, Source.REMAINING), memory.update(listOf(observation("c", c)))["c"])
    }

    @Test fun foodMemorySurvivesMissingHunterTags() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(observation("a", a, ScrappyFood.WHEAT)))
        assertEquals(ScrappyFood.WHEAT, memory.update(listOf(observation("a", a)))["a"]?.food)
    }

    @Test fun recreatedEntityAtTheSameSpawnKeepsItsVerifiedFood() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(observation("old-uuid", a, ScrappyFood.WHEAT)))
        assertEquals(Choice(ScrappyFood.WHEAT, Source.HUNTER), memory.update(listOf(observation("new-uuid", a)))["new-uuid"])
    }

    @Test fun reloadedEntitiesDoNotCountAsExtraSpawns() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(observation("old-a", a, ScrappyFood.WHEAT), observation("b", b, ScrappyFood.LILY_PAD), observation("c", c)))
        val result = memory.update(listOf(observation("new-a", a), observation("new-b", b), observation("new-c", c)))
        assertEquals(Choice(ScrappyFood.FISH, Source.REMAINING), result["new-c"])
    }

    @Test fun simultaneouslyLoadedBodiesAtOnePositionAreNotAssumedToBeOneSpawn() {
        val result = ScrappyFoodMemory().update(listOf(observation("a", a, ScrappyFood.WHEAT),
            observation("b", b, ScrappyFood.LILY_PAD), observation("c", c), observation("d", c)))
        assertNull(result["c"]); assertNull(result["d"])
    }

    @Test fun moreThanThreeSpawnsDisablesTheOneOfEachDeduction() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(observation("a", a, ScrappyFood.WHEAT), observation("b", b, ScrappyFood.LILY_PAD), observation("c", c)))
        val result = memory.update(listOf(observation("c", c), observation("d", Point(60.0, 0.0, 0.0))))
        assertNull(result["c"]); assertNull(result["d"])
    }

    @Test fun aMovingEntityDoesNotCarryAnUnverifiedLocationAssociation() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(observation("a", a, ScrappyFood.WHEAT)))
        assertNull(memory.update(listOf(observation("a", Point(10.0, 0.0, 0.0))))["a"])
    }

    @Test fun ambiguousNamedGuidesPreventDeductionUntilReconfirmed() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(observation("a", a, ScrappyFood.WHEAT), observation("b", b, ScrappyFood.LILY_PAD), observation("c", c)))
        assertNull(memory.update(listOf(Observation("c", c, conflictingHunter = true)))["c"])
        assertNull(memory.update(listOf(Observation("c", c, skinFood = ScrappyFood.FISH)))["c"])
        assertEquals(Source.HUNTER, memory.update(listOf(observation("c", c, ScrappyFood.FISH)))["c"]?.source)
    }

    @Test fun conflictingGuidesInvalidateAPreviouslyLearnedFood() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(observation("a", a, ScrappyFood.WHEAT)))
        assertNull(memory.update(listOf(Observation("a", a, conflictingHunter = true)))["a"])
        assertNull(memory.update(listOf(observation("a", a)))["a"])
    }

    @Test fun resetDoesNotCarryEvidenceOrDeductionsIntoAnotherRun() {
        val memory = ScrappyFoodMemory()
        memory.update(listOf(observation("a", a, ScrappyFood.WHEAT), observation("b", b, ScrappyFood.LILY_PAD), observation("c", c)))
        memory.clear()
        assertTrue(memory.update(listOf(observation("a", a), observation("b", b), observation("c", c))).isEmpty())
    }

    @Test fun invalidPositionsCannotSupplyEvidence() {
        val memory = ScrappyFoodMemory()
        assertTrue(memory.update(listOf(observation("a", Point(Double.NaN, 0.0, 0.0), ScrappyFood.WHEAT))).isEmpty())
        memory.update(listOf(observation("a", a, ScrappyFood.WHEAT)))
        assertNull(memory.update(listOf(observation("a", Point(Double.NaN, 0.0, 0.0))))["a"])
    }
}
