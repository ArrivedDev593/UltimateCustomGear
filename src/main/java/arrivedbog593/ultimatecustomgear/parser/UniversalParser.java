package arrivedbog593.ultimatecustomgear.parser;

import arrivedbog593.ultimatecustomgear.data.*;
import arrivedbog593.ultimatecustomgear.util.ContentRoots;
import arrivedbog593.ultimatecustomgear.util.ParserUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class UniversalParser {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    new com.google.gson.reflect.TypeToken<List<RecipeData>>(){}.getType(),
                    new RecipeListDeserializer()
            )
            .create();

    private static final Set<String> GEAR_TYPES = Set.of(
            "armor_set", "tool_set", "weapon_set",
            "sword", "bow", "crossbow", "shield",
            "pickaxe", "axe", "shovel", "hoe"
    );

    /** The slot types Curios ships. Others may exist if another mod adds them. */
    private static final Set<String> KNOWN_CURIOS_SLOTS = Set.of(
            "back", "belt", "body", "bracelet", "charm",
            "curio", "hands", "head", "necklace", "ring");

    public static class LoadResult {
        public final List<BlockData>       blocks       = new ArrayList<>();
        public final List<FluidData>       fluids       = new ArrayList<>();
        public final List<ItemData>        items        = new ArrayList<>();
        public final List<AdvancementData> advancements = new ArrayList<>();
        public final List<TagPatchData>    tagPatches   = new ArrayList<>();
        public final List<RecipePatchData> recipePatches = new ArrayList<>();
        public final List<ContainerContentData> containers = new ArrayList<>();
    }

    /**
     * Loads blocks/fluids/items from every content root: the loose folder
     * plus every zip mounted from packs/. Cache keys are prefixed with the
     * root label so zip entries never collide with loose files.
     */
    public static LoadResult loadAll(ContentRoots contentRoots) {
        LoadResult result = new LoadResult();

        Path configFolder = contentRoots.configFolder();
        if (!ParserUtils.ensureFolderExists(configFolder)) return result;

        GenericCache<BlockData> blockCache = GenericCache.load(
                configFolder, "block_cache.json",
                new TypeToken<Map<String, GenericCache.CacheEntry<BlockData>>>() {}.getType()
        );
        GenericCache<FluidData> fluidCache = GenericCache.load(
                configFolder, "fluid_cache.json",
                new TypeToken<Map<String, GenericCache.CacheEntry<FluidData>>>() {}.getType()
        );
        GenericCache<ItemData> itemCache = GenericCache.load(
                configFolder, "item_cache.json",
                new TypeToken<Map<String, GenericCache.CacheEntry<ItemData>>>() {}.getType()
        );
        GenericCache<ContainerContentData> containerCache = GenericCache.load(
                configFolder, "container_cache.json",
                new TypeToken<Map<String, GenericCache.CacheEntry<ContainerContentData>>>() {}.getType()
        );

        Set<String> seenBlocks = new HashSet<>();
        Set<String> seenFluids = new HashSet<>();
        Set<String> seenItems  = new HashSet<>();

        Set<String>   currentKeys   = new HashSet<>();
        AtomicBoolean cacheModified = new AtomicBoolean(false);

        for (ContentRoots.Root root : contentRoots.roots()) {
            // Symlink escapes are only possible on the real filesystem;
            // zip virtual filesystems cannot reference paths outside the zip.
            Path canonicalRoot = root.isLoose() ? ParserUtils.resolveCanonical(root.path()) : null;
            if (root.isLoose() && canonicalRoot == null) continue;

            try (var stream = Files.walk(root.path())) {
                stream.filter(p -> p.toString().endsWith(".json")
                                && !root.path().relativize(p).startsWith(".cache")
                                && !root.path().relativize(p).startsWith("packs"))
                        .sorted(ParserUtils.walkOrder(root.path()))
                        .forEach(path -> {
                            if (root.isLoose() && !ParserUtils.isSafeChild(path, canonicalRoot)) {
                                LOGGER.warn("[CustomGear] Skipping file outside ultimatecustomgear/ dir: {}", path);
                                return;
                            }

                            String relKey = root.label() + root.path().relativize(path);
                            currentKeys.add(relKey);

                            try {
                                String json = Files.readString(path);
                                String type = ParserUtils.extractType(json);

                                if (type == null || GEAR_TYPES.contains(type)) return;

                                FileTime ft           = Files.getLastModifiedTime(path);
                                long     lastModified = ft.toMillis();

                                switch (type) {
                                    case "block" -> {
                                        GenericCache.CacheEntry<BlockData> cached = blockCache.get(relKey);
                                        boolean fromCache =
                                                cached != null && cached.lastModified == lastModified && cached.data != null;
                                        BlockData data = fromCache
                                                ? cached.data
                                                : GSON.fromJson(json, BlockData.class);

                                        // Validation runs on cache hits too. The cache key is
                                        // path + mtime, so an untouched file would skip it
                                        // forever — and the rules validation applies belong to
                                        // the MOD, not to the file. Something legal in one
                                        // version and rejected in the next would keep loading
                                        // until someone happened to edit it, and a new
                                        // deprecation warning would never reach the packs that
                                        // most need it. The cache exists to avoid I/O and JSON
                                        // parsing, not a handful of field checks.
                                        if (validateBlock(data, path)) {
                                            if (!seenBlocks.add(data.id)) {
                                                contentRoots.exclude(path);
                                                LOGGER.error("[CustomGear] Duplicate block ID '{}' — skipping: {}", data.id, relKey);
                                                return;
                                            }
                                            result.blocks.add(data);
                                            contentRoots.recordSource(data, path);
                                            if (fromCache) {
                                                LOGGER.debug("[CustomGear] Block cache hit: {}", relKey);
                                            } else {
                                                blockCache.put(relKey, lastModified, data);
                                                cacheModified.set(true);
                                                LOGGER.info("[CustomGear] Block loaded: {}", data.id);
                                            }
                                        }else if (fromCache) {
                                            // Cached but no longer valid: drop the entry so the
                                            // file is parsed fresh next time and the cache does
                                            // not keep something the mod already rejects.
                                            blockCache.remove(relKey);
                                            cacheModified.set(true);
                                        }
                                    }
                                    case "fluid" -> {
                                        GenericCache.CacheEntry<FluidData> cached = fluidCache.get(relKey);
                                        boolean fromCache =
                                                cached != null && cached.lastModified == lastModified && cached.data != null;
                                        FluidData data = fromCache
                                                ? cached.data
                                                : GSON.fromJson(json, FluidData.class);

                                        if (validateFluid(data, path)) {
                                            if (!seenFluids.add(data.id)) {
                                                contentRoots.exclude(path);
                                                LOGGER.error("[CustomGear] Duplicate fluid ID '{}' — skipping: {}", data.id, relKey);
                                                return;
                                            }
                                            result.fluids.add(data);
                                            contentRoots.recordSource(data, path);
                                            if (fromCache) {
                                                LOGGER.debug("[CustomGear] Fluid cache hit: {}", relKey);
                                            } else {
                                                fluidCache.put(relKey, lastModified, data);
                                                cacheModified.set(true);
                                                LOGGER.info("[CustomGear] Fluid loaded: {}", data.id);
                                            }
                                        } else if (fromCache) {
                                            // Cached but no longer valid: drop the entry so the
                                            // file is parsed fresh next time and the cache does
                                            // not keep something the mod already rejects.
                                            fluidCache.remove(relKey);
                                            cacheModified.set(true);
                                        }
                                    }
                                    case "item", "food" -> {
                                        GenericCache.CacheEntry<ItemData> cached = itemCache.get(relKey);
                                        boolean fromCache =
                                                cached != null && cached.lastModified == lastModified && cached.data != null;
                                        ItemData data = fromCache
                                                ? cached.data
                                                : GSON.fromJson(json, ItemData.class);

                                        if (validateItem(data, path)) {
                                            if (!seenItems.add(data.id)) {
                                                contentRoots.exclude(path);
                                                LOGGER.error("[CustomGear] Duplicate item ID '{}' — skipping: {}", data.id, relKey);
                                                return;
                                            }
                                            result.items.add(data);
                                            contentRoots.recordSource(data, path);
                                            if (fromCache) {
                                                LOGGER.debug("[CustomGear] Item cache hit: {}", relKey);
                                            } else {
                                                itemCache.put(relKey, lastModified, data);
                                                cacheModified.set(true);
                                                LOGGER.info("[CustomGear] Item loaded: {}", data.id);
                                            }
                                        } else  if (fromCache) {
                                            // Cached but no longer valid: drop the entry so the
                                            // file is parsed fresh next time and the cache does
                                            // not keep something the mod already rejects.
                                            itemCache.remove(relKey);
                                            cacheModified.set(true);
                                        }
                                    }
                                    case "advancement" -> {
                                        AdvancementData data = GSON.fromJson(json, AdvancementData.class);
                                        if (validateAdvancement(data, path)) {
                                            result.advancements.add(data);
                                            LOGGER.debug("[CustomGear] Advancement parsed (not yet active): {}", data.id);
                                        }
                                    }
                                    case "tag_patch" -> {
                                        TagPatchData data = GSON.fromJson(json, TagPatchData.class);
                                        if (validateTagPatch(data, path)) {
                                            result.tagPatches.add(data);
                                            LOGGER.info("[CustomGear] Tag patch loaded: {} -> {}",
                                                    data.registry, data.tag);
                                        }
                                    }
                                    case "recipe_patch" -> {
                                        RecipePatchData data = GSON.fromJson(json, RecipePatchData.class);
                                        if (validateRecipePatch(data, path)) {
                                            result.recipePatches.add(data);
                                            LOGGER.info("[CustomGear] Recipe patch loaded: {}", path.getFileName());
                                        }
                                    }
                                    case "container" -> {
                                        GenericCache.CacheEntry<ContainerContentData> cached = containerCache.get(relKey);
                                        boolean fromCache =
                                                cached != null && cached.lastModified == lastModified && cached.data != null;
                                        ContainerContentData data = fromCache
                                                ? cached.data
                                                : GSON.fromJson(json, ContainerContentData.class);

                                        if (validateContainer(data, path)) {
                                            if (!seenBlocks.add(data.id)) {
                                                contentRoots.exclude(path);
                                                LOGGER.error("[CustomGear] Duplicate ID '{}' — skipping: {}", data.id, relKey);
                                                return;
                                            }
                                            result.containers.add(data);
                                            contentRoots.recordSource(data, path);
                                            if (fromCache) {
                                                LOGGER.debug("[CustomGear] Container cache hit: {}", relKey);
                                            } else {
                                                containerCache.put(relKey, lastModified, data);
                                                cacheModified.set(true);
                                                LOGGER.info("[CustomGear] Container loaded: {} ({} slots, {})",
                                                        data.id, data.container.slots, data.container.kind());
                                            }
                                        } else  if (fromCache) {
                                            // Cached but no longer valid: drop the entry so the
                                            // file is parsed fresh next time and the cache does
                                            // not keep something the mod already rejects.
                                            containerCache.remove(relKey);
                                            cacheModified.set(true);
                                        }
                                    }
                                    default -> LOGGER.warn("[CustomGear] Unknown type '{}' in: {}", type, path.getFileName());
                                }

                            } catch (Exception e) {
                                LOGGER.error("[CustomGear] Error reading {}: {}", path.getFileName(), e.getMessage());
                            }
                        });
            } catch (IOException e) {
                LOGGER.error("[CustomGear] Error scanning {}: {}",
                        root.isLoose() ? "folder" : root.label(), e.getMessage());
            }
        }

        if (blockCache.removeStale(currentKeys)) cacheModified.set(true);
        if (fluidCache.removeStale(currentKeys)) cacheModified.set(true);
        if (itemCache.removeStale(currentKeys))  cacheModified.set(true);
        if (containerCache.removeStale(currentKeys)) cacheModified.set(true);

        if (cacheModified.get()) {
            blockCache.save();
            fluidCache.save();
            itemCache.save();
            containerCache.save();
        }

        LOGGER.info("[CustomGear] Loaded: {} blocks, {} fluids, {} items, {} containers",
                result.blocks.size(), result.fluids.size(), result.items.size(),
                result.containers.size());

        return result;
    }

    // ── Validations ──────────────────────────────────────────────────────────

    private static boolean validateBlock(BlockData data, Path path) {
        if (data.id == null || data.id.isBlank()) {
            LOGGER.warn("[CustomGear] Block missing 'id': {}", path.getFileName());
            return false;
        }
        if (!data.id.matches("^[a-z][a-z0-9_]{1,63}$")) {
            LOGGER.warn("[CustomGear] Block ID does not match format: {}", data.id);
            return false;
        }
        return true;
    }

    private static boolean validateFluid(FluidData data, Path path) {
        if (data.id == null || data.id.isBlank()) {
            LOGGER.warn("[CustomGear] Fluid missing 'id': {}", path.getFileName());
            return false;
        }
        if (!data.id.matches("^[a-z][a-z0-9_]{1,63}$")) {
            LOGGER.warn("[CustomGear] Fluid ID does not match format: {}", data.id);
            return false;
        }

        // Declaring one of the two and not the other is always a mistake: the
        // fluid would draw its own still texture and water's flowing one, or the
        // other way round. Declaring NEITHER is fine — that is a water reskin,
        // and it is what gives the fluid water's blue tint.
        if (data.texture != null && data.texture.refs != null) {
            boolean still   = data.texture.refs.get("still") != null;
            boolean flowing = data.texture.refs.get("flowing") != null;
            if (still != flowing) {
                LOGGER.warn("[CustomGear] Fluid '{}' declares '{}' but not '{}' — a fluid needs "
                                + "both textures or neither: {}",
                        data.id, still ? "still" : "flowing", still ? "flowing" : "still",
                        path.getFileName());
                return false;
            }
        }
        return true;
    }

    private static boolean validateItem(ItemData data, Path path) {
        if (data.id == null || data.id.isBlank()) {
            LOGGER.warn("[CustomGear] Item missing 'id': {}", path.getFileName());
            return false;
        }
        if (!data.id.matches("^[a-z][a-z0-9_]{1,63}$")) {
            LOGGER.warn("[CustomGear] Item ID does not match format: {}", data.id);
            return false;
        }
        if (data.mobDrops != null) {
            if (data.mobDrops.entities == null || data.mobDrops.entities.isEmpty()) {
                LOGGER.warn("[CustomGear] Item '{}': mob_drops has no 'entities' — the drop is "
                        + "DISABLED. Use \"entities\": [\"all\"] for every mob, or list specific "
                        + "ids/#tags/mod:* wildcards", data.id);
                // Not a hard error: the item is still valid, its drop is just off.
            }
            if (data.mobDrops.chance < 0 || data.mobDrops.chance > 1) {
                LOGGER.warn("[CustomGear] Item '{}': mob_drops.chance must be 0.0-1.0 (got {})",
                        data.id, data.mobDrops.chance);
                return false;
            }
            if (data.mobDrops.min < 0) {
                LOGGER.warn("[CustomGear] Item '{}': mob_drops.min cannot be negative (got {})",
                        data.id, data.mobDrops.min);
                return false;
            }
            if (data.mobDrops.max < data.mobDrops.min) {
                LOGGER.warn("[CustomGear] Item '{}': mob_drops.max ({}) is lower than min ({})",
                        data.id, data.mobDrops.max, data.mobDrops.min);
                return false;
            }
            if (data.mobDrops.min == 0 && data.mobDrops.max == 0) {
                LOGGER.warn("[CustomGear] Item '{}': mob_drops min and max are both 0 — the drop "
                        + "will never produce anything", data.id);
            }
            if (data.mobDrops.requiresPlayerKill == null) {
                LOGGER.warn("[CustomGear] Item '{}': mob_drops.requires_player_kill is not "
                                + "declared. The default CHANGED to false in 1.6.0 for vanilla parity — "
                                + "mobs killed by the environment now drop this item, so automated farms "
                                + "can produce it. Declare it explicitly to silence this warning.",
                        data.id);
            }
            String mode = data.mobDrops.lootingMode;
            if (mode != null && !mode.equals("count") && !mode.equals("chance")
                    && !mode.equals("none")) {
                LOGGER.warn("[CustomGear] Item '{}': unknown looting_mode '{}' — expected "
                                + "'count', 'chance' or 'none'. Falling back to 'count'.",
                        data.id, mode);
            }
            if (!data.mobDrops.isLootingChance() && data.mobDrops.lootingChanceBonus != 0.01) {
                LOGGER.warn("[CustomGear] Item '{}': looting_chance_bonus only applies with "
                        + "looting_mode 'chance' — ignored.", data.id);
            }
            if (data.mobDrops.isLootingChance() && data.mobDrops.max <= 0) {
                LOGGER.warn("[CustomGear] Item '{}': looting_mode 'chance' with max 0 can never "
                        + "drop anything — 'chance' mode raises the probability but leaves the "
                        + "amount alone, so the roll still comes up empty. Use 'count' if you "
                        + "want Looting to create items from a 0 base.", data.id);
            }
        }
        return true;
    }

    private static boolean validateAdvancement(AdvancementData data, Path path) {
        if (data.id == null || data.id.isBlank()) {
            LOGGER.warn("[CustomGear] Advancement missing 'id': {}", path.getFileName());
            return false;
        }
        if (data.targetId == null || data.targetId.isBlank()) {
            LOGGER.warn("[CustomGear] Advancement '{}' missing 'targetId': {}", data.id, path.getFileName());
            return false;
        }
        return true;
    }

    private static boolean validateTagPatch(TagPatchData data, Path path) {
        if (data.tag == null || data.tag.isBlank()) {
            LOGGER.warn("[CustomGear] tag_patch missing 'tag': {}", path.getFileName());
            return false;
        }
        if (data.normalizedRegistry() == null) {
            LOGGER.warn("[CustomGear] tag_patch '{}' missing or invalid 'registry': {}",
                    data.tag, path.getFileName());
            return false;
        }
        boolean hasValues = data.values != null && !data.values.isEmpty();
        boolean hasRemove = data.remove != null && !data.remove.isEmpty();
        if (!hasValues && !hasRemove) {
            LOGGER.warn("[CustomGear] tag_patch '{}' has neither 'values' nor 'remove' — "
                    + "nothing to do: {}", data.tag, path.getFileName());
            return false;
        }
        return true;
    }

    private static boolean validateRecipePatch(RecipePatchData data, Path path) {
        boolean hasRemove = data.remove != null && !data.remove.isEmpty();
        boolean hasRemoveOutput = data.removeOutput != null && !data.removeOutput.isEmpty();
        boolean hasAdd = data.add != null && !data.add.isEmpty();
        boolean hasReplace = data.replace != null && !data.replace.isEmpty();
        if (!hasRemove && !hasRemoveOutput && !hasAdd && !hasReplace) {
            LOGGER.warn("[CustomGear] recipe_patch has none of 'add', 'remove', 'remove_output' or "
                    + "'replace' — nothing to do: {}", path.getFileName());
            return false;
        }
        if (data.recipeTypes != null && !data.recipeTypes.isEmpty() && !hasRemoveOutput) {
            // Accepted: the rest of the patch is fine. But 'recipe_types' only narrows
            // remove_output, and someone who wrote it expected it to do something.
            LOGGER.warn("[CustomGear] recipe_patch: 'recipe_types' only applies to 'remove_output', "
                    + "which this patch doesn't have — ignored: {}", path.getFileName());
        }
        return true;
    }

    /**
     * A container needs its container object, a variant, and a slot count. The
     * upper bound on slots is a WARNING, not an error: 128 is where vanilla's
     * click packet gives up, but only for an action that changes that many
     * slots at once — which is hard to reach outside creative. The owner
     * decides whether that trade is worth it; the mod says clearly what it
     * costs.
     */
    private static boolean validateContainer(ContainerContentData data, Path path) {
        if (!validateBlock(data, path)) return false;   // id, type, names, texture…

        if (data.container == null) {
            LOGGER.error("[CustomGear] Container '{}': missing the 'container' object — "
                    + "without it this is just a block. Use \"type\": \"block\" instead, "
                    + "or add \"container\": {{ \"type\": \"barrel\", \"slots\": N }}.", data.id);
            return false;
        }

        // Required, never guessed: the variant decides the shape, how the block
        // is placed, and whether breaking it keeps the contents. Defaulting it
        // would decide that last one behind the author's back.
        if (data.container.kind == null || data.container.kind.isBlank()) {
            LOGGER.error("[CustomGear] Container '{}': 'container.type' is required — "
                            + "expected one of {}.",
                    data.id, String.join(", ", ContainerData.KINDS));
            return false;
        }

        String kind = data.container.kind();
        if (!ContainerData.isKnownKind(kind)) {
            LOGGER.error("[CustomGear] Container '{}': unknown container type '{}' — "
                            + "expected one of {}.",
                    data.id, data.container.kind, String.join(", ", ContainerData.KINDS));
            return false;
        }

        if (data.container.slots < 1) {
            LOGGER.error("[CustomGear] Container '{}': 'slots' is {} — must be at least 1.",
                    data.id, data.container.slots);
            return false;
        }

        if (data.container.slots > ContainerData.CLICK_PACKET_LIMIT) {
            LOGGER.warn("[CustomGear] Container '{}' has {} slots, above the {} that vanilla's "
                            + "click packet can carry in one action. The container works, but any single "
                            + "action changing more than {} slots at once — a long drag, a mass "
                            + "shift-click — DISCONNECTS the player with an encoder error. Hard to reach "
                            + "in survival, trivial in creative.",
                    data.id, data.container.slots, ContainerData.CLICK_PACKET_LIMIT, ContainerData.CLICK_PACKET_LIMIT);
        }

        // A backpack has no block to store an inventory in, so the contents live
        // in the item or nowhere. The raw field is checked, not the accessor —
        // the accessor already answers true for a backpack, so asking it would
        // never catch the mistake.
        if (ContainerData.KIND_BACKPACK.equals(kind)
                && Boolean.FALSE.equals(data.container.keepsContents)) {
            LOGGER.error("[CustomGear] Container '{}': a backpack cannot set "
                            + "'keeps_contents' to false — it has no block to store an inventory in.",
                    data.id);
            return false;
        }

        // A container that keeps its contents carries them inside the ItemStack,
        // and the whole stack is re-sent every time the item moves in an
        // inventory. Slot count therefore costs bandwidth here in a way it does
        // not for a container that spills.
        if (data.container.keepsContents() && data.container.slots > ContainerData.CLICK_PACKET_LIMIT) {
            LOGGER.warn("[CustomGear] Container '{}' keeps its contents AND has {} slots — "
                            + "the dropped item carries the whole inventory, and the entire stack "
                            + "travels the network every time it moves in an inventory.",
                    data.id, data.container.slots);
        }

        if (data.container.columns != null && data.container.columns > data.container.slots) {
            LOGGER.warn("[CustomGear] Container '{}': 'columns' ({}) is greater than 'slots' ({}) — "
                            + "the extra columns are simply never drawn.",
                    data.id, data.container.columns, data.container.slots);
        }

        if (!data.container.curiosSlots().isEmpty()) {
            if (!ContainerData.KIND_BACKPACK.equals(kind)) {
                LOGGER.error("[CustomGear] Container '{}': 'curios_slots' only means anything on a "
                        + "backpack — a placeable container is not equipped.", data.id);
                return false;
            }
            for (String slot : data.container.curiosSlots()) {
                if (slot == null || slot.isBlank()) {
                    LOGGER.error("[CustomGear] Container '{}': empty entry in 'curios_slots'.",
                            data.id);
                    return false;
                }
                // A warning, never a rejection: another mod may have added a slot
                // type this list knows nothing about, and refusing it would break
                // a pack that is perfectly correct on that player's install.
                if (!KNOWN_CURIOS_SLOTS.contains(slot)) {
                    LOGGER.warn("[CustomGear] Container '{}': '{}' is not one of Curios' own slot "
                                    + "types ({}). It will still be emitted — another mod may "
                                    + "provide it — but check the spelling if the container never "
                                    + "appears in a slot.",
                            data.id, slot, String.join(", ", KNOWN_CURIOS_SLOTS));
                }
            }
        }

        // Block fields mean nothing on a backpack. Same warning path as everywhere
        // else: a field a type ignores is a warning, never a rejection.
        // (Reachable once backpacks exist; kept here so the list lives with the data.)
        return validateContainerTextureKeys(data);
    }

    /**
     * Each subtype takes a different shape of texture declaration, and none of
     * them takes all of them. A barrel is six faces; a chest is a 64x64 unwrap
     * under a single key. Silently ignoring a key from the wrong subtype is the
     * worst outcome — the author sees vanilla's texture and has no idea why.
     */
    private static boolean validateContainerTextureKeys(ContainerContentData data) {
        if (data.texture == null || data.texture.refs == null) return true;

        Set<String> valid = switch (data.container.kind()) {
            case ContainerData.KIND_BARREL ->
                    Set.of("top", "bottom", "side", "north", "south", "east", "west", "top_open");
            case ContainerData.KIND_CHEST ->
                    Set.of("single", "left", "right");
            case ContainerData.KIND_SHULKER ->
                    Set.of("single");
            case ContainerData.KIND_BACKPACK ->
                    Set.of("item");
            default -> Set.of();
        };

        boolean ok = true;
        for (String key : data.texture.refs.keySet()) {
            if (!valid.contains(key)) {
                LOGGER.error("[CustomGear] Container '{}' is a {}: texture key '{}' means nothing "
                                + "here. Valid keys: {}.",
                        data.id, data.container.kind(), key,
                        valid.stream().sorted().collect(java.util.stream.Collectors.joining(", ")));
                ok = false;
            }
        }
        return ok;
    }
}