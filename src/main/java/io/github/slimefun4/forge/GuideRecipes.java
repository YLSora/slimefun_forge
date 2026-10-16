package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Read-only recipe views; quantities and variants follow the live processing rules. */
final class GuideRecipes {
    record Display(ItemStack item,Component... lore) {}
    private static final Set<String> GENERATORS=Set.of("CoalGenerator","LavaGenerator","BioGenerator","MagnesiumGenerator","CombustionGenerator","NuclearReactor","NetherStarReactor");
    private static Component tr(String key,Object... args){return Component.translatable("guide.slimefun."+key,args);}
    static String section(String id){
        var entry=Catalog.entry(id);
        if(id.endsWith("industrial_miner")||entry!=null&&(entry.behavior().equals("android")||GENERATORS.contains(entry.implementation())))return "fuels";
        return id.equals("geo_miner")?"geo_resources":id.equals("climbing_pick")?"surfaces":"machine_recipes";
    }
    static List<Display> display(Level level,String id){
        var result=new ArrayList<Display>();var entry=Catalog.entry(id);String kind=entry==null?"":entry.implementation();
        if(section(id).equals("fuels")){
            for(var item:BuiltInRegistries.ITEM.stream().filter(i->i!=Items.AIR).sorted(Comparator.comparing(BuiltInRegistries.ITEM::getKey)).toList()){
                var stack=new ItemStack(item);
                if(id.endsWith("industrial_miner")){int ores=MiningResources.fuel(stack,id.startsWith("advanced"));if(ores>0)result.add(new Display(stack,tr("fuel_ores",ores)));}
                else if(entry.behavior().equals("android")){int cycles=AddonAndroids.fuelCycles(AddonAndroids.coreType(AndroidBlock.Type.BASIC,entry.tier()),stack);if(cycles>0)result.add(new Display(stack,tr("fuel_duration",MachineClock.seconds(cycles))));}
                else {
                    int cycles=kind.endsWith("Reactor")?ReactorEntity.fuelCycles(entry,stack):MachineEntity.fuelCycles(entry,stack);
                    if(cycles>0)result.add(new Display(stack,tr("fuel_duration",MachineClock.seconds(cycles)),tr("fuel_power",String.format(Locale.ROOT,"%.2f",entry.production()*20.0/MachineClock.interval()),(long)cycles*entry.production())));
                }
            }
        }else if(id.equals("geo_miner")){
            for(var resource:GeoMinerEntity.resources())result.add(new Display(GeoMinerEntity.result(resource).setHoverName(resource.name())));
        }else if(id.equals("climbing_pick")){
            BuiltInRegistries.BLOCK.stream().filter(b->b.asItem()!=Items.AIR&&(b.defaultBlockState().is(SpecialTools.tag("climbing_pick_strong_surfaces"))||b.defaultBlockState().is(SpecialTools.tag("climbing_pick_weak_surfaces")))).sorted(Comparator.comparing(BuiltInRegistries.BLOCK::getKey)).forEach(b->result.add(new Display(new ItemStack(b))));
        }else for(var recipe:forMachine(level,id)){
            var inputs=recipe.inputs.stream().filter(s->!s.isEmpty()).toList();var lore=new ArrayList<Component>();
            inputs.forEach(s->lore.add(tr("ingredient",s.getCount(),s.getHoverName())));lore.addAll(processingNotes(recipe));
            result.add(new Display(inputs.get(0),lore.toArray(Component[]::new)));result.add(new Display(recipe.output));
        }
        return result;
    }
    static MachineRecipe construction(String id){
        if(!MachineClock.enabled(id)||!GuideContent.RECIPES.has(id))return null;
        var recipe=new MachineRecipe.Serializer().fromJson(SlimefunForge.key("guide/"+id),GuideContent.RECIPES.getAsJsonObject(id));
        if(recipe.oreDoubling&&!OreGrinding.doubleOres())recipe.output.setCount(recipe.output.getCount()/2);
        return recipe;
    }
    static List<MachineRecipe> forMachine(Level level,String id){
        var definition=Catalog.entry(id);String kind=definition==null?"":definition.implementation();
        if(!Set.of("ElectricPress","ElectricIngotFactory","ElectricIngotPulverizer","CarbonPress","ElectricOreGrinder","Freezer","AutoDrier","FoodComposter","Refinery","ElectricGoldPan","OilPump","ProduceCollector","Crucible","Composter").contains(kind)
            &&!Set.of("gold_pan","nether_gold_pan","grind_stone","ore_crusher","compressor","smeltery","ore_washer","pressure_chamber","juicer","makeshift_smeltery","table_saw","automated_panning_machine").contains(id))return List.of();
        String station=ProcessingEntity.recipeStation(kind);
        var result=new ArrayList<MachineRecipe>();
        if(kind.equals("ElectricGoldPan")||Set.of("gold_pan","nether_gold_pan","automated_panning_machine").contains(id)){
            for(int group=0;group<2;group++){
                if(id.equals("gold_pan")&&group!=0||id.equals("nether_gold_pan")&&group!=1)continue;
                for(var input:group==0?List.of(Items.GRAVEL):List.of(Items.SOUL_SAND,Items.SOUL_SOIL))for(var drop:GoldPanDrops.drops(group))
                    add(result,id,List.of(new ItemStack(input)),drop.output(),false,kind.equals("ElectricGoldPan")?((group==0?3:4)/definition.speed())*20:1);
            }
        }else if(id.equals("ore_washer")){
            for(var dust:OreWashing.outputs())add(result,id,List.of(GuideContent.icon("sifted_ore")),dust,false,1);
            add(result,id,List.of(new ItemStack(Items.SAND,2)),GuideContent.icon("salt"),false,1);
            add(result,id,List.of(GuideContent.icon("pulverized_ore")),GuideContent.icon("pure_ore_cluster"),false,1);
        }else if(kind.equals("OilPump"))add(result,id,List.of(new ItemStack(Items.BUCKET)),GuideContent.icon("bucket_of_oil"),false,520);
        else if(kind.equals("ProduceCollector"))for(var recipe:AnimalProduce.RECIPES)add(result,id,List.of(recipe.input()),recipe.output(),false,100);
        else if(kind.equals("Composter"))BuiltInRegistries.ITEM.stream().sorted(Comparator.comparing(BuiltInRegistries.ITEM::getKey)).map(i->ComposterBlock.recipe(new ItemStack(i))).filter(Objects::nonNull).forEach(result::add);
        else {
            if(station==null)station=id.equals("makeshift_smeltery")?"smeltery":id;
            for(var recipe:MachineRecipe.forStation(level,station)){
                if((id.equals("smeltery")||!kind.isEmpty())&&recipe.inputs.stream().filter(s->!s.isEmpty()).count()!=1)continue;
                var inputs=recipe.inputs.stream().map(ItemStack::copy).toList();
                if(kind.equals("Freezer"))for(var input:inputs)if(!input.isEmpty())input.setCount(Freezing.amount(id,input));
                int ticks=definition!=null&&definition.behavior().equals("processing")?recipe.ticks/10/Math.max(1,definition.speed())*10:recipe.ticks;
                add(result,id,inputs,recipe.output,recipe.shaped,ticks);
            }
        }
        return result;
    }
    private static void add(List<MachineRecipe> recipes,String machine,List<ItemStack> inputs,ItemStack output,boolean shaped,int ticks){
        // Display IDs also distinguish native tag alternatives that share one recipe ID.
        recipes.add(new MachineRecipe(SlimefunForge.key("guide/"+machine+"/"+recipes.size()),machine,inputs,output.copy(),shaped,ticks,false));
    }
    static String source(String id){return switch(id){
        case "sifted_ore" -> "gold_pan";
        case "bucket_of_oil" -> "oil_pump";
        case "nether_ice" -> "geo_miner";
        case "broken_spawner" -> "pickaxe_of_containment";
        default -> null;
    };}
    static double panChance(int group,Item output){
        var drops=GoldPanDrops.drops(group);long total=drops.stream().mapToLong(GoldPanDrops.Drop::weight).sum();
        return total==0?0:drops.stream().filter(d->d.output().is(output)).mapToLong(GoldPanDrops.Drop::weight).sum()*100.0/total;
    }
    private static List<Component> processingNotes(MachineRecipe recipe){
        var entry=Catalog.entry(recipe.station);int energy=entry==null?0:entry.consumption()*(recipe.ticks/10+1);
        var notes=new ArrayList<Component>();notes.add(tr("time",entry!=null&&entry.behavior().equals("processing")?(recipe.ticks/10L+1)*MachineClock.interval():recipe.ticks,energy));
        var inputs=recipe.inputs.stream().filter(s->!s.isEmpty()).toList();int group=inputs.isEmpty()?-1:GoldPanDrops.inputGroup(inputs.get(0));
        if(group>=0&&(recipe.station.contains("gold_pan")||recipe.station.equals("automated_panning_machine")))notes.add(tr("chance",String.format(Locale.ROOT,"%.2f",panChance(group,recipe.output.getItem()))));
        if(recipe.station.contains("washer")&&inputs.get(0).is(SlimefunForge.item("sifted_ore"))){notes.add(tr("chance",String.format(Locale.ROOT,"%.2f",100.0/OreWashing.outputs().size())));if(recipe.station.equals("ore_washer"))notes.add(tr("byproduct",GuideContent.name("stone_chunk")));}
        if(entry!=null&&entry.implementation().equals("Freezer")&&inputs.stream().anyMatch(s->s.is(Items.WATER_BUCKET)||s.is(Items.LAVA_BUCKET)))notes.add(tr("byproduct",new ItemStack(Items.BUCKET).getHoverName()));
        if(recipe.station.equals("auto_drier")&&inputs.stream().anyMatch(s->s.is(Items.POTION)||s.is(Items.SPLASH_POTION)||s.is(Items.LINGERING_POTION)))notes.add(tr("any_potion"));
        if(recipe.station.equals("oil_pump"))notes.add(tr("source_oil"));
        if(recipe.station.equals("produce_collector"))notes.add(tr("source_produce",AnimalProduce.RANGE.get()));
        return notes;
    }
    private GuideRecipes(){}
}
