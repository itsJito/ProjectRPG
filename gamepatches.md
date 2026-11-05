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
