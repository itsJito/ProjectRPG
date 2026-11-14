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
