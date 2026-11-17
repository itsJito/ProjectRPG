package rpg.ai;

import rpg.Rpg;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.objects.Node;
import rpg.world.WorldMap;

/**
 * (0.4.1, gamefixes: every NPC able to perform their tasks, with a simpler simulation for those beyond the fog - what
 * they did through the day, at a fraction of the cost of the ones in play) The off-stage day.
 * <p>
 * A resident past the fog's far edge (and a margin, so nobody pops in or out where they could be seen) gives up their
 * body - no mover, no route searches, no senses - and lives their day as a schedule: asleep at home by night, at work
 * through the morning and the afternoon, home at midday to eat, about home in the evening. Work is the real work of their
 * trade, resolved by the same rules as in play, a step every ten game minutes: a woodcutter's tree loses its logs, a
 * miner's rock its ore, a fisher's spot its fish, a grower's ripe rows are cut and the bare ones sown; what they gather
 * fills their pack, and a full pack is sold at their own town's shop (its stock and their coin move). Each step stands
 * them where it happened, so when the player comes near again they are there - at the rock, at the counter, in bed - and
 * take up their day in full. Their diary says what they did.
 * <p>
 * Only on ShadowVeil (the devmap is small enough to keep everyone in play); companions, posses, hunters on a hunt, anyone
 * in a fight, a flight, a pursuit, a conversation, a saddle or another's arms stay in play wherever they are.
 */
final class Offstage {
    private final Rpg g;
    private final AiSystem ai;
    /** How far from home (m) a shop may be for an off-stage seller to walk the haul there and back. */
    static final float SELL_REACH = rpg.Tuning.f("offstage.sellReach", 400f);
    /** How far (m) from the player a resident leaves play, and how near they come back into it (hysteresis between). */
    static final float OUT = rpg.Tuning.f("offstage.unload", 150f), IN = rpg.Tuning.f("offstage.reload", 125f);
    private double checkAt;
    /** Steps resolved (the stress test's tally) and residents off-stage now. */
    int steps, count;

    Offstage(Rpg g, AiSystem ai) { this.g = g; this.ai = ai; }

    /** Twice a second: who leaves play and who comes back into it. */
    void update() {
        if (!WorldMap.shadowveil() || g.player == null || g.time < checkAt) return;
        checkAt = g.time + 0.5;
        int off = 0;
        for (Npc n : g.npcs) {
            if (!n.alive) continue;
            float d = (float) Math.hypot(n.x - g.player.x, n.z - g.player.z);
            if (n.loaded && d > OUT && free(n)) { ai.unload(n); n.offstage = true; n.diary.note(g.state.minutes, "(off-stage) " + activity(n)); }
            // (0.4.3, gamefixes: placeholder sleeping destinations until the interiors come) a resident come home for the
            // night goes in at their door - off-stage until morning, wherever the player is - and comes out there at dawn
            // (0.4.4) everyone with a bed indoors now walks in to it and sleeps in it, seen by whoever comes in
            else if (n.loaded && night() && n.goal == Brain.Goal.SLEEP && free(n) && rpg.world.shadowveil.Census.of(n.id) != null && !Float.isFinite(n.homeY)
                    && Math.hypot(n.x - n.homeX, n.z - n.homeZ) < 1.8f) {
                ai.unload(n); n.offstage = true; n.indoors = true; n.diary.note(g.state.minutes, "(indoors) gone in for the night");
            }
            else if (!n.loaded && n.offstage && d < IN && !(n.indoors && night())) { ai.reload(n); n.offstage = false; n.indoors = false; }
            else if (!n.loaded && n.indoors && !night() && d >= IN) n.indoors = false;
            if (!n.loaded && n.offstage) off++;
        }
        count = off;
    }

    /** The townsfolk's night (the census's hours: in at bedtime, out at waking). */
    private boolean night() {
        var st = rpg.world.shadowveil.Atlas.map(rpg.world.shadowveil.Atlas.get().map("census"), "stage");
        double h = g.state.hourOfDay(), bed = rpg.world.shadowveil.Atlas.d(st, "bedHour", 22), wake = rpg.world.shadowveil.Atlas.d(st, "wakeHour", 6);
        return h >= bed || h < wake;
    }

    /** Whether a resident can leave play now (nothing under way that must be seen through in full). */
    private boolean free(Npc n) {
        if (n.companionOf != 0 || n.hunt != null || n.posse || n.riding != 0 || n.carriedBy != 0 || n.summonOwner != 0) return false;
        if (n.reacting || n.traversal != null || n.cs.busy() || g.interact.of(n) != null) return false;
        return switch (n.goal) {
            case FIGHT, FLEE, SEARCH, INVESTIGATE, REPORT_CRIME, TIP_OFF, HIDE, RETURN -> false;
            default -> true;
        };
    }

    /** What the hour has a resident doing, in a few words. */
    private String activity(Npc n) {
        double h = g.state.hourOfDay();
        return h < 6 || h >= 22 ? "asleep at home" : h < 7 ? "up and about at home" : h >= 12 && h < 13 ? "at home for the midday meal" : h >= 18 ? "about home for the evening" : "at work";
    }

    /** One step (ten game minutes) of every off-stage resident's day, at the clock's minute. */
    void step(double minute) {
        if (!WorldMap.shadowveil()) return;
        double h = (minute % 1440) / 60.0;
        for (Npc n : g.npcs) {
            if (n.loaded || !n.alive || !n.offstage) continue;
            steps++;
            if (h < 6 || h >= 22) { atFloor(n, n.homeX, n.homeY, n.homeZ, n.homeX, n.homeZ); n.fatigue = Math.max(0, n.fatigue - 0.08f); note(n, minute, "asleep at home"); continue; }
            if (h >= 12 && h < 13 || h < 7) { atFloor(n, n.homeX, n.homeY, n.homeZ, n.homeX, n.homeZ); n.hunger = Math.max(0, n.hunger - 0.3f); note(n, minute, h < 7 ? "up and about at home" : "home for the midday meal"); continue; }
            if (h >= 18) {
                String did = g.repairs == null ? null : g.repairs.offstage(n);
                if (did == null) atFloor(n, n.homeX + (float) Math.cos(n.id) * 3, n.homeY, n.homeZ + (float) Math.sin(n.id) * 3, n.homeX, n.homeZ);
                note(n, minute, did != null ? did : "about home for the evening"); continue;
            }
            work(n, minute);
            n.hunger = Math.min(1, n.hunger + 0.01f); n.fatigue = Math.min(1, n.fatigue + 0.01f);
        }
    }

    private void note(Npc n, double minute, String what) { n.lastOutcome = "(off-stage) " + what; n.diary.note(minute, n.lastOutcome); }
    /**
     * (0.4.8, gamefixes: NPCs could not complete their daily tasks - a scholar whose bed is upstairs came back into play
     * shut in the hollow of his manor's turret) about home, or at a post, on the bed's or the post's own floor (the column's top was a roof or the
     * lane), and never somewhere shut off from it: a spot that is stays where they were.
     */
    private void atFloor(Npc n, float x, float floor, float z, float fallX, float fallZ) {
        if (!Float.isFinite(floor)) { at(n, x, z); return; }
        float y = g.vox.navigation.standY(x, floor, z);
        if (!Float.isFinite(y) || Math.abs(y - floor) > 1.2f) { x = fallX; z = fallZ; y = floor; }
        place(n, x, y, z);
    }
    private void at(Npc n, float x, float z) {
        float[] p = rpg.world.shadowveil.ShadowVeil.dry(x, z);
        place(n, p[0], WorldMap.standY(p[0], p[1]), p[1]);
    }
    private void place(Npc n, float px, float y, float pz) {
        float[] p = {px, pz};
        // (0.4.6, gamefixes: spawn points clustered) two sharing a post or a doorstep off-stage stand apart, so nobody
        // comes back into play stood inside another
        java.util.List<float[]> others = new java.util.ArrayList<>();
        for (Npc o : g.npcs) if (o != n && o.alive && Math.abs(o.x - p[0]) < 4 && Math.abs(o.z - p[1]) < 4) others.add(new float[]{o.x, o.y, o.z});
        float[] q = others.isEmpty() ? new float[]{p[0], y, p[1]} : ai.apart(p[0], y, p[1], others);
        if (ai.sealed(q[0], q[1], q[2])) return;
        n.x = q[0]; n.y = q[1]; n.z = q[2];
    }

    /** Ten minutes of a resident's work: their trade's site worked by the same rules as in play, the haul sold when the pack is full. */
    private void work(Npc n, double minute) {
        Profession p = n.profession;
        // (0.4.5) damage they know of comes first: told to the one who mends it, or mended by them
        if (g.repairs != null) { String did = g.repairs.offstage(n); if (did != null) { note(n, minute, (p == null ? n.title : p.title) + ": " + did); return; } }
        Node site = p == null ? null : site(n, p);
        if (site != null) {
            ItemDef product = site.product;
            // a full basket to the town's shop first
            if (n.count(product) >= 8 || !n.canStore(new ItemStack(product, 1))) { sell(n, product, minute); return; }
            float[] spot = ai.standSpot(n, site);
            at(n, spot[0], spot[1]);
            int got = 0;
            for (int i = 0; i < 2 && !site.depleted() && n.canStore(new ItemStack(product, 1)); i++) {
                site.stock--;
                g.state.mint(product, 1);
                n.store(new ItemStack(product, 1, n.id, false, 0));
                got++;
                if (site.stock <= 0) { site.regrowAt = g.state.minutes + site.respawnMinutes; site.setBlocks(g, false); g.state.commit("NodeDepleted", 0, site.id, null, 0, site.name + " (off-stage)"); }
            }
            if (got > 0) g.state.commit("ResourceGathered", n.id, site.id, null, 0, product.name + " x" + got + " (off-stage)");
            note(n, minute, p.title + ": " + (got > 0 ? "worked the " + site.name.toLowerCase() + ", " + n.count(product) + " " + product.name.toLowerCase() + " held" : "the " + site.name.toLowerCase() + " is spent; waiting for it"));
            return;
        }
        if (p == Profession.CROP_FARMER && g.farming != null) { farm(n, minute); return; }
        // any other trade keeps its post through the working hours
        atFloor(n, n.postX, n.postY, n.postZ, n.postX, n.postZ);
        note(n, minute, (p == null ? n.title : p.title) + ": at work");
    }

    /** The site of a gathering trade within reach of home: the nearest node of its kind it can work, or null. */
    private Node site(Npc n, Profession p) {
        Node best = null;
        float bd = Float.MAX_VALUE;
        for (Node node : g.objects.nodes) {
            if (node.depleted() || node.removed || node.owner != 0 && node.owner != n.id) continue;
            boolean fits = switch (p.family) {
                case WORKER -> node.kind == Node.Kind.TREE && p.skill == rpg.skill.Skill.WOODCUTTING && AiSystem.carriesTool(n, node);
                case MINER -> node.kind == Node.Kind.ROCK && AiSystem.carriesTool(n, node);
                case FISHERMAN -> node.kind == Node.Kind.FISH && node.needs != null && (n.has(node.needs, 1) || n.main() == node.needs);
                default -> false;
            };
            if (!fits || n.skills.level(node.skill) < node.level || !ZonePolicy.withinReach(n, node.x, node.z)) continue;
            float d = (float) Math.hypot(node.x - n.postX, node.z - n.postZ);
            if (d < bd) { bd = d; best = node; }
        }
        return best;
    }

    /** The grower's rows: a ripe one cut, else a bare one sown with seed carried; then the basket to the buyer. */
    private void farm(Npc n, double minute) {
        var ripe = g.farming.ripeFor(n.id, n.x, n.z);
        if (ripe != null && n.canStore(new ItemStack(ripe.crop.produce, ripe.crop.yieldMax))) {
            at(n, ripe.x, ripe.z);
            ItemDef produce = ripe.crop.produce;
            int yield = Math.max(1, ripe.crop.yieldMax / 2);
            g.state.mint(produce, yield);
            n.store(new ItemStack(produce, yield, n.id, false, 0));
            ripe.crop = null; ripe.growth = 0; ripe.ripeFor = 0;
            g.farming.changed(ripe.field); g.farming.harvestCount++;
            note(n, minute, "Crop Farmer: cut a ripe row (" + yield + " " + produce.name.toLowerCase() + ")");
            if (n.count(produce) >= 8) sell(n, produce, minute);
            return;
        }
        var empty = g.farming.emptyFor(n.id, n.x, n.z);
        if (empty != null) {
            rpg.world.Farming.Crop want = g.farming.rowCrop(empty);
            if (n.has(want.seed, 1)) {
                at(n, empty.x, empty.z);
                n.takeItems(want.seed, 1); g.state.burn(want.seed, 1);
                empty.crop = want; empty.growth = 0; empty.sower = n.id;
                g.farming.changed(empty.field); g.farming.sownCount++;
                note(n, minute, "Crop Farmer: sowed " + want.title.toLowerCase());
                return;
            }
        }
        atFloor(n, n.postX, n.postY, n.postZ, n.postX, n.postZ);
        note(n, minute, "Crop Farmer: weeding and watering the rows");
    }

    /**
     * The haul sold at the town's shop (its stock and the seller's purse move as at the counter) - a walk there and back
     * inside the ten minutes, so the seller is found at work afterwards; a shop past a day's walk buys nothing off-stage.
     */
    private void sell(Npc n, ItemDef product, double minute) {
        var shop = g.econ.localShop(n);
        if (shop == null || !g.econ.buys(shop, product) || Math.hypot(shop.counterX - n.homeX, shop.counterZ - n.homeZ) > SELL_REACH) {
            note(n, minute, "holding " + n.count(product) + " " + product.name.toLowerCase() + ": no buyer within reach");
            return;
        }
        int sold = g.econ.sellToShop(n, shop, product, n.count(product));
        note(n, minute, "sold " + sold + " " + product.name.toLowerCase() + " at " + shop.name);
    }
}
