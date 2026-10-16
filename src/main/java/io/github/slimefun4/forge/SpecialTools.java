package io.github.slimefun4.forge;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.*;

public final class SpecialTools {
    public static final class Mining {
        public final ItemStack tool,held;
        private final int slot;
        private final ServerLevel level;
        private int fortune=1;
        private Mining(ServerPlayer player,ItemStack held){this.held=held;tool=held.copy();slot=player.getInventory().selected;level=player.serverLevel();}
        public int fortune(){return fortune;}
        boolean active(ServerPlayer player){
            if(held.isEmpty()||!player.isAlive()||player.serverLevel()!=level||player.getInventory().selected!=slot||player.getMainHandItem()!=held||!held.is(tool.getItem())||!ResearchData.canUse(player,held))return false;
            var metadata=held.copy();metadata.setDamageValue(tool.getDamageValue());return ItemStack.matches(metadata,tool);
        }
    }
    public static Optional<Mining> begin(ServerPlayer player) {
        var held=player.getMainHandItem();
        return switch(kind(held)) {
            case "smelters_pickaxe","lumber_axe","pickaxe_of_vein_mining","explosive_pickaxe","explosive_shovel" -> Optional.of(new Mining(player,held));
            default -> Optional.empty();
        };
    }
    // Dough Vein order, also used by AndroidTasks; see the existing MIT attribution in CREDITS.
    private static final int[][] NEIGHBORS={{0,1,0},{0,-1,0},{0,0,-1},{1,0,0},{0,0,1},{-1,0,0},{1,0,-1},{-1,0,-1},{1,0,1},{-1,0,1}};
    private static final Map<String,TagKey<Block>> TAGS=new HashMap<>();
    static TagKey<Block> tag(String name){return TAGS.computeIfAbsent(name,n->BlockTags.create(SlimefunForge.key(n)));}
    static String kind(ItemStack stack){if(stack.getItem() instanceof io.github.slimefun4.forge.api.ToolApi.MiningTool tool)return switch(Objects.requireNonNull(tool.mining(stack.copy()))){case SMELT->"smelters_pickaxe";case LUMBER->"lumber_axe";case VEIN->"pickaxe_of_vein_mining";case EXPLOSIVE_PICKAXE->"explosive_pickaxe";case EXPLOSIVE_SHOVEL->"explosive_shovel";};var key=BuiltInRegistries.ITEM.getKey(stack.getItem());return key.getNamespace().equals(SlimefunForge.ID)?key.getPath():"";}
    public static boolean veinMining(ItemStack stack,BlockState state){return kind(stack).equals("pickaxe_of_vein_mining")&&state.is(tag("pickaxe_of_vein_mining_blocks"));}
    public static int fortune(ItemStack stack,BlockState state,Player player) {
        int level=stack.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE);if(level<=0||stack.getEnchantmentLevel(Enchantments.SILK_TOUCH)>0)return 1;
        int amount=Math.max(1,player.getRandom().nextInt(level+2)-1);return (state.is(Blocks.LAPIS_ORE)?4+player.getRandom().nextInt(5):1)*(amount+1);
    }
    static boolean available(ServerLevel level,BlockPos pos){return level.hasChunkAt(pos)&&!level.isOutsideBuildHeight(pos)&&level.getWorldBorder().isWithinBounds(pos);}
    static boolean slimefun(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(SlimefunForge.ID);}
    static boolean unstripped(BlockState state){return state.is(BlockTags.LOGS)&&!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_");}
    static List<BlockPos> vein(ServerLevel level,BlockPos root,Predicate<BlockState> matches,int limit) {
        var result=new ArrayList<BlockPos>();var seen=new HashSet<BlockPos>();var pending=new ArrayDeque<BlockPos>();pending.push(root);
        while(!pending.isEmpty()&&result.size()<limit){var pos=pending.pop();if(!seen.add(pos)||!available(level,pos)||!pos.equals(root)&&!matches.test(level.getBlockState(pos)))continue;result.add(pos);for(int i=NEIGHBORS.length-1;i>=0;i--){var d=NEIGHBORS[i];pending.push(pos.offset(d[0],d[1],d[2]));}}
        return result;
    }
    static boolean permitted(ServerPlayer p,BlockPos pos,BlockState state) {
        return available(p.serverLevel(),pos)&&p.mayBuild()&&p.level().mayInteract(p,pos)&&ForgeHooks.onBlockBreakEvent(p.level(),p.gameMode.getGameModeForPlayer(),p,pos)>=0&&p.level().getBlockState(pos).equals(state);
    }
    public static void applyMining(Level world,Player player,BlockPos origin,BlockState original,Mining mining) {
        ItemStack tool=mining.tool;
        if(!(world instanceof ServerLevel level)||!(player instanceof ServerPlayer p)||!ResearchData.canUse(p,tool))return;
        String kind=kind(tool);
        if(kind.equals("smelters_pickaxe")||kind.equals("pickaxe_of_vein_mining"))mining.fortune=fortune(tool,original,p);
        int fortune=mining.fortune;
        // RC-37 expands during its break event, before the native center durability cost.
        if(!mining.active(p))return;
        if(kind.equals("smelters_pickaxe")&&original.is(tag("smelters_pickaxe_blocks"))&&!slimefun(original))EquipmentDamage.damage(p,mining.held,EquipmentSlot.MAINHAND,false);
        boolean lumber=kind.equals("lumber_axe")&&!p.isShiftKeyDown()&&original.is(BlockTags.LOGS);
        boolean vein=kind.equals("pickaxe_of_vein_mining")&&original.is(tag("pickaxe_of_vein_mining_blocks"));
        boolean explosive=(kind.equals("explosive_pickaxe")||kind.equals("explosive_shovel"))&&!p.isShiftKeyDown();
        if(!lumber&&!vein&&!explosive)return;
        if(vein&&p.isCreative())drop(level,p,origin,original,null,tool,fortune,false);
        List<BlockPos> blocks;
        if(explosive) {
            level.explode(null,origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5,0,Level.ExplosionInteraction.NONE);
            level.playSound(null,origin,net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
            blocks=new ArrayList<>();for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)if(x!=0||y!=0||z!=0){var pos=origin.offset(x,y,z);if(available(level,pos)&&explosiveTarget(level.getBlockState(pos),kind))blocks.add(pos);}
            var event=new ExplosiveToolBreakBlocksEvent(p,origin,blocks,tool);
            if(MinecraftForge.EVENT_BUS.post(event))return;blocks=event.getAdditionalBlocks();
        } else blocks=vein(level,origin,lumber?s->s.is(BlockTags.LOGS):s->s.is(tag("pickaxe_of_vein_mining_blocks")),lumber?100:16);
        for(var pos:blocks) {
            if(!mining.active(p))break;
            if(pos.equals(origin)||!available(level,pos))continue;
            var expected=mining.held.copy();
            var state=level.getBlockState(pos);if(state.isAir()||state.getDestroySpeed(level,pos)<0||!p.mayBuild()||!level.mayInteract(p,pos))continue;
            if(!explosive&&slimefun(state))continue;
            if(explosive&&!explosiveTarget(state,kind))continue;
            var entity=level.getBlockEntity(pos);var entityTag=entity==null?null:entity.saveWithFullMetadata();
            int experience=ForgeHooks.onBlockBreakEvent(level,p.gameMode.getGameModeForPlayer(),p,pos);
            if(experience<0||!mining.active(p)||!ItemStack.matches(expected,mining.held)||!level.getBlockState(pos).equals(state)||level.getBlockEntity(pos)!=entity||entity!=null&&!entity.saveWithFullMetadata().equals(entityTag))continue;
            var harvestTool=lumber?new ItemStack(Items.DIAMOND_AXE):tool;
            var drops=vein?veinDrops(level,p,pos,state,entity,tool,fortune):Block.getDrops(state,level,pos,entity,p,harvestTool);
            if(!mining.active(p)||!ItemStack.matches(expected,mining.held)||!level.getBlockState(pos).equals(state)||level.getBlockEntity(pos)!=entity||entity!=null&&!entity.saveWithFullMetadata().equals(entityTag))continue;
            boolean correct=!state.requiresCorrectToolForDrops()||harvestTool.isCorrectToolForDrops(state);
            if(state.onDestroyedByPlayer(level,pos,p,correct,level.getFluidState(pos))){state.getBlock().destroy(level,pos,state);if(correct){for(var drop:drops)Block.popResource(level,pos,drop);if(experience>0&&!p.isCreative())state.getBlock().popExperience(level,pos,experience);}level.levelEvent(2001,pos,Block.getId(state));if(explosive)EquipmentDamage.damage(p,mining.held,EquipmentSlot.MAINHAND,false);}
        }
    }
    private static boolean explosiveTarget(BlockState state,String kind){return !state.isAir()&&!(state.getBlock() instanceof LiquidBlock)&&!state.is(tag("unbreakable_materials"))&&(!kind.equals("explosive_shovel")||state.is(tag("explosive_shovel_blocks")));}
    private static List<ItemStack> veinDrops(ServerLevel level,ServerPlayer p,BlockPos pos,BlockState state,BlockEntity entity,ItemStack tool,int fortune) {
        if(tool.getEnchantmentLevel(Enchantments.SILK_TOUCH)>0)return List.of(new ItemStack(state.getBlock()));
        var drops=Block.getDrops(state,level,pos,entity,p,tool);for(var drop:drops)if(!(drop.getItem() instanceof BlockItem))drop.setCount(fortune);return drops;
    }
    private static void drop(ServerLevel level,ServerPlayer p,BlockPos pos,BlockState state,BlockEntity entity,ItemStack tool,int fortune,boolean smelt) {
        var drops=smelt?Block.getDrops(state,level,pos,entity,p,tool):veinDrops(level,p,pos,state,entity,tool,fortune);
        for(var raw:drops){var output=raw;if(smelt){var recipe=level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SimpleContainer(raw),level);if(recipe.isPresent()){output=new ItemStack(recipe.get().getResultItem(level.registryAccess()).getItem());level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,5,.2,.2,.2,0);}output.setCount(fortune);}Block.popResource(level,pos,output);}
    }
    public static boolean primaryDrop(Level world,Player player,BlockPos pos,BlockState state,BlockEntity entity,ItemStack tool,int fortune) {
        if(!(world instanceof ServerLevel level)||!(player instanceof ServerPlayer p))return false;
        String kind=kind(tool);boolean smelt=kind.equals("smelters_pickaxe")&&state.is(tag("smelters_pickaxe_blocks"))&&!slimefun(state);
        if(!smelt&&!(kind.equals("pickaxe_of_vein_mining")&&state.is(tag("pickaxe_of_vein_mining_blocks"))))return false;
        p.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(state.getBlock()));p.causeFoodExhaustion(.005F);drop(level,p,pos,state,entity,tool,fortune,smelt);return true;
    }
    private SpecialTools(){}
}
