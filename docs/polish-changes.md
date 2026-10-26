# Polish branch — requested gameplay fixes

Based on main `b63b2fc` (the uploaded build plus the title-preview launch fix). This change addresses the latest polish list; it retains the GDD's player-only XP progression. Monster encounter configuration assigns fixed combat profiles and never awards NPC XP.

## Behavior

| Request | Implementation |
| --- | --- |
| OS cursor on title screen | Apply GLFW's hidden cursor mode on the first unlocked call, and reset raw mouse mode when unlocking. |
| Monster looting treated as theft | Monster corpses are lawful loot, including loaded corpses from older saves. Human corpse ownership rules remain authoritative. |
| Sydney recovery | Contact-aware bracing, support/capture-point and velocity checks, feedback-controlled support until balance, and an explicit successful controller handoff. A new fall clears a stale success result. The game finds supported, full-height standing space and restores the mover at ground height rather than ankle-joint height. |
| Level/combat banners | Queue earned celebrations separately from the latest award, use the UI clock, and show them over every gameplay scene. A second skill award cannot overwrite combat congratulations. |
| Ghosts above dungeons | Flight uses the dungeon floor and local ceiling. Dungeon path goals stay within their region. |
| Developer coverage | `features` discovers every monster kind, animal species, NPC role/profession, authored resident, object fixture, furniture/workstation type, spell, prayer and dungeon from the live registries. `spawn`, `interact`, `cast`, `prayer`, `dungeon`, `ragdoll`, `weather` and `block` exercise the existing systems. Spawned NPCs and supported object prefabs survive saves; repeated loads retire old registrations and physical shapes. |
| Bank expansion shrinks the vault | Keep cell size fixed; append real rows and scroll/page through an eight-row viewport. Item positions and drag/drop use the original full grid. |
| Entrance collision | Dungeon entrance structural boxes have real static rigid bodies and participate in gameplay ray contact. Geometry comes from the same boxes as the rendered prop. |
| Spear/javelin attachment gap | Align the actual item mesh's tip with the physical impact point, accounting for its grip pivot. Apply the same convention to lodged and loose ammunition and thrown flight. |
| NPC store visits | Working merchants/tellers expose the public customer-side anchor. Other conversations select clear standing space; completion requires range and sight. Escort duties remain a commitment above unconfirmed distractions. |
| Bounty boards | Braced posts, a framed board, cap, ironwork and pinned warrant sheets; structural mesh boxes also supply collision. |
| Monster corpse decay | Start a three-second spatial erosion at 60 seconds. Animated material coverage and a lit edge replace an abrupt despawn. At completion, original unclaimed items fall to the ground. Saved corpse age survives reload. |
| Dungeon levels | Configure living residents on entry, deriving their actual combat level from their skills. Micro/macro encounters never exceed entry combat level; elite and boss encounters can exceed it. Retain health share, remember encounter configuration, and apply it to later lair births and loads. |
| Hunter search/ambushes | Assemble out of sight and behind the camera; defer when no suitable location exists. Travel from confirmed clues, search normally, approach inferred routes, and spend carried snares at real placement locations. Snares trigger on physical contact with the wanted actor. Hunts and their source survive saves; wanted humans can be pursued as well as the player. |
| Crime notification spam | One notification per crime type per 30 seconds; evidence, reports and bounty transactions continue to commit. |
| Fonts | Friz Quadrata for the main interface and celebrations; Morpheus for lore and journal headers; Arial Narrow for chat, tooltips, console/information and incoming damage; Skurri for large outgoing floating damage. Uses the supplied font assets. |
| Trap visibility | Pressure plates are always physically visible, larger and edged with readable contrasting seams/slots. Discovery still announces a noticed trap; Thieving still gates disarming. |

## Sydney investigation

The native lab reproduced a premature transition from getting up to standing without stable support. A folded electrical-collapse pose also exposed a height-only bracing threshold that could keep an already supported body repeating its all-fours phase. Recovery now depends on the body's contacts, support polygon/capture point, torso attitude and velocity; it does not play a recorded get-up clip or reposition the physical body on a timer. A collision-checked emergency handoff remains for an explicit stuck result or a prolonged reaction.

The controller changes follow the supervisory precondition approach in [Composable Controllers for Physics-Based Character Animation](https://www.cs.ubc.ca/~van/papers/2001-siggraph-composable.pdf) and the support/feedback principles of [SIMBICON](https://www.cs.ubc.ca/~van/papers/Simbicon.htm). [Learning to Get Up](https://tianxintao.github.io/get_up_control/) was also reviewed for staged, contact-supported recovery; this patch does not add learned policy assets.

The lab's `recovercheck` scenario covers four knockdown directions at three trait strengths. All twelve cases reach a supported recovered pose in approximately 5–9 seconds. Gameplay regressions also check electrical collapse, a starting player's recovery beside a wall, correct mover floor placement, and walking after recovery; the latter explicitly requires a native recovery rather than a timeout.

## Console examples

```text
features ghost
spawn monster:ghost 30
spawn npc:carpenter
spawn animal:wolf baby
features bounty_board
spawn object:<fixture-id-from-features>
spawn furniture:bookcase
features spell
cast fire_bolt
prayer <id-from-features>
interact <entity-id>
dungeon 2
ragdoll
```

Spells, prayers and interactions retain their normal checks. `setlevel`, `give` and the other existing developer commands can prepare a scenario. Spawning content is explicit developer activity; normal gameplay still uses the shared AI, inventory, law and encounter systems.

## Verification and known baseline failures

Verified: 191 gameplay tests outside the seven reproduced baseline failures, 29 engine Java tests, 121 native self-tests, all twelve Sydney recovery cases, the final OpenGL UI check, and `installDist`.

Automated checks include the real OpenGL cursor/banners/bank UI, dungeon ceilings/scaling, lawful corpse loot and decay persistence, prefab/NPC persistence, contact collision, customer-side social visits, physical snare placement/contact, projectile mesh pivots, crime cooldowns and the twelve native recovery cases. Visual checks capture the level/combat banners, bank before/after expansion, and corpse erosion before/midway/near completion.

Seven existing tests fail on unchanged main **with unchanged main native physics**, independently reproduced from `b63b2fc`:

- `rpg.actor.MovementTest.emptyHandsCanGuardMeleeButCannotParryAMetalWeaponOrStopArrows` — null projectile XP skill.
- `rpg.ai.GraveyardDeliveryTest.aDeliveredBodyIsNotInvestigatedAgain` — delivered-body investigation.
- `rpg.ai.ProfessionTest.allFiveFamiliesHaveFiveValidSubtypesButOnlyFiveResidentsWereAdded` — roster expected 28, actual 29.
- `rpg.ai.ProfessionTest.aCarpenterReservesPlanksForJoineryInsteadOfSellingTheUnfinishedBatch` — carpenter work selection.
- `rpg.ai.SocietyRegressionTest.guardsPhysicallyDeliverAnActualCorpseWithItsOriginalGear` — guard searches instead of collecting.
- `rpg.law.RecoveryRegressionTest.olderProfilesAddAuthoredResidentsWithoutDuplicatingTheirStock` — existing item-ledger discrepancies during load.
- `rpg.monster.MonsterTest.theWildsHaveTheirLairsAndTheGoblinsTurnOnAnIntruder` — wilderness count assertion also counts dungeon residents.

These tests are not disabled in the committed build. A temporary local Gradle test filter is used only to verify the rest of the suite while preserving their visibility in ordinary `test` runs. The foliage traversal probe now checks a complete clear approach so a solid dungeon entrance is not misclassified as a colliding plant. The posse knowledge regression also verifies that camera-facing assembly is deferred.

From the Windows project root (`C:\Users\John\Documents\ProjectRPG`), launch with:

```powershell
.\rpggame\gradlew.bat -p .\rpggame run --no-daemon
```

Gradle rebuilds the modified native core through the existing Windows engine build task.
