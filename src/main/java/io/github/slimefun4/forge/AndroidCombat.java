package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** RC-37 attributes later deaths to the attacking robot's original coordinate. */
@Mod.EventBusSubscriber(modid=SlimefunForge.ID)
public final class AndroidCombat {
    private static final Map<LivingEntity,BlockPos> KILLERS=new WeakHashMap<>();
    private static final Map<ServerLevel,List<Harvest>> PENDING=new IdentityHashMap<>();
    private record Harvest(BlockPos origin,LivingDropsEvent event,long ready) {}
    private static boolean available(ServerLevel level,BlockPos pos){return !level.isOutsideBuildHeight(pos)&&level.hasChunkAt(pos);}
    static void attack(io.github.slimefun4.forge.api.AndroidApi.Robot android,AndroidEntity.Instruction instruction){
        var level=(ServerLevel)android.getLevel();var pos=android.getBlockPos();int radius=4+android.tier();var face=android.facing();
        var box=new AABB(pos.getX()-radius,pos.getY()-radius,pos.getZ()-radius,pos.getX()+radius,pos.getY()+radius,pos.getZ()+radius);
        for(var target:level.getEntitiesOfClass(LivingEntity.class,box,e->e.isAlive()&&!e.isRemoved()&&!(e instanceof Player)&&!(e instanceof ArmorStand)&&available(level,e.blockPosition())&&matches(e,instruction))){
            boolean front=switch(face){case NORTH->target.getZ()<pos.getZ();case SOUTH->target.getZ()>pos.getZ();case EAST->target.getX()>pos.getX();case WEST->target.getX()<pos.getX();default->false;};if(!front)continue;
            var saved=android.saveWithoutMetadata();var state=android.getBlockState();var location=target.position();float health=target.getHealth();var actor=AndroidTasks.actor(android,level);
            if(!actor.mayBuild()||level.getServer().isUnderSpawnProtection(level,target.blockPosition(),actor)||MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(actor,target)))return;
            if(!AddonAndroids.authorized(android)||android.getLevel()!=level||!available(level,pos)||level.getBlockEntity(pos)!=android.machine()||!state.equals(android.getBlockState())||!saved.equals(android.saveWithoutMetadata())||!target.isAlive()||target.isRemoved()||target.level()!=level||!location.equals(target.position())||health!=target.getHealth())return;
            KILLERS.put(target,pos.immutable());
            target.hurt(level.damageSources().generic(),android.tier()==3?20:4*android.tier());
            return;
        }
    }
    private static boolean matches(LivingEntity target,AndroidEntity.Instruction command){return switch(command){
        case ATTACK_MOBS_ANIMALS->true;case ATTACK_MOBS->target instanceof Monster;case ATTACK_ANIMALS->target instanceof Animal;case ATTACK_ANIMALS_ADULT->target instanceof Animal animal&&!animal.isBaby();default->false;
    };}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void death(LivingDropsEvent event){
        if(!(event.getEntity().level() instanceof ServerLevel level))return;var origin=KILLERS.remove(event.getEntity());if(origin==null||event.isCanceled())return;
        PENDING.computeIfAbsent(level,key->new ArrayList<>()).add(new Harvest(origin,event,level.getGameTime()+1));
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;var work=PENDING.remove(level);if(work==null)return;
        for(var harvest:work){
            if(harvest.ready>level.getGameTime()){PENDING.computeIfAbsent(level,key->new ArrayList<>()).add(harvest);continue;}
            var victim=harvest.event.getEntity();if(harvest.event.isCanceled()||victim.level()!=level||!available(level,victim.blockPosition()))continue;
            var drops=new ArrayList<ItemStack>();
            for(var item:level.getEntitiesOfClass(ItemEntity.class,victim.getBoundingBox().inflate(.5),e->e.isAlive()&&!e.getPersistentData().contains("slimefun:no_pickup")&&available(level,e.blockPosition()))){drops.add(item.getItem().copy());item.discard();}
            if(victim.getType()==EntityType.WITHER_SKELETON&&level.random.nextInt(250)<2)drops.add(new ItemStack(Items.WITHER_SKELETON_SKULL));
            else if(victim.getType()==EntityType.BLAZE)drops.add(new ItemStack(Items.BLAZE_ROD));
            else if(victim.getType()==EntityType.VINDICATOR)drops.add(new ItemStack(Items.EMERALD,1+level.random.nextInt(2)));
            if(available(level,harvest.origin))for(var drop:drops)deposit(level.getBlockEntity(harvest.origin),drop);
            level.addFreshEntity(new ExperienceOrb(level,victim.getX(),victim.getY(),victim.getZ(),1+level.random.nextInt(6)));
        }
    }
    // RC-37 writes the old robot's menu slots into the current machine at that coordinate.
    private static void deposit(net.minecraft.world.level.block.entity.BlockEntity target,ItemStack drop){
        if(target instanceof io.github.slimefun4.forge.api.AndroidApi.Robot a)insert(a.inventory(),drop,0,1,2,3,4,5);
        else if(target instanceof ProcessingEntity a){if(a.inputSlots()==6)insert(a.inventory,drop,1,3);else insert(a.inventory,drop,1);}
        else if(target instanceof MachineEntity a&&a.hasInventory()&&a.assembly==null&&a.inventory.getContainerSize()==4)insert(a.inventory,drop,1);
        else if(target instanceof GeoMinerEntity a)insert(a.inventory,drop,0,1,2);
        else if(target instanceof CargoEntity a&&a.filtered())insert(a.filter,drop,1,2,4,5);
    }
    private static void insert(net.minecraft.world.Container inventory,ItemStack drop,int...slots){
        for(int slot:slots){var current=inventory.getItem(slot);if(!current.isEmpty()&&!AddonItems.matches(current,drop,io.github.slimefun4.forge.api.ItemApi.Match.STACK))continue;int accepted=Math.min(drop.getCount(),drop.getMaxStackSize()-current.getCount());if(accepted>0){inventory.setItem(slot,drop.copyWithCount(current.getCount()+accepted));drop.shrink(accepted);}if(drop.isEmpty())return;}
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getLevel() instanceof ServerLevel)KILLERS.remove(event.getEntity());}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel){PENDING.remove(event.getLevel());KILLERS.keySet().removeIf(e->e.level()==event.getLevel());}}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){PENDING.keySet().removeIf(l->l.getServer()==event.getServer());KILLERS.keySet().removeIf(e->e.getServer()==event.getServer());}
}
