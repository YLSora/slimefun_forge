package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class SoulboundEquipment {
    private static final java.util.Set<String> IDS=Catalog.ITEMS.stream().filter(e->java.util.Set.of("soulbound","equipment_armor","equipment_elytra","mining_pickaxe","lumber_axe","explosive_shovel","multi_tool","weapon").contains(e.behavior())).map(Catalog.Entry::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    public static Item create(Catalog.Entry entry) {
        return switch(entry.material()) {
            case "diamond_sword"->new SwordItem(Tiers.DIAMOND,3,-2.4F,new Item.Properties());
            case "diamond_pickaxe"->new PickaxeItem(Tiers.DIAMOND,1,-2.8F,new Item.Properties());
            case "diamond_axe"->new AxeItem(Tiers.DIAMOND,5,-3,new Item.Properties());
            case "diamond_shovel"->new ShovelItem(Tiers.DIAMOND,1.5F,-3,new Item.Properties());
            case "diamond_hoe"->new HoeItem(Tiers.DIAMOND,-3,0,new Item.Properties());
            case "diamond_helmet"->new ArmorItem(ArmorMaterials.DIAMOND,ArmorItem.Type.HELMET,new Item.Properties());
            case "diamond_chestplate"->new ArmorItem(ArmorMaterials.DIAMOND,ArmorItem.Type.CHESTPLATE,new Item.Properties());
            case "diamond_leggings"->new ArmorItem(ArmorMaterials.DIAMOND,ArmorItem.Type.LEGGINGS,new Item.Properties());
            case "diamond_boots"->new ArmorItem(ArmorMaterials.DIAMOND,ArmorItem.Type.BOOTS,new Item.Properties());
            case "bow"->new BowItem(new Item.Properties().durability(384));
            case "trident"->new TridentItem(new Item.Properties().durability(250));
            case "elytra"->new ElytraItem(new Item.Properties().durability(432));
            default->throw new IllegalArgumentException("Unknown soulbound equipment: "+entry.id());
        };
    }
    private static boolean denied(ServerPlayer player,ItemStack stack) {
        var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if(!id.getNamespace().equals("slimefun")||!IDS.contains(id.getPath())||ResearchData.canUse(player,stack))return false;
        ItemAccess.denied(player,stack);return true;
    }
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem event) { if(event.getEntity() instanceof ServerPlayer p&&denied(p,event.getItemStack()))event.setCanceled(true); }
    @SubscribeEvent public static void block(PlayerInteractEvent.RightClickBlock event) { if(event.getEntity() instanceof ServerPlayer p&&denied(p,event.getItemStack()))event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY); }
    @SubscribeEvent public static void attack(AttackEntityEvent event) { if(event.getEntity() instanceof ServerPlayer p&&denied(p,p.getMainHandItem()))event.setCanceled(true); }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) { if(event.getPlayer() instanceof ServerPlayer p&&denied(p,p.getMainHandItem()))event.setCanceled(true); }
    private SoulboundEquipment() {}
}
