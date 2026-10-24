package rpg.actor;

import astratto.bridge.Snapshots;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.ai.Npc;
import rpg.animal.Animal;
import rpg.interact.Interactable;
import rpg.interact.Result;
import rpg.objects.Corpse;
import static astratto.bridge.AstrattoNative.*;

/** One ownership path for carried rigged bodies. Items remain on the original corpse throughout. */
public final class BodyCarry {
    private record Piece(long handle, float x, float y, float z, float qw, float qx, float qy, float qz) {}
    private static final class Hold {
        Actor carrier, body;
        Corpse corpse;
        double since, hurtAt, lowerAt = -99;
        float yaw, sx, sy, sz;
        float[] local, start;
        List<Piece> pieces = new ArrayList<>();
    }
    private record Pending(Actor carrier,Interactable focus,double until,double hurtAt) {}
    private final Map<Long,Pending> pending = new HashMap<>();
    private final Rpg g;
    private final Map<Long, Hold> holds = new HashMap<>();
    public BodyCarry(Rpg g) { this.g = g; }
    public boolean busy(Actor a) { return a != null && (holds.containsKey(a.id)||pending.containsKey(a.id)); }
    public boolean ready(Actor a) { Hold h = holds.get(a.id); return h != null && h.lowerAt < 0 && g.time - h.since >= 0.65; }
    public void putDown(Actor a) { Hold h = holds.get(a.id); if (h != null && h.lowerAt < 0) { h.lowerAt = g.time; a.carryLowerAt = g.time; } }
    public Actor target(Interactable focus) { return focus instanceof Corpse c ? c.npc(g) : focus instanceof Animal a && !a.alive && !a.butchered ? a : null; }
    public static boolean lightEnough(Actor body) {
        if (!(body instanceof Animal a)) return true;
        float mass = switch (a.species) { case HORSE -> 450; case BROWN_BEAR -> 280; case COW -> 550; case PIG -> 120; case SHEEP -> 65; case MOUNTAIN_LION -> 75; default -> 30; };
        return mass * a.scale() * a.scale() * a.scale() <= 60;
    }
    public Result begin(Actor carrier, Interactable focus) {
        Actor dead = target(focus);
        if (dead != null && !lightEnough(dead)) return Result.no("That body is too heavy to lift.");
        if (dead == null || dead.alive || dead.carriedBy != 0 || holds.containsKey(carrier.id) || !carrier.alive || carrier.cs.busy()) return Result.no("That body cannot be carried now.");
        if (Math.hypot(focus.ax() - carrier.x, focus.az() - carrier.z) > 2.2f || !g.vox.sight(carrier.x, carrier.eyeY(), carrier.z, focus.ax(), focus.ay() + 0.1f, focus.az())) return Result.no("Move closer to the body.");
        if(dead instanceof Animal animal && animal.ragdoll==null && !g.animals.ensurePhysical(animal))return Result.no("Wait for a body to settle before lifting another.");
        if(dead instanceof Npc npc && !g.ai.ensureCorpsePhysical(npc))return Result.no("That body has already been moved.");
        boolean waiting=dead instanceof Animal animal && (animal.ragdoll==null || g.lastRigids.body(animal.ragdoll.trunk().body())==null)
                || dead instanceof Npc npc && g.lastBodies.ragdoll(npc.ragdoll)==null;
        if(waiting) {
            pending.putIfAbsent(carrier.id,new Pending(carrier,focus,g.time+1.5,carrier.cs.lastHurt));carrier.cs.cancel();return Result.started();
        }
        pending.remove(carrier.id);
        Hold h = new Hold(); h.carrier = carrier; h.body = dead; h.corpse = focus instanceof Corpse c ? c : null; h.since = g.time; h.hurtAt = carrier.cs.lastHurt;
        h.yaw = carrier.yaw;
        if (dead instanceof Npc npc) {
            Snapshots.Ragdoll pose = g.lastBodies.ragdoll(npc.ragdoll);
            if (pose == null) return Result.no("Wait for the body to settle.");
            h.start = pose.p.clone(); h.local = new float[h.start.length];
            h.sx = pose.comX; h.sy = pose.comY; h.sz = pose.comZ;
            // The actual skeleton's long axis becomes the cross-body carry axis; no model-specific dimensions.
            float ux = pose.p[RD_NECK * 3] - pose.p[RD_PELVIS * 3], uy = pose.p[RD_NECK * 3 + 1] - pose.p[RD_PELVIS * 3 + 1], uz = pose.p[RD_NECK * 3 + 2] - pose.p[RD_PELVIS * 3 + 2];
            float length = Math.max(0.001f, (float) Math.sqrt(ux * ux + uy * uy + uz * uz)); ux /= length; uy /= length; uz /= length;
            float rx = -uz, ry = 0, rz = ux;
            if (Math.abs(uy) > 0.95f) { rx = uy; ry = -ux; rz = 0; }
            length = Math.max(0.001f, (float) Math.sqrt(rx * rx + ry * ry + rz * rz)); rx /= length; ry /= length; rz /= length;
            float fx = ry * uz - rz * uy, fy = rz * ux - rx * uz, fz = rx * uy - ry * ux;
            for (int i = 0; i < h.local.length; i += 3) {
                float x = pose.p[i] - h.sx, y = pose.p[i + 1] - h.sy, z = pose.p[i + 2] - h.sz;
                h.local[i] = x * ux + y * uy + z * uz; h.local[i + 1] = x * rx + y * ry + z * rz; h.local[i + 2] = x * fx + y * fy + z * fz;
            }
            g.game.commands().ragdollControl(npc.ragdoll, CONTROL_KINEMATIC, 2);
        } else if (dead instanceof Animal animal && animal.ragdoll != null) {
            var trunk = g.lastRigids.body(animal.ragdoll.trunk().body());
            if (trunk == null) return Result.no("Wait for the body to settle.");
            h.sx = trunk.x; h.sy = trunk.y; h.sz = trunk.z;
            for (var part : animal.ragdoll.pieces) {
                var body = g.lastRigids.body(part.body());
                if (body == null) continue;
                h.pieces.add(new Piece(body.handle, body.x - h.sx, body.y - h.sy, body.z - h.sz, body.qw, body.qx, body.qy, body.qz));
                g.game.commands().rigidSet(body.handle, (part == animal.ragdoll.trunk() ? RIGID_KINEMATIC : 0) | RIGID_DEBRIS | RIGID_NO_MOVERS, 0.8f, 0.02f, 0, 0, 0, 0);
            }
        } else return Result.no("That body is no longer here.");
        carrier.reachX = focus.ax(); carrier.reachY = focus.ay(); carrier.reachZ = focus.az();
        carrier.carryLowerAt = -99;
        holds.put(carrier.id, h); carrier.carryBody = dead.id; carrier.carrySince = g.time; dead.carriedBy = carrier.id;
        carrier.cs.cancel(); carrier.cs.blocking = false; carrier.cs.draw = 0; g.interact.cancel(carrier, "picking up a body");
        if (h.corpse != null) h.corpse.heldBy = carrier.id;
        g.state.commit("BodyPickedUp", carrier.id, dead.id, rpg.world.DevMap.jurisdiction(carrier.x, carrier.z), 0, dead.name);
        return Result.started();
    }
    public void update() {
        for(Pending request:new ArrayList<>(pending.values())) {
            if(g.time>request.until || !request.carrier.alive || !request.focus.exists() || request.carrier.cs.lastHurt>request.hurtAt) { pending.remove(request.carrier.id);continue; }
            Result result=begin(request.carrier,request.focus);if(result.rejected())pending.remove(request.carrier.id);
        }
        for (Hold h : new ArrayList<>(holds.values())) {
            Actor a = h.carrier;
            if (!a.alive || a instanceof Npc n && (n.goal == rpg.ai.Brain.Goal.FIGHT || n.goal == rpg.ai.Brain.Goal.FLEE || !n.loaded) || h.body.alive || a.cs.lastHurt > h.hurtAt || h.body instanceof Animal animal && animal.butchered) { drop(a, false); continue; }
            float t = Math.max(0, Math.min(1, (float) ((g.time - h.since) / 0.65))); t = t * t * (3 - 2 * t);
            float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw), fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
            float lower = h.lowerAt < 0 ? 0 : Math.min(1, (float) (g.time - h.lowerAt) / 0.45f);
            if (lower >= 1) { drop(a, false); continue; }
            float smoothLower = lower * lower * (3 - 2 * lower);
            float cx = a.x + fx * 0.68f, cy = a.y + a.height * 0.58f * (1 - smoothLower) + 0.4f * smoothLower, cz = a.z + fz * 0.68f;
            if (!g.vox.clear(a.x, cy, a.z, cx, cy, cz)) { drop(a, false); continue; }
            if (h.body instanceof Npc npc) {
                float[] pose = new float[h.local.length]; boolean clear = true;
                for (int i = 0; i < pose.length; i += 3) {
                    float x = cx + rx * h.local[i] + fx * h.local[i + 2], y = cy + h.local[i + 1], z = cz + rz * h.local[i] + fz * h.local[i + 2];
                    pose[i] = h.start[i] + (x - h.start[i]) * t; pose[i + 1] = h.start[i + 1] + (y - h.start[i + 1]) * t; pose[i + 2] = h.start[i + 2] + (z - h.start[i + 2]) * t;
                    if (!g.vox.clear(cx, cy, cz, x, y, z)) clear = false;
                }
                if (!clear) { drop(a, false); continue; }
                g.game.commands().ragdollPose(npc.ragdoll, a.vx, 0, a.vz, pose);
            } else {
                float angle = a.yaw - h.yaw, cos = (float) Math.cos(angle), sin = (float) Math.sin(angle), qw = (float) Math.cos(angle / 2), qy = (float) Math.sin(angle / 2);
                for (Piece p : h.pieces) {
                    if (h.body instanceof Animal animal && p.handle != animal.ragdoll.trunk().body()) continue;
                    float x = cx + cos * p.x + sin * p.z, y = cy + p.y, z = cz - sin * p.x + cos * p.z;
                    if (!g.vox.clear(cx, cy, cz, x, y, z)) { drop(a, false); break; }
                    g.game.commands().rigidPlace(p.handle, (1 - t) * (h.sx + p.x) + t * x, (1 - t) * (h.sy + p.y) + t * y, (1 - t) * (h.sz + p.z) + t * z,
                            qw * p.qw - qy * p.qy, qw * p.qx + qy * p.qz, qw * p.qy + qy * p.qw, qw * p.qz - qy * p.qx, 0, 0, 0, 0, 0, 0);
                }
            }
        }
    }
    public void drop(Actor a, boolean throwing) {
        pending.remove(a.id);
        Hold h = holds.remove(a.id); if (h == null) return;
        a.carryBody = 0; a.carryLowerAt = -99; if (throwing) { a.throwAt = g.time; a.cs.castCooldown = Math.max(0.45f, a.cs.castCooldown); }
        h.body.carriedBy = 0;
        if (a instanceof Npc n) n.collectAgain = g.time + 4;
        if (h.corpse != null) h.corpse.heldBy = 0;
        float speed = throwing ? launchSpeed(a) : 0;
        float cp = (float) Math.cos(a.pitch);
        float vx = a.vx - (float) Math.sin(a.aimYaw) * cp * speed, vz = a.vz - (float) Math.cos(a.aimYaw) * cp * speed, vy = throwing ? (float) Math.sin(a.pitch) * speed + 2 : 0;
        if (h.body instanceof Npc npc) {
            g.game.commands().ragdollControl(npc.ragdoll, CONTROL_KINEMATIC, 0);
            g.game.commands().ragdollCarry(npc.ragdoll, vx, vy, vz, 1);
        } else for (Piece p : h.pieces) {
            g.game.commands().rigidSet(p.handle, RIGID_DEBRIS, 0.8f, 0.02f, 1, 0, 0, 0);
            var body = g.lastRigids.body(p.handle);
            if (body != null) g.game.commands().rigidPlace(p.handle, body.x, body.y, body.z, body.qw, body.qx, body.qy, body.qz, vx, vy, vz, 0, 0, 0);
        }
        g.state.commit(throwing ? "BodyThrown" : "BodyPutDown", a.id, h.body.id, rpg.world.DevMap.jurisdiction(a.x, a.z), 0, h.body.name);
    }
    public static float launchSpeed(Actor a) { return 7 + a.skills.level(rpg.skill.Skill.STRENGTH) * 0.04f; }
    public void dropAll() { pending.clear(); for (Hold h : new ArrayList<>(holds.values())) drop(h.carrier, false); }
}
