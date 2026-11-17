import os
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg')
p = os.path.join(SRC, 'monster', 'MonsterKind.java')
s = open(p, encoding='utf-8').read()
def sub(old, new):
    global s
    if old not in s: raise SystemExit('MISSING: ' + old[:80])
    s = s.replace(old, new, 1)

sub('''    TROLL("Troll", 60, 130, 2.5f, 3.5f, 2.0f, 1.8f, 3.0f, 1.6f, Form.HUMANOID, Move.WALK, Archetype.SOLITARY_BRUTE),''',
    '''    TROLL("Mountain troll", 60, 130, 2.5f, 3.5f, 2.0f, 1.8f, 3.0f, 1.6f, Form.HUMANOID, Move.WALK, Archetype.SOLITARY_BRUTE),''')
sub('''    GIANT_SPIDER("Giant spider", 1, 70, 1.95f, 0.975f, 2.275f, 1.625f, 0.78f, 1.82f, Form.SPIDER, Move.CLIMB, Archetype.AMBUSH_PREDATOR);''',
    '''    GIANT_SPIDER("Giant spider", 1, 70, 1.95f, 0.975f, 2.275f, 1.625f, 0.78f, 1.82f, Form.SPIDER, Move.CLIMB, Archetype.AMBUSH_PREDATOR),
    // (0.4.8, gamefixes: trolls of the ice and the river beside the mountain's; scorpions of the desert, small and large;
    // the lesser and the greater demon)
    ICE_TROLL("Ice troll", 55, 120, 2.6f, 3.6f, 2.1f, 1.8f, 3.1f, 1.6f, Form.HUMANOID, Move.WALK, Archetype.SOLITARY_BRUTE),
    RIVER_TROLL("River troll", 35, 100, 2.3f, 3.2f, 1.9f, 1.7f, 2.8f, 1.5f, Form.HUMANOID, Move.WALK, Archetype.SOLITARY_BRUTE),
    SMALL_SCORPION("Scorpion", 3, 25, 1.25f, 0.55f, 1.7f, 1.0f, 0.5f, 1.3f, Form.SPIDER, Move.WALK, Archetype.AMBUSH_PREDATOR),
    LARGE_SCORPION("Giant scorpion", 26, 50, 2.6f, 1.15f, 3.5f, 2.1f, 1.0f, 2.8f, Form.SPIDER, Move.WALK, Archetype.AMBUSH_PREDATOR),
    LESSER_DEMON("Lesser demon", 30, 80, 1.0f, 1.8f, 0.9f, 0.8f, 1.75f, 0.8f, Form.HUMANOID, Move.WALK, Archetype.APEX_HUNTER),
    GREATER_DEMON("Greater demon", 70, 120, 1.7f, 2.45f, 1.3f, 1.2f, 2.35f, 1.1f, Form.HUMANOID, Move.WALK, Archetype.SOLITARY_BRUTE);''')
sub('''            String v = switch (k) { case GOBLIN -> "goblin"; case ORC -> "orc"; case TROLL -> "troll"; case IMP -> "imp"; case BAT -> "bat"; case GHOST -> "ghost";
                case ZOMBIE -> "zombie"; case WEREWOLF -> "werewolf"; case DRAGON -> "dragon"; case GIANT_SPIDER -> "spider"; default -> null; };''',
    '''            String v = switch (k) { case GOBLIN -> "goblin"; case ORC -> "orc"; case TROLL, ICE_TROLL, RIVER_TROLL -> "troll"; case IMP -> "imp"; case BAT -> "bat"; case GHOST -> "ghost";
                case ZOMBIE -> "zombie"; case WEREWOLF -> "werewolf"; case DRAGON -> "dragon"; case GIANT_SPIDER, SMALL_SCORPION, LARGE_SCORPION -> "spider";
                case LESSER_DEMON -> "orc"; case GREATER_DEMON -> "dragon"; default -> null; };''')
sub('''        GIANT_SPIDER.woundedAt = 0.3f; GIANT_SPIDER.weakness = Element.FIRE;
    }''', '''        GIANT_SPIDER.woundedAt = 0.3f; GIANT_SPIDER.weakness = Element.FIRE;
        // (0.4.8) the ice troll: the mountain troll's kin of the high snows - slower, harder, its blows numbing; fire is its bane
        ICE_TROLL.drives(0.6f, 0.06f, 0.65f, 0.95f, 0.55f, 0.1f, 0.05f).range(100, 1, 1, Active.ALWAYS).senses(14, 1.4f, 20, 600).gait(0.95f, 3.2f)
                .weapon("fists", 10, 75, 1.45f, BLUNT, "granite_warhammer").ranged("boulder", 16)
                .drops(of("big_bones", "troll_fat"), of("granite_mace", "rawhide:1-2", "uncut_sapphire"), of("granite_shield", "granite_warhammer", "iron_legs"),
                        of("cobalt_warhammer", "troll_totem"), of("troll_heart", "granite_warhammer")).looks(0xBCCCD8, 0x7A90A6)
                .sounds("m.troll.idle", "m.troll.alarm", "m.troll.hurt", "m.troll.death");
        ICE_TROLL.regen = 0.5f; ICE_TROLL.woundedAt = 0.08f; ICE_TROLL.cunning = 0.25f; ICE_TROLL.berserk = true; ICE_TROLL.weakness = Element.FIRE; ICE_TROLL.sydney = astratto.bridge.AstrattoNative.SYDNEY_GIANT;
        // the river troll: lean and long-armed, slick with weed, at home under bridges and in the shallows; regains its
        // strength in the water and hates the bolt
        RIVER_TROLL.drives(0.75f, 0.12f, 0.6f, 0.9f, 0.45f, 0.15f, 0.2f).range(70, 1, 2, Active.DUSK_NIGHT).senses(12, 1.6f, 24, 420).gait(1.15f, 3.9f)
                .weapon("claws", 7, 50, 1.3f, SLASH, "granite_mace")
                .drops(of("big_bones", "troll_fat"), of("rawhide:1-2", "guam:1-4", "raw_meat"), of("granite_mace", "iron_legs", "coins:20-90"),
                        of("troll_totem", "cobalt_warhammer"), of("troll_heart")).looks(0x5A7048, 0x3A4A30)
                .sounds("m.troll.idle", "m.troll.alarm", "m.troll.hurt", "m.troll.death");
        RIVER_TROLL.regen = 0.8f; RIVER_TROLL.woundedAt = 0.12f; RIVER_TROLL.cunning = 0.35f; RIVER_TROLL.weakness = Element.ELECTRIC; RIVER_TROLL.sydney = astratto.bridge.AstrattoNative.SYDNEY_GIANT;
        // scorpions: desert ambushers - a pincer that seizes and holds, a sting that poisons
        SMALL_SCORPION.drives(0.65f, 0.4f, 0.55f, 0.8f, 0.3f, 0.2f, 0.1f).range(30, 1, 3, Active.DUSK_NIGHT).senses(8, 1.3f, 6, 90).gait(1.3f, 4.4f)
                .weapon("pincers", 2, 6, 0.6f, PIERCE)
                .drops(of("carapace"), of("venom_gland", "coins:1-10"), of("venom_gland:2-3"), of("venom_flask"), of("queen_venom")).looks(0x8A6A3A, 0x4A3418)
                .sounds("creature_misc", "creature_misc", "creature_hurt", "creature_die");
        SMALL_SCORPION.woundedAt = 0.3f; SMALL_SCORPION.cunning = 0.3f; SMALL_SCORPION.weakness = Element.ICE;
        LARGE_SCORPION.drives(0.7f, 0.15f, 0.7f, 0.9f, 0.3f, 0.1f, 0.05f).range(45, 1, 1, Active.ALWAYS).senses(10, 1.4f, 8, 240).gait(1.4f, 4.8f)
                .weapon("pincers", 6, 30, 1.2f, PIERCE)
                .drops(of("carapace:2-3", "venom_gland"), of("venom_gland:1-2", "coins:10-60"), of("venom_flask"), of("queen_venom"), of("queen_venom:2")).looks(0x3A2E26, 0x8A4A2A)
                .sounds("creature_misc", "creature_roar", "creature_hurt", "creature_die");
        LARGE_SCORPION.woundedAt = 0.18f; LARGE_SCORPION.cunning = 0.4f; LARGE_SCORPION.weakness = Element.ICE;
        // demons: the lesser a squat red brute that hunts in twos and threes; the greater a winged lord of the burning
        // ground, mostly afoot, lifting off it on its wings now and then
        LESSER_DEMON.drives(0.7f, 0.12f, 0.9f, 0.7f, 0.3f, 0.5f, 0.3f).range(70, 1, 3, Active.ALWAYS).senses(22, 1.4f, 14, 600).gait(1.4f, 5.4f)
                .weapon("claws", 6, 26, 0.8f, SLASH, "iron_sword", "steel_sword", "iron_axe").ranged("firebolt", 14)
                .drops(of("ashes", "bones"), of("coins:20-120", "imp_charm"), of("imp_essence", "steel_sword", "uncut_ruby"), of("imp_contract"), of("black_helm")).looks(0xA82A22, 0xD8CCA8)
                .sounds("m.orc.idle", "m.orc.alarm", "m.orc.hurt", "m.orc.death");
        LESSER_DEMON.woundedAt = 0.1f; LESSER_DEMON.cunning = 0.55f; LESSER_DEMON.weakness = Element.ICE; LESSER_DEMON.berserk = true;
        GREATER_DEMON.drives(0.5f, 0.04f, 0.85f, 1.0f, 0.25f, 0.1f, 0.2f).range(160, 1, 1, Active.ALWAYS).senses(30, 1.4f, 20, 1800).gait(1.3f, 5.0f)
                .weapon("claws", 15, 90, 1.6f, SLASH, "steel_battleaxe", "black_sword").ranged("firebolt", 18)
                .drops(of("big_bones", "ashes"), of("coins:200-700", "imp_essence:2-4"), of("steel_battleaxe", "uncut_ruby", "uncut_diamond"), of("imp_contract", "black_helm"), of("dragon_artifact")).looks(0x8A1A18, 0x2A1A14)
                .sounds("m.dragon.idle", "m.dragon.alarm", "m.dragon.hurt", "m.dragon.death");
        GREATER_DEMON.woundedAt = 0.05f; GREATER_DEMON.cunning = 0.6f; GREATER_DEMON.weakness = Element.WATER; GREATER_DEMON.berserk = true; GREATER_DEMON.sydney = astratto.bridge.AstrattoNative.SYDNEY_GIANT;
    }''')
sub('''    public boolean seesInDark() { return this == GHOST || this == IMP || this == BAT || this == WEREWOLF; }''',
    '''    public boolean seesInDark() { return this == GHOST || this == IMP || this == BAT || this == WEREWOLF || demon() || scorpion(); }
    /** (0.4.8) The troll kin: the mountain's, the ice's and the river's - one build, three hides. */
    public boolean troll() { return this == TROLL || this == ICE_TROLL || this == RIVER_TROLL; }
    /** (0.4.8) The scorpions: a spider's eight legs, a pair of pincers and a sting over the back. */
    public boolean scorpion() { return this == SMALL_SCORPION || this == LARGE_SCORPION; }
    /** (0.4.8) The demons. */
    public boolean demon() { return this == LESSER_DEMON || this == GREATER_DEMON; }
    /** (0.4.8) How many looks a kind comes in (its figure varied by the body's own seed, MonsterModels): demons and trolls several. */
    public int variants() { return switch (this) { case LESSER_DEMON -> 3; case GREATER_DEMON -> 3; case TROLL, ICE_TROLL, RIVER_TROLL -> 2; default -> 1; }; }''')
sub('''    public float terror() { return switch (this) { case DRAGON -> 55; case TROLL -> 22; case WEREWOLF -> 20; case GIANT_SPIDER -> 12; default -> 0; }; }''',
    '''    public float terror() { return switch (this) { case DRAGON -> 55; case GREATER_DEMON -> 40; case TROLL, ICE_TROLL, RIVER_TROLL -> 22; case WEREWOLF -> 20; case LESSER_DEMON -> 16; case GIANT_SPIDER, LARGE_SCORPION -> 12; default -> 0; }; }''')
sub(''' * The eleven monsters (gamefixes, GDD 3.14) as data:''', ''' * The monsters (gamefixes, GDD 3.14; 0.4.8 the ice and river trolls, the scorpions, the demons) as data:''')
open(p, 'w', encoding='utf-8', newline='').write(s)
print('kinds')
