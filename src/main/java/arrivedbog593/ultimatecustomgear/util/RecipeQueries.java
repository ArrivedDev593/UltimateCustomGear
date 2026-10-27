package arrivedbog593.ultimatecustomgear.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Which recipes produce this item" — asked by /customgear recipes and by
 * recipe_patch's remove-by-item, so the command always shows exactly what a
 * patch would match.
 * <p>
 * A recipe has at most one MAIN output: what it reports through
 * {@code getResultItem}. Machines with several outputs (Create's crushing,
 * Mekanism's sawmill) report only the first, so the rest — SECONDARY outputs —
 * are found by encoding the recipe back to JSON and reading its output fields.
 * A recipe that reports nothing (Mekanism's sawmill and reaction chamber)
 * still gets a main output when its JSON has exactly one output item, or
 * exactly one outside fields named "secondary" — the sawmill's
 * {@code main_output} next to its {@code secondary_output}. Otherwise all of
 * them count as secondary.
 * <p>
 * The split matters because removing a recipe removes ALL of it: a patch that
 * matched secondary outputs would wipe Create's gold crushing to stop
 * experience nuggets. Remove-by-item uses {@link #mainOutput} only; secondary
 * matches are shown by the command and must be targeted by id on purpose.
 * <p>
 * A "recipe" that only exists in JEI (drawn by a mod's JEI plugin from code,
 * like Iron's Spells' scroll forge) is not in the recipe manager at all, so
 * nothing here can see it.
 */
public final class RecipeQueries {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    private RecipeQueries() {}

    /** Recipes that produce an item, split by whether it is their main output. */
    public record Matches(List<RecipeHolder<?>> main, List<RecipeHolder<?>> secondary) {}

    /**
     * The recipe's MAIN output, or AIR when it has none (see the class doc).
     * What recipe_patch's remove-by-item compares against. Encodes the recipe
     * to JSON only when it doesn't report a result.
     */
    public static Item mainOutput(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        Item reported = reportedResult(holder, registries).getItem();
        if (reported != Items.AIR) return reported;
        return jsonOutputs(holder, registries).mainItem();
    }

    /** Main and secondary matches together, for the command. Encodes recipes to JSON, so it is not cheap. */
    public static Matches find(RecipeManager recipes, HolderLookup.Provider registries, Item item) {
        List<RecipeHolder<?>> main = new ArrayList<>();
        List<RecipeHolder<?>> secondary = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes.getRecipes()) {
            Item reported = reportedResult(holder, registries).getItem();
            if (reported == item) {
                main.add(holder);
                continue;
            }
            // Vanilla's own recipe types have a single output, already reported.
            if (reported != Items.AIR && isVanillaSerializer(holder)) continue;

            JsonOutputs outputs = jsonOutputs(holder, registries);
            if (!outputs.all().contains(item)) continue;
            if (reported == Items.AIR && outputs.mainItem() == item) main.add(holder);
            else secondary.add(holder);
        }
        main.sort(ORDER);
        secondary.sort(ORDER);
        return new Matches(main, secondary);
    }

    /** The recipe type's id, e.g. minecraft:smelting or create:crushing. */
    public static ResourceLocation typeId(RecipeHolder<?> holder) {
        ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        return id != null ? id : ResourceLocation.withDefaultNamespace("unknown");
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static final Comparator<RecipeHolder<?>> ORDER =
            Comparator.<RecipeHolder<?>, String>comparing(h -> typeId(h).toString())
                    .thenComparing(h -> h.id().toString());

    /** What the recipe reports as its result, or an empty stack when it can't or won't say. */
    public static ItemStack reportedResult(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        try {
            ItemStack result = holder.value().getResultItem(registries);
            // Declared non-null, but this runs on every mod's recipes and one broken
            // mod returning null must not take the command and recipe_patch down.
            //noinspection ConstantValue
            return result == null ? ItemStack.EMPTY : result;
        } catch (Exception e) {
            // Some mods build the result from the crafting input and throw without one.
            LOGGER.debug("[CustomGear] Recipe {} did not report a result: {}", holder.id(), e.toString());
            return ItemStack.EMPTY;
        }
    }

    private static boolean isVanillaSerializer(RecipeHolder<?> holder) {
        ResourceLocation id = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
        return id != null && id.getNamespace().equals("minecraft");
    }

    /**
     * Output items found in a recipe's JSON. {@code unmarked} are the ones not
     * under a field named "secondary", which is what can still make one of
     * them the main output of a recipe that reports nothing.
     */
    private record JsonOutputs(Set<Item> all, Set<Item> unmarked) {

        /** The single output, else the single unmarked one, else AIR. */
        Item mainItem() {
            if (all.size() == 1) return all.iterator().next();
            if (unmarked.size() == 1) return unmarked.iterator().next();
            return Items.AIR;
        }
    }

    /**
     * Every item named under an output field of the recipe's JSON, in order.
     * Output fields are the keys containing "result" or "output" — result,
     * results, output, main_output, secondary_output, item_output... Ingredient
     * fields never match, so "uses gold" is never mistaken for "makes gold".
     */
    private static JsonOutputs jsonOutputs(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        JsonOutputs out = new JsonOutputs(new LinkedHashSet<>(), new LinkedHashSet<>());
        try {
            JsonElement json = Recipe.CODEC
                    .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), holder.value())
                    .result().orElse(null);
            if (json != null && json.isJsonObject()) collectFields(json.getAsJsonObject(), false, false, out);
        } catch (Exception e) {
            LOGGER.debug("[CustomGear] Recipe {} could not be read as JSON: {}", holder.id(), e.toString());
        }
        return out;
    }

    private static void collectOutputs(JsonElement element, boolean inOutput, boolean secondary, JsonOutputs out) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectOutputs(child, inOutput, secondary, out);
            return;
        }
        if (element.isJsonPrimitive()) {
            // "result": "minecraft:stone", the short form some mods write.
            if (inOutput && element.getAsJsonPrimitive().isString()) addItem(element.getAsString(), secondary, out);
            return;
        }
        if (!element.isJsonObject()) return;

        JsonObject object = element.getAsJsonObject();
        // A fluid or chemical stack carries "amount"; an item stack doesn't.
        if (inOutput && object.has("amount")) return;
        // A nested object with its own "type" outside the outputs is a recipe inside
        // the recipe — Create's sequenced assembly steps. Its results are
        // intermediate items, not what this recipe makes.
        if (!inOutput && object.has("type")) return;

        collectFields(object, inOutput, secondary, out);
    }

    private static void collectFields(JsonObject object, boolean inOutput, boolean secondary, JsonOutputs out) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            JsonElement value = entry.getValue();
            if (key.contains("result") || key.contains("output")) {
                collectOutputs(value, true, secondary || key.contains("secondary"), out);
            } else if (inOutput && (key.equals("id") || key.equals("item") || key.equals("stack")
                    || key.equals("name") || key.equals("block"))) {
                // Only the stack's own id: "components", "chance"... may hold ids that aren't outputs.
                // "name" and "block" are block outputs, like Botania's pure daisy {"name": "botania:livingrock"};
                // the block's item shares its id.
                collectOutputs(value, true, secondary, out);
            } else if (!inOutput && !value.isJsonPrimitive()) {
                // Outputs can sit deeper, under a wrapper object.
                collectOutputs(value, false, secondary, out);
            }
        }
    }

    private static void addItem(String id, boolean secondary, JsonOutputs out) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return;
        BuiltInRegistries.ITEM.getOptional(location)
                .filter(item -> item != Items.AIR)
                .ifPresent(item -> {
                    out.all().add(item);
                    if (!secondary) out.unmarked().add(item);
                });
    }
}
