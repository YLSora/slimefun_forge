package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** RC-37 transfer and binding rules, using native item tags and server-thread events. */
final class Enchanting {
    private record Limits(ForgeConfigSpec.BooleanValue useLevel,ForgeConfigSpec.IntValue level,
            ForgeConfigSpec.BooleanValue useMax,ForgeConfigSpec.IntValue max,ForgeConfigSpec.BooleanValue useLore,
            ForgeConfigSpec.ConfigValue<List<? extends String>> lore,ForgeConfigSpec.BooleanValue override) {}
    private record Permission(ForgeConfigSpec.BooleanValue enchant,ForgeConfigSpec.BooleanValue disenchant) {}
    record Plan(List<ItemStack> ingredients,ItemStack result,ItemStack second,int cycles) {}
    private static final Map<String,Limits> LIMITS=new LinkedHashMap<>();
    private static final Map<String,Permission> ITEMS=new HashMap<>();
    private static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.BooleanValue BYPASS,CUSTOM;
    private static final ForgeConfigSpec.IntValue MAX;
    static {
        var b=new ForgeConfigSpec.Builder();
        for(var id:List.of("auto_enchanter","auto_enchanter_2","auto_disenchanter","auto_disenchanter_2")) {
            b.push(id);var level=b.define("use-enchant-level-limit",false);var cap=b.defineInRange("enchant-level-limit",10,0,32767);
            var amount=b.define("use-max-enchants",false);var max=b.defineInRange("max-enchants",10,0,32767);var useLore=b.define("use-ignored-lores",false);
            String name=(id.startsWith("auto_disenchanter")?"&5Auto Disenchanter":"&5Auto Enchanter")+(id.endsWith("_2")?" &7- &eII":"");
            var lore=b.defineListAllowEmpty(List.of("ignored-lores"),List.of("&7- &cCan't be used in "+name),v->v instanceof String);
            var override=id.startsWith("auto_enchanter")?b.define("override-existing-enchants-lvl",false):null;
            LIMITS.put(id,new Limits(level,cap,amount,max,useLore,lore,override));b.pop();
        }
        b.push("book_binder");BYPASS=b.define("bypass-vanilla-max-level",false);CUSTOM=b.define("has-custom-max-level",false);MAX=b.defineInRange("custom-max-level",15,0,Integer.MAX_VALUE);b.pop();
        b.push("items");for(var entry:Catalog.ITEMS)if(!entry.implementation().equals("HiddenItem")) {
            b.push(entry.id());ITEMS.put(entry.id(),new Permission(b.define("allow-enchanting",true),b.define("allow-disenchanting",!entry.implementation().equals("EnchantedItem"))));b.pop();
        }b.pop();CONFIG=b.build();
    }
    private Enchanting() {}
    static void register(IEventBus bus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-enchanting-server.toml");
        bus.addListener((ModConfigEvent.Reloading event)->{if(event.getConfig().getSpec()!=CONFIG)return;var server=ServerLifecycleHooks.getCurrentServer();if(server!=null)server.execute(()->{for(var level:server.getAllLevels())EnergyNetwork.recipesReloaded(level);});});
    }
    static boolean machine(String implementation){return implementation.equals("AutoEnchanter")||implementation.equals("AutoDisenchanter")||implementation.equals("BookBinder");}
    static Plan find(ProcessingEntity machine) {
        return find(machine,machine.inventory,machine.definition.id(),machine.definition.speed());
    }
    static Plan find(net.minecraft.world.level.block.entity.BlockEntity machine,net.minecraft.world.Container inventory,String profile,int speed) {
        if(profile.equals("book_binder"))return bind(machine,inventory,speed);
        boolean disenchant=profile.startsWith("auto_disenchanter");var limits=LIMITS.get(profile);
        for(int index=0;index<2;index++) {
            int slot=disenchant?index:1-index;var target=inventory.getItem(slot);
            if(!eligible(target,disenchant,limits))continue;
            boolean canceled=disenchant?MinecraftForge.EVENT_BUS.post(new AutoDisenchantEvent(machine,target)):MinecraftForge.EVENT_BUS.post(new AutoEnchantEvent(machine,target));
            machine.setChanged();
            if(canceled||!current(machine,inventory,slot,target)||!eligible(target,disenchant,limits))return null;
            var book=inventory.getItem(1-slot);if(!book.is(disenchant?Items.BOOK:Items.ENCHANTED_BOOK)||book.isEmpty())continue;
            if(!disenchant) {
                boolean stopped=MinecraftForge.EVENT_BUS.post(new AutoEnchanterProcessEvent(machine,target,book));machine.setChanged();
                if(stopped||!current(machine,inventory,slot,target)||!current(machine,inventory,1-slot,book)||!eligible(target,false,limits)||!book.is(Items.ENCHANTED_BOOK))return null;
            }
            return transfer(machine,inventory,target,book,disenchant,limits,speed);
        }
        return null;
    }
    private static boolean current(net.minecraft.world.level.block.entity.BlockEntity machine,net.minecraft.world.Container inventory,int slot,ItemStack input) {
        return !machine.isRemoved()&&machine.getLevel().getBlockEntity(machine.getBlockPos())==machine&&inventory.getItem(slot)==input&&!input.isEmpty()&&ItemAccess.enabled(machine.getLevel(),input);
    }
    private static boolean eligible(ItemStack item,boolean disenchant,Limits limits) {
        if(item.isEmpty()||item.is(disenchant?Items.BOOK:Items.ENCHANTED_BOOK)||ignored(item,limits))return false;
        return allowed(item,disenchant);
    }
    static boolean allowed(ItemStack item,boolean disenchant) {
        if(item.getItem() instanceof io.github.slimefun4.forge.api.ItemApi.FixedSettings fixed)return MachineClock.enabled(item)&&(disenchant?fixed.allowDisenchanting(item.copy()):fixed.allowEnchanting(item.copy()));
        var id=BuiltInRegistries.ITEM.getKey(item.getItem());var permission=id.getNamespace().equals("slimefun")?ITEMS.get(id.getPath()):null;
        if(AddonContent.ENTRIES.containsKey(id)){var policy=AddonSettings.item(id.toString());return AddonSettings.enabled(id.toString())&&(disenchant?policy.disenchant():policy.enchant());}
        return permission==null||(disenchant?permission.disenchant:permission.enchant).get();
    }
    private static boolean ignored(ItemStack item,Limits limits) {
        if(!limits.useLore.get())return false;var display=item.getTagElement("display");if(display==null)return false;
        try {for(var tag:display.getList("Lore",Tag.TAG_STRING)){var text=Component.Serializer.fromJson(tag.getAsString());if(text==null)return true;var line=ItemText.glyphs(text);for(var ignored:limits.lore.get())if(line.equals(ItemText.glyphs(Component.literal(ignored.replaceAll("(?i)&([0-9a-fk-or])","§$1")))))return true;}}
        catch(RuntimeException malformed){return true;}return false;
    }
    // Native helpers clamp reads to 255 and switch book fields; neither fits RC-37 transfers.
    private static Map<Enchantment,Integer> read(ItemStack item,boolean stored) {
        var result=new LinkedHashMap<Enchantment,Integer>();String key=stored?"StoredEnchantments":"Enchantments";var tag=item.getTag();if(tag==null||!tag.contains(key))return result;
        if(!(tag.get(key) instanceof ListTag entries)||!entries.isEmpty()&&entries.getElementType()!=Tag.TAG_COMPOUND)return null;
        for(var value:entries){var entry=(CompoundTag)value;var id=ResourceLocation.tryParse(entry.getString("id"));var enchant=id==null?null:BuiltInRegistries.ENCHANTMENT.getOptional(id).orElse(null);int level=entry.getInt("lvl");if(enchant==null||level<1||level>32767||result.put(enchant,level)!=null)return null;}
        return result;
    }
    private static void write(ItemStack item,boolean stored,Map<Enchantment,Integer> enchantments) {
        String key=stored?"StoredEnchantments":"Enchantments";if(enchantments.isEmpty()){item.removeTagKey(key);return;}
        var tags=new ListTag();enchantments.forEach((enchant,level)->tags.add(EnchantmentHelper.storeEnchantment(BuiltInRegistries.ENCHANTMENT.getKey(enchant),level)));item.addTagElement(key,tags);
    }
    private static Plan transfer(net.minecraft.world.level.block.entity.BlockEntity machine,net.minecraft.world.Container inventory,ItemStack target,ItemStack book,boolean disenchant,Limits limits,int speed) {
        var original=read(target,false);var available=disenchant?original:read(book,true);if(original==null||available==null)return null;
        var selected=new LinkedHashMap<Enchantment,Integer>();
        for(var entry:available.entrySet())if(disenchant||entry.getKey().canEnchant(target)) {
            if(!limits.useLevel.get()||entry.getValue()<=limits.level.get())selected.put(entry.getKey(),entry.getValue());
            else {
                for(var player:machine.getLevel().players())if(player.containerMenu.slots.stream().anyMatch(slot->slot.container==inventory))Messages.send(player,Component.translatable("message.slimefun.enchantment_level_limit",limits.level.get()),true,false);
                return null;
            }
        }
        int preExisting=0;
        if(!disenchant) {
            if(!limits.override.get())selected.entrySet().removeIf(e->original.getOrDefault(e.getKey(),0)>=e.getValue());
            var ordinaryBook=read(book,false);if(ordinaryBook==null)return null;for(var enchant:original.keySet())if(ordinaryBook.containsKey(enchant))preExisting++;
        }
        if(selected.isEmpty()||limits.useMax.get()&&selected.size()+preExisting>limits.max.get())return null;
        var result=target.copyWithCount(1);var second=new ItemStack(disenchant?Items.ENCHANTED_BOOK:Items.BOOK);
        if(disenchant){selected.keySet().forEach(original::remove);write(result,false,original);write(second,true,selected);second.setRepairCost(target.getBaseRepairCost());result.setRepairCost(0);}
        else{original.putAll(selected);write(result,false,original);}
        var ingredients=disenchant?List.of(book.copyWithCount(1),target.copyWithCount(1)):List.of(target.copyWithCount(1),book.copyWithCount(1));
        return new Plan(ingredients,result,second,2*((disenchant?90:75)*selected.size()/speed));
    }
    private static Plan bind(net.minecraft.world.level.block.entity.BlockEntity machine,net.minecraft.world.Container inventory,int speed) {
        var first=inventory.getItem(0);var second=inventory.getItem(1);if(!first.is(Items.ENCHANTED_BOOK)||!second.is(Items.ENCHANTED_BOOK)||first.isEmpty()||second.isEmpty()||!ItemAccess.enabled(machine.getLevel(),first)||!ItemAccess.enabled(machine.getLevel(),second))return null;
        var a=read(first,true);var b=read(second,true);if(a==null||b==null)return null;
        for(var entries:List.of(a,b))for(var e:entries.entrySet())if(e.getValue()>(BYPASS.get()?MAX.get():e.getKey().getMaxLevel()))return null;
        var merged=new LinkedHashMap<>(a);boolean conflicts=false;
        for(var entry:b.entrySet()) {
            for(var existing:merged.keySet())if(entry.getKey()!=existing&&!entry.getKey().isCompatibleWith(existing))conflicts=true;
            if(!conflicts)merged.merge(entry.getKey(),entry.getValue(),(x,y)->combine(entry.getKey().getMaxLevel(),x,y));
        }
        int cycles=50*(merged.size()/speed);if(merged.values().stream().anyMatch(n->n>32767))return null;
        if(!BYPASS.get())merged.entrySet().removeIf(e->e.getValue()<e.getKey().getMinLevel()||e.getValue()>e.getKey().getMaxLevel());
        if(merged.isEmpty()||merged.equals(a)||merged.equals(b))return null;
        var result=new ItemStack(Items.ENCHANTED_BOOK);write(result,true,merged);return new Plan(List.of(second.copyWithCount(1),first.copyWithCount(1)),result,ItemStack.EMPTY,cycles);
    }
    private static int combine(int vanilla,int first,int second) {
        int highest=Math.max(first,second);if(!BYPASS.get()&&highest>=vanilla)return vanilla;
        int result=first==second?first+1:highest;return CUSTOM.get()?Math.min(result,MAX.get()):result;
    }
    static boolean validSecond(String implementation,ItemStack result,ItemStack second) {
        if(result.getCount()!=1)return false;
        return switch(implementation) {
            case "AutoEnchanter"->ItemStack.matches(second,new ItemStack(Items.BOOK));
            case "AutoDisenchanter"->second.is(Items.ENCHANTED_BOOK)&&second.getCount()==1&&read(second,true)!=null&&!read(second,true).isEmpty();
            case "BookBinder"->second.isEmpty();
            default->throw new IllegalArgumentException("Not an enchanting machine");
        };
    }
}
