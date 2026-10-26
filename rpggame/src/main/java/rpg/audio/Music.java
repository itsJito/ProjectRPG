package rpg.audio;

import astratto.audio.MusicStream;
import astratto.bridge.AstrattoNative;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The score: the game's music, chosen from what the player is living through rather than scripted per place. Each
 * moment is read as a {@link Mood} - fighting beats being hunted, which beats the lie of the land - and each mood
 * has its own small set of tracks. Exploration tracks play through once and are followed by a stretch of silence,
 * so the music is an event rather than wallpaper; combat starts at once and loops until the fight is over; a stinger
 * plays over everything for a moment that deserves one (a quest completed).
 *
 * <p>Tracks stream from sounds/music/ through the engine's music bus ({@link MusicStream}): nothing is decoded on the
 * game's thread, and a fourteen-minute track costs a four-second ring. The music carries on through the pause menu.
 * The clock here is wall time, so it keeps running while the world is paused.
 */
public final class Music {
    /** What the moment feels like, most urgent first. */
    public enum Mood { COMBAT, HUNTED, MENU, WOOD_DAY, WOOD_NIGHT, TOWN_DAY, TOWN_NIGHT, OPEN_DAY, OPEN_NIGHT, NONE }

    private static final Map<Mood, String[]> TRACKS = new EnumMap<>(Mood.class);
    static {
        TRACKS.put(Mood.COMBAT, new String[]{"combat"});
        TRACKS.put(Mood.HUNTED, new String[]{"desert_ominous"});
        TRACKS.put(Mood.MENU, new String[]{"mainmenu"});   // the title screen's own theme (MainMenu.mp3)
        TRACKS.put(Mood.WOOD_DAY, new String[]{"forest", "enchanted_forest"});
        TRACKS.put(Mood.WOOD_NIGHT, new String[]{"evil_forest"});
        TRACKS.put(Mood.TOWN_DAY, new String[]{"desert", "forest"});
        TRACKS.put(Mood.TOWN_NIGHT, new String[]{"mystery"});
        TRACKS.put(Mood.OPEN_DAY, new String[]{"enchanted_forest", "desert", "forest"});
        TRACKS.put(Mood.OPEN_NIGHT, new String[]{"mystery", "evil_forest"});
    }

    private final Game game;
    private final Random rnd = new Random();
    private final List<Playing> fading = new ArrayList<>();
    private Playing now;
    private Mood mood = Mood.NONE;
    /** Silence before the next exploration track (seconds of wall time). */
    private float rest = 2f;
    private Mood pendingMood = Mood.NONE;
    private float pendingFor;
    private float gain = 0.7f, duck = 1f, duckTarget = 1f;
    private String lastTrack = "";
    public boolean enabled = true;

    private final class Playing {
        final MusicStream stream;
        final long voice;
        final Mood mood;
        float age, fadeLeft = -1;
        boolean started;
        Playing(MusicStream s, long v, Mood m) { stream = s; voice = v; mood = m; }
    }

    public Music(Game game) { this.game = game; }

    /** The mood the game reports this frame, and the wall-clock step. */
    public void update(Mood want, float dt) {
        if (!enabled) want = Mood.NONE;
        // a mood must hold a moment before the music follows it (a glimpse of a wolf is not a battle) - except combat, which is answered at once
        if (want != pendingMood) { pendingMood = want; pendingFor = 0; }
        pendingFor += dt;
        float settle = want == Mood.COMBAT ? 0 : mood == Mood.COMBAT ? 5f : want == Mood.HUNTED ? 1.5f : 3f;
        if (want != mood && pendingFor >= settle) change(want);

        duck += (duckTarget - duck) * Math.min(1, dt * 3);
        for (int i = fading.size() - 1; i >= 0; i--) {
            Playing p = fading.get(i);
            p.age += dt; p.fadeLeft -= dt;
            if (p.fadeLeft <= -0.25f) { p.stream.close(); fading.remove(i); }
        }
        if (now != null) {
            if (!now.started && (now.stream.buffered() > 0.6 || now.stream.decoded())) {
                float fadeIn = now.mood == Mood.COMBAT ? 1.2f : 4f;
                game.commands().voicePlay(now.voice, now.stream.sound(), AstrattoNative.BUS_MUSIC, 0, gain * duck, 1, 0, 0, 0, 0, 0, fadeIn);
                now.started = true;
            } else if (now.started) {
                now.age += dt;
                game.commands().voiceSet(now.voice, AstrattoNative.SET_GAIN, gain * duck, 1, 0, 0, 0, 0, 0);
                // played through: the decoder has handed it all over and the mixer has had time to play it
                if (now.stream.decoded() && now.age > now.stream.buffered() + 0.5) {
                    if (now.stream.failure() != null) System.err.println("[music] " + now.stream.name() + ": " + now.stream.failure());
                    now.stream.close();
                    now = null;
                    rest = 25 + rnd.nextFloat() * 50;     // silence between pieces
                }
            }
        } else if (mood != Mood.NONE) {
            rest -= dt;
            if (rest <= 0) start(mood);
        }
    }

    private void change(Mood to) {
        Mood from = mood;
        mood = to;
        if (now != null && now.mood == to) return;
        // the same family keeps its piece (a day track carries on as night falls in the wood; it just isn't followed by another day one)
        if (now != null && family(now.mood) == family(to) && to != Mood.COMBAT && from != Mood.COMBAT) return;
        if (now != null) fadeOut(now, to == Mood.COMBAT ? 1.0f : from == Mood.COMBAT ? 3.5f : 5f);
        now = null;
        if (to == Mood.NONE) return;
        // urgent moods start at once; a calm one waits a little after a fight or a chase has faded
        rest = to == Mood.COMBAT || to == Mood.HUNTED || to == Mood.MENU ? 0 : from == Mood.COMBAT || from == Mood.HUNTED ? 6f : 2.5f;
        if (rest <= 0) start(to);
    }

    private static int family(Mood m) {
        return switch (m) {
            case WOOD_DAY, WOOD_NIGHT -> 1;
            case TOWN_DAY, TOWN_NIGHT -> 2;
            case OPEN_DAY, OPEN_NIGHT -> 3;
            default -> 10 + m.ordinal();
        };
    }

    private void start(Mood m) {
        String[] set = TRACKS.get(m);
        if (set == null) return;
        String pick = set[rnd.nextInt(set.length)];
        if (set.length > 1 && pick.equals(lastTrack)) pick = set[(java.util.Arrays.asList(set).indexOf(pick) + 1) % set.length];
        final String file = "/sounds/music/" + pick + ".mp3";
        MusicStream s = MusicStream.open(game.engine(), pick, () -> Music.class.getResourceAsStream(file), m == Mood.COMBAT || m == Mood.HUNTED || m == Mood.MENU);
        if (s == null) { rest = 30; return; }
        lastTrack = pick;
        now = new Playing(s, game.reserve(AstrattoNative.KIND_VOICE), m);
    }

    private void fadeOut(Playing p, float seconds) {
        if (p.started) game.commands().voiceStop(p.voice, seconds);
        p.fadeLeft = p.started ? seconds : 0;
        fading.add(p);
    }

    /** Fades out whatever is playing now; the next piece comes after the usual silence. */
    public void stopNow() { if (now != null) { fadeOut(now, 0.5f); now = null; } rest = 30; }

    /** A short piece over everything (the score ducks under it). */
    public void stinger(String name) {
        if (!enabled) return;
        final String file = "/sounds/music/" + name + ".mp3";
        MusicStream s = MusicStream.open(game.engine(), name, () -> Music.class.getResourceAsStream(file), false);
        if (s == null) return;
        Playing p = new Playing(s, game.reserve(AstrattoNative.KIND_VOICE), Mood.NONE);
        // started a moment later, once a little has been decoded; it ends itself and is closed after it has played
        stingers.add(p);
        duckTarget = 0.3f;
    }

    private final List<Playing> stingers = new ArrayList<>();

    /** Stingers: started when a little is ready, closed after they have played; the score comes back up after the last. */
    public void updateStingers(float dt) {
        for (int i = stingers.size() - 1; i >= 0; i--) {
            Playing p = stingers.get(i);
            if (!p.started && (p.stream.buffered() > 0.3 || p.stream.decoded())) {
                game.commands().voicePlay(p.voice, p.stream.sound(), AstrattoNative.BUS_MUSIC, 0, 0.9f, 1, 0, 0, 0, 0, 0, 0.05f);
                p.started = true;
            } else if (p.started) {
                p.age += dt;
                if (p.stream.decoded() && p.age > p.stream.buffered() + 0.5) { p.stream.close(); stingers.remove(i); }
            }
        }
        duckUntil = Math.max(0, duckUntil - dt);
        if (stingers.isEmpty()) duckTarget = duckUntil > 0 ? 0.3f : 1f;
    }

    /** A jingle played as a sound (a level, a death): the score dips under it for that long. */
    public void duckFor(float seconds) { duckUntil = Math.max(duckUntil, seconds); duckTarget = 0.3f; }
    private float duckUntil;

    /** The pause menu turns the score down a little; leaving it brings it back. */
    public void paused(boolean on) { gain = on ? 0.45f : 0.7f; }

    public Mood mood() { return mood; }
    public String playing() { return now == null ? "" : now.stream.name() + (now.started ? "" : " (buffering)"); }

    /** Everything stops (the window is closing). */
    public void close() {
        if (now != null) { now.stream.close(); now = null; }
        for (Playing p : fading) p.stream.close();
        for (Playing p : stingers) p.stream.close();
        fading.clear(); stingers.clear();
    }
}
