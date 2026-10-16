package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;

public final class MagicUtilityItem extends Item {
    private static final String OWNER="slimefun_tome_owner",NAME="slimefun_tome_name",USES="slimefun_storm_uses";
    private static final Set<UUID> CONVERTING=new HashSet<>();
    private static final Set<ServerPlayer> USING=Collections.newSetFromMap(new IdentityHashMap<>());
    private final String kind;
    public MagicUtilityItem(Catalog.Entry entry){super(new Properties());kind=entry.id();}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){return new InteractionResultHolder<>(activate(player,hand,null),player.getItemInHand(hand));}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> text,TooltipFlag flag){
        if(kind.equals("staff_elemental_storm"))text.add(Component.translatable("tooltip.slimefun.storm_uses",uses(stack)));
        if(kind.equals("tome_of_knowledge_sharing")&&stack.hasTag()&&stack.getTag().hasUUID(OWNER))text.add(Component.translatable("tooltip.slimefun.tome_owner",stack.getTag().getString(NAME)));
    }
    private static int uses(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(USES)?Math.max(1,Math.min(8,stack.getTag().getInt(USES))):8;}
    InteractionResult activate(Player player,InteractionHand hand,UseOnContext context) {
        if(!(player instanceof ServerPlayer p))return kind.equals("staff_elemental_water")||kind.equals("scroll_of_dimensional_teleposition")?InteractionResult.PASS:InteractionResult.SUCCESS;var stack=p.getItemInHand(hand);
        if(stack.isEmpty()||stack.getItem()!=this||USING.contains(p))return InteractionResult.FAIL;
        if(!ResearchData.canUse(p,stack)){ItemAccess.denied(p,stack);return InteractionResult.FAIL;}
        var level=p.serverLevel();
        USING.add(p);
        try {
        switch(kind) {
            case "staff_elemental_water" -> {p.clearFire();Messages.chat(p,Component.translatable("message.slimefun.fire_extinguish"));}
            case "staff_elemental_storm" -> {return storm(p,stack)?InteractionResult.SUCCESS:InteractionResult.PASS;}
            case "infernal_bonemeal" -> {
                if(context==null)return InteractionResult.SUCCESS;var pos=context.getClickedPos();var state=level.getBlockState(pos);
                if(state.is(Blocks.NETHER_WART)&&state.getValue(NetherWartBlock.AGE)<3&&level.mayInteract(p,pos)&&p.mayUseItemAt(pos,context.getClickedFace(),stack)) {
                    var snapshot=stack.copy();if(!ForgeHooks.onCropsGrowPre(level,pos,state,true)||p.serverLevel()!=level||!p.isAlive()||p.getItemInHand(hand)!=stack||!ItemStack.matches(stack,snapshot)||!ResearchData.canUse(p,stack)||!level.getBlockState(pos).equals(state))return InteractionResult.SUCCESS;
                    var paid=p.isCreative()?ItemStack.EMPTY:stack.split(1);
                    if(!level.setBlockAndUpdate(pos,state.setValue(NetherWartBlock.AGE,3))){returnItem(p,stack,paid);return InteractionResult.SUCCESS;}
                    ForgeHooks.onCropsGrowPost(level,pos,state);level.levelEvent(2001,pos,Block.getId(Blocks.REDSTONE_BLOCK.defaultBlockState()));
                }
            }
            case "scroll_of_dimensional_teleposition" -> {
                for(var entity:level.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(MagicSettings.SCROLL_RADIUS.get()),e->e!=p&&!(e instanceof ArmorStand))) {
                    float yaw=entity.getYRot()+180;if(yaw>360)yaw-=360;
                    if(entity instanceof ServerPlayer other)other.connection.teleport(other.getX(),other.getY(),other.getZ(),yaw,other.getXRot());
                    else{entity.teleportTo(entity.getX(),entity.getY(),entity.getZ());entity.setYRot(yaw);entity.setYHeadRot(yaw);entity.setYBodyRot(yaw);}
                }
            }
            case "tome_of_knowledge_sharing" -> {
                var tag=stack.getOrCreateTag();
                if(!tag.hasUUID(OWNER)){tag.putUUID(OWNER,p.getUUID());tag.putString(NAME,p.getGameProfile().getName());p.playNotifySound(SoundEvents.PLAYER_LEVELUP,SoundSource.PLAYERS,1,1);}
                else if(tag.getUUID(OWNER).equals(p.getUUID()))Messages.chat(p,Component.translatable("message.slimefun.tome_yourself"));
                else{ResearchData.get(p).share(tag.getUUID(OWNER),p);if(!p.isCreative())stack.shrink(1);}
            }
            case "magical_zombie_pills" -> {}
            default -> throw new IllegalStateException("Unknown magic utility "+kind);
        }
        return kind.equals("staff_elemental_water")||kind.equals("scroll_of_dimensional_teleposition")?InteractionResult.PASS:InteractionResult.SUCCESS;
        } finally {USING.remove(p);}
    }
    private static boolean storm(ServerPlayer p,ItemStack stack) {
        if(p.getFoodData().getFoodLevel()<4&&!p.isCreative()){Messages.chat(p,Component.translatable("message.slimefun.hungry"));return false;}
        var level=p.serverLevel();var start=p.getEyePosition();var end=start.add(p.getLookAngle().scale(30));
        var positions=new ArrayList<BlockPos>();BlockGetter.traverseBlocks(start,end,positions,(list,pos)->{if(!SpecialTools.available(level,pos))return list;list.add(pos.immutable());return level.getBlockState(pos).isAir()?null:list;},list->list);
        if(positions.isEmpty())return false;var pos=positions.get(positions.size()-1);
        if(!p.server.isPvpAllowed()||!level.mayInteract(p,pos)){Messages.chat(p,Component.translatable("message.slimefun.no_pvp"));return false;}
        var bolt=EntityType.LIGHTNING_BOLT.create(level);if(bolt==null)return true;bolt.moveTo(Vec3.atLowerCornerOf(pos));bolt.setCause(p);
        // Reserve before entity callbacks so reentrant use cannot spend the same final charge twice.
        var used=stack.split(1);var refund=used.copy();int left=uses(used)-1;if(left>0)used.getOrCreateTag().putInt(USES,left);else used.setCount(0);
        if(!level.addFreshEntity(bolt)){returnItem(p,stack,refund);return true;}
        returnItem(p,stack,used);if(left==0)p.playNotifySound(SoundEvents.ITEM_BREAK,SoundSource.PLAYERS,1,1);
        if(!p.isCreative()){var event=new StaffFoodEvent(p,p.getFoodData().getFoodLevel()-4);if(!MinecraftForge.EVENT_BUS.post(event))p.getFoodData().setFoodLevel(event.getFood());}
        return true;
    }
    private static void returnItem(ServerPlayer player,ItemStack held,ItemStack item){if(item.isEmpty())return;if(held.isEmpty()&&(player.getMainHandItem()==held||player.getOffhandItem()==held)){held.setCount(1);held.setTag(item.hasTag()?item.getTag().copy():null);}else if(!player.getInventory().add(item))player.drop(item,false);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void cure(PlayerInteractEvent.EntityInteract event) {
        if(!(event.getItemStack().getItem() instanceof MagicUtilityItem item)||!item.kind.equals("magical_zombie_pills")||!(event.getTarget() instanceof ZombieVillager||event.getTarget() instanceof ZombifiedPiglin))return;
        if(event.getEntity() instanceof ServerPlayer p) {
            var target=(Mob)event.getTarget();var stack=event.getItemStack();if(!ResearchData.canUse(p,stack)||!p.level().mayInteract(p,target.blockPosition())||!target.isAlive()||!CONVERTING.add(target.getUUID()))return;
            try {
                if(target instanceof ZombieVillager zombie){if(!p.isCreative())stack.shrink(1);((io.github.slimefun4.forge.mixin.ZombieCureAccess)zombie).slimefun$cure(p.getUUID(),1);}
                else {
                    var level=p.serverLevel();var expected=stack.copy();if(!net.minecraftforge.event.ForgeEventFactory.canLivingConvert(target,EntityType.PIGLIN,ticks->{})||!target.isAlive()||!p.isAlive()||p.serverLevel()!=level||target.level()!=level||p.getItemInHand(event.getHand())!=stack||!ItemStack.matches(stack,expected)||!ResearchData.canUse(p,stack))return;
                    var pig=EntityType.PIGLIN.create(p.serverLevel());if(pig==null)return;pig.moveTo(target.getX(),target.getY(),target.getZ(),target.getYRot(),target.getXRot());
                    pig.finalizeSpawn(p.serverLevel(),p.serverLevel().getCurrentDifficultyAt(target.blockPosition()),MobSpawnType.CONVERSION,null,null);
                    if(!target.isAlive()||!p.isAlive()||p.serverLevel()!=level||p.getItemInHand(event.getHand())!=stack||!ItemStack.matches(stack,expected))return;
                    var paid=p.isCreative()?ItemStack.EMPTY:stack.split(1);if(!p.serverLevel().addFreshEntity(pig)){returnItem(p,stack,paid);return;}target.discard();net.minecraftforge.event.ForgeEventFactory.onLivingConvert(target,pig);
                }
                p.playNotifySound(SoundEvents.ZOMBIE_VILLAGER_CONVERTED,SoundSource.PLAYERS,1,1);
            }finally{CONVERTING.remove(target.getUUID());}
        }
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
