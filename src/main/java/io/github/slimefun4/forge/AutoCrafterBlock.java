package io.github.slimefun4.forge;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.*;

public final class AutoCrafterBlock extends HeadBlock.Entity {
    public static final TagKey<Block> STORAGE=TagKey.create(Registries.BLOCK,SlimefunForge.key("auto_crafter_supported_storage_blocks"));
    public final Catalog.Entry definition;
    public AutoCrafterBlock(Catalog.Entry entry) { super(HeadBlock.properties()); definition=entry; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new AutoCrafterEntity(pos,state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player && !ResearchData.canUse(player,context.getItemInHand())) { ItemAccess.denied(player,context.getItemInHand()); player.inventoryMenu.sendAllDataToRemote(); return null; }
        return super.getStateForPlacement(context);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) { if(MachineClock.ready(level,pos,state)&&level.getBlockEntity(pos) instanceof AutoCrafterEntity machine) machine.work(); }
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos neighbor,boolean moving) { wake(level,pos); }
    private static void wake(Level level,BlockPos pos) {
        if(!(level instanceof ServerLevel server)) return;
        var chunk=server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk!=null && chunk.getBlockEntity(pos) instanceof AutoCrafterEntity machine) machine.wake();
    }
    public static void inventoryChanged(Level level,BlockPos pos,BlockState state) {
        if(level.isClientSide || !state.is(STORAGE)) return;
        wake(level,pos.above());
        if(state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE) wake(level,pos.relative(ChestBlock.getConnectedDirection(state)).above());
    }
    @SubscribeEvent public static void chunkLoad(ChunkEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
            for(var entity:chunk.getBlockEntities().values()) inventoryChanged(level,entity.getBlockPos(),entity.getBlockState());
    }
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof AutoCrafterBlock)) return;
        boolean denied=event.getUseBlock()==Event.Result.DENY;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        if(event.getHand()!=InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
            || !(event.getLevel().getBlockEntity(event.getPos()) instanceof AutoCrafterEntity machine)) return;
        if(denied || !event.getLevel().mayInteract(player,event.getPos()) || !player.mayUseItemAt(event.getPos(),event.getFace(),player.getMainHandItem())) return;
        if(!ResearchData.canUse(player,new ItemStack(machine.getBlockState().getBlock()))) { ItemAccess.denied(player,machine.definition.id()); return; }
        if(machine.vanilla() && player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_LIMITED_CRAFTING)
            && player.level().getRecipeManager().getRecipes().stream().anyMatch(r->ItemStack.isSameItemSameTags(r.getResultItem(player.level().registryAccess()),player.getMainHandItem()) && !player.getRecipeBook().contains(r))) {
            Messages.chat(player,Component.translatable("message.slimefun.autocrafter.undiscovered")); return;
        }
        AutoCrafterMenu.open(player,machine,player.isShiftKeyDown());
    }
}
