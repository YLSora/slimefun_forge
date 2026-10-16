package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class CargoEntity extends BlockEntity implements MenuProvider,io.github.slimefun4.forge.api.CargoApi.Node {
    public final Catalog.Entry definition;
    public final SimpleContainer filter=new SimpleContainer(9) {
        @Override public void setChanged() { super.setChanged(); CargoEntity.this.setChanged(); }
    };
    private int channel;
    private boolean blacklist, checkLore=true, roundRobin, smartFill, visualizer=true;
    private Component status=Component.translatable("menu.slimefun.cargo.waiting").withStyle(net.minecraft.ChatFormatting.GRAY);
    public CargoEntity(BlockPos pos,BlockState state) {
        super(SlimefunForge.CARGO_ENTITY.get(),pos,state); definition=((CargoBlock)state.getBlock()).definition;
    }
    public boolean manager() { return definition.id().equals("cargo_manager"); }
    public boolean connector() { return definition.id().equals("cargo_node"); }
    public boolean input() { return definition.id().equals("cargo_node_input"); }
    @Override public io.github.slimefun4.forge.api.CargoApi.Role cargoRole(){return manager()?io.github.slimefun4.forge.api.CargoApi.Role.MANAGER:connector()?io.github.slimefun4.forge.api.CargoApi.Role.CONNECTOR:input()?io.github.slimefun4.forge.api.CargoApi.Role.INPUT:io.github.slimefun4.forge.api.CargoApi.Role.OUTPUT;}
    @Override public Direction attachedFace(){return getBlockState().getValue(HeadBlock.POSE)<16?null:HeadBlock.wallFacing(getBlockState()).getOpposite();}
    @Override public void cargoStatus(Component value){status(value);}
    public boolean filtered() { return input()||definition.id().equals("cargo_node_output_advanced"); }
    public int channel() { return channel; }
    public void setChannel(int value) { channel=Math.floorMod(value,16); setChanged(); }
    public void setRoundRobin(boolean value) { roundRobin=value; setChanged(); }
    public void setSmartFill(boolean value) { smartFill=value; setChanged(); }
    public boolean smartFill() { return smartFill; }
    public boolean roundRobin() { return roundRobin; }
    public Component status(){return status;}
    public void status(Component value) { if(!status.equals(value)){status=value;sync();} }
    private void sync(){if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    public void toggleVisualizer(){visualizer=!visualizer;setChanged();sync();}
    @Override public CompoundTag getUpdateTag(){var tag=new CompoundTag();if(manager()){tag.putString("status",Component.Serializer.toJson(status));tag.putBoolean("visualizer",visualizer);}return tag;}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return manager()?net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this):null;}
    @Override public void handleUpdateTag(CompoundTag tag){if(tag.contains("status",8)){var text=Component.Serializer.fromJson(tag.getString("status"));if(text!=null)status=text;}visualizer=tag.getBoolean("visualizer");}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet){if(packet.getTag()!=null)handleUpdateTag(packet.getTag());}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return manager()?new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0,1.25,0).inflate(4):super.getRenderBoundingBox();}
    public boolean visualizer() { return visualizer; }
    public boolean accepts(ItemStack item) {
        boolean accepted=test(item);
        if(DebugLogging.enabled(level,DebugLogging.CARGO))DebugLogging.log(level,DebugLogging.CARGO,"Filter {} {} item={} blacklist={} lore={} accepted={}",level.dimension().location(),worldPosition,item,blacklist,checkLore,accepted);
        return accepted;
    }
    private boolean test(ItemStack item) {
        if(item.isEmpty()) return false;
        if(!filtered()) return true;
        for(int i=0;i<9;i++) {
            var template=filter.getItem(i);
            if(CargoFilter.matches(item,template,checkLore)) return !blacklist;
        }
        return blacklist;
    }
    @Override public void onLoad() { super.onLoad(); CargoNetwork.add(this); }
    @Override public void setRemoved() { CargoNetwork.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { CargoNetwork.remove(this); super.onChunkUnloaded(); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        var slots=NonNullList.withSize(9,ItemStack.EMPTY); for(int i=0;i<9;i++) slots.set(i,filter.getItem(i)); ContainerHelper.saveAllItems(tag,slots);
        tag.putInt("channel",channel); tag.putBoolean("blacklist",blacklist); tag.putBoolean("lore",checkLore);
        tag.putBoolean("round_robin",roundRobin); tag.putBoolean("smart_fill",smartFill); tag.putBoolean("visualizer",visualizer);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        var slots=NonNullList.withSize(9,ItemStack.EMPTY); ContainerHelper.loadAllItems(tag,slots); for(int i=0;i<9;i++) filter.setItem(i,slots.get(i));
        channel=Math.floorMod(tag.getInt("channel"),16); blacklist=tag.getBoolean("blacklist"); checkLore=!tag.contains("lore")||tag.getBoolean("lore");
        roundRobin=tag.getBoolean("round_robin"); smartFill=tag.getBoolean("smart_fill"); visualizer=!tag.contains("visualizer")||tag.getBoolean("visualizer");
    }
    @Override public Component getDisplayName() { return Component.translatable("menu.slimefun.cargo."+definition.id()); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return manager()||connector()?null:new CargoMenu(id,inventory,this); }
    private static final class CargoMenu extends ChestMenu {
        private final CargoEntity node;
        private final SimpleContainer display;
        private final net.minecraft.server.level.ServerPlayer owner;
        private static final int[] FILTER={19,20,21,28,29,30,37,38,39};
        private int previousConfiguration=-1;
        CargoMenu(int id,Inventory inventory,CargoEntity node) { this(id,inventory,node,new SimpleContainer(node.filtered()?54:27)); }
        CargoMenu(int id,Inventory inventory,CargoEntity node,SimpleContainer display) {
            super(node.filtered()?MenuType.GENERIC_9x6:MenuType.GENERIC_9x3,id,inventory,display,display.getContainerSize()/9); this.node=node; this.display=display; this.owner=(net.minecraft.server.level.ServerPlayer)inventory.player;
            for(int i=0;i<display.getContainerSize();i++) {
                var old=slots.get(i); int filter=filterIndex(i); final boolean editable=filter>=0;
                var slot=new Slot(editable?node.filter:display,editable?filter:i,old.x,old.y) {
                    @Override public boolean mayPlace(ItemStack stack) { return editable; }
                    @Override public boolean mayPickup(Player player) { return editable; }
                };
                slot.index=i; slots.set(i,slot);
            }
            refresh();
        }
        private int filterIndex(int slot){if(node.filtered())for(int i=0;i<FILTER.length;i++)if(FILTER[i]==slot)return i;return -1;}
        private int configuration(){return node.channel|(node.blacklist?16:0)|(node.checkLore?32:0)|(node.roundRobin?64:0)|(node.smartFill?128:0);}
        private Component text(String key,Object...args){return Component.translatable("menu.slimefun.cargo."+key,args);}
        private Component toggle(String key,boolean enabled){return text(key,Component.literal(enabled?"✔":"✘").withStyle(enabled?net.minecraft.ChatFormatting.DARK_GREEN:net.minecraft.ChatFormatting.DARK_RED));}
        private ItemStack icon(int slot,Item item,Component label,Component...lore) {
            var stack=new ItemStack(item).setHoverName(GuideContent.localize(owner,label));var lines=new net.minecraft.nbt.ListTag();
            for(var line:lore)lines.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(GuideContent.localize(owner,line))));
            stack.getOrCreateTagElement("display").put("Lore",lines);stack.getOrCreateTag().putBoolean("slimefun_preview_lore",true);display.setItem(slot,stack);return stack;
        }
        private void refresh() {
            previousConfiguration=configuration();
            for(int i=0;i<display.getContainerSize();i++)if(filterIndex(i)<0)icon(i,Items.CYAN_STAINED_GLASS_PANE,Component.literal(" "));
            int channel=node.filtered()?42:13;
            var blank=Component.empty();
            icon(channel-1,SlimefunForge.item("cargo_manager"),text("previous"),blank,text("previous_hint")).getOrCreateTag().putInt("CustomModelData",1);
            icon(channel,net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("minecraft",DyeColor.byId(node.channel).getName()+"_wool")),text("channel",node.channel+1));
            icon(channel+1,SlimefunForge.item("cargo_manager"),text("next"),blank,text("next_hint")).getOrCreateTag().putInt("CustomModelData",2);
            if(node.filtered()) {
                icon(2,Items.PAPER,text("items"),blank,text("items_hint.0"),text("items_hint.1"));
                icon(15,node.blacklist?Items.BLACK_WOOL:Items.WHITE_WOOL,text(node.blacklist?"blacklist":"whitelist"),blank,text(node.blacklist?"whitelist_hint":"blacklist_hint"));
                icon(25,Items.MAP,toggle("lore",node.checkLore),blank,text("lore_hint"));
            }
            if(node.input()) {
                icon(24,SlimefunForge.item("energy_regulator"),toggle("round_robin",node.roundRobin),blank,text(node.roundRobin?"round_robin_disable":"round_robin_enable"),text("round_robin_hint"));
                icon(16,node.smartFill?Items.WRITTEN_BOOK:Items.WRITABLE_BOOK,toggle("smart_fill",node.smartFill),blank,text(node.smartFill?"smart_fill_disable":"smart_fill_enable"),blank,text("smart_fill_hint.0"),text("smart_fill_hint.1"),text("smart_fill_hint.2"),text("smart_fill_hint.3"),text("smart_fill_hint.4"));
            }
        }
        @Override public void broadcastChanges() {
            if(node!=null && previousConfiguration!=configuration()) refresh();
            super.broadcastChanges();
        }
        @Override public boolean stillValid(Player player) { return player==owner && player.isAlive() && !node.isRemoved() && player.level()==node.level && node.level.getBlockEntity(node.worldPosition)==node && player.distanceToSqr(node.worldPosition.getCenter())<=64 && node.level.mayInteract(player,node.worldPosition) && ResearchData.canUse(owner,new ItemStack(node.getBlockState().getBlock())); }
        @Override public void clicked(int slot,int button,ClickType type,Player player) {
            if(player.containerMenu!=this || !stillValid(player)) return;
            if(slot>=0 && slot<display.getContainerSize() && filterIndex(slot)<0) {
                if(type!=ClickType.PICKUP || (button!=0 && button!=1)) return;
                int channel=node.filtered()?42:13;
                if(slot==channel-1) node.setChannel(node.channel-1);
                if(slot==channel+1) node.setChannel(node.channel+1);
                if(node.filtered() && slot==15) node.blacklist=!node.blacklist;
                if(node.filtered() && slot==25) node.checkLore=!node.checkLore;
                if(node.input() && slot==24) node.roundRobin=!node.roundRobin;
                if(node.input() && slot==16) node.smartFill=!node.smartFill;
                node.setChanged(); refresh(); return;
            }
            super.clicked(slot,button,type,player);
        }
        @Override public ItemStack quickMoveStack(Player player,int index) {
            int size=display.getContainerSize();
            if(player.containerMenu!=this||!stillValid(player)||!node.filtered()||index<0||index>=slots.size()||(index<size&&filterIndex(index)<0)) return ItemStack.EMPTY;
            var slot=slots.get(index); var stack=slot.getItem(); if(stack.isEmpty()) return ItemStack.EMPTY;
            var before=stack.copy();
            if(index<size) { if(!moveItemStackTo(stack,size,slots.size(),true)) return ItemStack.EMPTY; }
            else { boolean moved=false;for(int target:FILTER)moved=moveItemStackTo(stack,target,target+1,false)||moved;if(!moved)return ItemStack.EMPTY; }
            if(stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            return before;
        }
    }
}
