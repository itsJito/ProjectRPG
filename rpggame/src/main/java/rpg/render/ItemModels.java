package rpg.render;

import astratto.render.Tex;
import astratto.voxel.VoxModel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import rpg.item.ItemDef;
import rpg.item.ItemDef.Family;
import rpg.item.ItemDef.Slot;
import rpg.item.Items;
import rpg.item.Tier;
import rpg.skill.Skill;

/**
 * Every item's own voxel model (gamefixes: every item individually modelled; the same model in the hand, on the ground
 * and as the inventory icon). One voxel is {@link #VOX} metres. The model's y axis runs along the item (blade, haft,
 * shaft), x across it and z through its thickness; held things carry a grip point (the hand's place).
 *
 * <p>Materials ride in the colour's alpha: {@link #METAL} voxels are metal (they reflect as metal), the rest are not.
 * Tiers differ by more than colour: each has its own guard, grip wrap, pommel and head shapes, so a Cobalt sword and a
 * Steel sword can be told apart at a glance.
 */
public final class ItemModels {
    private ItemModels() { }

    /** One voxel: 1/64 m, the texel density of a block's 64 x 64 face (gamefixes: everything at 64 x 64). */
    public static final float VOX = 1 / 64f;
    /** The alpha that marks a metal voxel (0xFF is everything else). */
    public static final int METAL = 0xFE;

    /** A model and where the hand holds it (voxel coordinates; for things not held, the middle of the base). */
    public record Model(VoxModel vox, float gx, float gy, float gz) {}

    private static final Map<String, Model> CACHE = new ConcurrentHashMap<>();

    public static Model of(ItemDef d) {
        return CACHE.computeIfAbsent(d.id, k -> fine(d));
    }

    /**
     * The model at 1/64 m: the weapons, tools and shields built at that density from the start (HiResItems); the rest
     * from their 1/32 m designs, doubled and bevelled, with any dark metal brought up to the reflectance metal has.
     */
    private static Model fine(ItemDef d) {
        // wooden gear keeps the wood designs (planked shield, a wooden blade)
        Tier t = d.tier == Tier.WOODEN ? null : d.tier;
        if (d.id.startsWith("cape_")) return HiResItems.skillcape(Skill.valueOf(d.id.substring(5).toUpperCase()));
        switch (d.family) {
            case SWORD: return HiResItems.sword(d, t);
            case DAGGER: return HiResItems.dagger(d, t);
            case AXE: return HiResItems.axe(d, t);
            case MACE: return HiResItems.mace(d, t);
            case PICKAXE: return HiResItems.pickaxe(d, t);
            case BOW: return HiResItems.bow(d);
            case SHIELD: return HiResItems.shield(d, t);
            case LONGSWORD: return HiResItems.longsword(d, t);
            case SCIMITAR: return HiResItems.scimitar(d, t);
            case WARHAMMER: return HiResItems.warhammer(d, t);
            case SPEAR: return HiResItems.spear(d, t);
            case CLAWS: return HiResItems.claws(d, t);
            case HALBERD: return HiResItems.halberd(d, t);
            case CROSSBOW: return HiResItems.crossbow(d, t);
            case THROWN: return HiResItems.thrown(d, t);
            case BOLT: return HiResItems.bolts(d, t);
            case ARROW: if (d.id.contains("brutal")) return HiResItems.bolts(d, t); break;
            case ARMOR:
                if (d.tier != null && d.slot == Slot.HEAD) return HiResItems.helm(d, t);
                if (d.tier != null && d.slot == Slot.BODY) return HiResItems.body(d, t);
                if (d.tier != null && d.slot == Slot.LEGS) return HiResItems.legs(d, t);
                break;
            default: break;
        }
        Model treasure = TreasureModels.of(d);
        if (treasure != null) return treasure;
        if (d.look != null && d.slot == null) { Model loot = LootModels.of(d); if (loot != null) return loot; }
        if (d.restoresFaith > 0) return HiResItems.bottle(d);
        if (d.family == rpg.item.ItemDef.Family.JEWEL) return d.slot == Slot.RING ? HiResItems.ring(d) : HiResItems.necklace(d);
        if (d.id.startsWith("uncut_")) return HiResItems.gem(d, true);
        if (d.id.endsWith("_mould")) return HiResItems.mould(d);
        if (d.id.equals("gold_bar")) return HiResItems.bar(0xE8C048);
        if (d.id.equals("chisel")) return HiResItems.chisel();
        Model coarse = switch (d.id) {
            case "small_fishing_net" -> net();
            case "snare" -> snare();
            case "cloth_roll" -> clothRoll();
            case "stone_block" -> quarryBlock();
            case "crab_cage" -> cage();
            case "raw_crab", "crab" -> crab(d);
            default -> build(d);
        };
        Model m = HiResItems.doubled(coarse);
        var v = m.vox();
        for (int i = 0; i < v.col.length; i++) {
            int c = v.col[i];
            if (c == 0 || (c >>> 24) != METAL) continue;
            int l = HiResItems.lum(c);
            if (l < 150) v.col[i] = (METAL << 24) | (Tex.shade(c, Math.min(2.2f, 150f / Math.max(20, l))) & 0xFFFFFF);
        }
        return m;
    }

    private static Model snare() {
        VoxModel vox=new VoxModel(15,16,3);
        for(int x=0;x<15;x++)for(int y=0;y<15;y++) {
            float distance=(float)Math.hypot(x-7,y-7);
            if(distance>5.5f && distance<6.7f)vox.set(x,y,1,mat(0xAB9769));
        }
        vox.box(6,0,1,7,4,1,mat(0x8C7750));vox.box(1,2,0,2,15,1,mat(0x785537));
        return new Model(vox,7.5f,0.5f,1.5f);
    }
    private static Model clothRoll() {
        VoxModel vox=new VoxModel(14,10,10);
        for(int x=0;x<14;x++)for(int y=0;y<10;y++)for(int z=0;z<10;z++) {
            float radius=(float)Math.hypot(y-4.5f,z-4.5f);if(radius>4.5f)continue;
            int color=x==0 || x==13?((int)(radius*2)%2==0?0xC9BCA0:0x968B73):(x+y+z)%4==0?0xB9AD91:0xC5B99D;
            if(x==4 || x==9)color=0x7E745B;
            vox.set(x,y,z,mat(color));
        }
        return new Model(vox,7,0.5f,5);
    }
    private static Model quarryBlock() {
        VoxModel vox=new VoxModel(14,10,10);
        for(int x=0;x<14;x++)for(int y=0;y<10;y++)for(int z=0;z<10;z++) {
            if((x==0 || x==13) && (y==0 || y==9) && (z==0 || z==9))continue;
            int grain=Math.floorMod(x*31+y*17+z*13,9);
            vox.set(x,y,z,mat(grain<2?0x757A7D:grain>6?0x9A9C97:0x888C88));
        }
        return new Model(vox,7,0.5f,5);
    }

    /** A small hand net: a hoop on a short handle, the mesh across it. (1/32 m, doubled.) */
    private static Model net() {
        VoxModel m = new VoxModel(13, 22, 3);
        for (int y = 0; y < 9; y++) m.set(6, y, 1, mat(0x8A6236));
        for (int x = 0; x < 13; x++) for (int y = 9; y < 22; y++) {
            float dx = x - 6, dy = y - 15.5f, r = (float) Math.sqrt(dx * dx + dy * dy);
            if (r > 6.4f) continue;
            if (r > 5.4f) m.set(x, y, 1, mat(0x7A5430));
            else if ((x + y) % 2 == 0) m.set(x, y, 1, mat(0xD8CCA8));
            if (r <= 4.5f && (x * 3 + y) % 5 == 0) m.set(x, y, 2, mat(0xC8BC98));
        }
        return new Model(m, 6.5f, 3.5f, 1.5f);
    }

    /** A crab cage: a slatted wooden pot with a rope handle. (1/32 m, doubled.) */
    private static Model cage() {
        VoxModel m = new VoxModel(11, 10, 11);
        for (int x = 0; x < 11; x++) for (int y = 0; y < 8; y++) for (int z = 0; z < 11; z++) {
            boolean wall = x == 0 || x == 10 || z == 0 || z == 10, floor = y == 0;
            if (!(wall || floor || y == 7 && (x == 0 || x == 10 || z == 0 || z == 10))) continue;
            boolean slat = floor || y == 7 || (x + z) % 3 == 0 || y == 3;
            if (slat) m.set(x, y, z, (y + x) % 4 == 0 ? mat(0x6A4A2A) : mat(0x8A6236));
        }
        for (int x = 3; x <= 7; x++) m.set(x, 9, 5, mat(0xC8B48A));
        m.set(3, 8, 5, mat(0xC8B48A)); m.set(7, 8, 5, mat(0xC8B48A));
        return new Model(m, 5.5f, 9.5f, 5.5f);
    }

    /** A crab: a shell, claws and legs; cooked, it is redder. (1/32 m, doubled.) */
    private static Model crab(ItemDef d) {
        boolean cooked = d.id.equals("crab");
        int shell = cooked ? 0xD8603A : 0x8A4A3A, claw = cooked ? 0xE87A50 : 0xA05A44;
        VoxModel m = new VoxModel(13, 4, 10);
        for (int x = 2; x < 11; x++) for (int z = 2; z < 8; z++) { float dx = (x - 6) / 4.5f, dz = (z - 4.5f) / 3f; if (dx * dx + dz * dz <= 1) { m.set(x, 1, z, mat(shell)); if (dx * dx + dz * dz < 0.5f) m.set(x, 2, z, mat(Tex.shade(shell, 1.1f) & 0xFFFFFF)); } }
        for (int s2 = 0; s2 < 2; s2++) { int x = s2 == 0 ? 1 : 11; m.set(x, 1, 1, mat(claw)); m.set(x, 1, 0, mat(claw)); m.set(x + (s2 == 0 ? 1 : -1), 1, 0, mat(claw)); }
        for (int z = 3; z < 8; z += 2) { m.set(0, 0, z, mat(claw)); m.set(12, 0, z, mat(claw)); m.set(1, 1, z, mat(claw)); m.set(11, 1, z, mat(claw)); }
        m.set(5, 2, 2, mat(0x101010)); m.set(7, 2, 2, mat(0x101010));
        return new Model(m, 6.5f, 0.5f, 5f);
    }

    static int metal(int rgb) { return METAL << 24 | (rgb & 0xFFFFFF); }
    static int mat(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }
    static int sh(int c, float f) { return (c & 0xFF000000) | (Tex.shade(c, f) & 0xFFFFFF); }

    // ------------------------------------------------------------------ the catalogue
    private static Model build(ItemDef d) {
        Tier t = d.tier;
        if (d.id.startsWith("cape_")) return cape(Skill.valueOf(d.id.substring(5).toUpperCase()));
        switch (d.family) {
            case SWORD: return sword(d, t);
            case DAGGER: return dagger(d, t);
            case AXE: return axe(d, t);
            case MACE: return mace(d, t);
            case PICKAXE: return pickaxe(d, t);
            case SHIELD: return shield(d, t);
            case ARROW: return arrows(d);
            case STAFF: return staff(d);
            case WAND: return wand(d);
            case SYMBOL: return symbol(d);
            case BAG: return bag(d);
            case ARMOR:
                if (d.slot == Slot.HEAD) return helm(d, t);
                if (d.slot == Slot.BODY) return body(d, t);
                if (d.slot == Slot.LEGS) return legs(d, t);
                if (d.slot == Slot.FEET) return boots(d);
                if (d.slot == Slot.HANDS) return gloves(d);
                break;
            default: break;
        }
        return switch (d.id) {
            case "coins" -> coins();
            case "logs", "oak_logs", "willow_logs" -> logs(d);
            case "copper_ore", "tin_ore", "iron_ore", "coal" -> ore(d);
            case "bronze_bar", "iron_bar", "steel_bar" -> bar(d);
            case "raw_shrimp", "shrimp" -> shrimp(d);
            case "raw_trout", "trout", "burnt_fish" -> fish(d);
            case "bread" -> bread();
            case "raw_meat", "cooked_meat", "burnt_meat" -> meat(d);
            case "rawhide" -> hide();
            case "wool" -> wool();
            case "egg" -> egg();
            case "milk" -> milk();
            case "feather" -> feather();
            case "animal_feed" -> feedSack();
            case "berries" -> berries();
            case "guam" -> leaf();
            case "flax" -> flax();
            case "bowstring" -> bowstring();
            case "plank", "oak_plank" -> plank(d);
            case "bones", "big_bones" -> bones(d);
            case "arrow_shaft" -> shafts();
            case "tinderbox" -> tinderbox();
            case "hammer" -> hammer();
            case "knife" -> knife();
            case "fishing_rod" -> rod();
            case "lockpick" -> lockpick();
            case "sealed_letter" -> letter();
            case "bounty_scroll" -> warrant();
            case "xp_lamp" -> lamp();
            case "chancery_key" -> key();
            default -> lump(d);
        };
    }

    // ------------------------------------------------------------------ tier styling
    private record Look(int blade, int grip, int guard, int pommel, int style) {}

    private static Look look(Tier t, ItemDef d) {
        if (t == null) return new Look(mat(d.color), mat(0x4A3020), mat(0x6A4A2A), mat(0x5A3A20), 0);
        int b = metal(t.color);
        return switch (t) {
            case STONE -> new Look(mat(0x8E8C86), mat(0x5A3A22), mat(0x6A6862), mat(0x7A7872), 0);
            case BRONZE -> new Look(b, mat(0x5A3A22), metal(0x8A5A2A), metal(0x9A6030), 1);
            case IRON -> new Look(b, mat(0x3A2A1C), metal(0x55555A), metal(0x5A5A60), 2);
            case BLACK -> new Look(b, mat(0x18161A), metal(0x3A3A44), metal(0x2A2A32), 3);
            case WHITE -> new Look(b, mat(0xE8E4D8), metal(0xC8C4BA), metal(0xD8D4C8), 4);
            case STEEL -> new Look(b, mat(0x2E2A28), metal(0x7A828C), metal(0x8A929C), 5);
            case COBALT -> new Look(b, mat(0x223060), metal(0x6A7AC0), metal(0x8090D0), 6);
            case GRANITE -> new Look(b, mat(0x30343A), metal(0x9AA6AE), metal(0xC8D2DA), 7);
            case MOLTEN -> new Look(b, mat(0x2A1008), metal(0x8A3010), metal(0xF0A040), 8);
            case MALEVOLENT -> new Look(b, mat(0x1A0A20), metal(0x2A1A30), metal(0x6A3A80), 9);
            case DRAGON -> new Look(b, mat(0x3A0E0E), metal(0x8A1A1A), metal(0xD8B040), 10);
            case GODLY -> new Look(metal(0xF4F0E0), mat(0x5A2A7A), metal(0xF0C850), metal(0xF0C850), 11);
            default -> new Look(b, mat(0x5A3A22), metal(0x6A6A6A), metal(0x6A6A6A), 0);
        };
    }

    // ------------------------------------------------------------------ weapons
    private static Model sword(ItemDef d, Tier t) {
        Look L = look(t, d);
        boolean wood = t == null;
        int bladeLen = Math.round(d.reach * 0.78f / VOX), grip = 7, w = 13, cx = 6;
        int h = bladeLen + grip + 5;
        VoxModel m = new VoxModel(w, h, 3);
        int gz = 1;
        // pommel, grip (wrapped), guard, blade
        pommel(m, cx, 1, gz, L, wood);
        for (int y = 2; y < 2 + grip; y++) m.set(cx, y, gz, (y % 2 == 0) ? L.grip() : sh(L.grip(), 1.25f));
        int gy = 2 + grip;
        guard(m, cx, gy, gz, L, wood ? 2 : 3 + Math.min(3, L.style() / 3));
        int y0 = gy + 1, half = L.style() >= 9 ? 2 : 1;
        for (int i = 0; i < bladeLen; i++) {
            int y = y0 + i;
            int left = bladeLen - i;
            int hw = left <= 2 ? 0 : left <= 5 ? Math.min(half, 1) : half;
            for (int x = -hw; x <= hw; x++) {
                int c = Math.abs(x) == hw && hw > 0 ? sh(L.blade(), 1.22f) : L.blade();
                if (x == 0 && L.style() >= 2 && i > 1 && left > 6) c = sh(L.blade(), 0.78f);   // the fuller
                m.set(cx + x, y, gz, c);
            }
            if (L.style() == 10 && i % 4 == 2 && left > 4) { m.set(cx - hw - 1, y, gz, L.blade()); m.set(cx + hw + 1, y, gz, L.blade()); }   // Dragon: serrated
        }
        if (L.style() == 11) for (int i = 2; i < bladeLen - 6; i += 5) m.set(cx, y0 + i, gz, metal(0xF0C850));   // Godly: gold inlay
        return new Model(m, cx + 0.5f, 5.5f, gz + 0.5f);
    }

    private static Model dagger(ItemDef d, Tier t) {
        Look L = look(t, d);
        int bladeLen = Math.round(d.reach * 0.42f / VOX), grip = 5, cx = 4;
        VoxModel m = new VoxModel(9, bladeLen + grip + 4, 3);
        pommel(m, cx, 1, 1, L, false);
        for (int y = 2; y < 2 + grip; y++) m.set(cx, y, 1, y % 2 == 0 ? L.grip() : sh(L.grip(), 1.25f));
        guard(m, cx, 2 + grip, 1, L, 2);
        for (int i = 0; i < bladeLen; i++) {
            int y = 3 + grip + i, left = bladeLen - i;
            int hw = left <= 2 ? 0 : 1;
            for (int x = -hw; x <= hw; x++) m.set(cx + x, y, 1, Math.abs(x) == hw && hw > 0 ? sh(L.blade(), 1.2f) : L.blade());
        }
        return new Model(m, cx + 0.5f, 4.5f, 1.5f);
    }

    private static void pommel(VoxModel m, int cx, int y, int z, Look L, boolean wood) {
        m.set(cx, y, z, L.pommel());
        if (L.style() >= 5 && !wood) { m.set(cx - 1, y, z, L.pommel()); m.set(cx + 1, y, z, L.pommel()); m.set(cx, y - 1, z, L.pommel()); }
        if (L.style() == 11 || L.style() == 10) m.set(cx, y, z, mat(L.style() == 11 ? 0x40C0F0 : 0xE02020));   // a gem
    }

    /** The cross-guard: each tier its own shape. */
    private static void guard(VoxModel m, int cx, int y, int z, Look L, int span) {
        int c = L.guard();
        for (int x = -span; x <= span; x++) m.set(cx + x, y, z, c);
        switch (L.style()) {
            case 3 -> { m.set(cx - span, y - 1, z, c); m.set(cx + span, y - 1, z, c); }                // Black: down-swept
            case 4, 6 -> { m.set(cx - span, y + 1, z, c); m.set(cx + span, y + 1, z, c); }             // White, Mithril: up-swept
            case 7 -> { for (int x = -1; x <= 1; x++) for (int zz = 0; zz < 3; zz++) m.set(cx + x, y, zz, c); }   // Titanium: a disc
            case 8 -> { m.set(cx - span, y + 1, z, c); m.set(cx + span, y + 1, z, c); m.set(cx - span - 1, y + 2, z, c); m.set(cx + span + 1, y + 2, z, c); }   // Thorium: crescent
            case 9 -> { for (int x = -span; x <= span; x++) m.set(cx + x, y + 1, z, sh(c, 0.85f)); }   // Carbide: heavy block
            case 10 -> { m.set(cx - span - 1, y + 1, z, c); m.set(cx + span + 1, y + 1, z, c); m.set(cx - span - 1, y + 2, z, c); m.set(cx + span + 1, y + 2, z, c); }   // Dragon: wings
            case 11 -> { m.set(cx - span - 1, y, z, c); m.set(cx + span + 1, y, z, c); m.set(cx, y + 1, z, c); m.set(cx - 1, y + 1, z, c); m.set(cx + 1, y + 1, z, c); }   // Godly: broad
            default -> { }
        }
    }

    private static int haft(VoxModel m, int cx, int y0, int len, int z, int c) {
        for (int y = y0; y < y0 + len; y++) m.set(cx, y, z, (y / 3) % 2 == 0 ? c : sh(c, 0.88f));
        return y0 + len;
    }

    private static Model axe(ItemDef d, Tier t) {
        Look L = look(t, d);
        int len = Math.round(d.reach * 0.9f / VOX), cx = 10;
        VoxModel m = new VoxModel(21, len + 2, 3);
        int top = haft(m, cx, 0, len, 1, mat(0x7A5430));
        // the head: a bearded wedge, narrow at the haft and flaring to a long curved bit; bigger with the tier
        int hh = 8 + Math.min(3, (t == null ? 1 : t.power) / 4), depth = 6 + Math.min(3, (t == null ? 1 : t.power) / 4);
        boolean dbl = L.style() >= 10;
        for (int j = 0; j < hh; j++) {
            int y = top - 1 - j;
            float u = j / (float) (hh - 1);
            int reach = Math.round(2 + (depth - 2) * (float) Math.pow(Math.abs(u - 0.35f) / 0.65f + 0.35f, 0.8f));
            if (j > 1 && j < hh - 2) reach = Math.max(reach, 3);
            reach = Math.min(depth, reach);
            for (int x = 1; x <= reach; x++) for (int z = 0; z < 3; z++) if (z == 1 || x <= 2) m.set(cx + x, y, z, x >= reach - 0 ? sh(L.blade(), 1.3f) : L.blade());
            if (dbl) for (int x = 1; x <= reach; x++) m.set(cx - x, y, 1, x >= reach ? sh(L.blade(), 1.3f) : L.blade());
        }
        m.set(cx, top, 1, L.pommel());
        if (L.style() == 11) m.set(cx + 2, top - 3, 1, mat(0x40C0F0));
        return new Model(m, cx + 0.5f, 3.5f, 1.5f);
    }

    private static Model mace(ItemDef d, Tier t) {
        Look L = look(t, d);
        int len = Math.round(d.reach * 0.82f / VOX), cx = 4;
        VoxModel m = new VoxModel(9, len + 6, 9);
        haft(m, cx, 0, len, 4, mat(0x6A4628));
        int r = 3 + Math.min(1, (t == null ? 1 : t.power) / 6);
        int hy = len + 1;
        for (int y = hy - r; y <= hy + r; y++) for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            int dist = Math.abs(x) + Math.abs(z);
            boolean flange = (x == 0 || z == 0) && dist <= r + 1;
            if (dist <= r - 1 || flange && Math.abs(y - hy) < r) m.set(cx + x, y, 4 + z, flange ? sh(L.blade(), 1.15f) : L.blade());
        }
        if (L.style() == 3 || L.style() >= 10) for (int k = 0; k < 4; k++) m.set(cx + (k % 2 == 0 ? r + 1 : -r - 1), hy + (k < 2 ? 1 : -1), 4, L.guard());   // spikes
        m.set(cx, hy + r + 1, 4, L.pommel());
        return new Model(m, cx + 0.5f, 4.5f, 4.5f);
    }

    private static Model pickaxe(ItemDef d, Tier t) {
        Look L = look(t, d);
        int len = Math.round(d.reach * 0.9f / VOX), cx = 9;
        VoxModel m = new VoxModel(19, len + 3, 3);
        int top = haft(m, cx, 0, len, 1, mat(0x7A5430));
        int span = 6 + Math.min(3, (t == null ? 1 : t.power) / 3);
        for (int x = -span; x <= span; x++) {
            int droop = Math.abs(x) > span - 3 ? 1 : 0;
            int y = top - 1 - droop;
            m.set(cx + x, y, 1, Math.abs(x) >= span - 1 ? sh(L.blade(), 1.25f) : L.blade());
            if (Math.abs(x) < span - 3) m.set(cx + x, y + 1, 1, sh(L.blade(), 0.9f));
        }
        m.set(cx, top + 1, 1, L.pommel());
        return new Model(m, cx + 0.5f, 3.5f, 1.5f);
    }

    private static Model shield(ItemDef d, Tier t) {
        if (t == null) {
            // a round shield of planks with an iron rim and boss
            VoxModel m = new VoxModel(19, 19, 3);
            for (int x = 0; x < 19; x++) for (int y = 0; y < 19; y++) {
                float dx = x - 9, dy = y - 9, r = (float) Math.sqrt(dx * dx + dy * dy);
                if (r > 9.3f) continue;
                int c = r > 8.2f ? metal(0x55555A) : (x / 4) % 2 == 0 ? mat(0x8A6034) : mat(0x7A5430);
                m.set(x, y, 1, c);
            }
            m.box(8, 8, 2, 10, 10, 2, metal(0x6A6A70));
            return new Model(m, 9.5f, 9.5f, 0.5f);
        }
        Look L = look(t, d);
        // a heater shield: flat top, pointed foot, rim and boss; an emblem stripe in the tier's grip colour
        int W = 17, H = 21;
        VoxModel m = new VoxModel(W, H, 3);
        for (int y = 0; y < H; y++) {
            int half = y > 7 ? 8 : Math.max(1, (int) (8 * Math.sqrt(y / 7.0)));
            for (int x = -half; x <= half; x++) {
                boolean rim = Math.abs(x) >= half - 0 || y >= H - 1 || y == 0;
                int c = rim ? sh(L.blade(), 0.7f) : L.blade();
                if (!rim && (x == 0 || y == 13)) c = sh(L.grip() | 0xFF000000, 1.4f);
                m.set(8 + x, H - 1 - y, 1, c);
            }
        }
        m.box(7, 12, 2, 9, 14, 2, L.pommel());
        if (L.style() == 11) m.set(8, 13, 2, mat(0x40C0F0));
        return new Model(m, 8.5f, 12.5f, 0.5f);
    }

    private static Model arrows(ItemDef d) {
        VoxModel m = new VoxModel(7, 30, 3);
        for (int k = 0; k < 3; k++) {
            int x = 1 + k * 2, off = k == 1 ? 2 : 0;
            for (int y = 3 + off; y < 26 + off; y++) m.set(x, y, 1, mat(0xB8925A));
            m.set(x, 26 + off, 1, metal(d.color)); m.set(x, 27 + off, 1, metal(sh(d.color, 1.2f)));
            for (int y = 3 + off; y < 7 + off; y++) { m.set(x, y, 0, mat(0xE8E0D0)); m.set(x, y, 2, mat(0xC03020)); }
        }
        return new Model(m, 3.5f, 14.5f, 1.5f);
    }

    private static Model staff(ItemDef d) {
        // an elemental staff's orb is its element's colour, the haft darkened toward it; the plain staff's is violet
        boolean fire = d.id.equals("fire_staff"), elemental = d.staffElement != null;
        int orb = fire ? 0xFF6020 : elemental ? d.color : 0xB070F0;
        VoxModel m = new VoxModel(7, 54, 7);
        haft(m, 3, 0, 46, 3, mat(fire ? 0x5A2A1A : elemental ? sh(d.color, 0.38f) : 0x6A4A7A));
        m.box(2, 45, 2, 4, 46, 4, mat(fire ? 0x8A3A1A : elemental ? sh(d.color, 0.6f) : 0x8A6AA0));
        m.sphere(3.5f, 49.5f, 3.5f, 2.6f, mat(orb));
        if (fire) { m.set(3, 53, 3, mat(0xFFD040)); m.set(2, 52, 3, mat(0xFF9020)); m.set(4, 52, 4, mat(0xFF9020)); }
        else if (elemental) { m.set(3, 53, 3, mat(sh(d.color, 1.3f))); m.set(2, 52, 3, mat(sh(d.color, 1.15f))); m.set(4, 52, 4, mat(sh(d.color, 1.15f))); }
        return new Model(m, 3.5f, 14.5f, 3.5f);
    }

    private static Model wand(ItemDef d) {
        VoxModel m = new VoxModel(3, 14, 3);
        for (int y = 0; y < 12; y++) m.set(1, y, 1, y < 4 ? mat(0x5A3A20) : mat(d.color));
        m.set(1, 12, 1, mat(0xC0E0FF)); m.set(1, 13, 1, mat(0xE8F4FF));
        return new Model(m, 1.5f, 2.5f, 1.5f);
    }

    private static Model symbol(ItemDef d) {
        boolean blessed = d.id.equals("blessed_symbol");
        int c = blessed ? metal(0xF0C850) : metal(0xB8B8C0);
        VoxModel m = new VoxModel(9, 13, 3);
        m.box(4, 0, 1, 4, 10, 1, c);
        m.box(1, 6, 1, 7, 6, 1, c);
        if (blessed) { m.set(4, 6, 2, mat(0x40C0F0)); m.set(0, 6, 1, c); m.set(8, 6, 1, c); }
        for (int y = 11; y < 13; y++) m.set(4, y, 1, mat(0x8A7A5A));
        return new Model(m, 4.5f, 2.5f, 1.5f);
    }

    // ------------------------------------------------------------------ armour
    private static int[] armourColours(Tier t, ItemDef d) {
        if (t == null) return new int[]{mat(d.color), mat(Tex.shade(d.color, 1.3f)), 0};
        if (t == Tier.CHAINMAIL) return new int[]{metal(0x8A8F96), metal(0x5E636A), 1};
        Look L = look(t, d);
        return new int[]{L.blade(), L.guard(), 2};
    }

    /** A worn surface: leather is stitched, mail is linked, plate is riveted. */
    private static int surface(int[] c, int x, int y, int z) {
        return switch (c[2]) {
            case 1 -> (x + y + z) % 2 == 0 ? c[0] : c[1];
            case 2 -> (x % 5 == 0 && y % 4 == 1) ? sh(c[0], 1.35f) : c[0];
            default -> (y % 3 == 0 && x % 2 == 0) ? c[1] : c[0];
        };
    }

    private static Model helm(ItemDef d, Tier t) {
        int[] c = armourColours(t, d);
        VoxModel m = new VoxModel(11, 11, 11);
        for (int x = 0; x < 11; x++) for (int y = 0; y < 11; y++) for (int z = 0; z < 11; z++) {
            float dx = x - 5, dy = Math.max(0, y - 4), dz = z - 5;
            float r = (float) Math.sqrt(dx * dx + dy * dy * 1.2f + dz * dz);
            if (r > 5.6f || r < 4.4f) continue;
            if (z >= 9 && y >= 3 && y <= 5 && Math.abs(x - 5) <= 3 && c[2] == 2) continue;   // the visor slit
            if (z >= 9 && y < 6 && Math.abs(x - 5) <= 2 && c[2] != 2) continue;               // an open face
            m.set(x, y, z, surface(c, x, y, z));
        }
        if (c[2] == 2) for (int y = 1; y < 10; y++) m.set(5, y, 10, sh(c[1], 1.1f));   // the crest ridge... down the nose
        if (t == Tier.DRAGON || t == Tier.GODLY) for (int z = 2; z < 9; z++) m.set(5, 10 + (z % 2), z, t == Tier.DRAGON ? mat(0xC02020) : metal(0xF0C850));
        return new Model(m, 5.5f, 0.5f, 5.5f);
    }

    private static Model body(ItemDef d, Tier t) {
        int[] c = armourColours(t, d);
        VoxModel m = new VoxModel(17, 14, 7);
        for (int y = 0; y < 14; y++) for (int x = 0; x < 17; x++) for (int z = 0; z < 7; z++) {
            boolean torso = x >= 3 && x <= 13 && (z == 0 || z == 6 || x == 3 || x == 13);
            boolean shoulder = y >= 10 && (x < 3 || x > 13) && (z == 0 || z == 6 || y == 13);
            boolean neck = y == 13 && x >= 6 && x <= 10;
            if ((torso || shoulder) && !neck) m.set(x, y, z, surface(c, x, y, z));
        }
        if (c[2] == 2) { for (int y = 2; y < 12; y++) m.set(8, y, 6, sh(c[0], 1.25f)); for (int x = 4; x <= 12; x++) m.set(x, 6, 6, sh(c[0], 0.85f)); }   // keel and fauld line
        for (int x = 3; x <= 13; x++) m.set(x, 0, 6, c[1]);   // the hem
        return new Model(m, 8.5f, 0.5f, 3.5f);
    }

    private static Model legs(ItemDef d, Tier t) {
        int[] c = armourColours(t, d);
        VoxModel m = new VoxModel(13, 16, 6);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 13; x++) for (int z = 0; z < 6; z++) {
            boolean waist = y >= 13;
            boolean leg = (x <= 4 || x >= 8) && (x == 0 || x == 4 || x == 8 || x == 12 || z == 0 || z == 5);
            if (waist && (x == 0 || x == 12 || z == 0 || z == 5) || !waist && leg) m.set(x, y, z, surface(c, x, y, z));
        }
        for (int x = 0; x < 13; x++) m.set(x, 15, 5, c[1]);   // the belt
        if (c[2] == 2) { m.set(3, 8, 5, sh(c[0], 1.3f)); m.set(9, 8, 5, sh(c[0], 1.3f)); }   // knee cops
        return new Model(m, 6.5f, 0.5f, 3f);
    }

    private static Model boots(ItemDef d) {
        VoxModel m = new VoxModel(11, 7, 9);
        int c = mat(d.color), sole = mat(0x2A1A10);
        for (int k = 0; k < 2; k++) {
            int x0 = k * 6;
            m.box(x0, 0, 0, x0 + 4, 0, 8, sole);
            m.box(x0, 1, 0, x0 + 4, 2, 8, c);
            m.box(x0, 3, 0, x0 + 4, 6, 3, c);
            m.box(x0, 6, 0, x0 + 4, 6, 3, sh(c, 1.3f));
        }
        return new Model(m, 5.5f, 0.5f, 4.5f);
    }

    private static Model gloves(ItemDef d) {
        VoxModel m = new VoxModel(11, 9, 3);
        int c = mat(d.color);
        for (int k = 0; k < 2; k++) {
            int x0 = k * 6;
            m.box(x0, 0, 1, x0 + 4, 4, 1, c);
            for (int f = 0; f < 4; f++) m.box(x0 + f, 5, 1, x0 + f, 7 - (f == 0 || f == 3 ? 1 : 0), 1, sh(c, 1.1f));
            m.box(x0, 0, 1, x0 + 4, 0, 1, sh(c, 0.7f));
        }
        return new Model(m, 5.5f, 0.5f, 1.5f);
    }

    /** A skillcape: gold cloth, the skill's own trim colour and its emblem stitched on the back. */
    private static Model cape(Skill s) {
        int trim = mat(switch (s) {
            case ATTACK -> 0xB02020; case STRENGTH -> 0x30A040; case DEFENSE -> 0x3050B0; case HEALTH -> 0xD04050; case ARCHERY -> 0x3A6A2A;
            case MAGIC -> 0x4A3AA0; case FAITH -> 0xE8E8E0; case MINING -> 0x5A5A60; case SMITHING -> 0x3A3A3A; case FISHING -> 0x3A7AB0;
            case COOKING -> 0x8A2A6A; case WOODCUTTING -> 0x6A8A3A; case FIREMAKING -> 0xE06020; case CRAFTING -> 0x8A6A3A;
            case AGILITY -> 0x2A2A6A; case THIEVING -> 0x4A2A5A; case HARVESTING -> 0x2A8A5A; case CONSTRUCTION -> 0xA08060;
        });
        VoxModel m = new VoxModel(15, 20, 2);
        int gold = mat(0xC8A030);
        for (int y = 0; y < 20; y++) {
            int half = 5 + (19 - y) / 6;
            for (int x = -half; x <= half; x++) {
                boolean edge = Math.abs(x) >= half - 0 || y == 0;
                m.set(7 + x, y, 0, edge ? trim : (y + x) % 7 == 0 ? sh(gold, 0.92f) : gold);
            }
        }
        // the emblem: a diamond in the trim colour with a dot of the skill's own seed
        int seed = s.ordinal();
        for (int dy = -3; dy <= 3; dy++) for (int dx = -3 + Math.abs(dy); dx <= 3 - Math.abs(dy); dx++) m.set(7 + dx, 11 + dy, 1, (dx + dy + seed) % 3 == 0 ? sh(trim, 1.4f) : trim);
        m.box(4, 19, 1, 10, 19, 1, trim);
        return new Model(m, 7.5f, 19.5f, 0.5f);
    }

    private static Model bag(ItemDef d) {
        int c = mat(d.color), dark = sh(c, 0.7f), strap = mat(0x3A2614), buckle = metal(0xB8A060);
        switch (d.id) {
            case "basic_backpack" -> {
                VoxModel m = new VoxModel(13, 15, 8);
                m.box(1, 0, 1, 11, 11, 6, c);
                m.box(1, 9, 1, 11, 13, 7, dark);           // the flap
                m.box(3, 2, 7, 9, 6, 7, c);                // the front pocket
                m.set(6, 9, 8 - 1, buckle);
                m.box(3, 0, 0, 3, 13, 0, strap); m.box(9, 0, 0, 9, 13, 0, strap);
                m.box(5, 14, 3, 7, 14, 4, strap);          // the carry loop
                return new Model(m, 6.5f, 0.5f, 3.5f);
            }
            case "leather_pouch" -> {
                VoxModel m = new VoxModel(9, 8, 5);
                m.box(1, 0, 1, 7, 5, 3, c);
                m.box(1, 4, 1, 7, 6, 4, dark);
                m.set(4, 4, 4, buckle);
                m.box(0, 6, 2, 8, 7, 2, strap);
                return new Model(m, 4.5f, 0.5f, 2.5f);
            }
            default -> {
                VoxModel m = new VoxModel(11, 13, 9);
                m.sphere(5.5f, 5f, 4.5f, 5f, c);
                for (int x = 0; x < 11; x++) for (int z = 0; z < 9; z++) if (m.get(x, 0, z) != 0) m.set(x, 0, z, dark);
                m.box(4, 9, 3, 6, 11, 5, c);
                m.box(3, 10, 3, 7, 10, 5, strap);          // the tie
                return new Model(m, 5.5f, 0.5f, 4.5f);
            }
        }
    }

    // ------------------------------------------------------------------ goods
    private static Model coins() {
        VoxModel m = new VoxModel(9, 6, 9);
        int g = metal(0xE0B840), edge = metal(0xA8862A);
        int[][] stacks = {{2, 2, 4}, {6, 3, 3}, {4, 6, 2}};
        for (int[] s : stacks) for (int y = 0; y < s[2]; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            if (x * x + z * z <= 5) m.set(s[0] + x, y, s[1] + z, (x * x + z * z >= 4) ? edge : g);
        return new Model(m, 4.5f, 0.5f, 4.5f);
    }

    private static Model logs(ItemDef d) {
        int bark = mat(d.id.equals("willow_logs") ? 0x5A5A30 : d.id.equals("oak_logs") ? 0x4A3420 : 0x6A4A2A);
        int wood = mat(d.color), ring = sh(wood, 0.8f);
        VoxModel m = new VoxModel(13, 24, 7);
        for (int k = 0; k < 2; k++) {
            float cx = k == 0 ? 3.5f : 9.5f, cz = 3.5f;
            int y0 = k == 0 ? 0 : 2, y1 = k == 0 ? 21 : 23;
            for (int y = y0; y <= y1; y++) for (int x = 0; x < 13; x++) for (int z = 0; z < 7; z++) {
                float dx = x + 0.5f - cx, dz = z + 0.5f - cz, r = (float) Math.sqrt(dx * dx + dz * dz);
                if (r > 3.1f) continue;
                boolean end = y == y0 || y == y1;
                m.set(x, y, z, end ? (r < 1 || (r > 1.8f && r < 2.4f) ? ring : wood) : r > 2.2f ? ((x + y) % 4 == 0 ? sh(bark, 0.8f) : bark) : wood);
            }
        }
        return new Model(m, 6.5f, 0.5f, 3.5f);
    }

    private static Model ore(ItemDef d) {
        boolean coal = d.id.equals("coal");
        int rock = mat(coal ? 0x222226 : 0x6A645E), fleck = coal ? mat(0x3A3A44) : metal(d.color);
        VoxModel m = new VoxModel(9, 8, 9);
        m.sphere(4.5f, 3.6f, 4.5f, 3.8f, rock);
        m.sphere(3f, 5f, 5.5f, 2.2f, rock);
        for (int x = 0; x < 9; x++) for (int y = 0; y < 8; y++) for (int z = 0; z < 9; z++)
            if (m.get(x, y, z) != 0 && astratto.util.Rng.hash(x, y, z, d.id.hashCode()) < 0.28f) m.set(x, y, z, fleck);
        return new Model(m, 4.5f, 0.5f, 4.5f);
    }

    private static Model bar(ItemDef d) {
        int c = metal(d.color);
        VoxModel m = new VoxModel(7, 16, 5);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 7; x++) for (int z = 0; z < 5; z++) {
            boolean top = z == 4;
            if (top && (x == 0 || x == 6 || y == 0 || y == 15)) continue;   // the bevel
            m.set(x, y, z, top ? sh(c, 1.18f) : c);
        }
        return new Model(m, 3.5f, 0.5f, 2.5f);
    }

    private static Model shrimp(ItemDef d) {
        boolean raw = d.id.startsWith("raw");
        int c = mat(raw ? 0xC89A90 : 0xF08A60), stripe = sh(c, 0.82f);
        VoxModel m = new VoxModel(10, 9, 4);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 1.2 * i / 11;
            int x = (int) Math.round(5 + 3.6 * Math.cos(a)), y = (int) Math.round(4 + 3.6 * Math.sin(a));
            int r = i < 3 ? 0 : 1;
            for (int dx = -r; dx <= r; dx++) for (int z = 1; z <= 2; z++) m.set(x + dx, y, z, i % 2 == 0 ? c : stripe);
        }
        m.set(9, 4, 1, sh(c, 1.2f)); m.set(9, 5, 2, sh(c, 1.2f));   // the tail fan
        return new Model(m, 5f, 0.5f, 2f);
    }

    private static Model fish(ItemDef d) {
        boolean burnt = d.id.equals("burnt_fish"), raw = d.id.startsWith("raw");
        int body = mat(burnt ? 0x2A2420 : raw ? 0x9AA4AC : 0xC08A60), belly = mat(burnt ? 0x1A1614 : raw ? 0xE0E0D8 : 0xE0B080), spot = mat(burnt ? 0x100C0A : raw ? 0x5A6A4A : 0x8A5A3A);
        VoxModel m = new VoxModel(9, 22, 5);
        for (int y = 0; y < 18; y++) {
            float u = (y - 9) / 9f;
            int half = Math.max(0, (int) Math.round(3.2 * Math.sqrt(Math.max(0, 1 - u * u))));
            for (int x = -half; x <= half; x++) for (int z = 1; z <= 3; z++) {
                int c = x < -half / 2 ? belly : (x + y) % 5 == 0 && raw ? spot : body;
                m.set(4 + x, y + 3, z, c);
            }
        }
        for (int k = 0; k < 4; k++) { m.set(4 - k, 3 - k, 2, body); m.set(4 + k, 3 - k, 2, body); }   // the tail
        m.set(5, 18, 3, mat(0x101010));   // the eye
        return new Model(m, 4.5f, 0.5f, 2.5f);
    }

    private static Model bread() {
        VoxModel m = new VoxModel(9, 16, 7);
        int crust = mat(0xC08848), top = mat(0xA86A30), score = mat(0xE8C890);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 9; x++) for (int z = 0; z < 7; z++) {
            float dx = (x - 4) / 4.5f, dz = z / 6.5f, dy = (y - 7.5f) / 8f;
            if (dx * dx + dz * dz + dy * dy * dy * dy > 1) continue;
            m.set(x, y, z, z >= 5 ? ((y % 4 == 1 && Math.abs(x - 4) < 3) ? score : top) : crust);
        }
        return new Model(m, 4.5f, 0.5f, 3.5f);
    }

    /** A joint of meat on its bone: marbled red raw, browned and glistening cooked, black burnt. */
    private static Model meat(ItemDef d) {
        boolean raw = d.id.equals("raw_meat"), burnt = d.id.equals("burnt_meat");
        int flesh = burnt ? mat(0x2A201C) : raw ? mat(0xB83A34) : mat(0x8A4A26), fat = burnt ? mat(0x3A2E28) : raw ? mat(0xEAD2C8) : mat(0xC08850);
        int edge = burnt ? mat(0x1A1412) : raw ? mat(0x9A2A26) : mat(0x6A3418), bone = mat(0xEAE4D4);
        VoxModel m = new VoxModel(11, 18, 7);
        for (int y = 0; y < 13; y++) for (int x = 0; x < 11; x++) for (int z = 0; z < 7; z++) {
            float dx = (x - 5) / 5.2f, dy = (y - 6.5f) / 6.8f, dz = (z - 3) / 3.4f;
            if (dx * dx + dy * dy + dz * dz > 1) continue;
            boolean rim = dx * dx + dy * dy + dz * dz > 0.72f;
            boolean marble = ((x * 7 + y * 3 + z * 5) % 11 == 0) && !burnt;
            m.set(x, y, z, rim ? (z == 0 || z == 6 ? flesh : edge) : marble ? fat : flesh);
        }
        for (int x = 1; x < 10; x++) if (!burnt) m.set(x, 12 - Math.abs(x - 5) / 2, 3, fat);   // the fat cap
        for (int y = 12; y < 17; y++) m.box(5, y, 3, 5, y, 3, bone);
        m.box(4, 16, 2, 6, 17, 4, bone);   // the knuckle
        return new Model(m, 5.5f, 15.5f, 3.5f);
    }

    /** A rough, flat hide: tan with a darker hair side and ragged edges. */
    private static Model hide() {
        VoxModel m = new VoxModel(20, 22, 2);
        int skin = mat(0xB89068), hair = mat(0x7A5634), hair2 = mat(0x6A4828);
        for (int y = 0; y < 22; y++) for (int x = 0; x < 20; x++) {
            float dx = (x - 9.5f) / 9.6f, dy = (y - 10.5f) / 10.8f;
            float r = dx * dx + dy * dy, notch = 0.12f * (float) Math.sin(x * 1.7 + y * 0.9) + 0.08f * (float) Math.sin(y * 2.3);
            // legs: four flaps at the corners
            boolean leg = (Math.abs(dx) > 0.55f && Math.abs(dy) > 0.6f && Math.abs(dx) < 1.02f && Math.abs(dy) < 1.02f);
            if (r > 0.82f + notch && !leg) continue;
            m.set(x, y, 0, (x + y) % 5 == 0 ? hair2 : hair);
            m.set(x, y, 1, (x * 3 + y) % 7 == 0 ? sh(skin, 0.9f) : skin);
        }
        return new Model(m, 9.5f, 0.5f, 1f);
    }

    /** A loose fleece: lumps of cream wool. */
    private static Model wool() {
        VoxModel m = new VoxModel(12, 10, 10);
        int[][] lumps = {{4, 4, 5}, {7, 4, 4}, {5, 6, 6}, {7, 6, 6}, {3, 3, 3}, {8, 3, 7}};
        for (int[] l : lumps) m.sphere(l[0] + 0.5f, l[1] + 0.5f, l[2] + 0.5f, 2.6f, mat(0xEDE8DA));
        for (int x = 0; x < 12; x++) for (int y = 0; y < 10; y++) for (int z = 0; z < 10; z++) if (m.get(x, y, z) != 0 && (x * 5 + y * 3 + z) % 6 == 0) m.set(x, y, z, mat(0xD8D0BC));
        return new Model(m, 6f, 0.5f, 5f);
    }

    private static Model egg() {
        VoxModel m = new VoxModel(7, 10, 7);
        for (int x = 0; x < 7; x++) for (int y = 0; y < 10; y++) for (int z = 0; z < 7; z++) {
            float dx = (x - 3) / 3.3f, dz = (z - 3) / 3.3f, dy = (y - 4.3f) / (y > 4.3f ? 5.4f : 4.6f);
            if (dx * dx + dy * dy + dz * dz <= 1) m.set(x, y, z, (x * 3 + y * 5 + z) % 13 == 0 ? mat(0xE0D2B4) : mat(0xEFE6D0));
        }
        return new Model(m, 3.5f, 0.5f, 3.5f);
    }

    /** A wooden pail with iron hoops, full of milk. */
    private static Model milk() {
        VoxModel m = new VoxModel(13, 14, 13);
        int wood = mat(0x9A7246), hoop = metal(0x6A6A70), milk = mat(0xF4F2EC);
        for (int y = 0; y < 12; y++) {
            float r = 5.2f + y * 0.08f;
            for (int x = 0; x < 13; x++) for (int z = 0; z < 13; z++) {
                float d = (float) Math.hypot(x - 6, z - 6);
                if (d > r) continue;
                if (d > r - 1.2f || y == 0) m.set(x, y, z, y == 2 || y == 9 ? hoop : (Math.round(Math.atan2(z - 6, x - 6) * 4) % 2 == 0 ? wood : sh(wood, 0.88f)));
                else if (y == 10) m.set(x, y, z, milk);
            }
        }
        for (int i = 0; i <= 12; i++) { double t = Math.PI * i / 12; m.set((int) Math.round(6 - 5.6 * Math.cos(t)), 12 + (int) Math.round(1.6 * Math.sin(t)), 6, hoop); }   // the bail
        return new Model(m, 6.5f, 0.5f, 6.5f);
    }

    private static Model feather() {
        VoxModel m = new VoxModel(7, 18, 1);
        for (int y = 0; y < 17; y++) {
            int half = y < 2 ? 0 : (int) Math.round(2.6 * Math.sin(Math.PI * (y - 1) / 16.5));
            for (int x = -half; x <= half; x++) m.set(3 + x + (y > 12 ? 1 : 0), y, 0, x == 0 ? mat(0xC8C0B0) : (y + x) % 4 == 0 ? mat(0xDAD6CC) : mat(0xF2F0EA));
        }
        return new Model(m, 3.5f, 0.5f, 0.5f);
    }

    /** A tied sack of grain, a spill of it at the mouth. */
    private static Model feedSack() {
        VoxModel m = new VoxModel(14, 18, 10);
        int sack = mat(0xC8A868), seam = mat(0xA88A50), grain = mat(0xE0C060), tie = mat(0x6A4A2A);
        for (int y = 0; y < 15; y++) for (int x = 0; x < 14; x++) for (int z = 0; z < 10; z++) {
            float w = y < 12 ? 1f : 1f - (y - 11) * 0.22f;
            float dx = (x - 6.5f) / (6.8f * w), dz = (z - 4.5f) / (4.8f * w);
            if (dx * dx + dz * dz > 1) continue;
            m.set(x, y, z, x == 6 || x == 7 ? seam : (x + y) % 6 == 0 ? sh(sack, 0.9f) : sack);
        }
        m.box(5, 15, 3, 8, 15, 6, tie);
        m.box(6, 16, 4, 7, 17, 5, sack);
        m.box(5, 17, 4, 8, 17, 5, grain);
        return new Model(m, 7f, 0.5f, 5f);
    }

    private static Model berries() {
        VoxModel m = new VoxModel(9, 8, 9);
        int[][] b = {{3, 2, 3}, {6, 2, 4}, {4, 2, 6}, {4, 4, 4}, {6, 4, 6}};
        for (int[] p : b) m.sphere(p[0] + 0.5f, p[1] + 0.5f, p[2] + 0.5f, 1.7f, mat(0xC02030));
        for (int[] p : b) m.set(p[0], p[1] + 1, p[2] + 1, mat(0xF06070));
        m.box(4, 6, 4, 4, 7, 4, mat(0x3A6A2A));
        return new Model(m, 4.5f, 0.5f, 4.5f);
    }

    private static Model leaf() {
        VoxModel m = new VoxModel(9, 13, 2);
        for (int y = 0; y < 12; y++) {
            int half = (int) Math.round(3.6 * Math.sin(Math.PI * (y + 1) / 13));
            for (int x = -half; x <= half; x++) m.set(4 + x, y + 1, 0, x == 0 ? mat(0x7AC060) : mat(0x50A040));
        }
        m.set(4, 0, 0, mat(0x3A6A2A));
        return new Model(m, 4.5f, 0.5f, 0.5f);
    }

    private static Model flax() {
        VoxModel m = new VoxModel(7, 26, 3);
        for (int k = 0; k < 4; k++) {
            int x = 1 + k + (k % 2), h = 20 + (k * 3) % 5;
            for (int y = 0; y < h; y++) m.set(x, y, 1, y % 5 == 0 ? mat(0x9A9A50) : mat(0xB8B070));
            m.set(x, h, 1, mat(0x6080E0)); m.set(x, h + 1, 1, mat(0x8098F0));
        }
        m.box(1, 5, 0, 5, 6, 2, mat(0x8A6A3A));   // the tie
        return new Model(m, 3.5f, 0.5f, 1.5f);
    }

    private static Model bowstring() {
        VoxModel m = new VoxModel(9, 9, 2);
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 20;
            float r = 3.8f - i * 0.05f;
            m.set((int) Math.round(4 + r * Math.cos(a)), (int) Math.round(4 + r * Math.sin(a)), i / 20, mat(0xE8E0C8));
        }
        return new Model(m, 4.5f, 0.5f, 1f);
    }

    private static Model plank(ItemDef d) {
        int c = mat(d.color), grain = sh(c, 0.85f);
        VoxModel m = new VoxModel(7, 32, 2);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 7; x++) for (int z = 0; z < 2; z++) m.set(x, y, z, (x + y / 7) % 3 == 0 ? grain : c);
        m.set(2, 8, 1, sh(c, 0.6f)); m.set(4, 22, 1, sh(c, 0.6f));   // knots
        return new Model(m, 3.5f, 0.5f, 1f);
    }

    private static Model bones(ItemDef d) {
        boolean big = d.id.equals("big_bones");
        int len = big ? 22 : 15, c = mat(d.color), k = mat(Tex.shade(d.color, 0.9f));
        VoxModel m = new VoxModel(7, len, 5);
        for (int y = 2; y < len - 2; y++) m.box(3, y, 2, big ? 4 : 3, y, big ? 3 : 2, c);
        for (int e : new int[]{1, len - 2}) { m.sphere(2f, e + 0.5f, 2.5f, big ? 1.9f : 1.5f, k); m.sphere(5f, e + 0.5f, 2.5f, big ? 1.9f : 1.5f, k); }
        return new Model(m, 3.5f, 0.5f, 2.5f);
    }

    private static Model shafts() {
        VoxModel m = new VoxModel(7, 22, 3);
        for (int k = 0; k < 4; k++) for (int y = k % 2; y < 20 + k % 2; y++) m.set(1 + k + k / 2, y, 1, mat(0xC8A06A));
        m.box(0, 9, 0, 6, 10, 2, mat(0x8A6A3A));
        return new Model(m, 3.5f, 0.5f, 1.5f);
    }

    private static Model tinderbox() {
        VoxModel m = new VoxModel(9, 6, 7);
        m.box(0, 0, 0, 8, 3, 6, mat(0x806040));
        m.box(0, 4, 0, 8, 4, 6, mat(0x6A4E34));
        m.box(3, 5, 2, 5, 5, 4, metal(0x8A8A90));   // the striker
        return new Model(m, 4.5f, 0.5f, 3.5f);
    }

    private static Model hammer() {
        VoxModel m = new VoxModel(9, 18, 5);
        haft(m, 4, 0, 14, 2, mat(0x8A6034));
        m.box(1, 13, 1, 7, 16, 3, metal(0x707078));
        m.box(0, 14, 1, 0, 15, 3, metal(0x8A8A92));
        return new Model(m, 4.5f, 3.5f, 2.5f);
    }

    private static Model knife() {
        VoxModel m = new VoxModel(3, 16, 3);
        for (int y = 0; y < 6; y++) m.set(1, y, 1, mat(0x4A3020));
        for (int y = 6; y < 15; y++) m.set(1, y, 1, y > 12 ? metal(0xD0D0D8) : metal(0xB0B0B8));
        m.set(0, 6, 1, metal(0x8A8A90)); m.set(2, 6, 1, metal(0x8A8A90));
        return new Model(m, 1.5f, 2.5f, 1.5f);
    }

    private static Model rod() {
        VoxModel m = new VoxModel(7, 52, 3);
        for (int y = 0; y < 50; y++) m.set(2, y, 1, y < 8 ? mat(0x4A3020) : mat(0x9A7A4A));
        m.box(3, 9, 1, 5, 11, 1, metal(0x8A8A90));   // the reel
        for (int y = 12; y < 50; y += 6) m.set(3, y, 1, metal(0xB0B0B8));   // the line guides
        for (int y = 30; y < 51; y++) m.set(4, y, 1, mat(0xE8E8E0));   // the line, hanging
        return new Model(m, 2.5f, 4.5f, 1.5f);
    }

    private static Model lockpick() {
        VoxModel m = new VoxModel(7, 12, 2);
        for (int y = 0; y < 11; y++) { m.set(2, y, 0, metal(0x909098)); m.set(4, y + 1, 0, metal(0x80808A)); }
        m.set(1, 10, 0, metal(0x909098)); m.set(5, 11, 0, metal(0x80808A)); m.set(3, 0, 1, mat(0x5A3A20));
        return new Model(m, 3.5f, 0.5f, 1f);
    }

    private static Model warrant() {
        VoxModel m = new VoxModel(9, 20, 5);
        m.box(1, 2, 2, 7, 17, 2, mat(0xC6AC76));
        for (int y : new int[]{2, 17}) for (int x = 0; x < 9; x++) for (int dy = -2; dy <= 2; dy++) for (int z = 0; z < 5; z++) if (dy * dy + (z - 2) * (z - 2) <= 5) m.set(x, y + dy, z, mat(0xB39968));
        for (int y = 6; y <= 13; y += 2) m.box(2, y, 3, y == 12 ? 4 : 6, y, 3, mat(0x64503A));
        m.box(3, 4, 3, 5, 6, 4, mat(0x8E3528));
        return new Model(m, 4.5f, 0, 2.5f);
    }

    private static Model letter() {
        VoxModel m = new VoxModel(13, 9, 2);
        m.box(0, 0, 0, 12, 8, 0, mat(0xE8DCB0));
        for (int x = 0; x < 7; x++) { m.set(x, 8 - x * 8 / 12, 1, mat(0xC8BC90)); m.set(12 - x, 8 - x * 8 / 12, 1, mat(0xC8BC90)); }
        m.box(5, 3, 1, 7, 5, 1, mat(0xB02020));   // the wax seal
        return new Model(m, 6.5f, 0.5f, 0.5f);
    }

    private static Model lamp() {
        VoxModel m = new VoxModel(17, 9, 9);
        int gold = metal(0xF0C850), dark = metal(0xC09830);
        for (int x = 2; x < 12; x++) for (int y = 0; y < 6; y++) for (int z = 0; z < 9; z++) {
            float dx = (x - 7) / 5f, dy = y / 5.5f, dz = (z - 4) / 4f;
            if (dx * dx + dy * dy + dz * dz <= 1) m.set(x, y, z, y == 0 ? dark : gold);
        }
        for (int i = 0; i < 5; i++) m.set(12 + i, 2 + i / 2, 4, gold);   // the spout
        m.box(7, 6, 4, 7, 7, 4, dark); m.set(7, 8, 4, mat(0xFFE080));
        for (int y = 1; y < 5; y++) m.set(1, y, 4, gold);   // the handle
        return new Model(m, 7.5f, 0.5f, 4.5f);
    }

    private static Model key() {
        VoxModel m = new VoxModel(7, 16, 2);
        int c = metal(0xC0A040);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 7; x++) { float dx = x - 3, dy = y - 2; float r = (float) Math.sqrt(dx * dx + dy * dy); if (r <= 3 && r >= 1.4f) m.set(x, y + 11, 0, c); }
        for (int y = 0; y < 11; y++) m.set(3, y, 0, c);
        m.box(4, 0, 0, 5, 1, 0, c); m.box(4, 3, 0, 4, 4, 0, c);
        return new Model(m, 3.5f, 0.5f, 1f);
    }

    private static Model lump(ItemDef d) {
        VoxModel m = new VoxModel(8, 8, 8);
        m.sphere(4, 4, 4, 3.6f, mat(d.color));
        return new Model(m, 4f, 0.5f, 4f);
    }
}
