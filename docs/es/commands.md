# Comandos

| Comando                      | Permiso    | Descripción                                                                                                                                                              |
|------------------------------|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/customgear reload`         | OP nivel 2 | Recarga todos los archivos JSON y texturas sin reiniciar                                                                                                                 |
| `/customgear recipes [ítem]` | Todos      | Lista las recetas que fabrican un ítem (por defecto el de la mano), con IDs que se copian con un clic. Ver [recipe_patch](recipes.md#cambiar-otras-recetas-recipe_patch) |

## Qué actualiza el comando reload

- Nombres de ítems
- Efectos al sostener (armas y herramientas)
- Efectos por pieza y bonos de conjunto (armaduras)
- Efectos de contacto y comportamiento de fuego de los fluidos
- Recetas (el comando recarga los datapacks automáticamente)
- Tags de minado de bloques — cambios de `required_tool` y ajustes de `harvest_level` (1–4)
- Durabilidad mostrada
- Texturas y modelos — **tras presionar F3+T** (el juego solo recarga los recursos del cliente bajo demanda)
- Configuración de drops de mobs (`chance`, `min`/`max`, `entities`)
- Resistencias de daño, de atacante y condicionales (incluido `show_player_resistances`)

## Qué requiere reinicio completo del juego

- Daño de ataque y velocidad de ataque
- Defensa, toughness y resistencia al retroceso de armadura
- Velocidad de minado y nivel de las herramientas (tool sets)
- Activar/desactivar el requisito de drops de un bloque (`harvest_level` 0 ↔ ≥1)
- Agregar o eliminar ítems (archivos JSON nuevos o eliminados)
- Cambiar ID de ítems
- Cambios de `fire_resistant`
- Cambiar una capa de armadura entre mecanismos (reference ↔ custom ↔ transparent)
- Agregar o quitar el bloque `armor_3d` (el render 3D se fija al registrar)
