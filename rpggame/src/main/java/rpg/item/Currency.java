package rpg.item;

/**
 * Gold Coins (G) as the interface shows them (gamefixes): one slot, the amount coloured and suffixed by its size.
 * White, no suffix, to 99,999; yellow K to 9,999,999; green M to 999,999,999; blue B below two billion; purple X beyond.
 */
public final class Currency {
    private Currency() { }

    public static final int WHITE = 0xFFFFFFFF, YELLOW = 0xFFFFFF00, GREEN = 0xFF00FF80, BLUE = 0xFF40A0FF, PURPLE = 0xFFC060FF;

    /** The short form for an amount: 99,999 / 150K / 12M / 1B / 2X. */
    public static String text(long n) {
        if (n < 100_000) return String.valueOf(n);
        if (n < 10_000_000) return n / 1_000 + "K";
        if (n < 1_000_000_000) return n / 1_000_000 + "M";
        if (n < 2_000_000_000) return n / 1_000_000_000 + "B";
        return n / 1_000_000_000 + "X";
    }

    public static int color(long n) {
        if (n < 100_000) return WHITE;
        if (n < 10_000_000) return YELLOW;
        if (n < 1_000_000_000) return GREEN;
        if (n < 2_000_000_000) return BLUE;
        return PURPLE;
    }

    /** The full amount with its unit, for tooltips and prices: "1,250,000 G". */
    public static String full(long n) { return String.format("%,d G", n); }
}
