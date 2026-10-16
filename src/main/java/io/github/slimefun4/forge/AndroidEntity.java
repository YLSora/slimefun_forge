package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.AndroidApi;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Core robots use the same registered actions and single controller as native addons. */
public final class AndroidEntity extends BlockEntity implements MenuProvider,AndroidApi.Robot {
    public enum Instruction {
        START,REPEAT,WAIT,GO_FORWARD,GO_UP,GO_DOWN,TURN_LEFT,TURN_RIGHT,
        DIG_UP(AndroidBlock.Type.MINER),DIG_FORWARD(AndroidBlock.Type.MINER),DIG_DOWN(AndroidBlock.Type.MINER),
        MOVE_AND_DIG_UP(AndroidBlock.Type.MINER),MOVE_AND_DIG_FORWARD(AndroidBlock.Type.MINER),MOVE_AND_DIG_DOWN(AndroidBlock.Type.MINER),
        ATTACK_MOBS_ANIMALS(AndroidBlock.Type.FIGHTER),ATTACK_MOBS(AndroidBlock.Type.FIGHTER),ATTACK_ANIMALS(AndroidBlock.Type.FIGHTER),ATTACK_ANIMALS_ADULT(AndroidBlock.Type.FIGHTER),CHOP_TREE(AndroidBlock.Type.WOODCUTTER),CATCH_FISH(AndroidBlock.Type.FISHERMAN),
        FARM_FORWARD(AndroidBlock.Type.FARMER),FARM_DOWN(AndroidBlock.Type.FARMER),FARM_EXOTIC_FORWARD(AndroidBlock.Type.ADVANCED_FARMER),FARM_EXOTIC_DOWN(AndroidBlock.Type.ADVANCED_FARMER),INTERFACE_ITEMS,INTERFACE_FUEL;
        final AndroidBlock.Type required;Instruction(){required=null;}Instruction(AndroidBlock.Type type){required=type;}
    }
    private final AndroidApi.Controller controller;
    public final SimpleContainer inventory=new SimpleContainer(7){@Override public void setChanged(){super.setChanged();AndroidEntity.this.setChanged();wake();}};
    public AndroidEntity(BlockPos pos,BlockState state){super(SlimefunForge.ANDROID_ENTITY.get(),pos,state);var block=(AndroidBlock)state.getBlock();controller=new AndroidApi.Controller(this,AddonAndroids.coreType(block.type,block.tier));}
    @Override public BlockEntity machine(){return this;}@Override public AndroidApi.Controller controller(){return controller;}@Override public Container inventory(){return inventory;}
    @Override public UUID owner(){return controller.owner();}public List<net.minecraft.resources.ResourceLocation> script(){return controller.program();}
    public void setOwner(UUID value){controller.owner(value);}public void pause(boolean value){controller.pause(value);}public void setScript(List<net.minecraft.resources.ResourceLocation> value){controller.program(value);}
    @Override public List<net.minecraft.resources.ResourceLocation> choices(){return AndroidApi.choices(type());}
    @Override public void wake(){if(controller!=null&&level!=null&&!level.isClientSide&&!isRemoved()&&!controller.paused()&&owner()!=null&&(controller.fuel()>0||AndroidApi.fuelCycles(type(),inventory.getItem(6))>0)&&!level.getBlockTicks().hasScheduledTick(worldPosition,getBlockState().getBlock()))level.scheduleTick(worldPosition,getBlockState().getBlock(),MachineClock.delay(level));}
    public void work(){controller.tick();}
    @Override public void onLoad(){super.onLoad();wake();}@Override public void reviveCaps(){super.reviveCaps();wake();}
    @Override public Component getDisplayName(){return Component.translatable("menu.slimefun.android.inventory_title");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return AndroidApi.menu(id,inventory,this);}
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.put("controller",controller.save());var items=NonNullList.withSize(7,ItemStack.EMPTY);for(int i=0;i<7;i++)items.set(i,inventory.getItem(i));ContainerHelper.saveAllItems(tag,items);}
    @Override public void load(CompoundTag tag){if(!tag.contains("controller",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing robot controller");super.load(tag);controller.load(tag.getCompound("controller"));var items=NonNullList.withSize(7,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items);for(int i=0;i<7;i++)inventory.setItem(i,items.get(i));wake();}
}
