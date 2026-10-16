package io.github.slimefun4.forge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ElevatorMenu extends ChestMenu {
    private static final Map<ServerPlayer,ElevatorEntity> PROMPTS=new ConcurrentHashMap<>();
    private static final Map<ServerPlayer,GlobalPos> CONTACTS=new WeakHashMap<>();
    private final ElevatorEntity source;
    private final ServerPlayer owner;
    private final boolean editor;
    private final List<ElevatorEntity> floors;
    private int page;
    static Component text(String key,Object... args){return Component.translatable("menu.slimefun.elevator."+key,args);}
    public static void cancelPrompt(ServerPlayer player){PROMPTS.remove(player);}
    static boolean enter(ServerPlayer player,BlockPos pos){var contact=GlobalPos.of(player.level().dimension(),pos);return !contact.equals(CONTACTS.put(player,contact));}
    static void open(ServerPlayer player,ElevatorEntity source,boolean editor){
        if(!source.accessible(player)||editor&&!source.ownedBy(player))return;
        var floors=new ArrayList<ElevatorEntity>();var level=player.serverLevel();var pos=source.getBlockPos();
        if(!editor)for(int y=level.getMaxBuildHeight()-1;y>=level.getMinBuildHeight();y--)if(level.getBlockEntity(new BlockPos(pos.getX(),y,pos.getZ())) instanceof ElevatorEntity floor)floors.add(floor);
        if(!editor&&floors.size()<2){Messages.chat(player,text("no_destinations"));return;}
        player.openMenu(new SimpleMenuProvider((id,inv,p)->new ElevatorMenu(id,inv,source,editor,floors),GuideContent.localize(player,text(editor?"editor":"select"))));
    }
    private ElevatorMenu(int id,Inventory inv,ElevatorEntity source,boolean editor,List<ElevatorEntity> floors){
        super(editor?MenuType.GENERIC_9x1:MenuType.GENERIC_9x4,id,inv,new SimpleContainer(editor?9:36),editor?1:4);
        owner=(ServerPlayer)inv.player;this.source=source;this.editor=editor;this.floors=List.copyOf(floors);render();
    }
    private void render(){
        getContainer().clearContent();
        if(editor)getSlot(4).set(MenuItems.label(owner,Items.NAME_TAG,"elevator.name",source.name()));
        else {
            for(int i=0;i<27&&page*27+i<floors.size();i++) {
                int index=page*27+i;var floor=floors.get(index);
                getSlot(i).set(MenuItems.label(owner,floor==source?Items.COMPASS:Items.PAPER,"elevator.floor_"+(floor==source?"current":"destination"),floors.size()-index-1,floor.name()));
            }
            for(int i=27;i<36;i++)getSlot(i).set(MenuItems.blank(Items.GRAY_STAINED_GLASS_PANE));
            if(page>0)getSlot(29).set(MenuItems.label(owner,Items.ARROW,"android.library_previous",page+1,1+floors.size()/27));
            if((page+1)*27<floors.size())getSlot(33).set(MenuItems.label(owner,Items.ARROW,"android.library_next",page+1,1+floors.size()/27));
        }
        broadcastChanges();
    }
    @Override public boolean stillValid(Player player){return player==owner&&source.accessible(owner)&&owner.distanceToSqr(source.getBlockPos().getCenter())<=64&&(!editor||source.ownedBy(owner));}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(player.containerMenu!=this||!stillValid(player)||type!=ClickType.PICKUP||button!=0)return;
        if(editor) {
            if(slot!=4)return;
            Guide.cancelSearch(owner);GpsWaypoints.cancelPrompt(owner);ProjectorEntity.cancelEdit(owner);AndroidScripts.cancelPrompt(owner);
            PROMPTS.put(owner,source);owner.closeContainer();Messages.chat(owner,text("enter_name"));return;
        }
        if(slot==29&&page>0){page--;render();return;}
        if(slot==33&&(page+1)*27<floors.size()){page++;render();return;}
        if(slot<0||slot>=27||page*27+slot>=floors.size())return;
        var floor=floors.get(page*27+slot);if(floor==source||!floor.accessible(owner))return;
        var pos=floor.getBlockPos();var level=owner.serverLevel();if(!level.getWorldBorder().isWithinBounds(pos))return;
        owner.closeContainer();
        var destination=GlobalPos.of(level.dimension(),pos);CONTACTS.put(owner,destination);
        boolean success=owner.teleportTo(level,pos.getX()+.5,pos.getY()+.4,pos.getZ()+.5,Set.of(),Mth.wrapDegrees(owner.getYRot()+180),owner.getXRot());
        if(success&&owner.level()==level&&owner.distanceToSqr(pos.getX()+.5,pos.getY()+.4,pos.getZ()+.5)<.000001) {
            owner.connection.resetPosition();owner.fallDistance=0;
            owner.connection.send(new ClientboundSetTitlesAnimationPacket(20,60,20));owner.connection.send(new ClientboundSetTitleTextPacket(floor.name()));
        } else CONTACTS.replace(owner,destination,GlobalPos.of(level.dimension(),source.getBlockPos()));
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @SubscribeEvent public static void chat(ServerChatEvent event){
        var player=event.getPlayer();var floor=PROMPTS.get(player);if(floor==null)return;
        event.setCanceled(true);var message=event.getRawText();
        player.server.execute(()->{if(!PROMPTS.remove(player,floor)||!floor.accessible(player)||!floor.ownedBy(player)||player.distanceToSqr(floor.getBlockPos().getCenter())>64)return;
            if(message.isEmpty()||message.length()>256){Messages.chat(player,text("invalid_name"));return;}floor.rename(message);Messages.chat(player,text("named",floor.name()));open(player,floor,true);
        });
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer player){var contact=CONTACTS.get(player);if(contact!=null&&(!player.isAlive()||!contact.dimension().equals(player.level().dimension())||!contact.pos().equals(player.blockPosition())))CONTACTS.remove(player);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){cancelPrompt(player);CONTACTS.remove(player);}}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){PROMPTS.keySet().removeIf(player->player.server==event.getServer());CONTACTS.keySet().removeIf(player->player.server==event.getServer());}
}
