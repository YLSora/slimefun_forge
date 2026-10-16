package io.github.slimefun4.forge;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Matrix and activation plate ownership belong to their own placed blocks. */
public final class GpsTeleporterEntity extends BlockEntity {
    private UUID owner;
    public GpsTeleporterEntity(BlockPos pos,BlockState state){super(SlimefunForge.GPS_TELEPORTER_ENTITY.get(),pos,state);}
    public UUID owner(){return owner;}
    public void setOwner(UUID owner){this.owner=owner;setChanged();}
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);if(owner!=null)tag.putUUID("owner",owner);}
    @Override public void load(CompoundTag tag){super.load(tag);owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;}
}
