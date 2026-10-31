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
