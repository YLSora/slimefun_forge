package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** RC-37's 45-slot container layout with native inventory and progress synchronization. */
public final class ProcessingMenu extends ChestMenu {
    public final BlockPos pos;
    public final int inputSlots;
    private final Level level;
    private final ProcessingEntity machine;
    private final ContainerData data;
    private final Player owner;
    private final String energyFormat;
    private final int[] inputs;
    private final SimpleContainer display;
    private int previousProgress=-1,previousTotal=-1;
    private static String energyFormat(ServerPlayer player){return GuideContent.language(Guide.locale(player)).get("container.slimefun.processing_energy");}
    public static void write(FriendlyByteBuf buffer,ServerPlayer player,ProcessingEntity machine){buffer.writeBlockPos(machine.getBlockPos());buffer.writeByte(machine.inputSlots());buffer.writeUtf(energyFormat(player),1024);}
    public ProcessingMenu(int id,Inventory inventory,FriendlyByteBuf buffer){this(id,inventory,buffer.readBlockPos(),buffer.readUnsignedByte(),null,buffer.readUtf(1024),new SimpleContainer(45));}
    public ProcessingMenu(int id,Inventory inventory,ProcessingEntity machine){this(id,inventory,machine.getBlockPos(),machine.inputSlots(),machine,energyFormat((ServerPlayer)inventory.player),new SimpleContainer(45));}
    private ProcessingMenu(int id,Inventory inventory,BlockPos pos,int inputSlots,ProcessingEntity machine,String energyFormat,SimpleContainer display){
        super(SlimefunForge.PROCESSING_MENU.get(),id,inventory,display,5);this.pos=pos;this.machine=machine;this.display=display;level=inventory.player.level();owner=inventory.player;this.energyFormat=energyFormat;
        if(inputSlots!=2&&inputSlots!=6)throw new IllegalArgumentException("Invalid processing input count");
        this.inputSlots=inputSlots;inputs=inputSlots==6?new int[]{10,11,19,20,28,29}:new int[]{19,20};
        Container contents=machine==null?new SimpleContainer(inputSlots+2):machine.inventory;contents.startOpen(owner);MenuItems.processing(display,inputSlots==6);
        for(int i=0;i<45;i++){
            int mapped=i==24?inputSlots:i==25?inputSlots+1:-1;for(int n=0;n<inputs.length;n++)if(inputs[n]==i)mapped=n;
            final int storage=mapped;var old=slots.get(i);var slot=new Slot(storage<0?display:contents,storage<0?i:storage,old.x,old.y){
                @Override public boolean mayPlace(ItemStack stack){return storage>=0&&storage<inputSlots;}
                @Override public boolean mayPickup(Player player){return storage>=0&&stillValid(player);}
            };slot.index=i;slots.set(i,slot);
        }
        data=machine==null?new SimpleContainerData(4):new ContainerData(){
            public int get(int index){return switch(index){case 0->machine.energy();case 1->machine.definition.capacity();case 2->machine.progress();case 3->machine.total();default->0;};}
            public void set(int index,int value){}public int getCount(){return 4;}
        };addDataSlots(data);
    }
    public int energy(){return data.get(0);}public int capacity(){return data.get(1);}public int progress(){return data.get(2);}public int total(){return data.get(3);}
    public Component energyLabel(){return Component.translatableWithFallback("",energyFormat,energy(),capacity());}
    @Override public void broadcastChanges(){
        if(machine!=null&&(previousProgress!=machine.progress()||previousTotal!=machine.total())){
            previousProgress=machine.progress();previousTotal=machine.total();display.setItem(22,MenuItems.progress(MenuItems.progressIcon(machine.definition.id()),previousProgress,previousTotal));
        }super.broadcastChanges();
    }
    @Override public void removed(Player player){super.removed(player);if(machine!=null)machine.inventory.stopOpen(player);}
    @Override public boolean stillValid(Player player){
        return player==owner&&player.isAlive()&&player.level()==level&&player.distanceToSqr(pos.getCenter())<=64
            &&(!(player instanceof ServerPlayer server)||level.mayInteract(player,pos)&&ResearchData.canUse(server,new ItemStack(level.getBlockState(pos).getBlock())))
            &&(machine==null?level.getBlockState(pos).getBlock() instanceof ProcessingBlock:!machine.isRemoved()&&level.getBlockEntity(pos)==machine);
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(player.containerMenu==this&&stillValid(player))super.clicked(slot,button,type,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(player.containerMenu!=this||index<0||index>=slots.size()||!stillValid(player))return ItemStack.EMPTY;
        var slot=slots.get(index);var stack=slot.getItem();if(stack.isEmpty()||!slot.mayPickup(player))return ItemStack.EMPTY;var before=stack.copy();
        if(index<45){if(!moveItemStackTo(stack,45,slots.size(),true))return ItemStack.EMPTY;}
        else{for(int input:inputs)if(!stack.isEmpty())moveItemStackTo(stack,input,input+1,false);if(stack.getCount()==before.getCount())return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return before;
    }
}
