package io.github.slimefun4.forge;

import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

public final class SpecialPickaxeItem extends PickaxeItem implements io.github.slimefun4.forge.api.ToolApi.ClimbingPick {
    private final String kind;
    public SpecialPickaxeItem(Catalog.Entry entry){super(entry.material().startsWith("iron_")?Tiers.IRON:Tiers.DIAMOND,1,-2.8F,new Properties());kind=entry.id();}
    private boolean allowed(ServerPlayer p,ItemStack stack){if(ResearchData.canUse(p,stack))return true;ItemAccess.denied(p,stack);return false;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);if(!kind.equals("pickaxe_of_the_seeker"))return super.use(level,player,hand);
        if(player instanceof ServerPlayer p) {
            if(!allowed(p,stack))return InteractionResultHolder.fail(stack);
            var center=p.blockPosition();BlockPos nearest=null;double best=Double.MAX_VALUE;
            for(int x=-5;x<=5;x++)for(int y=-5;y<=5;y++)for(int z=-5;z<=5;z++) {
                var pos=center.offset(x,y,z);double distance=pos.distSqr(center);
                if(distance<best&&SpecialTools.available(p.serverLevel(),pos)&&level.getBlockState(pos).is(SpecialTools.tag("pickaxe_of_the_seeker_blocks"))){nearest=pos;best=distance;}
            }
            if(nearest==null)Messages.chat(p,Component.translatable("message.slimefun.seeker_no_ores"));
            else {
                double x=nearest.getX()+.5-p.getX(),z=nearest.getZ()+.5-p.getZ(),y=nearest.getY()-.5-p.getY();
                float yaw=(float)-Math.toDegrees(Math.atan2(x,z)),pitch=(float)-Math.toDegrees(Math.atan2(y,Math.hypot(x,z)));
                p.connection.teleport(p.getX(),p.getY(),p.getZ(),yaw,pitch);p.connection.resetPosition();
            }
            EquipmentDamage.damage(p,stack,hand==InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND,false);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context) {
        if(context.getPlayer()==null)return InteractionResult.PASS;
        if(kind.equals("pickaxe_of_the_seeker"))return use(context.getLevel(),context.getPlayer(),context.getHand()).getResult();
        if(!kind.equals("climbing_pick"))return InteractionResult.PASS;
        return io.github.slimefun4.forge.api.ToolApi.launch(stack,context);
    }
    @Override public double launchPower(ItemStack stack,net.minecraft.world.level.block.state.BlockState state){double power=state.is(SpecialTools.tag("climbing_pick_strong_surfaces"))?1:state.is(SpecialTools.tag("climbing_pick_weak_surfaces"))?.6:0;return power==0?0:power+stack.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY)*.125;}
}
