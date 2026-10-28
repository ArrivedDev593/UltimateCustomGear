package arrivedbog593.ultimatecustomgear.registry;

import arrivedbog593.ultimatecustomgear.data.GearData;
import arrivedbog593.ultimatecustomgear.items.gear.CustomArmorItem;
import arrivedbog593.ultimatecustomgear.items.tools.CustomToolItem;
import arrivedbog593.ultimatecustomgear.items.weapons.*;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GearRegistry {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems("customgear");

    // FIX: ConcurrentHashMap so reads are safe across threads.
    // Exposed only as an immutable map through getters, so other mods can't modify it directly.
    private static volatile Map<Identifier, GearData> gearMap = new ConcurrentHashMap<>();
    private static volatile Map<Identifier, String>   toolTypeMap = new ConcurrentHashMap<>();

    /**
     * Direct lookup for the hot paths in tick events. Returns one entry; the map
     * itself stays private so nothing outside this class can modify it.
     */
    public static GearData lookupGear(Identifier loc) {
        return gearMap.get(loc);
    }

    /**
     * Replaces both maps atomically during a reload.
     * Items already reading the old map finish reading it without an NPE;
     * later reads use the new map.
     */
    public static void atomicSwap(Map<Identifier, GearData> newGearMap,
                                  Map<Identifier, String>   newToolTypeMap) {
        gearMap     = new ConcurrentHashMap<>(newGearMap);
        toolTypeMap = new ConcurrentHashMap<>(newToolTypeMap);
        LOGGER.info("[CustomGear] Registry updated atomically: {} gear entries, {} tool-type entries",
                gearMap.size(), toolTypeMap.size());
    }

    public static void register(IEventBus modEventBus, List<GearData> gearList) {
        for (GearData data : gearList) {
            switch (data.type) {
                case "armor_set"  -> registerArmor(data);
                case "tool_set"   -> registerToolSet(data);
                case "weapon_set" -> registerWeaponSet(data);
                case "sword"      -> registerSword(data);
                case "bow"        -> registerBow(data);
                case "crossbow"   -> registerCrossbow(data);
                case "shield"     -> registerShield(data);
                case "pickaxe", "axe", "shovel", "hoe" -> registerTool(data);
                case "block", "item", "fluid", "advancement" -> {}
                default -> LOGGER.warn("[CustomGear] Unknown type: {}", data.type);
            }
        }
        ITEMS.register(modEventBus);
    }

    private static void registerArmor(GearData data) {
        if (data.pieces == null) return;

        // 3D rendering requires BOTH a complete armor_3d block and GeckoLib.
        // Anything else registers the plain armor item, which draws the flat
        // armor_layers — that is the fallback that keeps content working on
        // instances without GeckoLib.
        boolean useGeckoModel = data.texture != null
                && data.texture.armor3d != null
                && data.texture.armor3d.isComplete()
                && ModList.get().isLoaded("geckolib");

        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        for (String piece : pieces) {
            if (!data.pieces.containsKey(piece)) continue;
            String itemId = data.id + "_" + piece;

            if (useGeckoModel) {
                ITEMS.registerItem(itemId, props ->
                        new arrivedbog593.ultimatecustomgear.items.gear.geo.GeckoArmorItem(data, piece, props));
            } else {
                ITEMS.registerItem(itemId, props -> new CustomArmorItem(data, piece, props));
            }

            gearMap.put(Identifier.fromNamespaceAndPath("customgear", itemId), data);
        }
    }

    private static void registerSword(GearData data) {
        ITEMS.registerItem(data.id, props -> new CustomSwordItem(data, props));
        gearMap.put(Identifier.fromNamespaceAndPath("customgear", data.id), data);
    }

    private static void registerTool(GearData data) {
        ITEMS.registerItem(data.id, props -> CustomToolItem.create(data, props));
        gearMap.put(Identifier.fromNamespaceAndPath("customgear", data.id), data);
    }

    private static void registerToolSet(GearData data) {
        if (data.tools == null) return;
        String[] validTypes = {"pickaxe", "axe", "shovel", "hoe"};
        for (String toolType : validTypes) {
            if (!data.tools.containsKey(toolType)) continue;
            GearData.ToolData toolData = data.tools.get(toolType);
            String itemId = data.id + "_" + toolType;
            GearData derived = buildDerived(data, toolType, toolData);
            ITEMS.registerItem(itemId, props -> CustomToolItem.create(derived, props));
            Identifier loc = Identifier.fromNamespaceAndPath("customgear", itemId);
            gearMap.put(loc, derived);
            toolTypeMap.put(loc, toolType);
        }
    }

    private static void registerBow(GearData data) {
        ITEMS.registerItem(data.id, props -> new CustomBowItem(data, props));
        gearMap.put(Identifier.fromNamespaceAndPath("customgear", data.id), data);
    }

    private static void registerCrossbow(GearData data) {
        ITEMS.registerItem(data.id, props -> new CustomCrossbowItem(data, props));
        gearMap.put(Identifier.fromNamespaceAndPath("customgear", data.id), data);
    }

    private static void registerShield(GearData data) {
        ITEMS.registerItem(data.id, props -> new CustomShieldItem(data, props));
        gearMap.put(Identifier.fromNamespaceAndPath("customgear", data.id), data);
    }

    private static void registerWeaponSet(GearData data) {
        if (data.weapons == null) return;
        String[] validTypes = {"sword", "bow", "crossbow", "shield"};
        for (String weaponType : validTypes) {
            if (!data.weapons.containsKey(weaponType)) continue;
            GearData.WeaponData weaponData = data.weapons.get(weaponType);
            String itemId = data.id + "_" + weaponType;
            GearData derived = buildWeaponDerived(data, weaponType, weaponData);
            ITEMS.registerItem(itemId, props -> switch (weaponType) {
                case "sword"    -> new CustomSwordItem(derived, props);
                case "bow"      -> new CustomBowItem(derived, props);
                case "crossbow" -> new CustomCrossbowItem(derived, props);
                case "shield"   -> new CustomShieldItem(derived, props);
                default -> throw new IllegalArgumentException("Invalid weapon type: " + weaponType);
            });
            gearMap.put(Identifier.fromNamespaceAndPath("customgear", itemId), derived);
        }
    }

    public static GearData buildWeaponDerived(GearData parent, String weaponType,
                                              GearData.WeaponData weaponData) {
        GearData derived = new GearData();
        derived.id = parent.id + "_" + weaponType;
        derived.type = weaponType;
        derived.names = parent.names;
        derived.weaponNames = parent.weaponNames;
        derived.durability = weaponData.durability > 0 ? weaponData.durability : parent.durability;
        derived.attackDamage = weaponData.attackDamage;
        derived.attackDamageBonus = weaponData.attackDamageBonus;
        derived.attackSpeed = weaponData.attackSpeed;
        derived.damageMultiplier = weaponData.damageMultiplier > 0 ? weaponData.damageMultiplier : 1.0f;
        derived.arrowDamage = weaponData.arrowDamage;
        derived.arrowDamageBonus = weaponData.arrowDamageBonus;
        derived.arrowDamageMultiplier = weaponData.arrowDamageMultiplier > 0 ? weaponData.arrowDamageMultiplier : 1.0f;
        derived.chargeSpeed = weaponData.chargeSpeed > 0 ? weaponData.chargeSpeed : 1.0f;
        derived.enchantable = parent.enchantable;
        derived.enchantability = parent.enchantability;
        derived.heldEffects = weaponData.heldEffects != null ? weaponData.heldEffects : parent.heldEffects;
        derived.texture = parent.texture;
        return derived;
    }

    public static GearData buildDerived(GearData parent, String toolType,
                                        GearData.ToolData toolData) {
        GearData derived = new GearData();
        derived.id = parent.id + "_" + toolType;
        derived.type = toolType;
        derived.names = parent.names;
        derived.toolNames = parent.toolNames;
        derived.durability = toolData.durability > 0 ? toolData.durability : parent.durability;
        derived.attackDamage = toolData.attackDamage;
        derived.attackDamageBonus = toolData.attackDamageBonus;
        derived.attackSpeed = toolData.attackSpeed;
        derived.miningSpeed = toolData.miningSpeed;
        derived.harvestLevel = toolData.harvestLevel;
        derived.tillRadius = toolData.tillRadius;
        derived.damageMultiplier = toolData.damageMultiplier > 0 ? toolData.damageMultiplier : 1.0f;
        derived.enchantable = parent.enchantable;
        derived.enchantability = parent.enchantability;
        derived.heldEffects = toolData.heldEffects != null ? toolData.heldEffects : parent.heldEffects;
        derived.texture = parent.texture;
        return derived;
    }
}