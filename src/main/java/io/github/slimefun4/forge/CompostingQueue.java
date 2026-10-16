package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.Containers;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Paid composting and panning survive source removal and sleep with their owning chunk. */
public final class CompostingQueue implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<CompostingQueue> CAPABILITY=CapabilityManager.get(new CapabilityToken<>() {});
    private static final Map<ServerLevel,Map<Long,CompostingQueue>> ACTIVE=new IdentityHashMap<>();
    private static final class Work {
        final BlockPos source;
        final ItemStack input,output;
        final UUID actor;
        final long readyAt;
        boolean panning;
        int nextEffect;
        Work(BlockPos source,ItemStack input,ItemStack output,UUID actor,long readyAt,int nextEffect) { this.source=source.immutable(); this.input=input.copy(); this.output=output.copy(); this.actor=actor; this.readyAt=readyAt; this.nextEffect=nextEffect; }
    }
    private final LevelChunk owner;
    private final List<Work> pending=new ArrayList<>();
    private LazyOptional<CompostingQueue> capability=LazyOptional.of(()->this);
    private boolean invalidated;
    private CompostingQueue(LevelChunk owner) { this.owner=owner; }
    static void register(IEventBus bus) {
        bus.addListener((RegisterCapabilitiesEvent event)->event.register(CompostingQueue.class));
        MinecraftForge.EVENT_BUS.addGenericListener(LevelChunk.class,CompostingQueue::attach);
        MinecraftForge.EVENT_BUS.addListener(CompostingQueue::loaded);
        MinecraftForge.EVENT_BUS.addListener(CompostingQueue::tick);
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Unload event)-> { if(event.getLevel() instanceof ServerLevel level) { var active=ACTIVE.get(level); if(active!=null) { var queue=active.get(event.getChunk().getPos().toLong()); if(queue!=null) queue.invalidate(); } } });
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload event)-> { if(event.getLevel() instanceof ServerLevel level) ACTIVE.remove(level); });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event)->ACTIVE.keySet().removeIf(level->level.getServer()==event.getServer()));
    }
    private static void attach(AttachCapabilitiesEvent<LevelChunk> event) {
        if(event.getObject().getLevel() instanceof ServerLevel) { var queue=new CompostingQueue(event.getObject()); event.addCapability(SlimefunForge.key("composting"),queue); event.addListener(queue::invalidate); }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side) {
        if(cap==CAPABILITY && invalidated && owner.getLevel() instanceof ServerLevel level && level.getChunkSource().getChunkNow(owner.getPos().x,owner.getPos().z)==owner) { invalidated=false; capability=LazyOptional.of(()->this); activate(); }
        return cap==CAPABILITY?capability.cast():LazyOptional.empty();
    }
    private void activate() { if(!invalidated && !pending.isEmpty() && owner.getLevel() instanceof ServerLevel level) ACTIVE.computeIfAbsent(level,key->new HashMap<>()).put(owner.getPos().toLong(),this); }
    private void deactivate() { if(owner.getLevel() instanceof ServerLevel level) { var active=ACTIVE.get(level); if(active!=null) { active.remove(owner.getPos().toLong(),this); if(active.isEmpty()) ACTIVE.remove(level); } } }
    private void invalidate() { if(!invalidated) { invalidated=true; deactivate(); capability.invalidate(); } }
    private static void loaded(ChunkEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) level.getServer().execute(()-> { if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)==chunk) chunk.getCapability(CAPABILITY).ifPresent(CompostingQueue::activate); });
    }
    void start(BlockPos pos,ItemStack input,ItemStack output,UUID actor) { pending.add(new Work(pos,input,output,actor,owner.getLevel().getGameTime()+320,1)); owner.setUnsaved(true); activate(); }
    void startPanning(BlockPos pos,ItemStack input,ItemStack output,UUID actor) {
        var work=new Work(pos,input,output,actor,owner.getLevel().getGameTime()+120,1);work.panning=true;pending.add(work);owner.setUnsaved(true);activate();
    }
    private static void effect(ServerLevel level,Work work) { var block=work.input.getItem() instanceof BlockItem item?item.getBlock():Blocks.HAY_BLOCK; level.levelEvent(2001,work.panning?work.source.below():work.source,Block.getId(block.defaultBlockState())); }
    private static void deliver(ServerLevel level,Work work) {
        if(work.panning) {
            HandMachines.deliver(level,work.source.below(),work.source,work.output,work.actor);
            level.playSound(null,work.source,work.output.isEmpty()?SoundEvents.ARMOR_STAND_BREAK:SoundEvents.ARROW_HIT_PLAYER,SoundSource.BLOCKS,1,1);return;
        }
        var player=level.getServer().getPlayerList().getPlayer(work.actor);
        if(player!=null) player.playNotifySound(SoundEvents.ARROW_HIT_PLAYER,SoundSource.PLAYERS,1F,1F);
        for(var direction:List.of(Direction.UP,Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            var target=OutputChestBlock.outputAt(level,work.source.relative(direction)); if(target==null) continue;
            var planned=MachineRecipe.copy(target);
            if(planned.addItem(work.output.copy()).isEmpty()) { MachineRecipe.commit(target,planned); return; }
        }
        io.github.slimefun4.forge.api.ItemSpawnApi.reserve(level,work.actor,io.github.slimefun4.forge.api.ItemSpawnApi.Reason.MULTIBLOCK_MACHINE_OVERFLOW,net.minecraft.world.phys.Vec3.atCenterOf(work.source.above()),work.output);
    }
    private static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var active=ACTIVE.get(level); if(active==null) return;
        for(var queue:List.copyOf(active.values())) {
            if(level.getChunkSource().getChunkNow(queue.owner.getPos().x,queue.owner.getPos().z)!=queue.owner) { queue.deactivate(); continue; }
            boolean changed=false; var completed=new ArrayList<Work>();
            for(var iterator=queue.pending.iterator();iterator.hasNext();) {
                var work=iterator.next();int duration=work.panning?120:320,interval=work.panning?20:30,steps=work.panning?5:10;
                long elapsed=level.getGameTime()-(work.readyAt-duration);
                if(elapsed>=duration) { iterator.remove(); completed.add(work); changed=true; }
                else if(elapsed>=(long)interval*work.nextEffect && work.nextEffect<=steps) { effect(level,work); work.nextEffect=(int)(elapsed/interval)+1; changed=true; }
            }
            if(changed) queue.owner.setUnsaved(true);
            if(queue.pending.isEmpty()) queue.deactivate();
            // Remove paid actions before notifying players or invoking container callbacks.
            for(var work:completed) deliver(level,work);
        }
    }
    @Override public CompoundTag serializeNBT() {
        var tag=new CompoundTag(); if(pending.isEmpty()) return tag; var list=new ListTag();
        for(var work:pending) { var entry=new CompoundTag(); entry.putLong("source",work.source.asLong()); entry.put("input",work.input.save(new CompoundTag())); entry.put("output",work.output.save(new CompoundTag())); entry.putUUID("actor",work.actor); entry.putLong("ready_at",work.readyAt); entry.putInt("next_effect",work.nextEffect);entry.putBoolean("panning",work.panning); list.add(entry); }
        tag.put("pending",list); return tag;
    }
    @Override public void deserializeNBT(CompoundTag tag) {
        var parsed=new ArrayList<Work>();
        if(tag.contains("pending") && !tag.contains("pending",Tag.TAG_LIST)) throw new IllegalArgumentException("Invalid composting queue");
        if(tag.get("pending") instanceof ListTag list && !list.isEmpty() && list.getElementType()!=Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid composting action list");
        for(var value:tag.getList("pending",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)value; var pos=BlockPos.of(entry.getLong("source")); var input=ItemStack.of(entry.getCompound("input")); var output=ItemStack.of(entry.getCompound("output")); long ready=entry.getLong("ready_at"); int next=entry.getInt("next_effect");
            boolean pan=entry.getBoolean("panning");
            if(!entry.contains("source",Tag.TAG_LONG) || !entry.contains("ready_at",Tag.TAG_LONG) || !entry.contains("next_effect",Tag.TAG_INT) || !entry.hasUUID("actor") || input.isEmpty() || (!pan&&output.getCount()!=1) || ready<(pan?120:320) || next<1 || next>(pan?6:11) || pos.getX()>>4!=owner.getPos().x || pos.getZ()>>4!=owner.getPos().z || owner.getLevel().isOutsideBuildHeight(pos) || owner.getLevel().isOutsideBuildHeight(pan?pos.below():pos.above())) throw new IllegalArgumentException("Invalid paid hand-processing action");
            var work=new Work(pos,input,output,entry.getUUID("actor"),ready,next);work.panning=pan;parsed.add(work);
        }
        deactivate(); pending.clear(); pending.addAll(parsed);
        if(owner.getLevel() instanceof ServerLevel level && level.getServer().isSameThread() && level.getChunkSource().getChunkNow(owner.getPos().x,owner.getPos().z)==owner) activate();
    }
}
