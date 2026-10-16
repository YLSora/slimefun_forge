package io.github.slimefun4.forge;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import static net.minecraft.commands.Commands.literal;

@Mod(SlimefunForge.ID)
public final class SlimefunForge {
    public static final String ID = "slimefun";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES,ID);
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<GeoScanMenu>> GEO_SCAN_MENU = MENUS.register("geo_scan",()->net.minecraftforge.common.extensions.IForgeMenuType.create(GeoScanMenu::new));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<ProcessingMenu>> PROCESSING_MENU=MENUS.register("processing",()->net.minecraftforge.common.extensions.IForgeMenuType.create(ProcessingMenu::new));
    public static final Map<String, RegistryObject<Item>> CONTENT = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> MACHINES = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> AUTOCRAFTERS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> REACTORS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> PROCESSORS=new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> STORAGE_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> CARGO_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> RESISTANT_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> GPS_TRANSMITTERS=new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> GPS_TELEPORTERS=new LinkedHashMap<>();
    public static final Map<String,RegistryObject<Block>> ANDROID_INTERFACES=new LinkedHashMap<>();
    public static final Map<String,RegistryObject<Block>> ANDROIDS=new LinkedHashMap<>();
    public static final RegistryObject<Block> GPS_CONTROL_PANEL=BLOCKS.register("gps_control_panel",GpsControlPanelBlock::new);
    public static final RegistryObject<Block> GEO_SCANNER=BLOCKS.register("gps_geo_scanner",GeoScannerBlock::new);
    public static final RegistryObject<Block> GEO_MINER=BLOCKS.register("geo_miner",GeoMinerBlock::new);
    public static final RegistryObject<Block> ANCIENT_ALTAR=BLOCKS.register("ancient_altar",()->new AncientRitualBlock(false));
    public static final RegistryObject<Block> ANCIENT_PEDESTAL=BLOCKS.register("ancient_pedestal",()->new AncientRitualBlock(true));
    public static final RegistryObject<Block> CRUCIBLE=BLOCKS.register("crucible",CrucibleBlock::new);
    public static final RegistryObject<Block> COMPOSTER=BLOCKS.register("composter",ComposterBlock::new);
    public static final RegistryObject<Block> PROJECTOR=BLOCKS.register("hologram_projector",ProjectorBlock::new);
    public static final RegistryObject<Block> ELEVATOR=BLOCKS.register("elevator_plate",ElevatorBlock::new);
    public static final RegistryObject<Item> GUIDE = ITEMS.register("guide", () -> new UtilityItem("guide"));
    public static final RegistryObject<Item> CREATIVE_GUIDE = ITEMS.register("creative_guide", () -> new UtilityItem("creative_guide"));
    public static final RegistryObject<Item> DEBUG_FISH=ITEMS.register("debug_fish",DebugFish::new);
    public static final RegistryObject<Block> CLEARED_HEAD=BLOCKS.register("cleared_head",ClearedHeadBlock::new);
    public static final RegistryObject<Item> CLEARED_HEAD_ITEM=ITEMS.register("cleared_head",()->new BlockItem(CLEARED_HEAD.get(),new Item.Properties()));
    public static final RegistryObject<RecipeType<MachineRecipe>> PROCESSING = RECIPE_TYPES.register("machine", () -> new RecipeType<>() {
        public String toString() { return "slimefun:machine"; }
    });
    public static final RegistryObject<RecipeSerializer<MachineRecipe>> PROCESSING_SERIALIZER = SERIALIZERS.register("machine", MachineRecipe.Serializer::new);
    static {
        for (var entry : Catalog.ITEMS) {
            if(entry.behavior().equals("elevator")) {
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(ELEVATOR.get())));
            } else if(entry.behavior().equals("rainbow_block")||entry.behavior().equals("infused_hopper")||entry.behavior().equals("enhanced_furnace")||entry.behavior().equals("block_placer")) {
                var block=BLOCKS.register(entry.id(),()->switch(entry.behavior()) {
                    case "rainbow_block" -> RainbowBlocks.create(entry);
                    case "enhanced_furnace" -> new EnhancedFurnaceBlock(entry);
                    case "block_placer" -> new BlockPlacerBlock();
                    default -> new InfusedHopperBlock();
                });
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(block.get())));
            } else if(entry.id().equals("butter")||entry.id().equals("cheese")) {
                var block=BLOCKS.register(entry.id(),()->new HeadBlock(HeadBlock.properties()) {});
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(block.get())));
            } else if(entry.behavior().equals("radioactive")) {
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new RadioactiveItem(entry.radiation())));
            } else if(entry.behavior().equals("hazmat")) {
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new HazmatArmorItem(entry.material())));
            } else if(entry.behavior().equals("crucible")) {
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(CRUCIBLE.get())));
            } else if(entry.behavior().equals("composter")) {
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(COMPOSTER.get())));
            } else if(entry.behavior().equals("altar")) {
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(ANCIENT_ALTAR.get())));
            } else if(entry.behavior().equals("pedestal")) {
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(ANCIENT_PEDESTAL.get())));
            } else if(entry.behavior().equals("gps")) {
                RegistryObject<Block> block=BLOCKS.register(entry.id(),()->new GpsTransmitterBlock(entry));
                GPS_TRANSMITTERS.put(entry.id(),block);
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(block.get())));
            } else if(entry.behavior().equals("teleporter")) {
                RegistryObject<Block> block=BLOCKS.register(entry.id(),()->switch(entry.implementation()) {
                    case "TeleporterPylon" -> new GpsPylonBlock();
                    case "Teleporter" -> new GpsTeleporterBlock();
                    case "SharedActivationPlate" -> new GpsActivationPlate(false);
                    case "PersonalActivationPlate" -> new GpsActivationPlate(true);
                    default -> throw new IllegalArgumentException("Unknown teleporter: "+entry.id());
                });
                GPS_TELEPORTERS.put(entry.id(),block);
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(block.get())));
            } else if(entry.behavior().equals("android")) {
                RegistryObject<Block> block=BLOCKS.register(entry.id(),()->new AndroidBlock(entry));
                ANDROIDS.put(entry.id(),block);
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(block.get())));
            } else if(entry.behavior().equals("android_interface")) {
                RegistryObject<Block> block=BLOCKS.register(entry.id(),()->new AndroidInterfaceBlock(entry.id().equals("android_interface_fuel")));
                ANDROID_INTERFACES.put(entry.id(),block);
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(block.get())));
            } else if(entry.behavior().equals("gps_panel")) {
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(GPS_CONTROL_PANEL.get())));
            } else if(entry.behavior().equals("geo_miner")) {
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(GEO_MINER.get())));
            } else if(entry.behavior().equals("geo")) {
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(GEO_SCANNER.get())));
            } else if(entry.behavior().equals("reactor")) {
                RegistryObject<Block> block=BLOCKS.register(entry.id(),()->entry.implementation().equals("ReactorAccessPort")?new ReactorBlock.Port(entry):new ReactorBlock(entry));
                REACTORS.put(entry.id(),block);
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(block.get())));
            } else if(entry.behavior().equals("processing")) {
                RegistryObject<Block> block=BLOCKS.register(entry.id(),()->entry.material().equals("player_head")||entry.id().equals("refinery")?new ProcessingBlock.Head(entry):new ProcessingBlock(entry));
                PROCESSORS.put(entry.id(),block);
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(block.get())));
            } else if(entry.behavior().equals("autocrafter")) {
                RegistryObject<Block> block=BLOCKS.register(entry.id(),()->new AutoCrafterBlock(entry));
                AUTOCRAFTERS.put(entry.id(),block);
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),()->new ResearchBlockItem(block.get())));
            } else if (entry.machine()) {
                RegistryObject<Block> block = BLOCKS.register(entry.id(), () -> entry.implementation().equals("Capacitor")?new MachineBlock.Capacitor(entry):entry.material().equals("player_head")?new MachineBlock.Head(entry):new MachineBlock(entry));
                MACHINES.put(entry.id(), block);
                CONTENT.put(entry.id(), ITEMS.register(entry.id(), () -> new ResearchBlockItem(block.get())));
            } else if(entry.storage()) {
                RegistryObject<Block> block=BLOCKS.register(entry.id(),() -> switch(entry.id()) {
                    case "output_chest" -> new OutputChestBlock();
                    case "ignition_chamber" -> new IgnitionChamberBlock();
                    case "trash_can_block" -> new TrashCanBlock();
                    default -> throw new IllegalArgumentException("Unknown storage block: "+entry.id());
                });
                STORAGE_BLOCKS.put(entry.id(),block);
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),() -> entry.id().equals("output_chest")?new OutputChestItem(block.get()):new ResearchBlockItem(block.get())));
            } else if(entry.behavior().equals("resistant")) {
                RegistryObject<Block> block=BLOCKS.register(entry.id(),() -> new ResistantBlock(entry));
                RESISTANT_BLOCKS.put(entry.id(),block);
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),() -> new ResearchBlockItem(block.get())));
            } else if(entry.behavior().equals("projector")) {
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),() -> new ResearchBlockItem(PROJECTOR.get())));
            } else if(entry.behavior().equals("cargo")) {
                RegistryObject<Block> block=BLOCKS.register(entry.id(),() -> new CargoBlock(entry));
                CARGO_BLOCKS.put(entry.id(),block);
                CONTENT.put(entry.id(),ITEMS.register(entry.id(),() -> new ResearchBlockItem(block.get())));
            } else {
                CONTENT.put(entry.id(), ITEMS.register(entry.id(), () -> switch(entry.behavior()) {
                    case "backpack" -> new BackpackItem(entry.rows());
                    case "meat_jerky" -> new MeatJerkyItem(entry);
                    case "juice" -> new JuiceItem(entry);
                    case "cooler" -> new CoolerItem();
                    case "food" -> new SpecialFoodItem(entry);
                    case "magic_sugar" -> new MagicSugarItem();
                    case "seasonal_gift" -> new SeasonalGiftItem(entry);
                    case "containment_pickaxe" -> new ContainmentPickaxeItem();
                    case "broken_spawner" -> new SpawnerItems.Broken();
                    case "repaired_spawner" -> new SpawnerItems.Repaired();
                    case "soulbound" -> SoulboundEquipment.create(entry);
                    case "functional_rune" -> new FunctionalRuneItem(entry);
                    case "jetpack", "jetboots", "gadget_armor" -> GadgetArmorItem.create(entry);
                    case "steel_thruster" -> new SteelThrusterItem();
                    case "multi_tool" -> new MultiToolItem(entry);
                    case "magic_gadget" -> new MagicGadgetItem(entry);
                    case "measuring" -> new MeasuringItem(entry);
                    case "equipment_armor" -> EquipmentArmorItem.create(entry);
                    case "equipment_elytra" -> new EquipmentElytraItem(entry);
                    case "mining_pickaxe" -> new SpecialPickaxeItem(entry);
                    case "lumber_axe" -> new LumberAxeItem();
                    case "weapon" -> EquipmentWeapons.create(entry);
                    case "magic_utility" -> new MagicUtilityItem(entry);
                    case "infused_magnet" -> new InfusedMagnetItem();
                    case "talisman" -> new TalismanItem(entry);
                    case "explosive_shovel" -> new ShovelItem(Tiers.DIAMOND,1.5F,-3,new Item.Properties());
                    case "portable_teleporter" -> new PortableTeleporterItem();
                    case "strange_nether_goo" -> new StrangeNetherGoo();
                    case "experience_bottle" -> new net.minecraft.world.item.ExperienceBottleItem(new Item.Properties());
                    case "material" -> entry.implementation().equals("OrganicFood") || entry.implementation().equals("OrganicFertilizer") ? new OrganicMaterialItem() : new Item(new Item.Properties());
                    default -> new UtilityItem(entry.id());
                }));
            }
        }
    }
    public static final RegistryObject<BlockEntityType<MachineEntity>> MACHINE_ENTITY = ENTITIES.register("machine", () -> BlockEntityType.Builder.of(MachineEntity::new, MACHINES.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<AutoCrafterEntity>> AUTOCRAFTER_ENTITY=ENTITIES.register("autocrafter",()->BlockEntityType.Builder.of(AutoCrafterEntity::new,AUTOCRAFTERS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<AncientAltarEntity>> ANCIENT_ALTAR_ENTITY=ENTITIES.register("ancient_altar",()->BlockEntityType.Builder.of(AncientAltarEntity::new,ANCIENT_ALTAR.get()).build(null));
    public static final RegistryObject<BlockEntityType<AncientPedestalEntity>> ANCIENT_PEDESTAL_ENTITY=ENTITIES.register("ancient_pedestal",()->BlockEntityType.Builder.of(AncientPedestalEntity::new,ANCIENT_PEDESTAL.get()).build(null));
    public static final RegistryObject<BlockEntityType<ProcessingEntity>> PROCESSING_ENTITY=ENTITIES.register("processing",()->BlockEntityType.Builder.of(ProcessingEntity::new,PROCESSORS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<GeoMinerEntity>> GEO_MINER_ENTITY=ENTITIES.register("geo_miner",()->BlockEntityType.Builder.of(GeoMinerEntity::new,GEO_MINER.get()).build(null));
    public static final RegistryObject<BlockEntityType<ReactorEntity>> REACTOR_ENTITY=ENTITIES.register("reactor",()->BlockEntityType.Builder.of(ReactorEntity::new,REACTORS.get("nuclear_reactor").get(),REACTORS.get("netherstar_reactor").get()).build(null));
    public static final RegistryObject<BlockEntityType<ReactorPortEntity>> REACTOR_PORT_ENTITY=ENTITIES.register("reactor_access_port",()->BlockEntityType.Builder.of(ReactorPortEntity::new,REACTORS.get("reactor_access_port").get()).build(null));
    public static final RegistryObject<BlockEntityType<GpsTransmitterEntity>> GPS_TRANSMITTER_ENTITY=ENTITIES.register("gps_transmitter",()->BlockEntityType.Builder.of(GpsTransmitterEntity::new,GPS_TRANSMITTERS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<GpsTeleporterEntity>> GPS_TELEPORTER_ENTITY=ENTITIES.register("gps_teleporter",()->BlockEntityType.Builder.of(GpsTeleporterEntity::new,GPS_TELEPORTERS.entrySet().stream().filter(e->!e.getKey().equals("gps_teleporter_pylon")).map(e->e.getValue().get()).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<AndroidEntity>> ANDROID_ENTITY=ENTITIES.register("android",()->BlockEntityType.Builder.of(AndroidEntity::new,ANDROIDS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<AndroidInterfaceEntity>> ANDROID_INTERFACE_ENTITY=ENTITIES.register("android_interface",()->BlockEntityType.Builder.of(AndroidInterfaceEntity::new,ANDROID_INTERFACES.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<OutputChestEntity>> OUTPUT_CHEST_ENTITY = ENTITIES.register("output_chest",() -> BlockEntityType.Builder.of(OutputChestEntity::new,STORAGE_BLOCKS.get("output_chest").get()).build(null));
    public static final RegistryObject<BlockEntityType<IgnitionChamberEntity>> IGNITION_CHAMBER_ENTITY = ENTITIES.register("ignition_chamber",() -> BlockEntityType.Builder.of(IgnitionChamberEntity::new,STORAGE_BLOCKS.get("ignition_chamber").get()).build(null));
    public static final RegistryObject<BlockEntityType<TrashCanEntity>> TRASH_CAN_ENTITY = ENTITIES.register("trash_can",() -> BlockEntityType.Builder.of(TrashCanEntity::new,STORAGE_BLOCKS.get("trash_can_block").get()).build(null));
    public static final RegistryObject<BlockEntityType<CargoEntity>> CARGO_ENTITY = ENTITIES.register("cargo",() -> BlockEntityType.Builder.of(CargoEntity::new,CARGO_BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<ProjectorEntity>> PROJECTOR_ENTITY=ENTITIES.register("projector",() -> BlockEntityType.Builder.of(ProjectorEntity::new,PROJECTOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<ElevatorEntity>> ELEVATOR_ENTITY=ENTITIES.register("elevator",()->BlockEntityType.Builder.of(ElevatorEntity::new,ELEVATOR.get()).build(null));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.slimefun")).icon(() -> new ItemStack(GUIDE.get()))
        .displayItems((parameters, output) -> { output.accept(GUIDE.get()); output.accept(CREATIVE_GUIDE.get()); }).build());

    public SlimefunForge() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(AddonContent::freeze));
        ITEMS.register(bus); BLOCKS.register(bus); ENTITIES.register(bus);
        SERIALIZERS.register(bus); RECIPE_TYPES.register(bus); TABS.register(bus); MENUS.register(bus);
        MinecraftForge.EVENT_BUS.register(ManualMachines.class);
        MinecraftForge.EVENT_BUS.register(AddonMachines.class);
        MinecraftForge.EVENT_BUS.register(StorageLifecycle.class);
        MinecraftForge.EVENT_BUS.register(AutoCrafterBlock.class);
        ManualCraftingQueue.register(bus);
        CrucibleLiquids.register(bus);
        CompostingQueue.register(bus);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,CrucibleBlock.CONFIG,"slimefun_forge/slimefun-crucible-server.toml");
        IndustrialMiner.register(bus);
        MinecraftForge.EVENT_BUS.register(AncientRitualBlock.class);
        MinecraftForge.EVENT_BUS.register(AncientRituals.class);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,AncientAltarEntity.CONFIG,"slimefun_forge/slimefun-altar-server.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,MiningResources.CONFIG,"slimefun_forge/slimefun-server.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,AndroidTasks.CONFIG,"slimefun_forge/slimefun-android-server.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,MeatJerkyItem.CONFIG,"slimefun_forge/slimefun-jerky-server.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,StrangeNetherGoo.CONFIG,"slimefun_forge/slimefun-goo-server.toml");
        GoldPanDrops.register(bus);
        OreWashing.register(bus);
        MachineClock.register(bus);
        ItemAccess.register();
        OreGrinding.register(bus);
        Freezing.register(bus);
        Enchanting.register(bus);
        AnimalProduce.register(bus);
        ServerConfigReload.register(bus);
        Guide.register(bus);
        ResearchSettings.register();
        AddonSettings.register(bus);
        WorldPersistence.register();
        AdminCommands.register(bus);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,NetworkSettings.CONFIG,"slimefun_forge/slimefun-networks-server.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,MagicSettings.CONFIG,"slimefun_forge/slimefun-magic-server.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,BlockDrops.CONFIG,"slimefun_forge/slimefun-blocks-server.toml");
        Radiation.register();
        MinecraftForge.EVENT_BUS.register(UtilityItem.Events.class);
        MinecraftForge.EVENT_BUS.register(FoodEffects.class);
        MinecraftForge.EVENT_BUS.register(SeasonalGiftItem.class);
        MinecraftForge.EVENT_BUS.register(ContainmentPickaxeItem.class);
        MinecraftForge.EVENT_BUS.register(SpawnerItems.class);
        MinecraftForge.EVENT_BUS.register(Soulbinding.class);
        MinecraftForge.EVENT_BUS.register(AddonItems.class);
        MinecraftForge.EVENT_BUS.register(SoulboundEquipment.class);
        MinecraftForge.EVENT_BUS.register(FunctionalRuneItem.class);
        MinecraftForge.EVENT_BUS.register(FlightGadgets.class);
        MinecraftForge.EVENT_BUS.register(MultiToolItem.class);
        MinecraftForge.EVENT_BUS.register(GrapplingHooks.class);
        MinecraftForge.EVENT_BUS.register(EquipmentArmorItem.class);
        MinecraftForge.EVENT_BUS.register(EquipmentElytraItem.class);
        MinecraftForge.EVENT_BUS.register(EquipmentWeapons.class);
        MinecraftForge.EVENT_BUS.register(EquipmentDamage.class);
        MinecraftForge.EVENT_BUS.register(MagicUtilityItem.class);
        MinecraftForge.EVENT_BUS.register(InfusedMagnetItem.class);
        MinecraftForge.EVENT_BUS.register(TalismanItem.class);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,SpawnerItems.CONFIG,"slimefun_forge/slimefun-spawner-server.toml");
        MinecraftForge.EVENT_BUS.register(ProjectorEntity.class);
        MinecraftForge.EVENT_BUS.register(GpsWaypoints.class);
        MinecraftForge.EVENT_BUS.register(ElevatorMenu.class);
        MinecraftForge.EVENT_BUS.register(AndroidScripts.class);
        MinecraftForge.EVENT_BUS.register(GpsTeleportMenu.class);
        MinecraftForge.EVENT_BUS.register(ResistantBlock.class);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TagsUpdatedEvent event) -> MachineRecipe.invalidate());
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if(event.getPlayer()!=null) return;
            MachineRecipe.invalidate();
            for(var level : event.getPlayerList().getServer().getAllLevels()) EnergyNetwork.recipesReloaded(level);
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.level.LevelEvent.Unload event) -> {
            if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                EnergyNetwork.unload(level); CargoNetwork.unload(level); GpsSignals.unload(level); ProcessingEntity.unload(level);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent event) -> {
            for(var level : event.getServer().getAllLevels()) { EnergyNetwork.unload(level); CargoNetwork.unload(level); ProcessingEntity.unload(level); }
            GpsSignals.stop(event.getServer()); MachineRecipe.invalidate();
        });
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("sf").then(literal("status").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.slimefun.port_status",CONTENT.size()), false); return 1;
        })));
    }
    public static Item item(String id) {
        var value = CONTENT.get(id);
        if (value == null) throw new IllegalArgumentException("Unregistered Slimefun item " + id);
        return value.get();
    }
    public static ResourceLocation key(String path) { return new ResourceLocation(ID, path); }
}
