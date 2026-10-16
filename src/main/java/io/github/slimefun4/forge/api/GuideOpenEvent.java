package io.github.slimefun4.forge.api;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/** Server game bus, before every guide page is opened. Cancellation keeps the existing window.
 * Replacements use native menu registration; subscribers own their menu's transaction validation.
 */
@Cancelable
public final class GuideOpenEvent extends PlayerEvent {
    private final GuideApi.Mode mode;
    private final ItemStack book;
    private MenuProvider provider;
    public GuideOpenEvent(ServerPlayer player,GuideApi.Mode mode,ItemStack book,MenuProvider provider){super(player);this.mode=mode;this.book=book.copy();this.provider=Objects.requireNonNull(provider);}
    public GuideApi.Mode getMode(){return mode;}
    public ItemStack getBook(){return book.copy();}
    public MenuProvider getProvider(){return provider;}
    public void setProvider(MenuProvider provider){this.provider=Objects.requireNonNull(provider);}
}
