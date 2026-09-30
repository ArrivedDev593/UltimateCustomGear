package arrivedbog593.ultimatecustomgear.resources;

import arrivedbog593.ultimatecustomgear.data.ContainerContentData;
import arrivedbog593.ultimatecustomgear.data.ContainerData;
import arrivedbog593.ultimatecustomgear.items.containers.CustomShulkerBlock;
import arrivedbog593.ultimatecustomgear.registry.ContainerRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the SPRITE a rendered container draws with.
 * <p>
 * NOW AN ATLAS, WHERE IT USED NOT TO BE. Up to 1.21.1 this returned a full
 * texture path and the renderer pointed entityCutout straight at the PNG,
 * skipping the atlas entirely. That is no longer possible: a block entity
 * renderer submits a model plus a {@code SpriteId}, and a SpriteId only means
 * something inside an atlas. The same is true of the item side, where the
 * "minecraft:special" chest and shulker renderers take a sprite name too.
 * <p>
 * So what comes out of here is now a SPRITE NAME — {@code minecraft:normal},
 * {@code customgear:my_box} — and the caller maps it through
 * {@code Sheets.CHEST_MAPPER} or {@code Sheets.SHULKER_MAPPER}. Nothing has to
 * be registered for a user texture to be picked up: both atlases are built from
 * a DIRECTORY source, which scans every namespace, so a PNG written to
 * {@code customgear:textures/entity/chest/&lt;id&gt;.png} is in the atlas simply by
 * being there.
 * <p>
 * CACHED PER BLOCK AND HALF. The renderer runs once per container per frame, and
 * building an Identifier there would allocate for nothing. Cleared on reload,
 * since a definition can name a different texture afterward.
 */
public final class ContainerTextures {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    /**
     * Cache key: a chest needs three textures, one per half, and they are NOT
     * the single one cropped — the halves are 15 wide and unwrap differently.
     */
    private record Key(Block block, ChestType type) {}

    private static final Map<Key, Identifier> CACHE = new ConcurrentHashMap<>();

    private ContainerTextures() {}

    /** The sprite for a placed container, or vanilla's when it declares none. */
    public static Identifier forBlock(Block block, ChestType type) {
        return CACHE.computeIfAbsent(new Key(block, type), ContainerTextures::resolve);
    }

    /** A shulker has one 64x64 unwrap, under the same 'single' key as a chest. */
    public static Identifier forShulker(Block block) {
        return CACHE.computeIfAbsent(new Key(block, ChestType.SINGLE), ContainerTextures::resolve);
    }

    /** Called from /customgear reload — a definition may name a new texture. */
    public static void clearCache() {
        CACHE.clear();
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    /** The refs key each half reads. */
    private static String keyFor(ChestType type) {
        return switch (type) {
            case LEFT  -> "left";
            case RIGHT -> "right";
            default    -> "single";
        };
    }

    private static String vanillaChestSprite(ChestType type) {
        return switch (type) {
            case LEFT  -> "normal_left";
            case RIGHT -> "normal_right";
            default    -> "normal";
        };
    }

    private static Identifier resolve(Key key) {
        return spriteFor(
                ContainerRegistry.CONTAINER_MAP.get(BuiltInRegistries.BLOCK.getKey(key.block())),
                key.type(),
                key.block() instanceof CustomShulkerBlock);
    }

    /**
     * The same resolution, from the DEFINITION rather than from a placed block.
     * <p>
     * Both entry points are needed and neither can replace the other. The
     * renderer starts from a Block, because that is what a block entity carries;
     * the model generator starts from the JSON, because it runs before anything
     * is registered and there is no Block to look up yet.
     */
    public static Identifier spriteFor(ContainerContentData data, ChestType type, boolean shulker) {
        Identifier fallback = shulker
                ? Identifier.withDefaultNamespace("shulker")
                : Identifier.withDefaultNamespace(vanillaChestSprite(type));

        if (data == null || data.texture == null || data.texture.refs == null) {
            return fallback;
        }

        String value = data.texture.refs.get(keyFor(type));
        // A chest that only declares 'single' keeps vanilla's halves rather than
        // stretching its own texture across a mesh it was not drawn for. The
        // parser warns about this when the definition allows doubling.
        if (value == null) return fallback;

        // A resource location points at somebody else's texture and is used as
        // written; anything else is a file this pack ships, which the generator
        // already copied into the dynamic pack under a name derived from the id.
        if (value.contains(":")) {
            Identifier parsed = Identifier.tryParse(spriteName(value));
            if (parsed != null) return parsed;
            LOGGER.error("[CustomGear] Container '{}': '{}' is not a valid resource location — "
                    + "using the default chest texture.", data.id, value);
            return fallback;
        }
        return spriteLocation(data.id, type);
    }

    /**
     * Trims a written reference down to a sprite name.
     * <p>
     * Both forms are accepted, and they have to be: authors paste the path they
     * see in another mod's jar, which is the long one, while an atlas is keyed
     * by the short one. Refusing the long form would make chests the single
     * place in the mod where 'textures/' and '.png' must be left off, when every
     * block face gets them added for free.
     */
    private static String spriteName(String value) {
        int colon = value.indexOf(':');
        String namespace = colon >= 0 ? value.substring(0, colon + 1) : "";
        String path = colon >= 0 ? value.substring(colon + 1) : value;

        // The atlas is FLAT: a sprite is named by its file, and every directory
        // above it is noise. Keying on "textures/" only caught the longest of
        // the forms an author might write — 'entity/chest/christmas' has no such
        // segment and was passed through unchanged, which the atlas then failed
        // to find.
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) path = path.substring(lastSlash + 1);
        if (path.endsWith(".png")) path = path.substring(0, path.length() - ".png".length());

        return namespace + path;
    }

    /** Where this container's own sprite lives, as the atlas names it. */
    private static Identifier spriteLocation(String id, ChestType type) {
        String key = keyFor(type);
        String suffix = key.equals("single") ? "" : "_" + key;
        return Identifier.fromNamespaceAndPath(ModelConstants.NAMESPACE, id + suffix);
    }

    /** Where the PNG itself has to be written for the atlas to find it. */
    private static Identifier packLocation(String id, ChestType type, boolean shulker) {
        String key = keyFor(type);
        String suffix = key.equals("single") ? "" : "_" + key;
        String folder = shulker ? "entity/shulker/" : "entity/chest/";
        return Identifier.fromNamespaceAndPath(
                ModelConstants.NAMESPACE, "textures/" + folder + id + suffix + ".png");
    }

    // ── Injection ─────────────────────────────────────────────────────────────

    /**
     * Copies a container's own textures into the dynamic pack. Called from the
     * model generator, which is the only place with a PackSink.
     * <p>
     * WHICH FOLDER MATTERS NOW. Chests and shulkers are built into two different
     * atlases, each from its own directory, so a shulker skin written under
     * entity/chest would simply never be stitched.
     */
    public static void inject(PackSink pack, ContainerContentData data) {
        String kind = data.container.kind();
        boolean shulker = ContainerData.KIND_SHULKER.equals(kind);
        if (!ContainerData.KIND_CHEST.equals(kind) && !shulker) return;
        if (data.texture == null || data.texture.refs == null) return;

        for (ChestType type : ChestType.values()) {
            String value = data.texture.refs.get(keyFor(type));
            if (value == null || value.contains(":")) continue;   // absent or foreign

            Optional<Path> file = TextureLoader.resolveUserResource(value);
            if (file.isEmpty()) {
                LOGGER.error("[CustomGear] Container '{}': texture file not found in the config "
                        + "folder or any pack zip: {}", data.id, value);
                continue;
            }
            pack.addTextureWithMeta(packLocation(data.id, type, shulker), file.get());
        }
    }
}
