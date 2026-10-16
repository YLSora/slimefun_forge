package io.github.slimefun4.addons.example;

import io.github.slimefun4.forge.api.*;
import io.github.slimefun4.forge.AndroidFarmEvent;
import io.github.slimefun4.forge.AndroidMineEvent;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.registries.*;
import static io.github.slimefun4.addons.example.ExampleAddon.key;

/** Native BE, shared script editor/library and real protected harvesting; no core robot subclass. */
final class NativeAndroids {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ExampleAddon.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ExampleAddon.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ExampleAddon.ID);
    static final RegistryObject<Block> ROBOT=BLOCKS.register("field_robot",RobotBlock::new);
    static final RegistryObject<Item> ITEM=ITEMS.register("field_robot",()->new BlockItem(ROBOT.get(),new Item.Properties()));
    private static final RegistryObject<BlockEntityType<RobotEntity>> ENTITY=ENTITIES.register("field_robot",()->BlockEntityType.Builder.of(RobotEntity::new,ROBOT.get()).build(null));
    static void register(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);SlimefunAddonApi.registerEntry(new SlimefunAddonApi.Entry(ITEM.getId(),key("native")));
        var actions=new LinkedHashSet<ResourceLocation>();for(String action:List.of("wait","go_forward","go_up","go_down","turn_left","turn_right","dig_up","dig_forward","dig_down","move_and_dig_up","move_and_dig_forward","move_and_dig_down","chop_tree","catch_fish","farm_forward","farm_down","farm_exotic_forward","farm_exotic_down","attack_mobs_animals","attack_mobs","attack_animals","attack_animals_adult","interface_items","interface_fuel"))actions.add(new ResourceLocation("slimefun",action));
        actions.add(key("harvest-more"));actions.add(key("external-withdraw"));AndroidApi.register(new AndroidApi.Type(key("field_robot"),1,actions));
        AndroidApi.register(new AndroidApi.Action(key("harvest-more"),Component.translatable("action.sf_example.harvest"),()->new ItemStack(Items.IRON_HOE),robot->{var pos=robot.getBlockPos().relative(robot.facing());if(robot.getLevel().hasChunkAt(pos)&&robot.getLevel().getBlockState(pos).getBlock().getStateDefinition().getProperty("age")!=null)AndroidApi.farm(robot,pos,true);else AndroidApi.dig(robot,pos);return true;}));
        AndroidApi.register(new AndroidApi.Action(key("external-withdraw"),Component.translatable("action.sf_example.withdraw"),()->new ItemStack(Items.HOPPER),NativeAndroids::withdraw));
        AndroidApi.register(new AndroidApi.Fuel(key("iron_cell"),Set.of(key("field_robot")),stack->ItemApi.matches(stack,new ItemStack(ExampleAddon.COMPRESSED_IRON.get()),ItemApi.Match.STACK),40,()->new ItemStack(ExampleAddon.IRON_TOKEN.get())));
        MinecraftForge.EVENT_BUS.addListener((AndroidFarmEvent event)->{if(event.getAndroid().type().equals(key("field_robot"))&&event.getState().is(Blocks.MELON))event.setDrop(new ItemStack(Items.MELON_SLICE,3));});
        MinecraftForge.EVENT_BUS.addListener((AndroidMineEvent event)->{if(event.getAndroid().type().equals(key("field_robot"))&&event.getState().is(Blocks.DIAMOND_BLOCK))event.setCanceled(true);});
    }
    private static boolean withdraw(AndroidApi.Robot robot){
        var pos=robot.getBlockPos().relative(robot.facing());var before=robot.saveWithoutMetadata();var preview=ItemApi.interact(robot,pos,true);if(!preview.success()||!before.equals(robot.saveWithoutMetadata()))return true;
        var planned=new SimpleContainer(6);for(int i=0;i<6;i++)planned.setItem(i,robot.inventory().getItem(i).copy());for(var output:preview.outputs())if(!ItemHandlerHelper.insertItemStacked(new InvWrapper(planned),output,false).isEmpty())return true;
        var result=ItemApi.interact(robot,pos,false);if(!result.success())return true;
        for(var output:result.outputs()){
            if(!AndroidApi.valid(robot)){Block.popResource(robot.getLevel(),robot.getBlockPos(),output);continue;}
            for(int i=0;i<6&&!output.isEmpty();i++){var current=robot.inventory().getItem(i);if(!current.isEmpty()&&!ItemApi.matches(current,output,ItemApi.Match.STACK))continue;int accepted=Math.min(output.getCount(),Math.min(64,output.getMaxStackSize())-current.getCount());if(accepted>0){robot.inventory().setItem(i,output.copyWithCount(current.getCount()+accepted));output.shrink(accepted);}}
            if(!output.isEmpty())Block.popResource(robot.getLevel(),robot.getBlockPos(),output);
        }return true;
    }
    private static final class RobotBlock extends BaseEntityBlock {
        RobotBlock(){super(Properties.copy(Blocks.IRON_BLOCK));registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.HORIZONTAL_FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,context.getHorizontalDirection().getOpposite());}
        @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new RobotEntity(pos,state);}
        @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(placer instanceof ServerPlayer player&&level.getBlockEntity(pos) instanceof RobotEntity robot)robot.controller.owner(player.getUUID());}
        @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(level.getBlockEntity(pos) instanceof RobotEntity robot)robot.controller.tick();}
        @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(level.isClientSide)return InteractionResult.SUCCESS;if(!(player instanceof ServerPlayer server)||!(level.getBlockEntity(pos) instanceof RobotEntity robot)||!robot.access(server))return InteractionResult.FAIL;server.openMenu(robot);return InteractionResult.CONSUME;}
        @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof RobotEntity robot&&!robot.moving())Containers.dropContents(level,pos,robot.inventory);super.onRemove(state,level,pos,next,moving);}
    }
    private static final class RobotEntity extends BlockEntity implements AndroidApi.Robot,MenuProvider {
        private final SimpleContainer inventory=new SimpleContainer(7){@Override public void setChanged(){super.setChanged();RobotEntity.this.setChanged();if(controller!=null)wake();}};
        private final AndroidApi.Controller controller=new AndroidApi.Controller(this,key("field_robot"));
        RobotEntity(BlockPos pos,BlockState state){super(ENTITY.get(),pos,state);}
        @Override public BlockEntity machine(){return this;}@Override public AndroidApi.Controller controller(){return controller;}@Override public Container inventory(){return inventory;}
        boolean access(ServerPlayer player){return AndroidApi.valid(this)&&player.isAlive()&&!player.isSpectator()&&player.level()==level&&player.distanceToSqr(worldPosition.getCenter())<=64&&AndroidApi.mayAccess(player,this)&&SlimefunAddonApi.canUse(player,new ItemStack(ITEM.get()));}
        @Override public void onLoad(){super.onLoad();wake();}
        @Override public Component getDisplayName(){return Component.translatable("block.sf_example.field_robot");}
        @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return access((ServerPlayer)player)?AndroidApi.menu(id,inv,this):null;}
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.put("controller",controller.save());var items=NonNullList.withSize(7,ItemStack.EMPTY);for(int i=0;i<7;i++)items.set(i,inventory.getItem(i));ContainerHelper.saveAllItems(tag,items);}
        @Override public void load(CompoundTag tag){if(!tag.contains("controller",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing native robot controller");super.load(tag);controller.load(tag.getCompound("controller"));var items=NonNullList.withSize(7,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items);for(int i=0;i<7;i++)inventory.setItem(i,items.get(i));}
    }
    private NativeAndroids(){}
}
