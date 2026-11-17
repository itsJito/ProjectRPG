# Patch Notes Version Alpha 0.4.8: Troll Call

Trolls of ice and river, scorpions in the sands, demons on the burning ground, and a new map and journal to find your way to them.

Developer: Hoodie

## New Features
### NPC's
- Added the Ice Troll, a frost-crusted brute of the high snows with a white mane and a beard of icicles
- Added the River Troll, a long-armed troll slick with weed that lurks by marshes, lakes and fords
- Added the Scorpion and the Giant Scorpion to the deserts. Each seizes its prey in a pincer that holds it fast, and arches its tail over its back to sting
- Added the Lesser Demon, a squat crimson brute with curved bone horns and a snarling, fanged face, some with a long green tongue
- Added the Greater Demon, a hulking winged demon in blood red or bruised purple that lifts off the ground on its beating wings now and then. The greatest of them wear a crest and a beard
- Added demons to Ashmaw's burning ground and the depths of Ashmaw's Throat
- Added idle animations for monsters: they now look about, stretch, scratch, crouch to sniff the ground, beat their chests and shift their weight when left to themselves
### Gameplay
- Added poison. A scorpion's sting now poisons you, draining your health for a while, and your health bar turns green while it lasts
- Added being held fast. A scorpion's pincer now holds you in place for a moment
- Added footprints. Your steps, and everyone else's, now press prints into lying snow, leave wet prints on soaked ground and mark mud and sand. Horses leave hoof prints
- Added cellars beyond every stair down: the undercrofts of Loxford and Valgard, the Drunken Duck's cellar, the museum's vault and the Unlit Lantern's cellars are now rooms you can walk into, stocked and lit
- Added drops of rain you can hear one by one around you, falling on stone, water, leaves and roofs
- Added the sound of rain on the roof when you are indoors
### Blocks
- Added fifteen new furnishings, each breakable: the Barrel, Keg, Lectern, Spinning Wheel, Scroll Rack, Throne, Cradle, Coat Rack, Butter Churn, Globe, Tool Rack, Map Table, Display Cabinet, Folding Screen and Sacks

## Changes
### Gameplay
- Changed the world map: it now fills the screen, zooms in with the mouse wheel, pans when dragged, and shows a grid, a compass, a scale bar and what lies under your cursor
- Changed the world map to let you place your own markers with a right-click, listed down the side and kept in your save
- Changed the world map to show shops and services only once you zoom in, so the whole land reads by its names
- Changed the journal: main and side quests are now listed apart, coloured red for not begun, yellow for under way and green for completed
- Changed the journal to open a quest's overview when clicked, with a Set Active button that marks where it leads on the minimap and the world map
- Changed the journal to hide quests you have not begun by default. Its settings now let you show them, hide quests whose requirements you do not meet, and change the sort order
- Changed the Skills menu's unlock list to show each unlock as a coloured tag and the thing it opens, in tidy columns
- Changed enemy levels to rise the farther you travel from Loxford. Monsters, dungeons and the hostile people of encounters near Loxford are now weaker, and the far reaches of the land far deadlier
- Changed monster camps to keep their distance from people's homes, workplaces and the roads
- Changed random encounters to happen more often, and to end once you have left them far behind
- Changed random encounters to begin out of your sight and well away from you
- Changed people waiting in an encounter to come out to meet you if you take too long, to talk among themselves, and an injured traveller to kneel
- Changed dungeons to be more detailed: patterned floors, darker stone at the foot of the walls, timber beams in the mines and warrens, stalactites in the caves, sarcophagi in the crypts, and barrels, crates and sacks about the rooms
- Changed towers to give each of their floors a different use
- Changed rooms to be lit throughout: no corner of a room is now left far from a light
- Changed snow to cap the top edges of blocks and to settle on furniture and props that face the open sky
- Changed puddles to grow in the hollows as the ground soaks and shrink as it dries
- Changed the sound of the outdoors to be muffled when you are inside
- Changed the sound of rain to real recordings of rain falling over the land
- Changed Sleet to fall as rain and ice
- Changed the Troll's name to the Mountain Troll
### Items
- Changed shields to take up two by two spaces in your inventory
- Changed gold amounts to show their thousands separator
- Changed a new character's starting kit to leave out the pot and the bucket
### NPC's
- Changed the level tag beside a name to be coloured by how it compares to your combat level: green when three or more below you, yellow within two, red when three or more above

## Technical Changes
- Changed many hardcoded numbers to settings that can be tuned
- Removed unused code

## Fixed Bugs
- AE-04801 - Fixed bug where random encounters spawned in awkward places right in front of you
- AE-04802 - Fixed bug where the people of random encounters stood still and did nothing
- AE-04803 - Fixed bug where a migrating herd never moved
- AE-04804 - Fixed bug where the watch and outlaws of a skirmish never fought each other
- AE-04805 - Fixed bug where shot arrows made no sound to draw anyone's attention
- AE-04806 - Fixed bug where the outdoors did not sound muffled from inside
- AE-04807 - Fixed bug where rain sounded like a small puddle being filled
- AE-04808 - Fixed bug where rain and snow fell at the same time
- AE-04809 - Fixed bug where the Unlit Lantern's door had no wall beside it and no lintel over it
- AE-04810 - Fixed bug where the Unlit Lantern's basement led nowhere
- AE-04811 - Fixed bug where every floor of the Unlit Lantern was the same as the last
- AE-04812 - Fixed bug where a torch did not set plants on fire
- AE-04813 - Fixed bug where loose stones were strewn everywhere over stony ground
- AE-04814 - Fixed bug where random encounters appeared too seldom
- AE-04815 - Fixed bug where the Drunken Duck's interior was too dark
- AE-04816 - Fixed bug where people could not finish their daily tasks after starting the day outside their own building
- AE-04817 - Fixed bug where monsters made their homes too close to people
- AE-04818 - Fixed bug where a scholar of Harlette began the day shut inside the hollow of Dracula's Manor's turret
- AE-04819 - Fixed bug where Loxford's undertakers began the day down in the crypt under the graveyard
- AE-04820 - Fixed bug where people whose bed is upstairs came back into the world on the ground outside, or shut in a wall's hollow
- AE-04821 - Fixed bug where people could not climb a ladder up out of a cellar or a crypt
- AE-04822 - Fixed bug where people whose bed stands against a wall never got into it
- AE-04823 - Fixed bug where people asleep upstairs were drawn standing beside their bed
- AE-04824 - Fixed bug where people pottering about a cramped home kept walking into its walls

# Patch Notes Version Alpha 0.4.7: Going Underground

Dungeons beneath ShadowVeil, guild halls in every great town, encounters on the road, birds in the sky and townsfolk who pull up a chair.

Developer: Hoodie

## New Features
### Gameplay
- Added dungeons beneath ShadowVeil: every cave, warren, mine and crypt now leads down under the map, level after level, each laid out anew from its own seed and harder the deeper you go
- Added ladder shafts between a dungeon's levels, cut through the rock and walled all round
- Added torches in sconces along every dungeon's walls, and dungeons now have their own air, with no sky, weather or fog underground
- Added encounters across the world: highwaymen stepping out ahead of you on a lonely road, travellers asking for help, couriers, merchants, fugitives and their pursuers, prisoner escorts, patrols, assassins, bounty hunters, quarrels between strangers, emergencies in town, and wildlife on the move: roaming herds, predators hunting, scavengers at a carcass
- Added regional patterns to encounters: every region has its own kinds of trouble, and what you have done, the hour, the weather and your standing all change what you meet
- Added guild halls for all seven guilds: the Metallists in Valgard, the Foresters at Millwood Lodge, the Artisans in Azharad with the Arcanists' moon-scholars beside them, the Mariners, Provisioners and Arcanists in Severia, the Provisioners in Gorrick, the Farmers in Harlette and the Arcanists in Camelot
- Added Loxford Bank
- Added an Undertaker's Bunker beside every graveyard, where its undertaker lives and sleeps
- Added lava to the Ashmaw volcano: glowing gullies down its flanks, a lava-filled crater, a towering plume of smoke and embers rising from the summit. Lava burns anything that stands in it
- Added the sound of the weather: light and heavy rain, a soft wind and a howling gale, hail, a blizzard and a sandstorm, each rising and falling with the weather, muffled under a roof and silent underground
### Animals
- Added the Crow, which flocks, scavenges and caws from the rooftops
- Added the Pigeon, which flocks about towns and coos
- Added the Vulture, which circles high over the wilds and drops down onto carcasses
- Added wild animals and predators to the minimap
### NPC's
- Added chairs that people pull out from the table to sit, draw in once seated and push back when they get up
- Added people sitting on benches to talk, and at the table for their meals and their rest at home
- Added animations for milking, shearing, butchering, sowing, harvesting, picking, gathering by hand, burying, lighting a fire, stealing and picking pockets
- Added King Arthur's own look: the Crown of Camelot, the Pendragon mantle and godly arms, with the strength to bear them
- Added new undertakers, guild masters and chapter heads
### Blocks
- Added six interior wall finishes, each with a stone or timber outer face: Limewash, Painted Plaster, Linenfold Panelling, Clay Render, Zellige and Fresco
- Added Lava
- Added the Wall Sconce, a candle on the wall that lights rooms too low for a chandelier

## Changes
### Gameplay
- Changed fog to differ by region: thick over marshes, thin in the desert, tinted by the land
- Changed fog to cover the sky as it thickens
- Changed the edge of the world to lie behind a bank of fog, so nothing beyond it can be seen
- Changed the edge of the world to harm you only while you are swimming out toward it
- Changed resting at a Shrine of Light to move the weather on with the hours you rest
- Changed waterfalls to pour water down their whole height, with spray where they land
- Changed leaves, grass and plants to droop, shiver and darken in the rain
- Changed double doors to meet in the middle
- Changed animal calls to be quieter and less frequent
- Changed the air inside buildings to stay clear of the region's fog
- Changed every weather's look: billowing clouds lit from the sun's side and dark-bellied under a heavy sky, grey days drained of colour and darker, a silver edge where thin cloud crosses the sun
- Changed lightning to strike as one jagged, unbroken bolt of white light from the cloud to the ground
- Changed sandstorms, blizzards and drifting snow to blow past in soft wisps that thin out above the ground, instead of streaking at the eye
- Changed the grey weathers - Overcast, Dark Overcast, Drizzle, Rain and Showers - to darken the day as they thicken
### NPC's
- Changed shops on ShadowVeil to be called by their building's name, and people to call them so
- Changed people to say the names of the people they speak of, as they are in the world
- Changed townsfolk to talk of their own realm rather than of far-off places
- Changed everyone who wears gear to have the skills to wear it
- Changed people to begin and go about their day with their own work's outfit and skills
### Blocks
- Changed interiors to use the new two-faced wall finishes throughout
- Changed chandeliers to hang only in the grand houses of the rich, nobles and royals, and only where the ceiling clears a tall man's head
- Changed rooms with no space for a candle stand to be lit by wall sconces, taking down a painting where the wall had no other place
- Changed Loxford Bank to two storeys, with its banker's room upstairs

## Technical Changes
### Gameplay
- Changed encounters to be defined in the game's data files: who takes part, where, when, how they behave and what follows
- Changed encounters to be saved with the game and to give way cleanly when they end, are interrupted or are ignored
- Changed the fog, the world's edge, seating, lava and dungeon depth to be read from the game's data files
- Changed chairs and seats to be looked up nearby instead of across the whole world
- Changed the physics to wake only what rests near a changed block, instead of everything in the world whenever a door swung
- Changed the physics to keep its bodies in order between steps and to test only the moving ones against the world
- Changed line-of-sight and weapon checks to test only the doors and furniture near them
- Changed the dungeons' traps, the hearths, the graveyards and the engine's bodies to be found by place instead of searched for one by one
- Changed the frame-time benchmark to tour ShadowVeil's towns, woods, harbour and a battle

## Fixed Bugs
- AE-04701 - Fixed bug where ivy and plants grew inside buildings
- AE-04702 - Fixed bug where the edge of the world harmed you on dry land near the water
- AE-04703 - Fixed bug where fog did not cover the sky
- AE-04704 - Fixed bug where fog was the same in every region
- AE-04705 - Fixed bug where people still called shops 'Store A' and 'Store B'
- AE-04706 - Fixed bug where people spoke of people who are not in the world by name
- AE-04707 - Fixed bug where Shrines of Light stood in the middle of paths and roads
- AE-04708 - Fixed bug where railings round staircases did not join at their corners
- AE-04709 - Fixed bug where chairs faced away from their tables
- AE-04710 - Fixed bug where paintings and tapestries hung over doorways
- AE-04711 - Fixed bug where blocks, lamps and logs floated in the air
- AE-04712 - Fixed bug where foliage and leaves did not react to rain
- AE-04713 - Fixed bug where dire wolves and other wild animals did not show on the minimap
- AE-04714 - Fixed bug where resting at a Shrine of Light did not change the weather
- AE-04715 - Fixed bug where waterfalls had no water running down them
- AE-04716 - Fixed bug where double doors left a gap between them
- AE-04717 - Fixed bug where cows and other animals were too loud
- AE-04718 - Fixed bug where chandeliers hung in every house and low enough to touch your head
- AE-04719 - Fixed bug where bankers and guild masters could not get into the buildings they live and work in
- AE-04720 - Fixed bug where people stood stuck in doorways beside an open door
- AE-04721 - Fixed bug where people began the day on the wrong side of a wall from their room
- AE-04722 - Fixed bug where monsters stopped halfway up a ladder and slid back down
- AE-04723 - Fixed bug where archers chose a ladder's rungs as their high ground
- AE-04724 - Fixed bug where kitchens in log-built lodges and inns were walled off with no way in
- AE-04725 - Fixed bug where shops and workshops with low ceilings were left without any light
- AE-04726 - Fixed bug where armourers' workshops had no room for their anvil and furnace
- AE-04727 - Fixed bug where dungeon traps and hunters' snares on ShadowVeil never went off
- AE-04728 - Fixed bug where sandstorms and blizzards smeared streaks across the whole screen
- AE-04729 - Fixed bug where the weather made no sound

# Patch Notes Version Alpha 0.4.6: Weather or Not

A new sky over every region, a wider sea, footsteps that know what they are walking on, and townsfolk who get where they are going.

Developer: Hoodie

## New Features
### Gameplay
- Added a full weather cycle: Sunny, High Pressure, Clouds, Overcast, Dark Overcast, Mist, Dense Fog, Drizzle, Rain, Showers, Thunder, Thunderstorm, Hail, Sleet, Light Snow, Snow, Blizzard, Ground Blizzard, Sandstorm, Snow Clearing and Rain Clearing
- Added Hurricanes and Whiteouts, which never happen on their own
- Added regional weather: every region now has its own climate and its own weather at the same time. Azharad's desert sees sandstorms, Valgard's highlands blizzards, the marshes fog and drizzle, the coast showers and storms
- Added weather changes that follow the clock: each weather lasts a few hours and gives way on the hour, the way people's days turn over
- Added fog that is likelier at dawn and thunder that is likelier in the afternoon
- Added rain that clears and snow that clears, so a downpour no longer stops all at once
- Added a temperature to the air that follows the region, the hour and the weather
- Added the name of the region you enter, shown across the top of the screen
- Added more open sea round the edge of the map
- Added a deadly open sea: swimming out past the shore toward the edge of the world now harms you more and more until you turn back or die
### Items
- Added a way to turn an item in your inventory: point at it and press the rotate key
### NPC's
- Added kneeling and folded hands to people at prayer
- Added hand gestures to people while they talk
- Added resting stances to people standing about: arms folded, hands behind the back or hands on the hips, changing now and then
- Added hoofbeats to horses and Pegasi
### Blocks
- Added old headstones to every graveyard in place of the plain stone markers

## Changes
### Gameplay
- Changed rain to fall all around you in sheets that lean with the wind, stop at roofs and splash where they land
- Changed snow to drift and tumble on the wind, and blizzards and ground blizzards to blow snow along the ground
- Changed sandstorms to drive sand through the air and turn the air the colour of the desert
- Changed dense fog, blizzards, sandstorms and whiteouts to close the world in around you
- Changed storms to darken the sky, and lightning to come with thunder clouds rather than only heavy rain
- Changed the wind to swing trees and plants harder as the weather worsens
- Changed footsteps to match every kind of ground: grass, earth, mud, sand, gravel, stone, wood, carpet, straw, leaves, snow, ice, metal and glass, with a splash in shallow water and a crunch in fresh snow
- Changed footsteps to land with each foot as it touches the ground, and made them quieter
- Changed broken crates, barrels and furniture to leave pieces you can push about and walk straight through
- Changed the world to be put right when you load, sleep, die or rest at a Shrine of Light: broken furniture, crates, barrels, windows and walls are whole again
- Changed broken chandeliers and lamps to stop giving light
- Changed the region banner to sit above any bounty notice, never over it
- Changed the interact key to close a container or your inventory when one is open
- Changed lawmen to say exactly what you are wanted for, who named you and where it happened
- Changed lawmen to tell the town's undertaker by name when they take away a body
- Changed breaking out of jail to raise the alarm: the watch now searches for you and tells you so
- Changed the jail's held goods chest to hold everything taken from you when you are arrested, so it can be found if you break out
- Changed every Gaol to a Jail
- Changed sprinting into people: the first two shoves are now shrugged off with a word, and only the third makes them angry
### NPC's
- Changed people to begin the day spread out at their work, their homes or about their workplaces instead of all standing at their front doors
- Changed people to take shelter indoors from a dragon or another terror rather than crouching in the open
- Changed people to go indoors from snow, hail, sandstorms and gales as well as rain
- Changed carters to run their loads between their yard and their town's shop
- Changed border knights to stand a while at each end of their beat
- Changed people who cannot reach a prayer spot to find another, or say their prayers another day
- Changed guards to skip a patrol point they cannot reach
### Items
- Changed every tier of sword and blade to have its own form: knapped Stone, leaf-shaped Bronze, forge-marked Iron, blackened Black, ring-guarded White, banded Steel with swept guards, hewn Granite, swept Cobalt, glowing Molten, barbed Dragon, jewelled Godly and spiked Malevolent

## Technical Changes
### Gameplay
- Changed every weather, climate, temperature and precipitation look to be read from the game's data files
- Changed the weather to be saved with the game, region by region
- Changed precipitation to be drawn by the graphics card without being worked out each frame
- Changed far-away plants to be let go of one patch at a time instead of all at once
- Changed plants to be laid out further ahead of you, a few patches at a time
- Changed every new setting this update added to be read from the game's data files
- Added groundwork for the dungeons: every dungeon now has its levels and rooms planned and a recommended combat level
- Added a test that walks the whole world checking that people never spin, stall or crowd
- Added a test that checks every work station in the world stands indoors

## Fixed Bugs
- AE-04601 - Fixed bug where a church's staircase filled the way from its tower into the nave
- AE-04602 - Fixed bug where people turned round and round on the spot by altars and at their work
- AE-04603 - Fixed bug where paintings flickered the farther away you were from them
- AE-04604 - Fixed bug where your possessions were not in the jail's held goods chest after an arrest
- AE-04605 - Fixed bug where the watch did not notice when you broke out of jail
- AE-04606 - Fixed bug where you could walk through gravestones
- AE-04607 - Fixed bug where graveyards were scattered with patches of cobblestone
- AE-04608 - Fixed bug where flowing water stepped down in a sheer wall instead of running down the step
- AE-04609 - Fixed bug where people stood stuck all day short of their bed, their post or their prayers
- AE-04610 - Fixed bug where people stood stuck on staircases above a lower floor
- AE-04611 - Fixed bug where people stood pressed against an open door's leaf in narrow doorways
- AE-04612 - Fixed bug where some doorways sat below the street and could not be walked through
- AE-04613 - Fixed bug where fish could stay stuck swimming into a block
- AE-04614 - Fixed bug where chandeliers kept their light after being broken
- AE-04615 - Fixed bug where lawmen said they would take a body to Morrow in every kingdom
- AE-04616 - Fixed bug where a lawman who set a body down took its blood for a new trail
- AE-04617 - Fixed bug where plants popped in and out when running or flying fast
- AE-04618 - Fixed bug where a knocked-down body hung on to ledges for too long before getting up
- AE-04619 - Fixed bug where people crouched in the open when a dragon was near
- AE-04620 - Fixed bug where a knight could be stranded at the top of a church tower's ladder
- AE-04621 - Fixed bug where carters stood all day at their door with a blocked route
- AE-04622 - Fixed bug where many people began the game lined up in front of their homes
- AE-04623 - Fixed bug where a guard stood all day trying to reach a patrol point with no way to it
- AE-04624 - Fixed bug where people waiting for a blocked route turned slowly round and round on the spot
- AE-04625 - Fixed bug where a craftsman short of supplies paced back and forth across the workshop, turning on the spot
- AE-04626 - Fixed bug where people stood stuck for several seconds at a single step up or down on their way
- AE-04627 - Fixed bug where a fish turned back and forth against a wall instead of swimming away from it

# Patch Notes Version Alpha 0.4.5: Dressed to Kill

New clothes for everyone, water that finds its own way, and stairs you can actually climb.

Developer: Hoodie

## New Features
### Items
- Added the Pigskin outfit: a purple hood, a dark purple shirt, long purple robes, dark gloves, dark boots, a flowing purple cloak and the cult's insignia worn at the neck
- Added the Rogue outfit: a black mask that hides all but the eyes, a tight black leather top, slim black trousers, fitted gloves and dark leather boots. Requires 50 Agility and 50 Thieving
- Added the Prospector outfit: a mining helmet with its lamp, a thick tan work jacket, loose work trousers and heavy iron-toed boots. Requires 30 Mining
- Added the Desert outfit in Commoner, Noble and Royal styles: a fez, a loose desert top, a long open overcoat, light trousers and long flowing robes
- Added the Farmer's outfit: a wide straw hat, an open work jacket over a shirt, patched boro trousers and sturdy brown boots. Requires 5 Harvesting
- Added the Lumberjack outfit: a dark fitted cap, a red-and-black checked shirt, grey work trousers with braces and heavy leather boots. Requires 40 Woodcutting
- Added herb patches, flax and redberry bushes growing wild across the countryside of ShadowVeil
### NPC's
- Added ten new people to the wilds: four more of Garmund's band at his camp, three prospectors in the mine bunkhouses, two loggers at the Loggers' Rest and a charcoal burner at the clamps
- Added outfits to people who work for a living: miners now wear the Prospector outfit, loggers the Lumberjack outfit, farmers the Farmer's outfit, thieves the Rogue outfit and Garmund's band the Pigskin outfit
- Added the Desert outfit to the people of Azharad, in the style of their rank
### Gameplay
- Added a Shrine of Light by every kingdom's graveyard

## Changes
### Items
- Changed every tier of armour to have its own look: Stone slabs lashed with cord, Bronze scales with a plumed helm, Chainmail with a coif, banded Iron with a nasal helm, riveted Black plate with spiked pauldrons, fluted White plate, Steel lames with a grilled visor, hewn Granite, chevroned Cobalt with a winged helm, scaled Dragon plate with horns, cracked Molten plate glowing from within, filigreed Godly plate with a crown and jagged Malevolent plate
### NPC's
- Changed guards and knights to wear their full armour, and hunters their boots and gloves, all of which can be looted
- Changed people to only keep an eye on you, follow up something they noticed or offer help for a short while before going back to their day
- Changed people who find their way somewhere blocked again and again to leave it for a while and get on with the rest of their day
- Changed people who live or work where a window is broken to notice it, and to send word to the town's carpenter when they cannot walk the report over themselves
- Changed carpenters to go wherever in their town a repair is needed, even past their usual rounds
### Blocks
- Changed houses to face the street nearest to them
- Changed loose stones in the countryside to stay out of towns
- Changed partition walls inside buildings to reach all the way up to the roof
- Changed doorways that stand a block above the street to have a step up to them
### Gameplay
- Changed rivers to pull swimmers and waders much less, so you can now swim against a current
- Changed every river, stream, lake outflow and waterfall to flow on its own, following the lie of the land down to the sea
- Changed fast water to break into white foam below falls, through rapids and along quick channels
- Changed waterfalls to pour as streams of falling water that burst into spray and ripple the pool below
- Changed the map to mark the banks, and to stop marking every household's chests, beds and cooking ranges
- Changed dying to wake you at the graveyard of the nearest Shrine of Light if you are not bound to one

## Technical Changes
### Gameplay
- Changed the water's motion to be worked out once for the whole map and shared by swimming, wading and the water's surface
- Changed the water's surface motion to be sent to the graphics card only when you move a good distance
- Changed every outfit, armour design and water speed to be read from the game's data files
- Changed doors to only be checked when someone is moving near them, which makes towns full of buildings much cheaper to run
- Changed trees, rocks and plants to check whether they have grown back once a game minute instead of every frame
- Changed animals to only send their pace to the engine when it changes
- Changed plants for new ground to start being prepared as soon as they come into reach, which removes most of the hitch when walking into new ground
- Added a test that walks a player-sized body up and down every staircase in the world

## Fixed Bugs
- AE-04501 - Fixed bug where the Shrine of Light by Loxford's graveyard could not be used and could be walked through
- AE-04502 - Fixed bug where chandeliers hung down stairwells and over steps
- AE-04503 - Fixed bug where some houses faced the street with their side
- AE-04504 - Fixed bug where the map showed every chest as a bank
- AE-04505 - Fixed bug where fireplaces and other furniture stood inside staircases
- AE-04506 - Fixed bug where walls inside some buildings stopped short of the roof
- AE-04507 - Fixed bug where furniture could not be damaged or broken
- AE-04508 - Fixed bug where most staircases stopped you from going all the way up or down
- AE-04509 - Fixed bug where rivers pulled you along too hard
- AE-04510 - Fixed bug where there was nothing wild to harvest in ShadowVeil
- AE-04511 - Fixed bug where wall hangings and lights overlapped other furniture
- AE-04512 - Fixed bug where stone blocks were scattered against buildings in towns
- AE-04513 - Fixed bug where only some rivers flowed
- AE-04514 - Fixed bug where people followed you around for far too long
- AE-04515 - Fixed bug where people did not report or mend broken windows
- AE-04516 - Fixed bug where furnaces, anvils, ranges and other work stations stood outside buildings
- AE-04517 - Fixed bug where people could stand all day trying to reach somewhere they had no way to
- AE-04518 - Fixed bug where carpenters walked home and tried to mend a window from the other side of town
- AE-04519 - Fixed bug where people stalled in doorways that stood a block above the street

# Patch Notes Version Alpha 0.4.4: Interior Motives

Every building in ShadowVeil can now be walked into, and the people who live and work there use it.

Developer: Hoodie

## New Features
### Blocks
- Added interiors to all 509 enterable buildings across the seven kingdoms and the wilds, with floors, ceilings, partition walls, doorways and real staircases between every storey
- Added furnished rooms to every kind of building: hearths, kitchens and bedrooms in homes, shop floors with counters and storerooms, workrooms with their trade's stations, common rooms, kitchens and guest rooms in inns, banking halls with locked vaults, great halls, chambers and solars in castles and palaces, naves with pews and altars in churches, and mess halls, armouries and bunk rooms in barracks
- Added furniture chosen by how well off a building is: stone hearths, plain tables and stools in working homes, and marble fireplaces, four-poster beds and dining chairs in the grand ones
- Added light to every room, from candles, chandeliers and lit hearths, with more of them in larger rooms
- Added laid tables in homes, inns, halls and barracks, with bowls, cups, bread, jugs and a candle
- Added Iron Bars, Roman Brick, Mosaic and Banded Masonry
### NPC's
- Added a bed of their own for everyone who lives in a building; people now walk home, climb the stairs and sleep in it
- Added a place to work indoors for shopkeepers, bankers, innkeepers, bartenders, cooks, smiths, priests and lords: behind the counter, at the bar, at the range, at the anvil, at the altar or at the desk
- Added ten new people to keep the new gaols, banks and graveyards: Bogdan, Milena, Lothar, Hilde, Gunnar, Astrid, Nadim, Samir, Emrys and Rhiannon
### Gameplay
- Added a gaol to every kingdom, with barred cells; the watch now takes prisoners to their own kingdom's cells
- Added banks inside Harlette and Camelot; every banker now serves at their own counter, and one account can be used at any bank
- Added graveyards to Valgard, Gorrick, Azharad, Severia and Camelot, each with room for new graves
- Added an altar to every church; townsfolk now walk to their own church for morning prayers
- Added monster territories across the wilderness: goblin camps, orc warbands, troll haunts, imp nests, werewolf ranges, giant spider webs and restless ground, all well away from the kingdoms and their roads
- Added the sea beyond the edge of the map, stretching out to the horizon

## Changes
### Blocks
- Changed Severia to look like Rome and Byzantium, with stucco over Roman brick, banded masonry on its public buildings, low hipped roofs of terracotta tile, tiled and mosaic floors, and a domed Severian Church with a domed bell tower
- Changed river falls and rapids to pour over their edge as a sheet of falling water with spray and mist at the foot, instead of standing as columns of still water
- Changed doorways that stood above the street to have steps down to it
### NPC's
- Changed names to carry a rank or calling where a person has one, such as Sir Lancelot, Count Dracula and Father Gregory
- Changed Order Knights to carry on to the next inspection of their kingdom's round after reporting, instead of waiting by their commander
- Changed craftsmen who are out of supplies to tidy their workplace instead of standing still
- Changed bandits to keep out in the wilds, away from the towns' inns, markets and banks
- Changed farm animals to shy away from people walking past instead of charging them
### Gameplay
- Changed fog to take on the colour of the region you are in
- Changed the developer display to be smaller and easier to read, with no background behind it and each line on its own black strip

## Technical Changes
### Gameplay
- Changed lights to be chosen nearest first, so the lamps and candles near you always light the room you are in
- Changed furniture and fittings inside buildings to only be drawn when you are close enough to see them
- Changed plants to be prepared in the background as you travel, which removes the hitches when walking into new ground
- Changed doors to only be updated when someone is near them
- Changed distant shadows to refresh as soon as you turn, so they always match the view
- Changed monsters far away from you to rest where they are until you come near
- Changed animals far away from you to look around less often
- Changed people to look further afield for a way up when the nearest stairs in a large building are on its far side
- Added a quality record for every building interior, listing its rooms, furniture, working fittings, residents, how every room is reached, its screenshots and its score

## Fixed Bugs
- AE-04401 - Fixed bug where the game could refuse to draw a frame with "malformed command stream: invalid water ripple"
- AE-04402 - Fixed bug where most people stood around doing nothing or spun on the spot
- AE-04403 - Fixed bug where turning noclip off after noclipping out of water put you back in the water you had left
- AE-04404 - Fixed bug where there were no gaols in any kingdom
- AE-04405 - Fixed bug where the game hitched every few seconds while walking through towns
- AE-04406 - Fixed bug where distant shadows went wrong after turning away from the sun or a light
- AE-04407 - Fixed bug where lamps floated over the squares at 217, 39, 931 and 459, 36, 135
- AE-04408 - Fixed bug where cows attacked players who walked past them
- AE-04409 - Fixed bug where animals could get up onto fences
- AE-04410 - Fixed bug where stopping a sprint swim near a wall or the lake's edge at 177, 37, 963 pushed you under the map
- AE-04411 - Fixed bug where a body knocked down into water sank and tried to stand up on the bottom; it now swims
- AE-04412 - Fixed bug where every building interior was empty
- AE-04413 - Fixed bug where interiors were pitch black during the day
- AE-04414 - Fixed bug where a chest upstairs left an invisible obstacle on the roof or the floor above it
- AE-04415 - Fixed bug where people could not reach the upper floors of the Severian and Azharad palaces
- AE-04416 - Fixed bug where every lord and official spoke as if they were the banker
- AE-04417 - Fixed bug where nobody in ShadowVeil ever went to pray
- AE-04418 - Fixed bug where a house in the east of Severia could not be entered because its door stood above the street

# Patch Notes Version Alpha 0.4.3: Full House

Everyone the world's records name now lives in it, with a home, a bed waiting for them and a job to go to.

Developer: Hoodie

## New Features
### NPC's
- Added the full population of ShadowVeil: 205 people across the seven kingdoms and the wilds, each with their own one-word name, calling, home and workplace
- Added homes for everyone: each person now has a house, cottage, inn room, barracks bunk or hall of their own, with a bed set aside inside it for when the interiors open
- Added workplaces for everyone: shopkeepers now keep their shop fronts, guards their barracks and gates, farmers their fields, miners their mines, loggers their wood yards and fishers their waters
- Added going home for the night: townsfolk now walk to their own door at bedtime, go inside, and come out again in the morning
- The bounty hunter Corvane is now the one person in the world with no home, as the records say
### Blocks
- Added outdoor workstations beside the workshops that need them: anvils and furnaces at the smithies, sawbenches and crafting tables at the builders' yards, ranges at the kitchens, and the cloth trades' benches, wheels, looms, tubs and vats
### Gameplay
- Added wildlife across the whole map: deer, rabbits, foxes, ducks and coyotes in the meadows, woods, marshes and deserts, with wolves, bears, mountain lions and dire wolves in the deep wilds
- Added fish to every ocean, river, lake and pond, each holding the fish its kind of water should
- Added working farms: every field and garden is now sown with its kingdom's crops in rows, kept by a farmer of that kingdom
- Added the pos console command, which prints your X, Y and Z and copies them to the clipboard
- Added goto places for all seven kingdoms, every named landmark and every cave and mine mouth

## Changes
### NPC's
- Changed everyone to a one-word name. Sir Alden, Sir Roderic, Sir Lancelot, Sir Gawain, Baron Edric and Sir Roland are now Alden, Roderic, Lancelot, Gawain, Edric and Roland, and nobody shares a name with anyone else
- Changed people's titles to say what they do and where, such as Blacksmith of Valgard or Gate Guard of Severia
- Changed woodcutters and loggers to fell the trees near their workplace
### Gameplay
- Changed predators to keep well away from the towns and villages, so townsfolk are no longer scared off by wolves and lions at their doors
- Changed the developer display to be larger and easier to read, with your position in large green, red and blue figures and the people nearby set out as a table
- Changed torches so they can now be lit with a shield on the other arm; the shield now stays on the arm while the torch is lit
- Changed the cave mouths so they now open in a grassed bank, with bare rock only around the entrance
- Changed the fields so their crop rows now line up with the furrows
### Blocks
- Changed Valgard's houses to stand back from the town wall, leaving proper lanes between them
- Changed town walls to stand whole where a road runs alongside them, opening only where a road passes through
- Changed Valgard's aqueduct to keep clear of the houses and run underground where the hill rises over it

## Technical Changes
### Gameplay
- Changed the loading screen to run the world's first moments before you arrive, so the first seconds of play run smoothly
- Changed short trips for people and creatures to look for a way close at hand, which removes the long pauses when a creature first moves after loading
- Added a census of every resident to the building register, recording each building's residents, workers, planned beds and workstations
- Added automatic checks that everyone has a home, a bed and a workplace they can walk to, that every kind of water has fish, that the wild's predators keep away from the towns and that the roads are clear

## Fixed Bugs
- AE-04301 - Fixed bug where the map switched to a test map's dungeon on parts of ShadowVeil
- AE-04302 - Fixed bug where test map place names such as Store B and Chancery B floated over ShadowVeil
- AE-04303 - Fixed bug where goto, restart and reset sent you to the test map's places
- AE-04304 - Fixed bug where a torch could not be lit while a shield was equipped
- AE-04305 - Fixed bug where a torch could not be seen in first person
- AE-04306 - Fixed bug where fences on ShadowVeil were invisible, leaving a ring of invisible blocks around the farms
- AE-04307 - Fixed bug where the farms had no crops and could not be worked
- AE-04308 - Fixed bug where the buildings near 246, 55, 607 in Valgard left blind slots against the town wall and the aqueduct crossed the rooftops
- AE-04309 - Fixed bug where the culvert cave at 573, 40, 899 was a heap of loose cobblestone on the open meadow
- AE-04310 - Fixed bug where some alleyways were blocked by stray logs, earth and rocks
- AE-04311 - Fixed bug where people stood idle at work because their trade had no workplace or tools in reach
- AE-04312 - Fixed bug where smiths, builders and other crafters had no workstations on ShadowVeil
- AE-04313 - Fixed bug where there were no fish in the oceans, rivers, lakes or ponds
- AE-04314 - Fixed bug where wolves and mountain lions roamed beside Loxford and scared its people
- AE-04315 - Fixed bug where people far from the player kept test map titles such as Citizen of DevPalace
- AE-04316 - Fixed bug where two people shared the names Bram and Osric
- AE-04317 - Fixed bug where a large stutter followed loading into the world
- AE-04318 - Fixed bug where smoke and a glow from the test map's furnace hung in the air on ShadowVeil

# Patch Notes Version Alpha 0.4.2: Curb Appeal

The towns got a once-over from the street side, and every building is measured up for the furniture that comes next.

Developer: Hoodie

## New Features
### Blocks
- Added a walling of its own for each kingdom: Loxford's lime-pointed rubble stone, Harlette's ochre daub, Valgard's granite ashlar, Gorrick's tarred weatherboard, Azharad's straw-flecked adobe, Severia's warm stucco and Camelot's white ashlar
- Added inside faces to the new walls, so each is plastered or boarded on the room side
### Gameplay
- Added exterior detail to the towns and countryside: wall lanterns and hanging signs by shop and inn doors, woodpiles against cottage gables, planting along house fronts, hay by barns and stables, crates and benches outside, and pit-props by mine mouths
- Added guard rooms with doors on either side of the gatehouse passages
- Added stair wells through the upper floors of every tall building, kept open for the stairs to come
- Added a door into each wing of the palaces, temples and khans from their courtyards
- Added the devui console command, which shows the developer display
### NPC's
- Added one-word names for everyone who did not have one. The roadside bandits are now Garth, Nell and Grimbald, and bounty hunters and people called up at the console now have names of their own

## Changes
### Blocks
- Changed stained glass so it is now see-through, and sunlight now falls through church windows in coloured pools
### Gameplay
- Changed every building in a kingdom to draw its own mix of walls, roofs and trim from that kingdom's materials, so neighbouring houses no longer look alike
- Changed new characters to wake in Loxford Castle's courtyard in front of the keep door, facing the gate, instead of inside the keep
- Changed the music to follow the place you are in: each kingdom and kind of country now has its own pieces, the hymn plays at every church and shrine altar, and the dungeon music plays underground
- Changed the background ambience to rise and fall with where you are, quieter in deserts and wastes, muffled indoors and silent underground
- Changed the people standing near the fog to be drawn with less fine detail
- Changed the cave and mine entrances to slope down into the hill, so the passages beyond them now lie under solid rock
- Changed Valgard's aqueduct to fall only at its piers, so each arch now runs level
- Changed the Loxford Castle undercroft stair to stand in the open courtyard, where it can now be reached
### NPC's
- Changed fishers to work any water their gear suits when their own kind of water is out of reach, and up to two anglers can now share a lake shore or a quay
- Changed fishers who cannot fish for the moment (the tide is out, the weather is too rough) to mend their gear on the bank facing the water
- Changed Elowen to watch her net from the bank while it soaks

## Technical Changes
### Gameplay
- Changed the loading screen to also prepare the interface and the ground around every awake creature, so the first moments of play run smoothly
- Changed creatures that cannot reach where they want to go to give up for a while instead of searching for a way again and again, which noticeably lightens the game's work in the wild
- Changed people and creatures who fail to find a route to the same place to wait longer before each new try
- Added a building register of every structure in the world, recording its kingdom, size, floors, doors and the rooms it is ready to hold, checked so that every building now opens onto a usable room
- Changed the reference camera views to rise clear of any building that has grown over their spot
- Changed the automatic tests to build the test map once and reuse it, so they now finish several times faster

## Fixed Bugs
- AE-04201 - Fixed bug where the devmode command crashed the game
- AE-04202 - Fixed bug where characters made in 0.4 showed as created in an earlier version
- AE-04203 - Fixed bug where church glass was not see-through and let no light in
- AE-04204 - Fixed bug where people such as Elowen stood staring at the player
- AE-04205 - Fixed bug where fishers on ShadowVeil never caught anything, because its waters held no fish their gear could take
- AE-04206 - Fixed bug where shellfish gatherers on ShadowVeil were asked for a lobster pot instead of their crab cage
- AE-04207 - Fixed bug where a huge stutter followed loading into the world
- AE-04208 - Fixed bug where street lamps and wall lanterns on ShadowVeil never lit up at night
- AE-04209 - Fixed bug where the music and ambience on ShadowVeil followed the old test map's places
- AE-04210 - Fixed bug where the Loxford Castle undercroft stair was walled in by a corner tower
- AE-04211 - Fixed bug where the halls beyond cave and mine entrances could break through the hillside above them
- AE-04212 - Fixed bug where some reference camera views looked at the inside of a wall
- AE-04213 - Fixed bug where the automatic tests could run for hours without finishing

# Patch Notes Version Alpha 0.4.1: Brick by Brick

The seven kingdoms have walls, roofs and streets now, though nobody has moved the furniture in yet.

Developer: Hoodie

## New Features
### Gameplay
- Added the exteriors of all seven kingdoms, built in their own styles: Loxford's stone and timber-framed town under its castle, Harlette's thatched village and Dracula's walled manor, walled Valgard in pale limestone, Gorrick's timber hall behind its palisade, walled Azharad with its domed palace and courtyard khans, Severia's whitewashed city inside its land walls, and Camelot's castle ward
- Added Loxford Castle: a curtain wall with round towers, a gatehouse, a cobbled court with a well and a three-storey keep. New characters now wake inside the keep, facing the door and the gate beyond it
- Added the kingdoms' landmarks where the old maps put them, including Loxford Church and its graveyard, the Drunken Duck, the Sanctuary of Jaehaerys, the Metallists Guild, the Hojimo Monastery, Azharad Palace and Temple, the Workers' Khan, the Severian Palace, Museum, Bank, Red Moon Inn, Great Market and harbour, and Camelot's Keep, Sun Chapel and Service Court
- Added houses along the streets of every town, in each kingdom's three house sizes, with chimneys, timber framing and jettied upper floors where the culture builds them
- Added streets, squares, markets and lanes from every door to the road network, with lamp posts along the town streets
- Added town walls with gatehouses wherever a road passes through, and Pilgrim's Gate on the King's Road below Severia
- Added farms, fields, vineyards, olive terraces, a date grove, stables, mills, wood yards and work yards across the countryside
- Added the wilderness landmarks: the Waystation, Smugglers' Cove, the Tollgate on the Southern Road, the Marches Crossroads' ruined forge, the gibbets and hanging tree of Gallows Heath, the Standing Stones, the Bone Orchard, Gibbet Grove, Garmund's Camp, the Unlit Lantern on its isle, and the Mere's sunken bell
- Added the entrances of every documented dungeon, mine, cave and crypt, sealed for now: Loxford Crypt, the Castle Undercroft, the Murk caves, the Hollow Crags mines, the Spider Nest, the Museum Vault, the Lantern Cellars, Ashmaw Throat, the Sunkissed and Marble Mines and Goblin Hollow
- Added Valgard's aqueduct, carrying running water down from the highland into a cistern in the town
- Added bridges with parapets and piers wherever a road crosses water, and the rope crossing over the Murk
- Added doors to every building. The rooms behind them are empty until the interiors arrive
- Added the devmode console command, a developer display showing your frame rate, position and chunk, the region and building you are in, what you are standing on and looking at, the time and weather, and the people nearby with lines showing who can see you
- Added the devreport console command, which writes everything on the developer display to a report file and takes a screenshot
### NPC's
- Added a simpler day for people beyond the fog. They now sleep, work, eat and sell their goods while you are elsewhere, and are found where their day took them when you return
- Added kitchen gardens for the crop farmers of Loxford and Severia's vineyard terraces

## Changes
### Blocks
- Changed tree leaves to full, bushy crowns of small leaves with far fewer gaps of sky
### Gameplay
- Changed walking into a one-block rise to an automatic hop up onto it, for you and for everyone else, instead of appearing on top of it
- Changed the bank to stand in Loxford Castle's keep on ShadowVeil
- Changed the fog so it now covers the far edge of what is drawn, and trees near the fog are drawn more simply
- Changed rivers to run and bend with their channels, with waterfalls pouring down at Gleam Falls and the rapids, and spray at their feet
- Changed falling water to stream downward with white water
- Changed road verges to slope evenly to the road instead of in steps and notches
- Changed saves from older versions to carry your character over: your skills, pack, worn gear, bank vault, quest progress and Honor now come with you into the new version's world

## Technical Changes
### Gameplay
- Changed every save to also keep a character file that any future version can read
- Changed loading so the ground, plants and paths around you are ready before you arrive
- Changed the world's buildings, streets and walls to be read from data, so the layout of every town can be changed without touching the game

## Fixed Bugs
- AE-04101 - Fixed bug where some spots in the ground had one block deep holes, most of all around the spawn and along the roads
- AE-04102 - Fixed bug where stray stone blocks sat on the ground in some places
- AE-04103 - Fixed bug where fishermen, miners, woodcutters and farmers on ShadowVeil stood idle instead of working
- AE-04104 - Fixed bug where innkeepers and bartenders on ShadowVeil had nothing to do
- AE-04105 - Fixed bug where shellfish gatherers had no crab cages to work on ShadowVeil
- AE-04106 - Fixed bug where workers with a full pack walked off toward a bank that did not exist on ShadowVeil
- AE-04107 - Fixed bug where you and NPC's appeared on top of one-block-high blocks instead of jumping onto them
- AE-04108 - Fixed bug where running water only seemed to flow in some stretches of a river
- AE-04109 - Fixed bug where grass, flowers and bushes on hillsides floated above the ground or sank into it
- AE-04110 - Fixed bug where the game stuttered heavily for the first seconds after loading into the map
- AE-04111 - Fixed bug where NPC's going to bed walked toward the roof above their bed and stood waiting for a way up
- AE-04112 - Fixed bug where the mansion's servants could not reach their room or the top floor past the furniture, and the household woodcutter never lit the fire
- AE-04113 - Fixed bug where the mansion's porter started his day standing on a candelabrum
- AE-04114 - Fixed bug where fish swam off from anglers and waders standing still, and the lure changed with every cast

# Patch Notes Version Alpha 0.4.0: Lay of the Land

ShadowVeil is open: the first ground of the real world, with no towns on it yet.

Developer: Hoodie

## New Features
### Blocks
- Added Ash, the grey fall that covers Ashmaw's slopes and cinder fields
- Added Heath, the low heather of the moors with its drifts of purple bloom
- Added Podzol, the needle-strewn floor of the Dead Wood and the pine stands
- Added Dry Grass, the sun-cured turf of the Southern Marches and the desert's edge
- Added Pine Needles, the dark sprays on the ash-pines, the cypress of the north and the olive groves
### Gameplay
- Added ShadowVeil, the permanent 1,072 by 1,072 world. The game now starts there, in Loxford
- Added all seven realms' ground: Loxford's lowlands, Harlette's willow valley, Valgard's highlands, Gorrick on its shoulder above the lake, Azharad's desert basin, Camelot above the Dead Wood, and Severia along the northern coast
- Added the wilds between them: the Southern Marches, the Murk Gorge, Gallows Heath, the Hollow Crags, Dimwater and its marshes, the Dead Wood and the volcano Ashmaw
- Added the rivers and lakes. The Gleam runs north over Gleam Falls to its estuary, the Murk runs south through its gorge to Smugglers' Cove, and Dimwater, the Mere, the Oasis and the Mist Pools lie where the old maps put them
- Added the Lantern's isle in Dimwater, reached by causeways from the west and north shores
- Added the road network: the King's Road, the Southern Road, the Western and Ore Roads, the East Road, the Northern Crossing, the South Lake Road and the paths between them, with bridges wherever they cross water
- Added the northern scarp, a long escarpment broken by Pilgrim's Stair, Gleam Falls and the coast paths
- Added ocean all the way around the world, with a walkable shore of beaches, coves and shallows along every edge
- Added woods that can be felled across the whole map: oak, birch and willow in the south, palm and acacia at the oasis, ash-pine and cypress in the north, dead snags in the Dead Wood
- Added ore outcrops where they belong: copper and tin beside Loxford, coal at Azharad's clay pits, iron and coal in Valgard's mine slopes, iron, coal, mithril and cobalt in the Hollow Crags, and moltenite and dragonite on Ashmaw
- Added fishing waters on the Mere, Willow Brook, Dimwater, the Murk, the Gleam and its estuary, Smugglers' Cove, the harbour and the shores
- Added ground mist that gathers over Dimwater, Harlette's valley, the Mist Pools, the Murk Gorge, the Dead Wood and the heath's hollows at dawn, dusk, by night and in the wet
- Added each realm's own light: Loxford cool and grey, Harlette damp and misty, Azharad warm and amber, Severia bright and clear, and the wilds bleak and overcast
- Added Bloom, Depth of Field (Off, Subtle or Cinematic) and Volumetric Lighting to the Display settings
- Added a World setting, so you can return to the old development map for testing
### NPC's
- Added everyone from the development map to ShadowVeil, living near the places their trades and towns belong to. There are no buildings yet, so they keep their roles out in the open for now

## Changes
### NPC's
- Changed animals to walk, trot and gallop with their own footfalls. Wolves, the Dire Wolf, dogs and big cats now gallop with a flexing back, and every four-legged animal now bends its legs at the joints
- Changed predator attacks. Wolves and big cats now drop onto their haunches, spring with their forelegs flung out and bite with a shake of the head, and bears rise up to rake with a forepaw
- Changed how people, animals and monsters react to being hit. They are now knocked the way the blow came from, a blow to the head snaps the head and a blow to the legs buckles the knees, scaled by how hard it landed
- Changed getting off a mount to a full climb down from the saddle, the far leg swung back over and lowered to the ground by the stirrup
- Changed worshippers to stand in rows before the altar instead of all on one spot
### Gameplay
- Changed allies to show in green on the minimap and the world map: companions, your summons, and animals that are tamed to you or that you ride
- Changed natural ground so you can now walk up a single step of hillside without jumping
- Changed the minimap and world map to cover the whole of ShadowVeil, with its realms and great landmarks named on the world map
- Changed depth of field to focus smoothly on what you are looking at instead of snapping
- Changed saves to belong to the world they were made in. Saves from 0.3 and earlier do not carry over to 0.4.0

## Technical Changes
### Gameplay
- Changed loading of the world to use every core when lighting it and weathering its ground
- Changed how people plan long walks, so a crowd planning at once no longer stalls the game
- Changed far-off plants to be let go as you travel and grown again when you return, so a long journey no longer slows the game
- Changed the game to look things up by where they are, so thousands of trees and stones no longer slow the game

## Fixed Bugs
- AE-04001 - Fixed bug where NPC's could spawn inside furniture, most of all in the DevMansion
- AE-04002 - Fixed bug where NPC's jumped up onto tables and other furniture
- AE-04003 - Fixed bug where NPC's crowded together in one spot and stood idle
- AE-04004 - Fixed bug where an NPC hit by a spell or an arrow from someone they never saw still knew it was you
- AE-04005 - Fixed bug where getting off a mount had no animation

# Patch Notes Version Alpha 0.3.9: Ride or Die

Company on the road, a new beast to ride, and a watch that asks questions.

Developer: Hoodie

## New Features
### NPC's
- Added the Dire Wolf, an apex predator of the deep southern wood built like a Barbary lion, with a dark mane over its head, shoulders and belly
- Added companions. A knight who thinks well of you will ride with you, and bandits, brutes and hunters will join you for a fee, up to four at a time
- Added companion abilities drawn from what each companion is and carries: Power Strike, Taunt, Shield Wall, Volley, Battle Spell and Mend
- Added homicide investigations. A guard put on a killing goes to the body, reads how the person died, asks the neighbours what they saw, and names the killer or lets the case go cold
### Gameplay
- Added the Dire Wolf as a mount. It is broken in like a horse, quicker off the mark and in the turn, with less wind for a long run
- Added ally frames under your own bars, showing each companion's face, name, health, magic, what they are doing and which abilities are ready
- Added the Companion key (Left Alt by default). A tap gives your selected companion the order that fits what you are looking at, and holding it slows the world and opens the squad menu
- Added the squad menu, where you can pick a companion and order them to follow, rally, hold position, move, take cover or attack a marked foe
- Added the power wheel in the squad menu, which pauses the world while you call on a companion's abilities against the marked foe

## Changes
### NPC's
- Changed mounting to a full climb into the saddle instead of a snap. A blow, backing off, or an untamed mount shying away now interrupts it
- Changed people to ride as well. Companions now mount a spare saddled mount of yours when you ride and get down when you do, and anyone in the saddle rides their errands and fights from it
- Changed townsfolk out in the wilds to carry word of a crime to the nearest town's lawman
- Changed the DevMansion's household to eat at home at mealtimes instead of walking to town for bread
- Changed NPC's to speak in their own voice. Some are warm, some gruff, some nervous, some cheerful and some dry, and a face they know is now greeted by name
### Gameplay
- Changed riding so you can now attack, block, shoot, cast and sheathe from the saddle, turning in the saddle toward your aim
- Changed mounts to swim. A horse, Pegasus or Dire Wolf ridden into deep water now floats with its rider and walks out where the bottom rises
- Changed the Pegasus's flight to look alive. Its wingbeats follow the work of climbing, slowing and surging, it holds its wings out in a dive, works the outer wing harder in a bank, and flares to land
- Changed every room on the devmap to be furnished for what it is, with wardrobes, bedside tables, shelves, sideboards, clocks, rugs, paintings and chandeliers
- Changed chairs, stools, settees and benches to be solid. People now step up to a seat and sit down onto it
- Changed spells that lock on to keep the height you aimed at, so a shot at the head still hits the head

## Technical Changes
- Changed the physics so shut doors and resting objects no longer cost time every frame
- Changed route planning to prepare the whole map when a game starts, so fights and chases no longer stall on it
- Changed several systems to stop copying large lists every frame, which reduces hitches
- Changed the tuning of companions, mounts, investigations, seating and furnishing to be read from a data file

## Fixed Bugs
- AE-03901 - Fixed bug where couches, chairs, stools and candelabra could be walked through
- AE-03902 - Fixed bug where putting a log on the DevMansion's fire crashed the game
- AE-03903 - Fixed bug where riding into water threw both rider and mount and dropped the rider through the world
- AE-03904 - Fixed bug where the world froze for seconds after a fire while townsfolk weighed every burnt block
- AE-03905 - Fixed bug where a spell aimed at the head was pulled down into the body
- AE-03906 - Fixed bug where fire on some townsfolk was credited to, or burned, a piece of furniture elsewhere
- AE-03907 - Fixed bug where townsfolk spent their mornings investigating people who were nowhere near them
- AE-03908 - Fixed bug where loading an earlier save could leave blocks charred by a later fire
- AE-03909 - Fixed bug where an orc out of axes stood still below someone on a wall instead of throwing stones
- AE-03910 - Fixed bug where some townsfolk came back from an older save without their equipment
- AE-03911 - Fixed bug where the Household Baron left his estate to buy bread in DevCity
- AE-03912 - Fixed bug where townsfolk who sat down at an inn stood frozen in place after getting up
- AE-03913 - Fixed bug where a shut door could turn in its frame and jam halfway the next time it was opened
- AE-03914 - Fixed bug where townsfolk could stand all day staring at something half-seen instead of getting back to work
- AE-03915 - Fixed bug where craftsmen working far out of town spent their shifts walking to the inn and the bank

# Patch Notes Version Alpha 0.3.8: Home Wrecker

A manor in the woods, furniture worth breaking, and rain that finally looks wet.

Developer: Hoodie

## New Features
### Blocks
- Added eighteen new building materials: green, red and blue damask wallpaper, wood panelling, herringbone parquet, dark planks, a chequered marble floor, flagstones, slate roofing, timber framing, mossy stone, terracotta tiles, velvet carpet, thatch, basalt, stained glass, gravel and brass
- Added walls that are papered or panelled on the inside and stone or timber framing on the outside, in one block
### Items
- Added wooden crates, boxes, barrels and standing signs. They are physical objects that can be pushed, knocked over and thrown about, and they take damage where they are struck until they split apart board by board
- Added furniture for fine houses: chairs, armchairs, settees, stools, dining tables, writing desks, wardrobes, chests of drawers, bedside tables, sideboards, longcase clocks, kitchen dressers, pantry shelves, wine racks, four-poster beds, bathtubs, washstands, fireplaces, chandeliers, candelabra, rugs, paintings, tapestries, mirrors, weapon trophies, pot racks, suits of armour, potted ferns and marble busts
### NPC's
- Added the DevMansion household: the Household Baron, his Household Knight, a Cook, a Porter, a Firewood Cutter and an Orchard Keeper
- Added the Baron's routine. He works at his desk, looks in on each of his people, pays their wages on the first day of the week and dines at his table
- Added the Cook, who cooks from what is in the pantry's crates and barrels and serves the Baron his dinner
- Added the Porter, who carries in what is left at the gate and sets knocked-over crates and barrels back on their feet
- Added the Firewood Cutter, who fells trees for logs and keeps the house's fires burning
- Added the Orchard Keeper, who picks the estate's apple trees and leaves the apples at the gate
### Gameplay
- Added the DevMansion, a three-storey manor in a walled clearing of the southern wood with fifteen furnished rooms, two staircases, an orchard and a woodpile
- Added fireplaces that burn wood. Players can lay a fire with logs and keep it going, and it lights the room while it burns
- Added furniture that breaks. A blow knocks off the part it lands on, and enough damage brings the whole piece down
- Added skeletons that fall apart into their bones when they die

## Changes
### NPC's
- Changed monsters to fight with more sense, though never as well as people. They now read some of your blows in time to guard or step aside, punish a missed swing, follow up with a second strike and use heavy blows against a raised guard
- Changed goblins and orcs fighting alone or in pairs to close in and fight instead of circling
- Changed thieves, bandits and brutes to break open crates and barrels of goods when nobody is watching and take what spills out
- Changed goblins and orcs on the prowl to smash crates and barrels and carry off what falls out
- Changed NPC's to shove a crate or barrel out of their way, and to break through it when they are fighting or fleeing
- Changed NPC's to talk about what has actually happened around them, and about how they are doing themselves, instead of fixed gossip
### Gameplay
- Changed rain to fall as streaks that lean with the wind and stop under roofs
- Changed breaking someone's crate, barrel or furniture in town to count as vandalism

## Technical Changes
- Changed the drawing of furniture and props to do less work each frame
- Removed old unused code

## Fixed Bugs
- AE-03801 - Fixed bug where a single wolf sounded like a whole pack
- AE-03802 - Fixed bug where a javelin stuck in a player or NPC hung in the air beside their body
- AE-03803 - Fixed bug where the program's icon showed only faintly on the window and not on the taskbar
- AE-03804 - Fixed bug where a swimmer at the surface could not dive
- AE-03805 - Fixed bug where a villager woken by a noise at night gave up looking before reaching it

# Patch Notes Version Alpha 0.3.7: Lock, Stock and Ore

Locks, ore, crops and a shrine by the graves, with a long list of fixes on top.

Developer: Hoodie

## New Features
### Blocks
- Added rocks for every rung of the mining ladder: stone, coal, copper, tin, wrought iron, iron, mithril, granite, cobalt, dragonite, moltenite, godstone and malevolent
- Added a Shrine of Light beside the DevCity graveyard: a short stone tower crowned with blue flame
### Items
- Added the ores and bars for every metal from wrought iron to malevolent, each smelted at a furnace and worked at an anvil
- Added potatoes, onions, tomatoes and sweetcorn, with their seeds sold in the general stores and at the Farmers' Guild
### Gameplay
- Added a lockpicking minigame. Set the pick on its arc with the mouse and hold the left button to turn the lock. Near the hidden spot it turns further, inside it the lock opens, and anywhere else the pick strains and wears until it snaps
- Added resting at a Shrine of Light. It heals you fully, cures every ailment, passes the night to morning, calms the world and brings back the ordinary creatures and outlaws you have killed. Bosses and townsfolk stay dead
- Added respawning at the Shrine of Light you last rested at
- Added mining by breaking the rock. Each rock now takes several strikes and breaks apart in a cloud of dust and rubble, giving its ore all at once

## Changes
### Items
- Changed Smithing to follow the mining ladder. Every metal is smelted at its tier's level and worked from there, with better metals asking for more coal or a second ore
- Changed bronze to be smelted and worn from level 3
- Changed lockpicks to wear down only while they strain against the lock. A worn pick keeps its wear between locks until it breaks
### NPC's
- Changed people fighting in a group to work round to your back while one of them holds your attention
- Changed monsters in a fight to strike from the reach their weapon really has, and to stop backing off when no one else is waiting for a turn
- Changed skeleton archers to keep their distance and shoot rather than walk up and club you with the bow
- Changed townsfolk, guards, outlaws, workers, merchants and clergy to dress as their group does while each looks different
- Changed bounty hunters to wear different armour and hoods from one another
- Changed the dungeon's residents to leave each other alone
- Changed conversations to be shorter. NPC's now offer how they are, any news they actually have, their trade's advice and one question for the moment
### Gameplay
- Changed Harvesting so that apples come at level 1, potatoes at 2, onions at 5, cabbages at 7, tomatoes at 10 and sweetcorn at 20
- Changed any lock to be pickable at any Thieving level. Harder locks hide a smaller target and break picks faster
- Changed lockpicking to need no lockpick from Thieving 50, and from Thieving 25 a fresh pick now goes back where the last one broke
- Changed shift-clicking in the inventory to equip items, move every stack of a kind into or out of an open chest, and sell a whole stack at a shop
- Changed the player to tread water with their head well above the surface
- Changed the fog to a ring around the player that sits closer in, with nothing beyond it drawn
- Changed felled trees to leave a stump of their own wood
- Changed saves to stay compatible only within a minor version. Saves from Alpha 0.2 and earlier now start a new game in Alpha 0.3
- Changed the program to use its own icon

## Technical Changes
- Changed NPC's, animals, monsters and objects beyond the fog ring to no longer be drawn
- Changed dungeon torches so only those near you cast light
- Changed the inventory to draw its grid larger with the same number of slots
- Changed the Skills tab to show every unlock as a milestone track with the next one marked
- Changed the Character tab to show health and stamina as bars and your fighting skills at a glance
- Changed the smithing, smelting and cooking windows to one window with categories, recipe cards and a detail panel
- Changed crafting to tidy your pack when the result would otherwise not fit
- Removed old unused code

## Fixed Bugs
- AE-03701 - Fixed bug where the player and NPC's did not hold or aim crossbows properly
- AE-03702 - Fixed bug where dungeons spammed players with monsters
- AE-03703 - Fixed bug where dungeons were too dark to see in
- AE-03704 - Fixed bug where monsters stood still mid-fight reconsidering their route
- AE-03705 - Fixed bug where monsters swung at the air short of their target
- AE-03706 - Fixed bug where electric spells did not shock fish or anyone standing in the water
- AE-03707 - Fixed bug where every tree stump had the same texture
- AE-03708 - Fixed bug where the Skills tab did not show everything Agility and Fishing unlock
- AE-03709 - Fixed bug where hiding townsfolk stayed put while someone killed people around them
- AE-03710 - Fixed bug where the Fence would not trade for much of the day
- AE-03711 - Fixed bug where lawmen and bounty hunters surrounded a wanted player and only stared
- AE-03712 - Fixed bug where crafting refused for lack of room even when the ingredients freed enough

# Patch Notes Version Alpha 0.3.6: Rein Check

Mounts, fish and the insides of houses get a good going-over.

Developer: Hoodie

## New Features
### NPC's
- Added health bars, names and combat levels over fish that are hurt, fighting or being looked at
- Added whales that turn on swimmers who hurt them, and dive away from anyone they cannot reach
### Gameplay
- Added falls for mounts. A horse galloping or a Pegasus flying fast into a wall now goes down and throws its rider
- Added fish to the console's 'spawn' command. Spawning a fish puts it into the nearest water deep enough for it

## Changes
### NPC's
- Changed guards and lawmen to chase trespassers a short way past their borders before turning back
- Changed townsfolk to walk around dead bodies and react when they pass one
- Changed tamed animals to bear a blow from their owner rather than fight back
- Changed residents far from the player to go about their errands at their usual pace
- Changed the Fence to keep trading with customers who have a weapon drawn
- Changed the mountain lion to have real big cat calls and growls
### Gameplay
- Changed Pegasus steering so pushing forward climbs and pulling back dives, with even turning both ways
- Changed horses and Pegasi to push through leaves and bushes, slowing a little
- Changed riders to sit properly in the saddle with their feet in the stirrups
- Changed deer and other animals to slow down in water. Nothing now runs faster wading or swimming than on land

## Technical Changes
- Changed house interiors to light smoothly, without bands or patches across the walls
- Changed shadows close to the camera to update every frame
- Changed busy fights and crowded areas to run more smoothly
- Changed fish to only check the fish around them, which keeps large shoals cheap

## Fixed Bugs
- AE-03601 - Fixed bug where deer could sprint extremely fast, especially in water
- AE-03602 - Fixed bug where fish had no collision or health bars
- AE-03603 - Fixed bug where whales did not protect themselves
- AE-03604 - Fixed bug where the 'slay' command did not work on fish
- AE-03605 - Fixed bug where the 'spawn' command did not work with fish such as sharks
- AE-03606 - Fixed bug where townsfolk did not react to or avoid dead bodies
- AE-03607 - Fixed bug where interiors were badly glitched, especially on lamp-lit walls as the lamp moved out of view
- AE-03608 - Fixed bug where climbing out of the fountain in DevPalace or the well in DevTown glitched the player
- AE-03609 - Fixed bug where players and NPC's did not sit properly on horses and Pegasi
- AE-03610 - Fixed bug where riding a mount caused visual flickering
- AE-03611 - Fixed bug where mounts did not fall when hitting a wall at speed
- AE-03612 - Fixed bug where walking backwards on a horse or Pegasus looked like moonwalking
- AE-03613 - Fixed bug where horses and Pegasi got stuck on foliage
- AE-03614 - Fixed bug where Pegasus steering in the air was lopsided and its climb and dive were the wrong way round
- AE-03615 - Fixed bug where a Pegasus played footstep sounds while flying
- AE-03616 - Fixed bug where tamed animals defended themselves against their owner
- AE-03617 - Fixed bug where the game stuttered in areas with many NPC's
- AE-03618 - Fixed bug where NPC routines stalled away from the player
- AE-03619 - Fixed bug where a head under water was not counted as under water
- AE-03620 - Fixed bug where fire spells passed through foliage without setting it alight
- AE-03621 - Fixed bug where shadows lagged behind and flickered
- AE-03622 - Fixed bug where the mountain lion's sound was a person saying the word
- AE-03623 - Fixed bug where players could not trade with the Fence

# Patch Notes Version Alpha 0.3.5: Hook, Line and Guilder

The brook now runs into a sea full of fish, and the trades have organised themselves into guilds.

Developer: Hoodie

## New Features
### Blocks
- Added the sea along the south of the map, where the woodland brook runs out. A sand beach leads down to a wading strip, a shelf and open water deep enough to dive in
- Added a timber pier running out over the deep water, with lamps at its head, and a rocky islet off the western shore
- Added Guild Row, a street of halls along the seafront: the halls of the seven guilds, the Armory and the outworkers' cottage
- Added the carding bench, spinning wheel, loom, fulling tub and dye vat
### Items
- Added 20 new kinds of catch, each with a cooked dish: Sardine, Guppy, Herring, Mackerel, Cod, Salmon, Tuna, Lobster, Bass, Catfish, Swordfish, Squid, Haddock, Shark, Electric eel, Anglerfish, Blue marlin, Whale steak and Black marlin, plus the Starfish
- Added the Harpoon, Lobster pot and Fishing bait
- Added Shears, Cotton and Cotton seeds
- Added Clean wool, Clean cotton, Woollen yarn, Cotton thread, Linen thread, Loom-state cloth, Fulled cloth, Dyed cloth and Fine cloth
- Added cooking recipes for every new fish, and for crab, which had none
- Added casting a Pot from a bronze bar and a Bucket from an iron bar at the furnace
### NPC's
- Added real fish. Every pond, stream, the brook, the lake and the sea now hold their own fish, which swim, shoal, keep to their depth, scatter from splashes and swimmers, hunt smaller fish and come to a line they can be caught with
- Added sharks that circle and bite swimmers in the deep, electric eels that shock anyone who swims too close, and whales that surface to blow
- Added the seven guilds: the Metallists, Foresters, Artisans, Farmers, Provisioners, Arcanists and Mariners, each with a guildmaster and a hall
- Added fourteen people to Guild Row: the guildmasters, the Armory's keeper, a carder and a spinner, a fuller, a dyer, a smelter and a coastal fisher
- Added the Carder, Spinner, Weaver, Fuller, Dyer and Smelter trades
### Gameplay
- Added fishing levels for every catch, from Shrimp at level 1 to Black marlin at level 100
- Players can now catch a tuna with their bare hands by punching it, with Fishing 55 and Strength 35
- Added fighting fish. A blow, an arrow or a spell can kill a fish, and it trains your fighting skills rather than Fishing. A dead fish floats up to be picked from the water
- Added guild membership. Players can join a guild once one of its skills is high enough and the dues are paid
- Members now work a quarter quicker at their own guild hall's stations, and the guild store pays them more for their goods and sells to them for less
- Added the textile trade. Wool and cotton are sorted and carded, spun, woven, fulled, dyed and finished into cloth, each step at its own station
- Added shearing. Sheep can be shorn with shears, and their fleece grows back over a few days
- Added the metal trade on Guild Row. The miners' ore is smelted at the hall's furnaces, forged at its anvil and sold at the Armory
- Added evidence to the watch's questioning. Witnesses remember the weapon, the clothes, the hair or helmet, any blood, any wounds, the build and any stolen goods, and the watch now tells you what fits
- Added naming someone else who fits the witness's description when the watch stops you. If they fit as well, they are taken in instead
- Added signs along Guild Row and at the sea and the pier

## Changes
### Blocks
- Changed the brook to run on across the beach into the sea
### Items
- Changed Linen cloth to Cloth. It is now finished from fulled cloth rather than woven straight from flax
- Changed fishing gear so any net, rod, harpoon or pot can be tried at any fishing spot. What it catches depends on the fish there
- Changed fishing rods to use Fishing bait when you carry it, which draws fish from further away
### NPC's
- Changed deer to look less like horses: a slimmer upright neck, a short pointed head with a dark nose, longer thinner legs and a white rump
- Changed ducks on the water to sit low, paddle with their feet and tip up to feed
- Changed ducks, deer, mountain lions, foxes and wolves to have their own recorded calls
- Changed some dead bodies to float in water. Plate and mail drag a body under
- Changed searching guards and townsfolk to give up on places they cannot reach, check bushes, tall grass and doorways first, and call out while they look
- Changed people in a guild to greet members of the same guild
- Changed people to talk more about what is around them: your catch, fine cloth, a harpoon, being soaked, the sea and recent arrests
### Gameplay
- Changed Talk-to to be the main thing pressing at a person does. While sneaking up close, it picks their pocket instead
- Changed eating, drinking and filling a bucket or bottle to take a moment, with their own movements. Moving away or being hit stops them
- Changed every crafting station to have its own working movements
- Changed running water to visibly flow downstream
- Changed water to ripple around swimmers and anyone wading, with a wake and splashes from each stroke

## Technical Changes
- Changed the map to 192 by 256 blocks to make room for the sea
- Changed the world map to show the whole map, sea included
- Changed fish to only swim as live fish near the player. Elsewhere the waters keep count of their fish
- Changed lookups of people, animals, monsters and objects to be much faster
- Removed unused code

## Fixed Bugs
- AE-03501 - Fixed bug where garden crops swayed and wobbled like loose leaves
- AE-03502 - Fixed bug where water did not move or react when someone swam or waded through it
- AE-03503 - Fixed bug where skirts and long tunics flared out wildly on someone sneaking or on a body being carried
- AE-03504 - Fixed bug where the watch could talk to you from across a room, through a wall or from below a roof
- AE-03505 - Fixed bug where people searching for someone sometimes stood still in one place
- AE-03506 - Fixed bug where falling into water could kill you. Water now breaks a fall
- AE-03507 - Fixed bug where plants snapped back upright behind you or stretched when stepped on
- AE-03508 - Fixed bug where a finished music track caused errors in the log

# Patch Notes Version Alpha 0.3.4: Sink or Swim

The water is deep enough to swim in now, the woods have deer and the inns brew their own ale.

Developer: Hoodie

## New Features
### Gameplay
- Added swimming. Players can now float in water two blocks deep, swim with the movement keys, sprint to swim flat out, look up or down to rise or dive, hold jump to rise and crouch to sink
- Added breath under water. A row of bubbles shows how much is left, and running out hurts until you surface
- Added climbing out of the water onto a low bank by swimming into it with jump held. Agile swimmers can hold sprint to haul themselves out onto higher ledges
- Added currents. The stream and the woodland brook now carry swimmers downstream
- Swimming now trains Agility, and a higher Agility makes swimming easier and lets you hold your breath longer
- Added a blue-green tint to the view under water
### Blocks
- Changed the middle of the lake to three blocks deep, so there is room to dive
### Items
- Added the Glass Bottle. It can be filled at any water's edge or thrown, and it breaks loudly where it lands, drawing people and monsters to look there instead of at you
- Added the Bottle of Water, used for brewing
- Added Barley, Hops and their seeds, grown in the town gardens and sold to the inns
- Added the Antler, taken from a butchered buck
- Added Raw Venison and Venison, and Raw Duck and Roast Duck
### NPC's
- Added the Deer. Herds browse the wild wood and the southern wood, bolt when approached, can swim, and are hunted by bears, wolves and mountain lions. Bucks carry antlers and fawns are spotted
- Added a bear to the southern wood
- Added Follow, Stay and Dismiss for tamed animals. A dismissed animal goes back to its own routine but stays tamed and yours
- Added brewing to the inns. The cook brews ale from barley and hops, and takes the empty bottles to the nearest water to fill them
- Ducks now sit on the water and paddle across their pond
- Wolves, dogs, foxes, coyotes, bears, horses, Pegasi, cows and deer can now swim across deep water
- Monsters now hear noises and go to see what made them

## Changes
### Items
- Changed Ale to come in a glass bottle. Drinking it now gives five extra levels of Strength that wear off over a few minutes, and you keep the empty bottle
### NPC's
- Changed horse, Pegasus and deer legs to bend at the knee and hock, so their walk, canter and gallop now lift and fold each leg
- Changed townsfolk walking in the rain to hold a hand flat over their brow with the head ducked, instead of fidgeting at the face
- Changed patrons who drink ale at the inn to hand the empty bottle back to the bar, and to get the same Strength boost as players
- Changed the kitchen gardens to grow a row of barley in DevCity and a row of hops in DevPalace
### Gameplay
- Changed plants to tip over from the root when walked through, so they no longer stretch or squash out of shape
- Changed plants to only be pressed by bodies standing among them, not by someone on a ledge above

## Technical Changes
- Changed water to be read as volumes with real surface heights, so a body at the surface is judged by the surface
- Changed swimming to run on its own fixed timestep, separate from the frame rate
- Changed the check for who is in water to skip anyone not standing next to any
- Removed unused code

## Fixed Bugs
- AE-03401 - Fixed bug where smashing a window gave a bounty for every pane. A run of damage now counts as one act of vandalism
- AE-03402 - Fixed bug where horses and Pegasi could jump into a wall or block and get stuck in it
- AE-03403 - Fixed bug where foliage looked stretched when bumped into or stood on
- AE-03404 - Fixed bug where you could still talk to, trade with or buy from someone running away from you
- AE-03405 - Fixed bug where a horse or Pegasus let go of in mid-air stayed frozen in the air until it was ridden again
- AE-03406 - Fixed bug where a horse stepped off from on the ground stood frozen in place until it was ridden again
- AE-03407 - Fixed bug where getting off a mount mid-jump dropped you straight onto the ground below. You now fall from the saddle

# Patch Notes Version Alpha 0.3.3: Hold Your Horses

Mounts mind their footing, shops keep their hours and the lamps stay lit indoors.

Developer: Hoodie

## New Features
### NPC's
- Added opening hours to every shop, stall and bar. They follow their keeper's week, so a shop shuts for dinner, closes early on Solday and stays open late on market day
- Added people warming their hands at a fire on a cool evening, and hugging their arms against the cold when they stand outside at night
- Added monsters remembering who hurt them. One that held its own comes at you sooner next time, and one you beat keeps its distance
### Gameplay
- Added gliding for Pegasi. Holding the jump button in the air spreads the wings and lets you sink slowly to the ground
- Players can now ask a merchant when they're open
- Added today's hours to the shop window

## Changes
### Gameplay
- Changed overhead speech bubbles to compact dark panels with a thin border, evenly balanced lines and a gentler fade. They no longer stack on top of each other
- Changed shops to refuse trade outside their hours, saying when they open next. Townsfolk wait for opening time too
- Changed light blows that only just stagger someone into a stumble they recover from, instead of knocking them flat
- Changed a mount's best jump to clear two blocks at most
- Changed hard landings on a mount to hurt the horse a little and shake the rider
- Changed indoor lamps to stay lit through the day, so shops and inns are no longer dark at noon
- Changed rooms to get a soft daylight fill from their windows and doors
### NPC's
- Changed guards and townsfolk to stop repeating the same piece of news to everyone who walks past
- Changed people to look up past the edge of a ledge or roof to see someone standing just above them
- Changed townsfolk to keep working through light rain in their working hours, and to shelter under the nearest roof in a downpour instead of walking all the way home
- Changed Crop Farmers and Orchard Keepers to sell a full basket before picking more, and to drop off what they carry whenever they pass the inn or market
- Changed Hunters to let an animal go when they can't get a clear shot at it
- Changed foxes and coyotes to only threaten whatever they're hunting

## Technical Changes
- Changed people to judge their progress by how far they've walked rather than by straight-line distance, so long routes are no longer thrown away and re-planned over and over
- Changed the list of everyone in the world to be gathered once per moment instead of many times
- Changed the check for who is mid-swing nearby to be shared by everyone
- Changed indoor lamps to remember whether they're under a roof instead of checking every frame
- Removed unused code

## Fixed Bugs
- AE-03301 - Fixed bug where the rider clipped into the horse or Pegasus when it reared, rocked or climbed
- AE-03302 - Fixed bug where falling off a drop on a mount ragdolled the rider, who then snapped back into the saddle. Horse and rider now go down together
- AE-03303 - Fixed bug where horses and Pegasi could climb onto roofs
- AE-03304 - Fixed bug where a mount could get stuck in a wall and be unable to move or turn
- AE-03305 - Fixed bug where NPC's lost sight of anyone standing right above them
- AE-03306 - Fixed bug where NPC's kept abandoning their route in and out of buildings and stood around re-planning
- AE-03307 - Fixed bug where interiors glowed deep red and were nearly black during the day
- AE-03308 - Fixed bug where rings of light appeared on interior walls
- AE-03309 - Fixed bug where a hanging lamp left a black patch on the ceiling above it
- AE-03310 - Fixed bug where Guards and Lawmen kept asking about the same small fine over and over
- AE-03311 - Fixed bug where closing a Guard's fine conversation left it hanging, so they asked again
- AE-03312 - Fixed bug where people on fire threw off little rocks instead of smoke
- AE-03313 - Fixed bug where skirts and robes disappeared when someone was knocked off balance or fell
- AE-03314 - Fixed bug where NPC's ate their entire harvest in one sitting, so the inns never got any produce
- AE-03315 - Fixed bug where townsfolk couldn't reach the Lawman to report a crime and stood around for the whole morning
- AE-03316 - Fixed bug where NPC's picked up an old investigation again long after it had gone cold
- AE-03317 - Fixed bug where a beginner's low swing passed over small animals at your feet
- AE-03318 - Fixed bug where stepping off the bottom of a ladder while still holding back put you straight back onto it

# Patch Notes Version Alpha 0.3.2: Inn Season

Two inns open their doors, the gardens are planted, and everyone keeps a week now.

Developer: Hoodie

## New Features
### Blocks
- Added kitchen gardens in DevCity and DevPalace, with rows of tilled plots that can be sown, grown and harvested
- Added apple trees in an orchard beside each garden. Their apples come back a few at a time through the day
- Added benches at the inn tables. People sit on them to eat and drink
### Items
- Added Apples, Carrots and Cabbages. Each can be eaten to restore health or fed to a horse or Pegasus
- Added Carrot seeds and Cabbage seeds, sold at the markets and gathered back when you harvest
- Added Vegetable stew, cooked from a carrot and a cabbage on a range
- Added Hot cider, mulled from two apples on a range
### NPC's
- Added the DevCity Inn and the DevPalace Taberna. Each is kept by an Innkeeper, a Bartender and a Cook
- Added Tobin and Hedda, DevCity's Crop Farmer and Orchard Keeper, and Rufus and Valeria in DevPalace
- Added weekly schedules for everyone. Each person's week has its own rhythm, and no two of their days are the same
- Added townsfolk visiting the inns for meals and evenings out. They buy food and drink at the bar, sit down and chat with whoever shares their table
- Added people talking with their hands. Shouts point, whispers go behind a hand and ordinary talk gestures along with the words
### Gameplay
- Added renting a bed for the night from an Innkeeper
- Added the gardens to the economy. Farmers sell their harvest to the inns, cooks turn it into stew and cider, and a bar that runs out of carrots stops serving stew
- Added developer commands for mounts. Spawning a horse or Pegasus can now set it tame, saddled, its tameness or its agitation, and the mount command adjusts the one you're looking at

## Changes
### Gameplay
- Changed saddling to work on any horse or Pegasus however agitated. You take it by the bridle and hold it while the saddle goes on, which takes longer the more worked up it is
- Changed apples and carrots to be a treat for horses and Pegasi, earning more trust and restoring their vigor
- Changed melee swings to look clearly different by skill. Beginners hack down from beside the head and stumble after the blow, while masters sweep through with the whole body and return to a high guard
- Changed the sprint so the legs no longer cycle too fast. Sprinting now takes longer strides at a steadier pace
- Changed the charge to run lower through the knees with the chin tucked, driving forward on each step
- Changed two-handed swords to wind back over the shoulder before a cut
- Changed crossbows to be held in both hands at the hip, raised to the cheek to shoot, and tipped nose-down while the string is drawn back
- Changed overhead speech to the Comic Book font, in softer balloons that ease in and fade out
- Changed the grass, dirt, mud, cobblestone, brick, roof tile, plaster and bark textures to be painted with more depth and less repetition
- Changed the area around you to be fully grown in before you spawn
- Changed witnesses to only stay on the wanted panel while they're on their way to report you, or for a short while after they saw you
### NPC's
- Changed monsters that can't reach you on a roof or wall to pace below, roar up at you and throw stones
- Changed giant spiders to die where they stand, rolling onto their backs and curling their legs
- Changed people on an errand to the bank, the inn or the market to keep going in the rain instead of turning back

## Technical Changes
- Changed people far from you to rethink their plans less often, which saves time without changing their day
- Changed fire to only track the plants and furniture near you

## Fixed Bugs
- AE-03201 - Fixed bug where fire spells didn't burn grass and plants after travelling around the map
- AE-03202 - Fixed bug where melee swings looked the same at every skill level
- AE-03203 - Fixed bug where sprinting legs moved too fast
- AE-03204 - Fixed bug where crossbows weren't held in the hands in third person
- AE-03205 - Fixed bug where two-handed swords stood straight up through the wind-up and snapped round at the last moment
- AE-03206 - Fixed bug where giant spiders slid across the ground and lost their legs when they died
- AE-03207 - Fixed bug where witnesses stayed on the wanted panel for too long
- AE-03208 - Fixed bug where plants appeared around you a little at a time after spawning
- AE-03209 - Fixed bug where some townsfolk walked back and forth outside the bank in the rain

# Patch Notes Version Alpha 0.3.1: Bright Lights, Bridled Nights

Brighter nights, easier horses, and a little more nerve behind every swing.

Developer: Hoodie

## New Features
### Gameplay
- Added saddling an untamed horse or Pegasus. Hold a saddle and walk up to a calm one to ease it on, and it is far easier to break in afterwards
- Added gentling. Feeding or petting a wild horse or Pegasus now earns a little of its trust and taming before you ever climb on
- Added barging through doors while charging. A shut door now flies open as you hit it, and a locked one breaks open
- Added bursting through glass while charging
- Added four kinds of overhead speech: normal talk, shouts in a jagged burst, whispers in a dashed grey balloon and thoughts in a cloud
- Added melee animations that follow your skill. Low Strength swings from the arm while high Strength turns the whole body into the blow, low Attack wavers and hesitates while high Attack snaps clean through, and high Defense returns to guard faster. Level 99 in all three is the cleanest swing in the game
- Added footfall effects. Running kicks up dust on dry earth and sand, splashes on soaked ground in the rain and powder in the snow
- Added meadow patterns to grass, with broad lighter and darker swathes that make the land easier to read
### Items
- Added the Bounty scroll carried by bounty hunters. Every hunter now carries a signed warrant for your bounty, and one who loses it gives up the hunt
### NPC's
- Added bystanders flinching and covering their heads when a weapon is swung close to them
- Added townsfolk shielding their heads from the rain when out in it
- Added wounded monsters hunching over and limping, and flinching back from blows
- Added a slow hover to ghosts and a restless flutter to flying imps
### Blocks
- Added four new kinds of tall grass: wild oats, foxtail, feather grass and tussock grass, growing mixed in with the meadow grass

## Changes
### Gameplay
- Changed pressing Interact to always pick the most relevant action. Walking up to a horse with a saddle now saddles it instead of petting it
- Changed taming a horse on its back to be quicker and to throw you less often, especially with a saddle on and some trust earned
- Changed wild horses and Pegasi to let you walk up to them calmly when you carry food they eat
- Changed spell soft lock-on to catch targets just beside the crosshair and to curve gently toward where they are heading
- Changed swinging a torch overhead or while crouched to set grass and plants at your feet alight
- Changed burning plants to spread their fire to the plants around them
- Changed grass and plants to bend smoothly away as you walk through them and spring back after
- Changed bounty hunters to set out much further away, never within 50 metres of you
- Changed the charge sprint animation to drive lower and harder, shoulder first with the lead arm braced
- Changed ladder climbing so the hands reach for the rungs as you step on, and the body leans over the top as you climb off
- Changed nights to be moonlit and readable instead of near black
- Changed the title screen to be properly lit
- Changed indoor rooms at night to be lit beyond the lamp pools
- Changed lamps to light a wider area
- Changed the light to give blocks clearer lit and shaded sides
- Changed grass, cobblestone and stone textures to be softer and easier on the eye
- Changed the detection words to Hidden, In view, Noticed, Searching and Spotted, matching the wanted panel, and now says who noticed you
- Changed the place name under the minimap to match the clock's style
- Changed blood to spray as fine streaks and drip from the wound itself
### NPC's
- Changed guards and lawmen to step in when someone attacks a farm animal or a pet in front of them
- Changed ghosts to drift through walls
- Changed ghosts and imps to see clearly in the dark
- Changed imps to hit and run, darting in to scratch and swooping out again
- Changed flying monsters to drop down to strike rather than swinging over your head

## Technical Changes
- Changed shadows from lamps and torches to always go to the nearest lights
- Removed film grain from the picture
- Removed some unused code

## Fixed Bugs
- AE-03101 - Fixed bug where horses and Pegasi could not be mounted, saddled or tamed in practice
- AE-03102 - Fixed bug where arrows and thrown weapons in third person flew beside the crosshair instead of to it
- AE-03103 - Fixed bug where spell lock-on missed targets that were plainly under the crosshair
- AE-03104 - Fixed bug where swinging a torch or casting a fire spell could set yourself on fire
- AE-03105 - Fixed bug where a swung torch never set tall grass alight and fire did not spread through plants
- AE-03106 - Fixed bug where plants snapped and flipped when stepped through
- AE-03107 - Fixed bug where people could not find their way up to another floor
- AE-03108 - Fixed bug where mounting and dismounting ladders looked stiff and sudden
- AE-03109 - Fixed bug where guards let a brawl carry on once the victim hit back
- AE-03110 - Fixed bug where imps flew straight past people at night without noticing them
- AE-03111 - Fixed bug where ghosts swung over people's heads and never landed a hit
- AE-03112 - Fixed bug where ghosts were stopped by walls
- AE-03113 - Fixed bug where lamp and torch shadows flickered on and off while moving
- AE-03114 - Fixed bug where the light turned over suddenly at noon
- AE-03115 - Fixed bug where fine details shimmered on a still picture
- AE-03116 - Fixed bug where the clock showed twice under the minimap
- AE-03117 - Fixed bug where bounty hunters appeared too close to you
- AE-03118 - Fixed bug where speech balloons overlapped each other

# Patch Notes Version Alpha 0.3.0: Saddle Up

Horses, a winged horse, a calendar, and better light to see it all by.

Developer: Hoodie

## New Features
### Gameplay
- Added mountable horses. Walk up and choose Mount to ride
- Added riding controls: forward to canter, Sprint to gallop, Crouch to walk, back to slow and reverse, left and right to rein round, and Interact to dismount
- Added charged mount jumps. Hold Jump and let go at the peak of the meter for the best jump; each horse jumps as high as it can
- Added horse taming. A wild horse lets you on but goes where it likes and may throw you, and every moment you stay on is progress it remembers
- Added saddles. A tamed horse needs one to be steered, and a saddled horse carries saddlebags
- Added the Pegasus, a heavily built winged horse found wild in the wood. Gallop and jump at full charge to take off, dive to build speed, climb to spend it, bank to turn and surge with your wings while vigor lasts
- Added the Weekly System. The world now keeps a calendar starting Solday, March 5, 500, with the days Solday, Lunday, Marday, Merday, Iovday, Highday and Sterday
- Added the day and time under the minimap
- Added Solday afternoons as the week's rest, when most townsfolk put their work down and visit each other
### Items
- Added the Torch. It takes up two inventory spaces, lights with the Guard key while drawn, lights up everything around you, and can set flammable things alight when swung
- Added the Saddle, sold at the general store
### NPC's
- Added facial expressions: happy, sad, surprised, disgusted, tired, thinking and pained, on top of the old ones
- Added blinking and lips that move with the words NPCs are saying
- Added a day diary. NPCs now remember what they did today and tell you about it when asked

## Changes
### Gameplay
- Changed ledge hanging to be seamless. Keep holding Sprint to climb straight over, or let go to hang and shimmy
- Changed one-block-high edges so they are always climbed, never hung from
- Removed the on-screen hint while hanging from a ledge
- Changed the shoulder charge to start only after two full seconds of sprinting
- Changed the shoulder charge so it only knocks people down once it has been kept up a moment longer
- Changed ladders to be climbed facing them. You snap onto them, climb rung by rung, climb out at the top and step off at the bottom, and you can climb onto one from the ledge above
- Changed time to pass slower by day and quicker by night, about 48 minutes for a full day
- Changed tall grass to be taller and to grow in thicker drifts. Crouching in it now hides you better
- Changed lighting through the day and in every weather, with warmer golden hours, bluer nights, flatter overcast light and darker corners indoors
- Changed blood to fly and splash like a liquid, leave splats where it lands, and run down bodies for a few seconds before it dries
- Changed NPC speech bubbles to a comic book style, with bursting yellow bubbles for shouts
### NPC's
- Changed most townsfolk to combat level 3
- Changed NPC dialogue to three or four questions that change with their day, what they have heard and what is on their mind
- Changed bandits and thieves to keep their heads down when the watch is in sight
- Changed witnesses so they can only report an attack they actually saw. Others nearby now form suspicions instead
- Changed fighters so no more than two press an opponent at once while the rest circle and wait their turn
- Changed fighters to pace their blows and sidestep between them
- Changed monsters to sidestep blows they see coming and punish missed swings
- Changed monsters to roar as they square up to a foe, calling their kin nearby to join the fight
- Changed monsters to drop into a fighting crouch while standing their ground, and to shift their weight and look about when idle
- Changed the Undertaker to lay a body on its gravestone before burying it, with its belongings kept in the grave
- Changed the running and jogging animations to be taller, with a stronger arm swing and knee drive
- Changed builders to kneel and bend into their repairs with their whole body
- Changed fishing to move the whole body through the cast and the haul, for NPCs and players alike
- Changed shimmying to reach further with the leading hand and sway with the movement

## Technical Changes
- Changed the game engine to version 2.9 with environment presets for ambient light and colour grading

## Fixed Bugs
- AE-03001 - Fixed bug where shields stuttered while being sheathed or drawn
- AE-03002 - Fixed bug where crossbows looked like a thin stick when held in third person, and hung upside down on the back
- AE-03003 - Fixed bug where arrows, bolts and spells in third person did not land where the crosshair was
- AE-03004 - Fixed bug where dying while hanging from a ledge respawned you on the ledge
- AE-03005 - Fixed bug where the Undertaker did not properly bury bodies
- AE-03006 - Fixed bug where townsfolk stood around stuck on errands they could not reach
- AE-03007 - Fixed bug where NPCs asked where you went while you were still in plain sight
- AE-03008 - Fixed bug where the player kept walking injured after healing fully
- AE-03009 - Fixed bug where some animals such as dogs did not fall over when killed
- AE-03010 - Fixed bug where ladders could be climbed from any direction
- AE-03011 - Fixed bug where a charged heavy attack dropped the weapon back down before swinging
- AE-03012 - Fixed bug where guards investigated the same corner over and over for every villager who came to them

# Patch Notes Version Alpha 0.2.9: Hang in There

Some new ways to get a grip, and a few to lose it.

Developer: Hoodie

## New Features

### Gameplay
- Added ledge hanging at Agility level 6. Climbing a ledge now holds you at the edge, and pressing Jump again pulls you up.
- Added shimmying along a ledge with the strafe keys while hanging
- Added letting go from a hang with Crouch, and leaping off the wall behind you with Back and Jump
- Added controlled drops. At Agility level 6, crouch-walking off an edge lowers you onto it to hang instead of falling.
- Added the Shoulder Charge at Agility level 10. A full sprint is now run shoulder-first and barges through people in the way.
- Added an on-screen guide while hanging that shows your options and how long your grip will last

### Blocks
- Added stone stairs up the inside of DevCity's town wall
- Added ladders up each of DevPalace's corner towers to a lookout among the battlements
- Added stairs inside both of DevPalace's gatehouses up to their roofs
- Added a timber lookout tower with a ladder in DevTown

### NPC's
- Added new conversation choices. Anyone can now be asked for directions, about local dangers, what they think of you, and what the store is short of.
- Added compliments and jibes. People now remember a kind word or a cutting one, and think of you a little better or worse for it.

## Changes

### Gameplay
- Changed bounty payments so a player who cannot cover the full amount now pays what they have. The watch takes the rest in goods from your pack and jails you for anything still owed.
- Changed the watch so officers now step in for anyone attacked in front of them, the player included. An assault seen by an officer is now recorded on the spot.
- Changed ground textures so grass, dirt, sand and cobbles now blend into each other along ragged edges instead of meeting on a straight line
- Changed foliage so twice as many plants now grow across the world
- Changed the title screen buttons so they are now larger, red and lettered in yellow
- Changed the world map so its right-hand panel now shows where you are, the time, and a key for every icon on the chart
- Changed Faith so the first point now drains one second after a prayer is raised. Lowering a prayer before then still costs that point.

### Items
- Changed crossbows so they are now raised to the shoulder before firing, from a squarer stance with the cheek on the stock
- Changed drawing and sheathing a weapon with a shield so each hand now takes its turn, and the shield swings around the outside of the body
- Changed armour so plates now draw in at the elbows and knees and slide under each other when the limbs bend

### NPC's
- Changed archers so they now lead moving targets, wait until they are facing their mark, and move to a new spot every couple of shots
- Changed fighters so they now take up a carried bow or crossbow against something flying out of reach, and run for cover if they have none
- Changed townsfolk so they now call out at a noise and then creep close enough to see what made it, rather than hiding or going back to bed
- Changed lake net fishing so the net is now hauled in with its catch after a soak and before dark
- Changed woodcutters and miners so each now claims their own tree, or their own side of a rock

## Technical Changes

### Gameplay
- Changed the engine so the world shader can now read the ground beside each block, which is how ground textures blend

### NPC's
- Changed stuck-route recovery so a fighter now walks back onto open ground instead of being moved there instantly

## Fixed Bugs

### Gameplay
- AE-02901 - Fixed bug where icons on the world map spilled onto its right-hand panel
- AE-02902 - Fixed bug where a bounty could be paid without the coin for it

### Blocks
- AE-02903 - Fixed bug where Ivy and Moss could grow on a single post or pillar and look like they were standing on their own
- AE-02904 - Fixed bug where a fishing net stayed in the DevMap's fishing spot permanently

### NPC's
- AE-02905 - Fixed bug where guards did not defend the player when attacked
- AE-02906 - Fixed bug where archers kept backing away from bats and never fired
- AE-02907 - Fixed bug where fighters stood under flying enemies instead of fleeing
- AE-02908 - Fixed bug where orcs, goblins and other monsters stood stiff instead of reeling when struck or knocked down
- AE-02909 - Fixed bug where townsfolk abandoned looking into a noise at night and went back to bed
- AE-02910 - Fixed bug where two woodcutters or miners stood in the same spot at one tree or rock

### Items
- AE-02911 - Fixed bug where crossbows fired from the hip in third person
- AE-02912 - Fixed bug where a shield passed through the body while being drawn or put away

---

# Patch Notes Version Alpha 0.2.8: A Helping Hand

A few loose ends are now in hand.

Developer: Shadow

## New Features

### Blocks
- Added a DevMap marker so players can now find the Stairs and Ladder practice course

## Changes

### NPC's
- Changed repairs so workers now brace the surface and lift the Hammer before each short downstroke
- Changed mining so miners now use a downward strike and keep facing their work
- Changed food offers so players can now offer Bread when they carry a spare loaf and the recipient can accept it

### Gameplay
- Changed fog so distant scenery now disappears closer to the player, allowing more of it to be culled

## Technical Changes

### NPC's
- Changed route reuse so residents now spend less time checking the same floor connections on repeated journeys

## Fixed Bugs

### Items
- AE-02801 - Fixed bug where Two-Handed Swords and Battleaxes were held with a free support hand
- AE-02802 - Fixed bug where Shields ignored the forearm's orientation and clipped during carrying

### Blocks
- AE-02803 - Fixed bug where ground foliage and wall growth covered the first block of a tree
- AE-02804 - Fixed bug where Ivy and Moss floated away from their supporting wall in wind or around passing bodies

### NPC's
- AE-02805 - Fixed bug where witnesses remained on the HUD after the incident had reached the watch or been settled
- AE-02806 - Fixed bug where an officer described an old case as settled while a new bounty remained unpaid
- AE-02807 - Fixed bug where Morrow promised to take a body's belongings to himself
- AE-02808 - Fixed bug where remembered appeals or case closure could be lost when another account of the incident arrived
- AE-02809 - Fixed bug where separate older crime memories without incident numbers shared one report status
- AE-02810 - Fixed bug where stale conversation choices could be used after walking away or losing sight of the speaker

---

# Patch Notes Version Alpha 0.2.7: Taking the High Road

Townsfolk now have more ways to get where they are going.

Developer: Shadow

## New Features

### Blocks
- Added Stairs that now connect different heights with a stepped model
- Added Ladders that eligible humans and monsters can now climb onto roofs and landings

### NPC's
- Added personal route memories that now retain separate floors and remembered obstructions across saves
- Added route warnings that nearby townsfolk can now pass along in conversation
- Added reachable high-ground choices that ranged combatants now consider before attacking
- Added brief yielding so opposing travellers now give nearer agents a chance at narrow passages

## Changes

### NPC's
- Changed travel so NPCs now discover routes from the world, including edited terrain, stairs, and ladders
- Changed failed journeys so NPCs now pause, reconsider, and ask nearby people for directions
- Changed town ladders so Lawmen, suitable workers, and combatants can now use them while ordinary routines stay on the ground
- Changed repairs so workers now use an interruptible hammering motion
- Changed witness appeals so players now persuade, threaten, or offer Gold through the chatbox

### Gameplay
- Changed the title subtitle to now read “A World Awaits.” in larger, centred text
- Changed level celebrations earned on a fatal tick to now wait until the player has respawned

## Technical Changes

### NPC's
- Changed route checks so nearby construction now refreshes affected areas without discarding distant travel memories
- Changed repeated blocked searches so NPCs now retain temporary avoidance knowledge and try a larger search

## Fixed Bugs

### Gameplay
- AE-02701 - Fixed bug where an officer's full inventory could prevent payment even when its Gold purse had room
- AE-02702 - Fixed bug where a bounty payment reply could close during the officer's confrontation
- AE-02703 - Fixed bug where a level celebration could appear during death instead of after respawning

### NPC's
- AE-02704 - Fixed bug where NPCs repeatedly replaced their own speech with the same dialogue
- AE-02705 - Fixed bug where a climber could reach the top rung without stepping onto the landing
- AE-02707 - Fixed bug where travellers could mistake passable foliage for a wall
- AE-02708 - Fixed bug where a worker could keep moving after stopping to repair something
- AE-02709 - Fixed bug where a Knight without a separate duty location could walk toward the edge of the world

### Blocks
- AE-02706 - Fixed bug where the bottom edge of a hedge swayed away from the ground
