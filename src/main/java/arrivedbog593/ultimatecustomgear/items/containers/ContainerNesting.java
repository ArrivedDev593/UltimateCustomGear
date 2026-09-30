package arrivedbog593.ultimatecustomgear.items.containers;

import arrivedbog593.ultimatecustomgear.registry.ComponentRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Whether a stack carries an inventory, or would grow one.
 * <p>
 * Lives here because BOTH sides need the same answer: the block entity enforces
 * it, and the client's stand-in container uses it to avoid predicting a move the
 * server is about to refuse. Two copies of this rule would drift.
 */
public final class ContainerNesting {

    private ContainerNesting() {}

    /**
     * Four ways a stack can carry an inventory or be about to: our own
     * component, vanilla's minecraft:container (a filled shulker or bundle), an
     * EMPTY container of ours, and an EMPTY vanilla shulker. The last two have
     * no component yet but would grow one the moment someone filled them.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isContainer(ItemStack stack) {
        if (stack.has(ComponentRegistry.CONTAINER_CONTENTS.get())) return true;
        if (stack.has(DataComponents.CONTAINER)) return true;

        // A backpack is not a BlockItem, so the branch below never saw one. An
        // empty one carries no component yet, and keeps_contents cannot be false
        // on a backpack — it always grows an inventory the moment someone fills
        // it, so it never nests.
        if (stack.getItem() instanceof CustomBackpackItem) return true;

        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof ShulkerBoxBlock) return true;

            // OUR containers only count when they carry their inventory in the
            // item. A barrel that spills when broken holds no NBT, so nesting it
            // nests nothing — refusing it is stricter than vanilla for no gain,
            // and canFitInsideContainerItems already draws the same line.
            if (block instanceof CustomContainerBlock) {
                return !blockItem.canFitInsideContainerItems();
            }
        }
        return false;
    }
}