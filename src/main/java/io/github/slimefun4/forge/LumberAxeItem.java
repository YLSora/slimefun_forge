package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ToolActions;

public final class LumberAxeItem extends AxeItem {
    public LumberAxeItem(){super(Tiers.DIAMOND,5,-3,new Properties());}
    @Override public InteractionResult useOn(UseOnContext context) {
        var player=context.getPlayer();var origin=context.getClickedPos();var before=context.getLevel().getBlockState(origin);
        if(player instanceof ServerPlayer p&&!ResearchData.canUse(p,context.getItemInHand())){ItemAccess.denied(p,context.getItemInHand());return InteractionResult.FAIL;}
        var result=super.useOn(context);
        if(player instanceof ServerPlayer p&&!p.isShiftKeyDown()&&result.consumesAction()&&SpecialTools.unstripped(before)&&!p.level().getBlockState(origin).equals(before)) {
            for(var pos:SpecialTools.vein(p.serverLevel(),origin,SpecialTools::unstripped,20))if(!pos.equals(origin)) {
                var state=p.level().getBlockState(pos);if(!SpecialTools.permitted(p,pos,state)||SpecialTools.slimefun(state))continue;
                var at=new UseOnContext(p,context.getHand(),new BlockHitResult(Vec3.atCenterOf(pos),context.getClickedFace(),pos,false));
                var stripped=state.getToolModifiedState(at,ToolActions.AXE_STRIP,false);
                if(stripped!=null&&p.level().getBlockState(pos).equals(state)){p.level().setBlockAndUpdate(pos,stripped);p.level().playSound(null,pos,net.minecraft.sounds.SoundEvents.AXE_STRIP,net.minecraft.sounds.SoundSource.BLOCKS,1,1);}
            }
        }
        return result;
    }
}
