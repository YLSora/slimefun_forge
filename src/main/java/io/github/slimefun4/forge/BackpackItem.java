package io.github.slimefun4.forge;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Native item payload and issued server inventory; no external database or online lookup. */
public class BackpackItem extends Item implements io.github.slimefun4.forge.api.BackpackApi.Bag {
    public final int rows;
    @Override public int rows(ItemStack handle){return rows;}
    public BackpackItem(int rows) { super(new Properties().stacksTo(1)); if(rows<1||rows>6) throw new IllegalArgumentException("rows"); this.rows=rows; }
    @Override public boolean canFitInsideContainerItems() { return false; }
    @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<net.minecraft.network.chat.Component> lines,TooltipFlag flag){String identity=BackpackData.identity(stack);if(!identity.isEmpty())lines.add(net.minecraft.network.chat.Component.literal("ID: "+identity).withStyle(net.minecraft.ChatFormatting.GRAY));}
    public boolean accepts(ItemStack stack) { return !(stack.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Bag)&&stack.getItem().canFitInsideContainerItems(); }
    @Override public boolean onDroppedByPlayer(ItemStack stack,Player player) {
        if(player.containerMenu instanceof BagMenu) {
            if(player instanceof ServerPlayer server) server.containerMenu.sendAllDataToRemote();
            return false;
        }
        return true;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(stack.getCount()!=1) return InteractionResultHolder.fail(stack);
        if(player instanceof ServerPlayer server&&!open(server,hand))return InteractionResultHolder.fail(stack);
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    private static boolean usable(ServerPlayer player,ItemStack stack){AddonItems.check(player);return player.isAlive()&&!player.isSpectator()&&player.server.getPlayerList().getPlayer(player.getUUID())==player&&stack.getCount()==1&&ResearchData.canUse(player,stack);}
    public static boolean open(ServerPlayer player,InteractionHand hand){
        var stack=player.getItemInHand(hand);if(!usable(player,stack)||!(stack.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Bag))return false;
        var contents=BackpackData.get(player.server).inventory(player,stack);if(contents==null)return false;
        for(var viewer:player.server.getPlayerList().getPlayers())if(viewer!=player&&viewer.containerMenu instanceof BagMenu menu&&menu.inventory()==contents)return false;
        int slot=hand==InteractionHand.MAIN_HAND?player.getInventory().selected:40;
        return player.openMenu(new SimpleMenuProvider((id,inv,p)->new BagMenu(id,inv,contents,contents.getContainerSize()/9,slot,stack),stack.getHoverName())).isPresent();
    }
    public static boolean openEnder(ServerPlayer player,InteractionHand hand){
        var stack=player.getItemInHand(hand);if(!usable(player,stack))return false;
        return player.openMenu(new SimpleMenuProvider((id,inv,p)->new ChestMenu(MenuType.GENERIC_9x3,id,inv,player.getEnderChestInventory(),3){
            @Override public boolean stillValid(Player p){return p==player&&usable(player,stack)&&player.getItemInHand(hand)==stack;}
        },stack.getHoverName())).isPresent();
    }
    public static final class BagInventory extends SimpleContainer {
        private final ItemStack owner;
        private boolean loading=true;
        private long changes;
        public BagInventory(ItemStack owner,int size) {
            super(size); this.owner=owner;
            var items=NonNullList.withSize(size,ItemStack.EMPTY);
            if(owner.hasTag()) ContainerHelper.loadAllItems(owner.getTag().getCompound("slimefun_inventory"),items);
            for(int i=0;i<size;i++) setItem(i,items.get(i));
            loading=false;
        }
        public ItemStack type(){return owner.copy();}
        public long revision(){return changes;}
        @Override public boolean canPlaceItem(int slot,ItemStack stack) { return !(stack.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Bag)&&owner.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Bag bag && bag.accepts(stack.copy()); }
        @Override public void setChanged() {
            super.setChanged();
            if(loading || owner==null) return;
            var items=NonNullList.withSize(getContainerSize(),ItemStack.EMPTY);
            for(int i=0;i<items.size();i++) items.set(i,getItem(i));
            owner.getOrCreateTag().put("slimefun_inventory",ContainerHelper.saveAllItems(new CompoundTag(),items));
            changes++;
        }
    }
    public static final class BagMenu extends ChestMenu {
        private final int lockedSlot;
        private final ItemStack bag;
        private final Inventory inventory;
        private final int size;
        private final BagInventory contents;
        private long mirrored=-1;
        public BagMenu(int id,Inventory inventory,BagInventory contents,int rows,int lockedSlot,ItemStack bag) {
            super(menuType(rows),id,inventory,contents,rows);
            this.lockedSlot=lockedSlot; this.bag=bag; this.inventory=inventory; this.size=rows*9; this.contents=contents;
            for(int i=0;i<size;i++) {
                var old=slots.get(i);
                var restricted=new Slot(contents,i,old.x,old.y) {
                    @Override public boolean mayPlace(ItemStack stack) { return accepts(stack); }
                };
                restricted.index=i; slots.set(i,restricted);
            }
        }
        private static MenuType<?> menuType(int rows) { return switch(rows) { case 1->MenuType.GENERIC_9x1; case 2->MenuType.GENERIC_9x2; case 3->MenuType.GENERIC_9x3; case 4->MenuType.GENERIC_9x4; case 5->MenuType.GENERIC_9x5; default->MenuType.GENERIC_9x6; }; }
        @Override public boolean stillValid(Player player) { return inventory.player==player && player instanceof ServerPlayer server && usable(server,bag) && inventory.getItem(lockedSlot)==bag; }
        public BagInventory contents(ItemStack stack) { return stack==bag && stillValid(inventory.player)?contents:null; }
        public BagInventory inventory(){return contents;}
        private boolean accepts(ItemStack stack){return contents.canPlaceItem(0,stack);}
        @Override public void broadcastChanges(){
            if(contents!=null&&mirrored!=contents.changes){
                BackpackData.sync(bag,contents);mirrored=contents.changes;
                // Chest menus omit the offhand slot; update its item identity and contents directly.
                if(lockedSlot==40&&inventory.player instanceof ServerPlayer player)player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,lockedSlot,bag));
            }
            super.broadcastChanges();
        }
        private boolean locked(int slot) {
            return slot>=0 && slot<slots.size() && (slots.get(slot).getItem().getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Bag
                || (slots.get(slot).container==inventory && slots.get(slot).getContainerSlot()==lockedSlot));
        }
        @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot) { return !locked(slot.index) && super.canTakeItemForPickAll(stack,slot); }
        @Override public void clicked(int slot,int button,ClickType type,Player player) {
            if(player.containerMenu!=this||!stillValid(player) || locked(slot) || (type==ClickType.SWAP && button==lockedSlot)) return;
            if(slot>=0 && slot<size && !getCarried().isEmpty() && !accepts(getCarried())) return;
            super.clicked(slot,button,type,player);
        }
        @Override public ItemStack quickMoveStack(Player player,int slot) {
            if(slot<0 || slot>=slots.size() || locked(slot) || !stillValid(player)) return ItemStack.EMPTY;
            var entry=slots.get(slot); var stack=entry.getItem();
            if(slot>=size && !accepts(stack)) return ItemStack.EMPTY;
            return super.quickMoveStack(player,slot);
        }
        @Override public void removed(Player player) {
            contents.setChanged();BackpackData.sync(bag,contents);super.removed(player);
            if(player instanceof ServerPlayer server) server.playNotifySound(SoundEvents.HORSE_ARMOR,SoundSource.PLAYERS,1F,1F);
        }
    }
}
