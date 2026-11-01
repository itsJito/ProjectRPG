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
