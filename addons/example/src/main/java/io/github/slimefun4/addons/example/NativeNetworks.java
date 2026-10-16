package io.github.slimefun4.addons.example;

import io.github.slimefun4.forge.api.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import static io.github.slimefun4.addons.example.ExampleAddon.key;

/** Real grid/cargo/GPS members; FE and NBT belong to these native block entities. */
final class NativeNetworks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ExampleAddon.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ExampleAddon.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ExampleAddon.ID);
    private static final List<String> NAMES=List.of("native_regulator","native_connector","native_capacitor","native_cargo_manager","native_cargo_connector","native_cargo_input","native_cargo_output","native_transmitter");
    private static final List<RegistryObject<? extends Block>> MEMBERS=new ArrayList<>();
    static {for(int i=0;i<NAMES.size();i++){int mode=i;var block=BLOCKS.register(NAMES.get(i),()->new MemberBlock(mode));MEMBERS.add(block);ITEMS.register(NAMES.get(i),()->new BlockItem(block.get(),new Item.Properties()));}}
    private static final RegistryObject<BlockEntityType<Member>> ENTITY=ENTITIES.register("native_network",()->BlockEntityType.Builder.of(Member::new,MEMBERS.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    static void register(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);for(var item:ITEMS.getEntries())SlimefunAddonApi.registerEntry(new SlimefunAddonApi.Entry(item.getId(),key("native")));
        GeoApi.register(new GeoApi.Resource(key("iron_trace"),Component.translatable("geo.sf_example.iron_trace"),key("iron_token"),true,4,
            Map.of(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD,8),
            Map.of(net.minecraft.world.level.biome.Biomes.PLAINS,12),
            Map.of(net.minecraft.tags.TagKey.create(Registries.BIOME,key("trace_biomes")),16)));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(NativeNetworks::commands);
    }
    private static void commands(net.minecraftforge.event.RegisterCommandsEvent event){
        var root=net.minecraft.commands.Commands.literal("sf_example");
        root.then(net.minecraft.commands.Commands.literal("geo").requires(source->source.hasPermission(2)).executes(context->{var player=context.getSource().getPlayerOrException();var values=GeoApi.scan(player.serverLevel(),player.blockPosition());context.getSource().sendSuccess(()->Component.literal(values.entrySet().stream().map(e->e.getKey().id+"="+e.getValue()).collect(java.util.stream.Collectors.joining(", "))),false);return values.size();}));
        var gps=net.minecraft.commands.Commands.literal("gps").executes(context->{var player=context.getSource().getPlayerOrException();var points=GpsApi.points(player,player.getUUID());context.getSource().sendSuccess(()->Component.literal(points.toString()+"; complexity="+GpsApi.complexity(player.server,player.getUUID())+"; transmitters="+GpsApi.transmitters(player.server,player.getUUID())),false);return points.size();});
        gps.then(net.minecraft.commands.Commands.literal("add").then(net.minecraft.commands.Commands.argument("name",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(context->{var player=context.getSource().getPlayerOrException();return GpsApi.create(player,player.getUUID(),com.mojang.brigadier.arguments.StringArgumentType.getString(context,"name"),player.level().dimension(),player.position(),player.getYRot())?1:0;})));
        for(String action:List.of("go","move","remove"))gps.then(net.minecraft.commands.Commands.literal(action).then(net.minecraft.commands.Commands.argument("id",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context->{
            var player=context.getSource().getPlayerOrException();var id=com.mojang.brigadier.arguments.StringArgumentType.getString(context,"id");
            if(action.equals("remove"))return GpsApi.remove(player,player.getUUID(),id)?1:0;
            if(action.equals("move")){var point=GpsApi.points(player,player.getUUID()).stream().filter(p->p.id().equals(id)).findFirst().orElse(null);return point!=null&&GpsApi.update(player,player.getUUID(),id,point.name(),player.level().dimension(),player.position(),player.getYRot())?1:0;}
            return GpsApi.teleport(player,player.getUUID(),id,new GpsApi.Payment(){@Override public boolean canPay(){return player.totalExperience>=1;}@Override public void pay(){player.giveExperiencePoints(-1);}})?1:0;
        })));
        gps.then(net.minecraft.commands.Commands.literal("cancel").executes(context->GpsApi.cancel(context.getSource().getPlayerOrException())?1:0));
        gps.then(net.minecraft.commands.Commands.literal("owner").requires(source->source.hasPermission(2)).then(net.minecraft.commands.Commands.argument("owner",net.minecraft.commands.arguments.UuidArgument.uuid()).executes(context->{var points=GpsApi.points(context.getSource().getPlayerOrException(),net.minecraft.commands.arguments.UuidArgument.getUuid(context,"owner"));context.getSource().sendSuccess(()->Component.literal(points.toString()),false);return points.size();})));
        event.getDispatcher().register(root.then(gps));
    }
    private static final class MemberBlock extends BaseEntityBlock {
        final int mode;
        MemberBlock(int mode){super(Properties.copy(Blocks.COPPER_BLOCK));this.mode=mode;registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.HORIZONTAL_FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){var face=context.getClickedFace();return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,face.getAxis().isHorizontal()?face:context.getHorizontalDirection().getOpposite());}
        @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new Member(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ENTITY.get(),Member::tick);}
        @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack stack){if(placer!=null&&level.getBlockEntity(pos) instanceof Member member){member.owner=placer.getUUID();member.setChanged();}}
        @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;if(level.isClientSide)return InteractionResult.SUCCESS;
            if(!(player instanceof ServerPlayer server)||!(level.getBlockEntity(pos) instanceof Member member)||!member.access(server))return InteractionResult.FAIL;
            if(mode>=3&&mode<=6){if(player.isShiftKeyDown())member.channel=(member.channel+1)%16;else member.filter=player.getItemInHand(hand).copyWithCount(1);member.setChanged();}
            server.displayClientMessage(Component.translatable("message.sf_example.network",member.status,member.channel+1,member.energy,member.filter.isEmpty()?Component.literal("*"):member.filter.getHoverName()),false);return InteractionResult.CONSUME;
        }
    }
    private static final class Member extends BlockEntity implements EnergyApi.Member,CargoApi.Node,GpsApi.Transmitter {
        private UUID owner;private int energy,channel;private ItemStack filter=ItemStack.EMPTY;private Component status=Component.empty();
        private LazyOptional<IEnergyStorage> power=LazyOptional.of(Power::new);
        Member(BlockPos pos,BlockState state){super(ENTITY.get(),pos,state);}
        private int mode(){return ((MemberBlock)getBlockState().getBlock()).mode;}
        boolean access(ServerPlayer player){return player.isAlive()&&!player.isSpectator()&&player.serverLevel()==level&&player.mayBuild()&&level.mayInteract(player,worldPosition)&&player.distanceToSqr(worldPosition.getCenter())<=64&&(owner==null||owner.equals(player.getUUID()))&&SlimefunAddonApi.canUse(player,new ItemStack(getBlockState().getBlock()));}
        private void attach(){if(level instanceof ServerLevel server&&NetworkApi.live(server,this)){if(mode()<3||mode()==7)EnergyApi.register(this);else CargoApi.register(this);}}
        private void detach(){if(level instanceof ServerLevel){if(mode()<3||mode()==7)EnergyApi.unregister(this);else CargoApi.unregister(this);if(mode()==7)GpsApi.unregister(this);}}
        @Override public void onLoad(){super.onLoad();attach();}
        @Override public void setRemoved(){detach();super.setRemoved();}
        @Override public void onChunkUnloaded(){detach();super.onChunkUnloaded();}
        @Override public void invalidateCaps(){detach();super.invalidateCaps();power.invalidate();}
        @Override public void reviveCaps(){super.reviveCaps();power=LazyOptional.of(Power::new);attach();}
        @Override public EnergyApi.Role energyRole(){return mode()==0?EnergyApi.Role.REGULATOR:mode()==1?EnergyApi.Role.CONNECTOR:mode()==2?EnergyApi.Role.CAPACITOR:EnergyApi.Role.CONSUMER;}
        @Override public void networkEnergy(int amount){if(mode()!=2||amount<0||amount>32768)throw new IllegalArgumentException("Invalid capacitor buffer");energy=amount;setChanged();}
        @Override public void networkStatus(Component value){status=value.copy();}
        @Override public CargoApi.Role cargoRole(){return mode()==3?CargoApi.Role.MANAGER:mode()==4?CargoApi.Role.CONNECTOR:mode()==5?CargoApi.Role.INPUT:CargoApi.Role.OUTPUT;}
        @Override public int channel(){return channel;}
        @Override public Direction attachedFace(){return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();}
        @Override public boolean accepts(ItemStack offered){return filter.isEmpty()||CargoApi.matches(offered,filter,true);}
        @Override public boolean roundRobin(){return true;}
        @Override public void cargoStatus(Component value){status=value.copy();}
        @Override public UUID gpsOwner(){return owner;}
        @Override public ItemStack gpsIcon(){return new ItemStack(getBlockState().getBlock());}
        @Override public int signalStrength(){return Math.max(0,worldPosition.getY())*4;}
        @Override public int signalComplexity(){return signalStrength();}
        static void tick(Level level,BlockPos pos,BlockState state,Member member){
            if(level.getGameTime()%10!=0)return;int mode=member.mode();
            if(mode==0)EnergyApi.tick(member);else if(mode==3)CargoApi.tick(member);else if(mode==7){boolean paid=member.owner!=null&&member.energy>=1;if(paid){member.energy--;member.setChanged();}GpsApi.update(member,paid);}
        }
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);if(owner!=null)tag.putUUID("owner",owner);tag.putInt("energy",energy);tag.putInt("channel",channel);tag.put("filter",filter.save(new CompoundTag()));}
        @Override public void load(CompoundTag tag){super.load(tag);owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;energy=Math.max(0,Math.min(mode()==2?32768:1024,tag.getInt("energy")));channel=Math.floorMod(tag.getInt("channel"),16);filter=ItemStack.of(tag.getCompound("filter"));}
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){return !isRemoved()&&(mode()==2||mode()==7)&&cap==ForgeCapabilities.ENERGY?power.cast():super.getCapability(cap,side);}
        private final class Power implements IEnergyStorage {
            @Override public int receiveEnergy(int amount,boolean simulate){int accepted=Math.max(0,Math.min(amount,getMaxEnergyStored()-energy));if(!simulate&&accepted>0){energy+=accepted;setChanged();}return accepted;}
            @Override public int extractEnergy(int amount,boolean simulate){int extracted=mode()==2?Math.max(0,Math.min(amount,energy)):0;if(!simulate&&extracted>0){energy-=extracted;setChanged();}return extracted;}
            @Override public int getEnergyStored(){return energy;}
            @Override public int getMaxEnergyStored(){return mode()==2?32768:1024;}
            @Override public boolean canExtract(){return mode()==2;}
            @Override public boolean canReceive(){return true;}
        }
    }
    private NativeNetworks(){}
}
