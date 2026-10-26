# Changelog (developer notes)

A running log of what changed, in plain words. The newest pass is first; the first pass on `gamefixes.md` follows below it.

---

## Pass 6: the new gamefixes list (Version 0.1.6)

Worked down the current `gamefixes.md` with `instructions.md` first and the GDD for the rest. Where I could, I measured before changing anything (headless probes for doors and NPC routines). I judged visuals from gallery captures (`RPG_GALLERY_ONLY=gamefixes-ui`, `gamefixes-blocks`, `gamefixes-aim`, `locomotion`); the captures are in `rpggame/build/review-gamefixes/`.

### Bugs from the list
- **Doors.** A headless probe (`DoorPassageTest`) showed that house doors did open for NPCs, but the leaves swung to ±3.05 rad, straight through the wall beside them (`RIGID_NO_WORLD`). Changes:
  - `Door.SWING` is now 1.92 rad: to the wall, a little past square, either way.
  - `Door.shove()`: a body touching an open leaf while moving through the doorway drives the hinge in its direction of travel (the sign comes from the door's own `inward[]`), so the leaf gets out of the way.
  - `Door.knock()`: a blade striking an open leaf swings it.
  - Owners and key-holders now unlock a locked door when their trip ends on the other side, pass through, and relock it once it latches (`relock`, saved).
- **Doorway targeting.** Open leaves were hard walls for the melee rays, the blade-clearance test and the focus ray. Someone in a doorway could not be hit or targeted when a swung leaf stood beside them. Changes:
  - `Voxels.ray(..., leaves)` and `clear(..., leaves)` can skip open leaves (shut ones still count).
  - `Hit.door` names the struck leaf.
  - Melee knocks the leaf and carries on.
  - The focus uses the leaf-free distance for actors.
- **NPC routines.** A five-hour headless probe of every NPC (now `NpcRoutineTest`) found these problems, all fixed:
  - Lawmen were routed into their own locked jail cells. `Waypoints.route` now takes a door predicate.
  - The waypoint graph had no points inside buildings. `Waypoints.doors()` now adds a pair of points per doorway, linked through it and to everything in clear sight of each point.
  - `Waypoints.clear` treated a locked door as a wall.
  - The fisher was sent to a fishing spot out on the water. `standSpot` now searches rings up to 4 cells out for a dry bank, and nets are hauled from the bank.
  - People stood at an unattended counter trying to buy bread. Buying now needs the shopkeeper at the counter.
  - Maud waited all day for berry bushes. She now gathers herbs.
  - Guards spent the morning evicting the resident thief. Eviction is now for bandits and brutes, and for thieves only when a crime is known. An outlaw who is ordered out heads for the nearest wild waypoint.
  - Hiding now ends after 60 s even if clues keep coming.
  - Hale drank against his own cell's fence.
  - `agentStuck` now steps an NPC off non-standable ground.
  - Stuck events over five hours went from about 600 to 7.
- **Legs when turning.** `Rig` now tracks the yaw rate. While turning in place:
  - the corrective-step threshold drops from 1.8 to 0.9 px;
  - steps take 0.15–0.24 s;
  - each step lands where the turn will have carried the body (`toWorldTurned`).

  Filmed as `turnaround` and `pivot`: the feet now step round. Some knee bunching remains mid-turn (about 13–14/20 on my review).
- **Foliage reaction.** The engine already bent chunk foliage around body records, but no trail was ever sent (`push` was null). Changes:
  - `Foliage` now keeps a trail of where bodies pushed through (a point every 0.2 s, recovering over 2.2 s) and sends the freshest 8 as pushers.
  - The native renderer takes up to 16 benders per chunk: bodies first, then pushers.
  - `model.vert` bends further (0.9 of the height, dropping 0.45), and a trail point's hold fades with its radius.
  - Bodies moving through plants make a rustle, and a noise for stealth.

### Changes from the list
- **Tiers.**
  - `Tier.LEATHER` is gone, replaced by `WOODEN` (level 1: weapons, tools and shields; no armour) and `STONE` (level 2).
  - `wooden_sword` and `wooden_shield` now come from the tier loop.
  - Bronze's sword bonus is corrected to the GDD's +2.
  - `Items.saved()` maps `leather_*` ids in old saves to `stone_*`.
  - The player now starts in a linen tunic, and the shop sells wooden and stone tools.
- **XP.** `Config.xpRate` was 10x, and it was saved into every profile. It is now 1x and no longer saved, so action values sit on the OSRS-shaped curve.
- **Bears.** Skills went from 40/48/30/55 to 26/28/20/35, swipe damage from 12 to 6 and impact from 90 to 70. Max hit dropped from about 7 to 4.
- **Bounty overhaul** (`Law`, plus the new `law/Posses`):
  - `charge()` follows the schedule. Murder carries 400, 500, 600 or 1,000 G by victim (`Npc.Standing`, `GUARD`/`LAWMAN`).
  - There is no cap, and high Honor (+33 or more) halves each charge.
  - `pursued()`: the watch acts on bounties above 21 G, or on any bounty once the player has talked to them (`confront`).
  - `lockdown()` at 500 G: shops shut and the watch no longer halts first.
  - A `BountyReminder` HUD card appears when you enter a kingdom where you are wanted.
  - Tracking posses set out once a kingdom's bounty passes 1,000 G, every 30–40 minutes of play.
  - Border ambushes happen when you head out with 100 G or more: half the time, at most once every 10 minutes.
  - Posses follow the given make-up table, with law dogs. Their NPCs are saved and restored, and killing them or their dogs is lawful.
  - Arson is detected in `Chemistry` when an owned fire burns building wood or someone else's property in town.
  - A bare-fisted assault is now `DISORDERLY`.
  - `Container` used `"Lockpicking"`, which is not a recognised crime kind; it is now `"LOCKPICKING"`.
- **Resisting arrest** now closes the dialogue (`closeChoice`).
- **Chemistry.** `Blocks.Material` now drives burning, conduction and freezing for every block: all logs and leaves burn; water, mud and ore conduct. Elemental spell projectiles now stop at the water's surface:
  - ice freezes a 2.4 m patch;
  - lightning spreads 1.8 m;
  - fire thaws ice (the native engine already handled this).
- **Detached windows.** Containers and shops now draw as their own card, left of the inventory frame (`Windows.detach`; on narrow screens they fall back to the screen edge). The item info panel stays visible.
- **Two Take All buttons:** `takeall:equipment` and `takeall:items`. A body's worn gear counts as equipment.
- **Chat and dialogue.**
  - Chat is now 214 px tall and up to 640 px wide, in Arial Narrow at 14 px, always wrapped.
  - The dialogue box is now 780 px wide with 8 lines of text. Replies wrap and are numbered by the key that picks them.
- **Throw arc.** The new `combat/ThrowSolver` replaces `Ballistics`, which is removed.
  - The target is where the camera's centre looks and the origin is the hand bone; t = d / speed and v = (target - start - ½ g t²) / t, with per-item gravity of 13–20.
  - The path is 30 points with a 0.15 m sphere sweep (world and bodies), cut at the first contact. The landing ring is red when blocked short of the target or under 1.5 m away.
  - The plan is only re-solved when the aim moves more than 0.5°.
  - It draws as additive world-space dots every 0.22 m, fading from 100% to 40%, plus a landing ring.
  - The release flies the same plan. Thrown weapons tumble at up to ±12 rad/s and bounce with 0.3 restitution up to twice. The thrower's root freezes for 0.1 s on release.
  - Body throws use the plan too.
- **Level-up confetti.** Two bursts of 34 small, short-lived embers instead of three bursts of 70.
- **NPC item offers.** `Brain.offer()`: a wounded player is offered food through a parchment `OfferCard` with Accept and Decline. Nothing changes hands until Accept, and then the actual stack moves.
- **Pause menu.** Rebuilt as a Settings-style parchment card, without the equipped item or Honor.
- **Menus.**
  - `Style` is redone: dark parchment with a gold trim and studs, bevelled wells, stone buttons and hanging tabs.
  - Panels get Morpheus headings with gold rules, over inset sheets.
  - The equipment doll uses the provided slot plates, and the tabs use the provided icons.
  - Inventory grids are now trays of cells, and the world map sits in a trimmed mount.
- **Foliage.** Eight new kinds:
  - Half of every candidate cell now grows something, and an unsuitable kind falls through to one that suits the spot.
  - Plants sit off the grid lattice, and ivy climbs any stone or wood.
  - The Cut option is removed; fire still destroys plants.
- **Third-person ranged aim.** `Rig` turns the torso, hips and feet side-on (-1.2 rad) while drawing a bow, crossbow or throw.

### Added from the list
- **Water.** `WaterContacts` now leaves a wake ahead of a wading body (every 0.12–0.42 s depending on speed), throws spray when running, and rings round a still body every 1.1 s.
- **Fonts.** `Ui2D.Face`, each font baked at its own size, with the old font as the fallback:
  - Friz Quadrata for the interface;
  - Morpheus for headings;
  - Arial Narrow for chat;
  - Skurri for combat numbers;
  - Nimrod MT Italic for flavour text.
- **Music.** The provided jingles now play on:
  - skill level ups;
  - combat level ups (with a chat line);
  - reaching the maximum combat level, 127;
  - reaching level 100 in a skill;
  - death, which also stops the score.

  The score ducks under each one.
- **Fishing animations** (`Rig.fishPose`):
  - Rod: a back-cast, then the cast. The rod is held tip-up with a bob while the off hand sits on the reel and winds at the end. A line runs to a red and white float that bobs and dips on a bite.
  - Net: gathered, flung and held.
  - Crab cage: lowered to arm's length.
- **26 blocks** (ids 51–72, layers 47–74), painted at 64 x 64. Dirt, Sand, Glass and Cobblestone are redrawn. All are placed with a purpose on the devmap:
  - hay in the barn, spruce barn posts and birch kiosk posts;
  - a red runner in Chancery A, and rugs in the houses and the contact's den;
  - a marble altar dais and stone-brick town walls;
  - a limestone Chancery B with light-blue glass, and white glass at the undertaker's;
  - a granite den, clay and mud banks, mud round the duck pond, and sandstone cover on the range;
  - metal and coal blocks in the locked store.

  The atlas took two passes. In the first, birch, marble, granite, mud and clay scored under 16/20 because I had misread the noise octaves; the second pass brought them to about 16.

### Found along the way
- The caravan road was paved through House 1's floor. Buildings are now floored again after the roads (`floors()`).
- `ItemInfo` was hidden whenever a container was open; it now stays visible.

### Found by the acceptance suite
- **Wildfire.** One campfire on the lawn burned 942 grass cells, the herb and flax beds, and killed Dag and Oren (logged as murders by the tester). Grass turf is no longer tinder; the plants on it, the leaves and the trees still burn. `CampfireSpreadTest` covers it.
- **Embers.** A lit `Fire` marks its cell with the new non-solid hazard block `Blocks.EMBERS` (id 73), so routes go round a campfire. A body stuck on one steps off, and only living, loaded, upright NPCs are ever stepped (the step-off had been able to move corpses).
- **Hearth accidents.** Burning at a cooking fire its owner lit is no longer an attack by that owner.
- **Halts.** Being told to halt (or resisting) counts as dealing with that kingdom's watch, so a resisted bounty of 21 G or less is still acted on.
- **Wooden sword.** The Wooden tier's sword bonus is now 0, as the old untiered wooden sword had; +0.5 had put its hits on a rounding edge.
- **Acceptance fixtures.** The skill-action section now clears passers-by from the lab (NPCs whose routines now work were walking into the tester's swings), waits for herb regrowth, and gives Eda a purse for the pickpocket check.

### Verification
- All 163 unit tests pass.
  - New: `DoorPassageTest` (3 tests) and `NpcRoutineTest`.
  - Updated: `MaintenanceTest` (the new wall stops), `SocietyRegressionTest` (aid goes to an NPC; a player gets an offer) and `ReleaseSystemsTest` (the solver lands on the wall face).
- Full acceptance went from 67/68 (stopped by the wildfire's exception) to 240/263 before the last round of fixes; the run after those fixes was stopped for low memory and is not yet recorded. The stealth section passes in isolation; several remaining failures were order-dependent leftovers of the earlier ones.

---

## Pass 5: the gamefixes list, then the GDD core (Version 0.1.0)

This pass went down `gamefixes.md` in order (bugs first, then the changes, then the overhauls), then started on the GDD core systems, with `instructions.md` as the authority. I judged everything visual from gallery shots (`./gradlew gallery`, `RPG_GALLERY_ONLY=...`), with 8/10 as the bar. Animations I checked on threes (frames 1, 4, 7...).

### Bugs from the list
- **Death sounds.** `kill()` asked for a sound bank called "death" that never existed. Now `Sfx.deathCry(actor)` picks from the provided male or female death sounds by the victim's look, and that covers the player too. A hurt cry plays when a hit takes at least 8% of max HP.
- **Dismemberment.** Gore was handed the *clamped* damage (whatever HP was left), so a killing blow on someone at 2 HP could never sever anything. Gore now gets the raw force and decides severing from `cutShare`. `CONTROL_LIMBS` tells Sydney which limbs are gone, so the ragdoll stops simulating them.
- **Animals dismembered.** `Gore.animalPartAt` maps a hit onto the species' figure parts (legs, tail, head). `severAnimal` spawns the part as a rigid body, and the animal limps when `legs < 0.6` and stops at 0.2. Gfx draws a stump.
- **Blood on bodies.** New `actor/Blood`: up to 28 marks (part, face, uv, radius, wetness). `Skin.paint` draws a blot plus drips into both skin layers. `figureKey` includes the blood version, so the mesh rebuilds on the worker only when the blood changes. The attacker's sword arm and chest get spatter too, and water or healing washes it off.
- **Corpse crate.** The Corpse branch in Gfx was falling through to the generic container box, so it's removed now. You loot a body through the body itself.
- **Ragdolls getting up.** I couldn't reproduce it on any of the four kill paths, single- or multi-threaded. Hardened anyway:
  - An `afterFrame` watchdog re-sends `CONTROL_DIE` if a dead body's state ever drifts from DEAD.
  - A dead NPC's x/z follows the ragdoll.
  - Saves keep the ragdoll pose, so a loaded corpse doesn't snap back to standing first.
- **Stolen stacks.** `ItemStack` keeps a victims ledger, so stolen goods of one kind merge across victims, and a confiscation still sends each unit home. Units leave from the front of a stack. `markStolen` on coins makes them clean.
- **Shields.** They were edge-on because of the blade orientation, so `held()` takes a face-forward flag when blocking or in first person. First-person hands got a proper viewmodel (`Rig.view(aim, out, side, down, ahead)`).
- **Tunics.** The hem was boxes. Now it's a 12-strip cloth ring around the hips, each strip pushed by its thigh's swing and smoothed over 3 passes with overlap. It went from looking like boards to looking like a skirt, 8/10.
- **Health 10.** New characters have every skill at 1 except Health 10, and `maxHp = level(Health)`. Damage everywhere is scaled by `CombatMath.HP_SCALE = 0.1` to match. Tiny hits still round up to 1, so a fox can kill.

### Movement (the Human Walk System)
- **Gait** is rewritten with three gaits: walk 1.45 m/s, run 4.0 (hold Shift), sprint 6.6 (double-tap and hold Shift). Cadence and stance are blended by speed.
- The character turns to face where they run, in any direction.
- Deceleration is 17-18 m/s², so there's no glide. A sprint stop costs 0.45 s of recovery, and turning bleeds speed.
- **Rig:**
  - The walk sets its phase on start so a foot lifts straight away. That killed the old lunge.
  - Steps are capped from the predicted hip at landing.
  - Knees bend more, and the run gets a heel kick and lean.
  - Walk, jog and sprint all score 8/10 on threes.

### Gathering
- `Node.strike()` is called from the combat sweep. A real swing of the tool in your hand hits the block.
- Rocks give XP on every strike and ore after `strikesNeeded`.
- Trees fall after enough hits on any of their blocks, and give all the logs and XP at the fall.
- Fishing needs the right tool wielded (rod, small net, crab cage) and gives XP only on a catch.
- Tool tiers set the speed, and tools are gated on Woodcutting or Mining, with a leather tier at level 1. The woodcutter NPC now really swings an axe.

### Title, profiles, creator, console
- `Main.run` loops `session()`. Each session gets a fresh engine, world and Gfx in the same window (`Window.cancelClose()` in the engine). `Profiles` keeps up to 5 slots under `saves/profiles/<n>/`.
- `TitleScreen` uses the provided MainMenu music and a dusk render of your character.
- The creator is redrawn: step rail, summary card, presets, suggest-a-name, name validation, and a turntable with zoom.
- `ui/Console` replaces the Debug tab, with a Source-style command set (see the patch notes) and hooks for the game's own cvars.

### The GDD core
- **Tiers** are on the GDD ladder (Leather 1 ... Malevolent 100), with `swordStrength` per tier.
- **Combat level** is the exact GDD formula, done in integer x40 arithmetic: a new character is 3, and maxed out is 127.
- Attack sets the tempo (`attackCadence`), Strength the damage (`strengthScaling`), and Defense the guard and parry (`guardShare`, `parryWindow`). There's no passive armour mitigation except against magic.
- **Weapon families** each get their own timing table. The new families are longsword, scimitar, warhammer, spear, claws and halberd, plus crossbow, bolts and thrown weapons.
- **Items** are all rebuilt in `HiResItems` at 1/64 m, with per-tier metal kits (real reflectance) and proper fittings. Every skill has a skillcape with its emblem (level 100).
- **Faith (GDD 2.5)** lives in `combat/Prayers`:
  - The pool is the Faith level, with no regen.
  - Prayers toggle and pay a point at raise, then one per `6*(1+0.0333*faithBonus)` s. Nothing is refunded.
  - One overhead and one Strength prayer at a time. The protections negate their attack type completely and give Faith XP for damage prevented.
  - The altar fills Faith, and so do religious wine and restorative potions.
- **Magic (GDD 2.5):**
  - The pool is the Magic level, plus a point a minute (the part-minute is saved).
  - Four books with 11 slots each. One is active, and you switch at the altar.
  - Elemental damage uses the group ceiling, and species with a recorded weakness take double.
  - Fire Blast spends Fire staff charges, recharged with coal.
  - The utility spells live in `combat/Magic`, and each acts through an existing system:
    - Spiritual Step: a teleport.
    - Minor Illusion: the stealth hearing.
    - Darkness: dims Stealth's light.
    - Disguise Self: witnesses remember a stranger, so no bounty.
    - Friends: witnesses filtered out.
    - Charm: `hostile()` and `threatFor`.
    - Gaseous Form: exposure ×0.15, can't strike, untouchable in melee.
- Level-gated content (spells, prayers) shows as "???" until reached.

### Found while making the suite pass (227 -> all but the isolation-only checks)
- **The blood-trail loop.** `Gore.pool()` adds a new piece of BLOOD evidence every 0.8 m along a bleeder's path. Every new stain became a fresh "found blood -> MURDER (subject 0)" memory for a bystander, so witnesses like Eda lived on REPORT_CRIME for minutes, re-reporting "murder by nobody", and the theft they'd actually seen sat behind it. Now there's one incident per victim: `react()` skips evidence of a victim the NPC already reported in the last 240 game minutes, unless it's a body where only blood was known.
- **The lawman reporting to himself.** When Bram witnessed a crime, `report()` walked him toward `lawman.x + 1.3` - his own position, which moves with him - forever, away from the chancery. Now the lawman's own sighting is recorded on the spot.
- **Ledgered releases.** The console's `ent_create` spawned animals outside the habitat ledger, which would trip the end-of-run invariant. `Animals.release()` records them as "released", and the ledger counts that as an addition.
- **Bleed-out blame.** `bleedOut` blamed `lastAttacker`, which an arrest settlement clears, so Cole bleeding out from a leg the tester took read "Cole killed by Cole", and the murder belonged to no one. `Wounds.by` now remembers who opened the wound, and that's who killed them.
- **Models below 8/10, redone.** Religious wine and the restorative potion fell back to a shapeless blob, so `HiResItems.bottle()` now builds a labelled, wax-sealed wine bottle and a round flask with the liquid showing through clear glass. Darts are a fletched bundle of three instead of a white block, and the tomahawk has a socketed, bearded head with a back spike instead of a flag-shaped plate.
- Result: the full suite is at **234 / 234**.
- The Gaseous Form "can't strike" check ran after the stamina was spent and the wind-up set, so it moved to the top of `attack()`.
- Test hygiene, because a fresh 10-HP tester dies more often than the old one did:
  - The arrest check hands the tester a sword if a death left them bare-handed.
  - The noise check clears a leftover hunt.
  - The stolen-stack check reads the pack before the scenario's witness interception.
  - The predation check releases a fox and kit if earlier sections emptied the wood.


---

## Pass 4: running absurdly well on this machine (Version 0.0.6)

The goal: make the game run as well as it possibly can on the development machine. That's an AMD FX-6300 (six threads, weak per core), a GTX 1050 Ti (4 GB) and a 2560 x 1440 monitor at 164 Hz, sharing the box with a busy browser.

### Measuring first
- `./gradlew bench` is a new benchmark. It plays the devmap exactly as the game does (same tick, same drawing, same swap, vsync off) over a fixed tour:
  - the street with its people
  - the grove
  - the pond
  - the campfire at night
  - a sprint down the street
- For each stop it reports the mean, p50, p99 and worst frame, plus how many frames missed 60 and 144 FPS.
- Each frame is split into simulation, scene building, the renderer's CPU, swap and GPU, and the GPU part is broken down by pass.
- Every slow frame gets a breakdown. When the time was the game's own, a stack sampler names the code.
- Options:
  - `RPG_RENDER_SCALE` fixes the scale.
  - `RPG_BENCH_TARGET` sets the FPS the dynamic resolution aims at.
  - `RPG_BENCH_FULLSCREEN=1` runs at the monitor's own resolution.
  - `RPG_BENCH_SHOTS=<dir>` saves a full-size picture of each stop.
- The engine reports GPU time per pass now (timestamp queries, read back late so they never stall); see the engine's changelog, API 2.5.
- **Baseline** (1920 x 1080, windowed): **73 FPS** average, p99 36 ms. The GPU frame was about 12 ms: lighting 2.6, volumetric fog 2.5, G-buffer 2.0, shadows 1.8, resolve 1.5, AO 1.1. CPU work was only about 2.5 ms a frame, so the game was GPU-bound.

### What made it faster (GPU)
| Change | Effect |
|---|---|
| Leaf cards baked into a 64 x 64 texture array once at start-up, instead of cut by a procedure per fragment in the G-buffer and every shadow cascade | shadows 1.9 -> 0.8 ms, G-buffer 2.0 -> 0.7 ms |
| Volumetric haze at a quarter of the render resolution (depth-aware upsample, smoothed by TAA) | fog 2.5 -> 1.0 ms |
| PCSS: no filtering deep in the umbra, 8 taps for narrow penumbras, fixed loops that unroll | small; a first variable-loop version made lighting *slower* and was replaced |
| **Temporal upscaling**: below render scale 1 the TAA rebuilds the full-resolution image from jittered samples | 0.75 looks nearly identical to native at 1:1 and costs about 56% of the pixels |
| **Dynamic resolution** (`Config.autoScale`): the scale follows the GPU's time against the monitor's refresh rate, on fixed levels (1, 0.85, 0.75, 0.67, 0.6, 0.5), never below 720 lines rendered | holds the frame rate; stays at 1.0 when there's room |
| Packed-float history and lens targets, the exposure read once per pixel in bloom (it was read 13 times), the normal buffer read only where the hands could be, exposure measured every other frame | resolve 2.6 -> 2.2 ms at 1440p |

### What made it smoother (hitches)
- **The minimap was the big Java hitch.** Every block edit (an NPC felling a tree, a door opening) made it rescan the whole map top to bottom: about a million block reads, 20 - 45 ms. Now only the edited columns are coloured again (`Rpg.mapColumns`).
- **Figures were built on the render thread.** Painting a skin and building its meshes took 10 - 75 ms whenever someone new came into view or changed what they wore.
  - Now they're built on a worker thread; only the upload is the render thread's.
  - Until a new figure is ready, the person keeps their last one.
  - Everyone's figure is pre-built while the game loads.
  - The figure cache key is now a 64-bit hash instead of a string built every frame for every character.
- **GC:** the game's JVM runs ZGC with a pre-touched 512 MB heap (`rpgJvm` in `build.gradle`). G1's young-generation pauses were 7 - 17 ms. `RPG_GC=g1` brings back the old collector for comparison.
- **Frame pacing:** at most three frames in flight, enforced with a fence. Two frames starved the GPU: 99 FPS against 137 at 1440p.
- **Dynamic-resolution steps are rare:** down a level after half a second over budget, up one only after four seconds with plenty of room. Each change rebuilds the render targets.
- **Adaptive vsync** where the driver offers it: a late frame is shown at once instead of waiting a whole 164 Hz refresh, which would have halved the rate.
- **Alt+Enter** toggles borderless fullscreen at the monitor's own mode, which also avoids most compositor stalls from other apps.

### Results
| | Before | After |
|---|---|---|
| 1920 x 1080 windowed, native resolution | 73 FPS, p99 36 ms | 94 FPS |
| 1920 x 1080 windowed, dynamic resolution (aiming 144) | - | **155 FPS**, p99 11 ms, 7 slow frames in 2,000 (best run) |
| 2560 x 1440 fullscreen, dynamic resolution (aiming 164) | - | **141 FPS**, p99 9.4 ms, 5 slow frames in 2,000 |

Windowed runs vary with what else is on the GPU. Several runs had dozens of 25 - 40 ms swap stalls with the game's own work unchanged, while the browser was the busiest process on the machine. Fullscreen is steadier.

### Bugs found on the way
- **Corpses vanished on load.** The save writes each object's class name as its kind (`Corpse`), and the loader only knew `Container`. Corpses now load, still tied to their body.
- **Blood from a severed limb or a spray had no owner**, so a bandit's blood started a murder search. Spatter now carries whose blood it is and who drew it. A guard's or lawman's own blood from a fight the law was in no longer starts a search either.
- **Fighters approached into their opponent's cell.** On the 1 m navigation grid the target cell could be the opponent's own, so they ended up chest to chest and never swung. The approach point is now within reach but outside that cell.
- **A disarmed NPC fought bare-handed forever.** Getting up, they now pick up the weapon knocked from their hand if it's within reach.
- **Guards investigated the player standing in plain view, indefinitely.** An investigation now settles after about three seconds at the spot ("nobody there" or "just them"), and that person isn't a mystery again for 30 s unless they're creeping.
- Acceptance-test isolation: the NPC, pursuit and gore sections start with the law cleared, and stray bystanders are moved out of sword reach in the arrest test. The fox test sets the outlaws aside, since a fox is right to run from a brute 25 m away. The noise test measures progress toward the spot actually being investigated, and the corpse test counts the armour the bandit really wore.

### Status
- Native self-test: 104/104.
- Engine build and tests: pass.
- Game acceptance: **203/203** on one full run. The latest full run was 201/203: the gore check's guard had witnessed the killing blow itself (so he reported it instead of searching). The test now keeps him out of sight of the blow, and the gore section passes 21/21.
- Two farm checks are timing-dependent and occasionally fail: Mara's egg delivery when she has a long walk to Store A, and cow breeding when the keeper has no feed out.

---

## Pass 3: Sydney 3 and the renderer (Version 0.0.5)

This pass was renderer and Sydney first, as asked. Everything else in `gamefixes.md` comes after it. Before starting I reran every suite: native self-test 97/97, engine JUnit all green, game acceptance 176/194. The failures were the nondeterministic world-sim checks listed under "Still failing" below.

### Sydney 3: bodies that move themselves
The old Sydney was a particle ragdoll that got shoved back toward an animation pose. Sydney 3 is rebuilt from scratch around the biomechanics in `gamefixes.md`. All the code is in `native/src/physics/ragdoll.{h,cpp}` and `sydney.cpp`.
- **The body is rigid-body dynamics.**
  - 13 rigid segments: pelvis, belly, chest, head, upper/lower arms, thighs, shins and feet.
  - 12 joints. Spine, neck, shoulders, hips and ankles are cone-twist; elbows and knees are hinges.
  - It all lives in a private Impulsis space stepped at 240 Hz with 14 iterations. Gravity, contact, friction and every blow are real physics, not pose blending.
  - The 15 points the game draws are mapped back from the segments, so nothing on the Java side had to change shape.
- **48 Hill-type muscles.** Each joint axis is crossed by an antagonist pair.
  - Each muscle has Geyer force-length, Hill force-velocity and a passive elastic element.
  - Activation follows excitation through first-order dynamics (fast-twitch, 6 ms up and 14 ms down).
  - A static optimiser picks the activations that give the wanted torque at least squared activation, plus whatever co-contraction the behaviour asks for. Inverse activation dynamics turns those into excitations.
- **A layered nervous system.**
  - *Behaviours* set goals: joint targets, trunk orientation, a centre-of-mass reference, hand and foot targets, a rhythm.
  - *Supraspinal control* turns goals into torques:
    - An LQR on the linear inverted pendulum picks where the centre of pressure should be. I solved the DARE offline; it comes out at about 2.4·x + 0.5·ẋ.
    - Virtual-model control (Jacobian transpose) turns that ground force into ankle/knee/hip torques on each stance leg.
    - The trunk is held upright in the world through the stance hips (the SIMBICON/Coros rule).
    - Steps go where the capture point *will be* when the foot lands: ξ(t+T) = p + (ξ−p)·e^{ωT}.
    - Matsuoka oscillators (a CPG reset by foot strikes) time stagger steps, flailing and writhing.
  - *Spinal control*:
    - Gravity compensation.
    - A delayed stretch reflex: the spindles read 21 ms late, gain 0.6, threshold 0.08.
    - Joint servos whose damping runs **inside Impulsis** as implicit drive rows, bounded each step by what the joint's muscles can actually give.
- **Proprioception is honest.** Joint angles and rates are finite-differenced from orientations between steps, not read from the solver's velocities. Those carry Baumgarte drift correction, and that was making the old controller fight phantoms.
- **Behaviours:**
  - Standing balance (ankle strategy, with an optional hip strategy).
  - Stepping, with an anticipatory weight shift first, so the body unloads the foot before lifting it.
  - Bracing and protecting the head when going down.
  - Grabbing a ledge (committed reach, world ball joint at the hand) and hanging two-handed.
  - Lying hurt: holding the wound, writhing, breathing.
  - Getting up: roll, all-fours, kneel, stand.
  - Intents a caller can send: brace, protect, reach, walk, relax.
- **Traits** (`RAGDOLL_TRAITS`): strength scales the muscles, agility the reflexes and steps, toughness how long a blow stuns, and endurance how long a grip lasts and how often the body tries to rise.
- **The one cheat, and it's bounded.** While getting up, a root support of at most 70% of body weight follows the get-up's reference motion. It stands in for the hands, forearms and toes that a 13-segment body doesn't have. It's reported (`assistUsed`) so we can watch it.
- **Gravity pace.** The game's gravity is 22 m/s², not 9.81. Every timing (step time, oscillator frequencies) scales by √(g/9.81), so the body doesn't move in slow motion.
- **Tuning.** `tools/sydney_tune.py` random-searches the balance and stepping numbers against the lab's push sweep (pelvis pushes, plus neck pushes counted double). Three rounds took the score from 1,070 to 1,510. Those numbers are now the defaults (spine 3.75 Hz, stance gain 2.97, posture 1.68, hip strategy 0.3, hip roll keep 0.72).
- **What it does now** (lab numbers, game gravity):
  - It stands with the head still to about a millimetre.
  - It absorbs pelvis pushes of up to ~50 N·s in place and catches 50–70 N·s by stepping; above that it falls.
  - Neck-height pushes are weaker: it steps for them but falls sooner.
  - It gets up from its back in ~4.6 s and from its front in ~1.9 s. After a hard knockdown it is back up in ~8 s on the first try.
  - Shoved backward off a ledge at 75–105 N·s, it catches the edge and hangs two-handed for its grip time (6 s at average endurance), then drops and gets up.
  - Dead bodies settle in ~3.4 s.
- **Bugs found on the way:**
  - The LQR gains were absurd (retuned the weights).
  - The reflex saturated the calves.
  - Explicit damping was unstable at 240 Hz, which is why damping moved into the solver.
  - The servo spring saturated while its damping didn't.
  - Steps never lifted (the knee didn't fold), and the step chose the *loaded* foot.
  - The weight shift had its sign flipped.
  - The toes dragged.
  - The ledge reach was being overwritten by the brace.
  - Walking always stepped with the same foot: the oscillator chose a leg, but the step was planned with the previous one.
- **Walking (the `WALK` intent) is partly there.**
  - Strides chain with no stand in between, with a weight-transfer (double-support) phase before each swing.
  - Placement follows the divergent component of motion. The foot lands at the capture point predicted for touchdown, minus the steady-gait offset b = v·T/(e^{ωT} − 1), and is re-aimed during the swing.
  - The target speed ramps up at most 0.3 m/s above the current one, so the body isn't thrown forward by its ankles.
  - In stance the centre of pressure stays at the stance foot, which is the pendulum the plan assumes.
  - Result: it now takes about five clean strides before the trunk starts to tip and it falls. Before, it managed one or two.
  - The game doesn't use Sydney to walk (NPCs walk with the animated rig), so this sits as a known limitation for now.
- **Lab tooling.** `native/build.bat lab [exe]` builds `tests/sydney_lab.cpp`.
  - Scenarios: stand, push, sweep, lift, tilt, die, drop, ledge, getup, walk.
  - Plenty of `LAB_*` environment overrides.
  - `tools/sydney_sheet.py` turns its pose dumps into contact sheets.
- **New native self-tests (now 104/104):** stands steady; segments appear in the snapshot; absorbs 40 N·s; steps for 60 N·s; knocked flat, it reports impacts and is up within 14 s; weak traits fall at 40 N·s.

### Impulsis changes (for Sydney)
- Bodies now report support: the contact normal sum, centre of pressure and ground points from manifolds facing up (`Body.support/cop/groundPoints`).
- Configurable step rate and iterations (`setRate`), plus a public `tick()`.
- Jointed bodies no longer collide with each other. A multiset of jointed pairs is kept, and it's forgotten when a joint is removed or breaks.
- **Joint drives:** per-axis damping toward a target rate, solved as a soft constraint (γ = 1/(c·h)), clamped to a min/max impulse and warm-started at 0.8. This is the piece that made Sydney stable.

### Engine API 2.4
- `CMD_RAGDOLL_INTENT` (51), `CMD_RAGDOLL_TRAITS` (52), `EVT_RAGDOLL_IMPACT` (38).
- The ragdoll snapshot grew to 624 bytes. It now has activity, grips, effort, balance and the 13 segments' orientations and centres.
- `ASTRATTO_MESH_LEAF_CUBES`, `RCMD_LENS` (15), `RCMD_SMOKE` (16), `PASS_MOTION_BLUR`, `PASS_DEPTH_OF_FIELD`.
- Java bridge: `CommandBuffer.ragdollIntent/ragdollTraits`, `Snapshots.Ragdoll` fields, `RenderCommands.lens/smoke`, and `EventSink.ragdollImpact` (fanned out by `Game` like the other events).
- `docs/API.md` describes Sydney 3 and the 2.4 renderer features.

### Renderer
- **Advanced voxel foliage.**
  - Every leaf cell is now a cluster of leaf cards: three on the crown's surface, one inside, with hashed yaw and tilt, double-sided.
  - The leaves are cut procedurally in the shader: two twigs of 8–11 pointed ovals per card, alpha-tested.
  - Vertex wind sways the cluster with the branch and flutters each card on its own.
  - Lighting is wrap lighting plus a translucency (backlight/SSS-style) term when the sun is behind the leaves. Shadows use the same alpha.
  - `ASTRATTO_MESH_LEAF_CUBES` brings the old cubes back.
- **Smooth shadows.** Sun shadows are now PCSS: a blocker search sets the penumbra width, then a Poisson-disk filter rotated by interleaved gradient noise. Contact shadows are sharp, distant ones soft. This needed a raw-depth sampler object on unit 13, because unit 12 clashed with exposure.
- **Volumetric smoke, dust and steam.** `RenderCommands.smoke` sets up to 32 volumes. Each is marched separately (12 steps) over just the part of the ray inside it, lit by the sun's shadows, the lamps and its own glow, and it billows and rises over time. The game uses them for campfires and the furnace plume.
- **Water reflections.** Screen-space reflections of the real lit scene, marched through the HDR image and depth, on a surface rippled by crossing wave trains. They fall back to the sky where the march misses.
- **Motion blur and depth of field** now exist as a lens pass after TAA, and they're **off by default** (`Config.motionBlur`, `Config.depthOfField`, with shutter and aperture settings).
- **Material reflectance.** Each block layer now has its own roughness/metalness (`Blocks.layerMaterials`): wet stone, polished metal, rough wood, and so on.
- Volumetric light, godrays and volumetric fog were already in; they now composite correctly with the smoke.

### The game side
- **Reactions** (`combat/Reactions.java`). When a blow staggers someone (or hits someone already reacting), Sydney takes them over: J = clamp(14 + max(0, impact − 0.6·stability)·1.6, 14, 170) N·s at the hit point.
  - Their skills become traits.
  - Their AI pauses while their body is in Sydney's hands.
  - They're handed back to the animated figure when Sydney reports RECOVERED (or STUCK, or after 14 s), standing where their feet are and facing where their trunk faces.
  - A killing blow during a reaction reuses the same body and goes limp.
  - `Reactions.cancel` ends a reaction at once. Teleports and unloads call it, so a moved NPC never leaves a body behind.
- **Held weapons stay in hand** on a body in Sydney's hands: blades along the forearm, shields across it.
- **Disarming.** Going down hard knocks the main-hand weapon loose about half the time. It lands where the hand was and shows "Disarmed!". The roll is a hash of id and time, so replays match.
- **Impact sounds.** `EVT_RAGDOLL_IMPACT` plays a thud scaled by speed. A head hitting the ground above 6.5 m/s hurts (never fatally on its own).
- **Galleries.**
  - `RPG_GALLERY_ONLY=scenery` shows foliage, water, smoke, a DOF view and the campfire.
  - `RPG_GALLERY_ONLY=reactions` films blows of 35/65/160 N·s on a bandit. It now resets him between blows and the camera follows him.

### Bugs fixed outside Sydney
- **The player's belongings could vanish on death.** The grave packs the inventory into its own grid, and anything that didn't fit was silently dropped. That was the "shortbow ledger 2 but 1 exist" failure. Overflow now lies on the ground beside the grave.
- **Killing a bandit made you a murder suspect.** Gore evidence didn't know outlaws from townsfolk. A guard who found a dead bandit started a murder hunt, then attacked a bloodied player as a suspect. The theft-bounty checks failed as a knock-on (the bounty came out at 1,000). Bodies and blood of bandits, brutes and animals are now remarked on but not investigated.
- **Revived bandits came back friendly.** `revive` didn't restore hostility, so after the gore test a bandit stayed non-hostile and killing it counted as murder. Revive now restores it from the role.
- **`Actor.store` gave up too early on stackables.** If the first grid holding that kind was full, it didn't try the other grids. The caravan crate fill also ignored a failed add (cargo now stays in stock instead).
- **The memory invariant rejected unknown culprits.** "Remembers a crime by unknown #0" is a designed state: a body found, culprit unknown. It's allowed now.
- **Arrows vanished from save files.** Arrows in flight weren't saved, so a save during a shot lost them. That's what broke "item conservation" and "invariants hold after load". Saving now lands every projectile where it is first (`CombatSystem.landAll`).

### The `gamefixes.md` bug list
- **Shoe selection did nothing visible.** Loose trousers are painted onto a separate overlay box that stands off the leg (`Skin.leg`). That overlay copied the whole leg length *before* the shoes were painted, so every shoe was hidden under the trouser box. The overlay now stops where the shoe begins: trousers fall over low shoes and tuck into tall boots. A new gallery mode, `RPG_GALLERY_ONLY=catalogs`, renders only the creator catalogues for quick checks.
- **Couldn't sprint in first person.** First person counted as "aiming" (`strafing()`), and aiming rules out a sprint. It also capped first-person walking at 60% speed. `aiming()` is now its own check (a raised guard, drawn bow, charge, blow or cast). First person still faces the view, but it can sprint straight ahead and walks at full speed.
- **Some NPCs could only be talked to by walking into them.** An NPC's focus volume was one sphere at chest height. Behind a counter or desk, the camera ray to it hit the counter first. People now have a focus *column* (waist, chest, shoulders, head; `Interactable.focusColumn`), and the assist cone accepts a line of sight to any part of it.
- **Respawning with your gear still on.** Death deliberately kept equipment. Now everything worn goes into the grave with the pack (overflow on the ground beside it), and you come back in just your clothes.
- **The debug gear kit crashed the game.** It asked for an item called `backpack`, but the item is `basic_backpack`, and `Items.get` throws on unknown ids (the null check after it could never fire). Fixed the id. Unknown ids are now reported in the debug chat instead of crashing, and anything that doesn't fit is put at your feet.
- **Fights pressed chest to chest stalled.** A fighter 0.2 m from their target never counted as facing it, so they never swung. They now step back to make room. Sydney's hand-back also never stands a body inside someone else any more.
- **Guards kept eating mid-fight.** Goal inertia let a very hungry guard's EAT (0.85) hold against FIGHT (0.9). Fight and flight now always break inertia.
- **Character creator, redone** (`ui/Creator.java`). The old screen was a single column of `< value >` rows. Now it's a full-screen interface:
  - **Tabs:** Body, Hair, Clothing and Finish across the top.
  - **Body:** big selectable cards for sex and build, and a 12-swatch skin grid.
  - **Hair:** named two-column lists for hairstyles, swatch grids for hair and facial-hair colour, and arrows for facial hair.
  - **Clothing:** Top / Legs / Shoes sub-tabs, each with a list of the twenty styles and a 24-colour palette.
  - **Finish:** the name and a summary of every choice. Click a line to jump back to its tab.
  - **Controls:** randomise the tab or everything, Back / Next / Start, and turn buttons as well as dragging.
  - **Camera:** frames the figure in the free space right of the panel, and eases in on the face for the Hair tab (`Preview.focus`).
  - `rows()` and `step()` are unchanged, so the acceptance checks on the catalogues still apply.
- **Walking takes strides.** The rig's step-rate cap was based on hips held almost at full leg height, so a planted foot could only travel about ±0.1 m under the body. At walking speed that forced very rapid, tiny steps: the "tip-toeing". A walk now settles the hips up to 1.3 texels on softly bent knees (`Rig.WALK_BEND`, eased out at a run), and the cap uses that height with a 0.9 reach margin. The forced step rate at full walking speed drops from about 7 cycles a second to about 2.5.
- **Bodies can be searched, and their equipment mirrors the body** (`objects/Corpse.java`). Before, a dead NPC's belongings were scattered on the ground and their worn gear stayed out of reach.
  - Now what they carried goes into a corpse container that follows the ragdoll. What they wear stays on the body.
  - The search window shows the carried items plus a *Wearing* strip of their actual equipment. Taking a piece takes it off the body you see, and *Take all* takes both.
  - Outlaws' bodies are free to loot. Anyone else's belongings are still theirs, so taking them is theft.
  - When an outlaw respawns, whatever is left in their old body falls to the ground, so nothing leaves the ledger.
  - Corpses save and load with the body they belong to.
  - A held weapon stays drawn in a dead hand until it's taken. A bandit's still falls from his hand as before.
- **Worn arrows are drawn.** The ammunition slot was the one piece of worn gear never shown. Arrows now ride in a quiver over the right shoulder blade, beside a pack if there is one.
- New acceptance section, *Reported bugs stay fixed*, covers the gear kit, first-person sprint, talking across a counter, and dying with gear on.

### Still failing (to fix next pass)
Last complete run: **195 / 200** (up from 176 / 194 at the start of the pass). Every world-state invariant now holds. The five failures in that run:
- The arrest pair ("resisting and beaten down…" and "no guard strikes again"). Cole and the player ended up 0.2 m apart and he never swung. Fixed since then: the back-off target is now far enough away for the agent to move, and Sydney's hand-back separates bodies.
- "The guard investigates the noise". He chose INVESTIGATE but barely moved (6.0 → 5.7 m). Not investigated yet.
- "A guard who sees the body searches". Cole wasn't searching. I've added a diagnostic line to that check, but it hasn't produced output yet.
- "Sprint in first person". This was the check, not the game: the gear-kit test just before it loads the player down, so the sprint is burdened. The check now compares against the burdened walking speed.

The fixes since that run (fighters making room, the sprint check, the creator turning to face you on the Hair tab, corpse looting and its tests) haven't been through a full run yet. The last attempt was stopped because the machine ran low on memory.

---

## Pass 2: bugs, pause, pursuit, gore, music, animal sounds, day/night (Version 0.0.4)

### The twitching legs: the real causes (and how we know they're fixed)
This bug survived the earlier rig rewrite because the gallery films at a fixed 60 fps, which hid it. I added a measurement and found three separate causes.
- **The measurement.** `RPG_GALLERY_ONLY=motion ./gradlew gallery` now runs a twitch probe:
  - It walks, turns, strafes, backpedals, sprints, stops and creeps the figure under uneven frame times (7–40 ms, with the odd long frame).
  - It samples the knees and hands every frame in body space.
  - It reports how far each sample strays from the smooth path between its neighbours.
  - `RPG_TWITCH_FIXED=1` gives the same run at a fixed 60 fps for comparison, and `RPG_TWITCH_DUMP=<motion>` prints the frames.
- **Cause 1, the big one (engine): the ground flag flickered.**
  - Landings and stair steps leave a mover 3 mm above the floor. Next step it fell those 3 mm and was "off the ground" for a step, then landed and was lifted again.
  - Uneven frames turned that into random flicker. The rig flashed the airborne tuck pose for a frame, which was the twitch. The player's jump check (`grounded`) also failed on those frames, which explains "sometimes the player can't jump".
  - Fix: a body within 2 cm of solid ground, not rising, counts as standing (`GROUND_EPS` in `bodies.cpp`).
  - New native self-test: a standing mover is grounded in all 300 uneven frames and doesn't bob.
- **Cause 2 (engine): positions weren't interpolated.** Movers step at 120 Hz and the snapshot handed over the raw last step. A frame between steps showed the body up to a step behind, so it stuttered while the feet stayed pinned to the world. The mover now keeps its previous step's position, and the snapshot blends between the two by the time left over (`Mover::seen`, the same idea as the rigid-body poses). Teleports snap.
- **Cause 3 (rig): raw velocity, too deep a crouch.**
  - The rig drove step targets and body lean from the raw mover velocity, which jumps from step to step. It now uses a smoothed velocity (70 ms).
  - Walk start/stop has hysteresis (±25% around the still threshold).
  - The creep crouch was 4.5 px out of a ~7.5 px leg, a full squat. The planted leg ran out of reach, the hips collapsed to the floor and snapped back. The crouch is now 2.3 px (`SNEAK_CROUCH`).
  - Cadence now adapts so a stance never needs more than ~82% of the leg's comfortable reach at the current hip height.
  - A planted foot past reach no longer drags the hips down.
  - NPC body facing also uses a smoothed velocity, and ignores sub-2° wobble.
- **Results** (mean / worst departure, cm):

  | Motion | Before | After |
  |---|---|---|
  | Idle | 5.14 / 21.5 | 0.02 / 0.15 |
  | Walk | 8.19 / 64.6 | 1.03 / 7.0 |
  | Creep | 11.2 / 99.0 | 0.25 / 4.9 |

  What's left in walk/sprint is normal limb curvature over long frames. The fixed-60 fps run gives the same figures.

### Jumping
- Engine: a jump request now waits up to 0.2 s for the ground, and there's 0.12 s of grace after leaving an edge (no double jumps). Before, a request made in the air was kept forever and fired on landing.
- `PlayerController`: a 0.18 s jump buffer plus the same grace period. The late part of a swing's recovery (from 40%) can be jumped out of.

### Clicking menus swung the weapon
- The click that picked a dialogue choice or closed a window was also read as a mouse press once the menu closed and the cursor locked, so the release fired a swing.
- `Main` now arms each mouse button only when it goes down while the mouse is looking, not over a menu. It disarms on release or whenever the interface takes the mouse.

### Pause pauses everything
- `Hud.paused()`: true for any panel scene, any window (chest, shop, station, cabinet, kiosk), a dialogue (including the game menu) and the hold-E menu.
- Engine `Game.pump()` (new): runs the frame's commands with dt 0. No module updates, no physics, AI or clock. Events still get delivered, so UI sounds and music keep working.
- New engine command `ASTRATTO_CMD_AUDIO_PAUSE`: SFX, voice and ambience voices are held exactly where they are (silent and not advanced) while the UI and music buses play on.
- The world is drawn with dt 0 while paused, so figures freeze mid-pose. The HUD now keeps its own clock (`Hud.now` advances by the frame time), so fades, blinks and wanted-panel pulses still move.

### NPCs lose the player (no more omniscience)
- **The cause.** `AiSystem.threatFor` kept a fight target within 30 m and the last attacker for 12 s, whether or not the NPC could see them, and `Brain.fight` walked to the target's true position.
- **The fix.** A target counts only if the NPC can place it: seen now, or seen within 1.2 s (stepping behind a post doesn't make anyone vanish). Otherwise `AiSystem.lose()` starts a `Hunt` (new class) at the last known location from that NPC's own `Stealth.Aware` record. During that 1.2 s grace, `Brain.fight` heads for the last-seen spot, not the true one.
- **New goal `SEARCH`** (score 0.86 for combatants). `Brain.search()`:
  - Runs to the last known location, nudged a little along the direction they were last seen going.
  - Looks round.
  - Then checks spot after spot inside an area that grows with time since the last clue (3 m + 0.55 m/s, capped at 16 m). It favours spots it can't currently see (round corners), spots near cover, nearby spots, and spots no other searcher is checking.
- **Clues move the search.** Any new sighting or sound in the NPC's awareness (`keepHunt`) moves it. Footsteps heard while you flee steer the search, which comes for free from the existing stealth hearing.
- **Squads.** Guards of the same force within 35 m join a search. Whoever re-sights the target shouts "Over here!", which is a real noise, and gives the others the position.
- **Giving up.** Guards hold out 55 s without a clue, lawmen 25 s (and won't leave their post by more than 22 m), bandits 22 s (camp leash). Afterwards they're "vigilant" for 90 s: searching or vigilant eyes are 25% keener.
- **Events:** `SearchStarted`, `SearchJoined`, `SearchFound`, `SearchAbandoned`.
- `AiSystem.searchAreas()` merges nearby searches for the same quarry into areas, for the HUD.

### Minimap and wanted GUI
- **Minimap:** each search area is a filled patch, red for you and blue for an unknown culprit. It's clipped to the map's rim, has a pulsing dashed edge and a "?" at its centre. A chevron on the rim points to areas off the map.
- **Wanted panel, rebuilt:**
  - Heat pips (0–5) from the total bounty.
  - One status line: SPOTTED (pulses), SEARCHING (with a draining bar showing time until they give up), HIDDEN, or NOT WANTED yet.
  - Bounties per jurisdiction.
  - Each witness heading to report you, with metres left and a progress bar, and witnesses who saw but haven't set off.

### Gore and dismemberment (new `combat/Gore.java`)
- **Hit location.** `Gore.partAt` reads the part from the contact point: height up the body, and how far to the side of the spine. Parts are head, torso, arms and legs.
- **Wounds.**
  - Each part keeps the damage it has taken as a share of full health.
  - Slashes (1.2), piercing (1.0) and blunt (0.25) add bleeding. Magic doesn't bleed.
  - Bleeding costs 1.8% of max HP per second at 1.0 (the player: 0.6%, and never below 1 HP). It clots with a 28 s time constant, or 140 s while a limb is gone.
  - Bleeding to death is credited to the last attacker (`CombatSystem.bleedOut`, event `BledOut`).
- **Blood.**
  - Droplets are the same square particles the splashes and rain use (`Fx.blood`, new `Fx.drip`).
  - Stains go onto the world's faces through the engine's existing decal layer. The game had never connected it; `Gfx.stains()` now feeds it dynamic `BLOOD` marks, with no static grime.
  - Spatter rays follow the blow and downward, so blood lands on the wall behind and the floor below.
  - A pool forms under each wound and grows round bodies. Moving wounded bodies drip a trail.
  - Blood that falls in water washes away.
- **Dismemberment.**
  - A slash to an arm, a leg or the neck takes the part off when the blow is deadly (at least 12% of max HP), or, for anyone but the player, when that part has taken 60% damage and this cut is at least 16%.
  - The head only comes off on a deadly blow, and the player never loses a limb while alive.
  - The part becomes an Impulsis rigid box thrown along the blow, drawn from its owner's own figure segments (`Gfx.limbs`).
  - The body draws a dark stump in its place, alive or as a ragdoll (`goneSegs`).
  - A lost sword arm drops the main-hand item, a lost shield arm the off-hand. A lost leg knocks the body down: legless NPCs can't walk and call for help.
  - Anyone maimed tries only to flee.
  - There's a scream from the death-cry bank (the only voices used) and a `Dismembered` event.
- **Limping.** A badly hurt leg slows you (`Gore.legs`): down to 60% when hurt, 12% when gone.
- **Evidence.**
  - Blood pools, bodies (any dead human NPC still lying there) and severed limbs are `Gore.Evidence`.
  - NPCs notice them as they think (`Gore.perceive`): in their eye cone, a clear sight line, and enough light for the size. A body is easier to see than a stain. Each NPC reacts to each piece once.
  - Guards and lawmen search for an unknown culprit (a `Hunt` with target 0, shown blue on the minimap). A body brings the guards within 40 m.
  - Townsfolk cry out and report what they found. `Law.report` with no culprit adds no bounty; it sends the jurisdiction's guards to search there.
  - Small spots of blood just make people look.
- **Suspects.**
  - Striking close gets blood on you (`Actor.bloodied`, 0–1). Standing in water washes it off; otherwise it fades over about 7 minutes.
  - A guard searching for an unknown culprit who sees someone bloodied within 10 m of fresh blood or a body (under 90 s old) names them. That creates a witnessed `MURDER` memory, and the normal law takes over.
- **Not yet:** animals bleed but aren't dismembered (their models aren't segment-based), and wounds/evidence aren't saved.

### Music (new `audio/Music.java`, engine `audio/MusicStream.java`)
- **Assets.** The eight tracks from `assets to add/Sounds/Music` are in `resources/sounds/music/`, lower-cased.
- **Engine streaming (API 2.3).** A 14-minute MP3 decoded whole would have been about 300 MB, so the engine now streams.
  - `astratto_stream_create` makes a sound backed by a lock-free single-writer/single-reader ring at the mixer's rate.
  - `astratto_stream_write` tops it up from any one thread.
  - A voice plays it like any sound, holds silent if the ring runs dry, and ends after the final write (`ASTRATTO_STREAM_END`) drains.
  - `MusicStream` decodes MP3 frame by frame on its own thread, resampling to 44.1 kHz stereo and keeping 4 s ahead.
  - Releasing a stream's sound ends its ring, so a fading voice can't hang.
  - The engine's sound table is now locked, since a stream writer can look it up from another thread.
  - New self-tests: the ring takes only what fits, playback frees room, a paused bus is silent and isn't drained, playback resumes, and the voice ends after the last write.
- **Moods.** The game reports a `Mood` each frame (`Rpg.mood()`):
  - COMBAT: anyone fighting you within 18 m, or a blow traded in the last few seconds.
  - HUNTED: someone searching for you within 45 m.
  - Otherwise by place and time of day: wood, town (a jurisdiction), or open ground, by day or night.
- **Behaviour.**
  - Each mood has its own tracks.
  - A mood has to hold for a moment before the music follows (combat at once; 5 s to leave combat).
  - Exploration pieces play once, followed by 25–75 s of silence. Combat and hunted pieces loop.
  - Pieces fade in and out. Moving between day and night in the same place keeps the current piece.
  - `sacred_stinger` plays over the score (which ducks) when a quest is completed.
  - The menu (character creator) has its own mood.
  - The score drops to about 65% volume during pause.

### Animal sounds
- Downloaded high-quality recordings with free licences (credits in `resources/sounds/CREDITS.txt`):
  - BigSoundBank (CC0): cow, hen, rooster, pig, horse, sheep, dog, cat, ducks.
  - Wikimedia Commons public domain: wolf howls, and grizzly vocalisations and roar from the NPS Yellowstone library.
  - Coyotes are CC BY-SA 4.0 (Rybkovich), the only clip needing attribution.
- **New `Sfx.calls()`** cuts long field recordings into separate calls at load time:
  - It uses a 20 ms loudness envelope against the recording's 95th percentile, not its loudest instant.
  - Calls are taken loudest first, with lead-in and tail, faded, and levelled to the same peak.
  - Short single-call files are used whole.
- **Every species key now has a bank** (22 banks in all); before this only the fox was even wired.
- Per-bank pitch is used where a sound is borrowed: the mountain lion is a cat's growl an octave and a bit down.
- Cockerels crow, mostly around dawn.

### Day and night
- **Engine bug:** the moon's direction was taken as the opposite of the light direction. At night the light direction is already the moon's, so the moon was drawn under the horizon and never seen. It's now always opposite the true sun (`moonDir_`).
- **`Gfx.environment` rebuilt:**
  - The sun rises in the east and arcs south.
  - Horizon and fog colour blend from day haze to a warm band while the sun is low, then deep blue at night.
  - The light tint is golden at the low sun and cooler under the moon. The ambient floor rises a little at night.
- **`Stealth.daylight`** is now a smoothstep with a longer twilight. Night is 0.05; full daylight about 1.25 h after sunrise. Gameplay light and the picture still use the same curve.
- `RPG_GALLERY_ONLY=daycycle` writes a 16-hour contact sheet for review.

### Skills
- The skill guide now shows "??? (revealed at level N)" for unlocks above your level.
- `Progression.celebrate` works out what the new level revealed. It announces each item in chat, and the level-up banner lists up to four.

### Character creator
- **The head bug:** the preview actor's look direction was never set, so the rig turned the head up to 1.2 rad toward an old heading. At some angles you saw the back of the head, which looked like the face had vanished.
- The camera is now fixed in front, and the figure itself turns: dragged with the mouse, or slowly on its own.
- `RPG_GALLERY_ONLY=creator` writes screenshots of several looks.

### Smaller fixes
- `Memory.crime()` didn't include VANDALISM or LIVESTOCK, so those crimes could be witnessed but never reported. They now count, with bounties of twice the value (minimum 5) and the value (minimum 10).
- Mara: if she filled the trough in the last 90 s and six or more eggs are waiting, the eggs come first. Before, a hungry herd could keep her refilling forever.
- A lawman losing sight of a guard he was fighting could recruit that same guard to search for himself. Searches now never include their own quarry.
- `Rpg.teleport` resets an NPC's smoothed motion.
- The native build-info string reports API 2.3.

### Tests
- Native self-test: **97/97**. New checks cover streams, bus pause and steady grounding.
- New acceptance sections:
  - "Pursuit": the guard keeps only the last known location, searches near it rather than where you are, and gives up.
  - "Gore": hit location, a severed limb as a rigid body, stains and body evidence, getting bloodied, a guard searching after finding the body, a bloodied suspect, a maimed brute dropping his weapon and fleeing while he bleeds, and revival healing.
  - "Pause": paused frames hold the clock and the townsfolk; menus pause.
- New gallery modes: `motion` (with the twitch report), `daycycle`, `creator`, `gore`.

### Still to do from `gamefixes.md` (honest list)
- Renderer: volumetric light and godrays, volumetric fog and smoke, voxel leaf-card foliage, water reflections, smooth shadows, motion blur and depth of field (off by default), the 64×64 texture pass and per-material reflectance.
- Sydney: Hill-type muscles and central pattern generators.
- Corpse looting where NPCs drop only their weapon, merchant stock held physically in the world, and burden for NPCs.
- Animal dismemberment, and saving wounds and evidence.
- Then the GDD core systems.

---


## Pass 1: the first pass on `gamefixes.md` (Version 0.0.3)

### Engine (theastrattoengine)

### Impulsis: new rigid-body physics (native, C++)
- New engine service in `native/src/impulsis/` (`imath.h`, `space.h/.cpp`, `collide.cpp`, `impulsis_service.h/.cpp`).
- Shapes: box, sphere, capsule and convex hull (up to 64 points).
- Broadphase is sweep-and-prune over body boxes, plus the voxel cells under each body.
- Narrowphase:
  - SAT with face clipping for box–box and box–cell.
  - GJK/EPA for hulls.
  - Closest-point tests for spheres and capsules.
- Voxel faces between two solid cells are ignored, so boxes slide over multi-cell floors without snagging.
- Solver: sequential impulses with accumulated, clamped impulses. Friction stays inside the Coulomb cone, Baumgarte handles position drift, and contacts are warm-started.
- Speculative contacts (4 cm) and a "bullet" sweep stop fast bodies from tunnelling.
- Rolling resistance for spheres and capsules.
- Islands sleep and wake together.
- Joints: ball, hinge (limits, motor or friction), slider, cone-twist and fixed. Limits are speculative, so a stop doesn't bounce back. Any joint can break above a set force.
- Character movers appear inside Impulsis as capsule stand-ins:
  - They push light bodies, such as a door leaf, out of the way.
  - Heavy or pinned bodies push them back, in proportion to mass.
- C interface is now API 2.2: new handle kinds `RIGID` and `JOINT`, commands 160–167, events `RIGID_IMPACT`, `JOINT_BROKEN` and `RIGID_SLEEP`, and a `RIGIDS` snapshot with smooth poses blended between physics steps.
- Java bindings updated: `CommandBuffer`, `Snapshots.Rigids`, `EventSink`, `Game` fan-out and the layout table. The stats struct grew because there are two more handle kinds.
- New native self-test file `tests/impulsis.cpp` with 15 checks (falling crate, stack, rolling ball, hull, hinged door pushed and latched, joint breaking, slider, bullet, release, determinism). The full self-test is now **90/90**.

### Other engine fixes and changes
- **Movers** now accelerate as a vector, so a change of direction traces a curve instead of turning each axis separately.
- **Door cells** may now be non-solid, with the physical leaf doing the blocking. Routes, walkability and sight treat a door cell as a door whether or not it's solid.
- **Agents fixed:** after cutting a corner, an agent never reset its progress mark, so it could be falsely reported as stuck while walking normally. That bug predates this work and is now fixed.
- **Agents slow down for sharp turns**, instead of orbiting the route cell they need to reach.

### Game (rpggame)

### Animation and movement
- **Rig rewritten** (`render/Rig.java`). This fixes the twitching legs:
  - Planted feet are now locked in world space.
  - Each step lands where the body will be when the foot comes down, in any direction.
  - Standing feet take corrective steps when the body turns over them.
  - The hips ride on a damped spring with weight shift, so nothing pops.
  - The torso leans with acceleration and into turns, and the head tracks where the character looks.
- **Gait timing** (`actor/Gait.java`): step length is limited by leg reach, and the stance share drops at jogging speed. Cycles now run 0.5–0.75 s.
- **Player movement** (`actor/PlayerController.java`):
  - Speed builds up and falls away gradually, and turns follow an arc.
  - A sharp reversal at speed brakes first.
  - Sprinting is committed: wide steering, plus a short recovery after stopping short.
  - Aiming or guarding slows the player to a strafe and blocks the quick turn (tap S twice).
  - Attacks, guards and dodges slow the body during their start-up and recovery.
- **Facing:**
  - The body now has its own facing, separate from where the camera aims.
  - NPCs turn at a body's pace instead of snapping round.
  - NPCs only throw a blow at something they're actually facing.
  - NPCs build and lose speed gradually too.
- **First person:** you now see your own body and legs (the head still casts its shadow), and the camera rides the animated head.
- **Attacks:** strikes now follow keyframed paths (hand position plus blade direction, Catmull-Rom curves) for each move. Combat contact samples the same path, so what you see is what hits.
- **Gallery** (`./gradlew gallery`) now produces contact sheets of every third frame for each motion, plus sheets for each character-creator catalogue.

### Character creator (GDD 2.1)
- The catalogues now match the design doc:
  - 20 hairstyles per sex (voxel-modelled; long hair hangs from the neck).
  - 20 facial-hair presets (stubble is painted onto the skin).
  - 20 tops, 20 legs and 20 shoes (`render/Outfit.java`), with hems, skirts and robes, collars, hoods, cuffs and toe shapes.
  - 10 natural hair colours and 24 garment colours (the GDD swatches).
  - Masculine, Heavy and Skinny silhouettes.
- The female hitbox is 0.45 wide, in proportion to her narrower frame.

### Law
- **Guards and arrests:** guards no longer keep attacking after an arrest.
  - An arrest now settles every grievance from before it (blows traded and grudges), and guards never fight a prisoner.
  - An acceptance check covers the forced-arrest path.
- **Carters** treat an active shipment as a committed duty, so hunger or loneliness doesn't pull them off the job.

### Doors and glass
- **Doors are physical:**
  - Each leaf is an Impulsis box on a hinge. "Closed" means latched, with the hinge limits locked at 0°.
  - Opening unlatches the door and swings it in with the hinge motor.
  - Anyone, the player or NPCs, can push an open leaf.
  - Door cells only block sight and light while the leaf is shut.
- **Glass breaks:** blades, arrows, spells and bodies thrown into a pane shatter it into 10–15 convex-hull shards.
  - The noise draws NPCs.
  - Breaking a window in a town is a witnessed VANDALISM crime.
  - Shards fade out after 60 s.

### Inventory (in progress)
- **Spatial grid inventory** (`item/Inventory.java`):
  - Items have footprints and can be rotated.
  - A character has a 10 × 4 base grid.
  - Worn backpack (8 × 6) and pouch (4 × 2) slots each add a separate grid.
  - Bags keep their contents when moved.
  - A non-empty bag can't go into another container.
  - Chests are 10 × 10, graves 10 × 4 and cart crates 10 × 6.
- Every item has a weight. Carried weight sets a burden factor; the movement penalty from it isn't hooked up yet.
- Saves store each item's position and rotation, plus bag contents.
- Actor-level `store`, `count`, `has` and `takeItems` work across all of an actor's grids.

### Audio
- Real recorded sounds replace most synthesized ones (`audio/Sfx.java`):
  - Footsteps that depend on the surface underfoot.
  - Weapon swishes; flesh, metal, wood and glass impacts; doors; coins.
  - The provided UI sounds and the devmap ambience loop.
- Human voices use only the provided death cries. Hurt sounds are the opening moment of the same recordings.
- Downloaded sounds are all CC0 (Kenney, OpenGameArt). Sources are listed in `resources/sounds/CREDITS.txt`.
- All 55 provided icons are in `resources/ui/icons/`; the HUD doesn't use them yet.

### Interface overhaul (gamefixes UI)
- **Fragments instead of screens** (`ui/Fragment.java`): every HUD piece is a fragment that fades in only while its game state is live, or for a while after a UI event it listens for. The vitals appear when something is below full, the target read-out in a fight, the wanted panel when the law is involved, and the chat when someone speaks. When nothing is happening, only the minimap is on screen.
- **Event bus** (`ui/UiEvents.java`): committed domain events about the player become UI events (`combat`, `law`, `inventory`, `coins`, `container`, `xp`, `quest`, `world`). The interface adds `area`, `scene` and `chat`. Raw events also pass through as `event:<Type>`.
- **Scene state machine** (`Hud.go`): World, Inventory, Character, Skills, Journal, Magic and Debug. A scene is a list of fragment groups, and groups are reused (the equipment doll is in Inventory and Character; the vitals and action bar go into every scene). Keys: I, C, K, J and M toggle scenes; F1-F7 still work.
- **Flat, low-contrast look** (`ui/Style.java`): slate cards with hairline edges, one muted gold accent, and no stone or bevels. The character creator uses it too.
- **Minimap** stays top right with the same NPC colours (yellow, and red for hostile). Its markers now use the provided map icons, found from what's actually in the world (stations, stalls, graves, beds, merchants, lawmen, the quest giver).
- **Wanted panel** shows bounties per jurisdiction, witnesses who saw a crime, witnesses on their way to report it, guards searching, and guards coming for you.
- **Action bar** sits at the bottom centre with 10 slots on 1-0. A slot can hold an item, a blessing or a spell. Drop an item on a slot to bind it; right-click clears it.
- **Inventory panel** (`ui/InventoryPanel.java`):
  - Shows every carried grid as it really is, with category tabs and a name filter that dims non-matches instead of reflowing.
  - Drag to move items within or between grids, and R turns the held item.
  - Drop on an equipment slot to wear it, on the bar to bind it, into an open container or shop, or outside the panel to drop it on the ground.
  - Item actions, shop selling and container deposits now work from any grid, not just the base one.
- **Speech bubbles:** NPC speech and dialogue lines appear in a pale bubble above the speaker's head. Dialogue answers sit in a small card at the bottom.
- **Lua addon API** (`ui/Addons.java`, LuaJ 3.0.1, sandboxed):
  - Addons can add fragments with `when`/`wake` rules and draw them.
  - They can listen to any event; move, hide or show any game fragment; and put groups into scenes.
  - Faults are reported in chat and switch off only the faulty part.
  - Two examples ship in `rpggame/addons/` (session gold, low-health edge), with an API reference in `addons/README.md`.
- **Icons and sounds:** all 55 provided icons are packed into one atlas (`ui/Atlas.java`) and used for skills, tabs, vitals, map markers, the map key and the boss marker. Every provided UI sound now plays somewhere: scene open and close, tabs, buttons, hover, item pick-up and drop, coins, container open, quest start/complete/fail, the spellbook closing, fishing hook and reel, the new-profile button in the creator, and log-out.
- **Gold display** (`item/Currency.java`): white with no suffix up to 99,999, then yellow K, green M, blue B, and purple X from 2 billion.

### Every item modelled (gamefixes)
- `render/ItemModels.java` gives every item, about 170 of them, its own voxel model. Tiers differ by shape as well as colour: guards, grip wraps, pommels and gems, Dragon serrations, double-bit Dragon and Godly axes. Leather is stitched, mail is linked and plate is riveted. Each skillcape has its own trim and emblem.
- Metal voxels are flagged in the colour's alpha, and `render/VoxMesh.java` turns them into a separate mesh, so blades reflect as metal and grips don't.
- The same model is used in the hand, on the ground (long things lie down, the rest stand up) and as the inventory icon (`ui/ItemIcons.java`, ray-cast to fit the item's footprint). Worn backpacks and pouches are drawn on the body.
- `./gradlew gallery` now writes `items-1..3.png` contact sheets of every item.

### Fixes this round
- **Items no longer vanish:**
  - New `Rpg.grant()` creates new items in whatever the actor carries, or drops them at their feet when nothing has room.
  - Stock and containers only count what actually went in. This fixed the bread ledger mismatch, where the stall's goods row was too small for 2x1 loaves.
  - The shop storeroom is now 20 x 20.
- **Hunger during time skips** was 60 times too fast, so the woodcutter and carter spent the day eating. Hungry NPCs with money now buy bread at Store A.
- **Agents return to a destination** they reached and were pushed away from (engine: an arrived flag only counts while the agent is still there). This fixed the guard stuck "closing the distance" in the forced-arrest scenario.
- **Pickpocketing and stall theft** check whether the item actually fits, not free base-grid cells.
- **Shop sales:** the shop no longer loses coins when a buyer's purse is full; the coins drop instead.
- **Movement:** carried weight now slows the player: from 30 kg up to 60 kg, speed and acceleration fall to 55% and sprinting tires faster.
- **Acceptance test:**
  - Construction makes room first.
  - The witness-stopped check looks for the report itself.
  - Shipments create real demand at Store B.
  - The glass check accepts shards resting on the window ledge.
  - The worker check waits out a full trip.
  - A new "Interface" section drives the real mouse through drags (move, equip, bind, eat from the bar) and checks fragments, scenes, addons, the Gold display and the burden.

### Stealth (gamefixes)
- **New `ai/Stealth.java`:** each NPC tracks how aware it is of every actor, moving through Unaware, Curious, Investigating and Alert.
  - **Sight:**
    - The eyes see in a cone: 90 degrees to each side up close, narrowing to about 50 degrees out to 24 m.
    - Three sight lines go to the target's head, chest and knees, through the real cells. Walls block them, glass doesn't, and leaves and the smoke over a fire thin them out.
    - The result is scaled by the light on the target's block, whether they're creeping or running, the distance, and how sharp the watcher's eyes are. Guards see better than most, and sleepers barely see at all.
    - The light comes from the sky by time of day, from lamp light spread through the cells, and from nearby fires.
  - **Hearing:** footsteps, blows and breaking glass raise awareness of whoever made the noise, but never past Investigating. A sound tells an NPC roughly where to look, not who made it. Walls muffle it.
  - **Being seen:**
    - A clear sighting counts at once. A glimpse builds up over time.
    - An alert NPC keeps track of you even in the dark.
    - Out of sight, awareness fades: slowly when the NPC was sure, faster when it only suspected.
- **Behaviour (Brain):**
  - A curious NPC stops and looks (new goal `LOOK`).
  - An investigating NPC walks to where it last placed you and looks around the spot. This replaced the old version, which homed in on the player's real position.
  - NPCs ignore a neighbour walking openly at dusk, but notice a stranger, or anyone creeping.
  - Thieves creep while stealing.
- **Crimes and threats:** `canSee`, and through it witnesses, bandit aggro and guards spotting wanted people, now goes through stealth. A crime committed while creeping in the dark has no witnesses.
- **Sneaking:** hold Left Ctrl to creep. You move at 45% speed, crouch low and lean in, footsteps go from 2-4 m of noise down to 0.8 m, and you're less than half as visible.
- **HUD:**
  - While you creep, a marker over each NPC fills as it grows aware of you, showing "?" while it wonders or searches and "!" once it's sure. Outside of creeping, the marker shows briefly whenever an NPC's level changes.
  - A sneak meter above the vitals shows how exposed you are, how lit your spot is, and the furthest any NPC has got (Hidden, Glimpsed, Exposed, Noticed, Searched for, Detected).
  - NPCs react aloud ("Hm?", "Who's there?", "There you are!", "Must have been nothing.").
- **Night is dark now:**
  - `Gfx.environment` used to flip the below-horizon sun up to 15 degrees "for moonlight", so the renderer lit midnight like a late afternoon. The sun now stays where it is, and the renderer makes its own dim moon.
  - The exposure ceiling now scales with daylight (1.4 at night, 5 by day), so the eye still adjusts in a dark room by day, but moonlit ground stays dim and lamp light stands out.
- **Bandits** no longer walk into town to drink from the well when hungry. Outlaws forage at their camp. A long time skip had made one wander up to the player's bed.
- **Acceptance:** a new "Stealth" section with 11 checks: light by hour and under a lamp, exposure while standing and creeping, a dark theft with no witness, walls versus glass, the climb from Curious to Alert, fading, and hearing that leads to an investigation. The gallery now has sneak and night views.

### Animals (gamefixes + GDD 3.9)
- **14 species** (`animal/Species.java`): chicken, pig, horse, rabbit, fox, coyote, wolf, mountain lion, brown bear, duck, cow, sheep, dog and cat.
  - Each is data: the gamefixes hitbox, gaits, diet, prey, grouping, wariness and flight distance, whether it stands its ground, its natural weapon, gestation or incubation, litter size, time to grow up, carcass yields, and sounds.
  - Young are the same model at half scale; foals use 0.62.
  - Animals are full `Actor`s with their own Attack, Strength, Defense, Health and Agility levels, so they fight through the same combat rules as people. A bite, kick or swipe is a physical lunge from the chest, carries the animal's momentum on a pounce, and goes through `CombatSystem.resolve`.
- **One mind for all 28 profiles** (`animal/AnimalMind.java`):
  - **Senses:**
    - Sight uses the same stealth exposure people use, so a sneaking player gets closer, as does a predator stalking prey.
    - Hearing comes from the noises people and animals make.
    - Anything within arm's reach is felt or smelt.
    - Fear builds and fades, and the first animal to take fright alarms its group.
  - **GDD priority bands:**
    1. Danger and young: flee, warn (a bear or horse rears), defend, or run to its mother.
    2. Thirst and hunger: drink at the pond's edge, graze, eat at the trough, hunt, or scavenge a carcass.
    3. Following: a mother, the group, or (for a dog) its owner.
    4. Resting by night, or by day for nocturnal species.
    5. Courting.
  - Goals carry inertia, so animals don't flip-flop.
  - Predators stalk low and slow, then rush. A chased animal tires and slows, and a predator follows its chosen quarry beyond its usual range.
- **Life cycle** (`animal/Animals.java`):
  - Mammals become pregnant, then give birth: one event, each newborn a new individual with its mother and owner.
  - Birds lay a fertile clutch, sit on it, and it hatches.
  - Young grow up atomically: a baby becomes an adult, never an extra animal.
  - Breeding is refused, with a reason, without an adult female, a male nearby, rest since the last litter, food and water, calm, room in the habitat, and (for livestock) feed put out by the keeper.
  - Owned hens and ducks lay ordinary eggs into the nest boxes.
- **Habitats and the population ledger:**
  - The map has a wild wood (north-west: trees, cover, a pond, a rock den) and Mara's farm (south of the houses: a fenced paddock with a gate, an open barn with nest boxes, a duck pond and a trough).
  - Every change to a habitat's population is recorded with its cause. A new world-state invariant checks that each count equals its starting number plus births and hatchings, minus deaths.
  - Wild animals far from the player are held as exact cohort counts and come back only out of the player's sight, never one more than the ledger holds. Time skips age and breed the cohorts by the same rules.
  - Animals, clutches, cohorts and ledgers are saved and loaded.
- **Death:** the body stays as a carcass. It can be butchered once with a knife or blade for raw meat, rawhide, bones and feathers, and earns Cooking XP. Scavengers eat it, and it rots away after a day.
  - Killing someone's livestock is the crime `LIVESTOCK`, and butchering someone else's animal is theft. Witnesses work as for any other crime.
- **People and animals:**
  - You can pet and feed animals. A bond grows from repeated kindness spaced out over time, not from clicking.
  - Guards fight a dangerous animal near people, and everyone else gets away from it.
  - A mountain lion's young are cubs, a fox's are kits, and so on.
- **The farmer** (new `FARMER` role: Mara, a livestock farmer):
  - She carries feed from her chest to the trough, gathers eggs, sells the surplus at Store A, and buys feed there when she runs low.
  - The trough's sacks are opened into six rations. Grazers eat the paddock's grass first.
- **New items** (GDD 2.7 prices): raw meat, cooked meat (cooked on a range, with burnt meat as the failure), rawhide, wool, egg, milk, feather and animal feed. Each has its own voxel model.
- **Models** (`render/AnimalModels.java`):
  - Every species is a posable voxel figure (body, head, legs, tail, and wings for birds) at the same voxel size as people.
  - Coats: a cow's patches, a fox's white chest and black socks, a mallard's green head and white collar, a tabby's stripes.
  - Several species have colour variants.
  - Animation is driven by the gait: a walk's diagonal pairs, a gallop's bound, a rabbit's hop, a chicken's bobbing walk, and beating wings. Heads go down to graze or drink and come up to watch a threat. Dogs wag. Animals lie down to rest; dead ones lie on their side.
  - `./gradlew gallery` writes an `animals.png` sheet and gait films; `RPG_GALLERY_ONLY=animals` writes just those.
- **Fixes found along the way:**
  - **Paddock gate:** a gate with no roof on either side swung into whoever opened it. Gates now swing away from the opener, and NPCs start opening doors a little sooner.
  - **Close-range sight:** someone right beside you is now sensed within 2.2 m whichever way you face, or 1.2 m if they're creeping. There is also a smooth near-field bonus inside 6 m. Mara 2.7 m away in the barn's shade used to "barely see" the player at noon.
- **Acceptance:**
  - New sections: "Animals: the fourteen species" (catalog and hitboxes) and "Animals: ecology, breeding, people".
  - Their checks cover:
    - routines and a fox's stalk and kill;
    - pregnancy, birth, the calf following its mother, and growing up;
    - breeding refusals;
    - eggs gathered by Mara;
    - petting, hunting and butchering, and livestock crime with a witness;
    - cohorts, the ledger, and save and load.
  - `RPG_ONLY=animals,stealth` runs just the named sections.

### Removed
- The old fixed-slot inventory logic and the old door model that just removed blocks.
- The old 50-hairstyle, 22-beard and 16-outfit catalogues, which are replaced by the GDD versions.
- Most synthesized sound effects; they're kept only as fallbacks.

### Not done yet (as of pass 1)
- **Acceptance test:** 177/177, engine self-test 90/90.
- **Animal sounds:** animal noises are wired up but silent, apart from the fox's death cry; they need recordings.
- **Still to build from `gamefixes.md`:**
  - Gore and dismemberment, with blood as evidence that NPCs react to and guards search.
  - Corpse looting, where NPCs drop only their weapon and it falls physically.
  - Merchant stock that sits physically in the world, with restocks, discounts and markups.
  - Sydney's Hill-type muscles and central pattern generators.
  - Renderer features: godrays, smoke, leaf cards, motion blur and depth of field (off by default), soft shadows and material reflectance.
  - Burden for NPCs: it needs a per-agent speed scale in the engine.
- **After that:** the GDD core systems.
