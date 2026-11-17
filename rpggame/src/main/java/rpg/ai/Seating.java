package rpg.ai;

import static astratto.bridge.AstrattoNative.*;

import rpg.Rpg;

/**
 * (0.3.9, gamefixes: chairs, settees, stools and benches had no collision) A seat is solid: a body walking into it is
 * stopped as by any table. So a person does not walk onto a seat to sit; they walk up to its open side - in front of it,
 * else beside it, else behind it - and are eased down onto it, then held there (the physics' body posed in the seat, as a
 * rider is held in a saddle). Whatever they were sitting for has to keep asking for the seat every moment it wants it: the
 * moment it stops (a new goal, a blow, a fright, a call to work), they get up and step back out to where they came in from.
 */
public final class Seating {
    /** How long sitting down (or getting up) takes, s; how far from the seat's middle its approach is, m. */
    public static final float EASE = rpg.Tuning.f("seating.ease", 0.55f), APPROACH = rpg.Tuning.f("seating.approach", 1.0f);

    private final Rpg g;
    private final Brain brain;
    Seating(Rpg g, Brain brain) { this.g = g; this.brain = brain; }

    /**
     * To a seat and down on it: true once seated (and every moment after, while it keeps being asked for). The seat is a
     * cell's middle (x, z) on a floor (floorY, where feet stand), faced toward yaw (where a seated body looks).
     */
    public boolean sit(Npc n, float x, float floorY, float z, float yaw) {
        if (n.seated && Float.isFinite(n.seatX) && Math.abs(n.seatX - x) < 0.3f && Math.abs(n.seatZ - z) < 0.3f) {
            n.seatHeld = g.time; n.seatYaw = yaw; n.wantYaw = yaw;
            return true;
        }
        if (n.seated) stand(n);
        float[] in = approach(x, floorY, z, yaw);
        if (in == null) { n.lastOutcome = "no way in to the seat"; return false; }
        if (!brain.goTo(n, in[0], floorY, in[1], false, 0.45f)) return false;
        n.seated = true;
        n.seatX = x; n.seatY = floorY; n.seatZ = z; n.standX = n.x; n.standZ = n.z;
        n.seatYaw = yaw; n.wantYaw = yaw; n.seatHeld = n.seatSince = g.time;
        g.ai.desire(n, AiSystem.A_IDLE);
        return true;
    }

    /** Straight into a seat, already sat (a review shot's set-up, a loaded save): no walk to it, no easing down. */
    public void seatNow(Npc n, float x, float floorY, float z, float yaw) {
        float[] in = approach(x, floorY, z, yaw);
        n.seated = true;
        n.seatX = x; n.seatY = floorY; n.seatZ = z;
        n.standX = in == null ? x : in[0]; n.standZ = in == null ? z : in[1];
        n.seatYaw = yaw; n.yaw = n.wantYaw = yaw; n.seatHeld = g.time; n.seatSince = g.time - EASE;
        hold(n);
    }

    /**
     * (0.4.7, gamefixes: people pull out a chair and sit, push it in, and push it back as they rise) to a piece of
     * furniture and down on it: true once seated. A chair is drawn out from its table first, the sitter eases down into
     * it and the two go in together to the table; a bench, a stool, an armchair or a settee is simply sat on (a bench's
     * place along it by who sits). Held the same way as any seat: whatever wants it must ask every moment.
     */
    public boolean sitOn(Npc n, rpg.objects.Furniture f) {
        if (f == null || !f.exists() || f.sitter != 0 && f.sitter != n.id) return false;
        float[] fr = f.front();
        float x = f.x, z = f.z;
        if (f.kind == rpg.objects.Furniture.Kind.BENCH || f.kind == rpg.objects.Furniture.Kind.SOFA) {
            // along a long seat: a place by who sits, away from its ends
            boolean alongX = f.width() >= f.depth();
            float len = alongX ? f.width() : f.depth(), slot = (Math.floorMod(n.id, Math.max(1, (int) len)) + 0.5f) - len / 2f;
            if (alongX) x += slot; else z += slot;
        }
        boolean chair = f.kind == rpg.objects.Furniture.Kind.CHAIR;
        // the sitter sits back from the seat's middle as far as the chair stands out from its table
        float pulled = chair ? f.pull * rpg.objects.Furniture.CHAIR_PULL : 0;
        float sx = x - fr[0] * pulled, sz = z - fr[1] * pulled;
        if (n.seated && n.seatFurniture == f.id) {
            // in: once down, the chair goes back in toward the table with them in it
            if (chair && g.time - n.seatSince > PULL_TIME + EASE) { f.pullTarget = TUCKED; moving.add(f); }
            n.seatX = sx; n.seatZ = sz; n.seatHeld = g.time; n.seatYaw = f.yaw(); n.wantYaw = f.yaw();
            return true;
        }
        if (n.seated) stand(n);
        float[] in = approach(x, f.floor, z, f.yaw());
        if (in == null) { n.lastOutcome = "no way in to the " + f.kind.title.toLowerCase(); return false; }
        if (!brain.goTo(n, in[0], f.floor, in[1], false, 0.45f)) return false;
        // at its side: the chair drawn out first, then down
        if (chair && f.pull < 0.9f) { f.pullTarget = 1; f.sitter = n.id; moving.add(f); g.ai.desire(n, AiSystem.A_HOLD); n.wantYaw = (float) Math.atan2(-(x - n.x), -(z - n.z)); return false; }
        n.seated = true; n.seatFurniture = f.id; f.sitter = n.id;
        n.seatX = sx; n.seatY = f.floor; n.seatZ = sz; n.standX = n.x; n.standZ = n.z;
        n.seatYaw = f.yaw(); n.wantYaw = f.yaw(); n.seatHeld = n.seatSince = g.time;
        g.ai.desire(n, AiSystem.A_IDLE);
        return true;
    }
    /** How long drawing a chair out takes (s), and how far in it goes with its sitter (a share of the way out). */
    /** (0.4.7) How long a seat stays held after it was last asked for (s): a frame or two's grace, not an instant. */
    static final float HELD_SLACK = rpg.Tuning.f("seating.heldSlack", 0.1f);
    public static final float PULL_TIME = rpg.Tuning.f("seating.pull", 0.45f), TUCKED = rpg.Tuning.f("seating.tucked", 0.3f);
    /** (0.4.7) The chairs drawn out or going in, or with someone in them. */
    final java.util.Set<rpg.objects.Furniture> moving = new java.util.LinkedHashSet<>();
    /** Each frame: every chair's drawing out and pushing in, eased (a chair let go goes back under its table). */
    void chairs(float dt) {
        // only the chairs on the move (a few at a time), not every piece of furniture in the world
        for (var it = moving.iterator(); it.hasNext(); ) {
            var f = it.next();
            if (f.pull == f.pullTarget && f.sitter == 0 || f.removed) { it.remove(); continue; }
            float step = dt / PULL_TIME;
            f.pull = f.pull < f.pullTarget ? Math.min(f.pullTarget, f.pull + step) : Math.max(f.pullTarget, f.pull - step);
            if (f.sitter != 0 && !(g.actor(f.sitter) instanceof Npc s && s.seatFurniture == f.id) && f.pull >= 0.99f) { f.sitter = 0; f.pullTarget = 0; }
        }
    }

    /** Up from the seat and back out to where they came in from (nothing when they are not sitting). */
    public void stand(Npc n) {
        if (!n.seated) return;
        n.seated = false;
        // (0.4.7) a chair goes out again as they rise, and back in under the table once they are clear of it
        if (n.seatFurniture != 0 && g.objects.get(n.seatFurniture) instanceof rpg.objects.Furniture f) {
            if (f.kind == rpg.objects.Furniture.Kind.CHAIR) { f.pullTarget = 1; moving.add(f); } else f.sitter = 0;
        }
        n.seatFurniture = 0;
        if (!Float.isFinite(n.seatX)) return;
        float x = n.standX, z = n.standZ, y = n.seatY;
        n.seatX = Float.NaN;
        g.teleport(n, x, y, z);
        // out of the pose the seat held them in (a stance-only input leaves a body posed - frozen where it stood)
        if (n.body != 0) g.game.commands().moverInput(n.body, 0, 0, 0, 0, AiSystem.NPC_ACCEL, 0);
    }

    /**
     * Each frame after a person's behaviour has run: a seat nobody asked for this frame is got up from; one still wanted
     * holds its sitter in it, eased from where they stood down into it.
     */
    void hold(Npc n) {
        if (!n.seated || !Float.isFinite(n.seatX)) return;
        if (n.seatHeld < g.time - HELD_SLACK || n.reacting || !n.alive || n.cs.staggered > 0) { stand(n); return; }
        float k = (float) Math.min(1, (g.time - n.seatSince) / EASE);
        k = k * k * (3 - 2 * k);
        float x = n.standX + (n.seatX - n.standX) * k, z = n.standZ + (n.seatZ - n.standZ) * k;
        n.x = x; n.y = n.seatY; n.z = z; n.vx = n.vz = 0;
        if (n.body != 0) {
            g.game.commands().moverPlace(n.body, x, n.seatY, z, 0, 0, 0);
            g.game.commands().moverInput(n.body, 0, 0, 0, 0, AiSystem.NPC_ACCEL, MOVER_POSED);
        }
    }

    /** The open side of a seat to sit down from: in front, then either side, then behind; null when it is boxed in. */
    public float[] approach(float x, float floorY, float z, float yaw) {
        float fx = (float) -Math.sin(yaw), fz = (float) -Math.cos(yaw);
        float[][] ways = {{fx, fz}, {-fz, fx}, {fz, -fx}, {-fx, -fz}};
        int fy = (int) Math.floor(floorY + 0.05f);
        for (float[] w : ways) {
            int cx = (int) Math.floor(x + w[0] * APPROACH), cz = (int) Math.floor(z + w[1] * APPROACH);
            if (g.vox.solid(cx, fy, cz) || g.vox.solid(cx, fy + 1, cz) || !g.vox.solid(cx, fy - 1, cz)) continue;
            return new float[]{cx + 0.5f, cz + 0.5f};
        }
        return null;
    }
}
