# UltimateCustomGear

**UltimateCustomGear** is a data-driven NeoForge mod for Minecraft 1.21.1 that allows server owners, modpack creators, and players to add fully custom armor sets, weapons, tools, food items, items, blocks, and fluids — all through simple JSON files. No coding required.

## Documentation

| Page                                        | What's in it                                                   |
|---------------------------------------------|----------------------------------------------------------------|
| [Getting Started](getting-started.md)       | Install, where files go, the list of content types             |
| [Common Fields](common-fields.md)           | Fields every type shares, the effect object, ID patterns       |
| [Armor Sets](armor-sets.md)                 | Per-piece stats, piece effects, set bonuses, trims             |
| [Weapons](weapons.md)                       | Swords, bows, crossbows, shields                               |
| [Tools](tools.md)                           | Pickaxes, axes, shovels, hoes, harvest tiers, area-tilling     |
| [Food](food.md)                             | Nutrition, eating speed, on-eat effects                        |
| [Blocks](blocks.md)                         | Per-face textures, directional, gravity, mining requirements   |
| [Fluids](fluids.md)                         | Color, spread, contact effects, burning                        |
| [Containers](containers.md)                 | Barrels, chests, shulkers and backpacks                        |
| [Textures & Models](textures-and-models.md) | The three texture modes, armor layers, 3D models with GeckoLib |
| [Recipes](recipes.md)                       | Every recipe type, tags as ingredients, passthrough            |
| [Recipe Examples](recipe-examples.md)       | Patches with Create, Mekanism and Botania machines             |
| [Tags](tags.md)                             | Belonging to tags, automatic gear tags, tag patches            |
| [Mob Drops](mob-drops.md)                   | Drop chance, count, entity filters, Looting                    |
| [Damage Resistances](damage-resistances.md) | Reduction by damage type, by attacker, or both                 |
| [Content Packs](content-packs.md)           | Shipping your content as a zip                                 |
| [Multiplayer](multiplayer.md)               | Content verification and enforcement modes                     |
| [Commands](commands.md)                     | `/customgear reload`, and what needs a restart                 |
| [Migration](migration.md)                   | Breaking changes, version by version                           |

**Reference lists** — [Block sound types](block-sounds.md) · [Map colors](map-colors.md) · [Damage types](damage-types.md)

New here? [Getting Started](getting-started.md) walks through your first item.

## Features

### Content Creation

- **Armor sets** with per-piece stats (durability, defense, toughness, knockback resistance)
- **Weapons** — swords, bows, crossbows, and shields with custom damage, durability, and charge speed
- **Tools** — pickaxes, axes, shovels, and hoes with mining speed, harvest tiers, and area-tilling
- **Food items** with nutrition, saturation, eating speed, and on-eating effects
- **Blocks** — including directional and gravity-affected, with per-face textures, light, sounds, and mining requirements
- **Fluids** with custom colors, contact effects, and burning behavior

### Effects & Gameplay

- Held effects, piece effects, and full-set bonuses
- **Mob drops** — any item can drop from mobs with configurable chance, count, and entity filters (hot-reloadable: balance your economy live)
- `fire_resistant` items that survive fire and lava, like netherite — works on items, food, gear, blocks, and buckets
- **Transparent armor** — armor with full stats and effects that draws nothing on the body
- **Damage resistances** — three layers of damage reduction by damage type, by attacker, or both combined (hot-reloadable)

### Textures & Models

- Reference vanilla/modded textures or provide your own PNG files
- Per-face block textures, bow-pulling animations, custom armor layers
- **3D armor models** via GeckoLib — use Blockbench models with optional animations instead of flat layers (optional dependency: without it, the armor falls back to 2D layers)

### Recipes & Tags

- **Native recipe system** — shaped, shapeless, smelting, blasting, and smithing, defined in JSON
- Recipe ingredients support **tags** (`"#minecraft:planks"` = any plank type, including items from other mods)
- Content can **belong to tags** via a `tags` field, so your items work in other mods' recipes

### Server & Multiplayer

- **Content packs** — distribute everything as a single `.zip` that players drop into `packs/`
- **Multiplayer content verification** — the server checks at login that clients have matching files, with configurable enforcement
- `/customgear reload` — update names, effects, recipes, and drops without restarting

### Quality of Life

- Full multi-language support per item
- Informative tooltips (effects, set bonuses, harvest levels, mob drops)
- Clear validation errors that name the file and the exact problem

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.x
- JEI (optional, recommended) — recipes are fully visible
- GeckoLib (optional) — required only for 3D armor models
- Modded enchantments work automatically on enchantable items
- Models from any installed mod can be referenced with `reference` mode
- Ingredients from any installed mod can be used in recipes
- Multiplayer: server and clients must run the same mod version and matching content files (verified automatically at login)
