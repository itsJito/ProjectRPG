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
 * RPGGAME's devmap prototype on the Astratto Engine.
 * <pre>
 *   ./gradlew run          the character creator, then the devmap
 *   ./gradlew acceptance   off-screen: every GDD 4.2 end-to-end flow, scripted and checked, with screenshots
 * </pre>
 * Controls: WASD walk, Shift run (tap twice and hold: sprint), Space jump, Q dodge, mouse look; LMB main hand (tap light, hold heavy, draw a
 * bow), RMB off hand (guard, or the off-hand item); R cast; 1-3 blessings; E interact (hold for every option); V camera;
 * Tab frees the mouse for the interface; F1-F4/F7 panels; F5/F6 save/load; F8-F11 developer inspectors; Esc menus.
 */
public final class Main {
    public static void main(String[] args) {
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
        System.out.println(Astratto.NAME + " " + Astratto.VERSION + " - RPGGAME devmap" + (Astratto.javaFallbackAllowed() ? "" : " (Java fallbacks disabled)"));
        Window win = new Window();
        win.show = !hidden;
        if (bench) win.create("RPGGAME - bench", 1920, 1080); else win.create("RPGGAME", 1600, 900);
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
        long world = DevMap.build(game.engine());
        game.activate(world);
        System.out.printf("[Load] devmap %.2fs%n", (System.nanoTime() - boot) / 1e9);
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
        preview.world = dungeon; preview.dungeon = true; preview.x = 8.5f; preview.y = DevMap.FLOOR; preview.z = 8.5f;
        long last = System.nanoTime();
        win.setCursorLocked(false);
        rpg.audio.Music music = new rpg.audio.Music(game);
        gfx.findLamps(new rpg.world.Voxels(game.view()));
        // ---- the title screen: the profiles
        TitleScreen title = new TitleScreen(VERSION);
        title.sound = n -> rpg.audio.Sfx.shared(game).ui(n, 0.5f);
        Gfx.Preview stage = new Gfx.Preview();
        stage.world = dungeon; stage.dungeon = true; stage.x = 8.5f; stage.y = DevMap.FLOOR; stage.z = 8.5f;
        rpg.ui.SettingsPanel menuSettings = new rpg.ui.SettingsPanel(Preferences.load(java.nio.file.Path.of("saves", "preferences.json")), p -> {
            gfx.menuPreferences = p; p.mix(game.commands()); win.setFullscreen(p.fullscreen); win.setVsync(p.vsync);
        }, true);
        title.settings = menuSettings::show;
        if (!brandShown) { brandShown = true; splash(win, game, gfx, ui, stage); }

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
        // Dynamic resolution targets the configured 30 FPS budget, independently of presentation refresh rate.
        g.music = music;
        Hud hud = new Hud(g, gfx);
        g.ui = hud;
        gfx.findLamps(g.vox);
        gfx.preload(g.npcs); gfx.preload(java.util.List.of(g.player));
        if (!fresh && profile.hasSave() && !new SaveSystem(g).load(profile.save())) g.chat.warn("The save for " + profile.name + " could not be loaded: a fresh start.");
        if (fresh) new SaveSystem(g).save(profile.save());
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
        creator.sound = n -> rpg.audio.Sfx.shared(game).ui(n, 0.5f);
        long last = System.nanoTime();
        while (!win.shouldClose() && !creator.done && !creator.cancelled) {
            win.poll();
            float dt = frameDt(last); last = System.nanoTime();
            mouse(ui, in);
            music.update(rpg.audio.Music.Mood.MENU, dt);
            music.updateStingers(dt);
            game.tick(dt);
            creator.spin(dt);
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
        boolean eMenuOpened = false;
        // a mouse button is the body's only when it went down while the mouse was looking (not on a menu): the click
        // that picks a dialogue choice or closes a window is never also a swing when the menu closes under it
        boolean mainArmed = false, offArmed = false, wasPaused = false, crouchToggle = false, sprintToggle = false;
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
            if (in.keyPressed(GLFW_KEY_GRAVE_ACCENT)) hud.console.toggle();
            else if (hud.console.isOpen()) hud.console.input(typed.toString(), in.keyPressed(GLFW_KEY_ENTER) || in.keyPressed(GLFW_KEY_KP_ENTER), in.keyPressed(GLFW_KEY_BACKSPACE),
                    in.keyPressed(GLFW_KEY_UP), in.keyPressed(GLFW_KEY_DOWN), in.keyPressed(GLFW_KEY_TAB), in.keyPressed(GLFW_KEY_PAGE_UP) ? 1 : in.keyPressed(GLFW_KEY_PAGE_DOWN) ? -1 : 0);
            if (hud.console.isOpen()) typed.setLength(0);
            hud.input(typed.toString(), !hud.console.isOpen() && in.keyPressed(GLFW_KEY_BACKSPACE), !hud.console.isOpen() && in.keyPressed(GLFW_KEY_R));
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
            if (!typing && preferences.pressed(in, Preferences.Action.CROUCH)) crouchToggle = !crouchToggle;
            if (!typing && preferences.pressed(in, Preferences.Action.SPRINT)) sprintToggle = !sprintToggle;
            it.sneak = !typing && (preferences.toggleCrouch ? crouchToggle : preferences.down(in, Preferences.Action.CROUCH));
            if (preferences.toggleSprint) it.sprint = !typing && sprintToggle;
            it.jogToggle = !typing && in.keyPressed(GLFW_KEY_CAPS_LOCK);
            it.jump = !typing && preferences.pressed(in, Preferences.Action.JUMP);
            it.jumpHeld = !typing && preferences.down(in, Preferences.Action.JUMP);
            it.dodge = !typing && preferences.pressed(in, Preferences.Action.DODGE);
            it.backTap = !typing && preferences.pressed(in, Preferences.Action.BACK);
            it.cast = !free && in.keyPressed(GLFW_KEY_R);
            it.toggleCamera = preferences.pressed(in, Preferences.Action.CAMERA);
            it.mainDown = !free && mainArmed;
            it.offDown = !free && offArmed;
            float sens = 0.0025f * Math.max(0.05f, hud.console.f("sensitivity"));
            if (!free) { it.lookYaw = -(float) in.dx * sens; it.lookPitch = (preferences.invertY ? 1 : -1) * (float) in.dy * sens; }
            if (!free && in.scroll != 0) g.player.camDist = Math.max(1.6f, Math.min(8, g.player.camDist - (float) in.scroll * 0.4f));
            for (int k = 0; k < 10 && !typing; k++) if (in.keyPressed(k == 9 ? GLFW_KEY_0 : GLFW_KEY_1 + k)) {
                if (hud.dialog != null || hud.menuOpen()) hud.choose(k + (hud.dialog != null && !hud.dialog.pauseMenu ? hud.dialog.choiceOffset : 0));
                else hud.hotkey(k);
            }
            if (hud.dialog != null && hud.dialog.pauseMenu) {
                Dialog d = hud.dialog;
                if (in.keyPressed(GLFW_KEY_UP)) d.focusedChoice = (d.focusedChoice + d.choices.size() - 1) % d.choices.size();
                if (in.keyPressed(GLFW_KEY_DOWN)) d.focusedChoice = (d.focusedChoice + 1) % d.choices.size();
                if (in.keyPressed(GLFW_KEY_ENTER) || in.keyPressed(GLFW_KEY_KP_ENTER)) hud.choose(d.focusedChoice);
            }
            // the scenes: inventory, character, skills, journal, magic
            if (!typing && !(hud.dialog != null && hud.dialog.pauseMenu)) {
                if (preferences.pressed(in, Preferences.Action.INVENTORY)) hud.toggle(Hud.Scene.INVENTORY);
                if (in.keyPressed(GLFW_KEY_C)) hud.toggle(Hud.Scene.CHARACTER);
                if (in.keyPressed(GLFW_KEY_K)) hud.toggle(Hud.Scene.SKILLS);
                if (in.keyPressed(GLFW_KEY_J)) hud.toggle(Hud.Scene.JOURNAL);
                if (preferences.pressed(in, Preferences.Action.MAP)) hud.toggle(Hud.Scene.MAP);
                if (in.keyPressed(GLFW_KEY_B)) hud.toggle(Hud.Scene.MAGIC);
            }
            // E: a tap is the primary action, a hold opens every option
            if (!typing && preferences.pressed(in, Preferences.Action.INTERACT)) { eHeld = g.time; eMenuOpened = false; }
            if (eHeld >= 0 && preferences.down(in, Preferences.Action.INTERACT) && !eMenuOpened && g.time - eHeld > 0.3) {
                hud.openInteractMenu();
                eMenuOpened = true;
            }
            if (preferences.released(in, Preferences.Action.INTERACT)) { if (!eMenuOpened && !hud.captures()) it.interact = true; eHeld = -1; }
            for (int k = 0; k < 7; k++) if (k != 4 && k != 5 && in.keyPressed(GLFW_KEY_F1 + k)) hud.openTabKey(k);
            if (in.keyPressed(GLFW_KEY_F8)) hud.xpDashboard = !hud.xpDashboard;
            if (in.keyPressed(GLFW_KEY_F9)) hud.aiInspector = !hud.aiInspector;
            if (in.keyPressed(GLFW_KEY_F10)) hud.stateInspector = !hud.stateInspector;
            if (in.keyPressed(GLFW_KEY_F11)) hud.telemetry = !hud.telemetry;
            if (in.keyPressed(GLFW_KEY_PAGE_UP)) hud.nextInspected(-1);
            if (in.keyPressed(GLFW_KEY_PAGE_DOWN)) hud.nextInspected(1);
            if (!typing && in.keyPressed(GLFW_KEY_TAB)) cursorFree = !cursorFree;
            if (!typing && !hud.captures() && in.keyPressed(GLFW_KEY_F5) && new SaveSystem(g).save(g.savePath())) g.chat.add("Game saved.", Chat.Kind.DEBUG);
            if (!typing && !hud.captures() && in.keyPressed(GLFW_KEY_F6) && new SaveSystem(g).load(g.savePath())) g.chat.add("Game loaded.", Chat.Kind.DEBUG);
            if (in.keyPressed(GLFW_KEY_F12)) shot(gfx, win.width, win.height, new File("shots", "shot-" + System.currentTimeMillis() + ".png"));
            // Alt+Enter: the whole monitor, borderless, at its refresh rate (or back to the window)
            if (in.keyPressed(GLFW_KEY_ENTER) && (in.keyDown(GLFW_KEY_LEFT_ALT) || in.keyDown(GLFW_KEY_RIGHT_ALT))) { win.setFullscreen(!win.fullscreen); hud.settings.values.fullscreen = win.fullscreen; }
            if (g.death.choosing() && (in.keyPressed(GLFW_KEY_ENTER) || in.keyPressed(GLFW_KEY_KP_ENTER)) && !hud.console.isOpen()) g.death.proceed(false);
            if (!rebinding && in.keyPressed(GLFW_KEY_ESCAPE) && !hud.escape()) hud.dialog(menu(win, g));
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
            music.update(g.mood(), dt);
            music.updateStingers(dt);
            // host_timescale: the world's clock runs faster or slower; the interface keeps the wall clock
            float worldDt = Math.min(0.1f, dt * Math.max(0.05f, Math.min(10f, hud.console.f("timescale"))) * g.death.timeScale());
            if (paused) { it.clearEdges(); if (!game.pump()) break; }
            else if (!game.tick(worldDt)) break;
            drawGame(g, gfx, ui, hud, win.width, win.height, dt, free, paused);
            if (shotNext) { shotNext = false; shot(gfx, win.width, win.height, new File("shots", "shot-" + System.currentTimeMillis() + ".png")); }
            win.swap();
            // fps_max: the console's frame cap (0: none but the monitor's)
            float cap = hud.console.f("max_fps");
            if (cap >= 10) { long until = frameStart + (long) (1e9 / cap); while (System.nanoTime() < until) Thread.onSpinWait(); }
            in.endFrame();
        }
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
    /** The game's version (the patch notes' number). */
    public static final String VERSION = "0.1.2";

    /** A GLFW key from the name a bind uses ("f5", "p", "kp_1", "space", "mouse4"...), or -1. */
    static int keyCode(String name) {
        String n = name.toUpperCase(java.util.Locale.ROOT);
        if (n.length() == 1) { char c = n.charAt(0); if (c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') return c; }
        try { return org.lwjgl.glfw.GLFW.class.getField("GLFW_KEY_" + n).getInt(null); }
        catch (ReflectiveOperationException e) { return -1; }
    }

    static float frameDt(long last) { return Math.min(0.05f, Math.max(1e-4f, (System.nanoTime() - last) / 1e9f)); }

    static void mouse(Ui2D ui, Input in) {
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
        float worldDt = paused ? 0 : dt;
        gfx.camera(c.cx + sx, c.cy + sy, c.cz, c.fx, c.fy, c.fz, w, h, worldDt);
        gfx.scene(g, g.player.firstPerson, worldDt, g.state.hourOfDay());
        if (g.death.active) gfx.cmd.grading(g.death.grade(), g.death.flash(), 0, 0, 0);
        else if (g.time - g.player.cs.lastHurt < 0.4) gfx.cmd.grading(0, (float) (0.4 - (g.time - g.player.cs.lastHurt)) * 2, 0, 0, 0);
        ui.begin(gfx.cmd);
        hud.draw(ui, w, h, cursorFree, dt);
        ui.end();
        if (gfx.frame(w, h) == null) System.err.println("frame refused");
    }

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
