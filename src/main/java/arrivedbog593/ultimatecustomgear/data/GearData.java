package arrivedbog593.ultimatecustomgear.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class GearData {
    // Common fields
    public String id;
    public String type;
    public Map<String, String> names;

    @SerializedName("piece_names")
    public Map<String, Map<String, String>> pieceNames;

    public int durability;
    public boolean enchantable;
    public int enchantability;

    /** Where this gear's textures come from. */
    public GearTextureData texture;

    // Armor
    public Map<String, PieceData> pieces;

    @SerializedName("piece_effects")
    public Map<String, List<EffectData>> pieceEffects;

    @SerializedName("set_bonus")
    public SetBonusData setBonus;

    // Individual weapons and tools
    @SerializedName("attack_damage")
    public float attackDamage;

    @SerializedName("attack_damage_bonus")
    public float attackDamageBonus;

    @SerializedName("attack_speed")
    public float attackSpeed;

    @SerializedName("mining_speed")
    public float miningSpeed;

    @SerializedName("harvest_level")
    public int harvestLevel;

    @SerializedName("held_effects")
    public List<EffectData> heldEffects;

    @SerializedName("till_radius")
    public int tillRadius;

    // Tool set
    public Map<String, ToolData> tools;

    @SerializedName("tool_names")
    public Map<String, Map<String, String>> toolNames;

    // Weapon set
    public Map<String, WeaponData> weapons;

    @SerializedName("weapon_names")
    public Map<String, Map<String, String>> weaponNames;

    // Damage multiplier (sword, axe, etc.)
    @SerializedName("damage_multiplier")
    public float damageMultiplier = 1.0f;

    // Bow / Crossbow specific
    @SerializedName("arrow_damage")
    public float arrowDamage;

    @SerializedName("arrow_damage_bonus")
    public float arrowDamageBonus;

    @SerializedName("arrow_damage_multiplier")
    public float arrowDamageMultiplier = 1.0f;

    @SerializedName("charge_speed")
    public float chargeSpeed = 1.0f;

    // Recipes (sets: a map by piece; single items: a list)
    public Map<String, List<RecipeData>> recipes;
    public List<RecipeData> recipe;

    /** If true, the ITEM survives fire and lava when dropped (like netherite).
     *  Does NOT make the wearer fire-immune (use fire_resistance effects for that). */
    @SerializedName("fire_resistant")
    public boolean fireResistant = false;

    /**
     * Whether this armor accepts smithing trims. Off by default: trims draw
     * over the armor layers, so a set with transparent layers or a GeckoLib 3D
     * model applies the trim but never shows it.
     * <p>
     * Opting out needs an explicit tag removal — vanilla's trimmable_armor is
     * the union of the four slot tags, so armor inherits it just by being
     * armor. See GearTagLoader.
     */
    @SerializedName("trimmable")
    public boolean trimmable = false;

    /**
     * Damage resistances applied per equipped piece. Keys: damage type tags
     * ("#minecraft:is_fire") or exact ids ("iceandfire:dragon_fire").
     * Values: reduction per piece, 0.0-1.0. Piece-level maps merge with this
     * one, winning only on the keys they declare.
     */
    @SerializedName("damage_resistances")
    public Map<String, Double> damageResistances;

    /**
     * Resistances against specific attackers ("against WHOM"). Keys: entity
     * ids ("minecraft:zombie"), entity tags ("#minecraft:undead"), mod
     * wildcards ("mekanism:*"), "all", or a player ("player:Steve").
     * Values: reduction per equipped piece, 0.0-1.0.
     */
    @SerializedName("attacker_resistances")
    public Map<String, Double> attackerResistances;

    @SerializedName("show_player_resistances")
    public boolean showPlayerResistances = false;

    /**
     * Combined conditions: reduce only when the damage type AND the attacker
     * match. Either key may be omitted to check just the other one.
     */
    @SerializedName("conditional_resistances")
    public List<ConditionalResistance> conditionalResistances;


    // --- Internal classes ---

    /**
     * Gear textures, plus the two things only armor has.
     * <p>
     * Every value here takes the SHORT form: the model system adds 'textures/'
     * and '.png' itself, so a full path would resolve to nothing. The exception
     * is armor_3d, whose values go straight to GeckoLib and are written in full.
     */
    public static class GearTextureData extends TextureData {

        /**
         * The worn armor layers, "layer_1" and "layer_2".
         * <p>
         * Separate from refs because those are ICONS: a piece's inventory icon
         * and the layer drawn on the body are different images with different
         * layouts, and a set routinely has one without the other.
         * <p>
         * The value "transparent" is a keyword rather than a path — it makes the
         * worn armor invisible, for sets that render through armor_3d instead.
         */
        @SerializedName("armor_layers")
        public Map<String, String> armorLayers;

        /** GeckoLib model, texture and optional animation. */
        @SerializedName("armor_3d")
        public Armor3DData armor3d;
    }

    public static class PieceData {
        public int durability;
        public int defense;
        public double knockback_resistance;
        public double toughness;
        /**
         * When true (default) the piece inherits the set-level resistances and
         * its own entries merge on top, winning only on the keys it declares.
         * When false the set-level resistances do not apply to this piece at
         * all: only what it declares below counts, and declaring nothing means
         * this piece contributes no resistance.
         */
        @SerializedName("inherit_set_resistances")
        public boolean inheritSetResistances = true;

        /** Per-piece damage_resistances. Merges with the set-level map. */
        @SerializedName("damage_resistances")
        public Map<String, Double> damageResistances;

        /** Per-piece attacker_resistances. Merges with the set-level map. */
        @SerializedName("attacker_resistances")
        public Map<String, Double> attackerResistances;

        /** Per-piece conditional_resistances. Merges by (attacker, damage) pair. */
        @SerializedName("conditional_resistances")
        public List<ConditionalResistance> conditionalResistances;
    }

    public static class ToolData {
        public int durability;

        @SerializedName("attack_damage")
        public float attackDamage;

        @SerializedName("attack_damage_bonus")
        public float attackDamageBonus;

        @SerializedName("attack_speed")
        public float attackSpeed;

        @SerializedName("mining_speed")
        public float miningSpeed;

        @SerializedName("harvest_level")
        public int harvestLevel;

        @SerializedName("held_effects")
        public List<EffectData> heldEffects;

        @SerializedName("till_radius")
        public int tillRadius;

        @SerializedName("damage_multiplier")
        public float damageMultiplier = 1.0f;
    }

    public static class WeaponData {
        public int durability;

        @SerializedName("attack_damage")
        public float attackDamage;

        @SerializedName("attack_damage_bonus")
        public float attackDamageBonus;

        @SerializedName("attack_speed")
        public float attackSpeed;

        @SerializedName("damage_multiplier")
        public float damageMultiplier = 1.0f;

        @SerializedName("arrow_damage")
        public float arrowDamage;

        @SerializedName("arrow_damage_bonus")
        public float arrowDamageBonus;

        @SerializedName("arrow_damage_multiplier")
        public float arrowDamageMultiplier = 1.0f;

        @SerializedName("charge_speed")
        public float chargeSpeed = 1.0f;

        @SerializedName("held_effects")
        public List<EffectData> heldEffects;
    }

    public static class EffectData {
        public String effect;
        public int amplifier;
    }

    public static class SetBonusData {
        @SerializedName("required_pieces")
        public int requiredPieces;
        public List<EffectData> effects;
    }

    public static class Armor3DData {
        /** Blockbench GeckoLib model (.geo.json). Required. */
        public String model;

        /** PNG painted for the model's UV layout. Required. */
        public String texture;

        /** Optional .animation.json — without it the model is static. */
        public String animation;

        /** True when both required fields are present. */
        public boolean isComplete() {
            return model != null && !model.isBlank()
                    && texture != null && !texture.isBlank();
        }
    }

    public static class ConditionalResistance {
        /** Damage type tag or exact id. Optional if "attacker" is present. */
        public String damage;

        /** Entity key (id, #tag, mod:*, player:Name). Optional if "damage" is present. */
        public String attacker;

        /** Reduction per equipped piece, 0.0-1.0 */
        public double amount;
    }
}