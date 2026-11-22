package safaripatron.helpers

import java.util.UUID
import kotlin.test.*

class InteractionHelpersTest {
    private val self = UUID(0, 1)
    private val friend = UUID(0, 2)
    private fun ready(known: Boolean = true, members: Set<UUID> = setOf(self, friend), present: Set<UUID> = setOf(self, friend),
                      detection: Boolean = true, shift: Boolean = false, location: Boolean = true) =
        EntryReadiness(true, shift, known, self, members, present, detection, location)

    @Test fun waitsForRosterBeforeManagerOrTicketInteraction() { assertEquals("Loading party members", ready(known = false).reason) }
    @Test fun missingPartyMemberBlocksEntry() { assertEquals(setOf(friend), ready(present = setOf(self)).missing); assertNotNull(ready(present = setOf(self)).reason) }
    @Test fun staleTabListWithoutSelfCannotPass() { assertNotNull(ready(present = setOf(friend)).reason) }
    @Test fun confirmedSoloRosterDoesNotRequireAnotherPlayer() { assertNull(ready(members = emptySet(), present = setOf(self)).reason) }
    @Test fun waitsForActualScanReadiness() { assertEquals("Waiting for the loaded-entity scan", ready(detection = false).reason) }
    @Test fun waitsForLocationWhileScoreboardLoads() { assertEquals("Waiting for server location", ready(location = false).reason) }
    @Test fun physicalShiftBypassesAllPendingReasons() { assertNull(ready(known = false, present = emptySet(), detection = false, shift = true, location = false).reason) }
    @Test fun disabledGuardPreservesNormalInteraction() { assertNull(ready().copy(enabled = false, rosterKnown = false).reason) }
    @Test fun fullyReadyPartyPassesWithoutAnyClickAutomation() { assertNull(ready().reason) }
    @Test fun selectionRecognizesOnlyServerOwnedTicketControls() {
        assertTrue(EntryRecognition.menu("Critter Safari Entry", true, true))
        assertFalse(EntryRecognition.menu("Critter Safari Entry", false, true))
        assertFalse(EntryRecognition.menu("Critter Safari Entry", true, false))
        assertFalse(EntryRecognition.menu("Other Menu", true, true))
    }
    @Test fun ticketNamesAreAnchoredAndSupportAllTiers() {
        for (tier in listOf("Basic", "Economy", "Premium", "First-Class")) assertTrue(EntryRecognition.ticket("", "$tier Safari Ticket"))
        assertTrue(EntryRecognition.ticket("CRITTER_SAFARI_TICKET_BASIC", "Localized name"))
        assertFalse(EntryRecognition.ticket("", "Not a Safari Ticket"))
        assertFalse(EntryRecognition.ticket("", "Safari Ticket Reminder"))
    }
    @Test fun managerNamesDoNotMatchOrdinaryNpcNames() {
        assertTrue(EntryRecognition.manager("Safari Manager"))
        assertTrue(EntryRecognition.manager("[NPC] Safari Manager"))
        assertFalse(EntryRecognition.manager("Safari Manager's Friend"))
    }
    @Test fun playerChatCannotInvalidatePartyReadiness() {
        assertTrue(EntryRecognition.rosterChange("[MVP+] Player joined the party."))
        assertTrue(EntryRecognition.rosterChange("You left the party."))
        assertFalse(EntryRecognition.rosterChange("Party > [MVP+] Player: You left the party."))
        assertFalse(EntryRecognition.rosterChange("Player: Player joined the party."))
        assertFalse(EntryRecognition.rosterChange("A reminder that Player joined the party."))
    }
    @Test fun stableScanNeedsRepeatedFreshObservations() {
        val scan = ScanStability(); val world = Any(); val values = setOf(1 to "SPARKLING Macaw")
        scan.observe(world, values, false, 1000); assertFalse(scan.ready(world, 1500))
        scan.observe(world, values, false, 1500); assertTrue(scan.ready(world, 1500))
        assertFalse(scan.ready(world, 2600))
    }
    @Test fun newEntitiesAndPendingMetadataReopenTheScanGate() {
        val scan = ScanStability(); val world = Any()
        scan.observe(world, emptySet(), false, 1000); scan.observe(world, emptySet(), false, 1600)
        assertTrue(scan.ready(world, 1600))
        scan.observe(world, setOf(1 to null), true, 1700); assertFalse(scan.ready(world, 2500))
        scan.observe(world, setOf(1 to "SPARKLING Macaw"), false, 2500); assertFalse(scan.ready(world, 2500))
        scan.observe(world, setOf(1 to "SPARKLING Macaw"), false, 3000); assertTrue(scan.ready(world, 3000))
    }
    @Test fun worldChangeAndResetCannotReuseOldScanProof() {
        val scan = ScanStability(); val world = Any()
        scan.observe(world, emptySet(), false, 1000); scan.observe(world, emptySet(), false, 1600)
        assertFalse(scan.ready(Any(), 1600)); scan.reset(); assertFalse(scan.ready(world, 1600))
    }
    @Test fun clickClockProducesTwelveAttemptsInTwentyTicks() {
        val clock = MoundClickClock(); assertEquals(12, (1..20).count { clock.tick(true) })
        assertEquals(12, (1..20).count { clock.tick(true) })
    }
    @Test fun releasingTheMouseResetsCreditWithoutCatchUp() {
        val clock = MoundClickClock(); assertTrue(clock.tick(true)); repeat(40) { assertFalse(clock.tick(false)) }
        assertTrue(clock.tick(true)); assertFalse(clock.tick(true))
    }
    @Test fun everyInputConditionIsRequiredBeforeRepeatingAnAttack() {
        val valid = MoundClickConditions(true, true, true, true, false, false, false, true)
        assertTrue(valid.allowed)
        for (invalid in listOf(valid.copy(enabled = false), valid.copy(cavern = false), valid.copy(held = false),
            valid.copy(focused = false), valid.copy(screenOpen = true), valid.copy(usingItem = true),
            valid.copy(breakingBlock = true), valid.copy(targetMound = false))) assertFalse(invalid.allowed)
    }
    @Test fun serverInteractionShapeCanIdentifyAMoundWithoutAHeadDisplay() {
        assertTrue(MoundTargetRules.matches(-83.5, 40.5, 30.5, .8, .5, false, true, false))
        assertTrue(MoundTargetRules.matches(-83.5, 40.5, 30.5, 1.0, 1.0, true, true, false))
    }
    @Test fun ordinaryNpcAndFloorDropInteractionsAreRejected() {
        assertFalse(MoundTargetRules.matches(-83.5, 40.5, 30.5, .8, 2.0, false, true, false))
        assertFalse(MoundTargetRules.matches(-83.5, 40.5, 30.5, .8, .1, false, true, false))
        assertFalse(MoundTargetRules.matches(-83.2, 40.5, 30.5, .8, .5, true, true, false))
    }
    @Test fun waterAndASpawnedCreatureDisableMoundAttacks() {
        assertFalse(MoundTargetRules.matches(-83.5, 40.5, 30.5, .8, .5, true, false, false))
        assertFalse(MoundTargetRules.matches(-83.5, 40.5, 30.5, .8, .5, true, true, true))
        assertFalse(MoundTargetRules.matches(-83.5, 70.5, 30.5, .8, .5, true, true, false))
    }
}
