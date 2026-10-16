package io.github.slimefun4.forge;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.eventbus.api.Event;

/** Fired on the logical server before a previously unscanned reserve is stored, including zero. */
public final class GeoResourceGenerationEvent extends Event {
    private final ServerLevel level;
    private final ChunkPos chunk;
    private final Holder<Biome> biome;
    private final GeoResources resource;
    private int value;
    public GeoResourceGenerationEvent(ServerLevel level,ChunkPos chunk,Holder<Biome> biome,GeoResources resource,int value) {
        this.level=level; this.chunk=chunk; this.biome=biome; this.resource=resource; setValue(value);
    }
    public ServerLevel getLevel() { return level; }
    public ChunkPos getChunk() { return chunk; }
    public Holder<Biome> getBiome() { return biome; }
    public GeoResources getResource() { return resource; }
    public int getValue() { return value; }
    public void setValue(int value) {
        if(value<0) throw new IllegalArgumentException("GEO reserve cannot be negative");
        this.value=value;
    }
}
