package rpg;

import astratto.Astratto;
import astratto.bridge.AstrattoNative;
import astratto.bridge.NativeEngine;
import astratto.game.Game;
import astratto.platform.Input;
import astratto.platform.Window;
import java.io.File;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import rpg.actor.Appearance;
import rpg.render.Gfx;
import rpg.ui.Creator;
import rpg.ui.Dialog;
import rpg.ui.Hud;
import rpg.ui.TitleScreen;
import rpg.ui.Ui2D;
import rpg.world.DevMap;

import static org.lwjgl.glfw.GLFW.*;

/**
 * SHADOWVEIL's devmap prototype on the Astratto Engine.
 * <pre>
 *   ./gradlew run          the character creator, then the devmap
 *   ./gradlew acceptance   off-screen: every GDD 4.2 end-to-end flow, scripted and checked, with screenshots
 * </pre>
 * Default controls (remappable): WASD walk, Shift sprint, Caps Lock jog, Alt+S quick turn, Space jump, Q dodge, mouse look; LMB main hand (tap light, hold heavy, draw a
 * bow), RMB off hand (guard, or the off-hand item); R cast; 1-3 blessings; E interact (hold for every option); V camera;
 * Tab tapped frees the mouse for the interface, held opens the radial wheel; F1-F4/F7 panels; F5/F6 save/load; F8-F11 developer inspectors; Esc menus.
 */
public final class Main {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> crashLog(e, null));
        boolean acceptance = false, hidden = false, gallery = false, female = false;
        bench = false;
        String shots = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--acceptance" -> acceptance = true;
                case "--hidden" -> hidden = true;
                case "--shots" -> shots = args[++i];
                case "--gallery" -> gallery = true;
                case "--female" -> female = true;
                case "--bench" -> bench = true;
                default -> { System.err.println("unknown argument " + args[i]); System.exit(2); }
            }
        }
        System.exit(run(acceptance, gallery, female, hidden, shots));
    }

    /** --bench: the frame-time benchmark (see Bench), in a 1920 x 1080 window. */
    static boolean bench;

    static int run(boolean acceptance, boolean gallery, boolean female, boolean hidden, String shots) {
        System.out.println(Astratto.NAME + " " + Astratto.VERSION + " - SHADOWVEIL" + (Astratto.javaFallbackAllowed() ? "" : " (Java fallbacks disabled)"));
        // the recordings decode on background threads while the window, the engine and the intro come up
        rpg.audio.Sfx.warm();
        Window win = new Window();
        win.show = !hidden;
        win.appId = "Hoodie.Shadowveil";
        win.dress = Main::programIcon;
        if (bench) win.create("SHADOWVEIL - bench", 1920, 1080); else win.create("SHADOWVEIL", 1600, 900);
        if (!win.gl45) { System.err.println("the native renderer needs OpenGL 4.5: " + win.glInfo); return 1; }
        int status;
        // play goes round from the title screen to a session and back: each session a fresh engine and world in the one window
        while (true) {
            int[] out = new int[1];
            boolean again = session(win, acceptance, gallery, female, shots, out);
            status = out[0];
            if (!again || win.shouldClose()) break;
        }
        win.destroy();
        return status;
    }

    /** One engine, world and renderer, run to the end; true when play asked to go back to the title screen. */
    private static boolean session(Window win, boolean acceptance, boolean gallery, boolean female, String shots, int[] status) {
        NativeEngine.Config cfg = new NativeEngine.Config();
        cfg.audioDevice = !acceptance && !gallery;
        // RPG_NONDET=1: an automated run on every worker thread, as play has it (to catch what only threading shows)
        cfg.deterministic = (acceptance || gallery) && System.getenv("RPG_NONDET") == null;
        long boot = System.nanoTime();
        Game game;
        try { game = Game.create(cfg); }
        catch (IllegalStateException e) { System.err.println(e.getMessage()); status[0] = 1; return false; }
        System.out.println(AstrattoNative.buildInfo());
        System.out.printf("[Load] native services %.2fs%n", (System.nanoTime() - boot) / 1e9);
        boot = System.nanoTime();
        // (0.4.0) the map: ShadowVeil, the permanent world, unless the devmap is asked for - by the settings (World: Devmap),
        // by RPG_MAP=devmap, or by the acceptance and gallery runs, whose checks and pictures are of the devmap
        // (RPG_MAP=shadowveil runs them on ShadowVeil)
        String asked = System.getenv("RPG_MAP");
        boolean devmap = asked != null ? rpg.world.WorldMap.parse(asked) == rpg.world.WorldMap.Kind.DEVMAP
                : acceptance || gallery || Preferences.load(java.nio.file.Path.of("saves", "preferences.json")).legacyDevmap;
        long world = devmap ? DevMap.build(game.engine()) : rpg.world.shadowveil.ShadowVeil.build(game.engine());
        game.activate(world);
        System.out.printf("[Load] %s %.2fs%n", rpg.world.WorldMap.active().title, (System.nanoTime() - boot) / 1e9);
        boot = System.nanoTime();
        Gfx gfx = new Gfx(game.engine(), win.width, win.height);
        Ui2D ui = new Ui2D(gfx.renderer());
        System.out.printf("[Load] renderer and UI %.2fs%n", (System.nanoTime() - boot) / 1e9);
        boolean again = false;
        try {
            if (acceptance) status[0] = new Acceptance(win, game, gfx, ui, shots).run();
            else if (gallery) status[0] = new Gallery(win, game, gfx, ui, shots).run(female);
            else if (bench) status[0] = new Bench(win, game, gfx, ui).run();
            else again = play(win, game, gfx, ui, world);
        } finally {
            ui.free();
            gfx.close();
            game.close();
        }
        return again;
    }

    private static boolean brandShown;
    private static void splash(Window win, Game game, Gfx gfx, Ui2D ui, Gfx.Preview stage) {
        double elapsed = 0;long started=System.nanoTime(),last=started;
        while (!win.shouldClose() && elapsed < 5.4) {
            win.poll();float dt=frameDt(last);last=System.nanoTime();elapsed=(last-started)/1e9;game.tick(dt);
            gfx.title(stage, null, win.width, win.height, dt, game.time());
            ui.begin(gfx.cmd); ui.rect(0,0,win.width,win.height,0xFF07090C);
            float alpha = (float)Math.max(0,Math.min(1,Math.min(elapsed/0.7,(5.4-elapsed)/0.7))); float size = Math.min(300,win.height*0.42f);
            ui.rectTex(ui.engineLogo,(win.width-size)/2,(win.height-size)/2-25,size,size,0,0,1,1,rpg.ui.Style.fade(0xFFFFFFFF,alpha));
            ui.centered("ASTRATTO ENGINE",win.width/2f,win.height/2f+size/2,18,rpg.ui.Style.fade(0xFFD8B46A,alpha));
            ui.end(); gfx.frame(win.width,win.height); win.swap(); win.input.endFrame();
        }
        System.out.printf("[Load] engine intro %.2fs%n",(System.nanoTime()-started)/1e9);
    }

    // ------------------------------------------------------------------ play
    /** Plays from the title screen; true to come back to it (Quit to title), false to leave the game. */
    private static boolean play(Window win, Game game, Gfx gfx, Ui2D ui, long world) {
        Input in = win.input;
        Gfx.Preview preview = new Gfx.Preview();
        long dungeon = rpg.world.TitleStage.build(game.engine());
        preview.world = dungeon; preview.dungeon = true; preview.x = rpg.world.TitleStage.STAND_X; preview.y = DevMap.FLOOR; preview.z = rpg.world.TitleStage.STAND_Z;
        long last = System.nanoTime();
        win.setCursorLocked(false);
        rpg.audio.Music music = game.add(new rpg.audio.Music(game));
        gfx.findLamps(new rpg.world.Voxels(game.view()));
        // ---- the title screen: the profiles
        TitleScreen title = new TitleScreen(VERSION);
        title.sound = n -> { var sfx = rpg.audio.Sfx.ready(game); if (sfx != null) sfx.ui(n, 0.5f); };
        Gfx.Preview stage = new Gfx.Preview();
        stage.world = dungeon; stage.dungeon = true; stage.x = rpg.world.TitleStage.STAND_X; stage.y = DevMap.FLOOR; stage.z = rpg.world.TitleStage.STAND_Z;
        rpg.ui.SettingsPanel menuSettings = new rpg.ui.SettingsPanel(Preferences.load(java.nio.file.Path.of("saves", "preferences.json")), p -> {
            gfx.menuPreferences = p; p.mix(game.commands()); win.setFullscreen(p.fullscreen); win.setVsync(p.vsync);
        }, true);
        title.settings = menuSettings::show;
        if (!brandShown) { brandShown = true; splash(win, game, gfx, ui, stage); }
        // the sound bank, built at the cut from the intro to the title (its recordings decoded meanwhile), so no click pays for it
        rpg.audio.Sfx.shared(game);

        Profiles.Profile profile = null;
        boolean fresh = false;
        while (!win.shouldClose()) {
            win.poll();
            float dt = frameDt(last); last = System.nanoTime();
            mouse(ui, in);
            ui.doubleClick = in.mousePressed(GLFW_MOUSE_BUTTON_LEFT) && System.nanoTime() - lastClick < 350_000_000L;
            if (in.mousePressed(GLFW_MOUSE_BUTTON_LEFT)) lastClick = System.nanoTime();
            music.update(rpg.audio.Music.Mood.MENU, dt);
            music.updateStingers(dt);
            game.tick(dt);
            gfx.title(stage, title.selected == null ? null : title.selected.look, win.width, win.height, dt, game.time());
            ui.begin(gfx.cmd);
            if (menuSettings.open) {
                boolean capture = menuSettings.capturingKey(); menuSettings.keyInput(in); if (capture) ui.click = ui.rclick = false;
                menuSettings.draw(ui, win.width, win.height); if (!capture && in.keyPressed(GLFW_KEY_ESCAPE)) menuSettings.escape();
                TitleScreen.cursor(ui);
            } else title.draw(ui, win.width, win.height, dt, in.keyPressed(GLFW_KEY_ENTER), in.keyPressed(GLFW_KEY_ESCAPE));
            ui.end();
            gfx.frame(win.width, win.height);
            win.swap();
            in.endFrame();
            if (title.choice == TitleScreen.Choice.QUIT) { music.close(); return false; }
            if (title.choice == TitleScreen.Choice.CONTINUE && title.selected != null) { profile = title.selected; break; }
            if (title.choice == TitleScreen.Choice.NEW) {
                // ---- the character creator, for a new profile (Esc returns here)
                Creator creator = creator(win, game, gfx, ui, preview, music, in);
                last = System.nanoTime();
                title.choice = TitleScreen.Choice.NONE;
                if (creator == null) { title.refresh(); continue; }
                profile = Profiles.create(creator.name.toString().trim(), creator.look);
                if (profile == null) { title.refresh(); continue; }
                fresh = true;
                break;
            }
        }
        if (win.shouldClose() || profile == null) { music.close(); return false; }
        long loadStarted = System.nanoTime();
        Rpg g = game.add(new Rpg(game, profile.name, profile.look, false));
        g.profile = profile;
        // Dynamic resolution targets the configured FPS budget (60 by default), independently of presentation refresh rate.
        g.music = music;
        Hud hud = new Hud(g, gfx);
        g.ui = hud;
        gfx.findLamps(g.vox);
        gfx.preload(g.npcs); gfx.preload(java.util.List.of(g.player));
        if (!fresh && profile.hasSave() && !new SaveSystem(g).load(profile.save())) g.chat.warn("The save for " + profile.name + " could not be loaded: a fresh start.");
        // (0.4.1) an older version's save brings its character into this version's world (SaveSystem.load carries it over)
        if (fresh) new SaveSystem(g).save(profile.save());
        preloadAll(g, gfx);
        warmFrames(g, gfx, ui, hud, win.width, win.height);
        System.out.printf("[Load] session, audio, figures and save %.2fs%n", (System.nanoTime() - loadStarted) / 1e9);
        boolean[] toTitle = {false};
        hud.onTitle = () -> { toTitle[0] = true; win.requestClose(); };
        double sessionStart = g.time;
        try { gameLoop(win, game, gfx, ui, g, hud, music, in); } finally { hud.close(); }
        // the profile is saved as it is left (for the title, or out of the game)
        if (g.player.alive) new SaveSystem(g).save(profile.save());
        Profiles.played(profile, g, g.time - sessionStart);
        music.close();
        if (toTitle[0]) { win.cancelClose(); return true; }
        return false;
    }

    private static long lastClick;

    /** The character creator; null when backed out of. */
    private static Creator creator(Window win, Game game, Gfx gfx, Ui2D ui, Gfx.Preview preview, rpg.audio.Music music, Input in) {
        Creator creator = new Creator();
        creator.sound = n -> { var sfx = rpg.audio.Sfx.ready(game); if (sfx != null) sfx.ui(n, 0.5f); };
        long last = System.nanoTime();
        while (!win.shouldClose() && !creator.done && !creator.cancelled) {
            win.poll();
            float dt = frameDt(last); last = System.nanoTime();
            mouse(ui, in);
            music.update(rpg.audio.Music.Mood.MENU, dt);
            music.updateStingers(dt);
            game.tick(dt);
            preview.focus = creator.focus; preview.aim = creator.aim; preview.zoom = creator.zoom;
            gfx.creator(preview, creator.look, creator.turn, win.width, win.height, dt, game.time());
            ui.begin(gfx.cmd);
            creator.draw(ui, win.width, win.height, in.typed, in.keyPressed(GLFW_KEY_BACKSPACE), in.keyPressed(GLFW_KEY_ENTER));
            if (in.keyPressed(GLFW_KEY_ESCAPE)) creator.cancelled = true;
            ui.end();
            gfx.frame(win.width, win.height);
            win.swap();
            in.endFrame();
        }
        return creator.done && !win.shouldClose() ? creator : null;
    }

    /** The game itself, until the window is asked to close. */
    private static void gameLoop(Window win, Game game, Gfx gfx, Ui2D ui, Rpg g, Hud hud, rpg.audio.Music music, Input in) {
        long last = System.nanoTime();
        boolean cursorFree = false;
        win.setCursorLocked(true);
        double eHeld = -1;
        boolean eMenuOpened = false, eSwallow = false;
        // a mouse button is the body's only when it went down while the mouse was looking (not on a menu): the click
        // that picks a dialogue choice or closes a window is never also a swing when the menu closes under it
        boolean mainArmed = false, offArmed = false, wasPaused = false, crouchToggle = false, sprintToggle = false;
        double tabAt = -1;
        hud.settings.displayChanged = () -> { win.setFullscreen(hud.settings.values.fullscreen); win.setVsync(hud.settings.values.vsync); };
        hud.settings.displayChanged.run();
        hud.onQuit = win::requestClose;
        while (!win.shouldClose()) {
            long frameStart = System.nanoTime();
            win.poll();
            float dt = frameDt(last); last = System.nanoTime();
            var preferences = hud.settings.values;
            boolean free = cursorFree || hud.captures();
            win.setCursorLocked(!free);
            mouse(ui, in);
            if (free) { mainArmed = false; offArmed = false; }
            else {
                if (preferences.pressed(in, Preferences.Action.ATTACK)) mainArmed = true;
                if (preferences.pressed(in, Preferences.Action.GUARD)) offArmed = true;
            }
            if (!preferences.down(in, Preferences.Action.ATTACK)) mainArmed = false;
            if (!preferences.down(in, Preferences.Action.GUARD)) offArmed = false;
            var it = g.controller.in;
            // typed text goes to the interface when its filter box has the focus; then the keys are not the body's
            StringBuilder typed = new StringBuilder();
            for (char ch : in.typed) typed.append(ch);
            // the developer console: ` opens and closes it; open, it has the whole keyboard
            if (preferences.pressed(in, Preferences.Action.CONSOLE)) hud.console.toggle();
            else if (hud.console.isOpen()) hud.console.input(typed.toString(), preferences.pressed(in, Preferences.Action.CONFIRM) || in.keyPressed(GLFW_KEY_KP_ENTER), preferences.pressed(in, Preferences.Action.BACKSPACE),
                    preferences.pressed(in, Preferences.Action.NAV_UP), preferences.pressed(in, Preferences.Action.NAV_DOWN), preferences.pressed(in, Preferences.Action.COMPLETE), preferences.pressed(in, Preferences.Action.INSPECT_PREVIOUS) ? 1 : preferences.pressed(in, Preferences.Action.INSPECT_NEXT) ? -1 : 0);
            if (hud.console.isOpen()) typed.setLength(0);
            hud.input(typed.toString(), !hud.console.isOpen() && preferences.pressed(in, Preferences.Action.BACKSPACE), !hud.console.isOpen() && preferences.pressed(in, Preferences.Action.ROTATE));
            hud.shift = in.keyDown(GLFW_KEY_LEFT_SHIFT) || in.keyDown(GLFW_KEY_RIGHT_SHIFT) || (in.clickMods & GLFW_MOD_SHIFT) != 0;
            boolean typing = hud.typing();
            boolean rebinding = hud.settings.capturingKey();
            hud.settings.keyInput(in);
            if (rebinding) { ui.click = ui.rclick = false; }
            // the console's binds: a bound key pressed in play runs its command
            if (!typing && !hud.console.binds().isEmpty()) for (var b : hud.console.binds().keySet().toArray(new String[0])) { int key = keyCode(b); if (key >= 0 && in.keyPressed(key)) hud.console.key(b); }
            // the keyboard: movement always; the hands only while the mouse looks
            it.forward = typing ? 0 : (preferences.down(in, Preferences.Action.FORWARD) ? 1 : 0) - (preferences.down(in, Preferences.Action.BACK) ? 1 : 0);
            it.strafe = typing ? 0 : (preferences.down(in, Preferences.Action.RIGHT) ? 1 : 0) - (preferences.down(in, Preferences.Action.LEFT) ? 1 : 0);
            it.sprint = !typing && preferences.down(in, Preferences.Action.SPRINT);
            // The quick turn is its own remappable chord (Alt+S by default).
            boolean turnBack = !typing && preferences.pressed(in, Preferences.Action.QUICK_TURN);
            if (!typing && preferences.pressed(in, Preferences.Action.CROUCH)) crouchToggle = !crouchToggle;
            if (!typing && preferences.pressed(in, Preferences.Action.SPRINT)) sprintToggle = !sprintToggle;
            it.sneak = !typing && !turnBack && (preferences.toggleCrouch ? crouchToggle : preferences.down(in, Preferences.Action.CROUCH));
            if (preferences.toggleSprint) it.sprint = !typing && sprintToggle;
            it.jogToggle = !typing && preferences.pressed(in, Preferences.Action.JOG);
            // (0.2.2) F held a moment: sheathe or draw (a tap does nothing, so a stray press never bares a blade in town)
            if (!typing && preferences.down(in, Preferences.Action.SHEATHE)) { sheatheHeld += dt; if (sheatheHeld >= 0.3f && !sheatheFired) { it.sheathe = true; sheatheFired = true; } }
            else { sheatheHeld = 0; sheatheFired = false; }
            it.jump = !typing && preferences.pressed(in, Preferences.Action.JUMP);
            it.jumpHeld = !typing && preferences.down(in, Preferences.Action.JUMP);
            it.dodge = !typing && preferences.pressed(in, Preferences.Action.DODGE);
            it.turnBack = turnBack;
            it.cast = !free && preferences.pressed(in, Preferences.Action.CAST);
            it.toggleCamera = preferences.pressed(in, Preferences.Action.CAMERA);
            it.mainDown = !free && mainArmed;
            it.offDown = !free && offArmed;
            float sens = 0.0025f * Math.max(0.05f, hud.console.f("sensitivity"));
            if (!free) { it.lookYaw = -(float) in.dx * sens; it.lookPitch = (preferences.invertY ? 1 : -1) * (float) in.dy * sens; }
            if (!free && in.scroll != 0) g.player.camDist = Math.max(1.6f, Math.min(8, g.player.camDist - (float) in.scroll * 0.4f));
            for (int k = 0; k < 10 && !typing; k++) if ((hud.dialog != null || hud.menuOpen() ? preferences.pressed(in, Preferences.Action.values()[Preferences.Action.CHOICE_1.ordinal() + k]) : preferences.pressed(in, Preferences.Action.values()[Preferences.Action.HOTBAR_1.ordinal() + k]))) {
                if (hud.dialog != null || hud.menuOpen()) hud.choose(k + (hud.dialog != null && !hud.dialog.pauseMenu ? hud.dialog.choiceOffset : 0));
                else if (!hud.squadOpen()) hud.hotkey(k);
            }
            if (hud.dialog != null && hud.dialog.pauseMenu) {
                Dialog d = hud.dialog;
                if (preferences.pressed(in, Preferences.Action.NAV_UP)) d.focusedChoice = (d.focusedChoice + d.choices.size() - 1) % d.choices.size();
                if (preferences.pressed(in, Preferences.Action.NAV_DOWN)) d.focusedChoice = (d.focusedChoice + 1) % d.choices.size();
                if (preferences.pressed(in, Preferences.Action.CONFIRM) || in.keyPressed(GLFW_KEY_KP_ENTER)) hud.choose(d.focusedChoice);
            }
            // the scenes: inventory, character, skills, journal, magic
            if (!typing && !(hud.dialog != null && hud.dialog.pauseMenu)) {
                if (preferences.pressed(in, Preferences.Action.INVENTORY)) hud.toggle(Hud.Scene.INVENTORY);
                if (preferences.pressed(in, Preferences.Action.CHARACTER)) hud.toggle(Hud.Scene.CHARACTER);
                if (preferences.pressed(in, Preferences.Action.SKILLS)) hud.toggle(Hud.Scene.SKILLS);
                if (preferences.pressed(in, Preferences.Action.JOURNAL)) hud.toggle(Hud.Scene.JOURNAL);
                if (preferences.pressed(in, Preferences.Action.MAP)) hud.toggle(Hud.Scene.MAP);
                if (preferences.pressed(in, Preferences.Action.MAGIC)) hud.toggle(Hud.Scene.MAGIC);
                if (preferences.pressed(in, Preferences.Action.FAITH)) hud.toggle(Hud.Scene.FAITH);
            }
            if (!typing) {
                if (preferences.pressed(in, Preferences.Action.PANEL_CHARACTER)) hud.openTabKey(0);
                if (preferences.pressed(in, Preferences.Action.PANEL_SKILLS)) hud.openTabKey(1);
                if (preferences.pressed(in, Preferences.Action.PANEL_JOURNAL)) hud.openTabKey(2);
                if (preferences.pressed(in, Preferences.Action.PANEL_INVENTORY)) hud.openTabKey(3);
                if (preferences.pressed(in, Preferences.Action.PANEL_MAP)) hud.openTabKey(6);
            }
            // (0.4.6, gamefixes) E with a container or the inventory open closes it (and does nothing else that press)
            boolean eCloses = !typing && preferences.pressed(in, Preferences.Action.INTERACT) && hud.dialog == null && hud.scene == Hud.Scene.INVENTORY;
            if (eCloses) { hud.closeAll(); eHeld = -1; eSwallow = true; }
            // E: a tap is the primary action, a hold opens every option
            if (!typing && !eCloses && !eSwallow && preferences.pressed(in, Preferences.Action.INTERACT)) { eHeld = g.time; eMenuOpened = false; }
            if (eHeld >= 0 && preferences.down(in, Preferences.Action.INTERACT) && !eMenuOpened && g.time - eHeld > 0.3) {
                hud.openInteractMenu();
                eMenuOpened = true;
            }
            if (preferences.released(in, Preferences.Action.INTERACT)) { if (!eMenuOpened && !hud.captures() && !eSwallow) it.interact = true; eHeld = -1; eSwallow = false; }
            if (!typing && preferences.pressed(in, Preferences.Action.XP_INSPECTOR)) hud.xpDashboard = !hud.xpDashboard;
            if (!typing && preferences.pressed(in, Preferences.Action.AI_INSPECTOR)) hud.aiInspector = !hud.aiInspector;
            if (!typing && preferences.pressed(in, Preferences.Action.STATE_INSPECTOR)) hud.stateInspector = !hud.stateInspector;
            if (!typing && preferences.pressed(in, Preferences.Action.TELEMETRY)) hud.telemetry = !hud.telemetry;
            if (!typing && preferences.pressed(in, Preferences.Action.INSPECT_PREVIOUS)) hud.nextInspected(-1);
            if (!typing && preferences.pressed(in, Preferences.Action.INSPECT_NEXT)) hud.nextInspected(1);
            // TAB: a tap frees the mouse for the interface (or takes it back); held, the radial wheel opens in play and
            // what it points at is used when TAB is let go
            if (!typing && preferences.pressed(in, Preferences.Action.QUICK_WHEEL)) tabAt = g.time;
            if (tabAt >= 0 && preferences.down(in, Preferences.Action.QUICK_WHEEL) && g.time - tabAt > 0.18 && hud.scene == Hud.Scene.WORLD && !hud.captures() && g.player.alive) hud.radial.openInPlay();
            if (tabAt >= 0 && !preferences.down(in, Preferences.Action.QUICK_WHEEL)) {
                if (hud.radial.open) hud.radial.release(); else if (!typing) cursorFree = !cursorFree;
                tabAt = -1;
            }
            // (0.3.9, gamefixes: the companion command menu) the Companion key: a tap gives the selected companion the
            // order the crosshair suggests (Shift: the whole squad); held, the squad menu opens and the world slows. A key
            // pressed with it held (Alt+S, Alt+Enter) makes it a chord instead: no order
            boolean companionDown = !typing && preferences.down(in, Preferences.Action.COMPANION);
            if (!typing && preferences.pressed(in, Preferences.Action.COMPANION)) { companionAt = hud.now; companionChord = false; }
            if (companionAt >= 0 && companionDown && (turnBack || preferences.pressed(in, Preferences.Action.FULLSCREEN))) companionChord = true;
            if (companionAt >= 0 && companionDown && !companionChord && hud.now - companionAt > 0.2 && hud.scene == Hud.Scene.WORLD && g.player.alive && !hud.squadOpen() && !hud.captures()) hud.squadOpen(true);
            if (companionAt >= 0 && !companionDown) {
                if (hud.squadOpen()) hud.squadOpen(false);
                else if (!companionChord && hud.scene == Hud.Scene.WORLD && !hud.captures() && g.player.alive) hud.squadQuickOrder(hud.shift);
                companionAt = -1;
            }
            if (hud.squadOpen()) {
                for (int k = 0; k < 4; k++) if (preferences.pressed(in, Preferences.Action.values()[Preferences.Action.HOTBAR_1.ordinal() + k])) hud.squadSelect(k);
                if (preferences.pressed(in, Preferences.Action.JUMP)) hud.squadWheel();
                if (preferences.pressed(in, Preferences.Action.DODGE)) hud.squadCycle(-1);
                if (preferences.pressed(in, Preferences.Action.INTERACT)) hud.squadCycle(1);
                it.forward = 0; it.strafe = 0; it.lookYaw = 0; it.lookPitch = 0; it.sprint = false; it.jump = false; it.jumpHeld = false;
                it.dodge = false; it.mainDown = false; it.offDown = false; it.cast = false; it.interact = false; it.turnBack = false;
            }
            if (hud.radial.open) {
                // the wheel holds the body still: no steps, no look, no hands
                hud.radial.pointer((float) in.dx, (float) in.dy);
                it.forward = 0; it.strafe = 0; it.lookYaw = 0; it.lookPitch = 0; it.sprint = false; it.jump = false; it.jumpHeld = false;
                it.dodge = false; it.mainDown = false; it.offDown = false; it.cast = false; it.interact = false; it.turnBack = false;
            }
            if (!typing && !hud.captures() && preferences.pressed(in, Preferences.Action.SAVE) && new SaveSystem(g).save(g.savePath())) g.chat.add("Game saved.", Chat.Kind.DEBUG);
            if (!typing && !hud.captures() && preferences.pressed(in, Preferences.Action.LOAD) && new SaveSystem(g).load(g.savePath())) g.chat.add("Game loaded.", Chat.Kind.DEBUG);
            if (!typing && preferences.pressed(in, Preferences.Action.SCREENSHOT)) shot(gfx, win.width, win.height, new File("shots", "shot-" + System.currentTimeMillis() + ".png"));
            // Alt+Enter: the whole monitor, borderless, at its refresh rate (or back to the window)
            if (!typing && preferences.pressed(in, Preferences.Action.FULLSCREEN)) { win.setFullscreen(!win.fullscreen); hud.settings.values.fullscreen = win.fullscreen; }
            if (g.death.choosing() && (preferences.pressed(in, Preferences.Action.CONFIRM) || in.keyPressed(GLFW_KEY_KP_ENTER)) && !hud.console.isOpen()) g.death.proceed(false);
            if (!rebinding && preferences.pressed(in, Preferences.Action.MENU) && !hud.escape()) hud.dialog(menu(win, g));
            if (hud.screenshotRequested) { hud.screenshotRequested = false; shotNext = true; }
            // any menu pauses the whole game: no module runs, no simulated time passes, the world's sounds hold where
            // they are; only the interface, its sounds and the music go on
            boolean paused = hud.paused();
            if (paused != wasPaused) {
                if (paused) g.controller.cancelHandInput();
                game.commands().audioPause(paused ? 1 << astratto.bridge.AstrattoNative.BUS_SFX | 1 << astratto.bridge.AstrattoNative.BUS_VOICE | 1 << astratto.bridge.AstrattoNative.BUS_AMBIENCE : 0);
                music.paused(paused);
                wasPaused = paused;
            }
            music.regional = g.soundscape::tracks;
            music.update(g.mood(), dt);
            music.updateStingers(dt);
            // host_timescale: the world's clock runs faster or slower; the interface keeps the wall clock
            float worldDt = Math.min(0.1f, dt * Math.max(0.05f, Math.min(10f, hud.console.f("timescale"))) * g.death.timeScale() * hud.worldPace());
            // (0.2.2, gamefixes: a crash punching a bandit, not reproducible here) a fault inside one frame is written to
            // saves/crash-*.txt with its stack and the frame goes on; only a fault that repeats every frame ends the game
            try {
                if (paused) { it.clearEdges(); if (!game.pump()) break; }
                else if (!game.tick(worldDt)) break;
                drawGame(g, gfx, ui, hud, win.width, win.height, dt, free, paused);
                faultsInARow = 0;
            } catch (RuntimeException fault) {
                crashLog(fault, g);
                if (++faultsInARow > 30) throw fault;
                if (faultsInARow == 1) g.chat.add("Something went wrong (logged to saves/crash-*.txt).", Chat.Kind.WARN);
            }
            if (shotNext) { shotNext = false; shot(gfx, win.width, win.height, new File("shots", "shot-" + System.currentTimeMillis() + ".png")); }
            win.swap();
            // fps_max: the console's frame cap (0: none but the monitor's)
            float cap = hud.console.f("max_fps");
            if (cap >= 10) { long until = frameStart + (long) (1e9 / cap); while (System.nanoTime() < until) Thread.onSpinWait(); }
            in.endFrame();
        }
    }

    private static int faultsInARow;
    private static float sheatheHeld;
    /** (0.3.9) When the Companion key went down (interface time; -1 up), and whether another key made it a chord. */
    private static double companionAt = -1;
    private static boolean companionChord;
    private static boolean sheatheFired;
    private static String lastFault = "";

    /** Writes a fault, with where the player stood and what they were doing, to saves/crash-<time>.txt (once per kind). */
    static void crashLog(Throwable fault, Rpg g) {
        java.io.StringWriter w = new java.io.StringWriter();
        fault.printStackTrace(new java.io.PrintWriter(w));
        String trace = w.toString();
        System.err.println(trace);
        String key = trace.lines().limit(3).reduce("", String::concat);
        if (key.equals(lastFault)) return;
        lastFault = key;
        try {
            File dir = new File("saves"); dir.mkdirs();
            StringBuilder b = new StringBuilder(VERSION + "  " + java.time.LocalDateTime.now() + "%n".formatted());
            if (g != null && g.player != null) b.append(String.format("player at %.1f, %.1f, %.1f  hp %.1f  phase %s  jurisdiction %s%n", g.player.x, g.player.y, g.player.z, g.player.hp, g.player.cs.phase, rpg.world.DevMap.jurisdiction(g.player.x, g.player.z)));
            b.append(trace);
            java.nio.file.Files.writeString(new File(dir, "crash-" + System.currentTimeMillis() + ".txt").toPath(), b.toString());
        } catch (Exception ignored) { }
    }

    static Dialog menu(Window win, Rpg g) {
        Dialog d = new Dialog("Game paused", g.player.name)
                .choice("Resume", () -> { })
                .choice("Settings", () -> g.ui.settings.show())
                .choice("Save game", () -> { if (new SaveSystem(g).save(g.savePath())) g.chat.add("Game saved.", Chat.Kind.DEBUG); })
                .choice("Load last save", () -> { if (new SaveSystem(g).load(g.savePath())) g.chat.add("Game loaded.", Chat.Kind.DEBUG); })
                .choice("Quit to title", () -> { if (g.sfx != null) g.sfx.ui("LogoutProfileButton", 0.6f); g.ui.toTitle(); })
                .choice("Quit game", () -> { if (g.sfx != null) g.sfx.ui("LogoutProfileButton", 0.6f); win.requestClose(); });
        d.pauseMenu = true;
        return d;
    }

    private static boolean shotNext;
    /**
     * (0.3.7, gamefixes: the program's icon) the window's icon - the title bar's and the taskbar's - from the game's own
     * picture of it (resources ui/program_icon.png), at the sizes the desktop asks for. (0.3.8, gamefixes: it showed
     * only faintly in the title bar and not at all on the taskbar) It is set before the window is first shown, the
     * program has its own id on the taskbar (Window.appId) rather than sharing the java runtime's, every size a scaled
     * desktop picks from is given, and each is halved down step by step from the full picture so a small one stays crisp.
     */
    private static void programIcon(long handle) {
        try (var in = Main.class.getResourceAsStream("/ui/program_icon.png")) {
            if (in == null || handle == 0) return;
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(in);
            int[] sizes = {16, 20, 24, 32, 40, 48, 64, 96, 128, 256};
            try (var images = org.lwjgl.glfw.GLFWImage.malloc(sizes.length)) {
                java.util.List<java.nio.ByteBuffer> held = new java.util.ArrayList<>();
                for (int i = 0; i < sizes.length; i++) {
                    int s = sizes[i];
                    var scaled = halved(src, s);
                    java.nio.ByteBuffer px = org.lwjgl.system.MemoryUtil.memAlloc(s * s * 4);
                    for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) { int c = scaled.getRGB(x, y); px.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24)); }
                    px.flip(); held.add(px);
                    images.position(i).width(s).height(s).pixels(px);
                }
                images.position(0);
                org.lwjgl.glfw.GLFW.glfwSetWindowIcon(handle, images);
                for (var px : held) org.lwjgl.system.MemoryUtil.memFree(px);
            }
        } catch (java.io.IOException | RuntimeException e) { System.err.println("[icon] " + e.getMessage()); }
    }

    /** A picture brought down to s x s by halving it (each step bilinear), then one last step: no aliased small icon. */
    private static java.awt.image.BufferedImage halved(java.awt.image.BufferedImage src, int s) {
        java.awt.image.BufferedImage at = src;
        int w = src.getWidth();
        do {
            int next = Math.max(s, w / 2);
            var step = new java.awt.image.BufferedImage(next, next, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g2 = step.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(at, 0, 0, next, next, null); g2.dispose();
            at = step; w = next;
        } while (w > s);
        return at;
    }

    /** The game's version (the patch notes' number). */
    public static final String VERSION = "Alpha 0.4.6";
    /**
     * (0.4.1, gamefixes: the stutter on loading into the map) everything the first seconds in the world would otherwise do
     * a piece at a time, done before the first frame: the growth about the spawn, the routing graph about it, and the
     * chunks in view meshed and lit, so the player arrives into a world that is already all there.
     */
    public static void preloadAll(Rpg g, rpg.render.Gfx gfx) {
        long t0 = System.nanoTime();
        gfx.preloadWorld(g, g.player.x, g.player.z);
        gfx.prewarmFurniture(g);
        if (rpg.world.WorldMap.shadowveil()) {
            g.vox.navigation.warmAround(g.player.x, g.player.z, Tuning.i("load.navRadius", 96));
            // (0.4.2, gamefixes: the stutter after loading in) and about every creature awake beyond it - a goblin's den,
            // a spider's nest - whose first route would otherwise build its ground's navigation in the first seconds of play
            int r = Tuning.i("load.navRadiusCreatures", 24);
            if (g.monsters != null) for (var m : g.monsters.all()) if (m.loaded) g.vox.navigation.warmAround(m.x, m.z, r);
            for (var n : g.npcs) if (n.loaded) g.vox.navigation.warmAround(n.x, n.z, r);
        }
        // one step of the world behind the loading screen: the first tick's own costs (the engine's first frame, every
        // creature's first plan, the residents beyond the fog going off-stage) are paid before anything is shown
        g.game.tick(1f / 60);
        gfx.preloadChunks(g, g.player.x, g.player.z);
        System.out.printf("[Load] the world about the spawn made ready in %.2fs%n", (System.nanoTime() - t0) / 1e9);
    }

    /**
     * (0.4.2, gamefixes: the huge stutter on loading in) two whole game frames drawn behind the loading screen (never
     * shown: the buffers are not swapped) - the interface's first draw builds its map, its fonts' pages and its icons, a
     * second's work that was the first frame of play; it is paid here, before the player sees anything move.
     */
    public static void warmFrames(Rpg g, Gfx gfx, Ui2D ui, Hud hud, int w, int h) {
        long t0 = System.nanoTime();
        // (a second each: the interface's pieces fade in over their own time, and a piece not yet faded in is not drawn)
        for (int i = 0; i < 2; i++) drawGame(g, gfx, ui, hud, Math.max(1, w), Math.max(1, h), 1f, false);
        // (0.4.3, gamefixes: the stutter on loading in) the world's own first moments run behind the loading screen too -
        // a few dozen ticks and frames, so every system's first-time work (the routes first asked, the code first run) is
        // done before the player is shown the world, not in their first second of play
        int warm = Tuning.i("load.warmTicks", 45);
        for (int i = 0; i < warm; i++) { g.game.tick(1 / 60f); if (i % 3 == 2) drawGame(g, gfx, ui, hud, Math.max(1, w), Math.max(1, h), 1 / 20f, false); }
        System.out.printf("[Load] the first frames drawn behind the loading screen in %.2fs%n", (System.nanoTime() - t0) / 1e9);
    }

    /** A GLFW key from the name a bind uses ("f5", "p", "kp_1", "space", "mouse4"...), or -1. */
    static int keyCode(String name) {
        String n = name.toUpperCase(java.util.Locale.ROOT);
        if (n.length() == 1) { char c = n.charAt(0); if (c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') return c; }
        try { return org.lwjgl.glfw.GLFW.class.getField("GLFW_KEY_" + n).getInt(null); }
        catch (ReflectiveOperationException e) { return -1; }
    }

    static float frameDt(long last) { return Math.min(0.05f, Math.max(1e-4f, (System.nanoTime() - last) / 1e9f)); }

    static void mouse(Ui2D ui, Input in) {
        ui.consumed = false;
        ui.mx = (float) in.mouseX; ui.my = (float) in.mouseY;
        ui.click = in.mousePressed(GLFW_MOUSE_BUTTON_LEFT);
        ui.rclick = in.mousePressed(GLFW_MOUSE_BUTTON_RIGHT);
        ui.down = in.mouseDown(GLFW_MOUSE_BUTTON_LEFT);
        ui.released = in.mouseReleased(GLFW_MOUSE_BUTTON_LEFT);
        ui.scroll = in.scroll;
    }

    static void drawGame(Rpg g, Gfx gfx, Ui2D ui, Hud hud, int w, int h, float dt, boolean cursorFree) { drawGame(g, gfx, ui, hud, w, h, dt, cursorFree, false); }

    /** One drawn frame. Paused, the world is drawn exactly as it stands (no animation advances); the interface still moves. */
    static void drawGame(Rpg g, Gfx gfx, Ui2D ui, Hud hud, int w, int h, float dt, boolean cursorFree, boolean paused) {
        var c = g.controller;
        float shake = paused || hud.settings.values.reducedMotion ? 0 : g.fx.shake;
        float sx = shake > 0 ? (float) Math.sin(g.time * 60) * shake * 0.05f : 0, sy = shake > 0 ? (float) Math.cos(g.time * 47) * shake * 0.05f : 0;
        float worldDt = paused ? 0 : dt * hud.worldPace();
        gfx.camera(c.cx + sx, c.cy + sy, c.cz, c.fx, c.fy, c.fz, w, h, worldDt);
        long t0 = System.nanoTime();
        gfx.scene(g, g.player.firstPerson, worldDt, g.state.hourOfDay());
        if (g.death.active) gfx.cmd.grading(g.death.grade(), g.death.flash(), 0, 0, 0);
        else if (g.time - g.player.cs.lastHurt < 0.4) gfx.cmd.grading(0, (float) (0.4 - (g.time - g.player.cs.lastHurt)) * 2, 0, 0, 0);
        long t1 = System.nanoTime();
        ui.begin(gfx.cmd);
        hud.draw(ui, w, h, cursorFree, dt);
        ui.end();
        long t2 = System.nanoTime();
        if (gfx.frame(w, h) == null) System.err.println("frame refused: " + gfx.renderer().lastError());
        // (0.4.2, gamefixes: the stutter on loading in) the first frames of play, phase by phase, in the log
        lastScene = (t1 - t0) / 1e6f; lastUi = (t2 - t1) / 1e6f; lastRender = (System.nanoTime() - t2) / 1e6f;
        if (framesLogged < 3) { framesLogged++; System.out.printf("[Load] game frame %d: scene %.0f ms, interface %.0f ms, render %.0f ms%n", framesLogged, (t1 - t0) / 1e6, (t2 - t1) / 1e6, (System.nanoTime() - t2) / 1e6); }
    }
    private static int framesLogged;
    /** (0.4.4) The last drawn frame's phases (ms): the scene built, the interface, the renderer's frame - for the stutter's report. */
    public static float lastScene, lastUi, lastRender;

    static void shot(Gfx gfx, int w, int h, File out) {
        ByteBuffer px = gfx.pixels(w, h);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = ((h - 1 - y) * w + x) * 4;
            img.setRGB(x, y, (px.get(i) & 255) << 16 | (px.get(i + 1) & 255) << 8 | (px.get(i + 2) & 255));
        }
        MemoryUtil.memFree(px);
        try {
            if (out.getAbsoluteFile().getParentFile() != null) out.getAbsoluteFile().getParentFile().mkdirs();
            javax.imageio.ImageIO.write(img, "png", out);
            System.out.println("[shot] " + out.getAbsolutePath());
        } catch (java.io.IOException e) { System.err.println("could not save " + out + ": " + e); }
    }

    static Appearance defaultLook() { return Appearance.henric(); }
}
