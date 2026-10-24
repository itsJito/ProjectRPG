package rpg.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.Work;
import rpg.interact.WorldObject;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * A gathering node (GDD 3.2, 3.7 ecology): a tree, a rock, a fishing spot or a patch. NPCs gather by the same rules.
 *
 * <p>Gathering is physical (gamefixes):
 * <ul>
 *   <li><b>Mining</b>: a pickaxe is wielded and swung, and its head has to actually strike the ore. Every strike that
 *       lands pays Mining XP (a share of what the ore is worth); after enough strikes the ore breaks free into the
 *       miner's pack. The better the pickaxe's metal (and the miner's level over the rock's), the fewer strikes.</li>
 *   <li><b>Woodcutting</b>: a woodcutting axe (one-handed - a battleaxe is no felling tool) is wielded and swung at any
 *       part of the tree, trunk or crown. After enough blows the whole tree comes down - it is gone, a stump left - and
 *       the cutter has its wood; XP is paid then, for the tree felled. A better axe fells sooner.</li>
 *   <li><b>Fishing</b>: the spot's own tool (a net for shrimp, a rod for trout, a cage for crabs) is wielded and cast
 *       at the spot; XP is paid only for a fish caught.</li>
 * </ul>
 * Patches (herbs, flax, berries) are picked by hand, as timed work.
 */
public final class Node extends WorldObject {
    public enum Kind { TREE, ROCK, FISH, PATCH }

    public final Kind kind;
    public final Skill skill;
    public final int level, stockMax;
    public final ItemDef product;
    public final int xp;
    public final float baseTime, respawnMinutes;
    public int stock;
    public double regrowAt = -1;
    /** The block it is made of (trees: the log), and where. */
    public final int block, bx, bz;
    private final int leaves, height;
    /** A fishing spot's tool (net, rod, cage); null elsewhere. */
    public ItemDef needs;
    /** How far through felling the tree or breaking out the ore the blows so far have gone (strikes landed), and when the last landed. */
    public float struck;
    public double lastStruck = -99;
    public long striker;

    public Node(long id, String def, String name, Kind kind, Skill skill, int level, ItemDef product, int xp, float baseTime, int stock, float respawn,
                int block, int bx, int bz, int leaves, int height) {
        super(id, def, name, bx + 0.5f, DevMap.FLOOR + (kind == Kind.FISH ? 0.2f : 0.6f), bz + 0.5f);
        this.kind = kind; this.skill = skill; this.level = level; this.product = product; this.xp = xp; this.baseTime = baseTime;
        this.stockMax = stock; this.stock = stock; this.respawnMinutes = respawn; this.block = block; this.bx = bx; this.bz = bz; this.leaves = leaves; this.height = height;
        if (kind == Kind.TREE) y = DevMap.FLOOR + 1.2f;
    }

    @Override public float focusRadius() { return kind == Kind.TREE ? 0.9f : kind == Kind.FISH ? 1.2f : 0.75f; }
    /** Only a node with a stock can run out (a fishing spot has none and never does). */
    public boolean depleted() { return stockMax > 0 && stock <= 0; }
    @Override public String label() { return depleted() ? (kind == Kind.TREE ? "Tree stump" : kind == Kind.ROCK ? "Rocks (depleted)" : "Patch (picked)") : name; }

    public String verb() { return switch (kind) { case TREE -> "Chop down"; case ROCK -> "Mine"; case FISH -> "Fish"; case PATCH -> "Pick"; }; }
    private String working() { return switch (kind) { case TREE -> "Chopping"; case ROCK -> "Mining"; case FISH -> "Fishing"; case PATCH -> "Picking"; }; }

    /** Whether a block is part of this node as it stands: a tree's trunk or crown, a rock's ore. */
    public boolean contains(int x, int y, int z) {
        if (depleted()) return false;
        int f = DevMap.FLOOR;
        return switch (kind) {
            case ROCK -> x == bx && z == bz && y == f;
            case TREE -> x == bx && z == bz && y >= f && y <= f + height
                    || Math.abs(x - bx) <= 2 && Math.abs(z - bz) <= 2 && y >= f + height - 1 && y <= f + height + 2;
            default -> false;
        };
    }

    /** The tool in the actor's hand that works this node, if it is one they can use; null otherwise. Patches need none. */
    public ItemDef tool(Actor a) {
        if (kind == Kind.PATCH) return Items.COINS;
        ItemDef held = a.main();
        if (held == null) return null;
        if (kind == Kind.FISH) return held == needs ? held : null;
        if (held.toolSkill != skill || held.twoHanded() && kind == Kind.TREE) return null;
        return a.skills.level(skill) >= toolLevel(held) ? held : null;
    }

    /** The gathering level a tool asks: its tier's one requirement (GDD 2.4), on the gathering skill (a Leather hatchet from 1). */
    public static int toolLevel(ItemDef d) { return d.tier == null ? 1 : d.tier.requirement; }

    /** Why the actor cannot work this node with what they hold (null: they can). */
    public String refusal(Actor a) {
        if (depleted()) return kind == Kind.TREE ? "This tree has been cut down." : kind == Kind.ROCK ? "There is no ore left in this rock." : "There's nothing left to pick.";
        if (a.skills.level(skill) < level) return "You need " + skill.title + " level " + level + " to " + verb().toLowerCase() + " this.";
        if (tool(a) == null) {
            ItemDef held = a.main();
            if (held != null && held.toolSkill == skill && kind != Kind.FISH && !(held.twoHanded() && kind == Kind.TREE))
                return "You need " + skill.title + " level " + toolLevel(held) + " to use the " + held.name.toLowerCase() + ".";
            return switch (kind) {
                case TREE -> held != null && held.family == ItemDef.Family.AXE ? "That's a battleaxe. You need a woodcutting axe in your hand." : "You need to wield a woodcutting axe.";
                case ROCK -> "You need to wield a pickaxe.";
                case FISH -> "You need to wield a " + needs.name.toLowerCase() + " to fish here.";
                default -> "You can't.";
            };
        }
        if (!a.canStore(new ItemStack(product, 1)) && kind != Kind.TREE) return "Your inventory is too full to hold any more.";
        return null;
    }

    /** How many blows bring the tree down or break the ore out: the wood or rock's toughness against the tool and the hand. */
    public int strikesNeeded(Actor a) {
        ItemDef t = tool(a);
        int power = t == null || t.tier == null ? 1 : t.toolPower;
        int over = Math.max(0, a.skills.level(skill) - level);
        float tough = kind == Kind.TREE ? baseTime * 2.2f : baseTime * 1.6f;
        return Math.max(2, Math.round(tough / ((1 + (power - 1) * 0.35f) * (1 + Math.min(0.8f, over * 0.012f)))));
    }

    public float duration(Actor a) {
        int over = Math.max(0, a.skills.level(skill) - level);
        return Math.max(0.9f, baseTime * (1f - Math.min(0.55f, over * 0.008f)));
    }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        String why = refusal(a);
        o.add(Option.when(why == null, "gather", verb(), 10, why, g.cfg.interactRange + (kind == Kind.FISH ? 1.5f : 0)));
        o.add(Option.on("examine", "Examine", 0, g.cfg.inspectRange));
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
        if (!action.equals("gather")) return Result.no("You can't do that.");
        String why = reach(g, a, g.cfg.interactRange + (kind == Kind.FISH ? 1.5f : 0));
        if (why == null) why = refusal(a);
        if (why != null) return Result.no(why);
        if (kind == Kind.TREE || kind == Kind.ROCK) {
            // the button only swings: the tool has to land on the tree or the ore, like any blow
            a.yaw = (float) Math.atan2(-(x - a.x), -(z - a.z));
            a.pitch = (float) Math.atan2((kind == Kind.ROCK ? DevMap.FLOOR + 0.5f : DevMap.FLOOR + 1.2f) - a.eyeY() + 0.35f, Math.max(0.3, Math.hypot(x - a.x, z - a.z)));
            return g.combat.attack(a, false, rpg.actor.Actor.Combat.Move.LIGHT_R) ? Result.started() : Result.no(null);
        }
        if (a == g.player) g.chat.say(switch (kind) { case FISH -> "You cast out your " + needs.name.toLowerCase().replace("small ", "") + "."; default -> "You start picking."; });
        if (a == g.player && kind == Kind.FISH && g.sfx != null) g.sfx.ui("FishingHooked", 0.5f);
        g.interact.begin(new Work(a, this, "gather", working(), duration(a), true, g.time, () -> commit(g, a)));
        return Result.started();
    }

    /**
     * A blow from a held tool landed on this node (CombatSystem, when the head meets one of its blocks). Mining pays its
     * share of XP for every strike; felling pays when the tree comes down. Returns false when the blow does nothing here
     * (the wrong tool, too low a level): it is then just a blade meeting wood or stone.
     */
    public boolean strike(Rpg g, Actor a, float hx, float hy, float hz) {
        if (kind != Kind.TREE && kind != Kind.ROCK) return false;
        String why = refusal(a);
        if (why != null) {
            if (a == g.player && g.time - lastRefusal > 1.5) { g.chat.warn(why); lastRefusal = g.time; }
            return false;
        }
        // blows from someone else, or a long while ago, start the count afresh
        if (striker != a.id || g.time - lastStruck > 30) struck = 0;
        striker = a.id; lastStruck = g.time;
        int need = strikesNeeded(a);
        struck++;
        if (g.fx != null) { if (kind == Kind.ROCK) g.fx.sparks(hx, hy, hz, 8); else g.fx.chips(hx, hy, hz, 8); }
        if (g.sfx != null) g.sfx.play(kind == Kind.TREE ? "chop" : "mine", hx, hy, hz, 0.75f);
        g.noise(a, hx, hy, hz, 10, 0.4f);
        long req = g.state.newRequestId();
        if (kind == Kind.ROCK) {
            // every strike on the ore is Mining practice: the ore's worth of XP shared over the strikes it takes
            var ev = g.state.commit(req, "OreStruck", a.id, id, jurisdiction(), 0, String.format("%s %d/%d", name, (int) struck, need));
            g.prog.award(a, skill, xp / (float) need, "struck " + name.toLowerCase(), ev.id());
            if (struck < need) return true;
            struck = 0;
            ItemStack got = new ItemStack(product, 1);
            g.state.mint(product, 1);
            if (!a.store(got)) { g.objects.drop(got, a.x, a.y + 0.05f, a.z); if (a == g.player) g.chat.warn("No room for the " + product.name.toLowerCase() + ": it drops at your feet."); }
            stock--;
            var got2 = g.state.commit(g.state.newRequestId(), "ResourceGathered", a.id, id, jurisdiction(), 0, product.name + " (" + stock + " left)");
            if (a == g.player) g.chat.say("You manage to mine some " + product.name.toLowerCase().replace(" ore", "") + ".");
            if (stock <= 0) deplete(g, got2.id());
            return true;
        }
        // a tree: blow upon blow, then it is down
        if (a == g.player && struck < need && (int) struck % 3 == 0) g.chat.say("The tree creaks.");
        if (struck < need) { g.state.commit(req, "TreeStruck", a.id, id, jurisdiction(), 0, String.format("%s %d/%d", name, (int) struck, need)); return true; }
        struck = 0;
        int logs = Math.max(1, stock);
        for (int i = 0; i < logs; i++) g.grant(a, new ItemStack(product, 1));
        stock = 0;
        var ev = g.state.commit(req, "TreeFelled", a.id, id, jurisdiction(), 0, logs + " x " + product.name);
        g.prog.award(a, skill, xp * logs, "felled " + name.toLowerCase(), ev.id());
        if (a == g.player) g.chat.say("Timber! You fell the " + name.toLowerCase() + " and gather " + logs + " " + product.name.toLowerCase() + ".");
        if (g.sfx != null) g.sfx.play("wood.heavy", x, y + 1.5f, z, 1f);
        if (g.fx != null) for (int i = 0; i < 4; i++) g.fx.chips(bx + 0.5f, DevMap.FLOOR + 1.5f + i, bz + 0.5f, 10);
        deplete(g, ev.id());
        return true;
    }
    private double lastRefusal = -9;

    private Result commit(Rpg g, Actor a) {
        long req = g.state.newRequestId();
        String why = reach(g, a, g.cfg.interactRange + (kind == Kind.FISH ? 1.5f : 0));
        if (why == null) why = refusal(a);
        if (why != null) return Result.no(why);
        ItemStack got = new ItemStack(product, 1);
        g.state.mint(product, 1);
        a.store(got);
        if (stockMax > 0) stock--;
        var ev = g.state.commit(req, "ResourceGathered", a.id, id, jurisdiction(), 0, product.name + " (" + stock + " left)");
        g.prog.award(a, skill, xp, "gathered " + product.name, ev.id());
        if (g.sfx != null) g.sfx.play(kind == Kind.FISH ? "splash" : "pickup", x, y, z, 0.7f);
        if (kind == Kind.FISH && g.fx != null) g.chemistry.splash(x, y + 0.3f, z);
        if (kind == Kind.FISH && a == g.player && g.sfx != null) g.sfx.ui("FishReelIn", 0.55f);
        if (a == g.player) g.chat.say(kind == Kind.FISH ? "You catch " + (product == Items.RAW_SHRIMP ? "some shrimp" : "a " + product.name.toLowerCase().replace("raw ", "")) + "." : "You get some " + product.name.toLowerCase() + ".");
        if (stockMax > 0 && stock <= 0) deplete(g, ev.id());
        return Result.ok();
    }

    private void deplete(Rpg g, long cause) {
        regrowAt = g.state.minutes + respawnMinutes;
        setBlocks(g, false);
        g.state.commit("NodeDepleted", 0, id, jurisdiction(), cause, name + " regrows at minute " + (int) regrowAt);
    }

    public void update(Rpg g) {
        if (depleted() && regrowAt >= 0 && g.state.minutes >= regrowAt) {
            stock = stockMax;
            regrowAt = -1;
            setBlocks(g, true);
            g.state.commit("NodeRegrown", 0, id, jurisdiction(), 0, name);
        }
    }

    /** The world as the node's state says: a whole tree or a stump; ore or spent rock; a plant or bare soil. */
    public void setBlocks(Rpg g, boolean whole) {
        int f = DevMap.FLOOR;
        switch (kind) {
            case TREE -> {
                for (int y = f + 1; y <= f + height; y++) g.setBlock(bx, y, bz, whole ? block : Blocks.AIR);
                g.setBlock(bx, f, bz, whole ? block : Blocks.STUMP);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = f + height - 1; y <= f + height + 2; y++) {
                    boolean top = y == f + height + 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    if (top && (Math.abs(dx) > 1 || Math.abs(dz) > 1)) continue;
                    if (corner && y == f + height + 1) continue;
                    if (dx == 0 && dz == 0 && y <= f + height) continue;
                    g.setBlock(bx + dx, y, bz + dz, whole ? leaves : Blocks.AIR);
                }
            }
            case ROCK -> g.setBlock(bx, f, bz, whole ? block : Blocks.SPENT_ROCK);
            case PATCH -> { g.setBlock(bx, f, bz, whole ? block : Blocks.AIR); g.setBlock(bx, f - 1, bz, whole ? Blocks.DIRT : Blocks.BARE_PATCH); }
            case FISH -> { }
        }
    }

    @Override public String examine(Rpg g) {
        String how = switch (kind) {
            case TREE -> " Wield a woodcutting axe and swing at it.";
            case ROCK -> " Wield a pickaxe and strike the ore.";
            case FISH -> " Wield a " + needs.name.toLowerCase() + " and cast here.";
            default -> "";
        };
        return name + ": " + skill.title + " " + level + ", gives " + product.name.toLowerCase() + (stockMax > 0 ? " (" + stock + "/" + stockMax + " left)" : "") + "." + how;
    }
    @Override public String inspect(Rpg g) { return super.inspect(g) + "\n  " + skill.title + " " + level + " stock " + stock + "/" + stockMax + (regrowAt >= 0 ? " regrows@" + (int) regrowAt : "") + " struck " + (int) struck; }

    @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("stock", stock); m.put("regrowAt", regrowAt); return m; }
    @Override public void load(Rpg g, Map<String, Object> m) {
        super.load(g, m);
        stock = astratto.util.Json.integer(m, "stock", stockMax);
        regrowAt = astratto.util.Json.num(m, "regrowAt", -1);
        setBlocks(g, !depleted());
    }
}
