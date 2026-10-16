package io.github.slimefun4.addons.example;

import com.mojang.logging.LogUtils;
import io.github.slimefun4.forge.api.*;
import net.minecraft.core.*;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.level.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import static net.minecraft.commands.Commands.literal;

/** Native events observe or modify their own transaction; they never repeat native drops. */
final class NativeHooks {
    static void register(IEventBus bus){
        bus.addListener(NativeHooks::setup);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,NativeHooks::impact);
        MinecraftForge.EVENT_BUS.addListener(NativeHooks::explosion);
        MinecraftForge.EVENT_BUS.addListener(NativeHooks::commands);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,NativeHooks::formed);
    }
    private static void setup(FMLCommonSetupEvent event){event.enqueueWork(()->DispenserBlock.registerBehavior(NativeItems.FLARE.get(),new DefaultDispenseItemBehavior(){
        @Override protected ItemStack execute(BlockSource source,ItemStack stack){
            if(!ItemApi.enabled(source.getLevel(),stack))return stack;
            var direction=source.getBlockState().getValue(DispenserBlock.FACING);var pos=DispenserBlock.getDispensePosition(source);
            var rocket=new FireworkRocketEntity(source.getLevel(),NativeItems.SignalFlare.rocket(),pos.x(),pos.y(),pos.z(),true);
            rocket.shoot(direction.getStepX(),direction.getStepY(),direction.getStepZ(),.5F,1);
            if(source.getLevel().addFreshEntity(rocket))stack.shrink(1);return stack;
        }
    }));}
    private static void impact(ProjectileImpactEvent event){
        var arrow=event.getProjectile();if(arrow.level().isClientSide||event.getImpactResult()!=ProjectileImpactEvent.ImpactResult.DEFAULT)return;
        var data=arrow.getPersistentData();
        if(!data.getString("sf_example_weapon").equals(ExampleAddon.key("signal_bow").toString())||!data.hasUUID("sf_example_shooter")||arrow.getOwner()==null||!arrow.getOwner().getUUID().equals(data.getUUID("sf_example_shooter")))return;
        LogUtils.getLogger().debug("SF_EXAMPLE_ARROW owner={} hit={}",data.getUUID("sf_example_shooter"),event.getRayTraceResult().getType());
    }
    private static void explosion(ExplosionEvent.Detonate event){
        var source=event.getExplosion().getDirectSourceEntity();if(!(source instanceof WitherBoss||source instanceof WitherSkull))return;
        event.getAffectedBlocks().removeIf(pos->event.getLevel().hasChunkAt(pos)&&event.getLevel().getBlockState(pos).is(NativeBlocks.WITHER_TILE.get()));
    }
    private static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(literal("sf_example").then(literal("interact")
            .executes(context->interact(context.getSource().getPlayerOrException(),false))
            .then(literal("preview").executes(context->interact(context.getSource().getPlayerOrException(),true)))));
    }
    private static int interact(net.minecraft.server.level.ServerPlayer player,boolean simulate){
        if(!(player.pick(6,0,false) instanceof BlockHitResult hit))return 0;
        var result=ItemApi.interact(player,hit.getBlockPos(),simulate);
        if(result.success()&&!simulate)for(var stack:result.outputs())ItemHandlerHelper.giveItemToPlayer(player,stack);
        player.displayClientMessage(result.message(),false);return result.success()?1:0;
    }
    private static void formed(BlockEvent.FluidPlaceBlockEvent event){
        if(!(event.getLevel() instanceof ServerLevel))return;
        var block=event.getNewState().getBlock();
        if(block==Blocks.STONE||block==Blocks.COBBLESTONE||block==Blocks.BASALT)LogUtils.getLogger().debug("SF_EXAMPLE_FORM pos={} block={}",event.getPos(),block);
    }
    /** Inspect actual blocks/fluids without mining, loading chunks or claiming a generation occurred. */
    static Component generator(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos))return Component.translatable("message.sf_example.generator",false);
        boolean water=false,lava=false,ice=false;
        for(var direction:Direction.Plane.HORIZONTAL){
            var next=pos.relative(direction);if(!level.hasChunkAt(next))continue;
            water|=level.getFluidState(next).is(FluidTags.WATER);lava|=level.getFluidState(next).is(FluidTags.LAVA);ice|=level.getBlockState(next).is(Blocks.BLUE_ICE);
        }
        var state=level.getBlockState(pos);
        boolean basalt=state.is(Blocks.BASALT)&&ice&&lava&&level.hasChunkAt(pos.below())&&level.getBlockState(pos.below()).is(Blocks.SOUL_SOIL);
        boolean stone=state.is(Blocks.STONE)&&water&&level.hasChunkAt(pos.above())&&level.getFluidState(pos.above()).is(FluidTags.LAVA);
        return Component.translatable("message.sf_example.generator",basalt||stone||state.is(Blocks.COBBLESTONE)&&water&&lava);
    }
    private NativeHooks(){}
}
