# UltimateCustomGear 2.0.2

*NeoForge 26.1.2 · 2026-09-29*

> **Double chests and container drops.** Three container fixes the 2.0.1 release did not reach, plus one documentation correction.

## ⚠️ Important Notes

**Your content files load unchanged.** Nothing here touches a JSON field.

- A chest that was left alone after its partner broke, in 2.0.1 or earlier, may still believe it is part of a pair. Break it and place it again, or place a second chest beside it and break that one, to reset it

## 🐛 Bug Fixes

- **Breaking one half of a double chest dropped the wrong half.** Breaking the main half spilled the whole pair's contents and left the other half empty; breaking the other half dropped nothing and left the survivor holding both halves. The split that hands each half its own items ran after the game had already removed the inventory, so it never found anything to split
- **A container with `keeps_contents` still spilled its contents on explosions, pistons and `/setblock`.** 2.0.1 fixed the drop for a player breaking it, and only for that. The drop now runs on every kind of removal, while the inventory still exists, exactly as it did in 1.7.0
- **Placing a copied chest next to another sometimes did not form a pair.** A chest copied with Ctrl+pick remembered that it had already been merged with its old partner, so depending on the side it was placed on, the new pair skipped the merge: the window showed a single chest and the other half's items were unreachable until it was broken
- **A chest left alone after its partner was broken never paired again.** It kept believing it was merged, so the next chest placed beside it hit the same problem as above

## 📝 Documentation

- The summary table in **Textures and Models** still gave `othermod:geo/armor/their_armor.geo.json` as the way to reference another mod's GeckoLib model, contradicting the section below it. The form that resolves is `othermod:armor/their_armor`

## 🔧 Technical Changes

- **Container removal moved to `BlockEntity.preRemoveSideEffects`**, the hook that replaced `Block.onRemove`. `affectNeighborsAfterRemoval` runs after the block entity is gone, and `playerWillDestroy` only runs for players; this is the one place that sees every removal with the inventory still there
- `CustomContainerBlock.onBlockEntityRemoved` — new: the drop logic, called from the block entity. `CustomChestBlock` overrides it to split a pair before anything drops
- The creative-break flag lives on the block entity instead of being read in `playerWillDestroy`
- `removeComponentsFromTag` discards `Joined` along with `Contents`, and splitting a pair resets it on the chest that remains

## 📦 Dependencies

- **Minecraft 26.1.2** · **NeoForge 26.1.2** · **Java 25**
- **JEI 29.29.0.77** — optional, recommended
- **GeckoLib 5.5.2** — optional, required only for 3D armor models
- **Curios 15.0.0** — optional, only needed for `curios_slots`

---

<details>
<summary><b>🇪🇸 Leer en español</b></summary>

> **Cofres dobles y drops de contenedores.** Tres correcciones de contenedores que la 2.0.1 no alcanzó, más una corrección de documentación.

## ⚠️ Notas Importantes

**Tus archivos de contenido cargan sin cambios.** Nada de esto toca un campo del JSON.

- Un cofre que se quedó solo tras romper su pareja, en la 2.0.1 o antes, puede seguir creyéndose parte de una pareja. Rómpelo y colócalo de nuevo, o coloca un segundo cofre a su lado y rompe ese, para reiniciarlo

## 🐛 Correcciones

- **Romper una mitad de un cofre doble soltaba la mitad equivocada.** Romper la mitad principal derramaba el contenido de toda la pareja y dejaba vacía la otra mitad; romper la otra no soltaba nada y dejaba a la superviviente con las dos mitades. El reparto que devuelve a cada mitad sus ítems corría después de que el juego ya hubiera quitado el inventario, así que nunca encontraba nada que repartir
- **Un contenedor con `keeps_contents` seguía derramando su contenido con explosiones, pistones y `/setblock`.** La 2.0.1 arregló el drop cuando lo rompe un jugador, y solo eso. El drop corre ahora en cualquier tipo de eliminación, con el inventario todavía presente, igual que en 1.7.0
- **Colocar un cofre copiado junto a otro a veces no formaba pareja.** Un cofre copiado con Ctrl+clic recordaba que ya estaba unido a su pareja anterior, así que según el lado en que se colocara, la pareja nueva se saltaba la unión: la ventana mostraba un cofre sencillo y los ítems de la otra mitad quedaban inaccesibles hasta romperlo
- **Un cofre que se quedaba solo tras romper su pareja no volvía a emparejarse.** Seguía creyéndose unido, así que el siguiente cofre colocado a su lado caía en el mismo problema de arriba

## 📝 Documentación

- La tabla resumen de **Texturas y modelos** seguía dando `othermod:geo/armor/su_armadura.geo.json` como forma de referenciar el modelo GeckoLib de otro mod, contradiciendo la sección de debajo. La forma que resuelve es `othermod:armor/su_armadura`

## 🔧 Cambios Técnicos

- **La eliminación de contenedores pasó a `BlockEntity.preRemoveSideEffects`**, el hook que sustituyó a `Block.onRemove`. `affectNeighborsAfterRemoval` corre cuando el block entity ya no existe, y `playerWillDestroy` solo corre para jugadores; este es el único sitio que ve cualquier eliminación con el inventario todavía presente
- `CustomContainerBlock.onBlockEntityRemoved` — nuevo: la lógica del drop, llamada desde el block entity. `CustomChestBlock` lo sobrescribe para repartir la pareja antes de que caiga nada
- La marca de rotura en creativo vive en el block entity en vez de leerse en `playerWillDestroy`
- `removeComponentsFromTag` descarta `Joined` junto con `Contents`, y repartir una pareja lo reinicia en el cofre que queda

## 📦 Dependencias

- **Minecraft 26.1.2** · **NeoForge 26.1.2** · **Java 25**
- **JEI 29.29.0.77** — opcional, recomendada
- **GeckoLib 5.5.2** — opcional, solo necesaria para modelos de armadura 3D
- **Curios 15.0.0** — opcional, solo necesaria para `curios_slots`

</details>
