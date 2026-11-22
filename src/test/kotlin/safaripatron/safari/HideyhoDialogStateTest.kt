// HideyhoDialogStateTest.kt
// Module: Hideyho offer timing and unrelated dialogue rejection checks
// Build: Kotlin 2.4.20 / JVM 25 / JUnit 4

package safaripatron.safari

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HideyhoDialogStateTest {
    private val question = "[MOB] Hideyho: How about it?"

    @Test
    fun confirmedOfferWaitsThreeHundredMilliseconds() {
        val state = HideyhoAutoSureState()
        state.onMessage(question, 1_000)
        assertTrue(state.schedule(1_050))
        assertFalse(state.isDue(1_349))
        assertTrue(state.isDue(1_350))
    }

    @Test
    fun sureWithoutHideyhoQuestionIsIgnored() {
        val state = HideyhoAutoSureState()
        state.onMessage("Select an option: [Sure] [No thanks...]", 1_000)
        assertFalse(state.schedule(1_000))
        assertFalse(state.isDue(2_000))
    }

    @Test
    fun playerQuotingHideyhoDoesNotAuthenticateAnOffer() {
        val state = HideyhoAutoSureState()
        state.onMessage("[MVP+] Player: $question", 1_000)
        assertFalse(state.schedule(1_000))
    }

    @Test
    fun aDifferentNpcCannotReuseTheQuestion() {
        val state = HideyhoAutoSureState()
        state.onMessage(question, 1_000)
        state.onMessage("[NPC] Someone Else: Select an option: [Sure]", 1_010)
        assertFalse(state.schedule(1_020))
    }

    @Test
    fun questionExpiresBeforeAStaleOfferArrives() {
        val state = HideyhoAutoSureState()
        state.onMessage(question, 1_000)
        assertFalse(state.schedule(6_001))
    }

    @Test
    fun duplicateListenersDoNotDelayAcceptance() {
        val state = HideyhoAutoSureState()
        state.onMessage(question, 1_000)
        assertTrue(state.schedule(1_050))
        state.onMessage(question, 1_100)
        assertTrue(state.schedule(1_150))
        assertTrue(state.isDue(1_350))
    }

    @Test
    fun noPeekingCancelsThePendingAction() {
        val state = HideyhoAutoSureState()
        state.onMessage(question, 1_000)
        assertTrue(state.schedule(1_050))
        state.onMessage("[MOB] Hideyho: No peeking!", 1_100)
        assertFalse(state.isDue(1_350))
        assertFalse(state.schedule(1_400))
    }

    @Test
    fun expiredActionsAreNeverDue() {
        val state = HideyhoAutoSureState()
        state.onMessage(question, 1_000)
        state.schedule(1_000)
        assertFalse(state.isDue(31_001))
    }

    @Test
    fun clockMovingBackwardsDoesNotFireOrAuthenticate() {
        val state = HideyhoAutoSureState()
        state.onMessage(question, 1_000)
        assertFalse(state.schedule(999))
        state.schedule(1_000)
        assertFalse(state.isDue(999))
    }

    @Test
    fun formattedAndMultilineGameDialogueIsRecognized() {
        assertTrue(HideyhoDialogMessages.isQuestion("§e\uE087[MOB] Hideyho: §fHow about it? (2)"))
        assertTrue(HideyhoDialogMessages.isQuestion("[NPC] Hideyho: How about it?\nSelect an option: [Sure] [No thanks...]"))
        assertTrue(HideyhoDialogMessages.isHiding("§e[MOB] Hideyho: §fNo peeking!"))
        assertFalse(HideyhoDialogMessages.isHiding("[MOB] Someone Else: No peeking!"))
    }

    @Test
    fun resetRequiresAFreshQuestion() {
        val state = HideyhoAutoSureState()
        state.onMessage(question, 1_000)
        state.schedule(1_000)
        state.clear()
        assertFalse(state.isDue(1_300))
        assertFalse(state.schedule(1_400))
        state.onMessage(question, 2_000)
        assertTrue(state.schedule(2_000))
        assertTrue(state.isDue(2_300))
    }
}
