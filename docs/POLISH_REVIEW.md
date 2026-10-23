# Current polish review

The user's replacement task list and `GDD.md` are the requirements for this work. The earlier `gamefixes.md` checklist is historical. The user's rule that only the player gains skill/combat levels takes precedence over the GDD's references to NPC training. Animals retain species skill profiles and derive their combat level from those skills.

## Architecture and references

Reviewed the actual paths through player input, procedural posing, item models, native route following, combat resolution/XP, law, NPC memory/decisions, respawn, saving, HUD fragments and Gradle packaging before changing them. Gameplay policy remains in the game; shared matrix construction and route following remain in the engine. Existing save/load, UI actions, perception, memory caps and progression services are reused.

References retrieved online:

- [GLM matrix transforms](https://github.com/g-truc/glm/blob/master/glm/ext/matrix_transform.inl): normalized perpendicular axes and cross-product order define a rigid, consistently handed frame. The engine's new model-frame operation additionally handles zero/parallel references; it is not a copy of GLM's camera API.
- [Godot NavigationAgent3D](https://github.com/godotengine/godot-docs/blob/master/classes/class_navigationagent3d.rst): waypoint reach thresholds and overshoot affect path continuity. This fix consumes reached waypoints before steering during the same frame; it does not replace A* or arbitrarily enlarge reach thresholds.
- [Game Programming Patterns: State](https://github.com/munificent/game-programming-patterns/blob/master/book/state.markdown) and [Event Queue](https://github.com/munificent/game-programming-patterns/blob/master/book/event-queue.markdown): distinguish legal disposition from historical knowledge, preserve incident context, and avoid replaying old consequences as new events.
- Gradle's version-matched [Task dependencies](https://github.com/gradle/gradle/blob/v8.14.3/subprojects/core-api/src/main/java/org/gradle/api/Task.java) and [TaskInputs](https://github.com/gradle/gradle/blob/v8.14.3/subprojects/core-api/src/main/java/org/gradle/api/tasks/TaskInputs.java): explicit dependencies establish packaging order; declared inputs/outputs enable incremental work.

These references inform the design. Repository call sites, executable regressions and actual captures establish what this implementation does.

## Changes

### Justice after respawn, without erasing consequences

Respawn previously restored the player but left bounties and some hunts, investigations, fear and combat state behind. `Law.playerRespawned` now clears player charges across jurisdictions, resistance/halt state and immediate pursuits. It cancels attacks from involved NPCs and discards their pending projectiles. Unrelated fights and other offenders' charges remain intact.

Memories have an optional persisted `settled` disposition. Old accounts remain memories, continue to affect opinion, and can be retold, but cannot create another warrant, restart immediate fear or make guards resume watching an already settled case. Closure survives retelling, stronger copies of the same incident and JSON. A new incident remains actionable, including immediately after respawn. Memory eviction treats closed cases as ordinary forgettable history.

Personal victims can still refuse trade, and dialogue acknowledges the remembered act. Shopkeepers stop refusing service solely for settled gossip about someone else; a weaker new account cannot hide their own grievance. Honor, ownership/stolen tags, bodies, quest consequences and the event history are preserved. Six native-backed justice tests cover these distinctions; three original cases failed before implementation.

### Input and progression

Near-ready melee releases have one 150 ms buffer. A release just before recovery ends executes once when ready. Expired clicks, guarding, dodging, equipment changes, menus and respawn cancel the pending input. A held button from before death cannot become an attack on release after respawn. Both the swallowed recovery click and stale post-respawn release were reproduced with failing tests before correction.

Fighting Styles is removed from the character sheet, player state and save writer/reader. Older saves' extra style key is ignored. The dead style names and allocation helper are removed. Unused public Faith/mana regeneration settings and misleading comments are removed: the live resource loop already restores mana one point per minute without passively restoring Faith, as the GDD requires. No script, save or reflection consumer uses those settings. Real melee damage splits the existing primary XP budget between Attack and Strength; hostile HP damage trains the recipient's Defense, alongside existing guard/parry rewards. Health, ranged and magic retain their own action paths. Only the actual session player can receive XP; NPCs, animals and plain-Actor training dummies retain their configured skills.

Animal windup/recovery now honor their configured Attack handling modifier; their active contact window retains species timing. Natural attack HP damage uses Strength, rather than accidentally reading Attack through a misleading alias. The alias is removed. A regression demonstrates that changing an animal's configured Attack changes handling while actual XP awards cannot change its levels.

### Investigation priorities

The integration run exposed a thief who ignored a credible nighttime sound while looting a grave: investigation scored 0.80, but a 0.74 looting goal survived through the routine-goal inertia margin. Investigation now bypasses that margin when it is the highest-scoring choice, as fight/flight already did. Lower-scoring suspicions still lose to stronger needs or threats. A native-backed test recreates the exposed grave and fresh sound, failed on the original rule, and now verifies both interruption and the thief's hide plan. Existing role-specific investigation choices and perception remain in use.

### Movement and path continuity

Player walk tuning is 2.45 m/s (previously 2.1), sprint 8.2 m/s (7.8). The shared walk/run blend thresholds accommodate the brisker walk. The configured walk still has double support and reachable planted feet; running has flight. Purposeful NPC walking uses the same setting, and wandering uses 80% of it.

The native route follower previously produced a zero-input frame when consuming a reached intermediate waypoint. A public-API test isolates routing from mover inertia and reproduces a stationary frame at a height change. It now consumes reached points and steers toward the next point in the same frame. The test reaches the raised destination without intermediate stationary frames. Path generation, body clearance, turn braking, door handling and final arrival remain in the existing follower.

### First-person items and pause rendering

The shared matrix frame keeps the item's local +Y along its authored direction and uses a perpendicular +X reference. A safe fallback prevents invalid frames for parallel/zero references. Engine tests sample 2,701 yaw/pitch combinations and degenerate references, checking scale, orthogonality, positive handedness, grip position and item direction.

First-person blade directions now use camera right/up/forward together, including vertical aim and mirrored hand sides. Crossbows follow the aim rather than a sword's upward carry. Bows use the support hand, with the other hand drawing back from its actual grip; the string faces the archer. Idle/drawn bow grips are checked against the first-person horizontal view. Review captures exposed an out-of-view bow target that direction-only checks missed; it was corrected before acceptance of the visual change. Bows now share a continuous tapered model at 64 voxels/metre instead of a doubled coarse segmented shape; the obsolete builder is removed. Their support-hand assignment also remains consistent when Sydney owns the ragdoll.

Paused rendering now accepts a zero rig timestep rather than silently advancing by 0.0001 seconds per rendered frame. A reproduced failing test holds a moving pose over 600 paused renders and verifies every segment matrix remains unchanged after the fix.

### Menus and materials

The pause menu is a large centered presentation with five readable actions, player/time/level/Honor/equipment information, mouse selection, numbered shortcuts, Up/Down focus, Enter and Escape. It reuses the existing menu actions and pause state rather than adding another gameplay controller. Scene hotkeys do not open panels behind the pause menu. The character sheet separates condition, Honor and controls, and splits the long weapon readout so it does not overlap the controls.

The material audit verifies all 47 distinct, nonempty RGBA layers are 64×64, every face binding of the 49 block definitions is valid, and physical/rendered solidity agrees with explicit treatment of moving doors. It exports a nearest-neighbor contact sheet, hashes, color/alpha ranges and block bindings under `rpggame/build/reports/materials`. All layers were visually inspected in the contact sheet. This establishes texture density and binding/content checks; weather, mip transitions, repeated seams and every material under world lighting still need scene review.

### Native build freshness

Packaging previously depended on `buildNative` only when the binary directory was absent. Existing prebuilt libraries could therefore hide changed C++ sources. Engine resources now always depend on the incremental native build. Native source/header/test/module inputs, build scripts and platform are declared. A real build ran all native checks; subsequent game/gallery builds report `buildNative UP-TO-DATE` with unchanged sources. The toolchain requirements remain documented in the engine README.

## Windows build correction

The user's Windows rebuild exposed MSVC C3493 in `pathfinder.cpp`: the coordinate predicate referenced an enclosing floating-point `constexpr` through a captureless lambda. The limit now lives inside that lambda, so no capture is needed. Coordinate bounds and NaN/infinity rejection are unchanged. This addresses the reported compiler error and its cascading C2064 diagnostics; see Microsoft's [C3493 reference](https://github.com/MicrosoftDocs/cpp-docs/blob/main/docs/error-messages/compiler-errors-2/compiler-error-c3493.md).

The Windows compiler-discovery branch also had unconditional `exit` commands after `&`, outside the preceding `if`. Missing-tool errors now use proper conditional blocks, discovery requires the C++ tools component, and a failed `vcvars64.bat` setup stops the build. Normal builds continue to compile changed native sources rather than silently packaging an older DLL.

Strict Linux release and ASan/UBSan builds pass all 110 native checks, including nonfinite/extreme path-coordinate rejection. Engine/game Gradle builds succeed. MSVC and Windows command execution are unavailable in this Linux environment; a complete Windows build has not been claimed as verified here. After updating, run `.\gradlew.bat run --no-daemon` from `rpggame`.

## Verification and artifacts

- Game and engine Java builds succeed. Current suite: 44 game tests and 22 engine Java tests.
- Strict native release and ASan/UBSan configurations pass 110/110 checks without sanitizer findings. The eight CPU benchmarks pass their configured budgets. These are CPU checks, not target GPU measurements.
- PMD's unused local/private field/private method rules cover all 205 main Java sources. No new runtime dependency or public C ABI change is required.
- Visual artifacts are retained under `rpggame/build/review-polish`: character and first-person comparisons, pause before/after, and male/female locomotion sheets. Material reports are under `build/reports/materials`. Generated artifacts are not source dependencies.
- The final focused `reactions,pause` integration run passes **25/25** checks, including differing night investigation plans, louder suspicion at night, witness-to-rumor provenance, fear/escape, predator warning and guard pursuit, physical fall/recovery, pause/resume and all final world-state invariants. The exposed-grave regression separately verifies the exact competing-loot condition found in the broader run. An initial focused selection omitted earlier skill training; a second selection omitted the interaction section's shed tool pickup. Both were stopped after those fixture prerequisites were identified and are not reported as passing runs.

The broader combined run completed **159/161** checks: all skill-action/XP checks, physical interactions, combat, arrest, quest, save/load and final invariants passed. Its two failures were the investigation/loot priority defect described above, and the secured-grave collection assertion while the guard retained an unrelated search from earlier combat evidence. The corrected investigation rule passes both its regression and the fresh runtime run. A native-backed unit test additionally drives an available guard through the actual route, pickup and custody transfer while preserving the grave owner. A fresh `death,honor` run passes **27/27**, including the actual guard pickup, transport, Undertaker custody and town-fee redemption. The combined run is retained as a failed run, not represented as fully green; competing emergencies can delay collection and the harness still needs better isolation between those scenarios.

Review of the fence assertion also exposed two false-positive opportunities: it was attempting an ordinary shop sale from the fence's location, and could select a clean ring of the same definition. It now locates the stolen stack in the actual carried grid, approaches the ordinary merchant, requires the stolen-goods rejection, then approaches the fence and verifies the bought stock still has its stolen tag. The corrected standalone `honor` run passes **19/19**, including final invariants. The earlier 27-check run predates that assertion correction; the 19-check run verifies the corrected version.

From `rpggame`, with JDK 21, the native toolchain and a working display:

```sh
sh gradlew build pmdMain --init-script tools/unused-code.gradle --no-daemon --console=plain --max-workers=2
RPG_ONLY=reactions,pause sh gradlew acceptance --no-daemon --console=plain --max-workers=2
RPG_ONLY=death,honor sh gradlew acceptance --no-daemon --console=plain --max-workers=2
RPG_ONLY=honor sh gradlew acceptance --no-daemon --console=plain --max-workers=2
RPG_GALLERY_ONLY=polish sh gradlew gallery -PgalleryDir=build/review-polish/final --no-daemon --console=plain --max-workers=2
RPG_GALLERY_ONLY=pause sh gradlew gallery -PgalleryDir=build/review-polish/pause-after --no-daemon --console=plain --max-workers=2
```

Run the engine audit from `theastrattoengine` with `sh gradlew build pmdMain --init-script ../rpggame/tools/unused-code.gradle --no-daemon --console=plain --max-workers=2`.

Windows uses `gradlew.bat` instead of `sh gradlew`. The managed environment activates tools with `/workspace/.onboarding/env.sh`. Integration runs may set `RPG_RENDER_SCALE=0.5` for software rendering; simulation and UI retain the same paths. Normal gallery captures are reviewed separately. `RPG_GALLERY_ONLY=locomotion` captures unarmed walk/jog/sprint sheets; add `-Pfemale` for the female figure.

## Remaining design work

The complete GDD is a broader production backlog. These edits do not establish implementation of all 85 human subtypes, every estate/governance/quest system, or complete species behavior polish. Current investigation choices, night suspicion, rumor provenance, fear, Honor/fence, jewellery and Undertaker paths already have implementations; functional checks and further visual/edge-case review must determine what each still needs. Do not add duplicate versions merely because a system appears on the task list.

The male/female walk, jog and sprint contact sheets, captured both with the starter weapon and with empty hands, show different stride lengths, support/flight and bent running arms, but are not final animation sign-off. The 12 first-person captures cover sword, axe, bow and crossbow at three pitches; a further capture holds a drawn bow. Blade/crossbow orientation stays consistent through pitch, and the bow is visible at the left edge. Bows still need more draw/string deformation and framing work across body types. Character and pause captures show readable, non-overlapping panels at the review resolution.

Further movement review should cover sharp turns, starts/stops, slopes, injuries, fatigue and weapons across body/outfit combinations. Path continuity at one height change does not certify every doorway, crowd or streamed region. Death/release ordering remains covered by the native and game death regressions; broader terrain/unloading and corpse presentation still warrant review. First-person review must extend beyond the sampled families and poses. Per-species social, avoidance, feeding and predator/prey decisions need their own targeted comparisons before broader AI changes.
