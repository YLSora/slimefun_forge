package io.github.slimefun4.addons.example;

import io.github.slimefun4.forge.api.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.effect.*;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.*;

/** Native extension examples: no core implementation subclasses or parallel handler registry. */
final class NativeItems {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ExampleAddon.ID);
    static final RegistryObject<Item> BATTERY=ITEMS.register("battery",Battery::new);
    static final RegistryObject<Item> COMPONENT=ITEMS.register("colored_component",ColoredComponent::new);
    static final RegistryObject<Item> ISOTOPE=ITEMS.register("isotope",Isotope::new);
    static final RegistryObject<Item> KEEPSAKE=ITEMS.register("keepsake",Keepsake::new);
    static final RegistryObject<Item> RATION=ITEMS.register("field_ration",Ration::new);
    static final RegistryObject<Item> BANDAGE=ITEMS.register("field_bandage",Bandage::new);
    static final RegistryObject<Item> TOOL=ITEMS.register("field_pickaxe",FieldPickaxe::new);
    static final RegistryObject<Item> SWORD=ITEMS.register("field_sword",FieldSword::new);
    static final RegistryObject<Item> BOW=ITEMS.register("signal_bow",SignalBow::new);
    static final RegistryObject<Item> FORK=ITEMS.register("tuning_fork",TuningFork::new);
    static final RegistryObject<Item> FLARE=ITEMS.register("signal_flare",SignalFlare::new);
    static final List<RegistryObject<ProtectivePiece>> ARMOR=Arrays.stream(ArmorItem.Type.values()).map(type->ITEMS.register("protective_"+type.getName(),()->new ProtectivePiece(type))).toList();
    static void register(IEventBus bus){
        ITEMS.register(bus);
        SlimefunAddonApi.registerCategory(new SlimefunAddonApi.Category(ExampleAddon.key("native"),"category.sf_example.native",ExampleAddon.key("battery")));
        GuideApi.registerCategory(ExampleAddon.key("native"),new GuideApi.CategoryOptions(ExampleAddon.key("materials"),5));
        for(var item:ITEMS.getEntries())SlimefunAddonApi.registerEntry(new SlimefunAddonApi.Entry(item.getId(),ExampleAddon.key("native")));
    }
    static CompoundTag data(ItemStack stack){return stack.getOrCreateTagElement(ExampleAddon.ID);}
    static int color(ItemStack stack){var data=stack.getTagElement(ExampleAddon.ID);return data==null?0:data.getInt("color");}
    private static boolean permitted(Player player,ItemStack stack){return player instanceof ServerPlayer server&&SlimefunAddonApi.canUse(server,stack);}
    static final class Battery extends Item implements Rechargeable,ItemApi.FixedSettings {
        Battery(){super(new Properties().stacksTo(1));}
        @Override public float getMaxItemCharge(ItemStack stack){return 100;}
        @Override public ICapabilityProvider initCapabilities(ItemStack stack,CompoundTag tag){return energyCapability(stack);}
        @Override public String fixedSettingsReason(){return "The sealed cell uses native charge and cannot be enchanted or disenchanted.";}
        @Override public boolean allowEnchanting(ItemStack stack){return false;}
        @Override public boolean allowDisenchanting(ItemStack stack){return false;}
        @Override public boolean isEnchantable(ItemStack stack){return false;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var stack=player.getItemInHand(hand);if(level.isClientSide)return InteractionResultHolder.success(stack);
            if(!permitted(player,stack)||!removeItemCharge(stack,2.5F))return InteractionResultHolder.fail(stack);
            level.playSound(null,player.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,1,1);return InteractionResultHolder.consume(stack);
        }
    }
    static final class ColoredComponent extends Item implements ItemApi.Distinctive {
        ColoredComponent(){super(new Properties());}
        @Override public boolean matches(ItemStack item,ItemStack other,ItemApi.Match purpose){return color(item)==color(other);}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var stack=player.getItemInHand(hand);if(level.isClientSide)return InteractionResultHolder.success(stack);
            if(!permitted(player,stack))return InteractionResultHolder.fail(stack);
            if(player.isShiftKeyDown())data(stack).putInt("color",(color(stack)+1)%16);
            player.displayClientMessage(Component.literal(ItemApi.snapshot(stack).toString()),false);return InteractionResultHolder.consume(stack);
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("tooltip.sf_example.component",DyeColor.byId(color(stack)).getName()));}
    }
    static final class Isotope extends Item implements ItemApi.Radioactive {
        Isotope(){super(new Properties());}
        @Override public int radiation(ItemStack stack){return 2;}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("tooltip.sf_example.radiation",radiation(stack)));}
    }
    static final class Keepsake extends Item implements ItemApi.Soulbound {
        Keepsake(){super(new Properties().stacksTo(1));}
        @Override public boolean onDroppedByPlayer(ItemStack stack,Player player){return !player.isShiftKeyDown();}
    }
    static final class ProtectivePiece extends DyeableArmorItem implements ItemApi.ProtectiveArmor {
        ProtectivePiece(Type type){super(ArmorMaterials.LEATHER,type,new Properties());}
        @Override public ItemApi.ArmorProtection protection(ItemStack stack){return new ItemApi.ArmorProtection(Set.of(ItemApi.Protection.RADIATION,ItemApi.Protection.BEES,ItemApi.Protection.FLIGHT_IMPACT),ExampleAddon.key("protective"),true);}
        @Override public void onArmorTick(ItemStack stack,Level level,Player player){
            if(!(player instanceof ServerPlayer server)||!server.isAlive()||!SlimefunAddonApi.canUse(server,stack))return;
            if(server.tickCount%60==0)setColor(stack,DyeColor.byId(server.tickCount/60%16).getFireworkColor());
            if(getType()==Type.BOOTS&&server.tickCount%20==0)server.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,25,0));
        }
    }
    static final class Ration extends Item {
        Ration(){super(new Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(4).saturationMod(.6F).build()));}
        @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity user){
            if(user instanceof ServerPlayer server&&!SlimefunAddonApi.canUse(server,stack))return stack;
            int before=stack.getCount();var result=super.finishUsingItem(stack,level,user);
            if(user instanceof ServerPlayer server&&result.getCount()<before){server.addEffect(new MobEffectInstance(MobEffects.REGENERATION,60,0));if(result.isEmpty())return new ItemStack(Items.BOWL);ItemHandlerHelper.giveItemToPlayer(server,new ItemStack(Items.BOWL));}
            return result;
        }
    }
    static final class Bandage extends Item {
        Bandage(){super(new Properties());}
        @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.EAT;}
        @Override public int getUseDuration(ItemStack stack){return 32;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){var stack=player.getItemInHand(hand);if(!level.isClientSide&&!permitted(player,stack))return InteractionResultHolder.fail(stack);if(player.getHealth()>=player.getMaxHealth())return InteractionResultHolder.pass(stack);player.startUsingItem(hand);return InteractionResultHolder.consume(stack);}
        @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity user){if(user instanceof ServerPlayer server&&SlimefunAddonApi.canUse(server,stack)&&server.getHealth()<server.getMaxHealth()){server.heal(4);server.removeEffect(MobEffects.POISON);if(!server.isCreative())stack.shrink(1);}return stack;}
    }
    static final class FieldPickaxe extends PickaxeItem {
        FieldPickaxe(){super(Tiers.IRON,1,-2.8F,new Properties().durability(64));}
        @Override public boolean mineBlock(ItemStack stack,Level level,BlockState state,BlockPos pos,LivingEntity user){boolean result=super.mineBlock(stack,level,state,pos,user);if(user instanceof ServerPlayer server&&SlimefunAddonApi.canUse(server,stack))data(stack).putInt("mined",Math.addExact(data(stack).getInt("mined"),1));return result;}
    }
    static final class FieldSword extends SwordItem {
        FieldSword(){super(Tiers.IRON,3,-2.4F,new Properties().durability(64));}
        @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){boolean result=super.hurtEnemy(stack,target,attacker);if(attacker instanceof ServerPlayer server&&SlimefunAddonApi.canUse(server,stack))data(stack).putInt("hits",Math.addExact(data(stack).getInt("hits"),1));return result;}
    }
    static final class SignalBow extends BowItem {
        SignalBow(){super(new Properties().durability(128));}
        @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity user,int remaining){if(level.isClientSide||user instanceof ServerPlayer server&&SlimefunAddonApi.canUse(server,stack))super.releaseUsing(stack,level,user,remaining);}
        @Override public AbstractArrow customArrow(AbstractArrow arrow){arrow.getPersistentData().putString("sf_example_weapon",ExampleAddon.key("signal_bow").toString());if(arrow.getOwner()!=null)arrow.getPersistentData().putUUID("sf_example_shooter",arrow.getOwner().getUUID());return arrow;}
    }
    static final class TuningFork extends Item {
        TuningFork(){super(new Properties().stacksTo(1));}
        @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context){
            if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
            if(!(context.getPlayer() instanceof ServerPlayer player)||!permitted(player,context.getItemInHand())||!player.serverLevel().mayInteract(player,context.getClickedPos()))return InteractionResult.FAIL;
            player.displayClientMessage(NativeHooks.generator(player.serverLevel(),context.getClickedPos()),false);return InteractionResult.CONSUME;
        }
        @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;if(player.level().isClientSide)return InteractionResult.SUCCESS;if(!permitted(player,stack))return InteractionResult.FAIL;
            target.playSound(SoundEvents.AMETHYST_BLOCK_CHIME,1,1);return InteractionResult.CONSUME;
        }
    }
    static final class SignalFlare extends Item {
        SignalFlare(){super(new Properties().stacksTo(16));}
        static ItemStack rocket(){
            var rocket=new ItemStack(Items.FIREWORK_ROCKET);var fireworks=rocket.getOrCreateTagElement("Fireworks");fireworks.putByte("Flight",(byte)1);
            var explosion=new CompoundTag();explosion.putByte("Type",(byte)0);explosion.putIntArray("Colors",new int[]{DyeColor.BLUE.getFireworkColor()});var explosions=new ListTag();explosions.add(explosion);fireworks.put("Explosions",explosions);
            return rocket;
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var held=player.getItemInHand(hand);if(level.isClientSide)return InteractionResultHolder.success(held);if(!permitted(player,held))return InteractionResultHolder.fail(held);
            var entity=new FireworkRocketEntity(level,player.getX(),player.getY()+1,player.getZ(),rocket());entity.setOwner(player);
            if(!level.addFreshEntity(entity))return InteractionResultHolder.fail(held);if(!player.isCreative())held.shrink(1);return InteractionResultHolder.consume(held);
        }
    }
    private NativeItems(){}
}
