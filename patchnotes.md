# Patch Notes Version Alpha 0.2.5: Pane Relief

Townsfolk now have someone to tell when the neighbourhood needs mending.

_Developer: Shadow_

## New Features

### NPC's
- Added witnessed damage reports that townsfolk can now deliver to a Lawman or a suitable builder
- Added repair commissions so Lawmen now pay builders from their own Gold when the work is finished
- Added original names for the ten additional workers and traders, who now retain their existing jobs

## Changes

### NPC's
- Changed builders to now prioritise known repairs over ordinary crafting and unloading
- Changed Lawmen to now seek builders whose workshops are outside their town when reporting damage
- Changed townsfolk to now warn others and move clear of uncontrolled fires, with different responses from Lawmen, Carpenters, and Woodcutters
- Changed animals to now abandon feeding and other routines when nearby uncontrolled fire is visible
- Changed monsters to now avoid visible fire according to their kind, with the undead reacting only at close range
- Changed travel to now consider several nearby doorways and reconsider blocked routes when the world changes

### Blocks
- Changed palace hedges to now retain a continuous clipped shape
- Changed bushes and shrubs to now have folded pointed leaves, woody stems, and quieter leaf colours
- Changed leaf backlighting to now reduce the bright green fringe around sunlit foliage
- Changed the DevCity cell partition to now continue the prison bars, with its outer walls matching the room's plaster

### Items
- Changed dropped weapons and Shields to now tumble and settle under physics
- Removed named weapon variants outside the established material tiers, which now use standard tier equipment

## Technical Changes

### Gameplay
- Changed old saves to now convert retired equipment while preserving ownership and item totals
- Changed saved repairs to now retain who knows about the damage and who commissioned the work
- Changed dropped equipment to now retain its resting orientation and resume unfinished falls after loading
- Changed character appearance updates so wounded fighters now show their latest appearance sooner

## Fixed Bugs

### NPC's
- AE-2067 - Fixed bug where townsfolk described sword swinging as a decent deed
- AE-2068 - Fixed bug where reporting damage rejected builders whose workshops were outside the town
- AE-2069 - Fixed bug where shifting position repeatedly restarted repair work
- AE-2070 - Fixed bug where a missing movement body could leave a living resident absent indefinitely
- AE-2071 - Fixed bug where turning during a fight made combatants lose a fully visible opponent

### Gameplay
- AE-2072 - Fixed bug where a jail door could lock again after the player's sentence ended
- AE-2073 - Fixed bug where release could leave the player trapped in a cell still holding another prisoner
- AE-2074 - Fixed bug where route shortcuts could ignore a locked doorway's access restrictions
- AE-2075 - Fixed bug where repair materials were removed without updating the world's item totals
- AE-2080 - Fixed bug where guards could keep following an unanswered petty-fine request

### Blocks
- AE-2076 - Fixed bug where the DevCity prison slab intersected the back wall
- AE-2077 - Fixed bug where palace hedges appeared as scattered floating leaves

### Items
- AE-2078 - Fixed bug where broad dropped equipment remained standing upright
- AE-2079 - Fixed bug where underlying elbow and knee corners could show through bent armour

---

# Patch Notes Version Alpha 0.2.4: Cell Service

More hands at work, fewer interruptions, and doors on the cells.

_Developer: Shadow_

## New Features

### NPC's
- Added ten residents who now help with construction, woodcutting, mining, and material trading
- Added material traders who now sell construction supplies in the city and palace

### Gameplay
- Added gentle aiming assistance that now follows nearby visible targets while aiming ranged weapons
- Added controls pages where players can now remap gameplay actions, interface shortcuts, mouse buttons, and key combinations
- Added fishing motions so players now see Fishing Rods, Fishing Nets, and Crab Cages cast and retrieved in first and third person

### Blocks
- Added iron cell doors that now swing on hinges in every jail
- Added bushes and increased the height of tall grass across the expanded map

## Changes

### Gameplay
- Changed escaping before a sentence ends to now restore the original bounty and add 75 Gold
- Changed sunlight and skylight so shaded surfaces now receive softer transitions and more even indirect light
- Changed the palace jail so its cell now has complete walls and a roof

### NPC's
- Changed repair workers to now release interrupted jobs and leave unreachable work for another attempt
- Changed social approaches so townsfolk now keep a chosen conversation partner and approach side
- Changed investigations so identifying a nearby resident now resolves ordinary footsteps and glimpses sooner
- Changed animal warnings so bystanders now distinguish a warning at someone else from an active attack
- Changed Giant Spiders to now be smaller

## Technical Changes

### Gameplay
- Changed older saves to now gain the additional residents and material markets without duplicating them on later loads
- Changed material markets to now retain their actual stock through saving and loading

## Fixed Bugs

### Gameplay
- AE-2050 - Fixed bug where damage numbers from distant fights appeared on the player's screen
- AE-2051 - Fixed bug where the player kept limping after their wounds were fully healed
- AE-2052 - Fixed bug where the turn-around key combination could not be remapped
- AE-2053 - Fixed bug where item menus could pass clicks through to the inventory beneath them

### Items
- AE-2054 - Fixed bug where equipping an item from the Quick Wheel removed its assignment
- AE-2055 - Fixed bug where hovering another Quick Wheel slot changed the selected assignment slot
- AE-2056 - Fixed bug where Shields and Bread could not be dropped reliably
- AE-2057 - Fixed bug where Shift-clicking equipment assigned it to the Quick Wheel instead of equipping it

### NPC's
- AE-2058 - Fixed bug where monsters remained still when attempting attacks with sheathed weapons
- AE-2059 - Fixed bug where witnesses named nobody as a murderer instead of reacting to an unidentified body
- AE-2060 - Fixed bug where interrupted repair claims and changing social approaches caused repeated backtracking
- AE-2061 - Fixed bug where dismissed charges could cause guards to reopen the same suspicion
- AE-2062 - Fixed bug where a search for an unidentified culprit remained active after the body was buried
- AE-2063 - Fixed bug where replacing a monster's carried weapon discarded the previous weapon

### Blocks
- AE-2064 - Fixed bug where some leaves, berry bushes, and hedges blocked movement
- AE-2065 - Fixed bug where regrowing foliage could replace tree trunks
- AE-2066 - Fixed bug where hedges lost their leaf rendering and wind movement

---

# Patch Notes Version Alpha 0.2.3: Fog of Wardrobe

A smoother-running build, a moodier title screen and livelier legs.

## Changes

### Gameplay
- Changed the title screen camera to sit closer to your character, with a slight handheld sway
- Changed the title screen floor to have a low fog rolling around your character's feet

### NPC's
- Changed the jog to be lighter and more upright, with quicker, shorter steps instead of long crouched lunges
- Changed running arms to swing at the sides rather than being held out in front of the chest
- Changed the sprint to lean into the run a little more, with a stronger arm pump
- Changed fighters who are badly outmatched and alone to run for help instead of fighting to the death. Guards still hold their ground
- Changed wounded monsters to fall back to their den and make a stand there, rather than scattering into the open
- Changed drawing and sheathing to turn the shoulders into the reach, grip the weapon at its sheath, and bring the blade out in an arc

## Technical Changes

### Gameplay
- Changed how townsfolk and monsters pace their looking and thinking, so a crowd no longer does all its work on the same frame
- Changed doors so opening and closing them no longer recalculates the lighting around them
- Removed a batch of unused leftover code

## Fixed Bugs

### Gameplay
- AE-2047 - Fixed bug where the game stuttered every few seconds in town whenever a door opened or closed
- AE-2048 - Fixed bug where the game hitched when all the townsfolk noticed things or made up their minds on the same frame

### NPC's
- AE-2049 - Fixed bug where doors were held open forever by anyone standing near them, so locked doors were never locked again

# Patch Notes Version Alpha 0.2.2: Sheathe Happens

Weapons can now be put away, the farm has a proper gate and the undertaker has handed his duties to a box.

## New Features

### Blocks
- Added hedges, a dense clipped leaf block. DevPalace's greenery is now hedge
- Added the fence-gate, a one-block-high gate that matches the fences around it. Mara's paddock now has one

### Items
- Added the sheathe system. Hold F to put your weapon and shield away on your body, and hold it again to draw them
- Added sheathed spots for every kind of weapon. One-handed weapons now sit at the hip, while bows, crossbows, staves, polearms, great weapons and shields go on the back
- Added drawing on attack. Pressing attack with your weapon sheathed now draws it first

### NPC's
- Added sheathe manners. Townsfolk now keep their weapons sheathed until they need them, and guards now tell you to put yours away in town
- Added guilt. NPCs who come across someone they killed now either stand by their reasons or are shaken by it, instead of crying murder
- Added full-pack errands. Workers now sell what they have gathered when their pack is full, and bank it if nobody will buy

### Gameplay
- Added the Unfinished Casket in the graveyard. You now buy back your belongings there instead of from the Undertaker
- Added new music, including a new title theme, new fight music, wilderness and town pieces, and a hymn that plays near the altars
- Added crash reports. If something goes wrong, the game now writes a report to the saves folder and carries on where it can

## Changes

### Blocks
- Changed leaves to show bigger, clearer individual leaves

### Items
- Changed crossbows to be held in both hands in third person, with a kick when fired and a two-handed reload
- Changed Chests to be solid, so you can no longer walk through them

### NPC's
- Changed NPCs to forget their quarrels with you once you are released from jail
- Changed NPCs to react to wounds and to the gear on your back when you talk to them
- Changed goblin camps so one goblin now always stays behind to keep watch
- Changed monsters to fight hardest for the heart of their camp, and to take on whoever stands in it first
- Changed goblins, imps and other monsters with hands so they can now open doors
- Changed monsters to see a sheathed weapon as less of a threat
- Changed hurt NPCs to slow down as well as limp

### Gameplay
- Changed the minimap to be more zoomed in
- Changed walking and running so the arms now swing properly
- Changed walking and running to show your injuries. A hurt leg now limps, a hurt arm hangs and a body wound hunches you over
- Changed injuries so they no longer slow the player down, except for a moment while a blow lands
- Changed sprinting to only use stamina while you are actually covering ground
- Changed arrests so that a bounty worth more than everything you own now takes all of it for good
- Changed dragon bodies to fall with physics and to vanish within ten seconds
- Changed caravan cargo crates so you can now put items in them

## Technical Changes

### Gameplay
- Changed the Undertaker's chest payments so his fee now goes to him, or to his chest if he is away
- Changed several automated checks so they test only what they mean to

## Fixed Bugs

### Blocks
- AE-2030 - Fixed bug where chests, including the Undertaker's chest, had no collision
- AE-2031 - Fixed bug where you could not put items into cargo containers

### Items
- AE-2032 - Fixed bug where long hair was attached to the back and did not move with the head

### NPC's
- AE-2033 - Fixed bug where goblins and other monsters did not show on the minimap
- AE-2034 - Fixed bug where NPCs who watched a monster attack you then questioned you about the blood
- AE-2035 - Fixed bug where searches you had nothing to do with showed on the minimap
- AE-2036 - Fixed bug where an NPC who killed someone then reported finding the body
- AE-2037 - Fixed bug where NPCs could vanish or jump to another spot during a fight
- AE-2038 - Fixed bug where the fisherman and other workers stopped working when their pack was full
- AE-2039 - Fixed bug where dragons did not ragdoll when killed
- AE-2040 - Fixed bug where goblins got stuck pushing at a shut door
- AE-2041 - Fixed bug where a lawman could set off his own posse's snare

### Gameplay
- AE-2042 - Fixed bug where leaving a dungeon or the Spider Hollow left you stuck inside the entrance
- AE-2043 - Fixed bug where fire did not char burning people or dead bodies
- AE-2044 - Fixed bug where holding sprint against a wall still drained stamina
- AE-2045 - Fixed bug where a yellow marker still hovered over plants
- AE-2046 - Fixed bug where NPCs ignored suspicious sounds for the first few minutes of a game

# Patch Notes Version Alpha 0.2.1: Walls Have Two Faces

Houses are now finished inside, the title screen has moved underground and the light has calmed down.

## New Features

### Blocks
- Added two-faced wall blocks. A wall can now be brick or stone outside and plaster or planks inside, all in one block
- Added an iron-clad wall with a wooden lining. Fire burns the wood away and leaves the iron, which glows red with the heat

### NPC's
- Added sheltering. Townsfolk caught outside in heavy rain now head home until it passes
- Added morning prayers. Devout townsfolk now visit their town's altar once a day
- Added flanking. A monster that arrives while another is already fighting you now goes round to your other side
- Added new tactics for monsters. They now weave in when you draw a bow on them and work round behind you when you block

### Gameplay
- Added vaulting. From Agility level 2 you can vault fences and low walls by sprinting or jumping at them, and NPCs vault them too
- Added the Updates button to the title screen, where you can read every patch note

## Changes

### Blocks
- Changed every house to the new building standard. Walls now stand at least three blocks high and every door opens onto clear ground
- Changed fire to spread far more slowly. A wooden wall now takes most of a minute to burn along instead of a few seconds
- Changed DevPalace's layout. An alley now runs either side of the imperial palace, and columns no longer stand in doorways

### NPC's
- Changed NPC dialogue to depend on what they are doing, and DevPalace's citizens now talk about their own city
- Changed NPCs so they now notice when you talk to them twice in one day
- Changed NPC routes. NPCs now use the gates and gaps in town walls instead of walking the long way round

### Gameplay
- Changed the title screen to a torch-lit dungeon chamber with your last character standing before a great doorway
- Changed the title menu to the right side of the screen, and moved Settings into it
- Changed the character creator so your character only turns when you turn them
- Changed the minimap to zoom in when you are in a town, city or kingdom and out when you are in the wilds
- Changed the lighting to be softer and warmer, with gentler shadows, calmer greens and a bluer haze
- Changed the fog to begin closer, so the world now runs more smoothly
- Changed the running animation. Strides are now shorter and quicker and the arms pump
- Changed the bow draw seen from behind. The bow now comes up into the shot and is held at a slight cant
- Changed knockdowns. You and NPCs now get back up noticeably faster
- Removed the "?" controls button from the inventory, character, skills, journal, magic, faith and map pages
- Removed Examine from everything in the world except items

## Technical Changes
- Changed distant NPCs to think less often while they go about their day, to reduce stutter on the larger map
- Changed the world to rebuild only a few pieces of terrain a frame and to stop drawing shadows past the fog

## Fixed Bugs

### Blocks
- AE-2019 - Fixed bug where soot stayed floating in the air after the block it was on broke or burnt away
- AE-2020 - Fixed bug where some beds in DevPalace stood the wrong way round in the middle of the room

### Items
- AE-2021 - Fixed bug where the sleeves of your shirt showed through body armour between the elbow and the glove

### NPC's
- AE-2022 - Fixed bug where monsters could not lose limbs, wings, tails or heads
- AE-2023 - Fixed bug where DevPalace's citizens got stuck on beds standing in the way
- AE-2024 - Fixed bug where some DevPalace houses opened straight onto the palace wall
- AE-2025 - Fixed bug where a tailor shelved the cloth they needed and a carpenter stopped building furniture
- AE-2026 - Fixed bug where a knee could swing out sideways when stopping from a sprint

### Gameplay
- AE-2027 - Fixed bug where the game stuttered heavily on the larger map
- AE-2028 - Fixed bug where getting back up after being knocked down took far too long
- AE-2029 - Fixed bug where running looked like a series of lunges

# Patch Notes Version Alpha 0.2.0: All Roads Lead to DevPalace

The map has grown to its full size, an empire has moved in next door and the world has been repainted.

## New Features

### Blocks
- Added a hand-painted look to every block of the world. Grass, stone, brick, wood, bark, leaves, water, roofs and every other surface have been redrawn from scratch
- Added DevPalace, a walled Roman city east of DevCity with a marble forum and fountain, a temple, the emperor's palace, a basilica, a market hall, baths and houses round their atria
- Added the southern wood, a great forest south of the old map with a brook running through it
- Added the lake country south of DevPalace, with a lake and its beaches, mining hills, a marble quarry and fields of herbs, flax and berries
- Added a meadow north of DevPalace and a forester's lodge at the edge of the southern wood
- Added soot. Fire now blackens the walls it burns long before they burn through

### Items
- Added the Marble block, cut from the quarry south of DevPalace

### NPC's
- Added twenty residents of DevPalace, among them Emperor Severinus, Praetor Cassia, four legionaries, Senator Lucilla and Flavia the trader
- Added Arthur, Sir Lancelot, Sir Gawain, Tristan, Percival and Merlin at the forester's lodge: a carpenter, a knight errant, a monster hunter, a trapper, a coal miner and a gem prospector
- Added tip-offs. Townsfolk who know you are wanted now tell the nearest guard where they saw you
- Added repairs. Smashed windows and burnt fences, walls and roofs in the kingdoms are now mended by the carpenter, and timber by woodcutters
- Added the blacksmith's rack. Tamsin now sells what Store A will not take from her own rack at the forge

### Gameplay
- Added the Faith tab, a page of its own for your prayers, opened with P
- Added DevPalace's own law, cell and market
- Added new music for the dungeons, a theme for DevPalace and peaceful pieces for town and field, and a fanfare when your combat level rises

## Changes

### Blocks
- Changed the devmap to the full size of the game map: 192 by 192 blocks and 100 blocks high
- Changed doors so they now open away from whoever opens them and stay loose on their hinges once open, to be pushed either way. Pushed shut, a door latches
- Changed electricity on water so arcs of lightning now crawl across the surface, and a bolt that strikes someone standing in water now runs into it

### NPC's
- Changed guards and lawmen so they no longer attack over a small bounty. They now ask you to pay the fine, and leave it on the books if you put it off
- Changed the blacksmith so she now buys ore from the miner when the store has none and keeps working through the day
- Changed shops so they now stop buying goods they already have plenty of
- Changed townsfolk so they now sell what they gather at their own town's market
- Changed ducks so they now swim on their pond, and cows, horses, pigs and others now wade into the shallows from time to time

### Items
- Changed spells so they now wound the body by their element. Fire and lightning scorch, ice and wind cut, earth and water batter
- Changed deadly spells so they now take off limbs and heads
- Changed Earth Blast, Earth Wave and Earth Surge so a strong hit now knocks people off their feet
- Changed arrows and thrown blades so they now fall out of a body after a minute

### Gameplay
- Changed dying with a bounty over 25 gold so the law now takes a share of what you carried, more the higher the bounty, up to everything. Dying without a bounty is unchanged
- Changed the Magic tab to a cleaner spellbook page, with the chosen spell written out beside it
- Changed the bank so the vault now takes the middle of the screen, with buttons to deposit everything, deposit your gold or take everything out
- Changed the character tab so it now shows your standing with each kingdom's law instead of the controls
- Changed the wanted panel so bandits and others hunting you are now shown apart from the law
- Changed sprinting so it is now quick, driving strides instead of long bounds
- Changed sneaking so you now crouch low with your head up instead of folding over
- Changed the bow in first person so it now pushes out as you draw, trembles when held and kicks when you loose

## Technical Changes
- Removed the painterly filter from 0.1.9
- Changed campfire smoke so it is now drawn only as a lit cloud
- Changed the walls' textures so they now shimmer much less at a distance
- Added the version number Alpha 0.2.0

## Fixed Bugs

### NPC's
- AE-2001 - Fixed bug where paying your bounty to a guard who had stopped you did nothing and the watch kept chasing you
- AE-2002 - Fixed bug where bandits searching for you showed as the law
- AE-2003 - Fixed bug where the blacksmith waited for supplies most of the day
- AE-2004 - Fixed bug where cows and other animals could not go into water

### Items
- AE-2005 - Fixed bug where the arc shown for arrows and throws was not where they landed
- AE-2006 - Fixed bug where the bow's draw looked wrong and the bow did not move in first person
- AE-2007 - Fixed bug where Earth Wave did not knock anyone down
- AE-2008 - Fixed bug where magic and ranged attacks left no wounds
- AE-2009 - Fixed bug where magic could not take off a head
- AE-2010 - Fixed bug where arrows stuck in the edge of a body twitched

### Blocks
- AE-2011 - Fixed bug where open doors could not be pushed and swung flat to either side
- AE-2012 - Fixed bug where electricity on water barely showed
- AE-2013 - Fixed bug where fire did not burn the surfaces it touched or keep charring bodies
- AE-2014 - Fixed bug where some walls in DevCity shimmered
- AE-2015 - Fixed bug where soft grey discs floated near campfires at night

### Gameplay
- AE-2016 - Fixed bug where the slay command did not work on monsters
- AE-2017 - Fixed bug where sprinting took long bounding strides
- AE-2018 - Fixed bug where sneaking looked wrong

# Patch Notes Version 0.1.9: Here Be Dragons, There Be Fog

The town now knows what to do when something big comes calling, and the world got a fresh coat of paint.

## New Features

### Blocks
- Added a hand-painted look to the world's blocks. Stone, brick, wood, earth, ore and grass now read as brushwork with clear outlines
- Added a morning mist that gathers in the low ground and drifts in banks. It is thickest at dawn, light by noon and returns at dusk
- Added two more crab cage spots in the pond's shallows and a second face at the quarry

### NPC's
- Added the town alarm. When a dragon or another fearsome beast comes into a settlement, whoever sees it raises the cry and it spreads from person to person
- Added sheltering. Townsfolk now run home and stay inside while the alarm is up
- Added defenders. Guards, the lawmen, knights and hunters now turn out against a beast in town, shoot from range, work round its flanks and fall back when hurt

### Gameplay
- Added eye colour to the character creator, with eight colours to choose from
- Added a note at the top of the character creator explaining that you need to set your appearance before you begin
- Added scorch marks. Fire now chars bodies patch by patch where it burns them, starting at the legs, and burning bodies shed ash
- Added a reaction to lightning on water. The surface now flickers with cold light, throws sparks and shivers while the charge runs through it

## Changes

### Blocks
- Changed dungeons so their halls and passages are now much wider and taller, with cut corners, alcoves and small side chambers
- Changed lamps so they now give a warm, low light that pools around them, leaving the corners of a room in shadow

### NPC's
- Changed bats so they now circle out of reach, dive in to bite and climb away again, one at a time
- Changed goblins so they now spread out around you, dart in to strike from different sides and fall back again
- Changed people fighting a bat or another flyer high above them so they now wait for it to dive instead of swinging at the air
- Changed archers so they now step aside for a clear shot instead of firing through someone in the way
- Changed the dragon's animations. It now stalks low on bent legs with its head held out, and beats its wings hard in flight with its legs tucked up
- Changed the Border Knight so he now patrols along the border at his post instead of wandering around the houses
- Changed monsters so they no longer cry out with a human voice when they lose a limb

### Gameplay
- Changed the dead so they now drop the weapon and shield they were holding, which fall to the ground beside them
- Changed first-person movement so it now starts and stops much more quickly. Third person is unchanged
- Changed jogging so it is now upright with quicker, shorter steps instead of long lunges
- Changed female characters so they now have a more feminine figure in every build
- Changed skirts, robes and long tunics so they now hang closer and no longer split into strips as they swing
- Changed the way your eyes adjust to the dark, so night stays dark and lamplit rooms now look like evening

## Technical Changes
- Changed the far draw distance so it now ends just past the fog, which saves work on distant scenery
- Changed sound loading so it now happens in the background while the game starts
- Added the version number 0.1.9

## Fixed Bugs

### NPC's
- AE-1901 - Fixed bug where bats had poor combat behaviour
- AE-1902 - Fixed bug where goblins had poor combat behaviour
- AE-1903 - Fixed bug where people kept swinging at bats flying above them
- AE-1904 - Fixed bug where arrows and spells aimed at a flying creature went over its head
- AE-1905 - Fixed bug where an archer's arrow passed through other people to hit you

### Items
- AE-1906 - Fixed bug where Earth Wave, Water Wave, Fire Wave and the other Wave spells did not hit anything

### Gameplay
- AE-1907 - Fixed bug where the game nearly froze when deleting a profile on the title screen
- AE-1908 - Fixed bug where the character creator would not change your outfit, legs or their colours
- AE-1909 - Fixed bug where the character creator showed the last character you played instead of the one being made
- AE-1910 - Fixed bug where target practice dummies had no collision
- AE-1911 - Fixed bug where a lamplit room at night looked as bright as day
- AE-1912 - Fixed bug where fire only darkened a character evenly instead of burning them where the flames were

# Patch Notes Version 0.1.8: Wheel of Fortune, Pot of Stew

Five new neighbours, a quick wheel, a proper cooking fire and a town that notices when you wave a sword about.

## New Features

### Blocks
- Added a brook running south out of the pond in the western wood, with two fishing spots on it

### Items
- Added charred versions of planks, logs, beams, roofs and stumps. Burnt wood now keeps its own grain under the soot instead of turning into the same black block

### NPC's
- Added Hilda the Cook, who cooks her raw stock at the workshop range and sells what she makes
- Added Bertram the Quarry Worker, who cuts building stone at the quarry face
- Added Sir Roderic the Border Knight, who keeps the road where DevCity's ground ends and calls the watch when something comes over it
- Added Wynn the Shellfish Gatherer, who sets crab cages in the pond shallows when the water is low
- Added Corwin the River Fisherman, who fishes the new brook and moves on when a spot goes quiet
- Added reactions to arms shown in the open. Swinging a weapon, loosing arrows or casting near people now gets a reaction that depends on who they are
- Added guards that warn you, keep an eye on you and give you a last warning if you keep swinging a blade in town
- Added townsfolk that flinch away from a blade swung close to them, stare from further off and shout for the guard if it keeps happening
- Added scholars that come over to watch when you cast a spell nearby
- Added brutes and bandits that take a blade swung within reach as a challenge
- Added conversation topics for the five new residents, each with advice about their own work

### Gameplay
- Added the Quick Wheel. Hold Tab to open eight quick slots around the middle of the screen, point at one and let go of Tab to use it
- Added a Quick Wheel button to the inventory. Click an item to put it in the highlighted slot, or drag it onto a slot. Right-click a slot to clear it
- Added a cooking window. Using a range or a fire now lists the raw food you carry, what it becomes, how likely it is to burn and how many to cook
- Added a cooking animation. The food is held out over the fire, turned halfway through and finished off
- Added an Auto-Sort button to the inventory that packs everything neatly with each kind of item together
- Added spell animations for every spell. Strikes, Bolts, Blasts, Waves and Surges share one shape per tier, and each element has its own look
- Added casting effects for spells that don't fly, such as Forcewave, Necrocall, Vitalmend and Chronoslow

## Changes

### Blocks
- Changed burning fences so they now blacken where the flames are and then burn away, instead of becoming a block
- Changed the caravan road so it now goes around the houses instead of running through the first one
- Changed the path from the debug square so it now enters the city through the west gate
- Changed the beds in both houses so their heads now rest against the back wall
- Changed the lawman's cot and the undertaker's bed so they now face the right way

### NPC's
- Changed people so they now sprint when something is urgent, such as fleeing danger, chasing someone running away or reporting a killing, and only while they have the breath for it
- Changed people so they now ease into a walk for the last few steps of a trip
- Changed fighters so they now jog or sprint to close on a foe instead of shuffling the whole way
- Changed people so they now recognise anyone in plain view within ten blocks
- Changed people so they are now less certain of figures further than ten blocks away
- Changed people so they can now see through glass, but can only make anyone out up to five blocks away through it
- Changed escort knights so they now stay with the person they are guarding instead of going to look at every noise
- Changed people so they no longer keep going back to look at you when you are standing in the open with nothing known against you
- Changed bandits so they now walk home after giving up a chase instead of turning back and forth
- Changed some explanations, such as the herbalist's and the undertaker's, so they now sound like people talking

### Gameplay
- Changed the quick turn so it is now Ctrl+S instead of tapping back twice
- Changed Tab so a tap still frees the mouse and holding it now opens the Quick Wheel
- Changed new characters so they now start with 25 gold and a neatly packed inventory
- Changed water so it now ripples in fine rings around anyone wading, standing or falling into it, and around anything thrown in
- Changed water splashes so the drops now look like water instead of sparks
- Changed fire spells so they now look like balls of flame that leave embers and smoke
- Changed electric spells so they now crackle with forked lightning
- Changed water spells so they now splash back off whatever they hit
- Changed elite dungeons so their strongest residents are now capped at your combat level, like every other dungeon except boss dungeons. Most of their residents are close to that cap
- Changed level-up banners, quest notices, Honor changes and wanted reminders so they now stack at the top of the screen instead of covering each other
- Changed the walk so it is now more upright, with softly bent knees instead of a crouch

## Technical Changes
- Changed water ripples to a finer simulation that follows the camera
- Changed the anti-aliasing so still scenes no longer shimmer
- Changed ambient occlusion so it no longer leaves a moving pattern on walls
- Changed the sunlight shadows so surfaces past the edge of a shadow map now use the baked sky light instead of full sun
- Added console commands: weather, spells, book, cast, ignite and wheel
- Added the version number 0.1.8

## Fixed Bugs

### Blocks
- AE-1801 - Fixed bug where wooden blocks that are not cubes, such as fences, turned into a full block when they burned
- AE-1802 - Fixed bug where the bounty board's collision took up far more room than the board
- AE-1803 - Fixed bug where a road led straight into the wall of the first house

### NPC's
- AE-1804 - Fixed bug where every guard still followed you around after you successfully lied to one
- AE-1805 - Fixed bug where people sometimes could not see you standing right in front of them
- AE-1806 - Fixed bug where people standing beside you did not notice you if they were facing away
- AE-1807 - Fixed bug where people never sprinted
- AE-1808 - Fixed bug where the quarry worker cut clay from the bank beside the quarry face
- AE-1809 - Fixed bug where some beds faced backwards

### Gameplay
- AE-1810 - Fixed bug where the level-up banner and the wanted notice covered each other
- AE-1811 - Fixed bug where interior walls flickered and shimmered, most of all by lamplight
- AE-1812 - Fixed bug where water barely rippled for anything moving in it

# Patch Notes Version 0.1.7: Deep Trouble

The monsters can finally be seen, they have somewhere to live, and magic has been rewritten from the ground up.

## New Features

### Items
- Added the Air Staff, Water Staff, Electric Staff and Earth Staff. Each makes its own element's spells very cheap to cast

### NPC's
- Added a giant spider's web in the western wood. Its spider only comes out at night
- Added questioning. The watch now asks people nearby whether they have seen a wanted suspect, and searches wherever that person last saw them
- Added snares. Bounty hunters now lay snares where their quarry was last seen, and come running when one is sprung
- Added an option at the Undertaker to buy back the items in a gravestone that is still standing
- Added a voice of its own to every monster, from a goblin's grunts to a ghost's moans

### Gameplay
- Added dungeons. Each one is its own sealed map, entered through a cave mouth in the wilds and left by its own way out
- Added four dungeons: the Goblin Warren, the Spider Hollow, the Old Catacombs and the Troll's Den
- Added micro-dungeons, dungeons, elite dungeons and boss dungeons. Elite dungeons are far more dangerous than their size suggests, and boss dungeons are built around a single fight
- Added dungeon traps. Spike plates and loose stones can be spotted with a sharp eye and disarmed with Thieving, and anything that walks on one sets it off
- Added dungeon hazards, such as fire pits and sunken pools
- Added dungeon chests. What is taken from them stays taken
- Added a dungeon's own map to the minimap and world map while you are inside it
- Added a Holy Light when you level up. A beam of gold light erupts from the ground and bursts, sparkles fall around you, and you glow gold for a moment
- Added a cheer when you level up outside of a fight
- Added a LEVEL UP! banner, and a separate banner showing your new Combat Level when it rises
- Added a Move All button to the bank vault
- Added the Sun spellbook's 25 elemental spells, from Wind Strike to Fire Surge, in five tiers: Strike, Bolt, Blast, Wave and Surge
- Added Fogish's ice spells: Ice Rush, Ice Burst, Ice Blitz and Ice Barrage. Each freezes its target in place, from 5 to 20 seconds
- Added a chance for Wind Blast, Wind Wave and Wind Surge to knock a target off their feet. Smaller and less skilled targets go down more easily
- Added remains that dissolve. A monster's body now crumbles to ash a minute after it dies, leaving whatever it carried on the ground
- Added pillared halls to the larger dungeon rooms

## Changes

### Blocks
- Changed fences from blocks to proper models, with posts and rails that join to the fences and walls beside them
- Changed jail fronts to iron bars
- Changed Redberry bushes so you can now walk through them, like any other plant

### Items
- Changed the Fire Staff so it no longer needs charges. It now makes fire spells cheap to cast instead

### NPC's
- Changed guards so they now hunt for a killer and look into crimes before fetching bodies to the undertaker
- Changed guards and the undertaker so they no longer carry dead monsters to the graveyard
- Changed townsfolk so they now react to any monster nearby, with fighters going after it and everyone else keeping their distance
- Changed NPC dialogue to have far more variety, with remarks on your wounds, blood, a body on your shoulder, a drawn blade, the weather, the time of day, monsters nearby and your reputation
- Changed NPC small talk so they now talk about the weather, their work, monsters and news instead of repeating greetings
- Changed monsters so they are now always active underground
- Changed bounty hunters so they no longer appear in front of you or within sight
- Changed townsfolk who know of your crimes so they now say so in plain words
- Changed the lawman's crime reports to read as he would tell them

### Gameplay
- Changed leveling to be faster. Agility, Thieving and Faith now level a little faster than the rest, and combat skills a little slower
- Changed sword, dagger, axe and spear swings to recorded sounds for each kind of weapon
- Changed hits to recorded sounds for cuts, stabs and blunt blows
- Changed blocks to sound like wood or metal depending on the shield, or steel on steel with a blade
- Changed bows to new recorded releases and draws for longbows and recurves
- Changed crossbows to their own recorded shot, cocking and loading sounds
- Changed arrows to make a different sound when they strike wood, stone, earth, armour or flesh
- Changed glass to recorded breaking and tapping sounds
- Changed drawing and putting away a blade so it is now heard
- Changed a combat level up so it now heals you fully. A skill level up does not
- Changed splashes so jumping or falling into water now throws up a much bigger splash, with foam and waves
- Changed monsters so big ones now swing and reach as far as their size suggests
- Changed the spellbooks. Standard is now Sun, Ancients is now Fogish, and Lunar and Arcane are now one book, Moon
- Changed the spells of a tier so the lower ones now hit as hard as the strongest one of that tier you have unlocked
- Changed dungeons so their monsters now match you as you enter. A regular dungeon's strongest monster is never above your combat level, with weaker ones filling in, and elite dungeons go higher. Boss dungeons are unchanged
- Changed the weakest dungeon monsters so they now grow stronger with you from combat level 20
- Changed dungeons to be wider, with broader passages, larger rooms and higher ceilings
- Changed dungeon traps so they can now be spotted by a careful eye, and are marked once found
- Changed crime messages so the same kind of crime is now announced at most once every 30 seconds
- Changed the fonts. Friz Quadrata is now used for most of the interface, banners and names, Morpheus for the quest log and spellbooks, Arial Narrow for chat, tooltips and descriptions, and Skurri for the damage numbers over a body
- Changed knocked-down bodies so they now get back up sooner and stay on their feet once up
- Changed falling bodies to recorded body-fall sounds
- Changed the default performance budget to 60 FPS. The picture now lowers its resolution slightly in heavy scenes rather than dropping frames

## Technical Changes
- Changed the world to be wider, to make room for the dungeons beyond the cliffs at its edge
- Removed unused sound files and stale sound banks
- Changed sight checks, the minimap and the repainting of wounded characters to cost far less, which reduces lag in fights
- Changed fog and smoke to be cheaper to draw
- Added developer console commands to spawn any animal, monster or kind of person, and to list them all
- Removed an unused font and the old spells replaced by the new spellbooks

## Fixed Bugs

### NPC's
- AE-1701 - Fixed bug where imps were invisible but could still attack and kill you
- AE-1702 - Fixed bug where goblins were invisible but could still attack and kill you
- AE-1703 - Fixed bug where every monster was invisible
- AE-1704 - Fixed bug where monsters made no sounds
- AE-1705 - Fixed bug where guards prioritized picking up bodies over chasing criminals
- AE-1706 - Fixed bug where the thief hid from every passing guard, and the guards spent the morning searching for him instead of keeping to their routines
- AE-1707 - Fixed bug where an NPC trying to talk to someone they could not reach kept trying all day
- AE-1708 - Fixed bug where dead monsters left a pile of bones as well as their own remains
- AE-1713 - Fixed bug where lawmen and guards could let a wanted person bleed to death from their wounds instead of arresting them
- AE-1714 - Fixed bug where ghosts drifted up out of their dungeon through the rock and fell into DevCity
- AE-1715 - Fixed bug where monsters cried out with human voices when hurt or killed
- AE-1716 - Fixed bug where people kicked a leg out behind them when they stopped sprinting
- AE-1717 - Fixed bug where Fenna's home was inside a wall

### Gameplay
- AE-1709 - Fixed bug where some foliage blocked movement
- AE-1710 - Fixed bug where bow shots were heard about a second after the arrow was released
- AE-1711 - Fixed bug where a conjured ally's body could be left behind in the world after its time ran out
- AE-1712 - Fixed bug where a body being carried could break when it left the world
- AE-1718 - Fixed bug where the mouse cursor still showed on the title screen
- AE-1719 - Fixed bug where taking items from a monster's remains counted as theft
- AE-1720 - Fixed bug where you could be left stuck on the ground after being knocked down by a fall, a shock or a spell
- AE-1721 - Fixed bug where you were knocked down again the moment you got up from a long fall
- AE-1722 - Fixed bug where the Level Up and Combat Level banners did not show when you levelled up
- AE-1723 - Fixed bug where you could walk through dungeon entrances
- AE-1724 - Fixed bug where you could walk through bounty boards
- AE-1725 - Fixed bug where you could bleed out while in jail. You are now healed when jailed
- AE-1726 - Fixed bug where a bright circle followed everyone around and shadows under lamps were inverted
- AE-1727 - Fixed bug where walls flickered near lamps at night
- AE-1728 - Fixed bug where the equipment cabinet was sunk into its wall

---

# Patch Notes Version 0.1.6: Stone Cold Bounty

Kingdoms now keep their own books, doors behave like doors, and the world got a lot more to stand on.

## New Features

### Blocks
- Added Spruce Log, Jungle Log and Birch Log
- Added Granite, Stone Bricks, Marble Block, Clay, Mud, Sandstone and Red Sandstone
- Added White Stained Glass and Light Blue Stained Glass
- Added White Carpet, Black Carpet, Brown Carpet and Red Carpet
- Added Limestone Bricks, Block of Iron, Block of Gold, Block of Copper, Block of Coal and Hay Bale
- Added the new blocks around the devmap: hay in the barn, carpets in the chancery and houses, a marble altar dais, stone brick town walls, a limestone chancery in DevTown and more

### Items
- Added the Wooden tier. Wooden weapons, tools and shields need level 1
- Added the Stone tier in place of Leather. Stone gear needs level 2

### NPC's
- Added bounty hunter posses. Once a kingdom's bounty passes 1,000G, a posse comes looking for you roughly every 30 to 40 minutes
- Added border ambushes. Fleeing a kingdom with a bounty of 100G or more can find hunters waiting just over the line
- Added law dogs, which run with the larger posses
- NPC's can now offer you items, such as an herbalist sharing food when you are hurt. A popup lets you accept or decline

### Gameplay
- Added a reminder when you enter a kingdom where you are wanted, showing your bounty there
- Added town lockdowns. With a bounty of 500G or more in a town, its shops close and the watch attacks on sight
- Added arson as a crime. Setting fire to buildings or other people's property in town carries a 200G bounty
- Added fishing animations for the rod, the small net and the crab cage, including a line and float for the rod
- Added more foliage: shrubs, thistles, daisies, bluebells, cattails, heather, dock and bracken
- Added the game's fonts to the interface, headings, chat, combat numbers and item descriptions
- Added jingles for skill level ups, combat level ups, reaching the maximum combat level, reaching level 100 in a skill, and dying

## Changes

### Blocks
- Changed Dirt, Sand, Glass and Cobblestone to new, more detailed textures
- Changed every block to have a material, which now decides its footstep sound, whether it burns, whether it conducts electricity and how it takes the light
- Changed trees and leaves of every kind so they now burn
- Changed water and mud so they now conduct electricity

### Items
- Changed the Leather tier to Stone. Leather boots, gloves and the leather pouch stay as they are
- Changed the starting outfit so new characters now start in a linen tunic instead of leather armour
- Changed old saves so leather gear now carries over as the matching Stone gear

### NPC's
- Changed Brown Bears so they now hit less hard, have less health and are less accurate
- Changed town guards so they now only go after bounties higher than 21G, unless you talk to them while wanted
- Changed lawmen so they now leave their post for a wanted player more readily
- Changed the herbalist so she now gathers herbs, and only picks berries when the herb patches are bare
- Changed guards so they no longer order a thief out of town unless they know what the thief has done

### Gameplay
- Changed leveling so it is now much slower and follows the XP curve
- Changed bounties so each new reported crime now adds to that kingdom's total, with no maximum
- Changed bounties so a bounty in one kingdom no longer applies in another
- Changed bounties to a fixed schedule: Disorderly Conduct 5G, Vandalism 10G, Animal Slaughter 30G, Horse Slaughter 50G, Trespassing 50G, Arson 200G, Murder 400G, Murdering a Guard or Lawman 500G, Murdering a Noble 600G and Murdering a Royal 1,000G
- Changed High Honor so it now halves every bounty you are given
- Changed bounty hunters so killing them or their dogs no longer adds to your bounty
- Changed bare-fisted brawls so they now count as Disorderly Conduct. With a weapon it is still Assault
- Changed foliage so it now bends as people and animals walk through it, rustles, and springs back after
- Changed doors so they now swing open up to the wall in either direction, and walking into an open door pushes it out of your way
- Changed third-person aiming so your body now turns side-on with a bow, crossbow or throwing weapon
- Changed the throw arc to a white, glowing, dotted line along the exact path the throw will take, with a ring where it lands. The ring turns red when something is in the way or the landing is too close
- Changed thrown weapons so they now tumble in flight and can bounce once or twice before coming to rest
- Changed throwing so your feet now stay planted for a moment as you release
- Changed spells on water: ice now freezes a wider patch, fire thaws ice back into water, and lightning spreads further through it
- Changed water so it now ripples around anyone standing in it, leaves a wake behind anyone wading through, and throws up spray when you run through it
- Changed the level up confetti to be smaller and quicker
- Changed the chat box to be larger, with every message wrapping
- Changed the dialogue box to be larger, with long replies wrapping onto more lines instead of shrinking
- Changed containers, bodies and shops so they now open in their own window beside your inventory
- Changed the Take All button on containers and bodies into two, one for equipment and one for items
- Changed resisting arrest so it now closes the dialogue box
- Changed the pause menu to match the Settings screen
- Changed the inventory, character, skills, journal, magic and map screens to a new look, with the new slot and tab icons
- Removed the Cut option from foliage. Foliage can still be burned away
- Removed your equipped item and Honor from the pause menu

## Technical Changes
- Added door points to the waypoint network, so people can plan trips into buildings and through doors they are able to open
- Added a step-back for people who end up off walkable ground, such as at the water's edge
- Added a shared solver for the throw arc and the throw itself, so both use the same path
- Removed the old throw prediction code
- Added regression tests for doors, locked doors, NPC routines and the throw prediction

## Fixed Bugs

### NPC's
- AE-1601 - Fixed bug where shopkeepers could not get through their own locked doors
- AE-1602 - Fixed bug where lawmen tried to route through their own locked jail cells and stood blocked
- AE-1603 - Fixed bug where the fisherman walked into the pond and stayed stuck there
- AE-1604 - Fixed bug where NPC's waited at an empty shop counter trying to buy bread
- AE-1605 - Fixed bug where the herbalist waited all day for berry bushes to regrow
- AE-1606 - Fixed bug where guards spent all morning ordering a resident thief out of town
- AE-1607 - Fixed bug where a hiding NPC could stay hidden for hours in a busy street
- AE-1608 - Fixed bug where trips to the inside of buildings were planned to the wrong place

### Gameplay
- AE-1609 - Fixed bug where you could not hit or target someone standing beside an open door
- AE-1610 - Fixed bug where door leaves swung through the walls beside them
- AE-1611 - Fixed bug where legs twisted round each other when turning on the spot instead of stepping
- AE-1612 - Fixed bug where foliage did not react to people or animals walking through it
- AE-1613 - Fixed bug where spells passed straight through water instead of affecting it
- AE-1614 - Fixed bug where picking a lock on a container was not treated as the same crime as picking a door's lock
- AE-1615 - Fixed bug where the road was paved through the floor of a house in DevCity
- AE-1616 - Fixed bug where old saves brought back the old, much faster leveling rate
- AE-1617 - Fixed bug where a campfire on grass could burn the whole meadow, the herb beds and anyone in it
- AE-1618 - Fixed bug where NPC's walked through campfires
- AE-1619 - Fixed bug where someone walking into your campfire counted as you attacking them

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
