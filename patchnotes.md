# Patch Notes Version 0.1.0: Fresh Start

A title screen with music, a character creator worth lingering in, and a world that fights back harder and bleeds properly. Every weapon has been re-forged, prayer and magic work as the old books describe, and your character finally runs like they mean it. Happy exploring!

## New Features
- **Title screen and profiles.** The game opens on a title screen with its own music. You can keep up to five profiles, each with its own save. Continue, start a new one, or delete one you're done with.
- **A new character creator.** Step through it with a progress rail. Pick from presets, get a suggested name, and turn and zoom your character as you go.
- **Developer console.** Press **`** (the key under Esc) to open it. It has the classic commands you'd expect (`god`, `noclip`, `notarget`, `give`, `impulse 101`, `bind`, `exec`, `cvarlist`...) plus ones made for this game, like `give godly_2hsword 1`, `setlevel`, `addxp` and `gearkit`. Tab completes commands and item names.
- **Prayers.** Prayers are now switched on and off. Each one uses a point of Faith every six seconds, and a holy symbol makes that last longer. New prayers:
  - Protect from Melee, Missiles or Magic (blocks that kind of attack completely).
  - Burst of Strength, Piety and Rigour.
- **Four spellbooks.** Standard, Ancients, Lunar and Arcane. You use one at a time and change books at the new **altar**, which also restores your Faith. New spells:
  - Fire Blast (uses the Fire staff's charges).
  - Speak With Animals, Spiritual Step, Gaseous Form, Enhance Ability and Friends.
  - Minor Illusion, Darkness, Disguise Self and Charm Person.
- **New weapons:**
  - Longswords, scimitars, warhammers, spears, claws and halberds.
  - Crossbows with bolts.
  - Throwing darts, knives, tomahawks and javelins.
- **Skillcapes** for every skill. Reach level 100 to wear one.
- **New fishing gear.** Small nets and crab cages join the rod. Each works at its own kind of fishing spot.
- **Blood that stays on you.** Wounds leave blood where you were hit and it runs down. Fighters get splashed too, and it washes off in water.
- Religious wine and restorative potions bring back Faith.

## Changes
- **New material tiers**, from Leather and Bronze up through Steel, Granite, Cobalt, Dragon, Molten and Godly to Malevolent.
- **Every item has been remade** at four times the detail, with metals that actually shine like metal.
- **Fresh characters** start with every skill at 1 except Health, which starts at 10. Your hit points equal your Health level.
- **Combat:**
  - Attacks are quicker, and each kind of weapon has its own rhythm.
  - Attack makes you swing faster, Strength makes you hit harder, and Defense makes your guard and parry better.
  - Armour no longer softens a clean hit. Your guard is what saves you.
- **Movement:**
  - Hold Shift to run. Double-tap and hold Shift to sprint.
  - You turn to face the way you run, and you come to a proper stop instead of gliding.
  - Walking takes real strides, and running is looser and leans in.
- **Gathering:**
  - Mining takes a pickaxe in your hand, and every strike on the rock gives XP.
  - A tree comes down after enough chops anywhere on it, and you get the logs and XP when it falls.
  - Better tools work faster.
  - Woodcutting axes are now one-handed hatchets. The big two-handed axes are for fighting.
- Mana is now your Magic level and comes back one point a minute. Faith is your Faith level and only comes back at an altar or from holy drink.
- What you'll unlock at higher levels stays hidden until you reach them.
- Stolen goods of the same kind now stack together, even when they came from different people. Stolen gold is just gold.

## Technical Changes
- Items are now built on a 64-voxel-per-metre grid (was 32).
- The game creates a fresh engine and world each time you go back to the title screen, all in the same window.
- Saves now remember a dead body's pose, active prayers, your spellbook and the Fire staff's charges.
- Combat formulas now follow the design document exactly (combat level, attack speed, strength scaling, guard and parry).
- The old Debug tab has been replaced by the console.

## Fixed Bugs
- Fixed animals that couldn't lose limbs. A hard enough blow now takes a leg, tail or head, and a lamed animal limps.
- Fixed limbs almost never coming off people, even with a heavy two-handed blow.
- Fixed death cries never playing, for men, women and you.
- Fixed corpses showing a loot crate sitting on top of them.
- Fixed bodies that sometimes got back up or twitched after dying.
- Fixed shields held edge-on in first and third person. They now face forward, and you can see them in first person.
- Fixed tunics and robes looking like stiff boards. Hems now move like cloth around the legs.
- Fixed stiff, tip-toed walking and running.
- Fixed tiny animals like foxes and chickens doing no damage at all.
- Fixed woodcutters who couldn't use the axe they were given.
- Fixed townsfolk who spotted a wounded person's blood trail and ran to the lawman again and again, one drop at a time, instead of reporting what else they'd seen.
- Fixed the lawman wandering away from the chancery when he saw a crime himself. He now just takes note of it where he stands.
- Fixed someone who bled to death after a fight being recorded as having killed themselves. Whoever opened the wound is now held responsible.

## Known Issues
- Some spellbook slots are still empty. More spells will fill them in later updates.
- A few item models (mainly some older armour pieces) still look plainer than the new weapons.

---

# Patch Notes Version 0.0.6: Smooth Operator

We took the whole game apart, stopwatch in hand, and put it back together faster. The same world, the same leaves and smoke and reflections, at nearly twice the frame rate on the same machine, and the little stutters when someone walked into view or a tree came down are gone. Happy exploring!

## New Features
- **Dynamic resolution.** The game watches how hard your graphics card is working and quietly renders a little below your screen's resolution when it needs to, then rebuilds the full-sharpness picture from several frames. In practice you get a high, steady frame rate and a picture that's very hard to tell from full resolution.
- **Fullscreen.** Press **Alt+Enter** to switch between a window and borderless fullscreen at your monitor's full resolution and refresh rate.
- **Smarter vsync.** On monitors and drivers that support it, a frame that's a little late is shown straight away instead of waiting for the next refresh, so a high-refresh monitor doesn't suddenly drop to half speed.

## Changes
- **Much faster overall.** On the test machine (a GTX 1050 Ti):
  - 1080p went from about 73 frames a second to about 155.
  - The monitor's own 1440p now runs at about 140.
- **Fewer stutters:**
  - New people coming into view no longer cause a hitch, and neither do changes to what they're wearing.
  - The minimap no longer stalls the game when a tree is felled or a door opens.
  - Memory clean-up no longer causes hitches.
- Tree leaves, shadows and mist look the same but cost a fraction of what they did.
- Guards who catch a glimpse of you now go and look, then get back to their rounds instead of staring at you for minutes.

## Technical Changes
- Astratto Engine interface 2.5.
- The engine times every rendering pass on the graphics card, without slowing anything down.
- Temporal upscaling, fence-based frame pacing (at most three frames queued), and leaf shapes baked into a small texture at start-up.
- The game's Java runtime now uses a low-pause garbage collector.
- A new built-in benchmark (`./gradlew bench`) measures frame times over a fixed tour of the map.

## Fixed Bugs
- Fixed bodies you hadn't searched yet disappearing after loading a save.
- Fixed guards starting a murder hunt over a bandit's blood, or over their own after a fight.
- Fixed fighters sometimes walking right into their opponent and then never swinging.
- Fixed a disarmed guard fighting bare-handed for the rest of the day. They now pick their weapon back up when they get up.
- Fixed guards endlessly investigating you while you stood in plain sight.

## Known Issues
- In windowed mode, other busy programs (a web browser, for example) can still cause the occasional stutter. Fullscreen (Alt+Enter) avoids most of it.
- Mara sometimes takes a very long time to deliver eggs when she has to walk all the way to the store.

---

# Patch Notes Version 0.0.5: Bodies That Fight Back

Hit someone hard and they'll stumble, windmill their arms, catch themselves with a quick step, or go down in a heap and get back up again, all under their own muscle. The trees have real leaves now, campfires smoke, and the pond reflects the world around it. Mind your footing, and happy exploring!

## New Features
- **Search the fallen.** Bodies can be searched for what the person carried and wore. Their armour and gear show exactly as they're worn, and taking a piece takes it off the body. Looting bandits is fair game, but robbing a dead townsperson is still theft.
- **Bodies react for real.** A solid blow no longer plays a canned stagger. The person hit fights to stay upright:
  - Light hits are absorbed with a sway of the hips and ankles.
  - Harder ones make them stumble back a step or several, placing each foot where it will catch them.
  - Really heavy ones knock them flat. They throw their arms out to break the fall and shield their head.
  - Once down, they roll over, push up onto hands and knees, kneel and stand. Some take a moment to clutch a wound first.
  - Shoved off a ledge, they may grab the edge and hang on for a few seconds before dropping.
  - Strong, agile, tough people recover better. Weaker ones go down sooner and take longer to rise.
- **Knock the weapon out of their hand.** Someone knocked off their feet may lose their grip on their weapon, which lands in the dirt beside them.
- **Thuds.** Bodies hitting the ground make a sound, louder the harder they land. Landing on your head hurts.
- **Leafy trees.** Every tree crown is now full of individual leaves that sway with the branches, flutter in the breeze and glow when the sun shines through them.
- **Smoke and steam.** Campfires and the furnace give off rising, billowing smoke that catches the sunlight and the firelight.
- **Reflections in the water.** Ponds and rivers reflect the trees, buildings and sky around them, with ripples running across the surface.

## Changes
- **A brand-new character creator.** Pick your look across four tabs (Body, Hair, Clothing and Finish), with big cards, colour swatches and named style lists. The camera moves in on the face while you choose hair. Randomise a single tab or everything, and review it all on the Finish page.
- **Proper strides.** Characters walk on softly bent knees and take longer, slower steps instead of tip-toeing along.
- **Your quiver shows.** Equipped arrows now sit in a quiver on your back.
- **Softer shadows.** Shadows are crisp where an object touches the ground and soften with distance, like real sunlight.
- **Materials catch the light properly.** Metal shines, polished stone gleams, and wood and earth stay matte.
- **Motion blur and depth of field** are available as options, and both are off by default.
- **Calves stay at their mother's side** instead of trailing a few steps behind.
- **Guards drop everything for a fight.** A hungry guard no longer finishes his lunch while someone attacks him.

## Technical Changes
- Astratto Engine interface 2.4.
- Sydney 3, a new character physics system:
  - Bodies are built from 13 rigid parts and moved by 48 simulated muscles.
  - A layered balance controller handles standing, stepping, falling, hanging and getting up.
- Physics joints can now be driven with built-in damping, which keeps fast-reacting bodies stable.
- New engine commands to give a reacting body an intention (brace, protect the head, reach, walk, relax) and traits (strength, agility, toughness, endurance).
- New ground-impact events from reacting bodies.
- Leaf clusters, soft shadows, smoke, water reflections and a camera lens pass were added to the renderer.
- New automated checks for Sydney (standing, absorbing and stepping through pushes, getting back up, weak bodies falling sooner), bringing the engine's self-test to 104.

## Fixed Bugs
- Fixed shoes never showing on your character. Trousers were drawn over them. They now fall over low shoes and tuck into tall boots.
- Fixed being unable to sprint in first person, and first-person walking being slower than normal.
- Fixed having to walk right into some people (like shopkeepers behind a counter) before you could talk to them. Aiming at their head or shoulders now works.
- Fixed respawning with your gear still on. Everything you wore now waits in your grave with the rest of your belongings.
- Fixed the "gear kit" debug option crashing the game.
- Fixed fighters getting stuck pressed against their opponent without swinging.
- Fixed items in your pack sometimes disappearing when you died, instead of waiting in your grave.
- Fixed killing a bandit making the guards treat you as a murder suspect.
- Fixed bandits that came back after being killed no longer being hostile (and killing them counting as murder).
- Fixed arrows in mid-air vanishing when the game was saved.
- Fixed stackable items not going into your backpack or pouch when the first stack of that kind was full.
- Fixed an NPC who was moved or unloaded while reeling from a blow leaving a phantom body behind.

## Known Issues
- Bodies can't yet walk steadily under their own muscle power. A few strides in, they lose balance. (Normal walking uses the animation system and is unaffected.)
- Blows to the head or neck knock people down more easily than blows to the body.
- The player doesn't react physically to blows yet. Only NPCs do.

---

# Patch Notes Version 0.0.4: Blood, Music and Moonlight

The guards finally lose track of you when you duck out of sight. They'll grumble, comb the alley and eventually give up, so keep to the shadows. Swords now leave a mark, the farm moos and clucks, there's music to wander by, and your legs have stopped dancing on their own. Wipe your blade, mind the puddles, and happy exploring!

## New Features
- **Music!** Eight tracks now play depending on where you are and what you're doing:
  - Calm pieces for the town, the fields and the wild wood by day, and moodier ones by night.
  - A combat track kicks in as soon as a fight starts.
  - A tense track plays while someone is hunting for you.
  - A short fanfare plays when you complete a quest.
  - Between pieces there are stretches of quiet, so the music feels like an event rather than wallpaper.
- **Every animal now has a voice.** Cows moo, hens cluck, roosters crow at dawn, pigs grunt, horses neigh and snort, sheep bleat, dogs bark, cats meow and hiss, ducks quack, wolves and coyotes howl, and the bear roars.
- **NPCs can lose you.** Break line of sight and they only know where they *last* saw or heard you.
  - They run to that spot, then search the hidden corners nearby: behind walls, bushes and buildings.
  - Guards spread out to search together, and shout when one of them spots you again.
  - After a while with no new clues they give up, but they stay sharp-eyed for a minute or so afterwards.
- **Search areas on the minimap.** A red patch shows where people are looking for you, and a "?" marks its middle. A blue patch means guards are searching for an unknown culprit.
- **A new wanted panel:**
  - Heat pips show how much trouble you're in.
  - One line says what the law is doing right now: *Spotted*, *Searching* or *Hidden*.
  - A draining bar shows how long until the search is given up.
  - Each witness on the way to report you has a progress bar showing how close they are to the lawman.
- **Gore and dismemberment:**
  - Hits land on a real body part: head, torso, arms or legs.
  - Blades and arrows open bleeding wounds; maces bruise.
  - A heavy cut can take off an arm, a leg or (on a killing blow) the head, and the severed part falls away on its own.
  - Someone who loses their sword arm drops their sword. Someone who loses a leg goes down and calls for help.
  - Blood sprays, drips and pools. It stains walls and floors, and wounded people leave a trail behind them.
  - Badly hurt legs make you limp. You keep your limbs unless the blow would have killed you anyway, and your own bleeding is slower and never finishes you off on its own.
- **Blood is evidence:**
  - Townsfolk who find blood or a body cry out and run to tell the law.
  - Guards who find one start searching for whoever did it.
  - Walk past a searching guard with blood on your hands near a fresh kill and you'll be named a suspect. Wash it off in water.
- **Skill unlocks are a surprise now.** The skill guide only shows "???" for levels you haven't reached. Each level-up announces what it unlocked.
- **The character creator turns.** Drag the figure to spin them round and check the back of that braid.

## Changes
- **Pausing really pauses.** The inventory, character sheet, skills, journal, magic book, shops, chests, workstations, dialogues, the game menu and the hold-E menu all freeze the world. World sounds freeze with it, while the music and menu sounds carry on (slightly quieter).
- **A natural day and night:**
  - Dawn and dusk now have warm horizons and golden light, with a blue hour on either side.
  - Nights are deep blue under the moon.
  - The light fades in and out over a proper twilight instead of snapping on and off.
- **Creeping is a stalk, not a squat.** Your character crouches about a third of the way down and takes shorter, quicker steps.
- **Vandalism and killing livestock can now be reported** to the law like any other crime.
- Mara now gathers eggs when the nest boxes fill up, even if the herd is hungry.

## Fixed Bugs
- Fixed characters' legs and arms twitching, especially when standing still. Bodies kept flickering into a mid-air pose for a single frame.
- Fixed being unable to jump at times. Jumps pressed a split second before landing, just after stepping off a ledge, or at the tail end of a swing now go through.
- Fixed clicking a menu button, dialogue choice or interaction swinging your weapon (and sometimes hitting an NPC) as the menu closed.
- Fixed NPCs always knowing where you were while angry at you, even through walls and from across the map.
- Fixed the character creator turning the figure's head away (it could look like the face had vanished), and "drag to turn" not working.
- Fixed bodies stuttering on screens that don't run at exactly 60 frames a second.
- Fixed the moon never showing in the night sky.
- Fixed the fox being the only animal you could hear.
- Fixed a stuck loop where Mara kept refilling the trough and never collected the eggs.
- Fixed NPCs moved by the game (arrests, respawns) briefly turning to face the way they were walking before the move.

## Technical Changes
- Astratto Engine interface 2.3:
  - Streamed sounds, so music plays from a small rolling buffer instead of being loaded whole.
  - A per-channel pause for the audio mixer.
  - A paused frame that runs no simulation.
- Character positions are now blended between physics steps, like loose objects already were.
- Standing on the ground is detected reliably.
- Jumps are buffered for a moment, with a short grace period after leaving an edge.
- Long animal recordings are cut into individual calls automatically when the game loads.
- Blood uses the engine's decal layer and the same particles as water and rain.
- New automated checks cover losing the player, searching, gore, evidence and pausing.

## Known Issues
- Animals bleed and leave blood but can't be dismembered yet.
- Wounds, blood and severed limbs aren't kept in save files yet.
- Blood on your character isn't drawn yet. It is tracked, though, and guards can see it.
- The mountain lion's voice is a big cat's growl pitched down, and the rabbit's squeal is a pitched-up meow, until better recordings turn up.

---

# Patch Notes Version 0.0.3: The Weight of Things 

Everything in the world got a little heavier this update. Doors swing on real hinges, windows really shatter, and your adventurer finally walks like someone with bones in their legs. Now you can creep through the dark, and the woods and fields are alive with animals. Mind the glass, keep to the shadows, and happy exploring!

## New Features
- **Animals!** Fourteen species live in the world:
  - In the wild wood: rabbits, foxes, coyotes, a wolf pack, a mountain lion and her cub, a brown bear with her cub, and wild ducks.
  - On Mara's new farm: chickens, ducks, cows, sheep, pigs, horses, a dog and a cat.
- **Animals live their own lives.** They graze, drink, rest, keep to their herds and flocks, raise their young and run from danger.
  - Foxes stalk rabbits, and a bear with a cub will warn you off, then charge.
  - Young animals grow up, and the animals in the wood come and go, but the wood never fills up out of nowhere.
- **Hunting:** butcher a carcass with a knife for meat, a hide and bones, then cook the meat on a range.
- **Meet Mara, the farmer.** She feeds her animals, gathers the eggs, and sells them at Store A. Killing her livestock is a crime, and she will notice.
- **Make friends:** pet the farm dog, or feed a cat a fish. Trust grows the kinder you are.
- **Stealth:**
  - Hold Left Ctrl to creep. NPCs only notice what their eyes and ears actually pick up, so light, distance, cover and noise all matter.
  - Watch the marker over their heads go from "?" to "!", and keep an eye on the sneak meter.
  - Guards and bandits now go to investigate the sounds you make.
- **Crimes need witnesses who can actually see you.** Steal in the dark while creeping and nobody saw a thing.
- **A whole new interface.** It stays out of your way: health bars, the target panel and the wanted panel appear only when they matter, then fade away.
- **New menus** for Inventory (I), Character (C), Skills (K), Journal (J) and Magic (M).
- **Drag and drop your inventory:**
  - Move items around, press R to turn them, and drop them onto your equipment to wear them.
  - Drag items into chests and shops, or out of the window to drop them.
  - Tabs and a search box help you find things.
- **Action bar:** drop any item, blessing or spell on it and use it with keys 1-0.
- **Know when the law is on to you:** a panel tells you when someone saw your crime, when a witness is on their way to report you, and when guards are coming.
- **Speech bubbles:** NPCs now speak in bubbles over their heads.
- **Every item has its own 3D model,** in your hands, on the ground and in your inventory. A Dragon sword and a Steel sword finally look different.
- **Backpacks and belt pouches** now show on your character.
- **Gold coins** change colour as your fortune grows: yellow K, green M, blue B, and a very rare purple X.
- **Addons:** write your own HUD pieces in Lua. See `addons/README.md`, and try the two examples included.
- **New skill and map icons,** plus menu, quest, coin and fishing sounds.
- Doors are real, physical doors: they swing open on their hinges, and you can push an open door with your body. NPCs open and close them on their way through.
- Glass breaks. Swing a sword, loose an arrow or cast a spell at a window, and it shatters into shards that tumble and clink across the floor.
- Breaking someone's window in town is a crime if anyone sees you.
- You can see your own body and legs in first person.
- Quick turn: tap back twice to spin round (not while aiming).
- Inventory is now a grid: every item takes up its own shape, and you can rotate items to fit. Backpacks and belt pouches add grids of their own.
- You start with a leather belt pouch.
- Footsteps sound different on grass, earth, stone, wood and cloth.
- The devmap now has ambient sound.

## Changes
- People now notice someone standing right beside them, unless that someone is creeping.
- Night is properly dark now, with moonlight, starry skies and pools of lamplight to avoid.
- Carrying more than 30 kg slows you down and tires you out sooner when sprinting.
- Hungry townsfolk now buy bread at the store instead of making do at the well.
- The character creator has a cleaner look.
- **Character creator:**
  - 20 hairstyles each for men and women, and 20 facial-hair styles.
  - 20 tops, 20 legs and 20 shoes, each a distinct garment.
  - 10 natural hair colours and 24 clothing colours.
  - Three body types: Masculine, Heavy and Skinny.
- **Walking and running have weight:** you build up speed, carry it round corners and slow down to stop. Sprinting is a committed run that needs a moment to recover from.
- **Aiming and guarding** slow you to a careful strafe.
- **Attacks:** each blow now winds up over the shoulder, cuts through and follows through.
- **NPCs:** they turn naturally instead of snapping round, look at you as you pass, and only swing at what they're facing.
- **Coins** are now called Gold coins (G).
- **Sounds:** weapons, hits, blocks, doors and coins now use recorded sounds.

## Fixed Bugs
- Fixed the farm gate swinging into whoever opened it.
- Fixed midnight looking like late afternoon.
- Fixed hungry bandits strolling into town at night.
- Fixed bread and other goods disappearing when there was no room for them. They now drop at your feet.
- Fixed townsfolk getting hungry far too fast when time skipped forward, which left the woodcutter and carter eating all day.
- Fixed a guard who could get stuck instead of coming to arrest you.
- Fixed pickpocketing failing when your main bag was full but a pouch had room.
- Fixed shops losing your coins when your purse was full.
- Fixed legs and arms twitching while characters walked or stood.
- Fixed walking strides that were far too long, which made characters lunge.
- Fixed guards attacking you again after you'd been arrested.
- Fixed NPCs stopping mid-route as if stuck.
- Fixed the carter abandoning a shipment to go and eat or chat.

## Technical Changes
- The interface is now built from fragments and scenes, with a Lua addon API.
- Item models are shared between the world and the inventory, and metal parts reflect light like metal.
- New physics engine, Impulsis, for rigid bodies and joints. Doors, glass shards and the bodies you bump into all use it.
- The engine's interface is now version 2.2.
- New animation review tool that captures every third frame of each movement.

## Known Issues
- Animals are mostly silent for now; only the fox has a voice.
- Pickaxe icons lie on their side in the inventory.
- Carried weight only slows you, not NPCs, for now.

---

# Patch Notes Version 0.1.3: A Working Town

Five new residents bring real working trades to the devmap. Crime, custody and recovery now keep closer track of actual belongings, while fire, foliage and injuries share the same physical world.

## New Features
- **Five new townspeople:** Elowen the Lake Net Fisherman, Oren the Ore Miner, Tamsin the Blacksmith, Sir Alden the Escort Knight and Fenna the Predator Hunter.
- **All 25 occupational profiles** across Fisherman, Miner, Craftsman, Knight and Hunter are available to compatible agents. Only five new residents are spawned. Workers need appropriate fixed skills, actual tools, materials and suitable sites; NPC work never grants XP.
- **Deployed fishing nets and snares.** Nets retain their tool and actual catch across saves. Snares catch rabbits that physically cross them, and trappers return to the same captured animal or carcass.
- **Ten kinds of dynamic foliage:** ferns, tall grass, clover, reeds, brambles, wildflowers, ivy, moss, mushrooms and saplings. Soil, shade, walls and nearby water determine where they grow; cutting and burning persist until valid regrowth.
- **Voxel injuries and charring.** Human cuts recess the surface; animal cuts carve private copies of their model parts. Healing restores the original surface. Fire darkens characters, equipment and world props.
- **Physical water feedback.** Moving people, ragdolls and rigid items disturb water with bounded splashes and ripples.
- **Unfilled and Filled cemetery stones.** Burial removes the body and preserves its actual possessions in the named stone. Player recovery protections still apply.

## Changes
- **All 18 skills use the standard XP curve:** level 100 requires 14,391,160 XP. Existing saves retain raw XP; their levels and progress are recalculated. New characters still start at Health 10, now at 1,154 XP, and level 1 in other skills.
- **Jail takes the bounty plus 3% tax**, rounded up, from affordable carried currency. An unpaid shortfall extends the sentence. All remaining clean possessions, including equipment and filled bags, stay in private escrow until release; stolen goods are confiscated. NPC prisoners use the same rules.
- **City deaths go directly to Undertaker custody**, without a death-site gravestone. Wilderness graves remain exposed containers. Undertaker recovery excludes stolen goods and explains that rule before payment.
- **Guards, raiders and escorts coordinate local tactics** with distinct anchor, flanking and support orders, while threatened fighters can seek cover.
- **Enemy searches follow remembered locations and visible evidence**, with bounded patience. Civilians choose hiding places from nearby cover rather than a fixed home destination.
- **Burning NPCs stop, drop and roll through Sydney** after three seconds of sustained fire. Ground contact can smother the flames; a separate nearby fire can still ignite them again.
- **Body flames follow their emitter on the GPU.** Smoke can drift freely. Rain streaks remain upright, clouds advect with accumulated wind, and wet grass stays diffuse instead of reflecting like metal.
- **Chatboxes return to the main conversation choices** after a response; Goodbye exits. Long Worldbox messages wrap and can be scrolled.
- **Roof trespassing starts with a witnessed warning**, then a small, rate-limited bounty if ignored. DevTown replaces JUR-BETA in displayed names and structured legal saves.
- **Pickpocketing opens the actual pocket inventory.** Looking is free; choosing an item starts the theft attempt and its detection risk.
- The Fire staff requires Magic 10. Player Morphbeast lasts less time than the NPC effect. Fresh leather swords have a pre-armor maximum hit of at least 2.
- Every enclosed devmap building, including the barn, now has a physical door.

## Fixed Bugs
- Fixed title-screen Settings ignoring clicks after the opening click, and moved profile deletion off the render thread.
- Fixed Godric holding an empty bow instead of switching to a real melee weapon and answering a new attacker.
- Fixed the first-person camera using the previous frame's world-space head location, including downward sprint clipping and movement stutter.
- Fixed guards selecting other guards as enemies, and searches accumulating yaw until NPCs spun around.
- Fixed blood evidence interrupting an already committed body collection. Undertakers can collect and bury normal city corpses.
- Fixed corpse and burial interactions retaining stale physical bodies without their corresponding inventory.
- Fixed recovered stolen goods being returned to the player, and jail equipment losing its original slots across save/load.
- Fixed healing and respawning restoring stale burning receivers. Respawning also removes the player's detached limbs.
- Fixed necklaces disappearing inside armor, and ensured positive fall and environmental damage use the hurt-voice path.
- Fixed prone-target melee aim using standing chest height, and witnessed beast attacks being mistaken for witnessed human murder.
- Fixed horses' attack poses, rabbits' excessive hopping cadence and dead animals' residual leg motion.
- Fixed arrival state leaving stale movement goals, and gave occupational work resumable, committed tasks.

## Technical Changes
- Windows runtime builds use incremental Ninja when available, with two or four workers. The fallback compiler uses bounded parallel compilation instead of serial NMake. Verification executables stay outside the interactive launch dependency graph.
- The engine supplies shared behavior-tree and hierarchical-task-network control nodes driving existing native agents. Human, Animal and reserved Monster domains are separate; no monster content was added.
- Foliage uses shared model banks and cached chunk meshes, with distance/frustum culling, at most one dirty chunk upload per frame and no tiny foliage shadow passes.
- Attached flame state stays on the GPU, retains full emitter identity and reuses source slots without CPU particle readback.
- Added regression coverage for XP boundaries, fixed NPC progression, property conservation, custody reloads, professions, trap persistence, voxel healing, fire recovery and rigid-body water contact.
- [Visual review and verification details](docs/RELEASE_013_REVIEW.md) include captured animation frames and the hardware-validation limits.

## Known Issues
- The i5-3570 / GTX 660 / 8 GB target at 1080p and 30 FPS still needs measurement on that hardware. Software-rendered cloud screenshots are visual checks, not a hardware performance result.
- River, coastal and offshore professions require corresponding authored water resources and safe travel access. The devmap contains lake fishing; this update does not add an ocean or a ship.

---

# Patch Notes Version 0.1.4: Working Supply Chains

Craftsmen now keep the materials their work needs and use the market for supplies outside their own trade.

## Changes
- Blacksmiths and Armorers can smelt actual ore into bars for their forge work.
- Finished crafted goods can be delivered one at a time, so large equipment does not require an impossible three-item batch.

## Fixed Bugs
- Fixed craftsmen repeatedly choosing gathering work without the necessary tool instead of buying available ingredients.
- Fixed Tailors selling woven cloth before making garments.
- Fixed Carpenters selling planks needed for unfinished, eligible joinery projects.

## Technical Changes
- Removed the unused cross-profession gathering selector.
- Added actual-world checks for ingredient purchasing, ore consumption, garment production and owned furniture construction, while verifying that NPC XP stays fixed.

## Known Issues
- The hardware-validation and authored maritime-content limits described in Version 0.1.3 still apply.

---

# Patch Notes Version 0.1.5: Open Doors

A maintenance pass on moving through town, working routines and the feedback that makes physical interactions readable.

## Changes
- **Foliage bends around nearby people and animals**, keeps its roots planted and returns as they move away. Shared chunk meshes stay on the GPU; brushing a plant does not create another physics body or upload its mesh again.
- **Carried inventories are now four cells across and ten down**, for the player and human NPCs. Equipped bags remain beside the main grid. Existing ten-by-four save layouts are transposed with their item shapes, contents and ownership intact.
- **Conjured allies have their own following duty.** They stay with their caster and defend nearby, without adopting a hunter's meals, sleep, work or corpse-looting routine. They return when an opponent moves outside their protection range.
- Morphbeast forces the player into third person for the spell, then restores the previous camera preference.

## Fixed Bugs
- Fixed building doors being left out of the registry used for NPC opening and hinge updates. Human routes now recognize unlocked doors as passages they can open.
- Fixed door hinges stopping on one side. Unlatched leaves can swing nearly flat in either direction, open away from the person using them and close from their actual current angle.
- Fixed full-cell door blockers and duplicate voxel panels intercepting targeting and attacks through a visibly open doorway. Interaction, melee, arrows and sight now test the physical leaves.
- Fixed turn-in-place foot corrections using an unreachable threshold and being mistaken for unfinished walking steps. Both feet can lift, reposition and settle.
- Fixed wild-wood trees lacking gathering nodes. Their actual trunk height, wood type, yields, stumps and regrowth now use the existing gathering rules.
- Fixed lingering fire, queued elemental effects and old missiles restarting a dead player's combat after respawn. Fire still burns; its old attack attribution ends. This also survives an immediate save and reload.
- Fixed a selected pickpocket attempt waiting forever inside a paused inventory. The pocket view closes while the lift runs and reopens on success, showing the actual item and highlighting the receiving stack.
- Fixed ordinary lamps and fires skipping human body shadows. Local occlusion includes nearby standing, sleeping and reacting humans within the existing bounded shadow budget.
- Fixed thin model shadows being missed by the soft-shadow blocker search. Shadow sampling stays stable when temporal antialiasing is disabled.
- Reduced rapid fire-light variation and replaced arbitrary first-sensor lighting with stable, grouped sources that fade in and out.
- Fixed interrupted occupational plans surviving a change of goal or blocked route. Workers replan, and fatigue recovers only after actually reaching sleep.

## Technical Changes
- Added an engine command to retire attribution from active receivers and queued elemental volumes without extinguishing them.
- Reused bounded actor proxies for foliage contact. Each chunk considers at most eight nearby proxies; chemical lighting retains six selected sources plus two fading slots and refreshes selection at chemistry cadence or after camera travel.
- Removed the redundant voxel door render shape. Door ray tests use the native rigid pose rather than an assumed open angle.
- Added regression coverage for actual NPC doorway travel, bidirectional hinges, doorway focus and arrow hits, wild-tree conservation, conjured following, morph camera recovery, theft, packed-inventory migration and fire attribution across death and reload.
- [Maintenance review and captured evidence](docs/RELEASE_015_REVIEW.md) record the checks and their limits.

## Known Issues
- The i5-3570 / GTX 660 / 8 GB target at 1080p and 30 FPS still requires measurement on that hardware. Cloud software rendering verifies the changed visuals, not that performance target.
- Local body shadows and foliage contact use bounded nearby proxies; distant foliage remains inexpensive scenery.
