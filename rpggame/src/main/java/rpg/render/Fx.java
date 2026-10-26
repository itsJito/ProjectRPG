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
    public record Floating(String text, float x, float y, float z, int color, double born, float rise) {}

    private final List<P> ps = new ArrayList<>();
    public final List<Floating> floating = new ArrayList<>();
    private final Random rnd = new Random(5);
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

    private P add(int kind, float x, float y, float z, float vx, float vy, float vz, float size, float r, float g, float b, float a, float life, float gravity, float drag) {
        P p = new P();
        p.kind = kind; p.x = x; p.y = y; p.z = z; p.vx = vx; p.vy = vy; p.vz = vz; p.size = size; p.r = r; p.g = g; p.b = b; p.a = a;
        p.life = life; p.gravity = gravity; p.drag = drag; p.seed = rnd.nextFloat();
        if (ps.size() < 4000) ps.add(p);
        return p;
    }

    private float s() { return rnd.nextFloat() * 2 - 1; }

    /** The level-up confetti (gamefixes: smaller and quicker): two quick pops of bright embers just over the head. */
    public void fireworks(float x, float y, float z) {
        float[][] cols = {{6, 2.2f, 0.6f}, {1.2f, 3.5f, 6}, {5, 5, 1.4f}, {5, 1, 4}};
        for (int burst = 0; burst < 2; burst++) {
            float bx = x + s() * 0.45f, by = y + 0.35f + burst * 0.3f, bz = z + s() * 0.45f;
            float[] c = cols[(burst + rnd.nextInt(4)) % 4];
            for (int i = 0; i < 34; i++) {
                float th = rnd.nextFloat() * 6.283f, ph = (float) Math.acos(s()), sp = 1.6f + rnd.nextFloat() * 1.1f;
                add(2, bx, by, bz, (float) (Math.sin(ph) * Math.cos(th)) * sp, (float) Math.cos(ph) * sp, (float) (Math.sin(ph) * Math.sin(th)) * sp,
                        0.04f, c[0], c[1], c[2], 1, 0.45f + rnd.nextFloat() * 0.3f, 3.5f, 2.4f);
            }
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
