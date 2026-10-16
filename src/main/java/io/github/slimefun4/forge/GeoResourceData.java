package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension reserves. A missing chunk is unscanned; a stored zero is exhausted. */
public final class GeoResourceData extends SavedData {
    private final Map<ChunkPos,Map<GeoResources,Integer>> chunks=new HashMap<>();
    private final Set<ChunkPos> generating=new HashSet<>();
    record ClearedMining(GeoResources resource,int progress) {}
    // RC-37 keeps cleared machine operations until replacement or server shutdown, without saving them.
    final Map<BlockPos,ClearedMining> clearedMining=new HashMap<>();
    public static GeoResourceData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(GeoResourceData::load,GeoResourceData::new,"slimefun_geo"); }
    public OptionalInt supply(ChunkPos pos,GeoResources resource) {
        var values=chunks.get(pos);return values==null||!values.containsKey(resource)?OptionalInt.empty():OptionalInt.of(values.get(resource));
    }
    public Map<GeoResources,Integer> scan(ServerLevel level,BlockPos scanner) {
        if(!level.getServer().isSameThread()||this!=get(level))throw new IllegalStateException("GEO scan requires its owning server level thread");
        if(!level.hasChunkAt(scanner)) throw new IllegalArgumentException("Cannot scan an unloaded chunk");
        var pos=new ChunkPos(scanner);if(!generating.add(pos))throw new IllegalStateException("Recursive GEO scan");
        var values=chunks.computeIfAbsent(pos,p->new LinkedHashMap<>());
        var sample=new BlockPos(pos.getMinBlockX(),scanner.getY(),pos.getMinBlockZ());
        try{for(var resource:GeoResources.resources().values()) {
            if(values.containsKey(resource))continue;
            var event=new GeoResourceGenerationEvent(level,pos,level.getBiome(sample),resource,resource.generate(level,sample));
            // Commit the generated base before listeners, so a throwing listener cannot fire generation twice.
            values.put(resource,event.getValue());setDirty();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
            values.put(resource,event.getValue());
        }}finally{generating.remove(pos);}
        setDirty();
        for(var entity:level.getChunkAt(scanner).getBlockEntities().values())
            if(entity instanceof ProcessingEntity machine && machine.oilPump()) machine.recipesReloaded();
            else if(entity instanceof GeoMinerEntity miner) miner.wake();
        return Map.copyOf(values);
    }
    /** Commit one already-scanned unit; never generate or load a chunk to satisfy extraction. */
    public boolean extractOne(ServerLevel level,BlockPos pos,GeoResources resource) {
        if(!level.getServer().isSameThread() || this!=get(level)) throw new IllegalStateException("GEO extraction requires its owning server level thread");
        if(!level.hasChunkAt(pos)) return false;
        var chunk=new ChunkPos(pos);if(generating.contains(chunk))return false;
        var values=chunks.get(chunk);if(values==null||values.getOrDefault(resource,0)<=0)return false;
        values.put(resource,values.get(resource)-1); setDirty(); return true;
    }
    public void refundOne(ServerLevel level,BlockPos pos,GeoResources resource) {
        if(!level.getServer().isSameThread() || this!=get(level) || !level.hasChunkAt(pos)) throw new IllegalStateException("GEO refund requires its loaded owning level thread");
        var values=chunks.get(new ChunkPos(pos));
        if(values==null||!values.containsKey(resource)||generating.contains(new ChunkPos(pos)))throw new IllegalStateException("Reserved GEO resource was never scanned or is generating");
        values.put(resource,Math.addExact(values.get(resource),1)); setDirty();
        for(var entity:level.getChunkAt(pos).getBlockEntities().values()) if(entity instanceof GeoMinerEntity miner) miner.wake();
    }
    public void set(ServerLevel level,ChunkPos chunk,GeoResources resource,int amount){
        if(!level.getServer().isSameThread()||this!=get(level))throw new IllegalStateException("GEO write requires its owning server level thread");
        if(amount<0||generating.contains(chunk))throw new IllegalArgumentException("Invalid or generating GEO reserve");
        if(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null)throw new IllegalArgumentException("Cannot write unloaded GEO reserve");
        chunks.computeIfAbsent(chunk,p->new LinkedHashMap<>()).put(resource,amount);setDirty();
        for(var entity:level.getChunkSource().getChunkNow(chunk.x,chunk.z).getBlockEntities().values())if(entity instanceof GeoMinerEntity miner)miner.wake();else if(entity instanceof ProcessingEntity machine&&machine.oilPump())machine.recipesReloaded();
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var list=new ListTag();
        chunks.entrySet().stream().sorted(Comparator.comparingLong(e->e.getKey().toLong())).forEach(entry->{
            var chunk=new CompoundTag(); chunk.putInt("x",entry.getKey().x); chunk.putInt("z",entry.getKey().z);
            var reserves=new CompoundTag();entry.getValue().forEach((resource,amount)->reserves.putInt(resource.id.toString(),amount));chunk.put("reserves",reserves);list.add(chunk);
        });
        tag.put("chunks",list); return tag;
    }
    private static GeoResourceData load(CompoundTag tag) {
        var data=new GeoResourceData();
        for(var value:tag.getList("chunks",Tag.TAG_COMPOUND)) {
            var chunk=(CompoundTag)value;var counts=new LinkedHashMap<GeoResources,Integer>();
            if(!chunk.contains("x",Tag.TAG_INT)||!chunk.contains("z",Tag.TAG_INT)||!chunk.contains("reserves",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid GEO chunk format");
            var reserves=chunk.getCompound("reserves");for(var id:reserves.getAllKeys()){
                if(!id.contains(":")||!reserves.contains(id,Tag.TAG_INT)||reserves.getInt(id)<0)throw new IllegalArgumentException("Invalid GEO reserve "+id);
                counts.put(GeoResources.require(new net.minecraft.resources.ResourceLocation(id)),reserves.getInt(id));
            }
            if(data.chunks.put(new ChunkPos(chunk.getInt("x"),chunk.getInt("z")),counts)!=null) throw new IllegalArgumentException("Duplicate GEO chunk");
        }
        return data;
    }
}
