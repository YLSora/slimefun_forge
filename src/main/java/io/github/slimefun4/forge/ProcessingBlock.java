package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** RC-37 AContainer processors share a reserved-input operation. */
public class ProcessingBlock extends HeadBlock.Entity {
    public final Catalog.Entry definition;
    public ProcessingBlock(Catalog.Entry definition) {
        // Properties.copy omits the vanilla glass predicates.
        super(definition.material().equals("player_head")||definition.id().equals("refinery")?HeadBlock.properties():definition.material().endsWith("stained_glass")?Properties.copy(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation(definition.material()))).noOcclusion()
            .isValidSpawn((state,level,pos,type)->false).isRedstoneConductor((state,level,pos)->false)
            .isSuffocating((state,level,pos)->false).isViewBlocking((state,level,pos)->false)
            :definition.implementation().equals("ElectrifiedCrucible")?Properties.copy(Blocks.RED_TERRACOTTA)
            :Properties.of().strength(3.5F).requiresCorrectToolForDrops().sound(SoundType.METAL));
        this.definition=definition;
    }
    @Override protected boolean hasHead(){return false;}
    public static final class Head extends ProcessingBlock {
        public Head(Catalog.Entry definition){super(definition);}
        @Override protected boolean hasHead(){return true;}
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new ProcessingEntity(pos,state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getVisualShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return definition.material().endsWith("stained_glass")?Shapes.empty():super.getVisualShape(state,level,pos,context);
    }
    @Override public float getShadeBrightness(BlockState state,BlockGetter level,BlockPos pos) {
        return definition.material().endsWith("stained_glass")?1:super.getShadeBrightness(state,level,pos);
    }
    @Override public boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos) {
        return definition.material().endsWith("stained_glass") || super.propagatesSkylightDown(state,level,pos);
    }
    @Override public boolean skipRendering(BlockState state,BlockState adjacent,Direction direction) {
        return definition.implementation().equals("CarbonPress") && adjacent.getBlock() instanceof ProcessingBlock other && other.definition.implementation().equals("CarbonPress") || super.skipRendering(state,adjacent,direction);
    }
    @Override public float[] getBeaconColorMultiplier(BlockState state,LevelReader level,BlockPos pos,BlockPos beaconPos) {
        return definition.implementation().equals("CarbonPress")?Blocks.BLACK_STAINED_GLASS.getBeaconColorMultiplier(Blocks.BLACK_STAINED_GLASS.defaultBlockState(),level,pos,beaconPos):super.getBeaconColorMultiplier(state,level,pos,beaconPos);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player && !ResearchData.canUse(player,context.getItemInHand())) {
            ItemAccess.denied(player,context.getItemInHand()); player.inventoryMenu.sendAllDataToRemote(); return null;
        }
        return super.getStateForPlacement(context);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(!MachineClock.callbacksAllowed(state))return;
        if(level.getBlockEntity(pos) instanceof ProcessingEntity machine) {
            // A queued tick can become overdue while its chunk is not ticking.
            if(!MachineClock.due(level)) machine.wake(); else machine.work();
        }
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof ProcessingEntity machine) {
            if(!level.mayInteract(player,pos)) return InteractionResult.FAIL;
            var item=new ItemStack(asItem());
            if(!ResearchData.canUse(server,item)) { ItemAccess.denied(server,item); return InteractionResult.FAIL; }
            if(machine.oilPump() && GeoResourceData.get(server.serverLevel()).supply(new ChunkPos(pos),GeoResources.OIL).isEmpty()) {
                Messages.chat(server,Component.translatable("message.slimefun.geo_scan_required")); return InteractionResult.FAIL;
            }
            net.minecraftforge.network.NetworkHooks.openScreen(server,new net.minecraft.world.SimpleMenuProvider(machine::createMenu,GuideContent.localize(server,machine.getDisplayName())),buffer->ProcessingMenu.write(buffer,server,machine));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState old,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!old.is(next.getBlock())) {
            if(level.getBlockEntity(pos) instanceof ProcessingEntity machine) {
                machine.dismantle(); level.updateNeighbourForOutputSignal(pos,this);
            }
            super.onRemove(old,level,pos,next,moving);
        }
    }
}
