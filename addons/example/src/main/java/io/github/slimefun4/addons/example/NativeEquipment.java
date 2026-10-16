package io.github.slimefun4.addons.example;

import io.github.slimefun4.forge.api.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.*;

/** Native equipment, actual server settings and portable menu; no concrete core item subclasses. */
final class NativeEquipment {
    private enum Mode { WORKBENCH, METER, WIND }
    private static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.DoubleValue THRUST;
    private static final ForgeConfigSpec.EnumValue<ToolApi.Mining> MINING;
    private static final ForgeConfigSpec.ConfigValue<String> CLIMBABLE;
    static {
        var builder=new ForgeConfigSpec.Builder();
        THRUST=builder.defineInRange("flight-thrust",.45,0,4);
        MINING=builder.defineEnum("initial-mining-mode",ToolApi.Mining.SMELT);
        CLIMBABLE=builder.define("climbable-block-tag","minecraft:mineable/pickaxe",value->value instanceof String text&&ResourceLocation.tryParse(text)!=null&&text.equals(new ResourceLocation(text).toString()));
        CONFIG=builder.build();
    }
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ExampleAddon.ID);
    private static final RegistryObject<Item> JETPACK=ITEMS.register("field_jetpack",()->new Jet(EquipmentApi.FlightMode.JETPACK));
    private static final RegistryObject<Item> JETBOOTS=ITEMS.register("field_jetboots",()->new Jet(EquipmentApi.FlightMode.JETBOOTS));
    private static final RegistryObject<Item> PARACHUTE=ITEMS.register("field_parachute",()->new Wing(EquipmentApi.FlightMode.PARACHUTE));
    private static final RegistryObject<Item> SOLAR=ITEMS.register("field_solar_helmet",Solar::new);
    private static final RegistryObject<Item> BOOTS=ITEMS.register("field_travel_boots",TravelBoots::new);
    private static final RegistryObject<Item> MULTI=ITEMS.register("field_multi_tool",MultiTool::new);
    static void register(IEventBus bus){
        ITEMS.register(bus);ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"sf-example-server.toml");
        for(var item:ITEMS.getEntries())SlimefunAddonApi.registerEntry(new SlimefunAddonApi.Entry(item.getId(),ExampleAddon.key("native")));
        MinecraftForge.EVENT_BUS.addListener(NativeEquipment::trample);
    }
    static ToolApi.Mining initialMining(){return MINING.get();}
    static TagKey<Block> climbable(){return TagKey.create(Registries.BLOCK,new ResourceLocation(CLIMBABLE.get()));}
    private static class Wing extends ArmorItem implements EquipmentApi.FlightGear {
        private final EquipmentApi.FlightMode mode;
        Wing(EquipmentApi.FlightMode mode){super(ArmorMaterials.LEATHER,mode==EquipmentApi.FlightMode.JETBOOTS?Type.BOOTS:Type.CHESTPLATE,new Properties());this.mode=mode;}
        @Override public EquipmentApi.Flight flight(ItemStack stack){return new EquipmentApi.Flight(mode,THRUST.get());}
    }
    private static final class Jet extends Wing implements Rechargeable {
        Jet(EquipmentApi.FlightMode mode){super(mode);}
        @Override public float getMaxItemCharge(ItemStack stack){return 100;}
        @Override public ICapabilityProvider initCapabilities(ItemStack stack,net.minecraft.nbt.CompoundTag tag){return energyCapability(stack);}
    }
    private static final class Solar extends ArmorItem {
        Solar(){super(ArmorMaterials.IRON,Type.HELMET,new Properties());}
        @Override public void onArmorTick(ItemStack stack,Level level,Player player){if(player instanceof ServerPlayer server)EquipmentApi.chargeFromSun(server,stack,.1F);}
    }
    private static final class TravelBoots extends ArmorItem {
        TravelBoots(){super(ArmorMaterials.LEATHER,Type.BOOTS,new Properties());}
        @Override public void onArmorTick(ItemStack stack,Level level,Player player){
            if(!(player instanceof ServerPlayer server)||!server.isAlive()||server.tickCount%10!=0||!SlimefunAddonApi.canUse(server,stack))return;
            server.addEffect(new MobEffectInstance(MobEffects.JUMP,15,0));
            if(server.isSprinting())server.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,15,0));
            if(server.fallDistance>2)server.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,15,0));
        }
    }
    private static void trample(BlockEvent.FarmlandTrampleEvent event){
        if(event.getEntity() instanceof ServerPlayer player){var feet=player.getItemBySlot(EquipmentSlot.FEET);if(feet.is(BOOTS.get())&&SlimefunAddonApi.canUse(player,feet))event.setCanceled(true);}
    }
    private static final class MultiTool extends Item implements Rechargeable {
        MultiTool(){super(new Properties().stacksTo(1));}
        @Override public float getMaxItemCharge(ItemStack stack){return 100;}
        @Override public ICapabilityProvider initCapabilities(ItemStack stack,net.minecraft.nbt.CompoundTag tag){return energyCapability(stack);}
        private Mode mode(ItemStack stack){var tag=stack.getTagElement(ExampleAddon.ID);return Mode.values()[Math.floorMod(tag==null?0:tag.getInt("mode"),Mode.values().length)];}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var stack=player.getItemInHand(hand);if(!(player instanceof ServerPlayer server))return InteractionResultHolder.success(stack);
            if(!SlimefunAddonApi.canUse(server,stack))return InteractionResultHolder.fail(stack);
            if(server.isShiftKeyDown()){
                stack.getOrCreateTagElement(ExampleAddon.ID).putInt("mode",(mode(stack).ordinal()+1)%Mode.values().length);
                server.displayClientMessage(Component.translatable("message.sf_example.multi_mode",mode(stack).name()),true);
            }else if(mode(stack)==Mode.WORKBENCH){
                var dimension=level.dimension();
                server.openMenu(new SimpleMenuProvider((id,inventory,actor)->new CraftingMenu(id,inventory,ContainerLevelAccess.create(level,server.blockPosition())){
                    @Override public boolean stillValid(Player actor){return actor==server&&server.isAlive()&&server.level().dimension()==dimension&&server.getItemInHand(hand)==stack&&SlimefunAddonApi.canUse(server,stack);}
                },Component.translatable("container.crafting")));
            }else if(mode(stack)==Mode.WIND&&removeItemCharge(stack,.3F)){
                server.setDeltaMovement(server.getLookAngle().scale(.5).add(0,.3,0));server.fallDistance=0;server.hurtMarked=true;server.connection.send(new ClientboundSetEntityMotionPacket(server));
            }else return InteractionResultHolder.pass(stack);
            return InteractionResultHolder.consume(stack);
        }
        @Override public InteractionResult useOn(UseOnContext context){
            if(!(context.getPlayer() instanceof ServerPlayer server))return InteractionResult.SUCCESS;
            var stack=context.getItemInHand();if(!SlimefunAddonApi.canUse(server,stack)||!server.serverLevel().mayInteract(server,context.getClickedPos()))return InteractionResult.FAIL;
            if(mode(stack)!=Mode.METER||server.isShiftKeyDown())return use(context.getLevel(),server,context.getHand()).getResult();
            var entity=context.getLevel().getBlockEntity(context.getClickedPos());if(entity==null)return InteractionResult.PASS;
            var power=entity.getCapability(ForgeCapabilities.ENERGY,context.getClickedFace()).resolve();if(power.isEmpty())return InteractionResult.PASS;
            server.displayClientMessage(Component.translatable("message.sf_example.meter",power.get().getEnergyStored(),power.get().getMaxEnergyStored()),false);return InteractionResult.CONSUME;
        }
    }
    private NativeEquipment(){}
}
