package rpg.render;

import rpg.animal.Species;

import astratto.bridge.AstrattoNative;
import astratto.bridge.NativeEngine;
import astratto.bridge.NativeRenderer;
import astratto.bridge.RenderCommands;
import astratto.bridge.Snapshots;
import astratto.math.Mat4;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.lwjgl.system.MemoryUtil;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.actor.Body;
import rpg.ai.Brain;
import rpg.ai.Npc;
import rpg.combat.CombatSystem;
import rpg.combat.Spells;
import rpg.interact.WorldObject;
import rpg.item.ItemDef;
import rpg.item.ItemDef.Family;
import rpg.item.ItemDef.Slot;
import rpg.item.Tier;
import rpg.objects.BuildSpot;
import rpg.objects.Container;
import rpg.objects.Door;
import rpg.objects.Fire;
import rpg.objects.Fixtures;
import rpg.objects.Furniture;
import rpg.objects.GroundItem;
import rpg.objects.Node;
import rpg.objects.Station;
import rpg.render.CharModel.Seg;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * Describes each frame to the engine's native renderer: the camera, the time of day, the world (the engine meshes and
 * lights it), lights, every character, ragdolls from the engine's snapshot, world objects, projectiles and particles.
 * The 2D layer goes on after.
 *
 * Characters are 32-pixel figures built from their 128 x 128 skins ({@link Skin}, {@link CharModel}) and posed each
 * frame by the {@link Rig}; the striking arm follows the same grip and blade combat makes contact with. Objects -
 * furniture, workstations, doors - are drawn here as their own models ({@link Props}), not as blocks.
 */
public final class Gfx implements AutoCloseable {
    private final NativeRenderer r;
    public final RenderCommands cmd = new RenderCommands(1 << 18);
    private final int box;
    private final java.util.Map<String, int[]> itemMeshes = new java.util.HashMap<>();
    private final Map<String, Integer> meshes = new HashMap<>();
    public final Mat4 view = new Mat4(), proj = new Mat4(), viewProj = new Mat4();
    private final Mat4 m = new Mat4();
    private final float[] m16 = new float[16];
    private final Map<Long, Rig.State> anim = new HashMap<>();
    private final Rig rig = new Rig();
    /** The review's twitch probe (Gallery): whose figure to sample, and its joints as last posed. */
    public long probeId = -1;
    public final float[] probe = new float[21];
    public boolean probeFresh;
    /** Each look-and-armor's figure meshes: [0] the base layer, [1] the outer, per segment. Least recently used goes first. */
    private final LinkedHashMap<Long, int[][]> figures = new LinkedHashMap<>(32, 0.75f, true);
    /** Figures being built on the worker, by key; and what each person was last drawn as (shown until a new one is ready). */
    private final java.util.Map<Long, java.util.concurrent.Future<CharModel>> building = new java.util.HashMap<>();
    private final java.util.Map<Long, int[][]> shown = new java.util.HashMap<>();
    private final java.util.concurrent.ExecutorService builder = java.util.concurrent.Executors.newSingleThreadExecutor(run -> {
        Thread t = new Thread(run, "figure-builder"); t.setDaemon(true); t.setPriority(Thread.NORM_PRIORITY - 1); return t;
    });
    private static final int MAX_FIGURES = 48;
    private final Map<Long, Float> doorSwing = new HashMap<>();
    private final List<float[]> lamps = new ArrayList<>();
    private double clock;
    public NativeRenderer.Stats last;
    private boolean first = true;
    public int width, height;

    public Gfx(NativeEngine engine, int w, int h) {
        r = NativeRenderer.create(engine, w, h, AstrattoNative.QUALITY_HIGH, 1f);
        if (r == null) throw new IllegalStateException("no native renderer: " + NativeRenderer.createError);
        ByteBuffer atlas = Blocks.atlasPixels(), noise = Blocks.noisePixels();
        int atlasTex, noiseTex;
        try {
            atlasTex = r.texture(AstrattoNative.TEX_ARRAY, Blocks.TEX, Blocks.TEX, Blocks.LAYERS,
                    AstrattoNative.TEX_NEAREST | AstrattoNative.TEX_MIPMAP | AstrattoNative.TEX_REPEAT | AstrattoNative.texMaxLevel(5), atlas);
            noiseTex = r.texture(AstrattoNative.TEX_2D, 256, 256, 1, AstrattoNative.TEX_MIPMAP | AstrattoNative.TEX_MIP_LINEAR | AstrattoNative.TEX_REPEAT, noise);
        } finally { MemoryUtil.memFree(atlas); MemoryUtil.memFree(noise); }
        if (!r.worldLook(atlasTex, noiseTex, Blocks.LAYERS, Blocks.renderDefs(), Blocks.renderShapes(), Blocks.STONE, Blocks.palette(), Blocks.layerMaterials(), DevMap.FLOOR, 0))
            throw new IllegalStateException("world look refused: " + r.lastError());
        box = r.mesh(Meshes.unitBox(), 36);
        if (box == 0) throw new IllegalStateException("mesh refused: " + r.lastError());
    }

    public NativeRenderer renderer() { return r; }

    private int mesh(String key, Supplier<Meshes.Boxes> make) {
        Integer id = meshes.get(key);
        if (id != null) return id;
        Meshes.Boxes b = make.get();
        int h = b.count() == 0 ? 0 : r.mesh(b.vertices(), b.count());
        meshes.put(key, h);
        return h;
    }

    /** A model's layers as meshes (made once per key). */
    private int[] layers(String key, Supplier<Props.Layer[]> make) {
        Integer n = meshes.get(key + "#n");
        if (n == null) {
            Props.Layer[] ls = make.get();
            for (int i = 0; i < ls.length; i++) { Meshes.Boxes b = ls[i].boxes(); meshes.put(key + "#" + i, b.count() == 0 ? 0 : r.mesh(b.vertices(), b.count())); }
            meshes.put(key + "#n", ls.length);
            layerInfo.put(key, ls);
            n = ls.length;
        }
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) ids[i] = meshes.get(key + "#" + i);
        return ids;
    }
    private final Map<String, Props.Layer[]> layerInfo = new HashMap<>();

    /** Draws a model's layers with the current matrix. */
    private void drawLayers(String key, Supplier<Props.Layer[]> make) {
        int[] ids = layers(key, make);
        Props.Layer[] info = layerInfo.get(key);
        for (int i = 0; i < ids.length; i++) put(ids[i], 0, 1, 1, 1, info[i].emissive(), 1, info[i].rough(), info[i].metal());
    }

    /** Finds the lamp blocks once (for night lights). */
    public void findLamps(rpg.world.Voxels vox) {
        lamps.clear();
        for (int x = 0; x < DevMap.SX; x++) for (int z = 0; z < DevMap.SZ; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 8; y++)
            if (vox.block(x, y, z) == Blocks.LAMP) lamps.add(new float[]{x + 0.5f, y - 0.4f, z + 0.5f});
    }

    /** Where the camera stands (for distance culling). */
    private float camX, camZ;

    // ------------------------------------------------------------------ the frame
    /** The vertical field of view in degrees (fov_desired). */
    public float fovDeg = 70;

    public void camera(float cx, float cy, float cz, float fx, float fy, float fz, int w, int h, float dt) {
        width = w; height = h;
        camX = cx; camZ = cz;
        float aspect = w / (float) Math.max(1, h), fov = (float) Math.toRadians(fovDeg);
        view.lookAt(cx, cy, cz, cx + fx, cy + fy, cz + fz, 0, 1, 0);
        proj.perspective(fov, aspect, 0.05f, 400f);
        viewProj.set(proj).mul(view);
        cmd.camera(view.m, proj.m, proj.m, cx, cy, cz, fx, fy, fz, fov, aspect, 0.05f, 400f, 160f, dt, first);
        first = false;
    }

    /**
     * Sun, sky and fog for the time of day (the clock's hour). The sun rises in the east, arcs south and sets in the
     * west; the light runs through a real twilight: blue-grey day haze, a warm horizon and golden light while the sun
     * is low, a cooling dusk, then a deep blue night under the moon (the renderer lights it opposite the sun). The
     * daylight share is the same curve the stealth system reads, so what looks dark is dark to the guards too.
     */
    public float environment(double hour, double time) {
        double ang = (hour - 6) / 12.0 * Math.PI;   // 6:00 sunrise, 18:00 sunset
        float sy = (float) Math.sin(ang), sx = (float) Math.cos(ang) * 0.85f, sz = 0.38f;
        float daylight = rpg.ai.Stealth.daylight(hour);
        // how near the sun is to the horizon (dawn and dusk), and how far below it (night)
        float low = (float) Math.exp(-(sy / 0.2f) * (sy / 0.2f));
        float night = clamp01(-sy / 0.35f);
        float dayK = clamp01(sy / 0.35f);
        // the horizon: day haze, a warm band round sunrise and sunset, deep blue at night
        float hr = 0.60f * dayK + 0.62f * low * (1 - night * 0.7f) + 0.05f * night, hg = 0.67f * dayK + 0.40f * low * (1 - night * 0.7f) + 0.07f * night,
              hb = 0.76f * dayK + 0.36f * low * (1 - night * 0.6f) + 0.15f * night;
        float lift = 1 - 0.55f * night;
        float fogR = clamp01(hr * lift) * 0.9f + 0.03f, fogG = clamp01(hg * lift) * 0.9f + 0.035f, fogB = clamp01(hb * lift) * 0.9f + 0.06f;
        // the light itself: golden while the sun is low, moonlit blue at night, white by day
        float tr = 1, tg = 1 - 0.10f * low * (1 - night), tb = 1 - 0.22f * low * (1 - night);
        tr -= 0.18f * night; tg -= 0.08f * night;
        cmd.env(sx, sy, sz, daylight, (float) time, 0, 0, 0, 0.1f, 0, fogR, fogG, fogB, 1f / 300f, 1e9f, 2e9f, 0, 0, tr, tg, tb, 0.004f + 0.006f * night, Float.NaN, 0, 0, 0.05f + 0.03f * low);
        return daylight;
    }

    private static float clamp01(float v) { return Math.max(0, Math.min(1, v)); }

    private final float[] smoke = new float[32 * 12];

    /**
     * Smoke in the air: a soft, billowing volume over every fire, lit from beneath by its glow, and the furnace's plume
     * above its chimney. The renderer marches the light through them, so a fire's smoke catches the sun and the lamps.
     */
    private void smoke(Rpg g) {
        int n = 0;
        for (WorldObject o : g.objects.all()) {
            if (!(o instanceof Fire f) || f.removed || n >= 30) continue;
            n = putSmoke(n, f.x, f.y + 1.3f, f.z, 0.95f, 0.55f, 0.30f, 0.28f, 0.26f, 0.12f, 0.7f, 0.95f, f.id);
            n = putSmoke(n, f.x + 0.15f, f.y + 2.7f, f.z - 0.1f, 1.4f, 0.22f, 0.36f, 0.35f, 0.34f, 0.0f, 0.9f, 0.9f, f.id + 7);
        }
        n = putSmoke(n, DevMap.FURNACE_AT[0] + 0.5f, DevMap.FLOOR + 3.4f, DevMap.FURNACE_AT[1] + 0.5f, 1.1f, 0.7f, 0.24f, 0.23f, 0.22f, 0.05f, 1.0f, 0.9f, 991);
        if (n > 0) cmd.smoke(smoke, n);
    }

    private int putSmoke(int n, float x, float y, float z, float radius, float density, float r, float gg, float b, float glow, float rise, float swirl, long seed) {
        if (n >= 32) return n;
        float[] v = {x, y, z, radius, density, r, gg, b, glow, rise, swirl, (seed % 97) / 97f};
        System.arraycopy(v, 0, smoke, n * 12, 12);
        return n + 1;
    }

    private String qualitySent = "";
    private float dynScale = 1f;
    private int level;
    private static final float[] LEVELS = {1f, 0.85f, 0.75f, 0.67f, 0.6f, 0.5f};
    private double overT, underT;
    private long lastScaleCheck;

    /** The render scale this frame: fixed (RPG_RENDER_SCALE, or autoScale off), or following the GPU's time. */
    private float scale(Rpg g) {
        float fixed = g.cfg.fixedScale();
        if (fixed > 0) return fixed;
        if (!g.cfg.autoScale) return g.cfg.renderScale;
        long now = System.nanoTime();
        float dt = lastScaleCheck == 0 ? 0 : Math.min(0.1f, (now - lastScaleCheck) / 1e9f);
        lastScaleCheck = now;
        if (last == null || last.gpuMs <= 0) return dynScale;
        float budget = 1000f / Math.max(30, g.cfg.targetFps);
        float gpu = last.gpuMs;
        if (gpu > budget * 0.85f) { overT += dt; underT = 0; }
        else if (gpu < budget * 0.58f) { underT += dt; overT = 0; }
        else { overT = 0; underT = 0; }
        // a few fixed levels: each change rebuilds the render targets (a stall of its own), so changes are rare - down a
        // level after half a second over the budget, up one only after four seconds with plenty of room
        // the floor: never below 720 lines rendered (at 1440p that is half; at 1080p two-thirds), nor the configured one
        float floor = Math.max(g.cfg.minScale, Math.min(1f, 720f / Math.max(1, lastH)));
        if (overT > 0.5f && level < LEVELS.length - 1 && LEVELS[level + 1] >= floor - 1e-3f) { level++; overT = 0; }
        else if (underT > 4f && level > 0 && LEVELS[level - 1] <= g.cfg.renderScale + 1e-3f) { level--; underT = 0; }
        while (level < LEVELS.length - 1 && LEVELS[level] > g.cfg.renderScale + 1e-3f) level++;
        dynScale = LEVELS[level];
        return dynScale;
    }

    /** The render scale in use (for the interface). */
    public float renderScaleNow() { return dynScale; }

    /**
     * The renderer's quality: the preset, the passes (the lens's when motion blur or depth of field is chosen) and the
     * render scale (below 1 the TAA upscales to the window), sent when any of them changes; and the lens described.
     */
    private void lens(Rpg g) {
        boolean on = g.cfg.motionBlur || g.cfg.depthOfField;
        int passes = AstrattoNative.PASS_GTAO | AstrattoNative.PASS_SSR | AstrattoNative.PASS_VOLUMETRICS | AstrattoNative.PASS_TAA | AstrattoNative.PASS_BLOOM
                | (g.cfg.motionBlur ? AstrattoNative.PASS_MOTION_BLUR : 0) | (g.cfg.depthOfField ? AstrattoNative.PASS_DEPTH_OF_FIELD : 0);
        float scale = scale(g);
        String key = g.cfg.quality + "/" + passes + "/" + scale;
        if (!key.equals(qualitySent)) { cmd.quality(g.cfg.quality, passes, scale); qualitySent = key; }
        if (on) cmd.lens(g.cfg.shutter, 0, g.cfg.aperture, 12);
    }

    /** The game being drawn this frame (for parts drawn from its physics). */
    private Rpg sceneGame;

    public void scene(Rpg g, boolean firstPerson, float dt, double hour) {
        sceneGame = g;
        clock = g.time;
        gore = g.gore;
        cmd.world(g.game.world());
        float daylight = environment(hour, g.time);
        // lights: lamps by night, fires, the furnace, spells in flight
        if (daylight < 0.6f) for (float[] l : lamps) cmd.pointLight(l[0], l[1], l[2], 9, 5f, 4.2f, 2.8f, 0);
        for (WorldObject o : g.objects.all()) if (o instanceof Fire f) {
            float fl = 0.85f + 0.15f * (float) Math.sin(g.time * 17 + f.id);
            cmd.pointLight(f.x, f.y + 0.6f, f.z, 7, 4.5f * fl, 2.2f * fl, 0.6f * fl, 0);
        }
        cmd.pointLight(DevMap.FURNACE_AT[0] + 0.5f, DevMap.FLOOR + 0.4f, DevMap.FURNACE_AT[1] + 1.0f, 4, 3f, 1.4f, 0.4f, 0);
        smoke(g);
        lens(g);
        for (CombatSystem.Projectile p : g.combat.projectiles) if (p.spell != null) cmd.pointLight(p.x, p.y, p.z, 4, p.spell == Spells.Spell.FIRE_BOLT ? 5f : 1.5f, 2.5f, p.spell == Spells.Spell.FIRE_BOLT ? 0.8f : 4.5f, 0);
        // characters
        Actor p = g.player;
        if (p.alive) {
            human(p, p.look, dt, firstPerson, null);
            cmd.shadowBody(p.x, p.y, p.z, p.radius, p.height);
        } else if (g.death.ragdoll != 0) {
            // the player's own body, fallen (gamefixes: the death is watched), until it becomes the grave
            Snapshots.Ragdoll rd = g.lastBodies.ragdoll(g.death.ragdoll);
            if (rd != null) ragdoll(rd, p);
        }
        for (Npc n : g.npcs) {
            if (n.ragdoll != 0) {
                // a body in Sydney's hands - reeling from a blow, or dead - is drawn as the engine has it
                Snapshots.Ragdoll rd = g.lastBodies.ragdoll(n.ragdoll);
                if (rd != null) { ragdoll(rd, n); continue; }
            }
            if (n.alive && n.loaded) human(n, n.look, dt, false, sleeping(g, n));
        }
        for (Actor d : g.dummies) dummy(d, g.time);
        if (g.animals != null) for (rpg.animal.Animal an : g.animals.all()) if (an.loaded && (an.alive || an.carcass() && !an.butchered) && an.distTo(camX, camZ) < 70) animal(an, dt, g.time);
        // objects
        for (WorldObject o : g.objects.all()) object(g, o, dt);
        if (g.interact.focus != null && g.interact.focus != (Object) g.player) {
            var f = g.interact.focus;
            float bob = (float) Math.sin(g.time * 4) * 0.05f;
            m.identity().translate(f.ax(), f.ay() + f.focusRadius() + 0.35f + bob, f.az()).rotateY((float) g.time * 2).rotateZ(0.785f).rotateX(0.615f).scale(0.12f, 0.12f, 0.12f);
            put(box, 0, 1f, 0.85f, 0.2f, 1.4f, 1, 0.4f, 0);
        }
        for (CombatSystem.Projectile pr : g.combat.projectiles) projectile(pr);
        // clutches being sat: a ring of straw and the eggs in it
        if (g.animals != null) for (var ne : g.animals.nests) {
            if (ne.done) continue;
            m.identity().translate(ne.x, DevMap.FLOOR + 0.04f, ne.z).scale(0.5f, 0.08f, 0.5f); put(box, 0xC8A860, 1, 1f, 0);
            for (int i = 0; i < ne.eggs; i++) { double t = i * 2.4; m.identity().translate(ne.x + (float) Math.cos(t) * 0.12f, DevMap.FLOOR + 0.12f, ne.z + (float) Math.sin(t) * 0.12f).scale(0.07f, 0.09f, 0.07f); put(box, ne.species == Species.DUCK ? 0xD8E0D0 : 0xE8D8BC, 1, 0.6f, 0); }
        }
        // glass shards: each its own little prism, where its rigid body lies, drawn see-through
        for (rpg.objects.Glass.Shard sh : g.glass.shards) {
            Snapshots.Rigid r = g.lastRigids.body(sh.body);
            if (r == null) continue;
            if (sh.gpu == 0) sh.gpu = upload(sh.mesh);
            r.matrix(m16);
            cmd.model(sh.gpu, AstrattoNative.MODEL_FORWARD, m16, 0.78f, 0.9f, 0.95f, 0.45f, 0, 1, 0.05f, 0);
        }
        if (g.gore != null) { limbs(g); stains(g); }
        int n = g.fx.pack();
        if (n > 0) cmd.particles(g.fx.data(), n);
    }

    private int lastH = 1080;

    public NativeRenderer.Stats frame(int w, int h) {
        lastH = h;
        last = r.frame(cmd, w, h);
        return last;
    }

    // ------------------------------------------------------------------ drawing helpers
    private float[] right(float yaw) { return new float[]{(float) Math.cos(yaw), 0, (float) -Math.sin(yaw)}; }
    private float[] fwd(float yaw) { return new float[]{(float) -Math.sin(yaw), 0, (float) -Math.cos(yaw)}; }

    private static float[] add(float[] a, float[] b, float s) { return new float[]{a[0] + b[0] * s, a[1] + b[1] * s, a[2] + b[2] * s}; }
    private static float[] norm(float[] a) { float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return l < 1e-6f ? new float[]{0, -1, 0} : new float[]{a[0] / l, a[1] / l, a[2] / l}; }
    private static float[] cross(float[] a, float[] b) { return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    /** A box from point a to point b, w wide (across the body) and d deep. */
    private void limb(float[] a, float[] b, float w, float d, float yaw, int rgb, float shade, float rough, float metal) {
        float[] y = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        float len = (float) Math.sqrt(y[0] * y[0] + y[1] * y[1] + y[2] * y[2]);
        if (len < 1e-4f) return;
        y = new float[]{y[0] / len, y[1] / len, y[2] / len};
        float[] rr = right(yaw);
        float dot = rr[0] * y[0] + rr[1] * y[1] + rr[2] * y[2];
        float[] x = {rr[0] - y[0] * dot, rr[1] - y[1] * dot, rr[2] - y[2] * dot};
        if (Math.sqrt(x[0] * x[0] + x[1] * x[1] + x[2] * x[2]) < 0.1f) x = fwd(yaw);
        x = norm(x);
        float[] z = cross(x, y);
        float[] mm = m.m;
        mm[0] = x[0] * w; mm[1] = x[1] * w; mm[2] = x[2] * w; mm[3] = 0;
        mm[4] = y[0] * len; mm[5] = y[1] * len; mm[6] = y[2] * len; mm[7] = 0;
        mm[8] = z[0] * d; mm[9] = z[1] * d; mm[10] = z[2] * d; mm[11] = 0;
        mm[12] = (a[0] + b[0]) / 2; mm[13] = (a[1] + b[1]) / 2; mm[14] = (a[2] + b[2]) / 2; mm[15] = 1;
        put(box, rgb, shade, rough, metal);
    }

    private void put(int mesh, int rgb, float shade, float rough, float metal) {
        put(mesh, 0, ((rgb >> 16) & 255) / 255f * shade, ((rgb >> 8) & 255) / 255f * shade, (rgb & 255) / 255f * shade, 0, 1, rough, metal);
    }

    private void put(int mesh, int flags, float r, float g, float b, float emissive, float light, float rough, float metal) {
        if (mesh == 0) return;
        System.arraycopy(m.m, 0, m16, 0, 16);
        cmd.model(mesh, flags, m16, r, g, b, 1, emissive, light, rough, metal);
    }

    /** A box in an oriented frame: centre, axes, sizes. */
    private void frameBox(float[] c, float[] x, float[] y, float[] z, float sx, float sy, float sz, int mesh, int rgb, float shade, float rough, float metal) {
        float[] mm = m.m;
        mm[0] = x[0] * sx; mm[1] = x[1] * sx; mm[2] = x[2] * sx; mm[3] = 0;
        mm[4] = y[0] * sy; mm[5] = y[1] * sy; mm[6] = y[2] * sy; mm[7] = 0;
        mm[8] = z[0] * sz; mm[9] = z[1] * sz; mm[10] = z[2] * sz; mm[11] = 0;
        mm[12] = c[0]; mm[13] = c[1]; mm[14] = c[2]; mm[15] = 1;
        put(mesh, rgb, shade, rough, metal);
    }

    /** A held item: its own mesh, from the grip along a direction, flat across the body's right (or facing forward). */
    private void held(ItemDef d, float[] grip, float[] dir, float yaw, boolean flatToFace) {
        if (d == null) return;
        float[] y = norm(dir);
        float[] ref = flatToFace ? fwd(yaw) : right(yaw);
        float dot = ref[0] * y[0] + ref[1] * y[1] + ref[2] * y[2];
        float[] x = norm(new float[]{ref[0] - y[0] * dot, ref[1] - y[1] * dot, ref[2] - y[2] * dot});
        if (Float.isNaN(x[0])) x = right(yaw);
        float[] z = cross(x, y);
        if (d.family == Family.ARROW) {
            // a nocked arrow is one arrow, not the bundle the inventory holds
            int mesh = mesh("w:" + d.family + ":" + d.reach, () -> Meshes.weapon(d.family, d.reach));
            frameBox(grip, x, y, z, 1, 1, 1, mesh, d.color, 1, 0.5f, 0.3f);
            return;
        }
        frameBox(grip, x, y, z, 1, 1, 1, 0, 0, 1, 0, 0);
        drawItem(d, 0);
    }

    /** An item's own voxel meshes (metal, then the rest), made once. */
    private int[] itemMesh(ItemDef d) {
        int[] mm = itemMeshes.get(d.id);
        if (mm != null) return mm;
        float[][] v = VoxMesh.build(ItemModels.of(d));
        mm = new int[]{upload(v[0]), upload(v[1])};
        itemMeshes.put(d.id, mm);
        return mm;
    }

    /** Draws an item's model at the current matrix: its metal reflects as metal, the rest does not. */
    private void drawItem(ItemDef d, int flags) {
        int[] mm = itemMesh(d);
        put(mm[0], flags, 1, 1, 1, 0, 1, 0.3f, 0.9f);
        put(mm[1], flags, 1, 1, 1, 0, 1, 0.82f, 0);
    }

    /** A worn container on the torso (the backpack on the back, the pouch at the hip). */
    private void torsoItem(float[] torsoLocal, ItemDef d, float x, float y, float z, float turn, boolean shadowOnly) {
        System.arraycopy(torsoLocal, 0, seg.m, 0, 16);
        m.set(root).mul(seg).translate(x, y, z).rotateY(turn).scale(1 / rpg.actor.Body.PX, 1 / rpg.actor.Body.PX, 1 / rpg.actor.Body.PX);
        drawItem(d, shadowOnly ? AstrattoNative.MODEL_SHADOW_ONLY : 0);
    }

    // ------------------------------------------------------------------ characters
    /**
     * The figure's meshes for a look and what is worn over it (built from its skin once, then kept). 2.5: painting a
     * skin and building its meshes took 10 - 75 ms - a hitch whenever someone new came into view or changed what they
     * wore. They are built on a worker thread now (only the upload is the render thread's); until a new figure is ready
     * the person is drawn as they last were. A figure never seen before at all is built at once (preload() builds the
     * town's while the game loads).
     */
    private int[][] figure(Actor a, Appearance look) {
        long key = figureKey(a, look);
        int[][] f = figures.get(key);
        if (f != null) { shown.put(a.id, f); return f; }
        java.util.concurrent.Future<CharModel> job = building.get(key);
        if (job == null) {
            Skin.Armor armor = armorOf(a);
            Appearance copy = look.copy();
            var blood = a.blood.any() ? a.blood.snapshot() : null;
            job = builder.submit(() -> CharModel.build(Skin.paint(copy, armor, blood)));
            building.put(key, job);
        }
        int[][] old = shown.get(a.id);
        if (!job.isDone() && old != null) return old;
        CharModel cm;
        try { cm = job.get(); }
        catch (InterruptedException | java.util.concurrent.ExecutionException e) { building.remove(key); throw new IllegalStateException("figure for " + a.name, e); }
        building.remove(key);
        f = new int[2][CharModel.SEGS];
        for (int i = 0; i < CharModel.SEGS; i++) { f[0][i] = upload(cm.base[i]); f[1][i] = upload(cm.outer[i]); }
        figures.put(key, f);
        if (figures.size() > MAX_FIGURES) {
            var eldest = figures.entrySet().iterator().next();
            if (!shown.containsValue(eldest.getValue())) {
                for (int[] layer : eldest.getValue()) for (int id : layer) if (id != 0) r.release(AstrattoNative.RES_MESH, id);
                figures.remove(eldest.getKey());
            }
        }
        shown.put(a.id, f);
        return f;
    }

    /** Whether the figure for an actor as they look now has been built (the gallery waits on it; play shows the old one meanwhile). */
    public boolean figureReady(Actor a) { return a.look != null && figures.containsKey(figureKey(a, a.look)); }

    private static Skin.Armor armorOf(Actor a) { return new Skin.Armor(a.eq.def(Slot.HEAD), a.eq.def(Slot.BODY), a.eq.def(Slot.LEGS), a.eq.def(Slot.FEET), a.eq.def(Slot.HANDS)); }

    /** A figure's identity without building a string a frame: the look's numbers and the worn pieces, hashed to 64 bits. */
    private static long figureKey(Actor a, Appearance look) {
        long h = 0x9E3779B97F4A7C15L;
        for (int v : new int[]{look.female ? 1 : 0, look.bodyType, look.skin, look.hair, look.hairColor, look.beard, look.beardColor, look.top, look.topColor, look.bottom, look.bottomColor, look.shoes, look.shoesColor})
            h = (h ^ v) * 0x100000001B3L + 0x632BE59BD9B4E019L;
        for (Slot s : ARMOR_SLOTS) { ItemDef d = a.eq.def(s); h = (h ^ (d == null ? 0 : d.id.hashCode())) * 0x100000001B3L + 31; }
        // blood is this body's own: a bloodied figure is nobody else's (a clean one is shared by everyone who looks alike)
        if (a.blood.any()) h = (h ^ a.id * 0x9E3779B97F4A7C15L ^ (long) a.blood.version << 40) * 0x100000001B3L + 17;
        return h;
    }

    private static final Slot[] ARMOR_SLOTS = {Slot.HEAD, Slot.BODY, Slot.LEGS, Slot.FEET, Slot.HANDS};

    /** Builds everyone's figure ahead (the town's people as they are now), so none is built mid-play. */
    public void preload(Iterable<? extends Actor> actors) {
        for (Actor a : actors) if (a.look != null) figure(a, a.look);
    }

    private int upload(float[] v) { int n = v.length / 10; return n == 0 ? 0 : r.mesh(v, n); }

    // ------------------------------------------------------------------ animals
    private final java.util.Map<ItemModels.Model, int[]> partMeshes = new java.util.IdentityHashMap<>();
    private final Mat4 body = new Mat4();

    private int[] partMesh(ItemModels.Model p) {
        int[] mm = partMeshes.get(p);
        if (mm == null) { float[][] v = VoxMesh.build(p); mm = new int[]{upload(v[0]), upload(v[1])}; partMeshes.put(p, mm); }
        return mm;
    }

    /** Which of the animal being drawn's parts are gone (set per animal by animal()). */
    private boolean[] animalLost = new boolean[rpg.combat.Gore.Part.values().length];

    /** An animal's part, unless it has been taken off - then the raw stump where it joined. */
    private void limb(rpg.combat.Gore.Part which, ItemModels.Model p, float vs, float jx, float jy, float jz, float rx, float ry, float rz) {
        if (p == null) return;
        if (!animalLost[which.ordinal()]) { part(p, vs, jx, jy, jz, rx, ry, rz); return; }
        var vm = p.vox();
        float w = Math.max(1.2f, Math.min(vm.sx, vm.sz) * 0.9f) * vs;
        m.set(body).translate(jx * vs, jy * vs, jz * vs).scale(w, Math.max(0.03f, vs * 0.8f), w);
        put(box, 0x6E0E0E, 1, 0.55f, 0);
    }

    /** An animal whose body physics holds: each figure part drawn on its rigid body (false: none there yet - draw it posed). */
    private boolean ragdollAnimal(rpg.animal.Animal a, float vs) {
        var g0 = sceneGame;
        if (g0 == null) return false;
        animalLost = new boolean[rpg.combat.Gore.Part.values().length];
        boolean any = false;
        for (var p : a.ragdoll.pieces) {
            Snapshots.Rigid r = g0.lastRigids.body(p.body());
            if (r == null) continue;
            r.matrix(m16);
            System.arraycopy(m16, 0, body.m, 0, 16);
            var vm = p.model().vox();
            part(p.model(), vs, -(vm.sx / 2f - p.model().gx()), -(vm.sy / 2f - p.model().gy()), -(vm.sz / 2f - p.model().gz()), 0, 0, 0);
            any = true;
        }
        return any;
    }

    /** Draws one part at the body's frame: moved to its joint (figure voxels), turned, scaled from mesh units to the figure's. */
    private void part(ItemModels.Model p, float vs, float jx, float jy, float jz, float rx, float ry, float rz) {
        if (p == null) return;
        float k = vs / ItemModels.VOX;
        m.set(body).translate(jx * vs, jy * vs, jz * vs).rotateY(ry).rotateX(rx).rotateZ(rz).scale(k, k, k);
        int[] mm = partMesh(p);
        put(mm[1], 0, 1, 1, 1, 0, 1, 0.9f, 0);
    }

    /**
     * An animal, posed from what it is doing: its legs swing in its gait (a walk's diagonal pairs, a gallop's bound, a
     * rabbit's hop, a bird's quick steps and bobbing head), its head goes down to graze or drink and comes up to watch a
     * threat, a bird's wings beat when it runs, a bear or a horse rears to warn, a resting animal lies down, a dead one
     * lies on its side. A baby is the same figure at its stage's scale.
     */
    private void animal(rpg.animal.Animal a, float dt, double time) {
        var sp = a.species;
        var f = AnimalModels.of(sp, a.variant);
        float vs = AnimalModels.PX * a.scale();
        if (a.ragdoll != null && ragdollAnimal(a, vs)) return;
        boolean dead = !a.alive;
        float speed = dead ? 0 : (float) Math.hypot(a.vx, a.vz);
        // the gait: one cycle per two strides, its swing growing with speed
        float legM = Math.max(2, f.legH) * vs;
        a.gait += speed / (legM * 2.4f) * (float) Math.PI * 2 * dt * (f.bird ? 1.6f : 1);
        boolean running = speed > sp.walk * 1.5f * (a.baby ? 0.6f : 1);
        float wantAmp = speed < 0.06f ? 0 : Math.min(running ? 0.62f : 0.42f, speed / (sp.walk * (a.baby ? 0.6f : 1)) * 0.34f);
        a.amp += (wantAmp - a.amp) * Math.min(1, dt * 8);
        float lieWant = dead ? 1 : a.atRest && speed < 0.2f ? 1 : 0;
        a.lieT += (lieWant - a.lieT) * Math.min(1, dt * (dead ? 6 : 2.2f));
        float lie = a.lieT, amp = a.amp, ph = a.gait;
        // the head: down to the grass or the water, up and round toward a threat, otherwise a slow look about
        float headWant = a.headDown ? (sp == Species.HORSE || sp == Species.COW ? -1.15f : f.bird ? -0.9f : -0.8f) : a.faceThreat ? 0.18f : 0.05f * (float) Math.sin(time * 0.7 + a.id);
        float yawWant = 0;
        if (a.faceThreat) yawWant = Math.max(-0.9f, Math.min(0.9f, Actor.wrap((float) Math.atan2(-(a.threatX - a.x), -(a.threatZ - a.z)) - a.yaw)));
        else if (!a.headDown && speed < 0.1f) yawWant = 0.35f * (float) Math.sin(time * 0.31 + a.id * 1.7);
        a.headPitch += (headWant - a.headPitch) * Math.min(1, dt * 5);
        a.headYaw += (yawWant - a.headYaw) * Math.min(1, dt * 4);
        // a strike: crouch in the wind-up, lunge through the active window
        float lunge = 0, crouch = 0;
        if (a.cs.phase == Actor.Combat.Phase.WINDUP) crouch = Math.min(1, a.cs.t / Math.max(0.05f, a.cs.windup));
        else if (a.cs.phase == Actor.Combat.Phase.ACTIVE) lunge = (float) Math.sin(Math.PI * Math.min(1, a.cs.t / Math.max(0.05f, a.cs.active)));
        else if (a.cs.phase == Actor.Combat.Phase.RECOVER) lunge = Math.max(0, 1 - a.cs.t / Math.max(0.05f, a.cs.recover)) * 0.4f;
        float rear = a.rearing ? 0.5f + 0.5f * (float) Math.sin(Math.min(1, (float) (time - a.goalSince)) * Math.PI / 2) : 0;
        // the body's frame
        float bob = f.hopper ? Math.max(0, (float) Math.sin(ph)) * 3.2f * Math.min(1, amp * 2.5f) : Math.abs((float) Math.sin(ph)) * 0.6f * amp;
        // a straight leg swung through an arc is shorter at its ends: the body rides down to keep the feet on the ground
        float reachDrop = f.bird || f.hopper ? 0 : f.legH * (1 - (float) Math.cos(amp * 0.95f)) * 0.8f;
        float standY = f.bodyY - crouch * 1.2f + bob - reachDrop;
        float lyingY = dead ? f.bw * 0.5f : f.bird ? f.bh * 0.45f : f.bh * 0.5f;
        float y = standY + (lyingY - standY) * lie;
        float pitch = (f.bird ? 0.05f * (float) Math.sin(ph * 2) * amp : running ? 0.08f * (float) Math.sin(ph) : 0) - crouch * 0.08f + lunge * 0.12f;
        if (f.hopper && amp > 0.05f) pitch += 0.25f * (float) Math.sin(ph) * Math.min(1, amp * 3);
        body.identity().translate(a.x, a.y, a.z).rotateY(a.yaw);
        if (rear > 0) {
            // up on the hind legs: the body turns about the haunches
            body.translate(0, f.bodyY * vs, f.legB[2] * vs).rotateX(rear * (sp == Species.BROWN_BEAR ? 1.05f : 0.7f)).translate(0, (y - f.bodyY) * vs, -f.legB[2] * vs);
        } else body.translate(0, y * vs, -lunge * sp.reach * 0.6f);
        body.rotateX(pitch);
        if (dead) body.rotateZ(1.45f * lie);
        part(f.body, vs, 0, 0, 0, 0, 0, 0);
        // a part taken off (Gore) is not drawn: a raw stump where it joined
        var wd = gore == null ? null : gore.wounds.get(a.id);
        boolean[] lost = new boolean[rpg.combat.Gore.Part.values().length];
        if (wd != null && wd.any()) for (var gp : rpg.combat.Gore.Part.values()) lost[gp.ordinal()] = wd.lost(gp);
        animalLost = lost;
        // legs (a dead animal's stiff and out; a resting one's folded under it)
        float fold = lie * (dead ? 0 : 1);
        if (f.bird) {
            float stepA = (float) Math.sin(ph) * 0.75f * amp;
            float hide = fold > 0.6f ? 0 : 1;
            if (hide > 0) for (int side = -1; side <= 1; side += 2) limb(side < 0 ? rpg.combat.Gore.Part.LEG_L : rpg.combat.Gore.Part.LEG_R, f.legFront, vs, f.legF[0] * side * 0.6f, f.legF[1], f.legF[2], stepA * side, 0, 0);
            // wings: tucked, beating when it runs or flees
            boolean flapping = running || a.goal == rpg.animal.Animal.Goal.FLEE && speed > 0.5f;
            a.flap += ((flapping ? 1 : 0) - a.flap) * Math.min(1, dt * 6);
            float beat = a.flap * (0.25f + 0.85f * Math.abs((float) Math.sin(time * 21 + a.id)));
            limb(rpg.combat.Gore.Part.ARM_R, f.wing, vs, f.wingAt[0], f.wingAt[1], f.wingAt[2], 0, 0, beat);
            limb(rpg.combat.Gore.Part.ARM_L, f.wing, vs, -f.wingAt[0], f.wingAt[1], f.wingAt[2], 0, 0, -beat);
            // the head bobs with each step (a chicken's peck-walk) and dips to feed
            float bobZ = sp == Species.CHICKEN ? (float) Math.sin(ph * 2) * 0.9f * Math.min(1, amp * 3) : 0;
            float peck = a.headDown ? 0.4f * Math.max(0, (float) Math.sin(time * 6 + a.id)) : 0;
            limb(rpg.combat.Gore.Part.HEAD, f.head, vs, f.neck[0], f.neck[1] - lie * 1.2f, f.neck[2] + bobZ, a.headPitch - peck, a.headYaw, 0);
        } else {
            float swing = 0.95f * amp;   // amp is already capped (0.42 walk, 0.62 run)
            float fl, fr, bl, br;
            if (f.hopper) {
                float push = Math.max(0, (float) Math.sin(ph)) * Math.min(1, amp * 3);
                fl = fr = 0.5f * push; bl = br = -1.0f * push;
            } else if (running) {
                fl = (float) Math.sin(ph) * swing; fr = (float) Math.sin(ph + 0.5f) * swing;
                bl = (float) Math.sin(ph + Math.PI) * swing; br = (float) Math.sin(ph + Math.PI + 0.5f) * swing;
            } else {
                fl = (float) Math.sin(ph) * swing; br = fl;
                fr = (float) Math.sin(ph + Math.PI) * swing; bl = fr;
            }
            if (dead) { fl = fr = 0.25f; bl = br = -0.25f; }
            fl = fl * (1 - fold) + 1.45f * fold; fr = fr * (1 - fold) + 1.45f * fold;
            bl = bl * (1 - fold) - 1.35f * fold; br = br * (1 - fold) - 1.35f * fold;
            if (rear > 0) { fl += rear * 0.9f; fr += rear * 0.7f; bl -= rear * 0.6f; br -= rear * 0.6f; }
            // a lame animal carries the leg it lost the use of: the other three take the step
            limb(rpg.combat.Gore.Part.ARM_L, f.legFront, vs, -f.legF[0], f.legF[1], f.legF[2], fl, 0, 0);
            limb(rpg.combat.Gore.Part.ARM_R, f.legFront, vs, f.legF[0], f.legF[1], f.legF[2], fr, 0, 0);
            limb(rpg.combat.Gore.Part.LEG_L, f.legBack, vs, -f.legB[0], f.legB[1], f.legB[2], bl, 0, 0);
            limb(rpg.combat.Gore.Part.LEG_R, f.legBack, vs, f.legB[0], f.legB[1], f.legB[2], br, 0, 0);
            float headP = a.headPitch + (rear > 0 ? -0.4f * rear : 0) - lie * (dead ? 0.1f : 0.25f) + (float) Math.sin(ph * 2) * 0.05f * amp;
            limb(rpg.combat.Gore.Part.HEAD, f.head, vs, f.neck[0], f.neck[1], f.neck[2], headP, a.headYaw, 0);
            // the tail: a dog's wag, a cow's switch, a cat's slow curl, a fox's brush streaming when it runs
            float wag = 0, droop = 0.35f;
            switch (sp) {
                case DOG -> { boolean happy = a.owned() && (a.goal == rpg.animal.Animal.Goal.SEEK || time - a.lastSound < 3) || a.goal == rpg.animal.Animal.Goal.FOLLOW; wag = (happy ? 0.7f : 0.15f) * (float) Math.sin(time * (happy ? 16 : 4)); droop = a.goal == rpg.animal.Animal.Goal.FLEE ? 1.1f : -0.5f; }
                case COW, HORSE -> { wag = 0.25f * (float) Math.sin(time * 1.3 + a.id); droop = sp == Species.HORSE ? 0.9f - amp * 0.5f : 1.2f; }
                case CAT, MOUNTAIN_LION -> { wag = 0.35f * (float) Math.sin(time * 0.9 + a.id); droop = sp == Species.CAT ? -0.6f : 0.8f; }
                case FOX, WOLF, COYOTE -> { wag = 0.12f * (float) Math.sin(time * 2 + a.id); droop = running ? 0.15f : 0.75f; }
                case PIG -> { wag = 0.4f * (float) Math.sin(time * 5 + a.id); droop = 0; }
                default -> { }
            }
            limb(rpg.combat.Gore.Part.TAIL, f.tail, vs, f.tailAt[0], f.tailAt[1], f.tailAt[2], droop * (1 - lie * 0.5f), wag, 0);
        }
        if (!dead && lie < 0.5f) cmd.shadowBody(a.x, a.y, a.z, a.radius, a.height * (1 - lie * 0.5f));
    }

    private static boolean metal(ItemDef d) { return d != null && d.tier != null && (d.tier.metal() || d.tier == Tier.CHAINMAIL); }

    /** The outer layer's material for a segment: whatever armor covers it. */
    private static ItemDef cover(Actor a, Seg s) {
        return switch (s) {
            case HEAD -> a.eq.def(Slot.HEAD);
            case TORSO, R_ARM_UP, L_ARM_UP -> a.eq.def(Slot.BODY);
            case R_ARM_LO, L_ARM_LO -> a.eq.def(Slot.HANDS) != null ? a.eq.def(Slot.HANDS) : a.eq.def(Slot.BODY);
            case R_LEG_UP, L_LEG_UP -> a.eq.def(Slot.LEGS);
            default -> a.eq.def(Slot.FEET) != null ? a.eq.def(Slot.FEET) : a.eq.def(Slot.LEGS);
        };
    }

    /** Where a sleeping NPC lies: the bed under their home, if they are in it; else null. */
    private Furniture sleeping(Rpg g, Npc n) {
        if (n.goal != Brain.Goal.SLEEP || Math.hypot(n.x - n.homeX, n.z - n.homeZ) > 1.6) return null;
        Furniture f = g.objects.furnitureAt((int) Math.floor(n.homeX), DevMap.FLOOR, (int) Math.floor(n.homeZ));
        return f != null && f.kind == Furniture.Kind.BED && f.owner == n.id ? f : null;
    }

    private final Mat4 root = new Mat4(), seg = new Mat4();

    /**
     * Draws a human: the figure posed by the rig at the actor (or lying in a bed), its hair and facial hair, skirt or
     * cape, and what its hands hold. In first person the whole body is drawn but the head (which still throws its
     * shadow): looking down, the player sees their own chest, legs and feet carrying them; and the eyes the rig posed
     * are handed to the camera.
     */
    public void human(Actor a, Appearance look, float dt, boolean firstPerson, Furniture bed) {
        Body b = Body.of(look);
        if (a.poseReset) { anim.remove(a.id); a.poseReset = false; }
        Rig.State st = anim.computeIfAbsent(a.id, k -> new Rig.State());
        rig.firstPerson = firstPerson;
        rig.pose(a, st, dt, clock, bed != null);
        if (a.id == probeId) {
            // the review's twitch probe: knees, ankles and hands in figure space (pixels), and the feet in the world
            int k = 0;
            for (Seg s : new Seg[]{Seg.R_LEG_LO, Seg.L_LEG_LO, Seg.R_ARM_LO, Seg.L_ARM_LO}) { float[] m = rig.seg[s.ordinal()]; probe[k++] = m[12]; probe[k++] = m[13]; probe[k++] = m[14]; }
            for (int h = 0; h < 2; h++) { probe[k++] = rig.hand[h][0]; probe[k++] = rig.hand[h][1]; probe[k++] = rig.hand[h][2]; }
            probe[18] = rig.pelvis[1]; probe[19] = rig.debugA; probe[20] = rig.debugB;
            probeFresh = true;
        }
        int[][] fig = figure(a, look);
        float shade = 1 + a.cs.flash * 0.8f;
        if (bed != null) {   // lying on the mattress, face up, head at the bed's head
            float yaw = bed.yaw(), len = bed.facing % 2 == 0 ? bed.depth() : bed.width();
            float fx = (float) -Math.sin(yaw), fz = (float) -Math.cos(yaw);
            root.identity().translate(bed.x + fx * (len / 2 - 0.08f), DevMap.FLOOR + 0.52f + b.torso[2] * Body.PX / 2, bed.z + fz * (len / 2 - 0.08f))
                    .rotateY(yaw).rotateX((float) Math.PI / 2).scale(Body.PX, Body.PX, Body.PX);
        } else root.identity().translate(a.x, a.y, a.z).rotateY(a.yaw).scale(Body.PX, Body.PX, Body.PX);
        if (bed == null) keepPose(a, b);
        if (a instanceof rpg.actor.Player pl && bed == null) {
            float[] e = world(rig.eye);
            pl.headX = e[0]; pl.headY = e[1]; pl.headZ = e[2]; pl.headValid = true;
        }
        boolean[] gone = goneSegs(a);
        for (Seg s : Seg.values()) {
            int flags = firstPerson && s == Seg.HEAD ? AstrattoNative.MODEL_SHADOW_ONLY : 0;
            segMatrix(rig.seg[s.ordinal()]);
            if (gone[s.ordinal()]) { if (isRoot(s)) stump(); continue; }
            put(fig[0][s.ordinal()], flags, shade, shade, shade, 0, 1, 0.85f, 0);
            ItemDef c = cover(a, s);
            boolean met = metal(c);
            put(fig[1][s.ordinal()], flags, shade, shade, shade, 0, 1, met ? 0.42f : 0.85f, met ? 0.65f : 0);
        }
        {
            // hair and facial hair: the head-unit meshes, on the head (8 pixels a side, its middle 4 up from the neck);
            // long hair hangs from the neck down the back and over the shoulders, on the torso
            float[] hm = rig.seg[Seg.HEAD.ordinal()];
            int hairRgb = Appearance.hairColor(look.hairColor);
            boolean headless = gone[Seg.HEAD.ordinal()];
            if (!headless) {
                System.arraycopy(hm, 0, seg.m, 0, 16); headBase.set(root).mul(seg);
                System.arraycopy(rig.seg[Seg.TORSO.ordinal()], 0, seg.m, 0, 16); torsoBase.set(root).mul(seg);
                headUnits(a, look, b, eyesShut(a, bed != null), firstPerson);
            }
            garments(a, look, b, firstPerson);
            ItemDef cape = a.eq.def(Slot.CAPE);
            if (cape != null) {
                float speed = (float) Math.hypot(a.vx, a.vz);
                // the cape itself (a skillcape in its colours, its emblem on the back), hung from the shoulders and
                // streaming back as the body goes faster
                float sway = -(0.08f + 0.42f * Math.min(1, speed / 6f)) - 0.04f * (float) Math.sin(clock * 2.3 + a.id);
                System.arraycopy(rig.seg[Seg.TORSO.ordinal()], 0, seg.m, 0, 16);
                m.set(root).mul(seg).translate(0, b.torso[1] + 0.2f, b.torso[2] * b.depth / 2f + 0.25f).rotateX(sway).scale(1 / Body.PX, 1 / Body.PX, 1 / Body.PX);
                drawItem(cape, 0);
            }
            // worn containers ride on the body: the backpack on the back, the pouch at the right hip
            ItemDef pack = a.eq.def(Slot.BACK), pouch = a.eq.def(Slot.BELT);
            if (pack != null) torsoItem(rig.seg[Seg.TORSO.ordinal()], pack, 0, b.torso[1] * 0.2f, b.torso[2] * b.depth / 2f + (cape != null ? 0.45f : 0.05f), 0, false);
            if (pouch != null) torsoItem(rig.seg[Seg.TORSO.ordinal()], pouch, -(b.torso[0] * b.girth / 2f + 0.05f), 0.4f, 0, (float) -Math.PI / 2, false);
            // worn arrows ride in a quiver over the right shoulder blade, beside a pack if there is one
            ItemDef ammo = a.eq.def(Slot.AMMO);
            if (ammo != null) torsoItem(rig.seg[Seg.TORSO.ordinal()], ammo, -b.torso[0] * b.girth * 0.28f, b.torso[1] * 0.45f, b.torso[2] * b.depth / 2f + (pack != null ? 0.9f : 0.35f) + (cape != null ? 0.4f : 0), 0.35f, false);
            // jewellery: a necklace lies round the neck and down the breastbone to its pendant
            ItemDef neck = a.eq.def(Slot.NECK);
            if (neck != null && !firstPerson && a.eq.def(Slot.BODY) == null) necklace(rig.seg[Seg.TORSO.ordinal()], b, neck);
        }
        // what the hands hold
        ItemDef main = a.main(), off = a.off();
        boolean bow = main != null && (main.family == Family.BOW || main.family == Family.CROSSBOW);
        for (int k = 0; k < 2; k++) {
            ItemDef holds = k == 0 ? main : off;
            if (gone[(k == 0 ? Seg.R_ARM_LO : Seg.L_ARM_LO).ordinal()]) continue;
            float[] grip = world(rig.hand[k]), dir = worldDir(rig.itemDir[k]);
            float yaw = a.yaw;
            if (holds != null) {
                // a shield on the forearm: carried face-out at the side; raised in guard (or seen through the eyes) its face
                // turned to the front, where the blows come from
                if (holds.family == Family.SHIELD) held(holds, add(grip, worldDir(new float[]{0, 0, -1}), 0.08f), new float[]{0, 1, 0}, yaw, !(a.cs.blocking || firstPerson));
                else if (holds.family == Family.BOW || holds.family == Family.CROSSBOW) held(holds, grip, worldDir(rig.itemDir[k]), yaw, true);
                else held(holds, grip, dir, yaw, true);   // edge forward, flat to the side, as a blade is carried
            }
            if (k == 0 && bow && main.family == Family.BOW && a.cs.draw > 0) held(rpg.item.Items.BRONZE_ARROWS, grip, dir, yaw, false);
            // a ring on the right hand: a glint of gold (and its stone) at the knuckles
            ItemDef ring = k == 0 ? a.eq.def(Slot.RING) : null;
            if (ring != null && a.eq.def(Slot.HANDS) == null) {
                m.identity().translate(grip[0] - dir[0] * 0.03f, grip[1] - dir[1] * 0.03f, grip[2] - dir[2] * 0.03f).rotateY(yaw).scale(0.05f, 0.03f, 0.05f);
                put(box, 0xE2B640, 1, 0.3f, 0.85f);
                if (ring.gemColor != 0) { m.identity().translate(grip[0] - dir[0] * 0.03f, grip[1] - dir[1] * 0.03f + 0.022f, grip[2] - dir[2] * 0.03f).scale(0.026f, 0.02f, 0.026f); put(box, ring.gemColor, 1.3f, 0.15f, 0.1f); }
            }
        }
    }

    // ------------------------------------------------------------------ gore: lost parts, stumps, severed limbs
    /** Which of a figure's segments are gone (Gore): the head, an arm (both its segments), a leg. */
    private boolean[] goneSegs(Actor a) {
        boolean[] out = new boolean[CharModel.SEGS];
        if (gore == null) return out;
        var w = gore.wounds.get(a.id);
        if (w == null || !w.any()) return out;
        for (var p : rpg.combat.Gore.Part.values()) if (w.lost(p)) for (Seg s : segsOf(p)) out[s.ordinal()] = true;
        return out;
    }

    private static Seg[] segsOf(rpg.combat.Gore.Part p) {
        return switch (p) {
            case HEAD -> new Seg[]{Seg.HEAD};
            case ARM_R -> new Seg[]{Seg.R_ARM_UP, Seg.R_ARM_LO};
            case ARM_L -> new Seg[]{Seg.L_ARM_UP, Seg.L_ARM_LO};
            case LEG_R -> new Seg[]{Seg.R_LEG_UP, Seg.R_LEG_LO};
            case LEG_L -> new Seg[]{Seg.L_LEG_UP, Seg.L_LEG_LO};
            default -> new Seg[0];
        };
    }

    private static boolean isRoot(Seg s) { return s == Seg.HEAD || s == Seg.R_ARM_UP || s == Seg.L_ARM_UP || s == Seg.R_LEG_UP || s == Seg.L_LEG_UP; }

    /** The wound where a part was: a raw, dark cap at the joint (the current matrix is the lost segment's). */
    private void stump() {
        m.scale(2.2f, 0.7f, 2.2f).translate(0, -0.3f, 0);
        put(box, 0x6E0E0E, 1, 0.55f, 0);
    }

    /** The game's wounds (set by the scene each frame; null in the creator). */
    private rpg.combat.Gore gore;
    /** How many of the game's stains have been laid as decals. */
    private int stainsLaid;

    /** Severed limbs where their rigid bodies lie, each drawn from its owner's own figure. */
    private void limbs(Rpg g) {
        for (var l : g.gore.limbs) {
            Snapshots.Rigid r = g.lastRigids.body(l.body);
            if (r == null) continue;
            if (l.species != null) {
                // an animal's part: its own figure part, its middle where the rigid body is, and the cut end dark
                var af = AnimalModels.of(l.species, l.variant);
                ItemModels.Model md = switch (l.part) {
                    case HEAD -> af.head; case TAIL -> af.tail;
                    case ARM_R, ARM_L -> af.bird ? af.wing : af.legFront;
                    default -> af.bird ? af.legFront : af.legBack;
                };
                if (md == null) continue;
                r.matrix(m16);
                System.arraycopy(m16, 0, body.m, 0, 16);
                float vs = AnimalModels.PX * l.scale;
                var vm = md.vox();
                part(md, vs, -(vm.sx / 2f - md.gx()), -(vm.sy / 2f - md.gy()), -(vm.sz / 2f - md.gz()), 0, 0, 0);
                continue;
            }
            Actor owner = g.actor(l.owner);
            int[][] fig = figure(owner != null ? owner : new Actor(0, "", 0), l.look);
            r.matrix(m16);
            float px = Body.PX;
            Seg[] segs = segsOf(l.part);
            if (l.part == rpg.combat.Gore.Part.HEAD) {
                // the head's segment stands up from the neck: the box's centre is half a head above it
                System.arraycopy(m16, 0, root.m, 0, 16);
                m.set(root).translate(0, -l.length / 2, 0).scale(px, px, px);
                put(fig[0][Seg.HEAD.ordinal()], 0, 0.9f, 0.9f, 0.9f, 0, 1, 0.85f, 0);
                put(fig[1][Seg.HEAD.ordinal()], 0, 0.9f, 0.9f, 0.9f, 0, 1, 0.85f, 0);
                continue;
            }
            // a limb hangs down from its top joint: upper segment from the box's top, the lower one where it ends
            float upper = (l.part == rpg.combat.Gore.Part.ARM_R || l.part == rpg.combat.Gore.Part.ARM_L ? Body.UPPER_ARM : Body.THIGH) * px;
            System.arraycopy(m16, 0, root.m, 0, 16);
            m.set(root).translate(0, l.length / 2, 0).scale(px, px, px);
            put(fig[0][segs[0].ordinal()], 0, 0.9f, 0.9f, 0.9f, 0, 1, 0.85f, 0);
            put(fig[1][segs[0].ordinal()], 0, 0.9f, 0.9f, 0.9f, 0, 1, 0.85f, 0);
            m.set(root).translate(0, l.length / 2 - upper, 0).scale(px, px, px);
            put(fig[0][segs[1].ordinal()], 0, 0.9f, 0.9f, 0.9f, 0, 1, 0.85f, 0);
            put(fig[1][segs[1].ordinal()], 0, 0.9f, 0.9f, 0.9f, 0, 1, 0.85f, 0);
            // the cut end
            m.set(root).translate(0, l.length / 2, 0).scale(0.12f, 0.03f, 0.12f);
            put(box, 0x6E0E0E, 1, 0.55f, 0);
        }
    }

    /**
     * Blood on the world's faces: the engine's decal layer (only its dynamic marks - the devmap lays out no static
     * grime), fed each new stain the game makes. Pools on the floor and spatter on walls are lit and shadowed as the
     * surface they lie on.
     */
    private astratto.decal.Decals decals;
    private void stains(Rpg g) {
        if (decals == null) {
            decals = new astratto.decal.Decals();
            final Rpg game = g;
            decals.attach(new astratto.decal.Decals.Source() {
                public int width() { return DevMap.SX; }
                public int depth() { return DevMap.SZ; }
                public int height() { return DevMap.SY; }
                public int kind(int x, int y, int z) { return 0; }
                public boolean open(int x, int y, int z) { return !game.vox.solid(x, y, z); }
                public int skyLight(int x, int y, int z) { return Math.round(game.vox.skyLight(x, y, z) * 15); }
                public int top(int x, int z) { for (int y = DevMap.SY - 1; y >= 0; y--) if (game.vox.block(x, y, z) != 0) return y; return 0; }
                public float wear(int x, int z) { return 0; }
                public int version(int cx, int cz) { return 0; }
            });
            decals.density = 0;
            decals.initNative(r);
        }
        var gore = g.gore;
        int fresh = gore.stainCount - stainsLaid;
        int from = Math.max(0, gore.stains.size() - fresh);
        for (int i = from; i < gore.stains.size(); i++) {
            var st = gore.stains.get(i);
            // fresh blood is bright, and darkens a little with each layer on the same spot
            decals.add(astratto.decal.DecalAtlas.BLOOD, st.x(), st.y(), st.z(), st.nx(), st.ny(), st.nz(), st.size(), st.rot(), 1800, 0x9A1010, 0.92f);
        }
        stainsLaid = gore.stainCount;
        decals.time = (float) g.time;
        decals.frame(cmd);
    }

    /** The current matrix: the figure's root times a segment's matrix (figure space, pixels). */
    private void segMatrix(float[] local) {
        System.arraycopy(local, 0, seg.m, 0, 16);
        m.set(root).mul(seg);
    }

    private final Mat4 headBase = new Mat4(), torsoBase = new Mat4();

    /**
     * What sits on a head, drawn from the head's and torso's matrices in headBase / torsoBase (figure pixels, joints at
     * the origin): the hair (and its fall down the back) unless a helmet hides it, the beard, and - shut - the eyelids.
     * Shared by the living figure and the dead body, so a corpse keeps its hair and lies with its eyes closed.
     */
    private void headUnits(Actor a, Appearance look, Body b, boolean shut, boolean shadowOnly) {
        int hairRgb = Appearance.hairColor(look.hairColor);
        if (a.eq.def(Slot.HEAD) == null) {
            unit(headBase, b.head[1] / 2f, b, mesh("hair:" + look.female + ":" + look.hair, () -> Meshes.hair(look.female, look.hair)), hairRgb, shadowOnly);
            unit(torsoBase, b.neckY() - b.hipY(), b, mesh("hairhang:" + look.female + ":" + look.hair, () -> Meshes.hairHang(look.female, look.hair)), hairRgb, false);
        }
        if (!look.female && look.beard > 0) unit(headBase, b.head[1] / 2f, b, mesh("beard:" + look.beard, () -> Meshes.beard(look.beard)), Appearance.hairColor(look.beardColor), shadowOnly);
        if (shut && !shadowOnly) {
            int skin = look.skinRgb();
            unit(headBase, b.head[1] / 2f, b, mesh("lids:" + skin + ":" + look.female, () -> Meshes.eyelids(skin, look.female)), 0xFFFFFF, false);
        }
    }

    private void unit(Mat4 base, float up, Body b, int mesh, int rgb, boolean shadowOnly) {
        if (mesh == 0) return;
        m.set(base).translate(0, up, 0).scale(b.head[0], b.head[1], b.head[2]);
        put(mesh, shadowOnly ? AstrattoNative.MODEL_SHADOW_ONLY : 0, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 0, 1, 0.9f, 0);
    }

    /**
     * Whether a person's eyes are shut this frame: asleep in bed, or the blink - every three to six seconds (each
     * person on their own rhythm, now and then a double blink), the lids down for about a tenth of a second.
     */
    /** The review's switch: every figure drawn with its eyes shut (RPG_FIGURE_SHUT in the figures gallery). */
    public static boolean reviewShut;

    private boolean eyesShut(Actor a, boolean asleep) {
        if (asleep || reviewShut) return true;
        long h = a.id * 0x9E3779B97F4A7C15L;
        double period = 3.0 + ((h >>> 40) & 1023) / 1023.0 * 3.0;
        double phase = (clock + ((h >>> 20) & 1023) / 1023.0 * period) % period;
        return phase < 0.11 || (((h >>> 12) & 3) == 0 && phase > 0.24 && phase < 0.34);
    }

    private void headPart(float[] headLocal, Body b, int mesh, int rgb, boolean shadowOnly) {
        if (mesh == 0) return;
        System.arraycopy(headLocal, 0, seg.m, 0, 16);
        m.set(root).mul(seg).translate(0, b.head[1] / 2f, 0).scale(b.head[0], b.head[1], b.head[2]);
        put(mesh, shadowOnly ? AstrattoNative.MODEL_SHADOW_ONLY : 0, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 0, 1, 0.9f, 0);
    }

    /** A worn necklace on the torso (pixels): a chain in a V from either side of the neck to the pendant on the breastbone. */
    private void necklace(float[] torsoLocal, Body b, ItemDef d) {
        boolean dodgy = d.id.equals("dodgy_necklace"), amulet = d.id.endsWith("_amulet");
        int chain = dodgy ? 0x6A4A2C : 0xE2B640;
        float top = b.torso[1], front = -b.torso[2] * b.depth / 2f - 0.12f, half = 1.7f, drop = amulet ? 3.2f : 2.6f;
        System.arraycopy(torsoLocal, 0, seg.m, 0, 16);
        for (int s = -1; s <= 1; s += 2) {
            float x0 = s * half, y0 = top - 0.1f, x1 = 0, y1 = top - drop;
            float len = (float) Math.hypot(x1 - x0, y1 - y0), ang = (float) Math.atan2(y1 - y0, x1 - x0);
            m.set(root).mul(seg).translate((x0 + x1) / 2, (y0 + y1) / 2, front).rotateZ(ang).scale(len, 0.28f, 0.28f);
            put(box, chain, 1, dodgy ? 0.9f : 0.3f, dodgy ? 0 : 0.85f);
        }
        if (dodgy) {
            for (int k = -1; k <= 1; k++) { m.set(root).mul(seg).translate(k * 0.7f, top - drop + 0.5f - Math.abs(k) * 0.5f, front - 0.05f).scale(0.3f, 0.7f, 0.3f); put(box, 0xEDE6D0, 1, 0.8f, 0); }
            return;
        }
        float ps = amulet ? 1.1f : 0.75f;
        m.set(root).mul(seg).translate(0, top - drop - ps * 0.45f, front - 0.08f).scale(ps, ps * (amulet ? 1f : 1.2f), 0.35f);
        put(box, d.gemColor != 0 && !amulet ? d.gemColor : chain, 1, 0.25f, d.gemColor != 0 && !amulet ? 0.1f : 0.85f);
        if (amulet && d.gemColor != 0) { m.set(root).mul(seg).translate(0, top - drop - ps * 0.45f, front - 0.27f).scale(0.5f, 0.5f, 0.1f); put(box, d.gemColor, 1, 0.2f, 0.1f); }
    }

    /** Head-unit geometry hung from the top of the neck on the torso (long hair). */
    private void neckPart(float[] torsoLocal, Body b, int mesh, int rgb, boolean shadowOnly) {
        if (mesh == 0) return;
        System.arraycopy(torsoLocal, 0, seg.m, 0, 16);
        m.set(root).mul(seg).translate(0, b.neckY() - b.hipY(), 0).scale(b.head[0], b.head[1], b.head[2]);
        put(mesh, shadowOnly ? AstrattoNative.MODEL_SHADOW_ONLY : 0, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 0, 1, 0.9f, 0);
    }

    /**
     * The parts of a cosmetic outfit that stand off the body (GDD 2.1 catalogs, {@link Outfit}): hems of tunics and robe
     * tops, skirts and robes, collars, a hood, flared cuffs, a drape, the toes of shoes. A skirt is two panels, front and
     * back, hung from the hips; each swings out as far as the legs inside it reach, so a stride never pushes a knee
     * through the cloth. Armor covering the body or legs hides what it covers.
     */
    private void garments(Actor a, Appearance look, Body b, boolean firstPerson) {
        Outfit.Top t = Outfit.top(look.top);
        Outfit.Legs l = Outfit.legs(look.bottom);
        Outfit.Shoes sh = Outfit.shoes(look.shoes);
        float[] tm = rig.seg[Seg.TORSO.ordinal()];
        int top = Appearance.color(look.topColor), cloth = Appearance.color(look.bottomColor), shoe = Appearance.color(look.shoesColor);
        boolean bodyArmor = a.eq.def(Slot.BODY) != null, legArmor = a.eq.def(Slot.LEGS) != null, feetArmor = a.eq.def(Slot.FEET) != null;
        float tw = b.torso[0] * b.girth, td = b.torso[2] * b.depth;
        // how far forward and back the thighs swing (radians), for the panels
        float fwd = 0, back = 0;
        for (Seg s : new Seg[]{Seg.R_LEG_UP, Seg.L_LEG_UP}) {
            float[] q = rig.seg[s.ordinal()];
            float ang = (float) Math.atan2(q[6], q[5]);   // the bone's +y runs knee to hip: forward thigh tips +y back
            fwd = Math.max(fwd, ang); back = Math.max(back, -ang);
        }
        float hipW = Math.max(tw, b.hipX() * 2 + b.leg[0] * b.limb) + 0.9f, hipD = Math.max(td, b.leg[2] * b.limb) + 0.9f;
        // the legs: skirts and robes
        float skirt = legArmor ? 0 : Outfit.skirtLength(l);
        if (skirt > 0) {
            int c = cloth;
            float flare = l.kind() == Outfit.L_PLEATED ? 0.9f : l.kind() == Outfit.L_ROBE ? 0.7f : 0.5f;
            panels(tm, hipW + flare, hipD + flare, skirt, fwd, back, c, l.kind() == Outfit.L_PLEATED, l.detail() == Outfit.D_SPLIT_FRONT, firstPerson);
            if (l.detail() == Outfit.D_LAYERED) panels(tm, hipW + flare + 0.5f, hipD + flare + 0.5f, skirt * 0.55f, fwd, back, Skin.mix(0xFF000000 | cloth, 0xFFF0EBDD, 0.35f) & 0xFFFFFF, false, false, firstPerson);
            if (l.detail() == Outfit.D_WRAPPED) hangBox(tm, -hipW * 0.25f, 0.2f, -hipD / 2 - 0.05f, hipW * 0.55f, skirt * 0.9f, 0.3f, 0.12f + fwd, (Skin.shade(0xFF000000 | cloth, 0.8f) & 0xFFFFFF), 1);
        }
        // the top's hem below the waist: a tunic's, a long vest's, a robe top's
        if (!bodyArmor) {
            float hem = switch (t.hem()) { case Outfit.HEM_HIP -> 3.4f; case Outfit.HEM_THIGH -> 6.5f; case Outfit.HEM_KNEE -> 11.2f; default -> 0; };
            if (hem > 0) {
                float out = 0.6f + t.loose() * 1.5f + (skirt > 0 ? 0.6f : 0);
                panels(tm, hipW + out, hipD + out, hem, fwd, back, top, false, t.layer() == Outfit.LAYER_OPEN_FRONT, firstPerson);
                if (t.belt() || t.detail() == Outfit.T_ROPE) hangBox(tm, 0, 1.2f, 0, tw + 0.5f, 0.9f, td + 0.5f, 0, 0x4A3420, 1);
            }
            switch (t.neck()) {
                case Outfit.NECK_HIGH -> hangBox(tm, 0, b.torso[1] + 1.4f, 0, 4.8f, 1.6f, Math.min(td, 4f) + 0.6f, 0, top, 0.95f);
                case Outfit.NECK_COLLAR -> { hangBox(tm, -1.6f, b.torso[1] + 0.4f, -td / 2 - 0.2f, 1.6f, 1.6f, 0.4f, -0.35f, (Skin.shade(0xFF000000 | top, 1.12f) & 0xFFFFFF), 1); hangBox(tm, 1.6f, b.torso[1] + 0.4f, -td / 2 - 0.2f, 1.6f, 1.6f, 0.4f, -0.35f, (Skin.shade(0xFF000000 | top, 1.12f) & 0xFFFFFF), 1); }
                default -> { }
            }
            if (t.detail() == Outfit.T_HOOD && a.eq.def(Slot.HEAD) == null) hangBox(tm, 0, b.torso[1] + 0.8f, td / 2 + 0.6f, Math.min(tw, 6.5f), 3.2f, 1.6f, 0.2f, (Skin.shade(0xFF000000 | top, 0.9f) & 0xFFFFFF), 1);
            if (t.detail() == Outfit.T_DRAPE) {
                System.arraycopy(tm, 0, seg.m, 0, 16);
                m.set(root).mul(seg).translate(0, b.torso[1] * 0.55f, -td / 2 - 0.3f).rotateZ(0.75f).scale(tw * 1.25f, 1.6f, 0.5f);
                put(box, (Skin.shade(0xFF000000 | top, 0.85f) & 0xFFFFFF), 1, 0.9f, 0);
            }
            if (t.detail() == Outfit.T_WIDE_CUFFS) for (Seg s : new Seg[]{Seg.R_ARM_LO, Seg.L_ARM_LO}) {
                System.arraycopy(rig.seg[s.ordinal()], 0, seg.m, 0, 16);
                m.set(root).mul(seg).translate(0, -3.4f, 0).scale(b.arm[0] * b.limb + 1.6f, 2.4f, b.arm[2] * b.limb + 1.6f);
                put(box, top, 0.95f, 0.9f, 0);
            }
        }
        // the toes of shoes that have a shape of their own
        if (!feetArmor && (sh.style() == Outfit.S_POINTED || sh.style() == Outfit.S_ROUNDED)) for (Seg s : new Seg[]{Seg.R_LEG_LO, Seg.L_LEG_LO}) {
            System.arraycopy(rig.seg[s.ordinal()], 0, seg.m, 0, 16);
            float lw = b.leg[0] * b.limb, ld = b.leg[2] * b.limb;
            if (sh.style() == Outfit.S_POINTED) m.set(root).mul(seg).translate(0, -Body.SHIN + 0.45f, -ld / 2 - 0.7f).scale(lw * 0.55f, 0.9f, 1.4f);
            else m.set(root).mul(seg).translate(0, -Body.SHIN + 0.75f, -ld / 2 - 0.25f).scale(lw + 0.3f, 1.5f, 0.6f);
            put(box, shoe, 1, 0.8f, 0);
        }
    }

    /** A skirt (or a hem) as a front and a back panel hung from the hips, each tipped out as far as the thighs swing. */
    /**
     * A hem or a skirt: a ring of overlapping strips round the hips, each hanging from the waist and pushed out by the
     * thigh on its side - the front strips when that leg steps forward, the back ones when it swings back - with a
     * little flare for loose cloth, so the cloth moves with the stride instead of swinging as two boards (and shows no
     * corner at the hip). Pleated cloth alternates light and dark strips; a split front leaves the front strip out.
     */
    private void panels(float[] torsoLocal, float w, float d, float len, float fwd, float back, int rgb, boolean pleats, boolean splitFront, boolean firstPerson) {
        float[] qr = rig.seg[Seg.R_LEG_UP.ordinal()], ql = rig.seg[Seg.L_LEG_UP.ordinal()];
        float angR = (float) Math.atan2(qr[6], qr[5]), angL = (float) Math.atan2(ql[6], ql[5]);   // + the thigh forward
        int n = 12;
        float rx = w / 2, rz = d / 2, circ = (float) (Math.PI * (3 * (rx + rz) - Math.sqrt((3 * rx + rz) * (rx + 3 * rz))));
        float stripW = circ / n * 1.18f, flare = 0.08f + Math.max(0, (w - 8) * 0.02f);
        float r0 = ((rgb >> 16) & 255) / 255f, g0 = ((rgb >> 8) & 255) / 255f, b0 = (rgb & 255) / 255f;
        // each strip's push first, then smoothed round the ring so neighbours lean together (cloth, not a fringe)
        float[] outs = new float[n], pxs = new float[n], pzs = new float[n], nxs = new float[n], nzs = new float[n];
        for (int i = 0; i < n; i++) {
            double th = (i + 0.5) * 2 * Math.PI / n;
            float px = (float) Math.cos(th) * rx, pz = (float) Math.sin(th) * rz;
            float nx = (float) Math.cos(th) / rx, nz = (float) Math.sin(th) / rz, nl = (float) Math.hypot(nx, nz);
            nx /= nl; nz /= nl;
            float nf = -nz, leg = px >= 0 ? angR : angL;
            float out = flare + Math.max(0, leg) * Math.max(0, nf) * 1.05f + Math.max(0, -leg) * Math.max(0, -nf) * 1.05f;
            out += Math.abs(angR - angL) * 0.18f * (1 - Math.abs(nf));
            outs[i] = out; pxs[i] = px; pzs[i] = pz; nxs[i] = nx; nzs[i] = nz;
        }
        for (int pass = 0; pass < 3; pass++) {
            float[] t = outs.clone();
            for (int i = 0; i < n; i++) outs[i] = 0.25f * t[(i + n - 1) % n] + 0.5f * t[i] + 0.25f * t[(i + 1) % n];
        }
        stripW = circ / n * 1.5f;
        for (int i = 0; i < n; i++) {
            float px = pxs[i], pz = pzs[i], nx = nxs[i], nz = nzs[i], nf = -nz, out = outs[i];
            if (splitFront && nf > 0.93f) continue;
            float yaw = (float) Math.atan2(nx, nz);
            System.arraycopy(torsoLocal, 0, seg.m, 0, 16);
            m.set(root).mul(seg).translate(px, 0.8f, pz).rotateY(yaw).rotateX(-out).translate(0, -len / 2, 0).scale(stripW, len, 0.7f);
            float sh = (nf > 0 ? 1f : 0.93f) * (pleats && i % 2 == 1 ? 0.82f : 1f);
            put(box, 0, r0 * sh, g0 * sh, b0 * sh, 0, 1, 0.9f, 0);
        }
    }

    /** A box hung from a point on the torso frame: its top there, tilted about the frame's x, w x len x d pixels. */
    private void hangBox(float[] torsoLocal, float x, float y, float z, float w, float len, float d, float tilt, int rgb, float shade) {
        System.arraycopy(torsoLocal, 0, seg.m, 0, 16);
        m.set(root).mul(seg).translate(x, y, z).rotateX(tilt).translate(0, -len / 2, 0).scale(w, len, d);
        put(box, rgb, shade, 0.9f, 0);
    }

    /** A point in figure space (pixels) to the world, through the root. */
    private float[] world(float[] p) { float[] o = new float[3]; root.transformPoint(p[0], p[1], p[2], o); return o; }
    private float[] worldDir(float[] d) { float[] o = new float[3]; root.transformDir(d[0], d[1], d[2], o); return norm(o); }

    /** A target dummy: post, sack body, arms; it rocks when struck. */
    private void dummy(Actor d, double t) {
        float wob = d.cs.flash * (float) Math.sin(t * 30) * 0.2f;
        float[] base = {d.x, d.y, d.z};
        float[] top = {d.x + wob * 0.6f, d.y + 1.55f, d.z};
        limb(base, add(base, new float[]{0, 1, 0}, 0.4f), 0.12f, 0.12f, d.yaw, 0x6A4A2A, 1, 0.9f, 0);
        limb(add(base, new float[]{0, 1, 0}, 0.4f), top, 0.42f, 0.32f, d.yaw, 0xC8B080, 1 + d.cs.flash, 0.95f, 0);
        if (d.eq.def(Slot.BODY) != null) limb(add(base, new float[]{0, 1, 0}, 0.75f), add(top, new float[]{0, 1, 0}, -0.15f), 0.46f, 0.36f, d.yaw, d.eq.def(Slot.BODY).color, 1, 0.4f, 0.7f);
        float[] R = right(d.yaw);
        limb(add(add(top, R, -0.5f), new float[]{0, 1, 0}, -0.3f), add(add(top, R, 0.5f), new float[]{0, 1, 0}, -0.3f), 0.08f, 0.08f, d.yaw, 0x6A4A2A, 1, 0.9f, 0);
        limb(add(top, new float[]{0, 1, 0}, 0.05f), add(top, new float[]{0, 1, 0}, 0.3f), 0.26f, 0.24f, d.yaw, 0xD8C090, 1, 0.95f, 0);
    }

    /** The figure's pose as Sydney's fifteen joint points (world), kept on the actor: what a physical reaction starts from. */
    private void keepPose(Actor a, Body b) {
        if (a.posePts == null) a.posePts = new float[AstrattoNative.RD_PARTICLES * 3];
        float[] tm = rig.seg[Seg.TORSO.ordinal()], hm = rig.seg[Seg.HEAD.ordinal()];
        putPose(a, AstrattoNative.RD_PELVIS, new float[]{tm[12], tm[13], tm[14]});
        putPose(a, AstrattoNative.RD_NECK, new float[]{hm[12], hm[13], hm[14]});
        putPose(a, AstrattoNative.RD_HEAD, new float[]{hm[12] + hm[4] * 5, hm[13] + hm[5] * 5, hm[14] + hm[6] * 5});
        for (int k = 0; k < 2; k++) {
            // k 0: the right side, 1: the left (the rig's order); the API's points are left first
            float[] up = rig.seg[(k == 0 ? Seg.R_ARM_UP : Seg.L_ARM_UP).ordinal()], lo = rig.seg[(k == 0 ? Seg.R_ARM_LO : Seg.L_ARM_LO).ordinal()];
            int sh = k == 0 ? AstrattoNative.RD_R_SHOULDER : AstrattoNative.RD_L_SHOULDER;
            putPose(a, sh, new float[]{up[12], up[13], up[14]});
            putPose(a, sh + 1, new float[]{lo[12], lo[13], lo[14]});
            putPose(a, sh + 2, rig.hand[k]);
            float[] th = rig.seg[(k == 0 ? Seg.R_LEG_UP : Seg.L_LEG_UP).ordinal()], sn = rig.seg[(k == 0 ? Seg.R_LEG_LO : Seg.L_LEG_LO).ordinal()];
            int hp = k == 0 ? AstrattoNative.RD_R_HIP : AstrattoNative.RD_L_HIP;
            putPose(a, hp, new float[]{th[12], th[13], th[14]});
            putPose(a, hp + 1, new float[]{sn[12], sn[13], sn[14]});
            putPose(a, hp + 2, new float[]{sn[12] - sn[4] * Body.SHIN, sn[13] - sn[5] * Body.SHIN, sn[14] - sn[6] * Body.SHIN});
        }
        a.poseAt = clock;
    }

    private void putPose(Actor a, int i, float[] figurePoint) { float[] w = world(figurePoint); a.posePts[i * 3] = w[0]; a.posePts[i * 3 + 1] = w[1]; a.posePts[i * 3 + 2] = w[2]; }

    /** A dead NPC: their own figure's segments laid between the ragdoll's particles as the engine has them. */
    private void ragdoll(Snapshots.Ragdoll rd, Actor n) {
        int[][] fig = figure(n, n.look);
        float[] rr = right(rd.yaw);
        Object[][] bones = {
                {Seg.TORSO, AstrattoNative.RD_PELVIS, AstrattoNative.RD_NECK, true}, {Seg.HEAD, AstrattoNative.RD_NECK, AstrattoNative.RD_HEAD, true},
                {Seg.L_ARM_UP, AstrattoNative.RD_L_SHOULDER, AstrattoNative.RD_L_ELBOW, false}, {Seg.L_ARM_LO, AstrattoNative.RD_L_ELBOW, AstrattoNative.RD_L_WRIST, false},
                {Seg.R_ARM_UP, AstrattoNative.RD_R_SHOULDER, AstrattoNative.RD_R_ELBOW, false}, {Seg.R_ARM_LO, AstrattoNative.RD_R_ELBOW, AstrattoNative.RD_R_WRIST, false},
                {Seg.L_LEG_UP, AstrattoNative.RD_L_HIP, AstrattoNative.RD_L_KNEE, false}, {Seg.L_LEG_LO, AstrattoNative.RD_L_KNEE, AstrattoNative.RD_L_ANKLE, false},
                {Seg.R_LEG_UP, AstrattoNative.RD_R_HIP, AstrattoNative.RD_R_KNEE, false}, {Seg.R_LEG_LO, AstrattoNative.RD_R_KNEE, AstrattoNative.RD_R_ANKLE, false}};
        boolean[] gone = goneSegs(n);
        for (Object[] bn : bones) {
            Seg s = (Seg) bn[0];
            if (gone[s.ordinal()]) continue;
            int a = (int) bn[1], c = (int) bn[2];
            float[] p0 = {rd.p[a * 3], rd.p[a * 3 + 1], rd.p[a * 3 + 2]}, p1 = {rd.p[c * 3], rd.p[c * 3 + 1], rd.p[c * 3 + 2]};
            boolean up = (boolean) bn[3];
            float[] y = norm(up ? new float[]{p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]} : new float[]{p0[0] - p1[0], p0[1] - p1[1], p0[2] - p1[2]});
            float d = rr[0] * y[0] + rr[1] * y[1] + rr[2] * y[2];
            float[] x = norm(new float[]{rr[0] - y[0] * d, rr[1] - y[1] * d, rr[2] - y[2] * d});
            float[] z = cross(x, y);
            float px = Body.PX;
            float[] mm = m.m;
            mm[0] = x[0] * px; mm[1] = x[1] * px; mm[2] = x[2] * px; mm[3] = 0;
            mm[4] = y[0] * px; mm[5] = y[1] * px; mm[6] = y[2] * px; mm[7] = 0;
            mm[8] = z[0] * px; mm[9] = z[1] * px; mm[10] = z[2] * px; mm[11] = 0;
            mm[12] = p0[0]; mm[13] = p0[1]; mm[14] = p0[2]; mm[15] = 1;
            put(fig[0][s.ordinal()], 0, 0.9f, 0.9f, 0.9f, 0, 1, 0.9f, 0);
            put(fig[1][s.ordinal()], 0, 0.9f, 0.9f, 0.9f, 0, 1, 0.9f, 0);
            if (s == Seg.TORSO) torsoBase.set(m);
            if (s == Seg.HEAD) headBase.set(m);
        }
        // the hair stays on the head, and the dead lie with their eyes closed (the living, reeling, still blink)
        if (!gone[Seg.HEAD.ordinal()]) headUnits(n, n.look, Body.of(n.look), !n.alive || eyesShut(n, false), false);
        // a body in Sydney's hands still holds what it held, at the wrist along the forearm - living, or dead until the
        // weapon is taken from it
        {
            ItemDef main = n.main(), off = n.off();
            for (int k = 0; k < 2; k++) {
                ItemDef holds = k == 0 ? main : off;
                if (holds == null || gone[(k == 0 ? Seg.R_ARM_LO : Seg.L_ARM_LO).ordinal()]) continue;
                int el = k == 0 ? AstrattoNative.RD_R_ELBOW : AstrattoNative.RD_L_ELBOW, wr = el + 1;
                float[] grip = {rd.p[wr * 3], rd.p[wr * 3 + 1], rd.p[wr * 3 + 2]};
                float[] fore = norm(new float[]{grip[0] - rd.p[el * 3], grip[1] - rd.p[el * 3 + 1], grip[2] - rd.p[el * 3 + 2]});
                // a blade held out along the forearm, a shield across it
                held(holds, new float[]{grip[0] + fore[0] * 0.06f, grip[1] + fore[1] * 0.06f, grip[2] + fore[2] * 0.06f}, fore, rd.yaw, holds.family == Family.SHIELD);
            }
        }
    }

    // ------------------------------------------------------------------ objects
    private void object(Rpg g, WorldObject o, float dt) {
        if (o instanceof GroundItem gi) {
            // the item's own model on the ground: long things lie down, the rest stand on their base
            ItemDef d = gi.stack.def;
            ItemModels.Model md = ItemModels.of(d);
            int[] bb = astratto.voxel.VoxRaster.bounds(md.vox());
            float v = ItemModels.VOX, cx = (bb[0] + bb[3]) / 2f, cy = (bb[1] + bb[4]) / 2f, cz = (bb[2] + bb[5]) / 2f;
            boolean lies = (bb[4] - bb[1]) > 1.5f * Math.max(bb[3] - bb[0], bb[5] - bb[2]) || (bb[5] - bb[2]) <= 2;
            m.identity().translate(gi.x, gi.y - 0.04f, gi.z).rotateY(gi.id * 1.7f);
            if (lies) m.rotateX((float) (-Math.PI / 2)).translate(-(cx - md.gx()) * v, -(cy - md.gy()) * v, -(bb[2] - md.gz()) * v);
            else m.translate(-(cx - md.gx()) * v, -(bb[1] - md.gy()) * v, -(cz - md.gz()) * v);
            drawItem(d, 0);
        } else if (o instanceof Fire f) {
            for (int i = 0; i < 3; i++) { m.identity().translate(f.x, f.y + 0.06f, f.z).rotateY(i * 1.05f).scale(0.6f, 0.1f, 0.1f); put(box, 0x5A3A20, 0.6f, 0.9f, 0); }
        } else if (o instanceof BuildSpot) {
            // the plot itself is ground; its furniture draws itself
        } else if (o instanceof Station st) {
            m.identity().translate(st.x, DevMap.FLOOR, st.z).rotateY((float) Math.PI);
            drawLayers("station:" + st.kind, () -> Props.station(st.kind));
        } else if (o instanceof Furniture fu) {
            float w = fu.facing % 2 == 0 ? fu.width() : fu.depth(), d = fu.facing % 2 == 0 ? fu.depth() : fu.width();
            m.identity().translate(fu.x, DevMap.FLOOR, fu.z).rotateY(fu.yaw());
            drawLayers("furn:" + fu.kind + ":" + w + "x" + d + "x" + fu.h + ":" + (fu.kind == Furniture.Kind.BED || fu.kind == Furniture.Kind.BOOKCASE || fu.kind == Furniture.Kind.HEADSTONE ? fu.id : 0),
                    () -> Props.furniture(fu.kind, w, d, fu.h, (int) fu.id));
        } else if (o instanceof Door door) {
            doorLeaves(g, door, dt);
        } else if (o instanceof Container c && c.kind.equals("trough")) {
            // a long plank trough on legs; the grain in it rises and falls with the feed people put in
            float fx = c.x, fz = c.z, fl = DevMap.FLOOR;
            m.identity().translate(fx, fl + 0.32f, fz).scale(2.2f, 0.08f, 0.62f); put(box, 0x7A5634, 1, 0.85f, 0);
            for (int sd = -1; sd <= 1; sd += 2) {
                m.identity().translate(fx, fl + 0.45f, fz + sd * 0.29f).scale(2.2f, 0.3f, 0.06f); put(box, 0x8A6440, 1, 0.85f, 0);
                m.identity().translate(fx + sd * 1.08f, fl + 0.45f, fz).scale(0.06f, 0.3f, 0.62f); put(box, 0x8A6440, 1, 0.85f, 0);
                for (int e = -1; e <= 1; e += 2) { m.identity().translate(fx + sd * 0.95f, fl + 0.14f, fz + e * 0.24f).scale(0.1f, 0.28f, 0.1f); put(box, 0x5A3E24, 1, 0.9f, 0); }
            }
            int feed = c.contents.count(rpg.item.Items.ANIMAL_FEED);
            if (feed > 0) { float lvl = Math.min(1, feed / 6f); m.identity().translate(fx, fl + 0.37f + lvl * 0.16f, fz).scale(2.08f, 0.04f + lvl * 0.04f, 0.5f); put(box, 0xD8B860, 1, 0.95f, 0); }
        } else if (o instanceof Container c && c.kind.equals("nest")) {
            // a straw-lined box against the barn wall, its eggs on show
            m.identity().translate(c.x, DevMap.FLOOR + 0.18f, c.z).scale(0.7f, 0.36f, 0.55f); put(box, 0x8A6A44, 1, 0.85f, 0);
            m.identity().translate(c.x, DevMap.FLOOR + 0.37f, c.z).scale(0.58f, 0.04f, 0.44f); put(box, 0xD8C078, 1, 1f, 0);
            int eggs = c.contents.count(rpg.item.Items.EGG);
            for (int i = 0; i < Math.min(6, eggs); i++) { m.identity().translate(c.x - 0.18f + (i % 3) * 0.18f, DevMap.FLOOR + 0.43f, c.z - 0.08f + (i / 3) * 0.16f).scale(0.09f, 0.12f, 0.09f); put(box, 0xEFE6D0, 1, 0.6f, 0); }
        } else if (o instanceof rpg.objects.Corpse) {
            // a body is searched where it lies: the body itself is the container, so nothing is drawn over it
        } else if (o instanceof Container c) {
            if (c.kind.equals("grave") && c.heldBy != 0) {
                // carried in by a guard: a bundle of the dead's things in a sack over his back
                Actor carrier = g.actor(c.heldBy);
                if (carrier != null) {
                    float yaw = carrier.yaw, bx = (float) Math.sin(yaw) * 0.32f, bz = (float) Math.cos(yaw) * 0.32f;
                    m.identity().translate(carrier.x + bx, carrier.y + 1.05f, carrier.z + bz).rotateY(yaw).rotateX(0.25f).scale(0.5f, 0.55f, 0.32f); put(box, 0x7A6444, 1, 0.95f, 0);
                    m.identity().translate(carrier.x + bx, carrier.y + 1.36f, carrier.z + bz).rotateY(yaw).scale(0.16f, 0.1f, 0.16f); put(box, 0x5A4630, 1, 0.95f, 0);
                }
            } else if (c.kind.equals("grave")) {
                // a fresh grave: turned earth and a plain new stone, a candle burning at its foot while it stands
                m.identity().translate(c.x, DevMap.FLOOR, c.z + 0.45f);
                int honorClass = c.honor >= 22 ? 1 : c.honor <= -22 ? -1 : 0;
                drawLayers("pgrave:" + honorClass + ":" + (c.id & 7), () -> Props.memorial(c.honor, (int) (c.id & 7)));
                float fk = 0.8f + 0.2f * (float) Math.sin(g.time * 11 + c.id);
                m.identity().translate(c.x + 0.22f, DevMap.FLOOR + 0.07f, c.z - 0.25f).scale(0.05f, 0.14f, 0.05f); put(box, 0xEDE6D2, 1, 0.9f, 0);
                m.identity().translate(c.x + 0.22f, DevMap.FLOOR + 0.17f, c.z - 0.25f).scale(0.025f, 0.04f, 0.025f); put(box, 0, 1f, 0.8f, 0.35f, 3f, 1, 0.5f, 0);
                cmd.pointLight(c.x + 0.22f, DevMap.FLOOR + 0.3f, c.z - 0.25f, 3f, 2.2f * fk, 1.4f * fk, 0.5f * fk, 0);
            } else {
                float yaw = c.kind.equals("cargo") ? 0 : (float) Math.PI;
                m.identity().translate(c.x, DevMap.FLOOR + 0.3f, c.z).rotateY(yaw).scale(0.9f, 0.6f, 0.6f); put(box, c.kind.equals("cargo") ? 0x9A7448 : 0x7A5030, 1, 0.8f, 0);
                m.identity().translate(c.x, DevMap.FLOOR + 0.62f, c.z).rotateY(yaw).scale(0.94f, 0.08f, 0.64f); put(box, 0x5A3A20, 1, 0.8f, 0);
                if (c.locked) { m.identity().translate(c.x, DevMap.FLOOR + 0.45f, c.z + 0.31f).scale(0.12f, 0.14f, 0.04f); put(box, 0xC0A040, 1, 0.3f, 0.9f); }
            }
        } else if (o instanceof Fixtures.Altar) {
            // a stone altar: a plinth, a slab, a white cloth with a gold band, two candles burning
            float fl = DevMap.FLOOR;
            m.identity().translate(o.x, fl + 0.4f, o.z).scale(1.3f, 0.8f, 0.7f); put(box, 0x8A8680, 1, 0.85f, 0);
            m.identity().translate(o.x, fl + 0.85f, o.z).scale(1.45f, 0.12f, 0.82f); put(box, 0x9A968E, 1, 0.8f, 0);
            m.identity().translate(o.x, fl + 0.92f, o.z).scale(1.05f, 0.03f, 0.84f); put(box, 0xEDE8DA, 1, 0.9f, 0);
            m.identity().translate(o.x, fl + 0.93f, o.z + 0.01f).scale(1.06f, 0.031f, 0.16f); put(box, 0xD8B040, 1, 0.4f, 0.8f);
            for (int sd = -1; sd <= 1; sd += 2) {
                m.identity().translate(o.x + sd * 0.5f, fl + 1.04f, o.z).scale(0.06f, 0.2f, 0.06f); put(box, 0xF0EAD8, 1, 0.9f, 0);
                float fk = 0.85f + 0.15f * (float) Math.sin(g.time * 13 + sd);
                cmd.pointLight(o.x + sd * 0.5f, fl + 1.2f, o.z, 3.5f, 2.4f * fk, 1.6f * fk, 0.7f * fk, 0);
                m.identity().translate(o.x + sd * 0.5f, fl + 1.17f, o.z).scale(0.03f, 0.05f, 0.03f); put(box, 0, 1f, 0.8f, 0.3f, 3f, 1, 0.5f, 0);
            }
        } else if (o instanceof Fixtures.Terminal) {
            m.identity().translate(o.x, DevMap.FLOOR + 0.6f, o.z).scale(0.7f, 1.2f, 0.5f); put(box, 0x30343A, 1, 0.4f, 0.5f);
            m.identity().translate(o.x, DevMap.FLOOR + 1.0f, o.z + 0.26f).scale(0.55f, 0.4f, 0.02f); put(box, 0, 0.3f, 1f, 0.6f, 2.5f, 1, 0.2f, 0);
        } else if (o instanceof Fixtures.Cabinet) {
            m.identity().translate(o.x, DevMap.FLOOR + 1f, o.z).scale(1.6f, 2f, 0.5f); put(box, 0x6A6E74, 1, 0.5f, 0.6f);
            for (int i = 0; i < 4; i++) { m.identity().translate(o.x - 0.55f + i * 0.37f, DevMap.FLOOR + 1.1f, o.z + 0.3f).scale(0.06f, 1.2f, 0.06f); put(box, 0xC0C4CC, 1, 0.3f, 0.9f); }
        } else if (o instanceof Fixtures.Kiosk) {
            m.identity().translate(o.x, DevMap.FLOOR + 1.3f, o.z - 0.3f).scale(1.6f, 1.0f, 0.1f); put(box, 0, 0.9f, 0.75f, 0.3f, 1.2f, 1, 0.5f, 0);
        } else if (o instanceof Fixtures.Stall st) {
            int n = st.goods.count(rpg.item.Items.BREAD);
            for (int i = 0; i < n; i++) { m.identity().translate(o.x - 0.8f + i * 0.4f, DevMap.FLOOR + Furniture.COUNTER_TOP + 0.08f, o.z).scale(0.3f, 0.15f, 0.2f); put(box, 0xD8A860, 1, 0.8f, 0); }
        } else if (o instanceof Node nd && nd.kind == Node.Kind.FISH) {
            if ((int) (g.time * 3 + nd.id) % 4 == 0) g.fx.splash(nd.x + (float) Math.sin(g.time * 7) * 0.5f, nd.y, nd.z + (float) Math.cos(g.time * 5) * 0.5f);
        }
    }

    /**
     * A door's leaves, each where its rigid body is on its hinge (the physics world's pose, blended between steps): the
     * leaf model runs from its hinge edge (x = 0) to its free edge, so it is drawn half a leaf back from the body's middle.
     */
    private void doorLeaves(Rpg g, Door d, float dt) {
        String key = "door:" + d.height + ":" + d.locked;
        for (int i = 0; i < d.leaf.length; i++) {
            Snapshots.Rigid r = g.lastRigids.body(d.leaf[i]);
            if (r == null) continue;
            r.matrix(m16);
            System.arraycopy(m16, 0, m.m, 0, 16);
            float leafW = d.cells.length >= 2 ? 1f : 1f, h = d.height - 0.04f;
            m.translate(-leafW / 2, -h / 2 - 0.02f, 0);
            drawLayers(key, () -> Props.doorLeaf(d.height, d.locked));
        }
    }

    private void projectile(CombatSystem.Projectile p) {
        float l = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
        if (l < 1e-3f) return;
        float[] d = {p.vx / l, p.vy / l, p.vz / l};
        if (p.spell != null) {
            float[] col = p.spell == Spells.Spell.FIRE_BOLT ? new float[]{1, 0.45f, 0.1f} : p.spell == Spells.Spell.FROST_LANCE ? new float[]{0.4f, 0.8f, 1} : new float[]{0.8f, 0.8f, 1};
            m.identity().translate(p.x, p.y, p.z).scale(0.18f, 0.18f, 0.18f);
            put(box, AstrattoNative.MODEL_NO_SHADOW, col[0], col[1], col[2], 4f, 1, 0.3f, 0);
            return;
        }
        limb(new float[]{p.x - d[0] * 0.7f, p.y - d[1] * 0.7f, p.z - d[2] * 0.7f}, new float[]{p.x, p.y, p.z}, 0.025f, 0.025f, 0, 0xB08050, 1, 0.8f, 0);
    }

    // ------------------------------------------------------------------ the creator's stage
    /** The creator's preview: the figure on the debug plaza, the camera circling it. */
    public void creator(Preview preview, Appearance look, float turn, int w, int h, float dt, double time) {
        clock = time;
        // the camera stands still in front of the figure (the figure itself turns, dragged or slowly on its own): the whole
        // figure, or in on the face (focus 1); aimed a little to the figure's side so it stands in the free space right
        // of the creator's panel
        float f = Math.max(0, Math.min(1, preview.focus)), low = preview.aim < 0 ? Math.min(1, -preview.aim) : 0;
        float dist = (2.9f + (1.75f - 2.9f) * f) * (1 - 0.35f * preview.zoom), eye = 1.25f + (1.6f - 1.25f) * f, at = 0.95f + (1.45f - 0.95f) * f;
        // the shoes: down at the feet
        eye += (0.55f - eye) * low; at += (0.25f - at) * low;
        float cx = preview.x + 0.3f * dist / 2.9f, cz = preview.z + dist, cy = preview.y + eye;
        float side = 0.62f * dist / 2.9f;
        float fx = preview.x - side - cx, fy = (preview.y + at) - cy, fz = preview.z - cz, l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        camera(cx, cy, cz, fx / l, fy / l, fz / l, w, h, dt);
        cmd.world(preview.world);
        environment(9.5, time);
        preview.actor.look = look;
        preview.actor.x = preview.x; preview.actor.y = preview.y; preview.actor.z = preview.z;
        preview.actor.yaw = Actor.wrap((float) Math.atan2(cx - preview.x, cz - preview.z) + (float) Math.PI + turn);
        // the eyes look where the body faces (unset, the head turned as far as it could toward an old heading)
        preview.actor.aimYaw = preview.actor.yaw; preview.actor.pitch = 0;
        preview.actor.fitBody();
        human(preview.actor, look, dt, false, null);
    }

    /**
     * The title screen's shot: the chosen character standing at the edge of the town at dusk, the camera drifting slowly
     * round them in a long, low arc (to the right of the profile cards), the lamps coming on behind.
     */
    public void title(Preview preview, Appearance look, int w, int h, float dt, double time) {
        clock = time;
        double a = 0.55 + Math.sin(time * 0.035) * 0.45;
        float dist = 4.6f, cx = preview.x + (float) Math.sin(a) * dist, cz = preview.z + (float) Math.cos(a) * dist, cy = preview.y + 1.55f;
        // aimed to the figure's left, so it stands in the right half of the screen
        float rx = (float) Math.cos(a), rz = (float) -Math.sin(a);
        float tx = preview.x - rx * 1.25f, ty = preview.y + 1.15f, tz = preview.z - rz * 1.25f;
        float fx = tx - cx, fy = ty - cy, fz = tz - cz, l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        camera(cx, cy, cz, fx / l, fy / l, fz / l, w, h, dt);
        cmd.world(preview.world);
        float daylight = environment(17.9, time);
        if (daylight < 0.6f) for (float[] lp : lamps) cmd.pointLight(lp[0], lp[1], lp[2], 9, 5f, 4.2f, 2.8f, 0);
        if (look == null) return;
        preview.actor.look = look;
        preview.actor.x = preview.x; preview.actor.y = preview.y; preview.actor.z = preview.z;
        preview.actor.yaw = Actor.wrap((float) Math.atan2(cx - preview.x, cz - preview.z) + (float) Math.PI + 0.35f);
        preview.actor.aimYaw = preview.actor.yaw; preview.actor.pitch = 0;
        preview.actor.fitBody();
        human(preview.actor, look, dt, false, null);
    }

    /** What the creator shows: the figure, where it stands, in which world. */
    public static final class Preview {
        public long world;
        public float x, y, z;
        /** 0: the whole figure; 1: in on the face. */
        public float focus;
        /** Which way the closer look goes: below 0 down to the feet (the shoes), above 0 up to the face; and the wheel's zoom (0..1). */
        public float aim, zoom;
        public final Actor actor = new Actor(0, "Preview", 0);
    }

    /** World point to screen pixels; false when behind the camera. */
    public boolean project(float x, float y, float z, float[] out) {
        float w = viewProj.project(x, y, z, out);
        if (w <= 0.05f) return false;
        out[0] = (out[0] * 0.5f + 0.5f) * width;
        out[1] = (1 - (out[1] * 0.5f + 0.5f)) * height;
        return true;
    }

    public ByteBuffer pixels(int w, int h) {
        ByteBuffer px = MemoryUtil.memAlloc(w * h * 4);
        if (!r.readPixels(0, 0, w, h, px)) { MemoryUtil.memFree(px); throw new IllegalStateException("read back refused: " + r.lastError()); }
        return px;
    }

    @Override public void close() { r.close(); cmd.free(); }
}
