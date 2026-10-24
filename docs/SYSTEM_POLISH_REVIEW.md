# System polish and regression review

This pass continues the uploaded work list and the GDD. The user's later decisions take precedence: tap Attack lowers a carried body; holding Attack and releasing throws it. Armor reduces the maximum damage of unblocked hits. NPCs and animals retain fixed skill profiles; only the player earns XP. Update the game and engine together because traversal and carrying use new native flags.

## Changes and ownership

- One `BodyCarry` path handles human ragdolls and articulated animal carcasses. Pickup, carrying, lowering, interruption and throwing preserve the original corpse and its items. A carried body has one owner. Weapons, guarding, dodging and traversal are unavailable while carrying. Saving releases held bodies and cancels traversal before serialization.
- Guards use the same carry path to bring actual city corpses to a living undertaker's post. Claims prevent two collectors taking the same body. Hostile encounters interrupt the delivery. Looting and aid use short crouch/reach poses; transfers consume or move real inventory items at contact.
- `Traversal` senses nearby structural voxel faces, ranks clear ledges and checks character occupancy. It owns reach, hang, mantle and exit movement, contacts hands/feet with the wall and restores entry momentum. Level 1 cannot parkour; Level 5 reaches two-block ledges, with higher fixed/player Agility extending reach. Gates, fragile glass and occupied/obstructed landings are rejected. Native agents defer large traversal jumps to this shared animation system. The obsolete checkpoint/teleport XP route was removed.
- Native character volumes resolve overlaps after parallel world movement, using a sorted sweep broad phase and ordered separation. Posed movement and kinematic ragdolls explicitly suspend automatic steering/gravity/controller updates, then restore ordinary simulation on exit.
- Geometry bounds now determine furniture focus. Unplaced props cannot be focused, and a low counter cannot swallow the headspace of a person behind it. Rear sight obeys the cone even at close range; hearing remains separate.
- Bandits return to the wilderness and guards respect their jurisdiction for routine pursuit, while recent self-defense remains valid across a boundary. Friendly helpers can share real food with wounded players despite their separate faction identifiers. Fed familiar animals can groom/play; needs, fear, night routines and social ties decide when.
- Bounty payment also lands the payer's existing projectiles, closing a loophole that immediately restarted the fight. Incarcerated actors are excluded from combat targets/damage. Sleeping through a sentence fades out/in and releases the player inside the cell before the gate opens. Personal memories remain distinct from a settled legal case.
- Reported NPC crimes can produce physical one-by-two bounty scrolls. Markers use the watch's last known position, rather than tracking the fugitive. Rewards require a player-confirmed kill, a valid unresolved warrant and actual watch funds; duplicate scrolls cannot pay twice. Live capture contracts are not added by this change.
- Physical contact remains authoritative. Melee now sweeps the complete blade segment, respects aim pitch and resolves a small target before a later floor collision. Maximum damage uses the documented effective skill/bonus formula, armor absorption and uniform inclusive integer roll. The player zero-to-one exception applies only to positive maxima. Spells use fixed base damage and equipped Magic Damage Bonus, without a second skill damage multiplier. Obsolete scaling/finisher helpers and the write-only arm-visibility flag were removed and the GDD's conflicting formula text updated.

## Presentation and responsiveness

Settings have Display, Audio, Controls and Accessibility pages, persistent machine preferences, reset, immediate application and conflict-swapping keyboard/mouse bindings. Audio controls operate the actual native mixer buses; output follows the system's default stereo device. Quicksave/load moved to F5/F6, so crouching backward cannot save. These actions are suppressed while an interface or transition captures input.

Item models are immutable once published. A single low-priority icon worker performs CPU rasterization, while the render thread uploads at most one completed icon per frame. Skill guide pages and dialogue wrapping are cached. No worker issues OpenGL calls. The worker is stopped when leaving a session.

Follow-up Chatboxes retain NPC identity and the actual animated head. Long dialogue/reply lists scroll. Wanted status sits below the minimap and invalidates when a legal event changes it, including while paused. Crouch is now an anatomical bent stance at approximately 76% standing height. Idle third-person looking turns the head without forcing the body to rotate. First-person bare guard blends up and uses camera-space shoulder placement/perspective compensation, preserving bone lengths and world collision rules.

Frame review exposed incorrect carried-corpse rendering: the physics spine was horizontal, but rendering used standing yaw for the shoulder frame. Rendering now derives that frame from the actual shoulder points. Existing equipment remains on the original corpse.

## Recorded audio and references

Bow releases are three unchanged 44.1 kHz mono recordings from [0 A.D.](https://github.com/0ad/0ad/tree/master/binaries/data/mods/public/audio/attack/weapon), credited to Wildfire Games under CC BY-SA 3.0. Glass breaking is the unchanged Minetest recording credited to [cmusounddesign](https://github.com/minetest/minetest_game/blob/master/mods/default/README.md#sounds), CC BY 3.0. Per-file sources, attribution and license links ship in `sounds/CREDITS.txt`; the full bow license ships with the assets. Format/decode and provenance were checked. These are recordings, not synthesized replacements; subjective listening quality still needs a real play session.

Implementation references consulted:

- [Godot animation state machines and blending](https://github.com/godotengine/godot-docs/blob/master/tutorials/animation/animation_tree.rst): explicit transitions and blended contacts.
- [Jolt character collision sample](https://github.com/jrouwe/JoltPhysics/blob/master/Samples/Tests/Character/CharacterVirtualTest.cpp): character occupancy and caller-controlled movement.
- [Bullet sweep broad phase](https://github.com/bulletphysics/bullet3/blob/master/src/BulletCollision/BroadphaseCollision/btAxisSweep3Internal.h): prune distant character pairs before overlap resolution.

These informed the implementation; no gameplay framework was imported.

## Quality assurance

Linux game build/distributions and all 107 game tests pass. The engine build and all 22 Java tests pass, including software-OpenGL rendering, with no skips. Native strict and Address/UndefinedBehavior sanitizer runs each pass 113 checks. Scoped PMD unused-code checks pass for game/engine main and test source sets. The Java regressions exercise actual native services, including low duck contacts before the floor, wall obstruction, territory retaliation, testimony/reporting, food transfer, physical guard delivery, inmate immunity, scroll provenance/payment, movement, traversal occupancy, fixed NPC skills, carry orientation and save ownership. PMD checks unused locals, private fields and private methods in both main and test source sets. This is a scoped unused-code audit, not a proof of reachability for every public API in the repository.

The real renderer captured before references and animation frames 1, 4, 7, 10, etc. The reviewed JPEG exports are in [system-review](system-review). The prior crouch/loot references remain in [session-review](session-review). Current captures include settings, persistent head portrait, crouch, loot reach, wall traversal, pickup/carry/throw and jail wake. Iteration corrected fist framing, corpse orientation and leg/hand contacts. Art ratings are subjective; the captures document the actual result rather than guaranteeing a numerical score.

One cloud sample of HUD construction (first/worst of 24 frames) measured Inventory 5.11 ms, Skills 11.00 ms and Character 13.66 ms. These timings exclude world rendering and are diagnostics, not complete frame times or a target-hardware benchmark. Warm-cache first-frame phase measurements were about 0.34–0.49 seconds, excluding Gradle and session loading. The existing runtime-only native build separation is retained. The installed distribution also passed a timed launch smoke check without Gradle: native services 0.01 s, devmap 0.01 s, renderer/UI 0.36 s and first rendered frame 0.39 s. Startup help now names F5/F6 save/load instead of the obsolete F1–F7 panel range.

The cloud renderer is two-thread llvmpipe at 1520×800. It cannot certify 30 FPS at 1080p on an i5-3570 / GTX 660 / 8 GB rig. Windows build compatibility is checked separately in CI; Windows GPU rendering, sound mix and prolonged play on the target machine remain unverified. The full historical graphical acceptance tour is not claimed as rerun.

Reproduce from the repository root with Java 21 and a working OpenGL 4.5 display:

```sh
sh rpggame/gradlew -p rpggame build installDist pmdMain pmdTest --init-script "$PWD/rpggame/tools/unused-code.gradle" --no-daemon --max-workers=2
sh theastrattoengine/gradlew -p theastrattoengine build pmdMain pmdTest --init-script "$PWD/rpggame/tools/unused-code.gradle" --no-daemon --max-workers=2
RPG_GALLERY_ONLY=system-polish sh rpggame/gradlew -p rpggame gallery -PgalleryDir=/tmp/system-review --no-daemon --max-workers=2
```

Use `RPG_REVIEW_ONLY=ui`, `menus`, `motion`, `traversal`, `carry`, `loot`, `jail`, `guard` or `extras` for focused captures. Generated binaries, test reports and full PNG batches are intentionally excluded from source control.
