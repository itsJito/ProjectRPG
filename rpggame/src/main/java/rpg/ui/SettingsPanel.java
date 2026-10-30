package rpg.ui;

import rpg.Preferences;
import static org.lwjgl.glfw.GLFW.*;

/** Four short, stable settings pages. Changes apply immediately; Escape cancels a binding or returns to play. */
public final class SettingsPanel {
    private final java.util.function.Consumer<Preferences> applier;
    private final boolean persistent;
    private Runnable beforeShow = () -> {};
    public Preferences values;
    public boolean open;
    private int tab, controlsPage, modifierCandidate = -1;
    private Preferences.Action binding;
    private static final java.nio.file.Path PATH = java.nio.file.Path.of("saves", "preferences.json");
    public Runnable displayChanged = () -> {};
    public SettingsPanel(Hud hud) {
        this(hud.g.scripted ? new Preferences() : Preferences.load(PATH), p -> {
            p.apply(hud.g.cfg); p.mix(hud.g.game.commands()); hud.gfx.fovDeg = p.fov; hud.console.core.set("sensitivity", String.valueOf(p.sensitivity));
        }, !hud.g.scripted);
        beforeShow = hud::closeAll;
    }
    public SettingsPanel(Preferences initial, java.util.function.Consumer<Preferences> apply, boolean persistent) {
        values = initial; applier = apply; this.persistent = persistent; apply();
    }
    public boolean capturingKey() { return binding != null; }
    public void keyInput(astratto.platform.Input input) {
        if (binding == null) return;
        if (input.keyPressed(GLFW_KEY_ESCAPE)) { binding = null; modifierCandidate = -1; return; }
        int mods = Preferences.modifiers(input);
        for (int button = 0; button <= GLFW_MOUSE_BUTTON_LAST; button++) if (input.mousePressed(button)
                && values.bind(binding, astratto.platform.Input.MOUSE_OFFSET + button | mods << 16)) { binding = null; modifierCandidate = -1; changed(); return; }
        for (int key = GLFW_KEY_SPACE; key <= GLFW_KEY_LAST; key++) if (input.keyPressed(key)) {
            if (Preferences.modifier(key)) { modifierCandidate = key; continue; }
            if (values.bind(binding, key | mods << 16)) { binding = null; modifierCandidate = -1; changed(); return; }
        }
        if (modifierCandidate >= 0 && input.keyReleased(modifierCandidate) && values.bind(binding, modifierCandidate)) {
            binding = null; modifierCandidate = -1; changed();
        }
    }
    public void show() { beforeShow.run(); open = true; }
    public void showControls(int page) { show(); tab = 2; controlsPage = Math.max(0,page); }
    private static int controlColumns(float width) { return width < 800 ? 1 : 2; }
    private static int controlsPerPage(float width, float height) { return Math.max(1, (int)((height - (height < 500 ? 240 : 310)) / 39)) * controlColumns(width); }
    public static int controlPageCount(int width, int height) {
        int perPage=controlsPerPage(Math.min(920,width-40),Math.min(650,height-40));
        return (Preferences.Action.values().length+perPage-1)/perPage;
    }
    public boolean escape() { if (!open) return false; binding = null; open = false; changed(); return true; }
    private void apply() { applier.accept(values); }
    private void changed() { apply(); displayChanged.run(); if (persistent) values.save(PATH); }

    public void draw(Ui2D ui, int width, int height) {
        float w = Math.min(920, width - 40), h = Math.min(650, height - 40), x = (width - w) / 2, y = (height - h) / 2;
        boolean compact = tab == 2 && h < 500;
        ui.rect(0, 0, width, height, 0xB5101418); ui.rectTex(ui.parchment, x, y, w, h, 0, 0, w / 128, h / 128, 0xFFFFFFFF); ui.border(x, y, w, h, 1, Style.ACCENT);
        ui.text(ui.body, "SETTINGS", x + 28, y + 18, 32, 0xFFFFD15E);
        if (!compact) ui.text("Tune your display, sound and controls", x + 28, y + 57, 13, Style.DIM);
        String[] tabs = {"Display", "Audio", "Controls", "Accessibility"};
        for (int i = 0; i < tabs.length; i++) { float tx = x + 28 + i * (w - 56) / 4; if (ui.button(tabs[i], tx, y + (compact ? 52 : 88), (w - 64) / 4, 32, true)) { tab = i; binding = null; } if (i == tab) ui.rect(tx, y + (compact ? 86 : 122), (w - 64) / 4, 2, Style.ACCENT); }
        float lx = x + 36, rx = x + w - 290, yy = y + (compact ? 96 : 146);
        if (tab == 0) {
            if (row(ui, "Display mode", values.fullscreen ? "Fullscreen" : "Windowed", lx, rx, yy)) { values.fullscreen = !values.fullscreen; changed(); } yy += 45;
            if (row(ui, "Vertical sync", yes(values.vsync), lx, rx, yy)) { values.vsync = !values.vsync; changed(); } yy += 45;
            if (row(ui, "Graphics quality", new String[]{"Low", "Medium", "High", "Ultra"}[values.quality], lx, rx, yy)) { values.quality = (values.quality + 1) % 4; changed(); } yy += 45;
            if (row(ui, "Performance budget", values.targetFps + " FPS", lx, rx, yy)) { values.targetFps = values.targetFps == 30 ? 60 : values.targetFps == 60 ? 120 : 30; changed(); } yy += 45;
            if (row(ui, "Dynamic resolution", yes(values.autoScale), lx, rx, yy)) { values.autoScale = !values.autoScale; changed(); } yy += 45;
            values.renderScale = slider(ui, "Resolution scale", values.renderScale, 0.5f, 1, lx, rx, yy); yy += 45;
            values.fov = slider(ui, "Field of view", values.fov, 50, 110, lx, rx, yy);
        } else if (tab == 1) {
            values.master = slider(ui, "Master volume", values.master, 0, 1, lx, rx, yy); yy += 45;
            values.sfx = slider(ui, "Effects", values.sfx, 0, 1, lx, rx, yy); yy += 45;
            values.music = slider(ui, "Music", values.music, 0, 1, lx, rx, yy); yy += 45;
            values.voice = slider(ui, "Voices", values.voice, 0, 1, lx, rx, yy); yy += 45;
            values.ambience = slider(ui, "Ambience", values.ambience, 0, 1, lx, rx, yy); yy += 45;
            values.ui = slider(ui, "Interface", values.ui, 0, 1, lx, rx, yy); yy += 48;
            ui.text("Output: system default device / stereo", lx, yy, 13, Style.DIM);
        } else if (tab == 2) {
            values.sensitivity = slider(ui, "Mouse sensitivity", values.sensitivity, 0.1f, 4, lx, rx, yy); yy += compact ? 40 : 46;
            int columns=controlColumns(w), perPage = controlsPerPage(w,h);
            int pages = (Preferences.Action.values().length + perPage - 1) / perPage;
            controlsPage = Math.clamp(controlsPage, 0, pages - 1);
            int first = controlsPage * perPage, end = Math.min(first + perPage, Preferences.Action.values().length);
            for (int i = first; i < end; i++) {
                var a = Preferences.Action.values()[i]; int row = i - first;
                float col = row % columns * (w - 72) / columns, cy = yy + row / columns * 39;
                ui.text(a.title, lx + col, cy + 7, 12, Style.TEXT);
                if (ui.button(binding == a ? "Key or chord..." : values.label(a), lx + col + (w - 72) / columns - 165, cy, 145, 30, true)) { binding = a; modifierCandidate = -1; }
            }
            float py = y + h - (compact ? 106 : 144);
            if (ui.button("Previous", lx, py, 120, 28, controlsPage > 0)) { controlsPage--; binding = null; }
            ui.centered("Page " + (controlsPage + 1) + " / " + pages, x + w / 2, py + 6, 13, Style.DIM);
            if (ui.button("Next", x + w - 156, py, 120, 28, controlsPage + 1 < pages)) { controlsPage++; binding = null; }
            ui.text("Hold modifiers, then press a key or mouse button. Escape cancels capture.", lx, y + h - (compact ? 70 : 106), 12, Style.DIM);
        } else {
            if (row(ui, "Reduced camera motion", yes(values.reducedMotion), lx, rx, yy)) { values.reducedMotion = !values.reducedMotion; changed(); } yy += 45;
            if (row(ui, "World speech captions", yes(values.subtitles), lx, rx, yy)) { values.subtitles = !values.subtitles; changed(); } yy += 45;
            if (row(ui, "Invert vertical look", yes(values.invertY), lx, rx, yy)) { values.invertY = !values.invertY; changed(); } yy += 45;
            if (row(ui, "Crouch input", values.toggleCrouch ? "Toggle" : "Hold", lx, rx, yy)) { values.toggleCrouch = !values.toggleCrouch; changed(); } yy += 45;
            if (row(ui, "Sprint input", values.toggleSprint ? "Toggle" : "Hold", lx, rx, yy)) { values.toggleSprint = !values.toggleSprint; changed(); }
        }
        if (!compact) ui.text("Changes apply immediately", lx, y + h - 76, 12, Style.DIM);
        if (ui.button("Restore defaults", lx, y + h - 50, 180, 31, true)) { values = new Preferences(); binding = null; changed(); }
        if (ui.button("Done", x + w - 190, y + h - 50, 154, 31, true)) escape();
    }
    private static String yes(boolean value) { return value ? "On" : "Off"; }
    private static boolean row(Ui2D ui, String label, String value, float x, float rx, float y) { ui.text(label, x, y + 7, 14, Style.TEXT); return ui.button(value, rx, y, 250, 31, true); }
    private float slider(Ui2D ui, String label, float value, float min, float max, float x, float rx, float y) {
        ui.text(label, x, y + 7, 14, Style.TEXT);
        if (ui.button("-", rx, y, 35, 31, true)) { value = Math.max(min, value - (max - min) / 10); setSlider(label, value); changed(); }
        ui.flatCentered(String.format(java.util.Locale.ROOT, "%.2f", value), rx + 125, y + 7, 14, Style.ACCENT);
        ui.rect(rx + 50, y + 28, 150, 2, Style.DIM); ui.rect(rx + 50, y + 28, 150 * (value - min) / (max - min), 2, Style.ACCENT);
        if (ui.button("+", rx + 215, y, 35, 31, true)) { value = Math.min(max, value + (max - min) / 10); setSlider(label, value); changed(); }
        return value;
    }
    private void setSlider(String label, float value) {
        switch (label) { case "Master volume" -> values.master = value; case "Effects" -> values.sfx = value; case "Music" -> values.music = value; case "Voices" -> values.voice = value; case "Ambience" -> values.ambience = value; case "Interface" -> values.ui = value; case "Resolution scale" -> values.renderScale = value; case "Field of view" -> values.fov = value; case "Mouse sensitivity" -> values.sensitivity = value; default -> throw new IllegalArgumentException(label); }
    }
}
