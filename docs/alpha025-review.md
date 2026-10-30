# Alpha 0.2.5 — Pane Relief

Developer: Shadow

## Scope and architecture

This continues the main-based Alpha 0.2.4 tree. The original main base is `85a17791d3dcb9e1db7fa50ad9426895544b71c9`; the previous polish history remains in its ancestry. Publication targets `polish`.

The pass retains the Java simulation/native-engine command boundary, fixed NPC/animal/monster skill profiles, stable authored identities, real inventories and currency, the existing Work interruption system, and the current save schema. Repair knowledge, reports and commissions extend Repairs; navigation still uses native agents for local travel; fallen gear uses native rigid bodies. Existing attack, dodge and locomotion animations remain in place.

## Validation

The final affected run passed **52 tests**, including every law regression, with zero failures or skips. The distribution was rebuilt afterward. The broad suite completed **294 tests: 292 passed, one failed, one skipped**; its sole failure was the petty-fine search retained after deferral. The final law run includes that exact failing test and passes after clearing all related searches and enforcing the existing grace period. The skipped check is the explicitly disabled `SimProfileProbe`, replaced here by the standalone timing comparison.

- Native self-test: 121 passed, zero failed
- Engine build and Java integration suite: passed, 29 tests
- Distribution build: passed
- Final fire/repair checks: 16 passed, including perception of an actual burning solid wall, obstruction by a separate wall, real fire spread, the complete-map window repair journey, and payment/save conservation
- The initial full run completed 294 tests with one failure and one skipped check. The failure exposed petty-fine pursuit retained by improved combat tracking; the final correction expires conversations on the world clock and prevents old searches from restarting during the fine grace period. That run also encountered overlapping report destinations, so it was repeated with immutable inputs and separate report directories
- The latest fire-surface correction and final shrub mesh were made after the repeated full suite was snapshotted; the 52-test affected run and native rendered gallery were verified separately afterward

The new regression class also covers cautionary weapon-display memories, paid and unpaid reports, upgrading a civilian report to an officer's commission, interrupted work, missing-mover recovery without replacing possessions, visible opponents versus occlusion, dropped-shield physics/save pose, retired-equipment conversion and ledger totals, sentence release/relock state, cell placement/materials, and doorway permissions during route shortcuts.

One fire probe deliberately uses a solid plank cell: aiming a visibility ray at its centre made the wall hide its own fire. Observation now targets the near surface; a separate wall still occludes the hazard. The same surface rule permits witnessing a partially burnt dual-material wall.

## Performance evidence

A standalone native simulation comparison ran without competing build or gallery jobs: 600 warm-up ticks followed by 3,000 measured ticks, deterministic native configuration, morning conditions, and the player at the same city position. Java world decisions are not a recorded replay, so a small percentile difference is not a general frame-rate guarantee.

| Simulation tick | Alpha 0.2.4 baseline | Alpha 0.2.5 |
| --- | ---: | ---: |
| Median | 2.829 ms | 2.573 ms |
| 95th percentile | 5.628 ms | 4.983 ms |
| 99th percentile | 9.969 ms | 10.783 ms |
| Maximum | 23.193 ms | 23.226 ms |

The median and 95th percentile improved in this sample; the highest percentiles did not. This does not establish that all stutters have been eliminated or that hardware rendering meets a particular frame-rate target.

A separate native mesh-cache pressure probe preloaded 71 figures, then changed one actor's blood appearance 100 times. The baseline retained 99 obsolete completed/pending builds after showing the latest appearance (peak 100 jobs). The updated cache peaked at one pending build and ended with zero, while retaining the latest appearance. Its figure count stayed at 71. This verifies the bounded queue independently of software-rendered frame times.

## Visual review

The shared target remains Classic-era World of Warcraft: readable foliage silhouettes, restrained greens, warm light, and clear attack anticipation and recovery. References remain the [Classic Elwynn fishing guide](https://boosting-ground.com/wow-classic/guides/profession-guides/fishing-and-cooking-1-300) and [Classic Stockade guide](https://www.wowhead.com/classic/guide/classic-wow-the-stockade-dungeon-quests). No reference artwork was copied.

Matched native captures are included under [reference](alpha025/reference/) and [final](alpha025/final/). They cover palace hedges, DevCity bars and slab, living tree foliage, bushes, and dropped weapons/shields. Armor contact sheets capture heavy attack and dodge at 60 Hz on frames 1, 4, 7, through 58. Review checked elbow/knee coverage during flexion, continuity through attack/recovery, unchanged weapon grip, a slab clear of the back wall, and dropped gear lying on the floor.

The first shrub iteration still read as solid blocks. It was replaced with closed, folded pointed leaves, lit ridges and shaded undersides, arranged around the existing stems. Root coordinates remain attached to every vertex, so wind and contact bending use the existing shared plant response. The final crown has fewer triangles than the intermediate box crown.

The detail fixture synchronizes the abstract clock before unloading workers: otherwise a ten-hour time jump lets abstract woodcutters fell the very tree selected for the leaf photograph. The slab camera keeps the player behind the camera so the body does not obscure the mattress.

The existing block-based character and world style remains visible. The captures provide inspectable evidence of the changed shapes and poses; they do not certify an objective perfect score or an exact match to another game's art.

## Reproduction

From `rpggame`, build with `sh gradlew installDist` and run checks with `sh gradlew test`. From `theastrattoengine`, run `sh gradlew build`; the native self-test can also run directly from `native/bin/<platform>/astratto_selftest`.

The three standalone Java probes in [alpha025](alpha025/) are not shipped game code. Compile them from `rpggame` with:

```sh
javac -cp 'build/install/rpggame/lib/*' -d build/review ../docs/alpha025/*.java
java --enable-native-access=ALL-UNNAMED -cp 'build/review:build/install/rpggame/lib/*' rpg.Review025 build/review/shots
RPG_PROF=1 java --enable-native-access=ALL-UNNAMED -cp 'build/review:build/install/rpggame/lib/*' rpg.SimulationProfile
java --enable-native-access=ALL-UNNAMED -cp 'build/review:build/install/rpggame/lib/*' rpg.FigureCacheProbe
```

The gallery/cache probes need an OpenGL display. `Review025` accepts `details` or `bush` as its second argument for a limited capture. Use a baseline distribution's jars with the same probe to reproduce the comparison. Test runs performed concurrently must use independent binary/XML/HTML report destinations as well as frozen classpaths.

## Persistence and practical limits

Legacy named weapons and armor aliases convert to standard tier equipment, merging ledger keys and converting quick slots. This retains quantities, ownership, theft/provenance and nested contents through the existing item serializer; retired definitions are absent from the active catalog.

Repair reports/orders persist; transient claims and approach targets are reacquired after loading. A commissioned job waits if its payer lacks money or its worker lacks a tool/material. Repairs do not fabricate supplies. Workers retain the existing settling period and defer active burning or inaccessible sites.

Loose equipment follows the existing eight-second physics budget and then retains its resting pose. A missing living NPC mover is recovered only after a grace interval; it does not resurrect dead actors or replace deliberate unloaded/physical-reaction states.
