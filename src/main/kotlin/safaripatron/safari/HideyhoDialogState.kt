// HideyhoDialogState.kt
// Module: Hideyho dialogue recognition and delayed Sure acceptance
// Build: Kotlin 2.4.20 / JVM 25

package safaripatron.safari

internal object HideyhoDialogMessages {
    private const val START = "(?m)^\\h*(?:\\[(?:MOB|NPC)]\\h*)?Hideyho:\\h*"
    private const val END = "\\h*(?:\\(\\d+\\))?$"
    private val question = Regex(START + "How about it\\?" + END, RegexOption.IGNORE_CASE)
    private val hiding = Regex(START + "No peeking!" + END, RegexOption.IGNORE_CASE)
    private val otherNpc = Regex("(?m)^\\h*\\[(?:MOB|NPC)]\\h*([^:\\r\\n]+):", RegexOption.IGNORE_CASE)

    fun isQuestion(raw: String) = question.containsMatchIn(ChatParser.clean(raw))
    fun isHiding(raw: String) = hiding.containsMatchIn(ChatParser.clean(raw))
    fun isOtherNpc(raw: String) = otherNpc.findAll(ChatParser.clean(raw))
        .any { !it.groupValues[1].trim().equals("Hideyho", ignoreCase = true) }
}

internal class HideyhoAutoSureState(private val delayMillis: Long = 300) {
    private var questionAt: Long? = null
    private var offeredAt: Long? = null

    init { require(delayMillis in 50L..1000L) }

    fun onMessage(raw: String, now: Long) {
        when {
            HideyhoDialogMessages.isHiding(raw) || HideyhoDialogMessages.isOtherNpc(raw) -> clear()
            HideyhoDialogMessages.isQuestion(raw) -> questionAt = now
        }
    }

    fun schedule(now: Long): Boolean {
        val question = questionAt ?: return false
        if (now - question !in 0L..5_000L) return false
        if (offeredAt == null) offeredAt = now
        return true
    }

    fun isDue(now: Long): Boolean {
        val offer = offeredAt ?: return false
        return now - offer in delayMillis..30_000L
    }

    fun clear() {
        questionAt = null
        offeredAt = null
    }
}
