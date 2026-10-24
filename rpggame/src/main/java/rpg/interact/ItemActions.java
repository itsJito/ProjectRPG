package rpg.interact;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.item.Equipment;
import rpg.item.ItemDef;
import rpg.item.ItemDef.Family;
import rpg.item.ItemDef.Slot;
import rpg.item.ItemStack;
import rpg.item.Inventory;
import rpg.item.Items;
import rpg.objects.Fire;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * What can be done with a carried item (GDD 2.4: gear changes are manual, through the inventory, with no instant
 * loadouts): eat, bury, wield or wear (into the main or off hand), light, rub, drop, examine - and remove from the
 * equipment. Each is validated and applied whole: a swap that would not fit changes nothing.
 */
public final class ItemActions {
    private final Rpg g;
    public ItemActions(Rpg g) { this.g = g; }

    /** Whether there is open water within a step or two of the actor. */
    private boolean nearWater(Actor a) {
        int bx = (int) Math.floor(a.x), bz = (int) Math.floor(a.z);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = DevMap.FLOOR - 2; y <= DevMap.FLOOR; y++)
            if (g.vox.block(bx + dx, y, bz + dz) == rpg.world.Blocks.WATER) return true;
        return false;
    }

    public List<Option> options(Actor a, int slot) { return options(a, a.inv, slot); }

    /** What can be done with the item at a place in one of the actor's grids (the base grid or a worn bag's). */
    public List<Option> options(Actor a, Inventory grid, int slot) {
        List<Option> o = new ArrayList<>();
        ItemStack s = grid.get(slot);
        if (s == null) return o;
        ItemDef d = s.def;
        if (s.contract != null) o.add(Option.on("read_warrant", "Read warrant", 10, 0));
        if (d.heal > 0) o.add(Option.on("eat", "Eat", 10, 0));
        if (d.restoresFaith > 0) o.add(Option.on("drink", "Drink", 10, 0));
        if (d.maxCharges > 0) o.add(Option.when(a.has(Items.COAL, 1) && s.charges < d.maxCharges, "recharge", "Recharge (coal)", 6, s.charges >= d.maxCharges ? "It is fully charged." : "You need coal.", 0));
        if (d == Items.BONES || d == Items.BIG_BONES) o.add(Option.on("bury", "Bury", 10, 0));
        if (d == Items.XP_LAMP) o.add(Option.on("rub", "Rub", 10, 0));
        if ((d == Items.RAW_SHRIMP || d == Items.RAW_TROUT || d.id.equals("raw_crab")) && !s.stolen)
            o.add(Option.when(nearWater(a), "release", "Release", 4, "You need to be at the water's edge.", 0));
        if (d == Items.LOGS || d == Items.OAK_LOGS || d == Items.WILLOW_LOGS) {
            int lvl = fireLevel(d);
            boolean ok = a.skills.level(Skill.FIREMAKING) >= lvl && a.has(Items.TINDERBOX, 1);
            o.add(Option.when(ok, "light", "Light", 9, a.has(Items.TINDERBOX, 1) ? "You need Firemaking level " + lvl + "." : "You need a tinderbox.", 0));
        }
        if (d.equippable()) {
            String why = Equipment.refusal(d, a.skills);
            o.add(Option.when(why == null, "wield", d.slot == Slot.MAIN || d.slot == Slot.OFF ? "Wield" : "Wear", 8, why, 0));
            if (offHandable(d)) o.add(Option.when(why == null, "offhand", "Wield in off hand", 7, why, 0));
        }
        o.add(Option.on("drop", "Drop", 2, 0));
        o.add(Option.on("examine", "Examine", 1, 0));
        return o;
    }

    public static boolean offHandable(ItemDef d) {
        return d.slot == Slot.MAIN && !d.twoHanded() && (d.family == Family.SWORD || d.family == Family.AXE || d.family == Family.MACE || d.family == Family.DAGGER);
    }

    private static int fireLevel(ItemDef logs) { return logs == Items.WILLOW_LOGS ? 30 : logs == Items.OAK_LOGS ? 15 : 1; }

    public Result perform(Actor a, int slot, String action) { return perform(a, a.inv, slot, action); }

    public Result perform(Actor a, Inventory grid, int slot, String action) {
        long req = g.state.newRequestId();
        ItemStack s = grid.get(slot);
        if (s == null) return Result.no("Nothing there.");
        ItemDef d = s.def;
        Result r = switch (action) {
            case "eat" -> eat(a, grid, slot, req);
            case "drink" -> {
                grid.take(slot, 1);
                g.state.burn(d, 1);
                float before = a.faith;
                a.faith = Math.min(a.maxFaith(), a.faith + d.restoresFaith);
                g.state.commit(req, "Drank", a.id, 0, null, 0, d.name + String.format(" faith %.0f -> %.0f", before, a.faith));
                if (a == g.player) { g.chat.say("You drink the " + d.name.toLowerCase() + ". You feel your faith renewed."); if (g.sfx != null) g.sfx.ui("eat", 0.5f); }
                yield Result.ok();
            }
            case "recharge" -> {
                var coal = a.takeItems(Items.COAL, 1);
                if (coal == null) yield Result.no("You need coal to recharge it.");
                g.state.burn(Items.COAL, 1);
                s.charges = Math.min(d.maxCharges, s.charges + 10);
                g.state.commit(req, "Recharged", a.id, 0, null, 0, d.name + " -> " + s.charges + " charges");
                if (a == g.player) g.chat.say("The staff drinks in the coal's heat: " + s.charges + " charges.");
                yield Result.ok();
            }
            case "release" -> {
                if (!nearWater(a)) yield Result.no("You need to be at the water's edge.");
                grid.take(slot, 1);
                g.state.burn(d, 1);
                g.state.commit(req, "FishReleased", a.id, 0, null, 0, d.name);
                if (a == g.player) { g.chat.say("You slip the " + d.name.toLowerCase().replace("raw ", "") + " back into the water. It darts away."); g.honor.released(); }
                if (g.fx != null) g.fx.splash(a.x - (float) Math.sin(a.yaw) * 0.8f, DevMap.FLOOR - 0.2f, a.z - (float) Math.cos(a.yaw) * 0.8f);
                yield Result.ok();
            }
            case "bury" -> {
                if (a.cs.busy()) yield Result.no("You're busy.");
                g.interact.begin(new Work(a, selfTarget(a), "bury", "Burying", 0.9f, false, g.time, () -> {
                    if (grid.get(slot) == null || grid.get(slot).def != d) return Result.no("The bones are gone.");
                    grid.take(slot, 1);
                    g.state.burn(d, 1);
                    var ev = g.state.commit("BonesBuried", a.id, 0, null, 0, d.name);
                    g.prog.award(a, Skill.FAITH, d == Items.BIG_BONES ? 15 : 5, "buried " + d.name.toLowerCase(), ev.id());
                    if (a == g.player) g.chat.say("You bury the bones.");
                    return Result.ok();
                }));
                yield Result.started();
            }
            case "read_warrant" -> {
                if (s.contract == null) yield Result.no("There is no signed warrant on this scroll.");
                if (a == g.player) g.ui.dialog(new rpg.ui.Dialog("Signed warrant", "Wanted: " + g.nameOf(s.contract.target()) + ". Reward: " + s.contract.reward() + " gold from " + s.contract.jurisdiction() + ". Return this scroll after bringing them down. The map records the watch's last sighting."));
                yield Result.ok();
            }
            case "rub" -> { if (a == g.player) g.ui.dialog(g.dialogue.lamp(grid, slot)); yield Result.ok(); }
            case "light" -> light(a, grid, slot, d);
            case "wield" -> equip(a, grid, slot, d.slot == null ? Slot.MAIN : d.slot, req);
            case "offhand" -> offHandable(d) ? equip(a, grid, slot, Slot.OFF, req) : Result.no("That can't go in your off hand.");
            case "drop" -> {
                ItemStack moving = grid.take(slot, s.amount);
                float fx = (float) -Math.sin(a.yaw) * 0.7f, fz = (float) -Math.cos(a.yaw) * 0.7f;
                g.objects.drop(moving, a.x + fx, g.vox.groundY(a.x + fx, a.y + 1, a.z + fz) + 0.05f, a.z + fz);
                g.state.commit(req, "ItemDropped", a.id, 0, DevMap.jurisdiction(a.x, a.z), 0, moving.toString());
                yield Result.ok();
            }
            case "examine" -> { if (a == g.player) g.chat.say(d.examine + (s.stolen ? " (Stolen from " + g.victims(s) + ".)" : s.owner != 0 && s.owner != a.id ? " (Belongs to " + g.nameOf(s.owner) + ".)" : "")); yield Result.ok(); }
            default -> Result.no("You can't do that.");
        };
        if (r.rejected() && a == g.player && r.reason() != null) g.chat.warn(r.reason());
        return r;
    }

    private Result eat(Actor a, Inventory grid, int slot, long req) {
        ItemStack s = grid.get(slot);
        if (a.cs.castCooldown > 0) return Result.no(null);
        if (a.hp >= a.maxHp()) { if (a == g.player) g.chat.say("You're not hungry... but you eat it anyway."); }
        grid.take(slot, 1);
        g.state.burn(s.def, 1);
        float before = a.hp;
        a.hp = Math.min(a.maxHp(), a.hp + s.def.heal);
        a.cs.castCooldown = 1.2f;
        g.state.commit(req, "Ate", a.id, 0, null, 0, s.def.name + String.format(" hp %.0f -> %.0f", before, a.hp));
        if (a == g.player) { g.chat.say("You eat the " + s.def.name.toLowerCase() + ". It heals some health."); g.sfx.ui("eat", 0.6f); }
        return Result.ok();
    }

    private Result light(Actor a, Inventory grid, int slot, ItemDef logs) {
        int lvl = fireLevel(logs);
        if (a.skills.level(Skill.FIREMAKING) < lvl) return Result.no("You need a Firemaking level of " + lvl + " to light " + logs.name.toLowerCase() + ".");
        if (!a.has(Items.TINDERBOX, 1)) return Result.no("You need a tinderbox to light a fire.");
        String why = fireSpot(a);
        if (why != null) return Result.no(why);
        if (a == g.player) g.chat.say("You attempt to light the logs.");
        g.interact.begin(new Work(a, selfTarget(a), "light", "Lighting a fire", 1.6f, false, g.time, () -> {
            String w2 = fireSpot(a);
            if (w2 != null) return Result.no(w2);
            if (grid.get(slot) == null || grid.get(slot).def != logs) return Result.no("The logs are gone.");
            if (!a.has(Items.TINDERBOX, 1)) return Result.no("You need a tinderbox.");
            grid.take(slot, 1);
            g.state.burn(logs, 1);
            float fx = a.x - (float) Math.sin(a.yaw) * 0.9f, fz = a.z - (float) Math.cos(a.yaw) * 0.9f;
            int fl = a.skills.level(Skill.FIREMAKING);
            Fire f = g.objects.add(new Fire(g.state.newEntityId(), fx, g.vox.groundY(fx, a.y + 0.5f, fz), fz, g.state.minutes + 60 + 3 * fl + 30 * (lvl / 15), a.id));
            var ev = g.state.commit("FireLit", a.id, f.id, DevMap.jurisdiction(fx, fz), 0, logs.name);
            g.prog.award(a, Skill.FIREMAKING, logs == Items.WILLOW_LOGS ? 90 : logs == Items.OAK_LOGS ? 60 : 40, "lit " + logs.name.toLowerCase(), ev.id());
            if (a == g.player) g.chat.say("The fire catches and the logs begin to burn.");
            g.sfx.play("crackle", fx, f.y, fz, 0.6f);
            return Result.ok();
        }));
        return Result.started();
    }

    /** Fires go on open ground: not indoors, not in water, not on top of another fire. */
    private String fireSpot(Actor a) {
        float fx = a.x - (float) Math.sin(a.yaw) * 0.9f, fz = a.z - (float) Math.cos(a.yaw) * 0.9f;
        int bx = (int) Math.floor(fx), bz = (int) Math.floor(fz), by = (int) Math.floor(a.y + 0.1f);
        int ground = g.vox.block(bx, by - 1, bz);
        if (g.vox.solid(bx, by, bz) || ground == Blocks.WATER || g.vox.block(bx, by, bz) == Blocks.WATER) return "You can't light a fire there.";
        if (ground == Blocks.PLANKS || ground == Blocks.DEBUG_FLOOR) return "You can't light a fire here.";
        for (var o : g.objects.all()) if (o instanceof Fire f && Math.hypot(f.x - fx, f.z - fz) < 1.2) return "There's already a fire here.";
        return null;
    }

    /** Equips from an inventory slot into an equipment slot, swapping out whatever was there (and a two-hander's other hand). */
    /** Equips the first of a definition the actor carries, from whichever grid holds it. */
    public Result equipFirst(Actor a, rpg.item.ItemDef d, Slot where, long req) {
        for (Inventory grid : a.grids()) { int i = grid.first(d); if (i >= 0) return equip(a, grid, i, where, req); }
        return Result.no("Nothing there.");
    }

    public Result equip(Actor a, int slot, Slot where, long req) { return equip(a, a.inv, slot, where, req); }

    public Result equip(Actor a, Inventory grid, int slot, Slot where, long req) {
        ItemStack s = grid.get(slot);
        if (s == null) return Result.no("Nothing there.");
        ItemDef d = s.def;
        String why = Equipment.refusal(d, a.skills);
        if (why != null) return Result.no(why);
        if (a.cs.busy()) return Result.no("You can't change equipment mid-swing.");
        List<Slot> clear = new ArrayList<>();
        clear.add(where);
        if (where == Slot.MAIN && d.twoHanded() && a.eq.get(Slot.OFF) != null) clear.add(Slot.OFF);
        if (where == Slot.OFF && a.main() != null && a.main().twoHanded()) clear.add(Slot.MAIN);
        // ammunition of the same kind joins the worn stack
        ItemStack worn = a.eq.get(where);
        if (where == Slot.AMMO && worn != null && worn.sameKind(s)) {
            grid.take(slot, s.amount);
            worn.absorb(s);
            g.state.commit(req, "Equipped", a.id, 0, null, 0, d.name + " -> " + where);
            return Result.ok();
        }
        // what comes off must fit, counting the slot the new item frees
        Inventory probe = grid.copy();
        probe.take(slot, s.amount);
        for (Slot c : clear) if (a.eq.get(c) != null && !probe.add(a.eq.get(c))) return Result.no("You don't have enough inventory space to swap that.");
        ItemStack moving = grid.take(slot, s.amount);
        for (Slot c : clear) { ItemStack off = a.eq.get(c); if (off != null) { a.eq.set(c, null); a.store(off); } }
        a.eq.set(where, moving);
        g.state.commit(req, "Equipped", a.id, 0, null, 0, d.name + " -> " + where);
        if (a == g.player) g.sfx.ui("click", 0.4f);
        return Result.ok();
    }

    public Result unequip(Actor a, Slot where) {
        ItemStack s = a.eq.get(where);
        if (s == null) return Result.no("Nothing there.");
        if (a.cs.busy()) return Result.no("You can't change equipment mid-swing.");
        if (!a.canStore(s)) return Result.no("You don't have enough inventory space.");
        a.eq.set(where, null);
        a.store(s);
        g.state.commit("Unequipped", a.id, 0, null, 0, s.def.name);
        return Result.ok();
    }

    /** Work needs a target that exists for as long as the actor does. */
    private Interactable selfTarget(Actor a) {
        return new Interactable() {
            public long id() { return a.id; }
            public String label() { return a.name; }
            public float ax() { return a.x; }
            public float ay() { return a.y; }
            public float az() { return a.z; }
            public boolean exists() { return a.alive; }
            public List<Option> options(Rpg g, Actor x) { return List.of(); }
            public Result perform(Rpg g, Actor x, String id, long r) { return Result.no(null); }
            public String examine(Rpg g) { return ""; }
        };
    }
}
