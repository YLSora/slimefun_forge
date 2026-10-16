package io.github.slimefun4.forge;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;

public final class BlockDrops {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.BooleanValue CREATIVE;
    static {var builder=new ForgeConfigSpec.Builder();CREATIVE=builder.comment("Drop Slimefun block items when broken in creative mode. Stored inventory still drops independently.").define("drop-block-creative",false);CONFIG=builder.build();}
    public static List<ItemStack> creative(BlockState state,Level level,BlockPos pos,Player player){
        var key=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if(!(level instanceof ServerLevel server)||!player.isCreative()||!CREATIVE.get()||!key.getNamespace().equals(SlimefunForge.ID)||Catalog.entry(key.getPath())==null)return List.of();
        return Block.getDrops(state,server,pos,level.getBlockEntity(pos),player,player.getMainHandItem());
    }
    private BlockDrops(){}
}
