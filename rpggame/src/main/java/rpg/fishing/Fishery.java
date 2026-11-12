package rpg.fishing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Result;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.world.Coast;
import rpg.world.DevMap;
import rpg.world.Expanse;

/**
 * (0.3.5, gamefixes: fishing involves real fish AI) the fish of every water, as stock and as living fish. Each water - a
 * pond, the stream, the brook, the lake, the reaches of the sea - keeps a stock of each kind that lives in it, which
 * breeds back slowly toward what the water can hold. Near the player that stock swims as real fish: each one wanders,
 * shoals with its own kind, keeps its depth, turns from the banks, startles at a splash or a swimmer, flees a hunter,
 * hunts if it is a hunter, comes to a line or a pot it can be taken with - and bites. A catch is that fish, out of the
 * water; away from the player a cast is answered from the stock instead, by the same rules (so the people who fish for a
 * living empty the same waters the player does).
 *
 * <p>Fighting a fish is fighting: a blade, an arrow or a fist that hurts one trains the fighting skills, never Fishing,
 * and one killed floats up to be picked from the water. The one fish landed by fighting it is the tuna taken barehanded
 * (Fishing {@value FishKind#BAREHANDED_FISHING} and Strength {@value FishKind#BAREHANDED_STRENGTH}: a punch that lands
 * grabs it).
 */
public final class Fishery {
    /** One body of water and its stock: the kind of water, its bounds, how many of each kind it holds and can hold. */
    public static final class Water {
        public final String name;
        public final int cls, x0, z0, x1, z1;
        public final int[] capacity = new int[FishKind.values().length], stock = new int[FishKind.values().length];
        public boolean live;
        public double regrowAt;
        Water(String name, int cls, int x0, int z0, int x1, int z1) { this.name = name; this.cls = cls; this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; }
        Water hold(FishKind k, int n) { capacity[k.ordinal()] = n; stock[k.ordinal()] = n; return this; }
        public boolean contains(float x, float z) { return x >= x0 && x < x1 + 1 && z >= z0 && z < z1 + 1; }
        float distance(float x, float z) { float dx = Math.max(Math.max(x0 - x, 0), x - x1 - 1), dz = Math.max(Math.max(z0 - z, 0), z - z1 - 1); return (float) Math.hypot(dx, dz); }
        public int total() { int n = 0; for (int s : stock) n += s; return n; }
    }

    /** A line, a net or a pot in the water: who set it, where, with what gear, and the fish on it once one has bitten. */
    public static final class Lure {
        public final Actor who;
        public final Object spot;
        public final ItemDef gear;
        public final float x, y, z;
        public final double since;
        public double until;
        public boolean bait;
        public Fish hooked;
        Lure(Actor who, Object spot, ItemDef gear, float x, float y, float z, double now) { this.who = who; this.spot = spot; this.gear = gear; this.x = x; this.y = y; this.z = z; this.since = now; }
    }

    /** How near the player a water swims as live fish (m), and the most live fish there are at once. */
    public static final float LIVE_RANGE = 64;
    public static final int LIVE_MAX = 260;
    /** How far a line draws fish to it (m), with bait and without; how long a cast is waited on before giving up (s). */
    public static final float DRAW = 7, DRAW_BAITED = 12;
    public static final double GIVE_UP = 45;

    private final Rpg g;
    public final List<Water> waters = new ArrayList<>();
    public final List<Fish> live = new ArrayList<>();
    public final List<Lure> lures = new ArrayList<>();
    private final Random rnd = new Random(0x5EA5EAL);
    private long nextId = Fish.ID_BASE + 1;
    /**
     * (0.3.6, gamefixes: aggressive optimization) the live fish bucketed by GRID-metre cells, rebuilt each frame: a fish's
     * neighbours (its shoal, its hunters, what it bumps into) are looked for in the cells about it, not through every fish.
     */
    static final float GRID = 4f;
    private final Map<Long, List<Fish>> grid = new HashMap<>();
    private final List<List<Fish>> gridPool = new ArrayList<>();
    private int gridUsed;
    private double thinkClock;
    /** Splashes and blows in the water this moment (x, z, radius, until): the fish there startle. */
    private final List<float[]> alarms = new ArrayList<>();
    /** Swimmers and waders this frame (anyone in water deep enough to be among the fish). */
    private final List<Actor> inWater = new ArrayList<>();
    public long caught, eaten, killed;

    public Fishery(Rpg g) {
        this.g = g;
        // the fresh waters
        waters.add(new Water("the fishing pond", FishKind.W.POND, DevMap.POND_X0, DevMap.POND_Z0, DevMap.POND_X1, DevMap.POND_Z1)
                .hold(FishKind.SHRIMP, 10).hold(FishKind.GUPPY, 12).hold(FishKind.CRAB, 4).hold(FishKind.TROUT, 4).hold(FishKind.BASS, 3).hold(FishKind.CATFISH, 3));
        waters.add(new Water("the wild pond", FishKind.W.POND, DevMap.WILD_POND_X0, DevMap.WILD_POND_Z0, DevMap.WILD_POND_X1, DevMap.WILD_POND_Z1)
                .hold(FishKind.GUPPY, 6).hold(FishKind.SHRIMP, 6).hold(FishKind.TROUT, 2));
        waters.add(new Water("the duck pond", FishKind.W.POND, DevMap.DUCK_POND_X0, DevMap.DUCK_POND_Z0, DevMap.DUCK_POND_X1, DevMap.DUCK_POND_Z1)
                .hold(FishKind.GUPPY, 6).hold(FishKind.SHRIMP, 3));
        waters.add(new Water("the stream", FishKind.W.RIVER, DevMap.STREAM_X0 - 1, DevMap.STREAM_Z0, DevMap.STREAM_X1 + 1, DevMap.STREAM_Z1)
                .hold(FishKind.TROUT, 4).hold(FishKind.SALMON, 2));
        waters.add(new Water("the brook", FishKind.W.RIVER, Expanse.BROOK_X0 - 1, Expanse.BROOK_Z0, Expanse.BROOK_X1 + 1, Coast.shore(60) - 1)
                .hold(FishKind.TROUT, 6).hold(FishKind.SALMON, 4).hold(FishKind.CATFISH, 2).hold(FishKind.ELECTRIC_EEL, 2));
        waters.add(new Water("the lake", FishKind.W.LAKE, Expanse.LAKE_X0 - 1, Expanse.LAKE_Z0 - 1, Expanse.LAKE_X1 + 1, Expanse.LAKE_Z1 + 1)
                .hold(FishKind.SHRIMP, 10).hold(FishKind.GUPPY, 10).hold(FishKind.CRAB, 4).hold(FishKind.TROUT, 6).hold(FishKind.BASS, 6).hold(FishKind.CATFISH, 5).hold(FishKind.ELECTRIC_EEL, 3));
        // the sea, in four reaches along the coast, each with its shallows, its open water and its deep
        for (int i = 0; i < 4; i++) {
            int x0 = 1 + i * 48, x1 = Math.min(DevMap.SX - 2, x0 + 47);
            String reach = new String[]{"the western", "the brook mouth's", "the pier's", "the eastern"}[i];
            waters.add(new Water(reach + " shallows", FishKind.W.SHORE, x0, 194, x1, 214)
                    .hold(FishKind.SHRIMP, 8).hold(FishKind.SARDINE, 14).hold(FishKind.HERRING, 8).hold(FishKind.CRAB, 3).hold(FishKind.LOBSTER, 3).hold(FishKind.STARFISH, 4));
            waters.add(new Water(reach + " sea", FishKind.W.SEA, x0, 203, x1, 226)
                    .hold(FishKind.SARDINE, 6).hold(FishKind.HERRING, 6).hold(FishKind.MACKEREL, 10).hold(FishKind.COD, 4).hold(FishKind.SALMON, 3).hold(FishKind.SQUID, 3).hold(FishKind.HADDOCK, 3));
            Water deep = new Water(reach + " deep", FishKind.W.DEEP, x0, 218, x1, Coast.Z1 - 1)
                    .hold(FishKind.TUNA, 5).hold(FishKind.COD, 3).hold(FishKind.SQUID, 3).hold(FishKind.HADDOCK, 2).hold(FishKind.SWORDFISH, 1).hold(FishKind.SHARK, 1).hold(FishKind.ANGLERFISH, 2);
            if (i % 2 == 1) deep.hold(FishKind.BLUE_MARLIN, 1);
            if (i % 2 == 0) deep.hold(FishKind.WHALE, 1);
            if (i == 3 || i == 2) deep.hold(FishKind.BLACK_MARLIN, 1);
            waters.add(deep);
        }
    }

    // ------------------------------------------------------------------ the waters
    /** Whether a cell of water belongs to a water's kind (the sea's reaches overlap: the depth decides). */
    private boolean cellOf(Water w, int x, int z) {
        if (!w.contains(x + 0.5f, z + 0.5f)) return false;
        return switch (w.cls) {
            case FishKind.W.SHORE -> Coast.shelf(x, z);
            case FishKind.W.SEA -> Coast.depth(x, z) >= 2 && !Coast.shelf(x, z) && !Coast.deep(x, z);
            case FishKind.W.DEEP -> Coast.deep(x, z);
            default -> g.fluids.water(x, rpg.world.Ground.cell(x, z) - 1, z);
        };
    }
    /** The water a point is in (the sea's: the reach whose kind of water it is), or null. */
    public Water waterAt(float x, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (Water w : waters) if (w.contains(x, z) && cellOf(w, bx, bz)) return w;
        for (Water w : waters) if (w.contains(x, z)) return w;
        return null;
    }
    /** The waters a fishing spot reaches: the one it is in, and (at sea) the reaches next to it within a cast. */
    public List<Water> watersNear(float x, float z, float r) {
        List<Water> l = new ArrayList<>();
        for (Water w : waters) if (w.distance(x, z) <= r) l.add(w);
        return l;
    }
    /** The kinds a gear takes in the waters by a spot, the least level first (none: the gear is wrong for these waters). */
    public List<FishKind> takenHere(float x, float z, ItemDef gear) {
        List<FishKind> l = new ArrayList<>();
        for (Water w : watersNear(x, z, 2.5f)) for (FishKind k : FishKind.values()) if (w.capacity[k.ordinal()] > 0 && k.takenBy(gear) && !l.contains(k)) l.add(k);
        l.sort((a, b) -> Integer.compare(a.level, b.level));
        return l;
    }

    // ------------------------------------------------------------------ lines in the water
    /** A cast at a spot (x, z on the water): drawn fish come to it while its caster keeps at it. */
    public Lure cast(Actor who, Object spot, ItemDef gear, float x, float z) {
        // (0.4.1) casting again at the same spot with the same gear keeps the line that is out: a fish already drawn to
        // it lost it to every fresh cast and turned away before it got there
        for (Lure l : lures) if (l.who == who && l.spot == spot && l.gear == gear && l.hooked == null && g.time - l.since < GIVE_UP) return l;
        lures.removeIf(l -> l.who == who);
        float s = g.fluids.surface(x, rpg.world.Ground.at(x, z), z);
        if (Float.isNaN(s)) s = DevMap.FLOOR - 0.1f;
        float bed = bed(x, z, s);
        boolean pot = FishKind.methodOf(gear) == FishKind.Method.POT;
        Lure l = new Lure(who, spot, gear, x, pot ? bed + 0.15f : Math.max(bed + 0.3f, s - 0.6f), z, g.time);
        l.bait = FishKind.methodOf(gear) == FishKind.Method.ROD && who.has(Items.FISHING_BAIT, 1);
        l.until = g.time + GIVE_UP + 10;
        lures.add(l);
        return l;
    }
    public Lure lureOf(Actor who) { for (Lure l : lures) if (l.who == who) return l; return null; }
    /** The line is out of the water (the work ended): a fish on it gets away. */
    public void reel(Actor who) {
        for (Lure l : lures) if (l.who == who && l.hooked != null) { Fish f = l.hooked; f.lure = null; startle(f, l.x, l.z, 2.5f); }
        lures.removeIf(l -> l.who == who);
    }

    /**
     * The end of a wait at a spot: a fish on the line is landed - out of the water and its stock, into the fisher's hands,
     * with its Fishing XP. Away from the player the water's stock answers the cast. Nothing yet: still waiting (STARTED);
     * waited too long: nothing is biting here (REJECTED).
     */
    public Result land(Actor a, Object spot, ItemDef gear, float x, float z, long req) {
        Lure l = lureOf(a);
        if (l == null || l.spot != spot || l.gear != gear) l = cast(a, spot, gear, x, z);
        FishKind kind = null;
        Water from = null;
        Fish fish = l.hooked;
        if (fish != null && fish.alive) { kind = fish.kind; from = fish.water; }
        else {
            // no live fish here to come to it: the stock of the waters answers, now and then, as fish would
            boolean anyLive = false;
            for (Water w : watersNear(x, z, 2.5f)) anyLive |= w.live;
            if (!anyLive && rnd.nextFloat() < (l.bait ? 0.5f : 0.34f)) {
                List<Object[]> pool = new ArrayList<>();
                int sum = 0;
                for (Water w : watersNear(x, z, 2.5f)) for (FishKind k : FishKind.values())
                    if (w.stock[k.ordinal()] > 0 && eligible(k, a, gear)) { pool.add(new Object[]{w, k}); sum += w.stock[k.ordinal()]; }
                if (sum > 0) {
                    int pick = rnd.nextInt(sum);
                    for (Object[] o : pool) { Water w = (Water) o[0]; FishKind k = (FishKind) o[1]; pick -= w.stock[k.ordinal()]; if (pick < 0) { from = w; kind = k; break; } }
                }
            }
        }
        if (kind == null) {
            if (g.time - l.since > GIVE_UP) { lures.remove(l); return Result.no(takenHere(x, z, gear).isEmpty() ? "Nothing in these waters is taken with a " + gear.name.toLowerCase() + "." : "Nothing is biting here. The fish may have been fished out, or scared off."); }
            return Result.started();
        }
        // landed: the very fish out of its water
        if (fish != null) remove(fish);
        from.stock[kind.ordinal()] = Math.max(0, from.stock[kind.ordinal()] - 1);
        lures.remove(l);
        ItemDef got = kind.raw();
        g.state.mint(got, 1);
        ItemStack s = new ItemStack(got, 1);
        if (!a.store(s)) { g.objects.drop(s, a.x, a.y + 0.05f, a.z); if (a == g.player) g.chat.warn("No room for the " + got.name.toLowerCase() + ": it drops at your feet."); }
        if (l.bait) for (ItemStack used : a.takeItems(Items.FISHING_BAIT, 1)) g.state.burn(used.def, used.amount);
        caught++;
        var ev = g.state.commit(req, "FishCaught", a.id, 0, DevMap.jurisdiction(x, z), 0, kind.title + " from " + from.name + " (" + from.stock[kind.ordinal()] + " left)");
        g.prog.award(a, Skill.FISHING, kind.xp(), "landed a " + kind.title.toLowerCase(), ev.id());
        if (g.sfx != null) { g.sfx.play("splash", x, rpg.world.Ground.at(x, z) - 0.1f, z, Math.min(1, 0.4f + kind.length * 0.15f)); if (a == g.player) g.sfx.ui("FishReelIn", 0.55f); }
        if (g.chemistry != null) g.chemistry.splash(x, rpg.world.Ground.at(x, z) + 0.2f, z);
        if (a == g.player) g.chat.say("You catch " + (kind == FishKind.SHRIMP ? "some shrimp" : (kind.title.matches("^[AEIOU].*") ? "an " : "a ") + kind.title.toLowerCase()) + ".");
        return Result.ok();
    }

    /**
     * (0.3.5) A set net's catch: one fish of a kind a small net takes (and its owner could), out of the water's stock - a
     * live one by the net if the water swims near the player - or null when the net comes up empty.
     */
    public ItemDef netCatch(float x, float z, Actor owner) { return netCatch(x, z, owner, null); }
    /**
     * (0.3.6) A set net's catch, the spot's own fish first (what the spot is known for, if its water holds any), else
     * any other kind the net and its owner can take; the net's own interval sets how often - no chance of its own.
     */
    public ItemDef netCatch(float x, float z, Actor owner, ItemDef prefer) {
        for (int pass = 0; pass < 2; pass++) for (Water w : watersNear(x, z, 2.5f)) for (FishKind k : FishKind.values()) {
            if (pass == 0 && (prefer == null || k.raw() != prefer)) continue;
            if (w.stock[k.ordinal()] <= 0 || !eligible(k, owner, Items.FISHING_NET)) continue;
            // a net set overnight fishes the water's stock: one of its live fish if they swim (the nearest), else the stock
            if (w.live) { Fish f = null; float bd = Float.MAX_VALUE; for (Fish o : live) if (o.alive && o.water == w && o.kind == k) { float d = (float) Math.hypot(o.x - x, o.z - z); if (d < bd) { bd = d; f = o; } } if (f != null) remove(f); }
            w.stock[k.ordinal()]--;
            return k.raw();
        }
        return null;
    }

    /** Whether someone with this gear can take a fish of this kind (its gear, and their Fishing level). */
    public boolean eligible(FishKind k, Actor a, ItemDef gear) { return k.takenBy(gear) && a.skills.level(Skill.FISHING) >= k.level; }

    // ------------------------------------------------------------------ the tick
    public void update(float dt) {
        if (g.fluids == null) return;
        double minutes = g.state.minutes;
        // breeding: every water's stock creeps back toward what it can hold (a fished-out pond is slow to come back)
        for (Water w : waters) {
            if (w.regrowAt <= 0) w.regrowAt = minutes + 120;
            if (minutes < w.regrowAt) continue;
            w.regrowAt = minutes + 120;
            for (FishKind k : FishKind.values()) {
                int i = k.ordinal();
                if (w.stock[i] >= w.capacity[i]) continue;
                if (w.stock[i] >= 2 || rnd.nextFloat() < 0.25f) { w.stock[i]++; if (w.live) spawn(w, k); }
            }
        }
        // which waters swim near the player
        for (Water w : waters) {
            boolean near = w.distance(g.player.x, g.player.z) < LIVE_RANGE;
            if (near == w.live) continue;
            w.live = near;
            if (near) populate(w); else live.removeIf(f -> { if (f.water != w) return false; f.alive = false; return true; });
        }
        lures.removeIf(l -> g.time > l.until || !l.who.alive || l.who.working == null || l.who.working.target != l.spot);
        alarms.removeIf(a -> g.time > a[3]);
        inWater.clear();
        for (Actor a : g.actors()) if (a.alive && (a.swimMode != 0 || g.fluids.in(a.x, a.y + 0.3f, a.z))) inWater.add(a);
        thinkClock += dt;
        boolean think = thinkClock >= 0.1;
        if (think) thinkClock = 0;
        live.removeIf(f -> !f.alive);
        rebuildGrid();
        for (int i = live.size() - 1; i >= 0; i--) {
            Fish f = live.get(i);
            if (!f.alive) continue;
            if (think) think(f);
            move(f, dt);
        }
        live.removeIf(f -> !f.alive);
        collide();
    }

    // ------------------------------------------------------------------ neighbours
    private static long cellKey(float x, float z) { return (((long) Math.floorDiv((int) Math.floor(x), (int) GRID)) << 32) ^ (Math.floorDiv((int) Math.floor(z), (int) GRID) & 0xFFFFFFFFL); }
    private void rebuildGrid() {
        for (int i = 0; i < gridUsed; i++) gridPool.get(i).clear();
        grid.clear(); gridUsed = 0;
        for (Fish f : live) {
            if (!f.alive) continue;
            List<Fish> cell = grid.get(cellKey(f.x, f.z));
            if (cell == null) {
                if (gridUsed == gridPool.size()) gridPool.add(new ArrayList<>());
                cell = gridPool.get(gridUsed++);
                grid.put(cellKey(f.x, f.z), cell);
            }
            cell.add(f);
        }
    }
    /** The live fish within r (m, in plan) of a point, into out (cleared first). */
    private List<Fish> near(float x, float z, float r, List<Fish> out) {
        out.clear();
        int c0x = Math.floorDiv((int) Math.floor(x - r), (int) GRID), c1x = Math.floorDiv((int) Math.floor(x + r), (int) GRID);
        int c0z = Math.floorDiv((int) Math.floor(z - r), (int) GRID), c1z = Math.floorDiv((int) Math.floor(z + r), (int) GRID);
        for (int cx = c0x; cx <= c1x; cx++) for (int cz = c0z; cz <= c1z; cz++) {
            List<Fish> cell = grid.get(((long) cx << 32) ^ (cz & 0xFFFFFFFFL));
            if (cell == null) continue;
            for (Fish o : cell) if (o.alive && Math.abs(o.x - x) <= r && Math.abs(o.z - z) <= r) out.add(o);
        }
        return out;
    }
    private final List<Fish> nearThink = new ArrayList<>(), nearMove = new ArrayList<>(), nearHit = new ArrayList<>();

    /**
     * (0.3.6, gamefixes: fish had no collision) bodies in the water cannot pass through one another: two fish that overlap
     * are pushed apart, the lighter the more; a swimmer and a fish likewise - a minnow is brushed aside, a whale shoulders
     * the swimmer out of its way. Each is a capsule along its heading (a swimmer, an upright one round the body).
     */
    private void collide() {
        for (Fish f : live) {
            if (!f.alive) continue;
            float rf = f.girth(), half = f.length() * 0.45f;
            float ax = (float) -Math.sin(f.yaw), az = (float) -Math.cos(f.yaw);
            for (Fish o : near(f.x, f.z, half + rf + 2.5f, nearHit)) {
                if (o == f || o.id < f.id || o.water != f.water) continue;
                float ro = o.girth();
                float[] d = apart(f, ax, az, half, o.x, o.y, o.z);
                float dist = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), overlap = rf + ro - dist;
                if (overlap <= 0 || dist < 1e-4f) continue;
                float mf = f.mass(), mo = o.mass(), share = mo / (mf + mo);
                float nx = d[0] / dist, ny = d[1] / dist, nz = d[2] / dist;
                nudge(f, -nx * overlap * share, -ny * overlap * share, -nz * overlap * share);
                nudge(o, nx * overlap * (1 - share), ny * overlap * (1 - share), nz * overlap * (1 - share));
            }
            for (Actor a : inWater) {
                if (Math.abs(a.x - f.x) > half + 2 || Math.abs(a.z - f.z) > half + 2) continue;
                float ra = Math.max(0.25f, a.radius), cy = a.y + Math.min(0.9f, a.height * 0.45f);
                float[] d = apart(f, ax, az, half, a.x, cy, a.z);
                float dist = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), overlap = rf + ra - dist;
                if (overlap <= 0 || dist < 1e-4f) continue;
                float mf = f.mass(), ma = ACTOR_MASS * Math.max(0.2f, a.height / 1.8f), share = ma / (mf + ma);
                float nx = d[0] / dist, ny = d[1] / dist, nz = d[2] / dist;
                nudge(f, -nx * overlap * share, -ny * overlap * share, -nz * overlap * share);
                float hx = nx * overlap * (1 - share), hz = nz * overlap * (1 - share);
                if (Math.abs(hx) + Math.abs(hz) > 1e-3f && g.swimming != null) g.swimming.shove(a, hx, hz);
            }
        }
    }
    /** How much a swimmer's body weighs against a fish's, in the push between them (kg). */
    static final float ACTOR_MASS = 75;
    /** The offset from the nearest point of a fish's body axis to a point (x, y, z). */
    private static float[] apart(Fish f, float ax, float az, float half, float x, float y, float z) {
        float px = x - f.x, pz = z - f.z;
        float along = Math.max(-half, Math.min(half, px * ax + pz * az));
        return new float[]{x - (f.x + ax * along), y - f.y, z - (f.z + az * along)};
    }
    /** Moves a fish by a push, as far as the water lets it. */
    private void nudge(Fish f, float dx, float dy, float dz) {
        float nx = f.x + dx, ny = f.y + dy, nz = f.z + dz;
        if (wet(nx, ny, nz)) { f.x = nx; f.y = ny; f.z = nz; }
        else if (wet(nx, f.y, nz)) { f.x = nx; f.z = nz; }
    }

    private void populate(Water w) {
        for (FishKind k : FishKind.values()) for (int n = 0; n < w.stock[k.ordinal()]; n++) if (live.size() < LIVE_MAX) spawn(w, k);
    }

    private void spawn(Water w, FishKind k) {
        for (int tries = 0; tries < 40; tries++) {
            int x = w.x0 + rnd.nextInt(Math.max(1, w.x1 - w.x0 + 1)), z = w.z0 + rnd.nextInt(Math.max(1, w.z1 - w.z0 + 1));
            if (!cellOf(w, x, z)) continue;
            float fx = x + 0.2f + rnd.nextFloat() * 0.6f, fz = z + 0.2f + rnd.nextFloat() * 0.6f;
            float s = g.fluids.surface(fx, rpg.world.Ground.at(fx, fz), fz);
            if (Float.isNaN(s)) continue;
            float bed = bed(fx, fz, s);
            if (s - bed < Math.min(0.6f, k.length * 0.3f)) continue;
            place(w, k, fx, fz, s, bed);
            return;
        }
    }
    private Fish place(Water w, FishKind k, float fx, float fz, float s, float bed) {
        Fish f = new Fish(nextId++, k, w, fx, 0, fz, 0.8f + rnd.nextFloat() * 0.4f);
        f.y = f.wantY = depthFor(f, s, bed);
        f.yaw = f.wantYaw = rnd.nextFloat() * 6.283f;
        f.hunger = rnd.nextFloat() * 0.5f;
        f.breathAt = g.time + 10 + rnd.nextFloat() * 50;
        f.thinkAt = g.time + rnd.nextFloat() * 0.3;
        f.phase = rnd.nextFloat() * 6.283f;
        live.add(f);
        return f;
    }

    /**
     * (0.3.6, gamefixes: the 'spawn' command did not make fish) a fish of a kind put into the water nearest a point (within
     * 12 m): one more of the stock of that water (it can hold one more than it did), swimming at once. Null when there is
     * no water there deep enough for it.
     */
    public Fish release(FishKind k, float x, float z) {
        float bestD = Float.MAX_VALUE; float[] at = null;
        for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
            float d = dx * dx + dz * dz;
            if (d >= bestD || d > 144) continue;
            float cx = (float) Math.floor(x) + dx + 0.5f, cz = (float) Math.floor(z) + dz + 0.5f;
            float s = g.fluids.surface(cx, rpg.world.Ground.at(cx, cz) + 2, cz);
            if (Float.isNaN(s)) continue;
            float bed = bed(cx, cz, s);
            if (s - bed < Math.min(1.2f, Math.max(0.3f, k.length * 0.3f))) continue;
            bestD = d; at = new float[]{cx, cz, s, bed};
        }
        if (at == null) return null;
        Water w = waterAt(at[0], at[1]);
        if (w == null) {
            // water no water of the map's knows (a pool dug and filled): a water of its own round it
            int bx = (int) Math.floor(at[0]), bz = (int) Math.floor(at[1]);
            w = new Water("a pool at " + bx + ", " + bz, FishKind.W.POND, bx - 6, bz - 6, bx + 6, bz + 6);
            w.live = true;
            waters.add(w);
        }
        int i = k.ordinal();
        w.stock[i]++;
        w.capacity[i] = Math.max(w.capacity[i], w.stock[i]);
        Fish f = place(w, k, at[0], at[1], at[2], at[3]);
        g.state.commit("FishReleased", 0, 0, DevMap.jurisdiction(at[0], at[1]), 0, k.title + " into " + w.name);
        return f;
    }

    /** A fish by id among the live ones (null: none). */
    public Fish byId(long id) { for (Fish f : live) if (f.id == id && f.alive) return f; return null; }

    /** (0.3.6) The fish set on someone now: one fighting back against them, or a shark circling them; the nearest. */
    public Fish foeOf(Actor a) {
        Fish best = null; float bd = 20;
        for (Fish f : live) {
            if (!f.alive || Math.abs(f.struckBy) != a.id || f.state != Fish.State.DEFEND && f.state != Fish.State.HUNT) continue;
            float d = (float) Math.hypot(f.x - a.x, f.z - a.z);
            if (d < bd) { bd = d; best = f; }
        }
        return best;
    }

    private void remove(Fish f) { f.alive = false; live.remove(f); for (Lure l : lures) if (l.hooked == f) l.hooked = null; }

    /** The bed under a point (the top of the first block below the water there). */
    public float bed(float x, float z, float surface) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), h = (int) Math.floor(surface);
        while (h >= 0 && g.fluids.water(bx, h, bz)) h--;
        return h + 1;
    }
    /** The depth a fish keeps at a point: under the surface, mid-water or on the bed, within the water there. */
    private float depthFor(Fish f, float surface, float bed) {
        float half = Math.max(0.03f, f.length() * (f.kind == FishKind.WHALE ? 0.09f : 0.14f));
        float lo = bed + half + 0.04f, hi = surface - half - 0.06f;
        if (hi < lo) return (lo + hi) / 2;
        float want = switch (f.kind.band) { case FishKind.TOP -> hi - 0.15f; case FishKind.BED -> lo; default -> (lo + hi) / 2 + (float) Math.sin(f.id * 1.7) * (hi - lo) * 0.25f; };
        return Math.max(lo, Math.min(hi, want));
    }
    private boolean wet(float x, float y, float z) { return g.fluids.in(x, y, z); }

    // ------------------------------------------------------------------ a fish's mind
    private void think(Fish f) {
        if (g.time < f.thinkAt) return;
        f.thinkAt = g.time + 0.12 + (f.id % 5) * 0.02;
        float len = f.length();
        // 1. fright: a splash, a swimmer or a hunter too near (the great fish fear nothing their size or smaller)
        if (f.state == Fish.State.HOOKED || f.state == Fish.State.DEFEND) return;
        if (g.time < f.fleeUntil) return;
        for (float[] a : alarms) if (Math.hypot(a[0] - f.x, a[1] - f.z) < a[2]) { startle(f, a[0], a[1], a[2]); return; }
        if (len < 1.5f) for (Actor a : inWater) {
            // (0.4.1) the angler at their own line does not scare off the fish coming to it, and someone standing still in
            // the shallows only startles a fish that comes right up to them: a body's movement is what frightens fish
            if (f.lure != null && f.lure.who == a) continue;
            float d = (float) Math.hypot(a.x - f.x, a.z - f.z), moving = (float) Math.hypot(a.vx, a.vz);
            float reach = moving < 0.3f ? 1.0f + len : 2.6f + len + moving * 0.5f;
            if (d < reach && Math.abs(a.y + 0.6f - f.y) < 2.2f) { startle(f, a.x, a.z, 3); return; }
        }
        for (Fish h : near(f.x, f.z, 6.5f, nearThink)) {
            if (h == f || h.water != f.water || h.kind.preys <= 0 || h.kind.preys * h.length() < len || h.state != Fish.State.HUNT && h.hunger < 0.6f) continue;
            // (0.4.1) a hunter after this very fish is fled from at a distance; one merely hungry nearby only when it is close
            // (the whole pond's small fish used to scatter from any hungry trout within four metres, so a line rarely took one)
            float reach = h.prey == f ? 4 + h.length() * 0.5f : 1.8f + h.length() * 0.5f;
            if (Math.hypot(h.x - f.x, h.z - f.z) < reach) { startle(f, h.x, h.z, 4); return; }
        }
        // 2. a hunter that is hungry goes after something it can swallow
        if (f.kind.preys > 0 && f.hunger > 0.6f) {
            Fish best = null; float bd = 9;
            for (Fish p : near(f.x, f.z, 9, nearThink)) {
                if (p == f || p.water != f.water || p.length() > f.kind.preys * len || p.kind == f.kind || p.state == Fish.State.HOOKED) continue;
                float d = (float) Math.hypot(p.x - f.x, p.z - f.z);
                if (d < bd) { bd = d; best = p; }
            }
            if (best != null) { f.prey = best; set(f, Fish.State.HUNT, "hunting a " + best.kind.title.toLowerCase()); return; }
        }
        if (f.kind.attacksSwimmers() && f.hunger > 0.5f) for (Actor a : inWater) {
            if (a.swimMode == 0 || Math.hypot(a.x - f.x, a.z - f.z) > 12 || waterAt(a.x, a.z) != f.water) continue;
            f.prey = null; f.why = "circling " + a.name; f.state = Fish.State.HUNT; f.stateSince = g.time; f.fearX = a.x; f.fearZ = a.z;
            f.struckBy = -a.id;   // the swimmer it is after (negative: a target, not a blow)
            return;
        }
        // 3. a line, a net or a pot it can be taken with, within reach of its senses
        Lure best = null; float bd = Float.MAX_VALUE;
        for (Lure l : lures) {
            if (l.hooked != null || !eligible(f.kind, l.who, l.gear)) continue;
            float d = (float) Math.hypot(l.x - f.x, l.z - f.z);
            if (d < (l.bait ? DRAW_BAITED : DRAW) && d < bd && waterAt(l.x, l.z) != null) { bd = d; best = l; }
        }
        if (best != null) { f.lure = best; set(f, Fish.State.LURED, "drawn to a " + best.gear.name.toLowerCase()); return; }
        // 4. a whale comes up to breathe
        if (f.kind == FishKind.WHALE && g.time > f.breathAt) { set(f, Fish.State.SURFACE, "coming up to breathe"); return; }
        // 5. otherwise: shoal with its own kind, rest on the bed, or wander
        if (f.state == Fish.State.HUNT || f.state == Fish.State.LURED || f.state == Fish.State.FLEE) set(f, Fish.State.SWIM, "");
        f.hunger = Math.min(1, f.hunger + 0.002f);
        if (f.kind.school) set(f, Fish.State.SHOAL, "shoaling");
        else if (f.kind.crawler() || f.kind.band == FishKind.BED && rnd.nextFloat() < 0.04f) set(f, f.state == Fish.State.REST ? Fish.State.REST : rnd.nextFloat() < 0.5f ? Fish.State.REST : Fish.State.SWIM, "on the bed");
        else if (f.state == Fish.State.REST && rnd.nextFloat() < 0.1f) set(f, Fish.State.SWIM, "");
        if (rnd.nextFloat() < 0.15f) f.wantYaw += (rnd.nextFloat() - 0.5f) * 1.6f;
    }

    private void set(Fish f, Fish.State s, String why) { if (f.state != s) { f.state = s; f.stateSince = g.time; } f.why = why; }

    /** A fright: away from it, fast, for a moment (a hooked fish is not let off, it only thrashes). */
    void startle(Fish f, float x, float z, float r) {
        if (f.state == Fish.State.HOOKED) return;
        f.fearX = x; f.fearZ = z;
        float awayX = f.x - x, awayZ = f.z - z;
        f.wantYaw = (float) Math.atan2(-awayX, -awayZ);
        f.fleeUntil = g.time + 1.4 + rnd.nextFloat() * 1.6;
        if (f.lure != null) f.lure = null;
        set(f, Fish.State.FLEE, "startled");
    }

    /** A splash or a blow in the water: the fish within it startle. */
    public void alarm(float x, float z, float radius) { if (alarms.size() < 32) alarms.add(new float[]{x, z, radius, (float) (g.time + 0.4)}); }

    // ------------------------------------------------------------------ moving through the water
    private void move(Fish f, float dt) {
        float len = f.length();
        float s = g.fluids.surface(f.x, f.y + 1, f.z);
        if (Float.isNaN(s)) { remove(f); return; }   // the water it was in is gone (frozen over, built over)
        float bed = bed(f.x, f.z, s);
        float speed = f.kind.cruise, tx = Float.NaN, tz = Float.NaN, ty = depthFor(f, s, bed);
        switch (f.state) {
            case FLEE -> { speed = f.kind.burst; ty = Math.max(bed + len * 0.15f, ty - 0.4f); if (g.time > f.fleeUntil) set(f, Fish.State.SWIM, ""); }
            case HUNT -> {
                if (f.prey != null) {
                    Fish p = f.prey;
                    if (!p.alive) { f.prey = null; set(f, Fish.State.SWIM, ""); break; }
                    tx = p.x; tz = p.z; ty = p.y; speed = f.kind.burst * 0.85f;
                    if (Math.hypot(p.x - f.x, p.z - f.z) < (len + p.length()) * 0.45f + 0.15f && Math.abs(p.y - f.y) < 0.6f) {
                        // taken: the prey is eaten, out of its water's stock
                        p.water.stock[p.kind.ordinal()] = Math.max(0, p.water.stock[p.kind.ordinal()] - 1);
                        remove(p); eaten++; f.hunger = 0; f.prey = null; set(f, Fish.State.SWIM, "fed");
                        if (g.chemistry != null) g.chemistry.disturb(f.x, s, f.z, 0.6f, 1.2f, false);
                    } else if (g.time - f.stateSince > 14) { f.prey = null; set(f, Fish.State.SWIM, "gave up the chase"); }
                } else if (f.struckBy < 0 && g.actor(-f.struckBy) instanceof Actor a && a.alive && a.swimMode != 0) {
                    // a shark on a swimmer: circling in, then the bite
                    tx = a.x; tz = a.z; ty = Math.min(ty, a.y + 0.6f); speed = f.kind.burst * 0.55f;
                    if (Math.hypot(a.x - f.x, a.z - f.z) < len * 0.5f + 0.6f && g.time > f.biteAt) {
                        f.biteAt = g.time + 2.5;
                        int bite = 2 + f.kind.strength / 25;
                        g.combat.environmentalDamage(a, bite, "a " + f.kind.title.toLowerCase() + "'s bite");
                        if (g.chemistry != null) g.chemistry.disturb(a.x, s, a.z, 1.2f, 4, true);
                        if (g.sfx != null && g.sfx.has("animal.shark")) g.sfx.play("animal.shark", f.x, s, f.z, 0.8f);
                        f.hunger -= 0.3f;
                        if (f.hunger < 0.2f) { f.struckBy = 0; set(f, Fish.State.SWIM, "fed"); }
                    }
                    if (g.time - f.stateSince > 25) { f.struckBy = 0; set(f, Fish.State.SWIM, "lost interest"); }
                } else { f.struckBy = 0; set(f, Fish.State.SWIM, ""); }
            }
            case LURED -> {
                Lure l = f.lure;
                if (l == null || !lures.contains(l) || l.hooked != null && l.hooked != f) { f.lure = null; set(f, Fish.State.SWIM, ""); break; }
                tx = l.x; tz = l.z; ty = l.y; speed = f.kind.cruise * 1.2f + 0.15f;
                if (Math.hypot(l.x - f.x, l.z - f.z) < 0.55f + len * 0.3f && Math.abs(l.y - f.y) < 0.8f) {
                    l.hooked = f; set(f, Fish.State.HOOKED, "on " + l.who.name + "'s " + l.gear.name.toLowerCase());
                    if (g.chemistry != null) g.chemistry.disturb(l.x, s, l.z, 0.5f, 2.2f, len > 0.6f);
                }
            }
            case HOOKED -> {
                Lure l = f.lure;
                if (l == null || !lures.contains(l)) { f.lure = null; startle(f, f.x - (float) Math.sin(f.yaw), f.z - (float) Math.cos(f.yaw), 3); break; }
                // the fight on the line: darting round the hook, tugging at it
                double a = g.time * (2.2 + f.id % 3) + f.id;
                tx = l.x + (float) Math.cos(a) * (0.4f + len * 0.3f); tz = l.z + (float) Math.sin(a) * (0.4f + len * 0.3f); ty = l.y; speed = f.kind.burst * 0.4f;
                if (g.chemistry != null && rnd.nextFloat() < dt * 3) g.chemistry.disturb(f.x, s, f.z, 0.4f, 1.4f, false);
            }
            case SURFACE -> {
                ty = s - len * 0.06f; speed = f.kind.cruise * 0.6f;
                if (f.y > s - len * 0.12f && g.time - f.stateSince > 1.5) {
                    // the blow: a spout of spray and the sound of it
                    if (g.chemistry != null) { g.chemistry.disturb(f.x, s, f.z, 2.5f, 3, true); g.chemistry.visual(8, f.x - (float) Math.sin(f.yaw) * len * 0.3f, s + 0.4f, f.z - (float) Math.cos(f.yaw) * len * 0.3f, 0.2f, 160, 1.2f, 1.6f, 0.85f, 0.9f, 0.95f); }
                    if (g.sfx != null && g.sfx.has("animal.whale")) g.sfx.play("animal.whale", f.x, s, f.z, 0.9f, 1, 40);
                    f.breathAt = g.time + 40 + rnd.nextFloat() * 40;
                    set(f, Fish.State.SWIM, "breathed");
                }
            }
            case SHOAL -> {
                // keep with the shoal: toward its middle, along its heading, never on top of a neighbour
                float cx = 0, cz = 0, cy = 0, hx = 0, hz = 0, sx = 0, sz = 0; int n = 0;
                for (Fish o : near(f.x, f.z, 3, nearMove)) {
                    if (o == f || o.kind != f.kind || o.water != f.water) continue;
                    float dx = o.x - f.x, dz = o.z - f.z, d2 = dx * dx + dz * dz;
                    if (d2 > 9) continue;
                    cx += o.x; cz += o.z; cy += o.y; hx += o.vx; hz += o.vz; n++;
                    if (d2 < len * len * 4 + 0.01f) { sx -= dx; sz -= dz; }
                    if (n >= 8) break;
                }
                if (n > 0) {
                    cx /= n; cz /= n; cy /= n;
                    float wx = (cx - f.x) * 0.6f + hx / n * 0.8f + sx * 2f + (float) -Math.sin(f.wantYaw) * 0.5f, wz = (cz - f.z) * 0.6f + hz / n * 0.8f + sz * 2f + (float) -Math.cos(f.wantYaw) * 0.5f;
                    tx = f.x + wx; tz = f.z + wz; ty = (ty + cy) / 2;
                }
            }
            case REST -> speed = f.kind.crawler() ? f.kind.cruise * 0.4f : 0.05f;
            case DEFEND -> {
                // (0.3.6, gamefixes: whales did not protect themselves) turned on whoever hurt it: at a swimmer it can
                // reach, it comes at them and strikes; one it cannot (on the pier, the bank, a boat) it dives from and leaves
                Actor foe = g.actor(f.struckBy);
                if (foe == null || !foe.alive || g.time - f.stateSince > DEFEND_FOR) { f.struckBy = 0; set(f, Fish.State.SWIM, "calmed"); break; }
                boolean reach = foe.swimMode != 0 && waterAt(foe.x, foe.z) == f.water && Math.hypot(foe.x - f.x, foe.z - f.z) < DEFEND_RANGE + len;
                if (!reach) {
                    // out of its reach: it sounds - down deep and away from them
                    f.wantYaw = (float) Math.atan2(-(f.x - foe.x), -(f.z - foe.z));
                    speed = f.kind.burst * 0.8f; ty = bed + len * 0.15f + 0.1f;
                    if (Math.hypot(foe.x - f.x, foe.z - f.z) > 24) { f.struckBy = 0; set(f, Fish.State.SWIM, "got away"); }
                    break;
                }
                tx = foe.x; tz = foe.z; ty = Math.max(bed + len * 0.15f, Math.min(s - len * 0.06f, foe.y + 0.5f)); speed = f.kind.burst * 0.7f;
                float[] gap = apart(f, (float) -Math.sin(f.yaw), (float) -Math.cos(f.yaw), len * 0.5f, foe.x, foe.y + 0.6f, foe.z);
                if ((float) Math.sqrt(gap[0] * gap[0] + gap[1] * gap[1] + gap[2] * gap[2]) < f.girth() + 1.0f && g.time > f.biteAt) strike(f, foe, s);
            }
            default -> { }
        }
        // the heading: toward its target, or its own wandering one
        float want = Float.isNaN(tx) ? f.wantYaw : (float) Math.atan2(-(tx - f.x), -(tz - f.z));
        // the banks: a look ahead; wet one way or another, turn that way
        float ahead = 0.35f + len * 0.55f;
        float lx = f.x - (float) Math.sin(want) * ahead, lz = f.z - (float) Math.cos(want) * ahead;
        if (!wet(lx, f.y, lz) || !cellOrNear(f.water, lx, lz)) {
            float best = Float.NaN;
            for (float turn : new float[]{0.6f, -0.6f, 1.2f, -1.2f, 2.0f, -2.0f, 3.14f}) {
                float yy = want + turn, ax = f.x - (float) Math.sin(yy) * ahead, az = f.z - (float) Math.cos(yy) * ahead;
                if (wet(ax, f.y, az) && cellOrNear(f.water, ax, az)) { best = yy; break; }
            }
            want = Float.isNaN(best) ? want + 3.14f : best;
            f.wantYaw = want;
        }
        float turnRate = (f.state == Fish.State.FLEE ? 6f : 2.4f) / Math.max(0.6f, (float) Math.sqrt(len));
        float dy = Actor.wrap(want - f.yaw);
        f.yaw = Actor.wrap(f.yaw + Math.max(-turnRate * dt, Math.min(turnRate * dt, dy)));
        if (f.state == Fish.State.REST && !f.kind.crawler()) speed = 0.05f;
        if (f.kind.crawler()) ty = bed + Math.max(0.03f, len * 0.12f);
        float accel = Math.min(1, dt * (f.state == Fish.State.FLEE ? 5 : 2));
        float wvx = (float) -Math.sin(f.yaw) * speed, wvz = (float) -Math.cos(f.yaw) * speed, wvy = Math.max(-1, Math.min(1, (ty - f.y) * 1.5f));
        f.vx += (wvx - f.vx) * accel; f.vz += (wvz - f.vz) * accel; f.vy += (wvy - f.vy) * accel;
        float nx = f.x + f.vx * dt, ny = f.y + f.vy * dt, nz = f.z + f.vz * dt;
        float half = Math.max(0.03f, len * 0.12f);
        ny = Math.max(bed + half * 0.5f, Math.min(s - half * 0.4f, ny));
        if (wet(nx, ny, nz)) { f.x = nx; f.y = ny; f.z = nz; }
        else if (wet(f.x, ny, f.z)) { f.y = ny; f.vx *= -0.3f; f.vz *= -0.3f; f.wantYaw = f.yaw + 3.14f; }
        else { f.vx = f.vz = f.vy = 0; f.wantYaw = f.yaw + 3.14f; }
        f.pitch = (float) Math.atan2(f.vy, Math.max(0.05f, (float) Math.hypot(f.vx, f.vz))) * 0.6f;
        float sp = f.speed();
        f.phase += dt * (2.2f + sp / Math.max(0.15f, len) * 1.6f);
        // the water answers a big fish moving near the top
        if (len > 1.2f && s - f.y < len * 0.25f && sp > 0.8f && rnd.nextFloat() < dt * 2 && g.chemistry != null) g.chemistry.disturb(f.x, s, f.z, len * 0.4f, 1.5f, false);
        // an eel shocks whoever comes too near it
        if (f.kind.shocks() && g.time > f.biteAt) for (Actor a : inWater) {
            if (Math.hypot(a.x - f.x, a.z - f.z) > 1.6f || Math.abs(a.y + 0.6f - f.y) > 1.6f) continue;
            f.biteAt = g.time + 4;
            g.combat.environmentalDamage(a, 2, "an electric eel");
            if (g.fx != null) g.fx.sparks(f.x, f.y + 0.1f, f.z, 12);
            if (g.sfx != null && g.sfx.has("zap")) g.sfx.play("zap", f.x, f.y, f.z, 0.6f);
            startle(f, a.x, a.z, 3);
            break;
        }
    }
    /** Whether a point is in a fish's own water (the sea's reaches let it stray a little into the next). */
    private boolean cellOrNear(Water w, float x, float z) {
        if (w.cls == FishKind.W.SHORE || w.cls == FishKind.W.SEA || w.cls == FishKind.W.DEEP) {
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            if (!w.contains(x, z)) return false;
            return w.cls == FishKind.W.SHORE ? Coast.depth(bx, bz) >= 1 && Coast.depth(bx, bz) <= 2 : w.cls == FishKind.W.SEA ? Coast.depth(bx, bz) >= 2 : Coast.depth(bx, bz) == 3;
        }
        return w.contains(x, z);
    }

    // ------------------------------------------------------------------ fighting a fish
    /** The nearest fish a line from a point meets within a distance (a blade's edge, an arrow's path), and where. */
    public Fish ray(float ox, float oy, float oz, float dx, float dy, float dz, float limit, float pad, float[] at) {
        Fish best = null; float bt = limit;
        for (Fish f : live) {
            if (!f.alive) continue;
            float t = capsule(f, ox, oy, oz, dx, dy, dz, pad);
            if (t >= 0 && t < bt) { bt = t; best = f; }
        }
        if (best != null && at != null) { at[0] = ox + dx * bt; at[1] = oy + dy * bt; at[2] = oz + dz * bt; }
        return best;
    }

    /**
     * Where a ray from (ox, oy, oz) along the unit (dx, dy, dz) enters a fish's body - a capsule of its girth (plus pad)
     * along its heading, its length long - or -1 if it misses.
     */
    static float capsule(Fish f, float ox, float oy, float oz, float dx, float dy, float dz, float pad) {
        float r = f.girth() + pad, half = f.length() * 0.5f;
        float ax = (float) -Math.sin(f.yaw), az = (float) -Math.cos(f.yaw);
        // the point on the ray nearest the body's axis: sampled where the ray passes the middle and each end
        float best = -1, bestOff = Float.MAX_VALUE;
        for (float along : new float[]{0, -half, half}) {
            float sx = f.x + ax * along, sz = f.z + az * along;
            float px = sx - ox, py = f.y - oy, pz = sz - oz;
            float t = Math.max(0, px * dx + py * dy + pz * dz);
            float cx = ox + dx * t - f.x, cy = oy + dy * t - f.y, cz = oz + dz * t - f.z;
            float on = Math.max(-half, Math.min(half, cx * ax + cz * az));
            float qx = cx - ax * on, qz = cz - az * on, off = qx * qx + cy * cy + qz * qz;
            if (off > r * r || off >= bestOff) continue;
            bestOff = off;
            best = Math.max(0, t - (float) Math.sqrt(r * r - off));
        }
        return best;
    }

    /**
     * A blow or a shot lands on a fish: its HP go down by what got through its hide, the fighting skills of whoever struck
     * it are trained (never Fishing), it bolts; killed, it floats up to the surface to be picked from the water. A punch
     * on a tuna by one strong and skilled enough is no blow at all: they have it in their hands.
     */
    public void struck(Actor a, Fish f, float potential, boolean melee, boolean punch, Skill ranged, float px, float py, float pz) {
        if (!f.alive) return;
        if (punch && f.kind == FishKind.TUNA) {
            if (a.skills.level(Skill.FISHING) >= FishKind.BAREHANDED_FISHING && a.skills.level(Skill.STRENGTH) >= FishKind.BAREHANDED_STRENGTH) { barehanded(a, f); return; }
            if (a == g.player) g.chat.say("The tuna slips out of your hands. It takes Fishing " + FishKind.BAREHANDED_FISHING + " and Strength " + FishKind.BAREHANDED_STRENGTH + " to hold one.");
        }
        float protection = f.kind.defense * 0.35f;
        int dealt = Math.max(0, Math.round(potential - protection * (float) rnd.nextDouble()));
        if (potential >= 1 && dealt == 0 && rnd.nextFloat() < 0.5f) dealt = 1;
        dealt = Math.min(dealt, (int) Math.ceil(f.hp));
        f.hp -= dealt;
        f.lastHurt = g.time;
        f.struckBy = a.id;
        if (g.fx != null) g.fx.text(String.valueOf(dealt), f.x, f.y + f.girth() + 0.3f, f.z, 0xFFFF7050);
        // the great fish turn on what hurt them; the rest bolt
        if (f.kind.defends() && !f.kind.attacksSwimmers()) {
            if (f.lure != null) { for (Lure l : lures) if (l.hooked == f) l.hooked = null; f.lure = null; }
            f.prey = null; f.fleeUntil = 0;
            set(f, Fish.State.DEFEND, "fighting back against " + a.name);
            f.stateSince = g.time;
        } else startle(f, a.x, a.z, 4);
        float s = g.fluids.surface(f.x, f.y + 1, f.z);
        if (g.chemistry != null && !Float.isNaN(s)) g.chemistry.disturb(px, s, pz, 0.6f + f.length() * 0.3f, 2.5f, true);
        if (g.fx != null && dealt > 0) g.fx.blood(px, py, pz, -f.vx, -f.vz);
        var ev = g.state.commit("FishStruck", a.id, 0, DevMap.jurisdiction(f.x, f.z), 0, String.format("%s hit a %s for %d (%.0f HP left)", a.name, f.kind.title.toLowerCase(), dealt, Math.max(0, f.hp)));
        if (dealt > 0) {
            // fighting is fighting: the blow trains what made it, as on any creature
            if (melee) { g.prog.award(a, Skill.ATTACK, dealt * g.cfg.xpPerDamage * 0.5f, "melee damage", ev.id()); g.prog.award(a, Skill.STRENGTH, dealt * g.cfg.xpPerDamage * 0.5f, "melee damage", ev.id()); }
            else if (ranged != null) g.prog.award(a, ranged, dealt * g.cfg.xpPerDamage * (ranged == Skill.MAGIC ? 0.5f : 1f), "damage dealt", ev.id());
            g.prog.award(a, Skill.HEALTH, dealt * g.cfg.healthXpPerDamage, "damage dealt (Health share)", ev.id());
        }
        // a shark struck turns on what struck it
        if (f.kind.attacksSwimmers() && a.swimMode != 0) { f.struckBy = -a.id; f.hunger = 1; f.state = Fish.State.HUNT; f.stateSince = g.time; }
        if (f.hp <= 0) die(f, a);
    }

    /** (0.3.6) How long a great fish keeps up a fight (s), and how far off it will come at a swimmer who hurt it (m). */
    static final double DEFEND_FOR = 30;
    static final float DEFEND_RANGE = 14;

    /**
     * A great fish's blow at someone in the water: its Attack against their Defense to land it, its Strength in the harm,
     * and its bulk in how far it throws them - a whale's flukes send a swimmer flying out of the water.
     */
    private void strike(Fish f, Actor foe, float surface) {
        f.biteAt = g.time + 1.6 + f.length() * 0.08;
        float chance = Math.max(0.15f, Math.min(0.92f, 0.55f + (f.kind.attack - foe.skills.level(Skill.DEFENSE)) / 120f));
        boolean landed = rnd.nextFloat() < chance;
        if (g.chemistry != null) g.chemistry.disturb(f.x, surface, f.z, 0.8f + f.length() * 0.3f, 3.5f, true);
        if (g.sfx != null) g.sfx.play("splash", f.x, surface, f.z, Math.min(1, 0.5f + f.length() * 0.08f));
        if (!landed) { g.state.commit("FishMissed", 0, foe.id, DevMap.jurisdiction(f.x, f.z), 0, "a " + f.kind.title.toLowerCase() + "'s " + f.kind.weapon()); return; }
        int harm = 1 + rnd.nextInt(Math.max(1, f.kind.maxHit()));
        g.combat.environmentalDamage(foe, harm, "a " + f.kind.title.toLowerCase() + "'s " + f.kind.weapon());
        float dx = foe.x - f.x, dz = foe.z - f.z, l = Math.max(0.01f, (float) Math.hypot(dx, dz));
        if (f.length() >= 4 && foe.alive && !foe.reacting) g.reactions.knockDown(foe, dx / l, dz / l, f.kind.throwing(), foe.x, foe.chestY(), foe.z);
        else if (foe.alive) foe.cs.staggered = Math.max(foe.cs.staggered, 0.3f);
        if (foe == g.player) g.chat.add("The " + f.kind.title.toLowerCase() + " strikes you with its " + f.kind.weapon() + "!", rpg.Chat.Kind.WARN);
    }

    /** (0.3.6) Taken out of the water without a trace (the console's 'remove'): out of its stock too. */
    public void discard(Fish f) {
        if (!f.alive) return;
        f.water.stock[f.kind.ordinal()] = Math.max(0, f.water.stock[f.kind.ordinal()] - 1);
        remove(f);
    }

    /** (0.3.6, gamefixes: the 'slay' command did not work on fish) killed outright, as a death by any blow. */
    /**
     * (0.3.7) Charged water reaches a fish: struck as by any spell when someone sent the charge (their Magic is trained by
     * it), or simply hurt when nobody did (a dragon's breath, a storm); what lives through it is stunned where it swims.
     */
    public void shocked(Fish f, Actor by, float potential) {
        if (!f.alive) return;
        if (by != null) struck(by, f, potential, false, false, Skill.MAGIC, f.x, f.y, f.z);
        else { f.hp -= potential; f.lastHurt = g.time; if (f.hp <= 0) die(f, null); }
        if (f.alive && f.state != Fish.State.HOOKED) { f.vx = 0; f.vz = 0; f.fleeUntil = 0; set(f, Fish.State.REST, "stunned by a shock"); f.stateSince = g.time; }
    }
    public void slay(Fish f, Actor by) { if (!f.alive) return; f.hp = 0; f.lastHurt = g.time; die(f, by); }

    /** Killed: out of the stock, and its body floats up - a raw catch lying on the water to be picked up. */
    private void die(Fish f, Actor by) {
        killed++;
        f.water.stock[f.kind.ordinal()] = Math.max(0, f.water.stock[f.kind.ordinal()] - 1);
        remove(f);
        float s = g.fluids.surface(f.x, f.y + 1, f.z);
        ItemDef body = f.kind.raw();
        g.state.mint(body, 1);
        g.objects.drop(new ItemStack(body, 1), f.x, Float.isNaN(s) ? f.y : s - 0.04f, f.z);
        g.state.commit("FishKilled", by == null ? 0 : by.id, 0, DevMap.jurisdiction(f.x, f.z), 0, f.kind.title + " killed in " + f.water.name);
        if (by == g.player) g.chat.say("The " + f.kind.title.toLowerCase() + " goes still and floats up.");
    }

    /** The barehanded tuna: grabbed and hauled out - a catch, with its Fishing XP. */
    private void barehanded(Actor a, Fish f) {
        f.water.stock[f.kind.ordinal()] = Math.max(0, f.water.stock[f.kind.ordinal()] - 1);
        remove(f);
        ItemDef got = f.kind.raw();
        g.state.mint(got, 1);
        ItemStack s = new ItemStack(got, 1);
        if (!a.store(s)) g.objects.drop(s, a.x, a.y + 0.05f, a.z);
        caught++;
        var ev = g.state.commit("FishCaught", a.id, 0, DevMap.jurisdiction(f.x, f.z), 0, "a tuna, barehanded, from " + f.water.name);
        g.prog.award(a, Skill.FISHING, f.kind.xp() * 1.3f, "caught a tuna barehanded", ev.id());
        if (g.sfx != null) g.sfx.play("splash", f.x, f.y, f.z, 0.9f);
        if (a == g.player) g.chat.say("You grab the tuna with your bare hands and haul it out of the water!");
    }

    // ------------------------------------------------------------------ saving
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        for (Water w : waters) {
            List<Object> l = new ArrayList<>();
            for (int s : w.stock) l.add(s);
            m.put(w.name, l);
        }
        return m;
    }
    public void load(Map<String, Object> m) {
        live.clear(); lures.clear();
        for (Water w : waters) {
            w.live = false;
            if (m != null && m.get(w.name) instanceof List<?> l) for (int i = 0; i < Math.min(l.size(), w.stock.length); i++) if (l.get(i) instanceof Number n) w.stock[i] = Math.max(0, Math.min(w.capacity[i], n.intValue()));
        }
    }
}
