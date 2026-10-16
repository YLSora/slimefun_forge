package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Immediate items precede ordinary blocks and the independent multiblock handler. */
@Mod.EventBusSubscriber(modid=SlimefunForge.ID)
public final class InstantItemUse {
    public static void block(PlayerInteractEvent.RightClickBlock event) {
        var item=event.getItemStack().getItem();
        if(!(item instanceof MagicSugarItem||item instanceof SeasonalGiftItem||item instanceof MagicGadgetItem||item instanceof MagicUtilityItem||item instanceof MultiToolItem)||event.getUseItem()==Event.Result.DENY)return;
        var player=event.getEntity();
        var state=event.getLevel().getBlockState(event.getPos());
        if(state.getBlock() instanceof AncientRitualBlock)return;
        if(player instanceof ServerPlayer server&&!ResearchData.canUse(server,event.getItemStack())) {
            ItemAccess.denied(server,event.getItemStack());event.setUseItem(Event.Result.DENY);return;
        }
        if(item instanceof MagicGadgetItem||item instanceof MagicUtilityItem||item instanceof MultiToolItem) {
            var context=new UseOnContext(player,event.getHand(),event.getHitVec());
            var result=item instanceof MagicGadgetItem gadget?gadget.activate(player,event.getHand(),context):item instanceof MagicUtilityItem utility?utility.activate(player,event.getHand(),context):item.onItemUseFirst(event.getItemStack(),context);
            if(result.consumesAction())event.setUseBlock(Event.Result.DENY);
        }else if(item instanceof MagicSugarItem) {
            if(state.is(Blocks.DISPENSER)||state.is(Blocks.ENCHANTING_TABLE))return;
            item.use(event.getLevel(),player,event.getHand());
        }else {
            item.useOn(new UseOnContext(player,event.getHand(),event.getHitVec()));
            event.setUseBlock(Event.Result.DENY);
        }
        event.setUseItem(Event.Result.DENY);
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void entity(PlayerInteractEvent.EntityInteract event) {
        var item=event.getItemStack().getItem();
        if((item instanceof MagicSugarItem||item instanceof SeasonalGiftItem||item instanceof MagicGadgetItem||item instanceof MagicUtilityItem)&&event.getEntity() instanceof ServerPlayer player&&!ResearchData.canUse(player,event.getItemStack())) {
            ItemAccess.denied(player,event.getItemStack());event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
        }
    }
    private InstantItemUse() {}
}
