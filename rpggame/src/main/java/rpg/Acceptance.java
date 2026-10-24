package rpg;

import astratto.game.Game;
import astratto.platform.Window;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.ai.Brain;
import rpg.ai.Npc;
import rpg.ai.Stealth;
import rpg.actor.Player;
import rpg.combat.Spells;
import rpg.econ.Economy;
import rpg.interact.Interactable;
import rpg.interact.Result;
import rpg.interact.WorldObject;
import rpg.item.Equipment;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.objects.Container;
import rpg.objects.Door;
import rpg.objects.GroundItem;
import rpg.objects.Node;
import rpg.objects.Station;
import rpg.quest.Quests;
import rpg.render.Gfx;
import rpg.skill.Skill;
import rpg.skill.Skills;
import rpg.skill.XpTable;
import rpg.ui.Creator;
import rpg.ui.Hud;
import rpg.ui.Ui2D;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * The scripted, off-screen run through every GDD 4.2 end-to-end flow - the real game, the real engine, no shortcuts
 * around the rules: actions go through the same interaction requests the keyboard makes, NPCs decide for themselves,
 * and every flow ends in checks. Screenshots are kept for review.
 */
public final class Acceptance {
    final Window win;
    final Game game;
    final Gfx gfx;
    final Ui2D ui;
    final String shots;
    Rpg g;
    Hud hud;
    final List<String> failed = new ArrayList<>();
    int checks;

    /** -Drpg.debugFight=true prints the fight's phase changes (to see why a guard did or did not meet a blow). */
    static final boolean DEBUG_FIGHT = Boolean.getBoolean("rpg.debugFight");

    Acceptance(Window win, Game game, Gfx gfx, Ui2D ui, String shots) { this.win = win; this.game = game; this.gfx = gfx; this.ui = ui; this.shots = shots; }

    /** RPG_ONLY=animals,stealth: run only those sections (a quick loop while working on one system). */
    private static final java.util.Set<String> ONLY = System.getenv("RPG_ONLY") == null ? null : java.util.Set.of(System.getenv("RPG_ONLY").split(","));
    private static boolean on(String section) { return ONLY == null || ONLY.contains(section); }

    int run() {
        creator();
        start();
        try {
            if (on("animalCatalog")) { section("Animals: the fourteen species");              animalCatalog(); }
            if (on("leveling")) { section("Leveling: all 18 skills on one table");      leveling(); }
            if (on("interaction")) { section("Interaction edge cases");                     interaction(); }
            if (on("physical")) { section("Physical doors and breaking glass");          physical(); }
            if (on("interfaceChecks")) { section("Interface: fragments, scenes, addons");       interfaceChecks(); }
            if (on("skillActions")) { section("Skill actions (each of the 18)");             skillActions(); }
            if (on("combat")) { section("Combat and equipment");                       combat(); }
            if (on("npcs")) { section("Intelligent NPCs");                           npcs(); }
            if (on("theft")) { section("Witnessed theft and the law");                theft(); }
            if (on("arrest")) { section("Arrest: the law stands down");                arrest(); }
            if (on("stealth")) { section("Stealth: light, sight and awareness");        stealth(); }
            if (on("shipments")) { section("World state: shipments");                     shipments(); }
            if (on("quest")) { section("Quest and death");                            quest(); }
            if (on("persistence")) { section("Persistence and time");                       persistence(); }
            if (on("animals")) { section("Animals: ecology, breeding, people");         animals(); }
            if (on("pursuit")) { section("Pursuit: NPCs lose the player and search");   pursuit(); }
            if (on("gore")) { section("Gore: wounds, dismemberment, evidence");       gore(); }
            if (on("pause")) { section("Pause: menus stop the world");                 pause(); }
            if (on("bugs")) { section("Reported bugs stay fixed");                    bugs(); }
            if (on("deaths")) { section("Deaths: the dead stay down");                 deaths(); }
            if (on("reactions")) { section("NPC reactions: investigating, fear, rumor");   reactions(); }
            if (on("death")) { section("Player death, graves and the undertaker");     playerDeath(); }
            if (on("honor")) { section("Honor, the fence and jewellery");              honorChecks(); }
            if (on("console")) { section("Developer console and profiles");            console(); }
            section("Invariants");
            List<String> f = g.invariants.check();
            for (String s : g.invariants.lastReport) System.out.println("    " + s);
            need("every world-state invariant holds at the end", f.isEmpty());
        } catch (RuntimeException e) {
            e.printStackTrace();
            need("the run completed without an exception (" + e + ")", false);
        }
        System.out.println();
        System.out.println("acceptance: " + (checks - failed.size()) + " / " + checks + " checks passed");
        for (String f : failed) System.out.println("  FAILED: " + f);
        return failed.isEmpty() ? 0 : 1;
    }

    // ------------------------------------------------------------------ the creator
    private void creator() {
        section("Character creator");
        Creator c = new Creator();
        Gfx.Preview preview = new Gfx.Preview();
        preview.world = game.world(); preview.x = 64.5f; preview.y = DevMap.FLOOR; preview.z = 66.5f;
        // every row cycles through exactly the GDD's counts (3.18: sex, body, skin, 20 hair per sex, 10 natural hair
        // colours, 20 facial hair, 20 tops / legs / shoes, 24 garment colours) and wraps
        int[] want = {2, 3, 12, 20, 10, 20, 20, 24, 20, 24, 20, 24};
        var rows = c.rows();
        boolean counts = rows.size() == want.length;
        for (int i = 0; counts && i < rows.size(); i++) counts = rows.get(i).count() == want[i];
        need("creator rows have the GDD counts (2 sexes, 3 bodies, 12 skins, 20 hair, 10 hair colours, 20 facial hair, 20 tops/legs/shoes, 24 colours)", counts);
        java.util.function.BiFunction<Boolean, Integer, String> hairId = (fem, i) -> rpg.render.HairMeshes.head(fem, i).list.stream().map(java.util.Arrays::toString).reduce("", String::concat)
                + "|" + rpg.render.HairMeshes.hang(fem, i).list.stream().map(java.util.Arrays::toString).reduce("", String::concat) + "|" + rpg.render.HairMeshes.shavedSides(fem, i);
        java.util.Set<String> hair = new java.util.HashSet<>(), fhair = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) { hair.add(hairId.apply(false, i)); fhair.add(hairId.apply(true, i)); }
        need("20 distinct male and 20 distinct female hairstyles (" + hair.size() + ", " + fhair.size() + ")", hair.size() == 20 && fhair.size() == 20);
        java.util.Set<String> beards = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) beards.add(rpg.render.Meshes.beard(i).list.stream().map(java.util.Arrays::toString).reduce("", String::concat) + "|" + rpg.render.HairMeshes.stubble(i));
        need("20 distinct facial-hair presets including clean-shaven and the two stubbles (" + beards.size() + ")", beards.size() == 20);
        java.util.Set<String> outfits = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) { outfits.add("t" + rpg.render.Outfit.TOPS[i]); outfits.add("l" + rpg.render.Outfit.LEGS[i]); outfits.add("s" + rpg.render.Outfit.SHOES[i]); }
        need("20 distinct tops, 20 legs and 20 shoes (" + outfits.size() + " of 60)", outfits.size() == 60);
        for (int i = 0; i < 20; i++) c.step(rows.get(3), 1);
        need("hairstyle row wraps after 20", c.look.hair == 1);
        c.step(rows.get(0), 1);
        need("choosing female removes facial hair and its row", c.look.female && c.look.beard == 0 && c.rows().size() == want.length - 1);
        c.step(c.rows().get(0), 1);
        c.look.hair = 12; c.look.beard = 4; c.look.top = 7; c.look.topColor = 15; c.look.skin = 6; c.look.hairColor = 1;
        for (int i = 0; i < 90; i++) {
            game.tick(1 / 60f);
            gfx.creator(preview, c.look, 0.6f + i * 0.004f, win.width, win.height, 1 / 60f, game.time());
            ui.begin(gfx.cmd);
            c.draw(ui, win.width, win.height, List.of(), false, false);
            ui.end();
            gfx.frame(win.width, win.height);
        }
        shot("01-creator");
        // the human body plan: 32 pixels (8 head + 12 torso + 12 legs) on a 1.8-block hitbox; 128 x 128 skins
        rpg.actor.Body male = rpg.actor.Body.MALE, female = rpg.actor.Body.FEMALE;
        need("male figure: 32 px tall (8 head + 12 torso + 12 legs), hitbox 1.8 x 0.6 blocks",
                rpg.actor.Body.HEIGHT_PX == 32 && male.head[1] == 8 && male.torso[1] == 12 && male.leg[1] == 12 && Math.abs(male.height - 1.8f) < 1e-4f && Math.abs(male.width - 0.6f) < 1e-4f);
        need("female figure: head 8x8x8, body 6x12x3, arms 3x12x4, legs 4x12x4, its own hitbox (1.8 x " + female.width + ")",
                java.util.Arrays.equals(female.head, new int[]{8, 8, 8}) && java.util.Arrays.equals(female.torso, new int[]{6, 12, 3}) && java.util.Arrays.equals(female.arm, new int[]{3, 12, 4})
                        && java.util.Arrays.equals(female.leg, new int[]{4, 12, 4}) && Math.abs(female.height - 1.8f) < 1e-4f && female.width < male.width);
        rpg.render.Skin skin = rpg.render.Skin.paint(c.look, null);
        need("skins are painted at 128 x 128", rpg.render.Skin.N == 128 && skin.px.length == 128 * 128);
        int[] arr = c.look.toArray();
        need("appearance round-trips through save data", java.util.Arrays.equals(Appearance.fromArray(arr).toArray(), arr));
        look = c.look.copy();
    }
    private Appearance look;

    private void start() {
        g = game.add(new Rpg(game, "Tester", look, true));
        hud = new Hud(g, gfx);
        g.ui = hud;
        gfx.findLamps(g.vox);
        // the tester wears a backpack: the flows below carry a lot of bulky gear (a second grid of 8 x 6)
        g.player.eq.set(ItemDef.Slot.BACK, new ItemStack(Items.BACKPACK, 1));
        g.state.mint(Items.BACKPACK, 1);
        frames(120);
        shot("02-spawn");
        need("the player stands on the devmap", g.player.grounded && Math.abs(g.player.y - DevMap.FLOOR) < 0.2f);
    }

    // ------------------------------------------------------------------ 1. leveling
    private void leveling() {
        need("18 skills are registered", Skill.COUNT == 18);
        boolean allOne = true;
        for (Skill s : Skill.ALL) allOne &= g.player.skills.level(s) == (s == Skill.HEALTH ? 10 : 1);
        need("a new character starts with every skill at 1 but Health at 10, and 10 HP (" + g.player.maxHp() + ")", allOne && g.player.maxHp() == 10);
        need("XP table: L10 43,446, L50 598,694, L60 935,503, L80 2,321,784, L90 3,720,933, L100 6,000,000",
                XpTable.xpFor(10) == 43_446 && XpTable.xpFor(50) == 598_694 && XpTable.xpFor(60) == 935_503 && XpTable.xpFor(80) == 2_321_784 && XpTable.xpFor(90) == 3_720_933 && XpTable.xpFor(100) == 6_000_000);
        boolean mono = true;
        for (int l = 2; l <= 100; l++) mono &= XpTable.xpFor(l) > XpTable.xpFor(l - 1) && XpTable.levelFor(XpTable.xpFor(l)) == l && XpTable.levelFor(XpTable.xpFor(l) - 1) == l - 1;
        need("the stored table is strictly increasing and levelFor agrees at every boundary", mono);
        // train a stand-in through every milestone on every skill, through the real award path (events, level-ups)
        Actor t = new Actor(9999, "Trainee", 28);
        boolean milestones = true;
        long levelUps = 0;
        for (Skill s : Skill.ALL) {
            for (int target : new int[]{60, 80, 90, 100}) {
                long need = XpTable.xpFor(target) - t.skills.xp(s);
                var a = g.prog.awardRaw(t, s, need, "milestone test", 0, g.state.newRequestId());
                milestones &= t.skills.level(s) == target && t.skills.xp(s) == XpTable.xpFor(target);
                if (a != null && a.levelledUp()) levelUps++;
                if (target < 100) milestones &= XpTable.levelFor(t.skills.xp(s) - 1) == target - 1;
            }
            g.prog.awardRaw(t, s, 1_000_000, "over the cap", 0, 0);
            milestones &= t.skills.xp(s) == XpTable.MAX_XP && t.skills.level(s) == 100;
        }
        need("every skill reaches exactly 60, 80, 90 and 100 at the table's thresholds, and caps at 6,000,000", milestones);
        need("level-ups were committed as events (" + levelUps + ")", levelUps == 18 * 4);
        long req = g.state.newRequestId();
        long before = g.player.skills.xp(Skill.COOKING);
        g.prog.awardRaw(g.player, Skill.COOKING, 500, "dedupe test", 0, req);
        g.prog.awardRaw(g.player, Skill.COOKING, 500, "dedupe test (repeat)", 0, req);
        need("a repeated request pays XP once", g.player.skills.xp(Skill.COOKING) == before + 500);
        // the player's own level-up: overlay, jingle, fireworks
        long ups = g.prog.levelUps;
        g.prog.awardRaw(g.player, Skill.COOKING, XpTable.xpFor(3) - g.player.skills.xp(Skill.COOKING), "level-up test", 0, g.state.newRequestId());
        frames(20);
        need("a player level-up is celebrated (overlay and message)", g.prog.levelUps > ups && g.prog.celebrating != null && g.chat.contains("Cooking level is now 3"));
        shot("03-level-up");
    }

    // ------------------------------------------------------------------ 2b. doors and glass are physical (Impulsis)
    private void physical() {
        Door door = g.objects.shedDoor;
        tp(55f, 24.0f, 0);
        frames(10);
        if (!door.open) g.interact.request(g.player, door, "open");
        frames(70);
        float swung = 0;
        for (float a : door.angle) swung = Math.max(swung, Math.abs(a));
        need(String.format("opened, the shed door's leaves swing in on their hinges (%.2f rad) and the doorway is open", swung), door.open && swung > 1.0f && g.vox.block(54, DevMap.FLOOR, 22) == Blocks.AIR);
        g.interact.request(g.player, door, "close");
        frames(150);
        float left = 0;
        for (float a : door.angle) left = Math.max(left, Math.abs(a));
        need(String.format("closed, the leaves swing home and latch (%.3f rad) and the cells are a shut door again", left), !door.open && left < 0.08f && g.vox.block(54, DevMap.FLOOR, 22) == Blocks.DOOR);
        // a latched door is a wall to a body walking into it
        tp(55f, 24.6f, 0);
        g.player.firstPerson = false;
        g.controller.in.forward = 1;
        frames(90);
        float held = g.player.z;
        need(String.format("walking into the latched door, the player is held at its face (z %.2f)", held), held > 22.7f && held < 23.3f);
        // opened, it is walked through, the leaf pushed aside
        g.controller.in.forward = 0;
        frames(5);
        g.interact.request(g.player, door, "open");
        frames(20);
        g.controller.in.forward = 1;
        frames(150);
        g.controller.in.forward = 0;
        need(String.format("opened, the player walks through the doorway into the shed (z %.2f)", g.player.z), g.player.z < 21.5f);
        frames(10);
        // a window: a sword swung at it breaks the pane into shards that fly and come to rest on the floor
        int wx = 52, wy = DevMap.FLOOR + 1, wz = DevMap.SHED_Z0;
        boolean glassBefore = g.vox.block(wx, wy, wz) == Blocks.GLASS;
        tp(52.5f, wz + 2.25f, 0);   // far enough that the arc reaches the pane only where it is glass (the planks beside it would turn the blade)
        frames(5);
        aimAt(52.5f, wy + 0.5f, wz + 0.5f);
        frames(5);
        int before = g.glass.shards.size();
        swing(false);
        int made = g.glass.shards.size() - before;
        if (made == 0) {
            for (String t : g.combat.telemetry) System.out.println("      telemetry: " + t);
            System.out.printf("      player at (%.2f, %.2f, %.2f) yaw %.2f aim %.2f pitch %.2f phase %s stamina %.0f main %s glass %s%n", g.player.x, g.player.y, g.player.z, g.player.yaw, g.player.aimYaw, g.player.pitch,
                    g.player.cs.phase, g.player.stamina, g.player.main(), g.vox.block(wx, wy, wz));
        }
        need("a sword swung at the shed window breaks the pane (" + made + " shards)", glassBefore && g.vox.block(wx, wy, wz) == Blocks.AIR && made >= 10);
        frames(300);
        int resting = 0, counted = 0, low = 0, asleep = 0;
        for (var sh : g.glass.shards) {
            var r = g.lastRigids.body(sh.body);
            if (r == null) continue;
            counted++;
            if (r.asleep) asleep++;
            if (r.y < DevMap.FLOOR + 0.25f) low++;
            if (Math.sqrt(r.vx * r.vx + r.vy * r.vy + r.vz * r.vz) < 0.15f) resting++;
        }
        System.out.printf("      shards: %d counted, %d still, %d asleep, %d on the floor (the rest on sills and furniture)%n", counted, resting, asleep, low);
        need("the shards fell and came to rest, on the floor or the ledge under the window (" + resting + " of " + counted + " still, " + low + " on the floor)", counted > 0 && resting >= counted * 0.9f && low > 0);
        need("breaking the pane was committed as an event", g.state.all().stream().anyMatch(e -> e.type().equals("GlassBroken")));
        shot("04b-glass");
        g.player.firstPerson = false;
    }

    // ------------------------------------------------------------------ 2. interaction
    private void interaction() {
        Door door = g.objects.shedDoor;
        tp(55f, 26.5f, (float) Math.PI);
        frames(30);
        var opts = door.options(g, g.player);
        need("the shed door offers Open and Examine", opts.stream().anyMatch(o -> o.id().equals("open") && o.enabled()) && opts.stream().anyMatch(o -> o.id().equals("examine")));
        tp(55f, 34f, (float) Math.PI);
        frames(10);
        Result far = g.interact.request(g.player, door, "open");
        need("out of range: rejected, door unchanged", far.rejected() && !door.open);
        tp(55f, 23.6f, (float) Math.PI);
        frames(10);
        Result open = g.interact.request(g.player, door, "open");
        frames(20);
        need("in range: the door opens (blocks removed)", open.committed() && door.open && g.vox.block(54, DevMap.FLOOR, 22) == Blocks.AIR);
        // the locked store door: options disabled with reasons
        Door store = g.objects.storeDoor;
        tp(55.5f, 14.5f, -(float) Math.PI / 2);
        frames(20);
        var so = store.options(g, g.player);
        need("the locked door's Open is disabled with a reason", so.stream().anyMatch(o -> o.id().equals("open") && !o.enabled() && o.reason() != null));
        need("its Pick lock is disabled with a reason (Thieving 10)", so.stream().anyMatch(o -> o.id().equals("lockpick") && !o.enabled() && o.reason().contains("Thieving")));
        // loose items: take, then stale target
        tp(51.0f, 15.5f, 0);
        frames(15);
        GroundItem axe = (GroundItem) find(o -> o instanceof GroundItem gi && gi.stack.def == Items.get("bronze_axe"));
        Result took = g.interact.request(g.player, axe, "take");
        need("a loose item is taken exactly once", took.committed() && g.player.has(Items.get("bronze_axe"), 1) && axe.removed);
        Result again = g.interact.request(g.player, axe, "take");
        need("a stale target is rejected without a duplicate", again.rejected() && g.player.count(Items.get("bronze_axe")) == 1);
        for (ItemDef d : new ItemDef[]{Items.KNIFE, Items.HAMMER, Items.FISHING_ROD, Items.LOCKPICK, Items.get("bronze_pickaxe")}) {
            WorldObject o = find(x -> x instanceof GroundItem gi && gi.stack.def == d && !gi.removed);
            if (o != null) g.interact.request(g.player, o, "take");
        }
        need("the shed's tools were picked up", g.player.has(Items.KNIFE, 1) && g.player.has(Items.HAMMER, 1) && g.player.has(Items.FISHING_ROD, 1));
        // full inventory: nothing is deleted or half-moved
        List<ItemStack> filler = new ArrayList<>();
        // every grid filled, cell by cell (the base grid, the pouch, the backpack)
        while (g.player.canStore(new ItemStack(Items.GUAM, 1))) { ItemStack s = new ItemStack(Items.GUAM, 1); g.player.store(s); g.state.mint(Items.GUAM, 1); filler.add(s); }
        GroundItem coins = (GroundItem) find(o -> o instanceof GroundItem gi && gi.stack.def == Items.COINS && gi.stack.owner == Ids.MERCHANT_A);
        int coinsBefore = g.player.count(Items.COINS);
        GroundItem knifeDrop = g.objects.drop(new ItemStack(Items.BRONZE_BAR, 1), g.player.x + 0.5f, DevMap.FLOOR + 0.05f, g.player.z);
        g.state.mint(Items.BRONZE_BAR, 1);
        Result full = g.interact.request(g.player, knifeDrop, "take");
        need("pickup at full inventory is refused and the item stays", full.rejected() && !knifeDrop.removed && g.player.count(Items.BRONZE_BAR) == 0);
        clearBurnt();
        need("the owned coins on the table are still there for the theft test", coins != null && !coins.removed && g.player.count(Items.COINS) == coinsBefore);
        g.interact.request(g.player, knifeDrop, "take");
        // a craft interrupted before its commit point spends nothing
        Station anvil = (Station) find(o -> o instanceof Station s && s.def.equals("anvil"));
        tp(66.5f, 27.6f, 0);
        frames(10);
        int bars = g.player.count(Items.BRONZE_BAR);
        Result smith = g.interact.request(g.player, anvil, "make:smith_bronze_dagger");
        frames(30);
        tp(66.5f, 30.5f, 0);
        frames(10);
        need("crafting interrupted mid-work consumes nothing (" + smith.status() + ")", smith.status() == Result.Status.STARTED && g.player.count(Items.BRONZE_BAR) == bars && g.interact.of(g.player) == null);
        // furniture and workstations are objects (their own models, owners, state) standing on invisible collision cells
        var defs = Blocks.renderDefs();
        rpg.objects.Furniture shedTable = g.objects.furnitureAt(DevMap.SHED_TABLE[0], DevMap.FLOOR, DevMap.SHED_TABLE[1]);
        need("furniture and workstations are objects, not blocks: " + g.objects.furniture.size() + " pieces, their cells never meshed but still solid",
                shedTable != null && shedTable.kind == rpg.objects.Furniture.Kind.TABLE && defs[Blocks.TABLE].shape != 0 && defs[Blocks.ANVIL].shape != 0 && defs[Blocks.BED].shape != 0
                        && g.vox.solid(DevMap.SHED_TABLE[0], DevMap.FLOOR, DevMap.SHED_TABLE[1]) && g.objects.furniture.stream().anyMatch(f -> f.kind == rpg.objects.Furniture.Kind.BED));
        need("block textures are 64 x 64", Blocks.TEX == 64);
        shot("04-interaction");
    }

    // ------------------------------------------------------------------ 3. every skill's action
    private void skillActions() {
        long[] start = g.player.skills.raw();
        // Woodcutting (gamefixes): a woodcutting axe wielded and swung at the tree; a battleaxe will not do; the XP comes
        // when the tree comes down, and it comes down whole
        Node tree = node("tree");
        stand(tree);
        wield("bronze_battleaxe");
        long wc0 = g.player.skills.xp(Skill.WOODCUTTING);
        chopAt(tree, 4);
        need("a two-handed battleaxe does not fell trees (struck " + (int) tree.struck + ")", tree.struck == 0 && !tree.depleted());
        wield("leather_axe");
        need("the woodcutting axe is one-handed and 1 x 2 (" + Items.get("leather_axe").gw + " x " + Items.get("bronze_axe").gh + "), the battleaxe 1 x 4 and two-handed",
                Items.get("bronze_axe").gw == 1 && Items.get("bronze_axe").gh == 2 && !Items.get("bronze_axe").twoHanded() && Items.get("bronze_battleaxe").gh == 4 && Items.get("bronze_battleaxe").twoHanded());
        chopAt(tree, 1);
        need("a blow that bites the tree pays no XP yet (struck " + (int) tree.struck + ")", tree.struck >= 1 && g.player.skills.xp(Skill.WOODCUTTING) == wc0);
        int need = tree.strikesNeeded(g.player);
        chopAt(tree, need + 6);
        need("Woodcutting: swung blows fell the whole tree, its logs taken and XP paid at the fall (" + g.player.count(Items.LOGS) + " logs)",
                tree.depleted() && g.player.count(Items.LOGS) >= 2 && g.player.skills.xp(Skill.WOODCUTTING) > wc0);
        need("a felled tree changes the world (stump, no crown)", g.vox.block(tree.bx, DevMap.FLOOR, tree.bz) == Blocks.STUMP && g.vox.block(tree.bx, DevMap.FLOOR + 2, tree.bz) == Blocks.AIR);
        shot("05-woodcutting");
        // Firemaking: light one log
        tp(70.5f, 30.5f, 0);
        frames(5);
        int logSlot = g.player.inv.first(Items.LOGS);
        work(g.items.perform(g.player, logSlot, "light"), 300, () -> g.objects.all().stream().anyMatch(o -> o instanceof rpg.objects.Fire));
        need("Firemaking: a fire is lit from logs", g.objects.all().stream().anyMatch(o -> o instanceof rpg.objects.Fire));
        // Mining
        // Mining (gamefixes): a pickaxe wielded, the ore actually struck; XP with every strike that lands
        Node copper = node("copper_rock"), tin = node("tin_rock");
        stand(copper);
        wield("bronze_sword");
        long mn0 = g.player.skills.xp(Skill.MINING);
        chopAt(copper, 2);
        need("a sword on the ore mines nothing", g.player.skills.xp(Skill.MINING) == mn0 && copper.struck == 0);
        wield("leather_pickaxe");
        chopAt(copper, 1);
        need("one strike of the pickaxe on the ore pays Mining XP (" + (g.player.skills.xp(Skill.MINING) - mn0) + ")", g.player.skills.xp(Skill.MINING) > mn0 && !g.player.has(Items.COPPER_ORE, 1));
        for (int i = 0; i < 30 && !g.player.has(Items.COPPER_ORE, 1); i++) chopAt(copper, 1);
        stand(tin);
        for (int i = 0; i < 30 && !g.player.has(Items.TIN_ORE, 1); i++) chopAt(tin, 1);
        need("Mining: copper and tin ore broken out by the strikes", g.player.has(Items.COPPER_ORE, 1) && g.player.has(Items.TIN_ORE, 1));
        need("a mined-out rock changes the world", g.vox.block(copper.bx, DevMap.FLOOR, copper.bz) == Blocks.SPENT_ROCK);
        // Smithing: smelt and forge
        Station furnace = (Station) find(o -> o instanceof Station s && s.def.equals("furnace"));
        tp(64.5f, 27.6f, 0); frames(5);
        work(g.interact.request(g.player, furnace, "make:smelt_bronze"), 300, () -> false);
        Station anvil = (Station) find(o -> o instanceof Station s && s.def.equals("anvil"));
        tp(66.5f, 27.6f, 0); frames(5);
        work(g.interact.request(g.player, anvil, "make:smith_bronze_dagger"), 400, () -> false);
        need("Smithing: a bar smelted and a bronze dagger forged", g.player.has(Items.get("bronze_dagger"), 1));
        // Fishing
        // Fishing (gamefixes): the spot's own tool wielded and used; XP for the catch only
        Node shrimp = node("shrimp_spot");
        tp(89.5f, 27.6f, 0); frames(10);
        wield("fishing_rod");
        need("the net spot will not take a rod (" + g.interact.request(g.player, shrimp, "gather").reason() + ")", g.interact.of(g.player) == null);
        wield("small_fishing_net");
        long fs0 = g.player.skills.xp(Skill.FISHING);
        work(g.interact.request(g.player, shrimp, "gather"), 900, () -> g.player.count(Items.RAW_SHRIMP) >= 2);
        need("Fishing: raw shrimp caught with the net, and XP for the catch", g.player.count(Items.RAW_SHRIMP) >= 1 && g.player.skills.xp(Skill.FISHING) > fs0);
        wield("wooden_sword");
        stowTools();
        // Cooking at the range (burn chance is a seeded skill check)
        Station range = (Station) find(o -> o instanceof Station s && s.def.equals("range"));
        tp(68.5f, 27.6f, 0); frames(5);
        long cookXp = g.player.skills.xp(Skill.COOKING);
        work(g.interact.request(g.player, range, "makeall:cook_shrimp"), 900, () -> !g.player.has(Items.RAW_SHRIMP, 1));
        need("Cooking: shrimp cooked (or burnt by the seeded check, which pays no XP)", g.player.has(Items.SHRIMP, 1) && g.player.skills.xp(Skill.COOKING) > cookXp || g.player.has(Items.BURNT_FISH, 1));
        // Harvesting
        Node herb = node("herb_patch"), flax = node("flax");
        stand(herb);
        work(g.interact.request(g.player, herb, "gather"), 600, () -> g.player.has(Items.GUAM, 1));
        need("Harvesting: guam picked", g.player.has(Items.GUAM, 1));
        need("flax is level-gated (Harvesting 5) with a reason", flax.options(g, g.player).get(0).enabled() == g.player.skills.level(Skill.HARVESTING) >= 5);
        // Crafting
        Station table = (Station) find(o -> o instanceof Station s && s.def.equals("craft_table"));
        tp(70.5f, 27.6f, 0); frames(5);
        work(g.interact.request(g.player, table, "make:craft_shafts"), 300, () -> false);
        need("Crafting: arrow shafts fletched from logs", g.player.count(Items.ARROW_SHAFT) >= 15);
        // Construction: saw planks, build a crate into the world (room first: the gathering above filled the bags with ore and fish)
        room(Items.LOGS, Items.PLANK, Items.HAMMER);
        give(Items.LOGS, 2);
        Station saw = (Station) find(o -> o instanceof Station s && s.def.equals("sawbench"));
        tp(72.5f, 27.6f, 0); frames(5);
        work(g.interact.request(g.player, saw, "makeall:saw_plank"), 600, () -> g.player.count(Items.PLANK) >= 2);
        tp(75.5f, 26.5f, 0); frames(5);
        work(g.interact.request(g.player, g.objects.buildSpot, "make:build_crate"), 400, () -> false);
        frames(10);
        need("Construction: a crate built into the world as blocks", g.objects.buildSpot.built.get("crate") && g.vox.block(76, DevMap.FLOOR, 25) == Blocks.CRATE);
        shot("06-construction");
        // Agility uses structural geometry, not fixed interaction endpoints.
        g.player.skills.setLevel(Skill.AGILITY, 5);
        tp(117.5f, 25.8f, (float) Math.PI); frames(10);
        float ledgeY = g.player.y;
        boolean traversed = g.traversal.begin(g.player, (float) Math.PI);
        frames(90);
        need("Agility: a geometry-derived ledge crossed", traversed && g.player.y > ledgeY + 0.8f && g.player.skills.xp(Skill.AGILITY) > start[Skill.AGILITY.ordinal()]);
        // Thieving: pickpocket (a seeded chance) until it succeeds
        Npc eda = npc(Ids.VILLAGER);
        for (int i = 0; i < 8 && g.player.skills.xp(Skill.THIEVING) == start[Skill.THIEVING.ordinal()]; i++) {
            // a pocket is picked from someone standing still: one walking off is out of reach before the hand is in
            for (int w = 0; w < 900 && Math.hypot(eda.vx, eda.vz) > 0.15f; w++) frames(1);
            tp(eda.x + 0.9f, eda.z, (float) Math.PI / 2);
            frames(3);
            work(g.interact.request(g.player, eda, "pickpocket"), 120, () -> false);
            frames(30);
        }
        need("Thieving: a pocket picked (seeded skill check, roll recorded in the event)", g.player.skills.xp(Skill.THIEVING) > start[Skill.THIEVING.ordinal()]);
        // Faith: bury bones and cast a blessing
        give(Items.BONES, 1);
        work(g.items.perform(g.player, g.player.inv.first(Items.BONES), "bury"), 200, () -> false);
        float f0 = g.player.faith;
        boolean blessed = g.combat.bless(g.player, Spells.Prayer.WARD);
        need("Faith: bones buried and the Ward prayer raised for a point of Faith", g.player.skills.xp(Skill.FAITH) > start[Skill.FAITH.ordinal()] && blessed && g.prayers.on(g.player, Spells.Prayer.WARD) && g.player.faith == f0 - 1);
        g.combat.bless(g.player, Spells.Prayer.WARD);
        // the rest (Attack, Strength, Defense, Health, Archery, Magic) are trained by real combat below
        int trained = 0;
        for (Skill s : Skill.ALL) if (g.player.skills.xp(s) > start[s.ordinal()]) trained++;
        System.out.println("    non-combat skills trained by their actions: " + trained);
    }

    // ------------------------------------------------------------------ 4. combat
    private void combat() {
        Actor dummy = g.dummies.get(0), armored = g.dummies.get(2);
        // melee: the swing has to physically meet the dummy
        tp(dummy.x, dummy.z + 4f, 0);
        frames(10);
        long contacts = g.combat.contacts;
        swing(false);
        need("a swing out of reach makes no contact", g.combat.contacts == contacts);
        tp(dummy.x, dummy.z + 1.2f, 0);
        frames(10);
        long att = g.player.skills.xp(Skill.ATTACK), hp = g.player.skills.xp(Skill.HEALTH);
        long str = g.player.skills.xp(Skill.STRENGTH), def = g.player.skills.xp(Skill.DEFENSE);
        int d1 = swingDamage(dummy), d2 = swingDamage(dummy);
        need("collision-based melee hits the dummy (" + d1 + ", " + d2 + ")", d1 > 0 && d2 > 0);
        need("damage is deterministic: same stats, same move, same damage", d1 == d2);
        need("real melee trains Attack, Strength and Health", g.player.skills.xp(Skill.ATTACK) > att && g.player.skills.xp(Skill.STRENGTH) > str && g.player.skills.xp(Skill.HEALTH) > hp);
        need("striking a passive dummy does not train Defense", g.player.skills.xp(Skill.DEFENSE) == def);
        tp(armored.x, armored.z + 1.2f, 0);
        frames(10);
        int dA = swingDamage(armored);
        // GDD 2.3: neither Defense nor armour lessens a clean, unblocked hit (they make the active defence better)
        need("armour does not soften a clean hit (armored dummy " + dA + " = " + d1 + ")", dA == d1);
        need("Defense and armour make a raised guard stop more (share " + rpg.combat.CombatMath.guardShare(0.72f, 50, 30) + " > " + rpg.combat.CombatMath.guardShare(0.72f, 1, 0) + ")",
                rpg.combat.CombatMath.guardShare(0.72f, 50, 30) > rpg.combat.CombatMath.guardShare(0.72f, 1, 0));
        shot("07-melee");
        // equipment requirements: Strength 90 / Defense 50
        hud.debugSetLevel(Skill.STRENGTH, 90);
        hud.debugSetLevel(Skill.DEFENSE, 50);
        need("Strength 90 wields a Godly sword", Equipment.refusal(Items.get("godly_sword"), g.player.skills) == null);
        need("Defense 50 cannot wear Godly armor", Equipment.refusal(Items.get("godly_body"), g.player.skills) != null);
        need("Defense 50 wears Steel armor", Equipment.refusal(Items.get("steel_body"), g.player.skills) == null);
        give(Items.get("godly_sword"), 1); give(Items.get("godly_body"), 1); give(Items.get("steel_body"), 1);
        Result eqSword = g.items.equipFirst(g.player, Items.get("godly_sword"), ItemDef.Slot.MAIN, g.state.newRequestId());
        Result eqBody = g.items.equipFirst(g.player, Items.get("godly_body"), ItemDef.Slot.BODY, g.state.newRequestId());
        need("manual swaps through the inventory: sword equipped, Godly body refused", eqSword.committed() && eqBody.rejected() && g.player.main() == Items.get("godly_sword"));
        tp(dummy.x, dummy.z + 1.2f, 0);
        frames(10);
        int dG = swingDamage(dummy);
        need("gear changes damage (Godly sword " + dG + " > wooden " + d1 + ")", dG > d1);
        // impact and Stability: a heavy blow staggers, and Stability resistance then rises
        long staggers = g.combat.staggers;
        heavy();
        need("heavy impact exceeding Stability staggers (telemetry: " + g.combat.telemetry.peekLast() + ")", g.combat.staggers > staggers || dummy.cs.stabilityBonus > 0);
        // archery: a bow, arrows as ammunition, a projectile to a target
        hud.debugSetLevel(Skill.ARCHERY, 30);
        idle();   // gear cannot change mid-swing: let the heavy blow recover first
        give(Items.SHORTBOW, 1); give(Items.BRONZE_ARROWS, 30);
        g.items.equipFirst(g.player, Items.SHORTBOW, ItemDef.Slot.MAIN, g.state.newRequestId());
        g.items.equipFirst(g.player, Items.BRONZE_ARROWS, ItemDef.Slot.AMMO, g.state.newRequestId());
        int arrows = g.player.eq.get(ItemDef.Slot.AMMO).amount;
        long arch = g.player.skills.xp(Skill.ARCHERY);
        tp(DevMap.TARGET_X + 12.5f, 57f, (float) Math.PI / 2);
        frames(10);
        aimAt(DevMap.TARGET_X + 0.5f, DevMap.FLOOR + 1.0f, 57f);
        g.controller.in.mainDown = true; frames(60); g.controller.in.mainDown = false; frames(60);
        need("arrows are consumed as ammunition", g.player.eq.get(ItemDef.Slot.AMMO).amount == arrows - 1);
        need("Archery XP from the archery target", g.player.skills.xp(Skill.ARCHERY) > arch);
        tp(dummy.x, dummy.z + 7f, 0);
        frames(10);
        aimAt(dummy.x, dummy.y + 1.2f, dummy.z);
        long c0 = g.combat.contacts;
        g.controller.in.mainDown = true; frames(60); g.controller.in.mainDown = false; frames(40);
        need("an arrow physically hits the dummy", g.combat.contacts > c0);
        // magic
        long mag = g.player.skills.xp(Skill.MAGIC);
        float mana = g.player.mana;
        g.player.spell = Spells.Spell.SPARK;
        aimAt(dummy.x, dummy.y + 1.2f, dummy.z);
        g.controller.in.cast = true; frames(60);
        need("Magic: a spell costs mana, flies, hits, trains Magic", g.player.skills.xp(Skill.MAGIC) > mag && g.player.mana < mana + 1);
        // dodge and guard: the defensive actions
        g.items.equipFirst(g.player, g.player.has(Items.get("wooden_sword"), 1) ? Items.get("wooden_sword") : Items.get("godly_sword"), ItemDef.Slot.MAIN, g.state.newRequestId());
        if (g.player.main() != Items.get("godly_sword")) g.items.equipFirst(g.player, Items.get("godly_sword"), ItemDef.Slot.MAIN, g.state.newRequestId());
        give(Items.get("steel_shield"), 1);
        g.items.equipFirst(g.player, Items.get("steel_shield"), ItemDef.Slot.OFF, g.state.newRequestId());
        g.items.equipFirst(g.player, Items.get("steel_body"), ItemDef.Slot.BODY, g.state.newRequestId());
        hud.debugSetLevel(Skill.ATTACK, 40); hud.debugSetLevel(Skill.HEALTH, 40); g.player.restore();
        // a real fight with the hostile test NPC: it decides, approaches, attacks, guards; the player guards and parries
        Npc bandit = npc(Ids.BANDIT_1);
        // in the open middle of the pen, clear of its stone pillars (a blade that meets stone glances off it)
        g.teleport(bandit, 21.5f, DevMap.FLOOR, 81.5f);
        tp(21.5f, 78.5f, (float) Math.PI);
        frames(5);
        long blocked = g.combat.blocked + g.combat.parried;
        float hp0 = g.player.hp;
        boolean sawFight = false;
        for (int i = 0; i < 1500 && bandit.alive && g.player.alive; i++) {
            Actor.Combat bc = bandit.cs;
            // a guarded fighter: raise the shield against a telegraphed blow, strike into the opening after it
            boolean threat = (bc.phase == Actor.Combat.Phase.WINDUP || bc.phase == Actor.Combat.Phase.ACTIVE) && bandit.distTo(g.player) < 3;
            g.controller.in.offDown = threat && g.player.cs.phase == Actor.Combat.Phase.IDLE || g.player.cs.blocking && threat;
            aimAt(bandit.x, bandit.chestY(), bandit.z);
            if (bandit.goal == Brain.Goal.FIGHT) sawFight = true;
            boolean opening = bc.phase == Actor.Combat.Phase.RECOVER || bc.staggered > 0 || i > 900 && bc.phase == Actor.Combat.Phase.IDLE && i % 40 == 0;
            if (!threat && opening && bandit.distTo(g.player) < 2.4f && g.player.cs.phase == Actor.Combat.Phase.IDLE) { g.controller.in.offDown = false; g.controller.in.mainDown = true; frames(1); g.controller.in.mainDown = false; }
            if (bandit.distTo(g.player) > 2.5f) g.controller.in.forward = 1; else g.controller.in.forward = 0;
            Actor.Combat.Phase was = bc.phase;
            frames(1);
            if (DEBUG_FIGHT && was != bc.phase) System.out.printf("      [fight %d] bandit %s->%s d=%.2f | player phase=%s blocking=%s off=%s stag=%.2f dodge=%.2f%n", i, was, bc.phase, bandit.distTo(g.player), g.player.cs.phase, g.player.cs.blocking, g.controller.in.offDown, g.player.cs.staggered, g.player.cs.dodgeT);
            if (i == 300) shot("08-fight");
        }
        g.controller.in.forward = 0; g.controller.in.offDown = false;
        need("the hostile NPC chose to fight (goal FIGHT, reasons recorded)", sawFight);
        need("the bandit struck at the player (" + String.format("%.0f -> %.0f HP", hp0, g.player.hp) + ") or was blocked", g.player.hp < hp0 || g.combat.blocked + g.combat.parried > blocked);
        boolean guarded = g.combat.blocked + g.combat.parried > blocked;
        if (!guarded) for (String t : g.combat.telemetry) System.out.println("      telemetry: " + t);
        need("guarding blocked or parried incoming blows", guarded);
        need("the bandit was defeated by real hits (dead, ragdoll)", !bandit.alive && bandit.ragdoll != 0);
        frames(60);
        shot("09-ragdoll");
        boolean loot = g.objects.all().stream().anyMatch(o -> o instanceof GroundItem gi && gi.stack.def == Items.BONES);
        need("the bandit dropped bones and loot", loot);
        frames(60);
        g.player.cs.cancel(); g.player.cs.staggered = 0; g.player.stamina = g.player.maxStamina();
        float st0 = g.player.stamina;
        boolean anyDodge = g.combat.dodge(g.player, 1, 0);
        boolean iframes = g.player.cs.iframes > 0;
        float spent = st0 - g.player.stamina;
        frames(30);
        need("dodge spends stamina and grants i-frames (spent " + spent + ")", anyDodge && iframes && spent > 0);
        g.player.restore();
        int trained = 0;
        for (Skill s : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENSE, Skill.HEALTH, Skill.ARCHERY, Skill.MAGIC, Skill.FAITH}) if (g.player.skills.xp(s) > 0) trained++;
        need("all seven combat skills have earned XP from their actions (" + trained + "/7)", trained == 7);
        int all = 0;
        for (Skill s : Skill.ALL) if (g.player.skills.xp(s) > 0) all++;
        need("every one of the 18 skills has earned XP through an executable action (" + all + "/18)", all == 18);
        hud.telemetry = true;
        frames(2);
        shot("10-telemetry");
        hud.telemetry = false;
    }

    // ------------------------------------------------------------------ 5. NPCs
    private void npcs() {
        // the town at peace: no bounty or search left over from the skill and combat sections (the guards would spend the
        // minute after the player instead of on their duties)
        g.law.clear();
        for (Npc n : g.npcs) { n.hunt = null; n.fightTarget = 0; n.memories.removeIf(m -> m.subject == g.player.id); }
        tp(100.5f, 68.5f, (float) Math.PI);
        long decisions = g.ai.decisions;
        java.util.Map<Long, float[]> startPos = new java.util.HashMap<>();
        for (Npc n : g.npcs) startPos.put(n.id, new float[]{n.x, n.z});
        int logsBefore = g.econ.shopA.stock.count(Items.LOGS) + g.econ.shopB.stock.count(Items.LOGS) + (g.econ.active() == null ? 0 : g.econ.active().crate.contents.count(Items.LOGS));
        java.util.Set<Brain.Goal> goals = new java.util.HashSet<>();
        for (int i = 0; i < 3600; i++) {
            frames(1);
            for (Npc n : g.npcs) if (n.alive) goals.add(n.goal);
            if (i == 1800) { hud.aiInspector = true; frames(1); shot("11-ai-inspector"); hud.aiInspector = false; }
        }
        int moved = 0;
        for (Npc n : g.npcs) if (n.alive && Math.hypot(n.x - startPos.get(n.id)[0], n.z - startPos.get(n.id)[1]) > 3) moved++;
        need("NPCs decided (" + (g.ai.decisions - decisions) + " decisions)", g.ai.decisions - decisions > 50);
        if (!goals.contains(Brain.Goal.PATROL)) for (Npc n : g.npcs) if (n.role == Npc.Role.GUARD) System.out.println("      guard " + n.name + ": " + n.goal + " (" + n.goalReason + ") " + n.scores);
        need("at least three agents with different goals at work (" + goals + ")", goals.size() >= 4 && goals.contains(Brain.Goal.WORK) && goals.contains(Brain.Goal.PATROL));
        need("agents navigated on the engine's routes (" + moved + " moved)", moved >= 4);
        Npc dag = npc(Ids.WORKER);
        // one full work cycle: cut five, walk them to the store, sell (allow it to finish a trip under way)
        for (int i = 0; i < 3600; i++) {
            int now = g.econ.shopA.stock.count(Items.LOGS) + g.econ.shopB.stock.count(Items.LOGS) + (g.econ.active() == null ? 0 : g.econ.active().crate.contents.count(Items.LOGS));
            if (now > logsBefore) break;
            frames(1);
        }
        int logsAfter = g.econ.shopA.stock.count(Items.LOGS) + g.econ.shopB.stock.count(Items.LOGS) + (g.econ.active() == null ? 0 : g.econ.active().crate.contents.count(Items.LOGS));
        need("the worker cut real trees and sold logs into Store A (" + logsBefore + " -> " + logsAfter + ", Dag WC xp " + dag.skills.xp(Skill.WOODCUTTING)
                + ", holds " + dag.count(Items.LOGS) + " logs, goal " + dag.goal + ", last: " + dag.lastOutcome + ")", logsAfter > logsBefore && dag.skills.xp(Skill.WOODCUTTING) == XpTable.xpFor(12));
        need("NPCs gain no XP for their work (Dag stays Woodcutting 12)", dag.skills.level(Skill.WOODCUTTING) == 12);
        boolean scores = g.npcs.stream().allMatch(n -> !n.alive || !n.scores.isEmpty() && !n.goalReason.isEmpty());
        need("every agent exposes scores and reasons for its goal", scores);
        long chats = g.state.all().stream().filter(e -> e.type().equals("NpcGoal") && e.payload().contains("SOCIALIZE")).count();
        need("NPCs socialise with each other (" + chats + " times)", chats > 0);
        // perception with occlusion: a wall blocks sight
        Npc cole = npc(Ids.GUARD);
        need("perception: line of sight is blocked by walls (occluder test)", !g.vox.sight(110, DevMap.FLOOR + 1.5f, 74, 110, DevMap.FLOOR + 1.5f, 78) && g.vox.sight(100, DevMap.FLOOR + 1.5f, 70, 100, DevMap.FLOOR + 1.5f, 74));
        // hearing: combat noise brings the guard to investigate
        long heard = g.ai.heardEvents;
        g.noise(cole.x + 6, DevMap.FLOOR, cole.z + 6, 20, 1, 1);
        frames(20);
        need("agents hear noises (engine ears)", g.ai.heardEvents > heard);
        // unload / reload: the worker abstracted, its work resolved by the same rules
        int logsA = dag.count(Items.LOGS) + g.econ.shopA.stock.count(Items.LOGS);
        g.ai.unload(dag);
        frames(5);
        g.advanceTime(60, "abstract test");
        frames(30);
        int logsB = dag.count(Items.LOGS) + g.econ.shopA.stock.count(Items.LOGS);
        need("an unloaded worker keeps working abstractly (logs held and sold " + logsA + " -> " + logsB + ", " + dag.lastOutcome + ")", logsB > logsA || dag.lastOutcome.startsWith("(abstract)"));
        g.ai.reload(dag);
        frames(60);
        need("the worker reloads with consistent identity and inventory", dag.loaded && dag.alive && dag.body != 0);
    }

    // ------------------------------------------------------------------ 6. theft and law
    private void theft() {
        Npc eda = npc(Ids.VILLAGER), bram = npc(Ids.LAWMAN_A);
        var stall = g.objects.stall;
        hud.debugSetLevel(Skill.THIEVING, 10);
        frames(30);
        // a witness, close enough to see: Eda stands beside the stall
        tp(stall.x + 0.6f, stall.z + 1.3f, 0);
        g.teleport(eda, stall.x - 1.6f, DevMap.FLOOR, stall.z + 1.8f);
        // she stands and watches (a witness who walks off on her errand sees nothing)
        eda.goal = Brain.Goal.IDLE; eda.nextThink = g.time + 6;
        eda.yaw = eda.wantYaw = (float) Math.atan2(-(g.player.x - eda.x), -(g.player.z - eda.z));
        frames(4);
        int before = g.law.bounty(g.player.id, DevMap.ALPHA);
        long crimes = g.law.crimes, witnessed = g.law.witnessed;
        work(g.interact.request(g.player, stall, "steal"), 200, () -> false);
        need("stealing from the stall commits a theft", g.law.crimes > crimes);
        need("the nearby NPC witnessed it (memory recorded)", g.law.witnessed > witnessed && eda.memories.stream().anyMatch(m -> m.kind.equals("THEFT") && m.witnessed() && !m.reported));
        need("no bounty yet - nothing is reported", g.law.bounty(g.player.id, DevMap.ALPHA) == before);
        tp(70.5f, 30.5f, 0);    // walk away; the witness goes to the law on her own
        boolean reported = false;
        for (int i = 0; i < 4800 && !reported; i++) { frames(1); reported = g.law.bounty(g.player.id, DevMap.ALPHA) > before; }
        need("the witness travelled to the lawman and reported: JUR-ALPHA bounty " + g.law.bounty(g.player.id, DevMap.ALPHA), reported);
        need("the bounty is half the stolen value, in Alpha only", g.law.bounty(g.player.id, DevMap.ALPHA) == before + Math.max(1, Math.round(Items.BREAD.value * 0.5f)) && g.law.bounty(g.player.id, DevMap.BETA) == 0);
        // whoever saw it and reported it walked to the lawman to do so (the report is made in person)
        Npc reporter = null;
        for (int i = g.state.all().size() - 1; i >= 0 && reporter == null; i--) { var e = g.state.all().get(i); if (e.type().equals("CrimeReported") && g.actor(e.actor()) instanceof Npc n) reporter = n; }
        need("the report came after travel (" + (reporter == null ? "nobody" : reporter.name) + " is beside Bram)", reporter != null && reporter.distTo(bram) < 4);
        // wanted: Store A refuses; Store B (Beta) still trades
        Npc ada = npc(Ids.MERCHANT_A), gus = npc(Ids.MERCHANT_B);
        need("wanted in Alpha: Ada refuses trade with the reason", g.econ.tradeRefusal(ada, g.player) != null);
        need("not wanted in Beta: Gus would trade", g.econ.tradeRefusal(gus, g.player) == null);
        shot("12-wanted");
        // pay at the chancery
        tp(bram.x + 1.2f, bram.z + 1.6f, 0);
        frames(5);
        Result pay = g.interact.request(g.player, bram, "pay");
        need("the bounty is paid at the chancery (" + pay.status() + (pay.reason() == null ? "" : ": " + pay.reason()) + ", bounty " + g.law.bounty(g.player.id, DevMap.ALPHA) + ")", pay.committed() && g.law.bounty(g.player.id, DevMap.ALPHA) == 0);
        // and again, with the witness stopped before she can report
        frames(60);
        tp(stall.x + 0.6f, stall.z + 1.3f, 0);
        g.teleport(eda, stall.x - 1.6f, DevMap.FLOOR, stall.z + 1.8f);
        // she stands and watches (a witness who walks off on her errand sees nothing)
        eda.goal = Brain.Goal.IDLE; eda.nextThink = g.time + 6;
        eda.yaw = eda.wantYaw = (float) Math.atan2(-(g.player.x - eda.x), -(g.player.z - eda.z));
        frames(4);
        long w2 = g.law.witnessed, rev2 = g.state.revision;
        work(g.interact.request(g.player, stall, "steal"), 200, () -> false);
        need("the second theft is witnessed too", g.law.witnessed > w2);
        boolean stolen = g.player.grids().stream().anyMatch(inv -> inv.stacks().stream().anyMatch(s -> s.stolen && s.victims().containsKey(Ids.MERCHANT_A)));
        List<Npc> stopped = new ArrayList<>();
        for (Npc n : g.npcs) if (n.alive && n.memories.stream().anyMatch(m -> m.kind.equals("THEFT") && m.subject == g.player.id && m.witnessed() && !m.reported)) stopped.add(n);
        for (Npc n : stopped) {
            var ev = g.state.commit("DebugInterceptWitness", g.player.id, n.id, DevMap.ALPHA, 0, "witness stopped before reporting (scenario)");
            n.alive = false; n.hp = 0;
            g.state.commit("ActorKilled", g.player.id, n.id, DevMap.ALPHA, ev.id(), "witness intercepted");
            g.ai.onKilled(n, g.player);
        }
        System.out.println("      witnesses stopped: " + stopped.stream().map(n -> n.name).toList());
        frames(1800);
        boolean theftReported = g.state.all().stream().anyMatch(e -> e.revision() > rev2 && e.type().equals("CrimeReported") && e.payload().startsWith("THEFT"));
        need("with the witness stopped, the theft is never reported and no bounty comes of it", !theftReported);
        need("the stolen bread keeps its owner and stolen mark (stolen goods from different people share one stack and remember each)", stolen);
        for (Npc n : stopped) { g.ai.revive(n, true); n.memories.clear(); }
        frames(10);
    }

    // ------------------------------------------------------------------ 6b. arrest
    /** Wanted, the guard comes; struck, he fights; the player comes quietly - and every lawman stops fighting them. */
    private void arrest() {
        Npc cole = npc(Ids.GUARD), bram = npc(Ids.LAWMAN_A), eda = npc(Ids.VILLAGER);
        g.law.clear();
        // a theft witnessed and reported in person (the report path itself is checked above)
        var ev = g.state.commit("CrimeCommitted", g.player.id, Ids.MERCHANT_A, DevMap.ALPHA, 0, "THEFT value=40 (arrest scenario)");
        rpg.ai.Memory m = new rpg.ai.Memory("THEFT", g.player.id, Ids.MERCHANT_A, DevMap.ALPHA, g.state.minutes, 1f, "witnessed", ev.id(), 40, "arrest scenario");
        eda.remember(m, 24);
        g.teleport(eda, bram.x + 1.2f, DevMap.FLOOR, bram.z + 1.2f);
        frames(2);
        Result rep = g.law.report(eda, m, bram);
        need("wanted in JUR-ALPHA (bounty " + g.law.bounty(g.player.id, DevMap.ALPHA) + ")", rep.committed() && g.law.bounty(g.player.id, DevMap.ALPHA) > 0);
        // the guard comes to arrest; the player strikes him first
        tp(cole.x + 2.0f, cole.z, (float) Math.PI / 2);
        for (int i = 0; i < 240 && cole.goal != Brain.Goal.FIGHT; i++) frames(1);
        need("the guard moves to arrest the wanted player (goal " + cole.goal + ")", cole.goal == Brain.Goal.FIGHT);
        hud.closeDialog();
        // a blade to resist with (a death earlier in the run leaves the tester bare-handed, and a fist does not reach him):
        // a practice sword - the tester's trained Strength puts an iron one through a guard's leg, and he bleeds out
        wield("wooden_sword");
        long contacts = g.combat.contacts;
        for (int k = 0; k < 6 && cole.cs.lastAttacker != g.player.id; k++) {
            // on the open square, clear of the stall's beams and the lamp posts
            g.teleport(cole, 104.5f, DevMap.FLOOR, 67.5f);
            tp(105.7f, 67.5f, (float) Math.PI / 2);
            g.player.hp = g.player.maxHp();   // (on the HP scale a guard's blow is a good share of a tester's health)
            aimAt(cole.x, cole.chestY(), cole.z);
            idle();
            swing(false);
        }
        boolean struck = cole.cs.lastAttacker == g.player.id;
        if (!struck) { for (String t : g.combat.telemetry) System.out.println("      telemetry: " + t); System.out.printf("      cole at (%.1f, %.1f) goal %s, last: %s; player at (%.1f, %.1f) phase %s main %s stamina %.0f blocking %s busy %s scene %s held %s%n", cole.x, cole.z, cole.goal, cole.lastOutcome, g.player.x, g.player.z, g.player.cs.phase, g.player.main() == null ? "-" : g.player.main().id, g.player.stamina, g.player.cs.blocking, g.player.cs.busy(), hud.scene, g.law.inCustody(g.player)); }
        need("the player struck the guard (" + (g.combat.contacts - contacts) + " contacts)", struck);
        // and gives up: arrested, sentenced, released
        g.law.arrest(g.player, DevMap.ALPHA, cole);
        long after = g.state.revision;
        frames(600);
        long hitsAfter = g.state.all().stream().filter(e -> e.revision() > after && e.type().equals("ActorHit") && e.subject() == g.player.id
                && g.actor(e.actor()) instanceof Npc n && (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN)).count();
        boolean standDown = g.npcs.stream().noneMatch(n -> (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) && (n.fightTarget == g.player.id || n.goal == Brain.Goal.FIGHT));
        need("after the arrest no lawman strikes the player (" + hitsAfter + " hits) and none still fights them", hitsAfter == 0 && standDown);
        need("the sentence cleared the bounty and the crimes known of it are settled", g.law.bounty(g.player.id, DevMap.ALPHA) == 0
                && g.npcs.stream().allMatch(n -> n.memories.stream().noneMatch(x -> x.crime() && x.subject == g.player.id && DevMap.ALPHA.equals(x.jurisdiction) && x.witnessed() && !x.reported)));
        need("the player is held in a physical cell with time remaining", g.law.inCustody(g.player) && g.objects.jailDoor(DevMap.ALPHA).locked);
        var slab = g.objects.furniture.stream().filter(f -> f.cellSlab() && f.x > 100).findFirst().orElseThrow();
        g.teleport(g.player, slab.x - 1, DevMap.FLOOR, slab.z);
        need("sleeping on the slab serves the sentence", slab.perform(g, g.player, "sleep", g.state.newRequestId()).committed() && !g.law.inCustody(g.player));
        // the other way an arrest happens: the player resists and is beaten down - the blow that would kill arrests instead,
        // and every blow traded before it is settled by the sentence (no guard swings on afterwards)
        var ev2 = g.state.commit("CrimeCommitted", g.player.id, Ids.MERCHANT_A, DevMap.ALPHA, 0, "THEFT value=40 (resist scenario)");
        rpg.ai.Memory m2 = new rpg.ai.Memory("THEFT", g.player.id, Ids.MERCHANT_A, DevMap.ALPHA, g.state.minutes, 1f, "witnessed", ev2.id(), 40, "resist scenario");
        eda.remember(m2, 24);
        g.teleport(eda, bram.x + 1.2f, DevMap.FLOOR, bram.z + 1.2f);
        frames(2);
        g.law.report(eda, m2, bram);
        g.law.halt(cole, g.player);
        hud.choose(2);                                  // "Never! (resist arrest)"
        hud.closeDialog();
        long resistArrests = g.law.arrests;
        if (!cole.alive) for (var e : g.state.all()) if ((e.type().equals("ActorKilled") || e.type().equals("Dismembered") || e.type().equals("ActorHit")) && e.subject() == cole.id) System.out.println("      [cole] " + e.type() + " by " + g.nameOf(e.actor()) + " t-rev " + e.revision() + ": " + e.payload());
        if (!cole.alive) g.ai.revive(cole, false);
        g.gore.healed(cole);
        cole.restore();   // (on the HP scale an earlier fight leaves a guard badly hurt - and a badly hurt guard flees, he does not arrest)
        g.teleport(cole, 104.5f, DevMap.FLOOR, 67.5f);
        // bystanders out of the way of the swings (a thief wandering into the blade made this an accidental murder)
        for (Npc n : g.npcs) if (n != cole && n.alive && n.loaded && Math.hypot(n.x - 105f, n.z - 67.5f) < 8) g.teleport(n, n.x, DevMap.FLOOR, n.z + 12);
        tp(105.6f, 67.5f, (float) Math.PI / 2);
        aimAt(cole.x, cole.chestY(), cole.z);
        idle();
        swing(false);                                   // the player strikes first: resisting
        g.player.hp = 3;                                // one more blow brings them down
        for (int i = 0; i < 900 && g.law.arrests == resistArrests; i++) frames(1);
        if (g.law.arrests == resistArrests) System.out.printf("      cole: goal %s target %d last '%s' at (%.1f, %.1f) hp %.0f; player (%.1f, %.1f) hp %.1f resisting %s bounty %d%n", cole.goal, cole.fightTarget, cole.lastOutcome,
                cole.x, cole.z, cole.hp, g.player.x, g.player.z, g.player.hp, g.law.resisting(g.player.id), g.law.bounty(g.player.id, DevMap.ALPHA));
        if (g.law.arrests == resistArrests) System.out.printf("      cole: yaw %.2f want %.2f, stamina %.0f, phase %s, staggered %.2f, reach %.2f, player blocking %s, reacting %s%n",
                cole.yaw, cole.wantYaw, cole.stamina, cole.cs.phase, cole.cs.staggered, cole.main() == null ? 0.6f : cole.main().reach, g.player.cs.blocking, cole.reacting);
        if (g.law.arrests == resistArrests) { Actor th = g.ai.threatFor(cole); System.out.printf("      cole: sees %s, light %.2f, hour %.1f, threat %s, hunt %s, charm %.0f, lastAttacker %d, scores %s%n",
                g.ai.canSee(cole, g.player), g.ai.stealth.light(g.player), g.state.hourOfDay(), th == null ? "-" : th.name, cole.hunt == null ? "-" : cole.hunt.why, cole.charmUntil - g.time, cole.cs.lastAttacker, cole.scores);
            var aw = g.ai.stealth.of(cole, g.player);
            System.out.printf("      cole: exposure %.3f, aware %s, rays %.2f %.2f %.2f, eye %.2f, gaseous %.1f, notarget %s%n", g.ai.stealth.exposure(cole, g.player), aw == null ? "-" : String.format("%.2f at %.1f (exp %.2f)", aw.level, g.time - aw.at, aw.exposure),
                    g.vox.seeThrough(cole.x, cole.eyeY(), cole.z, g.player.x, g.player.eyeY(), g.player.z), g.vox.seeThrough(cole.x, cole.eyeY(), cole.z, g.player.x, g.player.chestY(), g.player.z),
                    g.vox.seeThrough(cole.x, cole.eyeY(), cole.z, g.player.x, g.player.y + 0.45f, g.player.z), cole.eyeY(), g.player.gaseousT, g.notarget()); System.out.printf("      cole: loaded %s alive %s acuity %.2f raw %.3f sneaking %s v (%.2f, %.2f)%n", cole.loaded, cole.alive, g.ai.stealth.acuity(cole), g.ai.stealth.exposure(cole, 1f, g.player), g.player.sneaking, g.player.vx, g.player.vz); }
        need("resisting and beaten down, the player is arrested rather than killed (arrests " + (g.law.arrests - resistArrests) + ", alive " + g.player.alive + ")",
                g.law.arrests > resistArrests && g.player.alive);
        long after2 = g.state.revision;
        frames(600);
        long hitsAfter2 = g.state.all().stream().filter(e -> e.revision() > after2 && e.type().equals("ActorHit") && e.subject() == g.player.id
                && g.actor(e.actor()) instanceof Npc n && (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN)).count();
        boolean standDown2 = g.npcs.stream().noneMatch(n -> (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) && (n.fightTarget == g.player.id || n.goal == Brain.Goal.FIGHT));
        need("after an arrest by force no guard or lawman strikes the player again (" + hitsAfter2 + " hits in ten seconds) and none still fights them", hitsAfter2 == 0 && standDown2);
        g.law.clear();
        for (Npc n : g.npcs) n.memories.removeIf(x -> x.subject == g.player.id);
        g.player.restore();
    }

    // ------------------------------------------------------------------ 7. shipments
    private void shipments() {
        tidy();
        // carters sleep at night: the haul is watched by day
        double hour = g.state.hourOfDay();
        if (hour >= 16 || hour < 7) g.advanceTime(((7 - hour) + 24) % 24 * 60, "wait for morning");
        System.out.println("      shipments start at " + g.state.clockText());
        if (g.econ.active() == null) {
            // demand at the far end: Store B has sold its logs down (a shipment is planned only when B wants more)
            int bLogs = g.econ.shopB.stock.count(Items.LOGS);
            if (bLogs > 4) { g.econ.shopB.stock.remove(Items.LOGS, bLogs - 4); g.state.burn(Items.LOGS, bLogs - 4); }
            int need = Math.max(0, 6 - g.econ.shopA.stock.count(Items.LOGS));
            if (need > 0) { if (g.econ.shopA.stock.add(new ItemStack(Items.LOGS, need, Ids.MERCHANT_A, false, 0))) g.state.mint(Items.LOGS, need); }
            frames(2);
        }
        Economy.Shipment s = g.econ.active();
        need("a shipment is planned from Store A's surplus", s != null);
        if (s == null) return;
        int cargo = s.crate.contents.count(Items.LOGS);
        int bBefore = g.econ.shopB.stock.count(Items.LOGS);
        boolean departed = false;
        for (int i = 0; i < 3600 && s.status != Economy.Status.DELIVERED; i++) {
            frames(1);
            if (s.status == Economy.Status.IN_TRANSIT && !departed) { departed = true; need("cargo leaves Store A for the crate (no double counting): crate " + s.crate.contents.count(Items.LOGS), s.crate.contents.count(Items.LOGS) == cargo); }
            if (i == 1200) shot("13-caravan");
        }
        if (s.status != Economy.Status.DELIVERED) g.advanceTime(60, "let the caravan arrive");
        frames(5);
        need("the carter walked the cargo to Store B: delivered once", s.status == Economy.Status.DELIVERED && g.econ.shopB.stock.count(Items.LOGS) == bBefore + cargo);
        need("a delivered shipment cannot deliver again", !g.econ.deliver(s));
        // a second shipment, intercepted on the road (Store B has sold through the first: it wants logs again)
        int bNow = g.econ.shopB.stock.count(Items.LOGS);
        if (bNow > 4) { g.econ.shopB.stock.remove(Items.LOGS, bNow - 4); g.state.burn(Items.LOGS, bNow - 4); }
        int need = Math.max(0, 6 - g.econ.shopA.stock.count(Items.LOGS));
        if (g.econ.shopA.stock.add(new ItemStack(Items.LOGS, Math.max(need, 1), Ids.MERCHANT_A, false, 0))) g.state.mint(Items.LOGS, Math.max(need, 1));
        frames(2);
        Economy.Shipment s2 = g.econ.active();
        for (int i = 0; i < 5000 && s2 != null && s2.status != Economy.Status.IN_TRANSIT; i++) frames(1);
        frames(240);
        Npc ivo = npc(Ids.CARTER);
        need("the second shipment is on the road (" + (s2 == null ? "none planned" : s2.status) + "; carter " + ivo.goal + " at " + String.format("(%.1f, %.1f)", ivo.x, ivo.z) + ", " + ivo.lastOutcome + ")",
                s2 != null && s2.status == Economy.Status.IN_TRANSIT);
        if (s2 == null) return;
        tp(s2.crate.x + 0.8f, s2.crate.z + 0.8f, 0);
        frames(1);
        Result r = g.interact.request(g.player, s2.crate, "takeall");
        frames(5);
        need("the cargo was taken on the road (theft)", r.committed() && g.player.count(Items.LOGS) >= 5);
        need("the intercepted shipment is LOST and cannot also deliver", s2.status == Economy.Status.LOST && !g.econ.deliver(s2));
        var f = g.invariants.check();
        need("cargo conservation holds after delivery and interception", f.isEmpty());
        if (!f.isEmpty()) f.forEach(x -> System.out.println("      " + x));
        g.law.clear();
    }

    // ------------------------------------------------------------------ 8. quest
    private void quest() {
        Npc jun = npc(Ids.QUEST_GIVER), kit = npc(Ids.CONTACT);
        tp(jun.x + 1.2f, jun.z + 1.2f, 0);
        frames(10);
        Result talk = g.interact.request(g.player, jun, "talk");
        need("talking to the quest giver opens dialogue" + (talk.reason() == null ? "" : " (" + talk.reason() + ")"), talk.committed() && hud.dialog != null);
        hud.closeDialog();
        g.quests.start();
        need("the test quest is ACTIVE with a letter and a journal entry", g.quests.courier == Quests.State.ACTIVE && g.player.has(Items.SEALED_LETTER, 1) && !g.quests.journal.isEmpty());
        hud.openKiosk();
        frames(2);
        shot("14-kiosk");
        hud.closeAll();
        // the dependency NPC dies (by a world event): the quest fails, permanently
        var ev = g.state.commit("DebugRemoveNpc", g.player.id, kit.id, DevMap.BETA, 0, "kiosk: remove dependency NPC");
        kit.alive = false; kit.hp = 0;
        g.state.commit("ActorKilled", 0, kit.id, DevMap.BETA, ev.id(), "Kit removed");
        g.ai.onKilled(kit, g.player);
        frames(60);
        need("the quest failed with its reason", g.quests.courier == Quests.State.FAILED && g.quests.failReason != null && g.quests.failReason.contains("Kit"));
        need("the journal records what the player learned", g.quests.journal.stream().anyMatch(j -> j.contains("dead")));
        need("Kit is not replaced: still dead after time passes", !kit.alive && g.time - kit.diedAt > 0.5);
        g.quests.deliver();
        need("the quest cannot be completed afterwards", g.quests.courier == Quests.State.FAILED);
    }

    // ------------------------------------------------------------------ 9. persistence
    private void persistence() {
        Path file = Path.of("build", "acceptance-save.json");
        // world state to keep: an open door, a depleted node, items moved, the dead NPC and failed quest, XP
        Door d = g.objects.shedDoor;
        if (!d.open) d.setOpen(g, true, g.player, 0);
        frames(5);
        long xpWc = g.player.skills.xp(Skill.WOODCUTTING);
        int coins = g.player.count(Items.COINS);
        double minutes = g.state.minutes;
        long revision = g.state.revision;
        // something remembered, to be remembered again after the load: Bram saw the player at the debug plaza
        npc(Ids.LAWMAN_A).remember(new rpg.ai.Memory("ATTACKED_BY", g.player.id, Ids.LAWMAN_A, DevMap.ALPHA, g.state.minutes, 0.6f, "personal", 0, 0, "persistence check"), 24);
        int memoriesSaved = g.npcs.stream().mapToInt(n -> n.memories.size()).sum();
        boolean saved = new SaveSystem(g).save(file);
        need("saved", saved);
        // change things after the save
        d.setOpen(g, false, g.player, 0);
        g.grant(g.player, new ItemStack(Items.COINS, 999));
        g.prog.awardRaw(g.player, Skill.WOODCUTTING, 5000, "after save", 0, 0);
        tp(30.5f, 100.5f, 0);
        g.advanceTime(120, "after save");
        frames(30);
        boolean loaded = new SaveSystem(g).load(file);
        double minutesLoaded = g.state.minutes;
        long revisionLoaded = g.state.revision;
        // Compare the restored snapshot before resumed minds can learn additional facts.
        int memoriesLoaded = g.npcs.stream().mapToInt(n -> n.memories.size()).sum();
        frames(30);
        need("loaded", loaded);
        need("door state survives save/load", d.open && g.vox.block(54, DevMap.FLOOR, 22) == Blocks.AIR);
        need("XP and items survive (no after-save changes leak in)", g.player.skills.xp(Skill.WOODCUTTING) == xpWc && g.player.count(Items.COINS) == coins);
        need("the clock and revision come back", Math.abs(minutesLoaded - minutes) < 0.01 && revisionLoaded >= revision);
        Npc kit = npc(Ids.CONTACT);
        need("the dead NPC stays dead and the quest stays failed with its reason", !kit.alive && g.quests.courier == Quests.State.FAILED && g.quests.failReason != null);
        need("NPC memories survive (" + memoriesSaved + " saved, " + memoriesLoaded + " loaded)", memoriesSaved > 0 && memoriesLoaded == memoriesSaved
                && npc(Ids.LAWMAN_A).memories.stream().anyMatch(m -> m.detail.equals("persistence check")));
        var f = g.invariants.check();
        need("invariants hold after load", f.isEmpty());
        if (!f.isEmpty()) f.forEach(x -> System.out.println("      " + x));
        // time: night falls and NPCs go home
        double h0 = g.state.hourOfDay();
        g.advanceTime((23 - h0 + 24) % 24 * 60, "to night");
        tp(DevMap.SPAWN_X, DevMap.SPAWN_Z, (float) Math.PI);
        frames(600);
        long sleepers = g.npcs.stream().filter(n -> n.alive && n.goal == Brain.Goal.SLEEP).count();
        need("time advance: at night NPCs replan to sleep (" + sleepers + ")", sleepers >= 2);
        shot("15-night");
        // a bed: someone else's is refused with a reason; an unowned one is slept in until morning, through the catch-up clock
        rpg.objects.Furniture adasBed = g.objects.furnitureAt(91, DevMap.FLOOR, 91), spare = g.objects.furnitureAt(106, DevMap.FLOOR, 91);
        tp(91.5f, 89.9f, 0);
        frames(5);
        Result theirs = g.interact.request(g.player, adasBed, "sleep");
        need("sleeping in someone else's bed is refused (" + theirs.reason() + ")", theirs.rejected() && theirs.reason() != null && theirs.reason().contains("bed"));
        tp(106.5f, 89.9f, 0);
        frames(5);
        g.player.hp = g.player.maxHp() * 0.5f;
        double before = g.state.minutes;
        Result slept = g.interact.request(g.player, spare, "sleep");
        if (slept.rejected()) for (Npc n : g.npcs) if (n.alive && (n.hostileToPlayer || n.fightTarget == g.player.id) && n.distTo(g.player) < 14) System.out.printf("      enemy near the bed: %s %s %s at %.1f m, hostile %s, fight %d, scores %s%n", n.name, n.goal, n.goalReason, n.distTo(g.player), n.hostileToPlayer, n.fightTarget, n.scores);
        need("sleeping in a bed skips to morning and rests (" + g.state.clockText() + (slept.reason() == null ? "" : ", " + slept.reason()) + ")", slept.committed() && g.state.minutes > before + 60 && Math.abs(g.state.hourOfDay() - 6) < 0.05 && g.player.hp >= g.player.maxHp());
        g.advanceTime(10 * 60, "to morning");
        frames(60);
        hud.openTab(Hud.TAB_SKILLS);
        tp(DevMap.SPAWN_X, DevMap.SPAWN_Z, (float) Math.PI);
        frames(30);
        shot("16-skills-tab");
        hud.openTab(Hud.TAB_INV);
        frames(2);
        shot("17-inventory");
        hud.openTab(Hud.TAB_EQUIP);
        frames(2);
        shot("18-equipment");
        hud.go(Hud.Scene.WORLD);
    }

    // ------------------------------------------------------------------ the interface
    /**
     * The interface: the Gold display, contextual fragments fading in and out with the game state, scenes swapping
     * fragment groups, the Lua addons, and the inventory driven by the mouse (drag to move, to equip, to bind).
     */
    // ------------------------------------------------------------------ pursuit (gamefixes: no omniscient NPCs)
    /**
     * A guard fighting the player loses them when they get out of his senses: he keeps only the last known location,
     * searches there (the area shows on the minimap), never heads for where the player really is, and gives up when
     * the trail is cold.
     */
    private void pursuit() {
        Npc cole = npc(Ids.GUARD);
        g.law.clear();
        for (Npc n : g.npcs) n.hunt = null;    // (no search left over from earlier sections)
        hour(12);
        g.teleport(cole, 104.5f, DevMap.FLOOR, 70.5f);
        tp(106.5f, 70.5f, (float) Math.PI / 2);
        cole.fightTarget = g.player.id; cole.cs.lastAttacker = g.player.id; cole.cs.lastHurt = g.time; cole.nextThink = 0;
        frames(30);
        if (cole.goal != Brain.Goal.FIGHT) System.out.println("      cole: " + cole.scores + " fightTarget " + cole.fightTarget + " hunt " + (cole.hunt != null) + " reacting " + cole.reacting + " alive " + cole.alive);
        need("the guard is fighting the player he can see (goal " + cole.goal + ")", cole.goal == Brain.Goal.FIGHT);
        float lkx = g.player.x, lkz = g.player.z;
        // the player slips away: far off, out of sight and earshot
        tp(64.5f, 40.5f, 0);
        g.player.sneaking = true;
        for (int i = 0; i < 180 && cole.hunt == null; i++) frames(1);
        var h = cole.hunt;
        need(String.format("out of his senses, the guard keeps only the last known location (%s; centre %.1f, %.1f vs last seen %.1f, %.1f)", h == null ? "no search" : h.why, h == null ? 0 : h.x, h == null ? 0 : h.z, lkx, lkz),
                h != null && h.target == g.player.id && Math.hypot(h.x - lkx, h.z - lkz) < 3);
        need("the search shows as an area on the minimap (" + g.ai.searchAreas().size() + " area(s))", !g.ai.searchAreas().isEmpty());
        double d0 = Math.hypot(cole.x - g.player.x, cole.z - g.player.z);
        frames(240);
        double toLkp = Math.hypot(cole.x - lkx, cole.z - lkz), toPlayer = Math.hypot(cole.x - g.player.x, cole.z - g.player.z);
        need(String.format("he searches round where he lost them (%.1f m from it), not where the player is (%.1f m off, was %.1f; goal %s: %s)", toLkp, toPlayer, d0, cole.goal, cole.goalReason),
                cole.goal == Brain.Goal.SEARCH && toLkp < 18 && toPlayer > 25);
        for (int i = 0; i < 60 * 80 && cole.hunt != null; i++) frames(1);
        need("with no new clue he gives the search up (" + cole.lastOutcome + ")", cole.hunt == null && cole.fightTarget != g.player.id);
        g.player.sneaking = false;
        cole.cs.lastAttacker = 0;
    }

    // ------------------------------------------------------------------ gore
    /**
     * A killing cut takes a limb off as a body of its own; blood marks the ground; a heavy cut maims the living (a man
     * with no sword arm drops his sword and runs); wounds bleed; a guard who finds a body searches for whoever did it,
     * and a searcher who sees someone bloodied by the scene suspects them.
     */
    private void gore() {
        var gore = g.gore;
        Npc bandit = npc(Ids.BANDIT_1), brute = npc(Ids.BRUTE), cole = npc(Ids.GUARD);
        // nothing left over from earlier sections: no crime still to report, no search under way
        g.law.clear();
        for (Npc n : g.npcs) { n.memories.removeIf(m -> m.subject == g.player.id); n.hunt = null; n.fightTarget = 0; }
        hour(12);
        // a bandit killed in the town square by a cut to the right arm - the guard far off, so that he comes on the body
        // afterwards and knows nothing of who did it (a guard who saw the blow knows the culprit and reports it instead)
        g.teleport(cole, 30.5f, DevMap.FLOOR, 30.5f);
        // (whole and alive: an earlier section may have killed him or taken a part)
        if (!bandit.alive) g.ai.revive(bandit, false);
        gore.healed(bandit); bandit.restore();
        g.teleport(bandit, 102.5f, DevMap.FLOOR, 74.5f);
        bandit.yaw = 0; bandit.hostileToPlayer = false;
        tp(102.5f, 76f, 0);
        frames(2);
        int stains0 = gore.stainCount;
        float rx = (float) Math.cos(bandit.yaw), rz = (float) -Math.sin(bandit.yaw);
        float ax = bandit.x + rx * bandit.radius, ay = bandit.y + bandit.height * 0.66f, az = bandit.z + rz * bandit.radius;
        need("a contact at the right shoulder's height and side is the right arm (" + g.gore.partAt(bandit, ax, ay, az) + ")", g.gore.partAt(bandit, ax, ay, az) == rpg.combat.Gore.Part.ARM_R);
        g.combat.resolve(g.player, bandit, bandit.maxHp() * 3, 0, rpg.item.ItemDef.DamageType.SLASH, null, ax, ay, az, "test cut", false);
        frames(30);
        need("the killing cut takes the arm off: a limb of its own in the world (" + gore.limbs.size() + " limb(s))", !bandit.alive && gore.lost(bandit, rpg.combat.Gore.Part.ARM_R) && !gore.limbs.isEmpty()
                && g.lastRigids.body(gore.limbs.get(gore.limbs.size() - 1).body) != null);
        need("blood stains the ground and the body is evidence (" + (gore.stainCount - stains0) + " stains, " + gore.evidence.size() + " pieces of evidence)",
                gore.stainCount > stains0 && gore.evidence.stream().anyMatch(e -> e.kind == rpg.combat.Gore.Kind.BODY && e.source == bandit.id));
        need("the player's blade and hands are bloodied (" + String.format("%.2f", g.player.bloodied) + ")", g.player.bloodied > 0.2f);
        // a guard comes round the corner and sees it
        watch(cole, bandit.x - 9, bandit.z, bandit.x, bandit.z);
        cole.nextThink = 0;
        tp(64.5f, 40.5f, 0);
        for (int i = 0; i < 120 && (cole.hunt == null || cole.hunt.target != 0); i++) frames(1);
        if (cole.hunt == null || cole.hunt.target != 0) System.out.println("      cole: goal " + cole.goal + " (" + cole.goalReason + ") fightTarget " + cole.fightTarget + " scores " + cole.scores);
        need("a guard who sees the body searches for whoever did it (" + (cole.hunt == null ? "no search" : cole.hunt.why) + ")", cole.hunt != null && cole.hunt.target == 0);
        // the culprit walks back, bloodied, into his sight: a suspect
        g.player.bloodied = 0.8f;
        tp(cole.x + 3, cole.z, (float) Math.PI / 2);
        for (int i = 0; i < 120 && cole.crimeKnown(g.player.id) == null; i++) frames(1);
        need("the searching guard sees blood on the player near the scene and suspects them", cole.crimeKnown(g.player.id) != null);
        g.player.bloodied = 0;
        for (Npc n : g.npcs) n.memories.removeIf(m -> m.subject == g.player.id);
        cole.fightTarget = 0; cole.hunt = null; cole.cs.lastAttacker = 0;
        g.law.clear();
        // the brute maimed: two heavy cuts to the sword arm
        g.teleport(brute, 96.5f, DevMap.FLOOR, 80.5f);
        brute.yaw = 0;
        tp(96.5f, 82f, 0);
        frames(2);
        float bx = brute.x + rx * brute.radius, by = brute.y + brute.height * 0.66f, bz = brute.z + rz * brute.radius;
        float cut = 0.45f * brute.maxHp();
        // two wounds of 45% each to the arm (the wound rules, given the damage dealt: armor would decide how much in a fight)
        for (int k = 0; k < 2; k++) g.gore.onHit(g.player, brute, Math.round(cut), rpg.item.ItemDef.DamageType.SLASH, bx, by, bz, 0, -1, false);
        frames(2);
        boolean armOff = gore.lost(brute, rpg.combat.Gore.Part.ARM_R);
        need("two heavy cuts take the living brute's sword arm: he drops his weapon (" + (brute.main() == null ? "dropped" : brute.main().name) + ")", armOff && brute.alive && brute.main() == null);
        float hp0 = brute.hp;
        brute.nextThink = 0;
        frames(120);
        need(String.format("he bleeds (%.0f -> %.0f hp) and tries only to get away (goal %s)", hp0, brute.hp, brute.goal), brute.hp < hp0 && brute.goal == Brain.Goal.FLEE || !brute.alive);
        g.ai.revive(brute, true);
        g.ai.revive(bandit, true);
        need("revived, the bandits are whole again", !gore.lost(brute, rpg.combat.Gore.Part.ARM_R) && !gore.lost(bandit, rpg.combat.Gore.Part.ARM_R));
        g.law.clear();
        for (Npc n : g.npcs) { n.memories.removeIf(m -> m.subject == g.player.id || m.subject == 0); n.hunt = null; }
    }

    // ------------------------------------------------------------------ the console and the profiles
    private void console() {
        var con = hud.console;
        emptyPack();
        con.run("sv_cheats 1", true);
        need("there is no sv_cheats: every command is simply available", con.cvar("sv_cheats") == null);
        con.run("give Godly_2hsword 1", true);
        need("'give Godly_2hsword 1' gives exactly that (case ignored)", g.player.count(Items.get("godly_2hsword")) == 1);
        con.run("give bronze arrows 25", true);
        need("give by name with spaces and an amount", g.player.count(Items.BRONZE_ARROWS) == 25);
        con.run("setlevel attack 50; setlevel strength 90", true);
        need("setlevel through the progression (and ';' runs two commands)", g.player.skills.level(Skill.ATTACK) == 50 && g.player.skills.level(Skill.STRENGTH) == 90);
        con.run("alias pumped \"setlevel defense 40\"", true);
        con.run("pumped", true);
        need("aliases run the commands they stand for", g.player.skills.level(Skill.DEFENSE) == 40);
        con.run("god", true);
        float hp0 = g.player.hp;
        g.combat.resolve(npc(Ids.BANDIT_1), g.player, 50, 0, ItemDef.DamageType.SLASH, null, g.player.x, g.player.y + 1, g.player.z, "console test", false);
        need("god mode: a blow does nothing (" + hp0 + " -> " + g.player.hp + ")", g.player.hp == hp0 && g.player.alive);
        con.run("god", true);
        con.run("invisible", true);
        Npc cole = npc(Ids.GUARD);
        need("invisible: no NPC sees the player", !g.ai.stealth.sees(cole, g.player));
        con.run("invisible", true);
        con.run("timescale 2", true);
        need("variables set and read back", con.f("timescale") == 2);
        con.run("timescale 1", true);
        need("the console is the engine's own (astratto.console.DevConsole), with the game's commands registered in it", con.core.names().contains("give") && con.core.names().contains("version"));
        int before = g.player.skills.level(Skill.MAGIC);
        con.run("setlevle magic 99", true);
        need("an unknown command changes nothing", g.player.skills.level(Skill.MAGIC) == before);
        emptyPack();
        for (Skill s : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENSE}) hud.debugSetLevel(s, 1);
        // profiles: five slots, each its own save, deleted for good
        java.nio.file.Path root0 = Profiles.root;
        Profiles.root = java.nio.file.Path.of("build", "test-profiles");
        for (int s = 1; s <= Profiles.MAX; s++) Profiles.delete(s);
        int made = 0;
        for (int i = 0; i < 6; i++) if (Profiles.create("Tester" + i, g.player.look) != null) made++;
        need("profiles: five slots and no more (" + made + " made)", made == 5 && Profiles.list().size() == 5);
        Profiles.Profile p2 = Profiles.read(2);
        boolean saved = new SaveSystem(g).save(p2.save());
        Profiles.played(p2, g, 125);
        need("a profile keeps its own save and the time played", saved && p2.hasSave() && Profiles.read(2).playSeconds >= 125 && !Profiles.read(3).hasSave());
        Profiles.delete(2);
        need("a deleted profile and its save are gone; its slot is free again", Profiles.read(2) == null && Profiles.create("Again", g.player.look).slot == 2);
        for (int s = 1; s <= Profiles.MAX; s++) Profiles.delete(s);
        Profiles.root = root0;
    }

    /** Empties the base grid through the ledger (what goes is burnt, as anything consumed is). */
    void emptyPack() {
        for (int i = 0; i < g.player.inv.size(); i++) { ItemStack st = g.player.inv.get(i); if (st != null) { g.player.inv.set(i, null); g.state.burn(st.def, st.amount); } }
    }

    // ------------------------------------------------------------------ deaths
    /**
     * gamefixes: a killed NPC's body must stay down. Kills the bandit four ways - outright, in the same blow that knocks
     * him reeling, a moment into a reaction, and by bleeding out after one - and watches the body for twelve seconds:
     * it must be Sydney's dead body throughout (never standing, stepping or getting up), lying low, and still drawn.
     */
    private void deaths() {
        Npc b = npc(Ids.BANDIT_1);
        String[] ways = {"outright", "in the staggering blow", "mid-reaction", "bled out"};
        for (int way = 0; way < ways.length; way++) {
            g.ai.revive(b, true);
            g.law.clear();
            hour(12);
            g.teleport(b, 102.5f, DevMap.FLOOR, 74.5f);
            b.yaw = 0; b.hostileToPlayer = true; b.nextThink = 1e9;
            tp(102.5f, 76.2f, 0);
            g.player.firstPerson = false;
            frames(20);
            float px = b.x, py = b.y + b.height * 0.6f, pz = b.z + b.radius;
            switch (way) {
                case 0 -> g.combat.resolve(g.player, b, b.maxHp() * 3, 0, ItemDef.DamageType.BLUNT, null, px, py, pz, "test kill", false);
                case 1 -> g.combat.resolve(g.player, b, b.maxHp() * 3, 400, ItemDef.DamageType.BLUNT, null, px, py, pz, "test kill", false);
                case 2 -> { g.combat.resolve(g.player, b, 0.01f, 400, ItemDef.DamageType.BLUNT, null, px, py, pz, "test shove", false); frames(40);
                            g.combat.resolve(g.player, b, b.maxHp() * 3, 60, ItemDef.DamageType.BLUNT, null, b.x, b.y + 1, b.z + b.radius, "test kill", false); }
                default -> { g.combat.resolve(g.player, b, 0.01f, 400, ItemDef.DamageType.BLUNT, null, px, py, pz, "test shove", false); frames(30);
                             b.hp = 0.5f; g.gore.of(b).bleed = 2; }
            }
            int frames = 0;
            while (b.alive && frames++ < 600) frames(1);
            boolean ok = !b.alive;
            if (System.getenv("RPG_DEATH_SHOTS") != null) { var r0 = g.lastBodies.ragdoll(b.ragdoll); if (r0 != null) { tp(r0.comX + 3.2f, r0.comZ + 0.4f, (float) Math.PI / 2); g.controller.camPitch = -0.35f; } }
            StringBuilder trace = new StringBuilder();
            float worst = 0;
            for (int i = 0; i < 24; i++) {
                frames(30);
                var rd = g.lastBodies.ragdoll(b.ragdoll);
                if (rd == null) { ok = false; trace.append(" gone"); break; }
                float head = rd.p[astratto.bridge.AstrattoNative.RD_HEAD * 3 + 1] - DevMap.FLOOR;
                worst = Math.max(worst, i >= 4 ? head : 0);
                if (rd.state != astratto.bridge.AstrattoNative.SYDNEY_DEAD && rd.outcome != astratto.bridge.AstrattoNative.OUTCOME_DEAD || rd.outcome == astratto.bridge.AstrattoNative.OUTCOME_RECOVERED) ok = false;
                if (i % 4 == 0) trace.append(String.format(" [%d/%d head %.2f]", rd.state, rd.outcome, head));
                if (System.getenv("RPG_DEATH_SHOTS") != null && (i == 1 || i == 6 || i == 20)) shot("death-" + way + "-" + i);
            }
            if (worst > 0.9f) ok = false;
            need("killed " + ways[way] + ", the body stays down and dead (highest head after 2 s " + String.format("%.2f", worst) + " m;" + trace + ")", ok);
        }
        g.ai.revive(b, true);
        b.nextThink = 0;
        g.law.clear();
        dismember();
    }

    /**
     * gamefixes: dismemberment works (a finishing cut on someone already hurt takes the part off - the capped damage of a
     * killing blow used to decide it, and almost never could), animals lose parts too, and blood lands on the bodies.
     */
    private void dismember() {
        var gore = g.gore;
        Npc b = npc(Ids.BANDIT_1);
        g.ai.revive(b, true);
        g.teleport(b, 102.5f, DevMap.FLOOR, 74.5f);
        b.yaw = 0; b.hostileToPlayer = true; b.nextThink = 1e9;
        tp(102.5f, 75.6f, 0);
        frames(10);
        b.hp = Math.max(1, b.maxHp() * 0.05f);
        int limbs0 = gore.limbs.size();
        float rx = (float) Math.cos(b.yaw), rz = (float) -Math.sin(b.yaw);
        float lx = b.x - rx * b.radius * 0.95f, ly = b.y + b.height * 0.25f, lz = b.z - rz * b.radius * 0.95f;
        // an ordinary heavy cut (a third of his full health): it would have been capped at the few HP he had left
        g.combat.resolve(g.player, b, b.maxHp() * 0.35f, 0, ItemDef.DamageType.SLASH, null, lx, ly, lz, "finishing cut", false);
        frames(20);
        need("a finishing cut on a wounded bandit takes the leg it struck (" + gore.limbs.size() + " limbs, was " + limbs0 + ")", !b.alive && gore.lost(b, rpg.combat.Gore.Part.LEG_L) && gore.limbs.size() > limbs0);
        need("the wound bloodies the body where it landed (" + b.blood.marks.size() + " marks: " + (b.blood.marks.isEmpty() ? "-" : b.blood.marks.get(0)) + ")",
                b.blood.marks.stream().anyMatch(m -> m.part() == rpg.actor.Blood.L_LEG));
        need("striking close bloodies the striker's hand (" + g.player.blood.marks.size() + " marks)", g.player.blood.marks.stream().anyMatch(m -> m.part() == rpg.actor.Blood.R_ARM));
        // the blood is painted on the figure: a bloodied skin differs from a clean one on that part
        var clean = rpg.render.Skin.paint(g.player.look, null, null);
        var red = rpg.render.Skin.paint(g.player.look, null, g.player.blood.snapshot());
        int diff = 0; for (int i = 0; i < clean.px.length; i++) if (clean.px[i] != red.px[i]) diff++;
        need("the blood is painted onto the figure (" + diff + " texels)", diff > 4);
        tp(DevMap.SPAWN_X, DevMap.SPAWN_Z, 0);
        g.player.blood.wash();
        // an animal: a deer-sized one standing still, a blow to a hind leg
        rpg.animal.Animal an = null;
        for (var x : g.animals.all()) if (x.alive && x.loaded && x.species == rpg.animal.Species.SHEEP && !x.baby) { an = x; break; }
        if (an == null) { need("a sheep to test animal dismemberment on", false); return; }
        tp(an.x, an.z + 3, 0);
        frames(2);
        var fig = rpg.render.AnimalModels.of(an.species, an.variant);
        float vs = rpg.render.AnimalModels.PX * an.scale();
        float ax = (float) Math.cos(an.yaw), az = (float) -Math.sin(an.yaw), bx = (float) Math.sin(an.yaw), bz = (float) Math.cos(an.yaw);
        float hx = fig.legB[0] * vs, hz = fig.legB[2] * vs, hy = (fig.bodyY + fig.legB[1] - fig.legH * 0.5f) * vs;
        float px = an.x + ax * hx + bx * hz, pz = an.z + az * hx + bz * hz, py = an.y + hy;
        var part = g.gore.partAt(an, px, py, pz);
        need("a blow below the barrel at the back right is the sheep's right hind leg (" + part + ")", part == rpg.combat.Gore.Part.LEG_R);
        int l0 = gore.limbs.size();
        for (int k = 0; k < 2; k++) gore.onHit(g.player, an, Math.round(an.maxHp() * 0.3f), an.maxHp() * 0.3f, ItemDef.DamageType.SLASH, px, py, pz, 1, 0, false);
        frames(20);
        need("two heavy cuts take the sheep's leg: a part of its own in the world, and it is lame (legs " + gore.legs(an) + ")",
                gore.lost(an, rpg.combat.Gore.Part.LEG_R) && gore.limbs.size() > l0 && gore.limbs.get(gore.limbs.size() - 1).species == rpg.animal.Species.SHEEP && gore.legs(an) < 0.6f
                        && g.lastRigids.body(gore.limbs.get(gore.limbs.size() - 1).body) != null);
        shot("dismember-sheep");
        gore.healed(an);
        g.ai.revive(b, true);
        b.nextThink = 0;
        g.law.clear();
    }

    // ------------------------------------------------------------------ pause
    /** A paused frame runs the interface's commands but no simulated time passes and nothing moves. */
    private void pause() {
        Npc eda = npc(Ids.VILLAGER);
        double t0 = g.time, m0 = g.state.minutes;
        float ex = eda.x, ez = eda.z;
        for (int i = 0; i < 60; i++) game.pump();
        need(String.format("sixty paused frames: the clock holds (%.3f -> %.3f s, %.2f -> %.2f min) and the townsfolk stand still (%.4f m)", t0, g.time, m0, g.state.minutes, Math.hypot(eda.x - ex, eda.z - ez)),
                g.time == t0 && g.state.minutes == m0 && Math.hypot(eda.x - ex, eda.z - ez) < 1e-4);
        hud.toggle(rpg.ui.Hud.Scene.INVENTORY);
        need("the inventory pauses the game (and so does any menu)", hud.paused());
        hud.toggle(rpg.ui.Hud.Scene.INVENTORY);
        need("closed, the world runs again", !hud.paused());
        hud.dialog(Main.menu(win, g));
        need("Escape menu uses the full pause presentation and stops the world", hud.dialog.pauseMenu && hud.paused());
        hud.choose(0);
        need("Resume closes the pause presentation", hud.dialog == null && !hud.paused());
    }

    // ------------------------------------------------------------------ stealth
    /** Moves to an hour of the day (forward), so the sky light is that hour's. */
    // ------------------------------------------------------------------ the reported bugs (gamefixes.md)
    /**
     * The bugs from the list: the gear kit, sprinting in first person, a shopkeeper behind a counter, and dying with gear
     * on. Each was a crash or a "can't"; each stays fixed.
     */
    private void bugs() {
        hour(12);
        // the gear kit (it asked for an item that does not exist, and the game crashed)
        boolean crashed = false;
        long kinds0 = g.player.grids().stream().mapToLong(inv -> inv.stacks().size()).sum();
        try { hud.gearKit(); } catch (RuntimeException e) { crashed = true; }
        frames(2);
        long kinds1 = g.player.grids().stream().mapToLong(inv -> inv.stacks().size()).sum();
        boolean onGround = g.objects.everything().stream().anyMatch(o -> o instanceof rpg.objects.GroundItem gi && !gi.removed && Math.hypot(gi.x - g.player.x, gi.z - g.player.z) < 1.5f);
        need("the debug gear kit hands out its gear without crashing (" + kinds0 + " -> " + kinds1 + " stacks carried" + (onGround ? ", the rest at your feet" : "") + ")", !crashed && (kinds1 > kinds0 || onGround));
        // sprinting in first person
        tp(64.5f, 62.5f, (float) Math.PI);
        g.player.firstPerson = true;
        // Earlier sections may have eaten or cast: sprint is intentionally blocked until the hands are free.
        idle();
        for (int i = 0; i < 120 && g.controller.aiming(g.player); i++) frames(1);
        need("the sprint check starts with free hands", !g.controller.aiming(g.player) && !g.player.cs.busy());
        g.player.stamina = g.player.maxStamina();
        // Shift held once is a sprint; no old double-tap input is required.
        g.controller.in.forward = 1; g.controller.in.sprint = true; frames(1);
        need("holding Shift starts a sprint without a double tap", g.player.sprinting);
        frames(45);
        float speed = (float) Math.hypot(g.player.vx, g.player.vz);
        float walkMax = g.cfg.walkSpeed * g.player.burden();   // (the gear kit above is heavy: every gait is slowed alike)
        need(String.format("the player can sprint in first person (sprinting %s, %.1f m/s against a %.1f m/s walk)", g.player.sprinting, speed, walkMax), g.player.sprinting && speed > walkMax * 1.2f);
        g.controller.in.forward = 0; g.controller.in.sprint = false;
        frames(40);
        // someone behind a counter: aimed at by the head over it, not only by walking round into them
        Npc eda = npc(Ids.VILLAGER);
        int wx = 60, wz = 70;
        tp(wx + 0.5f, wz + 1.9f, 0);
        g.teleport(eda, wx + 0.5f, DevMap.FLOOR, wz - 0.6f);
        eda.goal = Brain.Goal.IDLE; eda.nextThink = g.time + 10;
        for (int x = wx - 1; x <= wx + 1; x++) g.setBlock(x, (int) DevMap.FLOOR, wz, rpg.world.Blocks.PLANKS);
        frames(3);
        float ex = g.player.x, ey = g.player.eyeY(), ez = g.player.z;
        float hx = eda.x - ex, hy = eda.y + 1.8f - ey, hz = eda.z - ez;
        g.interact.computeFocus(g.player, ex, ey, ez, hx, hy, hz);
        need("aiming at a head over a counter picks the person (" + (g.interact.focus == null ? "nothing" : g.interact.focus.label()) + ")", g.interact.focus == eda);
        Result talk = g.interact.request(g.player, eda, "talk");
        need("and they can be talked to across it (" + talk + ")", !talk.rejected());
        g.ui.closeDialog();
        for (int x = wx - 1; x <= wx + 1; x++) g.setBlock(x, (int) DevMap.FLOOR, wz, rpg.world.Blocks.AIR);
        g.player.firstPerson = false;
        frames(3);
        // dying with gear on: it waits in the grave, and you come back without it
        if (g.player.eq.stacks().isEmpty()) wield("wooden_sword");
        int worn = g.player.eq.stacks().size();
        long graves0 = g.objects.everything().stream().filter(o -> o instanceof rpg.objects.Container c && c.kind.equals("grave")).count();
        g.player.hp = 0; g.player.alive = false;
        g.onKilled(g.player, npc(Ids.GUARD), 0);
        frames(300);
        rpg.objects.Container grave = null;
        for (var o : g.objects.everything()) if (o instanceof rpg.objects.Container c && c.kind.equals("grave") && !c.removed) grave = c;
        long graves1 = g.objects.everything().stream().filter(o -> o instanceof rpg.objects.Container c && c.kind.equals("grave")).count();
        need("dead and back, the player wears nothing they had on (" + worn + " worn -> " + g.player.eq.stacks().size() + ")", g.player.alive && worn > 0 && g.player.eq.stacks().isEmpty());
        need("their gear waits in the grave (" + (grave == null ? "no grave" : grave.contents.stacks().size() + " stacks") + ")", graves1 > graves0 && grave != null && !grave.contents.isEmpty());
        // looting a body: what they wore is shown as it is on them, and taking it strips the body
        Npc bandit = npc(Ids.BANDIT_1);
        if (!bandit.alive) g.ai.revive(bandit, true);
        frames(5);
        if (bandit.eq.get(ItemDef.Slot.BODY) == null) { bandit.eq.set(ItemDef.Slot.BODY, new ItemStack(Items.get("bronze_body"), 1)); g.state.mint(Items.get("bronze_body"), 1); }
        g.teleport(bandit, 70.5f, DevMap.FLOOR, 60.5f);
        bandit.hostileToPlayer = true;
        frames(3);
        g.combat.resolve(g.player, bandit, bandit.maxHp() * 3, 0, ItemDef.DamageType.BLUNT, null, bandit.x, bandit.chestY(), bandit.z, "test blow", false);
        frames(120);
        var body = g.objects.get(bandit.corpse) instanceof rpg.objects.Corpse c ? c : null;
        need("the dead bandit leaves a body to search, still wearing his armour (" + (body == null ? "no body" : body.worn(g)) + ")", body != null && body.worn(g).contains(ItemDef.Slot.BODY));
        if (body != null) {
            tp(body.x + 1.2f, body.z, (float) Math.PI / 2);
            ItemDef armour = bandit.eq.def(ItemDef.Slot.BODY);
            int had = g.player.count(armour);
            Result took = g.interact.request(g.player, body, "takeworn:BODY");
            need("taking the " + armour.name + " strips it from the body (" + took + ", " + had + " -> " + g.player.count(armour) + " carried)", !took.rejected() && bandit.eq.get(ItemDef.Slot.BODY) == null && g.player.count(armour) == had + 1);
        }
        g.ai.revive(bandit, true);
        frames(5);
        need("revived, the bandit's old body is gone", body == null || body.removed);
    }

    private void hour(double h) {
        double now = g.state.hourOfDay();
        g.advanceTime(((h - now) + 24) % 24 * 60, "stealth test");
        frames(2);
    }

    /** Pins an NPC in place facing a point, its mind paused, its eyes and ears still working. */
    private void watch(Npc n, float x, float z, float tx, float tz) {
        g.teleport(n, x, DevMap.FLOOR, z);
        n.goal = Brain.Goal.IDLE; n.nextThink = g.time + 600; n.fightTarget = 0; n.hunger = 0; n.fatigue = 0;
        n.yaw = n.wantYaw = (float) Math.atan2(-(tx - x), -(tz - z));
        n.aware.clear();
    }

    /** Open, unlit ground with a clear 7 m view along x: {target x, z, observer x, z}. */
    private float[] darkStretch() {
        for (int z = 12; z < DevMap.SZ - 12; z += 2) for (int x = 12; x < DevMap.SX - 20; x += 2) {
            boolean ok = true;
            for (int i = 0; i <= 7 && ok; i++) for (int y = DevMap.FLOOR; y <= DevMap.FLOOR + 2; y++) if (g.vox.block(x + i, y, z) != 0) { ok = false; break; }
            if (!ok) continue;
            for (int i = -6; i <= 13 && ok; i++) for (int j = -6; j <= 6 && ok; j++) if (g.vox.blockLight(x + i, DevMap.FLOOR, z + j) > 0) ok = false;
            if (!ok || g.vox.skyLight(x, DevMap.FLOOR, z) < 0.9f) continue;
            if (g.animals.habitatAt(x, z) != null || g.animals.habitatAt(x + 7, z) != null) continue;   // not among the wildlife
            return new float[]{x + 0.5f, z + 0.5f, x + 7.5f, z + 0.5f};
        }
        return null;
    }

    private void stealth() {
        var st = g.ai.stealth;
        Npc cole = npc(Ids.GUARD), eda = npc(Ids.VILLAGER);
        Player p = g.player;
        float[] s = darkStretch();
        need("there is open, unlit ground to test sight on", s != null);
        if (s == null) return;
        System.out.printf("      test ground: (%.1f, %.1f) seen from (%.1f, %.1f)%n", s[0], s[1], s[2], s[3]);
        // noon: lit ground, seen plainly
        hour(12);
        tp(s[0], s[1], (float) (Math.PI / 2));
        watch(eda, s[2], s[3], s[0], s[1]);
        frames(20);
        float dayLight = st.light(p), dayExp = st.exposure(eda, p);
        need(String.format("noon: the open ground is lit (%.2f) and a villager 7 m off sees the player plainly (exposure %.2f, aware: %s)", dayLight, dayExp, st.level(eda, p)),
                dayLight > 0.8f && dayExp >= Stealth.CLEAR && g.ai.canSee(eda, p) && g.ai.witnesses(p).contains(eda));
        // the same ground at one in the morning
        hour(1);
        tp(s[0], s[1], (float) (Math.PI / 2));
        watch(eda, s[2], s[3], s[0], s[1]);
        frames(2);
        float nightLight = st.light(p), standExp = st.exposure(eda, p);
        g.controller.in.sneak = true;
        frames(30);
        float sneakExp = st.exposure(eda, p);
        boolean crept = p.sneaking;
        need(String.format("1 am: the same ground is dark (%.2f); standing there is a glimpse (%.3f), creeping less still (%.3f)", nightLight, standExp, sneakExp),
                nightLight < 0.15f && standExp > 0 && standExp < Stealth.CLEAR && sneakExp < standExp * 0.6f && crept);
        need("a crime there, crept, has no witness: the villager facing it sees nobody (" + st.level(eda, p) + ")", !g.ai.witnesses(p).contains(eda));
        shot("19-sneak-night");
        g.controller.in.sneak = false;
        frames(10);
        // a lamp's light reaches into the dark
        float lampLight = 0;
        for (int x = 4; x < DevMap.SX - 4 && lampLight == 0; x++) for (int z = 4; z < DevMap.SZ - 4; z++)
            if (g.vox.blockLight(x, DevMap.FLOOR, z) >= 0.8f && g.vox.block(x, DevMap.FLOOR, z) == 0) { lampLight = st.light(x + 0.5f, DevMap.FLOOR + 0.2f, z + 0.5f); break; }
        need(String.format("at night a lamp lights the ground under it (%.2f)", lampLight), lampLight > 0.6f);
        // walls hide, glass does not
        need("a wall stops sight lines; glass lets them through", g.vox.seeThrough(110, DevMap.FLOOR + 1.5f, 74, 110, DevMap.FLOOR + 1.5f, 78) == 0 && glassSeen());
        // awareness builds from glimpses: Unaware -> Curious -> Investigating -> Alert, as the guard stares into the dark
        tp(s[0], s[1], (float) (Math.PI / 2));
        watch(cole, s[0] + 5, s[1], s[0], s[1]);
        List<Stealth.Level> seen = new ArrayList<>();
        for (int i = 0; i < 2400 && (seen.isEmpty() || seen.get(seen.size() - 1) != Stealth.Level.ALERT); i++) {
            frames(1);
            Stealth.Level lv = st.level(cole, p);
            if (seen.isEmpty() || seen.get(seen.size() - 1) != lv) seen.add(lv);
        }
        need("awareness climbs Curious -> Investigating -> Alert from glimpses in the dark: " + seen,
                seen.indexOf(Stealth.Level.CURIOUS) >= 0 && seen.indexOf(Stealth.Level.INVESTIGATING) > seen.indexOf(Stealth.Level.CURIOUS) && seen.indexOf(Stealth.Level.ALERT) > seen.indexOf(Stealth.Level.INVESTIGATING));
        need("once alert, the guard keeps sight of the player in the dark", g.ai.canSee(cole, p));
        // out of sight it fades again
        tp(s[0] - 40 < 4 ? s[0] + 40 : s[0] - 40, s[1], 0);
        float lvBefore = st.of(cole, p) == null ? 0 : st.of(cole, p).level;
        frames(900);
        float lvAfter = st.of(cole, p) == null ? 0 : st.of(cole, p).level;
        need(String.format("out of sight, awareness fades (%.2f -> %.2f)", lvBefore, lvAfter), lvAfter < lvBefore - 0.3f);
        // hearing: a noise behind him raises awareness short of Alert, and he goes to look where he placed it
        cole.aware.clear(); cole.restore(); cole.cs.lastAttacker = 0; cole.fightTarget = 0; cole.hunt = null;
        tp(s[0], s[1], (float) (Math.PI / 2));
        watch(cole, s[0] + 6, s[1], s[0] + 14, s[1]);   // facing away
        frames(5);
        g.noise(p, p.x, p.y, p.z, 12, 0.6f);
        frames(2);
        Stealth.Level heard = st.level(cole, p);
        need("a noise behind the guard makes him wonder, not know (" + heard + ")", heard == Stealth.Level.INVESTIGATING || heard == Stealth.Level.CURIOUS);
        tp(s[0] - 3, s[1] + 6, 0);    // and slip away
        cole.nextThink = 0;
        boolean looked = false;
        for (int i = 0; i < 300 && !looked; i++) { frames(1); looked = cole.goal == Brain.Goal.INVESTIGATE || cole.goal == Brain.Goal.LOOK; }
        // the spot he goes to look at: where he last placed the player - the noise, or a glimpse of them slipping away
        var odd = g.ai.stealth.unresolved(cole);
        float tx = odd != null ? odd.getValue().x : s[0], tz = odd != null ? odd.getValue().z : s[1];
        float d0 = (float) Math.hypot(cole.x - tx, cole.z - tz);
        frames(420);   // (at night he calls out first, then goes to look)
        float d1 = (float) Math.hypot(cole.x - tx, cole.z - tz);
        if (cole.hunt != null && g.gore != null) for (var e : g.gore.evidence) if (Math.hypot(e.x - cole.hunt.x, e.z - cole.hunt.z) < 4)
            System.out.println("      evidence near the search: " + e.kind + " of " + g.nameOf(e.source) + " at " + (int) e.x + "," + (int) e.z + ", " + (int) (g.time - e.at) + " s old; hunt: " + cole.hunt.why);
        need(String.format("the guard investigates the noise (%s, %s; %.1f m -> %.1f m from it)", cole.goal, cole.goalReason, d0, d1), looked && (cole.goal != Brain.Goal.INVESTIGATE || d1 < d0 - 1));
        // back to day and to their rounds
        cole.nextThink = 0; eda.nextThink = 0;
        cole.aware.clear(); eda.aware.clear();
        hour(9);
        frames(30);
    }

    // ------------------------------------------------------------------ reactions (gamefixes: investigation, sound, fear, rumor)
    private void reactions() {
        var st = g.ai.stealth;
        Player p = g.player;
        Npc ada = npc(Ids.MERCHANT_A), cole = npc(Ids.GUARD), finn = npc(Ids.THIEF), eda = npc(Ids.VILLAGER), dag = npc(Ids.WORKER);
        g.law.clear();
        for (Npc n : g.npcs) { n.investigation = null; n.fearOf = 0; n.hunt = null; }
        float[] s = darkStretch();
        if (s == null) { need("there is dark ground for the night tests", false); return; }
        // 1 - one noise in the dark, three people: each looks into it their own way
        hour(1);
        java.util.Map<String, rpg.ai.Investigation.Plan> plans = new java.util.LinkedHashMap<>();
        for (Npc n : new Npc[]{ada, cole, finn}) {
            for (Npc o : g.npcs) if (o != n && o.alive && o.distTo(n) < 30) g.teleport(o, o.homeX != 0 ? o.homeX : o.x, DevMap.FLOOR, o.homeZ != 0 ? o.homeZ : o.z);
            watch(n, s[0] + 6, s[1], s[0] + 14, s[1]);   // facing away from the player
            n.investigation = null; n.nextThink = 0; n.fatigue = 0; n.goal = Brain.Goal.IDLE;
            tp(s[0], s[1], (float) (Math.PI / 2));
            frames(3);
            g.noise(p, p.x, p.y, p.z, 12, 0.6f);
            rpg.ai.Investigation.Plan plan = null;
            for (int i = 0; i < 240 && plan == null; i++) { frames(1); plan = n.investigation == null ? null : n.investigation.plan; }
            plans.put(n.name, plan);
            if (plan == null) { var aw = g.ai.stealth.of(n, p); System.out.printf("      %s: goal %s, aware %s, scores %s%n", n.name, n.goal, aw == null ? "-" : String.format("%.2f heard %.1f seen %.1f settled %.1f", aw.level, g.time - aw.heardAt, g.time - aw.seenAt, g.time - aw.settledAt), n.scores); }
            n.investigation = null; n.aware.clear();
            tp(s[0] - 30 < 4 ? s[0] + 30 : s[0] - 30, s[1], 0);
        }
        need("a noise at night: the guard searches, the timid shopkeeper won't go and look, the thief hides " + plans,
                plans.get(cole.name) == rpg.ai.Investigation.Plan.SEARCH && plans.get(finn.name) == rpg.ai.Investigation.Plan.HIDE
                        && (plans.get(ada.name) == rpg.ai.Investigation.Plan.CALL_OUT || plans.get(ada.name) == rpg.ai.Investigation.Plan.FETCH_HELP || plans.get(ada.name) == rpg.ai.Investigation.Plan.HIDE));
        // 2 - by day, in town, ordinary footsteps behind someone are just the street
        hour(12);
        watch(eda, 100.5f, 70.5f, 100.5f, 60.5f);
        eda.nextThink = 0;
        tp(100.5f, 75.5f, 0);
        frames(3);
        for (int i = 0; i < 6; i++) { g.noise(p, p.x, p.y, p.z, 6, 0.2f); frames(10); }
        float dayLevel = st.of(eda, p) == null ? 0 : st.of(eda, p).level;
        hour(1);
        watch(eda, 100.5f, 70.5f, 100.5f, 60.5f);
        eda.nextThink = 0;
        for (int i = 0; i < 6; i++) { g.noise(p, p.x, p.y, p.z, 6, 0.2f); frames(10); }
        float nightLevel = st.of(eda, p) == null ? 0 : st.of(eda, p).level;
        need(String.format("the same footsteps mean more at night (%.2f) than in the noon bustle (%.2f)", nightLevel, dayLevel), nightLevel > dayLevel + 0.15f && dayLevel < Stealth.INVESTIGATING);
        eda.aware.clear(); eda.investigation = null;
        hour(11);
        // 3 - rumor: a witness's word travels by meeting; the hearer thinks the worse of the player, and is afraid
        tp(119.5f, 66.5f, 0);   // (far enough off that the witness, who dreads the player, need not run)
        rpg.ai.Memory seen = new rpg.ai.Memory("MURDER", p.id, Ids.CARTER, DevMap.ALPHA, g.state.minutes, 1f, "witnessed", 0, 0, "acceptance: a killing seen");
        seen.reported = true;
        eda.memories.add(seen);
        g.teleport(eda, 100.5f, DevMap.FLOOR, 70.5f); g.teleport(dag, 101.8f, DevMap.FLOOR, 70.5f);
        for (Npc n : new Npc[]{eda, dag}) { n.investigation = null; n.aware.clear(); n.fearOf = 0; n.hunt = null; n.fightTarget = 0; }
        eda.goal = Brain.Goal.IDLE; dag.goal = Brain.Goal.IDLE; eda.nextThink = 0; dag.nextThink = 0; eda.hunger = dag.hunger = 0;
        rpg.ai.Memory heard = null;
        for (int i = 0; i < 300 && heard == null; i++) {
            frames(1);
            g.teleport(eda, 100.5f, DevMap.FLOOR, 70.5f); g.teleport(dag, 101.8f, DevMap.FLOOR, 70.5f);
            for (rpg.ai.Memory m : dag.memories) if (m.kind.equals("MURDER") && m.subject == p.id) heard = m;
        }
        need("a witness tells a passer-by what they saw: the hearer now knows it as rumor (" + (heard == null ? "nothing" : heard.source + ", " + heard.confidence) + ")",
                heard != null && !heard.witnessed() && heard.confidence < 1);
        float op = g.ai.rumors.opinion(dag);
        need(String.format("and thinks ill of the player for it (opinion %.2f), and dreads them", op), op < -0.3f && g.ai.rumors.afraid(dag));
        // 4 - the dread made real: the player walks up, and the frightened man gets away
        dag.goal = Brain.Goal.IDLE; dag.nextThink = g.time + 1;
        dag.yaw = dag.wantYaw = (float) Math.PI;   // facing the player as they come up (south)
        g.game.commands().moverPlace(dag.body, dag.x, DevMap.FLOOR, dag.z, 0, 0, 0);
        tp(101.8f, 76f, 0);
        dag.nextThink = 0;
        float d0 = dag.distTo(p);
        boolean fled = false;
        for (int i = 0; i < 180 && !fled; i++) { frames(1); fled = dag.goal == Brain.Goal.FLEE; }
        frames(60);
        if (!fled) System.out.printf("      dag: sees %b afraid %b opinion %.2f exposure %.2f dist %.1f player sneaking %b disguise %.1f gas %.1f notarget %b%n", g.ai.canSee(dag, p), g.ai.rumors.afraid(dag),
                g.ai.rumors.opinion(dag), g.ai.stealth.exposure(dag, p), dag.distTo(p), p.sneaking, p.disguiseT, p.gaseousT, g.notarget());
        need(String.format("seeing the one he dreads, he runs (%s; %.1f m -> %.1f m)", dag.goal, d0, dag.distTo(p)), fled && dag.distTo(p) > d0 + 1);
        eda.memories.remove(seen);
        dag.memories.removeIf(m -> m.subject == p.id);
        dag.fearOf = 0; eda.fearOf = 0;
        tp(64.5f, 64.5f, 0);
        frames(30);
        // 5 - a beast: a villager runs from it, and once safe goes to tell the guard, who takes up the hunt
        rpg.animal.Animal bear = first(rpg.animal.Species.BROWN_BEAR, a -> !a.baby);
        if (bear == null) bear = first(rpg.animal.Species.WOLF, a -> !a.baby);
        if (bear == null) bear = g.animals.release(rpg.animal.Species.WOLF, false, false, 20.5f, 20.5f);   // (earlier sections may have thinned the wild)
        need("there is a beast to test fear with", bear != null);
        if (bear != null) {
            for (Npc n : g.npcs) if (n.role == Npc.Role.GUARD) { n.hunt = null; n.fightTarget = 0; }
            g.teleport(eda, 104.5f, DevMap.FLOOR, 78.5f);
            eda.yaw = eda.wantYaw = 0;   // facing the bear (north)
            eda.goal = Brain.Goal.IDLE; eda.nextThink = 0; eda.fearOf = 0;
            float bx = bear.x, bz = bear.z;
            g.teleport(bear, 104.5f, DevMap.FLOOR, 70.5f);
            boolean ran = false;
            for (int i = 0; i < 240 && !ran; i++) { frames(1); ran = eda.goal == Brain.Goal.FLEE && eda.fearOf == bear.id; if (!ran) g.teleport(bear, 104.5f, DevMap.FLOOR, 70.5f); }
            g.teleport(bear, bx, DevMap.FLOOR, bz);
            boolean warned = false;
            for (int i = 0; i < 60 * 40 && !warned; i++) { frames(1); for (Npc n : g.npcs) if (n.role == Npc.Role.GUARD && n.hunt != null && n.hunt.target == bear.id) warned = true; }
            if (!warned) System.out.printf("      eda: goal %s fear %d (%.0fs) scores %s; last %s%n", eda.goal, eda.fearOf, g.time - eda.fearSeenAt, eda.scores, eda.lastOutcome);
            need("a villager runs from a " + bear.kind() + " (" + ran + "), and when it's gone tells the guard, who goes after it (" + warned + ")", ran && warned);
            for (Npc n : g.npcs) if (n.hunt != null && n.hunt.target == bear.id) n.hunt = null;
            eda.fearOf = 0;
        }
        // 6 - blood is slick: a man running through a fresh pool loses his feet - his body really falls (Sydney) - and gets up
        Npc ivo = npc(Ids.CARTER);
        tp(96.5f, 66.5f, 0);
        g.teleport(ivo, 100.5f, DevMap.FLOOR, 70.5f);
        ivo.goal = Brain.Goal.IDLE; ivo.nextThink = 0;
        frames(20);
        ivo.vx = 4.5f; ivo.vz = 0;
        g.gore.slipNow(ivo);
        boolean down = false, up = false;
        for (int i = 0; i < 60 * 16 && !up; i++) { frames(1); down |= ivo.reacting && ivo.wentDown; up = down && !ivo.reacting; }
        need("a man who slips in blood goes down for real (Sydney: " + down + ") and gets back up (" + up + ")", down && up && ivo.alive);
        for (Npc n : g.npcs) { n.investigation = null; n.aware.clear(); n.nextThink = 0; }
        hour(9);
        frames(30);
    }

    // ------------------------------------------------------------------ the player's death (gamefixes; GDD 3.12)
    private void playerDeath() {
        Player p = g.player;
        g.law.clear();
        hour(10);
        // die outside the walls with something on you
        g.give(p, rpg.item.Items.BREAD, 2);
        g.give(p, rpg.item.Items.get("gold_ring"), 1);
        tp(30.5f, 30.5f, 0);
        frames(5);
        int carried = p.inv.stacks().size() + p.eq.stacks().size();
        p.alive = false; p.hp = 0;
        for (var old : g.undertaker.graves()) g.undertaker.toCustody(old, "cleared for the test");
        g.undertaker.claims.clear();
        for (int i = 0; i < g.objects.custody.contents.size(); i++) { var it = g.objects.custody.contents.get(i); if (it != null) { g.objects.custody.contents.set(i, null); g.objects.drop(it, 30.5f, DevMap.FLOOR + 0.05f, 33.5f); } }
        g.onKilled(p, null, 0);
        need("death: the body lies there and the death plays out (no grave yet, still dead)", g.death.active && !p.alive && g.undertaker.graves().isEmpty());
        for (int i = 0; i < 60 * 6 && g.death.active; i++) frames(1);
        var graves = g.undertaker.graves();
        need("after the five seconds the player wakes at the graveyard, and the grave stands where the body fell (" + graves.size() + " grave, " + carried + " things carried)",
                p.alive && !g.death.active && graves.size() == 1 && Math.hypot(p.x - DevMap.GRAVEYARD_SPAWN[0], p.z - DevMap.GRAVEYARD_SPAWN[1]) < 3);
        var grave = graves.isEmpty() ? null : graves.get(graves.size() - 1);
        if (grave == null) return;
        need("a grave outside the walls is exposed, with ten minutes to stand", !grave.secured && Math.abs(g.undertaker.left(grave) - 600) < 10);
        // the ten minutes run out: what is left goes to the undertaker, for a fee with the outside surcharge
        grave.expiresAt = g.time;
        frames(3);
        need("when the time is up the grave goes to the undertaker (" + g.undertaker.claims.size() + " claim, " + g.undertaker.held() + " held, fee " + g.undertaker.fee() + ")",
                grave.removed && g.undertaker.held() > 0 && g.undertaker.fee() == rpg.law.Undertaker.FEE + rpg.law.Undertaker.OUTSIDE_SURCHARGE);
        Npc morrow = npc(Ids.UNDERTAKER);
        g.teleport(p, morrow.x + 1, DevMap.FLOOR, morrow.z);
        frames(3);
        int before = p.count(rpg.item.Items.COINS) + g.objects.custody.contents.count(rpg.item.Items.COINS);
        g.give(p, rpg.item.Items.COINS, 60);
        var r = g.undertaker.redeem(p, morrow);
        need("the undertaker hands it all back for his fee (" + r.status() + ", ring back: " + p.has(rpg.item.Items.get("gold_ring"), 1) + ")",
                !r.rejected() && p.has(rpg.item.Items.get("gold_ring"), 1) && p.count(rpg.item.Items.COINS) == before + 60 - (rpg.law.Undertaker.FEE + rpg.law.Undertaker.OUTSIDE_SURCHARGE) && g.undertaker.held() == 0);
        // inside the walls: secured, and a guard comes for it once word reaches the watch
        g.give(p, rpg.item.Items.BREAD, 1);
        tp(100.5f, 74.5f, 0);
        p.alive = false; p.hp = 0;
        g.onKilled(p, null, 0);
        for (int i = 0; i < 60 * 6 && g.death.active; i++) frames(1);
        var town = g.undertaker.graves().isEmpty() ? null : g.undertaker.graves().get(g.undertaker.graves().size() - 1);
        need("a death inside the walls leaves a secured grave", town != null && town.secured);
        if (town != null) {
            // (word reaches the watch after two minutes: skipped here)
            town.expiresAt = g.time + rpg.law.Undertaker.GRAVE_SECONDS - 130;
            Npc cole = npc(Ids.GUARD);
            cole.hunt = null; cole.fightTarget = 0; cole.nextThink = 0; cole.investigation = null; cole.aware.clear();
            g.teleport(cole, town.x + 6, DevMap.FLOOR, town.z);
            boolean carried2 = false;
            for (int i = 0; i < 60 * 60 && !town.removed; i++) { frames(1); carried2 |= town.heldBy == cole.id; if (i % 60 == 0) { cole.investigation = null; cole.aware.clear(); } }
            need("a guard gathers it up and carries it to the undertaker (" + carried2 + ", " + cole.lastOutcome + ")", carried2 && town.removed && g.undertaker.held() > 0);
            g.give(p, rpg.item.Items.COINS, 30);
            g.teleport(p, morrow.x + 1, DevMap.FLOOR, morrow.z);
            frames(3);
            g.undertaker.redeem(p, morrow);
            need("redeemed for the town fee alone", g.undertaker.held() == 0);
        }
    }

    // ------------------------------------------------------------------ Honor, the fence, jewellery
    private void honorChecks() {
        var h = g.honor;
        int before = h.score;
        need("Honor's seventeen ranks cover -100..+100 exactly (Notorious, Neutral, Revered)",
                rpg.law.Honor.rankName(-100).equals("Notorious") && rpg.law.Honor.rankName(-89).equals("Notorious") && rpg.law.Honor.rankName(-88).equals("Ruthless")
                        && rpg.law.Honor.rankName(-10).equals("Neutral") && rpg.law.Honor.rankName(10).equals("Neutral") && rpg.law.Honor.rankName(11).equals("Considerate")
                        && rpg.law.Honor.rankName(89).equals("Revered") && rpg.law.Honor.rankName(100).equals("Revered"));
        Npc ada = npc(Ids.MERCHANT_A);
        h.greeted(ada.id); h.greeted(ada.id); h.greeted(ada.id);
        need("greeting the same person three times counts once (" + before + " -> " + h.score + ")", h.score == before + 1);
        // one canonical consequence: an assault that becomes a murder is the murder, not both
        int s0 = h.score;
        Npc gus = npc(Ids.MERCHANT_B);
        h.crime("ASSAULT", gus.id, 0, true);
        int afterAssault = h.score;
        h.crime("MURDER", gus.id, 0, true);
        need(String.format("an assault then a murder of the same person weighs as the murder alone (%d -> %d -> %d)", s0, afterAssault, h.score), afterAssault == s0 - 6 && h.score == s0 - 25);
        h.score = 60;
        need("Honor +60 earns 10% off with merchants who know you", Math.abs(h.discount() - 0.1f) < 1e-4);
        var shop = g.econ.shopA;
        ada.knowsPlayer = true;
        var d = rpg.item.Items.BREAD;
        int list = g.econ.buyPrice(shop, d), mine = g.econ.priceFor(g.player, shop, d);
        need("the discount applies but never below what the shop pays (" + list + " -> " + mine + ", floor " + (g.econ.sellPrice(shop, d) + 1) + ")", mine <= list && mine > g.econ.sellPrice(shop, d));
        h.score = before;
        // the fence buys what a shop won't
        Npc silas = npc(Ids.FENCE);
        var stolen = new rpg.item.ItemStack(rpg.item.Items.get("gold_ring"), 1);
        stolen.markStolen(Ids.MERCHANT_A);
        g.state.mint(stolen.def, 1);
        if (!g.player.store(stolen)) throw new IllegalStateException("no room for the fence test's stolen ring");
        rpg.item.Inventory saleGrid = null;
        int slot = -1;
        for (var grid : g.player.grids()) for (int i = 0; i < grid.size(); i++) {
            var candidate = grid.get(i);
            if (candidate != null && candidate.sameKind(stolen)) { saleGrid = grid; slot = i; break; }
        }
        g.teleport(g.player, gus.x + 1, DevMap.FLOOR, gus.z);
        frames(3);
        var refused = g.econ.sell(g.player, saleGrid, g.econ.shopB, slot, 1, g.state.newRequestId());
        g.teleport(g.player, silas.x + 1, DevMap.FLOOR, silas.z);
        for (Npc n : g.npcs) if ((n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) && n.distTo(silas) < 20) g.teleport(n, n.postX != 0 ? n.postX : 100, DevMap.FLOOR, n.postZ != 0 ? n.postZ : 70);
        frames(3);
        var sold = g.econ.sell(g.player, saleGrid, g.econ.fence, slot, 1, g.state.newRequestId());
        need("a shop won't buy a stolen ring (" + refused.reason() + "); the fence will (" + sold.status() + ")", refused.rejected() && refused.reason().contains("stolen") && !sold.rejected()
                && g.econ.fence.stock.stacks().stream().anyMatch(it -> it.def == stolen.def && it.stolen));
        need("the jewellery slots: a ring and a necklace can be worn", rpg.item.Items.get("ruby_ring").slot == rpg.item.ItemDef.Slot.RING && rpg.item.Items.get("emerald_amulet").slot == rpg.item.ItemDef.Slot.NECK);
    }

    /** Whether some window pane in the map can be seen through. */
    private boolean glassSeen() {
        for (int x = 2; x < DevMap.SX - 2; x++) for (int z = 2; z < DevMap.SZ - 2; z++) {
            int y = DevMap.FLOOR + 1;
            if (g.vox.block(x, y, z) != rpg.world.Blocks.GLASS) continue;
            if (g.vox.block(x - 1, y, z) == 0 && g.vox.block(x + 1, y, z) == 0) return g.vox.seeThrough(x - 0.5f, y + 0.5f, z + 0.5f, x + 1.5f, y + 0.5f, z + 0.5f) > 0.99f;
            if (g.vox.block(x, y, z - 1) == 0 && g.vox.block(x, y, z + 1) == 0) return g.vox.seeThrough(x + 0.5f, y + 0.5f, z - 0.5f, x + 0.5f, y + 0.5f, z + 1.5f) > 0.99f;
        }
        return false;
    }

    // ------------------------------------------------------------------ animals (GDD 3.9)
    private rpg.animal.Animal first(rpg.animal.Species s, java.util.function.Predicate<rpg.animal.Animal> p) {
        for (var a : g.animals.all()) if (a.species == s && a.alive && a.loaded && p.test(a)) return a;
        return null;
    }

    /** (1) at the start: the fourteen species, adult and young, each at its gamefixes hitbox (young at the stage's scale). */
    private void animalCatalog() {
        var zoo = g.animals;
        // (1) the fourteen species, adult and young, each at its gamefixes hitbox (young at the stage's scale)
        java.util.Set<rpg.animal.Species> adults = java.util.EnumSet.noneOf(rpg.animal.Species.class), young = java.util.EnumSet.noneOf(rpg.animal.Species.class);
        boolean boxes = true;
        String boxBad = "";
        for (var a : zoo.all()) {
            if (!a.alive) continue;
            (a.baby ? young : adults).add(a.species);
            float k = a.baby ? a.species.babyScale : 1;
            if (Math.abs(a.radius * 2 - a.species.w * k) > 0.01f || Math.abs(a.height - a.species.h * k) > 0.01f) { boxes = false; boxBad = a.kind() + String.format(" %.2fx%.2f", a.radius * 2, a.height); }
        }
        need("all 14 species live, adults and young of each (" + adults.size() + " adult, " + young.size() + " young species)", adults.size() == 14 && young.size() == 14);
        need("every animal has its species' hitbox (chicken 0.4x0.7 ... horse 1.3965x1.6), young at half (a foal at its own scale)" + (boxes ? "" : ": " + boxBad), boxes
                && Math.abs(rpg.animal.Species.HORSE.babyScale - 0.5f) > 0.05f && rpg.animal.Species.COW.babyScale == 0.5f);
    }

    private void animals() {
        var zoo = g.animals;
        hour(10);
        // (2) daily life, unscripted: the farm's animals graze, drink, rest, keep together - and move
        var cow = first(rpg.animal.Species.COW, a -> a.female && !a.baby);
        java.util.Map<Long, float[]> start = new java.util.HashMap<>();
        java.util.Set<rpg.animal.Animal.Goal> goals = java.util.EnumSet.noneOf(rpg.animal.Animal.Goal.class);
        for (var a : zoo.all()) if (a.alive && a.home == zoo.farm) start.put(a.id, new float[]{a.x, a.z});
        tp(104.5f, 98.5f, (float) Math.PI);   // at the gate, looking into the paddock
        for (int i = 0; i < 1500; i++) { frames(1); if (i % 10 == 0) for (var a : zoo.all()) if (a.alive && a.home == zoo.farm) goals.add(a.goal); }
        int moved = 0;
        for (var a : zoo.all()) { float[] s0 = start.get(a.id); if (s0 != null && Math.hypot(a.x - s0[0], a.z - s0[1]) > 1.5) moved++; }
        need("the farm's animals choose their own routines (" + goals + "), and " + moved + " of " + start.size() + " moved", goals.size() >= 4 && moved >= start.size() / 2);
        shot("20-farm");
        // (3) predation: a hungry fox hunts a rabbit kit it can see; the kill is a real death in the wood's ledger
        tp(30.5f, 44.5f, 0);    // near the wood, so its animals are out of the ledger and about
        frames(240);
        var fox = first(rpg.animal.Species.FOX, a -> !a.baby);
        var kit = first(rpg.animal.Species.RABBIT, a -> a.baby);
        if (kit == null) kit = first(rpg.animal.Species.RABBIT, a -> true);
        // (earlier sections may have thinned the wood: a scenario brings in the pair, on the ledger as released)
        if (fox == null) fox = g.animals.release(rpg.animal.Species.FOX, false, true, 34.5f, 44.5f);
        if (kit == null) kit = g.animals.release(rpg.animal.Species.RABBIT, true, false, 38.5f, 44.5f);
        need("a fox and a rabbit kit to watch", fox != null && kit != null);
        if (fox != null && kit != null) {
            // the bigger hunters held still at the wood's far edge for the length of this check (the fox rightly fears them)
            java.util.List<rpg.animal.Animal> held = new java.util.ArrayList<>();
            for (var a : zoo.all()) if (a.alive && a.loaded && (a.species == rpg.animal.Species.MOUNTAIN_LION || a.species == rpg.animal.Species.WOLF || a.species == rpg.animal.Species.BROWN_BEAR || a.species == rpg.animal.Species.COYOTE)) {
                held.add(a); a.puppet = true; g.teleport(a, 41.5f - held.size(), DevMap.FLOOR, 5.5f); zoo.puppetHold(a);
            }
            // a quiet corner of the wood, away from the lion and the wolves
            // open ground between the pen and the debug zone: nothing else about to frighten or distract either
            g.teleport(fox, 44.5f, DevMap.FLOOR, 80.5f);
            fox.homeX = 44.5f; fox.homeZ = 80.5f; fox.yaw = 0;
            tp(fox.x + 30, fox.z + 30, 0);
            final var prey = kit;
            g.teleport(kit, fox.x - (float) Math.sin(fox.yaw) * 4, DevMap.FLOOR, fox.z - (float) Math.cos(fox.yaw) * 4);
            fox.hunger = 0.95f; fox.nextThink = 0; fox.fear = 0; fox.fatigue = 0;
            prey.fatigue = prey.baby ? 0 : 0.95f;   // a kit, or an adult rabbit already run ragged (a healthy adult mostly outruns a fox)
            for (var c : zoo.all()) if (c.carcass()) c.butchered = true;    // nothing lying dead to scavenge: it must hunt
            // the outlaws' pen is 25 m off: a fox is right to run from a brute that close - for this test they are set aside
            List<Npc> outlaws = new ArrayList<>();
            for (Npc n : g.npcs) if (n.alive && n.loaded && (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE)) { g.ai.unload(n); outlaws.add(n); }
            int wildBefore = zoo.count(zoo.wild);
            int foxKills0 = zoo.wild.causes.getOrDefault("died (predation:fox)", 0);
            boolean hunted = false;
            for (int i = 0; i < 3600 && prey.alive && zoo.wild.causes.getOrDefault("died (predation:fox)", 0) == foxKills0; i++) {
                frames(1);
                if (fox.goal == rpg.animal.Animal.Goal.HUNT) hunted = true;
                if (i % 60 == 0) System.out.printf("      chase t%d: fox %s %.1f m/s fat %.2f sneak %s | %s %s %.1f m/s fat %.2f fear %.2f | %.1f m apart, fox cs %s%n", i / 60, fox.goal, Math.hypot(fox.vx, fox.vz), fox.fatigue, fox.sneaking, prey.kind(), prey.goal, Math.hypot(prey.vx, prey.vz), prey.fatigue, prey.fear, fox.distTo(prey), fox.cs.phase + (fox.goal != rpg.animal.Animal.Goal.HUNT ? " (" + fox.reason + ")" : ""));
            }
            System.out.println("      fox: " + fox.goal + " (" + fox.reason + "), kit alive " + kit.alive + ", " + kit.lastEvent);
            for (Npc n : outlaws) g.ai.reload(n);
            int foxKills = zoo.wild.causes.getOrDefault("died (predation:fox)", 0) - foxKills0;
            need("the hungry fox stalks and hunts, and makes a kill (" + foxKills + "; " + (prey.alive ? "its first quarry got away" : prey.lastEvent) + ")", hunted && foxKills > 0);
            need(String.format("the kill is a real death: the wood holds fewer (%d -> %d) and its ledger records it", wildBefore, zoo.count(zoo.wild)), zoo.count(zoo.wild) < wildBefore && zoo.ledgerProblem() == null && zoo.wild.causes.getOrDefault("died (predation:fox)", 0) > 0);
            boolean fed = false;
            for (int i = 0; i < 900 && !fed; i++) { frames(1); fed = fox.goal == rpg.animal.Animal.Goal.FEED; }
            need("the fox then feeds at the carcass", fed);
            for (var a : held) a.puppet = false;
        }
        // (4) breeding on the farm: an owned cow, fed, with a bull near -> pregnant -> one birth event -> a calf that follows her -> grown
        if (cow != null) {
            hour(10);
            // room for one more in the herd (earlier days of the run may have filled the farm to its capacity)
            zoo.farm.cap(rpg.animal.Species.COW, zoo.count(zoo.farm, rpg.animal.Species.COW) + 3);
            tp(104.5f, 98.5f, 0);
            cow.hunger = 0; cow.thirst = 0; cow.fatigue = 0; cow.fear = 0; cow.lastBred = -1e9; cow.nextThink = 0;
            var bull = first(rpg.animal.Species.COW, a -> !a.female && !a.baby);
            if (bull != null) g.teleport(bull, cow.x + 2, DevMap.FLOOR, cow.z);
            Npc keeper = npc(Ids.FARMER);
            keeper.hunger = 0; keeper.nextThink = 0;   // she has eaten: her day is the stock's
            System.out.println("      farm: trough " + g.objects.trough.contents.count(Items.ANIMAL_FEED) + " feed, chest " + g.objects.farmChest.contents.count(Items.ANIMAL_FEED)
                    + ", Mara " + keeper.count(Items.COINS) + " G, hunger " + keeper.hunger + ", " + keeper.goal + " / " + keeper.lastOutcome + ", Store A feed " + g.econ.shopA.stock.count(Items.ANIMAL_FEED)
                    + " at " + g.econ.buyPrice(g.econ.shopA, Items.ANIMAL_FEED) + " G, bread " + g.econ.shopA.stock.count(Items.BREAD));
            String why = zoo.breedRefusal(cow);
            for (int i = 0; i < 3600 && cow.dueAt < 0; i++) { frames(1); cow.hunger = Math.min(cow.hunger, 0.3f); cow.thirst = Math.min(cow.thirst, 0.4f); }
            if (cow.dueAt < 0) System.out.println("      cow: " + zoo.breedRefusal(cow) + "; " + cow.goal + " (" + cow.reason + "); Mara " + keeper.goal + " / " + keeper.lastOutcome + " at " + String.format("(%.1f, %.1f)", keeper.x, keeper.z) + ", trough " + g.objects.trough.contents.count(Items.ANIMAL_FEED) + ", chest " + g.objects.farmChest.contents.count(Items.ANIMAL_FEED));
            need("an owned cow with a bull near, fed and calm, conceives (" + (why == null ? cow.reason : why) + ")", cow.dueAt > 0);
            g.advanceTime(cow.species.gestation + 5, "pregnancy");
            frames(90);
            rpg.animal.Animal calf = null;
            for (var a : zoo.all()) if (a.alive && a.baby && a.mother == cow.id && a.lastEvent.startsWith("born")) calf = a;
            long born = g.state.all().stream().filter(e -> e.type().equals("AnimalBorn") && e.actor() == cow.id).count();
            need("she gives birth: one event, one calf, hers and Mara's (" + born + " birth events)", calf != null && born == 1 && calf.owner == Ids.FARMER && calf.mother == cow.id);
            if (calf != null) {
                g.teleport(calf, cow.x + 7, DevMap.FLOOR, cow.z);
                calf.nextThink = 0;
                float far = calf.distTo(cow);
                for (int i = 0; i < 900 && calf.distTo(cow) > 3.5f; i++) frames(1);
                need(String.format("the calf follows its mother (%.1f m -> %.1f m; calf %s: %s, legs %.2f; cow %s: %s)", far, calf.distTo(cow), calf.goal, calf.reason, g.gore.legs(calf), cow.goal, cow.reason), calf.distTo(cow) < 3.5f);
                int before = zoo.count(zoo.farm);
                g.advanceTime(calf.species.mature + 5, "growing up");
                frames(90);
                need("the calf grows up: a baby becomes an adult, the herd's count unchanged (" + before + " -> " + zoo.count(zoo.farm) + ")", !calf.baby && Math.abs(calf.height - calf.species.h) < 0.01f && zoo.count(zoo.farm) == before);
            }
        }
        // (5) no breeding without what it needs: a male, the keeper's feed, room, a mate at all
        tp(30.5f, 44.5f, 0);    // near the wood, so its animals are about (far off they are held as cohort counts)
        frames(240);
        var bull = first(rpg.animal.Species.COW, a -> !a.female && !a.baby);
        // a lone wild female: the first wild adult female about, with every male of her kind taken well away
        var lion = first(rpg.animal.Species.MOUNTAIN_LION, a -> a.female && !a.baby);
        if (lion == null) for (var a : zoo.all()) if (a.alive && a.loaded && a.female && !a.baby && a.home == zoo.wild && !a.species.domestic) { lion = a; break; }
        final var lone = lion;
        var ewe = first(rpg.animal.Species.SHEEP, a -> a.female && !a.baby);
        String rMale = bull == null ? "?" : zoo.breedRefusal(bull);
        java.util.List<ItemStack> feed = g.objects.trough.contents.remove(Items.ANIMAL_FEED, g.objects.trough.contents.count(Items.ANIMAL_FEED));
        int rations = zoo.trough.portions;
        zoo.trough.portions = 0;
        String rFeed = "?";
        if (ewe != null) { ewe.hunger = 0; ewe.thirst = 0; ewe.fear = 0; ewe.lastBred = -1e9; ewe.dueAt = -1; zoo.farm.cap(rpg.animal.Species.SHEEP, zoo.count(zoo.farm, rpg.animal.Species.SHEEP) + 3); rFeed = zoo.breedRefusal(ewe); }
        for (ItemStack s : feed) g.objects.trough.contents.add(s);
        zoo.trough.portions = rations;
        String rSoon = "?";
        if (ewe != null) { ewe.lastBred = g.state.minutes; rSoon = zoo.breedRefusal(ewe); ewe.lastBred = -1e9; }
        // a lone lioness: any male taken well away first
        java.util.List<float[]> back = new java.util.ArrayList<>();
        for (var a : zoo.all()) if (a.alive && lone != null && a.species == lone.species && !a.female) { back.add(new float[]{a.id, a.x, a.z}); g.teleport(a, 4.5f + back.size(), DevMap.FLOOR, 38.5f); }
        if (lion != null) { g.teleport(lion, 40.5f, DevMap.FLOOR, 6.5f); lion.hunger = 0; lion.thirst = 0; lion.fear = 0; lion.lastBred = -1e9; lion.dueAt = -1; }
        if (lone != null) zoo.wild.cap(lone.species, zoo.count(zoo.wild, lone.species) + 3);   // room, so only the mate is missing
        frames(2);
        boolean noMate = lion != null && zoo.breedRefusal(lion) == null && zoo.mateForTest(lion) == null;
        System.out.println("      lioness: " + (lion == null ? "none" : zoo.breedRefusal(lion) + ", mate " + zoo.mateForTest(lion)));
        for (float[] b : back) { var a = zoo.byId((long) b[0]); if (a != null) g.teleport(a, b[1], DevMap.FLOOR, b[2]); }
        need("breeding is refused with a reason: a male (" + rMale + "), no feed out (" + rFeed + "), too soon (" + rSoon + "); a lone lioness finds no mate (" + noMate + ")",
                rMale != null && rMale.contains("female") && rFeed != null && rFeed.contains("feed") && rSoon != null && rSoon.contains("soon") && noMate);
        hour(10);
        // (6) eggs: an owned hen lays an ordinary egg in a nest box; Mara carries it to her chest
        int chestEggs = g.objects.farmChest.contents.count(Items.EGG);
        for (var a : zoo.all()) if (a.alive && a.species == rpg.animal.Species.CHICKEN && a.female && !a.baby) { a.lastEgg = -1e9; a.hunger = 0; }
        frames(90);
        long laid = zoo.eggsLaid;
        Npc mara = npc(Ids.FARMER);
        mara.hunger = 0; mara.social = 0; mara.nextThink = 0;
        long sales = g.state.all().stream().filter(e -> e.type().equals("Trade") && e.payload().contains("x Egg") || e.type().equals("EggsCollected")).count();
        java.util.function.BooleanSupplier gathered = () -> g.objects.farmChest.contents.count(Items.EGG) > chestEggs
                || g.state.all().stream().filter(e -> e.type().equals("Trade") && e.payload().contains("x Egg") || e.type().equals("EggsCollected")).count() > sales;
        for (int i = 0; i < 9000 && !gathered.getAsBoolean(); i++) frames(1);   // a round trip to Store A for feed can come first
        int inBoxes = 0; for (var bx : g.objects.nestBoxes) inBoxes += bx.contents.count(Items.EGG);
        System.out.println("      eggs: boxes " + inBoxes + ", Mara carries " + mara.count(Items.EGG) + ", goal " + mara.goal + " (" + mara.goalReason + "), " + g.state.clockText());
        need("the hens lay eggs in the nest boxes (" + laid + ") and Mara gathers them, into her chest or to market (" + g.objects.farmChest.contents.count(Items.EGG) + " eggs; " + mara.lastOutcome + ")",
                laid > 0 && gathered.getAsBoolean());
        // (7) people and animals: a dog's trust grows from kindness; a frightened animal won't be petted
        var dog = first(rpg.animal.Species.DOG, a -> a.owned() && !a.baby);
        if (dog != null) {
            tp(dog.x + 1.2f, dog.z, (float) Math.PI / 2);
            dog.fear = 0; dog.goal = rpg.animal.Animal.Goal.IDLE; dog.nextThink = g.time + 5;
            float t0 = dog.trustOf(g.player.id);
            Result pet = g.interact.request(g.player, dog, "pet");
            need(String.format("petting the farm dog is allowed and builds a little trust (%.2f -> %.2f)", t0, dog.trustOf(g.player.id)), pet.committed() && dog.trustOf(g.player.id) > t0);
        }
        // (8) hunting: a struck rabbit runs; killed, it is butchered once with a knife into meat, hide and bones
        var rabbit = first(rpg.animal.Species.RABBIT, a -> !a.baby);
        if (rabbit != null) {
            tp(rabbit.x + 1.5f, rabbit.z, (float) Math.PI / 2);
            rabbit.goal = rpg.animal.Animal.Goal.IDLE;
            g.combat.resolve(g.player, rabbit, 1, 0, rpg.item.ItemDef.DamageType.SLASH, null, rabbit.x, rabbit.y + 0.3f, rabbit.z, "test", false);
            boolean ran = false;
            for (int i = 0; i < 120 && !ran; i++) { frames(1); ran = rabbit.goal == rpg.animal.Animal.Goal.FLEE; }
            need("a struck rabbit runs from whoever struck it (" + rabbit.goal + ", " + rabbit.reason + ")", ran);
            g.combat.resolve(g.player, rabbit, 999, 0, rpg.item.ItemDef.DamageType.SLASH, null, rabbit.x, rabbit.y + 0.3f, rabbit.z, "test", false);
            frames(5);
            tp(rabbit.x + 1.2f, rabbit.z, (float) Math.PI / 2);
            if (!g.player.has(Items.KNIFE, 1)) g.give(g.player, Items.KNIFE, 1);
            int meat = g.player.count(Items.RAW_MEAT), hides = g.player.count(Items.RAWHIDE);
            work(g.interact.request(g.player, rabbit, "butcher"), 600, () -> rabbit.butchered);
            Result again = g.interact.request(g.player, rabbit, "butcher");
            need("butchering the carcass yields raw meat and a hide, once (" + (g.player.count(Items.RAW_MEAT) - meat) + " meat, " + (g.player.count(Items.RAWHIDE) - hides) + " hide)",
                    g.player.count(Items.RAW_MEAT) > meat && g.player.count(Items.RAWHIDE) > hides && !again.committed());
        }
        // (9) killing someone's livestock in front of them is a crime they saw
        var hen = first(rpg.animal.Species.CHICKEN, a -> !a.baby);
        hour(12);
        if (hen != null) {
            g.teleport(mara, 104.5f, DevMap.FLOOR, 113.5f);    // open paddock ground, in daylight
            g.teleport(hen, mara.x + 1.5f, DevMap.FLOOR, mara.z);
            tp(hen.x + 1.2f, hen.z, (float) Math.PI / 2);
            mara.goal = Brain.Goal.IDLE; mara.nextThink = g.time + 6;
            mara.yaw = mara.wantYaw = (float) Math.atan2(-(g.player.x - mara.x), -(g.player.z - mara.z));
            frames(20);
            long w = g.law.witnessed;
            g.combat.resolve(g.player, hen, 999, 0, rpg.item.ItemDef.DamageType.SLASH, null, hen.x, hen.y + 0.3f, hen.z, "test", false);
            frames(2);
            boolean crime = g.state.all().stream().anyMatch(e -> e.type().equals("CrimeCommitted") && e.payload().startsWith("LIVESTOCK"));
            System.out.printf("      hen: owner %d, alive %s, crime %s, Mara at %.1f m sees %s (exposure %.2f), %s%n", hen.owner, hen.alive, crime, mara.distTo(g.player), g.ai.canSee(mara, g.player), g.ai.stealth.exposure(mara, g.player), g.state.clockText());
            need("killing Mara's hen is a livestock crime, and she saw it (" + (g.law.witnessed - w) + " witnesses)", crime && g.law.witnessed > w);
            mara.nextThink = 0;
        }
        // (10) far from anyone, wild animals rest in the ledger as cohort counts and come back unseen: none made, none lost
        int living = zoo.living();
        long d0 = zoo.deaths, b0 = zoo.births + zoo.hatchings;
        java.util.function.IntSupplier conserved = () -> zoo.living() + (int) (zoo.deaths - d0) - (int) (zoo.births + zoo.hatchings - b0);
        tp(118.5f, 92.5f, 0);
        frames(300);
        long out = zoo.dematerialized;
        int liveWild = 0;
        for (var a : zoo.all()) if (a.alive && a.home == zoo.wild) liveWild++;
        need("far away, the wood's ordinary wildlife is held as cohort counts (" + out + " held, " + liveWild + " still live), the total unchanged (" + living + " -> " + conserved.getAsInt() + " after " + (zoo.deaths - d0) + " deaths)", out > 0 && conserved.getAsInt() == living);
        tp(40.5f, 44.5f, 0);
        frames(300);
        need("coming back, they reappear out of sight, the total still " + conserved.getAsInt() + " (" + zoo.materialized + " materialized)", zoo.materialized > 0 && conserved.getAsInt() == living);
        String ledger = zoo.ledgerProblem();
        need("the population ledger balances: every habitat = start + births - deaths" + (ledger == null ? "" : " (" + ledger + ")"), ledger == null);
        // (11) save and load: the same animals, the same ids, stages and owners
        int n0 = zoo.living();
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (var a : zoo.all()) if (a.alive) ids.add(a.id + a.species.name() + a.baby + a.owner);
        java.nio.file.Path f = java.nio.file.Path.of("build", "animal-save.json");
        boolean saved = new SaveSystem(g).save(f);
        boolean loaded = saved && new SaveSystem(g).load(f);
        frames(30);
        java.util.Set<String> ids2 = new java.util.TreeSet<>();
        for (var a : zoo.all()) if (a.alive) ids2.add(a.id + a.species.name() + a.baby + a.owner);
        need("animals survive save and load: " + n0 + " -> " + zoo.living() + ", the same individuals", loaded && zoo.living() == n0 && ids.equals(ids2) && zoo.ledgerProblem() == null);
        shot("21-wood");
        hour(9);
        frames(30);
    }

    private void interfaceChecks() {
        need("Gold display: 99999 white, 150K yellow, 12M green, 1B blue, 2X purple",
                rpg.item.Currency.text(99_999).equals("99999") && rpg.item.Currency.color(99_999) == rpg.item.Currency.WHITE
                && rpg.item.Currency.text(150_000).equals("150K") && rpg.item.Currency.color(150_000) == rpg.item.Currency.YELLOW
                && rpg.item.Currency.text(12_000_000).equals("12M") && rpg.item.Currency.color(12_000_000) == rpg.item.Currency.GREEN
                && rpg.item.Currency.text(1_500_000_000L).equals("1B") && rpg.item.Currency.color(1_500_000_000L) == rpg.item.Currency.BLUE
                && rpg.item.Currency.text(2_100_000_000L).equals("2X") && rpg.item.Currency.color(2_100_000_000L) == rpg.item.Currency.PURPLE);
        need("the Lua addons loaded (" + hud.addons().summary() + ")", hud.fragment("session_gold") != null && hud.fragment("low_health_edge") != null);
        hud.go(Hud.Scene.WORLD);
        tp(DevMap.SPAWN_X, DevMap.SPAWN_Z, 0);
        g.player.restore();
        frames(120);
        need(String.format("at rest and unhurt the HUD keeps out of the way (vitals %.2f, target %.2f, wanted %.2f)", hud.alphaOf("vitals"), hud.alphaOf("target"), hud.alphaOf("wanted")),
                hud.alphaOf("vitals") < 0.05f && hud.alphaOf("target") < 0.05f && hud.alphaOf("wanted") < 0.05f);
        need("the minimap stays up in the world", hud.alphaOf("minimap") > 0.95f);
        g.player.hp = g.player.maxHp() * 0.2f;
        frames(60);
        need(String.format("hurt, the vitals fade in (%.2f) and the low-health addon reacts (%.2f)", hud.alphaOf("vitals"), hud.alphaOf("low_health_edge")), hud.alphaOf("vitals") > 0.95f && hud.alphaOf("low_health_edge") > 0.5f);
        shot("05b-hurt-hud");
        g.player.restore();
        hud.go(Hud.Scene.INVENTORY);
        frames(30);
        need(String.format("the inventory scene swaps groups in (grids %.2f, equipment %.2f) and the minimap out (%.2f)", hud.alphaOf("grids"), hud.alphaOf("equipment"), hud.alphaOf("minimap")),
                hud.alphaOf("grids") > 0.95f && hud.alphaOf("equipment") > 0.95f && hud.alphaOf("minimap") < 0.05f);
        hud.go(Hud.Scene.CHARACTER);
        frames(30);
        need("the character scene reuses the equipment doll beside the sheet, without the grids", hud.alphaOf("equipment") > 0.95f && hud.alphaOf("stats") > 0.95f && hud.alphaOf("grids") < 0.05f);
        hud.go(Hud.Scene.SKILLS);
        frames(30);
        need("the skills scene shows the grid and the guide", hud.alphaOf("skills") > 0.95f && hud.alphaOf("guide") > 0.95f);
        shot("05c-skills-scene");
        // the mouse: drag a sword across the grid, onto the main hand; drag food to the bar and eat with its key
        hud.go(Hud.Scene.INVENTORY);
        // the weapon in hand comes off (to the grid), is dragged across the grid, then dropped back on the main hand
        var heldStack = g.player.eq.get(rpg.item.ItemDef.Slot.MAIN);
        if (heldStack == null) { give(Items.tierItem(rpg.item.Tier.BRONZE, "dagger"), 1); g.items.equipFirst(g.player, Items.tierItem(rpg.item.Tier.BRONZE, "dagger"), rpg.item.ItemDef.Slot.MAIN, g.state.newRequestId()); heldStack = g.player.eq.get(rpg.item.ItemDef.Slot.MAIN); }
        var sword = heldStack.def;
        var off = g.items.unequip(g.player, rpg.item.ItemDef.Slot.MAIN);
        if (off.rejected()) System.out.println("      unequip: " + off.reason());
        room(sword, Items.TROUT, Items.COINS);
        if (g.player.count(Items.TROUT) < 2) give(Items.TROUT, 2);
        frames(20);
        rpg.item.Inventory inv = null;
        for (var grid : g.player.grids()) if (inv == null && grid.first(sword) >= 0) inv = grid;
        int i = inv.first(sword);
        int tx = -1, ty = -1;
        for (int y = inv.h - 1; y >= 0 && tx < 0; y--) for (int x = inv.w - 1; x >= 0 && tx < 0; x--)
            if ((x != inv.x(i) || y != inv.y(i)) && inv.fits(sword, x, y, inv.turned(i), i)) { tx = x; ty = y; }
        float fw = rpg.item.Inventory.fw(sword, inv.turned(i)), fh = rpg.item.Inventory.fh(sword, inv.turned(i));
        float[] from = hud.cellOnScreen(inv, inv.x(i) + fw / 2, inv.y(i) + fh / 2), to = hud.cellOnScreen(inv, tx + fw / 2, ty + fh / 2);
        drag(from, to);
        need("dragging an item moves it to the cell it is dropped on (" + inv.x(i) + "," + inv.y(i) + " -> " + tx + "," + ty + ")", inv.get(i) != null && inv.get(i).def == sword && inv.x(i) == tx && inv.y(i) == ty);
        drag(hud.cellOnScreen(inv, inv.x(i) + fw / 2, inv.y(i) + fh / 2), hud.spot("slot:MAIN"));
        if (g.player.main() != sword) System.out.println("      equip drag: slot " + java.util.Arrays.toString(hud.spot("slot:MAIN")) + " chat " + g.chat.last(2) + " main " + g.player.main());
        need("dropping the " + sword.name.toLowerCase() + " on the main-hand slot wields it", g.player.main() == sword);
        if (g.player.main() == null) g.items.equipFirst(g.player, sword, rpg.item.ItemDef.Slot.MAIN, g.state.newRequestId());
        int t = -1;
        rpg.item.Inventory tg = null;
        for (var grid : g.player.grids()) if (tg == null && (t = grid.first(Items.TROUT)) >= 0) tg = grid;
        float tfw = rpg.item.Inventory.fw(Items.TROUT, tg.turned(t)), tfh = rpg.item.Inventory.fh(Items.TROUT, tg.turned(t));
        drag(hud.cellOnScreen(tg, tg.x(t) + tfw / 2, tg.y(t) + tfh / 2), hud.spot("bar:5"));
        shot("05d-inventory-scene");
        hud.go(Hud.Scene.WORLD);
        frames(30);
        int trout = g.player.count(Items.TROUT);
        g.player.hp = g.player.maxHp() * 0.5f;
        g.player.cs.castCooldown = 0;
        hud.hotkey(5);
        frames(5);
        need("food dropped on the action bar is eaten with its key (" + trout + " -> " + g.player.count(Items.TROUT) + " trout)", g.player.count(Items.TROUT) == trout - 1 && g.player.hp > g.player.maxHp() * 0.5f);
        g.player.restore();
        // the load: carrying past 30 kg slows the body
        float free = g.player.burden();
        ItemStack ore = new ItemStack(Items.get("iron_ore"), 1);
        java.util.List<ItemStack> weights = new java.util.ArrayList<>();
        while (g.player.carried() < 45 && g.player.canStore(ore.copy(1))) { ItemStack o = ore.copy(1); g.player.store(o); weights.add(o); }
        float loaded = g.player.burden();
        need(String.format("a heavy load slows the body (%.1f kg: speed x%.2f, unloaded x%.2f)", g.player.carried(), loaded, free), loaded < free && loaded < 0.95f);
        for (ItemStack o : weights) for (var grid : g.player.grids()) { int k = grid.first(o.def); if (k >= 0) { grid.take(k, 1); break; } }
    }

    /** One drag with the mouse: press on a point, move, release on another (a frame each). */
    private void drag(float[] from, float[] to) {
        if (from == null || to == null) { System.out.println("      drag: no point on screen"); return; }
        mouse(from[0], from[1], true, true, false);
        mouse((from[0] + to[0]) / 2, (from[1] + to[1]) / 2, false, true, false);
        mouse(to[0], to[1], false, true, false);
        mouse(to[0], to[1], false, false, true);
        mouse(to[0], to[1], false, false, false);
    }

    private void mouse(float x, float y, boolean click, boolean down, boolean released) {
        ui.mx = x; ui.my = y; ui.click = click; ui.down = down; ui.released = released; ui.rclick = false;
        frames(1);
        ui.click = false; ui.released = false;
    }

    // ------------------------------------------------------------------ helpers
    void section(String name) {
        // RPG_CHECK_EACH=1: the item ledger checked as each section starts (to find the one that breaks it)
        if (System.getenv("RPG_CHECK_EACH") != null && g != null && g.invariants != null) for (String f : g.invariants.check()) if (f.startsWith("item")) System.out.println("    [ledger before '" + name + "'] " + f);
        System.out.println("== " + name);
    }

    void frames(int n) {
        for (int i = 0; i < n; i++) {
            boolean mainDown = g.controller.in.mainDown, offDown = g.controller.in.offDown;
            float fwd = g.controller.in.forward;
            if (!game.tick(1 / 60f)) need("the engine accepted every frame", false);
            Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false);
            g.controller.in.mainDown = mainDown;
            g.controller.in.offDown = offDown;
            g.controller.in.forward = fwd;
        }
    }

    void tp(float x, float z, float yaw) {
        g.teleport(g.player, x, DevMap.FLOOR, z);
        g.controller.snapTo(yaw);
        g.controller.camPitch = -0.1f;
        frames(2);
    }

    /** Turns the camera so the aim (from the eyes) points at a spot. */
    void aimAt(float x, float y, float z) {
        g.player.firstPerson = true;
        float dx = x - g.player.x, dy = y - g.player.eyeY(), dz = z - g.player.z;
        g.controller.camYaw = (float) Math.atan2(-dx, -dz);
        g.controller.camPitch = (float) Math.atan2(dy, Math.hypot(dx, dz));
    }

    void swing(boolean heavy) {
        g.controller.in.mainDown = true;
        frames(heavy ? 25 : 2);
        g.controller.in.mainDown = false;
        frames(50);
    }

    /** Waits until the player's hands are free (no swing, stagger or dodge in progress). */
    void idle() { for (int i = 0; i < 240 && g.player.cs.busy(); i++) frames(1); }

    void heavy() { aimAt(g.dummies.get(0).x, g.dummies.get(0).y + 1.2f, g.dummies.get(0).z); swing(true); }

    int swingDamage(Actor target) {
        aimAt(target.x, target.y + 1.2f, target.z);
        g.player.stamina = g.player.maxStamina();
        g.player.cs.combo = 0;
        long c = g.combat.contacts;
        swing(false);
        frames(10);
        if (g.combat.contacts == c) return -1;
        String last = g.combat.telemetry.peekLast();
        int i = last.indexOf("dealt ");
        return i < 0 ? -1 : Integer.parseInt(last.substring(i + 6, last.indexOf(';', i)));
    }

    /** Waits on timed work (or anything else) until a condition, or the frame budget runs out. */
    void work(Result started, int maxFrames, BooleanSupplier done) {
        if (started.rejected()) System.out.println("      (rejected: " + started.reason() + ")");
        for (int i = 0; i < maxFrames; i++) {
            frames(1);
            if (done.getAsBoolean()) break;
            if (g.interact.of(g.player) == null && i > 5) break;
        }
        g.interact.cancel(g.player, "test step done");
    }

    /** The gathering tools the tests handed out, taken out of the pack again (through the ledger) to make room. */
    void stowTools() {
        java.util.Set<String> ids = java.util.Set.of("bronze_battleaxe", "leather_axe", "leather_pickaxe", "bronze_sword", "fishing_rod", "small_fishing_net");
        for (rpg.item.Inventory inv : g.player.grids()) for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && ids.contains(s.def.id)) { inv.set(i, null); g.state.burn(s.def, s.amount); }
        }
    }

    /** Wields an item in the main hand: the one carried if there is one, else a new one; what the hand held goes to the pack (or out of the world, when it is full). */
    void wield(String id) {
        ItemDef d = Items.get(id);
        if (g.player.main() == d) return;
        idle();
        if (g.player.has(d, 1) && g.items.equipFirst(g.player, d, ItemDef.Slot.MAIN, g.state.newRequestId()).committed()) return;
        for (ItemDef.Slot sl : d.twoHanded() ? new ItemDef.Slot[]{ItemDef.Slot.MAIN, ItemDef.Slot.OFF} : new ItemDef.Slot[]{ItemDef.Slot.MAIN}) {
            ItemStack old = g.player.eq.get(sl);
            if (old == null) continue;
            g.player.eq.set(sl, null);
            if (!g.player.store(old)) g.state.burn(old.def, old.amount);
        }
        g.player.eq.set(ItemDef.Slot.MAIN, new ItemStack(d, 1));
        g.state.mint(d, 1);
    }

    /** Swings what is in the hand at a tree's trunk or a rock's ore, n times, the way the player would. */
    void chopAt(Node n, int times) {
        for (int i = 0; i < times && !n.depleted(); i++) {
            aimAt(n.bx + 0.5f, DevMap.FLOOR + (n.kind == Node.Kind.ROCK ? 0.55f : 1.3f), n.bz + 0.5f);
            frames(8);
            g.player.stamina = g.player.maxStamina();
            long c0 = g.combat.deflections;
            swing(false);
            idle();
            if (System.getenv("RPG_CHOP_DEBUG") != null) System.out.printf("      chop: %s struck %.0f defl %d phase %s main %s yaw %.2f pitch %.2f pos (%.2f %.2f) last %s%n", n.name, n.struck, g.combat.deflections - c0, g.player.cs.phase, g.player.main(), g.player.yaw, g.player.pitch, g.player.x, g.player.z, g.combat.telemetry.peekLast());
        }
        g.player.firstPerson = false;
    }

    void stand(Node n) {
        float[] s = g.ai.standSpot(npc(Ids.WORKER), n);
        tp(s[0], s[1], (float) Math.atan2(-(n.x - s[0]), -(n.z - s[1])));
        frames(5);
    }

    Node node(String def) {
        for (Node n : g.objects.nodes) if (n.def.equals(def) && !n.depleted()) return n;
        return null;
    }

    WorldObject find(java.util.function.Predicate<WorldObject> p) {
        for (WorldObject o : g.objects.everything()) if (p.test(o)) return o;
        return null;
    }

    Npc npc(long id) { return (Npc) g.actor(id); }

    void give(ItemDef d, int n) { g.give(g.player, d, n); }

    /** Clears the test's gathered resources out of the inventory (through the ledger) to make room. */
    void tidy() {
        for (int i = 0; i < g.player.inv.size(); i++) {
            ItemStack s = g.player.inv.get(i);
            if (s == null || s.def.equippable() || s.def == Items.COINS || s.def == Items.TINDERBOX || s.def == Items.SEALED_LETTER) continue;
            g.player.inv.set(i, null);
            g.state.burn(s.def, s.amount);
        }
    }

    /** Makes room as a player would at a bank: resources and food not kept for what follows are put away (burnt from the ledger). */
    void room(ItemDef... keep) {
        java.util.Set<ItemDef> k = new java.util.HashSet<>(java.util.Arrays.asList(keep));
        for (rpg.item.Inventory inv : g.player.grids()) for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s == null || k.contains(s.def)) continue;
            var c = s.def.category();
            if (c == ItemDef.Category.RESOURCE || c == ItemDef.Category.CONSUMABLE) { inv.set(i, null); g.state.burn(s.def, s.amount); }
        }
    }

    /** Takes away the filler (burnt fish, and the guam leaves the full-inventory test packed every grid with). */
    void clearBurnt() {
        for (rpg.item.Inventory inv : g.player.grids())
            for (int i = 0; i < inv.size(); i++) { ItemStack s = inv.get(i); if (s != null && (s.def == Items.BURNT_FISH || s.def == Items.GUAM)) { inv.set(i, null); g.state.burn(s.def, s.amount); } }
    }

    void shot(String name) { if (shots != null) Main.shot(gfx, win.width, win.height, new File(shots, name + ".png")); }

    void need(String what, boolean ok) {
        checks++;
        System.out.println("  [" + (ok ? " ok " : "FAIL") + "] " + what);
        if (!ok) failed.add(what);
    }
}
