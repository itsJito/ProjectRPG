package rpg.ui;

import java.util.List;
import rpg.Profiles;
import rpg.Profiles.Profile;

/**
 * The title screen (gamefixes; 0.2.1: a still shot of a torch-lit dungeon chamber, the menu on the right, the engine's
 * logo and the Updates notes bottom left): the game's name, the title theme playing, and the profiles - up to five characters, each with its own save. With none yet, it asks for the first. Otherwise each profile
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
    private java.util.concurrent.CompletableFuture<Void> deletion;
    public java.util.function.Consumer<String> sound;
    public Runnable settings = () -> {};
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

    /** The panel width of the menu on the right, and its margin from the edge. */
    private static final float MENU_W = 420, MARGIN = 64;
    /** The Updates panel: open, its lines (read from patchnotes.md once), and how far it is scrolled. */
    private boolean updatesOpen;
    private List<String> notes;
    private int notesScroll;

    public void draw(Ui2D ui, int w, int h, float dt, boolean enter, boolean escape) {
        ui.consumed = false;
        if (deletion != null && deletion.isDone()) {
            deletion.join(); deletion = null; confirmDelete = null; refresh();
        }
        fade = Math.min(1, fade + dt * 0.8f);
        // (0.2.1, gamefixes) the menu on the right; the character stands in the middle in front of the doorway: shade over
        // the right of the picture, and a vignette top and bottom, so the title and the cards read
        for (int i = 0; i < 40; i++) ui.rect(w - 360 - i * 10, 0, 360 + i * 10, h, Style.fade(0x06000000, fade));
        for (int i = 0; i < 12; i++) { ui.rect(0, 0, w, 14 + i * 6, 0x05000000); ui.rect(0, h - 14 - i * 6, w, 14 + i * 6, 0x06000000); }
        float x = w - MARGIN - MENU_W, y = 64;
        ui.text("SHADOWVEIL", x, y, 54, Style.fade(Style.ACCENT, fade));
        ui.rect(x, y + 66, 300, 2, Style.fade(0x80D8B46A, fade));
        ui.text("A world that remembers.", x + 2, y + 76, 15, Style.fade(Style.DIM, fade));
        y += 128;

        if (updatesOpen) { updates(ui, w, h, escape); footer(ui, w, h); return; }
        if (confirmDelete != null) { confirm(ui, x, y, enter, escape); footer(ui, w, h); return; }
        if (profiles.isEmpty()) {
            ui.text("Welcome. There are no profiles yet.", x, y, 15, Style.TEXT);
            ui.text("Create your first character to begin.", x, y + 24, 13, Style.DIM);
            if (bigButton(ui, "Create a new profile", x, y + 64, MENU_W, 52, true) || enter) { choice = Choice.NEW; click("NewProfileButton"); }
            if (Style.button(ui, "Settings", x, y + 128, MENU_W, 36, true)) { settings.run(); click("UIbutton1"); }
            if (Style.button(ui, "Quit", x, y + 172, MENU_W, 36, true)) choice = Choice.QUIT;
            footer(ui, w, h);
            return;
        }
        ui.text("PROFILES  " + profiles.size() + " / " + Profiles.MAX, x, y, 12, Style.DIM);
        y += 22;
        for (Profile p : profiles) {
            boolean on = p == selected, hov = ui.hover(x, y, MENU_W, 74);
            ui.rect(x, y, MENU_W, 74, on ? 0xC0242830 : hov ? 0xA01E2228 : 0x90181B20);
            ui.border(x, y, MENU_W, 74, on ? 2 : 1, on ? Style.ACCENT : Style.EDGE);
            if (on) ui.rect(x, y, 4, 74, Style.ACCENT);
            ui.text(p.name, x + 18, y + 10, 19, on ? Style.TEXT : Style.DIM);
            ui.text("Level " + p.combatLevel + "   Total level " + p.totalLevel, x + 18, y + 38, 12, Style.DIM);
            ui.text((p.hasSave() ? "Played " + Profiles.duration(p.playSeconds) + "   last " + ago(p.lastPlayed) : "New - not yet played"), x + 18, y + 54, 11, Style.FAINT);
            ui.text("Slot " + p.slot, x + MENU_W - 54, y + 10, 11, Style.FAINT);
            if (ui.clicked(x, y, MENU_W, 74)) { if (on && ui.doubleClick) { choice = Choice.CONTINUE; click("UI_select"); } else { selected = p; click("UIbutton1"); } }
            y += 82;
        }
        y += 6;
        boolean any = selected != null;
        if (bigButton(ui, selected != null && selected.hasSave() ? "Continue as " + selected.name : "Begin as " + (selected == null ? "..." : selected.name), x, y, MENU_W, 50, any) || enter && any) { choice = Choice.CONTINUE; click("UI_select"); }
        y += 60;
        boolean room = profiles.size() < Profiles.MAX;
        if (Style.button(ui, room ? "New profile" : "New profile (all five slots are taken)", x, y, 206, 36, room)) { choice = Choice.NEW; click("NewProfileButton"); }
        if (Style.button(ui, "Delete profile", x + 214, y, 206, 36, any)) { confirmDelete = selected; click("UIbutton2"); }
        y += 44;
        if (Style.button(ui, "Settings", x, y, MENU_W, 36, true)) { settings.run(); click("UIbutton1"); }
        y += 44;
        if (Style.button(ui, "Quit", x, y, MENU_W, 36, true)) choice = Choice.QUIT;
        footer(ui, w, h);
    }

    /** The patch notes (0.2.1, gamefixes: an Updates button by the engine's logo): patchnotes.md, newest first, read once. */
    private List<String> notes() {
        if (notes != null) return notes;
        String text = null;
        for (String path : new String[]{"patchnotes.md", "../patchnotes.md"}) {
            try { var f = java.nio.file.Path.of(path); if (java.nio.file.Files.exists(f)) { text = java.nio.file.Files.readString(f); break; } } catch (java.io.IOException ignored) { }
        }
        if (text == null) try (var in = TitleScreen.class.getResourceAsStream("/patchnotes.md")) { if (in != null) text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); } catch (java.io.IOException ignored) { }
        notes = text == null ? List.of("No patch notes were found.") : text.lines().toList();
        return notes;
    }

    /** The Updates panel: the notes laid out by their markdown - titles, sections, sub-sections, bullets - wrapped and scrolled. */
    private void updates(Ui2D ui, int w, int h, boolean escape) {
        float pw = Math.min(980, w - 160), ph = h - 180, px = (w - pw) / 2, py = 90;
        Style.card(ui, px, py, pw, ph);
        ui.text("UPDATES", px + 28, py + 20, 22, Style.ACCENT);
        ui.rect(px + 28, py + 52, pw - 56, 1, 0x60D8B46A);
        // the rows: a size, a colour (-1: a rule), an indent and the text
        List<Object[]> rows = new java.util.ArrayList<>();
        float tw = pw - 76;
        for (String raw : notes()) {
            String line = raw.strip();
            if (line.isEmpty()) { rows.add(new Object[]{8f, 0, 0f, ""}); continue; }
            if (line.equals("---")) { rows.add(new Object[]{12f, -1, 0f, ""}); continue; }
            float size; int colour; float indent = 0; String text;
            if (line.startsWith("# ")) { size = 21; colour = Style.ACCENT; text = line.substring(2).replace("Patch Notes ", ""); }
            else if (line.startsWith("## ")) { size = 17; colour = Style.TEXT; text = line.substring(3); }
            else if (line.startsWith("### ")) { size = 14; colour = 0xFFD8B46A; text = line.substring(4); }
            else if (line.startsWith("- ")) { size = 13; colour = Style.DIM; indent = 18; text = line.substring(2); }
            else { size = 13; colour = Style.FAINT; text = line; }
            boolean first = true;
            for (String part : HudFragments.wrap(ui, text, size, tw - indent)) { rows.add(new Object[]{size, colour, indent, (indent > 0 ? (first ? "- " : "  ") : "") + part}); first = false; }
        }
        float top = py + 66, bottom = py + ph - 56;
        int visible = 0; float at = top;
        for (int i = notesScroll; i < rows.size() && at + (float) rows.get(i)[0] + 6 < bottom; i++) { at += (float) rows.get(i)[0] + 6; visible++; }
        if (ui.hover(px, py, pw, ph) && ui.scroll != 0) { notesScroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible), notesScroll - (int) ui.scroll * 3)); ui.consumed = true; }
        at = top;
        for (int i = notesScroll; i < notesScroll + visible && i < rows.size(); i++) {
            Object[] r = rows.get(i);
            float size = (float) r[0];
            if ((int) r[1] == -1) ui.rect(px + 28, at + 5, pw - 56, 1, 0x30FFFFFF);
            else if (!((String) r[3]).isEmpty()) ui.text((String) r[3], px + 34 + (float) r[2], at, size, (int) r[1]);
            at += size + 6;
        }
        if (rows.size() > visible) {
            float track = bottom - top, bar = Math.max(24, track * visible / rows.size()), pos = top + (track - bar) * notesScroll / Math.max(1, rows.size() - visible);
            ui.rect(px + pw - 18, top, 4, track, 0x30FFFFFF); ui.rect(px + pw - 18, pos, 4, bar, 0xA0D8B46A);
        }
        if (Style.button(ui, "Close", px + pw / 2 - 80, py + ph - 46, 160, 34, true) || escape) { updatesOpen = false; click("UIbutton1"); }
    }

    /** Whether the Updates panel is open (Esc closes it before it quits the game). */
    public boolean updatesOpen() { return updatesOpen; }

    private void confirm(Ui2D ui, float x, float y, boolean enter, boolean escape) {
        if (deletion != null) {
            Style.card(ui, x, y, 420, 100); ui.text("Deleting profile...", x + 20, y + 30, 19, Style.TEXT); return;
        }
        Style.card(ui, x, y, 420, 170);
        ui.text("Delete " + confirmDelete.name + "?", x + 20, y + 18, 19, Style.TEXT);
        ui.text("The character and their save are gone for good.", x + 20, y + 52, 13, Style.DIM);
        ui.text("This can't be undone.", x + 20, y + 72, 13, Style.BAD);
        if (Style.button(ui, "Delete for good", x + 20, y + 110, 180, 38, true)) {
            int slot = confirmDelete.slot;
            if (selected == confirmDelete) selected = null;
            deletion = java.util.concurrent.CompletableFuture.runAsync(() -> Profiles.delete(slot));
            click("LogoutProfileButton");
        } else if (Style.button(ui, "Keep", x + 220, y + 110, 180, 38, true) || escape) { confirmDelete = null; click("UIbutton1"); }
    }

    private boolean bigButton(Ui2D ui, String label, float x, float y, float w, float h, boolean enabled) {
        boolean hv = enabled && ui.hover(x, y, w, h);
        ui.rect(x, y, w, h, !enabled ? 0x30202020 : hv ? 0xF0D8B46A : 0xD0B8964E);
        ui.border(x, y, w, h, 1, 0x80FFFFFF);
        ui.flatCentered(label, x + w / 2, y + h / 2 - 10, 17, enabled ? 0xFF16130E : Style.FAINT);
        return enabled && ui.clicked(x, y, w, h);
    }

    /** Bottom left: the engine's logo, the Updates button beside it, the version under them. */
    private void footer(Ui2D ui, int w, int h) {
        ui.rectTex(ui.engineLogo, 20, h - 104, 58, 58, 0, 0, 1, 1, 0xC0FFFFFF);
        if (Style.button(ui, "Updates", 90, h - 92, 130, 34, true) && !updatesOpen) { updatesOpen = true; notesScroll = 0; click("UIbutton1"); }
        ui.text(version + "   -   Astratto Engine", 20, h - 28, 11, Style.FAINT);
        ui.text("Esc: quit   Enter: continue", w - 190, h - 24, 11, Style.FAINT);
        cursor(ui);
    }

    public static void cursor(Ui2D ui) {
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
