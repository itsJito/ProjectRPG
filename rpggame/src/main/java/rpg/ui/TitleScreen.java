package rpg.ui;

import java.util.List;
import rpg.Profiles;
import rpg.Profiles.Profile;

/**
 * The title screen (gamefixes): the game's name over a slow shot of the devmap at dusk, the title theme playing, and the
 * profiles - up to five characters, each with its own save. With none yet, it asks for the first. Otherwise each profile
 * is a card (name, levels, time played, last played): choose one and Continue, or start a New profile while there is a
 * free slot, or Delete one (asked twice). The chosen profile's character stands in the shot.
 */
public final class TitleScreen {
    public enum Choice { NONE, CONTINUE, NEW, QUIT }

    public Choice choice = Choice.NONE;
    public Profile selected;
    private List<Profile> profiles;
    private Profile confirmDelete;
    private float fade;
    public java.util.function.Consumer<String> sound;
    private String version = "";

    public TitleScreen(String version) { this.version = version; refresh(); }

    public void refresh() {
        profiles = Profiles.list();
        if (selected != null) selected = profiles.stream().filter(p -> p.slot == selected.slot).findFirst().orElse(null);
        if (selected == null && !profiles.isEmpty()) {
            // the one last played comes first to hand
            selected = profiles.stream().max(java.util.Comparator.comparingLong(p -> p.lastPlayed)).orElse(profiles.get(0));
        }
    }

    public List<Profile> profiles() { return profiles; }
    private void click(String s) { if (sound != null) sound.accept(s); }

    public void draw(Ui2D ui, int w, int h, float dt, boolean enter, boolean escape) {
        ui.consumed = false;
        fade = Math.min(1, fade + dt * 0.8f);
        // shade over the left of the picture, and a vignette top and bottom, so the title and the cards read
        for (int i = 0; i < 40; i++) ui.rect(0, 0, 360 + i * 10, h, Style.fade(0x06000000, fade));
        for (int i = 0; i < 12; i++) { ui.rect(0, 0, w, 14 + i * 6, 0x05000000); ui.rect(0, h - 14 - i * 6, w, 14 + i * 6, 0x06000000); }
        float x = 64, y = 64;
        ui.text("RPGGAME", x, y, 54, Style.fade(Style.ACCENT, fade));
        ui.rect(x, y + 66, 300, 2, Style.fade(0x80D8B46A, fade));
        ui.text("A world that remembers.", x + 2, y + 76, 15, Style.fade(Style.DIM, fade));
        y += 128;

        if (confirmDelete != null) { confirm(ui, x, y, enter, escape); footer(ui, w, h); return; }
        if (profiles.isEmpty()) {
            ui.text("Welcome. There are no profiles yet.", x, y, 15, Style.TEXT);
            ui.text("Create your first character to begin.", x, y + 24, 13, Style.DIM);
            if (bigButton(ui, "Create a new profile", x, y + 64, 360, 52, true) || enter) { choice = Choice.NEW; click("NewProfileButton"); }
            if (Style.button(ui, "Quit", x, y + 128, 360, 36, true)) choice = Choice.QUIT;
            footer(ui, w, h);
            return;
        }
        ui.text("PROFILES  " + profiles.size() + " / " + Profiles.MAX, x, y, 12, Style.DIM);
        y += 22;
        for (Profile p : profiles) {
            boolean on = p == selected, hov = ui.hover(x, y, 420, 74);
            ui.rect(x, y, 420, 74, on ? 0xC0242830 : hov ? 0xA01E2228 : 0x90181B20);
            ui.border(x, y, 420, 74, on ? 2 : 1, on ? Style.ACCENT : Style.EDGE);
            if (on) ui.rect(x, y, 4, 74, Style.ACCENT);
            ui.text(p.name, x + 18, y + 10, 19, on ? Style.TEXT : Style.DIM);
            ui.text("Level " + p.combatLevel + "   Total level " + p.totalLevel, x + 18, y + 38, 12, Style.DIM);
            ui.text((p.hasSave() ? "Played " + Profiles.duration(p.playSeconds) + "   last " + ago(p.lastPlayed) : "New - not yet played"), x + 18, y + 54, 11, Style.FAINT);
            ui.text("Slot " + p.slot, x + 420 - 54, y + 10, 11, Style.FAINT);
            if (ui.clicked(x, y, 420, 74)) { if (on && ui.doubleClick) { choice = Choice.CONTINUE; click("UI_select"); } else { selected = p; click("UIbutton1"); } }
            y += 82;
        }
        y += 6;
        boolean any = selected != null;
        if (bigButton(ui, selected != null && selected.hasSave() ? "Continue as " + selected.name : "Begin as " + (selected == null ? "..." : selected.name), x, y, 420, 50, any) || enter && any) { choice = Choice.CONTINUE; click("UI_select"); }
        y += 60;
        boolean room = profiles.size() < Profiles.MAX;
        if (Style.button(ui, room ? "New profile" : "New profile (all five slots are taken)", x, y, 206, 36, room)) { choice = Choice.NEW; click("NewProfileButton"); }
        if (Style.button(ui, "Delete profile", x + 214, y, 206, 36, any)) { confirmDelete = selected; click("UIbutton2"); }
        y += 44;
        if (Style.button(ui, "Quit", x, y, 420, 36, true)) choice = Choice.QUIT;
        footer(ui, w, h);
    }

    private void confirm(Ui2D ui, float x, float y, boolean enter, boolean escape) {
        Style.card(ui, x, y, 420, 170);
        ui.text("Delete " + confirmDelete.name + "?", x + 20, y + 18, 19, Style.TEXT);
        ui.text("The character and their save are gone for good.", x + 20, y + 52, 13, Style.DIM);
        ui.text("This can't be undone.", x + 20, y + 72, 13, Style.BAD);
        if (Style.button(ui, "Delete for good", x + 20, y + 110, 180, 38, true)) {
            Profiles.delete(confirmDelete.slot);
            if (selected == confirmDelete) selected = null;
            confirmDelete = null;
            click("LogoutProfileButton");
            refresh();
        } else if (Style.button(ui, "Keep", x + 220, y + 110, 180, 38, true) || escape) { confirmDelete = null; click("UIbutton1"); }
    }

    private boolean bigButton(Ui2D ui, String label, float x, float y, float w, float h, boolean enabled) {
        boolean hv = enabled && ui.hover(x, y, w, h);
        ui.rect(x, y, w, h, !enabled ? 0x30202020 : hv ? 0xF0D8B46A : 0xD0B8964E);
        ui.border(x, y, w, h, 1, 0x80FFFFFF);
        ui.flatCentered(label, x + w / 2, y + h / 2 - 10, 17, enabled ? 0xFF16130E : Style.FAINT);
        return enabled && ui.clicked(x, y, w, h);
    }

    private void footer(Ui2D ui, int w, int h) {
        ui.text("Version " + version + "   -   Astratto Engine", 16, h - 24, 11, Style.FAINT);
        ui.text("Esc: quit   Enter: continue", w - 190, h - 24, 11, Style.FAINT);
        cursor(ui);
    }

    static void cursor(Ui2D ui) {
        ui.line(ui.mx, ui.my, ui.mx + 10, ui.my + 14, 3, 0xFF000000);
        ui.line(ui.mx, ui.my, ui.mx, ui.my + 16, 3, 0xFF000000);
        ui.line(ui.mx, ui.my, ui.mx + 9, ui.my + 13, 1.5f, 0xFFF0EEE8);
        ui.line(ui.mx, ui.my, ui.mx, ui.my + 15, 1.5f, 0xFFF0EEE8);
    }

    private static String ago(long at) {
        if (at <= 0) return "never";
        long m = (System.currentTimeMillis() - at) / 60000;
        if (m < 2) return "just now";
        if (m < 60) return m + " min ago";
        if (m < 60 * 24) return (m / 60) + " h ago";
        return (m / (60 * 24)) + " days ago";
    }
}
