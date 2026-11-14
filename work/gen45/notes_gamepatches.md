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
