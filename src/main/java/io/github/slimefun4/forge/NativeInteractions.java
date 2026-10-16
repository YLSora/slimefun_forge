package io.github.slimefun4.forge;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

/** Commit core block-click behavior only after every native protection listener has returned. */
public final class NativeInteractions {
    public static void after(PlayerInteractEvent.RightClickBlock event){
        EventRules.dispatch(event::isCanceled,
            ()->{if(event.getUseItem()!=Event.Result.DENY&&event.getUseBlock()!=Event.Result.DENY)DebugFish.right(event);},
            ()->InstantItemUse.block(event),
            ()->UtilityItem.Events.useUtility(event),
            ()->{if(event.getUseBlock()!=Event.Result.DENY)ManualMachines.interact(event);},
            ()->{if(event.getUseBlock()!=Event.Result.DENY)IndustrialMiner.interact(event);},
            ()->AddonMachines.interact(event),
            ()->{if(event.getUseBlock()!=Event.Result.DENY)AutoCrafterBlock.interact(event);});
    }
    private NativeInteractions(){}
}
