# Recetas

UltimateCustomGear soporta recetas de crafteo nativas definidas directamente en JSON. No se requieren mods externos.

## Tipos de receta

| Tipo                 | Descripción                                        |
|----------------------|----------------------------------------------------|
| `shaped`             | Mesa de crafteo con un patrón específico           |
| `shapeless`          | Mesa de crafteo, ingredientes en cualquier orden   |
| `smelting`           | Horno                                              |
| `blasting`           | Alto horno                                         |
| `smoking`            | Ahumador                                           |
| `campfire_cooking`   | Fogata                                             |
| `stonecutting`       | Cortapiedras                                       |
| `smithing_transform` | Mesa de herrería (plantilla + base + adición)      |
| `passthrough`        | Tipo de receta de otro mod, copiado tal cual       |

## Tags como ingredientes

Cualquier casilla de ingrediente acepta un **tag** con el prefijo `#` — la
receta aceptará cualquier ítem de ese tag:

```json
{
  "key": {
    "P": "#minecraft:planks",
    "I": "#c:ingots/iron",
    "S": "minecraft:stick"
  }
}
```

Funciona en todos los tipos de receta que este mod construye. Dentro de `passthrough` los tags se escriben como los espera el mod objetivo (normalmente `{"tag": "c:ingots/gold"}`), no con el atajo `#` — ese cuerpo se copia tal cual y nunca se traduce. Las recetas con errores estructurales (filas de patrones desiguales, símbolos sin definir, claves sin usar, ID mal formados) se omiten con un mensaje detallado en el log que nombra el ítem y el problema exacto.

**Ingredientes de otros mods.** Una receta que usa un ítem de otro mod como
ingrediente — por ejemplo `"mekanism:ingot_osmium"` — solo se carga si ese mod
está instalado. Sin él, la receta se omite en silencio en lugar de dejar un
error en el log, así el mismo contenido funciona en modpacks con y sin el mod.
No hay que escribir nada: se deduce de los ingredientes. Los tags no lo
necesitan, porque un tag vacío no es un error.

## Receta de ítem individual

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
      "G": "mimod:mi_gema",
      "S": "minecraft:stick"
    }
  }
}
```

Para múltiples recetas, usa un array:

```json
{
  "recipe": [
    {
      "type": "shaped",
      ...
    },
    {
      "type": "smelting",
      "ingredient": "mimod:mi_mineral",
      "experience": 1.0,
      "cooking_time": 10
    }
  ]
}
```

## Receta de set (armadura, herramientas, armas)

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
        "G": "mimod:mi_gema"
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
        "G": "mimod:mi_gema"
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
        "G": "mimod:mi_gema"
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
        "G": "mimod:mi_gema"
      }
    }
  }
}
```

## Receta de mesa de herrería

```json
{
  "recipe": {
    "type": "smithing_transform",
    "template": "minecraft:netherite_upgrade_smithing_template",
    "base": "mimod:mi_espada_diamante",
    "addition": "minecraft:netherite_ingot"
  }
}
```

> Los ingredientes soportan cualquier ítem de cualquier mod instalado mediante resource location (ej. `"otromod:lingote_especial"`).

## Recetas passthrough (otros mods)

`passthrough` entrega un cuerpo de receta crudo al juego sin que este mod lo
interprete, para que el tipo de receta de otro mod pueda producir tu contenido:

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
        { "count": 1, "id": "customgear:mi_gema" }
      ]
    }
  }
}
```

Todo lo que hay dentro de `json` se copia exactamente como está escrito,
incluida la forma de expresar los tags — `{"tag": "c:ingots/gold"}` en el caso
de Create, no el atajo `#` de este mod. Escríbelo como lo documente ese mod;
este mod no traduce ni valida el esquema, porque hacerlo significaría
conocerlo.

Que un campo concreto acepte tags o no lo decide ese mod, no este. La mayoría
usa el sistema `Ingredient` de vanilla y acepta tags en cualquier sitio donde
vaya un ítem, pero un campo que espere un ID literal lo rechazará — y el error
vendrá de su deserializador, no de aquí.

La condición `neoforge:mod_loaded` se deduce del namespace del `type` interno,
así que la receta se omite cuando ese mod no está en vez de romper el
datapack. Para recetas que abarquen varios mods, lista los extras:

```json
{
  "type": "passthrough",
  "requires": ["create", "createaddition"],
  "json": { "type": "create:mixing", "...": "..." }
}
```

**El resultado no es de este mod.** Todos los demás tipos construyen el
resultado a partir del ítem dueño de la receta; aquí el resultado vive dentro
de tu `json`, con la forma que use ese mod. Una receta passthrough colgada de
`ruby_gem` puede producir cualquier otra cosa.

> ⚠️ `mod_loaded` protege contra que el mod **falte**, no contra que sea otra
> **versión**. Los mods cambian sus esquemas de receta entre versiones, y un
> deserializador que falle puede tumbar la carga del datapack entera — no solo
> esa receta. Revisa tus recetas passthrough cuando actualices el mod objetivo.

Hay ejemplos que funcionan con máquinas de Create, Mekanism y Botania en
[Ejemplos de recetas](recipe-examples.md).

## Cambiar otras recetas (`recipe_patch`)

Todas las recetas anteriores pertenecen a un ítem tuyo. Un `recipe_patch` es
un archivo propio que trabaja sobre **cualquier** ítem y cualquier receta —
vanilla, Create, Mekanism, cualquier mod — en tres modos: **añadir**,
**quitar** y **reemplazar**. Un mismo archivo puede usar cualquiera de ellos.

Cada receta dentro de un patch se escribe exactamente igual que el campo
`recipe` de un ítem: `shaped`, `smelting`… o `passthrough` para el tipo de
receta de otro mod.

### Añadir

`add` da recetas a ítems que no son tuyos. La clave es el ítem que fabrica la
receta; el valor, una receta o un array:

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

Una receta para el ítem de otro mod solo se carga si ese mod está instalado, y
una receta passthrough solo si lo está el mod de su `type`. En una receta
passthrough el resultado es el que va dentro de `json`; la clave solo le da
nombre.

Las recetas añadidas reciben el ID `customgear:patch/<namespace>/<ítem>_recipe`
(`_recipe_2`, `_recipe_3`… cuando hay varias).

### Quitar

```json
{
  "type": "recipe_patch",
  "remove": ["minecraft:gold_ingot_from_nuggets"],
  "remove_output": ["minecraft:golden_apple"],
  "recipe_types": ["minecraft:crafting"]
}
```

- `remove` recibe IDs de receta.
- `remove_output` recibe IDs de ítem y quita toda receta cuya salida
  **principal** sea ese ítem.
- `recipe_types` limita `remove_output` a esos tipos — aquí se va la receta de
  crafteo de la manzana dorada y cualquier otra se queda. Sin él, todos los
  tipos. Un tipo sin namespace es de vanilla: `"smelting"` es
  `"minecraft:smelting"`. Los de otros mods necesitan el suyo: `"create:crushing"`.

**Salida principal y secundarias.** `remove_output` solo coincide con la salida
principal — lo que la receta fabrica primero. El triturado de mineral de oro de
Create también da experience nuggets, y el aserradero de Mekanism da serrín
junto a los tablones. Quitar una receta la quita **entera**, así que coincidir
con esas salidas secundarias borraría el triturado de oro solo para quitar los
nuggets. Para quitar una de esas recetas, usa su ID en `remove`.

`remove_output` nunca quita recetas añadidas por un `recipe_patch`, así que
"quita todas las recetas de X y añade la mía" funciona en un solo archivo.

### Reemplazar

`replace` cambia una receta por otra **con el mismo ID**, así que los
desbloqueos del libro de recetas, los logros y cualquier otra cosa que nombre
la receta siguen funcionando. La clave es el ID de receta:

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

La receta nueva fabrica el mismo ítem que la original, y la misma cantidad,
salvo que digas otra cosa: `result` cambia el ítem y `result_count` la
cantidad. Un reemplazo `passthrough` lleva su propio resultado en `json`.

Solo se reemplaza una receta que existe. Si el ID no está cargado — su mod no
está instalado — no se añade nada en su lugar. Si la receta nueva no es
válida, se queda la original y el log dice por qué.

### Encontrar los IDs

`/customgear recipes <ítem>` (o con el ítem en la mano) lista todas las recetas
que lo fabrican, agrupadas por tipo, con las salidas secundarias aparte en
amarillo. Haz clic en un ID de receta o en un tipo para copiarlo. Lee las
recetas en vivo, con los patches ya aplicados, así que también te dice si un
patch funcionó.

Un ID de un mod que no está instalado se ignora en silencio, así que un mismo
patch sirve para varios modpacks. Cuando el mod **sí** está cargado y el ID no
existe, se registra un aviso en el log: es una errata.

| Campo           | Tipo     | Descripción                                                                                         |
|-----------------|----------|-----------------------------------------------------------------------------------------------------|
| `add`           | Map      | ID de ítem → receta o array de recetas                                                              |
| `remove`        | String[] | IDs de receta a quitar                                                                              |
| `remove_output` | String[] | IDs de ítem: se quita toda receta cuya salida **principal** sea ese ítem                            |
| `recipe_types`  | String[] | Limita `remove_output` a estos tipos de receta, p. ej. `["minecraft:crafting"]`. Por defecto: todos |
| `replace`       | Map      | ID de receta → receta nueva. Acepta `result` además de los campos de receta habituales              |
| `comment`       | String   | Nunca se lee. Un patch no tiene nombre, así que sin esto el archivo no dice por qué existe          |

Hace falta al menos uno de `add`, `remove`, `remove_output` o `replace`. Varios
patches se suman.

> ⚠️ Algunas "recetas" no son recetas. Las mesas que funcionan por código — la
> mesa de inscripción, la forja de pergaminos y el yunque arcano de Iron's
> Spellbooks — y las recetas que un mod solo dibuja en JEI no están en la lista
> de recetas, así que ni el comando ni un patch pueden verlas. Lo mismo pasa
> con las máquinas que producen fluidos o químicos en vez de ítems (la lavadora
> química de Mekanism, por ejemplo): `remove_output` recibe ítems, pero
> `remove` y `replace` por ID sí funcionan con ellas.

Consulta [Ejemplos de recetas](recipe-examples.md) para ver patches completos
que usan tipos de receta de Create, Mekanism y Botania.

## Campos de receta

| Campo          | Tipo         | Descripción                                                                                                         |
|----------------|--------------|---------------------------------------------------------------------------------------------------------------------|
| `recipe`       | Objeto/Array | Receta para ítems individuales. Puede ser un objeto o un array para varias recetas                                  |
| `recipes`      | Mapa         | Recetas para sets. Una entrada por pieza/herramienta/arma                                                           |
| `type`         | String       | Ver la tabla de [tipos de receta](#tipos-de-receta)                                                                 |
| `pattern`      | String[]     | (shaped) 1–3 filas de hasta 3 caracteres cada una                                                                   |
| `key`          | Mapa         | (shaped) Asocia cada carácter del patrón a un ID de ítem                                                            |
| `ingredients`  | String[]     | (shapeless) Lista de IDs de ítem                                                                                    |
| `ingredient`   | String       | (cocción/stonecutting) ID del ítem de entrada                                                                       |
| `experience`   | Float        | (cocción) XP otorgada al completarse. Por defecto: 0.1                                                              |
| `cooking_time` | Float        | (cocción) **SEGUNDOS** de cocción. Admite decimales. Por defecto: 10s horno, 5s alto horno, 5s ahumador, 30s fogata |
| `template`     | String       | (smithing_transform) ID de la plantilla                                                                             |
| `base`         | String       | (smithing_transform) ID del ítem base a mejorar                                                                     |
| `addition`     | String       | (smithing_transform) ID del material de mejora                                                                      |
| `json`         | Objeto       | (passthrough) Cuerpo de receta crudo, copiado tal cual                                                              |
| `requires`     | String[]     | (passthrough) IDs de mods extra además del deducido del tipo interno                                                |
| `result_count` | Int          | Cantidad de ítems producidos. Por defecto: 1. Aplica a shaped, shapeless y stonecutting                             |

> 💥 **`cooking_time` cambió en la 1.6.0.** Antes estaba en ticks. Divide tus
> valores actuales entre 20 — una receta escrita como `200` significaba 10
> segundos y ahora significa 200 segundos. No falla nada, la receta simplemente
> va 20 veces más lenta.

Declarar un campo que el tipo ignora (como `experience` en una receta de
stonecutting) genera un aviso en el log nombrándolo. La receta sigue
funcionando; el valor simplemente no hace nada.
