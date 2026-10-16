package io.github.slimefun4.addons.example;

import io.github.slimefun4.forge.api.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.*;

/** Real native inventory, capabilities and transient TextDisplay, with no core storage superclass. */
final class NativeBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ExampleAddon.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ExampleAddon.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ExampleAddon.ID);
    static final RegistryObject<Block> VAULT=BLOCKS.register("vault",VaultBlock::new);
    static final RegistryObject<Block> WITHER_TILE=BLOCKS.register("wither_tile",WitherTile::new);
    static final RegistryObject<Block> INERT=BLOCKS.register("inert_block",()->new Block(BlockBehaviour.Properties.copy(Blocks.STONE)));
    static final RegistryObject<Item> VAULT_ITEM=ITEMS.register("vault",()->new BlockItem(VAULT.get(),new Item.Properties()));
    static final RegistryObject<Item> TILE_ITEM=ITEMS.register("wither_tile",()->new BlockItem(WITHER_TILE.get(),new Item.Properties()));
    static final RegistryObject<Item> INERT_ITEM=ITEMS.register("inert_block",()->new InertBlockItem(INERT.get()));
    static final RegistryObject<BlockEntityType<VaultEntity>> VAULT_ENTITY=ENTITIES.register("vault",()->BlockEntityType.Builder.of(VaultEntity::new,VAULT.get()).build(null));
    static void register(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);
        for(var item:ITEMS.getEntries())SlimefunAddonApi.registerEntry(new SlimefunAddonApi.Entry(item.getId(),ExampleAddon.key("native")));
    }
    static final class InertBlockItem extends BlockItem implements ItemApi.NotPlaceable {
        InertBlockItem(Block block){super(block,new Item.Properties());}
    }
    static final class WitherTile extends Block {
        WitherTile(){super(Properties.copy(Blocks.STONE));}
        @Override public boolean canEntityDestroy(BlockState state,BlockGetter level,BlockPos pos,Entity entity){return !(entity instanceof WitherBoss)&&super.canEntityDestroy(state,level,pos,entity);}
    }
    static final class VaultBlock extends BaseEntityBlock implements ItemApi.NotHopperable,ItemApi.ExternallyInteractable,ItemApi.AutomationBreak {
        VaultBlock(){super(Properties.copy(Blocks.IRON_BLOCK));}
        @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new VaultEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,VAULT_ENTITY.get(),VaultEntity::tick);}
        @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;if(level.isClientSide)return InteractionResult.SUCCESS;
            if(!(player instanceof ServerPlayer server)||!(level.getBlockEntity(pos) instanceof VaultEntity vault)||!vault.stillValid(server))return InteractionResult.FAIL;
            if(player.isShiftKeyDown()){var result=interact(new ItemApi.ExternalContext(server,server.getUUID(),null,pos,false));for(var item:result.outputs())ItemHandlerHelper.giveItemToPlayer(server,item);return result.success()?InteractionResult.CONSUME:InteractionResult.FAIL;}
            server.openMenu(vault);return InteractionResult.CONSUME;
        }
        @Override public ItemApi.ExternalResult interact(ItemApi.ExternalContext context){
            var level=context.level();if(!level.hasChunkAt(context.pos())||!(level.getBlockEntity(context.pos()) instanceof VaultEntity vault)||!vault.external(context))return new ItemApi.ExternalResult(false,Component.translatable("message.sf_example.empty"),List.of());
            var stored=vault.getItem(0);if(stored.isEmpty())return new ItemApi.ExternalResult(false,Component.translatable("message.sf_example.empty"),List.of());
            var result=stored.copyWithCount(1);if(!context.simulate())vault.removeItem(0,1);
            return new ItemApi.ExternalResult(true,Component.translatable("message.sf_example.withdrawn"),List.of(result));
        }
        @Override public boolean mayBreak(ItemApi.ExternalContext context){return context.level().getBlockEntity(context.pos()) instanceof VaultEntity vault&&vault.external(context);}
        @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
            if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof VaultEntity vault){vault.removeLabel();if(!level.isClientSide)Containers.dropContents(level,pos,vault);level.updateNeighbourForOutputSignal(pos,this);}
            super.onRemove(state,level,pos,next,moving);
        }
    }
    static final class VaultEntity extends BaseContainerBlockEntity {
        private NonNullList<ItemStack> contents=NonNullList.withSize(27,ItemStack.EMPTY);
        private Label label;
        VaultEntity(BlockPos pos,BlockState state){super(VAULT_ENTITY.get(),pos,state);}
        @Override protected Component getDefaultName(){return Component.translatable("block.sf_example.vault");}
        @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory){return ChestMenu.threeRows(id,inventory,this);}
        @Override public int getContainerSize(){return contents.size();}
        @Override public boolean isEmpty(){return contents.stream().allMatch(ItemStack::isEmpty);}
        @Override public ItemStack getItem(int slot){return contents.get(slot);}
        @Override public ItemStack removeItem(int slot,int amount){var result=ContainerHelper.removeItem(contents,slot,amount);if(!result.isEmpty())setChanged();return result;}
        @Override public ItemStack removeItemNoUpdate(int slot){return ContainerHelper.takeItem(contents,slot);}
        @Override public void setItem(int slot,ItemStack stack){contents.set(slot,stack);setChanged();}
        @Override public void clearContent(){contents.clear();setChanged();}
        @Override public boolean stillValid(Player player){return player instanceof ServerPlayer server&&server.isAlive()&&!server.isSpectator()&&level!=null&&player.level()==level&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(worldPosition.getCenter())<=64&&SlimefunAddonApi.canUse(server,new ItemStack(VAULT_ITEM.get()))&&super.canOpen(player);}
        @Override public boolean canOpen(Player player){return stillValid(player);}
        boolean external(ItemApi.ExternalContext context){
            if(context.machine()==null)return stillValid(context.actor());
            return context.machine() instanceof AndroidApi.Robot robot&&AndroidApi.valid(robot)&&context.owner().equals(robot.owner())&&context.owner().equals(context.actor().getUUID())&&context.level()==level&&level.getBlockEntity(worldPosition)==this&&robot.getBlockPos().distSqr(worldPosition)<=64&&ItemApi.enabled(context.level(),new ItemStack(VAULT_ITEM.get()))&&super.canOpen(context.actor());
        }
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);ContainerHelper.saveAllItems(tag,contents);}
        @Override public void load(CompoundTag tag){super.load(tag);contents=NonNullList.withSize(27,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,contents);}
        static void tick(Level level,BlockPos pos,BlockState state,VaultEntity vault){
            if(level.getGameTime()%20!=0)return;
            if(!ItemApi.enabled((ServerLevel)level,new ItemStack(VAULT_ITEM.get()))){vault.removeLabel();return;}
            if(vault.label==null||vault.label.isRemoved()){vault.label=new Label(level,pos);if(!level.addFreshEntity(vault.label)){vault.label=null;return;}}
            int total=vault.contents.stream().mapToInt(ItemStack::getCount).sum();vault.label.text(Component.translatable("message.sf_example.vault_count",total));
        }
        void removeLabel(){if(label!=null){label.discard();label=null;}}
        @Override public void setRemoved(){removeLabel();super.setRemoved();}
        @Override public void onChunkUnloaded(){removeLabel();super.onChunkUnloaded();}
    }
    static final class Label extends Display.TextDisplay {
        private final BlockPos owner;
        private String previous="";
        Label(Level level,BlockPos pos){super(EntityType.TEXT_DISPLAY,level);owner=pos.immutable();setPos(pos.getX()+.5,pos.getY()+1.3,pos.getZ()+.5);}
        void text(Component text){String json=Component.Serializer.toJson(text);if(json.equals(previous))return;previous=json;var data=new CompoundTag();data.putString("text",json);data.putString("billboard","center");readAdditionalSaveData(data);}
        @Override public boolean shouldBeSaved(){return false;}
        @Override public void tick(){super.tick();if(!level().isClientSide&&(!level().hasChunkAt(owner)||!level().getBlockState(owner).is(VAULT.get())))discard();}
    }
    private NativeBlocks(){}
}
