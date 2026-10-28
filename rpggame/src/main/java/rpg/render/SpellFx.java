package rpg.render;

import java.util.HashMap;
import java.util.Map;
import rpg.combat.Spells.Element;
import rpg.combat.Spells.Spell;
import rpg.combat.Spells.Tier;

/**
 * How spells look (gamefixes: every spell its own animation; the tiered spells one formula, tweaked by element; fire,
 * electricity and water splashes that look like what they are). Presentation only: it reads the spells in flight and
 * the casts, and draws.
 *
 * <p>The formula, by tier, the same for every element: a Strike is a small orb with a short trail; a Bolt a dart drawn
 * out along its flight; a Blast a big orb with motes circling it; a Wave a crescent of orbs swept across its path; a
 * Surge a comet - a great core with satellites spiralling round it and a heavy wake. What it is made of is the
 * element's: fire a ball of flame tongues shedding embers and smoke; water a clear-blue droplet spraying drops that fall;
 * earth tumbling rock with pebbles about it, shedding dust; air pale swirling wisps and gust rings; electricity a
 * white-hot spark with forked lightning crackling off it, struck afresh every few frames; ice spinning shards in frost.
 * Each lands in its own way, as big as its tier: a burst of flame, a splash crown, a spray of rock, a gust ring, a
 * branching discharge, a shatter of ice.
 *
 * <p>The spells with no flight (Forcewave, the Moon book's) are shown as they are cast, each its own.
 */
final class SpellFx {
    /** What it draws with (Gfx): a glowing or solid ball, a glowing beam, a rock. */
    interface Draw {
        void orb(float x, float y, float z, float size, float r, float g, float b, float glow);
        void beam(float[] from, float[] to, float width, float r, float g, float b, float glow);
        void rock(float x, float y, float z, float size, float angle, int rgb);
        void light(float x, float y, float z, float radius, float r, float g, float b);
        /** The ground under a point (the top of the solid below it). */
        float ground(float x, float y, float z);
    }

    private final Fx fx;
    /** The spells in flight last frame (id: spell and where), to show where each one ended. */
    private final Map<Long, float[]> flying = new HashMap<>(), still = new HashMap<>();
    private final Map<Long, Spell> spells = new HashMap<>(), stillSpells = new HashMap<>();
    /** The casts already shown (actor id: cast time). */
    private final Map<Long, Double> shownCast = new HashMap<>();
    private double now;

    SpellFx(Fx fx) { this.fx = fx; }

    /** The element's light colour (r, g, b, over 1 for the hot ones). */
    static float[] colour(Element e) {
        return switch (e) {
            case FIRE -> new float[]{1.6f, 0.62f, 0.14f}; case WATER -> new float[]{0.35f, 0.7f, 1.3f}; case EARTH -> new float[]{0.7f, 0.52f, 0.3f};
            case AIR -> new float[]{1.0f, 1.05f, 1.15f}; case ELECTRIC -> new float[]{1.6f, 1.6f, 1.9f}; case ICE -> new float[]{0.7f, 1.1f, 1.5f};
            case BLOOD -> new float[]{0.8f, 0.06f, 0.08f}; default -> new float[]{0.95f, 0.9f, 1.4f};
        };
    }

    private float s() { return fx.s(); }
    private float rnd() { return fx.rnd.nextFloat(); }

    /** Begin a frame. */
    void frame(double time) {
        now = time;
        still.clear(); still.putAll(flying); stillSpells.clear(); stillSpells.putAll(spells);
        flying.clear(); spells.clear();
    }

    /** One spell in flight: drawn by its tier and element, and remembered (its end is shown when it is gone). */
    void flight(Draw d, long id, Spell sp, float x, float y, float z, float vx, float vy, float vz, float dt) {
        flying.put(id, new float[]{x, y, z, vx, vy, vz}); spells.put(id, sp);
        float l = Math.max(1e-3f, (float) Math.sqrt(vx * vx + vy * vy + vz * vz));
        float[] f = {vx / l, vy / l, vz / l};
        // a frame across the flight: right and up
        float[] r = norm(new float[]{-f[2], 0, f[0]}), u = cross(r, f);
        Tier tier = sp.tier == null ? (sp.element == Element.ICE ? iceTier(sp) : Tier.BOLT) : sp.tier;
        Element e = sp.element == Element.NONE ? Element.NONE : sp.element;
        float[] c = colour(e);
        float t = (float) now;
        // the tier's formula: where its bodies are
        switch (tier) {
            case STRIKE -> body(d, e, c, x, y, z, 0.12f, f, dt, 1, id);
            case BOLT -> {
                body(d, e, c, x, y, z, 0.11f, f, dt, 1.3f, id);
                // drawn out: a tapering streak behind the head
                for (int k = 1; k <= 3; k++) body(d, e, c, x - f[0] * k * 0.16f, y - f[1] * k * 0.16f, z - f[2] * k * 0.16f, 0.1f - k * 0.022f, f, dt, 0.4f, id + k);
            }
            case BLAST -> {
                body(d, e, c, x, y, z, 0.22f, f, dt, 1.8f, id);
                for (int k = 0; k < 4; k++) {
                    double a = t * 9 + k * Math.PI / 2;
                    float ox = (float) Math.cos(a) * 0.34f, oy = (float) Math.sin(a) * 0.34f;
                    body(d, e, c, x + r[0] * ox + u[0] * oy, y + r[1] * ox + u[1] * oy, z + r[2] * ox + u[2] * oy, 0.06f, f, dt, 0.35f, id + 10 + k);
                }
            }
            case WAVE -> {
                // a crescent across the path, bowed forward at its middle
                for (int k = -3; k <= 3; k++) {
                    float w = k / 3f, ox = w * 0.75f, back = w * w * 0.35f;
                    body(d, e, c, x + r[0] * ox - f[0] * back, y + r[1] * ox - f[1] * back + 0.05f * (float) Math.sin(t * 14 + k), z + r[2] * ox - f[2] * back,
                            0.13f - Math.abs(w) * 0.04f, f, dt, 0.55f, id + 20 + k);
                }
            }
            case SURGE -> {
                body(d, e, c, x, y, z, 0.3f, f, dt, 2.4f, id);
                for (int k = 0; k < 3; k++) {
                    double a = t * 7 + k * 2.094;
                    float rad = 0.55f + 0.1f * (float) Math.sin(t * 5 + k);
                    float ox = (float) Math.cos(a) * rad, oy = (float) Math.sin(a) * rad, lag = 0.25f + k * 0.08f;
                    body(d, e, c, x + r[0] * ox + u[0] * oy - f[0] * lag, y + r[1] * ox + u[1] * oy - f[1] * lag, z + r[2] * ox + u[2] * oy - f[2] * lag, 0.1f, f, dt, 0.6f, id + 30 + k);
                }
            }
        }
        float scale = switch (tier) { case STRIKE -> 1; case BOLT -> 1.2f; case BLAST -> 1.7f; case WAVE -> 1.9f; case SURGE -> 2.6f; };
        d.light(x, y, z, 3 + scale * 1.5f, c[0] * scale, c[1] * scale, c[2] * scale);
    }

    /** The Fogish ice spells' own tiers: Rush, Burst, Blitz and Barrage as Strike, Bolt, Blast and Surge. */
    private static Tier iceTier(Spell sp) { return switch (sp) { case ICE_BURST -> Tier.BOLT; case ICE_BLITZ -> Tier.BLAST; case ICE_BARRAGE -> Tier.SURGE; default -> Tier.STRIKE; }; }

    /** One body of the formula, made of its element; 'shed' weighs what it trails behind it. */
    private void body(Draw d, Element e, float[] c, float x, float y, float z, float size, float[] f, float dt, float shed, long seed) {
        float t = (float) now;
        float n = shed * dt * 60;   // particles a frame, at 60 a second
        switch (e) {
            case FIRE -> {
                // a ball of flame: a white-gold heart, tongues licking off it and back along the flight, embers and smoke
                d.orb(x, y, z, size * 0.55f, 1.8f, 1.2f, 0.55f, 5);
                // (short-lived: a ball of fire with a ragged tail, not a jet)
                for (int i = 0; i < Math.max(1, (int) (n * 2)); i++)
                    fx.add(0, x + s() * size * 0.6f, y + s() * size * 0.5f, z + s() * size * 0.6f, -f[0] * 0.8f + s() * 0.3f, 0.35f + rnd() * 0.4f, -f[2] * 0.8f + s() * 0.3f,
                            size * (1.5f + rnd()), 1.15f, 0.62f, 0.3f, 0.95f, 0.12f + rnd() * 0.09f, 0, 1.5f);
                if (rnd() < n * 0.6f) fx.add(2, x, y, z, -f[0] * 2 + s() * 1.4f, rnd() * 1.6f, -f[2] * 2 + s() * 1.4f, 0.025f, 3.5f, 1.4f, 0.3f, 1, 0.5f + rnd() * 0.4f, -1.2f, 1.2f);
                if (rnd() < n * 0.25f) fx.add(1, x - f[0] * 0.3f, y, z - f[2] * 0.3f, s() * 0.2f, 0.5f, s() * 0.2f, size * 1.4f, 0.09f, 0.08f, 0.08f, 0.35f, 0.8f + rnd() * 0.4f, 0, 0.9f);
            }
            case WATER -> {
                // clear blue water, beading and shedding drops that fall away under their own weight
                d.orb(x, y, z, size, 0.25f, 0.55f, 0.95f, 0.9f);
                d.orb(x + 0.02f, y + size * 0.3f, z, size * 0.4f, 0.9f, 0.95f, 1, 1.6f);   // the light caught on it
                for (int i = 0; i < Math.max(1, (int) (n * 2)); i++)
                    fx.add(2, x + s() * size, y + s() * size, z + s() * size, -f[0] * 1.5f + s() * 0.8f, 0.4f + rnd() * 0.6f, -f[2] * 1.5f + s() * 0.8f,
                            0.025f + rnd() * 0.02f, 0.55f, 0.8f, 1.15f, 0.9f, 0.45f + rnd() * 0.3f, 9.8f, 0.3f);
                if (rnd() < n * 0.4f) fx.add(8, x, y, z, s() * 0.6f, 0.2f, s() * 0.6f, 0.06f, 0.6f, 0.8f, 1, 0.5f, 0.6f, 0, 0);
            }
            case EARTH -> {
                // rock, tumbling, with pebbles circling it and dust shaken off
                d.rock(x, y, z, size * 1.9f, t * 6 + seed, 0x6E5A44);
                for (int k = 0; k < 3; k++) {
                    double a = t * 11 + k * 2.1 + seed;
                    d.rock(x + (float) Math.cos(a) * size * 1.6f, y + (float) Math.sin(a * 1.3) * size, z + (float) Math.sin(a) * size * 1.6f, size * 0.35f, t * 9 + k, 0x8A7458);
                }
                if (rnd() < n * 0.5f) fx.add(1, x, y, z, -f[0] * 0.6f + s() * 0.3f, 0.15f, -f[2] * 0.6f + s() * 0.3f, size * 1.4f, 0.2f, 0.17f, 0.13f, 0.4f, 0.7f, 0.3f, 1.2f);
                if (rnd() < n * 0.6f) fx.add(2, x, y, z, s() * 1.5f, rnd() * 1.5f, s() * 1.5f, 0.03f, 0.42f, 0.34f, 0.24f, 1, 0.6f, 9.8f, 0.2f);
            }
            case AIR -> {
                // pale air turning on itself: a faint core, wisps spiralling round the flight, gust rings shed behind
                d.orb(x, y, z, size * 0.5f, 0.95f, 1, 1.05f, 0.8f);
                for (int i = 0; i < Math.max(1, (int) (n * 2)); i++) {
                    double a = t * 20 + i * 2.4 + seed;
                    float ox = (float) Math.cos(a) * size, oy = (float) Math.sin(a) * size;
                    fx.add(5, x + ox, y + oy, z + ox * 0.5f, -f[0] * 2 - oy * 3, -f[1] * 2 + ox * 3, -f[2] * 2 + oy * 3, size * 0.6f, 0.9f, 0.95f, 1, 0.55f, 0.25f + rnd() * 0.1f, 0, 2.5f);
                }
                if (rnd() < n * 0.25f) fx.add(4, x, y - 0.1f, z, 0, 0, 0, size * 3, 0.85f, 0.9f, 0.95f, 0.5f, 0.35f, 0, 0);
            }
            case ELECTRIC -> {
                // a white-hot spark with forked lightning crackling off it, struck again every few frames
                d.orb(x, y, z, size * 0.5f, 2.2f, 2.2f, 2.6f, 8);
                long strike = (long) (now * 24) + seed * 977;
                java.util.Random jag = new java.util.Random(strike);
                int forks = 3 + (int) (size * 12);
                for (int k = 0; k < forks; k++) {
                    float[] a = {x, y, z};
                    float[] dir = norm(new float[]{jag.nextFloat() * 2 - 1 - f[0] * 0.8f, jag.nextFloat() * 2 - 1, jag.nextFloat() * 2 - 1 - f[2] * 0.8f});
                    float reach = size * (2.2f + jag.nextFloat() * 2.5f);
                    int segs = 4;
                    for (int sgm = 0; sgm < segs; sgm++) {
                        float step = reach / segs;
                        float[] b = {a[0] + dir[0] * step + (jag.nextFloat() * 2 - 1) * step * 0.7f, a[1] + dir[1] * step + (jag.nextFloat() * 2 - 1) * step * 0.7f, a[2] + dir[2] * step + (jag.nextFloat() * 2 - 1) * step * 0.7f};
                        float w = 0.03f * (1 - sgm / (float) segs) + 0.01f;
                        d.beam(a, b, w, 1.3f, 1.6f, 2.6f, 9);
                        // a branch off it now and then
                        if (jag.nextFloat() < 0.3f) {
                            float[] br = {b[0] + (jag.nextFloat() * 2 - 1) * step, b[1] + (jag.nextFloat() * 2 - 1) * step, b[2] + (jag.nextFloat() * 2 - 1) * step};
                            d.beam(b, br, w * 0.6f, 1.2f, 1.4f, 2.2f, 5);
                        }
                        a = b;
                    }
                }
                if (rnd() < n) fx.add(2, x + s() * size, y + s() * size, z + s() * size, s() * 3, s() * 3, s() * 3, 0.02f, 3, 3, 4, 1, 0.12f + rnd() * 0.1f, 0, 2);
                // its wake: a jagged line crackling back along the way it came, struck afresh with the forks
                float[] prev = {x, y, z};
                float wake = 0.35f + size * 4;
                for (int sgm = 1; sgm <= 5; sgm++) {
                    float back = wake * sgm / 5;
                    float[] b = {x - f[0] * back + (jag.nextFloat() * 2 - 1) * 0.12f, y - f[1] * back + (jag.nextFloat() * 2 - 1) * 0.12f, z - f[2] * back + (jag.nextFloat() * 2 - 1) * 0.12f};
                    d.beam(prev, b, 0.022f * (1 - sgm / 6f), 1.1f, 1.4f, 2.4f, 6);
                    prev = b;
                }
            }
            case ICE -> {
                // shards turning in a breath of frost
                for (int k = 0; k < 3; k++) d.rock(x + s() * size * 0.3f, y + s() * size * 0.3f, z + s() * size * 0.3f, size * (0.7f - k * 0.15f), t * 8 + k * 2 + seed, 0xBFE6FF);
                d.orb(x, y, z, size * 0.4f, 0.7f, 0.95f, 1.2f, 2);
                if (rnd() < n * 0.4f) fx.add(1, x, y, z, -f[0] * 0.5f + s() * 0.2f, 0.05f, -f[2] * 0.5f + s() * 0.2f, size * 1.0f, 0.42f, 0.46f, 0.5f, 0.25f, 0.6f, 0.5f, 1.2f);
                if (rnd() < n * 0.6f) fx.add(2, x, y, z, s(), s(), s(), 0.02f, 1.2f, 1.5f, 1.8f, 1, 0.4f, 3, 1);
            }
            case BLOOD -> {
                d.orb(x, y, z, size, 0.45f, 0.03f, 0.05f, 1.2f);
                if (rnd() < n) fx.add(2, x, y, z, s() * 0.5f, 0, s() * 0.5f, 0.03f, 0.35f, 0.02f, 0.03f, 1, 0.5f, 9.8f, 0.2f);
            }
            default -> {
                // spectral (Spelldaggers): a cold violet glint and its streak
                d.orb(x, y, z, size * 0.6f, 0.8f, 0.75f, 1.5f, 4);
                if (rnd() < n) fx.add(5, x, y, z, -f[0] * 3, -f[1] * 3, -f[2] * 3, size, 0.7f, 0.6f, 1.4f, 0.7f, 0.2f, 0, 3);
            }
        }
    }

    /** Spells that were in flight last frame and are gone: they landed (or ran out) where they last were. */
    void landings(Draw d) {
        for (var e : still.entrySet()) {
            if (flying.containsKey(e.getKey())) continue;
            float[] p = e.getValue();
            Spell sp = stillSpells.get(e.getKey());
            if (sp != null) impact(d, sp, p[0], p[1], p[2], p[3], p[4], p[5]);
        }
    }

    /**
     * A spell's landing: its element's, as big as its tier. What is thrown off goes back the way the spell came (off a
     * wall it splashes back from the wall); a ring is laid only on ground close under it.
     */
    void impact(Draw d, Spell sp, float x, float y, float z, float vx, float vy, float vz) {
        Tier tier = sp.tier == null ? (sp.element == Element.ICE ? iceTier(sp) : Tier.BOLT) : sp.tier;
        float k = switch (tier) { case STRIKE -> 1; case BOLT -> 1.4f; case BLAST -> 2; case WAVE -> 2.4f; case SURGE -> 3.2f; };
        int n = (int) (12 * k);
        float[] c = colour(sp.element);
        float l = Math.max(1e-3f, (float) Math.sqrt(vx * vx + vy * vy + vz * vz));
        float bx = -vx / l, by = -vy / l, bz = -vz / l;   // back toward where it came from
        float gy = d.ground(x, y, z);
        boolean low = y - gy < 1.1f;
        switch (sp.element) {
            case FIRE -> {
                for (int i = 0; i < n; i++) fx.add(0, x + s() * 0.2f * k, y + s() * 0.15f, z + s() * 0.2f * k, (bx + s() * 0.8f) * k, 0.5f + rnd() * 1.2f, (bz + s() * 0.8f) * k, 0.18f + rnd() * 0.12f * k, 1.0f, 0.55f, 0.25f, 0.85f, 0.28f + rnd() * 0.2f, 0, 2.4f);
                for (int i = 0; i < n; i++) fx.add(2, x, y, z, (bx + s()) * 4, rnd() * 4, (bz + s()) * 4, 0.025f, 3.5f, 1.4f, 0.3f, 1, 0.5f + rnd() * 0.5f, 6, 0.8f);
                for (int i = 0; i < n / 3; i++) fx.add(1, x + s() * 0.3f * k, y + 0.2f, z + s() * 0.3f * k, s() * 0.4f, 0.7f, s() * 0.4f, 0.3f * k, 0.08f, 0.07f, 0.07f, 0.5f, 1.5f + rnd(), 0, 0.8f);
                // flames left licking the ground under a low landing
                if (low) for (int i = 0; i < n / 2; i++) fx.add(0, x + s() * 0.5f * k, gy + 0.05f, z + s() * 0.5f * k, 0, 0.3f, 0, 0.14f, 1, 0.5f, 0.2f, 0.8f, 0.7f + rnd() * 0.6f, 0, 1);
            }
            case WATER -> {
                // a splash: drops thrown back off what it hit in a crown, falling under their weight; a little spray hanging
                for (int i = 0; i < n * 3; i++) {
                    float sp2 = (1.4f + rnd() * 2.2f) * (0.6f + k * 0.3f);
                    float ox = bx + s() * 0.9f, oy = by * 0.5f + 0.6f + rnd() * 0.9f, oz = bz + s() * 0.9f;
                    fx.add(8, x, y, z, ox * sp2, oy * sp2, oz * sp2, 0.028f + rnd() * 0.03f, 0.62f, 0.78f, 0.95f, 0.9f, 0.55f + rnd() * 0.45f, 9.8f, 0.3f);
                }
                for (int i = 0; i < n / 3; i++) fx.add(8, x + s() * 0.15f, y + s() * 0.15f, z + s() * 0.15f, (bx + s() * 0.5f) * 0.8f, 0.3f, (bz + s() * 0.5f) * 0.8f, 0.07f * k, 0.75f, 0.85f, 0.95f, 0.35f, 0.5f, 0.5f, 1.5f);
                if (low) fx.add(4, x, gy + 0.03f, z, 0, 0, 0, 0.6f * k, 0.75f, 0.85f, 0.95f, 0.7f, 0.6f, 0, 0);
            }
            case EARTH -> {
                for (int i = 0; i < n * 2; i++) fx.add(2, x, y, z, (bx + s()) * 2.4f * k * 0.6f, 1 + rnd() * 3, (bz + s()) * 2.4f * k * 0.6f, 0.045f + rnd() * 0.04f, 0.38f, 0.3f, 0.22f, 1, 0.7f + rnd() * 0.4f, 9.8f, 0.2f);
                for (int i = 0; i < n / 3; i++) fx.add(1, x + s() * 0.4f * k, y, z + s() * 0.4f * k, s() * 0.6f, 0.25f, s() * 0.6f, 0.28f * k, 0.2f, 0.17f, 0.13f, 0.45f, 1.3f, 0, 1);
                if (low) fx.add(4, x, gy + 0.03f, z, 0, 0, 0, 0.7f * k, 0.32f, 0.27f, 0.2f, 0.8f, 0.7f, 0, 0);
            }
            case AIR -> {
                if (low) fx.add(4, x, gy + 0.03f, z, 0, 0, 0, 0.9f * k, 0.8f, 0.85f, 0.9f, 0.6f, 0.5f, 0, 0);
                for (int i = 0; i < n * 2; i++) {
                    double a = rnd() * Math.PI * 2;
                    float r1 = (float) Math.cos(a), r2 = (float) Math.sin(a), sp2 = 3.5f * (0.5f + k * 0.3f);
                    fx.add(5, x, y, z, (bx * 0.5f + r1 * bz) * sp2, r2 * 2.5f, (bz * 0.5f - r1 * bx) * sp2, 0.1f, 0.9f, 0.95f, 1, 0.55f, 0.3f, 0, 3);
                }
            }
            case ELECTRIC -> {
                // a discharge: sparks thrown everywhere, a flash, and the forks lingering a moment
                for (int i = 0; i < n * 2; i++) fx.add(2, x, y, z, (bx + s()) * 5, s() * 5, (bz + s()) * 5, 0.02f, 3, 3.2f, 4.2f, 1, 0.15f + rnd() * 0.2f, 0, 2.5f);
                fx.add(6, x, y, z, 0, 0, 0, 0.35f * k, 1.0f, 1.1f, 1.5f, 0.8f, 0.1f, 0, 0);
                impactArcs = new float[]{x, y, z, k, (float) now};
            }
            case ICE -> {
                for (int i = 0; i < n * 2; i++) fx.add(2, x, y, z, (bx + s()) * 3, rnd() * 3, (bz + s()) * 3, 0.035f, 1.1f, 1.4f, 1.7f, 1, 0.6f + rnd() * 0.3f, 9.8f, 0.4f);
                for (int i = 0; i < n / 3; i++) fx.add(1, x + s() * 0.3f, y, z + s() * 0.3f, s() * 0.4f, 0.1f, s() * 0.4f, 0.22f * k, 0.42f, 0.46f, 0.5f, 0.35f, 1.1f, 0, 1);
            }
            default -> { for (int i = 0; i < n; i++) fx.add(2, x, y, z, s() * 3, s() * 3, s() * 3, 0.03f, c[0], c[1], c[2], 1, 0.4f, 2, 1); }
        }
    }

    /** The lightning of an electric landing, drawn for a moment after (x, y, z, size, when). */
    private float[] impactArcs;

    /** Draws what lingers of a landing (the electric discharge's arcs, for a fifth of a second). */
    void lingering(Draw d) {
        if (impactArcs == null) return;
        float age = (float) (now - impactArcs[4]);
        if (age > 0.22f) { impactArcs = null; return; }
        java.util.Random jag = new java.util.Random((long) (now * 30));
        float x = impactArcs[0], y = impactArcs[1], z = impactArcs[2], k = impactArcs[3];
        for (int i = 0; i < 4 + (int) (k * 2); i++) {
            float[] a = {x, y, z};
            double ang = jag.nextFloat() * Math.PI * 2;
            float reach = 0.8f * k;
            for (int sgm = 0; sgm < 5; sgm++) {
                float st = reach / 5;
                float[] b = {a[0] + (float) Math.cos(ang) * st + (jag.nextFloat() * 2 - 1) * st * 0.8f, a[1] - st * 0.6f + (jag.nextFloat() * 2 - 1) * st * 0.6f, a[2] + (float) Math.sin(ang) * st + (jag.nextFloat() * 2 - 1) * st * 0.8f};
                d.beam(a, b, 0.018f * (1 - age * 4), 1.5f, 1.7f, 2.4f, 7);
                a = b;
            }
        }
        d.light(x, y, z, 6 * k, 2.5f, 2.6f, 3.2f);
    }

    /**
     * A cast with no flight to show it: each its own, at the caster (hand, body or feet). Called every frame for each
     * actor; shows a cast once.
     */
    void casting(Draw d, long actor, Spell sp, double castAt, float x, float y, float z, float yaw, float handX, float handY, float handZ) {
        if (sp == null || castAt < 0 || now - castAt > 0.15) return;
        Double shown = shownCast.get(actor);
        if (shown != null && shown == castAt) return;
        shownCast.put(actor, castAt);
        float fx0 = (float) -Math.sin(yaw), fz0 = (float) -Math.cos(yaw);
        float[] c = colour(sp.element);
        // the hand's flash, for every spell (the projectile ones too): a burst of the element's light where it left the hand
        fx.add(6, handX, handY, handZ, 0, 0, 0, sp.tier == null ? 0.25f : 0.18f + sp.tier.ordinal() * 0.05f, c[0], c[1], c[2], 0.9f, 0.12f, 0, 0);
        switch (sp) {
            case FORCEWAVE -> {
                // a cone of air thrown forward: streaks fanning out and a gust ring on the ground ahead
                for (int i = 0; i < 40; i++) { float sp2 = 5 + rnd() * 4, side = s() * 0.6f; fx.add(5, handX, handY, handZ, (fx0 + fz0 * side) * sp2, s() * 1.2f, (fz0 - fx0 * side) * sp2, 0.12f, 0.9f, 0.95f, 1, 0.6f, 0.3f, 0, 2.5f); }
                fx.add(4, x + fx0 * 2.5f, y, z + fz0 * 2.5f, 0, 0, 0, 2.2f, 0.85f, 0.9f, 0.95f, 0.7f, 0.5f, 0, 0);
            }
            case NECROCALL -> {
                // grave-green smoke climbing from a dark ring on the ground
                fx.add(4, x + fx0 * 1.5f, y, z + fz0 * 1.5f, 0, 0, 0, 1.4f, 0.3f, 0.6f, 0.3f, 0.9f, 1.4f, 0, 0);
                for (int i = 0; i < 26; i++) fx.add(1, x + fx0 * 1.5f + s() * 0.5f, y + 0.1f, z + fz0 * 1.5f + s() * 0.5f, s() * 0.2f, 0.6f + rnd() * 0.6f, s() * 0.2f, 0.4f, 0.18f, 0.32f, 0.16f, 0.7f, 1.8f, 0, 0.6f);
            }
            case SPEAK_WITH_ANIMALS -> { for (int i = 0; i < 18; i++) { double a = i * 0.35; fx.add(2, x + (float) Math.cos(a) * 0.6f, y + 1.5f + i * 0.02f, z + (float) Math.sin(a) * 0.6f, -(float) Math.sin(a) * 1.2f, 0.3f, (float) Math.cos(a) * 1.2f, 0.035f, 0.6f, 1.3f, 0.5f, 1, 1.1f, -0.2f, 0.8f); } }
            case MINOR_ILLUSION -> { for (int i = 0; i < 30; i++) fx.add(2, handX + fx0 * 3 + s() * 0.6f, handY + s() * 0.6f, handZ + fz0 * 3 + s() * 0.6f, s() * 0.4f, s() * 0.4f, s() * 0.4f, 0.03f, 1.2f, 0.7f, 1.6f, 1, 0.7f + rnd() * 0.5f, 0, 1); }
            case SPIRITUAL_STEP -> { for (int i = 0; i < 24; i++) fx.add(5, x + s() * 0.3f, y + rnd() * 1.6f, z + s() * 0.3f, fx0 * 3, 0.4f, fz0 * 3, 0.15f, 0.95f, 0.95f, 1.1f, 0.5f, 0.4f, 0, 2); }
            case DARKNESS -> { for (int i = 0; i < 40; i++) fx.add(1, handX + fx0 * 2 + s() * 0.8f, handY + s() * 0.5f, handZ + fz0 * 2 + s() * 0.8f, s() * 0.8f, s() * 0.3f, s() * 0.8f, 0.6f, 0.05f, 0.04f, 0.07f, 0.85f, 2.2f, 0, 0.6f); }
            case TELEGRAB -> { for (int i = 0; i < 20; i++) { float t2 = i / 20f; fx.add(2, handX + fx0 * t2 * 4, handY, handZ + fz0 * t2 * 4, s() * 0.1f, s() * 0.1f, s() * 0.1f, 0.025f, 0.5f, 0.8f, 1.6f, 1, 0.35f, 0, 1); } }
            case MORPHBEAST -> { for (int i = 0; i < 30; i++) { double a = i * 0.5; fx.add(1, x + (float) Math.cos(a) * 0.5f, y + i * 0.05f, z + (float) Math.sin(a) * 0.5f, -(float) Math.sin(a) * 1.5f, 0.4f, (float) Math.cos(a) * 1.5f, 0.3f, 0.6f, 0.45f, 0.25f, 0.6f, 0.9f, 0, 1); } }
            case GASEOUS_FORM -> { for (int i = 0; i < 36; i++) fx.add(1, x + s() * 0.4f, y + rnd() * 1.8f, z + s() * 0.4f, s() * 0.3f, 0.1f, s() * 0.3f, 0.45f, 0.75f, 0.77f, 0.8f, 0.6f, 2, 0, 0.7f); }
            case DISGUISE_SELF -> { for (int i = 0; i < 30; i++) fx.add(2, x + s() * 0.35f, y + rnd() * 1.8f, z + s() * 0.35f, 0, 0.2f, 0, 0.03f, 1.4f, 1.3f, 1.6f, 1, 0.6f + rnd() * 0.4f, 0, 0.5f); }
            case ENHANCE_ABILITY -> { for (int i = 0; i < 26; i++) fx.add(2, x + s() * 0.4f, y + rnd() * 0.3f, z + s() * 0.4f, 0, 1.2f + rnd(), 0, 0.03f, 1.8f, 1.4f, 0.5f, 1, 1.0f, -0.3f, 0.6f); }
            case CHRONOSLOW -> {
                fx.add(4, x, y + 0.05f, z, 0, 0, 0, 3, 0.6f, 0.6f, 1.4f, 0.8f, 1.6f, 0, 0);
                for (int i = 0; i < 16; i++) { double a = i * Math.PI / 8; fx.add(2, x + (float) Math.cos(a) * 2.5f, y + 1, z + (float) Math.sin(a) * 2.5f, -(float) Math.sin(a) * 0.4f, 0, (float) Math.cos(a) * 0.4f, 0.04f, 0.8f, 0.8f, 1.6f, 1, 1.6f, 0, 0); }
            }
            case FRIENDS, CHARM_PERSON -> { for (int i = 0; i < 22; i++) fx.add(2, handX + fx0 * (1 + rnd() * 2), handY + s() * 0.3f, handZ + fz0 * (1 + rnd() * 2), s() * 0.3f, 0.4f, s() * 0.3f, 0.035f, 1.6f, 0.6f, 1.0f, 1, 0.8f, -0.2f, 0.8f); }
            case VITALMEND -> { for (int i = 0; i < 28; i++) fx.add(2, x + s() * 0.4f, y + rnd() * 1.2f, z + s() * 0.4f, 0, 0.8f + rnd() * 0.6f, 0, 0.035f, 0.6f, 1.6f, 0.6f, 1, 1.1f, -0.3f, 0.6f); }
            default -> { }
        }
    }

    private static float[] norm(float[] a) { float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return l < 1e-6f ? new float[]{1, 0, 0} : new float[]{a[0] / l, a[1] / l, a[2] / l}; }
    private static float[] cross(float[] a, float[] b) { return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
}
