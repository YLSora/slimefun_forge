package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.*;
import io.github.slimefun4.forge.api.MachineApi.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Frozen definitions and their real core consumers, not an independent recipe or save system. */
public final class AddonMachines {
    private static final Map<ResourceLocation,Fuel> FUELS=new LinkedHashMap<>();
    private static final Map<ResourceLocation,Coolant> COOLANTS=new LinkedHashMap<>();
    private static final Map<ResourceLocation,Produce> PRODUCE=new LinkedHashMap<>();
    private static final Map<ResourceLocation,Assembly> ASSEMBLIES=new LinkedHashMap<>();
    private static final Map<ResourceLocation,Multiblock> STRUCTURES=new LinkedHashMap<>();
    private static final Map<ResourceLocation,List<BlockPattern>> PATTERNS=new LinkedHashMap<>();
    private static final Map<ResourceLocation,Set<ResourceLocation>> AUTO=new LinkedHashMap<>();
    private static final Set<BlockEntity> CONSUMING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<UUID> INTERACTING=new HashSet<>();
    private static <T> void put(Map<ResourceLocation,T> values,ResourceLocation id,T value){AddonContent.open();AddonContent.owned(id);if(values.putIfAbsent(id,Objects.requireNonNull(value))!=null)throw new IllegalArgumentException("Duplicate machine definition "+id);}
    public static synchronized void register(Fuel value){Objects.requireNonNull(value);put(FUELS,value.id(),value);}
    public static synchronized void register(Coolant value){Objects.requireNonNull(value);put(COOLANTS,value.id(),value);}
    public static synchronized void register(Produce value){Objects.requireNonNull(value);put(PRODUCE,value.id(),value);}
    public static synchronized void register(Assembly value){Objects.requireNonNull(value);put(ASSEMBLIES,value.id(),value);}
    public static synchronized void register(Multiblock value){Objects.requireNonNull(value);put(STRUCTURES,value.id(),value);}
    public static synchronized void autoCraft(ResourceLocation machine,ResourceLocation station){
        AddonContent.open();AddonContent.owned(station);Objects.requireNonNull(machine);
        if(!AUTO.computeIfAbsent(machine,k->new LinkedHashSet<>()).add(station))throw new IllegalArgumentException("Duplicate automatic station "+station+" for "+machine);
    }
    static void freeze(){
        var inputs=new HashSet<String>();
        for(var fuel:FUELS.values()){
            AddonContent.item(fuel.machine());AddonContent.item(fuel.input());if(fuel.result()!=null)AddonContent.item(fuel.result());
            coreMachine(fuel.machine(),Set.of("CoalGenerator","LavaGenerator","BioGenerator","MagnesiumGenerator","CombustionGenerator","NuclearReactor","NetherStarReactor"));
            if(!inputs.add(fuel.machine()+"/"+fuel.input()))throw new IllegalArgumentException("Duplicate machine fuel input "+fuel.id());
        }
        for(var coolant:COOLANTS.values()){AddonContent.item(coolant.machine());AddonContent.item(coolant.item());coreMachine(coolant.machine(),Set.of("NuclearReactor","NetherStarReactor"));}
        for(var produce:PRODUCE.values()){
            AddonContent.item(produce.input());AddonContent.item(produce.output());var output=new ItemStack(ForgeRegistries.ITEMS.getValue(produce.output()),produce.amount());
            if(output.getCount()>output.getMaxStackSize())throw new IllegalArgumentException("Invalid produce output "+produce.id());
            AnimalProduce.RECIPES.add(new AnimalProduce(new ItemStack(ForgeRegistries.ITEMS.getValue(produce.input())),output,produce.entity()));
        }
        for(var assembly:ASSEMBLIES.values()){
            AddonContent.item(assembly.machine());AddonContent.item(assembly.head());AddonContent.item(assembly.body());
            coreMachine(assembly.machine(),Set.of("IronGolemAssembler","WitherAssembler"));
            if(assembly.heads()>2*new ItemStack(ForgeRegistries.ITEMS.getValue(assembly.head())).getMaxStackSize()||assembly.bodies()>2*new ItemStack(ForgeRegistries.ITEMS.getValue(assembly.body())).getMaxStackSize())throw new IllegalArgumentException("Assembly input exceeds two slots "+assembly.id());
            if(!ForgeRegistries.ENTITY_TYPES.containsKey(assembly.entity()))throw new IllegalArgumentException("Unknown assembly entity "+assembly.entity());
        }
        for(var structure:STRUCTURES.values()){
            if(!ForgeRegistries.BLOCKS.containsKey(structure.trigger()))throw new IllegalArgumentException("Unknown structure trigger "+structure.trigger());
            var patterns=structure.patterns().stream().map(s->Objects.requireNonNull(s.get(),"Pattern "+structure.id())).toList();
            for(var pattern:patterns)if(pattern.getWidth()<1||pattern.getWidth()>32||pattern.getHeight()<1||pattern.getHeight()>32||pattern.getDepth()<1||pattern.getDepth()>32)throw new IllegalArgumentException("Structure dimensions must be 1..32: "+structure.id());
            PATTERNS.put(structure.id(),patterns);
        }
        AUTO.keySet().forEach(id->{AddonContent.item(id);coreMachine(id,Set.of("EnhancedAutoCrafter","ArmorAutoCrafter"));});
    }
    private static void coreMachine(ResourceLocation id,Set<String> kinds){if(id.getNamespace().equals(SlimefunForge.ID)){var entry=Catalog.entry(id.getPath());if(entry==null||!kinds.contains(entry.implementation()))throw new IllegalArgumentException("Unsupported core machine "+id);}}
    static ResourceLocation key(String id){return id.contains(":")?new ResourceLocation(id):SlimefunForge.key(id);}
    static Processor processor(BlockEntity owner,java.util.function.Supplier<Operation> current,Runnable end,Runnable cancel){return new Processor(){
        @Override public Operation operation(){MachineApi.check(owner);return current.get();}
        @Override public void end(){MachineApi.check(owner);end.run();}
        @Override public void cancel(){MachineApi.check(owner);cancel.run();}
    };}
    static String station(ResourceLocation id){return id.getNamespace().equals(SlimefunForge.ID)?id.getPath():id.toString();}
    private static void check(ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("Machine service requires its server thread");}
    public static List<Recipe> recipes(ServerLevel level,ResourceLocation station){
        check(level);return MachineRecipe.forStation(level,station(station)).stream().map(r->new Recipe(r.getId(),station,r.inputs,r.output,r.shaped,r.ticks)).toList();
    }
    public static List<ItemStack> consume(BlockEntity owner,Container inventory,Recipe expected){
        MachineApi.check(owner);var level=(ServerLevel)owner.getLevel();
        if(owner.isRemoved()||!level.hasChunkAt(owner.getBlockPos())||level.getBlockEntity(owner.getBlockPos())!=owner||!CONSUMING.add(owner))return List.of();
        try {
            var recipe=MachineRecipe.forStation(level,station(expected.station())).stream().filter(r->r.getId().equals(expected.id())).findFirst().orElse(null);
            if(recipe==null||recipe.ticks!=expected.ticks()||recipe.shaped!=expected.shaped()||!ItemStack.matches(recipe.output,expected.output())||recipe.inputs.size()!=expected.inputs().size())return List.of();
            var wanted=expected.inputs();for(int i=0;i<wanted.size();i++)if(!ItemStack.matches(recipe.inputs.get(i),wanted.get(i)))return List.of();
            var debit=recipe.debits(inventory);if(debit==null)return List.of();
            for(int i=0;i<debit.length;i++)if(debit[i]>0&&!ItemAccess.enabled(level,inventory.getItem(i)))return List.of();
            var consumed=new ArrayList<ItemStack>();for(int i=0;i<debit.length;i++)if(debit[i]>0)consumed.add(inventory.removeItem(i,debit[i]));inventory.setChanged();return consumed.stream().map(ItemStack::copy).toList();
        } finally {CONSUMING.remove(owner);}
    }
    public static Operation enchantment(BlockEntity owner,Container inventory,EnchantmentWork work,int speed){
        MachineApi.check(owner);Objects.requireNonNull(inventory);Objects.requireNonNull(work);if(inventory.getContainerSize()<2||speed<1||speed>64)throw new IllegalArgumentException("Enchantment inputs/speed out of range");
        if(!CONSUMING.add(owner))return null;
        try{
            String profile=switch(work){case ENCHANT->"auto_enchanter";case DISENCHANT->"auto_disenchanter";case BIND->"book_binder";};
            var plan=Enchanting.find(owner,inventory,profile,speed);if(plan==null)return null;
            var outputs=plan.second().isEmpty()?List.of(plan.result()):List.of(plan.result(),plan.second());var data=new net.minecraft.nbt.CompoundTag();data.putString("work",work.name());
            return new Operation(SlimefunForge.key("enchanting"),data,plan.cycles(),0,List.of(inventory.getItem(0).copyWithCount(1),inventory.getItem(1).copyWithCount(1)),outputs);
        }finally{CONSUMING.remove(owner);}
    }
    public static Fuel fuel(ServerLevel level,ResourceLocation machine,ItemStack input){if(level!=null)check(level);return FUELS.values().stream().filter(f->f.machine().equals(machine)&&matches(level,input,f.input())).findFirst().orElse(null);}
    static List<Item> fuels(ResourceLocation machine){return FUELS.values().stream().filter(f->f.machine().equals(machine)).map(f->ForgeRegistries.ITEMS.getValue(f.input())).toList();}
    static ItemStack fuelResult(ResourceLocation machine,ItemStack input){var fuel=fuel(null,machine,input);return fuel==null||fuel.result()==null?ItemStack.EMPTY:new ItemStack(ForgeRegistries.ITEMS.getValue(fuel.result()));}
    public static boolean coolant(ServerLevel level,ResourceLocation machine,ItemStack stack){if(level!=null)check(level);return COOLANTS.values().stream().anyMatch(c->c.machine().equals(machine)&&matches(level,stack,c.item()));}
    private static boolean matches(ServerLevel level,ItemStack stack,ResourceLocation id){return !stack.isEmpty()&&ItemAccess.enabled(level,stack)&&MachineRecipe.same(stack,new ItemStack(ForgeRegistries.ITEMS.getValue(id)));}
    static boolean autoCraft(ResourceLocation machine,String station){return AUTO.getOrDefault(machine,Set.of()).contains(key(station));}
    static List<Assembly> assemblies(ResourceLocation machine){return ASSEMBLIES.values().stream().filter(a->a.machine().equals(machine)).toList();}
    public static void interact(PlayerInteractEvent.RightClickBlock event){
        if(event.getHand()!=InteractionHand.MAIN_HAND||event.getUseBlock()==Event.Result.DENY||!(event.getEntity() instanceof ServerPlayer player)||!player.isAlive()||player.isSpectator()||INTERACTING.contains(player.getUUID()))return;
        var level=player.serverLevel();var pos=event.getPos();var state=level.getBlockState(pos);var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for(var structure:STRUCTURES.values())if(structure.trigger().equals(id))for(var pattern:PATTERNS.getOrDefault(structure.id(),List.of()))for(var forward:Direction.Plane.HORIZONTAL){
            var match=pattern.matches(level,pos,forward,Direction.UP);if(match==null)continue;
            event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
            if(!level.mayInteract(player,pos)||!player.mayBuild()||!ResearchData.canUse(player,new ItemStack(state.getBlock())))return;
            INTERACTING.add(player.getUUID());
            try {
                var interaction=new MultiBlockInteractEvent(player,structure.id(),pos,pos,event.getFace());
                if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(interaction)||!player.isAlive()||player.isSpectator()||!player.mayBuild()||player.serverLevel()!=level||player.distanceToSqr(pos.getCenter())>64||!level.hasChunkAt(pos)||!level.mayInteract(player,pos)||!ResearchData.canUse(player,new ItemStack(state.getBlock())))return;
                var current=pattern.matches(level,pos,forward,Direction.UP);if(current==null)return;
                event.setCancellationResult(Objects.requireNonNull(structure.interact().apply(player,current)));return;
            } finally {INTERACTING.remove(player.getUUID());}
        }
    }
    private AddonMachines(){}
}
