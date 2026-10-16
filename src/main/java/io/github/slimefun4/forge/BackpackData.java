package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.BackpackApi;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

/** One native inventory per issued identity. Item payloads are display mirrors, never a second source. */
public final class BackpackData extends SavedData {
    private static final String KEY="slimefun_backpack";
    private UUID world=UUID.randomUUID();
    private final Map<UUID,String> names=new HashMap<>();
    private final Map<UUID,Integer> lastIds=new HashMap<>();
    private final Map<UUID,Map<Integer,BackpackItem.BagInventory>> inventories=new HashMap<>();
    public static BackpackData get(MinecraftServer server){return NativeData.get(server,"slimefun_backpacks",BackpackData::load,BackpackData::new);}
    Set<UUID> players(){return Set.copyOf(inventories.keySet());}
    boolean delete(MinecraftServer server,UUID owner){var records=inventories.get(owner);if(records==null)return false;for(int id:List.copyOf(records.keySet()))close(server,owner,id);inventories.remove(owner);names.remove(owner);setDirty();return true;}
    public UUID owner(String name){try{var id=UUID.fromString(name);return inventories.containsKey(id)?id:null;}catch(IllegalArgumentException ignored){}return names.entrySet().stream().filter(e->e.getValue().equalsIgnoreCase(name)).map(Map.Entry::getKey).findFirst().orElse(null);}
    public Set<Integer> ids(UUID owner){return Set.copyOf(inventories.getOrDefault(owner,Map.of()).keySet());}
    private BackpackItem.BagInventory create(ItemStack type,int rows){var inventory=new BackpackItem.BagInventory(type,PlayerRules.slots(rows));inventory.addListener(c->setDirty());return inventory;}
    private void identify(ItemStack stack,UUID owner,int id){var tag=new CompoundTag();tag.putUUID("world",world);tag.putUUID("owner",owner);tag.putInt("id",id);stack.getOrCreateTag().put(KEY,tag);}
    private BackpackItem.BagInventory require(UUID owner,int id){var inventory=inventories.getOrDefault(owner,Map.of()).get(id);if(inventory==null)throw new IllegalArgumentException("Unknown backpack "+owner+"#"+id);return inventory;}
    public BackpackItem.BagInventory inventory(ServerPlayer player,ItemStack bag){
        if(!(bag.getItem() instanceof BackpackApi.Bag type)||bag.getCount()!=1)return null;int rows=type.rows(bag.copy());PlayerRules.slots(rows);
        var tag=bag.getOrCreateTag();
        if(!tag.contains(KEY,Tag.TAG_COMPOUND)){
            if(bag.is(SlimefunForge.item("restored_backpack")))return null;
            var owner=player.getUUID();int id=PlayerRules.nextId(lastIds.getOrDefault(owner,-1));
            var empty=bag.copy();empty.removeTagKey("slimefun_inventory");var inventory=create(empty,rows);
            identify(bag,owner,id);lastIds.put(owner,id);names.put(owner,player.getGameProfile().getName());inventories.computeIfAbsent(owner,k->new HashMap<>()).put(id,inventory);setDirty();
        }
        var identity=tag.getCompound(KEY);if(!identity.hasUUID("world")||!identity.getUUID("world").equals(world)||!identity.hasUUID("owner")||!identity.contains("id",Tag.TAG_INT)||identity.getInt("id")<0)return null;
        var records=inventories.get(identity.getUUID("owner"));if(records==null)return null;var inventory=records.get(identity.getInt("id"));if(inventory==null)return null;
        if(!bag.is(SlimefunForge.item("restored_backpack"))&&(inventory.getContainerSize()<rows*9||inventory.type().getItem()!=bag.getItem())){
            var copy=items(inventory);if(copy.stream().anyMatch(s->!s.isEmpty()&&!type.accepts(s.copy())))return null;
            close(player.server,identity.getUUID("owner"),identity.getInt("id"));
            var model=bag.copy();model.removeTagKey("slimefun_inventory");var replacement=create(model,Math.max(rows,inventory.getContainerSize()/9));for(int slot=0;slot<copy.size();slot++)replacement.setItem(slot,copy.get(slot));records.put(identity.getInt("id"),replacement);inventory=replacement;setDirty();
        }
        sync(bag,inventory);return inventory;
    }
    private static ArrayList<ItemStack> items(BackpackItem.BagInventory inventory){var items=new ArrayList<ItemStack>();for(int slot=0;slot<inventory.getContainerSize();slot++)items.add(inventory.getItem(slot).copy());return items;}
    public Optional<BackpackApi.Snapshot> snapshot(UUID owner,int id){var inventory=inventories.getOrDefault(owner,Map.of()).get(id);return inventory==null?Optional.empty():Optional.of(new BackpackApi.Snapshot(world,owner,id,ForgeRegistries.ITEMS.getKey(inventory.type().getItem()),inventory.getContainerSize()/9,items(inventory)));}
    public void close(MinecraftServer server,UUID owner,int id){var inventory=require(owner,id);for(var viewer:List.copyOf(server.getPlayerList().getPlayers()))if(viewer.containerMenu instanceof BackpackItem.BagMenu menu&&menu.inventory()==inventory)viewer.closeContainer();}
    public void edit(MinecraftServer server,UUID owner,int id,Consumer<List<ItemStack>> edit){
        close(server,owner,id);var inventory=require(owner,id);long revision=inventory.revision();var items=items(inventory);edit.accept(items);
        if(require(owner,id)!=inventory||inventory.revision()!=revision)throw new IllegalStateException("Reentrant backpack edit");
        if(items.size()!=inventory.getContainerSize())throw new IllegalArgumentException("Backpack edit cannot resize");
        for(int slot=0;slot<items.size();slot++){var stack=Objects.requireNonNull(items.get(slot));if(!stack.isEmpty()&&(stack.getCount()<1||stack.getCount()>Math.min(64,stack.getMaxStackSize())||!inventory.canPlaceItem(slot,stack.copy())))throw new IllegalArgumentException("Invalid backpack contents at "+slot);}
        if(require(owner,id)!=inventory||inventory.revision()!=revision||server.getPlayerList().getPlayers().stream().anyMatch(p->p.containerMenu instanceof BackpackItem.BagMenu menu&&menu.inventory()==inventory))throw new IllegalStateException("Backpack changed or reopened during edit");
        for(int slot=0;slot<items.size();slot++)inventory.setItem(slot,items.get(slot).copy());inventory.setChanged();
    }
    public void expand(MinecraftServer server,UUID owner,int id,int rows){int size=PlayerRules.slots(rows);var inventory=require(owner,id);if(size<inventory.getContainerSize())throw new IllegalArgumentException("Cannot shrink backpack");if(size==inventory.getContainerSize())return;close(server,owner,id);var type=inventory.type();sync(type,inventory);var replacement=create(type,rows);inventories.get(owner).put(id,replacement);setDirty();}
    public ItemStack restore(UUID owner,int id){var inventory=inventories.getOrDefault(owner,Map.of()).get(id);if(inventory==null)return ItemStack.EMPTY;var result=new ItemStack(SlimefunForge.item("restored_backpack"));identify(result,owner,id);sync(result,inventory);return result;}
    public static void sync(ItemStack bag,BackpackItem.BagInventory inventory){var contents=net.minecraft.core.NonNullList.withSize(inventory.getContainerSize(),ItemStack.EMPTY);for(int slot=0;slot<contents.size();slot++)contents.set(slot,inventory.getItem(slot));bag.getOrCreateTag().put("slimefun_inventory",net.minecraft.world.ContainerHelper.saveAllItems(new CompoundTag(),contents));}
    public static String identity(ItemStack bag){var tag=bag.getTagElement(KEY);return tag!=null&&tag.hasUUID("world")&&tag.hasUUID("owner")?tag.getUUID("world")+"/"+tag.getUUID("owner")+"#"+tag.getInt("id"):"";}
    public static BackpackData load(CompoundTag tag){
        if(tag.getInt("format")!=2||!tag.hasUUID("world")||!(tag.get("owners") instanceof ListTag owners)||!owners.isEmpty()&&owners.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid backpack format");var data=new BackpackData();data.world=tag.getUUID("world");
        for(var value:owners){var ownerTag=(CompoundTag)value;var owner=ownerTag.getUUID("uuid");if(!ownerTag.contains("last_id",Tag.TAG_INT)||ownerTag.getInt("last_id")<0||data.lastIds.putIfAbsent(owner,ownerTag.getInt("last_id"))!=null)throw new IllegalArgumentException("Invalid backpack owner counter");data.names.put(owner,ownerTag.getString("name"));var records=new HashMap<Integer,BackpackItem.BagInventory>();
            if(!(ownerTag.get("bags") instanceof ListTag bags)||!bags.isEmpty()&&bags.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Missing backpacks");
            for(var entry:bags){var saved=(CompoundTag)entry;int rows=saved.getInt("rows"),id=saved.getInt("id");PlayerRules.slots(rows);var typeId=new ResourceLocation(saved.getString("type"));var item=ForgeRegistries.ITEMS.getValue(typeId);
                if(!saved.contains("id",Tag.TAG_INT)||id<0||id>ownerTag.getInt("last_id")||!(item instanceof BackpackApi.Bag))throw new IllegalArgumentException("Invalid saved backpack "+typeId);var model=new ItemStack(item);model.getOrCreateTag().put("slimefun_inventory",saved.getCompound("contents"));var inventory=data.create(model,rows);
                var occupied=new HashSet<Integer>();var contents=saved.getCompound("contents");if(!(contents.get("Items") instanceof ListTag stacks)||!stacks.isEmpty()&&stacks.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid saved backpack contents");
                for(var stackTag:stacks){var stack=(CompoundTag)stackTag;int slot=stack.getByte("Slot")&255;var decoded=ItemStack.of(stack);if(slot>=rows*9||!occupied.add(slot)||decoded.isEmpty()||decoded.getCount()>decoded.getMaxStackSize()||!inventory.canPlaceItem(slot,decoded.copy()))throw new IllegalArgumentException("Invalid saved backpack slot");}
                if(records.putIfAbsent(id,inventory)!=null)throw new IllegalArgumentException("Duplicate backpack ID");
            }if(!records.isEmpty())data.inventories.put(owner,records);
        }return data;
    }
    @Override public CompoundTag save(CompoundTag tag){tag.putInt("format",2);tag.putUUID("world",world);var owners=new ListTag();lastIds.forEach((owner,last)->{var saved=new CompoundTag();saved.putUUID("uuid",owner);saved.putString("name",names.getOrDefault(owner,""));saved.putInt("last_id",last);var bags=new ListTag();inventories.getOrDefault(owner,Map.of()).forEach((id,inventory)->{var value=new CompoundTag();value.putInt("id",id);value.putInt("rows",inventory.getContainerSize()/9);value.putString("type",ForgeRegistries.ITEMS.getKey(inventory.type().getItem()).toString());var model=inventory.type();sync(model,inventory);value.put("contents",model.getTag().getCompound("slimefun_inventory"));bags.add(value);});saved.put("bags",bags);owners.add(saved);});tag.put("owners",owners);return tag;}
    @Override public void save(java.io.File file){NativeData.save(this,file);}
}
