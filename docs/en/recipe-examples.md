# Recipe Examples

Ready-to-adapt `recipe_patch` files that use other mods' machines: Create,
Mekanism and Botania. Each one makes an item of yours, of another mod, or of
vanilla with a method that belongs to a different mod. [Recipes](recipes.md)
explains every field; this page only shows them at work.

> ⚠️ **Follow the format of the mod you are using.** Everything inside a
> `passthrough` recipe's `json` is copied as written and read by **that mod**,
> not by this one: its field names, how it writes ingredients and results, and
> what each field accepts are its own. The examples below were checked in game
> against **Create 6.0.10, Mekanism 10.7.19 and Botania Revived 1.0.3** for
> Minecraft 1.21.1. Another version of those mods may have changed its format;
> a wrong field fails that recipe and the log names the mod that rejected it.

**Finding a mod's format.** The surest source is the mod's own recipes: open
its jar as a zip and look in `data/<mod>/recipe/`. Copy one of the same type
and change the items. `/customgear recipes <item>` lists the recipes that make
an item and their types, which tells you which file to look for.

Every `passthrough` recipe only loads when the mod of its `type` is installed.
When the recipe also names items from another mod, list it in `requires`, so
the recipe is skipped instead of failing when that mod is missing.

## Create

**Mixer.** `ingredients` is a list, one entry per item; `results` is a list
too. `heat_requirement` asks for a Blaze Burner under the basin: `"heated"`,
or `"superheated"`.

```json
{
  "type": "recipe_patch",
  "add": {
    "customgear:ruby_ingot": {
      "type": "passthrough",
      "json": {
        "type": "create:mixing",
        "heat_requirement": "heated",
        "ingredients": [
          { "item": "minecraft:iron_ingot" },
          { "item": "minecraft:redstone" },
          { "item": "minecraft:redstone" }
        ],
        "results": [
          { "count": 1, "id": "customgear:ruby_ingot" }
        ]
      }
    }
  }
}
```

**Crushing wheels**, with a secondary output. `chance` (0 to 1) makes a
result optional. The first result is the main one: it is what
`/customgear recipes` and `remove_output` see.

```json
{
  "type": "recipe_patch",
  "add": {
    "mekanism:dust_osmium": {
      "type": "passthrough",
      "requires": ["mekanism"],
      "json": {
        "type": "create:crushing",
        "ingredients": [{ "item": "customgear:ruby_ore" }],
        "processing_time": 250,
        "results": [
          { "count": 2, "id": "mekanism:dust_osmium" },
          { "chance": 0.5, "count": 1, "id": "create:experience_nugget" }
        ]
      }
    }
  }
}
```

## Mekanism

Mekanism names its inputs and outputs one by one (`input`, `output`), and every
ingredient carries a `count`.

**Enrichment Chamber**, one input:

```json
{
  "type": "recipe_patch",
  "add": {
    "create:zinc_ingot": {
      "type": "passthrough",
      "requires": ["create"],
      "json": {
        "type": "mekanism:enriching",
        "input": { "count": 1, "item": "create:raw_zinc" },
        "output": { "count": 2, "id": "create:zinc_ingot" }
      }
    }
  }
}
```

**Combiner**, two inputs (`main_input` and `extra_input`):

```json
{
  "type": "recipe_patch",
  "add": {
    "customgear:ruby_bow": {
      "type": "passthrough",
      "json": {
        "type": "mekanism:combining",
        "main_input": { "count": 1, "item": "minecraft:bow" },
        "extra_input": { "count": 2, "item": "minecraft:diamond" },
        "output": { "count": 1, "id": "customgear:ruby_bow" }
      }
    }
  }
}
```

## Botania

Botania has a different method for almost every machine, and they don't all
write their output the same way — check each type before copying it to
another.

**Mana Pool** (mana infusion). One `input` and the `mana` it costs:

```json
{
  "type": "passthrough",
  "json": {
    "type": "botania:mana_infusion",
    "input": { "item": "minecraft:bow" },
    "mana": 5000,
    "output": { "count": 1, "id": "customgear:ruby_bow" }
  }
}
```

**Runic Altar.** Several `ingredients`, plus `mana`:

```json
{
  "type": "passthrough",
  "json": {
    "type": "botania:runic_altar",
    "ingredients": [
      { "item": "botania:manasteel_ingot" },
      { "item": "botania:manasteel_ingot" },
      { "item": "botania:rune_fire" },
      { "item": "botania:rune_water" }
    ],
    "mana": 10000,
    "output": { "count": 1, "id": "customgear:ruby_pickaxe" }
  }
}
```

**Petal Apothecary.** Ingredients are usually petal tags, and `reagent` is what
you throw in last (seeds, in Botania's own recipes):

```json
{
  "type": "passthrough",
  "requires": ["mekanism"],
  "json": {
    "type": "botania:petal_apothecary",
    "ingredients": [
      { "tag": "botania:petals/gray" },
      { "tag": "botania:petals/gray" },
      { "item": "minecraft:iron_ingot" }
    ],
    "output": { "count": 1, "id": "mekanism:ingot_osmium" },
    "reagent": { "tag": "botania:seed_apothecary_reagent" }
  }
}
```

**Portal to Alfheim** (elven trade). Here `output` is a **list**:

```json
{
  "type": "passthrough",
  "json": {
    "type": "botania:elven_trade",
    "ingredients": [{ "item": "customgear:ruby_ore" }],
    "output": [{ "count": 1, "id": "botania:dragonstone" }]
  }
}
```

**Terrestrial Agglomeration Plate.** This one writes `result`, not `output`:

```json
{
  "type": "passthrough",
  "requires": ["mekanism"],
  "json": {
    "type": "botania:terra_plate",
    "ingredients": [
      { "item": "botania:manasteel_ingot" },
      { "item": "customgear:ruby_ore" },
      { "item": "mekanism:ingot_osmium" }
    ],
    "mana": 100000,
    "result": { "count": 1, "id": "customgear:ruby_backpack" }
  }
}
```

**Pure Daisy.** It converts **blocks placed in the world**, not items: `input`
and `output` name blocks, and the output uses `name`. Only blocks work here —
your `block` type, for example.

```json
{
  "type": "passthrough",
  "json": {
    "type": "botania:pure_daisy",
    "input": { "type": "block", "block": "minecraft:cobblestone" },
    "output": { "name": "customgear:ruby_ore" }
  }
}
```

The Botania bodies above go in `add` exactly like the Create and Mekanism ones,
under the item they make.

## Taking recipes away from every mod at once

`remove_output` with `recipe_types` catches recipes of any mod without naming
them. This makes gold impossible to smelt — vanilla's ores, Create's crushed
gold and Mekanism's gold dust alike — while crafting from a block and every
other type stay:

```json
{
  "type": "recipe_patch",
  "comment": "Gold only comes from my own method",
  "remove_output": ["minecraft:gold_ingot"],
  "recipe_types": ["smelting", "blasting"]
}
```

A secondary output is never matched: removing `create:experience_nugget` from
`create:crushing` removes nothing, because in Create's crushing the nugget is
always the bonus, never the main output.

## The same recipe on your own item

Every example also works in the `recipe` field of an item of yours. Only the
place changes: the body is identical.

```json
{
  "id": "ruby_gem",
  "type": "item",
  "recipe": {
    "type": "passthrough",
    "json": {
      "type": "mekanism:combining",
      "main_input": { "count": 1, "item": "minecraft:emerald" },
      "extra_input": { "count": 4, "item": "minecraft:redstone" },
      "output": { "count": 1, "id": "customgear:ruby_gem" }
    }
  }
}
```

On your own item, use the item's `recipe` field. Use a `recipe_patch` for
items that aren't yours, to take recipes away, or for recipes you want to ship
as an optional extra file.
