# Ejemplos de recetas

Archivos `recipe_patch` listos para adaptar que usan máquinas de otros mods:
Create, Mekanism y Botania. Cada uno fabrica un ítem tuyo, de otro mod o de
vanilla con un método que pertenece a un mod distinto. [Recetas](recipes.md)
explica cada campo; esta página solo los muestra funcionando.

> ⚠️ **Sigue el formato del mod que vayas a usar.** Todo lo que va dentro del
> `json` de una receta `passthrough` se copia tal cual y lo lee **ese mod**, no
> este: los nombres de los campos, cómo escribe ingredientes y resultados y qué
> acepta cada campo son suyos. Los ejemplos de abajo se comprobaron en el juego
> con **Create 6.0.10, Mekanism 10.7.19 y Botania Revived 1.0.3** para
> Minecraft 1.21.1. Otra versión de esos mods puede haber cambiado su formato;
> un campo equivocado hace fallar esa receta y el log nombra el mod que la
> rechazó.

**Cómo encontrar el formato de un mod.** La fuente más segura son las recetas
del propio mod: abre su jar como un zip y mira en `data/<mod>/recipe/`. Copia
una del mismo tipo y cambia los ítems. `/customgear recipes <ítem>` lista las
recetas que fabrican un ítem y sus tipos, y así sabes qué archivo buscar.

Toda receta `passthrough` solo se carga si el mod de su `type` está instalado.
Cuando la receta además nombra ítems de otro mod, ponlo en `requires`, así la
receta se omite en lugar de fallar cuando ese mod falta.

## Create

**Mezcladora.** `ingredients` es una lista, una entrada por ítem; `results`
también es una lista. `heat_requirement` pide un quemador de blaze bajo la
cuenca: `"heated"` o `"superheated"`.

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

**Ruedas trituradoras**, con una salida secundaria. `chance` (de 0 a 1) hace
que un resultado sea opcional. El primer resultado es el principal: es el que
ven `/customgear recipes` y `remove_output`.

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

Mekanism nombra sus entradas y salidas una por una (`input`, `output`), y cada
ingrediente lleva un `count`.

**Cámara de enriquecimiento**, una entrada:

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

**Combinador**, dos entradas (`main_input` y `extra_input`):

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

Botania tiene un método distinto para casi cada máquina, y no todos escriben
su salida igual — revisa cada tipo antes de copiarlo a otro.

**Reserva de maná** (infusión de maná). Una `input` y el `mana` que cuesta:

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

**Altar rúnico.** Varios `ingredients`, más `mana`:

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

**Boticario de pétalos.** Los ingredientes suelen ser tags de pétalos, y
`reagent` es lo que se echa al final (semillas, en las recetas de Botania):

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

**Portal a Alfheim** (intercambio élfico). Aquí `output` es una **lista**:

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

**Placa de aglomeración terrestre.** Esta escribe `result`, no `output`:

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

**Margarita pura.** Convierte **bloques colocados en el mundo**, no ítems:
`input` y `output` nombran bloques, y la salida usa `name`. Aquí solo sirven
bloques — tu tipo `block`, por ejemplo.

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

Los cuerpos de Botania de arriba van en `add` igual que los de Create y
Mekanism, bajo el ítem que fabrican.

## Quitar recetas de todos los mods a la vez

`remove_output` con `recipe_types` atrapa recetas de cualquier mod sin
nombrarlas. Esto hace imposible fundir oro — las menas de vanilla, el oro
triturado de Create y el polvo de oro de Mekanism por igual — mientras el
crafteo desde el bloque y cualquier otro tipo se quedan:

```json
{
  "type": "recipe_patch",
  "comment": "El oro solo sale de mi método propio",
  "remove_output": ["minecraft:gold_ingot"],
  "recipe_types": ["smelting", "blasting"]
}
```

Una salida secundaria nunca coincide: quitar `create:experience_nugget` de
`create:crushing` no quita nada, porque en el triturado de Create la pepita es
siempre el extra, nunca la salida principal.

## La misma receta en tu propio ítem

Cada ejemplo funciona también en el campo `recipe` de un ítem tuyo. Solo cambia
el lugar: el cuerpo es idéntico.

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

En tu propio ítem, usa su campo `recipe`. Usa un `recipe_patch` para ítems que
no son tuyos, para quitar recetas, o para recetas que quieras distribuir como
un archivo extra opcional.
