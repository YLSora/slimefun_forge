package io.github.slimefun4.forge;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public final class CargoBlock extends HeadBlock.Entity {
    public final Catalog.Entry definition;
    public CargoBlock(Catalog.Entry definition) {
        super(HeadBlock.properties());
        this.definition=definition;if(definition.id().startsWith("cargo_node_"))registerDefaultState(defaultBlockState().setValue(POSE,wallPose(Direction.NORTH)));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player && !ResearchData.canUse(player,context.getItemInHand())) {
            ItemAccess.denied(player,context.getItemInHand());
            player.inventoryMenu.sendAllDataToRemote(); // Restore the item consumed by client placement prediction.
            return null;
        }
        var face=context.getClickedFace();
        if(definition.id().startsWith("cargo_node_") && (face.getAxis().isVertical()||!context.getLevel().getBlockState(context.getClickedPos()).isAir())) return null;
        return super.getStateForPlacement(context);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new CargoEntity(pos,state); }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moving) {
        if(!level.isClientSide) level.scheduleTick(pos,this,MachineClock.delay(level));
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(!MachineClock.ready(level,pos,state))return;
        if(level.getBlockEntity(pos) instanceof CargoEntity node) {
            CargoNetwork.add(node);
            if(node.manager()) { CargoNetwork.tick(node); level.scheduleTick(pos,this,MachineClock.delay(level)); }
        }
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof CargoEntity node) {
            if(!level.mayInteract(player,pos))return InteractionResult.FAIL;
            var stack=new ItemStack(asItem());
            if(!ResearchData.canUse(server,stack)) { ItemAccess.denied(server,stack); return InteractionResult.FAIL; }
            if(node.manager()){
                node.toggleVisualizer();Messages.send(server,net.minecraft.network.chat.Component.translatable("menu.slimefun.cargo.visualizer",
                    net.minecraft.network.chat.Component.literal(node.visualizer()?"✔":"✘").withStyle(node.visualizer()?net.minecraft.ChatFormatting.DARK_GREEN:net.minecraft.ChatFormatting.DARK_RED)).withStyle(net.minecraft.ChatFormatting.RED),false,false);
            }else if(node.connector()){
                boolean connected=CargoNetwork.connected(level,pos);
                Messages.send(server,net.minecraft.network.chat.Component.translatable("message.slimefun.connected",net.minecraft.network.chat.Component.literal(connected?"✔":"✘").withStyle(connected?net.minecraft.ChatFormatting.DARK_GREEN:net.minecraft.ChatFormatting.DARK_RED)).withStyle(net.minecraft.ChatFormatting.GRAY),true,false);
            }else GuideContent.open(server,node);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState old,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!old.is(next.getBlock())) {
            if(level.getBlockEntity(pos) instanceof CargoEntity node) Containers.dropContents(level,pos,node.filter);
            super.onRemove(old,level,pos,next,moving);
        }
    }
}
