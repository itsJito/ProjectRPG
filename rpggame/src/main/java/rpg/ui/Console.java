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
        rpg.DebugContent content = new rpg.DebugContent(g);
        cmd("features", "features [filter]: searchable creature, NPC, object, spell, prayer and dungeon catalog.", a -> {
            String filter=a.length>1?DevConsole.join(a,1).toLowerCase(Locale.ROOT):"";
            content.catalog().forEach((id,label)->{if((id+" "+label).toLowerCase(Locale.ROOT).contains(filter))print(id+" - "+label,TEXT);});
        });
        cmd("spawn", "spawn <id> [level|baby]: monster:goblin, npc:carpenter, animal:wolf, object:<fixture-id>, furniture:bed. 'features' lists all IDs.", a -> {
            if(a.length<2){print("Use features to list spawn IDs.",WARN);return;}
            float x=g.player.x-(float)Math.sin(g.controller.camYaw)*3,z=g.player.z-(float)Math.cos(g.controller.camYaw)*3;
            print("Spawned "+content.spawn(a[1],a.length>2&&a[2].equalsIgnoreCase("baby"),a.length>2?(int)DevConsole.num(a[2],0):0,x,z),GOOD);
        });
        core.completer("spawn", t -> content.catalog().keySet().stream().filter(id->!id.startsWith("spell:")&&!id.startsWith("prayer:")&&!id.startsWith("dungeon:")&&id.startsWith(t)).toList());
        cmd("interact", "interact <entity-id> [action]: lists or performs any entity's registered interactions.",a->{
            if(a.length<2)throw new IllegalArgumentException("interact <entity-id> [action]");
            long id=Long.parseLong(a[1]);var actor=g.actor(id);rpg.interact.Interactable target=actor instanceof rpg.interact.Interactable entity?entity:g.objects.get(id);
            if(target==null)throw new IllegalArgumentException("No such entity.");
            if(a.length<3){for(var option:target.options(g,g.player))print(option.toString(),TEXT);return;}
            var result=g.interact.request(g.player,target,a[2]);print(result.rejected()?result.reason():"Committed "+a[2],result.rejected()?ERR:GOOD);
        });
        cmd("cast", "cast <spell-id>: casts any registered spell through its normal resource and skill checks.",a->{if(a.length<2)throw new IllegalArgumentException("Use features spell");var spell=rpg.combat.Spells.Spell.valueOf(a[1].replace("spell:","").toUpperCase(Locale.ROOT));g.combat.cast(g.player,spell,g.controller.camYaw,g.controller.camPitch);});
        cmd("prayer", "prayer <id>: toggles a registered prayer through normal checks.",a->{if(a.length<2)throw new IllegalArgumentException("Use features prayer");g.prayers.toggle(g.player,rpg.combat.Spells.Prayer.valueOf(a[1].replace("prayer:","").toUpperCase(Locale.ROOT)));});
        cmd("dungeon", "dungeon <id>: enters a registered dungeon through its actual entrance and scaling rules.",a->{if(a.length<2)throw new IllegalArgumentException("Use features dungeon");int id=Integer.parseInt(a[1]);for(var door:g.dungeons.doors)if(!door.inside&&door.layout.id==id){g.teleport(g.player,door.x,DevMap.FLOOR,door.z-1.3f);g.interact.request(g.player,door,"enter");return;}throw new IllegalArgumentException("No such dungeon.");});
        cmd("ragdoll", "Hands the player to Sydney's physical fall and recovery controller.",a->{g.reactions.falling(g.player,0);if(g.player.ragdoll!=0)g.game.commands().ragdollControl(g.player.ragdoll,astratto.bridge.AstrattoNative.CONTROL_COLLAPSE,1);});
        cmd("weather", "weather <kind>: sets a registered weather state.",a->{if(a.length<2){for(var kind:astratto.weather.Weather.Kind.values())print(kind.name(),TEXT);return;}g.timeSystem.weather.set(astratto.weather.Weather.Kind.valueOf(a[1].toUpperCase(Locale.ROOT)));});
        cmd("block", "block <x> <y> <z> <block-id>: changes an engine world cell.",a->{if(a.length!=5)throw new IllegalArgumentException("block <x> <y> <z> <block-id>");int id=Integer.parseInt(a[4]);if(id<0||id>=rpg.world.Blocks.COUNT)throw new IllegalArgumentException("Invalid block ID");g.setBlock(Integer.parseInt(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3]),id);});
        cmd("remove", "Removes what you are looking at (an item, an animal).", a -> {
            var f = g.interact.focus;
            if (f instanceof rpg.objects.GroundItem gi) { gi.removed = true; g.state.burn(gi.stack.def, gi.stack.amount); print("removed " + gi.stack, TEXT); }
            else if (f instanceof rpg.animal.Animal an) { g.animals.remove(an); print("removed " + an.kind(), TEXT); }
            else print("remove: look at an item or an animal", WARN);
        });
        cmd("slay", "Kills what you are looking at (a person or an animal).", a -> {
            if (g.interact.focus instanceof Actor v && v.alive && !v.dummy) g.combat.bleedOutNow(v); else print("slay: look at someone", WARN);
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

    private static Map<String, float[]> places() {
        Map<String, float[]> spots = new LinkedHashMap<>();
        spots.put("start", new float[]{DevMap.SPAWN_X, DevMap.SPAWN_Z}); spots.put("combat", new float[]{30.5f, 48.5f}); spots.put("skills", new float[]{66.5f, 29.5f});
        spots.put("yard", new float[]{100.5f, 68.5f}); spots.put("economy", new float[]{30.5f, 100.5f}); spots.put("quest", new float[]{61.5f, 98.5f});
        spots.put("farm", new float[]{(DevMap.FARM_X0 + DevMap.FARM_X1) / 2f, DevMap.FARM_Z0 - 2}); spots.put("wilds", new float[]{24.5f, 28.5f}); spots.put("pond", new float[]{96.5f, 43.5f});
        spots.put("graveyard", new float[]{DevMap.GRAVEYARD_SPAWN[0], DevMap.GRAVEYARD_SPAWN[1]}); spots.put("fence", new float[]{DevMap.FENCE_POST[0] + 1.5f, DevMap.FENCE_POST[1] - 1.5f});
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
        Ui2D.Face was = ui.use(ui.chat);
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
        ui.use(was);
    }
}
