# Migration

Breaking changes only — things that make a JSON file that worked in one version
behave differently or stop loading in the next. Everything additive lives in the
[CHANGELOG](../../CHANGELOG.md).

> This page is incomplete. It currently covers 1.7.0, 1.6.0, and the network protocol
> change in 1.3.0. Earlier-breaking changes need to be pulled out of the
> CHANGELOG.

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
