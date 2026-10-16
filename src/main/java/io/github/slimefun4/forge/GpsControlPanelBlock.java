package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class GpsControlPanelBlock extends HeadBlock {
    public GpsControlPanelBlock(){super(HeadBlock.properties());}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        if(context.getPlayer() instanceof ServerPlayer player&&!ResearchData.canUse(player,context.getItemInHand())){ItemAccess.denied(player,context.getItemInHand());player.inventoryMenu.sendAllDataToRemote();return null;}
        return super.getStateForPlacement(context);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer server){
            if(!GpsAccess.mayOpen(server,pos))return InteractionResult.FAIL;
            var stack=new ItemStack(asItem());if(!ResearchData.canUse(server,stack)){ItemAccess.denied(server,stack);return InteractionResult.FAIL;}
            server.openMenu(new SimpleMenuProvider((id,inv,p)->new GpsPanelMenu(id,inv,pos),GuideContent.localize(server,net.minecraft.network.chat.Component.translatable("menu.slimefun.gps.title").withStyle(net.minecraft.ChatFormatting.BLUE))));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
