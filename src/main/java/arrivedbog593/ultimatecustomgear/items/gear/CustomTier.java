package arrivedbog593.ultimatecustomgear.items.gear;

import arrivedbog593.ultimatecustomgear.data.GearData;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;

/**
 * Builds the vanilla {@link ToolMaterial} for a gear definition.
 * <p>
 * WHY THIS NO LONGER IMPLEMENTS ANYTHING. Up to 1.21.1 a tool's stats came from
 * a {@code Tier} interface the mod could implement, so every value was read live
 * from the JSON on each call. {@code ToolMaterial} is a record: the numbers are
 * baked in when the item is built and cannot change afterwards.
 * <p>
 * That costs the mod less than it looks. The live values still reach the player,
 * because every custom tool overrides {@code getMaxDamage} and builds its
 * tooltip from the current {@code GearData}. What is now fixed at registration
 * is the mining tier and the attribute modifiers — and both already needed a
 * restart before, since they are baked into the item's components.
 */
public final class CustomTier {

    private CustomTier() {}

    /**
     * Repair material is not part of the JSON schema, so custom gear repairs
     * from nothing. An empty tag is how that is spelled now that the old
     * {@code Ingredient.EMPTY} is gone.
     */
    private static final TagKey<Item> NO_REPAIR_ITEMS = ItemTags.create(
            Identifier.fromNamespaceAndPath("customgear", "never_repairs"));

    public static ToolMaterial of(GearData data) {
        return new ToolMaterial(
                incorrectBlocksFor(data.harvestLevel),
                data.durability > 0 ? data.durability : 64,
                data.miningSpeed > 0 ? data.miningSpeed : 1.0f,
                Math.max(data.attackDamageBonus, 0),
                Math.max(data.enchantability, 1),
                NO_REPAIR_ITEMS);
    }

    private static TagKey<Block> incorrectBlocksFor(int harvestLevel) {
        return switch (harvestLevel) {
            case 0  -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
            case 1  -> BlockTags.INCORRECT_FOR_STONE_TOOL;
            case 2  -> BlockTags.INCORRECT_FOR_IRON_TOOL;
            case 3  -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            default -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        };
    }
}
