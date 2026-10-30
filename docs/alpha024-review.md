# Alpha 0.2.4 — Cell Service

Developer: Shadow

## Branch and architecture

This pass starts from main `85a17791d3dcb9e1db7fa50ad9426895544b71c9`. Publication targets `polish`. Its previous tip, `1bafc4a13b641aee0c6ebf0f2bafdf79b39eb96a`, is retained through an ancestry-only merge: the resulting files are main plus this pass, without importing the previous polish implementation.

The game remains a Java 21 simulation over the native engine. Gameplay reads the shared voxel view and submits commands to the engine; queued changes take effect at the engine frame boundary. Jobs, inventories, combat, law, and rendering continue to use their existing systems. The save schema and established fixture IDs are retained. New residents use stable IDs; their shops transfer and conserve real items. NPC, animal, and monster skills remain fixed profiles.

## Validation

- Native engine build: passed, including 121 native checks and 29 Java tests
- Game distribution build: passed
- Unchanged-main baseline: 271 tests, eight failures, saved separately from modified classes
- Full game regression run: 283 tests, eight failures and one skipped; failures were investigated rather than accepted as passing
- Final affected-class verification: 47 distinct regression checks passed across the two follow-up runs (24 checks in the last run); all eight failures from the full run were covered and resolved
- The complete suite was not rerun after the last placement and investigation corrections; the affected classes were rerun instead
- Real save compatibility: a save produced by unchanged main loads into 70 residents, saves, and loads again into the same 70 residents, with no invariant discrepancies

The full run caught incorrect new bed orientations and an Ore Miner placed inside a resource block. The beds now face walls and the miner stands on clear ground south of the mining enclosure. Test assumptions were updated where the requested behavior intentionally changed: passable foliage can still have rendered cutout volume; additional workers share established professions; derived foliage must be revisited before checking its regenerated object. Mechanical, projectile, and summon probes now explicitly establish their needed free space and equipment state. Quiet-day movement is isolated from predator encounters and monster alarms, which have separate tests.

Tests run from frozen classpaths so a later distribution build cannot replace classes under an active worker. A prior run affected by concurrent compilation was discarded.

## Visual and animation review

The reference direction is Classic-era World of Warcraft: readable silhouettes, economical anticipation and recovery, warm direct light, and readable shaded spaces. Sources include the [Classic Elwynn fishing guide](https://boosting-ground.com/wow-classic/guides/profession-guides/fishing-and-cooking-1-300) and the [Classic Stockade guide](https://www.wowhead.com/classic/guide/classic-wow-the-stockade-dungeon-quests). No reference assets were copied into the game.

Matched original/final views cover clear midday, dawn, overcast daylight, and the palace cell. Monster contact sheets cover Goblins, Skeletons, Zombies, Ghosts, and Giant Spiders. Fishing records complete work cycles at 60 Hz, capturing frames 1, 4, 7, and onward for the rod, net, and cage in both camera views. Controls and wheel captures use normal UI dimensions. Foliage captures cover untouched plants, contact bending, and recovery.

Review found and corrected the following issues:

- Monster attacks inherited the human sheathed state and failed before their animation started
- A held off-hand shield was being moved into the fishing support-hand pose and obscured the first-person view
- Small-window controls overlapped their navigation; the controls now adapt their columns, rows, and spacing
- Capture runs inherited previous catches and eventually filled the inventory; catches are now transferred out between tool reviews
- Jail captures needed enough frames for nearby fence meshes to finish streaming

The existing block-based art direction remains visible. These captures support a functional animation and readability review, not a claim of a perfect 20/20 match to another game's artwork. The software-rendered environment cannot certify a hardware frame-rate target. Full acceptance and hardware benchmarking are separate from the checks above.

## Reproducing the review

From `rpggame`, run `sh gradlew test` for the game tests and `sh gradlew installDist` for the distribution. From `theastrattoengine`, run `sh gradlew build` for native and Java engine verification.

The gallery supports `RPG_GALLERY_ONLY=polish024`. Set `RPG_024_REVIEW` to `fishing`, `jail`, or `foliage`; leave it unset for the wheel and controls. Use `RPG_GALLERY_ONLY=monsters`, `RPG_MONSTER_MODE=attack`, and `RPG_MONSTERS=GOBLIN,SKELETON,ZOMBIE,GHOST,GIANT_SPIDER` for the attack sheets. Supply `--hidden --shots <directory>` through the existing gallery launcher.

## Compatibility limit

New saves retain the original custody bounty directly. An older active sentence can recover that amount from its arrest event while that event remains in the bounded event log. If an older save has already discarded that event, its missing original amount cannot be reconstructed; the 75 Gold jailbreak addition still applies.
