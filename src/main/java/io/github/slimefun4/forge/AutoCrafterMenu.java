package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Display-only vanilla container protocol; every action revalidates the server-side selection. */
public final class AutoCrafterMenu extends ChestMenu {
    private final AutoCrafterEntity machine;
    private final ServerPlayer owner;
    private final SimpleContainer display;
    private final List<Recipe<?>> choices;
    private int page,revision;
    private long ingredientFrame=-1;
    private boolean choosing;
    public static void open(ServerPlayer player,AutoCrafterEntity machine,boolean choosing) {
        if(machine.inventory()==null){Messages.chat(player,Component.translatable("message.slimefun.autocrafter.missing_chest"));return;}
        List<Recipe<?>> choices=new ArrayList<>();
        if(choosing) {
            for(var recipe:player.level().getRecipeManager().getRecipes()) if(machine.supports(recipe) && (machine.vanilla()?ItemStack.isSameItemSameTags(recipe.getResultItem(player.level().registryAccess()),player.getMainHandItem()):MachineRecipe.same(player.getMainHandItem(),recipe.getResultItem(player.level().registryAccess())))) choices.add(recipe);
            choices.sort(Comparator.comparing(Recipe::getId));
            choices.removeIf(recipe->!ResearchData.canUse(player,recipe.getResultItem(player.level().registryAccess())));
        } else if(machine.recipe()!=null) choices.add(machine.recipe());
        if(choices.isEmpty()) { Messages.chat(player,Component.translatable("message.slimefun.autocrafter."+(choosing?"no_recipe":"select_recipe"))); return; }
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new AutoCrafterMenu(id,inventory,player,machine,choices,choosing,new SimpleContainer(54)),GuideContent.localize(player,machine.getBlockState().getBlock().getName())));
        sound(player);
    }
    private AutoCrafterMenu(int id,Inventory inventory,ServerPlayer player,AutoCrafterEntity machine,List<Recipe<?>> choices,boolean choosing,SimpleContainer display) {
        super(MenuType.GENERIC_9x6,id,inventory,display,6); this.machine=machine; owner=player; this.choices=List.copyOf(choices); this.choosing=choosing; this.display=display; revision=machine.revision(); render();
    }
    private static void sound(ServerPlayer player) { player.playNotifySound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),net.minecraft.sounds.SoundSource.PLAYERS,1,1); }
    private void render() {
        for(int i=0;i<54;i++)display.setItem(i,MenuItems.blank(Items.GRAY_STAINED_GLASS_PANE));
        var recipe=choices.get(page); var slots=new int[]{11,12,13,20,21,22,29,30,31};
        for(int slot:slots)display.setItem(slot,ItemStack.EMPTY);
        if(recipe instanceof MachineRecipe nativeRecipe) { for(int i=0;i<nativeRecipe.inputs.size();i++) display.setItem(slots[i],GuideContent.localize(owner,nativeRecipe.inputs.get(i))); }
        else {
            int width=recipe instanceof ShapedRecipe shaped?shaped.getWidth():3;
            var ingredients=recipe.getIngredients(); long frame=owner.level().getGameTime()/20;
            for(int i=0;i<ingredients.size();i++) { var alternatives=ingredients.get(i).getItems(); if(alternatives.length>0) display.setItem(slots[(i/width)*3+i%width],GuideContent.localize(owner,alternatives[(int)Math.floorMod(frame,alternatives.length)])); }
        }
        display.setItem(24,GuideContent.localize(owner,recipe.getResultItem(owner.level().registryAccess())));
        display.setItem(49,MenuItems.label(owner,choosing?new ItemStack(Items.CRAFTING_TABLE):machine.enabled()?new ItemStack(Items.BARRIER):MenuItems.head(MenuItems.Head.EXCLAMATION_MARK),"autocrafter."+(choosing?"select":machine.enabled()?"enabled":"disabled")));
        if(choosing && machine.vanilla()) {
            display.setItem(46,MenuItems.label(owner,page>0?Items.ARROW:Items.BLACK_STAINED_GLASS_PANE,"android.library_previous",page+1,choices.size()));
            display.setItem(52,MenuItems.label(owner,page+1<choices.size()?Items.ARROW:Items.BLACK_STAINED_GLASS_PANE,"android.library_next",page+1,choices.size()));
        }
    }
    @Override public void broadcastChanges() {
        if(machine!=null) {
            long frame=owner.level().getGameTime()/20;
            if(ingredientFrame!=frame) { ingredientFrame=frame; render(); }
        }
        super.broadcastChanges();
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player) {
        if(player.containerMenu!=this || type!=ClickType.PICKUP || (button!=0 && button!=1) || !stillValid(player)) return;
        var recipe=choices.get(page);
        if(revision!=machine.revision() || owner.level().getRecipeManager().byKey(recipe.getId()).orElse(null)!=recipe) { owner.closeContainer(); return; }
        if(choosing && machine.vanilla() && button==0 && (slot==46 || slot==52)) { page=Math.max(0,Math.min(choices.size()-1,page+(slot==46?-1:1))); render(); sound(owner); return; }
        if(slot!=49) return;
        if(choosing) {
            if(button!=0 || !ResearchData.canUse(owner,recipe.getResultItem(owner.level().registryAccess())) || machine.vanilla() && owner.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_LIMITED_CRAFTING) && !owner.getRecipeBook().contains(recipe)) return;
            machine.select(recipe.getId()); choosing=false; revision=machine.revision(); render();
        } else { if(button==1) machine.select(null); else machine.setEnabled(!machine.enabled()); owner.closeContainer(); }
        sound(owner);
    }
    @Override public ItemStack quickMoveStack(Player player,int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) {
        return player==owner && player.isAlive() && !machine.isRemoved() && player.level()==machine.getLevel() && player.level().hasChunkAt(machine.getBlockPos())
            && player.level().getBlockEntity(machine.getBlockPos())==machine && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(machine.getBlockPos()))<=64
            && player.level().mayInteract(player,machine.getBlockPos()) && ResearchData.canUse(owner,new ItemStack(machine.getBlockState().getBlock()));
    }
}
