package io.github.slimefun4.forge.api;

import java.util.*;
import net.minecraftforge.eventbus.api.Event;

/** Background IO notification. No live world/player access; replacement affects this returned immutable view only. */
public final class AsyncProfileLoadEvent extends Event {
    private final UUID owner;private PlayerApi.Profile profile;
    public AsyncProfileLoadEvent(UUID owner,PlayerApi.Profile profile){this.owner=Objects.requireNonNull(owner);setProfile(profile);}
    public UUID getOwner(){return owner;}
    public PlayerApi.Profile getProfile(){return profile;}
    public void setProfile(PlayerApi.Profile profile){if(!owner.equals(Objects.requireNonNull(profile).owner()))throw new IllegalArgumentException("Profile replacement changed UUID");this.profile=profile;}
}
