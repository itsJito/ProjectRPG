package rpg.law;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.ai.Npc;
import rpg.item.Inventory;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.interact.Result;
import rpg.ui.Dialog;

/** Signed physical warrants are issued only from reported human offenses and paid once from the local watch's purse. */
public final class BountyBoard {
    private final Rpg g;
    public BountyBoard(Rpg g) { this.g = g; }
    public List<ItemStack.Contract> carried() {
        List<ItemStack.Contract> result = new ArrayList<>();
        for (Inventory grid : g.player.grids()) for (ItemStack item : grid.stacks()) if (item.def == Items.BOUNTY_SCROLL && item.contract != null) result.add(item.contract);
        return result;
    }
    public boolean authorized(long target) { for (var c : carried()) if (c.target() == target && g.law.bounty(target, c.jurisdiction()) > 0) return true; return false; }
    public Dialog browse(String jurisdiction) {
        Dialog d = new Dialog("Warrants - " + jurisdiction, "Only sworn reports reach this board. A warrant records the last sighting known to the watch; the fugitive may have moved.");
        var cases = g.law.all().get(jurisdiction);
        if (cases != null) for (var entry : cases.entrySet()) if (g.actor(entry.getKey()) instanceof Npc n && n.alive && !g.law.inCustody(n)) {
            int reward = entry.getValue();
            d.choice(n.name + " - " + reward + " gold", () -> { Result r = accept(n, jurisdiction); if (r.rejected()) g.chat.warn(r.reason()); });
        }
        d.choice("Claim completed warrants", () -> { Result r = claim(jurisdiction); if (r.rejected()) g.chat.warn(r.reason()); });
        d.choice("Leave", () -> {}); return d;
    }
    public Result accept(Npc target, String jurisdiction) {
        int reward = g.law.bounty(target.id, jurisdiction);
        if (!target.alive || reward <= 0 || g.law.inCustody(target)) return Result.no("That warrant is no longer open.");
        for (var c : carried()) if (c.target() == target.id && c.jurisdiction().equals(jurisdiction)) return Result.no("You already carry that warrant.");
        long caseId = 0;
        for (var event : g.state.all()) if (event.type().equals("CrimeReported") && event.subject() == target.id && jurisdiction.equals(event.jurisdiction())) caseId = event.id();
        if (caseId == 0) return Result.no("There is no sworn report for that warrant.");
        Npc issuer = g.ai.lawmanFor(jurisdiction);
        if (issuer == null) return Result.no("The watch cannot issue warrants now.");
        float x = issuer.postX, z = issuer.postZ; double seen = -99;
        for (Npc witness : g.npcs) if (witness.alive && witness.homeJurisdiction.equals(jurisdiction) && (witness.role == Npc.Role.GUARD || witness == issuer)) {
            var aware = witness.aware.get(target.id);
            if (aware != null && aware.seenAt > seen) { seen = aware.seenAt; x = aware.x; z = aware.z; }
        }
        ItemStack scroll = new ItemStack(Items.BOUNTY_SCROLL, 1, g.player.id, false, issuer.id);
        scroll.contract = new ItemStack.Contract(caseId, target.id, jurisdiction, reward, x, z);
        if (!g.player.canStore(scroll)) return Result.no("Make room for a one-by-two bounty scroll.");
        g.grant(g.player, scroll);
        g.state.commit("WarrantTaken", g.player.id, target.id, jurisdiction, caseId, reward + " gold; last known " + x + ", " + z);
        g.chat.say("You take the warrant for " + target.name + ". Its last known location is marked on your map.");
        return Result.ok();
    }
    public Result claim(String jurisdiction) {
        Npc issuer = g.ai.lawmanFor(jurisdiction);
        if (issuer == null || issuer.distTo(g.player) > 5) return Result.no("Bring the scroll to the watch when the lawman is here.");
        for (Inventory grid : g.player.grids()) for (int slot = 0; slot < grid.size(); slot++) {
            ItemStack scroll = grid.get(slot); if (scroll == null || scroll.contract == null || scroll.def != Items.BOUNTY_SCROLL) continue;
            var c = scroll.contract;
            if (!jurisdiction.equals(c.jurisdiction()) || !(g.actor(c.target()) instanceof Npc target) || target.alive || g.law.bounty(target.id, jurisdiction) <= 0) continue;
            boolean credited = false, settled = false;
            for (var event : g.state.all()) {
                if (event.id() <= c.caseId()) continue;
                if (event.type().equals("ActorKilled") && event.actor() == g.player.id && event.subject() == target.id) credited = true;
                if (jurisdiction.equals(event.jurisdiction()) && (event.type().equals("BountyPaid") && event.actor() == target.id || event.type().equals("Arrested") && event.subject() == target.id || event.type().equals("WarrantPaid") && event.subject() == target.id)) settled = true;
            }
            if (!credited || settled) continue;
            int reward = Math.min(c.reward(), g.law.bounty(target.id, jurisdiction));
            if (!issuer.has(Items.COINS, reward)) return Result.no("The watch's purse cannot cover that warrant yet.");
            grid.set(slot, null); g.state.burn(Items.BOUNTY_SCROLL, 1);
            for (ItemStack coins : issuer.takeItems(Items.COINS, reward)) { coins.owner = 0; if (!g.player.store(coins)) g.objects.drop(coins, g.player.x, g.player.y, g.player.z); }
            g.law.closeCase(target, jurisdiction);
            g.state.commit("WarrantPaid", g.player.id, target.id, jurisdiction, c.caseId(), reward + " gold");
            g.chat.say("The watch pays " + reward + " gold for " + target.name + "."); return Result.ok();
        }
        return Result.no("No completed warrant can be paid here.");
    }
}
