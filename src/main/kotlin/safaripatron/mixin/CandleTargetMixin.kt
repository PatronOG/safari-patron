package safaripatron.mixin

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import safaripatron.config.Config
import safaripatron.data.Biome
import safaripatron.safari.ChatParser
import safaripatron.safari.Safari

@Mixin(BlockBehaviour.BlockStateBase::class)
abstract class CandleTargetMixin {
    @Shadow abstract fun getBlock(): Block
    @Inject(method = ["getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun safariPatronCandle(world: BlockGetter, pos: BlockPos, context: CollisionContext, info: CallbackInfoReturnable<VoxelShape>) {
        if (!Config.s.incenseCandleTarget || world !is ClientLevel || getBlock() !is CandleBlock ||
            BuiltInRegistries.BLOCK.getKey(getBlock()).toString() != "minecraft:red_candle" ||
            !Safari.inSafari || Safari.biome != Biome.HAUNTED) return
        val client = Minecraft.getInstance()
        if (world !== client.level) return
        val player = client.player ?: return
        if (listOf(player.mainHandItem, player.offhandItem).any { ChatParser.clean(it.hoverName.string) == "Soothing Incense" })
            info.returnValue = Shapes.block()
    }
}
