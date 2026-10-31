package arrivedbog593.ultimatecustomgear.datapack;

import arrivedbog593.ultimatecustomgear.data.RecipeData;
import arrivedbog593.ultimatecustomgear.data.RecipePatchData;
import arrivedbog593.ultimatecustomgear.resources.PackSink;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the "add" mode of every recipe_patch into the dynamic pack. Removal
 * and replacement cannot be done with files — see RecipePatchHandler.
 * <p>
 * An addition is built exactly like an item's own recipe, by RecipeLoader,
 * with the patch key as the output. Ids are
 * customgear:patch/&lt;namespace&gt;/&lt;path&gt;_recipe, numbered _recipe_2, _recipe_3...
 * when several recipes (from one patch or several) make the same item. Content
 * is walked in a fixed order, so the numbering is the same on every machine.
 */
public class RecipePatchLoader {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");
    private static final Gson   GSON   = new GsonBuilder().setPrettyPrinting().create();

    /** Path prefix of added recipes; remove_output never matches it (see RecipePatchHandler). */
    public static final String PATCH_PREFIX = "patch/";

    public static void loadAll(PackSink pack, List<RecipePatchData> patches) {
        Map<Identifier, Integer> perItem = new HashMap<>();
        int count = 0;

        for (RecipePatchData patch : patches) {
            if (patch.add == null) continue;
            for (Map.Entry<String, List<RecipeData>> entry : patch.add.entrySet()) {
                Identifier output = Identifier.tryParse(entry.getKey().trim());
                if (output == null) {
                    LOGGER.warn("[CustomGear] recipe_patch add: malformed item id '{}' — skipping its "
                            + "recipes. Expected 'namespace:path'.", entry.getKey());
                    continue;
                }
                if (entry.getValue() == null) continue;

                for (RecipeData recipe : entry.getValue()) {
                    if (recipe == null) continue;
                    JsonObject json = RecipeLoader.buildRecipeJson(output.toString(), recipe);
                    if (json == null) continue;
                    requireOutputMod(json, recipe, output);

                    int n = perItem.merge(output, 1, Integer::sum);
                    String path = PATCH_PREFIX + output.getNamespace() + "/" + output.getPath()
                            + (n == 1 ? "_recipe" : "_recipe_" + n);
                    pack.addRaw(Identifier.fromNamespaceAndPath("customgear", "recipe/" + path + ".json"),
                            GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
                    LOGGER.debug("[CustomGear] recipe_patch added customgear:{}", path);
                    count++;
                }
            }
        }

        if (count > 0) LOGGER.info("[CustomGear] recipe_patch: {} recipes added", count);
    }

    /**
     * A friendly-type recipe for another mod's item gets a mod_loaded condition
     * on that mod, so the patch is skipped instead of logging a load error when
     * the mod is absent. Passthrough already has one from its type, and its
     * result is inside its json, not the key.
     */
    private static void requireOutputMod(JsonObject json, RecipeData recipe, Identifier output) {
        String ns = output.getNamespace();
        if ("passthrough".equals(recipe.type) || ns.equals("minecraft") || ns.equals("customgear")) return;

        // Merged with the ingredient conditions RecipeLoader already added.
        RecipeLoader.addModConditions(json, List.of(ns));
    }
}
