package arrivedbog593.ultimatecustomgear.data;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * A recipe_patch changes recipes that belong to OTHER content — vanilla's or
 * another mod's — the way tag_patch changes foreign tags. Three modes, any of
 * them in one file:
 * <pre>
 * {
 *   "type": "recipe_patch",
 *   "add": {
 *     "minecraft:saddle": { "type": "shaped", "pattern": ["LLL", "L L"], "key": { "L": "minecraft:leather" } }
 *   },
 *   "remove": ["minecraft:gold_ingot_from_nuggets"],
 *   "replace": {
 *     "minecraft:hopper": { "type": "shaped", "pattern": ["I I", "ICI", " I "],
 *                           "key": { "I": "minecraft:iron_ingot", "C": "#c:chests" } }
 *   }
 * }
 * </pre>
 * Every recipe body is the same format an item's "recipe" field takes —
 * shaped, smelting, ..., or passthrough for another mod's recipe type.
 * <p>
 * Additions are ordinary recipe files in the dynamic pack. Removal and
 * replacement work on the server's live recipe list after every datapack load
 * (see RecipePatchHandler): that is what lets "remove_output" find recipes whose
 * ids nobody wrote down, and lets a replacement keep the original's id.
 * <p>
 * There is no top-level "id": a patch registers nothing, and several patches
 * simply add up.
 */
public class RecipePatchData {

    /**
     * New recipes, keyed by the item they make: "minecraft:saddle" → one recipe
     * or an array. The key is the output of the friendly types; a passthrough
     * recipe carries its own result inside "json", and the key only names it.
     * Registered as customgear:patch/&lt;namespace&gt;/&lt;path&gt;_recipe[_n].
     */
    public Map<String, List<RecipeData>> add;

    /** Recipe ids to remove, exactly as /customgear recipes prints them. */
    public List<String> remove;

    /**
     * Item ids whose recipes are removed — every recipe whose MAIN output is
     * that item, as RecipeQueries defines it. A recipe that only makes the
     * item as a secondary output (Create's crushing bonus, a sawmill's
     * sawdust) is never matched: removing it would remove its main output
     * too. Those go in "remove" by id, on purpose.
     */
    @SerializedName("remove_output")
    public List<String> removeOutput;

    /**
     * Restricts "remove_output" to these recipe types, e.g.
     * ["minecraft:crafting"] to drop the crafting recipe but keep the furnace
     * one. Empty or absent means every type. Does not affect "remove", whose
     * ids are already exact.
     */
    @SerializedName("recipe_types")
    public List<String> recipeTypes;

    /**
     * Recipes to swap, keyed by recipe id. The new recipe takes the SAME id,
     * so recipe-book unlocks, advancements and anything else that names the
     * recipe keep working. Only an existing recipe is replaced: when the id is
     * not loaded (its mod is missing) nothing is added in its place.
     */
    public Map<String, Replacement> replace;

    /**
     * Optional note for whoever reads the file later. Never parsed; a patch has
     * no name, so without this the file gives no clue why it exists.
     */
    @SuppressWarnings("unused")
    @SerializedName("comment")
    public String comment;

    /** A replacement recipe: an ordinary recipe body plus an optional output. */
    public static class Replacement extends RecipeData {

        /**
         * What the friendly types make. Absent means the same item the
         * original made — and the same count, unless result_count says
         * otherwise — so "same output, new ingredients" needs nothing here.
         * Ignored by passthrough, whose result lives in its json.
         */
        public String result;
    }
}
