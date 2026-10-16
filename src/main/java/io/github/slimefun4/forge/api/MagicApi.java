package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.Event;

/** Traits on native registered Items; no second item registry or Bukkit event dispatcher. */
public final class MagicApi {
    public interface Talisman {
        int chance();
        List<MobEffectInstance> effects();
        default boolean consumable(){return true;}
        default boolean cancelsSource(){return false;}
        default Item enderVariant(){return null;}
        default Component activationMessage(){return Component.empty();}
    }
    public enum Activation { NONE, CANCELLED, ACTIVATED;
        public boolean selected(){return this!=NONE;}
    }
    /** CANCELLED is selection success in RC-37, but causes no effect, source cancellation or consumption. */
    public static Activation trigger(ServerPlayer player,Item talisman,Event source,boolean message){return NativeMagic.trigger(player,talisman,source,message);}
    public interface Rune {
        /** Pure planning on a private single-item copy; empty means this target is ineligible. */
        Optional<ItemStack> transform(ServerLevel level,ItemStack target);
    }
    /** Call from the Item's onEntityItemUpdate. Native toss schedules the ritual; one rune is consumed. */
    public static boolean tickRune(ItemStack stack,ItemEntity entity){return NativeRunes.tick(stack,entity);}
    private MagicApi(){}
}
