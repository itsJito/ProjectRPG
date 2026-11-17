package rpg.encounter;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Brain;
import rpg.ai.Npc;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.monster.Monster;
import rpg.monster.MonsterKind;
import rpg.skill.Skill;
import rpg.world.DevMap;
import rpg.world.WorldMap;

/**
 * (0.4.7, gamefixes: the Encounter System) The world's encounters, all of them data (data/encounters.json).
 *
 * <p><b>The Director.</b> Every few seconds it looks at where the player is - the region and its biome, how near a road,
 * whose law holds, the hour, the weather, the player's combat level, any bounty, their honour, the flags earlier
 * encounters left - and, by a seeded draw, may start one: among those whose conditions hold and whose cooldowns have run,
 * each weighted by its own weight, its rarity's share and the region's density. Never more than 'maxActive' at once, never
 * two close together, never in the minutes straight after another.
 *
 * <p><b>An encounter.</b> Its cast are made from the game's own kinds: people (a role, a title, a level, worn and carried
 * kit - the same people the world is made of, with the same minds), animals (any species, with their own minds), monsters
 * (any kind, at a level), a carcass. They come in where the definition says: ahead on the road, hidden in cover beside
 * it, behind, around, far off. Then it moves through its stages: in each, every party has something to do (hide, block
 * the road, approach, surround, hold, plead, travel on, lead, follow, chase, flee, fight the player or another party,
 * migrate, hunt, scavenge, go about its own business), things are said, a choice may be put to the player (with tests of
 * their purse, level, honour or skill) and effects fall out (coins and goods change hands, honour, rumours, crimes and the
 * law, flags the world remembers); conditions move it on (someone near, a timer, a party dead or hurt, the player gone,
 * someone arrived, the player wanted).
 *
 * <p><b>After.</b> When it ends, those of it still about go on: a traveller to the town they were bound for, an outlaw to a
 * camp near where it happened - a drifter with a routine of their own (their role's day, about that camp) for as long as
 * there is room for them. Whoever is far off and out of sight is let go. History, cooldowns, flags, the draw's count and
 * the drifters are saved; nothing in an encounter is a one-off script.
 */
public final class Encounters {
    /** One person, animal or monster in an encounter: its part and what it is doing now. */
    public static final class Part {
        public final long instance; public final String tag;
        public String behavior = "hold", arg = "";
        public float spawnX, spawnZ, destX = Float.NaN, destZ = Float.NaN, campX = Float.NaN, campZ = Float.NaN;
        public String destName = "", drift = "";
        public boolean run, bound, arrived, drifter;
        public int index;
        public double since;
        Part(long instance, String tag) { this.instance = instance; this.tag = tag; }
    }
    /** An encounter under way. */
    public static final class Instance {
        public final long id; public final Map<String, Object> def; public String stage; public double stageSince, startedAt;
        public final Map<String, List<Long>> cast = new LinkedHashMap<>();
        public final Map<String, Integer> levels = new HashMap<>();
        public float x, z; public boolean ended, dialogOpen; public String outcome = "", region = "";
        /** (0.4.8) Since when the player has been far from all of it (-1: not). */
        public double awayAt = -1;
        public double lastDialogAt = -99;
        Instance(long id, Map<String, Object> def) { this.id = id; this.def = def; }
        public String id() { return str(def, "id", "?"); }
    }

    private final Rpg g;
    private final Map<String, Object> data;
    private final Map<String, Map<String, Object>> defs = new LinkedHashMap<>();
    public final List<Instance> active = new ArrayList<>();
    /** The parts of everyone in or left over from an encounter, by actor id. */
    public final Map<Long, Part> parts = new HashMap<>();
    /** What has happened: {id, minute, region, outcome}, newest last. */
    public final List<Map<String, Object>> history = new ArrayList<>();
    private final Map<String, Double> lastAt = new HashMap<>();
    public final Map<String, Boolean> flags = new LinkedHashMap<>();
    private double nextCheck, lastEnded = -1e9;
    private long draws, nextInstance = 1;
    public boolean enabled = true;
    public String lastWhy = "";
    private final Map<Long, rpg.econ.Economy.Shop> shops = new HashMap<>();
    /** The freshest tales the encounters have left, for travellers to pass on. */
    private final List<String> heard = new ArrayList<>();

    public Encounters(Rpg g) {
        this.g = g;
        Map<String, Object> d = Map.of();
        try (InputStream in = Encounters.class.getResourceAsStream("/data/encounters.json")) {
            if (in != null) d = astratto.util.Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) { System.err.println("[encounters] encounters.json could not be read: " + e.getMessage()); }
        data = d;
        // the scripted runs (tests, reviews, the bench) draw no encounters of their own unless asked (-Drpg.encounters=on);
        // they start them themselves where they want them
        enabled = !g.scripted || "on".equals(System.getProperty("rpg.encounters"));
        for (Object o : list(d, "encounters")) if (o instanceof Map<?, ?> m) defs.put(str(m, "id", "?"), cast(m));
    }
    public Map<String, Map<String, Object>> definitions() { return defs; }
    private Map<String, Object> director() { return map(data, "director"); }

    // ================================================================== the director
    /** Each frame: the director's look (every 'interval' s), the running encounters' stages, the leftover parts. */
    public void update(float dt) {
        if (g.player == null) return;
        for (Instance in : new ArrayList<>(active)) step(in);
        tidy();
        if (!enabled || !WorldMap.shadowveil() && !Boolean.TRUE.equals(director().get("devmap"))) return;
        if (g.time < nextCheck) return;
        nextCheck = g.time + num(director(), "interval", 5);
        if (!g.player.alive || g.law.inCustody(g.player)) return;
        Random r = rng();
        if (r.nextDouble() > num(director(), "chance", 0.07) * densityHere()) return;
        Map<String, Object> pick = choose(r, false);
        if (pick != null) start(pick, r);
    }
    /** The draw's own sequence: the save's seed and how many draws there have been (the same world, the same draws). */
    private Random rng() { return seeded((long) num(director(), "seed", 7471), draws++); }
    /**
     * A generator for a seed and a count, its seed well mixed (splitmix64): java.util.Random's first draws from seeds a
     * step apart are nearly the same number, which drew the same encounter time after time.
     */
    public static Random seeded(long seed, long count) {
        long z = seed * 0x9E3779B97F4A7C15L + count * 0xBF58476D1CE4E5B9L + 0x632BE59BD9B4E019L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L; z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL; z ^= z >>> 31;
        return new Random(z);
    }

    /** How busy the roads are here: the region's density, less within a kingdom's law. */
    private double densityHere() {
        String region = region(g.player.x, g.player.z);
        double d = num(map(director(), "density"), region, num(map(director(), "density"), "_default", 1));
        if (!DevMap.WILDS.equals(DevMap.jurisdiction(g.player.x, g.player.z))) d *= num(director(), "realmDensity", 0.55);
        return d;
    }

    /** The encounter drawn from those eligible here and now (or, forcing, from all whose conditions hold), else null. */
    Map<String, Object> choose(Random r, boolean ignoreTiming) {
        if (!ignoreTiming) {
            if (active.size() >= (int) num(director(), "maxActive", 2)) { lastWhy = "enough under way"; return null; }
            if (g.state.minutes - lastEnded < num(director(), "quietMinutes", 45) && !active.isEmpty()) { lastWhy = "a quiet spell"; return null; }
            for (Instance in : active) if (Math.hypot(in.x - g.player.x, in.z - g.player.z) < num(director(), "minApart", 160)) { lastWhy = "one close by already"; return null; }
        }
        List<Map<String, Object>> pool = new ArrayList<>(); List<Double> w = new ArrayList<>(); double total = 0;
        for (var d : defs.values()) {
            String why = ineligible(d, ignoreTiming);
            if (why != null) continue;
            double k = num(d, "weight", 1) * num(map(director(), "rarity"), str(d, "rarity", "common"), 1);
            if (k <= 0) continue;
            pool.add(d); w.add(k); total += k;
        }
        if (pool.isEmpty()) { lastWhy = "nothing fits here"; return null; }
        double x = r.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) { x -= w.get(i); if (x <= 0) return pool.get(i); }
        return pool.get(pool.size() - 1);
    }

    /** Why an encounter may not happen here and now (null: it may). The console's 'encounter why' reads these. */
    public String ineligible(Map<String, Object> d, boolean ignoreTiming) {
        Map<String, Object> w = map(d, "when");
        double now = g.state.minutes;
        if (!ignoreTiming) {
            Double last = lastAt.get(str(d, "id", "?"));
            if (last != null && now - last < num(d, "cooldown", 120)) return "cooling down";
            if (!Boolean.TRUE.equals(d.get("repeatable")) && last != null) return "happened already";
        }
        float px = g.player.x, pz = g.player.z;
        boolean wilds = DevMap.WILDS.equals(DevMap.jurisdiction(px, pz));
        if (Boolean.TRUE.equals(w.get("wilds")) && !wilds) return "only in the wilds";
        if (Boolean.TRUE.equals(w.get("realm")) && wilds) return "only within a kingdom";
        if (w.get("road") instanceof Number n && roadWithin(px, pz, n.intValue()) == null) return "no road near";
        String region = region(px, pz), biome = biome(px, pz);
        if (!list(w, "regions").isEmpty() && !list(w, "regions").contains(region)) return "not in this region";
        if (!list(w, "biomes").isEmpty() && !list(w, "biomes").contains(biome)) return "not in this country";
        List<?> h = list(w, "hours");
        if (h.size() == 2) {
            double hr = g.state.hourOfDay(), a = n(h.get(0)), b = n(h.get(1));
            boolean in = a <= b ? hr >= a && hr < b : hr >= a || hr < b;
            if (!in) return "not at this hour";
        }
        List<?> lv = list(w, "playerLevel");
        int cl = g.player.skills.combatLevel();
        if (lv.size() == 2 && (cl < n(lv.get(0)) || cl > n(lv.get(1)))) return "not at the player's level";
        if (Boolean.TRUE.equals(w.get("bounty")) && !g.law.wantedAnywhere(g.player.id)) return "the player is not wanted";
        if (w.get("honorUnder") instanceof Number n && g.honor.score >= n.intValue()) return "the player's honour is too good";
        if (w.get("honorOver") instanceof Number n && g.honor.score <= n.intValue()) return "the player's honour is too poor";
        if (w.get("settledFar") instanceof Number n && settledWithin(px, pz, n.floatValue())) return "too near a settlement";
        List<?> weather = list(w, "weather");
        if (!weather.isEmpty() && g.weatherSystem != null && !weather.contains(g.weatherSystem.name())) return "not in this weather";
        for (var e : map(w, "flags").entrySet()) if (Boolean.TRUE.equals(e.getValue()) != flags.getOrDefault(e.getKey(), false)) return "the world is not so";
        return null;
    }

    // ================================================================== starting one
    /** Starts an encounter now about the player (the director's, or the console's 'encounter force'). Null: no room for it. */
    public Instance start(Map<String, Object> d, Random r) {
        Instance in = new Instance(nextInstance++, d);
        in.region = region(g.player.x, g.player.z);
        in.startedAt = g.time;
        String how = str(d, "spawn", "ahead");
        float[] anchor = spawnPoint(how, r, null);
        if (anchor == null) { lastWhy = "nowhere to set it"; return null; }
        in.x = anchor[0]; in.z = anchor[1];
        String biome = biome(in.x, in.z);
        for (Object o : list(d, "cast")) {
            Map<String, Object> c = cast(o);
            String tag = str(c, "tag", "?");
            List<Long> ids = in.cast.computeIfAbsent(tag, k -> new ArrayList<>());
            if (!list(c, "biomes").isEmpty() && !list(c, "biomes").contains(biome)) continue;
            if (list(c, "biomesNot").contains(biome)) continue;
            List<?> cnt = list(c, "count");
            int count = cnt.size() == 2 ? n(cnt.get(0)) + r.nextInt(Math.max(1, n(cnt.get(1)) - n(cnt.get(0)) + 1)) : 1;
            String where = str(c, "spawn", "");
            float[] base = where.isEmpty() ? anchor : spawnFor(where, in, r);
            for (int k = 0; k < count; k++) {
                double ang = r.nextDouble() * Math.PI * 2, rad = count == 1 ? 0 : 1.6 + r.nextDouble() * 2.2;
                float[] at = dry(base[0] + (float) (Math.cos(ang) * rad), base[1] + (float) (Math.sin(ang) * rad));
                // (0.4.8) each of the party out of sight as well, close by the party's own spot
                if (!spawnable(at[0], at[1], (float) num(director(), "spawnMin", 32) * 0.6f, (float) num(director(), "spawnSeen", 95))) {
                    float[] u = unseen(at, r, 0.6f);
                    if (u != null && Math.hypot(u[0] - base[0], u[1] - base[1]) < 24) at = u;
                }
                Actor a = make(c, in, k, at, r);
                if (a == null) continue;
                ids.add(a.id);
                Part p = new Part(in.id, tag); p.index = k; p.spawnX = at[0]; p.spawnZ = at[1]; p.since = g.time;
                p.run = Boolean.TRUE.equals(c.get("run")); p.bound = Boolean.TRUE.equals(c.get("bound")); p.drift = str(c, "drift", "");
                String travel = str(c, "travel", "");
                if (!travel.isEmpty()) destination(p, travel, at);
                parts.put(a.id, p);
                if (a instanceof Npc n) { n.encounter = p; n.nextThink = 0; }
            }
        }
        active.add(in);
        enter(in, str(d, "start", "start"));
        g.state.commit("EncounterStarted", g.player.id, in.id, DevMap.jurisdiction(in.x, in.z), 0, in.id() + " in " + in.region + " (" + how + ")");
        lastWhy = "started " + in.id();
        return in;
    }

    /** Someone or something of the cast, made at a point. */
    private Actor make(Map<String, Object> c, Instance in, int k, float[] at, Random r) {
        List<?> lv = list(c, "level");
        int level = lv.size() == 2 ? n(lv.get(0)) + r.nextInt(Math.max(1, n(lv.get(1)) - n(lv.get(0)) + 1)) : n(c.getOrDefault("level", 1));
        // (0.4.8, gamefixes: the dangerous further from Loxford) a fighting party's levels are the place's: its place in
        // its data's range carried into the band of the ground it comes in on
        if (lv.size() == 2 && fights(c)) {
            double share = n(lv.get(1)) > n(lv.get(0)) ? (level - n(lv.get(0))) / (double) (n(lv.get(1)) - n(lv.get(0))) : 0.5;
            level = rpg.world.shadowveil.Danger.level(at[0], at[1], share);
        }
        String tag = str(c, "tag", "?");
        in.levels.merge(tag, level, Math::max);
        switch (str(c, "kind", "person")) {
            case "person" -> { return person(c, in, at, level, r); }
            case "animal", "carcass" -> {
                Species sp;
                try { sp = Species.valueOf(str(c, "species", "DEER")); } catch (IllegalArgumentException e) { return null; }
                Animal a = g.animals.spawnNew(sp, false, r.nextBoolean(), at[0], at[1], g.animals.habitatAt(at[0], at[1]), 0, 0);
                if (a == null) return null;
                a.homeX = at[0]; a.homeZ = at[1]; a.range = 14;
                if (str(c, "kind", "").equals("carcass")) g.combat.environmentalDamage(a, (int) Math.ceil(a.maxHp()) + 5, "found dead");
                return a;
            }
            case "monster" -> {
                MonsterKind mk;
                try { mk = MonsterKind.valueOf(str(c, "monster", "GOBLIN")); } catch (IllegalArgumentException e) { return null; }
                return g.monsters.spawn(mk, Math.max(1, level), at[0], at[1], null);
            }
            default -> { return null; }
        }
    }

    /** A person of the cast: a new body of their role, named, levelled and kitted from the data, under the ground's law. */
    private Npc person(Map<String, Object> c, Instance in, float[] at, int level, Random r) {
        Npc.Role role;
        try { role = Npc.Role.valueOf(str(c, "role", "VILLAGER")); } catch (IllegalArgumentException e) { role = Npc.Role.VILLAGER; }
        long id = g.state.newEntityId();
        boolean female = c.containsKey("female") ? Boolean.TRUE.equals(c.get("female")) : r.nextInt(3) == 0;
        String jur = DevMap.jurisdiction(at[0], at[1]);
        String title = str(c, "title", role.name().toLowerCase());
        Npc n = new Npc(id, rpg.ai.Names.drawn(id, female), role, title + (DevMap.WILDS.equals(jur) || !Boolean.TRUE.equals(c.get("realmGuard")) ? "" : " of " + jur), at[0], at[1]);
        boolean fighter = role == Npc.Role.GUARD || role == Npc.Role.KNIGHT || role == Npc.Role.LAWMAN || role == Npc.Role.BANDIT || role == Npc.Role.BRUTE || role == Npc.Role.HUNTER || role == Npc.Role.THIEF;
        for (Skill s : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENSE, Skill.ARCHERY}) n.skills.setLevel(s, fighter ? level : Math.max(1, level / 2));
        n.skills.setLevel(Skill.HEALTH, Math.max(10, level));
        n.look = rpg.actor.Appearance.random(id * 31 + in.id, female);
        for (Object o : list(c, "wear")) { ItemDef d = Items.find(String.valueOf(o)); if (d != null && d.slot != null) { n.eq.set(d.slot, new ItemStack(d, 1)); g.state.mint(d, 1); } }
        for (var e : map(c, "give").entrySet()) { ItemDef d = Items.find(e.getKey()); if (d != null && e.getValue() instanceof Number v && v.intValue() > 0) g.grant(n, new ItemStack(d, v.intValue())); }
        n.meetGear();
        n.homeJurisdiction = Boolean.TRUE.equals(c.get("realmGuard")) && !DevMap.WILDS.equals(jur) ? jur : DevMap.WILDS.equals(jur) ? DevMap.WILDS : jur;
        // nobody of an encounter is hostile until a stage makes them so (a highwayman asks before he takes)
        n.hostileToPlayer = false;
        n.homeX = n.spawnX = n.postX = at[0]; n.homeZ = n.spawnZ = n.postZ = at[1];
        n.fitBody(); n.restore();
        if (c.get("wounded") instanceof Number wnd) n.hp = Math.max(1, n.maxHp() * wnd.floatValue());
        // a pedlar's pack: a little shop of their own for the length of the road
        if (!list(c, "stock").isEmpty()) {
            var shop = g.econ.newShop(id, n.name + "'s pack", n.homeJurisdiction, at[0], at[1]);
            for (Object o : list(c, "stock")) {
                String[] sv = String.valueOf(o).split(":");
                ItemDef d = Items.find(sv[0]);
                if (d != null) g.econ.stockShop(shop, d, sv.length > 1 ? Integer.parseInt(sv[1]) : 1, sv.length > 1 ? Integer.parseInt(sv[1]) : 1);
            }
            shops.put(id, shop);
        }
        n.yaw = (float) Math.atan2(-(g.player.x - at[0]), -(g.player.z - at[1]));
        g.npcs.add(n); g.ai.spawnBody(n);
        return n;
    }
    /** (pedlars) A cast member's own shop, if they have one. */
    public rpg.econ.Economy.Shop shopOf(Npc n) { return shops.get(n.id); }

    // ================================================================== where they come in
    private float[] heading() {
        float vx = g.player.vx, vz = g.player.vz;
        if (Math.hypot(vx, vz) > 0.5) { float l = (float) Math.hypot(vx, vz); return new float[]{vx / l, vz / l}; }
        float yaw = g.player.yaw;
        return new float[]{(float) -Math.sin(yaw), (float) -Math.cos(yaw)};
    }
    /** A point for an encounter's arrangement about the player: {x, z}, or null. */
    float[] spawnPoint(String how, Random r, float[] near) {
        List<?> sd = list(director(), "spawnDistance");
        float d0 = sd.size() == 2 ? (float) n2(sd.get(0)) : 34, d1 = sd.size() == 2 ? (float) n2(sd.get(1)) : 58;
        float d = d0 + r.nextFloat() * (d1 - d0);
        float[] h = heading();
        float px = g.player.x, pz = g.player.z;
        float[] p;
        switch (how) {
            case "behind" -> p = new float[]{px - h[0] * d, pz - h[1] * d};
            case "around", "around_hidden" -> { double a = r.nextDouble() * Math.PI * 2; p = new float[]{px + (float) Math.cos(a) * d, pz + (float) Math.sin(a) * d}; }
            case "far" -> { double a = Math.atan2(h[1], h[0]) + (r.nextDouble() - 0.5) * 2.2; p = new float[]{px + (float) Math.cos(a) * d * 1.6f, pz + (float) Math.sin(a) * d * 1.6f}; }
            default -> p = new float[]{px + h[0] * d, pz + h[1] * d};
        }
        if (!how.equals("far") && !how.startsWith("around")) { int[] road = roadWithin(p[0], p[1], 14); if (road != null) p = new float[]{road[0] + 0.5f, road[1] + 0.5f}; }
        if (how.endsWith("hidden")) { float[] c = cover(p[0], p[1], r); if (c != null) p = c; }
        p = dry(p[0], p[1]);
        return unseen(p, r, how.equals("far") ? 0.25f : 1f);
    }

    /**
     * (0.4.8, gamefixes: random events spawned in awkward places right in front of the player) a point the encounter's
     * people may come into being at: never closer than 'spawnMin' metres, never where the player can see the spot (their
     * eye to the body's head and knees) unless it is beyond 'spawnSeen' metres in the haze, on open ground a body can walk
     * (not in water, not on a roof, not in a wall). Searched about the asked point, then further out along the same
     * bearing; null when nowhere will do.
     */
    float[] unseen(float[] p, Random r, float minScale) {
        float min = (float) num(director(), "spawnMin", 32) * minScale, seenFar = (float) num(director(), "spawnSeen", 95);
        float px = g.player.x, pz = g.player.z;
        float bx = p[0] - px, bz = p[1] - pz, bl = Math.max(0.1f, (float) Math.hypot(bx, bz));
        for (int k = 0; k < 40; k++) {
            float cx, cz;
            if (k == 0) { cx = p[0]; cz = p[1]; }
            else if (k < 24) { double a = r.nextDouble() * Math.PI * 2, rr = 3 + r.nextDouble() * 16; cx = p[0] + (float) (Math.cos(a) * rr); cz = p[1] + (float) (Math.sin(a) * rr); }
            else { float out = bl + (k - 23) * 6; cx = px + bx / bl * out; cz = pz + bz / bl * out; }
            float[] c = dry(cx, cz);
            if (spawnable(c[0], c[1], min, seenFar)) return c;
        }
        lastWhy = "nowhere unseen to set it";
        return null;
    }
    /** Whether a body may come in at (x, z) unseen: far enough, out of the player's sight or beyond it, on walkable open ground. */
    boolean spawnable(float x, float z, float min, float seenFar) {
        float d = (float) Math.hypot(x - g.player.x, z - g.player.z);
        if (d < min) return false;
        float y = WorldMap.standY(x, z);
        if (!Float.isFinite(y) || y <= 1) return false;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
        if (g.vox.block(bx, by - 1, bz) == rpg.world.Blocks.WATER || g.vox.solid(bx, by, bz) || g.vox.solid(bx, by + 1, bz)) return false;
        if (g.vox.skyLight(bx, by + 1, bz) < 0.6f) return false;                 // under a roof: not a road
        if (WorldMap.shadowveil()) { var t = rpg.world.shadowveil.ShadowVeil.terrain(); if (t != null && t.arch != null && t.arch.at(bx, bz) != null) return false; }
        if (d >= seenFar) return true;
        float ex = g.player.x, ey = g.player.eyeY(), ez = g.player.z;
        return !g.vox.sight(ex, ey, ez, x, y + 1.6f, z) && !g.vox.sight(ex, ey, ez, x, y + 0.5f, z);
    }
    /** Where a party of the cast comes in relative to another (hidden further on, behind the runner, near the hunters...). */
    private float[] spawnFor(String where, Instance in, Random r) {
        float[] h = heading();
        float[] firstOf = null;
        for (var e : in.cast.entrySet()) if (!e.getValue().isEmpty() && where.endsWith("_" + e.getKey()) && g.actor(e.getValue().get(0)) instanceof Actor a) firstOf = new float[]{a.x, a.z};
        if (where.equals("hidden_far")) { float[] p = {in.x + h[0] * 26, in.z + h[1] * 26}; float[] c = cover(p[0], p[1], r); return c == null ? p : c; }
        if (firstOf != null && where.startsWith("behind_")) {
            float dx = firstOf[0] - g.player.x, dz = firstOf[1] - g.player.z, l = Math.max(0.1f, (float) Math.hypot(dx, dz));
            return new float[]{firstOf[0] + dx / l * 16, firstOf[1] + dz / l * 16};
        }
        if (firstOf != null && where.startsWith("near_")) return new float[]{firstOf[0] + 5, firstOf[1] + 3};
        return new float[]{in.x, in.z};
    }
    /** A spot out of the player's sight behind something (a tree, a rock, a wall) within a few metres of a point. */
    private float[] cover(float x, float z, Random r) {
        float[] best = null;
        for (int k = 0; k < 24; k++) {
            float cx = x + (r.nextFloat() - 0.5f) * 18, cz = z + (r.nextFloat() - 0.5f) * 18;
            float y = WorldMap.standY(cx, cz);
            if (g.vox.sight(g.player.x, g.player.eyeY(), g.player.z, cx, y + 1.2f, cz)) continue;
            best = new float[]{cx, cz};
            break;
        }
        return best;
    }
    private float[] dry(float x, float z) { return WorldMap.shadowveil() ? rpg.world.shadowveil.ShadowVeil.dry(x, z) : new float[]{x, z}; }

    /** Where a traveller of the cast is bound: the nearest settlement's middle, one further off, the nearest church. */
    private void destination(Part p, String kind, float[] from) {
        var t = WorldMap.shadowveil() ? rpg.world.shadowveil.ShadowVeil.terrain() : null;
        if (t == null || t.arch == null) { p.destX = from[0] + 120; p.destZ = from[1]; p.destName = "the next town"; return; }
        List<rpg.world.shadowveil.ShadowVeil.Realm> realms = rpg.world.shadowveil.ShadowVeil.realms();
        rpg.world.shadowveil.ShadowVeil.Realm near = null, far = null; double nd = 1e9, fd = 1e9;
        for (var re : realms) {
            double d = Math.hypot(re.cx() - from[0], re.cz() - from[1]);
            if (d < nd) { far = near; fd = nd; near = re; nd = d; } else if (d < fd) { far = re; fd = d; }
        }
        var pick = kind.equals("far_settlement") && far != null ? far : near;
        if (kind.equals("church")) {
            var best = (rpg.world.shadowveil.Architecture.Structure) null; double bd = 1e9;
            for (var st : t.arch.structures) if (st.template.equals("church") || st.name.contains("Sanctuary") || st.name.contains("Church")) {
                double d = Math.hypot(st.cx() - from[0], st.cz() - from[1]); if (d < bd) { bd = d; best = st; } }
            if (best != null) { float[] dd = dry(best.cx(), best.cz() + best.d / 2f + 3); p.destX = dd[0]; p.destZ = dd[1]; p.destName = best.name; return; }
        }
        if (pick == null) { p.destX = from[0] + 120; p.destZ = from[1]; p.destName = "the next town"; return; }
        float[] dd = dry(pick.cx(), pick.cz());
        p.destX = dd[0]; p.destZ = dd[1]; p.destName = pick.name();
    }

    // ================================================================== the stages
    private Map<String, Object> stageOf(Instance in) { return map(map(in.def, "stages"), in.stage); }

    private void enter(Instance in, String stage) {
        in.stage = stage; in.stageSince = g.time; in.dialogOpen = false;
        Map<String, Object> st = stageOf(in);
        for (var e : map(st, "do").entrySet()) for (long id : in.cast.getOrDefault(e.getKey(), List.of())) {
            Part p = parts.get(id);
            if (p == null) continue;
            String v = String.valueOf(e.getValue());
            int c = v.indexOf(':');
            p.behavior = c < 0 ? v : v.substring(0, c); p.arg = c < 0 ? "" : v.substring(c + 1); p.since = g.time;
            Actor a = g.actor(id);
            onBehavior(in, p, a);
        }
        Random r = new Random(in.id * 131 + stage.hashCode());
        for (var e : map(st, "say").entrySet()) {
            List<?> lines = e.getValue() instanceof List<?> l ? l : List.of(String.valueOf(e.getValue()));
            if (lines.isEmpty()) continue;
            Actor a = first(in, e.getKey());
            if (a instanceof Npc n && n.alive) g.ai.say(n, fill(in, String.valueOf(lines.get(r.nextInt(lines.size())))));
        }
        for (Object ef : list(st, "effects")) effect(in, String.valueOf(ef), null);
        if (st.get("end") != null) end(in, str(st, "end", "over"));
    }

    /** What a new behaviour sets up once: a fight, a flight, a hunt, a migration, a feed. */
    private void onBehavior(Instance in, Part p, Actor a) {
        if (a == null || !a.alive) return;
        switch (p.behavior) {
            case "attack_player" -> {
                if (a instanceof Npc n) { n.hostileToPlayer = true; n.fightTarget = g.player.id; n.nextThink = 0; }
                else if (a instanceof Monster m) { m.target = g.player.id; m.state = Monster.State.ENGAGE; }
                else if (a instanceof Animal an) { an.threat = g.player.id; an.goal = Animal.Goal.DEFEND; an.nextThink = 0; }
            }
            case "attack", "hunt" -> {
                Actor t = nearestOf(in, p.arg, a);
                if (t == null) break;
                if (a instanceof Npc n) { n.fightTarget = t.id; n.nextThink = 0; }
                else if (a instanceof Monster m) { m.target = t.id; m.state = Monster.State.ENGAGE; }
                else if (a instanceof Animal an) { an.hunger = Math.max(an.hunger, 0.85f); an.other = t.id; an.goal = Animal.Goal.HUNT; an.nextThink = 0; }
            }
            case "flee" -> {
                Actor from = p.arg.isEmpty() ? g.player : nearestOf(in, p.arg, a);
                if (from == null) from = g.player;
                if (a instanceof Npc n) { n.fearOf = from.id; n.fearX = from.x; n.fearZ = from.z; n.fearSeenAt = g.time; n.nextThink = 0; }
                else if (a instanceof Animal an) { an.threat = from.id; an.threatX = from.x; an.threatZ = from.z; an.fear = 1; an.goal = Animal.Goal.FLEE; an.nextThink = 0; }
            }
            case "migrate" -> {
                if (a instanceof Animal an) {
                    float[] h = heading();
                    float side = (in.id % 2 == 0 ? 1 : -1);
                    float[] to = dry(in.x - h[1] * side * 140, in.z + h[0] * side * 140);
                    // (0.4.8, gamefixes: a migrating herd stood still) the way is gone in stages: its home moved on a
                    // stretch at a time toward the far end (step), so the herd walks it rather than waiting on a path
                    // 140 metres long
                    p.destX = to[0]; p.destZ = to[1];
                    migrateStep(an, p);
                }
            }
            case "scavenge" -> {
                if (a instanceof Animal an) { an.hunger = 0.9f; an.nextThink = 0; an.nextFlight = 0; an.nextFlightCheck = 0; }
            }
            case "graze" -> { if (a instanceof Animal an) { an.hunger = Math.max(an.hunger, 0.7f); an.nextThink = 0; } }
            case "despawn" -> despawn(a);
            default -> { if (a instanceof Npc n) n.nextThink = 0; }
        }
    }

    /** One running encounter's moment: its dialogue, its conditions; and it gives up once everyone in it is gone. */
    private void step(Instance in) {
        if (in.ended) { active.remove(in); return; }
        boolean anyone = false;
        for (var ids : in.cast.values()) for (long id : ids) { Actor a = g.actor(id); if (a != null && a.alive) anyone = true; }
        Map<String, Object> st = stageOf(in);
        // the question put, once its speaker is close enough to put it
        Map<String, Object> dlg = map(st, "dialog");
        if (!dlg.isEmpty() && !in.dialogOpen) {
            Actor sp = first(in, str(dlg, "speaker", ""));
            if (sp instanceof Npc n && n.alive && n.distTo(g.player) < 5.0f && g.time - in.lastDialogAt > 2) {
                in.dialogOpen = true; in.lastDialogAt = g.time;
                if (g.ui != null && !g.scripted) showDialog(in, n, dlg);
                else g.ai.say(n, fill(in, str(dlg, "text", "")));
            }
        }
        for (Object o : list(st, "next")) {
            Map<String, Object> tr = cast(o);
            if (holds(in, str(tr, "when", ""))) { if (g.ui != null && in.dialogOpen) g.ui.closeDialog(); enter(in, str(tr, "to", "")); return; }
        }
        for (var ids : in.cast.values()) for (long id : ids) {
            Part p = parts.get(id);
            if (p != null && p.behavior.equals("migrate") && g.actor(id) instanceof Animal an && an.alive && Math.hypot(an.x - an.homeX, an.z - an.homeZ) < an.range + 2) migrateStep(an, p);
        }
        // (0.4.8, gamefixes: encounter people stood and did nothing) a lurker the player never comes near does not lie in
        // wait for ever: past the director's patience, with the player still within reach, they come out to meet them
        if (g.time - in.stageSince > num(director(), "patience", 40) && Math.hypot(in.x - g.player.x, in.z - g.player.z) < num(director(), "patienceReach", 130))
            for (var ids : in.cast.values()) for (long id : ids) {
                Part p = parts.get(id);
                if (p != null && p.behavior.equals("hide")) { p.behavior = "approach"; p.since = g.time; if (g.actor(id) instanceof Npc n) n.nextThink = 0; }
            }
        // (0.4.8, gamefixes: encounters came too seldom) one the player has gone far from is over: it held its place among
        // the director's few for as long as anyone of it lived, so two left behind on the road kept every other away
        float far = (float) num(director(), "despawnDistance", 170);
        boolean near = Math.hypot(in.x - g.player.x, in.z - g.player.z) < far;
        for (var ids : in.cast.values()) for (long id : ids) { Actor a = g.actor(id); if (a != null && a.alive && a.distTo(g.player) < far) near = true; }
        if (near) in.awayAt = -1; else if (in.awayAt < 0) in.awayAt = g.time; else if (g.time - in.awayAt > num(director(), "leftBehind", 30)) { end(in, "left behind"); return; }
        if (!anyone && !in.ended) end(in, "everyone gone");
    }

    private void showDialog(Instance in, Npc speaker, Map<String, Object> dlg) {
        rpg.ui.Dialog d = new rpg.ui.Dialog(speaker.name, fill(in, str(dlg, "text", "")));
        List<?> choices = list(dlg, "choices");
        for (int i = 0; i < choices.size(); i++) {
            Map<String, Object> c = cast(choices.get(i));
            String why = refusal(c);
            final int k = i;
            String label = fill(in, str(c, "text", "...")) + (why == null ? "" : " [" + why + "]");
            if (why == null) d.choice(label, () -> choose(in, k));
        }
        g.ui.dialog(d);
    }

    /** Why a choice cannot be taken (null: it can): its requirements of the player's purse or pack. */
    private String refusal(Map<String, Object> c) {
        Map<String, Object> req = map(c, "requires");
        if (req.get("coins") instanceof Number n && g.player.count(Items.COINS) < n.intValue()) return "not enough coins";
        if (req.get("item") != null) { ItemDef d = Items.find(String.valueOf(req.get("item"))); if (d == null || g.player.count(d) < 1) return "you have none"; }
        return null;
    }

    /** The player's answer to the question put (the dialogue's buttons call this; so do tests). */
    public void choose(Instance in, int index) {
        if (in.ended) return;
        List<?> choices = list(map(stageOf(in), "dialog"), "choices");
        if (index < 0 || index >= choices.size()) return;
        Map<String, Object> c = cast(choices.get(index));
        if (refusal(c) != null) return;
        boolean pass = test(in, map(c, "test"));
        for (Object ef : list(c, "effects")) if (pass) effect(in, String.valueOf(ef), c);
        g.state.commit("EncounterChoice", g.player.id, in.id, null, 0, in.id() + ": " + str(c, "text", "?"));
        enter(in, pass ? str(c, "to", "") : str(c, "else", str(c, "to", "")));
    }

    private boolean test(Instance in, Map<String, Object> t) {
        if (t.isEmpty()) return true;
        if (t.get("combatOver") instanceof List<?> l && l.size() == 2) return g.player.skills.combatLevel() > in.levels.getOrDefault(String.valueOf(l.get(0)), 1) + n(l.get(1));
        if (t.get("coinsUnder") instanceof Number n) return g.player.count(Items.COINS) < n.intValue();
        if (t.get("honorOver") instanceof Number n) return g.honor.score > n.intValue();
        if (t.get("skillOver") instanceof List<?> l && l.size() == 2) { Skill s = Skill.byName(String.valueOf(l.get(0))); return s != null && g.player.skills.level(s) > n(l.get(1)); }
        return true;
    }

    /** Whether a stage's condition holds now. */
    private boolean holds(Instance in, String when) {
        String[] w = when.split(":");
        double inStage = g.time - in.stageSince;
        switch (w[0]) {
            case "timer" -> { return inStage >= Double.parseDouble(w[1]); }
            case "near" -> { float d = Float.parseFloat(w[2]); for (long id : in.cast.getOrDefault(w[1], List.of())) { Actor a = g.actor(id); if (a != null && a.alive && a.distTo(g.player) < d) return true; } return false; }
            case "player_far" -> {
                float d = Float.parseFloat(w[1]); boolean anyAlive = false;
                for (var ids : in.cast.values()) for (long id : ids) { Actor a = g.actor(id); if (a != null && a.alive) { anyAlive = true; if (a.distTo(g.player) < d) return false; } }
                return anyAlive || Math.hypot(in.x - g.player.x, in.z - g.player.z) > d;
            }
            case "dead" -> {
                List<Long> ids = in.cast.getOrDefault(w[1], List.of());
                if (ids.isEmpty()) return w.length > 2 && w[2].equals("all");
                boolean any = w.length > 2 && w[2].equals("any");
                int dead = 0;
                for (long id : ids) { Actor a = g.actor(id); if (a == null || !a.alive) dead++; }
                return any ? dead > 0 : dead == ids.size();
            }
            case "gone" -> { for (long id : in.cast.getOrDefault(w[1], List.of())) { Actor a = g.actor(id); if (a instanceof Animal an && !an.butchered && g.animals.all().contains(an)) return false; if (a != null && !(a instanceof Animal)) return false; } return true; }
            case "hurt" -> { for (long id : in.cast.getOrDefault(w[1], List.of())) { Actor a = g.actor(id); if (a != null && (a.cs.lastHurt > in.startedAt && a.cs.lastAttacker == g.player.id || !a.alive)) return true; } return false; }
            case "hurt_half" -> {
                List<Long> ids = in.cast.getOrDefault(w[1], List.of());
                if (ids.isEmpty()) return false;
                float hp = 0, max = 0;
                for (long id : ids) { Actor a = g.actor(id); if (a == null) continue; max += a.maxHp(); if (a.alive) hp += a.hp; }
                return max > 0 && hp < max * 0.45f;
            }
            case "arrived" -> { for (long id : in.cast.getOrDefault(w[1], List.of())) { Part p = parts.get(id); if (p != null && p.arrived) return true; } return false; }
            case "wanted" -> { String jur = DevMap.jurisdiction(in.x, in.z); return g.law.bounty(g.player.id, jur) > 0; }
            default -> { return false; }
        }
    }

    /** One of an encounter's effects. */
    private void effect(Instance in, String e, Map<String, Object> choice) {
        String[] w = e.split(":");
        switch (w[0]) {
            case "pay" -> {
                Actor to = first(in, w[1]);
                int amount = w[2].equals("percent") ? Math.max(Integer.parseInt(w[5]), g.player.count(Items.COINS) * Integer.parseInt(w[3]) / 100) : Integer.parseInt(w[3]);
                amount = Math.min(amount, g.player.count(Items.COINS));
                move(g.player, to, Items.COINS, amount);
            }
            case "give" -> {
                ItemDef d = Items.find(w[1]);
                if (d == null) break;
                move(g.player, first(in, w.length > 3 ? w[3] : ""), d, Math.min(g.player.count(d), Integer.parseInt(w[2])));
            }
            case "reward" -> { ItemDef d = Items.find(w[1]); if (d != null) { int k = Integer.parseInt(w[2]); g.grant(g.player, new ItemStack(d, k)); g.state.mint(d, k); g.chat.add("You receive " + k + " " + d.name.toLowerCase() + ".", rpg.Chat.Kind.GAME); } }
            case "honor" -> g.honor.event("encounter", g.player.id, Integer.parseInt(w[1]), in.id() + ":" + in.id + ":" + w[1], false, w.length > 2 ? e.substring(e.indexOf(':', e.indexOf(':') + 1) + 1) : in.id());
            case "rumor" -> {
                // the people in it carry the tale (and anyone who saw); the road's travellers repeat the freshest
                String tale = e.substring(6);
                Actor teller = null;
                for (var ids : in.cast.values()) for (long id : ids) if (g.actor(id) instanceof Npc n && n.alive) teller = n;
                g.ai.rumors.deed(tale, teller == null ? g.player.id : teller.id);
                heard.add(0, tale);
                while (heard.size() > 8) heard.remove(heard.size() - 1);
            }
            case "flag" -> flags.put(w[1], true);
            case "crime" -> { Actor v = first(in, w.length > 2 ? w[2] : ""); g.law.crime(g.player, w[1], v == null ? 0 : v.id, 0, null, 0); }
            case "hostile" -> { for (long id : in.cast.getOrDefault(w[1], List.of())) if (g.actor(id) instanceof Npc n) { n.hostileToPlayer = true; n.fightTarget = g.player.id; } }
            case "free" -> { for (long id : in.cast.getOrDefault(w[1], List.of())) { Part p = parts.get(id); if (p != null) { p.bound = false; if (p.destX != p.destX) destination(p, "settlement", new float[]{in.x, in.z}); } } }
            case "heal" -> { for (long id : in.cast.getOrDefault(w[1], List.of())) { Actor a = g.actor(id); if (a != null && a.alive) a.restore(); } }
            case "trade" -> { Actor a = first(in, w[1]); if (a instanceof Npc n && g.ui != null && shops.get(n.id) != null) g.ui.openShop(shops.get(n.id)); }
            case "surrender" -> {
                Actor a = first(in, "hunter");
                String jur = null;
                for (var re : WorldMap.shadowveil() ? rpg.world.shadowveil.ShadowVeil.realms() : List.<rpg.world.shadowveil.ShadowVeil.Realm>of()) if (g.law.bounty(g.player.id, re.name()) > 0) { jur = re.name(); break; }
                if (jur == null) jur = DevMap.ALPHA;
                g.law.arrest(g.player, jur, a instanceof Npc n ? n : null);
            }
            default -> System.err.println("[encounters] unknown effect " + e + " in " + in.id());
        }
    }
    private void move(Actor from, Actor to, ItemDef d, int amount) {
        if (amount <= 0 || from == null) return;
        var taken = from.takeItems(d, amount);
        int got = 0;
        for (ItemStack s : taken) { got += s.amount; if (to != null) to.store(s); }
        if (from == g.player && got > 0) g.chat.add("You hand over " + got + " " + d.name.toLowerCase() + ".", rpg.Chat.Kind.GAME);
    }

    private void end(Instance in, String outcome) {
        if (in.ended) return;
        in.ended = true; in.outcome = outcome;
        lastEnded = g.state.minutes;
        lastAt.put(in.id(), g.state.minutes);
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("id", in.id()); h.put("minute", g.state.minutes); h.put("region", in.region); h.put("outcome", outcome);
        history.add(h);
        while (history.size() > 300) history.remove(0);
        g.state.commit("EncounterEnded", g.player.id, in.id, DevMap.jurisdiction(in.x, in.z), 0, in.id() + ": " + outcome);
        // those left go on as they were last doing (a traveller on to town, an outlaw away to camp); the parts' tidy lets them go
        for (var ids : in.cast.values()) for (long id : ids) {
            Part p = parts.get(id);
            if (p == null) continue;
            if (p.behavior.equals("hold") || p.behavior.equals("surround") || p.behavior.equals("block") || p.behavior.equals("hide") || p.behavior.equals("approach") || p.behavior.equals("plead"))
                p.behavior = p.destX == p.destX ? "travel" : "leave";
        }
    }

    // ================================================================== the parts' minds
    /** For the brain: what an encounter person is doing now (null: their own mind has them - a drifter, a patrol's arrest). */
    public Brain.Goal think(Npc n) {
        Part p = n.encounter;
        if (p == null || p.drifter) return null;
        return switch (p.behavior) {
            case "attack_player", "attack", "chase" -> {
                Actor t = p.behavior.equals("attack_player") ? g.player : nearestOf(instance(p), p.arg, n);
                if (t == null || !t.alive) { p.behavior = "leave"; yield Brain.Goal.ENCOUNTER; }
                if (p.behavior.equals("chase") && n.distTo(t) > 2.2f) yield Brain.Goal.ENCOUNTER;
                n.fightTarget = t.id; if (t == g.player) n.hostileToPlayer = true;
                yield Brain.Goal.FIGHT;
            }
            case "flee" -> { if (n.fearOf == 0) { n.fearOf = g.player.id; n.fearX = g.player.x; n.fearZ = g.player.z; } n.fearSeenAt = g.time; yield Brain.Goal.FLEE; }
            case "arrest", "drift" -> null;
            default -> Brain.Goal.ENCOUNTER;
        };
    }

    /** (0.4.8) Whether a cast member is one of the fighting kind (a monster, a predator, an armed role): its level follows the place's danger. */
    private static boolean fights(Map<String, Object> c) {
        String kind = str(c, "kind", "person");
        if (kind.equals("monster")) return true;
        if (kind.equals("animal")) { try { return Species.valueOf(str(c, "species", "DEER")).diet == Species.Diet.HUNTER; } catch (IllegalArgumentException e) { return false; } }
        String role = str(c, "role", "VILLAGER");
        return role.equals("BANDIT") || role.equals("BRUTE") || role.equals("THIEF") || role.equals("HUNTER") || role.equals("KNIGHT") || role.equals("GUARD");
    }
    /** (0.4.8) Whether an encounter person's present behaviour is a fight the encounter set (attack, hunt, chase). */
    public boolean fights(Npc n) {
        Part p = n.encounter;
        return p != null && (p.behavior.equals("attack") || p.behavior.equals("attack_player") || p.behavior.equals("hunt") || p.behavior.equals("chase"));
    }

    /** For the brain: an encounter person's moment under their present behaviour. */
    public void act(Npc n, float dt) {
        Part p = n.encounter;
        if (p == null) { g.ai.desire(n, rpg.ai.AiSystem.A_IDLE); return; }
        Brain b = g.ai.brain;
        Instance in = instance(p);
        float px = g.player.x, pz = g.player.z;
        n.sneaking = false; n.kneeling = false;
        switch (p.behavior) {
            case "hide" -> { n.sneaking = true; if (b.travel(n, p.spawnX, n.y, p.spawnZ, false, 0.8f)) { g.ai.desire(n, rpg.ai.AiSystem.A_HOLD); face(n, px, pz); } }
            case "block" -> {
                // into the way ahead of the player, a few steps apart, facing them
                float dx = n.x - px, dz = n.z - pz, l = Math.max(0.1f, (float) Math.hypot(dx, dz));
                float tx = px + dx / l * 5.5f + (p.index - 1) * 1.4f * (dz / l), tz = pz + dz / l * 5.5f - (p.index - 1) * 1.4f * (dx / l);
                if (b.travel(n, tx, n.y, tz, true, 1.0f)) { g.ai.desire(n, rpg.ai.AiSystem.A_HOLD); face(n, px, pz); }
            }
            case "approach" -> { if (b.travel(n, px, g.player.y, pz, false, 2.6f)) { g.ai.desire(n, rpg.ai.AiSystem.A_HOLD); face(n, px, pz); } }
            case "chase" -> { Actor t = nearestOf(in, p.arg, n); if (t != null) b.travel(n, t.x, t.y, t.z, true, 1.8f); else g.ai.desire(n, rpg.ai.AiSystem.A_IDLE); }
            case "surround" -> {
                int count = in == null ? 1 : Math.max(1, in.cast.getOrDefault(p.tag, List.of()).size());
                double a = Math.atan2(n.z - pz, n.x - px) * 0 + p.index * Math.PI * 2 / count + (in == null ? 0 : in.id);
                float tx = px + (float) Math.cos(a) * 3.4f, tz = pz + (float) Math.sin(a) * 3.4f;
                if (b.travel(n, tx, g.player.y, tz, false, 0.9f)) { g.ai.desire(n, rpg.ai.AiSystem.A_HOLD); face(n, px, pz); }
            }
            case "hold" -> {
                g.ai.desire(n, rpg.ai.AiSystem.A_HOLD);
                // (0.4.8) held in place but alive: turned to whoever of the encounter is nearest them (a quarrel's other
                // party, a patrol's fellows) until the player comes close, and talking now and then
                Actor other = null; float od = 6;
                if (in != null) for (var ids : in.cast.values()) for (long id : ids) { Actor o = g.actor(id); if (o != null && o != n && o.alive && n.distTo(o) < od) { od = n.distTo(o); other = o; } }
                if (n.distTo(g.player) < 9 || other == null) face(n, px, pz); else face(n, other.x, other.z);
                chatter(n, p, in);
            }
            case "plead" -> { n.sneaking = true; n.kneeling = true; g.ai.desire(n, rpg.ai.AiSystem.A_HOLD); if (n.distTo(g.player) < 14) face(n, px, pz); chatter(n, p, in); }
            case "travel" -> {
                if (p.bound) { followFirst(n, in, "escort", b); break; }
                if (p.destX != p.destX) { p.behavior = "leave"; break; }
                if (b.travel(n, p.destX, WorldMap.standY(p.destX, p.destZ), p.destZ, p.run, 5f)) { p.arrived = true; g.ai.desire(n, rpg.ai.AiSystem.A_IDLE); }
            }
            case "follow" -> followFirst(n, in, p.arg, b);
            case "lead" -> {
                Actor t = first(in, p.arg);
                if (t == null) { g.ai.desire(n, rpg.ai.AiSystem.A_IDLE); break; }
                if (n.distTo(g.player) > 9) { g.ai.desire(n, rpg.ai.AiSystem.A_HOLD); face(n, px, pz); if (n.speech == null && g.time - p.since > 6) { g.ai.say(n, "This way - keep up."); p.since = g.time; } }
                else b.travel(n, t.x, t.y, t.z, false, 4f);
            }
            case "leave" -> {
                float dx = n.x - px, dz = n.z - pz, l = Math.max(0.1f, (float) Math.hypot(dx, dz));
                if (Float.isNaN(p.campX)) { float[] c = dry(n.x + dx / l * 70, n.z + dz / l * 70); p.campX = c[0]; p.campZ = c[1]; }
                if (b.travel(n, p.campX, WorldMap.standY(p.campX, p.campZ), p.campZ, false, 3f)) settle(n, p);
            }
            default -> g.ai.desire(n, rpg.ai.AiSystem.A_IDLE);
        }
    }
    /** (0.4.8) A migrating animal's home moved on a stretch toward the far end of its way (or onto it, near the end). */
    private void migrateStep(Animal an, Part p) {
        if (p.destX != p.destX) return;
        float dx = p.destX - an.x, dz = p.destZ - an.z, l = (float) Math.hypot(dx, dz);
        float stride = (float) num(director(), "migrateStride", 18);
        float[] to = l <= stride ? new float[]{p.destX, p.destZ} : dry(an.x + dx / l * stride, an.z + dz / l * stride);
        an.homeX = to[0]; an.homeZ = to[1]; an.range = 5; an.nextThink = 0;
        if (an.goal != Animal.Goal.FLEE && an.goal != Animal.Goal.DEFEND) an.goal = Animal.Goal.WANDER;
    }
    /** (0.4.8) Someone of an encounter left standing speaks up now and then: the stage's lines for their party again, while the player is about. */
    private void chatter(Npc n, Part p, Instance in) {
        if (in == null || n.speech != null || g.time - p.since < 7 + p.index * 2.5 || n.distTo(g.player) > 40) return;
        p.since = g.time;
        Object lines = map(stageOf(in), "say").get(p.tag);
        if (lines == null) lines = map(stageOf(in), "chatter").get(p.tag);
        List<?> l = lines instanceof List<?> ll ? ll : lines == null ? List.of() : List.of(String.valueOf(lines));
        if (!l.isEmpty()) g.ai.say(n, fill(in, String.valueOf(l.get((int) ((g.time * 3 + n.id) % l.size())))));
    }
    private void followFirst(Npc n, Instance in, String tag, Brain b) {
        Actor t = first(in, tag);
        if (t == null || !t.alive) { n.encounter.behavior = "leave"; return; }
        if (n.distTo(t) > 2.6f) b.travel(n, t.x, t.y, t.z, t.distTo(n) > 8, 2f); else g.ai.desire(n, rpg.ai.AiSystem.A_HOLD);
    }
    private void face(Npc n, float x, float z) { n.wantYaw = (float) Math.atan2(-(x - n.x), -(z - n.z)); }

    /** Come to their camp: an outlaw stays as a drifter (their own day about it) while there is room; anyone else is let go when unseen. */
    private void settle(Npc n, Part p) {
        if (p.drift.equals("camp") && drifters() < (int) num(map(data, "drifters"), "most", 10)) {
            p.drifter = true; p.behavior = "drift";
            n.homeX = n.postX = n.spawnX = p.campX; n.homeZ = n.postZ = n.spawnZ = p.campZ;
            n.nextThink = 0;
            g.state.commit("EncounterDrifter", 0, n.id, DevMap.jurisdiction(n.x, n.z), 0, n.name + " keeps a camp near " + Math.round(p.campX) + "," + Math.round(p.campZ));
        } else p.behavior = "gone";
    }
    private int drifters() { int k = 0; for (Part p : parts.values()) if (p.drifter) k++; return k; }

    /** The parts no encounter needs any more: let go when far off and unseen (the drifters kept, to their limit). */
    private void tidy() {
        if (g.time < tidyAt) return;
        tidyAt = g.time + 1;
        float far = (float) num(director(), "despawnDistance", 170);
        for (var e : new ArrayList<>(parts.entrySet())) {
            Actor a = g.actor(e.getKey());
            Part p = e.getValue();
            if (a == null) { parts.remove(e.getKey()); continue; }
            Instance in = instance(p);
            if (in != null && !in.ended) continue;
            if (!a.alive) { parts.remove(e.getKey()); if (a instanceof Npc n) n.encounter = null; continue; }
            boolean seen = g.vox.sight(g.player.x, g.player.eyeY(), g.player.z, a.x, a.y + a.height * 0.6f, a.z);
            float d = a.distTo(g.player);
            if (p.drifter) {
                if (d > far * 3 && !seen && g.time - p.since > num(map(data, "drifters"), "forgetAfter", 2880) * 60 / Math.max(0.01, g.minuteRate())) { despawn(a); parts.remove(e.getKey()); }
                continue;
            }
            if (p.behavior.equals("gone") || p.arrived || d > far && !seen) {
                if (d > 40 && !seen || d > far) { despawn(a); parts.remove(e.getKey()); }
            }
        }
    }
    private double tidyAt;

    private void despawn(Actor a) {
        if (a == null) return;
        if (a instanceof Npc n) { g.ai.despawnBody(n); g.npcs.remove(n); n.encounter = null; shops.remove(n.id); }
        else if (a instanceof Animal an) g.animals.remove(an);
        else if (a instanceof Monster m) g.monsters.remove(m);
    }

    // ================================================================== looking things up
    public Instance instance(Part p) { if (p == null) return null; for (Instance in : active) if (in.id == p.instance) return in; return null; }
    private Actor first(Instance in, String tag) {
        if (in == null) return null;
        for (long id : in.cast.getOrDefault(tag, List.of())) { Actor a = g.actor(id); if (a != null && a.alive) return a; }
        for (long id : in.cast.getOrDefault(tag, List.of())) { Actor a = g.actor(id); if (a != null) return a; }
        return null;
    }
    private Actor nearestOf(Instance in, String tag, Actor from) {
        if (in == null) return null;
        Actor best = null; float bd = Float.MAX_VALUE;
        for (long id : in.cast.getOrDefault(tag, List.of())) { Actor a = g.actor(id); if (a == null || !a.alive) continue; float d = a.distTo(from); if (d < bd) { bd = d; best = a; } }
        return best;
    }
    private String fill(Instance in, String text) {
        if (text.contains("{toll}")) text = text.replace("{toll}", String.valueOf(Math.max(15, g.player.count(Items.COINS) / 10)));
        if (text.contains("{rumor}")) text = text.replace("{rumor}", heard.isEmpty() ? str(data, "quietWord", "the roads are quieter than they were, but not by much.") : "they say someone " + heard.get((int) (in.id % heard.size())) + ".");
        if (text.contains("{destination}")) { Part p = null; for (var ids : in.cast.values()) for (long id : ids) if (parts.get(id) != null && !parts.get(id).destName.isEmpty()) p = parts.get(id); text = text.replace("{destination}", p == null ? "the next town" : p.destName); }
        return text;
    }
    static String region(float x, float z) {
        var t = WorldMap.shadowveil() ? rpg.world.shadowveil.ShadowVeil.terrain() : null;
        String r = t == null ? null : t.regionId((int) Math.floor(x), (int) Math.floor(z));
        return r == null ? "devmap" : r;
    }
    static String biome(float x, float z) {
        var t = WorldMap.shadowveil() ? rpg.world.shadowveil.ShadowVeil.terrain() : null;
        return t == null || !t.in((int) x, (int) z) ? "meadow" : t.biomeAt((int) Math.floor(x), (int) Math.floor(z));
    }
    /** The nearest road cell within a reach of a point, else null. */
    /** (probes) The nearest road cell within reach. */
    public static int[] roadWithinPublic(float x, float z, int reach) { return roadWithin(x, z, reach); }
    static int[] roadWithin(float x, float z, int reach) {
        var t = WorldMap.shadowveil() ? rpg.world.shadowveil.ShadowVeil.terrain() : null;
        if (t == null) return new int[]{(int) x, (int) z};
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int r = 0; r <= reach; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int cx = bx + dx, cz = bz + dz;
            if (t.in(cx, cz) && (t.flags[t.idx(cx, cz)] & rpg.world.shadowveil.Terrain.ROAD) != 0) return new int[]{cx, cz};
        }
        return null;
    }
    private static boolean settledWithin(float x, float z, float reach) {
        var t = WorldMap.shadowveil() ? rpg.world.shadowveil.ShadowVeil.terrain() : null;
        if (t == null || t.arch == null) return false;
        for (var st : t.arch.structures) if (Math.abs(st.cx() - x) < reach && Math.abs(st.cz() - z) < reach && Math.hypot(st.cx() - x, st.cz() - z) < reach) return true;
        return false;
    }

    // ================================================================== saving
    public Map<String, Object> save() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("history", new ArrayList<>(history));
        m.put("lastAt", new LinkedHashMap<>(lastAt));
        m.put("flags", new LinkedHashMap<>(flags));
        m.put("draws", (double) draws); m.put("lastEnded", lastEnded); m.put("enabled", enabled);
        return m;
    }
    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> m) {
        history.clear(); lastAt.clear(); flags.clear();
        if (m == null) return;
        if (m.get("history") instanceof List<?> l) for (Object o : l) if (o instanceof Map<?, ?> h) history.add((Map<String, Object>) h);
        if (m.get("lastAt") instanceof Map<?, ?> l) for (var e : l.entrySet()) if (e.getValue() instanceof Number v) lastAt.put(String.valueOf(e.getKey()), v.doubleValue());
        if (m.get("flags") instanceof Map<?, ?> l) for (var e : l.entrySet()) flags.put(String.valueOf(e.getKey()), Boolean.TRUE.equals(e.getValue()));
        draws = (long) num(m, "draws", 0); lastEnded = num(m, "lastEnded", -1e9); enabled = !Boolean.FALSE.equals(m.get("enabled"));
    }
    /** A person of an encounter, as the save keeps them (their part; the save keeps the body as anyone's). */
    public Map<String, Object> savePart(Npc n) {
        Part p = n.encounter;
        if (p == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tag", p.tag); m.put("behavior", p.drifter ? "drift" : p.behavior.equals("travel") ? "travel" : "leave"); m.put("drifter", p.drifter);
        m.put("campX", Float.isNaN(p.campX) ? (double) n.x : (double) p.campX); m.put("campZ", Float.isNaN(p.campZ) ? (double) n.z : (double) p.campZ);
        m.put("destX", Float.isNaN(p.destX) ? -1.0 : (double) p.destX); m.put("destZ", Float.isNaN(p.destZ) ? -1.0 : (double) p.destZ); m.put("destName", p.destName);
        m.put("drift", p.drift); m.put("role", n.role.name()); m.put("title", n.title); m.put("look", n.id);
        return m;
    }
    /** Back from a save: the person made again with their part (an encounter under way when it was saved goes on as its aftermath). */
    public Npc restorePart(long id, String name, Map<String, Object> m, float x, float z) {
        Npc.Role role;
        try { role = Npc.Role.valueOf(str(m, "role", "VILLAGER")); } catch (IllegalArgumentException e) { role = Npc.Role.VILLAGER; }
        Npc n = new Npc(id, name, role, str(m, "title", "Traveller"), x, z);
        n.look = rpg.actor.Appearance.random(id * 31, (id & 1) == 0);
        Part p = new Part(-1, str(m, "tag", "?"));
        p.behavior = str(m, "behavior", "leave"); p.drifter = Boolean.TRUE.equals(m.get("drifter")); p.drift = str(m, "drift", "");
        p.campX = (float) num(m, "campX", x); p.campZ = (float) num(m, "campZ", z);
        double dx = num(m, "destX", -1);
        if (dx >= 0) { p.destX = (float) dx; p.destZ = (float) num(m, "destZ", z); p.destName = str(m, "destName", ""); }
        if (p.drifter) { n.homeX = n.postX = n.spawnX = p.campX; n.homeZ = n.postZ = n.spawnZ = p.campZ; }
        n.encounter = p;
        parts.put(id, p);
        return n;
    }

    // ================================================================== the console's
    /** The console's 'encounter' command: list, why, force <id>, active, history, on, off, seed <n>, clear. */
    public String console(String[] a) {
        String sub = a.length > 1 ? a[1] : "list";
        StringBuilder out = new StringBuilder();
        switch (sub) {
            case "list" -> { for (var d : defs.values()) out.append(str(d, "id", "?")).append(" (").append(str(d, "rarity", "?")).append(", ").append(str(d, "type", "?")).append(")\n"); }
            case "why" -> { out.append("here: ").append(region(g.player.x, g.player.z)).append(" / ").append(biome(g.player.x, g.player.z)).append(" / ").append(DevMap.jurisdiction(g.player.x, g.player.z)).append(String.format(" density %.2f%n", densityHere()));
                for (var d : defs.values()) { String why = ineligible(d, false); out.append(str(d, "id", "?")).append(": ").append(why == null ? "ELIGIBLE" : why).append('\n'); } }
            case "force" -> {
                Map<String, Object> d = a.length > 2 ? defs.get(a[2]) : choose(rng(), true);
                if (d == null) return "no such encounter (encounter list)";
                Instance in = start(d, rng());
                return in == null ? "could not start: " + lastWhy : "started " + in.id() + " #" + in.id + " at " + Math.round(in.x) + "," + Math.round(in.z);
            }
            case "active" -> { for (Instance in : active) out.append('#').append(in.id).append(' ').append(in.id()).append(" stage ").append(in.stage).append(String.format(" %.0f m off%n", Math.hypot(in.x - g.player.x, in.z - g.player.z))); out.append(drifters()).append(" drifters\n"); }
            case "history" -> { for (var h : history.subList(Math.max(0, history.size() - 15), history.size())) out.append(h).append('\n'); }
            case "on" -> { enabled = true; return "the encounter director is on"; }
            case "off" -> { enabled = false; return "the encounter director is off"; }
            case "clear" -> { for (Instance in : active) end(in, "cleared"); for (long id : new ArrayList<>(parts.keySet())) despawn(g.actor(id)); parts.clear(); return "cleared"; }
            case "seed" -> { draws = a.length > 2 ? Long.parseLong(a[2]) : 0; return "draws reset to " + draws; }
            default -> { return "encounter list | why | force [id] | active | history | on | off | clear | seed <n>"; }
        }
        return out.toString().trim();
    }

    // ================================================================== json helpers
    @SuppressWarnings("unchecked")
    static Map<String, Object> cast(Object o) { return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }
    static Map<String, Object> map(Map<?, ?> m, String k) { return m != null && m.get(k) instanceof Map<?, ?> v ? cast(v) : Map.of(); }
    static List<?> list(Map<?, ?> m, String k) { return m != null && m.get(k) instanceof List<?> l ? l : List.of(); }
    static String str(Map<?, ?> m, String k, String d) { Object o = m == null ? null : m.get(k); return o == null ? d : String.valueOf(o); }
    static double num(Map<?, ?> m, String k, double d) { return m != null && m.get(k) instanceof Number n ? n.doubleValue() : d; }
    static int n(Object o) { return o instanceof Number v ? v.intValue() : 0; }
    static double n2(Object o) { return o instanceof Number v ? v.doubleValue() : 0; }
}
