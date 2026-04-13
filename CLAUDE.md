# Encounters

Minecraft **1.21.1** mod (Fabric + NeoForge, Java 21). Configurable in-world encounter events — a thunderstorm bolt may awaken an "overcharged" group of mobs, nether portals may disgorge raids, etc. Event pools, group sizes, mob rosters and per-mob NBT are config-driven (JSON5 via YACL).

## Stack

- MultiLoader-Template (jaredlll08), Mojang + Parchment mappings
- Fabric Loom **1.8.13** (do not bump casually — see §Gotchas)
- YACL **3.6.6+1.21.1** (pinned — 3.7.0+ requires Loom 1.10+)
- Optional compat: Iron's Spells 'n Spellbooks (NeoForge only) — `compileOnly`

## Build

```
./gradlew :common:build :fabric:build :neoforge:build
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```

## Module layout

- **common/** — all gameplay logic, config, event API, cinematic system, YACL annotations and custom widgets
- **fabric/** — `FabricMod`, `FabricLightningListener`, `FabricPlatformHelper`, ModMenu integration, no-op spell compat
- **neoforge/** — `NeoForgeMod`, `NeoForgeLightningListener`, `NeoForgePlatformHelper`, real Iron's Spells compat

Common pulls YACL's `-neoforge` artifact at `compileOnly` — same Mojang mappings as our NeoForm setup. Loader modules pull the loader-specific runtime artifact.

The mod runs on **both sides**: server owns event logic and mob spawning; client only needs the jar for parity (and the YACL config GUI).

## Implemented

| Feature | State | Entry point |
|---|---|---|
| **Lightning Overcharge event** | ✅ done | `event/LightningOverchargeEvent.java` |
| Spawn cinematic (sculk-themed, 2-phase) | ✅ done | `event/cinematic/LightningCinematic.java` |
| Cinematic ticker (server-tick driven) | ✅ done | `event/cinematic/CinematicTicker.java` |
| Weighted mob roster + NBT customs | ✅ done | `event/MobRoster.java`, `event/ResolvedMob.java` |
| YACL config + JSON5 file | ✅ done | `config/EncountersConfig.java` |
| Custom GUI list editor for mob pool | ✅ done | `config/gui/WeightedMob*` |
| Debug `/encounters trigger` command | ✅ done | `command/EncountersCommands.java` |
| Iron's Spells optional shockwave | ✅ done | `platform/services/ISpellCompat.java` |
| Other event types (portal raids, etc.) | ❌ todo | — |

## Key entry points

- **Loader bootstrap** — `fabric/FabricMod.java`, `neoforge/NeoForgeMod.java` → both call `Encounters.init()` then register their lightning listener
- **Lightning hook** — per-loader listener calls `LightningOverchargeEvent.onLightningSpawn(level, bolt)` which returns `true` to cancel the vanilla bolt and schedule the encounter
- **Spawner** — `LightningOverchargeEvent.spawnOne()` uses `EntityType.loadEntityRecursive(nbtTag, ...)` and **intentionally skips `finalizeMobSpawn` when the user provided custom NBT** (lines 152-158) — otherwise vanilla would overwrite custom equipment
- **Platform abstraction** — `platform/Services.java` resolves `IPlatformHelper` and `ISpellCompat` via `ServiceLoader`. Each loader provides its own implementation in `platform/`
- **Config GUI** — `config/ConfigScreenBuilder.create(parent)` is called from each loader's ModMenu / NeoForge config screen factory; the screen is fully derived from `@AutoGen` annotations on `EncountersConfig`

## Config system

- **File**: `<config-dir>/encounters.json5`
- **Backend**: YACL `ConfigClassHandler` + GSON, `JSON5` format
- **Annotation-driven GUI**: `@AutoGen` + `@TickBox`/`@IntSlider`/`@DoubleSlider`/`@ListGroup`
- **Mob pool entries** (`WeightedMob`): `{ id, weight, nbt?, label? }` where `nbt` is SNBT identical to `/summon` syntax. NBT is in 1.21.1 format (`ArmorItems`/`HandItems` separate, `attributes` with `generic.` prefix, `active_effects` lowercase, items use `components:`)
- **Defaults** seeded by `EncountersConfig.defaultLightningMobs()` — 5 sculk/soul-themed undead with custom equipment, scale boosts and effects
- **Reload contract**: `EncountersConfig.load()`/`save()` invalidate the cached `MobRoster` so config edits take effect immediately

## Conventions

- Mod ID: `encounters` — package: `com.payangar.encounters`
- Logger: `Constants.LOG`
- Per-event ID constant on the event class (e.g. `LightningOverchargeEvent.ID = "lightning_overcharge"`)
- New events live under `common/.../event/`; cinematics under `event/cinematic/`
- Client-only YACL widget code lives in `config/gui/` (still in `common`, see §Gotchas)
- Lang keys: YACL autogen uses `yacl3.config.encounters:config.<fieldName>` ; custom screens use `encounters.config.<screen>.<key>`
- All written code is in **English**; user-visible strings go through `Component.translatable` with entries in both `en_us.json` and `fr_fr.json`

## Gotchas / non-obvious decisions

1. **YACL pinned at 3.6.6** — 3.7.0 requires Fabric Loom ≥ 1.10.2, 3.8.x requires ≥ 1.13.6. Bumping Loom is a separate chantier; until it's done, do not bump YACL past 3.6.6.
2. **Common references YACL widget classes** (e.g. `ControllerWidget`, `AbstractWidget`) inside `config/gui/`. This works because `common/build.gradle` pulls the `-neoforge` artifact at `compileOnly` (matching Mojang mappings). At runtime the loader-specific artifact is on classpath; the factory classes are only loaded when YACL builds the GUI, which is client-side only — dedicated servers never load them.
3. **Spawner skips `finalizeMobSpawn` when NBT is provided.** Vanilla `Mob#finalizeSpawn` randomises equipment; calling it after a custom NBT load would clobber the user's gear. The branch is at `LightningOverchargeEvent.java:152-158`.
4. **Cinematic deferral**: `level.getServer().execute(() -> trigger(...))` runs the encounter on the next server tick to avoid reentrancy during entity add/join events.
5. **Encounter lightning bolts are visual-only** (`bolt.setVisualOnly(true)` + `OWN_BOLT_TAG`) so they don't fry the mobs they just spawned, and so our own listener ignores them (no recursion).
6. **`/summon lightning_bolt` triggers the event during a thunderstorm** — intentional, kept as a debug path. `isNaturalStormBolt` only excludes Channeling-trident bolts (those have a non-null `cause`).
7. **Spawned mobs are locked down** during the impact phase: `setInvulnerable(true)`, `setNoAi(true)`, `setPersistenceRequired()`. Phase 1 lasts 40 ticks; afterwards they unlock and become a normal explorable threat.

## Status

Mod is **past scaffolding** — the lightning_overcharge event is the reference implementation. Future work: extracting an event registry / API surface so additional events (portal raids, etc.) plug in without ad-hoc per-event hooks.
