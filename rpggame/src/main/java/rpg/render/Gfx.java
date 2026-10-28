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
import rpg.ui.Ui2D;
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
    private final int box, ball;
    /** How spells look (SpellFx), for the game being drawn. */
    private SpellFx spellFx;
    private Object spellFxOwner;
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
    private final List<float[]> lamps = new ArrayList<>();
    private double clock;
    public NativeRenderer.Stats last;
    public rpg.Preferences menuPreferences = new rpg.Preferences();
    private boolean first = true;
    public int width, height;

    public Gfx(NativeEngine engine, int w, int h) {
        r = NativeRenderer.create(engine, w, h, AstrattoNative.QUALITY_HIGH, 1f);
        if (r == null) throw new IllegalStateException("no native renderer: " + NativeRenderer.createError);
        ByteBuffer atlas = Blocks.atlasPixels(), noise = Blocks.noisePixels();
        int atlasTex, noiseTex;
        try {
            // blocky up close, filtered far off (gamefixes: brick and other fine-patterned faces shimmered at a distance -
            // their mortar lines were sampled a texel at a time from the nearest mip, popping in and out as the view moved)
            atlasTex = r.texture(AstrattoNative.TEX_ARRAY, Blocks.TEX, Blocks.TEX, Blocks.LAYERS,
                    AstrattoNative.TEX_NEAREST | AstrattoNative.TEX_MIN_LINEAR | AstrattoNative.TEX_MIP_LINEAR | AstrattoNative.TEX_ANISOTROPIC
                            | AstrattoNative.TEX_MIPMAP | AstrattoNative.TEX_REPEAT | AstrattoNative.texMaxLevel(5), atlas);
            noiseTex = r.texture(AstrattoNative.TEX_2D, 256, 256, 1, AstrattoNative.TEX_MIPMAP | AstrattoNative.TEX_MIP_LINEAR | AstrattoNative.TEX_REPEAT, noise);
        } finally { MemoryUtil.memFree(atlas); MemoryUtil.memFree(noise); }
        if (!r.worldLook(atlasTex, noiseTex, Blocks.LAYERS, Blocks.renderDefs(), Blocks.renderShapes(), Blocks.STONE, Blocks.palette(), Blocks.layerMaterials(), DevMap.FLOOR, 0))
            throw new IllegalStateException("world look refused: " + r.lastError());
        box = r.mesh(Meshes.unitBox(), 36);
        // a ball of voxels, a unit across (the spells' orbs), white so a tint colours it
        Meshes.Boxes orb = new Meshes.Boxes();
        for (int x = 0; x < 6; x++) for (int y = 0; y < 6; y++) for (int z = 0; z < 6; z++) {
            float cx = (x - 2.5f) / 6, cy = (y - 2.5f) / 6, cz = (z - 2.5f) / 6;
            if (cx * cx + cy * cy + cz * cz <= 0.25f) orb.rgb(cx, cy, cz, 1f / 6, 1f / 6, 1f / 6, 0xFFFFFF);
        }
        ball = r.mesh(orb.vertices(), orb.count());
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
    /**
     * The lamps by night (0.1.9: lighting, gamefixes): a lantern's warm, low flame-light rather than a white flood - a
     * lamp in a room lights a pool round itself and leaves the corners in shadow (it reached the whole room evenly, so a
     * lamp-lit room at night looked like noon); a street lamp reaches further. The few nearest the eye cast shadows.
     */
    private void lampLights(rpg.world.Voxels vox) {
        int shadowed = 0;
        for (float[] l : lamps) {
            boolean roofed = vox != null && !vox.sight(l[0], l[1] + 0.6f, l[2], l[0], l[1] + 12, l[2]);
            float near = (float) Math.hypot(l[0] - camX, l[2] - camZ);
            int flags = near < 18 && shadowed < 3 ? AstrattoNative.LIGHT_SHADOW : 0;
            if (flags != 0) shadowed++;
            if (roofed) cmd.pointLight(l[0], l[1], l[2], 7, 4.2f, 2.7f, 1.25f, flags);
            else cmd.pointLight(l[0], l[1], l[2], 10, 4.6f, 3.3f, 1.7f, flags);
        }
    }

    public void findLamps(rpg.world.Voxels vox) {
        lamps.clear();
        for (int x = 0; x < DevMap.SX; x++) for (int z = 0; z < DevMap.SZ; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 8; y++)
            if (vox.block(x, y, z) == Blocks.LAMP) lamps.add(new float[]{x + 0.5f, y - 0.4f, z + 0.5f});
    }

    /** Where the fog bank has swallowed the world (metres from the eye): set by {@link #environment}, used to cull past it. */
    private float fogFar = 400;
    /** Where the camera stands (for distance culling). */
    private float camX, camY, camZ;

    // ------------------------------------------------------------------ the frame
    /** The vertical field of view in degrees (fov_desired). */
    public float fovDeg = 70;

    public void camera(float cx, float cy, float cz, float fx, float fy, float fz, int w, int h, float dt) {
        width = w; height = h;
        camX = cx; camY = cy; camZ = cz;
        float aspect = w / (float) Math.max(1, h), fov = (float) Math.toRadians(fovDeg);
        view.lookAt(cx, cy, cz, cx + fx, cy + fy, cz + fz, 0, 1, 0);
        // (gamefixes: the fog should help performance) nothing is drawn past the fog bank: the far plane closes in
        // behind it, so the chunks and props beyond are culled rather than drawn and then fogged away
        float far = Math.max(120, Math.min(400, fogFar + 12));
        proj.perspective(fov, aspect, 0.05f, far);
        viewProj.set(proj).mul(view);
        cmd.camera(view.m, proj.m, proj.m, cx, cy, cz, fx, fy, fz, fov, aspect, 0.05f, far, Math.min(160f, far), dt, first);
        first = false;
    }

    /**
     * Sun, sky and fog for the time of day (the clock's hour). The sun rises in the east, arcs south and sets in the
     * west; the light runs through a real twilight: blue-grey day haze, a warm horizon and golden light while the sun
     * is low, a cooling dusk, then a deep blue night under the moon (the renderer lights it opposite the sun). The
     * daylight share is the same curve the stealth system reads, so what looks dark is dark to the guards too.
     */
    public float environment(double hour, double time) {
        var weather = sceneGame == null ? null : sceneGame.timeSystem.weather;
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
        // (gamefixes: fog as part of the world, its strength by the hour, never plain white) the mist: heaviest round
        // dawn, when it lies in the low ground, thinning through the morning to a light haze at noon, gathering again
        // at dusk and lying over the night; weather thickens it. The volumetric march tears it into drifting banks near
        // the ground; past the bank the world dissolves into it (and is not drawn at all). Its colour is the sky's own
        // at the horizon, a cool blue-grey by day, gold-grey at dawn, deep blue at night.
        float dawn = (float) Math.exp(-Math.pow((hour - 6.6) / 1.4, 2)), dusk = (float) Math.exp(-Math.pow((hour - 19.4) / 1.3, 2));
        float wfog = weather == null ? 0 : weather.fog;
        float mist = 0.0032f + 0.0085f * dawn + 0.005f * dusk + 0.0045f * night + wfog * 0.012f;
        boolean indoorsScene = sceneGame == null;
        float bankStart = indoorsScene ? 1e9f : 105 - 45 * dawn - 22 * dusk - 30 * night - 45 * wfog;
        float bankEnd = indoorsScene ? 2e9f : bankStart + 70 + 20 * (1 - night);
        fogFar = indoorsScene ? 400 : bankEnd;
        // the light itself: golden while the sun is low, moonlit blue at night, white by day
        float tr = 1, tg = 1 - 0.10f * low * (1 - night), tb = 1 - 0.22f * low * (1 - night);
        tr -= 0.18f * night; tg -= 0.08f * night;
        cmd.env(sx, sy, sz, daylight, (float) time, weather==null?0:weather.rain, weather==null?0:weather.wetness, weather==null?0:weather.snowCover, weather==null?0.1f:weather.overcast, weather==null?0:weather.lightning, fogR, fogG, fogB, indoorsScene ? 1f / 300f : mist, bankStart, bankEnd, indoorsScene ? 0 : 0.8f, 0, tr * (sceneGame==null?1:sceneGame.timeSystem.tintR), tg * (sceneGame==null?1:sceneGame.timeSystem.tintG), tb * (sceneGame==null?1:sceneGame.timeSystem.tintB), (0.004f + 0.006f * night + wfog * 0.012f) * (sceneGame==null?1:sceneGame.timeSystem.fogScale), Float.NaN, 0, 0, 0.05f + 0.03f * low);
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
        for(var globe:g.magic.darkness)n=putSmoke(n,globe.x(),globe.y(),globe.z(),globe.r(),0.7f,0.015f,0.018f,0.03f,0,0.04f,0.3f,111+n);
        float mist=(1-rpg.ai.Stealth.daylight(g.state.hourOfDay()))*0.6f+g.timeSystem.weather.wetness*0.4f;
        for(var fog:g.timeSystem.fogVolumes)if(mist>0.05f && Math.hypot(fog.x()-camX,fog.z()-camZ)<45)
            n=putSmoke(n,fog.x(),fog.y(),fog.z(),fog.radius(),fog.density()*mist,fog.red(),fog.green(),fog.blue(),0,0.03f,0.4f,71+n);
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
    /** RPG_PASSES_OFF=gtao,ssr,volumetrics,taa,bloom: passes left out, for review captures that isolate one. */
    private static final int PASSES_OFF = passesOff(System.getenv("RPG_PASSES_OFF"));
    private static int passesOff(String names) {
        if (names == null) return 0;
        int off = 0;
        for (String n : names.split(",")) off |= switch (n.trim()) {
            case "gtao" -> AstrattoNative.PASS_GTAO; case "ssr" -> AstrattoNative.PASS_SSR; case "volumetrics" -> AstrattoNative.PASS_VOLUMETRICS;
            case "taa" -> AstrattoNative.PASS_TAA; case "bloom" -> AstrattoNative.PASS_BLOOM; default -> 0;
        };
        return off;
    }
    private void lens(Rpg g) {
        boolean on = g.cfg.motionBlur || g.cfg.depthOfField;
        int passes = AstrattoNative.PASS_GTAO | AstrattoNative.PASS_SSR | AstrattoNative.PASS_VOLUMETRICS | AstrattoNative.PASS_TAA | AstrattoNative.PASS_BLOOM
                | (g.cfg.motionBlur ? AstrattoNative.PASS_MOTION_BLUR : 0) | (g.cfg.depthOfField ? AstrattoNative.PASS_DEPTH_OF_FIELD : 0);
        passes &= ~PASSES_OFF;
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
        var weather=g.timeSystem.weather;
        // plants pressed over where bodies have just walked, springing back (gamefixes: foliage reacts to bodies)
        float[] push=g.foliage.pushBuffer();
        cmd.foliage(weather.windX,weather.windZ,(float)g.time,weather.gust,20,32,weather.snowCover,push,g.foliage.pushers(camX,camZ,push));
        // GPU precipitation stops on geometry, so rain remains visible through a window.
        if(weather.rain>0.02f)
            cmd.emitter(0x400000007L,7,camX,camY+8,camZ,0.025f,180*weather.rain,6,0.7f,0.6f,0.75f,0.9f,weather.windX,-18,weather.windZ);
        // lights: lamps by night, fires, the furnace, spells in flight
        if (daylight < 0.6f) lampLights(g.vox);
        for (WorldObject o : g.objects.all()) if (o instanceof Fire f) {
            float fl = 0.97f + 0.03f * (float) Math.sin(g.time * 3 + f.id);
            cmd.pointLight(f.x, f.y + 0.6f, f.z, 7, 4.5f * fl, 2.2f * fl, 0.6f * fl, 0);
        }
        cmd.pointLight(DevMap.FURNACE_AT[0] + 0.5f, DevMap.FLOOR + 0.4f, DevMap.FURNACE_AT[1] + 1.0f, 4, 3f, 1.4f, 0.4f, 0);
        dungeonLights(g);
        smoke(g);
        lens(g);
        chemicalLights(g, dt);
        // the level-up's Holy Light: a warm gold light at the beam, brightest as it bursts
        float lt = g.fx.light();
        if (lt >= 0 && lt < Fx.LIGHT_TIME) {
            float k = lt < Fx.LIGHT_RISE ? lt / Fx.LIGHT_RISE : Math.max(0, 1 - (lt - Fx.LIGHT_BURST) / (Fx.LIGHT_TIME - Fx.LIGHT_BURST));
            k = Math.min(1, k) * (lt > Fx.LIGHT_BURST - 0.06f && lt < Fx.LIGHT_BURST + 0.25f ? 1.6f : 1);
            cmd.pointLight(g.player.x, g.player.y + 1.6f, g.player.z, 10, 7.5f * k, 5.6f * k, 2.4f * k, 0);
        }
        for (Actor actor : g.actors()) if ((g.chemistry.state(actor.id) & AstrattoNative.CHEM_BURNING) != 0) {
            g.chemistry.center(actor, burnPosition);
            // A bounded real light at the flame, with occlusion, never a lingering source under the last body position.
            cmd.pointLight(burnPosition[0],burnPosition[1]+0.15f,burnPosition[2],4,2.8f,1.2f,0.25f,AstrattoNative.LIGHT_SHADOW);
        }
        int burning = 0;
        for (var chemical : g.chemistry.active) if ((chemical.state() & AstrattoNative.CHEM_BURNING) != 0 && Math.hypot(chemical.x() - camX, chemical.z() - camZ) < 35 && burning++ < 20) {
            Actor receiver = g.actor(chemical.id());
            if(receiver!=null)g.chemistry.center(receiver,burnPosition);
            float x = receiver == null ? chemical.x() : burnPosition[0], y = receiver == null ? chemical.y() : burnPosition[1], z = receiver == null ? chemical.z() : burnPosition[2];
            // Flame particles follow the moving body emitter on the GPU; smoke drifts in world space.
            cmd.emitter(chemical.id(),receiver==null?0:AstrattoNative.EMITTER_ATTACHED_FIRE,x,y-0.15f,z,0.22f,48,0.1f,receiver == null ? 0.8f : 0.45f,5.2f,1.7f,0.3f,0,0.8f,0);
            cmd.emitter(chemical.id()+33,1,x,y+0.5f,z,0.18f,12,0.2f,1.7f,0.22f,0.2f,0.19f,0,0.9f,0);
            // a burning body sheds ash: pale flakes and black bits of burnt cloth, lifted by the heat and drifting down
            if (receiver != null) {
                cmd.emitter(chemical.id()+71,8,x,y+0.4f,z,0.035f,14,0.3f,2.4f,0.62f,0.6f,0.57f,0,1.6f,0);
                cmd.emitter(chemical.id()+72,8,x,y+0.2f,z,0.03f,10,0.28f,2.0f,0.08f,0.07f,0.06f,0,1.2f,0);
            }
        }
        for (WorldObject object : g.objects.all()) if (object instanceof Fire fire) {
            cmd.emitter(fire.id,0,fire.x,fire.y+0.2f,fire.z,0.2f,28,0.15f,0.85f,5,1.8f,0.3f,0,0.8f,0);
            cmd.emitter(fire.id+7,1,fire.x,fire.y+0.6f,fire.z,0.16f,10,0.15f,1.8f,0.2f,0.2f,0.21f,0,0.7f,0);
        }
        for (var visual : g.chemistry.visuals) cmd.emitter(visual.id(),visual.kind(),visual.x(),visual.y(),visual.z(),visual.radius(),visual.rate(),0.08f,visual.life(),visual.r(),visual.green(),visual.b(),0,0.8f,0);
        for(var ripple:g.chemistry.ripples)cmd.ripple(ripple.x(),ripple.z(),ripple.radius(),ripple.strength());
        // charged water: a cold blue-white light jumping about over the surface (a new place and strength every few
        // frames, never steady), and sparks skittering out across it, fading as the charge spends itself
        for (var c : g.chemistry.charges) {
            float life = (float) Math.max(0, Math.min(1, (c.until() - g.time) / Math.max(0.1, c.until() - c.since())));
            long beat = (long) (g.time * 24);
            for (int k = 0; k < 2; k++) {
                long h = beat * 0x9E3779B97F4A7C15L + k * 0x632BE59BD9B4E019L + (long) (c.since() * 1000);
                h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
                float ang = (h & 1023) / 1023f * 6.283f, rad = ((h >>> 10) & 1023) / 1023f * c.radius() * 0.8f, flick = 0.35f + ((h >>> 20) & 1023) / 1023f * 0.65f;
                float px = c.x() + (float) Math.cos(ang) * rad, pz = c.z() + (float) Math.sin(ang) * rad;
                float k2 = flick * life * 1.1f;
                cmd.pointLight(px, c.y() + 0.12f, pz, c.radius(), 0.45f * k2, 0.75f * k2, 1.5f * k2, 0);
            }
            cmd.emitter((long) (c.since() * 1000) ^ 0x7700000000L, 5, c.x(), c.y() + 0.04f, c.z(), 0.07f, 140 * life, c.radius() * 0.75f, 0.16f, 1.6f, 2.2f, 3.6f, 0, 0.4f, 0);
        }
        foliage(g);
        fences(g);
        // characters
        Actor p = g.player;
        shadowProxy(g,p);
        if (p.alive && p.morphT > 0) animal(g.magic.form(p),dt,g.time);
        else if (p.alive && p.reacting && p.ragdoll != 0) {
            var body = g.lastBodies.ragdoll(p.ragdoll); if (body != null) ragdoll(body, p);
        } else if (p.alive) {
            human(p, p.look, dt, firstPerson, null);
        } else if (g.death.ragdoll != 0) {
            // the player's own body, fallen (gamefixes: the death is watched), until it becomes the grave
            Snapshots.Ragdoll rd = g.lastBodies.ragdoll(g.death.ragdoll);
            if (rd != null) ragdoll(rd, p);
        }
        for (Npc n : g.npcs) {
            if (n.loaded) shadowProxy(g,n);
            if (n.ragdoll != 0) {
                // a body in Sydney's hands - reeling from a blow, or dead - is drawn as the engine has it
                Snapshots.Ragdoll rd = g.lastBodies.ragdoll(n.ragdoll);
                if (rd != null) { ragdoll(rd, n); continue; }
            }
            if (n.alive && n.loaded) {
                if (n.morphT > 0) animal(g.magic.form(n),dt,g.time);
                else human(n, n.look, dt, false, sleeping(g, n));
            }
        }
        for (Actor d : g.dummies) dummy(d, g.time);
        holyLight(g);
        // the monsters (gamefixes: they were simulated, heard and fought, but never drawn - the invisible imps and goblins)
        if (g.monsters != null) for (rpg.monster.Monster mo : g.monsters.all()) if ((mo.alive ? mo.loaded : mo.ragdoll != 0) && mo.distTo(camX, camZ) < 90) monster(mo, dt);
        if (g.animals != null) for (rpg.animal.Animal an : g.animals.all()) if (an.loaded && (an.alive || an.carcass() && !an.butchered) && an.distTo(camX, camZ) < 70) animal(an, dt, g.time);
        // objects
        for (WorldObject o : g.objects.all()) if (Math.hypot(o.x - camX, o.z - camZ) < fogFar + 6) object(g, o, dt);
        if (g.interact.focus != null && g.interact.focus != (Object) g.player) {
            var f = g.interact.focus;
            float bob = (float) Math.sin(g.time * 4) * 0.05f;
            m.identity().translate(f.ax(), f.ay() + f.focusRadius() + 0.35f + bob, f.az()).rotateY((float) g.time * 2).rotateZ(0.785f).rotateX(0.615f).scale(0.12f, 0.12f, 0.12f);
            put(box, 0, 1f, 0.85f, 0.2f, 1.4f, 1, 0.4f, 0);
        }
        // spells: each in flight by its tier and element, each landing, and the casts with no flight (SpellFx)
        if (spellFx == null || spellFxOwner != g) { spellFx = new SpellFx(g.fx); spellFxOwner = g; }
        spellFx.frame(g.time);
        for (CombatSystem.Projectile pr : g.combat.projectiles) {
            if (pr.spell != null) spellFx.flight(spellDraw, pr.id, pr.spell, pr.x, pr.y, pr.z, pr.vx, pr.vy, pr.vz, dt);
            else projectile(pr);
        }
        spellFx.landings(spellDraw);
        spellFx.lingering(spellDraw);
        for (Actor a : g.actors()) if (a.castSpell != null && a.alive) {
            boolean held = g.time - a.handAt < 0.3;
            spellFx.casting(spellDraw, a.id, a.castSpell, a.castAt, a.x, a.y, a.z, a.yaw, held ? a.hand[0] : a.x - (float) Math.sin(a.yaw) * 0.4f, held ? a.hand[1] : a.chestY(), held ? a.hand[2] : a.z - (float) Math.cos(a.yaw) * 0.4f);
        }
        guideCount = 0;
        throwGuide(g);
        // arrows and blades lodged in bodies, where they went in
        for (Actor a : g.actors()) for (CombatSystem.Stuck st : a.stuck) {
            float[] w = st.world(g, a);
            float len = CombatSystem.ammoLength(st.stack().def);
            held(st.stack().def, new float[]{w[0] - w[3] * len, w[1] - w[4] * len, w[2] - w[5] * len}, new float[]{w[3], w[4], w[5]}, a.yaw, false);
        }
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
        // the frame's particles and the throw guide's points go in one list (gamefixes: sent apart, the particles replaced
        // the guide whenever anything else was in the air, so the arc vanished in a fight)
        int n = g.fx.pack();
        if (guideCount > 0) {
            if (merged.length < (n + guideCount) * 12) merged = new float[(n + guideCount) * 12];
            System.arraycopy(g.fx.data(), 0, merged, 0, n * 12);
            System.arraycopy(guide, 0, merged, n * 12, guideCount * 12);
            cmd.particles(merged, n + guideCount);
        } else if (n > 0) cmd.particles(g.fx.data(), n);
    }

    private int lastH = 1080;
    private boolean firstFrame = true;

    public NativeRenderer.Stats frame(int w, int h) {
        lastH = h;
        last = r.frame(cmd, w, h);
        // the figure upload cap is a frame's: every frame ends here (the title and the creator never pass through scene)
        uploadsThisFrame = 0;
        if (firstFrame && last != null) {
            System.out.printf("[Load] first rendered frame %.2fs%n", last.cpuMs / 1000);
            firstFrame = false;
        }
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

    private void shadowProxy(Rpg g, Actor actor) {
        if (actor.distTo(camX,camZ)>24 || actor.morphT>0) return;
        long handle=actor==g.player && g.death.active?g.death.ragdoll:actor.ragdoll;
        var body=handle==0?null:g.lastBodies.ragdoll(handle);
        if (body!=null) {
            cmd.shadowBody(body.comX,body.comY-0.25f,body.comZ,actor.radius*1.5f,0.5f);
        } else if (actor instanceof Npc npc && npc.asleep()) {
            cmd.shadowBody(actor.x,actor.y+0.4f,actor.z,actor.radius*1.5f,0.5f);
        } else if (actor.alive) cmd.shadowBody(actor.x,actor.y,actor.z,actor.radius,actor.height*(actor.crouched?Body.CROUCH_HEIGHT:1));
    }

    private static final class FlameLight { long id; float x,y,z,weight; boolean wanted; }
    private final List<FlameLight> flameLights = new ArrayList<>();
    private final List<Snapshots.Chemical> flameCandidates = new ArrayList<>(), flameNeighbors = new ArrayList<>();
    private List<Snapshots.Chemical> flameState;
    private Rpg flameOwner;
    private double nextFlameSelection;
    private float flameCamX, flameCamZ;
    private void chemicalLights(Rpg g, float dt) {
        if (flameOwner!=g) { flameLights.clear();flameState=null;flameOwner=g; }
        // Chemistry changes at four Hz; camera travel can also require a new local selection.
        if (flameState!=g.chemistry.active || g.time>=nextFlameSelection || Math.hypot(camX-flameCamX,camZ-flameCamZ)>2) {
            flameState=g.chemistry.active;nextFlameSelection=g.time+0.25;flameCamX=camX;flameCamZ=camZ;
            flameCandidates.clear();flameNeighbors.clear();
            for (var state : flameState) if ((state.state() & AstrattoNative.CHEM_BURNING) != 0
                    && g.actor(state.id()) == null && Math.hypot(state.x()-camX,state.z()-camZ)<32) flameCandidates.add(state);
            flameCandidates.sort(java.util.Comparator.<Snapshots.Chemical>comparingDouble(source ->
                    Math.hypot(source.x()-camX,source.z()-camZ) - (flameLights.stream().anyMatch(light -> light.id==source.id()) ? 2 : 0)).thenComparingLong(source -> source.id()));
            for (var light:flameLights)light.wanted=false;
            // Keep six stable sources; adjacent burning cells share a source instead of stacking lights.
            for (var source:flameCandidates) {
                if (flameNeighbors.size()>=6)break;
                boolean adjacent=false;
                for (var other:flameNeighbors) if (Math.abs(source.x()-other.x())<2.5f && Math.abs(source.y()-other.y())<2.5f && Math.abs(source.z()-other.z())<2.5f) { adjacent=true;break; }
                if(adjacent)continue;
                flameNeighbors.add(source);
                FlameLight light=flameLights.stream().filter(candidate -> candidate.id==source.id()).findFirst().orElse(null);
                if(light==null && flameLights.size()<8) { light=new FlameLight();light.id=source.id();flameLights.add(light); }
                if(light!=null) { light.wanted=true;light.x=source.x();light.y=source.y();light.z=source.z(); }
            }
        }
        float blend=1-(float)Math.exp(-Math.min(dt,0.1f)*8);
        for(var light:flameLights) {
            light.weight+=((light.wanted?1:0)-light.weight)*blend;
            if(light.weight>0.01f)cmd.pointLight(light.x,light.y+0.5f,light.z,3,3*light.weight,1.2f*light.weight,0.2f*light.weight,0);
        }
        flameLights.removeIf(light -> !light.wanted && light.weight<0.01f);
    }

    private record PlantMesh(int version,int mesh) {}
    private final Map<Integer,PlantMesh> plantMeshes=new HashMap<>();
    private rpg.world.Foliage plantOwner;
    private int plantEpoch;
    private void foliage(Rpg g) {
        if(plantOwner!=g.foliage || plantEpoch!=g.foliage.epoch) {
            for(var entry:plantMeshes.values())if(entry.mesh!=0)r.release(AstrattoNative.RES_MESH,entry.mesh);
            plantMeshes.clear();plantOwner=g.foliage;plantEpoch=g.foliage.epoch;
        }
        int builds=0;
        for(var chunk:g.foliage.chunks.values()) {
            if(Math.hypot(chunk.x+8-camX,chunk.z+8-camZ)>48)continue;
            PlantMesh cached=plantMeshes.get(chunk.key);
            if((cached==null || cached.version!=chunk.version) && builds++<1) {
                int mesh=upload(FoliageModels.chunk(chunk));
                if(cached!=null && cached.mesh!=0)r.release(AstrattoNative.RES_MESH,cached.mesh);
                cached=new PlantMesh(chunk.version,mesh);plantMeshes.put(chunk.key,cached);
            }
            if(cached==null)continue;
            m.identity().translate(chunk.x,DevMap.FLOOR,chunk.z);
            put(cached.mesh,AstrattoNative.MODEL_FOLIAGE|AstrattoNative.MODEL_NO_SHADOW,1,1,1,0,1,0.95f,0);
        }
    }

    // ------------------------------------------------------------------ fences (gamefixes: a model, not a block)
    private record FenceMesh(long hash, int[] meshes, Props.Layer[] info) {}
    private final Map<Integer, FenceMesh> fenceMeshes = new HashMap<>();
    private final Map<Integer, Double> fenceChecked = new HashMap<>();
    private int fenceWorld = -1;

    /** What borders a fence cell, for its model: 3 fence, 2 a wall (anything solid to butt a rail against), 0 open. */
    private static int fenceSide(Rpg g, int x, int y, int z) {
        int b = g.vox.block(x, y, z);
        if (b == rpg.world.Blocks.FENCE) return 3;
        return g.vox.solid(x, y, z) && !rpg.world.Blocks.isLeaves(b) && !rpg.world.Blocks.glass(b) ? 2 : 0;
    }

    /** Each nearby column's fences: rebuilt when the fence cells in it change (each column checked about once a second). */
    private void fences(Rpg g) {
        if (fenceWorld != System.identityHashCode(g.vox)) {
            for (var f : fenceMeshes.values()) for (int id : f.meshes()) if (id != 0) r.release(AstrattoNative.RES_MESH, id);
            fenceMeshes.clear(); fenceChecked.clear(); fenceWorld = System.identityHashCode(g.vox);
        }
        int y0 = DevMap.FLOOR - 1, y1 = DevMap.FLOOR + 4, built = 0;
        for (int cx = 0; cx * 16 < DevMap.SX; cx++) for (int cz = 0; cz * 16 < DevMap.SZ; cz++) {
            if (Math.hypot(cx * 16 + 8 - camX, cz * 16 + 8 - camZ) > 80) continue;
            int key = cx + cz * 64;
            FenceMesh fm = fenceMeshes.get(key);
            if ((fm == null || clock - fenceChecked.getOrDefault(key, -99.0) > 1) && built < 2) {
                fenceChecked.put(key, clock);
                long hash = 1469598103934665603L;
                for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) for (int y = y0; y <= y1; y++)
                    if (g.vox.block(x, y, z) == rpg.world.Blocks.FENCE) hash = (hash ^ (x * 131071L + z * 8191L + y + (long) (g.chemistry.fenceChar(x, y, z) * 8) * 1000003L)) * 1099511628211L;
                if (fm == null || fm.hash() != hash) {
                    built++;
                    if (fm != null) for (int id : fm.meshes()) if (id != 0) r.release(AstrattoNative.RES_MESH, id);
                    Props.Layer[] ls = Props.fences(cx * 16, cz * 16, y0, y1, (x, y, z) -> fenceSide(g, x, y, z), g.chemistry::fenceChar);
                    int[] ids = new int[ls.length];
                    for (int i = 0; i < ls.length; i++) { Meshes.Boxes b = ls[i].boxes(); ids[i] = b.count() == 0 ? 0 : r.mesh(b.vertices(), b.count()); }
                    fm = new FenceMesh(hash, ids, ls);
                    fenceMeshes.put(key, fm);
                }
            }
            if (fm == null) continue;
            m.identity().translate(cx * 16, y0, cz * 16);
            for (int i = 0; i < fm.meshes().length; i++) if (fm.meshes()[i] != 0) put(fm.meshes()[i], 0, 1, 1, 1, fm.info()[i].emissive(), 1, fm.info()[i].rough(), fm.info()[i].metal());
        }
    }

    private void put(int mesh, int rgb, float shade, float rough, float metal) {
        put(mesh, 0, ((rgb >> 16) & 255) / 255f * shade, ((rgb >> 8) & 255) / 255f * shade, (rgb & 255) / 255f * shade, 0, 1, rough, metal);
    }

    private void put(int mesh, int flags, float r, float g, float b, float emissive, float light, float rough, float metal) {
        if (mesh == 0) return;
        System.arraycopy(m.m, 0, m16, 0, 16);
        cmd.model(mesh, flags, m16, r*charTint, g*charTint, b*charTint, 1, emissive, light, rough, metal);
    }

    /** How much bigger than a person's grip what a hand holds is drawn (a troll's club is a troll's size). */
    private float heldScale = 1;

    /** A held item: its own mesh, from the grip along a direction, flat across the body's right (or facing forward). */
    private void held(ItemDef d, float[] grip, float[] dir, float yaw, boolean flatToFace) {
        if (d == null) return;
        float[] ref = flatToFace ? fwd(yaw) : right(yaw);
        m.frame(grip, dir, ref);
        if (heldScale != 1) m.scale(heldScale, heldScale, heldScale);
        if (d.family == Family.ARROW || d.family == Family.BOLT) {
            // a nocked arrow is one arrow, not the bundle the inventory holds
            int mesh = mesh("arrow:" + d.color + ":" + d.family, () -> Meshes.arrow(d.color, d.family == Family.BOLT));
            put(mesh, 0xFFFFFF, 1, 0.6f, 0.1f);
            return;
        }
        drawItem(d, 0);
    }

    /** Held bows share the inventory geometry; the string joins the limb tips to the actual drawing hand. */
    private void heldBow(ItemDef d, float[] grip, float[] dir, float yaw, float draw, float[] drawingHand) {
        int[] meshes = itemMeshes.computeIfAbsent(d.id + ":limbs", key -> {
            float[][] vertices = VoxMesh.build(HiResItems.bow(d, false));
            return new int[]{upload(vertices[0]), upload(vertices[1])};
        });
        m.frame(grip, dir, fwd(yaw + (float) Math.PI));
        float[] top = bowPoint(7, 38, 0), bottom = bowPoint(7, -38, 0);
        float[] nock = draw > 0 ? drawingHand : bowPoint(7, 0, 0);
        put(meshes[0], 0, 1, 1, 1, 0, 1, 0.3f, 0.9f);
        put(meshes[1], 0, 1, 1, 1, 0, 1, 0.82f, 0);
        bowString(top, nock); bowString(bottom, nock);
    }
    private float[] bowPoint(float x, float y, float z) {
        x *= ItemModels.VOX; y *= ItemModels.VOX; z *= ItemModels.VOX;
        return new float[]{m.m[0] * x + m.m[4] * y + m.m[8] * z + m.m[12],
                m.m[1] * x + m.m[5] * y + m.m[9] * z + m.m[13], m.m[2] * x + m.m[6] * y + m.m[10] * z + m.m[14]};
    }
    private void bowString(float[] a, float[] b) {
        float[] delta = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        float length = (float) Math.sqrt(delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2]);
        m.frame(new float[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2}, delta, new float[]{1, 0, 0}).scale(0.003f, length, 0.003f);
        put(box, 0xE8E0C8, 1, 0.85f, 0);
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
        int[][] last = shown.get(a.id);
        // (gamefixes: fights lagged) a body freshly marked again and again in a fight is repainted at most a few times a
        // second - each new mark was a new figure, painted, built and uploaded - and the uploads of a frame are capped:
        // until its turn the figure is drawn as it was a moment ago
        if (job == null && last != null && clock - rebuiltAt.getOrDefault(a.id, -9.0) < (a.alive ? 0.4 : 1.5)) return last;   // (the dead go on bleeding: repainted less often)
        if (job == null) {
            rebuiltAt.put(a.id, clock);
            Skin.Armor armor = armorOf(a);
            Appearance copy = look.copy();
            var blood = a.blood.any() ? a.blood.snapshot() : null;
            var injuries=a.injuries.snapshot();int severity=a.injuries.severity(a);
            job = builder.submit(() -> { Skin skin=Skin.paint(copy,armor,blood);skin.injure(injuries,severity);return CharModel.build(skin); });
            building.put(key, job);
        }
        int[][] old = last;
        if (!job.isDone() && old != null) return old;
        if (old != null && uploadsThisFrame >= 1) return old;
        uploadsThisFrame++;
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

    private final java.util.Map<Long, Double> rebuiltAt = new java.util.HashMap<>();
    private int uploadsThisFrame;

    /** Whether the figure for an actor as they look now has been built (the gallery waits on it; play shows the old one meanwhile). */
    public boolean figureReady(Actor a) { return a.look != null && figures.containsKey(figureKey(a, a.look)); }

    private static Skin.Armor armorOf(Actor a) { return new Skin.Armor(a.eq.def(Slot.HEAD), a.eq.def(Slot.BODY), a.eq.def(Slot.LEGS), a.eq.def(Slot.FEET), a.eq.def(Slot.HANDS)); }

    /** A figure's identity without building a string a frame: the look's numbers and the worn pieces, hashed to 64 bits. */
    private static long figureKey(Actor a, Appearance look) {
        long h = 0x9E3779B97F4A7C15L;
        for (int v : new int[]{look.female ? 1 : 0, look.bodyType, look.skin, look.hair, look.hairColor, look.beard, look.beardColor, look.top, look.topColor, look.bottom, look.bottomColor, look.shoes, look.shoesColor, look.eyeColor})
            h = (h ^ v) * 0x100000001B3L + 0x632BE59BD9B4E019L;
        for (Slot s : ARMOR_SLOTS) { ItemDef d = a.eq.def(s); h = (h ^ (d == null ? 0 : d.id.hashCode())) * 0x100000001B3L + 31; }
        if (look.monster != null) h = (h ^ look.monster.hashCode()) * 0x100000001B3L + 97;
        // blood is this body's own: a bloodied figure is nobody else's (a clean one is shared by everyone who looks alike)
        if (a.blood.any()) h = (h ^ a.id * 0x9E3779B97F4A7C15L ^ (long) a.blood.version << 40) * 0x100000001B3L + 17;
        if(a.injuries.version(a)!=0)h=(h ^ a.id ^ (long)a.injuries.version(a)<<32)*0x100000001B3L;
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

    private record DamageKey(long actor,ItemModels.Model base,rpg.combat.Gore.Part part) {}
    private record DamageBuild(ItemModels.Model model,float[][] vertices) {}
    private static final class DamageMesh {
        int version;double used;ItemModels.Model shown;java.util.concurrent.Future<DamageBuild> pending;
    }
    private final LinkedHashMap<DamageKey,DamageMesh> damagedParts=new LinkedHashMap<>(32,0.75f,true);
    private rpg.animal.Animal drawnAnimal;
    private ItemModels.Model injuredPart(rpg.animal.Animal animal,ItemModels.Model base,rpg.combat.Gore.Part part) {
        if(animal==null || base==null || animal.injuries.version(animal)==0 || !animal.injuries.affects(VoxelInjuries.part(part)))return base;
        var key=new DamageKey(animal.id,base,part);var state=damagedParts.computeIfAbsent(key,k->new DamageMesh());state.used=sceneGame.time;
        int version=animal.injuries.version(animal);
        if(state.version!=version) {
            if(state.pending!=null)state.pending.cancel(false);
            var cuts=animal.injuries.snapshot();int severity=animal.injuries.severity(animal);state.version=version;
            state.pending=builder.submit(()->{var model=VoxelInjuries.carve(base,part,cuts,severity);return new DamageBuild(model,model==base?null:VoxMesh.build(model));});
        }
        if(state.pending!=null && state.pending.isDone())try {
            var ready=state.pending.get();state.pending=null;
            if(state.shown!=null && state.shown!=base)releasePart(state.shown);
            state.shown=ready.model;
            if(ready.vertices!=null)partMeshes.put(ready.model,new int[]{upload(ready.vertices[0]),upload(ready.vertices[1])});
        }catch(InterruptedException | java.util.concurrent.ExecutionException failure){throw new IllegalStateException("Animal injury mesh",failure);}
        if(damagedParts.size()>96) {
            var iterator=damagedParts.entrySet().iterator();while(iterator.hasNext() && damagedParts.size()>96) {
                var entry=iterator.next();if(sceneGame.time-entry.getValue().used<0.5)continue;
                var stale=entry.getValue();if(stale.pending!=null)stale.pending.cancel(false);
                if(stale.shown!=null && stale.shown!=entry.getKey().base)releasePart(stale.shown);iterator.remove();
            }
        }
        return state.shown==null?base:state.shown;
    }
    private void releasePart(ItemModels.Model model) { int[] meshes=partMeshes.remove(model);if(meshes!=null)for(int mesh:meshes)if(mesh!=0)r.release(AstrattoNative.RES_MESH,mesh); }

    /** Which of the animal being drawn's parts are gone (set per animal by animal()). */
    private boolean[] animalLost = new boolean[rpg.combat.Gore.Part.values().length];

    /** An animal's part, unless it has been taken off - then the raw stump where it joined. */
    private void limb(rpg.combat.Gore.Part which, ItemModels.Model p, float vs, float jx, float jy, float jz, float rx, float ry, float rz) {
        if (p == null) return;
        if (!animalLost[which.ordinal()]) { part(injuredPart(drawnAnimal,p,which), vs, jx, jy, jz, rx, ry, rz); return; }
        var vm = p.vox();
        float w = Math.max(1.2f, Math.min(vm.sx, vm.sz) * 0.9f) * vs;
        m.set(body).translate(jx * vs, jy * vs, jz * vs);
        if (which == rpg.combat.Gore.Part.HEAD || which == rpg.combat.Gore.Part.TAIL) m.rotateX((float) Math.PI / 2);
        m.scale(w, 1, w);
        put(mesh("wound", Meshes::wound), 0xFFFFFF, 1, 0.55f, 0);
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
            var model = !a.alive && p.part() == rpg.combat.Gore.Part.HEAD ? AnimalModels.of(a.species, a.variant).headClosed : p.model();
            model=injuredPart(a,model,p.part());
            var vm = model.vox();
            part(model, vs, -(vm.sx / 2f - model.gx()), -(vm.sy / 2f - model.gy()), -(vm.sz / 2f - model.gz()), 0, 0, 0);
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
    private float charTint=1;
    private final float[] burnPosition=new float[3];
    private void animal(rpg.animal.Animal a,float dt,double time) {
        float previous=charTint;charTint=1-a.charred*0.86f;var priorAnimal=drawnAnimal;drawnAnimal=a;
        try { animalModel(a,dt,time); } finally { charTint=previous;drawnAnimal=priorAnimal; }
    }
    private void animalModel(rpg.animal.Animal a, float dt, double time) {
        if(a.morphT>0 && sceneGame!=null) { animal(sceneGame.magic.form(a),dt,time);return; }
        var sp = a.species;
        var f = AnimalModels.of(sp, a.variant);
        float vs = AnimalModels.PX * a.scale();
        if (a.ragdoll != null && ragdollAnimal(a, vs)) return;
        if(!a.alive && !a.restPose.isEmpty()) {
            for(var entry:a.restPose.entrySet()) {
                if(gore!=null && gore.lost(a,entry.getKey()))continue;
                ItemModels.Model model=switch(entry.getKey()) { case TORSO->f.body;case HEAD->f.headClosed;case TAIL->f.tail;case ARM_L,ARM_R->f.bird?f.wing:f.legFront;case LEG_L,LEG_R->f.bird?f.legFront:f.legBack; };
                if(model==null)continue;model=injuredPart(a,model,entry.getKey());entry.getValue().matrix(body.m);var vm=model.vox();
                part(model,vs,-(vm.sx/2f-model.gx()),-(vm.sy/2f-model.gy()),-(vm.sz/2f-model.gz()),0,0,0);
            }
            return;
        }
        boolean dead = !a.alive;
        float speed = dead ? 0 : (float) Math.hypot(a.vx, a.vz);
        if (dead) { a.amp = 0; a.flap = 0; }
        // the gait: one cycle per two strides, its swing growing with speed
        float legM = Math.max(2, f.legH) * vs;
        float cadence = f.hopper ? Math.min(2, 1.3f + speed * 0.12f) : Math.min(3.2f, speed / (legM * 2.4f) * (f.bird ? 1.6f : 1));
        if (speed > 0.06f) a.gait += cadence * (float) Math.PI * 2 * dt;
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
        if (!dead) {
            a.headPitch += (headWant - a.headPitch) * Math.min(1, dt * 5);
            a.headYaw += (yawWant - a.headYaw) * Math.min(1, dt * 4);
        }
        // a strike: crouch in the wind-up, lunge through the active window
        float lunge = 0, crouch = 0;
        if (a.cs.phase == Actor.Combat.Phase.WINDUP) crouch = Math.min(1, a.cs.t / Math.max(0.05f, a.cs.windup));
        else if (a.cs.phase == Actor.Combat.Phase.ACTIVE) lunge = (float) Math.sin(Math.PI * Math.min(1, a.cs.t / Math.max(0.05f, a.cs.active)));
        else if (a.cs.phase == Actor.Combat.Phase.RECOVER) lunge = Math.max(0, 1 - a.cs.t / Math.max(0.05f, a.cs.recover)) * 0.4f;
        float rear = !dead && a.rearing ? 0.5f + 0.5f * (float) Math.sin(Math.min(1, (float) (time - a.goalSince)) * Math.PI / 2) : 0;
        if (!dead && sp == Species.HORSE && a.cs.busy()) {
            float attackRear = a.cs.phase == Actor.Combat.Phase.WINDUP ? crouch * 0.8f
                    : a.cs.phase == Actor.Combat.Phase.ACTIVE ? 0.8f - lunge * 0.35f
                    : 0.45f * (1 - Math.min(1, a.cs.t / Math.max(0.05f, a.cs.recover)));
            rear = Math.max(rear, attackRear);
        }
        // the body's frame
        float hop = f.hopper ? Math.max(0, (float) Math.sin(ph)) : 0;
        float bob = f.hopper ? hop * hop * 2.6f * Math.min(1, amp * 2.5f) : Math.abs((float) Math.sin(ph)) * 0.6f * amp;
        // a straight leg swung through an arc is shorter at its ends: the body rides down to keep the feet on the ground
        float reachDrop = f.bird || f.hopper ? 0 : f.legH * (1 - (float) Math.cos(amp * 0.95f)) * 0.8f;
        float standY = f.bodyY - crouch * 1.2f + bob - reachDrop;
        float lyingY = dead ? f.bw * 0.5f : f.bird ? f.bh * 0.45f : f.bh * 0.5f;
        float y = standY + (lyingY - standY) * lie;
        if (a.goal == rpg.animal.Animal.Goal.GROOM && !dead) { a.headYaw = 0.6f * (float) Math.sin(time * 7); a.headPitch = 0.35f + 0.12f * (float) Math.sin(time * 14); }
        float pitch = (f.bird ? 0.05f * (float) Math.sin(ph * 2) * amp : running ? 0.08f * (float) Math.sin(ph) : 0) - crouch * 0.08f + lunge * 0.12f;
        if (f.hopper && amp > 0.05f) pitch += 0.12f * (float) Math.sin(ph) * Math.min(1, amp * 3);
        body.identity().translate(a.x, a.y, a.z).rotateY(a.yaw);
        if (rear > 0) {
            // up on the hind legs: the body turns about the haunches
            body.translate(0, f.bodyY * vs, f.legB[2] * vs).rotateX(rear * (sp == Species.BROWN_BEAR ? 1.05f : 0.7f)).translate(0, (y - f.bodyY) * vs, -f.legB[2] * vs);
        } else body.translate(0, y * vs, -lunge * sp.reach * 0.6f);
        body.rotateX(pitch);
        if (dead) body.rotateZ(1.45f * lie);
        part(injuredPart(a,f.body,rpg.combat.Gore.Part.TORSO), vs, 0, 0, 0, 0, 0, 0);
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
            float peck = !dead && a.headDown ? 0.4f * Math.max(0, (float) Math.sin(time * 6 + a.id)) : 0;
            limb(rpg.combat.Gore.Part.HEAD, dead ? f.headClosed : f.head, vs, f.neck[0], f.neck[1] - lie * 1.2f, f.neck[2] + bobZ, a.headPitch - peck, a.headYaw, 0);
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
            limb(rpg.combat.Gore.Part.HEAD, dead ? f.headClosed : f.head, vs, f.neck[0], f.neck[1], f.neck[2], headP, a.headYaw, 0);
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
            limb(rpg.combat.Gore.Part.TAIL, f.tail, vs, f.tailAt[0], f.tailAt[1], f.tailAt[2], droop * (1 - lie * 0.5f), dead ? 0 : wag, 0);
        }
        if (!dead && lie < 0.5f) cmd.shadowBody(a.x, a.y, a.z, a.radius, a.height * (1 - lie * 0.5f));
    }

    // ------------------------------------------------------------------ dungeons: torchlight
    /**
     * The dungeon the eye is in, lit as it is lit (gamefixes): a torch in an iron sconce on every lamp-wall, its flame
     * guttering and its warm light flickering with it; the braziers' fire bright and high, the fire pits glowing low.
     * There is no daylight underground - away from these it is dark.
     */
    private void dungeonLights(Rpg g) {
        var dl = rpg.world.DungeonLayout.at(camX, camZ);
        if (dl == null) return;
        int i = 0;
        for (var s : dl.spots) {
            i++;
            float x = dl.worldX(s.x()) + 0.5f, z = dl.worldZ(s.z()) + 0.5f;
            float fl = 0.86f + 0.1f * (float) Math.sin(clock * 9.1 + i * 1.7) + 0.05f * (float) Math.sin(clock * 23.3 + i);
            long emit = 0x5000000000L + dl.id * 4096L + i;
            switch (s.what()) {
                case "lamp" -> {
                    float dir = s.arg() == 1 ? 1 : -1;
                    float tx = x + dir * 0.62f, ty = DevMap.FLOOR + 2.0f;
                    m.identity().translate(tx, ty, z).rotateY(dir > 0 ? (float) (-Math.PI / 2) : (float) (Math.PI / 2));
                    drawLayers("dungeon-torch", Props::torch);
                    cmd.emitter(emit, 0, tx + dir * 0.06f, ty + 0.36f, z, 0.05f, 22, 0.06f, 0.42f, 5.5f, 1.8f, 0.35f, 0, 0.45f, 0);
                    cmd.pointLight(tx + dir * 0.3f, ty + 0.45f, z, 12, 4.6f * fl, 2.4f * fl, 0.8f * fl, 0);
                }
                case "brazier" -> {
                    cmd.emitter(emit, 0, x, DevMap.FLOOR + 1.3f, z, 0.25f, 46, 0.12f, 0.8f, 5.5f, 1.8f, 0.3f, 0, 0.9f, 0);
                    cmd.pointLight(x, DevMap.FLOOR + 2.0f, z, 14, 6.5f * fl, 3.2f * fl, 1.0f * fl, 0);
                }
                case "embers" -> { if (i % 3 == 0) cmd.pointLight(x, DevMap.FLOOR + 0.6f, z, 5, 2.6f * fl, 0.9f * fl, 0.2f, 0); }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ the level-up's Holy Light
    /**
     * The beam (gamefixes): a thick column of gold-white light erupting from the ground under the feet and shooting
     * up - a white-hot core inside a broader gold sheath, both turning slowly - a flash ring on the ground where it
     * rose, and at its top, as it bursts, a bright crown that blows apart (the sparks are Fx's). It follows the body.
     */
    private void holyLight(Rpg g) {
        float t = g.fx.light();
        if (t < 0 || t > Fx.LIGHT_TIME) return;
        Actor p = g.player;
        g.fx.lightX = p.x; g.fx.lightY = p.y; g.fx.lightZ = p.z;
        float rise = Math.min(1, t / Fx.LIGHT_RISE);
        rise = 1 - (1 - rise) * (1 - rise) * (1 - rise);                  // it shoots up and slows near the top
        float fade = t < Fx.LIGHT_BURST ? 1 : Math.max(0, 1 - (t - Fx.LIGHT_BURST) / 0.75f);
        if (fade <= 0.001f) return;
        float top = 9.5f * rise, spin = (float) (clock * 1.7);
        // the column: its base swells as it erupts and thins as it fades
        float swell = 1 + 0.35f * (float) Math.max(0, Math.sin(Math.min(1, t / 0.3f) * Math.PI));
        for (int layer = 0; layer < 3; layer++) {
            // (light enough that the body inside it still shows through, lit gold)
            float w = (layer == 0 ? 0.16f : layer == 1 ? 0.5f : 0.95f) * swell * (0.6f + 0.4f * fade);
            float r = layer == 0 ? 1.5f : layer == 1 ? 1.1f : 0.7f, gc = layer == 0 ? 1.4f : layer == 1 ? 0.82f : 0.48f, b = layer == 0 ? 1.1f : layer == 1 ? 0.28f : 0.1f;
            float a = fade * (layer == 0 ? 0.55f : layer == 1 ? 0.22f : 0.1f);
            m.identity().translate(p.x, p.y + top / 2, p.z).rotateY(spin * (layer == 1 ? -1 : 1) + layer * 0.4f).scale(w, top, w);
            System.arraycopy(m.m, 0, m16, 0, 16);
            cmd.model(box, AstrattoNative.MODEL_ADDITIVE | AstrattoNative.MODEL_NO_SHADOW, m16, r, gc, b, a, 0.6f, 0, 1, 0);
        }
        // the ring on the ground where it erupted, spreading and fading
        float ringT = Math.min(1, t / 0.7f), ring = 0.4f + 1.6f * ringT;
        for (int i = 0; i < 12; i++) {
            // a spreading ring: twelve short arcs round the feet
            float ang = i / 12f * 6.283f + spin * 0.3f;
            m.identity().translate(p.x + (float) Math.cos(ang) * ring, p.y + 0.03f, p.z + (float) Math.sin(ang) * ring).rotateY(-ang).scale(0.06f, 0.02f, ring * 0.55f);
            System.arraycopy(m.m, 0, m16, 0, 16);
            cmd.model(box, AstrattoNative.MODEL_ADDITIVE | AstrattoNative.MODEL_NO_SHADOW, m16, 1.2f, 0.85f, 0.25f, (1 - ringT) * 0.35f, 0.5f, 0, 1, 0);
        }
        // the crown at the head of the beam: bright as it bursts, then blown apart
        if (t > Fx.LIGHT_BURST - 0.15f && t < Fx.LIGHT_BURST + 0.35f) {
            float bt = (t - (Fx.LIGHT_BURST - 0.15f)) / 0.5f, size = 0.4f + bt * 2.4f;
            m.identity().translate(p.x, p.y + top, p.z).rotateY(spin).rotateX(0.6f).scale(size, size, size);
            System.arraycopy(m.m, 0, m16, 0, 16);
            cmd.model(box, AstrattoNative.MODEL_ADDITIVE | AstrattoNative.MODEL_NO_SHADOW, m16, 1.6f, 1.3f, 0.6f, (1 - bt) * 0.5f, 0.8f, 0, 1, 0);
        }
    }

    // ------------------------------------------------------------------ monsters
    /** A monster's gait and motion between frames: its step phase and stride, how far it is leaning, its wings' beat. */
    private static final class MonsterPose { float phase, amp, flap, lean; }
    private final java.util.Map<Long, MonsterPose> monsterPoses = new java.util.HashMap<>();
    private final Mat4 mf = new Mat4(), mp = new Mat4(), mq = new Mat4(), ma = new Mat4(), mb = new Mat4(), mc = new Mat4();
    private float mShade = 1, mAlpha = 1, mGlow = 0;

    /** One of a monster's parts in a frame measured in its voxels (the frame carries the figure's scale). */
    private void mpart(Mat4 frame, ItemModels.Model p) {
        if (p == null) return;
        float k = 1 / ItemModels.VOX;
        m.set(frame).scale(k, k, k);
        int mesh = partMesh(p)[1];
        if (mesh == 0) return;
        System.arraycopy(m.m, 0, m16, 0, 16);
        float sh = mShade * charTint;
        cmd.model(mesh, mAlpha < 1 ? AstrattoNative.MODEL_FORWARD : 0, m16, sh, sh, sh, mAlpha, mGlow, 1, 0.85f, 0);
    }
    private static float[] at(Mat4 f, float x, float y, float z) { float[] o = new float[3]; f.transformPoint(x, y, z, o); return o; }
    private static float[] dirOf(Mat4 f, float x, float y, float z) { float[] o = new float[3]; f.transformDir(x, y, z, o); return norm(o); }

    /**
     * A frame for a part laid between two points in the world: its y axis from b toward a for a hanging limb (from a
     * toward b for a part that stands up from a), x as near the reference as it can be, in voxels of vs metres.
     */
    private static Mat4 between(Mat4 out, float[] a, float[] b, float[] ref, float vs, boolean up) {
        float[] y = norm(up ? new float[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]} : new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]});
        float d = ref[0] * y[0] + ref[1] * y[1] + ref[2] * y[2];
        float[] x = norm(new float[]{ref[0] - y[0] * d, ref[1] - y[1] * d, ref[2] - y[2] * d});
        float[] z = cross(x, y);
        float[] mm = out.m;
        mm[0] = x[0] * vs; mm[1] = x[1] * vs; mm[2] = x[2] * vs; mm[3] = 0;
        mm[4] = y[0] * vs; mm[5] = y[1] * vs; mm[6] = y[2] * vs; mm[7] = 0;
        mm[8] = z[0] * vs; mm[9] = z[1] * vs; mm[10] = z[2] * vs; mm[11] = 0;
        mm[12] = a[0]; mm[13] = a[1]; mm[14] = a[2]; mm[15] = 1;
        return out;
    }

    /** Two bones from a shoulder to a target, bending toward a pole: where the elbow goes. The target is pulled in to what the bones reach. */
    private static float[] elbow(float[] s, float[] t, float a, float b, float[] pole) {
        float dx = t[0] - s[0], dy = t[1] - s[1], dz = t[2] - s[2], d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float reach = (a + b) * 0.995f;
        if (d > reach) { float k = reach / d; t[0] = s[0] + dx * k; t[1] = s[1] + dy * k; t[2] = s[2] + dz * k; dx *= k; dy *= k; dz *= k; d = reach; }
        d = Math.max(d, Math.abs(a - b) + 1e-3f);
        float ux = dx / d, uy = dy / d, uz = dz / d;
        float x = (a * a - b * b + d * d) / (2 * d), h = (float) Math.sqrt(Math.max(0, a * a - x * x));
        float pd = pole[0] * ux + pole[1] * uy + pole[2] * uz;
        float[] p = norm(new float[]{pole[0] - ux * pd, pole[1] - uy * pd, pole[2] - uz * pd});
        return new float[]{s[0] + ux * x + p[0] * h, s[1] + uy * x + p[1] * h, s[2] + uz * x + p[2] * h};
    }

    /** A monster, posed from what it is doing (gamefixes: each its own walk, run and attack): the living by their kind, the dead as they fell. */
    private void monster(rpg.monster.Monster mo, float dt) {
        var f = MonsterModels.of(mo.kind, mo.breed);
        float previous = charTint; charTint = 1 - mo.charred * 0.86f;
        try {
            boolean ghost = mo.kind == rpg.monster.MonsterKind.GHOST;
            mShade = 1 + mo.cs.flash * 0.8f;
            mAlpha = ghost ? 0.5f + 0.08f * (float) Math.sin(clock * 1.7 + mo.id) : 1;
            mGlow = ghost ? 0.35f : 0;
            if (!mo.alive && mo.dissolve > 0) {
                // the remains going (gamefixes): charred to ash, a dull ember glow through it at the height of it, then
                // fading into the motes that rise off it
                float d = mo.dissolve, gone = smooth(Math.min(1, d * 1.15f));
                charTint *= 1 - 0.75f * smooth(Math.min(1, d * 2.2f));
                mGlow = Math.max(mGlow, 0.55f * (float) Math.sin(Math.PI * Math.min(1, d * 1.3f)));
                mAlpha *= 1 - gone;
                float ash = 0.62f - 0.25f * d;
                cmd.emitter(mo.id ^ (5L << 54), 2, mo.x, mo.y + 0.25f + mo.height * 0.2f, mo.z, Math.max(0.3f, mo.radius * 1.2f), 70 * (1 - d * 0.6f), 0.05f, 1.1f,
                        ash + 0.35f, ash + 0.1f, ash - 0.05f, 0, 0.9f, 0);
            }
            MonsterPose st = monsterPoses.computeIfAbsent(mo.id, k -> new MonsterPose());
            if (f.humanoid) {
                if (!mo.alive) {
                    var rd = sceneGame == null ? null : sceneGame.lastBodies.ragdoll(mo.ragdoll);
                    if (rd != null) monsterRagdoll(mo, f, rd);
                } else humanoidMonster(mo, f, st, dt);
            } else switch (mo.kind) {
                case BAT -> bat(mo, f, st, dt);
                case GIANT_SPIDER -> spider(mo, f, st, dt);
                default -> dragon(mo, f, st, dt);
            }
            if (mo.alive && !ghost) cmd.shadowBody(mo.x, mo.y, mo.z, Math.max(mo.radius, mo.kind.hw * 0.4f), mo.height);
        } finally { charTint = previous; mShade = 1; mAlpha = 1; mGlow = 0; heldScale = 1; }
    }

    private static float smooth(float x) { return x * x * (3 - 2 * x); }

    /** Where a swing is: -1..0 through the wind-up, 0..1 through the stroke, 1..2 through the recovery, 2 idle. */
    private static float phaseU(Actor a) {
        var c = a.cs;
        return switch (c.phase) {
            case WINDUP -> -1 + Math.min(1, c.t / Math.max(1e-3f, c.windup));
            case ACTIVE -> Math.min(1, c.t / Math.max(1e-3f, c.active));
            case RECOVER -> 1 + Math.min(1, c.t / Math.max(1e-3f, c.recover));
            default -> 2;
        };
    }

    private void humanoidMonster(rpg.monster.Monster mo, MonsterModels.Fig f, MonsterPose st, float dt) {
        var k = mo.kind;
        float vs = f.vs;
        double t = clock + (mo.id % 97) * 0.37;
        float speed = (float) Math.hypot(mo.vx, mo.vz);
        boolean ghost = k == rpg.monster.MonsterKind.GHOST;
        boolean airborne = k == rpg.monster.MonsterKind.IMP && mo.altitude > 0.35f;
        boolean run = speed > k.walk * 1.35f;
        // the stride: a step per leg's length (a zombie's shuffle is short; a werewolf lopes)
        float legLen = (f.thigh + f.shin) * vs;
        float stride = Math.max(0.15f, legLen * switch (k) { case ZOMBIE -> 0.85f; case GOBLIN, IMP -> 1.0f; case WEREWOLF -> run ? 1.9f : 1.3f; case TROLL -> 1.15f; default -> 1.3f; });
        if (!ghost && !airborne && speed > 0.05f) st.phase += speed / stride * (float) Math.PI * dt;
        float wantAmp = speed < 0.05f || airborne || ghost ? 0 : Math.min(run ? 0.95f : 0.55f, speed / Math.max(0.2f, k.walk) * 0.42f);
        st.amp += (wantAmp - st.amp) * Math.min(1, dt * 8);
        float ph = st.phase, amp = st.amp;
        boolean dormant = mo.state == rpg.monster.Monster.State.DORMANT || mo.state == rpg.monster.Monster.State.REST;
        // ---- each kind's carriage
        float lean, kneeBase, thighBase, armFwd, spread, roll = 0, headX = 0, headZ = 0, elbowBase, swingArms = 0.6f;
        switch (k) {
            case GOBLIN -> { lean = 0.16f + amp * 0.14f; kneeBase = 0.5f; thighBase = 0.28f; armFwd = 0.3f; spread = 0.25f; elbowBase = 0.5f; headX = -0.05f; }
            case ORC -> { lean = 0.08f + amp * 0.12f; kneeBase = 0.12f; thighBase = 0.04f; armFwd = 0.05f; spread = 0.22f; elbowBase = 0.25f; roll = 0.05f * (float) Math.sin(ph) * amp; }
            case TROLL -> { lean = 0.42f + amp * 0.1f; kneeBase = 0.35f; thighBase = 0.2f; armFwd = 0.5f; spread = 0.16f; elbowBase = 0.25f; headX = 0.05f; roll = 0.09f * (float) Math.sin(ph) * Math.max(0.3f, amp); swingArms = 0.45f; }
            case IMP -> { lean = airborne ? 0.35f + Math.min(0.5f, speed * 0.08f) : 0.12f; kneeBase = airborne ? 0.8f : 0.2f; thighBase = airborne ? 0.5f : 0.08f; armFwd = 0.25f; spread = 0.3f; elbowBase = 0.7f; headX = -0.1f; }
            case GHOST -> { lean = 0.1f + Math.min(0.3f, speed * 0.1f); kneeBase = 0; thighBase = 0; armFwd = 0.3f; spread = 0.12f; elbowBase = 0.3f; headX = 0.15f; }
            case ZOMBIE -> { lean = 0.18f; kneeBase = 0.18f; thighBase = 0.05f; armFwd = 1.35f; spread = 0.12f; elbowBase = 0.15f; headZ = 0.3f + 0.08f * (float) Math.sin(t * 0.7); headX = 0.15f; roll = 0.07f * (float) Math.sin(ph); swingArms = 0.15f; }
            case SKELETON -> { lean = 0.02f; kneeBase = 0.08f; thighBase = 0.03f; armFwd = 0.08f; spread = 0.1f; elbowBase = 0.1f; headZ = 0.12f * (float) Math.sin(t * 0.45); swingArms = 0.85f; }
            default -> { // the werewolf: hunched on its bent legs; running, down on all fours
                lean = run ? 1.0f : 0.4f; kneeBase = 0; thighBase = 0; armFwd = run ? 1.2f : 0.3f; spread = 0.18f; elbowBase = 0.45f; headX = run ? -0.15f : 0.05f; }
        }
        if (dormant && !airborne) { lean += 0.2f; headX += 0.3f; armFwd *= 0.5f; }
        // the attack: the stroke the combat system is sweeping (the same path its hit test follows)
        float u = phaseU(mo);
        boolean striking = mo.cs.phase != Actor.Combat.Phase.IDLE && mo.cs.move != null;
        float lunge = 0, twist = 0;
        if (striking) {
            float uc = Math.max(-1, Math.min(1, u));
            float s = u < 0 ? 0 : u <= 1 ? (float) Math.sin(u * Math.PI / 2) : Math.max(0, 2 - u);
            lunge = s * (mo.cs.move == Actor.Combat.Move.HEAVY ? 0.25f : 0.12f);
            twist = rpg.combat.CombatSystem.twist(mo.cs.move, mo.cs.offHand, uc);
            lean += rpg.combat.CombatSystem.lean(mo.cs.move, uc) * 0.6f;
        }
        st.lean += (lean - st.lean) * Math.min(1, dt * 6);
        lean = st.lean;
        // skeletons move in jerks: their phase snaps between poses rather than flowing
        float gp = k == rpg.monster.MonsterKind.SKELETON ? (float) (Math.floor(ph / 0.35f) * 0.35f + (ph % 0.35f) * 0.4f) : ph;
        float breath = 0.03f * (float) Math.sin(t * (k == rpg.monster.MonsterKind.TROLL ? 1.2 : 2.1));
        // ---- the legs: thigh, knee, foot for each side; the pelvis rides at the height that keeps the planted foot down
        float[][] leg = new float[2][3];
        float lowest = 0;
        for (int side = 0; side < 2; side++) {
            float lp = gp + (side == 0 ? 0 : (float) Math.PI);
            float sw = (float) Math.sin(lp);
            float thighA, knee, foot;
            if (f.digitigrade) {
                // a wolf's leg: the thigh forward, the long shin back to the hock, the foot down onto its toes
                thighA = 0.55f + sw * amp * 0.9f;
                knee = 1.5f + Math.max(0, (float) Math.sin(lp + 1.4f)) * amp * 1.1f;
                foot = 0.95f - sw * amp * 0.3f;
            } else {
                boolean drag = k == rpg.monster.MonsterKind.ZOMBIE && side == 1;
                thighA = thighBase + sw * amp * (drag ? 0.45f : 0.85f);
                knee = Math.max(0, kneeBase + Math.max(0, (float) Math.sin(lp + 1.3f)) * amp * (run ? 1.6f : 1.05f) - (drag ? 0.1f : 0));
                foot = -(thighA - knee) + (run ? Math.max(0, -sw) * 0.4f * amp : 0);
            }
            if (airborne) { thighA = thighBase + 0.15f * (float) Math.sin(t * 2 + side); knee = kneeBase + 0.2f * (float) Math.sin(t * 2.3 + side); foot = 0.5f; }
            leg[side][0] = thighA; leg[side][1] = knee; leg[side][2] = foot;
            float a2 = thighA - knee, a3 = a2 + foot;
            float depth = f.thigh * (float) Math.cos(thighA) + f.shin * (float) Math.cos(a2) + (f.foot == null ? 0 : (f.digitigrade ? 2.6f : 1) * (float) Math.cos(a3));
            lowest = Math.max(lowest, depth);
        }
        float pelvis = ghost ? f.hipY + 0.6f * (float) Math.sin(t * 1.3) : airborne ? f.hipY : lowest;
        float bob = ghost || airborne ? 0 : Math.abs((float) Math.sin(gp)) * amp * (run ? 1.2f : 0.55f) * (k == rpg.monster.MonsterKind.TROLL ? 1.5f : 1);
        mf.identity().translate(mo.x, mo.y, mo.z).rotateY(mo.yaw).scale(vs, vs, vs);
        float castT = (float) (clock - mo.castAt), throwT = (float) (clock - mo.throwAt);
        boolean casting = castT >= 0 && castT < 0.5f, throwing = throwT >= 0 && throwT < 0.6f;
        mp.set(mf).translate(0, pelvis + bob, -lunge * 6).rotateY(twist).rotateX(-lean + breath).rotateZ(roll);
        mq.set(mf).translate(0, pelvis + bob, -lunge * 6).rotateY(twist * 0.25f).rotateZ(roll * 0.5f);
        mpart(mp, f.torso);
        // the head: it looks ahead (or at its foe) against the lean
        float look = mo.fightTarget != 0 ? 0 : 0.25f * (float) Math.sin(t * 0.3);
        ma.set(mp).translate(f.neck[0], f.neck[1], f.neck[2]).rotateY(look - twist * 0.5f).rotateX(lean - breath - headX).rotateZ(headZ);
        mpart(ma, f.head);
        float[] headTop = at(ma, 0, 5, 0), neckW = at(ma, 0, 0, 0);
        // ---- the arms
        float[][] hands = new float[2][], shoulders = new float[2][], elbows = new float[2][];
        float[] fwdW = dirOf(mf, 0, 0, -1), rightW = dirOf(mf, 1, 0, 0);
        for (int side = 0; side < 2; side++) {
            float sgn = side == 0 ? 1 : -1;
            float lp = gp + (side == 0 ? (float) Math.PI : 0);
            boolean main = side == 0 != mo.cs.offHand;
            float pitch = armFwd + (float) Math.sin(lp) * amp * swingArms;
            if (k == rpg.monster.MonsterKind.WEREWOLF && run) pitch = 1.25f + (float) Math.sin(lp) * amp * 0.9f;   // its arms reach and pull like forelegs
            float el = elbowBase + (run ? 0.6f : 0.15f) * amp;
            if (k == rpg.monster.MonsterKind.ZOMBIE) pitch += 0.12f * (float) Math.sin(t * 1.3 + side * 2);
            if (ghost) { pitch += 0.15f * (float) Math.sin(t * 1.1 + side); if (mo.fightTarget != 0) { pitch = 1.3f; el = 0.1f; } }
            if (main && casting) { pitch = 1.5f * Math.min(1, castT * 6); el = 0.1f; }
            if (main && throwing) { pitch = throwT < 0.15f ? 2.6f : 2.6f - Math.min(1, (throwT - 0.15f) * 5) * 1.6f; el = throwT < 0.15f ? 1.2f : 0.2f; }
            mb.set(mp).translate(sgn * f.shoulder[0], f.shoulder[1], f.shoulder[2]);
            float[] sh = at(mb, 0, 0, 0);
            shoulders[side] = sh;
            mb.rotateZ(sgn * spread).rotateX(pitch + lean * 0.4f);
            mc.set(mb).translate(0, -f.upperArm, 0).rotateX(el);
            float[] carried = at(mc, 0, -f.foreArm, 0);
            // how far into the stroke the hand is: eased in over the first part of the wind-up (the anticipation),
            // on the stroke's path through it, and eased back to its carriage through the recovery
            float into = !striking || !main ? 0 : u < 0 ? smooth(Math.min(1, (u + 1) / 0.55f)) : u <= 1 ? 1 : 1 - smooth(Math.min(1, u - 1));
            if (into > 0) {
                // the hand goes where the stroke's grip is: two bones solved from the shoulder to it, the elbow out and down
                float[] g = rpg.combat.CombatSystem.grip(mo, mo.cs.move, Math.max(-1, Math.min(1, u)));
                // a head wider than a person's (a goblin's, an imp's) keeps the stroke out to the side of it
                float clear = Math.max(0, f.headHalf * vs - 4 * Body.PX * mo.figureScale());
                float[] target = {g[0] + rightW[0] * sgn * clear, g[1], g[2] + rightW[2] * sgn * clear};
                for (int i = 0; i < 3; i++) target[i] = carried[i] + (target[i] - carried[i]) * into;
                float[] pole = {rightW[0] * sgn * 0.6f - fwdW[0] * 0.3f, -0.8f, rightW[2] * sgn * 0.6f - fwdW[2] * 0.3f};
                float[] e = elbow(sh, target, f.upperArm * vs, f.foreArm * vs, pole);
                mpart(between(mc, sh, e, rightW, vs, false), f.armUp);
                mpart(between(mc, e, target, rightW, vs, false), f.armLo);
                elbows[side] = e; hands[side] = target;
                continue;
            }
            mpart(mb, f.armUp);
            elbows[side] = at(mb, 0, -f.upperArm, 0);
            mpart(mc, f.armLo);
            hands[side] = carried;
        }
        // ---- the legs, or the robe
        if (ghost) mpart(mq, f.robe);
        else for (int side = 0; side < 2; side++) {
            float sgn = side == 0 ? 1 : -1;
            ma.set(mq).translate(sgn * f.hip[0], f.hip[1], f.hip[2]).rotateX(leg[side][0]);
            mpart(ma, f.legUp);
            mb.set(ma).translate(0, -f.thigh, 0).rotateX(-leg[side][1]);
            mpart(mb, f.legLo);
            mc.set(mb).translate(0, -f.shin, 0).rotateX(leg[side][2]);
            mpart(mc, f.foot);
        }
        // tails and wings
        if (f.tail != null) {
            float sway = 0.35f * (float) Math.sin(t * (k == rpg.monster.MonsterKind.IMP ? 2.4 : 1.1)) + (float) Math.sin(gp) * amp * 0.3f;
            float droop = k == rpg.monster.MonsterKind.WEREWOLF ? (run ? 0.1f : 0.7f) : airborne ? 0.4f : 0.9f + 0.15f * (float) Math.sin(t * 1.7);
            ma.set(mq).translate(f.tailAt[0], f.tailAt[1], f.tailAt[2]).rotateY(sway).rotateX(droop);
            mpart(ma, f.tail);
        }
        if (f.wing != null) {
            st.flap += ((airborne ? 1 : 0.15f) - st.flap) * Math.min(1, dt * 5);
            float beat = st.flap * (float) Math.sin(t * 26) * 0.85f;
            for (int side = 0; side < 2; side++) {
                ma.set(mp).translate((side == 0 ? 1 : -1) * f.wingAt[0], f.wingAt[1], f.wingAt[2]);
                if (side == 1) ma.rotateY((float) Math.PI);
                ma.rotateY(side == 0 ? -(0.55f + beat) : 0.55f + beat).rotateZ(-0.15f);
                mpart(ma, f.wing);
            }
        }
        // what it holds: its weapon in the hand, at its size
        heldScale = Math.max(0.55f, Math.min(2.4f, vs / Body.PX));
        ItemDef main = mo.main(), off = mo.off();
        for (int side = 0; side < 2; side++) {
            ItemDef holds = side == 0 ? main : off;
            if (holds == null || holds.natural || hands[side] == null) continue;
            float[] dir;
            // carried point-forward from the fist, square to the forearm; in a stroke, turned onto the stroke's blade
            float[] fore = norm(new float[]{hands[side][0] - elbows[side][0], hands[side][1] - elbows[side][1], hands[side][2] - elbows[side][2]});
            dir = norm(new float[]{fwdW[0] * 0.9f - fore[0] * 0.45f, 0.35f - fore[1] * 0.45f, fwdW[2] * 0.9f - fore[2] * 0.45f});
            float into = striking && side == 0 != mo.cs.offHand ? (u < 0 ? smooth(Math.min(1, (u + 1) / 0.55f)) : u <= 1 ? 1 : 1 - smooth(Math.min(1, u - 1))) : 0;
            if (into > 0) {
                float[] g = rpg.combat.CombatSystem.grip(mo, mo.cs.move, Math.max(-1, Math.min(1, u)));
                dir = norm(new float[]{dir[0] + (g[3] - dir[0]) * into, dir[1] + (g[4] - dir[1]) * into, dir[2] + (g[5] - dir[2]) * into});
            }
            if (holds.family == Family.SHIELD) held(holds, hands[side], new float[]{0, 1, 0}, mo.yaw, true);
            else if (holds.family == Family.BOW) heldBow(holds, hands[side], new float[]{0, 1, 0}, mo.yaw, mo.cs.draw, hands[1 - side]);
            else held(holds, hands[side], dir, mo.yaw, false);
        }
        heldScale = 1;
        // the pose its hit volumes and its wounds follow: the same fifteen points a person's rig keeps
        monsterPosePoints(mo, mp, mq, f, neckW, headTop, shoulders, elbows, hands, leg);
        mo.hand[0] = hands[0][0]; mo.hand[1] = hands[0][1]; mo.hand[2] = hands[0][2]; mo.handAt = clock;
        // an imp's hand kindles as it casts
        if (casting) { float[] h = hands[mo.cs.offHand ? 1 : 0]; cmd.emitter(mo.id ^ (1L << 52), 0, h[0], h[1], h[2], 0.04f, 30, 0.04f, 0.25f, 5, 1.6f, 0.3f, 0, 0.1f, 0); }
    }

    private void monsterPosePoints(rpg.monster.Monster mo, Mat4 torso, Mat4 hips, MonsterModels.Fig f, float[] neck, float[] head, float[][] shoulders, float[][] elbows, float[][] hands, float[][] leg) {
        if (mo.posePts == null) mo.posePts = new float[AstrattoNative.RD_PARTICLES * 3];
        float[] p = mo.posePts;
        java.util.function.BiConsumer<Integer, float[]> put = (i, v) -> { p[i * 3] = v[0]; p[i * 3 + 1] = v[1]; p[i * 3 + 2] = v[2]; };
        put.accept(AstrattoNative.RD_PELVIS, at(torso, 0, 0, 0));
        put.accept(AstrattoNative.RD_NECK, neck);
        put.accept(AstrattoNative.RD_HEAD, head);
        for (int side = 0; side < 2; side++) {
            // side 0 is the right
            int sh = side == 0 ? AstrattoNative.RD_R_SHOULDER : AstrattoNative.RD_L_SHOULDER;
            put.accept(sh, shoulders[side]); put.accept(sh + 1, elbows[side]); put.accept(sh + 2, hands[side]);
            float sgn = side == 0 ? 1 : -1;
            int hp = side == 0 ? AstrattoNative.RD_R_HIP : AstrattoNative.RD_L_HIP;
            if (f.robe != null) {
                // the ghost's robe: its "legs" are the robe's two halves, hanging to the hem
                put.accept(hp, at(hips, sgn * 2, 0, 0)); put.accept(hp + 1, at(hips, sgn * 3, -8, 0)); put.accept(hp + 2, at(hips, sgn * 3.5f, -16, 0));
                continue;
            }
            mc.set(hips).translate(sgn * f.hip[0], f.hip[1], f.hip[2]);
            put.accept(hp, at(mc, 0, 0, 0));
            mc.rotateX(leg[side][0]).translate(0, -f.thigh, 0);
            put.accept(hp + 1, at(mc, 0, 0, 0));
            mc.rotateX(-leg[side][1]).translate(0, -f.shin, 0);
            put.accept(hp + 2, at(mc, 0, 0, 0));
        }
        mo.poseAt = clock; mo.poseX = mo.x; mo.poseY = mo.y; mo.poseZ = mo.z; mo.poseYaw = mo.yaw;
    }

    /** A dead humanoid monster: its own parts laid between the ragdoll's particles as Sydney has them. */
    private void monsterRagdoll(rpg.monster.Monster mo, MonsterModels.Fig f, Snapshots.Ragdoll rd) {
        int ls = AstrattoNative.RD_L_SHOULDER * 3, rs = AstrattoNative.RD_R_SHOULDER * 3;
        float[] rr = norm(new float[]{rd.p[rs] - rd.p[ls], rd.p[rs + 1] - rd.p[ls + 1], rd.p[rs + 2] - rd.p[ls + 2]});
        boolean[] gone = goneSegs(mo);
        float vs = f.vs;
        java.util.function.IntFunction<float[]> pt = i -> new float[]{rd.p[i * 3], rd.p[i * 3 + 1], rd.p[i * 3 + 2]};
        float[] pelvis = pt.apply(AstrattoNative.RD_PELVIS), neck = pt.apply(AstrattoNative.RD_NECK);
        if (!gone[Seg.TORSO.ordinal()]) mpart(between(mc, pelvis, neck, rr, vs, true), f.torso);
        if (!gone[Seg.HEAD.ordinal()]) mpart(between(mc, neck, pt.apply(AstrattoNative.RD_HEAD), rr, vs, true), f.head);
        int[][] arms = {{AstrattoNative.RD_R_SHOULDER, Seg.R_ARM_UP.ordinal()}, {AstrattoNative.RD_L_SHOULDER, Seg.L_ARM_UP.ordinal()}};
        for (int[] arm : arms) {
            if (gone[arm[1]]) continue;
            mpart(between(mc, pt.apply(arm[0]), pt.apply(arm[0] + 1), rr, vs, false), f.armUp);
            mpart(between(mc, pt.apply(arm[0] + 1), pt.apply(arm[0] + 2), rr, vs, false), f.armLo);
        }
        if (f.robe != null) mpart(between(mc, pelvis, neck, rr, vs, true), f.robe);
        else {
            int[][] legs = {{AstrattoNative.RD_R_HIP, Seg.R_LEG_UP.ordinal()}, {AstrattoNative.RD_L_HIP, Seg.L_LEG_UP.ordinal()}};
            for (int[] lg : legs) {
                if (gone[lg[1]]) continue;
                mpart(between(mc, pt.apply(lg[0]), pt.apply(lg[0] + 1), rr, vs, false), f.legUp);
                between(mc, pt.apply(lg[0] + 1), pt.apply(lg[0] + 2), rr, vs, false);
                mpart(mc, f.legLo);
                mc.translate(0, -f.shin, 0).rotateX(-0.6f);
                mpart(mc, f.foot);
            }
        }
        if (f.tail != null) { mc.set(between(mb, pelvis, neck, rr, vs, true)).translate(f.tailAt[0], f.tailAt[1], f.tailAt[2]).rotateX(0.2f); mpart(mc, f.tail); }
        // what it held, still in the hand until someone takes it
        ItemDef main = mo.main();
        if (main != null && !main.natural && !gone[Seg.R_ARM_LO.ordinal()]) {
            heldScale = Math.max(0.55f, Math.min(2.4f, vs / Body.PX));
            float[] grip = pt.apply(AstrattoNative.RD_R_WRIST), el = pt.apply(AstrattoNative.RD_R_ELBOW);
            held(main, grip, norm(new float[]{grip[0] - el[0], grip[1] - el[1], grip[2] - el[2]}), rd.yaw, false);
            heldScale = 1;
        }
    }

    // ---- the beasts
    /** A bat: wings beating in flight (the inner panel from the arm, the outer folding at the wrist); folded and hanging head-down at its roost. */
    private void bat(rpg.monster.Monster mo, MonsterModels.Fig f, MonsterPose st, float dt) {
        float vs = f.vs;
        boolean dead = !mo.alive;
        float speed = (float) Math.hypot(mo.vx, mo.vz);
        boolean roosting = !dead && (mo.state == rpg.monster.Monster.State.DORMANT || mo.state == rpg.monster.Monster.State.REST) && speed < 0.3f;
        boolean diving = mo.windup != null && clock < mo.windupUntil;
        st.phase += (roosting || dead ? 0 : diving ? 9 : 13 + speed * 0.6f) * dt;
        float beat = (float) Math.sin(st.phase);
        float cy = mo.y + mo.kind.hh * 0.5f + (roosting || dead ? 0 : beat * 0.05f);
        mf.identity().translate(mo.x, dead ? mo.y + 0.08f : cy, mo.z).rotateY(mo.yaw);
        if (roosting) mf.rotateZ((float) Math.PI);                         // hanging by its feet, head down
        if (dead) mf.rotateX(-1.4f);                                       // on its back, wings splayed
        else mf.rotateX(diving ? 0.5f : -0.15f - Math.min(0.3f, speed * 0.04f));
        mf.scale(vs, vs, vs);
        mpart(mf, f.body);
        ma.set(mf).translate(f.headAt[0], f.headAt[1], f.headAt[2]).rotateX(diving ? 0.3f : 0);
        mpart(ma, f.beastHead);
        for (int side = 0; side < 2; side++) {
            float inner = roosting ? -1.35f : dead ? 0.05f : 0.15f + beat * 0.95f;
            float outer = roosting ? -2.6f : dead ? 0.2f : 0.25f + (float) Math.sin(st.phase - 0.9f) * 0.65f;
            ma.set(mf).translate((side == 0 ? 1 : -1) * f.wingAt[0], f.wingAt[1], f.wingAt[2]);
            if (side == 1) ma.rotateZ((float) Math.PI);
            ma.rotateZ(side == 0 ? inner : -inner).rotateY(roosting ? 0.2f : 0.08f);
            mpart(ma, f.wingIn);
            mb.set(ma).translate(f.wingInLen, 0, 0).rotateZ(side == 0 ? outer : -outer).rotateY(0.2f);
            mpart(mb, f.wingOut);
        }
        for (int side = 0; side < 2; side++) { ma.set(mf).translate((side == 0 ? 1 : -1) * 1.2f, -3, 1.5f).rotateX(roosting ? 3.1f : 0.6f); mpart(ma, f.legA); }
    }

    /** A giant spider: eight legs in an alternating gait (four planted, four lifting), rearing to bite; dead, on its back with its legs curled. */
    private void spider(rpg.monster.Monster mo, MonsterModels.Fig f, MonsterPose st, float dt) {
        float vs = f.vs;
        double t = clock + mo.id * 0.53;
        boolean dead = !mo.alive;
        float speed = dead ? 0 : (float) Math.hypot(mo.vx, mo.vz);
        if (speed > 0.05f) st.phase += speed / (f.legALen * vs * 0.9f) * (float) Math.PI * dt;
        st.amp += ((speed < 0.05f ? 0 : Math.min(1, speed / Math.max(0.2f, mo.kind.walk))) - st.amp) * Math.min(1, dt * 8);
        boolean rearing = !dead && mo.windup != null && clock < mo.windupUntil;
        float rear = rearing ? 0.35f : 0;
        float raise = 0.55f, down = 1.35f;
        float bodyY = -(2 + f.legALen * (float) Math.sin(raise) - f.legBLen * (float) Math.sin(down)) + 0.6f;
        float bob = Math.abs((float) Math.sin(st.phase * 2)) * st.amp * 0.4f + 0.15f * (float) Math.sin(t * 1.5);
        mf.identity().translate(mo.x, dead ? mo.y + 0.25f : mo.y, mo.z).rotateY(mo.yaw).scale(vs, vs, vs).translate(0, dead ? 2 : bodyY + bob, 0);
        if (dead) mf.rotateZ((float) Math.PI);
        mf.rotateX(rear);
        mpart(mf, f.beastHead);
        ma.set(mf).translate(0, 1, 0.5f).rotateX(-0.3f + 0.05f * (float) Math.sin(t * 0.9) - rear * 0.5f);
        mpart(ma, f.body);
        for (int i = 0; i < 8; i++) {
            int side = i < 4 ? 0 : 1, n = i % 4;
            float sgn = side == 0 ? 1 : -1;
            float spreadA = -0.8f + n * 0.53f;
            // the alternating tetrapod: R0 L1 R2 L3 together, then the others
            float off = ((n + side) % 2 == 0) ? 0 : (float) Math.PI;
            float sw = (float) Math.sin(st.phase + off) * 0.32f * st.amp;
            float lift = Math.max(0, (float) Math.cos(st.phase + off)) * 0.45f * st.amp;
            float r0 = dead ? 0.9f : raise + lift + (rearing && n < 2 ? 0.6f : 0);
            float r1 = dead ? 2.7f : down - raise + (n < 2 && rearing ? -0.4f : 0);
            float[] root = f.legRoots[i];
            ma.set(mf).translate(root[0], root[1], root[2]).rotateY(sgn * ((float) Math.PI / 2 - spreadA - sw)).rotateX(-r0);
            mpart(ma, f.legA);
            mb.set(ma).translate(0, 0, f.legALen).rotateX(r0 + r1);
            mpart(mb, f.legB);
        }
    }

    /**
     * A dragon: on the ground a heavy four-legged walk under its folded wings; aloft its great wings beat, its legs
     * tucked and its tail streaming; its jaws open for the breath and the bite. Dead, it lies on its side.
     */
    private void dragon(rpg.monster.Monster mo, MonsterModels.Fig f, MonsterPose st, float dt) {
        // (gamefixes: the dragon's animations were comically bad - a giraffe on stilts, legs dangling as it flew) it is
        // now a heavy predator: low on bent legs, its neck an S that holds the head out level, the tail a slow wave that
        // grows toward its tip; aloft its wings drive down hard and recover slowly, the body lifting on each stroke, the
        // legs folded up under it
        float vs = f.vs;
        double t = clock + mo.id * 0.3;
        boolean dead = !mo.alive;
        float speed = dead ? 0 : (float) Math.hypot(mo.vx, mo.vz);
        boolean aloft = !dead && mo.altitude > 1.2f;
        if (!aloft && speed > 0.05f) st.phase += speed / (f.legALen * vs * 1.6f) * (float) Math.PI * dt;
        st.amp += ((aloft || speed < 0.05f ? 0 : Math.min(1f, speed / Math.max(0.3f, mo.kind.walk) * 0.85f)) - st.amp) * Math.min(1, dt * 6);
        st.flap += ((aloft ? 1 : 0) - st.flap) * Math.min(1, dt * 3);
        float ph = st.phase, amp = st.amp, fl = st.flap;
        // the wingbeat: a fast downstroke and a slower recovery (the phase warped toward the down half)
        double wp = t * 5.2;
        float beat = (float) Math.sin(wp + 0.45 * Math.sin(wp));
        boolean breathing = !dead && mo.nextRanged > 0 && clock - (mo.nextRanged - 6) < 1.4;
        boolean biting = !dead && mo.windup != null && clock < mo.windupUntil;
        float wind = biting ? (float) Math.max(0, Math.min(1, 1 - (mo.windupUntil - clock) / 0.9)) : 0;
        float standY = (f.legALen + f.legBLen) * 0.8f + 2.5f;
        mf.identity().translate(mo.x, mo.y, mo.z).rotateY(mo.yaw).scale(vs, vs, vs);
        if (dead) mf.translate(0, 6, 0).rotateZ(1.5f);
        else {
            float bob = (1 - fl) * Math.abs((float) Math.sin(ph)) * amp * 0.7f - fl * beat * 1.3f;
            mf.translate(0, standY + bob, 0)
              .rotateZ((1 - fl) * (float) Math.sin(ph) * 0.05f * amp)
              .rotateX(fl * (-0.05f + beat * 0.05f) + (1 - fl) * (0.03f * (float) Math.sin(ph * 2) * amp - wind * 0.08f));
        }
        mpart(mf, f.body);
        // legs: the diagonal pairs of a walk, every joint bent under the weight; folded up under the belly aloft
        for (int i = 0; i < 4; i++) {
            float[] r = f.legRoots[i];
            boolean front = i < 2;
            float off = i == 0 || i == 3 ? 0 : (float) Math.PI;
            float sw = (float) Math.sin(ph + off) * amp;
            float lift = Math.max(0, (float) Math.sin(ph + off + 1.3f)) * amp * 0.9f;
            float g1 = sw * 0.6f + (front ? 0.3f : -0.35f), g2 = (front ? 0.55f : 0.7f) + lift;
            float a1 = dead ? 0.5f : g1 * (1 - fl) + fl * (front ? -1.55f : -1.4f);
            float a2 = dead ? 0.3f : g2 * (1 - fl) + fl * (front ? -2.1f : 0.35f);
            ma.set(mf).translate(r[0], r[1] - 4, r[2]).rotateX(a1);
            mpart(ma, f.legA);
            mb.set(ma).translate(0, -f.legALen, 0).rotateX(front ? -a2 : a2);
            mpart(mb, f.legB);
        }
        // the neck, two links in an S: up and forward from the shoulders, then out level, the head held looking ahead (a
        // bite rears it up and back first; the breath drops the head to aim it)
        float sway = dead ? 0.6f : 0.08f * (float) Math.sin(t * 0.37) + fl * 0.05f * (float) Math.sin(wp - 1);
        float n0 = dead ? -0.1f : (1 - fl) * (0.36f + 0.04f * (float) Math.sin(t * 0.8)) + fl * (0.16f + beat * 0.05f) + wind * 0.4f;
        float n1 = dead ? 0.12f : (1 - fl) * -0.1f + fl * -0.06f - wind * 0.1f;
        float nh = dead ? -0.72f : (1 - fl) * -0.24f + fl * -0.12f - (breathing ? 0.22f : 0) - wind * 0.35f;
        ma.set(mf).translate(0, 7, -12.5f).rotateY(sway).rotateX(n0);
        for (int i = 0; i < 2; i++) {
            mb.set(ma).rotateY((float) Math.PI);       // a link is a tail segment turned to face forward
            mpart(mb, f.tailSeg);
            ma.translate(0, 0, -f.tailLen).rotateY(sway * 0.5f).rotateX(i == 0 ? n1 : nh);
        }
        mpart(ma, f.beastHead);
        float jawOpen = breathing ? 0.55f : biting ? 0.15f + wind * 0.4f : dead ? 0.3f : 0.04f + 0.03f * (float) Math.max(0, Math.sin(t * 0.6));
        mb.set(ma).translate(f.jawAt[0], f.jawAt[1] - 4, -15 + f.jawAt[2]).rotateX(-jawOpen);
        mpart(mb, f.jaw);
        // the tail: five links in a travelling wave that grows toward the tip, drooping from the hips and curling up at
        // the end on the ground; streaming straight out behind aloft
        ma.set(mf).translate(0, 5, 12.5f).rotateX(dead ? 0.25f : (1 - fl) * 0.3f + fl * -0.04f);
        for (int i = 0; i < f.tailSegs; i++) {
            float sc = 1 - i * 0.14f;
            mb.set(ma).scale(sc, sc, 1);
            mpart(mb, f.tailSeg);
            float wave = (float) Math.sin(t * (1.4 + amp) - i * 0.75 + ph * 0.5f) * (0.07f + i * 0.045f) * (1 + amp * 0.6f);
            float waveUp = fl * 0.05f * (float) Math.sin(wp - 1.2 - i * 0.6);
            ma.translate(0, 0, f.tailLen - 0.5f).rotateY(dead ? 0.15f : wave).rotateX(dead ? 0.1f : (1 - fl) * -0.07f + waveUp);
        }
        // the wings: driving aloft (the outer panel trailing the stroke and snapping through at its foot), folded on the
        // ground along the flank, shifting a little with each step
        for (int side = 0; side < 2; side++) {
            float inner = dead ? 0.1f : fl * (0.12f + beat * 0.85f) + (1 - fl) * (0.42f + 0.03f * (float) Math.sin(t * 0.9) + 0.04f * amp * (float) Math.sin(ph));
            float outer = dead ? 0.6f : fl * (0.12f + (float) Math.sin(wp - 0.9) * 0.6f) + (1 - fl) * -2.75f;
            float sweep = dead ? 0.4f : fl * (0.12f - beat * 0.1f) + (1 - fl) * 1.3f;
            ma.set(mf).translate((side == 0 ? 1 : -1) * f.wingAt[0], f.wingAt[1], f.wingAt[2]);
            if (side == 1) ma.rotateZ((float) Math.PI);
            ma.rotateY(-sweep).rotateZ(side == 0 ? inner : -inner);
            mpart(ma, f.wingIn);
            mb.set(ma).translate(f.wingInLen, 0, 0).rotateZ(side == 0 ? outer : -outer);
            mpart(mb, f.wingOut);
        }
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
        if (!n.asleep()) return null;
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
    public void human(Actor a,Appearance look,float dt,boolean firstPerson,Furniture bed) {
        float previous=charTint;charTint=1-a.charred*(a instanceof rpg.animal.Animal ? 0.86f : 0.45f);
        try { humanModel(a,look,dt,firstPerson,bed); } finally { charTint=previous; }
    }
    private void humanModel(Actor a, Appearance look, float dt, boolean firstPerson, Furniture bed) {
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
        if (bed == null) { float[] hw = world(rig.hand[0]); a.hand[0] = hw[0]; a.hand[1] = hw[1]; a.hand[2] = hw[2]; a.handAt = clock; }
        if (bed == null && sceneGame != null && clock-a.castAt >= 0 && clock-a.castAt < 0.45) {
            float[] hand = a.hand;
            cmd.emitter(a.id ^ (1L<<52),2,hand[0],hand[1],hand[2],0.035f,35,0.03f,0.18f,0.8f,1.5f,3,0,0.04f,0);
        }
        // a rod's line: from its tip down to a float riding on the water at the spot, bobbing (and dipping as it bites)
        if (bed == null && Rig.fishing(a) == rpg.item.Items.FISHING_ROD && a.working.target instanceof rpg.objects.Node spot) {
            float[] tip = world(new float[]{rig.hand[0][0] + rig.itemDir[0][0] * 34, rig.hand[0][1] + rig.itemDir[0][1] * 34, rig.hand[0][2] + rig.itemDir[0][2] * 34});
            var w = a.working;
            float bite = w.duration - w.t < 0.8f ? 0.06f * (float) Math.sin(clock * 30) - 0.05f : 0;
            float[] bob = {spot.x + (float) Math.sin(a.id) * 0.4f, DevMap.FLOOR - 0.12f + 0.025f * (float) Math.sin(clock * 2.1) + bite, spot.z + (float) Math.cos(a.id) * 0.4f};
            float cast = Math.min(1, w.t / 0.55f);
            if (cast >= 1) {
                limb(tip, bob, 0.009f, 0.009f, 0, 0xF2F0EA, 1, 0.6f, 0);
                m.identity().translate(bob[0], bob[1], bob[2]).scale(0.07f, 0.09f, 0.07f); put(box, 0xD83A2A, 1, 0.5f, 0);
                m.identity().translate(bob[0], bob[1] + 0.06f, bob[2]).scale(0.05f, 0.04f, 0.05f); put(box, 0xF2EFE6, 1, 0.5f, 0);
            }
        }
        if (a instanceof rpg.actor.Player pl && bed == null) {
            float[] e = world(rig.eye);
            pl.headX = e[0] - a.x; pl.headY = e[1] - a.y; pl.headZ = e[2] - a.z; pl.headYaw = a.yaw; pl.headValid = true;
        }
        boolean[] gone = goneSegs(a);
        // the Holy Light's glow (gamefixes): the whole figure lit gold for a second as the beam rises and bursts
        float glow = 0;
        // Title and creator previews render characters without an active gameplay scene.
        if (sceneGame != null && a == sceneGame.player) {
            float lt = sceneGame.fx.light();
            if (lt >= 0 && lt < 1.3f) glow = lt < 0.2f ? lt / 0.2f : lt < 0.8f ? 1 : 1 - (lt - 0.8f) / 0.5f;
        }
        for (Seg s : Seg.values()) {
            int flags = firstPerson && s == Seg.HEAD ? AstrattoNative.MODEL_SHADOW_ONLY : 0;
            segMatrix(rig.seg[s.ordinal()]);
            // Perspective compensation for close bare fists; keep the world rig and hit volumes unchanged.
            if (firstPerson && st.guardRaise > 0.001f && a.main() == null && a.off() == null &&
                    (s == Seg.R_ARM_UP || s == Seg.R_ARM_LO || s == Seg.L_ARM_UP || s == Seg.L_ARM_LO)) m.scale(1 - st.guardRaise * 0.22f, 1, 1 - st.guardRaise * 0.22f);
            if (gone[s.ordinal()]) { if (isRoot(s)) stump(); continue; }
            float gr = shade * (1 + 0.4f * glow), gg = shade * (1 + 0.18f * glow), gb = shade * (1 - 0.4f * glow), ge = 0.32f * glow;
            put(fig[0][s.ordinal()], flags, gr, gg, gb, ge, 1, 0.85f, 0);
            ItemDef c = cover(a, s);
            boolean met = metal(c);
            put(fig[1][s.ordinal()], flags, gr, gg, gb, ge, 1, met ? 0.42f : 0.85f, met ? 0.65f : 0);
        }
        {
            // hair and facial hair: the head-unit meshes, on the head (8 pixels a side, its middle 4 up from the neck);
            // long hair hangs from the neck down the back and over the shoulders, on the torso
            float[] hm = rig.seg[Seg.HEAD.ordinal()];
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
            if (neck != null && !firstPerson) necklace(rig.seg[Seg.TORSO.ordinal()], b, neck);
        }
        // what the hands hold (cooking: the food in the hand over the fire, the arms put away)
        ItemDef cookingFood = Rig.cooking(a);
        ItemDef main = cookingFood != null ? cookingFood : a.main(), off = cookingFood != null ? null : a.off();
        boolean bow = main != null && main.family == Family.BOW;
        for (int k = 0; k < 2; k++) {
            ItemDef holds = bow ? (k == 1 ? main : null) : k == 0 ? main : off;
            if (gone[(k == 0 ? Seg.R_ARM_LO : Seg.L_ARM_LO).ordinal()]) continue;
            float[] grip = world(rig.hand[k]), dir = worldDir(rig.itemDir[k]);
            float yaw = a.yaw;
            if (holds != null && a.carryBody == 0 && clock - a.throwAt >= 0.45 && !(a instanceof Npc n && (n.goal == Brain.Goal.LOOT || n.goal == Brain.Goal.AID) && clock < n.lootUntil)) {
                // a shield on the forearm: carried face-out at the side; raised in guard (or seen through the eyes) its face
                // turned to the front, where the blows come from
                if (holds.family == Family.SHIELD) held(holds, grip, new float[]{0, 1, 0}, yaw, !(a.cs.blocking || firstPerson));
                else if (holds.family == Family.BOW) heldBow(holds, grip, dir, yaw, a.cs.draw, world(rig.hand[0]));
                else held(holds, grip, dir, yaw, !firstPerson);   // show the broad face through the eyes; carry edge-forward in third person
            }
            ItemDef ammo = a.eq.def(Slot.AMMO);
            if (k == 0 && ammo != null) {
                if (bow && a.cs.draw > 0 && ammo.family == Family.ARROW) held(ammo, grip, dir, yaw, false);
                else if (main != null && main.family == Family.CROSSBOW && a.cs.reload <= 0 && ammo.family == Family.BOLT)
                    held(ammo, add(grip, dir, 0.1f), dir, yaw, false);
            }
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
        m.scale(3.6f, 0.7f, 3.6f);
        put(mesh("wound", Meshes::wound), 0xFFFFFF, 1, 0.55f, 0);
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
                    case HEAD -> af.headClosed; case TAIL -> af.tail;
                    case ARM_R, ARM_L -> af.bird ? af.wing : af.legFront;
                    default -> af.bird ? af.legFront : af.legBack;
                };
                if (md == null) continue;
                r.matrix(m16);
                System.arraycopy(m16, 0, body.m, 0, 16);
                float vs = AnimalModels.PX * l.scale;
                var vm = md.vox();
                part(md, vs, -(vm.sx / 2f - md.gx()), -(vm.sy / 2f - md.gy()), -(vm.sz / 2f - md.gz()), 0, 0, 0);
                m.set(body).translate((md.gx() - vm.sx / 2f) * vs, (md.gy() - vm.sy / 2f) * vs, (md.gz() - vm.sz / 2f) * vs);
                if (l.part == rpg.combat.Gore.Part.HEAD || l.part == rpg.combat.Gore.Part.TAIL) m.rotateX((float) Math.PI / 2);
                m.scale(Math.min(0.3f, vm.sx * vs * 0.75f), 1, Math.min(0.3f, Math.min(vm.sx, vm.sz) * vs * 0.75f));
                put(mesh("wound", Meshes::wound), 0xFFFFFF, 1, 0.55f, 0);
                continue;
            }
            Actor owner = g.actor(l.owner);
            if (owner == null) owner = new Actor(0, "", 0);
            int[][] fig = figure(owner, l.look);
            r.matrix(m16);
            float px = Body.PX;
            Seg[] segs = segsOf(l.part);
            if (l.part == rpg.combat.Gore.Part.HEAD) {
                // the head's segment stands up from the neck: the box's centre is half a head above it
                System.arraycopy(m16, 0, root.m, 0, 16);
                m.set(root).translate(0, -l.length / 2, 0).scale(px, px, px);
                put(fig[0][Seg.HEAD.ordinal()], 0, 0.9f, 0.9f, 0.9f, 0, 1, 0.85f, 0);
                put(fig[1][Seg.HEAD.ordinal()], 0, 0.9f, 0.9f, 0.9f, 0, 1, 0.85f, 0);
                headBase.set(m);
                headFace(owner, l.look, Body.of(l.look), true, false);
                m.set(root).translate(0, -l.length / 2 - 0.004f, 0).scale(0.24f, 1, 0.24f);
                put(mesh("wound", Meshes::wound), 0xFFFFFF, 1, 0.55f, 0);
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
            m.set(root).translate(0, l.length / 2 + 0.004f, 0).scale(0.22f, 1, 0.22f);
            put(mesh("wound", Meshes::wound), 0xFFFFFF, 1, 0.55f, 0);
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

    /**
     * The throw guide (gamefixes): a white, glowing, dotted line in the world along the very path the throw will fly
     * (ThrowSolver), dots evenly spaced and fading from full to 40% toward its end, with a ring where it lands - white,
     * or red when the way is blocked or the landing too close. It is drawn as the world is: hills and walls hide it.
     */
    private final float[] guide = new float[12 * 160];
    private float[] merged = new float[12 * 256];
    private void throwGuide(Rpg game) {
        Actor actor = game.player;
        float[] eye = {camX, camY, camZ};
        if (!rpg.combat.CombatSystem.aimThrow(game, actor, eye)) return;
        var plan = actor.throwPlan;
        if (plan.count < 2) return;
        // dots every 0.22 m along the cut path (the "tiled" dotted line), alpha 1.0 down to 0.4
        float total = 0;
        for (int i = 1; i < plan.count; i++) total += dist(plan.points[i - 1], plan.points[i]);
        int n = 0;
        float along = 0, next = 0.35f;   // the first dots would sit in the hand
        for (int i = 1; i < plan.count && n < 150; i++) {
            float[] p0 = plan.points[i - 1], p1 = plan.points[i];
            float seg = dist(p0, p1);
            while (next <= along + seg && n < 150) {
                float t = seg <= 0 ? 0 : (next - along) / seg, fade = 1 - 0.6f * (next / Math.max(0.01f, total));
                int o = n * 12;
                guide[o] = p0[0] + (p1[0] - p0[0]) * t; guide[o + 1] = p0[1] + (p1[1] - p0[1]) * t; guide[o + 2] = p0[2] + (p1[2] - p0[2]) * t;
                guide[o + 3] = 0.045f;
                guide[o + 4] = 2.4f; guide[o + 5] = 2.4f; guide[o + 6] = 2.4f; guide[o + 7] = fade;
                guide[o + 8] = 0; guide[o + 9] = 2; guide[o + 10] = 0; guide[o + 11] = 0.5f;
                n++; next += 0.22f;
            }
            along += seg;
        }
        // the landing: a flat ring on what it strikes, and a bright point at its heart
        boolean bad = plan.blocked || plan.tooClose;
        float r = bad ? 3.2f : 2.6f, gr = bad ? 0.35f : 2.6f, b = bad ? 0.3f : 2.6f;
        int o = n * 12;
        guide[o] = plan.landing[0]; guide[o + 1] = plan.landing[1] + 0.03f; guide[o + 2] = plan.landing[2]; guide[o + 3] = 0.32f;
        guide[o + 4] = r; guide[o + 5] = gr; guide[o + 6] = b; guide[o + 7] = 1; guide[o + 8] = 0; guide[o + 9] = 4; guide[o + 10] = 0.05f; guide[o + 11] = 0.3f;
        n++; o = n * 12;
        guide[o] = plan.landing[0]; guide[o + 1] = plan.landing[1] + 0.05f; guide[o + 2] = plan.landing[2]; guide[o + 3] = 0.09f;
        guide[o + 4] = r; guide[o + 5] = gr; guide[o + 6] = b; guide[o + 7] = 1; guide[o + 8] = 0; guide[o + 9] = 2; guide[o + 10] = 0; guide[o + 11] = 0.7f;
        n++;
        guideCount = n;
    }
    /** How many of the guide's points this frame (sent with the frame's other particles: one particle list a frame). */
    private int guideCount;
    private static float dist(float[] a, float[] b) { return (float) Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])); }

    private final int[] portraitMeshes = new int[8], portraitColors = new int[8];
    private final float[][] portraitMatrices = new float[8][16];
    private final Mat4 portraitRoot = new Mat4(), portraitHead = new Mat4();
    private int portraitCount;

    /** The actual NPC head geometry, rendered directly into a small native UI viewport. */
    public void portrait(Npc npc, float x, float y, float w, float h, double now, float alpha) {
        Appearance look = npc.look; Body b = npc.plan();
        portraitCount = 0;
        portraitRoot.identity().translate(0, 0.012f * (float) Math.sin(now * 2.1), 0)
                .rotateY(0.16f + 0.08f * (float) Math.sin(now * 1.2)).rotateX(-0.06f)
                .translate(0, -4 * Body.PX, 0).scale(Body.PX, Body.PX, Body.PX);
        int[][] figure = figure(npc, look);
        portraitPart(figure[0][Seg.HEAD.ordinal()], portraitRoot, 0xFFFFFF);
        portraitPart(figure[1][Seg.HEAD.ordinal()], portraitRoot, 0xFFFFFF);
        portraitHead.set(portraitRoot).translate(0, 4, 0).scale(b.head[0], b.head[1], b.head[2]);
        portraitPart(mesh("hair:" + look.female + ":" + look.hair, () -> Meshes.hair(look.female, look.hair)), portraitHead, Appearance.hairColor(look.hairColor));
        if (!look.female && look.beard > 0) portraitPart(mesh("beard:" + look.beard, () -> Meshes.beard(look.beard)), portraitHead, Appearance.hairColor(look.beardColor));
        var mood=Faces.mood(sceneGame,npc); int mouth=Faces.speech(now,true);
        portraitPart(expression(look,mood,mouth),portraitHead,0xFFFFFF);
        if(eyesShut(npc,false))portraitPart(mesh("lids:"+look.skinRgb()+":"+look.female,()->Meshes.eyelids(look.skinRgb(),look.female)),portraitHead,0xFFFFFF);
        cmd.portrait(x, y, w, h, portraitMeshes, portraitMatrices, portraitColors, portraitCount, alpha);
    }

    private int expression(Appearance look,Faces.Mood mood,int mouth) {
        return mesh("face:"+look.skin+":"+look.hairColor+":"+look.female+":"+mood+":"+mouth,
                ()->Meshes.expression(look.skinRgb(),Appearance.hairColor(look.hairColor),mood,mouth,look.female));
    }

    private void portraitPart(int mesh, Mat4 matrix, int color) {
        if (mesh == 0) return;
        portraitMeshes[portraitCount] = mesh; portraitColors[portraitCount] = color;
        System.arraycopy(matrix.m, 0, portraitMatrices[portraitCount], 0, 16);
        portraitCount++;
    }

    private final Mat4 headBase = new Mat4(), torsoBase = new Mat4();

    /**
     * What sits on a head, drawn from the head's and torso's matrices in headBase / torsoBase (figure pixels, joints at
     * the origin): the hair (and its fall down the back) unless a helmet hides it, the beard, and - shut - the eyelids.
     * Shared by the living figure and the dead body, so a corpse keeps its hair and lies with its eyes closed.
     */
    private void headUnits(Actor a, Appearance look, Body b, boolean shut, boolean shadowOnly) {
        headFace(a, look, b, shut, shadowOnly);
        if (a.eq.def(Slot.HEAD) == null)
            unit(torsoBase, b.neckY() - b.hipY(), b, mesh("hairhang:" + look.female + ":" + look.hair, () -> Meshes.hairHang(look.female, look.hair)), Appearance.hairColor(look.hairColor), false);
    }

    /** Head-attached details also follow a severed head, whose eyes stay closed. */
    private void headFace(Actor a, Appearance look, Body b, boolean shut, boolean shadowOnly) {
        int hairRgb = Appearance.hairColor(look.hairColor);
        if (a.eq.def(Slot.HEAD) == null) {
            unit(headBase, b.head[1] / 2f, b, mesh("hair:" + look.female + ":" + look.hair, () -> Meshes.hair(look.female, look.hair)), hairRgb, shadowOnly);
        }
        if (!look.female && look.beard > 0) unit(headBase, b.head[1] / 2f, b, mesh("beard:" + look.beard, () -> Meshes.beard(look.beard)), Appearance.hairColor(look.beardColor), shadowOnly);
        if (a.alive && !shadowOnly && a.morphT<=0) {
            Faces.Mood mood=Faces.mood(sceneGame,a);
            int mouth=Faces.speech(clock,a instanceof Npc npc && npc.speechUntil>clock);
            if(mood!=Faces.Mood.NEUTRAL || mouth>0)unit(headBase,b.head[1]/2f,b,expression(look,mood,mouth),0xFFFFFF,false);
        }
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

    /** The review's switch: every figure drawn with its eyes shut (RPG_FIGURE_SHUT in the figures gallery). */
    public static boolean reviewShut;

    /** Sleeping eyes, or a 0.1-second blink on each person's own three-to-six-second rhythm. */
    private boolean eyesShut(Actor a, boolean asleep) {
        if (asleep || reviewShut) return true;
        long h = a.id * 0x9E3779B97F4A7C15L;
        double period = 3.0 + ((h >>> 40) & 1023) / 1023.0 * 3.0;
        double phase = (clock + ((h >>> 20) & 1023) / 1023.0 * period) % period;
        return phase < 0.11 || (((h >>> 12) & 3) == 0 && phase > 0.24 && phase < 0.34);
    }

    /** A worn necklace on the torso (pixels): a chain in a V from either side of the neck to the pendant on the breastbone. */
    private void necklace(float[] torsoLocal, Body b, ItemDef d) {
        boolean dodgy = d.id.equals("dodgy_necklace"), amulet = d.id.endsWith("_amulet");
        int chain = dodgy ? 0x6A4A2C : 0xE2B640;
        float top = b.torso[1], front = -b.torso[2] * b.depth / 2f - 0.9f, half = 1.7f, drop = amulet ? 3.2f : 2.6f;
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
            float flare = l.kind() == Outfit.L_PLEATED ? 0.6f : l.kind() == Outfit.L_ROBE ? 0.45f : 0.35f;
            panels(tm, hipW + flare, hipD + flare, skirt, fwd, back, c, l.kind() == Outfit.L_PLEATED, l.detail() == Outfit.D_SPLIT_FRONT, firstPerson);
            if (l.detail() == Outfit.D_LAYERED) panels(tm, hipW + flare + 0.5f, hipD + flare + 0.5f, skirt * 0.55f, fwd, back, Skin.mix(0xFF000000 | cloth, 0xFFF0EBDD, 0.35f) & 0xFFFFFF, false, false, firstPerson);
            if (l.detail() == Outfit.D_WRAPPED) hangBox(tm, -hipW * 0.25f, 0.2f, -hipD / 2 - 0.05f, hipW * 0.55f, skirt * 0.9f, 0.3f, 0.12f + fwd, (Skin.shade(0xFF000000 | cloth, 0.8f) & 0xFFFFFF), 1);
        }
        // the top's hem below the waist: a tunic's, a long vest's, a robe top's
        if (!bodyArmor) {
            float hem = switch (t.hem()) { case Outfit.HEM_HIP -> 3.4f; case Outfit.HEM_THIGH -> 6.5f; case Outfit.HEM_KNEE -> 11.2f; default -> 0; };
            if (hem > 0) {
                float out = 0.4f + t.loose() * 0.9f + (skirt > 0 ? 0.5f : 0);
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
        float flare = 0.05f + Math.max(0, (w - 8) * 0.012f);
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
        for (int i = 0; i < n; i++) {
            float px = pxs[i], pz = pzs[i], nx = nxs[i], nz = nzs[i], nf = -nz, out = outs[i];
            if (splitFront && nf > 0.93f) continue;
            float yaw = (float) Math.atan2(nx, nz);
            // (gamefixes: the cloth looked bad) wide enough to close the ring at the hem, not only at the waist: a strip
            // as narrow as the waist's share opened into a fringe of slits the further it swung out
            float stripW = Math.max(circ / n * 1.35f, (circ + 2 * (float) Math.PI * len * (float) Math.sin(out)) / n * 1.15f);
            System.arraycopy(torsoLocal, 0, seg.m, 0, 16);
            m.set(root).mul(seg).translate(px, 0.8f, pz).rotateY(yaw).rotateX(-out).translate(0, -len / 2, 0).scale(stripW, len, 0.6f);
            float sh = (nf > 0 ? 1f : 0.93f) * (pleats && i % 2 == 1 ? 0.9f : 1f);
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
        a.poseX=a.x; a.poseY=a.y; a.poseZ=a.z; a.poseYaw=a.yaw;
    }

    private void putPose(Actor a, int i, float[] figurePoint) { float[] w = world(figurePoint); a.posePts[i * 3] = w[0]; a.posePts[i * 3 + 1] = w[1]; a.posePts[i * 3 + 2] = w[2]; }

    /** A dead NPC: their own figure's segments laid between the ragdoll's particles as the engine has them. */
    private void ragdoll(Snapshots.Ragdoll rd, Actor n) {
        int[][] fig = figure(n, n.look);
        // A fallen or carried body's shoulders define its frame; world yaw loses roll and pitch.
        int ls = AstrattoNative.RD_L_SHOULDER * 3, rs = AstrattoNative.RD_R_SHOULDER * 3;
        float[] rr = norm(new float[]{rd.p[rs] - rd.p[ls], rd.p[rs + 1] - rd.p[ls + 1], rd.p[rs + 2] - rd.p[ls + 2]});
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
            boolean bow = main != null && main.family == Family.BOW;
            for (int k = 0; k < 2; k++) {
                ItemDef holds = bow ? (k == 1 ? main : null) : k == 0 ? main : off;
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
    private void object(Rpg g,WorldObject o,float dt) {
        float previous=charTint;charTint=1-o.charred*0.86f;
        try { objectModel(g,o,dt); } finally { charTint=previous; }
    }
    private void objectModel(Rpg g, WorldObject o, float dt) {
        if (o instanceof GroundItem gi && gi.dir != null) {
            // one arrow, bolt or thrown blade - or a weapon fallen from a dead hand - exactly as it lies: lodged where it
            // struck, or as its body came to rest
            float len = CombatSystem.lyingLength(gi.stack.def);
            held(gi.stack.def, new float[]{gi.x - gi.dir[0] * len / 2, gi.y - gi.dir[1] * len / 2, gi.z - gi.dir[2] * len / 2}, gi.dir, gi.id * 1.7f, false);
        } else if (o instanceof GroundItem gi) {
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
        } else if (o instanceof rpg.dungeon.Dungeons.Door dd) {
            // a dungeon's mouth in the wilds (its theme's stone round a black way down), or the way out inside
            m.identity().translate(dd.x, DevMap.FLOOR, dd.z).rotateY(dd.inside ? (float) (Math.PI / 2) : 0);
            int theme = dd.layout.theme.ordinal();
            drawLayers(dd.inside ? "dungeon-exit" : "dungeon-entrance:" + theme, () -> dd.inside ? Props.dungeonExit() : Props.dungeonEntrance(theme));
        } else if (o instanceof rpg.dungeon.Dungeons.Trap tr) {
            // a trap's plate always shows to a careful eye (found, it is marked); the spikes or the rock as it goes off
            float since = (float) (clock - tr.sprungAt);
            {
                int state = tr.disarmed ? 2 : tr.found ? 1 : 0;
                m.identity().translate(tr.x, DevMap.FLOOR, tr.z);
                drawLayers("dungeon-plate:" + state + ":" + tr.kind, () -> Props.trapPlate(state, tr.kind));
                if (since >= 0 && since < 1.2f) {
                    float up = since < 0.08f ? since / 0.08f : since < 0.9f ? 1 : 1 - (since - 0.9f) / 0.3f;
                    if (tr.kind == 0) { m.identity().translate(tr.x, DevMap.FLOOR - 0.55f + 0.6f * up, tr.z); drawLayers("dungeon-spikes", Props::trapSpikes); }
                    else { m.identity().translate(tr.x, DevMap.FLOOR + 2.6f - Math.min(1, since / 0.35f) * 2.45f, tr.z).rotateY(since * 3); drawLayers("dungeon-rocks", Props::trapRocks); }
                }
            }
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
        } else if(o instanceof rpg.objects.FishingNet) {
            m.identity().translate(o.x,o.y+0.08f,o.z);
            drawLayers("deployed-lake-net",Props::fishingNet);
        } else if(o instanceof rpg.objects.Trap trap) {
            boolean armed=trap.caught==0 && trap.contents.has(rpg.item.Items.SNARE,1);
            m.identity().translate(o.x,o.y+0.012f,o.z);
            drawLayers("deployed-snare:"+armed,()->Props.snare(armed));
        } else if (o instanceof rpg.objects.Corpse) {
            // a body is searched where it lies: the body itself is the container, so nothing is drawn over it
        } else if (o instanceof Container c) {
            if (c.kind.equals("claim") || c.kind.equals("escrow") || c.kind.equals("vault")) return;
            if (c.kind.equals("grave") && c.heldBy != 0) {
                // carried in by a guard: a bundle of the dead's things in a sack over his back
                Actor carrier = g.actor(c.heldBy);
                if (carrier != null) {
                    float yaw = carrier.yaw, bx = (float) Math.sin(yaw) * 0.32f, bz = (float) Math.cos(yaw) * 0.32f;
                    m.identity().translate(carrier.x + bx, carrier.y + 1.05f, carrier.z + bz).rotateY(yaw).rotateX(0.25f).scale(0.5f, 0.55f, 0.32f); put(box, 0x7A6444, 1, 0.95f, 0);
                    m.identity().translate(carrier.x + bx, carrier.y + 1.36f, carrier.z + bz).rotateY(yaw).scale(0.16f, 0.1f, 0.16f); put(box, 0x5A4630, 1, 0.95f, 0);
                }
            } else if (c.kind.equals("grave") || c.kind.equals("memorial")) {
                // a fresh grave: turned earth and a plain new stone, a candle burning at its foot while it stands
                m.identity().translate(c.x, DevMap.FLOOR, c.z + (c.burialPlot.isEmpty()?0.45f:0));
                if(c.kind.equals("memorial") && !c.buried) {
                    drawLayers("unfilled-stone:"+(c.id&7),()->new Props.Layer[]{Props.memorial(c.honor,(int)(c.id&7))[0]});return;
                }
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
        } else if (o instanceof Fixtures.Board) {
            m.identity().translate(o.x, o.y - 0.2f, o.z).scale(1.5f, 1.5f, 0.14f); put(box, 0x60452C, 1, 0.85f, 0);
            for (int side = -1; side <= 1; side += 2) { m.identity().translate(o.x + side * 0.55f, o.y - 0.55f, o.z).scale(0.1f, 1.3f, 0.14f); put(box, 0x49331E, 1, 0.85f, 0); }
            for (int page = -1; page <= 1; page++) { m.identity().translate(o.x + page * 0.4f, o.y - 0.12f + (page == 0 ? 0.12f : 0), o.z + 0.08f).scale(0.3f, 0.53f, 0.015f); put(box, 0xBCA67A, 1, 0.95f, 0); }
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
            if ((int) (g.time * 3 + nd.id) % 4 == 0) cmd.emitter(nd.id+0x300000000L,8,nd.x,nd.y,nd.z,0.05f,5,0.3f,0.5f,0.6f,0.85f,1,0,0.8f,0);
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
            float leafW = d.leafWidth, h = d.height - 0.04f;
            m.translate(-leafW / 2, -h / 2 - 0.02f, 0).scale(leafW, 1, 1);
            drawLayers(key, () -> Props.doorLeaf(d.height, d.locked));
        }
    }

    /** What SpellFx draws with: a voxel ball, a beam, a tumbling rock, a light. */
    private final SpellFx.Draw spellDraw = new SpellFx.Draw() {
        @Override public void orb(float x, float y, float z, float size, float r, float g, float b, float glow) {
            m.identity().translate(x, y, z).scale(size * 2, size * 2, size * 2);
            put(ball, AstrattoNative.MODEL_NO_SHADOW, r, g, b, glow, 1, 0.3f, 0);
        }
        @Override public void beam(float[] a, float[] b, float width, float r, float g, float bl, float glow) {
            float[] y = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
            float len = (float) Math.sqrt(y[0] * y[0] + y[1] * y[1] + y[2] * y[2]);
            if (len < 1e-4f) return;
            y = new float[]{y[0] / len, y[1] / len, y[2] / len};
            float[] x = norm(cross(y, Math.abs(y[1]) > 0.9f ? new float[]{1, 0, 0} : new float[]{0, 1, 0})), z = cross(x, y);
            float[] mm = m.m;
            mm[0] = x[0] * width; mm[1] = x[1] * width; mm[2] = x[2] * width; mm[3] = 0;
            mm[4] = y[0] * len; mm[5] = y[1] * len; mm[6] = y[2] * len; mm[7] = 0;
            mm[8] = z[0] * width; mm[9] = z[1] * width; mm[10] = z[2] * width; mm[11] = 0;
            mm[12] = (a[0] + b[0]) / 2; mm[13] = (a[1] + b[1]) / 2; mm[14] = (a[2] + b[2]) / 2; mm[15] = 1;
            put(box, AstrattoNative.MODEL_NO_SHADOW, r, g, bl, glow, 1, 0.3f, 0);
        }
        @Override public void rock(float x, float y, float z, float size, float angle, int rgb) {
            m.identity().translate(x, y, z).rotateY(angle).rotateX(angle * 0.7f).scale(size, size * 0.85f, size * 0.9f);
            put(box, 0, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 0, 1, 0.9f, 0);
        }
        @Override public void light(float x, float y, float z, float radius, float r, float g, float b) { cmd.pointLight(x, y, z, radius, r, g, b, 0); }
        @Override public float ground(float x, float y, float z) { return sceneGame == null ? DevMap.FLOOR : sceneGame.vox.groundY(x, y, z); }
    };

    /** A spell in flight, in its element's colour (a larger tier glows brighter: see the light it casts). */
    private static float[] spellColour(Spells.Spell s) {
        return switch (s.element) {
            case FIRE -> new float[]{1, 0.45f, 0.1f}; case WATER -> new float[]{0.25f, 0.55f, 1}; case EARTH -> new float[]{0.62f, 0.45f, 0.22f};
            case AIR -> new float[]{0.85f, 0.95f, 1}; case ELECTRIC -> new float[]{1, 0.95f, 0.35f}; case ICE -> new float[]{0.4f, 0.8f, 1};
            case BLOOD -> new float[]{0.7f, 0.08f, 0.1f}; default -> new float[]{0.8f, 0.8f, 1};
        };
    }

    private void projectile(CombatSystem.Projectile p) {
        float l = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
        if (l < 1e-3f) return;
        float[] d = {p.vx / l, p.vy / l, p.vz / l};
        if (p.spell != null) {
            float[] col = spellColour(p.spell);
            m.identity().translate(p.x, p.y, p.z).scale(0.18f, 0.18f, 0.18f);
            put(box, AstrattoNative.MODEL_NO_SHADOW, col[0], col[1], col[2], 4f, 1, 0.3f, 0);
            return;
        }
        if (p.ammo != null && (p.ammo.def.family == Family.ARROW || p.ammo.def.family == Family.BOLT)) {
            float length = p.ammo.def.family == Family.BOLT ? 0.45f : 0.85f;
            held(p.ammo.def, new float[]{p.x - d[0] * length, p.y - d[1] * length, p.z - d[2] * length}, d, 0, false);
            return;
        }
        if (p.ammo != null && p.ammo.def.family == Family.THROWN) {
            // a thrown weapon tumbles end over end about the axis across its flight (javelins fly nearly true)
            float spin = p.ammo.def.thrown.equals("javelin") ? p.angle * 0.08f : p.angle;
            float kx = -d[2], kz = d[0], kl = (float) Math.max(1e-4, Math.hypot(kx, kz));
            float[] up = norm3(new float[]{-(kz / kl) * d[1], (kz / kl) * d[0] - (kx / kl) * d[2], (kx / kl) * d[1]});
            float c = (float) Math.cos(spin), s = (float) Math.sin(spin);
            float[] t = {d[0] * c + up[0] * s, d[1] * c + up[1] * s, d[2] * c + up[2] * s};
            float half = p.ammo.def.thrown.equals("javelin") ? 0.9f : 0.2f;
            held(p.ammo.def, new float[]{p.x - t[0] * half, p.y - t[1] * half, p.z - t[2] * half}, t, 0, false);
            return;
        }
        limb(new float[]{p.x - d[0] * 0.7f, p.y - d[1] * 0.7f, p.z - d[2] * 0.7f}, new float[]{p.x, p.y, p.z}, 0.025f, 0.025f, 0, 0xB08050, 1, 0.8f, 0);
    }
    private static float[] norm3(float[] v) { float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); return l < 1e-6f ? new float[]{0, 1, 0} : new float[]{v[0] / l, v[1] / l, v[2] / l};
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
        sceneGame = null;
        environment(preview.dungeon ? 22 : 9.5, time);
        if (preview.dungeon) dungeonLights(preview, true);
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
    private void dungeonLights(Preview p, boolean creator) {
        cmd.quality(menuPreferences.quality,AstrattoNative.PASS_GTAO|AstrattoNative.PASS_VOLUMETRICS|AstrattoNative.PASS_TAA|AstrattoNative.PASS_BLOOM,menuPreferences.renderScale);
        cmd.pointLight(p.x-2.8f,p.y+2.2f,p.z-2.6f,7,5.5f,2.3f,0.65f,0);
        cmd.pointLight(p.x+1.2f,p.y+2.8f,p.z+2,6,creator?3.8f:2.5f,creator?3.3f:1.7f,creator?2.8f:1.1f,AstrattoNative.LIGHT_SHADOW);
        cmd.pointLight(p.x+3,p.y+1.8f,p.z-1.5f,5,0.35f,0.55f,1.1f,0);
        for (int side : new int[]{-1,1}) {
            float x=p.x+side*3,z=p.z-2.7f;
            m.identity().translate(x,p.y+0.75f,z).scale(0.24f,1.5f,0.24f);put(box,0x4B4540,1,0.9f,0);
            m.identity().translate(x,p.y+1.58f,z).scale(0.5f,0.2f,0.5f);put(box,0x4A3730,1,0.65f,0.4f);
            cmd.emitter(side+20,0,x,p.y+2,z,0.12f,16,0.05f,0.6f,6,2.1f,0.4f,0,0.7f,0);
        }
    }

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
        sceneGame = null;
        float daylight = environment(preview.dungeon ? 22 : 17.9, time);
        if (preview.dungeon) dungeonLights(preview, false);
        if (daylight < 0.6f) lampLights(null);
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
        public boolean dungeon;
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
