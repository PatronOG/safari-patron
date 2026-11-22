package safaripatron.resources

import kotlin.test.*
import safaripatron.data.Biome

class FloorDropModelTest {
    private fun biome(slot: Int): Biome = when {
        slot < 6 -> Biome.CAVERN
        slot < 15 -> Biome.FOREST
        slot < 24 -> Biome.HAUNTED
        slot < 29 -> Biome.ICY
        else -> Biome.entries[(slot - 29) % 4]
    }
    private fun displays(slots: List<Int>): List<FloorDisplay> = slots.flatMap { slot ->
        val b = biome(slot)
        val x = if (b == Biome.CAVERN || b == Biome.ICY) -120.0 + slot else 10.0 + slot
        val z = if (b == Biome.CAVERN || b == Biome.FOREST) 40.0 else -40.0
        (0..2).map { n -> FloorDisplay(1000 + slot * 3 + n, DropPoint(x + n * .2, 60.8, z), "minecraft:string:String") }
    }
    private fun scan(model: FloorDropModel, slots: List<Int>) = model.scan(displays(slots)) { p ->
        if (p.x < -50) { if (p.z > 0) Biome.CAVERN else Biome.ICY }
        else { if (p.z > 0) Biome.FOREST else Biome.HAUNTED }
    }
    private val partial = listOf(1, 2, 3, 8, 10, 11, 14, 15, 16, 17, 18, 19, 20, 21, 23, 25, 26, 28, 29, 30, 31)

    @Test fun recoversMissingOriginInsteadOfRotatingGemColors() {
        val model = FloorDropModel(); val result = scan(model, partial).associateBy { it.id }
        assertEquals(DropKind.PURPLE, result.getValue(1003).kind)
        assertEquals(DropKind.ORANGE, result.getValue(1006).kind)
        assertEquals(DropKind.LIME, result.getValue(1009).kind)
        assertEquals(setOf(1000), model.layouts.map { it.base }.toSet())
    }
    @Test fun foodGroupingUsesIdGapsRatherThanLoadedListIndex() {
        val result = scan(FloorDropModel(), partial).associateBy { it.id }
        assertEquals(DropKind.WORM, result.getValue(1024).kind)
        assertEquals(DropKind.BERRY, result.getValue(1030).kind)
        assertEquals(DropKind.SEEDS, result.getValue(1042).kind)
    }
    @Test fun ambiguousIceBoundaryStaysUnknown() {
        val model = FloorDropModel(); val result = scan(model, partial)
        assertNull(result.single { it.id == 1084 }.kind)
        assertTrue(model.layouts.map { it.ice }.toSet().containsAll(setOf(4, 5)))
    }
    @Test fun actualPickupDisambiguatesTheBoundaryWithoutCircularEvidence() {
        val model = FloorDropModel(); scan(model, partial)
        model.picked(1084, DropKind.ICEBREAKER)
        assertEquals(setOf(FloorDropModel.Layout(1000, 4, 5), FloorDropModel.Layout(1000, 5, 4)), model.layouts.toSet())
        assertFalse(scan(model, partial).any { it.id == 1084 })
        scan(model, partial + 24)
        assertEquals(listOf(FloorDropModel.Layout(1000, 4, 5)), model.layouts)
    }
    @Test fun conflictingActualRewardDropsAllInferredLabels() {
        val model = FloorDropModel(); scan(model, partial); model.picked(1003, DropKind.BERRY)
        assertTrue(model.layouts.isEmpty())
        assertTrue(model.knownGroups.all { it.kind == null })
    }
    @Test fun unloadRetainsAnchorsButDoesNotInventVisibleDrops() {
        val model = FloorDropModel(); scan(model, partial)
        assertTrue(scan(model, emptyList()).isEmpty())
        assertEquals(partial.size, model.knownGroups.size)
        assertEquals(DropKind.BERRY, scan(model, listOf(10)).single().kind)
    }
    @Test fun resetSeparatesRunsWithReusedIds() {
        val model = FloorDropModel(); scan(model, partial); model.picked(1084, DropKind.ICEBREAKER); model.reset()
        assertTrue(model.knownGroups.isEmpty()); assertTrue(model.layouts.isEmpty())
        assertNull(scan(model, listOf(10)).single().kind)
    }
    @Test fun oneBiomeCannotEstablishTheGlobalPrefix() {
        val model = FloorDropModel(); val result = scan(model, listOf(6, 7, 8, 9, 10, 11, 12, 13, 14))
        assertTrue(result.all { it.kind == null })
    }
    @Test fun everyFoodInACompletePrefixIsClassified() {
        val result = scan(FloorDropModel(), (0..33).toList()).associateBy { it.id }
        for (slot in 6..14) assertEquals(listOf(DropKind.WORM, DropKind.BERRY, DropKind.SEEDS)[(slot-6)/3], result.getValue(1000+slot*3).kind)
    }
    @Test fun ignoresUninitializedAndDecorativeDisplays() {
        val raw = displays(listOf(1)).map { it.copy(description = "") } + displays(listOf(2)).map { it.copy(description = "minecraft:paper") }
        assertTrue(FloorDropModel().scan(raw) { Biome.CAVERN }.isEmpty())
    }
    @Test fun requiresThreeNearbyConsecutiveMatchingDisplays() {
        val good = displays(listOf(1))
        assertTrue(FloorDropModel().scan(good.take(2)) { Biome.CAVERN }.isEmpty())
        val far = good.dropLast(1) + good.last().copy(point = DropPoint(80.0, 60.0, 40.0))
        assertTrue(FloorDropModel().scan(far) { Biome.CAVERN }.isEmpty())
        val mismatch = good.dropLast(1) + good.last().copy(description = "minecraft:string:Other")
        assertTrue(FloorDropModel().scan(mismatch) { Biome.CAVERN }.isEmpty())
    }
    @Test fun supportsGroupsAcrossBlockBoundaries() {
        val raw = (0..2).map { FloorDisplay(5+it, DropPoint(-.2+it*.3, 65.8, 2.0), "minecraft:string") }
        assertEquals(1, FloorDropModel().scan(raw) { Biome.FOREST }.size)
    }
    @Test fun boundedHistoryFailsToUnknownRatherThanForcingAColor() {
        val result = scan(FloorDropModel(), (0..300).toList())
        assertEquals(256, result.size)
        assertTrue(result.all { it.kind == null })
    }
    @Test fun pickupParserRejectsPlayerChatAndGemShards() {
        assertNull(DropKind.pickup("Player: FLOOR DROP! You found Purple Gem on the ground!"))
        assertEquals(DropKind.REWARD, DropKind.pickup("FLOOR DROP! You found Purple Gem Shard on the ground!"))
        assertEquals(DropKind.SEEDS, DropKind.pickup("FLOOR DROP! You found Bag of Seeds on the ground! (2)"))
        assertNull(DropKind.pickup("FLOOR DROP! You found Unknown Quest on the ground!"))
    }
}
