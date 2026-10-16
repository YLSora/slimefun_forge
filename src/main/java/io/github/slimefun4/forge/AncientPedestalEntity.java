package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** The native mounted item is the sole inventory, including after debug removes this block identity. */
public final class AncientPedestalEntity extends BlockEntity {
    private boolean interacting;
    private static final String MARKER="slimefun_altar_offering",STAND_NAME="slimefun:altar:";
    public AncientPedestalEntity(BlockPos pos,BlockState state){super(SlimefunForge.ANCIENT_PEDESTAL_ENTITY.get(),pos,state);}
    public static boolean isOffering(ItemEntity item){return item.getPersistentData().getBoolean(MARKER);}
    private static AABB area(BlockPos pos){return new AABB(pos.getX(),pos.getY()+.7,pos.getZ(),pos.getX()+1,pos.getY()+1.7,pos.getZ()+1);}
    public static ItemEntity find(Level level,BlockPos pos){
        return level.getEntitiesOfClass(ItemEntity.class,area(pos),e->e.isAlive()&&(isOffering(e)||level.isClientSide&&e.getVehicle() instanceof ArmorStand stand&&stand.hasCustomName()&&stand.getCustomName().getString().startsWith(STAND_NAME))).stream().findFirst().orElse(null);
    }
    private static ArmorStand stand(Level level,BlockPos pos){return level.getEntitiesOfClass(ArmorStand.class,area(pos),e->e.isAlive()&&e.hasCustomName()&&e.getCustomName().getString().startsWith(STAND_NAME)).stream().findFirst().orElse(null);}
    public ItemStack offering(){var item=level==null?null:find(level,worldPosition);return item==null?ItemStack.EMPTY:item.getItem().copy();}
    public boolean locked(){return level instanceof ServerLevel server&&AncientRituals.get(server).locked(worldPosition);}
    public void interact(ServerPlayer player){
        if(interacting)return;interacting=true;try{interactOnce(player);}finally{interacting=false;}
    }
    private void interactOnce(ServerPlayer player){
        if(!(level instanceof ServerLevel server)||locked())return;var item=find(server,worldPosition);
        if(item==null){
            if(player.getMainHandItem().isEmpty())return;if(!level.getBlockState(worldPosition.above()).isAir()){AncientAltarEntity.message(player,"obstructed");return;}
            var held=player.getMainHandItem();var expected=held.copy();
            item=io.github.slimefun4.forge.api.ItemSpawnApi.prepare(server,player,io.github.slimefun4.forge.api.ItemSpawnApi.Reason.ANCIENT_PEDESTAL_PLACE_ITEM,new Vec3(worldPosition.getX()+.5,worldPosition.getY()+1.2,worldPosition.getZ()+.5),held.copyWithCount(1));
            if(item==null||item.getItem().getCount()!=1||!area(worldPosition).contains(item.position())||!validSource(server,player,held,expected))return;
            item.getPersistentData().putBoolean(MARKER,true);item.setInvulnerable(true);item.setPickUpDelay(Short.MAX_VALUE);item.setUnlimitedLifetime();item.setCustomName(item.getItem().getHoverName());item.setCustomNameVisible(true);item.setDeltaMovement(0,.1,0);
            if(!server.addFreshEntity(item))return;if(item.isRemoved()||!validSource(server,player,held,expected)){item.discard();return;}var stand=stand(server,worldPosition);
            if(stand==null){stand=new ArmorStand(server,worldPosition.getX()+.5,worldPosition.getY()+1.2,worldPosition.getZ()+.5);stand.setInvisible(true);stand.setSilent(true);var settings=stand.saveWithoutId(new net.minecraft.nbt.CompoundTag());settings.putBoolean("Marker",true);stand.load(settings);stand.setNoGravity(true);stand.setNoBasePlate(true);stand.setCustomName(net.minecraft.network.chat.Component.literal(STAND_NAME+item.getUUID()));if(!server.addFreshEntity(stand)){item.discard();return;}}
            if(!validSource(server,player,held,expected)||!item.startRiding(stand,true)){item.discard();return;}if(!player.isCreative())held.shrink(1);AncientAltarEntity.sound(server,worldPosition,SoundEvents.ITEM_PICKUP,.5F,.5F);
        }else{var returned=item.getItem().copy();item.discard();player.getInventory().add(returned);if(!returned.isEmpty())drop(server,worldPosition.above(),returned);AncientAltarEntity.sound(server,worldPosition,SoundEvents.ITEM_PICKUP,1,1);}
        player.inventoryMenu.broadcastChanges();
    }
    private boolean validSource(ServerLevel server,ServerPlayer player,ItemStack held,ItemStack expected){return player.isAlive()&&player.serverLevel()==server&&player.server.getPlayerList().getPlayer(player.getUUID())==player&&server.hasChunkAt(worldPosition)&&server.getBlockEntity(worldPosition)==this&&!locked()&&player.getMainHandItem()==held&&ItemStack.matches(held,expected)&&server.mayInteract(player,worldPosition)&&player.distanceToSqr(Vec3.atCenterOf(worldPosition))<=64&&ResearchData.canUse(player,held);}
    void broken(){if(!(level instanceof ServerLevel server))return;var item=find(server,worldPosition);if(item!=null){var stack=item.getItem().copy();item.discard();drop(server,worldPosition,stack);}var stand=stand(server,worldPosition);if(stand!=null)stand.discard();}
    static void drop(ServerLevel level,BlockPos pos,ItemStack stack){io.github.slimefun4.forge.api.ItemSpawnApi.reserve(level,null,io.github.slimefun4.forge.api.ItemSpawnApi.Reason.MISC,new Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.5),stack);}
}
