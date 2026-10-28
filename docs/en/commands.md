# Commands

| Command                      | Permission | Description                                                                                                                                                  |
|------------------------------|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/customgear reload`         | OP level 2 | Reloads all JSON files and textures without restarting                                                                                                       |
| `/customgear recipes [item]` | Everyone   | Lists the recipes that make an item (the one in hand by default), with click-to-copy IDs. See [recipe_patch](recipes.md#changing-other-recipes-recipe_patch) |

## What the reload command updates

- Item names
- Held effects (weapons and tools)
- Piece effects and set bonuses (armor)
- Fluid contact effects and burning behavior
- Recipes (the command reloads data packs automatically)
- Block mining tags — `required_tool` changes and `harvest_level` adjustments (1–4)
- Durability display
- Textures and models — **after pressing F3+T** (the game only reloads client resources on demand)
- Mob drop settings (`chance`, `min`/`max`, `entities`)
- Damage, attacker, and conditional resistance (including `show_player_resistances`)

## What requires a full game restart

- Attack damage and attack speed
- Armor defense, toughness, and knockback resistance
- Tool mining speed and tool tier (tool sets)
- Enabling/disabling a block's drop requirement (`harvest_level` 0 ↔ ≥1)
- Adding or removing items (new or deleted JSON files)
- Changing item IDs
- `fire_resistant` changes
- Switching an armor layer between mechanisms (reference ↔ custom ↔ transparent)
- Adding or removing the `armor_3d` block (3D rendering is baked at registration)
