package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Discard combined Forge handlers when a partner crosses a chunk lifecycle boundary. */
public final class StorageLifecycle {
    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) invalidate(level,chunk);
    }
    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) invalidate(level,chunk);
    }
    private static void invalidate(ServerLevel level,LevelChunk chunk) {
        for(var entity:chunk.getBlockEntities().values()) {
            var state=entity.getBlockState();
            if(!(entity instanceof ChestBlockEntity chest) || !OutputChestBlock.canPair(state.getBlock(),Blocks.CHEST)
                || state.getValue(ChestBlock.TYPE)==ChestType.SINGLE) continue;
            var other=entity.getBlockPos().relative(ChestBlock.getConnectedDirection(state));
            if((other.getX()>>4)==chunk.getPos().x && (other.getZ()>>4)==chunk.getPos().z) continue;
            // ChunkEvent.Load may run before FULL completion. Never ask Level to acquire a chunk here.
            var otherChunk=level.getChunkSource().getChunkNow(other.getX()>>4,other.getZ()>>4);
            var otherEntity=otherChunk==null?null:otherChunk.getBlockEntities().get(other);
            if(!(chest instanceof OutputChestEntity) && !(otherEntity instanceof OutputChestEntity)) continue;
            // setBlockState discards only the vanilla chest's cached combined item capability.
            chest.setBlockState(state);
            if(otherEntity instanceof ChestBlockEntity partner) partner.setBlockState(partner.getBlockState());
        }
    }
    private StorageLifecycle() {}
}
