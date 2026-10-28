package rpg.audio;

import astratto.audio.Codecs;
import astratto.bridge.AstrattoNative;
import astratto.game.Game;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import rpg.actor.Actor;
import rpg.world.Blocks;

/**
 * The game's sounds, played through the engine's mixer: positional ones in the world (occluded and reverberated by the
 * engine) and interface ones on the UI bus. Recorded sounds come from the game's resources (sounds/), each a bank of
 * variations picked at random and pitched a little, so nothing repeats exactly:
 *
 * <ul>
 *   <li>ui/ - the interface, quests, trading, loot, the level-up fanfare (the game's own recordings)</li>
 *   <li>voice/ - human voices: the only recorded voices the game has are its death cries, male and female; a hurt
 *       grunt is the start of one, cut short and pitched (GDD: no NPC dialogue is voiced - speech is text)</li>
 *   <li>foley/ - footsteps by surface (grass, earth, stone, wood, cloth), impacts by material, doors, cloth, coins</li>
 *   <li>sfx/, swish/ - blades, swings, spells, creatures, stones and wood</li>
 *   <li>devmapambience.ogg - the devmap's ambience, looped</li>
 * </ul>
 * See sounds/CREDITS.txt for recording sources and individual licenses. All sounds use recorded assets; missing assets are logged without synthetic replacements.
 */
public final class Sfx {
    private static final int RATE = 44100;
    private final Game game;
    private final Map<String, List<Long>> banks = new HashMap<>();
    private final Random rnd = new Random(9);
    public int played, loaded;
    private long ambience;

    private static Sfx shared;
    /** One sound bank per engine (the character creator plays through it before the game world exists). */
    public static synchronized Sfx shared(Game game) {
        if (shared == null || shared.game != game) {
            if (warming != null) warming.join();   // (what the background pass has not decoded yet would be decoded twice)
            shared = new Sfx(game);
        }
        return shared;
    }

    /**
     * The bank for an interface click: only once the recordings are decoded (gamefixes: the game nearly choked when a
     * profile was deleted - the title's first click built the whole bank, decoding every recording, for five seconds on
     * the frame). Before that, the click is silent.
     */
    public static synchronized Sfx ready(Game game) {
        if (shared != null && shared.game == game) return shared;
        return warming != null && warming.isDone() ? shared(game) : null;
    }

    // ------------------------------------------------------------------ decoding ahead
    /** Every recording decoded once, by path (and how much of it is kept), shared by every bank built after. */
    private static final java.util.concurrent.ConcurrentHashMap<String, float[]> decoded = new java.util.concurrent.ConcurrentHashMap<>();
    private static java.util.concurrent.CompletableFuture<Void> warming;
    /** A decode-only pass (no engine): it walks the same list of recordings and only fills {@link #decoded}. */
    private final List<java.util.concurrent.Future<?>> decodes;
    private final java.util.concurrent.ExecutorService decoders;

    /** Starts decoding every recording on background threads (at launch), so building a bank later costs no decoding. */
    public static synchronized void warm() {
        if (warming != null) return;
        warming = java.util.concurrent.CompletableFuture.runAsync(() -> {
            int n = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(n, r -> { Thread t = new Thread(r, "sfx-decode"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); return t; });
            try {
                Sfx pass = new Sfx(null, pool);
                for (var f : pass.decodes) try { f.get(); } catch (Exception e) { /* reported where it failed */ }
            } finally { pool.shutdown(); }
        });
    }

    private static float[] decode(String path, float maxSeconds) throws IOException, Codecs.Failure {
        String key = path + "@" + maxSeconds;
        float[] w = decoded.get(key);
        if (w == null) {
            try (InputStream in = Sfx.class.getResourceAsStream("/sounds/" + path)) {
                if (in == null) return null;
                w = Codecs.decode(in.readAllBytes(), path, maxSeconds).mono();
            }
            decoded.put(key, w);
        }
        return w.clone();
    }

    public Sfx(Game game) { this(game, null); }

    private Sfx(Game game, java.util.concurrent.ExecutorService decoders) {
        this.game = game;
        this.decoders = decoders;
        this.decodes = decoders == null ? null : new ArrayList<>();
        // ---- recorded: the world
        bank("step.grass", "foley/footstep_grass_00", 5);
        bank("step.earth", "foley/footstep0", 10);
        bank("step.stone", "foley/footstep_concrete_00", 5);
        bank("step.wood", "foley/footstep_wood_00", 5);
        bank("step.cloth", "foley/footstep_carpet_00", 5);
        bank("step.snow", "foley/footstep_snow_00", 5);
        // combat (gamefixes: recorded swings and impacts). Bare-handed swings keep the swish pack; weapons swing with
        // the recorded blades and hafts of "Medieval sound effects - Weapon Textures" (CC0); flesh, steel and shields
        // are struck with 0 A.D.'s recorded impacts (Wildfire Games, CC BY-SA 3.0)
        bank("swing", "swish/swish-", 13, ".wav", 1);
        for (String w : new String[]{"katana", "sabre", "seax"}) bank("swing.blade", "combat/swing_" + w + "_0", 6, ".ogg", 1);
        bank("swing.dagger", "combat/swing_dagger_0", 6, ".ogg", 1);
        bank("swing.heavy", "combat/swing_axe_0", 7, ".ogg", 1);
        bank("hit", "foley/impactPunch_medium_00", 5);
        bank("heavyhit", "foley/impactPunch_heavy_00", 5);
        for (String f : new String[]{"fleshimp_10", "fleshimp_11", "fleshimp_12"}) one("hit.blunt", "combat/" + f + ".ogg", 0);
        for (String f : new String[]{"fleshstab_01", "fleshstab_02", "fleshstab_03", "fleshimp_10", "fleshimp_11"}) one("hit.blade", "combat/" + f + ".ogg", 0);
        for (String f : new String[]{"10", "11", "11b", "12", "12b", "13a", "14a", "15", "15a", "16", "17a", "18", "19a"}) one("block", "combat/swordhit_" + f + ".ogg", 0);
        for (String f : new String[]{"10a", "12", "13b", "16a", "16b", "17", "19"}) one("parry", "combat/swordhit_" + f + ".ogg", 0);
        numbered("shield.wood", "combat/shield_wood_", 15);
        numbered("shield.metal", "combat/shield_metal_", 15);
        bank("throw", "combat/jav_attack_0", 4, ".ogg", 1);
        bank("draw", "combat/draw_katana_0", 4, ".ogg", 1); bank("draw", "combat/draw_sabre_0", 4, ".ogg", 1); bank("draw", "combat/draw_dagger_0", 3, ".ogg", 1);
        bank("sheath", "combat/sheath_sabre_0", 4, ".ogg", 1); bank("sheath", "combat/sheath_seax_0", 3, ".ogg", 2);
        bank("plate", "foley/impactPlate_medium_00", 5);
        bank("wood.light", "foley/impactWood_light_00", 5);
        bank("wood", "foley/impactWood_medium_00", 5);
        bank("wood.heavy", "foley/impactWood_heavy_00", 5);
        bank("plank", "foley/impactPlank_medium_00", 5);
        bank("metal.light", "foley/impactMetal_light_00", 5);
        bank("soft", "foley/impactSoft_medium_00", 5);
        bank("soft.heavy", "foley/impactSoft_heavy_00", 5);
        bank("generic", "foley/impactGeneric_light_00", 5);
        // glass: recorded panes breaking (Minetest Game, CC BY 3.0, see CREDITS) and a recorded tap on glass
        one("glass.light", "glass/tap.ogg", 0);
        for (int i = 1; i <= 3; i++) one("glass.break", "glass/break_" + i + ".ogg", 0);
        bank("mine", "foley/impactMining_00", 5);
        bank("chop", "foley/chop", 1, ".ogg", -1);
        bank("door", "foley/doorOpen_", 2, ".ogg", 1);
        bank("door.close", "foley/doorClose_", 4, ".ogg", 1);
        bank("creak", "foley/creak", 3, ".ogg", 1);
        bank("cloth", "foley/cloth", 4, ".ogg", 1);
        bank("coins", "foley/handleCoins", 1, ".ogg", -1);
        bank("coins", "foley/handleCoins2", 1, ".ogg", -1);
        bank("pickup", "foley/handleSmallLeather", 1, ".ogg", -1);
        bank("pickup", "foley/handleSmallLeather2", 1, ".ogg", -1);
        bank("drop", "foley/dropLeather", 1, ".ogg", -1);
        bank("lock", "sfx/lock_0", 3, ".ogg", 1);
        bank("book", "foley/bookFlip", 3, ".ogg", 1);
        bank("stone", "sfx/stones_0", 4, ".ogg", 1);
        // the monsters' voices, under the names their kinds give them (MonsterKind.sounds): they were registered as
        // "growl" and "roar" while every monster asked for "creature_misc" and "creature_roar" - and so made no sound
        bank("creature_misc", "sfx/creature_misc_0", 8, ".ogg", 1);
        bank("creature_monster", "sfx/creature_monster_0", 4, ".ogg", 1);
        bank("creature_hurt", "sfx/creature_hurt_0", 2, ".ogg", 1);
        bank("creature_die", "sfx/creature_die_0", 1, ".ogg", 1);
        bank("creature_roar", "sfx/creature_roar_0", 3, ".ogg", 1);
        bank("creature_slime", "sfx/creature_slime_0", 4, ".ogg", 1);
        // each monster's own voice (gamefixes: they all cried out like people): idle, alarm, hurt and death banks, from
        // rubberduck's two "80 CC0 creature SFX" packs (CC0), qubodup's ghost moans (CC0) and jalastram's monster and
        // creature voices (CC BY 3.0) - see CREDITS.txt
        one("m.goblin.idle", "creature/rd1_grunt_01.ogg", 0);
        one("m.goblin.idle", "creature/rd1_grunt_02.ogg", 0);
        one("m.goblin.idle", "creature/rd1_grunt_03.ogg", 0);
        one("m.goblin.idle", "creature/rd1_grunt_04.ogg", 0);
        one("m.goblin.idle", "creature/rd1_grunt_05.ogg", 0);
        one("m.goblin.alarm", "creature/rd2_attack_01.ogg", 0);
        one("m.goblin.alarm", "creature/rd2_attack_02.ogg", 0);
        one("m.goblin.alarm", "creature/rd2_attack_03.ogg", 0);
        one("m.goblin.alarm", "creature/rd2_attack_04.ogg", 0);
        one("m.goblin.alarm", "creature/rd2_attack_05.ogg", 0);
        one("m.goblin.hurt", "creature/rd1_hurt_01.ogg", 0);
        one("m.goblin.hurt", "creature/rd1_hurt_02.ogg", 0);
        one("m.goblin.hurt", "creature/rd1_hurt_03.ogg", 0);
        one("m.goblin.hurt", "creature/rd1_hurt_04.ogg", 0);
        one("m.goblin.hurt", "creature/rd1_hurt_05.ogg", 0);
        one("m.goblin.death", "creature/rd2_die_01.ogg", 0);
        one("m.goblin.death", "creature/rd2_die_02.ogg", 0);
        one("m.goblin.death", "creature/rd2_die_03.ogg", 0);
        one("m.goblin.death", "creature/rd2_die_04.ogg", 0);
        one("m.orc.idle", "creature/rd2_grunt_06.ogg", 0);
        one("m.orc.idle", "creature/rd2_grunt_07.ogg", 0);
        one("m.orc.idle", "creature/rd2_grunt_08.ogg", 0);
        one("m.orc.idle", "creature/rd2_grunt_09.ogg", 0);
        one("m.orc.alarm", "creature/rd2_roar_04.ogg", 0);
        one("m.orc.alarm", "creature/rd2_roar_05.ogg", 0);
        one("m.orc.alarm", "creature/rd2_roar_06.ogg", 0);
        one("m.orc.alarm", "creature/jalastram_001.mp3", 0);
        one("m.orc.alarm", "creature/jalastram_002.mp3", 0);
        one("m.orc.alarm", "creature/jalastram_003.mp3", 0);
        one("m.orc.hurt", "creature/rd1_hurt_01.ogg", 0);
        one("m.orc.hurt", "creature/rd1_hurt_02.ogg", 0);
        one("m.orc.hurt", "creature/rd1_hurt_03.ogg", 0);
        one("m.orc.hurt", "creature/rd1_hurt_04.ogg", 0);
        one("m.orc.hurt", "creature/rd1_hurt_05.ogg", 0);
        one("m.orc.death", "creature/rd2_die_01.ogg", 0);
        one("m.orc.death", "creature/rd2_die_02.ogg", 0);
        one("m.orc.death", "creature/rd2_die_03.ogg", 0);
        one("m.orc.death", "creature/rd2_die_04.ogg", 0);
        one("m.troll.idle", "creature/rd1_troll_01.ogg", 0);
        one("m.troll.idle", "creature/rd1_troll_02.ogg", 0);
        one("m.troll.idle", "creature/rd1_troll_03.ogg", 0);
        one("m.troll.alarm", "creature/rd1_roar_01.ogg", 0);
        one("m.troll.alarm", "creature/rd1_roar_02.ogg", 0);
        one("m.troll.alarm", "creature/rd1_roar_03.ogg", 0);
        one("m.troll.hurt", "creature/rd1_troll_01.ogg", 0);
        one("m.troll.hurt", "creature/rd1_troll_02.ogg", 0);
        one("m.troll.hurt", "creature/rd1_troll_03.ogg", 0);
        one("m.troll.death", "creature/rd2_die_01.ogg", 0);
        one("m.troll.death", "creature/rd2_die_02.ogg", 0);
        one("m.troll.death", "creature/rd2_die_03.ogg", 0);
        one("m.troll.death", "creature/rd2_die_04.ogg", 0);
        one("m.imp.idle", "creature/rd1_weird_01.ogg", 0);
        one("m.imp.idle", "creature/rd1_weird_02.ogg", 0);
        one("m.imp.idle", "creature/rd1_weird_03.ogg", 0);
        one("m.imp.idle", "creature/rd1_weird_04.ogg", 0);
        one("m.imp.idle", "creature/rd1_weird_05.ogg", 0);
        one("m.imp.alarm", "creature/rd1_scream_01.ogg", 0);
        one("m.imp.alarm", "creature/rd1_scream_02.ogg", 0);
        one("m.imp.alarm", "creature/rd1_spit_01.ogg", 0);
        one("m.imp.alarm", "creature/rd1_spit_02.ogg", 0);
        one("m.imp.alarm", "creature/rd1_spit_03.ogg", 0);
        one("m.imp.hurt", "creature/rd2_weird_06.ogg", 0);
        one("m.imp.hurt", "creature/rd2_weird_07.ogg", 0);
        one("m.imp.hurt", "creature/rd2_weird_08.ogg", 0);
        one("m.imp.hurt", "creature/rd2_weird_09.ogg", 0);
        one("m.imp.death", "creature/rd2_die_01.ogg", 0);
        one("m.imp.death", "creature/rd2_die_02.ogg", 0);
        one("m.imp.death", "creature/rd2_die_03.ogg", 0);
        one("m.imp.death", "creature/rd2_die_04.ogg", 0);
        one("m.ghost.idle", "creature/ghostmoan_01.mp3", 0);
        one("m.ghost.idle", "creature/ghostmoan_02.mp3", 0);
        one("m.ghost.idle", "creature/ghostmoan_03.mp3", 0);
        one("m.ghost.idle", "creature/ghostmoan_04.mp3", 0);
        one("m.ghost.idle", "creature/ghostmoan_05.mp3", 0);
        one("m.ghost.alarm", "creature/ghostmoan_01.mp3", 0);
        one("m.ghost.alarm", "creature/ghostmoan_02.mp3", 0);
        one("m.ghost.alarm", "creature/ghostmoan_03.mp3", 0);
        one("m.ghost.alarm", "creature/ghostmoan_04.mp3", 0);
        one("m.ghost.alarm", "creature/ghostmoan_05.mp3", 0);
        one("m.ghost.hurt", "creature/ghostmoan_01.mp3", 0);
        one("m.ghost.hurt", "creature/ghostmoan_02.mp3", 0);
        one("m.ghost.hurt", "creature/ghostmoan_03.mp3", 0);
        one("m.ghost.hurt", "creature/ghostmoan_04.mp3", 0);
        one("m.ghost.hurt", "creature/ghostmoan_05.mp3", 0);
        one("m.ghost.death", "creature/ghostmoan_01.mp3", 0);
        one("m.ghost.death", "creature/ghostmoan_02.mp3", 0);
        one("m.ghost.death", "creature/ghostmoan_03.mp3", 0);
        one("m.ghost.death", "creature/ghostmoan_04.mp3", 0);
        one("m.ghost.death", "creature/ghostmoan_05.mp3", 0);
        one("m.zombie.idle", "creature/rd2_monster_08.ogg", 0);
        one("m.zombie.idle", "creature/rd2_monster_09.ogg", 0);
        one("m.zombie.idle", "creature/rd2_monster_10.ogg", 0);
        one("m.zombie.idle", "creature/rd2_monster_11.ogg", 0);
        one("m.zombie.idle", "creature/rd2_monster_12.ogg", 0);
        one("m.zombie.idle", "creature/rd2_monster_13.ogg", 0);
        one("m.zombie.idle", "creature/rd2_monster_14.ogg", 0);
        one("m.zombie.alarm", "creature/rd2_monster_15.ogg", 0);
        one("m.zombie.alarm", "creature/rd2_monster_16.ogg", 0);
        one("m.zombie.alarm", "creature/rd2_monster_17.ogg", 0);
        one("m.zombie.alarm", "creature/rd2_monster_18.ogg", 0);
        one("m.zombie.alarm", "creature/rd2_monster_19.ogg", 0);
        one("m.zombie.alarm", "creature/rd2_monster_20.ogg", 0);
        one("m.zombie.hurt", "creature/rd2_slime_01.ogg", 0);
        one("m.zombie.hurt", "creature/rd2_slime_02.ogg", 0);
        one("m.zombie.hurt", "creature/rd2_slime_03.ogg", 0);
        one("m.zombie.hurt", "creature/rd2_slime_04.ogg", 0);
        one("m.zombie.death", "creature/rd1_burble_01.ogg", 0);
        one("m.zombie.death", "creature/rd1_burble_02.ogg", 0);
        one("m.bat.idle", "creature/rd1_cute_01.ogg", 0);
        one("m.bat.idle", "creature/rd1_cute_02.ogg", 0);
        one("m.bat.idle", "creature/rd1_cute_03.ogg", 0);
        one("m.bat.idle", "creature/rd1_cute_04.ogg", 0);
        one("m.bat.idle", "creature/rd1_cute_05.ogg", 0);
        one("m.bat.alarm", "creature/rd1_cute_06.ogg", 0);
        one("m.bat.alarm", "creature/rd1_cute_07.ogg", 0);
        one("m.bat.alarm", "creature/rd1_cute_08.ogg", 0);
        one("m.bat.alarm", "creature/rd1_cute_09.ogg", 0);
        one("m.bat.alarm", "creature/rd1_cute_10.ogg", 0);
        one("m.bat.hurt", "creature/rd1_cute_01.ogg", 0);
        one("m.bat.hurt", "creature/rd1_cute_02.ogg", 0);
        one("m.bat.hurt", "creature/rd1_cute_03.ogg", 0);
        one("m.bat.hurt", "creature/rd1_cute_04.ogg", 0);
        one("m.bat.hurt", "creature/rd1_cute_05.ogg", 0);
        one("m.bat.hurt", "creature/rd1_cute_06.ogg", 0);
        one("m.bat.hurt", "creature/rd1_cute_07.ogg", 0);
        one("m.bat.hurt", "creature/rd1_cute_08.ogg", 0);
        one("m.bat.hurt", "creature/rd1_cute_09.ogg", 0);
        one("m.bat.hurt", "creature/rd1_cute_10.ogg", 0);
        one("m.bat.death", "creature/rd1_cute_06.ogg", 0);
        one("m.bat.death", "creature/rd1_cute_07.ogg", 0);
        one("m.bat.death", "creature/rd1_cute_08.ogg", 0);
        one("m.bat.death", "creature/rd1_cute_09.ogg", 0);
        one("m.bat.death", "creature/rd1_cute_10.ogg", 0);
        one("m.spider.idle", "creature/rd1_bug_01.ogg", 0);
        one("m.spider.idle", "creature/rd1_bug_02.ogg", 0);
        one("m.spider.idle", "creature/rd1_bug_03.ogg", 0);
        one("m.spider.idle", "creature/rd1_bug_04.ogg", 0);
        one("m.spider.alarm", "creature/rd2_bug_05.ogg", 0);
        one("m.spider.alarm", "creature/rd2_bug_06.ogg", 0);
        one("m.spider.alarm", "creature/rd2_bug_07.ogg", 0);
        one("m.spider.alarm", "creature/rd2_bug_08.ogg", 0);
        one("m.spider.alarm", "creature/rd2_bug_09.ogg", 0);
        one("m.spider.alarm", "creature/rd2_bug_10.ogg", 0);
        one("m.spider.alarm", "creature/rd2_bug_11.ogg", 0);
        one("m.spider.alarm", "creature/rd2_bug_12.ogg", 0);
        one("m.spider.alarm", "creature/rd2_bug_13.ogg", 0);
        one("m.spider.hurt", "creature/rd1_spit_01.ogg", 0);
        one("m.spider.hurt", "creature/rd1_spit_02.ogg", 0);
        one("m.spider.hurt", "creature/rd1_spit_03.ogg", 0);
        one("m.spider.death", "creature/rd2_bug_05.ogg", 0);
        one("m.spider.death", "creature/rd2_bug_06.ogg", 0);
        one("m.spider.death", "creature/rd2_bug_07.ogg", 0);
        one("m.spider.death", "creature/rd2_bug_08.ogg", 0);
        one("m.werewolf.idle", "creature/jalastram_004.mp3", 0);
        one("m.werewolf.idle", "creature/jalastram_005.mp3", 0);
        one("m.werewolf.idle", "creature/jalastram_006.mp3", 0);
        one("m.werewolf.alarm", "creature/rd1_howl.ogg", 0);
        one("m.werewolf.hurt", "creature/rd1_hurt_01.ogg", 0);
        one("m.werewolf.hurt", "creature/rd1_hurt_02.ogg", 0);
        one("m.werewolf.hurt", "creature/rd1_hurt_03.ogg", 0);
        one("m.werewolf.hurt", "creature/rd1_hurt_04.ogg", 0);
        one("m.werewolf.hurt", "creature/rd1_hurt_05.ogg", 0);
        one("m.werewolf.death", "creature/rd2_die_01.ogg", 0);
        one("m.werewolf.death", "creature/rd2_die_02.ogg", 0);
        one("m.werewolf.death", "creature/rd2_die_03.ogg", 0);
        one("m.werewolf.death", "creature/rd2_die_04.ogg", 0);
        one("m.dragon.idle", "creature/jalastram_007.mp3", 0);
        one("m.dragon.idle", "creature/jalastram_008.mp3", 0);
        one("m.dragon.alarm", "creature/rd1_roar_01.ogg", 0);
        one("m.dragon.alarm", "creature/rd1_roar_02.ogg", 0);
        one("m.dragon.alarm", "creature/rd1_roar_03.ogg", 0);
        one("m.dragon.alarm", "creature/rd2_roar_04.ogg", 0);
        one("m.dragon.alarm", "creature/rd2_roar_05.ogg", 0);
        one("m.dragon.alarm", "creature/rd2_roar_06.ogg", 0);
        one("m.dragon.alarm", "creature/jalastram_009.mp3", 0);
        one("m.dragon.alarm", "creature/jalastram_010.mp3", 0);
        one("m.dragon.hurt", "creature/rd1_roar_01.ogg", 0);
        one("m.dragon.hurt", "creature/rd1_roar_02.ogg", 0);
        one("m.dragon.hurt", "creature/rd1_roar_03.ogg", 0);
        one("m.dragon.death", "creature/rd2_roar_04.ogg", 0);
        one("m.dragon.death", "creature/rd2_roar_05.ogg", 0);
        one("m.dragon.death", "creature/rd2_roar_06.ogg", 0);
        // a body falling (a ragdoll striking the ground): dklon's recorded body hits (CC BY 3.0)
        numberedWav("body.fall", "foley/bodyfall_", 5);
        bank("stones", "sfx/stones_0", 4, ".ogg", 1);
        // ---- recorded: the animals (gamefixes: every species heard). Long field recordings are cut into their separate
        // calls by loudness (see calls()), so a pond of ducks or a pack's howling becomes a bank of single cries.
        bank("animal.cow", "animal/cow_", 4, ".ogg", 1); calls("animal.cow", "animal/cow_field.ogg", 4, 0.6f, 3.5f);
        calls("animal.chicken", "animal/henhouse.ogg", 8, 0.15f, 1.6f); calls("animal.chicken", "animal/hens_lay.ogg", 8, 0.15f, 1.6f); calls("animal.chicken", "animal/hen_lays.ogg", 6, 0.15f, 1.6f);
        one("animal.rooster", "animal/rooster_1.ogg", 0); one("animal.rooster", "animal/rooster_2.ogg", 0);
        calls("animal.chicken.alarm", "animal/hen_annoyed.ogg", 4, 0.1f, 1.5f);
        calls("animal.pig", "animal/pig_grunt_1.ogg", 2, 0.1f, 1.2f); calls("animal.pig", "animal/pig_grunt_2.ogg", 2, 0.1f, 1.2f); calls("animal.pig", "animal/pig_grunt_3.ogg", 2, 0.1f, 1.2f);
        calls("animal.pig.alarm", "animal/pig_grumpy_1.ogg", 4, 0.2f, 2.5f); calls("animal.pig.alarm", "animal/pig_grumpy_2.ogg", 3, 0.2f, 2.5f);
        one("animal.horse", "animal/horse_breath.ogg", 0); one("animal.horse", "animal/horse_nicker.ogg", 0);
        for (int i = 1; i <= 5; i++) one("animal.horse.alarm", "animal/horse_neigh_" + i + ".ogg", 0);
        for (String sh : new String[]{"2343", "2344", "2345", "2348", "2349"}) one("animal.sheep", "animal/sheep_" + sh + ".ogg", 0);
        calls("animal.dog", "animal/dog_bark_1.ogg", 4, 0.08f, 1.0f); calls("animal.dog", "animal/dog_bark_2.ogg", 2, 0.08f, 1.0f); calls("animal.dog", "animal/dog_barks.ogg", 6, 0.08f, 1.0f);
        calls("animal.dog.hurt", "animal/dog_bark_2.ogg", 2, 0.08f, 0.6f);
        one("animal.cat", "animal/cat_meow_1.ogg", 0); one("animal.cat", "animal/cat_meow_2.ogg", 0); calls("animal.cat", "animal/cat_meow_3.ogg", 4, 0.2f, 2f);
        calls("animal.cat.growl", "animal/cat_growl.ogg", 3, 0.3f, 3f); one("animal.cat.growl", "animal/cat_roar.ogg", 0);
        // the mountain lion: the same feline throat, much bigger (pitched down; see pitch())
        calls("animal.lion", "animal/cat_growl.ogg", 3, 0.3f, 3f); one("animal.lion", "animal/cat_roar.ogg", 0);
        calls("animal.duck", "animal/ducks.ogg", 12, 0.12f, 1.5f);
        calls("animal.wolf", "animal/wolf_howls.ogg", 6, 1.0f, 7f);
        calls("animal.wolf.growl", "animal/cat_growl.ogg", 3, 0.3f, 3f);
        calls("animal.coyote", "animal/coyotes_pack.ogg", 8, 0.4f, 5f);
        calls("animal.bear", "animal/bear_vocal.mp3", 10, 0.3f, 4f); calls("animal.bear", "animal/bear_roar.mp3", 3, 0.4f, 4f);
        calls("animal.rabbit.hurt", "animal/cat_meow_2.ogg", 1, 0.05f, 1f);
        for (int i = 1; i <= 3; i++) one("animal.fox.death", "animal/fox_death" + i + ".ogg", 0);
        calls("animal.fox", "animal/fox_death1.ogg", 2, 0.1f, 0.8f);
        pitch.put("animal.lion", 0.58f); pitch.put("animal.wolf.growl", 0.62f); pitch.put("animal.rabbit.hurt", 1.7f); pitch.put("animal.dog.hurt", 1.25f); pitch.put("animal.fox", 1.15f);
        // ---- recorded: voices (death cries only; hurt is the first moment of one)
        for (HumanVoice voice : HumanVoice.values()) for (String recording : voice.recordings) {
            one(voice.deathBank, recording, 0);
            one(voice.hurtBank, recording, 0.32f);
        }
        // ---- recorded: the interface (the game's own)
        for (String f : new String[]{"AchievementMenuClose", "AchievementMenuOpen", "ContainerOpen", "FishReelIn", "FishingHooked", "LevelUp", "LogoutProfileButton",
                "LootCoinSmall", "MenuScreenOpen", "NewProfileButton", "QuestActivate", "QuestActivate2", "QuestComplete", "QuestFailed", "QuestMenuOpen",
                "SpellBookCloseA", "UI_Trading_ItemSelect", "UI_hover", "UI_select", "UI_windowclose", "UIbutton1", "UIbutton2",
                // the jingles (gamefixes: the provided music, each in its place)
                "Skill_levelup", "Combat_levelup", "Max_Combat_levelup", "Level_100_SkillMusic", "Death"})
            one("ui." + f, "ui/" + f + ".ogg", 0);
        one("thunder","elements/thunder.ogg",6);
        one("crackle", "elements/fire.ogg", 2);
        for (int i = 1; i <= 3; i++) one("splash", "elements/water" + i + ".ogg", 0);
        // archery (gamefixes: proper bow and crossbow sounds): the longbow's and the recurve's own releases and draws, a
        // crossbow's shot and its cocking, arrows and bolts passing - all "Medieval sound effects - Weapon Textures" (CC0);
        // arrows striking wood, stone, earth, flesh and armour are 0 A.D.'s (Wildfire Games, CC BY-SA 3.0)
        bank("bow", "archery/longbow_shoot_0", 6, ".ogg", 1); bank("bow", "archery/recurve_shoot_0", 6, ".ogg", 1);
        bank("crossbow", "archery/crossbow_0", 6, ".ogg", 1);
        bank("crossbow.load", "archery/crossbow_cock_0", 3, ".ogg", 1); bank("crossbow.load", "archery/crossbow_load_0", 4, ".ogg", 1);
        bank("bow.draw", "archery/bow_draw_0", 6, ".ogg", 1); bank("bow.draw", "archery/recurve_draw_0", 6, ".ogg", 1);
        bank("bow.nock", "archery/nock_0", 4, ".ogg", 1);
        bank("arrow.fly", "archery/arrow_pass_0", 4, ".ogg", 1); bank("arrow.fly", "archery/bolt_pass_0", 3, ".ogg", 1);
        numbered("arrow.wood", "archery/arrow_wood_", 12);
        numbered("arrow.stone", "archery/arrow_stone_", 12);
        numbered("arrow.earth", "archery/arrow_dirt_", 12);
        numbered("arrow.metal", "archery/arrow_metal_", 12);
        bank("arrow.flesh", "archery/arrow_flesh_0", 6, ".ogg", 1);
        alias("zap", "metal.light"); alias("click", "ui.UIbutton1"); alias("fail", "ui.UIbutton2");
        alias("eat", "cloth"); alias("bless", "ui.LevelUp");
        bank("cast", "sfx/spell_0", 2, ".ogg", 1); alias("fire", "crackle"); if (!banks.containsKey("body.fall")) alias("body.fall", "soft.heavy");
        alias("monster.dissolve", "crackle"); alias("rope", "wood.light");   // remains going to ash; a snare snapping shut
        if (System.getenv("RPG_SFX_REPORT") != null) {
            java.util.TreeMap<String, Integer> t = new java.util.TreeMap<>();
            for (var e : banks.entrySet()) if (e.getKey().startsWith("animal.")) t.put(e.getKey(), e.getValue().size());
            System.out.println("[sfx] animal banks " + t);
        }
    }

    // ------------------------------------------------------------------ loading
    /** n variations: prefix + 0..n-1 (from 0, or from 1 with first = 1) + extension; first = -1: the prefix is the whole stem. */
    private void bank(String name, String prefix, int n, String ext, int first) {
        if (first < 0) { one(name, prefix + ext, 0); return; }
        for (int i = 0; i < n; i++) one(name, prefix + (i + first) + ext, 0);
    }
    private void bank(String name, String prefix, int n) { bank(name, prefix, n, ".ogg", 0); }
    /** n variations numbered from 01 (prefix + 01 .. nn + .ogg). */
    private void numbered(String name, String prefix, int n) { for (int i = 1; i <= n; i++) one(name, prefix + String.format("%02d", i) + ".ogg", 0); }

    private void numberedWav(String name, String prefix, int n) { for (int i = 1; i <= n; i++) one(name, prefix + String.format("%02d", i) + ".wav", 0); }

    /** One recording into a bank; maxSeconds > 0 keeps only its start (a hurt grunt from a death cry). */
    private void one(String name, String path, float maxSeconds) {
        if (game == null) { ahead(path, maxSeconds); return; }
        try {
            float[] raw = decode(path, maxSeconds);
            if (raw == null) return;
            float[] w = trimLead(raw);
            if (maxSeconds > 0) fade(w, 0.06f);
            long h = game.engine().createSound(w, 1, RATE);
            if (h != 0) { banks.computeIfAbsent(name, k -> new ArrayList<>()).add(h); loaded++; }
        } catch (IOException | Codecs.Failure e) {
            System.err.println("[sfx] could not load " + path + ": " + e.getMessage());
        }
    }

    /** A per-bank pitch (a sound borrowed from a smaller throat: the lion's growl is a cat's, an octave down). */
    private final Map<String, Float> pitch = new HashMap<>();
    public float pitchOf(String name) { return pitch.getOrDefault(name, 1f); }

    /**
     * A field recording cut into its separate calls: the loud stretches (above a share of the recording's own peak,
     * judged over 20 ms windows), each from a little before it starts to a little after it ends, between a shortest
     * and a longest length, faded at both ends. At most max calls, the loudest first.
     */
    /** In the decode-only pass: one recording decoded on a background thread. */
    private void ahead(String path, float maxSeconds) {
        decodes.add(decoders.submit(() -> {
            try { decode(path, maxSeconds); } catch (IOException | Codecs.Failure e) { System.err.println("[sfx] could not load " + path + ": " + e.getMessage()); }
        }));
    }

    private void calls(String name, String path, int max, float minLen, float maxLen) {
        if (game == null) { ahead(path, 0); return; }
        float[] w;
        try {
            w = decode(path, 0);
            if (w == null) return;
        } catch (IOException | Codecs.Failure e) { System.err.println("[sfx] could not load " + path + ": " + e.getMessage()); return; }
        int win = RATE / 50, nWin = w.length / win;
        if (nWin < 2) return;
        float[] env = new float[nWin];
        float peak = 0;
        for (int i = 0; i < nWin; i++) { double e = 0; for (int k = 0; k < win; k++) { float v = w[i * win + k]; e += v * v; } env[i] = (float) Math.sqrt(e / win); peak = Math.max(peak, env[i]); }
        if (peak <= 1e-4f) return;
        // judged against the recording's loud end (its 95th percentile), not its single loudest instant
        float[] sorted = env.clone();
        java.util.Arrays.sort(sorted);
        float p95 = Math.max(peak * 0.08f, sorted[(int) (sorted.length * 0.95f)]);
        float on = Math.min(peak * 0.5f, p95 * 0.5f), off = on * 0.4f;
        List<int[]> found = new ArrayList<>();
        int start = -1, quiet = 0;
        for (int i = 0; i < nWin; i++) {
            if (start < 0) { if (env[i] >= on) { start = i; quiet = 0; } continue; }
            quiet = env[i] < off ? quiet + 1 : 0;
            boolean tooLong = (i - start) * win > maxLen * RATE;
            if (quiet >= 6 || tooLong || i == nWin - 1) {
                int end = i - (tooLong ? 0 : quiet);
                if ((end - start) * win >= minLen * RATE) found.add(new int[]{start, end});
                start = -1;
            }
        }
        // a short recording of one call (or one too continuous to cut) is used whole
        if (found.isEmpty()) found.add(new int[]{0, nWin});
        // the loudest calls first
        found.sort((x, y) -> Float.compare(loudest(env, y), loudest(env, x)));
        for (int i = 0; i < Math.min(max, found.size()); i++) {
            int a0 = Math.max(0, found.get(i)[0] * win - RATE / 25), a1 = Math.min(w.length, found.get(i)[1] * win + RATE / 8);
            float[] c = java.util.Arrays.copyOfRange(w, a0, a1);
            int fi = Math.min(c.length / 4, RATE / 100);
            for (int k = 0; k < fi; k++) c[k] *= k / (float) fi;
            fade(c, Math.min(0.08f, c.length / (float) RATE / 4));
            // level each call to the same loudness (a far cry in the recording is not a quiet animal)
            float pk = 0; for (float v : c) pk = Math.max(pk, Math.abs(v));
            if (pk > 1e-4f) { float k = 0.8f / pk; for (int j = 0; j < c.length; j++) c[j] *= k; }
            make(name, c);
            loaded++;
        }
    }

    private static float loudest(float[] env, int[] r) { float m = 0; for (int i = r[0]; i < r[1]; i++) m = Math.max(m, env[i]); return m; }

    /**
     * The silence a recording opens with, cut (keeping 5 ms before the sound): several recordings (the 0 A.D. bow
     * releases among them) open with over a second of room tone, so the twang came a second after the arrow left.
     */
    static float[] trimLead(float[] w) {
        float peak = 0;
        for (float v : w) peak = Math.max(peak, Math.abs(v));
        if (peak <= 1e-4f) return w;
        float on = peak * 0.05f;
        int i = 0;
        while (i < w.length && Math.abs(w[i]) < on) i++;
        int from = Math.max(0, i - RATE / 200);
        if (from < RATE / 100) return w;
        float[] out = java.util.Arrays.copyOfRange(w, from, w.length);
        int f = Math.min(out.length, RATE / 200);
        for (int k = 0; k < f; k++) out[k] *= k / (float) f;
        return out;
    }

    private static void fade(float[] w, float seconds) {
        int n = Math.min(w.length, (int) (seconds * RATE));
        for (int i = 0; i < n; i++) w[w.length - 1 - i] *= i / (float) n;
    }

    private void make(String name, float[] wave) {
        if (game == null) return;
        long h = game.engine().createSound(wave, 1, RATE);
        if (h != 0) banks.computeIfAbsent(name, k -> new ArrayList<>()).add(h);
    }

    private void alias(String name, String recorded) { var bank = banks.get(recorded); if (bank != null) banks.put(name, bank); }

    public boolean has(String name) { return banks.containsKey(name); }

    private Long pick(String name) {
        List<Long> b = banks.get(name);
        if (b == null || b.isEmpty()) return null;
        return b.get(rnd.nextInt(b.size()));
    }

    // ------------------------------------------------------------------ playing
    /** A sound in the world, heard from where it happened. */
    public void play(String name, float x, float y, float z, float gain) { play(name, x, y, z, gain, 0.94f + rnd.nextFloat() * 0.12f, 28); }

    public void play(String name, float x, float y, float z, float gain, float pitch, float range) {
        Long s = pick(name);
        if (s == null) return;
        long v = game.reserve(AstrattoNative.KIND_VOICE);
        game.commands().voicePlay(v, s, AstrattoNative.BUS_SFX, AstrattoNative.VOICE_POSITIONAL | AstrattoNative.VOICE_OCCLUDE | AstrattoNative.VOICE_REVERB,
                gain, pitch, 0, x, y, z, range, 0);
        played++;
    }

    /** A voice in the world (the voice bus): a body's cry. */
    public void voice(String name, float x, float y, float z, float gain, float pitch) {
        Long s = pick(name);
        if (s == null) return;
        long v = game.reserve(AstrattoNative.KIND_VOICE);
        game.commands().voicePlay(v, s, AstrattoNative.BUS_VOICE, AstrattoNative.VOICE_POSITIONAL | AstrattoNative.VOICE_OCCLUDE | AstrattoNative.VOICE_REVERB,
                gain, pitch, 0, x, y, z, 32, 0);
        played++;
    }

    /** A person dying: one of the recorded death cries for their sex, from the head, pitched a little for each throat. */
    public void deathCry(Actor a) {
        if (a instanceof rpg.monster.Monster m) { creature(m, m.kind.deathSound, 1f); return; }
        float pitch = 0.95f + (float) ((a.id * 0x9E3779B97F4A7C15L >>> 40) % 1000) / 1000f * 0.1f;
        voice(HumanVoice.of(a).deathBank, a.x, a.eyeY(), a.z, 1f, pitch);
    }

    /** A person hurt by a real blow: the opening of a death cry, cut short (only when not already crying out). */
    public void hurtCry(Actor a, double now) {
        if (now - a.lastCry < 0.9) return;
        a.lastCry = now;
        if (a instanceof rpg.monster.Monster m) { creature(m, m.kind.hurtSound, 0.8f); return; }
        float pitch = 0.95f + (float) ((a.id * 0x9E3779B97F4A7C15L >>> 40) % 1000) / 1000f * 0.1f;
        voice(HumanVoice.of(a).hurtBank, a.x, a.eyeY(), a.z, 0.75f, pitch * 1.04f);
    }

    /** A monster's own voice (never a person's): pitched by its size - a big throat lower, a small one higher. */
    public void creature(rpg.monster.Monster m, String bank, float gain) {
        if (!has(bank)) return;
        float pitch = 1.25f - Math.min(0.75f, m.kind.scale() * 0.22f);
        play(bank, m.x, m.eyeY(), m.z, gain, pitch * (0.95f + rnd.nextFloat() * 0.1f), 22 * gain);
    }

    /** An interface sound. */
    public void ui(String name, float gain) {
        Long s = pick(name.startsWith("ui.") || banks.containsKey(name) ? name : "ui." + name);
        if (s == null) s = pick(name);
        if (s == null) return;
        long v = game.reserve(AstrattoNative.KIND_VOICE);
        game.commands().voicePlay(v, s, AstrattoNative.BUS_UI, 0, gain, 1, 0, 0, 0, 0, 0, 0);
        played++;
    }

    /** The level-up jingle: a skill's, a skill's hundredth (mastery), or - when the combat level rose too - combat's, or its maximum's. */
    public void jingle(boolean mastery, boolean combatUp, boolean combatMax) {
        ui(combatMax ? "ui.Max_Combat_levelup" : mastery ? "ui.Level_100_SkillMusic" : combatUp ? "ui.Combat_levelup" : "ui.Skill_levelup", 0.85f);
    }

    // ------------------------------------------------------------------ combat: which recording for which blow
    /** The swing of a weapon through the air: a blade's ring, a dagger's flick, a haft's heavier rush; fists swish. */
    public static String swingOf(rpg.item.ItemDef w) {
        if (w == null || w.natural) return "swing";
        return switch (w.family) {
            case SWORD, SCIMITAR, LONGSWORD -> "swing.blade";
            case DAGGER -> "swing.dagger";
            case AXE, MACE, WARHAMMER, HALBERD, SPEAR, STAFF, PICKAXE, TOOL -> "swing.heavy";
            default -> "swing";
        };
    }

    /** A blow landing on a body: a cut or a stab sounds of flesh opening, a blunt one of a body struck; fists are punches. */
    public static String hitOf(rpg.item.ItemDef.DamageType type, boolean bare, boolean heavy) {
        if (bare) return heavy ? "heavyhit" : "hit";
        return type == rpg.item.ItemDef.DamageType.BLUNT ? "hit.blunt" : "hit.blade";
    }

    /** A blow stopped by a guard: on a shield, wood or metal by what it is made of; on a blade, steel on steel. */
    public static String blockOf(rpg.item.ItemDef guard) {
        if (guard == null) return "block";
        if (guard.family == rpg.item.ItemDef.Family.SHIELD) return guard.id.startsWith("wooden") || guard.id.startsWith("stone") ? "shield.wood" : "shield.metal";
        return "block";
    }

    /** An arrow, bolt or thrown weapon striking the world: into wood, earth, or glancing off stone and metal. */
    public static String arrowOn(int block) {
        return switch (surface(block)) { case "wood" -> "arrow.wood"; case "earth", "grass", "cloth" -> "arrow.earth"; default -> "arrow.stone"; };
    }

    /** The devmap's ambience, looped on the ambience bus (once). */
    public void ambience(String file, float gain) {
        if (ambience != 0) return;
        one("ambience", file, 0);
        Long s = pick("ambience");
        if (s == null) return;
        ambience = game.reserve(AstrattoNative.KIND_VOICE);
        game.commands().voicePlay(ambience, s, AstrattoNative.BUS_AMBIENCE, AstrattoNative.VOICE_LOOP, gain, 1, 0, 0, 0, 0, 0, 0);
    }

    /** A footfall: what it sounds like depends on what the foot came down on, and how hard (0..1). */
    public void footstep(Actor a, float force) {
        int below = a.y > 0 ? blockBelow(a) : 0;
        String surface = surface(below);
        float gain = 0.16f + 0.3f * force, pitch = 0.9f + rnd.nextFloat() * 0.2f;
        play("step." + surface, a.x, a.y + 0.05f, a.z, gain, pitch, 18 + 10 * force);
    }

    /** What an actor stands on (the game hands this over: the voxel world is not this class's). */
    public java.util.function.ToIntFunction<Actor> ground = a -> 0;
    private int blockBelow(Actor a) { return ground.applyAsInt(a); }

    /** The family of sound a block makes underfoot: its material's (Blocks.material). */
    public static String surface(int block) {
        if (block == Blocks.GRASS) return "grass";
        return switch (Blocks.material(block)) {
            case FOLIAGE, STRAW -> "grass";
            case EARTH, SAND -> "earth";
            case WOOD -> "wood";
            case CLOTH -> "cloth";
            default -> "stone";
        };
    }

}
