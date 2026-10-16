package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public class ResearchBlockItem extends BlockItem {
    public ResearchBlockItem(Block block){super(block,new Properties());}
    @Override public InteractionResult place(BlockPlaceContext context){
        if(context.getPlayer() instanceof ServerPlayer player&&!ResearchData.canUse(player,context.getItemInHand())){ItemAccess.denied(player,context.getItemInHand());player.inventoryMenu.sendAllDataToRemote();return InteractionResult.FAIL;}
        return super.place(context);
    }
}
