package rpg.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.item.ItemDef.DamageType;
import rpg.item.ItemDef.Family;
import rpg.item.ItemDef.Slot;
import rpg.skill.Skill;

/**
 * Every item definition in the devmap's content set. Stats are tuning data derived from tier power, not balance.
 * Requirement mapping (the GDD fixes swords to Strength and armor to Defense; the rest is prototype configuration):
 * swords Strength; axes, maces, daggers Attack; armor, helmets, shields Defense; bows Archery; staffs and wands Magic;
 * holy symbols Faith.
 */
public final class Items {
    private Items() {}

    private static final Map<String, ItemDef> BY_ID = new LinkedHashMap<>();

    public static ItemDef get(String id) {
        ItemDef d = BY_ID.get(id);
        if (d == null) throw new IllegalArgumentException("no item " + id);
        return d;
    }
    public static ItemDef find(String id) { return BY_ID.get(id); }
    public static Collection<ItemDef> all() { return BY_ID.values(); }

    private static ItemDef def(String id, String name, int value, int color, String examine) {
        ItemDef d = new ItemDef(id, name);
        d.value = value; d.color = color; d.examine = examine;
        if (BY_ID.put(id, d) != null) throw new IllegalStateException("duplicate item " + id);
        return d;
    }
    private static ItemDef stack(String id, String name, int value, int color, String examine) { ItemDef d = def(id, name, value, color, examine); d.stackable = true; return d; }
    private static ItemDef drink(String id, String name, int value, int faith, int color, String examine) {
        ItemDef d = def(id, name, value, color, examine);
        d.restoresFaith = faith;
        return d;
    }

    /** A tool held in the hand to work a gathering spot (gamefixes: the right tool, wielded and used). */
    private static ItemDef tool(String id, String name, int value, int color, String examine, Skill skill) {
        ItemDef d = def(id, name, value, color, examine);
        d.slot = Slot.MAIN; d.family = Family.TOOL; d.toolSkill = skill; d.toolPower = 1; d.reach = 1.2f;
        d.damage = 0.2f; d.impact = 4; d.damageType = DamageType.BLUNT;
        return d;
    }
    private static ItemDef garment(String id, String name, Slot slot) {
        ItemDef item=def(id,name,25,0xA89476,"A fitted linen garment."); item.slot=slot;item.family=Family.ARMOR;
        item.reqSkill=Skill.DEFENSE;item.reqLevel=1;item.armor=0.5f;return item;
    }
    private static ItemDef food(String id, String name, int value, int heal, int color) { ItemDef d = def(id, name, value, color, "Restores " + heal + " HP."); d.heal = heal; return d; }

    // ------------------------------------------------------------------ resources and tools
    public static final ItemDef COINS = stack("coins", "Gold coins", 1, 0xE8C040, "Gold coins (G): the currency of the realm.");
    public static final ItemDef BOUNTY_SCROLL = def("bounty_scroll", "Bounty scroll", 0, 0xC6AC76, "A signed warrant. The map shows the last position known to the watch, not a live tracking spell.");
    public static final ItemDef LOGS = def("logs", "Logs", 4, 0x8A6034, "Logs cut from a tree.");
    public static final ItemDef OAK_LOGS = def("oak_logs", "Oak logs", 10, 0x9C7444, "Logs cut from an oak.");
    public static final ItemDef WILLOW_LOGS = def("willow_logs", "Willow logs", 16, 0x7A7040, "Logs cut from a willow.");
    public static final ItemDef COPPER_ORE = def("copper_ore", "Copper ore", 5, 0xC07040, "Ore with copper in it.");
    public static final ItemDef TIN_ORE = def("tin_ore", "Tin ore", 5, 0xA8A8A0, "Ore with tin in it.");
    public static final ItemDef STONE_BLOCK = def("stone_block", "Quarry stone block", 9, 0x92928A, "Usable masonry cut from a supported quarry face.");
    public static final ItemDef CLAY = def("clay", "Clay", 5, 0x9C7258, "Workable clay from a dry bank.");
    public static final ItemDef CLOTH = def("cloth_roll", "Linen cloth", 12, 0xB6A78C, "Woven flax for a tailor's patterns.");
    public static final ItemDef CLOTH_HOOD = garment("cloth_hood", "Linen hood", Slot.HEAD);
    public static final ItemDef CLOTH_TUNIC = garment("cloth_tunic", "Linen tunic", Slot.BODY);
    public static final ItemDef IRON_ORE = def("iron_ore", "Iron ore", 17, 0x7A5A48, "Ore with iron in it.");
    public static final ItemDef COAL = def("coal", "Coal", 22, 0x222226, "Fuel for smelting steel.");
    public static final ItemDef BRONZE_BAR = def("bronze_bar", "Bronze bar", 12, 0xB0703A, "A bar of bronze.");
    public static final ItemDef IRON_BAR = def("iron_bar", "Iron bar", 28, 0x6E6E72, "A bar of iron.");
    public static final ItemDef STEEL_BAR = def("steel_bar", "Steel bar", 50, 0x9CA4AE, "A bar of steel.");
    public static final ItemDef RAW_SHRIMP = def("raw_shrimp", "Raw shrimp", 3, 0xC89080, "Needs cooking.");
    public static final ItemDef SHRIMP = food("shrimp", "Shrimp", 6, 3, 0xE8A070);
    public static final ItemDef RAW_CRAB = def("raw_crab", "Raw crab", 14, 0xB04A30, "Needs cooking.");
    public static final ItemDef CRAB = food("crab", "Crab meat", 26, 8, 0xE07A50);
    public static final ItemDef RAW_TROUT = def("raw_trout", "Raw trout", 12, 0x9AA0A8, "Needs cooking.");
    public static final ItemDef TROUT = food("trout", "Trout", 20, 7, 0xC08A60);
    public static final ItemDef BURNT_FISH = def("burnt_fish", "Burnt fish", 1, 0x302820, "Oops.");
    public static final ItemDef BREAD = food("bread", "Bread", 8, 5, 0xD8A860);
    public static final ItemDef BERRIES = food("berries", "Redberries", 4, 2, 0xC02030);
    public static final ItemDef GUAM = def("guam", "Guam leaf", 8, 0x50A040, "A common herb.");
    public static final ItemDef FLAX = def("flax", "Flax", 3, 0xB8B070, "Spun into bowstrings.");
    public static final ItemDef BOWSTRING = def("bowstring", "Bowstring", 8, 0xE0E0C8, "For stringing bows.");
    public static final ItemDef PLANK = def("plank", "Plank", 10, 0xB08850, "Sawn timber.");
    public static final ItemDef OAK_PLANK = def("oak_plank", "Oak plank", 24, 0xC09858, "Sawn oak.");
    // animal products (GDD 2.7 catalog: Meat 38, Rawhide 105, Wool 45, Egg 9, Milk 18, Feather 3, Animal Feed 15; simple cooked food 55)
    public static final ItemDef RAW_MEAT = def("raw_meat", "Raw meat", 38, 0xB8463C, "Needs cooking.");
    public static final ItemDef COOKED_MEAT = food("cooked_meat", "Cooked meat", 55, 6, 0x8A4A2A);
    public static final ItemDef BURNT_MEAT = def("burnt_meat", "Burnt meat", 1, 0x2E2420, "Charred through.");
    public static final ItemDef RAWHIDE = def("rawhide", "Rawhide", 105, 0xA8805A, "An untanned hide.");
    public static final ItemDef WOOL = def("wool", "Wool", 45, 0xEDE8DA, "Shorn from a sheep.");
    public static final ItemDef EGG = def("egg", "Egg", 9, 0xEFE6D0, "A hen's egg.");
    public static final ItemDef MILK = def("milk", "Bucket of milk", 18, 0xF4F2EC, "Fresh cow's milk.");
    public static final ItemDef FEATHER = stack("feather", "Feather", 3, 0xF2F0EA, "Light and soft.");
    public static final ItemDef ANIMAL_FEED = def("animal_feed", "Animal feed", 15, 0xC8A858, "A sack of grain for livestock.");
    public static final ItemDef BONES = def("bones", "Bones", 2, 0xE8E4D8, "Bury them for Faith.");
    public static final ItemDef BIG_BONES = def("big_bones", "Big bones", 6, 0xF0ECE0, "Bury them for Faith.");
    public static final ItemDef ARROW_SHAFT = stack("arrow_shaft", "Arrow shaft", 1, 0xA88050, "Needs a head.");
    public static final ItemDef TINDERBOX = def("tinderbox", "Tinderbox", 2, 0x806040, "Lights fires.");
    public static final ItemDef HAMMER = def("hammer", "Hammer", 4, 0x707070, "For smithing and building.");
    public static final ItemDef SNARE = def("snare", "Rabbit snare", 12, 0x8D7852, "A reusable snare for real wildlife crossings.");
    public static final ItemDef KNIFE = def("knife", "Knife", 3, 0xB0B0B8, "For crafting.");
    public static final ItemDef FISHING_ROD = tool("fishing_rod", "Fishing rod", 5, 0x806040, "Wield it and cast at a fishing spot for trout.", Skill.FISHING);
    public static final ItemDef FISHING_NET = tool("small_fishing_net", "Small fishing net", 5, 0xB8A878, "Wield it and cast it at a fishing spot for shrimp.", Skill.FISHING);
    public static final ItemDef CRAB_CAGE = tool("crab_cage", "Crab cage", 12, 0x8A6A44, "Wield it and set it at a fishing spot for crabs.", Skill.FISHING);
    public static final ItemDef LOCKPICK = def("lockpick", "Lockpick", 20, 0x909098, "For locks that are not yours.");
    public static final ItemDef SEALED_LETTER = def("sealed_letter", "Sealed letter", 0, 0xE8DCB0, "A test-quest delivery.");
    public static final ItemDef XP_LAMP = def("xp_lamp", "Experience lamp", 0, 0xF0C850, "Rub it for XP in a skill of your choice.");
    public static final ItemDef CHANCERY_KEY = def("chancery_key", "Shed key", 0, 0xC0A040, "Opens the skill shed's locked store.");

    // ------------------------------------------------------------------ ranged and magic
    public static final ItemDef SHORTBOW = bow("shortbow", "Shortbow", 1, 10, 30, 0x9A7040);
    public static final ItemDef OAK_BOW = bow("oak_shortbow", "Oak shortbow", 15, 16, 80, 0xB08A50);
    public static final ItemDef WILLOW_BOW = bow("willow_shortbow", "Willow shortbow", 30, 24, 160, 0x8A8048);
    public static final ItemDef BRONZE_ARROWS = arrows("bronze_arrows", "Bronze arrows", 1, 4, 1, 0xB0703A);
    public static final ItemDef IRON_ARROWS = arrows("iron_arrows", "Iron arrows", 15, 9, 3, 0x6E6E72);
    public static final ItemDef STAFF = staff("apprentice_staff", "Apprentice staff", 1, 40, 0x7A5A9A, Family.STAFF);
    public static final ItemDef FIRE_STAFF = staff("fire_staff", "Fire staff", 10, 400, 0xC04020, Family.STAFF);
    public static final ItemDef WAND = staff("wand", "Oak wand", 1, 30, 0x9A7A50, Family.WAND);
    public static final ItemDef HOLY_SYMBOL = holy("holy_symbol", "Holy symbol", 1, 60);
    public static final ItemDef BLESSED_SYMBOL = holy("blessed_symbol", "Blessed symbol", 30, 600);
    /** Faith restored by drinking (GDD 2.5): religious wine, and the restorative potion. */
    public static final ItemDef RELIGIOUS_WINE = drink("religious_wine", "Religious wine", 40, 6, 0x7A1E3A, "Consecrated wine: drinking it restores 6 Faith points.");
    /** Alcohol and tobacco (GDD 3.5: content tags for low-Honor corpse loot; their effects are open): an ale warms, a pipe's worth of tobacco is a trade good. */
    public static final ItemDef ALE = food("ale", "Ale", 6, 2, 0xB07A2A);
    public static final ItemDef TOBACCO = def("pipe_tobacco", "Pipe tobacco", 18, 0x6A4A28, "A twist of dark pipe tobacco. Worth something to the right buyer.");
    public static final ItemDef RESTORATIVE = drink("restorative_potion", "Restorative potion", 140, 20, 0x5AB8C8, "A restorative: it restores 20 Faith points.");

    // ------------------------------------------------------------------ tier equipment
    public static final ItemDef WOODEN_SWORD;
    public static final List<ItemDef> TIER_GEAR = new ArrayList<>();

    static {
        WOODEN_SWORD = def("wooden_sword", "Wooden sword", 5, 0x9A7040, "A practice sword.");
        weapon(WOODEN_SWORD, Family.SWORD, null, 1, 6, 8, 1.15f, 1f, DamageType.SLASH);
        for (Tier t : Tier.values()) {
            String k = t.name().toLowerCase();
            int p = t.power;
            armor(def(k + "_helm", t.title + " helm", 20 * p * p, t.color, t.title + " head armor."), Slot.HEAD, t, 1.5f * p + 1);
            armor(def(k + "_body", t.title + " body", 50 * p * p, t.color, t.title + " body armor."), Slot.BODY, t, 4f * p + 2);
            armor(def(k + "_legs", t.title + " legs", 35 * p * p, t.color, t.title + " leg armor."), Slot.LEGS, t, 2.5f * p + 1);
            // every tier carries all thirteen melee families and the five ranged ones (GDD 2.4: tier labels are progression
            // categories - a Leather spear is a hardened-wood one); Leather and Chainmail make no shields
            // the heft: the heavy late metals are a hair slower in the hand
            float heft = 1f + Math.max(0, p - 6) * 0.006f;
            weapon(def(k + "_sword", t.title + " sword", 30 * p * p, t.color, "A " + t.title.toLowerCase() + " sword. Quick, one-handed."), Family.SWORD, t, t.requirement, 6 + 5 * p, 10 + 4 * p, 1.25f, heft, DamageType.SLASH);
            ItemDef great = def(k + "_2hsword", t.title + " 2h sword", 55 * p * p, t.color, "A " + t.title.toLowerCase() + " greatsword: two hands, long reach, a slow and heavy cut.");
            weapon(great, Family.SWORD, t, t.requirement, 9 + 7.5f * p, 20 + 6 * p, 1.7f, heft, DamageType.SLASH);
            great.twoHand = true;
            // a woodcutting axe: one hand, a short haft, made for felling (gamefixes); a battleaxe is the fighting one
            weapon(def(k + "_axe", t.title + " axe", 18 * p * p, t.color, "A woodcutting axe: one-handed, for felling trees."), Family.AXE, t, t.requirement, 4 + 3 * p, 12 + 3 * p, 1.0f, heft, DamageType.SLASH);
            ItemDef battle = def(k + "_battleaxe", t.title + " battleaxe", 48 * p * p, t.color, "A two-handed " + t.title.toLowerCase() + " battleaxe: brutal, slow, and no use on a tree.");
            weapon(battle, Family.AXE, t, t.requirement, 9 + 7 * p, 26 + 7 * p, 1.45f, heft, DamageType.SLASH);
            battle.twoHand = true;
            weapon(def(k + "_mace", t.title + " mace", 26 * p * p, t.color, "Good against armor."), Family.MACE, t, t.requirement, 5 + 4.5f * p, 22 + 6 * p, 1.15f, heft, DamageType.BLUNT);
            weapon(def(k + "_dagger", t.title + " dagger", 14 * p * p, t.color, "Short and quick."), Family.DAGGER, t, t.requirement, 4 + 3.5f * p, 6 + 2 * p, 0.8f, heft, DamageType.PIERCE);
            weapon(def(k + "_pickaxe", t.title + " pickaxe", 22 * p * p, t.color, "Mines rock: the better the metal, the faster."), Family.PICKAXE, t, t.requirement, 4 + 3 * p, 12 + 3 * p, 1.15f, heft, DamageType.PIERCE);
            weapon(def(k + "_longsword", t.title + " longsword", 40 * p * p, t.color, "A long, one-handed blade: reach for a little speed."), Family.LONGSWORD, t, t.requirement, 7 + 5.6f * p, 12 + 4.5f * p, 1.5f, heft * 1.1f, DamageType.SLASH);
            weapon(def(k + "_scimitar", t.title + " scimitar", 32 * p * p, t.color, "A curved blade, quick and wicked."), Family.SCIMITAR, t, t.requirement, 5.5f + 4.8f * p, 9 + 3.5f * p, 1.2f, heft * 0.88f, DamageType.SLASH);
            weapon(def(k + "_warhammer", t.title + " warhammer", 36 * p * p, t.color, "A heavy hammer that staggers whatever it hits."), Family.WARHAMMER, t, t.requirement, 6 + 5.2f * p, 30 + 8 * p, 1.2f, heft * 1.25f, DamageType.BLUNT);
            ItemDef spear = def(k + "_spear", t.title + " spear", 30 * p * p, t.color, "A long spear: keep them at its point.");
            weapon(spear, Family.SPEAR, t, t.requirement, 6.5f + 5 * p, 12 + 4 * p, 2.0f, heft * 1.05f, DamageType.PIERCE);
            spear.twoHand = true;
            weapon(def(k + "_claws", t.title + " claws", 28 * p * p, t.color, "Blades over the knuckles: close, fast, vicious."), Family.CLAWS, t, t.requirement, 4.5f + 4 * p, 6 + 2 * p, 0.6f, heft * 0.75f, DamageType.SLASH);
            ItemDef hal = def(k + "_halberd", t.title + " halberd", 50 * p * p, t.color, "An axe-blade and a spike on a long pole: the longest reach there is.");
            weapon(hal, Family.HALBERD, t, t.requirement, 9 + 7 * p, 24 + 7 * p, 2.3f, heft * 1.3f, DamageType.SLASH);
            hal.twoHand = true;
            // ranged: a crossbow (bolts), and four thrown weapons that go from the stack in the hand
            ItemDef xb = def(k + "_crossbow", t.title + " crossbow", 40 * p * p, t.color, "Loads a bolt, aims, looses: slow to reload, hard-hitting.");
            xb.slot = Slot.MAIN; xb.family = Family.CROSSBOW; xb.tier = t; xb.reqSkill = Skill.ARCHERY; xb.reqLevel = t.requirement;
            xb.damage = (8 + 4 * p) * rpg.combat.CombatMath.HP_SCALE; xb.impact = 16 + 4 * p; xb.damageType = DamageType.PIERCE; xb.reach = 0.9f;
            TIER_GEAR.add(xb);
            String[][] thrown = {{"dart", "darts", "A stack of darts: quick little throws."}, {"knife", "throwing knives", "Balanced throwing knives."},
                    {"tomahawk", "tomahawks", "Throwing axes: they bite deep."}, {"javelin", "javelins", "Javelins: heavy, far-flying."}};
            float[][] tstats = {{1.5f, 0.6f, 4}, {2.4f, 0.8f, 6}, {3.6f, 1.15f, 12}, {4.6f, 1.35f, 16}};
            for (int i = 0; i < 4; i++) {
                ItemDef th = stack(k + "_" + thrown[i][0], t.title + " " + thrown[i][1], Math.max(1, 2 * p * p * (i + 1) / 2), t.color, thrown[i][2]);
                th.slot = Slot.MAIN; th.family = Family.THROWN; th.thrown = thrown[i][0]; th.tier = t; th.reqSkill = Skill.ARCHERY; th.reqLevel = t.requirement;
                th.damage = (tstats[i][0] + tstats[i][0] * 0.55f * p) * rpg.combat.CombatMath.HP_SCALE; th.speed = tstats[i][1]; th.impact = tstats[i][2] + 2 * p; th.damageType = i >= 2 ? DamageType.SLASH : DamageType.PIERCE;
                if (i == 3) th.damageType = DamageType.PIERCE;
                TIER_GEAR.add(th);
            }
            ItemDef bolts = stack(k + "_bolts", t.title + " bolts", Math.max(1, p), t.color, "Crossbow bolts.");
            bolts.slot = Slot.AMMO; bolts.family = Family.BOLT; bolts.tier = t; bolts.reqSkill = Skill.ARCHERY; bolts.reqLevel = t.requirement; bolts.damage = (3 + 2.5f * p) * rpg.combat.CombatMath.HP_SCALE; bolts.damageType = DamageType.PIERCE;
            TIER_GEAR.add(bolts);
            if (find(k + "_arrows") == null) {
                ItemDef ar = arrows(k + "_arrows", t.title + " arrows", t.requirement, 2 + 2.2f * p, Math.max(1, p), t.color);
                ar.tier = t;
            } else find(k + "_arrows").tier = t;
            if (t.ordinal() <= Tier.STEEL.ordinal()) {
                // brutal arrows: blunt-headed, heavy-hitting, Leather to Steel only (GDD 2.4)
                ItemDef br = arrows(k + "_brutal_arrows", t.title + " brutal arrows", t.requirement, 3 + 2.6f * p, Math.max(1, p * 2), t.color);
                br.tier = t; br.damageType = DamageType.BLUNT; br.examine = "Heavy, blunt-headed arrows that knock the wind out of things.";
            }
            if (!t.metal()) continue;
            ItemDef sh = def(k + "_shield", t.title + " shield", 40 * p * p, t.color, "Raise it to block.");
            sh.slot = Slot.OFF; sh.family = Family.SHIELD; sh.tier = t; sh.reqSkill = Skill.DEFENSE; sh.reqLevel = t.requirement; sh.armor = 2f * p + 1; sh.guard = 0.72f;
            TIER_GEAR.add(sh);
        }
        // the brutal and the plain arrows of every tier are made above; the bronze and iron ones are the originals
        // tools carry their power for gathering, and their tier gates it by the gathering skill
        for (ItemDef d : BY_ID.values()) {
            if (d.family == Family.AXE && d.tier != null && !d.twoHand) { d.toolPower = d.tier.power; d.toolSkill = Skill.WOODCUTTING; d.reqSkill = Skill.WOODCUTTING; }
            if (d.family == Family.PICKAXE && d.tier != null) { d.toolPower = d.tier.power; d.toolSkill = Skill.MINING; d.reqSkill = Skill.MINING; }
        }
        ItemDef shield = def("wooden_shield", "Wooden shield", 10, 0x8A6034, "A plank shield.");
        shield.slot = Slot.OFF; shield.family = Family.SHIELD; shield.reqSkill = Skill.DEFENSE; shield.reqLevel = 1; shield.armor = 2; shield.guard = 0.6f;
        ItemDef boots = def("leather_boots", "Leather boots", 6, 0x6A4424, "Sturdy boots."); boots.slot = Slot.FEET; boots.family = Family.ARMOR; boots.armor = 1; boots.reqSkill = Skill.DEFENSE; boots.reqLevel = 1;
        ItemDef gloves = def("leather_gloves", "Leather gloves", 6, 0x7A5030, "Sturdy gloves."); gloves.slot = Slot.HANDS; gloves.family = Family.ARMOR; gloves.armor = 1; gloves.reqSkill = Skill.DEFENSE; gloves.reqLevel = 1;
        for (Skill s : Skill.ALL) {
            ItemDef cape = def("cape_" + s.name().toLowerCase(), s.title + " cape", 99000, 0xC8A030, "Mastery of " + s.title + ".");
            cape.slot = Slot.CAPE; cape.family = Family.ARMOR; cape.armor = 3; cape.reqSkill = s; cape.reqLevel = 100;
            if (s == Skill.FAITH) cape.faithBonus = 8;
        }
        // worn containers: each a grid of its own (GDD 2.4 Backpack slot; gamefixes: equipped containers add separate grids)
        ItemDef pack = def("basic_backpack", "Basic backpack", 650, 0x7A5A34, "A leather backpack: a second grid on your back.");
        pack.slot = Slot.BACK; pack.family = Family.BAG; pack.bagW = 8; pack.bagH = 6;
        ItemDef pouch = def("leather_pouch", "Leather pouch", 60, 0x8A6A40, "A belt pouch: a small grid at your hip.");
        pouch.slot = Slot.BELT; pouch.family = Family.BAG; pouch.bagW = 4; pouch.bagH = 2;
        ItemDef sack = def("sack", "Sack", 25, 0xB8A070, "A rough sack: carried, it holds a grid of its own.");
        sack.family = Family.BAG; sack.bagW = 5; sack.bagH = 4;
        jewellery();
        shapes();
        BOUNTY_SCROLL.gw = 1; BOUNTY_SCROLL.gh = 2;
    }

    /**
     * Jewellery (GDD 2.4 Ring and Necklace slots, 2.7 references): gold worked in a mould at the crafting table, set
     * with a cut gem or plain. Worn, a ring or a necklace is worth carrying and worth stealing, and is what a high-Honor
     * corpse is likelier to hold (GDD 3.5); the one with a stated use is the dodgy necklace, a thief's charm.
     */
    private static void jewellery() {
        def("gold_bar", "Gold bar", 1800, 0xE8C048, "A bar of gold. Moulded at a crafting table, it becomes jewellery.");
        tool("ring_mould", "Ring mould", 75, 0x8A8680, "Gold poured into it makes a ring.", Skill.CRAFTING);
        tool("necklace_mould", "Necklace mould", 85, 0x8A8680, "Gold poured into it makes a necklace.", Skill.CRAFTING);
        tool("amulet_mould", "Amulet mould", 85, 0x8A8680, "Gold poured into it makes an amulet.", Skill.CRAFTING);
        tool("chisel", "Chisel", 12, 0x9A9AA0, "For cutting gems.", Skill.CRAFTING);
        Object[][] gems = {{"sapphire", "Sapphire", 0x2A55D8, 250}, {"emerald", "Emerald", 0x22A050, 500}, {"ruby", "Ruby", 0xC81830, 1000}, {"diamond", "Diamond", 0xE8F4FF, 2000}};
        for (Object[] g : gems) {
            String id = (String) g[0], nm = (String) g[1];
            int col = (int) g[2], val = (int) g[3];
            def("uncut_" + id, "Uncut " + nm.toLowerCase(), (int) (val * 1.1f), col, "A rough " + nm.toLowerCase() + ". A chisel would bring out its colour.");
            def(id, nm, val, col, "A cut " + nm.toLowerCase() + ".");
        }
        jewel("gold_ring", "Gold ring", Slot.RING, 210, 0xE8C048, 0, "A plain gold band.");
        jewel("gold_necklace", "Gold necklace", Slot.NECK, 290, 0xE8C048, 0, "A fine gold chain.");
        jewel("gold_amulet", "Gold amulet", Slot.NECK, 320, 0xE8C048, 0, "A gold amulet on a chain.");
        for (Object[] g : gems) {
            String id = (String) g[0], nm = (String) g[1];
            int col = (int) g[2], val = (int) g[3];
            jewel(id + "_ring", nm + " ring", Slot.RING, 210 + val, 0xE8C048, col, "A gold ring set with a " + nm.toLowerCase() + ".");
            jewel(id + "_necklace", nm + " necklace", Slot.NECK, 290 + val, 0xE8C048, col, "A gold chain with a " + nm.toLowerCase() + " pendant.");
            jewel(id + "_amulet", nm + " amulet", Slot.NECK, 320 + val, 0xE8C048, col, "A gold amulet with a " + nm.toLowerCase() + " at its heart.");
        }
        ItemDef dodgy = jewel("dodgy_necklace", "Dodgy necklace", Slot.NECK, 1800, 0x7A5A34, 0xE8E0C8, "Teeth on a cord. A thief's charm: a fumbled pickpocket sometimes goes unnoticed.");
        dodgy.reqSkill = Skill.THIEVING; dodgy.reqLevel = 20;
    }

    private static ItemDef jewel(String id, String name, Slot slot, int value, int metal, int gem, String examine) {
        ItemDef d = def(id, name, value, metal, examine);
        d.slot = slot; d.family = Family.JEWEL; d.gemColor = gem;
        return d;
    }

    public static final ItemDef BACKPACK = get("basic_backpack"), POUCH = get("leather_pouch"), SACK = get("sack");

    /**
     * Every item's footprint in a grid (cells across x down, lying straight) and its weight in kg: an item's bulk and
     * its weight both matter (gamefixes). Long things are long (a sword is one cell by four, a fishing rod one by five),
     * broad things broad (body armor three by three), small things one cell.
     */
    private static void shapes() {
        Object[][] table = {
                {"coins", 1, 1, 0f}, {"logs", 3, 1, 2.5f}, {"oak_logs", 3, 1, 3f}, {"willow_logs", 3, 1, 2.8f},
                {"copper_ore", 1, 1, 2f}, {"tin_ore", 1, 1, 2f}, {"iron_ore", 1, 1, 2.4f}, {"coal", 1, 1, 1.5f},
                {"bronze_bar", 2, 1, 1.5f}, {"iron_bar", 2, 1, 1.8f}, {"steel_bar", 2, 1, 1.8f},
                {"raw_shrimp", 1, 1, 0.1f}, {"shrimp", 1, 1, 0.1f}, {"raw_trout", 2, 1, 0.5f}, {"trout", 2, 1, 0.4f}, {"burnt_fish", 2, 1, 0.3f},
                {"bread", 2, 1, 0.4f}, {"berries", 1, 1, 0.1f}, {"guam", 1, 1, 0.05f}, {"flax", 1, 2, 0.1f}, {"bowstring", 1, 1, 0.02f},
                {"plank", 4, 1, 3f}, {"oak_plank", 4, 1, 4f}, {"bones", 2, 1, 0.5f}, {"big_bones", 3, 1, 1.5f}, {"arrow_shaft", 1, 2, 0.01f},
                {"tinderbox", 1, 1, 0.2f}, {"hammer", 1, 2, 0.8f}, {"knife", 1, 2, 0.3f}, {"fishing_rod", 1, 5, 0.6f}, {"small_fishing_net", 2, 2, 0.5f}, {"crab_cage", 2, 2, 1.6f},
                {"raw_crab", 1, 1, 0.4f}, {"crab", 1, 1, 0.3f}, {"religious_wine", 1, 2, 0.8f}, {"restorative_potion", 1, 1, 0.3f}, {"lockpick", 1, 1, 0.05f},
                {"sealed_letter", 1, 1, 0.02f}, {"xp_lamp", 2, 1, 0.8f}, {"chancery_key", 1, 1, 0.1f},
                {"shortbow", 1, 4, 0.9f}, {"oak_shortbow", 1, 4, 1f}, {"willow_shortbow", 1, 4, 1f}, {"bronze_arrows", 1, 3, 0.03f}, {"iron_arrows", 1, 3, 0.03f},
                {"apprentice_staff", 1, 5, 1.6f}, {"fire_staff", 1, 5, 1.8f}, {"wand", 1, 2, 0.3f}, {"holy_symbol", 1, 1, 0.3f}, {"blessed_symbol", 1, 1, 0.3f},
                {"wooden_sword", 1, 3, 0.9f}, {"wooden_shield", 2, 3, 3f}, {"leather_boots", 2, 2, 1.2f}, {"leather_gloves", 2, 1, 0.4f},
                {"basic_backpack", 3, 3, 1.4f}, {"leather_pouch", 2, 1, 0.3f}, {"sack", 2, 2, 0.4f},
                {"raw_meat", 2, 1, 0.5f}, {"cooked_meat", 2, 1, 0.4f}, {"burnt_meat", 2, 1, 0.3f}, {"rawhide", 2, 2, 2.5f}, {"wool", 1, 1, 0.3f},
                {"egg", 1, 1, 0.06f}, {"milk", 2, 2, 2f}, {"feather", 1, 1, 0.005f}, {"animal_feed", 2, 2, 5f}};
        for (Object[] r : table) { ItemDef d = find((String) r[0]); if (d == null) continue; d.gw = (int) r[1]; d.gh = (int) r[2]; d.weight = (float) r[3]; }
        for (ItemDef d : BY_ID.values()) {
            d.conductive = d.tier != null && d.tier != Tier.LEATHER && d.tier != Tier.GRANITE || d.family == Family.JEWEL || d.family == Family.SYMBOL;
            if (d.tier == null && !d.id.startsWith("cape_")) continue;
            float metal = d.tier != null && d.tier.metal() ? 1f + d.tier.power * 0.08f : 0.5f;
            switch (d.family) {
                case SWORD -> { d.gw = 1; d.gh = d.twoHand ? 5 : 4; d.weight = (d.twoHand ? 3.2f : 1.4f) * metal; }
                case LONGSWORD -> { d.gw = 1; d.gh = 5; d.weight = 1.8f * metal; }
                case SCIMITAR -> { d.gw = 1; d.gh = 4; d.weight = 1.3f * metal; }
                case WARHAMMER -> { d.gw = 2; d.gh = 3; d.weight = 2.8f * metal; }
                case SPEAR -> { d.gw = 1; d.gh = 6; d.weight = 2.2f * metal; }
                case CLAWS -> { d.gw = 2; d.gh = 2; d.weight = 0.9f * metal; }
                case HALBERD -> { d.gw = 1; d.gh = 6; d.weight = 3.8f * metal; }
                case CROSSBOW -> { d.gw = 3; d.gh = 3; d.weight = 3.2f; }
                case THROWN -> { d.gw = 1; d.gh = d.thrown.equals("javelin") ? 4 : 2; d.weight = d.thrown.equals("javelin") ? 0.8f : d.thrown.equals("tomahawk") ? 0.6f : 0.08f; }
                case BOLT, ARROW -> { d.gw = 1; d.gh = 3; d.weight = 0.03f; }
                case AXE -> { d.gw = 1; d.gh = d.twoHand ? 4 : 2; d.weight = (d.twoHand ? 3.4f : 1.3f) * metal; }
                case MACE -> { d.gw = 2; d.gh = 3; d.weight = 2.2f * metal; }
                case DAGGER -> { d.gw = 1; d.gh = 2; d.weight = 0.5f * metal; }
                case PICKAXE -> { d.gw = 3; d.gh = 2; d.weight = 2.4f * metal; }
                case SHIELD -> { d.gw = 2; d.gh = 3; d.weight = 5f * metal; }
                case ARMOR -> {
                    if (d.slot == null) break;
                    switch (d.slot) {
                        case HEAD -> { d.gw = 2; d.gh = 2; d.weight = d.tier != null && d.tier.metal() ? 2.5f * metal : 0.8f; }
                        case BODY -> { d.gw = 3; d.gh = 3; d.weight = d.tier == Tier.CHAINMAIL ? 9f : d.tier != null && d.tier.metal() ? 14f * metal : 4f; }
                        case LEGS -> { d.gw = 2; d.gh = 3; d.weight = d.tier != null && d.tier.metal() ? 8f * metal : 3f; }
                        case CAPE -> { d.gw = 2; d.gh = 3; d.weight = 1.5f; }
                        default -> { }
                    }
                }
                default -> { }
            }
        }
    }

    public static ItemDef cape(Skill s) { return get("cape_" + s.name().toLowerCase()); }
    public static ItemDef tierItem(Tier t, String kind) { return find(t.name().toLowerCase() + "_" + kind); }

    private static void weapon(ItemDef d, Family f, Tier t, int req, float dmg, float impact, float reach, float speed, DamageType type) {
        d.slot = Slot.MAIN; d.family = f; d.tier = t; d.damage = dmg * rpg.combat.CombatMath.HP_SCALE; d.impact = impact; d.reach = reach; d.speed = speed; d.damageType = type;
        d.reqSkill = f == Family.SWORD || f == Family.LONGSWORD ? Skill.STRENGTH : Skill.ATTACK;
        // the bonuses: a sword's Strength bonus is the tier's (GDD 2.4, locked); other families take a share of it by
        // their nature (open in the GDD); the Attack bonus favours the quick families
        if (t != null) {
            float fam = switch (f) { case SWORD -> 1f; case LONGSWORD -> 1.05f; case SCIMITAR -> 0.9f; case DAGGER -> 0.45f; case CLAWS -> 0.55f; case MACE -> 1.1f; case WARHAMMER -> 1.2f;
                case AXE -> 0.95f; case SPEAR -> 0.95f; case HALBERD -> 1.15f; case PICKAXE -> 0.55f; default -> 0.8f; };
            d.strengthBonus = t.swordStrength * fam;
            d.attackBonus = switch (f) { case DAGGER, CLAWS -> 12; case SCIMITAR -> 10; case SWORD -> 8; case LONGSWORD -> 6; case SPEAR -> 5; case AXE -> 3; case MACE -> 2; case WARHAMMER, HALBERD -> 1; default -> 4; } + t.power * 0.5f;
        }
        d.reqLevel = req;
        d.guard = f == Family.DAGGER || f == Family.CLAWS ? 0.3f : f == Family.SPEAR || f == Family.HALBERD || f == Family.LONGSWORD ? 0.55f : 0.45f;
        TIER_GEAR.add(d);
    }

    private static void armor(ItemDef d, Slot s, Tier t, float armor) {
        d.slot = s; d.family = Family.ARMOR; d.tier = t; d.reqSkill = Skill.DEFENSE; d.reqLevel = t.requirement; d.armor = armor;
        TIER_GEAR.add(d);
    }

    private static ItemDef bow(String id, String name, int req, float dmg, int value, int color) {
        ItemDef d = def(id, name, value, color, "Fires arrows.");
        d.slot = Slot.MAIN; d.family = Family.BOW; d.reqSkill = Skill.ARCHERY; d.reqLevel = req; d.damage = dmg * rpg.combat.CombatMath.HP_SCALE; d.impact = 8 + dmg / 2; d.damageType = DamageType.PIERCE;
        return d;
    }

    private static ItemDef arrows(String id, String name, int req, float dmg, int value, int color) {
        ItemDef d = stack(id, name, value, color, "Ammunition for bows.");
        d.slot = Slot.AMMO; d.family = Family.ARROW; d.reqSkill = Skill.ARCHERY; d.reqLevel = req; d.damage = dmg * rpg.combat.CombatMath.HP_SCALE; d.damageType = DamageType.PIERCE;
        return d;
    }

    private static ItemDef staff(String id, String name, int req, int value, int color, Family f) {
        ItemDef d = def(id, name, value, color, f == Family.WAND ? "Casts the selected spell from the off hand." : "Casts the selected spell.");
        d.magicDamageBonusPct = f == Family.STAFF ? 16 : 8;
        d.slot = f == Family.WAND ? Slot.OFF : Slot.MAIN; d.family = f; d.reqSkill = Skill.MAGIC; d.reqLevel = req; d.damage = (f == Family.STAFF ? 4 : 2) * rpg.combat.CombatMath.HP_SCALE; d.impact = 10; d.reach = 1.3f; d.speed = 1.1f; d.damageType = DamageType.BLUNT;
        if (id.equals("fire_staff")) { d.damage = 8 * rpg.combat.CombatMath.HP_SCALE; d.maxCharges = 30; d.examine = "The Fire staff: it casts Fire Blast, a charge at a time (recharge it with coal)."; }
        return d;
    }

    private static ItemDef holy(String id, String name, int req, int value) {
        ItemDef d = def(id, name, value, 0xE8D890, "Cast blessings with the off hand.");
        d.slot = Slot.OFF; d.family = Family.SYMBOL; d.reqSkill = Skill.FAITH; d.reqLevel = req; d.armor = 1;
        d.faithBonus = req >= 30 ? 12 : 5;
        return d;
    }
}
