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
