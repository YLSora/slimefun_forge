package io.github.slimefun4.forge;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Vanilla container synchronization; the ten physical outputs replace display slots. */
public final class GeoMinerMenu extends ChestMenu {
    private static final int[] OUTPUTS={29,30,31,32,33,38,39,40,41,42};
    private final GeoMinerEntity machine;
    private final net.minecraft.server.level.ServerPlayer owner;
    private final SimpleContainer display;
    private int progress=-1,total=-1;
    public GeoMinerMenu(int id,Inventory inventory,GeoMinerEntity machine) { this(id,inventory,machine,new SimpleContainer(54)); }
    private GeoMinerMenu(int id,Inventory inventory,GeoMinerEntity machine,SimpleContainer display) {
        super(MenuType.GENERIC_9x6,id,inventory,display,6); this.machine=machine; owner=(net.minecraft.server.level.ServerPlayer)inventory.player; this.display=display;
        MenuItems.fill(display,Items.GRAY_STAINED_GLASS_PANE,0,1,2,3,5,6,7,8,9,10,11,12,13,14,15,16,17,18,26,27,35,36,44,45,53);
        MenuItems.fill(display,Items.ORANGE_STAINED_GLASS_PANE,19,20,21,22,23,24,25,28,34,37,43,46,47,48,49,50,51,52);
        MenuItems.fill(display,Items.BLACK_STAINED_GLASS_PANE,4);
        for(int i=0;i<54;i++) {
            final int index=i; int output=-1; for(int n=0;n<OUTPUTS.length;n++) if(OUTPUTS[n]==i) output=n;
            var old=slots.get(i); final boolean extract=output>=0;
            var slot=new Slot(extract?machine.inventory:display,extract?output:i,old.x,old.y) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return extract && stillValid(player); }
            }; slot.index=index; slots.set(i,slot);
        }
        broadcastChanges();
    }
    @Override public void broadcastChanges() {
        if(machine!=null) {
            int currentTotal=machine.pending()==null?0:GeoMinerEntity.CYCLES;
            if(progress!=machine.progress()||total!=currentTotal) {
                progress=machine.progress();total=currentTotal;
                display.setItem(4,MenuItems.progress(Items.DIAMOND_PICKAXE,progress,total));
            }
        }
        super.broadcastChanges();
    }
    @Override public boolean stillValid(Player player) { return player==owner && player.isAlive() && player.level()==machine.getLevel() && !machine.isRemoved() && player.level().getBlockEntity(machine.getBlockPos())==machine && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(machine.getBlockPos()))<=64 && player.level().mayInteract(player,machine.getBlockPos()) && (!(player instanceof net.minecraft.server.level.ServerPlayer server) || ResearchData.canUse(server,new ItemStack(SlimefunForge.GEO_MINER.get()))); }
    @Override public void clicked(int slot,int button,ClickType type,Player player) { if(player.containerMenu==this&&stillValid(player)) super.clicked(slot,button,type,player); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(player.containerMenu!=this || index<0 || index>=54 || !stillValid(player)) return ItemStack.EMPTY;
        var slot=slots.get(index); if(!slot.mayPickup(player) || !slot.hasItem()) return ItemStack.EMPTY;
        var stack=slot.getItem(); var before=stack.copy(); if(!moveItemStackTo(stack,54,slots.size(),true)) return ItemStack.EMPTY;
        if(stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player,stack); return before;
    }
}
