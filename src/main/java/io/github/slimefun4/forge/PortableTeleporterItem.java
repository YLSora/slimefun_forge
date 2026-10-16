package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.Rechargeable;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

public final class PortableTeleporterItem extends Item implements Rechargeable {
    public PortableTeleporterItem(){super(new Properties().stacksTo(1));}
    @Override public float getMaxItemCharge(ItemStack stack){return 50;}
    @Override public ICapabilityProvider initCapabilities(ItemStack stack,CompoundTag tag){return energyCapability(stack);}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("tooltip.slimefun.item_charge",getItemCharge(stack),getMaxItemCharge(stack)));}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer server){
            if(!ResearchData.canUse(server,stack)){ItemAccess.denied(server,stack);return InteractionResultHolder.fail(stack);}
            if(removeItemCharge(stack,10))GpsTeleportMenu.openPortable(server);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand()).getResult();}
}
