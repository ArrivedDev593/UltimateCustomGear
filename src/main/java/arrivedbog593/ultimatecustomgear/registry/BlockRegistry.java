package arrivedbog593.ultimatecustomgear.registry;

import arrivedbog593.ultimatecustomgear.data.BlockData;
import arrivedbog593.ultimatecustomgear.items.blocks.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers blocks loaded from JSON.
 * <p>
 * Also registers the corresponding BlockItem so the block
 * can be held in inventory and crafted.
 * <p>
 * Containers are NOT here — see ContainerRegistry. They register the same two
 * registry entries a block does, but not all of them are blocks, and the
 * block entity type belongs with them.
 * <p>
 * Required tool and tool level are applied via block tags:
 *   data/minecraft/tags/blocks/needs_stone_tool.json → toolLevel >= 1
 *   data/minecraft/tags/blocks/needs_iron_tool.json → toolLevel >= 2
 *   data/minecraft/tags/blocks/needs_diamond_tool.json → toolLevel >= 3
 * Tool type tags:
 *   data/minecraft/tags/blocks/mineable/pickaxe.json → requiredTool = "pickaxe"
 *   data/minecraft/tags/blocks/mineable/axe.json → requiredTool = "axe"
 *   etc.
 */
public class BlockRegistry {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks("customgear");

    public static final DeferredRegister.Items BLOCK_ITEMS =
            DeferredRegister.createItems("customgear");

    /** blockId → BlockData for runtime lookups */
    public static final Map<Identifier, BlockData> BLOCK_MAP = new HashMap<>();

    public static void register(IEventBus modEventBus, List<BlockData> blockList) {
        for (BlockData data : blockList) {
            registerBlock(data);
        }
        BLOCKS.register(modEventBus);
        BLOCK_ITEMS.register(modEventBus);
    }

    private static void registerBlock(BlockData data) {
        if (data.directional) {
            DeferredHolder<Block, CustomDirectionalBlock> blockHolder =
                    BLOCKS.registerBlock(data.id, props -> new CustomDirectionalBlock(data, props));
            BLOCK_ITEMS.registerItem(data.id, props ->
                    new BlockItem(blockHolder.get(), blockItemProps(data, props)));
            LOGGER.info("[CustomGear] Directional block registered: {}", data.id);
        } else if (data.gravity) {
            DeferredHolder<Block, CustomFallingBlock> blockHolder =
                    BLOCKS.registerBlock(data.id, props -> new CustomFallingBlock(data, props));
            BLOCK_ITEMS.registerItem(data.id, props ->
                    new BlockItem(blockHolder.get(), blockItemProps(data, props)));
            LOGGER.info("[CustomGear] Falling block registered: {}", data.id);
        } else {
            DeferredHolder<Block, CustomBlock> blockHolder =
                    BLOCKS.registerBlock(data.id, props -> new CustomBlock(data, props));
            BLOCK_ITEMS.registerItem(data.id, props ->
                    new BlockItem(blockHolder.get(), blockItemProps(data, props)));
            LOGGER.info("[CustomGear] Block registered: {}", data.id);
        }
        Identifier loc = Identifier.fromNamespaceAndPath("customgear", data.id);
        BLOCK_MAP.put(loc, data);
    }

    /**
     * An item's translation key now comes from its own registry entry with an
     * 'item.' prefix; a BlockItem no longer inherits its block's. Without this
     * the name is looked up as item.customgear.&lt;id&gt; while LangGenerator wrote
     * block.customgear.&lt;id&gt;, and the raw key shows instead of a name.
     */
    private static Item.Properties blockItemProps(BlockData data, Item.Properties props) {
        Item.Properties p = props.useBlockDescriptionPrefix();
        return data.fireResistant ? p.fireResistant() : p;
    }

    /**
     * Updates block data during reload without restarting the game.
     * Allows refreshing names and other runtime properties.
     */
    public static void updateBlockData(List<BlockData> blockList) {
        BLOCK_MAP.clear();
        for (BlockData data : blockList) {
            BLOCK_MAP.put(Identifier.fromNamespaceAndPath("customgear", data.id), data);
        }
        LOGGER.info("[CustomGear] Updated {} blocks in registry", blockList.size());
    }
}