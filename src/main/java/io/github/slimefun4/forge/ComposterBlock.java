package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

/** RC-37's hand-fed cauldron; paid work belongs to the chunk, not this block. */
public final class ComposterBlock extends CauldronBlock {
    public ComposterBlock() { super(Properties.copy(Blocks.CAULDRON)); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player && !ResearchData.canUse(player,context.getItemInHand())) {
            ItemAccess.denied(player,context.getItemInHand()); player.inventoryMenu.sendAllDataToRemote(); return null;
        }
        return super.getStateForPlacement(context);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(hand!=InteractionHand.MAIN_HAND) return InteractionResult.CONSUME;
        if(player instanceof ServerPlayer server) {
            if(player.isSpectator() || !level.mayInteract(player,pos) || level.isOutsideBuildHeight(pos.above())) return InteractionResult.FAIL;
            if(!ResearchData.canUse(server,new ItemStack(asItem()))) { ItemAccess.denied(server,new ItemStack(asItem())); return InteractionResult.FAIL; }
            var held=player.getMainHandItem();var recipe=recipe(held);
            if(recipe==null){wrong(server);return InteractionResult.CONSUME;}
            int count=recipe.inputs.get(0).getCount();Item output=recipe.output.getItem();
            if(held.getCount()<count || !MachineRecipe.same(held,new ItemStack(held.getItem()))) { wrong(server); return InteractionResult.CONSUME; }
            var chunk=server.serverLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            var queue=chunk==null?null:chunk.getCapability(CompostingQueue.CAPABILITY).resolve().orElse(null);
            if(queue==null) return InteractionResult.FAIL;
            var input=held.copyWithCount(count); int remaining=count;
            for(var stack:player.getInventory().items) {
                if(ItemStack.isSameItemSameTags(stack,input)) { int removed=Math.min(stack.getCount(),remaining); stack.shrink(removed); remaining-=removed; if(remaining==0) break; }
            }
            player.getInventory().setChanged(); queue.start(pos,input,new ItemStack(output),player.getUUID());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private static void wrong(ServerPlayer player) { Messages.chat(player,Component.translatable("message.slimefun.composter_wrong_item")); }
    static MachineRecipe recipe(ItemStack input){
        int count;Item output;
        if(input.is(ItemTags.LEAVES)||input.is(ItemTags.SAPLINGS)){count=8;output=Items.DIRT;}
        else if(input.is(Items.STONE)){count=4;output=Items.NETHERRACK;}
        else if(input.is(Items.SAND)){count=2;output=Items.SOUL_SAND;}
        else if(input.is(Items.WHEAT)){count=4;output=Items.NETHER_WART;}
        else return null;
        return new MachineRecipe(SlimefunForge.key("composter"),"composter",java.util.List.of(new ItemStack(input.getItem(),count)),new ItemStack(output),false,320,false);
    }
    @Override public void handlePrecipitation(BlockState state,Level level,BlockPos pos,Biome.Precipitation precipitation) {}
    @Override protected boolean canReceiveStalactiteDrip(Fluid fluid) { return false; }
}
