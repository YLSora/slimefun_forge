package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;

/** The altar starts world-owned rituals; its block entity only owns the book animation. */
public final class AncientAltarEntity extends BlockEntity {
    private static final int[][] OFFSETS={{2,-2},{3,0},{2,2},{0,3},{-2,2},{-3,0},{-2,-2},{0,-3}};
    private static final int[] RING={0,1,2,5,8,7,6,3};
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.IntValue STEP_DELAY;
    static {var builder=new ForgeConfigSpec.Builder();STEP_DELAY=builder.comment("Ticks between Ancient Altar ritual steps (RC-37 default: 8).").defineInRange("step-delay",8,0,Integer.MAX_VALUE);CONFIG=builder.build();}
    private long actions,searches;
    private net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity book;
    public AncientAltarEntity(BlockPos pos,BlockState state){super(SlimefunForge.ANCIENT_ALTAR_ENTITY.get(),pos,state);}
    public boolean running(){return level instanceof ServerLevel server&&AncientRituals.get(server).running(worldPosition);}
    public int stage(){return level instanceof ServerLevel server?AncientRituals.get(server).stage(worldPosition):0;}
    public long dueAt(){return level instanceof ServerLevel server?AncientRituals.get(server).dueAt(worldPosition):0;}
    public CompoundTag operation(){return level instanceof ServerLevel server?AncientRituals.get(server).snapshot(worldPosition):new CompoundTag();}
    public long actions(){return actions;}
    public long searches(){return searches;}
    void countAction(){actions++;}
    public void advance(){if(!isRemoved()&&level instanceof ServerLevel server)AncientRituals.get(server).advanceAt(server,worldPosition);}
    public void abort(){if(!isRemoved()&&level instanceof ServerLevel server)AncientRituals.get(server).cancel(server,worldPosition);}
    public net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity bookAnimation(){
        if(level==null||!level.isClientSide)throw new IllegalStateException("Book animation is client-only");
        if(book==null){book=new net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity(worldPosition,Blocks.ENCHANTING_TABLE.defaultBlockState());book.setLevel(level);}return book;
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0,1,0);}
    public static BlockPos pedestalAt(BlockPos center,int index){return center.offset(OFFSETS[index][0],0,OFFSETS[index][1]);}
    public boolean start(ServerPlayer player){
        if(!(level instanceof ServerLevel server)||isRemoved()||AncientRituals.get(server).locked(worldPosition)||!server.mayInteract(player,worldPosition))return false;
        var self=new ItemStack(getBlockState().getBlock().asItem());if(!ResearchData.canUse(player,self)){ItemAccess.denied(player,self);return false;}
        var catalyst=player.getMainHandItem();
        var inputs=new ArrayList<ItemStack>();var offerings=new ArrayList<ItemEntity>();int stands=0;
        for(int i=0;i<8;i++){var pos=pedestalAt(worldPosition,i);if(server.hasChunkAt(pos)&&server.getBlockEntity(pos) instanceof AncientPedestalEntity stand){if(stand.locked())return false;stands++;var item=AncientPedestalEntity.find(server,pos);offerings.add(item);inputs.add(item==null?ItemStack.EMPTY:item.getItem().copy());}}
        if(stands!=8){message(player,"structure",stands);return false;}
        if(catalyst.isEmpty()){message(player,"unknown_catalyst");return false;}
        searches++;var recipe=findRecipe(server,catalyst,inputs);if(recipe==null){message(player,"unknown_recipe");return false;}
        if(!ResearchData.canUse(player,recipe.output)){ItemAccess.denied(player,recipe.output);return false;}
        var result=recipe.output.copy();if(result.getItem() instanceof SpawnerItems.Repaired){var type=SpawnerItems.type(catalyst);if(type.isEmpty()){message(player,"unknown_catalyst");return false;}result=SpawnerItems.stack(true,type.get());}
        AncientRituals.get(server).start(player,worldPosition,result,offerings,inputs);if(!player.isCreative())catalyst.shrink(1);player.inventoryMenu.broadcastChanges();sound(server,worldPosition,SoundEvents.ILLUSIONER_PREPARE_MIRROR,1,1);return true;
    }
    public static MachineRecipe findRecipe(net.minecraft.world.level.Level level,ItemStack catalyst,List<ItemStack> inputs){
        if(inputs.size()!=8)return null;for(var recipe:MachineRecipe.forStation(level,"ancient_altar")){if(recipe.inputs.size()!=9||!same(catalyst,recipe.inputs.get(4)))continue;for(int rotation=0;rotation<8;rotation++){boolean matches=true;for(int j=0;j<8;j++)if(!same(inputs.get((rotation+j)%8),recipe.inputs.get(RING[j]))){matches=false;break;}if(matches)return recipe;}}return null;
    }
    private static boolean same(ItemStack actual,ItemStack wanted){return !actual.isEmpty()&&!wanted.isEmpty()&&wanted.getCount()==1&&MachineRecipe.same(actual,wanted);}
    static void message(ServerPlayer player,String key,Object...args){Messages.chat(player,Component.translatable("message.slimefun.altar."+key,args));}
    static void sound(ServerLevel level,BlockPos pos,SoundEvent sound,float volume,float pitch){level.playSound(null,pos,sound,SoundSource.BLOCKS,volume,pitch);}
}
