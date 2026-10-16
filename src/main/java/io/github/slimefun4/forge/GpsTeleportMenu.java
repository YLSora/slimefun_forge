package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** One transient menu or journey per player session; no persisted or idle teleport queue. */
public final class GpsTeleportMenu extends ChestMenu {
    private static final Map<ServerPlayer,GpsTeleportMenu> ACTIVE=new HashMap<>();
    private static final Set<ServerPlayer> REQUESTING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final int[] CONTENT={19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private final ServerPlayer player;
    private final ServerLevel level;
    private final GpsTeleporterEntity matrix,plate;
    private final UUID owner,plateOwner;
    private final Vec3 source;
    private final int complexity;
    private final boolean command;
    private boolean api;
    private io.github.slimefun4.forge.api.GpsApi.Payment payment;
    private final Map<Integer,GpsWaypoints.Waypoint> displayed=new HashMap<>();
    private GpsWaypoints.Waypoint destination;
    private int progress,step;
    private long due;
    public static boolean start(ServerPlayer player,UUID owner,String id,io.github.slimefun4.forge.api.GpsApi.Payment payment){
        io.github.slimefun4.forge.api.NetworkApi.check(player.serverLevel());
        if(ACTIVE.containsKey(player)||!io.github.slimefun4.forge.api.GpsApi.mayAccess(player,owner)||!REQUESTING.add(player))return false;
        try{
        if(!payment.canPay()||ACTIVE.containsKey(player)||!io.github.slimefun4.forge.api.GpsApi.mayAccess(player,owner))return false;
        var point=GpsWaypoints.get(player.server).points(owner).stream().filter(p->p.id().equals(id)).findFirst().orElse(null);if(point==null)return false;
        var journey=new GpsTeleportMenu(-1,player.getInventory(),null,null,owner,false);journey.api=true;journey.payment=payment;
        if(!journey.valid()||!journey.current(point)){ACTIVE.remove(player,journey);return false;}
        journey.destination=point;journey.step=Math.max(1,100/time(journey.complexity,journey.level.dimension().equals(point.dimension()),journey.source.distanceToSqr(point.position())));journey.advance();return ACTIVE.get(player)==journey;
        }finally{REQUESTING.remove(player);}
    }
    public static boolean cancel(ServerPlayer player){var journey=ACTIVE.get(player);if(journey==null)return false;journey.cancel();return true;}

    public static void openPortable(ServerPlayer p){
        if(!ACTIVE.containsKey(p))p.openMenu(new SimpleMenuProvider((id,inv,viewer)->new GpsTeleportMenu(id,inv,null,null,p.getUUID(),false),GuideContent.localize(p,Component.translatable("menu.slimefun.teleport.title").withStyle(net.minecraft.ChatFormatting.DARK_AQUA))));
    }
    static boolean openCommand(ServerPlayer p,UUID owner){
        if(!AdminCommands.canTeleport(p)||ACTIVE.containsKey(p))return false;
        p.openMenu(new SimpleMenuProvider((id,inv,viewer)->new GpsTeleportMenu(id,inv,null,null,owner,true),GuideContent.localize(p,Component.translatable("menu.slimefun.teleport.title").withStyle(net.minecraft.ChatFormatting.DARK_AQUA))));return true;
    }
    public static void open(ServerPlayer p,BlockPos pos){
        if(ACTIVE.containsKey(p)||!(p.level().getBlockEntity(pos) instanceof GpsTeleporterEntity plate)||!(plate.getBlockState().getBlock() instanceof GpsActivationPlate block)||!block.accessible(p,plate))return;
        if(!(p.level().getBlockEntity(pos.below()) instanceof GpsTeleporterEntity matrix)||!(matrix.getBlockState().getBlock() instanceof GpsTeleporterBlock)||matrix.owner()==null||!structure(p.serverLevel(),matrix.getBlockPos()))return;
        p.openMenu(new SimpleMenuProvider((id,inv,viewer)->new GpsTeleportMenu(id,inv,matrix,plate,matrix.owner(),false),GuideContent.localize(p,Component.translatable("menu.slimefun.teleport.title").withStyle(net.minecraft.ChatFormatting.DARK_AQUA))));
    }
    private GpsTeleportMenu(int id,Inventory inv,GpsTeleporterEntity matrix,GpsTeleporterEntity plate,UUID owner,boolean command){
        super(MenuType.GENERIC_9x6,id,inv,new SimpleContainer(54),6);player=(ServerPlayer)inv.player;level=player.serverLevel();this.matrix=matrix;this.plate=plate;this.owner=owner;this.command=command;plateOwner=plate==null?null:plate.owner();source=matrix==null?Vec3.atBottomCenterOf(player.blockPosition()).add(0,1,0):Vec3.atBottomCenterOf(matrix.getBlockPos()).add(0,2,0);complexity=command?999999999:GpsSignals.complexity(player.server,owner);
        for(int i=0;i<54;i++){var old=slots.get(i);var slot=new Slot(old.container,i,old.x,old.y){public boolean mayPlace(ItemStack stack){return false;}public boolean mayPickup(Player p){return false;}};slot.index=i;slots.set(i,slot);slot.set(new ItemStack(Items.GRAY_STAINED_GLASS_PANE).setHoverName(Component.literal(" ")));}
        var globe=new ItemStack(SlimefunForge.item("gps_control_panel")).setHoverName(text("destinations").copy().withStyle(net.minecraft.ChatFormatting.YELLOW));globe.getOrCreateTag().putInt("CustomModelData",1);getSlot(4).set(globe);for(int slot:CONTENT)getSlot(slot).set(ItemStack.EMPTY);
        int index=0;for(var point:GpsWaypoints.get(player.server).points(owner)){
            if(index==CONTENT.length)break;var p=point.position();var icon=MenuItems.label(player,SlimefunForge.item("gps_control_panel"),"teleport.point",point.name().replace("player:death ","").replaceAll("&([0-9a-fk-orA-FK-OR])","§$1"),point.dimension().location().toString(),p.x,p.y,p.z,time(complexity,level.dimension().equals(point.dimension()),source.distanceToSqr(p))*.5);icon.getOrCreateTag().putInt("CustomModelData",point.name().startsWith("player:death ")?4:point.dimension().equals(Level.NETHER)?2:point.dimension().equals(Level.END)?3:1);int slot=CONTENT[index++];getSlot(slot).set(icon);displayed.put(slot,point);
        }
        ACTIVE.put(player,this);player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(),SoundSource.PLAYERS,1,1);
    }
    private Component text(String key,Object... args){return GuideContent.localize(player,Component.translatable("menu.slimefun.teleport."+key,args));}
    private static boolean structure(ServerLevel l,BlockPos pos){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0){var p=pos.offset(x,0,z);if(!l.hasChunkAt(p)||!(l.getBlockState(p).getBlock() instanceof GpsPylonBlock))return false;}return true;}
    private boolean valid(){return ACTIVE.get(player)==this&&player.isAlive()&&!player.isRemoved()&&player.level()==level&&(!api||io.github.slimefun4.forge.api.GpsApi.mayAccess(player,owner))&&(!command||AdminCommands.canTeleport(player))&&player.distanceToSqr(source)<2&&(matrix==null||!matrix.isRemoved()&&!plate.isRemoved()&&level.hasChunkAt(matrix.getBlockPos())&&level.getBlockEntity(matrix.getBlockPos())==matrix&&level.getBlockEntity(plate.getBlockPos())==plate&&owner.equals(matrix.owner())&&Objects.equals(plateOwner,plate.owner())&&plate.getBlockState().getBlock() instanceof GpsActivationPlate block&&block.accessible(player,plate)&&structure(level,matrix.getBlockPos()));}
    @Override public boolean stillValid(Player p){return p==player&&ACTIVE.get(player)==this&&destination==null&&valid();}
    @Override public void clicked(int slot,int button,ClickType type,Player p){
        if(p.containerMenu!=this||!stillValid(p))return;
        if(slot>=0&&slot<54){if(type!=ClickType.PICKUP||(button!=0&&button!=1)||!displayed.containsKey(slot))return;var point=displayed.get(slot);player.closeContainer();if(!current(point)){title("cancelled",0);return;}destination=point;step=Math.max(1,100/time(complexity,level.dimension().equals(point.dimension()),source.distanceToSqr(point.position())));ACTIVE.put(player,this);advance();return;}
        super.clicked(slot,button,type,p);
    }
    @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
    @Override public void removed(Player p){super.removed(p);ACTIVE.remove(player,this);}
    // RC-37 intentionally uses Java int arithmetic, including overflow at high complexity.
    private static int time(int complexity,boolean sameDimension,double squared){if(complexity<100)return 100;int speed=50000+complexity*complexity;int distance=sameDimension?Math.min((int)squared,100000000):150000000;return Math.max(1,Math.min(4*distance/speed,40));}
    private boolean current(GpsWaypoints.Waypoint point){var target=player.server.getLevel(point.dimension());var pos=BlockPos.containing(point.position());return target!=null&&target.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null&&!target.isOutsideBuildHeight(pos)&&target.getWorldBorder().isWithinBounds(pos)&&GpsWaypoints.get(player.server).points(owner).contains(point);}
    private void title(String key,int value){player.connection.send(new ClientboundSetTitlesAnimationPacket(0,60,0));player.connection.send(new ClientboundSetTitleTextPacket(text(key)));player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(value+"%")));}
    private void cancel(){ACTIVE.remove(player,this);title("cancelled",progress);}
    private void advance(){
        if(!valid()||!current(destination)){cancel();return;}
        if(progress<=99){title("progress",progress);level.sendParticles(ParticleTypes.PORTAL,source.x,source.y,source.z,progress*2,.2,.8,.2,0);player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,.5F,1);progress+=step;due=player.server.getTickCount()+10L;return;}
        var target=player.server.getLevel(destination.dimension());var p=destination.position();var pos=BlockPos.containing(p);
        if(target==null||target.isOutsideBuildHeight(pos)||!target.getWorldBorder().isWithinBounds(pos)||!target.mayInteract(player,pos)||target!=level&&!ForgeHooks.onTravelToDimension(player,target.dimension())){cancel();return;}
        if(payment!=null&&!payment.canPay()||!valid()||!current(destination)){cancel();return;}
        boolean success;
        try{success=player.teleportTo(target,p.x,p.y,p.z,Set.of(),destination.yaw(),0)&&player.level()==target&&player.distanceToSqr(p)<.000001;}
        finally{ACTIVE.remove(player,this);}
        // A same-tick acknowledgement must compare movement against the destination.
        if(success){player.connection.resetPosition();player.fallDistance=0;if(payment!=null)payment.pay();title("success",100);target.sendParticles(ParticleTypes.PORTAL,p.x,p.y+1,p.z,200,.2,.8,.2,0);player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,1,1);}else title("cancelled",progress);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END||ACTIVE.isEmpty())return;for(var journey:List.copyOf(ACTIVE.values()))if(journey.player.server==event.getServer()&&journey.destination!=null&&event.getServer().getTickCount()>=journey.due)journey.advance();}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){ACTIVE.remove(event.getEntity());}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){ACTIVE.values().removeIf(journey->journey.level==event.getLevel()||journey.destination!=null&&event.getLevel() instanceof ServerLevel level&&journey.destination.dimension().equals(level.dimension()));}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){ACTIVE.keySet().removeIf(p->p.server==event.getServer());}
}
