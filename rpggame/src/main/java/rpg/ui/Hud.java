package rpg.ui;

import astratto.bridge.NativeRenderer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.SaveSystem;
import rpg.econ.Economy;
import rpg.interact.Interactable;
import rpg.interact.Option;
import rpg.item.Currency;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.objects.Container;
import rpg.objects.Fixtures;
import rpg.objects.Station;
import rpg.render.Gfx;
import rpg.skill.Skill;
import rpg.ui.Fragment.Anchor;
import rpg.ui.Fragment.Placement;

/**
 * The interface (gamefixes, UI). Nothing here is a fixed screen: the HUD is a set of {@link Fragment}s that fade in only
 * while their game state is live, and a scene state machine (the world; the inventory, character, skills, journal,
 * magic scenes) decides which fragment groups are on screen. Groups are reusable (the equipment doll is in
 * the inventory and the character scenes; the vitals and the action bar ride along into every scene) and addons can
 * add fragments, move them, hide them and add groups to scenes ({@link Addons}).
 *
 * <p>The look is flat and low-contrast ({@link Style}); the minimap is anchored top right with the NPC colours the game
 * has always used, the action bar bottom centre.
 */
public final class Hud {
    public enum Scene { WORLD, INVENTORY, CHARACTER, SKILLS, JOURNAL, MAGIC, MAP }
    public enum Win { NONE, CONTAINER, STATION, CABINET, SHOP, KIOSK, SKILL_GUIDE }
    /** The old side-panel tab numbers (F1..F7 and the tests use them): each now opens a scene. The debug tab is gone: the developer console (`) took its place. */
    public static final int TAB_COMBAT = 0, TAB_SKILLS = 1, TAB_QUESTS = 2, TAB_INV = 3, TAB_EQUIP = 4, TAB_MAGIC = 5, TAB_MAP = 6;
    static final Scene[] TAB_SCENES = {Scene.CHARACTER, Scene.SKILLS, Scene.JOURNAL, Scene.INVENTORY, Scene.CHARACTER, Scene.MAGIC, Scene.MAP};
    /** The scene frame's size (panel scenes draw inside it). */
    static final float FW = 900, FH = 520, FRAME_Y = -24;

    final Rpg g;
    final Gfx gfx;
    final NativeRenderer r;
    public Scene scene = Scene.WORLD;
    public Win win = Win.NONE;
    Container container;
    Station station;
    Economy.Shop shop;
    Skill guide = Skill.ATTACK;
    public Dialog dialog;
    Menu menu;
    public boolean aiInspector, stateInspector, telemetry, xpDashboard;
    int inspectIndex;
    public float[] flag;
    public int w, h;
    double now, last;
    float dt;
    /** Typed text, backspace and the rotate key this frame (from the window). */
    String typed = "";
    boolean backspace, rotate;

    public final UiEvents events;
    final Map<String, Fragment> fragments = new LinkedHashMap<>();
    final Map<String, List<Slot>> groups = new LinkedHashMap<>();
    final Map<Scene, List<String>> scenes = new EnumMap<>(Scene.class);
    final InventoryPanel inventory;
    final ActionBar bar;
    final ItemIcons icons;
    final Addons addons;
    private String tip;
    Ui2D ui;
    /** Where things were last drawn (grid origins, equipment slots, bar slots): for tests and addons. */
    final Map<Object, float[]> spots = new java.util.IdentityHashMap<>();
    final Map<String, float[]> named = new java.util.HashMap<>();

    record Slot(String fragment, Placement at) {}
    record MenuItem(String label, boolean enabled, String reason, Runnable action) {}
    static final class Menu { float x, y; String title; final List<MenuItem> items = new ArrayList<>(); }

    public Hud(Rpg g, Gfx gfx) {
        this.g = g; this.gfx = gfx; this.r = gfx.renderer();
        events = new UiEvents(g);
        icons = new ItemIcons(r);
        inventory = new InventoryPanel(this);
        bar = new ActionBar(this);
        HudFragments.register(this);
        Panels.register(this);
        Windows.register(this);
        layout();
        events.listen((type, detail) -> { for (Fragment f : fragments.values()) if (f.wakeOn.contains(type)) f.woken(now); });
        events.listen((type, detail) -> sounds(type, detail));
        addons = new Addons(this);
        addons.loadAll(java.nio.file.Path.of("addons"));
        console = new Console(g, this);
        console.autoexec();
    }

    // ------------------------------------------------------------------ registry and layout
    <T extends Fragment> T add(T f) { f.bind(this); fragments.put(f.id, f); return f; }
    public Fragment fragment(String id) { return fragments.get(id); }

    void group(String id, Object... fragmentsAndPlacements) {
        List<Slot> l = groups.computeIfAbsent(id, k -> new ArrayList<>());
        for (int i = 0; i < fragmentsAndPlacements.length; i += 2) l.add(new Slot((String) fragmentsAndPlacements[i], (Placement) fragmentsAndPlacements[i + 1]));
    }

    /** A placement inside the scene frame: (fx, fy) from the frame's top-left corner. */
    Placement inFrame(String fragment, float fx, float fy) {
        Fragment f = fragments.get(fragment);
        return Placement.at(Anchor.CENTER, -FW / 2 + fx + f.width() / 2, FRAME_Y - FH / 2 + fy + f.height() / 2);
    }

    private void layout() {
        group("world", "overlays", Placement.at(Anchor.SCREEN, 0, 0), "prompt", Placement.at(Anchor.SCREEN, 0, 0), "death", Placement.at(Anchor.SCREEN, 0, 0));
        group("vitals", "vitals", Placement.at(Anchor.TOP_LEFT, 16, 18), "sentence", Placement.at(Anchor.TOP, 0, 18), "sneak", Placement.at(Anchor.BOTTOM, 0, 90), "actionbar", Placement.at(Anchor.BOTTOM, 0, 10), "xpdrops", Placement.at(Anchor.TOP_RIGHT, 190, 18));
        group("hud", "minimap", Placement.at(Anchor.TOP_RIGHT, 16, 16), "area", Placement.at(Anchor.TOP_RIGHT, 16, 186),
                "wanted", Placement.at(Anchor.TOP_LEFT, 16, 16), "target", Placement.at(Anchor.TOP, 0, 16), "honor", Placement.at(Anchor.TOP, 0, 66), "levelup", Placement.at(Anchor.TOP, 0, 110),
                "chat", Placement.at(Anchor.BOTTOM_LEFT, 12, 12), "work", Placement.at(Anchor.CENTER, 0, 92));
        group("frame", "frame", Placement.at(Anchor.CENTER, 0, FRAME_Y));
        group("inventory", "equipment", inFrame("equipment", 16, 52), "grids", inFrame("grids", 232, 52), "iteminfo", inFrame("iteminfo", 620, 52),
                "container", inFrame("container", 620, 52), "shop", inFrame("shop", 620, 52));
        group("character", "equipment", inFrame("equipment", 16, 52), "stats", inFrame("stats", 232, 52));
        group("skills", "skills", inFrame("skills", 16, 52), "guide", inFrame("guide", 452, 52));
        group("journal", "journal", inFrame("journal", 16, 52), "legend", inFrame("legend", 552, 52));
        group("magic", "magic", inFrame("magic", 16, 52));
        group("map", "worldmap", inFrame("worldmap", 16, 52));
        group("window", "station", Placement.at(Anchor.CENTER, -120, -40), "cabinet", Placement.at(Anchor.CENTER, -120, -40), "kiosk", Placement.at(Anchor.CENTER, -120, -40));
        group("talk", "dialogue", Placement.at(Anchor.BOTTOM_LEFT, 8, 12), "pause", Placement.at(Anchor.CENTER, 0, 0));
        group("inspect", "ai", Placement.at(Anchor.TOP_LEFT, 8, 60), "state", Placement.at(Anchor.LEFT, 8, 40), "telemetry", Placement.at(Anchor.BOTTOM_LEFT, 8, 180), "xpboard", Placement.at(Anchor.TOP_RIGHT, 260, 200));
        group("addons");
        group("top", "menu", Placement.at(Anchor.SCREEN, 0, 0));
        scenes.put(Scene.WORLD, new ArrayList<>(List.of("world", "hud", "vitals", "window", "talk", "inspect", "addons", "top")));
        for (Scene s : new Scene[]{Scene.INVENTORY, Scene.CHARACTER, Scene.SKILLS, Scene.JOURNAL, Scene.MAGIC, Scene.MAP}) {
            String content = s.name().toLowerCase();
            scenes.put(s, new ArrayList<>(List.of("world", "vitals", "frame", content, "talk", "inspect", "addons", "top")));
        }
    }

    // ------------------------------------------------------------------ the API the game calls
    public void openContainer(Container c) { container = c; win = Win.CONTAINER; menu = null; go(Scene.INVENTORY); }
    public void openStation(Station s) { station = s; win = Win.STATION; menu = null; go(Scene.WORLD); }
    public void openCabinet(Fixtures.Cabinet c) { win = Win.CABINET; menu = null; go(Scene.WORLD); }
    public void openShop(Economy.Shop s) { shop = s; win = Win.SHOP; menu = null; go(Scene.INVENTORY); }
    public void openKiosk() { win = Win.KIOSK; menu = null; go(Scene.WORLD); }
    public void openTab(int t) { go(TAB_SCENES[Math.max(0, Math.min(TAB_SCENES.length - 1, t))]); }
    public void dialog(Dialog d) {
        dialog = d; menu = null;
    }
    public void closeDialog() { dialog = null; }
    public void closeAll() { win = Win.NONE; dialog = null; menu = null; if (scene != Scene.WORLD) go(Scene.WORLD); }
    /** Whether the interface needs the mouse (a scene, a window, a dialogue or a menu is open). */
    public boolean captures() { return g.death.choosing() || console.isOpen() || scene != Scene.WORLD || win != Win.NONE || dialog != null || menu != null || inventory.dragging(); }
    /**
     * Whether the world stands still (gamefixes: pausing pauses the entire game): any menu - a panel scene (inventory,
     * character, skills, journal, magic), a window (a chest, a shop, a station), a dialogue or the game menu, the hold-E
     * menu. Only the interface and the music carry on.
     */
    public boolean paused() { return console.isOpen() || scene != Scene.WORLD || win != Win.NONE || dialog != null || menu != null; }
    /** Whether typed keys belong to the interface (the inventory's filter box has the focus). */
    public boolean typing() { return console.isOpen() || scene == Scene.INVENTORY && inventory.filterFocus; }

    /** The scene state machine: a scene swaps fragment groups in and out (each fades on its own). */
    public void go(Scene s) {
        if (s == scene) return;
        Scene from = scene;
        scene = s;
        menu = null;
        if (s != Scene.INVENTORY && (win == Win.CONTAINER || win == Win.SHOP)) win = Win.NONE;
        inventory.cancelDrag();
        if (s == Scene.SKILLS) sound("AchievementMenuOpen", 0.5f);
        else if (s == Scene.JOURNAL) sound("QuestMenuOpen", 0.5f);
        else if (s != Scene.WORLD) sound(from == Scene.WORLD ? "MenuScreenOpen" : "UIbutton1", 0.5f);
        else sound(from == Scene.SKILLS ? "AchievementMenuClose" : from == Scene.MAGIC ? "SpellBookCloseA" : "UI_windowclose", 0.5f);
        events.emit("scene", s.name());
    }
    /** A scene key: opens it, or closes it when it is already open. */
    public void toggle(Scene s) { go(scene == s ? Scene.WORLD : s); }
    public void openTabKey(int t) { if (t >= 0 && t < TAB_SCENES.length) toggle(TAB_SCENES[t]); }

    public boolean escape() {
        if (console.isOpen()) { console.close(); return true; }
        if (inventory.dragging()) { inventory.cancelDrag(); return true; }
        if (menu != null) { menu = null; return true; }
        if (dialog != null) { dialog = null; return true; }
        if (win != Win.NONE && scene == Scene.WORLD) { win = Win.NONE; sound("UI_windowclose", 0.5f); return true; }
        if (scene != Scene.WORLD) { go(Scene.WORLD); return true; }
        return false;
    }

    /** The hold-E menu: every option of the focused target, enabled or with its reason. */
    public void openInteractMenu() {
        Interactable t = g.interact.focus;
        if (t == null) return;
        Menu m = new Menu();
        m.title = t.label();
        m.x = w / 2f + 20; m.y = h / 2f - 10;
        for (Option o : t.options(g, g.player)) m.items.add(new MenuItem(o.label(), o.enabled(), o.reason(), () -> g.interact.request(g.player, t, o.id())));
        m.items.add(new MenuItem("Cancel", true, null, () -> { }));
        menu = m;
        sound("UIbutton2", 0.4f);
    }

    /** A numbered choice on whatever is open (dialogue options, menu items) from the keyboard. */
    public void choose(int n) {
        if (dialog != null && n < dialog.choices.size()) { Dialog d = dialog; dialog = null; sound("UI_select", 0.4f); d.choices.get(n).action().run(); return; }
        if (menu != null && n < menu.items.size()) { MenuItem it = menu.items.get(n); menu = null; if (it.enabled()) { sound("UI_select", 0.4f); it.action().run(); } else if (it.reason() != null) g.chat.warn(it.reason()); }
    }

    public boolean menuOpen() { return menu != null; }

    /** The number keys in the world: the action bar's slots. */
    public void hotkey(int k) { bar.use(k); }

    /** Input the window hands over each frame. */
    public void input(String typed, boolean backspace, boolean rotate) { this.typed = typed == null ? "" : typed; this.backspace = backspace; this.rotate = rotate; }

    // ------------------------------------------------------------------ the frame
    public void draw(Ui2D ui, int width, int height, boolean cursorFree, float frameDt) {
        w = width; h = height;
        this.ui = ui;
        // the interface keeps its own clock: it fades and blinks on while the world is paused
        dt = Math.max(0, Math.min(0.1f, frameDt));
        now += dt;
        last = now;
        ui.consumed = false;
        // what closes itself: a dialogue partner who walked off or died, a window out of reach
        if (dialog != null && dialog.npc != 0) {
            var a = g.actor(dialog.npc);
            if (a == null || !a.alive || a.distTo(g.player) > 6) dialog = null;
        }
        if (win == Win.CONTAINER && (container == null || container.removed || container.dist(g.player) > 4)) { win = Win.NONE; if (scene == Scene.INVENTORY) go(Scene.WORLD); }
        if (win == Win.STATION && (station == null || station.removed || station.dist(g.player) > 4)) win = Win.NONE;
        if (win == Win.SHOP && (shop == null || g.econ.merchant(shop) == null || g.econ.merchant(shop).distTo(g.player) > 5)) { win = Win.NONE; if (scene == Scene.INVENTORY) go(Scene.WORLD); }
        events.poll();
        addons.frame();
        // the fragments of the scene's groups, in order; the rest fade out where they last were
        java.util.Set<String> on = new java.util.LinkedHashSet<>();
        Map<String, Placement> at = new java.util.HashMap<>();
        for (String gid : scenes.get(scene)) for (Slot s : groups.getOrDefault(gid, List.of())) if (fragments.containsKey(s.fragment()) && on.add(s.fragment())) at.put(s.fragment(), s.at());
        // dead, the interface steps aside for the death itself (the rest fades out where it was)
        if (!g.player.alive) on.retainAll(java.util.Set.of("death", "console"));
        else if (dialog != null && dialog.pauseMenu) on.retainAll(java.util.Set.of("pause", "console"));
        boolean click = ui.click, rclick = ui.rclick;
        // fading out first (underneath, deaf to the mouse)
        for (Fragment f : fragments.values()) {
            if (on.contains(f.id)) continue;
            f.alpha = Math.max(0, f.alpha - dt / f.fadeOut);
            if (f.alpha <= 0.01f || f.last == null) continue;
            ui.click = false; ui.rclick = false;
            paint(ui, f, f.last, false);
        }
        ui.click = click; ui.rclick = rclick;
        for (String id : on) {
            Fragment f = fragments.get(id);
            boolean want;
            try { want = f.wanted(now); } catch (RuntimeException e) { addons.fault(f, e); continue; }
            f.alpha = want ? Math.min(1, f.alpha + dt / f.fadeIn) : Math.max(0, f.alpha - dt / f.fadeOut);
            if (f.alpha <= 0.01f) continue;
            Placement p = f.moved != null ? f.moved : at.get(id);
            if (!want) { ui.click = false; ui.rclick = false; }
            paint(ui, f, p, want);
            ui.click = click && !ui.consumed; ui.rclick = rclick && !ui.consumed;
            f.last = p;
        }
        ui.click = click; ui.rclick = rclick;
        ui.alpha = 1;
        inventory.drawDragged(ui);
        flushTooltip(ui);
        if (cursorFree || captures()) cursor(ui);
        if (ui.click && !ui.consumed && menu != null) menu = null;
        if (ui.click && !ui.consumed && scene == Scene.INVENTORY) inventory.filterFocus = false;
        typed = ""; backspace = false; rotate = false;
        overlays(ui);
        console.draw(ui, w, h, dt);
    }

    /** cl_showfps and cl_showpos: the frame rate, and where you are, top left over everything. */
    private void overlays(Ui2D ui) {
        float y = 4;
        int fps = (int) console.f("show_fps");
        if (fps > 0) {
            fpsAvg += (dt - fpsAvg) * 0.08f;
            String s = Math.round(1 / Math.max(1e-4f, fpsAvg)) + " fps" + (fps > 1 ? String.format("  %.1f ms", fpsAvg * 1000) : "");
            ui.shadow(s, w / 2f - ui.width(s, 13) / 2, y, 13, 0xFFE8E8A0); y += 16;
        }
        if (console.on("show_pos")) {
            var p = g.player;
            ui.shadow(String.format("pos: %.2f %.2f %.2f", p.x, p.y, p.z), w / 2f - 80, y, 12, 0xFFE0E0E0); y += 14;
            ui.shadow(String.format("ang: %.1f %.1f", Math.toDegrees(g.controller.camPitch), Math.toDegrees(g.controller.camYaw)), w / 2f - 80, y, 12, 0xFFE0E0E0); y += 14;
            ui.shadow(String.format("vel: %.2f", Math.hypot(p.vx, p.vz)), w / 2f - 80, y, 12, 0xFFE0E0E0);
        }
    }
    private float fpsAvg = 1 / 60f;

    /** The developer console (` opens it). */
    public final Console console;
    /** A console command asked for a screenshot (the window takes it after this frame). */
    public boolean screenshotRequested;
    /** What quit and disconnect ask of the window (set by Main). */
    public Runnable onQuit = () -> { }, onTitle = () -> { };
    public void quit() { onQuit.run(); }
    public void toTitle() { onTitle.run(); }

    private void paint(Ui2D ui, Fragment f, Placement p, boolean live) {
        float[] c = f.corner(p, w, h);
        ui.alpha = f.alpha;
        f.live = live;
        try { f.draw(ui, c[0], c[1]); }
        catch (RuntimeException e) { addons.fault(f, e); }
        ui.alpha = 1;
    }

    private void cursor(Ui2D ui) {
        ui.line(ui.mx, ui.my, ui.mx + 10, ui.my + 14, 3, 0xFF000000);
        ui.line(ui.mx, ui.my, ui.mx, ui.my + 16, 3, 0xFF000000);
        ui.line(ui.mx, ui.my, ui.mx + 9, ui.my + 13, 1.5f, 0xFFF0EEE8);
        ui.line(ui.mx, ui.my, ui.mx, ui.my + 15, 1.5f, 0xFFF0EEE8);
    }

    // ------------------------------------------------------------------ probes (the acceptance run drives the mouse with these)
    public float alphaOf(String id) { Fragment f = fragments.get(id); return f == null ? -1 : f.alpha; }
    public Addons addons() { return addons; }
    /** The screen point at a cell position (fractions allowed) of a grid as last drawn, or null. */
    public float[] cellOnScreen(rpg.item.Inventory inv, float cx, float cy) { float[] o = spots.get(inv); return o == null ? null : new float[]{o[0] + cx * o[2], o[1] + cy * o[2]}; }
    /** The centre of a named place last drawn ("slot:MAIN", "bar:4"), or null. */
    public float[] spot(String name) { return named.get(name); }

    // ------------------------------------------------------------------ shared by the fragments
    /** Whether the mouse is over where a fragment was last drawn. */
    boolean hovering(Fragment f) {
        if (ui == null || f.last == null) return false;
        float[] c = f.corner(f.last, w, h);
        return ui.hover(c[0], c[1], f.width(), f.height());
    }

    void tooltip(String t) { tip = t; }

    private void flushTooltip(Ui2D ui) {
        if (tip == null) return;
        String[] lines = tip.split("\n");
        float tw = 0;
        for (String l : lines) tw = Math.max(tw, ui.width(l, 12));
        float x = Math.min(w - tw - 16, ui.mx + 14), y = Math.min(h - lines.length * 15 - 12, ui.my + 16);
        ui.rect(x, y, tw + 12, lines.length * 15 + 8, 0xF0141518);
        ui.border(x, y, tw + 12, lines.length * 15 + 8, 1, Style.EDGE_HI);
        for (int i = 0; i < lines.length; i++) ui.text(lines[i], x + 6, y + 4 + i * 15, 12, i == 0 ? Style.TEXT : Style.DIM);
        tip = null;
    }

    void openMenu(String title, float x, float y, List<MenuItem> items) {
        Menu m = new Menu();
        m.title = title; m.x = x; m.y = y;
        m.items.addAll(items);
        m.items.add(new MenuItem("Cancel", true, null, () -> { }));
        menu = m;
        sound("UIbutton2", 0.4f);
    }

    void sound(String name, float gain) { if (g.sfx != null) g.sfx.ui(name, gain); }

    /** UI sounds for what happened (the provided interface sounds, placed where they belong). */
    private void sounds(String type, String detail) {
        switch (type) {
            case "event:QuestStarted" -> sound("QuestActivate", 0.6f);
            case "event:QuestFlag" -> sound("QuestActivate2", 0.5f);
            case "coins" -> sound("LootCoinSmall", 0.5f);
            case "container" -> sound("ContainerOpen", 0.55f);
            case "event:QuestCompleted" -> { sound("QuestComplete", 0.7f); if (g.music != null) g.music.stinger("sacred_stinger"); }
            case "event:QuestFailed" -> sound("QuestFailed", 0.7f);
            default -> { }
        }
    }

    /** An item as its icon (rendered from its own voxel model), fitted into a box; Gold shows its amount in its colour and suffix. */
    void itemIcon(Ui2D ui, ItemStack s, float x, float y, float iw, float ih, float dim) { itemIcon(ui, s, x, y, iw, ih, dim, false); }

    /** Turned: the item lies across its footprint (its icon is drawn a quarter turn round). */
    void itemIcon(Ui2D ui, ItemStack s, float x, float y, float iw, float ih, float dim, boolean turned) {
        ItemDef d = s.def;
        ui.rect(x, y, iw, ih, Style.fade(s.stolen ? 0x40D06A5E : 0x18FFFFFF, dim));
        if (s.stolen) ui.border(x, y, iw, ih, 1, Style.fade(Style.BAD, dim));
        ItemIcons.Icon ic = icons.of(d);
        float aw = turned ? ic.h() : ic.w(), ah = turned ? ic.w() : ic.h();
        float sc = Math.min((iw - 2) / aw, (ih - 2) / ah), dw = aw * sc, dh = ah * sc;
        float dx = x + (iw - dw) / 2, dy = y + (ih - dh) / 2;
        int tint = Style.fade(0xFFFFFFFF, dim);
        if (turned) ui.quad(ic.texture(), new float[]{dx, dy, dx + dw, dy, dx + dw, dy + dh, dx, dy + dh}, new float[]{0, 1, 0, 0, 1, 0, 1, 1}, tint, 0);
        else ui.rectTex(ic.texture(), dx, dy, dw, dh, 0, 0, 1, 1, tint);
        if (d == Items.COINS) ui.shadow(Currency.text(s.amount), x + 3, y + 1, 11, Style.fade(Currency.color(s.amount), dim));
        else if (d.stackable || s.amount > 1) ui.shadow(amount(s.amount), x + 3, y + 1, 11, Style.fade(s.amount >= 100000 ? Style.TEXT : 0xFFFFE070, dim));
    }

    static String abbreviation(String name) {
        StringBuilder b = new StringBuilder();
        for (String p : name.split(" ")) if (!p.isEmpty() && b.length() < 3) b.append(Character.toUpperCase(p.charAt(0)));
        return b.toString();
    }

    static String amount(int n) { return n >= 10_000_000 ? n / 1_000_000 + "M" : n >= 100_000 ? n / 1000 + "K" : String.valueOf(n); }

    /** One line about an item for tooltips: name, amount, weight, footprint, owner, requirement. */
    String describe(ItemStack s) {
        ItemDef d = s.def;
        StringBuilder b = new StringBuilder(d.name + (d == Items.COINS ? " (" + Currency.full(s.amount) + ")" : s.amount > 1 ? " x " + s.amount : ""));
        b.append(String.format("\n%.1f kg, %d x %d", s.weight(), d.gw, d.gh));
        if (s.inner != null) b.append("\nHolds ").append(s.inner.stacks().size()).append(" item(s) in ").append(s.inner.w).append(" x ").append(s.inner.h);
        if (s.stolen) b.append("\nStolen from ").append(g.nameOf(s.owner));
        if (d.reqSkill != null) b.append("\nRequires ").append(d.reqSkill.title).append(" ").append(d.reqLevel);
        return b.toString();
    }

    // ------------------------------------------------------------------ debug helpers kept for the tests and the debug scene
    public void debugSetLevel(Skill s, int level) {
        g.player.skills.setLevel(s, level);
        g.invariants.rebase(g.player);
        g.state.commit("DebugSetLevel", g.player.id, 0, null, 0, s.name() + " = " + level);
        if (s == Skill.HEALTH) g.player.hp = Math.min(g.player.hp, g.player.maxHp());
    }

    public void gearKit() {
        String[] ids = {"basic_backpack", "godly_sword", "godly_body", "steel_body", "steel_shield", "willow_shortbow", "fire_staff", "iron_arrows", "bronze_axe", "bronze_pickaxe", "trout"};
        int dropped = 0;
        for (String id : ids) {
            ItemDef d;
            try { d = Items.get(id); } catch (IllegalArgumentException e) { g.chat.add("[Debug] Gear kit: no item " + id + ".", rpg.Chat.Kind.DEBUG); continue; }
            ItemStack s = new ItemStack(d, d.stackable ? 100 : 1);
            g.state.mint(d, s.amount);
            // what will not fit is put down at your feet
            if (!g.player.store(s)) { g.objects.drop(s, g.player.x, g.player.y + 0.05f, g.player.z); dropped++; }
        }
        if (dropped > 0) g.chat.add("[Debug] Gear kit: " + dropped + " item(s) didn't fit and are at your feet.", rpg.Chat.Kind.DEBUG);
        g.state.commit("DebugGearKit", g.player.id, 0, null, 0, "");
    }

    void save() { if (new SaveSystem(g).save(SaveSystem.slot("slot1"))) g.chat.add("[Debug] Saved to saves/slot1.json.", rpg.Chat.Kind.DEBUG); }
    void load() { if (new SaveSystem(g).load(SaveSystem.slot("slot1"))) g.chat.add("[Debug] Loaded saves/slot1.json.", rpg.Chat.Kind.DEBUG); }

    rpg.ai.Npc inspected() {
        if (g.interact.focus instanceof rpg.ai.Npc n) { inspectIndex = g.npcs.indexOf(n); return n; }
        if (g.npcs.isEmpty()) return null;
        return g.npcs.get(Math.floorMod(inspectIndex, g.npcs.size()));
    }
    public void nextInspected(int d) { inspectIndex += d; }
}
