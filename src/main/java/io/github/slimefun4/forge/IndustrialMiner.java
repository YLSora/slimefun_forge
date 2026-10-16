package io.github.slimefun4.forge;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** A loaded-only RC-37 mining task, persisted on its vanilla blast furnace. */
public final class IndustrialMiner implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<IndustrialMiner> CAPABILITY=CapabilityManager.get(new CapabilityToken<>() {});
    private static final Map<ServerLevel,Set<IndustrialMiner>> ACTIVE=new IdentityHashMap<>();
    private static final Map<ServerLevel,Map<Long,Set<IndustrialMiner>>> WAITING=new IdentityHashMap<>();
    private static final int[] WARM_DELAYS={4,10,8,10,0,6,9,4,7,3,5,2,4,1,3,1,2,1};
    private static final int[] WORK_DELAYS={1,3,1,3,0};
    public record Structure(boolean advanced,Direction side) {
        public int range() { return advanced?5:3; }
        public String research() { return advanced?"advanced_industrial_miner":"industrial_miner"; }
    }
    private final BlastFurnaceBlockEntity furnace;
    private LazyOptional<IndustrialMiner> capability=LazyOptional.of(()->this);
    private boolean invalidated,running,warming;
    private Structure structure;
    private UUID owner;
    private String ownerName="",lastStop="";
    private int x,z,fuel,ores,step;
    private long dueAt;
    private Long waitingChunk;
    private long actions,scans,inspectedBlocks;
    private final io.github.slimefun4.forge.api.MachineApi.Processor processor;

    private IndustrialMiner(BlastFurnaceBlockEntity furnace) {this.furnace=furnace;processor=AddonMachines.processor(furnace,()->running?operation(false):null,()->stop((ServerLevel)furnace.getLevel(),"cancelled"),()->stop((ServerLevel)furnace.getLevel(),"cancelled"));}
    public io.github.slimefun4.forge.api.MachineApi.Processor processor(){return processor;}
    private io.github.slimefun4.forge.api.MachineApi.Operation operation(boolean complete){int width=2*structure.range()+1;var pos=furnace.getBlockPos();int progress=(z-pos.getZ()+structure.range())*width+x-pos.getX()+structure.range();return new io.github.slimefun4.forge.api.MachineApi.Operation(SlimefunForge.key("industrial_mining"),serializeNBT(),width*width,complete?width*width:progress,List.of(),List.of());}
    static void register(IEventBus modBus) {
        modBus.addListener((RegisterCapabilitiesEvent event)->event.register(IndustrialMiner.class));
        var bus=MinecraftForge.EVENT_BUS;
        bus.addGenericListener(BlockEntity.class,IndustrialMiner::attach);
        bus.addListener((ChunkEvent.Load event)->loaded(event));
        bus.addListener(IndustrialMiner::tick);
        bus.addListener((LevelEvent.Unload event)-> { if(event.getLevel() instanceof ServerLevel level) { ACTIVE.remove(level); WAITING.remove(level); } });
        bus.addListener((ServerStoppedEvent event)-> {
            ACTIVE.keySet().removeIf(level->level.getServer()==event.getServer());
            WAITING.keySet().removeIf(level->level.getServer()==event.getServer());
        });
    }
    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if(event.getObject() instanceof BlastFurnaceBlockEntity furnace) {
            var miner=new IndustrialMiner(furnace);
            event.addCapability(SlimefunForge.key("industrial_miner"),miner);
            event.addListener(miner::invalidate);
        }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side) {
        if(cap==CAPABILITY && invalidated && !furnace.isRemoved()) {
            invalidated=false; capability=LazyOptional.of(()->this); activate();
        }
        return cap==CAPABILITY?capability.cast():LazyOptional.empty();
    }
    private void invalidate() { invalidated=true; unindex(); capability.invalidate(); }
    private void unindex() {
        if(!(furnace.getLevel() instanceof ServerLevel level)) return;
        var active=ACTIVE.get(level);
        if(active!=null) { active.remove(this); if(active.isEmpty()) ACTIVE.remove(level); }
        var waiting=WAITING.get(level);
        if(waiting!=null && waitingChunk!=null) {
            var miners=waiting.get(waitingChunk);
            if(miners!=null) { miners.remove(this); if(miners.isEmpty()) waiting.remove(waitingChunk); }
            if(waiting.isEmpty()) WAITING.remove(level);
        }
        waitingChunk=null;
    }
    private void activate() {
        unindex();
        if(running && !invalidated && !furnace.isRemoved() && furnace.getLevel() instanceof ServerLevel level)
            ACTIVE.computeIfAbsent(level,key->new LinkedHashSet<>()).add(this);
    }
    private void waitFor(ServerLevel level,long chunk) {
        unindex(); waitingChunk=chunk;
        WAITING.computeIfAbsent(level,key->new HashMap<>()).computeIfAbsent(chunk,key->new LinkedHashSet<>()).add(this);
    }
    private static void loaded(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        level.getServer().execute(()-> {
            if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk) return;
            var waiting=WAITING.get(level);
            if(waiting!=null) {
                var miners=waiting.get(chunk.getPos().toLong());
                if(miners!=null) for(var miner:List.copyOf(miners)) miner.activate();
            }
            for(var pos:chunk.getBlockEntitiesPos()) if(chunk.getBlockEntity(pos) instanceof BlastFurnaceBlockEntity be)
                be.getCapability(CAPABILITY).ifPresent(IndustrialMiner::activate);
        });
    }
    private static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var active=ACTIVE.get(level);
        if(active==null) return;
        for(var miner:List.copyOf(active))try(var sample=Timings.start(level,miner.furnace.getBlockPos(),miner.structure.research())){miner.advance(level);}
    }
    private static boolean loaded(Level level,BlockPos pos) {
        return level instanceof ServerLevel server?server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null:level.hasChunkAt(pos);
    }
    private static boolean is(Level level,BlockPos pos,Block block) { return loaded(level,pos) && level.getBlockState(pos).is(block); }
    public static Structure detect(Level level,BlockPos pos) {
        if(!is(level,pos,Blocks.BLAST_FURNACE) || !is(level,pos.above(),Blocks.CHEST)) return null;
        for(boolean advanced:new boolean[]{false,true}) for(var side:List.of(Direction.NORTH,Direction.WEST)) {
            var base=advanced?Blocks.DIAMOND_BLOCK:Blocks.IRON_BLOCK;
            if(is(level,pos.relative(side),base) && is(level,pos.relative(side.getOpposite()),base)
                && is(level,pos.above().relative(side),Blocks.PISTON) && is(level,pos.above().relative(side.getOpposite()),Blocks.PISTON))
                return new Structure(advanced,side);
        }
        return null;
    }
    static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(event.getHand()!=InteractionHand.MAIN_HAND || detect(event.getLevel(),event.getPos())==null) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        if(event.getEntity() instanceof ServerPlayer player && event.getLevel().getBlockEntity(event.getPos()) instanceof BlastFurnaceBlockEntity be)
            be.getCapability(CAPABILITY).ifPresent(miner->miner.start(player));
    }
    public boolean start(ServerPlayer player) {
        if(!(furnace.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
            || player.level()!=level || invalidated || furnace.isRemoved()) return false;
        if(running) { Messages.chat(player,Component.translatable("message.slimefun.miner.already_running")); return false; }
        var found=detect(level,furnace.getBlockPos());
        if(found==null || !level.mayInteract(player,furnace.getBlockPos()) || !player.mayBuild()) return false;
        if(!ItemAccess.canUse(player,found.research())) { ItemAccess.denied(player,found.research()); return false; }
        structure=found; owner=player.getUUID(); ownerName=player.getGameProfile().getName();
        x=furnace.getBlockPos().getX()-found.range(); z=furnace.getBlockPos().getZ()-found.range();
        fuel=0; ores=0; step=0; warming=true; running=true; lastStop="";
        dueAt=level.getGameTime()+WARM_DELAYS[0]; furnace.setChanged(); activate(); return true;
    }
    private BlockPos piston(int index) { return furnace.getBlockPos().above().relative(index==0?structure.side:structure.side.getOpposite()); }
    private boolean available(ServerLevel level) {
        for(var pos:List.of(furnace.getBlockPos(),piston(0),piston(1),new BlockPos(x,furnace.getBlockPos().getY(),z)))
            if(!loaded(level,pos)) { waitFor(level,new ChunkPos(pos).toLong()); return false; }
        return true;
    }
    private ChestBlockEntity chest(ServerLevel level) {
        var pos=furnace.getBlockPos().above();
        return is(level,pos,Blocks.CHEST) && level.getBlockEntity(pos) instanceof ChestBlockEntity chest?chest:null;
    }
    private boolean valid(ServerLevel level) {
        if(invalidated || furnace.isRemoved() || level.getBlockEntity(furnace.getBlockPos())!=furnace) { stop(level,"destroyed"); return false; }
        var base=structure.advanced?Blocks.DIAMOND_BLOCK:Blocks.IRON_BLOCK;
        if(chest(level)==null || !is(level,piston(0).below(),base) || !is(level,piston(1).below(),base)) { stop(level,"destroyed"); return false; }
        for(int i=0;i<2;i++) {
            var state=level.getBlockState(piston(i)); var head=level.getBlockState(piston(i).above());
            if(!state.is(Blocks.PISTON)) { stop(level,"destroyed"); return false; }
            if(state.getValue(PistonBaseBlock.FACING)!=Direction.UP) { stop(level,"piston_facing"); return false; }
            if(!head.isAir() && !(head.is(Blocks.PISTON_HEAD) && head.getValue(PistonHeadBlock.FACING)==Direction.UP && head.getValue(PistonHeadBlock.TYPE)==PistonType.DEFAULT)) {
                stop(level,"piston_space"); return false;
            }
        }
        return true;
    }
    private void advance(ServerLevel level) {
        if(!running || invalidated || furnace.isRemoved()) { unindex(); return; }
        if(!available(level) || dueAt>level.getGameTime() || !valid(level)) return;
        // Only adjacent zero-delay nodes share a tick. Never catch up unloaded positive delays.
        do {
            actions++;
            if(warming) {
                if(step==4) { if(!refuel(level)) return; }
                else if(step==17) { warming=false; step=0; dueAt=level.getGameTime()+WORK_DELAYS[0]; furnace.setChanged(); return; }
                else { int animation=step<4?step:step-1; animate(level,(animation/2)%2,animation%2==0); }
                if(!running) return;
                step++; dueAt=level.getGameTime()+WARM_DELAYS[step];
            } else {
                if(step==4) { scan(level); furnace.setChanged(); return; }
                animate(level,step/2,step%2==0);
                if(!running) return;
                step++; dueAt=level.getGameTime()+WORK_DELAYS[step];
            }
            furnace.setChanged();
        } while(running && dueAt<=level.getGameTime());
    }
    private void animate(ServerLevel level,int index,boolean extended) {
        var pos=piston(index); var center=furnace.getBlockPos();
        // Suppress vanilla neighbour-driven piston movement; only replace an empty/legal head.
        level.setBlock(pos,level.getBlockState(pos).setValue(PistonBaseBlock.EXTENDED,extended),18);
        level.setBlock(pos.above(),extended?Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING,Direction.UP):Blocks.AIR.defaultBlockState(),18);
        level.sendParticles(ParticleTypes.SMOKE,center.getX(),center.getY(),center.getZ(),20,0.7,0.7,0.7,0);
        level.playSound(null,pos,extended?SoundEvents.PISTON_EXTEND:SoundEvents.PISTON_CONTRACT,SoundSource.BLOCKS,0.1F,1);
    }
    private int planFuel(SimpleContainer planned) {
        for(int slot=0;slot<planned.getContainerSize();slot++) {
            int amount=MiningResources.fuel(planned.getItem(slot),structure.advanced);
            if(amount==0) continue;
            planned.getItem(slot).shrink(1);
            if(structure.advanced && !planned.addItem(new ItemStack(Items.BUCKET)).isEmpty()) return -1;
            return amount;
        }
        return 0;
    }
    private boolean refuel(ServerLevel level) {
        var chest=chest(level);
        if(chest==null) { stop(level,"destroyed"); return false; }
        var planned=MachineRecipe.copy(chest); int amount=planFuel(planned);
        if(amount<=0) { stop(level,amount<0?"full_chest":"no_fuel"); return false; }
        MachineRecipe.commit(chest,planned); fuel=amount; return true;
    }
    private ServerPlayer actor(ServerLevel level) {
        var online=level.getServer().getPlayerList().getPlayer(owner);
        if(online!=null && online.level()==level) return online;
        var actor=FakePlayerFactory.get(level,new GameProfile(owner,ownerName));
        actor.setGameMode(GameType.SURVIVAL);
        actor.setPos(furnace.getBlockPos().getX()+0.5,furnace.getBlockPos().getY(),furnace.getBlockPos().getZ()+0.5);
        return actor;
    }
    private void scan(ServerLevel level) {
        scans++;
        var actor=actor(level); var center=furnace.getBlockPos();
        level.levelEvent(2001,center,Block.getId(Blocks.STONE.defaultBlockState()));
        for(int y=center.getY()-1;y>level.getMinBuildHeight();y--) {
            var pos=new BlockPos(x,y,z); var state=level.getBlockState(pos); inspectedBlocks++;
            // A native protection hook for EVERY inspected block, including non-ores.
            if(!actor.mayBuild() || !level.mayInteract(actor,pos) || !level.getWorldBorder().isWithinBounds(pos)
                || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,state,actor))) { stop(level,"no_permission"); return; }
            if(!running || !available(level) || !valid(level)) return;
            if(!level.getBlockState(pos).equals(state)) { nextScan(level,4); return; }
            if(state.hasBlockEntity() || !MiningResources.canMine(state.getBlock(),structure.advanced)) continue;
            var chest=chest(level);var before=MachineRecipe.copy(chest);var planned=MachineRecipe.copy(before);int oldFuel=fuel;
            int availableFuel=fuel>0?fuel:planFuel(planned);
            if(availableFuel<=0) { stop(level,availableFuel<0?"full_chest":"no_fuel"); return; }
            if(!ItemAccess.enabled(level,new ItemStack(state.getBlock())))continue;
            var outputs=MiningResources.outcome(level,pos,state,structure.advanced,actor);
            for(var output:outputs)if(!ItemAccess.enabled(level,output)||!planned.addItem(output.copy()).isEmpty()) { stop(level,"full_chest"); return; }
            if(!running||!available(level)||!valid(level)||!level.getBlockState(pos).equals(state)||chest(level)!=chest)return;
            if(fuel!=oldFuel)return;
            for(int slot=0;slot<before.getContainerSize();slot++)if(!ItemStack.matches(before.getItem(slot),chest.getItem(slot)))return;
            // Physical vanilla chest commits do not call foreign inventory handlers. All mutation is on this thread.
            MachineRecipe.commit(chest,planned);
            if(!level.setBlock(pos,Blocks.AIR.defaultBlockState(),3)) { MachineRecipe.commit(chest,before); stop(level,"destroyed"); return; }
            fuel=availableFuel-1; ores++;
            level.levelEvent(2001,center,Block.getId(state));
            level.playSound(null,center,SoundEvents.ARROW_HIT_PLAYER,SoundSource.BLOCKS,0.2F,1);
            nextScan(level,4); return;
        }
        int range=structure.range();
        if(x<center.getX()+range) x++;
        else if(z<center.getZ()+range) { x=center.getX()-range; z++; }
        else {
            stop(level,"finished");
            var online=level.getServer().getPlayerList().getPlayer(owner);
            if(online!=null) online.playNotifySound(SoundEvents.ARROW_HIT_PLAYER,SoundSource.PLAYERS,0.4F,1);
            return;
        }
        nextScan(level,5);
    }
    private void nextScan(ServerLevel level,int delay) { step=0; dueAt=level.getGameTime()+delay+WORK_DELAYS[0]; }
    private void message(ServerLevel level,String reason) {
        var player=owner==null?null:level.getServer().getPlayerList().getPlayer(owner);
        if(player!=null) Messages.chat(player,Component.translatable("message.slimefun.miner."+reason,ores));
    }
    private void stop(ServerLevel level,String reason) {
        var completed=running&&reason.equals("finished")?operation(true):null;
        running=false;lastStop=reason;unindex();furnace.setChanged();if(!reason.equals("cancelled"))message(level,reason);
        if(completed!=null)MinecraftForge.EVENT_BUS.post(new MachineOperationFinishEvent(level,furnace,processor,completed));
    }
    public boolean running() { return running; }
    public int fuel() { return fuel; }
    public int ores() { return ores; }
    public String lastStop() { return lastStop; }
    public long actions() { return actions; }
    public long scans() { return scans; }
    public long inspectedBlocks() { return inspectedBlocks; }
    public boolean waiting() { return waitingChunk!=null; }
    @Override public CompoundTag serializeNBT() {
        var tag=new CompoundTag();
        if(!running) return tag;
        tag.putUUID("owner",owner); tag.putString("owner_name",ownerName);
        tag.putBoolean("advanced",structure.advanced); tag.putString("side",structure.side.getName());
        tag.putInt("x",x); tag.putInt("z",z); tag.putInt("fuel",fuel); tag.putInt("ores",ores);
        tag.putBoolean("warming",warming); tag.putInt("step",step); tag.putLong("due_at",dueAt);
        return tag;
    }
    @Override public void deserializeNBT(CompoundTag tag) {
        unindex(); running=false;
        if(tag.hasUUID("owner")) {
            var side=Direction.byName(tag.getString("side"));
            int savedStep=tag.getInt("step"); boolean warm=tag.getBoolean("warming");
            var pos=furnace.getBlockPos(); int range=tag.getBoolean("advanced")?5:3;
            if((side==Direction.NORTH || side==Direction.WEST) && savedStep>=0 && savedStep<(warm?WARM_DELAYS.length:WORK_DELAYS.length)
                && Math.abs((long)tag.getInt("x")-pos.getX())<=range && Math.abs((long)tag.getInt("z")-pos.getZ())<=range
                && tag.getInt("fuel")>=0 && tag.getInt("fuel")<=256 && tag.getInt("ores")>=0) {
                owner=tag.getUUID("owner"); ownerName=tag.getString("owner_name"); structure=new Structure(tag.getBoolean("advanced"),side);
                x=tag.getInt("x"); z=tag.getInt("z"); fuel=tag.getInt("fuel"); ores=tag.getInt("ores");
                warming=warm; step=savedStep; dueAt=tag.getLong("due_at"); running=true;
            }
        }
        if(furnace.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) activate();
    }
}
