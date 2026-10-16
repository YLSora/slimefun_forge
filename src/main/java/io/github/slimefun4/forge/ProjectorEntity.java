package io.github.slimefun4.forge;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Text lives with the block, rendered client-side; no armor stand entity or server ticker. */
public final class ProjectorEntity extends BlockEntity implements MenuProvider {
    private static final Map<net.minecraft.server.level.ServerPlayer,ProjectorEntity> PENDING=new ConcurrentHashMap<>();
    public static void cancelEdit(net.minecraft.server.level.ServerPlayer player) { PENDING.remove(player); }
    private UUID owner;
    private String text;
    private double offset=0.5;
    public ProjectorEntity(BlockPos pos,BlockState state) { super(SlimefunForge.PROJECTOR_ENTITY.get(),pos,state); }
    public Component text() { return text==null?Component.translatable("menu.slimefun.projector.default"):Component.literal(text.replaceAll("&([0-9a-fk-orA-FK-OR])","\u00a7$1")); }
    public double offset() { return offset; }
    public void setOwner(UUID value) { owner=value; changed(); }
    public boolean ownedBy(Player player) { return player.getUUID().equals(owner); }
    private void changed() { setChanged(); if(level!=null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); if(owner!=null) tag.putUUID("owner",owner); if(text!=null)tag.putString("text",text); tag.putDouble("offset",offset);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;
        text=tag.contains("text")?tag.getString("text"):null;
        if(text!=null&&text.length()>256) text=text.substring(0,256);
        offset=tag.contains("offset")?tag.getDouble("offset"):0.5;
        if(!Double.isFinite(offset)) offset=0.5;
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition).expandTowards(0,1+offset,0).inflate(4); }
    @Override public Component getDisplayName() { return Component.translatable("menu.slimefun.projector.title"); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new Editor(id,inventory,this); }
    @SubscribeEvent public static void chat(ServerChatEvent event) {
        var player=event.getPlayer(); var pending=PENDING.get(player); if(pending==null) return;
        event.setCanceled(true);
        var message=event.getRawText().substring(0,Math.min(256,event.getRawText().length()));
        // Forge dispatches ServerChatEvent from its asynchronous chat decorator. World and menu access stays on the server thread.
        player.getServer().execute(() -> {
            if(!PENDING.remove(player,pending))return;
            var level=player.serverLevel(); var pos=pending.getBlockPos();
            if(player.isRemoved()||!player.isAlive()||pending.isRemoved()||pending.getLevel()!=level||!level.hasChunkAt(pos)||player.distanceToSqr(pos.getCenter())>64||!level.mayInteract(player,pos)) return;
            if(level.getBlockEntity(pos)==pending&&pending.ownedBy(player)&&ResearchData.canUse(player,new ItemStack(pending.getBlockState().getBlock()))) {
                pending.text=message; pending.changed(); GuideContent.open(player,pending);
            }
        });
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { PENDING.remove(event.getEntity()); }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) { PENDING.keySet().removeIf(player->player.server==event.getServer()); }
    private static final class Editor extends ChestMenu {
        private final ProjectorEntity projector;
        private final net.minecraft.server.level.ServerPlayer owner;
        Editor(int id,Inventory inventory,ProjectorEntity projector) {
            super(MenuType.GENERIC_9x1,id,inventory,new SimpleContainer(9),1); this.projector=projector; this.owner=(net.minecraft.server.level.ServerPlayer)inventory.player;
            for(int i=0;i<9;i++) {
                var old=slots.get(i); var slot=new Slot(old.container,i,old.x,old.y) {
                    @Override public boolean mayPlace(ItemStack item) { return false; }
                    @Override public boolean mayPickup(Player player) { return false; }
                };
                slot.index=i; slots.set(i,slot);
            }
            refresh();
        }
        private void refresh() {
            slots.get(0).set(MenuItems.label(owner,Items.NAME_TAG,"projector.text",projector.text()));
            slots.get(1).set(MenuItems.label(owner,Items.CLOCK,"projector.height",String.format(java.util.Locale.ROOT,"%.1f",projector.offset+1)));
        }
        @Override public boolean stillValid(Player player) { return player==owner&&player.isAlive()&&projector.ownedBy(player)&&!projector.isRemoved()&&player.level()==projector.level&&projector.level.getBlockEntity(projector.worldPosition)==projector&&player.distanceToSqr(projector.worldPosition.getCenter())<=64&&player.level().mayInteract(player,projector.worldPosition)&&ResearchData.canUse(owner,new ItemStack(projector.getBlockState().getBlock())); }
        @Override public void clicked(int slot,int button,ClickType type,Player player) {
            if(player.containerMenu!=this||!stillValid(player)) return;
            if(slot>=0&&slot<9) {
                if(type!=ClickType.PICKUP || (button!=0&&button!=1)) return;
                if(slot==0) {
                    if(player instanceof net.minecraft.server.level.ServerPlayer server){Guide.cancelSearch(server);GpsWaypoints.cancelPrompt(server);AndroidScripts.cancelPrompt(server);ElevatorMenu.cancelPrompt(server);}
                    PENDING.put((net.minecraft.server.level.ServerPlayer)player,projector);
                    player.closeContainer(); Messages.chat(player,Component.translatable("message.slimefun.projector_text"));
                } else if(slot==1) {
                    double proposed=Math.rint((projector.offset+(button==0?0.1:-0.1))*10)/10;
                    if(projector.worldPosition.getY()+proposed>=projector.level.getMinBuildHeight() && projector.worldPosition.getY()+proposed<projector.level.getMaxBuildHeight()) projector.offset=proposed;
                    projector.changed(); refresh();
                }
                return;
            }
            super.clicked(slot,button,type,player);
        }
        @Override public ItemStack quickMoveStack(Player player,int slot) { return ItemStack.EMPTY; }
    }
}
