package safaripatron.mixin

import net.minecraft.world.entity.animal.fish.TropicalFish
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.entity.monster.Shulker
import net.minecraft.world.item.DyeColor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker
import java.util.Optional

/** Set the colors of screen-only critter copies (the game keeps these setters private). */
@Mixin(Parrot::class)
interface ParrotVariantInvoker {
    @Invoker("setVariant") fun safariPatronSetVariant(variant: Parrot.Variant)
}

@Mixin(TropicalFish::class)
interface TropicalFishVariantInvoker {
    @Invoker("setPackedVariant") fun safariPatronSetPackedVariant(variant: Int)
}

@Mixin(Shulker::class)
interface ShulkerColorInvoker {
    @Invoker("setVariant") fun safariPatronSetColor(color: Optional<DyeColor>)
}
