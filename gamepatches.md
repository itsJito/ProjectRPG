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
