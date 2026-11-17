def sub(p, a, b, cnt=1):
    s = open(p, encoding='utf-8').read()
    assert a in s, (p, a[:90])
    s = s.replace(a, b, cnt)
    open(p, 'w', encoding='utf-8').write(s)

SP = 'src/main/java/rpg/animal/Species.java'
sub(SP, '''    DIRE_WOLF("Dire wolf", "Cub", 1.4f, 1.75f, 0.45f, Plan.QUAD, 1.6f, 8.6f, Diet.HUNTER, Group.SOLO, 1, 1, false, true);''',
'''    DIRE_WOLF("Dire wolf", "Cub", 1.4f, 1.75f, 0.45f, Plan.QUAD, 1.6f, 8.6f, Diet.HUNTER, Group.SOLO, 1, 1, false, true),
    /**
     * (0.4.7, gamefixes: new animals that fly) the Crow: a glossy black corvid of the fields, woods and towns, in small
     * noisy flocks - quick to lift and wheel off cawing, back down to peck once the fright has passed; a scavenger first,
     * at any carcass it can find.
     */
    CROW("Crow", "Fledgling", 0.35f, 0.45f, 0.5f, Plan.BIRD, 0.8f, 2.4f, Diet.SCAVENGER, Group.FLOCK, 3, 7, false, false),
    /** (0.4.7) the Pigeon: a grey town bird of the squares and the rooftops, in flocks that clatter up together and settle on the eaves. */
    PIGEON("Pigeon", "Squab", 0.3f, 0.4f, 0.5f, Plan.BIRD, 0.7f, 2.0f, Diet.FORAGER, Group.FLOCK, 4, 9, false, false),
    /**
     * (0.4.7) the Vulture: a great dark bird of the open country with a bare head on a long neck, that circles high on
     * the warm air for hours and comes down to the dead.
     */
    VULTURE("Vulture", "Chick", 0.75f, 0.85f, 0.5f, Plan.BIRD, 0.6f, 2.2f, Diet.SCAVENGER, Group.FAMILY, 1, 3, false, false);''')
sub(SP, '''    /** How much of a walk's pace the deepest wading keeps (the water to the belly). */''', '''    /**
     * (0.4.7) A flier: it takes to the air when frightened or going any distance, cruises 'cruise' metres over the ground
     * at 'airspeed' m/s, a soarer circling on the warm air most of the day; a rooster settles high (a roof's ridge, a
     * tree's crown) rather than on the ground.
     */
    public boolean flies, soars, roostsHigh;
    public float cruise, airspeed;
    /** How much of a walk's pace the deepest wading keeps (the water to the belly). */''')
sub(SP, '''        DIRE_WOLF.prey.addAll(EnumSet.of(RABBIT, SHEEP, PIG, COW, HORSE, DEER, DUCK, CHICKEN, DOG, COYOTE, FOX, WOLF, CAT, MOUNTAIN_LION));
    }''', '''        DIRE_WOLF.prey.addAll(EnumSet.of(RABBIT, SHEEP, PIG, COW, HORSE, DEER, DUCK, CHICKEN, DOG, COYOTE, FOX, WOLF, CAT, MOUNTAIN_LION));
        // (0.4.7) the birds that fly: wary, quick to lift, light to the hand
        CROW.skills(2, 2, 2, 4, 24).weapon("peck", 1, 2, 0.3f, DamageType.PIERCE).senses(1.5f, 1.6f, 9, 7).breeds(600, 3, 5, 1440, 1440).yields(1, 0, false, 4).sounds("animal.crow", "animal.crow", "animal.crow", "animal.crow");
        PIGEON.skills(1, 1, 1, 3, 20).weapon("peck", 1, 2, 0.3f, DamageType.PIERCE).senses(1.3f, 1.3f, 5, 4).breeds(480, 2, 2, 1080, 960).yields(1, 0, false, 3).sounds("animal.pigeon", "animal.pigeon", "animal.pigeon", "animal.pigeon");
        VULTURE.skills(8, 6, 6, 12, 16).weapon("beak", 3, 8, 0.45f, DamageType.SLASH).senses(1.7f, 1.0f, 14, 10).breeds(1440, 1, 1, 4320, 2880).yields(2, 0, false, 8).sounds("animal.vulture", "animal.vulture", "animal.vulture", "animal.vulture");
        for (Species s : new Species[]{CROW, PIGEON, VULTURE}) { s.flies = true; s.weakness = rpg.combat.Spells.Element.FIRE; }
        CROW.cruise = 12; CROW.airspeed = 9;
        PIGEON.cruise = 8; PIGEON.airspeed = 10; PIGEON.roostsHigh = true;
        VULTURE.cruise = 42; VULTURE.airspeed = 11; VULTURE.soars = true; VULTURE.roostsHigh = true;
    }''')

SFX = 'src/main/java/rpg/audio/Sfx.java'
sub(SFX, '''        one("animal.duck", "animal/duck_quack_1.ogg", 0);''', '''        // (0.4.7) the flying birds' calls (made for the game: a crow's caw, a pigeon's coo, a vulture's hiss)
        for (int i = 1; i <= 3; i++) one("animal.crow", "animal/crow_caw_" + i + ".wav", 0);
        for (int i = 1; i <= 2; i++) one("animal.pigeon", "animal/pigeon_coo_" + i + ".wav", 0);
        for (int i = 1; i <= 2; i++) one("animal.vulture", "animal/vulture_hiss_" + i + ".wav", 0);
        one("animal.duck", "animal/duck_quack_1.ogg", 0);''')
print("ok")
