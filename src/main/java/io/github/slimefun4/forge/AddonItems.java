package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.ItemApi.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;

/** Internal consumers of the supported native traits. */
public final class AddonItems {
    static void check(ServerPlayer player){if(!player.server.isSameThread())throw new IllegalStateException("Item operation requires the owning server thread");}
    public static ItemStack placedItem(net.minecraft.world.level.Level level,BlockPos pos){
        if(level.getServer()!=null&&!level.getServer().isSameThread())throw new IllegalStateException("Placed item identity requires server thread");
        if(!level.hasChunkAt(pos))return ItemStack.EMPTY;var state=level.getBlockState(pos);var entity=level.getBlockEntity(pos);
        if(entity instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity&&entity.getPersistentData().getBoolean("slimefun_reinforced_spawner"))return SpawnerItems.mined(entity);
        var result=entity instanceof PlacedItem identity?Objects.requireNonNull(identity.placedItem()).copy():new ItemStack(state.getBlock());
        if(result.isEmpty())return ItemStack.EMPTY;
        EventRules.stack(result.getCount(),1);if(result.getItem()!=state.getBlock().asItem())throw new IllegalArgumentException("Placed identity does not match native block at "+pos);return result;
    }
    public static boolean distinctive(ItemStack first,ItemStack second,Match purpose){
        if(!(first.getItem() instanceof Distinctive item))return true;
        return item.matches(first.copy(),second.copy(),purpose)&&item.matches(second.copy(),first.copy(),purpose);
    }
    public static boolean matches(ItemStack first,ItemStack second,Match purpose){
        Objects.requireNonNull(purpose);if(first.isEmpty()||second.isEmpty())return first.isEmpty()&&second.isEmpty();
        if(first.getItem()!=second.getItem())return false;
        if(purpose==Match.STACK&&!ItemStack.isSameItemSameTags(first,second))return false;
        return first.getItem() instanceof Distinctive?distinctive(first,second,purpose):ItemStack.isSameItemSameTags(first,second);
    }
    public static boolean protectedFrom(ServerPlayer player,Protection protection){
        check(player);var suits=new ArrayList<String>();var equipment=io.github.slimefun4.forge.api.PlayerApi.armor(player);
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
            var stack=equipment.stack(slot);
            if(stack.isEmpty()||LivingEntity.getEquipmentSlotForItem(stack)!=slot||stack.isDamageableItem()&&stack.getDamageValue()>=stack.getMaxDamage()||!ResearchData.canUse(player,stack)||!(stack.getItem() instanceof ProtectiveArmor armor)){suits.add(null);continue;}
            var profile=Objects.requireNonNull(armor.protection(stack.copy()),"Protection for "+stack.getItem());
            if(!profile.protections().contains(protection)){suits.add(null);continue;}
            if(!profile.fullSuit())return !equipment.diverged(player);suits.add(profile.suit().toString());
        }
        return ItemRules.fullSuit(suits)&&!equipment.diverged(player);
    }
    public static int exposure(ServerPlayer player){check(player);return Radiation.exposure(player);}
    public static void exposure(ServerPlayer player,int value){check(player);if(value<0||value>100)throw new IllegalArgumentException("Exposure must be 0..100");Radiation.setExposure(player,value);}
    public static boolean soulbound(ItemStack stack){return Soulbinding.bound(stack);}
    public static boolean enabled(net.minecraft.server.level.ServerLevel level,ItemStack stack){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Item operation requires the owning server thread");
        return !stack.isEmpty()&&ItemAccess.enabled(level,stack);
    }
    public static ExternalResult interact(ServerPlayer actor,BlockPos target,boolean simulate){
        check(actor);var pos=target.immutable();var level=actor.serverLevel();var rejected=new ExternalResult(false,Component.translatable("message.slimefun.no_permission"),List.of());
        if(!actor.isAlive()||actor.isSpectator()||actor.server.getPlayerList().getPlayer(actor.getUUID())!=actor||!level.hasChunkAt(pos)||actor.distanceToSqr(pos.getCenter())>64||!level.mayInteract(actor,pos)||!ItemAccess.worldEnabled(level))return rejected;
        var state=level.getBlockState(pos);var item=placedItem(level,pos);var entity=level.getBlockEntity(pos);var data=entity==null?null:entity.saveWithoutMetadata();
        if(!(state.getBlock() instanceof ExternallyInteractable block)||!ResearchData.canUse(actor,item))return rejected;
        var event=new PlayerInteractEvent.RightClickBlock(actor,InteractionHand.MAIN_HAND,pos,new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),Direction.UP,pos,false));
        if(MinecraftForge.EVENT_BUS.post(event)||event.getUseBlock()==Event.Result.DENY||actor.serverLevel()!=level||!actor.isAlive()||actor.isSpectator()||actor.server.getPlayerList().getPlayer(actor.getUUID())!=actor||actor.distanceToSqr(pos.getCenter())>64||!level.hasChunkAt(pos)||!level.mayInteract(actor,pos)||level.getBlockState(pos)!=state||level.getBlockEntity(pos)!=entity||entity!=null&&!data.equals(entity.saveWithoutMetadata())||!ResearchData.canUse(actor,item))return rejected;
        return Objects.requireNonNull(block.interact(new ExternalContext(actor,actor.getUUID(),null,pos,simulate)),"External interaction result at "+pos);
    }
    public static ExternalResult interact(io.github.slimefun4.forge.api.AndroidApi.Robot robot,BlockPos target,boolean simulate){
        io.github.slimefun4.forge.api.AndroidApi.check(robot);var rejected=new ExternalResult(false,Component.translatable("message.slimefun.no_permission"),List.of());
        if(!io.github.slimefun4.forge.api.AndroidApi.valid(robot)||!AddonAndroids.authorized(robot))return rejected;
        var level=(net.minecraft.server.level.ServerLevel)robot.getLevel();var pos=target.immutable();if(!level.hasChunkAt(pos)||robot.getBlockPos().distSqr(pos)>64)return rejected;
        var state=level.getBlockState(pos);var entity=level.getBlockEntity(pos);var data=entity==null?null:entity.saveWithoutMetadata();var source=robot.saveWithoutMetadata();var item=placedItem(level,pos);
        if(!(state.getBlock() instanceof ExternallyInteractable block)||!ItemAccess.automation(level,robot.owner(),item))return rejected;
        var actor=AddonAndroids.actor(robot);if(!level.mayInteract(actor,pos))return rejected;
        var event=new PlayerInteractEvent.RightClickBlock(actor,InteractionHand.MAIN_HAND,pos,new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),Direction.UP,pos,false));
        if(MinecraftForge.EVENT_BUS.post(event)||event.getUseBlock()==Event.Result.DENY||!io.github.slimefun4.forge.api.AndroidApi.valid(robot)||!source.equals(robot.saveWithoutMetadata())||!AddonAndroids.authorized(robot)||!level.hasChunkAt(pos)||level.getBlockState(pos)!=state||level.getBlockEntity(pos)!=entity||entity!=null&&!data.equals(entity.saveWithoutMetadata())||!level.mayInteract(actor,pos)||!ItemAccess.automation(level,robot.owner(),item))return rejected;
        return Objects.requireNonNull(block.interact(new ExternalContext(actor,robot.owner(),robot.machine(),pos,simulate)),"Robot interaction result at "+pos);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void impact(LivingAttackEvent event){
        if(event.getEntity() instanceof ServerPlayer player&&event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL)&&protectedFrom(player,Protection.FLIGHT_IMPACT))event.setCanceled(true);
    }
    static void validate(){
        for(var entry:AddonContent.ENTRIES.values()){
            var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(entry.item());
            if(item instanceof FixedSettings fixed&&(fixed.fixedSettingsReason()==null||fixed.fixedSettingsReason().isBlank()))throw new IllegalArgumentException("Missing fixed settings reason for "+entry.item());
            if(item instanceof io.github.slimefun4.forge.api.Rechargeable rechargeable)rechargeable.getItemCharge(new ItemStack(item));
        }
    }
    private AddonItems(){}
}
