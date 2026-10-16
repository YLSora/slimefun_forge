package io.github.slimefun4.forge;

import javax.annotation.Nullable;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;

public final class MachineEntity extends BlockEntity implements MenuProvider,io.github.slimefun4.forge.api.MachineApi.ProcessHolder {
    public final SimpleContainer inventory;
    public final Catalog.Entry definition;
    final EntityAssembly assembly;
    private int energy, progress, total, storedExperience;
    private java.util.UUID owner;
    private Component networkStatus=Component.translatable("message.slimefun.network_connecting").withStyle(net.minecraft.ChatFormatting.GRAY);
    private ItemStack ingredient=ItemStack.EMPTY,fuelOutput=ItemStack.EMPTY;
    private final io.github.slimefun4.forge.api.MachineApi.Processor processor=AddonMachines.processor(this,this::currentOperation,()->endOperation((ServerLevel)level,true),()->endOperation((ServerLevel)level,false));
    @Override public io.github.slimefun4.forge.api.MachineApi.Processor processor(){return processor;}
    private io.github.slimefun4.forge.api.MachineApi.Operation currentOperation(){return ingredient.isEmpty()?null:new io.github.slimefun4.forge.api.MachineApi.Operation(SlimefunForge.key("fuel"),new CompoundTag(),total,progress,List.of(ingredient),fuelOutput.isEmpty()?List.of():List.of(fuelOutput));}
    private boolean loading, dismantling, pumping, growing, collecting;
    private LazyOptional<IEnergyStorage> power=LazyOptional.of(Power::new);
    private LazyOptional<IItemHandler> items=createItemCapability();
    private LazyOptional<IItemHandler> createItemCapability() { return LazyOptional.of(() -> new InvWrapper(inventory) {
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) {
            return isItemValid(slot,stack)?super.insertItem(slot,stack,simulate):stack;
        }
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate) {
            return hasInventory() && !sevenInputs() && slot>=inputSlots() && slot<inventory.getContainerSize()?super.extractItem(slot,amount,simulate):ItemStack.EMPTY;
        }
        @Override public boolean isItemValid(int slot,ItemStack stack) { return hasInventory() && slot>=0 && slot<inputSlots() && (assembly==null || assembly.accepts(slot,stack)); }
    }); }
    public MachineEntity(BlockPos pos,BlockState state) {
        super(SlimefunForge.MACHINE_ENTITY.get(),pos,state);definition=((MachineBlock)state.getBlock()).definition;
        inventory=new SimpleContainer(experienceCollector()?3:sevenInputs()?7:4) {
            @Override public void setChanged() { super.setChanged(); MachineEntity.this.inventoryChanged(); }
        };
        assembly=definition.implementation().equals("IronGolemAssembler") || definition.implementation().equals("WitherAssembler") ? new EntityAssembly(this) : null;
    }
    public int energy() { return energy; }
    public boolean regulator() { return definition.implementation().equals("EnergyRegulator"); }
    public Component networkStatus() { return networkStatus; }
    void networkStatus(Component value) {
        if(!networkStatus.equals(value)) { networkStatus=value; if(level!=null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); }
    }
    void networkEnergy(int value) { if(energy!=value) {energy=value;setChanged();wake();} }
    @Override public void setChanged(){super.setChanged();updateCapacitorTexture();}
    private void updateCapacitorTexture(){
        if(level==null||level.isClientSide||isRemoved()||!capacitor()||level.getBlockEntity(worldPosition)!=this)return;
        double filled=(double)energy/definition.capacity();
        int charge=filled<=0.25?0:filled<=0.5?1:filled<=0.75?2:3;
        var state=getBlockState();
        if(state.getValue(MachineBlock.Capacitor.CHARGE)!=charge)level.setBlock(worldPosition,state.setValue(MachineBlock.Capacitor.CHARGE,charge),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }
    @Override public CompoundTag getUpdateTag() { var tag=new CompoundTag(); if(regulator()) tag.putString("network_status",Component.Serializer.toJson(networkStatus)); return tag; }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() { return regulator()?net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this):null; }
    @Override public void handleUpdateTag(CompoundTag tag) {
        if(regulator() && tag.contains("network_status",8)) { var value=Component.Serializer.fromJson(tag.getString("network_status")); if(value!=null) networkStatus=value; }
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection,net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) { if(packet.getTag()!=null) handleUpdateTag(packet.getTag()); }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() { return regulator()?new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0,1.25,0).inflate(4):super.getRenderBoundingBox(); }
    private boolean generator() { return definition.production()>0; }
    private boolean solar() { return definition.implementation().equals("SolarGenerator"); }
    private boolean fueled() { return generator() && !solar(); }
    private boolean pump() { return definition.implementation().equals("FluidPump"); }
    private boolean tree() { return definition.implementation().equals("TreeGrowthAccelerator"); }
    private boolean cropGrowth() { return definition.implementation().equals("CropGrowthAccelerator"); }
    private boolean breeder() { return definition.implementation().equals("AutoBreeder"); }
    private boolean animalGrowth() { return definition.implementation().equals("AnimalGrowthAccelerator"); }
    boolean experienceCollector() { return definition.implementation().equals("ExpCollector"); }
    void owner(java.util.UUID value) { owner=value;setChanged(); }
    private boolean sevenInputs() { return tree() || cropGrowth() || breeder() || animalGrowth(); }
    private int inputSlots() { return assembly!=null?4:experienceCollector()?0:sevenInputs()?7:2; }
    boolean hasInventory() { return assembly!=null || fueled() || pump() || sevenInputs() || experienceCollector(); }
    private boolean capacitor() { return definition.implementation().equals("Capacitor"); }
    private void inventoryChanged() {
        if(loading) return;
        setChanged(); wake();
    }
    public void wake() {
        if(assembly!=null && !assembly.ready())return;
        if(level!=null && !level.isClientSide && !isRemoved() && !dismantling && !level.getBlockTicks().hasScheduledTick(worldPosition,getBlockState().getBlock()))
            level.scheduleTick(worldPosition,getBlockState().getBlock(),assembly!=null?assembly.delay():MachineClock.delay(level));
    }
    @Override public void onLoad() { super.onLoad(); updateCapacitorTexture(); EnergyNetwork.add(this); wake(); }
    @Override public void setRemoved() { EnergyNetwork.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { EnergyNetwork.remove(this); super.onChunkUnloaded(); }
    public void work() {
        if(level==null || level.isClientSide || isRemoved() || dismantling) return;
        if(assembly!=null) { assembly.work((ServerLevel)level); return; }
        if(regulator()) { EnergyNetwork.tick(this); wake(); return; }
        if(definition.implementation().equals("EnergyConnector")) return;
        if(pump()) { pumpFluid((ServerLevel)level); return; }
        if(tree()) { growTree((ServerLevel)level); return; }
        if(cropGrowth()) { growCrop((ServerLevel)level); return; }
        if(breeder() || animalGrowth()) { feedAnimal((ServerLevel)level); return; }
        if(experienceCollector()) { collectExperience((ServerLevel)level); return; }
        if(generator()) {
            if(solar()) {
                // A solar provider has no storage in RC-37: only this interval's unused output is available.
                long time=Math.floorMod(level.getDayTime(),24000);
                boolean day=!level.isRaining() && !level.isThundering() && (time<12300||time>23850);
                energy=level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD) && level.getBrightness(net.minecraft.world.level.LightLayer.SKY,worldPosition.above())==15 ? (day?definition.production():definition.nightProduction()) : 0;
                transfer(); wake();
            } else burn((ServerLevel)level);
            return;
        }
        if(capacitor()) { if(transfer() && energy>0) wake(); return; }
    }
    private boolean transfer() {
        boolean receiver=false;
        for(Direction direction : Direction.values()) {
            if(energy<=0) break;
            BlockPos target=worldPosition.relative(direction);
            if(!level.hasChunkAt(target) || EnergyNetwork.registered(level,target)) continue;
            var adjacent=level.getBlockEntity(target);
            if(adjacent==null) continue;
            receiver|=adjacent.getCapability(ForgeCapabilities.ENERGY,direction.getOpposite()).map(IEnergyStorage::canReceive).orElse(false);
            adjacent.getCapability(ForgeCapabilities.ENERGY,direction.getOpposite()).ifPresent(storage -> {
                int accepted=storage.receiveEnergy(energy,true);
                if(accepted>0) { energy-=storage.receiveEnergy(Math.min(energy,accepted),false); setChanged(); }
            });
        }
        return receiver;
    }
    private void collectExperience(ServerLevel server) {
        if(collecting || energy<10)return;
        // Moving orbs do not notify blocks; only a funded collector keeps its local scan awake.
        wake();collecting=true;
        try {
            var bounds=new net.minecraft.world.phys.AABB(worldPosition.getX()-4,worldPosition.getY()-4,worldPosition.getZ()-4,
                worldPosition.getX()+4,worldPosition.getY()+4,worldPosition.getZ()+4);
            int combined=0;
            for(var orb:server.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,bounds,net.minecraft.world.entity.Entity::isAlive)) {
                if(combined!=0 || energy<10 || dismantling || isRemoved())break;
                if(orb.isRemoved())continue;
                combined=storedExperience+orb.getValue();energy-=10;orb.discard();
                if(dismantling || isRemoved())return;
                var bottle=new ItemStack(SlimefunForge.item("filled_flask_of_knowledge"));int withdrawn=0;
                // RC-37 decides bottle count from the old balance, including its signed int arithmetic.
                for(int spent=0;spent<storedExperience;spent+=10) {
                    if(!inventory.canAddItem(bottle))break;
                    inventory.addItem(bottle.copy());withdrawn+=10;
                }
                storedExperience=combined-withdrawn;setChanged();
            }
        } finally {collecting=false;}
    }
    private void feedAnimal(ServerLevel server) {
        int cost=definition.consumption();boolean breed=breeder();
        if(growing || energy<cost) return;
        int selected=-1;
        for(int i=0;i<7;i++) {
            var stack=inventory.getItem(i);
            if(OrganicMaterialItem.isFood(stack)) {selected=i;break;}
        }
        if(selected<0) return;
        wake();
        int horizontal=breed?4:3,vertical=breed?2:3;
        var bounds=new net.minecraft.world.phys.AABB(worldPosition.getX()-horizontal,worldPosition.getY()-vertical,worldPosition.getZ()-horizontal,
            worldPosition.getX()+horizontal,worldPosition.getY()+vertical,worldPosition.getZ()+horizontal);
        for(var entity:server.getEntities((net.minecraft.world.entity.Entity)null,bounds,e->e.isAlive() && !e.isRemoved())) {
            if(breed) {
                if(!(entity instanceof net.minecraft.world.entity.animal.Animal animal) || animal.getAge()!=0 || animal.isInLove()) continue;
            } else if(entity instanceof net.minecraft.world.entity.AgeableMob ageable) {
                if(ageable.getAge()>=0) continue;
            } else if(!(entity instanceof net.minecraft.world.entity.monster.Zombie || entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin || entity instanceof net.minecraft.world.entity.monster.Zoglin)
                || !((net.minecraft.world.entity.Mob)entity).isBaby()) continue;
            growing=true;
            try {
                energy-=cost;inventory.removeItem(selected,1);setChanged();
                if(breed) ((net.minecraft.world.entity.animal.Animal)entity).setInLoveTime(600);
                else if(entity instanceof net.minecraft.world.entity.AgeableMob ageable) ageable.setAge(Math.min(0,ageable.getAge()+2000));
                // Bukkit exposes boolean-age monsters as -1/0, so one action matures them.
                else ((net.minecraft.world.entity.Mob)entity).setBaby(false);
                server.sendParticles(breed?net.minecraft.core.particles.ParticleTypes.HEART:net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    entity.getX(),entity.getEyeY(),entity.getZ(),8,.2,.2,.2,0);
            } finally {growing=false;}
            return;
        }
    }
    private void growCrop(ServerLevel server) {
        int cost=definition.consumption();
        if(growing || energy<cost) return;
        int selected=-1;
        for(int i=0;i<7;i++) {
            var stack=inventory.getItem(i);
            if(ItemAccess.enabled(server,stack)&&(OrganicMaterialItem.isFertilizer(stack)||stack.getItem() instanceof io.github.slimefun4.forge.api.MachineApi.Fertilizer)) {selected=i;break;}
        }
        if(selected<0) return;
        // Distant crop changes do not notify the machine; only funded work keeps scanning.
        wake();
        int radius=definition.id().equals("crop_growth_accelerator_2")?4:3;
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
            var pos=worldPosition.offset(x,0,z);
            if(server.isOutsideBuildHeight(pos) || !server.getWorldBorder().isWithinBounds(pos)) continue;
            var chunk=server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null) continue;
            var state=chunk.getBlockState(pos);
            if(!state.is(CROP_GROWTH_BLOCKS)) continue;
            if(!fertilizer(inventory.getItem(selected),state))continue;
            for(var property:state.getProperties()) if(property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty age && age.getName().equals("age")) {
                int value=state.getValue(age);
                if(value>=java.util.Collections.max(age.getPossibleValues())) break;
                growing=true;
                try {
                    if(!grow(server,pos,selected,cost,actor->server.setBlockAndUpdate(pos,state.setValue(age,value+1))))return;
                    server.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,4,.1,.1,.1,0);
                } finally {growing=false;}
                return;
            }
        }
    }
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> CROP_GROWTH_BLOCKS=BlockTags.create(new net.minecraft.resources.ResourceLocation("slimefun","crop_growth_accelerator_blocks"));
    private void growTree(ServerLevel server) {
        if(growing || energy<24) return;
        int selected=-1;
        for(int i=0;i<7;i++) {
            var stack=inventory.getItem(i);
            if(ItemAccess.enabled(server,stack)&&(OrganicMaterialItem.isFertilizer(stack)||stack.getItem() instanceof io.github.slimefun4.forge.api.MachineApi.Fertilizer)) {selected=i;break;}
        }
        if(selected<0) return;
        // Distant saplings can change without notifying this block; funded work keeps scanning.
        wake();
        for(int x=-9;x<=9;x++) for(int z=-9;z<=9;z++) {
            var pos=worldPosition.offset(x,0,z);
            var chunk=server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null || !chunk.getBlockState(pos).is(BlockTags.SAPLINGS)) continue;
            if(!fertilizer(inventory.getItem(selected),chunk.getBlockState(pos))||!server.hasChunksAt(pos.offset(-16,0,-16),pos.offset(16,0,16)))continue;
            growing=true;
            try {
                if(!grow(server,pos,selected,24,actor->BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL),server,pos,actor)))return;
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,4,.1,.1,.1,0);
            } finally {growing=false;}
            return;
        }
    }
    private boolean fertilizer(ItemStack stack,BlockState target){return ItemAccess.enabled(level,stack)&&(stack.getItem() instanceof io.github.slimefun4.forge.api.MachineApi.Fertilizer fertilizer?fertilizer.accepts(stack.copy(),target):OrganicMaterialItem.isFertilizer(stack));}
    private boolean grow(ServerLevel server,BlockPos target,int slot,int cost,java.util.function.Predicate<ServerPlayer> action){
        if(owner==null||server.captureBlockSnapshots||server.restoringBlockSnapshots||!ItemAccess.enabled(server,definition.id()))return false;
        var actor=net.minecraftforge.common.util.FakePlayerFactory.get(server,new com.mojang.authlib.GameProfile(owner,"[Slimefun Growth]"));actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);actor.setPos(worldPosition.getX()+.5,worldPosition.getY(),worldPosition.getZ()+.5);
        if(!actor.mayBuild()||!server.mayInteract(actor,target))return false;
        var expected=inventory.getItem(slot).copy();var snapshots=new java.util.ArrayList<net.minecraftforge.common.util.BlockSnapshot>();boolean committed=false;
        try {
            boolean changed;server.captureBlockSnapshots=true;
            try {changed=action.test(actor);}finally{server.captureBlockSnapshots=false;snapshots.addAll(server.capturedBlockSnapshots);server.capturedBlockSnapshots.clear();}
            if(!changed)return false;
            for(var snapshot:snapshots)if(!server.hasChunkAt(snapshot.getPos())||!server.getWorldBorder().isWithinBounds(snapshot.getPos())||!server.mayInteract(actor,snapshot.getPos()))return false;
            if(!snapshots.isEmpty()&&(snapshots.size()>1?net.minecraftforge.event.ForgeEventFactory.onMultiBlockPlace(actor,snapshots,Direction.UP):net.minecraftforge.event.ForgeEventFactory.onBlockPlace(actor,snapshots.get(0),Direction.UP)))return false;
            if(isRemoved()||server.getBlockEntity(worldPosition)!=this||energy<cost||!ItemStack.matches(expected,inventory.getItem(slot))||!ItemAccess.enabled(server,definition.id())||!ItemAccess.enabled(server,expected))return false;
            inventory.removeItem(slot,1);energy-=cost;setChanged();committed=true;
            for(var snapshot:snapshots){var pos=snapshot.getPos();var current=server.getBlockState(pos);current.onPlace(server,pos,snapshot.getReplacedBlock(),false);server.markAndNotifyBlock(pos,server.getChunkAt(pos),snapshot.getReplacedBlock(),current,snapshot.getFlag(),512);}
            return true;
        }finally{
            server.captureBlockSnapshots=false;
            if(!committed){server.restoringBlockSnapshots=true;try{for(int i=snapshots.size()-1;i>=0;i--)snapshots.get(i).restore(true,false);}finally{server.restoringBlockSnapshots=false;}}
        }
    }
    private void pumpFluid(ServerLevel server) {
        if(pumping || energy<32) return;
        BlockPos anchor=worldPosition.below();var fluid=server.getBlockState(anchor);
        if(!fluid.is(net.minecraft.world.level.block.Blocks.WATER) && !fluid.is(net.minecraft.world.level.block.Blocks.LAVA)) return;
        for(int slot=0;slot<2;slot++) {
            var input=inventory.getItem(slot);boolean bucket=MachineRecipe.same(input,new ItemStack(Items.BUCKET));
            if(!bucket && !MachineRecipe.same(input,new ItemStack(Items.GLASS_BOTTLE))) continue;
            boolean water=fluid.is(net.minecraft.world.level.block.Blocks.WATER);
            ItemStack result=bucket?new ItemStack(water?Items.WATER_BUCKET:Items.LAVA_BUCKET):water?
                net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.POTION),net.minecraft.world.item.alchemy.Potions.WATER):new ItemStack(Items.GLASS_BOTTLE);
            var output=new SimpleContainer(inventory.getItem(2).copy(),inventory.getItem(3).copy());
            if(!output.addItem(result).isEmpty()) return;
            wake();
            BlockPos source=null;
            if(water) { if(fluid.getValue(net.minecraft.world.level.block.LiquidBlock.LEVEL)==0) source=anchor; }
            else {
                var vein=new java.util.ArrayList<BlockPos>();lavaVein(server,anchor,vein);
                for(int i=vein.size()-1;i>=0;i--) if(server.getBlockState(vein.get(i)).getValue(net.minecraft.world.level.block.LiquidBlock.LEVEL)==0) { source=vein.get(i);break; }
            }
            if(source==null) return;
            pumping=true;
            try {
                if((bucket || server.random.nextInt(100)<30) && !server.setBlock(source,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3)) return;
                energy-=32;inventory.removeItem(slot,1);inventory.setItem(2,output.getItem(0));inventory.setItem(3,output.getItem(1));setChanged();
            } finally { pumping=false; }
            return;
        }
    }
    // Dough Vein order is observable: reverse DFS chooses a different source than BFS.
    private static final int[][] VEIN_OFFSETS={{0,1,0},{0,-1,0},{0,0,-1},{1,0,0},{0,0,1},{-1,0,0},{1,0,-1},{-1,0,-1},{1,0,1},{-1,0,1}};
    private static void lavaVein(ServerLevel level,BlockPos pos,java.util.List<BlockPos> vein) {
        if(vein.size()>=42 || vein.contains(pos)) return;
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null || !chunk.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LAVA)) return;
        vein.add(pos);
        for(var offset:VEIN_OFFSETS) lavaVein(level,pos.offset(offset[0],offset[1],offset[2]),vein);
    }
    private void burn(ServerLevel server) {
        if(!ingredient.isEmpty()) {
            if(progress<total) {
                if(definition.capacity()-energy<definition.production()) return;
                progress++; energy+=definition.production(); setChanged(); wake();
            } else {
                if(!fuelOutput.isEmpty()) {
                    var output=new SimpleContainer(inventory.getItem(2).copy(),inventory.getItem(3).copy());
                    if(!output.addItem(fuelOutput.copy()).isEmpty()) return;
                    inventory.setItem(2,output.getItem(0)); inventory.setItem(3,output.getItem(1));
                }
                endOperation(server,true);
            }
            return;
        }
        int selected=-1, rank=Integer.MAX_VALUE;
        for(int slot=0;slot<2;slot++) {
            int candidate=fuelRank(inventory.getItem(slot));
            if(candidate>=0 && candidate<rank) { selected=slot; rank=candidate; }
        }
        if(selected<0) return;
        var input=inventory.getItem(selected).copyWithCount(1);var fuel=AddonMachines.fuel(server,AddonMachines.key(definition.id()),input);
        var output=fuel!=null?AddonMachines.fuelResult(AddonMachines.key(definition.id()),input):input.is(Items.LAVA_BUCKET)||input.is(SlimefunForge.item("bucket_of_oil"))||input.is(SlimefunForge.item("bucket_of_fuel"))?new ItemStack(Items.BUCKET):ItemStack.EMPTY;
        if(!output.isEmpty()&&(!ItemAccess.enabled(server,output)||!new SimpleContainer(inventory.getItem(2).copy(),inventory.getItem(3).copy()).addItem(output.copy()).isEmpty()))return;
        ingredient=inventory.removeItem(selected,1).copy();progress=0;total=fuelCycles(definition,ingredient);fuelOutput=output;
        setChanged(); wake();
    }
    private void endOperation(ServerLevel server,boolean notify) {
        var event=notify&&!ingredient.isEmpty() && progress==total?new MachineOperationFinishEvent(server,this,processor,currentOperation()):null;
        ingredient=fuelOutput=ItemStack.EMPTY; progress=0; total=0; setChanged(); wake();
        if(event!=null) net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
    }
    public void dismantle() {
        if(!(level instanceof ServerLevel server) || dismantling) return;
        dismantling=true; Containers.dropContents(server,worldPosition,inventory); endOperation(server,true);
    }
    private static final int[] COAL_CYCLES={160,24,40,10,16,16,8,2,2,2,2,6,4,4,4};
    // Null entries retain the upstream positions of tags and the runtime-registered Goo item.
    private static final Item[] BIO_ITEMS={
        Items.ROTTEN_FLESH,Items.SPIDER_EYE,Items.BONE,Items.STRING,Items.APPLE,Items.MELON_SLICE,Items.MELON,Items.PUMPKIN,
        Items.PUMPKIN_SEEDS,Items.MELON_SEEDS,Items.WHEAT,Items.WHEAT_SEEDS,Items.CARROT,Items.POTATO,Items.SUGAR_CANE,Items.NETHER_WART,
        Items.RED_MUSHROOM,Items.BROWN_MUSHROOM,Items.VINE,Items.CACTUS,Items.LILY_PAD,Items.CHORUS_FRUIT,Items.KELP,Items.DRIED_KELP,
        Items.DRIED_KELP_BLOCK,Items.SEAGRASS,Items.SEA_PICKLE,Items.BAMBOO,Items.SWEET_BERRIES,Items.COCOA_BEANS,Items.BEETROOT,Items.BEETROOT_SEEDS,
        null,Items.HONEYCOMB,Items.HONEYCOMB_BLOCK,Items.SHROOMLIGHT,Items.CRIMSON_FUNGUS,Items.WARPED_FUNGUS,null,
        Items.GLOW_BERRIES,Items.SMALL_DRIPLEAF,Items.BIG_DRIPLEAF,Items.GLOW_LICHEN,Items.SPORE_BLOSSOM,null,null,null,null
    };
    private static final int[] BIO_CYCLES={4,4,4,4,6,6,54,6,6,6,6,6,6,6,6,6,4,4,4,4,4,16,2,4,40,2,4,2,4,4,6,6,2,8,80,8,4,4,32,4,6,6,4,40,2,2,4,4};
    private static int bioFuelRank(ItemStack input) {
        var block=input.getItem() instanceof BlockItem item?item.getBlock().defaultBlockState():null;
        for(int rank=0;rank<BIO_ITEMS.length;rank++) {
            boolean matches=switch(rank) {
                case 32 -> block!=null && block.is(BlockTags.SMALL_FLOWERS);
                case 38 -> input.is(SlimefunForge.item("strange_nether_goo"));
                case 44 -> block!=null && block.is(BlockTags.LEAVES);
                case 45 -> block!=null && block.is(BlockTags.SAPLINGS);
                case 46 -> block!=null && block.is(BlockTags.CORALS);
                case 47 -> block!=null && block.is(BlockTags.CORAL_BLOCKS);
                default -> input.is(BIO_ITEMS[rank]);
            };
            if(matches) return rank;
        }
        return -1;
    }
    static int fuelCycles(String kind,ItemStack item){
        int rank=fuelRank(kind,item);if(rank<0)return 0;
        return switch(kind){case "LavaGenerator"->80;case "BioGenerator"->BIO_CYCLES[rank];case "MagnesiumGenerator"->40;case "CombustionGenerator"->rank==0?60:180;default->COAL_CYCLES[rank];};
    }
    static int fuelCycles(Catalog.Entry definition,ItemStack item){var fuel=AddonMachines.fuel(null,AddonMachines.key(definition.id()),item);return fuel!=null?fuel.cycles():fuelCycles(definition.implementation(),item);}
    private int fuelRank(ItemStack item){if(!ItemAccess.enabled(level,item))return -1;var fuel=AddonMachines.fuel((ServerLevel)level,AddonMachines.key(definition.id()),item);return fuel!=null?1000:fuelRank(definition.implementation(),item);}
    private static int fuelRank(String kind,ItemStack item) {
        if(item.isEmpty() || !MachineRecipe.same(item,new ItemStack(item.getItem()))) return -1;
        if(kind.equals("LavaGenerator")) return item.is(Items.LAVA_BUCKET)?0:-1;
        if(kind.equals("BioGenerator")) return bioFuelRank(item);
        if(kind.equals("MagnesiumGenerator")) return item.is(SlimefunForge.item("magnesium_salt"))?0:-1;
        if(kind.equals("CombustionGenerator")) return item.is(SlimefunForge.item("bucket_of_oil"))?0:item.is(SlimefunForge.item("bucket_of_fuel"))?1:-1;
        if(!kind.equals("CoalGenerator")) return -1;
        if(item.is(Items.COAL_BLOCK)) return 0;
        if(item.is(Items.BLAZE_ROD)) return 1;
        if(item.is(Items.DRIED_KELP_BLOCK)) return 2;
        if(item.is(ItemTags.BOATS)) return 3;
        if(item.is(Items.COAL)) return 4;
        if(item.is(Items.CHARCOAL)) return 5;
        if(item.is(ItemTags.LOGS)) return 6;
        if(item.is(ItemTags.PLANKS)) return 7;
        if(item.is(ItemTags.WOODEN_SLABS)) return 8;
        if(item.is(ItemTags.WOODEN_BUTTONS)) return 9;
        if(item.is(ItemTags.WOODEN_FENCES)) return 10;
        if(item.is(ItemTags.WOODEN_TRAPDOORS)) return 11;
        if(item.is(ItemTags.WOODEN_PRESSURE_PLATES)) return 12;
        if(item.is(ItemTags.WOODEN_DOORS)) return 13;
        if(item.is(ItemTags.SIGNS)) return 14;
        return -1;
    }
    private final class Power implements IEnergyStorage {
        @Override public int receiveEnergy(int amount,boolean simulate) {
            if(!canReceive()||amount<=0) return 0;
            int value=Math.min(amount,definition.capacity()-energy);
            if(!simulate && value>0) { energy+=value; setChanged(); wake(); }
            return value;
        }
        @Override public int extractEnergy(int amount,boolean simulate) {
            if(!canExtract()||amount<=0) return 0;
            int value=Math.min(amount,energy);
            if(!simulate && value>0) { energy-=value; setChanged(); wake(); }
            return value;
        }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return solar()?definition.production():definition.capacity(); }
        @Override public boolean canExtract() { return generator()||capacitor(); }
        @Override public boolean canReceive() { return !generator(); }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,@Nullable Direction side) {
        if(!isRemoved() && capability==ForgeCapabilities.ENERGY) return power.cast();
        if(!isRemoved() && capability==ForgeCapabilities.ITEM_HANDLER) return items.cast();
        return super.getCapability(capability,side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); power.invalidate(); items.invalidate(); }
    @Override public void reviveCaps() {
        super.reviveCaps();
        power=LazyOptional.of(Power::new); items=createItemCapability(); wake();
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        var slots=NonNullList.withSize(inventory.getContainerSize(),ItemStack.EMPTY);
        for(int i=0;i<slots.size();i++) slots.set(i,inventory.getItem(i));
        ContainerHelper.saveAllItems(tag,slots);
        if(!solar()) tag.putInt("energy",energy);
        if(assembly!=null) assembly.save(tag);
        if(experienceCollector()) tag.putInt("stored_exp",storedExperience);
        if(owner!=null)tag.putUUID("owner",owner);
        if(!ingredient.isEmpty()) {
            var operation=new CompoundTag(); operation.put("ingredient",ingredient.save(new CompoundTag())); operation.put("output",fuelOutput.save(new CompoundTag())); operation.putInt("progress",progress); operation.putInt("total",total); tag.put("operation",operation);
        }
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); loading=true;
        var slots=NonNullList.withSize(inventory.getContainerSize(),ItemStack.EMPTY); ContainerHelper.loadAllItems(tag,slots);
        for(int i=0;i<slots.size();i++) inventory.setItem(i,slots.get(i)); loading=false;
        energy=Math.max(0,Math.min(definition.capacity(),tag.getInt("energy")));
        if(assembly!=null) assembly.load(tag);
        if(experienceCollector()) storedExperience=tag.getInt("stored_exp");
        owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;
        ingredient=fuelOutput=ItemStack.EMPTY; progress=0; total=0;
        if(fueled() && tag.contains("operation",10)) {
            var operation=tag.getCompound("operation"); var input=ItemStack.of(operation.getCompound("ingredient")); int duration=operation.getInt("total"),elapsed=operation.getInt("progress");
            var output=ItemStack.of(operation.getCompound("output"));
            if(!operation.contains("output",10)||!operation.contains("total",3)||!operation.contains("progress",3)||input.getCount()!=1||duration<=0||elapsed<0||elapsed>duration||output.getCount()>output.getMaxStackSize())throw new IllegalArgumentException("Invalid generator operation");
            ingredient=input;fuelOutput=output;total=duration;progress=elapsed;
        }
        wake();
    }
    @Override public Component getDisplayName() { return Component.translatable("item.slimefun."+definition.id()); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory playerInventory,Player player) { return assembly!=null?assembly.menu(id,playerInventory):new MachineMenu(id,playerInventory,this); }
    private static final class MachineMenu extends ChestMenu {
        private final MachineEntity machine;
        private final SimpleContainer display;
        private final ServerPlayer owner;
        private final int size,inputStart,outputStart;
        private int previousProgress=-1,previousTotal=-1;
        MachineMenu(int id,Inventory inventory,MachineEntity machine) { this(id,inventory,machine,new SimpleContainer(machine.pump()||machine.fueled()?45:27)); }
        MachineMenu(int id,Inventory inventory,MachineEntity machine,SimpleContainer display) {
            super(display.getContainerSize()==45?MenuType.GENERIC_9x5:MenuType.GENERIC_9x3,id,inventory,display,display.getContainerSize()/9); this.machine=machine; this.display=display; this.owner=(ServerPlayer)inventory.player;
            size=display.getContainerSize();inputStart=size==45?19:10;outputStart=size==45?24:15;
            if(size==45){MenuItems.processing(display,false);if(machine.pump())MenuItems.fill(display,Items.GRAY_STAINED_GLASS_PANE,22);}
            for(int i=0;i<size;i++) {
                var old=slots.get(i); final int slot=machine.experienceCollector()?(i>=12 && i<=14?i-12:-1):machine.sevenInputs()?(i>=10 && i<=16?i-10:-1):machine.hasInventory()?(i==inputStart?0:i==inputStart+1?1:i==outputStart?2:i==outputStart+1?3:-1):-1;
                if(slot<0&&size==27) display.setItem(i,MenuItems.blank(machine.experienceCollector()?Items.PURPLE_STAINED_GLASS_PANE:Items.CYAN_STAINED_GLASS_PANE));
                Slot replacement=new Slot(slot<0?display:machine.inventory,slot<0?i:slot,old.x,old.y) {
                    @Override public boolean mayPlace(ItemStack stack) { return slot>=0 && (machine.experienceCollector() || slot<machine.inputSlots()); }
                    @Override public boolean mayPickup(Player player) { return slot>=0&&stillValid(player); }
                };
                replacement.index=i; slots.set(i,replacement);
            }
        }
        @Override public void broadcastChanges() {
            if(machine!=null&&machine.fueled()&&(machine.progress!=previousProgress||machine.total!=previousTotal)) {
                previousProgress=machine.progress;previousTotal=machine.total;
                display.setItem(22,MenuItems.progress(Items.FLINT_AND_STEEL,previousProgress,previousTotal));
            }
            super.broadcastChanges();
        }
        @Override public boolean stillValid(Player player) {
            return player==owner&&player.isAlive() && !machine.isRemoved() && player.level()==machine.level && machine.level.getBlockEntity(machine.worldPosition)==machine
                && player.distanceToSqr(machine.worldPosition.getCenter())<=64&&player.level().mayInteract(player,machine.worldPosition)
                &&ResearchData.canUse(owner,new ItemStack(machine.getBlockState().getBlock()));
        }
        @Override public void clicked(int slot,int button,ClickType type,Player player){if(player.containerMenu==this&&stillValid(player))super.clicked(slot,button,type,player);}
        @Override public ItemStack quickMoveStack(Player player,int index) {
            if(player.containerMenu!=this||index<0||index>=slots.size()||!stillValid(player) || index<size && !slots.get(index).mayPickup(player)) return ItemStack.EMPTY;
            var slot=slots.get(index); var item=slot.getItem(); if(item.isEmpty()) return ItemStack.EMPTY;
            var before=item.copy();
            if(index<size) { if(!moveItemStackTo(item,size,slots.size(),true)) return ItemStack.EMPTY; }
            else if(!machine.hasInventory() || !moveItemStackTo(item,machine.experienceCollector()?12:inputStart,machine.experienceCollector()?15:inputStart+machine.inputSlots(),false)) return ItemStack.EMPTY;
            if(item.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            return before;
        }
    }
}
