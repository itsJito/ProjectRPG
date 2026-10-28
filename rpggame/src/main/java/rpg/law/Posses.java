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
 *   {@code posseMinutes} to ten more minutes of play, from a sighting actually communicated to the watch.</li>
 *   <li>The border: a fresh communicated heading can lead to an ambush on the projected route out.</li>
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
            // An ambush can be planned from a communicated heading, never the unseen player's current motion.
            var known = briefing(k);
            if (b >= g.cfg.ambushBounty && known != null && g.time - known.at() < 90
                    && k.equals(DevMap.jurisdiction(known.x(), known.z())) && g.time >= nextAmbush.getOrDefault(k, 0.0) && !active(k)) {
                float speed = (float) Math.hypot(known.vx(), known.vz());
                if (speed > 1.2f && !k.equals(DevMap.jurisdiction(known.x() + known.vx() / speed * 14, known.z() + known.vz() / speed * 14))) {
                    nextAmbush.put(k, g.time + 600);
                    if (unit() < 0.5f) form(k, b, true);
                }
            }
        }
        tend();
    }

    /** Face-to-face communication retains the original eyewitness and observation time. */
    public boolean tell(Npc from, Npc to) {
        if (from == to || !from.alive || !to.alive || !from.loaded || !to.loaded || from.asleep() || to.asleep()
                || from.distTo(to) > 6 || !g.vox.sight(from.x, from.eyeY(), from.z, to.x, to.eyeY(), to.z)) return false;
        var sighting = from.playerSighting;
        if (sighting == null || g.time - sighting.at() > 600 || to.playerSighting != null && to.playerSighting.at() >= sighting.at()) return false;
        to.playerSighting = sighting;
        g.state.commit("SightingShared", from.id, to.id, to.homeJurisdiction, 0,
                "eyewitness " + sighting.witness() + " at (" + sighting.x() + ", " + sighting.z() + ")");
        return true;
    }

    private Npc.Sighting briefing(String kingdom) {
        Npc officer = g.ai.lawmanFor(kingdom);
        if (officer == null) return null;
        for (Npc neighbor : g.npcs) tell(neighbor, officer);
        var known = officer.playerSighting;
        return known != null && g.time - known.at() <= 600 ? known : null;
    }

    /** Sends a posse of the kingdom's hunters after the player. Returns false when nowhere suitable was found. */
    boolean form(String kingdom, int bounty, boolean ambush) {
        var p = g.player;
        // a tracking posse starts from what the kingdom knows: the newest sighting, or the watch's post when nobody has
        // seen the quarry since the crime (they do not know where the player is now)
        var report = briefing(kingdom);
        Npc officer = g.ai.lawmanFor(kingdom);
        if (officer == null) return false;
        if (ambush && report == null) return false;
        float[] known = report == null ? new float[]{officer.postX, officer.postZ, (float)g.time} : new float[]{report.x(), report.z(), (float)report.at()};
        float[] at = ambush ? ambushPoint(report) : trackingPoint(p, known);
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
            n.fitBody(); n.restore(); n.yaw = (float) Math.atan2(-(known[0] - n.x), -(known[1] - n.z));
            g.npcs.add(n); g.ai.spawnBody(n);
            n.hunt = new Hunt(p.id, "a bounty of " + bounty + " G in " + kingdom, known[0], known[1], known[2], ambush ? 240 : 600);
            n.playerSighting = report;
            n.hunt.source = report == null ? 0 : report.witness();
            if (lead == null) lead = n;
        }
        for (int i = 0; i < m.dogs() && lead != null; i++) {
            Animal dog = g.animals.spawnNew(Species.DOG, false, i % 2 == 0, lead.x + 0.8f + i * 0.6f, lead.z - 0.8f, null, 0, 0);
            dog.owner = lead.id; dog.trust.put(lead.id, 1f);
        }
        formed++;
        g.state.commit("PosseFormed", lead == null ? 0 : lead.id, p.id, kingdom, 0, (ambush ? "border ambush" : "tracking") + ": " + m.hunters() + " hunters, " + m.dogs() + " dogs, bounty " + bounty);
        if (ambush) g.chat.crime("ambush", "Someone is waiting on the road out of " + kingdom + ".");
        else g.chat.crime("hunters", "Bounty hunters from " + kingdom + " are on your trail.");
        return true;
    }

    /** Hunters keep after the player (tracking posses know roughly where they are); a posse with no business goes home. */
    private void tend() {
        var p = g.player;
        for (Npc n : members()) {
            if (!n.alive) continue;
            boolean wanted = law.bounty(p.id, n.homeJurisdiction) > 0 && p.alive && !law.inCustody(p);
            float d = n.distTo(p);
            if (!wanted || n.hunt != null && n.hunt.expired(g.time)) {
                double since = lostSince.computeIfAbsent(n.id, k -> g.time);
                if (!wanted && (d > 30 || !g.ai.canSee(n, p)) || g.time - since > 90) { disband(n); continue; }
            } else lostSince.remove(n.id);
            if (!wanted) { n.hunt = null; n.fightTarget = 0; continue; }
            boolean sees = g.ai.canSee(n, p);
            // Ask people actually met on the search. An isolated witness cannot update this hunter.
            for (Npc neighbor : g.npcs) tell(neighbor, n);
            var word = n.playerSighting;
            if (n.hunt == null) {
                if (word == null && !sees) continue;
                n.hunt = sees ? new Hunt(p.id, "a bounty in " + n.homeJurisdiction, p.x, p.z, g.time, 600)
                        : new Hunt(p.id, "sighting by " + g.nameOf(word.witness()), word.x(), word.z(), word.at(), 600);
            }
            if (sees) {
                n.playerSighting = new Npc.Sighting(n.id, p.x, p.z, p.vx, p.vz, g.time);
                n.hunt.source = n.id; n.hunt.clue(p.x, p.z, p.vx, p.vz, g.time);
                if (d < 28) { n.fightTarget = p.id; n.nextThink = 0; }
            } else if (word != null && word.at() > n.hunt.clueAt) {
                n.hunt.source = word.witness();
                n.hunt.clue(word.x(), word.z(), word.vx(), word.vz(), word.at());
            }

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
            if (!open(x, z) || seen(p, x, z)) continue;
            return new float[]{x, z};
        }
        return null;
    }

    /** Just over the border ahead of a fleeing player, a little off their line. */
    private float[] ambushPoint(Npc.Sighting p) {
        float speed = (float) Math.max(0.01, Math.hypot(p.vx(), p.vz())), dx = p.vx() / speed, dz = p.vz() / speed;
        for (int tries = 0; tries < 24; tries++) {
            float ahead = 18 + unit() * 10, side = (unit() - 0.5f) * 10;
            float x = p.x() + dx * ahead - dz * side, z = p.z() + dz * ahead + dx * side;
            if (open(x, z) && !seen(g.player, x, z)) return new float[]{x, z};
        }
        return null;
    }

    /**
     * Whether the player would see a party appear there (gamefixes: hunters stepped out of thin air in front of them):
     * anywhere in the half of the world the camera faces, near enough to matter, or anywhere in plain sight.
     */
    private boolean seen(rpg.actor.Actor p, float x, float z) {
        float dx = x - p.x, dz = z - p.z, d = (float) Math.hypot(dx, dz);
        if (d < 24) return true;
        float yaw = p == g.player && g.controller != null ? g.controller.camYaw : p.yaw;
        float fx = (float) -Math.sin(yaw), fz = (float) -Math.cos(yaw);
        if ((dx * fx + dz * fz) / d > -0.15f && d < 70) return true;
        return g.vox.sight(p.x, p.eyeY(), p.z, x, DevMap.FLOOR + 1.6f, z);
    }

    private boolean open(float x, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (bx < 2 || bz < 2 || bx >= DevMap.SX - 2 || bz >= DevMap.SZ - 2) return false;
        return g.vox.block(bx, DevMap.FLOOR, bz) == Blocks.AIR && g.vox.block(bx, DevMap.FLOOR + 1, bz) == Blocks.AIR && Blocks.traversable(g.vox.block(bx, DevMap.FLOOR - 1, bz));
    }

    void clear() { nextTracking.clear(); nextAmbush.clear(); lostSince.clear(); check = 0; formed = 0; }
}
