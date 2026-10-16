package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.*;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ForgeHooks.class,remap=false)
abstract class NativeInteractionCommitMixin {
    @Inject(method="onRightClickBlock",at=@At("RETURN"))
    private static void slimefun$commit(CallbackInfoReturnable<PlayerInteractEvent.RightClickBlock> result){NativeInteractions.after(result.getReturnValue());}
    @Redirect(method={"onBlockBreakEvent","onInteractEntity"},at=@At(value="INVOKE",target="Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean slimefun$afterProtection(net.minecraftforge.eventbus.api.IEventBus bus,net.minecraftforge.eventbus.api.Event event){
        bus.post(event);
        EventRules.dispatch(event::isCanceled,()->{
            if(event instanceof net.minecraftforge.event.level.BlockEvent.BreakEvent breaking)TalismanItem.mining(breaking);
            else if(event instanceof PlayerInteractEvent.EntityInteract interaction)FunctionalRuneItem.villager(interaction);
        });
        return event.isCanceled();
    }
}
