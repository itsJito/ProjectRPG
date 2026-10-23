# Gamefixes review and implementation ledger

Review date: 2026-10-22. This is an implementation/verification ledger, not a claim that every item in `gamefixes.md` is complete.

## Structure and constraints

The Gradle composite connects `rpggame` (Java 21) to `theastrattoengine` (Java bindings plus C++17). `Rpg` composes game systems; the native engine owns simulation, graphics resources, and audio. Commands and snapshots cross the versioned C bridge. Keep game policy in the game, numerical simulation in the engine, and animation presentation in the render layer. The current fixes need no bridge or engine rewrite.

Read `gamefixes.md` with GDD §2.1 (appearance), §2.2 (progression), §2.3 (combat), and §4.2 (reusable development map and production-intent animation). The documents conflict on NPC progression: GDD permits NPC XP, whereas gamefixes explicitly prohibits it. This pass preserves gamefixes' existing player-only progression behavior. Numerical movement values are tuning, not a new rule or skill bonus.

### Online references consulted

- [Godot animation blending and cycle synchronization](https://github.com/godotengine/godot-docs/blob/master/tutorials/animation/animation_tree.rst): feet and upper-body gait transitions should use a common blend/cycle rather than independently drifting thresholds. The project keeps its procedural rig; no Godot dependency is added.
- [Game Programming Patterns: Game Loop](https://github.com/munificent/game-programming-patterns/blob/master/book/game-loop.markdown): variable-step integration can become unstable; fixed simulation and variable rendering have different responsibilities. The native simulation stays unchanged. The render-only pelvis spring uses a closed-form critically damped response instead of an unstable Euler step.
- [Unity's Mathf SmoothDamp implementation](https://github.com/Unity-Technologies/UnityCsReference/blob/master/Runtime/Export/Math/Mathf.cs): a velocity-carrying damped response is a conventional animation-smoothing tool. This rig evaluates the exponential directly for a constant target over each frame; it does not copy Unity's approximation or API.
- [Game Programming Patterns: State](https://github.com/munificent/game-programming-patterns/blob/master/book/state.markdown): keep mutually exclusive behavior explicit. No replacement state framework is justified by this locomotion fix.
- [PMD unused-code rules](https://github.com/pmd/pmd/blob/main/pmd-java/src/main/resources/category/java/bestpractices.xml): unused locals/private fields/private methods can be checked statically. Such checks cannot prove that every public API, asset, reflection path, or externally loaded module is reachable.

These sources were retrieved online from GitHub. Some direct article websites were blocked by the environment's network policy. References guide design decisions; repository call sites, regression tests and visual inspection supply evidence about this code.

## First pass: locomotion

- `Rig` previously blended upper-body running over 2.3–4.2 m/s while `Gait` switched the feet over 2.3–3.2 m/s. Both now use `Gait.runShare`. The rig also previously raised its own cadence to fit leg reach while sound/noise used a slower clock. Reach and ground-contact duty now live in `Gait`, shared by player footsteps, NPC footsteps and presentation. Walking retains double support; running shortens contact and extends flight instead of driving the rig at more than three cycles per second. Full-speed locomotion cycles stay within 0.5–1.5 seconds.
- The pelvis's explicit spring integration threw its height from 8 to 32.674 model pixels on a 100 ms frame. The entire character is 32 pixels tall. The exact critically damped response retains displacement and velocity, remains bounded at slow render rates, and agrees across frame partitions for a fixed target. It does not change the native physics timestep.
- Walk tuning changes from 1.85 to 2.1 m/s; sprint from 7.4 to 7.8 m/s. Sneak remains 1.45 and jog 4.6. Burden, injuries, aiming, stamina and agility still modify actual movement through the existing controller.
- Shift remains hold-to-sprint. Removed the write-only `sprintTap` input and the acceptance test's obsolete double-tap ritual. The functional check now explicitly verifies a single hold. Removed unused acceptance snapshots/counters and their orphaned `hitCount` helper; the reviewed AI roster also had an unused floor local.
- Removed the unused `Gait.stepLength` helper and rig state (`turn`, write-only `clock`) and an unreachable early-step flag: a foot lifts out of stance only when its phase is outside the stance interval, so that flag could never become true.
- Removed the acceptance helper's unused hold parameter and its conditional whose two outcomes were identical. Held attack input is still restored after each tick.
- Added `RPG_GALLERY_ONLY=locomotion`, a focused capture of walk/jog/sprint. Each is filmed for 1.2 seconds on frames 1, 4, 7… after warming up, using the real game controller and render path. The camera stays on the clear side of the square so the altar cannot hide the runner. This complements the existing full motion/twitch gallery. `-PgalleryDir=build/review-name` keeps comparison sets separate without overwriting references.

Regression tests in `rpggame/src/test/java/rpg/render/RigTest.java` failed against the original spring and pass with the replacement. Three rig tests check body-height bounds, finite transforms through gait changes and uneven frames, settling, and independence from frame partition for a fixed target. Two additional gait tests check double support versus flight, reachable ground contact, and the requested cycle durations. PMD 7.28.0 found no unused local variables/private fields/private methods across `Rig`, `Gait`, `PlayerController`, `Config`, `Main`, `Acceptance`, `Gallery` and `AiSystem` with those three rules. Expanding the scan caught unused counters and snapshots in `Acceptance`; these were removed without dropping assertions. An older PMD version incorrectly flagged used nested-loop indices; these loops were retained and the current analyzer confirmed them. Repository searches verified the removed input and state had no consumers. This is a scoped audit, not a whole-repository no-dead-code guarantee.

### Reproduce

Activate the cloud tools with `source /workspace/.onboarding/env.sh` and start the display with `bash /workspace/.onboarding/start-display.sh; export DISPLAY=:99`.

From `rpggame`:

```sh
sh gradlew test pmdMain --init-script tools/unused-code.gradle --no-daemon --console=plain --max-workers=2
RPG_ONLY=bugs,pause,console sh gradlew acceptance --no-daemon --console=plain --max-workers=2
RPG_GALLERY_ONLY=locomotion sh gradlew gallery -PgalleryDir=build/review-locomotion/male --no-daemon --console=plain --max-workers=2
RPG_GALLERY_ONLY=locomotion sh gradlew gallery -Pfemale -PgalleryDir=build/review-locomotion/female --no-daemon --console=plain --max-workers=2
```

Inspect the selected output directory's `*-locomotion-*.png` files in frame order and compare with retained before images. Tests do not establish visual quality. Software OpenGL supports screenshots but does not represent target GPU performance.

## Validation results

- Game build: passed. Five regression tests executed, none failed or skipped.
- Focused `bugs,pause,console` acceptance: **38/38 passed**, with Java fallbacks disabled. This includes the new single-hold Shift assertion.
- PMD 7.28.0: no findings under the three selected unused-code rules across the eight reviewed classes. The opt-in, reproducible audit is in `rpggame/tools/unused-code.gradle` and `unused-code.xml`; it adds no runtime dependency.
- Male and female focused galleries: both completed successfully. Each produced three 2400×954 contact sheets, 24 samples per gait on frames 1, 4, 7…70. All six final sheets were visually inspected. Final artifacts are in `rpggame/build/review-locomotion/final-male-clear` and `final-female` (ignored build output).
- Original walk/jog/run references and the uneven-frame baseline report were retained in `/workspace/.onboarding/before-motion`. The original long gallery was stopped after the needed reference sheets were produced; it is not reported as a completed gallery run. An intermediate capture was also stopped when the shared cadence fix superseded it.
- The first candidate exposed altar occlusion. The final focused camera uses the other side of the square; the inspected final sequences keep the figure visible throughout.

Visual assessment of the captured **side-view loops only**: provisional **8/10** for walking, jogging and sprinting, for both body plans. Walking retains support; running shows a distinct flight phase, heel recovery and bent arm swing; the sampled torso/hip motion stays controlled. This is a subjective review of these captures, not a 10/10 claim for the game's animation. Turning, uneven terrain, weapons, injuries and transitions still need their own review, and the underlying models/outfits are not certified by a locomotion check.

The full acceptance suite and long-running AI/stealth sections were not run in this pass. Shared walking cadence changes footstep/noise frequency, so stealth/hearing integration is a relevant follow-up. Strict native build warnings discovered during this pass were addressed in the second pass below. No GPU performance claim is made from software-rendered captures.

## Second pass: death lifecycle and voice resources

### Research and findings

Traced `Combat.resolve` → `AiSystem.kill` → `Reactions.died`, through queued ragdoll commands, the physics service, Sydney's state machine and Impulsis joint drives. The game already reuses an NPC's reacting ragdoll when killed. Ordinary death handling was present; the reproducible engine faults concerned controller release ordering:

- `RELEASE` followed by `DIE` ignored death because the physics service only delivered it to an attached driver.
- `DIE` followed by `RELEASE` during the fade left the outcome unfinished indefinitely because the detached driver could no longer finish its transition.
- Detaching the driver retained the last joint servo settings and cached drive impulses in Impulsis. Controller release must clear those as well as its pointer.

Added one explicit Sydney release operation, used by both manual release and normal completion of the death fade. It releases grips, clears intentions and servo state, detaches the driver, and finalizes a dead body's outcome. Physical velocities, joint limits and collision response remain available so a corpse can still fall and take impacts. Death is delivered to an existing Sydney record even after detachment; repeated death commands do not restart the fade. No public C ABI or record layout changed.

References retrieved online for this pass:

- [Godot ragdoll system](https://github.com/godotengine/godot-docs/blob/master/tutorials/physics/ragdoll_system.rst), especially starting/stopping simulation and collision exclusions: make the ownership handoff explicit. This engine releases active control while retaining passive simulation; it does not adopt Godot's API.
- [Game Programming Patterns: State](https://github.com/munificent/game-programming-patterns/blob/master/book/state.markdown): terminal behavior must remain consistent across transition orderings.
- [Godot AudioStreamRandomizer](https://github.com/godotengine/godot-docs/blob/master/classes/class_audiostreamrandomizer.rst): a pool's entries and weights determine selection probability; duplicate recordings unintentionally weight the same sound twice.
- [C++ Core Guidelines ES.46 and ES.103](https://github.com/isocpp/CppCoreGuidelines/blob/master/CppCoreGuidelines.md): validate narrowing conversions and prevent integer overflow. Applied to the path-query fault discovered during sanitizer verification.

### Voices

The existing death/hurt routing already selected banks using `Appearance.female`. Roster construction and saved-NPC appearance handling preserve the intended roster sex. This investigation has **not reproduced the reported male NPC playing a female voice**; filenames and waveform hashes cannot establish the perceived sex of a recording. That item remains open pending identification/audition of the offending NPC and clip.

Both female death resources numbered 5 and 6 were byte-identical (SHA-256 `e81209da758d4af7575838148100c257c8c915e353dfe1768c3edd189d55da8c`). A new test also confirmed identical decoded PCM. Removed number 6 and its redundant pool entry, retaining five distinct female and six distinct male recordings. `HumanVoice` now supplies a single resource manifest and bank routing for hurt and death playback. The resource audit decodes every configured voice, checks finite/non-silent samples, and rejects duplicate PCM across the banks. It failed on the original duplicate and passes after removal. This fixes weighting and redundant assets, without claiming to fix the unconfirmed voice-sex complaint.

### Verification and captures

- Four new public-C-API death checks failed before the fix (**104 passed, 4 failed**), then passed. They cover both release/death orders, repeated death, subsequent walking intentions and a later hit.
- The new headless game integration test exercises all four actual kill paths: outright, a lethal staggering blow, mid-reaction and bleeding out. Each corpse stays dead for 12 seconds with finite positions, no recovery outcome, no recovery retries, and its head down after settling. Existing reacting bodies are reused. Java fallbacks are disabled.
- Current game tests: **7 passed**, including the five locomotion tests, death integration test and decoded voice audit. Game build passed. PMD's three unused-code rules are clean across the previous eight classes plus `Sfx` and `HumanVoice`. This remains a scoped static audit.
- Both strict native configurations use `ASTRATTO_WERROR=ON` and pass **109/109** native checks. The address/undefined-behavior sanitizer run completes without findings. The standard benchmark passes all **8/8** budgets (including paths, physics and audio); this is CPU benchmark evidence, not a GPU performance claim. The engine build and all **20** Java tests also pass.
- Sanitizers exposed unaligned structure writes in the pre-existing test command builders. Their constructed records now reserve 8-byte slots, including variable policy payload padding. The public wire contract still permits 4-byte alignment; raw fuzz streams retain that coverage. An unused policy setup local was removed.
- The now-running native fuzz checks also exposed integer overflow after extreme path coordinates were converted to integers. Pathfinding validates finite, safely representable coordinates before conversion, leaving arithmetic room for offsets and differences. Unused goal height remains ignored when `GOAL_Y` is absent. Thirty targeted public-API queries cover NaN, both infinities and both extreme finite signs on all six coordinates; each returns no path with zero expansions. Valid searches still use the existing algorithm.
- Strict compilation also required splitting misleading adjacent `if` statements in `space.cpp` (no behavior change) and replacing the at-most-eight-corner support-hull sort with insertion sort to avoid GCC's small-array `std::sort` bounds diagnostic under sanitizer optimization. Existing support/balance checks remain in the native suite.
- Added `RPG_GALLERY_ONLY=npc-deaths`: two actual killing blows, from animation and during a physical reaction, sampled on frames 1, 4, 7…70, plus a late sample at 11.2 seconds. Each sheet contains 25 images. Before/after sets are retained under `rpggame/build/review-deaths/before` and `after`; all four sheets were visually inspected. Both ordinary paths remained collapsed in the baseline too: these images are a regression comparison, while the public-API tests demonstrate the ordering fault.

Visual assessment is provisional **8/10 for continuity and death-state stability in these two views**: the same body collapses, settles and remains down without an observed get-up transition. This does not certify death animation, interpenetration, gore, every body/outfit, terrain or all NPC glitches at that score. The standing fall still has a compact folded pose, and the existing gore/effect presentation needs separate polish. No new authored death animation was introduced.

Reproduce from `theastrattoengine`, after activating the cloud tools:

```sh
cmake -S native -B native/build-linux-x64 -DCMAKE_BUILD_TYPE=Release -DASTRATTO_PLATFORM=linux-x64 -DASTRATTO_WERROR=ON
cmake --build native/build-linux-x64 --parallel 3
native/bin/linux-x64/astratto_selftest
native/bin/linux-x64/astratto_bench native/tests/budgets.txt /tmp/astratto-bench.json
cmake -S native -B native/build-linux-x64-asan -DCMAKE_BUILD_TYPE=RelWithDebInfo -DASTRATTO_PLATFORM=linux-x64-asan -DASTRATTO_SANITIZE=ON -DASTRATTO_WERROR=ON
cmake --build native/build-linux-x64-asan --parallel 3
native/bin/linux-x64-asan/astratto_selftest
```

From `rpggame`, with the display active:

```sh
sh gradlew build pmdMain --init-script tools/unused-code.gradle --no-daemon --console=plain --max-workers=2
RPG_GALLERY_ONLY=npc-deaths sh gradlew gallery -PgalleryDir=build/review-deaths/after --no-daemon --console=plain --max-workers=2
```

## Third pass: remove confirmed unused code without expanding runtime systems

The unused-code audit now covers **all 120 game and 85 engine Java source files**, rather than ten selected game classes. It remains opt-in through a Gradle init script and adds no runtime dependency. Consulted the [current PMD rule definitions online](https://github.com/pmd/pmd/blob/main/pmd-java/src/main/resources/category/java/bestpractices.xml), especially the distinction between unused locals, write-only fields, and private methods. PMD documents its limits around reflection and annotations. Repository searches checked the findings and consumers before removal; public library APIs were retained.

Removed **18 confirmed unused items** (17 initial findings and one helper orphaned by their removal):

- Game: an unused habitat floor local, write-only music timer, prayer event result binding, temporary character box corners, unused door-angle cache, redundant hair-color local, obsolete head/neck render helpers, unused level-up queue, unused action-bar prayer array, and an inventory HUD alias.
- Engine Java: a write-only dog target field, unused decal scratch buffer, write-only batch mode, unused font position array, unused shader matrix buffer, and a discarded voxel-light vector plus its orphaned normalization helper.

The prayer event's `commit` call is retained; only its unused return binding was removed. Music mood debouncing still uses its active `pendingFor` timer. Dogs still attack their sensed threat through `out.attack`; doors still render their physical hinge state. Existing head-unit rendering, facial hair, eyelids and long hair keep their active path. These checks matter because deleting an unused variable's initializer can accidentally delete useful work.

This pass adds no gameplay implementation or public signatures. It removes unused allocations, fields, writes and methods. Both builds and all **27 Java tests** pass, with fallbacks disabled; the engine renderer test executes under software OpenGL. Both complete source scans report **zero violations, errors or suppressions** under the three selected PMD rules. This establishes a clean result for these checks, not universal proof of reachability for public APIs, scripts, assets or dynamically loaded code. No whole-engine rewrite or new framework was introduced.

The broader acceptance combination initially passed **63/65** checks and exposed a sprint fixture dependency: `interfaceChecks` eats food, setting a 1.2-second action cooldown, and `bugs` then tested sprint before the existing action lock had expired. `PlayerController.aiming` blocks sprint during that cooldown. The sprint fixture now waits, bounded to two seconds, for free hands and explicitly asserts readiness before checking a single Shift hold. It uses the existing `idle` helper, retains load and real movement, and does not change controller rules or clear the cooldown artificially. This change is confined to the acceptance harness. An independent headless probe confirmed both behaviors under 0.55 burden: sprint blocked during the cooldown, then a single hold reaches 4.30 m/s against a 1.15 m/s walk after eligibility returns.

The complete focused rerun (`leveling,physical,interfaceChecks,bugs,pause,console`) passes **66/66** checks, including the added readiness precondition and all world-state invariants at the end. It verifies creator/save round-trip, XP milestones and deduplication, physical doors, all 67 glass shards settling, inventory drag/equip, food hotkeys, burden, Lua addons, menu pause, single-hold first-person sprint, respawn/graves, corpse equipment removal, native console and profile isolation. This is a completed focused run with Java fallbacks disabled, not the full acceptance suite. The initial failure log and successful rerun are retained as `/workspace/.onboarding/cleanup-acceptance.log` and `cleanup-acceptance-final.log`.

The existing female body implementation was traced through creator labels, `Appearance`, `Body`, `Skin` and `CharModel`. Its three persisted ids map to Average/Curvy/Slender geometry and exclude the male muscular shading. Keeping those stable ids preserves saves and the GDD's three-build catalog. Existing blink and corpse-eye code was also verified by call-site review. These systems were not reimplemented merely because they appear in gamefixes.

Both runs of the existing `figures` gallery completed: **24 before and 24 after** views, covering both sexes, all three builds and four angles. Visual review found no missing geometry or accessories in the after set. An RGB comparison of all 24 pairs found a maximum channel difference of **5/255** and a worst image's mean absolute channel difference below **0.004/255**. They are not byte-identical images, but the differences are very small; no visible regression was found. This validates preservation of the sampled outfit/builds, not every garment, haircut or animation. Captures, comparison metrics and an after contact sheet are in `rpggame/build/review-cleanup/{before,after,comparison.json,after-figures.png}`. No new gallery runtime code was needed.

Reproduce the complete Java audits:

```sh
# From rpggame
sh gradlew build pmdMain --init-script tools/unused-code.gradle --no-daemon --console=plain --max-workers=2
RPG_ONLY=leveling,physical,interfaceChecks,bugs,pause,console sh gradlew acceptance --no-daemon --console=plain --max-workers=2
# From theastrattoengine
sh gradlew build pmdMain --init-script ../rpggame/tools/unused-code.gradle --no-daemon --console=plain --max-workers=2
```

## Fourth pass: rumors with local hearing and traceable sources

### Research and implementation

Traced `Memory` creation, `Npc.remember`, street gossip, `Brain` conversations, murder alarms, `Law.report`, opinion/fear, and save/load before changing propagation. Six regression cases failed against the original code: repeated deliveries multiplied memories and fear; weak hearsay blocked a better account; distinct incidents without event ids collapsed together; eyewitness upgrades duplicated evidence or reopened reports; alarms crossed walls without hearing checks; and conversations could share news with distant or unloaded NPCs.

References retrieved online:

- [Game Programming Patterns: Event Queue](https://github.com/munificent/game-programming-patterns/blob/master/book/event-queue.markdown): capture event-time context and prevent feedback loops. Memories retain the original incident time and identity through retellings; returning accounts do not become additional incidents.
- [Godot AudioStreamPlayer3D](https://github.com/godotengine/godot-docs/blob/master/classes/class_audiostreamplayer3d.rst): spatial sound has finite range and attenuation. Speech uses head-to-head distance and the existing stealth wall-muffle coefficient, with reduced range for sleeping listeners. This is the game's coarse hearing model, not a new acoustic simulation or Godot dependency.

`Npc.remember` now merges an incident into one canonical memory. Eyewitness knowledge outranks hearsay; stronger hearsay replaces a weaker account; duplicate eyewitness records preserve their reported state. Nonzero event ids identify incidents; legacy memories and deeds without ids use their original time, jurisdiction and detail as well as kind/subject/victim. The existing memory cap and eviction policy remain in use. Loading an old save also merges duplicate records through this same operation.

`AiSystem.share` delegates to `Rumors.tell`, removing its separate propagation implementation. Conversations now use the same distance, wall, loading, confidence and evidence rules as street gossip and alarms, and can pass good deeds too. A teller must actually own the account. Successful deliveries alone update social counters and meeting cooldowns. One alarm delivered to several listeners produces one overheard chat line while each listener receives their own memory.

Optional saved fields record the original known eyewitness and relay count. Missing fields in existing saves have conservative defaults; no witness or unidentified culprit is invented. Retelling preserves original incident time, weakens confidence by the existing 0.7 factor, and never creates reportable evidence. A new firsthand observation can still replace hearsay and become a legal report. Learning an account does not teach the player's name unless the teller knows it. Existing opinion, fear, flight and guard/report systems consume the resulting memories; no global reputation system or additional runtime framework was introduced.

### Verification and visual review

The six failing cases now pass. Thirteen native-backed rumor tests additionally cover relay provenance through JSON, anonymous old records, feedback loops, player-name knowledge, fabricated accounts, good-deed conversations, overheard alarm deduplication, and hearsay versus legal evidence. All **20 game tests** and **20 engine Java tests** pass with Java fallbacks disabled. The game and engine builds succeed. The three-rule unused-code audit remains clean across both main source trees; the audit is opt-in and adds no runtime dependency.

`RPG_GALLERY_ONLY=rumors` exercises the actual two-stage telling and render paths with a staged witness memory. The inspected `rumor-witness.png` and `rumor-relay.png` captures show “I saw it myself” changing to “I heard Eda saw it,” alongside the existing gossip cues. They are retained under `rpggame/build/review-rumors/after`, with the earlier mood/role reference under `before`. Source attribution and text legibility score a provisional **8/10 in these views**. This is a narrow visual assessment: the staged record does not establish murder detection, all NPC layouts, or overall animation quality.

Reproduce from `rpggame`, with a working display and native library:

```sh
sh gradlew build pmdMain --init-script tools/unused-code.gradle --no-daemon --console=plain --max-workers=2
RPG_ONLY=quest,persistence,reactions sh gradlew acceptance --no-daemon --console=plain --max-workers=2
RPG_GALLERY_ONLY=rumors sh gradlew gallery -PgalleryDir=build/review-rumors/after --no-daemon --console=plain --max-workers=2
```

The persistence acceptance section requires the quest/death setup, so its focused selection includes `quest`. An initial incomplete selection was stopped before completion. The next run exposed a harness timing error: it compared memory totals after 30 resumed simulation frames, during which NPCs can legitimately learn new facts (1 saved versus 2 after simulation). The comparison now captures the restored count immediately after load, alongside the existing restored clock/revision snapshots, and still verifies the saved marker memory after simulation. No AI behavior is disabled or memories cleared to satisfy the check. That run was stopped after the failure and the first social checks; neither stopped run is reported as passing. Final rerun results are recorded below.

The completed `quest,persistence,reactions` rerun passes **37/37** checks with Java fallbacks disabled, in 11m 58s under software OpenGL. It covers quest failure and permanent NPC death, restored memory/items/XP/clock, night scheduling and bed ownership, role-specific investigation, greater nighttime suspicion (0.86 versus 0.30 at noon), street rumor and resulting fear/flight, a villager warning a guard about a wolf and the guard taking up the search, and a physical blood-slip followed by recovery. Every world-state invariant holds at the end. The log is `/workspace/.onboarding/rumors-acceptance-final.log`. This is focused integration coverage, not a completed full acceptance suite.

### Applying source updates

Rebuild the native library after pulling these C++ changes; an existing prebuilt library contains the old implementation. From `theastrattoengine`, run `sh gradlew buildNative build`, then from `rpggame` run `sh gradlew build` and `sh gradlew run`. On Windows use `gradlew.bat` in place of `sh gradlew`. JDK 21 and the native toolchain prerequisites are described in `theastrattoengine/README.md`.

## Remaining gamefixes verification

| Area | Existing entry points | Remaining work |
|---|---|---|
| Human movement/animation | `Config`, `PlayerController`, `Gait`, `Rig`, `Gallery` | Review both sexes, armed movement, turns, starts/stops and fatigue beyond this first pass. |
| Voice sex | `audio/Sfx`, `HumanVoice`, `ai/AiSystem` roster | Routing/save handling reviewed and duplicate asset removed. Identify and audition the reported offending male NPC/recording; wrong-sex playback remains unconfirmed. |
| Female silhouettes | `actor/Appearance`, `Body`, `render/CharModel`, `ui/Creator` | All three female builds checked at four angles in the default fitted outfit, alongside the male builds. Other outfits, armor and motion still need review; preserve stable selection ids. |
| Death and balance recovery | `combat/Reactions`, `AiSystem`, native `sydney.cpp` | Release/death ordering fixed; four kill paths, late corpse stability and repeated impacts checked. Broader NPC/terrain/unloading cases and gore/pose polish remain. |
| NPC investigation, hearing, fear and rumor | `Investigation`, `Stealth`, `Rumors`, `Brain`, `Memory` | Incident deduplication, local speech hearing, source provenance and evidence separation now have regression coverage. Broader role/night choices, long chains and varied visual layouts still need review. |
| Animal behavior and physical reactions | `animal/AnimalMind`, `Species`, `AnimalRagdoll`, `Animals` | Verify all species/age profiles, locomotion, reactions and ownership; existing systems are not evidence of polished behavior. |
| Blink/dead eyes | `render/CharModel`, `Skin`, `Gfx` | Review blink timing and corpse-eye appearance in captures. |
| Console | `ui/Console`, engine `DevConsole` | Baseline acceptance confirmed native console and no `sv_cheats`; keep regression checks. |
| Player death/graves | `PlayerDeath`, `law/Undertaker`, `ui/HudFragments` | Existing five-second presentation, choice UI, skull marker/timer and expiry need further visual and edge-case review. |
| Blood/gore/slipping | `combat/Gore`, `Reactions`, `render/Fx` | Audit contact with blood and physical reactions, evidence lifetime and legibility. |
| Honor/fence/jewellery | `law/Honor`, `econ/Economy`, `item/Equipment` | Existing systems need the `honor` functional section and transaction/security review. |
| Pause menu | `ui/HudFragments`, `Windows`, `Gallery` | Pause behavior passes; composition, focus, navigation and visual quality need review. |
| Materials/core GDD systems | `world/Blocks`, native renderer, GDD §4.3 roadmap | Run the 64×64 material audit and build a requirement-by-requirement GDD ledger. This broad backlog is not completed by the locomotion pass. |
