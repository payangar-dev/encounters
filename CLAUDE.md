# Encounters

Minecraft **1.21.1** mod (Fabric + NeoForge, Java 21). Configurable in-world encounter events — a thunderstorm bolt may awaken an "overcharged" group of mobs, nether portals may disgorge raids, etc. Event pools, group sizes, mob rosters and per-mob NBT are config-driven (JSON5 via YACL).

## Stack

- MultiLoader-Template (jaredlll08), Mojang + Parchment mappings
- Fabric Loom **1.8.13** (do not bump casually — see §Gotchas)
- YACL **3.6.6+1.21.1** (pinned — 3.7.0+ requires Loom 1.10+)
- Optional compat (NeoForge only, `compileOnly`): Iron's Spells 'n Spellbooks, EpicFight (via Modrinth Maven, `epicfight_version` in `gradle.properties`)

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
| **Nether Portal Invasion event** | ✅ done | `event/portal/NetherPortalInvasionEvent.java` |
| Multi-wave cinematic (BUILDUP → WAVE_SPAWN/COMBAT × N → AFTERMATH) | ✅ done | `event/portal/PortalInvasion.java` |
| Portal frame analysis (axis, width, valid spawn face) | ✅ done | `event/portal/PortalGeometry.java` |
| Periodic portal scanner around players | ✅ done | `event/portal/PortalScanner.java` |
| Cinematic interface + ticker (server-tick driven, abandonment policy) | ✅ done | `event/cinematic/Cinematic.java`, `CinematicTicker.java` |
| Shared spawn primitive (NBT load, finalize skip, drop override) | ✅ done | `event/EncounterSpawner.java` |
| Allies / no friendly-fire (Mob#setTarget + LivingEntity#canAttack mixins) | ✅ done | `event/ally/EncounterAllies.java`, `mixin/MobSetTargetMixin.java`, `mixin/LivingEntityCanAttackMixin.java` |
| Universal force-aggro at spawn + per-event re-aggro | ✅ done | `EncounterSpawner.spawn`, `PortalInvasion.enforceAggroOnWave` |
| Weighted mob roster + NBT customs + per-pick weight transform | ✅ done | `event/MobRoster.java`, `event/ResolvedMob.java` |
| Wave-based rarity bias with boost cap | ✅ done | `PortalInvasion.waveWeightTransform` |
| Recursive passenger handling (mounted mobs) | ✅ done | `EncounterSpawner.spawn`, `PortalInvasion.registerWaveMember` |
| Portal teleportation suppression during invasion | ✅ done | `PortalInvasion.suppressPortalTeleportation` |
| Mob leash around the portal | ✅ done | `PortalInvasion.enforcePortalLeash` |
| YACL config + JSON5 file | ✅ done | `config/EncountersConfig.java` |
| Custom GUI list editor for mob pool | ✅ done | `config/gui/WeightedMob*` |
| Debug `/encounters trigger` command | ✅ done | `command/EncountersCommands.java` |
| Iron's Spells optional shockwave | ✅ done | `platform/services/ISpellCompat.java` |
| EpicFight scale compat (vanilla `generic.scale` honored) | ✅ done | `compat/epicfight/EpicFightScaleBridge.java` + `mixin/compat/epicfight/LivingEntityPatchMixin.java` (neoforge) |
| NBT mod-presence filter (entries skipped when referenced mod absent) | ✅ done | `event/NbtModFilter.java` |
| Global equipment-drop toggle (`mobsDropEquipment`, default false) | ✅ done | `EncountersConfig` + `EncounterSpawner` |

## Key entry points

- **Loader bootstrap** — `fabric/FabricMod.java`, `neoforge/NeoForgeMod.java` → both call `Encounters.init()` then register their lightning listener. `Encounters.init()` wires `CinematicTicker`, `GroupCohesionTicker` and `PortalScanner` into the platform's level-tick callback.
- **Lightning hook** — per-loader listener calls `LightningOverchargeEvent.onLightningSpawn(level, bolt)` which returns `true` to cancel the vanilla bolt and schedule the encounter.
- **Portal trigger paths** — natural: `PortalScanner` runs on every server tick, sweeps a 25×25×Y cube around each living overworld player every `netherPortalInvasionScanIntervalTicks` ticks, dedupes blocks to portal sites, rolls per site against `netherPortalInvasionTriggerChance`, and calls `NetherPortalInvasionEvent.forceTrigger(level, site, face)`. Debug: `/encounters trigger nether_portal_invasion` requires a portal within 16 blocks of the source position.
- **Cinematic system** — `Cinematic` interface (`level()`, `anchor()`, `tick()`, `isFinished()`, `onAbandoned()`). `CinematicTicker.start(c)` schedules and enforces the abandonment policy (96-block radius, 600 consecutive ticks → `onAbandoned()` + remove). Both `LightningCinematic` and `PortalInvasion` implement it.
- **Shared spawn primitive** — `EncounterSpawner.spawn(level, mob, pos, rng, eventId)` is the single entry point for spawning encounter mobs. **Intentionally skips `finalizeMobSpawn` when the user provided custom NBT** so vanilla doesn't overwrite custom equipment (mirroring `/summon`). After adding the entity, recursively force-aggroes the mob and every passenger on the nearest player within 64 blocks (sets both `Mob#target` and the `ATTACK_TARGET` brain memory) so encounter mobs always engage regardless of vanilla pacification.
- **Allies system** — `EncounterAllies.newGroupTag()` mints a unique tag; events stamp it on every mob they spawn. Two mixins veto cross-ally targeting: `MobSetTargetMixin` (HEAD-cancels `Mob#setTarget(LivingEntity)` for goal-driven AI) and `LivingEntityCanAttackMixin` (HEAD-cancels `canAttack` for brain-driven AI like piglins).
- **Wave weight transform** — `MobRoster.pick(rng, IntUnaryOperator)` lets callers remap entry weights per pick. `PortalInvasion.waveWeightTransform` uses this to shift the roster distribution toward rare mobs as the invasion progresses, with a per-entry cap (4× base) so very-rare entries (Ghast at base 1) never become dominant at the final wave.
- **Platform abstraction** — `platform/Services.java` resolves `IPlatformHelper` and `ISpellCompat` via `ServiceLoader`. Each loader provides its own implementation in `platform/`.
- **Config GUI** — `config/ConfigScreenBuilder.create(parent)` is called from each loader's ModMenu / NeoForge config screen factory; the screen is fully derived from `@AutoGen` annotations on `EncountersConfig`.
- **EpicFight compat bridge** — `compat/epicfight/EpicFightScaleBridge.java` is called from a `@Pseudo` mixin targeting `LivingEntityPatch#getModelMatrix`. The bridge uses cached reflection (no EpicFight imports in common) so the whole pipeline is inert when EpicFight is absent.

## Config system

- **File**: `<config-dir>/encounters.json5`
- **Backend**: YACL `ConfigClassHandler` + GSON, `JSON5` format
- **Annotation-driven GUI**: `@AutoGen` + `@TickBox`/`@IntSlider`/`@DoubleSlider`/`@ListGroup`, split across three categories (`general`, `lightning_overcharge`, `nether_portal_invasion`)
- **Mob pool entries** (`WeightedMob`): `{ id, weight, nbt?, label? }` where `nbt` is SNBT identical to `/summon` syntax. NBT is in 1.21.1 format (`ArmorItems`/`HandItems` separate, `attributes` with `generic.` prefix, `active_effects` lowercase, items use `components:`)
- **Defaults** seeded by `EncountersConfig.defaultLightningMobs()` — sculk/soul-themed undead: vanilla baseline + EpicFight/Iron's Spells/SimplySwords variants, each auto-skipped per install by `NbtModFilter` when its mod is absent. A single config file works across any modpack combination.
- **`mobsDropEquipment`** (global, default `false`): when false, `LightningOverchargeEvent.spawnOne` forces `Mob#setDropChance(slot, 0)` after `finalizeMobSpawn`, overriding both per-entry `HandDropChances`/`ArmorDropChances` NBT and vanilla defaults. New entries should omit the drop-chance boilerplate — the toggle handles it.
- **Reload contract**: `EncountersConfig.load()`/`save()` invalidate every event's cached `MobRoster` (`LightningOverchargeEvent.invalidateRoster()`, `NetherPortalInvasionEvent.invalidateRoster()`) so config edits take effect immediately. New events must add their invalidate call here.

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
8. **EpicFight mixin uses `@Pseudo` + `targets = "..."` (string), not a Class literal.** The `LivingEntityPatchMixin` targets `yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch` by name so the mixin class is only processed via ASM (no JVM class-load) and is silently skipped when EpicFight is absent. `@ModifyReturnValue` **must** use the real `OpenMatrix4f` type (not `Object`) — mixinextras rejects type widening at APPLY phase with a hard crash. That's why we need the `compileOnly` Modrinth dep despite `@Pseudo`. All EpicFight access outside the mixin method goes through `EpicFightScaleBridge` (reflection, no imports) so the rest of `common` stays inert when EpicFight isn't present.
9. **`NbtModFilter` walks the entire SNBT tree** looking for any string or compound key that parses as a `ResourceLocation` whose namespace is neither `minecraft` nor a loaded mod (via `Services.PLATFORM.isModLoaded`). Any entry with a missing mod reference is skipped at `MobRoster.resolve`. This lets modded entries (EpicFight weapons, Iron's Spells effects, etc.) coexist with vanilla ones in a single config that degrades gracefully per install. Filter decisions are logged at `DEBUG`, the resolve summary (`{} kept, {} filtered`) at `INFO`.
10. **Equipment drop override runs after `finalizeMobSpawn`.** The `setDropChance` loop in `EncounterSpawner.spawn` must come after both `loadEntityRecursive` (which applies NBT drop chances) and `finalizeMobSpawn` (which may set vanilla defaults for mobs without custom NBT). Running it earlier would let either path clobber our override.
11. **Portal teleportation suppression uses `Entity#setPortalCooldown`, not a mixin.** During a portal invasion, `PortalInvasion.tick()` re-applies `setPortalCooldown()` every tick on every entity inside a 10×6×10 box around the portal anchor. Vanilla `handleInsidePortal` skips the `isInsidePortal` accumulator while the cooldown is non-zero, so neither players nor invasion mobs can ever teleport. Cooldown drains naturally over ~15 s after the cinematic ends, giving a small grace period before the portal works again.
12. **Two mixins for allies, not one.** `MobSetTargetMixin` covers the `Mob#setTarget` path (used by Goal-based AI: `HurtByTargetGoal`, `NearestAttackableTargetGoal`, etc.). `LivingEntityCanAttackMixin` covers the brain-based path (piglins, hoglins, axolotls, …) where `Brain#setMemory(ATTACK_TARGET, …)` bypasses `setTarget` entirely but every brain task still validates a candidate via `canAttack`. Both mixins are required — removing either lets some friendly fire through.
13. **`ActiveEncounterTracker` is keyed on `Cinematic.eventId()`, not just level.** The interface contract is `eventId() = the event class's public static final String ID` (e.g. `NetherPortalInvasionEvent.ID`). `activeCount(level, eventId)` and `nearestActiveDistance(level, eventId, pos)` are the standard call sites — they only count cinematics from the same event family, so a running skirmish doesn't gate a portal invasion (and vice-versa). The eventId-less overloads remain as diagnostic helpers. Implementation is a single `CopyOnWriteArraySet` scanned linearly: at N ≤ ~20 active cinematics, that's strictly faster than bucketed indices (no hash, cache-friendly). Portal invasion concurrency settings: `netherPortalInvasionMaxConcurrent` (default 1) and `netherPortalInvasionMinDistanceBetween` (default 256, enforced only in `PortalScanner` so the debug command may force adjacent invasions). `NetherPortalInvasionEvent.lastInvasionEndTick` is the post-end world-wide cooldown timestamp. Mutualising the `canScannerTrigger` / `forceTrigger` boilerplate with `PatrolSkirmishEvent` is the natural next refacto step — `eventId` is already the parameter a generic coordinator would take.
14. **Portal invasion mobs are leashed to the portal**, not held by `GroupCohesion`. `PortalInvasion.enforcePortalLeash()` re-issues a navigation order toward the spawn anchor every second on any wave member that strayed beyond 20 blocks AND is not in active combat. Mobs chasing the player are deliberately left alone so retreats remain dangerous.
15. **Standalone `magma_cube` is absent from the default portal roster.** Magma_cube split children inherit no group tag and turn on the rest of the invasion. Can be added back in user configs at the user's risk. Standalone `hoglin` is also absent — but **hoglin appears as a mount** in the Tusked Vanguard / Crossbow Outrider archetypes. PiglinBruteAi has no `StartHuntingHoglin` task, and the regular Piglin's hunting task is suspended in RIDE activity, so neither rider/mount combo loops.
16. **Universal force-aggro overrides vanilla pacification.** `EncounterSpawner.spawn` recursively sets `setTarget(nearestPlayer)` AND `brain.setMemory(ATTACK_TARGET, nearestPlayer)` on the spawned mob and every passenger. This forces the FIGHT brain activity, which doesn't re-check pacification rules (gold armor on the player, Zombified Piglin neutrality, etc.). `PortalInvasion.enforceAggroOnWave` re-applies it every 60 ticks on null-target wave members so the directive holds across player respawns / line-of-sight breaks. `EncounterSpawner.applyAggro` is the package-public helper for this.
17. **Recursive passenger handling.** `PortalInvasion.registerWaveMember` walks `Entity#getPassengers()` so a Hoglin + Piglin Brute combo lands two entries in `currentWaveMobs` (both tagged as allies, both contributing to wave-completion). `EncounterSpawner.preventZombification` and `forceAggroRecursive` recurse the same way. Without this, the rider would not carry the allies tag, the rider's death wouldn't matter for wave progression, and a hoglin-mounted piglin could morph in the overworld.
18. **Wave weight transform has a boost cap (4×).** Without it, the linear-inversion formula (`adjusted = base + progress × (max − 2×base + 1)`) makes the rarest entry (Ghast at base 1) the most common pick at the final wave — contradicting "very rare". The cap clamps any boost so a base-1 entry climbs to 4 at most while base-30 entries (already decreasing toward 1) are unaffected. Defined as `WEIGHT_BOOST_CAP` in `PortalInvasion`.
19. **Large mobs (>3 blocks on width or height) get a wider portal spawn offset.** `PortalInvasion.pickSpawnPos` probes `EntityType#getDimensions` and pushes ghasts (4×4×4) 3.5–5 blocks out from the portal so their bounding box clears the obsidian frame. Regular mobs spawn at 0–0.5 blocks as before.
20. **Breaking the portal during an invasion stops it.** Once per second, `PortalInvasion.tick()` checks that `anchorKey` is still a `Blocks.NETHER_PORTAL`; if not (frame block destroyed, or any chain neighbour update extinguished the portal), `onPortalBroken()` runs the same teardown as `onAbandoned()` — releases the wave lockdown, discards the magma-bomb caster, fires the network end packet and unregisters from `ActiveEncounterTracker`. Already-spawned mobs stay alive on purpose: the player still has to deal with what came through. Subsequent waves are skipped.

## Status

Two events shipped: **lightning_overcharge** (reference impl) and **nether_portal_invasion** (multi-wave, scanner-driven). Architecture is now ready for additional events — drop a new class implementing `Cinematic`, register a tick listener or external trigger, and reuse `EncounterSpawner` + `EncounterAllies` + `MobRoster`. No event registry/API surface yet — each event still wires its own trigger path.
