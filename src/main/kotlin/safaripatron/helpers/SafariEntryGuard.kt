package safaripatron.helpers

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import safaripatron.config.Config
import safaripatron.party.Party
import safaripatron.party.TabList
import safaripatron.safari.ChatParser
import safaripatron.safari.Safari
import safaripatron.safari.Sidebar
import safaripatron.sparkling.SparklingFinder
import java.util.UUID

object SafariEntryGuard {
    private var level: Any? = null
    private val managerBodies = HashSet<UUID>()
    private var noticeAt = 0L

    private fun onHypixel(client: Minecraft): Boolean {
        val host = client.currentServer?.ip?.substringBefore(':')?.lowercase() ?: return false
        return host == "hypixel.net" || host.endsWith(".hypixel.net")
    }
    fun tick(client: Minecraft) {
        if (level !== client.level) { managerBodies.clear(); level = client.level; noticeAt = 0 }
        if (Config.s.safariEntryGuard && onHypixel(client) && !Party.known &&
            (Safari.inSafari || Sidebar.title(client).contains("SKYBLOCK", true))) Party.request()
    }
    fun manager(entity: Entity): Boolean {
        val client = Minecraft.getInstance()
        tick(client)
        if (!onHypixel(client) || entity.isRemoved || client.level?.getEntity(entity.id) !== entity || entity === client.player ||
            entity is Player && entity.uuid in Party.members) return false
        if (entity.uuid in managerBodies) return true
        fun named(e: Entity) = EntryRecognition.manager(ChatParser.clean(e.customName?.string ?: e.name.string))
        if (named(entity)) { managerBodies.add(entity.uuid); return true }
        val proxy = entity is Player || entity is ArmorStand || entity.type == EntityTypes.INTERACTION || entity is net.minecraft.world.entity.Display.TextDisplay
        if (!proxy) return false
        val match = client.level?.entitiesForRendering()?.any { label ->
            !label.isRemoved && named(label) && (label.x-entity.x)*(label.x-entity.x)+(label.z-entity.z)*(label.z-entity.z) <= 1.44 &&
                label.y-entity.y in -.5..3.5
        } == true
        if (match) managerBodies.add(entity.uuid)
        return match
    }
    fun ticket(player: Player, hand: InteractionHand): Boolean {
        val stack = player.getItemInHand(hand)
        val id = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "").orEmpty()
        return EntryRecognition.ticket(id, ChatParser.clean(stack.hoverName.string))
    }
    fun selection(containerId: Int, slot: Int): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        val screen = client.gui.screen() as? AbstractContainerScreen<*> ?: return false
        if (screen.menu.containerId != containerId || slot !in screen.menu.slots.indices) return false
        val target = screen.menu.getSlot(slot)
        return EntryRecognition.menu(ChatParser.clean(screen.title.string), target.container !== player.inventory, target.item.`is`(Items.NAME_TAG))
    }
    fun block(): Boolean {
        val client = Minecraft.getInstance()
        if (!Config.s.safariEntryGuard || !onHypixel(client)) return false
        tick(client)
        val player = client.player ?: return false
        val connection = client.connection ?: return false
        val shift = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)
        val present = if (TabList.active) TabList.players().toMutableSet() else connection.listedOnlinePlayers.mapTo(HashSet()) { it.profile.id }
        // A dimension respawn need not resend our own tab entry; the current local entity is direct presence evidence.
        if (player.level() === client.level) present.add(player.uuid)
        val now = System.currentTimeMillis()
        val state = EntryReadiness(true, shift, Party.known, player.uuid, Party.members, present,
            !Safari.inSafari || !Config.s.sparklingFinder || SparklingFinder.scanReady(client.level, now),
            Sidebar.title(client).contains("SKYBLOCK", true))
        val reason = state.reason ?: return false
        if (now - noticeAt >= 3000) {
            noticeAt = now
            player.sendSystemMessage(Component.literal("§6[Safari Patron] §e$reason. §7Hold Shift to enter anyway."))
        }
        return true
    }
}
