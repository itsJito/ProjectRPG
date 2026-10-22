package rpg.combat;

import rpg.Chat;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Player;
import rpg.ai.Npc;
import rpg.animal.Animal;
import rpg.combat.Spells.Spell;
import rpg.world.DevMap;
import rpg.world.Voxels;

/**
 * The non-damaging spells (GDD 2.5: Lunar traversal and social magic, Arcane illusion, obscuration, identity and
 * influence), each resolving through the same world as everything else - a step is a real teleport, an illusion a real
 * noise the NPCs' ears hear, a darkness a real drop in the light the stealth system reads, a disguise a witness who cannot
 * name who they saw, a charm a person who stops fighting. Every one is aimed: what the caster looks at is its target.
 */
public final class Magic {
    private final Rpg g;
    /** Globes of magical darkness: where, how big, until when (Stealth reads them). */
    public record Dark(float x, float y, float z, float r, double until) {}
    public final java.util.List<Dark> darkness = new java.util.ArrayList<>();

    public Magic(Rpg g) { this.g = g; }

    /** Casts a utility spell; false (nothing spent) when it has nothing to act on. */
    public boolean cast(Actor a, Spell s, float yaw, float pitch) {
        float dx = (float) (-Math.sin(yaw) * Math.cos(pitch)), dy = (float) Math.sin(pitch), dz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        switch (s) {
            case SPEAK_WITH_ANIMALS -> {
                if (!(g.interact.focus instanceof Animal an) || !an.alive) return no(a, "Look at an animal to speak with it.");
                an.trust.merge(a.id, 0.3f, (x, y) -> Math.min(1, x + y));
                an.fear = Math.max(0, an.fear - 0.5f);
                if (g.fx != null) g.fx.text("\"" + thought(an) + "\"", an.x, an.y + an.height + 0.4f, an.z, 0xFFC8F0C0);
                if (a == g.player) g.chat.say("The " + an.kind().toLowerCase() + " seems to say: " + thought(an));
                return true;
            }
            case SPIRITUAL_STEP -> {
                // along the gaze, up to six metres, stopping short of a wall; onto the ground there
                Voxels.Hit h = g.vox.ray(a.x, a.eyeY(), a.z, dx, 0, dz, 6, false);
                float d = h.hit ? Math.max(0, h.dist - 0.6f) : 6;
                if (d < 0.8f) return no(a, "There's no room to step there.");
                float tx = a.x + dx / (float) Math.hypot(dx, dz) * d, tz = a.z + dz / (float) Math.hypot(dx, dz) * d;
                float ty = g.vox.groundY(tx, a.y + 1.5f, tz);
                if (g.fx != null) for (int i = 0; i < 10; i++) g.fx.trail(a.x + (tx - a.x) * i / 10f, a.y + 1, a.z + (tz - a.z) * i / 10f, 3, 3, 5);
                g.teleport(a, tx, ty, tz);
                return true;
            }
            case GASEOUS_FORM -> { a.gaseousT = 10; return true; }
            case ENHANCE_ABILITY -> { a.enhanceT = 60; return true; }
            case FRIENDS -> {
                if (!(g.interact.focus instanceof Npc n) || !n.alive) return no(a, "Look at someone to befriend them.");
                if (n.hostileToPlayer && (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE)) return no(a, n.name + " will have none of it.");
                n.friendUntil = g.time + 120;
                g.ai.say(n, "Oh - it's you! Good to see you, friend.");
                return true;
            }
            case MINOR_ILLUSION -> {
                Voxels.Hit h = g.vox.ray(a.x, a.eyeY(), a.z, dx, dy, dz, 24, false);
                float t = h.hit ? h.dist : 24, px = a.x + dx * t, py = a.eyeY() + dy * t, pz = a.z + dz * t;
                // a sound with no maker: the engine's ears hear it, and the NPCs near go to see
                g.noise(px, py, pz, 12, 0.7f, 0);
                g.ai.stealth.illusion(px, py, pz, 12);
                if (g.sfx != null) g.sfx.play("wood", px, py, pz, 0.8f);
                return true;
            }
            case DARKNESS -> {
                Voxels.Hit h = g.vox.ray(a.x, a.eyeY(), a.z, dx, dy, dz, 16, false);
                float t = h.hit ? Math.max(0, h.dist - 0.5f) : 16;
                darkness.add(new Dark(a.x + dx * t, Math.max(DevMap.FLOOR + 1, a.eyeY() + dy * t), a.z + dz * t, 4.5f, g.time + 20));
                return true;
            }
            case DISGUISE_SELF -> { a.disguiseT = 60; return true; }
            case CHARM_PERSON -> {
                if (!(g.interact.focus instanceof Npc n) || !n.alive) return no(a, "Look at someone to charm them.");
                n.charmUntil = g.time + 30;
                if (n.fightTarget == a.id) { n.fightTarget = 0; n.cs.cancel(); n.nextThink = 0; }
                g.ai.say(n, "...why was I angry?");
                return true;
            }
            default -> { return false; }
        }
    }

    private boolean no(Actor a, String why) { if (a == g.player) g.chat.add(why, Chat.Kind.WARN); return false; }

    private static String thought(Animal an) {
        String r = an.reason == null || an.reason.isEmpty() ? "nothing much" : an.reason;
        return Character.toUpperCase(r.charAt(0)) + r.substring(1) + ".";
    }

    /** How much darker a point is for a darkness spell over it (1 none .. 0.1 deep in a globe). */
    public float dim(float x, float y, float z) {
        float k = 1;
        for (Dark d : darkness) {
            float dd = (float) Math.sqrt((x - d.x) * (x - d.x) + (y - d.y) * (y - d.y) + (z - d.z) * (z - d.z));
            if (dd < d.r) k = Math.min(k, 0.1f + 0.9f * dd / d.r);
        }
        return k;
    }

    public void update(float dt) {
        darkness.removeIf(d -> g.time > d.until);
        for (Actor a : g.actors()) {
            if (a.gaseousT > 0) a.gaseousT = Math.max(0, a.gaseousT - dt);
            if (a.enhanceT > 0) a.enhanceT = Math.max(0, a.enhanceT - dt);
            if (a.disguiseT > 0) { a.disguiseT = Math.max(0, a.disguiseT - dt); if (a.disguiseT == 0 && a instanceof Player) g.chat.say("Your disguise fades."); }
        }
    }
}
