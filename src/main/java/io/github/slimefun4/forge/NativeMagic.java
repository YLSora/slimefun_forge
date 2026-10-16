package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.MagicApi.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

public final class NativeMagic {
    private static final Set<ServerPlayer> ACTIVATING=Collections.newSetFromMap(new WeakHashMap<>());
    private static int find(Container inventory,Item item){for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).is(item))return i;return -1;}
    public static Activation trigger(ServerPlayer player,Item item,Event source,boolean message){
        if(!player.server.isSameThread())throw new IllegalStateException("Talisman activation requires server thread");
        if(!(item instanceof Talisman base)||!valid(player)||!ACTIVATING.add(player))return Activation.NONE;
        try{
            var effects=base.effects().stream().map(MobEffectInstance::new).toList();
            if(!EventRules.chance(player.getRandom().nextInt(100),base.chance())||effects.stream().anyMatch(e->player.hasEffect(e.getEffect())))return Activation.NONE;
            Container inventory=player.getInventory();int slot=find(inventory,item);Item selected=item;
            if(slot<0){selected=base.enderVariant();if(selected==null)return Activation.NONE;if(!(selected instanceof Talisman))throw new IllegalArgumentException("Ender variant must implement Talisman");inventory=player.getEnderChestInventory();slot=find(inventory,selected);}
            if(slot<0||!ResearchData.canUse(player,new ItemStack(item)))return Activation.NONE;
            var held=inventory.getItem(slot);var expected=held.copy();var level=player.serverLevel();var talisman=(Talisman)selected;
            if(!ResearchData.canUse(player,held))return Activation.NONE;
            effects=talisman.effects().stream().map(MobEffectInstance::new).toList();boolean consume=talisman.consumable(),cancel=talisman.cancelsSource();var text=talisman.activationMessage().copy();
            var event=new TalismanActivateEvent(player,selected,held);
            if(MinecraftForge.EVENT_BUS.post(event))return Activation.CANCELLED;
            if(!valid(player)||player.serverLevel()!=level||inventory.getItem(slot)!=held||!ItemStack.matches(held,expected)||!ResearchData.canUse(player,held)||!ResearchData.canUse(player,new ItemStack(item)))return Activation.NONE;
            if(consume&&!event.preventsConsumption()){held.shrink(1);inventory.setChanged();}
            for(var effect:effects)player.addEffect(new MobEffectInstance(effect));
            if(cancel&&source!=null&&source.isCancelable())source.setCanceled(true);
            if(message&&!text.getString().isEmpty()){boolean actionbar=Guide.TALISMAN_ACTIONBAR.get();Messages.send(player,text,actionbar,!actionbar);}
            return Activation.ACTIVATED;
        }finally{ACTIVATING.remove(player);}
    }
    private static boolean valid(ServerPlayer player){return player.isAlive()&&!player.isSpectator()&&player.server.getPlayerList().getPlayer(player.getUUID())==player;}
    private NativeMagic(){}
}
