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
    public Player player;
    public PlayerController controller;
    public final List<Npc> npcs = new ArrayList<>();
    public final List<Actor> dummies = new ArrayList<>();
    public Objects objects;
    public Interactions interact;
    public ItemActions items;
    public CombatSystem combat;
    public rpg.combat.Reactions reactions;
    public rpg.combat.Falling falling;
    public rpg.skill.MovementPractice movementPractice;
    public rpg.combat.Gore gore;
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
    public Quests quests;
    public Progression prog;
    public rpg.actor.Traversal traversal;
    public rpg.actor.BodyCarry carrying;
    public rpg.law.BountyBoard bountyBoard;
    public rpg.objects.Glass glass;
    public rpg.world.Chemistry chemistry;
    /** What has been broken in the kingdoms and not yet mended (0.2.0). */
    public rpg.world.Repairs repairs;
    public rpg.ai.Traps traps;
    public rpg.world.Foliage foliage;
    public rpg.world.WaterContacts waterContacts;
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
    public double time;
    /** The last tick's length (seconds of the world's clock). */
    public double lastDt = 1 / 60.0;
    public Snapshots.Bodies lastBodies = new Snapshots.Bodies();
    /** The rigid bodies and joints (Impulsis) as the last frame left them: doors, shards, things dropped. */
    public Snapshots.Rigids lastRigids = new Snapshots.Rigids();
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
        fx = new Fx();
        sfx = Sfx.shared(game);
        sfx.ground = a -> vox.block((int) Math.floor(a.x), (int) Math.floor(a.y - 0.05f), (int) Math.floor(a.z));
        if (!scripted) sfx.ambience("devmapambience.ogg", 0.35f);
        prog = new Progression(this);
        objects = new Objects(this);
        glass = new rpg.objects.Glass(this);
        interact = new Interactions(this);
        items = new ItemActions(this);
        combat = new CombatSystem(this);
        reactions = new rpg.combat.Reactions(this);
        falling = new rpg.combat.Falling(this);
        movementPractice = new rpg.skill.MovementPractice(this);
        gore = new rpg.combat.Gore(this);
        prayers = new rpg.combat.Prayers(this);
        magic = new rpg.combat.Magic(this);
        ai = new AiSystem(this);
        dialogue = new Dialogue(this);
        law = new Law(this);
        undertaker = new rpg.law.Undertaker(this);
        graveyards = new rpg.law.Graveyards(this);
        graveyards.plots.add(new rpg.law.Graveyards.Plot("DevCity graveyard", DevMap.ALPHA, 113, DevMap.FLOOR, 89, 8, 4));
        honor = new rpg.law.Honor(this);
        econ = new Economy(this);
        bank = new rpg.econ.Bank(this);
        quests = new Quests(this);
        traversal = new rpg.actor.Traversal(this);
        carrying = new rpg.actor.BodyCarry(this);
        chemistry = new rpg.world.Chemistry(this);
        repairs = new rpg.world.Repairs(this);
        traps = new rpg.ai.Traps(this);
        foliage = new rpg.world.Foliage(this);
        waterContacts = new rpg.world.WaterContacts(this);
        timeSystem.regions.add(new astratto.weather.TimeSystem.Region("Devmap",-32,-32,rpg.world.DungeonLayout.WORLD_X+32,DevMap.SZ+32,16,astratto.weather.TimeSystem.Profile.NEUTRAL));
        timeSystem.fogVolumes.add(new astratto.weather.TimeSystem.Fog(60,DevMap.FLOOR+0.5f,88,5,0.035f,0.38f,0.46f,0.5f));
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
        player.x = DevMap.SPAWN_X; player.z = DevMap.SPAWN_Z; player.y = DevMap.FLOOR;
        player.body = ctx.reserve(AstrattoNative.KIND_MOVER);
        ctx.commands().moverCreate(player.body, player.x, player.y + 0.05f, player.z, player.radius, player.height, 0.6f, 0, player.height * rpg.actor.Body.CROUCH_HEIGHT);
        player.restore();
        starterKit();
        controller.snapTo((float) Math.PI);
        // fixtures, agents, shops, dummies
        objects.buildFixtures();
        ai.init();
        animals = new rpg.animal.Animals(this);
        animals.init();
        monsters = new rpg.monster.Monsters(this);
        monsters.init();
        dungeons = new rpg.dungeon.Dungeons(this);
        dungeons.init();
        for (rpg.objects.Furniture f : objects.furniture) if (f.kind == rpg.objects.Furniture.Kind.BED && f.owner != 0 && f.owner != player.id) f.name = nameOf(f.owner) + "'s bed";
        econ.init();
        for (int i = 0; i < DevMap.DUMMIES.length; i++) {
            Actor d = new Actor(Ids.DUMMY_0 + i, "Target dummy", 0);
            d.dummy = true; d.team = 0;
            d.x = DevMap.DUMMIES[i][0] + 0.5f; d.z = DevMap.DUMMIES[i][1] + 0.5f; d.y = DevMap.FLOOR; d.yaw = (float) Math.PI;
            d.skills.setLevel(Skill.DEFENSE, 1 + i * 30);
            if (i == 2) { d.eq.set(rpg.item.ItemDef.Slot.BODY, new ItemStack(Items.get("steel_body"), 1)); state.mint(Items.get("steel_body"), 1); d.name = "Armored dummy"; }
            d.restore();
            dummies.add(d);
            solidDummy(d);
        }
        invariants.baseline();
        state.commit("SessionStarted", player.id, 0, null, 0, player.name + " on the devmap");
        chat.add("Welcome to the RPGGAME devmap. Tab frees the mouse for menus. F5 saves; F6 loads. Adjust your controls in Settings.", Chat.Kind.GAME);
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

    // ------------------------------------------------------------------ the tick
    /** RPG_PROF set: each system's share of the update, summed (nanoseconds) - see {@link #profile()}. */
    public static final boolean PROF = System.getenv("RPG_PROF") != null;
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
        state.minutes += dt * cfg.minutesPerSecond;
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
        if (!aiDisabled()) { repairs.update(); ai.update(dt); if (PROF) mark(8); animals.update(dt); if (PROF) mark(21); monsters.update(dt); if (PROF) mark(22); }
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
        if (PROF) mark(13);
        objects.update(dt);
        if (PROF) mark(14);
        undertaker.update();
        if (PROF) mark(15);
        spareWatch();
        if (PROF) mark(16);
        glass.update();
        if (PROF) mark(17);
        econ.update();
        if (PROF) mark(18);
        prog.update();
        if (PROF) mark(19);
        fx.update(dt);
        if (PROF) mark(20);
        for (Actor d : dummies) { d.hp = d.maxHp(); d.cs.staggered = 0; }
        if (audioOnRequest) game.engine().renderAudio(mix, Math.min(4096, Math.max(1, Math.round(dt * 44100))));
    }

    @Override public void afterFrame(GameContext ctx) {
        if (PROF) mark(23);
        vox.afterCommands();
        lastBodies = ctx.bodies();
        lastRigids = ctx.rigids();
        Snapshots.Mover m = lastBodies.mover(player.body);
        // noclip: the body is where the flight put it, through whatever is in the way (the mover's own step is ignored)
        if (m != null && !player.reacting && !(ui != null && ui.console.noclip)) {
            float traveled = (float) Math.hypot(m.x - player.x, m.z - player.z);
            player.walked += traveled;
            player.x = m.x; player.y = m.y; player.z = m.z; player.vx = m.vx; player.vz = m.vz; player.grounded = m.grounded; player.crouched = m.crouched;
            movementPractice.moved(traveled);
        }
        ai.afterFrame(lastBodies, ctx.agents());
        falling.afterFrame(lastBodies, (float) lastDt);
        reactions.afterFrame(lastBodies);
        animals.afterFrame(lastBodies, ctx.agents());
        monsters.afterFrame(lastBodies);
        controller.syncCamera();
        waterContacts.update();
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
    public List<Actor> actors() {
        List<Actor> l = new ArrayList<>();
        l.add(player);
        for (Npc n : npcs) if (n.alive && n.loaded) l.add(n);
        l.addAll(dummies);
        if (animals != null) for (rpg.animal.Animal a : animals.all()) if (a.alive && a.loaded) l.add(a);
        if (monsters != null) for (rpg.monster.Monster m : monsters.all()) if (m.alive && m.loaded) l.add(m);
        return l;
    }

    public Actor actor(long id) {
        if (id == player.id) return player;
        for (Npc n : npcs) if (n.id == id) return n;
        for (Actor d : dummies) if (d.id == id) return d;
        if (monsters != null) { Actor m = monsters.byId(id); if (m != null) return m; }
        if (animals != null) return animals.byId(id);
        return null;
    }

    public String nameOf(long id) {
        if (id == 0) return "nobody";
        Actor a = actor(id);
        if (a != null) return a.name;
        WorldObject o = objects == null ? null : objects.get(id);
        return o != null ? o.name : "#" + id;
    }

    /** The console's notarget (or ai_ignoreplayer): nothing perceives the player. */
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
        vox.edited(vox.block(x,y,z),id);
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
    }

    public void teleport(Actor a, float x, float y, float z) {
        if (falling != null) falling.reset(a);
        if (a == player && movementPractice != null) movementPractice.reset();
        if (reactions != null && !(a instanceof rpg.animal.Animal)) reactions.cancel(a);
        if (a.body != 0) game.commands().moverPlace(a.body, x, y + 0.05f, z, 0, 0, 0);
        a.x = x; a.y = y; a.z = z;
        a.poseReset=true; a.handAt=-99; a.throwPlan.solvedAt=-99;
        if(chemistry!=null)chemistry.register(a);
        if (a instanceof Npc n) { if (reactions != null) reactions.cancel(n); n.legs = null; n.hasGoalPos = false; n.resetMotion(); }
        interact.cancel(a, "moved");
    }

    // ------------------------------------------------------------------ death and time
    public void onKilled(Actor v, Actor killer, long cause) {
        falling.reset(v);v.morphT=v.chronoT=0;
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
        Container grave = objects.add(new Container(state.newEntityId(), secured ? "death_claim" : "grave", player.name + "'s belongings", secured ? "claim" : "grave", secured ? DevMap.CUSTODY_CHEST[0] : x, DevMap.FLOOR + 0.3f, secured ? DevMap.CUSTODY_CHEST[1] : z, 320, player.id));
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
            if (!grave.contents.add(s)) objects.drop(s, x + 0.5f, DevMap.FLOOR + 0.05f, z + 0.3f);
        }
        for (rpg.item.ItemDef.Slot slot : rpg.item.ItemDef.Slot.values()) {
            ItemStack s = player.eq.get(slot);
            if (s == null) continue;
            player.eq.set(slot, null);
            if (!grave.contents.add(s)) objects.drop(s, x - 0.5f, DevMap.FLOOR + 0.05f, z + 0.3f);
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
        teleport(player, DevMap.GRAVEYARD_SPAWN[0], DevMap.FLOOR, DevMap.GRAVEYARD_SPAWN[1]);
        controller.snapTo(0);
        law.playerRespawned();
        state.commit("PlayerRespawned", player.id, 0, DevMap.ALPHA, 0, "at the graveyard");
        chat.say("You wake among the graves, with nothing but the clothes you were born to.");
    }

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
            if (carter != null && carter.alive) { teleport(carter, DevMap.CARAVAN_B[0], DevMap.FLOOR, DevMap.CARAVAN_B[1]); econ.deliver(s); }
        }
    }
}
