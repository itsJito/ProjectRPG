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
    private int tab;
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
        if (input.keyPressed(GLFW_KEY_ESCAPE)) { binding = null; return; }
        for (int button = 0; button <= GLFW_MOUSE_BUTTON_LAST; button++) if (input.mousePressed(button) && values.bind(binding, astratto.platform.Input.MOUSE_OFFSET + button)) { binding = null; changed(); return; }
        for (int key = GLFW_KEY_SPACE; key <= GLFW_KEY_LAST; key++) if (input.keyPressed(key) && values.bind(binding, key)) { binding = null; changed(); return; }
    }
    public void show() { beforeShow.run(); open = true; }
    public boolean escape() { if (!open) return false; binding = null; open = false; changed(); return true; }
    private void apply() { applier.accept(values); }
    private void changed() { apply(); displayChanged.run(); if (persistent) values.save(PATH); }

    public void draw(Ui2D ui, int width, int height) {
        float w = Math.min(920, width - 40), h = Math.min(650, height - 40), x = (width - w) / 2, y = (height - h) / 2;
        ui.rect(0, 0, width, height, 0xB5101418); ui.rectTex(ui.parchment, x, y, w, h, 0, 0, w / 128, h / 128, 0xFFFFFFFF); ui.border(x, y, w, h, 1, Style.ACCENT);
        ui.text(ui.body, "SETTINGS", x + 28, y + 18, 32, 0xFFFFD15E);
        ui.text("Tune your display, sound and controls", x + 28, y + 57, 13, Style.DIM);
        String[] tabs = {"Display", "Audio", "Controls", "Accessibility"};
        for (int i = 0; i < tabs.length; i++) { float tx = x + 28 + i * (w - 56) / 4; if (ui.button(tabs[i], tx, y + 88, (w - 64) / 4, 32, true)) { tab = i; binding = null; } if (i == tab) ui.rect(tx, y + 122, (w - 64) / 4, 2, Style.ACCENT); }
        float lx = x + 36, rx = x + w - 290, yy = y + 146;
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
            values.sensitivity = slider(ui, "Mouse sensitivity", values.sensitivity, 0.1f, 4, lx, rx, yy); yy += 46;
            for (int i = 0; i < Preferences.Action.values().length; i++) {
                var a = Preferences.Action.values()[i]; float col = i % 2 * (w - 72) / 2, cy = yy + i / 2 * 39;
                ui.text(a.title, lx + col, cy + 7, 13, Style.TEXT);
                int key = values.keys.get(a); String name = key >= astratto.platform.Input.MOUSE_OFFSET ? "Mouse " + (key - astratto.platform.Input.MOUSE_OFFSET + 1) : glfwGetKeyName(key, 0); if (name == null) name = key == GLFW_KEY_LEFT_CONTROL ? "Ctrl" : key == GLFW_KEY_LEFT_SHIFT ? "Shift" : key == GLFW_KEY_SPACE ? "Space" : "Key " + key;
                if (ui.button(binding == a ? "Press a key..." : name.toUpperCase(java.util.Locale.ROOT), lx + col + (w - 72) / 2 - 165, cy, 145, 30, true)) binding = a;
            }
            ui.text("F5 quicksave  /  F6 quickload  /  Escape cancels rebinding", lx, y + h - 114, 12, Style.DIM);
        } else {
            if (row(ui, "Reduced camera motion", yes(values.reducedMotion), lx, rx, yy)) { values.reducedMotion = !values.reducedMotion; changed(); } yy += 45;
            if (row(ui, "World speech captions", yes(values.subtitles), lx, rx, yy)) { values.subtitles = !values.subtitles; changed(); } yy += 45;
            if (row(ui, "Invert vertical look", yes(values.invertY), lx, rx, yy)) { values.invertY = !values.invertY; changed(); } yy += 45;
            if (row(ui, "Crouch input", values.toggleCrouch ? "Toggle" : "Hold", lx, rx, yy)) { values.toggleCrouch = !values.toggleCrouch; changed(); } yy += 45;
            if (row(ui, "Sprint input", values.toggleSprint ? "Toggle" : "Hold", lx, rx, yy)) { values.toggleSprint = !values.toggleSprint; changed(); }
        }
        ui.text("Changes apply immediately", lx, y + h - 76, 12, Style.DIM);
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
