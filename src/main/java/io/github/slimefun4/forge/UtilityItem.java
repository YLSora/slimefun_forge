package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class UtilityItem extends Item {
    static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> FLASK_INTERACTABLES=
        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,new net.minecraft.resources.ResourceLocation("slimefun","knowledge_flask_interactables"));
    private final String kind;
    public UtilityItem(String kind) { super(new Properties().stacksTo(kind.equals("rag")||kind.equals("bandage")||kind.equals("splint")||kind.equals("vitamins")||kind.equals("flask_of_knowledge")||kind.equals("portable_dustbin")?64:1)); this.kind=kind; }
    @Override public boolean isFoil(ItemStack stack){return kind.equals("guide")||kind.equals("creative_guide")||super.isFoil(stack);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(kind.equals("medicine")) {
            if(player instanceof ServerPlayer server && !ResearchData.canUse(server,stack)) { ItemAccess.denied(server,stack); return InteractionResultHolder.fail(stack); }
            player.startUsingItem(hand); return InteractionResultHolder.consume(stack);
        }
        boolean healthyMedical=immediateMedical() && !kind.equals("vitamins") && !player.isOnFire() && player.getHealth()>=player.getMaxHealth();
        if(!(player instanceof ServerPlayer server)) return healthyMedical?InteractionResultHolder.pass(stack):InteractionResultHolder.success(stack);
        if(!ResearchData.canUse(server,stack)) { ItemAccess.denied(server,stack); return InteractionResultHolder.fail(stack); }
        if(healthyMedical) return InteractionResultHolder.pass(stack);
        switch(kind) {
            case "guide" -> {if(player.isShiftKeyDown())GuideSettings.open(server,stack);else GuideMenu.open(server);}
            case "creative_guide" -> {if(!Guide.canCheat(server))Messages.chat(server,Guide.text(server,"no_permission"));else if(player.isShiftKeyDown())GuideSettings.open(server,stack);else GuideMenu.openCreative(server);}
            case "portable_crafter" -> {
                player.openMenu(new SimpleMenuProvider((id,inv,p) -> new CraftingMenu(id,inv,ContainerLevelAccess.create(level,player.blockPosition())) {
                    @Override public boolean stillValid(Player p) { return p.isAlive(); }
                },net.minecraft.network.chat.Component.translatable("container.crafting")));
                level.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.WOODEN_BUTTON_CLICK_ON,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
            }
            case "gold_pan", "nether_gold_pan" -> {}
            case "portable_dustbin" -> {
                if(player.openMenu(new SimpleMenuProvider((id,inv,p) -> ChestMenu.threeRows(id,inv,new SimpleContainer(27)),stack.getHoverName())).isPresent())
                    server.playNotifySound(net.minecraft.sounds.SoundEvents.ANVIL_LAND,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
            }
            case "ender_backpack" -> {
                if(BackpackItem.openEnder(server,hand))
                    server.playNotifySound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
            }
            case "rag", "bandage", "splint", "vitamins" -> {
                if(kind.equals("vitamins")) player.heal(8);
                else player.addEffect(new MobEffectInstance(MobEffects.HEAL,1,kind.equals("bandage")?1:0));
                if(kind.equals("rag")||kind.equals("bandage")) level.levelEvent(2001,player.blockPosition(),net.minecraft.world.level.block.Block.getId(Blocks.WHITE_WOOL.defaultBlockState()));
                else server.playNotifySound(kind.equals("splint")?net.minecraft.sounds.SoundEvents.SKELETON_HURT:net.minecraft.sounds.SoundEvents.GENERIC_EAT,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
                if(!kind.equals("splint")) player.clearFire();
                if(kind.equals("vitamins")) clearMedicalEffects(player);
                if(!player.isCreative()) stack.shrink(1);
            }
            case "flask_of_knowledge" -> {
                if(player.experienceLevel<1) return InteractionResultHolder.fail(stack);
                // RC-37 charges a level and one flask even in creative mode.
                player.giveExperienceLevels(-1);
                var filled=new ItemStack(SlimefunForge.item("filled_flask_of_knowledge"));
                // RC-37 inserts into the main inventory before consuming the empty flask.
                // Inventory.add discards overflow in creative and can merge into the offhand.
                var remainder=net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(
                    new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(player.getInventory()),filled,false);
                if(!remainder.isEmpty()) player.drop(remainder,false);
                stack.shrink(1);
                server.playNotifySound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,net.minecraft.sounds.SoundSource.PLAYERS,1,0.5F);
            }
            default -> { return InteractionResultHolder.pass(stack); }
        }
        return InteractionResultHolder.success(stack);
    }
    private boolean immediateMedical() { return kind.equals("rag")||kind.equals("bandage")||kind.equals("splint")||kind.equals("vitamins"); }
    private boolean goldPan() { return kind.equals("gold_pan")||kind.equals("nether_gold_pan"); }
    private static void clearMedicalEffects(LivingEntity entity) {
        Radiation.clear(entity);
        for(var effect : java.util.List.of(MobEffects.POISON,MobEffects.WITHER,MobEffects.MOVEMENT_SLOWDOWN,MobEffects.DIG_SLOWDOWN,MobEffects.WEAKNESS,MobEffects.CONFUSION,MobEffects.BLINDNESS,MobEffects.BAD_OMEN)) entity.removeEffect(effect);
    }
    @Override public int getUseDuration(ItemStack stack) { return kind.equals("medicine")?32:super.getUseDuration(stack); }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return kind.equals("medicine")?UseAnim.DRINK:super.getUseAnimation(stack); }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity) {
        if(!kind.equals("medicine")) return super.finishUsingItem(stack,level,entity);
        if(entity instanceof ServerPlayer player && ResearchData.canUse(player,stack)) {
            player.heal(8); player.clearFire(); clearMedicalEffects(player);
            player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
            if(!player.isCreative()) {
                stack.shrink(1);
                var bottle=new ItemStack(Items.GLASS_BOTTLE);
                if(stack.isEmpty()) return bottle;
                if(!player.addItem(bottle)) player.drop(bottle,false);
            }
        }
        return stack;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if(!goldPan()) return super.useOn(context);
        Level level=context.getLevel(); BlockPos pos=context.getClickedPos(); Player player=context.getPlayer();
        boolean nether=kind.equals("nether_gold_pan");
        var state=level.getBlockState(pos);
        if(player==null) return InteractionResult.PASS;
        if(player instanceof ServerPlayer server) {
            if(!ResearchData.canUse(server,context.getItemInHand())) { ItemAccess.denied(server,context.getItemInHand()); return InteractionResult.FAIL; }
            if((nether ? state.is(Blocks.SOUL_SAND)||state.is(Blocks.SOUL_SOIL) : state.is(Blocks.GRAVEL))
                && level.mayInteract(player,pos) && player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand())
                && net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(level,server.gameMode.getGameModeForPlayer(),server,pos)>=0) {
                var held=context.getItemInHand();var expected=held.copy();var result=GoldPanDrops.pick(nether?1:0);
                if(!SpecialTools.available(server.serverLevel(),pos)||!level.getBlockState(pos).equals(state))return InteractionResult.FAIL;
                if(result.isEmpty()){level.destroyBlock(pos,false,player);return InteractionResult.CONSUME;}
                var drop=io.github.slimefun4.forge.api.ItemSpawnApi.spawn(server.serverLevel(),server,io.github.slimefun4.forge.api.ItemSpawnApi.Reason.GOLD_PAN_USE,net.minecraft.world.phys.Vec3.atCenterOf(pos),result,()->server.isAlive()&&server.level()==level&&SpecialTools.available(server.serverLevel(),pos)&&level.getBlockState(pos).equals(state)&&server.getItemInHand(context.getHand())==held&&ItemStack.matches(held,expected)&&ResearchData.canUse(server,held)&&level.mayInteract(server,pos));
                if(drop!=null&&!level.destroyBlock(pos,false,player))drop.discard();
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context) {
        if(kind.equals("gps_marker_tool")||kind.equals("portable_geo_scanner")) {
            if(context.getPlayer() instanceof ServerPlayer server) {
                if(!ResearchData.canUse(server,stack)){ItemAccess.denied(server,stack);return InteractionResult.FAIL;}
                if(!context.getLevel().mayInteract(server,context.getClickedPos()))return InteractionResult.FAIL;
                if(kind.equals("gps_marker_tool"))GpsWaypoints.prompt(server,context.getClickedPos().relative(context.getClickedFace()));
                else return GeoScannerBlock.scan(server,context.getClickedPos(),context.getHand());
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        // RC-37 handles the item before the clicked block, including while sneaking.
        if((kind.equals("ender_backpack") || kind.equals("portable_dustbin")) && context.getPlayer()!=null) {
            var result=use(context.getLevel(),context.getPlayer(),context.getHand()).getResult();
            // A locked RC-37 item denies item use only; an ordinary clicked chest remains usable.
            return result==InteractionResult.FAIL?InteractionResult.PASS:result;
        }
        if(!kind.equals("flask_of_knowledge")) return super.onItemUseFirst(stack,context);
        var player=context.getPlayer();
        if(player==null) return InteractionResult.FAIL;
        var state=context.getLevel().getBlockState(context.getClickedPos());
        // Material-level rejection also applies while sneaking and when this hand cannot activate the block.
        if(state.is(FLASK_INTERACTABLES) || state.getMenuProvider(context.getLevel(),context.getClickedPos())!=null) return InteractionResult.FAIL;
        return use(context.getLevel(),player,context.getHand()).getResult();
    }
    public static final class Events {
        public static void useUtility(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
            if(event.getUseItem()==net.minecraftforge.eventbus.api.Event.Result.DENY || !(event.getItemStack().getItem() instanceof UtilityItem item)
                || !(item.immediateMedical()||item.goldPan()||item.kind.equals("portable_crafter"))) return;
            // Ritual offerings consume the held item in the original custom event before item use.
            if(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof AncientRitualBlock) return;
            var result=item.goldPan()?item.useOn(new UseOnContext(event.getEntity(),event.getHand(),event.getHitVec())):item.use(event.getLevel(),event.getEntity(),event.getHand()).getResult();
            if(result!=InteractionResult.PASS) event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            // RC-37 still dispatches the independent multiblock handler after item cancellation.
            if(result.consumesAction()) event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGH)
        public static void interactTool(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
            if(!(event.getItemStack().getItem() instanceof UtilityItem item)||!(item.goldPan()||item.kind.equals("portable_crafter")))return;
            boolean denied=event.getEntity() instanceof ServerPlayer server&&!ResearchData.canUse(server,event.getItemStack());
            if(denied)ItemAccess.denied((ServerPlayer)event.getEntity(),event.getItemStack());
            if(denied||item.goldPan()&&!(event.getTarget() instanceof net.minecraft.world.entity.decoration.ItemFrame)) {
                event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
            }
        }
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void useFlask(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
            if(!(event.getItemStack().getItem() instanceof UtilityItem item) || !item.kind.equals("flask_of_knowledge")) return;
            // Finish before ServerPlayerGameMode's creative stack-count restoration.
            event.setCancellationResult(item.use(event.getLevel(),event.getEntity(),event.getHand()).getResult());
            event.setCanceled(true);
        }
        @SubscribeEvent public static void drops(LivingDropsEvent event) {
            if(event.getEntity() instanceof IronGolem golem && !golem.level().isClientSide && golem.getRandom().nextInt(100)<75) {
                event.getDrops().add(new ItemEntity(golem.level(),golem.getX(),golem.getY(),golem.getZ(),new ItemStack(SlimefunForge.item("basic_circuit_board"))));
            }
        }
    }
}
