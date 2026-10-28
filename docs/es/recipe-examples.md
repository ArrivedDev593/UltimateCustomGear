# Ejemplos de recetas

Archivos `recipe_patch` listos para adaptar que usan máquinas de otros mods:
Applied Energistics 2, Powah y Ender IO. Cada uno fabrica un ítem tuyo, de otro
mod o de vanilla con un método que pertenece a un mod distinto.
[Recetas](recipes.md) explica cada campo; esta página solo los muestra
funcionando.

> ⚠️ **Sigue el formato del mod que vayas a usar.** Todo lo que va dentro del
> `json` de una receta `passthrough` se copia tal cual y lo lee **ese mod**, no
> este: los nombres de los campos, cómo escribe ingredientes y resultados y qué
> acepta cada campo son suyos. Los ejemplos de abajo siguen las recetas que
> traen **Applied Energistics 2 26.1.13, Powah 7.0.4 y Ender IO 9.0.6** para
> Minecraft 26.1.2. Otra versión de esos mods puede haber cambiado su formato;
> un campo equivocado hace fallar esa receta y el log nombra el mod que la
> rechazó.

> 🧭 **¿Vienes de 1.21.1?** Desde 1.21.2, vanilla escribe un ingrediente como
> texto — `"minecraft:iron_ingot"`, `"#c:ingots/gold"` — en lugar de
> `{"item": ...}` / `{"tag": ...}`, y la mayoría de mods lo siguen. Un cuerpo
> passthrough copiado de un pack de 1.21.1 suele necesitar al menos ese cambio.
> La versión 1.21.1 de esta página tiene ejemplos de Create, Mekanism y
> Botania, que no tienen versión para 26.x.

**Cómo encontrar el formato de un mod.** La fuente más segura son las recetas
del propio mod: abre su jar como un zip y mira en `data/<mod>/recipe/`. Copia
una del mismo tipo y cambia los ítems. `/customgear recipes <ítem>` lista las
recetas que fabrican un ítem y sus tipos, y así sabes qué archivo buscar.

Toda receta `passthrough` solo se carga si el mod de su `type` está instalado.
Si la receta además nombra ítems de otro mod, ponlo en `requires`, así la
receta se omite en vez de fallar cuando falta ese mod.

## Applied Energistics 2

**Inscriptor.** `ingredients` es un objeto con hasta tres huecos: `top`,
`middle` y `bottom`. Con `"mode": "inscribe"` los ítems de arriba y abajo son
prensas y se quedan en la máquina; con `"mode": "press"` también se gastan.

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

**Orbe energizante.** `ingredients` es una lista, una entrada por ítem colocado
en las varas; repite una entrada para pedir dos. `energy` es la FE que cuesta la
receta.

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

Ender IO envuelve cada entrada en un objeto con su `count`, y llama a sus
resultados `output` (uno) u `outputs` (varios).

**Fundidora de aleaciones.** Hasta tres `inputs`. `energy` (la FE que cuesta)
y `experience` son obligatorios — Ender IO rechaza la receta sin ellos:

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

**Molino SAG**, con salidas secundarias. Aquí `energy` también es
obligatorio. Cada entrada de `outputs` lleva su ítem dentro de `item`; `chance`
(de 0 a 1) la hace opcional. La primera salida
es la principal: es la que ven `/customgear recipes` y `remove_output`.

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

## Quitar recetas de todos los mods a la vez

`remove_output` con `recipe_types` atrapa recetas de cualquier mod sin
nombrarlas. Esto hace imposible fundir oro en un horno o un alto horno, desde
cualquier mineral, oro en bruto o polvo que tenga una receta así, y deja el
crafteo desde un bloque y todos los demás tipos:

```json
{
  "type": "recipe_patch",
  "comment": "El oro solo sale de mi propio método",
  "remove_output": ["minecraft:gold_ingot"],
  "recipe_types": ["smelting", "blasting"]
}
```

Algunos mods copian cada receta de horno a una máquina propia, con su propio
tipo: la fundidora de aleaciones de Ender IO lo hace, así que tras el parche de
arriba sigue fundiendo oro. Añade ese tipo a la lista para cerrarla también —
`["smelting", "blasting", "enderio:alloy_smelting"]`. `/customgear recipes
minecraft:gold_ingot` muestra qué tipos quedan.

Una salida secundaria nunca coincide: quitar `minecraft:cobblestone` de
`enderio:sag_milling` no quita nada, porque en el molino SAG el cobblestone es
siempre un extra, nunca la salida principal.

## La misma receta en tu propio ítem

Todos los ejemplos funcionan también en el campo `recipe` de un ítem tuyo. Solo
cambia el sitio: el cuerpo es idéntico.

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

En tu propio ítem, usa su campo `recipe`. Usa un `recipe_patch` para ítems que
no son tuyos, para quitar recetas o para recetas que quieras distribuir como un
archivo extra opcional.
