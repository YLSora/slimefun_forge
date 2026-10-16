package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class SpawnerMiningMixin {
    @Shadow @Final protected ServerPlayer player;
    @Shadow protected ServerLevel level;
    @Unique private final java.util.Deque<java.util.Optional<SpecialTools.Mining>> slimefun$mining=new java.util.ArrayDeque<>();
    @Inject(method="destroyBlock",at=@At("HEAD"))
    private void slimefun$begin(BlockPos pos,CallbackInfoReturnable<Boolean> ci){slimefun$mining.push(slimefun$mining.isEmpty()?SpecialTools.begin(player):java.util.Optional.empty());}
    @Inject(method="destroyBlock",at=@At("RETURN"))
    private void slimefun$end(BlockPos pos,CallbackInfoReturnable<Boolean> ci){slimefun$mining.pop();}
    @Redirect(method="destroyBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;mineBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"))
    private void slimefun$veinDurability(ItemStack stack,Level level,BlockState state,BlockPos pos,Player player){var mining=slimefun$mining.peek().orElse(null);if(mining!=null)SpecialTools.applyMining(level,player,pos,state,mining);if(!SpecialTools.veinMining(stack,state))stack.mineBlock(level,state,pos,player);}
    @Redirect(method="destroyBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void slimefun$survivalDrop(Block block,Level level,Player player,BlockPos pos,BlockState state,BlockEntity entity,ItemStack tool) {
        var drop=tool.getItem() instanceof ContainmentPickaxeItem?SpawnerItems.mined(entity):ItemStack.EMPTY;
        var mining=slimefun$mining.peek().orElse(null);
        if(mining==null||!SpecialTools.primaryDrop(level,player,pos,state,entity,tool,mining.fortune()))block.playerDestroy(level,player,pos,state,entity,tool);
        if(!drop.isEmpty())io.github.slimefun4.forge.api.ItemSpawnApi.reserve((ServerLevel)level,player.getUUID(),io.github.slimefun4.forge.api.ItemSpawnApi.Reason.BROKEN_SPAWNER_DROP,net.minecraft.world.phys.Vec3.atCenterOf(pos),drop);
    }
    @Redirect(method="removeBlock",remap=false,at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;onDestroyedByPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/level/material/FluidState;)Z"))
    private boolean slimefun$creativeDrop(BlockState state,Level level,BlockPos pos,Player player,boolean harvest,FluidState fluid) {
        var drop=player.isCreative()&&player.getMainHandItem().getItem() instanceof ContainmentPickaxeItem?SpawnerItems.mined(level.getBlockEntity(pos)):ItemStack.EMPTY;
        var creativeDrops=BlockDrops.creative(state,level,pos,player);
        var entity=level.getBlockEntity(pos);
        boolean removed=state.onDestroyedByPlayer(level,pos,player,harvest,fluid);
        if(removed&&player.isCreative()&&!slimefun$mining.isEmpty()){var mining=slimefun$mining.peek().orElse(null);if(mining!=null&&!SpecialTools.veinMining(mining.tool,state))SpecialTools.primaryDrop(level,player,pos,state,entity,mining.tool,1);}
        if(removed)for(var stack:creativeDrops)Block.popResource(level,pos,stack);
        if(removed&&!drop.isEmpty())io.github.slimefun4.forge.api.ItemSpawnApi.reserve((ServerLevel)level,player.getUUID(),io.github.slimefun4.forge.api.ItemSpawnApi.Reason.BROKEN_SPAWNER_DROP,net.minecraft.world.phys.Vec3.atCenterOf(pos),drop);
        if(removed&&player.isCreative()&&!slimefun$mining.isEmpty()){var mining=slimefun$mining.peek().orElse(null);if(mining!=null)SpecialTools.applyMining(level,player,pos,state,mining);}
        return removed;
    }
}
