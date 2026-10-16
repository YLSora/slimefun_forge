package io.github.slimefun4.forge;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;

public final class MagicGadgetItem extends Item {
    private final String kind;
    public MagicGadgetItem(Catalog.Entry entry){super(new Properties());kind=entry.id();}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){return new InteractionResultHolder<>(activate(player,hand,null),player.getItemInHand(hand));}
    InteractionResult activate(Player player,InteractionHand hand,UseOnContext context) {
        if(!(player instanceof ServerPlayer p))return kind.equals("magic_eye_of_ender")?InteractionResult.SUCCESS:InteractionResult.PASS;
        var stack=p.getItemInHand(hand);
        if(!ResearchData.canUse(p,stack)){ItemAccess.denied(p,stack);return InteractionResult.FAIL;}
        switch(kind) {
            case "staff_elemental_wind" -> {
                if(p.getFoodData().getFoodLevel()<2){Messages.chat(p,Component.translatable("message.slimefun.hungry"));break;}
                if(stack.is(this)&&!p.isCreative()) {
                    var event=new StaffFoodEvent(p,p.getFoodData().getFoodLevel()-2);
                    if(!MinecraftForge.EVENT_BUS.post(event))p.getFoodData().setFoodLevel(event.getFood());
                }
                FlightGadgets.velocity(p,p.getLookAngle().scale(MagicSettings.WIND_POWER.get()));p.fallDistance=0;
                p.playNotifySound(SoundEvents.TNT_PRIMED,SoundSource.PLAYERS,1,1);
                p.serverLevel().sendParticles(ParticleTypes.SMOKE,p.getX(),p.getY(),p.getZ(),1,0,0,0,0);
            }
            case "magic_eye_of_ender" -> {
                for(var type:ArmorItem.Type.values())if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getItemBySlot(type.getSlot()).getItem()).equals(SlimefunForge.key("ender_"+type.getName())))return InteractionResult.SUCCESS;
                var pearl=new ThrownEnderpearl(p.serverLevel(),p);pearl.shootFromRotation(p,p.getXRot(),p.getYRot(),0,1.5F,1);
                if(p.serverLevel().addFreshEntity(pearl))p.playNotifySound(SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,1,1);
            }
            case "grappling_hook" -> {if(context==null)GrapplingHooks.launch(p,hand);}
            default -> throw new IllegalStateException("Unknown magic gadget "+kind);
        }
        return kind.equals("staff_elemental_wind")||kind.equals("grappling_hook")&&context!=null?InteractionResult.PASS:InteractionResult.SUCCESS;
    }
}
