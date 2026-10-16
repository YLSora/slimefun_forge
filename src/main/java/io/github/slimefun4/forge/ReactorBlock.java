package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ReactorBlock extends HeadBlock.Entity {
    public static final IntegerProperty COOLANT=IntegerProperty.create("coolant",0,50);
    public final Catalog.Entry definition;
    public ReactorBlock(Catalog.Entry entry) {super(entry.material().equals("player_head")?HeadBlock.properties():Properties.copy(Blocks.CYAN_TERRACOTTA));definition=entry;if(hasHead())registerDefaultState(defaultBlockState().setValue(COOLANT,0));}
    public static final class Port extends ReactorBlock {public Port(Catalog.Entry entry){super(entry);}@Override protected boolean hasHead(){return false;}}
    public boolean port() {return definition.implementation().equals("ReactorAccessPort");}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {super.createBlockStateDefinition(b);if(hasHead())b.add(COOLANT);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {return port()?new ReactorPortEntity(pos,state):new ReactorEntity(pos,state);}
    @Override public RenderShape getRenderShape(BlockState state) {return RenderShape.MODEL;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {if(ctx.getPlayer() instanceof ServerPlayer p && !ResearchData.canUse(p,ctx.getItemInHand())) {ItemAccess.denied(p,ctx.getItemInHand());p.inventoryMenu.sendAllDataToRemote();return null;}return super.getStateForPlacement(ctx);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player instanceof ServerPlayer p) {if(!level.mayInteract(p,pos)) return InteractionResult.FAIL;if(!ResearchData.canUse(p,new ItemStack(asItem()))) {ItemAccess.denied(p,new ItemStack(asItem()));return InteractionResult.FAIL;}if(level.getBlockEntity(pos) instanceof MenuProvider provider) GuideContent.open(p,provider);}return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState old,Level level,BlockPos pos,BlockState next,boolean moving) {if(!old.is(next.getBlock())) {var e=level.getBlockEntity(pos);if(e instanceof ReactorEntity r) r.dismantle();else if(e instanceof ReactorPortEntity p) p.dismantle();super.onRemove(old,level,pos,next,moving);}}
}
