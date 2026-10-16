package io.github.slimefun4.forge.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Block.class)
public abstract class TalismanLootMixin {
    @Redirect(method="dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Z)V",remap=false,
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",remap=true))
    private static List<ItemStack> slimefun$drops(BlockState state,ServerLevel level,BlockPos pos,BlockEntity block,Entity entity,ItemStack tool){
        // Loot queries also simulate mining; only the native committed drop path activates talismans.
        return io.github.slimefun4.forge.TalismanItem.blockDrops(state,entity,tool,Block.getDrops(state,level,pos,block,entity,tool));
    }
}
