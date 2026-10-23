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
 * See sounds/CREDITS.txt for where the downloaded ones came from (all CC0). Sounds with no recording (a spell's
 * crackle, a fire, the blessing chord) are still synthesized here.
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
    public static synchronized Sfx shared(Game game) { if (shared == null || shared.game != game) shared = new Sfx(game); return shared; }

    public Sfx(Game game) {
        this.game = game;
        // ---- recorded: the world
        bank("step.grass", "foley/footstep_grass_00", 5);
        bank("step.earth", "foley/footstep0", 10);
        bank("step.stone", "foley/footstep_concrete_00", 5);
        bank("step.wood", "foley/footstep_wood_00", 5);
        bank("step.cloth", "foley/footstep_carpet_00", 5);
        bank("step.snow", "foley/footstep_snow_00", 5);
        bank("swing", "swish/swish-", 13, ".wav", 1);
        bank("blade", "sfx/blade_0", 3, ".ogg", 1);
        bank("hit", "foley/impactPunch_medium_00", 5);
        bank("heavyhit", "foley/impactPunch_heavy_00", 5);
        bank("block", "foley/impactMetal_medium_00", 5);
        bank("parry", "foley/impactMetal_heavy_00", 5);
        bank("plate", "foley/impactPlate_medium_00", 5);
        bank("wood.light", "foley/impactWood_light_00", 5);
        bank("wood", "foley/impactWood_medium_00", 5);
        bank("wood.heavy", "foley/impactWood_heavy_00", 5);
        bank("plank", "foley/impactPlank_medium_00", 5);
        bank("metal.light", "foley/impactMetal_light_00", 5);
        bank("soft", "foley/impactSoft_medium_00", 5);
        bank("soft.heavy", "foley/impactSoft_heavy_00", 5);
        bank("generic", "foley/impactGeneric_light_00", 5);
        bank("glass.light", "foley/impactGlass_light_00", 5);
        bank("glass", "foley/impactGlass_medium_00", 5);
        bank("glass.break", "foley/impactGlass_heavy_00", 5);
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
        bank("draw", "foley/drawKnife", 3, ".ogg", 1);
        bank("lock", "sfx/lock_0", 3, ".ogg", 1);
        bank("book", "foley/bookFlip", 3, ".ogg", 1);
        bank("stone", "sfx/stones_0", 4, ".ogg", 1);
        bank("thud", "foley/impactGeneric_light_00", 5);
        bank("cast", "sfx/spell_0", 2, ".ogg", 1);
        bank("fire", "sfx/spell_fire_0", 7, ".ogg", 1);
        bank("gem", "sfx/item_gem_0", 4, ".ogg", 1);
        bank("growl", "sfx/creature_misc_0", 8, ".ogg", 1);
        bank("creature.hurt", "sfx/creature_hurt_0", 2, ".ogg", 1);
        bank("creature.die", "sfx/creature_die_0", 1, ".ogg", 1);
        bank("roar", "sfx/creature_roar_0", 3, ".ogg", 1);
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
                "SpellBookCloseA", "UI_Trading_ItemSelect", "UI_hover", "UI_select", "UI_windowclose", "UIbutton1", "UIbutton2"})
            one("ui." + f, "ui/" + f + ".ogg", 0);
        // ---- synthesized: what has no recording
        make("crackle", crackle());
        make("splash", noiseBurst(0.35f, 0.4f));
        make("bow", twang());
        make("zap", sweep(1400, 200, 0.25f));
        make("click", tone(1400, 0.03f, 0.3f));
        make("fail", tone(160, 0.18f, 0.4f));
        make("eat", noiseBurst(0.22f, 0.3f));
        make("bless", jingleBless());
        make("splat", noiseBurst(0.12f, 0.6f));
        // synthesized stand-ins, only where a recording failed to load
        fallback("swing", swing()); fallback("hit", hit(0.9f)); fallback("heavyhit", hit(1.6f)); fallback("block", clang(620, 0.35f));
        fallback("parry", clang(1180, 0.6f)); fallback("door", knock(110, 0.3f)); fallback("coins", coins()); fallback("thud", knock(240, 0.12f));
        fallback("step.stone", noiseBurst(0.08f, 0.5f)); fallback("cast", sweep(300, 1200, 0.4f)); fallback("ui.LevelUp", jingleWave());
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

    /** One recording into a bank; maxSeconds > 0 keeps only its start (a hurt grunt from a death cry). */
    private void one(String name, String path, float maxSeconds) {
        try (InputStream in = Sfx.class.getResourceAsStream("/sounds/" + path)) {
            if (in == null) return;
            byte[] raw = in.readAllBytes();
            Codecs.Decoded d = Codecs.decode(raw, path, maxSeconds);
            float[] w = d.mono();
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
    private void calls(String name, String path, int max, float minLen, float maxLen) {
        float[] w;
        try (InputStream in = Sfx.class.getResourceAsStream("/sounds/" + path)) {
            if (in == null) return;
            w = Codecs.decode(in.readAllBytes(), path, 0).mono();
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

    private static void fade(float[] w, float seconds) {
        int n = Math.min(w.length, (int) (seconds * RATE));
        for (int i = 0; i < n; i++) w[w.length - 1 - i] *= i / (float) n;
    }

    private void make(String name, float[] wave) {
        long h = game.engine().createSound(wave, 1, RATE);
        if (h != 0) banks.computeIfAbsent(name, k -> new ArrayList<>()).add(h);
    }

    private void fallback(String name, float[] wave) { if (!banks.containsKey(name)) make(name, wave); }

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
        float pitch = 0.95f + (float) ((a.id * 0x9E3779B97F4A7C15L >>> 40) % 1000) / 1000f * 0.1f;
        voice(HumanVoice.of(a).deathBank, a.x, a.eyeY(), a.z, 1f, pitch);
    }

    /** A person hurt by a real blow: the opening of a death cry, cut short (only when not already crying out). */
    public void hurtCry(Actor a, double now) {
        if (now - a.lastCry < 0.9) return;
        a.lastCry = now;
        float pitch = 0.95f + (float) ((a.id * 0x9E3779B97F4A7C15L >>> 40) % 1000) / 1000f * 0.1f;
        voice(HumanVoice.of(a).hurtBank, a.x, a.eyeY(), a.z, 0.75f, pitch * 1.04f);
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

    public void jingle() { ui("ui.LevelUp", 0.85f); }

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

    /** The family of sound a block makes underfoot. */
    public static String surface(int block) {
        return switch (block) {
            case Blocks.GRASS, Blocks.LEAVES, Blocks.OAK_LEAVES, Blocks.WILLOW_LEAVES, Blocks.BERRY_BUSH, Blocks.HERB_PATCH, Blocks.FLAX_PATCH -> "grass";
            case Blocks.DIRT, Blocks.SAND, Blocks.BARE_PATCH -> "earth";
            case Blocks.PLANKS, Blocks.LOG, Blocks.OAK_LOG, Blocks.WILLOW_LOG, Blocks.TABLE, Blocks.CRATE, Blocks.BOOKCASE, Blocks.BEAM, Blocks.STUMP -> "wood";
            case Blocks.BED -> "cloth";
            default -> "stone";
        };
    }

    // ------------------------------------------------------------------ synthesis (only for what has no recording)
    private float[] buf(float seconds) { return new float[(int) (RATE * seconds)]; }

    private float[] tone(float hz, float seconds, float amp) {
        float[] w = buf(seconds);
        for (int i = 0; i < w.length; i++) { double t = i / (double) RATE; w[i] = (float) (amp * Math.sin(2 * Math.PI * hz * t) * Math.exp(-t * 18 / seconds / 4)); }
        return w;
    }

    private float[] swing() {
        float[] w = buf(0.28f);
        float lp = 0;
        for (int i = 0; i < w.length; i++) {
            double t = i / (double) w.length;
            float n = rnd.nextFloat() * 2 - 1;
            float a = (float) (0.15 + 0.6 * t);
            lp += (n - lp) * a;
            w[i] = (float) (lp * Math.sin(Math.PI * t) * 0.7);
        }
        return w;
    }

    private float[] hit(float weight) {
        float[] w = buf(0.25f);
        for (int i = 0; i < w.length; i++) {
            double t = i / (double) RATE;
            w[i] = (float) ((0.8 * Math.sin(2 * Math.PI * (90 / weight) * t) + 0.5 * (rnd.nextFloat() * 2 - 1) * Math.exp(-t * 60)) * Math.exp(-t * 18) * 0.9);
        }
        return w;
    }

    private float[] clang(float hz, float seconds) {
        float[] w = buf(seconds);
        for (int i = 0; i < w.length; i++) {
            double t = i / (double) RATE, e = Math.exp(-t * 10 / seconds);
            w[i] = (float) ((0.5 * Math.sin(2 * Math.PI * hz * t) + 0.3 * Math.sin(2 * Math.PI * hz * 2.76 * t) + 0.2 * Math.sin(2 * Math.PI * hz * 5.4 * t)) * e * 0.6
                    + (rnd.nextFloat() * 2 - 1) * Math.exp(-t * 200) * 0.4);
        }
        return w;
    }

    private float[] knock(float hz, float seconds) {
        float[] w = buf(seconds);
        for (int i = 0; i < w.length; i++) {
            double t = i / (double) RATE;
            w[i] = (float) ((Math.sin(2 * Math.PI * hz * t) * 0.7 + (rnd.nextFloat() * 2 - 1) * 0.5 * Math.exp(-t * 90)) * Math.exp(-t * 30) * 0.9);
        }
        return w;
    }

    private float[] noiseBurst(float seconds, float amp) {
        float[] w = buf(seconds);
        float lp = 0;
        for (int i = 0; i < w.length; i++) { double t = i / (double) w.length; lp += ((rnd.nextFloat() * 2 - 1) - lp) * 0.3f; w[i] = (float) (lp * amp * Math.pow(1 - t, 2)); }
        return w;
    }

    private float[] crackle() {
        float[] w = buf(1.2f);
        for (int k = 0; k < 18; k++) {
            int at = rnd.nextInt(w.length - 800);
            for (int i = 0; i < 600; i++) w[at + i] += (float) ((rnd.nextFloat() * 2 - 1) * Math.exp(-i / 80.0) * 0.5);
        }
        return w;
    }

    private float[] coins() {
        float[] w = buf(0.4f);
        for (int k = 0; k < 4; k++) {
            int at = k * 3000;
            for (int i = 0; at + i < w.length; i++) { double t = i / (double) RATE; w[at + i] += (float) (Math.sin(2 * Math.PI * (2600 + k * 300) * t) * Math.exp(-t * 30) * 0.25); }
        }
        return w;
    }

    private float[] twang() {
        float[] w = buf(0.3f);
        for (int i = 0; i < w.length; i++) { double t = i / (double) RATE; w[i] = (float) (Math.sin(2 * Math.PI * (220 - t * 200) * t) * Math.exp(-t * 14) * 0.6); }
        return w;
    }

    private float[] sweep(float from, float to, float seconds) {
        float[] w = buf(seconds);
        double ph = 0;
        for (int i = 0; i < w.length; i++) {
            double t = i / (double) w.length;
            ph += 2 * Math.PI * (from + (to - from) * t) / RATE;
            w[i] = (float) ((Math.sin(ph) * 0.4 + Math.sin(ph * 2.01) * 0.15) * Math.sin(Math.PI * t));
        }
        return w;
    }

    private float[] jingleWave() {
        float[] notes = {523.25f, 659.25f, 783.99f, 1046.5f};
        float[] w = buf(1.9f);
        for (int k = 0; k < notes.length; k++) {
            int at = (int) (k * 0.13f * RATE);
            float len = k == notes.length - 1 ? 1.4f : 0.9f;
            for (int i = 0; i < len * RATE && at + i < w.length; i++) {
                double t = i / (double) RATE;
                double v = Math.sin(2 * Math.PI * notes[k] * t) + 0.35 * Math.sin(2 * Math.PI * notes[k] * 2 * t) + 0.12 * Math.sin(2 * Math.PI * notes[k] * 3 * t);
                w[at + i] += (float) (v * Math.exp(-t * 2.6) * Math.min(1, t * 200) * 0.18);
            }
        }
        return w;
    }

    private float[] jingleBless() {
        float[] notes = {392, 587.33f, 783.99f};
        float[] w = buf(1.2f);
        for (int k = 0; k < notes.length; k++) for (int i = 0; i < w.length; i++) {
            double t = i / (double) RATE;
            w[i] += (float) (Math.sin(2 * Math.PI * notes[k] * t) * Math.exp(-t * 2.5) * Math.min(1, t * 20) * 0.12);
        }
        return w;
    }
}
