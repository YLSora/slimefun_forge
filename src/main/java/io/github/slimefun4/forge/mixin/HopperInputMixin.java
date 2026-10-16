package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.ProcessingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperInputMixin {
    @Inject(method="addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",at=@At("HEAD"),cancellable=true)
    private static void slimefun$offering(net.minecraft.world.Container target,net.minecraft.world.entity.item.ItemEntity item,CallbackInfoReturnable<Boolean> callback) {
        if(io.github.slimefun4.forge.AncientPedestalEntity.isOffering(item)) callback.setReturnValue(false);
    }
    // Forge adds this unmapped method; intercept only hopper pushes, preserving cargo and pulls.
    @Inject(method="ejectItems",at=@At("HEAD"),cancellable=true,remap=false)
    private static void slimefun$input(Level level,BlockPos pos,BlockState state,HopperBlockEntity hopper,CallbackInfoReturnable<Boolean> callback) {
        var target=pos.relative(state.getValue(HopperBlock.FACING));
        if(level.hasChunkAt(target)&&level.getBlockState(target).getBlock() instanceof io.github.slimefun4.forge.api.ItemApi.NotHopperable){callback.setReturnValue(false);return;}
        if(level.hasChunkAt(target) && level.getBlockEntity(target) instanceof ProcessingEntity machine
            && switch(machine.definition.implementation()) {
                case "ElectricSmeltery","ElectricFurnace","ElectricOreGrinder","ElectricIngotPulverizer","AutoBrewer" -> true;
                default -> false;
            }) callback.setReturnValue(false);
    }
    @Inject(method="suckInItems",at=@At("HEAD"),cancellable=true)
    private static void slimefun$pull(Level level,net.minecraft.world.level.block.entity.Hopper hopper,CallbackInfoReturnable<Boolean> callback){
        var above=BlockPos.containing(hopper.getLevelX(),hopper.getLevelY()+1,hopper.getLevelZ());
        if(level.hasChunkAt(above)&&level.getBlockState(above).getBlock() instanceof io.github.slimefun4.forge.api.ItemApi.NotHopperable)callback.setReturnValue(false);
    }
}
