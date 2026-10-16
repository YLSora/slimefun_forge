package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Dynamic read-only pages; vanilla slot synchronization carries addon icons and localized text. */
public final class GeoScanMenu extends ChestMenu {
    private static final int[] CONTENT={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private final Player owner;private final Level level;private final net.minecraft.world.InteractionHand portable;
    public final BlockPos scanner;private final Map<GeoResources,Integer> supplies;private int page;
    public GeoScanMenu(int id,Inventory inventory,FriendlyByteBuf buffer){this(id,inventory,buffer.readBlockPos(),Map.of(),buffer.readNullable(b->b.readEnum(net.minecraft.world.InteractionHand.class)));}
    public GeoScanMenu(int id,Inventory inventory,BlockPos scanner,Map<GeoResources,Integer> values,net.minecraft.world.InteractionHand portable){
        super(SlimefunForge.GEO_SCAN_MENU.get(),id,inventory,new net.minecraft.world.SimpleContainer(54),6);
        owner=inventory.player;level=owner.level();this.scanner=scanner.immutable();supplies=Map.copyOf(values);this.portable=portable;
        for(int i=0;i<54;i++){var old=slots.get(i);var slot=new Slot(old.container,i,old.x,old.y){public boolean mayPlace(ItemStack item){return false;}public boolean mayPickup(Player player){return false;}};slot.index=i;slots.set(i,slot);}
        if(owner instanceof ServerPlayer server)render(server);
    }
    private List<GeoResources> resources(){return supplies.keySet().stream().sorted(Comparator.comparing((GeoResources r)->resourceName(r).getString().toLowerCase(Locale.ROOT)).thenComparing(r->r.id)).toList();}
    private void render(ServerPlayer player){
        for(int i=0;i<54;i++)getSlot(i).set(MenuItems.blank(Items.GRAY_STAINED_GLASS_PANE));for(int slot:CONTENT)getSlot(slot).set(ItemStack.EMPTY);
        getSlot(4).set(MenuItems.label(player,MenuItems.head(MenuItems.Head.MINECRAFT_CHUNK),"geo.chunk",chunkLabel(),worldLabel(),dimensionName(),scanner.getX()>>4,scanner.getZ()>>4));
        var resources=resources();int pages=Math.max(1,(resources.size()+CONTENT.length-1)/CONTENT.length);page=Math.max(0,Math.min(page,pages-1));
        for(int i=0;i<CONTENT.length&&page*CONTENT.length+i<resources.size();i++){
            var resource=resources.get(page*CONTENT.length+i);var output=resource.output();
            var stack=MenuItems.label(player,output.getItem(),"geo.resource",resourceName(resource),unitsLabel(resource));stack.setCount(Math.max(1,Math.min(supply(resource),64)));getSlot(CONTENT[i]).set(stack);
        }
        getSlot(47).set(MenuItems.label(player,Items.ARROW,"android.library_previous",page+1,pages));getSlot(51).set(MenuItems.label(player,Items.ARROW,"android.library_next",page+1,pages));
    }
    public int supply(GeoResources resource){return supplies.getOrDefault(resource,-1);}
    public String dimensionName(){return level.dimension().location().toString();}
    public Component resourceName(GeoResources resource){return owner instanceof ServerPlayer server?GuideContent.localize(server,resource.name()):resource.name();}
    public Component unitsLabel(GeoResources resource){return Component.literal(supply(resource)+" ").append(Component.translatable("geo.slimefun."+(supply(resource)==1?"unit":"units")));}
    public Component chunkLabel(){return Component.translatable("geo.slimefun.chunk");}
    public Component worldLabel(){return Component.translatable("geo.slimefun.world");}
    public static void write(FriendlyByteBuf buffer,ServerPlayer player,BlockPos scanner,Map<GeoResources,Integer> values,net.minecraft.world.InteractionHand portable){buffer.writeBlockPos(scanner);buffer.writeNullable(portable,(b,hand)->b.writeEnum(hand));}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(player.containerMenu!=this||!stillValid(player))return;
        if(slot>=0&&slot<54){if(type==ClickType.PICKUP&&(button==0||button==1)&&player instanceof ServerPlayer server&&(slot==47||slot==51)){page+=slot==47?-1:1;render(server);broadcastChanges();}return;}super.clicked(slot,button,type,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player){
        return player==owner&&player.isAlive()&&player.level()==level&&level.hasChunkAt(scanner)&&player.distanceToSqr(scanner.getCenter())<=64
            &&(portable==null?level.getBlockState(scanner).is(SlimefunForge.GEO_SCANNER.get()):player.getItemInHand(portable).is(SlimefunForge.item("portable_geo_scanner")))
            &&(!(player instanceof ServerPlayer server)||ResearchData.canUse(server,portable==null?new ItemStack(SlimefunForge.GEO_SCANNER.get()):player.getItemInHand(portable))&&(portable==null?GpsAccess.mayOpen(server,scanner):level.mayInteract(server,scanner)));
    }
}
