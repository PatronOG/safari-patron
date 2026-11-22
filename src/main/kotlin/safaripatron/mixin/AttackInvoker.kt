package safaripatron.mixin

import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(Minecraft::class)
interface AttackInvoker {
    @Invoker("startAttack") fun safariPatronAttack(): Boolean
}
