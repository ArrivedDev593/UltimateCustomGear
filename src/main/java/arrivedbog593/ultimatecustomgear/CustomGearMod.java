package arrivedbog593.ultimatecustomgear;

import arrivedbog593.ultimatecustomgear.client.ClientSetup;
import arrivedbog593.ultimatecustomgear.client.CustomGearKeys;
import arrivedbog593.ultimatecustomgear.commands.CustomGearCommandHandler;
import arrivedbog593.ultimatecustomgear.config.CustomGearConfig;
import arrivedbog593.ultimatecustomgear.data.BlockData;
import arrivedbog593.ultimatecustomgear.data.ContainerContentData;
import arrivedbog593.ultimatecustomgear.data.GearData;
import arrivedbog593.ultimatecustomgear.events.*;
import arrivedbog593.ultimatecustomgear.items.containers.CustomContainerBlock;
import arrivedbog593.ultimatecustomgear.items.containers.CustomContainerBlockEntity;
import arrivedbog593.ultimatecustomgear.datapack.*;
import arrivedbog593.ultimatecustomgear.parser.GearParser;
import arrivedbog593.ultimatecustomgear.parser.UniversalParser;
import arrivedbog593.ultimatecustomgear.registry.*;
import arrivedbog593.ultimatecustomgear.network.CustomGearNetworking;
import arrivedbog593.ultimatecustomgear.resources.DynamicResourcePack;
import arrivedbog593.ultimatecustomgear.resources.TextureLoader;
import arrivedbog593.ultimatecustomgear.util.ContentHasher;
import arrivedbog593.ultimatecustomgear.util.ContentRoots;
import arrivedbog593.ultimatecustomgear.util.GlobalIdValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mod("ultimatecustomgear")
public class CustomGearMod {

    public static final String MOD_ID = "ultimatecustomgear";
    public static DynamicResourcePack DYNAMIC_PACK;

    /**
     * The ultimatecustomgear config folder, resolved against the actual game
     * directory. Paths.get(".") depends on the process working directory,
     * which some launchers do NOT set to .minecraft — FMLPaths.GAMEDIR is
     * always the real game dir on both client and dedicated server.
     */
    public static Path configFolder() {
        return FMLPaths.GAMEDIR.get().resolve(MOD_ID);
    }

    public CustomGearMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the config FIRST, before anything might read it
        modContainer.registerConfig(ModConfig.Type.COMMON, CustomGearConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, CustomGearConfig.CLIENT_SPEC);

        Path configFolder = configFolder();

        List<GearData> gearList;
        UniversalParser.LoadResult universalResult;
        GlobalIdValidator.Result validated;

        try (ContentRoots roots = ContentRoots.open(configFolder)) {
            // 1. Read all JSONs (loose folder + every zip in packs/)
            gearList = GearParser.loadAll(roots);
            universalResult = UniversalParser.loadAll(roots);

            // 1.5. Global final-ID validation — drops colliding entries instead of crashing
            validated = GlobalIdValidator.validate(
                    gearList, universalResult.items, universalResult.blocks,
                    universalResult.fluids, universalResult.containers);

            // Capture the content hash of what THIS instance just loaded
            // (across ALL roots, zips included) — the handshake compares
            // these captured values, never the disk at join time. After the
            // validation, so the files it dropped stay out of the hash.
            roots.excludeEntries(validated.dropped);
            ContentHasher.capture(roots);

            // A placeable container IS a block for every resource purpose:
            // model, blockstate, lang, loot table, tags and recipe all come from
            // the same loaders. A BACKPACK is not — it has no blockstate, no
            // loot table, and its lang key is item.* — so feeding it to the
            // block loaders produces a stone-textured model and an untranslated
            // name, which is exactly what happened.
            List<BlockData> allBlocks = new ArrayList<>(validated.blocks);
            List<ContainerContentData> backpacks = new ArrayList<>();
            for (ContainerContentData c : validated.containers) {
                if (c.container != null && c.container.isBlock()) allBlocks.add(c);
                else backpacks.add(c);
            }

            // 2. Register all items, blocks and fluids (validated lists only)
            GearRegistry.register(modEventBus, validated.gear);
            ItemRegistry.register(modEventBus, validated.items);
            BlockRegistry.register(modEventBus, validated.blocks);
            ContainerRegistry.register(modEventBus, validated.containers);
            CustomGearTab.register(modEventBus);
            FluidRegistry.register(modEventBus, validated.fluids);
            MenuRegistry.register(modEventBus);
            ComponentRegistry.register(modEventBus);
            modEventBus.addListener(ClientSetup::onRegisterMenuScreens);

            // 3. Create the dynamic resource pack
            DYNAMIC_PACK = new DynamicResourcePack(
                    new PackLocationInfo(
                            "customgear_dynamic",
                            Component.literal("CustomGear Dynamic Pack"),
                            PackSource.BUILT_IN,
                            Optional.empty()
                    )
            );

            // 4. Load textures and lang (client resources)
            TextureLoader.loadAll(DYNAMIC_PACK, validated.gear,
                    validated.items, allBlocks, validated.fluids, backpacks);
            TextureLoader.generateLang(DYNAMIC_PACK, validated.gear,
                    validated.items, allBlocks, validated.fluids, backpacks);

            // 5. Generate recipe JSONs (server data)
            RecipeLoader.loadAll(DYNAMIC_PACK, validated.gear,
                    validated.items, allBlocks);

            // 5.1. recipe_patch: additions are files, removals run after every datapack load
            RecipePatchLoader.loadAll(DYNAMIC_PACK, universalResult.recipePatches);
            RecipePatchHandler.setPatches(universalResult.recipePatches);


            // 5.6. Tag files: one shared builder, emitted once at 5.9
            TagFileBuilder tagFiles = new TagFileBuilder();
            BlockTagLoader.loadAll(tagFiles, allBlocks);
            GearTagLoader.loadAll(tagFiles, validated.gear);

            // 5.7. Generate loot tables (server data)
            BlockLootLoader.loadAll(DYNAMIC_PACK, allBlocks);

            // 5.8. User-declared item/block/fluid tags — same builder
            ItemTagLoader.loadAll(tagFiles, validated.items, allBlocks, validated.fluids);

            // 5.85. Foreign content declared into tags — same builder
            TagPatchLoader.loadAll(tagFiles, universalResult.tagPatches);
            CuriosTagLoader.loadAll(DYNAMIC_PACK, tagFiles, validated.containers);

            // 5.9. Emit every tag file — must come after ALL tag sources
            tagFiles.emit(DYNAMIC_PACK);
        }

        // 6. Register the pack, client setup, event handlers and networking
        modEventBus.addListener(this::onAddPackFinders);
        modEventBus.addListener(CustomGearNetworking::registerPayloads);
        modEventBus.addListener(CustomGearNetworking::registerConfigurationTasks);
        modEventBus.addListener(this::onRegisterCapabilities);
        if (FMLEnvironment.getDist().isClient()) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
            modEventBus.addListener(CustomGearKeys::register);
            modEventBus.addListener(ClientSetup::onRegisterRenderers);
            modEventBus.addListener(ClientSetup::onRegisterTooltipComponents);
            modEventBus.addListener(ClientSetup::onRegisterFluidModels);
            NeoForge.EVENT_BUS.register(ClientSetup.class);
        }
        NeoForge.EVENT_BUS.register(CustomGearCommandHandler.class);
        NeoForge.EVENT_BUS.register(SetBonusHandler.class);
        NeoForge.EVENT_BUS.register(HeldEffectHandler.class);
        NeoForge.EVENT_BUS.register(ArrowDamageHandler.class);
        NeoForge.EVENT_BUS.register(MobDropHandler.class);
        NeoForge.EVENT_BUS.register(DamageResistanceHandler.class);
        NeoForge.EVENT_BUS.register(RecipePatchHandler.class);
    }

    /**
     * Item handler for every container block.
     * <p>
     * REDIRECTED FOR DOUBLE CHESTS. The shell half holds no inventory, so
     * without this a hopper feeding that side would push items into a container
     * nobody reads and that gets discarded when the pair splits — silent item
     * loss, not just a cosmetic gap. Jade and comparators go through the same
     * path and were showing the shell as empty for the same reason.
     */
    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        for (var holder : ContainerRegistry.BLOCKS.getEntries()) {
            event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, _, _) -> {
                BlockPos target = state.getBlock() instanceof CustomContainerBlock block
                        ? block.inventoryPos(state, pos)
                        : pos;
                return level.getBlockEntity(target) instanceof CustomContainerBlockEntity c
                        ? net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(c)
                        : null;
            }, holder.get());
        }
    }

    private void onAddPackFinders(AddPackFindersEvent event) {
        // Register for both CLIENT_RESOURCES (textures, models, lang)
        // and SERVER_DATA (recipes) using the same dynamic pack instance
        if (event.getPackType() == PackType.CLIENT_RESOURCES
                || event.getPackType() == PackType.SERVER_DATA) {
            event.addRepositorySource(consumer -> {
                // DYNAMIC_PACK.location() already carries the content-hash
                // KnownPack, recomputed automatically after any pack mutation.
                // This lambda runs on every pack-repository rescan, so after
                // /customgear reload + /reload the version reflects the new
                // contents with no extra work here.
                Pack pack = Pack.readMetaAndCreate(
                        DYNAMIC_PACK.location(),
                        new Pack.ResourcesSupplier() {
                            @Override
                            @NotNull
                            public PackResources openPrimary(@NotNull PackLocationInfo info) {
                                return DYNAMIC_PACK;
                            }

                            @Override
                            @NotNull
                            public PackResources openFull(@NotNull PackLocationInfo info,
                                                          Pack.@NotNull Metadata metadata) {
                                return DYNAMIC_PACK;
                            }
                        },
                        event.getPackType(),
                        new PackSelectionConfig(true, Pack.Position.TOP, false)
                );
                if (pack != null) consumer.accept(pack);
            });
        }
    }
}