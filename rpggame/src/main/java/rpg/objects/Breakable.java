package rpg.objects;

import astratto.voxel.VoxModel;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Option;
import rpg.interact.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * (0.3.8, gamefixes: wooden boxes, crates, signs and barrels - physics objects, and destructible) A loose wooden thing:
 * a crate, a small box, a barrel, a standing sign. It is a rigid body - it can be pushed, knocked over, thrown about by a
 * blow, stacked, and it falls - and it is built of voxels, each with its own toughness: wood gives, nails and iron hoops
 * hold. A blow (a blade, a club, an arrow, a spell, a fall, another body flung into it) carves into it where it lands, as
 * deep and as wide as the blow was hard and of its kind - a point punches a hole, an edge splits a board, a club staves
 * it in - and the voxels it breaks fly off as splinters. Enough of it broken, or one blow hard enough to burst it, and it
 * comes apart board by board: each surviving plank or stave a piece of debris of its own. What was in it spills out.
 * Its owner's goods are its owner's: breaking it is vandalism, and taking from it theft.
 */
public final class Breakable extends Container {
    /** One voxel's size (m): a sixteenth of a block - fine enough for a plank to split along its grain. */
    public static final float VOXEL = 1 / 16f;

    /** The kinds: their size (voxels), weight (kg), how much they hold, and how hard a single blow must be to burst one. */
    public enum Form {
        CRATE("Crate", 12, 12, 12, 16, 20, 15f, "crate"),
        BOX("Box", 8, 6, 8, 5, 8, 8f, "box"),
        BARREL("Barrel", 10, 14, 10, 30, 20, 18f, "barrel"),
        SIGN("Sign", 12, 18, 8, 9, 0, 10f, "sign");
        public final String title, def;
        public final int sx, sy, sz, holds;
        public final float mass, burst;
        Form(String title, int sx, int sy, int sz, float mass, int holds, float burst, String def) {
            this.title = title; this.sx = sx; this.sy = sy; this.sz = sz; this.mass = mass; this.holds = holds; this.burst = burst; this.def = def;
        }
        /** Half extents (m). */
        public float hx() { return sx * VOXEL / 2; }
        public float hy() { return sy * VOXEL / 2; }
        public float hz() { return sz * VOXEL / 2; }
    }

    /** What a voxel is made of: none, wood, end grain (a plank's cut end, weaker), iron (a nail, a hoop: it holds). */
    public static final byte AIR = 0, WOOD = 1, END = 2, IRON = 3;
    /** Toughness by material (how much blow a voxel takes before it goes). */
    static final float[] TOUGH = {0, 1.6f, 1.1f, 7f};

    public final Form form;
    /** Its voxels as they are now, the material of each, how much each has left, and the board each belongs to. */
    public final VoxModel vox;
    public final byte[] mat, board;
    public final float[] hp;
    /** Bumped whenever a voxel goes: the renderer remakes its mesh (its mesh id, and the version it was made at). */
    public int version, gpu, gpuMetal, gpuVersion = -1;
    /** Voxels it was built with, and how many are gone. */
    public final int built;
    public int lost;
    /** Its rigid body and its turn (w, x, y, z). */
    public long body;
    public float qw = 1, qx, qy, qz;
    public boolean broken;
    /** A sign's words. */
    public String text = "";
    /** When it was last struck (sounds and splinters are not repeated within a frame). */
    public double struckAt = -9;

    public Breakable(long id, Form form, float x, float y, float z, float yaw, long owner) {
        super(id, form.def, form.title, form == Form.BARREL ? "barrel" : "crate", x, y, z, Math.max(1, form.holds), owner);
        this.form = form;
        this.vox = new VoxModel(form.sx, form.sy, form.sz);
        int n = form.sx * form.sy * form.sz;
        this.mat = new byte[n]; this.board = new byte[n]; this.hp = new float[n];
        Shapes.build(this);
        int c = 0;
        for (int i = 0; i < n; i++) if (mat[i] != AIR) { hp[i] = TOUGH[mat[i]]; c++; }
        built = c;
        qw = (float) Math.cos(yaw / 2); qy = (float) Math.sin(yaw / 2);
        homeX = x; homeY = y; homeZ = z; homeYaw = yaw;
    }
    /** (0.4.6) Where and how it was first set down (a restored world puts it back there, whole). */
    public final float homeX, homeY, homeZ, homeYaw;
    /** Whole again where it was first set down: every voxel back, its pose its first. */
    public void rebuild() {
        java.util.Arrays.fill(mat, AIR); java.util.Arrays.fill(board, (byte) 0);
        java.util.Arrays.fill(vox.col, 0);
        Shapes.build(this);
        for (int i = 0; i < mat.length; i++) hp[i] = mat[i] != AIR ? TOUGH[mat[i]] : 0;
        lost = 0; broken = false; removed = false;
        x = homeX; y = homeY; z = homeZ; qw = (float) Math.cos(homeYaw / 2); qx = 0; qy = (float) Math.sin(homeYaw / 2); qz = 0;
        version++;
    }

    public int index(int x, int y, int z) { return (x * form.sy + y) * form.sz + z; }
    public boolean inside(int x, int y, int z) { return x >= 0 && y >= 0 && z >= 0 && x < form.sx && y < form.sy && z < form.sz; }

    /** How much of it is still whole (0 - 1). */
    public float integrity() { return built == 0 ? 0 : 1 - lost / (float) built; }

    @Override public float focusRadius() { return Math.max(form.hx(), Math.max(form.hy(), form.hz())) + 0.15f; }

    @Override public String label() { return broken ? "Broken " + form.title.toLowerCase() : form == Form.SIGN ? "Sign" : super.label(); }

    @Override public List<Option> options(Rpg g, Actor a) {
        if (broken) return List.of();
        if (form == Form.SIGN) {
            List<Option> o = new ArrayList<>();
            o.add(Option.on("read", "Read sign", 10, g.cfg.interactRange));
            return o;
        }
        return super.options(g, a);
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if (broken) return Result.no("It's broken.");
        if (form == Form.SIGN && action.equals("read")) {
            String why = reach(g, a, g.cfg.interactRange);
            if (why != null) return Result.no(why);
            if (a == g.player) g.chat.say(text.isEmpty() ? "The sign is blank." : "The sign reads: \"" + text + "\"");
            return Result.ok();
        }
        return super.perform(g, a, action, req);
    }

    @Override public String inspect(Rpg g) { return super.inspect(g) + String.format("%n  %s integrity=%.2f broken=%s body=%d", form, integrity(), broken, body); }

    // ------------------------------------------------------------------ saving: where it is, how it lies, what is gone
    @Override public Map<String, Object> save() {
        Map<String, Object> m = super.save();
        m.put("bx", x); m.put("by", y); m.put("bz", z); m.put("qw", qw); m.put("qx", qx); m.put("qy", qy); m.put("qz", qz);
        m.put("broken", broken);
        if (lost > 0) m.put("gone", gone());
        if (!text.isEmpty()) m.put("text", text);
        return m;
    }

    @Override public void load(Rpg g, Map<String, Object> m) {
        super.load(g, m);
        x = (float) astratto.util.Json.num(m, "bx", x); y = (float) astratto.util.Json.num(m, "by", y); z = (float) astratto.util.Json.num(m, "bz", z);
        qw = (float) astratto.util.Json.num(m, "qw", qw); qx = (float) astratto.util.Json.num(m, "qx", qx); qy = (float) astratto.util.Json.num(m, "qy", qy); qz = (float) astratto.util.Json.num(m, "qz", qz);
        broken = astratto.util.Json.bool(m, "broken", false);
        text = astratto.util.Json.str(m, "text", text);
        String gone = astratto.util.Json.str(m, "gone", "");
        if (!gone.isEmpty()) {
            for (String s : gone.split(",")) {
                try { int i = Integer.parseInt(s.trim()); if (i >= 0 && i < mat.length && mat[i] != AIR) { remove(i); } } catch (NumberFormatException ignored) { }
            }
        }
        if (broken) removed = true;
        if (g.breakables != null) g.breakables.respawn(this);
    }

    /** The voxels gone, as their indices. */
    private String gone() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < mat.length; i++) if (mat[i] == AIR && hp[i] < 0) { if (b.length() > 0) b.append(','); b.append(i); }
        return b.toString();
    }

    /** One voxel broken away (hp below zero marks it as once there). */
    void remove(int i) {
        if (mat[i] == AIR) return;
        mat[i] = AIR; hp[i] = -1; lost++;
        int sz = form.sz, sy = form.sy;
        int x = i / (sy * sz), y = (i / sz) % sy, z = i % sz;
        vox.set(x, y, z, 0);
        // the wood round a break shows fresh and pale where it split
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            if (!inside(nx, ny, nz)) continue;
            int j = index(nx, ny, nz);
            if (mat[j] == WOOD || mat[j] == END) vox.set(nx, ny, nz, Shapes.fresh(vox.get(nx, ny, nz)));
        }
        version++;
    }
}
