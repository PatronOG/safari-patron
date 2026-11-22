package safaripatron.mixin

import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import safaripatron.helpers.SafariEntryGuard

@Mixin(MultiPlayerGameMode::class)
class SafariEntryGuardMixin {
    @Inject(method = ["attack"], at = [At("HEAD")], cancellable = true, require = 1)
    private fun safariPatronManagerAttack(player: Player, entity: Entity, info: CallbackInfo) {
        if (SafariEntryGuard.manager(entity) && SafariEntryGuard.block()) info.cancel()
    }
    @Inject(method = ["interact"], at = [At("HEAD")], cancellable = true, require = 1)
    private fun safariPatronManagerUse(player: Player, entity: Entity, hit: EntityHitResult, hand: InteractionHand,
                                      info: CallbackInfoReturnable<InteractionResult>) {
        if (SafariEntryGuard.manager(entity) && SafariEntryGuard.block()) info.returnValue = InteractionResult.FAIL
    }
    @Inject(method = ["useItem"], at = [At("HEAD")], cancellable = true, require = 1)
    private fun safariPatronTicket(player: Player, hand: InteractionHand, info: CallbackInfoReturnable<InteractionResult>) {
        if (SafariEntryGuard.ticket(player, hand) && SafariEntryGuard.block()) info.returnValue = InteractionResult.FAIL
    }
    @Inject(method = ["useItemOn"], at = [At("HEAD")], cancellable = true, require = 1)
    private fun safariPatronTicketBlock(player: LocalPlayer, hand: InteractionHand, hit: BlockHitResult,
                                       info: CallbackInfoReturnable<InteractionResult>) {
        if (SafariEntryGuard.ticket(player, hand) && SafariEntryGuard.block()) info.returnValue = InteractionResult.FAIL
    }
    @Inject(method = ["handleContainerInput"], at = [At("HEAD")], cancellable = true, require = 1)
    private fun safariPatronEntryMenu(container: Int, slot: Int, button: Int, input: ContainerInput, player: Player, info: CallbackInfo) {
        if (SafariEntryGuard.selection(container, slot) && SafariEntryGuard.block()) info.cancel()
    }
}
