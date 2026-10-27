# Recipes

UltimateCustomGear supports native crafting recipes defined directly in JSON. No external mods are required.

## Recipe types

| Type                 | Description                                        |
|----------------------|----------------------------------------------------|
| `shaped`             | Crafting table with a specific pattern             |
| `shapeless`          | Crafting table, ingredients in any order           |
| `smelting`           | Furnace                                            |
| `blasting`           | Blast furnace                                      |
| `smoking`            | Smoker                                             |
| `campfire_cooking`   | Campfire                                           |
| `stonecutting`       | Stonecutter                                        |
| `smithing_transform` | Smithing table (template + base + addition)        |
| `passthrough`        | Another mod's recipe type, copied verbatim         |

## Tags as ingredients

Any ingredient slot accepts a **tag** with the `#` prefix — the recipe will
accept any item in that tag:

```json
{
  "key": {
    "P": "#minecraft:planks",
    "I": "#c:ingots/iron",
    "S": "minecraft:stick"
  }
}
```

Works in every recipe type this mod builds itself. Inside `passthrough` you write tags the way the target mod expects them (usually `{"tag": "c:ingots/gold"}`), not with the `#` shorthand — that body is copied verbatim and never translated. Recipes with structural errors (uneven pattern rows, undefined pattern symbols, unused keys, malformed IDs) are skipped with a detailed message in the log naming the item and the exact problem.

**Ingredients from other mods.** A recipe that uses another mod's item as an
ingredient — `"mekanism:ingot_osmium"`, say — only loads when that mod is
installed. Without it, the recipe is skipped quietly instead of logging an
error, so the same content works in modpacks with and without the mod. Nothing
to write: it is read from the ingredients. Tags need none of this, since an
empty tag is not an error.

## Individual item recipe

```json
{
  "recipe": {
    "type": "shaped",
    "pattern": [
      " G ",
      " G ",
      " S "
    ],
    "key": {
      "G": "mymod:my_gem",
      "S": "minecraft:stick"
    }
  }
}
```

For multiple recipes, use an array:

```json
{
  "recipe": [
    {
      "type": "shaped",
      ...
    },
    {
      "type": "smelting",
      "ingredient": "mymod:my_ore",
      "experience": 1.0,
      "cooking_time": 10
    }
  ]
}
```

## Set recipe (armor, tools, weapons)

```json
{
  "recipes": {
    "helmet": {
      "type": "shaped",
      "pattern": [
        "GGG",
        "G G",
        "   "
      ],
      "key": {
        "G": "mymod:my_gem"
      }
    },
    "chestplate": {
      "type": "shaped",
      "pattern": [
        "G G",
        "GGG",
        "GGG"
      ],
      "key": {
        "G": "mymod:my_gem"
      }
    },
    "leggings": {
      "type": "shaped",
      "pattern": [
        "GGG",
        "G G",
        "G G"
      ],
      "key": {
        "G": "mymod:my_gem"
      }
    },
    "boots": {
      "type": "shaped",
      "pattern": [
        "   ",
        "G G",
        "G G"
      ],
      "key": {
        "G": "mymod:my_gem"
      }
    }
  }
}
```

## Smithing table recipe

```json
{
  "recipe": {
    "type": "smithing_transform",
    "template": "minecraft:netherite_upgrade_smithing_template",
    "base": "mymod:my_diamond_sword",
    "addition": "minecraft:netherite_ingot"
  }
}
```

> Ingredients support any item from any installed mod via resource location (e.g. `"othermod:special_ingot"`).

## Passthrough recipes (other mods)

`passthrough` hands a raw recipe body to the game without this mod
interpreting it, so another mod's recipe type can produce your content:

```json
{
  "recipe": {
    "type": "passthrough",
    "json": {
      "type": "create:mixing",
      "heat_requirement": "heated",
      "ingredients": [
        { "item": "minecraft:amethyst_shard" },
        { "tag": "c:ingots/gold" }
      ],
      "results": [
        { "count": 1, "id": "customgear:my_gem" }
      ]
    }
  }
}
```

Everything inside `json` is copied exactly as written, including how tags are
expressed — `{"tag": "c:ingots/gold"}` in Create's case, not this mod's `#`
shorthand. Write it the way that mod documents it; this mod does not translate
or validate the schema, because doing so would mean knowing it.

Whether a given field accepts tags at all is up to that mod, not this one.
Most use vanilla's `Ingredient` system and take tags anywhere an item goes,
but a field expecting a literal ID will reject one — and the error will come
from their deserializer, not from here.

A `neoforge:mod_loaded` condition is derived from the namespace of the inner
`type`, so the recipe is skipped when that mod is absent instead of breaking
the datapack. For recipes spanning several mods, list the extras:

```json
{
  "type": "passthrough",
  "requires": ["create", "createaddition"],
  "json": { "type": "create:mixing", "...": "..." }
}
```

**The result is not this mod's to control.** Every other type builds the
result from the item that owns the recipe; here the result lives inside your
`json`, in whatever shape that mod uses. A passthrough recipe attached to
`ruby_gem` can produce something else entirely.

> ⚠️ `mod_loaded` protects against the mod being **missing**, not against it
> being a different **version**. Mods change their recipe schemas between
> versions, and a deserializer that throws can take down datapack loading
> entirely — not just that one recipe. Re-check your passthrough recipes when
> you update the target mod.

Working examples for Create, Mekanism and Botania machines are in
[Recipe Examples](recipe-examples.md).

## Changing other recipes (`recipe_patch`)

Every recipe above belongs to an item of yours. A `recipe_patch` is a file of
its own that works on **any** item and any recipe — vanilla, Create,
Mekanism, any mod — in three modes: **add**, **remove** and **replace**. One
file can use any of them.

Every recipe inside a patch is written exactly like an item's `recipe` field:
`shaped`, `smelting`… or `passthrough` for another mod's recipe type.

### Add

`add` gives recipes to items that are not yours. The key is the item the
recipe makes; the value is one recipe or an array:

```json
{
  "type": "recipe_patch",
  "add": {
    "minecraft:saddle": {
      "type": "shaped",
      "pattern": ["LLL", "L L"],
      "key": { "L": "minecraft:leather" }
    },
    "mekanism:dust_iron": [
      { "type": "smelting", "ingredient": "#c:raw_materials/iron" },
      {
        "type": "passthrough",
        "json": {
          "type": "create:milling",
          "ingredients": [{ "item": "minecraft:iron_ore" }],
          "results": [{ "id": "mekanism:dust_iron" }]
        }
      }
    ]
  }
}
```

A recipe for another mod's item only loads when that mod is installed, and a
passthrough recipe only when the mod of its `type` is. In a passthrough
recipe the result is the one inside `json`; the key only names the recipe.

Added recipes get the ID `customgear:patch/<namespace>/<item>_recipe`
(`_recipe_2`, `_recipe_3`… when there are several).

### Remove

```json
{
  "type": "recipe_patch",
  "remove": ["minecraft:gold_ingot_from_nuggets"],
  "remove_output": ["minecraft:golden_apple"],
  "recipe_types": ["minecraft:crafting"]
}
```

- `remove` takes recipe IDs.
- `remove_output` takes item IDs and removes every recipe whose **main**
  output is that item.
- `recipe_types` limits `remove_output` to those types — here, the crafting
  recipe of the golden apple goes and any other stays. Without it, all types.
  A type without a namespace is a vanilla one: `"smelting"` is
  `"minecraft:smelting"`. Other mods' types need theirs: `"create:crushing"`.

**Main and secondary outputs.** `remove_output` only matches the main output —
what the recipe makes first. Create's crushing of gold ore also gives
experience nuggets, and a Mekanism sawmill gives sawdust next to the planks.
Removing a recipe removes **all** of it, so matching those secondary outputs
would wipe the gold crushing just to stop the nuggets. To remove one of those
recipes, use its ID in `remove`.

`remove_output` never removes recipes added by a `recipe_patch`, so "remove
every recipe for X and add mine" works in a single file.

### Replace

`replace` swaps a recipe for another **under the same ID**, so recipe-book
unlocks, advancements and anything else that names the recipe keep working.
The key is the recipe ID:

```json
{
  "type": "recipe_patch",
  "replace": {
    "minecraft:hopper": {
      "type": "shaped",
      "pattern": ["I I", "ICI", " I "],
      "key": { "I": "minecraft:iron_ingot", "C": "#c:chests" }
    }
  }
}
```

The new recipe makes the same item as the original, and the same amount,
unless you say otherwise: `result` changes the item and `result_count` the
amount. A `passthrough` replacement carries its own result in `json`.

Only an existing recipe is replaced. If the ID is not loaded — its mod is not
installed — nothing is added in its place. If the new recipe is invalid, the
original stays and the log says why.

### Finding IDs

`/customgear recipes <item>` (or with the item in your hand) lists every
recipe that makes it, grouped by type, with the secondary outputs apart in
yellow. Click a recipe ID or a type to copy it. It reads the live recipes with
patches already applied, so it also tells you whether a patch worked.

An ID from a mod that is not installed is ignored silently, so one patch can
serve several modpacks. When the mod **is** loaded and the ID does not exist,
a warning is logged: that is a typo.

| Field           | Type     | Description                                                                               |
|-----------------|----------|-------------------------------------------------------------------------------------------|
| `add`           | Map      | Item ID → recipe or array of recipes                                                      |
| `remove`        | String[] | Recipe IDs to remove                                                                      |
| `remove_output` | String[] | Item IDs: every recipe whose **main** output is that item is removed                      |
| `recipe_types`  | String[] | Limits `remove_output` to these recipe types, e.g. `["minecraft:crafting"]`. Default: all |
| `replace`       | Map      | Recipe ID → new recipe. Accepts `result` besides the usual recipe fields                  |
| `comment`       | String   | Never parsed. A patch has no name, so without this the file gives no clue why it exists   |

At least one of `add`, `remove`, `remove_output` or `replace` is required.
Several patches add up.

> ⚠️ Some "recipes" are not recipes. Tables that work in code — Iron's
> Spellbooks' inscription table, scroll forge and arcane anvil — and recipes a
> mod only draws in JEI are not in the recipe list, so neither the command nor
> a patch can see them. The same goes for machines that make fluids or
> chemicals instead of items (Mekanism's chemical washer, for example):
> `remove_output` takes items, but `remove` and `replace` by ID work on them.

See [Recipe Examples](recipe-examples.md) for complete patches that use
Create, Mekanism and Botania recipe types.

## Recipe Fields

| Field          | Type         | Description                                                                                                   |
|----------------|--------------|---------------------------------------------------------------------------------------------------------------|
| `recipe`       | Object/Array | Recipe for individual items. Can be a single object or an array for multiple recipes                          |
| `recipes`      | Map          | Recipes for sets. One entry per piece/tool/weapon key                                                         |
| `type`         | String       | See the [recipe types](#recipe-types) table                                                                   |
| `pattern`      | String[]     | (shaped) 1–3 rows of up to 3 characters each                                                                  |
| `key`          | Map          | (shaped) Maps each pattern character to an item ID                                                            |
| `ingredients`  | String[]     | (shapeless) List of item IDs                                                                                  |
| `ingredient`   | String       | (cooking/stonecutting) Single input item ID                                                                   |
| `experience`   | Float        | (cooking) XP granted on completion. Default: 0.1                                                              |
| `cooking_time` | Float        | (cooking) **SECONDS** to cook. Decimals allowed. Default: 10s smelting, 5s blasting, 5s smoking, 30s campfire |
| `template`     | String       | (smithing_transform) Template item ID                                                                         |
| `base`         | String       | (smithing_transform) Base item ID to upgrade                                                                  |
| `addition`     | String       | (smithing_transform) Upgrade material item ID                                                                 |
| `json`         | Object       | (passthrough) Raw recipe body, copied verbatim                                                                |
| `requires`     | String[]     | (passthrough) Extra mod IDs beyond the one deduced from the inner type                                        |
| `result_count` | Int          | Number of items produced. Default: 1. Applies to shaped, shapeless and stonecutting                           |

> 💥 **`cooking_time` changed in 1.6.0.** It used to be in ticks. Divide your
> existing values by 20 — a recipe written as `200` meant 10 seconds and now
> means 200 seconds. Nothing errors, the recipe is just 20x slower.

Declaring a field the type ignores (like `experience` on a stonecutting
recipe) logs a warning naming it. The recipe still works; the value just does
nothing.
