package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A scan is a server-authoritative, read-only view of the current chunk's reserves. */
public final class GeoScannerBlock extends HeadBlock {
    public GeoScannerBlock() { super(HeadBlock.properties()); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player && !ResearchData.canUse(player,context.getItemInHand())) {
            ItemAccess.denied(player,context.getItemInHand()); player.inventoryMenu.sendAllDataToRemote(); return null;
        }
        return super.getStateForPlacement(context);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player instanceof ServerPlayer server) {
            if(!GpsAccess.mayOpen(server,pos)) return InteractionResult.FAIL;
            var item=new ItemStack(asItem());
            if(!ResearchData.canUse(server,item)) { ItemAccess.denied(server,item); return InteractionResult.FAIL; }
            return scan(server,pos,null);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    public static InteractionResult scan(ServerPlayer player,BlockPos pos,@javax.annotation.Nullable InteractionHand portable) {
        int complexity=GpsSignals.complexity(player.server,player.getUUID());
        if(complexity<600){Messages.lines(player,"message.slimefun.gps_insufficient",600);return InteractionResult.FAIL;}
        var resources=GeoResourceData.get(player.serverLevel()).scan(player.serverLevel(),pos);
        net.minecraftforge.network.NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,p)->new GeoScanMenu(id,inv,pos,resources,portable),GuideContent.localize(player,Component.translatable("container.slimefun.geo_scan").withStyle(net.minecraft.ChatFormatting.DARK_RED))),buffer->GeoScanMenu.write(buffer,player,pos,resources,portable));
        return InteractionResult.CONSUME;
    }
}
