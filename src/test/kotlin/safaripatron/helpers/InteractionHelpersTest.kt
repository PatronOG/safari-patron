package safaripatron.helpers

import safaripatron.party.RosterChange
import kotlin.test.*

class InteractionHelpersTest {
    @Test fun playerChatCannotInvalidatePartyReadiness() {
        assertTrue(RosterChange.matches("[MVP+] Player joined the party."))
        assertTrue(RosterChange.matches("You left the party."))
        assertFalse(RosterChange.matches("Party > [MVP+] Player: You left the party."))
        assertFalse(RosterChange.matches("Player: Player joined the party."))
        assertFalse(RosterChange.matches("A reminder that Player joined the party."))
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
