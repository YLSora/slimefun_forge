package io.github.slimefun4.forge;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;

/** A native dispenser with a persisted two-tick placement intent, never a second inventory. */
public final class BlockPlacerBlock extends DispenserBlock {
    private static final String OWNER="slimefun:placer_owner",PENDING="slimefun:placer_pending";
    private static final Set<DispenserBlockEntity> ACTIVE=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<String> DENIED=Catalog.ITEMS.stream().filter(e->Set.of("android","gps","projector","geo_miner","elevator","block_placer").contains(e.behavior())
        ||Set.of("energy_regulator","exp_collector","gps_teleporter","gps_activation_device_personal").contains(e.id())
        ||Set.of("CargoInputNode","CargoOutputNode","AdvancedCargoOutputNode","PersonalActivationPlate").contains(e.implementation())).map(Catalog.Entry::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final net.minecraft.tags.TagKey<Block> IGNORED=BlockTags.create(SlimefunForge.key("block_placer_ignored_materials"));
    public BlockPlacerBlock(){super(Properties.copy(Blocks.DISPENSER));}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity player,ItemStack stack){super.setPlacedBy(level,pos,state,player,stack);if(!level.isClientSide&&player!=null&&level.getBlockEntity(pos) instanceof DispenserBlockEntity inv){inv.getPersistentData().putUUID(OWNER,player.getUUID());inv.setChanged();}}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer server&&!ItemAccess.canUse(server,"block_placer")){ItemAccess.denied(server,"block_placer");return InteractionResult.FAIL;}
        return super.use(state,level,pos,player,hand,hit);
    }
    private static boolean available(ServerLevel level,BlockPos pos){return level.hasChunkAt(pos)&&!level.isOutsideBuildHeight(pos)&&level.getWorldBorder().isWithinBounds(pos);}
    private static boolean viewed(ServerLevel level,DispenserBlockEntity inv){return level.players().stream().anyMatch(p->p.containerMenu.slots.stream().anyMatch(s->s.container==inv));}
    private static ServerPlayer actor(ServerLevel level,DispenserBlockEntity inv){
        var data=inv.getPersistentData();if(!data.hasUUID(OWNER))return null;
        var actor=FakePlayerFactory.get(level,new GameProfile(data.getUUID(OWNER),"[Slimefun Placer]"));actor.setGameMode(GameType.SURVIVAL);var pos=inv.getBlockPos();actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);return actor;
    }
    private static boolean allowed(ServerLevel level,BlockPos target,ItemStack stack){
        if(stack.getItem() instanceof io.github.slimefun4.forge.api.ItemApi.NotPlaceable||!ItemAccess.enabled(level,stack))return false;
        if(!(stack.getItem() instanceof BlockItem item))return false;
        var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());if(id.getNamespace().equals(SlimefunForge.ID)&&DENIED.contains(id.getPath()))return false;
        var state=item.getBlock().defaultBlockState();if(state.is(Blocks.CAKE))return !level.getBlockState(target.below()).getCollisionShape(level,target.below()).isEmpty();
        var blockId=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(item.getBlock());
        return !state.is(IGNORED)&&MagicSettings.UNPLACEABLE_BLOCKS.get().stream().noneMatch(value->blockId.equals(new net.minecraft.resources.ResourceLocation(value.toLowerCase(Locale.ROOT))));
    }
    @Override protected void dispenseFrom(ServerLevel level,BlockPos pos){
        if(!ItemAccess.enabled(level,"block_placer")){super.dispenseFrom(level,pos);return;}
        if(!(level.getBlockEntity(pos) instanceof DispenserBlockEntity inv)||!ACTIVE.add(inv))return;
        try {
            if(inv.getPersistentData().contains(PENDING))return;
            var state=level.getBlockState(pos);var target=pos.relative(state.getValue(FACING));var actor=actor(level,inv);
            if(actor==null||!available(level,target)||!level.mayInteract(actor,target))return;
            int slot=inv.getRandomSlot(level.random);if(slot<0){level.levelEvent(1001,pos,0);return;}
            var input=inv.getItem(slot).copyWithCount(1);var item=input.copy();boolean shulker=item.getItem() instanceof BlockItem block&&block.getBlock() instanceof ShulkerBoxBlock;
            if(!level.isEmptyBlock(target)||!allowed(level,target,item)||!shulker&&viewed(level,inv))return;
            var before=inv.saveWithoutMetadata();
            var event=new BlockPlacerPlaceEvent(actor,pos,target,item);
            if(MinecraftForge.EVENT_BUS.post(event)||level.getBlockEntity(pos)!=inv||!state.equals(inv.getBlockState())||!before.equals(inv.saveWithoutMetadata())||!level.isEmptyBlock(target))return;
            item=event.getItem();shulker=((BlockItem)item.getItem()).getBlock() instanceof ShulkerBoxBlock;
            if(!allowed(level,target,item)||!shulker&&viewed(level,inv))return;
            if(shulker){place(level,inv,target,input,item,true);return;}
            var pending=new CompoundTag();pending.putUUID("owner",actor.getUUID());pending.putLong("target",target.asLong());pending.putLong("due",level.getGameTime()+2);pending.put("input",input.save(new CompoundTag()));pending.put("item",item.save(new CompoundTag()));
            inv.getPersistentData().put(PENDING,pending);inv.setChanged();level.scheduleTick(pos,this,2);
        } finally {ACTIVE.remove(inv);}
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        if(!(level.getBlockEntity(pos) instanceof DispenserBlockEntity inv))return;
        var data=inv.getPersistentData();
        if(!data.contains(PENDING)){super.tick(state,level,pos,random);return;}
        if(!ACTIVE.add(inv))return;
        try {
            var pending=data.getCompound(PENDING);long due=pending.getLong("due");
            if(due>level.getGameTime()){level.scheduleTick(pos,this,(int)Math.min(2,due-level.getGameTime()));return;}
            data.remove(PENDING);inv.setChanged();var target=BlockPos.of(pending.getLong("target"));
            if(!pending.hasUUID("owner")||!data.hasUUID(OWNER)||!pending.getUUID("owner").equals(data.getUUID(OWNER))||!target.equals(pos.relative(state.getValue(FACING))))return;
            place(level,inv,target,ItemStack.of(pending.getCompound("input")),ItemStack.of(pending.getCompound("item")),false);
        } finally {ACTIVE.remove(inv);}
    }
    private static void place(ServerLevel level,DispenserBlockEntity inv,BlockPos target,ItemStack expected,ItemStack replacement,boolean shulker){
        var actor=actor(level,inv);var pos=inv.getBlockPos();
        if(actor==null||expected.getCount()!=1||replacement.getCount()!=1||!ItemAccess.enabled(level,"block_placer")||!available(level,target)||!level.isEmptyBlock(target)||!allowed(level,target,expected)||!allowed(level,target,replacement)||!level.mayInteract(actor,target)
            ||level.captureBlockSnapshots||level.restoringBlockSnapshots||!shulker&&viewed(level,inv))return;
        int slot=-1;for(int i=0;i<inv.getContainerSize();i++)if(ItemStack.isSameItemSameTags(inv.getItem(i),expected)){slot=i;break;}if(slot<0)return;
        var paid=inv.removeItem(slot,1);inv.setChanged();boolean committed=false;
        var snapshots=new ArrayList<net.minecraftforge.common.util.BlockSnapshot>();
        try {
            var block=((BlockItem)replacement.getItem()).getBlock();var placed=block.defaultBlockState();
            if(shulker)placed=placed.setValue(ShulkerBoxBlock.FACING,inv.getBlockState().getValue(FACING));
            level.captureBlockSnapshots=true;
            boolean changed;
            try {
                changed=level.setBlock(target,placed,3);
                if(changed) {
                    if(shulker)BlockItem.updateCustomBlockEntityTag(level,actor,target,replacement);
                    block.setPlacedBy(level,target,placed,actor,replacement);
                }
            } finally {level.captureBlockSnapshots=false;}
            snapshots.addAll(level.capturedBlockSnapshots);level.capturedBlockSnapshots.clear();
            boolean cancelled=!changed;
            if(changed)cancelled=snapshots.size()>1?ForgeEventFactory.onMultiBlockPlace(actor,snapshots,inv.getBlockState().getValue(FACING)):!snapshots.isEmpty()&&ForgeEventFactory.onBlockPlace(actor,snapshots.get(0),inv.getBlockState().getValue(FACING));
            if(cancelled) {
                return;
            } else {
                committed=true;
                for(var snapshot:snapshots){var at=snapshot.getPos();var current=level.getBlockState(at);current.onPlace(level,at,snapshot.getReplacedBlock(),false);level.markAndNotifyBlock(at,level.getChunkAt(at),snapshot.getReplacedBlock(),current,snapshot.getFlag(),512);}
                level.levelEvent(2001,target,Block.getId(placed));
            }
        } finally {
            level.captureBlockSnapshots=false;
            if(!committed) {
                snapshots.addAll(level.capturedBlockSnapshots);level.capturedBlockSnapshots.clear();level.restoringBlockSnapshots=true;
                try {for(int i=snapshots.size()-1;i>=0;i--)snapshots.get(i).restore(true,false);}finally{level.restoringBlockSnapshots=false;}
                if(!inv.isRemoved()&&level.getBlockEntity(pos)==inv){var planned=MachineRecipe.copy(inv);var rest=planned.addItem(paid.copy());MachineRecipe.commit(inv,planned);paid=rest;}
                if(!paid.isEmpty())Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,paid.copy());
            }
        }
    }
}
