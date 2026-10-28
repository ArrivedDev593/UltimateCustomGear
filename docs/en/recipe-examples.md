# Recipe Examples

Ready-to-adapt `recipe_patch` files that use other mods' machines: Applied
Energistics 2, Powah and Ender IO. Each one makes an item of yours, of another
mod, or of vanilla with a method that belongs to a different mod.
[Recipes](recipes.md) explains every field; this page only shows them at work.

> ⚠️ **Follow the format of the mod you are using.** Everything inside a
> `passthrough` recipe's `json` is copied as written and read by **that mod**,
> not by this one: its field names, how it writes ingredients and results, and
> what each field accepts are its own. The examples below follow the recipes
> inside **Applied Energistics 2 26.1.13, Powah 7.0.4 and Ender IO 9.0.6** for
> Minecraft 26.1.2. Another version of those mods may have changed its format;
> a wrong field fails that recipe and the log names the mod that rejected it.

> 🧭 **Coming from 1.21.1?** Since 1.21.2, vanilla writes an ingredient as a
> plain string — `"minecraft:iron_ingot"`, `"#c:ingots/gold"` — instead of
> `{"item": ...}` / `{"tag": ...}`, and most mods follow it. A passthrough body
> copied from a 1.21.1 pack usually needs that change at least. The 1.21.1
> version of this page has examples for Create, Mekanism and Botania, which
> have no 26.x version.

**Finding a mod's format.** The surest source is the mod's own recipes: open
its jar as a zip and look in `data/<mod>/recipe/`. Copy one of the same type
and change the items. `/customgear recipes <item>` lists the recipes that make
an item and their types, which tells you which file to look for.

Every `passthrough` recipe only loads when the mod of its `type` is installed.
When the recipe also names items from another mod, list it in `requires`, so
the recipe is skipped instead of failing when that mod is missing.

## Applied Energistics 2

**Inscriber.** `ingredients` is an object with up to three slots: `top`,
`middle` and `bottom`. With `"mode": "inscribe"` the top and bottom items are
presses and stay in the machine; with `"mode": "press"` they are used up too.

```json
{
  "type": "recipe_patch",
  "add": {
    "customgear:ruby_gem": {
      "type": "passthrough",
      "json": {
        "type": "ae2:inscriber",
        "mode": "inscribe",
        "ingredients": {
          "top": "ae2:engineering_processor_press",
          "middle": "minecraft:emerald"
        },
        "result": { "id": "customgear:ruby_gem" }
      }
    }
  }
}
```

## Powah

**Energizing Orb.** `ingredients` is a list, one entry per item placed on the
rods; repeat an entry to ask for two. `energy` is the FE the recipe costs.

```json
{
  "type": "recipe_patch",
  "add": {
    "minecraft:netherite_scrap": {
      "type": "passthrough",
      "json": {
        "type": "powah:energizing",
        "energy": 500000,
        "ingredients": [
          "customgear:ruby_ingot",
          "customgear:ruby_ingot",
          "minecraft:ancient_debris"
        ],
        "result": { "count": 2, "id": "minecraft:netherite_scrap" }
      }
    }
  }
}
```

## Ender IO

Ender IO wraps each input in an object with its `count`, and names its results
`output` (one) or `outputs` (several).

**Alloy Smelter.** Up to three `inputs`. `energy` (the FE it costs) and
`experience` are required — Ender IO rejects the recipe without them:

```json
{
  "type": "recipe_patch",
  "add": {
    "powah:steel_energized": {
      "type": "passthrough",
      "requires": ["powah"],
      "json": {
        "type": "enderio:alloy_smelting",
        "energy": 4800,
        "experience": 0.3,
        "inputs": [
          { "count": 1, "ingredient": "customgear:ruby_ingot" },
          { "count": 2, "ingredient": "#c:ingots/iron" }
        ],
        "output": { "count": 2, "id": "powah:steel_energized" }
      }
    }
  }
}
```

**SAG Mill**, with secondary outputs. `energy` is required here too. Each
entry of `outputs` holds its item under `item`; `chance` (0 to 1) makes it
optional. The first output is the
main one: it is what `/customgear recipes` and `remove_output` see.

```json
{
  "type": "recipe_patch",
  "add": {
    "customgear:ruby_gem": {
      "type": "passthrough",
      "json": {
        "type": "enderio:sag_milling",
        "energy": 2400,
        "input": "customgear:ruby_ore",
        "outputs": [
          { "item": { "count": 2, "id": "customgear:ruby_gem" } },
          { "chance": 0.25, "item": { "id": "customgear:ruby_gem" } },
          { "chance": 0.15, "item": { "id": "minecraft:cobblestone" } }
        ]
      }
    }
  }
}
```

## Taking recipes away from every mod at once

`remove_output` with `recipe_types` catches recipes of any mod without naming
them. This makes gold impossible to smelt in a furnace or blast furnace, from
any ore, raw gold or dust that has such a recipe, while crafting from a block
and every other type stay:

```json
{
  "type": "recipe_patch",
  "comment": "Gold only comes from my own method",
  "remove_output": ["minecraft:gold_ingot"],
  "recipe_types": ["smelting", "blasting"]
}
```

Some mods copy every furnace recipe into a machine of their own, under their
own type: Ender IO's Alloy Smelter does, so after the patch above it still
smelts gold. Add that type to the list to close it too —
`["smelting", "blasting", "enderio:alloy_smelting"]`. `/customgear recipes
minecraft:gold_ingot` shows which types are left.

A secondary output is never matched: removing `minecraft:cobblestone` from
`enderio:sag_milling` removes nothing, because in the SAG Mill the cobblestone
is always a bonus, never the main output.

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
      "type": "powah:energizing",
      "energy": 20000,
      "ingredients": ["minecraft:emerald", "minecraft:redstone_block"],
      "result": { "count": 1, "id": "customgear:ruby_gem" }
    }
  }
}
```

On your own item, use the item's `recipe` field. Use a `recipe_patch` for
items that aren't yours, to take recipes away, or for recipes you want to ship
as an optional extra file.
