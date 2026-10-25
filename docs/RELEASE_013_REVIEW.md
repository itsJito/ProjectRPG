# 0.1.3 occupation, recovery and world-feedback review

This pass follows the ultimate work list and GDD 3.21. All 25 requested occupational profiles are available, while the devmap adds exactly five residents. Player-only progression, real inventory transfers and native physics remain the authority.

## Gameplay contracts

The standard cumulative curve reaches 14,391,160 XP at level 100. Each contribution is floored before summation; the cumulative total is then divided by four. Old saves preserve raw XP, so their displayed levels can change. Health starts at level 10 / 1,154 XP / 10 HP. NPCs and animals never earn XP through actions.

City deaths create private Undertaker claims immediately, with no death-site grave. Wilderness graves remain exposed. Stolen property is excluded from recovery, including stolen items inside a clean bag; confiscation returns property to known owners or protected evidence storage. Jail collects the affordable bounty plus 3% tax, adds time for a shortfall, and holds all other belongings until release. Gear is restored using saved inventory anchors, preserving filled bags and slot assignments across load.

Unfilled cemetery stones accept one actual carried body. Filled stones retain the departed's original possessions. Burial releases the physical body and clears its corpse association; protected player claims keep their recovery gates. A collector's ongoing task is not restarted by the same case's blood evidence.

Occupational decisions use committed task networks, suitable resources, recipe permissions, actual tools and finite inventories. Nets retain their tool and capped catch; snares bind the actual captured animal immediately on load and retain the carcass for butchering. Order Knights inspect and report assignments once; Household and Escort Knights have different protection duties. Watch, raiding and escort squads communicate local orders from perceived allies and foes. Monster is a reserved agent domain with no added content.

## Rendering and launch

Windows uses incremental Ninja when available, including the Visual Studio bundled tools. Without Ninja, the bounded parallel compiler replaces serial NMake. Worker count is capped at two or four to limit memory pressure. Normal launch builds only the runtime; verification tools remain in `check`. Windows CI also verifies that a subsequent unchanged engine-runtime-JAR/`classes` invocation leaves `buildNative` up to date.

Body flames retain full 64-bit emitter identity and follow emitter translation on the GPU. Smoke remains in world space. The bounded 4,096-particle state uses aligned vec4 storage; cached source indices avoid repeated searches unless emitter ordering changes. SSBO and vertex-attribute barriers separate compute writes from subsequent simulation and drawing.

Ten foliage silhouettes share cached chunk meshes. Growth, valid surfaces and saved depletion are separate from rendering; one dirty chunk is uploaded per frame, distant chunks are culled and tiny foliage casts no shadow pass. Human wounds recess mesh surfaces; animal wounds carve private copies in the struck part's own coordinates. Healing restores the original geometry. Charring tints actors and props without creating per-frame textures.

The first-person camera carries a local head offset through current actor motion and checks walls. Rain stays upright, spawns around the actual camera and collides with world geometry. Wet roughness preserves diffuse grass and the existing metal response. Water contacts consume authoritative mover, ragdoll and rigid snapshots with bounded spray/ripple events.

## Verification

- Full Linux game build: **141 JUnit tests passed**. Coverage includes the Brute's actual approach/attack, empty-bow retaliation, friendly guard exclusion, shared searches, custody and burial conservation, raw-XP loading, real mining/smithing, persistent nets/snares, order reporting, physical water contact, voxel healing and fire recovery.
- Standalone engine: **28 Java tests passed**, including actual OpenGL rendering and the behavior-tree/task-network/domain checks.
- Native strict warnings as errors: **119 passed**. AddressSanitizer/UndefinedBehaviorSanitizer with leak checking: **119 passed**, no reported failures.
- Game and engine PMD checks for unused local variables, private fields and private methods: **zero findings**. This is a scoped static check, not a claim that software can be proven free of every unreachable path.
- Creator/XP graphical acceptance: **20/20 passed**, including the corrected separate beard-color row, current creator defaults, player-only XP policy, level notice and end-state item/XP/reference/ecology invariants.
- Real llvmpipe OpenGL review covered Settings clicks, conversation return, long Worldbox text, downward sprint views, all ten foliage models, five new residents, deployed gear, cemetery states, wound/heal/char comparisons, horse/rabbit movement and burning-body motion. Changed motions were sampled at frames 1, 4, 7… at 60 simulation steps per second.
- Original `patchnotes.md` and `gamefixes.md` contents are preserved byte for byte, with new sections appended. Review images are documentation assets and are not packaged with the game runtime.

The first attempt to run the full historical graphical tour exposed obsolete creator/XP assumptions. Those checks were corrected and rerun successfully in the focused 20-check suite. The entire historical tour is not claimed as passed. One overlapping Gradle invocation invalidated a report; that report was discarded and the final suite was rerun alone.

## Visual evidence

The images below are presentation copies of actual engine captures; the review did not generate substitute artwork. Full-resolution PNG captures were inspected before producing smaller JPEG documentation copies.

| Review | Evidence |
|---|---|
| Settings accepts the next click | [VSync changed](release-013/settings-clicked.jpg) |
| Five residents, with trade-specific appearances | [roster](release-013/five-residents.jpg) |
| Cuts, healing and charring | [human and horse comparison](release-013/injuries-healing-char.jpg) |
| Rabbit cadence | [before](release-013/before-rabbit-hop.jpg), [after on threes](release-013/rabbit-hop.jpg) |
| Horse attack | [before](release-013/before-horse-attack.jpg), [after on threes](release-013/horse-attack.jpg) |
| Fire on a moving person | [attached flames](release-013/moving-body-flames.jpg) |
| Sydney stop, drop and rolling attempts | [collapse](release-013/stop-drop.jpg), [ground contact](release-013/ground-roll.jpg), [later state](release-013/smothered-fire.jpg) |
| Ten foliage models and qualified growth | [model bank](release-013/foliage-bank.jpg), [field](release-013/procedural-foliage.jpg) |
| Wet grass and precipitation | [weather view](release-013/wet-materials-rain.jpg) |
| Conversation and message wrapping | [return to choices](release-013/chatbox-return.jpg), [Worldbox](release-013/worldbox-wrap.jpg) |
| First-person downward sprint | [on threes](release-013/firstperson-down-sprint.jpg) |
| Cemetery states | [Unfilled](release-013/unfilled-stone.jpg), [Filled](release-013/filled-stone.jpg) |
| Actual deployed tools | [lake net](release-013/deployed-net.jpg), [snare](release-013/deployed-snare.jpg) |
| Updated XP presentation | [level notice](release-013/xp-notice.jpg) |

The earlier rabbit reference had camera occlusion during part of the sequence; the final review uses open ground. Wound review led to correcting animal-local hit coordinates, and resident review led to deliberate clothing and consistent gender/voice profiles. Rain review warms the particles from the same camera used for the still, rather than a remote player camera.

## Reproduction and limits

Run the game tests with `sh rpggame/gradlew -p rpggame build`. Run standalone engine tests with `sh theastrattoengine/gradlew -p theastrattoengine build`; rendering tests need a display. Apply `rpggame/tools/unused-code.gradle` as an init script for the PMD tasks. Windows uses the corresponding `gradlew.bat` commands.

After `installDist`, run the packaged application with `--gallery --hidden --shots <directory>`, setting `RPG_GALLERY_ONLY=ultimate` and `RPG_REVIEW_ONLY` to `ui`, `surfaces`, `motion`, `fire`, `hud` or `foliage`. The scoped graphical suite uses `RPG_ONLY=leveling` with `--acceptance --hidden --shots <directory>`.

The hardware target remains i5-3570 / GTX 660 / 8 GB at 1080p and **30 FPS**. Software OpenGL validates rendering and shader behavior, but does not certify performance on that machine. Windows GPU behavior and final audio balance need a real play session. River, coastal and offshore profiles require corresponding authored resources and safe travel access; the devmap supplies lake fishing and does not contain an ocean or ship. The remaining GDD content beyond these 25 profiles remains design scope, not newly spawned agents.

## Primary references consulted

- [CMake Ninja generator](https://github.com/Kitware/CMake/blob/master/Help/generator/Ninja.rst): incremental build graph and supported tool generator.
- [BehaviorTree.CPP sequence](https://github.com/BehaviorTree/BehaviorTree.CPP/blob/master/include/behaviortree_cpp/controls/sequence_node.h) and [fallback](https://github.com/BehaviorTree/BehaviorTree.CPP/blob/master/include/behaviortree_cpp/controls/fallback_node.h): running-child and cancellation semantics. The small Java control layer is project code, not a copied engine dependency.
- [Khronos glMemoryBarrier](https://github.com/KhronosGroup/OpenGL-Refpages/blob/main/gl4/glMemoryBarrier.xml): shader-storage visibility and vertex-attribute visibility after compute writes.
- Recorded audio provenance remains in [sound credits](../rpggame/src/main/resources/sounds/CREDITS.txt), with the upstream Foley references documented in [the preceding review](RELEASE_012_REVIEW.md#references).
