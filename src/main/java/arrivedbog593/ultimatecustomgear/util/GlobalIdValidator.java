package arrivedbog593.ultimatecustomgear.util;

import arrivedbog593.ultimatecustomgear.data.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates that every FINAL derived ID is unique across all content types
 * BEFORE anything is handed to a DeferredRegister.
 * <p>
 * Per-parser duplicate checks (GearParser seenIds, UniversalParser seenBlocks/
 * seenFluids/seenItems) only catch duplicates within the same type. They cannot
 * catch cross-type collisions, which crash the game at registry freeze time:
 * <ul>
 *   <li>item "ruby" + block "ruby"            → both register Item "customgear:ruby"</li>
 *   <li>item "myset_sword" + weapon_set "myset" (with sword) → "customgear:myset_sword"</li>
 *   <li>item "oil_bucket" + fluid "oil"        → "customgear:oil_bucket"</li>
 *   <li>fluid "lava_x" + fluid "lava_x_flowing" → Fluid "customgear:lava_x_flowing"</li>
 * </ul>
 * Collision domains (must mirror what the registries actually create):
 * <ul>
 *   <li>ITEM registry:  gear items (with set suffixes), items/food,
 *                       BlockItems (same id as block), buckets (id + "_bucket")</li>
 *   <li>BLOCK registry: blocks, LiquidBlocks (same id as fluid)</li>
 *   <li>FLUID registry: fluid id and id + "_flowing"</li>
 * </ul>
 * Each entry claims ALL of its derived IDs atomically: if any single ID is
 * already taken, the whole entry is dropped (nothing partially registered)
 * and a clear error names both the loser and the current owner.
 * <p>
 * Priority is load order: gear → items → blocks → fluids. This matches the
 * current registration order in CustomGearMod, so behavior for non-conflicting
 * content is unchanged.
 * <p>
 * IMPORTANT: if you ever change how IDs are derived in GearRegistry,
 * BlockRegistry or FluidRegistry, update {@link #finalItemIds(GearData)} and
 * the claim logic here to match, or the validator will drift from reality.
 */
public final class GlobalIdValidator {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    // Must match the valid-type arrays used in GearRegistry / the reload command.
    private static final String[] ARMOR_PIECES = {"helmet", "chestplate", "leggings", "boots"};
    private static final String[] TOOL_TYPES   = {"pickaxe", "axe", "shovel", "hoe"};
    private static final String[] WEAPON_TYPES = {"sword", "bow", "crossbow", "shield"};

    private GlobalIdValidator() {}

    /** Filtered, collision-free lists. Feed THESE to the registries and loaders. */
    public static final class Result {
        public final List<GearData>  gear   = new ArrayList<>();
        public final List<ItemData>  items  = new ArrayList<>();
        public final List<BlockData> blocks = new ArrayList<>();
        public final List<FluidData> fluids = new ArrayList<>();
        public final List<ContainerContentData> containers = new ArrayList<>();
        public int droppedEntries = 0;
        /** The dropped entries themselves, so their files can be left out of the content hash. */
        public final List<Object> dropped = new ArrayList<>();
    }

    public static Result validate(List<GearData> gearList, List<ItemData> itemList,
                                  List<BlockData> blockList, List<FluidData> fluidList,
                                  List<ContainerContentData> containerList) {

        // finalId → human-readable owner ("gear 'myset' (weapon_set)", etc.)
        Map<String, String> itemIds  = new HashMap<>();
        Map<String, String> blockIds = new HashMap<>();
        Map<String, String> fluidIds = new HashMap<>();

        Result result = new Result();

        // ── 1. Gear (highest priority — registers first today) ──────────────
        for (GearData data : gearList) {
            String owner = "gear '" + data.id + "' (" + data.type + ")";
            List<Claim> claims = new ArrayList<>();
            for (String id : finalItemIds(data)) {
                claims.add(new Claim(itemIds, "item", id));
            }
            if (tryClaimAll(claims, owner)) {
                result.gear.add(data);
            } else {
                result.droppedEntries++;
                result.dropped.add(data);
            }
        }

        // ── 2. Simple items / food ───────────────────────────────────────────
        for (ItemData data : itemList) {
            String owner = "item '" + data.id + "'"
                    + ("food".equals(data.type) ? " (food)" : "");
            List<Claim> claims = List.of(new Claim(itemIds, "item", data.id));
            if (tryClaimAll(claims, owner)) {
                result.items.add(data);
            } else {
                result.droppedEntries++;
                result.dropped.add(data);
            }
        }

        // ── 3. Blocks (register a Block AND a BlockItem with the same id) ───
        for (BlockData data : blockList) {
            String owner = "block '" + data.id + "'";
            List<Claim> claims = List.of(
                    new Claim(blockIds, "block", data.id),
                    new Claim(itemIds,  "item",  data.id)   // the BlockItem
            );
            if (tryClaimAll(claims, owner)) {
                result.blocks.add(data);
            } else {
                result.droppedEntries++;
                result.dropped.add(data);
            }
        }

        // ── 3.5. Containers — a placeable one claims the same IDs as any block ──
        // Same registries, same derived names: a Block plus a BlockItem. Only
        // registration treats them differently, so they must compete with blocks
        // for IDs here or a collision surfaces at registry freeze.
        //
        // A BACKPACK claims only the item id: it registers no Block, and
        // reserving one would block a real block from using that name for
        // nothing.
        for (ContainerContentData data : containerList) {
            String owner = "container '" + data.id + "' (" + data.container.kind() + ")";
            List<Claim> claims = data.container.isBlock()
                    ? List.of(new Claim(blockIds, "block", data.id),
                    new Claim(itemIds,  "item",  data.id))
                    : List.of(new Claim(itemIds,  "item",  data.id));
            if (tryClaimAll(claims, owner)) {
                result.containers.add(data);
            } else {
                result.droppedEntries++;
                result.dropped.add(data);
            }
        }

        // ── 4. Fluids (fluid + "_flowing" fluid + LiquidBlock + bucket item) ─
        for (FluidData data : fluidList) {
            String owner = "fluid '" + data.id + "'";
            List<Claim> claims = List.of(
                    new Claim(fluidIds, "fluid", data.id),
                    new Claim(fluidIds, "fluid", data.id + "_flowing"),
                    new Claim(blockIds, "block", data.id),                // LiquidBlock
                    new Claim(itemIds,  "item",  data.id + "_bucket")     // BucketItem
            );
            if (tryClaimAll(claims, owner)) {
                result.fluids.add(data);
            } else {
                result.droppedEntries++;
                result.dropped.add(data);
            }
        }

        if (result.droppedEntries > 0) {
            LOGGER.error("[CustomGear] {} entr{} dropped due to final-ID collisions. "
                            + "See errors above — each names the conflicting IDs and both owners. "
                            + "Rename one of the conflicting JSONs' 'id' fields and restart.",
                    result.droppedEntries, result.droppedEntries == 1 ? "y was" : "ies were");
        } else {
            LOGGER.info("[CustomGear] Global ID validation passed: {} item IDs, {} block IDs, {} fluid IDs — no collisions",
                    itemIds.size(), blockIds.size(), fluidIds.size());
        }

        return result;
    }

    /**
     * All ITEM-registry IDs a GearData will create.
     * Must stay in sync with GearRegistry.register* methods.
     */
    public static List<String> finalItemIds(GearData data) {
        List<String> ids = new ArrayList<>();
        switch (data.type) {
            case "armor_set" -> {
                if (data.pieces != null) {
                    for (String piece : ARMOR_PIECES) {
                        if (data.pieces.containsKey(piece)) ids.add(data.id + "_" + piece);
                    }
                }
            }
            case "tool_set" -> {
                if (data.tools != null) {
                    for (String tool : TOOL_TYPES) {
                        if (data.tools.containsKey(tool)) ids.add(data.id + "_" + tool);
                    }
                }
            }
            case "weapon_set" -> {
                if (data.weapons != null) {
                    for (String weapon : WEAPON_TYPES) {
                        if (data.weapons.containsKey(weapon)) ids.add(data.id + "_" + weapon);
                    }
                }
            }
            // Individual types: sword, bow, crossbow, shield, pickaxe, axe, shovel, hoe
            default -> ids.add(data.id);
        }
        return ids;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** One (registry map, registry name, final id) tuple to claim. */
    private record Claim(Map<String, String> registry, String registryName, String id) {}

    /**
     * Atomic claim: first verifies EVERY id is free, then claims them all.
     * On the first conflict, logs a detailed error and claims nothing.
     */
    private static boolean tryClaimAll(List<Claim> claims, String owner) {
        for (Claim c : claims) {
            String existing = c.registry.get(c.id);
            if (existing != null) {
                LOGGER.error("[CustomGear] ID COLLISION in {} registry: 'customgear:{}' is claimed by "
                                + "both {} and {}. The entire entry {} will NOT be registered. "
                                + "Rename one of the two 'id' fields to fix this.",
                        c.registryName, c.id, existing, owner, owner);
                return false;
            }
        }
        for (Claim c : claims) {
            c.registry.put(c.id, owner);
        }
        return true;
    }
}