# Requested gameplay fixes

Based on `main` at `b8f1227`. Scope is the supplied bug/change list, with GDD.md supplying the surrounding rules. Human NPCs and animals keep fixed skill profiles; only the player earns levels.

## Changes

- Open doors respond to Impulsis body contacts and weapon impulses. Removed the proximity-driven opening motors. Explicit Open gives an initial push, then releases the motor; Close returns each leaf from its actual signed angle. Leaf width now agrees across physics, targeting, drawing and restored double doors.
- Each bank account owns one expandable vault: 80 cells initially, then four 40-cell expansions at the existing prices. Opening costs 25 G. Upgrades keep the container identity and item positions. Old multi-vault saves migrate into one grid, preserving quantities, provenance, orientation and layout within each former section.
- Bank dialogue explains the account and prices, displays insufficient-gold refusals, and checks teller availability again when committing. Wren faces the customer side of the counter. The bank counter and cot now register collision cells at creation.
- Container screens show the separate container and carried inventory, with other scene tabs and equipment controls removed. Keyboard scene changes are also blocked until the container closes. The compact inventory frame removes the unused equipment area.
- Employed NPCs expose Free and Working states derived from their actual activity. Trade and banking require working service staff; off-duty conversations retain personal/general topics. Dialogue selections revalidate the speaker's availability and employment state.
- Lawmen use the guard combat, investigation, patrol, search, pursuit and collection policies, instead of fetching another guard.
- Hunter briefings and updates carry a confirmed eyewitness, coordinates, heading and observation time. Sightings transfer through nearby NPC conversations with line of sight. Hunters ask people encountered on the search. Removed kingdom-wide delayed sighting scans, global scent tracking, and ambush placement based on the unseen player's current position or velocity. Sighting provenance survives save/load.
- Arrows and bolts have native Impulsis flight bodies. Gameplay sweeps successive native positions against the actual world and articulated hit volumes, preserving ammunition ownership and preventing tunnelling. Native bullet sweeps now respect `RIGID_NO_WORLD`. Release respects the requested aim. Lodged ammunition follows the struck bone; stale or teleported poses cannot leave a head hitbox behind.
- Burning actors emit a real light at the current posed body, separate from stationary fire-light clustering. The lighting shader excludes a light's source capsule from its own occluders, preventing the bright ground wedge.
- Heal clears chemical state immediately and suppresses stale receiver snapshots briefly. Removing a chemical receiver also removes that receiver's own secondary flame emissions. A fresh external fire can still ignite the healed actor later.
- Water displacement originates at the sampled physical contact, without the old forward offset. Stronger impulses and slower wave damping make the rings more visible.
- Electric charge cancels voluntary actions, starts physical collapse and drives spasms through the same articulated player/NPC body. Animals use their existing articulated knockdown budget. Recovery waits for charge to expire.
- Projectile spells originate at the current posed hand, with a fallback hand position for unseen actors. Bare hands, staffs and wands share the casting gesture and hand particles; utility casts also use hand effects.

## Verification

The regression tests cover paid accounts, in-place growth, insufficient funds, old vault migration, off-duty dialogue, native arrow flight/impact, articulated head attachments, hand casting without a staff, electrical collapse/recovery, healing without reload, local sighting communication/persistence, and the guard investigation policy for lawmen. Existing door-passage, maintenance and elemental integration checks are also exercised.

Final verification: **176 game tests passed**, **29 engine Java tests passed**, **121 native self-tests passed**, and `installDist` succeeded. Java fallbacks were disabled. The four baseline game failures below were excluded only in the final local run.

Four unrelated failures were reproduced against unchanged main gameplay in a separate temporary build:

- `NpcRoutineTest.everyNpcGetsOnWithTheirMorningWithoutStandingBlocked`: Mara's social route remains blocked.
- `ProfessionTest.allFiveFamiliesHaveFiveValidSubtypesButOnlyFiveResidentsWereAdded`: expects 28 residents; main has 29.
- `ProfessionTest.aCarpenterReservesPlanksForJoineryInsteadOfSellingTheUnfinishedBatch`: the carpenter never completes the table.
- `RecoveryRegressionTest.olderProfilesAddAuthoredResidentsWithoutDuplicatingTheirStock`: existing natural-claws/bucket/pot ledger discrepancies.

These are not disabled in the committed build. A temporary local filter isolates them during the final run. The stale `Config.god` test reference was removed so the suite can compile, and the merchant-grievance test now explicitly sets its merchant to Working.

Visual review uses the real renderer under Xvfb/llvmpipe. `RPG_GALLERY_ONLY=gamefixes-bank` captures the counter and separate vault UI; `RPG_GALLERY_ONLY=requested-fixes` captures hand casting, attached fire lighting and physical electrical collapse. Captures are temporary build/review artifacts, not bundled assets.
