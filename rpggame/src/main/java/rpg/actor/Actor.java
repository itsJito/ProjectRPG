package rpg.actor;

import java.util.HashSet;
import java.util.Set;
import rpg.item.Equipment;
import rpg.item.ItemDef;
import rpg.item.Inventory;
import rpg.item.ItemStack;
import rpg.skill.Skill;
import rpg.skill.Skills;

/**
 * Anyone who can act and be hurt: the player and every NPC share this record (GDD 3.3: tiered parity - NPCs train the
 * same skills on the same table, carry real inventories and equipment, and fight with the same combat rules). The
 * engine owns the body (a mover); this is the rules' side of it.
 */
public class Actor {
    public final long id;
    public String name;
    public final Skills skills = new Skills();
    public final Inventory inv;
    public final Equipment eq = new Equipment();
    public Appearance look = new Appearance();
    /** The engine mover this actor walks (0 when it has none - a dummy). */
    public long body;
    public float x, y, z, yaw, pitch, vx, vz;
    /** Where the eyes look (the head turns toward it within its reach; the body follows at its own pace). */
    public float aimYaw;
    public boolean grounded = true;
    public float hp, stamina, faith, mana;
    /** Blood on hands, clothes and blade (0 clean .. 1 soaked): from blows struck close; washed off in water (Gore). */
    public float bloodied;
    /** Blood on the body itself, splash by splash where it landed (painted onto the figure). */
    public final Blood blood = new Blood();
    /** When this body last cried out (hurt cries are not repeated faster than a breath). */
    public double lastCry = -99;
    public boolean alive = true;
    /** The body's fifteen joint points (Sydney's, world) as last drawn, and when: what a physical reaction starts from. */
    public float[] posePts;
    public double poseAt = -1;
    /** The figure's animation memory is to be started afresh (the body was handed back from Sydney somewhere else). */
    public boolean poseReset;
    /** Creeping: low and slow, harder to see and to hear (stealth). */
    public boolean sneaking;
    /** Dummies take hits and never die. */
    public boolean dummy;
    public int team;
    public final Combat cs = new Combat();
    /** The hitbox (a capsule): set from the body plan by {@link #fitBody}. */
    public float radius = Body.MALE.radius, height = Body.MALE.height;

    /** Every person carries a base grid of 10 x 4 (gamefixes); worn containers add grids of their own (see {@link #grids}). */
    public Actor(long id, String name, int invSize) { this.id = id; this.name = name; this.inv = new Inventory(10, 4); }

    /** Every grid this actor carries: the base grid, then each worn container's (the backpack's, the pouch's). */
    public java.util.List<Inventory> grids() {
        java.util.List<Inventory> l = new java.util.ArrayList<>();
        l.add(inv);
        for (ItemDef.Slot s : new ItemDef.Slot[]{ItemDef.Slot.BACK, ItemDef.Slot.BELT}) { var st = eq.get(s); if (st != null && st.inner != null) l.add(st.inner); }
        return l;
    }

    // ---- what is carried, across every grid (the base grid first, then the worn bags)
    /** Puts a stack into the first grid it fits (a stack of its kind anywhere first); all or nothing. */
    public boolean store(ItemStack s) {
        if (s == null) return false;
        // a stackable joins a stack of its kind where one has room; otherwise it goes wherever there is space
        if (s.def.stackable) for (Inventory g : grids()) for (ItemStack t : g.stacks()) if (t.sameKind(s) && g.canAdd(s)) return g.add(s);
        for (Inventory g : grids()) if (g.add(s)) return true;
        return false;
    }
    public boolean canStore(ItemStack s) {
        if (s == null) return false;
        for (Inventory g : grids()) if (g.canAdd(s)) return true;
        return false;
    }
    /** Whether several stacks fit together in what is carried. */
    public boolean canStoreAll(java.util.List<ItemStack> list) {
        java.util.List<Inventory> probes = new java.util.ArrayList<>();
        for (Inventory g : grids()) probes.add(g.copy());
        outer:
        for (ItemStack s : list) { for (Inventory p : probes) if (p.add(s)) continue outer; return false; }
        return true;
    }
    public int count(ItemDef d) { int n = 0; for (Inventory g : grids()) n += g.count(d); return n; }
    public boolean has(ItemDef d, int n) { return count(d) >= n; }
    /** Takes n of a definition from what is carried (the bags last), or nothing at all when there are fewer. */
    public java.util.List<ItemStack> takeItems(ItemDef d, int n) {
        if (count(d) < n) return null;
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        int left = n;
        for (Inventory g : grids()) {
            int here = Math.min(left, g.count(d));
            if (here <= 0) continue;
            java.util.List<ItemStack> got = g.remove(d, here);
            if (got != null) out.addAll(got);
            left -= here;
            if (left == 0) break;
        }
        return out;
    }

    /** What the actor carries and wears, in kg: bulk and weight both matter. */
    public float carried() { float kg = 0; for (Inventory g : grids()) kg += g.weight(); for (var st : eq.stacks()) kg += st.inner == null ? st.weight() : st.def.weight; return kg; }

    /** How much the load slows and tires the body (1 = not at all): free to 30 kg, then down to 0.55 at 60 kg and beyond. */
    public float burden() { float kg = carried(); return kg <= 30 ? 1f : Math.max(0.55f, 1f - (kg - 30) / 30f * 0.45f); }

    /** The body plan this actor's look gives it (part sizes, joints, hitbox). */
    public Body plan() { return Body.of(look); }
    /** Sizes the hitbox to the body plan; call after the look is set and before the engine body is made. */
    public void fitBody() { Body b = plan(); radius = b.radius; height = b.height; }

    /** HP is the Health level itself (gamefixes): 10 HP at Health 10, 100 at 100. */
    public float maxHp() { return dummy ? 1e9f : Math.max(1, skills.level(Skill.HEALTH)); }
    public float maxStamina() { return 100 + 0.5f * skills.level(Skill.AGILITY); }
    /** The Faith-point maximum rises with Faith level (GDD 2.3). */
    /** The Faith pool is the Faith level (GDD 2.5): 1 point at Faith 1, 100 at 100. It does not refill by itself. */
    public float maxFaith() { return Math.max(1, skills.level(Skill.FAITH)); }
    /** The Mana pool is the Magic level (GDD 2.5): it regains a point a minute. */
    public float maxMana() { return Math.max(1, skills.level(Skill.MAGIC)); }
    /** Time toward the next point of Mana (seconds of play), kept across saves so no partial minute is lost or doubled. */
    public float manaAcc;
    /** The spells on this body: mist (Gaseous Form), surer hands (Enhance Ability), a stranger's face (Disguise Self), seconds left. */
    public float gaseousT, enhanceT, disguiseT;
    public void restore() { hp = maxHp(); stamina = maxStamina(); faith = maxFaith(); mana = maxMana(); }

    public ItemDef main() { return eq.def(ItemDef.Slot.MAIN); }
    public ItemDef off() { return eq.def(ItemDef.Slot.OFF); }
    public float eyeY() { return y + plan().eyeY() * Body.PX; }
    public float chestY() { return y + (Body.LEGS + Body.TORSO * 0.6f) * Body.PX; }

    public float distTo(float px, float pz) { return (float) Math.hypot(px - x, pz - z); }

    /** Turns the body toward a heading at no more than a rate (rad/s): a body has weight, it does not snap round. */
    public void turnToward(float target, float rate, float dt) {
        float d = wrap(target - yaw), step = rate * dt;
        yaw = wrap(yaw + (Math.abs(d) <= step ? d : Math.signum(d) * step));
    }

    public static float wrap(float a) { while (a > Math.PI) a -= (float) (2 * Math.PI); while (a < -Math.PI) a += (float) (2 * Math.PI); return a; }
    public float distTo(Actor o) { return (float) Math.hypot(o.x - x, o.z - z); }

    /** Combat state: the move in progress, guard, stagger, dodge, blessings. */
    public static final class Combat {
        public enum Phase { IDLE, WINDUP, ACTIVE, RECOVER }
        public enum Move { LIGHT_R, LIGHT_L, HEAVY, THRUST, PUNCH, BASH, BITE }

        public Phase phase = Phase.IDLE;
        public Move move;
        public boolean offHand;
        /** Time in the current phase, and each phase's length. */
        public float t, windup, active, recover;
        /** The sweep's last sampled position (0..1 through the active phase). */
        public float sweep;
        public final Set<Long> struck = new HashSet<>();
        public boolean blocking;
        /** This swing's tool has already bitten a tree or a rock (one strike a swing). */
        public boolean gathered;
        public double blockStart = -9;
        public float staggered, stabilityBonus, iframes, dodgeT, dodgeX, dodgeZ, hitstop;
        public float charge, draw, castCooldown, regenDelay, flash, slowT;
        /** (The old cast-once blessings' timers are gone: prayers are toggled; see combat.Prayers.) */
        /** A crossbow being loaded again (seconds left). */
        public float reload;
        public int combo;
        public double lastHurt = -99, lastAttack = -99;
        public long lastAttacker;

        public boolean busy() { return phase != Phase.IDLE || staggered > 0 || dodgeT > 0; }
        public void cancel() { phase = Phase.IDLE; move = null; t = 0; struck.clear(); charge = 0; draw = 0; gathered = false; }
        /** 0..1 through the whole move (for animation). */
        public float progress() {
            return switch (phase) {
                case IDLE -> 0;
                case WINDUP -> 0.35f * t / Math.max(1e-3f, windup);
                case ACTIVE -> 0.35f + 0.3f * t / Math.max(1e-3f, active);
                case RECOVER -> 0.65f + 0.35f * t / Math.max(1e-3f, recover);
            };
        }
    }
}
