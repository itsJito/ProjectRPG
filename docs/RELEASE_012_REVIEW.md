# 0.1.2 systems and presentation review

This pass follows the current work list and GDD 3.20. NPCs and animals retain fixed skill profiles; only the player earns XP. Existing spell names and item ownership paths remain authoritative.

## Behavior and recovery

Guards deter visible outlaw profiles, raise equipped shields, and collect visible bodies in their assigned territory when safe. Collection uses real navigation, pickup, carrying and arrival at a registered available graveyard. NPC remains become individually named memorials containing their original possessions. Player graves keep their secured-at-death classification: town claims wait safely for the watch, while expired exposed claims schedule an actual Undertaker journey. Filled backpacks, stolen provenance, quantities and equipment survive burial and save/load. Recovery fees use real accessible coins; unrelated criminal evidence is excluded from modern claims. Older profiles receive the six added authored residents through one stock-accounted migration; loading an older timeline repeatedly resets their initial possessions instead of duplicating them.

Parked animal carcasses preserve exact per-part poses outside the ten-body physics budget. Picking one up restores physics on demand through a bounded pending interaction. Carried bodies cannot be evicted. Human corpse associations are rebuilt from actual saved corpse records, and missing physical bodies restore from the cached dead pose. Death remains terminal; fall recovery cannot stand a dead actor up. These address visible bodies that previously rejected interaction as no longer present.

Humans take environmental fall damage once per real landing beyond five blocks. Longer falls hand physical ownership to Sydney, including the living player; recovery uses the actual terrain height. Body carrying pins the torso and leaves passive limb articulation. Held Attack throws along the aim pitch with momentum; tap Attack lowers the body. Heavy animals cannot be lifted. Thrown weapons and bodies have geometry-clipped trajectory guides. First-person camera offsets stay inside the collider, and the shield guard sits farther down and to the side.

Guard challenges use officer-local evidence certainty, with weaker rumor evidence and a bounded lie outcome. Legal settlement remains separate from personal memory. Dialogue options recheck whether a speaker is still available. A witnessed rampage can frighten civilians without global knowledge. Predators consume actual human/animal remains progressively, leaving original possessions behind. Bobwin joins the watch; Oswin, Maud, Alric, Godric and Isolde provide survival, healing, graveyard, hunting and social duties. Silas's existing fence is discoverable on the map and through local dialogue.

## Engine and combat

The native chemistry service handles finite element volumes and tagged receivers, spatially indexed and time-sliced: up to 4,096 sensors, 256 volumes and 96 active sensor updates per chemistry tick. Burning, wetness, freezing, electrical propagation and wind interact through element intermediaries. Dry and burnt receivers sleep. Real damage retains attribution; non-projectile elemental harm uses Magic protection/XP rather than melee training. Projectiles also test actual dead human ragdoll heads, with no corpse damage or XP award.

Charred cells and frozen water use a persistent edit journal. Loading rolls back the abandoned timeline's chemistry edits before restoring the saved edits; native polling waits until reset/restore has run. Ice retains its remaining thaw duration. The engine JSON reader/writer now preserves signed 64-bit integer IDs exactly, fixing coordinate loss that previously stranded restored ice.

The ten requested powers use shared casting rules and existing names where effects overlap: Fire Blast, Voltchain, Vitalmend, Morphbeast, Spiritual Step, Frost lance, Forcewave, Chronoslow, Necrocall and Spelldaggers. NPCs use the same book, fixed-level, Mana, targeting and cooldown rules. Transformations retain the actor and possessions; summons are temporary, capped and owned, with no duplicate gear, corpse loot or XP reward.

Fire, smoke, rain, splashes and spell trails use a bounded GPU billboard pool: 1,024/2,048/4,096 particles by quality, with at most 64 emitters and no particle readback. Water uses a 256×256 fixed-step heightfield, at most eight substeps per frame, shoreline/rain masks, scrolling surface detail, refraction and the existing Fresnel/SSR path. Its mask buffer is reused and only edited world columns are rescanned. A burning log no longer triggers a height scan of the entire map every update.

The Time System reads the caller-owned world clock, shared by lighting and perception. Registered region profiles blend day/night color and fog across borders; the devmap registers one neutral profile. Weather transitions and phase persist. Volumetric clouds and local fog reuse the existing bounded pass. Living entities cast shadows; only luminous equipment/effects emit light.

## Presentation and recordings

The title and creator share a separate dungeon preview. Henric is the single default appearance; catalogs, randomization and a rotation pause remain available. The bundled engine logo holds for four seconds between fades and remains on the title. Title settings reuse the in-game panel. Quest and level notices share one bounded queue, clear text and reserved space below Honor. Human moods and a 0.6-second talking loop use cached geometry in both world heads and Chatbox portraits. Horse head/neck proportions and attachment were reviewed through the walk cycle.

Synthesized audio generators were removed. Fire, water, thunder, impacts and existing bow/arrow/foley paths use recordings, with sources and licenses in `rpggame/src/main/resources/sounds/CREDITS.txt`. Credits retain the upstream thunder license statement without inventing an unspecified version.

Environmental blood now includes more directed wall/floor spray, small satellite droplets, validated wall streaks and pools below the source. The stain history remains capped at 512.

## Verification

- Full Linux game build and installed distribution: **122 tests passed**. Affected carrying/rig/falling/magic tests were repeated after the final view changes.
- Standalone engine build: **25 Java tests passed**, including actual software-OpenGL rendering, regional filter blending, weather restoration and exact 64-bit save numbers.
- Native strict warnings-as-errors: **119 passed**; AddressSanitizer/UndefinedBehaviorSanitizer: **119 passed**, no reported failures.
- Scoped PMD unused locals/private fields/private methods: **zero findings** across game and engine main/test sources. `git diff --check` is clean.
- Packaged application exercised the actual title/creator, Chatbox mouth frames 1, 4, 7 … 34, horse walk frames 1, 4, 7 … 52, pickup/throw contact sheets, first-person shields, world-projected throw guide, GPU water/rain/fire and environmental gore. Screenshot review corrected both shield obstruction and guide occlusion by the carrier.
- Runtime native rebuilds are up to date after packaging; interactive launch does not run the native verification suite. The intentional intro uses wall time so a slow render cannot stretch its four-second hold.

| Review | Evidence |
|---|---|
| Title before / dungeon title / Henric creator | [before](release-012/title-before.jpg), [title](release-012/title.jpg), [creator](release-012/creator.jpg) |
| Honor + queued milestones | [quest](release-012/quest-honor.jpg), [level and unlock](release-012/level.jpg) |
| Chatbox and mouth loop on threes | [Chatbox](release-012/chatbox.jpg), [mouth frames](release-012/chatbox-mouths.jpg) |
| Horse walk on threes | [walk frames](release-012/horse-walk.jpg) |
| Carrying and aimed release | [pickup](release-012/body-pickup.jpg), [throw](release-012/body-throw.jpg), [visible guide](release-012/throw-guide.jpg) |
| First-person shield | [before](release-012/shield-before.jpg), [rest](release-012/shield-rest-firstperson.jpg), [guard](release-012/shield-firstperson.jpg) |
| GPU water, clouds and fire | [water/rain](release-012/water.jpg), [fire/smoke](release-012/fire.jpg) |
| Environmental blood | [before](release-012/gore-before.jpg), [impact](release-012/gore-impact.jpg), [settled](release-012/gore.jpg) |
| Engine intro, actual application | [logo hold](release-012/engine-intro.jpg) |
| Shared title settings | [settings](release-012/title-settings.jpg) |

The full historical graphical acceptance tour is not claimed as rerun. The cloud renderer is llvmpipe software OpenGL 4.5; it validates shader compilation, rendering and interactions, but cannot certify 1080p/30 FPS on the requested i5-3570 / GTX 660 / 8 GB target. Windows GPU behavior and final sound balance still need a real play session. Future kingdom content and the complete GDD occupation/content catalog remain production work.

## References

- [Khronos glMemoryBarrier](https://github.com/KhronosGroup/OpenGL-Refpages/blob/main/gl4/glMemoryBarrier.xml): image writes require image/texture-fetch barriers before water sampling; SSBO particle writes require the vertex-attrib barrier before drawing.
- [Khronos glDispatchCompute](https://github.com/KhronosGroup/OpenGL-Refpages/blob/main/gl4/glDispatchCompute.xml): bounded workgroup dispatch and workgroup sizing.
- [Minetest fire recording credits](https://github.com/minetest/minetest_game/blob/master/mods/fire/README.md) and [default sound credits](https://github.com/minetest/minetest_game/blob/master/mods/default/README.md): individual recording provenance and attribution.
- [Lightning recording credits](https://github.com/minetest-mods/lightning/blob/master/README.md): upstream attribution for the unchanged thunder recording.
