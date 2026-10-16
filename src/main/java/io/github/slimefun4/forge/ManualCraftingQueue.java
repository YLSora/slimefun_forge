package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
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

/** Paid manual crafts persist with the vanilla dispenser, never in a global world save or timer. */
public final class ManualCraftingQueue implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<ManualCraftingQueue> CAPABILITY=CapabilityManager.get(new CapabilityToken<>() {});
    private static final Map<ServerLevel,Set<ManualCraftingQueue>> ACTIVE=new IdentityHashMap<>();
    private static final class Pending {
        final long readyAt;
        final List<ItemStack> outputs;
        final String station;
        final BlockPos effectPos;
        final UUID actor;
        int nextStep;
        Pending(long readyAt,List<ItemStack> outputs,String station,BlockPos effectPos,UUID actor,int nextStep) {
            this.readyAt=readyAt; this.outputs=outputs; this.station=station;
            this.effectPos=effectPos.immutable(); this.actor=actor; this.nextStep=nextStep;
        }
    }
    private final DispenserBlockEntity owner;
    private final List<Pending> pending=new ArrayList<>();
    private LazyOptional<ManualCraftingQueue> capability=LazyOptional.of(() -> this);
    private boolean invalidated;

    private ManualCraftingQueue(DispenserBlockEntity owner) { this.owner=owner; }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterCapabilitiesEvent event) -> event.register(ManualCraftingQueue.class));
        var bus=MinecraftForge.EVENT_BUS;
        bus.addGenericListener(BlockEntity.class,ManualCraftingQueue::attach);
        bus.addListener(ManualCraftingQueue::loaded);
        bus.addListener(ManualCraftingQueue::tick);
        bus.addListener((LevelEvent.Unload event) -> { if(event.getLevel() instanceof ServerLevel level) ACTIVE.remove(level); });
        bus.addListener((ServerStoppedEvent event) -> ACTIVE.keySet().removeIf(level -> level.getServer()==event.getServer()));
    }
    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if(event.getObject() instanceof DispenserBlockEntity dispenser && !(dispenser instanceof IgnitionChamberEntity)) {
            var queue=new ManualCraftingQueue(dispenser);
            event.addCapability(SlimefunForge.key("manual_crafts"),queue);
            event.addListener(queue::invalidate);
        }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side) {
        if(cap==CAPABILITY && invalidated && !owner.isRemoved()) {
            invalidated=false; capability=LazyOptional.of(() -> this); activate();
        }
        return cap==CAPABILITY ? capability.cast() : LazyOptional.empty();
    }
    private void activate() {
        if(!invalidated && !pending.isEmpty() && owner.getLevel() instanceof ServerLevel level)
            ACTIVE.computeIfAbsent(level,key -> new LinkedHashSet<>()).add(this);
    }
    private void deactivate() {
        if(owner.getLevel() instanceof ServerLevel level) {
            var entries=ACTIVE.get(level);
            if(entries!=null) { entries.remove(this); if(entries.isEmpty()) ACTIVE.remove(level); }
        }
    }
    private void invalidate() {
        if(invalidated) return;
        invalidated=true;
        deactivate();
        capability.invalidate();
        // Forge unload invalidates before setRemoved; a second invalidation must not drop the saved queue.
        if(owner.isRemoved() && owner.getLevel() instanceof ServerLevel level) {
            for(var work:pending) for(var stack:work.outputs) drop(level,stack);
            pending.clear();
        }
    }
    private static void loaded(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        // Chunk load may precede FULL status. Only inspect its block entities on the server thread afterwards.
        level.getServer().execute(() -> {
            if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk) return;
            for(var pos:chunk.getBlockEntitiesPos()) {
                var entity=chunk.getBlockEntity(pos);
                if(entity instanceof DispenserBlockEntity) entity.getCapability(CAPABILITY).ifPresent(ManualCraftingQueue::activate);
            }
        });
    }
    private static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var active=ACTIVE.get(level);
        if(active==null) return;
        for(var queue:List.copyOf(active)) {
            if(!level.hasChunkAt(queue.owner.getBlockPos()) || queue.owner.isRemoved()) continue;
            var iterator=queue.pending.iterator();
            boolean changed=false;
            while(iterator.hasNext()) {
                var work=iterator.next();
                long elapsed=level.getGameTime()-(work.readyAt-60);
                int step=(int)Math.min(3,elapsed/20);
                if(elapsed>=0 && step>=work.nextStep) {
                    // Resume at the current step; never burst-replay effects missed while unloaded.
                    ManualCraftEffects.play(level,work.station,work.effectPos,work.actor,step);
                    work.nextStep=step+1; changed=true;
                }
                if(work.readyAt>level.getGameTime()) continue;
                queue.deliver(level,work.outputs);
                iterator.remove(); changed=true;
            }
            if(changed) queue.owner.setChanged();
            if(queue.pending.isEmpty()) queue.deactivate();
        }
    }
    public static boolean start(DispenserBlockEntity input,MachineRecipe recipe,ServerPlayer player,BlockPos clicked,ItemStack... extras) {
        if(!(input.getLevel() instanceof ServerLevel level)) return false;
        var queue=input.getCapability(CAPABILITY).resolve().orElse(null);
        var plan=recipe.prepare(input,extras);
        if(queue==null || plan==null) return false;
        boolean fits=false;
        for(var target:queue.targets(level)) {
            var candidate=target==input && recipe.shaped ? plan.input() : target;
            if(fits(candidate,plan.outputs())) { fits=true; break; }
        }
        if(!fits) return false;
        var effectPos=recipe.station.equals("armor_forge")?input.getBlockPos():clicked;
        queue.pending.add(new Pending(level.getGameTime()+60,plan.outputs(),recipe.station,effectPos,player.getUUID(),1));
        MachineRecipe.commit(input,plan.input());
        ManualCraftEffects.play(level,recipe.station,effectPos,player.getUUID(),0);
        queue.activate();
        return true;
    }
    private List<Container> targets(ServerLevel level) {
        List<Container> targets=new ArrayList<>();
        for(var direction:List.of(Direction.UP,Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            var pos=owner.getBlockPos().relative(direction);
            var chest=OutputChestBlock.outputAt(level,pos);
            if(chest!=null) targets.add(chest);
        }
        targets.add(owner);
        return targets;
    }
    private static boolean fits(Container target,List<ItemStack> outputs) {
        var planned=MachineRecipe.copy(target);
        for(var stack:outputs) if(!planned.addItem(stack.copy()).isEmpty()) return false;
        return true;
    }
    private void deliver(ServerLevel level,List<ItemStack> outputs) {
        for(var target:targets(level)) {
            var planned=MachineRecipe.copy(target);
            boolean fits=true;
            for(var stack:outputs) if(!planned.addItem(stack.copy()).isEmpty()) { fits=false; break; }
            if(fits) { MachineRecipe.commit(target,planned); return; }
        }
        // RC-37 attempts partial insertion into the dispenser before spawning overflow.
        var planned=MachineRecipe.copy(owner);
        for(var stack:outputs) {
            var remaining=planned.addItem(stack.copy());
            if(!remaining.isEmpty()) drop(level,remaining);
        }
        MachineRecipe.commit(owner,planned);
    }
    private void drop(ServerLevel level,ItemStack stack) {
        var pos=owner.getBlockPos();
        Containers.dropItemStack(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,stack.copy());
    }
    @Override public CompoundTag serializeNBT() {
        var tag=new CompoundTag();
        if(pending.isEmpty()) return tag;
        var list=new ListTag();
        for(var work:pending) {
            var entry=new CompoundTag(); entry.putLong("ready_at",work.readyAt);
            entry.putString("station",work.station); entry.putLong("effect_pos",work.effectPos.asLong());
            entry.putUUID("actor",work.actor); entry.putInt("next_step",work.nextStep);
            var items=new ListTag();
            for(var stack:work.outputs) items.add(stack.save(new CompoundTag()));
            entry.put("outputs",items); list.add(entry);
        }
        tag.put("pending",list);
        return tag;
    }
    @Override public void deserializeNBT(CompoundTag tag) {
        pending.clear();
        for(var value:tag.getList("pending",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)value;
            List<ItemStack> outputs=new ArrayList<>();
            for(var item:entry.getList("outputs",Tag.TAG_COMPOUND)) {
                var stack=ItemStack.of((CompoundTag)item);
                if(!stack.isEmpty()) outputs.add(stack);
            }
            if(!outputs.isEmpty()) pending.add(new Pending(entry.getLong("ready_at"),List.copyOf(outputs),
                entry.getString("station"),BlockPos.of(entry.getLong("effect_pos")),entry.getUUID("actor"),entry.getInt("next_step")));
        }
        // load() can also replace NBT on an already live dispenser; normal chunk loading activates via its event.
        if(owner.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) {
            deactivate(); activate();
        }
    }
}
