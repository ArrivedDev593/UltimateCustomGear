package arrivedbog593.ultimatecustomgear.registry;

import arrivedbog593.ultimatecustomgear.data.ContainerContentData;
import arrivedbog593.ultimatecustomgear.data.ContainerData;
import arrivedbog593.ultimatecustomgear.items.containers.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers containers: storage that exists either as a placeable block
 * (barrel, chest, shulker) or as a carried item (backpack).
 * <p>
 * WHY THIS IS NOT PART OF BlockRegistry: a backpack is not a block. Keeping
 * containers there would force that class to know which subtypes register a
 * Block and which register only an Item. Here the split lives in one place and
 * BlockRegistry goes back to knowing nothing about storage.
 * <p>
 * Three DeferredRegisters in one class is deliberate and not a workaround:
 * a container's Block, its BlockItem and the shared BlockEntityType are
 * registered together and reference each other. Splitting them across classes
 * would only mean two classes that have to import each other.
 * <p>
 * Several DeferredRegisters over the same registry and namespace are fine —
 * they are queues feeding the same registration event, not owners of it.
 */
public class ContainerRegistry {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks("customgear");

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems("customgear");

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "customgear");

    /**
     * Container blocks collected while registering, so the block entity type
     * can declare exactly which blocks it is valid for. Blocks register before
     * block entities, so by the time the supplier below runs every holder here
     * resolves.
     */
    private static final List<DeferredHolder<Block, CustomContainerBlock>> BLOCK_HOLDERS =
            new ArrayList<>();

    /**
     * ONE type for every custom container regardless of subtype or size.
     * <p>
     * The type's id ends up in the NBT of every placed block, so splitting it
     * per subtype would freeze three ids into people's worlds while the subtypes
     * are still being written — and a backpack, which is not a block at all,
     * would need its own branch regardless. The variation lives in the supplier
     * instead, where reorganising costs nothing.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CustomContainerBlockEntity>>
            CONTAINER_BE = BLOCK_ENTITIES.register("container", () ->
            // Built directly rather than through a Builder: that class is gone, and
            // with it the trailing DFU-type argument that mods always passed null to.
            //
            // THE SET OVERLOAD, NOT THE VARARGS ONE, and that is not style. An
            // empty varargs array now throws — "instantiated without valid
            // blocks" — so an instance with no containers in its JSON crashed on
            // startup, which is the most common instance there is. The Set form
            // is the one vanilla points at for exactly this case.
            new BlockEntityType<>(
                    ContainerRegistry::blockEntityFor,
                    BLOCK_HOLDERS.stream()
                            .map(DeferredHolder::get)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet())));


    /** The block decides which block entity class backs it. */
    private static CustomContainerBlockEntity blockEntityFor(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CustomChestBlock) {
            return new CustomChestBlockEntity(pos, state);
        }
        if (state.getBlock() instanceof CustomShulkerBlock) {
            return new CustomShulkerBlockEntity(pos, state);
        }
        return new CustomBarrelBlockEntity(pos, state);
    }

    /**
     * containerId → its definition, for runtime lookups.
     * <p>
     * Separate from BlockRegistry.BLOCK_MAP on purpose. That map holds
     * BlockData, so every read had to test whether the entry happened to be a
     * container; here absence means exactly one thing — the definition is gone —
     * which is what orphan rescue keys off.
     */
    public static final Map<Identifier, ContainerContentData> CONTAINER_MAP = new HashMap<>();

    public static void register(IEventBus modEventBus, List<ContainerContentData> containerList) {
        for (ContainerContentData data : containerList) {
            if (data.container == null) continue;

            if (!data.container.isBlock()) {
                registerCarriedContainer(data);
                continue;
            }
            registerBlockContainer(data);
        }
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    private static void registerBlockContainer(ContainerContentData data) {
        DeferredHolder<Block, CustomContainerBlock> blockHolder =
                BLOCKS.registerBlock(data.id, props -> blockFor(data, props));
        BLOCK_HOLDERS.add(blockHolder);
        ITEMS.registerItem(data.id, props -> itemFor(data, blockHolder.get(), props));
        CONTAINER_MAP.put(Identifier.fromNamespaceAndPath("customgear", data.id), data);
        LOGGER.info("[CustomGear] Container registered: {} ({}, {} slots)",
                data.id, data.container.kind(), data.container.slots);
    }

    /**
     * A backpack registers an Item and no Block: there is nothing to place, so
     * the block registry never hears about it and BLOCK_HOLDERS stays free of
     * something the block entity type would choke on.
     */
    private static void registerCarriedContainer(ContainerContentData data) {
        ITEMS.registerItem(data.id, props -> new CustomBackpackItem(data, containerItemProps(data, props)));
        CONTAINER_MAP.put(Identifier.fromNamespaceAndPath("customgear", data.id), data);
        LOGGER.info("[CustomGear] Container registered: {} (backpack, {} slots)",
                data.id, data.container.slots);
    }

    /**
     * ONE BlockItem class for every container shape.
     * <p>
     * Chests and shulkers used to need a subclass each, purely to hand rendering
     * over to a BlockEntityWithoutLevelRenderer. That class is gone: a shape
     * that cannot be baked is now declared in the item MODEL, as a
     * "minecraft:special" entry naming the vanilla chest or shulker renderer —
     * see BlockModelGenerator. With nothing left for them to override, the two
     * subclasses were deleted rather than kept as empty shells.
     */
    private static BlockItem itemFor(ContainerContentData data, Block block, Item.Properties props) {
        return new CustomContainerBlockItem(block, blockContainerItemProps(data, props));
    }

    /**
     * The subtype picks the block class. Anything not listed here was already
     * rejected by the parser, so reaching the default means the two lists have
     * drifted apart — worth failing loudly rather than guessing a shape.
     */
    private static CustomContainerBlock blockFor(ContainerContentData data, BlockBehaviour.Properties props) {
        if (ContainerData.KIND_BARREL.equals(data.container.kind())) {
            return new CustomBarrelBlock(data, props);
        }
        if (ContainerData.KIND_CHEST.equals(data.container.kind())) {
            return new CustomChestBlock(data, props);
        }
        if (ContainerData.KIND_SHULKER.equals(data.container.kind())) {
            return new CustomShulkerBlock(data, props);
        }
        throw new IllegalStateException(
                "No block class for container type '" + data.container.kind()
                        + "' on '" + data.id + "' — the parser accepted a subtype "
                        + "ContainerRegistry cannot build.");
    }

    /**
     * A keeps_contents container must NOT stack. Stacking merges two items into
     * one set of components, which is one inventory: place both, and each reads
     * the same contents (duplication), or one inventory is simply lost. An item
     * cannot stack conditionally on its components, so vanilla's shulker box is
     * stacksTo(1) whether it holds anything or not, and so is this.
     */
    private static Item.Properties containerItemProps(ContainerContentData data, Item.Properties props) {
        Item.Properties p = props;
        if (data.fireResistant) p = p.fireResistant();
        return data.container.keepsContents() ? p.stacksTo(1) : p;
    }

    /**
     * Same as BlockRegistry: a BlockItem no longer inherits its block's
     * translation key, so it has to ask for the block prefix explicitly. NOT
     * applied to a backpack — that registers a plain Item, and LangGenerator
     * writes item.customgear.&lt;id&gt; for it on purpose.
     */
    private static Item.Properties blockContainerItemProps(ContainerContentData data, Item.Properties props) {
        return containerItemProps(data, props.useBlockDescriptionPrefix());
    }

    /**
     * Refreshes the definitions during /customgear reload. Slot count is baked
     * at registration and is NOT affected — everything else (names, drop
     * behaviour, columns, obstruction) reads this map live.
     */
    public static void updateContainerData(List<ContainerContentData> containerList) {
        CONTAINER_MAP.clear();
        for (ContainerContentData data : containerList) {
            CONTAINER_MAP.put(Identifier.fromNamespaceAndPath("customgear", data.id), data);
        }
        LOGGER.info("[CustomGear] Updated {} containers in registry", containerList.size());
    }
}