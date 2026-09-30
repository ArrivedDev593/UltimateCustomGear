# Texturas y Modelos

Un valor de textura dice de dónde sale una imagen, y el mod deduce de qué tipo
es a partir del propio valor:

| Si el valor…                | Significa                                            | Ejemplo                          |
|-----------------------------|------------------------------------------------------|----------------------------------|
| contiene `:`                | Un recurso de vanilla o de otro mod                  | `minecraft:item/netherite_sword` |
| termina en una extensión    | Un archivo de tu carpeta `ultimatecustomgear/`       | `textures/mi_espada.png`         |
| ninguna de las dos          | **Error** — no se adivina nada                       | `item/bundle`                    |

La tercera fila importa. `item/bundle` podría ser un resource location al que le
falta el namespace o un archivo al que le falta la extensión, así que el mod lo
rechaza y te dice las dos formas válidas en vez de elegir una.

> ⚠️ **Apuntar a otro mod crea una dependencia dura.** Ese mod pasa a ser
> **obligatorio** para que tu contenido se vea bien: sin él el objeto muestra el
> cuadriculado de textura perdida, y un modelo de armadura 3D declarado así
> simplemente no se dibuja. Las referencias a vanilla (`minecraft:…`) siempre son
> seguras. Si quieres que tu pack se sostenga solo, incluye los PNG dentro.

## Se pueden mezclar

Cada valor decide por sí mismo, así que un mismo objeto puede tomar unas
imágenes de vanilla y otras de tu carpeta:

```json
{
  "texture": {
    "refs": {
      "top":    "textures/block/mi_barril_top.png",
      "bottom": "minecraft:block/barrel_bottom",
      "side":   "minecraft:block/barrel_side"
    }
  }
}
```

Omitir `texture` por completo le da a cada hueco su textura de reserva.

## A qué apunta cada clave

La misma sintaxis significa cosas distintas según qué se esté texturizando, y
esta es la parte que conviene leer dos veces:

| Contenido                | Una referencia nombra… | Se escribe                                  |
|--------------------------|------------------------|---------------------------------------------|
| Caras de bloque          | una textura            | `minecraft:block/stone`                     |
| Piezas de armadura       | una textura            | `minecraft:item/diamond_helmet`             |
| Capas de armadura        | un equipment asset     | `othermod:su_armadura`                      |
| Objetos y comida         | una textura            | `minecraft:item/apple`                      |
| Fluidos                  | un sprite del atlas    | `minecraft:block/lava_still`                |
| **Herramientas y armas** | **un modelo**          | `minecraft:item/diamond_pickaxe`            |
| Cofres y shulkers        | un sprite del atlas    | `minecraft:christmas`                       |
| Armadura 3D de GeckoLib  | un ID de caché         | `othermod:armor/su_armadura`                |

Las herramientas y armas son la excepción a propósito: heredar un modelo trae
consigo las transformaciones de ese objeto, y por eso un pico referenciado se
sostiene en la mano exactamente igual que aquel al que apuntaste. Escribir ahí
una ruta de textura se rechaza con un mensaje que lo explica.

Todo salvo GeckoLib usa la **forma corta** — sin `textures/`, sin `.png`. El
sistema de modelos añade ambos por su cuenta, así que una ruta completa no
resuelve a nada. Los cofres y shulkers aceptan cualquiera de las dos, porque se
dibujan sin un modelo de por medio.

## Texturas animadas

Pon un `.mcmeta` junto a tu PNG y ambos viajan al pack, igual que en un resource
pack normal:

```
ultimatecustomgear/
  textures/
    mi_fluido.png
    mi_fluido.png.mcmeta
```

Sin el `.mcmeta`, una tira animada se mete entera en una sola celda y sale
irreconocible — que es lo que pasa si copias la textura de lava de vanilla y te
olvidas de sus metadatos.

## Campos de textura

| Campo                | Tipo   | Descripción                                                                                                                                                                                        |
|----------------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `texture.refs`       | Mapa   | Valores de textura por clave. Qué claves son válidas depende del tipo de contenido                                                                                                                 |
| `armor_layers`       | Mapa   | (Solo conjuntos de armadura) `layer_1` y `layer_2` para la textura de la armadura puesta. El valor especial `"transparent"` la hace invisible al vestirla, con sus estadísticas y efectos intactos |
| `texture.refs.all`   | Texto  | (Bloques simples) Una textura para las seis caras                                                                                                                                                  |
| `texture.faces`      | Objeto | (Solo bloques) Configuración por cara. Claves: `top`, `bottom`, `north`, `south`, `east`, `west`, `side`, `top_open`                                                                               |
| `texture.faces.side` | Texto  | Atajo: se aplica a `north`, `south`, `east`, `west` cuando no se declaran individualmente                                                                                                          |

> **`refs` frente a `faces`:** `refs` sirve para todo — una textura única con la
> clave `all`, o texturas por cara con las claves de cara. `faces` es un alias
> heredado que solo funciona para texturas por cara. Usa `refs`. (La clave
> `block` es un alias heredado de `all`.) Ojo: `all`/`block` solo funcionan
> dentro de `refs`, nunca dentro de `faces`.

## Arcos y ballestas

Los fotogramas de tensado son opcionales, y cada uno cae por su cuenta — si
falta uno se usa el de vanilla en vez de congelar la animación:

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

Las ballestas usan `crossbow`, `crossbow_pulling_0` hasta `_2`, más
`crossbow_arrow` y `crossbow_firework` para los estados cargados.

## Modelos de Armadura 3D (GeckoLib)

Con [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) instalado,
una armadura puede dibujarse como un modelo 3D completo al vestirla — cuernos,
hombreras, capas, incluso animaciones — en vez de las capas planas de vanilla.

Modela tu armadura en [Blockbench](https://www.blockbench.net/) con el formato
**GeckoLib Animated Model**, usando los nombres de hueso estándar (`armorHead`,
`armorBody`, `armorRightArm`, `armorLeftArm`, `armorRightLeg`, `armorLeftLeg`,
`armorRightBoot`, `armorLeftBoot`), y declara los archivos exportados en
`texture.armor_3d`:

```json
{
  "texture": {
    "refs": {
      "helmet":     "textures/mi_casco_icono.png",
      "chestplate": "textures/mi_pechera_icono.png",
      "leggings":   "textures/mis_pantalones_icono.png",
      "boots":      "textures/mis_botas_icono.png"
    },
    "armor_layers": {
      "layer_1": "textures/mi_armadura_layer_1.png",
      "layer_2": "textures/mi_armadura_layer_2.png"
    },
    "armor_3d": {
      "model":     "models/mi_armadura.geo.json",
      "texture":   "textures/mi_armadura_3d.png",
      "animation": "models/mi_armadura.animation.json"
    }
  }
}
```

| Campo       | Obligatorio | Descripción                                                                     |
|-------------|-------------|---------------------------------------------------------------------------------|
| `model`     | Sí          | El `.geo.json` exportado de Blockbench                                          |
| `texture`   | Sí          | PNG pintado para el desplegado UV de ese modelo (no el icono, no una capa)      |
| `animation` | No          | `.animation.json`; sin él el modelo es estático                                 |

**Tus propios archivos** se escriben tal como están en tu carpeta, con extensión
y todo — esa extensión es lo que los marca como ficheros y no como referencias.

**Una referencia al modelo o la animación de otro mod** se escribe con el ID con
el que GeckoLib lo cachea, que no es ni la ruta del fichero ni la del pack: sin
el prefijo `geo/` y sin la extensión `.geo.json`. `"othermod:armor/su_armadura"`,
no `"othermod:geo/armor/su_armadura.geo.json"`. El campo `texture` es la
excepción por partida doble — es una ruta de verdad y conserva su `.png`.

Referenciar el modelo de otro mod no copia nada, y ese es el punto: evita
redistribuir recursos que no son tuyos. El precio es que ese mod pasa a ser
obligatorio para que tu armadura se dibuje.

**Cómo se combinan los tres sistemas de textura:**

| Declarado                                  | Con GeckoLib   | Sin GeckoLib             |
|--------------------------------------------|----------------|--------------------------|
| Solo `armor_layers`                        | Capas planas   | Capas planas             |
| `armor_layers` + `armor_3d`                | **Modelo 3D**  | Capas planas (reserva)   |
| Solo `armor_3d`                            | **Modelo 3D**  | Capas de hierro vanilla  |
| `armor_layers: "transparent"` + `armor_3d` | **Modelo 3D**  | Armadura invisible       |

> Declara siempre `armor_layers` junto a `armor_3d` — es tu red de seguridad
> para instancias sin GeckoLib. Declarar solo `armor_3d` no rompe nada, pero la
> armadura cae a las capas de hierro de vanilla, que casi nunca es lo que
> quieres. `refs` sigue controlando el icono del inventario, que siempre es 2D.
> El modelo 3D sustituye a las capas cuando está activo; nunca se dibujan juntos.

GeckoLib es una dependencia opcional: el mod funciona bien sin ella. El dibujado
3D se fija al registrar — añadir o quitar `armor_3d` requiere reiniciar.
