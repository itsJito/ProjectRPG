RPGGAME ultimate doc

**IMPLEMENTATION GUIDE**
When editing ANYTHING, take reference screenshots and make sure everything scores atleast a 10/10 and be extremely critical. Accept nothing that isn't close.
When animating, make sure to take reference screenshots from the start of animation to the on Threes: Frames 1, 4, 7, 10 etc get screenshotted and analyzed for finetuning, redo this until every animation gets a 10/10 or at minimum 8/10.
0.5–1.5 seconds for most gameplay loops (walk/run/idle), with attacks and one-shots usually in the 0.3–1.2s range. Keep them short and responsive so the player feels in control.

**THINGS TO NOTE**
You are allowed to and encouraged to modify and change the game engine for our needs.
Alongside everything listed in this document, make sure to follow the GDD.md for further instructions on what we want/need from this game, and remove/alter anything that doesn't allign with either this document or GDD.md. This document has higher authority than GDD.md.
Even animals are subjects to the skill system, and their skills also determine their combat level.
Everything should be systemic, dynamic and not scripted.
NPC's can not gain skill levels or combat levels; only the player.

**BUGS TO BE FIXED:**
- Interacting with foliage bugs the foliage out and expands it. 
- Info/Chatbox is too big and overlaps with the hotbar
- Doors at the cemetary/morrows house are bugged
- From a distance, brick textures flicker.
- The player/NPC's can't accurately target/hit small NPC's like cats when directly below them.
- When carrying a carcass, a bug occurs where the particles of said dead body magnetically reattach to the body.
- Guards reinvestigate dead bodies they drop off at the graveyard.
- Bounty hunters shouldn't have omnipotent knowledge of the player; they should always track the player dynamically and focus on the last known area.
- In first person, the player can clip through their own body when looking directly down and crouched.
- Arrows don't always visually show leaving bows/crossbows, they should be real Impulsis objects and arrows need to be real objects, so an individual arrow is shown exactly where it landed and the invidual arrow can be picked up.
- In third person, humans aiming bows should ALL turn sideways; however when they do this they shouldn't clip with themselves. Currently they turn on their side and their arms go through their body.

**THINGS TO BE CHANGED**
- Replace the current levelup SFX with the new one in Asset Queue folder.
- Player Starting Equipment must be changed to:
	Melee: Wooden sword
	Shield: Wooden shield
	Ranged: Shortbow + 25x Wooden arrows
	Other: Stone axe, Stone pickaxe, Tinderbox, Small fishing net
	Pot, Bucket, 1x Bread, 1x Shrimp
	No coins.
- Bow and Arrows / Crossbows should also use the visual arc along with the reticle.
- When picking up ANYTHING, all human models should play a non-restrictive grab animation with their free hand, and if no hand is free still making the motion.

**THINGS TO BE REMOVED:**
- Nothing yet.

**THINGS TO BE ADDED:**
- A 'Bank of Gameworld' bank building in Devtown with one teller. NPC's are allowed to use banks and have accounts. Players who open their account with the bank get their first account (which is twice the size of their inventory space) but can purchase additional room (the size of their inventory) up to four times.
- A 'Dungeon' system. The dungeon system should be set up to allow for different types of dungeons to be created, such as micro-dungeons, macro-dungeons, boss dungeons, and elite dungeons. Macro dungeons are the traditional dungeons found throughout Gameworld. They are subterranean areas filled with hostile monsters, traps, and hazards. Micro dungeons are built exactly like macro dungeons but on a much smaller scale. Boss dungeons are not procedural unlike micro/macro dungeons and are designed around specific boss/monster encounters. Elite dungeons are medium sized dungeons with an extreme threat level. All dungeons are seperate from the map and take place in their own map.

- Monsters
	Monster AI needs to be implemented into NeuralAI alongside Animal and Human AI. Monster AI should also differ in that kills on monsters never count as crimes, and effectively all Human/Animal AI dislikes Monster AI and vice versa. They should be territorial just like animals but to a more dynamic and less predefined extent. Monsters naturally spawn in the wilderness, though not too much. All monsters must have different walk/run/attack animations, and all must be properly rigged to Sydney to allow for ragdolls and dismemberment/gore;

	Goblin
			Level Range: 1-50
			Design: Small wiry green humanoid, oversized head & ears, crouched stance.
			Model: 0.6×1.3×0.6 blocks · Hitbox: 0.5×1.2×0.5
			Attack Types: Melee (spear / dagger) or Thrown weapon (spear / rock / knife)
			Drops;
				Always: Bones (or nothing)
				Common: Coins, bronze/iron weapons & armour scraps, low-level runes/herismans, food scraps
				Uncommon: Goblin mail, spears, shields, herbs, crafting materials
				Rare: Champion’s spear or unique goblin banner/relic
				Very Rare: Goblin high-shaman’s staff or legendary tribal artifact

	Orc
			Level Range: 20-120
			Design: Muscular green-gray humanoid, heavy brow, tusks, broad shoulders.
			Model: 1×2.1×1 blocks · Hitbox: 0.8×2×0.8
			Attack Types: Melee (axe / sword) or Thrown weapon or war shout (brief stun / debuff)
			Drops;
				Always: Bones / orcish remains
				Common: Crude iron/steel weapons & armour, coins, low-mid reagents
				Uncommon: Orcish plate pieces, axes, war paint, mid-tier gear
				Rare: Chieftain’s axe or bloodied standard
				Very Rare: Legendary orc warlord’s armour set piece or clan relic

	Troll
			Level Range: 60-130
			Design: Hulking bipedal brute, oversized arms, rocky/craggy skin, hunched posture, club.
			Model: 2.5×3.5×2 blocks · Hitbox: 1.8×3×1.6
			Attack Types: Melee smash (club / fists) or Thrown rock / boulder
			Drops;
				Always: Big bones / troll fat
				Common: Crude weapons, hides, herbs, low-mid runes or reagents
				Uncommon: Granite / stone shields or clubs, mid-tier armour pieces
				Rare: Rune-grade or enchanted weapons, unique troll totems
				Very Rare: Regenerative troll heart or legendary club

	Imp
			Level range: 1-30
			Design: Tiny red-skinned humanoid, horns, pointed tail, oversized head.
			Model: 0.4×0.9×0.4 blocks · Hitbox: 0.35×0.8×0.35 [Can Fly]
			Attack Types: Melee scratch / claw or Ranged firebolt / magic bolt
			Drops;
				Always: Ashes (or nothing)
				Common: Coins, low-level runes, beads, trinkets, food
				Uncommon: Imp-specific charms, small magical reagents, minor gems
				Rare: Magical beads set or imp familiar essence
				Very Rare: Bound imp contract or legendary trickster’s artifact

	Bat
			Level Range: 1-40
			Design: Winged flying mammal, large leathery wings, small furred body.
			Model: 1.5×0.6×1.5 blocks (wings spread) · Hitbox: 0.8×0.5×0.8 [Can Fly]
			Attack Types: Melee bite or Diving screech / wing buffet
			Drops;
				Always: Bat wings / meat (or minimal remains)
				Common: Small fangs, guano / reagents
				Uncommon: Wing membranes, minor gold
				Rare: Giant bat eye or sonic essence
				Very Rare: Vampiric bat essence or legendary wing cloak

	Ghost
			Level Range: 15-80
			Design: Translucent floating humanoid, tattered robes, no legs, faint glow.
			Model: 0.8×2.2×0.8 blocks · Hitbox: 0.7×2×0.7 [Can Float/Hover]
			Attack Types: Melee touch (cold / draining) or Ranged spectral bolt / life drain
			Drops;
				Always: Ectoplasm / spirit dust (or nothing tangible)
				Common: Spectral residue, minor soul essences
				Uncommon: Ghostly cloth or ethereal reagents
				Rare: Spirit-bound trinkets or spectral weapons
				Very Rare: Bound ghost essence or legendary haunt artifact

	Zombie
			Level Range: 10-120
			Design: Rotting bipedal humanoid, torn flesh, shambling posture.
			Model: 0.8×2×0.8 blocks · Hitbox: 0.6×1.95×0.6
			Attack Types: Melee slam / claw or Biting Lunge
			Drops;
				Always: Bones or rotting remains
				Common: Tattered cloth / leather, low-mid weapons, soul dust
				Uncommon: Ancient Nord / forgotten armour pieces, herbs, runes
				Rare: Cursed weapons, undead champion relics
				Very Rare: Phylactery fragment or legendary undead commander’s gear

	Skeleton
			Level Range: 15-140
			Design: Animated humanoid bones, exposed ribcage & skull, loose joints.
			Model: 0.7×2×0.7 blocks · Hitbox: 0.6×1.95×0.6
			Attack Types: Melee (sword / axe / bash) or Ranged (bow or thrown bone)
			Drops;
				Always: Bones
				Common: Rusty / iron weapons and armour, low-mid runes or soul dust
				Uncommon: Mid-tier weapons, herbs, coins
				Rare: Ancient skeleton champion scroll / relic, enchanted bone weapons
				Very Rare: Full ancient skeleton set piece or soul-bound artifact

	Werewolf
			Level Range: 20-100
			Design: Bipedal wolf-human hybrid, digitigrade legs, claws, furred, hunched.
			Model: 1.1×2.3×1.1 blocks · Hitbox: 0.9×2.1×0.9
			Attack Types: Melee claw / bite combo or Pounce / howling charge
			Drops;
				Always: Wolf bones / pelt, raw meat
				Common: Claws, fangs, bloodied cloth
				Uncommon: Silver-sensitive reagents, quality pelts
				Rare: Werewolf blood / curse essence, alpha pelt
				Very Rare: Cured werewolf heart or legendary lunar artifact

	Dragon (Ice, Fire, Earth, Electric and Water)
			Level Range: 70-350
			Design: Large four-legged winged reptile, heavy scales, long neck & tail, horned head.
			Model: 5×4×8 blocks · Hitbox: 4×3.5×6 [Can Fly over and around their territory aswell as walk]
			Attack Types: Melee (bite / claw / tail swipe) or Breath (fire, frost, or magic projectile, depends on dragon type)
			Drops;
				Always: Dragon bones / scales, dragonhide / leather
				Common: Gold, elemental essences (fire/frost), high-tier metal bars
				Uncommon: Dragon equipment pieces (helms, legs, weapons), rare gems
				Rare: Draconic visage / dragon soul essence, unique dragon weapons or armour
				Very Rare: Legendary dragon artifact or mount-related item

	Giant Spider
			Level Range: 1-70
			Design: Eight-legged arachnid, bulbous abdomen, long jointed legs, fangs.
			Model: 3×1.5×3.5 blocks · Hitbox: 2.5×1.2×2.8 [Can crawl on walls]
			Attack Types: Melee bite (venomous) or Ranged spit (poison / web)
			Drops;
				Always: Spider silk / carapace, venom / poison gland
				Common: Small amounts of gold, basic reagents
				Uncommon: Poison-coated weapons or arrows, webbing materials
				Rare: Giant spider eye or unique venom flask
				Very Rare: Queen’s venom or legendary silk armour piece

- New Items:

	Ruby:
		Design/Model: Deep crimson teardrop, soft pink star glowing inside, faint embers floating off.
		Inventory Size: 1x1
		Base Price: 1,111 / 970 (Uncut / Cut)
	Emerald:
		Design/Model: Chunky rectangular vivid green block, shiny top, rough mossy sides.
		Inventory Size: 1x1
		Base Price: 624 / 547 (Uncut / Cut)
	Sapphire:
		Design/Model: Oval, dark royal blue bottom fading to pale sky blue top, watery shine.
		Inventory Size: 1x1
		Base Price: 360 / 346 (Uncut / Cut)
	Diamond:
		Design/Model: Chunky round white gem with icy blue core, bright sparkle in the center.
		Inventory Size: 1x1
		Base Price: 3,019 / 6,314 (Uncut / Cut)
	Amethyst:
		Design/Model: Jagged purple crystal cluster, dark veins pulsing through.
		Inventory Size: 1x1 (cluster 1x2)
		Base Price: 7,819
	Onyx:
		Design/Model: Jet-black sphere with rough dark base, faint red glow inside when turned to light.
		Inventory Size: 1x1
		Base Price: 53,037 / 53,550 (Uncut / Cut)
	Dragonstone:
		Design/Model: Heart-shaped purple-red gem, soft flame flickering inside.
		Inventory Size: 1x1
		Base Price: 7,560 / 7,168 (Uncut / Cut)
	Dragonhide:
		Design/Model: Thick folded block of big black scales edged in red, dark greenish backing.
		Inventory Size: 2x2
		Base Price: 2,753
	Dragon bones:
		Design/Model: Big ivory bone, cracked with golden cracks and charred ends.
		Inventory Size: 1x2
		Base Price: 1,553
	Dragon Egg:
		Design/Model: Spotted egg with glowing orange cracks and soft smoke.
		Inventory Size: 2x2
		Base Price: 22,493
	Rare Fossil:
		Design/Model: Stone slab with a tiny raptor skeleton preserved in amber and leaf marks around it.
		Inventory Size: 2x3
		Base Price: 750

**THINGS TO BE EXPANDED:**
- Human AI choice trees need to be polished. They don't always follow their routine or act convincingly.
- XP takes too long to level up; it shouldn't be too fast either. Try XP rate 2 or 3? But each action on different skills also costs differently. Model after RS3+WOW.
- NPC Dialogue, for all NPC subtypes, need to be expanded to have more variety and to be more dynamic/contextual as well.
