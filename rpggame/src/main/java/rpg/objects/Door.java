package rpg.objects;

import astratto.bridge.AstrattoNative;
import astratto.bridge.Snapshots;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.Work;
import rpg.interact.WorldObject;
import rpg.item.ItemDef;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * A door (GDD 3.2; gamefixes: doors are physical): open or shut, locked or not; opened by hand or by the key, or picked
 * with a lockpick and Thieving. Each leaf is a rigid body (Impulsis) hung on a hinge at its post: a shut door is
 * latched - its hinge held at zero, so it stands like a wall and a body walking into it is stopped by it - and opening
 * it lifts the latch and swings the leaf in. An open leaf swings freely within its stops: a character walking into it
 * pushes it, as an NPC does on its way through, and it pushes back. The door's cells say to the rest of the world
 * (routes, sight, light) where a door stands: while the leaf is shut they are a closed door (not walked through by
 * those who do not open doors, not seen through); swung open, they are air. A locked door's cells are solid and sealed.
 * Picking an owned lock is a crime that witnesses can report.
 */
public final class Door extends WorldObject {
    public final int[][] cells;
    public final int height = DevMap.DOOR_HEIGHT;
    /** open: unlatched (swung, or free to swing); locked: latched and locked. */
    public boolean open, locked;
    public final ItemDef key;
    public final int lockLevel;
    /** Opened by an NPC passing through (closed again behind them). */
    public boolean autoOpened;
    public double autoCloseAt;
    /** The leaves: their bodies and hinges, the turn that swings each one in (+1 or -1), and the angle each is at. */
    public long[] leaf = new long[0], hinge = new long[0];
    public float[] inward = new float[0], angle = new float[0];
    /** The initial motor swings away from whoever opens it; an unlatched hinge then permits both directions. */
    private float inSide = 1, line, swingSign = 1;
    public final float[][] hingeAt;
    private boolean closing, cellsOpen;
    private double motorUntil;
    public static final float LEAF_MASS = 28, SWING = 3.05f;
    private final float[][] leafPose = new float[2][16];
    private float leafWidth = 1;
    public static final int MATERIAL_WOOD_DOOR = 2;

    public Door(long id, String name, int[][] cells, boolean locked, ItemDef key, int lockLevel, long owner) {
        super(id, "door", name, cx(cells), DevMap.FLOOR + 1.2f, cz(cells));
        this.cells = cells; this.locked = locked; this.key = key; this.lockLevel = lockLevel; this.owner = owner;
        this.hingeAt = new float[Math.min(2, cells.length)][];
    }

    private static float cx(int[][] c) { float s = 0; for (int[] p : c) s += p[0] + 0.5f; return s / c.length; }
    private static float cz(int[][] c) { float s = 0; for (int[] p : c) s += p[1] + 0.5f; return s / c.length; }

    @Override public float focusRadius() { return 1.1f; }
    @Override public float focusEntry(float ox,float oy,float oz,float dx,float dy,float dz) {
        var hit=new rpg.world.Voxels.Hit(); ray(hit,ox,oy,oz,dx,dy,dz,12);
        return hit.hit?hit.dist:Float.POSITIVE_INFINITY;
    }
    @Override public String label() { return name + (locked ? " (locked)" : open ? " (open)" : ""); }

    // ------------------------------------------------------------------ the leaves in the physics world
    /** Whether the doorway runs along x (its cells side by side in x, the leaves turning out of the z line). */
    private boolean alongX() { return cells.length < 2 || cells[0][1] == cells[1][1]; }

    /** Hangs the leaves on their hinges (once, when the door is made in the world). */
    public void spawn(Rpg g) {
        int n = Math.min(2, cells.length);
        leaf = new long[n]; hinge = new long[n]; inward = new float[n]; angle = new float[n];
        boolean ax = alongX();
        int minA = Integer.MAX_VALUE, maxA = Integer.MIN_VALUE;
        for (int[] c : cells) { int v = ax ? c[0] : c[1]; minA = Math.min(minA, v); maxA = Math.max(maxA, v); }
        float line = ax ? cells[0][1] + 0.5f : cells[0][0] + 0.5f;
        // which side is inside: the one with a roof over it
        int c0x = cells[0][0], c0z = cells[0][1], up = DevMap.FLOOR + 4, plus = 0, minus = 0;
        for (int k = 1; k <= 3; k++) {
            if (g.vox.solid(c0x + (ax ? 0 : k), up, c0z + (ax ? k : 0))) plus++;
            if (g.vox.solid(c0x - (ax ? 0 : k), up, c0z - (ax ? k : 0))) minus++;
        }
        float in = plus >= minus ? 1 : -1;
        inSide = in; this.line = line;
        float leafW = cells.length >= 2 ? (maxA - minA + 1) / 2f : 1f;
        leafWidth = leafW;
        float h = height - 0.04f, y = DevMap.FLOOR + 0.02f + h / 2;
        for (int i = 0; i < n; i++) {
            boolean first = i == 0;
            float hingeA = first ? minA : maxA + 1;
            float hx = ax ? hingeA : line, hz = ax ? line : hingeA;
            hingeAt[i] = new float[]{hx, hz};
            // the leaf runs from its hinge toward the doorway's middle: its own +x
            float ex = ax ? (first ? 1 : -1) : 0, ez = ax ? 0 : (first ? 1 : -1);
            float yaw = (float) Math.atan2(-ez, ex);          // a turn about +y that takes +x onto (ex, ez)
            float qw = (float) Math.cos(yaw / 2), qy = (float) Math.sin(yaw / 2);
            float nx = ax ? 0 : in, nz = ax ? in : 0;          // the inward side
            // a small turn +t of the leaf about +y moves its far edge along (ez, -ex); the hinge's angle is the world's
            // turn as the leaf sees it, so it runs the other way: the sign that swings this leaf inward, in the hinge's terms
            inward[i] = (ez * nx + -ex * nz) > 0 ? -1 : 1;
            leaf[i] = g.game.reserve(AstrattoNative.KIND_RIGID);
            hinge[i] = g.game.reserve(AstrattoNative.KIND_JOINT);
            float half = leafW / 2 - 0.015f;
            g.game.commands().rigidCreate(leaf[i], AstrattoNative.SHAPE_BOX, AstrattoNative.RIGID_NO_WORLD | AstrattoNative.RIGID_REPORT, half, h / 2, 0.06f, LEAF_MASS,
                    hx + ex * leafW / 2, y, hz + ez * leafW / 2, qw, 0, qy, 0, 0, 0, 0, 0, 0, 0, 0.5f, 0, 0.05f, 0.6f, 0, 0, 0, MATERIAL_WOOD_DOOR, null);
            float lo = open ? -SWING : 0, hi = open ? SWING : 0;
            g.game.commands().jointCreate(hinge[i], leaf[i], 0, AstrattoNative.JOINT_HINGE, AstrattoNative.JOINT_LIMITED, hx, y, hz, 0, 1, 0, lo, hi, 0, 5, 0);
            restPose(i, hx + ex * leafW / 2, y, hz + ez * leafW / 2, yaw);
        }
        cellsOpen = false;
        applyCells(g, false);
        if (open) swing(g, 1.6f);
    }

    /** Reads where each leaf has swung to, latches a closing door as it comes home, and says to the world whether the way is open. */
    public void update(Rpg g, Snapshots.Rigids rigids) {
        float most = 0;
        for (int i = 0; i < hinge.length; i++) {
            Snapshots.Joint j = rigids.joint(hinge[i]);
            if (j != null) angle[i] = j.angle;
            most = Math.max(most, Math.abs(angle[i]));
            var body = rigids.body(leaf[i]);
            if (body != null) body.matrix(leafPose[i]);
            if (closing) g.game.commands().jointMotor(hinge[i], Math.clamp(-angle[i] * 5, -2.4f, 2.4f), 90);
        }
        if (motorUntil > 0 && g.time > motorUntil && !closing) { friction(g); motorUntil = 0; }
        if (closing && most < 0.06f) { latch(g); closing = false; }
        boolean wayOpen = most > 0.35f;
        if (wayOpen != cellsOpen) applyCells(g, wayOpen);
    }

    private void restPose(int i, float x, float y, float z, float yaw) {
        var pose = new Snapshots.Rigid(); pose.x = x; pose.y = y; pose.z = z;
        pose.qw = (float)Math.cos(yaw / 2); pose.qy = (float)Math.sin(yaw / 2);
        pose.matrix(leafPose[i]); angle[i] = 0;
    }

    /** Segment/OBB intersection against the same rigid leaf pose used for drawing. No full-cell doorway collider. */
    public void ray(rpg.world.Voxels.Hit hit, float ox, float oy, float oz, float dx, float dy, float dz, float max) {
        if (Math.hypot(ox - x, oz - z) > max + leafWidth + 2) return;
        for (int i = 0; i < leaf.length; i++) {
            float[] m = leafPose[i]; float near = 0, far = hit.hit ? Math.min(max, hit.dist) : max;
            float nx = 0, ny = 0, nz = 0; boolean miss = false;
            for (int axis = 0; axis < 3; axis++) {
                int k = axis * 4;
                float origin = (ox-m[12])*m[k] + (oy-m[13])*m[k+1] + (oz-m[14])*m[k+2];
                float direction = dx*m[k] + dy*m[k+1] + dz*m[k+2];
                float half = axis == 0 ? leafWidth / 2 - 0.015f : axis == 1 ? (height-0.04f)/2 : 0.06f;
                if (Math.abs(direction) < 1e-6f) { if (Math.abs(origin) > half) { miss = true; break; } continue; }
                float a = (-half-origin)/direction, b = (half-origin)/direction, enter = Math.min(a,b);
                if (enter > near) { near=enter; float sign=direction>0?-1:1; nx=m[k]*sign; ny=m[k+1]*sign; nz=m[k+2]*sign; }
                far = Math.min(far, Math.max(a,b));
                if (near > far) { miss=true; break; }
            }
            if (miss) continue;
            hit.hit=true; hit.dist=near; hit.x=ox+dx*near; hit.y=oy+dy*near; hit.z=oz+dz*near;
            hit.bx=(int)Math.floor(hit.x); hit.by=(int)Math.floor(hit.y); hit.bz=(int)Math.floor(hit.z);
            hit.nx=Math.round(nx); hit.ny=Math.round(ny); hit.nz=Math.round(nz); hit.block=locked?Blocks.LOCKED_DOOR:Blocks.DOOR;
        }
    }

    private void friction(Rpg g) { for (long h : hinge) g.game.commands().jointMotor(h, 0, 0); }

    /** Gives the opening leaf an initial push; closing uses each leaf's current signed angle. */
    private void swing(Rpg g, float seconds) {
        for (int i = 0; i < hinge.length; i++) g.game.commands().jointMotor(hinge[i], inward[i] * swingSign * 2.4f, 90);
        motorUntil = g.time + seconds;
    }

    private void unlatch(Rpg g) { for (int i = 0; i < hinge.length; i++) g.game.commands().jointLimits(hinge[i], true, -SWING, SWING); }

    private void latch(Rpg g) {
        for (int i = 0; i < hinge.length; i++) { g.game.commands().jointLimits(hinge[i], true, 0, 0); g.game.commands().jointMotor(hinge[i], 0, 0); }
        motorUntil = 0;
    }

    /** The door's cells as the world should take them: a shut door (or a locked one), or an open way. */
    private void applyCells(Rpg g, boolean wayOpen) {
        cellsOpen = wayOpen;
        int block = locked ? Blocks.LOCKED_DOOR : wayOpen ? Blocks.AIR : Blocks.DOOR;
        for (int[] c : cells) for (int y = 0; y < height; y++) g.setBlock(c[0], DevMap.FLOOR + y, c[1], block);
        for (int[] c : cells) for (int y = 0; y < height; y++) g.game.commands().doorSeal(c[0], DevMap.FLOOR + y, c[1], locked);
    }

    // ------------------------------------------------------------------ interaction
    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        float r = g.cfg.interactRange;
        if (open) o.add(Option.on("close", "Close", 10, r));
        else if (locked) {
            o.add(Option.off("open", "Open", 10, "It's locked.", r));
            if (key != null) o.add(Option.when(a.has(key, 1), "unlock", "Unlock", 9, "You need the " + key.name + ".", r));
            boolean lvl = a.skills.level(Skill.THIEVING) >= lockLevel, pick = a.has(Items.LOCKPICK, 1);
            o.add(Option.when(lvl && pick, "lockpick", "Pick lock", 8, !lvl ? "You need Thieving level " + lockLevel + "." : "You need a lockpick.", r));
        } else o.add(Option.on("open", "Open", 10, r));
        if (!open && !locked && key != null && a.has(key, 1)) o.add(Option.on("lock", "Lock", 2, r));
        o.add(Option.on("examine", "Examine", 0, g.cfg.inspectRange));
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
        String why = reach(g, a, g.cfg.interactRange);
        if (why != null) return Result.no(why);
        switch (action) {
            case "open" -> {
                if (open) return Result.no("It's already open.");
                if (locked) return Result.no("The door is locked.");
                setOpen(g, true, a, req);
                return Result.ok();
            }
            case "close" -> {
                if (!open) return Result.no("It's already closed.");
                if (blocked(g)) return Result.no("Something is in the way.");
                setOpen(g, false, a, req);
                return Result.ok();
            }
            case "unlock", "lock" -> {
                if (key == null || !a.has(key, 1)) return Result.no("You need the " + (key == null ? "key" : key.name) + ".");
                if (open) return Result.no("Close it first.");
                boolean lock = action.equals("lock");
                if (locked == lock) return Result.no(lock ? "It's already locked." : "It isn't locked.");
                setLocked(g, lock);
                g.state.commit(req, lock ? "DoorLocked" : "DoorUnlocked", a.id, id, jurisdiction(), 0, name);
                if (a == g.player) g.chat.say(lock ? "You lock the door." : "You unlock the door.");
                if (g.sfx != null) g.sfx.play("lock", x, y, z, 0.6f);
                return Result.ok();
            }
            case "lockpick" -> {
                if (!locked) return Result.no("It isn't locked.");
                if (a.skills.level(Skill.THIEVING) < lockLevel) return Result.no("You need Thieving level " + lockLevel + " to pick this lock.");
                if (!a.has(Items.LOCKPICK, 1)) return Result.no("You need a lockpick.");
                g.interact.begin(new Work(a, this, "lockpick", "Picking the lock", 2.4f, false, g.time, () -> commitPick(g, a)));
                return Result.started();
            }
            default -> { return Result.no("You can't do that."); }
        }
    }

    private Result commitPick(Rpg g, Actor a) {
        long req = g.state.newRequestId();
        String why = reach(g, a, g.cfg.interactRange);
        if (why != null) return Result.no(why);
        if (!locked) return Result.no("It isn't locked any more.");
        if (!a.has(Items.LOCKPICK, 1)) return Result.no("You need a lockpick.");
        int lvl = a.skills.level(Skill.THIEVING);
        double chance = Math.min(0.95, 0.4 + (lvl - lockLevel) * 0.04);
        double roll = g.roll(req, id);
        boolean ok = roll < chance;
        var ev = g.state.commit(req, ok ? "LockPicked" : "LockpickFailed", a.id, id, jurisdiction(), 0, String.format("%s chance=%.2f roll=%.3f", name, chance, roll));
        if (owner != 0 && owner != a.id) g.law.crime(a, "LOCKPICKING", owner, 0, this, ev.id());
        if (!ok) { if (a == g.player) g.chat.say("You fail to pick the lock."); g.prog.award(a, Skill.THIEVING, 5, "failed lockpick", ev.id()); return Result.ok(); }
        setLocked(g, false);
        g.prog.award(a, Skill.THIEVING, 35, "picked a lock", ev.id());
        if (a == g.player) g.chat.say("You pick the lock.");
        if (g.sfx != null) g.sfx.play("lock", x, y, z, 0.5f);
        return Result.ok();
    }

    public void setLocked(Rpg g, boolean lock) {
        locked = lock;
        if (lock) latch(g);
        applyCells(g, false);
    }

    /** Whether an actor stands in the doorway. */
    public boolean blocked(Rpg g) {
        for (Actor o : g.actors()) for (int[] c : cells) if (Math.abs(o.x - (c[0] + 0.5f)) < 0.5f + o.radius && Math.abs(o.z - (c[1] + 0.5f)) < 0.5f + o.radius) return true;
        return false;
    }

    /** Whether someone stands on the side a gate normally swings away from (so it may swing its usual way). */
    private boolean alongXSide(Actor by) {
        float side = alongX() ? by.z - line : by.x - line;
        return side * inSide < 0;
    }

    /** Opens (lifts the latch and swings the leaves in) or closes (swings them home; they latch as they arrive). */
    public void setOpen(Rpg g, boolean o, Actor by, long req) {
        open = o;
        if (leaf.length == 0) spawn(g);
        if (o && by != null) swingSign = alongXSide(by) ? 1 : -1;
        if (o) { closing = false; unlatch(g); swing(g, 0.7f); }
        else { closing = true; motorUntil = 0; }
        g.state.commit(req, o ? "DoorOpened" : "DoorClosed", by == null ? 0 : by.id, id, jurisdiction(), 0, name);
        if (g.sfx != null) g.sfx.play(o ? "door" : "door.close", x, y, z, 0.6f);
    }

    /** Puts the leaves and the cells back as the state says (after a load). */
    public void apply(Rpg g) {
        if (leaf.length == 0) { spawn(g); return; }
        for (int i = 0; i < leaf.length; i++) {
            // back on the hinge at zero, then open again if it was
            boolean ax = alongX();
            float ex = ax ? (i == 0 ? 1 : -1) : 0, ez = ax ? 0 : (i == 0 ? 1 : -1);
            float yaw = (float) Math.atan2(-ez, ex), h = height - 0.04f, leafW = cells.length >= 2 ? 1f : 1f;
            restPose(i, hingeAt[i][0] + ex * leafW / 2, DevMap.FLOOR + 0.02f + h / 2, hingeAt[i][1] + ez * leafW / 2, yaw);
            g.game.commands().rigidPlace(leaf[i], hingeAt[i][0] + ex * leafW / 2, DevMap.FLOOR + 0.02f + h / 2, hingeAt[i][1] + ez * leafW / 2,
                    (float) Math.cos(yaw / 2), 0, (float) Math.sin(yaw / 2), 0, 0, 0, 0, 0, 0, 0);
        }
        latch(g);
        closing = false;
        applyCells(g, false);
        if (open && !locked) { unlatch(g); swing(g, 1.6f); }
    }

    @Override public String examine(Rpg g) { return locked ? "A sturdy door with an iron lock." : open ? "A wooden door, standing open." : "A wooden door, latched shut."; }
    @Override public String inspect(Rpg g) {
        return super.inspect(g) + "\n  open=" + open + " locked=" + locked + " key=" + (key == null ? "-" : key.name) + " lockLevel=" + lockLevel
                + String.format(" leaves=%s", java.util.Arrays.toString(angle));
    }

    @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("open", open); m.put("locked", locked); return m; }
    @Override public void load(Rpg g, Map<String, Object> m) { super.load(g, m); open = astratto.util.Json.bool(m, "open", false); locked = astratto.util.Json.bool(m, "locked", locked); autoOpened = false; apply(g); }
}
