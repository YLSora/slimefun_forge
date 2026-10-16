package io.github.slimefun4.addons.example;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.slimefun4.forge.api.ItemApi;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

/** One native loot modifier: replacement barter and a separate player-kill drop, both policy-aware. */
final class NativeDrops extends LootModifier {
    private static final Codec<NativeDrops> CODEC=RecordCodecBuilder.create(instance->codecStart(instance).apply(instance,NativeDrops::new));
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS=DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,ExampleAddon.ID);
    private static final RegistryObject<Codec<NativeDrops>> TYPE=SERIALIZERS.register("native_drops",()->CODEC);
    private static final ResourceLocation BARTER=new ResourceLocation("minecraft:gameplay/piglin_bartering"),ZOMBIE=new ResourceLocation("minecraft:entities/zombie");
    private NativeDrops(LootItemCondition[] conditions){super(conditions);}
    static void register(IEventBus bus){SERIALIZERS.register(bus);}
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,LootContext context){
        boolean barter=context.getQueriedLootTableId().equals(BARTER),kill=context.getQueriedLootTableId().equals(ZOMBIE)&&context.hasParam(LootContextParams.LAST_DAMAGE_PLAYER);
        if(kill){
            var damage=context.getParamOrNull(LootContextParams.DAMAGE_SOURCE);
            if(damage==null||!(damage.getDirectEntity() instanceof net.minecraft.server.level.ServerPlayer actor)||actor.server.getPlayerList().getPlayer(actor.getUUID())!=actor||!actor.getMainHandItem().is(NativeItems.SWORD.get())||!io.github.slimefun4.forge.api.SlimefunAddonApi.canUse(actor,actor.getMainHandItem()))return loot;
        }
        if(!barter&&!kill)return loot;var stack=new ItemStack(barter?ExampleAddon.IRON_TOKEN.get():NativeItems.COMPONENT.get());
        if(!ItemApi.enabled(context.getLevel(),stack)||context.getRandom().nextFloat()>=(barter?.05F:.1F))return loot;
        if(barter)loot.clear();loot.add(stack);return loot;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec(){return TYPE.get();}
}
