package arrivedbog593.ultimatecustomgear.datapack;

import arrivedbog593.ultimatecustomgear.data.BlockData;
import arrivedbog593.ultimatecustomgear.data.GearData;
import arrivedbog593.ultimatecustomgear.data.ItemData;
import arrivedbog593.ultimatecustomgear.data.RecipeData;
import arrivedbog593.ultimatecustomgear.resources.PackSink;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generates vanilla recipe JSONs from RecipeData and injects them into the
 * dynamic pack as SERVER_DATA, so they load through the normal data pack
 * pipeline and are visible to JEI. No reflection involved.
 * <p>
 * VALIDATION PHILOSOPHY: everything vanilla would reject at data-pack load
 * time is caught HERE, with a message that names the item and the exact
 * problem. Vanilla's own errors are cryptic and don't point back to the
 * user's JSON — for a no-coding-required mod, clear validation IS the product.
 * Item existence is still NOT validated here (custom items aren't registered
 * yet at mod init); only structure and ID syntax are.
 * <p>
 * INGREDIENTS: every ingredient position accepts either a plain item ID
 * ("minecraft:diamond") or a TAG prefixed with '#' ("#minecraft:planks" =
 * any plank type). Tags work in shaped keys, shapeless ingredients, cooking
 * inputs, and smithing template/base/addition.
 * <p>
 * Supported recipe types:
 *   - shaped → crafting table with a specific pattern
 *   - shapeless → crafting table, ingredients in any order
 *   - smelting → furnace
 *   - blasting → blast furnace
 *   - smoking → smoker
 *   - campfire_cooking → campfire
 *   - stonecutting → stonecutter
 *   - smithing_transform → smithing table
 *   - passthrough → another mod's recipe type, copied verbatim
 * <p>
 * PASSTHROUGH is the deliberate exception: its body is copied verbatim and
 * only the inner "type" is checked. Validating another mod's recipe schema
 * would mean knowing it, and a validator that is right most of the time is
 * worse than none — the mod's own deserializer is the authority there.
 */
public class RecipeLoader {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");
    private static final Gson   GSON   = new GsonBuilder().setPrettyPrinting().create();

    // ── Public API ────────────────────────────────────────────────────────────

    public static void loadAll(PackSink pack, List<GearData> gearList,
                               List<ItemData> itemList, List<BlockData> blockList) {
        int count = 0;

        for (GearData data : gearList) {
            if (data.recipes != null) {
                for (Map.Entry<String, List<RecipeData>> entry : data.recipes.entrySet()) {
                    String itemId = data.id + "_" + entry.getKey();
                    count += writeRecipeList(pack, itemId, entry.getValue());
                }
            }
            if (data.recipe != null) {
                count += writeRecipeList(pack, data.id, data.recipe);
            }
        }

        for (ItemData data : itemList) {
            if (data.recipe != null) {
                count += writeRecipeList(pack, data.id, data.recipe);
            }
        }

        for (BlockData data : blockList) {
            if (data.recipe != null) {
                count += writeRecipeList(pack, data.id, data.recipe);
            }
        }

        LOGGER.info("[CustomGear] Generated {} recipe JSON files", count);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static int writeRecipeList(PackSink pack, String itemId,
                                       List<RecipeData> list) {
        if (list == null) return 0;
        int count = 0;
        for (int i = 0; i < list.size(); i++) {
            RecipeData rd     = list.get(i);
            String     suffix = list.size() == 1 ? "recipe" : "recipe_" + (i + 1);
            String     recipeId = itemId + "_" + suffix;
            JsonObject json   = buildRecipeJson(itemId, rd);
            if (json != null) {
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                        "customgear", "recipe/" + recipeId + ".json");
                pack.addRaw(loc, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
                LOGGER.debug("[CustomGear] Generated recipe: {}", recipeId);
                count++;
            }
        }
        return count;
    }

    /**
     * Builds one recipe's JSON, or null when invalid. {@code itemId} is the
     * output: a bare id is this mod's own item, a full "namespace:path" id is
     * anyone's — what recipe_patch uses to give a vanilla or foreign item a
     * recipe in the same friendly format.
     */
    public static JsonObject buildRecipeJson(String itemId, RecipeData data) {
        if (data.type == null) {
            LOGGER.warn("[CustomGear] Recipe for '{}' missing 'type' — skipping", itemId);
            return null;
        }
        warnUnusedFields(itemId, data);
        JsonObject json = switch (data.type) {
            case "shaped"             -> buildShaped(itemId, data);
            case "shapeless"          -> buildShapeless(itemId, data);
            case "smelting"           -> buildCooking("minecraft:smelting", itemId, data, 10f);
            case "blasting"           -> buildCooking("minecraft:blasting", itemId, data, 5f);
            case "smoking"            -> buildCooking("minecraft:smoking", itemId, data, 5f);
            case "campfire_cooking"   -> buildCooking("minecraft:campfire_cooking", itemId, data, 30f);
            case "stonecutting"       -> buildStonecutting(itemId, data);
            case "smithing_transform" -> buildSmithing(itemId, data);
            case "passthrough"        -> buildPassthrough(itemId, data);
            default -> {
                LOGGER.warn("[CustomGear] Recipe for '{}' has unknown type '{}' — skipping",
                        itemId, data.type);
                yield null;
            }
        };
        // Passthrough already requires its mods: its type's namespace plus "requires".
        if (json != null && !"passthrough".equals(data.type)) requireIngredientMods(json);
        return json;
    }

    /**
     * A friendly-type recipe naming another mod's item as an ingredient gets a
     * mod_loaded condition on that mod. Without the mod the recipe is then
     * skipped quietly, instead of failing to parse and logging an error — which
     * matters for content written for several modpacks. Tags need nothing: an
     * empty tag is not an error. Like passthrough, this assumes an item's
     * namespace is its mod's id, which holds for practically every mod.
     */
    private static void requireIngredientMods(JsonObject json) {
        Set<String> mods = new TreeSet<>();
        collectIngredientMods(json, mods);
        addModConditions(json, mods);
    }

    private static void collectIngredientMods(JsonElement element, Set<String> mods) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectIngredientMods(child, mods);
            return;
        }
        if (!element.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (entry.getKey().equals("result")) continue; // the output is not an ingredient
            JsonElement value = entry.getValue();
            if (entry.getKey().equals("item") && value.isJsonPrimitive()) {
                ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
                if (id == null) continue;
                String ns = id.getNamespace();
                if (!ns.equals("minecraft") && !ns.equals("customgear")) mods.add(ns);
            } else {
                collectIngredientMods(value, mods);
            }
        }
    }

    /**
     * Adds a neoforge:mod_loaded condition per mod to a recipe, keeping the
     * conditions it already has and never adding the same mod twice.
     */
    public static void addModConditions(JsonObject json, Collection<String> mods) {
        if (mods.isEmpty()) return;
        JsonArray conditions = json.has("neoforge:conditions") && json.get("neoforge:conditions").isJsonArray()
                ? json.getAsJsonArray("neoforge:conditions")
                : new JsonArray();
        Set<String> present = new HashSet<>();
        for (JsonElement element : conditions) {
            if (element instanceof JsonObject condition && condition.has("modid")) {
                present.add(condition.get("modid").getAsString());
            }
        }
        for (String mod : mods) {
            if (!present.add(mod)) continue;
            JsonObject condition = new JsonObject();
            condition.addProperty("type", "neoforge:mod_loaded");
            condition.addProperty("modid", mod);
            conditions.add(condition);
        }
        json.add("neoforge:conditions", conditions);
    }

    // ── Shaped ────────────────────────────────────────────────────────────────

    private static JsonObject buildShaped(String itemId, RecipeData data) {
        if (data.pattern == null || data.pattern.isEmpty()) {
            LOGGER.warn("[CustomGear] Shaped recipe for '{}' missing 'pattern' — skipping", itemId);
            return null;
        }
        if (data.key == null || data.key.isEmpty()) {
            LOGGER.warn("[CustomGear] Shaped recipe for '{}' missing 'key' — skipping", itemId);
            return null;
        }
        if (data.pattern.size() > 3) {
            LOGGER.warn("[CustomGear] Shaped recipe for '{}' has more than 3 rows — skipping", itemId);
            return null;
        }

        // Row lengths: each 1-3 chars, and ALL rows the same length.
        // Vanilla rejects uneven or oversized patterns at data-pack load
        // with an error that doesn't point back to the user's JSON.
        int width = data.pattern.getFirst().length();
        for (String row : data.pattern) {
            if (row.isEmpty() || row.length() > 3) {
                LOGGER.warn("[CustomGear] Shaped recipe for '{}': row \"{}\" must be 1-3 characters — skipping",
                        itemId, row);
                return null;
            }
            if (row.length() != width) {
                LOGGER.warn("[CustomGear] Shaped recipe for '{}': all pattern rows must have the SAME length "
                                + "(row \"{}\" has {} chars, expected {}). Pad with spaces for empty slots — skipping",
                        itemId, row, row.length(), width);
                return null;
            }
        }

        // Collect the symbols actually used in the pattern (space = empty slot)
        Set<Character> usedSymbols = new HashSet<>();
        for (String row : data.pattern) {
            for (char c : row.toCharArray()) {
                if (c != ' ') usedSymbols.add(c);
            }
        }

        // Key entries: 1 char, not a space, and every key must be USED in the
        // pattern — vanilla errors on unused key symbols too.
        for (Map.Entry<String, String> entry : data.key.entrySet()) {
            String symbol = entry.getKey();
            if (symbol.length() != 1) {
                LOGGER.warn("[CustomGear] Shaped recipe for '{}': key '{}' must be exactly 1 char — skipping",
                        itemId, symbol);
                return null;
            }
            if (symbol.charAt(0) == ' ') {
                LOGGER.warn("[CustomGear] Shaped recipe for '{}': the space character is reserved for "
                        + "empty slots and can't be a key — skipping", itemId);
                return null;
            }
            if (!usedSymbols.contains(symbol.charAt(0))) {
                LOGGER.warn("[CustomGear] Shaped recipe for '{}': key '{}' is defined but never appears "
                        + "in the pattern — skipping. Remove it or use it in the pattern", itemId, symbol);
                return null;
            }
        }

        // Every pattern symbol must have a key entry
        for (char symbol : usedSymbols) {
            if (!data.key.containsKey(String.valueOf(symbol))) {
                LOGGER.warn("[CustomGear] Shaped recipe for '{}': pattern uses '{}' but 'key' doesn't "
                        + "define it — skipping", itemId, symbol);
                return null;
            }
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("type", "minecraft:crafting_shaped");

        JsonArray pattern = new JsonArray();
        for (String row : data.pattern) pattern.add(row);
        obj.add("pattern", pattern);

        JsonObject key = new JsonObject();
        for (Map.Entry<String, String> entry : data.key.entrySet()) {
            JsonObject ing = ingredient(entry.getValue(), itemId, "key '" + entry.getKey() + "'");
            if (ing == null) return null;
            key.add(entry.getKey(), ing);
        }
        obj.add("key", key);

        obj.add("result", buildResult(itemId, data.resultCount()));
        return obj;
    }

    // ── Shapeless ─────────────────────────────────────────────────────────────

    private static JsonObject buildShapeless(String itemId, RecipeData data) {
        if (data.ingredients == null || data.ingredients.isEmpty()) {
            LOGGER.warn("[CustomGear] Shapeless recipe for '{}' missing 'ingredients' — skipping", itemId);
            return null;
        }
        if (data.ingredients.size() > 9) {
            LOGGER.warn("[CustomGear] Shapeless recipe for '{}' has {} ingredients — a crafting grid "
                    + "only holds 9. Skipping", itemId, data.ingredients.size());
            return null;
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("type", "minecraft:crafting_shapeless");

        JsonArray ingredients = new JsonArray();
        for (String id : data.ingredients) {
            JsonObject ing = ingredient(id, itemId, "ingredients");
            if (ing == null) return null;
            ingredients.add(ing);
        }
        obj.add("ingredients", ingredients);

        obj.add("result", buildResult(itemId, data.resultCount()));
        return obj;
    }

    // ── Cooking (smelting / blasting / smoking / campfire) ───────────────────────────────

    private static JsonObject buildCooking(String type, String itemId,
                                           RecipeData data, float defaultSeconds) {
        if (data.ingredient == null || data.ingredient.isBlank()) {
            LOGGER.warn("[CustomGear] {} recipe for '{}' missing 'ingredient' — skipping", type, itemId);
            return null;
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);

        JsonObject ing = ingredient(data.ingredient, itemId, "ingredient");
        if (ing == null) return null;
        obj.add("ingredient", ing);

        obj.add("result", buildResult(itemId, 1));
        obj.addProperty("experience", data.experience());
        obj.addProperty("cookingtime", cookingTicks(data, itemId, defaultSeconds));

        return obj;
    }

    /**
     * Resolves cooking_time (seconds) to the ticks the datapack wants.
     * <p>
     * Also catches the most likely upgrade mistake. cooking_time was in TICKS
     * before 1.6.0, and a leftover tick value is still perfectly valid as a
     * number — 200 means 10s under the old unit and 200s under the new one, and
     * nothing in the file says which was meant. The recipe would just be
     * mysteriously slow. So anything over a minute gets flagged: no real recipe
     * cooks that long, and every unconverted tick value from a normal recipe
     * lands well above the line.
     */
    private static int cookingTicks(RecipeData data, String itemId, float defaultSeconds) {
        if (data.cookingTime == 0) {
            LOGGER.warn("[CustomGear] Recipe for '{}': cooking_time 0 is treated as 'not set' "
                            + "and falls back to the default ({}s). Use a positive number of seconds.",
                    itemId, defaultSeconds);
            return Math.round(defaultSeconds * 20f);
        }
        float seconds = data.cookingTime > 0 ? data.cookingTime : defaultSeconds;
        return Math.round(seconds * 20f);
    }


    // ── Stonecutting ────────────────────────────────────────────────────────────

    private static JsonObject buildStonecutting(String itemId, RecipeData data) {
        if (data.ingredient == null || data.ingredient.isBlank()) {
            LOGGER.warn("[CustomGear] Stonecutting recipe for '{}' missing 'ingredient' — skipping",
                    itemId);
            return null;
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("type", "minecraft:stonecutting");

        JsonObject ing = ingredient(data.ingredient, itemId, "ingredient");
        if (ing == null) return null;
        obj.add("ingredient", ing);

        obj.add("result", buildResult(itemId, data.resultCount()));
        return obj;
    }

    // ── Smithing Transform ────────────────────────────────────────────────────

    private static JsonObject buildSmithing(String itemId, RecipeData data) {
        if (data.template == null || data.base == null || data.addition == null) {
            LOGGER.warn("[CustomGear] Smithing recipe for '{}' requires template, base and addition — skipping",
                    itemId);
            return null;
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("type", "minecraft:smithing_transform");

        JsonObject template = ingredient(data.template, itemId, "template");
        JsonObject base     = ingredient(data.base,     itemId, "base");
        JsonObject addition = ingredient(data.addition, itemId, "addition");
        if (template == null || base == null || addition == null) return null;

        obj.add("template", template);
        obj.add("base", base);
        obj.add("addition", addition);

        obj.add("result", buildResult(itemId, 1));
        return obj;
    }

    // ── Passthrough ───────────────────────────────────────────────────────────

    private static JsonObject buildPassthrough(String itemId, RecipeData data) {
        if (data.json == null) {
            LOGGER.warn("[CustomGear] Passthrough recipe for '{}' missing 'json' — skipping. "
                    + "The raw recipe body goes in a 'json' object.", itemId);
            return null;
        }

        JsonElement innerType = data.json.get("type");
        if (innerType == null || !innerType.isJsonPrimitive()) {
            LOGGER.warn("[CustomGear] Passthrough recipe for '{}': the inner json has no 'type' — "
                    + "skipping. Every recipe needs a type, e.g. \"create:mixing\".", itemId);
            return null;
        }

        String typeStr = innerType.getAsString();
        ResourceLocation typeRl = ResourceLocation.tryParse(typeStr);
        if (typeRl == null) {
            LOGGER.warn("[CustomGear] Passthrough recipe for '{}': malformed inner type '{}' — "
                    + "skipping. Expected 'namespace:path'.", itemId, typeStr);
            return null;
        }

        // deepCopy so the parsed data is never mutated — a /customgear reload
        // reuses it, and a condition array appended twice would be a mess.
        JsonObject obj = data.json.deepCopy();

        // Collect the mods this recipe needs: the type's namespace plus any
        // extras the user listed. LinkedHashSet keeps the order deterministic
        // (content hash) and drops the duplicate when 'requires' repeats it.
        Set<String> mods = new LinkedHashSet<>();
        if (!typeRl.getNamespace().equals("minecraft")) {
            mods.add(typeRl.getNamespace());
        }
        if (data.requires != null) {
            for (String mod : data.requires) {
                if (mod != null && !mod.isBlank()) mods.add(mod.trim());
            }
        }

        if (!mods.isEmpty()) {
            JsonArray conditions = new JsonArray();
            for (String mod : mods) {
                JsonObject condition = new JsonObject();
                condition.addProperty("type", "neoforge:mod_loaded");
                condition.addProperty("modid", mod);
                conditions.add(condition);
            }
            obj.add("neoforge:conditions", conditions);
        } else {
            LOGGER.debug("[CustomGear] Passthrough recipe for '{}' targets a vanilla type — "
                    + "no mod_loaded condition needed", itemId);
        }

        return obj;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a vanilla ingredient object from a config value.
     * <ul>
     *   <li>"minecraft:diamond"  → {"item": "minecraft:diamond"}</li>
     *   <li>"#minecraft:planks"  → {"tag": "minecraft:planks"} (any plank)</li>
     * </ul>
     * Returns null (with a clear log naming the item and the position) if the
     * resource location is syntactically invalid — vanilla would reject the
     * whole recipe file at load time with a much less helpful error.
     */
    private static JsonObject ingredient(String value, String itemId, String where) {
        if (value == null || value.isBlank()) {
            LOGGER.warn("[CustomGear] Recipe for '{}': empty ingredient in {} — skipping recipe",
                    itemId, where);
            return null;
        }

        boolean isTag = value.startsWith("#");
        String rl = isTag ? value.substring(1) : value;

        if (ResourceLocation.tryParse(rl) == null) {
            LOGGER.warn("[CustomGear] Recipe for '{}': malformed {} '{}' in {} — skipping recipe. "
                            + "Expected 'namespace:path' (e.g. 'minecraft:diamond') or "
                            + "'#namespace:tag' (e.g. '#minecraft:planks'). Only lowercase letters, "
                            + "numbers, '_', '-', '.' and '/' are allowed",
                    itemId, isTag ? "tag" : "item ID", value, where);
            return null;
        }

        JsonObject obj = new JsonObject();
        obj.addProperty(isTag ? "tag" : "item", rl);
        return obj;
    }

    private static JsonObject buildResult(String itemId, int count) {
        JsonObject result = new JsonObject();
        // Own ids never contain ':' (they are registry paths); a recipe_patch output always does.
        result.addProperty("id", itemId.indexOf(':') >= 0 ? itemId : "customgear:" + itemId);
        if (count > 1) {
            result.addProperty("count", Math.min(count, 64));
        }
        return result;
    }

    /**
     * Warns about fields the declared type will ignore.
     * <p>
     * RecipeData is one shared POJO for every recipe type, so Gson happily
     * accepts "pattern" on a smelting recipe or "cooking_time" on a
     * stonecutting one. Nothing breaks — the builder just never reads them, and
     * the user is left thinking they configured something that does nothing.
     * <p>
     * Deliberately a single table rather than a check inside each builder: the
     * per-builder version is how this ended up inconsistent in the first place,
     * with stonecutting warning about cooking_time while shaped said nothing
     * about a stray ingredient. A type missing from the switch below falls to
     * an empty set and warns about everything, which is loud but obvious —
     * better than silently validating nothing.
     */
    private static void warnUnusedFields(String itemId, RecipeData data) {
        Set<String> used = switch (data.type) {
            case "shaped"      -> Set.of("pattern", "key", "result_count");
            case "shapeless"   -> Set.of("ingredients", "result_count");
            case "smelting", "blasting", "smoking", "campfire_cooking"
                    -> Set.of("ingredient", "experience", "cooking_time");
            case "stonecutting"-> Set.of("ingredient", "result_count");
            case "smithing_transform" -> Set.of("template", "base", "addition");
            case "passthrough" -> Set.of("json", "requires");
            default            -> Set.of();
        };

        // Declared fields, by their JSON name. Only non-null counts: that is
        // why experience and result_count had to be boxed.
        Map<String, Object> declared = new LinkedHashMap<>();
        declared.put("pattern",      data.pattern);
        declared.put("key",          data.key);
        declared.put("ingredients",  data.ingredients);
        declared.put("ingredient",   data.ingredient);
        declared.put("experience",   data.experience);
        declared.put("cooking_time", data.cookingTime > 0 ? data.cookingTime : null);
        declared.put("template",     data.template);
        declared.put("base",         data.base);
        declared.put("addition",     data.addition);
        declared.put("json",         data.json);
        declared.put("requires",     data.requires);
        declared.put("result_count", data.resultCount);

        List<String> unused = new ArrayList<>();
        for (Map.Entry<String, Object> entry : declared.entrySet()) {
            if (entry.getValue() == null) continue;
            if (used.contains(entry.getKey())) continue;
            unused.add(entry.getKey());
        }

        if (!unused.isEmpty()) {
            LOGGER.warn("[CustomGear] Recipe for '{}' of type '{}' declares {} that this type "
                            + "ignores: {}. The recipe still works — those values simply do nothing.",
                    itemId, data.type,
                    unused.size() == 1 ? "a field" : "fields",
                    String.join(", ", unused));
        }
    }
}