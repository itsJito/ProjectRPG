package rpg.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import rpg.actor.Appearance;

/**
 * Character creation (GDD 2.1): sex, one of three bodies (Masculine, Heavy, Skinny), one of 12 skin tones, twenty
 * hairstyles per sex, twenty facial-hair presets, twenty tops, legs and shoes each; ten natural hair colours for hair and
 * facial hair, twenty-four garment colours for the clothes. Appearance is cosmetic only.
 *
 * <p>A full-screen interface: four tabs along the top (Body, Hair, Clothing, Finish), the choices of the open tab in a
 * panel on the left - big cards for sex and build, swatch grids for skin and colours, named lists for styles - and the
 * figure itself on the devmap to the right, turning slowly or dragged round, the camera closing in on the face while the
 * hair is chosen. Back and Next walk the tabs; Finish names the character and starts.
 */
public final class Creator {
    public final Appearance look = new Appearance();
    public final StringBuilder name = new StringBuilder("Adventurer");
    public boolean done;
    /** Backed out of (Esc): no profile is made. */
    public boolean cancelled;
    public float turn;
    private boolean dragging;
    private float lastMx;
    /** Whether the player is turning the figure by hand (the slow turn waits meanwhile). */
    public boolean dragging() { return dragging; }
    private long seed = 1;

    /** The open tab, and on Clothing which garment. */
    public int tab;
    public int garment;
    public static final String[] TABS = {"Body", "Hair", "Clothing", "Finish"};
    private static final String[] GARMENTS = {"Top", "Legs", "Shoes"};
    /** How close the camera comes (0 the whole figure, 1 the face), eased toward what the tab wants. */
    public float focus;

    /** One row of choices: its label, how many, the getter and setter, and how to name the current value. */
    public record Row(String label, int count, IntSupplier get, IntConsumer set, java.util.function.IntFunction<String> text, boolean colour) {}

    public List<Row> rows() {
        List<Row> r = new ArrayList<>();
        r.add(new Row("Sex", 2, () -> look.female ? 1 : 0, v -> { look.female = v == 1; if (look.female) look.beard = 0; }, v -> v == 1 ? "Female" : "Male", false));
        r.add(new Row("Body", Appearance.BODY_TYPES, () -> look.bodyType, v -> look.bodyType = v, v -> Appearance.bodyNames(look.female)[v], false));
        r.add(new Row("Skin", Appearance.SKIN_TONES, () -> look.skin, v -> look.skin = v, v -> "Tone " + (v + 1) + " / 12", false));
        r.add(new Row("Hair", Appearance.HAIRSTYLES, () -> look.hair, v -> look.hair = v, v -> (look.female ? Appearance.HAIR_FEMALE : Appearance.HAIR_MALE)[v], false));
        r.add(new Row("Hair colour", Appearance.HAIR_COLORS, () -> look.hairColor, v -> { if (look.beardColor == look.hairColor) look.beardColor = v; look.hairColor = v; }, v -> String.format("H%02d %s", v + 1, Appearance.HAIR_NAMES[v]), true));
        if (!look.female) {
            r.add(new Row("Facial hair", Appearance.BEARDS, () -> look.beard, v -> look.beard = v, v -> Appearance.BEARD_NAMES[v], false));
            if (look.beard > 0) r.add(new Row("Facial hair colour", Appearance.HAIR_COLORS, () -> look.beardColor, v -> look.beardColor = v, v -> String.format("H%02d %s", v + 1, Appearance.HAIR_NAMES[v]), true));
        }
        r.add(new Row("Top", Appearance.TOPS, () -> look.top, v -> look.top = v, v -> Appearance.TOP_NAMES[v], false));
        r.add(new Row("Top colour", Appearance.COLORS, () -> look.topColor, v -> look.topColor = v, v -> String.format("C%02d %s", v + 1, Appearance.COLOR_NAMES[v]), true));
        r.add(new Row("Legs", Appearance.BOTTOMS, () -> look.bottom, v -> look.bottom = v, v -> Appearance.BOTTOM_NAMES[v], false));
        r.add(new Row("Legs colour", Appearance.COLORS, () -> look.bottomColor, v -> look.bottomColor = v, v -> String.format("C%02d %s", v + 1, Appearance.COLOR_NAMES[v]), true));
        r.add(new Row("Shoes", Appearance.SHOES, () -> look.shoes, v -> look.shoes = v, v -> Appearance.SHOE_NAMES[v], false));
        r.add(new Row("Shoes colour", Appearance.COLORS, () -> look.shoesColor, v -> look.shoesColor = v, v -> String.format("C%02d %s", v + 1, Appearance.COLOR_NAMES[v]), true));
        return r;
    }

    /** Plays an interface sound by name (set by the game once its sound bank exists). */
    public java.util.function.Consumer<String> sound;
    private void click() { if (sound != null) sound.accept("UIbutton1"); }

    public void step(Row row, int d) { row.set().accept(Math.floorMod(row.get().getAsInt() + d, row.count())); }

    public void randomise() {
        Appearance a = Appearance.random(seed++ * 7919 + System.nanoTime() % 1000);
        look.female = a.female; look.bodyType = a.bodyType; look.skin = a.skin; look.hair = a.hair; look.hairColor = a.hairColor; look.beard = a.beard; look.beardColor = a.beardColor;
        look.top = a.top; look.topColor = a.topColor; look.bottom = a.bottom; look.bottomColor = a.bottomColor; look.shoes = a.shoes; look.shoesColor = a.shoesColor;
    }

    /**
     * The figure's turn for a frame: slowly round by itself, or - while the hair is chosen - round to face the camera, so
     * the face is what is seen up close. Held still while dragged.
     */
    public void spin(float dt) {
        if (dragging) return;
        if (tab == 1) {
            float tau = (float) (2 * Math.PI), off = (float) (turn - Math.rint(turn / tau) * tau);
            turn -= off * Math.min(1, dt * 3);
        } else turn += dt * 0.25f;
    }

    /** Randomises only what the open tab chooses (the rest of the look is kept). */
    public void randomiseTab() {
        Appearance a = Appearance.random(seed++ * 7919 + System.nanoTime() % 1000);
        switch (tab) {
            case 0 -> { look.female = a.female; look.bodyType = a.bodyType; look.skin = a.skin; if (look.female) look.beard = 0; }
            case 1 -> { look.hair = a.hair; look.hairColor = a.hairColor; look.beard = look.female ? 0 : a.beard; look.beardColor = a.beardColor; }
            case 2 -> { look.top = a.top; look.topColor = a.topColor; look.bottom = a.bottom; look.bottomColor = a.bottomColor; look.shoes = a.shoes; look.shoesColor = a.shoesColor; }
            default -> randomise();
        }
    }

    // ------------------------------------------------------------------ drawing
    private static final int SEL = 0xFFD8B46A, SEL_BG = 0x38D8B46A, HOVER_BG = 0x18FFFFFF, ROW_BG = 0x10FFFFFF;
    /** Where the camera looks: -1 the feet, 0 the whole figure, 1 the face (eased toward what is being chosen). */
    public float aim;
    /** The figure's zoom by the wheel (0..1 closer). */
    public float zoom;
    private float panelFade;
    private String nameWarning;
    private static final String[] STEPS = {"Body", "Hair", "Clothing", "Finish"};
    private static final String[] STEP_HINT = {"Sex, build and skin", "Style and colour", "Top, legs and shoes", "Name and review"};
    private static final String[] NAMES_M = {"Aldric", "Bram", "Corin", "Edrin", "Garrick", "Hal", "Osric", "Rowan", "Tobin", "Wulfric"};
    private static final String[] NAMES_F = {"Ada", "Brynn", "Elsa", "Isolde", "Maren", "Nell", "Rhea", "Sabine", "Wren", "Yara"};

    public void draw(Ui2D ui, int w, int h, java.util.Collection<Character> typed, boolean backspace, boolean enter) {
        ui.consumed = false;
        if (tab == 3) {
            for (char c : typed) if (name.length() < 12 && (Character.isLetterOrDigit(c) || c == ' ' || c == '-')) { name.append(c); nameWarning = null; }
            if (backspace && name.length() > 0) name.setLength(name.length() - 1);
        }
        panelFade = Math.min(1, panelFade + 0.06f);
        // the camera: the face for the hair, the feet while the shoes are chosen, the whole figure otherwise; the wheel brings it closer
        float want = tab == 1 ? 1 : tab == 2 && garment == 2 ? -1 : 0;
        aim += (want - aim) * 0.12f;
        focus = aim < 0 ? -aim * 0.45f : aim;
        if (ui.scroll != 0 && ui.mx > 490) zoom = Math.max(0, Math.min(1, zoom + (float) ui.scroll * 0.12f));

        // the shade behind the panel side, so the text reads over the bright world
        for (int i = 0; i < 36; i++) ui.rect(0, 0, 320 + i * 9, h, 0x06000000);
        ui.text("CREATE YOUR CHARACTER", 36, 26, 24, Style.ACCENT);
        ui.text("Appearance is cosmetic: it grants no skill or stat.", 38, 58, 12, Style.DIM);

        // the steps: a rail of four, the done ones ticked, the open one lit
        float sy = 92, sw = 106;
        for (int i = 0; i < STEPS.length; i++) {
            float sx = 36 + i * (sw + 6);
            boolean on = tab == i, done = i < tab, hov = ui.hover(sx, sy, sw, 46);
            ui.rect(sx, sy, sw, 46, on ? 0xC0242830 : hov ? 0x90202428 : 0x70141619);
            ui.rect(sx, sy + 44, sw, 2, on ? SEL : done ? 0x90D8B46A : 0x30FFFFFF);
            ui.disc(sx + 16, sy + 16, 9, on ? SEL : done ? 0xC0D8B46A : 0x40FFFFFF);
            if (done) { ui.line(sx + 11, sy + 16, sx + 15, sy + 20, 2.2f, 0xFF16130E); ui.line(sx + 15, sy + 20, sx + 22, sy + 11, 2.2f, 0xFF16130E); }
            else ui.flatCentered(String.valueOf(i + 1), sx + 16, sy + 9, 12, on ? 0xFF16130E : Style.DIM);
            ui.text(STEPS[i], sx + 32, sy + 8, 14, on ? Style.TEXT : Style.DIM);
            ui.text(STEP_HINT[i], sx + 10, sy + 29, 9, Style.FAINT);
            if (ui.clicked(sx, sy, sw, 46) && !on) { tab = i; click(); }
        }

        // the panel of the open step
        float px = 36, py = 150, pw = 442, ph = h - py - 196;
        ui.rect(px, py, pw, ph, Style.fade(0xD8121418, panelFade));
        ui.border(px, py, pw, ph, 1, 0x30FFFFFF);
        ui.rect(px, py, pw, 3, Style.fade(SEL, panelFade));
        float y = py + 20;
        switch (tab) {
            case 0 -> y = bodyTab(ui, px + 20, y, pw - 40);
            case 1 -> y = hairTab(ui, px + 20, y, pw - 40);
            case 2 -> y = clothingTab(ui, px + 20, y, pw - 40);
            default -> y = finishTab(ui, px + 20, y, pw - 40);
        }

        // a summary card of the whole look, always to hand under the panel
        summary(ui, px, py + ph + 8, pw, 72);

        // the footer: randomise, back, and the primary button
        float fy = h - 100;
        if (tab != 3 && Style.button(ui, "Randomise " + STEPS[tab].toLowerCase(), px, fy, 216, 34, true)) { randomiseTab(); click(); }
        if (tab == 3 && Style.button(ui, "Suggest a name", px, fy, 216, 34, true)) { suggestName(); click(); }
        if (Style.button(ui, "Randomise everything", px + 226, fy, 216, 34, true)) { randomise(); click(); }
        if (Style.button(ui, tab == 0 ? "< Title screen" : "< Back", px, fy + 44, 150, 40, true)) { if (tab == 0) cancelled = true; else tab--; click(); }
        boolean named = name.toString().trim().length() >= 2;
        if (tab < 3) { if (primary(ui, "Next: " + STEPS[tab + 1] + "  >", px + 162, fy + 44, 280, 40, true)) { tab++; click(); } }
        else if (primary(ui, "Begin your adventure", px + 162, fy + 44, 280, 40, named) || enter && named) start();
        else if (enter) nameWarning = "A name needs at least two letters.";

        // the figure's own controls, under it
        float cx = 478 + (w - 478) / 2f;
        ui.rect(cx - 170, h - 54, 340, 38, 0x70101214);
        if (Style.button(ui, "<", cx - 164, h - 50, 34, 30, true)) turn -= 0.5f;
        if (Style.button(ui, ">", cx + 130, h - 50, 34, 30, true)) turn += 0.5f;
        ui.centered("Drag to turn  -  wheel to zoom", cx, h - 43, 12, Style.DIM);
        if (ui.down && dragging) turn += (ui.mx - lastMx) * 0.012f;
        if (ui.click && ui.mx > 490 && ui.my < h - 60) dragging = true;
        if (!ui.down) dragging = false;
        lastMx = ui.mx;
        TitleScreen.cursor(ui);
    }

    /** The accent button: the way forward. */
    private boolean primary(Ui2D ui, String label, float x, float y, float w, float h, boolean enabled) {
        boolean hv = enabled && ui.hover(x, y, w, h);
        ui.rect(x, y, w, h, !enabled ? 0x30202020 : hv ? 0xF0D8B46A : 0xD0B8964E);
        ui.border(x, y, w, h, 1, 0x80FFFFFF);
        ui.flatCentered(label, x + w / 2, y + h / 2 - 9, 15, enabled ? 0xFF16130E : Style.FAINT);
        return enabled && ui.clicked(x, y, w, h);
    }

    /** The whole look at a glance: swatches of skin, hair and the three garments, and their names. */
    private void summary(Ui2D ui, float x, float y, float w, float h) {
        ui.rect(x, y, w, h, 0xB0101214);
        ui.border(x, y, w, h, 1, 0x22FFFFFF);
        int[] sw = {Appearance.SKIN[look.skin], Appearance.hairColor(look.hairColor), Appearance.color(look.topColor), Appearance.color(look.bottomColor), Appearance.color(look.shoesColor)};
        String[] label = {"Body", "Hair", "Top", "Legs", "Shoes"};
        String[] what = {look.bodyName() + (look.female ? " female" : " male"), (look.female ? Appearance.HAIR_FEMALE : Appearance.HAIR_MALE)[look.hair],
                Appearance.TOP_NAMES[look.top], Appearance.BOTTOM_NAMES[look.bottom], Appearance.SHOE_NAMES[look.shoes]};
        float cw = (w - 20) / 5;
        for (int i = 0; i < 5; i++) {
            float cx = x + 10 + i * cw;
            ui.text(label[i].toUpperCase(), cx, y + 8, 9, Style.FAINT);
            ui.rect(cx, y + 24, 22, 22, 0xFF000000 | sw[i]);
            ui.border(cx, y + 24, 22, 22, 1, 0x40FFFFFF);
            String t = what[i];
            boolean cut = false;
            while (t.length() > 3 && ui.width(t + (cut ? "..." : ""), 10) > cw - 8) { t = t.substring(0, t.length() - 1); cut = true; }
            ui.text(cut ? t + "..." : t, cx, y + 52, 10, Style.DIM);
            if (ui.clicked(cx, y, cw, h)) { tab = i == 0 ? 0 : i == 1 ? 1 : 2; garment = Math.max(0, i - 2); click(); }
        }
    }

    private void suggestName() {
        String[] pool = look.female ? NAMES_F : NAMES_M;
        String pick = pool[(int) Math.floorMod(seed++ * 2654435761L + System.nanoTime() / 1000, (long) pool.length)];
        name.setLength(0); name.append(pick); nameWarning = null;
    }

    private void start() { done = true; if (sound != null) sound.accept("NewProfileButton"); }

    private float heading(Ui2D ui, String s, float x, float y, float w) {
        ui.text(s.toUpperCase(), x, y, 11, Style.DIM);
        ui.rect(x, y + 16, w, 1, 0x30FFFFFF);
        return y + 26;
    }

    /** Big cards side by side, one of them chosen. Returns the y below them. */
    private float cards(Ui2D ui, String[] labels, int chosen, IntConsumer pick, float x, float y, float w, float ch) {
        float gap = 8, cw = (w - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            float cx = x + i * (cw + gap);
            boolean on = i == chosen, hov = ui.hover(cx, y, cw, ch);
            ui.rect(cx, y, cw, ch, on ? SEL_BG : hov ? HOVER_BG : ROW_BG);
            ui.border(cx, y, cw, ch, on ? 2 : 1, on ? SEL : 0x22FFFFFF);
            ui.flatCentered(labels[i], cx + cw / 2, y + ch / 2 - 8, 15, on ? Style.TEXT : Style.DIM);
            if (ui.clicked(cx, y, cw, ch) && !on) { pick.accept(i); click(); }
        }
        return y + ch + 18;
    }

    /** A grid of colour swatches; the chosen one ringed. Returns the y below it. */
    private float swatches(Ui2D ui, int count, int perRow, IntSupplier get, IntConsumer set, java.util.function.IntUnaryOperator rgb, java.util.function.IntFunction<String> nameOf, float x, float y, float w) {
        float gap = 6, s = Math.min(40, (w - gap * (perRow - 1)) / perRow);
        int chosen = get.getAsInt();
        String hovered = null;
        for (int i = 0; i < count; i++) {
            float sx = x + (i % perRow) * (s + gap), sy = y + (i / perRow) * (s + gap);
            boolean on = i == chosen, hov = ui.hover(sx, sy, s, s);
            if (on) ui.rect(sx - 3, sy - 3, s + 6, s + 6, SEL);
            else if (hov) ui.rect(sx - 2, sy - 2, s + 4, s + 4, 0x80FFFFFF);
            ui.rect(sx, sy, s, s, 0xFF000000 | rgb.applyAsInt(i));
            if (hov) hovered = nameOf.apply(i);
            if (ui.clicked(sx, sy, s, s) && !on) { set.accept(i); click(); }
        }
        float below = y + ((count + perRow - 1) / perRow) * (s + gap);
        ui.text(hovered != null ? hovered : nameOf.apply(chosen), x, below + 2, 12, hovered != null ? Style.TEXT : Style.DIM);
        return below + 26;
    }

    /** A two-column list of named options; the chosen one highlighted. Returns the y below it. */
    private float list(Ui2D ui, String[] names, int count, IntSupplier get, IntConsumer set, float x, float y, float w, float rh) {
        int chosen = get.getAsInt();
        float cw = (w - 6) / 2;
        int perCol = (count + 1) / 2;
        for (int i = 0; i < count; i++) {
            float lx = x + (i / perCol) * (cw + 6), ly = y + (i % perCol) * rh;
            boolean on = i == chosen, hov = ui.hover(lx, ly, cw, rh - 2);
            ui.rect(lx, ly, cw, rh - 2, on ? SEL_BG : hov ? HOVER_BG : ROW_BG);
            if (on) ui.rect(lx, ly, 3, rh - 2, SEL);
            ui.text(names[i], lx + 10, ly + (rh - 2) / 2 - 7, 12, on ? Style.TEXT : hov ? Style.TEXT : Style.DIM);
            if (ui.clicked(lx, ly, cw, rh - 2) && !on) { set.accept(i); click(); }
        }
        return y + perCol * rh + 14;
    }

    private static final String[] PRESETS = {"Farmhand", "Soldier", "Scholar", "Wanderer", "Noble", "Smith"};
    private static final long[] PRESET_SEEDS = {11, 23, 37, 41, 53, 67};

    private float bodyTab(Ui2D ui, float x, float y, float w) {
        // whole looks to start from (the later steps change them as you like); the sex is kept
        y = heading(ui, "Start from", x, y, w);
        float bw = (w - 5 * 6) / 6;
        for (int i = 0; i < PRESETS.length; i++) {
            float bx = x + i * (bw + 6);
            boolean hov = ui.hover(bx, y, bw, 30);
            ui.rect(bx, y, bw, 30, hov ? HOVER_BG : ROW_BG);
            ui.border(bx, y, bw, 30, 1, 0x22FFFFFF);
            ui.flatCentered(PRESETS[i], bx + bw / 2, y + 8, 11, hov ? Style.TEXT : Style.DIM);
            if (ui.clicked(bx, y, bw, 30)) {
                Appearance a = Appearance.random(PRESET_SEEDS[i] * 7919);
                boolean fem = look.female;
                look.bodyType = a.bodyType; look.skin = a.skin; look.hair = a.hair; look.hairColor = a.hairColor; look.beard = fem ? 0 : a.beard; look.beardColor = a.beardColor;
                look.top = a.top; look.topColor = a.topColor; look.bottom = a.bottom; look.bottomColor = a.bottomColor; look.shoes = a.shoes; look.shoesColor = a.shoesColor;
                click();
            }
        }
        y += 46;
        y = heading(ui, "Sex", x, y, w);
        y = cards(ui, new String[]{"Male", "Female"}, look.female ? 1 : 0, v -> { look.female = v == 1; if (look.female) look.beard = 0; }, x, y, w, 56);
        y = heading(ui, "Build", x, y, w);
        y = cards(ui, Appearance.bodyNames(look.female), look.bodyType, v -> look.bodyType = v, x, y, w, 56);
        y = heading(ui, "Skin tone", x, y, w);
        return swatches(ui, Appearance.SKIN_TONES, 6, () -> look.skin, v -> look.skin = v, v -> Appearance.SKIN[v], v -> "Tone " + (v + 1) + " of 12", x, y, w);
    }

    private float hairTab(Ui2D ui, float x, float y, float w) {
        y = heading(ui, "Hairstyle", x, y, w);
        y = list(ui, look.female ? Appearance.HAIR_FEMALE : Appearance.HAIR_MALE, Appearance.HAIRSTYLES, () -> look.hair, v -> look.hair = v, x, y, w, 24);
        y = heading(ui, "Hair colour", x, y, w);
        y = swatches(ui, Appearance.HAIR_COLORS, 10, () -> look.hairColor, v -> { if (look.beardColor == look.hairColor) look.beardColor = v; look.hairColor = v; }, Appearance::hairColor, v -> Appearance.HAIR_NAMES[v], x, y, w);
        if (!look.female) {
            y = heading(ui, "Facial hair", x, y, w);
            int chosen = look.beard;
            // the facial hair cycles with arrows (twenty would not fit beside the hair): its name between them
            if (Style.button(ui, "<", x, y, 30, 28, true)) { look.beard = Math.floorMod(chosen - 1, Appearance.BEARDS); click(); }
            ui.rect(x + 36, y, w - 72, 28, ROW_BG);
            ui.flatCentered(Appearance.BEARD_NAMES[look.beard], x + w / 2, y + 7, 13, Style.TEXT);
            if (Style.button(ui, ">", x + w - 30, y, 30, 28, true)) { look.beard = Math.floorMod(chosen + 1, Appearance.BEARDS); click(); }
            y += 38;
            if (look.beard > 0) {
                ui.text("Facial hair colour", x, y, 11, Style.DIM);
                y += 18;
                y = swatches(ui, Appearance.HAIR_COLORS, 10, () -> look.beardColor, v -> look.beardColor = v, Appearance::hairColor, v -> Appearance.HAIR_NAMES[v], x, y, w);
            }
        }
        return y;
    }

    private float clothingTab(Ui2D ui, float x, float y, float w) {
        // the garment's own tabs
        float gw = (w - 12) / 3;
        for (int i = 0; i < 3; i++) {
            boolean on = garment == i, hov = ui.hover(x + i * (gw + 6), y, gw, 30);
            ui.rect(x + i * (gw + 6), y, gw, 30, on ? SEL_BG : hov ? HOVER_BG : ROW_BG);
            ui.border(x + i * (gw + 6), y, gw, 30, 1, on ? SEL : 0x22FFFFFF);
            ui.flatCentered(GARMENTS[i], x + i * (gw + 6) + gw / 2, y + 7, 14, on ? Style.TEXT : Style.DIM);
            if (ui.clicked(x + i * (gw + 6), y, gw, 30) && !on) { garment = i; click(); }
        }
        y += 44;
        switch (garment) {
            case 0 -> {
                y = heading(ui, "Style", x, y, w);
                y = list(ui, Appearance.TOP_NAMES, Appearance.TOPS, () -> look.top, v -> look.top = v, x, y, w, 24);
                y = heading(ui, "Colour", x, y, w);
                y = swatches(ui, Appearance.COLORS, 12, () -> look.topColor, v -> look.topColor = v, Appearance::color, v -> Appearance.COLOR_NAMES[v], x, y, w);
            }
            case 1 -> {
                y = heading(ui, "Style", x, y, w);
                y = list(ui, Appearance.BOTTOM_NAMES, Appearance.BOTTOMS, () -> look.bottom, v -> look.bottom = v, x, y, w, 24);
                y = heading(ui, "Colour", x, y, w);
                y = swatches(ui, Appearance.COLORS, 12, () -> look.bottomColor, v -> look.bottomColor = v, Appearance::color, v -> Appearance.COLOR_NAMES[v], x, y, w);
            }
            default -> {
                y = heading(ui, "Style", x, y, w);
                y = list(ui, Appearance.SHOE_NAMES, Appearance.SHOES, () -> look.shoes, v -> look.shoes = v, x, y, w, 24);
                y = heading(ui, "Colour", x, y, w);
                y = swatches(ui, Appearance.COLORS, 12, () -> look.shoesColor, v -> look.shoesColor = v, Appearance::color, v -> Appearance.COLOR_NAMES[v], x, y, w);
            }
        }
        return y;
    }

    private float finishTab(Ui2D ui, float x, float y, float w) {
        y = heading(ui, "Name", x, y, w);
        ui.rect(x, y, w, 36, 0x30FFFFFF);
        ui.border(x, y, w, 36, 1, SEL);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        ui.text(name + (blink ? "|" : ""), x + 10, y + 9, 17, Style.TEXT);
        y += 46;
        ui.text(nameWarning != null ? nameWarning : "Type a name (2 to 12 letters), or ask for a suggestion.", x, y, 11, nameWarning != null ? Style.BAD : Style.FAINT);
        y += 30;
        y = heading(ui, "Your character", x, y, w);
        for (Row r : rows()) {
            int v = r.get().getAsInt();
            ui.text(r.label(), x, y, 12, Style.DIM);
            float vx = x + 150;
            if (r.colour()) { ui.rect(vx, y + 1, 12, 12, 0xFF000000 | (r.label().startsWith("Hair") || r.label().startsWith("Facial") ? Appearance.hairColor(v) : Appearance.color(v))); vx += 18; }
            if (r.label().equals("Skin")) { ui.rect(vx, y + 1, 12, 12, 0xFF000000 | Appearance.SKIN[v]); vx += 18; }
            ui.text(r.text().apply(v), vx, y, 12, Style.TEXT);
            // a click on a line goes back to the tab that chooses it
            if (ui.clicked(x, y - 2, w, 18)) { tab = r.label().startsWith("Sex") || r.label().startsWith("Body") || r.label().startsWith("Skin") ? 0 : r.label().startsWith("Hair") || r.label().startsWith("Facial") ? 1 : 2; garment = r.label().startsWith("Legs") ? 1 : r.label().startsWith("Shoes") ? 2 : 0; click(); }
            y += 20;
        }
        return y;
    }
}
