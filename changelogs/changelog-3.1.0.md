# UltimateCustomGear 3.1.0

*NeoForge 26.2 · 2026-10-28*

> **Change any recipe, from any mod — and your own recipes load again.** Packs written for 3.0.x load unchanged.

## ⚠️ Important Notes

- **Recipes defined in an item's `recipe` field load again.** From 3.0.0 to 3.0.1 none of them did (see Bug Fixes). If you worked around it with a datapack or a `passthrough` copy of the same recipe, remove the copy, or the item will have the recipe twice
- If your content has two files with the same id, the multiplayer content check now gives a different result than in 3.0.x. Update the server and every client together. Content without duplicate ids checks exactly as before

## ✨ New Features

### Recipe patches

- New content type `recipe_patch`: changes recipes of **any** item — vanilla's, another mod's or yours — in four ways, any of them in one file:
    - **`add`** — gives an item new recipes, whether it had any or not, in the same format as an item's `recipe` field
    - **`remove`** — removes recipes by id
    - **`remove_output`** — removes every recipe that makes an item, from any mod, without knowing their ids. **`recipe_types`** narrows it: `["smelting", "blasting"]` stops gold from being smelted in every furnace-like recipe and leaves crafting alone
    - **`replace`** — swaps a recipe for a new one **under the same id**, so the recipe book and advancements keep working. It keeps the original's output and count unless you change them
- `remove_output` only matches an item made as a recipe's **main** output. A recipe that gives it as a bonus is never removed by it, since removing a recipe removes all of it
- Works with other mods' machines through `passthrough`, for any mod that loads its recipes from data
- Applies while the game runs: add, edit or delete a patch and run `/customgear reload`

### Finding recipes

- New command **`/customgear recipes [item]`**: lists every recipe that makes an item, grouped by type, with the id and the type a patch needs. Click either to copy it
- Without an item, it uses the one in your hand
- Recipes where the item is only a secondary output are listed apart, with a reminder that removing one removes the whole recipe
- Open to every player, not just operators: it only reads

### Recipes

- A recipe that uses another mod's item as an ingredient now only loads when that mod is installed. Without it, the recipe is skipped quietly instead of logging an error, so the same content works in modpacks with and without the mod
- New documentation page, **Recipe Examples**, with test patches for Applied Energistics 2, Powah and Ender IO machines. Those mods have no 26.2 version yet: the examples come from 26.1.2 and show how a patch is put together; follow the format of the mod you use

## 🐛 Bug Fixes

- **No recipe from an item's `recipe` field ever loaded.** Since 1.21.2 the game writes an ingredient as plain text — `"minecraft:diamond"`, `"#minecraft:planks"` — and the mod kept building them in the 1.21.1 form, `{"item": ...}`. Every `shaped`, `shapeless`, cooking, `stonecutting` and `smithing_transform` recipe was rejected at load with a `Couldn't parse data file` error in the log. `passthrough` recipes were not affected
- **The content check let a player join with different stats when a duplicate id was involved.** The check compares every JSON regardless of where it lives, but which of two files with the same id is loaded depends on file names and packs. A client and a server with the same files arranged differently passed the check and loaded different versions of the item
- **With a duplicate id, a Windows client and a Linux server could keep different files.** Content files and pack zips were read in an order that depends on the operating system. They are now read in the same order everywhere
- **The reload cooldown message was always in English.** It now follows the game language like the rest of the command's messages

## 📝 Documentation

- The `passthrough` examples in **Recipes** used Create's 1.21.1 format, with `{"item": ...}` ingredients; Create has no 26.x version and that form doesn't parse on 26.x. They now use Ender IO recipes copied from its own files

## 🔧 Technical Changes

- `RecipeLoader.java` — ingredients are written as plain strings, with `#` for tags. A friendly-type recipe naming another mod's item as an ingredient gets a `mod_loaded` condition on that mod
- `RecipePatchData.java`, `RecipePatchLoader.java`, `RecipePatchHandler.java` — new. Additions are written to the dynamic pack; removals and replacements run on the server's loaded recipes in `TagsUpdatedEvent.ServerDataLoad`, right before the game builds the recipe book and sends recipes to players
- `accesstransformer.cfg` — `RecipeManager.recipes` made public: 26.x has no `replaceRecipes`, so the patched recipe map is set directly
- `RecipeQueries.java`, `RecipesCommand.java` — new. One lookup decides what makes an item for both the command and `remove_output`: the result of the recipe's first recipe-book display, then the output fields of its JSON for recipes that show none or have several outputs
- `GearParser.java`, `UniversalParser.java` — content files are read in a fixed order and the directory stream is closed, so with a duplicate id client and server keep the same file on any OS
- `ContentRoots.java` — packs are mounted in an order that doesn't depend on the OS
- `ContentHasher.java`, `ContentRoots.java` — files that lost a duplicate id, in the parsers or in `GlobalIdValidator`, are left out of the content hash
- `CustomGearMod.java` — the content hash is captured after the global id validation
- `CustomGearNetworking.java` — documented when `PROTOCOL_VERSION` must change; it stays at `"3"`, so 3.1.0 still connects with 3.0.x
- `CustomContainerScreen.java` — key and mouse transfer bindings go through separate methods instead of one taking a nullable event of each kind
- `neoforge.mods.toml` — removed an unused `modproperties` block; the config screen is registered in code and works as before

## ⚙️ Known Limitations

- Some "recipes" are not recipes: tables that work in code and recipes a mod only draws in JEI are not in the recipe list. Neither the command nor a patch can see them
- `remove_output` takes items. Machines that make fluids or chemicals instead can still be removed or replaced by id
- A machine recipe with several item outputs that shows nothing in the recipe book has no main output, so `remove_output` leaves it alone. Remove it by id; `/customgear recipes` lists it under secondary outputs

## 📦 Dependencies

- **Minecraft 26.2** · **NeoForge 26.2** · **Java 25**
- **JEI 30.25.0.177** — optional, recommended
- **GeckoLib 5.5.3** — optional, required only for 3D armor models
- **Curios 16.0.0** — optional, only needed for `curios_slots`

---

<details>
<summary><b>🇪🇸 Leer en español</b></summary>

> **Cambia cualquier receta, de cualquier mod — y tus propias recetas vuelven a cargar.** Los packs escritos para 3.0.x cargan sin cambios.

## ⚠️ Notas Importantes

- **Las recetas definidas en el campo `recipe` de un ítem vuelven a cargar.** De la 3.0.0 a la 3.0.1 no cargaba ninguna (ver Correcciones). Si lo sorteaste con un datapack o con una copia `passthrough` de la misma receta, quita la copia o el ítem tendrá la receta dos veces
- Si tu contenido tiene dos archivos con el mismo id, la verificación de contenido en multijugador da ahora un resultado distinto al de la 3.0.x. Actualiza el servidor y todos los clientes a la vez. El contenido sin ids duplicados se verifica exactamente igual que antes

## ✨ Nuevas Características

### Parches de recetas

- Nuevo tipo de contenido `recipe_patch`: cambia recetas de **cualquier** ítem — de vanilla, de otro mod o tuyo — de cuatro formas, cualquiera de ellas en un mismo archivo:
    - **`add`** — da recetas nuevas a un ítem, tuviera o no, con el mismo formato que el campo `recipe` de un ítem
    - **`remove`** — quita recetas por id
    - **`remove_output`** — quita toda receta que fabrica un ítem, de cualquier mod, sin conocer sus ids. **`recipe_types`** lo acota: `["smelting", "blasting"]` impide fundir oro en cualquier receta de tipo horno y deja el crafteo en paz
    - **`replace`** — cambia una receta por otra **con el mismo id**, así el libro de recetas y los logros siguen funcionando. Conserva la salida y la cantidad de la original salvo que las cambies
- `remove_output` solo coincide con un ítem que es la salida **principal** de una receta. Una receta que lo da como extra nunca se quita así, porque quitar una receta la quita entera
- Funciona con máquinas de otros mods mediante `passthrough`, con cualquier mod que cargue sus recetas desde datos
- Se aplica con el juego abierto: añade, edita o borra un parche y ejecuta `/customgear reload`

### Encontrar recetas

- Nuevo comando **`/customgear recipes [ítem]`**: lista toda receta que fabrica un ítem, agrupada por tipo, con el id y el tipo que necesita un parche. Haz clic en cualquiera para copiarlo
- Sin ítem, usa el que tienes en la mano
- Las recetas donde el ítem es solo una salida secundaria aparecen aparte, con un aviso de que quitar una quita la receta entera
- Abierto a todos los jugadores, no solo a operadores: solo lee

### Recetas

- Una receta que usa un ítem de otro mod como ingrediente ahora solo se carga si ese mod está instalado. Sin él, la receta se omite en silencio en lugar de dejar un error en el log, así el mismo contenido funciona en modpacks con y sin el mod
- Nueva página de documentación, **Ejemplos de recetas**, con parches de prueba para máquinas de Applied Energistics 2, Powah y Ender IO. Esos mods aún no tienen versión para 26.2: los ejemplos salen de 26.1.2 y muestran cómo se arma un parche; sigue el formato del mod que uses

## 🐛 Correcciones

- **Ninguna receta del campo `recipe` de un ítem llegaba a cargar.** Desde 1.21.2 el juego escribe un ingrediente como texto — `"minecraft:diamond"`, `"#minecraft:planks"` — y el mod los seguía generando con la forma de 1.21.1, `{"item": ...}`. Toda receta `shaped`, `shapeless`, de cocción, `stonecutting` y `smithing_transform` se rechazaba al cargar con un error `Couldn't parse data file` en el log. Las recetas `passthrough` no se veían afectadas
- **La verificación de contenido dejaba entrar a un jugador con stats distintas cuando había un id duplicado.** La verificación compara todos los JSON sin importar dónde estén, pero cuál de dos archivos con el mismo id se carga depende de los nombres de archivo y los packs. Un cliente y un servidor con los mismos archivos organizados distinto pasaban la verificación y cargaban versiones distintas del ítem
- **Con un id duplicado, un cliente Windows y un servidor Linux podían quedarse con archivos distintos.** Los archivos de contenido y los zips de packs se leían en un orden que depende del sistema operativo. Ahora se leen en el mismo orden en todas partes
- **El mensaje de espera de la recarga salía siempre en inglés.** Ahora sigue el idioma del juego como el resto de mensajes del comando

## 📝 Documentación

- Los ejemplos `passthrough` de **Recetas** usaban el formato de Create de 1.21.1, con ingredientes `{"item": ...}`; Create no tiene versión para 26.x y esa forma no se lee en 26.x. Ahora usan recetas de Ender IO copiadas de sus propios archivos

## 🔧 Cambios Técnicos

- `RecipeLoader.java` — los ingredientes se escriben como texto, con `#` para los tags. Una receta de tipo propio que nombra un ítem de otro mod como ingrediente recibe una condición `mod_loaded` de ese mod
- `RecipePatchData.java`, `RecipePatchLoader.java`, `RecipePatchHandler.java` — nuevos. Las adiciones se escriben en el pack dinámico; las eliminaciones y reemplazos se aplican sobre las recetas cargadas del servidor en `TagsUpdatedEvent.ServerDataLoad`, justo antes de que el juego arme el libro de recetas y las envíe a los jugadores
- `accesstransformer.cfg` — `RecipeManager.recipes` pasa a ser público: 26.x no tiene `replaceRecipes`, así que el mapa de recetas parcheado se asigna directamente
- `RecipeQueries.java`, `RecipesCommand.java` — nuevos. Una sola búsqueda decide qué fabrica un ítem para el comando y para `remove_output`: el resultado de la primera vista de la receta en el libro de recetas, y luego los campos de salida de su JSON para las recetas que no muestran ninguna o tienen varias salidas
- `GearParser.java`, `UniversalParser.java` — los archivos de contenido se leen en un orden fijo y el stream del directorio se cierra, así con un id duplicado cliente y servidor se quedan con el mismo archivo en cualquier sistema
- `ContentRoots.java` — los packs se montan en un orden que no depende del sistema operativo
- `ContentHasher.java`, `ContentRoots.java` — los archivos que perdieron un id duplicado, en los parsers o en `GlobalIdValidator`, quedan fuera del hash de contenido
- `CustomGearMod.java` — el hash de contenido se captura después de la validación global de ids
- `CustomGearNetworking.java` — documentado cuándo debe cambiar `PROTOCOL_VERSION`; se queda en `"3"`, así que 3.1.0 sigue conectando con 3.0.x
- `CustomContainerScreen.java` — las teclas y botones de transferencia pasan por métodos separados en vez de uno que recibía un evento de cada tipo, uno de ellos nulo
- `neoforge.mods.toml` — quitado un bloque `modproperties` que no se usaba; la pantalla de configuración se registra en el código y funciona igual que antes

## ⚙️ Limitaciones Conocidas

- Algunas "recetas" no son recetas: las mesas que funcionan por código y las recetas que un mod solo dibuja en JEI no están en la lista de recetas. Ni el comando ni un parche pueden verlas
- `remove_output` recibe ítems. Las máquinas que producen fluidos o químicos en su lugar se pueden quitar o reemplazar por id
- Una receta de máquina con varias salidas de ítem que no muestra nada en el libro de recetas no tiene salida principal, así que `remove_output` no la toca. Quítala por id; `/customgear recipes` la lista entre las salidas secundarias

## 📦 Dependencias

- **Minecraft 26.2** · **NeoForge 26.2** · **Java 25**
- **JEI 30.25.0.177** — opcional, recomendada
- **GeckoLib 5.5.3** — opcional, solo necesaria para modelos de armadura 3D
- **Curios 16.0.0** — opcional, solo necesaria para `curios_slots`

</details>
