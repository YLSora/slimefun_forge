package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Stop native item/block entry points before placement or another handler mutates the world. */
@Mod.EventBusSubscriber(modid=SlimefunForge.ID)
public final class DisabledItemUse {
    private static boolean allowed(ServerPlayer player,ItemStack stack){return io.github.slimefun4.forge.AddonContent.ENTRIES.containsKey(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))?ResearchData.canUse(player,stack):ItemAccess.enabled(player.level(),stack);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void block(PlayerInteractEvent.RightClickBlock event){
        if(event.getEntity() instanceof ServerPlayer player){
            var block=io.github.slimefun4.forge.api.ItemApi.placedItem(event.getLevel(),event.getPos());
            if(!allowed(player,block))deny(event,player,block);
            else if(!allowed(player,event.getItemStack())){event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);ItemAccess.denied(player,event.getItemStack());}
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void item(PlayerInteractEvent.RightClickItem event){
        if(event.getEntity() instanceof ServerPlayer player&&!allowed(player,event.getItemStack()))deny(event,player,event.getItemStack());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void entity(PlayerInteractEvent.EntityInteract event){if(event.getEntity() instanceof ServerPlayer player&&!allowed(player,event.getItemStack()))deny(event,player,event.getItemStack());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void entitySpecific(PlayerInteractEvent.EntityInteractSpecific event){if(event.getEntity() instanceof ServerPlayer player&&!allowed(player,event.getItemStack()))deny(event,player,event.getItemStack());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(net.minecraftforge.event.entity.player.AttackEntityEvent event){if(event.getEntity() instanceof ServerPlayer player&&!allowed(player,player.getMainHandItem())){event.setCanceled(true);ItemAccess.denied(player,player.getMainHandItem());}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mine(net.minecraftforge.event.level.BlockEvent.BreakEvent event){
        if(event.getPlayer() instanceof ServerPlayer player&&event.getLevel() instanceof net.minecraft.world.level.Level level&&(!allowed(player,player.getMainHandItem())||!allowed(player,io.github.slimefun4.forge.api.ItemApi.placedItem(level,event.getPos()))))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void consuming(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Tick event){if(event.getEntity() instanceof ServerPlayer player&&!allowed(player,event.getItem()))player.stopUsingItem();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void placing(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event){
        if(event.getLevel() instanceof net.minecraft.world.level.Level level){var stack=io.github.slimefun4.forge.api.ItemApi.placedItem(level,event.getPos());if(!ItemAccess.enabled(level,stack)||event.getEntity() instanceof ServerPlayer player&&!allowed(player,stack))event.setCanceled(true);}
    }
    private static void deny(PlayerInteractEvent event,ServerPlayer player,ItemStack stack){event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);ItemAccess.denied(player,stack);}
    private DisabledItemUse(){}
}
