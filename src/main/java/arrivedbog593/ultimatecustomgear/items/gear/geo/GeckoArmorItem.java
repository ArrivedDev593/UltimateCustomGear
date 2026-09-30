package arrivedbog593.ultimatecustomgear.items.gear.geo;

import arrivedbog593.ultimatecustomgear.data.GearData;
import arrivedbog593.ultimatecustomgear.items.gear.CustomArmorItem;
import arrivedbog593.ultimatecustomgear.resources.ModelConstants;
import arrivedbog593.ultimatecustomgear.resources.TextureRef;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

/**
 * Armor piece that renders as a GeckoLib 3D model when worn.
 * <p>
 * Extends CustomArmorItem, so every stat, effect, set bonus and damage
 * resistance keeps working exactly the same — this class only changes HOW the
 * armor is drawn on the body.
 * <p>
 * This class is instantiated ONLY when GearRegistry confirms that
 * texture.armor_3d is declared AND GeckoLib is loaded. Everywhere else the
 * plain CustomArmorItem is registered instead, which is why the mod still
 * works without GeckoLib installed (the armor falls back to armor_layers).
 * <p>
 * Asset resolution follows the mod's usual texture grammar:
 * <ul>
 *   <li><b>file</b> — armor_3d paths point to the user's own files; the
 *       GearModelGenerator has already copied them into the dynamic pack at
 *       customgear:geo/armor/&lt;id&gt;.geo.json (and siblings), so the model
 *       reads them from there.</li>
 *   <li><b>reference</b> — armor_3d values are resource locations of assets
 *       shipped by another mod (or vanilla); they are used as-is, nothing is
 *       copied. NOTE: this makes that mod REQUIRED for the armor to render.</li>
 * </ul>
 * <p>
 * GECKOLIB 5 MOVED THE RESOURCE LOOKUP OFF THE ITEM. A GeoModel is now asked
 * for its model and texture given a {@link GeoRenderState}, not given the item
 * instance — that state is built on the render thread and no longer carries the
 * item. Which costs nothing here: the three locations depend only on the gear
 * definition this renderer was built for, so they are resolved once in the
 * constructor and handed straight back.
 */
public class GeckoArmorItem extends CustomArmorItem implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GeckoArmorItem(GearData data, String piece, Item.Properties props) {
        super(data, piece, props);

        if (!ModList.get().isLoaded("geckolib")) {
            throw new IllegalStateException(
                    "GeckoArmorItem('" + data.id + "', piece='" + piece + "') was instantiated "
                            + "but GeckoLib is not loaded. This class must only be created when "
                            + "ModList.get().isLoaded(\"geckolib\") is true — see the guard in "
                            + "GearRegistry.registerArmor().");
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No controllers: animations declared in the .animation.json play through
        // GeckoLib's default handling. Custom controllers would need per-armor
        // animation names, which the JSON schema does not expose (yet).
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<GeckoArmorItem, HumanoidRenderState> renderer;

            @Override
            public GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack itemStack,
                                                              EquipmentSlot equipmentSlot) {
                if (this.renderer == null) {
                    this.renderer = new GeoArmorRenderer<>(
                            new GearArmorGeoModel(getGearDataDirect()));
                }
                return this.renderer;
            }
        });
    }

    /**
     * Resolves the three GeckoLib resources for this armor.
     * <p>
     * Each value decides for itself: a resource location points into another
     * mod's jar, which the generator deliberately left uncopied, so pointing at
     * the pack instead would find nothing. A file was copied under this gear's
     * id, so its path is derived rather than read.
     * <p>
     * Note the model and animation ids are CACHE KEYS, not paths: GeckoLib
     * strips 'geckolib/models/' and the '.geo.json' suffix when it indexes what
     * it scanned, so the id is what is left. The texture is a real path and
     * keeps its '.png'.
     */
    private static class GearArmorGeoModel extends GeoModel<GeckoArmorItem> {

        private final Identifier modelResource;
        private final Identifier textureResource;
        private final Identifier animationResource;

        GearArmorGeoModel(GearData data) {
            GearData.Armor3DData armor3d = data.texture != null ? data.texture.armor3d : null;

            this.modelResource = resolveCached(armor3d == null ? null : armor3d.model,
                    "armor/" + data.id);
            this.textureResource = resolve(armor3d == null ? null : armor3d.texture,
                    "textures/armor/" + data.id + ".png");
            this.animationResource = resolveCached(armor3d == null ? null : armor3d.animation,
                    "armor/" + data.id);
        }

        private static Identifier packLoc(String path) {
            return Identifier.fromNamespaceAndPath(ModelConstants.NAMESPACE, path);
        }

        /**
         * A reference is used as written; anything else was copied into the
         * pack under the derived path.
         */
        /**
         * A reference is turned into the id GeckoLib caches the asset under, so
         * the JSON can keep naming the FILE — which is what a pack author can
         * actually look up in the other mod's jar. GeckoLib strips the same two
         * things when it indexes, so both spellings land on the same key.
         */
        private static Identifier resolveCached(String value, String packPath) {
            if (value != null && TextureRef.kindOf(value) == TextureRef.Kind.REFERENCE) {
                Identifier rl = Identifier.tryParse(value);
                if (rl != null) return rl.withPath(cacheKey(rl.getPath()));
            }
            return packLoc(packPath);
        }

        private static String cacheKey(String path) {
            for (String prefix : new String[]{"geckolib/models/", "geckolib/animations/"}) {
                if (path.startsWith(prefix)) { path = path.substring(prefix.length()); break; }
            }
            for (String suffix : new String[]{".geo.json", ".animation.json", ".animations.json", ".json"}) {
                if (path.endsWith(suffix)) { path = path.substring(0, path.length() - suffix.length()); break; }
            }
            return path;
        }

        /**
         * Textures are not cached: they are read by path, extension and all.
         */
        private static Identifier resolve(String value, String packPath) {
            if (value != null && TextureRef.kindOf(value) == TextureRef.Kind.REFERENCE) {
                Identifier rl = Identifier.tryParse(value);
                if (rl != null) return rl;
            }
            return packLoc(packPath);
        }

        @Override
        public @NonNull Identifier getModelResource(@NonNull GeoRenderState renderState) {
            return this.modelResource;
        }

        @Override
        public @NonNull Identifier getTextureResource(@NonNull GeoRenderState renderState) {
            return this.textureResource;
        }

        @Override
        public @NonNull Identifier getAnimationResource(@NonNull GeckoArmorItem animatable) {
            return this.animationResource;
        }
    }
}
