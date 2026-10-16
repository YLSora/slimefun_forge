package io.github.slimefun4.forge;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ElevatorEntity extends BlockEntity {
    private UUID owner;
    private String name;
    public ElevatorEntity(BlockPos pos,BlockState state){super(SlimefunForge.ELEVATOR_ENTITY.get(),pos,state);}
    public void setOwner(UUID owner){this.owner=owner;setChanged();}
    public boolean ownedBy(ServerPlayer player){return player.getUUID().equals(owner);}
    public Component name(){return name==null?Component.translatable("menu.slimefun.elevator.default"):Component.literal(name.replaceAll("&([0-9a-fk-orA-FK-OR])","§$1"));}
    public void rename(String name){if(name.isEmpty()||name.length()>256)throw new IllegalArgumentException("Invalid floor name");this.name=name.replace('§','&');setChanged();}
    public boolean accessible(ServerPlayer player){return !isRemoved()&&player.isAlive()&&!player.isSpectator()&&player.level()==level&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&level.mayInteract(player,worldPosition)&&ItemAccess.canUse(player,"elevator_plate");}
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);if(owner!=null)tag.putUUID("owner",owner);if(name!=null)tag.putString("floor",name);}
    @Override public void load(CompoundTag tag){super.load(tag);owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;name=null;if(tag.contains("floor"))rename(tag.getString("floor"));}
}
