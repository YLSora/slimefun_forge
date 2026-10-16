package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** No vanilla dispenser inventory or enchanting menu is exposed by these dedicated ritual blocks. */
public final class AncientRitualBlock extends BaseEntityBlock {
    private final boolean pedestal;
    public AncientRitualBlock(boolean pedestal) {
        super(Properties.copy(pedestal?Blocks.DISPENSER:Blocks.ENCHANTING_TABLE).noOcclusion()); this.pedestal=pedestal;
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return pedestal?Shapes.block():Block.box(0,0,0,16,12,16);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {
        return pedestal?new AncientPedestalEntity(pos,state):new AncientAltarEntity(pos,state);
    }
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level,BlockState state,net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return !pedestal && level.isClientSide?createTickerHelper(type,SlimefunForge.ANCIENT_ALTAR_ENTITY.get(),(world,pos,block,altar)->net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity.bookAnimationTick(world,pos,block,altar.bookAnimation())):null;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player && !ResearchData.canUse(player,context.getItemInHand())) {
            ItemAccess.denied(player,context.getItemInHand()); player.inventoryMenu.sendAllDataToRemote(); return null;
        }
        return super.getStateForPlacement(context);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(pedestal&&hand!=InteractionHand.MAIN_HAND) return InteractionResult.CONSUME;
        if(player instanceof ServerPlayer server) {
            if(!level.mayInteract(player,pos) || player.isSpectator()) return InteractionResult.FAIL;
            var entity=level.getBlockEntity(pos);
            if(entity instanceof AncientPedestalEntity stand) stand.interact(server);
            else if(entity instanceof AncientAltarEntity altar) altar.start(server);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void offering(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof AncientRitualBlock))return;
        // Ritual ingredients must reach the block before their own spell or flask action.
        event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
        if(event.getUseBlock()!=net.minecraftforge.eventbus.api.Event.Result.DENY)event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!state.is(next.getBlock())) {
            var entity=level.getBlockEntity(pos);
            if(entity instanceof AncientPedestalEntity stand) stand.broken();
        }
        super.onRemove(state,level,pos,next,moving);
    }
    @SubscribeEvent public static void placement(BlockEvent.EntityPlaceEvent event) {
        if(event.getLevel().getBlockState(event.getPos().below()).is(SlimefunForge.ANCIENT_PEDESTAL.get())) {
            event.setCanceled(true);
            // Forge restores the pre-placement stack before posting this event.
            if(event.getEntity() instanceof ServerPlayer player) player.inventoryMenu.sendAllDataToRemote();
        }
    }
}
