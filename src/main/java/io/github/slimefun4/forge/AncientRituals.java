package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** World-owned rituals keep running when their center or block identities are removed. */
public final class AncientRituals extends SavedData {
    private static final String FILE="slimefun_rituals";
    private final Map<UUID,Task> tasks=new LinkedHashMap<>();
    private final Set<BlockPos> locks=new HashSet<>();
    public static AncientRituals get(ServerLevel level){return level.getDataStorage().computeIfAbsent(AncientRituals::load,AncientRituals::new,FILE);}
    public boolean locked(BlockPos pos){return locks.contains(pos);}
    private Task current(BlockPos pos){return tasks.values().stream().filter(t->t.active&&t.pos.equals(pos)).findFirst().orElse(null);}
    public boolean running(BlockPos pos){return locks.contains(pos);}
    public CompoundTag snapshot(BlockPos pos){var task=current(pos);return task==null?new CompoundTag():task.save();}
    int stage(BlockPos pos){var task=current(pos);return task==null?0:task.stage;}
    long dueAt(BlockPos pos){var task=current(pos);return task==null?0:task.due;}
    void start(ServerPlayer player,BlockPos pos,ItemStack output,List<ItemEntity> offerings,List<ItemStack> inputs){
        var task=new Task();task.id=UUID.randomUUID();task.owner=player.getUUID();task.name=player.getGameProfile().getName();task.pos=pos.immutable();task.output=output.copy();task.expected=inputs.stream().map(ItemStack::copy).toList();task.delay=AncientAltarEntity.STEP_DELAY.get();task.due=player.serverLevel().getGameTime()+10;
        for(int i=0;i<8;i++){task.items[i]=offerings.get(i).getUUID();task.positions[i]=offerings.get(i).position();locks.add(AncientAltarEntity.pedestalAt(pos,i));}
        locks.add(pos.immutable());tasks.put(task.id,task);setDirty();
    }
    public void cancel(ServerLevel level,BlockPos pos){var task=current(pos);if(task!=null){abort(level,task);tasks.remove(task.id);}}
    public void advanceAt(ServerLevel level,BlockPos pos){for(var task:List.copyOf(tasks.values()))if(task.pos.equals(pos))advance(level,task);}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
        var data=level.getDataStorage().get(AncientRituals::load,FILE);if(data!=null)for(var task:List.copyOf(data.tasks.values()))data.advance(level,task);
    }
    public static void chunkReady(ServerLevel level,long chunk){var data=level.getDataStorage().get(AncientRituals::load,FILE);if(data!=null)for(var task:data.tasks.values())task.waiting.remove(chunk);}
    private void advance(ServerLevel level,Task task){
        if(task.finishing||!tasks.containsKey(task.id)||level.getGameTime()<task.due||!task.waiting.isEmpty())return;
        boolean ready=ready(level,task,task.pos);for(int i=0;i<8;i++)ready&=ready(level,task,AncientAltarEntity.pedestalAt(task.pos,i));if(!ready)return;
        do{
            particles(level,task);
            var chunk=level.getChunkSource().getChunkNow(task.pos.getX()>>4,task.pos.getZ()>>4);
            if(chunk!=null&&chunk.getBlockEntity(task.pos) instanceof AncientAltarEntity altar)altar.countAction();
            for(int i=0;i<8;i++)if(task.items[i]!=null){var entity=level.getEntity(task.items[i]);if(entity!=null&&entity.position().distanceToSqr(task.positions[i])>0.1){abort(level,task);tasks.remove(task.id);return;}}
            if(task.stage==36){finish(level,task);return;}
            if(task.stage>0&&task.stage%4==0){
                int index=task.stage/4-1;var pos=AncientAltarEntity.pedestalAt(task.pos,index);var item=AncientPedestalEntity.find(level,pos);
                if(item==null||!item.getUUID().equals(task.items[index]))abort(level,task);
                else{task.items[index]=null;task.consumed|=1<<index;item.discard();AncientAltarEntity.sound(level,pos,SoundEvents.ENDERMAN_TELEPORT,1,2);pedestalParticles(level,pos);}
            }
            task.stage++;task.due=level.getGameTime()+(long)task.delay;setDirty();
        }while(task.delay==0&&tasks.containsKey(task.id));
    }
    private static boolean ready(ServerLevel level,Task task,BlockPos pos){long chunk=new ChunkPos(pos).toLong();if(!level.hasChunkAt(pos)){task.waiting.add(chunk);return false;}return level.areEntitiesLoaded(chunk);}
    private void unlock(Task task){locks.remove(task.pos);for(int i=0;i<8;i++)locks.remove(AncientAltarEntity.pedestalAt(task.pos,i));setDirty();}
    private void abort(ServerLevel level,Task task){task.active=false;Arrays.fill(task.items,null);unlock(task);AncientAltarEntity.sound(level,task.pos,SoundEvents.ZOMBIE_ATTACK_IRON_DOOR,1,1);}
    private void finish(ServerLevel level,Task task){
        if(task.finishing)return;task.finishing=true;
        try{
            if(task.active){var event=new AncientAltarCraftEvent(level,task.pos,task.owner,task.name,task.output);if(!MinecraftForge.EVENT_BUS.post(event)&&task.active&&tasks.get(task.id)==task){
                AncientAltarEntity.sound(level,task.pos,SoundEvents.ZOMBIE_VILLAGER_CURE,1,1);level.levelEvent(2001,task.pos.above(),Block.getId(Blocks.EMERALD_BLOCK.defaultBlockState()));
                io.github.slimefun4.forge.api.ItemSpawnApi.reserve(level,task.owner,io.github.slimefun4.forge.api.ItemSpawnApi.Reason.MISC,new Vec3(task.pos.getX()+.5,task.pos.getY()+.8,task.pos.getZ()+.5),event.getOutput());
            }}else AncientAltarEntity.sound(level,task.pos,SoundEvents.ZOMBIE_ATTACK_IRON_DOOR,1,1);
        }finally{if(task.active)unlock(task);tasks.remove(task.id);task.finishing=false;setDirty();}
    }
    private static void particles(ServerLevel level,Task task){
        double x=task.pos.getX()+.5,y=task.pos.getY()+1.3,z=task.pos.getZ()+.5;level.sendParticles(ParticleTypes.WITCH,x,y,z,16,1.2,0,1.2,0);level.sendParticles(ParticleTypes.FIREWORK,x,y,z,8,.2,0,.2,0);
        for(int i=0;i<8;i++)if((task.consumed&(1<<i))!=0)pedestalParticles(level,AncientAltarEntity.pedestalAt(task.pos,i));
    }
    private static void pedestalParticles(ServerLevel level,BlockPos pos){level.sendParticles(ParticleTypes.ENCHANT,pos.getX()+.5,pos.getY()+1.5,pos.getZ()+.5,16,.3,.2,.3,0);level.sendParticles(ParticleTypes.ENCHANTED_HIT,pos.getX()+.5,pos.getY()+1.5,pos.getZ()+.5,8,.3,.2,.3,0);}
    @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();for(var task:tasks.values())list.add(task.save());tag.put("tasks",list);tag.putLongArray("locks",locks.stream().mapToLong(BlockPos::asLong).sorted().toArray());return tag;}
    private static AncientRituals load(CompoundTag tag){var data=new AncientRituals();for(var entry:tag.getList("tasks",Tag.TAG_COMPOUND)){var task=Task.load((CompoundTag)entry);if(data.tasks.put(task.id,task)!=null)throw new IllegalArgumentException("Duplicate altar ritual");}for(long pos:tag.getLongArray("locks"))data.locks.add(BlockPos.of(pos));return data;}
    private static final class Task {
        UUID id,owner;String name;BlockPos pos;ItemStack output;List<ItemStack> expected;final UUID[] items=new UUID[8];final Vec3[] positions=new Vec3[8];final Set<Long> waiting=new HashSet<>();int stage,delay,consumed;long due;boolean active=true,finishing;
        CompoundTag save(){
            var tag=new CompoundTag();tag.putUUID("ritual",id);tag.putUUID("owner",owner);tag.putString("owner_name",name);tag.putLong("position",pos.asLong());tag.put("output",output.save(new CompoundTag()));tag.putInt("stage",stage);tag.putInt("delay",delay);tag.putLong("due_at",due);tag.putInt("consumed",consumed);tag.putBoolean("active",active);
            var refs=new ListTag();for(int i=0;i<8;i++){var ref=new CompoundTag();if(items[i]!=null)ref.putUUID("item",items[i]);ref.putDouble("x",positions[i].x);ref.putDouble("y",positions[i].y);ref.putDouble("z",positions[i].z);refs.add(ref);}tag.put("offerings",refs);var inputs=new ListTag();for(var item:expected)inputs.add(item.save(new CompoundTag()));tag.put("expected",inputs);return tag;
        }
        static Task load(CompoundTag tag){
            var task=new Task();var refs=tag.getList("offerings",Tag.TAG_COMPOUND);var inputs=tag.getList("expected",Tag.TAG_COMPOUND);
            if(!tag.hasUUID("ritual")||!tag.hasUUID("owner")||refs.size()!=8||inputs.size()!=8||!tag.contains("position",Tag.TAG_LONG)||!tag.contains("stage",Tag.TAG_INT)||!tag.contains("delay",Tag.TAG_INT)||!tag.contains("due_at",Tag.TAG_LONG)||!tag.contains("active",Tag.TAG_BYTE))throw new IllegalArgumentException("Invalid altar ritual identity");
            task.id=tag.getUUID("ritual");task.owner=tag.getUUID("owner");task.name=tag.getString("owner_name");task.pos=BlockPos.of(tag.getLong("position"));task.output=ItemStack.of(tag.getCompound("output"));task.stage=tag.getInt("stage");task.delay=tag.getInt("delay");task.due=tag.getLong("due_at");task.consumed=tag.getInt("consumed");task.active=tag.getBoolean("active");
            var expected=new ArrayList<ItemStack>();for(int i=0;i<8;i++){var ref=refs.getCompound(i);task.items[i]=ref.hasUUID("item")?ref.getUUID("item"):null;task.positions[i]=new Vec3(ref.getDouble("x"),ref.getDouble("y"),ref.getDouble("z"));expected.add(ItemStack.of(inputs.getCompound(i)));if(!Double.isFinite(task.positions[i].lengthSqr()))throw new IllegalArgumentException("Invalid altar position lock");}task.expected=List.copyOf(expected);
            if(task.stage<0||task.stage>36||task.delay<0||task.consumed<0||task.consumed>255||task.output.isEmpty()||expected.stream().anyMatch(ItemStack::isEmpty))throw new IllegalArgumentException("Invalid altar ritual progress");return task;
        }
    }
}
