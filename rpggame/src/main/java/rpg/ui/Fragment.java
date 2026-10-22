package rpg.ui;

import java.util.HashSet;
import java.util.Set;

/**
 * One piece of the interface (gamefixes, UI): a HUD fragment that fades in only while the game state it belongs to is
 * active (combat, being wanted, a menu) and fades out after. A fragment knows its size and how to draw itself at a
 * place; where it goes is decided by the fragment group that holds it in the current scene (or by an addon's override),
 * so the same fragment (the equipment doll, the inventory grids) is reused across scenes.
 *
 * <p>A fragment is wanted when {@link #active()} says its state is live, or for {@link #linger} seconds after one of the
 * UI events it listens for ({@link #wakeOn}). Addons may force it shown or hidden.
 */
public abstract class Fragment {
    public enum Anchor { TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT, SCREEN }

    /** Where a fragment sits: a screen anchor and an offset in pixels (toward the screen's middle is positive). */
    public record Placement(Anchor anchor, float x, float y) {
        public static Placement at(Anchor a, float x, float y) { return new Placement(a, x, y); }
    }

    public final String id;
    protected Hud hud;
    /** The current fade, 0..1, and how long fading in and out take. */
    public float alpha;
    public float fadeIn = 0.14f, fadeOut = 0.45f;
    /** How long it stays up after an event it listens for. */
    public float linger = 4;
    public final Set<String> wakeOn = new HashSet<>();
    private double wokenUntil = -1;
    /** Addons: true always shown, false never; null leaves it to the game state. */
    public Boolean forced;
    /** Addons: a placement that wins over every group's. */
    public Placement moved;
    /** Where it was last drawn (a fragment fading out of a scene keeps its place). */
    Placement last;
    /** Whether it takes the mouse (false while it fades out). */
    protected boolean live;

    protected Fragment(String id) { this.id = id; }

    public Fragment wake(String... events) { wakeOn.addAll(java.util.List.of(events)); return this; }
    public Fragment linger(float s) { linger = s; return this; }

    void bind(Hud h) { hud = h; }
    void woken(double now) { wokenUntil = Math.max(wokenUntil, now + linger); }
    boolean wanted(double now) { return forced != null ? forced : active() || now < wokenUntil; }

    /** Whether the game state this fragment shows is live now (combat, wanted, a window open...). */
    protected boolean active() { return true; }
    public abstract float width();
    public abstract float height();
    /** Draws at a top-left corner, in the fragment's current fade (already applied to the 2D layer). */
    public abstract void draw(Ui2D ui, float x, float y);

    /** The top-left corner a placement puts this fragment at on a w x h screen. */
    float[] corner(Placement p, int w, int h) {
        float fw = width(), fh = height(), x, y;
        switch (p.anchor()) {
            case TOP_LEFT -> { x = p.x(); y = p.y(); }
            case TOP -> { x = (w - fw) / 2 + p.x(); y = p.y(); }
            case TOP_RIGHT -> { x = w - fw - p.x(); y = p.y(); }
            case LEFT -> { x = p.x(); y = (h - fh) / 2 + p.y(); }
            case CENTER -> { x = (w - fw) / 2 + p.x(); y = (h - fh) / 2 + p.y(); }
            case RIGHT -> { x = w - fw - p.x(); y = (h - fh) / 2 + p.y(); }
            case BOTTOM_LEFT -> { x = p.x(); y = h - fh - p.y(); }
            case BOTTOM -> { x = (w - fw) / 2 + p.x(); y = h - fh - p.y(); }
            case BOTTOM_RIGHT -> { x = w - fw - p.x(); y = h - fh - p.y(); }
            default -> { x = 0; y = 0; }
        }
        return new float[]{x, y};
    }

    /** A fragment from a lambda, for the small ones. */
    public static Fragment of(String id, float w, float h, java.util.function.BooleanSupplier active, Drawer d) {
        return new Fragment(id) {
            @Override protected boolean active() { return active.getAsBoolean(); }
            @Override public float width() { return w; }
            @Override public float height() { return h; }
            @Override public void draw(Ui2D ui, float x, float y) { d.draw(ui, x, y); }
        };
    }

    public interface Drawer { void draw(Ui2D ui, float x, float y); }
}
