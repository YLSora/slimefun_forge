package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.ResearchApi.State;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Internal adapter for the supported research API; never exposes Catalog or mutable SavedData. */
public final class AddonResearch {
    private static void check(MinecraftServer server){
        Objects.requireNonNull(server,"Research server");
        if(!server.isSameThread())throw new IllegalStateException("Research API requires the server thread");
        AddonContent.content();
    }
    private static Catalog.Research require(ResourceLocation id){
        Objects.requireNonNull(id,"Research ID");
        return ResearchData.all().stream().filter(r->AddonContent.commandId(r).equals(id.toString())).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Unknown Slimefun research "+id));
    }
    private static State state(MinecraftServer server,UUID player,Catalog.Research research){
        return new State(new ResourceLocation(AddonContent.commandId(research)),AddonContent.researchKey(research),ResearchSettings.cost(research),
            ResearchSettings.enabled(research),ResearchData.get(server).completed(player,research),
            research.items().stream().map(id->new ResourceLocation(id.contains(":")?id:"slimefun:"+id)).toList());
    }
    public static State get(MinecraftServer server,UUID player,ResourceLocation id){check(server);Objects.requireNonNull(player,"Research player UUID");return state(server,player,require(id));}
    public static List<State> all(MinecraftServer server,UUID player){check(server);Objects.requireNonNull(player,"Research player UUID");return ResearchData.all().stream().map(r->state(server,player,r)).sorted(Comparator.comparing(s->s.id().toString())).toList();}
    public static boolean learn(ServerPlayer player,ItemStack source){
        check(player.server);Objects.requireNonNull(source,"Research source item");String id=ItemAccess.id(source);
        if(source.isEmpty()||id==null)throw new IllegalArgumentException("Research source is not a registered Slimefun guide item");
        var research=ResearchData.research(source);
        return research!=null&&ItemAccess.enabled(player.level(),id)&&ItemAccess.permitted(player,id)&&GuideResearch.unlock(player,research,source);
    }
    public static boolean grant(ServerPlayer player,ResourceLocation id){
        check(player.server);var research=require(id);GuideResearch.cancel(player,research);boolean changed=ResearchData.get(player).grant(player,research);refresh(player);return changed;
    }
    public static boolean revoke(ServerPlayer player,ResourceLocation id){
        check(player.server);var research=require(id);boolean cancelled=GuideResearch.cancel(player,research);
        boolean changed=ResearchData.get(player).revoke(player,research)||cancelled;refresh(player);return changed;
    }
    public static int grantAll(ServerPlayer player){check(player.server);GuideResearch.cancel(player);int count=0;for(var research:ResearchData.all())if(ResearchData.get(player).grant(player,research))count++;refresh(player);return count;}
    public static int reset(ServerPlayer player){check(player.server);GuideResearch.cancel(player);int count=0;for(var research:ResearchData.all())if(ResearchData.get(player).revoke(player,research))count++;refresh(player);return count;}
    private static void refresh(ServerPlayer player){if(player.containerMenu instanceof GuideMenu menu)menu.refresh();}
    public static boolean grant(MinecraftServer server,UUID owner,ResourceLocation id){check(server);Objects.requireNonNull(owner);var player=server.getPlayerList().getPlayer(owner);return player==null?ResearchData.get(server).grant(owner,require(id)):grant(player,id);}
    public static boolean revoke(MinecraftServer server,UUID owner,ResourceLocation id){check(server);Objects.requireNonNull(owner);var player=server.getPlayerList().getPlayer(owner);return player==null?ResearchData.get(server).revoke(owner,require(id)):revoke(player,id);}
    public static int reset(MinecraftServer server,UUID owner){check(server);Objects.requireNonNull(owner);var player=server.getPlayerList().getPlayer(owner);if(player!=null)return reset(player);int count=0;for(var research:ResearchData.all())if(ResearchData.get(server).revoke(owner,research))count++;return count;}
    private AddonResearch(){}
}
