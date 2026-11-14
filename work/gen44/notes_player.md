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

