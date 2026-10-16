package io.github.slimefun4.addons.example;

import com.mojang.logging.LogUtils;
import io.github.slimefun4.forge.*;
import io.github.slimefun4.forge.api.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.registries.*;

/** Real native consumers and event listeners; no fake worlds, actors, inventories or duplicate event bus. */
final class NativeEvents {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ExampleAddon.ID);
    private static final RegistryObject<Item> TALISMAN=ITEMS.register("ward_talisman",()->new Ward(false));
    private static final RegistryObject<Item> ENDER=ITEMS.register("ender_ward_talisman",()->new Ward(true));
    private static final RegistryObject<Item> RUNE=ITEMS.register("polishing_rune",PolishingRune::new);
    private static final RegistryObject<Item> MINING=ITEMS.register("field_mining_tool",MiningTool::new);
    private static final RegistryObject<Item> CLIMBING=ITEMS.register("field_climbing_pick",ClimbingPick::new);
    static void register(IEventBus bus){
        ITEMS.register(bus);for(var item:ITEMS.getEntries())SlimefunAddonApi.registerEntry(new SlimefunAddonApi.Entry(item.getId(),ExampleAddon.key("native")));
        GuideApi.registerItem(ENDER.getId(),new GuideApi.ItemOptions(true,null));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,NativeEvents::attack);
        MinecraftForge.EVENT_BUS.addListener(NativeEvents::activate);
        MinecraftForge.EVENT_BUS.addListener(NativeEvents::launch);
        MinecraftForge.EVENT_BUS.addListener(NativeEvents::explode);
        MinecraftForge.EVENT_BUS.addListener(NativeEvents::spawn);
        MinecraftForge.EVENT_BUS.addListener(NativeEvents::click);
        MinecraftForge.EVENT_BUS.addListener(NativeEvents::breaking);
        MinecraftForge.EVENT_BUS.addListener(NativeEvents::placing);
        MinecraftForge.EVENT_BUS.addListener(NativeEvents::commands);
    }
    private static final class Ward extends Item implements MagicApi.Talisman {
        private final boolean ender;
        Ward(boolean ender){super(new Properties());this.ender=ender;}
        @Override public int chance(){return 100;}
        @Override public List<MobEffectInstance> effects(){return List.of(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));}
        @Override public boolean cancelsSource(){return true;}
        @Override public Item enderVariant(){return ender?null:ENDER.get();}
        @Override public Component activationMessage(){return Component.translatable("message.sf_example.ward");}
    }
    private static void attack(LivingAttackEvent event){
        if(event.getEntity() instanceof ServerPlayer player&&event.getSource().is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT))
            MagicApi.trigger(player,TALISMAN.get(),event,true);
    }
    private static void activate(TalismanActivateEvent event){if(event.getTalisman()==TALISMAN.get()||event.getTalisman()==ENDER.get())if(event.getEntity().isCreative())event.setPreventsConsumption(true);}
    private static final class PolishingRune extends Item implements MagicApi.Rune {
        PolishingRune(){super(new Properties());}
        @Override public Optional<ItemStack> transform(ServerLevel level,ItemStack stack){
            if(!stack.isDamageableItem()||stack.getDamageValue()==0)return Optional.empty();stack.setDamageValue(Math.max(0,stack.getDamageValue()-25));return Optional.of(stack);
        }
        @Override public boolean onEntityItemUpdate(ItemStack stack,ItemEntity entity){return MagicApi.tickRune(stack,entity);}
    }
    private static final class MiningTool extends PickaxeItem implements ToolApi.MiningTool {
        MiningTool(){super(Tiers.DIAMOND,1,-2.8F,new Properties());}
        @Override public ToolApi.Mining mining(ItemStack stack){var data=stack.getTagElement(ExampleAddon.ID);return data==null||!data.contains("mode")?NativeEquipment.initialMining():ToolApi.Mining.values()[Math.floorMod(data.getInt("mode"),ToolApi.Mining.values().length)];}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var held=player.getItemInHand(hand);if(player instanceof ServerPlayer server){if(!SlimefunAddonApi.canUse(server,held))return InteractionResultHolder.fail(held);held.getOrCreateTagElement(ExampleAddon.ID).putInt("mode",(mining(held).ordinal()+1)%ToolApi.Mining.values().length);server.displayClientMessage(Component.literal(mining(held).name()),true);}return InteractionResultHolder.sidedSuccess(held,level.isClientSide);
        }
    }
    private static final class ClimbingPick extends PickaxeItem implements ToolApi.ClimbingPick {
        ClimbingPick(){super(Tiers.IRON,1,-2.8F,new Properties());}
        @Override public double launchPower(ItemStack stack,BlockState surface){return surface.is(NativeEquipment.climbable())?1:0;}
        @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return ToolApi.launch(stack,context);}
    }
    private static void launch(ClimbingPickLaunchEvent event){if(event.getItem().is(CLIMBING.get()))event.setVelocity(event.getVelocity().scale(.8));}
    private static void explode(ExplosiveToolBreakBlocksEvent event){if(event.getTool().is(MINING.get()))event.setAdditionalBlocks(event.getAdditionalBlocks().stream().filter(pos->pos.getY()>=event.getOrigin().getY()).toList());}
    private static void spawn(ItemSpawnEvent event){if(event.getItem().is(ExampleAddon.IRON_TOKEN.get()))LogUtils.getLogger().debug("SF_EXAMPLE_SPAWN reason={} position={}",event.getReason(),event.getPosition());}
    private static void click(PlayerInteractEvent.RightClickBlock event){
        if(event.getLevel() instanceof ServerLevel level&&level.getBlockState(event.getPos()).is(NativeBlocks.VAULT.get())&&event.getHand()==InteractionHand.OFF_HAND)event.setUseBlock(Event.Result.DENY);
    }
    private static void breaking(BlockEvent.BreakEvent event){
        if(event.getLevel() instanceof ServerLevel level&&level.getBlockState(event.getPos()).is(NativeBlocks.VAULT.get())&&event.getPlayer().isShiftKeyDown())event.setCanceled(true);
    }
    private static void placing(BlockEvent.EntityPlaceEvent event){
        if(event.getLevel() instanceof ServerLevel level){var identity=ItemApi.placedItem(level,event.getPos());if(identity.is(NativeBlocks.VAULT_ITEM.get()))LogUtils.getLogger().debug("SF_EXAMPLE_PLACE_CANDIDATE identity={} position={}",ItemApi.snapshot(identity),event.getPos());}
    }
    private static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("sf_example").then(Commands.literal("diagnostics").requires(source->source.hasPermission(2))
            .executes(context->{var source=context.getSource();var report=DiagnosticApi.latest(source.getServer());source.sendSuccess(()->Component.literal(report.map(sample->"tick="+sample.tick()+" "+DiagnosticApi.summaries(sample,Timings.Group.ADDON,Timings.Order.HIGH)).orElse("not sampled")),false);return report.isPresent()?1:0;})
            .then(Commands.literal("sample").executes(context->{var source=context.getSource();DiagnosticApi.sample(source.getServer()).whenComplete((sample,error)->{if(error==null)source.sendSuccess(()->Component.literal(DiagnosticApi.summaries(sample,Timings.Group.ADDON,Timings.Order.HIGH).toString()),false);});return 1;}))));
    }
    private NativeEvents(){}
}
