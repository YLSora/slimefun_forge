package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/** Machines use scheduled block ticks; idle processors have no permanent BE ticker. */
public class MachineBlock extends HeadBlock.Entity {
    public final Catalog.Entry definition;
    public MachineBlock(Catalog.Entry definition) {
        super(definition.material().equals("player_head")?HeadBlock.properties():Properties.of().mapColor(MapColor.METAL).strength(3.5F).requiresCorrectToolForDrops().sound(SoundType.METAL));
        this.definition=definition;
    }
    @Override protected boolean hasHead(){return false;}
    public static final class Head extends MachineBlock {
        public Head(Catalog.Entry definition){super(definition);}
        @Override protected boolean hasHead(){return true;}
    }
    public static final class Capacitor extends MachineBlock {
        public static final IntegerProperty CHARGE=IntegerProperty.create("charge_level",0,3);
        public Capacitor(Catalog.Entry definition){super(definition);}
        @Override protected boolean hasHead(){return true;}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(CHARGE);}
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new MachineEntity(pos,state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player && !ResearchData.canUse(player,context.getItemInHand())) {
            ItemAccess.denied(player,context.getItemInHand()); player.inventoryMenu.sendAllDataToRemote(); return null;
        }
        return super.getStateForPlacement(context);
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moving) {
        super.onPlace(state,level,pos,previous,moving);
        if(!level.isClientSide) level.scheduleTick(pos,this,MachineClock.delay(level));
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof MachineEntity machine)machine.owner(player.getUUID());
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(level.getBlockEntity(pos) instanceof MachineEntity machine) {
            EnergyNetwork.add(machine);
            if(!MachineClock.callbacksAllowed(state))return;
            // A queued tick can become overdue while its chunk is not ticking.
            if(machine.assembly==null && !MachineClock.due(level)) machine.wake(); else machine.work();
        }
    }
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos neighbor,boolean moving) {
        if(level.getBlockEntity(pos) instanceof MachineEntity machine) machine.wake();
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof MachineEntity machine) {
            if(!level.mayInteract(player,pos)) return InteractionResult.FAIL;
            var stack=new net.minecraft.world.item.ItemStack(asItem());
            if(!ResearchData.canUse(server,stack)) { ItemAccess.denied(server,stack); return InteractionResult.FAIL; }
            if(definition.implementation().equals("EnergyConnector")) {
                boolean connected=EnergyNetwork.connected(level,pos);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.slimefun.connected",
                    net.minecraft.network.chat.Component.literal(connected?"✔":"✘").withStyle(connected?net.minecraft.ChatFormatting.DARK_GREEN:net.minecraft.ChatFormatting.DARK_RED)).withStyle(net.minecraft.ChatFormatting.GRAY),false);
            } else if(machine.hasInventory()) GuideContent.open(server,machine);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState old,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!old.is(next.getBlock())) {
            if(level.getBlockEntity(pos) instanceof MachineEntity machine) {
                machine.dismantle();
                level.updateNeighbourForOutputSignal(pos,this);
            }
            super.onRemove(old,level,pos,next,moving);
        }
    }
}
