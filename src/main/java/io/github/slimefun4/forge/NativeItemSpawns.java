package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.*;
import io.github.slimefun4.forge.api.ItemSpawnApi.Reason;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;

/** Single ownership for paid outputs until their business event and native entity join both succeed. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=SlimefunForge.ID)
public final class NativeItemSpawns extends SavedData {
    private record Pending(UUID id,ResourceKey<Level> dimension,UUID owner,Reason reason,Vec3 at,ItemStack item){}
    private static final Set<ServerLevel> NOTIFYING=Collections.newSetFromMap(new WeakHashMap<>()),SPAWNING=Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<UUID,Pending> pending=new LinkedHashMap<>();
    private final Set<UUID> delivering=new HashSet<>();
    private static NativeItemSpawns get(MinecraftServer server){return NativeData.get(server,"slimefun_pending_spawns",NativeItemSpawns::load,NativeItemSpawns::new);}
    private static boolean available(ServerLevel level,Vec3 at){return SpecialTools.available(level,BlockPos.containing(at))&&level.areEntitiesLoaded(new ChunkPos(BlockPos.containing(at)).toLong());}
    public static ItemEntity prepare(ServerLevel level,ServerPlayer player,Reason reason,Vec3 at,ItemStack stack){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Item generation requires server thread");
        var event=new ItemSpawnEvent(level,player,reason,at,stack);
        if(player!=null&&player.server!=level.getServer())throw new IllegalArgumentException("Foreign item generation player");
        if(!available(level,at)||!NOTIFYING.add(level))return null;
        try{if(MinecraftForge.EVENT_BUS.post(event)||!available(level,event.getPosition())||!ItemAccess.enabled(level,event.getItem()))return null;
            var position=event.getPosition();var entity=new ItemEntity(level,position.x,position.y,position.z,event.getItem());entity.setDefaultPickUpDelay();return entity;
        }finally{NOTIFYING.remove(level);}
    }
    public static ItemEntity spawn(ServerLevel level,ServerPlayer player,Reason reason,Vec3 at,ItemStack stack,BooleanSupplier sourceValid){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Item generation requires server thread");
        Objects.requireNonNull(sourceValid);if(!SPAWNING.add(level))return null;
        try{var entity=prepare(level,player,reason,at,stack);if(entity==null)return null;
            return EventRules.commitSpawn(()->!entity.isRemoved()&&sourceValid.getAsBoolean(),()->level.addFreshEntity(entity),entity::discard)?entity:null;
        }finally{SPAWNING.remove(level);}
    }
    public static void reserve(ServerLevel level,UUID owner,Reason reason,Vec3 at,ItemStack stack){
        var validated=new ItemSpawnEvent(level,null,reason,at,stack);var data=get(level.getServer());var id=UUID.randomUUID();
        var output=new Pending(id,level.dimension(),owner,validated.getReason(),validated.getPosition(),validated.getItem());data.pending.put(id,output);data.setDirty();data.deliver(level.getServer(),output);
    }
    private void deliver(MinecraftServer server,Pending output){
        var level=server.getLevel(output.dimension);if(level==null||!available(level,output.at)||!delivering.add(output.id))return;
        try{var player=output.owner==null?null:server.getPlayerList().getPlayer(output.owner);
            if(spawn(level,player,output.reason,output.at,output.item,()->pending.get(output.id)==output)!=null){pending.remove(output.id);setDirty();}
        }finally{delivering.remove(output.id);}
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END||event.getServer().getTickCount()%20!=0)return;var data=get(event.getServer());for(var output:List.copyOf(data.pending.values()))data.deliver(event.getServer(),output);}
    @Override public void save(java.io.File file){NativeData.save(this,file);}
    @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();for(var output:pending.values()){var value=new CompoundTag();value.putUUID("id",output.id);value.putString("dimension",output.dimension.location().toString());if(output.owner!=null)value.putUUID("owner",output.owner);value.putString("reason",output.reason.name());value.putDouble("x",output.at.x);value.putDouble("y",output.at.y);value.putDouble("z",output.at.z);value.put("item",output.item.save(new CompoundTag()));list.add(value);}tag.put("pending",list);return tag;}
    private static NativeItemSpawns load(CompoundTag tag){
        if(!(tag.get("pending") instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid pending item generation");var data=new NativeItemSpawns();
        for(var entry:list){var value=(CompoundTag)entry;if(!value.hasUUID("id")||!value.contains("dimension",Tag.TAG_STRING)||!value.contains("reason",Tag.TAG_STRING)||!value.contains("x",Tag.TAG_DOUBLE)||!value.contains("y",Tag.TAG_DOUBLE)||!value.contains("z",Tag.TAG_DOUBLE)||value.contains("owner")&&!value.hasUUID("owner"))throw new IllegalArgumentException("Invalid pending item identity");
            var dimension=ResourceKey.create(Registries.DIMENSION,new ResourceLocation(value.getString("dimension")));var reason=Reason.valueOf(value.getString("reason"));var at=new Vec3(value.getDouble("x"),value.getDouble("y"),value.getDouble("z"));var item=ItemStack.of(value.getCompound("item"));EventRules.position(at.x,at.y,at.z);EventRules.stack(item.getCount(),item.getMaxStackSize());if(item.isEmpty())throw new IllegalArgumentException("Empty pending item");var id=value.getUUID("id");if(data.pending.putIfAbsent(id,new Pending(id,dimension,value.hasUUID("owner")?value.getUUID("owner"):null,reason,at,item))!=null)throw new IllegalArgumentException("Duplicate pending item "+id);
        }return data;
    }
}
