package rpg.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Presentation-only effects (they never change game state): particles handed to the engine's renderer each frame -
 * level-up fireworks, sparks, blood, flames and smoke, splashes, spell trails - and floating text over the world
 * (damage, XP, "Blocked!"), plus camera shake.
 */
public final class Fx {
    static final class P {
        float x, y, z, vx, vy, vz, size, r, g, b, a, life, age, gravity, drag, seed;
        int kind;
    }
    public record Floating(String text, float x, float y, float z, int color, double born, float rise) {
        public boolean near(float px, float py, float pz) {
            float dx = x - px, dy = y - py, dz = z - pz;
            return dx * dx + dy * dy + dz * dz <= 18 * 18;
        }
    }

    private final List<P> ps = new ArrayList<>();
    public final List<Floating> floating = new ArrayList<>();
    final Random rnd = new Random(5);
    public float shake;
    public double now;
    private float[] data = new float[12 * 1024];

    public void update(float dt) {
        for (Iterator<P> it = ps.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.age += dt;
            if (p.age >= p.life) { it.remove(); continue; }
            p.vy -= p.gravity * dt;
            float k = (float) Math.exp(-p.drag * dt);
            p.vx *= k; p.vy *= k; p.vz *= k;
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
        }
        floating.removeIf(f -> now - f.born() > 1.6);
        lightUpdate(dt);
        shake = Math.max(0, shake - dt * 3f);
    }

    /** The live particles as the renderer's 12-float records; returns the count. */
    public int pack() {
        int n = Math.min(ps.size(), 1024);
        if (data.length < n * 12) data = new float[n * 12];
        for (int i = 0; i < n; i++) {
            P p = ps.get(i);
            float t = p.age / p.life;
            int o = i * 12;
            data[o] = p.x; data[o + 1] = p.y; data[o + 2] = p.z; data[o + 3] = p.size * (p.kind == 1 ? 1 + t * 1.5f : 1);
            data[o + 4] = p.r; data[o + 5] = p.g; data[o + 6] = p.b; data[o + 7] = p.a;
            data[o + 8] = p.seed * 6.28f; data[o + 9] = p.kind; data[o + 10] = t; data[o + 11] = p.seed;
        }
        return n;
    }
    public float[] data() { return data; }

    P add(int kind, float x, float y, float z, float vx, float vy, float vz, float size, float r, float g, float b, float a, float life, float gravity, float drag) {
        P p = new P();
        p.kind = kind; p.x = x; p.y = y; p.z = z; p.vx = vx; p.vy = vy; p.vz = vz; p.size = size; p.r = r; p.g = g; p.b = b; p.a = a;
        p.life = life; p.gravity = gravity; p.drag = drag; p.seed = rnd.nextFloat();
        if (ps.size() < 4000) ps.add(p);
        return p;
    }

    float s() { return rnd.nextFloat() * 2 - 1; }

    // ------------------------------------------------------------------ the level-up's Holy Light (gamefixes)
    /** How long the whole Holy Light lasts (s): the beam's rise, its burst at the top, the sparkles' fall. */
    public static final float LIGHT_TIME = 2.6f, LIGHT_RISE = 0.38f, LIGHT_BURST = 0.62f;
    /** When the light began (-99: none), whether a combat level came with it, and where (the renderer keeps it at the feet). */
    public double lightAt = -99;
    public boolean lightCombat;
    public float lightX, lightY, lightZ;
    private boolean burst;
    private float rain;

    /**
     * A thick beam of gold-white light erupts from the ground under the feet and shoots up; at its top it bursts, and
     * sparkles rain down round the body for a moment after (the renderer draws the beam, the glow and the light).
     */
    public void holyLight(float x, float y, float z, boolean combat) {
        lightAt = now; lightCombat = combat; lightX = x; lightY = y; lightZ = z; burst = false; rain = 0;
        // the eruption at the feet: a ring of motes thrown out low and fast
        for (int i = 0; i < 40; i++) {
            float th = i / 40f * 6.283f, sp = 2.2f + rnd.nextFloat() * 1.4f;
            add(2, x, y + 0.05f, z, (float) Math.cos(th) * sp, 0.6f + rnd.nextFloat() * 0.8f, (float) Math.sin(th) * sp, 0.05f, 6, 5, 2.4f, 1, 0.45f + rnd.nextFloat() * 0.2f, 2, 3.2f);
        }
    }
    /** Seconds into the Holy Light (negative or past LIGHT_TIME: none showing). */
    public float light() { return (float) (now - lightAt); }

    private void lightUpdate(float dt) {
        float t = light();
        if (t < 0 || t > LIGHT_TIME) return;
        float top = lightY + 9.5f;
        if (!burst && t >= LIGHT_BURST) {
            burst = true;
            // the burst at the head of the beam: a sphere of bright gold sparks
            for (int i = 0; i < 110; i++) {
                float th = rnd.nextFloat() * 6.283f, ph = (float) Math.acos(s()), sp = 2.5f + rnd.nextFloat() * 3.5f;
                add(2, lightX, top, lightZ, (float) (Math.sin(ph) * Math.cos(th)) * sp, (float) Math.cos(ph) * sp, (float) (Math.sin(ph) * Math.sin(th)) * sp,
                        0.07f, 7, 6, 3, 1, 0.7f + rnd.nextFloat() * 0.5f, 2.2f, 1.8f);
            }
        }
        // the sparkles raining down round the body after the burst: slow, drifting, gold and white
        if (t >= LIGHT_BURST * 0.8f && t < LIGHT_TIME - 0.5f) {
            rain += dt * 90;
            while (rain >= 1) {
                rain--;
                float a = rnd.nextFloat() * 6.283f, r = 0.3f + rnd.nextFloat() * 1.3f;
                boolean white = rnd.nextFloat() < 0.35f;
                add(2, lightX + (float) Math.cos(a) * r, lightY + 2.6f + rnd.nextFloat() * 1.4f, lightZ + (float) Math.sin(a) * r, s() * 0.15f, -0.6f - rnd.nextFloat() * 0.5f, s() * 0.15f,
                        0.035f + rnd.nextFloat() * 0.025f, white ? 6 : 6.5f, white ? 6 : 4.6f, white ? 5.5f : 1.6f, 1, 1.4f + rnd.nextFloat() * 0.6f, 0.15f, 0.3f);
            }
        }
    }

    /**
     * Something landing in water (gamefixes: bigger, livelier splashes): the harder and heavier, the bigger - a crown of
     * drops thrown up and out round the hole it makes, a jet climbing out of the middle when it went in hard, and
     * white foam left spreading on the surface.
     */
    public void splash(float x, float surface, float z, float energy, float radius) {
        energy = Math.max(0.5f, Math.min(12, energy));
        int crown = (int) (24 + energy * 14 + radius * 30);
        float out = 1.2f + energy * 0.28f, up = 1.6f + energy * 0.55f;
        for (int i = 0; i < crown; i++) {
            float a = rnd.nextFloat() * 6.283f, r = radius * (0.6f + rnd.nextFloat() * 0.5f), sp = out * (0.6f + rnd.nextFloat() * 0.6f);
            add(8, x + (float) Math.cos(a) * r, surface + 0.02f, z + (float) Math.sin(a) * r, (float) Math.cos(a) * sp, up * (0.55f + rnd.nextFloat() * 0.6f), (float) Math.sin(a) * sp,
                    0.03f + rnd.nextFloat() * 0.035f, 0.66f, 0.8f, 0.92f, 0.9f, 0.6f + rnd.nextFloat() * 0.5f, 9.8f, 0.35f);
        }
        if (energy > 4.5f) {
            // the jet: water closing over the hole and thrown straight back up
            int jet = (int) (energy * 6);
            for (int i = 0; i < jet; i++)
                add(8, x + s() * 0.12f, surface + 0.05f, z + s() * 0.12f, s() * 0.35f, energy * (0.55f + rnd.nextFloat() * 0.35f), s() * 0.35f,
                        0.045f + rnd.nextFloat() * 0.03f, 0.7f, 0.82f, 0.94f, 0.9f, 0.8f + rnd.nextFloat() * 0.4f, 9.8f, 0.2f);
        }
        // the foam: white, low, spreading and thinning on the surface
        int foam = (int) (18 + energy * 6);
        for (int i = 0; i < foam; i++) {
            float a = rnd.nextFloat() * 6.283f, sp = 0.4f + rnd.nextFloat() * (0.5f + energy * 0.08f);
            add(1, x + (float) Math.cos(a) * radius * 0.5f, surface + 0.04f, z + (float) Math.sin(a) * radius * 0.5f, (float) Math.cos(a) * sp, 0.02f, (float) Math.sin(a) * sp,
                    0.09f + rnd.nextFloat() * 0.06f, 1.2f, 1.25f, 1.3f, 0.55f, 1.1f + rnd.nextFloat() * 0.8f, 0, 1.6f);
        }
    }

    public void sparks(float x, float y, float z, int n) {
        for (int i = 0; i < n; i++) add(2, x, y, z, s() * 4, rnd.nextFloat() * 4, s() * 4, 0.05f, 6, 4, 1.5f, 1, 0.35f + rnd.nextFloat() * 0.25f, 12, 1);
    }

    /** Wood chips flying from an axe's bite: small square splinters, the colour of fresh wood. */
    public void chips(float x, float y, float z, int n) {
        for (int i = 0; i < n; i++) add(2, x, y, z, s() * 3, 1 + rnd.nextFloat() * 2.5f, s() * 3, 0.05f, 0.85f, 0.62f, 0.38f, 1, 0.5f + rnd.nextFloat() * 0.4f, 16, 0.6f);
    }

    public void blood(float x, float y, float z, float dx, float dz) {
        for (int i = 0; i < 22; i++) {
            boolean gout = i < 4;
            add(2, x, y, z, dx * (gout ? 1.4f : 2.2f) + s() * 1.6f, rnd.nextFloat() * (gout ? 1.6f : 2.8f), dz * (gout ? 1.4f : 2.2f) + s() * 1.6f, gout ? 0.09f : 0.055f, 0.35f, 0.02f, 0.02f, 1, 0.5f + rnd.nextFloat() * 0.35f, 14, 0.5f);
        }
    }

    /** A fine mist of blood hanging a moment in the air where a heavy blow landed. */
    public void bloodMist(float x, float y, float z, float dx, float dz, float amount) {
        int n = 6 + (int) (amount * 18);
        for (int i = 0; i < n; i++) add(2, x + s() * 0.1f, y + s() * 0.1f, z + s() * 0.1f, dx * 1.2f + s() * 0.9f, 0.4f + rnd.nextFloat() * 0.8f, dz * 1.2f + s() * 0.9f, 0.025f, 0.45f, 0.03f, 0.03f, 1, 0.35f + rnd.nextFloat() * 0.25f, 4, 0.35f);
    }

    /** An arterial gout: a jet of heavier drops thrown up and out in an arc. */
    public void spurt(float x, float y, float z, float dx, float dz, float strength) {
        int n = 10 + (int) (strength * 16);
        for (int i = 0; i < n; i++) {
            float t = rnd.nextFloat();
            add(2, x, y, z, dx * (2.2f + t * 2.4f) * strength + s() * 0.3f, (1.6f + t * 1.8f) * strength, dz * (2.2f + t * 2.4f) * strength + s() * 0.3f, 0.055f, 0.33f, 0.015f, 0.015f, 1, 0.6f + rnd.nextFloat() * 0.3f, 14, 0.55f);
        }
    }

    /** One drop falling from a wound: the same square droplet the rain and splashes use, dark red. */
    public void drip(float x, float y, float z) {
        add(2, x + s() * 0.12f, y, z + s() * 0.12f, s() * 0.1f, -0.4f, s() * 0.1f, 0.045f, 0.32f, 0.015f, 0.015f, 1, 0.5f, 14, 0.2f);
    }


    public void ring(float x, float y, float z, float size, float r, float g, float b) { add(4, x, y + 0.05f, z, 0, 0, 0, size, r, g, b, 1, 0.7f, 0, 0); }

    public void trail(float x, float y, float z, float r, float g, float b) { add(2, x, y, z, s() * 0.3f, s() * 0.3f, s() * 0.3f, 0.12f, r, g, b, 1, 0.35f, 0, 2); }

    public void puff(float x, float y, float z) {
        for (int i = 0; i < 8; i++) add(1, x + s() * 0.3f, y, z + s() * 0.3f, s() * 0.5f, 0.6f, s() * 0.5f, 0.4f, 0.55f, 0.52f, 0.48f, 0.5f, 1.4f, 0, 0.8f);
    }

    public void text(String t, float x, float y, float z, int color) { floating.add(new Floating(t, x, y, z, color, now, 0.9f)); }
}
