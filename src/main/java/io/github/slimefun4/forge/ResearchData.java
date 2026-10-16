package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Owned by one server's overworld. Shared across dimensions, retained across death. */
public final class ResearchData extends SavedData {
    private final Map<UUID, Set<String>> unlocked = new HashMap<>();
    private static final Map<String, Catalog.Research> BY_ITEM = new HashMap<>();
    static { for (var research : Catalog.RESEARCH) for (String id : research.items()) BY_ITEM.put(id, research); }
    static List<Catalog.Research> all(){return java.util.stream.Stream.concat(Catalog.RESEARCH.stream(),ADDONS.stream()).toList();}
    private static final List<Catalog.Research> ADDONS=new ArrayList<>();
    static void addons(){for(var definition:AddonContent.RESEARCHES.values()){
        var items=AddonContent.ENTRIES.values().stream().filter(e->definition.id().equals(e.research())).map(e->e.item().toString()).toList();
        var research=new Catalog.Research(definition.id().toString(),definition.title(),definition.cost(),items);
        ADDONS.add(research);for(String item:items)BY_ITEM.put(item,research);
    }}
    public static ResearchData get(ServerPlayer player) {
        return get(player.server);
    }
    static ResearchData get(net.minecraft.server.MinecraftServer server){return NativeData.get(server,"slimefun_research",ResearchData::load,ResearchData::new);}
    Set<UUID> players(){return Set.copyOf(unlocked.keySet());}
    boolean delete(UUID owner){if(unlocked.remove(owner)==null)return false;setDirty();return true;}
    @Override public void save(java.io.File file){NativeData.save(this,file);}
    public static Catalog.Research research(ItemStack item) {
        var key = ForgeRegistries.ITEMS.getKey(item.getItem());
        return key==null?null:research(key.getNamespace().equals(SlimefunForge.ID)?key.getPath():key.toString());
    }
    public static Catalog.Research research(String id) { var research=BY_ITEM.get(id);return ResearchSettings.registered(research)?research:null; }
    public boolean knows(ServerPlayer player, Catalog.Research research) {
        return !ResearchSettings.enabled(research) || player.isCreative()&&Guide.FREE_CREATIVE.get() || completed(player,research);
    }
    boolean completed(ServerPlayer player,Catalog.Research research){return completed(player.getUUID(),research);}
    boolean completed(UUID player,Catalog.Research research){return research==null||unlocked.getOrDefault(player,Set.of()).contains(research.id());}
    boolean pay(ServerPlayer player,Catalog.Research research){
        if(!ResearchSettings.enabled(research)||player.isCreative()&&Guide.FREE_CREATIVE.get())return true;
        int cost=ResearchSettings.cost(research);if(player.experienceLevel<cost)return false;
        player.giveExperienceLevels(-cost);return true;
    }
    boolean grant(ServerPlayer player,Catalog.Research research){
        if(!grant(player.getUUID(),research))return false;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new io.github.slimefun4.forge.api.ResearchCompletedEvent(player,new net.minecraft.resources.ResourceLocation(AddonContent.commandId(research))));
        return true;
    }
    boolean revoke(ServerPlayer player,Catalog.Research research){
        return revoke(player.getUUID(),research);
    }
    boolean grant(UUID owner,Catalog.Research research){if(!unlocked.computeIfAbsent(owner,k->new HashSet<>()).add(research.id()))return false;setDirty();return true;}
    boolean revoke(UUID owner,Catalog.Research research){var values=unlocked.get(owner);if(values==null||!values.remove(research.id()))return false;if(values.isEmpty())unlocked.remove(owner);setDirty();return true;}
    public boolean unlock(ServerPlayer player, Catalog.Research research) {
        if (!ResearchSettings.enabled(research) || unlocked.getOrDefault(player.getUUID(), Set.of()).contains(research.id())) return true;
        if(!pay(player,research))return false;
        grant(player,research);
        return true;
    }
    public static boolean canUse(ServerPlayer player, ItemStack item) { String id=ItemAccess.id(item);return id==null||ItemAccess.canUse(player,id); }
    private static boolean enabled(Catalog.Research research){return ResearchSettings.registered(research)&&(research.items().isEmpty()||research.items().stream().anyMatch(MachineClock::enabled));}
    public static int total(){return (int)ResearchData.all().stream().filter(ResearchData::enabled).count();}
    public int completed(ServerPlayer player) { return (int)ResearchData.all().stream().filter(ResearchData::enabled).filter(r->unlocked.getOrDefault(player.getUUID(),Set.of()).contains(r.id())).count(); }
    public long spent(ServerPlayer player){return ResearchData.all().stream().filter(r->completed(player,r)).mapToLong(ResearchSettings::cost).sum();}
    void reset(ServerPlayer player){var values=unlocked.get(player.getUUID());if(values!=null&&values.removeAll(ResearchData.all().stream().filter(ResearchSettings::registered).map(Catalog.Research::id).toList())){if(values.isEmpty())unlocked.remove(player.getUUID());setDirty();}}
    public void share(UUID owner,ServerPlayer recipient) {
        var source=Set.copyOf(unlocked.getOrDefault(owner,Set.of()));
        for(var research:ResearchData.all())if(source.contains(research.id())&&ResearchSettings.enabled(research)&&!completed(recipient,research))GuideResearch.share(recipient,research);
    }
    public static ResearchData load(CompoundTag nbt) {
        var result = new ResearchData();
        for (String key : nbt.getAllKeys()) {
            UUID id = UUID.fromString(key);
            var values = new HashSet<String>();
            if(!(nbt.get(key) instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=net.minecraft.nbt.Tag.TAG_STRING)throw new IllegalArgumentException("Invalid stored research list for "+key);
            for (int i=0;i<list.size();i++) if(list.getString(i).isBlank()||!values.add(list.getString(i)))throw new IllegalArgumentException("Invalid stored research ID for "+key);
            result.unlocked.put(id, values);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag nbt) {
        unlocked.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var list = new ListTag();
            entry.getValue().stream().sorted().forEach(v -> list.add(StringTag.valueOf(v)));
            nbt.put(entry.getKey().toString(), list);
        });
        return nbt;
    }
}
