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
