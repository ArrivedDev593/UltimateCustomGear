# UltimateCustomGear 3.0.1

*NeoForge 26.2 · 2026-09-29*

> **Fixes for the 26.2 port.** Eleven things the port broke or brought with it — most of them in containers, and five of them stopped content from working at all.

## ⚠️ Important Notes

**Your content files load unchanged**, references included. Chest and shulker textures accept the folder path as well as the sprite name, `armor_layers` accepts both the old layer texture and the new equipment asset, and `armor_3d` accepts the file path as well as the id GeckoLib caches it under. Where a form changed, both are read.

- **One thing is outside this mod's control: GeckoLib moved its models.** What lived under `geo/` in a 1.21.1 jar is under `geckolib/models/` now, so a reference into another mod's assets needs that segment updated — `"othermod:geckolib/models/armor/their.geo.json"`, or the short `"othermod:armor/their"`. Both work. `texture` is unaffected and keeps its full path with `.png`
- Double chests saved before this version may have their contents split across both halves. Opening one shows the same items, in different slots; nothing was lost

## 🐛 Bug Fixes

### Containers

- **Breaking one half of a double chest dropped the wrong half.** Breaking the main half spilled the whole pair's contents and left the other half empty; breaking the other half dropped nothing and left the survivor holding both halves. The split that hands each half its own items ran after the game had already removed the inventory, so it never found anything to split
- **A container with `keeps_contents` spilled its contents instead of dropping itself** — first on every break, and after a partial fix still on explosions, pistons and `/setblock`. The drop now runs on every kind of removal, while the inventory still exists, exactly as it did in 1.7.0
- **Placing a copied chest next to another sometimes did not form a pair.** A chest copied with Ctrl+pick remembered that it had already been merged with its old partner, so depending on the side it was placed on, the new pair skipped the merge: the window showed a single chest and the other half's items were unreachable until it was broken
- **A chest left alone after its partner was broken never paired again.** It kept believing it was merged, so the next chest placed beside it hit the same problem as above
- **Copying the main half of a double chest with Ctrl+pick duplicated its contents.** The item carried the inventory twice — once as the half this mod writes, once inside the block entity data vanilla attaches — and placing it put one copy inside and dropped the other on the floor
- **A double chest split its contents across both halves on every world load.** The merge that forms a pair did not record that it had already run, so it ran again on an inventory that was already merged
- **An empty backpack could be placed inside another backpack.** Nesting is refused for anything that carries an inventory, but the check only ever looked at block-backed containers, and a backpack is a plain item
- **The container screen had no text.** Its title, the inventory label and the search box were drawn fully transparent: text colours are now read with their alpha channel, and these had none

### Everything else

- **The game crashed on startup unless every piece of gear declared `enchantability`.** The field is optional, so omitting it is the ordinary case: it arrived as `0`, and both material types now reject a value that is not positive. A material's value only grades the offers — whether gear can be enchanted at all is still the `enchantable` flag
- **Custom blocks and containers showed their raw translation key** instead of their name, and a placed container had no title. An item's key now comes from its own registry entry unless the properties say otherwise, and a `BlockItem` no longer inherits its block's
- **GeckoLib 3D armor did not render.** GeckoLib 5 scans one fixed directory for models and serves them from a cache keyed by a stripped id, so files written anywhere else were never found. Both the location and the id are now what it expects
- **Chest and shulker texture references only resolved in their shortest form.** `"minecraft:entity/chest/christmas"` drew the missing-texture checkerboard, because the trim to a sprite name keyed on a `textures/` segment that this spelling does not have. Every form now trims to the same sprite

## 🔧 Technical Changes

- **Container removal moved to `BlockEntity.preRemoveSideEffects`**, the hook that replaced `Block.onRemove`. `affectNeighborsAfterRemoval` runs after the block entity is gone, and `playerWillDestroy` only runs for players; this is the one place that sees every removal with the inventory still there
- `CustomContainerBlock.onBlockEntityRemoved` — new: the drop logic, called from the block entity. `CustomChestBlock` overrides it to split a pair before anything drops
- The creative-break flag lives on the block entity instead of a position set on the block
- `removeComponentsFromTag` discards `Joined` along with `Contents`
- GeckoLib is `compileOnly` + `localRuntime` instead of `implementation`, so it no longer appears as a runtime dependency in the published POM

## 📦 Dependencies

- **Minecraft 26.2** · **NeoForge 26.2** · **Java 25**
- **JEI 30.25.0.177** — optional, recommended
- **GeckoLib 5.5.3** — optional, required only for 3D armor models
- **Curios 16.0.0** — optional, only needed for `curios_slots`

---

<details>
<summary><b>🇪🇸 Leer en español</b></summary>

> **Correcciones del port a 26.2.** Once cosas que el port rompió o trajo consigo — la mayoría en contenedores, y cinco de ellas impedían que el contenido funcionara.

## ⚠️ Notas Importantes

**Tus archivos de contenido cargan sin cambios**, referencias incluidas. Las texturas de cofre y shulker aceptan tanto la ruta de carpeta como el nombre del sprite, `armor_layers` acepta tanto la textura de capa antigua como el equipment asset nuevo, y `armor_3d` acepta tanto la ruta del fichero como el ID con el que GeckoLib lo cachea. Donde cambió la forma, se leen las dos.

- **Una cosa queda fuera del control de este mod: GeckoLib movió sus modelos.** Lo que en un jar de 1.21.1 vivía en `geo/` está ahora en `geckolib/models/`, así que una referencia a los assets de otro mod necesita ese segmento actualizado — `"othermod:geckolib/models/armor/su.geo.json"`, o el corto `"othermod:armor/su"`. Los dos funcionan. `texture` no cambia y conserva su ruta completa con `.png`
- Los cofres dobles guardados antes de esta versión pueden tener su contenido repartido entre las dos mitades. Al abrirlos verás los mismos ítems en slots distintos; no se perdió nada

## 🐛 Correcciones

### Contenedores

- **Romper una mitad de un cofre doble soltaba la mitad equivocada.** Romper la mitad principal derramaba el contenido de toda la pareja y dejaba vacía la otra mitad; romper la otra no soltaba nada y dejaba a la superviviente con las dos mitades. El reparto que devuelve a cada mitad sus ítems corría después de que el juego ya hubiera quitado el inventario, así que nunca encontraba nada que repartir
- **Un contenedor con `keeps_contents` derramaba su contenido en vez de soltarse a sí mismo** — primero en cualquier rotura, y tras un arreglo parcial todavía con explosiones, pistones y `/setblock`. El drop corre ahora en cualquier tipo de eliminación, con el inventario todavía presente, igual que en 1.7.0
- **Colocar un cofre copiado junto a otro a veces no formaba pareja.** Un cofre copiado con Ctrl+clic recordaba que ya estaba unido a su pareja anterior, así que según el lado en que se colocara, la pareja nueva se saltaba la unión: la ventana mostraba un cofre sencillo y los ítems de la otra mitad quedaban inaccesibles hasta romperlo
- **Un cofre que se quedaba solo tras romper su pareja no volvía a emparejarse.** Seguía creyéndose unido, así que el siguiente cofre colocado a su lado caía en el mismo problema de arriba
- **Copiar la mitad principal de un cofre doble con Ctrl+clic duplicaba su contenido.** El ítem llevaba el inventario dos veces — una como la mitad que escribe este mod, otra dentro de los datos de block entity que adjunta vanilla — y al colocarlo una copia entraba dentro y la otra caía al suelo
- **Un cofre doble repartía su contenido entre las dos mitades en cada carga del mundo.** La unión que forma la pareja no registraba que ya se había hecho, así que volvía a ejecutarse sobre un inventario ya unido
- **Una mochila vacía se podía meter dentro de otra mochila.** El anidamiento se rechaza para cualquier cosa que lleve un inventario, pero la comprobación solo miraba contenedores que son bloque, y una mochila es un ítem normal
- **La pantalla del contenedor no tenía texto.** El título, la etiqueta del inventario y el buscador se dibujaban totalmente transparentes: los colores de texto se leen ahora con su canal alfa, y estos no lo tenían

### Todo lo demás

- **El juego crasheaba al arrancar salvo que todo el equipo declarara `enchantability`.** El campo es opcional, así que omitirlo es lo normal: llegaba como `0`, y los dos tipos de material rechazan ahora un valor que no sea positivo. El valor del material solo gradúa las ofertas — que el equipo se pueda encantar lo sigue decidiendo el flag `enchantable`
- **Los bloques y contenedores personalizados mostraban su clave de traducción en crudo** en vez de su nombre, y un contenedor colocado no tenía título. La clave de un ítem sale ahora de su propia entrada de registro salvo que las propiedades digan otra cosa, y un `BlockItem` ya no hereda la de su bloque
- **La armadura 3D de GeckoLib no se dibujaba.** GeckoLib 5 escanea un directorio fijo para sus modelos y los sirve desde una caché indexada por un id recortado, así que los archivos escritos en otro sitio no se encontraban nunca. La ubicación y el id son ahora los que espera
- **Las referencias de textura de cofre y shulker solo resolvían en su forma más corta.** `"minecraft:entity/chest/christmas"` dibujaba el cuadriculado de textura faltante, porque el recorte a nombre de sprite se apoyaba en un segmento `textures/` que esa forma no tiene. Ahora todas las formas se recortan al mismo sprite

## 🔧 Cambios Técnicos

- **La eliminación de contenedores pasó a `BlockEntity.preRemoveSideEffects`**, el hook que sustituyó a `Block.onRemove`. `affectNeighborsAfterRemoval` corre cuando el block entity ya no existe, y `playerWillDestroy` solo corre para jugadores; este es el único sitio que ve cualquier eliminación con el inventario todavía presente
- `CustomContainerBlock.onBlockEntityRemoved` — nuevo: la lógica del drop, llamada desde el block entity. `CustomChestBlock` lo sobrescribe para repartir la pareja antes de que caiga nada
- La marca de rotura en creativo vive en el block entity en vez de en un conjunto de posiciones en el bloque
- `removeComponentsFromTag` descarta `Joined` junto con `Contents`
- GeckoLib es `compileOnly` + `localRuntime` en vez de `implementation`, así que ya no figura como dependencia de ejecución en el POM publicado

## 📦 Dependencias

- **Minecraft 26.2** · **NeoForge 26.2** · **Java 25**
- **JEI 30.25.0.177** — opcional, recomendada
- **GeckoLib 5.5.3** — opcional, solo necesaria para modelos de armadura 3D
- **Curios 16.0.0** — opcional, solo necesaria para `curios_slots`

</details>
