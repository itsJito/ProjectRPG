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
        if(rpg.law.WitnessInfluence.pending(g,n)!=null)return witness(n);
        Memory known = n.crimeKnown(g.player.id);
        int bounty = n.watch() ? g.law.bounty(g.player.id, n.homeJurisdiction) : 0;
        String greet = bounty > 0 ? "You still owe " + bounty + " Gold in " + n.homeJurisdiction + ". We have an open charge to settle."
                : known == null ? greeting(n) : known.settled
                ? "Your debt to the law is settled. I still remember what happened."
                : known.witnessed()
                ? "I saw what you did - the " + rpg.law.Law.crimeWord(known.kind) + ". Keep your distance."
                : "I've heard about you. " + (known.kind.equals("MURDER") ? "They say you've killed" : "Word is it was " + rpg.law.Law.crimeWord(known.kind)) + ". I'm watching you.";
        Dialog d = new Dialog(n.name + " - " + n.title, greet);
        d.npc = n.id; d.employment = n.employmentState();
        // a courtesy (Honor, GDD 3.5): a warm greeting to someone who will take it, once each
        if (!n.greeted && known == null && !n.hostileToPlayer && n.combatant() == n.watch()) d.choice("Good day to you! (greet them warmly)", () -> {
            n.greeted = true;
            g.honor.greeted(n.id);
            g.ai.rumors.deed("greeted " + n.name + " kindly", n.id);
            g.ai.say(n, n.role == Npc.Role.GUARD ? "And to you. Stay out of trouble." : "Oh! Good day to you, too.");
        });
        String[] tip = null;
        switch (n.role) {
            case MERCHANT -> { if (n.working()) d.choice("Let's trade.", () -> g.interact.request(g.player, n, "trade")); }
            case LAWMAN -> {
                int b = g.law.bounty(g.player.id, n.homeJurisdiction);
                if (b > 0) d.standoff=true;
                int purse = g.player.count(Items.COINS);
                if (b > 0 && purse >= b) d.choice("Pay my bounty (" + b + " coins).", () -> g.interact.request(g.player, n, "pay"));
                else if (b > 0) d.choice("Pay what I have (" + purse + " of " + b + " coins; the rest is taken in goods and time).", () -> {
                    var r = g.law.payWhatYouCan(g.player, n, g.state.newRequestId());
                    if (r.rejected()) g.chat.warn(r.reason());
                });
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
                d.choice("How does this work?", () -> g.ui.dialog(new Dialog(n.name, "Fall inside the walls and the watch brings your things to me - all but anything stolen, which goes back where it came from. Fall out in the wild and you'd best get "
                        + "back to your body quick, because anyone passing may help themselves. Either way, after ten minutes I send for what's left. What I hold of yours lies in the unfinished casket by the path: "
                        + rpg.law.Undertaker.FEE + " coins in the box to have it back, " + (rpg.law.Undertaker.FEE + rpg.law.Undertaker.OUTSIDE_SURCHARGE) + " if I had to go beyond the walls. Your own purse counts, if that's all you have. "
                        + "Can't wait? " + rpg.law.Undertaker.PROMPT + " more in the casket and I'll send for your grave straight away.")));
            }
            case BEGGAR -> {
                if (n.breadRefusal(g, g.player) == null)
                    d.choice("Offer a loaf of bread.", () -> g.interact.request(g.player, n, "give_bread"));
            }
            case HEALER -> tip = tip(n, "Where can I find supplies?", "A good meal mends more than you'd think - bread, a cooked fish, anything hot. I carry what I can for the hurt, but it never stretches far. If you come across herbs or berries, the store will pay for them, and I'd thank you for any you spare.");
            case PRIEST -> tip = tip(n, "Who tends the dead?", "The watch carries the fallen to these stones. Morrow keeps the belongings safe. Even a criminal deserves a name and a resting place.");
            case SCHOLAR -> tip = tip(n, "Where can I sell questionable goods?", "Silas keeps a quiet nook behind Store B, in DevTown. He buys stolen goods, at a price. I would keep that conversation away from the watch.");
            case HUNTER -> tip = tip(n, "Any advice for the wilderness?", "Keep clear of a predator's kill. Feed brings the herd together, and blood brings the scavengers. A horse won't pick a fight unless you hurt it.");
            case CIVIC -> { if (n.id == Ids.TELLER) g.bank.choices(n, d); }
            case THIEF -> tip = tip(n, "Got anything for me?", "Psst. Stalls are easy when nobody's looking. The trick is that nobody's looking.");
            default -> { }
        }
        // the callings of 0.1.8: each talks of their own work, as a tip from someone who does it
        if (n.profession != null) switch (n.profession) {
            case COOK -> tip = tip(n, "Any secret to cooking?", "Any fire will do, or a range. Hold it over the heat and turn it once, halfway. "
                    + "You'll burn a few at first - everyone does. The more you cook, the surer your hands get, and one day you just stop burning things.");
            case QUARRY_WORKER -> tip = tip(n, "What do you cut here?", "Building stone, block by block. Work a face square-on, from the front. "
                    + "Swing at it from the side and you'll take a chunk of whatever's next to it - I filled a sack with clay that way, my first week.");
            case BORDER_KNIGHT -> tip = tip(n, "What do you watch for?", "This is where the city's ground ends. Anything that comes over it - beasts, worse - "
                    + "meets me first, and I call the watch. Past this line the city's watch can't help you. Its warrants stop here too, mind - but word travels.");
            case SHELLFISH_GATHERER -> tip = tip(n, "When are the crabs about?", "When the water's low. Set a cage in the shallows and leave it be - "
                    + "crabs won't come to a crowd. If a bed's picked clean, give it time, or find another.");
            case RIVER_FISHERMAN -> tip = tip(n, "Any luck on the brook?", "Trout like moving water. If one spot goes quiet, try the next bend - "
                    + "that's the whole art of it. And watch the woods behind you. Things come down to drink.");
            default -> { }
        }
        // (0.3.0, gamefixes: every answer from the NPC's own day; three or four choices, not a wall of them) how their day has
        // been and what they have heard - always; their own work, as it has gone today; and one thing for the moment: what
        // shook them, their hurt, their hunger - or, on a quiet day, one of the everyday questions in turn
        if (n.role != Npc.Role.BANDIT && n.role != Npc.Role.BRUTE) {
            d.choice("How's your day been?", () -> g.ui.dialog(new Dialog(n.name, day(n))));
            d.choice("What have you heard?", () -> g.ui.dialog(new Dialog(n.name, news(n))));
            final String[] t = tip != null ? tip : workTip(n);
            if (t != null) d.choice(t[0], () -> g.ui.dialog(new Dialog(n.name, t[1] + workToday(n))));
            situational(n, d, t == null);
        } else d.choice("What do you want?", () -> g.ui.dialog(new Dialog(n.name, day(n))));
        d.closeChoice("Goodbye.", () -> { });
        return d;
    }

    // ------------------------------------------------------------------ (0.3.0) the day, told from the Diary
    private static String[] tip(Npc n, String question, String answer) { return new String[]{question, answer}; }

    /** A calling with no role tip of its own: asked about their work. */
    private String[] workTip(Npc n) {
        if (n.profession == null && n.role != Npc.Role.WORKER && n.role != Npc.Role.FARMER && n.role != Npc.Role.MERCHANT && n.role != Npc.Role.GUARD
                && n.role != Npc.Role.LAWMAN && n.role != Npc.Role.CARTER && n.role != Npc.Role.UNDERTAKER && n.role != Npc.Role.VILLAGER) return null;
        return new String[]{n.watch() ? "How's the watch?" : "How's work?", n.watch() ? pick(n, "Same streets, same faces. ", "Long hours on these stones. ") : pick(n, "It keeps me fed. ", "Honest work, mostly. ", "It's work. ")};
    }

    /** What of their work they did today, said after the tip (empty when nothing of it). */
    private String workToday(Npc n) {
        var done = n.diary.today(g.state.minutes);
        for (int i = done.size() - 1; i >= 0; i--) {
            String w = done.get(i).what();
            if (w.startsWith("sold") || w.startsWith("worked") || w.startsWith("cut") || w.startsWith("walked my round") || w.startsWith("laid") || w.startsWith("mended")
                    || w.contains("eggs") || w.contains("trough") || w.startsWith("set my snares"))
                return " Today I " + w + (done.get(i).times() > 1 ? ", a few times over" : "") + ".";
        }
        return n.goal == Brain.Goal.WORK ? " I'm only just getting to it today." : "";
    }

    /** How their day has been: a mood, what they did (in order, with the time of day), and how they are now. */
    String day(Npc n) {
        double now = g.state.minutes, hour = g.state.hourOfDay();
        var done = n.diary.today(now);
        boolean hurt = n.hp < n.maxHp() * 0.8f, shaken = shook(n) != null, hungry = n.hunger > 0.6f, tired = n.fatigue > 0.7f || hour >= 21;
        boolean rain = g.timeSystem != null && g.timeSystem.weather.rain > 0.4f;
        StringBuilder b = new StringBuilder();
        b.append(hurt || shaken ? pick(n, "Rough, if I'm honest. ", "Not my best. ", "Could have been better. ")
                : done.size() >= 3 ? pick(n, "Busy. ", "Long one. ", "Full, I'd say. ") : done.isEmpty() ? (hour < 9 ? pick(n, "Only just begun. ", "Early yet. ") : pick(n, "Quiet. ", "Slow, so far. "))
                : pick(n, "Not bad at all. ", "Can't complain. ", "Fair enough. "));
        if (g.state.weekday() == 0 && hour >= 12 && hour < 20 && !n.watch()) b.append("It's Solday - the week's rest. ");
        // up to three things, earliest first, each with the time of day it happened
        int from = Math.max(0, done.size() - 3);
        String lastWhen = "";
        for (int i = from; i < done.size(); i++) {
            var e = done.get(i);
            double h = (e.minutes() % 1440) / 60.0;
            String when = h < 12 ? "this morning" : h < 17 ? "this afternoon" : "this evening";
            String part = (i == from ? (when.equals(lastWhen) ? "" : capital(when) + " ") : i == done.size() - 1 ? (when.equals(lastWhen) ? "and then " : "and " + when + " ") : (when.equals(lastWhen) ? "then " : when + " "));
            b.append(i == from ? part + "I " : part + "I ").append(e.what()).append(e.times() > 1 ? " a few times" : "").append(i == done.size() - 1 ? ". " : ", ");
            lastWhen = when;
        }
        if (shaken) b.append(shook(n)).append(' ');
        if (hurt) b.append(n.cs.lastAttacker != 0 && g.actor(n.cs.lastAttacker) != null ? "Got hurt, too - " + g.nameOf(n.cs.lastAttacker) + " saw to that. " : "Took a knock, too. ");
        if (rain) b.append(pick(n, "And this rain doesn't help. ", "Wet through, besides. "));
        if (hungry) b.append(pick(n, "I could eat a horse.", "I haven't eaten in too long.", "Starving, truth be told."));
        else if (tired) b.append(pick(n, "I'll sleep well tonight.", "Nearly done, thank the stones.", "Ready for my bed."));
        return b.toString().trim();
    }

    /** What shook them today (a crime or a beast they saw themselves), in their words; null when nothing did. */
    private String shook(Npc n) {
        double since = g.state.minutes - ((g.state.minutes - 5 * 60) % 1440 + 1440) % 1440;
        for (int i = n.memories.size() - 1; i >= 0; i--) {
            Memory m = n.memories.get(i);
            if (m.minutes < since || !m.witnessed()) continue;
            if (m.kind.equals("BEAST_ATTACK")) return "I saw a creature go for " + g.nameOf(m.victim) + ". Still shaking.";
            if (m.crime()) return "I saw " + (m.subject == g.player.id ? "you" : g.nameOf(m.subject)) + " - " + rpg.law.Law.crimeWord(m.kind) + ", with my own eyes.";
        }
        return null;
    }

    /** The fourth choice: what is on them now, or one of the everyday questions in turn. */
    private void situational(Npc n, Dialog d, boolean twoSlots) {
        String shaken = shook(n);
        java.util.List<Runnable> picks = new java.util.ArrayList<>();
        if (shaken != null) d.choice("You look shaken. What happened?", () -> g.ui.dialog(new Dialog(n.name, shaken + " " + pick(n, "I keep seeing it.", "I told the watch, for what that's worth.", "You'd best be careful about."))));
        else if (n.hp < n.maxHp() * 0.7f) d.choice("You're hurt. Are you all right?", () -> g.ui.dialog(new Dialog(n.name, (n.cs.lastAttacker != 0 ? g.nameOf(n.cs.lastAttacker) + " did this. " : "") + pick(n, "I'll mend. The healer will see to it.", "It looks worse than it is.", "Nothing a meal and a night won't fix."))));
        else if (n.hunger > 0.65f) d.choice("You look hungry.", () -> g.ui.dialog(new Dialog(n.name, "I am. " + (n.diary.did(g.state.minutes, "had some") ? "I've had a bite today, but not enough." : "I've not eaten all day.") + " If you've bread to spare, I'd not say no.")));
        else picks.add(null);
        if (picks.isEmpty() && !twoSlots) return;
        // a quiet day: one (or, with no work to ask about, two) of the everyday questions, turned about day by day
        int seed = Math.floorMod((int) (g.state.minutes / 1440) * 7 + (int) n.id, 6);
        int count = (picks.isEmpty() ? 0 : 1) + (twoSlots ? 1 : 0);
        for (int k = 0; k < count; k++) switch ((seed + k) % 6) {
            case 0 -> d.choice("Could you point me somewhere?", () -> g.ui.dialog(directions(n)));
            case 1 -> d.choice("Is it safe around here?", () -> g.ui.dialog(new Dialog(n.name, dangers(n))));
            case 2 -> d.choice("What do you make of me?", () -> g.ui.dialog(new Dialog(n.name, standing(n))));
            case 3 -> d.choice("Is anything in short supply?", () -> g.ui.dialog(new Dialog(n.name, shortages(n))));
            case 4 -> d.choice("You do good work here. (compliment them)", () -> word(n, true));
            default -> d.choice("You look like you've never done a day's work. (mock them)", () -> word(n, false));
        }
    }
    private static String capital(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    // ------------------------------------------------------------------ the universal choices (0.2.9)
    /** The places one asks the way to, by name and where they are (the nearest of each kind is given). */
    private java.util.List<Object[]> places(Npc n) {
        java.util.List<Object[]> l = new java.util.ArrayList<>();
        if (g.econ.shopA != null) l.add(new Object[]{"the store", g.econ.shopA.counterX, g.econ.shopA.counterZ});
        if (g.econ.shopB != null) l.add(new Object[]{"the store", g.econ.shopB.counterX, g.econ.shopB.counterZ});
        l.add(new Object[]{"the bank", rpg.world.DevMap.BANK_TELLER[0], rpg.world.DevMap.BANK_TELLER[1]});
        l.add(new Object[]{"an altar", rpg.world.DevMap.ALTAR[0] + 0.5f, rpg.world.DevMap.ALTAR[1] + 0.5f});
        l.add(new Object[]{"an altar", rpg.world.Expanse.ALTAR[0], rpg.world.Expanse.ALTAR[1]});
        l.add(new Object[]{"the well", rpg.world.DevMap.WELL[0] + 0.5f, rpg.world.DevMap.WELL[1] + 0.5f});
        for (Npc o : g.npcs) {
            if (!o.alive) continue;
            if (o.role == Npc.Role.LAWMAN) l.add(new Object[]{"the watch", o.postX, o.postZ});
            if (o.role == Npc.Role.UNDERTAKER) l.add(new Object[]{"the undertaker", o.postX, o.postZ});
            if (o.role == Npc.Role.HEALER) l.add(new Object[]{"the healer", o.postX, o.postZ});
        }
        return l;
    }

    /** The way to each kind of place from where they stand: a compass point and how far. */
    private Dialog directions(Npc n) {
        Dialog d = new Dialog(n.name, pick(n, "Where to?", "Lost, are you? Where are you headed?", "I know the place well enough. Where do you want to go?"));
        d.npc = n.id;
        java.util.Map<String, Object[]> nearest = new java.util.LinkedHashMap<>();
        for (Object[] p : places(n)) {
            float px = (float) p[1], pz = (float) p[2];
            Object[] had = nearest.get((String) p[0]);
            if (had == null || Math.hypot(px - n.x, pz - n.z) < Math.hypot((float) had[1] - n.x, (float) had[2] - n.z)) nearest.put((String) p[0], p);
        }
        for (Object[] p : nearest.values()) {
            String where = (String) p[0];
            float px = (float) p[1], pz = (float) p[2];
            d.choice("To " + where + ".", () -> g.ui.dialog(new Dialog(n.name, way(n, where, px, pz))));
        }
        d.closeChoice("Never mind.", () -> { });
        return d;
    }

    /** "The store's north-east of here, a short walk" - a direction (north is up the map) and a distance in plain words. */
    String way(Npc n, String where, float x, float z) {
        float dx = x - n.x, dz = z - n.z, d = (float) Math.hypot(dx, dz);
        if (d < 6) return pick(n, "You're standing by " + where + ".", "Look about you - " + where + "'s right here.");
        String[] points = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        int i = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(dx, -dz)) / 45f), 8);
        String far = d < 25 ? "just a short walk" : d < 60 ? "a fair walk" : d < 110 ? "a long walk" : "a good way off";
        String town = rpg.world.DevMap.jurisdiction(x, z);
        String in = town.equals(rpg.world.DevMap.jurisdiction(n.x, n.z)) || town.equals(rpg.world.DevMap.WILDS) ? "" : ", in " + town;
        return pick(n, "Head " + points[i] + " for " + where + in + " - " + far + ".", "That's " + points[i] + " of here" + in + ", " + far + ".",
                Character.toUpperCase(where.charAt(0)) + where.substring(1) + "? Go " + points[i] + in + ". It's " + far + ".");
    }

    /** What they know of danger: a beast that has struck near them lately, the lairs they would keep away from. */
    String dangers(Npc n) {
        StringBuilder b = new StringBuilder();
        for (Memory m : n.memories) {
            if (!m.kind.equals("BEAST_ATTACK") || g.state.minutes - m.minutes > 1440) continue;
            rpg.actor.Actor beast = g.actor(m.subject);
            b.append("Not long ago I saw ").append(beast == null ? "a beast" : "a " + beast.name.toLowerCase()).append(" attack ").append(g.nameOf(m.victim)).append(". ");
            break;
        }
        if (n.fearOf != 0 && g.actor(n.fearOf) != null && g.time - n.fearSeenAt < 120) b.append("There's ").append(g.actor(n.fearOf).name.toLowerCase().startsWith("a") ? "an " : "a ").append(g.actor(n.fearOf).name.toLowerCase()).append(" about - I've just seen it. ");
        var lair = g.monsters == null ? null : g.monsters.lairs.stream().filter(l -> !l.name.isEmpty()).min(java.util.Comparator.comparingDouble(l -> Math.hypot(l.x - n.x, l.z - n.z))).orElse(null);
        if (lair != null) b.append("Keep clear of the ").append(lair.name.toLowerCase()).append(" - ").append(way(n, "it", lair.x, lair.z).replaceFirst("^Head ", "head ").replaceFirst("^That's ", "it's ")).append(' ');
        if (g.law.lockdown(n.homeJurisdiction)) b.append("And the town's shut up tight - the watch is looking for someone. ");
        if (b.length() == 0) b.append(pick(n, "Quiet enough, these days. ", "Nothing I've seen. Touch wood. "));
        return b.toString().trim();
    }

    /** What they make of the player: their opinion from all they know, said as they would say it. */
    String standing(Npc n) {
        float o = g.ai.rumors.opinion(n);
        Memory bad = n.crimeKnown(g.player.id);
        if (o <= -0.5f) return pick(n, "I'd sooner you weren't here, if I'm honest.", "I know what you are. I'd thank you to keep your distance.");
        if (o < -0.1f) return bad != null ? "I've heard about the " + rpg.law.Law.crimeWord(bad.kind) + ". I'll judge for myself, but you've a way to go." : pick(n, "I'm not sure about you yet.", "You've not given me much reason to trust you.");
        if (o < 0.15f) return n.knowsPlayer ? pick(n, "You seem decent enough.", "I've no quarrel with you.") : pick(n, "I don't know you, stranger. That's not a bad thing - yet.", "A new face. We'll see.");
        if (o < 0.45f) return pick(n, "People speak well of you. That counts for something here.", "You've done right by folk. I've noticed.");
        return pick(n, "You're good people. If you ever need a hand, ask.", "Glad to know you. Truly.");
    }

    /** What the local store is short of (under the stock it means to keep), as a hint for someone with goods to sell. */
    String shortages(Npc n) {
        var shop = g.econ.localShop(n);
        if (shop == null) return "There's no store near enough for me to say.";
        java.util.List<String> short_ = new java.util.ArrayList<>();
        for (var e : shop.target.entrySet()) {
            var def = rpg.item.Items.get(e.getKey());
            if (def != null && shop.stock.count(def) < e.getValue() / 2) short_.add(def.name.toLowerCase());
            if (short_.size() >= 3) break;
        }
        if (short_.isEmpty()) return pick(n, shop.name + " is well stocked just now. Nobody's crying out for anything.", "Nothing's short that I've heard. Times are steady.");
        return shop.name + " is running low on " + String.join(", ", short_) + ". They'd pay fair for any brought in.";
    }

    /** A word of the player's own: kindly said, they think a little better of them; a jibe, a little worse - remembered. */
    void word(Npc n, boolean kind) {
        // (0.3.0) the face answers first: pleased by a kind word; a jibe, a sneer from the hard, hurt from the soft
        rpg.render.Faces.react(g, n, kind ? rpg.render.Faces.Mood.HAPPY : n.combatant() ? rpg.render.Faces.Mood.ANGRY : rpg.render.Faces.Mood.SAD, 4);
        int day = (int) (g.state.minutes / 1440);
        boolean already = n.memories.stream().anyMatch(m -> (m.kind.equals("KIND_WORD") || m.kind.equals("SLIGHT")) && m.subject == g.player.id && (int) (m.minutes / 1440) == day);
        String reply;
        if (kind) reply = already ? pick(n, "You said as much already. But thank you.", "Twice in a day? Now I'm suspicious.") : pick(n, "That's kind of you to say. It isn't often anyone does.", "Well. Thank you. That makes the day go easier.", "Hah - flattery. It works, mind.");
        else reply = n.combatant() ? pick(n, "Say that again and see what happens.", "Careful. I've put people down for less.") : pick(n, "Well, that was uncalled for.", "And you're a charmer, aren't you.", "I'll remember that.");
        if (!already) {
            n.remember(new Memory(kind ? "KIND_WORD" : "SLIGHT", g.player.id, n.id, rpg.world.DevMap.jurisdiction(n.x, n.z), g.state.minutes, 1f, "witnessed", 0, 0, kind ? "was kind to me" : "mocked me"), 24);
            n.knowsPlayer = true;
            if (kind) g.honor.greeted(n.id);
        }
        g.ai.say(n, reply);
        if (g.ui != null) g.ui.dialog(new Dialog(n.name, reply));
    }

    public Dialog witness(Npc n) {
        var memory=rpg.law.WitnessInfluence.pending(g,n);
        Dialog d=new Dialog(n.name,"I saw what happened. Why should I keep it from the watch?");d.npc=n.id;d.standoff=true;
        if(memory!=null){
            d.choice("Please don't report me.",()->appeal(n,"witness_persuade"));
            d.choice("Keep quiet, or you'll regret it.",()->appeal(n,"witness_threaten"));
            int price=rpg.law.WitnessInfluence.price(g,memory);
            d.choice("Would "+price+" Gold persuade you to keep quiet?",()->appeal(n,"witness_bribe"));
        }
        d.closeChoice("Never mind.",()->{});return d;
    }
    private void appeal(Npc n,String method){
        var result=rpg.law.WitnessInfluence.attempt(g,n,g.player,method,g.state.newRequestId());
        Dialog response=new Dialog(n.name,result.reason());response.npc=n.id;response.standoff=true;
        response.closeChoice("Leave.",()->{});g.ui.dialog(response);
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
        // (0.2.1, gamefixes: more dialogue variety) what they are about right now comes first when it is worth saying;
        // DevPalace's people talk of their own city; a second word the same day is met as one
        String doing = doing(n);
        if (doing != null) line = doing;
        else if (rpg.world.DevMap.PALACE.equals(n.homeJurisdiction) && Math.floorMod(n.conversations + n.id, 3) == 0) line = palace(n);
        if (n.talkedDay == (int) (g.state.minutes / 1440) && n.conversations > 1 && doing == null)
            line = pick(n, "You again. ", "Back so soon? ", "Still here, then. ", "Twice in a day - you must want something. ") + line;
        n.talkedDay = (int) (g.state.minutes / 1440);
        String remark = remark(n);
        return remark == null ? line : line + " " + remark;
    }

    /** What they are in the middle of, if it shapes what they say (null: nothing worth a word). */
    private String doing(Npc n) {
        return switch (n.goal) {
            case SHELTER -> pick(n, "Get in out of this rain, if you've any sense.", "Not now - I'm soaked through already.", "Rain like this, a roof is worth more than gold.");
            case PRAY -> pick(n, "Hush. I'm at my prayers.", "A moment - let me finish.", "Come back when I've said my piece to the altar.");
            case SOCIALIZE -> pick(n, "We were just talking. Join us, if you like.", "Oh - I was miles away, gossiping.", "You've caught us passing the time.");
            case PATROL -> pick(n, "Walk with me, if you're talking. I've a round to keep.", "Make it quick. The round won't walk itself.");
            case EAT -> pick(n, "Let a body eat in peace.", "Mm. Sorry - mouth full.");
            case BANK -> pick(n, "I'm off to the bank. Coin doesn't keep itself.", "Errands. The bank won't wait.");
            default -> null;
        };
    }

    /** DevPalace's people on their own city: what stands in it and who rules it. */
    private String palace(Npc n) {
        return switch (n.role) {
            case GUARD, LAWMAN, KNIGHT -> pick(n, "The legion keeps the gates. You'll keep to the decumanus.", "Mind yourself near the palace steps. The emperor's household is watchful.", "Disorder in the forum is answered at the basilica.");
            case MERCHANT -> pick(n, "Everything worth having passes through the market hall sooner or later.", "Prices are fair here. The praetor sees to that.");
            case PRIEST -> pick(n, "The temple doors are open from dawn. Come and be quiet a while.", "Leave something at the altar, if only a thought.");
            case CIVIC -> pick(n, "The senate sits when it sits. Patience is the first civic virtue.", "The emperor hears petitions from the throne, when he is minded to.");
            default -> pick(n, "Have you seen the forum fountain? Best water in the city.", "The baths are the only warm place on a cold night.", "Walk the cardo to the palace if you want to see grandeur. Walk it back if you want to see the rest of us.", "The temple bell, the market crowd, the legion's boots. That's DevPalace.");
        };
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
            case UNDERTAKER -> g.undertaker.held() > 0 ? "Your belongings are in the casket by the path. Leave the fee in the box." : pick(n, "Mind your step among the stones.", "Every name here belonged to someone. Remember that.", "If you need to make arrangements, I'll hear you out.");
            case FARMER -> pick(n, "Shut the gate behind you. They take any chance to wander.", "The stock eats before I do, most mornings.", "Hands away from the feed unless you're helping.", "A fox has been at the coop again. I'll have its hide.");
            case BANDIT -> pick(n, "That's close enough. Keep your hands where I can see them.", "You're on the wrong road, friend.", "Looking for trouble, or merely lost?");
            case BEGGAR -> pick(n, "A loaf to spare? I'll remember the kindness.", "The cold finds you quicker without a roof.", "Spare a thought, if not a coin.");
            case HEALER -> pick(n, "Let me see that wound. Have you eaten?", "I can share what I have. There isn't much.", "Guam for the blood, rest for the rest.");
            case PRIEST -> pick(n, "We keep their names, even when their stories are lost.", "Walk gently among the stones.", "The altar is open to anyone who comes in peace.");
            case HUNTER -> pick(n, "The wind carries your scent farther than your footsteps.", "Never stand between a mother and her young.", "Every set of tracks is a question. I'm still answering this morning's.", "The woods are quiet when something big is near.");
            case FISHERMAN -> pick(n, "The water's been kind this week. It won't last.", "Wet boots and an empty creel, some days.", "Fish don't care who's watching.");
            case MINER -> pick(n, "There's ore in the hills, if you've the back for it.", "A good pick is worth more than a good friend. Some days.", "The rock gives up nothing without a fight.");
            case CRAFTSMAN -> pick(n, "Bring me the stock and I'll bring you the work.", "Every tool on this bench has a story. Most of them involve my thumbs.", "Good work outlasts the hands that made it.");
            case KNIGHT -> pick(n, "Keep your blade clean and your word cleaner.", "There's a road to watch. There always is.", "I serve. That's the whole of it.");
            case CIVIC -> g.bank.open() ? pick(n, "Welcome to the Bank of ASHENWRATH. How may I help?", "Deposits, withdrawals, a new vault? Step up to the counter.", "Good day. Your things are safe with us.")
                    : pick(n, "We're closed just now. Come back when I'm at the counter.", "The counter's shut. The vaults aren't going anywhere.", "Banking hours are over. Your coin is sleeping, as I'd like to be.");
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
        // (0.2.2) blood they saw spilled is no mystery to them: they saw what did it
        if (p.bloodied >= 0.4f && g.ai.bloodExplained(n, p)) {
            var w = n.aware.get(p.id); rpg.actor.Actor by = g.actor(w.bloodBy);
            String what = by == null ? "that" : by instanceof rpg.monster.Monster mo ? "that " + mo.kind.title.toLowerCase() : by instanceof rpg.animal.Animal an ? "that " + an.kind() : by.name;
            return watch ? pick(n, "I saw " + what + " get you. Get yourself patched up.", "That was a nasty one. Are you standing all right?")
                    : outlaw ? "Took a beating, didn't you?" : pick(n, "I saw what " + what + " did to you. Gods, are you all right?", "You need a healer after that.");
        }
        if (p.bloodied >= 0.4f) return watch ? "Is that your blood, or someone else's?" : outlaw ? "Busy day?" : pick(n, "You're covered in blood!", "Is - is that blood?", "Please tell me that isn't yours.");
        if (town && g.combat.drawn(p) && !outlaw && (n.conversations + n.id) % 2 == 0) return watch ? "Weapons sheathed while you're in town. I won't ask twice." : pick(n, "Put that away, would you? You're frightening people.", "There's no call for a blade in the street.");
        if (town && p.sheathed && p.sheathable() && watch && (n.conversations + n.id) % 5 == 0) return "Good - keep it sheathed while you're within the walls.";
        String hurt = woundTalk(n, p, watch, outlaw);
        if (hurt != null && (n.conversations + n.id) % 2 == 1) return hurt;
        String gear = gearTalk(n, p, watch, outlaw);
        if (gear != null && (n.conversations + n.id) % 4 == 2) return gear;
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

    /**
     * (0.2.2, gamefixes: more dialogue variety) What they can see of where the player is hurt - the walk, the arm held
     * in, the knock to the head - each in their own manner.
     */
    private String woundTalk(Npc n, rpg.actor.Actor p, boolean watch, boolean outlaw) {
        float[] inj = p.injury;
        if (inj == null) return null;
        float leg = Math.max(inj[4], inj[5]), arm = Math.max(inj[2], inj[3]);
        if (leg > 0.35f) return n.role == Npc.Role.HEALER ? "You're limping. Sit, let me look at that leg." : outlaw ? "Limping, eh? Wouldn't get far if someone came after you." : pick(n, "You're favouring that leg.", "That's a bad limp. Did something bite you?", "Mind how you go on that leg.");
        if (arm > 0.35f) return watch ? "That arm's no good for a sword like that. Get it seen to." : pick(n, "You're holding that arm like it's broken.", "Your arm - is it bad?");
        if (inj[0] > 0.3f) return pick(n, "You took a knock to the head? Your eyes look wrong.", "Steady - you're swaying on your feet.");
        if (inj[1] > 0.4f) return pick(n, "You're bent double. Was it a blade?", "Hand pressed to your side like that - you're bleeding, aren't you?");
        return null;
    }

    /** What they make of what the player carries put away: a bow on the back, a great weapon, a shield. */
    private String gearTalk(Npc n, rpg.actor.Actor p, boolean watch, boolean outlaw) {
        if (!p.sheathed || p.main() == null) return null;
        var f = p.main().family;
        boolean hunter = n.role == Npc.Role.HUNTER;
        if (f == rpg.item.ItemDef.Family.BOW) return hunter ? "Nice bow. What do you draw on it?" : pick(n, "A bow on your back - you a hunter?", "Bring me back a rabbit, archer.");
        if (f == rpg.item.ItemDef.Family.CROSSBOW) return n.role == Npc.Role.KNIGHT ? "A crossbow. A coward's weapon, some say. Not me." : "Heavy thing, that crossbow, to carry about.";
        if (f == rpg.item.ItemDef.Family.STAFF) return n.role == Npc.Role.SCHOLAR ? "A staff! Are you one of the learned, then?" : pick(n, "A staff? Are you a wizard or a shepherd?", "Mind you don't poke anyone with that staff.");
        if (p.main().twoHanded()) return watch ? "That's a lot of steel on your back. Keep it there." : outlaw ? "Big blade. Bet it's slow." : pick(n, "That's a lot of steel to be carrying about.", "Who are you planning to fight with that?");
        if (p.off() != null && p.off().family == rpg.item.ItemDef.Family.SHIELD) return watch ? "Sword at your hip, shield on your back. Good. That's how it's done here." : null;
        return null;
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
            case 3 -> pick(n, "Prices at the store are robbery.", "Have you tried the bread at Store A? Like stone.", "The well water's gone strange.",
                    "The undertaker's put a casket out by the path. Gives me the shivers.", "Did you see the farm's new gate? Mara's proud of it.",
                    "The bank's full of fish, I heard. Someone had nowhere else to put his catch.", "The guards want every blade sheathed in town now.");
            default -> n.profession != null ? trade(n) : calling(n);
        };
    }

    /**
     * What they have heard (0.3.0: from the world as it stands): crimes they know of, witnessed or rumoured; a beast they
     * saw attack someone; a terror the town is alarmed about; what happened in town today that they would have heard -
     * a body buried, a birth on the farm, a horse broken in, the store running short; and what they are about now.
     */
    private String news(Npc n) {
        StringBuilder b = new StringBuilder();
        int told = 0;
        for (Memory m : n.memories) if (m.crime() && told < 2) { b.append(g.ai.rumors.line(n, m)).append(' '); told++; }
        var alarm = g.ai.alarmFor(n);
        if (alarm != null && g.actor(alarm.monster) instanceof rpg.monster.Monster mo) { b.append("There's a ").append(mo.kind.title.toLowerCase()).append(" loose near ").append(alarm.place).append(". Stay indoors. "); told++; }
        // the day's talk of the town: events heard of within earshot of the place they live
        double since = g.state.minutes - 600;
        java.util.List<String> heard = new java.util.ArrayList<>();
        for (var e : g.state.recent(200)) {
            if (e.gameMinutes() < since || heard.size() >= 2) continue;
            String j = e.jurisdiction();
            if (j != null && !j.equals(n.homeJurisdiction)) continue;
            String pl = e.payload() == null ? "" : e.payload();
            String line = switch (e.type()) {
                case "BodyBuried" -> "Morrow laid " + pl.replace("'s body", "") + " to rest.";
                case "AnimalBorn" -> "There's a new " + (pl.contains("foal") ? "foal" : pl.contains("calf") ? "calf" : pl.contains("lamb") ? "lamb" : "young one") + " up at the farm.";
                case "MountTamed" -> g.nameOf(e.actor()) + " broke in a " + pl.toLowerCase() + ". Rode it right through town.";
                case "AlarmRaised" -> "Somebody saw a " + pl.split(" sighted")[0].toLowerCase() + ".";
                case "ShipmentDelivered" -> "The caravan came in.";
                case "TorchLit" -> null;
                default -> null;
            };
            if (line != null && !heard.contains(line)) heard.add(line);
        }
        for (String h : heard) { b.append(h).append(' '); told++; }
        if (told == 0) b.append(pick(n, "Nothing worth repeating. ", "Not much. It's been an ordinary day. ", "Quiet, as days go. "));
        b.append(switch (n.goal) { case WORK -> "I've my own work to see to."; case SLEEP -> "I should be getting some rest."; case REPORT_CRIME -> "I have business with the watch."; case COLLECT -> "There's a body that needs seeing to."; case AID -> "Someone nearby could use a hand."; default -> pick(n, "Keep your ears open. News travels with the people who carry it.", "Ask at the store - they hear everything.", "That's all I know."); });
        return b.toString();
    }

    private String reports(Npc lawman) {
        StringBuilder b = new StringBuilder();
        // the last few, in his own words ("came to me", not his own name)
        java.util.List<String> mine = new java.util.ArrayList<>();
        for (String r : g.law.reportsLog) if (r.contains(" came to " + lawman.name + " ")) mine.add(r.replace(" came to " + lawman.name + " ", " came to me "));
        for (int i = Math.max(0, mine.size() - 4); i < mine.size(); i++) b.append(mine.get(i)).append(". ");
        return b.length() == 0 ? "Nobody has come to me with anything. Quiet times." : b.toString();
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
