package io.github.slimefun4.forge;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Material handlers run before ordinary blocks, after RC-37's ritual/multiblock dispatch. */
@Mod.EventBusSubscriber(modid = SlimefunForge.ID)
public final class MaterialItemUse {
    private static final Set<String> TYPES = Set.of("UnplaceableBlock", "CoolantCell", "BasicCircuitBoard", "InfusedMagnet", "HeavyCream", "RadioactiveItem", "BrokenSpawner");
    private static Catalog.Entry material(ItemStack stack) {
        String id = ItemAccess.id(stack); var entry = id == null ? null : Catalog.entry(id);
        return entry != null && TYPES.contains(entry.implementation()) ? entry : null;
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void block(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getUseItem() == Event.Result.DENY) return;
        var item = material(event.getItemStack()); if (item == null) return;
        var state = event.getLevel().getBlockState(event.getPos());
        // The original custom ritual event consumes offerings before the held-item handler.
        if (state.getBlock() instanceof AncientRitualBlock) return;
        if (event.getHand() == InteractionHand.MAIN_HAND && ManualMachines.detect(event.getLevel(), event.getPos()) != null) return;
        if (!ResearchData.canUse(player, event.getItemStack())) {
            event.setUseItem(Event.Result.DENY); ItemAccess.denied(player, event.getItemStack()); return;
        }
        if (item.implementation().equals("HeavyCream")) {
            String id = ItemAccess.id(new ItemStack(state.getBlock())); var block = id == null ? null : Catalog.entry(id);
            if (block != null) state = BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", block.material())).defaultBlockState();
            if (state.is(UtilityItem.FLASK_INTERACTABLES)) return;
        }
        cancel(event);
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void item(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || material(event.getItemStack()) == null) return;
        if (!ResearchData.canUse(player, event.getItemStack())) ItemAccess.denied(player, event.getItemStack());
        cancel(event);
    }
    private static void cancel(PlayerInteractEvent event) { event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); }
    private MaterialItemUse() {}
}
