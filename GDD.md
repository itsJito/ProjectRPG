# RPGGAME
## Game Design Document

**Design status:** Definitive design baseline · **Version:** 3.21 · **Engineering handoff:** reusable-asset devmap and acceptance tests in §4.2; prototype contracts in §3.2 and §3.8; animal ecosystem and companion animals in §3.9; Magic/Faith in §2.5; stealth in §3.10; Honor in §3.5; estates/Construction, collections and estate activities in §3.11; player death and recovery in §3.12; cross-system causality and integration tests in §3.13; unified item definitions, 3D inventory/world assets and initial Gold-price anchors in §2.7
**Genre:** Single-player, classless, open-world fantasy RPG  
**Technology:** Astratto Engine; voxel environments; adapted *Absentialis* character models  
**World:** GAMEWORLD (working title) · **Approach:** Mechanics first; authored world and narrative built around systems  
**Primary readers:** Game design, gameplay engineering, AI/simulation, UI, narrative, economy, art/animation and QA · **Source of truth:** Rules in each system section; numeric values marked Open remain tuning tasks.

> **Document rules:** This is the current design specification, not a discussion history. **Established** means agreed design; **Open** means a specific rule or numeric value is not settled; **Target** means a planned outcome needing validation. Proposed examples do not confer approval. Preserve the four primary sections. When rules change, replace the obsolete rule in place rather than appending another version.

---

# 1. Product Vision and Player Experience

	## 1.1 High concept

	RPGGAME is a traditional open-world fantasy RPG in which one classless human adventurer develops eighteen permanent skills, acquires increasingly prestigious equipment, discovers authored quests, and participates in an autonomous, reactive society. The world is dangerous, geographically interconnected, and does **not** scale enemy levels to the player. Advancement is long-term and meaningful, while direct combat, exploration, and ordinary world interactions must be rewarding on their own.

	**Core promise:** Progression gives the player goals; simulation gives the world independent life; authored stories give it identity; their interaction produces replayable, consequential situations.

	This is a **handcrafted RPG with systemic reactivity**, not a procedurally authored storyline generator, kingdom-destruction simulator, or endless survival grind.

	## 1.2 Design pillars

	| Pillar | Implementation rule |
	|---|---|
	| Earned progression | Levels, equipment, knowledge, property, and rare items retain value over time. |
	| Freedom of activity | Skills, exploration, quests, combat, trade, crafting, crime, and social play support self-directed goals; no mandatory class path. |
	| Skillful physical combat | Direct, omnidirectional movement, precise contact, meaningful defensive actions, strong audiovisual feedback, and weapon-specific technique. |
	| A dangerous, fixed world | Threats do not level-scale; superior development should make formerly dangerous enemies easier. |
	| Authored discovery | Main characters, significant quests, major conflicts, and narrative branches are deliberately written, then made responsive to world state. |
	| Reactive society | NPCs maintain routines, work, remember, share information, and affect the economy and law. |
	| Cohesive systems | The same authoritative state links items, ownership, witnesses, markets, quests, factions, and character progression. |
	| Convenience without trivialization | Reduce interface friction, not meaningful preparation or achievement. |
	| Replayability through consequences | Relationships, commerce, faction influence, and local opportunities evolve even without an active player quest. |
	| A lived-in home | Estates reward Construction and collecting with real utility, pet care, safe practice, and personalized spaces without bypassing world-state rules. |
	| Earned moral identity | Honor records morally meaningful actions, creates perceptible social and reward consequences, and never replaces individual NPC knowledge or the law. |

	## 1.3 Player loop and progression arc

	**Core loop:** Discover opportunity → prepare → undertake combat, skill work, questing, trade, or social action → earn XP, resources, wealth, access, or knowledge → change personal capability/world state → pursue a new goal.

	Parallel activities include gathering-to-production, wealth-building, Treasure Trails, boss progression, authored questlines, exploration, crime and legal consequences, collections, and housing. No single loop must dominate.

	**Early:** Meaningful vulnerability, practical local quests, modest equipment, and discoverable danger.  
	**Middle:** More skill methods, accessible properties, valuable trade, wider routes, specialization, and stronger encounters.  
	**Late:** Advanced abilities, Dragon/Molten/Godly/Malevolent gear, demanding PvM, extensive questlines, political/faction influence, and prestigious collections.  
	**After completion:** The simulated world continues generating situated choices and consequences; no guarantee of infinite novelty or content.

	## 1.4 Product boundaries and release scope

	- **Established:** Single-player baseline; 18 skills; no fixed classes; direct combat; three kingdoms minimum in the first playable build, with two additional kingdoms planned after systems are proven.
	- **Established:** Optional social/noble progression must never gate ordinary adventuring, skills, general PvM, or basic exploration.
	- **Established:** World disruption is meaningful but scoped: the player can harm NPCs, commerce, property, relationships, and local interests, not freely delete whole kingdoms through unrestricted systemic destruction.
	- **Devmap purpose:** The first engineering environment is a neutral **developer test map (devmap)**, not a narrative vertical slice, town, or kingdom. **The devmap is where the reusable core game and its production-intent assets are built**, not a throwaway proof of concept. Its terrain arrangement and debug stations can be artificial, but any character model, animation, texture, object, interaction setup, or gameplay component intended for the shipped game must be built so it can be transferred into the eventual world without being recreated. **All established gameplay systems begin development here**, prioritized by risk: **1. leveling/all 18 skills; 2. real-time combat; 3. intelligent NPC AI.** Lore, kingdoms, major characters and authored stories develop in parallel; neither worldbuilding nor finished level design is a prerequisite for reusable systems and assets.
	- **Open:** Release platforms, optional co-op, total world size, story premise, map, culture, history, major characters, and final volume of content.

# 2. Character, Progression, Combat, and Items

	## 2.1 Character creation — base appearance and starting clothes

	**Scope — established:** The character creator designs only the player's **base human appearance** and **cosmetic starting outfit**. It does **not** select, award, or configure armor tiers, combat weapons, skills, equipment bonuses, or a class. Creator garments are appearance layers separate from the Head, Torso, Legs, Boots and other functional equipment slots defined in §2.4; equipping actual gear must respect the usual equip/visual override rules without granting creator clothing any protective or combat effect. All creator looks are available from the start; their option numbers are **appearance IDs**, not levels or items.

	The player uses the adapted *Absentialis* model foundation. Character and outfit assets created on the devmap are **production-intent and transferable**: compatible reusable rigs, meshes sized for all three bodies, skin/material tint slots, hair and facial-hair anchors, modular garments, deformation, clipping checks, camera visibility, and combat/interaction animation compatibility. The devmap needs a representative high-quality implemented sample; production of every variation can continue without blocking core systems.

	**Prototype entry presentation:** Version 0.1.2 opens with the bundled Astratto Engine logo fading in, holding for four seconds, then fading out. The title and creator share a separate dark dungeon preview, with the most recently played appearance on the title and the engine logo in its footer. Settings apply through the same panel as in play. The creator has one starting appearance: **Henric**, male/masculine, Skin 1, bald with dark-brown goatee and moustache, olive open-collar shirt, brown fitted trousers, black rounded work shoes. Catalog editing and randomization remain available; the preview rotation can be paused. This preview never edits the playable world.

	### Creator fields and limits — established

	| Field | Available choices | Rule |
	|---|---|---|
	| **Sex** | **Male; Female** | Two selectable sex presets. Sex determines which of the two authored hair catalogs is displayed. |
	| **Body** | **Masculine; Heavy; Skinny** | Exactly three body silhouettes/presets, selectable independently of hairstyle and clothing. These are visual only. |
	| **Skin** | **12 realistic tones** | Retain the pre-established twelve-tone range; these are authored tones, not recolors from the garment palette. |
	| **Hair** | **20 Male variants; 20 Female variants** | Exactly twenty distinct authored choices **per sex**; forty total assets/presets maximum. |
	| **Facial Hair** | **20 variants** | Includes Clean-shaven; a separate catalog from head hair. Facial-hair fit/availability follows supported character mesh/rig presentation. |
	| **Tops** | **20 cosmetic styles** | Clothing silhouette only; not torso armor or gear. |
	| **Legs** | **20 cosmetic styles** | Clothing silhouette only; greaves-inspired looks are **not** leg armor. |
	| **Shoes** | **20 cosmetic styles** | Clothing silhouette only; boots do **not** give defense or occupy an extra slot. |
	| **Hair color** | **10 natural shades** | One natural-only palette shared by head hair and facial hair; independently selectable colors where facial hair is visible. No fantasy or dyed colors. |
	| **Starting outfit colors** | **24 recognizable swatches** | A separate, fixed palette for cosmetic Tops, Legs and Shoes. Each swatch has its own name, ID and reference color; no duplicate aliases or near-identical options. **Skin retains its own 12 realistic tones.** |

	### Creator color palettes — established

	**Separate catalogs:** **Hair and facial hair use only the 10 natural hair colors below.** Tops, Legs, and Shoes use only the **24 garment colors** below. Skin retains its established 12 realistic skin tones; skin tones are never garment colors. Each color uses a stable selection ID and a visually distinct authored swatch. The listed hex values are **sRGB design references** for neutral preview lighting, not a requirement that physically shaded surfaces appear flat. Keep recognizability and separation under ordinary in-game lighting. Do not pad the count with synonyms, indistinguishable shades, or rebranded duplicates.

	**Hair / facial hair — 10 natural colors**

	| ID | Color | Reference swatch |
	|---:|---|---|
	| H01 | Black | `#201A19` |
	| H02 | Dark Brown | `#39271F` |
	| H03 | Medium Brown | `#68452E` |
	| H04 | Light Brown | `#9B7451` |
	| H05 | Chestnut | `#79402D` |
	| H06 | Auburn | `#8D3723` |
	| H07 | Natural Red | `#BE6030` |
	| H08 | Strawberry Blonde | `#CE9464` |
	| H09 | Dark Blonde | `#AA9167` |
	| H10 | Light Blonde | `#E4C78F` |

	**Starting clothes — 24 distinct, recognizable colors**

	| ID | Color | Reference swatch | ID | Color | Reference swatch |
	|---:|---|---|---:|---|---|
	| C01 | Red | `#D12C36` | C13 | Magenta | `#C83F9F` |
	| C02 | Orange | `#F08424` | C14 | Pink | `#EF97C3` |
	| C03 | Yellow | `#EAD43A` | C15 | Coral | `#ED6C5C` |
	| C04 | Lime | `#9ACF34` | C16 | Burgundy | `#7E2A45` |
	| C05 | Green | `#278B49` | C17 | Brown | `#73442F` |
	| C06 | Olive | `#707837` | C18 | Rust | `#AD5331` |
	| C07 | Teal | `#1A827B` | C19 | Gold | `#C9A12D` |
	| C08 | Cyan | `#35BEDA` | C20 | Tan | `#C69A6D` |
	| C09 | Blue | `#3570D6` | C21 | Beige | `#D8C4A3` |
	| C10 | Navy | `#283B7B` | C22 | Gray | `#898C92` |
	| C11 | Purple | `#793DAF` | C23 | White | `#F4F2E8` |
	| C12 | Lavender | `#B69EE3` | C24 | Black | `#1B1C20` |

	**Customization behavior:** Each eligible starting garment can independently select one of C01–C24. Hair and visible facial hair independently select H01–H10, with a matching facial-hair color as the initial default where applicable. Bald and clean-shaven styles do not display hair color on the absent mesh; they should not produce an unintended tint or extra cosmetic option. No hair dye, gradients, exotic shades, or additional color-category UI is implied. The same selected IDs, colors, and material-channel behavior must persist after save/load and transfer from devmap to production scenes. Garment colors remain strictly cosmetic and confer no properties or equipment-tier effects.

	### Hair — 20 unique variations per sex

	Presets cover short, medium, long and specialty appearances. Similar style names can exist in both catalogs, but **male and female hair models are separately authored variants** fitted to their respective character meshes.

	| # | Length group | Male | Female |
	|---:|---|---|---|
	| 1 | Short | Bald | Bald |
	| 2 | Short | Close-cropped | Close-cropped pixie |
	| 3 | Short | Crew / buzz cut | Short bob |
	| 4 | Short | Spiky textured crop | Spiky textured pixie |
	| 5 | Short | Short swept-back | Swept-back crop |
	| 6 | Short | Short curls | Short curls |
	| 7 | Medium | Side-part | Side-part long bob |
	| 8 | Medium | Curtains | Curtain-fringe shoulder cut |
	| 9 | Medium | Ear-length layered | Shoulder-length layers |
	| 10 | Medium | Mid ponytail | Half-up mid ponytail |
	| 11 | Medium | Top-knot / man-bun | Top-knot |
	| 12 | Medium | Medium tousled waves | Medium loose waves |
	| 13 | Long | Straight long | Straight long |
	| 14 | Long | Wavy layered long | Wavy layered long |
	| 15 | Long | High ponytail | High ponytail |
	| 16 | Long | Single long braid | Single long braid |
	| 17 | Long | Dual braids | Dual braids |
	| 18 | Long | Long dreadlocks | Long dreadlocks |
	| 19 | Specialty | Mohawk | Mohawk |
	| 20 | Specialty | Undercut / half-shaved | Side-shaved long cut |

	### Facial hair — 20 choices

	These options are visual styles, not personality, ancestry, status or character-stat selections. The clean-shaven option removes facial hair; moustache, sideburn and beard variants use the same stable face/rig attachment rules.

	| # | Facial-hair preset |
	|---:|---|
	| 1 | Clean-shaven |
	| 2 | Light stubble |
	| 3 | Dense stubble |
	| 4 | Short boxed beard |
	| 5 | Medium boxed beard |
	| 6 | Long full beard |
	| 7 | Short rounded full beard |
	| 8 | Long tapered beard |
	| 9 | Bare goatee |
	| 10 | Goatee with moustache |
	| 11 | Pointed goatee |
	| 12 | Van Dyke beard |
	| 13 | Thin pencil moustache |
	| 14 | Chevron moustache |
	| 15 | Handlebar moustache |
	| 16 | Horseshoe moustache |
	| 17 | Full walrus moustache |
	| 18 | Mutton-chop sideburns |
	| 19 | Short sideburns only |
	| 20 | Long sideburns only |

	### Starting outfit — 20 tops, 20 legs, 20 shoes

	Each of the three outfit categories has **exactly twenty independent choices**; table rows do **not** denote fixed three-piece sets. Mix any compatible top, leg garment and footwear. The catalog follows the requested grounded fantasy silhouettes: tunics/shirts, bodices/fitted tops, vests/open layers, robes, oversized tops; trousers, cropped bottoms, skirts, robe bottoms and decorative greaves-inspired styles; tall boots, low shoes and sandals. **All are cosmetic base clothes**, not newly added protective equipment, items, recipes or material tiers.

	| # | Tops (20) | Legs (20) | Shoes (20) |
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

	**Presentation and consistency:** Creator choices preview live on the selected sex/body mesh, preserving the same silhouette and color settings when saved/reloaded and transferred to the production world. Clothing pieces should use consistent modular attachment and material conventions, hide/clamp covered geometry where needed, and avoid robe/top/leg/boot interpenetration during locomotion and combat. Hair, facial hair, skin, and outfit choices persist as **appearance data** rather than detachable gameplay items. The production team may solve clipping with authored compatibility/rigging variants, but **must not silently replace a player's selection**. No additional cosmetic slot categories are created here.

	**Open:** Post-creation appearance editing, restrictions arising from facial-hair rig compatibility, and starting inventory contents. The **24 garment colors, 10 natural hair colors, stable IDs, and catalog boundaries are established**; the asset team may adjust material calibration only if swatches remain recognizable and distinctly separated.


	## 2.2 Skill system

	All **18 player skills** independently earn XP and cap at level 100. Human NPCs and animals use fixed skill profiles that determine their Level and capabilities; their actions never award XP or raise skills or Level. **Starting character exception:** Health (also called **Hitpoints** in the combat-level formula) starts at **level 10**, with **10 maximum HP and 10 current HP**; the other **17 skills start at level 1**. Health starts with the cumulative XP threshold corresponding to level 10, not an inconsistent level-10/zero-XP state. Primary skill categories describe their usual role, not exclusive use.

	| Category | Skill | Defined function |
	|---|---|---|
	| Combat | Attack | Speed and reliability of **player-controlled consecutive melee actions**: recovery, chaining, timing and weapon handling; **never changes hit chance or maximum HP damage** |
	| Combat | Strength | **Maximum melee HP damage**, physical force, lifting, impact, stagger, and sword-tier eligibility |
	| Combat | Defense | Better execution of **active** dodges, blocks, parries and guard control; armor-tier eligibility; **no passive evasion rolls; armor reduces the maximum HP damage before rolling** |
	| Combat | Health | Maximum HP and endurance; detailed recovery rules open |
	| Combat | Archery | Projectile offense and relevant ranged effectiveness |
	| Combat | Magic | Four unlockable spellbooks (Standard, Ancients, Lunar, Arcane); Mana-powered combat, elemental interactions, and real-world utility effects |
	| Combat | Faith | Persistent Faith-point resource for toggled prayers: overhead attack-type protection, offensive/stat boosts, and spiritual practice |
	| Gathering | Woodcutting | Timber and wood resource extraction |
	| Gathering | Mining | Ore, minerals, and stone extraction |
	| Gathering | Fishing | Aquatic resource gathering |
	| Gathering | Harvesting | Plants, herbs, crops, and applicable natural gathering |
	| Production | Cooking | Meals, food, and preparation consumables |
	| Production | Crafting | Manufactured goods, equipment, and limited item improvement |
	| Production | Smithing | Metal processing, forging, and limited equipment improvement |
	| Production | Construction | Housing, furnishing, and supported building work |
	| Utility | Agility | Movement/endurance efficiency and traversal capabilities |
	| Utility | Firemaking | Fire, ignition, fuel, camps, and heat-related interaction |
	| Utility | Thieving | Theft, lockpicking, concealment, and illicit access |

	**Cross-system XP source:** The same action-result events used by combat, gathering, cooking, crime, economy, ecology and estate tasks determine eligible XP; §3.13 specifies multi-skill attribution and forbids XP from duplicated side effects.

	**Training — naturally earned XP:** The player gains XP by **actually accomplishing skill-relevant actions in the persistent world**—hitting a real opponent, cooking an actual food item, cutting an available resource, completing a repair, using a spell to affect a real target, or traversing meaningful terrain. The five qualitative reward tiers for every skill identify **five gameplay-grounded activity categories**, not five mandatory minigames or rigid XP payouts. **No action has a fixed global XP award.** Its earned XP varies by the specific recipe/item, target/resource, difficulty, risk, commitment, amount of real work completed, and appropriate skill balancing; numerical values are **open**. A high-tier act usually yields more XP **per completed act** than a lower-tier act in comparable conditions, but may take longer or consume more resources. XP never comes merely from opening a menu, purchasing an item, standing idle, reusing an unchanged state, or playing an animation without a valid result. Melee hits legitimately train Attack and Strength; **all real hostile HP damage suffered awards Defense XP**, and successful active defenses can train it too. Health has its own bounded exertion/endurance contributions. Magic XP credits legitimate spell effects and damaging hits; Faith XP credits meaningful prayer-assisted combat, real protection, and existing spiritual practices rather than idle prayer drain. Quest/minigame XP lamps remain separate, explicitly authored rewards.

	**Level-up feedback:** Immediate automatic level increase, recorded jingle and fireworks. Level advances and quest acceptance/completion/failure share one bounded top-center notice queue, with a brief fade, skill/quest icon and newly unlocked actions. Notices reserve space beneath an active Honor toast instead of overlapping it. Skill mastery at **100** permits purchase of a corresponding **skillcape**.

	### Universal XP thresholds — established

	All skills use one cumulative XP schedule: **6,000,000 XP at level 100** (**108,000,000** to max all eighteen). The curve becomes progressively more demanding after level 60 while remaining suitable for a single-player game.

	Let `i` be a level transition index from 1 to 99:

	```text
	weight(i) = 1.04^(min(i, 59) - 1) * 1.05^(max(0, i - 59))
	XP(level) = round(6_000_000 * sum(weight(i), i=1..level-1)
	                  / sum(weight(i), i=1..99))
	XP(1) = 0
	XP(100) = 6_000_000
	```

	Precompute and store **integer level thresholds** as runtime authority, including fixed NPC and animal profile initialization. **New-game initialization:** Health starts with **43,446 cumulative XP** (the established level-10 threshold), while each other skill begins with **0 XP** at level 1. This is a starting condition, **not** XP awarded through an action or reflected as a level-up. The per-skill level-100 cap remains **6,000,000 cumulative XP**. Future HP growth is governed by Health progression; its detailed scaling is open beyond the established 10 HP starting value.

	| Level | Cumulative XP | Level | Cumulative XP |
	|---:|---:|---:|---:|
	| 1 | 0 | 60 | 935,503 |
	| 10 | 43,446 | 65 | 1,167,164 |
	| 20 | 113,599 | 70 | 1,462,828 |
	| 30 | 217,444 | 75 | 1,840,178 |
	| 40 | 371,159 | 80 | 2,321,784 |
	| 50 | 598,694 | 85 | 2,936,448 |
	| 90 | 3,720,933 | 95 | 4,722,157 |
	| 100 | 6,000,000 | | |

	**Open:** Numerical XP rewards, per-item/recipe/resource target values, XP/hour, combat XP budgets, reward multipliers, method prerequisites, repeat-action eligibility, recovery scaling, post-cap XP treatment, and quest-lamp sizes. The five-tier catalogs below are **relative reward rankings**, not numerical commitments. Shared XP thresholds do **not** require equal training speed across skills.

	### Open-world XP framework — five natural training routes per skill [established design; numerical tuning open]

	**Purpose.** Each of the **18 skills** has **five distinct, recognizable sources of XP**, arranged from typically **Lowest (I)** to **Highest (V)** reward **per meaningful completion**. These represent gameplay already present in RPGGAME—combat, terrain, gathering, production, NPCs, supported workstations, spells, property, law and the persistent ecology—not isolated training courses, invented items, or extra minigames. The five rows are a **design classification, not an exhaustive whitelist**: any later-supported real act of the same skill must be classified under a suitable existing route or be explicitly designed and balanced. Progression from 1 to 100 must remain viable through ordinary adventuring and work; Tier V is not mandatory.

	| Relative tier | **I · Lowest** | **II · Lower** | **III · Moderate** | **IV · High** | **V · Highest** |
	|---|---|---|---|---|---|
	| Meaning | Common, low-demand contribution | Routine trained work | Materially harder or more involved work | Advanced work or a demanding real encounter | Exceptional, difficult, resource-intensive accomplishment |

	**Variable award rules:**

	- **The actual completed thing determines XP.** A food's **recipe definition** and result determine Cooking XP—not whether it was made on a stove versus a fire. For gathered resources, use the actual node/yield and extraction requirements; for crafting, use the actual output and consumed inputs; for combat, use the legitimate hostile encounter, resulting hit/effect, and supported skill contribution. A method row is not itself a fixed reward.
	- **Pay on authoritative results.** Skill XP is linked to a committed event recording `eventId`, `actorId`, `skillId`, `routeId`, `resultDefinitionId`, `work/target context`, and **computed** `xpAward`. Do not invent a second item, wound, target or transaction to issue XP. A batch pays for its real completed outputs/effort once; a final building completion cannot re-pay all previously credited parts.
	- **No duplicated same-skill awards.** When one committed act matches multiple routes, apply the **most appropriate highest-qualified route once**. Distinct steps with distinct world-state outcomes can separately earn XP. Supporting skills may receive smaller justified shares of one bounded event budget; merely using an item from another skill does not automatically award that skill XP.
	- **Every genuine hostile damaging hit trains Defense** (regardless of defensive input), and damage/impact from real melee hits trains Strength while connected melee execution trains Attack. The same event can legitimately train Health where its conditions are met, without unrestricted overlapping rewards. Physical collision, not a hit-chance roll, decides contact. Failed attacks, fake defenses, self-injury, harmless mutual hit loops and artificial repeat encounters do not provide farmable XP.
	- **Prevent state-reset farming.** Reopening/relocking the same door, placing the same furnishing repeatedly, tending already-satisfied crops, repeatedly igniting an unchanged fire, healing fictitious damage, retreading a short obstacle or save/reload retries must not mint XP. Repeating **genuine** work remains valid; rewards reflect new resource consumption, changed world state, effort, credible danger, and limits on trivial recurrences.
	- **Tier ranking is not XP/hour ranking.** The best per-action reward can be impractical to farm. Lower/mid-tier methods remain useful. High-level XP should come from more productive or demanding naturally occurring opportunities, not compulsory scripted challenges. Player and simulated NPC work resolve against the **same item/resource ledger and action prerequisites**; only the player's own verified actions award XP. NPC work leaves configured skills unchanged.

	#### Combat — 7 skills

	**Attack — successful melee execution and chaining**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Land a regular melee strike | A real weapon hit connects with an eligible opponent during normal fighting; XP responds to legitimate target and encounter context. |
	| II · Lower | Land a successive melee-chain hit | Another real hit lands inside the equipped weapon's actual follow-up window, requiring continued offensive control. |
	| III · Moderate | Exploit an opponent's missed swing | Physically connect while an enemy is recovering from a missed committed attack. |
	| IV · High | Counterattack after a successful active defense | Land the follow-up strike after actually blocking, parrying or dodging a threatening attack. |
	| V · Highest | Connect with a weapon-specific special | A committed special physically connects with a real hostile target; its difficulty, risk and resources shape the award. |

	**Strength — force applied to the world and melee HP damage**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Lift, push or reposition a meaningful physical object | Complete a supported force interaction with a real world object; no inventory-weight mechanic is implied. |
	| II · Lower | Damage an opponent with an ordinary melee hit | A valid connected strike actually removes HP; XP can vary with real resulting offense and encounter context. |
	| III · Moderate | Connect with a committed heavy melee move | A registered slower/higher-force attack hits and deals damage, exposing the attacker longer. |
	| IV · High | Cause a legitimate Stability stagger | The real impact of an attack exceeds the target's current Stability; repeat-stagger safeguards apply. |
	| V · Highest | Land a demanding, high-impact weapon special | A valid offensive special damages the opponent and meets its intended impact condition. |

	**Defense — incoming harm and performed avoidance**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Receive actual hostile HP damage | **Always** credit qualified damage taken, even without a defense action; ignore self-inflicted/exploit damage. Amount and threat can vary XP. |
	| II · Lower | Stop an incoming attack with a weapon guard | The performed guard blocks a real incoming strike; not a defensive animation against empty space. |
	| III · Moderate | Block a real attack with the Left Hand shield | A valid shield defense succeeds within its actual direction, resource and timing constraints. |
	| IV · High | Evade a threatening strike with an active dodge | The player dodges a genuinely committed attack that would have threatened their collision volume. |
	| V · Highest | Execute a correctly timed parry | A physical incoming attack is actually deflected in the parry window. |

	**Health — exertion, endurance, recovery and survival**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Complete meaningful exertion during travel or work | A real sustained stamina-demanding traversal/work interval finishes; routine idle walking is not an XP loop. |
	| II · Lower | Endure legitimate injury during danger | An actual hostile HP-loss event is resolved; it may also give bounded Defense XP. |
	| III · Moderate | Restore genuinely missing Health through supported recovery | Consume valid recovery resources or complete an existing recovery action; no full-HP or manufactured-damage payouts. |
	| IV · High | Survive a genuine hostile encounter after injury | Reach the real end of an encounter after meaningful exertion and injury; once per encounter, not per enemy animation. |
	| V · Highest | Survive a high-threat encounter while seriously depleted | Complete a genuinely demanding enemy/boss encounter under validated injury/endurance conditions, replacing the lower survival reward. |

	**Archery — physically successful ranged attacks**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Hit a target with a thrown Dart or Knife | A real trajectory collides with eligible wildlife or a hostile target. |
	| II · Lower | Hit a target with a Tomahawk or Javelin | A committed thrown weapon connects at valid range; item/target difficulty affects the award. |
	| III · Moderate | Land a loaded Crossbow projectile | A compatible fired projectile actually makes contact; no artificial accuracy roll. |
	| IV · High | Strike a target that is actively moving or evading | Any supported ranged family connects under verifiable target movement rather than stationary contact. |
	| V · Highest | Hit a real exposed vulnerability or combat opening | Projectile impact exploits an authored weak point or timed opening on a dangerous target. |

	**Magic — spell effects that actually affect combat or the world**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Complete a useful Lunar enhancement | A real enhancement (for example, Enhance Ability) changes a subsequent eligible action; activating and canceling without effect does not pay. |
	| II · Lower | Alter an NPC's actual perception with an Arcane spell | Darkness or Minor Illusion produces a verified perception/decision change, not an unobserved visual effect spam loop. |
	| III · Moderate | Cross an otherwise meaningful obstacle with Lunar movement magic | Spiritual Step or Gaseous Form genuinely changes traversal and resolves a valid destination/obstacle. |
	| IV · High | Successfully strike a hostile target with eligible Standard/Ancients combat magic | Real spell contact and authorized damage/effect resolve; cast cost alone gives no XP. The target and effect difficulty set variable reward. |
	| V · Highest | Change a substantial encounter through eligible Lunar/Arcane social or identity magic | Friends, Charm Person, Disguise Self or Speak With Animals affects a real eligible interaction; no automatic success or instant global knowledge. |

	**Faith — meaningful prayer-assisted protection and spiritual practices**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Bury a genuinely acquired eligible bone | Consume or commit the real resource once through the existing burial action. |
	| II · Lower | Receive a meaningful offensive benefit from an active stat prayer | An actual successful eligible strike or attack uses the relevant boosted stat; XP tracks verified contribution, never mere toggling or upkeep. |
	| III · Moderate | Protect against a qualifying incoming attack with an active overhead prayer | Protect from Melee, Missiles or Magic genuinely prevents the matching threat; one incoming event is credited once. |
	| IV · High | Sustain effective prayer use through a demanding real encounter | Valid prayer-assisted offense or defense across a consequential fight; no extra XP solely for seconds active and no repeated payout for the same event. |
	| V · Highest | Resolve a demanding supported spiritual purification | Remove a genuine authored spiritual affliction/condition if such a valid target exists; the activity is not a prerequisite to train Faith and does not imply invented creatures/items. |

	#### Gathering — 4 skills

	**Woodcutting — physical changes to vegetation and timber**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Clear usable vegetation with a Machete | A real eligible vegetation node is cut/cleared rather than a repeated empty swing. |
	| II · Lower | Split actual harvested Logs into firewood | Existing wood is transformed into usable fuel with Axe/Hatchet work. |
	| III · Moderate | Fell a smaller accessible tree | The available tree stock is legitimately harvested with a suitable Axe/Hatchet. |
	| IV · High | Fell a larger, tougher working tree | An objectively more demanding existing timber node is felled, with appropriate work and yield. |
	| V · Highest | Carry out a demanding controlled tree fell | A difficult tree/resource is successfully cut under meaningful terrain, fall-zone or managed-forest conditions; no job enrollment needed. |

	**Mining — extraction from real geology/resource nodes**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Excavate workable clay | A real clay source loses stock and supplies a usable existing material. |
	| II · Lower | Cut usable quarry stone | A valid quarry face yields an actual building resource. |
	| III · Moderate | Extract usable coal | A genuine fuel seam yields stock; deposit/work difficulty governs variation. |
	| IV · High | Extract ore from a metal-bearing vein | A valid Pickaxe and sufficiently developed real vein produce material stock. |
	| V · Highest | Work a difficult gem-bearing deposit | A real higher-difficulty gem-bearing resource yields an eligible result, not a randomly awarded XP bonus. |

	**Fishing — catching real available aquatic resources**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Recover a small-net catch | A matching fishing site yields a real catch using the Small Fishing Net. |
	| II · Lower | Land a catch using a fishing rod | Real fish are caught with an eligible Fishing, Fly, Barbarian or Oily Rod and any required Bait/Feathers; the particular catch drives XP. |
	| III · Moderate | Make a successful harpoon catch | A matching site yields a real result using the Harpoon; difficulty reflects the catch and effort. |
	| IV · High | Retrieve a productive Lobster Pot | A genuinely deployed pot produces an available catch; placement alone is not a completed catch. |
	| V · Highest | Recover a demanding big-net haul | The Big Fishing Net returns actual available catch stock; XP follows genuine yield/effort, not fake per-batch duplication. |

	**Harvesting — productive field and plant care**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Prepare an unready growing plot | Rake/Spade work genuinely changes soil/plot state. |
	| II · Lower | Plant an eligible seed or sapling | A Seed Dibber/Trowel/Spade task consumes real planting stock and establishes a supported plant state. |
	| III · Moderate | Water plants that actually need it | A Watering Can improves real moisture state; repeated watering at saturation pays nothing. |
	| IV · High | Perform needed pruning or comparable plant care | Secateurs resolve a real maintenance condition that improves supported growth/health. |
	| V · Highest | Harvest a mature crop, herb or supported yield | Actual ready yield is removed from the world and transferred; crop/plant type and maturity determine XP. |

	#### Production — 4 skills

	**Cooking — success depends on the *food prepared*, not the workstation**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Successfully cook an introductory food recipe | A simple eligible food ingredient becomes its real cooked output at a Fire or Stove. |
	| II · Lower | Successfully cook a more demanding single-food recipe | An actual fish, meat or harvested food with a higher recipe difficulty is cooked; **food/recipe type**, not fish-versus-meat or fire-versus-stove, controls XP. |
	| III · Moderate | Successfully prepare a supported combined-ingredient meal | Its registered ingredients are consumed and a completed meal is created once. |
	| IV · High | Successfully finish an advanced multi-step food recipe | A real more involved Cooking recipe produces food, with required ingredients and work actually completed. |
	| V · Highest | Successfully cook a top-difficulty supported dish | The eligible high-skill recipe produces its real final food output; ingredient value, complexity and recipe definition govern a larger variable reward. |

	**Cooking-specific rule:** **XP is computed from the successfully prepared food definition and verified completion.** Cooking the *same* food on a Fire or Stove uses the same recipe-derived XP unless a separately approved recipe property genuinely changes the result. Recipe batches award XP only for actual completed edible outputs; changing station, wearing a Chef's Hat, or cooking multiple portions does not magically reclassify the food. Burnt/failed attempts do not award successful-cook XP. Exact recipe items and numbers remain open.

	**Crafting — converting real ingredients into finished objects**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Cut or stitch a simple leather/cloth craft | An eligible pattern uses real material, Shears or Needle/Thread where relevant, and creates a basic output. |
	| II · Lower | Form an eligible clay or glass object | An actual clay shaping or Glassblowing Pipe recipe creates its supported output. |
	| III · Moderate | Cut an eligible gem with a Chisel | A real uncut gem is transformed, with gem difficulty/value affecting XP. |
	| IV · High | Assemble a more involved wearable/furnishing craft | A registered multi-input leather/cloth or similar Crafting output is finished using its required tools. |
	| V · Highest | Finish a high-difficulty molded jewelry/symbol craft | A Ring, Necklace, Amulet, Bracelet or Holy Symbol Mold completes an existing recipe using applicable material inputs; no bracelet slot is created. |

	**Smithing — authentic forge work and metal improvement**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Smelt an actual metal bar from valid resources | A forge/processing interaction converts real inputs into real usable stock. |
	| II · Lower | Repair existing damaged metal equipment | A repairable item has actual condition restored using legitimate stock and Hammer/workstation conditions. |
	| III · Moderate | Forge a supported functional metal tool | A valid Hammer/Anvil operation produces a real tiered tool; material/tier/recipe determine XP. |
	| IV · High | Forge a supported metal weapon or armor component | An eligible authored equipment recipe consumes real bars and produces a weapon or protective piece. |
	| V · Highest | Complete an eligible advanced within-tier refinement | Smith an actual item improvement using required tools/materials without changing the item's tier or bypassing its level requirement. |

	**Construction — work that visibly changes supported property**

	Player estates use the **fixed-hotspot, doorway-connected Building Mode** in §3.11; free wall placement in an owned estate is not allowed. Construction XP requires an actual completed furnishing, repair, valid authorized structural operation or finished construction project, with its real materials consumed. Estate purchases, room previews, demolition and item repositioning give none. The Crude Wooden Chair is the lowest-yield initial recipe; numeric rewards remain open.

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Build and install a new basic furnishing | Actual materials are used to create the furnishing once; moving the same completed item does not re-award XP. |
	| II · Lower | Repair a genuinely damaged fixture | Restore a real fixture's condition with authorized Hammer/Saw work and materials. |
	| III · Moderate | Build a real structural segment | Create a valid wall/structural piece in an owned or authorized supported building project. |
	| IV · High | Construct or install a functioning workstation | Complete the supported structure or working station with its actual inputs and permitted site. |
	| V · Highest | Finish a demanding multi-stage construction project | Deliver a real finished structure after work and costs; grant only the verified completion contribution, never the component XP twice. |

	#### Utility — 3 skills

	**Agility — environmental movement, without a mandatory course or tool**

	Traversal is intent-driven and derived from full structural geometry, not authored teleport points. Sprint requests local ledge candidates; the mover validates occupied body space and transitions through reach, hang, mantle and exit, with two-bone hand IK and preserved entry momentum. Level 1 has ordinary movement/jumping but no parkour; level 5 may catch a two-block ledge. Higher fixed skill profiles unlock progressively higher ledges, up to the full reach at level 100. Human NPCs with sufficient fixed Agility use the same pipeline when their route is blocked. Locked gates, glass and thin props are excluded; a blocked landing aborts safely. Practice markers teach this behavior and never award automatic checkpoint XP. Small prototype Agility rewards require sustained sprint travel with actual stamina expenditure, or a travelling jump that lands at a new location. Walls, idle jumps, repeated landings on the same short route, teleports and physical ragdoll travel do not train it. Only the player earns these rewards.


	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Vault a real low obstacle during travel | Supported movement successfully clears actual geometry, subject to anti-loop limits. |
	| II · Lower | Cross a real narrow or precarious surface | Physically balance/traverse genuine world terrain without falling. |
	| III · Moderate | Land a meaningful gap jump | A committed jump crosses eligible actual geometry with real landing/collision. |
	| IV · High | Complete a demanding vertical climb | Traverse a supported difficult ledge or obstacle using real movement and time. |
	| V · Highest | Escape an actual pursuit using demanding traversal | Successfully elude a genuine pursuing NPC/creature using a connected sequence of supported environmental movements; not a fake timed obstacle course. |

	**Firemaking — making and sustaining fires with real uses**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Ignite a new supported campfire | Tinderbox and actual Logs create a valid persistent lit fire. |
	| II · Lower | Relight a fire that legitimately went out | An extinguished fueled fire returns to a usable burning state; repeated toggling is not eligible. |
	| III · Moderate | Restore a working fire that genuinely lacks fuel | Real Logs are consumed to extend a materially insufficient burn state. |
	| IV · High | Prepare a useful cooking/work fire for an actual task | A valid ignition makes a fire available for a real upcoming supported work/cooking activity; classify as this route rather than also ordinary ignition. |
	| V · Highest | Complete a real controlled charcoal-fuel conversion | Existing Charcoal Burner behavior transforms actual wood over work time; credit only the finished valid fuel output. |

	**Thieving — illicit gain and unauthorized access that actually occurred**

	| Relative tier | Naturally occurring XP-earning act | What counts; why it differs |
	|---|---|---|
	| I · Lowest | Pickpocket a real carried item | A person's item transfers through a valid Thieving/awareness check; NPC skills, awareness and item circumstances affect XP. |
	| II · Lower | Steal a real item from an owned open container | An unauthorized ownership transfer actually occurs; mere looking/searching does not qualify. |
	| III · Moderate | Pick an eligible secured door | An actual lock changes to accessible through a Lockpick interaction; reopening/relocking cannot repeat the reward. |
	| IV · High | Open an eligible secured chest/container | Resolve a real container lock; lock difficulty and risk shape the award, not a generic chest bonus. |
	| V · Highest | Successfully extract property from actively guarded premises | Complete a genuinely guarded/occupied illegal extraction with real ownership and detection conditions; this replaces ordinary container-theft XP for the same item. |

	**Overlap and event examples:** One real melee hit may award Attack and Strength under the bounded combat attribution rules; a successful stagger does not produce a second same-skill Strength award. A hostile blow that removes HP always grants qualified Defense XP; a fully blocked strike grants no *damage-taken* XP. A Lockpick action and a later genuine theft may each qualify because they have separate, meaningful results; stealing one item does not pay twice because it is both container loot and guarded-premises loot. Cooking on a Stove does not earn more than Fire Cooking of the **same recipe**. Firemaking a work fire is one ignition event. The same building part, crop, fish, item, animal or resource cannot be credited a second time through menu reuse or save/reload. All route IDs are configured once and shared by player and NPC action resolvers.


	### Training tools, workstations, and skill materials — established baseline; exact item tuning open

	**Item-system principle:** An activity's requirements are defined by that activity, **not by a blanket rule that every skill must have a tool**. Distinguish **reusable tools**, **consumable resources**, **world resource nodes**, **workstations**, and **optional equipment bonuses**. This catalog records the initial named items and objects for the **eleven noncombat skills**; it does not add new skills, weapon families, equipment slots, creature types, or world locations. All tool/recipe-specific skill levels, material quantities, action timings, item stats, and reward rates remain **open**.

	| Skill | Core tool or training condition | Other tools / inputs explicitly in scope | Stations, options, and constraints |
	|---|---|---|---|
	| **Agility** | **No tool or inventory item required.** Practice through relevant movement/traversal actions. | Cloth clothing/equipment is optional. | The game has **no carry-weight stat**; cloth cannot reduce a nonexistent inventory weight. Any actual Agility/stamina or handling bonus from *equipable* cloth is **open**. Base creator clothes remain entirely cosmetic. |
	| **Construction** | **Hammer and saw** are core construction tools, checked for relevant building actions; the Hammer also supports melee combat (Bronze–Godly). | Planks, nails, cloth, gold leaf, marble block, magic stone, and other **recipe-defined** existing construction inputs. | Requires a valid supported building site or construction interaction; no universal rule that all projects consume every listed material. Ownership, permissions, stock transfer, and structure state are authoritative. |
	| **Cooking** | **No carried tool required.** A usable **fire or stove** is required for cooking actions. | Optional cooking gauntlets and chef's hat. Food ingredients remain recipe-defined. | Fire/stove is a **world workstation**, not an inventory tool. Chef's hat uses **Head** and cooking gauntlets use **Gloves** if implemented as equippable bonus items; specific bonuses are open. |
	| **Crafting** | **Chisel** is the primary tool for applicable crafting activities, **not necessarily every recipe**. | Ring mold, necklace mold, amulet mold, bracelet mold, holy symbol mold, needle and thread, glassblowing pipe, shears; leather, gems, gold bars, clay and other recipe-defined materials. | Recipes select the relevant tool/mold and consume the correct inputs; one recipe does not require every tool. Rings use the existing Ring(s) slot, necklaces/amulets the Necklace category where equippable; **no bracelet slot exists**. Bracelets may be crafted/traded unless a wearable design is separately authorized. |
	| **Harvesting** | Task-specific horticultural tools: rake, spade, gardening trowel, seed dibber, watering can, secateurs. | Plant pots, compost, seeds, saplings; applicable crops, herbs, and plants. | Tools correspond to different plant/field care actions rather than being simultaneously required. Soil/plant growth and availability are world state. No separate Farming skill is introduced. |
	| **Firemaking** | **Tinderbox** is essential for its ordinary ignition action. | Logs are the principal consumed fuel; exact fuel quantities/type requirements are open. | The resulting fire is a world-state object/interaction and can support Cooking where appropriate. A fire may require a suitable location and safety conditions; implementation constraints open. |
	| **Fishing** | Tool is **catch-method-specific**: small fishing net, fishing rod, fly fishing rod, harpoon, lobster pot, big fishing net, barbarian rod, oily fishing rod. | Bait, feathers, and any method-appropriate consumables. | A matching fishing site/interaction must exist; some methods use consumables, others do not. The rod variants remain named tool options, not newly defined fish species or new lore. Tool/catch pairings and level gates open. |
	| **Mining** | **Pickaxe** is the standard required extraction tool. | Ore rocks and essence mines are **world resource-node/site types**, not portable tools; prospector outfit is optional bonus equipment. | Bronze–Godly Pickaxes are **dual-use** Mining tools and physically usable Pickaxe-family melee weapons; relevant skill/tier checks are described below. Prospector bonuses, exact outfit pieces and mining content remain open. |
	| **Smithing** | **Hammer** is required for applicable anvil smithing actions and is dual-use as a melee weapon (Bronze–Godly). | Metal bars, coal, and recipe-specific smithing inputs. | **Anvil** is the required **world workstation**, not a carried inventory item. Hammer may be the same item definition used in Construction; the recipe governs the actual material costs. |
	| **Thieving** | **No tool required** for ordinary pickpocketing. | **Lockpick** for relevant doors/chests; dodgy necklace and thieves cape are optional bonuses. | Lockpick rules apply to specified locks, not every theft. Dodgy necklace maps to **Necklace**, thieves cape to **Cape**, with exact effect and consumption/degradation rules open. Theft obeys ownership, detection, witness, and delayed Guard reporting. |
	| **Woodcutting** | **Axe / hatchet** is required for ordinary tree-cutting. | **Machete** for applicable jungle vegetation cutting. | Bronze–Godly Axes and Hatchets **both** cut eligible trees and function as melee weapons using existing Axe-family foundations. Trees/vegetation are world resource nodes; tier, site and skill prerequisites are checked for each action. Jungle vegetation is a use case, not approval of a new biome or creature. |

	**Common rules for skill tools and training:**

	- **No new tool slot.** Tools are ordinary inventory items or compatible existing weapon-slot equipment; exact rules for when a work animation requires a held/equipped tool versus an accessible inventory tool remain **open**. A tool must not be silently treated as equipped in the Left Hand, Arrows, or Backpack slots.
	- **Use one authoritative action transaction.** A successful action validates the actor's skill, required tool, input quantities, world station/node, ownership/permission, and output space at commit. It then consumes/materially changes the applicable inputs and site, creates/transfers the output once, and grants the appropriate skill XP once. An aborted or invalid action cannot duplicate items or XP.
	- **Tools and ingredients are different.** Possessing a reusable hammer, saw, chisel, mold, rod or tinderbox does not mean that tool is consumed per action. Consumable inputs such as logs, bait, feathers, planks, bars, nails, seeds and recipe materials are charged only when the corresponding action actually specifies them. Durability and depletion for individual tools are **open**.
	- **Workstations and nodes are not inventory contents.** Fire/stove, anvil, ore rock/essence mine, fishing site, tree/vegetation and supported construction site exist as world targets with their own state. These are instantiated from reusable interactables and participate in the same economy and resource systems as NPC work.
	- **Optional items are optional.** Head, Gloves, Cape, Necklace and protective/utility equipment may eventually carry action bonuses, but no unstated increase to success chance, XP/hour, inventory capacity, combat power or movement stats is canonized by this list. Cosmetics chosen during character creation never grant benefits.
	- **Shared-player/NPC implementation.** Where relevant, human NPCs use the same tools, workstations, input stock, production time and material conservation as the player. A job request or distant simulation must not create output when the necessary supplies or activity are unavailable.

	**Open for inventory and item-content design:** Exact item records/IDs, icons and models, tool requirements and tier applicability, consumability and breakage, method-specific fishing catches, all recipes, station access, bonuses of named optional gear, wearability of non-slotted crafted accessories, and whether specialized tools must be physically held. Keep all naming and presentation compatible with the production-intent devmap asset pipeline.

	### Bronze–Godly dual-use tool progression — established availability; per-tool combat tuning open

	**Axes, hatchets, pickaxes and hammers are reusable training tools *and* physically usable melee weapons.** Their work functions and attack actions belong to the **same item record**, not two duplicates that can be owned/equipped independently. All four tool types are available in **each tier from Bronze through Godly inclusive**; Stone and Malevolent variants are **not in this specific tool catalog**. The material names designate progression tiers rather than literal manufacturing composition.

	| Tier | Required skill level | Available in all four tool types |
	|---|---:|---|
	| Stone  | **1** | Axe · Hatchet · Pickaxe · Hammer |
	| Bronze | **5** | Axe · Hatchet · Pickaxe · Hammer |
	| Chainmail | **10** | Axe · Hatchet · Pickaxe · Hammer |
	| Iron | **20** | Axe · Hatchet · Pickaxe · Hammer |
	| Black | **30** | Axe · Hatchet · Pickaxe · Hammer |
	| White | **30** | Axe · Hatchet · Pickaxe · Hammer |
	| Steel | **40** | Axe · Hatchet · Pickaxe · Hammer |
	| Granite | **50** | Axe · Hatchet · Pickaxe · Hammer |
	| Cobalt | **60** | Axe · Hatchet · Pickaxe · Hammer |
	| Dragon | **70** | Axe · Hatchet · Pickaxe · Hammer |
	| Molten | **80** | Axe · Hatchet · Pickaxe · Hammer |
	| Godly | **90** | Axe · Hatchet · Pickaxe · Hammer |

	**Role mapping:** Axe and Hatchet share the already-approved **Axe** weapon-family foundation, with different authored tool/handling definitions; Hatchet is a **tool variant**, not a new melee weapon family. Pickaxe uses the existing **Pickaxe** weapon family. A work **Hammer** uses an appropriately shortened, physically credible move profile from the existing **Mace/Warhammer** melee-action repertoire **without introducing a new Hammer weapon family** or assuming a small construction hammer has full Warhammer reach/power. The independent Warhammer family stays unchanged. All four are valid to use in their relevant skill work: Axe/Hatchet for Woodcutting, Pickaxe for Mining, Hammer for Smithing and relevant Construction. Every tool model/animation/collision setup must work in its work and melee contexts on the reusable devmap.

	**Level requirement:** The universal material requirement never changes between functions. Each **work interaction** checks its own governing skill (Woodcutting, Mining, Smithing or Construction) against the selected tool tier; equipped melee use must pass the item’s separately authored governing **combat** skill requirement and usual one-/two-hand compatibility. These are **individual action/equipment eligibility checks**, not a total-combat-level gate or a new skill. Combat governing skills for non-sword families remain open rather than being invented here. A higher-tier tool generally improves valid work efficiency and its authored combat statistics, but exact speed, extraction yields, hit damage, impact, stamina, breakage, bonuses and parity between tool types remain **open**. Tool tier by itself does **not** grant extra XP on idle use, unsuccessful hits or exhausted nodes.

	**No slot additions:** Dual-use tools use the existing primary weapon/compatible Left Hand slot **when equipped for fighting** and ordinary inventory access for work where the action permits. A tool does not grant a free additional hand, duplicate its contents, or bypass the 2h/Left Hand exclusion. A construction hammer and a Smithing hammer may share the same underlying item type and tier; duplicate function must not imply duplicate inventory objects or free combat gear.

	## 2.3 Combat identity and resolution

	The seven combat skills produce a **derived combat level** for players and applicable NPCs through the fixed formula below. RPGGAME uses responsive **real-time 3D action combat**, fully playable from first- and third-person cameras. The player's aim, spacing, weapon animation, collision, and defensive execution determine whether strikes connect. **No post-contact random accuracy contest or passive evasion roll is allowed.** Enemies do not level-scale to the player.

	### Combat-level calculation and initial character state — established

	**Combat level is calculated from current combat skill levels; it is not separately trained or used as an equipment requirement.** RPGGAME uses **Health** as the name of the skill called **Hitpoints** in the calculation. The spelling **Defense** in RPGGAME corresponds to **Defence** in the formula. **Ranged** is the Archery contribution, not an additional skill.

	```text
	Base   = (Defense + Health + floor(Faith / 2)) / 4
	Melee  = (13 / 40) * (Attack + Strength)
	Ranged = (13 / 40) * floor(Archery * 3 / 2)
	Magic  = (13 / 40) * floor(Magic * 3 / 2)

	CombatLevel = floor(Base + max(Melee, Ranged, Magic))
	```

	**Exact evaluation rules:** Halve Faith with `floor` **before** calculating Base. Multiply Archery/Magic levels by 1.5 and `floor` **before** multiplying by `13/40`. Add **only the greatest** of the Melee, Ranged or Magic terms to Base; never add the styles together. Keep Base and style calculations fractional until the **single final `floor`**. Use exact rational arithmetic or otherwise avoid floating-point rounding errors at integer boundaries. Recalculate when any contributing skill level changes, including Health, and persist the underlying skill values rather than an independently editable combat-level stat. Other skills—including the eleven noncombat skills—do not contribute to combat level.

	**New-character baseline (established):** Attack **1**, Strength **1**, Defense **1**, Health **10**, Archery **1**, Magic **1**, Faith **1**; all remaining skills **1**. Maximum and current HP are **10** at creation. At these values:

	```text
	Base = (1 + 10 + floor(1 / 2)) / 4 = 2.75
	Melee = (13 / 40) * (1 + 1) = 0.65
	Ranged = (13 / 40) * floor(1 * 3 / 2) = 0.325
	Magic = (13 / 40) * floor(1 * 3 / 2) = 0.325
	CombatLevel = floor(2.75 + 0.65) = 3
	```

	The result is **starting combat level 3**. At all seven combat skills at level 100, the same formula yields **combat level 127**; combat level therefore has a different possible range from the individual **1–100 skill caps**. It affects combat-level display/identification, **not** real-time hit detection, enemy scaling, or per-item material gates.

	### The three melee attributes — established

	- **Attack — How often can I hit?** Attack improves the execution of **successive player-controlled melee actions**: practical chain cadence, eligible recovery intervals, timing/input buffering, and handling within an equipped weapon's inherent startup, active frames, stamina, and recovery. Attack **does not** increase melee HP damage or make a collision succeed by random roll. A weapon's **Attack bonus** represents an authored **handling/tempo advantage**, not accuracy probability or aim assistance; the exact implementation and cap are **open**.
	- **Strength — How hard can I hit?** Strength, weapon base HP damage, the weapon's **Strength bonus**, move properties, and observable execution conditions govern the HP damage of a landed melee strike. Strength also contributes to physical force, lifting, impact/Stability, and sword-tier eligibility. Strength **does not** separately increase attack cadence or alter hit detection.
	- **Defense — Can I avoid or stop the hit?** Defense improves **active defense execution and control**: suitable dodge/parry timing, block/guard effectiveness, recovery, and stamina/stability handling when using a valid defensive action. Defense also gates protective armor. **Neither Defense nor armor introduces a passive hit/miss chance. Armor reduces the maximum damage of unblocked hits; Defense improves trained armor absorption and active defense.** Exact per-level advantages and armor effects are **open** and must be visible in actual defensive behavior, not hidden dice rolls.

	**Direct action combat:**
	- Omnidirectional locomotion, fully supported first-/third-person cameras, free aim, modest contextual aiming assistance, **no hard target lock-on**, and no automatic melee target acquisition.
	- One primary **Melee (weapon)** slot and one **Left Hand** slot: equip a compatible one-handed weapon in each hand for dual-wielding, or a one-handed primary weapon with a shield in the Left Hand. Two-handed weapons occupy both hands and disable the Left Hand slot. Ammunition remains in its own slot; separately invoked Magic/Faith actions remain available when compatible.
	- Weapon-family movesets, reach, startup, active collision windows, recovery, committed animations, stamina costs, and situational specials; responsive hitstop, flinch, sound, and camera feedback.
	- A physical miss, a successfully avoided swing, a blocked hit, a parried strike, and a damaging hit must produce different visual/audio feedback and distinct logged outcomes.

	### Real-time melee resolution contract — established; numerical balance open

	1. **Execute an attack.** Player or NPC chooses an attack. Weapon animation, reach, pose, active collision frames, stamina, and current physical positions govern its opportunity to connect. Attack level and the weapon's Attack bonus may affect **bounded handling/chain parameters**, but may not invent contact or bypass the chosen weapon's physical speed identity.
	2. **Resolve active movement and defense.** Defenders physically reposition, dodge, block, or parry. The applicable collision, guard direction, active defense window, and response timing determine whether the strike is avoided, stopped, or countered. Defense skill may improve those **performed actions**; it cannot passively cancel an otherwise unblocked collision.
	3. **Verify exposed physical contact.** A strike deals HP damage only when its valid hit volume contacts an eligible, unprotected-by-successful-defense target during active frames. If it misses, is dodged, blocked, or parried, no ordinary unblocked HP hit is registered. There is **no subsequent Attack-vs-Defense hit-probability roll**.
	4. **Resolve landed HP damage.** Strength level, weapon base damage, weapon Strength bonus, move multiplier, and observable target/context conditions determine the result. **Attack does not increase maximum HP damage; Defense only contributes through trained armor absorption and performed defense**, and a uniform whole-number damage roll is made only after physical contact, with armor applied to the maximum before rolling.
	5. **Resolve impact separately.** Strength, weapon impact, and move properties act against immediate Stability. Successful stagger raises temporary resistance to repeat staggers until it decays; specialized enemy/boss safeguards apply.
	6. **Commit and communicate.** Spend resources, apply HP/impact/XP, animate reactions, and record combat consequences once. Telemetry distinguishes physical miss, dodge, block, parry, damaging collision, stagger, and defensive failure; it records **no contested accuracy roll**.

	**Resolution contract (whole HP):**

	Physical contact determines whether an attack connects; there is no random hit/miss roll. For melee/ranged, `EffectiveLevel = floor(relevant skill × active Faith multiplier) + 8`; `BaseMax = floor(0.5 + EffectiveLevel × (StrengthBonus + 64) / 640)`. Strength governs melee; Archery governs ranged. Move multipliers modify the maximum before rolling. Magic uses its fixed spell base multiplied by the summed Magic Damage Bonus percentage of equipped gear, without an extra Magic-level damage multiplier.

	Armor reduces the unblocked maximum before the roll. Current tuning absorbs `floor(equipped armor × (1 + Defense/100) / 24)` HP from that maximum, clamped at zero; the divisor is balance data. Roll each integer from zero through the resulting maximum with equal probability. For the player, a zero roll becomes one **only when the resulting maximum is positive**; an armor-stopped maximum remains zero. NPCs and animals retain their zero rolls. Successful parries, performed guards and matching protection prayers may still stop all damage. Impact/Stability remains a separate channel; Attack affects handling and never increases maximum damage.

	**Physical falling and throwing:** Humans take two HP of environmental fall damage above five blocks, plus one per additional complete block; landing is charged once. A fall exceeding eight blocks transfers the living player or NPC to Sydney until safely stabilized on the actual terrain. Death disables stabilization. Carrying holds the torso while limbs retain passive articulation; tap Attack lowers a body, held Attack throws along the aim pitch with carrier momentum. Oversized animal bodies cannot be carried. Thrown bodies and physical thrown weapons show a bounded trajectory guide clipped by world geometry.

	**Weapon speed and skill:** Higher Attack can make chains more responsive and shorten only eligible recovery/transition intervals up to strict move-family caps. It cannot turn a warhammer into a dagger or remove intentional attack commitment. A fast lower-tier weapon may outperform a slower higher-tier weapon through practical openings and sustained hit frequency—even at Attack 100. Weapon Attack bonuses scale across materials as **tempo/handling statistics**, while tier-appropriate Strength bonuses improve offensive force; numeric mapping and interplay remain open.

	**Armor and shields:** Armor remains tier-gated by Defense and has meaningful defensive equipment properties, but **not random miss probability**. Benefits include visible active guard stability and a displayed reduction to the maximum HP damage that penetrates armor. A Shield provides its own active block options when equipped and compatible with the weapon. Exact armor properties, protected contact geometry, parry and dodge timing, stamina costs, block outcomes, and Guard/Stability balance are **open**. An unblocked exposed contact remains a hit; a weak weapon against overwhelming armor may have a maximum damage of zero, shown explicitly as zero damage.

	**Boundaries:** Ranged projectiles and offensive Magic use real 3D trajectories/contact or explicit authored area effects; neither Attack nor Defense gains post-contact melee hit RNG. Magic-only targeting accuracy and elemental weakness use the distinct spell system in §2.5; that is **not** a permission to introduce random sword misses. Health governs HP and endurance. **Faith overhead prayers are an explicit exception to ordinary active melee defense:** while Protect from Melee is legitimately active, the corresponding tagged attack deals zero matching HP damage; it does not convert Defense or armor into passive mitigation. NPC and player actions resolve through the same combat contracts, while AI uses its own perception and decisions.

	**Combat resources:** HP and stamina are universal; applicable ranged weapons consume ammunition. **Magic uses Mana (maximum equal to Magic level, recovering 1 point per 60 seconds of active simulation); every cast consumes Mana.** **Faith uses toggled, continuously draining prayers**, not the previous finite-duration blessing casts; Faith does **not** regenerate naturally and is replenished through altars, religious wine, and eligible potions. A selected protection prayer can fully negate its corresponding attack category while actually active. Full resource and prayer rules are specified in §2.5.

	**Death and risk — established:** Genuine ordinary player death respawns the player **without carried possessions or equipped gear at the nearest reachable graveyard**. At the death moment, location is classified **inside a registered city boundary** or **outside all cities**. Inside, **only Guards** may collect the protected corpse and **all original gear is guaranteed recoverable from the Undertaker** for the ordinary fee. Outside, any physically eligible human NPC may discover and act on the exposed corpse; thieves and other opportunists seek it more eagerly when they credibly learn of it. The player may retrieve remaining items in person or commission an Undertaker for an additional outside-city fee. Violent death can damage eligible equipment, and a real owned mount may flee. **PvH is an independent aggression flag affecting human AI hunting, never a third death category or a city-looting override.** See §3.12; estate Combat Room defeat is nonlethal and has no normal death penalty (§3.11).

	## 2.4 Equipment, materials, weapon families, and inventory

	### Equipment tiers and universal tier-level requirements — established

	**A material tier has exactly one skill-level requirement across all equipment of that tier**—weapons, protective armor, accessories, and other tier-tagged equippables. The item still checks its **own governing skill**, never overall combat level: a Godly sword requires **90 Strength**, and Godly armor requires **90 Defense**. Two items with the same tier cannot have different level requirements merely because they occupy different slots. The existing 100 skill cap and 6-million-XP curve remain unchanged.

	**Complete authoritative material ladder and Sword-family Strength-bonus baseline:**

	| Tier | **Required level (all tiered items)** | **Sword Strength bonus** | Status / progression note |
	|---|---:|---:|---|
	| Stone | **1** | **+1** | Starting baseline; lower base item power than Bronze |
	| Bronze | **5** | **+2** | Original established sword reference point |
	| Chainmail | **10** | **+3** | Named tier; not a generic armor class |
	| Iron | **20** | **+6** | Progression tier |
	| Black | **30** | **+9** | Peer of White; distinct set |
	| White | **30** | **+9** | Peer of Black; distinct set |
	| Steel | **40** | **+12** | Original established sword reference point; stronger than Black/White |
	| Granite | **50** | **+12.5** | Upper weapon-family catalog begins |
	| Cobalt | **60** | **+13** | Progression tier |
	| Dragon | **70** | **+13.5** | Progression tier |
	| Molten | **80** | **+14** | Progression tier |
	| Godly | **90** | **+15** | Original established sword reference point |
	| Malevolent | **100** | **+16** | Maximum-tier equipment; requires mastery of governing skill |

	**Level gates and listed Sword Strength bonuses are locked.** Fractional weapon bonuses are allowed in the underlying stat data so each ascending non-peer tier can improve despite the narrow +12 to +15 interval. Black/White are intentionally equal-rank peers. The above bonuses are the **Sword-family baseline**, **not automatically the stats of every weapon, armor piece, accessory, or creature**. Other families use a data-driven mapping respecting tier ordering and family identity; their exact numbers remain open.

	**Tier semantics:** Material labels are progression categories; they do not mandate that every item is literally made of the named material. Tier-specific crafting recipes, colors, appearance, and loot remain content definitions. No generic light/medium/heavy armor taxonomy. Black/White have equal progression requirements and distinct sets; Steel is above both.

	**Pricing / voxel catalog:** §2.7 owns the shared 3D item-definition pipeline and item Gold values; this section remains the sole authority for tier gates, equipment family legality, slots and stats.

	**Item-by-item gate:** `requiredLevel = TierRequirement[item.tier]`; `qualifyingSkill = item.requirementSkill`; eligibility requires `skillLevel[qualifyingSkill] >= requiredLevel`. Validate each equipped item and every equipment change independently. **Defense** gates protective armor and shields; **Strength** gates swords, including Longsword and 2h Sword; **Archery** governs relevant ranged weapons/ammunition. Requirement skills for other melee families and functional capes, rings, necklaces, backpacks remain **open per item**, never inferred from tier alone. Untiered cosmetics and level-100 skillcapes do not acquire arbitrary material requirements.

	**Example:** A character with **Strength 90 / Defense 50** can wield a **Godly sword** while equipping at most **Granite-tier** protective armor. A **Malevolent sword** requires **100 Strength**; Malevolent armor requires **100 Defense**.

	### Wearable and combat equipment slots — established

	Only the following equipment **slot categories** are supported; cosmetic creator garments remain separate. A listed category is not a promise of extra layers or arbitrary additional accessory slots.

	| Slot | Equipped content | Rules |
	|---|---|---|
	| **Head** | Helmet / head equipment | One occupied head category; relevant material tier requirement |
	| **Torso** | Chestplate / torso equipment | Independent from other armor pieces |
	| **Legs** | Leg armor | Independent equipment piece |
	| **Boots** | Armored boots / footwear | Independent equipment piece |
	| **Cape** | Cape | Material-tiered cape if defined, or special skillcape rules |
	| **Ring(s)** | Rings | One accessory category; **number of simultaneous rings open** (prototype may use one without creating extra categories) |
	| **Necklace** | Necklaces | Necklace category, one equipped item in the baseline |
	| **Gloves** | Gloves | Independent hand-worn piece |
	| **Melee (weapon)** | Primary weapon, one-handed or two-handed, including supported ranged weapons | The existing **Melee** slot remains the primary weapon slot. One-handed weapons can be combined with a compatible Left Hand weapon or shield; a two-handed weapon occupies both hands. Ranged crossbows and thrown weapon families continue to use the primary slot, not an added ranged-weapon slot. |
	| **Left Hand** | One shield **or** one compatible one-handed weapon | Enables shield use or dual-wielding with a one-handed primary weapon. A two-handed primary weapon occupies both hands and prevents any Left Hand item from being equipped. Each left-hand weapon independently meets its tier/skill requirement. |
	| **Arrows (ammunition)** | One equipped ammunition type/stack | Includes **arrows, bolts, brutal arrows** where supported; equipped ammunition is outside normal backpack/inventory storage and thus saves a regular inventory slot. This is **not** an additional held weapon slot. |
	| **Backpack** | One backpack | Increases the maximum count of **ordinary inventory slots**; exact capacity and tier interactions are deferred to inventory design |

	**Hand compatibility and dual-wielding:** Every weapon has an authored `handedness` value. A **one-handed primary weapon + shield** is valid, as is a **one-handed primary weapon + compatible one-handed Left Hand weapon**. The Left Hand slot holds **one item**, never both weapon and shield. A **two-handed primary weapon uses both hands** and cannot coexist with any Left Hand item; the same exclusion applies in either equip order. Equipping a conflicting item must resolve atomically: stow/unequip the displaced item only if a legal destination exists, otherwise reject the action with clear feedback. Never delete, duplicate, silently drop, or leave an illegal combination equipped. Both equipped weapons obey their own tier gates, handedness, collision, timing, and stamina costs. Exact left-hand attack inputs, combo interactions, and weapon-family pairing restrictions are **open**; dual-wielding does not promise free extra hits, instant attacks, or an additional equipment category. Compatible Magic/Faith actions remain available under their existing action and animation rules.

	**Slot logic vs inventory logic:** Equipment and ammunition occupancy are tracked separately from ordinary fixed-slot inventory. Equipping/unequipping performs an authoritative item transfer, maintains ownership and one-location invariants, and respects capacity. Items cannot escape requirements by being transferred from inventory, looted, or equipped by NPCs. Equipment swaps are **manual via inventory**; no instant loadout swap system. Ordinary inventory base size, item-stack caps, backpack capacity bonuses, ammunition capacity, ring count, and detailed quick-use flow are **open** for the next inventory-design pass. No carry-weight system.

	### Weapon-family catalogs by tier — established

	The following are the **only** conventional weapon and ammunition families currently supported. There are **13 melee weapon families in every tier** (no additional upper-tier melee family), five ranged weapon families, and tier-limited ammunition. `Arrow`, `Bolts`, and `Brutal Arrows` are ammunition rather than standalone weapon slots; the dedicated **Arrows** slot holds compatible ammunition. No bow or unlisted launcher is implied. **Narrow named exception:** the **Fire staff** specified in §2.5 is a special Magic focus using the **existing primary slot**, not an extra melee weapon family, ammunition launcher, slot, or material tier. Its handedness and compatibility remain open.

	| Eligible tiers | **Melee weapon families (13)** | **Ranged weapon families (5)** | **Ammunition** |
	|---|---|---|---|
	| **Stone–Steel**: Stone, Bronze, Chainmail, Iron, Black, White, Steel | Dagger, Sword, Longsword, Scimitar, Axe, Pickaxe, Mace, Warhammer, Battleaxe, Spear, Claws, Halberd, 2h Sword | Crossbow, Dart, Knife, Tomahawk, Javelin | Arrow, Bolts, **Brutal Arrows** |
	| **Granite–Malevolent**: Granite, Cobalt, Dragon, Molten, Godly, Malevolent | Dagger, Sword, Longsword, Scimitar, Axe, Pickaxe, Mace, Warhammer, Battleaxe, Spear, Claws, Halberd, 2h Sword | Crossbow, Dart, Knife, Tomahawk, Javelin | Arrow, Bolts; **no Brutal Arrows** |

	**Hard boundaries:** `Brutal Arrows` are available **only Stone–Steel**, never Granite or higher. `Longsword` and `Sword` are **one-handed and eligible for the Left Hand slot**, subject to weapon pairing rules; they can also accompany a shield when used as the primary weapon. `2h Sword` is **two-handed**, occupies both hands, and excludes any Left Hand item. Other families' handedness is item/family data yet to be finalized; do not assume all spears, halberds, warhammers or crossbows are two-handed. Dart/Knife/Tomahawk/Javelin use the primary Melee (weapon) slot for ranged attacks; throwing-stack handling and recovery remain **open**. Valid launchers and ammunition pairings require explicit data. Pickaxes and Axes (including Hatchet variants) retain their dual-use gathering/tool combat behavior; work Hammers use existing Mace/Warhammer action foundations without creating another weapon family or equipment slot.

	Lists establish **family × tier eligibility**, not promises of unique recipes, skins, drop sources, or universally available stock. Families share foundational animations and moves, with special items supporting exceptional attacks.

	### Weapon bonuses, Attack cadence, and damage identity — established

	**Per-weapon data:** family, tier, governing skill, handedness, base HP damage, Attack bonus, Strength bonus, impact, startup/active/recovery, reach, stamina costs, and specials. Dual-use Axe/Hatchet/Pickaxe/Hammer records additionally specify their existing skill-work interaction profiles; work and weapon references resolve to the same owned item ID. Level requirement derives solely from the shared tier table; **bonuses do not change requirements**.

	- **Attack level + weapon Attack bonus → quicker, more controllable attack chaining** within bounded weapon-family startup/recovery rules. No invisible hit rolls, automatic targeting, or extra melee HP damage.
	- **Strength level + weapon Strength bonus + weapon base damage → damage on physically landed hits**, plus physical impact. Strength determines **how hard**, not how often, a character hits. The Sword-family Strength bonuses for **all 13 tiers** are authoritative in the tier table above. Other weapon families retain their own base stats and bonuses (numeric mappings open).
	- **Defense level + worn armor + a shield equipped in the Left Hand (when present) → better active defense options and execution**, subject to explicit timing, coverage and resource rules. Defense/armor do **not** create passive evasion RNG or reduce the HP damage of a fully landed, unblocked melee strike. Exact defensive equipment bonuses remain open.
	- **Weapon speed matters at all Attack levels.** A lower-tier, fast weapon can outperform a higher-tier, slower one depending on the enemy, openings, stamina, reach and encounter length. Higher tiers ordinarily raise baseline stats, but the best practical choice depends on circumstance. No hidden tier-mismatch penalties, and no enemy level scaling.

	**Tuning boundaries:** Attack handling/tempo bonuses, cadence caps, non-Sword Strength bonuses, weapon base HP damage, impact, special attacks, armor's active-defense benefits and exact block/parry/dodge timing remain **open**. The specified Sword progression is a **flat item bonus**, not a promise that +15 equals 15 HP or that all weapons share the same max hit. No random hit or damage rolls in direct physical melee combat.

	**Magic/Faith equipment hooks:** Eligible currently defined equipment can carry a **Magic Damage Bonus percentage** or a **Faith bonus** as authored item data, without introducing equipment-slot categories or automatically granting bonuses to every tier. The only specifically authorized special focus is Fire staff (§2.5). Slot compatibility and actual bonus values remain open.

	**Other equipment rules:** Smithing and Crafting can modestly improve items **within their tier** without bypassing tier requirements or introducing forbidden family/tier combinations. Only selected gear degrades. Quest/dungeon/boss/raid loot, rare special attacks, Treasure Trails and prestigious cosmetics remain supported.

	### Acquisition and content boundaries

	Gear is acquired by exploration, environmental loot, quests, crafting, dungeons, minigames, bosses, raids, and Treasure Trails. Hard/Elite Treasure Trails award caskets and are particularly relevant to valuable cosmetics/special gear. Rare holiday-style collectibles (including partyhats and Halloween masks) can come from rare boss or clue rewards, with scarcity and demand informing prestige. The equipment catalog **does not create any additional material tiers, slots, launchers, general weapon families, or creature types** beyond those listed above; the explicitly named Fire staff, religious wine, and restoratives in §2.5 are constrained special content, not generic permissions to expand the item catalog.

	## 2.5 Magic spellbooks, Mana, Faith prayers, and combat interactions

	**Established design scope:** Magic and Faith are two separate skills and resource pools. Both interact with real-time 3D combat, NPC perception, inventory, equipment, world time, and XP attribution. Spell/prayer effects must resolve through the same authoritative world-state transaction and visible combat/perception logic as other actions. This specification names **only spells and prayers previously discussed or explicitly named here**; it does not create the remaining spell catalog.

	### Magic — four books, 11 level-gated slots per book

	There are **exactly four spellbooks with eleven spells each: 44 total spell definitions at content completion**. A spell belongs to exactly one book, has a unique definition ID, Magic-level unlock, Mana cost, valid target type/range, cast commitment, execution/visuals, and independently defined effect. The spellbooks are **progression lists, not separate player skills**. Casting uses Magic skill and Mana regardless of book.

	| Spellbook | Function and eligibility | **Existing named spells assigned to this book** | Other definitions |
	|---|---|---|---|
	| **Standard** | Elemental offense and concussive control. | **Spark; Fire bolt; Fire Blast; Voltchain; Forcewave.** Fire Blast retains its Fire staff charge requirement. | **6 reserved slots**, content open. |
	| **Ancients** | Ice, spectral blades and temporary undead allies. | **Frost lance; Necrocall; Spelldaggers.** | **8 reserved slots**, content open. |
	| **Lunar** | Traversal, healing, personal enhancement and social utility. | **Spiritual Step; Gaseous Form; Enhance Ability; Friends; Speak With Animals; Telekinetic grab; Vitalmend.** | **4 reserved slots**, content open. |
	| **Arcane** | Transformation, time control, illusion, obscuration, identity and influence. | **Darkness; Minor Illusion; Disguise Self; Charm Person; Morphbeast; Chronoslow.** | **5 reserved slots**, content open. |

	**Provisional unlock scaffold, not a spell-balancing approval:** Each book reserves one slot at Magic levels **1, 10, 20, 30, 40, 50, 60, 70, 80, 90, and 100**. The names above have assigned books **but not individually approved unlock levels or Mana costs**; placing them in slots is a content/balancing pass. No unnamed slot is automatically a usable spell. A player may use only genuinely learned/unlocked definitions and has **one active spellbook at a time**. The installed estate **Occult Altar** provides a validated way to select Standard, Ancients, Lunar or Arcane (§3.11), without unlocking spells that have not been learned. Whether other switching sites exist, and switching permissions during combat or interrupted interactions, remain **open**. Do not create four physical spellbook inventory items or an extra weapon slot without a later decision.

	**Prototype additions and shared rules:** Keep original names where requests overlap: Pyroburst uses **Fire Blast**, Cryoshard uses **Frost lance**, and Phasejump uses **Spiritual Step**. Fire Blast explodes and emits attributed fire; Frost lance chills/freezes eligible receivers; Voltchain shocks nearby wet/conductive receivers through the chemistry rules. Forcewave pushes and interrupts visible targets. Vitalmend heals a valid ally or the caster and closes bleeding. Morphbeast temporarily renders the same actor harmless, retaining identity, fixed skills and belongings; Chronoslow temporarily reduces movement/action rate. Spiritual Step validates the full destination capsule and intervening geometry. Necrocall creates at most two temporary unarmed allies per caster, with saved ownership/lifetime and no corpse, duplicated inventory or XP reward. Spelldaggers launches three gently steering projectiles that must physically contact a visible foe. Trained NPCs use the same book, fixed-level, Mana, targeting and cooldown checks; only the player gains XP. Current numeric gates, costs and durations live in spell definitions and remain balance tuning.

	### Mana — progression, casting, and recovery

	- `MaxMana = MagicLevel`: **level 1 = 1 Mana, level 100 = 100 Mana**, with no additional base pool, and Mana clamped to the current maximum. Mana is **not** Faith points, HP, stamina or ammunition.
	- **Every committed spell cast costs Mana**, including utility, social, and staff-assisted casts. Spell-specific integer costs remain **open**; a valid cast requires enough available Mana. Reservation/commit prevents duplicate charges or free cast effects, and aborted casts follow one consistent interruption policy (open).
	- **Regeneration is 1 Mana per 60 seconds of elapsed active simulation time**, up to the cap. Persist the partial regeneration accumulator; do not reset it on save/load or repeatedly grant an entire point for partial minutes. Recharging through world-clock acceleration, sleep/wait, offline time, or repeated scene loads is **not automatically allowed**; time-skip treatment requires a deliberate, exploit-safe rule.
	- Spell targeting must be physically credible in first and third person: explicit rays, projectiles, areas, caster/target state and timing are authored per spell. Offensive magic does not produce damage because an input was pressed at a target that was physically missed.

	### Elemental spell scaling and weaknesses

	- Eligible offensive elemental spells have an **elementalGroup** (the Standard elemental groups are **Fire, Water, Earth, Air**; Ancients can use **Ice/Blood** groups if marked). A group maintains the **highest Magic-level spell actually unlocked in that group**. For eligible attacks, that group's highest unlocked tier defines the **base damage ceiling** of lower spells in the same group, rather than each lower spell becoming permanently damage-obsolete. This is a *ceiling rule*, not an excuse to give every utility or special spell elemental scaling.
	- The resulting damage maximum is derived from the **applicable spell/group base max hit** and the character's **Magic Damage Bonus percentage** from valid existing equipment/effects. Illustrative calculation, coefficients and integer rounding **open**:

	```text
	Eligible elemental cast:
	    baseMaxHit = GroupBaseMaxHit(highestUnlockedSpellInGroup, spellContext)
	Other ordinary damaging spell:
	    baseMaxHit = SpellDefinition.baseMaxHit

	preWeaknessMaxHit = baseMaxHit * (1 + totalMagicDamageBonusPct / 100)
	if target has an authored weakness to the cast's eligible element:
	    effectiveMagicAccuracyRating *= 2
	    eligibleElementalDamageMultiplier = 2
	else:
	    eligibleElementalDamageMultiplier = 1

	maximumAllowedDamage = preWeaknessMaxHit * eligibleElementalDamageMultiplier
	Actual damage requires a valid hit/area overlap and respects active counters/prayers.
	```

	- **Elemental weakness doubles both Magic accuracy rating and eligible elemental damage**, where that weakness is *explicitly recorded for that enemy type*. The ice-giant/fire weakness pairing is an **illustration**, not an authorization to create an ice-giant species. Accuracy here is **spell-specific** guidance/dispersion/targeting reliability or authored magical-resistance interaction, **not an Attack-vs-Defense melee hit-chance roll**. A completely missed projectile cannot suddenly hit because of weakness. Exact accuracy representation and how it converts to gameplay remain **open and must be visibly testable**.
	- Resistance, spell defenses, target weakness tables, group-ceiling progression, damage-bonus stacking and per-spell area rules require later numeric tuning. **No creature, new item, or new spell is created by this specification.**

	**Fire Blast — named exception:** Fire Blast belongs to **Standard** but is a **specialized, staff-powered spell** explicitly **exempt from generic elemental group-ceiling and weakness logic unless its individual definition later authorizes it**. It **consumes Mana AND one or more actual charges from the named Fire staff** whenever its cast commits. Treat Fire staff as the specifically authorized spellcasting focus using the **existing primary weapon/equipment interface**, **not** a newly opened general weapon family or additional hand slot. Fire staff handedness, charges per cast, recharge/source, equip requirements, numeric hit and special visuals are **open**. An out-of-charge staff blocks Fire Blast; no free effect and no duplicated charge debit.

	### Faith — persistent pool, toggled prayers, and active overhead protection

	- Faith has a current pool and a maximum **tied directly to Faith level** (baseline **`MaxFaithPoints = FaithLevel`, 1–100**, mirroring the Magic pool). **Unlike Mana, Faith points do not naturally regenerate.** Restore them through eligible **altar prayer**, consumption of **religious wine**, approved **restorative potions**, or the constructed estate **Ornate Rejuvenation Pool** (§3.11). Such restorations cannot exceed the pool maximum; costs/quantities, item definitions and altar conditions remain **open**. Wine and potions use existing consumable inventory transactions; an altar is an in-world interactive location, not a carryable station by default.
	- An individual **prayer** is a **toggleable persistent effect** with an activation condition, Faith drain, possibly a mutually exclusive category, and a clearly displayed current state. These replace the old cast-once finite blessings. Each active prayer drains **1 Faith point per baseline six-second period**, independently; active prayer count increases aggregate drain. **Pay one point on activation for the first interval**, then one point at each subsequent full interval; ending prayer early **never refunds a partially used interval**. Persist elapsed time per active prayer across save/load and prevent toggle spamming to avoid cost.
	- Equipped items can grant a **Faith bonus**. **Each +1 adds 3.33% to the duration of a point's drain interval, additively**; it does **not** strengthen the prayer, increase its protection magnitude, or produce a hidden resource refund. This interpretation matches the required lifespan example:

	```text
	effectiveDrainIntervalSeconds = 6 * (1 + 0.0333 * max(0, totalEquippedFaithBonus))

	Faith bonus +0  -> approx. 6.0 sec per active-prayer point
	Faith bonus +30 -> approx. 12.0 sec per active-prayer point (about 2x lifespan)
	```

	  Negative bonuses, Faith bonus caps, exact interval rounding, changing bonus items during an active interval, and stacking modifications are **open**. Do **not** implement this as `6 * (1 - .0333 * B)` or subtract 3.33% of drain per point, which would incorrectly approach free prayer at +30.
	- **Overhead protection prayers — named and established:** **Protect from Magic**, **Protect from Missiles**, **Protect from Melee**. While the correctly typed prayer is active and valid, it **negates 100% of the corresponding tagged incoming attack's HP damage**; it is an explicit, visible supernatural counter, not armor mitigation, an evasion roll, or generic Defense scaling. **At most one overhead protection prayer** may be active at a time, preventing simultaneous immunity to all three forms. Overhead visuals identify the chosen protection. Whether special/secondary effects bypass an overhead, and how the game communicates mixed-type attacks, must be explicitly authored and cannot silently override the advertised attack-type protection.
	- **Offensive/stat prayers — existing named set:** **Burst of Strength**, **Piety**, and **Rigour**, plus any already-authored prayer definitions. These apply **multiplicative modifiers to their listed eligible combat stats**, on top of relevant equipment bonuses; exactly which stats and percentages each prayer modifies remain **open**. Attack boosts affect **tempo/handling**, Strength boosts **landed melee damage/impact**, Defense boosts **active defense**, Archery boosts its appropriate ranged properties; no hidden random melee accuracy or passive armor mitigation returns through prayer bonuses.
	- Multiple **compatible** non-overhead prayers may run together, each charging its own Faith point interval. Category conflicts, stat-boost multiplication order, stacking caps and exact prayer unlock levels are **open**. On insufficient Faith for a due drain, affected prayers deactivate with visible feedback and no negative points. Prayers persist through gameplay until toggled off, exhausted, or explicitly interrupted by a supported game rule; their state and paid intervals survive save/reload.
	- **Faith XP:** Successful prayer-assisted actions and prevented legitimate threats may grant Faith XP via the five-route skill table in §2.2. Simply leaving a prayer active, consuming Faith over time, restoring points, or toggling an overhead repeatedly must not produce an XP loop. Burial and actually supported purification remain distinct Faith training activities.

	**Magic versus Faith:** Magic is **cast-by-cast, Mana-consuming, and naturally regenerating**; Faith is **on/off prayer management with time-based Faith-point drain and manual restoration**. Neither resource can pay for the other. Spellbooks do not define Faith prayers, and the Faith skill does not unlock Magic spells. Both have data-driven level unlocks; **no new actual spell/prayer effects are implicit in the named list or vacant slots**.

	## 2.6 Additional progression profiles

	Ironman, Hard Ironman, and Hardcore Ironman are planned optional profile modes. Their exact restrictions, economic rules, profile conversion, and failure penalties are **open**. All ordinary character progression must function without opting into these modes.

	---

	## 2.7 Item catalog, shared 3D voxel assets, and Gold-price baselines

	### Item authority, scope and physical presentation — established

	**Every physical, obtainable item is one data-defined object with a production-intent 3D voxel model.** The **same authored voxel mesh/material source** powers its dropped/world representation, equipped/held representation when supported, NPC/estate displays, merchant inspection and the inventory preview. The inventory may cache a camera-rendered thumbnail of the **same** mesh for performance, but must not substitute an unrelated hand-drawn sprite or a differently shaped item. Gems need visible uncut/cut geometry, potions actual bottles and liquid/material variants, keys recognizable teeth/metal, bars/resources distinguishable shapes, food recognizable preparation states, and all existing weapon/armor/material-tier skins must be inspectable as 3D objects. Voxel source files, LODs, pivots, collision, world scale, attachment sockets, UV/texture/material data and render previews must be reusable outside the devmap. A quality-appropriate small object may be compactly modeled without inventing inaccessible microscopic voxel geometry.

	**One catalog, no duplicate equipment specification.** §2.4 remains authoritative for the **13 equipment tiers**, slot categories, 13 melee/5 ranged families, material gates, Attack/Strength bonuses and ammunition eligibility. §2.2 governs training tools and skill inputs. **This section owns item definition fields, visual consistency and initial reference values only**; a listed *price formula* does **not** create a new gear family, grant a missing equipment slot, unlock crafting or confirm every conceivable tier/accessory combination as content. Existing skill-level requirements remain based on **tier + item governing skill**, never price or item rarity. NPC- and player-used versions of an object reference the same item definition; valuable story variants may use an authored child definition.

	**Definition fields** (`ItemDefinition`, immutable): `definitionId`, name/localization, item category, existing weapon family/tier if applicable, required skill and handedness if equipped, stack class/unit/limit, canonical voxel-asset ID, variant material IDs, world/inventory/equipment/fixture presentation rules, use/recipe/action tags, legal acquisition/trade tags, and an **integer reference value in Gold**. **Instances** (`ItemInstance`, mutable) store unique instance/stack ID, number of units, condition/damage, potion doses or staff charges when applicable, owner and current holder, original provenance/stolen marker, optional named crafted variant, and supported enchant/quest-binding state. A stack is one authoritative quantity, with physically sensible count/volume cues in its world model; unit reference price times units is an estimate, **not a guarantee of instant NPC liquidation**. Physical Gold coins/piles use the same 3D voxel-rendering discipline; their deposited/accounted currency value remains one ledger amount rather than a duplicate coin item.

	The item is moved between locations, not replaced by a different inventory icon object. Placement, harvesting, buying, stealing, quest rewards, death/corpse custody, estate storage, trophy mounting, equipment swapping and save/load all use the **same durable item identity**. Exceptions: resource nodes (ore rocks/essence mines, live trees, beds of fish), workstations (anvil, stove/fire), rooms, placed fixed estate installations, animals and property titles are **world entities**, not portable items merely because their 3D visuals exist. A constructed fixture uses a world prefab plus **authentic consumed building inputs**; if a future design permits dismantling it into a portable furnishing item, that item needs its own real catalog definition and transfer rule.

	### Currency and reference-price policy — initial balancing baselines

	**Gold (G)** is the sole quoted unit here; `K = 1,000 G`, `M = 1,000,000 G`. Store balances, unit prices and trade totals as **signed 64-bit integer Gold** (with overflow checks and safe multiplication); never use floats for currency or round discounted prices inconsistently. **Reference value is neither a mandated shop buy quote nor an NPC buyback guarantee**; it is the neutral-market calibration anchor for a **legal, intact, ordinary-quality, available unit**. All numerical values below are **prototype market baselines**, not final economy locks or guaranteed sources. Balancing may revise them together with quest rewards, loot frequencies, work wages, market stock, repair fees, estate costs and merchant wealth **without changing item identity or mechanical eligibility**.

	**Reference reconciliation:** The submitted Godly armor pieces (215M + 172M + 112M) totaled **499M**, not the desired ~485M. Set the **Godly helmet to 215M, body to 172M, legs to 98M, totaling exactly 485M**. The submitted Dragon components (4M + 2M + 161K) totaled **6.161M**, not ~50M; the **50M Dragon set benchmark** is retained, but its components are repriced at **12.5M / 22.5M / 15M**. The **Dragon Sword reference is retained at 65K**. Dragon-tier swords are thus much more obtainable than prestigious complete Dragon plate armor; **rarity, production effort and availability differ by item family**, and identical tier names do not imply identical market scarcity. The unusually expensive **Godly helm** is a prestige/supply exception to the normal armor-slot proportions and must be represented by scarcity in authored supply/loot, not an unexplained arbitrary vendor markup.

	**Prices must be economically earnable.** Common goods and normal NPC wages operate on ordinary Gold scales; multimillion-Gold Dragon/Godly armor is **rare, late-game capital**, traded by wealthy qualified buyers, major rewards or large legitimate transactions, not restocked endlessly in the average village shop. World loot, high-end trade, quests, valuable production and commissions must collectively make the intended item progression attainable at reasonable late-game playtime without generating free money. Check total money flow and time-to-afford before approving final prices. **Item reference value never changes its combat statistics.**

	### Tiered armor and Sword reference values — Gold per complete intact piece

	The **three-piece set** is **Helmet + Body + Legs only**, with no invisible set item, bundle discount or duplicated stock. Set prices are exactly the sum of the component prices. These armor labels cover the corresponding existing Head, Torso and Legs pieces; the equipment requirements remain those in §2.4.


	| Material tier | Full helm | Platebody | Platelegs | Three-piece set total | Sword |
	|---|---|---|---|---|---|
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


	All other established material-tiered weapon families use their own **relative family factor applied to that tier’s Sword anchor**; each legal `family × tier` entry has a calculable price even when its content has not yet been individually authored. `referenceGold = max(1, roundHalfUp(tierSwordGold × familyFactor))`. `Black` and `White` are equal-price peers; the same method covers all 13 tiers without 234 manually maintained prices. The factors represent **reference item value**, not damage, speed, handedness, attack accuracy, ammunition compatibility, or required level.


	| Existing weapon family | Sword-price factor | Identity / limit |
	|---|---|---|
	| Dagger | 0.55 | Short reach; quick one-handed form |
	| Sword | 1.00 | Exact sword anchor in the tier table |
	| Longsword | 1.35 | One-handed; distinct handling from Sword |
	| Scimitar | 1.15 | Curved melee weapon |
	| Axe | 1.10 | Weapon-family Axe; work hatchet has its own item record |
	| Pickaxe | 1.40 | Weapon-family Pickaxe; also usable for Mining |
	| Mace | 1.00 | Blunt melee family |
	| Warhammer | 1.65 | Heavy impact-oriented family |
	| Battleaxe | 1.70 | Heavy cutting family |
	| Spear | 1.25 | Reach-oriented family |
	| Claws | 0.95 | Paired/hand-attachment look; handedness rules unchanged |
	| Halberd | 1.90 | Long-weapon family |
	| 2h Sword | 2.15 | Two-handed; Left Hand blocked |
	| Crossbow | 1.80 | Ranged launcher; valid bolt pairings still data-driven |
	| Dart | 0.08 | Price per individual thrown item |
	| Knife | 0.32 | Price per individual thrown knife |
	| Tomahawk | 0.55 | Price per individual thrown tomahawk |
	| Javelin | 0.60 | Price per individual thrown javelin |


	**Worked weapon examples:** `Dragon Dagger = 65,000 × 0.55 = 35,750 G`; `Dragon Sword = 65,000 G`; `Dragon Crossbow = 117,000 G`; `Godly 2h Sword = 8,000,000 × 2.15 = 17,200,000 G`. A Dart, Knife, Tomahawk and Javelin quote is **per authentic thrown item** if the item is sold/stacked, with consumed/recovered projectile behavior still defined by §2.4. A rare named weapon/unique boss version receives an explicit `priceOverride` and rarity rationale; it never silently changes the whole family.

	**Additional tiered slots:** The following **conditional** formula yields references for already-approved item definitions that carry a tier: `roundHalfUp(sameTierThreePieceSetGold × listedFactor)`. It does **not** promise a new ring/necklace/cape/backpack variant for every tier; author only actual supported content and let untiered cosmetics, trophies and skillcapes keep their own values. A shield is one item in the Left Hand, never a new slot.


	| Eligible tier-tagged item | Fraction of same-tier three-piece armor total | Constraint |
	|---|---|---|
	| Shield | 0.20 | Left Hand; Defense gate when tiered |
	| Boots | 0.07 | Boots slot |
	| Gloves | 0.05 | Gloves slot |
	| Cape | 0.06 | Only where an actual tiered cape is authored; skillcapes separate |
	| Ring | 0.035 | Only where tiered ring is actually authored; ring count still Open |
	| Necklace/amulet | 0.055 | Only where tiered accessory actually authored; no bracelet slot |
	| Backpack | 0.09 | Only if a tiered backpack is authored; ordinary capacity remains Open |


	**Ammunition** is priced as a **single physical unit**. Multiply by the actual stack quantity for a reference stack total; displayed quiver stacks must still use a 3D voxel-derived visual. `Bolts = roundHalfUp(ArrowUnit × 1.25)`; `Brutal Arrows = roundHalfUp(ArrowUnit × 1.70)` and **are prohibited above Steel**. These values are consumption and risk sinks; an arrow stack in the equipment **Arrows** slot remains separate from normal inventory slots. A price entry does not imply an undefined bow or launcher.


	| Tier | Arrow / unit | Bolts / unit | Brutal Arrows / unit |
	|---|---|---|---|
	| Stone | 1 | 1 | 2 |
	| Bronze | 2 | 3 | 3 |
	| Chainmail | 4 | 5 | 7 |
	| Iron | 7 | 9 | 12 |
	| Black | 11 | 14 | 19 |
	| White | 11 | 14 | 19 |
	| Steel | 18 | 23 | 31 |
	| Granite | 32 | 40 | **Not available** |
	| Cobalt | 90 | 113 | **Not available** |
	| Dragon | 250 | 313 | **Not available** |
	| Molten | 750 | 938 | **Not available** |
	| Godly | 2,200 | 2,750 | **Not available** |
	| Malevolent | 6,000 | 7,500 | **Not available** |

	**Dual-use tools:** The same price algorithm applies to **Bronze–Godly only** (`toolPrice = roundHalfUp(tierSwordGold × toolFactor)`) for each named tool below. The Axe and Pickaxe overlap existing weapon families; price/identity must not create two copies of the same tool. Hatchet uses Axe-family combat foundations; Hammer uses existing blunt foundations and adds **no fourteenth melee family**. No Stone/Malevolent variants of these specific tool lines are authorized.


	| Dual-use tool | Sword-price factor | Work/combat behavior |
	|---|---|---|
	| Axe | 1.10 | Woodcutting and Axe-family melee |
	| Hatchet | 0.85 | Woodcutting and Axe-family melee; independent item |
	| Pickaxe | 1.40 | Mining and Pickaxe-family melee |
	| Hammer | 0.90 | Construction/Smithing and existing blunt melee actions |


	For example, a **Dragon Pickaxe** is **91,000 G**, a **Dragon Hatchet** is **55,250 G**, and a **Godly Hammer** is **7,200,000 G**; the mining/woodcutting/smithing/Construction requirements and physical melee checks remain separate. The family catalog and tool catalog must reference a shared asset/definition for the same Axe/Pickaxe model, without a duplicate separately generated weapon edition.

	### Gems, resources, and crafting/estate materials — Gold reference values

	**Gem cutting can reduce market value.** Uncut stones command a premium for flexibility, collection condition or artisan demand; cutting an ordinary stone changes the real input into the defined cut item and may legitimately earn Crafting XP, but need not create easy resale profit. The cut stone uses visibly different geometry from the uncut voxel model, both deriving from the same art asset library. Price changes are not a promise of successful cut yield or a new Jewelry slot. **Dragonstone** is added as a gemstone item from the supplied benchmark, **not** a Dragon equipment material tier.


	| Gemstone | Uncut | Cut | Notes |
	|---|---|---|---|
	| Topaz | 130 | 95 | New entry |
	| Sapphire | 240 | 172 | Existing reference adapted |
	| Emerald | 480 | 313 | Existing reference adapted |
	| Ruby | 1,060 | 724 | Existing reference adapted |
	| Diamond | 2,664 | 1,950 | Existing reference adapted |
	| Dragonstone | 15,300 | 10,950 | Existing reference adapted |


	| Item or normalized stocked commodity | Reference Gold / stated unit | Gameplay source/use |
	|---|---|---|
	| Iron Ore | 55 | Mining; metal feedstock |
	| Copper Ore | 25 | Mining; metal feedstock |
	| Coal | 35 | Mining; processing fuel |
	| Iron Bar | 150 | Smelting product; 10 units in basic Garden recipe |
	| Gold Bar | 1,800 | Crafting jewelry and applicable Smithing |
	| Rawhide | 105 | Livestock/game yield; must be processed if recipe requires Leather |
	| Leather | 160 | Crafting input; processed from applicable hide |
	| Bones | 18 | Burial and genuine Faith offerings |
	| Wool | 45 | Sheep husbandry input |
	| Cloth | 35 | Textile and Construction input |
	| Thread | 4 | Consumed when sewing recipe specifies |
	| Clay | 12 | Mining/Crafting material |
	| Quarry Stone Block | 170 | Stone from Quarry Worker; not the named Marble Block |
	| Marble Block | 650 | Advanced Construction material |
	| Magic Stone | 12,000 | Advanced Construction material; not automatically a casting reagent |
	| Gold Leaf | 1,450 | Advanced Construction material |
	| Plank | 35 | Construction building stock |
	| Nail | 3 | Construction fastener; price per nail |
	| Log | 20 | Woodcutting output and Firemaking fuel |
	| Firewood Bundle | 28 | Split domestic fuel |
	| Charcoal | 42 | Converted fuel |
	| Resin | 36 | Renewable tree resource |
	| Soil | 10 | Garden/planting input; 10 units in basic Garden recipe |
	| Compost | 18 | Harvesting/plant care |
	| Seed (ordinary) | 7 | Harvesting; actual crop/plant recipe determines availability |
	| Sapling (ordinary) | 55 | Harvesting/tree care |
	| Plant Pot | 35 | Harvesting container |
	| Herbs (common bundle) | 20 | Specific herbs authored separately |
	| Grapes | 14 | Vineyard/Food input |
	| Fish (common raw) | 28 | Per actual catch item; rarity may override |
	| Meat (common raw) | 38 | Per actual animal-appropriate cut |
	| Food (simple cooked) | 55 | Successful recipe output, not free conversion |
	| Food (prepared meal) | 125 | Recipe output for richer food |
	| Animal Feed | 15 | Real livestock/Menagerie food stock |
	| Egg (ordinary) | 9 | Animal/food input; not a collectible Fish Egg |
	| Milk | 18 | Cow product |


	**Starting recipe valuation checks:** A **Crude Wooden Chair** consumes exactly `2 × Plank (35 G) + 2 × Nail (3 G) = 76 G` of reference materials, plus access to reusable Hammer and Saw; it is not additionally purchased as a finished item for this construction commit. The **Garden** consumes exactly `10 × Iron Bar (150 G) + 10 × Soil (10 G) = 1,600 G` in reference materials. Material quotes do **not** finalize all room recipes, and a finished furnishing's sale value is not automatically greater than the inputs. Every newly authored metal bar type must receive its own item definition, price and recipe; a generic untyped Metal Bar cannot stand in for Gold or Iron.

	### Ordinary tools, keys, potions and other named portable goods

	The catalog below covers the **previously established specialist skill tools** and the **new Brass Key, Antipoison, Stamina Potion**. Distinct equipment item instances retain their designated slot; non-equippable work tools remain real inventory or physically usable world items. World fire, stove, anvil and fishing sites are stations/nodes, **not purchasable portable kit** by default. New potion types have the stated basic functional identity but their detailed duration/doses/restore magnitudes still need definitions; this table does not introduce an unnamed disease, permanent stamina-buff stacking system or altered Faith regeneration.


	| Named reusable tool / utility equipment | Reference Gold | Constraint |
	|---|---|---|
	| Saw | 110 | Construction reusable |
	| Chisel | 70 | Crafting reusable |
	| Ring Mold | 75 | Crafting reusable |
	| Necklace Mold | 85 | Crafting reusable |
	| Amulet Mold | 85 | Crafting reusable |
	| Bracelet Mold | 75 | Crafts tradable bracelets; **does not** add a slot |
	| Holy Symbol Mold | 95 | Crafting/Faith output where defined |
	| Needle | 12 | Crafting sewing |
	| Glassblowing Pipe | 130 | Crafting reusable |
	| Shears | 90 | Harvesting, wool or relevant clipping |
	| Rake | 90 | Harvesting bed preparation |
	| Spade | 85 | Harvesting digging |
	| Gardening Trowel | 55 | Harvesting plant handling |
	| Seed Dibber | 35 | Harvesting seeding |
	| Watering Can | 95 | Harvesting; filled state has no duplicate item |
	| Secateurs | 110 | Harvesting pruning |
	| Tinderbox | 25 | Firemaking ignition |
	| Machete | 220 | Jungle vegetation tool; not a new weapon family |
	| Small Fishing Net | 85 | Fishing method |
	| Fishing Rod | 135 | Fishing method |
	| Fly Fishing Rod | 300 | Fishing method |
	| Harpoon | 460 | Fishing method |
	| Lobster Pot | 430 | Fishing method |
	| Big Fishing Net | 700 | Fishing method |
	| Barbarian Rod | 2,400 | Fishing method |
	| Oily Fishing Rod | 1,150 | Fishing method |
	| Fishing Bait | 4 | Consumed per use only when method says so |
	| Feather | 3 | Consumed when applicable |
	| Lockpick | 105 | Thieving lock access; break/consumption rules Open |
	| Brass Key (ordinary) | 75 | One specific lock/key identity; not a universal bypass |
	| Fire Staff (uncharged) | 135,000 | Existing Magic focus; charges separate instance state |
	| Prospector Outfit (complete) | 3,000 | Optional Mining-themed gear; individual pieces defined later |
	| Cooking Gauntlets | 550 | Optional gloves-slot utility |
	| Chef’s Hat | 250 | Optional head-slot utility |
	| Dodgy Necklace | 1,800 | Optional necklace-slot utility |
	| Thieves Cape | 5,000 | Optional cape-slot utility |


	| Item / category baseline | Reference Gold | Scope |
	|---|---|---|
	| Antipoison | 240 | Removes applicable poison on consumption; does not silently cure venom unless approved |
	| Stamina Potion | 280 | Restores bounded current stamina; exact magnitude/other effects Open |
	| Faith Potion | 420 | Replenishes Faith; exact amount Open |
	| Religious Wine | 260 | Faith restore item; exact amount Open |
	| Ordinary Restorative Potion | 220 | Recovery/bonus loot; exact HP/stat tags authored per variant |
	| Alcoholic Drink (ordinary) | 35 | Existing consumable/loot class; optional drinking effects Open |
	| Tobacco (ordinary) | 60 | Existing low-Honor loot class; smoking effects not established |
	| Arrows / Bolts / Brutal Arrows | See ammunition table | Tier and unit price set above, not a new ammo family |
	| Fish Egg (collector baseline) | 2,400 | Legitimate Fishing/Aquarium collectible; fish-specific rarity overrides |
	| Teleport Tablet (generic base) | 1,600 | Study-lectern output; valid destination and recipe required |
	| Clue Scroll (entry-grade) | 650 | Treasure Trails eligible item; cannot freely generate reward |
	| Treasure Casket (unopened, basic) | 2,200 | Reward container; actual contents generated and committed once |
	| Skillcape | 95,000 | Level-100 mastery purchase reference; non-tiered |
	| Ordinary Cape | 170 | Non-tiered wearable |
	| Achievement Cape | 12,000 | Authored unlock required; tradeability to be explicitly defined |
	| Basic Ring (crafted, unenchanted) | 210 | Non-tiered baseline jewelry; equipped only if allowed |
	| Basic Necklace (crafted, unenchanted) | 290 | Non-tiered baseline jewelry |
	| Basic Amulet (unenchanted) | 320 | Existing Necklace-category jewelry; mounted compatibility authored |
	| Basic Bracelet (crafted) | 185 | Tradeable crafting output; not wearable without a slot |
	| Holy Symbol (crafted) | 360 | Prayer/faith use only where actually authored |
	| Basic Backpack | 650 | Backpack-slot item; exact added slot capacity Open |
	| Starter Clothing Piece (top/legs/shoes) | 75 | Cosmetic base clothing only; 24 swatches do not change price |
	| Holiday Toy (ordinary) | 850 | Collector Toy Box baseline; exclusive event items price individually |
	| Regular Treasure-Trail Reward (ordinary) | 2,500 | Category baseline; authentic award only |
	| Monster Head Trophy (ordinary) | 12,000 | Requires acquired stuffed trophy; rarity/model may override |


	**Creator clothing** retains its **20 styles each for Tops/Legs/Shoes** and independent 24 colors, without stats. A style/color variant is a **model/material parameter of one base clothing definition**, not a separate piece conjured into player inventory for each combination; actual acquired/swapped clothing must still be tracked. Hair, beard and skin are **character appearance data**, not saleable portable items. **Holiday-event collectibles, partyhats, Halloween masks, rare Treasure Trail caskets, boss/raid artifacts, stuffed monster heads, specialized teleport jewelry and unique Keys** require item-specific availability, tradeability and an individually approved reference override before shipment. The generic category references above supply *a baseline only*, not a claim that these unenumerated unique rewards share identical value. Skillcapes preserve their level-100 eligibility even if a reference purchase price is supplied.

	### Price resolution, scarcity and cross-system interactions — binding contract

	1. **One authoritative price:** Every authored obtainable physical item resolves by `specificOverride → eligible tier/family calculation → explicit commodity row → category default`. Content validation fails if an item has no way to resolve an integer reference value. `priceOverride` for a rare/untradeable item may still specify a non-market **replacement/inspection value**; legal sale permission is **independent of value**. Never fall back to the value of another equipment family or expose `0 G` as a free purchase because data are absent.
	2. **Quotes are contextual:** `neutralReference × supplyDemandFactor × conditionQualityFactor × regionTransportFactor × traderMargin/AttitudeFactor`, then permitted Honor **purchase** discount from §3.5, respecting real funds, inventory, provenance, authorized buyers and minimum sustainable-price floors. Factors and final rounding/caps **Open for balancing**. Player Honor does not automatically apply to sold-item bids or to illicit Fence dealings; an NPC must possess or learn the reputation facts required by §3.5.
	3. **No same-counterparty arbitrage:** For identical condition, stock and moment, a merchant's firm **buyback bid stays below its own retail ask** after discounts and bargaining, barring an explicitly authored loss-leader/contract. A sale to a cash-poor NPC may be rejected, paid partly via permitted barter or routed to an actual funded buyer—not transformed into minted Gold. Quotes expire or revalidate when availability changes; no instant free restock. Merchant buyback and estate utility repair fees consider **condition, materials and labor**, rather than reverting damaged items to full reference value.
	4. **Provenance affects saleability:** Owned/stolen/branded quest items retain original item IDs as they move through theft, corpse looting, Guard custody, Undertaker recovery, NPC use, merchant stock, estate collection or housing. A Brass Key can have a market/material value but will only unlock its own authored lock(s); selling it does not teleport access or grant rights. Fences evaluate provenance and enforcement risk; ordinary merchants need not buy conspicuously illicit goods.
	5. **Causality makes items useful:** Mining Iron/Copper Ore supports legitimate processing orders; rawhide/leather support Crafting; gems and Gold Bars support molds/jewelry; cooked food and potions support survival, trade and NPC orders; logs/planks/bars/soil feed estate work; real fish eggs/trophies/capes enter their corresponding estate containers; combat gear and work tools can be stolen, repaired, sold, displayed or retrieved from corpses only via original-item transfers. Crafting XP is granted on the **successful physical creation/change**, not on price increase, trading or inspection.
	6. **Finite luxury availability:** The 50M Dragon / 485M Godly / 1.25B Malevolent three-piece references require appropriate supply constraints, rare acquisition avenues and solvent buyers. **No generic shop offers an unlimited quantity or infinite buyback funds.** Major prices are a late-game calibration target: test against legitimate Gold earning speeds, attainable skill levels, repair/upkeep drains and collection goals. Small/common materials must remain meaningfully obtainable without 10,000-fold arbitrary inflation purely to match luxury gear.
	7. **World and inventory show the same thing:** Picking up a Sapphire removes the 3D world gem and exposes the same voxel gem in the inventory; dropping it restores a corresponding world instance without creating a second item. Cutting changes item definition and geometry at a committed Crafting action; equipping/showcasing changes attachment/holder; transferring charged staff or potion preserves mutable charges/doses. Multiple items in a stack remain one correct quantity and are rendered with suitable 3D pile/group visuals. LOD/cached thumbnails are technical projections of the same authored model, not separate content art.

	**Implementation/data boundaries:** Reference prices are an **initial economic model**, not locked exchange/loot rates. Crafting recipes and item stats, some special item functions, source frequencies, unique-gear overrides, potion dose sizes, trophy variants, special clothing/rare cosmetic tradeability, Gold emission/sinks, NPC wage/reward curves, regional elasticity and rare-item buyer funding remain **Open**. The definition registry and asset-link requirements are **Established**. Every new physical item added later must declare its 3D source asset, category/authorized actions, unit semantics, saleability, and an integer reference-value rule before entering a production build.

# 3. World, Narrative, Simulation, and Player Interaction

	## 3.1 World structure and authored content

	GAMEWORLD comprises connected voxel environments, settlements, roads, wilderness, contested territory, dungeons, and discoverable secrets. The first playable build must have **at least three kingdoms** with distinct jurisdictional, political, and economic functions; **two more kingdoms** are planned for later expansion. Kingdom names, governments, cultures, geography, religions, and story remain open.

	**Authored priority:** Principal NPCs, political conflicts, major quests, consequential dialogue, memorable locations, and narrative branches are handwritten/designed. World simulation sustains their context and aftermath; it does not randomly generate replacements for major authored storylines.

	**Quest discovery:** Exploration and NPC conversations. Journal records learned facts and objectives; markers can pinpoint a known target or mark a search area. Failed or never-discovered opportunities are not necessarily signposted. Stories can include prerequisites, branching choices, puzzles, skill checks, physical evidence, discovered documents, and hidden locations.

	**Quest vulnerability:** Quests can permanently fail or become unavailable through NPC death, destroyed prerequisites, political change, missed opportunities, or other valid world events. No guarantee of completing all quests in one save. Narrative failure is intentional; broken technical quest state is not. Alternative quest contacts/resolutions exist only when specifically supported.

	## 3.2 Player Interaction System — prototype contract

	**Established product behavior:** One primary contextual interaction, one secondary action menu, physically present meaningful interactables, skill-gated or skill-influenced resolutions, world-aware ownership, and clearly communicated consequences. Combat aiming, spell casting, and manual inventory swaps remain distinct controls. Interactable objects must be **reusable asset/prefab definitions** with stable interaction anchors, collision, state components, animation/audio hooks, and appearance independent of the devmap's coordinates or test scripts. **Prototype defaults** below are implementable recommendations, **not permanent player-facing design approvals**; keep them data-driven.

	### Interaction pipeline

	1. **Acquire target:** From the active camera, raycast toward crosshair/reticle and inspect the first eligible interactable collision target. Use an additional bounded cone or nearby-target query only for modest accessibility assistance; line of sight still applies. Interactables expose an anchor/interaction point and their valid distance. **Prototype defaults:** 3 m interaction range; inspect to 5 m; prioritize raycast hit, then nearest visible eligible anchor within a small cone.
	2. **Build options:** Ask the target's interaction provider for actions available in this context (actor state, items/skills, ownership, distance, quest/faction flags, current target state). Return both `enabled` and `disabled with reason`; never hide crucial state behind an unresponsive button. Do not leak secret quest/loot knowledge by advertising undiscovered actions.
	3. **Choose action:** Primary button invokes the provider's highest-priority valid ordinary action; secondary button opens a sorted contextual list (e.g., Talk, Trade, Examine; or Open, Lockpick, Examine). Holding an item may expose `Use item on target` where explicitly supported. Do not turn every scene prop into a physics object.
	4. **Validate at commit:** Recheck distance, LOS, actor eligibility, requirements, ownership, resource cost, target state, and contention. The menu preview is advisory; world state may change between selection and commit.
	5. **Resolve and transact:** Apply one authoritative action and its atomic state changes (see §3.8), then publish the resulting event. An action that cannot commit changes no durable game state. Deferred actions (e.g., crafting or lockpicking animation) have a clearly defined completion/interrupt point.
	6. **Present feedback:** Animation, SFX, prompt, inventory delta, NPC response, notifications, and journal updates reflect committed results rather than independently changing state.

	### Prototype action vocabulary and permissions

	| Target | Primary action default | Context options | Durable outcome / validation |
	|---|---|---|---|
	| Door | Open / Close | Examine; Unlock; Lockpick if supported | Open/locked state; key/skill gate; access ownership |
	| Container | Open / Search | Examine; Lockpick; Take contents | Inventory transfers; ownership and possible crime evidence |
	| Loose item | Take | Examine; Use if applicable | Change item's container/holder exactly once; preserve origin ownership |
	| NPC | Talk | Trade; Examine; supported service/quest actions | Dialogue context, offers; separate trade transaction |
	| Animal | Observe/Examine or permitted contextual action | Approach; Feed from valid existing supplies; permitted husbandry interaction; defend, hunt or otherwise physically interact under ordinary rules | Species/age temperament, fear and needs; actual feeding/use, owner consent, injury, ownership, animal/group state and possible witnessed offense |
	| Workstation | Use | Recipes; Examine | Check recipe/materials/skill; consume inputs and create outputs atomically |
	| Resource node | Gather | Examine | Consume/deplete node stock; award item and validated XP |
	| Owned furnishing | Use | Move/place only in supported build mode; Examine | Ownership, placement cell, persistent state |

	**Rules:** A theft is still physically possible where allowed: ownership marks it illicit rather than automatically blocking action. Stealing creates observed evidence only if a witness actually perceives it. A witness report is a *separate later action* before kingdom bounty changes. Some checks have fixed levels (smithing recipe, locked interaction class); others roll conditional chances (pickpocketing, lockpicking where authored). **Melee combat is distinct:** valid weapon collision against a target not saved by an actual dodge, block, or parry produces a hit without a probability roll; Strength then determines deterministic HP damage.

	### Prototype UI and controls (replaceable mappings)

	- **Primary interact:** `E` keyboard / controller south face button, when a valid prompt is in focus.
	- **Secondary interact:** hold `E` / controller south face button to open action menu; controller navigation uses conventional focus controls. Binding is configurable and must not conflict with combat action handling. Alternative input layouts are **open**.
	- A crosshair prompt shows verb, target label, and input hint; disabled entries display a concise requirement or failure reason. Hide prompts during incompatible animation/menus but preserve input buffering decisions as **open**.
	- UI is not an authority. Calling a context menu action by ID must follow the same server-independent single-player validation as using the primary button.

	### Minimal code-facing contract (illustrative pseudocode)

	```text
	GetInteractionOptions(actorId, targetId, context) -> [
	  { actionId, labelKey, priority, enabled, disabledReasonKey,
	    requiredRange, predictedDuration, parameters }
	]

	RequestInteraction({ requestId, actorId, targetId, actionId, parameters })
	  -> { result: committed | rejected | interrupted,
	       reasonCode?, worldEventIds[], presentationCues[] }
	```

	Use stable action IDs rather than hardcoded localized strings. The same physical target may return different actions as its state changes. `requestId` prevents an accidentally repeated input from duplicating a transaction. **Open/engineering choice:** Exact interaction range, ability to turn camera while using menus, radial versus list menu, exact animation timings, and input bindings; the above defaults are sufficient for a prototype.

	**Integration rule:** Physical actions are the only authority for downstream XP, Honor, ownership, AI knowledge, market, quest and corpse changes (§3.13). An action menu may preview potential consequences but must never commit them before the actual validated action succeeds.

	### Interaction edge cases to test

	- Move out of reach or lose LOS before committing: action rejected without item loss.
	- Target removed, NPC dies, chest locks, or another actor takes an item while menu open: revalidation rejects/updates options safely.
	- Pickup at full inventory: no deletion or partial ownership transfer unless an explicitly supported partial pickup is chosen.
	- Crafting interrupted during wind-up: materials spent **only** at the defined commit point.
	- Theft with no witness: item ownership retains crime history, but no immediate bounty; witnessed theft creates evidence, not an automatic law report.
	- Save/reload while door open, resource depleted, item moved, or quest NPC dead: preserve the exact relevant state.

	## 3.3 NPC agents, occupations, intelligence, and memory

	### Mandatory role → subtype architecture — established

	**Every human societal NPC must have exactly one active, concrete behavioral subtype from one of twenty role families: fifteen occupations (the original twelve plus Clergy, Civic Services and Leatherworker), the Unemployed life-state family, and four noble-governance families (Baron, Count, Duke, King).** Every family has **exactly five registered subtypes**, producing **100 concrete NPC definitions**. A record whose behavior is merely `Guard`, `Thief`, `Merchant`, `Unemployed`, `Baron`, `Count`, `Duke`, `King`, `Clergy`, `Civic Services` or `Leatherworker` is **invalid**: these family names are catalogs, **never spawnable agents or fallback AI**. Characters without paid work use a matching **Unemployed subtype** rather than an untyped idle state; authored characters still require a concrete subtype appropriate to their current life/office assignment.

	**Family is not social status.** Baron, Count, Duke and King are **governance assignments for NPCs performing the relevant duties**, not automatic permission sets triggered by a title. A noble title, office powers, land rights and allegiance remain distinct established world-state properties. An NPC may carry a title while actively following another valid role/subtype if their principal daily occupation differs. Political choices must always pass actual jurisdiction, office, story and resource checks. The King role does not unlock playable kingship or unrestricted wars, policies or succession.

	**Priority and safety constraints for the new families:** The **Hobo** responds chiefly to real unmet survival needs; food requests, retreat and lawful help-seeking take precedence over risk. A need-driven attempt to take food is an exceptional last-resort theft using normal ownership, witness and bounty rules; Hobo is **not a Thief subtype** and does not initiate predatory violence. Unemployed profiles do not manufacture jobs, housing or supplies. Noble-governance profiles govern **only existing people, properties, commitments, legal states, stock and funds** and exercise authority by validated dialogue/assignments. They cannot create armies, wars, laws, titles, items, creatures, kingdoms or irreversible storylines procedurally. Sovereign death/succession remains unresolved; no new immortality or automatic replacement rule is implied.

	**Shared sensory awareness, including light, sound, occlusion and remembered identity, is governed by §3.10; role subtypes select responses to perceived events rather than inventing independent perception.**

	**The subtype is behavioral, not cosmetic.** It defines an NPC's occupational goals, eligible job-specific choices/actions, evaluation priorities, prerequisites/tools, supply/demand connections, acceptable risks and responses when work fails. The parent role supplies reusable job mechanics (such as exchange for Merchants or harvesting for Producers); it does **not** independently pick occupation-specific actions. Individual skills, personality, needs, memory, allegiance, possessions, relationships and current circumstances then determine *whether, when, where, and how* the NPC attempts those subtype-enabled actions. Personality does not grant an unrelated professional action without the necessary capacity/permissions.

	All NPCs additionally use **shared behaviors**—movement, perception, survival, eat/rest, basic socializing, personal trade, self-defense/flight, witnessing/reporting, communication, remembering, and reasonable incidental interactions. A Fisherman can flee a wolf or purchase food; doing so does not make them a Guard or Merchant. Jobs may change only through an explicit employment/role transition that installs a different **valid subtype**, with compatible skills, opportunities, residence or office state, and equipment; there is no unconstrained role mixing or automatically unlocking every subtype's occupational actions.

	### The 100 concrete human NPC subtypes

	**Non-overlapping specialized decision authority is mandatory.** The 100 subtypes each have distinct **signature decisions, objectives, eligibility conditions and persistent effects**. Universal physical verbs such as move, interact, buy, defend, report and transfer inventory are reusable mechanics, not 100 independent AI implementations. An NPC may use shared verbs but **cannot autonomously select another subtype’s exclusive objective or signature operation** unless a validated subtype transition occurs. Distinction is tested at the decision/permission layer, not by artificially duplicating primitive animations.

	| Role family | Mandatory concrete subtype | Unique decisions / goals | Protected boundary |
	|---|---|---|---|
	| **Farmer** | **Crop Farmer** | Choose seasonal field crops, rotate plots, sow, irrigate and harvest annual field yields. | Crop rotation and planting windows; not livestock breeding or orchard care. |
	| | **Livestock Farmer** | Breed herds, manage grazing and feed, tend animal health, and collect milk, wool or other husbandry output. | Herd welfare and reproductive cycles; not crop-field planting. |
	| | **Orchard Keeper** | Prune fruit trees, manage perennial tree health, protect blossoms and schedule fruit picking. | Multi-season orchard maintenance; not vine trellising. |
	| | **Herbal Grower** | Select medicinal or arcane herb beds, control growing conditions, harvest and grade potent botanicals. | Specialized herb viability and quality; not food-field yield. |
	| | **Vineyard Keeper** | Train grapevines, manage trellises, choose harvest ripeness and supply grapes to processors. | Vine training and ripeness decisions; not generic fruit tree maintenance. |
	| **Fisherman** | **River Fisherman** | Track river flow, migratory runs and changing riverbank fishing spots; choose line/net placement. | Moving freshwater currents and fish migration. |
	| | **Coastal Fisherman** | Read tidal windows, select shore or near-coast fishing grounds and land catch before tide changes. | Tidal access and coastal return windows. |
	| | **Deep-Water Fisherman** | Provision a sea voyage, evaluate offshore weather, choose distant grounds and return with a large catch. | Voyage supplies and exposure beyond shore. |
	| | **Shellfish Gatherer** | Identify shellfish beds, time low-water access, collect shellfish and avoid depleted or hazardous beds. | Intertidal bed collection rather than fish-catching. |
	| | **Lake Net Fisherman** | Set and retrieve permitted lake nets, monitor net saturation and select still-water net locations. | Stationary-water deployed gear; not river flow or sea tides. |
	| **Woodcutter** | **Logger** | Choose legal stands for felling, assess tree fall paths, cut trunks and stage full logs. | Bulk timber felling and safe fall-zone decisions. |
	| | **Forester** | Survey managed woodland, plant saplings, mark protected trees and authorize sustainable selective cuts. | Woodland stewardship and harvest permissions rather than timber volume. |
	| | **Resin Tapper** | Select suitable living trees, score bark without felling, install collection vessels and harvest sap/resin. | Renewable tree tapping; distinct resin inputs for production. |
	| | **Charcoal Burner** | Acquire cut wood, construct charcoal pits or kilns, manage low-oxygen burns and recover fuel. | Fuel conversion through controlled burn, not ordinary chopping. |
	| | **Firewood Cutter** | Select low-grade wood, cut and split logs into domestic fuel bundles and fulfill local heating demand. | Domestic fuel sizing and bundle preparation; not full-log supply. |
	| **Miner** | **Ore Miner** | Select metal-bearing veins, extract and sort ore by metal type, and stage ore for processors. | Metal-ore seam selection and depletion. |
	| | **Quarry Worker** | Choose supported quarry faces, cut usable masonry blocks and stage dimension stone. | Structured quarry-face cutting for construction blocks. |
	| | **Gem Prospector** | Survey uncertain deposits, sample promising ground, identify gems and decide where further excavation is warranted. | Exploration under deposit uncertainty, not routine extraction. |
	| | **Clay Digger** | Inspect clay-bearing ground, excavate workable clay, manage waterlogging and supply ceramic or building inputs. | Soil moisture/quality and clay excavation; not stone blocks. |
	| | **Coal Miner** | Find combustible seams, assess underground ventilation and fire risk, extract coal for fuel users. | Fuel seam extraction and ignition/air hazards; not metal ore. |
	| **Craftsman** | **Blacksmith** | Select metal tools or melee-weapon orders, heat/forge components, temper blades and repair metal tools. | Metal tools/weapons and forge processes; armor patterns excluded. |
	| | **Armorer** | Fit individual wearers, shape or assemble protective pieces, test fit and repair armor. | Wearer measurements, armor fit and protection components. |
	| | **Carpenter** | Measure planned furniture or joinery, cut timber, assemble wooden goods and install supported fittings. | Precision wood joinery and furnishing output; not structure construction. |
	| | **Cook** | Choose recipes from available ingredients, prepare food batches, monitor spoilage and complete meal orders. | Food recipes, perishability and meal service. |
	| | **Tailor** | Take garment measurements, choose patterns, cut cloth, sew and alter garments to fit. | Textile patterns and garment fitting, not armor construction. |
	| **Merchant** | **General Store Owner** | Assess broad household demand, allocate limited shelf space among categories and replenish everyday staples. | Multicategory stock balancing; not specialist appraisal. |
	| | **Weapons Merchant** | Appraise weapon condition and tier, choose arms stock, verify buyer access and match products to demand. | Weapon-specific appraisal and eligible buyer recommendations. |
	| | **Food Merchant** | Forecast daily edible demand, rotate expiring lots, discount perishables and replenish fresh food. | Spoilage-aware stock rotation and short replenishment cycles. |
	| | **Traveling Merchant** | Compare regional buy/sell margins, choose markets, purchase a traveling stock and arrange journeys. | Intermarket arbitrage and route selection, not fixed-store operation. |
	| | **Fence** | Assess stolen-item provenance and enforcement risk, accept/reject illegal stock and arrange discreet resale. | Illicit provenance/risk screening; legitimate merchants refuse flagged stolen goods. |
	| **Hauler** | **Courier** | Prioritize lightweight sealed letters or parcels, choose fast delivery routes and obtain recipient acknowledgment. | Information/parcels, speed and proof of receipt. |
	| | **Wagon Driver** | Evaluate vehicle capacity and road passability, load a contracted bulk shipment and complete a point-to-point haul. | Single vehicle trip capacity and terrain restrictions. |
	| | **Caravan Hauler** | Coordinate multiple cargo units and travelers, set convoy departure, maintain formation and respond to route security. | Convoy coordination and shared scheduling, not merely driving one wagon. |
	| | **Porter** | Move goods short distances within a facility or district, sequence nearby carry tasks and place cargo at exact stations. | On-foot local transfer and tight loading/unloading locations. |
	| | **Supply Runner** | Identify time-critical supply requests, abandon low-priority runs and expedite emergency food, fuel or medicine deliveries. | Urgency-driven dispatch and critical-demand prioritization. |
	| **Guard** | **Town Guard** | Respond to resident complaints, perform neighborhood patrols, verify witnesses and investigate local offenses. | Community-scale report handling and local case continuity. |
	| | **City Guard** | Monitor crowded districts, disperse disturbances, coordinate reinforcements and route arrested persons into city custody. | Crowd control and multiple-unit district response. |
	| | **Gate Guard** | Check passage permissions and suspicious cargo, operate access checkpoints and deny or admit entry. | Checkpoint admission/inspection permissions. |
	| | **Prison Guard** | Record custody, supervise cell transfers and visitation, check confinement and respond to escape attempts. | Prisoner custody and secure facility procedures. |
	| | **Rural Patrol Guard** | Select road circuits, investigate roadside reports, respond to isolated offenses and seek reinforcements over distance. | Sparse-territory patrol routing and remote reporting. |
	| **Knight** | **Household Knight** | Maintain a named patron’s security, protect household members and respond to threats against their estate. | Patron-specific protection and household loyalty. |
	| | **Border Knight** | Inspect frontier fortifications, evaluate incursions and hold or reinforce assigned border sites. | Frontier threat escalation and territorial defense. |
	| | **Escort Knight** | Assess threats along a designated journey, stay with a protected target and adjust escort formation or route. | Moving VIP/security contract; prioritizes principal over location. |
	| | **Knight Errant** | Seek sanctioned martial-service petitions, assess merit, travel to resolve accepted duties and report results. | Independent selection of approved service petitions. |
	| | **Order Knight** | Receive vows-based institutional directives, protect order assets and report compliance to a specific command hierarchy. | Order mission eligibility and vow/command adherence. |
	| **Thief** | **Pickpocket** | Profile carried valuables and attention, approach crowds, lift accessible items and leave unnoticed. | Person-carried stealth theft without premises entry. |
	| | **Burglar** | Survey building occupancy and access, plan unlawful entry, breach locks and remove stored valuables. | Premises infiltration and container theft. |
	| | **Highwayman** | Choose isolated road victims, block passage, demand surrender and flee before assistance arrives. | Coercive roadside hold-up without coordinated assault. |
	| | **Smuggler** | Seek restricted cargo contracts, conceal contraband, choose covert transport routes and evade checkpoints. | Illegal freight evasion rather than stealing ownership. |
	| | **Bandit** | Coordinate an armed group, select lucrative targets, initiate a forceful raid and divide captured spoils. | Group assault and loot allocation rather than solo extortion. |
	| **Hunter** | **Game Hunter** | Assess game populations, stalk approved prey, harvest meat/hides and choose sustainable hunting areas. | Wild game resource yields and stalking. |
	| | **Trapper** | Select trap-site corridors, deploy devices, return to check capture status and recover or reset traps. | Persistent placed traps and revisit schedules. |
	| | **Monster Hunter** | Research nonordinary creature threats, prepare specialized equipment, track dangerous targets and claim contracts. | Monster-specific preparation and contracted elimination. |
	| | **Bounty Hunter** | Verify a lawful active warrant, follow suspect evidence, attempt capture and return the target or proof. | Person-specific warrants and legal custody terms. |
	| | **Predator Hunter** | Identify dangerous wildlife threatening people, livestock, or traveled routes; assess active threats, track their locations, and drive off or kill threats when justified. | Threat mitigation driven by observed danger and settlement/route safety, rather than harvesting game, placing traps, pursuing extraordinary creatures, or collecting criminal warrants. |
	| **Laborer** | **Builder** | Receive authorized building plans, sequence construction stages, consume materials and complete structures. | New structural project stages and site dependencies. |
	| | **Road Worker** | Inspect transport infrastructure, identify blockages or decay, repair roads/bridges and reopen routes. | Route operability and scheduled civil maintenance. |
	| | **Dockworker** | Allocate berths, unload/load arriving waterborne cargo and reconcile port manifests. | Vessel arrival timing and port transfer, not general hauling. |
	| | **Repair Worker** | Diagnose damage to existing supported structures, request parts and restore working condition. | Damage assessment and restoration; not new construction. |
	| | **Excavator** | Survey digging assignments, clear earth, dig approved foundations/trenches and manage excavation stability. | Terrain preparation and excavation, not building assembly. |
	| **Clergy** | **Sun Priest** | Conduct public Sun rites, hear confessions, and oversee public altars and liturgical schedules. | Public solar rites, confession and altar oversight; not private hospitality contracts or funerary records. |
	| | **Moon Priest** | Perform hospitality, contract and recitation rites; maintain temple custom and mediate disputes under Moon observance. | Hospitality/contract/recitation rites, temple custom and mediation; not public Sun liturgy or graveyard duties. |
	| | **Funerary Priest** | Conduct graveyard and memorial rites, maintain death records and guide burial or memorial practice. | Death/memorial rites and records; not ordinary altar service or doctrinal instruction. |
	| | **Faith Instructor** | Teach doctrine and practice guidance, and direct petitioners toward existing Faith-training actions already present in the world. | Instruction and direction to real Faith practices; awards no Faith XP itself and grants no free prayer unlocks. |
	| | **Shrine Keeper** | Maintain altar stock, burners, offerings and shrine access under established rules. | Shrine logistics and access control; provides no free offerings, infinite restoration or invented Faith resources. |
	| **Civic Services** | **Banker** | Operate deposit and withdrawal ledger services, maintain account records and enforce balance limits. | Ledger-based deposit/withdraw service and records; creates no money and holds no independent law authority. |
	| | **Innkeeper** | Manage lodging, keep a guest register and coordinate room service for paying guests. | Lodging and guest-register service; not tavern drink service or museum display. |
	| | **Bartender** | Serve drinks, manage bar stock and support ordinary pub social interaction. | Drink service and pub social facilitation; not lodging management or administrative permits. |
	| | **Clerk** | Process records, issue permitted documents, manage queues and provide administrative service. | Records, permits and administrative queues; holds no law-enforcement authority. |
	| | **Curator** | Arrange museum exhibits, maintain collection display and manage guest viewing of existing items. | Exhibit and collection display service; generates no items and does not invent provenance. |
	| **Leatherworker** | **Tanner** | Cure rawhide and hides into usable leather under existing material and ownership rules. | Hide-to-leather curing; not pattern cutting, stitching, footwear or armor production. |
	| | **Leather Cutter** | Cut leather patterns and components from prepared stock for downstream assembly. | Pattern and component cutting; not curing, stitching, footwear specialization or armor. |
	| | **Leather Stitcher** | Assemble leather goods from cut components using existing tools and recipes. | Assembly of leather goods; not tanning, cutting specialization, cobbling or protective armor. |
	| | **Cobbler** | Produce and fit leather footwear from authorized materials and patterns. | Leather footwear production; not general garments, armor or unrelated leather goods. |
	| | **Leather Goods Repairer** | Repair non-armor leather goods, restoring condition without changing tier or creating armor. | Repair of non-armor leather items; protective leather armor remains Armorer work. |
	| **Unemployed** | **Hobo** | When hunger, exhaustion, or safety fall below tolerable limits, seek accessible food, rest, help, or legal charity; only when desperate consider taking available necessities, avoid confrontation and escape rather than initiate violence. | Need-threshold survival triage and reluctant necessity theft; never opportunistic profit-seeking, planned burglary, highway robbery, or armed predation. |
	| | **Job Seeker** | Survey known employers and viable vacancies, weigh wages against skills, risk and travel, request employment, and accept a verified appointment when offered. | Active pursuit of paid employment and validated reassignment; not routine begging, displacement recovery, or work performed without an employer. |
	| | **Displaced Resident** | After losing an actual home or settlement access, search for a lawful temporary place to stay, contact known relations, and plan relocation or return if conditions recover. | Event-triggered housing/displacement recovery tied to a lost residence; not chronic street survival or searching primarily for wages. |
	| | **Retiree** | Manage remaining income and possessions, maintain established relationships, visit familiar places, and share personally known information when socially appropriate. | Voluntarily inactive livelihood centered on stable personal routines and social ties; no autonomous professional production or magical skill instruction. |
	| | **Household Dependent** | Remain within a real supporting household, coordinate daily personal needs with that household’s available resources, and seek help from its members when essential support fails. | Resource dependence on a specific existing household/relationship, not independent homelessness, job hunting, paid domestic service, or invented childcare mechanics. |
	| **Baron** | **Agrarian Baron** | Assess production failures across authorized local agricultural holdings, prioritize support requests, and commission existing Farmer/Hauler work only within granted estate control. | Estate agriculture patronage; does not plant, harvest, trade, or direct a whole regional economy as a Farmer or Count. |
	| | **Workshop Baron** | Assess supported workshops on owned property, decide which material shortages merit authorized supply or production commissions, and track fulfillment. | Local workshop patronage and commissioned Craftsman output; does not itself forge, appraise shop inventory, or manage county markets. |
	| | **Garrison Baron** | Monitor threats to a specific authorized holding, request assigned Guards/Knights where legitimately empowered, and react to estate-security failures. | Protection of a particular holding by delegated orders; does not investigate crimes as a Guard or command kingdom armies. |
	| | **Restoration Baron** | Identify damaged facilities on controlled property, prioritize funded repair tasks, and authorize eligible Laborers to restore services. | Restoration of directly held property, not personal construction, general settlement governance, or unsupported destruction. |
	| | **Household Baron** | Manage obligations among a specific titled household, resolve supported staff or dependent needs, and maintain relationships relevant to its continuity. | Household personnel and obligations; distinct from agricultural production, workshop output, fortification security, and repairs. |
	| **Count** | **Market Count** | Monitor registered supply shortages and trade interruptions across an authorized county, choose which existing merchants or transport links to support, and review market recovery. | County-wide supply continuity and market intervention, not running a shop or issuing kingdom-wide trade policy. |
	| | **Circuit Count** | Review forwarded legal incidents across the authorized county, request appropriate Guard coverage and follow up on jurisdictional reporting failures. | County-wide coordination of lawful enforcement resources; cannot conjure proof, create a bounty without a report, or personally make arrests as a Guard. |
	| | **Settlement Count** | Assess competing settlement needs within an administered county, select permitted building and road-work priorities, and authorize existing projects. | Allocation among local civic projects rather than fixing a structure personally or running a single estate. |
	| | **Revenue Count** | Review authorized county expenses, wages and income obligations, decide which supported services can be funded, and address recorded shortfalls. | County-level financial continuity using existing monetary transfers; cannot mint money or invent taxes or forced levies. |
	| | **Patronage Count** | Assess existing local barons, employers and community relationships for permitted appointments, mediation or recognition through authorized dialogue and office actions. | County-scale social appointments and mediation; rank advancement requires authored eligibility and cannot be created randomly. |
	| **Duke** | **Frontier Duke** | Compare supported border-site threat reports across administered territories, prioritize reinforcement resources and authorize responses through existing Knights or institutions. | Cross-territory frontier triage; does not patrol physically as a Border Knight or autonomously start wars. |
	| | **Logistics Duke** | Identify bottlenecks between regions under lawful influence, prioritize existing road or shipment restoration, and coordinate permitted Hauler work. | Interregional transit continuity, not operating cargo as a Hauler or managing local shop prices. |
	| | **Marshal Duke** | Evaluate readiness and assignment gaps among already subordinate military groups, authorize permitted escort, protection and patrol commitments. | Assignment of existing military personnel across holdings, not frontier-only threat evaluation or physical Knight combat. |
	| | **Court Duke** | Resolve eligible disputes between recognized houses and offices through established relationships, meetings, and faction permissions. | Inter-house mediation at ducal scope, not routine county appointments or unconstrained treaties. |
	| | **Domain Duke** | Assess operating failures across multiple administered estates/counties and prioritize authorized upkeep, labor and property recovery. | Portfolio-level estate/service continuity, not a Baron’s single holding or a Count’s single settlement plan. |
	| **King** | **Administrator King** | Review kingdom-level reports on funded services and institutional performance; authorize feasible priorities through existing officials and budgets. | Kingdom-wide administrative direction through delegated offices; not direct settlement micromanagement or free resource creation. |
	| | **Lawgiver King** | Review existing jurisdictional enforcement patterns and consider only authored/configured lawful changes that the sovereign office is permitted to authorize. | Kingdom-level legal policy decisions within existing jurisdiction mechanics; cannot fabricate evidence, rewrite laws freely, or instant-report crimes. |
	| | **Diplomat King** | Evaluate known relations with other recognized rulers and factions, receive credible approaches, and select permitted dialogue/relationship responses. | External relations and negotiated standing; no automatically generated treaties, wars, or kingdom transfers. |
	| | **Defender King** | Assess verified threats against the kingdom and set supported defensive priorities through existing authorized Knights, Guards and institutions. | Strategic defense of the kingdom, distinct from military personnel scheduling and never an unrestricted conquest AI. |
	| | **Patron King** | Review authored petitions, legitimate services and existing eligibility for honors or appointments, then grant, refuse or postpone authorized recognition. | Recognitions and appointments tied to established title/office rules and authored conditions; no automatic inheritance or random promotion chain. |

	### Player-visible NPC life and intervention — established design direction; tuning open

	**Simulation contract:** The 100 concrete subtypes are not merely background work scripts. Every concrete subtype must create an **observable routine or dilemma**, at least one **grounded player participation/assistance route**, at least one **grounded disturbance or competition route**, an **NPC-initiated approach or situational response**, and a **persistent outcome** using RPGGAME’s already established systems. These are **behavioral design targets**, not one hundred new minigames, interfaces, items, creature types, or a requirement to finish all 100 in the current devmap build. The role/subtype taxonomy and existing gameplay action APIs remain authoritative.

	**The five faces of an NPC encounter:**

	1. **Watch:** The player can see the NPC notice conditions, prepare, perform a real task, make progress, encounter obstacles, finish or abandon the task, and move on. NPCs do not stage a performance just because the player is nearby.
	2. **Engage / help:** Existing contextual actions, dialogue, gathering, production, trade, combat and world-state permissions make the player a participant. Offering legitimate assistance must modify the **same** assignment, stock, resource site, threat, or relationship that the NPC is using, with no duplicated output.
	3. **Disturb:** The player can interrupt, compete, steal, obstruct, mislead, threaten, fight or otherwise alter a supported situation. The outcome is grounded in what could be perceived, owned, moved, lost, or reported—not in an arbitrary “disruption meter.”
	4. **Be approached / reacted to:** The NPC may initiate a short, context-appropriate exchange: request help, warn about danger or trespass, challenge an inconsistent story, request passage, offer trade, confront a thief, or recognize a past ally. The **subtype and perceived facts** constrain which reason for approaching is valid.
	5. **See a ripple:** Changes propagate through ownership, inventory, skills/XP, markets, jobs, travel, jurisdiction, faction standing, relationships, credible rumors or authored quest conditions. The world does not fabricate stock, arrests, guilt, task completion, or universally shared knowledge.

	**Presentation discipline:** Ground these encounters in readable microbehavior—looking toward a relevant target, pausing work, moving to inspect a missing input, addressing a known person, turning away from danger, resuming an interrupted task. Short authored context lines and reusable gestures can signal intent. A busy NPC should acknowledge or refuse the player only when appropriate. No constant chatter, scripted spectacle loops, omniscient recognition, global reaction radius, or forced quest markers. Player attention and proximity affect presentation fidelity, **not whether the underlying task exists**.

	**Contextual interaction rule:** A valid target presents only its currently relevant actions using the established primary action and secondary menu; choices depend on subtype, circumstances, relationship, player capabilities and local authority. NPC-initiated interactions use the same eligibility checks. A player can ignore an invitation, keep walking, simply observe, or act later if the opportunity remains available. Never force dialogue just because the player passes someone.

	**Reaction ladder:** Perceive or learn → acknowledge → choose to tolerate, refuse, confront, adjust work, seek aid, flee, defend, or make a credible report → remember the result → resume a feasible goal. A personality- or relationship-driven reaction may differ between two NPCs with identical subtypes. A witness's observation still must reach a Guard to establish a bounty under the existing crime contract; a worker's complaint alone is not an instant arrest.

	**Subtype encounter matrix (all 100 subtypes)** — each row describes a distinctive *player-facing situation*. “Help” and “disturb” are examples of supported mechanisms, not an exhaustive list of inputs, guaranteed dialogue, or new quest content. NPCs can also use shared survival, ordinary socializing, nonoccupational trade, self-defense, and legal witnessing independent of subtype.


	**Farmer**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Crop Farmer** | Checks field condition, chooses the next plot, tends crops, and harvests when ready. | Join a harvest or provide crop work through existing Harvesting and trade actions. | Take a ripe crop, block access to a plot, or exhaust its available yield. | Requests harvest help during a short window; warns a trespasser; reschedules when fields are inaccessible. | Changed field yield alters food supply, wages, and the next production task. |
	| **Livestock Farmer** | Moves between grazing, feed, and animal-welfare work as herds change condition. | Help tend an assigned herd or deliver existing feed/supplies. | Interfere with an enclosure, take husbandry output, or disrupt feeding access. | Seeks help when herd care is blocked; confronts a witnessed intruder; prioritizes animal safety over sales. | Herd condition changes future husbandry output and local food/material stock. |
	| **Orchard Keeper** | Inspects perennial trees, prunes where needed, and times fruit collection. | Help collect ready fruit or fulfill a seasonal picking task. | Strip unready fruit, take harvested produce, or damage a supported tree resource. | Warns against damaging trees; redirects pickers after loss; asks for help at harvest time. | Tree condition and seasonal yields affect future supply, not only today's stock. |
	| **Herbal Grower** | Checks herb-bed viability, separates harvest-ready beds, and grades gathered growth. | Gather ripe herbs under permission or supply an existing herbal order. | Take high-value herbs or deplete a working bed before an assigned harvest. | Questions suspicious picking; requests harvesting help; moves work to viable beds. | Available herb stock and harvesting XP change; no invented potion or spell ingredients. |
	| **Vineyard Keeper** | Tends vines and delays or advances collection according to known ripeness. | Assist timed grape picking or fulfill an existing grape delivery. | Remove grapes prematurely or interrupt the narrow picking window. | Calls for additional hands when ready; reacts to missing or spoiled yield by changing orders. | Grape volume and the schedule of existing processing/trade requests change. |


	**Fisherman**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **River Fisherman** | Tests riverbank sites, responds to changing flow, and relocates when a spot underperforms. | Fish beside the worker where access permits or transport an existing catch. | Crowd out an accessible spot, take a catch, or repeatedly deplete a shared fishing site. | Comments on a poor location, disputes a stolen catch, or relocates to another known bank. | River-site availability and fish supply update through the shared resource ledger. |
	| **Coastal Fisherman** | Times shore fishing and return trips around accessible tidal windows. | Help land and deliver a catch before access closes. | Delay the worker's access or take a landed catch before transfer. | Urges quick assistance near a closing window or cancels a missed outing. | Catch delivery shifts according to actual travel and fishing time. |
	| **Deep-Water Fisherman** | Prepares an offshore trip, leaves when conditions permit, and later unloads a recorded catch. | Help with a legitimate loading/unloading or catch-delivery job when the trip returns. | Steal recorded cargo or obstruct departure/return transfers. | Requests help at a time-sensitive unload; postpones travel if safe passage is unavailable. | Trip, cargo, and market stock stay one consistent shipment—not free fish spawned on return. |
	| **Shellfish Gatherer** | Approaches reachable beds, gathers during access windows, and leaves exhausted sites. | Work an open bed with permission or help collect its currently available yield. | Deplete the same bed first or steal sorted catch. | Warns when access is unsafe; relocates to a known viable bed; disputes missing output. | Bed depletion and replenishment affect the next gatherer's opportunities. |
	| **Lake Net Fisherman** | Places nets in allowed lake areas, later checks fill state, and retrieves catch. | Help check or retrieve a deployed net using supported Fishing interactions. | Tamper with or empty an existing net before its owner returns. | Investigates an empty net, changes net-placement priorities, or confronts a witnessable theft. | Persistent net state links time advancement, catch, and ownership. |


	**Woodcutter**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Logger** | Chooses authorized trees, clears a falling area, fells, and stages logs for collection. | Clear a work area or help move staged logs into an approved order. | Stand in an active fall zone, take logs, or fell assigned trees first. | Issues a safety warning, pauses work for obstruction, or reports missing logs. | Changed timber inventory affects Carpenter, Builder, and Hauler work. |
	| **Forester** | Surveys woodland condition and decides which existing trees may be harvested or protected. | Assist surveys or follow a marked selective-cut assignment. | Cut protected trees or ignore a managed area's restrictions. | Warns against harmful cutting; alters permission decisions; reports a confirmed infraction. | Woodland productivity, lawful access, and resource recovery change. |
	| **Resin Tapper** | Revisits designated living trees and checks whether their taps can be collected. | Help collect the existing tapped-tree yield without felling the source. | Take collected resin or damage the usable tree before the next visit. | Returns to investigate missing output and chooses another eligible tree. | Renewable tree-resource state and existing Craftsman supply change. |
	| **Charcoal Burner** | Stages cut wood, monitors a controlled burn, and judges when conversion is complete. | Deliver wood or assist an authorized fuel-production action using Firemaking. | Remove input wood, interrupt site access, or take the finished fuel. | Requests missing fuel stock; changes burn timing; confronts witnessed theft. | Wood input and fuel output transfer through real inventories and work time. |
	| **Firewood Cutter** | Selects lower-grade wood, splits it, and assigns bundles to nearby heating demand. | Help split or fulfill an assigned short-distance fuel delivery. | Take prepared fuel or block access to the woodpile. | Requests immediate help when household demand exceeds ready supply. | Available household fuel and downstream hauling work change. |


	**Miner**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Ore Miner** | Checks metal seams, selects productive excavation, and sorts the extracted ore. | Mine the same permitted vein or move ore toward an active forging order. | Deplete an assigned vein, take staged ore, or block shaft access. | Changes seam after depletion; disputes theft; asks for help moving production stock. | Actual ore depletion propagates to forging, sales, and related prices. |
	| **Quarry Worker** | Inspects the face, selects safe cuts, and stages usable building stone. | Help cut or move approved building material for a Construction job. | Remove already cut blocks or stop safe quarry-face access. | Suspends an unsafe cut, reassigns the face, or asks for removal of obstructions. | Construction stock and project progress reflect the delivered stone. |
	| **Gem Prospector** | Surveys uncertain ground, samples likely deposits, and changes sites after weak finds. | Share a discovered location or participate in an allowed prospecting task. | Follow to exploit a newly found site or extract the finite deposit first. | May conceal a profitable location from an untrusted observer or warn of a poor claim. | Prospecting knowledge follows believable information paths; resources remain finite. |
	| **Clay Digger** | Selects workable deposits by current moisture and stages usable clay. | Help excavate an approved clay patch or carry its yield to existing work. | Exhaust an assigned patch or take collected clay. | Moves to a workable patch, requests access, or stops digging when ground conditions fail. | Usable clay stock and corresponding construction/production tasks change. |
	| **Coal Miner** | Evaluates combustible seam access, extracts fuel, and stops when the site becomes unsafe. | Help mine or transport authorized fuel to an existing worker. | Remove staged coal, obstruct extraction, or create an unsafe work interruption. | Warns of known extraction danger, halts work, and reports lost fuel. | Fuel supply alters supported Firemaking and production availability. |


	**Craftsman**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Blacksmith** | Checks available metal, selects a tool/weapon order, works a forge, and finishes a real item. | Bring metal, commission a supported tool/weapon, or arrange an existing repair. | Take forge input or unfinished stock; interrupt workstation access. | Quotes a job, requests supply, pauses when inputs vanish, and remembers repeat interference. | Equipment/tool inventory, work completion, and Smithing XP follow actual production. |
	| **Armorer** | Checks an order's wearer/armor requirements and fits a protective piece. | Commission armor, provide materials, or bring a supported piece for repair. | Remove armor inputs or interfere with a fitting or collection. | Checks eligibility, asks for a fitting, refuses suspicious interference, and reschedules. | Armor availability and legitimate equipment service are affected, not weapon forging. |
	| **Carpenter** | Measures an approved furniture/joinery order and uses stored timber to assemble it. | Commission a supported furnishing or provide approved wood for an order. | Take measured materials or block a furniture workstation. | Asks for missing wood and renegotiates time when work is disrupted. | Housing fixtures, craft stock, and delivered furniture orders reflect real output. |
	| **Cook** | Plans batches from edible stock and current meal demand, then prepares servings. | Sell existing ingredients, request food, or assist a Cooking task. | Steal perishables, withhold needed stock, or interrupt access to preparation. | Changes the available offering, asks for ingredients, or confronts theft. | Food supply and spoilage state affect nearby Merchant and consumer decisions. |
	| **Tailor** | Takes measurements, cuts assigned fabric, and completes fitted garments. | Request an approved garment/alteration or provide cloth for a commissioned job. | Remove measured fabric or take completed clothing before delivery. | Requests fitting, delays an affected order, and may remember identity-related deception. | Clothing availability, ownership, and existing appearance/disguise cues change. |


	**Merchant**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **General Store Owner** | Surveys many categories, shifts shelf allocation, trades, and reorders ordinary stock. | Buy/sell permitted goods or offer supply to cover a confirmed shortage. | Steal shelf goods or withhold a promised replenishment. | Suggests routine trade, flags a shortage, refuses wanted buyers, or identifies repeat customers. | General stock variety, local prices, and merchant trust change. |
	| **Weapons Merchant** | Examines weapon condition/tier, compares current weapon demand, and advises eligible buyers. | Trade existing weapons, seek an eligible upgrade, or sell a lawful replacement. | Steal a weapon, attempt illicit resale, or threaten the shopkeeper. | Comments on a weapon's tier, refuses stolen goods, or responds cautiously to armed intimidation. | Weapons stock, lawful ownership, and local combat readiness shift. |
	| **Food Merchant** | Inspects perishables, rotates older batches, discounts goods near spoilage, and orders fresh food. | Supply edible stock or purchase what is currently fresh. | Remove an imminent delivery, buy out scarce food, or steal a perishable batch. | Calls for rapid resupply and changes offers based on genuine stock age. | Food availability, waste, and short-cycle producer demand change. |
	| **Traveling Merchant** | Compares regional margins, chooses a viable route, and makes trades along the journey. | Trade at a stop, offer legitimate carriage/protection, or share known route conditions. | Intercept cargo, obstruct a route, or create credible danger along it. | Offers trades while passing, seeks escort when threatened, or takes another known route. | Goods are redistributed between actual markets only when their journey succeeds. |
	| **Fence** | Checks provenance and local risk before covertly exchanging flagged stolen property. | Sell stolen property through the existing illicit trade path if accepted. | Inform Guards, steal the fence's inventory, or draw unwanted attention. | Offers a low-risk exchange to a trusted contact; refuses exposed transactions; may withdraw. | Stolen-item custody and criminal-network knowledge change; no laundering by ordinary shops. |


	**Hauler**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Courier** | Receives a small specific delivery, chooses the fastest valid route, and confirms receipt. | Entrust a supported message/parcel delivery or help with a known destination. | Intercept an active delivery or block the recipient's access. | Asks for directions/help when delayed; checks recipient identity; reports failure to sender. | Knowledge or property transfers only upon a valid handoff, not when work is assigned. |
	| **Wagon Driver** | Checks single-vehicle load capacity and passable roads, moves one bulk consignment. | Help load/unload an accepted shipment or clear a blocked route. | Steal part of the load or make the selected route impassable. | Warns bystanders near the vehicle, requests access, and replans when roads fail. | Cargo remains in transit until physically or abstractly delivered, with no duplication. |
	| **Caravan Hauler** | Synchronizes a multi-participant journey, inventories group cargo, and keeps departures together. | Join a supported escort job or help prepare the convoy's declared cargo. | Delay a participant, split formation, or target goods while the group is exposed. | Asks for protection, halts to regroup, and changes departure based on route threats. | Coordinated trade flow and escort commitments change across locations. |
	| **Porter** | Completes nearby carry assignments in a precise pickup/drop-off order. | Help shift a recorded stock transfer within a workplace or district. | Place cargo in the wrong location, take it, or block the designated station. | Requests passage, notices a missing handoff, and reroutes short transfers. | Facility inventory and available workstation inputs update only at valid endpoints. |
	| **Supply Runner** | Monitors urgent existing supply requests and selects the most time-sensitive delivery. | Assist an urgent delivery or grant a clear route through a supported interaction. | Delay or redirect an urgent load, or take its contents. | Approaches eligible helpers, reprioritizes a newer emergency, and reports failure. | Which existing activity resumes first depends on actual delivered supply. |


	**Guard**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Town Guard** | Makes neighborhood rounds, listens to reports, and follows up local incidents. | Report witnessed wrongdoing or give relevant information about an existing case. | Lie, obstruct questioning, or commit an offense in view. | Recognizes known residents, requests testimony, questions suspicious behavior, and seeks evidence. | Local crime cases and reports retain provenance; a rumor alone does not create a bounty. |
	| **City Guard** | Scans populated districts, approaches active disturbances, and coordinates nearby reinforcements. | Ask for assistance or report an observable disturbance. | Provoke a public fight or divert available responders from a real incident. | Orders dispersal, calls authorized support, and changes patrol focus after a disturbance. | District guard coverage and recorded incidents respond to real events. |
	| **Gate Guard** | Watches a checkpoint, evaluates approaching travelers and relevant access conditions. | Request legal passage or present existing status/permission evidence. | Attempt to slip past, conceal illegal cargo, or impersonate permitted affiliation. | Initiates an inspection, permits/denies passage, or reports a confirmed crime. | Checkpoint access and suspicion change without inventing universal identification items. |
	| **Prison Guard** | Checks existing custody, visitation, and secure transfer states. | Request an allowed visit, surrender, or complete a lawful release condition. | Attempt escape, bribe, or obstruct a prisoner transfer. | States custody conditions, intervenes in escape, and transfers a prisoner if authorized. | Sentence/custody records persist; prisoner status is not just a teleport trigger. |
	| **Rural Patrol Guard** | Moves between distant roads, notices isolated incidents, and chooses patrol detours. | Report a road threat or share an eyewitness location. | Mislead the patrol, obstruct a circuit, or attack a traveler while observed. | Approaches suspicious travelers, investigates a report, or seeks distant reinforcement. | Road safety knowledge and jurisdictional reports propagate only through contact. |


	**Knight**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Household Knight** | Stays close to an assigned household, assesses threats to its people and property. | Offer relevant service to the household or assist a legitimate protection task. | Threaten a protected member, intrude on property, or attack the patron's assets. | Challenges suspicious proximity, warns intruders, or seeks help for the patron. | Household security, patron relations, and noble standing change, not ordinary police authority. |
	| **Border Knight** | Inspects assigned border positions and reacts to known incursions or vulnerability. | Share credible border threat information or assist an authorized defense task. | Sabotage a supported position or give misleading information about an incursion. | Questions suspicious movements and calls for reinforcement under actual threat. | Border readiness and faction security respond to valid local events. |
	| **Escort Knight** | Tracks a designated principal's movement, keeps formation, and evaluates route danger. | Offer a safe route or join a supported escort action. | Follow too closely, split an escort party, or attack the protected traveler. | Warns a perceived pursuer, asks for passage, and shields the principal if attacked. | Principal location and safety determine success; escort does not independently run a shop. |
	| **Knight Errant** | Hears sanctioned service petitions, evaluates which to accept, travels and resolves them. | Bring a legitimate grievance or cooperate in an accepted task. | Offer false evidence or obstruct an ongoing sworn-service assignment. | May offer help after observing wrongs, question competing claims, or challenge a proven offender. | Resolved petitions change relationships and authored opportunities without random major plot creation. |
	| **Order Knight** | Follows known institutional assignments, respects vows and protects authorized order interests. | Cooperate with an allowed order duty or demonstrate recognized allegiance. | Impersonate heraldry or interfere with an authorized order activity. | Recognizes affiliation cues, demands clarification, or refers a dispute through its command chain. | Order trust/permissions respond to recorded conduct; symbols alone grant no office. |


	**Thief**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Pickpocket** | Studies proximity, carried valuables, attention and escape lines in busy areas. | Cooperate only through existing distraction or return/recover stolen property via supported actions. | Expose the attempted theft, intercept the thief, or watch the victim deliberately. | May target the player's accessible possessions, disengage when noticed, or plead/escape when caught. | Real item loss and witness memory can lead to a report; no automatic wanted state. |
	| **Burglar** | Observes building occupancy, access conditions and owned storage before an attempted entry. | Offer information about an actual opening or intervene to prevent a burglary. | Secure an entrance, interrupt lockpicking, or report a witnessed intrusion. | May enter the player's owned premises if opportunity permits; flees or confronts discovery. | Premises access, moved items, and identifiable crime evidence persist. |
	| **Highwayman** | Waits for isolated travelers, judges their readiness and blocks passage to demand valuables. | Surrender, negotiate, refuse, or help a threatened traveler using existing talk/combat actions. | Call for help, resist with combat, or deny an easy roadside approach. | Stops the player directly, states a demand, assesses resistance, then robs or retreats. | Recorded robbery changes cargo, relationships and road-danger reports. |
	| **Smuggler** | Chooses a route around known inspections and evaluates concealment of illicit cargo. | Provide an already known route or trade through supported illicit channels. | Alert a checkpoint, expose cargo, or interrupt the covert delivery. | May cautiously seek a trusted route contact; abandons a transaction under scrutiny. | Contraband position and any witnessed offenses influence border enforcement. |
	| **Bandit** | Assesses grouped force, selects exposed targets and coordinates an armed seizure. | Defend a victim, oppose a raid, or cooperate only where existing faction relations allow. | Break the assault through combat or prevent the group reaching its target. | May threaten or ambush the player/transport; retreats if the group loses its advantage. | Stolen shipment state, combat casualties, and perceived road security change. |


	**Hunter**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Game Hunter** | Observes lawful game activity, stalks a chosen target, and recovers permitted animal yields. | Offer a known wildlife location or help with an existing hunt/resource order. | Interrupt stalking or take the harvest from a successful hunt. | Warns nearby movement away from a stalking route or offers a legitimate trade. | Wildlife availability and meat/hide stock respond to real kills and gathering. |
	| **Trapper** | Selects a permitted corridor, sets persistent traps and returns to check actual outcomes. | Help check an existing trap location or turn over its recovered yield. | Empty, tamper with, or remove an existing trap before its owner arrives. | Investigates disturbed trap state and changes checking route or trust. | Traps, wildlife and ownership resolve once, including across time skip. |
	| **Monster Hunter** | Prepares for a known extraordinary threat, follows grounded sightings and attempts a difficult contract. | Report a credible sighting or assist a supported combat objective. | Mislead the hunter, interfere with preparation, or take an agreed reward before delivery. | Warns about an observed threat, seeks relevant support, or declines an unworkable pursuit. | Threat removal and related authored contract conditions change; no new creature types. |
	| **Bounty Hunter** | Checks a valid active warrant, follows known evidence and seeks a lawful capture. | Give relevant witness testimony or surrender if legitimately wanted. | Hide a warranted suspect, misdirect pursuit, or resist an arrest attempt. | May track and approach the wanted player; identifies the applicable jurisdiction before acting. | Capture/reporting changes custody and bounty records, not arbitrary accusations. |
	| **Predator Hunter** | Responds to credible wildlife threats near people, herds, or traveled areas. | Report a real threat or assist in protecting an existing route or herd. | Distract the defender, misdirect their tracking, or obstruct a safety response. | Warns travelers away from a known hazard and intervenes against an immediate observed threat. | Reduced danger affects known route/herd safety, not an invented species or companion system. |


	**Laborer**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Builder** | Follows a valid building plan, requests staged resources, and completes work in sequence. | Deliver required material or participate through existing Construction actions. | Take staged stock or obstruct the approved building area. | Requests missing materials, warns against a worksite hazard, or pauses until access returns. | Actual project completion changes an allowed structure's persistent state. |
	| **Road Worker** | Surveys road/bridge operability, chooses maintenance actions, and records when passage resumes. | Clear an obstacle or help with an approved road repair. | Block the road again or take repair materials. | Warns approaching travelers and requests help if a route remains closed. | Passability changes Hauler, Merchant, Knight and Guard travel decisions. |
	| **Dockworker** | Reconciles an arriving vessel's recorded cargo with a port-side transfer. | Assist a legitimate loading operation or report a cargo discrepancy. | Remove goods before a recorded handoff or obstruct unloading access. | Requests clearance, challenges a mismatched manifest, or postpones an unsafe transfer. | Shipments and port stock change at verified handoffs, never at both ends. |
	| **Repair Worker** | Inspects a damaged supported structure and chooses the repair required to restore function. | Provide repair materials or help perform an existing Construction repair. | Repeat damage, conceal broken state, or take materials before repair. | Requests access, identifies damage, and warns residents when a fixture stays unusable. | Functionality and condition of an already existing property actually change. |
	| **Excavator** | Checks an authorized footprint, removes earth in safe stages, and clears a site for later building. | Clear an assigned digging area or help prepare a permitted foundation. | Interrupt access, remove staged material, or undermine an active excavation. | Warns someone entering a work area and stops when conditions are unsafe. | Approved site-preparation state gates later Builder work; no uncontrolled terrain destruction. |


	**Clergy**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Sun Priest** | Conducts public Sun rites, tends a public altar schedule and receives confessions under established custom. | Attend a public rite, make a legitimate confession, or supply authorized altar materials. | Disrupt a public ceremony, steal altar stock, or prevent access to a scheduled service. | Calls the faithful to a legitimate rite, warns against desecration, or directs a petitioner to an available altar. | Rite participation and altar state change; no free Faith XP or prayer unlocks from attendance alone. |
	| **Moon Priest** | Performs hospitality, contract or recitation rites and mediates disputes under temple custom. | Seek mediation, fulfill a supported contract-rite obligation, or respect temple hospitality rules. | Break a mediated agreement, desecrate a Moon observance space, or abuse temple hospitality. | Offers mediation when both parties are present, or declines when authority or facts are insufficient. | Mediation outcomes and temple custom records persist; no invented legal power. |
	| **Funerary Priest** | Conducts graveyard or memorial rites and updates death records for eligible burials. | Commission a legitimate burial/memorial service or supply permitted funerary materials. | Desecrate a grave, steal memorial goods, or obstruct a lawful burial. | Schedules a rite when remains and permissions are valid; refuses unsupported requests. | Death records and memorial state update; ties to Undertaker/graveyard recovery without duplicating corpse claims. |
	| **Faith Instructor** | Instructs doctrine and practice, and points petitioners toward existing Faith-training actions in the world. | Receive instruction and then perform real Faith actions (burial, prayer-assisted combat, purification) yourself. | Interrupt instruction, spread false doctrine as fact, or demand free prayer unlocks. | Directs the player to a real altar, burial site or practice rather than granting XP or unlocks. | Knowledge of practice routes; instructor awards no Faith XP and unlocks no prayers. |
	| **Shrine Keeper** | Stocks altars, tends burners, receives offerings and controls shrine access under rules. | Deliver authorized offerings or burner fuel, or request legitimate shrine access. | Steal offerings, extinguish required burners without cause, or force unauthorized access. | Refuses free or infinite restoration; accepts only valid stocked offerings and access rules. | Altar stock, burner state and offering records change; no free Faith restore beyond established sources. |


	**Civic Services**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Banker** | Maintains deposit/withdraw ledgers and enforces account balance limits for authorized clients. | Deposit or withdraw through the real ledger service using accessible funds. | Attempt unauthorized ledger access, forge balances, or demand money creation. | Refuses overdrafts and invalid claims; records only real transfers. | Account balances and transaction records update; no money is created. |
	| **Innkeeper** | Assigns rooms, keeps a guest register and coordinates lodging service for paying guests. | Pay for a room, register as a guest, or request ordinary room service. | Steal from rooms, falsify the register, or occupy space without payment. | Offers available rooms at stated terms; refuses unpaid or blocked occupancy. | Occupancy, register and lodging stock change; no free housing. |
	| **Bartender** | Serves drinks from real stock and supports ordinary pub social interaction. | Order and pay for drinks, or participate in open social exchange at the bar. | Steal drinks/stock, start a fight that empties the room, or refuse payment after service. | Serves paying customers; may refuse service to known troublemakers under local rules. | Drink stock and pub atmosphere change; no free alcohol or invented social minigame. |
	| **Clerk** | Processes records, issues permitted documents and manages administrative queues. | Submit a valid request, wait in queue, or supply required documentation. | Forge documents, jump a lawful queue, or demand authority the Clerk does not hold. | Issues only permitted papers; has no arrest or bounty power. | Records and permit state update; law enforcement remains Guard/Knight jurisdiction. |
	| **Curator** | Arranges exhibits, maintains collection display and manages guest viewing of existing items. | View an exhibit, request information about a displayed item, or donate an eligible piece if rules allow. | Steal displayed items, damage exhibits, or demand generated artifacts. | Protects collection integrity; displays only real owned items. | Exhibit arrangement and provenance remain authentic; no item generation. |


	**Leatherworker**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Tanner** | Cures real rawhide/hides into leather under material and ownership rules. | Supply eligible hides or collect finished leather through legitimate trade. | Steal hides/leather, spoil a curing batch, or claim armor production. | Accepts valid hide stock; refuses armor or textile work outside specialty. | Leather stock and hide consumption update; armor remains Armorer domain. |
	| **Leather Cutter** | Cuts patterns and components from prepared leather for downstream assembly. | Deliver leather stock or collect cut components under an existing order. | Take cut pieces, damage patterns, or demand finished goods without work. | Works only with available leather; does not tan or stitch finished goods as primary duty. | Component inventory changes; no new armor tier or slot. |
	| **Leather Stitcher** | Assembles leather goods from cut components using existing tools and recipes. | Supply components or request a supported leather-good order. | Steal work-in-progress, interrupt assembly, or demand armor. | Completes registered non-armor goods; protective armor remains Armorer. | Finished leather goods enter stock; Tailor boundary (textiles) respected. |
	| **Cobbler** | Produces and fits leather footwear from authorized materials and patterns. | Commission footwear or supply leather for a valid cobbling order. | Steal shoes, damage lasts, or demand armor-grade boots as combat gear beyond existing rules. | Fits footwear; does not produce general garments or plate armor. | Footwear stock and condition update under ordinary item rules. |
	| **Leather Goods Repairer** | Repairs non-armor leather goods, restoring condition without tier changes. | Bring a repairable leather item and pay/material costs under existing rules. | Sabotage repairs, steal customer goods, or claim armor repair authority. | Restores non-armor leather; Armorer handles protective armor. | Item condition updates; no free tier bypass or new slot. |


	**Unemployed**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Hobo** | When in need, searches for accessible food, asks known locals for help, looks for a safe existing place to rest, and avoids dangerous people. | Share food/coin through existing transfers, offer permitted shelter or directions, or protect them from a present threat. | Refuse help, remove accessible necessities, displace them from a lawful resting place, or intimidate them; urgent scarcity may lead to a small attempted food theft. | May quietly ask for help when hungry; thanks or remembers aid; when threatened tries to withdraw or flee, not start a fight. | Actual hunger/safety state, ownership, lawful shelter access, social trust and any witnessed last-resort theft persist; no automatic criminal identity. |
	| **Job Seeker** | Checks reachable businesses or projects for known work, compares viable openings, and travels to make requests. | Tell them about genuinely available work, supply information to a legitimate employer, or create a supported opening through existing business activity. | Interfere with a real hiring opportunity, mislead about a vacancy, or disrupt an employer’s ability to pay. | May ask about an opening or decline work that conflicts with skills, pay or safety; follows up with an employer when appropriate. | An actual vacancy fills or remains open; job assignment, wages and eventual subtype transition update together. |
	| **Displaced Resident** | After an actual loss of residence, visits known contacts, checks existing legal lodging opportunities and judges whether returning home is possible. | Provide a lawful place to stay or help restore access to their existing property/settlement. | Block entry to their former home, deny assistance, steal remaining possessions or worsen a genuine access problem. | May ask known people for temporary accommodation or warn others about the event that displaced them. | Residence/location, property access, ties and relocation decisions persist until real recovery or resettlement. |
	| **Retiree** | Keeps a chosen routine, visits familiar people, uses savings prudently, and comments on events they personally know. | Trade fairly, spend time in dialogue, or assist with a concrete personal need without making them take a job. | Scam or steal savings, disturb their lawful routine, or antagonize close relations. | Recognizes regular visitors and may offer a fact from established memory or refuse an untrusted stranger. | Financial security and relationships change; shared knowledge retains its actual source; no invented advice-based XP. |
	| **Household Dependent** | Uses real household supplies, remains near supporting relations, and adjusts personal routines when support is interrupted. | Aid the specific household with food/money, restore legitimate property access, or help its members perform an existing obligation. | Remove household essentials, disrupt support relationships or displace the provider. | Asks known household members for assistance, seeks a familiar contact after a shortfall, and avoids unnecessary risk. | Household inventory, dependency relationship and survival conditions update; no invisible free maintenance. |

	**Baron**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Agrarian Baron** | Inspects reports of crop/herd output on owned holdings and decides which existing farm obligations deserve support. | Deliver actual resources, fulfill a Farmer or Hauler assignment, or negotiate a legitimate estate commission. | Steal estate harvest, interrupt farm access or sever a real supply transfer. | May offer permitted farm work, question trespass or seek replacements for a failed agricultural delivery. | Estate output, employment, money, relationships and actual shortages change; the Baron cannot invent harvests. |
	| **Workshop Baron** | Visits or receives updates on supported local workshops, comparing waiting orders with available materials. | Deliver contracted materials or complete an authorized production order using existing skills. | Take workshop stock, interrupt a Craftsman’s access or disrupt an active commission. | May commission a qualified player, reject unqualified bids or confront missing-order evidence. | Order status, stock, wages and Craftsman decisions propagate through the real economy. |
	| **Garrison Baron** | Reviews guard presence and recent credible incidents affecting a particular holding. | Report a real threat, escort an authorized person or assist lawful defense. | Threaten a holding, falsify an account, interrupt a patrol or attack assigned personnel. | May warn trespassers, request aid or authorize limited protection if their office permits. | Local duty assignments, credible reports and relationships update without spontaneous arrests or armies. |
	| **Restoration Baron** | Surveys approved damage reports on their property and waits for repair resources or available workers. | Provide actual materials, help through Construction or work an approved repair order. | Delay access, steal building materials or cause damage permitted by structural rules. | May request repair work or refuse access to a dangerous site. | Specific property condition, labor assignments, costs and service access change. |
	| **Household Baron** | Coordinates an established household’s obligations and responds to disruptions among dependents or staff. | Fulfill a supported household need, trade supplies or earn trust through authorized assistance. | Steal household possessions, spread a witnessed false claim or disrupt staff’s real assignments. | May request help for a household member, acknowledge loyalty or bar an untrusted visitor. | Household relationships, possessions and service continuity change without inventing succession. |

	**Count**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Market Count** | Reviews known county shortages and local trade flows, then prioritizes support for an affected market. | Resolve a verifiable supply problem, transport an existing shipment or make a relevant trade offer. | Intercept a shipment, hoard supply through legal trade or disrupt local access. | May seek a qualified contractor or explain a known shortage; cannot instantly infer its cause. | County market availability, commitments, prices and perceived reliability update. |
	| **Circuit Count** | Receives authentic reports from multiple local jurisdictions and assesses whether Guard coverage is adequate. | Provide credible witnessed information or assist a lawful patrol already authorized. | Mislead an official, interfere with delivery of a report or commit a real offense. | May request testimony or refer the player to an actual Guard; reports still require lawful handling. | Case knowledge, assigned patrol coverage and jurisdictional bounties update only by valid procedure. |
	| **Settlement Count** | Examines competing authorized road/building needs and schedules one supported civic project ahead of another. | Contribute materials, labor or known access information to an actual approved project. | Block a work site, steal stock or interrupt a selected project. | May solicit Construction help or postpone a project when costs/resources fail. | Settlement services, work queues, inventories and access reflect completed or stalled work. |
	| **Revenue Count** | Reviews existing county revenue and spending records and revises feasible expenditure priorities. | Fulfill a paid task, complete a lawful transfer or help restore genuine taxable/trading activity already represented. | Interrupt a funded job, steal recorded funds or damage a real revenue source. | May refuse unsupported expense requests, offer paid authorized work or discuss a real shortfall. | County funds, permitted wages, service availability and debts/obligations already modeled remain consistent. |
	| **Patronage Count** | Receives requests from known local officials or houses and weighs eligibility, loyalty and prior conduct. | Complete an existing authored service, truthfully support a candidate or demonstrate recognized standing. | Betray a patron, damage faction standing or spread a report through valid social channels. | May sponsor an eligible meeting or refuse recognition after an evidenced breach. | Relationships, offices and authored opportunities change only when supported by permissions and story data. |

	**Duke**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Frontier Duke** | Compares credible border reports from several holdings and reallocates allowed defensive resources. | Bring a truthful frontier warning or fulfill an authorized supply/escort request. | Interrupt reinforcements, falsify intelligence or exploit a real weak site. | May request verified assistance or issue a limited defensive warning to relevant parties. | Border readiness and existing assignment state change; no newly generated invasion or conquest. |
	| **Logistics Duke** | Inspects delivery delays across regional connections and identifies the transit link with the greatest supported impact. | Complete an existing Hauler contract, reopen a supported route or recover a stolen shipment. | Block a key road, intercept goods or delay a verified cargo transfer. | May seek a qualified delivery contractor or reroute a known shipment when feasible. | Transit status, market stock and route access remain causally linked across regions. |
	| **Marshal Duke** | Reviews available sworn forces and decides where authorized Knights/Guards should be assigned for current obligations. | Serve in an approved escort or convey reliable information about personnel availability. | Attack or distract forces, disrupt a sanctioned escort or impersonate an order without credentials. | May offer recognized service to an eligible player or insist on leaving a protected duty intact. | Assignments and personnel commitments update without creating soldiers or transferring forbidden authority. |
	| **Court Duke** | Receives conflicting statements from known houses and seeks an eligible mediated settlement. | Present witnessed facts, represent a recognized interest or repair a damaged relationship through authored dialogue. | Misrepresent a credible event, betray an entrusted party or obstruct attendance at a valid meeting. | May summon an authorized discussion or refuse to entertain an unsupported claim. | Recorded standing and existing obligations change; political catastrophes are not procedurally generated. |
	| **Domain Duke** | Examines underperforming properties across authorized holdings and assigns feasible repair/service support. | Repair a supported property, deliver resources or complete an already funded estate task. | Deplete maintenance stock, obstruct work or compromise a critical real service. | May request actual workers or halt an unaffordable repair; responds to credible failure reports. | Multiple estate service states and bounded budgets update without free materials or scripted prosperity. |

	**King**

	| Subtype | Watch without intervening | Engage / help | Disturb / compete | NPC initiates or responds | Persistent ripple |
	|---|---|---|---|---|---|
	| **Administrator King** | Receives summaries from real officials and decides which permitted kingdom services receive priority within recorded resources. | Complete a sanctioned public assignment, provide verified results or support an established institution. | Disrupt a funded service, steal real supplies or undermine a legitimate official’s ability to act. | May receive an eligible petitioner or delegate a concrete matter to an authorized subordinate. | Kingdom-level priority flags and existing official tasks change; local work still needs labor, time and stock. |
	| **Lawgiver King** | Reviews credible law-enforcement outcomes and authored policy questions already supported by kingdom rules. | Offer valid testimony or complete an authored legal petition where access is permitted. | Mislead a lawful proceeding, obstruct real reporting or break existing rules. | May hear a qualifying grievance and uphold, decline or authorize a predesigned rule change. | Only configured jurisdictional rules and legitimate records may change; no invented law editor or instant guilty verdict. |
	| **Diplomat King** | Consults known external faction relationships and conducts eligible authored audience or negotiation responses. | Deliver genuinely learned information or assist an approved diplomatic errand through existing dialogue/travel. | Intercept a legitimate message, betray an entrusted relationship or spread a grounded rumor. | May grant a contextual audience or decline an unknown/untrusted approach. | Faction standing and authored diplomatic flags respond; wars and treaties are not automatically generated. |
	| **Defender King** | Reviews verified kingdom threats and chooses lawful defensive priorities with existing commanders and resources. | Warn of a truly observed threat or assist a commissioned escort/defense action. | Disrupt a guarded route, attack existing defenders or give unreliable reports. | May ask for help or issue a warning through established channels, never instantly deploy an unseen army. | Threat assessments, permitted defense commitments and budgets change under protected-world autonomy. |
	| **Patron King** | Reviews authored service records and permitted petitions for honors, noble status or office appointments. | Earn qualifying service, provide credible support for an eligible applicant or attend a recognized audience. | Damage trust, falsify qualifications or interfere with a valid application. | May acknowledge a proven deed, refuse a claim or grant a specifically authorized title/appointment. | Title and office records update only by valid eligibility; no random succession, automatic promotions or new player kingship. |

	**Systemic links, not one hundred authored quests:** NPC work and reactions use the same real events as player actions. Situations connect when agents share a resource, delivery, person, job, location, or report. For example, a damaged road can delay a Wagon Driver, who fails a Blacksmith's ore delivery, prompting the Blacksmith to seek supply and a Weapons Merchant to change offers. A Highwayman may target that same route; a Rural Patrol Guard can only act when the threat is observed or reported. A nearby player can repair access, intercept the cargo, help defend it, profit from the shortage, or watch the agents adapt. None of this requires a generated quest or item spawn.

	**Cross-family chains for new roles (existing systems only):**
	- **Survival without predation:** An unemployed Hobo grows hungry, requests food from a Food Merchant or the player, and remembers the response. A compassionate player may give food through ordinary item transfer; an unhelpful player may ignore them. If genuine deprivation persists and a vulnerable food item is accessible, a desperate theft *may* become an available action; the NPC still prefers avoidance and escape. A Guard does not automatically know about it without a witness and successful report. A Pickpocket in the same street acts from a different profit-driven opportunity query.
	- **Real employment recovery:** A Job Seeker learns about a supported Workshop Baron commission. If an actual Craftsman vacancy exists and the NPC has the skills/permission, accepting work changes their **single active subtype** to the proper Craftsman specialty. The Baron cannot manufacture employees, resources or a vacant workshop to make the outcome happen. A Displaced Resident instead prioritizes legitimate housing after a real home loss, even if employment is available.
	- **Bottom-up political information:** A Road Worker cannot reopen a blocked passage without authorized work and materials; a Wagon Driver suffers a genuine delay; a Market Count may notice shortages through believable reports; a Logistics Duke may prioritize a real interregional transit issue; an Administrator King may delegate an already supported response. Each level acts **within its actual authority**, and the road is not repaired until people and resources perform the work. The player can repair, deliver, obstruct, testify, appeal, or simply watch.
	- **Noble access is conditional:** Approaching a Count, Duke or King does not automatically open a sovereign command interface or political quest. Available responses are filtered by recognition, proximity, current duties, existing authored opportunities and lawful access. The player can still affect these NPCs indirectly through trade, service, crime, relationships, and the people they employ.

	**Living-world follow-through:**
	- **Idle is still an understandable state.** With no feasible subtype-specific objective, an NPC seeks valid alternatives or performs shared routines; it does not mimic production while secretly producing items.
	- **A job has continuity.** Save the current commitment, desired result, required state, and who owns the relevant resources. NPCs return to tasks after interruptions when still useful.
	- **Recognition is specific.** An NPC can remember the player as someone who helped, interfered, stole, threatened, or merely stood nearby for a suspicious period if actually perceived; idle observation alone is not automatically criminal.
	- **Conflicts arise from compatible goals.** A Farmer wants yields, a Food Merchant wants supply, a Hauler wants delivery, a Thief wants a target, and a Guard wants lawful order. Every conflict is traceable to actual state.
	- **No mandatory player participation.** The same work may succeed, fail, or adapt without player input. Player intervention should matter causally but is not necessary for the world to function.
	- **Agent-to-agent equivalence.** An NPC can help, disrupt, trade with, threaten, witness, or report another NPC through the same underlying rules and role permissions. Avoid NPC-specific shortcuts or magical economy changes.
	- **Narrative discipline.** Authored major NPC histories, dialogue and quests add weight and controlled opportunities; ambient simulation creates encounters, not random kings, irreversible kingdom destruction or made-up principal storylines.

	**Scope and validation:** Start with one end-to-end interdependent chain containing a producer, Hauler, Merchant, Thief, and Guard. Validate normal execution, player aid, theft/interruption, NPC initiative, legitimate witness reporting, time advancement, and save/reload. Use it as a *behavior-quality benchmark* while engineering continues its existing devmap priorities. Expand to the other subtype definitions through existing action modules and content data, not one hundred bespoke AI architectures. The tuning of frequency, dialogue variation, thresholds and animation budgets remains **open**.


	**Roster scope:** Exactly **twenty** parent families, **five concrete subtypes per family**, **one hundred** total. The original twelve work roles remain intact; **Clergy**, **Civic Services** and **Leatherworker** expand occupational coverage. **Unemployed** covers five distinct situations with no active paid occupation; **Baron, Count, Duke, King** cover five distinct approaches to *authorized noble governance* per rank. Every subtype has its own opportunity query and signature decision profile. **Predator Hunter** uses existing wildlife, perception, navigation, combat, settlements, and safety rules; it introduces no animal species, trained companions, taming system, or additional creature content. Predator Hunter prioritizes threats to people and routes; Game Hunter seeks lawful resource yields; Trapper manages placed traps; Monster Hunter targets extraordinary threats; Bounty Hunter pursues wanted people.

	**Subtype contract required for each registered definition:** `subtype_id`, `family_id`, `signature_goal_ids`, `signature_action_ids`, `opportunity_query`, `prerequisites` (tools, skills, assignment/authority, assets, **need thresholds for Unemployed**, **title/office/jurisdiction permissions for nobility**), `priority_inputs`, `success_effects`, `failure_fallbacks`, `world_system_dependencies`. Each registered subtype must have at least one distinctive opportunity query and signature goal/action pair absent from the other four subtypes in its family. Content may share underlying action primitives, such as interacting with inventories, but not duplicate occupational decision profiles under different names.

	**Exclusivity examples:** A Hobo seeks food under need pressure, whereas a Pickpocket pursues portable valuables as income; an Agrarian Baron authorizes help on a specific estate, whereas a Market Count coordinates registered county shortages; a Defender King considers sovereign defense priorities but cannot directly arrest people or create troops. A Fence may evaluate and purchase stolen items because that operation is in their occupational permission set; a Weapons Merchant cannot do so by default. A Prison Guard can transfer prisoners between secure custody states; a Gate Guard can grant/deny checkpoint access, not manage custody. A Caravan Hauler forms a convoy; a Wagon Driver accepts one vehicle-based delivery and does not manage convoy participants. A Blacksmith produces metal weapons/tools; an Armorer handles fitted protective equipment.

	**Coverage boundaries:** The five Craftsman subtypes represent five supported AI specializations, **not** an implicit promise of AI-only fletchers, masons, jewelers or alchemists. Those products may be authored, player-crafted, sold as stocked items, or covered by later explicitly approved subtype revisions; the current 100-subtype contract does not grant another subtype’s specialized work. **Leatherworker** covers tanning and non-armor leather goods (Tanner, Leather Cutter, Leather Stitcher, Cobbler, Leather Goods Repairer); protective leather armor remains **Armorer**, and textile garments remain **Tailor**. Religious functions are covered by the **Clergy** family; medical functions remain existing abilities, authored duties or future candidates for explicit taxonomy review rather than automatically adding new AI professions. **Civic Services** covers banks, inns, pubs, administrative desks and museum display without creating money, law authority or items. The four noble-governance families are limited **NPC office-behavior profiles**, not unconditional hereditary powers, new political simulation engines or a player-accessible Duke/King career. Fog is not a church; a “Veil Warden” would be a later authored duty, not a Clergy subtype.

	**Scope:** One active concrete subtype per human societal NPC. Parent role IDs cannot spawn an NPC. An NPC without employment chooses an eligible Unemployed subtype; job search, displacement, retirement and household support are not interchangeable states. Subtype transitions replace the active subtype through validated employment, residence, retirement, title/office or life-state changes; a named character never acquires a second active family by merely receiving an honor. Personality, ambitions and needs rank permissible options but never bypass occupational authorization.

	**Cross-system behavior requirement:** Occupational decisions must react to legitimate outputs from skills, supplies, ecology, commerce, death and reported crimes, while retaining subtype-specific permissions. The connected examples in §3.13 are required world-state tests, not new occupational permissions.

	### Autonomous selection and simulation contract

	1. **Shared baseline:** Perception, physical needs, safety, navigation, communication, inventory and memory apply to all living human agents.
	2. **Subtype action whitelist:** Build specialized candidates **only** from the NPC's registered subtype and its family's reusable primitives. Shared human responses remain universal. An NPC cannot autonomously perform another subtype's specialized actions by merely having a broad family name. For noble subtypes, the whitelist is further reduced by current office, jurisdiction, resources, and authored event gates; for Hobo, hunger/rest/safety thresholds determine whether survival requests or last-resort theft become candidates.
	3. **World-grounded prerequisites:** Every option is checked against accessible tools, skill requirements, legal/job authority, known destinations, resources, market stock, employer contracts, schedules and physical feasibility. Ineligible actions are omitted or marked blocked, not silently simulated as successful.
	4. **Personal goal evaluation:** Select among feasible occupational tasks, shared needs, personal ambitions and external requests using urgency, expected benefit, **realistic expected margin, input/transport costs, available funds, known local demand**, risk, personality, memory, relationships and obligations. An NPC producer or merchant may redirect effort toward a better opportunity **within the current subtype's permitted actions**; taking a different profession still requires a validated subtype transition. Personal goals can interrupt duties when justified; actors should avoid rapid indecisive goal-switching.
	5. **Execute existing gameplay actions:** Walking, gathering, crafting, trading, combat, theft and reporting use the same authoritative item, XP, ownership, economy, combat and crime rules as player actions. No NPC-only resources, free stock generation or instant authoritative knowledge.
	6. **React and remember:** When an action is interrupted or fails, reassess the actual world state; use subtype-relevant alternatives (e.g., Merchant seeks supply, Hauler reroutes, Thief flees). Social information must have an origin and travel path.
	7. **Persistence and scale:** Save subtype ID, occupation assignment, current goal/task, prerequisites, relationships and important memories. Off-screen agents resolve the **same commitments and state transitions** abstractly, without fabricated deliveries or economic duplication. Important authored characters receive additional story goals but still use a valid subtype; their title and current occupational/governance assignment remain separate records.

	**Example of distinction:** A **Gate Guard** prioritizes controlled access, checks and entry incidents; a **Rural Patrol Guard** prioritizes road patrols and reports beyond town limits. Both can defend themselves, but neither is instantiated as a generic `Guard`. Likewise a **Pickpocket** seeks exposed carried valuables while a **Burglar** seeks access to premises: they share theft and stealth mechanics, but evaluate different opportunities, plans and risks.

	**Scope principle:** **Less bespoke AI code, more consequences from shared systems.** A large population can feel heterogeneous because the same 100 configured behavior profiles interact with physical supplies, individual personality, skill progression, relationships, authored history and persistent world state. No unlimited procedural career generator or unconstrained narrative generation is promised.

	### Social knowledge, dialogue, and mortality

	**Tiered simulation parity:** Significant agents support detailed skills, equipment, inventories, goals, memories, and behavior. Distant/background actors resolve corresponding outcomes abstractly using consistent underlying rules. Background simplification must not silently alter legal, economic, or physical outcomes.

	**Social knowledge:** NPCs remember meaningful experiences, maintain relationships, form opinions, and share information through actual contact paths. Memory distinguishes witnessed facts, rumor, personal experience, reputation, and long-term disposition. Equipment, attire, recognized **public Honor** (§3.5), rank, relationship, and recognized identity can affect conversations. Private Honor changes are never independently visible to NPCs.

	**Dialogue:** Handcrafted major exchanges and significant character arcs; contextual routine replies may respond to occupation, previous actions, relationship, and world state. NPCs converse with each other. No promise that unconstrained generated dialogue writes the game's defining stories.

	**Mortality:** NPCs, including important characters, can die permanently under established world rules. Vacated jobs, property, and services may be reassigned or disappear. The same death can legitimately remove a quest. Whether special exceptions apply to monarchs or other rulers is **open**, not an assumed immortality rule.

	## 3.4 Economy, logistics, production, and property

	### Economic identity and non-negotiable boundaries — established

	RPGGAME has a **single-player, NPC-agent-driven living economy**, not a static NPC shop list or an actual multiplayer exchange. Producers, artisans, merchants, guilds, workers, transporters, factions, and consumers have **their own goals, stocks, funds, skill limits, commitments, and willingness to trade**. They act when the player is absent, compete for scarce opportunities, and react to the same regional conditions as the player. The player is one economic participant, not the economy's exclusive customer, employer, or source of change. Authored disruptions can enter the simulation, but routine shortages and opportunities should arise from existing actions and state.

	**Currency enters** through quests, wages, bounties, heists, and NPC loot purchases. **Currency leaves** through taxes, property costs/upkeep, bribes, transport, ammunition, consumables, and repairs. Shops react to local stock and regional conditions; reputation and haggling may affect prices. Player and NPC actions can interrupt shipments, change supply, and create opportunities.

	**Progression boundary:** **Skill levels are earned only through valid training actions and the existing XP/quest-lamp rules in §2.2.** Coins, purchases, investment, trading volume, merchant favor, and property ownership **never directly purchase, convert into, or grant skill levels or XP**. Buying finished equipment also grants no production XP. Economic success improves access to goods, facilities, labor, and opportunities—not unearned skill advancement.

	**Economy invariants:** Trading transfers existing money and goods; production consumes actual inputs and work time; consumption, spoilage, and supported wear remove or transform actual item stock. NPCs have no free inventory refreshes, fabricated deliveries, or unlimited purchasing budgets. NPC occupational actions still obey the **100-subtype exclusivity contract in §3.3**. Demand may be represented as a bounded regional aggregate, but stock, cargo, funds, order fulfillment, and important actor commitments remain reconciled to a single authoritative state (§3.8).

	### NPC production, consumption, specialization, and competition — established direction; tuning open

	- **Producers** harvest real accessible resources, choose feasible opportunities within their subtype, respond to scarcity, and sell to real buyers. They incur work, access, tool, and transport constraints; they do not instantly change professions because another commodity becomes profitable.
	- **Craftsmen** obtain materials, accept supported commissions, spend production time, manufacture or repair permitted goods, and seek customers. Skills, recipe eligibility, workplace access, and material availability determine output rather than invisible item spawns.
	- **Merchants** assess customer demand, liquidity, storage, existing stock, spoilage where applicable, and expected resale margin. They buy, price, replenish, discount, or refuse goods; some other NPCs may acquire the desired stock before the player arrives.
	- **NPC consumers** have needs and budgets: households obtain food/fuel, qualified Guards/Knights/Hunters seek applicable supplies or equipment, and institutions request existing authorized work. Purchases consume real stock and funds; repeat demand must be supported by needs, plans, orders, or replenishment rules.
	- **Economic competitors** pursue legitimate known opportunities: another merchant may buy an underpriced shipment, another qualified craftsman may win an order, or a hauler may claim a profitable route. Competition follows knowledge, available capital, subtype permissions, travel, and risk—not omniscient price discovery or scripted punishment of the player.

	A worker lacking inputs or buyers must visibly **pause, seek supply, change an eligible task, lower an offer, or abandon an uneconomic commitment** as appropriate. Production, work, consumption, and off-screen resolution use the same accounting and causal rules.

	**Integration rule:** Crafting, animal husbandry, player estates, repair work and outside-city corpse theft all obtain and remove goods through the same economy and provenance model. An animal’s birth is a new living entity, not instant food stock; a stolen corpse item is already existing stock, not a new market drop. See §3.13 for enforceable supply-chain tests.

	**Neutral item valuation:** All presently named goods and equipment resolve to an **initial integer Gold reference price under §2.7**. These are calibration anchors rather than universal fixed merchant quotes. Regional supply, rarity, condition, solvency, trader Attitude and eligible Honor benefits determine actual authorized offers; high-end armor supply and monetary sinks must justify the Dragon/Godly/Malevolent prestige references without inventing goods or cash.

	### Local and regional markets — established direction; numeric curves open

	**Each settlement or market region has distinct availability, recent trade pressure, production/consumption flows, import dependence, and transport conditions.** The same item can command meaningfully different prices in different areas and with different merchants/factions. Price formation uses an item's defined reference value plus local stock and demand, credible purchasing activity, quality/condition, merchant margin, route cost/risk, and applicable relationship factors; exact weights, floors, ceilings, and update cadence are **open**.

	- **Buying pressure:** Sustained or significant purchases deplete actual stock and can raise subsequent local offers; a temporary one-off sale must not force permanent dramatic repricing.
	- **Oversupply:** Flooding a market with the same goods increases saleable stock and tends to lower future offers, with diminishing merchant interest and finite funds; the player cannot repeatedly sell at yesterday's high price to an already saturated buyer.
	- **Supply response:** A credible shortage may prompt eligible NPC producers, merchants, and haulers to increase orders or redirect permitted supply. As goods actually arrive, prices and shortages may ease. Labor, skills, access, production time, safety, and transportation prevent instantaneous normalization.
	- **Regional arbitrage:** Buying where an item is plentiful and transporting it to a scarce market can pay, but capacity, travel time, fees where already applicable, theft, spoilage, and a changing destination price affect realized profit. Other NPCs can pursue the same price difference.
	- **Constrained volatility:** Price changes are bounded and legible. Quote validity, adjustment cadence, minimum useful stock and anti-oscillation parameters should prevent buy/sell loops, merchant-fund exhaustion, and background demand from producing runaway or inexplicable results.

	**Player-visible cause and effect:** Traders discuss known shortages, road trouble and stock changes; visible shelves, busy workstations, posted requests, caravans and credible rumors help players infer why prices differ. No perfect global live-price feed, universal NPC knowledge, or mandatory spreadsheet interface. Players can learn markets through exploration and relationships.

	### Merchant liquidity, barter, and commercial relationships — established

	**Finite merchant funds and stock:** Each shop uses real inventory, a bounded purchasing budget, existing orders, and feasible replenishment. A merchant short on funds can refuse an otherwise legal sale, offer less, seek a buyer/supply, or suggest an item exchange. Shop wealth and inventory do not automatically reset to arbitrary unlimited values after sleeping or fast travel. Legally stolen goods remain subject to §3.5; legitimate traders do not ignore provenance because an offer is profitable.

	**Two-way trading and barter:** Player and NPC may exchange **item bundles directly for other item bundles**, or negotiate a permitted combination of items and currency. Both sides evaluate condition, utility, local demand, legality, existing ownership and willingness to trade. Confirming a deal atomically transfers every accepted item and any payment; inadequate funds, capacity, missing stock, changed permissions or an interrupted transaction cancel the entire deal without duplication or partial theft. Merchant inventory and financial limits apply to barter too.

	**Personal trader Attitude:** Each persistent trader maintains an individual commercial relationship with known customers, **separate from general Honor, faction/house standing, and jurisdictional wanted status**. Remembered fair dealings, repeated fulfilled orders, gifts, meaningful assistance, betrayals, and observed wrongdoing can influence trust, willingness to negotiate, priority service, and access to specialty stock or commissions. Relationships must be learned or witnessed, never globally synchronized. Relevant faction standing adds a distinct affiliation modifier; it cannot erase the merchant's own memory or a legal trading prohibition. **Player Honor purchase discounts and their floors are specified separately in §3.5**; they are not a form of personal Attitude. The rules for decay, thresholds, haggling, gifting returns, and modifier caps are **open**.

	**Commercial trust is not free wealth:** Gifts transfer real items; favorable prices still respect the merchant's budget, margins, real inventory and obligations. Reliable repeat business and greater operating revenue can grow a merchant's future purchasing funds or stock range **through actual retained earnings and procurement**, rather than a relationship score instantly creating money or merchandise. Better reputation may create introductions, reservations, or preferential access to **stock that exists or can be procured**, never conjure a rare item merely because a threshold was reached.

	### Buy orders, commissions, and delivery contracts — established direction; scope open

	Qualified NPCs and supported player-owned businesses can issue **bounded, discoverable requests** to buy goods, craft specific eligible items, repair equipment, or deliver existing cargo. Requests record the actual requester, required quantity/quality, eligibility, destination, agreed consideration, available funding or reserved goods, deadline where applicable, and fulfillment status. Players can accept offers, provide materials, complete an existing order, or place a supported request for an eligible merchant/craftsman; competing qualified NPCs may take still-open jobs.

	Orders are **commitments**, not infinitely repeatable reward faucets: fulfillment consumes the goods/services once, updates payer and supplier state, and adjusts relevant stock/demand once. Cancellation, missed deadlines, sold stock, NPC death, route failure, and save/reload use explicit outcomes. Authored high-value rewards and rare equipment remain constrained by content, item sources, existing tier/skill gates, and actual stock. The system reuses trade, production, transport and employment primitives; it does **not** add an internet-style Auction House, new professional subtypes, or an unlimited procedural quest generator.

	### Logistics, shocks, and economic recovery — established

	**Hybrid logistics:** Regional stock accounting remains authoritative. Important shipments and nearby trade are physically represented when relevant. A shipment cannot be counted as delivered while its corresponding caravan remains vulnerable on the road. Routes have real travel time, availability, and risk. Bandit attacks, road damage, lost shipments, faction access, labor shortages, and resource depletion may affect supply and create opportunities that survive the player's absence. Faction or noble intervention uses only existing lawful authority, workers, stocks and funded commitments; no automatic wars, taxation regimes or kingdom-scale collapse follow from price signals alone.

	**Responsive recovery:** Shortages can attract permitted extraction, production, imports, replacement shipments, or reduced consumption. Oversupply can prompt lower purchasing offers or a different eligible order. Recovery requires actual labor, inventory, transport, and time. Protect important settlements from permanent economic deadlock through **transparent, bounded recovery paths**, not invisible free deliveries that erase player-caused consequences. Economic changes are communicated through shop dialogue, visible transport and local information—not only UI numbers.

	### Property, merchant investment, and financial risk — established direction; returns open

	**Property:** The player can own and furnish houses; **purchasable player estates, fixed-hotspot room construction, utilities, estate-agent service, and servants** follow the authoritative specification in **§3.11**. Supported properties may additionally include storage, workstations, businesses, workshops, farms, and estates with NPC workers and economic output. Estate and territorial ownership are distinct from blanket sovereignty. Property functionality uses ordinary inventory, ownership, NPC, and economic systems. The depth and limits of territorial rights remain open.

	**Investment:** Where a supported agreement exists, the player can commit resources to a merchant or owned/partnered business to expand **real purchasing liquidity, capacity, workforce, infrastructure, or access to better suppliers**. More available cash does not automatically mean better stock: improvements require actual procurement, qualifying workers, property capacity, trade access, and time. Any revenue, profit share, operating cost, loss, or withdrawal reflects recorded sales, production, wages, upkeep and obligations; **no guaranteed passive profit or magically generated stock**. Ownership rights, access, contracts, repayment, failure and NPC control require explicit authorization. Investment is an economic choice, not a shortcut to skill levels, political office, or unrestricted NPC command.

	**Player tradeoffs:** The economy should reward commercial specialization without forcing it: fast resale versus patient relationships, reliable local trade versus risky regional routes, stocking one product versus diversifying goods, immediate purchases versus funding infrastructure, and profiting from disruption versus repairing the supply network. Combat, exploration, quests, production and ordinary trade remain viable alternatives rather than mandatory merchant careers.

	## 3.5 Crime, law, Honor, reputation, and factions

	### Legal systems and distinct reputations

	**Law and crime.** Theft, assault, murder, illicit trade, resisted arrest, and related actions can produce legal consequences. A witness to a crime by the player **or another NPC** must actually perceive it and successfully communicate a report to a **Guard** before a jurisdictional wanted/bounty state is established. Discovery of a missing object is not automatic proof of an offender. Bounties are separate by kingdom; theft bounty is half the stolen item's value, with the previously specified **$1,500** cap interpretation still **open**. Wanted status blocks ordinary lawful shops in that jurisdiction and can cause pursuit. Wilderness enforcement follows the established ranger-jurisdiction rules.

	**Legal resolution.** The player may pay a bounty at a Chancery, surrender to a Guard, serve a sentence, receive available lawful bail, attempt escape, or resist. Stolen goods remain restricted to lawful buyers; Fences may handle them under their established conditions. **An Honor gain from surrender or restitution does not erase a report, cancel a bounty, override a prison sentence, or cause a merchant to accept stolen merchandise.**

	**Separate state domains.** The player has (1) **global Honor**, (2) individual NPC memories and attitudes, (3) personal merchant Attitude (§3.4), (4) house/faction/kingdom standings, and (5) jurisdictional criminal and wanted state. None is an alias for another. Honor can influence an NPC's response **only** if the NPC plausibly knows enough about the player's public character or directly observes relevant conduct. Local reputation travels through the existing rumor, relationship, and communication network; no instant global knowledge. Factions continue their established recruitment, trade, territorial, patrol and rivalry behaviors.

	**Guard challenges:** An officer's own identified eyewitness memory has greater certainty than testimony received through rumors. A challenged suspect may pay, submit, resist or lie; lying commits one bounded evidence-dependent outcome, dismissing the legal charge or beginning custody. Paying, arrest, sentence completion and death end active legal pursuit and old projectiles, while actual witnessed history remains personal knowledge. Jail admissions and who witnessed them persist; they may inform prejudice, respect or fear without reviving a settled warrant. **DevCity** replaces the prototype jurisdiction label JUR-ALPHA; old structured legal keys migrate without renaming people or arbitrary text.

	### Honor — authoritative player-facing rules

	**Purpose.** Honor records the player's moral conduct across the open world, independent of XP, combat level, money, social title and legal standing. It is a **single persistent integer from −100 to +100**, initialized at **0**, and clamped after every validated change. **Zero is Neutral.** The scale has **exactly 17 discrete ranks** (eight negative, one neutral, eight positive). There is no separate “good/evil XP” bar and no automatic Honor drift for idling, traveling or advancing time.

	```text
	HonorBefore = persisted integer in [-100, 100]
	HonorDelta  = configured signed result of one validated moral event
	HonorAfter  = clamp(HonorBefore + HonorDelta, -100, 100)
	HonorRank   = rank containing HonorAfter in the table below
	```

	**Rank register — final boundaries and ordinal IDs.** Each integer value belongs to exactly one interval. Names are UI descriptors, not NPC personality categories or legal classifications.

	| Rank | Honor interval | Display name | Rank | Honor interval | Display name |
	|---:|---:|---|---:|---:|---|
	| 1 | −100 to −89 | Notorious | 17 | +89 to +100 | Revered |
	| 2 | −88 to −77 | Ruthless | 16 | +77 to +88 | Exemplary |
	| 3 | −76 to −66 | Vile | 15 | +66 to +76 | Noble |
	| 4 | −65 to −55 | Cruel | 14 | +55 to +65 | Virtuous |
	| 5 | −54 to −44 | Hostile | 13 | +44 to +54 | Generous |
	| 6 | −43 to −33 | Dishonorable | 12 | +33 to +43 | Honorable |
	| 7 | −32 to −22 | Disreputable | 11 | +22 to +32 | Respectable |
	| 8 | −21 to −11 | Questionable | 10 | +11 to +21 | Considerate |
	| **9** | **−10 to +10** | **Neutral** | | | |

	**Honor HUD and profile representation.** Display a horizontal **−100 ↔ +100** bar with an **animated face marker that physically moves** to the exact Honor position, not merely between rank centers. The face smoothly interpolates from a recognizable sinister/evil expression at negative values, through a neutral face near zero, to a smile at high positive values. Changes briefly reveal signed movement, current rank, and accessible numeric value; threshold crossings receive a distinct but restrained presentation. The interface must be readable without relying only on color. Player-profile portrait framing and Honor-themed imagery use the current rank as a cosmetic presentation input; they do not alter the creator's face, equipment or game statistics.

	### Earning and losing Honor

	The following are **eligible event families**, not new standalone minigames or unlimited interaction loops. Only confirmed, causally meaningful acts emit an Honor event. **Severity and point values are configurable and open**: minor courtesy should be less influential than saving a life or deliberately murdering an innocent. Context can suppress or invert a normally positive/negative event if the act was defensive, coerced, fraudulent, mechanically redundant or had no meaningful effect.

	| Player action that can **increase** Honor | Eligibility and required context | Expected relative weight |
	|---|---|---|
	| Greet or compliment a stranger | A genuine first/meaningful social exchange with a responsive person; not repeated lines to the same NPC | Very small |
	| Complete a chore or help with legitimate work | An existing NPC request or actual job/task advances and concludes; normal reward and XP still work independently | Small–moderate |
	| Give a voluntary donation | Real owned money or goods transfer to a qualifying recipient; value and need determine impact | Small–moderate |
	| Assist a stranger in genuine difficulty | A real danger, need, blocked task or lost property is resolved; not a self-created resettable setup | Moderate–large |
	| Spare an opponent who is genuinely defeated or surrendering | The player has a real opportunity to kill or spare and chooses restraint; not a reusable animation exploit | Moderate–large |
	| Disarm rather than kill in a supported duel | A legitimate duel encounter, successful disarm and preserved life; does not require building a duel mode before one exists | Moderate |
	| Pet an approachable animal | Supported player–animal interaction with safe/appropriate temperament and real cooldown; no compulsive farming | Very small |
	| Release a genuinely caught live fish | Voluntarily return the catch to eligible habitat instead of retaining it; must close the catch transaction once | Small |
	| Surrender peacefully to a Guard | Settle an actual, active confrontation with legitimate surrender; credit at most once per incident | Small–moderate |
	| Pay an outstanding bounty or legitimate debt | Existing liability is actually satisfied with owned money; no repeated credit for the same payment | Small–moderate |

	| Player action that can **decrease** Honor | Eligibility and required context | Expected relative weight |
	|---|---|---|
	| Rob a lawful store or innocent traveler | A real theft, threat or coerced transfer; victim vulnerability and loss inform severity | Moderate–large |
	| Assault or murder an innocent | Confirm victim was not a valid imminent threat; intentional killing gives one of the largest penalties | Large–severe |
	| Loot an innocent person's corpse | Knowingly loot protected/innocent remains; this is distinct from legitimate enemy loot | Moderate |
	| Commit serious exploitative crimes | Only meaningful supported acts with harmed victims, property or trust; avoid stacking duplicate classifications | Contextual |
	| Trample a person while riding a horse | Actual controllable horse–person collision and player responsibility, **only if riding is implemented**; accidents differentiated from intent | Moderate–severe |
	| Deliberately leave a wounded animal suffering | Only when the player caused or knowingly worsened the injury and had a clear feasible intervention opportunity; no penalty for every injured animal encountered | Moderate |
	| Kill a domesticated animal without justification | Established domestic ownership/harmlessness; excludes documented self-defense and legitimate husbandry circumstances | Moderate–large |
	| Intentionally harm helpless people or animals | Real victims and proven player agency; authored/physical context determines severity | Large–severe |

	**Event arbitration:** Each physical event is attributed to a responsible actor, victim, action context and unique event ID. Apply **one canonical moral consequence** for a single outcome: the same innocent murder does not also independently stack “assault,” “crime,” and “looting” penalties unless a *separate later act* occurs. Repeatable positive minor actions use unique-recipient/event eligibility, bounded recurrence, and diminishing or absent repeat credit; cannot be farmed by alternating greetings, paying fabricated debts, harming and rescuing the same victim, or repeatedly surrendering. Low Honor does not automatically make crime profitable or skill-training more efficient. Morality does **not** require a witness to change the hidden player score, but observer knowledge is still required for social or criminal reactions.

	### Threshold rewards, commercial effects, and meaningful trade-offs

	**Purchase discounts:** The four positive thresholds are **+33, +55, +77, and +89**. Eligible legitimate merchants offer the following **maximum Honor-specific reductions** to qualifying **player purchase prices**, using the highest crossed threshold only. These do **not** add together.

	| Minimum Honor | Rank band at unlock | Honor purchase discount |
	|---:|---|---:|
	| +33 | Honorable (12) | 5% |
	| +55 | Virtuous (14) | 10% |
	| +77 | Exemplary (16) | 15% |
	| +89 | Revered (17) | 20% |

	Discount eligibility is restricted by actual legal access, merchant participation, local reputation and available stock. A merchant's real funds, cost basis and **minimum acceptable sale price** remain authoritative: apply the discount to the eligible quote **without creating a guaranteed loss or an item-for-sale that does not exist**. Honor discounts do not apply to player **sale** prices and must not create profitable purchase/resale arbitrage. Exact merchant exemptions or faction restrictions are **open**. A player whose Honor drops below a threshold loses that tier of discount immediately on subsequent valid quotes, without retroactively altering completed sales.

	**Positive-Honor access:** Authored quests, services and qualified merchants may gate **exclusive gear rewards/availability** at positive Honor milestones (default design gates **+55 / +77 / +89**, specific rewards **open**). Such gear uses the **existing equipment slots, family/tier rules, inventory stock and normal skill gates**. Honor alone does not create items or equip gear above the player's skill requirement.

	**Negative Honor:** Provides **no Honor shop discounts and no negative-Honor-exclusive outfits or equipment unlocks**. Its distinct reward bias is in eligible corpse loot and more confrontational ambient responses; it remains fully playable. Low Honor can open authored intimidating dialogue alternatives or darker ways through an existing situation, but cannot grant universal immunity, forced violence, or new gameplay roles.

	**Civilian response:** Positive Honor can soften the first response from people who recognize the player's reputation: friendlier greetings, greater willingness to hear a peaceful explanation, and greater tolerance for **minor** social missteps. Negative Honor can produce wariness, refusal, terse remarks, or quicker escalation when a person credibly recognizes the player. These are *modifiers*, not replacement AI scripts or telepathic awareness. Direct experience, personality, fear, individual Attitude, faction loyalty and local law take precedence. Major crimes remain reportable even against a revered player.

	**Guards and peaceful resolution:** Positive Honor increases the availability or effectiveness of **already supported** de-escalation, cooperation, surrender and restitution options *when the Guard has a valid basis to consider them*. It never cancels real evidence, fabricated innocence, wanted status, or penalties; hostile attacks can still demand active defense. Negative Honor biases remembered encounters toward suspicion and confrontational choices when the Guard has credible knowledge, but is not a passive arrest trigger.

	### Honor-conditioned corpse loot — single-source persistence

	This mechanic affects **eligible secondary corpse loot**, not already-owned inventory or authored quest drops. At an eligible opponent's death, resolve a **single, persistent supplemental loot allocation** from a bounded, preconfigured table using the player's **Honor snapshot at death**, corpse/encounter class and loot budget. The allocation is committed once, stored with the corpse, and cannot change when the player returns later, changes Honor, saves/loads, or transfers an item. The corpse still retains its actual possessions, ownership history, quest items and standard drops; Honor neither transmutes a witness's known sword into a potion nor duplicates money.

	| Honor side | Eligible supplemental-loot bias | Boundaries |
	|---|---|---|
	| **High Honor** | Higher relative likelihood of **potions and jewelry** | Only permitted loot classes and existing item definitions; no reward for looting innocent victims and no guaranteed rare drops |
	| **Neutral** | Standard encounter/corpse distribution | No morality-driven modifier |
	| **Low Honor** | Higher relative likelihood of **Arrows, supported thrown weapons, alcohol, tobacco and money**; **eligible Guard corpses** can bias toward additional money or jewelry | Guard-killing remains criminal and dishonorable; extra value is bounded by the authored loot budget, not a free currency generator |

	**Scope:** The table expresses distribution preferences, not guaranteed goods, species drops, or new weapon families. Alcohol and tobacco are permitted content tags for this feature; **their exact inventory definitions and consumption effects are open**. No Honor-related loot is created if a corpse has no permitted supplemental loot allocation. Acquiring any corpse contents still follows theft, innocence, ownership, witness and Honor rules. Drop rates, value caps, allocation eligibility, and the meaning of “high/low” for these distributions remain **open for balancing**; the polarity and categories above are established.

	### Authored story, profile imagery, and death memorial

	- **Ambient presentation:** Use small, grounded variations in civilian greeting, street conversation, body language and public recognition. Do not generate a parallel authored plot for every rank, trigger forced chatter, or make NPCs aware of private events.
	- **Quest context:** Authored missions may alter lines, responses, available peaceful or threatening approaches, and minor staging or reward eligibility according to an **explicit Honor check** combined with actual facts and individual relationships. Honor cannot resurrect a permanently dead quest NPC, bypass a lost prerequisite, or manufacture quest outcomes.
	- **Final-mission branching:** The eventual authored finale must **read the current Honor rank/value at defined story decision points** and support Honor-conditioned **choices and/or ending presentation**. The specific finale, choices and thresholds are **open until the story is written**. No random final mission is generated by the simulation.
	- **Profile imagery:** Rank drives compatible good/neutral/evil portrait framing or profile emblems; the actual authored artwork is not selected yet.
	- **Player gravestone:** At a player death or a story-defined final memorial presentation, **snapshot Honor at the death event**. Use the snapshot to select the gravestone's visual treatment and **authored inscription variant** (negative, neutral, positive, with permitted rank-specific refinement). This is a presentation record and **does not change the already-established recoverable ordinary death rules** or create compulsory permanent graves for every respawn. Persist the chosen memorial description so later Honor changes do not rewrite that death's inscription.

	**Shared-event constraint:** Honor receives results from valid care, social, combat, crime and restitution actions but may never substitute for observation, law reports or economic item transactions. §3.13 requires an NPC to learn of a reputation or act through a plausible channel.

	### Cross-system implementation contract

	1. **Single ownership:** A `PlayerHonor` state component stores score/rank; a validated `HonorEvent` contains `eventId`, action kind, responsible actor, subject/victim, cause, context, signed adjustment and commit time. All updates occur in the authoritative world-state transaction model (§3.8).
	2. **Event sourcing and causality:** Combat, interaction, trade, social, animal, law and quest outcomes *may* emit Honor events after they successfully commit. UI, particles, dialogue playback, noncommittal attempts and duplicated callbacks **never** award Honor.
	3. **Information separation:** NPC observations/memories receive only witnessed or credibly communicated data; they don't read the player's hidden Honor change as proof of a specific unreported act. Recognized public reputation can shape attitude after a plausible knowledge pathway.
	4. **Priority order for shop access:** Jurisdictional legality and availability → trader stock, budget and base quote → personal/faction eligibility → highest applicable Honor discount → merchant cost floor → atomic currency/item transfer.
	5. **Corpse authority:** Eligible defeated-NPC supplemental loot resolves once with an Honor snapshot; no reroll on interaction, reload or repeat retrieval. **Player death is different:** §3.12 transfers original carried items to one authoritative persistent corpse, with no generated or Honor-biased bonus loot. The player's death-time Honor snapshot remains available for gravestone presentation.
	6. **Performance and scope:** Honor scoring is **event-driven**, not an every-frame NPC utility calculation. Rank-band/known-reputation tags are sufficient inputs for ordinary dialogue and decision logic. Reserve detailed authored reactions for meaningful characters and key encounters.

	**Acceptance criteria:** (a) all 201 legal integer scores map to exactly one of 17 ranks; (b) score clamps at ±100; (c) a recognized public act and identical unwitnessed act change Honor equally but cause appropriately different NPC legal/social knowledge; (d) repeated greeting/petting/settlement events do not produce unlimited gains; (e) purchase discounts obey thresholds and merchant affordability; (f) Honor loot resolves exactly once and is stable across save/load; (g) lawful surrender retains legally valid bounties; (h) a death's memorial image/inscription remains tied to its Honor snapshot; (i) final narrative choice checks operate only on authored content; and (j) unrelated progression, equipment requirements and NPC occupations remain unchanged.

	## 3.6 Optional nobility and heraldry

	Nobility is an **optional, world-integrated activity**, not an eighteenth-skill alternative, main progression pillar, mandatory faction path, or strategy-game mode. Noble houses, knights, estates, offices, and symbols operate in the world whether or not the player participates.

	**Player paths:** Authored opportunities informed by systemic eligibility, reputation, alliances, and events. **Squire → Knight** and **Count** are supported ambitions, but a player need not follow a universal linear ladder. Playable Duke/King/Queen progression is **not currently in scope**; such figures can exist as authored NPCs.

	**Heraldry:** Modular designs combining symbols, patterns, shields, and colors. It communicates recognized social or institutional identity on suitable clothing, shields, banners, documents, and property. A copied emblem alone does not grant lawful authority.

	**Governance AI:** Baron, Count, Duke and King are now concrete NPC role families with five subtypes each, reflecting different stewardship priorities at increasing institutional scope. These subtypes select **what an office-holder attends to**, not permissions they automatically possess. A Count who operates a shop may instead have an active Merchant subtype; title and office records persist separately. A King's strategic preferences affect choices among **already permitted** administrative, judicial, diplomatic, defensive and patronage actions. No new armies, procedurally authored major events, automatic succession, or guaranteed kingdom destruction are introduced.

	**Separate states:** Noble title, current office/permissions, land ownership, and allegiance are related but distinct. Homes, businesses, and estates can exist without nobility. Titles may be durable honors, yet serious misconduct or authored political events can revoke status; full succession and inheritance rules remain open. Office-holders can delegate relevant tasks to NPCs, but unrestricted kingdom management is outside initial scope.

	**Scope boundary:** Players can disrupt businesses, relationships, local factions, and opportunities, but are not promised the ability to depopulate or erase a kingdom. Important ruler mortality/continuity is still unresolved, with earlier consequential NPC mortality preserved until specifically revised.

	## 3.7 World simulation and protected autonomy

	Seven connected domains use the same world state:

	1. **Agent decisions:** Jobs, schedules, needs, skills, goals, individual memory.
	2. **Social information:** Relationships, rumors, recognition, reputation, allegiance.
	3. **Economic logistics:** Production, ownership, consumption, inventories, prices, shipments.
	4. **Crime and law:** Witnesses, evidence, reports, jurisdictions, wanted state.
	5. **Ecology and resources:** Habitat-based animal populations, age and breeding, predation, livestock husbandry, harvest sites, replenishment, local resource conditions.
	6. **Factions and territory:** Goals, material resources, influence, patrols, controlled sites.
	7. **Systemic opportunities:** Playable situations caused by world conditions and resolved through supported mechanics.

	**Protected world autonomy:** Ordinary activities occur independently. Consequential but recoverable developments can unfold without the player. World-defining irreversible changes require valid conditions and discoverable escalation or opportunities to intervene, but are not guaranteed to end favorably.

	**Consequences:** Permanent NPC deaths; controlled construction, damage, repair, and reuse of supported structures; territory held until changed by another valid event; authored quests fully vulnerable to world events; discovered information can appear in the journal; recovery requires plausible labor, goods, money, security, and time, supported by anti-deadlock safeguards.

	**Replayability boundary:** Emergent *situations* arise from economic, social, environmental, legal, and faction conditions. They do not replace authored major characters or create arbitrary world wars on a pacing whim.

	## 3.8 World State & Entity Model — prototype architecture

	**Design contract:** A **single authoritative world state** owns entities, inventories, identity, ownership, quests, knowledge, and material outcomes. Player, AI, quests, commerce, and simulation submit actions through the same rule/transaction boundary. The world may run fully locally; multiplayer networking is not required. **Engineering choices** (ECS versus object model, storage engine, tick budget) remain implementation decisions so long as these behavioral invariants hold.

	**Honor integration:** The player entity owns a clamped `PlayerHonor` value and derived rank; once-committed `HonorEvent` records moral causes, and any Honor-influenced supplemental corpse allocation is stored with an **eligible NPC/opponent corpse**, not the player's death-recovery inventory. NPCs retain distinct personal knowledge and jurisdictional legal state. See §3.5 for event contracts and invariants; player death transfers real possessions under §3.12 and never generates new Honor-biased loot.

	**Cross-system integration authority:** The event paths and multi-feature test cases in §3.13 are normative interface contracts for the components below; no subsystem may implement a second shadow inventory, NPC knowledge ledger, population count, skill awarder or corpse claim to make its local feature work.

	### Identity, location, and components

	Every persistent entity has an immutable, save-stable `EntityId`; content templates use a separate `DefinitionId`; authored major characters additionally have stable authored references. Runtime indexes may be transient, but cross-entity references and save files must use durable IDs. Entities have one authoritative location: scene/world transform, containing entity/inventory, or abstract regional location; never mutually exclusive locations at once.

	| Component / record | Essential fields for the first vertical slice |
	|---|---|
	| `Identity` | `entityId`, `definitionId`, optional `authoredId`, display key |
	| `TransformOrLocation` | Zone/region, position/orientation **or** container/abstract location |
	| `Inventory` | Ordered slots/contents, stack amount, capacity; reference to unique items |
	| `ItemInstanceState` | Stable `itemInstanceId`, immutable `definitionId` under §2.7, 3D voxel asset reference, quantity/stack semantics, condition, doses/charges, optional variant, reference-value rule, original provenance and the existing one-holder ownership/location invariant; visuals never own a second authoritative item copy |
	| `Ownership` | Legal owner (`EntityId`/faction/property), current possessor, stolen/provenance flags |
	| `InteractionProvider` | Supported action IDs, anchors, requirements, live state |
	| `Actor` | Skills, HP/stamina, **equipment-slot assignments and validated primary/Left Hand weapon-or-shield compatibility**, required concrete NPC subtype ID (for all human societal agents, including unemployed and noble office-holders), current goal, action availability, faction, NPC/player state |
	| `AnimalAgent` | `speciesId`, `lifeStage` (`baby`/`adult`), sex, biological age, health, energy/need bands, fear, individual temperament, current goal, group/home-range ID, **legal owner/unowned and domestic/feral context**, familiar handlers, trust/bond, permitted home/roaming assignment, reproductive state and current physical or abstract simulation state. Animals use this component, **not** a human NPC job subtype. |
	| `AnimalGroup` | Stable herd/flock/pack/group ID, eligible species, parent/offspring links if active, member count/IDs, home range, current destination, alarm state, social coordination; dogs never acquire wolf pack-hunting eligibility merely by sharing a group schema. |
	| `HabitatPopulation` | Habitat-zone ID, species and stage counts, carrying capacity, food/water/shelter/access, predator pressure, disturbance, supported neighbor migration links, population change history. |
	| `AnimalReproduction` | Eligible adult pairing or wild breeding-cohort state, sex and maturity checks, pregnancy or clutch/incubation record, due game-time, offspring count pending, parent ownership, cooldown; **birth/hatch is an idempotent event**. |
	| `AnimalRemains` | Original animal/cause, location, remaining accessible resource quantity, condition/decay timer, ownership where relevant, consumed/harvested state; a carcass cannot also remain living in population counts. |
	| `PlayerDeathRecord` | Unique death event, cause/killer, **exactly one INSIDE_CITY/OUTSIDE_CITY boundary result**, separate optional PvH aggression flag, Honor snapshot, reachable graveyard, death/respawn times and status; see §3.12. |
	| `PlayerCorpseClaim` | One original corpse/container, **city Guard-protected flag effective at death**, owner, actual exposed/custody holder, unique original item references, real transfer history, last known location, authorized Guard/Undertaker assignments, fee/payment and claim progress; the item holder is authoritative and city contents cannot be looted. |
	| `DeathMountState` | Stable surviving horse/owned-mount animal ID, flee/stay decision, last known position, current actual location and retrieval status; never an inventory-item copy. |
	| `PerceptionAndMemory` | Sensed events, LOS/audio/illumination context, suspected versus known identity, last known position, confidence/time, evidence, knowledge links; §3.10 governs which facts persist |
	| `MagicResource` | Magic level, current/max Mana, regeneration accumulator, unlocked spell IDs/group maxima, selected spellbook ID (if used), active cast and commit state |
	| `FaithResource` | Faith level, current/max points, equipped Faith bonus, active prayer IDs and paid interval/next drain timestamps; altar/consumable restoration history |
	| `SpellAndPrayerEffect` | Spell/prayer definition ID, caster/targets, eligible elemental/attack-type tags, resolved ongoing effects, unique cost/event IDs; Fire staff charges remain authoritative item state |
	| `DoorContainerWorkstationResource` | Lock/open state; stored contents; recipes; charges/stock/regeneration as appropriate |
	| `FactionJurisdiction` | Membership, territory jurisdiction, standing, crime reports, active wanted state |
	| `QuestState` | Authored quest ID, stages, facts learned, conditions met/failed, permanently closed flags |
	| `EconomyShipment` | Source/destination, owner, cargo, status (`planned`, `inTransit`, `delivered`, `lost`), market stock references |
	| `RegionalMarket` | Market/region ID, eligible item/category references, real stock and reserved quantities, bounded demand/price signals, local event causes, last update |
	| `MerchantAccount` | Trader ID, owned inventory, available purchasing funds, operating commitments, individual customer Attitude/known history links, procurement constraints |
	| `EconomicOrder` | Issuer and eligible supplier, order type, item/service requirements, quantity/quality, reserved input or funding, deadline, fulfillment/cancel state, destination |
	| `BusinessAgreement` | Property/merchant parties, authorized investment or ownership terms, committed resources, operating costs, obligations, recorded returns and losses |
	| `PropertyTitleOffice` | Property owner, operating permissions, optional noble title/office, title-to-office jurisdiction, allegiance; subtype never grants authority by itself |
	| `EstatePlotLayout` | Persistent purchased estate ID, kingdom and plot listing, entrance, owner, permitted footprint, room-cell/door graph, room instances/orientation, fixed furniture hotspot occupancy and validated demolition dependencies |
	| `EstateFacilityStorage` | Installed utility ID/type/condition, true stored-item holders, mounted amulet identity, registered teleport destinations and valid network access, altar/pool/crafting capabilities, trophy/collectible-display references and style-kit definition |
	| `EstateServantAssignment` | Existing concrete-subtype NPC ID, employing estate, bedroom availability, room access, authorized tasks, real location, contract/wage status and paused/ended causes |
	| `EstatePetRegistry` | Estate/Menagerie ID, up to 30 **unique real owned dog/cat IDs**, roster slot, home/rest/food/habitat anchors, actual pet location, feed inventory and feeding commitments, individual bond and visit permissions. |
	| `EstateArenaMatch` | Approved ring/arena type, actual participant entity IDs, active session/consent/eligibility, nonlethal rule set, reservations, score/result, exit/return anchors and last committed action/event. |
	| `EstateCollectionDisplay` | Fixture/room ID, authentic item ID(s) or display-only reference, collection category and set completeness, visitor-view permission, current gallery index and atomic swap status. |
	| `AquariumCollection` | Aquarium/tank instance, eligible original fish-egg item IDs, discovered supported fish definitions, owned/perk eligibility flags, display/care status and collection progress. |

	**Data organization:** Immutable item, skill, NPC role/subtype catalogs, NPC archetype, recipe, dialogue, faction, and authored-quest definitions live in content data; mutable instances live in save/world state. One object can hold multiple components. Do not duplicate a quantity in market stock and physical cargo without a reservation/transfer rule.

	### Actions, events, transactions

	```text
	Player/AI/Quest/System requests Command
	    -> check identity + target + conditions against latest WorldState
	    -> compute Result and ordered StateMutations
	    -> atomically commit StateMutations and increment WorldRevision
	    -> append durable DomainEvent(s) (with EventId, Tick, ActorId,
	       SubjectId, Location/Jurisdiction, CauseId, Payload)
	    -> subscribers update knowledge, journal, notifications, AI plans,
	       pricing views, and presentation (without reapplying mutations)
	```

	**Invariants:**
	1. **One item, one holder:** An item stack's contents exist in exactly one place. Transfers are atomic; items cannot exist in both chest and backpack.
	2. **Single economic truth:** Delivery/loss changes a shipment status **once**, and updates corresponding cargo/stock once. Trading and barter debit/credit both parties atomically; orders reserve and consume each input/payment only once. Merchant funds, business accounts, regional aggregates and physical inventory cannot each count the same money or item as independently spendable. Event replay or save/load cannot double-credit a shop.
	3. **Causal legal knowledge:** A theft creates a theft event; a witness who actually sees it gains evidence; law status changes only after a valid report reaches a Guard in the applicable jurisdiction. Rumor spread follows actor encounters, not global broadcast.
	4. **Quest facts, not assumed success:** Authored triggers consume world facts and events and may mark quests permanently unavailable; save/load preserves both failure reason and learned journal knowledge.
	5. **Deterministic commit:** Given the same snapshot and resolved random input for permitted skill checks, command execution produces one consistent state result. Store generated random outcome/seed with committed events if replay requires it. No random hit/miss or random damage rolls in physical combat.
	6. **Idempotent processing:** Each request and durable event has a unique ID; re-processing cannot duplicate reward, item consumption, title grant, wanted notice, delivery, barter exchange, contract payment, or business return.
	7. **Persistence:** Commit outcomes, entity IDs, timers, ownership, facts, NPC memory, and shipment status survive save/reload. Reference validation must detect missing/dead targets without crashing.
	8. **Death inventory conservation:** A genuine player death transfers each equipped/carried item exactly once to a persisted corpse; player graveyard respawn grants no items. **City classification immediately guards every original item against all non-Guard corpse access and guarantees complete eventual Undertaker recovery; outside-city exposure allows physically credible human looting.** PvH is a separate AI hunting flag, not a third death territory. Guard custody, exposed looting, Undertaker collection and player retrieval are exclusive original-item transfers. A looted outdoor item cannot reappear in the service claim. Nonlethal estate matches cannot call the death pipeline.

	**Implementation allowance:** Event log plus snapshots, transactional state mutations plus audit events, or equivalent architecture is acceptable; **full event sourcing is not mandatory**. Events are facts about committed changes, never substitute commands that apply the same change twice. A deterministic fixed-step simulation tick is recommended as a *prototype default*; specific cadence and performance budgets remain open.

	### Time, simulation tiers, and loaded/unloaded agents

	- Maintain one authoritative **game-time clock** plus a simulation tick/revision. Waiting, sleeping, and travel skip time through a catch-up coordinator; no offline simulation requirement. The engine Time System reads that same clock for daylight, directional/ambient light, sight conditions and weather. Registered regions can blend day/night RGB filters and fog across borders; the devmap registers one neutral profile. Weather transitions, accumulated wetness and phase persist. Clouds use a bounded low-resolution volumetric pass; local fog volumes share the existing renderer.
	- **Near agents:** Detailed movement, actual object interactions, inventories, local perception, authored dialogue. **Far agents:** Job/schedule, travel, resource and event outcomes may resolve abstractly. On entry to loaded range, instantiate consistent physical entities from the same durable state.
	- Background outcomes are prohibited from bypassing critical causality: an abstract witness cannot instantaneously submit a report without plausible travel/contact; an intercepted caravan cannot deliver as if never intercepted.
	- Important NPC deaths and lost opportunities are persistent. Economy and faction progression must obey previously established protected-autonomy limits; do not create unrestricted player-driven kingdom annihilation.

	**Chemistry and visual budget:** Native property emitters produce finite fire, water, ice, wind and electricity volumes; tagged material sensors transition among wet, burning, frozen, charged and burnt states. Material effects propagate through elements, never direct material-to-material calls. Spatial buckets, a bounded active sensor queue and dormant/burnt sleeping states limit work. Original attribution reaches real combat/law outcomes. Charred cells and frozen water use a persistent edit journal, restoring the correct world state and remaining thaw time after load. Preserve all 64 bits of durable IDs in save numbers. GPU billboard particles are capped, with no particle readback; fire/smoke and spell trails share this path. Water uses a fixed-step two-dimensional heightfield, actual shoreline/roof masks, dual scrolling detail, refraction and existing Fresnel/SSR. Dynamic creature shadows reuse the shadow budget; light emission belongs only to luminous gear/effects. Target **1080p / 30 FPS on i5-3570, GTX 660, 8 GB RAM**; software-rendered cloud checks validate functionality and cannot certify this hardware target.

	### Engineering example — one theft across systems

	1. Player focuses on a blacksmith's owned ore in a chest. Provider offers `Take (Steal)`; ownership belongs to the blacksmith.
	2. On input, the command validates container access and space, moves ore chest → player, records original owner/provenance, commits a `ItemTaken`/`TheftCommitted` event.
	3. A nearby NPC receives `WitnessMemory` only after §3.10 verifies actual sight of the offense and appropriate identification or uncertainty; hearing a noise alone is not eyewitness proof. The player is **not** wanted yet.
	4. Witness travels to a Guard and uses `ReportCrime`. A valid report creates a jurisdiction-specific wanted/bounty record, using the established theft rule of half the stolen item's value (ceiling interpretation open).
	5. The blacksmith loses usable stock, potentially affecting crafting offers; a related authored quest may react through explicit conditions. Re-opening the container shows ore gone.
	6. Save/reload after any step retains ore's unique holder, memory/report status, market quantity, and quest facts. Killing/intercepting the witness before the report can prevent this report, without undoing the theft.

	### Engineering example — quest NPC removed

	An authored NPC dies in combat; the entity becomes `Dead`, authored role/quest references remain resolvable as dead, dialogue is no longer offered, affected quest conditions recompute, and an explicitly authored quest becomes `Failed/Unavailable`. Other services may be reassigned only if there is a valid job succession rule. The journal retains what the player actually knows; technical dangling references are not allowed.

	### Instrumentation and save validation

	**Honor debug:** Expose the player’s numerical Honor, ordinal rank, current face-marker position, recent source event IDs and score deltas, cooldown/unique-recipient eligibility, discount quotes and merchant floors, effective NPC public-knowledge flags, and corpse-loot Honor snapshot/allocation. Provide devmap fixtures for positive, neutral, negative, witnessed/unwitnessed and death-memorial cases.

	Provide a **developer inspector** exposing: entity ID/definition, location/owner, action options with disabled reasons, current skills, quest facts, reported crimes, NPC memories, **trader Attitude, funds/stock, price factors and causation, open orders/reservations, business inputs/revenue/costs**, market cargo and shipment status, world tick/revision, and ordered event trace with causes. Add one-click scenario reset and save/reload tests. Save metadata includes schema version; migration strategy remains open beyond the prototype. Keep a test harness that replays commands and asserts all eight invariants above.


	---


	## 3.9 Animal ecosystem, behavior, breeding, and livestock — established direction; balancing open

	### Design outcome and species boundary

	The wilderness and farms must **feel inhabited independently of the player**. Wildlife follows recognizable daily patterns, forms species-appropriate groups, reacts to threats, hunts or avoids other supported species, gives birth or hatches young, and leaves lasting population consequences. The player can watch, approach, protect, feed where permitted, hunt, steal owned livestock, disturb habitats, or assist farmers; animals and human roles respond using the **same authoritative world state**. No need for a new quest or a staged encounter to produce an interesting situation.

	**The supported land/farm/companion animal catalog comprises fourteen species.** `Cow / Calf` is **one cattle species**, with Calf the visible baby state, not another species; the formerly supplied "Bown Bear" denotes **Brown Bear**. Every species has exactly two concrete life-stage behavior profiles (`Baby` and `Adult`): **14 species × 2 = 28 profiles**, sharing one animal-AI architecture rather than 28 AI engines. Sex, age, reproductive condition, legal owner, domestic/feral status, temperament, trust, bonded humans, parent/offspring ties, pack/group affiliation, injury and need state are **instance attributes**, not extra AI role families or stages. `Adult` includes both sexes. Stage transitions do not create extra animals.

	**Required species:** Chicken, Pig, Horse, Rabbit, Fox, Coyote, Wolf, Mountain Lion, Brown Bear, Duck, Cattle (Cow/Calf), Sheep, **Dog (Dog/Puppy)** and **Cat (Cat/Kitten)**. Dogs and cats are full-fledged animal agents, including owned companion pets; **wild/feral dogs and unowned/free-roaming cats use the same respective species definitions** with different situational goals and relationships. Do not create a separate "Pet" species or copy Wolf behavior into Dog. **Menagerie pets are currently restricted to owned dogs and cats**; eligibility for other existing species remains Open. No further terrestrial species, fantasy creature, veterinary NPC family, or riding system is authorized here. The Fishing/fish resource model and new estate collectible **fish eggs** (§3.11) are separate from the fourteen land-animal profiles; the fish eggs do not imply a fifteenth animal species or inventory poultry eggs.

	**Simulation philosophy:** Do not simulate thousands of identical full-detail brains. Author **habitats and opportunities**; let a shared set of biological needs and species-specific priorities drive behavior; preserve important identity and consequences; approximate distant populations through aggregate accounting. Animals cannot appear in front of an observant player merely to hit a desired spawn density.

	### Shared intelligence with species-defined priorities

	All animal agents use a common decision vocabulary: `forage/feed`, `seekWater`, `rest/shelter`, `travelWithinHomeRange`, `followGroup/parent`, `assessThreat`, `warn/flee`, `defend/attack if appropriate`, `hunt/scavenge if appropriate`, `seekMate`, `careForYoung`, and `reactToHuman`. Eligibility and scoring are gated by **species + life stage**, viable local habitat, actual need, perception, body capabilities, group information, and territory. Flying/short flutter, swimming, swimming-to-land, and quadruped navigation are allowed **only when consistent with that species and available authored locomotion**, not inherited generically.

	**Priority bands:** (1) immediate physical danger / parental protection; (2) urgent survival needs; (3) offspring care and following; (4) feeding, resting, and routine travel; (5) courtship/reproductive opportunities, social behavior and exploration. Assign species-specific thresholds and **commitment/hysteresis** to prevent animals switching goals every evaluation. Perception uses proximity, visibility, sound, and applicable scent/alert cues as **existing perception channels**, with tuning per species. Threat reaction progresses through `unaware → alert → cautious retreat/defensive display → flee or attack when justified → recover and resume`. A predator must not auto-attack every visible player; a prey animal must not flee forever after hearing one sound.

	**Group behavior:** Rabbits and many predators can be territorial/individual or in small family groups; wolves prefer packs, sheep and cattle move in herds, chickens and ducks in flocks, and pigs/horses in groups where practical. One *group intent* can choose destinations and threat responses; members retain near-field spacing, follow/lag, baby protection and collision individuality. Stagger activity starts and independent head turns, browsing, grazing, play, resting and alarm cues so the group does not move like synchronized clones. Group size, territories, daily activity windows and seasonal rules are species/habitat data, **not hardcoded global traits**.

	### The 28 animal behavior profiles — species-specific work and player ecology

	| Species | Adult signature goals / interactions | Baby signature goals / interactions | Distinct player and human-NPC consequences |
	|---|---|---|---|
	| **Chicken** | Forage close to a roost, group with flock, avoid fox/coyote threats, adult hens conditionally lay incubable clutches. | Chick stays close to flock/mother, shelters, feeds under supervision; exceptionally vulnerable to separation. | Livestock Farmer manages contained flock, breeding and losses; player can watch foraging, feed under permission, protect from predation or unlawfully take owned birds. |
	| **Pig** | Feed opportunistically, root in suitable ground, seek shelter and social company, breed when husbandry permits. | Piglet follows sow/group, competes less effectively for food and flees with adults. | Farmer adjusts feed/space; pigs can disturb existing farm growing areas when containment fails, prompting believable owner reaction. |
	| **Horse** | Graze, travel with herd/group, startle and reposition at danger, form proximity bonds with familiar handlers (not a new taming system). | Foal follows mare, tires easily, stays near herd and requires protection. | Farmer can maintain owned horses and breed adults; disturbed horses may flee or disrupt an existing Hauler's available animal assignment; **riding remains separately open**. |
	| **Rabbit** | Graze/browse, use dense-cover habitat, keep watch and escape quickly; breeding favors safe cover and food. | Kit remains close to sheltered home area and avoids open ground until older. | Fox, coyote and mountain-lion-compatible predators may hunt rabbits; hunters and player can track local depletion; overhunting reduces subsequent encounters. |
	| **Fox** | Mostly solitary/paired search for rabbits and vulnerable poultry or duck young, scavenge accessible remains, avoid stronger predators and people. | Kit remains near den/home zone, follows or awaits parents, cannot perform independent adult hunting. | Farmers can perceive poultry losses; Predator Hunters respond to verified farm threats rather than killing any fox anywhere. |
	| **Coyote** | Opportunistic small-prey hunt/scavenge, sometimes travel in small groups, test risk before attacking livestock. | Pup follows adults, practices short chase/flee responses, relies on parent/group provisioning. | Competes with foxes over reachable prey and scavenging; may threaten poultry or young sheep, producing local farmer/guard/Predator Hunter responses. |
	| **Wolf** | Pack-coordinated approach/pressure and retreat; prioritize feasible wild prey or poorly protected sheep/other livestock; avoid needless fatal engagements. | Pup remains near secure group area, follows adults when capable, uses play and alarm/flee rather than pack offense. | Herd alarms and guard/knight responses can emerge from a real nearby pack; player intervention changes predation outcome and pack threat pressure. |
	| **Mountain Lion** | Solitary concealment, stalk and short committed ambush, defend space and withdraw if the risk becomes excessive. | Cub remains close to parent range; dependent on protection, hides or flees rather than independently stalking large prey. | Unpredictable close-range threat to lone prey/at-risk livestock or travelers; not a social pack hunter or a generic wolf replacement. |
	| **Brown Bear** | Forage widely, investigate food/remains opportunistically, rest, defend space or cubs with escalating warnings; usually avoids unnecessary pursuit. | Cub closely follows mother, explores cautiously, triggers strong maternal protective responses when threatened. | Hunting or crowding a cub can provoke defense; bear presence can scatter nearby animals and temporarily change use of an area without spawning scripted attacks. |
	| **Duck** | Alternate water/shore feeding, roosting and flock movement; adults may incubate clutches in suitable nesting space. | Duckling follows adult on land/water, stays grouped and seeks safe shore/water access. | Farmer can maintain ducks if owned; fox/coyote threats and player obstruction can separate a brood, influencing local survival and farm output. |
	| **Cattle (Cow/Calf)** | Adult cows/bulls graze and regroup; eligible females reproduce and may support existing milk production; group warns/defends young. | **Calf** stays near mother/herd, feeds and follows, has reduced speed/endurance until mature. | Existing Livestock Farmer manages grazing, feeding, breeding, and lawful ownership; theft, predation or player assistance changes herd count and production. |
	| **Sheep** | Graze as a cohesive flock, maintain spacing and shelter, react collectively to predators; adults contribute existing wool/husbandry outputs. | Lamb follows dam/flock, frequently regroups, flees poorly alone and needs protection. | Farmer/shepherd tasks and Predator Hunter work emerge from real flock risk; harvesting wool or taking sheep follows normal ownership and production rules. |
	| **Dog** | **Owned:** recognize household/handler, patrol a permitted home range, bark at perceived intrusions, greet/follow/play and rest; obey a limited set of learned, permitted orders depending on trust. **Wild/feral:** seek food/shelter, scavenge, avoid or cautiously assess people, optionally cooperate loosely with familiar dogs. Neither adopts wolf-pack predator tactics. | **Puppy** seeks parent/caregiver, learns safe proximity, explores in short bursts, plays and retreats to shelter; no effective guard/hunt role. | Players may acquire or care for owned dogs through real ownership transactions, bond through legitimate attention, give `follow/stay/return/guard-home/play` instructions when capable, or encounter wary feral dogs. Barking is an audible local stimulus, **not a crime report**. |
	| **Cat** | Choose favored rest/perch and scratching sites, groom, explore safely, stalk permissible small prey or toys, assess threats and people, seek affection **on its own terms**; owned and unowned cats differ in home territory and tolerated humans. | **Kitten** plays, hides, sleeps frequently and seeks its mother/caregiver; feeding and safe shelter are high priorities, with limited range and no independent hunting. | Players can interact with approachable cats; owned cats explore estate furnishings and the Menagerie scratching post. Nearby human NPCs may react with authored contextual dialogue; the animal never speaks human lines. |

	**Boundary:** These profiles supply *different goals, contextual responses and risks*, not 28 bespoke mini-games or 28 independent reasoning engines. The livestock catalog does not imply new craft recipes or drops; outputs are restricted to resource categories already supported by harvesting, hunting, food and husbandry systems until content is defined.

	### Dogs, cats, ownership, companion behavior and welfare

	**Identity is persistent.** Every owned dog/cat and consequential free-roaming dog/cat retains a unique ID, age/stage, sex, needs, current location, health, temperament, home/territory, actual owner (or `unowned`), recognized people, trust/bond history, and parents/offspring as applicable. Legal ownership changes only through a valid sale, adoption/transfer, birth-inheritance or other authorized event; petting, feeding, a temporary follow response, or approaching an unowned animal **never silently confers ownership**. A wild dog may gradually habituate to familiar people, but becoming owned requires a documented successful acquisition/transfer, not a species conversion or instantaneous mind control. Ownership affects crime, permissions, feeding responsibility and where an animal may legitimately live; bonds affect willingness to respond, not property title. Animals can still be wounded, stolen, die or become lost when actual circumstances permit; the Menagerie is not an immortality bank.

	| Behavior domain | Dog — owned / unowned | Cat — owned / unowned | Observable player/NPC interaction |
	|---|---|---|---|
	| **Needs and daily life** | Seek food, drinking water, shelter, company, rest; household dogs patrol/rest near permitted people; feral dogs choose safe roaming/scavenging routes. | Feed, drink, groom, sun/rest, seek vantage points and cover; free-roaming cats keep small independent ranges rather than human schedules. | Feeding and care consume or use real eligible resources/fixtures; needs are simplified bands, not constant player chores. |
	| **Senses and threat** | Species-authored hearing/vision and modest scent/tracking cues; alert posture, growling, barking, avoidance, defense. Feral dogs may be defensive or scavenge but **do not receive Wolf's coordinated-pack hunting goals**. | Sensitive hearing and vision, crouch/hide, flee/climb to eligible authored perches, hiss/swat only when justified. Cats do not become general household guards. | A bark or hiss can attract a nearby person through ordinary sound/LOS; observers decide what they actually witnessed and may investigate. |
	| **Social affinity** | Recognize handler and familiar NPCs; short contextual follow/stay/return/play, greet and guard-home assignments only where the animal can navigate and has sufficient trust. Orders cannot force remote teleports, impossible knowledge, invulnerable attacks or unbounded combat assistance. | Approach, rub, sit near known people or refuse contact based on disposition; investigate familiar rooms or the scratching post without forced obedience. | Context actions include `Examine`, `Pet` (when safe), `Feed`, `Play`, `Call`, and authorized `Assign Home`/`Release from Pen`; trained dog commands are additional *capabilities*, not a new player skill. |
	| **Young and family** | Puppies follow a caregiver, play and learn; household care must account for a mother/litter and actual space. | Kittens seek adults/shelter and are fed automatically by a functioning Menagerie feeder (§3.11); no juvenile hunting/guard loops. | Birth and maturity remain exact lifecycle events; a new puppy/kitten is not automatically added to a full 30-pet Menagerie roster. |
	| **Home and absence** | Home-range assignment, sleeping/rest anchor and return-home attempt; when separated, use last known handler position and route rather than teleporting. | Favor particular household sleeping/scratching/perch hotspots; may roam without standing next to owner. | On unloading, simulate owned individuals through lightweight records without losing location, care events, relationships, or the ability to find them again. |

	**Environmental fidelity:** Dogs have breed-independent base behavior and may later receive approved visual/temperament variations; there is **no required dog-breed catalog or genetics minigame**. Cats similarly permit visual/fur variations without separate species. Author deliberate idle, sniff, bark, whine, tail/posture, gait, sleep, grooming, scratch, pounce, playful chase, young-follow and mother/offspring animations, with sound/pose reflecting temperament. No instant bark triggers scripted arrests, no autonomous legal bounty from an animal, no cat magically detecting thieves through walls. Bond/trust changes through a *meaningful pattern of treatment*, not endlessly clicking Pet. The existing Honor event for petting is small and repeat-limited; neglect or unjustified harm to owned animals can have witnessed owner/NPC, crime and Honor consequences under §3.5.

	**Pet safety and interaction rules:** Feeding, care, guest handling, home guarding, adoption and arena eligibility respect animal health, needs, temperament, age, property access and current authoritative location. Only a consenting/eligible estate guest can interact with a permitted pet; pet aggression is not an all-purpose license to attack a trespasser. Dog and cat AI is active in farms, settlements, wild margins and owned homes **even when no Menagerie has been constructed**. Menagerie fixtures add care and activities; they do not replace the base animal system.

	### Habitat, food-chain and population model

	Each playable region is divided into **authored habitat cells**: terrain/cover access, water access, grazing/foraging suitability, nest/shelter anchors, safe movement corridors, settlement/farm boundaries, allowed species, activity windows, and a **soft carrying-capacity range**. These are generic tags and locations, not new item types or lore-specific biomes. Designers set species suitability and starting cohorts; seeded local variance changes *who and when*, never whether a Brown Bear can appear in an unsupported environment. Connected cells allow plausible migration/dispersal rather than universal random respawning.

	**Food-chain rules are deliberately limited to supported actors:** herbivore/omnivore access to tagged food availability; foxes and coyotes opportunistically targeting rabbits, accessible poultry/duck young or small vulnerable livestock; wolves using pack pressure against feasible prey/livestock; mountain lions stalking solitary feasible prey; brown bears primarily foraging and opportunistically scavenging, threatening others when circumstances justify it. Predator–prey matchups, group advantage and avoidance are **data-defined**, bounded and physically plausible. Unowned dogs mainly scavenge or pursue only plausible small accessible prey when genuinely hungry; owned dogs protect/retreat within local permissions rather than executing wolf-pack hunts. Cats may stalk appropriate existing small prey, but no rodents or other unregistered species are spawned to justify that behavior. **Do not require a fictional extra prey species to feed the predators.** Environmental food availability is a habitat resource signal; it is not secretly an unregistered animal spawn.

	In a populated cell, update a **conserved population ledger**, with transitions recorded by cause:

	```text
	nextPopulation(species, cell, lifeStage) =
	    previousPopulation + recordedBirthsOrHatches + permittedImmigration
	    - recordedDeathsOrHarvest - permittedEmigration + maturationStageTransfers
	```

	Maturation is exactly **one subtraction from Baby and one addition to Adult**; it is not a net population increase. Predation removes a real victim and supports the predator's feeding; a killed animal cannot simultaneously keep contributing to its herd or be harvested again. **Death causes** include hunting, predators and exceptional sustained need failure; ordinarily hunger/fear pressure changes *behavior and reproduction* before causing mass mortality. Disturbance (nearby fighting, pursuit, repeated hunting) raises caution and can temporarily displace groups or reduce local activity; it does not magically delete population. Long-term overhunting depresses a cell's real population; recovery requires birth, growth, or immigration from reachable populations. Population safeguards should avoid game-breaking ecological collapse by tuning birth/immigration and material inputs **without inventing invisible replacement animals**.

	**Carcasses/remains:** When an animal is killed in a loaded area, a world-state remains record has a location, time, any existing harvestable output, access/ownership, depletion and decay. Only currently supported predator/scavenger species may investigate it. A remain in an unloaded area can be represented by an abstract timer and resource count; no need to simulate every bite. Remains **never duplicate** the deceased animal or create unsupported collectible items.

	### Animal reproduction and life stages

	**Reproduction is a real system for all fourteen species; explicit breeding management is supported for owned livestock** (Chicken, Pig, Horse, Duck, Cattle and Sheep). Owned dogs and cats can also produce puppies/kittens through valid, appropriately cared-for same-species reproduction; this is **household animal care**, not a new Livestock Farmer occupational subtype. Wild/feral dogs and free-roaming cats reproduce under the existing population/individual lifecycle rules. Rabbit raising or captive wild species are **not presumed** merely because wild rabbits reproduce. Eligible **adult male and female** of the **same species**, access/condition, nourishment, local habitat capacity, and cooldown determine whether a reproduction attempt can begin; details are species-configurable. Do not generate offspring from two incompatible stages, a single lone adult, or a dead/absent required parent. Wild reproduction may resolve at the **cohort/group level** where individuals are unloaded and no player-observed parentage depends on it, but still requires plausible sex composition and species population state.

	For **mammals**, a successful attempt records a gestation/pregnancy outcome and a future game-time birth; for **Chicken and Duck**, a suitable adult breeding state creates a nest/clutch-incubation **world-state record**, later hatching into chicks/ducklings if viable. Clutches are **not automatically inventory egg items**, and no artificial mating animation is required. Species-specific gestation/incubation times, clutch/litter sizes, recovery, maturity age and breeding intervals are *configurable design parameters, not real-world-length obligations*. Scale time so the system is observable and rewarding in a single-player game without explosive population growth. Never breed merely because a timer elapsed when the parent, enclosure or habitat is no longer valid.

	**At birth/hatch:** Commit exactly one event, create or reserve the correct number of **Baby** individuals in the right cell/group/owner ledger, attach mother/caregiver relationship where supported, and record a mature-at game-time. An individual baby receives a corresponding production-intent juvenile model/animation profile, smaller collision and speed/endurance ranges, dependency/follow behavior and suitable adult protection. Babies grow gradually in presentation before transitioning atomically to **Adult** on maturity. Premature death cancels that individual's maturation; no automatic replacement occurs.

	**Livestock Farmer → breeding loop:** A Livestock Farmer checks true ownership, usable animals, available food and space, reproductive eligibility and existing obligations; chooses a viable species-compatible breeding pair/group; commits an authorized breeding assignment; tends the pregnant animal or incubating clutch; responds to feed shortfall, poor health/absence or predator exposure; witnesses a real birth/hatch or receives a legitimate distant-simulation completion; cares for dependent young; and later may manage the grown adult. **The player** may assist using existing contextual husbandry and resource-transfer actions when permission allows, buy/sell genuine animals where supported, protect the herd, interfere with feeding/access, steal livestock with law consequences, or just observe. A farm with only one eligible animal cannot conjure another parent; services and stock must be actually available.

	**Ownership and exploitation protections:** An owned baby inherits an authorized ownership/household relation based on the breeding record, unless an existing law/transaction explicitly changes it. Stolen breeding animals retain provenance; witnessing and Guard reporting use §3.5. No XP is generated merely by waiting for pregnancy, opening a breeding menu, or reloading before birth. Where Harvesting or other existing skills are relevant, award XP only for a **real performed skilled action** according to §2.2 and the shared XP transaction contract; exact husbandry XP mapping remains **open**. No endless birth rewards, instant adult growth, free input creation or duplication by unloading/reloading.

	### Player, NPC and animal interactions

	| Existing actor/system | Valid grounded interaction | Persistent visible result |
	|---|---|---|
	| **Player** | Watch feeding/group movement, approach carefully, help lawful feeding/care, defend an animal, hunt with direct combat, obstruct a path, remove existing supply, steal owned livestock or deliberately frighten animals. | Animal alarm/flight/injury/death, ownership/witness events, herd condition, changed resource access, altered population and consequences that survive leaving. |
	| **Livestock Farmer** | Check specific cattle/sheep/pig/horse/chicken/duck needs, feed and tend owned groups, initiate eligible breeding, protect young, collect permitted existing husbandry outputs. | Real breeding/care progression, birth, maturation, lost stock and production capacity. |
	| **Game Hunter / Trapper** | Evaluate actual wildlife density, tracks/signs, legal access and feasible prey, then hunt or trap using existing skills/fixtures. | Animal population decreases; harvest/remains and supply resolve once; depleted areas yield fewer encounters. |
	| **Predator Hunter** | Identify verified local threat from wolf, coyote, fox, mountain lion or bear, locate it and act to defend settlements, routes or livestock. | Threat resolved, relocated or still present; cannot claim success against an animal that did not exist. |
	| **Merchant / Hauler** | Buy/sell existing husbandry outputs and fulfill legitimate supply work related to owned livestock, within existing stock/transport permissions. | Funds/ownership/shipments and shortages reflect actual farm events. |
	| **Guard / Knight** | React where assigned to witnessed or reported livestock theft, a dangerous animal on a protected route, or an authorized escort/protection duty. | Normal crime-report causality or protection task results; no omniscient animal-crime alerts. |
	| **Other wildlife** | React to alarms, investigate actual remains, hunt feasible prey, yield territory, defend offspring and seek resources. | Connected predator/prey outcomes and evidence that a player can discover without scripting a quest. |
	| **Dog / Cat owners, player and guests** | Acquire, care for, greet and maintain real companion-animal relationships under ownership/access rules; respond to animal distress, barking or independent roaming. | Persistent trust, home preferences and recognizable interactions; cruelty, loss or theft retains normal law/Honor consequences. |

	**Player-facing authenticity:** A rabbit spots danger and bolts; ducks on a bank scatter into water; a cow moves toward a distressed calf; wolves hesitate if their pack is outmatched; a bear defending cubs warns or charges when crowded; a dog recognizes its owner and barks at an unfamiliar sound without knowing whether a crime occurred; a kitten investigates a scratching post while a nearby household NPC reacts naturally. These moments arise from normal local state, with readable body language/sounds, not arbitrary scripted wildlife encounters. The player can **observe without being treated as the center of every encounter**. Avoid excessive animal chatter/pop-up UI; readable animation and ambient sound do most of the communication.

	### Runtime architecture: hundreds of animals without hundreds of full AI updates

	Use **population scale** separate from **fully simulated entity count**. The system must support **hundreds of animals across a streamed game area**, while maintaining a strict budget for how many receive full movement, perception, combat, animation and physiology evaluation at once. These are tuning targets, not a promise of hundreds of simultaneously visible, fully animated actors.

	| Simulation tier | Active when | State and behavior | Conservation/transition |
	|---|---|---|---|
	| **A — Observed** | Near player, in direct combat, breeding/birth/handling being watched, or otherwise consequential on screen. | Full individual entity/animation/navigation, local sensory reactions, parent/young movement and actual combat collision. | Persistent ID for any owned, named, wounded, pregnant, attached or otherwise consequential animal. |
	| **B — Nearby/Unobserved** | Near enough to enter view shortly, or significant locally but out of sight. | Simplified group intent, occasional checks, approximate travel, hunger/fear bands; individual outcomes if consequential. | Do not teleport through a watched space; preserve group count, position and disturbances on promotion to A. |
	| **C — Regional/Cohort** | Distant habitats, with no player-observed individual behavior requiring exact reproduction. | Update sex/stage cohorts, births, maturation, mortality, habitat use, group moves and predation events from bounded regional rules. | Aggregate exact counts and important histories; explicit promote/demote ledger transfers to/from live actors. |
	| **D — Inactive/Time-skip catch-up** | Unloaded regions or large legal time advances. | Discrete age/resource/population integration at coarse game-time steps and summarized events; no unseen frame-by-frame hunting scene. | Process each lifecycle/event timer once; enforce capacity/cooldowns and bounded catch-up. |

	**Identity & population conservation:** All owned livestock, authored/observed important animals, births the player witnesses, wounded or tracked targets, active reproduction records, and surviving owned young keep durable IDs across saves and region streaming. Generic unseen wild animals may exist as exact integer **cohort counts** by region/species/stage/sex (rather than one serialized brain each). Materializing a wild individual **moves one count out of the cohort** into an actor instance; dematerializing an eligible ordinary individual **returns exactly one to the correct cohort** with updated stage/state. Death/harvest subtracts from whichever representation currently owns the animal. Never count a physical entity and its cohort entry simultaneously; don't rematerialize a dead tracked wolf when the player turns away and back.

	**Scheduling:** Spread AI decisions over time and use event-driven wakeups for sudden threats, pain, births, injuries, and player intervention. Slow cohort updates may use minutes or hours of *game time*, whereas visible fights and animal movement need reactive frame-appropriate evaluation. Exact intervals, active counts, animation LOD, culling and spatial-partition budgets are engineering-tunable, with test fixtures supporting **hundreds of total animals**, tens or fewer fully active at a time as needed for target hardware. Maintain deterministic/seeded scenario reproduction and inspectable population accounting.

	**Spawning is presentation, not creation:** Authored habitat distribution plus real births, dispersal and starting population establish the animals. Loading into a cell instantiates **existing accounted-for wildlife**, never creates free additional population. If the player camps in view of a meadow, animals may approach using actual viable routes or remain absent; they cannot materialize nearby to fill a cosmetic quota. Population collapse is possible locally, but recovery can occur through supported reproduction and immigration over time if neighboring populations exist.

	**Cross-system requirements:** Animal births, harvests, owned livestock, NPC care, hunter predation, pet recognition and post-death mount flight must feed the same food/economy, Honor/crime, social knowledge and persistence contracts as other entities (§3.13); animals do not duplicate inventories or substitute for human NPC looters.

	### Devmap, art and test contract — target

	Use the production-quality reusable devmap already specified in §4.2. Add an animal test enclosure/farm corner, a connected wild habitat, cover/water points and adjacent predator/prey test corridors as **simple layouts populated with transferable, production-intent animals and interactables**. Prioritize representative **adult/baby model pairs from different body plans** (e.g., Cattle/Calf, Chicken/Chick, Wolf/Pup) with genuine locomotion, idle, feeding, fleeing, contact and follow/protection animation integration; expand toward the full fourteen-species catalog through the same reusable asset pipeline. A young animal must not be an adult mesh shrunk without appropriate rig, proportions, animation and collision work.

	**Required test cases:** (1) 14-species validation and two stages each; (2) groups choose feeding/rest/water routes and react to nearby player without scripted triggers; (3) prey alarms and a physically valid predator attack affect an actual population; (4) farm assignment with two viable owned adults progresses pregnancy or clutch → one birth/hatch event → baby following parent → maturity; (5) missing mate, poor care, insufficient space or owner permissions prevent a valid breeding attempt; (6) adult/young/owned/observed ID and count invariants survive sleep, fast-forward, save/reload and streaming; (7) two wildlife cells lose animals through overhunting and regain them only via births or connected immigration; (8) farm theft follows normal ownership → observation → Guard report; (9) a player-observed animal does not respawn or duplicate at the camera edge; (10) hundreds of aggregate animals with deliberately limited full-actor budgets yield consistent totals and no visible herd teleporting. Use per-animal/group/cell inspectors for **goal, stimulus, age/stage, parent, breeding countdown, care conditions, legal owner, recognized handler, trust/home assignment, group membership, cohort count, alarm, population causes and last event**. Add an owned dog following a real owner, a wary wild dog avoiding strangers **without Wolf pack tactics**, an owned/free-roaming cat using rest and scratching anchors, and a puppy/kitten reaching maturity across save/streaming boundaries.

	**Open to balancing and art/engineering:** Pet acquisition/adoption conditions, dog commands/affinity thresholds, cat play/perching placement, pet food compatibility, reproductive care and placement overflow, feral roaming pressure, pet health/welfare thresholds, species-specific animation variants, habitat-map authoring method, precise population sizes/soft caps, allowed regional diets and predator preferences, group sizes, game-time gestation/incubation and maturity durations, litter/clutch sizes, reproduction seasons, care requirements and consequences, natural mortality, carcass decay, breeding-action UX/XP, animal ownership transfer, hunting outputs, performance limits, full baby/adult model/animation budgets, and any horse riding or combat-trained pet progression beyond the defined basic commands (not automatically granted). This section establishes the *behavioral architecture and testable guarantees*, not a new content or lore catalog.


	## 3.10 Stealth, shadows, suspicion, and Thieving — shared perception system

	**Established design target:** Stealth is **ordinary 3D movement plus NPC/animal senses**, not an invisibility toggle, stealth-only minigame, global hidden meter, or a guaranteed detection roll. A character can avoid notice by staying out of line of sight, using genuine cover and shadows, moving quietly, waiting for NPC attention to shift, or affecting perception with already listed spells. Detection and pursuit obey physical world state, memory, communication, crime, and authored quest constraints. A player's Thieving level improves trained illicit execution and supported stealth technique; it never switches off NPC eyes, grants magical invisibility, or makes an actually witnessed crime un-witnessed.

	### Perception layers and readable stealth states

	| Layer | What agents can sense | Player / NPC response |
	|---|---|---|
	| **Sight / geometry** | Configured visual angle and distance, actual line-of-sight ray/occlusion, target partial cover and posture, local lighting and shadows, movement silhouette. | Stay behind real obstacles; crouch or reduce exposed silhouette; move between genuine shadowed regions. Seeing a character is not the same as recognizing an offense. |
	| **Illumination** | Discrete environmental visibility bands derived from the scene's existing sun/lamps/shadowing or equivalent approximated region data. | Darkness can lower visual clarity where a spell actually changes visibility. Bright daylight exposes targets more readily; shadow alone is not guaranteed concealment at close range. |
	| **Hearing** | Event sound strength, speed, distance, barriers, movement/action category, and present attention; no carry-weight statistic. | Slow movement is less conspicuous; running, attacks, doors, lockpicking and collisions can attract investigation when audibly justified. |
	| **Recognition** | Face/identity, clothing or known heraldry, memory of prior interactions, distance/lighting, and supported disguise effects. | NPC may see an unknown figure without identifying the player; Disguise Self affects identity belief only while its valid effect is active. |
	| **Suspicion / memory** | Known recent sounds, unexplained movements, seen items changing owner, last known target position, witness confidence and believable communication. | Agent checks a disturbance, warns someone, seeks help, resumes ordinary work or escalates when evidence warrants. |

	**NPC awareness states:** `Unaware → Noticed/Curious → Investigating → Alert/Confronting → Searching/Pursuing → CoolingDown/Resuming`. Transitions are **condition-driven**, not mandatory passage through every state. A credible visible assault can jump straight to Alert; minor distant noise may lead only to Curiosity. The actor preserves last observed position and factual uncertainty. Breaking line of sight **does not delete memory or instantly end a pursuit**, while an agent cannot continuously track a target through solid walls using omniscient coordinates.

	**Decision logic:** Each agent's shared perception has capabilities and thresholds influenced by location, assignment, attention, personality, prior alarms and individual senses. The **100 existing human NPC subtypes** then choose occupationally appropriate reactions: a Gate Guard examines checkpoint anomalies, a Pickpocket assesses attention, a Merchant checks missing stock, a Knight protects their charge, and a Hobo preferentially withdraws from danger. Subtypes do not need separate vision systems. Existing predators/prey use species-configured sight/hearing/fear for stalking and flight; humans' faction recognition and legal reasoning do not transfer to wildlife.

	### Thieving interactions and the law

	- **Approach and opportunity:** Thieving affects the mechanics of **pickpocketing, lockpicking, unauthorized access, theft timing/noise and eligible covert action**. Higher skill can improve action speed, steadiness, noise control or skill-check performance where explicitly configured. Skills do **not** override a locked gate, lack of a lockpick, full inventory, or a target plainly watching the theft.
	- **Physical concealment versus ownership:** Hiding near or taking an owned item does not erase its legitimate owner. Unauthorized transfer commits normal theft provenance if the action succeeds, **even when undetected**. Stealth only affects who can perceive the action, identify the actor, and later relate a credible report.
	- **Crime recognition:** A witness must perceive the **criminal act**, have sufficient basis to identify a suspect if making an identified report, retain evidence/memory, and actually reach a Guard (or otherwise use a previously established permitted reporting channel). A Guard may investigate an unknown perpetrator without magically producing a known player's bounty. A false or uncertain belief cannot silently become certain global knowledge.
	- **Magic interaction:** **Darkness** changes actual relevant sight conditions; **Minor Illusion** can draw attention or alter plausible belief if it creates a supported observable effect; **Disguise Self** affects apparent identity; **Friends**/**Charm Person** influence eligible social behavior, not physical LOS; **Gaseous Form**/**Spiritual Step** affect travel/position if eligible. Spell names alone do not guarantee undetectability, unimpeded burglary or immunity to consequences. Casting can itself generate a perceptible sound/sight event if the eventual spell definition calls for one.
	- **Feedback:** Readable NPC head turns, investigation gestures, inquiry/dialogue, guard warnings, search at the last known location, retreat or return to work; optional minimal player UI may communicate local exposure, but it cannot display secrets an NPC has not shared. No omniscient 'everyone is alerted' meter.
	- **Thieving XP:** Credit meaningful committed theft/lockpick outcomes as defined in §2.2; standing in a shadow, crouching idly, repeatedly reopening an unchanged lock or deliberately triggering/resetting a predictable suspicion loop pays **no XP**. If the same item qualifies as guarded-premises theft and container theft, only its highest valid same-skill route pays.

	**Cross-system stealth requirement:** Illumination from actual fire, authored Magic effects and environmental cover feeds the same perception query used by Thieves, Guards, predators and ordinary witnesses. Stealth affects what is observed, not whether a committed theft occurred (§3.13).

	### Persistence, performance, and devmap acceptance

	Persist only **significant** sightings, crime evidence, lasting suspicion or searches, known identities and last known positions relevant to continued behavior. Ordinary light/hearing sampling can be transient. Near loaded agents get real occlusion checks and animation-level investigation. Far/unloaded agents resolve conservatively from places, schedules, actual encounters and grounded knowledge—**not** from global full-map sight or fake reports. Perception checks can be time-sliced and event-triggered rather than repeated against every NPC each frame.

	**Devmap tests:** (1) a target crosses from shadow behind cover into lit view and is noticed at a different threshold; (2) a genuine sound outside sight causes an investigation, not instant identification; (3) a moving target breaks LOS and the observer searches the **last known position**; (4) a pickpocket transfers an owned item while unseen and creates no automatic bounty; (5) a second theft witnessed and reported reaches a Guard and creates a jurisdiction-specific wanted state; (6) Darkness and Disguise Self influence perception/identity only under actual valid conditions; (7) a Thief NPC and a Guard use the same sensory substrate but choose different occupational reactions; (8) save/load preserves significant memory and not an omniscient live target position.


	## 3.11 Player estates, Construction rooms, and household utilities

	**System purpose — established:** A purchased estate is the player's most capable **optional utility hub**, combining Construction, storage, restoration, transport, **pets and collections, safe combat practice, decorative themes, Fishing showcases** and supported NPC services. It remains a persistent physical property with real access, ownership, equipment, room and inventory state—not a disconnected upgrade screen or mandatory prerequisite for ordinary adventuring. Its modular rooms and furnishings are production-intent assets authored once on the devmap and reused in GAMEWORLD. Only explicitly described estate fixtures and fish-egg collectible items are added; the specification does **not** imply further species, spells, equipment slots or unsupported combat abilities.

	### Estate availability, ownership and acquisition

	- **Property supply:** Provide **at least three distinct purchasable estate plots in each kingdom** (minimum **nine across the three initial kingdoms**). The **devmap requires one fully functional estate plot**, plus a synthetic Estate Agent and property listings for testing; it does not require the three kingdoms or their finished terrain. More estate plots or kingdom-specific differences are content decisions, not new systems.
	- **Estate Agent:** The player discovers available estates, purchase price, ownership, location and access through an **Estate Agent**. Estate Agent is a **property-sales/service assignment to an eligible existing human NPC with a registered concrete subtype**, not a new eighteenth occupational family or an NPC with no subtype. The assignment grants only validated listing, purchase and property-service interactions; it cannot bypass the 100-subtype work/permission rules or spontaneously generate property. Exact assignment/subtype mapping is **Open**.
	- **Purchase contract:** Confirm available listing, sufficient legitimate funds, jurisdiction and ownership eligibility; commit payment and ownership change **once**. Record stable estate/plot IDs, buyer, district, permissions and ownership history. A sold plot is not simultaneously listed as unowned. Sale, resale, ongoing fees and concurrent-estate limits are **Open**; do not assume a required noble title or global ownership limit.
	- **Multiple locations:** Each purchased estate has an **independent** layout, rooms, furniture, storage contents, hired NPC assignments and unlocked fixtures. A player may designate a preferred home for convenience once that behavior is defined. Owning multiple estates does not automatically combine storage, duplicate amulets, link doors, or teleport the player for free.
	- **Access and law:** Owners can use authorized building and household interactions. Visitors, hired workers and other NPCs receive explicit permissions; unauthorized entry/theft uses existing ownership, LOS, witnesses and kingdom law. Estates do not grant blanket legal immunity, territorial government or mandatory noble progression (§3.5–3.6).

	### Building Mode — grid/door graph, room previews and fixed hotspots

	**Building Mode** is the estate-specific editing state, **selectable on entering an owned estate**; it is not automatically forced on every visit. Normal gameplay mode allows travel, conversations, use of services and existing furnishing interaction. Building Mode exposes permitted construction actions and ghostly preview guides in a clearly different presentation layer.

	1. **Room anchors and connectivity.** An estate begins with an authorized entry/exit and a finite, data-defined building footprint. Rooms occupy valid room cells/placements; each room definition exposes **fixed doorway hotspots along its edges**. A new room can be placed **only by selecting a valid unoccupied door hotspot on an already built room**. The preview shows alignment, room orientation, accessible adjoining doors, bounds, cost and eligibility before committing. No floating, overlapping, unreachable or arbitrary free-placed rooms.
	2. **Occupied door rule.** If the selected door hotspot is occupied by a connected room, the player receives a **Remove/Rebuild Existing Room** prompt. The player **must delete the existing adjacent room first** (and address any rooms attached beyond it) before placing a different room there. Never silently overwrite a room or its contents. Reject operations that strand the entrance or active occupants unless an explicit safe relocation/rebuild path succeeds.
	3. **Furniture hotspots.** Every room has an authored set of **fixed, ghostly furniture hotspots**, each admitting specified furnishing categories and orientations. The player chooses from eligible furnishings for that hotspot. Furniture **cannot be freely dragged, nudged or relocated** in the room. Reorganizing it means removing/replacing the furnishing through Building Mode with a valid destination; relocating the *room itself* requires deletion and reconstruction through the room graph. Appearance/variant choices can provide design freedom **within** the authored hotspots, not freeform physics placement.
	4. **Validation and atomic cost.** Constructing or upgrading a furnishing/room checks estate ownership, Building Mode, existing room/door state, effective Construction level, applicable **hammer and saw**, actual materials, valid connection and a free destination for displaced possessions. Preview and failed attempts cost nothing and award **no XP**. At the **construction completion/commit point**, consume materials exactly once, establish the room/furnishing instance, update interactions and award validated Construction XP for genuine new work.
	5. **Removal and recovery.** Before deleting a room or occupied furnishing, summarize affected attachments, workstations, installed amulets and stored items, active servant use and inaccessible connected rooms. Require explicit confirmation, resolve every stored asset through a valid transfer or **reject removal**. Never destroy/duplicate possessions as a hidden consequence of changing the layout. Materials recovered/refunded on demolition are **Open**; the same materials must never yield infinite Construction XP through rebuild/refund loops.
	6. **Usability and presentation.** Out of Building Mode, all installed fixtures remain physical, textured, animated and interactable where applicable. In Building Mode, ghost guides do not represent duplicated world items, collision, or real resources. Rooms, their hotspot definitions, trim/material variants and use-state hooks are **portable reusable prefabs** rather than geometry scripted specifically to one plot.

	**Freedom within constraints:** Players can completely reconsider an estate's room composition, order, size where supported, design and furnishing choices **through valid demolition and rebuilding**. Fixed hotspots do not imply a permanent initial blueprint; neither do they permit free-moving furniture or bypassing doorway connectivity. Valid placement footprint, number of floors, room caps, style palettes, salvage/refund rates and demolition costs remain **Open**.

	### Early Construction and room progression — established requirements

	**Construction is trained by actually constructing and repairing eligible property**, using the already defined five qualitative XP routes in §2.2. A room purchase, estate purchase, blueprint preview, deletion or arbitrary object movement never grants Construction XP. XP depends on the **completed furnishing/room and its real materials/difficulty**, not on a hardcoded award for being in Building Mode.

	| Construction level | Room or fixture | Established build specification | Primary function |
	|---:|---|---|---|
	| **1** | **Parlor** | First low-level training room; standard owned-room/door placement checks. | Basic entry-level furnishings and Construction XP. |
	| **1 — furniture** | **Crude Wooden Chair** | **Exactly 2 planks + 2 nails**, with **hammer and saw** available; valid Parlor chair hotspot. | Lowest-tier furnishing in the entire game; awards **very small, variable Construction XP** on successful construction. |
	| **Garden** | **Garden** | Can serve as the **entrance or backyard**. Building it consumes **10 iron bars + 10 soil**; Construction level to unlock **Open**. | Outdoor room and supported garden hotspots; distinct from later Superior Garden. |
	| **5** | **Kitchen** | Room placement and building inputs besides room level **Open**. | Existing Cooking workstation/use actions and appropriate furniture. |
	| **10** | **Dining Room** | Room inputs and layouts **Open**. | Eating and household/social furnishings; no invented hunger buff. |
	| **15** | **Workshop** | Room inputs and layouts **Open**. | Legitimate tool/repair fixtures and later armor stand/combat dummy. |
	| **20** | **Bedroom** | Room inputs and layouts **Open**. | Rest and household accommodation; counts toward servant eligibility. |

	**The Parlor's Crude Wooden Chair is the absolute first and lowest-yield furniture training recipe.** The first Parlor does not need unrelated lore, special tools beyond hammer/saw or an arbitrary quest. The Garden's **10 iron bars + 10 soil** is a real consumed construction cost, not decorative text. Early room progression is intentionally rapid. Additional room types unlock through Construction levels and room/furnishing eligibility as authored; do not assign unsupplied numerical unlocks.

	### Advanced utilities — estate facilities and dependency contract

	The player can attain the full intended **utility-complete house at effective Construction 92**, potentially by training to **base level 84 and receiving at least +8 eligible temporary Construction boost**. The `effectiveLevel` is validated when each level-92 build action commits. Temporary boosts **do not** permanently raise skill level/XP, allow equipment-gate bypass or cause a completed furnishing to stop functioning when they expire. **Boost source, stacking/cap and each advanced furnishing's exact unlock level/material cost are Open except for the supported level-92 top-end requirement.** Utility-complete is distinct from mastering Construction at level 100.

	| Room / fixture | Installed utility / player interaction | Existing-system integration and restrictions |
	|---|---|---|
	| **Ornate Rejuvenation Pool** | A single successful use **fully restores current HP, Faith points, temporary drained stats, special-attack reserve where implemented, and run/stamina**, and removes **poison and venom** status where present. | Restoration is one validated transaction, capped to legitimate maxima. **Does not grant skill levels, XP, extra items or an unstated Mana refill.** Poison/venom and special-reserve hooks may be test status definitions until their full systems are balanced; don't invent sources or creatures. |
	| **Crystalline Portal Nexus** | Provides **unlimited repeat teleport uses** among genuinely registered/accessible destinations once the fixture is built and configured. | Teleport requests still check actual allowed destination, arrival/safe-position rules and world-state permissions. No charges or per-trip resources consumed for the nexus's approved destinations; it cannot teleport to arbitrary coordinates, fabricate unknown places or bypass authored restrictions. Destination unlocking, nexus capacity and registration method **Open**. |
	| **Mounted amulets** | Existing eligible amulets may be mounted at authorized estate hotspots for **unlimited teleports** to their registered destinations. | Mounting performs a real inventory → furnishing transfer (and unmounting reverses it when space permits); **never clones jewelry or adds a wearable slot**. Supported amulet list, valid destinations and initial mounting prerequisites **Open**. |
	| **Occult Altar** | Allows the player to **switch their active spellbook** among **Standard, Ancients, Lunar and Arcane**, subject to whatever book access/unlock rules are established. | This establishes **one active spellbook at a time** when casting, while known/unlocked spells and current Mana persist. Swap is a validated altar interaction, not a fifth book, an invented spell or a free unlock of missing spell slots. Other possible switching sites, combat restrictions and cast interruption rules **Open**. |
	| **Gilded Altar with burners** | Eligible Faith-training offerings completed at the altar with the required burners active yield **350% of that action's ordinary base Faith XP** (**3.5× total**, **not** +350% or 4.5×). | Uses real offerable resources and one committed XP event; no passive/toggle XP or free infinite offering. Burner activation/fuel and valid offering definitions are content **Open**. The altar may also support existing Faith-point restoration as a separate interaction, which **does not** award repeat Faith XP. |
	| **Superior Garden — Spirit Tree and Fairy Ring** | Installs **Spirit Tree** and **Fairy Ring** as two registered world-travel interaction points with their respective permitted destination networks. | These are **estate travel fixtures**, not authorization for a newly simulated tree species, animal type or spell. Network membership, eligibility and destination discovery **Open**; activated travel is reusable without consumable per-trip charges unless a separately authored effect explicitly requires one. |
	| **Costume Room — collection fixtures** | The **Toy Box** holds supported holiday-event collectibles; **Treasure Chest** holds real Treasure Trails/clue-scroll rewards; **Jewelry Box** holds eligible accessories; **Cape Rack** holds regular, skill and achievement capes; **Armor Case** stores eligible complete or partial armor sets and previews their looks. | All are actual item containers filtered by item tags, owner/permissions and configurable capacity. Armor Case groups existing individual equipment pieces; it **never creates a packaged extra set**. The Treasure Chest displays rewards earned elsewhere, not an endless clue-reward generator. Viewing a set is not equipping it. |
	| **Trophy space — head mounts and collection gallery** | Approved wall hotspots display **stuffed monster-head trophies**, which can be **swapped instantly through the mounted display menu** without demolishing the wall or rebuilding the room; a guest with viewing access can inspect the **full owned trophy collection**, including non-mounted stored entries. | Each trophy is a unique earned/obtained item in estate storage or a valid fixture, with one authoritative holder; mount changes the display reference and physical presentation atomically. The gallery enumerates genuinely owned/acquired trophies, not unseen drops. Trophy acquisition, head catalog and taxidermy preparation rules **Open**. |
	| **Menagerie — indoor or outdoor pet home** | An indoor **Menagerie** supports up to **30 registered owned pets**, currently dogs/cats; compatible feeding/rest/scratching/habitat hotspots provide care and believable resident behavior. Its **outdoor variant** additionally permits an **Arena** for voluntary, playful pet-versus-pet mock battles. | Registration has a hard maximum of **30 real pet IDs per Menagerie**, not 30 simultaneously fully animated actors. Kittens are automatically fed via stocked feeder/caregiver functionality, with shortages clearly reported. Pet arenas are **strictly noninjurious** novelty contests, never lethal fights, Thieving/combat XP machines or gambling. See detailed rules below. |
	| **Aquarium — Fishing collection room** | Display **collectible fish eggs** associated with actually caught supported fish, maintain fish/tank presentation and a collection log, and unlock modest authored **Fishing convenience/perk** hooks through meaningful collection milestones. | Egg items arise only from an eligible committed Fishing event, never by opening the tank or reloading. Displays/care use genuine aquarium records and do **not** spawn new outdoor fish species, unlimited edible catches or passive XP. Fish categories, egg odds, perks, care costs and room/furnishing unlocks **Open**. |
	| **Combat Room — practice rings** | Build a **Boxing Ring** (unarmed), **Fencing Ring** (approved weapon sparring), or **Combat Ring** (unrestricted supported weapon/magic/Faith approaches within arena safety). Host consenting NPC opponents for **legal, fast repeatable combat training**; defeat returns the player inside their own estate **without the normal death penalty**. | A sanctioned nonlethal sparring instance resolves real movement, contact, active defense and appropriate XP, but replaces terminal damage with a match-defeat result; it never kills NPCs, triggers a corpse, crime, Honor murder loss, gravestone or ordinary death retrieval. No practice dummy XP or passive reward loops. See detailed rules below. |
	| **Estate exterior/interior style kits** | Select **Swamp**, bright **Civitas**, or **Wilderness** style kits to restyle compatible roof, wall, trim, furniture finishes and grounds using reusable appearance/material variants. | The layout, door/furniture hotspot graph, collision, travel, contents, costs already paid and permissions remain unchanged. Kits are purely cosmetic, not region swaps, creature spawners or additional kingdoms. Kits/recipes and eligibility **Open**. |
	| **Workshop — Armor Stand** | Supports **discounted repair** of eligible worn/damaged equipment. | Repair is an existing equipment-condition/material/currency transaction; the stand reduces an applicable *repair expense* rather than spawning materials, ignoring tier restrictions or restoring unrepairable items. Discount amount and affected repairs **Open**. |
	| **Workshop — Combat Dummy** | Safe target for testing attacks, weapons, animation timing, specials and hit telemetry. | **No unlimited Attack/Strength/Defense/Health XP from striking an inert dummy** and no free special-attack charge regeneration. Provides meaningful practice/testing feedback without being a fake hostile combat farm. |
	| **Study — Lecterns** | Produce **teleport tablets** through eligible authored craft interactions. | A tablet is an actual consumable output from validated inputs, skill/recipe access and commit. The Study cannot create unlimited free tablets; tablet destination catalog, exact inputs, unlocks, use conditions and XP attribution are **Open**. |

	**Travel is powerful but finite in scope:** The Nexus and approved installed networks offer **unlimited uses of their valid destinations**, not arbitrary instant movement, infinite physical copies of installed jewelry, or new dungeon/quest bypass permissions. These destinations operate through the same travel, region activation and time/world-state systems as other supported travel, with safe placement and interrupted-action handling. Owners can use high-value services once constructed even if a temporary Construction boost has expired.

	**Pools, altars and special resources:** The pool's Faith restore is a specifically permitted new restoration source alongside ordinary altars, religious wine and potions (§2.5). The pool restores **stamina/run** rather than inventing a second run-energy statistic. "Stats" means **current temporary stat drains**, not XP, base skill levels or permanent buffs. Special-attack energy is restored only if the established combat-special reserve is implemented; its charge model is still **Open**. Restore actions remain useful even at full resources, but cannot grant XP or cycle resource generation.

	### Menagerie and pets — a living household, not a collectible counter

	**Build and capacity:** Both indoor and outdoor Menageries are real Construction rooms/variants with the same fixed-door and fixture-hotspot rules. Each constructed Menagerie can register **a maximum of 30 owned pet entity IDs**, currently dogs and cats; stage and visual variation do not change this limit. Registry occupancy and a pet's physical location are different values. A pet may be resting, visiting an allowed part of the estate or nearby outdoors while counted against the Menagerie roster; a registered animal **cannot also be registered at a second estate or cloned by accessing a collection menu**. Moving its home to a different estate is a validated ownership/location/roster transfer. A full Menagerie refuses new registrations rather than deleting an animal, forcing birth losses or inventing extra capacity. Puppies and kittens born into an owner's household are real animals but require an available roster slot before being registered; their actual care/location remains the owner's responsibility in the meantime.

	**Automatic kitten feeding:** An installed feeder/care hotspot periodically checks real registered kitten needs and available **eligible stocked food**. Successful feeding consumes its configured quantity exactly once, reduces hunger and emits normal pet/household response cues. It functions during legal estate time-skips/unloaded simulation. If stock or access runs out, mark the care attempt unfulfilled, give the owner a legible notification on return, and permit authorized restocking (including actual servant delivery if their existing subtype supports it); **never produce infinite free food, kill kittens offscreen silently, or grant idle XP**. Care for puppies and adult pets uses compatible generic food/water/shelter hooks; detailed recipes and feeding intervals remain Open.

	**Scratching post and personality:** A cat may autonomously select a reachable scratching-post hotspot according to its needs and personality, perform a distinctive scratching/arching animation, and leave a short-lived observable state/event. A player or nearby NPC **who actually notices** can trigger a brief **authored context dialogue variant**—amusement, annoyance, affectionate recognition, an owned-pet greeting—conditioned on relationship, known cat and proximity. It is **human dialogue about the cat**, not a talking animal, randomly generated quest or an omniscient world notification. Pet idles include sleeping, drinking, social approach, sniffing/grooming, play and watching visiting people. NPC visitors may greet a familiar dog or avoid one they fear; those reactions use §3.3 and §3.10 perception/memory.

	**Customizable habitat hotspots:** A Menagerie can install **Grassland** or **Desert** habitat *dressing* as a selected visual/environment furnishing variant, with species-safe walkable/rest/shade/water anchors. These themes add tactile set dressing, foliage/terrain materials and ambient presentation, **not a new outdoor ecological region, species registry, predation exemption, free food source or rearrangeable room geometry**. All animals still need suitable space and valid navigation; two aesthetically different environments can share the same base behavior/hotspot schemas. Additional themes are Open.

	**Outdoor pet Arena:** The **outdoor Menagerie only** can install an Arena fixture on its permitted anchor. Two **present, consenting/eligible owned pets** (or a guest-owned pet admitted with real guest/owner permission) can enter a brief **play-battle** activity: feints, chase, playful pawing, mock lunges, disengagement, yielding and victory pose. Species, age, stress, injury and temperament gate participation; a frightened or unwilling pet opts out. The contest resolves through safe play-animation/interaction state rather than ordinary damaging combat: **no HP loss, injury, kill, animal suffering, Faith/Magic drains, PvP wagers or combat skill XP**. It may inform a cosmetic friendly-win record or guest dialogue, not generate resources, compulsory dominance, gambling or infinite Honor. Each pet returns to its actual supported location/owner state after the activity; save/abort safely releases the arena reservation. Indoor Menagerie lacks the Arena and cannot masquerade as outdoor via a style kit.

	**Guest-facing fantasy:** The room feels occupied even if the player is not interacting: cats seek high places and scratching spots, kittens approach scheduled food, dogs bark at real unfamiliar movement, pets investigate guest arrivals and retire to sleeping sites. Fidelity is tiered; the roster may hold 30, but the full set is **never a hard requirement for 30 simultaneous full-detail AI/animation actors**. No special estate-only AI overwrites individual temperament, ownership, health or the survival/memory system.

	### Trophy collection, Costume Room and exhibition

	**Trophy rooms and head mounts:** Authored trophy mounting hotspots in eligible trophy space accept genuine collectible **stuffed monster-head items** acquired through defined boss/monster encounters, legal trade or a later approved preparation recipe. The player may select a different **already owned** stored head from the wall's interaction menu, and the visible mount changes **immediately without demolition, Construction XP or room modification**. Swapping is one atomic change to display association and storage/fixture holder; the previous head remains recoverable. A gallery interaction enumerates the **entire physically owned trophy collection** from estate storage and other eligible registered possessions, with display previews and source/achievement info only where discovered. Guests may view via explicit estate viewing permissions but may not withdraw, swap, sell or steal using the gallery interface. Actual theft requires existing physical access, action and witness rules. Missing/destroyed/sold heads cease to be selectable.

	**Costume Room inventory fixtures:**

	| Fixture | Eligible collection | Interaction rule |
	|---|---|---|
	| **Toy Box** | Earned holiday/event novelty items. | Display catalog and store/withdraw original items; no event item is granted by constructing the box. |
	| **Treasure Chest** | Existing Treasure Trails and clue-scroll reward items. | Organize acquired loot and optionally show learned source information; never complete a clue or generate a reward by itself. |
	| **Jewelry Box** | Existing qualified necklaces, rings and other supported jewelry collectibles. | Categorized storage/preview; bracelet craft items are not automatically wearable equipment slots. |
	| **Cape Rack** | Achievement capes, earned level-100 skillcapes, and normal capes. | Display owned capes and transfer a selected cape into existing inventory/equipment only through standard validation. |
	| **Armor Case** | Real individual armor pieces grouped by an authored set definition. | Shows incomplete and complete sets, with set-visual previews; withdrawing or equipping checks actual piece ownership, capacity, equipment tiers and valid slots. |

	All five are **real category-restricted estate containers with independent instance IDs**, capacity and access checks. Presentation-only mannequin/preview meshes do not count as item copies. Browsing is free; storing, withdrawing and changing mounted content are atomic authoritative item transfers. A stored equipped item is **not simultaneously worn**. Removing a fixture or room requires safely emptying/transferring its contents first. Exact eligible content lists, room level and per-fixture capacities are Open.

	### Combat Room — sanctioned sparring and rapid combat practice

	**Room and setup:** A Combat Room is an optional Construction room with a fixed ring placement hotspot and authored safe entry/exit and spectator locations. Choose one installed **Boxing Ring**, **Fencing Ring** or **Combat Ring** per compatible hotspot; changing the actual fixture obeys Building Mode removal/rebuilding, not freeform relocation. **Boxing** permits authored unarmed sparring moves; **Fencing** supports approved hand-held weapon families and defensive actions; **Combat** permits the full set of supported physical weapon families, ranged equipment, Magic and Faith where the authored safety boundaries can be met. The Combat Ring is *unrestricted among legitimate combat styles*, not permission for explosions outside the ring, attacks on spectators or unsupported equipment. Exact legal moves, projectile containment and magic exception rules are Open.

	**Real opponents and permission:** Matches require a **present, consenting human NPC** who retains a valid one-of-100 subtype, can actually reach the estate, and has permission to act as a sparring opponent; sparring is an **event assignment**, not a new occupational family, a fabricated duplicate NPC or a loophole to summon quest characters. Actual fees, invitation/booking and willing-opponent supply are Open. A voluntary match is recognized as **lawful sport** inside the marked ring, but robbery, harm to nonparticipants or fighting outside its boundaries still use ordinary crime and Honor rules. NPC injuries/death cannot be hidden by declaring an illegal assault to be practice after the fact.

	**Combat resolution and defeat:** In-match strikes still use exact physical contact, active block/dodge/parry, deterministic Strength damage and real attack cadence; defenses and valid player actions award their ordinary eligible **Attack, Strength, Defense, Health, Archery, Magic and Faith XP** if justified under §2.2. Terminal HP is **nonlethally capped**, converting a would-be lethal blow into `SparringDefeat` instead of `Dead`. On player defeat, return to the **same estate's interior sparring respawn anchor**, with HP and necessary match state reset, **no ordinary death penalty, corpse, dropped/retrievable possessions, gravestone snapshot, quest death, bounty, or item-loss roll**. NPCs similarly yield/are safely reset or dismissed rather than dying. This is a **specific practice-mode exception** to ordinary death mechanics, not a rule for non-consensual combat elsewhere. The player's persistent Honor, ownership, items and story flags do not reset. Clear temporary ring targeting and protect actual inventories on interrupt/save/reload.

	**Fast but legitimate training:** House respawn, immediate availability of the next willing match and minimal travel/recovery downtime make repeated sanctioned bouts a **deliberately efficient combat-training route**. Award XP **only for real executed skill actions and meaningful damage/defense**, not entering matches, taking practice damage while idle, surrender spam, hitting an inert dummy, shooting scenery or repeated free restoration. Training reward amounts remain variable under the five-route XP design, and the opponent must genuinely be capable of acting and defending. Prevent duplicate per-hit commits, stalled invulnerable fights, scripted AFK exploitation and cross-room duplication; **do not impose arbitrary zero XP just because the ring is safe**. The regular Ornate Rejuvenation Pool remains a separate physical household utility, not a free automatic global Mana/Faith exploit.

	### House styles and Fishing Aquarium

	**Appearance kits:** Select and persist one compatible estate style theme—**Swamp** (wetland-inspired, mossy/weathered materials), **Civitas** (bright masonry, crisp trim, cultivated grounds) or **Wilderness** (rugged timber, natural roofing/terrain finishes). All themes are visual kits applied to existing room walls, ceilings, outer walls, roof pieces and garden/yard dressing via stable material/mesh variants. **Civitas** is an *art-style label*, not canon approval of a kingdom or lore name. Style changes do not shift the estate world position, delete contents, alter the fixed door graph, add utility hotspots, change room functions, reset cooldowns, affect movement collision, provide camouflage bonuses, or grant XP for repeatedly recoloring. A preview and cost/entitlement validation precede commit; purchase/recipe details Open. The transfer scene reuses the same interchangeable prefab skins.

	**Aquarium:** The buildable Aquarium room contains fixed eligible tank, egg-display and collection-record hotspots. Qualifying **genuine Fishing catches** may include a rare collectible **fish-egg item** where its existing supported fish definition permits an authored egg entry; egg acquisition is a one-time event outcome stored with the catch and subject to actual inventory capacity/ownership. The player can deposit authentic eggs for a visible species-associated display or a separately defined incubation/showcase outcome. Tanks maintain persistent display membership, care/appearance state and guest inspectability. Eggs are **collector items**, not an automatic source of unlimited fish, food, bait or new open-world aquatic AI agents. A collection book can track actual discoveries/acquisitions by supported fish definition, leaving unknown species hidden. Modest **Fishing perks** (e.g., an authored convenience modifier on eligible Fishing actions, or increased opportunity to find particular existing collectibles) may unlock at documented collection milestones; perks use bounded modifiers to the same Fishing action pipeline, with no passive XP, free catch or mandatory training boost. Exact fish categories/egg acquisition rates, perk catalog/unlocks, habitat care, aquarium recipes/level and storage limits are **Open**; do not invent a new fish-species list here.

	**Collecting is physical:** Aquarium eggs and fish displays, trophy heads, Toy Box novelties, Treasure Chest loot, capes, armor and jewelry occupy real persistent records. Inspect previews may show thumbnails or display meshes; they never generate duplicate tradeable items. Sale, theft, demolition, estate transfer and save/load update collection ownership and exhibition availability through one authoritative event.

	### Estate servants and autonomous household activity

	- **Hard eligibility:** The estate must contain **at least two completed, accessible Bedrooms** before the player can **hire or assign a servant**. Merely previewing, funding or placing a ghost Bedroom does not satisfy this condition. The necessary bedroom count remains reserved while a servant is assigned; deleting a required Bedroom must resolve the service assignment first.
	- **Real agents:** Servants are persistent **actual human NPCs**, not invisible, free inventory transfer or teleport effects. Each maintains its **one required concrete human subtype** from §3.3. "Servant" is a **paid/authorized estate employment assignment**, not an additional human role or blank subtype. Eligible mappings must be explicitly defined before service is enabled: for example an existing Porter may carry permitted household goods, while an appropriate repair worker may undertake approved maintenance; a worker cannot automatically perform every subtype's profession. Exact eligible subtypes and supported servant tasks **Open**. Clergy, Civic Services and Leatherworker subtypes may be considered for specific estate duties later; they are **not** automatically eligible as general servants.
	- **Work contract:** A hired actor has named duties, access and storage permissions, an actual location/schedule, and relevant wages/availability where applicable. Service can fail, pause or end through insufficient funds, invalid room access, interrupted employment, NPC death or other legitimate world events. One servant cannot work at two physically distant estates simultaneously.
	- **Player participation:** The player may observe staff using available room paths, provide resources, issue permitted requests, change permissions or dismiss a worker through validated interactions. A servant's work modifies **real stored goods, building condition or orders**; it does not grant free Construction XP to the player for work solely completed by the servant.

	**System connection:** Every utility and furnishing must interface with a real existing subsystem—Construction skill actions, market-sourced materials, Magic/Faith resource updates, NPC service tasks, original item storage, animal ownership/care, or nonlethal combat—rather than granting a detached menu reward. The shared causal examples in §3.13 are part of estate integration acceptance.

	### Save data, physical simulation and engineering contract

	**Estate-specific persistent records:** `EstatePetRegistry`, `EstateCollectionDisplay`, `EstateArenaMatch`, `AquariumCollection`, `EstateThemeSelection`, together with `EstatePlot` (stable estate ID, kingdom, listing/ownership, building footprint and entry); `EstateLayout` (room instance IDs, orientation/cells, graph edges/door occupancy, room definition); `EstateHotspot` (fixture anchor/category, actual installed entity, active/damaged state); `EstateStorage` (real held items/owner/capacity); `EstateTravel` (Nexus/amulet/Tree/Ring registrations and access); `EstateEmployment` (actual actor, assigned estate/rooms, contract and permissions). The new records must reference **existing unique animal, human NPC and item IDs** and must not duplicate their ownership or physical location. A sold/relocated/destroyed fixture's state must not survive as a second live copy.

	**Transactional acceptance rules:** (a) estate agent listing → payment → ownership/permission; (b) room placement → physical material consumption → hotspot occupancy → XP awarded once; (c) safe removal only after dependent-room/storage/installed-item checks; (d) storage deposits/withdrawals and amulet mounting are single-location item transfers; (e) spellbook choice, resource restoration, travel and Faith offering each have their own explicit once-only state commits; (f) NPC service assignment changes exactly one worker's authoritative employer/location; (g) pet care/feeding, roster admission, trophy/cape/armor/fish egg transfers, gallery swaps, theme selection, ring reservations and nonlethal practice-defeat events commit once with real ownership; (h) save/load, time skips and moving the same prefabs to a second scene preserve all of the above.

	**Devmap required sample — one genuine reusable estate, not nine fake test houses:** Estate Agent + one purchasable plot; Garden and Parlor; build the Crude Wooden Chair (**2 planks + 2 nails + hammer + saw**) and Garden (**10 iron bars + 10 soil**); one locked/occupied doorway test; fixed ghost furnishing hotspot; Kitchen/Dining/Workshop/Bedroom level checks at **5/10/15/20**; two-bedroom servant eligibility gate; a representative high-level restoration/travel/altar/storage fixture and a configured level-92 build check using an effective level of **84 + 8**. Include representative Menagerie dog/cat and kitten auto-feeding/scratching fixtures, outdoor play Arena, instant trophy-mount swap, a Costume Room storage fixture, one legal NPC combat-ring match with no-penalty defeat and genuine XP, Swamp/Civitas/Wilderness style swap, and a collectible fish-egg Aquarium fixture. Verify the full authored room/fixture registry can grow without reauthoring the room editor. **All installed representative estate rooms, meshes, materials, animation hooks and interactions are production-intent reusable assets**, in accordance with §4.2.

	**Open for balancing/content:** Menagerie room levels/recipes and 30-pet roster UX, pet food supply/feeder cadence/consent/guest permissions, habitat theme models, pet arena outcome/presentation, monster trophy acquisition/catalog, collector storage capacities and set tags, Boxing/Fencing/Combat Ring unlocks and eligible NPC opponents/fees, nonlethal match boundary and balancing, estate style-kit costs/mesh variants, Aquarium fish-egg eligibility/drop rules/Fishing perks, estate pricing, regional availability/plot specifics, resale, simultaneous ownership limits, expansion footprints/floors/room caps, Garden minimum level, room input recipes beyond the two specified costs, 92-tier boost source/duration/stack rules, higher-room unlocks and furnishing costs, ghost-hotspot variants/style options, demolition refund/salvage and occupant eviction, servant subtype eligibility/tasks/wages, estate access policy, storage caps, repair discount, quest/travel restrictions and Nexus/Tree/Ring/amulet destination registrations, teleport tablet recipes, altar offering/burner rules, and remaining utility models. Do not invent those numbers or content by analogy to another game.

	---

	## 3.12 Player death, corpse security, and recovery

	### Core death and location rules — established

	**Design intent.** A real death interrupts the player and exposes carried possessions to the living world. The player respawns **empty-handed at the nearest reachable, enabled graveyard**, retaining skills, earned levels, Honor, relationships, owned estates, stored property, and existing authored-quest consequences. Creator clothing can still render as cosmetic appearance but is not retained gear. The death site has **exactly two territorial classifications: INSIDE_CITY or OUTSIDE_CITY**. A PvH region is an **independent human-AI aggression/risk flag**, never a third death territory or a different corpse-recovery category.

	1. **Resolve the death boundary.** At the lethal, non-exempt commit, classify the player's actual physical location against the authoritative city boundary. Classify once for that death, then persist it: later moving the corpse, undertaker or player across a border does not change its rules. An urban-looking settlement or a player estate beyond a defined city boundary counts as **outside** until its boundary is explicitly registered; do not infer city security from nearby buildings, a Civitas-style house kit, or the jurisdiction of the nearest graveyard.
	2. **Transfer the originals.** Record unique death event ID, cause and actual killer, time, Honor snapshot and graveyard. Atomically empty **all** carried inventory, equipped items, backpack and ammunition, and carried currency represented by actual item stacks, into **one persistent corpse container**. Preserve each item's unique identity, quantities, owner/provenance and equipment condition. An eligible violent-death condition penalty is applied once to that same item, not to a duplicate. The player's equipment and normal inventory are empty after respawn. No fresh loot or currency is generated by dying.
	3. **Enforce the territorial security rule immediately.** **Inside city**, the original corpse is flagged **Guard-protected on death**; only a qualified Guard may physically collect it and route its **entire death-time inventory** into the city's Undertaker custody. The player, civilians, Thieves, visiting hostile NPCs and other actors cannot loot, seize or compete for this city corpse, even while a Guard is traveling to it or if the area is loaded/unloaded. Guard collection is an observable, scheduled task when near the player and a causally timed abstract task at distance. If no Guard is currently available, the protected claim waits safely; no timeout converts city gear into public loot. **Outside city**, the corpse remains physically exposed and its inventory is at risk from any human AI that actually encounters, learns of and reaches it, as well as the player and an eligible Undertaker retrieval team.
	4. **Respawn and present choice.** Respawn at the nearest **navigably reachable** graveyard (fall back to the next valid anchor), with no carried possessions or free recovery kit. The death interface displays whether the claim is **Guard-secured** or **Exposed**. **City:** the normal retrieval route is the Undertaker, with complete original gear guaranteed recoverable upon meeting the service's legitimate access/payment conditions. **Outside:** the player may race to the corpse and reclaim any remaining contents personally or commission an Undertaker to recover the remaining originals. The outside-city collection service adds an explicit surcharge. The last known death marker is not a live tracking beacon for stolen items.
	5. **Resolve the world honestly.** Actors continue to perceive, choose, travel, collect, protect, steal and trade according to actual capabilities and elapsed game time. A service claim, NPC or inventory never holds a second instance of an original item. Losing a corpse mesh to streaming never deletes its durable items. Repeated requests, reloads and time skips do not duplicate or reroll recovery.

	**The only two territorial outcomes:**

	| Death site | Who can acquire the body? | Player's return route | Recovery certainty |
	|---|---|---|---|
	| **Inside a city boundary** | **Guards only.** Claim becomes protected at death; Guards collect and deliver all original contents to an Undertaker. No opportunistic looting or killer-first claim is permitted, even during a delay. | **Undertaker**, normal fee. Guard custody is a recovery service, not a criminal seizure. | **All death-time equipment, inventory and carried money remain recoverable**; violent-death repair costs can still apply to eligible items, but no NPC steals city corpse items. |
	| **Outside every city boundary** | **Any physically eligible human NPC**, including the killer, passerby, Guard or Thief, can decide to search or collect the exposed remains; the player or commissioned Undertaker can reach the same source. Human motivations determine what they do. | Free physical self-recovery **or** Undertaker collection for base fee **plus outside-city surcharge**. | **Only items not already removed remain recoverable by either route**. Delaying can mean items are taken and later traded or otherwise lost. |

	**Priority rule:** City security beats PvH aggression, criminal intent and possession claims: a Bandit who kills the player inside city limits does **not** receive post-death corpse-looting permission. Outside, PvH danger can increase the chance of being pursued or killed, but it does not change access rights to an already exposed corpse. A body found just beyond the city border remains **outside-city** even if a Guard, graveyard, or Undertaker is very close.

	### Undertakers and Guard handling

	- **City recovery chain:** Guard discovers/receives a legitimate collection assignment → travels to the protected death scene → transfers the **entire intact original claim** into named Undertaker custody → player pays the ordinary quoted recovery charge and retrieves actual original items. City recovery is **guaranteed in content and quantity**, not necessarily instant or free. A Guard cannot selectively keep the player's possessions, add a theft roll, or convert this normal death claim into bounty evidence. Any unrelated lawful investigation is a separate system; it cannot silently remove gear from this guaranteed death-recovery claim.
	- **Outdoor recovery chain:** The nearest eligible Undertaker is determined by valid travel/service coverage. Accepting a collection contract is not teleportation, locking the corpse against everybody, or claiming items still at the death site. A real assigned worker, or time-consistent distant simulation, travels and secures **what is physically left at the moment of collection**. A thief reaching it first can take things; the player reaching it first can retrieve them. If an Undertaker arrives first, subsequent outdoor looters cannot take items that have already moved into secure custody. The quote itemizes base fee and outside-city surcharge; exact prices and expedition delays are Open.
	- **Access to funds:** Graveyard respawn grants no money. Undertaker redemption must use **actual accessible funds** through an authorized payment interaction. Collected goods may remain held while the player acquires legitimate payment; recovery is not silently free, and the claim is not destroyed because the player initially cannot afford it. No assumed remote bank account, advance loan or free item transfer.
	- **Existing NPC architecture:** Undertaker is a **service/office assignment**, not a new eighteenth role family. Qualified Guards and eligible Hauler/Porter assignments still require an existing valid concrete subtype, authentic location, route, permission, work time and compensation. A city's temporary lack of an available Guard must not break the guaranteed claim or create new NPCs; queue the protected job until an eligible worker can complete it, using a tested abstract resolution where appropriate.
	- **Physical burial:** Guards prioritize visible bodies in their assigned territory when safe, carry the actual body to an available registered graveyard, and replace it with a persistent individually named gravestone holding original possessions. Player bodies participate with their secured-at-death protection unchanged. A protected town claim never expires into public loot. An exposed grave becomes eligible for an actual Undertaker collection journey after ten minutes; expiry alone moves no items. Filled bags and their contents survive burial. Recovering a death claim never releases unrelated evidence from the shared criminal custody chest.
	- **Claim resolution:** Collection transactions move each original item exactly once from corpse → Guard custody (city) or collector (outside) → Undertaker claim → player, as applicable. If the player already removed an outdoor item or another actor stole it, it cannot appear in the collection inventory or final bill as recoverable property. A quoted price is not a completed transfer. Fees are charged only according to the disclosed, committed service terms; failed collection cannot bill for goods never secured unless a separately approved transparent service charge exists.

	### Outdoor opportunists, PvH hunting, and the wider simulation

	**Any human may assess an exposed outdoor corpse.** Corpse examination and possible pickup are **shared opportunistic human actions**, not a new profession and not reserved to Thief subtypes. The NPC must have a believable path to knowledge (direct sight, audible disturbance, a credible report, following the killer, or already being on-site), physical reach, free inventory slots/capacity, sufficient time and a motive. The system does **not** globally ping all NPCs or spawn corpse seekers when the player dies.

	- **Gravitating to valuables:** Pickpockets, Burglars, Highwaymen, Bandits and other characters already inclined toward theft or opportunistic gain assign **higher search, approach and looting priority** when they *notice or credibly learn* of an exposed corpse. They may share that information through existing dialogue/memory, encouraging further local arrivals. A seller or Fence may later accept the resulting goods subject to provenance/risk; an ordinary Merchant should not magically buy illegal stock. The attraction is an AI goal score, **not an area-wide loot magnet or guaranteed robbery countdown**.
	- **Other human choices:** Hunters, travelers, ordinary workers, Knights and Guards may inspect, ignore, guard, report, safeguard or take some possessions according to existing needs, ethics, duty, legal permissions and personal risk. A desperate Hobo may consider taking necessities as a last resort; a lawful passerby may leave everything or seek help. **Every human has potential physical access outside**, but **not every subtype has a standing goal to steal**, and one NPC never acquires another subtype's exclusive professional behavior. NPCs remain physically credible in far simulation.
	- **PvH is an aggression overlay only:** Authored *Player versus Human AI* areas allow qualified existing NPCs such as Bandits/Highwaymen to proactively stalk, intercept, rob and attack the player using actual perception, goals, route knowledge, combat ability and group tactics. A qualifying outdoor killer may choose to loot **some** real available items after the death; the same opportunity is also available outside PvH if a credible human killer is present. **No artificial hard cap or automatic percentage** is needed: carry capacity, urgency, risk, item utility and competition bound what is taken. Ordinary passersby do not gain predatory AI just by crossing a PvH boundary.
	- **Living consequences:** A looter's acquisition is an ordinary property transfer with persistent original ownership/stolen provenance. Recognizable theft may be witnessed and reported through actual Guard channels; private dishonor does not instantly produce a bounty. Looted gear may be carried, equipped only if valid, sold through permitted trade paths or recovered from its later holder. Regional stock and available purchases may change through those real transfers, not invented item generation. No service automatically traces an unknown thief or forces a stolen object to reappear.
	- **Animals and companions:** Horses, owned dogs and other animals can perceive a lethal struggle and act on their own fear, bonds or established species behavior. **Animals do not equip, store, or sell player gear** and are not substituted for human corpse looters. A dog might react to its owner or a threat; this does not imply an approved item-fetch or tracking command. The same live horse or other supported mount may flee to a viable location; it keeps its stable animal ID and original owner.

	### Physical loss, equipment condition, exceptions and player feedback

	**Violent death and repair:** Particularly forceful lethal impacts may apply a bounded penalty to **eligible degradable gear only**, committed exactly once to each item's real condition. No second damage roll is retroactively applied when deciding equipment wear and gear is not arbitrarily destroyed. A player who recovers such equipment may repair it through the existing Smithing/Crafting services or the discounted estate Workshop Armor Stand, spending actual permitted materials or funds. City custody guarantees the **items**, not free repairs. Exact qualifying causes, penalties and repair recipes are Open.

	**Mount separation:** A living mount is not corpse equipment. When the player dies nearby, the same owned horse may remain, bolt from danger or seek an established safer location according to current animal AI, temperament and situation; its last known position and actual destination survive save/reload. The player finds it through legitimate exploration and existing bonds. This rule does not invent mounted-combat controls or summon mechanics.

	**Visible status:** Show *City — secured for Undertaker* or *Outside City — exposed*, where applicable; known death-site location; remaining original goods actually stored in custody; item condition when inspected; collection/fee status; and uncertain outcomes for outdoor items no longer at the source. The interface must never expose an unknown looter's live position. A city claim must clearly promise **complete gear recovery**, subject to normal fee/access and elapsed collection time; outside-city messaging must stress that waiting can cost possessions.

	**Exceptions and narrative:** The estate Combat Room (§3.11) produces an expressly **nonlethal sparring defeat**, returning the player inside the same estate with no corpse, graveyard respawn, loot loss, violent-death repair or Undertaker charge. Similar authored nonlethal losses are distinct from `PlayerDeath`. Death-time Honor controls only the gravestone/memorial presentation (§3.5), not item recovery. Authored quest characters, murder evidence and lasting consequences follow their ordinary rules. Ironman/Hardcore exceptions and story-unique quest-item protections must be **explicitly designed**, not inferred here.

	**Persistent records:** `PlayerDeathRecord` stores stable death ID, cause/killer, **cityBoundaryId or OUTSIDE_CITY** (exactly one), death-time Honor, graveyard and time. `PlayerCorpseClaim` records the original container, **secured-at-death city flag**, unique original item IDs, actual holder, Guard/collector work reservation, Undertaker custody/release and payment status. `DeathMountState` references the original surviving animal and its live location. Keep crime evidence, NPC memories, marketplace offers, authored quest state and equipment condition in their existing authoritative records. Derived claim summaries cannot act as second inventories.

	**Transition sketch:**

	```text
	Actual lethal death -> classify death site exactly once
	  -> transfer all carried original items into one corpse -> respawn empty at graveyard
	  -> INSIDE_CITY: protected immediately -> Guard transport -> Undertaker
	                  -> full original inventory released upon ordinary valid payment
	  -> OUTSIDE_CITY: exposed -> player / eligible human looter / Undertaker
	                   race via real access and travel
	                   -> each actor transfers only items still in the corpse
	                   -> Undertaker releases its actual collection for fee + surcharge
	  -> living mount remains separate and may flee
	  -> estate sparring defeat never enters this pipeline
	```

	**Validation and open tuning:** Verify boundary edges, Guard-only city collectors and **zero city corpse losses**; outdoor looting by a credible non-Thief as well as a high-priority Thief; appropriate uninformed NPC nonresponse; delayed and interrupted collection; duplicate/depleted corpses; killer, player and Undertaker racing; provenance and resale; repair; pet/mount responses; fee/payment gating; and all stages across save/load, time skips and transferred world scenes. **Open:** retrieval costs, worker travel delays, payment methods, corpse persistence/decay behavior, outdoor looter preferences, PvH zone placement, repair severity, post-respawn HP/Mana/Faith, mount retrieval details, quest-critical item policy and explicitly authored restricted-profile exceptions. **No open setting may revoke the city gear guarantee, create a third territorial death category, or duplicate original items.**

	## 3.13 Cross-system causality and gameplay integration

	### One simulation, not a collection of disconnected features

	**Design requirement.** Any consequential action resolves once through §3.8, then propagates to every relevant system through **recorded state changes and typed domain events**. Interactivity means the player can **observe, take part in, interrupt, and later recognize consequences**, using real prerequisites rather than a scripted reaction vignette. Cross-system participation does not mean every event adjusts every meter. The table below defines the **required bridges** between existing systems; implementation may be distributed across components provided the same game-world facts are used.

	**Causal chain:** `Actor intent (player or permitted NPC) → physical/eligibility validation → single committed result → inventory/skills/resources/health/ownership → lawful knowledge and Honor → NPC goal/relationship updates → economy/ecology/quests/estate effects → visible feedback and persistent save`. A participant sees only facts it can observe or learn. Subsystems reacting to a committed event may schedule **new checked commands**, not silently perform transfers, grant XP, alter crimes or produce items just by receiving a notification. **An action's origin never exempts it from the rules**: player, NPC, commissioned service, abstracted distant agent and estate fixture all use the same authoritative contracts.

	### Required cross-system links

	| Source of genuine action | Connected systems and player-facing consequence | Hard gate / conservation rule |
	|---|---|---|
	| **Combat: Attack, Strength, Defense, Health, Archery** | Real contact, effective defense and damage generate eligible skill XP; injury changes survival decisions, Guard/NPC memories, Honor/crime when appropriate, item condition and possibly death → corpse custody/loot → repair demand. Combat level derives from skill levels and never overrides actual hit resolution. | No fake hit rolls, damage outside the armor-adjusted roll bounds or reward for whiff/idle spam. Faith-protected fully negated HP damage is **not** damage-taken Defense XP; a genuine performed defensive action can have its own qualified XP. |
	| **Magic and Mana** | Cast spell → consume real Mana / applicable Fire staff charges → alter only its authored physical, social, illumination, movement or combat state → affect stealth, navigation, NPC perception, eligible quest facts and Magic XP. Estate Occult Altar changes available book, not learned spells. | Four books × 11 slots; no invented spell, free cast, omniscient Charm/Disguise result, or physical contact conjured by elemental weakness. |
	| **Faith and prayers** | Active protection or combat boost changes a legitimate encounter's outcome; pool drain continues per active prayer; restored at authorized altars, wine, potions or estate pool. Faith XP comes from meaningful Faith practice, not an idle toggle loop. Gilded Altar uses actual eligible offerings and burners. | Protection negates the **tagged** attack's HP damage when valid; no simultaneous incompatible overheads, no natural Faith regeneration or bypass of resource cost. |
	| **Woodcutting, Mining and Harvesting** | Physical harvesting depletes a site → real XP and materials → legal ownership/access checks → NPC producer/Hauler work, market availability and Construction/Smithing/Crafting orders; forestry and animal habitat conditions may react to supported disturbance. | Tools, time, stock, applicable tier/skill gate and claim rights checked once; no free downstream resource or unscripted ecosystem destruction. |
	| **Fishing** | Valid catch changes the site and earns XP; fish becomes an item for Cooking/trade or other supported use. A genuine eligible catch may contain an Aquarium collectible egg; releasing a real fish can trigger eligible Honor and resource outcomes. | No passive Aquarium-generated catch, double counting an egg or creating fish by opening collection UI. |
	| **Cooking and Firemaking** | Gathering/buying ingredients and fuel → actual fire/stove and valid recipe → consumable food, Cooking/Firemaking XP where actions qualify → household supplies, NPC hunger, commerce and pet food stock. Real light/fire may affect stealth visibility and animal caution if implemented in the shared perception environment. | Food, fuel, cooking output, fire state and XP commit once; no skill XP for purchasing meals, idle flames or food that never successfully cooks. |
	| **Unified item identity, voxel assets and Gold prices** | One physical item’s authoritative definition governs the same voxel model in world/inventory/equipment/storage, its authentic value and source/recipe, and its custody through trade, theft, death and estate displays. Dragon/Godly scarcity, gem-cutting economics, potion consumption and luxury-armor demand feed actual NPC decisions. | §2.7 resolves integer reference prices for named items; region/condition/Honor affect only validated quotes, while stock, liquidity, provenance and visible geometry remain real. No mismatched 2D-only inventory asset, duplicate item copy or fixed infinite-vendor pricing. |
	| **Crafting and Smithing** | Actual metal/gems/leather/cloth → molds/workstations and recipe checks → real equipment, jewelry, tools or estate inputs → trade/quest/collection readiness. Damaged recovered equipment can create legitimate repair tasks, including discounted estate Armor Stand service. | Recipe/tool/anvil checks, material conservation, permitted tiers and no new bracelet slot or item duplication. |
	| **Construction and property** | Earned skill + legitimate boosts permit fixed-hotspot estate building → draw materials from real inventories and market supply → new functional rooms, travel, storage, utilities, Menagerie care, NPC employment and player skill work. | No free room layout relocation, material resurrection on demolition or NPC servant labor counted as player Construction XP. |
	| **Agility and Thieving/stealth** | Movement, cover, light, sound and skillful traversal affect whether the player can approach valuables or escape pursuit; successful theft shifts ownership, Honor and local NPC memory, then may trigger a report and a Guard response. | Stealth never deletes a witnessed event retroactively, creates free XP for repeated traversal or produces an automatic bounty without a valid report. |
	| **NPC subtypes, social relationships and orders** | Real need, skill, remembered information and occupational permission drive work and offers; player help/disruption changes the same job stock/order, wages, trader Attitude, friendships and future dialogue. NPC deaths can interrupt labor, food delivery, quests and property service. | Every social human keeps **one of 100 concrete subtypes**; service jobs (Undertaker/estate servant) are validated assignments, not new role families. No global NPC knowledge or fabricated jobs. |
	| **Animals, pets, livestock and hunters** | Breeding/hunger/predation affect living herd numbers and the supply available to Livestock Farmers, Hunters, Food Merchants and buyers; owners can react to injured/stolen animals, and observed animal care/cruelty can influence Honor. Dog/cat trust, Menagerie feeding and actual owned horses persist across travel and death. | 14 species and real parent/offspring counts; dogs are not wolf AI; pet arena play is noninjurious and not combat XP; no extra animal or item when changing simulation detail. |
	| **Commerce, logistics and employment** | Actual orders consume money/inputs, pay qualifying workers and move cargo; shortages, road risk and theft change NPC assignments, market prices and player preparation choices; Honor and trader Attitude can affect authorized purchase quotes. | One stock ledger, finite funds, real shipments and contractual access; buying/investing alone never generates skill XP or free stock. |
	| **Honor, law, factions and stealth knowledge** | Player acts morally → personal Honor updates; actual witnesses create private memories/evidence → credible reporting may produce a bounty → jurisdictional Guard search/de-escalation; recognized public Honor affects ambient dialogue and permitted merchant discounts. | Honor is not knowledge, rumor is not a completed Guard report, and a shop discount cannot override real stock, legal restrictions or cost floor. |
	| **Estates, travel and collections** | Estate fixtures consume owned goods to become useful; teleports, restoration, repair, storage and legal sparring shorten preparation without completing wilderness objectives. Mounted ornaments, stored gear, trophy heads, pets and fish eggs remain authentic entities with inspectable provenance. | Travel must check actual destination/access restrictions; no instant item summoning from a second estate, no duplicate mounted amulet, no ring combat death claim or endlessly paid XP for inert actions. |
	| **Death, PvH and mounts** | Hazardous combat can lead to a corpse: inside city → protected Guard delivery → **all original gear recoverable**; outside → NPC search/looting and Undertaker/player race; opportunists can later trade stolen items, affecting markets and legal evidence. Living horse remains independent and may flee. | Only two death territories. PvH changes **who may proactively hunt**, not body-access classification. Original items exist in one holder and only currently recoverable items are released outdoors. |
	| **Authored quests, nobility, faction/property rights** | Completed physical/social/economic facts satisfy authored conditions; credible deaths or scarcity can close genuine story paths. Valid title/office authority permits limited employment, trade or estate decisions; recognition and heraldry influence established social interactions when known. | No generated substitute major quests, no free office permissions from a title, no freely authored wars/succession, no retroactive restoration of removed prerequisites. |

	**Accessibility of consequences:** Actions that logically produce a consequence but are not currently relevant to a particular NPC, market or quest **do not force an immediate dramatic response**. Only existing nearby observers or valid delayed recipients update. A player should recognize effects through changed inventory, work routines, conversation, prices, collections, animal behavior, Guard conduct and quest journal where appropriate; otherwise the world remains quiet. Distant simulation commits the same events at lower detail and does not create fake actors or stealth witnesses.

	### Priority and interoperability rules

	1. **Physical action first.** Collision, reach, world access, resources and eligibility determine outcomes; skill levels and gear change permitted effectiveness, not whether a noncontacted weapon secretly hits. A successful defensive prayer is an explicitly authored counter, and protected HP damage cannot also be recorded as suffered damage.
	2. **One origin event, bounded reward distribution.** A real action can credit multiple genuinely exercised skills, Honor or quests **only through their respective validated event rules**. Exactly one primary action completion; no duplicate XP from its animation, loot transfer, market receipt, quest check or replay. Player XP is not granted for an NPC's independently completed assignment.
	3. **Separation of fact, belief and public standing.** World-state ownership and deed records know what occurred; NPCs act on perceived/communicated information; Guard cases follow actual reports; personal Honor is a separate moral score. One is never used as an omniscient shortcut for another.
	4. **Ownership survives interface changes.** Equipping, storing, displaying, selling, dying, looting, mounting amulets, Aquarium deposition and transferring across scenes relocate or reference a unique original item. *Preview* is not *inventory*. A civilian NPC corpse's Honor-biased supplemental loot is a separately allocated one-time item event; **player death generates none**.
	5. **Existing definitions, no implicit expansions.** New connections reuse approved skills, weapon families, 14 species, 100 human NPC subtypes, rooms, spells, prayers and item families. An interaction reference does not silently approve a new profession, creature, player ability, spell, item slot, teleport or economic resource.
	6. **Progression and work remain earned.** Estate comfort, high Honor, finite funds and noble titles can grant legitimate convenience, access and relationships, never a shortcut past equipment skill requirements or the five natural XP routes. Difficulty does not scale to player level, and content remains authored.
	7. **Saves are full-world facts.** Every long-running claim, population change, offer, theft report, animal birth, NPC death, estate task, prayer interval and corpse collection either persists or is derived deterministically from persisted inputs. Streaming, time skipping, retrying an input and migrating the same prefabs to GAMEWORLD may change visual resolution, **not the result**.

	### Connected end-to-end acceptance scenarios

	**A — Farm-to-estate economy.** An existing Livestock Farmer feeds an owned animal from actual stock and later records a valid animal birth. A Food Merchant or authorized buyer purchases real produce; the player supplies materials through a Hauler/other valid trade path to build an estate room, gaining Construction XP only from the player's own valid build. Removing feed, stealing a newborn, or killing an animal changes the herd, ownership, witnesses/Honor where justified, work priorities, and future supply. Run one segment off-screen; totals must match.

	**B — Forest resource, craft, and crime.** The player uses a tier-valid Axe to cut a permitted tree, earns Woodcutting XP and transfers logs to a real holder. Craftsmen can consume the stock for an existing job, or the player can lawfully build furniture. If the same resource belongs to another owner, taking it can reduce stock, create theft provenance and—only if seen and reported—a Guard case. The same log can never be sold, burned and installed three times.

	**C — Spell, shadow, theft, and pursuit.** Cast an already named obscuration/disguise spell with actual Mana; determine illumination and observer perception with §3.10, let an existing Thief/Guard/merchant witness or miss a supported theft, and update Honor independently. A later report creates a bounty only when credible; change local attitude/available trade legitimately. Time advance and save/load must preserve which observer knew what and whether a report occurred.

	**D — Caught between city and outdoors.** Kill the player one step inside a city boundary while a Thief and a Bandit are close: only a Guard can collect; the entire death-time inventory reaches the Undertaker despite a delayed route. Repeat one step outside: both human opportunists may consider the corpse through their own motives; if one takes the sword, the Undertaker returns only what remains for base fee plus surcharge. Mark the outside area as PvH and repeat: stalking behavior can change **before** death, but the identical outdoor loot/claim rules still apply. Include an uninformed NPC who never approaches.

	**E — Consequences of a violent road encounter.** A player fights a credible Bandit outside the city using real weapon collision and eligible prayers; true HP damage, protection events, XP, Honor and NPC memory resolve without duplicate rewards. On death, equipment condition may degrade and the owned horse may flee. An actual looter can later offer stolen gear to a Fence; normal merchants check provenance. Player recovery, repair, market changes and credible Guard reporting all reference the same items and facts.

	**F — Home as preparation hub, not event reset.** Retrieve recovered equipment, repair an eligible piece through the Workshop, restore HP/Faith via the Ornate Pool, change active Magic book at Occult Altar, use a paid-for registered travel fixture, display a real trophy, and undertake one legal nonlethal Combat Room match. Verify real material costs, permitted spellbook, XP from successful actions, no new corpse on sparring defeat, no copied items across storage and equipment, and no global reset of wanted status, NPC memory or wilderness population.

	**G — Wildlife, Fishing and the living household.** Catch an eligible fish for variable Fishing XP; either retain it as a real ingredient/commodity or release it for any authored Honor consequence, not both. An eligible authentic egg is stored in the Aquarium without creating another fish. Feed an owned kitten from Menagerie supplies, allow the cat to scratch a post and an actually present NPC to respond. Save away from the estate, advance time and return; the same pet and supplies remain consistent.

	**H — A meaningful authored story dependency.** An approved authored quest depends on a named NPC, a delivery and a piece of existing equipment. During legitimate world simulation the NPC may die or the delivery may be intercepted; journal state, household/job availability, stock and personal relationships update once. Neither high Honor, a noble title nor a player death/respawn invents replacement quest content or repairs its prerequisites unless that alternative is explicitly authored.

	**Gate for production acceptance:** Every demonstration must expose the causation chain in a developer inspector: original actor/command, validated preconditions, entity IDs, item/source ownership, committed event IDs, knowledge recipients, relevant XP/Honor deltas, dependent agent orders, and final world state. QA must replay each at close range, abstract distance, after a save/load, and in the migrated second scene without divergent inventories, corpses, NPC occupations, legal knowledge, economy or quest outcomes.

	## 3.14 MONSTER AI & BEHAVIOUR

		Core Philosophy
			Monsters are not enemies waiting to be killed. They are residents of the world, same as Humans and Animals
			Every monster has a life before the player arrives: a den, a routine, a hunger, a reason to be where it is
			Monsters are instinct-driven, not goal-driven. They do not "want to kill the player", they want to eat, defend, nest, dominate, survive
			Player is just one more thing in the ecosystem: prey, threat, intruder or irrelevant, depending on the monster and the moment
			Same simulation rules as Humans & Animals (needs, routines, senses, memory) with a harsher, more territorial weighting
			Humans reason, Animals react, Monsters defend & dominate. Where Humans have schedules and Animals have habits, Monsters have territories
			No monster is permanently hostile. Hostility is a state, not a label. Distance, noise, hunger, numbers and fear all move it
			The world should be able to kill the player without the player ever being the target (a troll fighting a dragon, a goblin camp wiped out by orcs)
			Readable first, fair second: players should be able to learn a monster by watching it, not by dying to it

		Instinct Drives
			Every monster carries a small set of live meters that decide what it does next. Highest weighted drive wins
			Hunger: Rises over time. Low = ignores weak targets. High = hunts, scavenges, raids, takes risks
			Fear: Spikes from fire, injury, outnumbering, a stronger predator, loud noise or a fallen packmate. Decays slowly
			Aggression: Baseline per species. Raised by hunger, territory breach, wounded allies, provocation, night / full moon
			Territory: How strongly it feels "this is mine". Highest at the den, lowest at the edge of its range
			Fatigue: Rises while active. Forces rest, retreat to den, or sluggish combat (slower tells, slower recovery)
			Social: Pull toward its group. Low social = solitary. High social = will not leave pack, will not fight alone
			Curiosity: Chance to investigate unknown sights / sounds instead of ignoring or fleeing. High on Imps, Bats. Near zero on Trolls, Zombies
			Drives are tuned per species, then nudged per individual (±15%) so no two goblins behave identically

		Territory System
			Lair / Den: The anchor point. Camp, cave, crypt, nest, roost, bridge-underside, ruin. Monster returns here to rest, heal, feed, breed
			Core Zone (0-15 blocks from lair): Maximum aggression. Will not flee unless near-dead. Will chase to the edge of the Home Range
			Home Range: Patrol & forage area. Size scales with monster size and diet (Goblin ~40 blocks, Troll ~100, Dragon 500+)
			Border: Soft edge of the range. Monster will stop chasing here and return, unless hunger / rage is high
			Territory Marks: Visible in world so players can read danger before it finds them
				Goblins: Totems, bone fences, skull stakes, war banners
				Orcs: Palisades, war paint, impaled trophies, burned ground
				Trolls: Gnawed bones, stripped trees, boulder piles, claw marks
				Imps: Scorch marks, stolen shiny things hung in nests
				Bats: Guano, squeaking, hanging bodies in the rafters
				Ghosts: Cold spots, flickering lights, whispers, objects out of place
				Zombies / Skeletons: Graves dug up, shambling trails, scattered bones
				Werewolves: Claw-scarred trees, drained livestock, howling at night
				Dragons: Scorched/frozen land, melted stone, bones of large prey, hoard glow
				Giant Spiders: Webbing, cocooned prey, silk trip lines
			Territory Contests: Rival monsters fight over borders (goblins vs orcs, trolls vs werewolves). Winners expand, losers retreat or die. The map slowly changes
			Territory Vacuum: If a lair is cleared, the area is empty for a long time. Then something else moves in (not always the same species)

		Behaviour States
			Dormant: Asleep or inert at lair. Reduced senses, wakes to noise, smell or proximity. (Skeletons / Zombies idle, Ghosts dim)
			Idle / Routine: Species-specific lair activity. Goblins tend fires, orcs sharpen weapons, bats cling, trolls gnaw bones
			Patrol / Forage: Moves through Home Range along learned routes. Hunts animals, scavenges, checks borders
			Alert: Heard / smelled / half-saw something. Stops, faces source, calls out to packmates
			Investigate: Moves toward the disturbance cautiously. Curious species approach, cowardly species send a scout
			Stalk: Predators only. Shadows target, uses cover, waits for isolation or vulnerability
			Engage: Full combat. Uses species tactics, calls allies, sets target priority
			Flee: Fear exceeds Aggression. Runs to lair, packmates or cover. Will return when fear decays
			Regroup: Pack monsters fall back, rally, then re-engage with new formation
			Feed: Consumes kill / carcass. Vulnerable, low awareness, defensive of food. A big opportunity (and risk) for the player
			Rest: Heals, restores fatigue. Regeneration species heal fastest here
			Return: Disengages and walks back to lair when target is lost, leash broken or hunger is satisfied
			Transitions are driven by drives + senses, not scripts. Designers tune weights, not hard-coded sequences

		Senses & Perception
			Sight: Cone + range per species. Reduced in low light for day-creatures, improved for night creatures. Blocked by terrain & foliage
			Hearing: Footsteps, combat, mining, doors, dropped items, shouts. Sprinting & heavy armour are louder. Sneaking reduces radius
			Smell: Predators track blood, fresh kills, carried meat, wounded players. Wind direction matters. Werewolves, Trolls, Spiders strongest
			Vibration: Giant Spiders feel movement through their webs. Dragons feel footsteps in their territory
			Light: Fire, torches and spells attract or repel depending on species (Bats scatter, Imps are drawn, Ghosts flicker, Zombies ignore)
			Magic Sense: Ghosts, Imps, Dragons notice spellcasting, enchanted items and runes from a distance
			Memory: Monsters remember where they last saw / heard a threat, who hurt them, and where they were hurt. Short for Zombies, long for Dragons, Trolls and Werewolves
			Awareness States: Unaware > Suspicious > Alerted > Hunting. Player can see this in body language & sound (growl, sniff, head-turn, call)

		Threat, Aggro & Leashing
			Threat Score: Each monster tracks nearby entities (Players, Humans, Animals, other Monsters) and ranks by Threat = Proximity + Noise + Damage Dealt + Visible Weapon / Armour + Territory Intrusion
			Opportunism: Hungry monsters target the weakest / most isolated. Cautious monsters avoid anything that looks stronger
			Disengage: Monster gives up if target breaks line of sight, leaves Home Range, hides well, or fear > aggression for long enough
			Leash: Not a hard invisible wall. A drift toward the lair that gets stronger with distance. Enraged monsters stretch it
			Group Aggro: Hitting one pack monster alerts those within call range. Calls can be interrupted (kill the shaman / drummer / caller first)
			No Free Aggro: Monsters never "snap" to the player across the map. They need a sense, a reason, and a path

		Daily & World Cycles
			Day / Night: Goblins, Orcs, Trolls active by day, more aggressive at night. Bats, Imps, Werewolves, Ghosts, Zombies strongest at night. Dragons ignore the clock
			Moon Phases: Werewolves scale with the moon. Full moon = bolder, bigger packs, wider roaming. New moon = hiding
			Weather: Rain muffles sound, masks scent, and drives many monsters to shelter. Storms drive Bats & Imps to hunt. Cold slows reptilian types
			Seasons: Hunger pressure rises in winter, raids increase, monsters migrate toward settlements. Spring = nesting, aggressive young
			Events: Monsters respond to world events, a battle draws scavengers, a burning village draws Imps, an eclipse stirs the dead

		Ecology & Interaction
			Monsters vs Animals: Predator monsters hunt wildlife. Wildlife flees or goes quiet near monster territory. The absence of birdsong is a warning
			Monsters vs Humans: Raid, ambush, scavenge, haunt. Humans fortify, hire guards, hunt bounties, avoid certain roads. Settlements near lairs feel it
			Monsters vs Monsters: Food chain & rivalries. Dragons dominate. Trolls bully Goblins. Werewolves prey on everything. Undead ignore most living things unless they are close
			Corpses & Carrion: Dead things attract scavengers (Bats, Goblins, Spiders). Player kills have consequences for the local balance
			Allies of Convenience: Goblins and Orcs may share a camp. Skeletons guard for a necromancer. Imps serve a stronger master. Some bonds are ordered, some are accidental
			Player Impact: Clearing a lair shifts the ecology. Killing the apex lets weaker packs spread. The player is a variable the world reacts to

		Spawning & Population
			No timer respawns. Populations are simulated, not summoned
			Breeding: Living monsters breed at lairs when fed and safe. A thriving lair slowly grows, a starved one shrinks
			Migration: Hungry or displaced groups move. New lairs appear. Old lairs die out
			Recolonisation: Cleared lairs are reclaimed after a long delay, by whichever species is expanding nearby
			Undead Sources: Zombies / Skeletons / Ghosts arise from graves, battlefields, curses, necromancers, cursed ground. Remove the source or they return
			Population Caps: Per region and per species. Apex monsters are unique or near-unique (a handful of dragons per world)
			Offscreen Simulation: Distant territories tick on a low-fidelity abstract model (hunger, raids, growth, deaths). Full AI only wakes near players

		Combat AI Rules
			Telegraphs: Every strong attack has a visible, audible wind-up. Heavier monsters = longer, clearer tells
			Roles: Pack monsters are assigned roles on engage (front, flanker, ranged, caller, bait). Roles reassign when a member dies
			Morale: Groups carry a shared morale value. Leader death, heavy losses, fire or fear effects lower it. Broken groups flee or surrender
			Target Switching: Monsters switch targets for good reasons only (lower HP, wounded, shot from behind, more threatening) with a cooldown to avoid ping-ponging
			Environmental Use: Monsters use doors, chokepoints, high ground, light and darkness, and can be fooled by decoys, noise and bait
			Pathfinding Rules: Ground units respect block pathing (jump 1 block, no unrealistic climbing). Flyers use 3D volumes. Spiders use surfaces. Large monsters avoid tight spaces and break soft blocks
			Fair Fights: Monsters are not omniscient. They lose track, get confused by invisibility / darkness, and miss on thrown attacks at range
			Wounded Behaviour: Below ~25% HP most monsters flee, turtle, or go berserk, by species. Wounded predators are dangerous
			Cheese Protection: Repeated exploit patterns (pillar-poling, door-stuck) cause monsters to adapt, retreat out of range, or call help

		AI Archetypes
			Pack Raiders: Goblins, Orcs. Tribal, social, morale-driven, camp-based, raid & scavenge
			Solitary Brutes: Trolls. Territorial, slow-witted, high durability, big hits, ambush from lairs
			Harassers: Imps, Bats. Fast, evasive, hit and run, irritating, hard to pin down
			Haunters: Ghosts. Bound to a place or memory, routine-driven, unsettling, ignore the living until disturbed
			Bound Dead: Zombies, Skeletons. Hordes or guardians, mindless, relentless, drawn to sound or ordered to hold ground
			Apex Hunters: Werewolves. Cunning, cyclical, patient, stalk before they strike
			Apex Sovereigns: Dragons. Rulers of the region, everything else reacts to them
			Ambush Predators: Giant Spiders. Static, trap-based, patient, strike from cover

	Goblins
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
		AI;
			Archetype: Pack Raider (tribal scavengers)
			Territory: Camp (huts, fire pit, totem, loot pile) in caves, ruins, forest hollows. Home Range ~40 blocks. Camps cluster into tribes
			Instincts: High Social, High Hunger, Low Fear threshold alone, Fear drops sharply in numbers. Cowardly alone, vicious in a mob
			Active Time: Day & night. More aggressive at night, raids on settlements and farms after dark
			Senses: Average sight, good hearing, poor in bright light. Notices loot, shiny gear and unattended food from far away
			Routine: Cooking, gambling, bickering, sharpening, guarding. Patrols in pairs, scouts in threes. Scavenge battlefields and corpses
			Social Behaviour: Lead goblin (Boss / Champion) sets tone. Shaman buffs & calls reinforcements. Drummers / horn-blowers alert the camp
			Combat AI: Swarms and surrounds. Spear goblins hold the front, knife goblins flank, throwers stay back. Lights traps (tripwires, pits) near camp
			Morale: Break and scatter when the boss falls or the group drops under half. Will drop loot and run, regroup later at a different camp
			Reactions: Steal from the player and flee. Flee from fire, ignore small animals. Fight Imps, flee from Orcs and Trolls
			Dynamic: Tribes grow with food, then raid. Starving tribes attack settlements. Wiped-out camps are retaken by Orcs or Spiders

	Orcs
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
		AI;
			Archetype: Pack Raider (organised warbands)
			Territory: Fortified strongholds, palisades, watchtowers, mining camps. Home Range ~80 blocks. Clans claim valleys and roads
			Instincts: High Aggression, High Territory, Moderate Social, Pride-driven. Low Fear, rarely flee unless the chieftain falls
			Active Time: Day & night. Disciplined watch rotations
			Senses: Good sight and hearing. Tracks by scent. Reads banners and armour, recognises threats
			Routine: Drilling, forging, feasting, sparring, patrolling borders. Warbands leave the stronghold to hunt, raid, or push into new land
			Social Behaviour: Strict hierarchy. Chieftain > Warboss > Warriors > Scouts. Orders pass down. Lower orcs challenge for rank. Duels over honour
			Combat AI: Coordinated. Shield-bearers advance, archers / throwers cover, berserkers charge, war-shouter stuns or debuffs the group. Uses chokepoints and fortifications
			Morale: High. Leader death causes infighting before retreat. Defeated clans scatter into raiders
			Reactions: Respect strength. Challenge strong-looking players, ignore weak ones unless hungry. Attack Goblins and humans in disputed land. Compete with Trolls
			Dynamic: Strongholds expand, raid roads and villages, take captives and loot. Defeated chieftains are replaced by the strongest warrior

	Trolls
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
		AI;
			Archetype: Solitary Brute (territorial ambusher)
			Territory: Caves, under bridges, ruined towers, mountain passes, rivers. Strongly claims choke points and crossings. Home Range ~100 blocks. Rarely lives in groups, small family groups at most
			Instincts: Very High Territory, High Hunger, Low Curiosity, Low Fear. Slow to anger, impossible to calm
			Active Time: Mostly night and dusk. Sleeps in lair by day, but wakes easily
			Senses: Poor sight, excellent smell and hearing. Tracks blood and cooked food. Feels footsteps through the ground
			Routine: Gnawing bones, hoarding, stacking boulders, scratching, sleeping. Wanders short loops around lair, hunts when hungry. Often camps in tolls or dens
			Combat AI: Charges, smashes, sweeps. Throws boulders at range or when the target is out of reach. Smashes through obstacles and soft blocks. Poor at turning, easy to kite but terrible to get close to
			Regeneration: Heals fast out of combat and while resting. Fire and acid halt regeneration
			Morale: Does not flee unless near death. Flees to lair to regenerate, then returns angrier. Enraged below 30% HP
			Reactions: Bully Goblins, steal from Orcs, fear Dragons, fight Werewolves. Ignore small animals. Demand tolls from travellers in myth, and attack those who do not pay in practice
			Dynamic: Pushes settlements away from key roads and bridges. A troll on a crossing can cut trade for weeks

	Imps
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
		AI;
			Archetype: Harasser (mischievous trickster)
			Territory: Loose. Ruins, chimneys, rooftops, scorched ground, ritual sites, near fire. Home Range ~60 blocks. Small groups (2-6), or a single imp bound to a master
			Instincts: High Curiosity, High Aggression (small), High Fear. Attracted to shiny things, fire, magic. Bored easily
			Active Time: Dusk and night. Less active in rain
			Senses: Sharp sight, good magic sense. Detects casters, enchanted items and flames from far away
			Routine: Flitting between perches, stealing, playing with fire, teasing animals. Collects trinkets in a nest
			Combat AI: Hit-and-run. Fly in, scratch, fly out. Firebolt from distance, keep moving, never stay still. Break line of sight constantly. Group imps swarm from different angles
			Special Behaviour: Steal small items from the player or NPCs and flee. Dropped loot is recoverable by killing it or finding its nest
			Morale: Flee at low HP, fly high, return later. Will ignore players with no valuables
			Reactions: Torment Goblins, fear Dragons, serve stronger demonic or magical masters. Ignite hay, thatch and torches for fun
			Dynamic: Cause small chaotic events in settlements (fires, stolen goods, scared animals). A bound imp may act as a mage’s familiar, with its own routine

	Bats
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
		AI;
			Archetype: Harasser (colony swarmer)
			Territory: Caves, ruins, belltowers, old mines, crypts. Roosts in colonies of 5-40. Home Range ~80 blocks (hunting flights at night)
			Instincts: High Social, High Fear, Light sensitivity. Skittish, not brave. Defends roost aggressively
			Active Time: Strictly nocturnal. Roosting by day, hanging motionless
			Senses: Echolocation (reads shape of space, sees through darkness, detects movement). Poor in bright light. Disrupted by loud sound (bells, shouts, thunder)
			Routine: Roost > leave at dusk in a stream > feed on insects, livestock, small animals > return at dawn. Colonies are seen and heard before they are met
			Combat AI: Erratic flight. Dive, bite, retreat, circle. Swarm in groups to overwhelm, individually weak. Wing buffet to disrupt aim. Screech to alert the colony
			Reactions: Scatter from fire, torches and loud noises. Disturbing a roost sends the entire colony out at once. Avoid stronger fliers
			Variants (by level): Cave bat (low), Giant bat (mid), Vampiric bat (high, drains, linked to undead lairs)
			Dynamic: Bat flights show where caves are. A sudden mass exodus warns of a disturbance (dragon, earthquake, undead)

	Ghosts
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
		AI;
			Archetype: Haunter (bound memory)
			Territory: Anchored to a specific place or object, place of death, grave, ruin, manor, battlefield, shipwreck. Home Range is tight (~20-30 blocks). Rarely leaves
			Instincts: No hunger. Driven by Grievance, Obsession and Disturbance. Low Fear. Very High Territory. Does not tire
			Active Time: Strongest at night, dusk, fog and storm. Faint by day but never gone
			Senses: Sees the living regardless of light. Strong magic sense. Reacts to emotion (fear, grief, violence) and to items tied to its past
			Routine: Replays a short loop of its former life (walks a corridor, rocks a cradle, mourns at a window). Players can observe, interrupt or follow the loop
			Behaviour Layers: Unaware (loop only) > Disturbed (cold, flicker, whispers) > Hostile (attacks) > Enraged (poltergeist effects, doors, objects). Escalates the longer the player remains
			Combat AI: Drifts through walls & terrain, phases between visible / invisible. Drains warmth and life. Spectral bolts from range. Harder to hit while phasing
			Resolution: Some can be put to rest by returning an item, finishing a task, burying remains. Others are only banished, and return
			Reactions: Ignore most animals, repel living creatures, are feared by Goblins and Imps. Undead are indifferent
			Dynamic: A haunted place affects the local population (people avoid it, animals flee, crops fail). Cleansed haunts recover over time

	Zombies
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
		AI;
			Archetype: Bound Dead (relentless horde)
			Territory: Graveyards, crypts, battlefields, plague villages, swamps. No true lair. Loose Home Range (~50 blocks) around the source of their rising. Hordes wander once triggered
			Instincts: No Fear, no Fatigue, no Hunger in the living sense. Driven by proximity to living things and sound. Zero Curiosity, zero Social planning, they cluster without coordinating
			Active Time: Always. More numerous and mobile at night
			Senses: Poor sight, strong hearing, good smell for living flesh and blood. Ignores light. Draws toward noise, fighting and fires
			Routine: Shamble in place, sway, sit, wander slowly. Drift toward distant sounds. Idle zombies are easy to bypass, alerted ones are not
			Combat AI: Direct pursuit, slow but unstoppable. Do not dodge, do not retreat. Grab and bite when close. Crowd and block doorways. Break weak doors and barricades through numbers
			Horde Behaviour: Noise draws more. A fight at a village gate can pull in zombies from far around. Players can lure hordes away or into other monsters
			Weakness: Easy to kite, bad at stairs and obstacles, slow turn. Fire, blunt and head damage are effective. Light does not help
			Reactions: Attack the living on sight, ignore other undead, attack monsters and animals if closer than the player
			Dynamic: Source-driven. A fresh battle, plague or curse raises more. Burning, blessing or destroying the source stops it

	Skeletons
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
		AI;
			Archetype: Bound Dead (sentinel guardians)
			Territory: Tombs, crypts, barrow mounds, dungeons, ancient fortresses, ruined roads. Bound to the site they guard. Strict Home Range (~25 blocks), they do not chase far
			Instincts: No Fear, no Hunger, no Fatigue. Driven by Duty / Binding. High Territory, low Aggression until trespass. Remember a fixed set of orders
			Active Time: Always. Dormant until triggered, then immediate
			Senses: Sight and hearing of what they were in life. Notice trespass, sound and light. Detect the living and the unliving differently
			Routine: Stand watch, patrol a short loop, sit among bones, rise from the floor when intruders step into trigger zones. Idle skeletons appear to be part of the decor
			Combat AI: Soldier-like. Hold position, form lines, archers stay behind. Disciplined, reactive, not clever. Break formation if the binding is cut. Ranged skeletons keep distance and retreat when closed on
			Special Behaviour: Reassemble after death unless bones are scattered, burned or the binding is broken. Champions command lesser skeletons and lead patrols
			Weakness: Blunt weapons, holy / fire damage, ambush from behind, cutting the controller or binding object
			Reactions: Attack the living who enter, leave those who leave, ignore animals. Serve necromancers and cursed sites
			Dynamic: Guarded sites reset when the player leaves and returns. The strongest guardians protect the best loot

	Werewolves
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
		AI;
			Archetype: Apex Hunter (cunning stalker)
			Territory: Dense forest, hills, caves, abandoned farms. Solitary or small packs of 2-5, with an alpha. Home Range ~120 blocks. Hunt in wide loops
			Instincts: High Hunger, High Aggression, High Curiosity (about prey), Medium Fear (will not fight losing odds). Patient and intelligent
			Active Time: Night, especially under a full moon. By day, hides in the den, sluggish and wary
			Moon Scaling: New moon = reduced and cautious. Waxing = more active. Full moon = peak aggression, speed, numbers and range, will enter settlements
			Senses: Excellent smell (tracks blood, wounds and old trails), excellent hearing, good night sight. Learns the player’s routes and habits
			Routine: Den > hunt at dusk > stalk prey > feed > return before dawn. Marks territory with howls and kills. Will remember and follow a wounded player
			Combat AI: Stalks first. Circles at distance, tests defences, strikes at isolation, wounded or sleeping targets. Pounces from cover. Retreats if hurt, returns when the target is weak. Pack wolves flank while the alpha attacks. Howling calls others in range
			Weakness: Silver, fire, light, bright open ground, group of players. Wounded werewolves become cautious and then vicious
			Reactions: Prey on livestock, humans, animals and Goblins. Fight Trolls and Orcs over territory. Avoid Dragons. Wolves and wild dogs follow them
			Dynamic: Appears in rumours first (missing livestock, torn carcasses, tracks). Villages react (curfew, silver, guards). A werewolf can become a recurring, personal threat to the player

	Dragons
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
		AI;
			Archetype: Apex Sovereign (regional ruler)
			Territory: Huge. Mountain peaks, volcanic ridges, glacial valleys, ancient ruins, deep caverns. Home Range 500+ blocks, and an influence zone far beyond. Lair holds a hoard. Typically one dragon per region
			Instincts: Very High Territory, Very High Pride, High Hunger (large kills), Low Fear (high only at low HP), High Intelligence. Not mindless, will assess, wait and negotiate with its own priorities
			Active Time: Day and night. Sleeps for long cycles in its lair. Hunts periodically, patrols the borders, circles high above
			Senses: Sharp sight at long range, strong smell, vibration through the ground, magic sense. Notices anyone who enters its range. Long memory, remembers faces, armour and wrongs
			Routine: Sleep on hoard > leave to hunt (livestock, large game, caravans, monsters) > feed > patrol territory > return > preen and arrange hoard. Dragons are seen in the sky before they are met on the ground
			Awareness Layers: Unaware (asleep) > Aware (watches from distance) > Warning (roars, circles, scorched ground) > Hostile (attacks) > Wrathful (below 40% HP)
			Combat Phases: 
				Aerial: Circles, strafes with breath, dives with claws. Hard to hit unless it lands or is forced down (ranged weapons, ballistae, spells)
				Ground: Bites, claws, tail sweeps, wing buffets, stomps. Very dangerous at close range, breath in a cone
				Enraged: Faster, louder, more breath, takes risks, may take off or flee to lair to recover
			Weakness: Wing joints, underbelly, eyes, tail, being forced to land, elemental opposites (frost dragon vs fire, and so on). Mounted weapons and traps
			Dragon Types: Fire (volcanic, aggressive, scorched earth), Frost (glacial, patient, freezes land), Magic (ancient, intelligent, spell breath, rare)
			Reactions: Dominates all other monsters. Trolls, Orcs and Werewolves avoid or flee its territory. Settlements pay tribute, abandon land, or hire dragonslayers. Will occasionally destroy a fort or village
			Dynamic: A dragon changes the shape of a region (burned forests, abandoned roads, fleeing monsters). Slaying one triggers a power vacuum and a scramble for the hoard and territory

	Giant Spiders
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
		AI;
			Archetype: Ambush Predator (web trapper)
			Territory: Caves, dense forest canopy, ruins, mines, dark hollows. Builds web networks across the area. Nest holds eggs and cocoons. Home Range ~50 blocks. Broods led by a Queen in high-level nests
			Instincts: High Hunger, High Patience, High Territory around the nest. Low Curiosity, Low Social (except broods), low Fear
			Active Time: Mostly night, but ambushes anything that touches the web at any hour
			Senses: Poor sight, extremely sensitive to vibration. Reads the web like a nervous system and knows where prey is, how big it is and how fast it moves
			Routine: Wait in the web > feel prey > wrap and drain > repair > re-set trap. Hunts out of the web only when hungry. Hatchlings spread outward when the nest grows
			Combat AI: Ambush from ceilings, walls and darkness. Drops on targets, bites to poison, wraps prey in silk and retreats to wait for the venom. Spits web to slow and poison at range. Crawls on any surface, avoids open floors. Retreats to the web when hurt
			Web Behaviour: Web strands act as alarm lines. Cutting, burning or walking through them alerts the spider. Fire destroys webs and spooks spiders
			Brood Behaviour: Young spiders swarm in numbers, Queen stays behind and directs. Killing the Queen scatters the brood
			Weakness: Fire, open ground, light, ranged attacks, cutting the web network
			Reactions: Prey on Bats, Goblins, animals and any living thing. Compete with Goblins for caves. Fear fire and Dragons
			Dynamic: Cocooned corpses mark a spider zone. Abandoned mines and forests get overrun slowly if left unchecked

# 4. Content, Production, and Open Specifications

## 4.1 Content framework

| Content | Purpose |
|---|---|
| Short and long authored quests | Stories, prerequisites, puzzles, choices, consequences, meaningful rewards |
| Open-world exploration | Dangerous fixed-level territory, environmental evidence, secrets, travel and discovery |
| Skilling / production | Training identity, useful outputs, supply preparation, economic participation |
| Dungeons | Extended exploration, enemies, hazards, resource decisions, treasure |
| Bosses / raids | High-level PvM, specialized mechanical tests, rare rewards and collections |
| Human predation (PvH) | Authored regions where qualified human AI can proactively hunt or rob the player; PvH affects **predatory NPC intent**, while exposed-corpse recovery is governed solely by the death site being outside city limits (§3.12). Not multiplayer PvP. |
| Battlegrounds / minigames | Optional structured challenges and distinct rewards |
| Treasure Trails | Clue-solving and prestigious casket rewards |
| Collection logs / skillcapes | Long-term completion and visible achievement |
| **Honor, reputation and authored consequence** | 17-rank moral identity, socially mediated reactions, shop pricing, reward eligibility, final-mission choice hooks and gravestone presentation |
| **Player estates, housing and Construction** | At least three purchasable estate plots per kingdom; a modular, fixed-hotspot Construction house supporting full utilities, owned dog/cat Menageries, trophy/Costume Room collecting, legal combat practice, theme kits and Fishing Aquariums (§3.11). |
| Heraldry and other property | Personal expression, recognized identity and supported commercial/territorial ownership |
| Daily rewards | Optional recurring incentives, not compulsory progression chores |

**Endgame:** Exceptional combat capability plus meaningful faction/economic/social influence. Specialized bosses demand varying combinations of gear, stats, preparation, positioning, and execution rather than one universal counter-build. Raids must be feasible within the single-player baseline; party/companion/co-op rules remain open.

## 4.2 System dependencies and acceptance tests

| System slice | Demonstrable acceptance test |
|---|---|
| **Character creator** | Creator selects Male/Female, Masculine/Heavy/Skinny, one of 12 realistic skin tones, 20 sex-specific hairstyles, 20 facial-hair presets, and 20 independently mixable styles each for Tops/Legs/Shoes. Hair and facial hair offer only the 10 named natural shades H01–H10; Tops, Legs and Shoes independently use only the 24 recognizable, non-duplicate colors C01–C24; Skin uses its separate 12-tone range. Verify unique displayed swatches/names, correct palette isolation, color persistence across save/load and the second transfer scene, and cosmetic-only status with no equipment-stat effects. |
| Creator & input | Both cameras support locomotion, a primary weapon plus Left Hand weapon or shield, legal dual-wielding, two-handed exclusion, ammunition, non-equipment spell/ability actions, interaction, attacks, and defensive moves. |
| Combat | Real-time 3D weapon collision is authoritative; misses, dodges, blocks and parries are physically/timing resolved, **never rolled**. Attack affects bounded attack tempo; Strength governs deterministic landed-hit damage; Defense improves performed defensive actions, not passive hit chance or unblocked HP mitigation. Impact/Stability remains distinct. **Derived combat level** uses the exact Base + maximum-style formula in §2.3 and never overrides item-skill gates or physical combat results. |
| **Magic and Mana** | Four registered 11-slot books (44 total reserved), existing named spell-to-book assignments only, Magic level cap exactly matching Mana cap (1–100), 1 Mana regeneration per 60 seconds active simulation, valid spell targeting/damage effects, elemental weakness doubling **eligible Magic accuracy rating and damage** without creating projectile contact, shared-group maximum-hit ceiling for eligible spells, and Fire Blast debiting both Mana and Fire staff charges exactly once. |
| **Faith prayers** | Maximum Faith points tied to Faith level, zero passive regeneration, eligible altar/wine/potion/**estate pool** restoration; individual toggles pay initial and repeated 6-second baseline drain, +30 Faith bonus yields approximately 12-second periods, no mid-interval refund, one overhead at a time, and correctly tagged Protect from Magic/Missiles/Melee blocks corresponding HP damage when active. |
| **Stealth and Thieving** | Real LOS, cover, light/shadows, sound, NPC suspicion and last known position; unobserved owned-item theft persists but creates no immediate bounty, recognized/communicated crime follows existing reporting rules; Magic perception/disguise effects obey actual world conditions and valid NPC knowledge. |
| **Shared voxel item registry and Gold pricing** | Spawn/pick up/rotate in inventory/equip/drop a Sapphire, Cut Ruby, Brass Key, Antipoison, Stamina Potion, Iron Ore, Copper Ore, Leather, Rawhide, Bones, Bronze Hammer and Dragon Sword: same canonical 3D voxel asset data across world, inventory and original item holder; all references resolve as integer Gold. Validate 13-tier armor/sword schedule, 18-family factor lookup, 44 Bronze–Godly dual-use tools, ammo tier caps, 485M Godly and 50M Dragon three-piece sums, 65K Dragon sword, gem cut/uncut conversions, fixed-chair and Garden reference recipe totals, city/outside corpse transfers, merchant liquidity, real Honor buy discount floors, zero arbitrage after buyback and original IDs surviving second-scene migration. |
| Equipment | **All 13 tiers** use their locked gates across every tier-tagged slot: Strength-90 / Defense-50 permits a Godly sword plus Granite-or-lower armor. Verify Malevolent at level 100, all 12 slot categories, dual-wield eligibility, Left Hand shield-or-weapon exclusivity, two-handed/Left Hand exclusion in either equip order, ammunition outside ordinary inventory, and backpack capacity hook. |
| XP and new-character initialization | Every skill uses the identical precomputed table, reaching exactly 6M at 100; initialize Health at level 10 / 43,446 cumulative XP with 10/10 HP, and all other skills at level 1 / 0 XP. No inconsistent client/UI thresholds, duplicate starting level-up, or unintended XP awards. |
| Interaction | A door, owned container, workstation, and NPC expose options; revalidate on commit; out-of-range, stale-target, interrupted, and full-inventory interactions cannot corrupt state. |
| Crime | Take owned item → optionally create witness evidence → travel/report to Guard → jurisdictional bounty; stopping report prevents automatic bounty. |
| **Honor, 17 ranks and social response** | Player Honor persists as integer −100…+100, maps into exactly 17 nonoverlapping ranks, animates a face marker along the bar, classifies distinct good/evil acts, rejects repeat-farming, applies lawful tiered merchant discounts, respects NPC knowledge/Guard evidence, biases **eligible NPC** corpse loot once, gates only authored rewards/ending choices, and snapshots player death memorial imagery/inscription. |
| **Player death and two-territory recovery** | Test exactly **inside city versus outside city**. Inside: all death-time items are protected immediately, **only Guards** collect and carry the complete set to the normal-fee Undertaker, even with a delayed/absent Guard and a Thief nearby. Outside: **any** physically eligible human may examine/loot, Thief subtypes gravitate more strongly on credible knowledge, and player/Undertaker race for actual remaining items at fee plus surcharge. PvH modifies pre-death AI hunting only. Test original-item custody, equipment repair, mount flight, payment gating and nonlethal estate-sparring exception across time skips/save/load. |
| **Cross-system integration scenarios** | Pass all eight linked production cases **A–H** in §3.13: material supply/Construction; owned forest and crime; Magic/shadows/witnesses; city/outside boundary; PvH looting and repair; estate utilities and sparring; Fishing/egg/pet husbandry; authored quest vulnerability. Assert causal events, item conservation, non-omniscient NPC reactions, valid XP/Honor attribution, persistence and second-scene portability. |
| Logistics | Cargo exists in one authoritative location; shipment status transitions once; intercepted caravan cannot also deliver its cargo. |
| Local markets | Two regions with different genuine stock/demand produce different quotes; purchasing a scarce item raises subsequent pressure, selling oversupply lowers bids, and a real shipment gradually restores availability; price changes show inspectable causes. |
| Merchant liquidity and barter | A trader cannot buy beyond available funds; reciprocal item-for-item exchange atomically transfers real owned stock, rejects illegal or stale offers, and survives save/reload without duplication. |
| Merchant Attitude and orders | Repeated fulfilled deals/assistance change the correct trader's relationship but not every trader's; a funded order is accepted, delivered, paid and closed exactly once, with a competing NPC capable of taking an open request. |
| Business investment | A funded, authorized shop/workshop improvement affects feasible stock or operating capacity only after actual costs, prerequisites and delivery; revenue/losses track real transactions, never guaranteed passive creation. |
| NPC economic choices | A producer, craftsperson, merchant and transporter respond differently to scarcity or oversupply within their permitted subtypes, consider costs and risks, and continue consistently when simulated off-screen. |
| **Wildlife ecology, companions and breeding** | **14 species/28 baby–adult behavior profiles**; contrast owned and wild dogs against wolf pack AI; owned/free-roaming cats; adult and juvenile care; viable reproduction, exact persistent animal/ownership counts, pet bond and context responses; hundreds of abstract distant animals and nonduplicated on-screen individuals. Estate Menagerie/arena behavior follows §3.11. |
| Social simulation | An NPC remembers an event, relays it through a valid path, and later changes behavior. |
| Quest persistence | An authored NPC death can permanently invalidate a quest; references, journal facts, and failure reasons survive save/reload. |
| Kingdom data model | Devmap has two or more synthetic jurisdiction IDs to test separate legal/market state. Actual three kingdoms are production-world content, not required on the devmap. |
| Optional nobility | Implement title, heraldry-identity, office, and permission **data interfaces** with a simple grant/revoke fixture; authored nobility progression is later content. |

### Prototype devmap — engineer-ready definition of done

**Deliverable:** One small, deliberately artificial **devmap** consisting of readable test areas and simple modular structures constructed using **properly modeled, textured, rigged, animated, and reusable game assets**. It is a complete foundation for developing gameplay and a representative set of finished-world-quality assets, **not** a production town, kingdom, geographical map or story demonstration. The **layout may be functional and unadorned; the game assets within it must not be disposable greybox stand-ins**. Final NPC names, cultures, world-specific architecture, geography, cinematics, quest plots, and heraldry are not prerequisites. Test-only data, controls, signage and diagnostic geometry remain clearly separated from production asset packages.

### Devmap asset quality and world-transfer contract

**Every retained asset and gameplay implementation made for the devmap must be portable into GAMEWORLD as an asset, prefab, data definition or subsystem—not recreated or rewritten for a new map.** This includes player/NPC models, meshes, textures, materials, animation clips and controllers, equipment visuals, interactive props, collision and attachment setups, audio/event hooks, AI role definitions, and system-driven behaviors. World placement and lore-specific styling can change without breaking the underlying asset.

- **Shared 3D item fidelity:** **Every obtainable physical item** has a canonical 3D voxel asset with production-intent texture/materials and consistent presentation in world, inventory, merchant UI and permitted equipment/estate views (§2.7). Production can build a representative sample first, but a production item cannot ship as an icon-only entry. Inventory thumbnails, if cached, are renders of the same item model; unique keys, uncut/cut gems, potion bottles, ores, hide/leather, bones, weapons and tier visuals must be distinguishable. Asset source and reference value resolve by stable definition ID.
- **Models and textures:** Build representative character, equipment, environment-piece and interactive-object assets at an agreed production-intent quality, with deliberate topology, UV/material organization, correct scale, readable visual identity, consistent style, colliders and performance awareness. Workstations, doors, chests, resource fixtures and basic structural pieces should be reusable, visually credible objects—not temporary boxes awaiting total replacement.
- **Characters and animation:** Use carefully adapted *Absentialis* character foundations and shared compatible rigs where appropriate. Author and validate production-intent locomotion, camera transitions, melee/ranged timing, left/right-hand equipment and shield states, defensive actions, hit reactions and representative NPC work/interaction animations. Align actual combat collision windows and interactions to animation; do not fake the finished movement feel with throwaway clips.
- **Modular asset construction:** Separate reusable object/character definitions from where a devmap places them. Each reusable prefab references stable asset identifiers and data-defined interactions; it owns its mesh/material variants, collision, sockets/anchors, animations, state hooks and audiovisual bindings where relevant. Avoid hardcoded map paths, coordinates, dev-only object names, temporary asset IDs or scripts tied to the test scene. Item/entity identity and ownership still follow §3.8.
- **Art/engineering integration:** Establish shared conventions for asset naming, versioning, source files, exports, scale/pivots, rig/socket names, material setup, animation events, collisions, performance budgets, and ownership before mass asset production. Keep editable sources and derived/imported versions traceable. Final numerical texture/LOD/rig budgets are engineering/art decisions, but the reusable pipeline is mandatory.
- **Scope discipline:** Create a **small, representative, genuinely reusable asset library** per gameplay need; do not demand every environment kit, weapon appearance, NPC outfit, creator variant, or lore-specific building during devmap completion. Temporary debug shapes, labels, synthetic NPC identities and test-only UI may remain visibly crude **only when explicitly tagged non-shipping**. They must not conceal or substitute for the required finished-world-intent samples.
- **World-transfer proof:** Maintain a second, otherwise empty **migration test scene** outside the devmap. Instantiate approved player/NPC prefabs, representative equipment, environmental pieces, interactable objects and their data into that scene **without copying devmap-only scripts or rebuilding assets**. Verify models, textures, animation, interaction, combat, NPC logic, saving/loading and ownership behave there identically. Repeat this test after material pipeline or schema changes.

**Quality bar:** Good enough to reuse and build upon—not an obligation to deliver full-game art coverage or final lore art direction before the devmap plays well. Do not sacrifice gameplay iteration speed, but do not accrue avoidable replacement work by treating core characters, animations or props as expendable prototypes.

**Layout (one continuously accessible map):**

| Devmap area | Minimum fixtures | What it proves |
|---|---|---|
| Movement/combat lane | Modular textured ground/cover, production-intent rigged player and NPC, reusable targets, representative equipped weapon/shield meshes, equipment cabinet | First/third camera, free aim, primary weapon/Left Hand/ammunition slots, dual-wield and shield alternatives, one-/two-handed conflicts, Attack cadence, distinct family speeds, hit collision, blocking/parrying/dodging, impact/Stability, resources, tier gates, combat XP |
| Skill laboratory / interaction shed | Reusable textured door and lock variants, loose item, owned container, detailed workstation and resource-fixture prefabs, fire fixture, modular traversal pieces, fishing fixture | All **18 registered skills**, primary/supporting XP, shared 1–100 curve, level-up feedback, requirements, action outcomes, recipes, basic test loops |
| NPC/social yard | Reusable NPC character prefabs with representative work/social/combat animations, concrete worker/Guard/Thief/witness subtypes, patrol anchors, modular occluders, jurisdiction volume | **Decision-making**, perception, schedules/goals, work selection, pathing, social memory, suspicious behavior, law reporting, interrupt/replan, agent debugging |
| State/economy bay | Two reusable shop/container setups, one additional synthetic market region, real stock/funds and offer fixtures, order/commission data, cargo prefab and transport-state fixture (debug-only abstract carrier permitted) | Region-sensitive quotes, finite liquidity, barter, individual trader Attitude, one funded order, bounded investment effect, price/stock causality, shipment transfer/interception invariants |
| Quest-state kiosk | Debug quest giver/flag panel, quest dependency target, removable NPC | Authored state-machine hooks, intentional failure, journal changes, persistence |
| **Farm/habitat test zone** | Reusable farm enclosure, animal resource/water/cover anchors, connected wild cells, representative production-intent adult/baby animal assets, human Livestock Farmer and Hunter fixtures | Animal goals, herds/packs, predator/prey, breeding/birth, maturation, player interference, ownership, streaming and population-accounting tests |
| **Estate/Construction plot** | One purchasable estate with Estate Agent interaction, reusable Parlor/Garden and room-edge door hotspots, furniture preview anchors, sample Kitchen/Dining/Workshop/Bedroom, valid hammer/saw/plank/nail and Garden iron/soil costs, representative advanced fixture and two-bedroom/servant test actor | Room-graph construction and demolition, skill/boost gating, one-time XP/material cost, safe content transfer, utility integrations, portable asset prefabs and save/load |
| Debug/control zone | Clock advance, teleport, spawn/reset, XP grants, skill/gear setters, AI behavior inspector, save/load, world-state inspector | Fast reproducible tests, XP curve boundary checks, AI decisions, combat telemetry, state invariants |

**Implementation principle:** Temporary test dialogue, synthetic lore, tuning values, and explicitly non-shipping diagnostics are allowed. **Reusable visual, animation, object and character assets are not placeholder art**: the devmap produces durable, detailed, game-ready representative assets alongside real functionality. The core logic of leveling, combat, NPC AI, interactions, and authoritative world-state changes must run for real. Build small **functional samples of every established system**, not cosmetic buttons or permanent stubs. An AI worker must actually perceive, choose, navigate, act, and update shared state; a combatant must actually target, move, attack, defend, and react using the intended rig, animation and collision pipeline. A 2-state test quest remains sufficient for the authored-content interface.

**Required end-to-end test flows:**

1. **Interaction:** Focus an object in either camera → inspect valid/disabled contextual actions → approach/use or fail due to distance/ownership/requirements → verify result or failure is explicit.
2. **Leveling / all 18 skills — primary gate:** Every skill is registered and visible in a skill panel; a fresh player starts with **Health 10 / 43,446 XP / 10 HP** and every other skill **1 / 0 XP**, and all use the same exact stored 1–100 cumulative XP thresholds (6M at 100). Each skill has **at least one executable test action**, with the **five specified open-world action-route definitions per skill registered and inspectable** (90 total; complete production animations/content are not a devmap sign-off gate), awarding validated XP and applying at least one requirement/unlock or stat/effect hook; train across multiple levels and verify milestone thresholds at 60, 80, 90, and 100, including cap behavior, level-up presentation, save/load, and no duplicated XP. Run representative tests at **each of the five qualitative reward tiers** across the roster, then cover all 90 routes through data validation; verify an actual melee collision awards appropriate Attack/Strength shares, taking real hostile HP damage always awards Defense XP, and a defended hit grants only its justified active-defense XP. Verify rewards vary by real recipe/resource/target/effort instead of paying a fixed amount for a route or tier.
3. **Inventory and production:** Pick up, transfer, stack, consume, and craft test items → enforce skill/material checks, capacity and item ownership → grant relevant XP on successful actions only → retry without duplicate outputs. Confirm a tool-free Agility action, tool-free Thieving pickpocket, Cooking using a world fire/stove with no carried tool, Smithing using a reusable hammer and world anvil, and at least one correctly tool-gated gathering/construction action. Test one Bronze and one Godly **Axe/Hatchet, Pickaxe and Hammer** as both a real work tool and a melee weapon using the same item ID; assert **all eleven Bronze–Godly tiers** exist in the definition registry for each tool, that work checks the correct skill threshold, and that item transfer/equip does not duplicate a tool. Distinguish reusable tools, consumed inputs, and world stations; no tool-only equipment slot.
4. **Combat/equipment — second gate:** Verify the derived combat-level formula on the **level-3 starter**, at mixed melee/Archery/Magic builds where only the largest combat style contributes, at Faith odd/even values (halving before Base), around rounding boundaries, and at **all seven combat skills 100 → combat level 127**; recalculate after leveling and save/load. Run direct-contact melee and projectile tests. Log **weapon animation and active frames → physical contact or miss → actual dodge/block/parry result → Strength/base/Strength-bonus landed-hit damage → impact/Stability**, with stamina and recovery. Verify Attack changes bounded chain timing/handling but **never max hit**; Strength changes damage but not attack timing/collision; Defense improves **performed** active defenses but cannot negate an unblocked contact or reduce its HP damage. Compare faster lower-tier and slower higher-tier families. Test **all 13 locked tier gates** (Stone 1 to Malevolent 100), both weapon catalogs (13 melee, 5 ranged families), **Brutal Arrows permitted only Leather–Steel**, Sword/one-handed Longsword + shield or compatible second one-handed weapon, mutual exclusivity of weapon vs shield in Left Hand, and 2h Sword/Left Hand exclusion in both equip orders; confirm equipped ammo outside inventory and backpack capacity hook. Swap gear manually; validate independent Strength/Defense requirements. Exact tempo, damage and defensive-window coefficients may be provisional, but physical contact and active-defense responsibilities are mandatory; **no melee accuracy RNG**.
5. **Intelligent NPC behavior — third gate:** Run at least three agents with different goals and routines. Agents sense visible/audible stimuli (including occlusion), evaluate competing needs/roles, select jobs/patrol/trade/social/response actions, navigate to targets, recognize blocked paths or unavailable resources, react to player interference, retain relevant memories, share information through encounters, and replan after failure or time advance. A hostile agent must use basic combat choices rather than a fixed attack loop. Inspectors must expose current goal, perceived facts, decision reasons, navigation target, action outcome, memory, and next reconsideration. **AI must be functional and observable, not hardcoded scripted choreography. The devmap must instantiate explicit concrete subtypes (not bare roles), validate their non-overlapping signature action/goal eligibility, and support data-driven inspection of subtype IDs and candidate action lists; the full 100-subtype content set can be built and validated incrementally. Include at least one Unemployed need-driven agent and one synthetic noble-office permission fixture in subsequent subtype acceptance testing; neither requires introducing production lore or replacing current P0 prototype priorities. Spot-check representative new subtypes (Sun Priest, Faith Instructor, Banker, Innkeeper, Bartender, Clerk, Curator, Tanner) for non-overlapping signature decisions and boundaries.**
6. **Witnessed theft:** Take owned item → witness records observation → no automatic bounty → NPC reports to Guard → kingdom-specific wanted state. Reset and block reporting to verify no bounty appears.
7. **World state:** Move cargo from source to in-transit to destination, or intercept it. Confirm cargo exists in exactly one authoritative location and cannot be both delivered and stolen.
8. **Economy chain:** In two synthetic markets, create a shortage → observe trader/producer/hauler responses and altered offers → have the player barter with one trader and complete a funded crafting/delivery order → inject competition or intercept real cargo → verify resulting inventory, funds, price signals, personal Attitude, and order status → time skip and save/reload with no double-counting. Confirm a supported business investment changes capacity only after real prerequisites and inputs. **No purchase or investment grants XP or levels.**
9. **Quest and NPC mortality:** Activate a short **test-only authored quest state machine**, remove its prerequisite NPC, confirm intentional failure, journal update, and persistent death; do not auto-replace the NPC to rescue the quest.
9a. **City and outside-city death, Undertaker and PvH:** On a test boundary, kill the player **inside** with a Bandit, Thief and Guard nearby; only the Guard must access the sealed-at-death corpse, and the Undertaker must return **every original item** for the normal fee regardless of collection delay. Repeat **outside** with ordinary non-Thief passersby, an informed Thief and a nearby PvH-enabled hostile NPC. Check distinct motivations, actual line of sight or learned information, arrival priority, partial real loot transfers, player self-retrieval versus **base + outdoor surcharge** service, missing item provenance, inadequate funds and no double charges. Repeat with PvH disabled to confirm that outdoor corpse access does not change—only proactive human hunting does. Verify graveyard empty inventory, repair state, same-ID mount flight, no item duplication, nonlethal ring exemption and persistent state across time skips and migrated scenes.
10. **Persistence and time:** Save/reload between transitions; advance world clock; unload/reload or abstract/simulate a test NPC/shipment; confirm identity, ownership, memory, and state remain coherent.
11. **Estate and Construction — portable foundation:** Purchase the **one devmap estate** from an authorized Estate Agent; build an attached Parlor/Garden via a valid free door using the exact **Crude Wooden Chair (2 planks + 2 nails, hammer and saw)** and **Garden (10 iron bars + 10 soil)** costs; reject occupied-door placement with a safe delete/rebuild prompt; refuse to delete a room whose installed items/attached rooms cannot be safely resolved; validate **Kitchen 5, Dining Room 10, Workshop 15 and Bedroom 20**; verify **one bedroom fails servant hiring, two accessible bedrooms permit eligible assignment**, subject to an actual concrete-subtype worker; check **84 Construction + 8 eligible test boost → effective 92**, losing the boost afterward without disabling installed furniture; test the Pool's Faith/HP/stamina/stat/status restoration **without Mana refill or XP**; one registered Nexus teleport; one mounted amulet or other stored fixture without item duplication; Occult Altar book change without unlocking unknown spells; Gilded Altar offering with active burners grants exactly **3.5× eligible base Faith XP once**; verify the Combat Dummy cannot farm combat XP; carry all reusable room/asset definitions into the migration test scene and preserve state across save/load. Then test a genuine owned dog and kitten with unique IDs in a ≤30 Menagerie, automatic stocked-feeder kitten care including shortage, cat scratching post with observer-dependent NPC dialogue, optional outdoor pet play Arena with zero injury/XP, a swapped trophy mount retaining the previous original item, categorized Costume Room withdrawal, a three-theme cosmetic toggle preserving room graph, one catch-authenticated fish egg and Aquarium display, and three sanctioned Combat Ring type data definitions with one practical NPC sparring/defeat scenario (no player death penalty, real skill-action XP, no spectator injury). Full nine-estate production art is not a devmap gate.
12. **Animal ecology, cats, dogs and livestock:** In farm/habitat test cells, verify **all 14 species and 28 Baby/Adult decision profiles**; adult/young responses, group movement, prey flight and predator detection, actual ledger removals on death, Livestock Farmer breeding of a valid owned pair, single birth/hatch transaction, offspring maturation, owned-livestock theft reporting, and hundreds of distant aggregate animals without duplicating visible individuals across streaming or saves. Include observed owned/wild dogs and their different goals from wolf AI, social/rest/scratch behavior for owned/unowned cats, puppy/kitten birth/maturity and an actual owner transfer; owned residents must not be dematerialized into extra wild cohorts. Exact life-cycle timings are configurable.
13. **Asset portability and production-quality representative set:** Verify authored player/NPC models and rigs, shared animation controllers, representative weapon/shield attachments, textured objects, doors/containers/workstations and prefab interaction anchors meet the agreed quality/technical standards. Spawn the **same** prefabs and action bindings in the migration test scene without devmap-specific dependencies; test locomotion, combat hit timing, NPC actions, object interactions and save/load there. A renamed scene, changed world origin, or replacement test jurisdiction must not require re-authoring meshes, animations, logic, or item definitions.

**Required engineering tooling:** Fixtures spawned/reset independently; deterministic scenario seeds or explicit scenario parameters; direct command/API test harness; event trace with causation; XP/level dashboard showing all 18 skills and threshold diffs; combat telemetry (attack startup/contact/recovery, physical miss/contact, dodge/block/parry outcome, Strength-based landed-hit damage, active-defense window, impact, stamina, XP); **AI decision inspector** (stimuli, needs, scores/reasons, selected task, path/replan, memory); entity/state inspector; one-click save/load and time advance; assertion output for item conservation, **finite trader funds, real order reservations, barter conservation, price-change causes, nonduplicated business revenues, and no economy action granting XP/levels**, no duplicated rewards, **one same-skill tier award per event, correct cross-skill combat attribution, Defense XP on actual incoming HP damage, and zero XP for failed/idle/spam attempts**, and report-before-bounty, **player corpse/Undertaker/looter item provenance and fees, exactly two death classifications, immediate city security/full-gear guarantee, actual outdoor human looter motivation, separate PvH aggression flag, equipment condition, and surviving mount recovery**. Include estate/pet/collection debug views for animal owner/home/trust, Menagerie roster/capacity and food stock, display original-item IDs, ring nonlethal state/XP attribution, theme ID/room-graph invariance, aquarium catch event/egg provenance and guest viewing permissions. Include asset/prefab validation for stable references, materials, rig/animation bindings, colliders, interaction anchors and content-only migration to the second scene. All fixture values and action ranges should be configuration-driven.

**Acceptance gate:** All 18 skills have functioning XP/action/level hooks; representative responsive player/NPC combat works; NPCs independently perceive, decide, navigate, remember, and react; **every major system has a working devmap test fixture**; every required end-to-end flow is replayable by QA and critical invariants are covered by automated tests. Engineering should be able to diagnose *why* a transaction, witness report, skill check, quest transition, or inventory transfer succeeded or failed from the inspector. **In addition, representative player/NPC models, equipment, animations, textured environment/interactable props and prefab bindings meet the agreed production-intent standards and pass the second-scene portability test.** Finished lore, authored quests and complete game-wide asset coverage are **not** required for devmap sign-off.

**Deferred from devmap completion (not from systems or reusable asset creation):** Three authored production kingdoms, complete towns/terrain biomes, lore, named cast, full questlines, all 20 options in each creator catalog and every weapon/prop skin, complete production training content for all 18 skills, final XP/hour balancing, mass-population AI optimization, dynastic politics, authored noble advancement, production raids/boss rosters, finished world-specific art direction, cinematic polish and broad environment destruction. **Representative production-intent models, animations, textures, materials, interactable objects and modular structures are NOT deferred**; the devmap builds and validates these as reusable foundations. All gameplay system implementations begin during devmap development with real test cases. No subsystem may exist only as a nonfunctional placeholder at sign-off.

## 4.3 Parallel workstreams and engineering order

**Engineering — concurrent system streams on one devmap (prioritized by gameplay risk):**

1. **P0: Progression and leveling FIRST.** Implement the canonical XP table and atomic XP awards; all 18 skill records; level thresholds, cap, UI and celebration; primary/supporting combat XP; XP from real gathering, production and utility actions; skill gates/effect hooks; save/load and exploitation tests. The complete roster must be functional in miniature, **not** deferred to later world content.
2. **P0: Combat alongside leveling.** Implement player controller, both cameras, primary weapon plus Left Hand weapon or shield (including compatible dual-wield combos and two-handed exclusion), dedicated ammunition slot, ability actions, aim/collision, weapon-family foundations, Attack tempo/handling, Strength landed-hit damage, Defense active dodge/block/parry capability, Attack/Strength/Defense/Health/Archery/Magic/Faith test actions, stamina/health, guarding/parry/dodge, immediate Stability and resistance, enemy targeting, responsive feedback, death/recovery state hooks, and XP integration. Values are tuning data until balanced.
3. **P0: Intelligent AI alongside combat.** Build a reusable perception → knowledge/memory → needs/goals → action selection → navigation/execution → outcome/replanning loop, with occupation routines, autonomous skill/job actions, basic combat tactics, witness reporting, interruptions, social information and unload/reload reconciliation. Important NPCs get richer decisions than background agents. Avoid a fake 'AI' demo driven solely by fixed scripted paths.
4. **P0: Shared substrate supporting all streams.** Stable entity IDs, world state, event causation, action/interaction provider, inventories/ownership, validated transfers, **graveyard/corpse/Undertaker records with two-way territorial classification and immediate city Guard protection**, persistence, time, inspector, debug controls, and automated tests. These are implemented **from day one** rather than after gameplay.
5. **P0: Reusable art, animation and object pipeline from day one.** In parallel with the gameplay streams, establish the production-intent player/NPC rig and adapted character asset pipeline, representative equipment and environment assets, collision/attachment conventions, textured modular structures, interaction prefabs, animation controllers and a reliable migration test scene. These assets must be made with final-world reuse in mind. Detailed quality is required for the representative set; the full catalog of visual variations is not a devmap prerequisite.
6. **P1: All remaining systems begin immediately in compact form.** Every established system must own a schema/interface, fixture, and executable scenario: gathering/production, agility and firemaking, thieving/stealth, quests/dialogue, crime/law, trade/logistics/economy (**regional pricing, finite merchant funds, barter, merchant Attitude, orders and supported business investment**), reputation/factions, **14-species animal ecology (28 Baby/Adult profiles, distinct owned/wild dogs, owned/free-roaming cats, predator/prey, breeding and population streaming)**, resources/ecology, **player estate/Construction room editor, utilities, Menagerie, collection rooms, sanctioned combat and Aquarium contracts** (§3.11), other property/construction, exploration/discovery, equipment/degradation, special attacks/**four Magic spellbooks and Mana/Faith prayer resources** (representative actual named spells only), **LOS/shadow/audio stealth and Thieving**, profile restrictions, nobility/heraldry permissions, clue/minigame/raid reward frameworks and systemic events. Develop real representative behavior progressively rather than the entirety of its eventual content. Unresolved rules remain configurable and visibly labeled.
7. **Integration and hardening.** Run cross-system chains (combat → XP → equipment eligibility; harvest → craft → trade; theft → NPC perception → report → law; NPC work → market stock; death → quest invalidation; all chains → time skip/save/reload). Prioritize correctness, iteration speed, and gameplay feel before population/world scale or polish.

**Scope definition:** **All systems and the reusable asset pipeline start during the devmap phase; not all systems or visual libraries must be content-complete when the devmap first runs.** The 100-subtype catalog is a design specification and next-iteration behavior registry; teams can add its configurations progressively without abandoning current leveling, combat or core AI work. Use compact, working implementation slices and a **small production-intent asset set** with clear extension points and tests. The P0 order establishes what must work earliest, not permission to abandon other systems or postpone fundamental model/animation quality.

**Narrative/worldbuilding — parallel, non-blocking:** Develop GAMEWORLD's kingdom identities, chronology, cultures, conflicts, authored characters, major quests and art-direction references as **design deliverables**. Define future data needs (dialogue conditions, quest dependencies, faction relations, heraldry, jurisdiction) and supply evolving visual guidance. Reusable generic assets are built carefully now; lore-specific models/decoration can be layered onto them once the world is defined. **Do not require finished narrative assets to validate the devmap.** Narrative tests can use synthetic identities and authored test dialogue without compromising character rig, object or animation quality.

**Integration milestone:** Transfer the established systems and **the exact approved devmap asset/prefab library** into a new world scene first, with no wholesale remodeling, retexturing, re-rigging, reanimating, or replacing gameplay interactions. Then instantiate a small authored scene from narrative data and expand toward a production settlement and the eventual three-kingdom playable build. Future lore-specific visual variants and additions may be created, but the core asset and system foundation is intended to survive the transition.

This is **development priority**, not a release schedule. All established systems and representative production-intent art/animation/object work enter development on the devmap; mass content coverage, final balancing, large-scale optimization and world-specific dressing are later milestones.

## 4.4 Open specifications (do not infer)

**Critical:**
- **Real-time Attack cadence/handling** (Attack level plus weapon Attack bonus), **Defense skill and armor contributions to performed block/parry/dodge control without RNG**, **Strength-driven landed-hit HP damage**, weapon base stats, family-specific Strength bonuses beyond the locked **13-tier Sword-bonus table**, impact/Stability, Attack cadence, HP, stamina, Magic damage/accuracy conversion and elemental-group max-hit curves, **Faith prayer drain interval arithmetic/boost stack caps (not passive regeneration)**. **The combat-level formula and starting levels are finalized in §2.3; only engineering precision and UI presentation require verification.** No passive evasion chance. Armor absorption and uniform damage rolls follow §2.3; armor tuning remains open.
- **Ninety natural skill-action routes (five per skill) and five qualitative reward tiers are established in §2.2; no fixed XP-by-route or XP-by-tier amounts are approved.** Per-result XP, resource/recipe/target scaling, effort and risk factors, anti-farm pacing and the gameplay-hour budget for ordinary and completionist play need tuning. The eleven noncombat skills’ core tools, resources, and workstations are specified; Bronze–Godly dual-use Axe/Hatchet/Pickaxe/Hammer availability is established. Tool equip/inventory access details, actual yield/speed/combat statistics, recipes, consumables, method-specific catch requirements, and optional gear benefits still require validation.
- Final player interaction verb coverage, production targeting distances and skill-check probabilities; **equipment slot categories are established in §2.4**. Ordinary inventory base capacity, stack limits, ammunition-stack rules, exact ring count, and backpack capacity bonuses are reserved for inventory design. Prototype interaction contract in §3.2 is specified.
- Production save serialization format, schema migrations, exact implementation architecture and near/distant performance budgets. Prototype invariants and transaction contract in §3.8 are specified.
- **Player death and PvH are established in §3.12:** empty graveyard respawn and one persistent original-item corpse; **only inside-city versus outside-city** death classification; city claims protected from death and entirely returned through Guard/Undertaker at normal fee; outside claims exposed to physically credible looting by **any human** (with opportunistic Thieves more inclined to seek them), or player/Undertaker retrieval at fee plus surcharge; **PvH affects proactive human hunting, not territorial corpse rights**; violent-death equipment condition; owned-mount flight; Combat Room exception. **Open:** price/time values, outdoor claim decay, looter decisions and collection routing, post-respawn resources, mount retrieval detail, protected quest-item handling and explicitly authored restricted-profile/Hardcore overrides. City gear security and recovery are not tunable away.
- Quest-state representation and protection from technical softlocks while preserving intentional failure.

**High:**
- **Honor balancing:** The 17-rank intervals, ±100 bounds, four commercial discount thresholds/percentages, core gain/loss triggers, profile and memorial behavior, and once-committed loot-bias architecture are established in §3.5. **Open:** exact event deltas/cooldowns, public reputation spread, merchant exceptions/floors and non-arbitrage math, authored exclusive gear and ending thresholds, eligible corpse classes/secondary loot budgets and alcohol/tobacco item definitions.
- Final asset authoring budgets and conventions (texture resolution/packing, geometry/LOD targets, animation retargeting/export, shader/material variants, rig/socket naming, streaming limits) remain an art/engineering validation task. **Canonical 3D voxel assets for every physical item and shared world/inventory presentation are established under §2.7;** individual high-end art variants can be delivered in later content batches. **Portability, production-intent quality, reusable source files, stable prefab contracts and second-scene migration tests are established requirements**, not optional future polish.
- Validate and tune action conditions, priority parameters, assets and labor supply for the established 100 concrete NPC subtypes (twenty families × five); do not treat the catalog as 100 separate AI implementations. **Clergy**, **Civic Services** and **Leatherworker** are established occupational families; religious functions are no longer a future-candidate gap for taxonomy.
- Governing skill assignment for melee weapon families other than swords and for nonprotective accessories/backpacks; handedness of non-sword families; projectile/launcher compatibility and thrown-ammunition consumption/recovery. **The 13-tier level ladder, Sword Strength bonuses, and permitted weapon catalogs are finalized in §2.4**.
- Defensive timings, camera assistance, stagger resistance, status effects; **the proposed 11-step 1/10/…/100 spell-slot ladder, and each named spell's actual assigned unlock level/Mana cost**, remaining unnamed spell definitions, **other** spellbook-switching sites and combat restrictions (the estate Occult Altar and one active book are established), Magic Damage Bonus equipment sources, Fire staff handedness/charge rules, Magic accuracy/weakness targeting behavior; **named Faith prayers**, their stat boosts/unlocks, Faith bonus cap and restoration amounts; LOS/light/audio/recognition thresholds and memory budgets for stealth.
- NPC perception and memory rules, dialogue implementation, succession/role replacement, and monarch mortality handling.
- **Animal ecosystem and companion tuning:** **14 species/28 profiles** and owned/feral dog–cat behavior are established; Menagerie support is defined in §3.11. Open: dog/cat acquisition, training/affinity, food, care, trust, wild/owned ranges, companion animation budget, habitat-cell densities/capacities, diet/threat matrix, animal group sizes and ranges, movement/sensory thresholds, breeding eligibility, realistic-looking but compressed game-time gestation/incubation/maturity, viable offspring counts, persistent identity/cohort thresholds, carcass decay, ownership/animal-sales actions, rendering and behavior budgets, and reusable baby/adult asset pipeline. Architecture/14 species are established in §3.9; numbers and optional horse riding are not.
- Kingdom identities, resource dependencies, political structure, culture, authored cast and story.
- Crime fine tables, witness interruption, bounty cap interpretation, ranger jurisdiction boundaries.
- **Initial item reference values, standardized voxel item definitions and tier/family calculations now exist in §2.7.** Open for economy balancing: regional elasticity, wage/quest/loot wealth sources, exact vendor spreads/floors, rare-item funding and availability, quote duration, volatility limits, scarcity/oversupply thresholds, price-update cadence, production/consumption capacities, resource replenishment, transport margins, shipment rerouting and controlled structural changes.
- Merchant funding/restock rules, personal Attitude and faction-modifier thresholds, gift/haggle effects and safeguards against circular gift-trade exploits; barter valuation, permitted bundles and exchange UI.
- Order/commission types and visibility, deposits/reservations, NPC bidding/competition, delivery deadlines, cancellations, and settlement/failure consequences.
- Raid companion/group assumptions, encounter resets, risk warnings and loot rates.

**Later:**
- **Estate/Construction** is established in §3.11: at least three purchasable plots per kingdom, one devmap plot, fixed door/furniture hotspots, Parlor/Crude Wooden Chair and Garden recipes, Kitchen 5/Dining 10/Workshop 15/Bedroom 20 unlocks, two-bedroom servant gate, effective-92 utility house, Menagerie (30 actual dogs/cats and automatic kitten feeding), outdoor playful pet Arena, trophy gallery/instant mounts, expanded Costume Room, nonlethal three-ring Combat Room with rapid legitimate combat XP and no-penalty estate defeat, Swamp/Civitas/Wilderness style kits, and Aquarium collectible fish eggs/Fishing perks. **Open:** new room/fixture levels and costs, room limit/floors, boosts/salvage, servants, storage capacities, teleports, repair/altar recipes, pet welfare/bond tuning, specific trophies/items, sparring XP balance, Fishing perks and estate pricing. Separate business investment terms, maintenance and financial risk, workforce expansion, withdrawal/insolvency, heraldry and noble privileges/title revocation also remain open.
- Ironman variant restrictions, daily reward details, mount/travel access, post-100 tracking, platform targets, mod support, and additional-kingdom scope.

**Integration governance:** Required causal links, priority rules and eight complete scenarios are specified in §3.13. Resolve numeric tuning in each owning system; do not create local substitutes for original inventories, NPC knowledge, quests, animal populations, skill XP or corpse custody.

**Governance:** Resolve an open rule in its system section; remove the corresponding item here. Do not create duplicate speculative systems or revive rejected light/mail/heavy classifications, class respecs, hard target lock-on, or unrestricted kingdom destruction.
