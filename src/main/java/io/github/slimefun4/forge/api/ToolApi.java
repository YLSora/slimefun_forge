package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.NativeTools;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/** Native tool opt-ins consumed by the same mining transaction as core tools. */
public final class ToolApi {
    public enum Mining { SMELT, LUMBER, VEIN, EXPLOSIVE_PICKAXE, EXPLOSIVE_SHOVEL }
    public interface MiningTool { Mining mining(ItemStack stack); }
    public interface ClimbingPick {
        /** Pure surface query, zero for an unsupported surface; final speed is capped at 4. */
        double launchPower(ItemStack stack,BlockState surface);
    }
    /** Call in onItemUseFirst; dual native picks, permission, event, snapshots and durability are shared. */
    public static InteractionResult launch(ItemStack stack,UseOnContext context){return NativeTools.launch(stack,context);}
    private ToolApi(){}
}
