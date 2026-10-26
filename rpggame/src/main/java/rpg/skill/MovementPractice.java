package rpg.skill;

import java.util.LinkedHashMap;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Player;

/** Small player-only rewards for actual travel, with no credit for pushing a wall or hopping in place. */
public final class MovementPractice {
    private final Rpg g;
    private float distance, spent, jumpX, jumpZ;
    private boolean jumping;
    private double nextJump;
    private final Map<Long, Double> landings = new LinkedHashMap<>();
    public MovementPractice(Rpg g) { this.g = g; }
    public void sprintCost(float stamina) { spent = Math.min(20, spent + Math.max(0, stamina)); }
    public void jumped() { jumping = true; jumpX = g.player.x; jumpZ = g.player.z; }
    public void moved(float traveled) {
        Player p = g.player;
        if (!p.alive || p.reacting || g.traversal.busy(p) || traveled > 1 || p.gaseousT > 0) { jumping = false; return; }
        if (p.sprinting && traveled > 0.005f && spent > 0) {
            distance += traveled;
            if (distance >= 20 && spent >= 5) { distance -= 20; spent -= 5; g.prog.award(p, Skill.AGILITY, 1, "sustained running", 0); }
        }
        if (jumping && p.grounded) {
            jumping = false;
            if (g.time < nextJump) return;
            if (Math.hypot(p.x - jumpX, p.z - jumpZ) < 2.5) return;
            long tile = ((long)Math.floor(p.x / 4) << 32) ^ (int)Math.floor(p.z / 4);
            landings.entrySet().removeIf(e -> g.time - e.getValue() > 600);
            if (landings.containsKey(tile)) return;
            landings.put(tile, g.time); if (landings.size() > 128) landings.remove(landings.keySet().iterator().next());
            nextJump = g.time + 8; g.prog.award(p, Skill.AGILITY, 1, "travelling jump", 0);
        }
    }
    public void reset() { distance = spent = 0; jumping = false; }
}
