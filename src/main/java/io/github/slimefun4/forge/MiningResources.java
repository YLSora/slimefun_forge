package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.ForgeConfigSpec;

/** RC-37 OreDictionary17 and MachineFuel amounts, expressed in ores per item. */
public final class MiningResources {
    public static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.BooleanValue[] DEBRIS=new ForgeConfigSpec.BooleanValue[2];
    private static final ForgeConfigSpec.BooleanValue[] DEEPSLATE=new ForgeConfigSpec.BooleanValue[2];
    private record Drop(Item item,int minimum,int variants,boolean deepslate) {}
    private static final Map<Block,Drop> ORES=new HashMap<>();
    private static final net.minecraft.tags.TagKey<Block> ADDON_ORES=net.minecraft.tags.BlockTags.create(SlimefunForge.key("industrial_miner_ores"));
    private static final net.minecraft.tags.TagKey<Block> ADVANCED_ORES=net.minecraft.tags.BlockTags.create(SlimefunForge.key("advanced_industrial_miner_ores"));
    static {
        var config=new ForgeConfigSpec.Builder();
        for(int tier=0;tier<2;tier++) {
            config.push(tier==0?"industrial_miner":"advanced_industrial_miner");
            DEBRIS[tier]=config.define("can-mine-ancient-debris",false);
            DEEPSLATE[tier]=config.define("can-mine-deepslate-ores",true);
            config.pop();
        }
        CONFIG=config.build();
        add(Blocks.COAL_ORE,Blocks.DEEPSLATE_COAL_ORE,Items.COAL,1,1);
        add(Blocks.IRON_ORE,Blocks.DEEPSLATE_IRON_ORE,Items.RAW_IRON,1,1);
        add(Blocks.COPPER_ORE,Blocks.DEEPSLATE_COPPER_ORE,Items.RAW_COPPER,1,1);
        add(Blocks.GOLD_ORE,Blocks.DEEPSLATE_GOLD_ORE,Items.RAW_GOLD,1,1);
        add(Blocks.DIAMOND_ORE,Blocks.DEEPSLATE_DIAMOND_ORE,Items.DIAMOND,1,1);
        add(Blocks.EMERALD_ORE,Blocks.DEEPSLATE_EMERALD_ORE,Items.EMERALD,1,1);
        add(Blocks.REDSTONE_ORE,Blocks.DEEPSLATE_REDSTONE_ORE,Items.REDSTONE,4,2);
        add(Blocks.LAPIS_ORE,Blocks.DEEPSLATE_LAPIS_ORE,Items.LAPIS_LAZULI,4,4);
        ORES.put(Blocks.NETHER_QUARTZ_ORE,new Drop(Items.QUARTZ,1,1,false));
        ORES.put(Blocks.NETHER_GOLD_ORE,new Drop(Items.GOLD_NUGGET,2,4,false));
        ORES.put(Blocks.GILDED_BLACKSTONE,new Drop(Items.GILDED_BLACKSTONE,1,1,false));
    }
    private static void add(Block stone,Block deep,Item item,int minimum,int variants) {
        ORES.put(stone,new Drop(item,minimum,variants,false));
        ORES.put(deep,new Drop(item,minimum,variants,true));
    }
    public static boolean canMine(Block block,boolean advanced) {
        int tier=advanced?1:0;
        if(block==Blocks.ANCIENT_DEBRIS) return DEBRIS[tier].get();
        var drop=ORES.get(block);
        return drop!=null?(!drop.deepslate || DEEPSLATE[tier].get()):block.defaultBlockState().is(ADDON_ORES)||advanced&&block.defaultBlockState().is(ADVANCED_ORES);
    }
    static List<ItemStack> outcome(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,boolean advanced,net.minecraft.world.entity.player.Player actor) {
        var block=state.getBlock();
        if(!ORES.containsKey(block)&&block!=Blocks.ANCIENT_DEBRIS){var tool=new ItemStack(Items.DIAMOND_PICKAXE);if(advanced)tool.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH,1);return Block.getDrops(state,level,pos,null,actor,tool);}
        if(advanced || block==Blocks.ANCIENT_DEBRIS) return List.of(new ItemStack(block));
        var drop=ORES.get(block);
        return List.of(new ItemStack(drop.item,drop.minimum+level.random.nextInt(drop.variants)));
    }
    public static int fuel(ItemStack stack,boolean advanced) {
        if(stack.isEmpty()) return 0;
        if(advanced) {
            if(MachineRecipe.same(stack,new ItemStack(Items.LAVA_BUCKET))) return 96;
            if(MachineRecipe.same(stack,new ItemStack(SlimefunForge.item("bucket_of_oil")))) return 128;
            if(MachineRecipe.same(stack,new ItemStack(SlimefunForge.item("bucket_of_fuel")))) return 256;
            return 0;
        }
        // Vanilla inputs must match their unmodified recipe stack, including NBT.
        if(!MachineRecipe.same(stack,new ItemStack(stack.getItem()))) return 0;
        if(stack.is(Items.COAL) || stack.is(Items.CHARCOAL) || stack.is(Items.BLAZE_ROD)) return 8;
        if(stack.is(Items.COAL_BLOCK)) return 80;
        if(stack.is(Items.DRIED_KELP_BLOCK)) return 20;
        return stack.is(ItemTags.LOGS)?2:0;
    }
    private MiningResources() {}
}
