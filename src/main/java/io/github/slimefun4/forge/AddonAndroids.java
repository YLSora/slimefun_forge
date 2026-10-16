package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.*;
import io.github.slimefun4.forge.api.AndroidApi.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.*;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.server.permission.PermissionAPI;

/** Shared native consumers for core and addon robot BEs. */
public final class AddonAndroids {
    private static final Map<ResourceLocation,Type> TYPES=new LinkedHashMap<>();
    private static final Map<ResourceLocation,Action> ACTIONS=new LinkedHashMap<>();
    private static final Map<ResourceLocation,Fuel> FUELS=new LinkedHashMap<>();
    static {
        for(var instruction:AndroidEntity.Instruction.values()){
            var id=id(instruction);ACTIONS.put(id,new Action(id,Component.translatable("menu.slimefun.android.instruction."+instruction.name()),()->new ItemStack(Items.PAPER),robot->execute(robot,instruction)));
        }
        for(var family:AndroidBlock.Type.values())for(int tier=1;tier<=3;tier++){
            var actions=new LinkedHashSet<ResourceLocation>();for(var instruction:AndroidEntity.Instruction.values())if(supports(family,instruction))actions.add(id(instruction));
            var id=coreType(family,tier);TYPES.put(id,new Type(id,tier,actions));
        }
    }
    static ResourceLocation id(AndroidEntity.Instruction instruction){return new ResourceLocation("slimefun",instruction.name().toLowerCase(Locale.ROOT));}
    static ResourceLocation coreType(AndroidBlock.Type family,int tier){return new ResourceLocation("slimefun","android_"+family.name().toLowerCase(Locale.ROOT)+"_"+tier);}
    private static boolean supports(AndroidBlock.Type type,AndroidEntity.Instruction instruction){return switch(instruction){case GO_FORWARD,GO_UP,GO_DOWN->type!=AndroidBlock.Type.FIGHTER;default->instruction.required==null||instruction.required==type;};}
    private static <T> void put(Map<ResourceLocation,T> map,ResourceLocation id,T value){if(map.putIfAbsent(id,value)!=null)throw new IllegalArgumentException("Duplicate robot ID "+id);}
    public static synchronized void register(Type value){AddonContent.open();AddonContent.owned(value.id());put(TYPES,value.id(),value);}
    public static synchronized void register(Action value){AddonContent.open();AddonContent.owned(value.id());if(Set.of("start","repeat").contains(value.id().getPath()))throw new IllegalArgumentException("Reserved robot boundary action");put(ACTIONS,value.id(),value);}
    public static synchronized void register(Fuel value){AddonContent.open();AddonContent.owned(value.id());put(FUELS,value.id(),value);}
    public static Type type(ResourceLocation id){return Objects.requireNonNull(TYPES.get(id),"Unknown robot type "+id);}
    public static Action action(ResourceLocation id){return Objects.requireNonNull(ACTIONS.get(id),"Unknown robot action "+id);}
    static void freeze(){
        for(var type:TYPES.values())for(var id:type.actions()){var action=action(id);if(action.icon().get().isEmpty())throw new IllegalArgumentException("Missing robot action icon "+id);}
        addFuel("coal_block",1,Items.COAL_BLOCK,160);addFuel("blaze_rod",1,Items.BLAZE_ROD,90);addFuel("kelp",1,Items.DRIED_KELP_BLOCK,140);addFuel("coal",1,Items.COAL,16);addFuel("charcoal",1,Items.CHARCOAL,16);addFuel("bamboo",1,Items.BAMBOO,2);
        addFuel("lava",2,Items.LAVA_BUCKET,200);addFuel("oil",2,SlimefunForge.item("bucket_of_oil"),400);addFuel("fuel",2,SlimefunForge.item("bucket_of_fuel"),1000);
        addFuel("uranium",3,SlimefunForge.item("uranium"),5000);addFuel("neptunium",3,SlimefunForge.item("neptunium"),2400);addFuel("boosted_uranium",3,SlimefunForge.item("boosted_uranium"),6000);
        var basic=coreTypes(1);for(var name:List.of("logs","planks")){var id=new ResourceLocation("slimefun",name);FUELS.put(id,new Fuel(id,basic,stack->stack.is(name.equals("logs")?ItemTags.LOGS:ItemTags.PLANKS)&&MachineRecipe.same(stack,new ItemStack(stack.getItem())),name.equals("logs")?4:2,()->ItemStack.EMPTY));}
        for(var fuel:FUELS.values()){fuel.remaining();for(var id:fuel.types())type(id);}
    }
    private static Set<ResourceLocation> coreTypes(int tier){return TYPES.values().stream().filter(t->t.id().getNamespace().equals("slimefun")&&t.tier()==tier).map(Type::id).collect(java.util.stream.Collectors.toSet());}
    private static void addFuel(String name,int tier,Item item,int cycles){var id=new ResourceLocation("slimefun",name);FUELS.put(id,new Fuel(id,coreTypes(tier),stack->MachineRecipe.same(stack,new ItemStack(item)),cycles,()->tier==2?new ItemStack(Items.BUCKET):ItemStack.EMPTY));}
    public static int maximumFuel(ResourceLocation type){return FUELS.values().stream().filter(f->f.types().contains(type)).mapToInt(Fuel::cycles).max().orElse(0);}
    public static int fuelCycles(ResourceLocation type,ItemStack stack){if(stack.isEmpty())return 0;for(var fuel:FUELS.values())if(fuel.types().contains(type)&&fuel.input().test(stack.copy()))return fuel.cycles();return 0;}
    public static boolean authorized(Robot robot){return AndroidApi.valid(robot)&&ItemAccess.automation((ServerLevel)robot.getLevel(),robot.owner(),new ItemStack(robot.getBlockState().getBlock()));}
    public static boolean mayAccess(ServerPlayer player,Robot robot){return player.getUUID().equals(robot.owner())||PermissionAPI.getPermission(player,AndroidBlock.BYPASS);}
    public static ServerPlayer actor(Robot robot){
        var level=(ServerLevel)robot.getLevel();var actor=FakePlayerFactory.get(level,new GameProfile(Objects.requireNonNull(robot.owner()),"[Slimefun Android]"));actor.setGameMode(GameType.SURVIVAL);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);var pos=robot.getBlockPos();actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);return actor;
    }
    public static void refuel(Robot robot){
        var source=robot.inventory().getItem(6);if(source.isEmpty())return;var before=robot.saveWithoutMetadata();
        for(var fuel:FUELS.values())if(fuel.types().contains(robot.type())&&fuel.input().test(source.copy())){
            var planned=outputs(robot);var remainder=fuel.remaining();if(!remainder.isEmpty()&&!ItemHandlerHelper.insertItemStacked(new InvWrapper(planned),remainder,false).isEmpty())return;
            if(!AndroidApi.valid(robot)||!before.equals(robot.saveWithoutMetadata())||!authorized(robot))return;
            robot.inventory().removeItem(6,1);robot.controller().fueled(fuel.cycles());for(int i=0;i<6;i++)robot.inventory().setItem(i,planned.getItem(i));return;
        }
    }
    private static SimpleContainer outputs(Robot robot){var copy=new SimpleContainer(6);for(int i=0;i<6;i++)copy.setItem(i,robot.inventory().getItem(i).copy());return copy;}
    public static boolean dig(Robot robot,BlockPos target){return AndroidTasks.dig(robot,target);}
    public static void farm(Robot robot,BlockPos target,boolean advanced){AndroidTasks.farm(robot,target,advanced);}
    private static boolean execute(Robot robot,AndroidEntity.Instruction instruction){
        var level=(ServerLevel)robot.getLevel();var face=robot.facing();var origin=robot.getBlockPos();
        switch(instruction){
            case TURN_LEFT,TURN_RIGHT->{var property=net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;if(robot.getBlockState().hasProperty(property))level.setBlock(origin,robot.getBlockState().setValue(property,instruction==AndroidEntity.Instruction.TURN_LEFT?face.getCounterClockWise():face.getClockWise()),3);}
            case GO_FORWARD,GO_UP,GO_DOWN->move(robot,origin.relative(instruction==AndroidEntity.Instruction.GO_UP?Direction.UP:instruction==AndroidEntity.Instruction.GO_DOWN?Direction.DOWN:face));
            case INTERFACE_ITEMS,INTERFACE_FUEL->transfer(robot,face,instruction==AndroidEntity.Instruction.INTERFACE_FUEL);
            case DIG_UP,DIG_FORWARD,DIG_DOWN,MOVE_AND_DIG_UP,MOVE_AND_DIG_FORWARD,MOVE_AND_DIG_DOWN->{var direction=switch(instruction){case DIG_UP,MOVE_AND_DIG_UP->Direction.UP;case DIG_DOWN,MOVE_AND_DIG_DOWN->Direction.DOWN;default->face;};var target=origin.relative(direction);if(dig(robot,target)&&instruction.name().startsWith("MOVE_AND"))move(robot,target);}
            case CHOP_TREE->{return AndroidTasks.chop(robot,origin.relative(face));}
            case CATCH_FISH->AndroidTasks.fish(robot);
            case ATTACK_MOBS_ANIMALS,ATTACK_MOBS,ATTACK_ANIMALS,ATTACK_ANIMALS_ADULT->AndroidCombat.attack(robot,instruction);
            case FARM_FORWARD,FARM_DOWN,FARM_EXOTIC_FORWARD,FARM_EXOTIC_DOWN->farm(robot,origin.relative(instruction==AndroidEntity.Instruction.FARM_DOWN||instruction==AndroidEntity.Instruction.FARM_EXOTIC_DOWN?Direction.DOWN:face),instruction==AndroidEntity.Instruction.FARM_EXOTIC_FORWARD||instruction==AndroidEntity.Instruction.FARM_EXOTIC_DOWN);
            default->{}
        }return true;
    }
    private static void transfer(Robot robot,Direction face,boolean refuel){
        var level=(ServerLevel)robot.getLevel();var pos=robot.getBlockPos().relative(face);if(!level.hasChunkAt(pos)||!(level.getBlockState(pos).getBlock() instanceof AndroidInterfaceBlock block)||block.fuel!=refuel||!(level.getBlockEntity(pos) instanceof AndroidInterfaceEntity target)||!ItemAccess.automation(level,robot.owner(),new ItemStack(block)))return;
        var actor=actor(robot);var event=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(actor,InteractionHand.MAIN_HAND,pos,new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),face,pos,false));var before=robot.saveWithoutMetadata();var data=target.saveWithoutMetadata();
        if(!level.mayInteract(actor,pos)||MinecraftForge.EVENT_BUS.post(event)||event.getUseBlock()==net.minecraftforge.eventbus.api.Event.Result.DENY||!AndroidApi.valid(robot)||!before.equals(robot.saveWithoutMetadata())||!level.hasChunkAt(pos)||level.getBlockEntity(pos)!=target||!data.equals(target.saveWithoutMetadata())||!authorized(robot)||!ItemAccess.automation(level,robot.owner(),new ItemStack(block)))return;
        if(refuel){for(int i=0;i<9;i++){var source=target.getItem(i);var current=robot.inventory().getItem(6);if(source.isEmpty()||!current.isEmpty()&&!AddonItems.matches(source,current,ItemApi.Match.STACK))continue;int accepted=Math.min(source.getCount(),Math.min(source.getMaxStackSize(),64)-current.getCount());if(accepted>0){var next=source.copyWithCount(current.getCount()+accepted);target.removeItem(i,accepted);robot.inventory().setItem(6,next);}}}
        else{var handler=new InvWrapper(target);for(int i=0;i<6;i++){var source=robot.inventory().getItem(i);if(source.isEmpty())continue;var remaining=ItemHandlerHelper.insertItemStacked(handler,source.copy(),false);robot.inventory().removeItem(i,source.getCount()-remaining.getCount());}}
    }
    public static boolean move(Robot robot,BlockPos target){
        if(!authorized(robot))return false;var level=(ServerLevel)robot.getLevel();var source=robot.getBlockPos();var state=robot.getBlockState();if(source.distManhattan(target)!=1||level.isOutsideBuildHeight(target)||!level.getWorldBorder().isWithinBounds(target)||!level.hasChunkAt(target)||!level.isEmptyBlock(target))return false;
        var data=robot.saveWithoutMetadata();var actor=actor(robot);if(!level.mayInteract(actor,source)||!level.mayInteract(actor,target)||MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,source,state,actor))||!AndroidApi.valid(robot)||!data.equals(robot.saveWithoutMetadata())||!level.isEmptyBlock(target)||!authorized(robot))return false;
        var snapshot=BlockSnapshot.create(level.dimension(),level,target);if(!level.setBlock(target,state,3))return false;var placed=level.getBlockEntity(target);
        boolean denied=ForgeEventFactory.onBlockPlace(actor,snapshot,Direction.UP);
        if(denied||!(placed instanceof Robot next)||placed.getType()!=robot.machine().getType()||level.getBlockEntity(target)!=placed||!level.getBlockState(target).equals(state)||!AndroidApi.valid(robot)||!data.equals(robot.saveWithoutMetadata())||!authorized(robot)){
            if(level.getBlockEntity(target)==placed){if(placed instanceof Robot next)AndroidApi.moving(next,true);level.removeBlock(target,false);}return false;
        }
        placed.load(data);AndroidApi.moving(robot,true);
        if(level.removeBlock(source,false)){robot.inventory().clearContent();placed.setChanged();next.wake();return true;}
        AndroidApi.moving(next,true);level.removeBlock(target,false);AndroidApi.moving(robot,false);return false;
    }
    private AddonAndroids(){}
}
