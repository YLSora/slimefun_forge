package io.github.slimefun4.addons.example;

import io.github.slimefun4.forge.ReactorExplodeEvent;
import io.github.slimefun4.forge.api.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.registries.*;
import static io.github.slimefun4.addons.example.ExampleAddon.key;

/** Native ticks, inventory, FE and persistence; the core owns no addon task queue or duplicate inventory. */
final class NativeMachines {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ExampleAddon.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ExampleAddon.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ExampleAddon.ID);
    static final RegistryObject<Block> PRESS=BLOCKS.register("native_press",()->new MachineBlock(0));
    static final RegistryObject<Block> GENERATOR=BLOCKS.register("native_generator",()->new MachineBlock(1));
    static final RegistryObject<Block> REACTOR=BLOCKS.register("native_reactor",()->new MachineBlock(2));
    static final RegistryObject<Block> STRUCTURE=BLOCKS.register("structure_press",()->new MachineBlock(3));
    static final RegistryObject<Block> ENCHANTER=BLOCKS.register("native_enchanter",()->new MachineBlock(4));
    static final RegistryObject<BlockEntityType<Machine>> ENTITY=ENTITIES.register("native_machine",()->BlockEntityType.Builder.of(Machine::new,PRESS.get(),GENERATOR.get(),REACTOR.get(),STRUCTURE.get(),ENCHANTER.get()).build(null));
    static final RegistryObject<Item> FERTILIZER=ITEMS.register("native_fertilizer",Fertilizer::new);
    static final RegistryObject<Item> MOVER=ITEMS.register("machine_mover",Mover::new);
    static {
        for(var block:List.of(PRESS,GENERATOR,REACTOR,STRUCTURE,ENCHANTER))ITEMS.register(block.getId().getPath(),()->new BlockItem(block.get(),new Item.Properties()));
    }
    static void register(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);
        for(var item:ITEMS.getEntries())SlimefunAddonApi.registerEntry(new SlimefunAddonApi.Entry(item.getId(),key("native")));
        for(String machine:List.of("native_generator","coal_generator"))MachineApi.registerFuel(new MachineApi.Fuel(key("fuel/"+machine),machine.equals("coal_generator")?core(machine):key(machine),key("compressed_iron"),8,key("iron_token")));
        MachineApi.registerFuel(new MachineApi.Fuel(key("reactor_fuel"),key("native_reactor"),key("isotope"),80,key("iron_token")));
        for(var machine:List.of(key("native_reactor"),core("nuclear_reactor")))MachineApi.registerCoolant(new MachineApi.Coolant(key("coolant/"+machine.getPath()),machine,key("iron_token")));
        MachineApi.registerProduce(new MachineApi.Produce(key("sheep_token"),new ResourceLocation("minecraft:wheat"),key("iron_token"),1,entity->entity instanceof net.minecraft.world.entity.animal.Sheep sheep&&!sheep.isBaby()&&!sheep.isSheared()));
        MachineApi.registerAssembly(new MachineApi.Assembly(key("snow_golem"),core("iron_golem_assembler"),new ResourceLocation("minecraft:snow_golem"),new ResourceLocation("minecraft:carved_pumpkin"),1,new ResourceLocation("minecraft:snow_block"),2));
        MachineApi.registerAutoCraft(core("enhanced_auto_crafter"),key("native_press"));
        MachineApi.registerMultiblock(new MachineApi.Multiblock(key("pedestal_press"),STRUCTURE.getId(),List.of(()->BlockPatternBuilder.start().aisle("M","B")
            .where('M',block->block.getState()!=null&&block.getState().is(STRUCTURE.get()))
            .where('B',block->block.getState()!=null&&block.getState().is(Blocks.COPPER_BLOCK)).build()),(player,match)->{
                var pos=match.getBlock(0,0,0).getPos();
                if(!(player.serverLevel().getBlockEntity(pos) instanceof Machine machine)||!machine.stillValid(player))return InteractionResult.FAIL;
                if(player.isShiftKeyDown()){machine.processor.cancel();return InteractionResult.CONSUME;}
                return machine.startPress(player.serverLevel())?InteractionResult.CONSUME:InteractionResult.FAIL;
            }));
    }
    private static ResourceLocation core(String path){return new ResourceLocation("slimefun",path);}
    private static final class Fertilizer extends Item implements MachineApi.Fertilizer {
        Fertilizer(){super(new Properties());}
        @Override public boolean accepts(ItemStack stack,BlockState target){return target.getBlock() instanceof CropBlock||target.is(net.minecraft.tags.BlockTags.SAPLINGS);}
    }
    private static final class MachineBlock extends BaseEntityBlock {
        final int mode;
        MachineBlock(int mode){super(Properties.copy(Blocks.IRON_BLOCK).pushReaction(PushReaction.BLOCK));this.mode=mode;}
        @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new Machine(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ENTITY.get(),Machine::tick);}
        @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack item){super.setPlacedBy(level,pos,state,placer,item);if(placer!=null&&level.getBlockEntity(pos) instanceof Machine machine){machine.owner=placer.getUUID();machine.setChanged();}}
        @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;if(level.isClientSide)return InteractionResult.SUCCESS;
            if(!(player instanceof ServerPlayer server)||!(level.getBlockEntity(pos) instanceof Machine machine)||!machine.stillValid(player))return InteractionResult.FAIL;
            if(player.isShiftKeyDown()){
                if(player.getItemInHand(hand).is(Items.LEVER)&&mode==2)machine.productionMode=!machine.productionMode;else machine.paused=!machine.paused;
                machine.setChanged();server.displayClientMessage(Component.translatable("message.sf_example.machine_mode",machine.paused,machine.productionMode),false);
            }else server.openMenu(machine);
            return InteractionResult.CONSUME;
        }
        @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
            if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof Machine machine&&!level.isClientSide){machine.removing=true;Containers.dropContents(level,pos,machine);machine.processor.end();}
            super.onRemove(state,level,pos,next,moving);
        }
    }
    static final class Machine extends BaseContainerBlockEntity implements MachineApi.ProcessHolder,MachineApi.ReactorAccess,EnergyApi.Member,CargoApi.Endpoint {
        private NonNullList<ItemStack> contents=NonNullList.withSize(9,ItemStack.EMPTY);
        final MachineApi.PersistentProcessor processor=new MachineApi.PersistentProcessor(this);
        private UUID owner;
        private int energy;
        private boolean paused,productionMode,working,removing;
        private LazyOptional<IEnergyStorage> power=LazyOptional.of(Power::new);
        Machine(BlockPos pos,BlockState state){super(ENTITY.get(),pos,state);}
        int mode(){return ((MachineBlock)getBlockState().getBlock()).mode;}
        @Override public EnergyApi.Role energyRole(){return mode()==1||mode()==2?EnergyApi.Role.GENERATOR:EnergyApi.Role.CONSUMER;}
        @Override public void networkEnergy(int amount){if(amount<0||amount>8192)throw new IllegalArgumentException("Invalid machine buffer");energy=amount;setChanged();}
        @Override public int[] cargoSlots(Direction side,boolean extracting,ItemStack offered,int slots){return java.util.stream.IntStream.range(extracting?6:0,extracting?9:6).toArray();}
        @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel)EnergyApi.register(this);}
        @Override public void setRemoved(){if(level instanceof ServerLevel)EnergyApi.unregister(this);super.setRemoved();}
        @Override public void onChunkUnloaded(){if(level instanceof ServerLevel)EnergyApi.unregister(this);super.onChunkUnloaded();}
        ResourceLocation id(){return ForgeRegistries.BLOCKS.getKey(getBlockState().getBlock());}
        @Override public MachineApi.Processor processor(){return processor;}
        @Override public boolean isReactor(){return mode()==2;}
        @Override public boolean acceptsCoolant(ItemStack stack){return mode()==2&&level instanceof ServerLevel server&&MachineApi.coolant(server,id(),stack);}
        @Override public void wake(){setChanged();}
        @Override protected Component getDefaultName(){return Component.translatable("block."+id().getNamespace()+"."+id().getPath());}
        @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory){return new Menu(id,inventory,this);}
        @Override public int getContainerSize(){return contents.size();}
        @Override public boolean isEmpty(){return contents.stream().allMatch(ItemStack::isEmpty);}
        @Override public ItemStack getItem(int slot){return contents.get(slot);}
        @Override public ItemStack removeItem(int slot,int amount){var result=ContainerHelper.removeItem(contents,slot,amount);if(!result.isEmpty())setChanged();return result;}
        @Override public ItemStack removeItemNoUpdate(int slot){return ContainerHelper.takeItem(contents,slot);}
        @Override public void setItem(int slot,ItemStack item){contents.set(slot,item);setChanged();}
        @Override public void clearContent(){contents.clear();setChanged();}
        @Override public boolean canPlaceItem(int slot,ItemStack item){return slot<6;}
        @Override public boolean stillValid(Player player){return player instanceof ServerPlayer server&&player.isAlive()&&!player.isSpectator()&&player.level()==level&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(worldPosition.getCenter())<=64&&(owner==null||owner.equals(player.getUUID()))&&SlimefunAddonApi.canUse(server,new ItemStack(getBlockState().getBlock()))&&super.canOpen(player);}
        @Override public boolean canOpen(Player player){return stillValid(player);}
        @Override protected IItemHandler createUnSidedHandler(){return new InvWrapper(this){
            @Override public ItemStack extractItem(int slot,int amount,boolean simulate){return slot>=6?super.extractItem(slot,amount,simulate):ItemStack.EMPTY;}
        };}
        private SimpleContainer outputPlan(List<ItemStack> outputs){var planned=new SimpleContainer(getItem(6).copy(),getItem(7).copy(),getItem(8).copy());for(var output:outputs)if(!planned.addItem(output.copy()).isEmpty())return null;return planned;}
        boolean startPress(ServerLevel server){
            if(paused||working||removing||processor.operation()!=null||!ItemApi.enabled(server,new ItemStack(getBlockState().getBlock())))return false;
            working=true;
            try {
                for(var recipe:MachineApi.recipes(server,key("native_press"))){
                    var inputs=new SimpleContainer(6);for(int i=0;i<6;i++)inputs.setItem(i,getItem(i).copy());
                    var paid=MachineApi.consume(this,inputs,recipe);if(paid.isEmpty())continue;
                    var outputs=new ArrayList<ItemStack>();outputs.add(recipe.output());
                    for(var input:paid)if(input.hasCraftingRemainingItem())for(int count=0;count<input.getCount();count++)outputs.add(input.getCraftingRemainingItem());
                    if(outputPlan(outputs)==null)continue;
                    var data=new CompoundTag();data.putString("recipe",recipe.id().toString());data.putInt("power",8);
                    var operation=new MachineApi.Operation(key("pressing"),data,recipe.ticks(),0,paid,outputs);
                    if(!processor.start(operation))return false;
                    for(int i=0;i<6;i++)setItem(i,inputs.getItem(i));return true;
                }
                return false;
            }finally{working=false;}
        }
        private void startEnchantment(){
            working=true;
            try{
                var kind=getItem(0).is(Items.ENCHANTED_BOOK)&&getItem(1).is(Items.ENCHANTED_BOOK)?MachineApi.EnchantmentWork.BIND:getItem(0).is(Items.BOOK)||getItem(1).is(Items.BOOK)?MachineApi.EnchantmentWork.DISENCHANT:MachineApi.EnchantmentWork.ENCHANT;
                var planned=MachineApi.enchantment(this,this,kind,1);if(planned==null||outputPlan(planned.outputs())==null||planned.outputs().stream().anyMatch(stack->!ItemApi.enabled((ServerLevel)level,stack)))return;
                var data=planned.data();data.putInt("power",8);var operation=new MachineApi.Operation(planned.type(),data,planned.total(),0,planned.inputs(),planned.outputs());
                if(processor.start(operation)){removeItem(0,1);removeItem(1,1);}
            }finally{working=false;}
        }
        private void startFuel(ServerLevel server){
            var fuel=MachineApi.fuel(server,id(),getItem(0));if(fuel==null)return;
            var outputs=fuel.result()==null?List.<ItemStack>of():List.of(new ItemStack(ForgeRegistries.ITEMS.getValue(fuel.result())));if(outputPlan(outputs)==null)return;
            var data=new CompoundTag();data.putString("fuel",fuel.id().toString());data.putInt("power",mode()==2?64:16);
            var operation=new MachineApi.Operation(key(mode()==2?"reactor_fuel":"generator_fuel"),data,fuel.cycles(),0,List.of(getItem(0).copyWithCount(1)),outputs);
            if(processor.start(operation))removeItem(0,1);
        }
        static void tick(Level level,BlockPos pos,BlockState state,Machine machine){
            if(level.getGameTime()%10!=0)return;
            var server=(ServerLevel)level;var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(state.getBlock().asItem());
            if(!DiagnosticApi.execute(server,pos,item,"machine tick",()->{try(var timing=DiagnosticApi.start(server,pos,item)){machine.work(server);}})){machine.paused=true;machine.setChanged();}
        }
        private void exchangePort(ServerLevel server){
            var port=MachineApi.reactorPort(this);if(port==null)return;
            for(int slot=0;slot<6;slot++){
                var offered=port.getItem(slot);if(offered.isEmpty()||!(slot<3?MachineApi.fuel(server,id(),offered)!=null:acceptsCoolant(offered)))continue;
                int destination=slot<3?0:1;var planned=new SimpleContainer(getItem(destination).copy());var rest=planned.addItem(offered.copy());
                if(rest.getCount()<offered.getCount()){setItem(destination,planned.getItem(0));port.setItem(slot,rest);}
            }
            for(int slot=6;slot<9;slot++){var original=getItem(slot);if(original.isEmpty())continue;var planned=new SimpleContainer(port.getItem(6).copy());var rest=planned.addItem(original.copy());if(rest.getCount()<original.getCount()){setItem(slot,rest);port.setItem(6,planned.getItem(0));}}
            port.setChanged();
        }
        private void work(ServerLevel server){
            if(working||removing||paused||server.hasNeighborSignal(worldPosition))return;
            working=true;try{if(mode()==2)exchangePort(server);}finally{working=false;}
            var operation=processor.operation();
            if(operation==null){if(!ItemApi.enabled(server,new ItemStack(getBlockState().getBlock())))return;if(mode()==0)startPress(server);else if(mode()==4)startEnchantment();else if(mode()!=3)startFuel(server);return;}
            working=true;
            try {
                var plan=outputPlan(operation.outputs());if(plan==null)return;
                if(operation.finished()){for(int i=0;i<3;i++)setItem(6+i,plan.getItem(i));processor.end();return;}
                int amount=operation.data().getInt("power");
                if(mode()==0||mode()==3||mode()==4){if(energy<amount)return;energy-=amount;}
                else {
                    boolean fits=8192-energy>=amount;if(!fits&&(mode()!=2||!productionMode))return;
                    if(mode()==2){
                        for(var direction:Direction.Plane.HORIZONTAL){var pos=worldPosition.relative(direction);if(!server.hasChunkAt(pos))return;if(!server.getBlockState(pos).is(Blocks.WATER)){melt(server);return;}}
                        if((operation.progress()+1)%50==0){if(!MachineApi.coolant(server,id(),getItem(1))){melt(server);return;}removeItem(1,1);}
                    }
                    if(fits)energy+=amount;
                }
                processor.advance(1);setChanged();
            }finally{working=false;}
        }
        private void melt(ServerLevel server){removing=true;processor.cancel();MinecraftForge.EVENT_BUS.post(new ReactorExplodeEvent(server,this));if(server.getBlockEntity(worldPosition)==this)server.setBlockAndUpdate(worldPosition,Blocks.LAVA.defaultBlockState());}
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);ContainerHelper.saveAllItems(tag,contents);tag.putInt("energy",energy);tag.putBoolean("paused",paused);tag.putBoolean("production",productionMode);if(owner!=null)tag.putUUID("owner",owner);tag.put("operation",processor.save());}
        @Override public void load(CompoundTag tag){
            super.load(tag);var operation=tag.getCompound("operation");
            var type=mode()==4?new ResourceLocation("slimefun:enchanting"):key(mode()==1?"generator_fuel":mode()==2?"reactor_fuel":"pressing");
            if(!operation.isEmpty()&&(!type.toString().equals(operation.getString("type"))||operation.getCompound("data").getInt("power")!=(mode()==1?16:mode()==2?64:8)||operation.getInt("total")>72000))throw new IllegalArgumentException("Invalid native machine operation");
            processor.load(operation);var restored=NonNullList.withSize(9,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,restored);contents=restored;energy=Math.max(0,Math.min(8192,tag.getInt("energy")));paused=tag.getBoolean("paused");productionMode=tag.getBoolean("production");owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;
        }
        private final class Power implements IEnergyStorage {
            @Override public int receiveEnergy(int amount,boolean simulate){int value=canReceive()?Math.max(0,Math.min(amount,8192-energy)):0;if(!simulate&&value>0){energy+=value;setChanged();}return value;}
            @Override public int extractEnergy(int amount,boolean simulate){int value=mode()==1||mode()==2?Math.max(0,Math.min(amount,energy)):0;if(!simulate&&value>0){energy-=value;setChanged();}return value;}
            @Override public int getEnergyStored(){return energy;}
            @Override public int getMaxEnergyStored(){return 8192;}
            @Override public boolean canExtract(){return mode()==1||mode()==2;}
            @Override public boolean canReceive(){return mode()==0||mode()==3||mode()==4;}
        }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,Direction side){return !isRemoved()&&capability==ForgeCapabilities.ENERGY?power.cast():super.getCapability(capability,side);}
        @Override public void invalidateCaps(){if(level instanceof ServerLevel)EnergyApi.unregister(this);super.invalidateCaps();power.invalidate();}
        @Override public void reviveCaps(){super.reviveCaps();power=LazyOptional.of(Power::new);if(level instanceof ServerLevel&&NetworkApi.live((ServerLevel)level,this))EnergyApi.register(this);}
    }
    private static final class Menu extends ChestMenu {
        private final Machine machine;
        private final ServerPlayer owner;
        private int previous=-2,previousEnergy=-1;
        Menu(int id,Inventory inventory,Machine machine){
            super(MenuType.GENERIC_9x1,id,inventory,machine,1);this.machine=machine;owner=(ServerPlayer)inventory.player;
            for(int i=0;i<9;i++){int slot=i;var old=slots.get(i);var replacement=new Slot(machine,i,old.x,old.y){@Override public boolean mayPlace(ItemStack stack){return slot<6;}@Override public boolean mayPickup(Player player){return stillValid(player);}};replacement.index=i;slots.set(i,replacement);}
        }
        @Override public boolean stillValid(Player player){return machine!=null&&machine.stillValid(player);}
        @Override public void broadcastChanges(){
            if(machine!=null&&stillValid(owner)){var operation=machine.processor.operation();int progress=operation==null?-1:operation.progress();if(progress!=previous||previousEnergy!=machine.energy){previous=progress;previousEnergy=machine.energy;int bar=operation==null?0:operation.bar(20);owner.displayClientMessage(Component.translatable("message.sf_example.machine_progress","|".repeat(bar)+".".repeat(20-bar),progress,operation==null?0:operation.total(),machine.energy),true);}}
            super.broadcastChanges();
        }
        @Override public void clicked(int slot,int button,ClickType type,Player player){if(player.containerMenu==this&&stillValid(player))super.clicked(slot,button,type,player);}
        @Override public ItemStack quickMoveStack(Player player,int index){if(player.containerMenu!=this||!stillValid(player)||index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);var item=slot.getItem();var before=item.copy();if(item.isEmpty()||!(index<9?moveItemStackTo(item,9,slots.size(),true):moveItemStackTo(item,0,6,false)))return ItemStack.EMPTY;if(item.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return before;}
    }
    private static final class Mover extends Item {
        Mover(){super(new Properties().stacksTo(1));}
        @Override public InteractionResult useOn(UseOnContext context){
            if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
            if(!(context.getPlayer() instanceof ServerPlayer player)||context.getHand()!=InteractionHand.MAIN_HAND||!SlimefunAddonApi.canUse(player,context.getItemInHand()))return InteractionResult.FAIL;
            var level=player.serverLevel();var target=context.getClickedPos();if(!(level.getBlockEntity(target) instanceof Machine to)||!to.stillValid(player))return InteractionResult.FAIL;
            var tag=context.getItemInHand().getOrCreateTag();
            if(!tag.contains("source")){tag.putLong("source",target.asLong());tag.putString("dimension",level.dimension().location().toString());return InteractionResult.CONSUME;}
            var source=BlockPos.of(tag.getLong("source"));boolean sameWorld=tag.getString("dimension").equals(level.dimension().location().toString());tag.remove("source");tag.remove("dimension");
            if(!sameWorld||source.equals(target)||!level.hasChunkAt(source)||!(level.getBlockEntity(source) instanceof Machine from)||!from.stillValid(player)||!from.getBlockState().is(to.getBlockState().getBlock())||!to.isEmpty()||to.energy!=0||to.processor.operation()!=null||from.working||to.working)return InteractionResult.FAIL;
            if(!level.mayInteract(player,source)||!level.mayInteract(player,target)||!player.mayBuild())return InteractionResult.FAIL;
            var beforeFrom=from.saveWithoutMetadata();var beforeTo=to.saveWithoutMetadata();
            var face=context.getClickedFace();
            if(net.minecraftforge.event.ForgeEventFactory.onBlockPlace(player,net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(),level,source),face)||net.minecraftforge.event.ForgeEventFactory.onBlockPlace(player,net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(),level,target),face))return InteractionResult.FAIL;
            if(!from.stillValid(player)||!to.stillValid(player)||!beforeFrom.equals(from.saveWithoutMetadata())||!beforeTo.equals(to.saveWithoutMetadata()))return InteractionResult.FAIL;
            to.processor.load(from.processor.save());for(int i=0;i<9;i++)to.contents.set(i,from.contents.get(i).copy());to.energy=from.energy;to.paused=from.paused;to.productionMode=from.productionMode;to.owner=from.owner;
            from.processor.cancel();from.clearContent();from.energy=0;from.setChanged();to.setChanged();return InteractionResult.CONSUME;
        }
    }
    private NativeMachines(){}
}
