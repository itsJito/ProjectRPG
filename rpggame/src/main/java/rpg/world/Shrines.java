package rpg.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.WorldObject;

/**
 * Shrines of Light (0.3.7, gamefixes): short stone towers, two blocks square, crowned with blue flame, that stand by the
 * graveyards. Resting at one:
 *
 * <ul>
 *   <li>heals the one who rests completely - Health, stamina, Faith and Mana; wounds, burns, frost, shock, slowness all
 *       gone;</li>
 *   <li>passes the night: the world wakes to the next morning;</li>
 *   <li>settles the world's minds - whoever was hunting, fighting, fleeing or searching goes back to their day - and
 *       brings back the ordinary creatures and outlaws that were killed: a cleared lair is made up again, a thinned
 *       one filled; never a dungeon's master or the boss den's, and never anyone who was a person of the towns;</li>
 *   <li>binds the rester to it: when they die, they wake at its graveyard.</li>
 * </ul>
 * What the law knows, what was stolen, what was built or broken stays as it is: the world remembers; only the
 * creatures of the wild and their kind come back.
 */
public final class Shrines {
    private final Rpg g;
    public final List<Shrine> all = new ArrayList<>();
    /** The shrine the player last rested at (0: none - they wake at the town graveyard). */
    public long bound;

    public Shrines(Rpg g) { this.g = g; }

    /** How near a fight may be (someone after the rester) and still let them rest (m); the hour the world wakes to. */
    public static final float NO_REST_NEAR = 20;
    public static final double MORNING = 6.5;

    /** A shrine: where it stands (its 2 x 2 cells from x0, z0), the graveyard it keeps, and where one bound to it wakes. */
    public static final class Shrine extends WorldObject {
        public final int x0, z0;
        public final String graveyard;
        public final float wakeX, wakeZ;
        Shrine(long id, String name, int x0, int z0, String graveyard, float wakeX, float wakeZ) {
            super(id, "shrine", name, x0 + 1f, DevMap.FLOOR + 1.0f, z0 + 1f);
            this.x0 = x0; this.z0 = z0; this.graveyard = graveyard; this.wakeX = wakeX; this.wakeZ = wakeZ;
        }
        @Override public float focusRadius() { return 1.4f; }
        @Override public List<Option> options(Rpg g, Actor a) {
            String why = g.shrines.refusal(a);
            return List.of(Option.when(why == null, "rest", g.shrines.bound == id ? "Rest at the shrine" : "Rest at the shrine (bind to it)", 10, why, g.cfg.interactRange + 1));
        }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            if (!action.equals("rest")) return Result.no("You can't do that.");
            String why = reach(g, a, g.cfg.interactRange + 1);
            if (why == null) why = g.shrines.refusal(a);
            if (why != null) return Result.no(why);
            if (a == g.player && g.ui != null) g.ui.fadeThen(() -> g.shrines.rest(a, this, req));
            else g.shrines.rest(a, this, req);
            return Result.ok();
        }
    }

    // ------------------------------------------------------------------ where they stand
    /** The devmap's shrine: by DevCity's graveyard, on the nearest clear ground to the graves' east end. */
    public void init() {
        if (g.graveyards.plots.isEmpty()) return;
        var plot = g.graveyards.plots.get(0);
        int[] at = clear(Math.round(plot.x() + plot.columns() * 1.4f + 1.5f), Math.round(plot.z() - 2));
        Shrine s = g.objects.add(new Shrine(rpg.Ids.SHRINE_0, "Shrine of Light", at[0], at[1], plot.id(), DevMap.GRAVEYARD_SPAWN[0], DevMap.GRAVEYARD_SPAWN[1]));
        all.add(s);
        // its footprint is solid stone two blocks high: walked round, not through
        for (int x = at[0]; x < at[0] + 2; x++) for (int z = at[1]; z < at[1] + 2; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 2; y++)
            if (g.vox.block(x, y, z) == Blocks.AIR) g.setBlock(x, y, z, Blocks.PROP_STONE);
    }

    /** The nearest 2 x 2 of open, level ground (with a cell of room round it) to a spot. */
    private int[] clear(int cx, int cz) {
        for (int r = 0; r < 14; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int x0 = cx + dx, z0 = cz + dz;
            boolean ok = true;
            for (int x = x0 - 1; x <= x0 + 2 && ok; x++) for (int z = z0 - 1; z <= z0 + 2 && ok; z++)
                ok = g.vox.solid(x, DevMap.FLOOR - 1, z) && g.vox.block(x, DevMap.FLOOR - 1, z) != Blocks.WATER
                        && g.vox.block(x, DevMap.FLOOR, z) == Blocks.AIR && g.vox.block(x, DevMap.FLOOR + 1, z) == Blocks.AIR && g.vox.block(x, DevMap.FLOOR + 2, z) == Blocks.AIR;
            if (ok) return new int[]{x0, z0};
        }
        return new int[]{cx, cz};
    }

    // ------------------------------------------------------------------ resting
    /** Why someone may not rest now (null: they may): something is after them, close by. */
    public String refusal(Actor a) {
        if (!a.alive) return "You are dead.";
        if (g.law.inCustody(a)) return "Not while you are held.";
        for (Actor o : g.actors()) {
            if (o == a || !o.alive || o.distTo(a) > NO_REST_NEAR) continue;
            boolean after = o instanceof rpg.monster.Monster m ? m.target == a.id && m.state == rpg.monster.Monster.State.ENGAGE
                    : o instanceof Npc n && n.fightTarget == a.id;
            if (after) return "You cannot rest with " + o.name + " after you.";
        }
        return null;
    }

    /** The rest itself (at the black of the fade, for the player). */
    public void rest(Actor a, Shrine s, long req) {
        // whole again
        a.restore();
        g.chemistry.cleanse(a);
        if (g.gore != null) g.gore.healed(a);
        a.cs.cancel(); a.cs.slowT = 0; a.cs.frozenT = 0; a.cs.staggered = 0; a.bloodied = 0; a.oxygen = rpg.actor.Swimming.lungs(a);
        // the night passes: the next morning
        double hour = g.state.hourOfDay(), wait = (MORNING - hour + 24) % 24;
        if (wait < 1) wait += 24;
        g.advanceTime(wait * 60, "rested at " + s.name);
        // the world's minds settle, and the wild's dead come back
        int back = g.monsters.restoreFallen();
        int outlaws = 0;
        for (Npc n : g.npcs) {
            if (!n.alive && (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE) && n.carriedBy == 0) { g.ai.revive(n, true); outlaws++; continue; }
            if (!n.alive) continue;
            n.fightTarget = 0; n.hunt = null; n.investigation = null; n.fearOf = 0; n.watchTarget = 0; n.resetMotion();
            n.goal = rpg.ai.Brain.Goal.IDLE; n.nextThink = 0; n.cs.cancel();
            var w = n.aware.get(a.id); if (w != null) w.level = 0;
        }
        g.ai.alarms.clear();
        // bound to it
        boolean newly = bound != s.id;
        bound = s.id;
        g.state.commit(req, "ShrineRested", a.id, s.id, s.jurisdiction(), 0, String.format("%s: %d creatures and %d outlaws back, bound=%s", s.name, back, outlaws, newly ? "new" : "same"));
        if (a == g.player) {
            g.chat.say("You rest by the blue flame. You wake in the morning, whole again." + (newly ? " Your spirit is bound to the " + s.name + ": you will wake by " + s.graveyard + " if you fall." : ""));
            if (back + outlaws > 0) g.chat.add("The wild stirs again: what was slain has returned.", rpg.Chat.Kind.GAME);
        }
        if (g.sfx != null) g.sfx.play("crackle", s.x, s.y + 1.4f, s.z, 0.6f);
        if (g.fx != null) g.fx.ring(s.x, DevMap.FLOOR, s.z, 2.6f, 0.4f, 0.7f, 1f);
    }

    /** Where the player wakes after death: by the shrine they are bound to, or the town graveyard. */
    public float[] wakeSpot() {
        for (Shrine s : all) if (s.id == bound) return new float[]{s.wakeX, s.wakeZ};
        return new float[]{DevMap.GRAVEYARD_SPAWN[0], DevMap.GRAVEYARD_SPAWN[1]};
    }

    public Map<String, Object> save() { return Map.of("bound", bound); }
    public void load(Map<String, Object> m) { bound = m == null ? 0 : astratto.util.Json.lng(m, "bound", 0); }
}
