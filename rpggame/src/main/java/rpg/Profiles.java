package rpg;

import astratto.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.actor.Appearance;

/**
 * The player's profiles (gamefixes: a title screen with up to five): each a character - a name and a look - and its own
 * save. A profile lives in {@code saves/profiles/<slot>/}: {@code profile.json} (who, when made, when last played, time
 * played, levels at the last save) and {@code save.json} (the game itself, written by {@link SaveSystem}).
 */
public final class Profiles {
    private Profiles() {}

    public static final int MAX = 5;
    static Path root = Path.of("saves", "profiles");

    /** One profile as the title screen shows it. */
    public static final class Profile {
        public final int slot;
        public String name;
        public Appearance look;
        public long created, lastPlayed;
        public double playSeconds;
        public int combatLevel = 3, totalLevel = 27;
        Profile(int slot) { this.slot = slot; }
        public Path save() { return dir(slot).resolve("save.json"); }
        public boolean hasSave() { return Files.isRegularFile(save()); }
    }

    static Path dir(int slot) { return root.resolve(String.valueOf(slot)); }

    /** Every profile there is, by slot (1 to 5). */
    public static List<Profile> list() {
        List<Profile> out = new ArrayList<>();
        for (int s = 1; s <= MAX; s++) { Profile p = read(s); if (p != null) out.add(p); }
        return out;
    }

    public static Profile read(int slot) {
        Path f = dir(slot).resolve("profile.json");
        if (!Files.isRegularFile(f)) return null;
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(f, StandardCharsets.UTF_8));
            Profile p = new Profile(slot);
            p.name = Json.str(m, "name", "Adventurer");
            List<Object> look = Json.list(m, "look");
            int[] l = new int[look == null ? 0 : look.size()];
            for (int i = 0; i < l.length; i++) l[i] = ((Number) look.get(i)).intValue();
            p.look = l.length == 0 ? new Appearance() : Appearance.fromArray(l);
            p.created = Json.lng(m, "created", 0); p.lastPlayed = Json.lng(m, "lastPlayed", 0);
            p.playSeconds = Json.num(m, "playSeconds", 0);
            p.combatLevel = Json.integer(m, "combatLevel", 3); p.totalLevel = Json.integer(m, "totalLevel", 27);
            return p;
        } catch (IOException | RuntimeException e) {
            System.err.println("[profiles] could not read " + f + ": " + e.getMessage());
            return null;
        }
    }

    /** A new profile in the first free slot, or null when all five are taken. */
    public static Profile create(String name, Appearance look) {
        for (int s = 1; s <= MAX; s++) {
            if (read(s) != null) continue;
            Profile p = new Profile(s);
            p.name = name; p.look = look.copy(); p.created = p.lastPlayed = System.currentTimeMillis();
            write(p);
            return p;
        }
        return null;
    }

    public static boolean write(Profile p) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", p.name);
        List<Object> look = new ArrayList<>(); for (int v : p.look.toArray()) look.add(v);
        m.put("look", look);
        m.put("created", p.created); m.put("lastPlayed", p.lastPlayed); m.put("playSeconds", p.playSeconds);
        m.put("combatLevel", p.combatLevel); m.put("totalLevel", p.totalLevel);
        try {
            Files.createDirectories(dir(p.slot));
            Files.writeString(dir(p.slot).resolve("profile.json"), Json.write(m), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) { System.err.println("[profiles] could not write profile " + p.slot + ": " + e.getMessage()); return false; }
    }

    /** Removes a profile and its save, for good. */
    public static void delete(int slot) {
        Path d = dir(slot);
        try (var files = Files.list(d)) { for (Path f : files.toList()) Files.deleteIfExists(f); Files.deleteIfExists(d); }
        catch (IOException e) { System.err.println("[profiles] could not delete profile " + slot + ": " + e.getMessage()); }
    }

    /** After a save: the time played and the levels, for the title screen's cards. */
    public static void played(Profile p, Rpg g, double seconds) {
        p.lastPlayed = System.currentTimeMillis();
        p.playSeconds += seconds;
        p.combatLevel = g.player.skills.combatLevel();
        p.totalLevel = g.player.skills.totalLevel();
        p.look = g.player.look.copy();
        write(p);
    }

    /** "3 h 12 min", "8 min". */
    public static String duration(double seconds) {
        long m = (long) (seconds / 60);
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min";
    }
}
