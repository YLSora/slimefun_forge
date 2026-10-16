package io.github.slimefun4.addons.example;

import io.github.slimefun4.forge.api.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import static io.github.slimefun4.addons.example.ExampleAddon.key;
import static net.minecraft.commands.Commands.*;

/** Real carried items, persistent status consumer and authorized UUID administration. */
final class NativePlayers {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ExampleAddon.ID);
    static final RegistryObject<Item> BAG=ITEMS.register("field_backpack",()->new Bag(2));
    static final RegistryObject<Item> SOUL=ITEMS.register("bound_backpack",SoulBag::new);
    static final RegistryObject<Item> COOLER=ITEMS.register("field_cooler",Cooler::new);
    static final RegistryObject<Item> ENDER=ITEMS.register("field_enderpack",Ender::new);
    static final RegistryObject<Item> PATCH=ITEMS.register("recovery_patch",Patch::new);
    static void register(IEventBus bus){
        ITEMS.register(bus);for(var item:ITEMS.getEntries())SlimefunAddonApi.registerEntry(new SlimefunAddonApi.Entry(item.getId(),key("native")));
        MinecraftForge.EVENT_BUS.addListener(NativePlayers::commands);MinecraftForge.EVENT_BUS.addListener(NativePlayers::tick);
        MinecraftForge.EVENT_BUS.addListener((AsyncProfileLoadEvent event)->{var nativeData=event.getProfile().nativeData();nativeData.putBoolean("sf_example_readonly_view",true);event.setProfile(event.getProfile().withNative(nativeData));});
    }
    private static class Bag extends Item implements BackpackApi.Bag {
        private final int rows;Bag(int rows){super(new Properties().stacksTo(1));this.rows=rows;}
        @Override public int rows(ItemStack stack){return rows;}
        @Override public boolean canFitInsideContainerItems(){return false;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){var stack=player.getItemInHand(hand);return level.isClientSide||player instanceof ServerPlayer server&&BackpackApi.open(server,hand)?InteractionResultHolder.sidedSuccess(stack,level.isClientSide):InteractionResultHolder.fail(stack);}
    }
    private static final class SoulBag extends Bag implements ItemApi.Soulbound {SoulBag(){super(3);}}
    private static final class Cooler extends Bag implements BackpackApi.Cooling {
        Cooler(){super(3);}
        @Override public boolean accepts(ItemStack stack){return stack.is(Items.POTION);}
        @Override public boolean canFeed(ItemStack drink){return drink.is(Items.POTION);}
        @Override public List<MobEffectInstance> effects(ItemStack drink){return net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(drink);}
        @Override public ItemStack remainder(ItemStack drink){return new ItemStack(Items.GLASS_BOTTLE);}
    }
    private static final class Ender extends Item {
        Ender(){super(new Properties().stacksTo(1));}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){var stack=player.getItemInHand(hand);return level.isClientSide||player instanceof ServerPlayer server&&BackpackApi.openEnder(server,hand)?InteractionResultHolder.sidedSuccess(stack,level.isClientSide):InteractionResultHolder.fail(stack);}
    }
    private static final class Patch extends Item {
        Patch(){super(new Properties());}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var stack=player.getItemInHand(hand);if(level.isClientSide)return InteractionResultHolder.success(stack);
            if(!(player instanceof ServerPlayer server)||!SlimefunAddonApi.canUse(server,stack)||PlayerApi.present(server.server,server.getUUID(),key("recovery")))return InteractionResultHolder.fail(stack);
            PlayerApi.status(server.server,server.getUUID(),key("recovery"),1,60,TimeUnit.SECONDS);PlayerApi.addStatistic(server.server,server.getUUID(),key("patches"),1);PlayerApi.title(server.server,server.getUUID(),key("field_medic"),true);
            PlayerApi.edit(server.server,server.getUUID(),key("medical"),data->data.putLong("last_patch",System.currentTimeMillis()));if(!server.isCreative())stack.shrink(1);return InteractionResultHolder.consume(stack);
        }
    }
    private static void tick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer player&&player.isAlive()&&player.tickCount%20==0&&SlimefunAddonApi.canUse(player,new ItemStack(PATCH.get()))&&PlayerApi.present(player.server,player.getUUID(),key("recovery")))player.heal(.5F*Math.max(0,PlayerApi.level(player.server,player.getUUID(),key("recovery")).orElse(0)));}
    private static int profile(net.minecraft.commands.CommandSourceStack source,UUID owner){
        PlayerApi.request(source.getServer(),owner).whenComplete((result,error)->{
            if(!source.getServer().isRunning())return;if(error!=null){source.sendFailure(Component.literal("Profile load failed: "+error));return;}
            source.sendSuccess(()->Component.literal(result.map(p->"owner="+p.owner()+"; online="+p.online()+"; statuses="+p.statuses()+"; bags="+p.backpacks()+"; waypoints="+p.waypoints().size()+"; titles="+p.titles()+"; statistics="+p.statistics()+"; armor="+p.armor().hashCode()+"; replaced_view="+p.nativeData().getBoolean("sf_example_readonly_view")).orElse("No profile for "+owner)),false);
        });return 1;
    }
    private static void commands(RegisterCommandsEvent event){
        var root=literal("sf_example");var profile=literal("profile").executes(c->profile(c.getSource(),c.getSource().getPlayerOrException().getUUID()));
        profile.then(literal("armor").executes(c->{var player=c.getSource().getPlayerOrException();var snapshot=PlayerApi.armor(player);c.getSource().sendSuccess(()->Component.literal("armor="+snapshot.hashCode()+"; diverged="+snapshot.diverged(player)),false);return 1;}));
        profile.then(literal("players").requires(s->s.hasPermission(2)).executes(c->{var source=c.getSource();PlayerApi.players(source.getServer()).whenComplete((ids,error)->{if(source.getServer().isRunning()){if(error!=null)source.sendFailure(Component.literal(error.toString()));else source.sendSuccess(()->Component.literal(ids.toString()),false);}});return 1;}));
        var owner=argument("uuid",net.minecraft.commands.arguments.UuidArgument.uuid()).executes(c->profile(c.getSource(),net.minecraft.commands.arguments.UuidArgument.getUuid(c,"uuid")));
        owner.then(literal("delete").executes(c->PlayerApi.delete(c.getSource().getServer(),net.minecraft.commands.arguments.UuidArgument.getUuid(c,"uuid"))?1:0));
        owner.then(literal("grant").executes(c->ResearchApi.grant(c.getSource().getServer(),net.minecraft.commands.arguments.UuidArgument.getUuid(c,"uuid"),key("compression"))?1:0));
        owner.then(literal("backpack").then(argument("id",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(c->{var value=BackpackApi.snapshot(c.getSource().getServer(),net.minecraft.commands.arguments.UuidArgument.getUuid(c,"uuid"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"id"));c.getSource().sendSuccess(()->Component.literal(value.toString()),false);return value.isPresent()?1:0;})
            .then(literal("expand").then(argument("rows",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,6)).executes(c->{BackpackApi.expand(c.getSource().getServer(),net.minecraft.commands.arguments.UuidArgument.getUuid(c,"uuid"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"id"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"rows"));return 1;})))
            .then(literal("clear").executes(c->{BackpackApi.edit(c.getSource().getServer(),net.minecraft.commands.arguments.UuidArgument.getUuid(c,"uuid"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"id"),items->Collections.fill(items,ItemStack.EMPTY));return 1;}))));
        profile.then(literal("owner").requires(s->s.hasPermission(2)).then(owner));root.then(profile);event.getDispatcher().register(root);
    }
    private NativePlayers(){}
}
