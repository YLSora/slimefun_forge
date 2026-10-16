package io.github.slimefun4.addons.example;

import com.mojang.logging.LogUtils;
import io.github.slimefun4.forge.MachineOperationFinishEvent;
import io.github.slimefun4.forge.api.SlimefunAddonApi;
import io.github.slimefun4.forge.api.ResearchApi;
import io.github.slimefun4.forge.api.AddonRegistryFinalizedEvent;
import io.github.slimefun4.forge.api.ResearchCompletedEvent;
import io.github.slimefun4.forge.api.GuideApi;
import io.github.slimefun4.forge.api.GuideOpenEvent;
import io.github.slimefun4.forge.api.PlayerLanguageChangeEvent;
import java.time.Month;
import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import static net.minecraft.commands.Commands.literal;

/** Independent addon: only Forge and the documented Slimefun API/event are used. */
@Mod(ExampleAddon.ID)
public final class ExampleAddon {
    public static final String ID="sf_example";
    static ResourceLocation key(String path){return new ResourceLocation(ID,path);}
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
    private static final net.minecraft.tags.TagKey<Item> INPUTS=net.minecraft.tags.TagKey.create(Registries.ITEM,key("compression_inputs"));
    public static final RegistryObject<Item> COMPRESSED_IRON=ITEMS.register("compressed_iron",()->new Item(new Item.Properties()){
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var held=player.getItemInHand(hand);
            if(level.isClientSide)return InteractionResultHolder.sidedSuccess(held,true);
            if(!(player instanceof ServerPlayer server))return InteractionResultHolder.fail(held);
            if(!SlimefunAddonApi.canUse(server,held)){
                server.displayClientMessage(Component.translatable("message.sf_example.locked"),false);
                return InteractionResultHolder.fail(held);
            }
            held.shrink(1);ItemHandlerHelper.giveItemToPlayer(server,new ItemStack(Items.IRON_INGOT,4));
            return InteractionResultHolder.sidedSuccess(held,false);
        }
    });
    public static final RegistryObject<Item> IRON_TOKEN=ITEMS.register("iron_token",()->new Item(new Item.Properties()){
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("lore.sf_example.type"));lines.add(Component.translatable("lore.sf_example.tier",1));
            lines.add(Component.translatable("lore.sf_example.color",Component.translatable("color.minecraft."+DyeColor.LIGHT_GRAY.getName())));
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var held=player.getItemInHand(hand);
            if(level.isClientSide)return InteractionResultHolder.sidedSuccess(held,true);
            if(!(player instanceof ServerPlayer server)||!SlimefunAddonApi.canUse(server,held))return InteractionResultHolder.fail(held);
            held.shrink(1);ItemHandlerHelper.giveItemToPlayer(server,new ItemStack(Items.IRON_NUGGET));
            return InteractionResultHolder.sidedSuccess(held,false);
        }
    });
    public ExampleAddon(){
        var modBus=FMLJavaModLoadingContext.get().getModEventBus();ITEMS.register(modBus);
        NativeItems.register(modBus);NativeBlocks.register(modBus);NativeDrops.register(modBus);NativeHooks.register(modBus);NativeMachines.register(modBus);NativeNetworks.register(modBus);NativePlayers.register(modBus);NativeAndroids.register(modBus);NativeEvents.register(modBus);NativeEquipment.register(modBus);
        modBus.addListener(this::registered);
        SlimefunAddonApi.registerCategory(new SlimefunAddonApi.Category(key("materials"),"category.sf_example.materials",key("compressed_iron")));
        for(String category:List.of("tokens","unlocked","seasonal","custom"))SlimefunAddonApi.registerCategory(new SlimefunAddonApi.Category(key(category),"category.sf_example."+category,key("iron_token")));
        GuideApi.registerCategory(key("tokens"),new GuideApi.CategoryOptions(key("materials"),1,Set.of(),Set.of(),Set.of(),new GuideApi.CategoryBehavior(){
            @Override public List<ResourceLocation> entries(GuideApi.Context context,List<ResourceLocation> entries){return preferences(context.player()).getBoolean("hide_token")?List.of():entries;}
        }));
        GuideApi.registerCategory(key("unlocked"),new GuideApi.CategoryOptions(key("materials"),2,Set.of(key("compression")),Set.of(),Set.of(),new GuideApi.CategoryBehavior(){}));
        GuideApi.registerCategory(key("seasonal"),new GuideApi.CategoryOptions(key("unlocked"),3,Set.of(),Set.of(Month.OCTOBER),Set.of(),new GuideApi.CategoryBehavior(){}));
        GuideApi.registerCategory(key("custom"),new GuideApi.CategoryOptions(key("seasonal"),4,Set.of(),Set.of(),Set.of(),new GuideApi.CategoryBehavior(){
            @Override public boolean visible(GuideApi.Context context){return context.mode()==GuideApi.Mode.SURVIVAL;}
            @Override public boolean accessible(GuideApi.Context context){return SlimefunAddonApi.canUse(context.player(),new ItemStack(IRON_TOKEN.get()));}
            @Override public MenuProvider page(GuideApi.Context context,Runnable back){return new SimpleMenuProvider((id,inv,p)->new ExamplePage(id,inv,context.player(),back),Component.translatable("category.sf_example.custom"));}
        }));
        SlimefunAddonApi.registerResearch(new SlimefunAddonApi.Research(key("compression"),"research.sf_example.compression",1));
        SlimefunAddonApi.registerEntry(new SlimefunAddonApi.Entry(key("compressed_iron"),key("materials"),key("compression")));
        GuideApi.registerItem(key("compressed_iron"),new GuideApi.ItemOptions(false,"https://github.com/Slimefun/Slimefun4/wiki"));
        SlimefunAddonApi.registerEntry(new SlimefunAddonApi.Entry(key("iron_token"),key("tokens")));
        GuideApi.registerLanguage(ID,new GuideApi.Language("en_us","English (US)",false));
        GuideApi.registerLanguage(ID,new GuideApi.Language("zh_cn","简体中文",false));
        GuideApi.registerRecipeType(new GuideApi.RecipeType(key("unpacking"),"recipe.sf_example.unpacking",key("compressed_iron"),null));
        GuideApi.registerRecipes(key("compressed_iron"),context->java.util.stream.StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(INPUTS).spliterator(),false)
            .map(holder->new GuideApi.DisplayRecipe(key("unpacking"),Component.translatable("recipe.sf_example.section"),List.of(new ItemStack(holder.value())),List.of(new ItemStack(Items.IRON_INGOT,4),new ItemStack(IRON_TOKEN.get())),List.of(Component.translatable("recipe.sf_example.description")))).toList());
        GuideApi.registerSetting(key("show_token"),new GuideApi.Setting(){
            @Override public ItemStack icon(GuideApi.Context context){return new ItemStack(Items.LIGHT_GRAY_WOOL).setHoverName(Component.translatable("setting.sf_example.show_token"));}
            @Override public Component selection(GuideApi.Context context){return Component.translatable("setting.sf_example."+(preferences(context.player()).getBoolean("hide_token")?"hidden":"shown"));}
            @Override public void click(GuideApi.Context context,GuideApi.Click click){var data=preferences(context.player());data.putBoolean("hide_token",click.button()==0&&!data.getBoolean("hide_token"));}
        });
        MinecraftForge.EVENT_BUS.addListener(this::finished);
        MinecraftForge.EVENT_BUS.addListener(this::researched);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        MinecraftForge.EVENT_BUS.addListener((PlayerLanguageChangeEvent event)->LogUtils.getLogger().info("SF_EXAMPLE_LANGUAGE player={} old={} new={}",event.getEntity().getUUID(),event.getPreviousLanguage(),event.getLanguage()));
        MinecraftForge.EVENT_BUS.addListener((GuideOpenEvent event)->LogUtils.getLogger().debug("SF_EXAMPLE_GUIDE player={} mode={} book={}",event.getEntity().getUUID(),event.getMode(),event.getBook()));
    }
    private static net.minecraft.nbt.CompoundTag preferences(ServerPlayer player){var persisted=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);player.getPersistentData().put(Player.PERSISTED_NBT_TAG,persisted);var data=persisted.getCompound(ID);persisted.put(ID,data);return data;}
    /** Read-only native menu: no item movement; return callback is bound to this exact server window. */
    private static final class ExamplePage extends ChestMenu {
        private final ServerPlayer owner;
        private final net.minecraft.world.phys.Vec3 origin;
        private final net.minecraft.resources.ResourceKey<Level> dimension;
        private final Runnable back;
        ExamplePage(int id,Inventory inv,ServerPlayer owner,Runnable back){
            super(MenuType.GENERIC_9x3,id,inv,new SimpleContainer(27),3);this.owner=owner;this.back=back;origin=owner.position();dimension=owner.level().dimension();
            getContainer().setItem(0,new ItemStack(Items.ARROW).setHoverName(GuideApi.localize(owner,Component.translatable("menu.sf_example.back"))));
            getContainer().setItem(13,GuideApi.withLore(new ItemStack(COMPRESSED_IRON.get()),List.of(GuideApi.localize(owner,Component.translatable("recipe.sf_example.description")))));
        }
        @Override public void clicked(int slot,int button,ClickType type,Player player){if(player==owner&&owner.containerMenu==this&&stillValid(player)&&slot==0&&button==0&&type==ClickType.PICKUP)back.run();}
        @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
        @Override public boolean stillValid(Player player){return player==owner&&owner.isAlive()&&owner.level().dimension().equals(dimension)&&owner.position().distanceToSqr(origin)<=64&&GuideApi.canOpen(new GuideApi.Context(owner,GuideApi.Mode.SURVIVAL),key("custom"));}
    }
    private void registered(AddonRegistryFinalizedEvent event){
        LogUtils.getLogger().info("SF_EXAMPLE_REGISTERED research={} entry={}",event.getContent().researches().get(key("compression")),event.getContent().entries().get(key("iron_token")));
    }
    private void researched(ResearchCompletedEvent event){
        if(event.getResearch().equals(key("compression")))LogUtils.getLogger().info("SF_EXAMPLE_RESEARCH_COMPLETE player={} research={}",event.getEntity().getUUID(),event.getResearch());
    }
    private void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(literal("sf_example").then(literal("machine").requires(source->source.hasPermission(2))
            .then(net.minecraft.commands.Commands.argument("position",net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                .executes(context->{var pos=net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(context,"position");return inspectMachine(context.getSource(),pos);})
                .then(literal("cancel").executes(context->{var pos=net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(context,"position");var processor=io.github.slimefun4.forge.api.MachineApi.processor(context.getSource().getLevel(),pos);if(processor==null)return 0;processor.cancel();return 1;})))));
        event.getDispatcher().register(literal("sf_example").then(literal("guide")
            .executes(context->{GuideApi.category(context.getSource().getPlayerOrException(),GuideApi.Mode.SURVIVAL,key("materials"),0);return 1;})
            .then(literal("custom").executes(context->{GuideApi.category(context.getSource().getPlayerOrException(),GuideApi.Mode.SURVIVAL,key("custom"),0);return 1;}))
            .then(literal("recipes").executes(context->{GuideApi.item(context.getSource().getPlayerOrException(),GuideApi.Mode.SURVIVAL,key("compressed_iron"));return 1;}))));
        event.getDispatcher().register(literal("sf_example").then(literal("research")
            .executes(context->{var player=context.getSource().getPlayerOrException();var state=ResearchApi.get(player.server,player.getUUID(),key("compression"));context.getSource().sendSuccess(()->Component.literal(state.toString()),false);return 1;})
            .then(literal("learn").executes(context->ResearchApi.learn(context.getSource().getPlayerOrException(),new ItemStack(COMPRESSED_IRON.get()))?1:0))
            .then(literal("grant").requires(source->source.hasPermission(2)).executes(context->ResearchApi.grant(context.getSource().getPlayerOrException(),key("compression"))?1:0))
            .then(literal("revoke").requires(source->source.hasPermission(2)).executes(context->ResearchApi.revoke(context.getSource().getPlayerOrException(),key("compression"))?1:0))
            .then(literal("all").requires(source->source.hasPermission(2)).executes(context->ResearchApi.grantAll(context.getSource().getPlayerOrException())))
            .then(literal("reset").requires(source->source.hasPermission(2)).executes(context->ResearchApi.reset(context.getSource().getPlayerOrException())))));
    }
    private static int inspectMachine(net.minecraft.commands.CommandSourceStack source,net.minecraft.core.BlockPos pos){
        var processor=io.github.slimefun4.forge.api.MachineApi.processor(source.getLevel(),pos);if(processor==null)return 0;
        var entity=source.getLevel().getBlockEntity(pos);var operation=processor.operation();var json=new com.google.gson.JsonObject();
        json.add("state",net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,entity.saveWithoutMetadata()));
        if(operation!=null){json.addProperty("type",operation.type().toString());json.addProperty("progress",operation.progress());json.addProperty("total",operation.total());json.addProperty("remaining",operation.remaining());}
        source.sendSuccess(()->Component.literal(json.toString()),false);return 1;
    }
    private void finished(MachineOperationFinishEvent event){
        if(event.getOperation().type().getNamespace().equals(ID)||event.getResults().stream().anyMatch(s->s.is(COMPRESSED_IRON.get())))
            LogUtils.getLogger().info("SF_EXAMPLE_FINISH pos={} input={} planned={} cycles={}",event.getPosition(),event.getIngredients(),event.getResults(),event.getTotalTicks());
    }
}
