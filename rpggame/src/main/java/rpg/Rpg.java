package rpg;

import astratto.bridge.AstrattoNative;
import astratto.bridge.Snapshots;
import astratto.game.Game;
import astratto.game.GameContext;
import astratto.game.GameModule;
import java.util.ArrayList;
import java.util.List;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.actor.Player;
import rpg.actor.PlayerController;
import rpg.ai.AiSystem;
import rpg.ai.Dialogue;
import rpg.ai.Npc;
import rpg.audio.Sfx;
import rpg.combat.CombatSystem;
import rpg.econ.Economy;
import rpg.interact.Interactions;
import rpg.interact.ItemActions;
import rpg.interact.WorldObject;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.law.Law;
import rpg.objects.Container;
import rpg.objects.Objects;
import rpg.quest.Quests;
import rpg.render.Fx;
import rpg.skill.Progression;
import rpg.skill.Skill;
import rpg.state.WorldState;
import rpg.ui.Hud;
import rpg.world.DevMap;
import rpg.world.Voxels;

/**
 * One play session on the devmap: the authoritative world state and every system that reads and changes it, run as a
 * Java gameplay module round the engine's tick. Order each tick: the player's intent, timed work, combat, NPC minds,
 * the world's objects, the economy, then presentation; after the engine's frame, bodies and agents are read back.
 */
public final class Rpg implements GameModule {
    public final Config cfg = new Config();
    public final WorldState state = new WorldState();
    public final Chat chat = new Chat();
    public final Game game;
    public final boolean scripted;
    public Voxels vox;
    /** (0.4.0) The map this session is played on (rpg.world.WorldMap): ShadowVeil, or the legacy devmap. */
    public rpg.world.WorldMap map = rpg.world.WorldMap.active();
    public Player player;
    public PlayerController controller;
    public final Roster npcs = new Roster();
    /**
     * (0.3.9) The people: a list that counts its own changes, so the lookup by id knows when it is stale (it was only
     * rebuilt when the count changed, and a person taken out and put back - a reload's roster restored - left the lookup
     * pointing at the one taken out).
     */
    public static final class Roster extends ArrayList<Npc> { int changes() { return modCount; } }
    public final List<Actor> dummies = new ArrayList<>();
    public Objects objects;
    public Interactions interact;
    /** (0.3.7) Lockpicking: the hidden-angle locks, the picks' wear, the player's minigame and everyone else's hands. */
    public rpg.interact.Lockpicking lockpicking;
    public ItemActions items;
    public CombatSystem combat;
    public rpg.combat.Reactions reactions;
    public rpg.combat.Falling falling;
    public rpg.skill.MovementPractice movementPractice;
    public rpg.combat.Gore gore;
    /** (0.3.8) The loose bones of the bodies that came apart. */
    public rpg.combat.Bones bones;
    public rpg.combat.Prayers prayers;
    public rpg.combat.Magic magic;
    public AiSystem ai;
    public rpg.animal.Animals animals;
    public Dialogue dialogue;
    public Law law;
    /** The player's death as it plays out (gamefixes), and the graves and undertaker that follow it (GDD 3.12). */
    public final PlayerDeath death = new PlayerDeath(this);
    public rpg.law.Undertaker undertaker;
    public rpg.law.Graveyards graveyards;
    /** The player's Honor (GDD 3.5). */
    public rpg.law.Honor honor;
    public Economy econ;
    public rpg.econ.Bank bank;
    public rpg.monster.Monsters monsters;
    /** The dungeons (gamefixes): their doors, traps, hoards and residents. */
    public rpg.dungeon.Dungeons dungeons;
    /** (0.3.7) The Shrines of Light: rest, the night passing, the wild's dead back, and where the player wakes. */
    public rpg.world.Shrines shrines;
    public Quests quests;
    public Progression prog;
    public rpg.actor.Traversal traversal;
    public rpg.actor.Ladders ladders;
    public rpg.mount.Riding riding;
    public rpg.actor.BodyCarry carrying;
    public rpg.law.BountyBoard bountyBoard;
    public rpg.objects.Glass glass;
    /** (0.3.8) The crates, boxes, barrels and signs, and the boards of the broken ones. */
    public rpg.objects.Breakables breakables;
    /** (0.3.8) What blows have knocked off the furniture, and the pieces of it lying about. */
    public rpg.objects.Wreckage wreckage;
    public rpg.world.Chemistry chemistry;
    /** What has been broken in the kingdoms and not yet mended (0.2.0). */
    public rpg.world.Repairs repairs;
    public rpg.ai.Traps traps;
    public rpg.world.Foliage foliage;
    /** (0.3.2) The kitchen gardens and orchards; the inns. */
    public rpg.world.Farming farming;
    public rpg.econ.Inns inns;
    public rpg.world.WaterContacts waterContacts;
    /** (0.3.4) The water system: volumes, surfaces, depths and currents; and the swimming that moves bodies through it. */
    public rpg.world.Fluids fluids;
    public rpg.actor.Swimming swimming;
    /** (0.3.5) The fish of every water: their stocks, and the live fish near the player. */
    public rpg.fishing.Fishery fishery;
    /** (0.3.5) The guilds, their halls on Guild Row, the Armory. */
    public rpg.econ.Guilds guilds;
    public final astratto.weather.TimeSystem timeSystem = new astratto.weather.TimeSystem();
    public Fx fx;
    public Sfx sfx;
    /** The score (set by the window's loop; null in automated runs). */
    public rpg.audio.Music music;
    public Hud ui;
    /** The profile being played (null in automated runs): where the game is saved. */
    public Profiles.Profile profile;
    public java.nio.file.Path savePath() { return profile != null ? profile.save() : SaveSystem.slot("slot1"); }
    public Invariants invariants;
    private boolean worldReady;
    public double time;
    /** The last tick's length (seconds of the world's clock). */
    public double lastDt = 1 / 60.0;
    public Snapshots.Bodies lastBodies = new Snapshots.Bodies();
    /** The rigid bodies and joints (Impulsis) as the last frame left them: doors, shards, things dropped. */
    public Snapshots.Rigids lastRigids = new Snapshots.Rigids();
    /** (0.4.0) The agents as the last frame left them (their route searches' cost: the stress test's report). */
    public Snapshots.Agents lastAgents = new Snapshots.Agents();
    /** Set when a block edit should refresh the minimap. */
    public boolean mapDirty = true;
    private final boolean audioOnRequest;
    private final float[] mix = new float[2 * 4096];

    public Rpg(Game game, String name, Appearance look, boolean scripted) {
        this.game = game;
        this.scripted = scripted;
        this.audioOnRequest = !game.engine().audioStats().device;
        state.reserveIdsBelow(Ids.DYNAMIC);
        player = new Player(Ids.PLAYER, name == null || name.isBlank() ? "Adventurer" : name);
        player.look = look == null ? new Appearance() : look.copy();
        player.fitBody();
        // a new character: every skill at 1 but Health, which starts at 10 (10 HP; gamefixes)
        player.skills.setLevel(Skill.HEALTH, 10);
    }

    @Override public void init(GameContext ctx) {
        vox = new Voxels(ctx.view());
        fluids = new rpg.world.Fluids(vox);
        // (0.4.0) the map in play: the devmap (legacy, for regression testing) or ShadowVeil (rpg.world.WorldMap)
        map = rpg.world.WorldMap.active();
        if (map.kind == rpg.world.WorldMap.Kind.DEVMAP) DevMap.currents(fluids); else rpg.world.shadowveil.ShadowVeil.currents(fluids);
        fx = new Fx();
        fx.ground = (x, y, z) -> vox.groundY(x, y, z);
        sfx = Sfx.shared(game);
        sfx.ground = a -> vox.block((int) Math.floor(a.x), (int) Math.floor(a.y - 0.05f), (int) Math.floor(a.z));
        if (!scripted) sfx.ambience("devmapambience.ogg", 0.35f);
        prog = new Progression(this);
        objects = new Objects(this);
        glass = new rpg.objects.Glass(this);
        breakables = new rpg.objects.Breakables(this);
        wreckage = new rpg.objects.Wreckage(this);
        interact = new Interactions(this);
        lockpicking = new rpg.interact.Lockpicking(this);
        items = new ItemActions(this);
        combat = new CombatSystem(this);
        reactions = new rpg.combat.Reactions(this);
        falling = new rpg.combat.Falling(this);
        movementPractice = new rpg.skill.MovementPractice(this);
        gore = new rpg.combat.Gore(this);
        bones = new rpg.combat.Bones(this);
        prayers = new rpg.combat.Prayers(this);
        magic = new rpg.combat.Magic(this);
        ai = new AiSystem(this);
        dialogue = new Dialogue(this);
        law = new Law(this);
        undertaker = new rpg.law.Undertaker(this);
        graveyards = new rpg.law.Graveyards(this);
        if (map.kind == rpg.world.WorldMap.Kind.DEVMAP) graveyards.plots.add(new rpg.law.Graveyards.Plot("DevCity graveyard", DevMap.ALPHA, 113, rpg.world.Ground.at(113, 89), 89, 8, 4));
        honor = new rpg.law.Honor(this);
        econ = new Economy(this);
        bank = new rpg.econ.Bank(this);
        quests = new Quests(this);
        traversal = new rpg.actor.Traversal(this);
        ladders = new rpg.actor.Ladders(this);
        swimming = new rpg.actor.Swimming(this);
        riding = new rpg.mount.Riding(this);
        carrying = new rpg.actor.BodyCarry(this);
        chemistry = new rpg.world.Chemistry(this);
        repairs = new rpg.world.Repairs(this);
        traps = new rpg.ai.Traps(this);
        foliage = new rpg.world.Foliage(this);
        farming = new rpg.world.Farming(this);
        inns = new rpg.econ.Inns(this);
        waterContacts = new rpg.world.WaterContacts(this);
        if (map.kind == rpg.world.WorldMap.Kind.DEVMAP) {
            timeSystem.regions.add(new astratto.weather.TimeSystem.Region("Devmap",-32,-32,rpg.world.DungeonLayout.WORLD_X+32,DevMap.SZ+32,16,astratto.weather.TimeSystem.Profile.NEUTRAL));
            timeSystem.fogVolumes.add(new astratto.weather.TimeSystem.Fog(60,rpg.world.Ground.at(60, 88)+0.5f,88,5,0.035f,0.38f,0.46f,0.5f));
        } else { rpg.world.shadowveil.ShadowVeil.atmosphere(timeSystem); rpg.world.shadowveil.ShadowVeil.mists(timeSystem); }
        timeSystem.weather.listener = new astratto.weather.Weather.Listener() {
            @Override public void thunder(float distance,float gain) { if(sfx!=null && sfx.has("thunder"))sfx.ui("thunder",gain*0.7f); }
            @Override public void strike(float x,float y,float z) {
                for(var receiver:chemistry.active)if((receiver.tags()&astratto.bridge.AstrattoNative.CHEM_CONDUCTIVE)!=0 && (receiver.state()&astratto.bridge.AstrattoNative.CHEM_WET)!=0
                        && Math.hypot(receiver.x()-x,receiver.z()-z)<18) { x=receiver.x();y=receiver.y();z=receiver.z();break; }
                chemistry.emit(null,astratto.bridge.AstrattoNative.CHEM_ELECTRIC,x,y,z,2,5,0.25f);
            }
        };
        bountyBoard = new rpg.law.BountyBoard(this);
        invariants = new Invariants(this);
        controller = new PlayerController(this);
        // the player
        player.x = map.spawnX; player.z = map.spawnZ; player.y = rpg.world.WorldMap.standY(player.x, player.z);
        player.body = ctx.reserve(AstrattoNative.KIND_MOVER);
        ctx.commands().moverCreate(player.body, player.x, player.y + 0.05f, player.z, player.radius, player.height, 0.6f, 0, player.height * rpg.actor.Body.CROUCH_HEIGHT);
        player.restore();
        starterKit();
        controller.snapTo(map.spawnYaw);
        fishery = new rpg.fishing.Fishery(this);
        guilds = new rpg.econ.Guilds(this);
        if (map.kind == rpg.world.WorldMap.Kind.DEVMAP) {
            // fixtures, agents, shops, dummies
            objects.buildFixtures();
            // (0.3.2) the gardens' plots and trees, the inns' fixtures: after every older fixture, so their ids hold
            objects.reserveAfter(id -> inns.init(farming.build(id)));
            // (0.3.5) the sea's fishing spots (after every older fixture, so their ids hold)
            objects.reserveAfter(this::seaSpots);
            // (0.3.5) Guild Row's fixtures, then the cotton field's plots
            objects.reserveAfter(id -> farming.buildLate(guilds.init(id)));
            // (0.3.8) the DevMansion's furnishings, doors, range and stores, and its orchard (after every older fixture)
            farming.buildLateOrchards(objects.mansion(Ids.ESTATE));
            // (0.3.9) every other room dressed for its use (numbered in the estates' band, after the DevMansion's)
            rpg.world.Interiors.furnish(this, rpg.world.Interiors.INTERIORS);
            // (0.3.9, optimisation) the routing graph built up front for play (tests and review runs build it as they go)
            if (!scripted) vox.navigation.warm();
        } else {
            // (0.4.0) ShadowVeil: terrain only in this version - its woods, outcrops and fishing waters are the fixtures
            objects.buildShadowveil(rpg.world.shadowveil.ShadowVeil.terrain());
            // (0.4.1) the farms' gardens' plots (the growers' work), in their own id band
            farming.buildShadowveil(Ids.SV_PLOTS);
        }
        for (rpg.objects.Node n : objects.nodes) if (n.kind == rpg.objects.Node.Kind.FISH) n.fishery = fishery;
        ai.init();
        animals = new rpg.animal.Animals(this);
        animals.init();
        monsters = new rpg.monster.Monsters(this);
        monsters.init();
        dungeons = new rpg.dungeon.Dungeons(this);
        if (map.kind == rpg.world.WorldMap.Kind.DEVMAP) dungeons.init();     // (0.4.0) ShadowVeil's dungeons come with its buildings
        shrines = new rpg.world.Shrines(this);
        shrines.init();
        for (rpg.objects.Furniture f : objects.furniture) if (f.kind == rpg.objects.Furniture.Kind.BED && f.owner != 0 && f.owner != player.id) f.name = nameOf(f.owner) + "'s bed";
        econ.init();
        inns.shops(econ);
        guilds.shops(econ);
        for (var inn : inns.all) for (var bed : inn.guestBeds) bed.name = "Guest bed";
        for (int i = 0; i < (map.kind == rpg.world.WorldMap.Kind.DEVMAP ? DevMap.DUMMIES.length : 0); i++) {
            Actor d = new Actor(Ids.DUMMY_0 + i, "Target dummy", 0);
            d.dummy = true; d.team = 0;
            d.x = DevMap.DUMMIES[i][0] + 0.5f; d.z = DevMap.DUMMIES[i][1] + 0.5f; d.y = DevMap.FLOOR; d.yaw = (float) Math.PI;
            d.skills.setLevel(Skill.DEFENSE, 1 + i * 30);
            if (i == 2) { d.eq.set(rpg.item.ItemDef.Slot.BODY, new ItemStack(Items.get("steel_body"), 1)); state.mint(Items.get("steel_body"), 1); d.name = "Armored dummy"; }
            d.restore();
            dummies.add(d);
            solidDummy(d);
        }
        repairs.clear(); // Fixture placement is construction, not damage.
        worldReady=true;
        invariants.baseline();
        state.commit("SessionStarted", player.id, 0, null, 0, player.name + " on " + map.title);
        chat.add(map.kind == rpg.world.WorldMap.Kind.DEVMAP ? "Welcome to the SHADOWVEIL devmap. Tab frees the mouse for menus. F5 saves; F6 loads. Adjust your controls in Settings."
                : "Welcome to SHADOWVEIL. Tab frees the mouse for menus. F5 saves; F6 loads. Adjust your controls in Settings.", Chat.Kind.GAME);
    }

    /**
     * A target dummy stands in the way (gamefixes: bodies walked through them): its post and sack as one fixed box, its
     * crossbar arms as another, the size Gfx draws them (it faces along z, so the arms run along x).
     */
    private void solidDummy(Actor d) {
        var c = game.commands();
        long post = game.reserve(astratto.bridge.AstrattoNative.KIND_RIGID), arms = game.reserve(astratto.bridge.AstrattoNative.KIND_RIGID);
        c.rigidCreate(post, astratto.bridge.AstrattoNative.SHAPE_BOX, astratto.bridge.AstrattoNative.RIGID_STATIC, 0.22f, 0.92f, 0.18f, 0,
                d.x, d.y + 0.92f, d.z, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.6f, 0.1f, 0, null);
        c.rigidCreate(arms, astratto.bridge.AstrattoNative.SHAPE_BOX, astratto.bridge.AstrattoNative.RIGID_STATIC, 0.52f, 0.05f, 0.05f, 0,
                d.x, d.y + 1.25f, d.z, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.6f, 0.1f, 0, null);
    }

    /** What a new character carries (gamefixes): wooden arms worn, a shortbow, the gathering tools, a little food and 25 gold, packed tidily. */
    private void starterKit() {
        // what is worn goes on first, so the pack holds only what is carried
        give(player, Items.WOODEN_SWORD, 1);
        give(player, Items.get("wooden_shield"), 1);
        give(player, Items.get("wooden_arrows"), 25);
        var r = items.equipFirst(player, Items.WOODEN_SWORD, rpg.item.ItemDef.Slot.MAIN, 0);
        var r2 = items.equipFirst(player, Items.get("wooden_shield"), rpg.item.ItemDef.Slot.OFF, 0);
        var r3 = items.equipFirst(player, Items.get("wooden_arrows"), rpg.item.ItemDef.Slot.AMMO, 0);
        if (r.rejected() || r2.rejected() || r3.rejected()) chat.warn("Starter gear could not be equipped.");
        give(player, Items.SHORTBOW, 1);
        give(player, Items.get("stone_axe"), 1);
        give(player, Items.get("stone_pickaxe"), 1);
        give(player, Items.TINDERBOX, 1);
        give(player, Items.FISHING_NET, 1);
        give(player, Items.POT, 1);
        give(player, Items.BUCKET, 1);
        give(player, Items.BREAD, 1);
        give(player, Items.SHRIMP, 1);
        give(player, Items.COINS, 25);
        for (rpg.item.Inventory grid : player.grids()) grid.sort();
    }

    /**
     * New items come into being for an actor (a reward, a craft, a catch): into what they carry, or - when nothing they
     * carry has room for its shape - onto the ground at their feet. Either way the item exists, once.
     */
    public void grant(Actor a, ItemStack s) {
        state.mint(s.def, s.amount);
        if (a.store(s)) return;
        objects.drop(s, a.x, a.y + 0.05f, a.z);
        if (a == player) chat.warn("No room for the " + s.def.name.toLowerCase() + ": it drops at your feet.");
    }

    public void give(Actor a, rpg.item.ItemDef d, int n) { grant(a, new ItemStack(d, n)); }

    /** (0.3.5) The sea's fishing spots: the wading strip (nets and pots), the islet's rocks, along the pier (rods) and its head over the deep (harpoons). */
    private long seaSpots(long id) {
        float[][] at = rpg.world.Coast.spots();
        rpg.item.ItemDef[] gear = {Items.FISHING_NET, Items.LOBSTER_POT, Items.FISHING_NET, Items.FISHING_ROD, Items.FISHING_ROD, Items.FISHING_ROD, Items.HARPOON, Items.HARPOON};
        for (int i = 0; i < at.length; i++) {
            rpg.item.ItemDef g = gear[i];
            String what = g == Items.HARPOON ? "harpoon, deep sea" : g == Items.LOBSTER_POT ? "pot, sea" : g == Items.FISHING_ROD ? "rod, sea" : "net, sea";
            var spot = new rpg.objects.Node(id++, "sea_spot", "Fishing spot (" + what + ")", rpg.objects.Node.Kind.FISH, Skill.FISHING, 1, Items.RAW_SHRIMP, 10, g == Items.HARPOON ? 5.5f : 4f, 0, 0, 0, (int) at[i][0], (int) at[i][1], 0, 0);
            spot.needs = g; spot.waters = i < 3 ? rpg.objects.Node.Waters.SHORE : i == 3 ? rpg.objects.Node.Waters.COAST : rpg.objects.Node.Waters.OFFSHORE;
            spot.y = DevMap.FLOOR - 0.1f;
            objects.addNode(spot);
        }
        return id;
    }

    // ------------------------------------------------------------------ the tick
    /** RPG_PROF set: each system's share of the update, summed (nanoseconds) - see {@link #profile()}. */
    public static final boolean PROF = System.getenv("RPG_PROF") != null || Boolean.getBoolean("rpg.prof");
    public static final String[] PROF_NAMES = {"time+death", "law.update", "controller.update", "interact.update", "combat.update", "gore.update", "prayers.update", "magic.update", "ai", "carrying.update", "chemistry.update", "traps.update", "dungeons.update", "foliage.update", "objects.update", "undertaker.update", "spareWatch", "glass.update", "econ.update", "prog.update", "fx.update", "animals", "monsters", "engine.frame (native)", "afterFrame"};
    public final long[] prof = new long[PROF_NAMES.length];
    private long profAt;
    private void mark(int i) { long now = System.nanoTime(); prof[i] += now - profAt; profAt = now; }
    /** The profile so far, the costliest first. */
    public String profile() {
        Integer[] order = new Integer[prof.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Long.compare(prof[b], prof[a]));
        StringBuilder b = new StringBuilder();
        for (int i : order) b.append(String.format("%-24s %8.2f ms%n", PROF_NAMES[i], prof[i] / 1e6));
        return b.toString();
    }

    @Override public void update(GameContext ctx, float dt) {
        if (PROF) profAt = System.nanoTime();
        time += dt;
        lastDt = dt;
        state.tick++;
        // (0.3.0) the Weekly System's clock: slower by day, quicker by night, paused, scaled, run by the distance the
        // player travels or carried on to an arrival - the calendar says how far it goes this frame
        var calendar = state.cal();
        if (calendar.mode() == astratto.time.Calendar.Mode.DISTANCE) {
            if (Float.isFinite(travelX)) calendar.travelled(Math.hypot(player.x - travelX, player.z - travelZ));
        } else calendar.tick(dt * cfg.minutesPerSecond);
        travelX = player.x; travelZ = player.z;
        state.minutes = calendar.totalMinutes();
        timeSystem.update(state.minutes,dt,player.x,player.eyeY(),player.z);
        game.commands().chemWind(timeSystem.weather.windX,timeSystem.weather.windZ);
        game.commands().chemWeather(timeSystem.weather.rain);
        chat.now = time; fx.now = time;
        death.update();
        if (PROF) mark(0);
        law.update();
        if (PROF) mark(1);
        controller.update(dt);
        if (PROF) mark(2);
        interact.update(dt);
        if (PROF) mark(3);
        combat.update(dt);
        if (PROF) mark(4);
        gore.update(dt);
        if (PROF) mark(5);
        prayers.update(dt);
        if (PROF) mark(6);
        magic.update(dt);
        if (PROF) mark(7);
        if (!aiDisabled()) { repairs.update(); ai.update(dt); if (PROF) mark(8); riding.update(dt); animals.update(dt); riding.landings(); if (PROF) mark(21); monsters.update(dt); if (PROF) mark(22); }
        else if (PROF) mark(8);
        carrying.update();
        if (PROF) mark(9);
        chemistry.update();
        if (PROF) mark(10);
        traps.update();
        if (PROF) mark(11);
        dungeons.update();
        if (PROF) mark(12);
        foliage.update();
        farming.update();
        inns.update();
        if (PROF) mark(13);
        objects.update(dt);
        if (PROF) mark(14);
        undertaker.update();
        if (PROF) mark(15);
        spareWatch();
        if (PROF) mark(16);
        glass.update();
        breakables.update();
        wreckage.update();
        bones.update();
        if (PROF) mark(17);
        econ.update();
        if (PROF) mark(18);
        prog.update();
        if (PROF) mark(19);
        fx.update(dt);
        if (PROF) mark(20);
        for (Actor d : dummies) { d.hp = d.maxHp(); d.cs.staggered = 0; }
        // (0.3.4) what a drink lent ebbs away
        float ebb = 1f / rpg.interact.ItemActions.ALE_EBB_SECONDS;
        player.skills.ebb(dt, ebb);
        for (Npc n : npcs) n.skills.ebb(dt, ebb);
        if (audioOnRequest) game.engine().renderAudio(mix, Math.min(4096, Math.max(1, Math.round(dt * 44100))));
    }

    @Override public void afterFrame(GameContext ctx) {
        if (PROF) mark(23);
        vox.afterCommands();
        lastBodies = ctx.bodies();
        lastRigids = ctx.rigids();
        Snapshots.Mover m = lastBodies.mover(player.body);
        // noclip: the body is where the flight put it, through whatever is in the way (the mover's own step is ignored)
        // (0.3.0) in the saddle the body is where the saddle is: its capsule was shoved out of the horse's and read back behind it
        if (m != null && !player.reacting && player.riding == 0 && !swimming.active(player) && !(ui != null && ui.console.noclip)) {
            float traveled = (float) Math.hypot(m.x - player.x, m.z - player.z);
            player.walked += traveled;
            player.x = m.x; player.y = m.y; player.z = m.z; player.vx = m.vx; player.vz = m.vz; player.grounded = m.grounded; player.crouched = m.crouched; player.onLadder=rpg.world.Blocks.ladder(vox.block((int)Math.floor(m.x),(int)Math.floor(m.y),(int)Math.floor(m.z))) || rpg.world.Blocks.ladder(vox.block((int)Math.floor(m.x),(int)Math.floor(m.y)-1,(int)Math.floor(m.z))); player.climbVelocity=m.vy;
            movementPractice.moved(traveled);
        }
        lastAgents = ctx.agents();
        ai.afterFrame(lastBodies, lastAgents);
        falling.afterFrame(lastBodies, (float) lastDt);
        reactions.afterFrame(lastBodies);
        animals.afterFrame(lastBodies, ctx.agents());
        monsters.afterFrame(lastBodies);
        swimming.others((float) lastDt);
        controller.syncCamera();
        waterContacts.update();
        if (fishery != null) fishery.update((float) lastDt);
        if (PROF) mark(24);
    }

    @Override public void path(int request, int[] cells, int expansions) { controller.onPath(request, cells); }

    /** A body Sydney holds struck the ground: a thud (louder the harder), and a hard fall on the head hurts. */
    @Override public void ragdollImpact(long ragdoll, int point, float speed, float impulse, float x, float y, float z, int block) {
        if (sfx != null && speed > 2.6f) sfx.play("body.fall", x, y, z, Math.min(1f, 0.25f + speed / 9f));
        if (speed < 6.5f) return;
        for (Npc n : npcs) if (n.ragdoll == ragdoll && n.alive && n.reacting && point == astratto.bridge.AstrattoNative.RD_HEAD) n.hp = Math.max(1, n.hp - (speed - 6.5f) * 3 * rpg.combat.CombatMath.HP_SCALE);
    }
    @Override public void agentAction(long agent, int action, int previous) { ai.agentAction(agent, action, previous); }
    @Override public void agentSees(long agent, long other) { ai.agentSees(agent, other); }
    @Override public void agentLost(long agent, long other) { ai.agentLost(agent, other); }
    @Override public void agentArrived(long agent, long target) { ai.agentArrived(agent, target); }
    @Override public void agentStuck(long agent) { ai.agentStuck(agent); }
    @Override public void agentHeard(long agent, int loudness) { ai.agentHeard(agent, loudness); }
    /** Impulsis: two things met at speed - what is heard, and a pane broken by a body flung into it. */
    @Override public void rigidImpact(long a, long b, long mover, float x, float y, float z, float nx, float ny, float nz, float speed, float impulse, int ma, int mb) {
        if (ma == rpg.objects.Glass.MATERIAL_GLASS) glass.clink(x, y, z, speed);
        else if (ma == rpg.combat.Bones.MATERIAL_BONE) bones.clatter(x, y, z, speed);
        else if (ma == rpg.objects.Breakables.MATERIAL_WOOD && breakables.byBody(a) != null) breakables.impact(a, x, y, z, nx, ny, nz, speed, impulse);
        else if (mb == rpg.objects.Breakables.MATERIAL_WOOD && b != 0 && breakables.byBody(b) != null) breakables.impact(b, x, y, z, -nx, -ny, -nz, speed, impulse);
        else if (ma == rpg.objects.Breakables.MATERIAL_WOOD) { if (sfx != null && speed > 1.3f) sfx.play("wood.light", x, y, z, Math.min(0.4f, speed * 0.07f), 1.2f, 14); }
        else if (ma == rpg.objects.Door.MATERIAL_WOOD_DOOR && speed > 1.2f && sfx != null) sfx.play(speed > 3 ? "wood" : "wood.light", x, y, z, Math.min(0.7f, speed * 0.12f));
        else if (sfx != null && speed > 1.5f) sfx.play(speed > 4 ? "wood" : "generic", x, y, z, Math.min(0.6f, speed * 0.1f));
        if (b == 0 && mover == 0 && rpg.world.Blocks.glass(mb) && speed > 4.5f && ma != rpg.objects.Glass.MATERIAL_GLASS) {
            int bx = (int) Math.floor(x - nx * 0.06f), by = (int) Math.floor(y - ny * 0.06f), bz = (int) Math.floor(z - nz * 0.06f);
            glass.shatter(bx, by, bz, x, y, z, -nx, -ny, -nz, speed / 6f, null);
        }
    }

    @Override public void error(int command, int status, long handle) { if (cfg != null) System.err.println("[engine] command " + command + " refused: " + AstrattoNative.status(status) + " (handle " + Long.toHexString(handle) + ")"); }

    // ------------------------------------------------------------------ the score
    /**
     * What the moment feels like, for the music: a fight beats being hunted, which beats the lie of the land. Read from
     * the simulation, never scripted: anyone actually fighting the player close by (or a blow traded a moment ago), the
     * law or a foe searching for them, then the place they stand in and whether it is night.
     */
    public rpg.audio.Music.Mood mood() {
        if (!player.alive) return rpg.audio.Music.Mood.NONE;
        boolean fight = time - player.cs.lastHurt < 7 && player.cs.lastAttacker != 0 || time - player.cs.lastAttack < 5 && combatNear(14);
        if (!fight) fight = combatNear(18);
        if (fight) return rpg.audio.Music.Mood.COMBAT;
        for (Npc n : npcs) if (n.alive && n.loaded && n.hunt != null && n.hunt.target == player.id && n.distTo(player) < 45) return rpg.audio.Music.Mood.HUNTED;
        double h = state.hourOfDay();
        boolean night = h < 5.5 || h >= 20.5;
        if (DevMap.inDungeon(player.x, player.z)) return rpg.audio.Music.Mood.DUNGEON;
        // standing at a town's altar (DevTown's shrine, DevPalace's temple) the hymn plays
        if (Math.hypot(player.x - DevMap.ALTAR[0] - 0.5, player.z - DevMap.ALTAR[1] - 0.5) < 9
                || Math.hypot(player.x - rpg.world.Expanse.ALTAR[0], player.z - rpg.world.Expanse.ALTAR[1]) < 9) return rpg.audio.Music.Mood.SACRED;
        if (DevMap.PALACE.equals(DevMap.jurisdiction(player.x, player.z))) return night ? rpg.audio.Music.Mood.PALACE_NIGHT : rpg.audio.Music.Mood.PALACE_DAY;
        boolean wood = player.x >= DevMap.WILD_X0 && player.x <= DevMap.WILD_X1 && player.z >= DevMap.WILD_Z0 && player.z <= DevMap.WILD_Z1;
        if (wood) return night ? rpg.audio.Music.Mood.WOOD_NIGHT : rpg.audio.Music.Mood.WOOD_DAY;
        boolean town = !DevMap.WILDS.equals(DevMap.jurisdiction(player.x, player.z));
        if (town) return night ? rpg.audio.Music.Mood.TOWN_NIGHT : rpg.audio.Music.Mood.TOWN_DAY;
        return night ? rpg.audio.Music.Mood.OPEN_NIGHT : rpg.audio.Music.Mood.OPEN_DAY;
    }

    /** Someone (a person or an animal) fighting the player within a distance. */
    private boolean combatNear(float within) {
        for (Npc n : npcs) if (n.alive && n.loaded && n.goal == rpg.ai.Brain.Goal.FIGHT && n.fightTarget == player.id && n.distTo(player) < within) return true;
        if (animals != null) for (rpg.animal.Animal a : animals.all()) if (a.alive && a.loaded && (a.goal == rpg.animal.Animal.Goal.DEFEND || a.goal == rpg.animal.Animal.Goal.HUNT) && (a.other == player.id || a.threat == player.id) && a.distTo(player) < within) return true;
        return false;
    }

    // ------------------------------------------------------------------ queries
    /** Everyone who can be hit: the player, living loaded NPCs, the dummies. */
    /**
     * Everyone who can act (the player, living loaded people, dummies, animals and monsters). (0.3.3, aggressive
     * optimization) it was a fresh list on every one of its many calls - several per person per frame; now one list a
     * tick, rebuilt whenever someone has died, gone or come since (a rebuild is a new list, so a loop over the old one
     * is never disturbed). Read-only: callers copy it if they keep it.
     */
    public List<Actor> actors() {
        int sizes = npcs.size() * 31 + dummies.size() * 7 + (animals == null ? 0 : animals.all().size() * 131) + (monsters == null ? 0 : monsters.all().size() * 1009);
        if (actorsCache != null && actorsAt == time && actorsSizes == sizes && actorsLive == liveCount()) return actorsCache;
        List<Actor> l = new ArrayList<>(actorsCache == null ? 64 : actorsCache.size() + 8);
        l.add(player);
        for (Npc n : npcs) if (n.alive && n.loaded) l.add(n);
        l.addAll(dummies);
        if (animals != null) for (rpg.animal.Animal a : animals.all()) if (a.alive && a.loaded) l.add(a);
        if (monsters != null) for (rpg.monster.Monster m : monsters.all()) if (m.alive && m.loaded) l.add(m);
        actorsCache = java.util.Collections.unmodifiableList(l); actorsAt = time; actorsSizes = sizes; actorsLive = liveCount();
        return actorsCache;
    }
    private List<Actor> actorsCache;
    private double actorsAt = -1;
    private int actorsSizes, actorsLive;
    /** How many of the listed kinds are alive and loaded now (a cheap check that the tick's list still holds). */
    private int liveCount() {
        int c = 0;
        for (Npc n : npcs) if (n.alive && n.loaded) c++;
        if (animals != null) for (rpg.animal.Animal a : animals.all()) if (a.alive && a.loaded) c += 1 << 10;
        if (monsters != null) for (rpg.monster.Monster m : monsters.all()) if (m.alive && m.loaded) c += 1 << 20;
        return c;
    }

    public Actor actor(long id) {
        if (id == player.id) return player;
        // (0.3.5, optimization) the people by id: looked up thousands of times a frame, it was a walk of the whole roster
        if (npcs.changes() != npcIndexed) { npcIndex.clear(); for (Npc n : npcs) npcIndex.put(n.id, n); npcIndexed = npcs.changes(); }
        Npc known = npcIndex.get(id);
        if (known != null && known.id == id) return known;
        for (Npc n : npcs) if (n.id == id) { npcIndex.put(id, n); return n; }
        for (Actor d : dummies) if (d.id == id) return d;
        if (monsters != null) { Actor m = monsters.byId(id); if (m != null) return m; }
        if (animals != null) return animals.byId(id);
        return null;
    }

    private final java.util.Map<Long, Npc> npcIndex = new java.util.HashMap<>();
    private int npcIndexed = -1;
    public final java.util.Map<Long,String> rememberedNames=new java.util.LinkedHashMap<>();
    public void rememberName(long id,String name) {
        if(id==0 || name==null || name.isBlank())return;
        rememberedNames.put(id,name);
        while(rememberedNames.size()>2048)rememberedNames.remove(rememberedNames.keySet().iterator().next());
    }
    public String nameOf(long id) {
        if(id==0)return "nobody";
        Actor actor=actor(id);WorldObject object=objects==null?null:objects.get(id);
        if(actor!=null)rememberName(id,actor.name);else if(object!=null)rememberName(id,object.name);
        return rememberedNames.getOrDefault(id,"someone");
    }

    public boolean notarget() { return ui != null && ui.console.invisible; }
    /** The console's ai_disabled: every mind is frozen. */
    public boolean aiDisabled() { return ui != null && ui.console.on("ai_disabled"); }

    /** Who a stolen stack was taken from, by name ("Mara and Cole"). */
    public String victims(ItemStack s) {
        List<String> names = new ArrayList<>();
        for (long v : s.victims().keySet()) names.add(nameOf(v));
        if (names.isEmpty()) return nameOf(s.owner);
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    /** Whether the actor can physically reach an object from where they stand (line of sight to it, or to its own cells). */
    public boolean canReach(Actor a, WorldObject o) {
        float ex = a.x, ey = a.eyeY(), ez = a.z;
        float dx = o.x - ex, dy = o.y - ey, dz = o.z - ez, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        var h = vox.ray(ex, ey, ez, dx, dy, dz, d, false);
        if (!h.hit || h.dist >= d - o.focusRadius() - 0.25f) return true;
        return Math.hypot(h.bx + 0.5f - o.x, h.bz + 0.5f - o.z) < 1.3f && Math.abs(h.by + 0.5f - o.y) < 2.6f;
    }

    // ------------------------------------------------------------------ changing the world
    public void setBlock(int x, int y, int z, int id) {
        int before=vox.block(x,y,z);
        if(worldReady && repairs!=null && before!=id && !rpg.world.Blocks.objectCell(before) && before!=rpg.world.Blocks.DOOR && before!=rpg.world.Blocks.LOCKED_DOOR
                && (id==rpg.world.Blocks.AIR || rpg.world.Blocks.charred(id) || rpg.world.Blocks.dual(before) && id==rpg.world.Blocks.survivorOf(before)))repairs.broken(x,y,z,before,"damage");
        vox.edited(x,y,z,before,id);
        game.commands().blockSet(x, y, z, id); mapDirty = true; mapColumns.add(x << 16 | (z & 0xFFFF));
        if (chemistry != null && (id == rpg.world.Blocks.AIR || id == rpg.world.Blocks.EMBERS || id == rpg.world.Blocks.WATER)) chemistry.cleared(x, y, z);
    }
    /** The map columns edited since the minimap last looked (x << 16 | z); a whole redraw when mapDirty is set without them. */
    public final java.util.Set<Integer> mapColumns = new java.util.HashSet<>();

    /** A permitted skill check's random input, derived from the request (so a replay gets the same outcome). */
    public double roll(long requestId, long salt) {
        long z = requestId * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L + 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    public void noise(float x, float y, float z, float radius, float loudness, int team) { game.commands().noise(x, y, z, radius, loudness, team); }
    /** A noise someone made: the engine's ears hear it, and NPCs in earshot grow aware of whoever made it. */
    public void noise(Actor source, float x, float y, float z, float radius, float loudness) {
        noise(x, y, z, radius, loudness, source == null ? 0 : source.team);
        if (source != null) ai.stealth.hear(source, x, y, z, radius, loudness);
        if (source != null && animals != null) animals.hear(source, x, y, z, radius);
        if (source != null && monsters != null) monsters.hear(source, x, y, z, radius);
    }

    /**
     * (0.3.1) A footfall's mark on the world, for anyone near enough the player to see it: dust off dry earth and sand,
     * a spatter off soaked open ground in the rain, a powder of snow where it lies (see Fx.footfall).
     */
    public void footfall(Actor a, float force) {
        if (fx == null || a == null || !a.grounded || Math.hypot(a.x - player.x, a.z - player.z) > 32) return;
        int bx = (int) Math.floor(a.x), by = (int) Math.floor(a.y - 0.05f), bz = (int) Math.floor(a.z);
        int below = vox.block(bx, by, bz);
        var w = timeSystem.weather;
        boolean open = vox.skyLight(bx, by + 1, bz) > 0.7f;
        if (open && w.snowCover > 0.25f) { fx.footfall(a.x, a.y, a.z, 2, 0.92f, 0.94f, 0.98f, force); return; }
        if (open && (w.rain > 0.25f || w.wetness > 0.5f) && below != rpg.world.Blocks.AIR) { fx.footfall(a.x, a.y, a.z, 1, 0, 0, 0, force * Math.max(w.rain, w.wetness * 0.6f)); return; }
        var m = rpg.world.Blocks.material(below);
        if (below == rpg.world.Blocks.SAND) fx.footfall(a.x, a.y, a.z, 0, 0.82f, 0.74f, 0.55f, force * 1.2f);
        else if (m == rpg.world.Blocks.Material.EARTH && below != rpg.world.Blocks.GRASS && below != rpg.world.Blocks.MUD && force > 0.45f) fx.footfall(a.x, a.y, a.z, 0, 0.55f, 0.45f, 0.34f, force);
    }

    public void teleport(Actor a, float x, float y, float z) {
        if (falling != null) falling.reset(a);
        // a hang (or a ladder) held over a teleport re-pinned the body to the old edge the next frame: a respawn put one back on the ledge one died on
        if (traversal != null) traversal.cancel(a);
        if (ladders != null) ladders.cancel(a);
        if (swimming != null) swimming.cancel(a);
        if (riding != null && a.riding != 0) riding.dismount(a, false);
        if (a == player && movementPractice != null) movementPractice.reset();
        if (reactions != null && !(a instanceof rpg.animal.Animal)) reactions.cancel(a);
        if (a.body != 0) game.commands().moverPlace(a.body, x, y + 0.05f, z, 0, 0, 0);
        a.x = x; a.y = y; a.z = z;
        a.poseReset=true; a.handAt=-99; a.throwPlan.solvedAt=-99;
        if(chemistry!=null)chemistry.register(a);
        if (a instanceof Npc n) { n.routeBook.cancel(); if (reactions != null) reactions.cancel(n); n.legs = null; n.hasGoalPos = false; n.resetMotion(); }
        interact.cancel(a, "moved");
    }

    // ------------------------------------------------------------------ death and time
    public void onKilled(Actor v, Actor killer, long cause) {
        falling.reset(v);v.morphT=v.chronoT=0;
        if (traversal != null) traversal.cancel(v);
        if (ladders != null) ladders.cancel(v);
        if (riding != null && v.riding != 0) riding.dismount(v, false);
        if (prayers != null) prayers.clear(v);
        law.onKilled(v, killer, cause);
        if (v instanceof rpg.monster.Monster mo) monsters.onKilled(mo, killer);
        if (v instanceof Npc n) ai.onKilled(n, killer);
        if (v instanceof rpg.animal.Animal an) animals.onKilled(an, killer, cause);
        if (v == player) {
            if (scripted) System.out.printf("      [death] the player was killed by %s at (%.1f, %.1f), t %.1f%n", killer == null ? "?" : killer.name, player.x, player.z, time);
            player.diedAt = time;
            interact.cancel(player, "died");
            // the death is seen through (gamefixes): the body falls, the camera finds the killer, the world goes grey;
            // the grave is made only when the player chooses to go on
            death.start(killer);
            state.commit("PlayerDeath", killer == null ? 0 : killer.id, player.id, DevMap.jurisdiction(player.x, player.z), cause,
                    DevMap.WILDS.equals(DevMap.jurisdiction(player.x, player.z)) ? "OUTSIDE_CITY" : "INSIDE_CITY " + DevMap.jurisdiction(player.x, player.z));
        }
    }

    /**
     * A grave where the body lay (GDD 3.12): everything carried and worn, packed into one container that stands for ten
     * minutes. Secured (inside the walls), only the guard who carries it in may touch it; exposed
     * (outside), anyone who finds it may. What will not fit lies on the ground beside it, never lost. Null if there was
     * nothing to keep.
     */
    public Container makeGrave(float x, float z, long killer, boolean secured) {
        int honorAtDeath = honor.score;
        // the wanted pay for it first: the law takes its share before the rest goes to the grave
        law.forfeitOnDeath(player);
        if (player.inv.isEmpty() && player.eq.stacks().isEmpty()) return null;
        Container grave = objects.add(new Container(state.newEntityId(), secured ? "death_claim" : "grave", player.name + "'s belongings", secured ? "claim" : "grave", secured ? DevMap.CUSTODY_CHEST[0] : x, rpg.world.Ground.at(secured ? DevMap.CUSTODY_CHEST[0] : x, secured ? DevMap.CUSTODY_CHEST[1] : z) + 0.3f, secured ? DevMap.CUSTODY_CHEST[1] : z, 320, player.id));
        grave.ownerOnly = secured;
        grave.secured = secured;
        grave.killer = killer;
        grave.expiresAt = time + rpg.law.Undertaker.GRAVE_SECONDS;
        // the memorial is the Honor of the moment of death (GDD 3.5): it keeps that inscription whatever comes after
        grave.honor = honorAtDeath;
        grave.name = player.name + (secured?"'s belongings":"'s grave");
        for (int i = 0; i < player.inv.size(); i++) {
            ItemStack s = player.inv.get(i);
            if (s == null) continue;
            player.inv.set(i, null);
            if (!grave.contents.add(s)) objects.drop(s, x + 0.5f, rpg.world.Ground.at(x + 0.5f, z + 0.3f) + 0.05f, z + 0.3f);
        }
        for (rpg.item.ItemDef.Slot slot : rpg.item.ItemDef.Slot.values()) {
            ItemStack s = player.eq.get(slot);
            if (s == null) continue;
            player.eq.set(slot, null);
            if (!grave.contents.add(s)) objects.drop(s, x - 0.5f, rpg.world.Ground.at(x - 0.5f, z + 0.3f) + 0.05f, z + 0.3f);
        }
        state.commit("GraveCreated", player.id, grave.id, DevMap.jurisdiction(x, z), 0, (secured ? "secured; " : "exposed; ") + grave.contents.stacks());
        if (secured) undertaker.secureClaim(grave);
        return grave;
    }

    /**
     * Sparing a beaten foe (GDD 3.5): an outlaw the player has beaten into flight - badly hurt, running from them, the
     * player close enough to finish it - and then let go: if the player strikes no more blows and the outlaw is still alive
     * forty seconds later, that restraint is honorable. Striking again forfeits it for the day.
     */
    private void spareWatch() {
        for (Npc n : npcs) {
            if (!n.alive || !(n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE)) { n.spareSince = 0; continue; }
            if (n.spareSince < 0) continue;
            boolean beaten = n.goal == rpg.ai.Brain.Goal.FLEE && n.hp < n.maxHp() * 0.35f && n.cs.lastAttacker == player.id && player.alive;
            if (n.spareSince == 0) { if (beaten && n.distTo(player) < 8) n.spareSince = time; continue; }
            if (time - n.spareSince > 40) { honor.spared(n.id); ai.rumors.deed("let a beaten outlaw go", n.id); n.spareSince = -1; }
        }
    }

    /** Wakes the player at the graveyard (GDD 3.12: the nearest reachable one; the devmap has the one). */
    public void respawn() {
        player.alive = true;
        player.diedAt = -1;
        player.restore();
        chemistry.cleanse(player);
        player.cs.cancel();
        player.cs.lastAttacker = 0;
        if (gore != null) gore.healed(player);
        // (0.3.7) by the Shrine of Light the player last rested at, else the town's graveyard
        float[] wake = shrines.wakeSpot();
        teleport(player, wake[0], rpg.world.Ground.at(wake[0], wake[1]), wake[1]);
        controller.snapTo(0);
        law.playerRespawned();
        state.commit("PlayerRespawned", player.id, 0, DevMap.ALPHA, 0, "at the graveyard");
        chat.say("You wake among the graves, with nothing but the clothes you were born to.");
    }

    private float travelX = Float.NaN, travelZ;
    /** Game minutes passing per real second now (the Weekly System's rate at this hour, times the configured speed). */
    public double minuteRate() { return state.cal().minutesPerSecond() * cfg.minutesPerSecond; }

    /** Skips game time (waiting, sentences, the debug clock): the catch-up coordinator. */
    public void advanceTime(double minutes, String why) {
        state.minutes += minutes;
        state.commit("TimeAdvanced", player.id, 0, null, 0, String.format("+%.0f min (%s) -> %s", minutes, why, state.clockText()));
        for (Npc n : npcs) {
            n.hunger = Math.min(1, n.hunger + (float) minutes / 900f);   // the same rate as in play: full in fifteen hours
            n.nextThink = 0;
        }
        animals.catchUp(minutes);
        // a shipment on the road arrives if the skipped time covers its trip
        var s = econ.active();
        if (s != null && s.status == Economy.Status.IN_TRANSIT && minutes >= 20) {
            Npc carter = (Npc) actor(Ids.CARTER);
            if (carter != null && carter.alive) { teleport(carter, DevMap.CARAVAN_B[0], rpg.world.Ground.at(DevMap.CARAVAN_B[0], DevMap.CARAVAN_B[1]), DevMap.CARAVAN_B[1]); econ.deliver(s); }
        }
    }
}
