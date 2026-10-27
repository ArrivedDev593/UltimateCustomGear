# UltimateCustomGear 1.8.0

*NeoForge 1.21.1 · 2026-10-26*

> **Change any recipe, from any mod.** Packs written for 1.7.x load unchanged — but NeoForge 21.1.248 is now required.

## ⚠️ Important Notes

- **NeoForge 21.1.248 or newer is required**, up from 21.1.235. Update NeoForge before updating this mod, or the game will refuse to start
- **With JEI installed, JEI 19.70.0.460 or newer is required.** JEI is still optional; without it nothing changes
- If your content has two files with the same id, the multiplayer content check now gives a different result than in 1.7.x. Update the server and every client together. Content without duplicate ids checks exactly as before

## ✨ New Features

### Recipe patches

- New content type `recipe_patch`: changes recipes of **any** item — vanilla's, another mod's or yours — in four ways, any of them in one file:
    - **`add`** — gives an item new recipes, whether it had any or not, in the same format as an item's `recipe` field
    - **`remove`** — removes recipes by id
    - **`remove_output`** — removes every recipe that makes an item, from any mod, without knowing their ids. **`recipe_types`** narrows it: `["smelting", "blasting"]` stops gold from being smelted by vanilla, Create and Mekanism alike, and leaves crafting alone
    - **`replace`** — swaps a recipe for a new one **under the same id**, so the recipe book and advancements keep working. It keeps the original's output and count unless you change them
- `remove_output` only matches an item made as a recipe's **main** output. A recipe that gives it as a bonus — Create's crushing and its experience nuggets — is never removed by it, since removing a recipe removes all of it
- Works with other mods' machines through `passthrough`: Create, Mekanism, Botania and any mod that loads its recipes from data
- Applies while the game runs: add, edit or delete a patch and run `/customgear reload`

### Finding recipes

- New command **`/customgear recipes [item]`**: lists every recipe that makes an item, grouped by type, with the id and the type a patch needs. Click either to copy it
- Without an item, it uses the one in your hand
- Recipes where the item is only a secondary output are listed apart, with a reminder that removing one removes the whole recipe
- Open to every player, not just operators: it only reads

### Recipes

- A recipe that uses another mod's item as an ingredient now only loads when that mod is installed. Without it, the recipe is skipped quietly instead of logging an error, so the same content works in modpacks with and without the mod
- New documentation page, **Recipe Examples**, with complete patches for Create, Mekanism and Botania machines

## 🐛 Bug Fixes

- **The content check let a player join with different stats when a duplicate id was involved.** The check compares every JSON regardless of where it lives, but which of two files with the same id is loaded depends on file names and packs. A client and a server with the same files arranged differently passed the check and loaded different versions of the item
- **With a duplicate id, a Windows client and a Linux server could keep different files.** Content files and pack zips were read in an order that depends on the operating system. They are now read in the same order everywhere
- **The reload cooldown message was always in English.** It now follows the game language like the rest of the command's messages

## 🔧 Technical Changes

- `RecipePatchData.java`, `RecipePatchLoader.java`, `RecipePatchHandler.java` — new. Additions are written to the dynamic pack; removals and replacements run on the server's loaded recipes after every datapack load, before they are sent to players
- `RecipeQueries.java`, `RecipesCommand.java` — new. One lookup decides what makes an item for both the command and `remove_output`: the result a recipe reports, then the output fields of its JSON for recipes that don't report one or have several outputs
- `RecipeLoader.java` — a friendly-type recipe naming another mod's item as an ingredient gets a `mod_loaded` condition on that mod
- `GearParser.java`, `UniversalParser.java` — content files are read in a fixed order and the directory stream is closed, so with a duplicate id client and server keep the same file on any OS
- `ContentRoots.java` — packs are mounted in an order that doesn't depend on the OS
- `ContentHasher.java`, `ContentRoots.java` — files that lost a duplicate id, in the parsers or in `GlobalIdValidator`, are left out of the content hash
- `CustomGearMod.java` — the content hash is captured after the global id validation
- `CustomGearNetworking.java` — documented when `PROTOCOL_VERSION` must change; it stays at `"3"`
- `CustomContainerScreen.java` — key and mouse transfer bindings go through separate methods instead of one with placeholder values
- `neoforge.mods.toml` — removed an unused `modproperties` block; the config screen is registered in code and works as before

## ⚙️ Known Limitations

- Some "recipes" are not recipes: tables that work in code, like Iron's Spellbooks' scroll forge and arcane anvil, and recipes a mod only draws in JEI are not in the recipe list. Neither the command nor a patch can see them
- `remove_output` takes items. Machines that make fluids or chemicals instead can still be removed or replaced by id

## 📦 Dependencies

- **NeoForge** — 21.1.248 or newer, up from 21.1.235
- **JEI** — optional; when installed, 19.70.0.460 or newer

---

<details>
<summary><b>🇪🇸 Leer en español</b></summary>

> **Cambia cualquier receta, de cualquier mod.** Los packs escritos para 1.7.x cargan sin cambios — pero ahora se requiere NeoForge 21.1.248.

## ⚠️ Notas Importantes

- **Se requiere NeoForge 21.1.248 o más nuevo**, antes 21.1.235. Actualiza NeoForge antes que este mod, o el juego no arrancará
- **Con JEI instalado, se requiere JEI 19.70.0.460 o más nuevo.** JEI sigue siendo opcional; sin él no cambia nada
- Si tu contenido tiene dos archivos con el mismo id, la verificación de contenido en multijugador da ahora un resultado distinto al de la 1.7.x. Actualiza el servidor y todos los clientes a la vez. El contenido sin ids duplicados se verifica exactamente igual que antes

## ✨ Nuevas Características

### Parches de recetas

- Nuevo tipo de contenido `recipe_patch`: cambia recetas de **cualquier** ítem — de vanilla, de otro mod o tuyo — de cuatro formas, cualquiera de ellas en un mismo archivo:
    - **`add`** — da recetas nuevas a un ítem, tuviera o no, con el mismo formato que el campo `recipe` de un ítem
    - **`remove`** — quita recetas por id
    - **`remove_output`** — quita toda receta que fabrica un ítem, de cualquier mod, sin conocer sus ids. **`recipe_types`** lo acota: `["smelting", "blasting"]` impide fundir oro en vanilla, Create y Mekanism por igual, y deja el crafteo en paz
    - **`replace`** — cambia una receta por otra **con el mismo id**, así el libro de recetas y los logros siguen funcionando. Conserva la salida y la cantidad de la original salvo que las cambies
- `remove_output` solo coincide con un ítem que es la salida **principal** de una receta. Una receta que lo da como extra — el triturado de Create y sus pepitas de experiencia — nunca se quita así, porque quitar una receta la quita entera
- Funciona con máquinas de otros mods mediante `passthrough`: Create, Mekanism, Botania y cualquier mod que cargue sus recetas desde datos
- Se aplica con el juego abierto: añade, edita o borra un parche y ejecuta `/customgear reload`

### Encontrar recetas

- Nuevo comando **`/customgear recipes [ítem]`**: lista toda receta que fabrica un ítem, agrupada por tipo, con el id y el tipo que necesita un parche. Haz clic en cualquiera para copiarlo
- Sin ítem, usa el que tienes en la mano
- Las recetas donde el ítem es solo una salida secundaria aparecen aparte, con un aviso de que quitar una quita la receta entera
- Abierto a todos los jugadores, no solo a operadores: solo lee

### Recetas

- Una receta que usa un ítem de otro mod como ingrediente ahora solo se carga si ese mod está instalado. Sin él, la receta se omite en silencio en lugar de dejar un error en el log, así el mismo contenido funciona en modpacks con y sin el mod
- Nueva página de documentación, **Ejemplos de recetas**, con parches completos para máquinas de Create, Mekanism y Botania

## 🐛 Correcciones

- **La verificación de contenido dejaba entrar a un jugador con stats distintas cuando había un id duplicado.** La verificación compara todos los JSON sin importar dónde estén, pero cuál de dos archivos con el mismo id se carga depende de los nombres de archivo y los packs. Un cliente y un servidor con los mismos archivos organizados distinto pasaban la verificación y cargaban versiones distintas del ítem
- **Con un id duplicado, un cliente Windows y un servidor Linux podían quedarse con archivos distintos.** Los archivos de contenido y los zips de packs se leían en un orden que depende del sistema operativo. Ahora se leen en el mismo orden en todas partes
- **El mensaje de espera de la recarga salía siempre en inglés.** Ahora sigue el idioma del juego como el resto de mensajes del comando

## 🔧 Cambios Técnicos

- `RecipePatchData.java`, `RecipePatchLoader.java`, `RecipePatchHandler.java` — nuevos. Las adiciones se escriben en el pack dinámico; las eliminaciones y reemplazos se aplican sobre las recetas cargadas del servidor tras cada carga de datapacks, antes de enviarlas a los jugadores
- `RecipeQueries.java`, `RecipesCommand.java` — nuevos. Una sola búsqueda decide qué fabrica un ítem para el comando y para `remove_output`: el resultado que informa la receta, y luego los campos de salida de su JSON para las recetas que no informan uno o tienen varias salidas
- `RecipeLoader.java` — una receta de tipo propio que nombra un ítem de otro mod como ingrediente recibe una condición `mod_loaded` de ese mod
- `GearParser.java`, `UniversalParser.java` — los archivos de contenido se leen en un orden fijo y el stream del directorio se cierra, así con un id duplicado cliente y servidor se quedan con el mismo archivo en cualquier sistema
- `ContentRoots.java` — los packs se montan en un orden que no depende del sistema operativo
- `ContentHasher.java`, `ContentRoots.java` — los archivos que perdieron un id duplicado, en los parsers o en `GlobalIdValidator`, quedan fuera del hash de contenido
- `CustomGearMod.java` — el hash de contenido se captura después de la validación global de ids
- `CustomGearNetworking.java` — documentado cuándo debe cambiar `PROTOCOL_VERSION`; se queda en `"3"`
- `CustomContainerScreen.java` — las teclas y botones de transferencia pasan por métodos separados en vez de uno con valores de relleno
- `neoforge.mods.toml` — quitado un bloque `modproperties` que no se usaba; la pantalla de configuración se registra en el código y funciona igual que antes

## ⚙️ Limitaciones Conocidas

- Algunas "recetas" no son recetas: las mesas que funcionan por código, como la forja de pergaminos y el yunque arcano de Iron's Spellbooks, y las recetas que un mod solo dibuja en JEI no están en la lista de recetas. Ni el comando ni un parche pueden verlas
- `remove_output` recibe ítems. Las máquinas que producen fluidos o químicos en su lugar se pueden quitar o reemplazar por id

## 📦 Dependencias

- **NeoForge** — 21.1.248 o más nuevo, antes 21.1.235
- **JEI** — opcional; si está instalado, 19.70.0.460 o más nuevo

</details>
