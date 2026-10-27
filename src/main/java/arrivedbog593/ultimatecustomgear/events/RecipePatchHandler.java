package arrivedbog593.ultimatecustomgear.events;

import arrivedbog593.ultimatecustomgear.data.RecipePatchData;
import arrivedbog593.ultimatecustomgear.datapack.RecipeLoader;
import arrivedbog593.ultimatecustomgear.datapack.RecipePatchLoader;
import arrivedbog593.ultimatecustomgear.util.RecipeQueries;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies the "remove" and "replace" modes of every recipe_patch to the
 * server's live recipe list, after each datapack load — at world start, on
 * /reload and on /customgear reload.
 * <p>
 * WHY NOT FILES. Overriding a recipe file removes or replaces it by id, but
 * "remove_output" has to find recipes by what they make, which is only known
 * once every recipe is loaded; and a file override only wins when this pack
 * loads above the mod that owns the recipe. Working on the loaded list does
 * all of it the same way, and lets a replacement default to the original's
 * output.
 * <p>
 * TIMING. AddReloadListenerEvent hands over the new RecipeManager before it is
 * filled; TagsUpdatedEvent (SERVER_DATA_LOAD) fires once the whole load has
 * finished and before the recipes are sent to players — on /reload and at
 * startup alike. So clients, JEI and /customgear recipes all see the patched
 * list.
 */
public final class RecipePatchHandler {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    /** Patches from the last content load; swapped whole on /customgear reload. */
    private static volatile List<RecipePatchData> patches = List.of();

    /** RecipeManager of the datapack load in progress, until its tags are bound. */
    private static volatile RecipeManager loading;

    private RecipePatchHandler() {}

    public static void setPatches(List<RecipePatchData> loaded) {
        patches = List.copyOf(loaded);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        loading = event.getServerResources().getRecipeManager();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        RecipeManager recipes = loading;
        loading = null;
        if (recipes != null) apply(recipes, event.getRegistryAccess(), patches);
    }

    // ── Apply ────────────────────────────────────────────────────────────────

    /** One patch's remove_output, with the recipe_types that narrow it (empty = any). */
    private record OutputRule(Set<Item> items, Set<ResourceLocation> types) {}

    private static void apply(RecipeManager recipes, HolderLookup.Provider registries,
                              List<RecipePatchData> patches) {
        Set<ResourceLocation> byId = new LinkedHashSet<>();
        List<OutputRule> byOutput = new ArrayList<>();
        Map<ResourceLocation, RecipePatchData.Replacement> replacements = new LinkedHashMap<>();
        for (RecipePatchData patch : patches) {
            if (patch.remove != null) {
                for (String raw : patch.remove) {
                    ResourceLocation id = parse(raw, "recipe id");
                    if (id != null) byId.add(id);
                }
            }
            OutputRule rule = outputRule(patch);
            if (rule != null) byOutput.add(rule);
            if (patch.replace != null) {
                for (Map.Entry<String, RecipePatchData.Replacement> entry : patch.replace.entrySet()) {
                    ResourceLocation id = parse(entry.getKey(), "recipe id");
                    if (id == null || entry.getValue() == null) continue;
                    if (replacements.putIfAbsent(id, entry.getValue()) != null) {
                        LOGGER.warn("[CustomGear] recipe_patch: '{}' is replaced by more than one patch — "
                                + "only the first one is used.", id);
                    }
                }
            }
        }
        if (byId.isEmpty() && byOutput.isEmpty() && replacements.isEmpty()) return;

        List<RecipeHolder<?>> result = new ArrayList<>();
        Set<ResourceLocation> foundIds = new HashSet<>();
        Set<Item> foundItems = new HashSet<>();
        int removed = 0, replaced = 0;

        for (RecipeHolder<?> holder : recipes.getRecipes()) {
            ResourceLocation id = holder.id();

            // Replace first: an explicit swap beats any removal that would also match.
            RecipePatchData.Replacement replacement = replacements.get(id);
            if (replacement != null) {
                foundIds.add(id);
                if (byId.contains(id)) {
                    LOGGER.warn("[CustomGear] recipe_patch: '{}' is both removed and replaced — "
                            + "it is replaced.", id);
                }
                RecipeHolder<?> swapped = buildReplacement(holder, replacement, registries);
                if (swapped != null) {
                    result.add(swapped);
                    replaced++;
                } else {
                    result.add(holder);
                }
                continue;
            }

            if (byId.contains(id)) {
                foundIds.add(id);
                LOGGER.debug("[CustomGear] recipe_patch removed {} (by id)", id);
                removed++;
                continue;
            }
            Item output = matchingOutput(holder, registries, byOutput);
            if (output != null) {
                foundItems.add(output);
                LOGGER.debug("[CustomGear] recipe_patch removed {} (makes {})",
                        id, BuiltInRegistries.ITEM.getKey(output));
                removed++;
                continue;
            }
            result.add(holder);
        }

        if (removed > 0 || replaced > 0) recipes.replaceRecipes(result);
        LOGGER.info("[CustomGear] recipe_patch: {} recipes removed, {} replaced", removed, replaced);

        for (ResourceLocation id : byId) {
            if (!foundIds.contains(id)) warnNotFound(id, "no recipe has the id '" + id + "'");
        }
        for (ResourceLocation id : replacements.keySet()) {
            if (!foundIds.contains(id)) {
                warnNotFound(id, "no recipe has the id '" + id + "', so nothing is replaced");
            }
        }
        for (OutputRule rule : byOutput) {
            for (Item item : rule.items()) {
                if (!foundItems.contains(item)) {
                    LOGGER.info("[CustomGear] recipe_patch: no recipe{} has '{}' as its main output — "
                                    + "nothing removed for it. /customgear recipes shows what makes it.",
                            rule.types().isEmpty() ? "" : " of the listed recipe_types",
                            BuiltInRegistries.ITEM.getKey(item));
                }
            }
        }
    }

    // ── Replace ──────────────────────────────────────────────────────────────

    /**
     * The new recipe under the original's id, or null to keep the original.
     * Built with the same RecipeLoader an item's own recipes use, then decoded
     * here instead of loaded from a file — so a mistake costs this one recipe
     * and an error naming it, never the datapack load.
     */
    private static RecipeHolder<?> buildReplacement(RecipeHolder<?> original,
                                                    RecipePatchData.Replacement replacement,
                                                    HolderLookup.Provider registries) {
        ResourceLocation id = original.id();
        boolean passthrough = "passthrough".equals(replacement.type);

        String output;
        boolean sameOutput = false;
        if (passthrough) {
            if (replacement.result != null) {
                LOGGER.warn("[CustomGear] recipe_patch replace '{}': 'result' is ignored by passthrough — "
                        + "its result is inside 'json'.", id);
            }
            output = id.toString(); // only names the recipe in log messages
        } else if (replacement.result != null) {
            ResourceLocation rl = parse(replacement.result, "result item");
            if (rl == null) return null;
            output = rl.toString();
        } else {
            Item item = RecipeQueries.mainOutput(original, registries);
            if (item == Items.AIR) {
                LOGGER.warn("[CustomGear] recipe_patch replace '{}': the original recipe doesn't say what it "
                        + "makes — add a 'result' to the replacement. Kept the original.", id);
                return null;
            }
            output = BuiltInRegistries.ITEM.getKey(item).toString();
            sameOutput = true;
        }

        JsonObject json = RecipeLoader.buildRecipeJson(output, replacement);
        if (json == null) return null;

        // Same output and no result_count: keep the original count too, so
        // swapping ingredients doesn't quietly turn "makes 4" into "makes 1".
        if (sameOutput && replacement.resultCount == null && json.get("result") instanceof JsonObject result) {
            ItemStack reported = RecipeQueries.reportedResult(original, registries);
            if (reported.getCount() > 1 && result.has("id")) result.addProperty("count", reported.getCount());
        }

        if (!modsLoaded(json.remove("neoforge:conditions"))) {
            LOGGER.debug("[CustomGear] recipe_patch replace '{}': its recipe type's mod is not installed — "
                    + "kept the original.", id);
            return null;
        }

        DataResult<Recipe<?>> decoded = Recipe.CODEC.parse(
                registries.createSerializationContext(JsonOps.INSTANCE), json);
        Recipe<?> recipe = decoded.result().orElse(null);
        if (recipe == null) {
            LOGGER.error("[CustomGear] recipe_patch replace '{}': the new recipe is invalid — kept the "
                            + "original. {}", id,
                    decoded.error().map(DataResult.Error::message).orElse(""));
            return null;
        }
        LOGGER.debug("[CustomGear] recipe_patch replaced {}", id);
        return new RecipeHolder<>(id, recipe);
    }

    /** Evaluates the mod_loaded conditions a built recipe carries; null means none. */
    private static boolean modsLoaded(JsonElement conditions) {
        if (conditions == null || !conditions.isJsonArray()) return true;
        for (JsonElement element : conditions.getAsJsonArray()) {
            if (!(element instanceof JsonObject condition) || !condition.has("modid")) continue;
            if (!ModList.get().isLoaded(condition.get("modid").getAsString())) return false;
        }
        return true;
    }

    // ── Remove ───────────────────────────────────────────────────────────────

    /**
     * The item a rule removes this recipe for, or null to keep it. Recipes added
     * by a recipe_patch are never matched: "remove the old recipes for X, add a
     * new one" would otherwise remove the new one too.
     */
    private static Item matchingOutput(RecipeHolder<?> holder, HolderLookup.Provider registries,
                                       List<OutputRule> rules) {
        if (rules.isEmpty()) return null;
        ResourceLocation id = holder.id();
        if (id.getNamespace().equals("customgear")
                && id.getPath().startsWith(RecipePatchLoader.PATCH_PREFIX)) return null;

        Item output = null;
        ResourceLocation type = null;
        for (OutputRule rule : rules) {
            if (output == null) {
                // Computed once, and only when some rule is left to check.
                output = RecipeQueries.mainOutput(holder, registries);
                type = RecipeQueries.typeId(holder);
            }
            if (rule.items().contains(output)
                    && (rule.types().isEmpty() || rule.types().contains(type))) return output;
        }
        return null;
    }

    private static OutputRule outputRule(RecipePatchData patch) {
        if (patch.removeOutput == null || patch.removeOutput.isEmpty()) return null;

        Set<Item> items = new HashSet<>();
        for (String raw : patch.removeOutput) {
            ResourceLocation id = parse(raw, "item id");
            if (id == null) continue;
            BuiltInRegistries.ITEM.getOptional(id).ifPresentOrElse(items::add,
                    () -> warnNotFound(id, "'" + id + "' in remove_output is not a registered item"));
        }

        Set<ResourceLocation> types = new HashSet<>();
        if (patch.recipeTypes != null) {
            for (String raw : patch.recipeTypes) {
                ResourceLocation id = parse(raw, "recipe type");
                if (id == null) continue;
                if (!BuiltInRegistries.RECIPE_TYPE.containsKey(id)) {
                    warnNotFound(id, "'" + id + "' in recipe_types is not a registered recipe type");
                }
                types.add(id);
            }
            if (types.isEmpty() && !patch.recipeTypes.isEmpty()) {
                // Every listed type was malformed. Matching all types instead
                // would remove far more than the patch asked for.
                LOGGER.warn("[CustomGear] recipe_patch: no valid recipe_types — its remove_output "
                        + "is skipped rather than applied to every type.");
                return null;
            }
        }
        return items.isEmpty() ? null : new OutputRule(items, types);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static ResourceLocation parse(String raw, String what) {
        if (raw == null || raw.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(raw.trim());
        if (id == null) {
            LOGGER.warn("[CustomGear] recipe_patch: malformed {} '{}' — skipped. "
                    + "Expected 'namespace:path'.", what, raw);
        }
        return id;
    }

    /**
     * Warns only when the namespace belongs to a LOADED mod — then a missing id
     * is a typo. An unloaded mod is the normal case for a patch meant for
     * several modpacks, and stays at debug.
     */
    private static void warnNotFound(ResourceLocation id, String message) {
        String ns = id.getNamespace();
        if (ns.equals("minecraft") || ModList.get().isLoaded(ns)) {
            LOGGER.warn("[CustomGear] recipe_patch: {} although '{}' is loaded — "
                    + "this looks like a typo.", message, ns);
        } else {
            LOGGER.debug("[CustomGear] recipe_patch: {} ('{}' is not installed)", message, ns);
        }
    }
}
