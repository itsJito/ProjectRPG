package rpg.ui;

/**
 * The interface's look (gamefixes, UI): flat and low-contrast. Translucent slate cards with a hairline edge, muted text,
 * one warm accent, no bevels or stone, and nothing decorative. Every fragment draws with these.
 */
public final class Style {
    private Style() { }

    public static final int CARD = 0xC8181A1E, CARD_HI = 0xE01E2126, WELL = 0x60000000, EDGE = 0x22FFFFFF, EDGE_HI = 0x50FFFFFF,
            TEXT = 0xFFE4E0D6, DIM = 0xFF9A968C, FAINT = 0xFF6A675F, ACCENT = 0xFFD8B46A, ACCENT_BG = 0x40D8B46A,
            GOOD = 0xFF86B97E, BAD = 0xFFD06A5E, INFO = 0xFF7EA8C9, MAGIC = 0xFF8C8CE0,
            HP = 0xFFB4483E, STAMINA = 0xFFC2A848, FAITH = 0xFF4FA6A8, MANA = 0xFF5470C0;
    /** NPCs on the minimap and over their heads keep the colours they have always had. */
    public static final int NPC = 0xFFFFFF00, NPC_HOSTILE = 0xFFFF5050;
    public static final float BODY = 13, SMALL = 11, TITLE = 15;

    public static void card(Ui2D ui, float x, float y, float w, float h) {
        ui.rect(x, y, w, h, CARD);
        ui.border(x, y, w, h, 1, EDGE);
    }

    public static void well(Ui2D ui, float x, float y, float w, float h) { ui.rect(x, y, w, h, WELL); }

    /** A slim bar with its fill; the share is clamped. */
    public static void bar(Ui2D ui, float x, float y, float w, float h, float share, int color) {
        ui.rect(x, y, w, h, 0x50000000);
        ui.rect(x, y, w * Math.max(0, Math.min(1, share)), h, color);
    }

    /** A flat button: text on a faint tile that lights on hover. True when clicked. */
    public static boolean button(Ui2D ui, String label, float x, float y, float w, float h, boolean enabled) {
        boolean hv = enabled && ui.hover(x, y, w, h);
        ui.rect(x, y, w, h, hv ? 0x30FFFFFF : 0x14FFFFFF);
        ui.border(x, y, w, h, 1, hv ? EDGE_HI : EDGE);
        float ts = Math.min(BODY, h - 7);
        ui.flatCentered(label, x + w / 2, y + (h - ts * 1.15f) / 2, ts, enabled ? (hv ? TEXT : DIM) : FAINT);
        return enabled && ui.clicked(x, y, w, h);
    }

    /** A tab: underlined in the accent when on. */
    public static boolean tab(Ui2D ui, String label, float x, float y, float w, float h, boolean on) {
        boolean hv = ui.hover(x, y, w, h);
        if (on || hv) ui.rect(x, y, w, h, on ? 0x18FFFFFF : 0x0CFFFFFF);
        if (on) ui.rect(x, y + h - 2, w, 2, ACCENT);
        ui.flatCentered(label, x + w / 2, y + (h - BODY * 1.15f) / 2, BODY, on ? TEXT : DIM);
        return ui.clicked(x, y, w, h);
    }

    public static int fade(int argb, float a) { return ((int) (((argb >>> 24) & 255) * Math.max(0, Math.min(1, a)))) << 24 | (argb & 0xFFFFFF); }
}
