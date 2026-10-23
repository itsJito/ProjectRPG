# Combat, perception and presentation polish

This pass follows the current task list and `GDD.md` sections 2.3 (physical action combat and derived level), 2.4 (equipment), 3.3 (perception, memory and shared NPC behaviors), 3.5 (Honor) and 3.9 (animal ecology). The user's rule overrides NPC-training passages: only the session player gains skill XP; NPCs and animals retain their configured skills and derive Level from those skills. Existing save keys and the level formula remain compatible; displayed “Combat Level” labels become “Level”.

## Changes and causes

- Killing an already knocked-down animal was releasing its body and creating another upright one. Death now converts the existing fall to a corpse and disables recovery. Dead animation fallbacks stop gait, wing beats, tail motion, head tracking and rearing. Rigid bodies still settle physically.
- Delete-profile confirmation returned before drawing the custom cursor. The confirmation now draws the same footer/cursor as the other title states.
- Third-person resting weapon axes pointed back toward the character. The carry direction now points up and ahead. First-person orientation stays relative to the camera.
- Severed human heads previously drew only skin segments, bypassing eyelids and head-attached details. They now use the shared head-detail renderer with closed eyes. Animal heads also have closed-eye variants, including detached heads and physical corpses. Shared, cached cut surfaces add torn muscle, a dark rim and bone to detached heads/limbs and animation stumps.
- A Brute could retain `FIGHT` without a target because assignment depended on changing the goal enum. Each fight decision now assigns its perceived threat even when the goal stays the same.
- Honor inherited an always-active HUD predicate. It now wakes only for an Honor event, fades in over 0.2 seconds and finishes its 0.4-second fade-out within six seconds of the last change. Clamped/duplicate events that change no Honor do not emit that event.
- Melee timing now carries unused frame time through wind-up, contact and recovery boundaries. Light wind-up is 8% shorter, light recovery 12% shorter, attacker contact pause 45 ms, and the recovery input buffer 200 ms. Heavy commitment, stamina, weapon/skill cadence, physical sweeps and active defense remain authoritative. Hit feedback follows positive HP damage; kill feedback follows the kill resolver. White/red crosshair markers and a brief, low-opacity contact flash complement existing blood, particles and camera response.
- Free arms were swinging with the same-side leg. They now counter-swing; a smooth bounded response replaces the hard-clipped arm target. Foot placement remains driven by velocity and ground contact. Bow raises also blend rather than snap.
- Bows keep their authored inventory geometry while held strings join both limb tips to the actual drawing hand. The equipped arrow is nocked with its own shaft, head and fletching; flying arrows/bolts share that geometry. Crossbows have a raised aim on RMB, a reachable support grip, a loaded bolt and reload feedback. Bow/crossbow reticles show draw/reload state. Removed the obsolete generic weapon-mesh switch and unused procedural face mesh; their replacement paths already use the authored item models and painted skins.
- The old swept sine bow tone is replaced by three original damped plucked-string release variants for each ranged weapon type, with body/snap layers. Existing credited CC0 recordings supply draw creaks, fly-bys and material impacts. Banks are generated/loaded once; no new audio dependencies or unlicensed downloads. Audio quality still benefits from listening and mix review on the target setup.
- Guards question visible blood on either the player or a loaded human NPC, watch and approach, and stop when the evidence is no longer visible. Blood alone does not create a charge, culprit identity or crime memory. Existing witnessed crimes and legal evidence keep their separate rules.
- Combat tactics use visible allies fighting the same foe and actual incoming projectiles/recent ranged wounds. Isolated, reloading or hurt combatants can seek two-height occluding cover; supported fighters can choose a separated flank and advance into range. Short commitments, occupancy checks, terrain revalidation and stuck/expiry handling prevent permanent tactical goals. Movement uses the existing native navigator. After losing sight, movement and facing use a cached source/last-seen point; attacks require visibility. Guard arrest dialogue takes priority over tactical movement. Speech and the existing AI inspector expose the choice.
- The Map tab and default `M` key show the entire current 128 × 128 m playable area, using the minimap's existing terrain texture and incremental edits. Landmarks, the player, graves, visible nearby people and a clickable destination marker are shown. Magic moves to `B`; F7 opens Map. The map does not reveal hidden remote NPC positions.

## Architecture references

Reviewed online source references before implementing the new tactics and audio:

- [gdx-ai Hide behavior](https://github.com/libgdx/gdx-ai/blob/master/gdx-ai/src/com/badlogic/gdx/ai/steer/behaviors/Hide.java): score cover locations, handle missing cover and use existing movement/arrival behavior. Here cover is tested against actual voxel occlusion and routed by the engine; no second steering/pathfinding stack is added.
- [Faust physical models, Karplus–Strong string](https://github.com/grame-cncm/faustlibraries/blob/master/physmodels.lib): excitation, a delay line and dissipative filtering produce a string release instead of a pitched UI-like tone. The Java release generator is small original code, rendered once into the existing mixer banks; it does not embed Faust or copy its implementation.
- [Existing MSVC capture and narrowing review](POLISH_REVIEW.md#windows-build-correction): native strict-warning fixes remain in place. The root Windows workflow now also builds the Java game and runs its tests, and triggers on game/engine changes.

## Verification

The new regressions first reproduced animal-body replacement, the always-visible Honor toast, stale Brute targeting, discarded melee phase time and same-side arm swing. Additional tests cover positive-damage/kill feedback, visible blood without guilt, bloody NPCs, occlusion, ally perception, cover destruction, actual native navigation into cover, ranged sound headroom and two-hand carry/aim geometry.

Linux verification passes all **58 game tests and 22 engine Java tests**, both Gradle builds and the unused-code audits. Native sources are unchanged in this pass; the expanded Windows workflow rebuilds them under MSVC and runs the existing native self-test before Java checks.

The pushed implementation at `e03cb79` also passed [Windows run 39928232138](https://github.com/itsJito/ProjectRPG/actions/runs/39928232138): MSVC native compilation/self-test and the Java game build/regressions completed successfully. This is a Windows build check, not a Windows rendering or listening session. A final small regression also checks that leaving combat lowers an NPC's crossbow and that both body builds counter-swing their arms.

The real renderer captures confirm the full map, Honor gain/loss, forward axe carry, bow/crossbow ammunition, white/red markers, closed severed-head eyes, the underside cut surface, and a visible cursor during confirmation and after deleting a fixture profile. No runtime assets, third-party libraries or duplicate pathfinder were added.

Run from `rpggame` with JDK 21 and the native toolchain:

```sh
sh gradlew build pmdMain --init-script tools/unused-code.gradle --no-daemon --console=plain --max-workers=2
RPG_GALLERY_ONLY=field-polish sh gradlew gallery -PgalleryDir=build/review-field --no-daemon --console=plain --max-workers=2
RPG_GALLERY_ONLY=locomotion sh gradlew gallery -PgalleryDir=build/review-motion --no-daemon --console=plain --max-workers=2
```

Use `gradlew.bat` on Windows. Gallery review needs a display/OpenGL context; automated Java regressions use the native engine without opening a window. Gallery profiles are isolated under `build/gallery-profiles`, with the real profile root restored afterward. The field gallery captures map, Honor changes, first/third-person weapons, hit/kill markers, severed-head eyes/cut and profile deletion. Captures settle the renderer/camera before readback.

This is a bounded implementation of the current list. It does not establish completion of every system in the full production GDD, every tactical situation or target-hardware animation/audio quality. No source-only audit can prove that an entire game contains zero dead code; the changed paths receive call-site review and the existing unused-code audit.
