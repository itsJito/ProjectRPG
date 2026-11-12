package rpg.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Result;
import rpg.item.ItemDef;
import rpg.item.Items;
import rpg.skill.Skill;

/**
 * (0.3.9, gamefixes: recruitable NPC allies, the squad commands) The player's companions: people of the world - a knight
 * who thinks well of them, a bandit or a brute paid to, a hunter who knows the wilds - who join the player and go where
 * the player goes, up to four at a time. A companion is still themselves: they fight their own way, flee when it is
 * hopeless, and speak for themselves; what changes is whose side they are on and what they are told.
 *
 * <p>Orders are given to one companion at a time (the selected one) or to all: follow (the default), rally (to the
 * player's side at once, dropping whatever they were about), hold a place, move to a place (a ranged one finding an angle
 * to shoot from there, anyone taking cover behind something solid if told to), and attack a foe - which puts that foe
 * first for them without making them deaf to everything else (a blow from another still gets an answer). Each has
 * abilities drawn from what they are and carry, each with its cooldown, that the player can call on against a chosen
 * target.
 */
public final class Companions {
    /** At most this many at a time. */
    public static final int MAX = rpg.Tuning.i("companions.max", 4);
    /** What a companion has been told. */
    public enum Order { FOLLOW, RALLY, HOLD, MOVE, COVER, ATTACK }
    /** What a companion can be called on to do (each only by one able to: see abilities). */
    public enum Ability {
        POWER_STRIKE("Power strike", "A heavy blow at the target", 14),
        TAUNT("Taunt", "Draws the foes round them away from you", 22),
        SHIELD_WALL("Shield wall", "Guard raised, standing firm", 18),
        VOLLEY("Volley", "Shoots the target quickly, three times", 16),
        SPELL("Battle spell", "Their strongest spell at the target", 10),
        MEND("Mend", "Eats or drinks what they carry to recover", 30);
        public final String title, help;
        public final float cooldown;
        Ability(String title, String help, float cooldown) { this.title = title; this.help = help; this.cooldown = rpg.Tuning.f("companions.cooldown." + name(), cooldown); }
    }

    /** What it costs to hire one (G), by what they are; a knight cannot be bought - only won by honour. */
    public static int fee(Npc n) {
        return switch (n.role) { case BANDIT, BRUTE, HUNTER, KNIGHT -> rpg.Tuning.i("companions.fee." + n.role.name(), -1); default -> -1; };
    }
    /** The honour a knight asks of anyone they will follow. */
    public static final int KNIGHT_HONOR = rpg.Tuning.i("companions.knightHonor", 25);
    /** How far a holding companion goes from its post to meet a foe (m). */
    public static final float HOLD_LEASH = rpg.Tuning.f("companions.holdLeash", 7);

    private final Rpg g;
    private final Brain brain;
    Companions(Rpg g, Brain brain) { this.g = g; this.brain = brain; }

    /** The player's companions, in the order they joined (the HUD's order, and the 1-4 keys'). */
    public List<Npc> of(Actor leader) {
        List<Npc> l = new ArrayList<>();
        for (Npc n : g.npcs) if (n.companionOf == leader.id && n.alive) l.add(n);
        l.sort(Comparator.comparingDouble(n -> n.companionSince));
        return l;
    }

    /** Why this person will not join the player now (null: they will). */
    public String refusal(Npc n) {
        Actor p = g.player;
        if (n.companionOf == p.id) return n.name + " is already with you.";
        if (!n.alive || n.asleep()) return n.name + " can't answer you now.";
        if (n.hostileToPlayer || n.fightTarget == p.id) return n.name + " wants you dead, not led.";
        if (n.summonOwner != 0 || n.posse) return n.name + " is bound elsewhere.";
        int fee = fee(n);
        if (fee < 0) return n.name + " has a life of their own to see to.";
        if (of(p).size() >= MAX) return "You already lead " + MAX + ". There is no room for another.";
        if (n.role == Npc.Role.KNIGHT) {
            if (g.honor != null && g.honor.score < KNIGHT_HONOR) return n.name + " will not ride with someone of your name (Honor " + KNIGHT_HONOR + " needed).";
            for (String jur : new String[]{rpg.world.DevMap.ALPHA, rpg.world.DevMap.BETA, rpg.world.DevMap.PALACE}) if (g.law.bounty(p.id, jur) > 0) return n.name + " will not follow a wanted man.";
        } else if (!p.has(Items.COINS, fee)) return n.name + " wants " + fee + " coins up front.";
        return null;
    }

    /** They join: paid, if they are paid; out of their routine and at the player's side from now on. */
    public Result recruit(Npc n) {
        String why = refusal(n);
        if (why != null) return Result.no(why);
        int fee = fee(n);
        if (fee > 0) { for (var c : g.player.takeItems(Items.COINS, fee)) n.store(c); }
        n.companionOf = g.player.id; n.companionSince = g.time; n.order = Order.FOLLOW; n.orderTarget = 0;
        n.hostileToPlayer = false; n.homeTeam = n.team; n.team = g.player.team;
        n.goal = Brain.Goal.FOLLOW; n.nextThink = 0; n.hunt = null; n.investigation = null;
        g.interact.cancel(n, "joined the player");
        g.state.commit("CompanionJoined", n.id, g.player.id, rpg.world.DevMap.jurisdiction(n.x, n.z), 0, n.name + (fee > 0 ? " for " + fee + " coins" : " on the player's honour"));
        g.ai.say(n, switch (n.role) {
            case KNIGHT -> "I'll ride with you. Do not make me regret it.";
            case BANDIT -> "Coin's coin. Point me at whoever.";
            case BRUTE -> "Fine. Who needs hitting?";
            case HUNTER -> "I know these woods. Lead on - I'll keep up.";
            default -> "All right. Lead on.";
        });
        return Result.ok();
    }

    /** They part from the player and go back to their own life (their home and their work are where they left them). */
    public void dismiss(Npc n, String why) {
        if (n.companionOf == 0) return;
        n.companionOf = 0; n.order = Order.FOLLOW; n.orderTarget = 0;
        n.team = n.homeTeam; n.goal = Brain.Goal.IDLE; n.nextThink = 0;
        g.state.commit("CompanionLeft", n.id, g.player.id, rpg.world.DevMap.jurisdiction(n.x, n.z), 0, n.name + ": " + why);
        if (n.alive) g.ai.say(n, "I'll be on my way, then.");
    }

    /** An order for one companion: at a place (x, z) or a foe (target), as the order wants. */
    public void command(Npc n, Order o, float x, float z, long target) {
        if (n.companionOf == 0 || !n.alive) return;
        if (o == Order.ATTACK && (!(g.actor(target) instanceof Actor t) || !t.alive || g.magic.allied(n, t))) return;
        n.order = o; n.orderX = x; n.orderZ = z; n.orderTarget = o == Order.ATTACK ? target : 0;
        if (o == Order.COVER) { float[] c = cover(n, x, z); n.orderX = c[0]; n.orderZ = c[1]; }
        if (o == Order.MOVE && Squads.ranged(n)) { float[] a = firingSpot(n, x, z); n.orderX = a[0]; n.orderZ = a[1]; }
        if (o == Order.RALLY) { n.fightTarget = 0; g.combat.setBlock(n, false); }
        n.hasGoalPos = false; n.legs = null; n.tactic = null; n.nextThink = 0;
        g.state.commit("CompanionOrdered", g.player.id, n.id, rpg.world.DevMap.jurisdiction(n.x, n.z), 0, o + (o == Order.ATTACK ? " " + g.nameOf(target) : String.format(" (%.0f, %.0f)", n.orderX, n.orderZ)));
        if (n.speech == null || g.time - n.lastDialogAt > 2) {
            g.ai.say(n, switch (o) {
                case FOLLOW -> "Right behind you.";
                case RALLY -> "Coming!";
                case HOLD -> "I'll hold here.";
                case MOVE -> Squads.ranged(n) ? "Moving. I'll find an angle." : "Moving.";
                case COVER -> "Getting into cover.";
                case ATTACK -> "On " + g.nameOf(target) + "!";
            });
            n.lastDialogAt = g.time;
        }
    }

    // ------------------------------------------------------------------ abilities
    /** What this companion can be called on to do, by what they are and carry. */
    public List<Ability> abilities(Npc n) {
        List<Ability> l = new ArrayList<>();
        ItemDef main = n.main(), off = n.off();
        boolean ranged = Squads.ranged(n);
        if (main != null && !ranged && main.family != ItemDef.Family.STAFF) l.add(Ability.POWER_STRIKE);
        if (!ranged && (n.role == Npc.Role.KNIGHT || n.role == Npc.Role.BRUTE || n.role == Npc.Role.GUARD)) l.add(Ability.TAUNT);
        if (off != null && off.family == ItemDef.Family.SHIELD) l.add(Ability.SHIELD_WALL);
        if (ranged) l.add(Ability.VOLLEY);
        if (n.skills.level(Skill.MAGIC) >= 10) l.add(Ability.SPELL);
        for (ItemDef food : new ItemDef[]{Items.VEGETABLE_STEW, Items.BREAD, Items.SHRIMP, Items.APPLE}) if (n.has(food, 1)) { l.add(Ability.MEND); break; }
        return l;
    }
    /** Seconds until an ability is ready again (0: ready). */
    public float cooldown(Npc n, Ability a) { return (float) Math.max(0, n.abilityReady[a.ordinal()] - g.time); }

    /** An ability called on, against a target (for those that want one); done at once where it can be, else as an order. */
    public Result use(Npc n, Ability a, Actor target) {
        if (n.companionOf == 0 || !n.alive) return Result.no("Not one of your companions.");
        if (!abilities(n).contains(a)) return Result.no(n.name + " can't do that.");
        if (cooldown(n, a) > 0) return Result.no(String.format("%s needs %.0f s more.", a.title, cooldown(n, a)));
        boolean needsTarget = a == Ability.POWER_STRIKE || a == Ability.VOLLEY || a == Ability.SPELL;
        if (needsTarget && (target == null || !target.alive || g.magic.allied(n, target))) return Result.no("Choose a foe for that.");
        switch (a) {
            case POWER_STRIKE -> { n.powerNext = true; command(n, Order.ATTACK, target.x, target.z, target.id); }
            case VOLLEY -> { n.volleyLeft = 3; command(n, Order.ATTACK, target.x, target.z, target.id); }
            case SPELL -> {
                var spell = g.magic.combatSpell(n, target);
                if (spell == null) return Result.no(n.name + " has no spell for that.");
                float yaw = (float) Math.atan2(-(target.x - n.x), -(target.z - n.z)), pitch = (float) Math.atan2(target.chestY() - n.eyeY(), Math.max(0.1f, n.distTo(target)));
                if (!g.combat.cast(n, spell, yaw, pitch)) return Result.no(n.name + " can't cast now.");
            }
            case TAUNT -> {
                int drawn = 0;
                for (Actor foe : g.actors()) {
                    if (!foe.alive || foe == n || g.magic.allied(n, foe) || foe.distTo(n) > 12) continue;
                    if (foe instanceof rpg.monster.Monster m && m.target != 0) { m.target = n.id; drawn++; }
                    else if (foe instanceof Npc e && e.fightTarget != 0 && e.fightTarget != n.id) { e.fightTarget = n.id; e.nextThink = 0; drawn++; }
                }
                g.ai.say(n, drawn > 0 ? "Over here! Face me!" : "Come on, then!");
                g.noise(n, n.x, n.y, n.z, 14, 0.9f);
            }
            case SHIELD_WALL -> { n.shieldWallUntil = g.time + 6; g.combat.setBlock(n, true); g.ai.say(n, "Behind me!"); }
            case MEND -> {
                for (ItemDef food : new ItemDef[]{Items.VEGETABLE_STEW, Items.BREAD, Items.SHRIMP, Items.APPLE}) if (n.has(food, 1)) {
                    n.takeItems(food, 1); g.state.burn(food, 1);
                    n.hp = Math.min(n.maxHp(), n.hp + Math.max(4, food.heal));
                    g.ai.say(n, "A moment - there.");
                    break;
                }
            }
        }
        n.abilityReady[a.ordinal()] = g.time + a.cooldown;
        g.state.commit("CompanionAbility", g.player.id, n.id, rpg.world.DevMap.jurisdiction(n.x, n.z), 0, a + (target == null ? "" : " on " + target.name));
        return Result.ok();
    }

    // ------------------------------------------------------------------ the behaviour
    /** The foe this companion should be fighting now, by its order (null: none). */
    Actor foe(Npc n) {
        Actor leader = g.actor(n.companionOf);
        if (leader == null || !leader.alive) return null;
        if (n.order == Order.RALLY) return g.time - n.cs.lastHurt < 2 ? g.actor(n.cs.lastAttacker) : null;
        // a blow from someone close gets an answer whatever the order (it is not an automaton)
        Actor striker = g.time - n.cs.lastHurt < 3 ? g.actor(n.cs.lastAttacker) : null;
        if (striker != null && (!striker.alive || g.magic.allied(n, striker) || n.distTo(striker) > 4)) striker = null;
        if (n.order == Order.ATTACK) {
            Actor t = g.actor(n.orderTarget);
            if (t == null || !t.alive || g.magic.allied(n, t)) { n.order = Order.FOLLOW; n.orderTarget = 0; }
            else return striker != null && striker != t && n.distTo(striker) < n.distTo(t) * 0.5f ? striker : t;
        }
        // the leader's fight is theirs: whoever hurts the leader, or the leader is fighting
        Actor t = striker;
        if (t == null) {
            Actor hit = g.time - leader.cs.lastHurt < 12 ? g.actor(leader.cs.lastAttacker) : null;
            if (hit != null && hit.alive && !g.magic.allied(n, hit)) t = hit;
        }
        if (t == null) for (Actor a : g.actors()) {
            if (!a.alive || a == leader || a == n || g.magic.allied(n, a) || a.distTo(leader) > 14) continue;
            boolean onUs = a instanceof rpg.monster.Monster m ? m.target == leader.id || m.target == n.id : a instanceof Npc e && (e.fightTarget == leader.id || e.fightTarget == n.id);
            boolean leaderOnIt = leader.cs.lastAttacker == 0 && a.cs.lastAttacker == leader.id && g.time - a.cs.lastHurt < 12;
            if ((onUs || leaderOnIt) && g.ai.canSee(n, a)) { t = a; break; }
        }
        if (t == null) return null;
        // holding: only what comes within reach of the post
        if ((n.order == Order.HOLD || n.order == Order.COVER || n.order == Order.MOVE) && Math.hypot(t.x - n.orderX, t.z - n.orderZ) > HOLD_LEASH + (Squads.ranged(n) ? 18 : 0)) return null;
        return t;
    }

    /**
     * (0.3.9) The leader rides: a companion takes a spare mount of the leader's (or their own) - saddled, broken in, not
     * ridden, close by - and rides with them; the leader on foot again, they get down too. True while seeing to it.
     */
    boolean mountUp(Npc n, Actor leader) {
        boolean leaderUp = leader.riding != 0;
        if (n.riding != 0) {
            var ride = g.riding.of(n);
            if (!leaderUp && ride != null && !ride.mounting() && ride.body.grounded) { g.riding.stepDown(n); n.lastOutcome = "down from the saddle"; return true; }
            return false;
        }
        if (!leaderUp || n.order != Order.FOLLOW && n.order != Order.RALLY) return false;
        rpg.animal.Animal best = null; float bd = 25;
        if (g.animals != null) for (rpg.animal.Animal a : g.animals.all()) {
            if (!rpg.mount.Riding.mountable(a) || !a.saddled || a.rider != 0 || g.riding.ridden(a) != null || a.id == leader.riding) continue;
            if (!(a.owner == leader.id || a.tamedBy == leader.id || a.owner == n.id)) continue;
            float d = n.distTo(a);
            if (d < bd) { bd = d; best = a; }
        }
        if (best == null) return false;
        if (n.distTo(best) > 2.2f) { brain.goTo(n, best.x, best.z, true, 1.6f); n.lastOutcome = "to the " + best.kind().toLowerCase() + " to ride with " + leader.name; return true; }
        var r = g.riding.mount(n, best);
        n.lastOutcome = r.rejected() ? "could not mount: " + r.reason() : "mounting the " + best.kind().toLowerCase();
        return true;
    }

    /** Not fighting: where the order puts them. */
    void act(Npc n) {
        Actor leader = g.actor(n.companionOf);
        if (leader == null || !leader.alive) { g.ai.desire(n, AiSystem.A_HOLD); return; }
        if (mountUp(n, leader)) return;
        switch (n.order) {
            case HOLD, MOVE, COVER -> {
                if (brain.goTo(n, n.orderX, n.orderZ, n.order != Order.HOLD || n.distTo(n.orderX, n.orderZ) > 6, 0.8f)) {
                    g.ai.desire(n, AiSystem.A_IDLE);
                    n.sneaking = n.order == Order.COVER;
                    n.wantYaw = (float) Math.atan2(-(n.x - leader.x), -(n.z - leader.z)) + (float) Math.PI;
                    n.lastOutcome = n.order == Order.COVER ? "in cover" : "holding position";
                } else n.lastOutcome = "moving to the ordered spot";
            }
            default -> {
                boolean rally = n.order == Order.RALLY;
                if (rally && n.distTo(leader) < 3) n.order = Order.FOLLOW;
                float side = slot(n) * 1.3f;
                float x = leader.x + (float) Math.cos(leader.yaw) * side + (float) Math.sin(leader.yaw) * 2.0f;
                float z = leader.z - (float) Math.sin(leader.yaw) * side + (float) Math.cos(leader.yaw) * 2.0f;
                if (!Waypoints.clear(g.vox, leader.x, leader.z, x, z)) { x = leader.x; z = leader.z; }
                if (brain.goTo(n, x, z, rally || n.distTo(leader) > 6, 1.1f)) { g.ai.desire(n, AiSystem.A_IDLE); n.wantYaw = leader.yaw; }
                n.lastOutcome = rally ? "rallying to " + leader.name : "following " + leader.name;
            }
        }
    }

    /** Where in the column behind the leader this one walks (-1.5 .. 1.5, alternating sides). */
    private float slot(Npc n) {
        List<Npc> l = of(g.actor(n.companionOf));
        int i = Math.max(0, l.indexOf(n));
        return (i % 2 == 0 ? 1 : -1) * (0.6f + (i / 2) * 0.9f);
    }

    /** Behind something solid from the nearest foe, near a place (or the place itself when there is nothing to hide behind). */
    float[] cover(Npc n, float x, float z) {
        Actor foe = null; float fd = 40;
        for (Actor a : g.actors()) if (a.alive && !g.magic.allied(n, a) && (a instanceof rpg.monster.Monster || a instanceof Npc e && e.combatant()) && Math.hypot(a.x - x, a.z - z) < fd) { fd = (float) Math.hypot(a.x - x, a.z - z); foe = a; }
        float fy = n.y;
        float[] best = {x, z}; float bd = Float.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            float cx = (int) Math.floor(x) + dx + 0.5f, cz = (int) Math.floor(z) + dz + 0.5f;
            int fyc = (int) Math.floor(fy + 0.05f);
            if (g.vox.solid((int) Math.floor(cx), fyc, (int) Math.floor(cz)) || g.vox.solid((int) Math.floor(cx), fyc + 1, (int) Math.floor(cz)) || !g.vox.solid((int) Math.floor(cx), fyc - 1, (int) Math.floor(cz))) continue;
            boolean hidden = foe != null && !g.vox.sight(cx, fy + 1.2f, cz, foe.x, foe.eyeY(), foe.z);
            boolean wall = false;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (g.vox.solid((int) Math.floor(cx) + d[0], fyc + 1, (int) Math.floor(cz) + d[1])) wall = true;
            if (!(foe == null ? wall : hidden)) continue;
            float d = (float) Math.hypot(cx - x, cz - z);
            if (d < bd) { bd = d; best = new float[]{cx, cz}; }
        }
        return best;
    }

    /** For a shooter sent somewhere: a spot near it with a clear line to the nearest foe (the place itself if it has one). */
    float[] firingSpot(Npc n, float x, float z) {
        Actor foe = null; float fd = 40;
        for (Actor a : g.actors()) if (a.alive && !g.magic.allied(n, a) && (a instanceof rpg.monster.Monster || a instanceof Npc e && e.combatant()) && Math.hypot(a.x - x, a.z - z) < fd) { fd = (float) Math.hypot(a.x - x, a.z - z); foe = a; }
        if (foe == null || g.vox.sight(x, n.y + 1.5f, z, foe.x, foe.chestY(), foe.z)) return new float[]{x, z};
        for (int r = 1; r <= 6; r++) for (int k = 0; k < 12; k++) {
            float cx = x + (float) Math.cos(k * Math.PI / 6) * r, cz = z + (float) Math.sin(k * Math.PI / 6) * r;
            int fyc = (int) Math.floor(n.y + 0.05f);
            if (g.vox.solid((int) Math.floor(cx), fyc, (int) Math.floor(cz)) || !g.vox.solid((int) Math.floor(cx), fyc - 1, (int) Math.floor(cz))) continue;
            if (g.vox.sight(cx, n.y + 1.5f, cz, foe.x, foe.chestY(), foe.z)) return new float[]{cx, cz};
        }
        return new float[]{x, z};
    }
}
