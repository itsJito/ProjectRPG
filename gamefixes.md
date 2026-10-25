RPGGAME ultimate doc

**IMPLEMENTATION GUIDE**
When editing ANYTHING, take reference screenshots and make sure everything scores atleast a 10/10 and be extremely critical. Accept nothing that isn't close.
When animating, make sure to take reference screenshots from the start of animation to the on Threes: Frames 1, 4, 7, 10 etc get screenshotted and analyzed for finetuning, redo this until every animation gets a 10/10 or at minimum 8/10.
0.5–1.5 seconds for most gameplay loops (walk/run/idle), with attacks and one-shots usually in the 0.3–1.2s range. Keep them short and responsive so the player feels in control.

**THINGS TO NOTE**
You are allowed to and encouraged to modify and change the game engine for our needs.
Alongside everything listed in this document, make sure to follow the GDD.md for further instructions on what we want/need from this game, and remove/alter anything that doesn't allign with either this document or GDD.md.
Even animals are subjects to the skill system, and their skills also determine their combat level.
Everything should be systemic, dynamic and not scripted.
NPC's can not gain skill levels or combat levels; only the player.

**BUGS TO BE FIXED:**
- Players/characters walk way too slow, and their run animations don't look like running.
- Running animation needs to be polished and different than the walk. Currently looks like punching instead of running.
- Some female death sounds play for the male NPC's.
- Female humans need to look more feminine, disable the muscular type for women.
- Some NPC's still glitch out after death due to Sydney trying to have them regain balance after dying.
-

**THINGS TO BE CHANGED**
- NPC investigation system needs to be more in-depth in terms of choice trees and states. Various NPC types should investigate differently and care differently.
- NPC's should react to sound differently and become more suspicious of sound at night. E.G if the player isnt visible but makes a noise, certain NPC's will investigate.
- SV_Cheats should be removed from the dev console and any source specific commands. If it isn't already, make sure that the dev console is natively running from the engine itself to save on resources.
- Animal AI needs to be polished per species to behave more realistically and visually.
- Need more visual feedback in terms of the human AI and how they feel, know, etc.
- Human AI needs to actually react more dynamically to other AI and the player itself. E.G running away from a bear/lion or running away/hiding from a player on a rampage VS running to get a guard when it's safe
- Human character models should have blinking added to them, and close their eyes when dead.
- Animals need to be rigged so that they ragdoll VIA the Sydney Engine but still use their animations.
- The player should be forced to actually see their player characters death animation for 5 seconds, showcasing the world slowly turn black and white as the camera locks onto the thing that killed the player. The players corpse wont turn into the gravestone UNTIL AFTER the player respawns if they choose to do so, as this screen should prompt a "Continue" or "Exit" option. Make it look fancy and pleasing.
- Blood/Gore should be more visual and more graphic, aswell as making blood more of a physical object in the world that NPC's have a chance of slipping on (slipping actually triggering a Sydney Ragdoll)
- Player gravestones (which only appear after respawning) should appear on the minimap as a marker (make it just a skull) with a timer underneath the minimap. The grave should expire in 10 minutes.

**THINGS TO BE ADDED:**
- Honor and a fence NPC for selling stolen goods.
- Undertakers and the other core GDD systems.
- Ring and neck equipment slots.
- The 64x64 block-texture and material audit for the engine.
- Polish of the systems
- Anything not present from the GDD.md

**THINGS TO BE OVERHAULED:**
- The Walking/Running animations for players. Running should be a bit faster than currently is, walking should be a tad bit faster than currently is. Sneak should be the same speed as current walk. (I noticed it wasn't a "true" sprint, but still its animation is ugly. Make sprint happen with the shift button by default not jogging)
- Human AI Rumor system. NPC's should be aware of other NPC's or the players actions. E.G if the player massacres a bunch of people outside a town and other NPC's have positively ID'd the player, they can warn other NPC's about the player, causing them to have a bad reputation on the player without relying on omnipotent knowledge. The player must find this out naturally and visually. More consequences for players actions. Make it in depth.
- Escape/Pause menu needs to be overhauled to be genuinely impressive and to take up more screen space.

**THINGS TO BE REMOVED:**


RPGGAME ultimate doc — Version 0.1.3 implementation update

**IMPLEMENTATION GUIDE**
- Keep the existing request and update history intact. Visual review uses real engine frames; changed animal and fire motions are sampled on threes. Build, persistence and dead-code checks accompany the review. Subjective quality scores and minimum-hardware FPS are not substitutes for measured evidence.

**THINGS TO NOTE**
- All 25 Fisherman, Miner, Craftsman, Knight and Hunter subtype profiles are implemented, with only five new NPCs spawned: Elowen, Oren, Tamsin, Sir Alden and Fenna.
- Existing saves preserve raw XP. All eighteen skills use the standard cumulative curve, reaching 14,391,160 XP at level 100. Only the player earns XP; humans and animals retain fixed profiles.
- Prisoners who cannot afford the bounty plus 3% tax pay the affordable portion and serve extra time for the shortfall. Player and NPC possessions use the same escrow and confiscation rules.

**BUGS TO BE FIXED:**
- [Fixed] Windows native builds prefer incremental Ninja with bounded parallel compilation; the fallback avoids serial NMake and uncapped compiler processes.
- [Fixed] Settings accepts subsequent clicks; profile deletion runs away from the render thread.
- [Fixed] Empty ranged weapons no longer prevent retaliation. Godric has actual backup equipment and can change targets when attacked.
- [Fixed] First-person head positioning uses current actor motion and collision checks, including downward sprint views.
- [Fixed] City death transfers lawful originals directly to Undertaker custody without creating a wilderness gravestone.
- [Fixed] Positive fall and environmental damage use hurt sounds.
- [Fixed] Searches use remembered locations, bounded patience, visible blood trails and a stable look direction instead of accumulated spinning.
- [Fixed] Necklaces render outside armor; Fire staff requires Magic 10.
- [Fixed] Flames follow posed bodies; healing and respawning clear stale fire. Respawn removes detached player limbs.
- [Fixed] Cloud movement accumulates wind rather than jumping with gust direction. The spellbook altar retains physical collision.
- [Fixed] Arrival clears stale navigation goals; occupational tasks retain their current work target.
- [Fixed] Worldbox text wraps without truncating oversized words and supports scrolling.
- [Fixed] Civilians derive hiding places from local cover and reconsider when found.
- [Fixed] Human and animal surface injuries alter geometry and heal back to the original model.
- [Fixed] Melee aim follows ragdolled opponents' actual center instead of striking above them.
- [Fixed] Guards reject friendly guard targets; vandalism produces role-sensitive reactions.
- [Fixed] Horses rear into attacks; rabbit hopping has a bounded cadence; dead animals settle without animated legs.
- [Fixed] Fresh blood does not cancel committed corpse carrying. Undertakers collect and bury city NPC bodies.
- [Fixed] Cemetery stones accept one body only while Unfilled, then retain the departed's original items as Filled containers.
- [Fixed] A witnessed animal attack is remembered as a beast attack rather than attributed human murder.
- [Fixed] Fresh leather swords have a maximum pre-armor hit of at least 2.

**THINGS TO BE CHANGED**
- [Implemented] Chatbox responses return to conversation options; Goodbye exits.
- [Implemented] Jail confiscates stolen goods, charges bounty plus 3% tax, holds all other belongings and restores them on release, including saved equipment slots and filled bags.
- [Implemented] Human NPCs burning for three seconds collapse into Sydney and physically roll against solid ground to smother the flames.
- [Implemented] Fire chars actor and prop materials. Physics impact aliases use the existing recorded Foley bank.
- [Implemented] Seen roof trespass receives a warning before a small, rate-limited charge. Elevated combat uses the shared targeting and traversal systems.
- [Implemented] Rain streaks remain upright; material-specific wet roughness keeps grass diffuse and preserves metal response.
- [Implemented] Devmap building entrances, including the barn, have physical doors. Structured legal saves migrate JUR-BETA to DevTown.
- [Implemented] Inspecting pockets shows the actual inventory before any theft roll. Detection is rolled only when attempting to lift an item.
- [Implemented] Player Morphbeast duration is shorter than the shared NPC effect.

**THINGS TO BE ADDED:**
- [Implemented] Ten reusable foliage models, deterministic surface-qualified growth, cutting, chemistry destruction, saved depletion and valid regrowth.
- [Implemented] Physical water crossings and wakes from movers, ragdolls and rigid items, with bounded splash/ripple events.
- [Implemented] Separate Human, Animal and reserved Monster agent domains; no new monsters.
- [Implemented] Shared resumable behavior-tree and hierarchical-task-network support used by human work and animal needs.
- [Implemented] Local Watch, raid and escort coordination with distinct anchor, flank and support behavior.
- [Implemented] All five requested occupational families and their 25 concrete subtype profiles, persistent lake nets and snares, real resource/recipe transactions and fixed NPC skills.

**THINGS TO BE OVERHAULED:**
- [Implemented] Recovery and prison custody conserve actual item ownership, quantities and nested contents across save/load.
- [Implemented] Fire attachment remains on the GPU; foliage shares chunk meshes and a bounded upload budget.

**THINGS TO BE REMOVED:**
- [Removed] Serial NMake runtime fallback, accumulated search yaw and stale head-camera coordinates.
- [Removed] Automatic stolen-goods recovery and duplicate corpse representations after burial.
