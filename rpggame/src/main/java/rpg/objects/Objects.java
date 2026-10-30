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
    private final List<Door> doors = new ArrayList<>();
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
    public Fixtures.Casket casket;
    public final List<Furniture> furniture = new ArrayList<>();

    public Objects(Rpg g) { this.g = g; }

    public List<WorldObject> all() { List<WorldObject> l = new ArrayList<>(); for (WorldObject o : list) if (!o.removed) l.add(o); return l; }
    public List<WorldObject> everything() { return list; }
    public WorldObject get(long id) { for (WorldObject o : list) if (o.id == id) return o; return null; }
    public <T extends WorldObject> T add(T o) {
        list.add(o);
        if (o instanceof Door door) doors.add(door);
        if (o instanceof Container c && c.kind.equals("chest")) c.solid(g);
        return o;
    }
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
        add(new Fixtures.Cabinet(fid(), DevMap.CABINET[0] + 0.5f, f + 1f, DevMap.CABINET[1] + 0.3f));   // (in front of its backboard, not sunk in it)
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
        farmGate = add(Door.fenceGate(fid(), "Paddock gate", DevMap.FARM_GATE, 0));
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
            if (p.kind().equals("HEADSTONE")) {
                Container stone = add(new Container(fid(), "headstone", "Gravestone", "memorial", p.x0()+0.5f, f, p.z0()+0.5f, 320, 0));
                stone.burialPlot = "DevCity graveyard"; continue;
            }
            Furniture fu = add(new Furniture(fid(), Furniture.Kind.valueOf(p.kind()), p.x0(), p.z0(), p.x1(), p.z1(), p.h(), p.facing(), p.owner(), true));
            furniture.add(fu);
        }
        custody = add(new Container(fid(), "custody", "The undertaker's chest", "chest", DevMap.CUSTODY_CHEST[0], f + 0.4f, DevMap.CUSTODY_CHEST[1], 100, Ids.UNDERTAKER));
        custody.locked = true; custody.lockLevel = 60;
        // (the cells of the first two kingdoms here, so the old ids hold; DevPalace's is made with the rest of 0.2.0 below)
        for (var cell : rpg.world.Jails.CELLS) if (!cell.jurisdiction().equals(DevMap.PALACE)) jailCell(cell);
        for (var cell : rpg.world.Jails.CELLS) if (!cell.jurisdiction().equals(DevMap.PALACE)) add(new Fixtures.Board(fid(), cell.releaseX() - 2, f + 1, cell.releaseZ(), cell.jurisdiction())).solid(g);
        node(Node.Kind.ROCK,"quarry_face","Supported quarry face",Skill.MINING,1,Items.STONE_BLOCK,15,3,3,g.cfg.rockRespawn,Blocks.STONE,new int[]{96,19},0,0);
        node(Node.Kind.ROCK,"clay_bank","Clay bank",Skill.MINING,1,Items.CLAY,12,2.2f,3,g.cfg.rockRespawn,Blocks.DIRT,new int[]{97,19},0,0);
        node(Node.Kind.ROCK,"gem_deposit","Uncertain gem deposit",Skill.MINING,30,Items.get("uncut_sapphire"),45,4,1,g.cfg.rockRespawn,Blocks.COPPER_ROCK,new int[]{98,19},0,0);
        for (Node node : nodes) if (node.def.equals("quarry_face") || node.def.equals("clay_bank") || node.def.equals("gem_deposit")) node.setBlocks(g,true);
        int[][][] entrances={
            {{90,DevMap.SHOP_A_Z1},{91,DevMap.SHOP_A_Z1}},{{113,DevMap.CH_A_Z1},{114,DevMap.CH_A_Z1}},
            {{89,DevMap.HOUSE1_Z0},{90,DevMap.HOUSE1_Z0}},{{103,DevMap.HOUSE2_Z0},{104,DevMap.HOUSE2_Z0}},
            {{DevMap.SHOP_B_X1,108},{DevMap.SHOP_B_X1,109}},{{35,DevMap.CH_B_Z0},{36,DevMap.CH_B_Z0}},
            {{48,DevMap.CONTACT_Z0},{49,DevMap.CONTACT_Z0}},{{DevMap.UNDERTAKER_X0,96},{DevMap.UNDERTAKER_X0,97}}
        };
        long[] owners={Ids.MERCHANT_A,Ids.LAWMAN_A,Ids.MERCHANT_A,Ids.VILLAGER,Ids.MERCHANT_B,Ids.LAWMAN_B,Ids.CONTACT,Ids.UNDERTAKER};
        for(int i=0;i<entrances.length;i++)add(new Door(fid(),"Building door",entrances[i],false,null,1,owners[i])).spawn(g);
        int barn=(DevMap.BARN_X0+DevMap.BARN_X1)/2;
        add(new Door(fid(),"Barn door",new int[][]{{barn,DevMap.BARN_Z0},{barn+1,DevMap.BARN_Z0}},false,null,1,Ids.FARMER)).spawn(g);
        // Append new nodes after all existing fixtures: old saves retain every established fixture identity.
        for (int[] t : DevMap.WILD_TREES) {
            boolean oak = (t[0] + t[1]) % 2 == 0;
            node(Node.Kind.TREE, oak ? "oak" : "tree", oak ? "Oak tree" : "Tree", Skill.WOODCUTTING,
                    oak ? 15 : 1, oak ? Items.OAK_LOGS : Items.LOGS, oak ? 37 : 25, oak ? 4 : 3.2f,
                    oak ? 4 : 3, g.cfg.treeRespawn, oak ? Blocks.OAK_LOG : Blocks.LOG, t,
                    oak ? Blocks.OAK_LEAVES : Blocks.LEAVES, 4 + (t[0] * 7 + t[1]) % 3);
        }
        // the bank (after every older fixture, so their ids hold): its door, the counter across the hall, the teller's cot
        add(new Door(fid(), "Bank door", DevMap.BANK_DOOR, false, null, 1, Ids.TELLER)).spawn(g);
        furniture.add(add(new Furniture(fid(), Furniture.Kind.COUNTER, DevMap.BANK_X0 + 2, DevMap.BANK_COUNTER_Z, DevMap.BANK_X1 - 2, DevMap.BANK_COUNTER_Z, 1, 0, Ids.TELLER, true)));
        bankBed = add(new Furniture(fid(), Furniture.Kind.BED, DevMap.BANK_X1 - 2, DevMap.BANK_Z1 - 4, DevMap.BANK_X1 - 2, DevMap.BANK_Z1 - 3, 1, 0, Ids.TELLER, true));
        furniture.add(bankBed); bankBed.name = "Teller's cot";
        for (Furniture furnishing : furniture) if (furnishing.owner == Ids.TELLER) furnishing.place(g, true);
        // 0.1.8 (after every older fixture, so their ids hold): the brook's rod spots, river water; and the crab cage's
        // spot is the pond's shore shallows, which the shellfish gatherer works
        for (float[] at : DevMap.STREAM_SPOTS) {
            node(Node.Kind.FISH, "river_spot", "Fishing spot (rod, brook)", Skill.FISHING, 20, Items.RAW_TROUT, 50, 4f, 0, 0, 0, new int[]{(int) at[0], (int) at[1]}, 0, 0);
            Node spot = nodes.get(nodes.size() - 1); spot.waters = Node.Waters.RIVER; spot.needs = Items.FISHING_ROD;
        }
        // 0.1.9 (after every older fixture, so their ids hold): the cage spots and the quarry face the residents' work wanted
        for (float[] at : DevMap.MORE_CRAB_SPOTS) {
            node(Node.Kind.FISH, "crab_spot", "Fishing spot (cage)", Skill.FISHING, 10, Items.RAW_CRAB, 30, 4.6f, 0, 0, 0, new int[]{(int) at[0], (int) at[1]}, 0, 0);
            nodes.get(nodes.size() - 1).needs = Items.CRAB_CAGE;
        }
        node(Node.Kind.ROCK, "quarry_face", "Supported quarry face", Skill.MINING, 1, Items.STONE_BLOCK, 15, 3, 3, g.cfg.rockRespawn, Blocks.STONE, DevMap.QUARRY_FACE_2, 0, 0);
        nodes.get(nodes.size() - 1).setBlocks(g, true);
        for (Node node : nodes) if (node.def.equals("crab_spot")) node.waters = Node.Waters.SHORE;
        expanse(f);
        // 0.2.2 (after every older fixture, so their ids hold): the unfinished casket where the dead buy back their things
        casket = add(new Fixtures.Casket(fid(), DevMap.CASKET[0], f, DevMap.CASKET[1])).solid(g);
        supportBeds();
        Furniture cityStall=add(new Furniture(fid(),Furniture.Kind.COUNTER,76,29,78,29,1,0,Ids.TRADER_CITY,true));
        furniture.add(cityStall);cityStall.place(g,true);
        Furniture palaceStall=add(new Furniture(fid(),Furniture.Kind.COUNTER,181,97,183,97,1,0,Ids.TRADER_PALACE,true));
        furniture.add(palaceStall);palaceStall.place(g,true);
        g.vox.doors(doors);
        rpg.ai.Waypoints.doors(g.vox, doors);
    }

    /** New beds append after established fixture ids; spare beds retain their identities. */
    public void supportBeds() {
        for (var p : DevMap.SUPPORT_BEDS) {
            Furniture bed = furnitureAt(p.x0(),DevMap.FLOOR,p.z0());
            if (bed == null) {
                bed=add(new Furniture(fid(),Furniture.Kind.BED,p.x0(),p.z0(),p.x1(),p.z1(),1,p.facing(),p.owner(),true));
                furniture.add(bed);bed.place(g,true);
            }
            if (bed.owner==0 || bed.owner==p.owner()) {bed.owner=p.owner();bed.name=g.nameOf(p.owner())+"'s bed";}
        }
    }

    private void jailCell(rpg.world.Jails.Cell cell) {
        Door door = add(Door.cellDoor(fid(), cell.jurisdiction() + " cell door", new int[][]{{cell.gateX(), cell.frontZ()}}));
        door.spawn(g); jailDoors.add(door);
        var p = cell.slab();
        Furniture slab = add(new Furniture(fid(), Furniture.Kind.SLAB, p.x0(), p.z0(), p.x1(), p.z1(), 1, p.facing(), 0, true));
        slab.name = "Cell slab"; furniture.add(slab);
    }

    /**
     * 0.2.0's fixtures (after every older one, so their ids hold): DevPalace's cell and its warrants board, the new
     * buildings' furniture, and the new ground's resources - the southern wood's trees, the brook's and the lake's spots,
     * the mining hills' rocks, the marble quarry, the fields' patches and the meadow's bushes.
     */
    private void expanse(float f) {
        var palace = rpg.world.Jails.forJurisdiction(DevMap.PALACE);
        jailCell(palace);
        add(new Fixtures.Board(fid(), palace.releaseX() + 3, f + 1, palace.releaseZ(), DevMap.PALACE)).solid(g);
        for (DevMap.Piece p : rpg.world.Expanse.PIECES) furniture.add(add(new Furniture(fid(), Furniture.Kind.valueOf(p.kind()), p.x0(), p.z0(), p.x1(), p.z1(), p.h(), p.facing(), p.owner(), true)));
        for (int[] t : rpg.world.Expanse.WOOD_TREES) {
            int s = t[2], log = rpg.world.Expanse.logOf(s), leaves = rpg.world.Expanse.leavesOf(s), h = rpg.world.Expanse.heightOf(t[0], t[1], s);
            switch (s) {
                case 1 -> node(Node.Kind.TREE, "oak", "Oak tree", Skill.WOODCUTTING, 15, Items.OAK_LOGS, 37, 4f, 4, g.cfg.treeRespawn, log, new int[]{t[0], t[1]}, leaves, h);
                case 2 -> node(Node.Kind.TREE, "willow", "Willow tree", Skill.WOODCUTTING, 30, Items.WILLOW_LOGS, 68, 4.4f, 5, g.cfg.treeRespawn, log, new int[]{t[0], t[1]}, leaves, h);
                default -> node(Node.Kind.TREE, "tree", s == 3 ? "Birch tree" : s == 4 ? "Spruce tree" : s == 5 ? "Jungle tree" : "Tree", Skill.WOODCUTTING, 1, Items.LOGS, 25, 3.2f, 3, g.cfg.treeRespawn, log, new int[]{t[0], t[1]}, leaves, h);
            }
        }
        for (int[] t : rpg.world.Expanse.MEADOW_TREES) node(Node.Kind.TREE, "oak", "Oak tree", Skill.WOODCUTTING, 15, Items.OAK_LOGS, 37, 4f, 4, g.cfg.treeRespawn, Blocks.OAK_LOG, t, Blocks.OAK_LEAVES, 5);
        for (float[] at : rpg.world.Expanse.BROOK_SPOTS) {
            node(Node.Kind.FISH, "river_spot", "Fishing spot (rod, brook)", Skill.FISHING, 20, Items.RAW_TROUT, 50, 4f, 0, 0, 0, new int[]{(int) at[0], (int) at[1]}, 0, 0);
            Node spot = nodes.get(nodes.size() - 1); spot.waters = Node.Waters.RIVER; spot.needs = Items.FISHING_ROD;
        }
        float[][] lake = rpg.world.Expanse.LAKE_SPOTS;
        node(Node.Kind.FISH, "shrimp_spot", "Fishing spot (net, lake)", Skill.FISHING, 1, Items.RAW_SHRIMP, 10, 3.4f, 0, 0, 0, new int[]{(int) lake[0][0], (int) lake[0][1]}, 0, 0);
        nodes.get(nodes.size() - 1).needs = Items.FISHING_NET; nodes.get(nodes.size() - 1).waters = Node.Waters.LAKE;
        node(Node.Kind.FISH, "trout_spot", "Fishing spot (rod, lake)", Skill.FISHING, 20, Items.RAW_TROUT, 50, 4f, 0, 0, 0, new int[]{(int) lake[1][0], (int) lake[1][1]}, 0, 0);
        nodes.get(nodes.size() - 1).needs = Items.FISHING_ROD; nodes.get(nodes.size() - 1).waters = Node.Waters.LAKE;
        node(Node.Kind.FISH, "crab_spot", "Fishing spot (cage, lake)", Skill.FISHING, 10, Items.RAW_CRAB, 30, 4.6f, 0, 0, 0, new int[]{(int) lake[2][0], (int) lake[2][1]}, 0, 0);
        nodes.get(nodes.size() - 1).needs = Items.CRAB_CAGE; nodes.get(nodes.size() - 1).waters = Node.Waters.SHORE;
        for (int[] r : rpg.world.Expanse.COPPER) node(Node.Kind.ROCK, "copper_rock", "Copper rocks", Skill.MINING, 1, Items.COPPER_ORE, 17, 3f, 1, g.cfg.rockRespawn, Blocks.COPPER_ROCK, r, 0, 0);
        for (int[] r : rpg.world.Expanse.TIN) node(Node.Kind.ROCK, "tin_rock", "Tin rocks", Skill.MINING, 1, Items.TIN_ORE, 17, 3f, 1, g.cfg.rockRespawn, Blocks.TIN_ROCK, r, 0, 0);
        for (int[] r : rpg.world.Expanse.IRON) node(Node.Kind.ROCK, "iron_rock", "Iron rocks", Skill.MINING, 15, Items.IRON_ORE, 35, 3.6f, 1, g.cfg.rockRespawn, Blocks.IRON_ROCK, r, 0, 0);
        for (int[] r : rpg.world.Expanse.COAL) node(Node.Kind.ROCK, "coal_rock", "Coal rocks", Skill.MINING, 30, Items.COAL, 50, 4f, 1, g.cfg.rockRespawn, Blocks.COAL_ROCK, r, 0, 0);
        for (int[] r : rpg.world.Expanse.GEMS) { node(Node.Kind.ROCK, "gem_deposit", "Uncertain gem deposit", Skill.MINING, 30, Items.get("uncut_emerald"), 45, 4, 1, g.cfg.rockRespawn, Blocks.COPPER_ROCK, r, 0, 0); nodes.get(nodes.size() - 1).setBlocks(g, true); }
        for (int[] r : new int[][]{rpg.world.Expanse.MARBLE_FACE, rpg.world.Expanse.MARBLE_FACE_2}) {
            node(Node.Kind.ROCK, "marble_face", "Marble face", Skill.MINING, 20, Items.get("marble_block"), 30, 3.6f, 3, g.cfg.rockRespawn, Blocks.MARBLE, r, 0, 0);
            nodes.get(nodes.size() - 1).setBlocks(g, true);
        }
        for (int[] p : rpg.world.Expanse.HERBS) node(Node.Kind.PATCH, "herb_patch", "Herb patch", Skill.HARVESTING, 1, Items.GUAM, 11, 2.2f, 3, g.cfg.patchRespawn, Blocks.HERB_PATCH, p, 0, 0);
        for (int[] p : rpg.world.Expanse.FLAXES) node(Node.Kind.PATCH, "flax", "Flax", Skill.HARVESTING, 5, Items.FLAX, 8, 1.8f, 4, g.cfg.patchRespawn, Blocks.FLAX_PATCH, p, 0, 0);
        for (int[] p : rpg.world.Expanse.BERRIES) node(Node.Kind.PATCH, "berry_bush", "Redberry bush", Skill.HARVESTING, 10, Items.BERRIES, 18, 2.4f, 3, g.cfg.patchRespawn, Blocks.BERRY_BUSH, p, 0, 0);
        for (Node node : nodes) if (node.kind == Node.Kind.PATCH && node.bx > 128 && node.def.matches("herb_patch|flax|berry_bush")) node.setBlocks(g, true);
    }

    /** Where the bank's teller sleeps (in the back of the hall). */
    public Furniture bankBed;

    /** The undertaker's chest: what the guards bring in from the dead, held until it is redeemed (GDD 3.12). */
    public Container custody;

    /** Every door, for the frame's physics read-back and the NPCs' door manners. */
    public List<Door> doors() { return java.util.Collections.unmodifiableList(doors); }
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
        GroundItem item=add(new GroundItem(g.state.newEntityId(),s,x,y,z));
        if(!s.def.natural && (s.def.slot==rpg.item.ItemDef.Slot.MAIN || s.def.slot==rpg.item.ItemDef.Slot.OFF)) {
            item.rotation=new float[]{0.9238795f,0.3826834f,0,0};
            item.wx=1.5f;item.wz=(Math.floorMod(item.id,7)-3)*0.3f;looseBody(item,true);
        }
        return item;
    }
    private void looseBody(GroundItem item,boolean lift) {
        var model=rpg.render.ItemModels.of(item.stack.def);int[] b=astratto.voxel.VoxRaster.bounds(model.vox());float unit=rpg.render.ItemModels.VOX;
        float hx=Math.max(0.025f,(b[3]-b[0])*unit/2),hy=Math.max(0.025f,(b[4]-b[1])*unit/2),hz=Math.max(0.025f,(b[5]-b[2])*unit/2);
        if(lift)item.y=Math.max(item.y,g.vox.groundY(item.x,item.y+2,item.z)+Math.max(hx,Math.max(hy,hz))+0.04f);
        item.rigid=g.game.reserve(astratto.bridge.AstrattoNative.KIND_RIGID);item.rigidSince=g.time;item.resumePhysics=false;
        var q=item.rotation;
        g.game.commands().rigidCreate(item.rigid,astratto.bridge.AstrattoNative.SHAPE_BOX,astratto.bridge.AstrattoNative.RIGID_DEBRIS|astratto.bridge.AstrattoNative.RIGID_NO_MOVERS,
                hx,hy,hz,Math.max(0.1f,item.stack.def.weight),item.x,item.y,item.z,q[0],q[1],q[2],q[3],item.vx,item.vy,item.vz,item.wx,item.wy,item.wz,0.8f,0.1f,0,null);
    }

    /**
     * One arrow, bolt or thrown blade where it ended up: lodged (held fast where it struck, point along dir), or loose -
     * a thin rigid body in the physics world, thrown on with a velocity, that tumbles and settles where it will.
     */
    public GroundItem dropAmmo(ItemStack one, float x, float y, float z, float[] dir, boolean lodged, float vx, float vy, float vz) {
        return dropLong(one, x, y, z, dir, lodged, vx, vy, vz);
    }

    /**
     * A long thing - an arrow, a thrown blade, a sword or a spear let go of - where it is, lodged or loose: loose, a thin
     * rigid body as long as the thing is, thrown on with a velocity, tumbling and settling where it will.
     */
    public GroundItem dropLong(ItemStack one, float x, float y, float z, float[] dir, boolean lodged, float vx, float vy, float vz) {
        GroundItem gi = add(new GroundItem(g.state.newEntityId(),one,x,y,z));
        float l = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        gi.dir = l < 1e-5f ? new float[]{0, 0, 1} : new float[]{dir[0] / l, dir[1] / l, dir[2] / l};
        gi.lodged = lodged;
        if (!lodged) {
            float[] q = rpg.combat.CombatSystem.alongY(gi.dir);
            float half = rpg.combat.CombatSystem.lyingLength(one.def) / 2;
            boolean ammo = one.def.family == rpg.item.ItemDef.Family.ARROW || one.def.family == rpg.item.ItemDef.Family.BOLT;
            gi.rigid = g.game.reserve(astratto.bridge.AstrattoNative.KIND_RIGID);
            gi.rigidSince = g.time;
            g.game.commands().rigidCreate(gi.rigid, astratto.bridge.AstrattoNative.SHAPE_BOX, astratto.bridge.AstrattoNative.RIGID_DEBRIS | astratto.bridge.AstrattoNative.RIGID_NO_MOVERS,
                    ammo ? 0.02f : 0.04f, half, ammo ? 0.02f : 0.04f, ammo ? 0.05f : Math.max(0.1f, one.def.weight), x, y, z, q[0], q[1], q[2], q[3],
                    vx, vy, vz, (float) (Math.random() - 0.5) * 6, (float) (Math.random() - 0.5) * 2, (float) (Math.random() - 0.5) * 6, 0.95f, 0.1f, 0, null);
        }
        return gi;
    }

    /** Loose ammunition follows its rigid body until it settles (or a few seconds pass), then lies still where it came to rest. */
    private void settleAmmo() {
        for (WorldObject o : list) {
            if(!(o instanceof GroundItem gi))continue;
            if(gi.removed){if(gi.rigid!=0){g.game.commands().release(gi.rigid);gi.rigid=0;}continue;}
            if(gi.resumePhysics && gi.rotation!=null)looseBody(gi,false);
            if(gi.rigid==0)continue;
            var body = g.lastRigids.body(gi.rigid);
            if (body == null) { if (g.time - gi.rigidSince > 1) { gi.rigid = 0; } continue; }
            gi.x = body.x; gi.y = body.y; gi.z = body.z;
            float qw = body.qw, qx = body.qx, qy = body.qy, qz = body.qz;
            // the body's long axis (its +y) in the world
            if(gi.rotation!=null){gi.rotation[0]=qw;gi.rotation[1]=qx;gi.rotation[2]=qy;gi.rotation[3]=qz;gi.vx=body.vx;gi.vy=body.vy;gi.vz=body.vz;gi.wx=body.wx;gi.wy=body.wy;gi.wz=body.wz;}
            else gi.dir = new float[]{2 * (qx * qy - qw * qz), 1 - 2 * (qx * qx + qz * qz), 2 * (qy * qz + qw * qx)};
            if (body.asleep || g.time - gi.rigidSince > 8 || gi.y < DevMap.FLOOR - 8) {
                g.game.commands().release(gi.rigid); gi.rigid = 0;
                if (gi.y < DevMap.FLOOR - 8) gi.y = g.vox.groundY(gi.x, DevMap.FLOOR + 2, gi.z) + 0.03f;
            }
        }
    }

    public void update(float dt) {
        for (Node n : nodes) n.update(g);
        for(var object:all())if(object instanceof FishingNet net)net.update(g);
        stall.update(g);
        for (WorldObject o : new ArrayList<>(list)) if (o instanceof Fire fire) fire.update(g, dt);
        list.removeIf(o -> o.removed && (o instanceof Fire || (o instanceof GroundItem gi && gi.id >= Ids.DYNAMIC)));
        doorsForNpcs();
        for (Door d : doors()) d.update(g, g.lastRigids);
        settleAmmo();
    }

    /** In the doorway, or by it bound for somewhere on its far side (an imp idling beside a door does not hold it open). */
    private static boolean passing(Door d, rpg.ai.Npc n) {
        double dd = Math.hypot(n.x - d.x, n.z - d.z);
        if (dd >= 2.6) return false;
        float bx = n.legs != null ? n.legsX : n.gx, bz = n.legs != null ? n.legsZ : n.gz;
        return dd < 1.3 || n.hasGoalPos && d.side(n.x, n.z) != d.side(bx, bz);
    }

    /** NPCs open unlocked doors on their way through and close them behind them. */
    private void doorsForNpcs() {
        for (Door d : doors()) {
            boolean near = false;
            rpg.ai.Npc by = null;
            // (0.2.3) near means passing through: in the doorway, or by it with somewhere to be on its far side - someone who
            // has come through and stopped just inside (a shopkeeper at her counter) no longer holds it open behind her
            for (var n : g.npcs) {
                if (!n.alive || !n.loaded || n.asleep() || n.reacting || g.law.inCustody(n)) continue;
                if (passing(d, n)) { near = true; by = n; }
            }
            // (0.2.2, pathfinding: goblins pushed at a shut shed door forever) a goblin, an imp, a troll - anything with
            // hands - opens an unlocked door too; a beast does not (its routes keep to open ways)
            if (g.monsters != null) for (var m : g.monsters.all()) if (m.alive && m.loaded && m.kind.form == rpg.monster.MonsterKind.Form.HUMANOID && passing(d, m)) { near = true; if (by == null) by = m; }
            if (!d.open && !d.locked && near) { d.setOpen(g, true, by, 0); d.autoOpened = true; d.autoCloseAt = g.time + 3; }
            // a locked door its owner (or whoever carries its key) is on their way through: unlocked, opened, and locked
            // again behind them once it is shut (gamefixes: people open their own doors)
            if (!d.open && d.locked) for (var n : g.npcs) {
                if (!n.alive || !n.loaded || n.asleep() || n.reacting || g.law.inCustody(n) || Math.hypot(n.x - d.x, n.z - d.z) > 2.4) continue;
                boolean keyHolder = d.owner == n.id || d.key != null && n.has(d.key, 1);
                // where they are bound (the trip's end, not the leg to the door's front) lies through it
                float bx = n.legs != null ? n.legsX : n.gx, bz = n.legs != null ? n.legsZ : n.gz;
                if (!keyHolder || !n.hasGoalPos || d.side(n.x, n.z) == d.side(bx, bz)) continue;
                d.setLocked(g, false); d.setOpen(g, true, n, 0); d.autoOpened = true; d.relock = true; d.autoCloseAt = g.time + 3;
                g.state.commit("DoorUnlocked", n.id, d.id, d.jurisdiction(), 0, d.name + " (passing through)");
                break;
            }
            if (d.open && d.autoOpened && !near && g.time > d.autoCloseAt && !d.blocked(g) && Math.hypot(g.player.x - d.x, g.player.z - d.z) > 2.2) {
                d.setOpen(g, false, null, 0); d.autoOpened = false;
            }
        }
    }
}
