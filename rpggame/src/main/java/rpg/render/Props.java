package rpg.render;

import java.util.Random;
import rpg.objects.Furniture;

/**
 * The models of the devmap's objects - furniture, workstations, door leaves - built from coloured boxes, in metres, with
 * the footprint's middle on the floor at the origin and the front toward -z. Each model is a few layers, one per
 * material (wood, metal, something glowing), drawn untinted.
 */
public final class Props {
    private Props() {}

    /** One material's boxes. */
    public record Layer(Meshes.Boxes boxes, float rough, float metal, float emissive) {}

    private static Layer wood(Meshes.Boxes b) { return new Layer(b, 0.82f, 0, 0); }
    private static Layer metal(Meshes.Boxes b) { return new Layer(b, 0.38f, 0.75f, 0); }
    private static Layer glow(Meshes.Boxes b, float e) { return new Layer(b, 0.9f, 0, e); }
    private static Layer stone(Meshes.Boxes b) { return new Layer(b, 0.92f, 0, 0); }

    // ------------------------------------------------------------------ dungeons
    /**
     * A dungeon's mouth (gamefixes): a heap of its theme's rock with a black way down in it, the front at -z. A warren's
     * is propped with crooked timbers, a cave's hung with old web, a crypt's a dressed arch with a lintel, the troll's
     * hall a great split boulder; steps go down into the dark in every one.
     */
    public static Layer[] dungeonEntrance(int theme) {
        Meshes.Boxes rock = new Meshes.Boxes(), wood = new Meshes.Boxes(), dark = new Meshes.Boxes();
        int stone = switch (theme) { case 1 -> 0x6E5A44; case 2 -> 0x8A8A86; case 3 -> 0x6E7262; default -> 0x7A7A74; };
        Random r = new Random(theme * 31L + 7);
        // the mound round the mouth: a low hill of rock, broad at its foot, turf over its top and grass in its cracks
        Meshes.Boxes turf = new Meshes.Boxes();
        for (int i = 0; i < 40; i++) {
            float a = (float) (Math.PI * (0.02 + 0.96 * r.nextFloat())), rad = 1.3f + r.nextFloat() * 0.9f;
            float x = (float) Math.cos(a) * rad, z = 0.5f + (float) Math.sin(a) * rad * 0.8f, s = 0.55f + r.nextFloat() * 0.6f;
            float lift = Math.max(0, 1.6f - rad * 0.55f) + r.nextFloat() * 0.35f;
            rock.rgb(x, s / 2 + lift * 0.6f, z, s, s, s, shadeRgb(stone, 0.8f + r.nextFloat() * 0.3f));
            if (r.nextFloat() < 0.45f) turf.rgb(x, s + lift * 0.6f + 0.04f, z, s * 0.9f, 0.08f, s * 0.9f, shadeRgb(0x5E8A34, 0.85f + r.nextFloat() * 0.25f));
        }
        rock.rgb(0, 1.6f, 0.7f, 2.6f, 0.6f, 1.6f, shadeRgb(stone, 0.9f));
        turf.rgb(0, 1.93f, 0.7f, 2.5f, 0.08f, 1.5f, 0x5A8432);
        for (int i = 0; i < 7; i++) turf.rgb(-1.1f + i * 0.36f, 2.05f, 0.2f + (i % 3) * 0.4f, 0.05f, 0.18f, 0.05f, 0x6E9A3E);
        // the way down: black within, steps descending into it
        dark.rgb(0, 0.7f, 0.25f, 1.3f, 1.4f, 0.9f, 0x050404);
        for (int i = 0; i < 4; i++) rock.rgb(0, 0.04f + (3 - i) * 0.0f, -0.35f + i * 0.22f, 1.2f, 0.08f + (3 - i) * 0.06f, 0.22f, shadeRgb(stone, 0.7f - i * 0.1f));
        switch (theme) {
            case 1 -> { // the warren: crooked props and a lintel log
                wood.rgb(-0.72f, 0.75f, -0.2f, 0.16f, 1.5f, 0.16f, 0x5A3E22); wood.rgb(0.7f, 0.72f, -0.2f, 0.16f, 1.45f, 0.16f, 0x4E361E);
                wood.rgb(0, 1.48f, -0.2f, 1.7f, 0.18f, 0.18f, 0x5E4224);
                wood.rgb(0.95f, 1.2f, -0.35f, 0.08f, 0.6f, 0.08f, 0x6A4A2A); wood.rgb(0.95f, 1.55f, -0.35f, 0.22f, 0.18f, 0.18f, 0xE6DEC8);   // a skull on a stake
            }
            case 2 -> { // the crypt: a dressed arch
                for (int s = -1; s <= 1; s += 2) rock.rgb(s * 0.75f, 0.8f, -0.25f, 0.3f, 1.6f, 0.3f, 0xA8A8A0);
                rock.rgb(0, 1.68f, -0.25f, 1.9f, 0.24f, 0.36f, 0xB4B4AC); rock.rgb(0, 1.86f, -0.25f, 0.5f, 0.14f, 0.3f, 0x9A9A92);
            }
            case 0 -> { // the cave: old web across the corners of the mouth
                for (int i = 0; i < 6; i++) wood.rgb(-0.55f + i * 0.04f, 1.25f - i * 0.12f, -0.3f, 0.5f - i * 0.06f, 0.015f, 0.015f, 0xD8D8D0);
                for (int i = 0; i < 5; i++) wood.rgb(0.5f - i * 0.04f, 1.2f - i * 0.12f, -0.3f, 0.45f - i * 0.06f, 0.015f, 0.015f, 0xD0D0C8);
            }
            default -> { // the hall: a boulder split by the passage
                rock.rgb(-1.0f, 1.0f, 0.2f, 0.9f, 2.0f, 1.3f, shadeRgb(stone, 0.95f)); rock.rgb(1.0f, 1.1f, 0.2f, 0.9f, 2.2f, 1.3f, shadeRgb(stone, 0.88f));
            }
        }
        return new Layer[]{stone(rock), wood(wood), new Layer(dark, 1, 0, 0), new Layer(turf, 0.95f, 0, 0)};
    }

    /** A wall torch in an iron sconce: the bracket against the wall (at +z), the shaft, the pitch-soaked head. */
    public static Layer[] torch() {
        Meshes.Boxes iron = new Meshes.Boxes(), wood = new Meshes.Boxes(), head = new Meshes.Boxes();
        iron.rgb(0, 0, 0.05f, 0.16f, 0.22f, 0.04f, 0x2E2E32);
        iron.rgb(0, 0.06f, -0.04f, 0.06f, 0.04f, 0.16f, 0x3A3A40);
        iron.rgb(0, 0.1f, -0.1f, 0.1f, 0.03f, 0.1f, 0x3A3A40);
        wood.rgb(0, 0.12f, -0.1f, 0.05f, 0.42f, 0.05f, 0x5A3E22);
        head.rgb(0, 0.33f, -0.1f, 0.08f, 0.1f, 0.08f, 0x2A1A10);
        head.rgb(0, 0.38f, -0.1f, 0.06f, 0.04f, 0.06f, 0xF0A030);
        return new Layer[]{metal(iron), wood(wood), glow(head, 1.2f)};
    }

    /** The way out from inside a dungeon: worn steps up to a ladder, and the grey of daylight falling down the shaft. */
    public static Layer[] dungeonExit() {
        Meshes.Boxes rock = new Meshes.Boxes(), wood = new Meshes.Boxes(), light = new Meshes.Boxes();
        for (int i = 0; i < 3; i++) rock.rgb(0, 0.08f + i * 0.16f, 0.2f - i * 0.25f, 1.1f, 0.16f + i * 0.16f, 0.26f, shadeRgb(0x7A7A74, 0.9f - i * 0.05f));
        for (int s = -1; s <= 1; s += 2) wood.rgb(s * 0.32f, 2.2f, -0.55f, 0.07f, 3.4f, 0.07f, 0x6A4A2A);
        for (int i = 0; i < 9; i++) wood.rgb(0, 0.9f + i * 0.34f, -0.55f, 0.6f, 0.05f, 0.05f, 0x7E5C3A);
        light.rgb(0, 2.4f, -0.4f, 0.9f, 4.4f, 0.9f, 0xB0B8C0);
        return new Layer[]{stone(rock), wood(wood), glow(light, 0.35f)};
    }

    /**
     * A trap in the floor (gamefixes: traps were invisible until found). Every plate shows to a careful eye: a square
     * set proud of the floor in darker, newer stone, its seam cut clean, a ring of holes for the spikes or, under a
     * loose stone, rubble and a crack in the floor beneath the bad roof. Found, it is marked as an adventurer marks one
     * - a scratched red cross on the plate and chalk round it; disarmed, it is wedged with a wooden peg.
     * state: 0 hidden, 1 found, 2 disarmed; kind: 0 spikes, 1 falling rock.
     */
    public static Layer[] trapPlate(int state, int kind) {
        Meshes.Boxes b = new Meshes.Boxes(), dark = new Meshes.Boxes(), mark = new Meshes.Boxes(), wood = new Meshes.Boxes();
        if (kind == 2) {
            // a hunter's snare: a loop of rope laid flat in the leaves, its tail run to a peg driven in beside it; found,
            // a strip of red cloth tied to the peg; cut, the loop lies open
            Meshes.Boxes rope = new Meshes.Boxes(), leaf = new Meshes.Boxes();
            int n = 12;
            for (int i = 0; i < n; i++) {
                if (state == 2 && i < 2) continue;
                double a = i * Math.PI * 2 / n;
                rope.rgb((float) Math.cos(a) * 0.22f, 0.012f, (float) Math.sin(a) * 0.22f, 0.07f, 0.02f, 0.07f, 0xB09A6A);
            }
            for (int i = 0; i < 4; i++) rope.rgb(0.26f + i * 0.06f, 0.014f, 0, 0.07f, 0.02f, 0.025f, 0xA89062);
            wood.rgb(0.52f, 0.1f, 0, 0.05f, 0.22f, 0.05f, 0x5A3E22);
            java.util.Random r = new java.util.Random(7);
            for (int i = 0; i < 9; i++) leaf.rgb((r.nextFloat() - 0.5f) * 0.6f, 0.02f, (r.nextFloat() - 0.5f) * 0.6f, 0.09f, 0.01f, 0.06f, shadeRgb(0x6A5A2A, 0.8f + r.nextFloat() * 0.4f));
            if (state >= 1) mark.rgb(0.52f, 0.2f, 0.035f, 0.08f, 0.05f, 0.01f, 0xC02A1E);
            return new Layer[]{wood(rope), wood(wood), new Layer(leaf, 0.95f, 0, 0), glow(mark, 0.25f)};
        }
        boolean wedged = state == 2;
        dark.rgb(0, 0.006f, 0, 0.7f, 0.012f, 0.7f, 0x1E1C1A);                                  // the clean-cut seam round it
        b.rgb(0, 0.02f, 0, 0.6f, 0.03f, 0.6f, wedged ? 0x5A5A56 : 0x6A665E);                     // the plate, set proud
        b.rgb(0, 0.04f, 0, 0.46f, 0.012f, 0.46f, wedged ? 0x4A4A46 : 0x777268);
        if (kind == 0) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) dark.rgb(x * 0.15f, 0.047f, z * 0.15f, 0.05f, 0.006f, 0.05f, 0x121110);   // the spike holes
        else {
            dark.rgb(0.08f, 0.047f, -0.05f, 0.34f, 0.006f, 0.025f, 0x141312); dark.rgb(-0.1f, 0.047f, 0.08f, 0.025f, 0.006f, 0.28f, 0x141312);   // cracked
            for (int i = 0; i < 5; i++) b.rgb(-0.42f + i * 0.2f, 0.05f, (i % 2 == 0 ? 0.42f : -0.4f), 0.09f, 0.08f, 0.08f, 0x6E6A62);        // fallen grit
        }
        if (state >= 1) {
            // found: a red cross scratched across it, chalk at its corners
            for (int s2 = -1; s2 <= 1; s2 += 2) for (int i = -3; i <= 3; i++) mark.rgb(i * 0.06f, 0.05f, s2 * i * 0.06f, 0.06f, 0.006f, 0.035f, 0xB02A1E);
            for (int cx = -1; cx <= 1; cx += 2) for (int cz = -1; cz <= 1; cz += 2) { mark.rgb(cx * 0.4f, 0.014f, cz * 0.34f, 0.16f, 0.006f, 0.03f, 0xE8E4D8); mark.rgb(cx * 0.34f, 0.014f, cz * 0.4f, 0.03f, 0.006f, 0.16f, 0xE8E4D8); }
        }
        if (wedged) { wood.rgb(0.2f, 0.06f, 0.2f, 0.18f, 0.06f, 0.07f, 0x6A4A2A); wood.rgb(-0.18f, 0.06f, -0.2f, 0.07f, 0.06f, 0.18f, 0x5E4224); }
        return new Layer[]{stone(b), new Layer(dark, 1, 0, 0), state >= 1 ? glow(mark, 0.25f) : stone(mark), wood(wood)};
    }
    /** The spikes that come up through a sprung plate. */
    public static Layer[] trapSpikes() {
        Meshes.Boxes b = new Meshes.Boxes();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if ((x + z) % 2 == 0) { b.rgb(x * 0.11f, 0.25f, z * 0.11f, 0.035f, 0.5f, 0.035f, 0x8A8C92); b.rgb(x * 0.11f, 0.52f, z * 0.11f, 0.02f, 0.06f, 0.02f, 0xB0B2B8); }
        return new Layer[]{metal(b)};
    }
    /** The rock that falls from a cracked roof. */
    public static Layer[] trapRocks() {
        Meshes.Boxes b = new Meshes.Boxes();
        Random r = new Random(11);
        for (int i = 0; i < 5; i++) { float s = 0.18f + r.nextFloat() * 0.2f; b.rgb((r.nextFloat() - 0.5f) * 0.5f, r.nextFloat() * 0.3f, (r.nextFloat() - 0.5f) * 0.5f, s, s, s, shadeRgb(0x7A7A74, 0.8f + r.nextFloat() * 0.3f)); }
        return new Layer[]{stone(b)};
    }

    /** What stands beside a fence cell: 3 more fence (or bars), 2 a wall it butts against, 0 nothing. */
    public interface FenceSides { int at(int x, int y, int z); }
    /** How charred a fence cell is, 0 .. 1. */
    public interface FenceChar { float at(int x, int y, int z); }

    /**
     * The fences of one 16 x 16 column of the world as a model (gamefixes: fences were plain blocks). A fence cell is a
     * squared post with a cap, and split rails - two weathered boards - out to each neighbour that is fence or wall;
     * a lone cell is a post. Fence stacked on fence (the jail fronts) is iron bars instead: square uprights a hand
     * apart, banded top and bottom, joined to the bars beside them. Coordinates are from the column's corner.
     */
    public static Layer[] fences(int x0, int z0, int y0, int y1, FenceSides sides) { return fences(x0, z0, y0, y1, sides, (x, y, z) -> 0); }
    /** The same with the fire's work on it: each cell's wood darkened toward char by how much of it has burnt. */
    public static Layer[] fences(int x0, int z0, int y0, int y1, FenceSides sides, FenceChar burnt) {
        Meshes.Boxes wood = new Meshes.Boxes(), iron = new Meshes.Boxes();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) for (int y = y0; y <= y1; y++) {
            if (sides.at(x, y, z) != 3) continue;
            float cx = x - x0 + 0.5f, cz = z - z0 + 0.5f, by = y - y0;
            // bars where fence is stacked on fence - and the odd cell beside such bars (over a cell's door) joins them
            boolean bars = stacked(sides, x, y, z) || stacked(sides, x + 1, y, z) || stacked(sides, x - 1, y, z) || stacked(sides, x, y, z + 1) || stacked(sides, x, y, z - 1);
            long h = (x * 73856093L) ^ (z * 19349663L) ^ (y * 83492791L);
            float k = 0.92f + (h >>> 8 & 15) / 15f * 0.16f;
            if (bars) {
                // iron bars: the uprights along whichever way the bars run, the bands at the cell's top and bottom
                boolean alongX = sides.at(x + 1, y, z) == 3 || sides.at(x - 1, y, z) == 3 || !(sides.at(x, y, z + 1) == 3 || sides.at(x, y, z - 1) == 3);
                for (int i = 0; i < 4; i++) {
                    float o = -0.375f + i * 0.25f;
                    iron.rgb(alongX ? cx + o : cx, by + 0.5f, alongX ? cz : cz + o, 0.055f, 1, 0.055f, shadeRgb(0x4A4C52, k));
                }
                boolean top = sides.at(x, y + 1, z) != 3, bottom = sides.at(x, y - 1, z) != 3 && sides.at(x, y - 1, z) != 2;
                if (top) iron.rgb(cx, by + 0.94f, cz, alongX ? 1 : 0.09f, 0.08f, alongX ? 0.09f : 1, 0x3A3C42);
                if (bottom) iron.rgb(cx, by + 0.06f, cz, alongX ? 1 : 0.09f, 0.08f, alongX ? 0.09f : 1, 0x3A3C42);
                iron.rgb(cx, by + 0.5f, cz, alongX ? 1 : 0.07f, 0.05f, alongX ? 0.07f : 1, 0x44464C);
                continue;
            }
            // the post, a little sunk at its foot; its cap (charred as far as the fire has got: the top first, as flames climb)
            float ch = burnt.at(x, y, z);
            wood.rgb(cx, by + 0.5f, cz, 0.2f, 1.0f, 0.2f, charred(shadeRgb(0x6A4A2E, k), ch));
            wood.rgb(cx, by + 1.02f, cz, 0.25f, 0.06f, 0.25f, charred(shadeRgb(0x7E5C3A, k), Math.min(1, ch * 1.4f)));
            wood.rgb(cx, by + 0.04f, cz, 0.26f, 0.08f, 0.26f, charred(shadeRgb(0x4A3420, k), ch * 0.7f));
            // rails toward every neighbour that is fence, or a wall to butt against
            for (int[] d : dirs) {
                if (sides.at(x + d[0], y, z + d[1]) == 0) continue;
                for (int r = 0; r < 2; r++) {
                    float ry = by + (r == 0 ? 0.36f : 0.76f), sag = (h >>> (12 + r * 3) & 3) * 0.008f;
                    int board = charred(shadeRgb(r == 0 ? 0x8C6640 : 0x96704A, k * (0.96f + r * 0.05f)), Math.min(1, ch * (r == 0 ? 0.9f : 1.25f)));
                    float mx = cx + d[0] * 0.25f, mz = cz + d[1] * 0.25f;
                    wood.rgb(mx, ry - sag, mz, d[0] != 0 ? 0.5f : 0.06f, 0.12f, d[1] != 0 ? 0.5f : 0.06f, board);
                }
            }
        }
        return new Layer[]{wood(wood), metal(iron)};
    }
    private static boolean stacked(FenceSides sides, int x, int y, int z) { return sides.at(x, y, z) == 3 && (sides.at(x, y + 1, z) == 3 || sides.at(x, y - 1, z) == 3); }
    /** Wood darkened toward char: by half its fire the grain is brown-black, burnt through it is soot with a grey of ash. */
    private static int charred(int rgb, float c) {
        if (c <= 0) return rgb;
        c = Math.min(1, c);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float t = c * c * (3 - 2 * c);
        return (int) (r + (30 - r) * t) << 16 | (int) (g + (25 - g) * t) << 8 | (int) (b + (21 - b) * t);
    }
    private static int shadeRgb(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * k)), g = Math.min(255, (int) (((rgb >> 8) & 255) * k)), b = Math.min(255, (int) ((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }

    /** Deployed fishing gear: floats, stakes and a submerged mesh, rather than a storage chest. */
    public static Layer[] fishingNet() {
        Meshes.Boxes frame=new Meshes.Boxes(),rope=new Meshes.Boxes();
        for(int side:new int[]{-1,1}) {
            frame.rgb(side*0.52f,0.18f,0,0.055f,0.7f,0.055f,0x6B5034);
            frame.rgb(side*0.4f,0.02f,0,0.11f,0.08f,0.1f,0xAD8D58);
        }
        for(int row=0;row<7;row++)rope.rgb(0,-row*0.07f,0,1,0.012f,0.012f,0x6C7355);
        for(int col=0;col<11;col++)rope.rgb((col-5)*0.1f,-0.21f,0,0.012f,0.44f,0.012f,0x83856A);
        rope.rgb(0,0.04f,0,1.04f,0.025f,0.025f,0xBAA87B);
        return new Layer[]{wood(frame),new Layer(rope,0.94f,0,0)};
    }

    /** A grounded stake and a thin loop with a visible trigger and tether. */
    public static Layer[] snare(boolean armed) {
        Meshes.Boxes wood=new Meshes.Boxes(),cord=new Meshes.Boxes();
        wood.rgb(-0.24f,0.1f,0,0.045f,0.2f,0.045f,0x785537);
        wood.rgb(0.03f,0.03f,0,0.16f,0.045f,0.04f,0xB18A52);
        for(int step=0;step<24;step++) {
            double angle=step*Math.PI*2/24;
            cord.rgb((float)Math.cos(angle)*0.18f,armed?0.09f:0.018f,(float)Math.sin(angle)*0.18f,
                0.035f,0.014f,0.028f,0xAB9769);
        }
        cord.rgb(-0.14f,0.035f,0,0.22f,0.014f,0.014f,0x8A7957);
        return new Layer[]{wood(wood),new Layer(cord,0.96f,0,0)};
    }

    // ------------------------------------------------------------------ furniture
    public static Layer[] furniture(Furniture.Kind kind, float w, float d, int h, int seed) {
        return switch (kind) {
            case TABLE -> new Layer[]{wood(table(w, d, Furniture.TABLE_TOP))};
            case COUNTER -> new Layer[]{wood(counter(w, d))};
            case CRATE -> new Layer[]{wood(crate())};
            case BOOKCASE -> new Layer[]{wood(bookcase(h, seed))};
            case SLAB -> new Layer[]{stone(cellSlab(w, d))};
            case BED -> new Layer[]{wood(bedFrame()), new Layer(bedding(seed), 0.95f, 0, 0)};
            case TARGET -> new Layer[]{wood(targetStand(w)), new Layer(targetFace(), 0.95f, 0, 0)};
            case HEADSTONE -> new Layer[]{stone(headstone(seed)), new Layer(graveMound(seed), 0.97f, 0, 0)};
        };
    }

    private static Meshes.Boxes cellSlab(float w, float d) {
        Meshes.Boxes boxes = new Meshes.Boxes();
        boxes.rgb(0, 0.43f, 0, w - 0.08f, 0.24f, d - 0.08f, 0x75766D);
        for (float end : new float[]{-0.65f, 0.65f}) boxes.rgb(0, 0.16f, end, w - 0.24f, 0.32f, 0.24f, 0x5C5F59);
        // A few worn courses break the slab's silhouette without per-frame or per-voxel geometry work.
        for (int i = 0; i < 5; i++) boxes.rgb(0, 0.554f, (i - 2) * 0.35f, w - 0.14f, 0.008f, 0.32f, i % 2 == 0 ? 0x818177 : 0x777B70);
        return boxes;
    }

    /**
     * A weathered headstone (the cell's middle, facing -z): a slab with a rounded top built in stepped courses, a carved
     * cross or panel on its face, moss low on its sides - or, now and then, a stone cross on a plinth; each its own from its seed.
     */
    static Meshes.Boxes headstone(int seed) { return headstone(seed, 0, false); }

    /**
     * The player's memorial (GDD 3.5: chosen by the Honor they died with): honourable, white marble with its cross picked
     * out in gilt; despised, dark slate, cracked, its lettering scratched through; otherwise a plain grey stone.
     */
    public static Layer[] memorial(int honor, int seed) {
        Meshes.Boxes stone = honor >= 22 ? headstone(seed * 2 + 1, 1, false) : honor <= -22 ? headstone(seed * 2 + 1, -1, true) : headstone(seed * 2 + 1, 0, false);
        return new Layer[]{new Layer(stone, honor >= 22 ? 0.55f : 0.92f, 0, 0), new Layer(graveMound(seed), 0.97f, 0, 0)};
    }

    private static Meshes.Boxes headstone(int seed, int honor, boolean cracked) {
        Meshes.Boxes b = new Meshes.Boxes();
        long h = seed * 0x9E3779B97F4A7C15L;
        int tone = honor > 0 ? 0xE4E0D6 : honor < 0 ? 0x34383C : new int[]{0x8E8C86, 0x9A958B, 0x7F8086, 0xA39C8F}[(int) ((h >>> 40) & 3)];
        int dark = honor > 0 ? 0xC8A040 : honor < 0 ? 0x1A1C1E : Skin.shade(0xFF000000 | tone, 0.72f) & 0xFFFFFF, moss = 0x5E6E3A;
        float w = 0.5f + ((h >>> 20) & 7) * 0.02f, ht = 0.62f + ((h >>> 24) & 7) * 0.035f, d = 0.13f, z0 = 0.06f;
        boolean cross = honor == 0 && ((h >>> 30) & 3) == 0;
        if (cross) {
            b.span(-0.2f, 0, z0 - 0.08f, 0.2f, 0.14f, z0 + d + 0.08f, dark);
            b.span(-0.07f, 0.14f, z0, 0.07f, ht + 0.12f, z0 + d, tone);
            b.span(-0.25f, ht - 0.2f, z0, 0.25f, ht - 0.07f, z0 + d, tone);
        } else {
            b.span(-w / 2 - 0.03f, 0, z0 - 0.03f, w / 2 + 0.03f, 0.08f, z0 + d + 0.03f, dark);                 // its foot in the earth
            b.span(-w / 2, 0.08f, z0, w / 2, ht - 0.08f, z0 + d, tone);                                          // the slab
            b.span(-w / 2 + 0.05f, ht - 0.08f, z0, w / 2 - 0.05f, ht - 0.02f, z0 + d, tone);                      // the rounded top, in courses
            b.span(-w / 2 + 0.13f, ht - 0.02f, z0, w / 2 - 0.13f, ht + 0.03f, z0 + d, tone);
            // the carving on its face: a cross over lines of lettering
            b.span(-0.025f, ht - 0.25f, z0 - 0.006f, 0.025f, ht - 0.07f, z0, dark);
            b.span(-0.08f, ht - 0.16f, z0 - 0.006f, 0.08f, ht - 0.12f, z0, dark);
            for (int i = 0; i < 3; i++) b.span(-w / 2 + 0.1f, ht - 0.36f - i * 0.07f, z0 - 0.005f, w / 2 - 0.1f - (i == 2 ? 0.1f : 0), ht - 0.34f - i * 0.07f, z0, dark);
        }
        if (cracked) {
            // a crack running down from the shoulder, and the lettering struck through
            for (int i = 0; i < 6; i++) b.span(w / 2 - 0.08f - i * 0.045f, ht - 0.1f - i * 0.08f, z0 - 0.007f, w / 2 - 0.05f - i * 0.045f, ht - 0.02f - i * 0.08f, z0, 0x0E0F10);
            b.span(-w / 2 + 0.08f, ht - 0.43f, z0 - 0.009f, w / 2 - 0.08f, ht - 0.41f, z0, 0x8A2A20);
        }
        if (honor != 0) return b;
        // moss creeping up from the ground on the shaded side
        b.span(cross ? -0.08f : -w / 2 - 0.004f, 0.08f, z0 + d * 0.3f, cross ? -0.07f : -w / 2 + 0.01f, 0.22f + ((h >>> 33) & 3) * 0.04f, z0 + d + 0.004f, moss);
        b.span(cross ? -0.2f : -w / 2 - 0.03f, 0.08f, z0 + d + 0.03f, cross ? 0.05f : -w / 2 + 0.12f, 0.1f, z0 + d + 0.035f, moss);
        return b;
    }

    /** The grave in front of a headstone: a low mound of turned earth with a few tufts. */
    static Meshes.Boxes graveMound(int seed) {
        Meshes.Boxes b = new Meshes.Boxes();
        long h = seed * 0x632BE59BD9B4E019L;
        b.span(-0.36f, 0, -1.0f, 0.36f, 0.07f, 0.0f, 0x5C4632);
        b.span(-0.3f, 0.07f, -0.92f, 0.3f, 0.12f, -0.08f, 0x664E38);
        for (int i = 0; i < 4; i++) {
            float x = -0.28f + ((h >>> (i * 8)) & 63) / 63f * 0.56f, z = -0.9f + ((h >>> (i * 8 + 4)) & 63) / 63f * 0.8f;
            b.span(x, 0.1f, z, x + 0.04f, 0.17f, z + 0.04f, 0x5E7A34);
        }
        return b;
    }

    static Meshes.Boxes table(float w, float d, float top) {
        Meshes.Boxes b = new Meshes.Boxes();
        int topC = 0xA87A4A, apron = 0x8A5E34, leg = 0x7A5230;
        b.span(-w / 2 + 0.02f, top - 0.06f, -d / 2 + 0.02f, w / 2 - 0.02f, top, d / 2 - 0.02f, topC);
        b.span(-w / 2 + 0.02f, top - 0.065f, -d / 2 + 0.03f, w / 2 - 0.02f, top - 0.06f, d / 2 - 0.03f, 0x946638);   // the top's edge, darker underneath
        float ix = w / 2 - 0.1f, iz = d / 2 - 0.1f;
        b.span(-ix, top - 0.16f, -iz - 0.03f, ix, top - 0.06f, -iz + 0.0f, apron); b.span(-ix, top - 0.16f, iz, ix, top - 0.06f, iz + 0.03f, apron);
        b.span(-ix - 0.03f, top - 0.16f, -iz, -ix, top - 0.06f, iz, apron); b.span(ix, top - 0.16f, -iz, ix + 0.03f, top - 0.06f, iz, apron);
        for (float sx : new float[]{-1, 1}) for (float sz : new float[]{-1, 1}) b.span(sx * ix - 0.04f, 0, sz * iz - 0.04f, sx * ix + 0.04f, top - 0.06f, sz * iz + 0.04f, leg);
        if (w > 1.6f) for (float sz : new float[]{-1, 1}) b.span(-0.04f, 0, sz * iz - 0.04f, 0.04f, top - 0.06f, sz * iz + 0.04f, leg);
        if (d > 1.6f) for (float sx : new float[]{-1, 1}) b.span(sx * ix - 0.04f, 0, -0.04f, sx * ix + 0.04f, top - 0.06f, 0.04f, leg);
        return b;
    }

    static Meshes.Boxes counter(float w, float d) {
        Meshes.Boxes b = new Meshes.Boxes();
        float top = Furniture.COUNTER_TOP;
        b.span(-w / 2 + 0.04f, 0.08f, -d / 2 + 0.12f, w / 2 - 0.04f, top - 0.06f, d / 2 - 0.06f, 0x6A4426);   // the body
        b.span(-w / 2 + 0.07f, 0, -d / 2 + 0.16f, w / 2 - 0.07f, 0.08f, d / 2 - 0.08f, 0x4A2E18);            // kick plinth
        b.span(-w / 2, top - 0.06f, -d / 2, w / 2, top, d / 2, 0xA57A4C);                                      // the top, overhanging
        for (float x = -w / 2 + 0.06f; x <= w / 2 - 0.05f; x += 0.5f) b.span(x, 0.1f, -d / 2 + 0.105f, x + 0.05f, top - 0.08f, -d / 2 + 0.125f, 0x7E5530);   // panel stiles
        b.span(-w / 2 + 0.05f, top - 0.18f, -d / 2 + 0.105f, w / 2 - 0.05f, top - 0.14f, -d / 2 + 0.125f, 0x7E5530);
        b.span(-w / 2 + 0.05f, 0.14f, -d / 2 + 0.105f, w / 2 - 0.05f, 0.18f, -d / 2 + 0.125f, 0x7E5530);
        b.span(-w / 2 + 0.1f, 0.55f, d / 2 - 0.06f, w / 2 - 0.1f, 0.58f, d / 2 - 0.02f, 0x8A6038);           // a shelf on the keeper's side
        return b;
    }

    static Meshes.Boxes crate() {
        Meshes.Boxes b = new Meshes.Boxes();
        int body = 0xA0784A, frame = 0x6E4A28;
        b.span(-0.42f, 0.02f, -0.42f, 0.42f, 0.86f, 0.42f, body);
        float o = 0.45f, t = 0.07f;
        for (float sx : new float[]{-1, 1}) for (float sz : new float[]{-1, 1}) b.span(sx * o - (sx > 0 ? t : 0), 0, sz * o - (sz > 0 ? t : 0), sx * o + (sx < 0 ? t : 0), 0.9f, sz * o + (sz < 0 ? t : 0), frame);
        for (float y : new float[]{0, 0.9f - t}) {
            b.span(-o, y, -o, o, y + t, -o + t, frame); b.span(-o, y, o - t, o, y + t, o, frame);
            b.span(-o, y, -o, -o + t, y + t, o, frame); b.span(o - t, y, -o, o, y + t, o, frame);
        }
        for (float y : new float[]{0.3f, 0.58f}) { b.span(-o + t, y, -0.425f, o - t, y + 0.015f, -0.42f, 0x7E5832); b.span(-o + t, y, 0.42f, o - t, y + 0.015f, 0.425f, 0x7E5832); }
        return b;
    }

    static Meshes.Boxes bookcase(int h, int seed) {
        Meshes.Boxes b = new Meshes.Boxes();
        float top = h - 0.02f;
        int wood = 0x7A5230, dark = 0x5A3A20;
        b.span(-0.48f, 0, -0.3f, -0.43f, top, 0.3f, wood); b.span(0.43f, 0, -0.3f, 0.48f, top, 0.3f, wood);
        b.span(-0.48f, 0, 0.26f, 0.48f, top, 0.3f, dark);
        b.span(-0.48f, top - 0.05f, -0.3f, 0.48f, top, 0.3f, wood);
        Random r = new Random(seed);
        int[] covers = {0x962828, 0x285090, 0x327840, 0xA08232, 0x5A3270, 0x7A5A3A, 0x2A2A30};
        for (float y = 0; y < top - 0.3f; y += 0.42f) {
            b.span(-0.43f, y, -0.3f, 0.43f, y + 0.05f, 0.26f, wood);
            float x = -0.42f;
            while (x < 0.38f) {
                float bw = 0.04f + r.nextFloat() * 0.05f, bh = 0.22f + r.nextFloat() * 0.12f;
                if (r.nextFloat() < 0.12f) { x += 0.06f; continue; }
                b.span(x, y + 0.05f, -0.22f, Math.min(0.42f, x + bw), y + 0.05f + bh, 0.24f, covers[r.nextInt(covers.length)]);
                x += bw + 0.005f;
            }
        }
        return b;
    }

    static Meshes.Boxes bedFrame() {
        Meshes.Boxes b = new Meshes.Boxes();
        int wood = 0x7E5432, dark = 0x5E3C22;
        b.span(-0.48f, 0.18f, -0.98f, -0.43f, 0.38f, 0.98f, wood); b.span(0.43f, 0.18f, -0.98f, 0.48f, 0.38f, 0.98f, wood);   // rails
        b.span(-0.48f, 0, -1.0f, 0.48f, 0.5f, -0.94f, wood);                                                          // the foot board
        b.span(-0.5f, 0, 0.92f, 0.5f, 0.95f, 1.0f, wood);                                                             // the head board
        b.span(-0.5f, 0.95f, 0.9f, 0.5f, 1.0f, 1.0f, dark);
        for (float sx : new float[]{-1, 1}) { b.span(sx * 0.46f - 0.04f, 0, -0.98f, sx * 0.46f + 0.04f, 0.58f, -0.9f, dark); b.span(sx * 0.46f - 0.045f, 0, 0.9f, sx * 0.46f + 0.045f, 1.05f, 1.0f, dark); }
        b.span(-0.43f, 0.3f, -0.94f, 0.43f, 0.36f, 0.92f, 0x6A4628);                                                  // slats
        return b;
    }

    static Meshes.Boxes bedding(int seed) {
        Meshes.Boxes b = new Meshes.Boxes();
        int[] blankets = {0x7A8AA8, 0xA84A3A, 0x4A7A4A, 0xB8A060, 0x6A4A7A};
        int blanket = blankets[Math.floorMod(seed, blankets.length)];
        b.span(-0.44f, 0.36f, -0.93f, 0.44f, 0.52f, 0.91f, 0xE8E2D2);                                                 // mattress
        b.span(-0.46f, 0.4f, -0.95f, 0.46f, Furniture.MATTRESS, 0.32f, blanket);                                       // blanket, turned down
        b.span(-0.46f, 0.53f, 0.26f, 0.46f, 0.565f, 0.36f, 0xF0ECE0);
        b.span(-0.32f, 0.52f, 0.5f, 0.32f, 0.63f, 0.86f, 0xF4F2EC);                                                    // pillow
        return b;
    }

    static Meshes.Boxes targetStand(float w) {
        Meshes.Boxes b = new Meshes.Boxes();
        int wood = 0x7A5230;
        for (float sx : new float[]{-1, 1}) { b.span(sx * 0.62f - 0.05f, 0, 0.0f, sx * 0.62f + 0.05f, 1.7f, 0.1f, wood); b.span(sx * 0.62f - 0.06f, 0, -0.35f, sx * 0.62f + 0.06f, 0.08f, 0.4f, wood); }
        b.span(-0.68f, 1.55f, 0.0f, 0.68f, 1.63f, 0.1f, wood);
        b.span(-0.68f, 0.3f, 0.0f, 0.68f, 0.38f, 0.1f, wood);
        return b;
    }

    /** The boss: straw, then the painted rings stepping out toward the archer, each a disc of thin slices. */
    static Meshes.Boxes targetFace() {
        Meshes.Boxes b = new Meshes.Boxes();
        float cy = 1.0f, zBack = -0.2f;   // the painted face sits at the front of its cells, where arrows strike
        disc(b, 0.9f, cy, zBack - 0.24f, zBack, 0xC8A860);
        float[] radii = {0.9f, 0.72f, 0.54f, 0.36f, 0.18f};
        int[] colors = {0xF0ECE2, 0xC82828, 0xF0ECE2, 0xC82828, 0xE6C828};
        for (int i = 0; i < radii.length; i++) disc(b, radii[i], cy, zBack - 0.25f - i * 0.006f, zBack - 0.24f, colors[i]);
        return b;
    }

    private static void disc(Meshes.Boxes b, float r, float cy, float z0, float z1, int rgb) {
        int n = Math.max(4, Math.round(r * 18));
        for (int k = 0; k < n; k++) {
            float y0 = -r + 2 * r * k / n, y1 = -r + 2 * r * (k + 1) / n, ym = (y0 + y1) / 2;
            float hw = (float) Math.sqrt(Math.max(0, r * r - ym * ym));
            b.span(-hw, cy + y0, z0, hw, cy + y1, z1, rgb);
        }
    }

    // ------------------------------------------------------------------ workstations (front toward -z)
    public static Layer[] station(rpg.item.Recipes.Station kind) {
        return switch (kind) {
            case FURNACE -> furnace();
            case ANVIL -> anvil();
            case RANGE -> range();
            case CRAFT_TABLE -> craftTable();
            case SAWBENCH -> sawbench();
            default -> new Layer[0];
        };
    }

    private static Layer[] furnace() {
        Meshes.Boxes s = new Meshes.Boxes(), br = new Meshes.Boxes(), fire = new Meshes.Boxes();
        s.span(-0.48f, 0, -0.45f, 0.48f, 1.0f, 0.48f, 0x6E6A66);
        Random r = new Random(5);
        for (int i = 0; i < 18; i++) {   // masonry: stones standing a hair proud of the face
            float x = -0.42f + r.nextFloat() * 0.66f, y = 0.05f + r.nextFloat() * 0.8f;
            if (Math.abs(x + 0.08f) < 0.32f && y < 0.6f) continue;
            s.span(x, y, -0.455f, x + 0.12f + r.nextFloat() * 0.06f, y + 0.08f + r.nextFloat() * 0.04f, -0.448f, r.nextBoolean() ? 0x5E5A56 : 0x7E7A76);
        }
        s.span(-0.5f, 1.0f, -0.5f, 0.5f, 1.08f, 0.5f, 0x7A7672);
        s.span(-0.28f, 0.1f, -0.47f, 0.28f, 0.6f, -0.45f, 0x4E4A46);       // the arch's surround
        s.span(-0.24f, 0.12f, -0.471f, 0.24f, 0.56f, -0.2f, 0x1C1612);     // the mouth
        fire.span(-0.2f, 0.12f, -0.472f, 0.2f, 0.26f, -0.26f, 0xFF8A30);    // coals
        fire.span(-0.12f, 0.26f, -0.473f, 0.12f, 0.38f, -0.3f, 0xFFB050);
        br.span(-0.2f, 1.08f, -0.05f, 0.2f, 2.5f, 0.35f, 0x9C4A38);
        for (float y = 1.2f; y < 2.5f; y += 0.24f) br.span(-0.205f, y, -0.055f, 0.205f, y + 0.03f, 0.355f, 0x7A3A2C);
        br.span(-0.25f, 2.5f, -0.1f, 0.25f, 2.58f, 0.4f, 0x5A5652);
        return new Layer[]{stone(s), stone(br), glow(fire, 3f)};
    }

    private static Layer[] anvil() {
        Meshes.Boxes w = new Meshes.Boxes(), m = new Meshes.Boxes();
        w.span(-0.24f, 0, -0.24f, 0.24f, 0.42f, 0.24f, 0x7A5634);
        w.span(-0.23f, 0.42f, -0.23f, 0.23f, 0.44f, 0.23f, 0xA07A4E);
        m.span(-0.2f, 0.44f, -0.13f, 0.2f, 0.52f, 0.13f, 0x34363C);
        m.span(-0.09f, 0.52f, -0.08f, 0.09f, 0.66f, 0.08f, 0x34363C);
        m.span(-0.3f, 0.66f, -0.11f, 0.24f, 0.8f, 0.11f, 0x3E4048);
        m.span(-0.3f, 0.795f, -0.105f, 0.24f, 0.805f, 0.105f, 0x6A6E78);   // the worn face
        m.span(0.24f, 0.69f, -0.07f, 0.42f, 0.78f, 0.07f, 0x3E4048);
        m.span(0.42f, 0.71f, -0.04f, 0.5f, 0.76f, 0.04f, 0x3E4048);
        m.span(-0.34f, 0.7f, -0.04f, -0.3f, 0.8f, 0.04f, 0x3E4048);
        m.span(-0.05f, 0.805f, -0.2f, 0.02f, 0.84f, -0.1f, 0x505058);       // a hammer's head on the face
        w.span(0.02f, 0.81f, -0.17f, 0.3f, 0.83f, -0.13f, 0x8A6A40);
        return new Layer[]{wood(w), metal(m)};
    }

    private static Layer[] range() {
        Meshes.Boxes s = new Meshes.Boxes(), m = new Meshes.Boxes(), hot = new Meshes.Boxes();
        s.span(-0.48f, 0, -0.45f, 0.48f, 0.86f, 0.48f, 0x8E4636);
        for (float y = 0.12f; y < 0.86f; y += 0.12f) s.span(-0.485f, y, -0.455f, 0.485f, y + 0.015f, 0.485f, 0xB8AA98);
        m.span(-0.5f, 0.86f, -0.5f, 0.5f, 0.92f, 0.5f, 0x2A2A2E);
        m.span(-0.3f, 0.14f, -0.47f, 0.3f, 0.6f, -0.45f, 0x2A2A2E);
        m.span(-0.2f, 0.52f, -0.49f, 0.2f, 0.55f, -0.47f, 0xB0B0B0);
        m.span(0.25f, 0.92f, 0.25f, 0.37f, 2.1f, 0.37f, 0x2E2E32);
        hot.span(-0.36f, 0.92f, -0.28f, -0.1f, 0.93f, -0.02f, 0xC03A1A);
        hot.span(0.06f, 0.92f, -0.02f, 0.32f, 0.93f, 0.24f, 0xC03A1A);
        return new Layer[]{stone(s), metal(m), glow(hot, 1.2f)};
    }

    private static Layer[] craftTable() {
        Meshes.Boxes w = table(1f, 1f, 0.86f), m = new Meshes.Boxes();
        w.span(-0.3f, 0.86f, -0.2f, -0.05f, 0.885f, -0.17f, 0x8A6A40);       // hammer handle
        m.span(-0.33f, 0.86f, -0.24f, -0.27f, 0.9f, -0.13f, 0x505058);       // and head
        m.span(0.0f, 0.86f, 0.05f, 0.35f, 0.866f, 0.18f, 0xC0C0C8);          // a saw
        w.span(0.35f, 0.86f, 0.08f, 0.45f, 0.9f, 0.15f, 0x7A5230);
        w.span(-0.38f, 0.86f, 0.14f, -0.26f, 0.98f, 0.26f, 0xE0D8C0);        // a spool
        m.span(0.18f, 0.7f, -0.52f, 0.36f, 0.95f, -0.42f, 0x505058);         // the vise on the front edge
        w.span(-0.15f, 0.86f, -0.05f, 0.1f, 0.875f, 0.25f, 0xC8B080);         // fletching on a cloth
        return new Layer[]{wood(w), metal(m)};
    }

    private static Layer[] sawbench() {
        Meshes.Boxes w = new Meshes.Boxes(), m = new Meshes.Boxes();
        int wood = 0x8A6038;
        for (float z : new float[]{-0.3f, 0.3f}) {
            w.span(-0.45f, 0.6f, z - 0.05f, 0.45f, 0.68f, z + 0.05f, wood);
            for (float sx : new float[]{-1, 1}) { w.span(sx * 0.38f - 0.03f, 0, z - 0.12f, sx * 0.38f + 0.03f, 0.6f, z - 0.06f, wood); w.span(sx * 0.38f - 0.03f, 0, z + 0.06f, sx * 0.38f + 0.03f, 0.6f, z + 0.12f, wood); }
        }
        w.span(-0.55f, 0.68f, -0.13f, 0.55f, 0.92f, 0.13f, 0x6A4A2C);   // a log across the horses
        w.span(-0.56f, 0.7f, -0.11f, -0.55f, 0.9f, 0.11f, 0xC8A070); w.span(0.55f, 0.7f, -0.11f, 0.56f, 0.9f, 0.11f, 0xC8A070);
        m.span(0.06f, 0.8f, -0.008f, 0.12f, 1.26f, 0.008f, 0xC8C8D0);   // the saw, stopped mid-cut
        w.span(0.04f, 1.22f, -0.025f, 0.2f, 1.32f, 0.025f, 0x7A5230);
        return new Layer[]{wood(w), metal(m)};
    }

    // ------------------------------------------------------------------ doors
    /** Iron bars, a braced frame, strap hinges and a visible lock, at the ordinary hinge's exact dimensions. */
    public static Layer[] cellDoorLeaf(int height) {
        Meshes.Boxes bars = new Meshes.Boxes();
        for (float x : new float[]{0.045f, 0.955f}) bars.span(x - 0.045f, 0.02f, -0.055f, x + 0.045f, height - 0.02f, 0.055f, 0x454B52);
        for (float y : new float[]{0.08f, height * 0.45f, height - 0.08f}) bars.span(0, y - 0.04f, -0.045f, 1, y + 0.04f, 0.045f, 0x58616A);
        for (int i = 1; i < 6; i++) bars.span(i / 6f - 0.025f, 0.1f, -0.025f, i / 6f + 0.025f, height - 0.1f, 0.025f, 0x65717B);
        for (float y : new float[]{0.3f, height - 0.3f}) bars.span(0, y - 0.075f, -0.075f, 0.22f, y + 0.075f, 0.075f, 0x353B43);
        bars.span(0.8f, 1.05f, -0.09f, 0.98f, 1.3f, 0.09f, 0x353B43);
        bars.span(0.87f, 1.13f, -0.101f, 0.9f, 1.2f, -0.09f, 0x11151A);
        return new Layer[]{metal(bars)};
    }
    /** A door leaf from its hinge (at the origin) along +x, one metre wide and as tall as the doorway. */
    /**
     * (0.2.2, gamefixes) A fence-gate leaf (x 0..1 from the hinge, y up from the ground, a block high): a stile at
     * either end, the fence's two weathered rails at its heights, a diagonal brace from the hinge's foot to the latch
     * side's top that keeps it from sagging, and an iron latch and strap hinges.
     */
    public static Layer[] fenceGateLeaf() {
        Meshes.Boxes w = new Meshes.Boxes(), m = new Meshes.Boxes();
        w.span(0.0f, 0.04f, -0.05f, 0.1f, 0.94f, 0.05f, 0x6A4A2E);              // the hanging stile
        w.span(0.88f, 0.04f, -0.05f, 0.98f, 0.9f, 0.05f, 0x6E4E30);             // the latch stile
        w.span(0.1f, 0.3f, -0.035f, 0.88f, 0.42f, 0.035f, 0x8C6640);            // the rails, as the fence's
        w.span(0.1f, 0.7f, -0.035f, 0.88f, 0.82f, 0.035f, 0x96704A);
        w.span(0.1f, 0.06f, -0.03f, 0.88f, 0.14f, 0.03f, 0x7E5A38);             // a kick board at the foot
        // the brace: a run of short boards climbing from the hinge's foot to the far top, between the rails
        for (int i = 0; i < 7; i++) {
            float x = 0.14f + i * 0.105f, y = 0.14f + i * 0.08f;
            w.span(x, y, -0.028f, x + 0.12f, y + 0.12f, 0.028f, 0x83603C);
        }
        for (float y : new float[]{0.32f, 0.72f}) m.span(0.0f, y, -0.06f, 0.26f, y + 0.07f, 0.06f, 0x3A3A40);   // strap hinges
        m.span(0.9f, 0.56f, -0.075f, 0.99f, 0.62f, 0.075f, 0x2E2E32);           // the latch bar
        return new Layer[]{wood(w), metal(m)};
    }

    public static Layer[] doorLeaf(float height, boolean locked) {
        Meshes.Boxes w = new Meshes.Boxes(), m = new Meshes.Boxes();
        w.span(0.02f, 0, -0.05f, 0.98f, height - 0.02f, 0.05f, 0x8A5E34);
        for (float x = 0.25f; x < 0.95f; x += 0.24f) { w.span(x, 0.02f, -0.052f, x + 0.012f, height - 0.04f, -0.05f, 0x6A4426); w.span(x, 0.02f, 0.05f, x + 0.012f, height - 0.04f, 0.052f, 0x6A4426); }
        int band = locked ? 0x3A3A40 : 0x4A4A50;
        for (float y : new float[]{0.25f, height - 0.35f}) { m.span(0.02f, y, -0.058f, 0.9f, y + (locked ? 0.1f : 0.06f), -0.05f, band); m.span(0.02f, y, 0.05f, 0.9f, y + (locked ? 0.1f : 0.06f), 0.058f, band); }
        if (locked) m.span(0.72f, height * 0.45f, -0.07f, 0.9f, height * 0.45f + 0.2f, 0.07f, 0xC8A040);   // the lock plate
        m.span(0.82f, height * 0.48f, -0.1f, 0.88f, height * 0.48f + 0.04f, 0.1f, 0x2A2A2A);                // the handle, both sides
        return new Layer[]{wood(w), metal(m)};
    }
}
