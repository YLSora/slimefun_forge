package io.github.slimefun4.forge;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** Datapack recipes with exact quantities and positions. No Bukkit material/lore identity. */
public final class MachineRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final String definition;
    public final String station;
    public final List<ItemStack> inputs;
    public final ItemStack output;
    public final boolean shaped;
    public final int ticks;
    public final boolean oreDoubling;
    private static final Map<RecipeManager, Map<String,List<MachineRecipe>>> INDEX = new WeakHashMap<>();
    public MachineRecipe(ResourceLocation id, String station, List<ItemStack> inputs, ItemStack output, boolean shaped, int ticks, boolean oreDoubling) {
        if(oreDoubling && (!station.equals("ore_crusher") || shaped || inputs.stream().filter(s->!s.isEmpty()).count()!=1
            || inputs.stream().filter(s->!s.isEmpty()).anyMatch(s->s.getCount()!=1) || output.isEmpty() || output.getCount()%2!=0))
            throw new IllegalArgumentException("Invalid ore doubling recipe "+id);
        this.id=id; this.station=station; this.inputs=List.copyOf(inputs); this.output=output; this.shaped=shaped; this.ticks=ticks; this.oreDoubling=oreDoubling;
        var key=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem());
        definition=key.getNamespace().equals(SlimefunForge.ID)?key.getPath():key.getNamespace().equals("minecraft")?Catalog.VANILLA.stream().filter(v->v.material().equals(key.getPath())).map(Catalog.Vanilla::id).findFirst().orElse(null):key.toString();
    }
    private boolean enabled(){return definition==null||MachineClock.enabled(definition);}
    public static synchronized void invalidate() { INDEX.clear(); }
    public static synchronized List<MachineRecipe> forStation(Level level, String station) {
        return INDEX.computeIfAbsent(level.getRecipeManager(), manager -> {
            Map<String,List<MachineRecipe>> index = new HashMap<>();
            boolean doubled=OreGrinding.doubleOres();
            // RC-37 RecipeType.getRecipeInputList: smelteries try more nonempty inputs first.
            // Sort only when rebuilding the index; preserve deterministic ID order for ties.
            manager.getAllRecipesFor(SlimefunForge.PROCESSING.get()).stream()
                .filter(MachineRecipe::enabled)
                .sorted(Comparator.comparingLong((MachineRecipe r) -> r.station.equals("smeltery")
                    ? -r.inputs.stream().filter(s -> !s.isEmpty()).count() : 0)
                    .thenComparing(r -> r.id))
                .forEach(r -> index.computeIfAbsent(r.station,k -> new ArrayList<>()).add(r.oreDoubling && !doubled
                    ? new MachineRecipe(r.id,r.station,r.inputs,r.output.copyWithCount(r.output.getCount()/2),r.shaped,r.ticks,false) : r));
            var smeltery=new ArrayList<MachineRecipe>();
            for(var recipe:index.getOrDefault("smeltery",List.of())) {
                var inputs=recipe.inputs.stream().filter(s->!s.isEmpty()).toList();
                if(recipe.shaped || inputs.isEmpty()) continue;
                var key=ForgeRegistries.ITEMS.getKey(inputs.get(0).getItem());
                if(inputs.size()==1 && key!=null && key.getNamespace().equals(SlimefunForge.ID) && key.getPath().endsWith("_dust")) continue;
                smeltery.add(new MachineRecipe(recipe.id,"electric_smeltery",inputs,recipe.output,false,240,false));
            }
            index.put("electric_smeltery",List.copyOf(smeltery));
            var grinder=new ArrayList<MachineRecipe>();
            for(var source:List.of("grind_stone","ore_crusher")) for(var recipe:index.getOrDefault(source,List.of())) {
                var inputs=recipe.inputs.stream().filter(s->!s.isEmpty()).toList();
                if(!recipe.shaped && inputs.size()==1) grinder.add(new MachineRecipe(recipe.id,"electric_ore_grinder",inputs,recipe.output,false,80,false));
            }
            if(!OreGrinding.legacy()) grinder.sort(Comparator.comparingInt(r->-r.inputs.get(0).getCount()));
            index.put("electric_ore_grinder",List.copyOf(grinder));
            var pulverizer=index.computeIfAbsent("ingot_pulverizer",k->new ArrayList<>());
            for(var recipe:index.getOrDefault("ingot_factory",List.of())) {
                var inputs=recipe.inputs.stream().filter(s->!s.isEmpty()).toList();
                if(recipe.shaped || inputs.size()!=1) continue;
                var key=ForgeRegistries.ITEMS.getKey(inputs.get(0).getItem());
                if(key!=null && key.getNamespace().equals(SlimefunForge.ID) && key.getPath().endsWith("_dust"))
                    pulverizer.add(new MachineRecipe(recipe.id,"ingot_pulverizer",List.of(recipe.output),inputs.get(0),false,60,false));
            }
            var furnace=new ArrayList<MachineRecipe>();
            manager.getAllRecipesFor(RecipeType.SMELTING).stream().sorted(Comparator.comparing(SmeltingRecipe::getId)).forEach(recipe -> {
                var ingredient=recipe.getIngredients().get(0);
                // RC-37 expands MaterialChoice and always uses four seconds, independent of furnace time.
                if(ingredient.isSimple()) for(var input:ingredient.getItems()) if(!input.isEmpty())
                    furnace.add(new MachineRecipe(recipe.getId(),"electric_furnace",List.of(new ItemStack(input.getItem())),recipe.getResultItem(level.registryAccess()).copy(),false,80,false));
            });
            index.put("electric_furnace",List.copyOf(furnace));
            var drier=index.computeIfAbsent("auto_drier",k->new ArrayList<>());
            for(var tag:List.of(BlockTags.SAPLINGS,BlockTags.LEAVES)) {
                var blocks=new ArrayList<net.minecraft.world.level.block.Block>();
                BuiltInRegistries.BLOCK.getTagOrEmpty(tag).forEach(holder->blocks.add(holder.value()));
                blocks.sort(Comparator.comparing(BuiltInRegistries.BLOCK::getKey));
                for(var block:blocks) if(block.asItem()!=Items.AIR) {
                    var key=BuiltInRegistries.BLOCK.getKey(block);
                    drier.add(new MachineRecipe(SlimefunForge.key("auto_drier/"+tag.location().getPath()+"/"+key.getNamespace()+"/"+key.getPath()),"auto_drier",List.of(new ItemStack(block)),new ItemStack(Items.STICK,tag.equals(BlockTags.SAPLINGS)?2:1),false,120,false));
                }
            }
            return index;
        }).getOrDefault(station, List.of()).stream().filter(r->ItemAccess.enabled(level,r.output)).toList();
    }
    public static boolean same(ItemStack actual, ItemStack required) {
        return same(actual,required,true);
    }
    private static boolean same(ItemStack actual,ItemStack required,boolean checkDistinction) {
        if (actual.isEmpty() || required.isEmpty()) return actual.isEmpty() && required.isEmpty();
        if (actual.getItem()!=required.getItem()) return false;
        if(actual.getItem() instanceof io.github.slimefun4.forge.api.ItemApi.Distinctive)return AddonItems.matches(actual,required,io.github.slimefun4.forge.api.ItemApi.Match.RECIPE);
        var key=ForgeRegistries.ITEMS.getKey(required.getItem());
        if(actual.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Bag) {
            if(checkDistinction) {
                var actualDisplay=actual.getTagElement("display");
                var requiredDisplay=required.getTagElement("display");
                var actualLore=actualDisplay==null?new ListTag():actualDisplay.getList("Lore",8);
                var requiredLore=requiredDisplay==null?new ListTag():requiredDisplay.getList("Lore",8);
                return Soulbinding.comparableLore(actualLore).equals(Soulbinding.comparableLore(requiredLore));
            }
            return true;
        }
        if(key!=null&&key.getNamespace().equals(SlimefunForge.ID))return true;
        return Objects.equals(Soulbinding.comparableTag(actual),Soulbinding.comparableTag(required));
    }
    /** Returns exact slot debits or null. Duplicate ingredients cannot spend a slot twice. */
    public int[] debits(Container inv) {
        if(!enabled())return null;
        int[] debit = new int[inv.getContainerSize()];
        boolean checkDistinction=!station.equals("enhanced_crafting_table") && !station.equals("magic_workbench");
        boolean singleStack=switch(station) {
            case "grind_stone","compressor","ore_crusher","pressure_chamber","ore_washer" -> true;
            default -> false;
        };
        if(!shaped && singleStack && inputs.stream().filter(s -> !s.isEmpty()).count()==1) {
            var required=inputs.stream().filter(s -> !s.isEmpty()).findFirst().orElseThrow();
            for(int i=0;i<inv.getContainerSize();i++) {
                if(same(inv.getItem(i),required) && inv.getItem(i).getCount()>=required.getCount()) {
                    // RC-37 GrindStone checks the recipe amount, then removes exactly one item.
                    debit[i]=station.equals("grind_stone")?1:required.getCount(); return debit;
                }
            }
            return null;
        }
        if (shaped) {
            if (inv.getContainerSize() < inputs.size()) return null;
            for (int i=0;i<inputs.size();i++) {
                var required=inputs.get(i); var actual=inv.getItem(i);
                if (!same(actual, required,checkDistinction) || actual.getCount()<required.getCount()) return null;
                debit[i] = required.getCount();
            }
        } else {
            for (var required : inputs) {
                if (required.isEmpty()) continue;
                int remaining=required.getCount();
                for (int i=0;i<inv.getContainerSize() && remaining>0;i++) {
                    if (same(inv.getItem(i),required,checkDistinction)) {
                        int count=Math.min(remaining,inv.getItem(i).getCount()-debit[i]);
                        debit[i]+=count; remaining-=count;
                    }
                }
                if (remaining>0) return null;
            }
        }
        return debit;
    }
    /** Plan all consumption, remainders and outputs against a copy, commit only on success. */
    public boolean craft(Container source, Container target, ItemStack... additionalOutputs) {
        var plan=prepare(source,additionalOutputs);
        if(plan==null) return false;
        var out=source==target ? plan.input : copy(target);
        for(var stack:plan.outputs) if(!out.addItem(stack.copy()).isEmpty()) return false;
        commit(source,plan.input);
        if(source!=target) commit(target,out);
        return true;
    }
    /** The paid output is fixed before input is committed, including backpack data and remainders. */
    public record CraftPlan(SimpleContainer input,List<ItemStack> outputs) {}
    public CraftPlan prepare(Container source,ItemStack... additionalOutputs) {
        var debit=debits(source);
        if (debit==null) return null;
        var in=copy(source);
        var result=output.copy();
        if (result.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Bag) {
            for (int i=0;i<source.getContainerSize();i++) {
                var original=source.getItem(i);
                if (debit[i]>0 && original.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Bag) { if(original.hasTag())result.setTag(original.getTag().copy());if(Soulbinding.bound(original))Soulbinding.bind(result);break; }
            }
        }
        List<ItemStack> remainder=new ArrayList<>();
        for (int i=0;i<debit.length;i++) if(debit[i]>0) {
            ItemStack original=in.getItem(i);
            if(original.hasCraftingRemainingItem()) for(int j=0;j<debit[i];j++) remainder.add(original.getCraftingRemainingItem());
            original.shrink(debit[i]);
        }
        remainder.add(result);
        for(var extra:additionalOutputs) remainder.add(extra.copy());
        return new CraftPlan(in,List.copyOf(remainder));
    }
    public static SimpleContainer copy(Container source) {
        var result=new SimpleContainer(source.getContainerSize());
        for(int i=0;i<source.getContainerSize();i++) result.setItem(i,source.getItem(i).copy());
        return result;
    }
    static void commit(Container target,Container planned) {
        for(int i=0;i<target.getContainerSize();i++) target.setItem(i,planned.getItem(i));
        target.setChanged();
    }
    @Override public boolean matches(Container inv,Level level) { return debits(inv)!=null; }
    @Override public ItemStack assemble(Container inv,RegistryAccess access) { return output.copy(); }
    @Override public ItemStack getResultItem(RegistryAccess access) { return output; }
    @Override public boolean canCraftInDimensions(int w,int h) { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return SlimefunForge.PROCESSING_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return SlimefunForge.PROCESSING.get(); }
    @Override public boolean isSpecial() { return true; }
    public static final class Serializer implements RecipeSerializer<MachineRecipe> {
        private ItemStack read(JsonElement json) {
            if(json==null || json.isJsonNull()) return ItemStack.EMPTY;
            var obj=json.getAsJsonObject(); var key=new ResourceLocation(obj.get("item").getAsString());
            if(!ForgeRegistries.ITEMS.containsKey(key)) throw new JsonParseException("Unknown item "+key);
            int count=obj.has("count") ? obj.get("count").getAsInt() : 1;
            if(count<1 || count>64) throw new JsonParseException("Invalid count "+count);
            return obj.has("nbt")?net.minecraftforge.common.crafting.CraftingHelper.getItemStack(obj,true,true):Catalog.stack(ForgeRegistries.ITEMS.getValue(key),count);
        }
        @Override public MachineRecipe fromJson(ResourceLocation id,JsonObject obj) {
            List<ItemStack> inputs=new ArrayList<>(); obj.getAsJsonArray("inputs").forEach(v -> inputs.add(read(v)));
            boolean shaped=obj.has("shaped") && obj.get("shaped").getAsBoolean();
            if(inputs.isEmpty() || inputs.size()>9 || inputs.stream().allMatch(ItemStack::isEmpty) || (shaped && inputs.size()!=9)) throw new JsonParseException("Invalid input grid "+id);
            int ticks=obj.has("ticks") ? obj.get("ticks").getAsInt() : 80;
            if(ticks<1 || ticks>72000) throw new JsonParseException("Invalid processing time");
            var result=read(obj.get("result")); if(result.isEmpty()) throw new JsonParseException("Empty result");
            if(obj.has("ore_doubling") && (!obj.get("ore_doubling").isJsonPrimitive() || !obj.getAsJsonPrimitive("ore_doubling").isBoolean())) throw new JsonParseException("Invalid ore doubling flag");
            return new MachineRecipe(id,obj.get("station").getAsString(),inputs,result,shaped,ticks,obj.has("ore_doubling") && obj.get("ore_doubling").getAsBoolean());
        }
        @Override public MachineRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buf) {
            String station=buf.readUtf(64); boolean shaped=buf.readBoolean(); int ticks=buf.readVarInt();
            int size=buf.readVarInt(); if(size<1 || size>9) throw new IllegalArgumentException("Invalid grid size");
            List<ItemStack> inputs=new ArrayList<>(); for(int i=0;i<size;i++) inputs.add(buf.readItem());
            var result=buf.readItem();
            return new MachineRecipe(id,station,inputs,result,shaped,ticks,buf.readBoolean());
        }
        @Override public void toNetwork(FriendlyByteBuf buf,MachineRecipe recipe) {
            buf.writeUtf(recipe.station,64); buf.writeBoolean(recipe.shaped); buf.writeVarInt(recipe.ticks); buf.writeVarInt(recipe.inputs.size());
            recipe.inputs.forEach(buf::writeItem); buf.writeItem(recipe.output); buf.writeBoolean(recipe.oreDoubling);
        }
    }
}
