package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.Rechargeable;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MultiToolItem extends Item implements Rechargeable {
    private static final Map<MinecraftServer,Map<UUID,Map<Item,Integer>>> MODES=new WeakHashMap<>();
    private static final Set<ServerPlayer> USING=Collections.newSetFromMap(new IdentityHashMap<>());
    private final int capacity;
    private final List<String> modes;
    public MultiToolItem(Catalog.Entry entry){super(new Properties().durability(238));capacity=entry.capacity();modes=entry.id().equals("carbonado_multi_tool")?List.of("portable_crafter","magic_eye_of_ender","staff_elemental_wind","grappling_hook","gold_pan","nether_gold_pan"):List.of("portable_crafter","magic_eye_of_ender","staff_elemental_wind","grappling_hook");}
    @Override public float getMaxItemCharge(ItemStack stack){return capacity;}
    @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack,net.minecraft.nbt.CompoundTag tag){return energyCapability(stack);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){return new InteractionResultHolder<>(activate(player,hand,null),player.getItemInHand(hand));}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return context.getPlayer()==null?InteractionResult.PASS:activate(context.getPlayer(),context.getHand(),context);}
    private InteractionResult activate(Player player,InteractionHand hand,UseOnContext context) {
        if(!(player instanceof ServerPlayer p))return InteractionResult.SUCCESS;
        var stack=p.getItemInHand(hand);if(!ResearchData.canUse(p,stack)){ItemAccess.denied(p,stack);return InteractionResult.FAIL;}
        if(!USING.add(p))return InteractionResult.FAIL;
        try {
        var selected=MODES.computeIfAbsent(p.server,s->new HashMap<>()).computeIfAbsent(p.getUUID(),u->new HashMap<>());int mode=selected.getOrDefault(this,0);
        if(p.isShiftKeyDown()){mode=(mode+1)%modes.size();selected.put(this,mode);Messages.chat(p,Component.translatable("message.slimefun.multi_tool_mode","Multi Tool",SlimefunForge.item(modes.get(mode)).getDescription()));}
        else if(removeItemCharge(stack,.3F)) {
            p.inventoryMenu.broadcastChanges();
            Item target=SlimefunForge.item(modes.get(mode));
            if(context==null)target.use(p.level(),p,hand);
            else if(target instanceof MagicGadgetItem gadget)gadget.activate(p,hand,context);
            else if(target.onItemUseFirst(stack,context)==InteractionResult.PASS)target.useOn(context);
            // PortableCrafter has no block-use override, but its RC-37 handler also opens on blocks.
            if(context!=null&&modes.get(mode).equals("portable_crafter"))target.use(p.level(),p,hand);
        }
        return InteractionResult.SUCCESS;
        } finally {USING.remove(p);}
    }
    @Override public boolean canAttackBlock(BlockState state,Level level,BlockPos pos,Player player){Messages.chat(player,Component.translatable("message.slimefun.multi_tool_not_shears"));return false;}
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){
        if(target instanceof net.minecraft.world.entity.animal.Sheep||target instanceof net.minecraft.world.entity.animal.MushroomCow||target instanceof net.minecraft.world.entity.animal.SnowGolem){Messages.chat(player,Component.translatable("message.slimefun.multi_tool_not_shears"));return InteractionResult.SUCCESS;}return InteractionResult.PASS;
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){MODES.remove(event.getServer());}
}
