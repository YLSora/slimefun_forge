package io.github.slimefun4.forge;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.ForgeEventFactory;

/** The two RC-37 assemblers share a server phase, but never inventories or paid attempts. */
final class EntityAssembly {
    private static final int[] MENU_SLOTS={19,28,25,34};
    private final MachineEntity machine;
    private final boolean golem;
    private final ItemStack head,body;
    private boolean enabled,working;
    private double offset=3;
    private int lastAttempt=Integer.MIN_VALUE;

    EntityAssembly(MachineEntity machine){
        this.machine=machine;golem=machine.definition.implementation().equals("IronGolemAssembler");
        head=new ItemStack(golem?Items.CARVED_PUMPKIN:Items.WITHER_SKELETON_SKULL,golem?1:3);
        body=new ItemStack(golem?Items.IRON_BLOCK:Items.SOUL_SAND,4);
    }
    boolean accepts(int slot,ItemStack stack){return slot>=0&&slot<4&&(stack.is((slot<2?head:body).getItem())||AddonMachines.assemblies(AddonMachines.key(machine.definition.id())).stream().anyMatch(a->stack.is(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(slot<2?a.head():a.body()))));}
    private boolean enough(int start,ItemStack required){
        int count=0;for(int i=start;i<start+2;i++){var item=machine.inventory.getItem(i);if(ItemAccess.enabled(machine.getLevel(),item)&&MachineRecipe.same(item,required))count+=item.getCount();}return count>=required.getCount();
    }
    private record Plan(EntityType<?> type,ItemStack head,ItemStack body){}
    private Plan plan(){
        if(!enabled||machine.energy()<machine.definition.consumption()||!ItemAccess.enabled(machine.getLevel(),machine.definition.id()))return null;
        if(enough(2,body)&&enough(0,head))return new Plan(golem?EntityType.IRON_GOLEM:EntityType.WITHER,head,body);
        for(var value:AddonMachines.assemblies(AddonMachines.key(machine.definition.id()))){
            var head=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(value.head()),value.heads());var body=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(value.body()),value.bodies());
            if(ItemAccess.enabled(machine.getLevel(),head)&&ItemAccess.enabled(machine.getLevel(),body)&&enough(0,head)&&enough(2,body))return new Plan(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(value.entity()),head,body);
        }
        return null;
    }
    boolean ready(){return plan()!=null;}
    int delay(){long period=60L*MachineClock.interval();return (int)Math.min(Integer.MAX_VALUE,period-Math.floorMod(machine.getLevel().getServer().getTickCount(),period));}
    private void consume(int start,ItemStack required){
        int remaining=required.getCount();for(int i=start;i<start+2&&remaining>0;i++){var item=machine.inventory.getItem(i);if(MachineRecipe.same(item,required))remaining-=machine.inventory.removeItem(i,Math.min(remaining,item.getCount())).getCount();}
    }
    void work(ServerLevel level){
        if(working||!ready()||!level.shouldTickBlocksAt(new ChunkPos(machine.getBlockPos()).toLong()))return;
        int tick=level.getServer().getTickCount();
        if(Math.floorMod(tick,60L*MachineClock.interval())!=0||lastAttempt==tick){machine.wake();return;}
        working=true;lastAttempt=tick;
        try{
            var plan=plan();if(plan==null)return;var before=MachineRecipe.copy(machine.inventory);int energy=machine.energy();
            var pos=machine.getBlockPos();double x=pos.getX()+.5,y=pos.getY()+offset,z=pos.getZ()+.5;
            var target=net.minecraft.core.BlockPos.containing(x,y,z);if(level.isOutsideBuildHeight(target)||!level.hasChunkAt(target)||!level.getWorldBorder().isWithinBounds(target))return;
            var entity=plan.type().create(level);if(entity==null)return;entity.moveTo(x,y,z,0,0);
            if(entity instanceof Mob mob){
                ForgeEventFactory.onFinalizeSpawn(mob,level,level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.TRIGGERED,null,null);
                if(mob.isSpawnCancelled())return;
            }
            if(!level.addFreshEntity(entity))return;
            boolean valid=!entity.isRemoved()&&!machine.isRemoved()&&level.getBlockEntity(pos)==machine&&machine.energy()==energy&&ready();
            for(int slot=0;slot<before.getContainerSize();slot++)valid&=ItemStack.matches(before.getItem(slot),machine.inventory.getItem(slot));
            if(!valid){entity.discard();return;}
            consume(2,plan.body());consume(0,plan.head());machine.networkEnergy(energy-machine.definition.consumption());machine.setChanged();
            if(golem)level.playSound(null,x,y,z,SoundEvents.IRON_GOLEM_REPAIR,SoundSource.BLOCKS,.5F,1F);
            if(plan.head().getItem() instanceof BlockItem item)level.levelEvent(2001,pos,Block.getId(item.getBlock().defaultBlockState()));
        }finally{working=false;machine.wake();}
    }
    void save(CompoundTag tag){tag.putBoolean("enabled",enabled);tag.putDouble("offset",offset);}
    void load(CompoundTag tag){
        enabled=tag.getBoolean("enabled");offset=tag.contains("offset",99)?tag.getDouble("offset"):3;
        if(!Double.isFinite(offset)||tag.contains("offset")&&!tag.contains("offset",99)){offset=3;enabled=false;}
    }
    AbstractContainerMenu menu(int id,Inventory inventory){return new Menu(id,inventory);}
    private final class Menu extends ChestMenu {
        private final SimpleContainer display;
        private final ServerPlayer owner;
        private Boolean previousEnabled;
        private double previousOffset=Double.NaN;
        Menu(int id,Inventory inventory){this(id,inventory,new SimpleContainer(45));}
        Menu(int id,Inventory inventory,SimpleContainer display){
            super(MenuType.GENERIC_9x5,id,inventory,display,5);this.display=display;this.owner=(ServerPlayer)inventory.player;
            MenuItems.fill(display,Items.GRAY_STAINED_GLASS_PANE,0,2,3,4,5,6,8,12,14,21,23,30,32,39,40,41);
            MenuItems.fill(display,golem?Items.ORANGE_STAINED_GLASS_PANE:Items.BLACK_STAINED_GLASS_PANE,9,10,11,18,20,27,29,36,37,38);
            MenuItems.fill(display,golem?Items.WHITE_STAINED_GLASS_PANE:Items.BROWN_STAINED_GLASS_PANE,15,16,17,24,26,33,35,42,43,44);
            for(int i=0;i<45;i++){
                int found=-1;for(int n=0;n<4;n++)if(MENU_SLOTS[n]==i)found=n;final int input=found;var old=slots.get(i);
                var replacement=new Slot(input<0?display:machine.inventory,input<0?i:input,old.x,old.y){
                    @Override public boolean mayPlace(ItemStack stack){return input>=0;}
                    @Override public boolean mayPickup(Player player){return input>=0&&stillValid(player);}
                };replacement.index=i;slots.set(i,replacement);
            }
            display.setItem(1,MenuItems.label(owner,head,"assembly."+(golem?"pumpkin":"skull")));
            display.setItem(7,MenuItems.label(owner,body,"assembly."+(golem?"iron":"soul_sand")));
            display.setItem(13,MenuItems.label(owner,Items.CLOCK,"assembly.cooldown"));
            broadcastChanges();
        }
        @Override public boolean stillValid(Player player){
            return player==owner&&player.isAlive()&&!machine.isRemoved()&&player.level()==machine.getLevel()&&player.level().getBlockEntity(machine.getBlockPos())==machine
                &&player.distanceToSqr(machine.getBlockPos().getCenter())<=64&&player.level().mayInteract(player,machine.getBlockPos())
                &&(!(player instanceof ServerPlayer server)||ResearchData.canUse(server,new ItemStack(machine.getBlockState().getBlock())));
        }
        @Override public void clicked(int slot,int button,ClickType type,Player player){
            if(player.containerMenu!=this||!stillValid(player))return;
            if(slot==22||slot==31){
                if((type!=ClickType.PICKUP&&type!=ClickType.QUICK_MOVE)||(button!=0&&button!=1))return;
                if(slot==22)enabled=!enabled;
                else{
                    double next=offset+(button==1?-.1F:.1F);
                    if(Double.isFinite(next))offset=Double.parseDouble(new DecimalFormat("#.##",DecimalFormatSymbols.getInstance(Locale.ROOT)).format(next));
                }
                machine.setChanged();machine.wake();broadcastChanges();return;
            }
            super.clicked(slot,button,type,player);
        }
        @Override public void broadcastChanges(){
            if(display!=null){
                if(previousEnabled==null||previousEnabled!=enabled){previousEnabled=enabled;display.setItem(22,MenuItems.label(owner,enabled?Items.REDSTONE:Items.GUNPOWDER,enabled?"assembly.enabled":"assembly.disabled"));}
                if(previousOffset!=offset){previousOffset=offset;display.setItem(31,MenuItems.label(owner,Items.PISTON,"assembly.offset",offset));}
            }
            super.broadcastChanges();
        }
        @Override public ItemStack quickMoveStack(Player player,int index){
            if(player.containerMenu!=this||!stillValid(player)||index<0||index>=slots.size()||!slots.get(index).mayPickup(player))return ItemStack.EMPTY;
            var slot=slots.get(index);var item=slot.getItem();if(item.isEmpty())return ItemStack.EMPTY;var before=item.copy();
            if(index<45){if(!moveItemStackTo(item,45,slots.size(),true))return ItemStack.EMPTY;}
            else{for(int n=0;n<4&&!item.isEmpty();n++)if(accepts(n,item))moveItemStackTo(item,MENU_SLOTS[n],MENU_SLOTS[n]+1,false);if(item.getCount()==before.getCount())return ItemStack.EMPTY;}
            if(item.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return before;
        }
    }
}
