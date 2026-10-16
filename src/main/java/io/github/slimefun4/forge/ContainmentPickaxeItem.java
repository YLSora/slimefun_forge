package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ContainmentPickaxeItem extends PickaxeItem {
    public ContainmentPickaxeItem() { super(Tiers.IRON,1,-2.8F,new Properties()); }
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,Player player,net.minecraft.world.InteractionHand hand){var stack=player.getItemInHand(hand);if(player instanceof ServerPlayer p&&!ResearchData.canUse(p,stack)){ItemAccess.denied(p,stack);return net.minecraft.world.InteractionResultHolder.fail(stack);}return net.minecraft.world.InteractionResultHolder.pass(stack);}
    @Override public net.minecraft.world.InteractionResult onItemUseFirst(ItemStack stack,net.minecraft.world.item.context.UseOnContext context){if(context.getPlayer() instanceof ServerPlayer p&&!ResearchData.canUse(p,stack))ItemAccess.denied(p,stack);return net.minecraft.world.InteractionResult.PASS;}
    @Override public boolean onBlockStartBreak(ItemStack stack,BlockPos pos,Player player) {
        if(player instanceof ServerPlayer server&&!ResearchData.canUse(server,stack)) { ItemAccess.denied(server,stack);return true; }
        return false;
    }
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if(event.getPlayer() instanceof ServerPlayer player&&player.getMainHandItem().getItem() instanceof ContainmentPickaxeItem) {
            if(!ResearchData.canUse(player,player.getMainHandItem())) { event.setCanceled(true);ItemAccess.denied(player,player.getMainHandItem()); }
            else if(event.getState().is(Blocks.SPAWNER))event.setExpToDrop(0);
        }
    }
}
