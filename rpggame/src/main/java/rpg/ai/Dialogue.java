package rpg.ai;

import rpg.Ids;
import rpg.Rpg;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.quest.Quests;
import rpg.ui.Dialog;

/**
 * Routine, contextual dialogue (GDD 3.3): what an NPC says depends on their role, what they remember about the player
 * (seen with their own eyes, or heard as rumor), the player's legal standing, and the quest's state. Test lines only.
 */
public final class Dialogue {
    private final Rpg g;

    public Dialogue(Rpg g) { this.g = g; }

    public Dialog talk(Npc n) {
        n.conversations++;
        Memory known = n.crimeKnown(g.player.id);
        String greet = known == null ? greeting(n) : known.settled
                ? "Your debt to the law is settled. I still remember what happened."
                : known.witnessed()
                ? "I saw you - " + known.kind.toLowerCase() + ". Keep your distance."
                : "I've heard about you (" + known.kind.toLowerCase() + ", " + known.source + "). I'm watching you.";
        Dialog d = new Dialog(n.name + " - " + n.title, greet);
        d.npc = n.id;
        // a courtesy (Honor, GDD 3.5): a warm greeting to someone who will take it, once each
        if (!n.greeted && known == null && !n.hostileToPlayer && n.combatant() == (n.role == Npc.Role.GUARD)) d.choice("Good day to you! (greet them warmly)", () -> {
            n.greeted = true;
            g.honor.greeted(n.id);
            g.ai.rumors.deed("greeted " + n.name + " kindly", n.id);
            g.ai.say(n, n.role == Npc.Role.GUARD ? "And to you. Stay out of trouble." : "Oh! Good day to you, too.");
        });
        switch (n.role) {
            case MERCHANT -> d.choice("Let's trade.", () -> g.interact.request(g.player, n, "trade"));
            case LAWMAN -> {
                int b = g.law.bounty(g.player.id, n.homeJurisdiction);
                if (b > 0) d.choice("Pay my bounty (" + b + " coins).", () -> g.interact.request(g.player, n, "pay"));
                d.choice("Any crimes reported?", () -> g.ui.dialog(new Dialog(n.name, reports(n))));
            }
            case QUEST_GIVER -> questGiver(n, d);
            case CONTACT -> {
                if (g.quests.courier == Quests.State.ACTIVE && g.player.has(Items.SEALED_LETTER, 1)) d.choice("I have a letter for you from Jun.", g.quests::deliver);
            }
            case FENCE -> {
                d.choice("I've got some things to sell. No questions.", () -> g.interact.request(g.player, n, "trade"));
                d.choice("How does this work?", () -> g.ui.dialog(new Dialog(n.name, "You bring it, I don't ask where it came from, and I pay a third of what it's worth - "
                        + "the rest is my risk. I don't deal with the law looking on. And what I sell on is still whatever it was when it came to me: if it's hot, it stays hot.")));
            }
            case UNDERTAKER -> {
                int held = g.undertaker.held();
                if (held > 0) d.choice("I've come for my belongings (" + g.undertaker.fee() + " coins).", () -> g.interact.request(g.player, n, "reclaim"));
                d.choice("How does this work?", () -> g.ui.dialog(new Dialog(n.name, "Die inside the walls and the guard brings me what you had - all of it, every coin. Die out in the wild and you'd best get "
                        + "back to it quick: anyone may help themselves. Either way, after ten minutes I fetch what's left. " + rpg.law.Undertaker.FEE + " coins to have it back, "
                        + (rpg.law.Undertaker.FEE + rpg.law.Undertaker.OUTSIDE_SURCHARGE) + " if I had to go beyond the walls. I'll take it out of your own purse if that's all you have.")));
            }
            case THIEF -> d.choice("Got anything for me?", () -> g.ui.dialog(new Dialog(n.name, "Psst. Stalls are easy when nobody's looking. The trick is that nobody's looking.")));
            default -> { }
        }
        d.choice("What do you know?", () -> g.ui.dialog(new Dialog(n.name, news(n))));
        d.choice("Goodbye.", () -> { });
        return d;
    }

    private void questGiver(Npc n, Dialog d) {
        switch (g.quests.courier) {
            case NOT_STARTED -> {
                Npc kit = (Npc) g.actor(Ids.CONTACT);
                if (kit != null && kit.alive) d.choice("Do you have a job for me?", () -> g.ui.dialog(new Dialog(n.name, "Take this sealed letter to Kit - east of Store B, in JUR-BETA. Don't open it.")
                        .choice("I'll do it.", g.quests::start).choice("Not now.", () -> { })));
                else d.choice("Do you have a job for me?", () -> g.ui.dialog(new Dialog(n.name, "I did... but Kit is dead. There's no one left to take it to.")));
            }
            case ACTIVE -> d.choice("About the letter...", () -> g.ui.dialog(new Dialog(n.name, "Kit's waiting for it, east of Store B.")));
            case COMPLETED -> d.choice("About the letter...", () -> g.ui.dialog(new Dialog(n.name, "Kit got it. Thank you.")));
            case FAILED -> d.choice("About the letter...", () -> g.ui.dialog(new Dialog(n.name, "Kit is dead. That letter will never be delivered now.")));
        }
    }

    private String pick(Npc n, String... lines) { return lines[Math.floorMod(n.conversations + (int) n.id + (int) (g.state.minutes / 1440), lines.length)]; }

    public String greeting(Npc n) {
        if (n.hunger > 0.75f) return "Forgive me. I haven't eaten, and I have little patience left.";
        if (n.fatigue > 0.85f) return "I've been on my feet too long. What do you need?";
        return switch (n.role) {
            case MERCHANT -> pick(n, "Have a look. Tell me what you need, and we'll see about a fair price.", "Good day. Buying, or selling?", "Mind the counter. I've just put the stock in order.");
            case LAWMAN -> pick(n, "If you have a charge to lay, give me names and what you saw.", "Bounties and sworn reports are handled here. Speak plainly.", "The watch keeps records. What business brings you in?");
            case GUARD -> pick(n, "Keep the road clear, please.", "Quiet so far. Help me keep it that way.", "If there's trouble, tell me what happened before you draw steel.");
            case WORKER -> pick(n, "There's work enough for two days, and only one pair of hands.", "Mind where you stand. I don't want to catch you with the axe.", "A clean cut saves work at the sawbench.");
            case VILLAGER -> pick(n, "Good day. Have you come far?", "If you're staying, keep out of other people's business.", "A little peace and a full cupboard. That's all I ask.");
            case THIEF -> pick(n, "Do I know you? No? Best keep it that way.", "I was just passing through. Same as you, I reckon.", "Don't stare. It makes honest people nervous.");
            case CARTER -> g.econ.active() == null ? pick(n, "No load today. The beasts could use the rest.", "Waiting on a load. Waiting is the greater part of hauling.") : pick(n, "Keep clear of the load. It doesn't stop quickly.", "A full cart and a sound road. I'll settle for one of the two.");
            case QUEST_GIVER -> pick(n, "I could use a reliable pair of hands.", "Have you time for a little paid work?", "There you are. I have a delivery to arrange.");
            case CONTACT -> pick(n, "Were you looking for me?", "If you've brought a letter, let's have it.", "I've been expecting word. Is that why you're here?");
            case FENCE -> pick(n, "Keep your voice down. What have you got?", "Names cost extra. Best leave them out.", "Show me the goods. We'll discuss the price quietly.");
            case UNDERTAKER -> g.undertaker.held() > 0 ? "I have your belongings. Take your time; they're safe here." : pick(n, "Mind your step among the stones.", "Every name here belonged to someone. Remember that.", "If you need to make arrangements, I'll hear you out.");
            case FARMER -> pick(n, "Shut the gate behind you. They take any chance to wander.", "The stock eats before I do, most mornings.", "Hands away from the feed unless you're helping.");
            case BANDIT -> pick(n, "That's close enough. Keep your hands where I can see them.", "You're on the wrong road, friend.", "Looking for trouble, or merely lost?");
            case BRUTE -> pick(n, "What? Speak up, then.", "Don't crowd me.", "I've nothing to say to you. Move on.");
        };
    }

    private String news(Npc n) {
        StringBuilder b = new StringBuilder();
        for (Memory m : n.memories) if (m.crime()) b.append(g.nameOf(m.subject)).append(": ").append(m.kind.toLowerCase()).append(" (").append(m.source).append("). ");
        if (b.length() == 0) b.append("Nothing worth repeating. ");
        b.append(switch (n.goal) { case WORK -> "I've my own work to see to."; case SLEEP -> "I should be getting some rest."; case REPORT_CRIME -> "I have business with the watch."; case COLLECT -> "There's a body that needs seeing to."; case AID -> "Someone nearby could use a hand."; default -> "Keep your ears open. News travels with the people who carry it."; });
        return b.toString();
    }

    private String reports(Npc lawman) {
        StringBuilder b = new StringBuilder();
        for (String r : g.law.reportsLog) if (r.contains(lawman.name)) b.append(r).append(". ");
        return b.length() == 0 ? "No crimes have been reported to me." : b.toString();
    }

    /** The XP lamp: rubbing it grants XP in a chosen skill (a quest reward; not scaled by the test rate). */
    public Dialog lamp(int slot) { return lamp(g.player.inv, slot); }

    public Dialog lamp(rpg.item.Inventory grid, int slot) {
        Dialog d = new Dialog("Experience lamp", "Choose a skill to gain 2,500 XP in.");
        for (rpg.skill.Skill s : rpg.skill.Skill.ALL) d.choice(s.title, () -> {
            ItemStack it = grid.get(slot);
            if (it == null || it.def != Items.XP_LAMP) return;
            long req = g.state.newRequestId();
            grid.take(slot, 1);
            g.state.burn(Items.XP_LAMP, 1);
            g.prog.awardRaw(g.player, s, 2500, "experience lamp", 0, req);
            g.chat.say("The lamp grants you 2,500 " + s.title + " XP.");
        });
        return d;
    }

}
