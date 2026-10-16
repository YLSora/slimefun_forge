package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.ToolApi.ClimbingPick;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

public final class NativeTools {
    private static final Set<ServerPlayer> LAUNCHING=Collections.newSetFromMap(new WeakHashMap<>());
    public static InteractionResult launch(ItemStack stack,UseOnContext context){
        if(!(stack.getItem() instanceof ClimbingPick pick)||context.getPlayer()==null)return InteractionResult.PASS;
        if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.SUCCESS;
        if(!player.server.isSameThread())throw new IllegalStateException("Climbing launch requires server thread");
        var level=player.serverLevel();var pos=context.getClickedPos().immutable();var hand=context.getHand();var other=hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
        if(!SpecialTools.available(level,pos)||context.getClickedFace().getAxis()==Direction.Axis.Y||player.position().distanceToSqr(Vec3.atCenterOf(pos))>4.4||!level.mayInteract(player,pos))return InteractionResult.PASS;
        var second=player.getItemInHand(other);
        if(!second.is(stack.getItem())||!ResearchData.canUse(player,stack)||!ResearchData.canUse(player,second))return InteractionResult.FAIL;
        if(player.getCooldowns().isOnCooldown(stack.getItem())||!LAUNCHING.add(player))return InteractionResult.SUCCESS;
        try{
            var before=stack.copy();var secondBefore=second.copy();var state=level.getBlockState(pos);double power=pick.launchPower(stack.copy(),state);
            if(!Double.isFinite(power)||power<0)throw new IllegalArgumentException("Invalid climbing power");if(power<=.05)return InteractionResult.PASS;
            var event=new ClimbingPickLaunchEvent(player,stack,pos,new Vec3(0,Math.min(4,power),0));
            if(MinecraftForge.EVENT_BUS.post(event)||!player.isAlive()||player.isSpectator()||player.server.getPlayerList().getPlayer(player.getUUID())!=player||player.serverLevel()!=level
                ||player.getItemInHand(hand)!=stack||player.getItemInHand(other)!=second||!ItemStack.matches(before,stack)||!ItemStack.matches(secondBefore,second)
                ||!SpecialTools.available(level,pos)||!level.getBlockState(pos).equals(state)||!level.mayInteract(player,pos)||player.position().distanceToSqr(Vec3.atCenterOf(pos))>4.4
                ||!ResearchData.canUse(player,stack)||!ResearchData.canUse(player,second))return InteractionResult.SUCCESS;
            player.getCooldowns().addCooldown(stack.getItem(),4);FlightGadgets.velocity(player,event.getVelocity());player.fallDistance=0;
            var chosen=player.getRandom().nextBoolean()?hand:other;
            if(!player.isCreative())EquipmentDamage.damage(player,player.getItemInHand(chosen),chosen==InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND,false);
            player.swing(chosen,true);level.levelEvent(2001,pos,net.minecraft.world.level.block.Block.getId(state));return InteractionResult.SUCCESS;
        }finally{LAUNCHING.remove(player);}
    }
    private NativeTools(){}
}
