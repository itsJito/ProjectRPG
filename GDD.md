# RPGGAME
## Game Design Document

**Working world name:** GAMEWORLD  
**Genre:** Single-player, classless, open-world fantasy action RPG  
**Presentation:** Fully explorable 3D voxel environments; first- and third-person play  
**Engine:** Astratto Engine; adapted *Absentialis* character models  
**Document edition:** Concept-focused revision of design baseline 3.21  
**Status:** Working game design. **Confirmed** means an intended design decision; **To decide** identifies unapproved details. Gameplay descriptions take priority over illustrative examples.

> **The pitch:** Build a life as a self-directed adventurer in a dangerous fantasy world that does not wait for you. Master eighteen skills, fight with physical real-time combat, explore handcrafted stories, practice one of three traditions of magic, own an estate, and develop a reputation among people who have lives and interests of their own.

### At a glance

| Question | Game-design answer |
|---|---|
| **Who am I?** | A customizable human adventurer. There are no character classes or permanently locked builds. |
| **What do I do?** | Explore, fight, train skills, gather, craft, trade, converse, quest, steal, hunt, build, collect and influence the world. |
| **What makes progress matter?** | Every skill grows to level 100; equipment, property, knowledge, relationships and rare accomplishments are earned. |
| **What makes the world feel alive?** | People work, buy, travel, remember, witness crimes and react to changing local circumstances. Animals and monsters have habitats and needs. |
| **How does magic work?** | Exactly **three** books: **Sun**, **Moon** and **Fog**. They share the Magic skill and Mana, but support very different kinds of play. |
| **What happens when I make a choice?** | Quest opportunities, Honor, legal trouble, economic conditions, NPC relationships and even corpse recovery can change. |
| **What is the initial scope?** | One single-player game, at least three distinct kingdoms in the first playable world, with two additional kingdoms planned later. |

### Contents

1. [The game and its player experience](#1-the-game-and-its-player-experience)
2. [Creating and developing an adventurer](#2-creating-and-developing-an-adventurer)
3. [Combat, weapons and equipment](#3-combat-weapons-and-equipment)
4. [The three books of magic and Faith](#4-the-three-books-of-magic-and-faith)
5. [Exploration, quests and the world](#5-exploration-quests-and-the-world)
6. [People, conversation and living society](#6-people-conversation-and-living-society)
7. [Economy, crime, Honor and politics](#7-economy-crime-honor-and-politics)
8. [Wildlife, pets and monsters](#8-wildlife-pets-and-monsters)
9. [Estates, Construction and collecting](#9-estates-construction-and-collecting)
10. [Death and recovery](#10-death-and-recovery)
11. [Interface, atmosphere and presentation](#11-interface-atmosphere-and-presentation)
12. [Playable content and development priorities](#12-playable-content-and-development-priorities)
13. [Design questions still open](#13-design-questions-still-open)
14. [Reference catalogs](#14-reference-catalogs)

---

# 1. The game and its player experience

## 1.1 The fantasy

RPGGAME is about arriving in a world that feels much bigger than the hero. The player begins inexperienced, with modest clothing, little physical power and plenty of places that are genuinely unsafe. Skill levels do not determine which stories may exist; they determine what the player has learned how to do. A lone traveler may work as a miner, become a swordsman, fall into criminal life, master Moon magic, purchase an estate or pursue obscure clues, often within the same save.

The world must remain **handcrafted**. Important people, major quests, civilizations, conflicts and dramatic scenes are written deliberately. Simulation surrounds those stories with an ordinary life that can interfere with them. A merchant cannot sell what they do not have; a farmer may lose livestock to predators; a crucial quest-giver can die; a road becomes dangerous because of nearby creatures rather than because the player reached a particular level.

This is not a procedural story generator, a kingdom-destruction sandbox or a multiplayer MMO. Its inspirations are the satisfying long-term skill and gear goals of traditional RPGs, the moment-to-moment freedom of action adventures and the continuity of a believable simulated society.

## 1.2 The six design pillars

| Pillar | What the player should experience |
|---|---|
| **Progress is earned** | New levels, rare equipment, houses, skills and abilities mark accomplishments instead of appearing automatically with the story. |
| **Every path is viable** | Fighting, working, crafting, trade, exploration, crime and relationships offer meaningful activities without a forced class. |
| **Combat is physical** | Distance, facing, movement, timing, weapon reach and deliberate defense decide fights. |
| **The world has its own pace** | NPCs and creatures act when not observed. They do not exist solely to supply the player with tasks. |
| **Stories are authored, consequences are systemic** | Memorable narratives are written by designers; surrounding people, goods, knowledge and opportunities respond to actual events. |
| **Comfort is earned, not a cheat code** | Homes, travel, equipment, money and reputation make life easier but do not erase challenge, skill requirements or consequences. |

## 1.3 The core adventure loop

**Discover something → decide whether it is worth pursuing → prepare → act in the world → survive or face consequences → improve → discover what is now possible.**

A small example: the player hears that a remote mine has a valuable ore deposit. The journey requires food and weapons. A wolf pack on the road may be avoided or fought; the mine might already be occupied; another worker may have claimed the richest seam. The player returns with ore, earns Mining experience, negotiates its sale, repairs equipment and considers whether the profit justifies another expedition. This is one adventure assembled from existing activities rather than a mandatory scripted mission.

## 1.4 The shape of a playthrough

- **Early game:** Vulnerability; local work, small errands, starter combat, simple crafting and learning which roads are safe.
- **Middle game:** Stronger equipment, specialized training, expanding questlines, richer commerce, owned property and a wider world.
- **Late game:** Demanding bosses, raids, complex quests, advanced Sun/Moon/Fog magic, prestigious equipment, rare collections and political or commercial influence.
- **Beyond the main story:** The character and world persist. There are still professions, changing markets, discoveries, collections and relationships, without a promise of unlimited procedurally generated stories.

The world has **no enemy level scaling**. Returning to an old threat after fifty levels should feel like a genuine triumph. Entering an extremely dangerous region too early should be possible and regrettable in a fair, readable way.

---

# 2. Creating and developing an adventurer

## 2.1 Character creation

The creator determines the adventurer's **appearance**, not their profession, class, armor or starting power. All initial appearance choices are available immediately. Starting clothing is decorative and gives no combat benefits. Functional armor and weapons are found, purchased, produced or earned in play.

| Appearance choice | Confirmed offering |
|---|---|
| Sex preset | Male or Female |
| Body silhouette | Masculine, Heavy or Skinny, independently selectable |
| Skin | 12 realistic tones |
| Hair | 20 male styles and 20 female styles |
| Facial hair | 20 options, including clean-shaven |
| Hair color | 10 natural colors for head and facial hair |
| Tops | 20 cosmetic styles |
| Leg garments | 20 cosmetic styles |
| Shoes | 20 cosmetic styles |
| Clothing colors | 24 distinct selectable colors for each of the three garment categories |

Players can combine tops, bottoms and shoes rather than selecting fixed outfits. The current creator demonstration features **Henric**, a masculine man with the first skin preset, a bald head and dark-brown goatee/moustache, olive open-collar shirt, brown trousers and black rounded work shoes. The existing prototype opening presents the Astratto Engine logo, then a dark dungeon-themed title/character preview. This is presentation for the prototype, not an unchangeable final title screen.

A player's chosen appearance should remain recognizable in either camera, during movement and combat, after saving, and underneath compatible functional gear. No appearance option silently changes statistics.

## 2.2 The eighteen skills

Every skill runs from **level 1 to level 100**. The player begins with **Health 10** (10 maximum and current HP) and **all seventeen other skills at level 1**. No race or class closes off any skill. At level 100, the player can purchase the corresponding prestige **skillcape**.

| Group | Skill | What it lets the player become better at |
|---|---|---|
| Combat | **Attack** | Chaining, timing and handling melee strikes more quickly and reliably |
| Combat | **Strength** | Dealing heavier melee damage, striking with force and using stronger swords |
| Combat | **Defense** | Actively blocking, parrying, dodging and using stronger armor |
| Combat | **Health** | Surviving injury and developing endurance and maximum HP |
| Combat | **Archery** | Effective use of crossbows and thrown projectiles |
| Combat | **Magic** | Learning and casting Sun, Moon and Fog spells with Mana |
| Combat | **Faith** | Maintaining prayers, gaining spiritual protections and managing Faith points |
| Gathering | **Woodcutting** | Obtaining timber, splitting logs and clearing useful vegetation |
| Gathering | **Mining** | Gathering stone, clay, ore, coal and gems |
| Gathering | **Fishing** | Catching fish and other approved aquatic resources with appropriate gear |
| Gathering | **Harvesting** | Planting, maintaining and collecting useful crops, herbs and plants |
| Production | **Cooking** | Turning ingredients into practical food and meals |
| Production | **Crafting** | Producing goods, accessories and other useful objects |
| Production | **Smithing** | Smelting, forging and repairing metal equipment |
| Production | **Construction** | Building and maintaining useful property and furnishings |
| Utility | **Agility** | Traversing difficult environments and moving efficiently |
| Utility | **Firemaking** | Establishing, fueling and managing useful fires |
| Utility | **Thieving** | Pickpocketing, lockpicking and taking property discreetly |

**How players learn:** By actually doing those activities. Fighting trains fighting skills; cooking a meal develops Cooking; cutting a real tree earns Woodcutting; a spell earns Magic experience when it actually affects a target or meaningful situation. Talking to a menu or repeating a meaningless animation does not produce progress. Difficulty, effort, risk and value of the real result influence rewards.

The five rough degrees of training are **ordinary**, **practiced**, **demanding**, **advanced** and **exceptional**. These describe the difficulty of an accomplishment, not five separate minigames. Easier actions should remain useful instead of instantly becoming obsolete.

### Progression scale

Every skill reaches level 100 at **6,000,000 cumulative XP**; reaching 100 in all eighteen skills requires **108,000,000 total XP**. Growth becomes more demanding after level 60. These are the established reference milestones:

| Level | XP | Level | XP |
|---:|---:|---:|---:|
| 1 | 0 | 60 | 935,503 |
| 10 | 43,446 | 65 | 1,167,164 |
| 20 | 113,599 | 70 | 1,462,828 |
| 30 | 217,444 | 75 | 1,840,178 |
| 40 | 371,159 | 80 | 2,321,784 |
| 50 | 598,694 | 85 | 2,936,448 |
| 90 | 3,720,933 | 95 | 4,722,157 |
| 100 | 6,000,000 | | |

A level-up immediately updates the skill and triggers a recognizable jingle, small fireworks and a concise announcement identifying newly available actions. Quest notices and Honor announcements should not obscure each other.

## 2.3 Training should look like real life in the world

| Skill | Ordinary training | More demanding or notable training |
|---|---|---|
| Attack | Land real melee hits and connect follow-up attacks | Punish an enemy's missed attack, counter after a defense or land a weapon special |
| Strength | Move meaningful obstacles and inflict real melee damage | Land heavy strikes, stagger enemies or connect high-impact specials |
| Defense | Survive actual hostile hits; guard attacks | Dodge committed blows, block with shields and time successful parries |
| Health | Work, travel and recover after legitimate strain | Survive injured or depleted through a dangerous encounter |
| Archery | Connect thrown weapons and crossbow shots | Strike moving targets and real enemy weak points |
| Magic | Make practical Sun, Moon or Fog spells accomplish something | Defeat dangerous foes, change perceptions or solve meaningful obstacles with spells |
| Faith | Bury eligible remains and benefit from prayers | Use meaningful protection in battle or perform rare supported purification |
| Woodcutting | Clear vegetation, split timber, cut small trees | Fell difficult trees or manage challenging controlled fells |
| Mining | Gather clay, quarry stone and mine coal | Extract metal from difficult veins or valuable gems |
| Fishing | Catch through nets, rods and harpoons | Complete more demanding lobster-pot or big-net catches |
| Harvesting | Prepare soil, sow and water plants that need it | Prune effectively and gather mature valuable harvests |
| Cooking | Cook basic food and meat/fish recipes | Prepare combined meals, involved recipes and high-level dishes |
| Crafting | Work leather, fabric, clay or glass | Cut gems and assemble more elaborate goods or jewelry |
| Smithing | Smelt bars and repair damaged gear | Forge quality tools, armor, weapons and appropriate improvements |
| Construction | Install furnishings and repair fixtures | Build rooms, workstations and significant multi-stage projects |
| Agility | Vault, balance, jump and climb genuine terrain | Navigate demanding climbs or escape pursuit through physical traversal |
| Firemaking | Light and maintain legitimate fires | Prepare work fires and complete charcoal production |
| Thieving | Pick pockets and steal from containers | Open tougher locks or make a successful guarded theft |

**Rules that matter to players:** Melee contact can train Attack and Strength together. Legitimate hostile damage always contributes to Defense training; active defenses also count. Different skills may benefit from a single action when both were genuinely exercised. Repeating the same closed door, fake injury, full water trough, decorative object or empty enemy interaction never creates infinite XP. A training dummy is good for practice, not an infinite leveling machine.

## 2.4 Tools and workstations

The world offers axes and hatchets for timber; pickaxes for ore; hammers, anvils and bars for Smithing; hammer, saw, planks and nails for Construction; chisels, molds, needle/thread, shears and glassblowing equipment for Crafting; farming tools and seeds for Harvesting; nets, rods, harpoons and traps for Fishing; a tinderbox and logs for Firemaking; and lockpicks for secured theft. Cooking happens at an actual usable fire or stove. Agility and pickpocketing do not require mandatory special equipment.

Some equipment has two believable purposes: **axes, hatchets, pickaxes and hammers** can help with work and serve as physical melee weapons. The tier required for a tool depends on the skill being practiced; combat use depends on its applicable combat requirement. Their work and combat purposes do not create two separate items.

No carry-weight statistic is planned. Inventory is limited by **slots**, and an equipped backpack can increase the number of ordinary inventory slots. Exact capacities remain to be chosen.

---

# 3. Combat, weapons and equipment

## 3.1 Combat should feel immediate

Combat is a real-time, fully 3D physical experience in either first or third person. The player chooses aim, distance, movement, attacks and defensive timing. A weapon must actually touch the target to connect. A near miss is a miss. There is **no invisible accuracy roll after contact**, no random passive dodge stat and no mandatory hard lock-on.

- **Attack** improves how efficiently the character chains and handles melee attacks; it does not secretly make a missed swing hit.
- **Strength** determines how hard connected melee attacks can hit and how much force they create.
- **Defense** improves the player's deliberate blocks, guards, dodges and parries. Armor is a separate protective benefit.
- **Health** determines HP and endurance. **Stamina** governs sustained physical effort.
- **Archery** uses real projectiles and readable range/flight behavior.
- **Magic and Faith** provide separate combat options with distinct resources, described in Chapter 4.

Weapons should differ in rhythm, range, stamina demand, reach, impact and special attacks. A dagger rewards rapid close engagement, while a warhammer or heavy sword asks the player to commit and anticipate. Defense must be worth performing. High-quality armor protects against injury, but skilled movement and timing still matter.

**Damage character:** Armor reduces the possible damage of an unblocked hit; a landed, unprotected strike then produces variable HP damage within its allowed range. Impact and stagger are separate physical effects. Trained defensive skills influence armor efficiency and active defense without creating a secret chance to evade a real strike. Enemy attacks should be visibly anticipated when fair to do so.

## 3.2 Combat level

The game displays an overall **combat level**, derived from the seven combat skills. It begins at **3** and can ultimately reach **127**. It is a useful shorthand for relative fighting development, **not** a separate skill, automatic accuracy bonus or equipment requirement.

For reference, the exact established calculation is:

```text
Base   = (Defense + Health + floor(Faith / 2)) / 4
Melee  = 13/40 × (Attack + Strength)
Ranged = 13/40 × floor(Archery × 1.5)
Magic  = 13/40 × floor(Magic × 1.5)
Combat Level = floor(Base + highest of Melee, Ranged or Magic)
```

## 3.3 The equipment ladder

There are **thirteen equipment tiers**. Each tier has the same level requirement wherever that tier is used, although the **relevant skill depends on the item**. For instance, a Godly sword requires Strength 90, while Godly armor requires Defense 90.

| Tier | Required skill level | Established Sword Strength bonus |
|---|---:|---:|
| Stone | 1 | +1 |
| Bronze | 5 | +2 |
| Chainmail | 10 | +3 |
| Iron | 20 | +6 |
| Black | 30 | +9 |
| White | 30 | +9 |
| Steel | 40 | +12 |
| Granite | 50 | +12.5 |
| Cobalt | 60 | +13 |
| Dragon | 70 | +13.5 |
| Molten | 80 | +14 |
| Godly | 90 | +15 |
| Malevolent | 100 | +16 |

Black and White are equal-level, distinct tiers; Steel is stronger than either. These names represent item progression rather than a guarantee of literal material composition. The bonuses in the table apply to the **Sword family**, not automatically to other weapon types.

### What can be equipped?

The established categories are **Head, Torso, Legs, Boots, Cape, Ring(s), Necklace, Gloves, Melee (primary weapon), Left Hand, Arrows (ammunition), and Backpack**. A one-handed primary weapon can be paired with a shield or compatible left-hand weapon. A two-handed weapon occupies both hands. Ammunition has a dedicated slot outside ordinary inventory. Equipment is swapped manually from inventory; there is no assumed instant loadout system.

The ordinary melee weapon families are **Dagger, Sword, Longsword, Scimitar, Axe, Pickaxe, Mace, Warhammer, Battleaxe, Spear, Claws, Halberd and 2h Sword**. Ranged weapon families are **Crossbow, Dart, Knife, Tomahawk and Javelin**. Supported ammunition includes Arrows and Bolts; **Brutal Arrows** exist only from **Stone through Steel**. No general bow family is currently approved. The **Fire staff** is a specific magical focus, not permission to create a new general weapon family.

Sword and Longsword are one-handed, including valid off-hand use; 2h Sword uses both hands. Exact hand restrictions for other families are still being designed. Axes, hatchets, pickaxes and work hammers can also be used for their professions.

### Gear that deserves to be chased

Players find or earn items through quests, skilled production, shops, exploration, dungeon chests, bosses, raids and **Treasure Trails**. Strong equipment retains value, and rare weapon specials or cosmetic rewards can become long-term goals. Skillcapes celebrate reaching 100. Holiday-style collectibles such as partyhats and Halloween masks may appear in rare boss or clue rewards; these are scarce prestige items, not guaranteed seasonal handouts.

Smithing and Crafting can improve selected items within their existing tier. Some equipment may wear down and need repair; not every item must degrade. The game never requires an all-purpose armor-weight class taxonomy, and high tiers cannot bypass their skill requirements simply because the player is rich.

---

# 4. The three books of magic and Faith

## 4.1 Three traditions. One Magic skill.

**There are exactly three magic books: Sun, Moon and Fog.** They are the game's only primary magical traditions and the only spellbook categories visible to the player. They are not three extra skills: the single **Magic** skill governs magical progression, and every spell consumes **Mana**.

| Book | Magical identity | Player fantasy |
|---|---|---|
| **Sun** | Elemental power: **Air, Earth, Water, Fire and Electricity** | Direct, spectacular combat magic that progresses from precise projectiles to larger and more forceful attacks |
| **Moon** | Dark, subtle, spiritual and reality-bending magic | Speak to animals, deceive senses, manipulate objects, alter appearances, step across obstacles and influence situations |
| **Fog** | Necromancy, spectral magic and severe cold | Command temporary dead allies, manifest sinister attacks and wield the **Ice Rush → Ice Burst → Ice Blitz → Ice Barrage** progression |

These three traditions can also inform the world's future religious imagery, institutions and competing beliefs. **Faith remains a separate gameplay skill**, not a fourth spellbook. The current GDD does **not** invent a detailed theology, new gods, religious history or named institutions beyond what is confirmed; those are authored-world decisions still to be made.

The player has **one active book at a time**. Once its switching facility has been obtained, the estate's **Occult Altar** allows the player to select Sun, Moon or Fog. Switching changes the available repertoire, but does not erase anything previously learned or grant skills not yet unlocked. Other places where changing books is possible are **To decide**.

**Important content change:** The old requirement that every book have eleven spells has been retired. **Sun alone has twenty-five named elemental combinations**, so imposing equal eleven-slot books would contradict the design. Each book will have a curated roster determined by its identity and the actual spells authored, rather than a forced shared slot count.

## 4.2 Sun: the elemental book

Sun is the baseline, straightforward combat school. It is built around **five elements** and **five attack forms**. They combine to make **25 primary elemental spells**.

| Form | Air | Earth | Water | Fire | Electricity |
|---|---|---|---|---|---|
| **Strike** | Air Strike | Earth Strike | Water Strike | Fire Strike | Electricity Strike |
| **Bolt** | Air Bolt | Earth Bolt | Water Bolt | Fire Bolt | Electricity Bolt |
| **Blast** | Air Blast | Earth Blast | Water Blast | Fire Blast | Electricity Blast |
| **Wave** | Air Wave | Earth Wave | Water Wave | Fire Wave | Electricity Wave |
| **Surge** | Air Surge | Earth Surge | Water Surge | Fire Surge | Electricity Surge |

These names define the **five-by-five Sun roster**, not separate crafting elements or new skills. The five forms should be recognizable increases in force and magical sophistication:

- **Strikes** are basic elemental attacks that teach direct spellcasting.
- **Bolts** are more committed elemental projectiles.
- **Blasts** deliver stronger, more dramatic impact.
- **Waves** deliver high-power elemental attacks with more impressive presentation.
- **Surges** represent the strongest conventional spells in each elemental path.

Those descriptions set fantasy and progression, **not** mandatory areas of effect. A *Wave* is not automatically a radial explosion, for example. The caster aims and actually hits a target or affects a legitimate authored area. The particular trajectory, area, speed, interrupt power and Mana cost of each spell are still subject to balancing.

**Additional previously named Sun effects:** **Spark**, **Voltchain** and **Forcewave** remain Sun-aligned special/utility effects rather than creating a fourth school. Spark produces a small electrical effect, Voltchain chains electricity to eligible nearby wet or conductive targets, and Forcewave pushes or interrupts viable targets. Whether Spark is incorporated into the introductory Electricity path or remains an extra entry is **To decide**. They should not create redundant copies of the twenty-five core spells without a deliberate content decision.

**Fire Blast's established exception:** The named **Fire Blast** is the Sun Fire Blast in the table, **not a second spell with the same name**. Its established staff-assisted version requires the designated **Fire staff** and consumes both Mana and real staff charges. This is a special casting requirement that may be revisited during balancing; the ordinary name and its place in Sun are fixed. Do not inadvertently create a charge-free duplicate Fire Blast.

### Elemental strengths and enemy weaknesses

Sun spells can reward players who understand an enemy rather than merely selecting the highest-tier attack. Eligible elemental spells belong to their element's progression. As stronger spells become available within an element, weaker attacks should remain useful, with their maximum possible damage drawing on the highest learned tier where appropriate.

An enemy with an **explicitly authored weakness** to an eligible element takes **twice the eligible elemental damage**, and the established magical accuracy/reliability rating is also doubled. This helps a spell that is capable of reaching its target; it cannot turn a projectile that completely missed into a hit. Enemy resistances, element interactions and exact damage formulas remain subject to design tuning. An illustrative weakness is not permission to introduce a new monster.

## 4.3 Moon: shadows, spirits, illusions and influence

Moon is for players who want to **change what a situation means**, rather than destroy everything in front of them. Its magic concerns concealment, spiritual travel, transformation, manipulation, deception, support and selected forms of influence.

| Named Moon spell | Intended fantasy and effect |
|---|---|
| **Speak With Animals** | Establish a magical means of communication with eligible animals; it must not make them omniscient or automatically obedient. |
| **Minor Illusion** | Create a small deception that can draw attention, misdirect or alter what a real observer believes they perceive. |
| **Spiritual Step** | Move spiritually/instantaneously to a legitimate reachable destination, respecting obstacles and safe placement. |
| **Darkness** | Create an area of magical obscurity that interacts with light, vision and stealth. |
| **Telekinetic Grab** | Manipulate or retrieve a valid object from a distance without inventing or duplicating it. |
| **Gaseous Form** | Briefly alter the caster's form to support believable movement and obstacle traversal. |
| **Enhance Ability** | Improve a supported next action or ability under a genuine, limited effect. |
| **Vitalmend** | Heal genuine missing HP or aid an eligible ally, including the previously established bleeding-clearing effect. |
| **Friends** | Temporarily influence a plausible social interaction; it never guarantees universal friendship. |
| **Charm Person** | Attempt to sway a suitable person within an authored scope; does not override all agency or make everyone know the result. |
| **Disguise Self** | Alter perceived appearance, with limits based on observation, prior memory, evidence and circumstances. |
| **Morphbeast** | Temporarily transform the actor into a harmless animal-like form without losing their identity, possessions or underlying capabilities. |
| **Chronoslow** | Temporarily slow the movement or action pace of affected eligible entities. |

These are **named design concepts**. Their precise costs, duration, cast ranges, mastery levels and effectiveness are not yet final. Spellcasting is never a substitute for the NPC perception and memory systems. A guard who does not see through a disguise has still seen *someone*; a charmed shopkeeper does not suddenly gain limitless gold; animal speech does not grant global knowledge.

Moon magic should feel eerie and inventive: muted light, softened sound, silver or shadow-tinted visual language, subtle gestures and the satisfaction of solving a problem without another fireball. Those are art-direction targets, not a new gameplay resource.

## 4.4 Fog: death, spirits and ice

Fog is the darkest of the three traditions, concentrating on **necromancy and spectral assault**, with a complete escalating path of **Ice** combat magic. Its tone is cold, grave-bound and unsettling. Unlike Sun's broad element combinations, Fog is a specialized offensive and summoning discipline.

### The four Ice spells

| Spell | Progression role | Intended experience |
|---|---|---|
| **Ice Rush** | Entry Ice attack | A fast, direct cold attack that introduces freezing/chilling effects where applicable |
| **Ice Burst** | Developing Ice attack | A more forceful icy strike with an authored burst effect |
| **Ice Blitz** | Advanced Ice attack | A dangerous, concentrated attack that can impose serious cold control |
| **Ice Barrage** | Highest Ice attack | Fog's most formidable Ice spell, with a powerful battlefield presence |

The **Rush → Burst → Blitz → Barrage** sequence is explicitly established. Exact areas, freeze durations, target caps and required Magic levels must be balanced later rather than assumed from a different game. The previously named **Frost Lance** can remain a separate Fog specialty or be retired in favor of this sequence; until a content decision is made, it is **not counted as a required fifth Ice progression step**.

### Named necromantic and spectral effects

| Named Fog spell | Intended fantasy and effect |
|---|---|
| **Necrocall** | Briefly summon or command up to **two temporary unarmed undead allies** per caster. They have real durations and identities, do not replicate corpses or inventory, and do not grant passive XP. |
| **Spelldaggers** | Manifest **three gently steering spectral projectiles**. They must genuinely connect with a visible enemy to deal harm. |
| **Frost Lance** | Previously proposed focused chilling/freezing projectile. **Optional pending reconciliation** with the four required Ice spells. |

Future necromancy content may explore ghosts, the dead, grave-sites and temporary servitors only through individually designed spells. Existing skeletons, zombies and ghosts in the world are **creatures in their own right**; they are not automatically player-owned allies or free spell effects. Fog can be fearsome without making other characters, economies or quests meaningless.

## 4.5 Learning, casting and using Mana

All Sun, Moon and Fog spells have **individual Magic-level unlocks** and individually tuned Mana costs. A character does not learn an entire book merely by selecting it. Exact unlock requirements are **To decide**; the previous eleven-step level ladder no longer applies.

**Mana capacity equals Magic level:** a level-1 mage has a maximum of 1 Mana, and a level-100 mage has 100. Mana regenerates naturally at **one point per 60 seconds of active game time**, up to the maximum. Every real cast costs Mana, even when it is a movement, illusion, healing or social spell. Spells must have an appropriate target, placement and effect. The game should not let players spam a failed illusion or heal an uninjured character for easy XP. Rest/sleep/time-skipping rules for Mana recovery remain **To decide**.

NPC mages also need appropriate magical knowledge, resources, reach and opportunities. A mage does not conjure an effect merely because the player is close enough to be targeted.

## 4.6 Faith and prayers are separate from the spellbooks

**Faith** is its own skill, with a separate resource pool. Unlike one-off Mana casts, prayers are **sustained toggles** that continue helping while the player maintains them.

- **Maximum Faith points equal Faith level**, from 1 to 100. Faith does **not** regenerate automatically.
- Faith can be restored at suitable **altars**, through **religious wine**, approved **potions** and the estate's **Ornate Rejuvenation Pool**.
- Each active prayer consumes one Faith point when activated and another at each subsequent **six-second baseline interval**. Several compatible prayers can be active at once and consume their own points.
- A **Faith equipment bonus** makes points last longer. Each +1 bonus adds about **3.33%** to an individual prayer's interval; +30 extends the ordinary six-second interval to approximately twelve seconds.
- Players can toggle prayers off; already paid partial intervals are not refunded. Prayers shut off visibly when Faith runs out.

### Known prayers

| Prayer | Player effect |
|---|---|
| **Protect from Melee** | When active, completely blocks eligible incoming melee HP damage |
| **Protect from Missiles** | When active, completely blocks eligible incoming missile HP damage |
| **Protect from Magic** | When active, completely blocks eligible incoming magic HP damage |
| **Burst of Strength** | An offensive/strength-related combat enhancement; exact multipliers not finalized |
| **Piety** | A stronger combat-stat prayer; exact bonuses not finalized |
| **Rigour** | An advanced combat-stat prayer; exact bonuses not finalized |

**Only one overhead protection prayer may be active at a time.** This prevents simultaneous immunity to melee, projectiles and magic. Offensive prayers may coexist where compatible. Faith training should come from genuinely helped attacks, genuine prevention of injury, burial and meaningful spiritual acts, not leaving a prayer running without consequence.

### How the traditions coexist

A Sun mage can rely on Faith protection against melee; a Moon mage can combine a real Darkness effect with stealth; a Fog caster can use a summoned ally while avoiding enemy blows. These interactions should change the play experience without inventing cross-book spells, extra resources or invisible permissions. **Sun, Moon and Fog are the entire spellbook system.**

---
# 5. Exploration, quests and the world

## 5.1 A geographically connected fantasy world

The working setting, **GAMEWORLD**, is a continuous-feeling collection of voxel landscapes, roads, settlements, farmland, forests, wilderness, caves, dungeons and contested land. At least **three kingdoms** should be present in the first playable world, each with distinguishable laws, trade conditions, political interests and landscape identity. **Two more kingdoms** are planned after the foundation is proven. The actual nations' names, histories, cultures, religions, rulers and map are still open design material rather than established lore.

The player should learn geography through landmarks, paths, local testimony and risk. Towns offer safety and services, but are not identical hubs. Remote country can contain better resources, dangers, isolated people or valuable secrets. Stronger monsters do not conveniently wait until the player's level catches up.

## 5.2 Quests are stories worth finding

Important quests are **authored**, with memorable characters, scenes, choices and consequences. A quest may begin through a conversation, a letter, a discovered place, physical evidence, an employment opportunity or an existing relationship. Journal entries record what the character actually knows, rather than perfectly revealing unseen secrets.

Quest types include brief personal stories, long narrative arcs, investigations, political conflict, wilderness journeys, combat assignments, trade/commercial problems, skill work, puzzles and adventures involving factions. The player may solve some problems through speech, stealth, skill, equipment or magic, where **that quest deliberately supports those options**. Not every quest needs a universal peaceful and violent ending.

Quest markers may identify a known person or destination, or indicate a search region, but should not replace discovery. The world can close a quest permanently: a required character dies, a shipment is intercepted, a faction changes its position or a valuable opportunity expires. The journal should explain the outcome when the character could reasonably know it. The system must never pretend those events did not happen simply to preserve a quest chain.

## 5.3 Exploration and traversal

The player can move freely, sprint, jump, climb or mantle supported obstacles as their Agility improves. At level 1, ordinary movement and jumping are available, but advanced parkour is not. Around **Agility level 5**, a small ledge catch (approximately two blocks in the prototype) becomes possible. Higher skill improves the scale and difficulty of climbable terrain, reaching its full intended range at level 100. Difficult routes should be real physical spaces, not magical checkpoints disguised as obstacles.

Traversal must make room for first-person and third-person readability. A ledge with insufficient space on top should not become a teleport destination. NPCs with adequate Agility can use appropriate traversal when a real route requires it. **Spiritual Step** and **Gaseous Form** provide Moon alternatives, but remain constrained by their own magical rules.

## 5.4 What makes travel interesting

Travel creates opportunities for encounters that feel connected to circumstances: a merchant caravan carrying goods, wolves following a herd, goblins probing a neglected settlement, a patrol investigating a report, a traveler unable to reach a destination, or a hunter following tracks. None requires a constant feed of random pop-up quests.

Dungeons, boss lairs, resource sites, roads and dangerous territories should reward thoughtful preparation. Weather, darkness, seasons and time of day can change visibility and creature behavior. The world should make its dangers legible through sounds, bodies, damaged wagons, abandoned camps, territorial markings, rumors and recognizable silhouettes.

---

# 6. People, conversation and living society

## 6.1 NPCs are people first, services second

Human NPCs are intended to have **homes, routines, needs, occupations, relationships, personalities and memories**. Some farm, mine, craft or sell. Others patrol, steal, transport goods, administer institutions or live without paid work. They can eat, rest, trade, flee, help, quarrel, witness events and sometimes die without the player watching.

The player should feel this by doing ordinary things: visiting a workshop and discovering the craftsman is out buying stock, hearing a merchant complain about blocked roads, meeting a guard who remembers an earlier incident or encountering a town that has already lost an important figure. NPCs should **not know events they could not have witnessed or learned about**.

## 6.2 Occupations and distinctive behavior

There are **twenty human role families with five distinct subtypes each**, for **100 detailed character-behavior identities**. These include ordinary employment, social service, unemployment and noble governance. A generic label like *Merchant*, *Guard* or *King* is not by itself a personality or full behavior description. A living merchant has a specialty; a guard has a kind of duty; a noble's daily work is not automatically the same as their social title.

For example, an orchard keeper cares about long-lived fruit trees; a livestock farmer manages feed and breeding; a coastal fisher thinks about tides; a gem prospector evaluates risky deposits. A hungry unemployed wanderer may seek shelter or ask for food, but is not automatically a violent bandit. Specific duties depend on skill, equipment, employment, genuine resources and opportunity.

A complete catalog of the **100 established subtypes** appears in [Section 14.3](#143-the-one-hundred-human-npc-subtypes). These roles should lead to noticeably different choices, not merely different clothes and greetings.

## 6.3 Routines, perception and memory

Every person can react to relevant sights and sounds, remember meaningful interactions, and share credible information with someone else. Attention is imperfect. Cover, distance, ambient noise, shadows, visibility, identity and local conditions matter. If the player steals an item while genuinely unseen, the owner may notice a loss but should not instantly know who took it. If a witness follows through on a report, the affected guard or merchant may later act on it.

NPC knowledge and the truth are different things. A character might believe a rumor, misunderstand a disguised visitor or forget the details of a casual exchange. This supports genuine suspense without granting players a universal invisibility button. Important memories can influence social contact, prices, allegiance and the story.

## 6.4 Conversation and interaction

The player interacts through a **primary contextual action** and an **expanded set of relevant options**. A door can open, close, lock or be picked when appropriate. An NPC can offer conversation, barter, information or specialized interaction based on their situation. A container can be searched or stolen from, but only when the player is physically able to reach it.

Conversations should be **authored, situational and reactive**, not treated as automatically generated dialogue. Who is speaking, what they know, what they want, how they feel about the player, the current quest and the local political/legal situation should shape available lines. Important choices are consciously written with consequences; everyday conversations may draw from reusable, well-authored contextual remarks.

### A little everyday scene

A player enters a village just after a caravan attack. The baker has less flour, so the price of bread has risen. A guard knows about the road ambush because a survivor reported it. A different guard has not yet heard. A merchant wants escorts for a replacement shipment, while a fearful villager refuses to travel after dark. This is the desired sensation of a living world: several people reacting differently to the same real problem.

## 6.5 A society that keeps its own boundaries

NPCs can change occupations when a believable opportunity and a valid job change exist, not whenever the most profitable role is convenient. Their inventories, wages, supplies and work are real. Nobles can govern only through supported offices, property, people and powers, not generate unlimited armies or dramatic wars as an accidental result of the daily-routine simulation. Death is persistent and can affect the community.

---

# 7. Economy, crime, Honor and politics

## 7.1 Economy: real goods, real buyers, regional opportunity

The economy is a **single-player economy populated by autonomous characters**, not a multiplayer auction house or an infinite shop menu. Producers collect resources, craftspeople turn them into goods, merchants buy and sell, caravans carry shipments, households consume necessities, and local conditions affect what people can afford.

Different settlements may have different prices because of available supply, demand, transport danger and personal relationships. A long-term grain shortage can make food expensive; a caravan arrival can ease it; a player who floods a trader with identical goods should eventually find the trader less interested. This must be understandable from visible evidence and conversation, not hidden global price magic.

**Gold** is the common pricing unit. The document's reference item prices are **initial valuation anchors**, not identical quotes at every merchant. A trader has limited cash and stock. If they cannot buy an expensive item, they can reject the offer or consider a real barter arrangement instead of generating money. NPCs can compete for contracts, buy goods before the player arrives, repair equipment or spend money they have actually earned.

Money enters through work, loot, quests, bounties and approved exchanges. It leaves through housing, repairs, taxes, travel, supplies, bribes and other legitimate costs. Players can invest in supported commercial improvements or order goods from craftsmen. Investments can lose money. **Gold never buys XP or skill levels directly.**

## 7.2 Trading, barter and relationships

A sale removes the actual item from one owner and places it with another; it cannot simultaneously remain in a chest, a shop and a player's inventory. The same applies to wages, ingredients, house building materials, caravan cargo and stolen property.

Trade supports the ordinary sale/purchase of goods and **negotiated item-for-item barter**. Merchants have a personal **Attitude** toward known customers. Fair dealing, fulfilled contracts and assistance can build trust; betrayal, theft and bad behavior can damage it. Local faction standing, a player's public Honor and the merchant's individual memories are related influences, **not one interchangeable friendship meter**.

## 7.3 Crime needs a witness and a process

The player and NPCs can commit theft, assault, murder, illicit trade and other supported crimes. An offense becomes a jurisdictional guard matter when credible evidence actually reaches law enforcement. A witness must perceive something relevant and then manage to report it. Discovery of a missing item is not automatic identification of the thief.

Each kingdom maintains its own wanted state. A pursued player may surrender, pay a valid bounty, serve a sentence, resolve available bail, escape or resist. Wanted status can close lawful shops and provoke guards in the relevant jurisdiction. Fences can handle stolen goods under their own risks and permissions; reputable merchants are not automatically willing to buy contraband.

Law must distinguish **what actually happened**, **what a witness believes happened**, and **what the guards have been told**. A player being morally generous does not erase the legal consequences of an earlier crime.

## 7.4 Stealth that uses the actual environment

Stealth relies on **light, shadows, line of sight, obstacles, sound, suspicion and recognition**. Sneaking in darkness differs from crouching in full sunlight. Loud movement, heavy gear, nearby combat, dropped objects or a visible spell may attract attention. Suspicious people might stare, investigate, search, call for assistance or return to normal routines after losing the trail.

Moon's **Darkness**, **Minor Illusion** and **Disguise Self** are meaningful because ordinary NPCs already understand sights, sound and identity imperfectly. Stealth should not require one universal stealth percentage, and Thieving should not automatically produce a bounty if nobody knows the offender.

## 7.5 Honor: the person the adventurer becomes

Honor is a persistent moral measure from **−100 to +100**, beginning at **0**. It tracks actual choices, not simple faction membership or whether a guard happened to see an offense. The bar has **17 named ranks**. The center is **Neutral**; eight ranks extend toward dishonor and eight toward virtue.

| Negative Honor | Score | Positive Honor | Score |
|---|---|---|---|
| Notorious | −100 to −89 | Revered | +89 to +100 |
| Ruthless | −88 to −77 | Exemplary | +77 to +88 |
| Vile | −76 to −66 | Noble | +66 to +76 |
| Cruel | −65 to −55 | Virtuous | +55 to +65 |
| Hostile | −54 to −44 | Generous | +44 to +54 |
| Dishonorable | −43 to −33 | Honorable | +33 to +43 |
| Disreputable | −32 to −22 | Respectable | +22 to +32 |
| Questionable | −21 to −11 | Considerate | +11 to +21 |
| **Neutral** | **−10 to +10** | | |

Helping someone in genuine need, making a donation, sparing a defeated enemy, settling a real debt or peacefully surrendering can improve Honor. Robbery, harming innocents, abusing helpless people, unjustified cruelty to animals and desecrating the innocent can lower it. Very small gestures, such as greeting someone or petting a friendly animal, may contribute a little but should never be repeatable loopholes. Killing a person is more significant than offering a compliment. Context, intent and whether the result was real matter.

Honor is visible through a bar with a moving facial marker, from sinister to neutral to warmly expressive. Changes can affect certain interactions, prices, authored rewards, ending choices and the visual presentation of the player's memorial. People respond to public Honor only when they could plausibly know the character's reputation. Personal trust and criminal charges remain independent.

### High-Honor merchant benefits

Eligible legitimate merchants offer **5% off at +33 Honor**, **10% at +55**, **15% at +77** and **20% at +89**. Only the highest earned tier applies; discounts never stack. Merchants still respect cost floors, legality and actual stock, so Honor cannot become an infinite resale-profit exploit. See the quick reference in §14.8.

High and low Honor should both have meaningful authored consequences. A ruthless character might be treated with fear or welcomed by criminals in selected stories; an honorable character might gain genuine trust, opportunity or recognition. Neither path should bypass the rest of the RPG.

## 7.6 Factions, titles and noble life

Factions, households, guilds and political interests can offer work, trade, affiliations, rivalries, titles and privileges. Noble advancement and heraldry are **optional**. An adventurer must not have to become a noble to train skills, explore, defeat ordinary bosses or finish the main quest.

Barons, Counts, Dukes and Kings are part of the world's social and administrative structure, but noble **rank**, **office**, **land**, **loyalty** and **legal authority** are different things. A title alone does not permit limitless rulemaking. Political stories, succession and wars remain authored where appropriate; the background simulation is not allowed to destroy the world map by itself.

---

# 8. Wildlife, pets and monsters

## 8.1 A genuine animal world

Ordinary animals should behave like living creatures with locations, hunger, thirst, fear, rest, groups, offspring and survival instincts. Livestock can be tended, prey can be hunted, predators can stalk, and familiar pets can recognize people. Adults and young behave differently. The game aims for **fourteen animal species** with separate adult and young behaviors, for **twenty-eight animal life-stage profiles**.

**The fourteen species:** Chicken, Pig, Horse, Rabbit, Fox, Coyote, Wolf, Mountain Lion, Brown Bear, Duck, Cattle (Cow/Calf), Sheep, Dog (Dog/Puppy) and Cat (Cat/Kitten).

| Animals | How they add life |
|---|---|
| Cattle, sheep, pigs and chickens | Herds, flocks, feeding, young and husbandry connect farms to local food and industry. |
| Horses | Social animals that graze, travel, startle and may bond with familiar handlers. |
| Ducks and rabbits | Small prey and habitat life that react to predators, seasons and cover. |
| Foxes and coyotes | Opportunistic hunters and scavengers that pressure smaller wildlife or poorly defended farms. |
| Wolves | Family/pack hunters that make roads and livestock boundaries meaningful. |
| Mountain lions | Solitary ambush predators whose territory is dangerous even without a formal quest. |
| Brown bears | Wide-ranging animals that can threaten an intruder, especially near cubs or food. |
| Dogs | Owned companions can greet, follow, patrol and respond to threats; unowned dogs prioritize survival. |
| Cats | Independent household or free-roaming animals that groom, rest, stalk, scratch, explore and seek affection selectively. |

The specific adult and juvenile behaviors are recorded in [Section 14.4](#144-animal-life-stages). Players should be able to notice the difference between a wolf pack, a lone mountain lion and a mother bear rather than merely observing different models follow the same chase script.

## 8.2 Companions and animal welfare

Dogs and cats can be **real owned animals**, not cosmetic inventory icons. They recognize their home and familiar people, have needs and develop responses through their temperament and treatment. A dog may alert the family to an intruder. A cat may pick a favored sleeping spot, play or scratch its post. A kitten should rely on care rather than wander as a miniature adult.

Pet welfare matters without making the game a punishing animal-care simulator. A player can provide food, shelter, compatible facilities and household care. Real food stock is consumed when needed. A pet must not silently be replaced by a new identical pet because its home was unloaded.

## 8.3 Monsters are inhabitants too

Monsters are not simply enemy spawners. They have **dens, territories, hunger, fear, routines and preferred prey or rivals**. A goblin away from its tribe may retreat, while a cornered troll defends its lair. A ghost may haunt an old site without attacking every visitor. A dragon affects the entire local ecological order.

**Design principle:** *Hostility is a response, not a permanent requirement.* Territorial signs, activity, weather, wounds, numbers and time of day shape the encounter. Players should be able to observe, evade, exploit habits, force a retreat or sometimes trigger conflict between enemies without becoming the universal target.

### Ecological rules

- Monsters establish territories around caves, camps, ruins, webs, grave-sites or hoards. They hunt, rest, scavenge and defend their homes.
- Disturbing or destroying a lair changes local pressure. The area may remain quiet for a long time before another group moves in.
- Living creatures can reproduce or migrate where the environment supports them; undead may continue emerging from a **real curse, burial ground, necromancer or other established source** until it is addressed.
- Pack creatures may call allies and lose morale. Solitary predators stalk or withdraw. Heavy attacks should have meaningful tells.
- Night, storms, cold, seasonal scarcity and even moon phases can affect particular species. Werewolves are especially influenced by lunar conditions; dragons are not obliged to follow a human bedtime.
- NPCs and monsters do not gain impossible knowledge of the player's position. They rely on sight, sound, scent, supernatural senses where appropriate, and remembered encounters.

### Monster families in the current design

| Creature | Encounter identity |
|---|---|
| **Goblin** | Social scavenger and pack raider; dangerous in numbers, more hesitant alone |
| **Orc** | Organized territorial raider with tribal pressure and group combat |
| **Troll** | Slow, brutally strong solitary brute that defends its ground |
| **Imp** | Curious, evasive harasser, associated with strange or fiery disturbances |
| **Bat** | Agile roosting creature that scatters or swarms according to context |
| **Ghost** | Haunting, place-bound spirit whose behavior relates to disturbance and memory |
| **Zombie** | Unliving creature driven by grave or curse conditions and crude pursuit |
| **Skeleton** | Rigid dead guardian or roaming undead that may be commanded by necromancers |
| **Werewolf** | Intelligent predator, patient stalker and nighttime apex threat |
| **Dragon** | Rare regional apex creature with a hoard and strong territorial influence |
| **Giant Spider** | Web-building ambush predator that senses vibration and guards its brood |

The source describes distinct loot, habitats, senses, routines, weaknesses, group tactics and regional effects for these monsters. The design of monster encounters should preserve those identities rather than using one universal aggressive AI. This is not permission to invent additional species or to turn every undead character into a Fog summon.

### Reading the landscape

A forest with cut branches, silence and webbing warns of a spider colony. Scorched stone or frozen ground can signal a dragon. Bone fences indicate goblin territory. An old grave with disturbed earth might suggest necromancy. Animals may fall silent or relocate before a player ever encounters the source of the danger. Visual evidence is a major exploration reward.

---

# 9. Estates, Construction and collecting

## 9.1 A home that means something

Players can buy and develop **at least three estate plots per kingdom**. Property is a meaningful long-term goal: a safe place for utility, personal expression, collections, animals, sanctioned sparring and preparation for travel. It is not a universal reset button for the world's dangers or laws.

## 9.2 Construction: deliberate rooms, not freeform building chaos

House construction uses a **doorway-connected room layout** with **fixed furnishing hotspots** rather than arbitrary free placement of walls and furniture. The player selects valid rooms, pays real material costs and builds supported structures using Construction. Each room has an identity and purpose. Furniture should visibly appear and be usable after construction. Previewing or moving a finished item is not equivalent to building it again for XP.

The early Construction experience begins with simple furnishings, such as a **Crude Wooden Chair**, then develops into fixtures, workstations and demanding larger projects. Costs rise with ambition and materials. Builders can repair damaged fixtures without manufacturing new originals of stored property.

## 9.3 Rooms and their reasons to exist

| Estate space or furnishing | What the player gains |
|---|---|
| **Parlour / ordinary rooms** | A customizable, inhabited home and the entry points to more elaborate spaces |
| **Workshop with Armor Stand** | Convenient discounted repairs of eligible worn gear |
| **Workshop with Combat Dummy** | Safe weapon and timing practice **without unlimited dummy XP** |
| **Study with Lecterns** | The ability to create eligible teleport tablets using actual supplies |
| **Ornate Rejuvenation Pool** | Restore the specifically supported depleted pools and temporary status conditions, including HP, stamina and Faith |
| **Occult Altar** | Switch between **Sun, Moon and Fog**, the only three Magic books |
| **Gilded Altar and burners** | Perform meaningful Faith training with legitimate offerings; the established altar offers **3.5× total base XP** for eligible actions when conditions are met |
| **Crystalline Portal Nexus** | Repeated travel to properly discovered and registered destinations, not arbitrary locations |
| **Superior Garden: Spirit Tree / Fairy Ring** | Additional authorized travel networks through installed fixtures |
| **Costume Room** | Display and store owned costumes, holiday rewards, jewelry, capes and armor collections |
| **Trophy spaces** | Show authentic earned monster-head trophies, change visible mounts and inspect an owned collection |
| **Menagerie** | A living home for owned dogs and cats with care, rest and play facilities |
| **Aquarium** | Display collectible fish eggs and record Fishing milestones |
| **Combat Room** | Host nonlethal, consenting sparring sessions in specialized rings |
| **Visual house styles** | Restyle compatible rooms and exterior finishes without replacing the underlying property |

### Travel convenience has limits

The Nexus and installed networks can offer unlimited **valid** travel uses after construction, but only to allowed registered destinations. They do not grant discovery of a secret place, instant escape from every possible situation or duplicate physical jewelry items.

## 9.4 Pets and the Menagerie

An indoor or outdoor Menagerie can register up to **30 owned pets**, currently cats and dogs. The pets remain the same living creatures whether they are resting, playing elsewhere on the estate or temporarily absent. Kittens can be automatically fed through a **properly supplied feeder or caregiver**; this consumes real food and cannot remain magically stocked forever. If supplies run out, the player should be told and given a fair opportunity to provide care.

A cat scratching its post can trigger a small authentic reaction from a person who actually notices. A familiar dog might greet a visitor while another dog barks at a stranger. An outdoor Menagerie may feature a **playful, non-injurious pet arena**, strictly for fun rather than gambling, animal harm or XP farming.

## 9.5 Collections that represent actual adventures

Costume Room fixtures include a **Toy Box**, **Treasure Chest**, **Jewelry Box**, **Cape Rack** and **Armor Case**. They organize objects the player genuinely owns. Displays can be rearranged, but showing an armor set never produces another copy of its equipment. Treasure Trails and bosses can supply rare trophies or cosmetics; storage is not a reward generator.

The **Aquarium** celebrates Fishing through legitimate collectible eggs associated with eligible catches. Its display and milestone perks may offer modest Fishing conveniences, but it does not spawn endless fish or award passive experience.

## 9.6 Sanctioned sparring

The estate Combat Room supports a **Boxing Ring** for unarmed practice, a **Fencing Ring** for approved weapon sparring and a broader **Combat Ring** for permitted weapons, Magic and Faith. Participants consent and combat behaves credibly, but defeat is nonlethal and **does not trigger normal corpse/death penalties**. Appropriate combat skill experience may be earned through genuine actions, not repeated automatic match victories.

## 9.7 Estate styles and household life

Supported design themes include **Swamp**, bright **Civitas** and **Wilderness** aesthetics. These change the look and mood of supported surfaces and furnishings; they do not move the house to another kingdom or change legal ownership. Servants and household workers, where available, must perform believable supported duties using real supplies, work time and permissions.

---

# 10. Death and recovery

## 10.1 Death has a cost without undoing the character

When the player dies outside a sanctioned nonlethal event, they respawn **empty-handed** at the nearest viable graveyard. Their learned skills, XP, Honor, owned estates, relationships and legitimate quest consequences remain. The equipment, money and items carried at death stay behind as **the actual original belongings**, subject to the following rules.

The death location is classified as **inside a city** or **outside a city**. There is no third corpse-security category. A PvH territory, where hostile humans may deliberately hunt the player, affects danger **before death** but does not override the corpse rules.

| Death location | What happens | Player's choice |
|---|---|---|
| **Inside a defined city boundary** | The corpse and its entire original inventory become **Guard-protected immediately**. No civilian or criminal can steal those items while a Guard is coming. The Guard delivers them to an Undertaker. | Recover **all original belongings** by paying the normal Undertaker fee when the claim is available. |
| **Outside city limits** | The body and its carried possessions remain exposed. Any human who actually finds the site and has an appropriate motive may inspect or take eligible items. An Undertaker may travel there to collect what remains. | Race back to reclaim remaining gear personally, or pay an Undertaker **normal fee plus a disclosed outside-city surcharge** to retrieve what is still present. |

City protection is an unconditional feature of the death rules, **not a guarantee of free or immediate service**. Outdoor recovery is a genuine race against people in the same world. A thief who arrives first can carry away a valuable sword and eventually trade it. A lawful passerby may seek help instead of stealing. A nearby guard may be helpful, but simply standing outside a town does not convert the corpse into a protected city claim.

## 10.2 The Undertaker and the risk of loss

Undertakers are real, supported services. Their workers cannot instantly teleport to a wilderness grave or recover an item somebody already removed. A player who cannot afford the collection fee immediately can acquire legitimate funds and redeem the goods already secured in Undertaker custody. Recovered goods remain the same items, including their repairable wear.

Visible gravestones, NPC movement, reports and the player's recovery screen should clarify what is happening. Item durability can be reduced by eligible violent deaths, adding a reason to visit the Workshop afterward. A living owned horse may flee during the incident and should not silently appear in the player's inventory upon respawn.

## 10.3 An example

A player carrying a Dragon sword dies just beyond the city gates. A Highwayman sees the fight and searches the body, taking the sword. Later the player pays an Undertaker to recover the remaining armor and food. The sword cannot appear again in the Undertaker's list, because it is now somewhere in the world with the Highwayman or a subsequent buyer. The exact same fight a few steps **inside** the registered city boundary would protect the full inventory for eventual Guard/Undertaker delivery.

---

# 11. Interface, atmosphere and presentation

## 11.1 The UI should invite play, not resemble a debug panel

The player's view prioritizes the world. The HUD communicates **HP, stamina, Mana, Faith, currently active prayers, important combat status, relevant equipment, interaction prompts and brief quest/skill notifications**. It should be readable in both camera modes. Where information is not necessary during ordinary play, it belongs in a menu rather than permanently occupying the screen.

The player needs accessible screens for **skills**, **equipment**, **inventory**, **Sun/Moon/Fog spells**, **Faith prayers**, **journal and quests**, **map**, **Honor**, **relationships where known**, **collections** and **estate construction**. These should communicate game meaning in plain language. A skill panel describes what a higher skill unlocks; a spell entry says what it does and its Mana cost; an item describes what the wearer gains and which skill level is required.

## 11.2 Feedback should show cause and effect

Landing a sword blow should feel different from striking a shield. Blocking, parrying, magical effects, elemental weaknesses, stagger, injury and death should produce credible animation, audio and visual information. Important state changes should be apparent through NPC reaction, world objects or short notices rather than hidden logs.

The same applies to everyday life: freshly planted fields, a lit charcoal pit, an occupied workstation, a stocked shop, a hungry pet or a nervous guard should be visibly understandable. Players should notice consequences before they open a spreadsheet-sized information screen.

## 11.3 Visual identity

The game uses **voxel-built environments with intentionally crafted models, readable materials, believable lighting and a coherent fantasy art direction**. Voxel form should not imply cheap or disposable character work. People, animals, tools, weapons, furniture and architecture need recognizable silhouettes and natural animation. Lighting and effects should make Sun elemental combat energetic, Moon magic uncanny and Fog magic cold and ominous, while avoiding visual noise that hides actual combat or stealth conditions.

Different kingdoms should eventually have their own architecture, colors, economy and ritual imagery once their lore is written. Until those decisions exist, the design document should describe the intended feeling rather than assert invented canon.

## 11.4 Sound and atmosphere

Audio communicates weight, location, weather, material, creature presence, danger and social life. Players can hear a distant smithy, a busy market, a wolf warning call, armored steps on stone, a spell crackling through wet ground or the unnatural quiet around an undead site. Combat sound should reward precise action and never replace necessary visual cues.

## 11.5 Accessibility and legibility

High-impact information such as Honor, magical resources, enemy tells, danger, injuries and interaction opportunities must not rely on color alone. Camera comfort, subtitles, remapping, readable font sizing, intensity options and the precise limits of accessibility aids need dedicated design decisions. No unapproved platform-specific controls are asserted in this document.

---

# 12. Playable content and development priorities

## 12.1 What is planned for players to do?

| Content | Why it belongs in the game |
|---|---|
| Main and optional authored quests | Give the world recognizable characters, conflicts, revelations and emotional stakes |
| Open-world exploration | Reward curiosity with discoveries, risk, shortcuts, secrets and beautiful spaces |
| Skilling and production | Provide independent long-term progression and real utility |
| Dungeons | Test exploration, supplies, enemy knowledge and physical combat |
| Bosses and solo-compatible raids | Demand mastery of gear, position, timing and preparation |
| Treasure Trails | Turn clues and discovery into prestigious rewards |
| Minigames and battlegrounds | Offer focused optional challenges, not mandatory XP loops |
| Human predation (PvH) | Make certain roads/regions dangerous because human criminals can choose to hunt or rob |
| Factions and noble affairs | Provide optional social and political development |
| Property, pets and collections | Make accomplishments and care visible in a persistent home |
| Honor and crime | Give ordinary behavior real social and legal consequences |
| Ironman variants | Optional **Ironman, Hard Ironman and Hardcore Ironman** profiles, with final restrictions to be decided |
| Daily incentives | Optional recurring reasons to return, never required chores for basic progression |

## 12.2 What gets built first?

The immediate development environment is a neutral **developer test map (devmap)**. It is a place to build, refine and demonstrate real reusable gameplay and finished-quality representative assets, not an authored first kingdom. All major systems should be represented there before the team commits to the complete world layout.

**Foundational priorities in order:**

1. **The eighteen skills and progression:** A functioning reason to gather, craft, fight, explore and grow; representative meaningful training for all skills.
2. **Real-time physical combat:** Reliable movement, aiming, attacks, timing, defense, damage, stamina, Magic, Faith and feedback.
3. **Intelligent autonomous NPCs:** Routines, work, perception, interaction, remembered events and believable responses.

Other systems begin as small usable demonstrations alongside these foundations: trading, housing, wildlife, monsters, law, quests, stealth, death, collections and the three Magic books. The goal is to build useful parts of the eventual game rather than disposable demos that must be remade later.

## 12.3 First playable experience

A strong initial demonstration need not contain a finished royal court or full cinematic storyline. It should let a player create a character, explore a compact area, train a variety of skills, talk to different people, buy and use equipment, fight credibly, cast representative **Sun, Moon and Fog** spells, maintain Faith prayers, interact with wildlife, encounter real work and trade, and observe at least a few persistent consequences.

The eventual first playable **world** should have at least three kingdoms; the internal devmap does **not** need finished nations or lore. The total world size, full story, release platforms, optional co-op and exhaustive content volumes are not yet decided.

## 12.4 What success looks like

A successful RPGGAME session should produce stories like these without requiring an elaborate cinematic trigger:

- A traveler gathers wood, smiths equipment and returns to confront a monster that once forced a retreat.
- A Moon spell conceals the adventurer in a warehouse, but a guard remains suspicious because he heard footsteps.
- A Fog mage survives a haunted crypt with ice magic and short-lived undead allies, then returns to an estate to restore Faith and switch to Sun.
- A merchant's shortage becomes an opportunity for a player transporter, while another merchant tries to claim the same deal.
- A careless crime ruins a relationship even before formal law enforcement becomes involved.
- A player returns to their estate and finds a familiar cat sleeping by a favorite scratching post, unchanged in identity despite weeks of adventuring.

All of those stories arise from **real systems serving deliberate game design**. They are not permission to replace authored quests with algorithmic plot generation.

---

# 13. Design questions still open

The following subjects are intentionally **not settled**. An open question is not a missing feature the document silently fills in.

| Area | Decisions still needed |
|---|---|
| **World and narrative** | Final game title, kingdom names, faiths' theology, world map, culture/history, story premise, cast and main quest arcs |
| **Sun spells** | Exact Magic levels and Mana costs for all 25 combinations; how Spark and other special effects appear in the spell list; whether the Fire Blast staff condition is permanent |
| **Moon spells** | Individual levels/costs, durations, influence limits, disguise credibility, transformation limits and meaningful restrictions |
| **Fog spells** | Four Ice attacks' precise patterns and freeze behavior; full necromancy repertoire; whether Frost Lance remains as a specialty |
| **General Magic** | Book-switching places other than the estate altar, cast interruption, time-skip Mana recovery, spell visuals and target rules |
| **Faith** | Individual prayer unlock levels, specific percentage bonuses, restoration amounts and additional religious offerings |
| **Combat and equipment** | Timing/impact/damage curves, animation sets, remaining handedness rules, accessory quantities, backpack size, equipment durability |
| **Skills and economy** | Detailed XP payouts, recipe/resource levels, market supply curves, wages, trade margins, repairs, material stock and item availability |
| **NPCs and wildlife** | Population sizes, routine pacing, exact sense thresholds, dialogue volumes, occupation opportunities, pet care frequency and detailed breeding conditions |
| **Estates** | Room/furnishing recipes, house purchase prices, collection capacities, travel destinations, applicable upgrades and perks |
| **Crime and Honor** | Certain criminal caps, additional reputational thresholds, authored law responses and the precise consequences of particular moral acts |
| **Product** | Platforms, full launch scope, accessibility standards, save options, final HUD/control layouts, sound direction and any optional co-op |

**The principal boundaries that are *not* open:** exactly 18 skills; skill level cap 100; single-player baseline; thirteen equipment tiers; no enemy level scaling; actual physical combat contact; persistent authored quest consequences; distinct social/legal/Honor systems; twenty role families × five subtypes; fourteen ordinary animal species; and **exactly three spellbooks called Sun, Moon and Fog**.

---

# 14. Reference catalogs

The reference section keeps useful approved numbers and specific choices near the end instead of burying the reader in formulas and engine contracts. These are **game-content catalogs**, not programming instructions.

## 14.1 Appearance catalogs

**Natural hair colors (10):** Black, Dark Brown, Medium Brown, Light Brown, Chestnut, Auburn, Natural Red, Strawberry Blonde, Dark Blonde and Light Blonde.

**Starting garment colors (24):** Red, Orange, Yellow, Lime, Green, Olive, Teal, Cyan, Blue, Navy, Purple, Lavender, Magenta, Pink, Coral, Burgundy, Brown, Rust, Gold, Tan, Beige, Gray, White and Black.

**Hair variations (20 per sex):** Bald; close-cropped; buzz/crew or bob; spiky textured; swept back; short curls; side part; curtains; medium layered; mid ponytail; top-knot; medium waves; long straight; long layered waves; high ponytail; single braid; dual braids; dreadlocks; mohawk; and side-shaved/undercut. Male and female catalog assets remain independently fitted.

**Facial hair (20):** Clean-shaven; light stubble; dense stubble; short boxed beard; medium boxed beard; long full beard; short rounded full beard; long tapered beard; bare goatee; goatee with moustache; pointed goatee; Van Dyke; pencil moustache; chevron moustache; handlebar moustache; horseshoe moustache; walrus moustache; mutton chops; short sideburns; long sideburns.

The original detailed hairstyle and clothing-choice catalogs remain baseline appearance references. Character creation must preserve **twenty independent choices each for tops, leg garments and footwear**; none should turn into a new armor slot.

## 14.2 Equipment and work-tool quick reference

- **Thirteen tiers:** Stone, Bronze, Chainmail, Iron, Black, White, Steel, Granite, Cobalt, Dragon, Molten, Godly, Malevolent.
- **Thirteen melee families:** Dagger, Sword, Longsword, Scimitar, Axe, Pickaxe, Mace, Warhammer, Battleaxe, Spear, Claws, Halberd, 2h Sword.
- **Five ranged families:** Crossbow, Dart, Knife, Tomahawk, Javelin.
- **Tools also used in combat:** Axes, Hatchets, Pickaxes and Hammers. These follow the respective work-skill rules and applicable melee handling.
- **Twelve equipment categories:** Head, Torso, Legs, Boots, Cape, Ring(s), Necklace, Gloves, Melee, Left Hand, Arrows, Backpack.
- **Important absence:** no fourth book, no bow family, no carry-weight bar, no general extra tool slot and no newly invented monster or animal species.

## 14.3 The one hundred human NPC subtypes

The following established occupation catalog is retained for world design. **Twenty families, five subtypes per family, 100 distinct daily specialties.** Each subtype describes what that character is naturally interested in doing when the world permits it, not an unlimited ability to perform every job.


### Farmer

- **Crop Farmer:** Choose seasonal field crops, rotate plots, sow, irrigate and harvest annual field yields.
- **Livestock Farmer:** Breed herds, manage grazing and feed, tend animal health, and collect milk, wool or other husbandry output.
- **Orchard Keeper:** Prune fruit trees, manage perennial tree health, protect blossoms and schedule fruit picking.
- **Herbal Grower:** Select medicinal or arcane herb beds, control growing conditions, harvest and grade potent botanicals.
- **Vineyard Keeper:** Train grapevines, manage trellises, choose harvest ripeness and supply grapes to processors.

### Fisherman

- **River Fisherman:** Track river flow, migratory runs and changing riverbank fishing spots; choose line/net placement.
- **Coastal Fisherman:** Read tidal windows, select shore or near-coast fishing grounds and land catch before tide changes.
- **Deep-Water Fisherman:** Provision a sea voyage, evaluate offshore weather, choose distant grounds and return with a large catch.
- **Shellfish Gatherer:** Identify shellfish beds, time low-water access, collect shellfish and avoid depleted or hazardous beds.
- **Lake Net Fisherman:** Set and retrieve lake nets, monitor net saturation and select still-water net locations.

### Woodcutter

- **Logger:** Choose legal stands for felling, assess tree fall paths, cut trunks and stage full logs.
- **Forester:** Survey managed woodland, plant saplings, mark protected trees and authorize sustainable selective cuts.
- **Resin Tapper:** Select suitable living trees, score bark without felling, install collection vessels and harvest sap/resin.
- **Charcoal Burner:** Acquire cut wood, construct charcoal pits or kilns, manage low-oxygen burns and recover fuel.
- **Firewood Cutter:** Select low-grade wood, cut and split logs into domestic fuel bundles and fulfill local heating demand.

### Miner

- **Ore Miner:** Select metal-bearing veins, extract and sort ore by metal type, and stage ore for processors.
- **Quarry Worker:** Choose supported quarry faces, cut usable masonry blocks and stage dimension stone.
- **Gem Prospector:** Survey uncertain deposits, sample promising ground, identify gems and decide where further excavation is warranted.
- **Clay Digger:** Inspect clay-bearing ground, excavate workable clay, manage waterlogging and supply ceramic or building inputs.
- **Coal Miner:** Find combustible seams, assess underground ventilation and fire risk, extract coal for fuel users.

### Craftsman

- **Blacksmith:** Select metal tools or melee-weapon orders, heat/forge components, temper blades and repair metal tools.
- **Armorer:** Fit individual wearers, shape or assemble protective pieces, test fit and repair armor.
- **Carpenter:** Measure planned furniture or joinery, cut timber, assemble wooden goods and install supported fittings.
- **Cook:** Choose recipes from available ingredients, prepare food batches, monitor spoilage and complete meal orders.
- **Tailor:** Take garment measurements, choose patterns, cut cloth, sew and alter garments to fit.

### Merchant

- **General Store Owner:** Assess broad household demand, allocate limited shelf space among categories and replenish everyday staples.
- **Weapons Merchant:** Appraise weapon condition and tier, choose arms stock, verify buyer access and match products to demand.
- **Food Merchant:** Forecast daily edible demand, rotate expiring lots, discount perishables and replenish fresh food.
- **Traveling Merchant:** Compare regional buy/sell margins, choose markets, purchase a traveling stock and arrange journeys.
- **Fence:** Assess stolen-item provenance and enforcement risk, accept/reject illegal stock and arrange discreet resale.

### Hauler

- **Courier:** Prioritize lightweight sealed letters or parcels, choose fast delivery routes and obtain recipient acknowledgment.
- **Wagon Driver:** Evaluate vehicle capacity and road passability, load a contracted bulk shipment and complete a point-to-point haul.
- **Caravan Hauler:** Coordinate multiple cargo units and travelers, set convoy departure, maintain formation and respond to route security.
- **Porter:** Move goods short distances within a facility or district, sequence nearby carry tasks and place cargo at exact stations.
- **Supply Runner:** Identify time-critical supply requests, abandon low-priority runs and expedite emergency food, fuel or medicine deliveries.

### Guard

- **Town Guard:** Respond to resident complaints, perform neighborhood patrols, verify witnesses and investigate local offenses.
- **City Guard:** Monitor crowded districts, disperse disturbances, coordinate reinforcements and route arrested persons into city custody.
- **Gate Guard:** Check passage permissions and suspicious cargo, operate access checkpoints and deny or admit entry.
- **Prison Guard:** Record custody, supervise cell transfers and visitation, check confinement and respond to escape attempts.
- **Rural Patrol Guard:** Select road circuits, investigate roadside reports, respond to isolated offenses and seek reinforcements over distance.

### Knight

- **Household Knight:** Maintain a named patron’s security, protect household members and respond to threats against their estate.
- **Border Knight:** Inspect frontier fortifications, evaluate incursions and hold or reinforce assigned border sites.
- **Escort Knight:** Assess threats along a designated journey, stay with a protected target and adjust escort formation or route.
- **Knight Errant:** Seek sanctioned martial-service petitions, assess merit, travel to resolve accepted duties and report results.
- **Order Knight:** Receive vows-based institutional directives, protect order assets and report compliance to a specific command hierarchy.

### Thief

- **Pickpocket:** Profile carried valuables and attention, approach crowds, lift accessible items and leave unnoticed.
- **Burglar:** Survey building occupancy and access, plan unlawful entry, breach locks and remove stored valuables.
- **Highwayman:** Choose isolated road victims, block passage, demand surrender and flee before assistance arrives.
- **Smuggler:** Seek restricted cargo contracts, conceal contraband, choose covert transport routes and evade checkpoints.
- **Bandit:** Coordinate an armed group, select lucrative targets, initiate a forceful raid and divide captured spoils.

### Hunter

- **Game Hunter:** Assess game populations, stalk approved prey, harvest meat/hides and choose sustainable hunting areas.
- **Trapper:** Select trap-site corridors, deploy devices, return to check capture status and recover or reset traps.
- **Monster Hunter:** Research nonordinary creature threats, prepare specialized equipment, track dangerous targets and claim contracts.
- **Bounty Hunter:** Verify a lawful active warrant, follow suspect evidence, attempt capture and return the target or proof.
- **Predator Hunter:** Identify dangerous wildlife threatening people, livestock, or traveled routes; assess active threats, track their locations, and drive off or kill threats when justified.

### Laborer

- **Builder:** Receive building plans, sequence construction stages, consume materials and complete structures.
- **Road Worker:** Inspect transport infrastructure, identify blockages or decay, repair roads/bridges and reopen routes.
- **Dockworker:** Allocate berths, unload/load arriving waterborne cargo and reconcile port manifests.
- **Repair Worker:** Diagnose damage to supported structures, request parts and restore working condition.
- **Excavator:** Survey digging assignments, clear earth, dig approved foundations/trenches and manage excavation stability.

### Clergy

- **Sun Priest:** Conduct public Sun rites, hear confessions, and oversee public altars and liturgical schedules.
- **Moon Priest:** Perform hospitality, contract and recitation rites; maintain temple custom and mediate disputes under Moon observance.
- **Funerary Priest:** Conduct graveyard and memorial rites, maintain death records and guide burial or memorial practice.
- **Faith Instructor:** Teach doctrine and practice guidance, and direct petitioners toward Faith-training actions already present in the world.
- **Shrine Keeper:** Maintain altar stock, burners, offerings and shrine access under established rules.

### Civic Services

- **Banker:** Operate deposit and withdrawal ledger services, maintain account records and enforce balance limits.
- **Innkeeper:** Manage lodging, keep a guest register and coordinate room service for paying guests.
- **Bartender:** Serve drinks, manage bar stock and support ordinary pub social interaction.
- **Clerk:** Process records, issue documents, manage queues and provide administrative service.
- **Curator:** Arrange museum exhibits, maintain collection display and manage guest viewing of items.

### Leatherworker

- **Tanner:** Cure rawhide and hides into usable leather under material and ownership rules.
- **Leather Cutter:** Cut leather patterns and components from prepared stock for downstream assembly.
- **Leather Stitcher:** Assemble leather goods from cut components using tools and recipes.
- **Cobbler:** Produce and fit leather footwear from materials and patterns.
- **Leather Goods Repairer:** Repair non-armor leather goods, restoring condition without changing tier or creating armor.

### Unemployed

- **Hobo:** When hunger, exhaustion, or safety fall below tolerable limits, seek accessible food, rest, help, or legal charity; only when desperate consider taking available necessities, avoid confrontation and escape rather than initiate violence.
- **Job Seeker:** Survey known employers and viable vacancies, weigh wages against skills, risk and travel, request employment, and accept a verified appointment when offered.
- **Displaced Resident:** After losing an actual home or settlement access, search for a lawful temporary place to stay, contact known relations, and plan relocation or return if conditions recover.
- **Retiree:** Manage remaining income and possessions, maintain established relationships, visit familiar places, and share personally known information when socially appropriate.
- **Household Dependent:** Remain within a real supporting household, coordinate daily personal needs with that household’s available resources, and seek help from its members when essential support fails.

### Baron

- **Agrarian Baron:** Assess production failures across local agricultural holdings, prioritize support requests, and commission Farmer/Hauler work only within granted estate control.
- **Workshop Baron:** Assess supported workshops on owned property, decide which material shortages merit supply or production commissions, and track fulfillment.
- **Garrison Baron:** Monitor threats to a specific holding, request assigned Guards/Knights where legitimately empowered, and react to estate-security failures.
- **Restoration Baron:** Identify damaged facilities on controlled property, prioritize funded repair tasks, and authorize eligible Laborers to restore services.
- **Household Baron:** Manage obligations among a specific titled household, resolve supported staff or dependent needs, and maintain relationships relevant to its continuity.

### Count

- **Market Count:** Monitor supply shortages and trade interruptions across an county, choose which merchants or transport links to support, and review market recovery.
- **Circuit Count:** Review forwarded legal incidents across the county, request appropriate Guard coverage and follow up on jurisdictional reporting failures.
- **Settlement Count:** Assess competing settlement needs within an administered county, select building and road-work priorities, and authorize projects.
- **Revenue Count:** Review county expenses, wages and income obligations, decide which supported services can be funded, and address recorded shortfalls.
- **Patronage Count:** Assess local barons, employers and community relationships for appointments, mediation or recognition through dialogue and office actions.

### Duke

- **Frontier Duke:** Compare supported border-site threat reports across administered territories, prioritize reinforcement resources and authorize responses through Knights or institutions.
- **Logistics Duke:** Identify bottlenecks between regions under lawful influence, prioritize road or shipment restoration, and coordinate Hauler work.
- **Marshal Duke:** Evaluate readiness and assignment gaps among already subordinate military groups, authorize escort, protection and patrol commitments.
- **Court Duke:** Resolve eligible disputes between recognized houses and offices through established relationships, meetings, and faction permissions.
- **Domain Duke:** Assess operating failures across multiple administered estates/counties and prioritize upkeep, labor and property recovery.

### King

- **Administrator King:** Review kingdom-level reports on funded services and institutional performance; authorize feasible priorities through officials and budgets.
- **Lawgiver King:** Review jurisdictional enforcement patterns and consider only authored/configured lawful changes that the sovereign office is to authorize.
- **Diplomat King:** Evaluate known relations with other recognized rulers and factions, receive credible approaches, and select dialogue/relationship responses.
- **Defender King:** Assess verified threats against the kingdom and set supported defensive priorities through Knights, Guards and institutions.
- **Patron King:** Review authored petitions, legitimate services and eligibility for honors or appointments, then grant, refuse or postpone recognition.

## 14.4 Animal life stages

These paired behaviors retain the defining adult/young distinction and the ecological role of each established animal.

| Species | Adult behavior | Young behavior |
|---|---|---|
| **Chicken** | Forage close to a roost, group with flock, avoid fox/coyote threats, adult hens conditionally lay incubable clutches. | Chick stays close to flock/mother, shelters, feeds under supervision; exceptionally vulnerable to separation. |
| **Pig** | Feed opportunistically, root in suitable ground, seek shelter and social company, breed when husbandry permits. | Piglet follows sow/group, competes less effectively for food and flees with adults. |
| **Horse** | Graze, travel with herd/group, startle and reposition at danger, form proximity bonds with familiar handlers (not a new taming system). | Foal follows mare, tires easily, stays near herd and requires protection. |
| **Rabbit** | Graze/browse, use dense-cover habitat, keep watch and escape quickly; breeding favors safe cover and food. | Kit remains close to sheltered home area and avoids open ground until older. |
| **Fox** | Mostly solitary/paired search for rabbits and vulnerable poultry or duck young, scavenge accessible remains, avoid stronger predators and people. | Kit remains near den/home zone, follows or awaits parents, cannot perform independent adult hunting. |
| **Coyote** | Opportunistic small-prey hunt/scavenge, sometimes travel in small groups, test risk before attacking livestock. | Pup follows adults, practices short chase/flee responses, relies on parent/group provisioning. |
| **Wolf** | Pack-coordinated approach/pressure and retreat; prioritize feasible wild prey or poorly protected sheep/other livestock; avoid needless fatal engagements. | Pup remains near secure group area, follows adults when capable, uses play and alarm/flee rather than pack offense. |
| **Mountain Lion** | Solitary concealment, stalk and short committed ambush, defend space and withdraw if the risk becomes excessive. | Cub remains close to parent range; dependent on protection, hides or flees rather than independently stalking large prey. |
| **Brown Bear** | Forage widely, investigate food/remains opportunistically, rest, defend space or cubs with escalating warnings; usually avoids unnecessary pursuit. | Cub closely follows mother, explores cautiously, triggers strong maternal protective responses when threatened. |
| **Duck** | Alternate water/shore feeding, roosting and flock movement; adults may incubate clutches in suitable nesting space. | Duckling follows adult on land/water, stays grouped and seeks safe shore/water access. |
| **Cattle (Cow/Calf)** | Adult cows/bulls graze and regroup; eligible females reproduce and may support existing milk production; group warns/defends young. | **Calf** stays near mother/herd, feeds and follows, has reduced speed/endurance until mature. |
| **Sheep** | Graze as a cohesive flock, maintain spacing and shelter, react collectively to predators; adults contribute existing wool/husbandry outputs. | Lamb follows dam/flock, frequently regroups, flees poorly alone and needs protection. |
| **Dog** | **Owned:** recognize household/handler, patrol a permitted home range, bark at perceived intrusions, greet/follow/play and rest; obey a limited set of learned, permitted orders depending on trust. **Wild/feral:** seek food/shelter, scavenge, avoid or cautiously assess people, optionally cooperate loosely with familiar dogs. Neither adopts wolf-pack predator tactics. | **Puppy** seeks parent/caregiver, learns safe proximity, explores in short bursts, plays and retreats to shelter; no effective guard/hunt role. |
| **Cat** | Choose favored rest/perch and scratching sites, groom, explore safely, stalk permissible small prey or toys, assess threats and people, seek affection **on its own terms**; owned and unowned cats differ in home territory and tolerated humans. | **Kitten** plays, hides, sleeps frequently and seeks its mother/caregiver; feeding and safe shelter are high priorities, with limited range and no independent hunting. |


## 14.5 Starting clothing choices

These are **cosmetic**. The three columns can be mixed freely rather than treated as twenty pre-made outfits.

| Choice | Tops | Leg garments | Shoes |
|---:|---|---|---|
| 1 | Plain tunic | Straight trousers | Plain tall boots |
| 2 | Fitted everyday tunic | Fitted trousers | Laced tall boots |
| 3 | Belted tunic | Loose trousers | Folded-cuff tall boots |
| 4 | High-collar shirt | Drawstring trousers | Fitted riding boots |
| 5 | Fitted laced bodice | Knee-length shorts | Soft travel boots |
| 6 | Tailored fitted shirt | Rolled-cuff cropped trousers | Strapped tall boots |
| 7 | Cross-wrapped fitted top | Split-hem cropped trousers | Panelled tall boots |
| 8 | High-neck fitted top | Loose knee breeches | Tall pull-on boots |
| 9 | Open-collar shirt | Plain long skirt | Plain low shoes |
| 10 | Sleeveless waistcoat | Short overskirt | Pointed low shoes |
| 11 | Lace-up vest | Divided riding skirt | Rounded work shoes |
| 12 | Long open-front vest | Pleated skirt | Lace-up low shoes |
| 13 | Plain robe top | Plain robe bottom | Soft slip-on shoes |
| 14 | Layered over-tunic | Layered robe bottom | Ankle-height shoes |
| 15 | Monk-style robe top | Split-front robe bottom | Buckle-detail low shoes |
| 16 | Cross-over layered robe top | Wrapped monk-style bottom | Single-strap sandals |
| 17 | Loose pullover shirt | Leg-wrap leggings | Cross-strap sandals |
| 18 | Oversized tunic | Strapped-shin trousers | Ankle-tie sandals |
| 19 | Wide-sleeved shirt | Panelled greaves-look leggings | Open-toe wrap sandals |
| 20 | Asymmetrical draped top | Fitted reinforced-look leggings | High-laced open sandals |

## 14.6 Initial Gold-price anchors

Gold is a real economic resource; these are **initial neutral values** for complete undamaged reference equipment. They are not guaranteed shop prices, daily wages or minimum loot drops. The set column is the price of **Helmet + Platebody + Platelegs**, not a second item magically granted alongside the three pieces.

| Tier | Helmet | Platebody | Platelegs | Three-piece armor total | Sword |
|---|---:|---:|---:|---:|---:|
| Stone | 250 | 450 | 300 | 1,000 | 50 |
| Bronze | 500 | 900 | 600 | 2,000 | 180 |
| Chainmail | 1,250 | 2,250 | 1,500 | 5,000 | 450 |
| Iron | 4,000 | 7,200 | 4,800 | 16,000 | 1,100 |
| Black | 13,750 | 24,750 | 16,500 | 55,000 | 5,000 |
| White | 13,750 | 24,750 | 16,500 | 55,000 | 5,000 |
| Steel | 40,000 | 72,000 | 48,000 | 160,000 | 12,000 |
| Granite | 375,000 | 675,000 | 450,000 | 1,500,000 | 28,000 |
| Cobalt | 2,750,000 | 4,950,000 | 3,300,000 | 11,000,000 | 45,000 |
| Dragon | 12,500,000 | 22,500,000 | 15,000,000 | 50,000,000 | 65,000 |
| Molten | 41,250,000 | 74,250,000 | 49,500,000 | 165,000,000 | 500,000 |
| Godly | 215,000,000 | 172,000,000 | 98,000,000 | 485,000,000 | 8,000,000 |
| Malevolent | 312,500,000 | 562,500,000 | 375,000,000 | 1,250,000,000 | 30,000,000 |

The prestige anchors are deliberate: **Dragon armor set 50 million Gold**, **Godly armor set 485 million Gold**, and **Malevolent armor set 1.25 billion Gold**. A **Dragon Sword** is valued at **65,000 Gold**, much less than the complete armor set; the player is not meant to assume equal scarcity just because items carry the same tier label. Ordinary towns must not stock infinite amounts of luxury equipment or unlimited money to purchase it.

Other items including gems, ore, potions, food, tools and estate materials should have reasonable named prices, and player/NPC activity should make those prices possible to earn. High Honor affects *eligible purchase prices*, not these reference values.

## 14.7 First Construction rooms and exact early recipes

| Construction level | Room or item | Experience and purpose |
|---:|---|---|
| **1** | Parlor | First basic owned room with simple furniture |
| **1** | Crude Wooden Chair | First and lowest-yield furnishing; costs **2 planks + 2 nails** with hammer and saw |
| **To decide** | Garden | Can serve as entrance or backyard; costs **10 iron bars + 10 soil** |
| **5** | Kitchen | Home Cooking fixtures |
| **10** | Dining Room | Household dining/social space |
| **15** | Workshop | Tool, repair and training furnishings |
| **20** | Bedroom | Rest and basic household accommodation |
| **92 effective** | Utility-complete high-end estate | Reaches intended advanced utility offerings; Construction **84 + a valid temporary +8 boost** may qualify for construction |

When a temporary Construction boost fades, already completed rooms keep working. Construction 100 remains a mastery goal, not a prerequisite for every useful home fixture. Materials for unspecified rooms are **To decide**.

## 14.8 Honor's established purchase discounts

| Minimum Honor | Rank | Qualifying merchant purchase discount |
|---:|---|---:|
| +33 | Honorable | **5%** |
| +55 | Virtuous | **10%** |
| +77 | Exemplary | **15%** |
| +89 | Revered | **20%** |

Only the highest applicable discount is used. They do not stack and do not apply to the player's selling price. The shop must still have the item, honor the legal transaction and respect a sustainable minimum offer. **Negative Honor grants no special shop discount or exclusive outfit**; its distinctive consequences concern social responses, particular story opportunities and eligible corpse-loot tendencies.

## 14.9 Design documentation references

The document was rewritten around the **player fantasy, core loop, feature explanation, concrete examples and explicitly unresolved design choices**. That approach follows modern GDD practice, where readers should understand *what the game is* and *how it plays* before confronting implementation detail. There is no single universal GDD format.

- [Game Developer: *How To: Write a Game Design Document* (2023)](https://www.gamedeveloper.com/design/how-to-write-a-game-design-document) emphasizes readable cross-disciplinary vision, a concise pitch, meaningful player-experience goals and searchable system descriptions.
- [Nuclino: *Game Design Document Template and Examples* (updated 2026)](https://www.nuclino.com/articles/game-design-document-template) uses overview, gameplay, mechanics, world/story and assets, and recommends keeping documentation modular and maintainable.
- [GitBook: *How to write a game design document* (updated 2026)](https://www.gitbook.com/blog/how-to-write-a-game-design-document) recommends moving from high-level vision and core loop to individual systems, rules and unresolved questions.

**Document principle:** When a design changes, update the relevant explanation and catalog. Avoid leaving a retired rule as an equally authoritative alternative elsewhere. Detailed asset specifications, developer test scripts and code contracts belong in separate technical documents, not at the center of this player-facing conceptual GDD.
