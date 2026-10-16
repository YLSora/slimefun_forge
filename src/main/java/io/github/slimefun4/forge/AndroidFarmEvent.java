package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/** Fired for every loaded in-bounds farming target, including blocks with no default drop. */
@Cancelable
public final class AndroidFarmEvent extends BlockEvent {
    private final io.github.slimefun4.forge.api.AndroidApi.Robot android;
    private final boolean advanced;
    private ItemStack drop;
    public AndroidFarmEvent(io.github.slimefun4.forge.api.AndroidApi.Robot android,BlockPos pos,BlockState state,boolean advanced,ItemStack drop){super(android.getLevel(),pos,state);this.android=android;this.advanced=advanced;setDrop(drop);}
    public io.github.slimefun4.forge.api.AndroidApi.Robot getAndroid(){return android;}
    public boolean isAdvanced(){return advanced;}
    /** Null means no harvest; replacements are copied and must be a legal single stack. */
    public ItemStack getDrop(){return drop==null?null:drop.copy();}
    public void setDrop(ItemStack drop){if(drop!=null&&!drop.isEmpty()&&(drop.getCount()<1||drop.getCount()>drop.getMaxStackSize()))throw new IllegalArgumentException("Invalid android crop drop");this.drop=drop==null?null:drop.copy();}
}
