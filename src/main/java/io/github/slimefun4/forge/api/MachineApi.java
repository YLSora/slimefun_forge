package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockPattern;

/** Native machine extension contracts. Register definitions in the addon constructor, on both sides. */
public final class MachineApi {
    /** Immutable operation snapshot. Type and data describe arbitrary addon work, not a core class name. */
    public record Operation(ResourceLocation type,CompoundTag data,int total,int progress,List<ItemStack> inputs,List<ItemStack> outputs){
        public Operation {
            Objects.requireNonNull(type);data=Objects.requireNonNull(data).copy();MachineRules.advance(progress,0,total);
            inputs=stacks(inputs);outputs=stacks(outputs);
        }
        @Override public CompoundTag data(){return data.copy();}
        @Override public List<ItemStack> inputs(){return stacks(inputs);}
        @Override public List<ItemStack> outputs(){return stacks(outputs);}
        public int remaining(){return total-progress;}
        public boolean finished(){return progress==total;}
        public int bar(int width){return MachineRules.bar(progress,total,width);}
    }
    /** end clears first and notifies only if complete; cancel clears without notification or implicit refund. */
    public interface Processor { Operation operation();void end();void cancel(); }
    public interface ProcessHolder { Processor processor(); }
    /** A native BE may use the core access port three blocks above; the reactor pulls from its one inventory. */
    public interface ReactorAccess {boolean isReactor();boolean acceptsCoolant(ItemStack stack);void wake();}
    public static Container reactorPort(BlockEntity owner){check(owner);var level=(ServerLevel)owner.getLevel();var pos=owner.getBlockPos().above(3);return level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof ReactorPortEntity port?port.inventory:null;}
    public enum EnchantmentWork {ENCHANT,DISENCHANT,BIND}
    /** Plans against live inputs 0/1, including synchronous cancelable events; does not reserve or charge. */
    public static Operation enchantment(BlockEntity owner,Container inventory,EnchantmentWork work,int speed){return AddonMachines.enchantment(owner,inventory,work,speed);}
    /** A single BE owns this processor and its save tag; it schedules work with native ticks, never a static queue. */
    public static final class PersistentProcessor implements Processor {
        private final BlockEntity owner;
        private Operation operation;
        public PersistentProcessor(BlockEntity owner){this.owner=Objects.requireNonNull(owner);}
        @Override public Operation operation(){check(owner);return operation;}
        public boolean start(Operation value){check(owner);Objects.requireNonNull(value);if(operation!=null)return false;operation=value;owner.setChanged();return true;}
        public boolean advance(int amount){
            check(owner);if(amount<0)throw new IllegalArgumentException("Negative machine progress");if(operation==null)return false;
            var old=operation;int progress=MachineRules.advance(old.progress(),amount,old.total());if(progress==old.progress())return false;
            operation=new Operation(old.type(),old.data(),old.total(),progress,old.inputs(),old.outputs());owner.setChanged();return true;
        }
        @Override public void end(){
            check(owner);var completed=operation;operation=null;owner.setChanged();
            if(completed!=null&&completed.finished())net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new MachineOperationFinishEvent((ServerLevel)owner.getLevel(),owner,this,completed));
        }
        @Override public void cancel(){check(owner);operation=null;owner.setChanged();}
        /** Pure NBT persistence is also valid before the BE has a level. Unknown types remain the owner's responsibility. */
        public CompoundTag save(){
            var tag=new CompoundTag();if(operation==null)return tag;
            tag.putString("type",operation.type().toString());tag.put("data",operation.data());tag.putInt("total",operation.total());tag.putInt("progress",operation.progress());
            tag.put("inputs",write(operation.inputs()));tag.put("outputs",write(operation.outputs()));return tag;
        }
        public void load(CompoundTag tag){
            if(tag.isEmpty()){operation=null;return;}
            var type=ResourceLocation.tryParse(tag.getString("type"));
            if(type==null||!tag.contains("data",Tag.TAG_COMPOUND)||!tag.contains("total",Tag.TAG_INT)||!tag.contains("progress",Tag.TAG_INT)||!tag.contains("inputs",Tag.TAG_LIST)||!tag.contains("outputs",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid operation at "+owner.getBlockPos());
            var candidate=new Operation(type,tag.getCompound("data"),tag.getInt("total"),tag.getInt("progress"),read(tag.get("inputs")),read(tag.get("outputs")));operation=candidate;
        }
    }
    private static List<ItemStack> stacks(List<ItemStack> values){
        Objects.requireNonNull(values);if(values.size()>64)throw new IllegalArgumentException("Too many operation stacks");
        return values.stream().map(stack->{Objects.requireNonNull(stack);if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize())throw new IllegalArgumentException("Invalid operation stack");return stack.copy();}).toList();
    }
    private static ListTag write(List<ItemStack> stacks){var tags=new ListTag();stacks.forEach(stack->tags.add(stack.save(new CompoundTag())));return tags;}
    private static List<ItemStack> read(Tag value){
        if(!(value instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid operation stacks");
        return list.stream().map(tag->ItemStack.of((CompoundTag)tag)).toList();
    }
    /** Owning server-thread check; persistence callbacks alone do not need a live world. */
    public static void check(BlockEntity owner){if(!(owner.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread()||owner.isRemoved()||!level.hasChunkAt(owner.getBlockPos())||level.getBlockEntity(owner.getBlockPos())!=owner)throw new IllegalStateException("Machine operation requires its live owner and server thread");}
    /** Returns null for unloaded positions and native blocks without a processor; never loads a chunk. */
    public static Processor processor(ServerLevel level,BlockPos pos){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Machine query requires its server thread");
        if(!level.hasChunkAt(pos))return null;var entity=level.getBlockEntity(pos);if(entity==null)return null;
        return entity instanceof ProcessHolder holder?holder.processor():entity.getCapability(IndustrialMiner.CAPABILITY).map(IndustrialMiner::processor).orElse(null);
    }
    public record Recipe(ResourceLocation id,ResourceLocation station,List<ItemStack> inputs,ItemStack output,boolean shaped,int ticks){
        public Recipe { inputs=inputs.stream().map(ItemStack::copy).toList();output=output.copy(); }
        @Override public List<ItemStack> inputs(){return inputs.stream().map(ItemStack::copy).toList();}
        @Override public ItemStack output(){return output.copy();}
    }
    /** Queries the current native RecipeManager, including core derived stations, without a parallel recipe loader. */
    public static List<Recipe> recipes(ServerLevel level,ResourceLocation station){return AddonMachines.recipes(level,station);}
    /** A complete input reservation. Rechecks the recipe; an empty result consumes nothing. Check output space first. */
    public static List<ItemStack> consume(BlockEntity owner,Container inventory,Recipe recipe){return AddonMachines.consume(owner,inventory,recipe);}
    public record Fuel(ResourceLocation id,ResourceLocation machine,ResourceLocation input,int cycles,ResourceLocation result){
        public Fuel { Objects.requireNonNull(id);Objects.requireNonNull(machine);Objects.requireNonNull(input);MachineRules.positive(cycles); }
    }
    public record Coolant(ResourceLocation id,ResourceLocation machine,ResourceLocation item){public Coolant{Objects.requireNonNull(id);Objects.requireNonNull(machine);Objects.requireNonNull(item);}}
    public record Produce(ResourceLocation id,ResourceLocation input,ResourceLocation output,int amount,Predicate<LivingEntity> entity){
        public Produce {Objects.requireNonNull(id);Objects.requireNonNull(input);Objects.requireNonNull(output);Objects.requireNonNull(entity);MachineRules.positive(amount); }
    }
    public record Assembly(ResourceLocation id,ResourceLocation machine,ResourceLocation entity,ResourceLocation head,int heads,ResourceLocation body,int bodies){
        public Assembly {Objects.requireNonNull(id);Objects.requireNonNull(machine);Objects.requireNonNull(entity);Objects.requireNonNull(head);Objects.requireNonNull(body);MachineRules.positive(heads);MachineRules.positive(bodies);if(heads>128||bodies>128)throw new IllegalArgumentException("Assembly input exceeds two slots"); }
    }
    public record Multiblock(ResourceLocation id,ResourceLocation trigger,List<Supplier<BlockPattern>> patterns,BiFunction<ServerPlayer,BlockPattern.BlockPatternMatch,net.minecraft.world.InteractionResult> interact){
        public Multiblock { Objects.requireNonNull(id);Objects.requireNonNull(trigger);patterns=List.copyOf(patterns);if(patterns.isEmpty())throw new IllegalArgumentException("Empty structure");Objects.requireNonNull(interact); }
    }
    /** Native growth items can opt into existing crop/tree accelerators without pretending to be core fertilizer. */
    public interface Fertilizer { boolean accepts(ItemStack stack,BlockState target); }
    public static void registerFuel(Fuel fuel){AddonMachines.register(fuel);}
    public static void registerCoolant(Coolant coolant){AddonMachines.register(coolant);}
    public static void registerProduce(Produce produce){AddonMachines.register(produce);}
    public static void registerAssembly(Assembly assembly){AddonMachines.register(assembly);}
    public static void registerMultiblock(Multiblock structure){AddonMachines.register(structure);}
    /** Adds a native machine-recipe station to one existing automatic crafter. */
    public static void registerAutoCraft(ResourceLocation machine,ResourceLocation station){AddonMachines.autoCraft(machine,station);}
    public static Fuel fuel(ServerLevel level,ResourceLocation machine,ItemStack input){return AddonMachines.fuel(level,machine,input);}
    public static boolean coolant(ServerLevel level,ResourceLocation machine,ItemStack input){return AddonMachines.coolant(level,machine,input);}
    private MachineApi(){}
}
