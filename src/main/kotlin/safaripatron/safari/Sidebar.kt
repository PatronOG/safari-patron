package safaripatron.safari

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam

/**
 * Yan paneldeki (scoreboard) satırları okur. Ekranda nasıl çizildiğine değil, oyunun içindeki
 * veriye bakar; bu yüzden özel scoreboard modları (noamaddons vb.) kullanılsa da çalışır.
 */
object Sidebar {
    /** Sidebar title, e.g. "SKYBLOCK" (formatting removed). */
    fun title(client: Minecraft): String {
        val objective = client.level?.scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return ""
        return ChatParser.clean(objective.displayName.string).trim()
    }

    fun lines(client: Minecraft): List<String> {
        val level = client.level ?: return emptyList()
        val scoreboard = level.scoreboard
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return emptyList()
        return scoreboard.listPlayerScores(objective)
            .filterNot { it.isHidden }
            .sortedByDescending { it.value() }
            .map { entry ->
                val team = scoreboard.getPlayersTeam(entry.owner())
                val rendered: Component =
                    if (team != null) PlayerTeam.formatNameForTeam(team, entry.ownerName()) else entry.ownerName()
                ChatParser.clean(rendered.string)
            }
            .filter { it.isNotEmpty() }
    }
}
