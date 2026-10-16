package io.github.slimefun4.forge;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** RC-37 radioactive resources have no placement or right-click action. */
public final class RadioactiveItem extends Item implements io.github.slimefun4.forge.api.ItemApi.Radioactive {
    final int radiation;
    public RadioactiveItem(int radiation) {
        super(new Properties());
        if(radiation<1 || radiation>10) throw new IllegalArgumentException("Invalid radioactivity");
        this.radiation=radiation;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) { return InteractionResultHolder.fail(player.getItemInHand(hand)); }
    @Override public InteractionResult useOn(UseOnContext context) { return InteractionResult.FAIL; }
    @Override public int radiation(ItemStack stack){return radiation;}
}
