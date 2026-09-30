# UltimateCustomGear 1.7.2

*NeoForge 1.21.1 · 2026-09-29*

> **One container fix.** Nothing else changed, and nothing you wrote needs editing.

## ⚠️ Important Notes

- A chest that was left alone after its partner broke, in 1.7.1, may still believe it is part of a pair. Break it and place it again to reset it

## 🐛 Bug Fixes

- **A chest left alone after its partner was broken did not pair again.** Breaking one half of a double chest left the surviving main half still marked as merged, so when a new chest was placed beside it and it became the main half again, the merge was skipped: the window showed a single chest's slots and the new half's items were unreachable until it was broken. In 1.7.0 the mark was lost on the next world load, which hid the problem; 1.7.1 started saving it, and from then on it stayed

## 🔧 Technical Changes

- `CustomChestBlock.java` — splitting a pair clears the `joined` flag on the chest that remains, whichever half was broken

## 📦 Dependencies

- No changes

---

<details>
<summary><b>🇪🇸 Leer en español</b></summary>

> **Una corrección de contenedores.** Nada más cambió, y nada de lo que escribiste necesita editarse.

## ⚠️ Notas Importantes

- Un cofre que se quedó solo tras romper su pareja, en la 1.7.1, puede seguir creyéndose parte de una pareja. Rómpelo y colócalo de nuevo para reiniciarlo

## 🐛 Correcciones

- **Un cofre que se quedaba solo tras romper su pareja no volvía a emparejarse.** Romper una mitad de un cofre doble dejaba a la mitad principal superviviente marcada como unida, así que cuando se colocaba un cofre nuevo a su lado y volvía a ser la mitad principal, la unión se saltaba: la ventana mostraba los slots de un cofre sencillo y los ítems de la mitad nueva quedaban inaccesibles hasta romperla. En la 1.7.0 la marca se perdía en la siguiente carga del mundo, lo que escondía el problema; la 1.7.1 empezó a guardarla, y desde entonces se quedaba

## 🔧 Cambios Técnicos

- `CustomChestBlock.java` — repartir una pareja limpia el flag `joined` en el cofre que queda, sea cual sea la mitad que se rompió

## 📦 Dependencias

- Sin cambios

</details>
