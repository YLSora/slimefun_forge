package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Read-only icon slots; requests are resolved against the viewer's current waypoint IDs. */
public final class GpsPanelMenu extends ChestMenu {
    private static final int[] CONTENT={19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private final ServerPlayer owner;
    private final Level level;
    private final BlockPos panel;
    private final Map<Integer,String> displayed=new HashMap<>();
    private boolean waypoints;
    private int page,pages=1;
    public GpsPanelMenu(int id,Inventory inventory,BlockPos panel){
        super(MenuType.GENERIC_9x6,id,inventory,new SimpleContainer(54),6);owner=(ServerPlayer)inventory.player;level=owner.level();this.panel=panel.immutable();
        for(int i=0;i<54;i++){var old=slots.get(i);var slot=new Slot(old.container,i,old.x,old.y){public boolean mayPlace(ItemStack item){return false;}public boolean mayPickup(Player player){return false;}};slot.index=i;slots.set(i,slot);}
        refresh();
    }
    private ItemStack icon(Item item,Component name,Component... lore){var stack=new ItemStack(item).setHoverName(GuideContent.localize(owner,name));var lines=new ListTag();for(var line:lore)lines.add(StringTag.valueOf(Component.Serializer.toJson(GuideContent.localize(owner,line))));stack.getOrCreateTagElement("display").put("Lore",lines);return stack;}
    private Component text(String key,Object... values){return GuideContent.localize(owner,Component.translatable("menu.slimefun.gps."+key,values));}
    private void refresh(){
        displayed.clear();for(int i=0;i<54;i++)getSlot(i).set(icon(Items.GRAY_STAINED_GLASS_PANE,Component.literal(" ")));for(int slot:CONTENT)getSlot(slot).set(ItemStack.EMPTY);
        var open=Component.literal("⇨ ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.translatable("guide.slimefun.open_category").withStyle(net.minecraft.ChatFormatting.GRAY));
        var tabs=new Component[]{Component.empty(),open};
        getSlot(2).set(icon(SlimefunForge.item("gps_transmitter"),text("transmitters").copy().withStyle(net.minecraft.ChatFormatting.GRAY),waypoints?tabs:new Component[0]));int complexity=GpsSignals.complexity(owner.server,owner.getUUID());
        var status=text(complexity>0?"online":"offline").copy().withStyle(complexity>0?net.minecraft.ChatFormatting.DARK_GREEN:net.minecraft.ChatFormatting.DARK_RED,net.minecraft.ChatFormatting.BOLD);
        getSlot(4).set(MenuItems.label(owner,SlimefunForge.item("gps_control_panel"),"gps.network",status,complexity));
        var globe=icon(SlimefunForge.item("gps_control_panel"),text("waypoints").copy().withStyle(net.minecraft.ChatFormatting.GRAY),waypoints?new Component[0]:tabs);globe.getOrCreateTag().putInt("CustomModelData",1);getSlot(6).set(globe);
        int index=0;
        if(waypoints)for(var point:GpsWaypoints.get(owner.server).points(owner.getUUID())){
            if(index>=CONTENT.length)break;int slot=CONTENT[index++];var p=point.position();var stack=MenuItems.label(owner,SlimefunForge.item("gps_control_panel"),"gps.point",point.name().replace("player:death ","").replaceAll("&([0-9a-fk-orA-FK-OR])","§$1"),point.dimension().location().toString(),p.x,p.y,p.z);
            stack.getOrCreateTag().putInt("CustomModelData",point.name().startsWith("player:death ")?4:point.dimension().equals(Level.NETHER)?2:point.dimension().equals(Level.END)?3:1);getSlot(slot).set(stack);displayed.put(slot,point.id());
        }else {
            var signals=GpsSignals.signals(owner.server,owner.getUUID());pages=Math.max(1,(signals.size()+CONTENT.length-1)/CONTENT.length);page=Math.max(0,Math.min(page,pages-1));
            for(int i=page*CONTENT.length;i<signals.size()&&index<CONTENT.length;i++){var signal=signals.get(i);var pos=signal.position();getSlot(CONTENT[index++]).set(MenuItems.label(owner,signal.icon(),"gps.transmitter",pos.dimension().location().toString(),(double)pos.pos().getX(),(double)pos.pos().getY(),(double)pos.pos().getZ(),signal.strength(),String.format(Locale.ROOT,"%.2f",1000D/pos.pos().getY())));}
            if(page>0)getSlot(47).set(MenuItems.label(owner,Items.ARROW,"android.library_previous",page+1,pages));if(page+1<pages)getSlot(51).set(MenuItems.label(owner,Items.ARROW,"android.library_next",page+1,pages));
        }
        broadcastChanges();
    }
    @Override public boolean stillValid(Player player){return player==owner&&player.isAlive()&&player.level()==level&&level.hasChunkAt(panel)&&level.getBlockState(panel).is(SlimefunForge.GPS_CONTROL_PANEL.get())&&player.distanceToSqr(panel.getCenter())<=64&&ResearchData.canUse(owner,new ItemStack(SlimefunForge.GPS_CONTROL_PANEL.get()))&&GpsAccess.mayOpen(owner,panel);}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(player.containerMenu!=this||!stillValid(player))return;
        if(slot>=0&&slot<54){
            if(type!=ClickType.PICKUP||(button!=0&&button!=1))return;
            if(slot==2){waypoints=false;page=0;refresh();}else if(slot==6){waypoints=true;refresh();}else if(!waypoints&&(slot==47&&page>0||slot==51&&page+1<pages)){page+=slot==47?-1:1;refresh();}else if(waypoints&&displayed.containsKey(slot)){GpsWaypoints.get(owner.server).remove(owner.getUUID(),displayed.get(slot));owner.playNotifySound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),net.minecraft.sounds.SoundSource.PLAYERS,1,1);refresh();}
            return;
        }
        super.clicked(slot,button,type,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
