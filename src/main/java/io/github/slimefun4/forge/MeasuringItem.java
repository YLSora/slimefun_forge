package io.github.slimefun4.forge;

import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class MeasuringItem extends Item {
    private final boolean tape;
    public MeasuringItem(Catalog.Entry entry){super(new Properties());tape=entry.id().equals("tape_measure");}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){var stack=player.getItemInHand(hand);if(player instanceof ServerPlayer p&&!ResearchData.canUse(p,stack)){ItemAccess.denied(p,stack);return InteractionResultHolder.fail(stack);}return tape?InteractionResultHolder.success(stack):InteractionResultHolder.pass(stack);}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context) {
        if(!(context.getPlayer() instanceof ServerPlayer p))return tape?InteractionResult.SUCCESS:InteractionResult.PASS;
        if(!ResearchData.canUse(p,stack)){ItemAccess.denied(p,stack);return InteractionResult.PASS;}
        var pos=context.getClickedPos();if(!p.level().mayInteract(p,pos))return InteractionResult.FAIL;
        if(!tape) {
            var block=p.level().getBlockEntity(pos);if(block==null)return InteractionResult.PASS;
            var power=block.getCapability(ForgeCapabilities.ENERGY).resolve();if(power.isEmpty())return InteractionResult.PASS;
            p.sendSystemMessage(Component.empty());
            Messages.send(p,Component.translatable("message.slimefun.multimeter",energy(power.get().getEnergyStored()),energy(power.get().getMaxEnergyStored())),false,false);
            p.sendSystemMessage(Component.empty());return InteractionResult.SUCCESS;
        }
        var world=WorldId.get(p.serverLevel());
        if(p.isShiftKeyDown()) {
            var anchor=new CompoundTag();anchor.putUUID("world",world);anchor.putLong("pos",pos.asLong());stack.getOrCreateTag().put("slimefun_measure_anchor",anchor);
            Messages.chat(p,Component.translatable("message.slimefun.tape_anchor",pos.getX()+" | "+pos.getY()+" | "+pos.getZ()));
        } else {
            var tag=stack.getTag();var anchor=tag==null?new CompoundTag():tag.getCompound("slimefun_measure_anchor");
            if(!anchor.hasUUID("world")||!anchor.contains("pos",Tag.TAG_LONG))Messages.chat(p,Component.translatable("message.slimefun.tape_no_anchor"));
            else if(!anchor.getUUID("world").equals(world))Messages.chat(p,Component.translatable("message.slimefun.tape_wrong_world"));
            else {
                var at=net.minecraft.core.BlockPos.of(anchor.getLong("pos"));double distance=Math.sqrt(pos.distSqr(at));
                Messages.chat(p,Component.translatable("message.slimefun.tape_distance",new java.text.DecimalFormat("##.###",java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT)).format(distance)));
                p.serverLevel().playSound(null,pos,net.minecraft.sounds.SoundEvents.BOOK_PUT,net.minecraft.sounds.SoundSource.BLOCKS,1,.7F);
            }
        }
        return InteractionResult.SUCCESS;
    }
    private static String energy(int value){int unit=value>=1000000000?3:value>=1000000?2:value>=1000?1:0;return new java.text.DecimalFormat("#.##",java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT)).format(value/Math.pow(1000,unit))+new String[]{"","K","M","B"}[unit]+" J";}
    private static final class WorldId extends SavedData {
        private final UUID id;
        private WorldId(){id=UUID.randomUUID();setDirty();}
        private WorldId(CompoundTag tag){if(!tag.hasUUID("id"))throw new IllegalArgumentException("Invalid measuring world identity");id=tag.getUUID("id");}
        static UUID get(ServerLevel level){return level.getDataStorage().computeIfAbsent(WorldId::new,WorldId::new,"slimefun_measure_world").id;}
        @Override public CompoundTag save(CompoundTag tag){tag.putUUID("id",id);return tag;}
    }
}
