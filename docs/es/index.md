# UltimateCustomGear

**UltimateCustomGear** es un mod de NeoForge para Minecraft 1.21.1 basado en datos, que permite a administradores de servidores, creadores de modpacks y jugadores agregar sets de armadura, armas, herramientas, comida, ítems, bloques y fluidos completamente personalizados — todo mediante simples archivos JSON. No se requiere programar.

## Documentación

| Página                                        | Qué contiene                                                           |
|-----------------------------------------------|------------------------------------------------------------------------|
| [Primeros pasos](getting-started.md)          | Instalación, dónde van los archivos, tipos de contenido                |
| [Campos comunes](common-fields.md)            | Campos que comparten todos los tipos, objeto de efecto, patrones de ID |
| [Sets de armadura](armor-sets.md)             | Stats por pieza, efectos, bonos de conjunto, adornos                   |
| [Armas](weapons.md)                           | Espadas, arcos, ballestas, escudos                                     |
| [Herramientas](tools.md)                      | Picos, hachas, palas, azadones, niveles de cosecha, arado en área      |
| [Comida](food.md)                             | Nutrición, velocidad al comer, efectos al consumir                     |
| [Bloques](blocks.md)                          | Texturas por cara, direccionales, gravedad, requisitos de minado       |
| [Fluidos](fluids.md)                          | Color, propagación, efectos al contacto, quemado                       |
| [Contenedores](containers.md)                 | Barriles, cofres, shulkers y mochilas                                  |
| [Texturas y modelos](textures-and-models.md)  | Los tres modos, capas de armadura, modelos 3D con GeckoLib             |
| [Recetas](recipes.md)                         | Todos los tipos de receta, tags como ingredientes, passthrough         |
| [Ejemplos de recetas](recipe-examples.md)     | Patches con máquinas de Create, Mekanism y Botania                     |
| [Tags](tags.md)                               | Pertenecer a tags, tags automáticos del equipamiento, parches          |
| [Drops de mobs](mob-drops.md)                 | Probabilidad, cantidad, filtros de entidad, Saqueo                     |
| [Resistencias de daño](damage-resistances.md) | Reducción por tipo de daño, por atacante, o ambos                      |
| [Packs de contenido](content-packs.md)        | Distribuir tu contenido como zip                                       |
| [Multijugador](multiplayer.md)                | Verificación de contenido y modos de cumplimiento                      |
| [Comandos](commands.md)                       | `/customgear reload`, y qué requiere reiniciar                         |
| [Migración](migration.md)                     | Cambios incompatibles, versión por versión                             |

**Listas de referencia** — [Tipos de sonido de bloque](block-sounds.md) · [Colores de mapa](map-colors.md) · [Tipos de daño](damage-types.md)

¿Vienes llegando? [Primeros pasos](getting-started.md) te lleva por tu primer ítem.

## Características

### Creación de Contenido

- **Sets de armadura** con stats por pieza (durabilidad, defensa, dureza, resistencia al retroceso)
- **Armas** — espadas, arcos, ballestas y escudos con daño, durabilidad y velocidad de carga personalizados
- **Herramientas** — picos, hachas, palas y azadones con velocidad de minado, niveles de cosecha y arado en área
- **Comida** con nutrición, saturación, velocidad al comer y efectos al consumir
- **Bloques** — incluyendo direccionales y con gravedad, con texturas por cara, luz, sonidos y requisitos de minado
- **Fluidos** con colores personalizados, efectos de contacto y comportamiento de fuego

### Efectos y Gameplay

- Efectos al sostener, efectos por pieza y bonos de conjunto completo
- **Drops de mobs** — cualquier ítem puede caer de los mobs con probabilidad, cantidad y filtros de entidad configurables (recargable en caliente: balancea tu economía en vivo)
- Ítems `fire_resistant` que sobreviven al fuego y la lava, como la netherita — funciona en ítems, comida, gear, bloques y cubetas
- **Armadura transparente** — armadura con stats y efectos completos que no se dibuja sobre el cuerpo
- **Resistencias de daño** — tres capas de reducción de daño por tipo de daño, por atacante, o ambos combinados (recargable en caliente)

### Texturas y Modelos

- Referencia texturas de vanilla o de otros mods, o usa tus propios archivos PNG
- Texturas por cara en bloques, animaciones de tensado de arcos, capas de armadura personalizadas
- **Modelos de armadura 3D** con GeckoLib — usa modelos de Blockbench con animaciones opcionales en lugar de capas planas (dependencia opcional: sin ella la armadura cae a las capas 2D)

### Recetas y Tags

- **Sistema nativo de recetas** — shaped, shapeless, smelting, blasting y smithing, definidas en JSON
- Los ingredientes de recetas aceptan **tags** (`"#minecraft:planks"` = cualquier tabla de madera, incluyendo ítems de otros mods)
- El contenido puede **pertenecer a tags** mediante el campo `tags`, para que tus ítems funcionen en recetas de otros mods

### Servidor y Multijugador

- **Packs de contenido** — distribuye todo como un solo `.zip` que los jugadores colocan en `packs/`
- **Verificación de contenido en multijugador** — el servidor comprueba al conectar que los clientes tengan los mismos archivos, con cumplimiento configurable
- `/customgear reload` — actualiza nombres, efectos, recetas y drops sin reiniciar

### Calidad de Vida

- Soporte completo multi-idioma por ítem
- Tooltips informativos (efectos, bonos de conjunto, niveles de cosecha, drops de mobs)
- Errores de validación claros que nombran el archivo y el problema exacto

## Compatibilidad

- Minecraft 1.21.1
- NeoForge 21.1.x
- JEI (opcional, recomendado) — las recetas son completamente visibles
- GeckoLib (opcional) — requerido solo para modelos de armadura en 3D
- Los encantamientos de otros mods funcionan automáticamente en ítems encantables
- Los modelos de cualquier mod instalado pueden referenciarse con el modo `reference`
- Los ingredientes de cualquier mod instalado pueden usarse en recetas
- Multijugador: el servidor y los clientes deben usar la misma versión del mod y archivos de contenido coincidentes (verificado automáticamente al conectar)
