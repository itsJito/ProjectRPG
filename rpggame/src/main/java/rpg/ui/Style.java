package rpg.ui;

/**
 * The interface's look (gamefixes: the menus redesigned, WoW / OSRS in mind): dark parchment panels in a gold-trimmed
 * frame with studded corners, wells sunk into them with a bevel, buttons of worn stone with gold lettering, tabs that
 * hang from the frame. The same parchment and gold as the Settings and pause screens. Every fragment draws with these.
 */
public final class Style {
    private Style() { }

    public static final int CARD = 0xF01C1813, CARD_HI = 0xF8262019, WELL = 0xC0100D0A, EDGE = 0x40C8A060, EDGE_HI = 0xC0E8C878,
            TEXT = 0xFFEDE3CC, DIM = 0xFFA89C82, FAINT = 0xFF746A58, ACCENT = 0xFFE8C26A, ACCENT_BG = 0x40D8B46A,
            GOOD = 0xFF8CC47E, BAD = 0xFFD8705E, WARN = 0xFFE09A4A, INFO = 0xFF7EA8C9, MAGIC = 0xFF8C8CE0,
            HP = 0xFFB4483E, STAMINA = 0xFFC2A848, FAITH = 0xFF4FA6A8, MANA = 0xFF5470C0;
    /** The frame's metal: a dark outer line, the gold trim, its lit edge. */
    public static final int RIM = 0xFF0C0A07, GOLD = 0xFF9C7838, GOLD_LIT = 0xFFE0BC6C;
    /** NPCs on the minimap and over their heads keep the colours they have always had. */
    public static final int NPC = 0xFFFFFF00, NPC_HOSTILE = 0xFFFF5050, MONSTER = 0xFFB01818;
    public static final float BODY = 13, SMALL = 11, TITLE = 15;

    /** A panel: parchment darkened to leather, a gold trim inside a dark rim, a stud at each corner. */
    public static void card(Ui2D ui, float x, float y, float w, float h) {
        ui.rectTex(ui.parchment, x, y, w, h, 0, 0, w / 128, h / 128, 0xFF8A7A66);
        ui.rect(x, y, w, h, 0x8C140F0A);
        ui.rect(x, y, w, Math.min(40, h), 0x14FFE0A0);                       // the light catches the top
        trim(ui, x, y, w, h);
    }

    /** The gold-on-dark frame round a panel, with corner studs. */
    public static void trim(Ui2D ui, float x, float y, float w, float h) {
        ui.border(x, y, w, h, 1, RIM);
        ui.border(x + 1, y + 1, w - 2, h - 2, 1, GOLD);
        ui.rect(x + 2, y + 2, w - 4, 1, 0x60E0BC6C);
        ui.border(x + 3, y + 3, w - 6, h - 6, 1, 0x50000000);
        for (float[] c : new float[][]{{x, y}, {x + w - 7, y}, {x, y + h - 7}, {x + w - 7, y + h - 7}}) {
            ui.rect(c[0], c[1], 7, 7, RIM);
            ui.rect(c[0] + 1, c[1] + 1, 5, 5, GOLD);
            ui.rect(c[0] + 2, c[1] + 2, 2, 2, GOLD_LIT);
        }
    }

    /** A sunken well (a slot, a grid): darker, shadowed on its top and left, a lit lip on the bottom and right. */
    public static void well(Ui2D ui, float x, float y, float w, float h) {
        ui.rect(x, y, w, h, WELL);
        ui.rect(x, y, w, 1, 0x90000000);
        ui.rect(x, y, 1, h, 0x90000000);
        ui.rect(x, y + h - 1, w, 1, 0x30E8C878);
        ui.rect(x + w - 1, y, 1, h, 0x30E8C878);
    }

    /** A gold-ruled heading underline. */
    public static void rule(Ui2D ui, float x, float y, float w) {
        ui.rect(x, y, w, 1, 0x80B8954C);
        ui.rect(x, y + 1, w, 1, 0x60000000);
    }

    /** A slim bar with its fill, in a dark trough; the share is clamped. */
    public static void bar(Ui2D ui, float x, float y, float w, float h, float share, int color) {
        ui.rect(x, y, w, h, 0x80000000);
        float s = Math.max(0, Math.min(1, share));
        ui.rect(x, y, w * s, h, color);
        if (h >= 3) ui.rect(x, y, w * s, 1, 0x40FFFFFF);
    }

    /** A stone button with gold lettering (the Settings screen's): it lightens on hover. True when clicked. */
    public static boolean button(Ui2D ui, String label, float x, float y, float w, float h, boolean enabled) {
        boolean hv = enabled && ui.hover(x, y, w, h);
        ui.rect(x, y, w, h, enabled ? (hv ? 0xFF6A5A44 : 0xFF4A3F33) : 0xFF302B25);
        ui.border(x, y, w, h, 1, hv ? GOLD : 0xFF1E1A15);
        ui.rect(x + 1, y + 1, w - 2, 1, 0x40FFFFFF);
        ui.rect(x + 1, y + h - 2, w - 2, 1, 0x40000000);
        float ts = Math.min(BODY, h - 7);
        ui.flatCentered(label, x + w / 2, y + (h - ts * 1.15f) / 2, ts, enabled ? (hv ? 0xFFFFD15E : 0xFFFF981F) : FAINT);
        return enabled && ui.clicked(x, y, w, h);
    }

    /** A tab hanging from the frame: raised and gold-edged when on, a dim strip when not. */
    public static boolean tab(Ui2D ui, String label, float x, float y, float w, float h, boolean on) {
        boolean hv = ui.hover(x, y, w, h);
        ui.rect(x, y, w, h, on ? 0xFF3A3024 : hv ? 0xFF2C251C : 0xFF211C16);
        ui.border(x, y, w, h, 1, on ? GOLD : 0xFF0E0B08);
        if (on) { ui.rect(x + 1, y + 1, w - 2, 1, 0x80E0BC6C); ui.rect(x + 1, y + h - 2, w - 2, 2, ACCENT); }
        ui.flatCentered(label, x + w / 2, y + (h - BODY * 1.15f) / 2, BODY, on ? TEXT : hv ? 0xFFD8CCB0 : DIM);
        return ui.clicked(x, y, w, h);
    }

    public static int fade(int argb, float a) { return ((int) (((argb >>> 24) & 255) * Math.max(0, Math.min(1, a)))) << 24 | (argb & 0xFFFFFF); }
}
