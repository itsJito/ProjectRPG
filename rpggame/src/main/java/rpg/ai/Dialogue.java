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
        d.npc = n.id; d.employment = n.employmentState();
        // a courtesy (Honor, GDD 3.5): a warm greeting to someone who will take it, once each
        if (!n.greeted && known == null && !n.hostileToPlayer && n.combatant() == n.watch()) d.choice("Good day to you! (greet them warmly)", () -> {
            n.greeted = true;
            g.honor.greeted(n.id);
            g.ai.rumors.deed("greeted " + n.name + " kindly", n.id);
            g.ai.say(n, n.role == Npc.Role.GUARD ? "And to you. Stay out of trouble." : "Oh! Good day to you, too.");
        });
        switch (n.role) {
            case MERCHANT -> { if (n.working()) d.choice("Let's trade.", () -> g.interact.request(g.player, n, "trade")); }
            case LAWMAN -> {
                int b = g.law.bounty(g.player.id, n.homeJurisdiction);
                if (b > 0) d.choice("Pay my bounty (" + b + " coins).", () -> g.interact.request(g.player, n, "pay"));
                d.choice("Any crimes reported?", () -> g.ui.dialog(new Dialog(n.name, reports(n))));
            }
            case QUEST_GIVER -> { if(n.working()) questGiver(n, d); }
            case CONTACT -> {
                if (g.quests.courier == Quests.State.ACTIVE && g.player.has(Items.SEALED_LETTER, 1)) d.choice("I have a letter for you from Jun.", g.quests::deliver);
            }
            case FENCE -> {
                if (!n.working()) break;
                d.choice("I've got some things to sell. No questions.", () -> g.interact.request(g.player, n, "trade"));
                d.choice("How does this work?", () -> g.ui.dialog(new Dialog(n.name, "You bring it, I don't ask where it came from, and I pay a third of what it's worth - "
                        + "the rest is my risk. I don't deal with the law looking on. And what I sell on is still whatever it was when it came to me: if it's hot, it stays hot.")));
            }
            case UNDERTAKER -> {
                if (!n.working()) break;
                int held = g.undertaker.held();
                if (held > 0) d.choice("I've come for my belongings (" + g.undertaker.fee() + " coins).", () -> g.interact.request(g.player, n, "reclaim"));
                d.choice("How does this work?", () -> g.ui.dialog(new Dialog(n.name, "Die inside the walls and your lawful belongings come straight into my keeping. Stolen goods are confiscated, never reclaimed. Die out in the wild and you'd best get "
                        + "back to it quick: anyone may help themselves. Either way, after ten minutes I fetch what's left. " + rpg.law.Undertaker.FEE + " coins to have it back, "
                        + (rpg.law.Undertaker.FEE + rpg.law.Undertaker.OUTSIDE_SURCHARGE) + " if I had to go beyond the walls. I'll take it out of your own purse if that's all you have.")));
            }
            case BEGGAR -> d.choice("Offer a loaf of bread.", () -> {
                if (!g.player.has(Items.BREAD, 1) || !n.canStore(new ItemStack(Items.BREAD, 1))) { g.chat.warn("You need a loaf to spare."); return; }
                for (ItemStack loaf : g.player.takeItems(Items.BREAD, 1)) n.store(loaf);
                n.hunger = Math.max(0, n.hunger - 0.25f); g.ai.say(n, "Bless you. A warm meal at last.");
                g.state.commit("FoodGiven", g.player.id, n.id, n.homeJurisdiction, 0, "bread");
            });
            case HEALER -> d.choice("Where can I find supplies?", () -> g.ui.dialog(new Dialog(n.name, "Food restores the body. I share what I carry with the wounded, but my supplies are finite. The shops will buy herbs and berries.")));
            case PRIEST -> d.choice("Who tends the dead?", () -> g.ui.dialog(new Dialog(n.name, "The watch carries the fallen to these stones. Morrow keeps the belongings safe. Even a criminal deserves a name and a resting place.")));
            case SCHOLAR -> d.choice("Where can I sell questionable goods?", () -> g.ui.dialog(new Dialog(n.name, "Silas keeps a quiet nook behind Store B, in DevTown. He buys stolen goods, at a price. I would keep that conversation away from the watch.")));
            case HUNTER -> d.choice("Any advice for the wilderness?", () -> g.ui.dialog(new Dialog(n.name, "Keep clear of a predator's kill. Feed brings the herd together, and blood brings the scavengers. A horse won't pick a fight unless you hurt it.")));
            case CIVIC -> { if (n.id == Ids.TELLER) g.bank.choices(n, d); }
            case THIEF -> d.choice("Got anything for me?", () -> g.ui.dialog(new Dialog(n.name, "Psst. Stalls are easy when nobody's looking. The trick is that nobody's looking.")));
            default -> { }
        }
        d.choice("What do you know?", () -> g.ui.dialog(new Dialog(n.name, news(n))));
        d.closeChoice("Goodbye.", () -> { });
        return d;
    }

    private void questGiver(Npc n, Dialog d) {
        switch (g.quests.courier) {
            case NOT_STARTED -> {
                Npc kit = (Npc) g.actor(Ids.CONTACT);
                if (kit != null && kit.alive) d.choice("Do you have a job for me?", () -> g.ui.dialog(new Dialog(n.name, "Take this sealed letter to Kit - east of Store B, in DevTown. Don't open it.")
                        .choice("I'll do it.", g.quests::start).choice("Not now.", () -> { })));
                else d.choice("Do you have a job for me?", () -> g.ui.dialog(new Dialog(n.name, "I did... but Kit is dead. There's no one left to take it to.")));
            }
            case ACTIVE -> d.choice("About the letter...", () -> g.ui.dialog(new Dialog(n.name, "Kit's waiting for it, east of Store B.")));
            case COMPLETED -> d.choice("About the letter...", () -> g.ui.dialog(new Dialog(n.name, "Kit got it. Thank you.")));
            case FAILED -> d.choice("About the letter...", () -> g.ui.dialog(new Dialog(n.name, "Kit is dead. That letter will never be delivered now.")));
        }
    }

    private String pick(Npc n, String... lines) { return lines[Math.floorMod(n.conversations + (int) n.id + (int) (g.state.minutes / 1440), lines.length)]; }

    /**
     * What they say on being spoken to (gamefixes: more variety, and more of what is going on): their state first (off
     * duty, hungry, worn out), else a line of their calling or their trade - and with it, often, a remark on what they
     * can see or know right now: the hour and the weather, the state the player is in (hurt, bloodied, a body over the
     * shoulder, a blade out in the street, their name good or bad), a monster about, a town shut up, an old face back.
     */
    public String greeting(Npc n) {
        if (!n.working() && (n.profession != null || n.role == Npc.Role.CIVIC || n.role == Npc.Role.MERCHANT || n.role == Npc.Role.FENCE || n.role == Npc.Role.UNDERTAKER))
            return pick(n, "I'm off duty at the moment. We can talk, but work will have to wait.", "Not now - I've finished for the day. Ask me tomorrow.", "My hours are over. If it's not business, I'm glad to talk.");
        if (n.hunger > 0.75f) return pick(n, "Forgive me. I haven't eaten, and I have little patience left.", "I'm famished. Whatever it is, be quick.");
        if (n.fatigue > 0.85f) return pick(n, "I've been on my feet too long. What do you need?", "I'm dead on my feet. What is it?");
        String line = n.profession != null ? trade(n) : calling(n);
        String remark = remark(n);
        return remark == null ? line : line + " " + remark;
    }

    /** A line of their trade, for the townsfolk with a profession: what the day's work is like for one of them. */
    private String trade(Npc n) {
        String t = n.profession.title;
        return switch (n.profession.family) {
            case FISHERMAN -> pick(n, "The fish bite at first light, or not at all.", "Mind the nets. They take an age to mend.", "Quiet water today. Good for the line, bad for the talk.", "A " + t.toLowerCase(java.util.Locale.ROOT) + "'s day is mostly waiting, and a little cursing.");
            case MINER -> pick(n, "Rock doesn't move itself. Neither does ore.", "Mind the spoil heap. It shifts.", "Every seam runs out. You just hope it's tomorrow.", "Dust in the lungs and coin in the purse. Fair trade, mostly.");
            case CRAFTSMAN -> pick(n, "Good work takes good stock, and I'm short of both.", "Measure twice. That's the whole of the trade.", "If you've an order, I'll hear it. If you've materials, even better.", "The " + t.toLowerCase(java.util.Locale.ROOT) + " before me left a mess. I'm still putting it right.");
            case KNIGHT -> pick(n, "I keep my oath and my edge. Both need tending.", "State your business. Plainly, if you please.", "The road is safer for those who ride it armed.", "My duty is my duty. What is yours?");
            case HUNTER -> n.profession == Profession.MONSTER_HUNTER
                    ? pick(n, "Goblins in the north woods, bats under the den rock. Leave them to someone who knows their habits.", "A monster's lair tells you everything, if you can read it.", "Fire for trolls and spiders. Remember that, it might save you.")
                    : pick(n, "The wind carries your scent farther than your footsteps.", "Tracks don't lie. People do.", "A clean kill and a full larder. That's a good day.");
            default -> "There is work to be done.";
        };
    }

    /** A line of their calling (the townsfolk without a profession, and the outlaws). */
    private String calling(Npc n) {
        return switch (n.role) {
            case MERCHANT -> pick(n, "Have a look. Tell me what you need, and we'll see about a fair price.", "Good day. Buying, or selling?", "Mind the counter. I've just put the stock in order.", "Fresh stock this morning. Well, fresh enough.", "If you don't see it, ask. If I haven't got it, I'll know who has.");
            case LAWMAN -> pick(n, "If you have a charge to lay, give me names and what you saw.", "Bounties and sworn reports are handled here. Speak plainly.", "The watch keeps records. What business brings you in?", "The law doesn't sleep. I try to, now and then.");
            case GUARD -> pick(n, "Keep the road clear, please.", "Quiet so far. Help me keep it that way.", "If there's trouble, tell me what happened before you draw steel.", "Move along, unless you've something to report.", "Long shift. Short patience.");
            case WORKER -> pick(n, "There's work enough for two days, and only one pair of hands.", "Mind where you stand. I don't want to catch you with the axe.", "A clean cut saves work at the sawbench.", "Logs to the store, coin to the purse, sore back to bed.");
            case VILLAGER -> pick(n, "Good day. Have you come far?", "If you're staying, keep out of other people's business.", "A little peace and a full cupboard. That's all I ask.", "The berries are good this year. Don't tell the birds.");
            case THIEF -> pick(n, "Do I know you? No? Best keep it that way.", "I was just passing through. Same as you, I reckon.", "Don't stare. It makes honest people nervous.", "Lovely day for minding your own business.");
            case CARTER -> g.econ.active() == null ? pick(n, "No load today. The beasts could use the rest.", "Waiting on a load. Waiting is the greater part of hauling.") : pick(n, "Keep clear of the load. It doesn't stop quickly.", "A full cart and a sound road. I'll settle for one of the two.");
            case QUEST_GIVER -> pick(n, "I could use a reliable pair of hands.", "Have you time for a little paid work?", "There you are. I have a delivery to arrange.");
            case CONTACT -> pick(n, "Were you looking for me?", "If you've brought a letter, let's have it.", "I've been expecting word. Is that why you're here?");
            case FENCE -> pick(n, "Keep your voice down. What have you got?", "Names cost extra. Best leave them out.", "Show me the goods. We'll discuss the price quietly.");
            case UNDERTAKER -> g.undertaker.held() > 0 ? "I have your belongings. Take your time; they're safe here." : pick(n, "Mind your step among the stones.", "Every name here belonged to someone. Remember that.", "If you need to make arrangements, I'll hear you out.");
            case FARMER -> pick(n, "Shut the gate behind you. They take any chance to wander.", "The stock eats before I do, most mornings.", "Hands away from the feed unless you're helping.", "A fox has been at the coop again. I'll have its hide.");
            case BANDIT -> pick(n, "That's close enough. Keep your hands where I can see them.", "You're on the wrong road, friend.", "Looking for trouble, or merely lost?");
            case BEGGAR -> pick(n, "A loaf to spare? I'll remember the kindness.", "The cold finds you quicker without a roof.", "Spare a thought, if not a coin.");
            case HEALER -> pick(n, "Let me see that wound. Have you eaten?", "I can share what I have. There isn't much.", "Guam for the blood, rest for the rest.");
            case PRIEST -> pick(n, "We keep their names, even when their stories are lost.", "Walk gently among the stones.", "The altar is open to anyone who comes in peace.");
            case HUNTER -> pick(n, "The wind carries your scent farther than your footsteps.", "Never stand between a mother and her young.");
            case FISHERMAN, MINER, CRAFTSMAN, KNIGHT -> "There is work to be done.";
            case CIVIC -> g.bank.open() ? pick(n, "Welcome to the Bank of Gameworld. How may I help?", "Deposits, withdrawals, a new vault? Step up to the counter.", "Good day. Your things are safe with us.")
                    : "We're closed just now. Come back when I'm at the counter.";
            case SCHOLAR -> pick(n, "A story is only as sound as its witness.", "I collect accounts, not certainties. What have you seen?", "Everyone in this town has a version. I like to compare them.");
            case BRUTE -> pick(n, "What? Speak up, then.", "Don't crowd me.", "I've nothing to say to you. Move on.");
            case MONSTER -> "...";
        };
    }

    /**
     * A remark on the moment, or null (about two times in three there is one): the most pressing thing they can see or
     * know, in their own manner - the watch, a healer and the outlaws do not say the same about the same blood.
     */
    private String remark(Npc n) {
        var p = g.player;
        boolean watch = n.watch(), outlaw = n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE || n.role == Npc.Role.THIEF;
        boolean town = !rpg.world.DevMap.WILDS.equals(rpg.world.DevMap.jurisdiction(n.x, n.z));
        if (p.carryBody != 0) return watch ? "And who is that over your shoulder? Explain yourself." : pick(n, "Is that... a body? Gods.", "Who - who is that you're carrying?", "I'm not going to ask. I don't want to know.");
        if (p.hp < p.maxHp() * 0.35f) return n.role == Npc.Role.HEALER ? "You're badly hurt. Sit, before you fall." : outlaw ? "You look half dead. Easy pickings." : pick(n, "You're hurt - you should see the healer.", "You're bleeding. Get that seen to.", "Sit down before you fall down.");
        if (p.bloodied >= 0.4f) return watch ? "Is that your blood, or someone else's?" : outlaw ? "Busy day?" : pick(n, "You're covered in blood!", "Is - is that blood?", "Please tell me that isn't yours.");
        if (town && p.main() != null && p.main().damage > 0 && !watch && !outlaw && (n.conversations + n.id) % 2 == 0) return pick(n, "Put that away, would you? You're frightening people.", "There's no call for a blade in the street.");
        if (g.law.lockdown(n.homeJurisdiction)) return watch ? "The town is shut until this is settled." : "The shops are shut and the watch is everywhere. Something's happened.";
        String monster = monsterTalk(n);
        if (monster != null && (n.conversations + n.id) % 3 != 1) return monster;
        int honor = g.honor == null ? 0 : g.honor.score;
        if (honor >= 50 && !outlaw && (n.conversations + n.id) % 3 == 0) return pick(n, "Folk speak well of you around here.", "I've heard good things about you.", "You've a good name in this town.");
        if (honor <= -40 && (n.conversations + n.id) % 3 == 0) return outlaw ? "Word is you're one of us. Nice." : pick(n, "People say you're trouble.", "I've heard things about you. Not good things.");
        if (watch && p.skills.combatLevel() >= 60 && (n.conversations + n.id) % 4 == 0) return "You carry yourself like a fighter. Don't make me find out.";
        if (n.conversations == 1) return pick(n, "I don't believe we've met.", "New face. Welcome, I suppose.", "You're not from around here.");
        if (n.conversations > 6 && (n.conversations + n.id) % 4 == 0) return pick(n, "Back again? You're becoming a regular.", "You again. Good to see you.", "We keep meeting like this.");
        String weather = weatherTalk(n);
        if (weather != null && (n.conversations + n.id) % 2 == 0) return weather;
        return (n.conversations + n.id) % 3 == 0 ? hourTalk(n) : null;
    }

    /** A monster they know of: the nearest living one about (a lair close to town, something seen on the road). */
    private String monsterTalk(Npc n) {
        if (g.monsters == null) return null;
        rpg.monster.Monster near = null;
        float best = 70;
        for (var m : g.monsters.all()) if (m.alive && m.distTo(n) < best) { best = m.distTo(n); near = m; }
        if (near == null) return null;
        boolean bold = n.combatant();
        return switch (near.kind) {
            case GOBLIN -> bold ? "Goblins have been raiding from the north woods. They scatter when you kill the big one." : pick(n, "Goblins in the woods again. Lock your door at night.", "My cousin lost a goat to the goblins. A goat!");
            case BAT -> pick(n, "Bats come out of the den rock at dusk. Thousands of the things.", "Keep away from the den rock after dark. The bats.");
            case IMP -> pick(n, "Imps on the shed roof. They set the hay alight last week.", "If something small and red laughs at you, run.");
            case GIANT_SPIDER -> bold ? "There's a spider's web in the western wood. Bring fire." : pick(n, "There's something big in the western wood. Webs as tall as a man.", "Don't go west at night. Just don't.");
            case GHOST -> "They say the old graves aren't quiet any more.";
            case ZOMBIE, SKELETON -> pick(n, "The dead aren't staying dead. I don't like it.", "Something walks out there at night that shouldn't.");
            case WEREWOLF -> "Watch the full moon. Wolves aren't the worst of what howls.";
            case TROLL -> "A troll, they say. Fire's the only thing that stops one mending.";
            case ORC -> "Orc warbands on the move. Keep your gate barred.";
            case DRAGON -> "A dragon. A real one. Gods help us all.";
        };
    }

    private String weatherTalk(Npc n) {
        var w = g.timeSystem == null ? null : g.timeSystem.weather;
        if (w == null) return null;
        if (w.snow > 0.3f) return pick(n, "Snow again. My feet haven't been warm in a week.", "Mind the ice on the steps.");
        if (w.rain > 0.5f) return pick(n, "Wretched rain.", "Rain's good for the fields and bad for everything else.", "Get yourself under a roof, you'll catch your death.");
        if (w.fog > 0.4f) return pick(n, "Can't see your hand in this fog.", "Fog like this, anything could walk right up to you.");
        if (w.wind > 0.6f) return pick(n, "Hold onto your hat.", "The wind's got teeth today.");
        return null;
    }

    private String hourTalk(Npc n) {
        double h = g.state.hourOfDay();
        if (h < 9) return pick(n, "Early start for you too?", "Morning. Not my best hour.");
        if (h < 13) return pick(n, "Half the day gone already.", "Busy morning.");
        if (h < 18) return pick(n, "Long afternoon.", "The day's dragging.");
        if (h < 22) return pick(n, "Getting dark. Best not be on the road.", "Evening already. Where did the day go?");
        return pick(n, "It's late to be wandering about.", "Shouldn't you be in bed?");
    }

    /**
     * What two townsfolk say to each other in passing: news of a crime first, then whatever is on their minds - the
     * weather, a monster about, their work, the day.
     */
    public String smallTalk(Npc n, Npc other) {
        for (Memory m : n.memories) if (m.crime() && m.subject != other.id) return pick(n, "Did you hear? " + g.nameOf(m.subject) + " - " + m.kind.toLowerCase() + "!", "Word is " + g.nameOf(m.subject) + " did it. " + m.kind.charAt(0) + m.kind.substring(1).toLowerCase() + ".", "You heard about " + g.nameOf(m.subject) + "?");
        String monster = monsterTalk(n);
        if (monster != null && (n.conversations + n.id + other.id) % 3 == 0) return monster;
        String weather = weatherTalk(n);
        if (weather != null && (n.id + other.id) % 2 == 0) return weather;
        return switch ((int) Math.floorMod(n.id * 31 + other.id + (long) (g.state.minutes / 30), 5)) {
            case 0 -> n.profession != null ? trade(n) : calling(n);
            case 1 -> hourTalk(n);
            case 2 -> pick(n, "How's the family, " + other.name + "?", "Good to see you, " + other.name + ".", "Still alive, " + other.name + "?");
            case 3 -> pick(n, "Prices at the store are robbery.", "Have you tried the bread at Store A? Like stone.", "The well water's gone strange.");
            default -> n.profession != null ? trade(n) : calling(n);
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
