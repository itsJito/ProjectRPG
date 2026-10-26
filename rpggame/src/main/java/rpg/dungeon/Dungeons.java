package rpg.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Chat;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.WorldObject;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.monster.MonsterKind;
import rpg.monster.Monsters;
import rpg.objects.Container;
import rpg.skill.Skill;
import rpg.world.DevMap;
import rpg.world.DungeonLayout;
import rpg.world.DungeonLayout.Spot;

/**
 * The dungeon system (gamefixes): micro-dungeons, dungeons (macro), elite and boss dungeons, each in its own sealed map
 * (DungeonLayout carves them; this places and runs what is in them). A dungeon has:
 *
 * <ul>
 *   <li>its door: an entrance in the wilds, and an exit inside - the only ways in and out;</li>
 *   <li>its residents: monsters of its theme, born into lairs in its rooms, harder the deeper the room and far harder in
 *       an elite or a boss dungeon - and, being lairs, they live: a cleared dungeon stands empty a long while before
 *       something moves back in (nothing respawns on a timer);</li>
 *   <li>its traps: plates in the corridors and before the hoard - spikes, or a fall of rock - found by a sharp eye (or a
 *       sore foot), disarmed by a thief's hands, sprung by anyone who walks on them, man or beast;</li>
 *   <li>its hazards (carved in): fire pits and sunken pools; its hoard: chests, rolled once and never refilled.</li>
 * </ul>
 * Everything in it is remembered across saves: the dead stay dead, the chests stay empty, a disarmed trap stays disarmed.
 */
public final class Dungeons {
    private final Rpg g;
    public final List<Door> doors = new ArrayList<>();
    public final List<Trap> traps = new ArrayList<>();
    private final Map<Integer, Long> visited = new HashMap<>();
    private final Map<Integer, Integer> encounterLevels = new HashMap<>();

    public Dungeons(Rpg g) { this.g = g; }

    // ------------------------------------------------------------------ the things in a dungeon
    /** An entrance (in the overworld) or an exit (inside): the way between the two. */
    public static final class Door extends WorldObject {
        public final DungeonLayout layout;
        public final boolean inside;
        /** Where it takes you, and which way you face on arriving. */
        public float toX, toZ, toYaw;
        public Door(long id, DungeonLayout l, boolean inside, float x, float z) {
            super(id, inside ? "dungeon_exit" : "dungeon_entrance", inside ? "Way out" : l.name, x, DevMap.FLOOR, z);
            this.layout = l; this.inside = inside;
        }
        @Override public float focusRadius() { return 1.1f; }
        @Override public String examine(Rpg g) {
            if (inside) return "Daylight, a long way up. The way back out of the " + layout.name + ".";
            String who = switch (layout.theme) { case WARREN -> "Goblin tracks lead in and out of it, and it smells of smoke."; case CAVE -> "Old webs hang across the mouth of it."; case CRYPT -> "Cold air breathes out of it. Something inside is not resting."; case HALL -> "Huge knuckle-prints are worn into the rock at its mouth."; };
            return layout.name + " - " + layout.type.title.toLowerCase() + ", " + layout.threatWord() + " threat. " + who;
        }
        @Override public List<Option> options(Rpg g, Actor a) {
            return List.of(Option.on(inside ? "leave" : "enter", inside ? "Leave the " + layout.name : "Enter the " + layout.name, 10, g.cfg.interactRange), Option.on("examine", "Examine", 0, 5));
        }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
            if (!action.equals("enter") && !action.equals("leave")) return Result.no("You can't do that.");
            String why = reach(g, a, g.cfg.interactRange);
            if (why != null) return Result.no(why);
            if (g.carrying.busy(a)) return Result.no("Put down what you're carrying first.");
            if (a.cs.busy()) return Result.no("Not in the middle of a fight.");
            g.dungeons.travel(a, this, req);
            return Result.ok();
        }
    }

    /**
     * A trap: a plate in the floor. Hidden until someone with a thief's eye comes near (or it is sprung); sprung by
     * anything that treads on it - spikes up through the plate, or rock down from the roof - and set again a few seconds
     * after; disarmed for good by careful hands.
     */
    public static final class Trap extends WorldObject {
        public final int kind;          // 0 spikes, 1 falling rock
        public final float threat;
        public final int level;
        public boolean armed = true, found, disarmed;
        public double sprungAt = -99, readyAt;
        public Trap(long id, int kind, float threat, int level, float x, float z) {
            super(id, "dungeon_trap", kind == 0 ? "Spike plate" : "Loose stone", x, DevMap.FLOOR, z);
            this.kind = kind; this.threat = threat; this.level = level;
        }
        @Override public float focusRadius() { return 0.45f; }
        @Override public boolean exists() { return !removed; }
        @Override public String examine(Rpg g) {
            return disarmed ? "A trap's plate, wedged so it will not spring." : kind == 0 ? "A plate in the floor, sitting a little proud. Spikes wait under it." : "A loose stone underfoot. The roof above it is cracked and heavy.";
        }
        @Override public List<Option> options(Rpg g, Actor a) {
            if (disarmed) return List.of(Option.on("examine", "Examine", 0, 5));
            int need = disarmLevel();
            return List.of(Option.when(a.skills.level(Skill.THIEVING) >= need, "disarm", "Disarm", 10, "You need Thieving level " + need + " to disarm this.", 1.6f), Option.on("examine", "Examine", 0, 5));
        }
        int disarmLevel() { return Math.min(90, 5 + level); }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
            if (!action.equals("disarm") || disarmed) return Result.no("You can't do that.");
            String why = reach(g, a, 1.6f);
            if (why != null) return Result.no(why);
            int need = disarmLevel(), have = a.skills.level(Skill.THIEVING);
            if (have < need) return Result.no("You need Thieving level " + need + " to disarm this.");
            // the better the hands against the trap, the surer the work; a slip springs it
            double chance = Math.min(0.95, 0.55 + (have - need) * 0.02);
            if (g.roll(g.state.tick, id) < chance) {
                disarmed = true; armed = false;
                g.state.commit(req, "TrapDisarmed", a.id, id, jurisdiction(), 0, name);
                g.prog.award(a, Skill.THIEVING, 25 + level * 1.5 * threat, "disarmed a trap", 0);
                if (a == g.player) g.chat.say("You wedge the plate. The trap will not spring now.");
                if (g.sfx != null) g.sfx.play("lock", x, y + 0.2f, z, 0.5f);
            } else {
                if (a == g.player) g.chat.add("Your hand slips!", Chat.Kind.WARN);
                g.dungeons.spring(this, a);
            }
            return Result.ok();
        }
        @Override public Map<String, Object> save() { var m = super.save(); m.put("found", found); m.put("disarmed", disarmed); return m; }
        @Override public void load(Rpg g, Map<String, Object> m) { super.load(g, m); found = astratto.util.Json.bool(m, "found", false); disarmed = astratto.util.Json.bool(m, "disarmed", false); armed = !disarmed; }
    }

    // ------------------------------------------------------------------ building them into the world
    /** Each dungeon's door, exit, traps and chests (fixed ids: their state is saved like any fixture's), and its residents. */
    public void init() {
        long id = rpg.Ids.DUNGEONS;
        for (DungeonLayout l : DungeonLayout.all()) {
            float[] gate = entranceSpot(l);
            Door entrance = g.objects.add(new Door(id++, l, false, gate[0], gate[1]));
            Door exit = g.objects.add(new Door(id++, l, true, l.worldX(l.entryX) + 0.5f, l.worldZ(l.entryZ) + 0.5f));
            entrance.toX = exit.x + 1.2f; entrance.toZ = exit.z; entrance.toYaw = (float) (-Math.PI / 2);
            exit.toX = entrance.x; exit.toZ = entrance.z + 1.6f; exit.toYaw = 0;
            doors.add(entrance); doors.add(exit);
            java.util.Random r = new java.util.Random(l.seed ^ 0x7EA5);
            for (Spot s : l.spots) {
                float x = l.worldX(s.x()) + 0.5f, z = l.worldZ(s.z()) + 0.5f;
                switch (s.what()) {
                    case "trap" -> traps.add(g.objects.add(new Trap(id++, s.arg(), l.type.threat, level(l, 2), x, z)));
                    case "chest" -> {
                        Container chest = g.objects.add(new Container(id++, "chest", "Old chest", "chest", x, DevMap.FLOOR + 0.4f, z, 20, 0));
                        for (ItemStack st : hoard(l, s.arg(), r)) if (chest.contents.add(st)) g.state.mint(st.def, st.amount);
                    }
                    default -> { }
                }
            }
        }
        populate();
    }

    /** The residents (once: a load brings them back from the save; an old save without them gets them now). */
    public void populate() {
        for (DungeonLayout l : DungeonLayout.all()) {
            String lairName = l.name;
            boolean have = false;
            for (var lair : g.monsters.lairs) if (lair.name.startsWith(lairName)) have = true;
            if (have) continue;
            int room = 0;
            for (Spot s : l.spots) {
                if (!s.what().equals("resident") && !s.what().equals("master")) continue;
                boolean master = s.what().equals("master");
                MonsterKind kind = resident(l, s, master);
                int lv = level(l, s.arg()) + (master ? (int) (12 * l.type.threat) : 0);
                if (l.type == DungeonLayout.Type.BOSS) lv = kind.maxLevel;
                float x = l.worldX(s.x()) + 0.5f, z = l.worldZ(s.z()) + 0.5f;
                Monsters.Lair lair = g.monsters.addLair(kind, lairName + " " + (++room), x, z, Math.max(kind.minLevel, lv - 6), Math.min(kind.maxLevel, lv), master ? 1 : 2);
                var m = g.monsters.spawn(kind, lv, x, z, lair);
                if (master) m.leader = true;
            }
        }
    }

    /** Who lives where: by the dungeon's theme, the deeper rooms holding the stronger kinds. */
    private static MonsterKind resident(DungeonLayout l, Spot s, boolean master) {
        int depth = s.arg(), roll = Math.floorMod(s.x() * 31 + s.z() * 17, 6);
        return switch (l.theme) {
            case WARREN -> master || depth >= 3 && roll < 3 ? MonsterKind.ORC : roll == 5 ? MonsterKind.BAT : MonsterKind.GOBLIN;
            case CAVE -> roll < 4 || master ? MonsterKind.GIANT_SPIDER : MonsterKind.BAT;
            case CRYPT -> master ? MonsterKind.GHOST : roll < 3 ? MonsterKind.SKELETON : roll < 5 ? MonsterKind.ZOMBIE : MonsterKind.GHOST;
            case HALL -> MonsterKind.TROLL;
        };
    }
    /** A resident's level: the dungeon's threat on the room's depth. */
    private static int level(DungeonLayout l, int depth) {
        return switch (l.type) {
            case MICRO -> 6 + depth * 4;
            case MACRO -> 6 + depth * 6;
            case ELITE -> 70 + depth * 10;
            case BOSS -> 130;
        };
    }
    /** A chest's hoard, rolled from its dungeon's threat and its depth (once, from the dungeon's seed). */
    private List<ItemStack> hoard(DungeonLayout l, int depth, java.util.Random r) {
        List<ItemStack> out = new ArrayList<>();
        float t = l.type.threat;
        out.add(new ItemStack(Items.COINS, (int) ((40 + r.nextInt(160)) * t * (1 + depth * 0.5f))));
        String[] gems = t >= 2.5f ? new String[]{"uncut_ruby", "uncut_diamond", "uncut_dragonstone"} : new String[]{"uncut_sapphire", "uncut_emerald", "uncut_ruby"};
        for (int i = 0; i < 1 + depth / 2; i++) { ItemDef d = Items.find(gems[r.nextInt(gems.length)]); if (d != null) out.add(new ItemStack(d, 1)); }
        String[] gear = switch (l.theme) {
            case WARREN -> new String[]{"bronze_spear", "iron_sword", "iron_battleaxe", "goblin_mail"};
            case CAVE -> new String[]{"spider_silk", "venom_gland", "webbing"};
            case CRYPT -> new String[]{"soul_dust", "spectral_residue", "ancient_helm", "cursed_blade"};
            case HALL -> new String[]{"troll_fat", "granite_shield", "rare_fossil", "troll_totem"};
        };
        for (int i = 0; i < 1 + (t >= 2.5f ? 2 : 0); i++) { ItemDef d = Items.find(gear[r.nextInt(gear.length)]); if (d != null) out.add(new ItemStack(d, 1)); }
        if (l.type == DungeonLayout.Type.BOSS) { ItemDef heart = Items.find("troll_heart"); if (heart != null) out.add(new ItemStack(heart, 1)); }
        return out;
    }

    /** Where a dungeon's entrance stands in the wilds: near its residents' kin above ground, on clear ground. */
    private float[] entranceSpot(DungeonLayout l) {
        float[] want = switch (l.id) {
            case 0 -> new float[]{31.5f, 26.5f};                        // the goblin warren, in the wood south of the goblins' camp
            case 1 -> new float[]{9.5f, 46.5f};                         // the spider hollow, in the western wood
            case 2 -> new float[]{70.5f, 40.5f};                        // the catacombs, out past the wood
            default -> new float[]{12.5f, 12.5f};                       // the troll's den, under the den rock
        };
        for (int r = 0; r < 10; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int cx = (int) Math.floor(want[0]) + dx, cz = (int) Math.floor(want[1]) + dz;
            if (cx < 3 || cz < 3 || cx >= DevMap.SX - 3 || cz >= DevMap.SZ - 3) continue;
            boolean ok = true;
            for (int ox = -1; ox <= 1 && ok; ox++) for (int oz = -1; oz <= 2 && ok; oz++)
                ok = g.vox.solid(cx + ox, DevMap.FLOOR - 1, cz + oz) && !g.vox.solid(cx + ox, DevMap.FLOOR, cz + oz) && !g.vox.solid(cx + ox, DevMap.FLOOR + 1, cz + oz) && g.vox.block(cx + ox, DevMap.FLOOR - 1, cz + oz) != rpg.world.Blocks.WATER;
            if (ok) return new float[]{cx + 0.5f, cz + 0.5f};
        }
        return want;
    }

    // ------------------------------------------------------------------ going in and coming out
    /** Through a dungeon's door: the traveller, and any ally of theirs close by, to the other side. */
    void travel(Actor a, Door door, long req) {
        List<Actor> party = new ArrayList<>();
        party.add(a);
        for (var n : g.npcs) if (n.alive && n.summonOwner == a.id && n.distTo(a) < 12) party.add(n);
        for (int i = 0; i < party.size(); i++) {
            Actor p = party.get(i);
            float off = i * 0.9f;
            g.teleport(p, door.toX + (float) Math.cos(door.toYaw) * off, DevMap.FLOOR, door.toZ - (float) Math.sin(door.toYaw) * off);
            p.yaw = door.toYaw;
            if (p instanceof rpg.ai.Npc n) n.wantYaw = door.toYaw;
        }
        if (a == g.player) {
            g.controller.snapTo(door.toYaw);
            var l = door.layout;
            if (!door.inside) {
                configureEncounter(l);
                g.chat.add("You enter the " + l.name + ". (" + l.type.title + ", " + l.threatWord() + " threat)", Chat.Kind.GAME);
                if (!visited.containsKey(l.id)) { visited.put(l.id, (long) g.state.minutes); g.chat.say(firstWords(l)); }
            } else g.chat.add("You climb back out into the open air.", Chat.Kind.GAME);
        }
        g.state.commit(req, door.inside ? "DungeonLeft" : "DungeonEntered", a.id, door.id, DevMap.WILDS, 0, door.layout.name);
    }
    /** Scale the living encounter at its boundary; injured enemies retain their health share. */
    public void configureEncounter(DungeonLayout layout) {
        encounterLevels.put(layout.id, g.player.skills.combatLevel());
        for (var monster : g.monsters.all()) if (monster.alive && DungeonLayout.at(monster.lairX, monster.lairZ) == layout) configureResident(monster);
    }
    public void configureResident(rpg.monster.Monster monster) {
        var layout = DungeonLayout.at(monster.lairX, monster.lairZ);
        if (layout == null || !encounterLevels.containsKey(layout.id)) return;
        int playerLevel = encounterLevels.get(layout.id);
        int target = switch (layout.type) {
            case MICRO, MACRO -> Math.max(1, playerLevel - Math.floorMod(monster.id, 4));
            case ELITE -> playerLevel + 5 + Math.floorMod(monster.id, 8);
            case BOSS -> playerLevel + Math.max(10, playerLevel / 4);
        };
        monster.encounterLevel(target);
    }

    private static String firstWords(DungeonLayout l) {
        return switch (l.theme) {
            case WARREN -> "Low tunnels, dug by small hands. Somewhere ahead, goblins are arguing.";
            case CAVE -> "Silk everywhere. Something large shifts in the dark.";
            case CRYPT -> "Rows of old niches, and dust that has been disturbed recently.";
            case HALL -> "The tunnel opens into something huge. You can hear it breathing.";
        };
    }

    // ------------------------------------------------------------------ the frame: traps
    public void update() {
        for (Trap t : traps) {
            if (t.removed || t.disarmed) continue;
            if (!t.armed && g.time >= t.readyAt) t.armed = true;
            // found by a sharp eye: the player near enough, sharp enough (Thieving), and looking
            if (!t.found && g.player.alive && Math.hypot(g.player.x - t.x, g.player.z - t.z) < 3.2f && g.player.skills.level(Skill.THIEVING) >= t.disarmLevel() - 10) {
                t.found = true;
                g.chat.add("You notice a " + (t.kind == 0 ? "plate set in the floor ahead. A trap." : "loose stone under a cracked roof. A trap."), Chat.Kind.WARN);
            }
            if (!t.armed) continue;
            for (Actor a : g.actors()) {
                if (!a.alive || a.dummy || a instanceof rpg.monster.Monster || !a.grounded && !(a instanceof rpg.animal.Animal)) continue;
                if (Math.abs(a.x - t.x) < 0.45f && Math.abs(a.z - t.z) < 0.45f && a.y < DevMap.FLOOR + 0.3f) { spring(t, a); break; }
            }
        }
    }

    /** A trap goes off under someone: spikes up through the plate, or the roof's rock down on them. */
    void spring(Trap t, Actor a) {
        t.armed = false; t.found = true; t.sprungAt = g.time; t.readyAt = g.time + 8;
        int harm = Math.round((4 + t.level * 0.35f) * t.threat);
        g.combat.environmentalDamage(a, harm, t.kind == 0 ? "spike trap" : "falling rock");
        if (t.kind == 0) a.cs.slowT = Math.max(a.cs.slowT, 2.5f);
        else a.cs.staggered = Math.max(a.cs.staggered, 0.9f);
        if (g.sfx != null) g.sfx.play(t.kind == 0 ? "block" : "stone", t.x, t.y + 0.3f, t.z, 0.9f);
        if (g.fx != null) { if (t.kind == 0) g.fx.sparks(t.x, t.y + 0.2f, t.z, 8); else g.fx.puff(t.x, t.y + 2.5f, t.z); }
        g.noise(a, t.x, t.y, t.z, 10, 0.6f);   // and the whole place hears it
        g.state.commit("TrapSprung", a.id, t.id, DevMap.WILDS, 0, t.name);
    }

    // ------------------------------------------------------------------ queries, saving
    /** The dungeon an actor is in, or null. */
    public DungeonLayout in(Actor a) { return DungeonLayout.at(a.x, a.z); }

    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        Map<String, Object> v = new HashMap<>();
        for (var e : visited.entrySet()) v.put(String.valueOf(e.getKey()), e.getValue());
        m.put("visited", v);
        Map<String,Object> levels=new HashMap<>();encounterLevels.forEach((id,level)->levels.put(id.toString(),level));m.put("encounters",levels);
        return m;
    }
    public void load(Map<String, Object> m) {
        visited.clear(); encounterLevels.clear();
        if(m!=null && astratto.util.Json.obj(m,"encounters") instanceof Map<String,Object> levels)
            levels.forEach((id,value)->{if(value instanceof Number n)encounterLevels.put(Integer.parseInt(id),n.intValue());});
        if (m != null && astratto.util.Json.obj(m, "visited") instanceof Map<String, Object> v) for (var e : v.entrySet()) if (e.getValue() instanceof Number n) visited.put(Integer.parseInt(e.getKey()), n.longValue());
        populate();
        for(var monster:g.monsters.all())if(monster.alive)configureResident(monster);
    }
}
