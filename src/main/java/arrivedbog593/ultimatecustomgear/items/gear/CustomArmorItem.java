package arrivedbog593.ultimatecustomgear.items.gear;

import arrivedbog593.ultimatecustomgear.data.GearData;
import arrivedbog593.ultimatecustomgear.resources.TextureRef;
import arrivedbog593.ultimatecustomgear.util.GearLookup;
import arrivedbog593.ultimatecustomgear.util.TooltipHelper;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Consumer;

/**
 * An armour piece built from JSON.
 * <p>
 * NOT a subclass of the vanilla armour item: that class no longer exists. An
 * armour piece is now an {@code Item} carrying an EQUIPPABLE component, applied
 * by {@code Item.Properties.humanoidArmor(material, type)}.
 * <p>
 * WHAT CHANGED FOR THE WORN TEXTURE. Up to 1.21.1 the material named a texture
 * prefix directly and the renderer appended {@code _layer_1} / {@code _layer_2}.
 * Now the material names an EQUIPMENT ASSET — a JSON under
 * {@code assets/<ns>/equipment/<name>.json} that lists one texture per body
 * layer. The two old layers map onto the {@code humanoid} and
 * {@code humanoid_leggings} entries of that file, which GearModelGenerator
 * writes; this class only has to decide WHICH asset key the item points at.
 */
public class CustomArmorItem extends Item {

    /** Repair material is not part of the JSON schema, so nothing repairs it. */
    private static final TagKey<Item> NO_REPAIR_ITEMS = ItemTags.create(
            Identifier.fromNamespaceAndPath("customgear", "never_repairs"));

    private final GearData initialGearData;
    private final String   piece;

    public CustomArmorItem(GearData data, String piece, Item.Properties props) {
        super(buildProps(props, data, piece));
        this.initialGearData = data;
        this.piece = piece;
    }

    private static Item.Properties buildProps(Item.Properties props, GearData data, String piece) {
        Item.Properties p = props.humanoidArmor(buildMaterial(data, piece), pieceToType(piece));
        int durability = data.pieces.get(piece).durability;
        if (durability > 0) p = p.durability(durability);
        return data.fireResistant ? p.fireResistant() : p;
    }

    private GearData getGearData() {
        return GearLookup.getGearData(this, initialGearData);
    }

    /** Called by SetBonusHandler — keeps the existing public API. */
    public GearData getGearDataDirect() {
        return getGearData();
    }

    public String getPiece() {
        return piece;
    }

    @Override
    public int getMaxDamage(@NotNull ItemStack stack) {
        GearData data = getGearData();
        if (data.pieces != null && data.pieces.containsKey(piece)) {
            int dur = data.pieces.get(piece).durability;
            return dur > 0 ? dur : super.getMaxDamage(stack);
        }
        return super.getMaxDamage(stack);
    }

    /**
     * Defense is declared for THIS piece only. The map still has to carry every
     * slot, because one material is built per piece rather than one per set —
     * the JSON allows a set whose pieces disagree on toughness, which a shared
     * material could not express.
     */
    private static ArmorMaterial buildMaterial(GearData data, String piece) {
        GearData.PieceData pieceData = data.pieces.get(piece);
        return new ArmorMaterial(
                Math.max(pieceData.durability, 1),
                Map.of(
                        ArmorType.HELMET,     piece.equals("helmet")     ? pieceData.defense : 0,
                        ArmorType.CHESTPLATE, piece.equals("chestplate") ? pieceData.defense : 0,
                        ArmorType.LEGGINGS,   piece.equals("leggings")   ? pieceData.defense : 0,
                        ArmorType.BOOTS,      piece.equals("boots")      ? pieceData.defense : 0,
                        ArmorType.BODY,       0
                ),
                Math.max(data.enchantability, 1),
                SoundEvents.ARMOR_EQUIP_IRON,
                (float) pieceData.toughness,
                (float) pieceData.knockback_resistance,
                NO_REPAIR_ITEMS,
                buildAssetId(data));
    }

    /**
     * Which equipment asset the worn armour reads.
     * <p>
     * A REFERENCE names another mod's asset. Authors paste the path they see in
     * that mod's jar, which is still written the old way — {@code models/armor/}
     * prefix, {@code _layer_1} suffix — so both are trimmed here, exactly as
     * before. What is left is the asset name, which is what the equipment
     * registry is keyed by.
     */
    private static ResourceKey<EquipmentAsset> buildAssetId(GearData data) {
        if (data.texture == null || data.texture.armorLayers == null) {
            return EquipmentAssets.IRON;
        }

        String layer1 = data.texture.armorLayers.get("layer_1");
        if (layer1 == null || layer1.isBlank()) {
            return EquipmentAssets.IRON;
        }

        // "transparent" points at the customgear asset, whose generated JSON
        // names the fully transparent PNG the generator injected.
        if ("transparent".equals(layer1)) {
            return customAsset(data.id);
        }

        return switch (TextureRef.kindOf(layer1)) {
            case REFERENCE -> {
                Identifier rl = Identifier.parse(layer1);
                String path = rl.getPath();
                if (path.startsWith("models/armor/")) {
                    path = path.substring("models/armor/".length());
                }
                if (path.endsWith("_layer_1")) {
                    path = path.substring(0, path.length() - "_layer_1".length());
                }
                yield ResourceKey.create(EquipmentAssets.ROOT_ID,
                        Identifier.fromNamespaceAndPath(rl.getNamespace(), path));
            }
            case FILE    -> customAsset(data.id);
            case INVALID -> EquipmentAssets.IRON;
        };
    }

    /** The asset GearModelGenerator writes for this gear's own layers. */
    public static ResourceKey<EquipmentAsset> customAsset(String gearId) {
        return ResourceKey.create(EquipmentAssets.ROOT_ID,
                Identifier.fromNamespaceAndPath("customgear", gearId));
    }

    private static ArmorType pieceToType(String piece) {
        return switch (piece) {
            case "helmet"     -> ArmorType.HELMET;
            case "chestplate" -> ArmorType.CHESTPLATE;
            case "leggings"   -> ArmorType.LEGGINGS;
            case "boots"      -> ArmorType.BOOTS;
            default -> throw new IllegalArgumentException("Invalid piece: " + piece);
        };
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        GearData data = getGearData();
        if (data.pieceNames == null) return super.getName(stack);

        String lang = GearLookup.getCurrentLang();
        Map<String, String> namesForLang = data.pieceNames.getOrDefault(lang, data.pieceNames.get("en_us"));
        if (namesForLang != null && namesForLang.containsKey(piece)) {
            return Component.literal(namesForLang.get(piece));
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack,
                                @NotNull Item.TooltipContext context,
                                @NotNull TooltipDisplay display,
                                @NotNull Consumer<Component> builder,
                                @NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, display, builder, tooltipFlag);
        GearData data = getGearData();
        TooltipHelper.addPieceEffectsTooltip(builder, data, piece);
        TooltipHelper.addSetBonusTooltip(builder, data);
        TooltipHelper.addDamageResistancesTooltip(builder, data, piece);
        TooltipHelper.addAttackerResistancesTooltip(builder, data, piece);
        TooltipHelper.addConditionalResistancesTooltip(builder, data, piece);
        if (!TooltipHelper.detailsShown() && TooltipHelper.hasDetails(data, piece)) {
            TooltipHelper.addDetailsHint(builder);
        }
    }
}
