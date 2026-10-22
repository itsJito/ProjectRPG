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
 * (potential x (1 - mitigation)) and impact (against Stability, for stagger) resolve on separate channels. Guards,
 * parries and dodges are timing and position, not chance. Combat XP is paid from the damage actually dealt.
 */
public final class CombatSystem {
    private final Rpg g;
    public final List<Projectile> projectiles = new ArrayList<>();
    /** Combat telemetry (GDD 4.2): the last contacts with every number that went into them. */
    public final ArrayDeque<String> telemetry = new ArrayDeque<>();
    public long contacts, blocked, parried, dodged, staggers, kills, deflections, shots, casts, blessings;

    public CombatSystem(Rpg g) { this.g = g; }

    public static final class Projectile {
        public Actor owner;
        public float x, y, z, vx, vy, vz, gravity, life, potential, impact;
        public DamageType type;
        public Skill skill;
        public ItemStack ammo;
        public Spell spell;
        public boolean done;
    }

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
        c.windup = tm[0] * k * cadence; c.active = tm[1] * k; c.recover = tm[2] * k * cadence;
        if (a instanceof rpg.animal.Animal an) {
            // an animal's strike: a crouch and a lunge (a bear's swipe and a horse's kick are slower to come)
            float heavy = an.species.impact > 50 ? 1.35f : 1f;
            c.windup = 0.32f * heavy; c.active = 0.14f * heavy; c.recover = 0.42f * heavy;
        }
        c.lastAttack = g.time;
        g.interact.cancel(a, "attacking");
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

    public void setBlock(Actor a, boolean on) {
        boolean can = on && a.alive && a.cs.phase == Phase.IDLE && a.cs.staggered <= 0 && a.cs.dodgeT <= 0;
        if (can && !a.cs.blocking) a.cs.blockStart = g.time;
        a.cs.blocking = can;
    }

    /** A dodge: a quick burst of movement with a moment of invulnerability. */
    public boolean dodge(Actor a, float dx, float dz) {
        if (!a.alive || a.cs.staggered > 0 || a.cs.dodgeT > 0 || a.cs.phase == Phase.ACTIVE) return false;
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
    public boolean throwOne(Actor a, float yaw, float pitch, float wind) {
        ItemStack held = a.eq.get(ItemDef.Slot.MAIN);
        if (held == null || held.def.family != Family.THROWN) return false;
        if (a.skills.level(Skill.ARCHERY) < held.def.reqLevel) { if (a == g.player) g.chat.add("You need Archery level " + held.def.reqLevel + " to throw those.", Chat.Kind.WARN); return false; }
        if (a.cs.castCooldown > 0) return false;
        wind = Math.max(0.4f, Math.min(1, wind));
        ItemStack one = held.copy(1);
        held.amount--;
        if (held.amount <= 0) a.eq.set(ItemDef.Slot.MAIN, null);
        boolean heavy = held.def.thrown.equals("javelin") || held.def.thrown.equals("tomahawk");
        Projectile p = launch(a, yaw, pitch, (heavy ? 17 : 22) + (heavy ? 12 : 14) * wind, heavy ? 9.8f : 7.5f);
        p.ammo = one; p.type = held.def.damageType; p.skill = Skill.ARCHERY;
        p.potential = held.def.damage * CombatMath.archeryScaling(a.skills.level(Skill.ARCHERY)) * (0.6f + 0.4f * wind);
        p.impact = held.def.impact * wind;
        a.cs.castCooldown = 0.32f * held.def.speed;
        shots++;
        if (g.sfx != null) g.sfx.play("swing", a.x, a.eyeY(), a.z, 0.5f);
        g.state.commit("Thrown", a.id, 0, null, 0, String.format("%s potential=%.1f", held.def.name, p.potential));
        return true;
    }

    /** Looses a bolt from a loaded crossbow along an aim; the crossbow is loaded again over a second or so (Agility quickens it). */
    public boolean shootCrossbow(Actor a, float yaw, float pitch) {
        ItemDef xb = a.main();
        if (xb == null || xb.family != Family.CROSSBOW) return false;
        if (a.cs.reload > 0) return false;
        ItemStack ammo = a.eq.get(ItemDef.Slot.AMMO);
        if (ammo == null || ammo.def.family != Family.BOLT) { if (a == g.player) g.chat.add("You have no bolts equipped.", Chat.Kind.WARN); return false; }
        if (a.skills.level(Skill.ARCHERY) < xb.reqLevel) { if (a == g.player) g.chat.add("You need Archery level " + xb.reqLevel + " to use that crossbow.", Chat.Kind.WARN); return false; }
        ItemStack one = ammo.copy(1);
        ammo.amount--;
        if (ammo.amount <= 0) a.eq.set(ItemDef.Slot.AMMO, null);
        Projectile p = launch(a, yaw, pitch, 58, 6f);
        p.ammo = one; p.type = DamageType.PIERCE; p.skill = Skill.ARCHERY;
        p.potential = (xb.damage + ammo.def.damage) * CombatMath.archeryScaling(a.skills.level(Skill.ARCHERY));
        p.impact = xb.impact;
        a.cs.reload = 1.25f * (1 - Math.min(0.3f, a.skills.level(Skill.AGILITY) * 0.003f));
        shots++;
        if (g.sfx != null) g.sfx.play("bow", a.x, a.eyeY(), a.z, 0.9f, 0.7f, 30);
        g.state.commit("BoltLoosed", a.id, 0, null, 0, String.format("%s potential=%.1f", ammo.def.name, p.potential));
        return true;
    }

    /** Looses an arrow from an equipped bow along an aim, drawn this far (0..1). */
    public boolean shoot(Actor a, float yaw, float pitch, float draw) {
        ItemDef bow = a.main();
        if (bow == null || bow.family != Family.BOW) return false;
        ItemStack ammo = a.eq.get(ItemDef.Slot.AMMO);
        if (ammo == null || ammo.def.family != Family.ARROW) { if (a == g.player) g.chat.add("You have no arrows equipped.", Chat.Kind.WARN); return false; }
        if (a.skills.level(Skill.ARCHERY) < ammo.def.reqLevel) { if (a == g.player) g.chat.add("You can't use " + ammo.def.name + " yet.", Chat.Kind.WARN); return false; }
        draw = Math.max(0.35f, Math.min(1, draw));
        ItemStack one = ammo.copy(1);
        ammo.amount--;
        if (ammo.amount <= 0) a.eq.set(ItemDef.Slot.AMMO, null);
        Projectile p = launch(a, yaw, pitch, 18 + 34 * draw, 9.8f);
        p.ammo = one; p.type = DamageType.PIERCE; p.skill = Skill.ARCHERY;
        p.potential = (bow.damage + ammo.def.damage) * CombatMath.archeryScaling(a.skills.level(Skill.ARCHERY)) * draw;
        p.impact = bow.impact * draw;
        shots++;
        if (g.sfx != null) g.sfx.play("bow", a.x, a.eyeY(), a.z, 0.7f);
        g.state.commit("ArrowLoosed", a.id, 0, null, 0, String.format("%s draw=%.2f potential=%.1f", ammo.def.name, draw, p.potential));
        return true;
    }

    public String castRefusal(Actor a, Spell s) {
        if (a.skills.level(Skill.MAGIC) < s.level) return "You need Magic level " + s.level + " to cast " + s.title + ".";
        if (a instanceof rpg.actor.Player p && p.book != s.book) return s.title + " is in the " + s.book.title + " spellbook; yours is " + p.book.title + ". Change it at an altar.";
        if (s == Spell.FIRE_BLAST) {
            ItemStack staff = a.eq.get(ItemDef.Slot.MAIN);
            if (staff == null || !staff.def.id.equals("fire_staff")) return "Fire Blast needs the Fire staff in your hand.";
            if (staff.charges <= 0) return "The Fire staff has no charges left. Recharge it with coal.";
        }
        if (a.mana < s.cost) return "You don't have enough mana.";
        if (a.cs.castCooldown > 0 || a.cs.busy()) return "";
        return null;
    }

    public boolean cast(Actor a, Spell s, float yaw, float pitch) {
        String no = castRefusal(a, s);
        if (no != null) { if (a == g.player && !no.isEmpty()) g.chat.add(no, Chat.Kind.WARN); return false; }
        if (s == Spell.TELEGRAB) return telegrab(a, s, yaw, pitch);
        if (s.utility) {
            if (!g.magic.cast(a, s, yaw, pitch)) return false;
            a.mana -= s.cost;
            a.cs.castCooldown = 0.7f;
            casts++;
            var uev = g.state.commit("SpellCast", a.id, 0, null, 0, s.title);
            g.prog.award(a, Skill.MAGIC, s.xp, "cast " + s.title, uev.id());
            if (g.sfx != null) g.sfx.play("cast", a.x, a.eyeY(), a.z, 0.6f);
            if (g.fx != null) g.fx.ring(a.x, a.y, a.z, 1.0f, 2f, 2f, 3.5f);
            return true;
        }
        if (s == Spell.FIRE_BLAST) a.eq.get(ItemDef.Slot.MAIN).charges--;
        a.mana -= s.cost;
        a.cs.castCooldown = 0.7f;
        ItemDef staff = a.main() != null && a.main().family == Family.STAFF ? a.main() : a.off() != null && a.off().family == Family.WAND ? a.off() : null;
        Projectile p = launch(a, yaw, pitch, s.speed, 0);
        p.spell = s; p.type = DamageType.MAGIC; p.skill = Skill.MAGIC;
        p.potential = Spells.groupBase(s, a.skills.level(Skill.MAGIC)) * CombatMath.HP_SCALE * CombatMath.magicScaling(a.skills.level(Skill.MAGIC)) * (staff == null ? 1f : 1f + staff.damage / CombatMath.HP_SCALE * 0.04f);
        p.impact = 12 + s.base * 0.6f;
        casts++;
        var ev = g.state.commit("SpellCast", a.id, 0, null, 0, s.title);
        g.prog.award(a, Skill.MAGIC, s.xp, "cast " + s.title, ev.id());
        if (g.sfx != null) g.sfx.play("cast", a.x, a.eyeY(), a.z, 0.6f);
        return true;
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
        Projectile p = new Projectile();
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
        for (Actor a : g.actors()) tick(a, dt);
        updateProjectiles(dt);
    }

    private void tick(Actor a, float dt) {
        Actor.Combat c = a.cs;
        if (!a.alive) return;
        c.flash = Math.max(0, c.flash - dt * 3);
        c.iframes = Math.max(0, c.iframes - dt);
        c.dodgeT = Math.max(0, c.dodgeT - dt);
        c.castCooldown = Math.max(0, c.castCooldown - dt);
        if (c.reload > 0) { c.reload = Math.max(0, c.reload - dt); if (c.reload == 0 && a == g.player && g.sfx != null) g.sfx.play("lock", a.x, a.eyeY(), a.z, 0.4f); }
        c.slowT = Math.max(0, c.slowT - dt);
        c.stabilityBonus = Math.max(0, c.stabilityBonus - dt * 8f);
        if (c.staggered > 0) { c.staggered -= dt; c.blocking = false; }
        // resources: stamina (not while guarding or just after spending), Faith points and mana regenerate naturally
        c.regenDelay -= dt;
        if (c.regenDelay <= 0 && !c.blocking) a.stamina = Math.min(a.maxStamina(), a.stamina + g.cfg.staminaRegen * (1 + a.skills.level(Skill.AGILITY) * 0.006f) * dt);
        // Faith does not refill by itself (GDD 2.5); Mana regains a point a minute, the part-minute kept
        if (a.mana < a.maxMana()) { a.manaAcc += dt; while (a.manaAcc >= 60 && a.mana < a.maxMana()) { a.manaAcc -= 60; a.mana = Math.min(a.maxMana(), a.mana + 1); } }
        else a.manaAcc = 0;
        if (g.time - c.lastHurt > 8 && !a.dummy) a.hp = Math.min(a.maxHp(), a.hp + a.maxHp() * 0.004f * dt);

        if (c.hitstop > 0) { c.hitstop -= dt; return; }
        if (c.phase == Phase.IDLE) return;
        c.t += dt;
        switch (c.phase) {
            case WINDUP -> { if (c.t >= c.windup) { c.phase = Phase.ACTIVE; c.t = 0; c.sweep = 0; if (g.sfx != null) g.sfx.play("swing", a.x, a.y + 1.3f, a.z, 0.5f); g.noise(a, a.x, a.y, a.z, 6, 0.3f); } }
            case ACTIVE -> {
                float to = Math.min(1, c.t / c.active);
                sweep(a, c.sweep, to);
                c.sweep = to;
                if (c.phase == Phase.ACTIVE && c.t >= c.active) { c.phase = Phase.RECOVER; c.t = 0; }
            }
            case RECOVER -> { if (c.t >= c.recover) c.cancel(); }
            default -> { }
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
        float px = rpg.actor.Body.PX;
        return new float[]{a.x + (rx * tx + bx * tz) * px, a.y + (b.hipY() + ly) * px, a.z + (rz * tx + bz * tz) * px};
    }

    /** A path point (hand, blade) in the actor's frame to the world: mirrored for the off hand, tipped by the aim's pitch. */
    private static float[] toWorld(Actor a, float[] q, boolean offHand) {
        float side = offHand ? -1 : 1;
        float hx = q[0] * side, hy = q[1], hz = q[2], dx = q[3] * side, dy = q[4], dz = q[5];
        // the aim's pitch tips the whole stroke about the shoulders' line
        float p = a.pitch * 0.7f, cp = (float) Math.cos(p), sp = (float) Math.sin(p);
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
        float dist = 0.06f + u * reach;
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
        Move m = a.cs.move;
        float reach = reach(a);
        int steps = 6;
        for (int i = 1; i <= steps; i++) {
            float s = from + (to - from) * i / steps;
            // the blade meets a wall first: deflected
            float[] tip = blade(a, m, s, 1, reach);
            if (!g.vox.clear(tip[3], tip[4], tip[5], tip[0], tip[1], tip[2])) {
                Voxels.Hit h = g.vox.ray(tip[3], tip[4], tip[5], tip[0] - tip[3], tip[1] - tip[4], tip[2] - tip[5], reach + 0.5f, false);
                // a tool meeting what it works: an axe biting a tree (trunk or crown), a pickaxe striking the ore -
                // once a swing; the head bites and stops in wood or stone (in leaves it carries on through)
                if (h.hit && !a.cs.gathered && g.objects != null) {
                    rpg.objects.Node nd = g.objects.nodeAt(h.bx, h.by, h.bz);
                    if (nd != null && nd.strike(g, a, h.x, h.y, h.z)) {
                        a.cs.gathered = true;
                        boolean leafy = h.block == Blocks.LEAVES || h.block == Blocks.OAK_LEAVES || h.block == Blocks.WILLOW_LEAVES;
                        if (!leafy) { a.cs.hitstop = 0.06f; a.cs.phase = Phase.RECOVER; a.cs.t = 0; return; }
                    }
                }
                if (h.hit && h.block == Blocks.GLASS && g.glass != null) {
                    // glass does not turn a blade: it breaks, and the blow carries on through
                    float[] d0 = blade(a, m, Math.max(0, s - 0.1f), 1, reach);
                    g.glass.shatter(h.bx, h.by, h.bz, h.x, h.y, h.z, tip[0] - d0[0], tip[1] - d0[1], tip[2] - d0[2], m == Move.HEAVY ? 1.4f : 1f, a);
                    log(String.format("%s's %s broke the pane at (%d, %d, %d)", a.name, m, h.bx, h.by, h.bz));
                } else if (h.hit && h.block != Blocks.LEAVES && h.block != Blocks.OAK_LEAVES && h.block != Blocks.WILLOW_LEAVES) {
                    deflections++;
                    if (g.fx != null) g.fx.sparks(h.x, h.y, h.z, 10);
                    if (g.sfx != null) g.sfx.play("block", h.x, h.y, h.z, 0.5f);
                    a.cs.hitstop = 0.08f;
                    a.cs.phase = Phase.RECOVER; a.cs.t = 0;
                    log(String.format("%s's %s deflected by block %d at (%d, %d, %d), s=%.2f", a.name, m, h.block, h.bx, h.by, h.bz, s));
                    return;
                }
            }
            for (int k = 0; k <= 3; k++) {
                float[] p = blade(a, m, s, k / 3f, reach);
                for (Actor v : g.actors()) {
                    if (v == a || !v.alive || a.cs.struck.contains(v.id) || (v.team != 0 && v.team == a.team && !hostileTo(a, v))) continue;
                    if (!inCapsule(v, p[0], p[1], p[2], 0.06f)) continue;
                    a.cs.struck.add(v.id);
                    meleeContact(a, v, m, p[0], p[1], p[2]);
                    if (a.cs.phase != Phase.ACTIVE) return;
                }
            }
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
                float potential = sp.damage * CombatMath.HP_SCALE * an.scale() * CombatMath.attackScaling(an.skills.level(Skill.ATTACK)) * CombatMath.execution(v.cs.staggered > 0, behind);
                float impact = CombatMath.impact(sp.impact * an.scale(), an.skills.level(Skill.STRENGTH), 1);
                resolve(an, v, potential, impact, sp.damageType, null, px, hy, pz, sp.weapon, behind);
                if (an.cs.phase != Phase.ACTIVE) return;
            }
        }
    }

    /** Whether two actors on one team may still fight (a guard arresting a resisting townsperson). */
    private boolean hostileTo(Actor a, Actor v) { return g.ai != null && g.ai.hostile(a, v); }

    public static boolean inCapsule(Actor v, float px, float py, float pz, float pad) {
        float dx = px - v.x, dz = pz - v.z, r = v.radius + pad;
        if (dx * dx + dz * dz > r * r) return false;
        return py >= v.y + 0.05f - pad && py <= v.y + v.height + pad;
    }

    private void meleeContact(Actor a, Actor v, Move m, float px, float py, float pz) {
        ItemDef w = a.cs.offHand ? a.off() : a.main();
        boolean weapon = w != null && m != Move.PUNCH && m != Move.BASH;
        float base = weapon ? w.damage : (m == Move.BASH && w != null ? 2 + w.armor : 6) * CombatMath.HP_SCALE;
        float impactBase = weapon ? w.impact : m == Move.BASH ? 26 : 6;
        DamageType type = weapon ? w.damageType : DamageType.BLUNT;
        boolean behind = behind(v, a);
        // Strength makes the damage of a landed blow (with the weapon's Strength bonus); Attack does not (GDD 2.3)
        float potential = base * CombatMath.strengthScaling(a.skills.level(Skill.STRENGTH), weapon ? w.strengthBonus : 0) * CombatMath.moveDamage(m) * CombatMath.execution(v.cs.staggered > 0, behind);
        potential *= g.prayers.strength(a);
        float impact = CombatMath.impact(impactBase, a.skills.level(Skill.STRENGTH), CombatMath.moveImpact(m));
        resolve(a, v, potential, impact, type, null, px, py, pz, String.valueOf(m), behind);
    }

    private static boolean behind(Actor v, Actor a) {
        float fx = (float) -Math.sin(v.yaw), fz = (float) -Math.cos(v.yaw), dx = a.x - v.x, dz = a.z - v.z, l = (float) Math.hypot(dx, dz);
        return l > 1e-3f && (fx * dx + fz * dz) / l < -0.5f;
    }

    /** Contact made: guard and parry, mitigation, HP damage, impact and stagger, feedback, XP, events. */
    public void resolve(Actor a, Actor v, float potential, float impact, DamageType type, Projectile proj, float px, float py, float pz, String how, boolean behind) {
        contacts++;
        if (v.cs.iframes > 0 || v.gaseousT > 0 && proj == null) {
            dodged++;
            if (g.fx != null) g.fx.text("Dodged", v.x, v.y + v.height + 0.3f, v.z, 0xFFB0E0FF);
            log(String.format("%s -> %s %s: DODGED (i-frames)", a.name, v.name, how));
            return;
        }
        boolean melee = proj == null;
        ItemDef guardItem = v.off() != null && v.off().family == Family.SHIELD ? v.off() : melee && v.main() != null && v.main().guard > 0 ? v.main() : null;
        float fx = (float) -Math.sin(v.yaw), fz = (float) -Math.cos(v.yaw), tx = a.x - v.x, tz = a.z - v.z, tl = Math.max(1e-3f, (float) Math.hypot(tx, tz));
        boolean facing = (fx * tx + fz * tz) / tl > 0.35f;
        boolean guarding = v.cs.blocking && facing && guardItem != null && type != DamageType.MAGIC;
        g.noise(a, v.x, v.y, v.z, g.cfg.combatNoise, 0.8f);
        g.ai.onAttacked(v, a);
        if (v instanceof rpg.animal.Animal an && g.animals != null) g.animals.onHurt(an, a);
        if (guarding && melee && g.time - v.cs.blockStart <= CombatMath.parryWindow(g.cfg.parryWindow, v.skills.level(Skill.DEFENSE))) {
            parried++;
            a.cs.cancel();
            a.cs.staggered = 0.9f;
            a.cs.hitstop = 0.1f;
            if (g.fx != null) { g.fx.sparks(px, py, pz, 24); g.fx.text("Parry!", v.x, v.y + v.height + 0.3f, v.z, 0xFFFFE070); }
            if (g.sfx != null) g.sfx.play("parry", px, py, pz, 0.9f);
            var ev = g.state.commit("Parry", v.id, a.id, null, 0, how);
            g.prog.award(v, Skill.DEFENSE, 6, "parry", ev.id());
            log(String.format("%s -> %s %s: PARRIED within %.2fs; attacker staggered", a.name, v.name, how, g.time - v.cs.blockStart));
            return;
        }
        float protection = CombatMath.protection(v.skills.level(Skill.DEFENSE), v.eq.armor());
        float mit = CombatMath.mitigation(protection, type);
        float dmg = CombatMath.finalDamage(potential, mit);
        dmg *= g.prayers.ward(v);
        // an elemental spell on a creature with that weakness on record: double (GDD 2.5)
        if (proj != null && proj.spell != null && v instanceof rpg.animal.Animal wan && wan.species.weakness == proj.spell.element && proj.spell.element != Spells.Element.NONE) dmg *= 2;
        if (proj != null && proj.skill == Skill.ARCHERY) dmg *= g.prayers.archery(a);
        // the overhead protections: the attack of their kind does no harm at all - a visible counter, not armour
        boolean magicHit = proj != null && proj.spell != null;
        boolean prayed = g.prayers.protects(v, melee, magicHit) && dmg > 0;
        if (prayed) { if (g.fx != null) g.fx.text("Protected", v.x, v.y + v.height + 0.5f, v.z, 0xFFE8F0FF); g.prayers.prevented(v, dmg, 0); dmg = 0; impact *= 0.3f; }
        float blockedPart = 0;
        if (guarding) {
            // the guard stops its share - a better defender behind better armour stops more, and tires less doing it
            float share = Math.min(0.97f, CombatMath.guardShare(guardItem.guard, v.skills.level(Skill.DEFENSE), v.eq.armor()) * g.prayers.guard(v));
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
            if (g.sfx != null) g.sfx.play("block", px, py, pz, 0.7f);
            if (g.fx != null) g.fx.sparks(px, py, pz, 12);
        }
        // whole HP, rounded; a blow of a quarter point and more always draws at least one (a fox's bite on the HP scale
        // is a third of a point: rounded away, no small animal could ever hurt anything)
        int dealt = Math.round(dmg);
        if (dealt == 0 && dmg >= 0.25f) dealt = 1;
        if (!v.dummy) dealt = Math.min(dealt, (int) Math.ceil(v.hp));
        // the console's god (nothing hurts) and buddha (hurt, never below 1 HP)
        if (v == g.player && g.ui != null && g.ui.console.god) { dealt = 0; impact = 0; }
        if (v == g.player && g.ui != null && g.ui.console.immortal) dealt = Math.min(dealt, Math.max(0, (int) Math.ceil(v.hp) - 1));
        v.hp -= dealt;
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
        a.cs.hitstop = v.cs.hitstop = dealt > 0 ? 0.07f : 0.03f;
        if (v == g.player && g.fx != null) g.fx.shake = Math.min(1, g.fx.shake + 0.25f + dealt * 0.01f);
        if (a == g.player && g.fx != null) g.fx.shake = Math.min(1, g.fx.shake + 0.08f);
        if (g.fx != null) {
            if (dealt > 0) g.fx.blood(px, py, pz, -tx / tl, -tz / tl);
            g.fx.text(dealt > 0 ? String.valueOf(dealt) : guarding ? "Blocked" : "0", v.x, v.y + v.height + 0.2f, v.z, dealt > 0 ? (v == g.player ? 0xFFFF4040 : 0xFFFFFFFF) : 0xFF80C0FF);
            if (stagger) g.fx.text("Staggered", v.x, v.y + v.height + 0.6f, v.z, 0xFFFFC040);
        }
        if (g.sfx != null && dealt > 0) g.sfx.play(potential > 30 * CombatMath.HP_SCALE ? "heavyhit" : "hit", px, py, pz, 0.8f);
        if (a == g.player && v instanceof rpg.ai.Npc sp && sp.spareSince > 0) sp.spareSince = -1;
        if (g.sfx != null && dealt > 0 && v.hp > 0 && !v.dummy && !(v instanceof rpg.animal.Animal) && dealt >= v.maxHp() * 0.08f) g.sfx.hurtCry(v, g.time);
        String detail = String.format("%s -> %s %s%s: potential %.1f, protection %.0f (%.0f%% mit %s)%s%s, dealt %d; impact %.1f vs stability %.1f%s; stamina %.0f",
                a.name, v.name, how, behind ? " (from behind)" : "", potential, protection, mit * 100, type,
                g.prayers.on(v, Prayer.WARD) ? ", ward" : "", guarding ? String.format(", guarded %.1f", blockedPart) : "", dealt, impact, stab, stagger ? " -> STAGGER" : "", v.stamina);
        var ev = g.state.commit("ActorHit", a.id, v.id, rpg.world.DevMap.jurisdiction(v.x, v.z), 0, detail);
        log(detail);
        // XP from the damage actually dealt (and Defense from the damage guarded)
        if (dealt > 0 && a != v) {
            Skill primary = proj != null ? proj.skill : styleSkill(a);
            if (primary == null) {
                for (Skill s : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENSE}) g.prog.award(a, s, dealt * g.cfg.xpPerDamage / 3, "melee damage (shared)", ev.id());
            } else g.prog.award(a, primary, dealt * g.cfg.xpPerDamage * (primary == Skill.MAGIC ? 0.5f : 1f), "damage dealt", ev.id());
            g.prog.award(a, Skill.HEALTH, dealt * g.cfg.healthXpPerDamage, "damage dealt (Health share)", ev.id());
        }
        if (blockedPart > 0) g.prog.award(v, Skill.DEFENSE, blockedPart * g.cfg.defenseXpPerBlocked, "damage guarded", ev.id());
        if (g.law != null) g.law.onHit(a, v, ev.id());
        // the wound itself: blood, stains, and a part taken off by a heavy enough cut (Gore)
        if (g.gore != null && dealt > 0) {
            boolean deadly = v.hp <= 0 && !v.dummy && !(g.law != null && a instanceof rpg.ai.Npc ln && (ln.role == rpg.ai.Npc.Role.GUARD || ln.role == rpg.ai.Npc.Role.LAWMAN) && g.law.bounty(v.id, ln.homeJurisdiction) > 0);
            g.gore.onHit(a, v, dealt, dmg, type, px, py, pz, -tx / tl, -tz / tl, deadly);
        }
        // a blow that knocks a person off balance (or lands on one already reeling) moves the body itself: Sydney takes it,
        // by the blow's momentum - how far past their Stability it struck, or a fraction of it on someone already reeling
        if (v instanceof rpg.ai.Npc vn && vn.alive && v.hp > 0 && g.reactions != null && (stagger || vn.reacting) && !guarding) {
            float over = Math.max(0, impact - stab * 0.6f);
            float J = Math.max(14f, Math.min(170f, 14f + over * 1.6f));
            g.reactions.struck(vn, -tx / tl, 0.12f, -tz / tl, J, J * 0.3f, px, py, pz);
        }
        // an animal struck: the blow is remembered (a killing one throws the body), and a heavy one fells a big beast
        if (v instanceof rpg.animal.Animal an && g.animals != null && dealt > 0) g.animals.struck(an, -tx / tl, -tz / tl, impact, stagger && v.hp > 0);
        if (v.hp <= 0 && !v.dummy) {
            if (g.law != null && g.law.arrestInsteadOfKill(a, v)) v.hp = Math.max(1, v.hp);
            else kill(a, v, ev.id());
        }
    }

    /** The skill a melee hit trains, by the attacker's chosen style (null: shared). */
    private static Skill styleSkill(Actor a) {
        return switch (a instanceof rpg.actor.Player p ? p.style : 3) { case 0 -> Skill.ATTACK; case 1 -> Skill.STRENGTH; case 2 -> Skill.DEFENSE; default -> null; };
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
        var ev = g.state.commit("BledOut", killer == null ? 0 : killer.id, v.id, rpg.world.DevMap.jurisdiction(v.x, v.z), 0, v.name);
        kill(killer == null ? v : killer, v, ev.id());
    }

    private void kill(Actor killer, Actor v, long cause) {
        v.alive = false;
        v.hp = 0;
        v.cs.cancel();
        kills++;
        var ev = g.state.commit("ActorKilled", killer.id, v.id, rpg.world.DevMap.jurisdiction(v.x, v.z), cause, v.name + " killed by " + killer.name);
        // a person's last cry, on the voice bus, from their own throat's bank (there was never a plain "death" bank, so no
        // human - the player included - made a sound when they died); a creature's cry is its species' (Animals)
        if (g.sfx != null && !(v instanceof rpg.animal.Animal)) g.sfx.deathCry(v);
        g.onKilled(v, killer, ev.id());
    }

    // ------------------------------------------------------------------ projectiles
    private void updateProjectiles(float dt) {
        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            p.life -= dt;
            if (p.life <= 0 || p.done) { if (!p.done) dropArrow(p); it.remove(); continue; }
            int sub = 4;
            for (int i = 0; i < sub && !p.done; i++) step(p, dt / sub);
            if (p.spell != null && g.fx != null) {
                float[] c = p.spell == Spell.FROST_LANCE ? new float[]{1.2f, 2.5f, 5} : p.spell == Spell.FIRE_BOLT ? new float[]{6, 2, 0.4f} : new float[]{3, 3, 5};
                g.fx.trail(p.x, p.y, p.z, c[0], c[1], c[2]);
            }
            if (p.done) it.remove();
        }
    }

    private void step(Projectile p, float dt) {
        p.vy -= p.gravity * dt;
        float nx = p.x + p.vx * dt, ny = p.y + p.vy * dt, nz = p.z + p.vz * dt;
        float sx = nx - p.x, sy = ny - p.y, sz = nz - p.z, len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        // bodies first along the segment, then walls
        Voxels.Hit h = g.vox.ray(p.x, p.y, p.z, sx, sy, sz, len, false);
        float wall = h.hit ? h.dist : len;
        for (int k = 1; k <= 4; k++) {
            float t = wall * k / 4f / Math.max(1e-4f, len);
            float qx = p.x + sx * t, qy = p.y + sy * t, qz = p.z + sz * t;
            for (Actor v : g.actors()) {
                if (v == p.owner || !v.alive || (v.team != 0 && v.team == p.owner.team && !hostileTo(p.owner, v))) continue;
                if (!inCapsule(v, qx, qy, qz, 0.08f)) continue;
                p.done = true;
                if (p.ammo != null) g.state.burn(p.ammo.def, 1);
                if (p.spell == Spell.FROST_LANCE) v.cs.slowT = 3;
                resolve(p.owner, v, p.potential, p.impact, p.type, p, qx, qy, qz, p.spell != null ? p.spell.title : "arrow", false);
                return;
            }
        }
        if (h.hit && h.block == Blocks.GLASS && g.glass != null) {
            // through the pane: it shatters, and the arrow or the bolt flies on, slowed
            g.glass.shatter(h.bx, h.by, h.bz, h.x, h.y, h.z, sx, sy, sz, p.spell != null ? 1.6f : 0.9f, p.owner);
            p.vx *= 0.7f; p.vy *= 0.7f; p.vz *= 0.7f;
            p.x = nx; p.y = ny; p.z = nz;
            return;
        }
        if (h.hit) {
            p.done = true;
            p.x = h.x - sx / len * 0.15f; p.y = h.y - sy / len * 0.15f; p.z = h.z - sz / len * 0.15f;
            if (p.spell != null) { if (g.fx != null) g.fx.sparks(p.x, p.y, p.z, 14); if (g.sfx != null) g.sfx.play("zap", p.x, p.y, p.z, 0.5f); return; }
            if (h.block == Blocks.TARGET) { archeryTarget(p, h); return; }
            if (g.sfx != null) g.sfx.play("thud", p.x, p.y, p.z, 0.4f);
            dropArrow(p);
            return;
        }
        p.x = nx; p.y = ny; p.z = nz;
    }

    /** An arrow in the archery target: Archery XP growing with the distance shot from (the arrow stays in the target). */
    private void archeryTarget(Projectile p, Voxels.Hit h) {
        if (p.ammo != null) g.state.burn(p.ammo.def, 1);
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
        if (g.sfx != null) g.sfx.play("thud", h.x, h.y, h.z, 0.5f);
    }

    /** A spent arrow that missed lands where it stopped, to be picked up again. */
    /** Every projectile in flight comes down where it is now (before a save: flights are not kept). */
    public void landAll() {
        for (Projectile p : projectiles) dropArrow(p);
        projectiles.clear();
    }

    private void dropArrow(Projectile p) {
        if (p.ammo == null) return;
        float y = g.vox.groundY(p.x, p.y, p.z);
        g.objects.drop(p.ammo, p.x, Math.max(y + 0.05f, Math.min(p.y, y + 0.3f)), p.z);
        p.ammo = null;
    }

    public void log(String line) {
        telemetry.addLast(line);
        while (telemetry.size() > 14) telemetry.removeFirst();
    }
}
