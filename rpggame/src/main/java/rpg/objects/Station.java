package rpg.objects;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.Work;
import rpg.interact.WorldObject;
import rpg.item.ItemStack;
import rpg.item.Recipes;
import rpg.item.Recipes.Recipe;

/**
 * A workstation (GDD 3.2): a furnace, anvil, range, crafting table or sawbench - or a lit fire, which cooks like a
 * range. Using it lists its recipes, each enabled or disabled with its reason. Making one checks level, tool,
 * materials and room for the result, works for the recipe's time, and only at that commit point consumes the inputs and
 * creates the outputs, atomically; XP is paid only on success.
 */
public class Station extends WorldObject {
    public final Recipes.Station kind;
    /** (0.4.4) The floor it stands on (its cell above the floor's blocks), where known: an upper storey's station is drawn there. */
    public float floorY = Float.NaN;

    public Station(long id, String def, String name, Recipes.Station kind, float x, float y, float z) {
        super(id, def, name, x, y, z);
        this.kind = kind;
    }

    @Override public float focusRadius() { return 0.75f; }

    public String verb() {
        return switch (kind) { case FURNACE -> "Smelt"; case ANVIL -> "Smith"; case RANGE -> "Cook"; case CRAFT_TABLE -> "Craft"; case SAWBENCH -> "Saw"; case BUILD_SPOT -> "Build";
            case CARDING_BENCH -> "Card"; case SPINNING_WHEEL -> "Spin"; case LOOM -> "Weave"; case FULLING_TUB -> "Full"; case DYE_VAT -> "Dye"; };
    }

    /** Null when the actor may make it now, else why not. */
    public String refusal(Recipe r, Actor a) {
        if (a.skills.level(r.skill) < r.level) return "You need " + r.skill.title + " level " + r.level + ".";
        if (r.tool != null && !a.has(r.tool, 1)) return "You need a " + r.tool.name.toLowerCase() + ".";
        for (Recipes.Part p : r.inputs) if (!a.has(p.item(), p.amount())) return "You need " + p.amount() + " x " + p.item().name + ".";
        // the outputs must fit once the inputs are gone
        var probe = a.inv.copy();
        for (Recipes.Part p : r.inputs) probe.remove(p.item(), p.amount());
        // (0.3.7) the pack tidied first if that is what it takes: what the inputs leave free is room, wherever it lies
        if (!fits(probe, r)) { probe.sort(); if (!fits(probe, r) && !otherGrids(a, r)) return "You don't have room for the result."; }
        return null;
    }
    private static boolean fits(rpg.item.Inventory probe, Recipe r) {
        var copy = probe.copy();
        for (Recipes.Part p : r.outputs) if (!copy.add(new ItemStack(p.item(), p.amount()))) return false;
        return true;
    }
    /** Whether a worn bag has room for the whole result. */
    private static boolean otherGrids(Actor a, Recipe r) {
        var grids = a.grids();
        for (int i = 1; i < grids.size(); i++) { var c = grids.get(i).copy(); boolean all = true; for (Recipes.Part p : r.outputs) all &= c.add(new ItemStack(p.item(), p.amount())); if (all) return true; }
        return false;
    }

    public List<Recipe> recipes() { return Recipes.at(kind); }

    /** The chance (0 .. 0.6) of burning a recipe at a cook's level: falls to nothing at the recipe's burn-stop level. */
    public static double burnChance(Recipe r, Actor a) {
        if (r.burnt == null) return 0;
        return Math.max(0, 0.6 * (r.burnStop - a.skills.level(r.skill)) / Math.max(1.0, r.burnStop - r.level));
    }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        o.add(Option.on("use", verb() + " (" + recipes().size() + " recipes)", 10, g.cfg.interactRange));
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        String why = reach(g, a, g.cfg.interactRange);
        if (why != null) return Result.no(why);
        if (action.equals("use")) { if (a == g.player) g.ui.openStation(this); return Result.ok(); }
        boolean all = action.startsWith("makeall:"), some = action.startsWith("cook:");
        if (action.startsWith("make:") || all || some) {
            // cook:<recipe>:<how many> (the cooking window's choice); make:<recipe> once; makeall:<recipe> while it can
            String[] parts = action.split(":");
            Recipe r = Recipes.byId(parts.length > 1 ? parts[1] : "");
            int times = some && parts.length > 2 ? Math.max(1, Integer.parseInt(parts[2])) : 0;
            if (r == null || r.station != kind) return Result.no("You can't make that here.");
            String no = refusal(r, a);
            if (no != null) return Result.no(no);
            // (0.3.5) a guild member works a quarter quicker at their own hall's stations
            float seconds = r.seconds * (g.guilds == null ? 1 : g.guilds.speed(a, this));
            Work w = new Work(a, this, action, r.station == Recipes.Station.RANGE ? "Cooking" : "Making " + r.name, seconds, all || times > 1, g.time, () -> make(g, a, r));
            w.limit = times;
            g.interact.begin(w);
            return Result.started();
        }
        return Result.no("You can't do that.");
    }

    /** The commit point: everything is checked again against the world as it is now, then applied whole. */
    protected Result make(Rpg g, Actor a, Recipe r) {
        long req = g.state.newRequestId();
        String why = reach(g, a, g.cfg.interactRange);
        if (why == null) why = refusal(r, a);
        if (why != null) return Result.no(why);
        for (Recipes.Part p : r.inputs) { a.takeItems(p.item(), p.amount()); g.state.burn(p.item(), p.amount()); }
        // (0.3.7) the room the inputs left made whole: the pack tidied when the result would not otherwise go in
        boolean room = true;
        for (Recipes.Part p : r.outputs) room &= a.canStore(new ItemStack(p.item(), p.amount()));
        if (!room) a.inv.sort();
        boolean burnt = false;
        double roll = 0, chance = 0;
        if (r.burnt != null) {
            chance = burnChance(r, a);
            roll = g.roll(req, id);
            burnt = roll < chance;
        }
        List<String> made = new ArrayList<>();
        if (burnt) { g.grant(a, new ItemStack(r.burnt, 1)); made.add(r.burnt.name); }
        else for (Recipes.Part p : r.outputs) { g.grant(a, new ItemStack(p.item(), p.amount())); made.add(p.amount() + " x " + p.item().name); }
        var ev = g.state.commit(req, burnt ? "FoodBurnt" : "ItemCrafted", a.id, id, jurisdiction(), 0,
                r.id + " -> " + made + (r.burnt != null ? String.format(" burn chance=%.2f roll=%.3f", chance, roll) : ""));
        if (!burnt) g.prog.award(a, r.skill, r.xp / 10.0, r.name, ev.id());
        if (a == g.player) g.chat.say(burnt ? "You accidentally burn the " + r.name.toLowerCase() + "." : "You make " + String.join(", ", made).toLowerCase() + ".");
        if (g.sfx != null) g.sfx.play(kind == Recipes.Station.ANVIL ? "mine" : kind == Recipes.Station.SAWBENCH ? "chop" : "crackle", x, y, z, 0.5f);
        return Result.ok();
    }

}
