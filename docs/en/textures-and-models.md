# Textures & Models

A texture value says where an image comes from, and the mod works out which kind
it is from the value itself:

| The value…               | Means                                           | Example                          |
|--------------------------|-------------------------------------------------|----------------------------------|
| contains `:`             | An asset from vanilla or another mod            | `minecraft:item/netherite_sword` |
| ends in a file extension | A file inside your `ultimatecustomgear/` folder | `textures/my_sword.png`          |
| neither                  | **Error** — nothing is guessed                  | `item/bundle`                    |

The third row matters. `item/bundle` could be a resource location missing its
namespace or a file missing its extension, so the mod refuses it and tells you
both valid forms instead of picking one.

> ⚠️ **Pointing at another mod creates a hard dependency.** That mod becomes
> **required** for your content to look right: without it the item shows the
> missing-texture checkerboard, and a 3D armor model declared this way simply
> won't render. Vanilla references (`minecraft:…`) are always safe. If you want
> your pack to stand on its own, ship the PNGs inside it.

## Mixing is allowed

Each value decides for itself, so one object can take some images from vanilla
and some from your own folder:

```json
{
  "texture": {
    "refs": {
      "top":    "textures/block/my_barrel_top.png",
      "bottom": "minecraft:block/barrel_bottom",
      "side":   "minecraft:block/barrel_side"
    }
  }
}
```

Leaving `texture` out entirely gives every slot its default placeholder.

## What a key points at

The same syntax means different things depending on what is being textured, and
this is the part worth reading twice:

| Content             | A reference names… | Written as                                  |
|---------------------|--------------------|---------------------------------------------|
| Block faces         | a texture          | `minecraft:block/stone`                     |
| Armor pieces        | a texture          | `minecraft:item/diamond_helmet`             |
| Armor layers        | an equipment asset | `othermod:their_armor`                      |
| Items and food      | a texture          | `minecraft:item/apple`                      |
| Fluids              | an atlas sprite    | `minecraft:block/lava_still`                |
| **Tools & weapons** | **a model**        | `minecraft:item/diamond_pickaxe`            |
| Chests & shulkers   | an atlas sprite    | `minecraft:christmas`                       |
| GeckoLib 3D armor   | a cached id        | `othermod:armor/their_armor`                |

Tools and weapons are the odd one out on purpose: inheriting a model brings that
item's display transforms along, which is why a referenced pickaxe sits in the
hand exactly like the one you pointed at. Writing a texture path there is
rejected with a message saying so.

Everything except GeckoLib takes the **short form** — no `textures/`, no `.png`.
The model system adds both itself, so a full path resolves to nothing. Chests
and shulkers accept either, since they are drawn without a model in the way.

## Animated textures

Put a `.mcmeta` next to your PNG and both travel into the pack, exactly as they
would in an ordinary resource pack:

```
ultimatecustomgear/
  textures/
    my_fluid.png
    my_fluid.png.mcmeta
```

Without the `.mcmeta` an animated strip is stitched into a single cell and comes
out unrecognisable — which is what happens if you copy vanilla's lava texture
and forget its metadata.

## Texture Fields

| Field                | Type   | Description                                                                                                                                                               |
|----------------------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `texture.refs`       | Map    | Texture values by key. Which keys are valid depends on the content type                                                                                                   |
| `armor_layers`       | Map    | (Armor sets only) `layer_1` and `layer_2` for the in-world armor texture. The special value `"transparent"` makes the armor invisible when worn, stats and effects intact |
| `texture.refs.all`   | String | (Simple blocks) One texture for all six faces                                                                                                                             |
| `texture.faces`      | Object | (Blocks only) Per-face configuration. Keys: `top`, `bottom`, `north`, `south`, `east`, `west`, `side`, `top_open`                                                         |
| `texture.faces.side` | String | Shortcut: applies to `north`, `south`, `east`, `west` when they are not declared individually                                                                             |

> **`refs` vs `faces`:** `refs` handles everything — a single texture with the
> `all` key, or per-face textures with the face keys. `faces` is a legacy alias
> that only works for per-face textures. Use `refs`. (The `block` key is a legacy
> alias of `all`.) Note: `all`/`block` only work inside `refs`, never inside
> `faces`.

## Bows and crossbows

The pulling frames are optional, and each one falls back on its own — a missing
frame uses vanilla's rather than freezing the animation:

```json
{
  "texture": {
    "refs": {
      "bow":           "othermod:item/epic_bow",
      "bow_pulling_0": "othermod:item/epic_bow_pulling_0",
      "bow_pulling_1": "othermod:item/epic_bow_pulling_1",
      "bow_pulling_2": "othermod:item/epic_bow_pulling_2"
    }
  }
}
```

Crossbows use `crossbow`, `crossbow_pulling_0` through `_2`, plus
`crossbow_arrow` and `crossbow_firework` for the loaded states.

## 3D Armor Models (GeckoLib)

With [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) installed,
armor can render as a full 3D model when worn — horns, shoulder pads, capes,
even animations — instead of the flat vanilla layers.

Model your armor in [Blockbench](https://www.blockbench.net/) using the
**GeckoLib Animated Model** format, using the standard armor bone names
(`armorHead`, `armorBody`, `armorRightArm`, `armorLeftArm`, `armorRightLeg`,
`armorLeftLeg`, `armorRightBoot`, `armorLeftBoot`), then declare the exported
files in `texture.armor_3d`:

```json
{
  "texture": {
    "refs": {
      "helmet":     "textures/my_helmet_icon.png",
      "chestplate": "textures/my_chestplate_icon.png",
      "leggings":   "textures/my_leggings_icon.png",
      "boots":      "textures/my_boots_icon.png"
    },
    "armor_layers": {
      "layer_1": "textures/my_armor_layer_1.png",
      "layer_2": "textures/my_armor_layer_2.png"
    },
    "armor_3d": {
      "model":     "models/my_armor.geo.json",
      "texture":   "textures/my_armor_3d.png",
      "animation": "models/my_armor.animation.json"
    }
  }
}
```

| Field       | Required | Description                                                             |
|-------------|----------|-------------------------------------------------------------------------|
| `model`     | Yes      | The `.geo.json` exported from Blockbench                                |
| `texture`   | Yes      | PNG painted for that model's UV layout (not the item icon, not a layer) |
| `animation` | No       | `.animation.json`; without it the model is static                       |

**Your own files** are written as they sit in your folder, extension and all —
that extension is what marks them as files rather than references.

**A reference to another mod's model or animation** is written as the id
GeckoLib caches it under, which is neither the file path nor the pack path: no
`geo/` prefix, no `.geo.json` extension. `"othermod:armor/their_armor"`, not
`"othermod:geo/armor/their_armor.geo.json"`. The `texture` field is the
exception on both counts — it is a real path and keeps its `.png`.

Referencing another mod's model copies nothing, which is the point: it avoids
redistributing assets that are not yours. The cost is that the mod becomes
required for your armor to render.

**How the three texture systems combine:**

| Declared                                   | With GeckoLib | Without GeckoLib       |
|--------------------------------------------|---------------|------------------------|
| `armor_layers` only                        | Flat layers   | Flat layers            |
| `armor_layers` + `armor_3d`                | **3D model**  | Flat layers (fallback) |
| `armor_3d` only                            | **3D model**  | Vanilla iron layers    |
| `armor_layers: "transparent"` + `armor_3d` | **3D model**  | Invisible armor        |

> Always declare `armor_layers` alongside `armor_3d` — it is your safety net for
> instances without GeckoLib. Declaring only `armor_3d` will not break anything,
> but the armor falls back to the vanilla iron layers, which is almost never what
> you want. `refs` still controls the inventory icon, which is always 2D. The 3D
> model replaces the layers when active; they are never drawn together.

GeckoLib is an optional dependency: the mod runs fine without it. 3D rendering is
baked at registration — adding or removing `armor_3d` requires a restart.
