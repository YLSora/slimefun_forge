package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.*;

/** The dropped native entity owns its ritual; there are no global world tasks or extra rune inventories. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=SlimefunForge.ID)
public final class NativeRunes {
    private static final String JOB="slimefun_native_rune";
    private static final Set<ItemEntity> RUNNING=Collections.newSetFromMap(new WeakHashMap<>());
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void toss(ItemTossEvent event){
        var entity=event.getEntity();var stack=entity.getItem();
        if(!(stack.getItem() instanceof MagicApi.Rune)||!(event.getPlayer() instanceof ServerPlayer player)||!ResearchData.canUse(player,stack))return;
        var job=new CompoundTag();job.putUUID("actor",player.getUUID());job.putLong("due",entity.level().getGameTime()+20);entity.getPersistentData().put(JOB,job);
    }
    public static boolean tick(ItemStack stack,ItemEntity rune){
        if(rune.level() instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Rune updates require server thread");
        if(!(rune.level() instanceof ServerLevel level)||!(stack.getItem() instanceof MagicApi.Rune rule)||!rune.getPersistentData().contains(JOB,Tag.TAG_COMPOUND)||!RUNNING.add(rune))return false;
        try{
            var job=rune.getPersistentData().getCompound(JOB);if(level.getGameTime()<job.getLong("due"))return false;
            rune.getPersistentData().remove(JOB);
            if(!job.hasUUID("actor")||!ItemAccess.automation(level,job.getUUID("actor"),stack))return false;
            var actor=level.getServer().getPlayerList().getPlayer(job.getUUID("actor"));
            if(!job.hasUUID("target")){
                var targets=level.getEntitiesOfClass(ItemEntity.class,new AABB(rune.position(),rune.position()).inflate(1.5),entity->entity!=rune&&entity.isAlive()&&entity.getItem().getCount()==1&&!entity.getItem().is(stack.getItem())&&!AncientPedestalEntity.isOffering(entity));
                for(var target:targets){
                    var input=target.getItem().copy();var expected=stack.copy();var origin=rune.position();var targetAt=target.position();var output=Objects.requireNonNull(rule.transform(level,input.copy()));if(output.isEmpty())continue;
                    var result=output.get().copy();EventRules.stack(result.getCount(),1);if(result.isEmpty())throw new IllegalArgumentException("Empty rune output");
                    if(!valid(level,rune,target,expected,input,origin,targetAt)||!ItemAccess.automation(level,job.getUUID("actor"),stack))return false;
                    job.putUUID("target",target.getUUID());job.put("input",input.save(new CompoundTag()));job.put("rune",expected.save(new CompoundTag()));job.put("output",result.save(new CompoundTag()));job.putLong("due",level.getGameTime()+10);
                    job.putDouble("x",origin.x);job.putDouble("y",origin.y);job.putDouble("z",origin.z);job.putDouble("target_x",targetAt.x);job.putDouble("target_y",targetAt.y);job.putDouble("target_z",targetAt.z);rune.getPersistentData().put(JOB,job);
                    var bolt=EntityType.LIGHTNING_BOLT.create(level);if(bolt!=null){bolt.moveTo(origin);bolt.setVisualOnly(true);level.addFreshEntity(bolt);}return false;
                }return false;
            }
            if(!(level.getEntity(job.getUUID("target")) instanceof ItemEntity target))return false;
            var input=ItemStack.of(job.getCompound("input"));var expected=ItemStack.of(job.getCompound("rune"));var output=ItemStack.of(job.getCompound("output"));
            var origin=new net.minecraft.world.phys.Vec3(job.getDouble("x"),job.getDouble("y"),job.getDouble("z"));var targetAt=new net.minecraft.world.phys.Vec3(job.getDouble("target_x"),job.getDouble("target_y"),job.getDouble("target_z"));
            if(output.isEmpty()||output.getCount()!=1||!valid(level,rune,target,expected,input,origin,targetAt))return false;
            var spawned=ItemSpawnApi.spawn(level,actor,ItemSpawnApi.Reason.MISC,target.position(),output,()->valid(level,rune,target,expected,input,origin,targetAt)&&ItemAccess.automation(level,job.getUUID("actor"),stack));
            if(spawned==null)return false;
            target.discard();stack.shrink(1);if(stack.isEmpty())rune.discard();else rune.setItem(stack.copy());
            level.playSound(null,spawned.blockPosition(),net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_CURE,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
            return rune.isRemoved();
        }finally{RUNNING.remove(rune);}
    }
    private static boolean valid(ServerLevel level,ItemEntity rune,ItemEntity target,ItemStack expected,ItemStack input,net.minecraft.world.phys.Vec3 origin,net.minecraft.world.phys.Vec3 targetAt){
        return rune.isAlive()&&target.isAlive()&&rune.level()==level&&target.level()==level&&level.hasChunkAt(rune.blockPosition())&&level.hasChunkAt(target.blockPosition())&&rune.position().distanceToSqr(origin)<=2.25&&target.position().distanceToSqr(targetAt)<=2.25&&rune.distanceToSqr(target)<=2.25&&!AncientPedestalEntity.isOffering(target)&&ItemStack.matches(rune.getItem(),expected)&&ItemStack.matches(target.getItem(),input);
    }
    private NativeRunes(){}
}
