package rpg.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import rpg.Chat;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Actor.Combat.Move;
import rpg.actor.Actor.Combat.Phase;
import rpg.combat.Spells.Prayer;
import rpg.combat.Spells.Spell;
import rpg.item.ItemDef;
import rpg.item.ItemDef.DamageType;
import rpg.item.ItemDef.Family;
import rpg.item.ItemStack;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.Voxels;

/**
 * Direct physical combat (GDD 2.3), the same rules for the player and every NPC.
 *
 * A melee move has a wind-up, an active window and a recovery. During the active window the blade - a segment from the
 * hand to the tip, swept along the move's arc in the world - is sampled several times a frame; only where it actually
 * passes through another body's capsule is there contact, and a wall in the way deflects it. Arrows and spells are
 * projectiles flying through the voxel world with the same contact test. Contact is never rolled: on contact, HP damage
 * rolls uniformly up to the armor-adjusted maximum; impact resolves separately against Stability. Guards,
 * parries and dodges are timing and position, not chance. Combat XP is paid from the damage actually dealt.
 */
public final class CombatSystem {
    /** Distance along a normalized ray to the first of the body's (articulated) hit volumes, or infinity: what every blow tests. */
    public static float rayHit(Rpg g, Actor v, float ox, float oy, float oz, float dx, float dy, float dz, float limit, float pad) { return HitVolumes.ray(g, v, ox, oy, oz, dx, dy, dz, limit, pad); }

    private final Rpg g;
    public final List<Projectile> projectiles = new ArrayList<>();
    private final List<Actor> projectileTargets = new ArrayList<>();
    /** Combat telemetry (GDD 4.2): the last contacts with every number that went into them. */
    public final ArrayDeque<String> telemetry = new ArrayDeque<>();
    public long contacts, blocked, parried, dodged, staggers, kills, deflections, shots, casts, blessings;
    /** Confirmed player contacts only; HUD feedback never predicts a hit. */
    public double lastPlayerHit = -99, lastPlayerKill = -99;

    private final java.util.Random damageRandom;
    public CombatSystem(Rpg g) { this.g = g; damageRandom = new java.util.Random(g.scripted ? 3 : System.nanoTime()); }

    private int physicalMax(Actor a, Skill skill, float bonus) {
        float prayer = skill == Skill.STRENGTH ? g.prayers.strength(a) : g.prayers.archery(a);
        return CombatMath.maxHit(CombatMath.effectiveLevel(a.skills.level(skill), prayer), bonus);
    }


    private long projectileSerial;
    public static final class Projectile {
        public long id, rigid;
        public Actor owner;
        public float x, y, z, vx, vy, vz, gravity, life, potential, impact;
        /** A creature's own magic (weaker effects than the learned spell's). */
        public boolean innate;
        public DamageType type;
        public Skill skill;
        public ItemStack ammo;
        public Spell spell;
        public boolean done, heardPass;
        /** A thrown thing's tumble (rad/s, set at the release) and how far it has turned; how many times it has bounced. */
        public float spin, angle;
        public int bounces;
        public long target;
        /** What a monster hurled that is no item: "boulder" or "web" (null for everything else). */
        public String what;
        /** Those a Wave has already swept through (it strikes each body in its path once and rolls on). */
        public java.util.Set<Long> swept;
    }

    /**
     * An arrow, bolt or thrown blade lodged in a body: the item itself, where it went in and which way it points, in the
     * body's own frame (x to its right, y up from its feet, z to its front), and when (gamefixes: arrows are real objects).
     */
    public record Stuck(ItemStack stack, float lx, float ly, float lz, float dx, float dy, float dz, double at, int anchorA, int anchorB) {
        /** The tip's point and the shaft's direction in the world, as the body stands now. */
        public float[] world(Rpg g, Actor a) {
            float[] f = HitVolumes.attachmentFrame(g, a, anchorA, anchorB);
            return new float[]{f[0]+f[3]*lx+f[6]*ly+f[9]*lz, f[1]+f[4]*lx+f[7]*ly+f[10]*lz, f[2]+f[5]*lx+f[8]*ly+f[11]*lz,
                    f[3]*dx+f[6]*dy+f[9]*dz, f[4]*dx+f[7]*dy+f[10]*dz, f[5]*dx+f[8]*dy+f[11]*dz};
        }
    }

    /** How long a piece of ammunition is, tail to point (m). */
    public static float ammoLength(ItemDef d) {
        if (d.family == Family.BOLT) return 0.45f;
        if (d.family == Family.THROWN) return switch (d.thrown) { case "javelin" -> 1.8f; case "tomahawk" -> 0.42f; case "knife" -> 0.3f; default -> 0.22f; };
        return 0.85f;
    }

    /** How long a thing lies when it lies along a direction: ammunition its own length, a weapon about its reach. */
    public static float lyingLength(ItemDef d) {
        if (d.family == Family.ARROW || d.family == Family.BOLT || d.family == Family.THROWN) return ammoLength(d);
        return Math.max(0.35f, Math.min(1.9f, d.reach * 0.8f));
    }

    /** The turn (w, x, y, z) that takes +y onto a unit direction. */
    public static float[] alongY(float[] d) {
        float w = 1 + d[1];
        if (w < 1e-4f) return new float[]{0, 1, 0, 0};
        float x = d[2], z = -d[0], l = (float) Math.sqrt(w * w + x * x + z * z);
        return new float[]{w / l, x / l, 0, z / l};
    }

    /** What lodges where it strikes: pointed things (arrows, bolts, darts, knives, javelins) and a tomahawk's blade. */
    private static boolean lodges(ItemDef d) { return d.family == Family.ARROW && !d.id.contains("brutal") || d.family == Family.BOLT || d.family == Family.THROWN; }

    // ------------------------------------------------------------------ starting actions
    public static Move lightMove(Actor a, boolean offHand) {
        ItemDef w = offHand ? a.off() : a.main();
        if (w == null || w.family == Family.SHIELD || w.family == Family.SYMBOL) return offHand && w != null && w.family == Family.SHIELD ? Move.BASH : Move.PUNCH;
        if (w.family == Family.DAGGER || w.family == Family.SPEAR) return Move.THRUST;
        // a halberd alternates a thrust of its spike with a cut of its blade
        if (w.family == Family.HALBERD) { a.cs.combo++; return (a.cs.combo & 1) == 0 ? Move.THRUST : Move.LIGHT_R; }
        a.cs.combo++;
        return (a.cs.combo & 1) == 0 ? Move.LIGHT_L : Move.LIGHT_R;
    }

    /** Begins a melee move; false (and nothing spent) when the actor cannot. */
    public boolean attack(Actor a, boolean offHand, Move move) {
        if (a.morphT > 0 || a.reacting || g.carrying.busy(a) || g.traversal.busy(a) || g.time - a.throwAt < 0.45) return false;
        // (0.2.2) a weapon put away is drawn first: the blow waits until it is in the hand
        if (a.sheathed && a.sheathable()) { sheathe(a, false); return false; }
        if (!armsReady(a)) return false;
        a.armsUsedAt = g.time;
        if (!a.alive || a.cs.busy() || a.cs.blocking) return false;
        if (a.gaseousT > 0) { if (a == g.player) g.chat.add("You can't strike while you are mist.", Chat.Kind.WARN); return false; }
        float cost = move == Move.HEAVY ? g.cfg.staminaHeavy : g.cfg.staminaLight;
        if (a.stamina < cost * 0.5f) { if (a == g.player) g.chat.add("You're too tired to attack.", Chat.Kind.WARN); return false; }
        a.stamina = Math.max(0, a.stamina - cost);
        a.cs.regenDelay = 0.9f;
        ItemDef w = offHand ? a.off() : a.main();
        Actor.Combat c = a.cs;
        c.cancel();
        c.move = move; c.offHand = offHand; c.phase = Phase.WINDUP; c.t = 0; c.sweep = 0;
        float[] tm = timing(w, move);
        // a weapon's own heft (its speed); Attack (and the weapon's Attack bonus) quickens the wind-up and the recovery -
        // how often, never how hard (GDD 2.3); the active window, where the blow lands, is the weapon's own
        float k = w == null || move == Move.PUNCH || move == Move.BASH ? 1f : w.speed;
        float cadence = CombatMath.attackCadence(a.skills.level(Skill.ATTACK), w == null ? 0 : w.attackBonus) * (g.prayers == null ? 1 : g.prayers.tempo(a));
        c.windup = tm[0] * k * cadence * (move == Move.HEAVY ? 1 : 0.92f);
        c.active = tm[1] * k;
        c.recover = tm[2] * k * cadence * (move == Move.HEAVY ? 1 : 0.88f);
        if (a instanceof rpg.animal.Animal an) {
            // an animal's strike: a crouch and a lunge (a bear's swipe and a horse's kick are slower to come)
            float heavy = an.species.impact > 50 ? 1.35f : 1f;
            c.windup = 0.32f * heavy * cadence; c.active = 0.14f * heavy; c.recover = 0.42f * heavy * cadence;
        }
        c.lastAttack = g.time;
        g.interact.cancel(a, "attacking");
        g.ai.arms.shown(a, rpg.ai.Arms.Kind.SWING);
        return true;
    }

    /**
     * The shape of a blow in time, by what it is struck with (gamefixes: attacks were slow and all alike): wind-up,
     * active window and recovery, in seconds. A fist and a dagger are quick jabs; a one-handed sword is a snappy cut; a
     * mace and a pickaxe have a heavier head to bring round; the two-handers - great swords and battle axes - are slow,
     * wide and committed, with a long recovery. A heavy (held) blow takes longer to bring than a light one.
     */
    public static float[] timing(ItemDef w, Move move) {
        boolean heavy = move == Move.HEAVY;
        if (move == Move.PUNCH) return new float[]{0.08f, 0.07f, 0.14f};
        if (move == Move.BASH) return new float[]{0.15f, 0.1f, 0.24f};
        if (w == null) return heavy ? new float[]{0.2f, 0.1f, 0.24f} : new float[]{0.08f, 0.07f, 0.14f};
        boolean twoHand = w.twoHanded();
        return switch (w.family) {
            case DAGGER -> heavy ? new float[]{0.2f, 0.1f, 0.26f} : new float[]{0.09f, 0.08f, 0.15f};
            case SWORD -> twoHand ? (heavy ? new float[]{0.44f, 0.2f, 0.5f} : new float[]{0.24f, 0.15f, 0.32f})
                                  : (heavy ? new float[]{0.3f, 0.14f, 0.32f} : new float[]{0.13f, 0.1f, 0.2f});
            case AXE -> twoHand ? (heavy ? new float[]{0.48f, 0.2f, 0.55f} : new float[]{0.26f, 0.15f, 0.34f})
                                : (heavy ? new float[]{0.32f, 0.15f, 0.36f} : new float[]{0.16f, 0.11f, 0.24f});
            case MACE -> heavy ? new float[]{0.36f, 0.16f, 0.42f} : new float[]{0.18f, 0.12f, 0.26f};
            case PICKAXE, TOOL -> heavy ? new float[]{0.34f, 0.15f, 0.36f} : new float[]{0.2f, 0.12f, 0.26f};
            case LONGSWORD -> heavy ? new float[]{0.36f, 0.17f, 0.4f} : new float[]{0.17f, 0.12f, 0.24f};
            case SCIMITAR -> heavy ? new float[]{0.26f, 0.13f, 0.28f} : new float[]{0.11f, 0.09f, 0.17f};
            case WARHAMMER -> heavy ? new float[]{0.46f, 0.18f, 0.5f} : new float[]{0.24f, 0.14f, 0.32f};
            case SPEAR -> heavy ? new float[]{0.34f, 0.16f, 0.36f} : new float[]{0.18f, 0.12f, 0.24f};
            case CLAWS -> heavy ? new float[]{0.2f, 0.1f, 0.24f} : new float[]{0.08f, 0.07f, 0.13f};
            case HALBERD -> heavy ? new float[]{0.52f, 0.2f, 0.56f} : new float[]{0.28f, 0.16f, 0.36f};
            case STAFF -> heavy ? new float[]{0.32f, 0.16f, 0.34f} : new float[]{0.17f, 0.12f, 0.22f};
            default -> heavy ? new float[]{0.3f, 0.14f, 0.32f} : new float[]{0.14f, 0.1f, 0.2f};
        };
    }

    /**
     * (0.2.2, gamefixes) Puts what the hands hold away on the body, or draws it. Not mid-blow, mid-draw or while
     * reeling; the hand goes to the place and back (Rig), the item changing places halfway. Drawing is seen (Arms).
     */
    public boolean sheathe(Actor a, boolean put) {
        if (a.sheathed == put || !a.alive || a.reacting || a.morphT > 0 || g.carrying.busy(a) || g.traversal.busy(a)) return false;
        if (!a.sheathable()) { a.sheathed = put; return false; }
        if (a.cs.phase != Phase.IDLE || a.cs.staggered > 0 || g.time - a.sheatheAt < Actor.SHEATHE_TIME) return false;
        a.cs.blocking = false; a.cs.draw = 0; a.cs.charge = 0; a.cs.aim = 0;
        a.sheathed = put; a.sheatheAt = g.time;
        if (!put) a.armsUsedAt = g.time;
        if (g.sfx != null) g.sfx.play(put ? "sheathe" : "unsheathe", a.x, a.y + 1.0f, a.z, 0.45f);
        g.state.commit(put ? "Sheathed" : "Unsheathed", a.id, 0, rpg.world.DevMap.jurisdiction(a.x, a.z), 0, String.valueOf(a.main() != null ? a.main().name : a.off().name));
        if (!put) g.ai.arms.shown(a, rpg.ai.Arms.Kind.DRAW); else g.ai.arms.sheathed(a);
        return true;
    }

    /** Whether the hands are ready to fight with what they hold: drawn, and not in the middle of drawing it. */
    public boolean armsReady(Actor a) { return !(a.sheathed && a.sheathable()) && g.time - a.sheatheAt >= Actor.SHEATHE_TIME; }

    /** Whether the weapon or shield is out, in hand, where people can see it (an empty hand is no weapon). */
    public boolean drawn(Actor a) {
        if (a.sheathed || !a.alive) return false;
        ItemDef m = a.main(), o = a.off();
        return m != null && (m.damage > 0 || m.family == Family.BOW || m.family == Family.CROSSBOW || m.family == Family.STAFF) && m.toolSkill == null || o != null && (o.family == Family.SHIELD || o.damage > 0);
    }

    public void setBlock(Actor a, boolean on) {
        boolean can = on && a.alive && armsReady(a) && a.morphT<=0 && !a.reacting && !g.carrying.busy(a) && !g.traversal.busy(a) && a.cs.phase == Phase.IDLE && a.cs.staggered <= 0 && a.cs.dodgeT <= 0;
        if (can && !a.cs.blocking) a.cs.blockStart = g.time;
        a.cs.blocking = can;
    }

    /** A dodge: a quick burst of movement with a moment of invulnerability. */
    public boolean dodge(Actor a, float dx, float dz) {
        if (g.carrying.busy(a) || g.traversal.busy(a)) return false;
        if (!a.alive || a.morphT>0 || a.reacting || a.cs.staggered > 0 || a.cs.dodgeT > 0 || a.cs.phase == Phase.ACTIVE) return false;
        if (a.stamina < g.cfg.staminaDodge) { if (a == g.player) g.chat.add("You're too tired to dodge.", Chat.Kind.WARN); return false; }
        float l = (float) Math.hypot(dx, dz);
        if (l < 1e-3f) { dx = (float) Math.sin(a.yaw); dz = (float) Math.cos(a.yaw); l = 1; }
        a.stamina -= g.cfg.staminaDodge;
        a.cs.regenDelay = 0.8f;
        a.cs.cancel();
        a.cs.blocking = false;
        a.cs.dodgeT = g.cfg.dodgeTime; a.cs.iframes = g.cfg.dodgeTime + 0.05f; a.cs.dodgeX = dx / l; a.cs.dodgeZ = dz / l;
        g.state.commit("Dodge", a.id, 0, null, 0, "");
        return true;
    }

    /**
     * Throws one of the stack in the hand (a dart, a knife, a tomahawk, a javelin) along an aim, wound up this far (0..1).
     * It flies as a projectile under gravity, strikes by the same contact rules, and lands where it stops, to be picked up.
     */
    /**
     * The throw an actor is aiming now (a carried body charged to throw, or a throwing weapon wound up): the plan is
     * solved from the hand to where the eye looks (only re-solved when the aim moved). False when nothing is aimed.
     */
    public static boolean aimThrow(Rpg g, Actor a, float[] eye) {
        float speed, gravity;
        if (g.carrying.ready(a) && a.cs.charge > 0) { speed = rpg.actor.BodyCarry.launchSpeed(a); gravity = ThrowSolver.gravityFor(null); }
        else if (a.main() != null && a.main().family == Family.THROWN && a.cs.draw > 0) {
            boolean heavy = a.main().thrown.equals("javelin") || a.main().thrown.equals("tomahawk");
            speed = (heavy ? 17 : 22) + (heavy ? 12 : 14) * Math.max(0.4f, a.cs.draw); gravity = ThrowSolver.gravityFor(a.main().thrown);
        }
        // a bow being drawn, a crossbow raised to the eye (gamefixes: they show the arc too, with the reticle)
        else if (a.main() != null && a.main().family == Family.BOW && a.cs.draw > 0 && a.eq.get(ItemDef.Slot.AMMO) != null) { speed = bowSpeed(a.cs.draw); gravity = ARROW_GRAVITY; }
        else if (a.main() != null && a.main().family == Family.CROSSBOW && a.cs.aim > 0.5f && a.cs.reload <= 0 && a.eq.get(ItemDef.Slot.AMMO) != null) { speed = BOLT_SPEED; gravity = BOLT_GRAVITY; }
        else return false;
        float[] hand = g.time - a.handAt < 0.25 ? a.hand : new float[]{a.x - (float) Math.sin(a.aimYaw) * 0.35f, a.eyeY() - 0.25f, a.z - (float) Math.cos(a.aimYaw) * 0.35f};
        a.throwPlan.solve(g, a, hand, eye, speed, gravity, false);
        return true;
    }

    /** How long an arrow or a blade stays in a living body before it works loose and falls (gamefixes: a minute). */
    static final double LODGED_FOR = 60;
    static final float ARROW_GRAVITY = 9.8f, BOLT_SPEED = 58, BOLT_GRAVITY = 6f;
    static float bowSpeed(float draw) { return 18 + 34 * Math.max(0.35f, Math.min(1, draw)); }

    /**
     * Sends an arrow or a bolt from the drawing hand. The player's flies the arc the guide drew - from the hand to where the
     * reticle is (ThrowSolver) - so what is shown is what is flown; anyone else's leaves the hand along their own aim.
     */
    private void fromHand(Projectile p, Actor a, float speed, float gravity, float yaw, float pitch) {
        if (a == g.player) {
            ThrowSolver plan = a.throwPlan;
            float[] eye = g.time - plan.solvedAt < 0.3 ? plan.eye.clone() : new float[]{a.x, a.eyeY(), a.z};
            float[] hand = g.time - a.handAt < 0.25 ? a.hand : new float[]{a.x - (float) Math.sin(a.aimYaw) * 0.35f, a.eyeY() - 0.25f, a.z - (float) Math.cos(a.aimYaw) * 0.35f};
            float previousYaw=a.aimYaw,previousPitch=a.pitch;
            a.aimYaw=yaw;a.pitch=pitch;
            try { plan.solve(g, a, hand, eye, speed, gravity, true); }
            finally { a.aimYaw=previousYaw;a.pitch=previousPitch; }
            p.x = plan.start[0]; p.y = plan.start[1]; p.z = plan.start[2];
            p.vx = plan.velocity[0]; p.vy = plan.velocity[1]; p.vz = plan.velocity[2];
        } else if (g.time - a.handAt < 0.25) { p.x = a.hand[0]; p.y = a.hand[1]; p.z = a.hand[2]; }
    }

    /** The plan a release flies: the guide's own if it is current, else solved now from the actor's eye. */
    private ThrowSolver release(Actor a) {
        ThrowSolver plan = a.throwPlan;
        float[] eye = g.time - plan.solvedAt < 0.3 ? plan.eye.clone() : new float[]{a.x, a.eyeY(), a.z};
        aimThrow(g, a, eye);
        return plan;
    }

    public boolean throwOne(Actor a, float yaw, float pitch, float wind) {
        if (a.morphT > 0 || a.reacting || g.carrying.busy(a) || g.traversal.busy(a) || g.time - a.throwAt < 0.45) return false;
        ItemStack held = a.eq.get(ItemDef.Slot.MAIN);
        if (held == null || held.def.family != Family.THROWN) return false;
        if (a.skills.level(Skill.ARCHERY) < held.def.reqLevel) { if (a == g.player) g.chat.add("You need Archery level " + held.def.reqLevel + " to throw those.", Chat.Kind.WARN); return false; }
        if (a.cs.castCooldown > 0) return false;
        wind = Math.max(0.4f, Math.min(1, wind));
        a.cs.draw = wind;
        ThrowSolver plan = release(a);
        ItemStack one = held.copy(1);
        held.amount--;
        if (held.amount <= 0) a.eq.set(ItemDef.Slot.MAIN, null);
        boolean heavy = held.def.thrown.equals("javelin") || held.def.thrown.equals("tomahawk");
        Projectile p = launch(a, yaw, pitch, (heavy ? 17 : 22) + (heavy ? 12 : 14) * wind, plan.gravity);
        // the guide's path, exactly: from the hand, at the solved velocity, tumbling; the thrower's feet held a moment
        p.x = plan.start[0]; p.y = plan.start[1]; p.z = plan.start[2];
        p.vx = plan.velocity[0]; p.vy = plan.velocity[1]; p.vz = plan.velocity[2];
        p.spin = (float) (g.roll(p.id, a.id) * 2 - 1) * 12;
        a.throwFreezeUntil = g.time + 0.1;
        p.life=8;
        p.ammo = one; p.type = held.def.damageType; p.skill = Skill.ARCHERY;
        p.potential = (float) Math.floor(physicalMax(a, Skill.ARCHERY, held.def.damage / CombatMath.HP_SCALE) * (0.6f + 0.4f * wind));
        p.impact = held.def.impact * wind;
        a.cs.castCooldown = 0.32f * held.def.speed;
        shots++;
        if (g.sfx != null) g.sfx.play("throw", a.x, a.eyeY(), a.z, 0.6f);
        g.state.commit("Thrown", a.id, 0, null, 0, String.format("%s potential=%.1f", held.def.name, p.potential));
        return true;
    }

    /** Looses a bolt from a loaded crossbow along an aim; the crossbow is loaded again over a second or so (Agility quickens it). */
    public boolean shootCrossbow(Actor a, float yaw, float pitch) {
        if (a.morphT > 0 || a.reacting || g.carrying.busy(a) || g.traversal.busy(a) || g.time - a.throwAt < 0.45) return false;
        ItemDef xb = a.main();
        if (xb == null || xb.family != Family.CROSSBOW) return false;
        if (a.sheathed) { sheathe(a, false); return false; }
        if (!armsReady(a)) return false;
        if (a.cs.reload > 0) return false;
        ItemStack ammo = a.eq.get(ItemDef.Slot.AMMO);
        if (ammo == null || ammo.def.family != Family.BOLT) { if (a == g.player) g.chat.add("You have no bolts equipped.", Chat.Kind.WARN); return false; }
        if (a.skills.level(Skill.ARCHERY) < xb.reqLevel) { if (a == g.player) g.chat.add("You need Archery level " + xb.reqLevel + " to use that crossbow.", Chat.Kind.WARN); return false; }
        ItemStack one = ammo.copy(1);
        ammo.amount--;
        if (ammo.amount <= 0) a.eq.set(ItemDef.Slot.AMMO, null);
        Projectile p = launch(a, yaw, pitch, BOLT_SPEED, BOLT_GRAVITY);
        fromHand(p, a, BOLT_SPEED, BOLT_GRAVITY,yaw,pitch);
        p.ammo = one; p.type = DamageType.PIERCE; p.skill = Skill.ARCHERY;
        p.potential = physicalMax(a, Skill.ARCHERY, (xb.damage + ammo.def.damage) / CombatMath.HP_SCALE);
        p.impact = xb.impact;
        a.cs.reload = 1.25f * (1 - Math.min(0.3f, a.skills.level(Skill.AGILITY) * 0.003f));
        shots++;
        if (g.sfx != null) g.sfx.play("crossbow", a.x, a.eyeY(), a.z, 0.8f, 1, 30);
        g.state.commit("BoltLoosed", a.id, 0, null, 0, String.format("%s potential=%.1f", ammo.def.name, p.potential));
        g.ai.arms.shown(a, rpg.ai.Arms.Kind.SHOT);
        return true;
    }

    /** Looses an arrow from an equipped bow along an aim, drawn this far (0..1). */
    public boolean shoot(Actor a, float yaw, float pitch, float draw) {
        if (a.morphT > 0 || a.reacting || g.carrying.busy(a) || g.traversal.busy(a) || g.time - a.throwAt < 0.45) return false;
        ItemDef bow = a.main();
        if (bow == null || bow.family != Family.BOW) return false;
        if (a.sheathed) { sheathe(a, false); return false; }
        ItemStack ammo = a.eq.get(ItemDef.Slot.AMMO);
        if (ammo == null || ammo.def.family != Family.ARROW) { if (a == g.player) g.chat.add("You have no arrows equipped.", Chat.Kind.WARN); return false; }
        if (a.skills.level(Skill.ARCHERY) < ammo.def.reqLevel) { if (a == g.player) g.chat.add("You can't use " + ammo.def.name + " yet.", Chat.Kind.WARN); return false; }
        draw = Math.max(0.35f, Math.min(1, draw));
        ItemStack one = ammo.copy(1);
        ammo.amount--;
        if (ammo.amount <= 0) a.eq.set(ItemDef.Slot.AMMO, null);
        Projectile p = launch(a, yaw, pitch, bowSpeed(draw), ARROW_GRAVITY);
        fromHand(p, a, bowSpeed(draw), ARROW_GRAVITY,yaw,pitch);
        p.ammo = one; p.type = DamageType.PIERCE; p.skill = Skill.ARCHERY;
        p.potential = (float) Math.floor(physicalMax(a, Skill.ARCHERY, (bow.damage + ammo.def.damage) / CombatMath.HP_SCALE) * draw);
        p.impact = bow.impact * draw;
        shots++;
        if (g.sfx != null) g.sfx.play("bow", a.x, a.eyeY(), a.z, 0.7f);
        g.state.commit("ArrowLoosed", a.id, 0, null, 0, String.format("%s draw=%.2f potential=%.1f", ammo.def.name, draw, p.potential));
        g.ai.arms.shown(a, rpg.ai.Arms.Kind.SHOT);
        return true;
    }

    public String castRefusal(Actor a, Spell s) {
        if (a.skills.level(Skill.MAGIC) < s.level) return "You need Magic level " + s.level + " to cast " + s.title + ".";
        if (a instanceof rpg.actor.Player p && p.book != s.book) return s.title + " is in the " + s.book.title + " spellbook; yours is " + p.book.title + ". Change it at an altar.";
        if (a instanceof rpg.ai.Npc npc && npc.spellBook != s.book) return "That spellbook is not prepared.";
        if (a.mana < cost(a, s)) return "You don't have enough mana.";
        if (!a.alive || a.morphT > 0 || a.reacting || a.cs.castCooldown > 0 || a.cs.busy()) return "";
        return null;
    }

    public boolean cast(Actor a, Spell s, float yaw, float pitch) {
        if (a.morphT > 0 || a.reacting || g.carrying.busy(a) || g.traversal.busy(a) || g.time - a.throwAt < 0.45) return false;
        String no = castRefusal(a, s);
        if (no != null) { if (a == g.player && !no.isEmpty()) g.chat.add(no, Chat.Kind.WARN); return false; }
        if (s == Spell.TELEGRAB) { boolean cast=telegrab(a,s,yaw,pitch); if(cast){a.castAt=g.time;a.castSpell=s;g.ai.arms.shown(a,rpg.ai.Arms.Kind.SPELL);} return cast; }
        if (s.utility) {
            if (!g.magic.cast(a, s, yaw, pitch)) return false;
            a.castAt=g.time; a.castSpell=s;
            g.ai.arms.shown(a, rpg.ai.Arms.Kind.SPELL);
            a.mana -= cost(a, s);
            a.cs.castCooldown = 0.7f;
            casts++;
            var uev = g.state.commit("SpellCast", a.id, 0, null, 0, s.title);
            g.prog.award(a, Skill.MAGIC, s.xp, "cast " + s.title, uev.id());
            if (g.sfx != null) g.sfx.play("cast", a.x, a.eyeY(), a.z, 0.6f);
            return true;
        }
        a.mana -= cost(a, s);
        a.cs.castCooldown = 0.7f;
        Projectile p = bolt(a, s, yaw, pitch, 1);
        if (s == Spell.SPELLDAGGERS) {
            Actor target = g.magic.target(a,yaw,pitch,24); p.target = target == null ? 0 : target.id;
            for (float offset : new float[]{-0.11f,0.11f}) {
                Projectile blade=launch(a,yaw+offset,pitch,s.speed,0);
                spellFromHand(blade,a,yaw+offset,pitch); blade.spell=s; blade.type=DamageType.MAGIC; blade.skill=Skill.MAGIC; blade.potential=p.potential; blade.impact=p.impact; blade.target=p.target;
            }
        }
        casts++;
        var ev = g.state.commit("SpellCast", a.id, 0, null, 0, s.title);
        g.prog.award(a, Skill.MAGIC, s.xp, "cast " + s.title, ev.id());
        if (g.sfx != null) g.sfx.play("cast", a.x, a.eyeY(), a.z, 0.6f);
        return true;
    }

    /** What a spell costs this caster: an elemental staff in hand makes its own element's spells very cheap. */
    public int cost(Actor a, Spell s) {
        ItemStack held = a.eq.get(ItemDef.Slot.MAIN);
        return s.cost(held == null ? null : held.def.staffElement);
    }

    /**
     * A spell sent on its way from the hand: its maximum hit the tier's best that the caster has open (with equipment's
     * Magic damage on it), scaled for innate magic by how strong the creature is (share).
     */
    private Projectile bolt(Actor a, Spell s, float yaw, float pitch, float share) {
        Projectile p = launch(a, yaw, pitch, s.speed, 0);
        spellFromHand(p, a, yaw, pitch);
        a.castAt = g.time; a.castSpell = s;
        g.ai.arms.shown(a, rpg.ai.Arms.Kind.SPELL);
        p.spell = s; p.type = DamageType.MAGIC; p.skill = Skill.MAGIC;
        float magicBonus = 0; for (ItemStack item : a.eq.stacks()) magicBonus += item.def.magicDamageBonusPct;
        float base = s.maxBase(a.skills.level(Skill.MAGIC)) * share;
        p.potential = CombatMath.magicMax(base, magicBonus);
        p.impact = 12 + base * 0.6f;
        p.innate = share < 1;
        return p;
    }

    /**
     * A creature's own magic (an imp's spit of fire, a ghost's grave-cold rush): no book and no Mana, as strong as the
     * creature is - a spell's full strength only at its Magic level - and the same spell once it flies.
     */
    public boolean castInnate(Actor a, Spell s, float yaw, float pitch) {
        if (!a.alive || a.morphT > 0 || a.reacting || a.cs.castCooldown > 0 || a.cs.busy()) return false;
        a.cs.castCooldown = 0.7f;
        float share = Math.max(0.2f, Math.min(0.99f, a.skills.level(Skill.MAGIC) / (float) Math.max(1, s.level)));
        bolt(a, s, yaw, pitch, share);
        a.castAt = g.time; a.castSpell = s;
        casts++;
        g.state.commit("SpellCast", a.id, 0, null, 0, s.title + " (innate)");
        if (g.sfx != null) g.sfx.play("cast", a.x, a.eyeY(), a.z, 0.6f);
        return true;
    }

    private float[] castOrigin(Actor a,float yaw,float pitch) {
        if(g.time-a.handAt<0.25)return a.hand.clone();
        float cp=(float)Math.cos(pitch);
        return new float[]{a.x+(float)Math.cos(yaw)*a.radius-(float)Math.sin(yaw)*cp*0.4f,
                a.chestY()+0.1f,a.z-(float)Math.sin(yaw)*a.radius-(float)Math.cos(yaw)*cp*0.4f};
    }

    private void spellFromHand(Projectile p, Actor a, float yaw, float pitch) {
        float dx=-(float)Math.sin(yaw)*(float)Math.cos(pitch),dy=(float)Math.sin(pitch),dz=-(float)Math.cos(yaw)*(float)Math.cos(pitch);
        float tx=a.x+dx*24,ty=a.eyeY()+dy*24,tz=a.z+dz*24;
        float[] hand=castOrigin(a,yaw,pitch);p.x=hand[0];p.y=hand[1];p.z=hand[2];
        float speed=(float)Math.sqrt(p.vx*p.vx+p.vy*p.vy+p.vz*p.vz),length=(float)Math.sqrt(Math.pow(tx-p.x,2)+Math.pow(ty-p.y,2)+Math.pow(tz-p.z,2));
        p.vx=(tx-p.x)/length*speed;p.vy=(ty-p.y)/length*speed;p.vz=(tz-p.z)/length*speed;
    }

    /** Telekinetic grab: pulls the loose item under the aim into the caster's inventory (an owned one is still theft). */
    private boolean telegrab(Actor a, Spell s, float yaw, float pitch) {
        float dx = (float) (-Math.sin(yaw) * Math.cos(pitch)), dy = (float) Math.sin(pitch), dz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        rpg.objects.GroundItem best = null;
        float bestC = 0.985f;
        for (var o : g.objects.all()) {
            if (!(o instanceof rpg.objects.GroundItem gi)) continue;
            float rx = gi.x - a.x, ry = gi.y - a.eyeY(), rz = gi.z - a.z, d = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (d > 15 || d < 0.5f) continue;
            float c = (rx * dx + ry * dy + rz * dz) / d;
            if (c > bestC && g.vox.sight(a.x, a.eyeY(), a.z, gi.x, gi.y + 0.1f, gi.z)) { bestC = c; best = gi; }
        }
        if (best == null) { if (a == g.player) g.chat.add("There's no item there to grab.", Chat.Kind.WARN); return false; }
        var r = best.takeBy(g, a, g.state.newRequestId(), "telekinesis");
        if (r.rejected()) { if (a == g.player) g.chat.add(r.reason(), Chat.Kind.WARN); return false; }
        a.mana -= s.cost;
        a.cs.castCooldown = 0.7f;
        casts++;
        var ev = g.state.commit("SpellCast", a.id, best.id, null, 0, s.title);
        g.prog.award(a, Skill.MAGIC, s.xp, "cast " + s.title, ev.id());
        if (g.fx != null) for (int i = 0; i < 12; i++) g.fx.trail(best.x + (a.x - best.x) * i / 12f, best.y + 0.3f, best.z + (a.z - best.z) * i / 12f, 1.5f, 1.2f, 4);
        return true;
    }

    /** Raises or lowers a prayer (GDD 2.5: toggled, paid a point an interval; see Prayers). */
    public boolean bless(Actor a, Prayer p) {
        boolean on = g.prayers.toggle(a, p);
        if (on) blessings++;
        return on;
    }

    private Projectile launch(Actor a, float yaw, float pitch, float speed, float gravity) {
        Projectile p = new Projectile(); p.id = ++projectileSerial;
        float dx = (float) (-Math.sin(yaw) * Math.cos(pitch)), dy = (float) Math.sin(pitch), dz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        p.owner = a;
        p.x = a.x + dx * 0.5f; p.y = a.eyeY() - 0.1f + dy * 0.5f; p.z = a.z + dz * 0.5f;
        p.vx = dx * speed; p.vy = dy * speed; p.vz = dz * speed; p.gravity = gravity; p.life = 4f;
        projectiles.add(p);
        g.noise(a, a.x, a.y, a.z, 10, 0.4f);
        return p;
    }

    // ------------------------------------------------------------------ the frame
    public void update(float dt) {
        for (Actor a : g.actors()) { tick(a, dt); if (!a.stuck.isEmpty()) shedLodged(a, LODGED_FOR); }
        updateProjectiles(dt);
    }

    private void tick(Actor a, float dt) {
        Actor.Combat c = a.cs;
        if (!a.alive) return;
        c.flash = Math.max(0, c.flash - dt * 3);
        c.iframes = Math.max(0, c.iframes - dt);
        c.dodgeT = Math.max(0, c.dodgeT - dt);
        c.castCooldown = Math.max(0, c.castCooldown - dt * a.actionRate());
        if (c.reload > 0) {
            float was = c.reload;
            c.reload = Math.max(0, c.reload - dt);
            // the string drawn back to the nut midway, then the bolt laid in the groove (anyone's crossbow, heard nearby)
            if (g.sfx != null && was > 0.6f && c.reload <= 0.6f) g.sfx.play("crossbow.load", a.x, a.eyeY(), a.z, 0.45f, 1, 14);
            if (g.sfx != null && c.reload == 0) g.sfx.play("bow.nock", a.x, a.eyeY(), a.z, 0.3f, 1.1f, 10);
        }
        c.slowT = Math.max(0, c.slowT - dt);
        c.frozenT = Math.max(0, c.frozenT - dt);
        c.stabilityBonus = Math.max(0, c.stabilityBonus - dt * 8f);
        if (c.staggered > 0) { c.staggered -= dt; c.blocking = false; }
        // Resources: stamina recovers outside guard/recovery; mana regenerates, while Faith requires restoration.
        c.regenDelay -= dt;
        if (c.regenDelay <= 0 && !c.blocking) a.stamina = Math.min(a.maxStamina(), a.stamina + g.cfg.staminaRegen * (1 + a.skills.level(Skill.AGILITY) * 0.006f) * dt);
        // Faith does not refill by itself (GDD 2.5); Mana regains a point a minute, the part-minute kept
        if (a.mana < a.maxMana()) { a.manaAcc += dt; while (a.manaAcc >= 60 && a.mana < a.maxMana()) { a.manaAcc -= 60; a.mana = Math.min(a.maxMana(), a.mana + 1); } }
        else a.manaAcc = 0;
        if (g.time - c.lastHurt > 8 && !a.dummy) a.hp = Math.min(a.maxHp(), a.hp + a.maxHp() * 0.004f * dt);

        if (c.hitstop > 0) {
            float held = Math.min(c.hitstop, dt);
            c.hitstop -= held; dt -= held;
            if (dt <= 0) return;
        }
        if (c.phase == Phase.IDLE) return;
        c.t += dt * a.actionRate();
        // Carry elapsed time through boundaries. A slow frame must not add a frame of latency to every phase.
        for (int transition = 0; transition < 3; transition++) {
            if (c.phase == Phase.WINDUP) {
                if (c.t < c.windup) return;
                c.t -= c.windup; c.phase = Phase.ACTIVE; c.sweep = 0;
                if (g.sfx != null) g.sfx.play(rpg.audio.Sfx.swingOf(c.move == Move.BASH ? a.off() : a.main()), a.x, a.y + 1.3f, a.z, c.move == Move.HEAVY ? 0.65f : 0.5f, c.move == Move.HEAVY ? 0.88f : 0.95f + (float) g.roll(a.id, (long) (g.time * 10)) * 0.12f, 20);
                g.noise(a, a.x, a.y, a.z, 6, 0.3f);
            } else if (c.phase == Phase.ACTIVE) {
                float to = Math.min(1, c.t / Math.max(1e-5f, c.active));
                sweep(a, c.sweep, to); c.sweep = to;
                if (c.phase != Phase.ACTIVE || c.t < c.active) return;
                c.t -= c.active; c.phase = Phase.RECOVER;
                if (c.hitstop > 0) return;
            } else if (c.phase == Phase.RECOVER) {
                if (c.t >= c.recover) c.cancel();
                return;
            } else return;
        }
    }

    // ------------------------------------------------------------------ the shape of a swing (the renderer draws exactly this)
    /** Easing through the active window: slow out of the wind-up, fastest through the middle, slowing into the follow-through. */
    public static float ease(float s) { return s <= 0 ? s : s >= 1 ? 1 : s * s * (3 - 2 * s); }

    /**
     * The strike paths: for each move, keys at k = -1 (cocked), -0.5, 0 (the middle of the cut, where it bites), 0.5 and
     * 1 (the follow-through) - the gripping hand relative to its shoulder (m; x the striking side's outward, y up, z
     * forward) and the blade's direction. A right-hand forehand cuts from the right shoulder across to the low left; a
     * backhand from the left shoulder across to the right; a heavy blow is raised over the head and driven down through
     * the target; a thrust is drawn back to the hip and driven straight out. The off hand mirrors them. Between keys the
     * path is a Catmull-Rom curve, so the hand never turns a corner.
     */
    private static final float[][][] PATHS = new float[6][][];
    static {
        PATHS[Move.LIGHT_R.ordinal()] = new float[][]{
                {0.22f, 0.18f, -0.06f, 0.35f, 0.55f, -0.76f}, {0.32f, 0.10f, 0.22f, 0.92f, 0.25f, 0.30f}, {0.06f, -0.02f, 0.48f, 0.05f, 0.02f, 1f},
                {-0.28f, -0.12f, 0.36f, -0.88f, -0.12f, 0.45f}, {-0.30f, -0.28f, 0.10f, -0.55f, -0.45f, -0.70f}};
        PATHS[Move.LIGHT_L.ordinal()] = new float[][]{
                {-0.30f, 0.12f, 0.02f, -0.45f, 0.50f, -0.74f}, {-0.30f, 0.05f, 0.30f, -0.92f, 0.18f, 0.35f}, {0.02f, -0.04f, 0.48f, -0.02f, 0.0f, 1f},
                {0.30f, -0.10f, 0.32f, 0.90f, -0.08f, 0.42f}, {0.34f, -0.22f, 0.04f, 0.62f, -0.40f, -0.68f}};
        PATHS[Move.HEAVY.ordinal()] = new float[][]{
                {0.10f, 0.38f, -0.10f, 0.05f, 0.55f, -0.84f}, {0.08f, 0.42f, 0.16f, 0.0f, 1f, 0.12f}, {0.04f, 0.16f, 0.45f, 0.0f, 0.25f, 0.97f},
                {0.0f, -0.14f, 0.44f, -0.03f, -0.62f, 0.78f}, {-0.04f, -0.36f, 0.28f, -0.08f, -0.94f, 0.25f}};
        PATHS[Move.THRUST.ordinal()] = new float[][]{
                {0.14f, -0.08f, -0.14f, 0.05f, 0.08f, 1f}, {0.11f, -0.04f, 0.10f, 0.03f, 0.05f, 1f}, {0.06f, 0.0f, 0.36f, 0.0f, 0.02f, 1f},
                {0.03f, 0.01f, 0.48f, 0.0f, 0.0f, 1f}, {0.03f, 0.0f, 0.46f, 0.0f, -0.05f, 1f}};
        PATHS[Move.PUNCH.ordinal()] = new float[][]{
                {0.10f, -0.06f, -0.04f, 0.0f, 0.1f, 1f}, {0.08f, -0.03f, 0.12f, 0.0f, 0.05f, 1f}, {0.04f, 0.0f, 0.34f, 0.0f, 0.0f, 1f},
                {0.02f, 0.01f, 0.46f, 0.0f, 0.0f, 1f}, {0.02f, 0.0f, 0.44f, 0.0f, 0.0f, 1f}};
        PATHS[Move.BASH.ordinal()] = new float[][]{
                {0.05f, 0.0f, -0.02f, 0.0f, 1f, 0.1f}, {0.0f, 0.02f, 0.14f, 0.0f, 1f, 0.05f}, {-0.05f, 0.03f, 0.32f, 0.0f, 1f, 0.0f},
                {-0.06f, 0.03f, 0.42f, 0.0f, 1f, 0.0f}, {-0.06f, 0.02f, 0.40f, 0.0f, 1f, 0.0f}};
    }

    /** s (0..1 through the active window; below 0 is the cocked wind-up) to the path's key parameter (-1..1). */
    private static float key(float s) { return s <= 0 ? -1 : -1 + 2 * ease(Math.min(1, s)); }

    /** A point of a path at k (-1..1): Catmull-Rom through its five keys. */
    private static float[] path(Move m, float k) {
        float[][] p = PATHS[m.ordinal()];
        float f = (Math.max(-1, Math.min(1, k)) + 1) * 2;      // 0..4 over the keys
        int i = Math.min(3, (int) f);
        float t = f - i;
        float[] a = p[Math.max(0, i - 1)], b = p[i], c = p[i + 1], d = p[Math.min(4, i + 2)];
        float[] o = new float[6];
        for (int j = 0; j < 6; j++) {
            o[j] = 0.5f * ((2 * b[j]) + (-a[j] + c[j]) * t + (2 * a[j] - 5 * b[j] + 4 * c[j] - d[j]) * t * t + (-a[j] + 3 * b[j] - 3 * c[j] + d[j]) * t * t * t);
        }
        return o;
    }

    /**
     * How far the torso is turned (radians; + brings the right shoulder forward) at s through a move: wound away in the
     * wind-up, unwinding through the strike into the follow-through - the hips lead, the shoulders follow.
     */
    public static float twist(Move m, boolean offHand, float s) {
        float from, to;
        switch (m) {
            case LIGHT_R -> { from = -0.5f; to = 0.42f; }
            case LIGHT_L -> { from = 0.42f; to = -0.38f; }
            case HEAVY -> { from = -0.32f; to = 0.18f; }
            case THRUST, PUNCH -> { from = -0.38f; to = 0.3f; }
            case BASH -> { from = 0.3f; to = -0.2f; }
            default -> { from = 0; to = 0; }
        }
        if (offHand) { from = -from; to = -to; }
        float k = (key(s) + 1) / 2;
        return from + (to - from) * k;
    }

    /** The torso's forward lean (radians): a heavy blow rears back, then drives down through the target. */
    public static float lean(Move m, float s) {
        float k = (key(s) + 1) / 2;
        return switch (m) {
            case HEAVY -> -0.22f + 0.6f * k;
            case THRUST, PUNCH, BASH -> -0.08f + 0.3f * k;
            default -> 0.03f + 0.1f * (float) Math.sin(Math.PI * k);
        };
    }

    /** The striking arm's shoulder in the world, with the torso leaning and turned (about the hips) as the move has it. */
    public static float[] shoulder(Actor a, boolean offHand, float twist, float lean) {
        rpg.actor.Body b = a.plan();
        float lx = (offHand ? -1 : 1) * b.shoulderX(), up = b.shoulderY() - b.hipY();
        float ly = up * (float) Math.cos(lean), lz = -up * (float) Math.sin(lean);
        float c = (float) Math.cos(twist), sn = (float) Math.sin(twist);
        float tx = lx * c + lz * sn, tz = -lx * sn + lz * c;
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw), bx = (float) Math.sin(a.yaw), bz = (float) Math.cos(a.yaw);
        float px = rpg.actor.Body.PX * a.figureScale();
        return new float[]{a.x + (rx * tx + bx * tz) * px, a.y + (b.hipY() + ly) * px, a.z + (rz * tx + bz * tz) * px};
    }

    /** A path point (hand, blade) in the actor's frame to the world: mirrored for the off hand, tipped by the aim's pitch. */
    private static float[] toWorld(Actor a, float[] q, boolean offHand) {
        float side = offHand ? -1 : 1, k = a.figureScale();
        float hx = q[0] * side * k, hy = q[1] * k, hz = q[2] * k, dx = q[3] * side, dy = q[4], dz = q[5];
        // the aim's pitch tips the whole stroke about the shoulders' line
        float p = a.pitch, cp = (float) Math.cos(p), sp = (float) Math.sin(p);
        float hy2 = hy * cp + hz * sp, hz2 = -hy * sp + hz * cp, dy2 = dy * cp + dz * sp, dz2 = -dy * sp + dz * cp;
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw), fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        float l = (float) Math.sqrt(dx * dx + dy2 * dy2 + dz2 * dz2);
        return new float[]{rx * hx + fx * hz2, hy2, rz * hx + fz * hz2, (rx * dx + fx * dz2) / l, dy2 / l, (rz * dx + fz * dz2) / l};
    }

    /** The gripping hand and the blade's direction at s through the move: {hand x, y, z, dir x, y, z}. */
    public static float[] grip(Actor a, Move m, float s) {
        float[] sh = shoulder(a, a.cs.offHand, twist(m, a.cs.offHand, s), lean(m, s));
        float[] w = toWorld(a, path(m, key(s)), a.cs.offHand);
        return new float[]{sh[0] + w[0], sh[1] + w[1], sh[2] + w[2], w[3], w[4], w[5]};
    }

    /** A point on the blade: s through the swing (0..1; below 0 in the wind-up), u along the blade (0 hand .. 1 tip); then the shoulder. */
    public static float[] blade(Actor a, Move m, float s, float u, float reach) {
        float[] sh = shoulder(a, a.cs.offHand, twist(m, a.cs.offHand, s), lean(m, s));
        float[] w = toWorld(a, path(m, key(s)), a.cs.offHand);
        float dist = (0.06f + u * reach) * Math.max(1, a.figureScale());
        return new float[]{sh[0] + w[0] + w[3] * dist, sh[1] + w[1] + w[4] * dist, sh[2] + w[2] + w[5] * dist, sh[0], sh[1], sh[2]};
    }

    public static float reach(Actor a) {
        ItemDef w = a.cs.offHand ? a.off() : a.main();
        if (a.cs.move == Move.PUNCH) return 0.12f;   // measured from the hand: a fist, a shield's face
        if (a.cs.move == Move.BASH) return 0.25f;
        return w == null ? 0.12f : w.reach;
    }

    private void sweep(Actor a, float from, float to) {
        if (a instanceof rpg.animal.Animal an) { bite(an, from, to); return; }
        Move m = a.cs.move; float reach = reach(a);
        for (int i = 1; i <= 6; i++) {
            float s = from + (to - from) * i / 6;
            float[] tip = blade(a, m, s, 1, reach), grip = grip(a, m, s);
            float dx = tip[0] - grip[0], dy = tip[1] - grip[1], dz = tip[2] - grip[2];
            float length = Math.max(0.001f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz)); dx /= length; dy /= length; dz /= length;
            // A low stroke may enter the floor after hitting a small animal. Resolve the exposed blade before that wall.
            // (an open door's leaf is no wall: the blade knocks it aside and carries on to whoever stands in the doorway)
            Voxels.Hit wall = g.vox.ray(grip[0], grip[1], grip[2], dx, dy, dz, length, false);
            if (wall.hit && wall.door != null && wall.door.open && !wall.door.locked) {
                wall.door.knock(g, wall.x, wall.z, dx, dz);
                wall = g.vox.ray(grip[0], grip[1], grip[2], dx, dy, dz, length, false, false);
            }
            boolean hard = wall.hit && !Blocks.glass(wall.block) && !Blocks.isLeaves(wall.block);
            float limit = hard ? wall.dist : length;
            if (!g.vox.clear(tip[3], tip[4], tip[5], grip[0], grip[1], grip[2], false)) limit = 0;
            if (limit > 0) for (Actor v : g.actors()) {
                if (v == a || !v.alive || a.cs.struck.contains(v.id) || (v.team != 0 && v.team == a.team && !hostileTo(a, v))) continue;
                float contact = HitVolumes.ray(g, v, grip[0], grip[1], grip[2], dx, dy, dz, limit, 0.06f);
                if (!Float.isFinite(contact)) continue;
                a.cs.struck.add(v.id); meleeContact(a, v, m, grip[0] + dx * contact, grip[1] + dy * contact, grip[2] + dz * contact);
                if (a.cs.phase != Phase.ACTIVE) return;
            }
            if (wall.hit) strikeWorld(a, m, reach, s, tip, wall);
            if (a.cs.phase != Phase.ACTIVE) return;
        }
    }

    private void strikeWorld(Actor a, Move m, float reach, float s, float[] tip, Voxels.Hit h) {
        // a tool meeting what it works: an axe biting a tree (trunk or crown), a pickaxe striking the ore -
        // once a swing; the head bites and stops in wood or stone (in leaves it carries on through)
        if (!a.cs.gathered && g.objects != null) {
            rpg.objects.Node nd = g.objects.nodeAt(h.bx, h.by, h.bz);
            if (nd != null && nd.strike(g, a, h.x, h.y, h.z)) {
                a.cs.gathered = true;
                boolean leafy = h.block == Blocks.LEAVES || h.block == Blocks.OAK_LEAVES || h.block == Blocks.WILLOW_LEAVES;
                if (!leafy) { a.cs.hitstop = 0.06f; a.cs.phase = Phase.RECOVER; a.cs.t = 0; return; }
            }
        }
        if (Blocks.glass(h.block) && g.glass != null) {
            // glass does not turn a blade: it breaks, and the blow carries on through
            float[] d0 = blade(a, m, Math.max(0, s - 0.1f), 1, reach);
            g.glass.shatter(h.bx, h.by, h.bz, h.x, h.y, h.z, tip[0] - d0[0], tip[1] - d0[1], tip[2] - d0[2], m == Move.HEAVY ? 1.4f : 1f, a);
            log(String.format("%s's %s broke the pane at (%d, %d, %d)", a.name, m, h.bx, h.by, h.bz));
        } else if (h.ny > 0 && m != Move.HEAVY && m != Move.THRUST && !Blocks.isLeaves(h.block)) {
            // a cut swung low at something small at the feet (a cat, a hen) meets the ground at a glancing angle: it
            // scrapes and skims on along it rather than stopping dead, so it still crosses what it was aimed at
            // (gamefixes: the forehand reached the floor just short of a cat below the attacker and ended there)
            if (!a.cs.grazed) {
                a.cs.grazed = true;
                if (g.fx != null) g.fx.puff(h.x, h.y, h.z);
                if (g.sfx != null) g.sfx.play("block", h.x, h.y, h.z, 0.25f);
            }
        } else if (h.block != Blocks.LEAVES && h.block != Blocks.OAK_LEAVES && h.block != Blocks.WILLOW_LEAVES) {
            deflections++;
            if (g.fx != null) g.fx.sparks(h.x, h.y, h.z, 10);
            if (g.sfx != null) g.sfx.play("block", h.x, h.y, h.z, 0.5f);
            a.cs.hitstop = 0.08f;
            a.cs.phase = Phase.RECOVER; a.cs.t = 0;
            log(String.format("%s's %s deflected by block %d at (%d, %d, %d), s=%.2f", a.name, m, h.block, h.bx, h.by, h.bz, s));
            return;
        }
    }

    /**
     * An animal's strike: its mouth (or forefeet, or hooves) lunges forward from its chest along its heading through the
     * active window, and whatever hitbox it passes through is struck - the same contact, damage, guarding and stagger
     * rules a blade meets, with the species' own weapon in place of an item.
     */
    private void bite(rpg.animal.Animal an, float from, float to) {
        var sp = an.species;
        float fx = (float) -Math.sin(an.yaw), fz = (float) -Math.cos(an.yaw);
        float body = an.radius * 1.4f, reach = sp.reach * an.scale();
        // a pounce: a strike made on the run carries the body on through it (half a metre and more at a gallop)
        reach += Math.min(1.2f, (float) Math.hypot(an.vx, an.vz) * 0.22f);
        float hy = an.y + an.height * (sp.weapon.equals("kick") || sp.weapon.equals("swipe") ? 0.55f : 0.62f);
        for (int i = 1; i <= 4; i++) {
            float s = from + (to - from) * i / 4;
            float k = body + reach * ease(s);
            float px = an.x + fx * k, pz = an.z + fz * k;
            for (Actor v : g.actors()) {
                if (v == an || !v.alive || an.cs.struck.contains(v.id) || (v.team == an.team && !hostileTo(an, v))) continue;
                // the jaws (or hooves) close on whatever is within their span of the strike point: a sphere, not a blade's edge
                float ddx = px - v.x, ddz = pz - v.z, span = v.radius + 0.22f + an.radius * 0.3f;
                if (ddx * ddx + ddz * ddz > span * span || hy < v.y - 0.35f || hy > v.y + v.height + 0.45f) continue;
                an.cs.struck.add(v.id);
                boolean behind = behind(v, an);
                float potential = physicalMax(an, Skill.STRENGTH, sp.damage * an.scale());
                float impact = CombatMath.impact(sp.impact * an.scale(), an.skills.level(Skill.STRENGTH), 1);
                resolve(an, v, potential, impact, sp.damageType, null, px, hy, pz, sp.weapon, behind);
                if (an.cs.phase != Phase.ACTIVE) return;
            }
        }
    }

    /** Whether two actors on one team may still fight (a guard arresting a resisting townsperson). */
    private boolean hostileTo(Actor a, Actor v) { return g.ai != null && g.ai.hostile(a, v); }
    /** On the same side and not at odds: a shot that strikes them is a stray, not an attack. */
    private boolean friendly(Actor a, Actor v) { return v.team != 0 && v.team == a.team && !hostileTo(a, v) || a.summonOwner == v.id || v.summonOwner == a.id; }

    /**
     * The first body other than the shooter and the target standing in the line from the shooter's eye to the target's
     * chest (within a body's breadth of it), or null when the line is clear: what an archer looks past before loosing.
     */
    public Actor inLineOfFire(Actor shooter, Actor target) {
        float ox = shooter.x, oy = shooter.eyeY(), oz = shooter.z, dx = target.x - ox, dy = target.chestY() - oy, dz = target.z - oz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.5f) return null;
        dx /= len; dy /= len; dz /= len;
        Actor best = null; float nearest = len;
        for (Actor v : g.actors()) {
            if (v == shooter || v == target || !v.alive || v instanceof rpg.ai.Npc npc && !npc.loaded) continue;
            float distance = HitVolumes.ray(g, v, ox, oy, oz, dx, dy, dz, nearest, 0.2f);
            if (distance < nearest) { nearest = distance; best = v; }
        }
        return best;
    }

    private void meleeContact(Actor a, Actor v, Move m, float px, float py, float pz) {
        ItemDef w = a.cs.offHand ? a.off() : a.main();
        boolean weapon = w != null && m != Move.PUNCH && m != Move.BASH;
        float impactBase = weapon ? w.impact : m == Move.BASH ? 26 : 6;
        DamageType type = weapon ? w.damageType : DamageType.BLUNT;
        boolean behind = behind(v, a);
        // Strength makes the damage of a landed blow (with the weapon's Strength bonus); Attack does not (GDD 2.3)
        float maximum = physicalMax(a, Skill.STRENGTH, weapon ? w.strengthBonus : 0);
        if (weapon) maximum = Math.max(2, maximum);
        float potential = (float) Math.floor(maximum * CombatMath.moveDamage(m));
        if (m == Move.HEAVY && (a.sneaking || a.crouched) && v instanceof rpg.ai.Npc target && !g.ai.canSee(target, a)
                && (target.aware.get(a.id) == null || target.aware.get(a.id).level < rpg.ai.Stealth.ALERT)) potential = (float)Math.floor(potential * 1.5f);
        float impact = CombatMath.impact(impactBase, a.skills.level(Skill.STRENGTH), CombatMath.moveImpact(m));
        resolve(a, v, potential, impact, type, null, px, py, pz, String.valueOf(m), behind);
    }

    private static boolean behind(Actor v, Actor a) {
        float fx = (float) -Math.sin(v.yaw), fz = (float) -Math.cos(v.yaw), dx = a.x - v.x, dz = a.z - v.z, l = (float) Math.hypot(dx, dz);
        return l > 1e-3f && (fx * dx + fz * dz) / l < -0.5f;
    }

    /** Contact made: guard and parry, mitigation, HP damage, impact and stagger, feedback, XP, events. */
    public void resolve(Actor a, Actor v, float potential, float impact, DamageType type, Projectile proj, float px, float py, float pz, String how, boolean behind) {
        if (!a.alive) { environmentalDamage(v, Math.max(0, Math.round(potential)), how); return; }
        if (a instanceof rpg.ai.Npc && g.law.inCustody(v)) return;
        contacts++;
        if (v.cs.iframes > 0 || v.gaseousT > 0 && proj == null) {
            dodged++;
            if (g.fx != null) g.fx.text("Dodged", v.x, v.y + v.height + 0.3f, v.z, 0xFFB0E0FF);
            log(String.format("%s -> %s %s: DODGED (i-frames)", a.name, v.name, how));
            return;
        }
        boolean melee = proj == null && type != DamageType.MAGIC;
        ItemDef guardItem = v.off() != null && v.off().family == Family.SHIELD ? v.off() : melee && v.main() != null && v.main().guard > 0 ? v.main() : null;
        float fx = (float) -Math.sin(v.yaw), fz = (float) -Math.cos(v.yaw), tx = a.x - v.x, tz = a.z - v.z, tl = Math.max(1e-3f, (float) Math.hypot(tx, tz));
        boolean facing = (fx * tx + fz * tz) / tl > 0.35f;
        boolean bare = v.main() == null && v.off() == null && !(v instanceof rpg.animal.Animal);
        boolean guarding = v.cs.blocking && facing && (guardItem != null || bare && melee) && type != DamageType.MAGIC;
        g.noise(a, v.x, v.y, v.z, g.cfg.combatNoise, 0.8f);
        g.ai.onAttacked(v, a);
        if (v instanceof rpg.animal.Animal an && g.animals != null) g.animals.onHurt(an, a);
        if (v instanceof rpg.monster.Monster mo && g.monsters != null) g.monsters.onHurt(mo, a, potential);
        if (guarding && !bare && melee && g.time - v.cs.blockStart <= CombatMath.parryWindow(g.cfg.parryWindow, v.skills.level(Skill.DEFENSE))) {
            parried++;
            a.cs.cancel();
            a.cs.staggered = 0.9f;
            a.cs.hitstop = 0.1f;
            if (g.fx != null) { g.fx.sparks(px, py, pz, 24); g.fx.text("Parry!", v.x, v.y + v.height + 0.3f, v.z, 0xFFFFE070); }
            if (g.sfx != null) g.sfx.play(rpg.audio.Sfx.blockOf(guardItem).equals("block") ? "parry" : rpg.audio.Sfx.blockOf(guardItem), px, py, pz, 0.9f);
            var ev = g.state.commit("Parry", v.id, a.id, null, 0, how);
            g.prog.award(v, Skill.DEFENSE, 6, "parry", ev.id());
            log(String.format("%s -> %s %s: PARRIED within %.2fs; attacker staggered", a.name, v.name, how, g.time - v.cs.blockStart));
            return;
        }
        float protection = CombatMath.protection(v.skills.level(Skill.DEFENSE), v.eq.armor());
        int maximum = CombatMath.armoredMax((int) Math.floor(Math.max(0, potential)), v.skills.level(Skill.DEFENSE), v.eq.armor());
        float mit = potential <= 0 ? 0 : 1 - maximum / potential;
        // Roll once per contact after a successful parry has already returned. Defenses may still reduce it to zero.
        float dmg = CombatMath.roll(maximum, a == g.player, damageRandom);
        dmg *= g.prayers.ward(v);
        // an elemental spell on a creature with that weakness on record: double (GDD 2.5)
        if (proj != null && proj.spell != null && v instanceof rpg.animal.Animal wan && wan.species.weakness == proj.spell.element && proj.spell.element != Spells.Element.NONE) dmg *= 2;
        // the overhead protections: the attack of their kind does no harm at all - a visible counter, not armour
        boolean magicHit = type == DamageType.MAGIC;
        boolean prayed = g.prayers.protects(v, melee, magicHit) && dmg > 0;
        if (prayed) { if (g.fx != null) g.fx.text("Protected", v.x, v.y + v.height + 0.5f, v.z, 0xFFE8F0FF); g.prayers.prevented(v, dmg, 0); dmg = 0; impact *= 0.3f; }
        float blockedPart = 0;
        if (guarding) {
            // the guard stops its share - a better defender behind better armour stops more, and tires less doing it
            float share = Math.min(0.97f, (bare ? Math.min(0.5f, (type == DamageType.BLUNT ? 0.25f : 0.1f) + v.skills.level(Skill.DEFENSE) * 0.002f) : CombatMath.guardShare(guardItem.guard, v.skills.level(Skill.DEFENSE), v.eq.armor())) * g.prayers.guard(v));
            blockedPart = dmg * share;
            dmg -= blockedPart;
            float cost = (blockedPart * g.cfg.staminaBlockPerDamage * 3 / CombatMath.HP_SCALE * 0.1f + impact * 0.15f) * (1 - Math.min(0.4f, v.skills.level(Skill.DEFENSE) * 0.004f));
            impact *= 1 - share;
            if (v.stamina < cost) {
                v.stamina = 0; v.cs.blocking = false; v.cs.staggered = 0.8f;
                if (g.fx != null) g.fx.text("Guard broken!", v.x, v.y + v.height + 0.3f, v.z, 0xFFFF8040);
            } else v.stamina -= cost;
            v.cs.regenDelay = 0.8f;
            blocked++;
            if (g.sfx != null) g.sfx.play(bare ? "hit" : rpg.audio.Sfx.blockOf(guardItem), px, py, pz, 0.7f);
            if (g.fx != null && !bare) g.fx.sparks(px, py, pz, 12);
        }
        // Do not impose a minimum after a performed guard or protection: a fully stopped hit can deal zero.
        int dealt = Math.max(0, Math.round(dmg));
        if (!v.dummy) dealt = Math.min(dealt, (int) Math.ceil(v.hp));
        // the console's god (nothing hurts) and buddha (hurt, never below 1 HP)
        if (v == g.player && g.ui != null && g.ui.console.god) { dealt = 0; impact = 0; }
        if (v == g.player && g.ui != null && g.ui.console.immortal) dealt = Math.min(dealt, Math.max(0, (int) Math.ceil(v.hp) - 1));
        v.hp -= dealt;
        Spells.Element element = proj != null && proj.spell != null ? proj.spell.element : Spells.Element.NONE;
        // the wound shows on the body: cuts and stabs, and the spells that tear (ice, wind, earth, blood) as well
        if(dealt>0 && (type==DamageType.SLASH || type==DamageType.PIERCE || type==DamageType.MAGIC && element!=Spells.Element.WATER))v.injuries.cut(v,px,py,pz,dealt);
        v.cs.lastHurt = g.time;
        v.cs.lastAttacker = a.id;
        v.cs.flash = 1;
        // impact against Stability, a channel of its own
        float stab = CombatMath.stability(v.skills.level(Skill.DEFENSE), v.skills.level(Skill.STRENGTH), v.eq.armor(), v.cs.stabilityBonus);
        boolean stagger = impact > stab && v.cs.staggered <= 0;
        if (stagger) {
            staggers++;
            v.cs.cancel();
            v.cs.blocking = false;
            v.cs.staggered = 0.55f;
            v.cs.stabilityBonus += CombatMath.staggerResistance(stab);
            g.interact.cancel(v, "staggered");
        }
        a.cs.hitstop = dealt > 0 ? 0.045f : 0.03f;
        v.cs.hitstop = dealt > 0 ? 0.07f : 0.03f;
        if (v == g.player && g.fx != null) g.fx.shake = Math.min(1, g.fx.shake + 0.25f + dealt * 0.01f);
        if (a == g.player && g.fx != null) g.fx.shake = Math.min(1, g.fx.shake + 0.08f);
        if (g.fx != null) {
            if (dealt > 0) g.fx.blood(px, py, pz, -tx / tl, -tz / tl);
            g.fx.text(dealt > 0 ? String.valueOf(dealt) : guarding ? "Blocked" : "0", v.x, v.y + v.height + 0.2f, v.z, dealt > 0 ? (v == g.player ? 0xFFFF4040 : 0xFFFFFFFF) : 0xFF80C0FF);
            if (stagger) g.fx.text("Staggered", v.x, v.y + v.height + 0.6f, v.z, 0xFFFFC040);
        }
        if (g.sfx != null && dealt > 0) {
            boolean heavy = potential > 30 * CombatMath.HP_SCALE;
            ItemDef weapon = a.cs.offHand ? a.off() : a.main();
            boolean fist = weapon == null || weapon.family == Family.SHIELD || weapon.natural && type == DamageType.BLUNT;
            String key = proj != null && proj.ammo != null ? (v.eq.armor() > 20 ? "arrow.metal" : "arrow.flesh") : proj != null ? "hit" : rpg.audio.Sfx.hitOf(type, fist, heavy);
            g.sfx.play(key, px, py, pz, heavy ? 0.9f : 0.75f);
            // a heavy blow is felt as well as heard: the body's thump under the cut
            if (heavy && !fist && proj == null) g.sfx.play("heavyhit", px, py, pz, 0.45f);
        }
        if (a == g.player && v instanceof rpg.ai.Npc sp && sp.spareSince > 0) sp.spareSince = -1;
        if (g.sfx != null && dealt > 0 && v.hp > 0 && !v.dummy && !(v instanceof rpg.animal.Animal)) g.sfx.hurtCry(v, g.time);
        String detail = String.format("%s -> %s %s%s: potential %.1f, protection %.0f (%.0f%% mit %s)%s%s, dealt %d; impact %.1f vs stability %.1f%s; stamina %.0f",
                a.name, v.name, how, behind ? " (from behind)" : "", potential, protection, mit * 100, type,
                g.prayers.on(v, Prayer.WARD) ? ", ward" : "", guarding ? String.format(", guarded %.1f", blockedPart) : "", dealt, impact, stab, stagger ? " -> STAGGER" : "", v.stamina);
        if (a == g.player && a != v && dealt > 0) lastPlayerHit = g.time;
        var ev = g.state.commit("ActorHit", a.id, v.id, rpg.world.DevMap.jurisdiction(v.x, v.z), 0, detail);
        log(detail);
        // GDD 2.2: real melee damage trains Attack and Strength; Defense follows harm endured or active defense.
        if (dealt > 0 && a != v && v.summonOwner == 0) {
            if (melee) {
                g.prog.award(a, Skill.ATTACK, dealt * g.cfg.xpPerDamage * 0.5f, "melee damage", ev.id());
                g.prog.award(a, Skill.STRENGTH, dealt * g.cfg.xpPerDamage * 0.5f, "melee damage", ev.id());
            } else {
                Skill skill=type==DamageType.MAGIC?Skill.MAGIC:proj.skill;
                g.prog.award(a,skill,dealt*g.cfg.xpPerDamage*(skill==Skill.MAGIC?0.5f:1f),"damage dealt",ev.id());
            }
            g.prog.award(a, Skill.HEALTH, dealt * g.cfg.healthXpPerDamage, "damage dealt (Health share)", ev.id());
            g.prog.award(v, Skill.DEFENSE, dealt * g.cfg.defenseXpPerDamage, "hostile damage suffered", ev.id());
        }
        if (blockedPart > 0) g.prog.award(v, Skill.DEFENSE, blockedPart * g.cfg.defenseXpPerBlocked, "damage guarded", ev.id());
        if (g.law != null) g.law.onHit(a, v, ev.id());
        // the wound itself: blood, stains, and a part taken off by a heavy enough cut (Gore)
        if (g.gore != null && dealt > 0) {
            boolean deadly = v.hp <= 0 && !v.dummy && !(g.law != null && a instanceof rpg.ai.Npc ln && (ln.role == rpg.ai.Npc.Role.GUARD || ln.role == rpg.ai.Npc.Role.LAWMAN) && g.law.bounty(v.id, ln.homeJurisdiction) > 0);
            g.gore.onHit(a, v, dealt, dmg, type, element, px, py, pz, -tx / tl, -tz / tl, deadly);
        }
        // a blow that knocks a person off balance (or lands on one already reeling) moves the body itself: Sydney takes it,
        // by the blow's momentum - how far past their Stability it struck, or a fraction of it on someone already reeling
        if (v instanceof rpg.ai.Npc vn && vn.alive && v.hp > 0 && g.reactions != null && (stagger || vn.reacting) && !guarding) {
            float over = Math.max(0, impact - stab * 0.6f);
            float J = Math.max(14f, Math.min(170f, 14f + over * 1.6f));
            g.reactions.struck(vn, -tx / tl, 0.12f, -tz / tl, J, J * 0.3f, px, py, pz);
        }
        // the ground heaving under them: an Earth spell of the great tiers that strikes past their footing throws them down
        // (a wave or a surge always lands with the weight of the earth behind it; a blast when it beats their Stability)
        boolean quake = magicHit && element == Spells.Element.EARTH && proj != null && proj.spell != null && !prayed
                && (proj.spell.tier == Spells.Tier.WAVE || proj.spell.tier == Spells.Tier.SURGE || proj.spell.tier == Spells.Tier.BLAST && impact > stab);
        if (quake && v.alive && v.hp > 0 && !v.dummy && g.reactions != null && impact > stab * 0.6f) {
            float J = Math.max(60f, Math.min(220f, 60f + (impact - stab * 0.6f) * 2.2f));
            g.reactions.knockDown(v, -tx / tl, -tz / tl, J, px, py, pz);
        }
        // an animal struck: the blow is remembered (a killing one throws the body), and a heavy one fells a big beast
        if (v instanceof rpg.animal.Animal an && g.animals != null && dealt > 0) g.animals.struck(an, -tx / tl, -tz / tl, impact, (stagger || quake) && v.hp > 0);
        if (v.hp <= 0 && !v.dummy) {
            if (g.law != null && g.law.arrestInsteadOfKill(a, v)) v.hp = Math.max(1, v.hp);
            else kill(a, v, ev.id());
        }
    }

    /** Kills outright, with no blow struck (the console's kill, a fall into the void): nobody is to blame. */
    public void bleedOutNow(Actor v) {
        if (!v.alive || v.dummy) return;
        var ev = g.state.commit("Died", v.id, v.id, rpg.world.DevMap.jurisdiction(v.x, v.z), 0, v.name);
        v.hp = 0;
        kill(v, v, ev.id());
    }

    /** Someone bled to death: whoever opened the wound killed them (else the last to strike them). */
    public void bleedOut(Actor v) { bleedOut(v, 0); }
    public void bleedOut(Actor v, long woundedBy) {
        if (!v.alive) return;
        long by = woundedBy != 0 && woundedBy != v.id ? woundedBy : v.cs.lastAttacker;
        Actor killer = by != 0 ? g.actor(by) : null;
        // the watch's own wound on a wanted offender ends in custody, not a grave: the bleeding is stopped and they are
        // taken in (an officer kills only when he must - a lawman's cut used to bleed a wanted thief to death)
        if (killer != null && g.law != null && g.law.arrestInsteadOfKill(killer, v)) { v.hp = Math.max(1, v.hp); if (g.gore != null) g.gore.staunch(v); return; }
        var ev = g.state.commit("BledOut", killer == null ? 0 : killer.id, v.id, rpg.world.DevMap.jurisdiction(v.x, v.z), 0, v.name);
        kill(killer == null ? v : killer, v, ev.id());
    }

    /** Environmental harm commits once without an attack roll, fabricated attacker or combat XP. */
    public void environmentalDamage(Actor victim, int amount, String reason) {
        if (!victim.alive || amount <= 0 || victim.dummy || g.law.inCustody(victim)) return;
        if (victim == g.player && g.ui != null && g.ui.console.god) return;
        int damage = Math.min(amount, (int) Math.ceil(victim.hp)); victim.hp -= damage; victim.cs.lastHurt = g.time; victim.cs.flash = 1;
        var event = g.state.commit("EnvironmentalDamage", 0, victim.id, rpg.world.DevMap.jurisdiction(victim.x, victim.z), 0, reason + ": " + damage);
        if (g.fx != null) g.fx.text(String.valueOf(damage), victim.x, victim.y + victim.height, victim.z, 0xFFFF7050);
        if (g.sfx != null && victim.hp > 0 && !(victim instanceof rpg.animal.Animal)) g.sfx.hurtCry(victim, g.time);
        if (victim.hp <= 0) kill(null, victim, event.id());
    }

    private void kill(Actor killer, Actor v, long cause) {
        if (killer == g.player && v != g.player) lastPlayerKill = g.time;
        v.alive = false;
        shedLodged(v, Integer.MAX_VALUE);
        if (g.chemistry != null) g.chemistry.retire(v);
        for (Projectile shot : projectiles) if (shot.owner == v && !shot.done) { dropArrow(shot); shot.done = true; }
        if (g.gore != null) for (var wound : g.gore.wounds.values()) if (wound.by == v.id) wound.by = 0;
        for (Actor other : g.actors()) if (other.cs.lastAttacker == v.id) other.cs.lastAttacker = 0;
        v.hp = 0;
        v.cs.cancel();
        if (v != g.player) dropHeld(v);
        kills++;
        var ev = g.state.commit("ActorKilled", killer == null ? 0 : killer.id, v.id, rpg.world.DevMap.jurisdiction(v.x, v.z), cause, v.name + " killed by " + (killer == null ? "the environment" : killer.name));
        // a person's last cry, on the voice bus, from their own throat's bank (there was never a plain "death" bank, so no
        // human - the player included - made a sound when they died); a creature's cry is its species' (Animals)
        if (g.sfx != null && !(v instanceof rpg.animal.Animal)) g.sfx.deathCry(v);
        g.onKilled(v, killer, ev.id());
    }

    /**
     * (gamefixes) the dead let go: what was in their hands - the weapon, the shield or a second blade - falls from where
     * the hands were, a loose thing tumbling to the ground, not kept in the corpse's grip nor its pack. A creature's own
     * teeth and claws are no item and stay. (The player's own go with them to the grave, as before.)
     */
    private void dropHeld(Actor v) {
        if (g.objects == null) return;
        boolean fresh = g.time - v.handAt < 0.5;
        float yaw = v.yaw;
        int k = 0;
        for (ItemDef.Slot slot : new ItemDef.Slot[]{ItemDef.Slot.MAIN, ItemDef.Slot.OFF}) {
            ItemStack held = v.eq.get(slot);
            if (held == null || held.def.natural) continue;
            v.eq.set(slot, null);
            float side = slot == ItemDef.Slot.MAIN ? 1 : -1;
            float rx = (float) Math.cos(yaw) * side * 0.3f, rz = (float) -Math.sin(yaw) * side * 0.3f;
            float x = fresh && slot == ItemDef.Slot.MAIN ? v.hand[0] : v.x + rx, y = fresh && slot == ItemDef.Slot.MAIN ? v.hand[1] : v.chestY(), z = fresh && slot == ItemDef.Slot.MAIN ? v.hand[2] : v.z + rz;
            double r = g.roll(v.id, 71 + k++);
            float[] dir = {(float) Math.cos(yaw + r * 2) * 0.6f, 0.75f, (float) -Math.sin(yaw + r * 2) * 0.6f};
            float vx = v.vx * 0.6f + rx * 2.5f, vz = v.vz * 0.6f + rz * 2.5f;
            if (held.def.family == Family.SHIELD) g.objects.drop(held, x, Math.max(v.y + 0.05f, g.vox.groundY(x, v.y + 1, z) + 0.05f), z);
            else g.objects.dropLong(held, x, y, z, dir, false, vx, 0.6f, vz);
        }
    }

    // ------------------------------------------------------------------ projectiles
    private void updateProjectiles(float dt) {
        if(projectiles.isEmpty())return;
        projectileTargets.clear();projectileTargets.addAll(g.actors());
        for(var dead:g.npcs)if(!dead.alive && dead.loaded && dead.ragdoll!=0 && dead.corpse!=0)projectileTargets.add(dead);
        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            p.life -= dt;
            if (p.life <= 0 || p.done) { if (!p.done) dropArrow(p); releasePhysical(p); it.remove(); continue; }
            if (p.ammo != null && (p.ammo.def.family == Family.ARROW || p.ammo.def.family == Family.BOLT)) {
                if (p.rigid == 0) physicalArrow(p);
                var body = g.lastRigids.body(p.rigid);
                if (body != null) {
                    p.vx=body.vx; p.vy=body.vy; p.vz=body.vz;
                    sweep(p, body.x, body.y, body.z, dt);
                }
            } else {
                int sub = 4;
                for (int i = 0; i < sub && !p.done; i++) step(p, dt / sub);
            }

            if (!p.done && !p.heardPass && p.ammo != null && p.owner != g.player) {
                float dx = g.player.x - p.x, dy = g.player.eyeY() - p.y, dz = g.player.z - p.z;
                if (dx * dx + dy * dy + dz * dz < 16 && g.vox.sight(p.x, p.y, p.z, g.player.x, g.player.eyeY(), g.player.z)) {
                    p.heardPass = true;
                    if (g.sfx != null) g.sfx.play("arrow.fly", p.x, p.y, p.z, 0.26f, 1.4f, 10);
                }
            }
            if (p.done) { releasePhysical(p); it.remove(); }
        }
    }

    /** Impulsis owns flight. Gameplay sweeps consecutive native positions for exact world/posed-body contacts. */
    private void physicalArrow(Projectile p) {
        p.rigid = g.game.reserve(astratto.bridge.AstrattoNative.KIND_RIGID);
        int flags = astratto.bridge.AstrattoNative.RIGID_BULLET | astratto.bridge.AstrattoNative.RIGID_NO_SLEEP
                | astratto.bridge.AstrattoNative.RIGID_NO_WORLD | astratto.bridge.AstrattoNative.RIGID_NO_MOVERS;
        g.game.commands().rigidCreate(p.rigid, astratto.bridge.AstrattoNative.SHAPE_SPHERE, flags,
                0.018f,0,0,0.025f,p.x,p.y,p.z,1,0,0,0,p.vx,p.vy,p.vz,0,0,0,0.2f,0,
                0.00001f,0.00001f,p.gravity/22f,1<<29,1<<29,0,null);
    }
    private void releasePhysical(Projectile p) {
        if (p.rigid != 0) { g.game.commands().release(p.rigid); p.rigid=0; }
    }

    /**
     * What a spell does besides its damage: Fogish ice freezes the target where it stands (a creature's own ice for less
     * time), lightning jolts it, and Wind from Blast up can throw it off its feet - the likelier the smaller it is beside
     * a person and the further its skill falls short of the caster's.
     */
    private void spellEffect(Projectile p, Actor struck, float dx, float dz) {
        Spell s = p.spell;
        if (s == null) return;
        if (s.freeze() > 0) { struck.cs.frozenT = Math.max(struck.cs.frozenT, s.freeze() * (p.innate ? 0.4f : 1)); struck.cs.slowT = Math.max(struck.cs.slowT, struck.cs.frozenT); }
        if (s.element == Spells.Element.ELECTRIC) struck.cs.staggered = Math.max(struck.cs.staggered, 0.25f + (s.tier == null ? 0 : s.tier.ordinal() * 0.05f));
        float k = s.knockdown();
        if (k <= 0 || struck.dummy || struck.reacting || struck.morphT > 0) return;
        float size = Math.max(0.3f, struck.figureScale());
        int theirs = Math.max(struck.skills.level(Skill.DEFENSE), struck.skills.level(Skill.STRENGTH)), mine = p.owner.skills.level(Skill.MAGIC);
        float chance = k / (size * size) * Math.max(0.3f, Math.min(1.8f, 1 + (mine - theirs) / 60f));
        if (g.roll(g.state.tick, struck.id ^ 0x77494E44L) >= chance) return;
        float l = Math.max(1e-3f, (float) Math.hypot(dx, dz));
        if (struck instanceof rpg.animal.Animal an) { if (g.animals != null) g.animals.makeRagdoll(an, dx / l * 6, 2, dz / l * 6, true); return; }
        g.reactions.falling(struck, 1);
        if (struck.ragdoll != 0) g.game.commands().ragdollHit(struck.ragdoll, astratto.bridge.AstrattoNative.RD_PELVIS, dx / l, 0.25f, dz / l, 90 + 40 * s.tier.ordinal(), 30);
    }

    private void spellImpact(Projectile p,float x,float y,float z,Actor primary) {
        int element=switch(p.spell.element) {
            case FIRE -> astratto.bridge.AstrattoNative.CHEM_FIRE;
            case ICE -> astratto.bridge.AstrattoNative.CHEM_ICE;
            case AIR -> astratto.bridge.AstrattoNative.CHEM_WIND;
            case ELECTRIC -> astratto.bridge.AstrattoNative.CHEM_ELECTRIC;
            case WATER -> astratto.bridge.AstrattoNative.CHEM_WATER;
            default -> 0;
        };
        // the bursting fire of Blast and above catches those beside the target
        boolean burst = p.spell.element == Spells.Element.FIRE && p.spell.tier != null && p.spell.tier.ordinal() >= Spells.Tier.BLAST.ordinal();
        // on water the element spreads through the surface: frost skins it over, lightning runs out across it
        boolean onWater=g.vox.block((int)Math.floor(x),(int)Math.floor(y-0.6f),(int)Math.floor(z))==Blocks.WATER || g.vox.block((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z))==Blocks.WATER;
        float radius=burst?3:onWater && element==astratto.bridge.AstrattoNative.CHEM_ICE?2.4f:onWater && element==astratto.bridge.AstrattoNative.CHEM_ELECTRIC?1.8f:1.2f;
        if(element!=0)g.chemistry.emit(p.owner,element,x,onWater?(float)Math.floor(y-0.6f)+0.5f:y,z,radius,3,burst?1.2f:onWater?0.6f:0.3f);
        // lightning that strikes someone standing in water runs on down into it and out across the surface
        if(element==astratto.bridge.AstrattoNative.CHEM_ELECTRIC && !onWater && primary!=null && g.chemistry.onWater(primary.x,primary.y+0.2f,primary.z))
            g.chemistry.emit(p.owner,element,primary.x,primary.y+0.2f,primary.z,1.8f,3,0.6f);
        // (how it looks landing is the renderer's: SpellFx, by element and tier)
        if(burst) for(Actor other:g.actors()) {
            if(other==primary || other==p.owner || !other.alive || other.dummy || other.distTo(x,z)>3 || !g.vox.sight(x,y,z,other.x,other.chestY(),other.z))continue;
            float falloff=1-other.distTo(x,z)/4;
            resolve(p.owner,other,p.potential*falloff,p.impact*falloff,DamageType.MAGIC,p,other.x,other.chestY(),other.z,p.spell.title,false);
        }
    }

    private void step(Projectile p, float dt) {
        if (p.target!=0) {
            Actor target=g.actor(p.target);
            if(target!=null && target.alive && g.vox.sight(p.x,p.y,p.z,target.x,target.chestY(),target.z)) {
                float x=target.x-p.x,y=target.chestY()-p.y,z=target.z-p.z,len=(float)Math.sqrt(x*x+y*y+z*z);
                float blend=Math.min(1,dt*3), speed=p.spell.speed;
                if(len>0.1f) { p.vx+=(x/len*speed-p.vx)*blend;p.vy+=(y/len*speed-p.vy)*blend;p.vz+=(z/len*speed-p.vz)*blend; }
            } else p.target=0;
        }
        // (gamefixes: the arc shown was not where a throw went) the exact step of pos = start + v t - g t^2 / 2, the
        // guide's own formula: lowering the speed first and then moving by it dropped every shot a little more each frame
        // than the guide drew (a quarter of a metre over a long throw, more at a low frame rate)
        float ny = p.y + p.vy * dt - 0.5f * p.gravity * dt * dt;
        p.vy -= p.gravity * dt;
        float nx = p.x + p.vx * dt, nz = p.z + p.vz * dt;
        sweep(p, nx, ny, nz, dt);
    }

    private void sweep(Projectile p, float nx, float ny, float nz, float dt) {
        float sx = nx - p.x, sy = ny - p.y, sz = nz - p.z, len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-7f) return;
        // bodies first along the segment, then walls
        p.angle += p.spin * dt;
        // an elemental spell meets the surface of water (gamefixes: ice freezes it, fire thaws ice, lightning runs through it)
        boolean elemental = p.spell != null && (p.spell.element == Spells.Element.ICE || p.spell.element == Spells.Element.FIRE || p.spell.element == Spells.Element.ELECTRIC || p.spell.element == Spells.Element.WATER);
        Voxels.Hit h = g.vox.ray(p.x, p.y, p.z, sx, sy, sz, len, elemental);
        float wall = h.hit ? h.dist : len;
        Actor struck = null;
        float nearest = Float.POSITIVE_INFINITY;
        float inv = 1 / Math.max(1e-6f, len);
        // (gamefixes: wave spells hit nothing) a Wave is a crescent as wide as a person is tall: it strikes every body its
        // breadth passes over, not only one under its middle, and rolls on through them to the first wall
        if (p.spell != null && p.spell.tier == Spells.Tier.WAVE) { wave(p, sx * inv, sy * inv, sz * inv, wall); }
        else for (Actor v : projectileTargets) {
            boolean body = v instanceof rpg.ai.Npc npc && npc.ragdoll != 0 && !npc.alive;
            // (gamefixes: an archer's arrow phased through the people between it and the player) a friend in the way is
            // in the way: the shot stops in them, as a stray
            if (v == p.owner || !v.alive && !body || v instanceof rpg.ai.Npc npc && !npc.loaded) continue;
            float distance = HitVolumes.ray(g, v, p.x, p.y, p.z, sx * inv, sy * inv, sz * inv, wall, 0.03f);
            if (distance < nearest) { nearest = distance; struck = v; }
        }
        if (struck != null) {
            float qx = p.x + sx * inv * nearest, qy = p.y + sy * inv * nearest, qz = p.z + sz * inv * nearest;
            p.done = true;
            if (p.ammo != null) lodgeInBody(p, struck, qx, qy, qz, sx * inv, sy * inv, sz * inv);
            if (struck.alive && friendly(p.owner, struck)) {
                // a stray: it hurts, but it was no one's blow - no grudge, no crime, no fight started
                environmentalDamage(struck, Math.max(1, Math.round(p.potential * 0.5f * (float) damageRandom.nextDouble())), "a stray " + (p.spell != null ? p.spell.title : "arrow") + " from " + p.owner.name);
                if (p.spell != null) spellImpact(p, qx, qy, qz, struck);
                return;
            }
            if (struck.alive) {
                if ("web".equals(p.what)) { struck.cs.slowT = Math.max(struck.cs.slowT, 4); if (g.fx != null) g.fx.puff(qx, qy, qz); return; }
                spellEffect(p, struck, sx * inv, sz * inv);
                resolve(p.owner, struck, p.potential, p.impact, p.type, p, qx, qy, qz, p.spell != null ? p.spell.title : "arrow", false);
            } else if (g.sfx != null) g.sfx.play(p.ammo != null ? "arrow.flesh" : "hit", qx, qy, qz, 0.4f);
            if(p.spell!=null) spellImpact(p,qx,qy,qz,struck);
            return;
        }
        if (h.hit && Blocks.glass(h.block) && g.glass != null) {
            // through the pane: it shatters, and the arrow or the bolt flies on, slowed
            g.glass.shatter(h.bx, h.by, h.bz, h.x, h.y, h.z, sx, sy, sz, p.spell != null ? 1.6f : 0.9f, p.owner);
            p.vx *= 0.7f; p.vy *= 0.7f; p.vz *= 0.7f;
            p.x = nx; p.y = ny; p.z = nz;
            return;
        }
        if (h.hit) {
            p.done = true;
            p.x = h.x - sx / len * 0.15f; p.y = h.y - sy / len * 0.15f; p.z = h.z - sz / len * 0.15f;
            if (p.spell != null) {
                if (h.block == Blocks.WATER) { p.x = h.x; p.y = h.y + 0.5f; p.z = h.z; if (g.chemistry != null) g.chemistry.splash(h.x, h.y + 1f, h.z); }
                spellImpact(p,p.x,p.y,p.z,null); if (g.sfx != null) g.sfx.play("zap", p.x, p.y, p.z, 0.5f); return;
            }
            // a thrown thing glances off what it strikes (restitution 0.3) and comes to rest after a bounce or two
            if (p.ammo != null && p.ammo.def.family == Family.THROWN && p.bounces < 2 && !bites(p, h, sx / len, sy / len, sz / len, false)) {
                float speed = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
                if (speed > 3.5f) {
                    float dn = p.vx * h.nx + p.vy * h.ny + p.vz * h.nz;
                    p.vx = (p.vx - 2 * dn * h.nx) * 0.3f; p.vy = (p.vy - 2 * dn * h.ny) * 0.3f; p.vz = (p.vz - 2 * dn * h.nz) * 0.3f;
                    p.spin *= 0.5f; p.bounces++; p.done = false;
                    if (g.sfx != null) g.sfx.play(rpg.audio.Sfx.arrowOn(h.block), p.x, p.y, p.z, 0.4f);
                    return;
                }
            }
            if (h.block == Blocks.TARGET) { archeryTarget(p, h); lodgeInWorld(p, h, sx / len, sy / len, sz / len, true); return; }
            if (g.sfx != null) g.sfx.play(rpg.audio.Sfx.arrowOn(h.block), p.x, p.y, p.z, 0.5f);
            lodgeInWorld(p, h, sx / len, sy / len, sz / len, false);
            return;
        }
        p.x = nx; p.y = ny; p.z = nz;
    }

    private void wave(Projectile p, float dx, float dy, float dz, float wall) {
        if (p.swept == null) p.swept = new java.util.HashSet<>();
        for (Actor v : projectileTargets) {
            if (v == p.owner || !v.alive || v instanceof rpg.ai.Npc npc && !npc.loaded || p.swept.contains(v.id)) continue;
            if (v.team != 0 && v.team == p.owner.team && !hostileTo(p.owner, v)) continue;
            float distance = HitVolumes.ray(g, v, p.x, p.y, p.z, dx, dy, dz, wall, 0.7f);
            if (distance >= wall) continue;
            p.swept.add(v.id);
            float qx = p.x + dx * distance, qy = p.y + dy * distance, qz = p.z + dz * distance;
            spellEffect(p, v, dx, dz);
            resolve(p.owner, v, p.potential, p.impact, p.type, p, qx, qy, qz, p.spell.title, false);
            spellImpact(p, qx, qy, qz, v);
        }
    }

    /** An arrow in the archery target: Archery XP growing with the distance shot from (the arrow stays in the target). */
    private void archeryTarget(Projectile p, Voxels.Hit h) {
        float dist = (float) Math.hypot(p.owner.x - h.x, p.owner.z - h.z);
        // how far from the bull: measured in the face of the target the arrow struck (its middle, not the cell's)
        rpg.objects.Furniture t = g.objects.furnitureAt(h.bx, h.by, h.bz);
        float cy = t != null ? rpg.world.DevMap.FLOOR + t.h * 0.5f : h.by + 1f;
        float off = t == null ? (float) Math.hypot(h.y - cy, h.z - Math.round(h.z))
                : t.facing % 2 == 1 ? (float) Math.hypot(h.y - cy, h.z - t.z) : (float) Math.hypot(h.y - cy, h.x - t.x);
        int ring = off < 0.25f ? 3 : off < 0.6f ? 2 : 1;
        var ev = g.state.commit("TargetHit", p.owner.id, 0, null, 0, String.format("distance %.1f m, ring %d", dist, ring));
        g.prog.award(p.owner, Skill.ARCHERY, (4 + dist * 0.6f) * ring, "archery target", ev.id());
        if (p.owner == g.player) g.chat.say(String.format("You hit the target %s from %.0f m.", ring == 3 ? "dead centre" : ring == 2 ? "near the centre" : "on its edge", dist));
        if (g.fx != null) g.fx.text(ring == 3 ? "Bullseye!" : "Hit", h.x, h.y + 0.4f, h.z, 0xFFFFE070);
        if (g.sfx != null) g.sfx.play("wood.light", h.x, h.y, h.z, 0.6f);
    }

    /** End a pursuit without deleting its ammunition or allowing an old shot to hit after legal settlement. */
    public void landProjectiles(java.util.function.Predicate<Actor> owners) {
        for (Iterator<Projectile> shots = projectiles.iterator(); shots.hasNext();) {
            Projectile shot = shots.next();
            if (owners.test(shot.owner)) { dropArrow(shot); shots.remove(); }
        }
    }

    /** Every projectile in flight comes down where it is now (before a save: flights are not kept). */
    public void landAll() {
        for (Projectile p : projectiles) dropArrow(p);
        projectiles.clear();
    }

    /** A shot that never struck anything (its flight cut short): it falls where it is, as a loose thing tumbling down. */
    private void dropArrow(Projectile p) {
        releasePhysical(p);
        if (p.ammo == null) return;
        float l = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
        float[] dir = l < 1e-3f ? new float[]{0, -1, 0} : new float[]{p.vx / l, p.vy / l, p.vz / l};
        g.objects.dropAmmo(p.ammo, p.x, Math.max(p.y, g.vox.groundY(p.x, p.y, p.z) + 0.08f), p.z, dir, false, p.vx * 0.2f, p.vy * 0.2f, p.vz * 0.2f);
        p.ammo = null;
    }

    /**
     * A shot meets the world (gamefixes: arrows are real objects, each lying exactly where it ended up). Driven in hard
     * enough and square enough to soft stuff - wood, earth, straw, a target - it lodges there, its point buried a little,
     * and stays until taken. Off stone, or at a glancing angle, or spent, it skips off: a loose rigid body that tumbles and
     * settles. Now and then one breaks on stone.
     */
    private void lodgeInWorld(Projectile p, Voxels.Hit h, float dx, float dy, float dz, boolean target) {
        if (p.ammo == null) return;
        boolean bite = bites(p, h, dx, dy, dz, target);
        ItemStack one = p.ammo; p.ammo = null;
        float speed = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
        String surface = rpg.audio.Sfx.surface(h.block);
        boolean soft = target || !surface.equals("stone");
        float len = ammoLength(one.def);
        if (bite) {
            float bury = Math.min(0.22f, 0.05f + speed * 0.003f) * (surface.equals("wood") ? 0.8f : 1f) * Math.min(len, 0.85f) / 0.85f;
            float tipX = h.x + dx * bury, tipY = h.y + dy * bury, tipZ = h.z + dz * bury;
            g.objects.dropAmmo(one, tipX - dx * len / 2, tipY - dy * len / 2, tipZ - dz * len / 2, new float[]{dx, dy, dz}, true, 0, 0, 0);
            return;
        }
        if (!soft && one.def.family != Family.THROWN && speed > 20 && g.roll(p.id, h.bx * 31 + h.bz) < 0.2f) {
            g.state.burn(one.def, 1);
            if (g.fx != null) g.fx.sparks(h.x, h.y, h.z, 4);
            return;
        }
        // skipping off: the bounce takes a little of the speed and the spin of it
        float dn = p.vx * h.nx + p.vy * h.ny + p.vz * h.nz;
        float bx = (p.vx - 2 * dn * h.nx) * 0.12f, by = (p.vy - 2 * dn * h.ny) * 0.12f + 0.6f, bz = (p.vz - 2 * dn * h.nz) * 0.12f;
        g.objects.dropAmmo(one, h.x + h.nx * 0.06f, h.y + h.ny * 0.06f + 0.02f, h.z + h.nz * 0.06f, new float[]{dx, dy, dz}, false, bx, by, bz);
    }

    /** Whether a shot drives into what it struck: a point (or a tomahawk's edge into wood), into something soft, hard and square enough. */
    private static boolean bites(Projectile p, Voxels.Hit h, float dx, float dy, float dz, boolean target) {
        if (p.ammo == null || !lodges(p.ammo.def)) return false;
        String surface = rpg.audio.Sfx.surface(h.block);
        if (p.ammo.def.family == Family.THROWN && p.ammo.def.thrown.equals("tomahawk") && !surface.equals("wood")) return false;
        float speed = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz), square = -(dx * h.nx + dy * h.ny + dz * h.nz);
        // earth and turf take a point even at a low angle; wood wants it squarer
        float needs = surface.equals("earth") || surface.equals("grass") ? 0.1f : 0.3f;
        return (target || !surface.equals("stone")) && speed > 9 && square > needs;
    }

    /** A shot lodges in whatever body it struck (a living one carries it; a corpse holds it where it went in). One in five breaks. */
    private void lodgeInBody(Projectile p, Actor v, float x, float y, float z, float dx, float dy, float dz) {
        ItemStack one = p.ammo; p.ammo = null;
        if (!lodges(one.def) || g.roll(p.id, v.id) < 0.2f) { g.state.burn(one.def, 1); return; }
        float len = ammoLength(one.def), bury = Math.min(0.12f, len * 0.15f);
        if (!v.alive) {
            g.objects.dropAmmo(one, x + dx * (bury - len / 2), y + dy * (bury - len / 2), z + dz * (bury - len / 2), new float[]{dx, dy, dz}, true, 0, 0, 0);
            return;
        }
        int[] anchors = HitVolumes.attachment(g, v, x, y, z);
        float[] f = HitVolumes.attachmentFrame(g, v, anchors[0], anchors[1]);
        float ox=x+dx*bury-f[0], oy=y+dy*bury-f[1], oz=z+dz*bury-f[2];
        v.stuck.add(new Stuck(one, ox*f[3]+oy*f[4]+oz*f[5], ox*f[6]+oy*f[7]+oz*f[8], ox*f[9]+oy*f[10]+oz*f[11],
                dx*f[3]+dy*f[4]+dz*f[5], dx*f[6]+dy*f[7]+dz*f[8], dx*f[9]+dy*f[10]+dz*f[11], g.time, anchors[0], anchors[1]));
    }

    /** Lodged things fall out of a body: all of them (it died, or is gone), or those that have worked loose by now. */
    public void shedLodged(Actor v, double olderThan) {
        for (java.util.Iterator<Stuck> it = v.stuck.iterator(); it.hasNext(); ) {
            Stuck s = it.next();
            if (olderThan != Integer.MAX_VALUE && g.time - s.at() < olderThan) continue;
            it.remove();
            float[] w = s.world(g, v);
            float len = ammoLength(s.stack().def);
            g.objects.dropAmmo(s.stack(), w[0] - w[3] * len / 2, Math.max(w[1] - w[4] * len / 2, v.y + 0.1f), w[2] - w[5] * len / 2, new float[]{w[3], w[4], w[5]}, false, v.vx, 0.5f, v.vz);
        }
    }

    /** A creature's own blow (a bite, a claw, a tail, a breath): the same contact, guarding and damage rules a weapon meets. */
    public void creatureBlow(Actor a, Actor v, float damage, float impact, DamageType type, String what) {
        float potential = type == DamageType.MAGIC ? damage * CombatMath.HP_SCALE : physicalMax(a, Skill.STRENGTH, damage);
        resolve(a, v, potential, CombatMath.impact(impact, a.skills.level(Skill.STRENGTH), 1), type, null, v.x, v.chestY(), v.z, what, behind(v, a));
    }

    /**
     * Something hurled at a point (a monster's throw, a troll's boulder, a spider's web): it flies the arc a throw flies,
     * from the hand, and strikes by the same contact rules. A thrown weapon lands, to be picked up; a boulder or a web is
     * spent where it strikes.
     */
    public Projectile hurl(Actor a, ItemStack one, String what, float tx, float ty, float tz, float speed, float gravity, float potential, float impact, DamageType type) {
        float hx = g.time - a.handAt < 0.25 ? a.hand[0] : a.x, hy = g.time - a.handAt < 0.25 ? a.hand[1] : a.eyeY() - 0.2f, hz = g.time - a.handAt < 0.25 ? a.hand[2] : a.z;
        float dx = tx - hx, dy = ty - hy, dz = tz - hz, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float t = Math.max(0.15f, d / speed);
        Projectile p = new Projectile(); p.id = ++projectileSerial; p.owner = a;
        p.x = hx; p.y = hy; p.z = hz; p.vx = dx / t; p.vy = (dy + 0.5f * gravity * t * t) / t; p.vz = dz / t; p.gravity = gravity; p.life = 6;
        p.ammo = one; p.what = what; p.type = type; p.skill = Skill.ARCHERY;
        p.potential = Math.max(1, potential * CombatMath.HP_SCALE); p.impact = impact;
        p.spin = what != null && what.equals("boulder") ? 4 : 10;
        projectiles.add(p);
        g.noise(a, a.x, a.y, a.z, 8, 0.3f);
        if (g.sfx != null) g.sfx.play("swing", a.x, a.eyeY(), a.z, 0.5f);
        return p;
    }

    public void log(String line) {
        telemetry.addLast(line);
        while (telemetry.size() > 14) telemetry.removeFirst();
    }
}
