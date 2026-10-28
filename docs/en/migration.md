# Migration

Breaking changes only — things that make a JSON file that worked in one version
behave differently or stop loading in the next. Everything additive lives in the
[CHANGELOG](../../CHANGELOG.md).

> This page is incomplete. It currently covers 2.0.0, 1.7.0, 1.6.0, and the network
> protocol change in 1.3.0. Earlier-breaking changes need to be pulled out of the
> CHANGELOG.

## 2.0.0

**Nothing in your JSON changes.** No field was added, removed or renamed in the
port to Minecraft 26.1.2. Copy your content folder across and it loads.

What does change is the jar, and none of it is optional:

- **2.0.0 runs on Minecraft 26.1.2 only.** 1.7.0 will not load on it, and this
  will not load on 1.21.1. Keep the version that matches your game
- **Java 25**, up from Java 21. A server pinned to an older JDK refuses to start
- **GeckoLib 5.5.2**, **JEI 29.29.0.77**, **Curios 15.0.0**. The builds you were
  running alongside 1.7.0 do not load on 26.1.2

### Two texture forms got shorter, and both still work

Chest, shulker and armour-layer references can now be written the way the game
addresses them:

```diff
  "refs": {
-   "single": "minecraft:textures/entity/chest/normal.png"
+   "single": "minecraft:normal"
  }
```

```diff
  "armor_layers": {
-   "layer_1": "othermod:models/armor/diamond_layer_1"
+   "layer_1": "othermod:diamond"
  }
```

Both forms are read, and the long one resolves to exactly the same asset, so this
is cleanup rather than a fix. Nothing breaks if you leave it alone.

## 1.7.0

**Nothing to change.** Packs written for 1.6.0 load unchanged.

### `texture.mode` no longer does anything

```diff
  "texture": {
-   "mode": "reference",
    "refs": {
      "sword": "minecraft:item/netherite_sword"
    }
  }
```

What a value is now comes from the value itself: a `:` makes it another mod's
asset, a file extension makes it a file in your folder. A pack that still
declares `mode` works exactly as before — the field is read and ignored — so
deleting it is cleanup, not a fix.

The one thing that genuinely changed: a pack declaring `"mode": "default"` **and**
values in `refs` used to ignore those values and show the placeholder textures.
Those values now apply. Nobody writes both on purpose, but if you did, remove the
`refs` block rather than the `mode` line.

See [Textures & Models](textures-and-models.md) for the full rule.

### Network protocol

1.7.0 clients cannot join 1.6.x servers, and vice versa: 1.7.0 adds network
channels the older version doesn't have, and the connection is refused before
joining. Both sides must update together. Nothing to change in your JSON files.

## 1.6.0

### `cooking_time` is now in seconds

Applies to `smelting`, `blasting`, `smoking` and `campfire_cooking`.

```diff
- "cooking_time": 200
+ "cooking_time": 10
```

Divide your old value by 20. A file left untouched cooks 20× slower than
intended — `200` used to be the vanilla furnace default and is now three and a
half minutes.

Decimals are accepted, so `"cooking_time": 7.5` is valid.

### `requires_player_kill` now defaults to `false`

```diff
  "mob_drops": {
    "entities": ["all"],
    "chance": 0.10,
+   "requires_player_kill": true
  }
```

It used to default to `true`. If your drops back are a server economy, declare it
explicitly — otherwise a fall-damage or lava farm prints currency with no player
involved. The parser warns when the field is left undeclared.

The check also changed meaning: it now asks whether a player damaged the mob
recently, not whether a player landed the killing blow. A mob finished off by
its own fall after a player hit it still counts.

### `affected_by_looting` replaced by `looting_mode`

```diff
- "affected_by_looting": true
+ "looting_mode": "count"
```

```diff
- "affected_by_looting": false
+ "looting_mode": "none"
```

The new third value, `"chance"`, raises the drop probability instead of the
amount — vanilla uses one or the other, never both on the same drop. Default is
`"count"`.

## 1.3.0

### Network protocol

1.3.0 clients cannot join pre-1.3.0 servers, and vice versa. Both sides must
update together. Nothing to change in your JSON files.
