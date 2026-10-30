# Alpha 0.2.6 — Ash and Order

Developer: Shadow. Visual target: Classic-era World of Warcraft, within the existing voxel art direction. This pass continues local Alpha 0.2.5 `35fe43e`; its main ancestry is `85a1779`. Publication is to `polish`, preserving its existing history. Technical project/save paths remain compatible. Earlier release notes are preserved.

## Architecture and scope

Gameplay remains Java over the native engine's physics, local A*, agent policies, chemistry, audio, and renderer. World commands become visible after a frame; tests and save reconstruction account for that boundary. The existing witness/rumor memories, legal settlement, interruptible Work, material ledger, repair orders, damage-mesh worker, and personal conversations are extended rather than replaced. No NPC progression is added; only the player earns skill XP.

The complete navigation vision is explicitly groundwork for this release. Native partial-path feedback and chunk invalidation, height-aware gameplay targets, persistent personal ground-route memories, and nearby knowledge sharing ship here. Automatically generated multi-level chunk regions/portals replacing all designer routes, learned native edge costs, broader landmark seeds, per-person cost noise, and new chokepoint yield arbitration remain for the next update. Existing native avoidance and bounded search remain in place.

## Navigation measurements

The same deterministic native course was run using preserved Alpha 0.2.5 jars and Alpha 0.2.6. Each case runs 45 simulated seconds, at 60 Hz, with a 3.5 m/s desired speed. Arrival is measured from actual agent position, separately from arrival events. The doorway case has ten travellers in each direction through a one-cell opening. The dynamic wall appears after two seconds.

| Course | Baseline arrival | Release arrival | Reached | Stuck events | Release partials / searches | Cap hits | Distance travelled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Dead-end pocket | 13.40 s | 13.40 s | 1/1 | 0 | 0/1 | 0 | 45.70 m |
| One-cell doorway | 17.98 s | 17.98 s | 20/20 | 0 | 0/30 | 0 | 787.83 m total |
| Wall appears mid-route | 12.30 s | 12.30 s | 1/1 | 0 | 0/2 | 0 | 42.36 m |

“Searches” includes the initial route; the appended per-agent counter is named `repaths`. Baseline binaries do not expose partial, cap, repath, or yield counters: the repeatable runner prints `-1`, meaning unavailable. Baseline raw query counts are 2, 31, and 3; they include the separate reference query. No baseline zero rate is inferred from missing telemetry. The yield counter is reserved and remains zero; new crowd arbitration is not claimed.

For actual-versus-optimal comparison, the runner additionally solves a visibility graph around final obstacle rectangles. This is an exact geometric **point-agent lower bound**, divided by desired speed: it ignores acceleration, capsule radius, other agents, and—in the wall case—assumes foreknowledge of the final wall. It is an optimistic oracle, not a claim of physically achievable crowd time.

| Course | Point-agent lower bound | Release arrival / lower bound |
| --- | ---: | ---: |
| Pocket | 12.77 s | 1.049 |
| Doorway, longest individual bound | 10.96 s | 1.641 |
| Changing wall, foreknowledge bound | 11.71 s | 1.050 |

The old raw grid reference is also printed for reproducibility, but it is not called optimal: cell-centre paths can exceed smoothed travel, and the original wall reference is requested before the wall exists. The passing twenty-agent result does not justify a full crowd rewrite, and says nothing about arbitrarily denser crowds. A separate unreachable-goal regression exercises capped partial searches and asserts they never emit successful arrival.

Raw logs: [baseline](alpha026/reference/navigation.txt), [release](alpha026/final/navigation.txt). Runner: [NavCourse.java](../rpggame/tools/alpha026/NavCourse.java).

The native API minor version advances from 2.5 to 2.6 for the added snapshot tail and rendering flag; matching Java bindings and native binaries are shipped together.

## Visual evidence and review

All pictures are captured through the native renderer at 640 × 360 under llvmpipe OpenGL 4.5. Reference captures use the preserved Alpha 0.2.5 distribution. Cameras, lighting, equipment, and exposure are matched where compared. Development puppets and normalized animation progress are used for reproducible model inspection; these are not performance benchmarks.

- Cell geometry: [reference](alpha026/reference/palace-cell.png) → [release](alpha026/final/palace-cell.png). The cell's side partition is barred; the gate leaf shares the bars' plane; enclosing limestone matches the basilica. [DevCity reference](alpha026/reference/city-bars.png) and [release](alpha026/final/city-bars.png) show the door correction there as well. The foreground bench is intentionally retained.
- Palace slab: [reference](alpha026/reference/palace-slab.png) → [release](alpha026/final/palace-slab.png). The slab now lies inside the usable cell rather than intersecting the wall, with stable furniture identity.
- Crossbow: [reference](alpha026/reference/crossbow.png) → [release](alpha026/final/crossbow.png). The shared 1.5× mesh has a readable prod and longer stock while retaining its grip. [First-person reference](alpha026/reference/crossbow-firstperson.png) and [release](alpha026/final/crossbow-firstperson.png) retain the complete prod inside the frame. [Reload reference](alpha026/reference/crossbow-reload.png) and [release](alpha026/final/crossbow-reload.png) sample frames 1, 4, 7 through 58; resizing does not replace the existing rig or timing.
- Dissolve: [reference](alpha026/reference/dissolve.png) → [release](alpha026/final/dissolve.png). Frames 1, 4, 7 through 58 cover the normalized transition on a Giant Spider. The initial implementation's large bright particle cloud was rejected and replaced by small ash motes. Final breakup uses whole local voxel cells and the same shadow cutoff. Half-voxel model grips are aligned using mesh bounds. Temporal smoothing can soften the smallest remaining fragments; a perfect artistic score or review of every creature animation is not claimed.
- Animal fire: [burning](alpha026/final/animal-burning.png), [charred](alpha026/final/animal-charred.png). The same native exposure produced 15 localized marks in the release and none in the baseline; global charring reached approximately 0.76 versus 0.26 in this inspection. Native fire, not a manually painted scorch, drives the result.
- Human remains: [release](alpha026/final/body-charred.png) versus [reference](alpha026/reference/body-charred.png). The release body acquired 9 marks and approximately 0.36 charring; the baseline dead-body receiver produced none. Flame brightness can mask small marks, and [the view after a water application](alpha026/final/body-after-water.png) records the ongoing native effect without claiming the surrounding fire is extinguished.
- Invisibility: [visible](alpha026/final/visible.png) → [invisible](alpha026/final/invisible.png). The same camera loses the player's body, held Sword, Shield, and shadow while retaining world geometry. Registered command checks cover sensory exclusion and reset as well.

Reference and release directories retain the original captures. These establish concrete geometry, visibility, and effect corrections; they do not certify subjective 20/20 perfection or substitute for playtesting on the player's GPU.

## Verification and regressions

| Check | Final result |
| --- | --- |
| Full gameplay suite | 312 cases: 311 passed, 0 failed, 1 deliberately disabled profiling probe |
| Final affected navigation, world, and morning-routine checks | 12 passed, 0 failed |
| Native self-test | 121 passed, 0 failed |
| Engine Java/GL suite | 29 passed, 0 failed |
| Release distribution | `installDist` passed; all four standalone review runners compile against it |
| Final distribution navigation course | 1/1 pocket, 20/20 doorway, 1/1 changing wall; measurements match the table above |
| Registered console integration | Scoped bounty set/reset, invalid input, invisibility and player reset passed |

The intentionally disabled case is `SimProfileProbe.profileTheSimulation`. The complete suite passed after the routine fixes. Two final focused regressions were then added for a same-position unreachable floor and unrelated arrival verification; the final 12-case run covers both plus all six world cases and the unchanged long morning-routine thresholds. The native floor-failure correction was followed by the complete engine check. Interrupted pre-reconnection runs are not counted as completed verification.

A compact record is retained in [verification.txt](alpha026/verification.txt).

The tests exercise actual material consumption and coin transfer, personal witness ownership, one appeal per witness/incident, rejected bribery, saved defiance and names, real ignition, parked corpse receivers, saved animal scorch marks, seven-block falling, six prioritized distinct speech lines, height queries, bounded partial-path failure, and persistent retry penalties. The registered console runner validates all three kingdoms, selective/all reset, invalid input without mutation, invisibility, and player reset.

Two existing tests needed fixture/observation corrections: artificial map clearing must clear its own repair records; full-pack events are observed when emitted because the live log is bounded to 600 entries. The posse test inspects the first newly laid snare immediately rather than waiting for another snare while animals can already spring the first. The arrest test now asserts lethal explicit resistance, as requested. The routine test's original thresholds are retained.

Long-run verification caught two navigation issues during development: target furniture height must not override interaction reach, and a destination's 30-second retry penalty must not freeze every unrelated errand. The visible pause is capped at two seconds, while the personal destination penalty remains. Goal changes clear the prior errand's status. A same-position destination can also change its target floor without retaining a stale ground-level goal. An unreachable floor directly overhead also emits BLOCKED rather than freezing on an empty path; this is covered by a separate native regression. Unrelated arrivals and teleports cannot verify an interrupted journey. Elevated raw trips are kept out of the ground-only RouteBook. Ordinary door collision-cell changes are explicitly excluded from structural damage records; opening a door is not a broken building.

## Reproduce

From `rpggame`, build with Java 21 and the native toolchain:

```sh
sh gradlew --offline test installDist
javac -cp 'build/install/rpggame/lib/*' -d build/review026 tools/alpha026/*.java
java --enable-native-access=ALL-UNNAMED -cp 'build/review026:build/install/rpggame/lib/*' rpg.NavCourse
```

With a working OpenGL display, run `rpg.Review026 <output>` for cells, third-person Crossbow, and dissolve; append `crossbow` for both cameras plus reload frames, or `dissolve` for the dissolve sheet alone. Run `rpg.Fire026Review <output>` for real fire exposure (append `held` to reproduce the paired held-animal reference views) and `rpg.Console026Review <output>` for command checks and visibility captures. Replace the distribution classpath with the preserved baseline jars for comparable reference runs. Each animation sheet samples every third frame.

Engine checks run from `theastrattoengine` with `sh gradlew --offline check`. CI and local native gameplay require Java fallbacks disabled. Frozen classpaths and separate report directories were used during the parallel verification runs so later compilation could not replace a running test's code or reports.
