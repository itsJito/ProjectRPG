package rpg.combat;

import astratto.bridge.AstrattoNative;
import static astratto.bridge.AstrattoNative.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.actor.Body;
import rpg.ai.Hunt;
import rpg.ai.Memory;
import rpg.ai.Npc;
import rpg.item.ItemDef;
import rpg.item.ItemDef.DamageType;
import rpg.item.ItemStack;
import rpg.world.Blocks;
import rpg.world.DevMap;
import rpg.world.Voxels;

/**
 * Wounds, blood and dismemberment (gamefixes: the gore and dismemberment system). Every hit lands on a part of the
 * body - the head, the torso, an arm, a leg - read from where the blow actually met the body. Damage there wounds that
 * part: blades and points open bleeding wounds, blunt blows bruise; a bleeding body drips as it goes, leaving a trail,
 * and bleeds on to the ground where it lies. A heavy cut to an arm, a leg or the neck can take the part off: it falls
 * away as a physical body of its own, the stump bleeds hard, and the injured react - a man who has lost his sword arm
 * drops his sword, one who has lost a leg goes down, and both scream (the death cries are the only voices used).
 * The player is wounded and bleeds too, but keeps their limbs unless the blow is a deadly one.
 *
 * <p>The blood is the voxel world's own: square droplets on the same particles the water and rain use, and stains laid
 * on the world's faces by the engine's decal layer (spatter on walls and floors, pools under the fallen). And it is
 * evidence. Blood, bodies and severed limbs are things NPCs can see - in their eyes' cone, in the light - and they react:
 * a passer-by cries out and runs to report it; a guard goes to look and sets out to search for whoever did it (the area
 * shows on the player's minimap); a searcher who then sees someone with blood on them, close to a fresh kill, has found
 * a suspect.
 */
public final class Gore {
    /**
     * The parts a blow lands on. A person's arms and legs; on a four-legged animal ARM_* are the forelegs and LEG_* the
     * hind legs, on a bird ARM_* are the wings and LEG_* its legs; TAIL is an animal's.
     */
    public enum Part { HEAD, TORSO, ARM_R, ARM_L, LEG_R, LEG_L, TAIL }

    /** One body's injuries: how badly each part is hurt (share of full health), what is gone, how fast it bleeds. */
    public static final class Wounds {
        public final float[] hurt = new float[Part.values().length];
        public final boolean[] severed = new boolean[Part.values().length];
        /** Bleeding, as a share of full health lost a second at 1.0; clots over time unless a limb is gone. */
        public float bleed;
        /** The part last wounded (where blood runs from, on the body). */
        public Part last = Part.TORSO;
        /** Who opened the wound that bleeds (an arrest settling the fight does not change who cut him). */
        public long by;
        double nextDrip;
        /** An opened artery: it spurts in time with the heart until then (a severed limb, a deep cut to the neck). */
        public double spurtUntil = -1;
        double nextSpurt;
        public boolean any() { for (boolean b : severed) if (b) return true; return false; }
        public boolean lost(Part p) { return severed[p.ordinal()]; }
    }

    /** Something an NPC can find: blood on the ground, a body, a severed limb. */
    public enum Kind { BLOOD, BODY, LIMB }
    public static final class Evidence {
        public final long id;
        public final Kind kind;
        public float x, y, z, size;
        public final double at;
        /** When it was last fresh (blood is slick while wet - see slip()). */
        public double wetAt;
        /** Whose blood or body; and who struck the blow that spilled it (the law does not know this). */
        public final long source, culprit;
        Evidence(long id, Kind kind, float x, float y, float z, float size, double at, long source, long culprit) {
            this.id = id; this.kind = kind; this.x = x; this.y = y; this.z = z; this.size = size; this.at = at; this.source = source; this.culprit = culprit;
        }
    }

    /** A severed part lying in the world: its rigid body, whose it was (to draw it from their own figure) and which part. */
    public static final class Limb {
        public final long body, owner;
        public final Part part;
        public final Appearance look;
        public final float length;
        /** An animal's part: its species and coat, and its size (null species for a person's). */
        public rpg.animal.Species species;
        public int variant;
        public float scale = 1;
        /** A monster's part (0.2.1): drawn from its kind's own figure. */
        public rpg.monster.MonsterKind monster;
        public rpg.monster.MonsterKind.Breed breed;
        Limb(long body, long owner, Part part, Appearance look, float length) { this.body = body; this.owner = owner; this.part = part; this.look = look; this.length = length; }
    }

    /** A stain for the decal layer: where, the face it lies on, how big, turned how. Read by the renderer. */
    public record Stain(float x, float y, float z, float nx, float ny, float nz, float size, float rot) {}

    private final Rpg g;
    public final Map<Long, Wounds> wounds = new HashMap<>();
    public final List<Evidence> evidence = new ArrayList<>();
    public final List<Limb> limbs = new ArrayList<>();
    public final List<Stain> stains = new ArrayList<>();
    /** Stains ever made (the renderer keeps its own count of those it has laid). */
    public int stainCount;
    private long nextId = 1;
    private final java.util.Random rnd = new java.util.Random(31);
    /** Who has noticed which evidence (an NPC reacts to each thing once). */
    private final Map<Long, Set<Long>> noticed = new HashMap<>();
    public long hits, severs, bledOut, found;

    public Gore(Rpg g) { this.g = g; }

    public Wounds of(Actor a) { Wounds w = wounds.computeIfAbsent(a.id, k -> new Wounds()); a.injury = w.hurt; return w; }
    public boolean lost(Actor a, Part p) { Wounds w = wounds.get(a.id); return w != null && w.severed[p.ordinal()]; }

    // ------------------------------------------------------------------ a blow lands
    /** Which part a contact point is on: by its height up the body and how far to the side of the spine it is. */
    public Part partAt(Actor v, float px, float py, float pz) {
        if (v instanceof rpg.animal.Animal an) return animalPartAt(an, px, py, pz);
        if (v instanceof rpg.monster.Monster mo && mo.kind.form != rpg.monster.MonsterKind.Form.HUMANOID) return HitVolumes.beastPart(mo, px, py, pz);
        Part posed = HitVolumes.part(g, v, px, py, pz);
        if (posed != null) return posed;
        float h = (py - v.y) / Math.max(0.5f, v.height * (v.sneaking || v.crouched ? 0.5f : 1));
        float rx = (float) Math.cos(v.yaw), rz = (float) -Math.sin(v.yaw);
        float side = (px - v.x) * rx + (pz - v.z) * rz;
        if (h > 0.84f) return Part.HEAD;
        if (h < 0.48f) return side >= 0 ? Part.LEG_R : Part.LEG_L;
        if (Math.abs(side) > v.radius * 0.7f) return side >= 0 ? Part.ARM_R : Part.ARM_L;
        return Part.TORSO;
    }

    /**
     * Where a blow meets an animal, from its own figure: the head out in front of the shoulders, the tail behind, a leg
     * below the barrel (fore or hind, left or right), a bird's wing at its flank; the barrel itself otherwise.
     */
    public static Part animalPartAt(rpg.animal.Animal an, float px, float py, float pz) {
        var f = rpg.render.AnimalModels.of(an.species, an.variant);
        float vs = rpg.render.AnimalModels.PX * an.scale();
        float rx = (float) Math.cos(an.yaw), rz = (float) -Math.sin(an.yaw), fx = (float) -Math.sin(an.yaw), fz = (float) -Math.cos(an.yaw);
        float side = ((px - an.x) * rx + (pz - an.z) * rz) / vs, fwd = ((px - an.x) * fx + (pz - an.z) * fz) / vs, up = (py - an.y) / vs;
        float belly = f.bodyY - f.bh / 2f + 0.5f;
        if (fwd > f.bl / 2f - 1.5f && up > belly + f.bh * 0.25f) return Part.HEAD;
        if (fwd < -f.bl / 2f + 1 && up > f.bodyY - f.bh * 0.25f && f.tail != null) return Part.TAIL;
        if (f.bird && Math.abs(side) > f.bw / 2f - 1.2f && up > belly) return side >= 0 ? Part.ARM_R : Part.ARM_L;
        if (up < belly) {
            if (f.bird) return side >= 0 ? Part.LEG_R : Part.LEG_L;
            return fwd >= 0 ? (side >= 0 ? Part.ARM_R : Part.ARM_L) : (side >= 0 ? Part.LEG_R : Part.LEG_L);
        }
        return Part.TORSO;
    }

    /**
     * Tells a body Sydney holds which of its limbs are gone, so it no longer stands, steps or pushes up on a leg or an
     * arm it has lost (it crawls, or lies, as a body that has lost them would). Bits: 1 left arm, 2 right arm, 4 left
     * leg, 8 right leg, 16 the head.
     */
    public void limbsToBody(Npc n) {
        Wounds w = wounds.get(n.id);
        if (n.ragdoll == 0 || w == null || !w.any()) return;
        int broken = (w.lost(Part.ARM_L) ? 1 : 0) | (w.lost(Part.ARM_R) ? 2 : 0) | (w.lost(Part.LEG_L) ? 4 : 0) | (w.lost(Part.LEG_R) ? 8 : 0) | (w.lost(Part.HEAD) ? 16 : 0);
        g.game.commands().ragdollControl(n.ragdoll, AstrattoNative.CONTROL_LIMBS, broken);
    }

    /**
     * A blow dealt damage: the part is wounded, blood flies and stains what it lands on, and a heavy enough cut takes the
     * part off. Called by combat after the damage is applied (and before a death is resolved).
     */
    public void onHit(Actor a, Actor v, int dealt, DamageType type, float px, float py, float pz, float dirX, float dirZ, boolean deadly) {
        onHit(a, v, dealt, dealt, type, px, py, pz, dirX, dirZ, deadly);
    }

    /**
     * force: the blow's whole damage before it was capped at the HP the victim had left - how hard the cut really was.
     * (The killing blow's damage is capped at what remained, so a finishing cut read only from what it dealt was almost
     * never heavy enough to take anything off: that is why dismemberment did not work.)
     */
    public void onHit(Actor a, Actor v, int dealt, float force, DamageType type, float px, float py, float pz, float dirX, float dirZ, boolean deadly) {
        onHit(a, v, dealt, force, type, Spells.Element.NONE, px, py, pz, dirX, dirZ, deadly);
    }

    /**
     * How a spell's element wounds flesh (gamefixes: magic did no visible damage, could not take a head, and a deadly spell
     * should dismember): fire and lightning burn - a scorch where it struck and little blood; ice and wind cut and pierce
     * like steel; earth and water batter like a club; a blood spell opens the veins. Any of them, killing, takes the part
     * it struck clean off - a head included - and an overwhelming one tears off another besides.
     */
    static float magicBleed(Spells.Element e) {
        return switch (e) { case FIRE -> 0.25f; case ELECTRIC -> 0.3f; case ICE -> 0.9f; case AIR -> 1.0f; case EARTH -> 0.6f; case WATER -> 0.35f; case BLOOD -> 1.6f; default -> 0.5f; };
    }

    public void onHit(Actor a, Actor v, int dealt, float force, DamageType type, Spells.Element element, float px, float py, float pz, float dirX, float dirZ, boolean deadly) {
        if (v.dummy || dealt <= 0) return;
        hits++;
        Part part = partAt(v, px, py, pz);
        Wounds w = of(v);
        boolean animal = v instanceof rpg.animal.Animal;
        float share = dealt / Math.max(1f, v.maxHp()), cutShare = Math.max(dealt, force) / Math.max(1f, v.maxHp());
        w.hurt[part.ordinal()] += share;
        w.last = part;
        if (a != null) w.by = a.id;
        boolean magic = type == DamageType.MAGIC;
        float bleedy = switch (type) { case SLASH -> 1.2f; case PIERCE -> 1.0f; case BLUNT -> 0.25f; case MAGIC -> magicBleed(element); default -> 0f; };
        if (bleedy <= 0) return;
        // fire and lightning sear where they strike: a char on the figure, smouldering at its edge
        if (magic && (element == Spells.Element.FIRE || element == Spells.Element.ELECTRIC) && !animal) {
            float[] at = rpg.actor.Blood.locate(v, px, py, pz);
            v.blood.scorch((int) at[0], (int) at[1], at[2], at[3], 0.9f + Math.min(2.4f, share * 9), g.time);
        }
        w.bleed = Math.min(2f, w.bleed + share * bleedy * (part == Part.HEAD || part == Part.TORSO ? 1.3f : 1f));
        // the spray: droplets thrown along the blow - a mist of fine drops and heavier gouts - and where they land,
        // stains: on a wall behind (streaked the way the blow went), on the floor below
        int drops = 10 + Math.min(60, (int) (share * 160 * bleedy));
        if (g.fx != null) {
            for (int i = 0; i < Math.max(1, drops / 7); i++) g.fx.blood(px, py, pz, dirX, dirZ);
            g.fx.bloodMist(px, py, pz, dirX, dirZ, Math.min(1, share * 3 * bleedy));
        }
        spatter(px, py, pz, dirX, dirZ, share * bleedy * 1.5f, v.id, a == null ? 0 : a.id);
        // a deep cut to the throat opens an artery
        if (part == Part.HEAD && type == DamageType.SLASH && share >= 0.2f && !animal) of(v).spurtUntil = g.time + 4;
        float close = a == null ? 9 : (float) Math.hypot(a.x - v.x, a.z - v.z);
        if (a == g.player || a instanceof Npc) a.bloodied = Math.min(1, a.bloodied + share * bleedy * (close < 1.6f ? 1.5f : 0.6f));
        pool(v.x, v.z, 0.18f + Math.min(0.5f, share * 2), v.id, a == null ? 0 : a.id);
        // blood on the body itself: the wound, where the blow landed on the figure; and on the one who struck close
        // (the striking hand and forearm, and the chest for a heavy cut)
        if (!animal) {
            float[] at = rpg.actor.Blood.locate(v, px, py, pz);
            v.blood.add((int) at[0], (int) at[1], at[2], at[3], 1.4f + Math.min(4.6f, share * 16 * bleedy), g.time);
        }
        if (a != null && !(a instanceof rpg.animal.Animal) && close < 1.7f) {
            int arm = a.cs.offHand ? rpg.actor.Blood.L_ARM : rpg.actor.Blood.R_ARM;
            a.blood.add(arm, rpg.actor.Blood.FRONT, 0.35f + rnd.nextFloat() * 0.3f, 0.72f + rnd.nextFloat() * 0.22f, 1 + Math.min(3, share * 10), g.time);
            if (share * bleedy > 0.12f) a.blood.add(rpg.actor.Blood.TORSO, rpg.actor.Blood.FRONT, 0.25f + rnd.nextFloat() * 0.5f, 0.1f + rnd.nextFloat() * 0.5f, 1 + Math.min(3.5f, share * 9), g.time);
        }
        // a part off: a heavy cut, or a deadly one (the player keeps their limbs unless the blow kills); animals as people,
        // a leg, a wing, the tail or the head
        boolean[] whole = w.severed.clone();
        boolean cleave = (type == DamageType.SLASH || magic && element != Spells.Element.WATER) && part != Part.TORSO;
        if (cleave && !w.severed[part.ordinal()]) {
            boolean deadlyCut = deadly && cutShare >= (magic ? 0.08f : 0.12f);
            boolean grievous = v != g.player && !deadly && !magic && part != Part.HEAD && w.hurt[part.ordinal()] >= (animal ? 0.45f : 0.6f) && cutShare >= (part == Part.TAIL ? 0.08f : 0.16f);
            if (deadlyCut || grievous) {
                if (animal) severAnimal((rpg.animal.Animal) v, part, dirX, dirZ, a);
                else if (part != Part.TAIL || v instanceof rpg.monster.Monster) sever(v, part, dirX, dirZ, a);
            }
        }
        // a killing spell that strikes the trunk, or one far past what was needed, tears a limb off as well
        if (magic && deadly && !animal && v != g.player && (part == Part.TORSO ? cutShare >= 0.1f : cutShare >= 0.55f)) {
            Part[] limbs = {Part.ARM_R, Part.ARM_L, Part.LEG_R, Part.LEG_L, Part.HEAD};
            Part extra = limbs[(int) Math.floorMod(v.id * 31 + (long) (g.time * 7), limbs.length)];
            for (int k = 0; k < limbs.length && w.severed[extra.ordinal()]; k++) extra = limbs[(java.util.Arrays.asList(limbs).indexOf(extra) + 1) % limbs.length];
            if (!w.severed[extra.ordinal()]) sever(v, extra, dirX, dirZ, a);
        }
        if (magic && (element == Spells.Element.FIRE || element == Spells.Element.ELECTRIC) && !animal) for (Part p : Part.values()) if (w.severed[p.ordinal()] && !whole[p.ordinal()] && p != Part.TORSO) charStump(v, p);
    }

    /** The stump a burning spell left is charred, not raw: a scorch where the part was. */
    private void charStump(Actor v, Part p) {
        int region = switch (p) { case HEAD -> rpg.actor.Blood.TORSO; case ARM_R -> rpg.actor.Blood.R_ARM; case ARM_L -> rpg.actor.Blood.L_ARM; case LEG_R -> rpg.actor.Blood.R_LEG; case LEG_L -> rpg.actor.Blood.L_LEG; default -> rpg.actor.Blood.TORSO; };
        float vv = p == Part.HEAD ? 0.05f : 0.15f;
        v.blood.scorch(region, rpg.actor.Blood.TOP, 0.5f, 0.5f, 1.6f, g.time);
        v.blood.scorch(region, rpg.actor.Blood.FRONT, 0.5f, vv, 1.2f, g.time);
    }

    /**
     * An animal's part comes off (a leg, a wing, the tail, the head): a body of its own thrown along the blow, drawn from
     * the animal's own figure part; the stump bleeds; a lost leg lames it (see {@link #legs}), a lost head ends it.
     */
    public void severAnimal(rpg.animal.Animal an, Part part, float dirX, float dirZ, Actor by) {
        Wounds w = of(an);
        w.severed[part.ordinal()] = true;
        severs++;
        var f = rpg.render.AnimalModels.of(an.species, an.variant);
        float vs = rpg.render.AnimalModels.PX * an.scale();
        rpg.render.ItemModels.Model md;
        float[] joint;
        float sideSign = part == Part.ARM_R || part == Part.LEG_R ? 1 : part == Part.ARM_L || part == Part.LEG_L ? -1 : 0;
        switch (part) {
            case HEAD -> { md = f.head; joint = f.neck; }
            case TAIL -> { md = f.tail; joint = f.tailAt; }
            case ARM_R, ARM_L -> { md = f.bird ? f.wing : f.legFront; joint = f.bird ? f.wingAt : f.legF; }
            default -> { md = f.bird ? f.legFront : f.legBack; joint = f.bird ? f.legF : f.legB; }
        }
        if (md == null || joint == null) return;
        var vm = md.vox();
        float jx = joint[0] * (sideSign == 0 ? 1 : sideSign) * (f.bird && (part == Part.LEG_R || part == Part.LEG_L) ? 0.6f : 1), jy = f.bodyY + joint[1], jz = joint[2];
        // the part's middle, from its joint (its model's pivot)
        float cx = jx + (vm.sx / 2f - md.gx()), cy = jy + (vm.sy / 2f - md.gy()), cz = jz + (vm.sz / 2f - md.gz());
        float rx = (float) Math.cos(an.yaw), rz = (float) -Math.sin(an.yaw), bx = (float) Math.sin(an.yaw), bz = (float) Math.cos(an.yaw);
        float lx = an.x + (rx * cx + bx * cz) * vs, ly = an.y + cy * vs, lz = an.z + (rz * cx + bz * cz) * vs;
        float hx = Math.max(0.03f, vm.sx * vs / 2), hy = Math.max(0.03f, vm.sy * vs / 2), hz = Math.max(0.03f, vm.sz * vs / 2);
        long body = g.game.reserve(AstrattoNative.KIND_RIGID);
        float sp = 1.8f + rnd.nextFloat() * 1.4f, ang = an.yaw;
        float mass = Math.max(0.2f, an.species.w * an.species.w * an.species.h * 420 * an.scale() * an.scale() * an.scale() * (part == Part.HEAD ? 0.07f : part == Part.TAIL ? 0.02f : 0.06f));
        g.game.commands().rigidCreate(body, AstrattoNative.SHAPE_BOX, AstrattoNative.RIGID_DEBRIS | AstrattoNative.RIGID_NO_MOVERS, hx, hy, hz, mass,
                lx, ly, lz, (float) Math.cos(ang / 2), 0, (float) Math.sin(ang / 2), 0,
                dirX * sp, 1.4f + rnd.nextFloat(), dirZ * sp, (rnd.nextFloat() - 0.5f) * 10, (rnd.nextFloat() - 0.5f) * 6, (rnd.nextFloat() - 0.5f) * 10, 0.7f, 0.05f, 0, null);
        Limb l = new Limb(body, an.id, part, null, Math.max(hx, Math.max(hy, hz)) * 2);
        l.species = an.species; l.variant = an.variant; l.scale = an.scale();
        limbs.add(l);
        evidence(Kind.LIMB, lx, DevMap.FLOOR, lz, 0.4f, an.id, by == null ? 0 : by.id);
        w.bleed = Math.min(2.5f, w.bleed + (part == Part.HEAD ? 2f : part == Part.TAIL ? 0.3f : 0.7f));
        if (g.fx != null) for (int i = 0; i < 5; i++) g.fx.blood(lx, ly, lz, dirX * 0.5f, dirZ * 0.5f);
        for (int i = 0; i < 2; i++) spatter(lx, ly, lz, dirX + (rnd.nextFloat() - 0.5f), dirZ + (rnd.nextFloat() - 0.5f), 0.4f, an.id, by == null ? 0 : by.id);
        pool(an.x, an.z, 0.45f, an.id, by == null ? 0 : by.id);
        if (g.sfx != null) {
            g.sfx.play("soft.heavy", lx, ly, lz, 0.85f);
            if (part != Part.HEAD && an.alive && g.animals != null) g.animals.sound(an, an.species.hurtSound, 1f);
        }
        if (part == Part.ARM_R || part == Part.ARM_L || part == Part.LEG_R || part == Part.LEG_L) { an.cs.cancel(); an.cs.staggered = Math.max(an.cs.staggered, 1.5f); }
        if (an.alive && g.animals != null && by != null) g.animals.onHurt(an, by);
        g.state.commit("Dismembered", by == null ? 0 : by.id, an.id, DevMap.jurisdiction(an.x, an.z), 0, an.kind() + " " + part.name());
    }

    /** Stains where a spray lands: rays from the wound along the blow and down, stopping at the first face. */
    /** (whose blood, and who drew it: evidence keeps both, so an outlaw's blood is known as one) */
    private void spatter(float px, float py, float pz, float dx, float dz, float amount, long source, long culprit) {
        int rays = 4 + Math.min(10, (int) (amount * 20));
        for (int i = 0; i < rays; i++) {
            float sx = dx + (rnd.nextFloat() - 0.5f) * 0.9f, sz = dz + (rnd.nextFloat() - 0.5f) * 0.9f, sy = (rnd.nextFloat() - 0.55f) * 1.3f;
            Voxels.Hit h = g.vox.ray(px, py, pz, sx, sy, sz, 4.2f, true);
            if (!h.hit || h.block == Blocks.WATER) continue;
            float size = 0.22f + rnd.nextFloat() * 0.34f + Math.min(0.45f, amount);
            surfaceStain(h.x,h.y,h.z,h.nx,h.ny,h.nz,size);
            for(int drop=0;drop<2;drop++) {
                float u=(rnd.nextFloat()-0.5f)*0.7f,v=(rnd.nextFloat()-0.5f)*0.7f;
                float x=h.x+(h.nx==0?u:0),y=h.y+(h.ny==0?v:0),z=h.z+(h.nx!=0?u:h.ny!=0?v:0);
                surfaceStain(x,y,z,h.nx,h.ny,h.nz,size*(0.12f+rnd.nextFloat()*0.15f));
            }
            if (h.ny > 0) evidence(Kind.BLOOD, h.x, h.y, h.z, size * 0.6f, source, culprit);
            else if (amount > 0.12f) {
                // on a wall it runs: a streak of smaller stains below the splash, the heavier the blow the longer
                int run = 2 + (int) Math.min(5, amount * 12);
                for (int k = 1; k <= run; k++) surfaceStain(h.x + h.nx * 0.002f, h.y - k * size * 0.32f, h.z + h.nz * 0.002f, h.nx, h.ny, h.nz, size * (0.55f - k * 0.05f));
            }
        }
    }

    /**
     * The heart pushing blood out of an opened artery: a gout thrown up and out with each beat (about a second apart),
     * weakening as the body fails, landing as spatter beyond and pooling below.
     */
    private void spurt(Actor a, Wounds w) {
        float beat = (float) Math.max(0.25, (w.spurtUntil - g.time) / 6);
        float yaw = a.yaw + (rnd.nextFloat() - 0.5f) * 1.2f, dx = (float) -Math.sin(yaw), dz = (float) -Math.cos(yaw);
        float y = a.y + (a.alive ? 1.35f : 0.25f);
        if (g.fx != null) g.fx.spurt(a.x, y, a.z, dx, dz, beat);
        spatter(a.x, y, a.z, dx * 1.6f, dz * 1.6f, 0.25f * beat, a.id, w.by);
        pool(a.x + dx * 0.9f, a.z + dz * 0.9f, 0.3f * beat, a.id, w.by);
    }

    /** Blood on the floor under someone (a drip, a wound, the pool round a body). */
    public void pool(float x, float z, float size, long source, long culprit) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        Actor owner=g.actor(source);int from=(int)Math.floor(owner==null?DevMap.FLOOR+2:owner.y+owner.height);
        int top = 0;
        for (int y = Math.min(DevMap.SY-1,from); y >= 0; y--) if (g.vox.solid(bx, y, bz)) { top = y; break; }
        if (g.vox.block(bx, top + 1, bz) == Blocks.WATER) return;   // into water: it washes away
        float y = top + 1;
        stain(x + (rnd.nextFloat() - 0.5f) * 0.2f, y, z + (rnd.nextFloat() - 0.5f) * 0.2f, 0, 1, 0, size);
        // a pool joins the nearest blood close by rather than making another
        for (Evidence e : evidence) if (e.kind == Kind.BLOOD && Math.hypot(e.x - x, e.z - z) < 0.8f) { e.size = Math.min(2.4f, e.size + size * 0.4f); e.wetAt = g.time; return; }
        evidence(Kind.BLOOD, x, y, z, size, source, culprit);
    }

    private void surfaceStain(float x,float y,float z,float nx,float ny,float nz,float size) {
        if(!g.vox.solid((int)Math.floor(x-nx*0.03f),(int)Math.floor(y-ny*0.03f),(int)Math.floor(z-nz*0.03f)))return;
        stain(x,y,z,nx,ny,nz,size);
    }

    private void stain(float x, float y, float z, float nx, float ny, float nz, float size) {
        stains.add(new Stain(x, y, z, nx, ny, nz, size, rnd.nextFloat() * 6.28f));
        stainCount++;
        if (stains.size() > 512) stains.remove(0);
    }

    private Evidence evidence(Kind k, float x, float y, float z, float size, long source, long culprit) {
        Evidence e = new Evidence(nextId++, k, x, y, z, size, g.time, source, culprit);
        e.wetAt = g.time;
        evidence.add(e);
        return e;
    }

    /** A part comes off: a body of its own thrown along the blow, a gush, a scream, and what the injured can no longer do. */
    /**
     * Where a monster's part is and how big, for the piece cut off it: {x, y, z, length, half-thickness} - from the
     * pose its figure was last drawn in (a humanoid's joints), from its beast zones, or from its kind's own proportions.
     */
    private float[] monsterPart(rpg.monster.Monster mo, Part part) {
        if (mo.kind.form != rpg.monster.MonsterKind.Form.HUMANOID) {
            for (float[] z : HitVolumes.zones(mo)) if ((int) z[0] == part.ordinal()) {
                float l = (float) Math.sqrt(Math.pow(z[4] - z[1], 2) + Math.pow(z[5] - z[2], 2) + Math.pow(z[6] - z[3], 2)) + z[7];
                return new float[]{(z[1] + z[4]) / 2, (z[2] + z[5]) / 2, (z[3] + z[6]) / 2, Math.max(0.1f, l), Math.max(0.04f, z[7] * 0.8f)};
            }
            return null;
        }
        float[] p = HitVolumes.pose(g, mo);
        int a, c;
        switch (part) {
            case HEAD -> { a = RD_NECK; c = RD_HEAD; }
            case ARM_R -> { a = RD_R_SHOULDER; c = RD_R_WRIST; }
            case ARM_L -> { a = RD_L_SHOULDER; c = RD_L_WRIST; }
            case LEG_R -> { a = RD_R_HIP; c = RD_R_ANKLE; }
            case LEG_L -> { a = RD_L_HIP; c = RD_L_ANKLE; }
            default -> { return null; }
        }
        var f = rpg.render.MonsterModels.of(mo.kind, mo.breed);
        float thick = (part == Part.HEAD ? f.headHalf : 1.6f) * f.vs;
        float len = (part == Part.HEAD ? f.headHalf * 2 : part == Part.ARM_R || part == Part.ARM_L ? f.upperArm + f.foreArm : f.thigh + f.shin) * f.vs;
        if (p != null) return new float[]{(p[a * 3] + p[c * 3]) / 2, (p[a * 3 + 1] + p[c * 3 + 1]) / 2, (p[a * 3 + 2] + p[c * 3 + 2]) / 2, len, thick};
        float h = part == Part.HEAD ? mo.height - len / 2 : part == Part.ARM_R || part == Part.ARM_L ? mo.height * 0.62f : len / 2;
        float s = part == Part.ARM_R || part == Part.LEG_R ? 1 : part == Part.ARM_L || part == Part.LEG_L ? -1 : 0, rx = (float) Math.cos(mo.yaw), rz = (float) -Math.sin(mo.yaw);
        float out = (part == Part.ARM_R || part == Part.ARM_L ? f.shoulder[0] : f.hip[0]) * f.vs * s;
        return new float[]{mo.x + rx * out, mo.y + h, mo.z + rz * out, len, thick};
    }

    public void sever(Actor v, Part part, float dirX, float dirZ, Actor by) {
        Wounds w = of(v);
        w.severed[part.ordinal()] = true;
        severs++;
        Body b = v.plan();
        float px = Body.PX;
        float rx = (float) Math.cos(v.yaw), rz = (float) -Math.sin(v.yaw);
        float side = part == Part.ARM_R || part == Part.LEG_R ? 1 : part == Part.ARM_L || part == Part.LEG_L ? -1 : 0;
        float lx, ly, lz, hx, hy, hz, len;
        float[] at = v instanceof rpg.monster.Monster mo ? monsterPart(mo, part) : null;
        if (at != null) { lx = at[0]; ly = at[1]; lz = at[2]; len = at[3]; hx = hz = at[4]; hy = len / 2; }
        else switch (part) {
            case HEAD -> { len = b.head[1] * px; lx = v.x; ly = v.y + (b.neckY() + b.head[1] / 2f) * px; lz = v.z; hx = b.head[0] * px / 2; hy = len / 2; hz = b.head[2] * px / 2; }
            case ARM_R, ARM_L -> { len = (Body.UPPER_ARM + Body.FOREARM) * px; lx = v.x + rx * side * b.shoulderX() * px; ly = v.y + b.shoulderY() * px - len / 2; lz = v.z + rz * side * b.shoulderX() * px;
                hx = b.arm[0] * b.limb * px / 2; hy = len / 2; hz = b.arm[2] * px / 2; }
            default -> { len = (Body.THIGH + Body.SHIN) * px; lx = v.x + rx * side * b.hipX() * px; ly = v.y + len / 2; lz = v.z + rz * side * b.hipX() * px;
                hx = b.leg[0] * b.limb * px / 2; hy = len / 2; hz = b.leg[2] * px / 2; }
        }
        long body = g.game.reserve(AstrattoNative.KIND_RIGID);
        float sp = 2.2f + rnd.nextFloat() * 1.5f, ang = v.yaw;
        g.game.commands().rigidCreate(body, AstrattoNative.SHAPE_BOX, AstrattoNative.RIGID_DEBRIS | AstrattoNative.RIGID_NO_MOVERS,
                Math.max(0.04f, hx), Math.max(0.05f, hy), Math.max(0.04f, hz), part == Part.HEAD ? 4.5f : part == Part.ARM_R || part == Part.ARM_L ? 3.5f : 9f,
                lx, ly, lz, (float) Math.cos(ang / 2), 0, (float) Math.sin(ang / 2), 0,
                dirX * sp, 1.6f + rnd.nextFloat(), dirZ * sp, (rnd.nextFloat() - 0.5f) * 12, (rnd.nextFloat() - 0.5f) * 6, (rnd.nextFloat() - 0.5f) * 12, 0.7f, 0.05f, 0, null);
        Limb cut = new Limb(body, v.id, part, v.look.copy(), len);
        if (v instanceof rpg.monster.Monster mo) { cut.monster = mo.kind; cut.breed = mo.breed; }
        limbs.add(cut);
        evidence(Kind.LIMB, lx, DevMap.FLOOR, lz, 0.6f, v.id, by == null ? 0 : by.id);
        // the gush
        w.spurtUntil = g.time + 6;
        w.bleed = Math.min(2.5f, w.bleed + (part == Part.HEAD ? 2f : 0.75f));
        if (g.fx != null) for (int i = 0; i < 6; i++) g.fx.blood(lx, ly, lz, dirX * 0.5f, dirZ * 0.5f);
        for (int i = 0; i < 3; i++) spatter(lx, ly, lz, dirX + (rnd.nextFloat() - 0.5f), dirZ + (rnd.nextFloat() - 0.5f), 0.5f, v.id, by == null ? 0 : by.id);
        pool(v.x, v.z, 0.6f, v.id, by == null ? 0 : by.id);
        if (g.sfx != null) {
            g.sfx.play("soft.heavy", lx, ly, lz, 0.9f);
            if (part != Part.HEAD && v.alive && v instanceof rpg.monster.Monster m) g.sfx.creature(m, m.kind.hurtSound, 1f);
            else if (part != Part.HEAD && v.alive && v instanceof rpg.monster.Monster mo) g.sfx.creature(mo, mo.kind.hurtSound, 1f);   // its own scream, never a man's
            else if (part != Part.HEAD && v.alive && !(v instanceof rpg.animal.Animal)) g.sfx.voice(v.look.female ? "hurt.female" : "hurt.male", v.x, v.y + 1.6f, v.z, 1f, 0.9f);
        }
        // what it can no longer do
        if (part == Part.ARM_R) dropHeld(v, ItemDef.Slot.MAIN);
        if (part == Part.ARM_L) dropHeld(v, ItemDef.Slot.OFF);
        if (part == Part.LEG_R || part == Part.LEG_L) { v.cs.cancel(); v.cs.staggered = Math.max(v.cs.staggered, 2.5f); }
        if (v instanceof Npc n && !(v instanceof rpg.monster.Monster) && v.alive) { n.say(part == Part.ARM_R || part == Part.ARM_L ? "My arm!" : "My leg!", g.time + 3); n.nextThink = 0; }
        if (v instanceof Npc n) limbsToBody(n);
        g.state.commit("Dismembered", by == null ? 0 : by.id, v.id, DevMap.jurisdiction(v.x, v.z), 0, part.name());
    }

    private void dropHeld(Actor v, ItemDef.Slot slot) {
        ItemStack s = v.eq.get(slot);
        if (s == null) return;
        v.eq.set(slot, null);
        g.objects.drop(s, v.x + (float) Math.cos(v.yaw) * 0.5f, DevMap.FLOOR + 0.05f, v.z - (float) Math.sin(v.yaw) * 0.5f);
        v.cs.cancel();
        v.cs.blocking = false;
    }

    /** Whether a body can walk (a leg lost: it cannot; one badly hurt: it limps). Movement speed share. */
    public float legs(Actor a) {
        Wounds w = wounds.get(a.id);
        if (w == null) return 1;
        if (a instanceof rpg.animal.Animal an) {
            // a four-legged animal limps on three and cannot go on two; a bird hops on one
            boolean bird = an.species.plan == rpg.animal.Species.Plan.BIRD;
            int lost = (w.lost(Part.LEG_R) ? 1 : 0) + (w.lost(Part.LEG_L) ? 1 : 0) + (bird ? 0 : (w.lost(Part.ARM_R) ? 1 : 0) + (w.lost(Part.ARM_L) ? 1 : 0));
            if (lost == 0) return 1;
            return lost == 1 ? (bird ? 0.4f : 0.45f) : 0.08f;
        }
        if (w.lost(Part.LEG_R) || w.lost(Part.LEG_L)) return 0.12f;   // dragging itself along
        float worst = Math.max(w.hurt[Part.LEG_R.ordinal()], w.hurt[Part.LEG_L.ordinal()]);
        return worst > 0.35f ? Math.max(0.6f, 1 - (worst - 0.35f)) : 1;
    }

    // ------------------------------------------------------------------ slick blood
    /** How long spilled blood stays slick (seconds). */
    public static final double WET = 150;
    private double nextSlipCheck;
    private final Map<Long, Double> slippedAt = new HashMap<>();
    public int slips;

    /**
     * Blood on the ground is a thing in the world (gamefixes): while it is wet, anyone running through a pool of it may
     * lose their footing - the faster and the bigger the pool, the likelier, the more agile the less. An NPC goes down
     * for real: their body is handed to Sydney with their feet swept out along their run, they fall, and get up again
     * (or not, if hurt enough). The player stumbles (slowed, the camera jolted) rather than falling.
     */
    private void slipCheck() {
        if (g.time < nextSlipCheck) return;
        nextSlipCheck = g.time + 0.1;
        for (Actor a : g.actors()) {
            if (!a.alive || a.dummy || a instanceof rpg.animal.Animal || !a.grounded) continue;
            float speed = (float) Math.hypot(a.vx, a.vz);
            if (speed < 2.6f) continue;
            if (a instanceof Npc n && (n.reacting || !n.loaded)) continue;
            Double last = slippedAt.get(a.id);
            if (last != null && g.time - last < 8) continue;
            for (Evidence e : evidence) {
                if (e.kind != Kind.BLOOD || g.time - e.wetAt > WET || e.size < 0.35f) continue;
                if (Math.hypot(e.x - a.x, e.z - a.z) > e.size * 0.55f + 0.15f || Math.abs(e.y - a.y) > 0.6f) continue;
                float agility = a.skills.level(rpg.skill.Skill.AGILITY);
                float chance = 0.05f * (speed / 4f) * Math.min(2, e.size) * (1 - agility / 140f) * (a.sneaking ? 0.3f : 1);
                if (rnd.nextFloat() >= chance) continue;
                slippedAt.put(a.id, g.time);
                slip(a, e);
                break;
            }
        }
    }

    /** Makes someone slip in the nearest wet blood (a fresh pool under them if there is none): the test's way in. */
    public void slipNow(Actor a) {
        Evidence best = null;
        for (Evidence e : evidence) if (e.kind == Kind.BLOOD && (best == null || Math.hypot(e.x - a.x, e.z - a.z) < Math.hypot(best.x - a.x, best.z - a.z))) best = e;
        if (best == null || Math.hypot(best.x - a.x, best.z - a.z) > 2) best = evidence(Kind.BLOOD, a.x, a.y, a.z, 1.2f, 0, 0);
        slippedAt.put(a.id, g.time);
        slip(a, best);
    }

    private void slip(Actor a, Evidence e) {
        slips++;
        float speed = Math.max(0.1f, (float) Math.hypot(a.vx, a.vz)), dx = a.vx / speed, dz = a.vz / speed;
        g.state.commit("Slipped", a.id, 0, DevMap.jurisdiction(a.x, a.z), 0, String.format("in blood at (%.1f, %.1f), %.1f m/s", e.x, e.z, speed));
        // the pool smears where the feet went
        stain(a.x + dx * 0.5f, e.y, a.z + dz * 0.5f, 0, 1, 0, e.size * 0.7f);
        if (g.sfx != null) g.sfx.play("soft.heavy", a.x, a.y + 0.3f, a.z, 0.55f);
        if (a instanceof Npc n && g.reactions != null) {
            // the feet go forward along the run, the body backward: Sydney takes it from there
            g.ai.say(n, "Whoa-!");
            float[] feet = {a.x, a.y + 0.08f, a.z};
            g.reactions.struck(n, dx, 0.15f, dz, 70 + speed * 10, 40, feet[0], feet[1], feet[2]);
            if (n.ragdoll != 0) g.game.commands().ragdollControl(n.ragdoll, AstrattoNative.CONTROL_COLLAPSE, 1);
        } else if (a == g.player) {
            a.cs.staggered = Math.max(a.cs.staggered, 0.6f);
            a.vx *= 0.3f; a.vz *= 0.3f;
            if (g.fx != null) g.fx.shake = Math.min(1, g.fx.shake + 0.35f);
            g.chat.say("You slip in the blood!");
        }
    }

    // ------------------------------------------------------------------ each tick
    public void update(float dt) {
        slipCheck();
        // bleeding: life runs out of a body that bleeds; it drips as it goes, and clots in time unless a part is gone
        for (Iterator<Map.Entry<Long, Wounds>> it = wounds.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Actor a = g.actor(e.getKey());
            Wounds w = e.getValue();
            if (a == null) { it.remove(); continue; }
            if (w.bleed <= 0.005f) { w.bleed = 0; continue; }
            if (a.alive && g.law != null && g.law.inCustody(a)) { w.bleed = 0; w.spurtUntil = 0; continue; }   // held: dressed
            if (a.alive && !a.dummy) {
                // the player bleeds to a lesser degree, and a wound alone never finishes them (a blow does)
                boolean player = a == g.player;
                a.hp -= w.bleed * a.maxHp() * (player ? 0.006f : 0.018f) * dt;
                if (player) a.hp = Math.max(1, a.hp);
                else if (a.hp <= 0) { a.hp = 0; bledOut++; g.combat.bleedOut(a, w.by); }
            }
            w.bleed *= (float) Math.exp(-dt / (w.any() ? 140f : a.alive ? 28f : 50f));
            if (w.spurtUntil > g.time && g.time >= w.nextSpurt) { w.nextSpurt = g.time + 0.85 + rnd.nextFloat() * 0.25; spurt(a, w); }
            if (g.time >= w.nextDrip) {
                // a drip: the faster the bleeding the more often, a pool where it falls (a trail behind someone walking)
                w.nextDrip = g.time + Math.max(0.25, 2.2 / (0.2 + w.bleed * 3));
                if (g.fx != null) g.fx.drip(a.x, a.y + (a.alive ? 0.9f : 0.2f), a.z);
                pool(a.x, a.z, a.alive ? 0.12f + w.bleed * 0.1f : 0.25f + w.bleed * 0.3f, a.id, a.cs.lastAttacker);
                // and on the body: it runs down from the wound (a person's; an animal's coat is not painted)
                if (a.alive && !(a instanceof rpg.animal.Animal) && w.bleed > 0.08f) a.blood.run(bloodPart(w.last), g.time);
            }
        }
        // blood on hands and blades: washed off in water, otherwise it fades slowly
        boolean ageing = g.time >= nextAge;
        if (ageing) nextAge = g.time + 2;
        for (Actor a : g.actors()) {
            if (a.bloodied <= 0 && !a.blood.any()) continue;
            boolean water = g.vox.block((int) Math.floor(a.x), (int) Math.floor(a.y + 0.3f), (int) Math.floor(a.z)) == Blocks.WATER;
            a.bloodied = water ? 0 : Math.max(0, a.bloodied - dt / 400f);
            // blood on the body: water washes it all off; otherwise it dries, darker, and in time flakes away
            if (water) a.blood.wash();
            else if (ageing) a.blood.age(g.time);
        }
        // bodies are evidence while they lie there; evidence goes when what it was is gone (a day for blood)
        for (Npc n : g.npcs) if (!n.alive && n.ragdoll != 0 && !bodyKnown(n.id)) evidence(Kind.BODY, n.x, DevMap.FLOOR, n.z, 1, n.id, n.cs.lastAttacker);
        // a body is found where it lies now, not where it fell (it may have been carried off, thrown, dragged)
        for (Evidence e : evidence) if (e.kind == Kind.BODY && g.actor(e.source) instanceof Npc n && n.ragdoll != 0) {
            var pose = g.lastBodies.ragdoll(n.ragdoll);
            if (pose != null) { e.x = pose.comX; e.z = pose.comZ; }
        }
        evidence.removeIf(e -> e.kind == Kind.BLOOD && g.time - e.at > 1440 / Math.max(0.01, g.cfg.minutesPerSecond)
                || e.kind == Kind.BODY && !(g.actor(e.source) instanceof Npc n && !n.alive && n.ragdoll != 0)
                || e.kind == Kind.LIMB && g.time - e.at > 1800);
        // limbs that have settled far away or too long: let go (they stay drawn until then)
        while (limbs.size() > 24) { g.game.commands().release(limbs.get(0).body); limbs.remove(0); }
    }

    private double nextAge;

    /** The figure part a wound's blood runs on. */
    private static int bloodPart(Part p) {
        return switch (p) {
            case HEAD -> rpg.actor.Blood.HEAD; case ARM_R -> rpg.actor.Blood.R_ARM; case ARM_L -> rpg.actor.Blood.L_ARM;
            case LEG_R -> rpg.actor.Blood.R_LEG; case LEG_L -> rpg.actor.Blood.L_LEG; default -> rpg.actor.Blood.TORSO;
        };
    }

    private boolean bodyKnown(long id) { for (Evidence e : evidence) if (e.kind == Kind.BODY && e.source == id) return true; return false; }

    /** The bleeding is stopped (a prisoner's wounds dressed): the marks stay, nothing more runs from them. */
    public void staunch(Actor a) {
        Wounds w = wounds.get(a.id);
        if (w != null) { w.bleed = 0; w.spurtUntil = 0; }
    }

    /** A body is revived (a bandit back at camp): its wounds and lost parts heal. */
    public void healed(Actor a) {
        wounds.remove(a.id); a.injury = null; a.blood.clear(); a.bloodied = 0;
        limbs.removeIf(limb -> { if (limb.owner != a.id) return false; g.game.commands().release(limb.body); return true; });
        evidence.removeIf(e -> e.source == a.id && e.kind == Kind.LIMB);
    }

    // ------------------------------------------------------------------ finding it
    /**
     * What an NPC sees of the evidence around it, and what it does about it (called as it thinks). In its eyes' cone, a
     * clear line to it, and light enough to make it out (a body is easier to see than a stain); each thing once.
     */
    public void perceive(Npc n) {
        if (!n.alive || !n.loaded) return;
        Set<Long> seen = noticed.computeIfAbsent(n.id, k -> new HashSet<>());
        float fx = (float) -Math.sin(n.yaw), fz = (float) -Math.cos(n.yaw);
        Evidence best = null;
        for (Evidence e : evidence) {
            if (seen.contains(e.id) || e.source == n.id) continue;
            if (e.kind == Kind.BODY && inLawfulCare(e)) continue;
            float dx = e.x - n.x, dz = e.z - n.z, d = (float) Math.hypot(dx, dz);
            float reach = e.kind == Kind.BODY ? 18 : e.kind == Kind.LIMB ? 12 : 6 + e.size * 6;
            if (d > reach) continue;
            if (d > 1.5f && (fx * dx + fz * dz) / d < 0.15f) continue;
            float lit = g.ai.stealth.light(e.x, e.y + 0.2f, e.z);
            if (lit * (e.kind == Kind.BLOOD ? e.size * 2 : 1.5f) < 0.18f) continue;
            if (!g.vox.sight(n.x, n.eyeY(), n.z, e.x, e.y + 0.25f, e.z)) continue;
            seen.add(e.id);
            if (best == null || e.kind.ordinal() > best.kind.ordinal()) best = e;
        }
        if (best == null) return;
        found++;
        // (0.2.2, gamefixes) its own work: the one who struck the blow does not discover it - it stands by why it did it,
        // or it is sorry. Blood it saw spilled (a beast mauling someone before its eyes) is no mystery to look into.
        if (best.culprit == n.id) { ownWork(n, best); return; }
        if (best.kind == Kind.BLOOD && g.actor(best.source) instanceof Actor hurt && g.ai.bloodExplained(n, hurt)) return;
        if (g.carrying.busy(n) || n.goal == rpg.ai.Brain.Goal.COLLECT) return;
        if (n.hunt != null && (n.hunt.source == best.source || n.hunt.target != 0)) return;
        react(n, best);
    }

    /** Who struck the blow behind a person's blood or body (the player's minimap marks only searches over its own deeds). */
    public long culpritOf(long victim) {
        if (victim == 0) return 0;
        for (Evidence e : evidence) if (e.source == victim && e.culprit != 0) return e.culprit;
        return 0;
    }

    /**
     * An NPC comes upon its own work: a body or blood it spilled itself. Whoever it had a reason for - one who struck it
     * first, an outlaw, a known criminal, someone wanted, or the one it was set on fighting - it stands by ("had it
     * coming"); anyone else it is shaken by, and it does not linger (no cry of murder, no search for a culprit).
     */
    private void ownWork(Npc n, Evidence e) {
        if (e.kind == Kind.BLOOD) return;
        Actor v = g.actor(e.source);
        String name = v == null ? "them" : v.name;
        boolean struck = v != null && n.cs.lastAttacker == v.id;
        boolean outlaw = v instanceof Npc o && (o.role == Npc.Role.BANDIT || o.role == Npc.Role.BRUTE || o.role == Npc.Role.THIEF || o.hostileToPlayer);
        boolean criminal = v != null && (n.crimeKnown(v.id) != null || g.law.bounty(v.id, n.homeJurisdiction) > 0);
        boolean foe = v != null && n.fightTarget == v.id;
        boolean law = n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN;
        String line;
        if (struck) line = pick(n, name + " came at me first. I did what I had to.", "They swung first. Nobody can say otherwise.");
        else if (criminal) line = law ? pick(n, "The law's been served. " + name + " knew what was coming.", name + " had a price on them. It's done.") : pick(n, name + " had it coming, after what they did.", "Justice, of a kind.");
        else if (outlaw) line = pick(n, "One less outlaw on the road.", "That's what comes of living by the blade.");
        else if (foe) line = pick(n, name + " wouldn't back down. It didn't have to end like this.", "I'd do it again. I think.");
        else line = pick(n, "Gods... what have I done?", "I didn't mean - " + name + "... no.", "Forgive me. Forgive me.");
        g.ai.say(n, line);
        n.lastOutcome = struck || criminal || outlaw || foe ? "stood over " + name + ", justified" : "shaken over " + name + ", guilty";
        g.state.commit("OwnWork", n.id, e.source, DevMap.jurisdiction(e.x, e.z), 0, n.lastOutcome);
    }

    private static String pick(Npc n, String... lines) { return lines[(int) Math.floorMod(n.id * 31 + (long) (n.conversations), lines.length)]; }

    /**
     * A body already in the law's hands is no new scene: one the watch or the undertaker is carrying, or one laid in a
     * graveyard (gamefixes: guards took up the bodies they had just delivered as fresh murders).
     */
    private boolean inLawfulCare(Evidence e) {
        if (!(g.actor(e.source) instanceof Npc dead)) return false;
        if (g.actor(dead.carriedBy) instanceof Npc carrier && (carrier.role == Npc.Role.GUARD || carrier.role == Npc.Role.LAWMAN || carrier.role == Npc.Role.UNDERTAKER)) return true;
        return e.x >= DevMap.GRAVE_X0 && e.x <= DevMap.GRAVE_X1 + 1 && e.z >= DevMap.GRAVE_Z0 - 1 && e.z <= DevMap.GRAVE_Z1 + 1;
    }

    /**
     * Whether the blood or body is no crime to look into: an outlaw's (a bandit, a brute), an animal's, the player's own
     * (who comes back from the dead), or the law's own blood from a fight the law was in (it knows its own fights; a dead
     * guard is still a murder).
     */
    private boolean lawful(Evidence e) {
        Actor v = g.actor(e.source);
        boolean lawHurt = v instanceof Npc o && o.alive && (o.role == Npc.Role.GUARD || o.role == Npc.Role.LAWMAN) && e.kind == Kind.BLOOD;
        return lawHurt || v == g.player || v instanceof rpg.animal.Animal || v instanceof rpg.monster.Monster || v instanceof Npc o && o.hostileToPlayer && (o.role == Npc.Role.BANDIT || o.role == Npc.Role.BRUTE);
    }

    /** Follow only a fresh visible stain belonging to the searched body/person; provenance never identifies its attacker. */
    public void followTrail(Npc n) {
        Hunt hunt=n.hunt;if(hunt==null)return;
        long source=hunt.target!=0?hunt.target:hunt.source;if(source==0)return;
        Evidence clue=null;
        for(Evidence stain:evidence)if(stain.kind==Kind.BLOOD && stain.source==source && stain.id>hunt.lastTrail && g.time-stain.at<20
                && Math.hypot(stain.x-n.x,stain.z-n.z)<8 && g.vox.sight(n.x,n.eyeY(),n.z,stain.x,stain.y+0.1f,stain.z)
                && (clue==null || stain.id>clue.id))clue=stain;
        if(clue!=null) { hunt.lastTrail=clue.id;hunt.clue(clue.x,clue.z,0,0,g.time);n.lastOutcome="following a visible blood trail"; }
    }

    private void react(Npc n, Evidence e) {
        String jur = DevMap.jurisdiction(e.x, e.z);
        for(var memory:n.memories) if(memory.kind.equals("BEAST_ATTACK") && memory.victim==e.source) {
            g.ai.say(n,"An animal did this. Keep away from its trail.");
            if(n.combatant() && n.hunt==null) { n.hunt=new Hunt(memory.subject,"witnessed a beast attack",e.x,e.z,g.time,40);n.nextThink=0; }
            return;
        }
        if(n.role==Npc.Role.BANDIT || n.role==Npc.Role.BRUTE) {
            if(g.actor(e.source) instanceof Npc victim && victim.team==n.team && n.hunt==null) {
                n.hunt=new Hunt(0,"found a fallen ally",e.x,e.z,g.time,40);n.hunt.source=e.source;n.nextThink=0;g.ai.say(n,"Someone got one of ours. Spread out.");
            }
            return;
        }
        String what = e.kind == Kind.BODY ? "a body" : e.kind == Kind.LIMB ? "a severed limb" : "blood";
        g.state.commit("EvidenceFound", n.id, e.source, jur, 0, what + String.format(" at (%.1f, %.1f)", e.x, e.z));
        // an outlaw's end is remarked on, not investigated
        if (lawful(e)) {
            if (e.kind != Kind.BLOOD) {
                boolean beast = g.actor(e.source) instanceof rpg.monster.Monster;
                g.ai.say(n, beast ? (n.combatant() ? "One less of those things." : "Ugh - what is that? Is it dead?") : n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN ? "One less bandit on the road." : "Gods, is that a bandit?");
            }
            return;
        }
        boolean law = n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN;
        if (law) {
            // the law goes to look, and searches for whoever did it
            if (n.hunt == null || n.hunt.target == 0) {
                Hunt h = new Hunt(0, "found " + what, e.x, e.z, g.time, e.kind == Kind.BLOOD ? 35 : 60);
                h.source = e.source;
                h.clueAt = g.time - (e.kind == Kind.BLOOD ? Math.min(20, g.time - e.at) : 0);
                n.hunt = h;
                n.nextThink = 0;
            }
            g.ai.say(n, e.kind == Kind.BODY ? "Someone's been killed here!" : e.kind == Kind.LIMB ? "Gods... what happened here?" : "Blood. Fresh.");
            // a body brings the rest of the force
            if (e.kind != Kind.BLOOD) for (Npc o : g.npcs) if (o != n && o.alive && o.loaded && o.role == Npc.Role.GUARD && o.hunt == null && o.distTo(n) < 40) {
                Hunt j = new Hunt(0, n.name + " found " + what, e.x, e.z, g.time, 60);
                j.joined = true;
                o.hunt = j; o.nextThink = 0;
            }
            return;
        }
        // anyone else: cries out, and goes to tell the law (a report with no culprit sends the guards to search)
        if (e.kind == Kind.BLOOD && e.size < 0.35f) { g.ai.say(n, "Is that... blood?"); n.wantYaw = (float) Math.atan2(-(e.x - n.x), -(e.z - n.z)); return; }
        // one incident, one report: more of the same person's blood (a trail, the pool growing) is not a new crime to run
        // to the law about - only a body found where only blood was known is news
        for (Memory m : n.memories)
            if (m.kind.equals("MURDER") && m.subject == 0 && m.victim == e.source && (e.kind != Kind.BODY || m.detail.endsWith("a body")) && g.state.minutes - m.minutes < 240) return;
        g.ai.say(n, e.kind == Kind.BODY ? "Murder! Somebody fetch the guards!" : "Help! Guards!");
        n.remember(new Memory("MURDER", 0, e.source, jur, g.state.minutes, 0.8f, "witnessed", e.id, 0, "found " + what), 24);
        n.nextThink = 0;
        n.cs.lastHurt = Math.max(n.cs.lastHurt, g.time - 10);
    }

    /**
     * A searcher for an unknown culprit sees someone: blood on them, close to fresh blood or a fresh body - a suspect.
     * Returns true when it accuses them (a witnessed murder memory: the law takes it from there).
     */
    public boolean suspect(Npc n, Actor a) {
        if (a.bloodied < 0.25f) return false;
        for (Evidence e : evidence) {
            if (e.kind == Kind.BLOOD && e.size < 0.3f) continue;
            if (lawful(e) || e.source == a.id || g.law.settled(a.id, e.at)) continue;   // (nobody is a suspect for their own blood)
            if (g.time - e.at > 90 || Math.hypot(e.x - a.x, e.z - a.z) > 10) continue;
            long victim = e.source;
            n.remember(new Memory("MURDER", a.id, victim, DevMap.jurisdiction(e.x, e.z), g.state.minutes, 0.9f, "witnessed", e.id, 0, "caught bloodied by " + (e.kind == Kind.BODY ? "a body" : "the blood")), 24);
            g.ai.say(n, "You there - that's blood on you! Stop!");
            g.state.commit("SuspectSpotted", n.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, String.format("bloodied %.2f near %s", a.bloodied, e.kind));
            if (a == g.player) g.chat.crime("bloodied", n.name + " saw blood on you near the scene.");
            return true;
        }
        return false;
    }

    public void clear() {
        for (Limb l : limbs) g.game.commands().release(l.body);
        limbs.clear(); evidence.clear(); stains.clear(); wounds.clear(); noticed.clear();
    }
}
