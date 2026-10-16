package io.github.slimefun4.forge;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.common.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

/** Bounded native harvesting; only the android's six output slots may receive drops. */
public final class AndroidTasks {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.BooleanValue REDUCED_UPDATES,GENERATOR_EVENTS;
    static {
        var builder=new ForgeConfigSpec.Builder();builder.push("miner");
        REDUCED_UPDATES=builder.define("reduced-block-updates",true);
        GENERATOR_EVENTS=builder.define("trigger-event-for-generators",false);
        builder.pop();CONFIG=builder.build();
    }
    private static TagKey<Block> tag(String name){return BlockTags.create(new ResourceLocation(SlimefunForge.ID,name));}
    private static final TagKey<Block> UNBREAKABLE=tag("unbreakable_materials"),DIRT=tag("dirt_variants"),FUNGUS=tag("fungus_soil"),MANGROVE=tag("mangrove_base_blocks");
    private static final int[][] NEIGHBORS={{0,1,0},{0,-1,0},{0,0,-1},{1,0,0},{0,0,1},{-1,0,0},{1,0,-1},{-1,0,-1},{1,0,1},{-1,0,1}};
    private static boolean loaded(ServerLevel level,BlockPos pos){return !level.isOutsideBuildHeight(pos)&&level.hasChunkAt(pos);}
    private static boolean available(ServerLevel level,BlockPos pos){return loaded(level,pos)&&level.getWorldBorder().isWithinBounds(pos);}
    private static boolean slimefun(BlockState state){return SlimefunForge.ID.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()).getNamespace());}
    static ServerPlayer actor(io.github.slimefun4.forge.api.AndroidApi.Robot android,ServerLevel level){
        return io.github.slimefun4.forge.api.AndroidApi.actor(android);
    }
    private record Snapshot(io.github.slimefun4.forge.api.AndroidApi.Robot android,ServerLevel level,BlockPos pos,BlockState state,BlockEntity blockEntity,CompoundTag targetData,CompoundTag data,BlockState androidState) {
        Snapshot(io.github.slimefun4.forge.api.AndroidApi.Robot android,BlockPos pos){this(android,(ServerLevel)android.getLevel(),pos,android.getLevel().getBlockState(pos),android.getLevel().getBlockEntity(pos),android.getLevel().getBlockEntity(pos)==null?null:android.getLevel().getBlockEntity(pos).saveWithoutMetadata(),android.saveWithoutMetadata(),android.getBlockState());}
        boolean unchanged(){return io.github.slimefun4.forge.api.AndroidApi.valid(android)&&android.getBlockState().equals(androidState)&&loaded(level,pos)&&level.getBlockState(pos).equals(state)&&level.getBlockEntity(pos)==blockEntity&&(blockEntity==null||targetData.equals(blockEntity.saveWithoutMetadata()))&&data.equals(android.saveWithoutMetadata());}
        // ServerLevel.mayInteract also tests the border; these tasks apply that rule at their own RC-37 boundary.
        boolean permitted(){var player=actor(android,level);return AddonAndroids.authorized(android)&&ItemAccess.automation(level,android.owner(),new ItemStack(state.getBlock()))&&player.mayBuild()&&!level.getServer().isUnderSpawnProtection(level,pos,player)&&!MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,state,player))&&unchanged()&&AddonAndroids.authorized(android)&&ItemAccess.automation(level,android.owner(),new ItemStack(state.getBlock()));}
        boolean automation(){if(state.getBlock() instanceof io.github.slimefun4.forge.api.ItemApi.AutomationBreak block)return block.mayBreak(new io.github.slimefun4.forge.api.ItemApi.ExternalContext(actor(android,level),android.owner(),android.machine(),pos,true))&&unchanged();return !slimefun(state)&&blockEntity==null;}
    }
    static boolean dig(io.github.slimefun4.forge.api.AndroidApi.Robot android,BlockPos pos){
        var level=(ServerLevel)android.getLevel();if(!AddonAndroids.authorized(android)||android.getBlockPos().distSqr(pos)>64||!loaded(level,pos))return false;var snapshot=new Snapshot(android,pos);var state=snapshot.state;
        if(state.is(UNBREAKABLE))return false;if(state.isAir())return true;
        var pickaxe=new ItemStack(Items.DIAMOND_PICKAXE);if(state.requiresCorrectToolForDrops()&&!pickaxe.isCorrectToolForDrops(state))return true;
        var drops=Block.getDrops(state,level,pos,snapshot.blockEntity,actor(android,level),pickaxe);
        if(!snapshot.unchanged())return false;if(drops.isEmpty())return true;
        if(!snapshot.permitted())return false;
        if(MinecraftForge.EVENT_BUS.post(new AndroidMineEvent(android,pos,state))||!snapshot.unchanged()||!snapshot.automation())return false;
        // RC-37 checks the mined block's border only after protection and AndroidMineEvent.
        if(!level.getWorldBorder().isWithinBounds(pos))return false;
        boolean generated=REDUCED_UPDATES.get()&&generator(level,pos,state);
        if(generated&&GENERATOR_EVENTS.get()){
            // RC-37 uses this as a notification and does not apply the returned event state/cancellation.
            MinecraftForge.EVENT_BUS.post(new BlockEvent.FluidPlaceBlockEvent(level,pos,pos,state));
            if(!snapshot.unchanged()||!generator(level,pos,state))return false;
        }
        if(!level.getWorldBorder().isWithinBounds(pos)||!commit(snapshot,drops,generated?state:Blocks.AIR.defaultBlockState()))return false;
        if(generated){level.playSound(null,pos,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,.075F,.8F);level.sendParticles(ParticleTypes.SMOKE,pos.getX()+.5,pos.getY()+1.25,pos.getZ()+.5,8,.5,.5,.5,.015);}
        return true;
    }
    private static boolean is(ServerLevel level,BlockPos pos,Block block){return loaded(level,pos)&&level.getBlockState(pos).is(block);}
    private static boolean adjacent(ServerLevel level,BlockPos pos,Block block){for(var direction:Direction.Plane.HORIZONTAL)if(is(level,pos.relative(direction),block))return true;return false;}
    private static boolean generator(ServerLevel level,BlockPos pos,BlockState state){
        if(state.is(Blocks.COBBLESTONE))return adjacent(level,pos,Blocks.WATER)&&adjacent(level,pos,Blocks.LAVA);
        if(state.is(Blocks.STONE))return is(level,pos.above(),Blocks.LAVA)&&adjacent(level,pos,Blocks.WATER);
        return state.is(Blocks.BASALT)&&is(level,pos.below(),Blocks.SOUL_SOIL)&&adjacent(level,pos,Blocks.LAVA)&&adjacent(level,pos,Blocks.BLUE_ICE);
    }
    static boolean chop(io.github.slimefun4.forge.api.AndroidApi.Robot android,BlockPos anchor){
        var level=(ServerLevel)android.getLevel();if(!level.getWorldBorder().isWithinBounds(anchor))return true;if(!available(level,anchor))return false;if(!level.getBlockState(anchor).is(BlockTags.LOGS))return true;
        // Dough 0130f8d9ce Vein order, with a bounded iterative DFS and no chunk loading (MIT; see CREDITS).
        var pending=new ArrayDeque<BlockPos>();var visited=new HashSet<BlockPos>();pending.push(anchor);BlockPos last=anchor;
        while(!pending.isEmpty()&&visited.size()<160){
            var pos=pending.pop();if(visited.contains(pos)||!loaded(level,pos)||!level.getBlockState(pos).is(BlockTags.LOGS))continue;
            visited.add(pos);last=pos;for(int i=NEIGHBORS.length-1;i>=0;i--){var d=NEIGHBORS[i];pending.push(pos.offset(d[0],d[1],d[2]));}
        }
        var snapshot=new Snapshot(android,last);if(!snapshot.automation()||!snapshot.permitted()||!available(level,anchor))return false;
        var sapling=last.getY()==anchor.getY()?sapling(snapshot.state.getBlock()):null;boolean plant=false;
        if(sapling!=null){var soil=sapling==Blocks.CRIMSON_FUNGUS||sapling==Blocks.WARPED_FUNGUS?FUNGUS:sapling==Blocks.MANGROVE_PROPAGULE?MANGROVE:DIRT;plant=loaded(level,last.below())&&level.getBlockState(last.below()).is(soil);}
        var replacement=plant?sapling.defaultBlockState():Blocks.AIR.defaultBlockState();
        if(commit(snapshot,List.of(new ItemStack(snapshot.state.getBlock())),replacement)&&sapling!=null&&!plant)Block.popResource(level,last,new ItemStack(sapling));
        return false;
    }
    private static Block sapling(Block log){
        var id=ForgeRegistries.BLOCKS.getKey(log);if(!id.getNamespace().equals("minecraft"))return null;var name=id.getPath();if(name.startsWith("stripped_"))name=name.substring(9);
        return switch(name){
            case "oak_log","oak_wood"->Blocks.OAK_SAPLING;case "birch_log","birch_wood"->Blocks.BIRCH_SAPLING;
            case "jungle_log","jungle_wood"->Blocks.JUNGLE_SAPLING;case "spruce_log","spruce_wood"->Blocks.SPRUCE_SAPLING;
            case "acacia_log","acacia_wood"->Blocks.ACACIA_SAPLING;case "dark_oak_log","dark_oak_wood"->Blocks.DARK_OAK_SAPLING;
            case "crimson_stem","crimson_hyphae"->Blocks.CRIMSON_FUNGUS;case "warped_stem","warped_hyphae"->Blocks.WARPED_FUNGUS;
            case "mangrove_log"->Blocks.MANGROVE_PROPAGULE;case "cherry_log"->Blocks.CHERRY_SAPLING;default->null;
        };
    }
    static void farm(io.github.slimefun4.forge.api.AndroidApi.Robot android,BlockPos pos,boolean advanced){
        var level=(ServerLevel)android.getLevel();if(!AddonAndroids.authorized(android)||android.getBlockPos().distSqr(pos)>64||!available(level,pos))return;var snapshot=new Snapshot(android,pos);var state=snapshot.state;
        var property=state.getBlock().getStateDefinition().getProperty("age");var age=property instanceof IntegerProperty value&&value.getPossibleValues().contains(0)?value:null;ItemStack drop=null;
        if(age!=null&&state.getValue(age)==Collections.max(age.getPossibleValues())){
            Item item=state.is(Blocks.WHEAT)?Items.WHEAT:state.is(Blocks.POTATOES)?Items.POTATO:state.is(Blocks.CARROTS)?Items.CARROT:state.is(Blocks.BEETROOTS)?Items.BEETROOT:state.is(Blocks.COCOA)?Items.COCOA_BEANS:state.is(Blocks.NETHER_WART)?Items.NETHER_WART:state.is(Blocks.SWEET_BERRY_BUSH)?Items.SWEET_BERRIES:null;
            if(item!=null)drop=new ItemStack(item,level.random.nextInt(item==Items.WHEAT?2:3)+1);
        }
        var event=new AndroidFarmEvent(android,pos,state,advanced,drop);
        if(MinecraftForge.EVENT_BUS.post(event)||!snapshot.unchanged()||!available(level,pos))return;drop=event.getDrop();
        if(drop!=null&&!drop.isEmpty()&&snapshot.permitted()&&snapshot.automation()&&available(level,pos))commit(snapshot,List.of(drop),age==null?Blocks.AIR.defaultBlockState():state.setValue(age,0));
    }
    static void fish(io.github.slimefun4.forge.api.AndroidApi.Robot android){
        var level=(ServerLevel)android.getLevel();var water=android.getBlockPos().below();if(!is(level,water,Blocks.WATER))return;var snapshot=new Snapshot(android,water);
        level.playSound(null,water.getX(),water.getY(),water.getZ(),SoundEvents.PLAYER_SPLASH,SoundSource.BLOCKS,.3F,.7F);
        if(!snapshot.unchanged()||level.random.nextInt(100)>=10*android.tier())return;
        var loot=net.minecraft.util.random.SimpleWeightedRandomList.<Item>builder();
        for(var fish:ForgeRegistries.ITEMS.tags().getTag(ItemTags.FISHES))loot.add(fish,25);
        loot.add(Items.BONE,10).add(Items.STRING,10).add(Items.INK_SAC,8).add(Items.KELP,6).add(Items.STICK,5).add(Items.ROTTEN_FLESH,3).add(Items.LEATHER,2).add(Items.BAMBOO,3).add(Items.SADDLE,1).add(Items.NAME_TAG,1).add(Items.NAUTILUS_SHELL,1);
        var drop=new ItemStack(loot.build().getRandomValue(level.random).orElseThrow());
        for(int i=0;i<6;i++){var stored=android.inventory().getItem(i);if(stored.isEmpty()||AddonItems.matches(stored,drop,io.github.slimefun4.forge.api.ItemApi.Match.STACK)&&stored.getCount()<stored.getMaxStackSize()){android.inventory().setItem(i,drop.copyWithCount(stored.getCount()+1));return;}}
    }
    private static boolean commit(Snapshot snapshot,List<ItemStack> drops,BlockState replacement){
        if(!snapshot.unchanged())return false;var inventory=snapshot.android.inventory();var planned=new SimpleContainer(6);for(int i=0;i<6;i++)planned.setItem(i,inventory.getItem(i).copy());
        var slots=new InvWrapper(planned);boolean complete=true;for(var drop:drops)if(!ItemHandlerHelper.insertItem(slots,drop.copy(),false).isEmpty())complete=false;
        // No partial harvest: an unchanged crop must never yield the same output twice.
        if(!complete||!snapshot.unchanged()||!AddonAndroids.authorized(snapshot.android))return false;
        if(!replacement.equals(snapshot.state)&&!snapshot.level.setBlock(snapshot.pos,replacement,3))return false;
        if(!snapshot.level.getBlockState(snapshot.pos).equals(replacement))return false;
        if(snapshot.data.equals(snapshot.android.saveWithoutMetadata())&&io.github.slimefun4.forge.api.AndroidApi.valid(snapshot.android))for(int i=0;i<6;i++)inventory.setItem(i,planned.getItem(i));
        else for(var drop:drops)Block.popResource(snapshot.level,snapshot.pos,drop.copy());
        snapshot.level.levelEvent(2001,snapshot.pos,Block.getId(snapshot.state));return true;
    }
}
