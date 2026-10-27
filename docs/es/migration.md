# Migración

Solo cambios incompatibles — cosas que hacen que un JSON que funcionaba en una
versión se comporte distinto o deje de cargar en la siguiente. Todo lo aditivo
está en el [CHANGELOG](../../CHANGELOG_ES.md).

> Esta página está incompleta. Por ahora cubre 1.7.0, 1.6.0, y el cambio de protocolo de
> red de 1.3.0. Los cambios incompatibles anteriores hay que sacarlos del
> CHANGELOG.

## 1.7.0

**No hay nada que cambiar.** Los packs escritos para 1.6.0 cargan sin
modificaciones.

### `texture.mode` ya no hace nada

```diff
  "texture": {
-   "mode": "reference",
    "refs": {
      "sword": "minecraft:item/netherite_sword"
    }
  }
```

Lo que es un valor ahora sale del propio valor: un `:` lo convierte en el recurso
de otro mod, una extensión lo convierte en un archivo de tu carpeta. Un pack que
siga declarando `mode` funciona exactamente igual que antes — el campo se lee y
se ignora — así que borrarlo es limpieza, no un arreglo.

Lo único que cambió de verdad: un pack que declaraba `"mode": "default"` **y**
valores en `refs` ignoraba esos valores y mostraba las texturas de reserva. Ahora
esos valores sí se aplican. Nadie escribe ambas cosas a propósito, pero si lo
hiciste, quita el bloque `refs` en lugar de la línea `mode`.

Consulta [Texturas y Modelos](textures-and-models.md) para la regla completa.

### Protocolo de red

Los clientes 1.7.0 no pueden entrar a servidores 1.6.x, ni al revés: 1.7.0 añade
canales de red que la versión anterior no tiene, y la conexión se rechaza antes
de entrar. Ambos lados deben actualizarse juntos. No hay nada que cambiar en tus
JSON.

## 1.6.0

### `cooking_time` ahora va en segundos

Aplica a `smelting`, `blasting`, `smoking` y `campfire_cooking`.

```diff
- "cooking_time": 200
+ "cooking_time": 10
```

Divide tu valor viejo entre 20. Un archivo sin tocar cocina 20× más lento de lo
que pretendías — `200` era el default del horno de vanilla y ahora son tres
minutos y medio.

Se aceptan decimales, así que `"cooking_time": 7.5` es válido.

### `requires_player_kill` ahora es `false` por defecto

```diff
  "mob_drops": {
    "entities": ["all"],
    "chance": 0.10,
+   "requires_player_kill": true
  }
```

Antes era `true`. Si tus drops sostienen la economía de un servidor, decláralo
explícitamente — si no, una granja de daño por caída o de lava imprime moneda sin
que participe ningún jugador. El parser avisa cuando el campo se deja sin
declarar.

La comprobación también cambió de significado: ahora pregunta si un jugador dañó
al mob recientemente, no si dio el golpe final. Un mob que termina muriendo por
su propia caída después de que un jugador lo golpeó sí cuenta.

### `affected_by_looting` reemplazado por `looting_mode`

```diff
- "affected_by_looting": true
+ "looting_mode": "count"
```

```diff
- "affected_by_looting": false
+ "looting_mode": "none"
```

El tercer valor nuevo, `"chance"`, sube la probabilidad del drop en vez de la
cantidad — vanilla usa uno u otro, nunca ambos en el mismo drop. El default es
`"count"`.

## 1.3.0

### Protocolo de red

Los clientes 1.3.0 no pueden entrar a servidores anteriores a 1.3.0, ni al revés.
Ambos lados deben actualizarse juntos. No hay nada que cambiar en tus JSON.
