package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;

public final class TalismanItem extends Item implements io.github.slimefun4.forge.api.MagicApi.Talisman {
    private static final String REPAIRS="slimefun_anvil_repairs";
    private final Catalog.Entry entry;
    private final List<MobEffectInstance> effects;
    public TalismanItem(Catalog.Entry entry){super(new Properties());this.entry=entry;effects=entry.effects().stream().map(e->new MobEffectInstance(Objects.requireNonNull(BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation("minecraft",e.effect()))),e.duration(),e.amplifier())).toList();}
    public String id(){return entry.id();}
    @Override public int chance(){return entry.talisman().chance();}
    @Override public List<MobEffectInstance> effects(){return effects.stream().map(MobEffectInstance::new).toList();}
    @Override public boolean consumable(){return entry.talisman().consumable();}
    @Override public boolean cancelsSource(){return entry.talisman().cancels();}
    @Override public Item enderVariant(){return entry.id().startsWith("ender_")?null:SlimefunForge.item("ender_"+entry.id());}
    @Override public Component activationMessage(){return Component.translatable("message.slimefun.talisman."+entry.id().replace("ender_","").replace("_talisman",""));}
    private void message(ServerPlayer player){boolean actionbar=Guide.TALISMAN_ACTIONBAR.get();Messages.send(player,activationMessage(),actionbar,!actionbar);}
    // RC-37 returns selection success even when TalismanActivateEvent cancels the inner activation.
    private static boolean trigger(ServerPlayer player,String name,Event source,boolean sendMessage) {
        return io.github.slimefun4.forge.api.MagicApi.trigger(player,SlimefunForge.item(name+"_talisman"),source,sendMessage).selected();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingAttackEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;var source=event.getSource();
        if(source.is(DamageTypes.LAVA))trigger(player,"lava",event,true);
        else if(source.is(DamageTypes.DROWN))trigger(player,"water",event,true);
        else if(source.is(DamageTypes.FALL))trigger(player,"angel",event,true);
        else if(source.is(DamageTypes.IN_FIRE))trigger(player,"fire",event,true);
        else if(source.is(DamageTypes.PLAYER_ATTACK)||source.is(DamageTypes.MOB_ATTACK)||source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)){trigger(player,"knight",event,true);trigger(player,"warrior",event,true);}
        else if(source.is(DamageTypeTags.IS_PROJECTILE)&&source.getDirectEntity() instanceof Projectile projectile&&!(projectile instanceof ThrownTrident)&&trigger(player,"whirlwind",event,true)) {
            var copy=projectile.getType().create(player.level());if(!(copy instanceof Projectile returned))return;
            var direction=player.getLookAngle().scale(2);var origin=player.getEyePosition().add(direction);returned.moveTo(origin.x,origin.y,origin.z,player.getYRot(),player.getXRot());returned.setOwner(projectile.getOwner());returned.setDeltaMovement(direction);
            if(projectile instanceof AbstractArrow arrow&&returned instanceof AbstractArrow reflected){reflected.setBaseDamage(arrow.getBaseDamage());reflected.pickup=arrow.pickup;reflected.setPierceLevel(arrow.getPierceLevel());}
            if(player.serverLevel().addFreshEntity(returned))projectile.discard();
        }
    }
    public static void sprint(ServerPlayer player){trigger(player,"traveller",null,true);}
    @SubscribeEvent public static void experience(PlayerXpEvent.XpChange event){if(event.getAmount()>0&&event.getEntity() instanceof ServerPlayer p&&trigger(p,"wise",event,true))event.setAmount((int)Math.min(Integer.MAX_VALUE,event.getAmount()*2L));}
    public static void mining(BlockEvent.BreakEvent event){if(event.getPlayer() instanceof ServerPlayer p&&event.getState().is(SpecialTools.tag("caveman_talisman_triggers")))trigger(p,"caveman",event,true);}
    public static List<ItemStack> blockDrops(BlockState state,Entity entity,ItemStack tool,List<ItemStack> drops) {
        if(!(entity instanceof ServerPlayer p)||tool.isEmpty()||tool.getEnchantmentLevel(Enchantments.SILK_TOUCH)>0)return drops;
        String kind=state.is(SpecialTools.tag("miner_talisman_triggers"))?"miner":state.is(SpecialTools.tag("farmer_talisman_triggers"))?"farmer":null;
        if(kind==null||!trigger(p,kind,null,false))return drops;
        int fortune=SpecialTools.fortune(tool,state,p);var result=new ArrayList<>(drops);boolean doubled=false;
        // Plantable crops/seeds are items in RC-37, despite extending BlockItem in Minecraft.
        for(var stack:drops)if(!stack.isEmpty()&&(!(stack.getItem() instanceof BlockItem)||stack.getItem() instanceof ItemNameBlockItem)){result.add(stack.copyWithCount(Math.max(1,fortune*2-stack.getCount())));doubled=true;}
        if(doubled)((TalismanItem)SlimefunForge.item(kind+"_talisman")).message(p);return result;
    }
    public static void mobDrops(LootTable table,LootParams params,long seed,java.util.function.Consumer<ItemStack> consumer) {
        var target=params.getOptionalParameter(LootContextParams.THIS_ENTITY);var killer=params.getOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER);
        if(!(killer instanceof ServerPlayer p)||!(target instanceof Mob mob)||mob.canPickUpLoot()||target instanceof ArmorStand){table.getRandomItems(params,seed,consumer);return;}
        var drops=new ArrayList<ItemStack>();table.getRandomItems(params,seed,drops::add);
        drops.removeIf(ItemStack::isEmpty);
        drops.forEach(consumer);
        if(!drops.isEmpty()&&trigger(p,"hunter",null,true))for(var stack:drops)if(target instanceof Ravager||!stack.is(Items.SADDLE))consumer.accept(stack.copy());
    }
    public static List<EnchantmentInstance> enchant(ServerPlayer player,ItemStack stack,List<EnchantmentInstance> original) {
        if(original.isEmpty())return original;var selected=new LinkedHashMap<Enchantment,Integer>();for(var e:original)selected.put(e.enchantment,e.level);
        var extra=new ArrayList<EnchantmentInstance>();
        for(var enchant:BuiltInRegistries.ENCHANTMENT)if((stack.is(Items.BOOK)&&MagicSettings.ENCHANT_BOOKS.get()||enchant.canEnchant(stack))&&selected.keySet().stream().allMatch(e->e.isCompatibleWith(enchant)))for(int i=1;i<=enchant.getMaxLevel();i++){var levels=MagicSettings.ENCHANTMENT_LEVELS.get(enchant);if(levels!=null&&levels.get(i-1).get())extra.add(new EnchantmentInstance(enchant,i));}
        if(!extra.isEmpty()){var pick=extra.get(player.getRandom().nextInt(extra.size()));if(trigger(player,"magician",null,true))selected.put(pick.enchantment,pick.level);}
        if(!selected.containsKey(Enchantments.SILK_TOUCH)&&Enchantments.BLOCK_FORTUNE.canEnchant(stack)&&trigger(player,"wizard",null,true)) {
            selected.replaceAll((enchant,value)->value>1&&player.getRandom().nextInt(100)<40?value-1:value);selected.put(Enchantments.BLOCK_FORTUNE,player.getRandom().nextInt(3)+3);
        }
        return selected.entrySet().stream().map(e->new EnchantmentInstance(e.getKey(),e.getValue())).toList();
    }
    public static void broken(ServerPlayer player,ItemStack stack) {
        if(stack.isEmpty())return;int slot=-1;
        for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i)==stack){slot=i;break;}
        if(slot<0||!trigger(player,"anvil",null,true))return;
        var repaired=stack.copyWithCount(1);repaired.setDamageValue(0);var data=player.getPersistentData();var pending=data.getList(REPAIRS,10);var tag=new CompoundTag();tag.putInt("Slot",slot);tag.putLong("Due",player.server.overworld().getGameTime()+1);tag.put("Item",repaired.save(new CompoundTag()));pending.add(tag);data.put(REPAIRS,pending);
    }
    @SubscribeEvent public static void repairs(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player)||!player.isAlive())return;var data=player.getPersistentData();if(!data.contains(REPAIRS))return;
        var pending=data.getList(REPAIRS,10);
        for(int i=pending.size()-1;i>=0;i--){var tag=pending.getCompound(i);if(tag.getLong("Due")>player.server.overworld().getGameTime())continue;var stack=ItemStack.of(tag.getCompound("Item"));int slot=tag.getInt("Slot");pending.remove(i);if(slot>=0&&slot<player.getInventory().getContainerSize()&&player.getInventory().getItem(slot).isEmpty())player.getInventory().setItem(slot,stack);else if(!player.getInventory().add(stack))player.drop(stack,false);}
        if(pending.isEmpty())data.remove(REPAIRS);else data.put(REPAIRS,pending);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){var data=event.getOriginal().getPersistentData();if(data.contains(REPAIRS))event.getEntity().getPersistentData().put(REPAIRS,data.get(REPAIRS).copy());}
}
