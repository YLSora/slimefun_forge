package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Liquid actions outlive the source crucible and persist with their target chunk. */
public final class CrucibleLiquids implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<CrucibleLiquids> CAPABILITY=CapabilityManager.get(new CapabilityToken<>() {});
    private static final Map<ServerLevel,Map<Long,CrucibleLiquids>> ACTIVE=new IdentityHashMap<>();
    private record Work(BlockPos target,boolean water,boolean place,int step) {}
    private final LevelChunk owner;
    private final TreeMap<Long,List<Work>> pending=new TreeMap<>();
    private final Map<BlockPos,Integer> targets=new HashMap<>();
    private LazyOptional<CrucibleLiquids> capability=LazyOptional.of(()->this);
    private boolean invalidated;
    private CrucibleLiquids(LevelChunk owner) { this.owner=owner; }
    static void register(IEventBus modBus) {
        modBus.addListener((RegisterCapabilitiesEvent event)->event.register(CrucibleLiquids.class));
        var bus=MinecraftForge.EVENT_BUS;
        bus.addGenericListener(LevelChunk.class,CrucibleLiquids::attach);
        bus.addListener(CrucibleLiquids::loaded);
        bus.addListener((ChunkEvent.Unload event)-> {
            if(event.getLevel() instanceof ServerLevel level) {
                var active=ACTIVE.get(level);
                if(active!=null) { var queue=active.get(event.getChunk().getPos().toLong()); if(queue!=null) queue.invalidate(); }
            }
        });
        bus.addListener(CrucibleLiquids::tick);
        bus.addListener((LevelEvent.Unload event)-> { if(event.getLevel() instanceof ServerLevel level) ACTIVE.remove(level); });
        bus.addListener((ServerStoppedEvent event)->ACTIVE.keySet().removeIf(level->level.getServer()==event.getServer()));
    }
    private static void attach(AttachCapabilitiesEvent<LevelChunk> event) {
        if(event.getObject().getLevel() instanceof ServerLevel) {
            var queue=new CrucibleLiquids(event.getObject());
            event.addCapability(SlimefunForge.key("crucible_liquids"),queue); event.addListener(queue::invalidate);
        }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side) {
        if(cap==CAPABILITY && invalidated && owner.getLevel() instanceof ServerLevel level
            && level.getChunkSource().getChunkNow(owner.getPos().x,owner.getPos().z)==owner) {
            invalidated=false; capability=LazyOptional.of(()->this); activate();
        }
        return cap==CAPABILITY?capability.cast():LazyOptional.empty();
    }
    private void activate() {
        if(!invalidated && !pending.isEmpty() && owner.getLevel() instanceof ServerLevel level)
            ACTIVE.computeIfAbsent(level,key->new HashMap<>()).put(owner.getPos().toLong(),this);
    }
    private void deactivate() {
        if(owner.getLevel() instanceof ServerLevel level) {
            var active=ACTIVE.get(level);
            if(active!=null) { active.remove(owner.getPos().toLong(),this); if(active.isEmpty()) ACTIVE.remove(level); }
        }
    }
    private void invalidate() { invalidated=true; deactivate(); capability.invalidate(); }
    private static void loaded(ChunkEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            level.getServer().execute(()-> {
                if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)==chunk)
                    chunk.getCapability(CAPABILITY).ifPresent(CrucibleLiquids::activate);
            });
    }
    public static boolean holds(Level level,BlockPos pos) {
        var active=ACTIVE.get(level);
        var queue=active==null?null:active.get(net.minecraft.world.level.ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));
        return queue!=null && queue.targets.containsKey(pos) && level.getBlockState(pos).getBlock() instanceof LiquidBlock;
    }
    private void add(long due,Work work) {
        pending.computeIfAbsent(due,key->new ArrayList<>()).add(work);
        targets.merge(work.target,1,Integer::sum);
    }
    void generate(BlockPos target,boolean water) {
        var level=(ServerLevel)owner.getLevel();
        if(water && level.dimension()==Level.NETHER && !CrucibleBlock.ALLOW_WATER_IN_NETHER.get()) {
            level.sendParticles(ParticleTypes.SMOKE,target.getX()+.5,target.getY()+.5,target.getZ()+.5,4,0,0,0,0);
            sound(level,target,SoundEvents.LAVA_EXTINGUISH); return;
        }
        var state=level.getBlockState(target); var fluid=water?Blocks.WATER:Blocks.LAVA;
        if(state.is(water?Blocks.LAVA:Blocks.WATER)) {
            int height=state.getValue(LiquidBlock.LEVEL);
            level.setBlockAndUpdate(target,(height==0 || height==8?Blocks.OBSIDIAN:Blocks.STONE).defaultBlockState());
            sound(level,target,SoundEvents.LAVA_EXTINGUISH); return;
        }
        long delay=50; int step=1; boolean place=!state.is(fluid);
        if(!place) {
            int height=state.getValue(LiquidBlock.LEVEL); if(height>7) height-=8;
            if(height==0) delay=1; else step=7-height;
        }
        add(level.getGameTime()+delay,new Work(target.immutable(),water,place,step)); owner.setUnsaved(true); activate();
    }
    private static void sound(ServerLevel level,BlockPos pos,SoundEvent sound) {
        level.playSound(null,pos.getX(),pos.getY(),pos.getZ(),sound,SoundSource.BLOCKS,1F,1F);
    }
    private void advance(ServerLevel level,Work work) {
        var state=level.getBlockState(work.target);
        var sound=work.water?SoundEvents.PLAYER_SPLASH:SoundEvents.LAVA_POP;
        if(work.place) {
            if(state.isAir()) {
                level.setBlock(work.target,(work.water?Blocks.WATER:Blocks.LAVA).defaultBlockState(),2);
                state=level.getBlockState(work.target);
            } else if(work.water && state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                level.setBlock(work.target,state.setValue(BlockStateProperties.WATERLOGGED,true),2); sound(level,work.target,sound); return;
            }
        }
        if(!(state.getBlock() instanceof LiquidBlock)) {
            // Forge block identity and its inventory stay intact when the target is obstructed.
            sound(level,work.target,SoundEvents.METAL_BREAK); return;
        }
        sound(level,work.target,sound);
        level.setBlock(work.target,state.setValue(LiquidBlock.LEVEL,8-work.step),2);
        if(work.step<8) add(level.getGameTime()+50,new Work(work.target,work.water,false,work.step+1));
        else sound(level,work.target,SoundEvents.ARROW_HIT_PLAYER);
    }
    private static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var active=ACTIVE.get(level); if(active==null) return;
        for(var queue:List.copyOf(active.values())) {
            if(level.getChunkSource().getChunkNow(queue.owner.getPos().x,queue.owner.getPos().z)!=queue.owner) { queue.deactivate(); continue; }
            if(queue.pending.isEmpty() || queue.pending.firstKey()>level.getGameTime()) continue;
            while(!queue.pending.isEmpty() && queue.pending.firstKey()<=level.getGameTime()) {
                for(var work:queue.pending.pollFirstEntry().getValue()) {
                    queue.targets.compute(work.target,(pos,count)->count==1?null:count-1);
                    queue.advance(level,work);
                    if(!queue.targets.containsKey(work.target)) {
                        var fluid=level.getFluidState(work.target);
                        if(!fluid.isEmpty()) level.scheduleTick(work.target,fluid.getType(),fluid.getType().getTickDelay(level));
                    }
                }
            }
            queue.owner.setUnsaved(true); if(queue.pending.isEmpty()) queue.deactivate();
        }
    }
    @Override public CompoundTag serializeNBT() {
        var tag=new CompoundTag(); if(pending.isEmpty()) return tag;
        var list=new ListTag();
        pending.forEach((due,works)-> { for(var work:works) {
            var entry=new CompoundTag(); entry.putLong("due",due); entry.putLong("target",work.target.asLong());
            entry.putBoolean("water",work.water); entry.putBoolean("place",work.place); entry.putInt("step",work.step); list.add(entry);
        } });
        tag.put("pending",list); return tag;
    }
    @Override public void deserializeNBT(CompoundTag tag) {
        var parsed=new TreeMap<Long,List<Work>>();
        if(tag.contains("pending") && !tag.contains("pending",Tag.TAG_LIST)) throw new IllegalArgumentException("Invalid crucible pending list");
        for(var value:tag.getList("pending",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)value; var target=BlockPos.of(entry.getLong("target")); long due=entry.getLong("due"); int step=entry.getInt("step");
            if(!entry.contains("target",Tag.TAG_LONG) || !entry.contains("due",Tag.TAG_LONG) || !entry.contains("step",Tag.TAG_INT)
                || !entry.contains("water",Tag.TAG_BYTE) || !entry.contains("place",Tag.TAG_BYTE) || due<0 || step<0 || step>8
                || target.getX()>>4!=owner.getPos().x || target.getZ()>>4!=owner.getPos().z || owner.getLevel().isOutsideBuildHeight(target)
                || (entry.getBoolean("place") && step!=1)) throw new IllegalArgumentException("Invalid crucible liquid action");
            parsed.computeIfAbsent(due,key->new ArrayList<>()).add(new Work(target,entry.getBoolean("water"),entry.getBoolean("place"),step));
        }
        deactivate(); pending.clear(); targets.clear();
        parsed.forEach((due,works)->works.forEach(work->add(due,work)));
        if(owner.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()
            && level.getChunkSource().getChunkNow(owner.getPos().x,owner.getPos().z)==owner) activate();
    }
}
