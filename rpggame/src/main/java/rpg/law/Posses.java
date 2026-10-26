package rpg.law;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.ai.Hunt;
import rpg.ai.Npc;
import rpg.ai.Profession;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * Bounty hunters (gamefixes). A kingdom's bounty draws hunters from that kingdom, and hunters are not the watch: they
 * cross borders. Two ways they come:
 * <ul>
 *   <li>Tracking: once a kingdom's bounty passes {@code posseBounty}, a posse sets out after the offender every
 *   {@code posseMinutes} to ten more minutes of play, from wherever the kingdom's people last saw them.</li>
 *   <li>The border: someone fleeing a kingdom with {@code ambushBounty} or more may find hunters waiting just over the
 *   line, on the road they are taking.</li>
 * </ul>
 * The posse scales with the bounty (1-29 G one hunter with a bow; 30-99 two with crossbows; 100-199 three; 200-399
 * four or five with swords and crossbows and one or two law dogs; 400-499 three or four; 500 and more five or six with
 * three or four law dogs). Hunting or killing a hunter adds nothing to a bounty. A posse whose quarry is no longer
 * wanted, or who lost them long ago, goes home (leaves the world with what it carried).
 *
 * <p>Hunters know only what someone could have told them or what they find themselves (gamefixes: they used to be
 * handed the quarry's true position every few seconds): word of the newest sighting by the kingdom's own people
 * reaches them a little after it happens; their own eyes; and their law dogs, which pick up the quarry's scent where
 * it is fresh and lead along it. Between clues they search the last known area like any other search ({@link Hunt}).
 */
public final class Posses {
    private final Rpg g;
    private final Law law;
    private final Map<String, Double> nextTracking = new HashMap<>(), nextAmbush = new HashMap<>();
    private final Map<Long, Double> lostSince = new HashMap<>();
    /** The quarry's scent: where they walked, a point every two seconds for the last two minutes. */
    private final java.util.ArrayDeque<float[]> scent = new java.util.ArrayDeque<>();
    private double scentAt;
    /** How long word of a sighting takes to reach hunters in the field, how long a trail stays fresh, and how far a dog's nose finds it. */
    static final double WORD_DELAY = 15, SCENT_LIFE = 120;
    static final float NOSE = 12;
    private double check;
    private long seed = 0x5EEDL;
    public int formed;

    Posses(Rpg g, Law law) { this.g = g; this.law = law; }

    /** One posse's make-up, by the bounty (the table above). */
    record Makeup(int hunters, boolean bows, boolean swords, int dogs) {}
    Makeup makeup(int bounty) {
        if (bounty < 30) return new Makeup(1, true, false, 0);
        if (bounty < 100) return new Makeup(2, false, false, 0);
        if (bounty < 200) return new Makeup(3, false, false, 0);
        if (bounty < 400) return new Makeup(4 + roll(2), false, true, 1 + roll(2));
        if (bounty < 500) return new Makeup(3 + roll(2), false, true, 0);
        return new Makeup(5 + roll(2), false, true, 3 + roll(2));
    }

    private int roll(int n) { seed = seed * 6364136223846793005L + 1442695040888963407L; return (int) Math.floorMod(seed >>> 33, n); }
    private float unit() { return roll(10000) / 10000f; }

    public List<Npc> members() { List<Npc> l = new ArrayList<>(); for (Npc n : g.npcs) if (n.posse) l.add(n); return l; }
    private boolean active(String kingdom) { for (Npc n : g.npcs) if (n.posse && n.alive && n.homeJurisdiction.equals(kingdom)) return true; return false; }

    void update() {
        if (g.time < check) return;
        check = g.time + 1;
        var p = g.player;
        for (String k : new String[]{DevMap.ALPHA, DevMap.BETA}) {
            int b = law.bounty(p.id, k);
            // tracking posses, every 30-40 minutes past the threshold (the first a few minutes after it is crossed)
            if (b > g.cfg.posseBounty && p.alive && !law.inCustody(p)) {
                Double due = nextTracking.get(k);
                if (due == null) nextTracking.put(k, g.time + 120 + unit() * 180);
                else if (g.time >= due && !active(k)) {
                    if (form(k, b, false)) nextTracking.put(k, g.time + (g.cfg.posseMinutes + unit() * 10) * 60);
                    else nextTracking.put(k, g.time + 20);
                }
            } else nextTracking.remove(k);
            // the border: fleeing the kingdom where the bounty is
            if (b >= g.cfg.ambushBounty && p.alive && k.equals(DevMap.jurisdiction(p.x, p.z)) && g.time >= nextAmbush.getOrDefault(k, 0.0) && !active(k)) {
                float speed = (float) Math.hypot(p.vx, p.vz);
                if (speed > 1.2f) {
                    float ax = p.x + p.vx / speed * 14, az = p.z + p.vz / speed * 14;
                    if (!k.equals(DevMap.jurisdiction(ax, az))) {
                        // "sometimes": half the times someone heads out, once every ten minutes at most
                        nextAmbush.put(k, g.time + 600);
                        if (unit() < 0.5f) form(k, b, true);
                    }
                }
            }
        }
        if (g.time >= scentAt && p.alive) {
            scentAt = g.time + 2;
            scent.addLast(new float[]{p.x, p.z, (float) g.time});
            while (!scent.isEmpty() && g.time - scent.peekFirst()[2] > SCENT_LIFE) scent.removeFirst();
        }
        tend();
    }

    /**
     * The newest sighting of the player by anyone of the kingdom (its people, its watch, its hunters) made before a
     * time: {x, z, when}, or null when nobody has seen them.
     */
    float[] lastSighting(String kingdom, double before) {
        float[] best = null;
        for (Npc witness : g.npcs) {
            if (!witness.alive || !kingdom.equals(witness.homeJurisdiction)) continue;
            var aware = witness.aware.get(g.player.id);
            if (aware == null || aware.seenAt < 0 || aware.seenAt > before) continue;
            if (best == null || aware.seenAt > best[2]) best = new float[]{aware.x, aware.z, (float) aware.seenAt};
        }
        return best;
    }

    /**
     * What a hunter's dogs smell: the freshest point of the quarry's trail within a nose's reach of one of the dogs,
     * followed on along the line as far as the nose reaches, as {x, z, when}; null with no dog near a fresh trail.
     */
    private float[] dogsScent(Npc hunter) {
        float[] best = null;
        for (Animal dog : g.animals.all()) {
            if (!dog.alive || dog.owner != hunter.id) continue;
            for (float[] pt : scent) if (Math.hypot(pt[0] - dog.x, pt[1] - dog.z) < NOSE && (best == null || pt[2] > best[2])) best = pt;
        }
        if (best == null) return null;
        float[] at = best;
        for (float[] pt : scent) if (pt[2] > at[2] && Math.hypot(pt[0] - at[0], pt[1] - at[1]) < NOSE) at = pt;
        return at;
    }

    /** Sends a posse of the kingdom's hunters after the player. Returns false when nowhere suitable was found. */
    boolean form(String kingdom, int bounty, boolean ambush) {
        var p = g.player;
        // a tracking posse starts from what the kingdom knows: the newest sighting, or the watch's post when nobody has
        // seen the quarry since the crime (they do not know where the player is now)
        float[] known = lastSighting(kingdom, g.time);
        if (known == null) { Npc lawman = g.ai.lawmanFor(kingdom); if (lawman == null) return false; known = new float[]{lawman.postX, lawman.postZ, -99}; }
        float[] at = ambush ? ambushPoint(p) : trackingPoint(p, known);
        if (at == null) return false;
        Makeup m = makeup(bounty);
        int level = Math.min(45, 12 + bounty / 40);
        Npc lead = null;
        for (int i = 0; i < m.hunters(); i++) {
            float ox = (i % 3 - 1) * 1.3f, oz = (i / 3) * 1.3f;
            if (!open(at[0] + ox, at[1] + oz)) { ox = 0; oz = 0; }
            Npc n = new Npc(g.state.newEntityId(), "Bounty hunter", Npc.Role.HUNTER, "Bounty Hunter", at[0] + ox, at[1] + oz);
            Profession.BOUNTY_HUNTER.assign(n);
            n.homeJurisdiction = kingdom; n.posse = true; n.hostileToPlayer = false; n.aggroRange = 30;
            n.skills.setLevel(Skill.ATTACK, level); n.skills.setLevel(Skill.STRENGTH, level); n.skills.setLevel(Skill.DEFENSE, level);
            n.skills.setLevel(Skill.HEALTH, Math.max(12, level)); n.skills.setLevel(Skill.ARCHERY, level);
            n.look = rpg.actor.Appearance.random(n.id, false);
            boolean melee = m.swords() && i % 2 == 1;
            if (m.bows()) { wear(n, Items.SHORTBOW); wear(n, Items.BRONZE_ARROWS, 30); }
            else if (melee) wear(n, Items.get("iron_sword"));
            else { wear(n, Items.get("iron_crossbow")); wear(n, Items.get("iron_bolts"), 20); }
            wear(n, Items.get("stone_body"));
            n.fitBody(); n.restore(); n.yaw = (float) Math.atan2(-(p.x - n.x), -(p.z - n.z));
            g.npcs.add(n); g.ai.spawnBody(n);
            n.hunt = new Hunt(p.id, "a bounty of " + bounty + " G in " + kingdom, known[0], known[1], Math.max(known[2], g.time - 60), ambush ? 240 : 600);
            if (ambush) { n.hunt.x = n.x; n.hunt.z = n.z; }
            if (lead == null) lead = n;
        }
        for (int i = 0; i < m.dogs() && lead != null; i++) {
            Animal dog = g.animals.spawnNew(Species.DOG, false, i % 2 == 0, lead.x + 0.8f + i * 0.6f, lead.z - 0.8f, null, 0, 0);
            dog.owner = lead.id; dog.trust.put(lead.id, 1f);
        }
        formed++;
        g.state.commit("PosseFormed", lead == null ? 0 : lead.id, p.id, kingdom, 0, (ambush ? "border ambush" : "tracking") + ": " + m.hunters() + " hunters, " + m.dogs() + " dogs, bounty " + bounty);
        if (ambush) g.chat.add("[Crime] Someone is waiting on the road out of " + kingdom + ".", rpg.Chat.Kind.CRIME);
        else g.chat.add("[Crime] Bounty hunters from " + kingdom + " are on your trail.", rpg.Chat.Kind.CRIME);
        return true;
    }

    /** Hunters keep after the player (tracking posses know roughly where they are); a posse with no business goes home. */
    private void tend() {
        var p = g.player;
        for (Npc n : members()) {
            if (!n.alive) continue;
            boolean wanted = law.bounty(p.id, n.homeJurisdiction) > 0 && p.alive && !law.inCustody(p);
            float d = n.distTo(p);
            if (!wanted || d > 110) {
                double since = lostSince.computeIfAbsent(n.id, k -> g.time);
                if (!wanted && (d > 30 || !g.ai.canSee(n, p)) || g.time - since > 90) { disband(n); continue; }
            } else lostSince.remove(n.id);
            if (!wanted) { n.hunt = null; n.fightTarget = 0; continue; }
            boolean sees = g.ai.canSee(n, p);
            if (n.hunt == null) {
                float[] known = lastSighting(n.homeJurisdiction, g.time);
                if (known == null && !sees) continue;
                n.hunt = sees ? new Hunt(p.id, "a bounty in " + n.homeJurisdiction, p.x, p.z, g.time, 600) : new Hunt(p.id, "a bounty in " + n.homeJurisdiction, known[0], known[1], known[2], 600);
            }
            // their own eyes: the quarry is in sight
            if (sees) { n.hunt.clue(p.x, p.z, p.vx, p.vz, g.time); if (d < 28) { n.fightTarget = p.id; n.nextThink = 0; } continue; }
            // word from the kingdom: someone saw them more recently than this hunter's last clue (an ambusher waits for it)
            boolean tracking = n.hunt.patience > 300;
            float[] word = lastSighting(n.homeJurisdiction, g.time - WORD_DELAY);
            if (tracking && word != null && word[2] > n.hunt.clueAt + 1) n.hunt.clue(word[0], word[1], 0, 0, word[2]);
            // the dogs' noses: a fresh stretch of the trail near one of their dogs leads on along it
            float[] trail = dogsScent(n);
            if (trail != null && trail[2] > n.hunt.clueAt + 1) n.hunt.clue(trail[0], trail[1], 0, 0, trail[2]);
        }
    }

    /** A hunter leaves the world (alive): what they carried goes with them, and their dogs follow. */
    private void disband(Npc n) {
        lostSince.remove(n.id);
        for (var s : new ArrayList<>(n.inv.stacks())) g.state.burn(s.def, s.amount);
        for (var s : new ArrayList<>(n.eq.stacks())) g.state.burn(s.def, s.amount);
        n.inv.clear();
        for (var slot : ItemDef.Slot.values()) n.eq.set(slot, null);
        for (Animal a : new ArrayList<>(g.animals.all())) if (a.owner == n.id && a.home == null) g.animals.remove(a);
        g.magic.dismiss(n);
        g.state.commit("PosseDisbanded", n.id, g.player.id, n.homeJurisdiction, 0, "went home");
    }

    private void wear(Npc n, ItemDef d) { wear(n, d, 1); }
    private void wear(Npc n, ItemDef d, int amount) { n.eq.set(d.slot, new ItemStack(d, amount)); g.state.mint(d, amount); }

    /** Out of the player's sight and on open ground, 25-45 m from where the quarry was last known to be. */
    private float[] trackingPoint(rpg.actor.Actor p, float[] known) {
        for (int tries = 0; tries < 40; tries++) {
            double a = unit() * Math.PI * 2; float r = 25 + unit() * 20;
            float x = known[0] + (float) Math.sin(a) * r, z = known[1] + (float) Math.cos(a) * r;
            if (!open(x, z) || g.vox.sight(p.x, p.eyeY(), p.z, x, DevMap.FLOOR + 1.6f, z)) continue;
            return new float[]{x, z};
        }
        return null;
    }

    /** Just over the border ahead of a fleeing player, a little off their line. */
    private float[] ambushPoint(rpg.actor.Actor p) {
        float speed = (float) Math.max(0.01, Math.hypot(p.vx, p.vz)), dx = p.vx / speed, dz = p.vz / speed;
        for (int tries = 0; tries < 24; tries++) {
            float ahead = 18 + unit() * 10, side = (unit() - 0.5f) * 10;
            float x = p.x + dx * ahead - dz * side, z = p.z + dz * ahead + dx * side;
            if (open(x, z)) return new float[]{x, z};
        }
        return null;
    }

    private boolean open(float x, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (bx < 2 || bz < 2 || bx >= DevMap.SX - 2 || bz >= DevMap.SZ - 2) return false;
        return g.vox.block(bx, DevMap.FLOOR, bz) == Blocks.AIR && g.vox.block(bx, DevMap.FLOOR + 1, bz) == Blocks.AIR && Blocks.traversable(g.vox.block(bx, DevMap.FLOOR - 1, bz));
    }

    void clear() { nextTracking.clear(); nextAmbush.clear(); lostSince.clear(); check = 0; formed = 0; }
}
