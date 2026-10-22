package rpg.item;

import java.util.ArrayList;
import java.util.List;

/**
 * A spatial inventory (gamefixes, Item & Inventory System): a grid of cells in which every item occupies its own
 * footprint - a sword one cell wide and four long, a body armor three by three, a coin stack one cell - so an item's bulk
 * matters as well as its weight, and gear is arranged like a puzzle. An item may lie turned (its footprint rotated).
 * Stackable items of one kind (and one owner and provenance) merge into one stack in one place.
 *
 * <p>A character carries a base grid of 10 x 4; equipped containers (a backpack, a pouch) are grids of their own, not
 * more cells in this one; world containers and corpses are grids too, and a container may be made of several
 * compartments (a shelf of small grids). A container item (a bag) holds a grid of its own and goes with it wherever it
 * goes; it may be put into another grid only when it is empty.
 *
 * <p>Every operation either applies whole or changes nothing: {@link #canAdd} and {@link #count} are the checks, and
 * {@link #add} and {@link #remove} refuse rather than apply partly. Entries keep their index while they exist (an index
 * is the item's handle for the interface: get, set, take), and an emptied index is reused.
 */
public final class Inventory {
    public static final int STACK_CAP = Integer.MAX_VALUE;
    /** The grid's size in cells. */
    public final int w, h;
    /** Whether this grid is itself a container's (a bag, a chest, a corpse): a container item that is not empty may not go in. */
    public boolean container;
    private final ItemStack[] stacks;
    private final int[] px, py;
    private final boolean[] turned;
    private final int[] cell;   // which entry covers each cell (-1: free)

    public Inventory(int w, int h) {
        this.w = Math.max(1, w); this.h = Math.max(1, h);
        int n = this.w * this.h;
        stacks = new ItemStack[n]; px = new int[n]; py = new int[n]; turned = new boolean[n];
        cell = new int[n];
        java.util.Arrays.fill(cell, -1);
    }

    /** A grid of a number of cells (the old fixed-slot form): ten wide, as many rows as needed. */
    public Inventory(int cells) { this(Math.min(10, Math.max(1, cells)), Math.max(1, (cells + 9) / 10)); }

    // ------------------------------------------------------------------ footprints
    public static int fw(ItemDef d, boolean turn) { return turn ? d.gh : d.gw; }
    public static int fh(ItemDef d, boolean turn) { return turn ? d.gw : d.gh; }

    /** Whether an item of a definition fits with its corner at (x, y), turned or not, ignoring one entry (the one being moved). */
    public boolean fits(ItemDef d, int x, int y, boolean turn, int ignore) {
        int ww = fw(d, turn), hh = fh(d, turn);
        if (x < 0 || y < 0 || x + ww > w || y + hh > h) return false;
        for (int j = y; j < y + hh; j++) for (int i = x; i < x + ww; i++) { int c = cell[j * w + i]; if (c >= 0 && c != ignore) return false; }
        return true;
    }

    /** The first place an item fits (row by row; the turned way when the straight one does not), or null. */
    public int[] findSpace(ItemDef d, int ignore) {
        for (int pass = 0; pass < 2; pass++) {
            boolean turn = pass == 1;
            if (turn && d.gw == d.gh) break;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (fits(d, x, y, turn, ignore)) return new int[]{x, y, turn ? 1 : 0};
        }
        return null;
    }

    private void mark(int e, int v) {
        ItemDef d = stacks[e].def;
        int ww = fw(d, turned[e]), hh = fh(d, turned[e]);
        for (int j = py[e]; j < py[e] + hh; j++) for (int i = px[e]; i < px[e] + ww; i++) cell[j * w + i] = v;
    }

    private int freeEntry() { for (int i = 0; i < stacks.length; i++) if (stacks[i] == null) return i; return -1; }

    private boolean placeNew(ItemStack s, int x, int y, boolean turn) {
        int e = freeEntry();
        if (e < 0 || !fits(s.def, x, y, turn, -1)) return false;
        stacks[e] = s; px[e] = x; py[e] = y; turned[e] = turn;
        mark(e, e);
        return true;
    }

    // ------------------------------------------------------------------ the old slot-shaped interface (entries)
    /** The number of entries (an upper bound on items: one a cell). */
    public int size() { return stacks.length; }
    public ItemStack get(int i) { return i >= 0 && i < stacks.length ? stacks[i] : null; }
    public int x(int i) { return px[i]; }
    public int y(int i) { return py[i]; }
    public boolean turned(int i) { return turned[i]; }
    /** The entry covering a cell, or -1. */
    public int at(int x, int y) { return x < 0 || y < 0 || x >= w || y >= h ? -1 : cell[y * w + x]; }

    /** Sets an entry: null removes it; another stack replaces it where it lies (or goes wherever it fits). False when it does not fit. */
    public boolean set(int i, ItemStack s) {
        if (i < 0 || i >= stacks.length) return false;
        if (stacks[i] != null) {
            ItemStack old = stacks[i];
            mark(i, -1);
            stacks[i] = null;
            if (s == null) return true;
            if (fits(s.def, px[i], py[i], turned[i], -1)) { stacks[i] = s; mark(i, i); return true; }
            int[] sp = findSpace(s.def, -1);
            if (sp == null) { stacks[i] = old; mark(i, i); return false; }
            stacks[i] = s; px[i] = sp[0]; py[i] = sp[1]; turned[i] = sp[2] == 1; mark(i, i);
            return true;
        }
        if (s == null) return true;
        int[] sp = findSpace(s.def, -1);
        if (sp == null) return false;
        stacks[i] = s; px[i] = sp[0]; py[i] = sp[1]; turned[i] = sp[2] == 1; mark(i, i);
        return true;
    }

    /** Moves an entry to (x, y), turned or not; false (and nothing moved) when it does not fit there. */
    public boolean move(int i, int x, int y, boolean turn) {
        ItemStack s = get(i);
        if (s == null || !fits(s.def, x, y, turn, i)) return false;
        mark(i, -1);
        px[i] = x; py[i] = y; turned[i] = turn;
        mark(i, i);
        return true;
    }

    /** Puts a stack at a place (merging into a stack of the same kind there); false when it does not fit. */
    public boolean putAt(ItemStack s, int x, int y, boolean turn) {
        if (!accepts(s)) return false;
        int there = at(x, y);
        if (there >= 0 && s.def.stackable && stacks[there].sameKind(s)) { if ((long) stacks[there].amount + s.amount > STACK_CAP) return false; stacks[there].absorb(s); return true; }
        return placeNew(s.copyWhole(), x, y, turn);
    }

    /** Free cells (room: an item needs its footprint of them, in the right shape). */
    public int freeSlots() { int n = 0; for (int c : cell) if (c < 0) n++; return n; }
    public boolean isEmpty() { for (ItemStack s : stacks) if (s != null) return false; return true; }

    // ------------------------------------------------------------------ adding and removing
    /** Whether a stack may go in at all (a container item that is not empty may not go into a container). */
    public boolean accepts(ItemStack s) { return s != null && s.amount > 0 && !(container && s.inner != null && !s.inner.isEmpty()); }

    /** Whether all of the stack fits (into a matching stack, or into free space shaped for it; one place a unit when it does not stack). */
    public boolean canAdd(ItemStack s) {
        if (!accepts(s)) return false;
        if (s.def.stackable) {
            for (ItemStack t : stacks) if (t != null && t.sameKind(s)) return (long) t.amount + s.amount <= STACK_CAP;
            return findSpace(s.def, -1) != null;
        }
        if (s.amount == 1) return findSpace(s.def, -1) != null;
        return copy().add(s);
    }

    /** Whether several stacks fit together. */
    public boolean canAddAll(List<ItemStack> list) {
        Inventory probe = copy();
        for (ItemStack s : list) if (!probe.add(s)) return false;
        return true;
    }

    /** Adds all of it or nothing; true when added. The stack is copied in (a bag's contents go with it). */
    public boolean add(ItemStack s) {
        if (!accepts(s)) return false;
        if (s.def.stackable) {
            for (ItemStack t : stacks) if (t != null && t.sameKind(s)) { if ((long) t.amount + s.amount > STACK_CAP) return false; t.absorb(s); return true; }
            int[] sp = findSpace(s.def, -1);
            return sp != null && placeNew(s.copyWhole(), sp[0], sp[1], sp[2] == 1);
        }
        // one place a unit: all of them or none
        if (s.amount == 1) { int[] sp = findSpace(s.def, -1); return sp != null && placeNew(s.copyWhole(), sp[0], sp[1], sp[2] == 1); }
        if (s.amount > 1) {
            Inventory probe = copy();
            for (int k = 0; k < s.amount; k++) { int[] sp = probe.findSpace(s.def, -1); if (sp == null || !probe.placeNew(s.copy(1), sp[0], sp[1], sp[2] == 1)) return false; }
        }
        for (int k = 0; k < s.amount; k++) {
            int[] sp = findSpace(s.def, -1);
            placeNew(k == 0 && s.amount == 1 ? s.copyWhole() : s.copy(1), sp[0], sp[1], sp[2] == 1);
        }
        return true;
    }

    public int count(ItemDef d) { int n = 0; for (ItemStack t : stacks) if (t != null && t.def == d) n += t.amount; return n; }
    public boolean has(ItemDef d, int n) { return count(d) >= n; }
    public int first(ItemDef d) { for (int i = 0; i < stacks.length; i++) if (stacks[i] != null && stacks[i].def == d) return i; return -1; }
    public boolean anyStolen() { for (ItemStack t : stacks) if (t != null && t.stolen) return true; return false; }

    /** Removes n of a definition (clean stacks last), returning what was taken merged by kind - or null, with nothing taken, when there are fewer. */
    public List<ItemStack> remove(ItemDef d, int n) {
        if (count(d) < n) return null;
        List<ItemStack> out = new ArrayList<>();
        int left = n;
        for (int i = stacks.length - 1; i >= 0 && left > 0; i--) {
            ItemStack t = stacks[i];
            if (t == null || t.def != d) continue;
            int take = Math.min(left, t.amount);
            merge(out, t.copy(take));
            t.amount -= take;
            if (t.amount == 0) set(i, null);
            left -= take;
        }
        return out;
    }

    /** Takes an entry's stack, or part of it. */
    public ItemStack take(int i, int n) {
        ItemStack t = get(i);
        if (t == null || n <= 0) return null;
        n = Math.min(n, t.amount);
        ItemStack out = n == t.amount ? t.copyWhole() : t.copy(n);
        t.amount -= n;
        if (t.amount == 0) set(i, null);
        return out;
    }

    public void swap(int a, int b) {
        ItemStack sa = get(a), sb = get(b);
        if (sa == null || sb == null) return;
        int ax = px[a], ay = py[a], bx = px[b], by = py[b];
        boolean at = turned[a], bt = turned[b];
        mark(a, -1); mark(b, -1);
        stacks[a] = sb; stacks[b] = sa;
        if (fits(sb.def, ax, ay, at, -1)) { px[a] = ax; py[a] = ay; turned[a] = at; mark(a, a); }
        else { stacks[a] = sa; stacks[b] = sb; mark(a, a); mark(b, b); return; }
        if (fits(sa.def, bx, by, bt, -1)) { px[b] = bx; py[b] = by; turned[b] = bt; mark(b, b); }
        else { mark(a, -1); stacks[a] = sa; stacks[b] = sb; px[a] = ax; py[a] = ay; turned[a] = at; px[b] = bx; py[b] = by; turned[b] = bt; mark(a, a); mark(b, b); }
    }

    public void clear() { java.util.Arrays.fill(stacks, null); java.util.Arrays.fill(cell, -1); }

    public List<ItemStack> stacks() { List<ItemStack> l = new ArrayList<>(); for (ItemStack s : stacks) if (s != null) l.add(s); return l; }

    /** The total weight (kg) of what is in the grid, bags' contents included. */
    public float weight() { float kg = 0; for (ItemStack s : stacks) if (s != null) kg += s.weight(); return kg; }

    /** Packs everything again from the top left, largest first (the interface's "sort"); false (and unchanged) if it would not fit. */
    public boolean pack() {
        Inventory probe = new Inventory(w, h);
        probe.container = container;
        List<ItemStack> l = stacks();
        l.sort((a, b) -> Integer.compare(b.def.gw * b.def.gh, a.def.gw * a.def.gh));
        for (ItemStack s : l) { int[] sp = probe.findSpace(s.def, -1); if (sp == null || !probe.placeNew(s, sp[0], sp[1], sp[2] == 1)) return false; }
        clear();
        System.arraycopy(probe.stacks, 0, stacks, 0, stacks.length);
        System.arraycopy(probe.px, 0, px, 0, px.length); System.arraycopy(probe.py, 0, py, 0, py.length);
        System.arraycopy(probe.turned, 0, turned, 0, turned.length); System.arraycopy(probe.cell, 0, cell, 0, cell.length);
        return true;
    }

    public Inventory copy() {
        Inventory c = new Inventory(w, h);
        c.container = container;
        for (int i = 0; i < stacks.length; i++) if (stacks[i] != null) { c.stacks[i] = stacks[i].copyWhole(); c.px[i] = px[i]; c.py[i] = py[i]; c.turned[i] = turned[i]; }
        System.arraycopy(cell, 0, c.cell, 0, cell.length);
        return c;
    }

    private static void merge(List<ItemStack> out, ItemStack s) {
        for (ItemStack o : out) if (o.sameKind(s)) { o.absorb(s); return; }
        out.add(s);
    }
}
