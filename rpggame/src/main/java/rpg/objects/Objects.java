package rpg.objects;

import java.util.ArrayList;
import java.util.List;
import rpg.Ids;
import rpg.Rpg;
import rpg.interact.WorldObject;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.item.Recipes;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * Every world object on the devmap, by stable id: the authored fixtures (built here in a fixed order, so their ids are
 * the same in every run and every save) and the things made in play - dropped items, fires, graves.
 */
public final class Objects {
    private final Rpg g;
    private final List<WorldObject> list = new ArrayList<>();
    private long nextFixture = Ids.FIXTURES;
    public Door shedDoor, storeDoor, farmGate;
    private final List<Door> jailDoors = new ArrayList<>();
    /** The farm's fixtures (GDD 3.9): the trough, the nest boxes, the farmer's chest of feed and eggs. */
    public Container trough, farmChest;
    public final List<Container> nestBoxes = new ArrayList<>();
    public Container shedChest, storeChest;
    public Fixtures.Stall stall;
    public BuildSpot buildSpot;
    public final List<Node> nodes = new ArrayList<>();
    public Fixtures.Altar altar;
    public final List<Furniture> furniture = new ArrayList<>();

    public Objects(Rpg g) { this.g = g; }

    public List<WorldObject> all() { List<WorldObject> l = new ArrayList<>(); for (WorldObject o : list) if (!o.removed) l.add(o); return l; }
    public List<WorldObject> everything() { return list; }
    public WorldObject get(long id) { for (WorldObject o : list) if (o.id == id) return o; return null; }
    public <T extends WorldObject> T add(T o) { list.add(o); return o; }
    private long fid() { return nextFixture++; }

    /** The authored fixtures, in a fixed order. */
    public void buildFixtures() {
        int f = DevMap.FLOOR;
        // the interaction shed: a door, a locked store, an owned chest, loose items (one of them owned)
        shedDoor = add(new Door(fid(), "Shed door", DevMap.SHED_DOOR, false, null, 1, 0));
        storeDoor = add(new Door(fid(), "Store-room door", DevMap.STORE_DOOR, true, Items.CHANCERY_KEY, 10, Ids.MERCHANT_A));
        shedDoor.spawn(g);
        storeDoor.spawn(g);
        shedChest = add(new Container(fid(), "chest", "Ada's chest", "chest", DevMap.SHED_CHEST[0] + 0.5f, f + 0.4f, DevMap.SHED_CHEST[1] + 0.5f, 20, Ids.MERCHANT_A));
        storeChest = add(new Container(fid(), "chest", "Store chest", "chest", DevMap.STORE_CHEST[0] + 0.5f, f + 0.4f, DevMap.STORE_CHEST[1] + 0.5f, 20, Ids.MERCHANT_A));
        storeChest.locked = true; storeChest.lockLevel = 20;
        float tx = DevMap.SHED_TABLE[0] + 1f, ty = f + Furniture.TABLE_TOP, tz = DevMap.SHED_TABLE[1] + 1f;
        Object[][] loose = {{Items.TINDERBOX, 1, -0.6f, -0.6f}, {Items.KNIFE, 1, 0f, -0.6f}, {Items.HAMMER, 1, 0.6f, -0.6f}, {Items.FISHING_ROD, 1, -0.6f, 0.4f},
                {Items.LOCKPICK, 1, 0f, 0.4f}, {Items.get("bronze_axe"), 1, 0.6f, 0.4f}, {Items.get("bronze_pickaxe"), 1, 0.25f, -0.1f}};
        for (Object[] o : loose) add(new GroundItem(fid(), new ItemStack((rpg.item.ItemDef) o[0], (int) o[1]), tx + (float) o[2], ty, tz + (float) o[3]));
        add(new GroundItem(fid(), new ItemStack(Items.COINS, 50, Ids.MERCHANT_A, false, 0), tx - 0.3f, ty, tz - 0.1f));
        // workstations and the build spot
        add(new Station(fid(), "furnace", "Furnace", Recipes.Station.FURNACE, DevMap.FURNACE_AT[0] + 0.5f, f + 0.6f, DevMap.FURNACE_AT[1] + 0.5f));
        add(new Station(fid(), "anvil", "Anvil", Recipes.Station.ANVIL, DevMap.ANVIL_AT[0] + 0.5f, f + 0.6f, DevMap.ANVIL_AT[1] + 0.5f));
        add(new Station(fid(), "range", "Range", Recipes.Station.RANGE, DevMap.RANGE_AT[0] + 0.5f, f + 0.6f, DevMap.RANGE_AT[1] + 0.5f));
        add(new Station(fid(), "craft_table", "Crafting table", Recipes.Station.CRAFT_TABLE, DevMap.CRAFT_AT[0] + 0.5f, f + 0.6f, DevMap.CRAFT_AT[1] + 0.5f));
        add(new Station(fid(), "sawbench", "Sawbench", Recipes.Station.SAWBENCH, DevMap.SAW_AT[0] + 0.5f, f + 0.6f, DevMap.SAW_AT[1] + 0.5f));
        buildSpot = add(new BuildSpot(fid(), (DevMap.BUILD_X0 + DevMap.BUILD_X1 + 1) / 2f, f + 0.4f, (DevMap.BUILD_Z0 + DevMap.BUILD_Z1 + 1) / 2f));
        // gathering nodes
        for (int[] t : DevMap.TREES) node(Node.Kind.TREE, "tree", "Tree", Skill.WOODCUTTING, 1, Items.LOGS, 25, 3.2f, 3, g.cfg.treeRespawn, Blocks.LOG, t, Blocks.LEAVES, 4);
        for (int[] t : DevMap.OAKS) node(Node.Kind.TREE, "oak", "Oak tree", Skill.WOODCUTTING, 15, Items.OAK_LOGS, 37, 4f, 4, g.cfg.treeRespawn, Blocks.OAK_LOG, t, Blocks.OAK_LEAVES, 5);
        for (int[] t : DevMap.WILLOWS) node(Node.Kind.TREE, "willow", "Willow tree", Skill.WOODCUTTING, 30, Items.WILLOW_LOGS, 68, 4.4f, 5, g.cfg.treeRespawn, Blocks.WILLOW_LOG, t, Blocks.WILLOW_LEAVES, 4);
        for (int[] r : DevMap.COPPER) node(Node.Kind.ROCK, "copper_rock", "Copper rocks", Skill.MINING, 1, Items.COPPER_ORE, 17, 3f, 1, g.cfg.rockRespawn, Blocks.COPPER_ROCK, r, 0, 0);
        for (int[] r : DevMap.TIN) node(Node.Kind.ROCK, "tin_rock", "Tin rocks", Skill.MINING, 1, Items.TIN_ORE, 17, 3f, 1, g.cfg.rockRespawn, Blocks.TIN_ROCK, r, 0, 0);
        for (int[] r : DevMap.IRON) node(Node.Kind.ROCK, "iron_rock", "Iron rocks", Skill.MINING, 15, Items.IRON_ORE, 35, 3.6f, 1, g.cfg.rockRespawn, Blocks.IRON_ROCK, r, 0, 0);
        for (int[] r : DevMap.COALS) node(Node.Kind.ROCK, "coal_rock", "Coal rocks", Skill.MINING, 30, Items.COAL, 50, 4f, 1, g.cfg.rockRespawn, Blocks.COAL_ROCK, r, 0, 0);
        node(Node.Kind.FISH, "shrimp_spot", "Fishing spot (net)", Skill.FISHING, 1, Items.RAW_SHRIMP, 10, 3.4f, 0, 0, 0, new int[]{(int) DevMap.FISH_SPOTS[0][0], (int) DevMap.FISH_SPOTS[0][1]}, 0, 0);
        nodes.get(nodes.size() - 1).needs = Items.FISHING_NET;
        node(Node.Kind.FISH, "trout_spot", "Fishing spot (rod)", Skill.FISHING, 20, Items.RAW_TROUT, 50, 4f, 0, 0, 0, new int[]{(int) DevMap.FISH_SPOTS[1][0], (int) DevMap.FISH_SPOTS[1][1]}, 0, 0);
        nodes.get(nodes.size() - 1).needs = Items.FISHING_ROD;
        for (int[] p : DevMap.HERBS) node(Node.Kind.PATCH, "herb_patch", "Herb patch", Skill.HARVESTING, 1, Items.GUAM, 11, 2.2f, 3, g.cfg.patchRespawn, Blocks.HERB_PATCH, p, 0, 0);
        for (int[] p : DevMap.FLAXES) node(Node.Kind.PATCH, "flax", "Flax", Skill.HARVESTING, 5, Items.FLAX, 8, 1.8f, 4, g.cfg.patchRespawn, Blocks.FLAX_PATCH, p, 0, 0);
        for (int[] p : DevMap.BERRIES) node(Node.Kind.PATCH, "berry_bush", "Redberry bush", Skill.HARVESTING, 10, Items.BERRIES, 18, 2.4f, 3, g.cfg.patchRespawn, Blocks.BERRY_BUSH, p, 0, 0);
        // agility: the course wall and the town-wall shortcut
        add(new Fixtures.Climb(fid(), "Climbing wall", 117.5f, f + 1.4f, DevMap.AG_WALL_Z + 0.5f));
        add(new Fixtures.Climb(fid(), "Wall shortcut", DevMap.SHORTCUT[0] + 0.5f, f + 1.4f, DevMap.SHORTCUT[1] + 0.5f));
        // the combat lane's cabinet, the debug terminal, the stall, the kiosk
        add(new Fixtures.Cabinet(fid(), DevMap.CABINET[0] + 0.5f, f + 1f, DevMap.CABINET[1] - 0.2f));
        add(new Fixtures.Terminal(fid(), DevMap.DEBUG_TERMINAL[0] + 0.5f, f + 1.4f, DevMap.DEBUG_TERMINAL[1] + 0.5f));
        stall = add(new Fixtures.Stall(fid(), DevMap.STALL[0] + 0.5f, f + 1.1f, DevMap.STALL[1] + 0.5f, Ids.MERCHANT_A));
        add(new Fixtures.Kiosk(fid(), 61.5f, f + 1.2f, 104.5f));
        // fixture contents (created once, through the ledger)
        put(shedChest, new ItemStack(Items.BRONZE_BAR, 3, Ids.MERCHANT_A, false, shedChest.id));
        put(shedChest, new ItemStack(Items.COINS, 120, Ids.MERCHANT_A, false, shedChest.id));
        put(storeChest, new ItemStack(Items.STEEL_BAR, 4, Ids.MERCHANT_A, false, storeChest.id));
        put(storeChest, new ItemStack(Items.IRON_ARROWS, 40, Ids.MERCHANT_A, false, storeChest.id));
        for (int i = 0; i < 4; i++) if (stall.goods.add(new ItemStack(Items.BREAD, 1, Ids.MERCHANT_A, false, stall.id))) g.state.mint(Items.BREAD, 1);
        for (WorldObject o : list) if (o instanceof GroundItem gi) g.state.mint(gi.stack.def, gi.stack.amount);
        // furniture: the map's pieces (standing), then the build spot's (standing once built) - after every older fixture, so their ids hold
        for (DevMap.Piece p : DevMap.FURNITURE) {
            Furniture fu = add(new Furniture(fid(), Furniture.Kind.valueOf(p.kind()), p.x0(), p.z0(), p.x1(), p.z1(), p.h(), p.facing(), p.owner(), true));
            furniture.add(fu);
        }
        for (String k : BuildSpot.ORDER) {
            DevMap.Piece p = BuildSpot.PIECES.get(k);
            Furniture fu = add(new Furniture(fid(), Furniture.Kind.valueOf(p.kind()), p.x0(), p.z0(), p.x1(), p.z1(), p.h(), p.facing(), p.owner(), false));
            if (fu.kind == Furniture.Kind.BED) fu.name = "Your bed";
            buildSpot.pieces.put(k, fu);
            furniture.add(fu);
        }
        // the farm (after every older fixture, so their ids hold): a gate in the paddock fence, a trough, nest boxes, the farmer's chest
        farmGate = add(new Door(fid(), "Paddock gate", DevMap.FARM_GATE, false, null, 1, 0));
        farmGate.spawn(g);
        trough = add(new Container(fid(), "trough", "Feed trough", "trough", DevMap.TROUGH[0], f + 0.35f, DevMap.TROUGH[1], 40, Ids.FARMER));
        for (float[] nb : DevMap.NEST_BOXES) nestBoxes.add(add(new Container(fid(), "nest_box", "Nest box", "nest", nb[0], f + 0.3f, nb[1], 10, Ids.FARMER)));
        farmChest = add(new Container(fid(), "chest", "Mara's chest", "chest", DevMap.FARM_CHEST[0], f + 0.4f, DevMap.FARM_CHEST[1], 20, Ids.FARMER));
        for (int i = 0; i < 10; i++) put(farmChest, new ItemStack(Items.ANIMAL_FEED, 1, Ids.FARMER, false, farmChest.id));
        for (int i = 0; i < 3; i++) put(trough, new ItemStack(Items.ANIMAL_FEED, 1, Ids.FARMER, false, trough.id));
        // a crab spot at the pond's far shallows (after every older fixture, so their ids hold): the cage's own
        node(Node.Kind.FISH, "crab_spot", "Fishing spot (cage)", Skill.FISHING, 10, Items.RAW_CRAB, 30, 4.6f, 0, 0, 0, new int[]{(int) DevMap.FISH_SPOTS[2][0], (int) DevMap.FISH_SPOTS[2][1]}, 0, 0);
        nodes.get(nodes.size() - 1).needs = Items.CRAB_CAGE;
        // the altar at the edge of the debug square (the last fixture made: every older id holds)
        altar = add(new Fixtures.Altar(fid(), DevMap.ALTAR[0] + 0.5f, f + 0.9f, DevMap.ALTAR[1] + 0.5f));
        // the graveyard (after the altar, so every older id holds): its furniture, and the undertaker's custody chest
        for (DevMap.Piece p : DevMap.GRAVEYARD_PIECES) {
            Furniture fu = add(new Furniture(fid(), Furniture.Kind.valueOf(p.kind()), p.x0(), p.z0(), p.x1(), p.z1(), p.h(), p.facing(), p.owner(), true));
            furniture.add(fu);
        }
        custody = add(new Container(fid(), "custody", "The undertaker's chest", "chest", DevMap.CUSTODY_CHEST[0], f + 0.4f, DevMap.CUSTODY_CHEST[1], 100, Ids.UNDERTAKER));
        custody.locked = true; custody.lockLevel = 60;
        for (var cell : rpg.world.Jails.CELLS) {
            Door door = add(new Door(fid(), cell.jurisdiction() + " cell door", new int[][]{{cell.gateX(), cell.frontZ()}}, true, null, 100, 0));
            door.spawn(g); jailDoors.add(door);
            var p = cell.slab();
            Furniture slab = add(new Furniture(fid(), Furniture.Kind.SLAB, p.x0(), p.z0(), p.x1(), p.z1(), 1, p.facing(), 0, true));
            slab.name = "Cell slab"; furniture.add(slab);
        }
        for (var cell : rpg.world.Jails.CELLS) add(new Fixtures.Board(fid(), cell.releaseX() - 2, f + 1, cell.releaseZ(), cell.jurisdiction()));
    }

    /** The undertaker's chest: what the guards bring in from the dead, held until it is redeemed (GDD 3.12). */
    public Container custody;

    /** Every door, for the frame's physics read-back and the NPCs' door manners. */
    public Door[] doors() {
        List<Door> doors = new ArrayList<>(); doors.add(shedDoor); doors.add(storeDoor);
        if (farmGate != null) doors.add(farmGate);
        doors.addAll(jailDoors);
        return doors.toArray(Door[]::new);
    }
    public Door jailDoor(String jurisdiction) {
        for (Door door : jailDoors) if (door.name.equals(jurisdiction + " cell door")) return door;
        return null;
    }

    /** The tree or rock a block belongs to (the trunk or crown, the ore), or null. */
    public Node nodeAt(int bx, int by, int bz) {
        for (Node n : nodes) if ((n.kind == Node.Kind.TREE || n.kind == Node.Kind.ROCK) && Math.abs(n.bx - bx) <= 2 && Math.abs(n.bz - bz) <= 2 && n.contains(bx, by, bz)) return n;
        return null;
    }

    /** The standing furniture whose cells hold a block, or null. */
    public Furniture furnitureAt(int bx, int by, int bz) {
        for (Furniture f : furniture)
            if (f.exists() && bx >= f.x0 && bx <= f.x1 && bz >= f.z0 && bz <= f.z1 && by >= DevMap.FLOOR && by < DevMap.FLOOR + f.h) return f;
        return null;
    }

    private void put(Container c, ItemStack s) { if (c.contents.add(s)) g.state.mint(s.def, s.amount); }

    private void node(Node.Kind kind, String def, String name, Skill skill, int level, rpg.item.ItemDef product, int xp, float time, int stock, float respawn, int block, int[] at, int leaves, int height) {
        nodes.add(add(new Node(fid(), def, name, kind, skill, level, product, xp, time, stock, respawn, block, at[0], at[1], leaves, height)));
    }

    /** Drops a stack in the world (it keeps its owner and provenance). */
    public GroundItem drop(ItemStack s, float x, float y, float z) {
        return add(new GroundItem(g.state.newEntityId(), s, x, y, z));
    }

    public void update(float dt) {
        for (Node n : nodes) n.update(g);
        stall.update(g);
        for (WorldObject o : new ArrayList<>(list)) if (o instanceof Fire fire) fire.update(g, dt);
        list.removeIf(o -> o.removed && (o instanceof Fire || (o instanceof GroundItem gi && gi.id >= Ids.DYNAMIC)));
        doorsForNpcs();
        for (Door d : doors()) d.update(g, g.lastRigids);
    }

    /** NPCs open unlocked doors on their way through and close them behind them. */
    private void doorsForNpcs() {
        for (Door d : doors()) {
            boolean near = false;
            rpg.ai.Npc by = null;
            for (var n : g.npcs) if (n.alive && n.loaded && Math.hypot(n.x - d.x, n.z - d.z) < 2.6) { near = true; by = n; }
            if (!d.open && !d.locked && near) { d.setOpen(g, true, by, 0); d.autoOpened = true; d.autoCloseAt = g.time + 3; }
            if (d.open && d.autoOpened && !near && g.time > d.autoCloseAt && !d.blocked(g) && Math.hypot(g.player.x - d.x, g.player.z - d.z) > 2.2) {
                d.setOpen(g, false, null, 0); d.autoOpened = false;
            }
        }
    }
}
