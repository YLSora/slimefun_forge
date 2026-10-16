package io.github.slimefun4.forge;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

public final class MeatJerkyItem extends Item {
    public static final ForgeConfigSpec CONFIG;
    public static final Map<String,ForgeConfigSpec.IntValue> SATURATION=new LinkedHashMap<>();
    static {
        var builder=new ForgeConfigSpec.Builder();
        for(var entry:Catalog.ITEMS)if(entry.implementation().equals("MeatJerky")){
            builder.push(entry.id());SATURATION.put(entry.id(),builder.defineInRange("saturation-level",6,0,Integer.MAX_VALUE));builder.pop();
        }
        CONFIG=builder.build();
    }
    private final ForgeConfigSpec.IntValue saturation;
    public MeatJerkyItem(Catalog.Entry entry){
        super(new Properties().food(java.util.Objects.requireNonNull(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft",entry.material()))).getFoodProperties(null))));
        saturation=SATURATION.get(entry.id());
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer server&&!ResearchData.canUse(server,stack)){ItemAccess.denied(server,stack);return InteractionResultHolder.fail(stack);}
        return super.use(level,player,hand);
    }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
        // Forge 47.4.10 reaches completion with negative remaining ticks after Tick cancellation.
        if(entity.isUsingItem()&&entity.getUseItemRemainingTicks()<0)return stack;
        if(entity instanceof ServerPlayer player){
            if(!ResearchData.canUse(player,stack))return stack;
            // RC-37 adds its bonus before vanilla nutrition clamps saturation to the new food level.
            player.getFoodData().setSaturation(player.getFoodData().getSaturationLevel()+saturation.get());
        }
        return super.finishUsingItem(stack,level,entity);
    }
}
