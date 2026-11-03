package rpg.render;

import astratto.bridge.RenderCommands;
import astratto.weather.Weather;

/**
 * (0.3.0, gamefixes: atmosphere and lighting) Reusable environment presets: the light and grade of each part of the day,
 * changed by the weather and by being under a roof, blended as the hour turns - so sky, sun, ambient, haze and grade
 * move together through every transition instead of each on its own curve.
 *
 * <p>A preset is lighting first: a six-axis ambient (sky from above, the ground's bounce from below, the sun's side
 * warmer than the shade's - an ambient cube, weighted on each surface by its normal, so form reads in shadow and the
 * silhouettes against the sky stay strong), then the finishing grade - exposure, saturation, contrast and a tint - kept
 * restrained. Under a roof little of the sky's light reaches (the dark gaps between lamps), the grade warms slightly so
 * lamp-lit pools glow against the cool of the room around them.
 */
public final class Atmosphere {
    /** One preset: ambient sky (up), ground (down), sun side, shade side (linear rgb), then exposure, saturation, contrast, tint. */
    public record Preset(String name, float[] sky, float[] ground, float[] sun, float[] shade, float exposure, float saturation, float contrast, float[] tint) {
        Preset mix(Preset o, float t) {
            return new Preset(t < 0.5f ? name : o.name, lerp(sky, o.sky, t), lerp(ground, o.ground, t), lerp(sun, o.sun, t), lerp(shade, o.shade, t),
                    exposure + (o.exposure - exposure) * t, saturation + (o.saturation - saturation) * t, contrast + (o.contrast - contrast) * t, lerp(tint, o.tint, t));
        }
    }
    private static float[] v(float r, float g, float b) { return new float[]{r, g, b}; }
    private static float[] lerp(float[] a, float[] b, float t) { return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}; }

    public static final Preset NIGHT = new Preset("night", v(0.035f, 0.05f, 0.10f), v(0.010f, 0.010f, 0.016f), v(0.04f, 0.05f, 0.09f), v(0.022f, 0.028f, 0.05f), 0.95f, 0.82f, 1.08f, v(0.93f, 0.97f, 1.07f));
    public static final Preset DAWN = new Preset("dawn", v(0.11f, 0.12f, 0.17f), v(0.05f, 0.04f, 0.035f), v(0.20f, 0.13f, 0.08f), v(0.06f, 0.07f, 0.11f), 1.03f, 1.04f, 1.05f, v(1.05f, 0.99f, 0.93f));
    public static final Preset DAY = new Preset("day", v(0.12f, 0.15f, 0.21f), v(0.06f, 0.06f, 0.04f), v(0.11f, 0.10f, 0.08f), v(0.07f, 0.08f, 0.11f), 1.0f, 1.0f, 1.05f, v(1.0f, 1.0f, 1.0f));
    public static final Preset GOLDEN = new Preset("golden", v(0.10f, 0.10f, 0.14f), v(0.06f, 0.045f, 0.03f), v(0.24f, 0.14f, 0.07f), v(0.05f, 0.06f, 0.10f), 1.02f, 1.07f, 1.07f, v(1.07f, 0.99f, 0.90f));
    public static final Preset DUSK = new Preset("dusk", v(0.07f, 0.065f, 0.12f), v(0.025f, 0.02f, 0.025f), v(0.13f, 0.07f, 0.08f), v(0.035f, 0.04f, 0.075f), 0.98f, 0.96f, 1.07f, v(1.03f, 0.96f, 1.02f));

    /** The keys of the day: an hour and its preset, blended between neighbours. */
    private static final Object[][] DAYKEYS = {
            {0.0, NIGHT}, {4.8, NIGHT}, {6.3, DAWN}, {8.0, DAY}, {16.2, DAY}, {17.8, GOLDEN}, {19.3, DUSK}, {20.8, NIGHT}, {24.0, NIGHT}};

    /** The preset for an hour of the day (before weather and roofs). */
    public static Preset forHour(double hour) {
        hour = ((hour % 24) + 24) % 24;
        for (int i = 0; i < DAYKEYS.length - 1; i++) {
            double h0 = (double) DAYKEYS[i][0], h1 = (double) DAYKEYS[i + 1][0];
            if (hour >= h0 && hour <= h1) {
                float t = (float) ((hour - h0) / Math.max(1e-6, h1 - h0));
                t = t * t * (3 - 2 * t);
                return ((Preset) DAYKEYS[i][1]).mix((Preset) DAYKEYS[i + 1][1], t);
            }
        }
        return NIGHT;
    }

    /**
     * The weather's hand on it: cloud flattens the sun's side into the shade's and greys the sky; rain and storm darken
     * and cool it and drain the colour; fog lifts the shadows toward its own colour and softens the contrast; snow
     * brightens the ground's bounce.
     */
    public static Preset weather(Preset p, Weather w, float[] fog) {
        if (w == null) return p;
        float cloud = Math.max(w.overcast, w.rain * 0.8f), rain = w.rain, fogK = w.fog, snow = w.snowCover;
        float[] flatSide = lerp(p.sun, p.shade, 0.5f);
        float[] sun = lerp(p.sun, flatSide, cloud * 0.85f), shade = lerp(p.shade, flatSide, cloud * 0.6f);
        float[] grey = v((p.sky[0] + p.sky[1] + p.sky[2]) / 3, (p.sky[0] + p.sky[1] + p.sky[2]) / 3, (p.sky[0] + p.sky[1] + p.sky[2]) / 3 * 1.06f);
        float[] sky = lerp(p.sky, grey, cloud * 0.7f);
        float dark = 1 - 0.12f * rain;
        sky = scale(sky, dark); sun = scale(sun, dark); shade = scale(shade, dark);
        float[] ground = lerp(p.ground, v(p.ground[0] + 0.08f, p.ground[1] + 0.08f, p.ground[2] + 0.09f), snow);
        if (fog != null && fogK > 0) { float k = Math.min(0.5f, fogK * 0.6f); shade = lerp(shade, scale(fog, 0.25f), k); ground = lerp(ground, scale(fog, 0.15f), k); }
        float[] tint = lerp(p.tint, v(p.tint[0] * 0.95f, p.tint[1] * 0.98f, p.tint[2] * 1.05f), rain);
        return new Preset(p.name + (rain > 0.4f ? "+rain" : cloud > 0.5f ? "+cloud" : ""), sky, ground, sun, shade, p.exposure * (1 + 0.04f * cloud + 0.12f * rain),
                p.saturation * (1 - 0.14f * cloud - 0.1f * rain), p.contrast * (1 - 0.05f * cloud - 0.08f * fogK), tint);
    }

    /** Under a roof: the sky's ambient mostly shut out (dark gaps), the grade warmed and the contrast raised a touch. */
    public static Preset interior(Preset p, float k) {
        if (k <= 0) return p;
        float keep = 1 - 0.7f * k;
        float[] cool = v(0.85f, 0.92f, 1.1f);
        return new Preset(p.name + "+indoors", mul(scale(p.sky, keep), cool, k), mul(scale(p.ground, keep), cool, k), mul(scale(p.sun, keep * 0.8f), cool, k), mul(scale(p.shade, keep), cool, k),
                p.exposure * (1 + 0.08f * k), p.saturation * (1 + 0.04f * k), p.contrast * (1 + 0.06f * k), lerp(p.tint, v(p.tint[0] * 1.05f, p.tint[1], p.tint[2] * 0.94f), k));
    }
    private static float[] scale(float[] a, float k) { return v(a[0] * k, a[1] * k, a[2] * k); }
    private static float[] mul(float[] a, float[] m, float k) { return v(a[0] * (1 + (m[0] - 1) * k), a[1] * (1 + (m[1] - 1) * k), a[2] * (1 + (m[2] - 1) * k)); }

    /** The preset in force (eased toward each frame's target, so a door or a cloud never snaps the light), and the indoor share. */
    private Preset current;
    private float indoors;
    public Preset current() { return current; }

    /**
     * Sends this frame's preset: the hour's, weathered, under a roof as much as the camera is; the sun's side is the side
     * of the sky the sun (or the moon) is on. dt eases it.
     */
    public Preset apply(RenderCommands cmd, double hour, float sunX, Weather w, float[] fog, boolean roofed, float dt) {
        indoors += ((roofed ? 1 : 0) - indoors) * Math.min(1, dt * 2.5f);
        Preset want = interior(weather(forHour(hour), w, fog), indoors);
        current = current == null ? want : current.mix(want, Math.min(1, dt * 1.5f));
        Preset p = current;
        boolean east = sunX >= 0;
        float[] px = east ? p.sun : p.shade, nx = east ? p.shade : p.sun, side = lerp(p.sun, p.shade, 0.55f);
        float[] cube = {px[0], px[1], px[2], nx[0], nx[1], nx[2], p.sky[0], p.sky[1], p.sky[2], p.ground[0], p.ground[1], p.ground[2], side[0], side[1], side[2], side[0], side[1], side[2]};
        cmd.atmos(cube, p.exposure, p.saturation, p.contrast, p.tint[0], p.tint[1], p.tint[2]);
        return p;
    }
}
