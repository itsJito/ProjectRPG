package rpg.monster;

import rpg.combat.Spells.Element;

/**
 * The eleven monsters (gamefixes, GDD 3.14) as data: one shared monster mind reads these numbers, so species differ by
 * what they are - how big (the model and the hitbox, in blocks), how they get about, their archetype and the weights of
 * their instinct drives, the size of their territory and their group, when they are abroad, how sharp their senses are,
 * how they fight, what they are weak to, and what they leave behind. Levels are a range; each one spawned takes a
 * level within it, and its skills follow from the level (their skills determine their combat level, as anyone's do).
 */
public enum MonsterKind {
    //          title           lvl       model w,h,d          hitbox w,h,d          form             move              archetype
    GOBLIN("Goblin", 1, 50, 0.6f, 1.3f, 0.6f, 0.5f, 1.2f, 0.5f, Form.HUMANOID, Move.WALK, Archetype.PACK_RAIDER),
    ORC("Orc", 20, 120, 1.0f, 2.1f, 1.0f, 0.8f, 2.0f, 0.8f, Form.HUMANOID, Move.WALK, Archetype.PACK_RAIDER),
    TROLL("Troll", 60, 130, 2.5f, 3.5f, 2.0f, 1.8f, 3.0f, 1.6f, Form.HUMANOID, Move.WALK, Archetype.SOLITARY_BRUTE),
    IMP("Imp", 1, 30, 0.4f, 0.9f, 0.4f, 0.35f, 0.8f, 0.35f, Form.HUMANOID, Move.FLY, Archetype.HARASSER),
    BAT("Bat", 1, 40, 1.5f, 0.6f, 1.5f, 0.8f, 0.5f, 0.8f, Form.BAT, Move.FLY, Archetype.HARASSER),
    GHOST("Ghost", 15, 80, 0.8f, 2.2f, 0.8f, 0.7f, 2.0f, 0.7f, Form.HUMANOID, Move.HOVER, Archetype.HAUNTER),
    ZOMBIE("Zombie", 10, 120, 0.8f, 2.0f, 0.8f, 0.6f, 1.95f, 0.6f, Form.HUMANOID, Move.WALK, Archetype.BOUND_DEAD),
    SKELETON("Skeleton", 15, 140, 0.7f, 2.0f, 0.7f, 0.6f, 1.95f, 0.6f, Form.HUMANOID, Move.WALK, Archetype.BOUND_DEAD),
    WEREWOLF("Werewolf", 20, 100, 1.1f, 2.3f, 1.1f, 0.9f, 2.1f, 0.9f, Form.HUMANOID, Move.WALK, Archetype.APEX_HUNTER),
    DRAGON("Dragon", 70, 350, 5.0f, 4.0f, 8.0f, 4.0f, 3.5f, 6.0f, Form.DRAGON, Move.FLY, Archetype.APEX_SOVEREIGN),
    GIANT_SPIDER("Giant spider", 1, 70, 1.95f, 0.975f, 2.275f, 1.625f, 0.78f, 1.82f, Form.SPIDER, Move.CLIMB, Archetype.AMBUSH_PREDATOR);

    /** How the body is built: a person's skeleton (head, torso, two arms, two legs), a bat, a dragon, a spider. */
    public enum Form { HUMANOID, BAT, DRAGON, SPIDER }
    /** How it gets about: on its feet, on the wing (and its feet), floating, or on any surface. */
    public enum Move { WALK, FLY, HOVER, CLIMB }
    /** The GDD's AI archetypes: what drives a species' behaviour as a whole. */
    public enum Archetype { PACK_RAIDER, SOLITARY_BRUTE, HARASSER, HAUNTER, BOUND_DEAD, APEX_HUNTER, APEX_SOVEREIGN, AMBUSH_PREDATOR }
    /** When it is abroad. */
    public enum Active { DAY, NIGHT, DUSK_NIGHT, ALWAYS }
    /** The dragons' breeds (gamefixes): what their breath is. */
    public enum Breed {
        FIRE("Fire", Element.FIRE, 0xB02020), ICE("Ice", Element.ICE, 0x7AB8E0), EARTH("Earth", Element.EARTH, 0x6A5A3A),
        ELECTRIC("Electric", Element.ELECTRIC, 0xD8C030), WATER("Water", Element.WATER, 0x2A5AA8);
        public final String title; public final Element element; public final int color;
        Breed(String title, Element element, int color) { this.title = title; this.element = element; this.color = color; }
    }

    public final String title;
    public final int minLevel, maxLevel;
    /** The model's size and the hitbox (width, height, depth) in blocks, as the gamefixes gives them. */
    public final float mw, mh, md, hw, hh, hd;
    public final Form form;
    public final Move move;
    public final Archetype archetype;

    // ---- filled in below, species by species
    /** The instinct drives' species baselines (0..1): how fast hunger rises, the fear threshold, aggression, territorial feeling, fatigue rate, social pull, curiosity. */
    public float hungerRate, fearThreshold, aggression, territory, fatigueRate, social, curiosity;
    /** Home range (blocks) and core zone (blocks from the lair), the group it keeps (min..max), when it is abroad. */
    public float range, core = 15;
    public int groupMin = 1, groupMax = 1;
    public Active active = Active.ALWAYS;
    /** Senses: sight range (m), hearing (a multiple of a noise's radius), smell range (m), and memory (game seconds a threat is remembered). */
    public float sight = 18, hearing = 1, smell = 0, memory = 120;
    /** Walking and running speeds (m/s). */
    public float walk = 1.2f, run = 3.6f;
    /** Natural weapon: its name, damage, impact, reach (blocks), type; and the weapons it may carry instead (item ids, any one). */
    public String natural = "claws";
    public float damage = 3, impact = 12, reach = 0.6f;
    public rpg.item.ItemDef.DamageType damageType = rpg.item.ItemDef.DamageType.SLASH;
    public String[] carries = {};
    /** Its ranged attack, if it has one: a thrown item id, or "firebolt", "breath", "web", "spectral", "screech", "shout"; its range. */
    public String ranged;
    public float rangedRange;
    /** What harms it double, and what it heals by (regenerates when out of combat, per game second). */
    public Element weakness = Element.NONE;
    public float regen;
    /** Below this share of its HP it flees (or, for the stubborn, turns berserk instead). */
    public float woundedAt = 0.25f;
    public boolean berserk;
    /** Drops (item id[:min-max] each): always, common, uncommon, rare, very rare. */
    public String[] always = {}, common = {}, uncommon = {}, rare = {}, veryRare = {};
    /** Its voice (sound keys): idle, alarm, hurt, death. */
    public String idleSound = "creature_misc", alarmSound = "creature_roar", hurtSound = "creature_hurt", deathSound = "creature_die";
    /** Sydney's profile for its ragdoll (a person's skeleton, scaled; the undead stagger, the big ones fall heavy). */
    public int sydney = astratto.bridge.AstrattoNative.SYDNEY_HUMAN;
    /** Its skin's colours: the body, and a second (markings, cloth, bone). */
    public int skin = 0x6A8A3A, skin2 = 0x3A2A1A;

    MonsterKind(String title, int minLevel, int maxLevel, float mw, float mh, float md, float hw, float hh, float hd, Form form, Move move, Archetype archetype) {
        this.title = title; this.minLevel = minLevel; this.maxLevel = maxLevel;
        this.mw = mw; this.mh = mh; this.md = md; this.hw = hw; this.hh = hh; this.hd = hd;
        this.form = form; this.move = move; this.archetype = archetype;
    }

    private MonsterKind drives(float hunger, float fear, float aggression, float territory, float fatigue, float social, float curiosity) {
        hungerRate = hunger; fearThreshold = fear; this.aggression = aggression; this.territory = territory; fatigueRate = fatigue; this.social = social; this.curiosity = curiosity; return this;
    }
    private MonsterKind range(float range, int groupMin, int groupMax, Active active) { this.range = range; this.groupMin = groupMin; this.groupMax = groupMax; this.active = active; return this; }
    private MonsterKind senses(float sight, float hearing, float smell, float memory) { this.sight = sight; this.hearing = hearing; this.smell = smell; this.memory = memory; return this; }
    private MonsterKind gait(float walk, float run) { this.walk = walk; this.run = run; return this; }
    private MonsterKind weapon(String natural, float damage, float impact, float reach, rpg.item.ItemDef.DamageType type, String... carries) {
        this.natural = natural; this.damage = damage; this.impact = impact; this.reach = reach; this.damageType = type; this.carries = carries; return this;
    }
    private MonsterKind ranged(String what, float range) { ranged = what; rangedRange = range; return this; }
    private MonsterKind drops(String[] always, String[] common, String[] uncommon, String[] rare, String[] veryRare) {
        this.always = always; this.common = common; this.uncommon = uncommon; this.rare = rare; this.veryRare = veryRare; return this;
    }
    private MonsterKind looks(int skin, int skin2) { this.skin = skin; this.skin2 = skin2; return this; }
    static {
        // each kind its own recorded voice (Sfx's "m.<kind>.*" banks); the skeleton keeps its rattle of bones
        for (MonsterKind k : values()) {
            String v = switch (k) { case GOBLIN -> "goblin"; case ORC -> "orc"; case TROLL -> "troll"; case IMP -> "imp"; case BAT -> "bat"; case GHOST -> "ghost";
                case ZOMBIE -> "zombie"; case WEREWOLF -> "werewolf"; case DRAGON -> "dragon"; case GIANT_SPIDER -> "spider"; default -> null; };
            if (v != null) k.sounds("m." + v + ".idle", "m." + v + ".alarm", "m." + v + ".hurt", "m." + v + ".death");
        }
    }

    private MonsterKind sounds(String idle, String alarm, String hurt, String death) { idleSound = idle; alarmSound = alarm; hurtSound = hurt; deathSound = death; return this; }
    private static String[] of(String... s) { return s; }

    static {
        rpg.item.ItemDef.DamageType SLASH = rpg.item.ItemDef.DamageType.SLASH, PIERCE = rpg.item.ItemDef.DamageType.PIERCE, BLUNT = rpg.item.ItemDef.DamageType.BLUNT, MAGIC = rpg.item.ItemDef.DamageType.MAGIC;
        // Pack Raiders: tribal scavengers - cowardly alone, vicious in a mob; more aggressive at night
        GOBLIN.drives(0.7f, 0.35f, 0.55f, 0.6f, 0.4f, 0.85f, 0.45f).range(40, 3, 6, Active.ALWAYS).senses(16, 1.3f, 6, 90).gait(1.3f, 4.2f)
                .weapon("knife", 2, 8, 0.6f, PIERCE, "bronze_spear", "bronze_dagger", "stone_spear", "wooden_spear").ranged("bronze_javelin", 14)
                .drops(of("bones"), of("coins:2-18", "bronze_dagger", "bronze_spear", "bread", "raw_meat"), of("bronze_body", "bronze_spear", "wooden_shield", "guam:1-3", "plank:1-2", "cloth_roll"),
                        of("black_spear", "goblin_banner"), of("fire_staff")).looks(0x5E8A2E, 0x5A3A22)
                .sounds("creature_misc", "creature_monster", "creature_hurt", "creature_die");
        GOBLIN.woundedAt = 0.4f;
        // organised warbands: proud, rarely flee unless the chieftain falls
        ORC.drives(0.5f, 0.15f, 0.8f, 0.8f, 0.35f, 0.6f, 0.2f).range(80, 2, 5, Active.ALWAYS).senses(22, 1.2f, 10, 300).gait(1.3f, 4.6f)
                .weapon("fists", 4, 20, 0.7f, BLUNT, "iron_battleaxe", "iron_sword", "iron_axe", "steel_sword").ranged("iron_tomahawk", 12)
                .drops(of("big_bones"), of("iron_sword", "iron_body", "coins:10-60", "iron_axe"), of("iron_body", "iron_battleaxe", "war_paint", "steel_sword"),
                        of("steel_battleaxe", "bloodied_standard"), of("black_helm", "clan_relic")).looks(0x5C6E4A, 0x3A2A20)
                .sounds("creature_monster", "creature_roar", "creature_hurt", "creature_die");
        ORC.woundedAt = 0.12f;
        // a solitary brute: territorial ambusher, slow to anger, impossible to calm; regenerates; enraged below 30%
        TROLL.drives(0.7f, 0.08f, 0.6f, 0.95f, 0.5f, 0.1f, 0.05f).range(100, 1, 1, Active.DUSK_NIGHT).senses(12, 1.6f, 22, 600).gait(1.0f, 3.4f)
                .weapon("fists", 9, 70, 1.4f, BLUNT, "granite_warhammer").ranged("boulder", 16)
                .drops(of("big_bones", "troll_fat"), of("granite_mace", "rawhide:1-2", "guam:1-4"), of("granite_shield", "granite_warhammer", "iron_legs"),
                        of("cobalt_warhammer", "troll_totem"), of("troll_heart", "granite_warhammer")).looks(0x6E7268, 0x4A4A42)
                .sounds("creature_roar", "creature_roar", "creature_hurt", "creature_die");
        TROLL.regen = 0.6f; TROLL.woundedAt = 0.08f; TROLL.berserk = true; TROLL.weakness = Element.FIRE; TROLL.sydney = astratto.bridge.AstrattoNative.SYDNEY_GIANT;
        // harassers: mischievous tricksters, attracted to shiny things, fire and magic
        IMP.drives(0.4f, 0.6f, 0.5f, 0.3f, 0.3f, 0.5f, 0.95f).range(60, 2, 6, Active.DUSK_NIGHT).senses(24, 1.2f, 0, 60).gait(1.8f, 5.6f)
                .weapon("claws", 1, 4, 0.4f, SLASH).ranged("firebolt", 16)
                .drops(of("ashes"), of("coins:1-12", "beads", "trinket", "bread"), of("imp_charm", "uncut_sapphire", "magic_beads"),
                        of("imp_essence"), of("imp_contract")).looks(0xB0302A, 0x4A1A16)
                .sounds("creature_misc", "creature_misc", "creature_hurt", "creature_die");
        IMP.woundedAt = 0.5f; IMP.weakness = Element.ICE;
        BAT.drives(0.6f, 0.65f, 0.4f, 0.5f, 0.3f, 0.9f, 0.6f).range(80, 4, 8, Active.NIGHT).senses(10, 2.0f, 6, 40).gait(3.0f, 7.5f)
                .weapon("bite", 1.5f, 4, 0.5f, PIERCE).ranged("screech", 8)
                .drops(of("bat_wings"), of("small_fang", "guano"), of("wing_membrane", "coins:1-6"), of("giant_bat_eye", "sonic_essence"), of("vampiric_essence", "wing_cloak"))
                .looks(0x3A3036, 0x5A4248).sounds("creature_misc", "creature_misc", "creature_hurt", "creature_die");
        BAT.woundedAt = 0.45f; BAT.weakness = Element.FIRE;
        // a haunter: bound to a place, does not tire or hunger; escalates the longer the living remain
        GHOST.drives(0f, 0.1f, 0.4f, 1.0f, 0f, 0.1f, 0.3f).range(25, 1, 1, Active.ALWAYS).senses(20, 1.0f, 0, 400).gait(0.8f, 2.6f)
                .weapon("touch", 4, 6, 0.8f, MAGIC).ranged("spectral", 14)
                .drops(of("ectoplasm"), of("spirit_dust:1-3", "spectral_residue"), of("ghostly_cloth", "soul_essence"), of("spirit_trinket", "white_longsword"), of("bound_ghost_essence", "haunt_artifact"))
                .looks(0xC8DCE8, 0x8AA0B4).sounds("creature_misc", "creature_monster", "creature_misc", "creature_misc");
        GHOST.woundedAt = 0; GHOST.weakness = Element.FIRE;
        // bound dead: a relentless horde, drawn to sound
        ZOMBIE.drives(0.2f, 0f, 0.7f, 0.3f, 0f, 0.4f, 0f).range(50, 2, 6, Active.ALWAYS).senses(10, 1.8f, 14, 30).gait(0.7f, 1.6f)
                .weapon("claws", 4, 18, 0.7f, SLASH)
                .drops(of("rotting_remains"), of("tattered_cloth", "bronze_sword", "soul_dust"), of("iron_helm", "guam:1-2", "iron_dagger"), of("black_sword", "undead_relic"), of("phylactery_fragment", "black_helm"))
                .looks(0x6A7E5A, 0x4A4034).sounds("creature_slime", "creature_monster", "creature_slime", "creature_die");
        ZOMBIE.woundedAt = 0; ZOMBIE.weakness = Element.FIRE; ZOMBIE.sydney = astratto.bridge.AstrattoNative.SYDNEY_INFECTED;
        SKELETON.drives(0f, 0f, 0.5f, 1.0f, 0f, 0.6f, 0f).range(25, 2, 5, Active.ALWAYS).senses(18, 1.2f, 0, 60).gait(1.1f, 3.6f)
                .weapon("fists", 2, 10, 0.6f, BLUNT, "iron_sword", "iron_sword", "iron_axe", "white_mace").ranged("shortbow", 18)
                .drops(of("bones"), of("iron_sword", "iron_body", "soul_dust"), of("iron_sword", "guam", "coins:5-40"), of("champion_scroll", "white_mace"), of("white_helm", "soulbound_artifact"))
                .looks(0xE6DECA, 0x8A8070).sounds("stones", "creature_misc", "stones", "stones");
        SKELETON.woundedAt = 0;
        // an apex hunter: patient, cunning, stalks before it strikes; bolder under the full moon
        WEREWOLF.drives(0.8f, 0.4f, 0.8f, 0.6f, 0.35f, 0.4f, 0.7f).range(120, 1, 3, Active.NIGHT).senses(24, 1.6f, 30, 900).gait(1.6f, 7.2f)
                .weapon("claws", 6, 30, 0.8f, SLASH)
                .drops(of("wolf_bones", "wolf_pelt", "raw_meat"), of("claw", "fang", "bloodied_cloth"), of("silver_reagent", "quality_pelt"), of("werewolf_blood", "alpha_pelt"), of("cured_heart", "lunar_artifact"))
                .looks(0x5A4A3A, 0x8A7A62).sounds("animal.wolf", "animal.wolf.growl", "animal.dog.hurt", "animal.dog.hurt");
        WEREWOLF.woundedAt = 0.3f; WEREWOLF.sydney = astratto.bridge.AstrattoNative.SYDNEY_INFECTED;
        // the apex sovereign: rules its region; everything else reacts to it
        DRAGON.drives(0.5f, 0.05f, 0.7f, 1.0f, 0.2f, 0f, 0.4f).range(500, 1, 1, Active.ALWAYS).senses(60, 1.5f, 40, 3600).gait(2.4f, 9.0f)
                .weapon("bite", 22, 160, 3.2f, PIERCE).ranged("breath", 22)
                .drops(of("dragon_bones", "dragonhide"), of("coins:200-900", "steel_bar:2-5", "gold_bar"), of("dragon_helm", "dragon_legs", "dragon_sword", "uncut_diamond", "uncut_dragonstone", "uncut_onyx"),
                        of("draconic_visage", "dragon_soul"), of("dragon_artifact", "dragon_egg")).looks(0xB02020, 0x2A1A14)
                .sounds("creature_roar", "creature_roar", "creature_roar", "creature_roar");
        DRAGON.woundedAt = 0.4f; DRAGON.berserk = true; DRAGON.sydney = astratto.bridge.AstrattoNative.SYDNEY_GIANT;
        // an ambush predator: waits in the web, feels prey through it
        GIANT_SPIDER.drives(0.7f, 0.2f, 0.6f, 0.9f, 0.2f, 0.1f, 0.05f).range(50, 1, 3, Active.NIGHT).senses(8, 1.0f, 8, 120).gait(1.4f, 5.0f)
                .weapon("fangs", 4, 14, 1.0f, PIERCE).ranged("web", 12)
                .drops(of("spider_silk", "venom_gland"), of("coins:2-20", "carapace"), of("webbing", "spider_silk:2-4"), of("giant_spider_eye", "venom_flask"), of("queen_venom", "silk_robe"))
                .looks(0x2A2622, 0x6A3A2A).sounds("creature_misc", "creature_misc", "creature_hurt", "creature_die");
        GIANT_SPIDER.woundedAt = 0.3f; GIANT_SPIDER.weakness = Element.FIRE;
    }

    /** Active now? hour of day 0..24. */
    public boolean abroad(double hour) {
        boolean night = hour >= 20 || hour < 5.5, dusk = hour >= 17.5 && hour < 20;
        return switch (active) { case DAY -> !night; case NIGHT -> night; case DUSK_NIGHT -> night || dusk; case ALWAYS -> true; };
    }

    /** How much bigger than a person's figure (1.8 m) its own is (the humanoids' skeletons are a person's, scaled). */
    public float scale() { return mh / 1.8f; }
    public boolean flies() { return move == Move.FLY || move == Move.HOVER; }
    /**
     * How far off the sight of one throws people into fear (gamefixes: a dragon in the city should cause a genuine panic):
     * within it, whoever sees it raises the alarm, the townsfolk run for shelter and the city's fighters turn out against
     * it. 0: a monster people deal with as they come to it.
     */
    public float terror() { return switch (this) { case DRAGON -> 55; case TROLL -> 22; case WEREWOLF -> 20; case GIANT_SPIDER -> 12; default -> 0; }; }

    public boolean undead() { return this == ZOMBIE || this == SKELETON || this == GHOST; }
}
