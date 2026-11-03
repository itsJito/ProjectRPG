package rpg.actor;

import rpg.combat.Spells;

/** The player: an actor with a camera, a selected spell and its own death state. */
public final class Player extends Actor {
    public Spells.Spell spell = Spells.Spell.WIND_STRIKE;
    /** The one spellbook open to the player (GDD 2.5), changed at an altar. */
    public Spells.Book book = Spells.Book.SUN;
    public boolean firstPerson;
    public boolean morphCameraSaved, morphFirstPerson;
    public float camDist = 3.6f;
    public double diedAt = -1;
    public boolean sprinting;
    /** The run key is held (a run; with a sprint armed, a sprint). */
    public boolean running;
    public float walked;
    /** Where the posed figure's eyes were last drawn (the first-person camera rides them). */
    public float headX, headY, headZ, headYaw;
    public boolean headValid;
    /**
     * (0.3.0, gamefixes: third-person arrows, bolts and spells did not go where the crosshair was) the point the camera's
     * centre ray meets - the world or a body, whichever is first - and when it was found. Shots from the hand are solved
     * to pass through it, so the shoulder camera's offset no longer throws them wide of what the crosshair is on.
     */
    public final float[] aimPoint = new float[3];
    public double aimAt = -99;
    /** The fresh crosshair point if it lies along this aim (within about a degree of it, as seen from this eye), else null. */
    public float[] aimPointFor(double now, float[] eye, float yaw, float pitch) {
        if (now - aimAt > 0.12) return null;
        float dx = aimPoint[0] - eye[0], dy = aimPoint[1] - eye[1], dz = aimPoint[2] - eye[2];
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 0.5f) return null;
        float cp = (float) Math.cos(pitch);
        float ux = (float) -Math.sin(yaw) * cp, uy = (float) Math.sin(pitch), uz = (float) -Math.cos(yaw) * cp;
        return (dx * ux + dy * uy + dz * uz) / l > 0.9997f ? aimPoint : null;
    }

    public Player(long id, String name) { super(id, name, 28); team = 1; }

    /**
     * One of the radial wheel's eight quick slots (gamefixes): the item it holds (by kind, as the action bar binds), and
     * the stack count last seen of it - the wheel draws the item's icon from it and uses it by its first action.
     */
    public record QuickSlot(rpg.item.ItemDef item, int count) {}
    /** The radial wheel (TAB held): eight slots, nullable, clockwise from the top. Saved with the player. */
    public final QuickSlot[] quick = new QuickSlot[8];

    /** Brings each slot's count up to date; a slot whose item is gone (eaten, dropped, sold, stolen) is cleared. */
    public void refreshQuick() {
        for (int i = 0; i < quick.length; i++) {
            QuickSlot q = quick[i];
            if (q == null) continue;
            int n = quickCount(q.item());
            quick[i] = n <= 0 ? null : n == q.count() ? q : new QuickSlot(q.item(), n);
        }
    }

    /** Quick slots follow an owned item between the pack and the equipment. */
    public int quickCount(rpg.item.ItemDef item) {
        int total = count(item);
        for (var worn : eq.stacks()) if (worn.def == item) total += worn.amount;
        return total;
    }

}
