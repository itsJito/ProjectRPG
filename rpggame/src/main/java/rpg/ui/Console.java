package rpg.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import astratto.console.DevConsole;
import rpg.Rpg;
import rpg.SaveSystem;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.item.ItemDef;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.skill.XpTable;
import rpg.world.DevMap;

/**
 * The developer console, the game's side of it (gamefixes: no sv_cheats, no other engine's commands; the console runs
 * in the engine). The engine's {@link DevConsole} owns the registry, the parser, aliases, binds, scripts, history and
 * completion; this class adds the game's commands and variables to it under plain names and draws it - a pane that
 * drops down over the top of the screen and pauses the world while it is open. Everything is available at once: it is
 * a developer's tool, not a mode to be unlocked.
 *
 * <p>Items are given by id ({@code give godly_2hsword 1}), case and spaces ignored ({@code give Godly 2h sword}); a
 * near miss suggests what was meant. Everything a command does goes through the same rules the rest of the game does:
 * given items are minted into the ledger, levels go through the progression, a teleport is a real teleport.
 */
public final class Console {
    private final Rpg g;
    private final Hud hud;
    /** The engine's console, which this one fills with the game's commands. */
    public final DevConsole core;
    private float slide;

    // ---- the debugging states the game reads
    public boolean god, immortal, noclip, invisible;

    static final int TEXT = DevConsole.TEXT, ECHO = DevConsole.ECHO, WARN = DevConsole.WARN, ERR = DevConsole.ERR, GOOD = DevConsole.GOOD, DIM = DevConsole.DIM;

    public Console(Rpg g, Hud hud) {
        this.g = g; this.hud = hud;
        core = new DevConsole(astratto.Astratto.NAME, astratto.Astratto.VERSION, nativeInfo());
        core.onRun = (name, line) -> g.state.commit("ConsoleCommand", g.player.id, 0, null, 0, line);
        core.onToggle = open -> { if (g.sfx != null) g.sfx.ui(open ? "MenuScreenOpen" : "UI_windowclose", 0.35f); };
        register();
        print("Developer console. 'help' for a start, 'commands' and 'vars' for everything, Tab to complete.", DIM);
    }

    private static String nativeInfo() { try { return astratto.bridge.AstrattoNative.buildInfo(); } catch (Throwable t) { return null; } }

    // ------------------------------------------------------------------ the engine console's surface, as the game uses it
    public boolean isOpen() { return core.open; }
    public DevConsole.Var cvar(String name) { return core.get(name); }
    public float f(String name) { return core.f(name); }
    public boolean on(String name) { return core.on(name); }
    public Map<String, String> binds() { return core.binds; }
    public void key(String name) { core.key(name); }
    public void run(String line, boolean typed) { core.run(line, typed); }
    public void print(String s, int color) { core.print(s, color); }
    public void toggle() { core.toggle(); }
    public void close() { core.close(); }
    public void autoexec() { core.autoexec(); }
    public void input(String typed, boolean enter, boolean backspace, boolean up, boolean down, boolean tab, int page) { core.input(typed, enter, backspace, up, down, tab, page); }

    private void var(String name, String def, String help) { core.var(name, def, help); }
    private void var(String name, String def, String help, Consumer<DevConsole.Var> changed) { core.var(name, def, help, changed); }
    private void cmd(String name, String help, Consumer<String[]> run) { core.cmd(name, help, run); }

    // ------------------------------------------------------------------ the game's commands and variables
    private void register() {
        var("developer", "0", "Developer readouts (1: notices; 2: the inspectors too).", c -> { hud.stateInspector = c.f() >= 2; hud.aiInspector = c.f() >= 2; });
        var("timescale", "1", "The world's clock: 0.5 half speed, 2 double (the interface keeps real time).");
        var("max_fps", "0", "Frame rate cap (0: the monitor's / none).");
        var("show_fps", "0", "Show the frame rate (1) and frame time (2).");
        var("show_pos", "0", "Show where you are, which way you face and how fast you go.");
        var("telemetry", "0", "The combat telemetry overlay.", c -> hud.telemetry = c.on());
        var("fov", "70", "Field of view (degrees, 50 - 110).", c -> hud.gfx.fovDeg = Math.max(50, Math.min(110, c.f())));
        var("gravity", "22", "World gravity (m/s/s). The game's own is 22.", c -> g.game.commands().gravity(c.f(), c.f()));
        var("sensitivity", "1", "Mouse sensitivity multiplier.");
        var("volume", "1", "Master volume (0 - 1).");
        var("console_filter", "", "Show only console lines containing this text (empty: all).");
        var("xp_rate", "2.5", "XP rate multiplier on every award (1: the raw curve, no per-skill pacing).", c -> g.cfg.xpRate = Math.max(0, c.f()));
        var("minimap", "1", "Show the minimap.");
        var("motion_blur", "0", "Camera motion blur (off by default).", c -> g.cfg.motionBlur = c.on());
        var("depth_of_field", "0", "Depth of field (off by default).", c -> g.cfg.depthOfField = c.on());
        var("dynamic_resolution", "1", "Dynamic resolution (the TAA upscales).", c -> g.cfg.autoScale = c.on());
        var("render_scale", "1", "Share of the window's resolution rendered (0.5 - 1).", c -> g.cfg.renderScale = Math.max(0.5f, Math.min(1, c.f())));
        var("ai_disabled", "0", "Freeze every NPC's and animal's mind.");
        var("ai_ignore_player", "0", "NPCs and animals neither see nor hear the player.", c -> invisible = c.on());

        cmd("status", "Who and where you are, the time, the session.", a -> {
            print(String.format("%s at (%.1f, %.1f, %.1f) in %s, %s, day %d %s", g.player.name, g.player.x, g.player.y, g.player.z, DevMap.jurisdiction(g.player.x, g.player.z),
                    g.player.alive ? String.format("%.0f/%.0f HP", g.player.hp, g.player.maxHp()) : "dead", (int) (g.state.minutes / 1440) + 1, g.state.clockText()), TEXT);
            print(String.format("Honor %+d (%s); level %d; %d events this session", g.honor.score, g.honor.rankName(), g.player.skills.combatLevel(), g.state.all().size()), DIM);
        });
        cmd("quit", "Leaves the game.", a -> hud.quit());
        cmd("title", "Back to the title screen.", a -> hud.toTitle());
        cmd("save", "save <name>: saves the game to saves/<name>.json.", a -> { String n = a.length > 1 ? a[1] : "quick"; if (new SaveSystem(g).save(SaveSystem.slot(n))) print("Saved " + SaveSystem.slot(n), GOOD); });
        cmd("load", "load <name>: loads saves/<name>.json.", a -> { String n = a.length > 1 ? a[1] : "quick"; if (new SaveSystem(g).load(SaveSystem.slot(n))) print("Loaded " + SaveSystem.slot(n), GOOD); });
        cmd("screenshot", "Saves a screenshot to shots/.", a -> hud.screenshotRequested = true);
        cmd("restart", "Puts you back at the start of the devmap, healed.", a -> { g.player.restore(); g.teleport(g.player, DevMap.SPAWN_X, DevMap.FLOOR, DevMap.SPAWN_Z); });
        cmd("where", "Where you are and which way you look (as 'goto' and 'face' would take them).", a -> print(String.format("goto %.2f %.2f %.2f; face %.2f %.2f", g.player.x, g.player.y, g.player.z, Math.toDegrees(g.controller.camPitch), Math.toDegrees(g.controller.camYaw)), TEXT));
        cmd("thirdperson", "The camera behind you.", a -> g.player.firstPerson = false);
        cmd("firstperson", "The camera at your eyes.", a -> g.player.firstPerson = true);
        cmd("stop_music", "Stops the music for now (it comes back with the next piece).", a -> { if (g.music != null) g.music.stopNow(); });
        cmd("play", "play <sound>: plays an interface or world sound by its bank name (ui.LevelUp, swing, hit ...).", a -> { if (a.length > 1 && g.sfx != null) { if (g.sfx.has(a[1]) && !a[1].startsWith("ui.")) g.sfx.play(a[1], g.player.x, g.player.eyeY(), g.player.z, 0.8f); else g.sfx.ui(a[1], 0.8f); } });
        cmd("items", "items [text]: every item id (with the text in it).", a -> {
            String t = a.length > 1 ? norm(DevConsole.join(a, 1)) : "";
            int n = 0;
            for (ItemDef d : Items.all()) if (t.isEmpty() || norm(d.id).contains(t) || norm(d.name).contains(t)) { print(String.format("%-28s %s", d.id, d.name), TEXT); n++; }
            print(n + " items", DIM);
        });
        cmd("occupation","occupation <npc-id> <subtype> [principal-id]: changes a compatible fixed NPC profession; no XP is granted.",a->{
            if(a.length<3){for(var type:rpg.ai.Profession.values())print(type.name()+" - "+type.title,DIM);return;}
            if(!(g.actor(Long.parseLong(a[1])) instanceof Npc npc)){print("No such NPC.",ERR);return;}
            var type=rpg.ai.Profession.valueOf(a[2].toUpperCase(java.util.Locale.ROOT));
            if(type.family!=npc.role){print("That subtype belongs to "+type.family+", not "+npc.role,ERR);return;}
            g.interact.cancel(npc,"occupation changed");type.assign(npc);
            if(a.length>3)npc.dutyTarget=Long.parseLong(a[3]);npc.dutyX=npc.postX;npc.dutyZ=npc.postZ;
            print(npc.name+" is now a "+type.title+"; existing skills and belongings retained.",GOOD);
        });
        cmd("npcs", "Every person: id, name, where, alive or dead.", a -> { for (Npc n : g.npcs) print(String.format("#%-6d %-10s %-12s (%.0f, %.0f) %s", n.id, n.name, n.role, n.x, n.z, n.alive ? "" : "DEAD"), TEXT); });
        cmd("animals", "Every animal near you (within 60 m).", a -> { for (var an : g.animals.all()) if (an.alive && an.distTo(g.player) < 60) print(String.format("#%-6d %-14s level %-3d (%.0f, %.0f)", an.id, an.kind(), an.skills.combatLevel(), an.x, an.z), TEXT); });
        cmd("invariants", "Checks every world-state invariant.", a -> { var f = g.invariants.check(); if (f.isEmpty()) print("All invariants hold.", GOOD); else for (String s : f) print(s, ERR); });
        cmd("ai_inspector", "Toggles the AI inspector (F9).", a -> hud.aiInspector = !hud.aiInspector);
        cmd("state_inspector", "Toggles the state and events inspector (F10).", a -> hud.stateInspector = !hud.stateInspector);
        cmd("combat_telemetry", "Toggles the combat telemetry (F11).", a -> hud.telemetry = !hud.telemetry);
        cmd("xp_dashboard", "Toggles the XP dashboard (F8).", a -> hud.xpDashboard = !hud.xpDashboard);
        cmd("addons_reload", "Reloads the interface addons.", a -> hud.addons.loadAll(Path.of("addons")));

        // ---- the body
        cmd("god", "Toggles god mode: nothing hurts you.", a -> { god = !god; print("god mode " + (god ? "ON" : "OFF"), TEXT); });
        cmd("immortal", "Toggles immortality: you are hurt, but never below 1 HP.", a -> { immortal = !immortal; print("immortal " + (immortal ? "ON" : "OFF"), TEXT); });
        cmd("noclip", "Toggles flying through everything (WASD, Space up, Ctrl down, Shift fast).", a -> { noclip = !noclip; print("noclip " + (noclip ? "ON" : "OFF"), TEXT); });
        cmd("invisible", "Toggles invisibility to NPCs and animals (the ai_ignore_player variable).", a -> { core.set("ai_ignore_player", invisible ? "0" : "1"); print("invisible " + (invisible ? "ON" : "OFF"), TEXT); });
        cmd("hurt", "hurt <hp>: hurts you (a negative amount heals).", a -> { float d = a.length > 1 ? DevConsole.num(a[1], 1) : 1; g.player.hp = Math.max(1, Math.min(g.player.maxHp(), g.player.hp - d)); });
        cmd("kill", "Kills you (no one is to blame).", a -> g.combat.bleedOutNow(g.player));
        cmd("heal", "Restores all resources and clears burning, shock, frost and bleeding.", a -> { g.player.restore(); g.chemistry.cleanse(g.player); if (g.gore != null) g.gore.healed(g.player); });
        cmd("goto", "goto <x> <y> <z>, or goto <place>: start, combat, skills, yard, economy, quest, farm, wilds, pond, graveyard, fence.", a -> {
            if (a.length >= 4) { g.teleport(g.player, DevConsole.num(a[1], g.player.x), DevConsole.num(a[2], g.player.y), DevConsole.num(a[3], g.player.z)); return; }
            Map<String, float[]> spots = places();
            float[] s = a.length > 1 ? spots.get(a[1].toLowerCase(Locale.ROOT)) : null;
            if (s == null) { print("goto: x y z, or one of " + String.join(", ", spots.keySet()), WARN); return; }
            g.teleport(g.player, s[0], DevMap.FLOOR, s[1]);
        });
        core.completer("goto", t -> { List<String> l = new ArrayList<>(); for (String k : places().keySet()) if (k.startsWith(t)) l.add(k); return l; });
        cmd("face", "face <pitch> <yaw>: turns the camera (degrees).", a -> { if (a.length < 3) { print("Usage: face pitch yaw", WARN); return; } g.controller.camPitch = (float) Math.toRadians(DevConsole.num(a[1], 0)); g.controller.snapTo((float) Math.toRadians(DevConsole.num(a[2], 0))); });

        // ---- things
        cmd("give", "give <item> [amount]: items into your pack (by id or name; case and spaces don't matter).", a -> {
            if (a.length < 2) { print("Usage: give <item id> [amount]  ('items' shows the ids)", WARN); return; }
            int n = 1;
            String[] words = a;
            if (a.length > 2 && a[a.length - 1].matches("\\d+")) { n = Integer.parseInt(a[a.length - 1]); words = java.util.Arrays.copyOf(a, a.length - 1); }
            ItemDef d = item(DevConsole.join(words, 1));
            if (d == null) return;
            give(d, Math.max(1, Math.min(n, 100000)));
        });
        core.completer("give", t -> { List<String> l = new ArrayList<>(); for (ItemDef d : Items.all()) if (d.id.startsWith(t)) l.add(d.id); return l; });
        cmd("givebest", "givebest weapons | armor: the best of every weapon (and a full quiver), or the best armor.", a -> {
            String k = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
            if (k.startsWith("w")) { for (String id : new String[]{"godly_sword", "godly_2hsword", "godly_battleaxe", "godly_mace", "godly_dagger", "godly_shield", "willow_shortbow", "fire_staff", "blessed_symbol"}) give(Items.get(id), 1); give(Items.IRON_ARROWS, 200); }
            else if (k.startsWith("a")) { for (String id : new String[]{"godly_helm", "godly_body", "godly_legs", "leather_boots", "leather_gloves", "diamond_amulet", "diamond_ring"}) give(Items.get(id), 1); }
            else print("givebest weapons | armor", WARN);
        });
        cmd("gearkit", "The old debug gear kit.", a -> hud.gearKit());
        cmd("spawn", "spawn <animal | monster | person> [level | baby | breed]: makes one in front of you (spawn wolf baby, spawn goblin 12, spawn dragon ice, spawn guard). 'spawnlist' names them all.", a -> {
            if (a.length < 2) { print("Usage: spawn <cow, goblin, dragon, guard, bounty_hunter ...> [level | baby | breed]", WARN); return; }
            float fx = (float) -Math.sin(g.controller.camYaw), fz = (float) -Math.cos(g.controller.camYaw), x = g.player.x + fx * 3, z = g.player.z + fz * 3;
            String want = norm(a[1]), extra = a.length > 2 ? a[2] : "";
            for (var s : rpg.animal.Species.values()) if (norm(s.name()).equals(want) || norm(s.label).equals(want)) {
                var an = g.animals.release(s, extra.equalsIgnoreCase("baby"), (g.state.tick & 1) == 0, x, z);
                print("spawned " + an.kind() + " #" + an.id, GOOD);
                return;
            }
            for (var k : rpg.monster.MonsterKind.values()) if (norm(k.name()).equals(want) || norm(k.title).equals(want)) {
                int level = (int) Math.max(k.minLevel, Math.min(k.maxLevel, DevConsole.num(extra, (k.minLevel + k.maxLevel) / 2f)));
                var m = g.monsters.spawn(k, level, x, z, null);
                for (var b : rpg.monster.MonsterKind.Breed.values()) if (k == rpg.monster.MonsterKind.DRAGON && b.name().equalsIgnoreCase(extra)) m.breed = b;
                print("spawned " + m.label() + " #" + m.id, GOOD);
                return;
            }
            Npc person = person(want, x, z, (int) DevConsole.num(extra, 0));
            if (person != null) { print("spawned " + person.title + " #" + person.id, GOOD); return; }
            print("spawn: no such creature or person '" + a[1] + "' (spawnlist names them all)", ERR);
        });
        core.completer("spawn", t -> spawnable().stream().filter(n -> n.startsWith(t)).toList());
        cmd("spawnlist", "Every animal, monster and kind of person 'spawn' can make.", a -> {
            List<String> animals = new ArrayList<>(), monsters = new ArrayList<>(), people = new ArrayList<>();
            for (var s : rpg.animal.Species.values()) animals.add(s.name().toLowerCase(Locale.ROOT));
            for (var k : rpg.monster.MonsterKind.values()) monsters.add(k.name().toLowerCase(Locale.ROOT));
            for (Npc.Role r : Npc.Role.values()) if (r != Npc.Role.MONSTER) people.add(r.name().toLowerCase(Locale.ROOT));
            for (rpg.ai.Profession pr : rpg.ai.Profession.values()) people.add(pr.name().toLowerCase(Locale.ROOT));
            print("Animals: " + String.join(", ", animals), TEXT);
            print("Monsters (spawn <kind> [level]; dragons take a breed): " + String.join(", ", monsters), TEXT);
            print("People (spawn <role or calling> [level]): " + String.join(", ", people), TEXT);
        });
        cmd("remove", "Removes what you are looking at (an item, an animal).", a -> {
            var f = g.interact.focus;
            if (f instanceof rpg.objects.GroundItem gi) { gi.removed = true; g.state.burn(gi.stack.def, gi.stack.amount); print("removed " + gi.stack, TEXT); }
            else if (f instanceof rpg.animal.Animal an) { g.animals.remove(an); print("removed " + an.kind(), TEXT); }
            else if (f instanceof rpg.monster.Monster mo) { g.monsters.remove(mo); print("removed " + mo.label(), TEXT); }
            else if (f instanceof Npc n && spawned.contains(n.id)) { g.ai.despawnBody(n); g.npcs.remove(n); spawned.remove(n.id); print("removed " + n.title, TEXT); }
            else print("remove: look at an item, an animal, a monster or someone you spawned", WARN);
        });
        cmd("slay", "Kills what you are looking at (a person, an animal or a monster).", a -> {
            Actor v = g.interact.focus instanceof Actor f && f.alive && !f.dummy ? f : sighted(40);
            if (v != null) { g.combat.bleedOutNow(v); print("slay: " + v.name, GOOD); } else print("slay: look at someone", WARN);
        });

        // ---- the world and the character
        cmd("setlevel", "setlevel <skill|all> <level>: sets a skill (1 - 100).", a -> {
            if (a.length < 3) { print("Usage: setlevel <skill|all> <level>", WARN); return; }
            int lv = (int) Math.max(1, Math.min(XpTable.MAX_LEVEL, DevConsole.num(a[2], 1)));
            if (a[1].equalsIgnoreCase("all")) { for (Skill s : Skill.ALL) hud.debugSetLevel(s, lv); return; }
            Skill s = Skill.byName(a[1]);
            if (s == null) { print("setlevel: no skill " + a[1], ERR); return; }
            hud.debugSetLevel(s, lv);
        });
        core.completer("setlevel", t -> { List<String> l = new ArrayList<>(); if ("all".startsWith(t)) l.add("all"); for (Skill s : Skill.ALL) if (s.title.toLowerCase(Locale.ROOT).startsWith(t)) l.add(s.title.toLowerCase(Locale.ROOT)); return l; });
        cmd("addxp", "addxp <skill> <xp>: grants XP through the progression (levels, the fanfare).", a -> {
            Skill s = a.length > 2 ? Skill.byName(a[1]) : null;
            if (s == null) { print("Usage: addxp <skill> <xp>", WARN); return; }
            g.prog.awardRaw(g.player, s, (long) Math.max(0, DevConsole.num(a[2], 0)), "console", 0, g.state.newRequestId());
        });
        cmd("resetskills", "Every skill back to 1 (Health to 10), as a new character.", a -> { for (Skill k : Skill.ALL) hud.debugSetLevel(k, k == Skill.HEALTH ? 10 : 1); });
        cmd("honor", "honor [value]: shows your Honor, or sets it (-100 .. 100).", a -> {
            if (a.length > 1) g.honor.score = (int) Math.max(-100, Math.min(100, DevConsole.num(a[1], 0)));
            print(String.format("Honor %+d: %s", g.honor.score, g.honor.rankName()), TEXT);
        });
        cmd("time", "time <hour>: sets the clock forward to that hour (0 - 24); time +<minutes> skips.", a -> {
            if (a.length < 2) { print(g.state.clockText(), TEXT); return; }
            if (a[1].startsWith("+")) { g.advanceTime(DevConsole.num(a[1].substring(1), 0), "console"); return; }
            double want = DevConsole.num(a[1], 12), now = g.state.hourOfDay(), d = (want - now + 24) % 24;
            g.advanceTime(d * 60, "console");
        });
        // 0.1.8 (gamefixes: everything in the game reachable from the console)
        cmd("weather", "weather <clear|overcast|fog|rain|storm|snow>: sets the weather at once.", a -> {
            if (a.length < 2) { print("weather: " + g.timeSystem.weather.kind().name().toLowerCase(Locale.ROOT), TEXT); return; }
            try { g.timeSystem.weather.set(astratto.weather.Weather.Kind.valueOf(a[1].toUpperCase(Locale.ROOT))); g.timeSystem.weather.snap(); print("Weather: " + a[1], GOOD); }
            catch (IllegalArgumentException e) { print("weather: clear, overcast, fog, rain, storm or snow", ERR); }
        });
        cmd("spells", "Every spell, by book, with its level.", a -> {
            for (var book : rpg.combat.Spells.Book.values()) {
                StringBuilder b = new StringBuilder(book.name().toLowerCase(Locale.ROOT) + ":");
                for (var sp : rpg.combat.Spells.Spell.values()) if (sp.book == book) b.append(' ').append(sp.name().toLowerCase(Locale.ROOT)).append('(').append(sp.level).append(')');
                print(b.toString(), DIM);
            }
        });
        cmd("book", "book <sun|fogish|moon>: switches your spellbook (as at the altar).", a -> {
            if (a.length < 2) { print("book: " + g.player.book.name().toLowerCase(Locale.ROOT), TEXT); return; }
            try { g.player.book = rpg.combat.Spells.Book.valueOf(a[1].toUpperCase(Locale.ROOT)); print("Spellbook: " + g.player.book.name().toLowerCase(Locale.ROOT), GOOD); }
            catch (IllegalArgumentException e) { print("book: sun, fogish or moon", ERR); }
        });
        cmd("cast", "cast <spell>: casts it where you look, its book and mana given (spells names them).", a -> {
            if (a.length < 2) { print("cast <spell>", ERR); return; }
            rpg.combat.Spells.Spell sp;
            try { sp = rpg.combat.Spells.Spell.valueOf(a[1].toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { print("cast: no such spell (spells names them)", ERR); return; }
            g.player.book = sp.book; g.player.spell = sp; g.player.mana = g.player.maxMana(); g.player.cs.castCooldown = 0;
            if (g.player.skills.level(Skill.MAGIC) < sp.level) hud.debugSetLevel(Skill.MAGIC, sp.level);
            if (!g.combat.cast(g.player, sp, g.controller.camYaw, g.controller.camPitch)) print("cast: it would not go off here", WARN);
        });
        cmd("ignite", "Sets fire to where you look (a few metres ahead).", a -> {
            float d = 3, x = g.player.x - (float) Math.sin(g.controller.camYaw) * d, z = g.player.z - (float) Math.cos(g.controller.camYaw) * d;
            g.chemistry.emit(g.player, astratto.bridge.AstrattoNative.CHEM_FIRE, x, g.player.y + 0.6f, z, 0.6f, 3, 0.6f);
            print("Fire set.", GOOD);
        });
        cmd("wheel", "wheel <slot 1-8> <item> | wheel clear: puts an item you carry in a quick-wheel slot.", a -> {
            if (a.length > 1 && a[1].equalsIgnoreCase("clear")) { java.util.Arrays.fill(g.player.quick, null); print("Quick wheel cleared.", GOOD); return; }
            if (a.length < 3) { for (int i = 0; i < 8; i++) print((i + 1) + ": " + (g.player.quick[i] == null ? "-" : g.player.quick[i].item().name), TEXT); return; }
            int slot = (int) DevConsole.num(a[1], 0) - 1;
            rpg.item.ItemDef d = rpg.item.Items.find(a[2]);
            if (slot < 0 || slot > 7 || d == null) { print("wheel <1-8> <item id>", ERR); return; }
            g.player.quick[slot] = new rpg.actor.Player.QuickSlot(d, g.player.count(d)); g.player.refreshQuick();
            print(g.player.quick[slot] == null ? "You carry no " + d.name + "." : "Slot " + (slot + 1) + ": " + d.name, g.player.quick[slot] == null ? WARN : GOOD);
        });
        cmd("revive", "revive [all|<npc>]: brings the dead back.", a -> {
            for (Npc n : g.npcs) if (!n.alive && (a.length < 2 || a[1].equalsIgnoreCase("all") || n.name.equalsIgnoreCase(a[1]))) { g.ai.revive(n, true); print("revived " + n.name, GOOD); }
        });
        cmd("bounty", "bounty clear | list: the law's books on you (clear also wipes what people know of your crimes).", a -> {
            if (a.length > 1 && a[1].equalsIgnoreCase("clear")) { g.law.clear(); for (Npc n : g.npcs) n.memories.removeIf(m -> m.subject == g.player.id); print("Bounties cleared.", GOOD); }
            else for (String j : new String[]{DevMap.ALPHA, DevMap.BETA}) print(j + ": " + g.law.bounty(g.player.id, j) + " G", TEXT);
        });
        cmd("rumors", "rumors [npc]: what people know of you (or what one person knows of everyone), and what they think of you.", a -> {
            for (Npc n : g.npcs) {
                if (a.length > 1 && !n.name.equalsIgnoreCase(a[1])) continue;
                StringBuilder b = new StringBuilder();
                for (var m : n.memories) if (a.length > 1 || m.subject == g.player.id) b.append(m.line(g::nameOf)).append("; ");
                if (b.length() > 0 || a.length > 1) print(String.format("%-8s opinion %+.2f  %s", n.name, g.ai.rumors.opinion(n), b), TEXT);
            }
        });
        cmd("npc_unload", "npc_unload <name>: takes a person out of the simulated area (abstract simulation), or back.", a -> { for (Npc n : g.npcs) if (a.length > 1 && n.name.equalsIgnoreCase(a[1])) { if (n.loaded) g.ai.unload(n); else g.ai.reload(n); } });
    }

    /** The names 'spawn' knows: every species, monster kind, role and calling. */
    private static List<String> spawnable() {
        List<String> l = new ArrayList<>();
        for (var s : rpg.animal.Species.values()) l.add(s.name().toLowerCase(Locale.ROOT));
        for (var k : rpg.monster.MonsterKind.values()) l.add(k.name().toLowerCase(Locale.ROOT));
        for (Npc.Role r : Npc.Role.values()) if (r != Npc.Role.MONSTER) l.add(r.name().toLowerCase(Locale.ROOT));
        for (rpg.ai.Profession pr : rpg.ai.Profession.values()) l.add(pr.name().toLowerCase(Locale.ROOT));
        return l;
    }

    /** The people the console made (only these may be removed again). */
    private final java.util.Set<Long> spawned = new java.util.HashSet<>();

    /**
     * A person of a role or calling, made where asked: dressed and armed as that kind of person is, at a fitting level
     * (or the one asked for), at home where they stand, under the jurisdiction of the ground. Null: no such kind.
     */
    private Npc person(String want, float x, float z, int level) {
        Npc.Role role = null; rpg.ai.Profession calling = null;
        for (rpg.ai.Profession pr : rpg.ai.Profession.values()) if (norm(pr.name()).equals(want) || norm(pr.title).equals(want)) { calling = pr; role = pr.family; }
        if (role == null) for (Npc.Role r : Npc.Role.values()) if (r != Npc.Role.MONSTER && norm(r.name()).equals(want)) role = r;
        if (role == null) return null;
        String title = calling != null ? calling.title : role.name().charAt(0) + role.name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        Npc n = new Npc(g.state.newEntityId(), title, role, title, x, z);
        if (calling != null) calling.assign(n);
        boolean fighter = role == Npc.Role.GUARD || role == Npc.Role.KNIGHT || role == Npc.Role.LAWMAN || role == Npc.Role.BANDIT || role == Npc.Role.BRUTE || role == Npc.Role.HUNTER;
        int lv = level > 0 ? Math.min(99, level) : switch (role) { case KNIGHT -> 45; case GUARD, LAWMAN -> 35; case BRUTE -> 28; case BANDIT -> 15; case HUNTER -> 22; default -> 1; };
        for (Skill s : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENSE, Skill.ARCHERY}) n.skills.setLevel(s, lv);
        n.skills.setLevel(Skill.HEALTH, Math.max(10, fighter ? lv : 10));
        n.look = rpg.actor.Appearance.random(n.id, (n.id & 1) == 0);
        String[] kit = switch (role) {
            case GUARD, LAWMAN, KNIGHT -> new String[]{"steel_sword", "steel_shield", "steel_body"};
            case BANDIT -> new String[]{"iron_sword"};
            case BRUTE -> new String[]{"iron_battleaxe"};
            case HUNTER -> new String[]{"shortbow"};
            default -> new String[0];
        };
        for (String id : kit) { ItemDef d = Items.find(id); if (d != null) { n.eq.set(d.slot, new rpg.item.ItemStack(d, 1)); g.state.mint(d, 1); } }
        if (role == Npc.Role.HUNTER && Items.find("bronze_arrows") != null) n.eq.set(ItemDef.Slot.AMMO, new rpg.item.ItemStack(Items.get("bronze_arrows"), 30));
        n.homeJurisdiction = DevMap.jurisdiction(x, z);
        n.hostileToPlayer = role == Npc.Role.BANDIT || role == Npc.Role.BRUTE;
        n.fitBody(); n.restore();
        n.yaw = (float) Math.atan2(x - g.player.x, z - g.player.z);
        g.npcs.add(n); g.ai.spawnBody(n);
        spawned.add(n.id);
        g.state.commit("ConsoleSpawned", g.player.id, n.id, n.homeJurisdiction, 0, title + " level " + lv);
        return n;
    }

    private static Map<String, float[]> places() {
        Map<String, float[]> spots = new LinkedHashMap<>();
        spots.put("start", new float[]{DevMap.SPAWN_X, DevMap.SPAWN_Z}); spots.put("combat", new float[]{30.5f, 48.5f}); spots.put("skills", new float[]{66.5f, 29.5f});
        spots.put("yard", new float[]{100.5f, 68.5f}); spots.put("economy", new float[]{30.5f, 100.5f}); spots.put("quest", new float[]{61.5f, 98.5f});
        spots.put("farm", new float[]{(DevMap.FARM_X0 + DevMap.FARM_X1) / 2f, DevMap.FARM_Z0 - 2}); spots.put("wilds", new float[]{24.5f, 28.5f}); spots.put("pond", new float[]{96.5f, 43.5f});
        spots.put("graveyard", new float[]{DevMap.GRAVEYARD_SPAWN[0], DevMap.GRAVEYARD_SPAWN[1]}); spots.put("fence", new float[]{DevMap.FENCE_POST[0] + 1.5f, DevMap.FENCE_POST[1] - 1.5f});
        // inside each dungeon, at its way out (goto warren, goto catacombs ...)
        for (var l : rpg.world.DungeonLayout.all()) spots.put(norm(l.name.replace("Goblin ", "").replace("Spider ", "").replace("Old ", "").replace("'s", "s")), new float[]{l.worldX(l.entryX) + 1.7f, l.worldZ(l.entryZ) + 0.5f});
        return spots;
    }

    // ------------------------------------------------------------------ items by id or name
    /** The item meant: by id, by name, with case, spaces and underscores ignored; a near miss suggests the closest. */
    ItemDef item(String text) {
        String want = norm(text);
        for (ItemDef d : Items.all()) if (norm(d.id).equals(want) || norm(d.name).equals(want)) return d;
        List<ItemDef> hits = new ArrayList<>();
        for (ItemDef d : Items.all()) if (norm(d.id).contains(want) || norm(d.name).contains(want)) hits.add(d);
        if (hits.size() == 1) return hits.get(0);
        if (hits.isEmpty()) {
            ItemDef best = null; int bd = Integer.MAX_VALUE;
            for (ItemDef d : Items.all()) { int dd = DevConsole.distance(norm(d.id), want); if (dd < bd) { bd = dd; best = d; } }
            print("give: no item '" + text + "'" + (best != null ? " - did you mean " + best.id + "?" : ""), ERR);
            return null;
        }
        StringBuilder sb = new StringBuilder("give: '" + text + "' could be ");
        for (int i = 0; i < Math.min(8, hits.size()); i++) sb.append(i == 0 ? "" : ", ").append(hits.get(i).id);
        print(sb.append(hits.size() > 8 ? " ..." : "").toString(), WARN);
        return null;
    }

    private void give(ItemDef d, int n) {
        // a stackable goes as one stack; anything else one at a time (what does not fit is at your feet)
        if (d.stackable) g.give(g.player, d, n);
        else for (int i = 0; i < n; i++) g.give(g.player, d, 1);
        print("Gave " + n + " x " + d.name + " (" + d.id + ")", GOOD);
    }

    private static String norm(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-']", ""); }

    // ------------------------------------------------------------------ drawing (only while it is shown)
    public void draw(Ui2D ui, int w, int h, float dt) {
        slide += ((core.open ? 1 : 0) - slide) * Math.min(1, dt * 14);
        if (slide < 0.01f) return;
        float ch = h * 0.46f, top = -ch * (1 - slide);
        ui.rect(0, top, w, ch, 0xE6101216);
        ui.rect(0, top + ch - 2, w, 2, 0xFF3A4048);
        float lh = 16, y = top + ch - 34 - lh;
        String ft = core.get("console_filter").value.toLowerCase(Locale.ROOT);
        var it = core.newestFirst();
        for (int skip = 0; skip < core.scroll() && it.hasNext(); skip++) it.next();
        while (it.hasNext() && y > top + 4) {
            DevConsole.Line l = it.next();
            if (!ft.isEmpty() && !l.text().toLowerCase(Locale.ROOT).contains(ft)) continue;
            ui.text(l.text(), 12, y, 13, l.color());
            y -= lh;
        }
        if (core.scroll() > 0) ui.text("^ ^ ^  (" + core.scroll() + " lines below)  ^ ^ ^", w / 2f - 90, top + ch - 50, 11, DIM);
        // the prompt, with a blinking caret, and the completions under it
        float py = top + ch - 26;
        ui.rect(8, py - 3, w - 16, 22, 0xFF1C2026);
        boolean caret = (System.nanoTime() / 500_000_000L) % 2 == 0;
        ui.text("] " + core.inputText() + (caret ? "_" : ""), 14, py, 14, TEXT);
        List<String> m = core.matches();
        if (!m.isEmpty() && core.open) {
            float my = top + ch + 4;
            int n = Math.min(10, m.size());
            ui.rect(14, my, 420, n * 17 + 6, 0xF0181B20);
            for (int i = 0; i < n; i++) {
                String nm = m.get(i);
                var v = core.get(nm);
                ui.text(nm + (v != null && core.isVar(nm) ? "  " + v.value : ""), 20, my + 3 + i * 17, 13, v != null ? ECHO : TEXT);
            }
        }
    }

    /** The living body nearest the middle of the view (within a few degrees), out to a range: anyone, any animal, any monster. */
    private Actor sighted(float range) {
        float yaw = g.controller.camYaw, pitch = g.controller.camPitch;
        float dx = (float) (-Math.sin(yaw) * Math.cos(pitch)), dy = (float) Math.sin(pitch), dz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        Actor best = null; float bestCos = 0.985f;
        for (Actor v : g.actors()) {
            if (v == g.player || !v.alive || v.dummy) continue;
            float rx = v.x - g.player.x, ry = v.chestY() - g.player.eyeY(), rz = v.z - g.player.z, d = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (d < 0.01f || d > range) continue;
            float c = (rx * dx + ry * dy + rz * dz) / d;
            if (c > bestCos) { bestCos = c; best = v; }
        }
        return best;
    }
}
