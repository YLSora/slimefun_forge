package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.ForgeConfigSpec;

public final class CrucibleBlock extends CauldronBlock {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.BooleanValue ALLOW_WATER_IN_NETHER;
    static {
        var builder=new ForgeConfigSpec.Builder();
        ALLOW_WATER_IN_NETHER=builder.define("allow-water-in-nether",false);
        CONFIG=builder.build();
    }
    public CrucibleBlock() { super(Properties.copy(Blocks.CAULDRON)); }
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
            if(!ResearchData.canUse(server,new ItemStack(asItem()))) {
                ItemAccess.denied(server,new ItemStack(asItem())); return InteractionResult.FAIL;
            }
            var held=player.getMainHandItem();
            for(var recipe:MachineRecipe.forStation(level,"crucible")) {
                if(recipe.shaped || recipe.inputs.size()!=1 || recipe.output.getCount()!=1 || recipe.output.hasTag()
                    || (!recipe.output.is(Items.WATER_BUCKET) && !recipe.output.is(Items.LAVA_BUCKET))) continue;
                var required=recipe.inputs.get(0);
                if(!MachineRecipe.same(held,required) || held.getCount()<required.getCount()) continue;
                var chunk=server.serverLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
                var liquids=chunk==null?null:chunk.getCapability(CrucibleLiquids.CAPABILITY).resolve().orElse(null);
                if(liquids==null) return InteractionResult.FAIL;
                int remaining=required.getCount();
                for(var stack:player.getInventory().items) {
                    if(ItemStack.isSameItemSameTags(stack,required)) {
                        int count=Math.min(remaining,stack.getCount()); stack.shrink(count); remaining-=count;
                        if(remaining==0) break;
                    }
                }
                player.getInventory().setChanged();
                liquids.generate(pos.above(),recipe.output.is(Items.WATER_BUCKET));
                return InteractionResult.CONSUME;
            }
            Messages.chat(player,Component.translatable("message.slimefun.crucible_wrong_item"));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void handlePrecipitation(BlockState state,Level level,BlockPos pos,Biome.Precipitation precipitation) {}
    @Override protected boolean canReceiveStalactiteDrip(Fluid fluid) { return false; }
}
