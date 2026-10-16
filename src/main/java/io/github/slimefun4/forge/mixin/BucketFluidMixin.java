package io.github.slimefun4.forge.mixin;

import java.util.function.Supplier;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.*;

@Mixin(BucketItem.class)
public abstract class BucketFluidMixin {
    @Shadow(remap=false) @Final private Supplier<? extends Fluid> fluidSupplier;

    /**
     * @author MinazukiSora
     * @reason Keep Forge's supplier getter when a generated Fabric accessor has the same signature.
     */
    @Overwrite(remap=false)
    public Fluid getFluid(){return fluidSupplier.get();}
}
