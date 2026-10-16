package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Registered programs contain only frozen action IDs, never executable user text. */
public final class AndroidApi {
    public static final ResourceLocation START=new ResourceLocation("slimefun","start"),REPEAT=new ResourceLocation("slimefun","repeat");
    public record Type(ResourceLocation id,int tier,Set<ResourceLocation> actions){
        public Type{Objects.requireNonNull(id);if(tier<1||tier>3)throw new IllegalArgumentException("Robot tier must be 1..3");var copy=new LinkedHashSet<>(actions);copy.add(START);copy.add(REPEAT);actions=Collections.unmodifiableSet(copy);if(actions.size()>46)throw new IllegalArgumentException("Robot menu supports at most 44 actions");}
    }
    /** Return false to repeat this action next cycle. Callbacks run on the owning server thread. */
    public record Action(ResourceLocation id,Component title,Supplier<ItemStack> icon,Predicate<Robot> execute){
        public Action{Objects.requireNonNull(id);title=title.copy();Objects.requireNonNull(icon);Objects.requireNonNull(execute);}
        @Override public Component title(){return title.copy();}
    }
    public record Fuel(ResourceLocation id,Set<ResourceLocation> types,Predicate<ItemStack> input,int cycles,Supplier<ItemStack> remainder){
        public Fuel{Objects.requireNonNull(id);types=Set.copyOf(types);Objects.requireNonNull(input);Objects.requireNonNull(remainder);if(types.isEmpty()||cycles<1||cycles>1_000_000)throw new IllegalArgumentException("Invalid robot fuel");}
        public ItemStack remaining(){var stack=Objects.requireNonNull(remainder.get()).copy();if(!stack.isEmpty()&&(stack.getCount()<1||stack.getCount()>stack.getMaxStackSize()))throw new IllegalArgumentException("Invalid robot fuel remainder "+id);return stack;}
    }
    /** Native BE and its single seven-slot inventory: output 0..5, fuel 6. */
    public interface Robot {
        BlockEntity machine();Controller controller();Container inventory();
        default UUID owner(){return controller().owner();}
        default ResourceLocation type(){return controller().type();}
        default int tier(){return AndroidApi.type(type()).tier();}
        default List<ResourceLocation> script(){return controller().program();}
        default void setScript(List<ResourceLocation> code){controller().program(code);}
        default void pause(boolean paused){controller().pause(paused);}
        default List<ResourceLocation> choices(){return AndroidApi.choices(type());}
        default Level getLevel(){return machine().getLevel();}
        default BlockPos getBlockPos(){return machine().getBlockPos();}
        default BlockState getBlockState(){return machine().getBlockState();}
        default boolean isRemoved(){return machine().isRemoved();}
        default CompoundTag saveWithoutMetadata(){return machine().saveWithoutMetadata();}
        default Component getDisplayName(){return Component.translatable("menu.slimefun.android.inventory_title");}
        default Direction facing(){return getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)?getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING):Direction.NORTH;}
        /** Suppress inventory drops only while moving the real BE through AndroidApi.move. */
        default boolean moving(){return controller().moving;}
        default void wake(){if(getLevel() instanceof ServerLevel level&&valid(this)&&owner()!=null&&!controller().paused()&&(controller().fuel()>0||fuelCycles(type(),inventory().getItem(6))>0)&&!level.getBlockTicks().hasScheduledTick(getBlockPos(),getBlockState().getBlock()))level.scheduleTick(getBlockPos(),getBlockState().getBlock(),10);}
    }
    public static final class Controller {
        private final Robot robot;private final ResourceLocation type;
        private UUID owner;private List<ResourceLocation> program;private int index,fuel;private boolean paused=true,working,moving;private long revision;
        public Controller(Robot robot,ResourceLocation type){this.robot=Objects.requireNonNull(robot);this.type=Objects.requireNonNull(type);var turn=new ResourceLocation("slimefun","turn_left");program=AndroidApi.type(type).actions().contains(turn)?List.of(START,turn,REPEAT):List.of(START,REPEAT);}
        public ResourceLocation type(){return type;}public UUID owner(){return owner;}public List<ResourceLocation> program(){return program;}public int index(){return index;}public int fuel(){return fuel;}public boolean paused(){return paused;}
        private void changed(){revision++;robot.machine().setChanged();robot.wake();}
        public void owner(UUID owner){check(robot);this.owner=Objects.requireNonNull(owner);changed();}
        public void pause(boolean paused){check(robot);this.paused=paused;changed();}
        public void program(List<ResourceLocation> value){check(robot);program=validate(type,value);index=0;changed();}
        public void tick(){
            check(robot);if(working||paused||owner==null||!valid(robot)||!AddonAndroids.authorized(robot))return;working=true;
            try{
                if(fuel==0){AddonAndroids.refuel(robot);return;}
                int previous=index;index=PlayerRules.nextInstruction(index,program.size());var action=action(program.get(index));if(action.id().equals(REPEAT))index=0;
                fuel--;changed();long version=revision;boolean advance=action.execute().test(robot);
                if(!advance&&valid(robot)&&revision==version){index=previous;changed();}
            }finally{working=false;robot.wake();}
        }
        /** Internal shared fuel consumer; all capacity and identity checks precede this call. */
        public void fueled(int cycles){check(robot);if(fuel!=0||cycles<1||cycles>AddonAndroids.maximumFuel(type))throw new IllegalStateException("Invalid robot refuel");fuel=cycles;changed();}
        public CompoundTag save(){var tag=new CompoundTag();tag.putString("type",type.toString());if(owner!=null)tag.putUUID("owner",owner);tag.put("program",writeProgram(program));tag.putInt("index",index);tag.putInt("fuel",fuel);tag.putBoolean("paused",paused);return tag;}
        public void load(CompoundTag tag){
            if(!tag.getString("type").equals(type.toString())||!tag.contains("index",Tag.TAG_INT)||!tag.contains("fuel",Tag.TAG_INT)||!tag.contains("paused",Tag.TAG_BYTE))throw new IllegalArgumentException("Invalid robot state");
            var next=validate(type,readProgram(tag,"program"));int index=tag.getInt("index"),fuel=tag.getInt("fuel");PlayerRules.nextInstruction(index,next.size());if(fuel<0||fuel>AddonAndroids.maximumFuel(type))throw new IllegalArgumentException("Invalid robot fuel");
            this.program=next;this.index=index;this.fuel=fuel;owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;paused=tag.getBoolean("paused");revision++;
        }
    }
    public static void register(Type type){AddonAndroids.register(type);}public static void register(Action action){AddonAndroids.register(action);}public static void register(Fuel fuel){AddonAndroids.register(fuel);}
    public static Type type(ResourceLocation id){return AddonAndroids.type(id);}public static Action action(ResourceLocation id){return AddonAndroids.action(id);}
    public static int fuelCycles(ResourceLocation type,ItemStack stack){return AddonAndroids.fuelCycles(type,stack);}
    public static List<ResourceLocation> choices(ResourceLocation type){return type(type).actions().stream().filter(id->!id.equals(START)&&!id.equals(REPEAT)).toList();}
    public static List<ResourceLocation> validate(ResourceLocation type,List<ResourceLocation> code){PlayerRules.program(code.stream().map(ResourceLocation::toString).toList(),type(type).actions().stream().map(ResourceLocation::toString).collect(java.util.stream.Collectors.toSet()));if(!code.get(0).equals(START)||!code.get(code.size()-1).equals(REPEAT))throw new IllegalArgumentException("Robot boundaries require slimefun IDs");return List.copyOf(code);}
    public static ListTag writeProgram(List<ResourceLocation> code){var list=new ListTag();code.forEach(id->list.add(StringTag.valueOf(id.toString())));return list;}
    public static List<ResourceLocation> readProgram(CompoundTag tag,String key){if(!(tag.get(key) instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=Tag.TAG_STRING)throw new IllegalArgumentException("Invalid robot program list");var code=new ArrayList<ResourceLocation>();for(int i=0;i<list.size();i++){String id=list.getString(i);if(!id.contains(":"))throw new IllegalArgumentException("Robot action requires namespace");code.add(new ResourceLocation(id));}return List.copyOf(code);}
    public static void check(Robot robot){if(!(robot.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread())throw new IllegalStateException("Robot operation requires owning server thread");}
    public static boolean valid(Robot robot){return robot.getLevel() instanceof ServerLevel level&&!robot.isRemoved()&&level.hasChunkAt(robot.getBlockPos())&&level.getBlockEntity(robot.getBlockPos())==robot.machine()&&robot.inventory().getContainerSize()==7;}
    public static boolean mayAccess(ServerPlayer player,Robot robot){return AddonAndroids.mayAccess(player,robot);}
    public static ServerPlayer actor(Robot robot){check(robot);return AddonAndroids.actor(robot);}
    public static AbstractContainerMenu menu(int id,Inventory inventory,Robot robot){return new AndroidMenu(id,inventory,robot);}
    public static boolean dig(Robot robot,BlockPos target){check(robot);return AddonAndroids.dig(robot,target);}
    public static void farm(Robot robot,BlockPos target,boolean advanced){check(robot);AddonAndroids.farm(robot,target,advanced);}
    public static boolean move(Robot robot,BlockPos target){check(robot);return AddonAndroids.move(robot,target);}
    /** Movement service alone sets this transient on the source/destination. It is never saved. */
    public static void moving(Robot robot,boolean value){check(robot);robot.controller().moving=value;}
    private AndroidApi(){}
}
