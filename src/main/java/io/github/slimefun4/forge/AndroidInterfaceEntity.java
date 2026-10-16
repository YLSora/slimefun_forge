package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class AndroidInterfaceEntity extends DispenserBlockEntity {
    public AndroidInterfaceEntity(BlockPos pos,BlockState state){super(SlimefunForge.ANDROID_INTERFACE_ENTITY.get(),pos,state);}
    @Override protected Component getDefaultName(){return Component.translatable("block.slimefun.android_interface_"+(((AndroidInterfaceBlock)getBlockState().getBlock()).fuel?"fuel":"items"));}
    @Override public boolean stillValid(Player player){return player.isAlive()&&player.level()==level&&super.stillValid(player)&&(!(player instanceof ServerPlayer server)||ResearchData.canUse(server,new ItemStack(getBlockState().getBlock())));}
}
