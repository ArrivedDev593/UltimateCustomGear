package arrivedbog593.ultimatecustomgear.commands;

import arrivedbog593.ultimatecustomgear.CustomGearMod;
import arrivedbog593.ultimatecustomgear.data.BlockData;
import arrivedbog593.ultimatecustomgear.data.ContainerContentData;
import arrivedbog593.ultimatecustomgear.data.GearData;
import arrivedbog593.ultimatecustomgear.datapack.*;
import arrivedbog593.ultimatecustomgear.events.RecipePatchHandler;
import arrivedbog593.ultimatecustomgear.parser.GearParser;
import arrivedbog593.ultimatecustomgear.parser.UniversalParser;
import arrivedbog593.ultimatecustomgear.registry.*;
import arrivedbog593.ultimatecustomgear.resources.ContainerTextures;
import arrivedbog593.ultimatecustomgear.resources.DiskPackSink;
import arrivedbog593.ultimatecustomgear.resources.TextureLoader;
import arrivedbog593.ultimatecustomgear.util.ContentHasher;
import arrivedbog593.ultimatecustomgear.util.ContentRoots;
import arrivedbog593.ultimatecustomgear.util.GlobalIdValidator;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.ReloadCommand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static arrivedbog593.ultimatecustomgear.CustomGearMod.DYNAMIC_PACK;

public class CustomGearCommandHandler {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    private static final long RELOAD_COOLDOWN_MS = 5_000L;
    private static final AtomicLong lastReloadTime = new AtomicLong(0L);

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("customgear")
                .then(RecipesCommand.build(event.getBuildContext()))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();

                            // FIX: cooldown anti-spam
                            long now = System.currentTimeMillis();
                            long last = lastReloadTime.get();
                            long remaining = RELOAD_COOLDOWN_MS - (now - last);
                            if (remaining > 0) {
                                source.sendFailure(Component.translatable(
                                        "customgear.command.reload.cooldown", remaining / 1000 + 1));
                                return 0;
                            }
                            lastReloadTime.set(now);

                            try {
                                Path customgear = CustomGearMod.configFolder();

                                GlobalIdValidator.Result validated;

                                try (ContentRoots roots = ContentRoots.open(customgear)) {
                                    // 1. Reload all JSONs (loose folder + every zip in packs/)
                                    List<GearData> gearList = GearParser.loadAll(roots);
                                    UniversalParser.LoadResult universalResult = UniversalParser.loadAll(roots);

                                    // 1.5. Same global validation as startup — keeps runtime maps consistent
                                    validated = GlobalIdValidator.validate(
                                            gearList, universalResult.items, universalResult.blocks,
                                            universalResult.fluids, universalResult.containers);

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

                                    // 2. Clear the previous dynamic pack
                                    DYNAMIC_PACK.clear();

                                    // 3. Reload textures and languages
                                    TextureLoader.loadAll(DYNAMIC_PACK, validated.gear,
                                            validated.items, allBlocks, validated.fluids, backpacks);
                                    TextureLoader.generateLang(DYNAMIC_PACK, validated.gear,
                                            validated.items, allBlocks, validated.fluids, backpacks);

                                    // 3.5. Regenerate recipes
                                    RecipeLoader.loadAll(DYNAMIC_PACK, validated.gear,
                                            validated.items, allBlocks);

                                    // 3.55. recipe_patch: removals apply in the datapack reload at step 6
                                    RecipePatchLoader.loadAll(DYNAMIC_PACK, universalResult.recipePatches);
                                    RecipePatchHandler.setPatches(universalResult.recipePatches);

                                    // 3.6. Tag files: one shared builder, emitted once at 5.9
                                    TagFileBuilder tagFiles = new TagFileBuilder();
                                    BlockTagLoader.loadAll(tagFiles, allBlocks);
                                    GearTagLoader.loadAll(tagFiles, validated.gear);
                                    CuriosTagLoader.loadAll(DYNAMIC_PACK, tagFiles, validated.containers);

                                    // 3.7. Generate loot tables (server data)
                                    BlockLootLoader.loadAll(DYNAMIC_PACK, allBlocks);

                                    // 3.8. User-declared item/block/fluid tags — same builder
                                    ItemTagLoader.loadAll(tagFiles, validated.items, allBlocks, validated.fluids);

                                    // 3.85. Foreign content declared into tags — same builder
                                    TagPatchLoader.loadAll(tagFiles, universalResult.tagPatches);


                                    // 3.9. Emit every tag file — must come after ALL tag sources
                                    tagFiles.emit(DYNAMIC_PACK);

                                    // 4. Reload fluid, item and block data in registries
                                    FluidRegistry.updateFluidData(validated.fluids);
                                    ItemRegistry.updateItemData(validated.items);
                                    BlockRegistry.updateBlockData(validated.blocks);
                                    ContainerRegistry.updateContainerData(validated.containers);
                                    ContainerTextures.clearCache();

                                    // 5. Atomic swap of the GEAR_MAP
                                    updateGearRegistryAtomic(validated.gear);

                                    // 5.5. Re-capture the content hash so new connections
                                    // validate against the reloaded content (zips included)
                                    roots.excludeEntries(validated.dropped);
                                    ContentHasher.capture(roots);
                                }

                                // 6. Trigger the vanilla data pack reload so the
                                // regenerated recipes take effect immediately.
                                //
                                // Rescan first: reloadPacks works from the repository's
                                // current view of the packs, and DYNAMIC_PACK was
                                // repopulated after that view was built. Without this the
                                // reload reads the previous contents, which is why a second
                                // /customgear reload appeared to be needed for anything that
                                // is read once at load — the Curios slot assignment being
                                // the case that surfaced it.
                                source.getServer().getPackRepository().reload();
                                ReloadCommand.reloadPacks(
                                        source.getServer().getPackRepository().getSelectedIds(),
                                        source);

                                // 7. Notify user
                                source.sendSuccess(
                                        () -> Component.translatable("customgear.command.reload.success"),
                                        true
                                );

                                return 1;

                            } catch (Exception e) {
                                // FIX: don't expose internal paths or stack traces to the player
                                source.sendFailure(
                                        Component.translatable("customgear.command.reload.error"));
                                // The full detail only goes to the server log
                                LOGGER.error("[CustomGear] Reload failed", e);
                                return 0;
                            }
                        })
                )
                .then(Commands.literal("dump")
                        .requires(src -> src.hasPermission(2))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            try {
                                // A timestamped folder: dumps are diagnostic, so never
                                // delete anything the user might still be looking at.
                                Path outDir = CustomGearMod.configFolder()
                                        .resolve("dump")
                                        .resolve(DateTimeFormatter
                                                .ofPattern("yyyy-MM-dd_HH-mm-ss")
                                                .format(LocalDateTime.now()));

                                DiskPackSink sink = new DiskPackSink(outDir);

                                try (ContentRoots roots = ContentRoots.open(CustomGearMod.configFolder())) {
                                    List<GearData> gearList = GearParser.loadAll(roots);
                                    UniversalParser.LoadResult universalResult = UniversalParser.loadAll(roots);

                                    GlobalIdValidator.Result validated = GlobalIdValidator.validate(
                                            gearList, universalResult.items,
                                            universalResult.blocks, universalResult.fluids, universalResult.containers);

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
                                    // Same generation sequence as reload, different destination.
                                    // NOTE: no DYNAMIC_PACK.clear(), no registry swap, no
                                    // ContentHasher.capture — a diagnostic dump must not move
                                    // the hash the multiplayer handshake compares against.
                                    TextureLoader.loadAll(sink, validated.gear,
                                            validated.items, allBlocks, validated.fluids, backpacks);
                                    TextureLoader.generateLang(sink, validated.gear,
                                            validated.items, allBlocks, validated.fluids, backpacks);
                                    RecipeLoader.loadAll(sink, validated.gear,
                                            validated.items, allBlocks);
                                    // Additions only: removals are not files, and a dump
                                    // must not change the live patch list.
                                    RecipePatchLoader.loadAll(sink, universalResult.recipePatches);
                                    BlockLootLoader.loadAll(sink, allBlocks);

                                    TagFileBuilder tagFiles = new TagFileBuilder();
                                    BlockTagLoader.loadAll(tagFiles, allBlocks);
                                    GearTagLoader.loadAll(tagFiles, validated.gear);
                                    CuriosTagLoader.loadAll(sink, tagFiles, validated.containers);
                                    ItemTagLoader.loadAll(tagFiles, validated.items,
                                            allBlocks, validated.fluids);
                                    TagPatchLoader.loadAll(tagFiles, universalResult.tagPatches);
                                    tagFiles.emit(sink);
                                }

                                LOGGER.info("[CustomGear] Dumped {} files ({} failed) to {}",
                                        sink.written(), sink.failed(), outDir);
                                source.sendSuccess(() -> Component.translatable(
                                        "customgear.command.dump.success",
                                        sink.written(), outDir.toString()), true);
                                return 1;

                            } catch (Exception e) {
                                source.sendFailure(Component.translatable(
                                        "customgear.command.dump.error"));
                                LOGGER.error("[CustomGear] Dump failed", e);
                                return 0;
                            }
                        })
                )
        );
    }

    /**
     * Builds new maps from scratch and swaps them in atomically via
     * {@link GearRegistry#atomicSwap}. This eliminates the clear() + put() window
     * where the map was temporarily empty and could cause NPEs in server ticks.
     */
    private static void updateGearRegistryAtomic(List<GearData> gearList) {
        Map<ResourceLocation, GearData> newGear     = new HashMap<>();
        Map<ResourceLocation, String>   newToolType = new HashMap<>();

        for (GearData data : gearList) {
            switch (data.type) {
                case "armor_set" -> {
                    if (data.pieces != null) {
                        for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
                            if (!data.pieces.containsKey(piece)) continue;
                            newGear.put(rl(data.id + "_" + piece), data);
                        }
                    }
                }
                case "sword", "bow", "crossbow", "shield",
                     "pickaxe", "axe", "shovel", "hoe" -> newGear.put(rl(data.id), data);

                case "tool_set" -> {
                    if (data.tools != null) {
                        for (String toolType : new String[]{"pickaxe", "axe", "shovel", "hoe"}) {
                            if (!data.tools.containsKey(toolType)) continue;
                            GearData derived = GearRegistry.buildDerived(data, toolType, data.tools.get(toolType));
                            ResourceLocation loc = rl(data.id + "_" + toolType);
                            newGear.put(loc, derived);
                            newToolType.put(loc, toolType);
                        }
                    }
                }
                case "weapon_set" -> {
                    if (data.weapons != null) {
                        for (String weaponType : new String[]{"sword", "bow", "crossbow", "shield"}) {
                            if (!data.weapons.containsKey(weaponType)) continue;
                            GearData derived = GearRegistry.buildWeaponDerived(data, weaponType, data.weapons.get(weaponType));
                            newGear.put(rl(data.id + "_" + weaponType), derived);
                        }
                    }
                }
            }
        }

        GearRegistry.atomicSwap(newGear, newToolType);
    }

    private static ResourceLocation rl(String id) {
        return ResourceLocation.fromNamespaceAndPath("customgear", id);
    }
}
