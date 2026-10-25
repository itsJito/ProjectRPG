# 0.1.5 maintenance review

This pass addresses the reported polish and gameplay faults without adding residents, skill progression for NPCs or a second simulation for existing systems. Earlier patch notes, gamefixes and GDD text are preserved byte for byte; the maintenance notes are appended.

## Faults and resulting behavior

| Report | Cause and correction |
|---|---|
| Foliage ignores bodies | Cached foliage meshes previously received wind only. Root-weighted GPU bending now reuses nearby actor proxies, with at most eight local contacts per chunk. It recovers as bodies leave, without per-plant rigid bodies or per-frame mesh rebuilds. |
| Humans cannot open building doors | New building doors were not included in the original hand-maintained door list. Every added door now registers once for opening and hinge read-back. Human waypoint visibility treats unlocked doors as operable passages. |
| Doors stop on one side | Open hinges had one-sided limits. They now allow ±3.05 radians, initially swing away from the opener and close toward zero using each actual signed angle. Locked leaves remain latched. |
| Feet do not turn | The correction threshold exceeded normal foot travel during a half-turn. Corrective swings also entered the ordinary stride-completion branch. Both faults are corrected; stationary turns lift, place and settle both feet as needed. |
| Some trees cannot be chopped | The seven authored wild-wood trees had geometry without gathering nodes. Appended nodes retain established fixture IDs and use the correct wood, actual height, gathering gates, yield, stump and regrowth. |
| Damage after death restarts combat | Existing native receivers, active volumes and queued volumes retained the previous life’s owner. A native disown command retires attribution without extinguishing fire. Old missiles land with real ammunition preserved, bleed attribution is retired and player-lit fires save their retired state. |
| Light flicker | Fire lighting selected arbitrary first sensors, abrupt source changes had no fade and fire intensity varied rapidly. Sources are grouped, retained with hysteresis and smoothly faded. Selection runs at chemistry cadence or after camera travel; body-source positions still follow each frame. Shadow noise is fixed when TAA is disabled. |
| Pickpocket transfer is invisible | The selected-item lift was deferred work inside a paused inventory. Choosing an item now closes the view while the interruptible action runs, then reopens it on success, highlights the actual received stack and reports the item. Capacity is checked with stolen provenance already applied; success is published after the transfer. |
| Human shadows are missing | Ordinary local lights were not flagged for the old body-occlusion path. The first two nearby tile lights now apply bounded capsule occlusion at every quality level. Standing, sleeping and reacting humans submit suitable proxies. Sunlight retains actual model silhouettes; a center blocker sample prevents thin shadows being skipped. |
| Targets cannot be hit in doorways | Full voxel cells and duplicate voxel door geometry did not follow the rigid leaves. Rays, sight and weapon sweeps now intersect the rendered rigid pose. An open door’s interaction volume cannot intercept the person standing in its clear aperture. |
| Conjured allies abandon the caster | Summons inherited hunter needs and utility scoring. Their dedicated FOLLOW/FIGHT policy follows behind the moving caster and protects nearby, with a leash that returns them from distant opponents. It does not run occupational, social or looting duties. |
| Morphbeast leaves first person active | The controller enforces third person throughout the effect, rejects camera toggles during it and restores the saved preference afterward. Saving retains the preferred view rather than the temporarily forced one. |
| Inventories are too wide | Human/player base grids are now 4×10, preserving forty cells. Legacy layouts transpose coordinates and item orientation rather than greedily repacking. Nested bag grids, quantities, charges, ownership and stolen provenance stay intact. Bags fit alongside the carried grid. |
| Some routines do not resume | Goal changes and blocked routes could retain an unfinished task network. These now cancel it before replanning. Unlocked-door routing is consistent, and fatigue recovers only when actually asleep rather than while travelling to a bed. |

## Attribution boundary

The native command clears attribution in sensors and both active and pending elemental volumes. It does not disable the actor ID for future attacks. A successful native-batch sequence acknowledges when queued retirement has crossed the engine boundary, including paused pumps; Java masks only the stale snapshot before that acknowledgement. Immediate saves apply the same rule, and loading clears pending acknowledgements.

The native checks caught an additional fault: passive wind and older unowned fire could erase a new attack’s source. Attribution is now updated by a damaging ignition or charge. Old unowned flame does not overwrite the owner of a fresh attack on an already burning receiver. A dedicated game regression attacks immediately after respawn to guard against permanently blacklisting that actor.

## Visual review

The presentation copies below come from actual OpenGL engine screenshots. Original PNG captures remain in the execution evidence directory. Prior release captures were used as references; changed turning, passage and contact motions were sampled on threes, at frames 1, 4, 7… with 60 simulation steps per second. These are visual inspections, not an objective numerical art score.

| Check | Captured evidence |
|---|---|
| Tall carried grid, equipment and bags fit | [inventory](release-015/vertical-inventory.jpg) |
| Actual selected theft, before and after | [pockets before](release-015/inspect-pockets.jpg), [item received](release-015/pocket-item-received.jpg) |
| Foot correction through a half-turn | [on threes](release-015/turn-in-place.jpg) |
| Native NPC navigation opens the barn | [passage on threes](release-015/npc-opens-barn.jpg) |
| Hinge travel from both sides | [physical leaves](release-015/door-push-both-directions.jpg) |
| Rooted foliage contact and recovery | [before](release-015/foliage-before-contact.jpg), [contact on threes](release-015/foliage-contact.jpg), [released](release-015/foliage-released.jpg) |
| Dynamic human shadows | [daylight](release-015/human-day-shadows.jpg), [fire at night](release-015/human-fire-shadows.jpg) |

The turning review confirmed that feet reposition instead of staying crossed. The barn sequence follows the real agent route and hinges. The foliage sequence uses the same shared model shader as generated patches, with the root anchored and the upper growth displaced by body contact. The inventory review includes both equipped bags and the changed pocket container, with the stolen arrows visible in the receiving grid.

## Verification

The final Linux checks passed. The Windows workflow builds the engine with MSVC warnings as errors, runs game regressions and verifies that an unchanged runtime dependency invocation leaves the native build up to date.

- Game: **159 tests passed**, full build and distribution installation. The focused attribution/fire/persistence suite also passed before the final full run.
- Engine: **29 Java tests passed**, including actual OpenGL rendering and successful native-batch acknowledgement. Native strict warnings-as-errors self-test: **121 passed, zero failed**.
- Native: AddressSanitizer/UndefinedBehaviorSanitizer with leak detection: **121 passed, zero failed**, with no sanitizer diagnostics.
- Scoped Java unused-code audit: **zero findings** for local variables, private fields and private methods in game and engine. This does not claim proof of every unreachable code path.
- Unchanged runtime dependency check: `:theastrattoengine:jar classes installDist` left `buildNative` **UP-TO-DATE**.
- Markdown prefix checks and `git diff --check` passed: the existing notes and GDD remain intact, with new text appended.

Run `sh rpggame/gradlew -p rpggame build` and `sh theastrattoengine/gradlew -p theastrattoengine build`. The unused-code audit adds `pmdMain pmdTest --init-script tools/unused-code.gradle` from the game project, or `--init-script ../rpggame/tools/unused-code.gradle` from the engine. Rendering checks need an OpenGL 4.5 display.

After `installDist`, capture this review with `RPG_GALLERY_ONLY=maintenance`, `RPG_REVIEW_ONLY=ui`, `motion`, `foliage` or `shadows`, and application arguments `--gallery --hidden --shots <directory>`.

## Limits

The target remains i5-3570 / GTX 660 / 8 GB, 1080p at **30 FPS**. The cloud uses llvmpipe OpenGL 4.5 and cannot certify that target. Local capsule shadows and foliage contacts are bounded approximations near the camera; sunlight uses model shadow maps. Ground foliage remains passable and bends rather than acting as a solid obstacle. The routine fixes cover identified interruption and access faults, not a claim that every possible future schedule or map has been exhaustively validated.

## Primary references

- [Box2D revolute-joint definition](https://github.com/erincatto/box2d/blob/main/include/box2d/types.h): signed lower/upper angular limits, motor velocity and bounded torque. The game continues to use its own Impulsis joints.
- [Khronos glUniform reference](https://github.com/KhronosGroup/OpenGL-Refpages/blob/main/gl4/glUniform.xml): matching vector component types for GLSL uniform arrays. Foliage contact uniforms use vec4 uploads, without CPU particle or foliage readback.
