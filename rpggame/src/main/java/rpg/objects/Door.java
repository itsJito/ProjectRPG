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
import rpg.interact.WorldObject;
import rpg.item.ItemDef;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * A door (GDD 3.2; gamefixes: doors are physical): open or shut, locked or not; opened by hand or by the key, or picked
 * with a lockpick and Thieving. Each leaf is a rigid body (Impulsis) hung on a hinge at its post: a shut door is
 * latched - its hinge held at zero, so it stands like a wall and a body walking into it is stopped by it - and opening
 * it lifts the latch and swings the leaf in. An open leaf is a free body on its hinge, swinging between shut and its
 * stop: a character walking into it pushes it, as an NPC does on its way through, and it pushes back; pushed home, it
 * latches. (gamefixes: open doors flung to either side and could not be pushed - the hinge was in the middle of the wall,
 * so an open leaf turned into the wall itself and stuck there.) A house door is hung on the inside face of its wall:
 * it opens away from whoever opens it - into the room a little past square, or out over the step as far as square - and
 * is then a free body either way; a gate in a fence, with no roof on either side, swings fully both ways. The door's cells say to the rest of the world
 * (routes, sight, light) where a door stands: while the leaf is shut they are a closed door (not walked through by
 * those who do not open doors, not seen through); swung open, they are air. A locked door's cells are solid and sealed.
 * Picking an owned lock is a crime that witnesses can report.
 */
public final class Door extends WorldObject {
    public final int[][] cells;
    public final int height;
    /** The cell the door stands in at its foot (the devmap's floor there; a building's own on ShadowVeil). */
    public final int floor;
    /** Whether the door has written its cells once (the first time, cells already as wanted are left alone). */
    private boolean spawned;
    /** (0.2.2, gamefixes) A fence-gate: one block high, rails and a brace in the fence's own boards, in a fence's line. */
    public final boolean fenceGate;
    public boolean cellDoor;
    /** open: unlatched (swung, or free to swing); locked: latched and locked. */
    public boolean open, locked;
    /**
     * (0.4.7, gamefixes: NPCs stood outside the bank and the guild chapter they live in) those besides its owner who hold
     * its key - everyone who lives or works behind it - and whether one of them may pass.
     */
    public final java.util.Set<Long> keepers = new java.util.HashSet<>();
    public boolean keeps(long id) { return id != 0 && (owner == id || keepers.contains(id)); }
    public final ItemDef key;
    public final int lockLevel;
    /** Opened by an NPC passing through (closed again behind them); relock: they unlocked it, and lock it again once shut. */
    public boolean autoOpened, relock;
    public double autoCloseAt;
    /** The leaves: their bodies and hinges, the turn that swings each one in (+1 or -1), and the angle each is at. */
    public long[] leaf = new long[0], hinge = new long[0];
    public float[] inward = new float[0], angle = new float[0];
    /** Which side is in (+1 / -1 along the doorway's normal), the doorway's line; a gate swings away from whoever opens it. */
    private float inSide = 1, line, swingSign = 1;
    /** A gate in a fence (no roof either side): hung on the line, swinging both ways. A house door opens inward only. */
    private boolean gate;
    /** When a free leaf came back to rest at its frame (it latches if it stays there). */
    private double homeSince = -1;
    public final float[][] hingeAt;
    private boolean closing, cellsOpen;
    private double motorUntil;
    /** How far an unlatched leaf turns: a little past square, into the room (a gate as far either way). */
    public static final float LEAF_MASS = 28, SWING = 1.75f;
    /** How far a house door turns outward (pushed from inside): to square and a little short, never into the wall's thickness. */
    public static final float OUT = 1.45f;
    /** A leaf's thickness, half: the hinge sits this far in from the wall's inner face. */
    private static final float HALF_THICK = 0.06f;
    /** Leaf transforms shared by physical targeting and rendering. */
    private final float[][] leafPose = new float[2][16];
    /** A leaf's pose as drawn (column-major 4x4: its axes, then its centre). */
    public float[] leafPose(int i) { return leafPose[i]; }
    public float leafWidth = 1;
    public static final int MATERIAL_WOOD_DOOR = 2;

    public Door(long id, String name, int[][] cells, boolean locked, ItemDef key, int lockLevel, long owner) {
        this(id, name, cells, locked, key, lockLevel, owner, DevMap.DOOR_HEIGHT, false, DevMap.FLOOR);
    }
    /** (0.4.1) A door standing on its own floor (ShadowVeil's buildings stand at every height): its sill's cell is 'floor'. */
    public Door(long id, String name, int[][] cells, boolean locked, ItemDef key, int lockLevel, long owner, int floor) {
        this(id, name, cells, locked, key, lockLevel, owner, DevMap.DOOR_HEIGHT, false, floor);
    }
    private Door(long id, String name, int[][] cells, boolean locked, ItemDef key, int lockLevel, long owner, int height, boolean fenceGate, int floor) {
        super(id, "door", name, cx(cells), floor + (fenceGate ? 0.5f : 1.2f), cz(cells));
        this.cells = cells; this.locked = locked; this.key = key; this.lockLevel = lockLevel; this.owner = owner;
        this.height = height; this.fenceGate = fenceGate; this.floor = floor;
        this.hingeAt = new float[Math.min(2, cells.length)][];
    }
    /** A gate in a fence, as high as the fence (one block): it swings either way and can be climbed over. */
    public static Door fenceGate(long id, String name, int[][] cells, long owner) { return new Door(id, name, cells, false, null, 1, owner, 1, true, DevMap.FLOOR); }
    public static Door cellDoor(long id, String name, int[][] cells) {
        Door door = new Door(id, name, cells, true, null, 100, 0);
        door.cellDoor = true;
        return door;
    }
    /** (0.4.4) A jail cell's barred door on its own floor (ShadowVeil's jails): 'walk' is the cell above the floor's blocks. */
    public static Door cellDoor(long id, String name, int[][] cells, int walk) {
        Door door = new Door(id, name, cells, true, null, 100, 0, walk);
        door.cellDoor = true;
        return door;
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
    public boolean alongXAxis() { return alongX(); }
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
        int c0x = cells[0][0], c0z = cells[0][1], up = floor + 4, plus = 0, minus = 0;
        for (int k = 1; k <= 3; k++) {
            if (g.vox.solid(c0x + (ax ? 0 : k), up, c0z + (ax ? k : 0))) plus++;
            if (g.vox.solid(c0x - (ax ? 0 : k), up, c0z - (ax ? k : 0))) minus++;
        }
        float in = plus >= minus ? 1 : -1;
        inSide = in; this.line = line;
        gate = plus == 0 && minus == 0;
        // a house door hangs on the inside face of its wall (a gate on the fence line)
        float inset = gate || cellDoor ? 0 : 0.5f - HALF_THICK;
        float leafW = cells.length >= 2 ? (maxA - minA + 1) / 2f : 1f;
        leafWidth = leafW;
        float h = height - 0.04f, y = floor + 0.02f + h / 2;
        for (int i = 0; i < n; i++) {
            boolean first = i == 0;
            float hingeA = first ? minA : maxA + 1;
            float hx = ax ? hingeA : line + in * inset, hz = ax ? line + in * inset : hingeA;
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
            // (0.4.7) a pair's leaves meet at the middle (a hair apart, so they never bind on each other)
            float half = leafW / 2 - (n >= 2 ? 0.004f : 0.015f);
            g.game.commands().rigidCreate(leaf[i], AstrattoNative.SHAPE_BOX, AstrattoNative.RIGID_NO_WORLD | AstrattoNative.RIGID_REPORT, half, h / 2, 0.06f, LEAF_MASS,
                    hx + ex * leafW / 2, y, hz + ez * leafW / 2, qw, 0, qy, 0, 0, 0, 0, 0, 0, 0, 0.5f, 0, 0.05f, 0.6f, 0, 0, 0, MATERIAL_WOOD_DOOR, null);
            float lo = open ? lo(i) : 0, hi = open ? hi(i) : 0;
            g.game.commands().jointCreate(hinge[i], leaf[i], 0, AstrattoNative.JOINT_HINGE, AstrattoNative.JOINT_LIMITED, hx, y, hz, 0, 1, 0, lo, hi, 0, 5, 0);
            restPose(i, hx + ex * leafW / 2, y, hz + ez * leafW / 2, yaw);
        }
        cellsOpen = false;
        applyCells(g, false);
        if (open) swing(g, 1.6f); else hold(g, true);
    }

    /** Reads where each leaf has swung to, latches a closing door as it comes home, and says to the world whether the way is open. */
    /** (0.4.4, optimisation) Shut, latched and still: nothing to follow until someone comes to it. */
    public boolean idle() { return !open && !closing && motorUntil == 0 && !cellsOpen; }

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
        if (closing && most < 0.06f) { latch(g); closing = false; if (relock) { relock = false; setLocked(g, true); } }
        // a free leaf pushed back into its frame and left there a moment is shut: it latches, as a real one clicks to
        if (open && !closing && motorUntil == 0 && most < 0.05f) {
            if (homeSince < 0) homeSince = g.time;
            else if (g.time - homeSince > 0.6 && !blocked(g)) { open = false; latch(g); homeSince = -1; g.state.commit("DoorClosed", 0, id, jurisdiction(), 0, name + " (pushed shut)"); if (g.sfx != null) g.sfx.play("door.close", x, y, z, 0.45f); }
        } else homeSince = -1;
        boolean wayOpen = most > 0.35f;
        if (wayOpen != cellsOpen) applyCells(g, wayOpen);
        // (0.4.6, gamefixes: NPC AI still got stuck) someone of the town going through an open door is never held by its
        // leaf: a leaf swung out against its stop, square across a narrow doorway's jamb, pinned a monk in the monastery's
        // doorway all morning, walking into it and pushed back by it. While anyone but the player is in or by the
        // doorway, the leaf lets bodies by; the player still pushes it (it is solid to them again once the way is clear)
        boolean passing = false;
        if (open && !locked) for (Actor o : g.npcs) if (o.alive && Math.abs(o.x - x) < PASS_REACH && Math.abs(o.z - z) < PASS_REACH) { passing = true; break; }
        if (passing != ghost && leaf.length > 0) {
            ghost = passing;
            int flags = AstrattoNative.RIGID_NO_WORLD | AstrattoNative.RIGID_REPORT | (ghost ? AstrattoNative.RIGID_NO_MOVERS : 0);
            for (long l : leaf) g.game.commands().rigidSet(l, flags, 0, 0, 0, Float.NaN, Float.NaN, Float.NaN);
        }
    }
    /** (0.4.6) How near (m, either way) someone of the world comes to an open door for its leaf to let bodies by. */
    static final float PASS_REACH = rpg.Tuning.f("doors.passReach", 1.8f);
    /** Whether the leaves now let bodies by (see {@link #update}). */
    private boolean ghost;

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
            hit.nx=Math.round(nx); hit.ny=Math.round(ny); hit.nz=Math.round(nz); hit.block=locked?Blocks.LOCKED_DOOR:Blocks.DOOR; hit.door=this;
        }
    }

    private void friction(Rpg g) { for (long h : hinge) g.game.commands().jointMotor(h, 0, 0); }

    /** An actual weapon contact transfers momentum at its point of impact. Body contacts are solved by Impulsis. */
    public void knock(Rpg g, float hx, float hz, float dx, float dz) {
        if (!open || locked) return;
        for (int i = 0; i < leaf.length; i++) {
            float[] m = leafPose[i];
            if (Math.hypot(hx - m[12], hz - m[14]) > leafWidth) continue;
            g.game.commands().rigidImpulse(leaf[i], hx, m[13], hz, dx * 12, 0, dz * 12);
        }
    }

    /** Gives the opening leaf an initial push; closing uses each leaf's current signed angle. */
    private void swing(Rpg g, float seconds) {
        for (int i = 0; i < hinge.length; i++) g.game.commands().jointMotor(hinge[i], inward[i] * swingSign * 2.4f, 90);
        motorUntil = g.time + seconds;
    }

    private void unlatch(Rpg g) { hold(g, false); for (int i = 0; i < hinge.length; i++) g.game.commands().jointLimits(hinge[i], true, lo(i), hi(i)); }

    /**
     * (0.3.9, optimisation: the stutter) a shut door is held in its frame as a fixed body - it stops whoever walks into it
     * as a wall does, and the rigid-body solver has nothing to do for it; unlatched, its leaves are free on their hinges
     * again. Every leaf in the world used to be a live body on a live hinge, woken by everyone who walked past, and their
     * hinges were most of the physics' time.
     */
    private void hold(Rpg g, boolean still) {
        int flags = AstrattoNative.RIGID_NO_WORLD | AstrattoNative.RIGID_REPORT | (still ? AstrattoNative.RIGID_KINEMATIC : 0);
        ghost = false;
        for (int i = 0; i < leaf.length; i++) {
            // held: squared in its frame and stopped dead first - a fixed body keeps whatever spin it had, and a leaf held
            // with the last of its closing swing turned slowly on in the frame and jammed on its hinge when next opened
            if (still) placeShut(g, i);
            g.game.commands().rigidSet(leaf[i], flags, 0, 0, 0, Float.NaN, Float.NaN, Float.NaN);
        }
    }
    /** A leaf put back shut on its hinge, at rest. */
    private void placeShut(Rpg g, int i) {
        boolean ax = alongX();
        float ex = ax ? (i == 0 ? 1 : -1) : 0, ez = ax ? 0 : (i == 0 ? 1 : -1);
        float yaw = (float) Math.atan2(-ez, ex), h = height - 0.04f, leafW = leafWidth;
        g.game.commands().rigidPlace(leaf[i], hingeAt[i][0] + ex * leafW / 2, floor + 0.02f + h / 2, hingeAt[i][1] + ez * leafW / 2,
                (float) Math.cos(yaw / 2), 0, (float) Math.sin(yaw / 2), 0, 0, 0, 0, 0, 0, 0);
    }
    /** A leaf's stops, unlatched: from shut to SWING into the room (a gate, either way). */
    private float lo(int i) { return gate ? -SWING : Math.min(inward[i] * SWING, -inward[i] * OUT); }
    private float hi(int i) { return gate ? SWING : Math.max(inward[i] * SWING, -inward[i] * OUT); }

    private void latch(Rpg g) {
        for (int i = 0; i < hinge.length; i++) { g.game.commands().jointLimits(hinge[i], true, 0, 0); g.game.commands().jointMotor(hinge[i], 0, 0); }
        motorUntil = 0;
        hold(g, true);
    }

    /** The door's cells as the world should take them: a shut door (or a locked one), or an open way. */
    private void applyCells(Rpg g, boolean wayOpen) {
        cellsOpen = wayOpen;
        int block = locked ? Blocks.LOCKED_DOOR : wayOpen ? Blocks.AIR : Blocks.DOOR;
        // (0.4.1, the stutter on loading in) a door hung where its world already has it shut (ShadowVeil's are built shut)
        // leaves the cells alone: every door rewriting its cells at load re-meshed hundreds of chunks in the first seconds
        boolean same = !spawned;
        for (int[] c : cells) for (int y = 0; y < height && same; y++) if (g.vox.block(c[0], floor + y, c[1]) != block) same = false;
        if (!same) for (int[] c : cells) for (int y = 0; y < height; y++) g.setBlock(c[0], floor + y, c[1], block);
        spawned = true;
        for (int[] c : cells) for (int y = 0; y < height; y++) g.game.commands().doorSeal(c[0], floor + y, c[1], locked);
    }

    // ------------------------------------------------------------------ interaction
    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        float r = g.cfg.interactRange;
        if (open) o.add(Option.on("close", "Close", 10, r));
        else if (locked) {
            o.add(Option.off("open", "Open", 10, "It's locked.", r));
            if (key != null) o.add(Option.when(a.has(key, 1), "unlock", "Unlock", 9, "You need the " + key.name + ".", r));
            // (0.3.7) any lock, any hand: a lockpick carried (none needed from Thieving 50); the lock's grade in the label
            String why = rpg.interact.Lockpicking.refusal(a);
            o.add(Option.when(why == null, "lockpick", "Pick lock (" + rpg.interact.Lockpicking.Difficulty.of(lockLevel).title + ")", 8, why, r));
        } else o.add(Option.on("open", "Open", 10, r));
        if (!open && !locked && key != null && a.has(key, 1)) o.add(Option.on("lock", "Lock", 2, r));
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
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
            case "lockpick" -> { return g.lockpicking.start(a, this); }
            default -> { return Result.no("You can't do that."); }
        }
    }

    public void setLocked(Rpg g, boolean lock) {
        locked = lock;
        if (lock) latch(g);
        applyCells(g, false);
    }

    /** Where to stand to open the door from a point's side: a metre in front of the doorway's middle. */
    public float[] front(float px, float pz) {
        float l = lineOf(), s = (alongX() ? pz - l : px - l) >= 0 ? 1 : -1;
        return alongX() ? new float[]{x, l + s * 1.1f} : new float[]{l + s * 1.1f, z};
    }
    /** The doorway's line (known from its cells, hung or not). */
    private float lineOf() { return alongX() ? cells[0][1] + 0.5f : cells[0][0] + 0.5f; }

    /** Which side of the doorway's line a point is on (the same number for two points on the same side). */
    public int side(float px, float pz) { float l = lineOf(); return (alongX() ? pz - l : px - l) >= 0 ? 1 : -1; }

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
        // a door (or a gate) swings away from whoever opens it, so it never swings into them: into the room from the
        // street, out over the step from inside
        if (o && by != null) swingSign = alongXSide(by) ? 1 : -1;
        if (o) { closing = false; unlatch(g); swing(g, 0.25f); }
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
            float yaw = (float) Math.atan2(-ez, ex), h = height - 0.04f, leafW = leafWidth;
            restPose(i, hingeAt[i][0] + ex * leafW / 2, floor + 0.02f + h / 2, hingeAt[i][1] + ez * leafW / 2, yaw);
            g.game.commands().rigidPlace(leaf[i], hingeAt[i][0] + ex * leafW / 2, floor + 0.02f + h / 2, hingeAt[i][1] + ez * leafW / 2,
                    (float) Math.cos(yaw / 2), 0, (float) Math.sin(yaw / 2), 0, 0, 0, 0, 0, 0, 0);
        }
        latch(g);
        closing = false;
        applyCells(g, false);
        if (open && !locked) { unlatch(g); swing(g, 1.6f); }
    }

    @Override public String inspect(Rpg g) {
        return super.inspect(g) + "\n  open=" + open + " locked=" + locked + " key=" + (key == null ? "-" : key.name) + " lockLevel=" + lockLevel
                + String.format(" leaves=%s", java.util.Arrays.toString(angle));
    }

    @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("open", open); m.put("locked", locked); if (relock) m.put("relock", true); return m; }
    @Override public void load(Rpg g, Map<String, Object> m) {
        super.load(g, m); open = astratto.util.Json.bool(m, "open", false); locked = astratto.util.Json.bool(m, "locked", locked); autoOpened = false; apply(g);
        // a door its owner unlocked in passing is locked again, as they would have
        if (astratto.util.Json.bool(m, "relock", false)) { open = false; setLocked(g, true); }
    }
}
