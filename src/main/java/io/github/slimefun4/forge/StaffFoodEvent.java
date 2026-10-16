package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

@Cancelable
public final class StaffFoodEvent extends PlayerEvent {
    private int food;
    public StaffFoodEvent(ServerPlayer player,int food){super(player);setFood(food);}
    public int getFood(){return food;}
    public void setFood(int value){if(value<0||value>20)throw new IllegalArgumentException("Food outside 0..20");food=value;}
}
