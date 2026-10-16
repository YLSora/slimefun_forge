package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;

public final class FunctionalRuneItem extends Item implements io.github.slimefun4.forge.api.MagicApi.Rune {
    private final String kind;
    public FunctionalRuneItem(Catalog.Entry entry) { super(new Properties());kind=entry.implementation(); }
    @Override public boolean onEntityItemUpdate(ItemStack stack,ItemEntity rune) {
        return io.github.slimefun4.forge.api.MagicApi.tickRune(stack,rune);
    }
    @Override public Optional<ItemStack> transform(ServerLevel level,ItemStack stack) {
        if(kind.equals("VillagerRune")||stack.getCount()!=1)return Optional.empty();
        if(kind.equals("SoulboundRune")){if(Soulbinding.bound(stack))return Optional.empty();Soulbinding.bind(stack);return Optional.of(stack);}
        if(kind.equals("EnchantmentRune")) {
            if(!Enchanting.allowed(stack,false))return Optional.empty();
            var existing=EnchantmentHelper.getEnchantments(stack);var available=new ArrayList<Enchantment>();
            for(var enchant:BuiltInRegistries.ENCHANTMENT)if(!enchant.isCurse()&&enchant.canEnchant(stack)&&existing.keySet().stream().noneMatch(e->e==enchant||!enchant.isCompatibleWith(e)))available.add(enchant);
            if(available.isEmpty())return Optional.empty();
            var enchant=available.get(level.random.nextInt(available.size()));stack.enchant(enchant,1+level.random.nextInt(enchant.getMaxLevel()));return Optional.of(stack);
        }
        return Optional.empty();
    }
    public static void villager(PlayerInteractEvent.EntityInteract event) {
        if(!(event.getItemStack().getItem() instanceof FunctionalRuneItem item)||!item.kind.equals("VillagerRune")||!(event.getTarget() instanceof Villager villager))return;
        var data=villager.getVillagerData();if(data.getProfession()==VillagerProfession.NONE||data.getProfession()==VillagerProfession.NITWIT)return;
        if(event.getEntity() instanceof ServerPlayer player) {
            if(!ResearchData.canUse(player,event.getItemStack())) {ItemAccess.denied(player,event.getItemStack());event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);return;}
            if(!player.level().mayInteract(player,villager.blockPosition()))return;
            if(!player.isCreative())event.getItemStack().shrink(1);
            villager.setVillagerXp(0);villager.setVillagerData(data.setLevel(1).setProfession(VillagerProfession.NONE));
            var level=player.serverLevel();level.playSound(null,villager.blockPosition(),SoundEvents.VILLAGER_CELEBRATE,SoundSource.NEUTRAL,1,1.4F);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE,villager.getX(),villager.getY(),villager.getZ(),10,0,level.random.nextDouble()*.25,0,0);
            level.sendParticles(ParticleTypes.ENCHANT,villager.getX(),villager.getY(),villager.getZ(),5,.04,1,.04,0);
        }
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
