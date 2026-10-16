package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Seven real slots in the RC-37 layout, carried by the native chest protocol. */
public final class ReactorMenu extends ChestMenu {
    private static final int[] SLOTS={19,28,37,25,34,43,40};
    private final BlockEntity entity;
    private final ServerPlayer owner;
    private final SimpleContainer display;
    private int progress=-1,total=-1,mode=-1,link=-1;
    public BlockEntity entity() {return entity;}
    public ReactorMenu(int id,Inventory inv,BlockEntity entity,Container contents) {this(id,inv,entity,contents,new SimpleContainer(54));}
    private ReactorMenu(int id,Inventory inv,BlockEntity entity,Container contents,SimpleContainer display) {
        super(MenuType.GENERIC_9x6,id,inv,display,6);this.entity=entity;owner=(ServerPlayer)inv.player;this.display=display;
        MenuItems.fill(display,Items.GRAY_STAINED_GLASS_PANE,0,2,3,4,5,6,8,12,13,14,21,23);
        MenuItems.fill(display,Items.LIME_STAINED_GLASS_PANE,9,10,11,18,20,27,29,36,38,45,46,47);
        MenuItems.fill(display,Items.CYAN_STAINED_GLASS_PANE,15,16,17,24,26,33,35,42,44,51,52,53);
        MenuItems.fill(display,Items.GREEN_STAINED_GLASS_PANE,30,31,32,39,41,48,50);
        boolean port=entity instanceof ReactorPortEntity;
        display.setItem(1,MenuItems.label(owner,entity instanceof ReactorEntity r&&r.star()?Items.NETHER_STAR:SlimefunForge.item("uranium"),port?"reactor.port_fuel":"reactor.fuel"));
        display.setItem(7,MenuItems.label(owner,entity instanceof ReactorEntity r?r.coolant():SlimefunForge.item("reactor_collant_cell"),port?"reactor.port_coolant":"reactor.coolant"));
        display.setItem(22,port?MenuItems.label(owner,SlimefunForge.item("plutonium"),"reactor.byproduct"):MenuItems.blank(Items.BLACK_STAINED_GLASS_PANE));
        for(int i=0;i<54;i++) {int mapped=-1;for(int n=0;n<SLOTS.length;n++) if(SLOTS[n]==i) mapped=n;final int input=mapped;var old=slots.get(i);var slot=new Slot(input>=0?contents:display,input>=0?input:i,old.x,old.y) {
            @Override public boolean mayPlace(ItemStack stack) {return input>=0 && input<6;}
            @Override public boolean mayPickup(Player player) {return input>=0 && stillValid(player);}
        };slot.index=i;slots.set(i,slot);}
        broadcastChanges();
    }
    private BlockEntity linked() {return entity instanceof ReactorEntity r?r.port():((ReactorPortEntity)entity).reactor();}
    @Override public void broadcastChanges() {
        if(entity!=null) {
            if(entity instanceof ReactorEntity r) {
                int currentMode=r.productionMode()?1:0;
                if(mode!=currentMode) {mode=currentMode;display.setItem(4,MenuItems.label(owner,SlimefunForge.item(r.productionMode()?"plutonium":"nuclear_reactor"),r.productionMode()?"reactor.production":"reactor.electricity"));}
                if(progress!=r.progress()||total!=r.total()) {progress=r.progress();total=r.total();display.setItem(22,MenuItems.progress(r.star()?Items.NETHER_STAR:SlimefunForge.item("lava_crystal"),progress,total));}
            }
            int currentLink=linked()==null?0:1;
            if(link!=currentLink) {link=currentLink;display.setItem(49,MenuItems.label(owner,link==0?Items.RED_WOOL:Items.GREEN_WOOL,"reactor."+(entity instanceof ReactorEntity?"port_":"reactor_")+(link==0?"missing":"linked")));}
        }
        super.broadcastChanges();
    }
    @Override public boolean stillValid(Player p) {return p==owner && p.isAlive() && p.level()==entity.getLevel() && !entity.isRemoved() && p.level().getBlockEntity(entity.getBlockPos())==entity && p.distanceToSqr(entity.getBlockPos().getCenter())<=64 && p.level().mayInteract(p,entity.getBlockPos()) && (!(p instanceof ServerPlayer s) || ResearchData.canUse(s,new ItemStack(entity.getBlockState().getBlock())));}
    @Override public void clicked(int slot,int button,ClickType type,Player p) {
        if(p.containerMenu!=this||!stillValid(p)) return;
        if(slot==4 && type==ClickType.PICKUP && entity instanceof ReactorEntity r) {r.setProductionMode(!r.productionMode());broadcastChanges();return;}
        if(slot==49 && type==ClickType.PICKUP && p instanceof ServerPlayer s) {var target=linked();if(target instanceof MenuProvider provider && !target.isRemoved() && s.distanceToSqr(target.getBlockPos().getCenter())<=64 && s.level().mayInteract(s,target.getBlockPos()) && ResearchData.canUse(s,new ItemStack(target.getBlockState().getBlock()))) GuideContent.open(s,provider);return;}
        super.clicked(slot,button,type,p);
    }
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if(p.containerMenu!=this || index<0 || index>=slots.size() || !stillValid(p)) return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem() || !slot.mayPickup(p)) return ItemStack.EMPTY;var stack=slot.getItem();var original=stack.copy();
        if(index<54) {if(!moveItemStackTo(stack,54,slots.size(),true)) return ItemStack.EMPTY;}
        else {boolean coolant=entity instanceof ReactorPortEntity port?port.coolant(stack):((ReactorEntity)entity).acceptsCoolant(stack);for(int n=coolant?3:0;n<(coolant?6:3) && !stack.isEmpty();n++) moveItemStackTo(stack,SLOTS[n],SLOTS[n]+1,false);if(stack.getCount()==original.getCount()) return ItemStack.EMPTY;}
        if(stack.isEmpty()) slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return original;
    }
}
