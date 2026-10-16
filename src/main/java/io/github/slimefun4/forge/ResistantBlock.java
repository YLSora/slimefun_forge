package io.github.slimefun4.forge;

import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ResistantBlock extends Block {
    private final boolean glass, witherProof;
    public ResistantBlock(Catalog.Entry entry) {
        super(properties(entry)); glass=entry.material().contains("glass"); witherProof=!entry.id().equals("hardened_glass");
    }
    private static Properties properties(Catalog.Entry entry) {
        var result=Properties.copy(entry.material().contains("glass")?Blocks.GLASS:Blocks.OBSIDIAN).explosionResistance(Float.MAX_VALUE);
        if(entry.material().contains("glass")) result.noOcclusion();
        return result;
    }
    @Override public boolean skipRendering(BlockState state,BlockState adjacent,Direction side) { return glass&&adjacent.is(this)||super.skipRendering(state,adjacent,side); }
    @Override public boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos) { return glass||super.propagatesSkylightDown(state,level,pos); }
    @Override public boolean canEntityDestroy(BlockState state,BlockGetter level,BlockPos pos,Entity entity) { return !(witherProof&&entity instanceof WitherBoss) && super.canEntityDestroy(state,level,pos,entity); }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        // Wither skulls can reduce blast resistance, so resistance alone does not implement RC-37's immunity.
        event.getAffectedBlocks().removeIf(pos -> event.getLevel().getBlockState(pos).getBlock() instanceof ResistantBlock);
    }
}
