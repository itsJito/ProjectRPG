package rpg.world.shadowveil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.Ids;
import rpg.ai.Npc;
import rpg.ai.Profession;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.world.DevMap;
import rpg.world.WorldMap;

/**
 * (0.4.3, gamefixes: every NPC defined in the lore registered and spawned in their kingdom with their documented
 * occupation, faction, workplace, home and bed) ShadowVeil's people, from {@code data/shadowveil_census.json} - the
 * 'NPCs Present' lists of projectrpg.txt, one entry each. The roster people the devmap shares take the lore identities
 * the data gives their Ids constants; everyone else of the lore is made for ShadowVeil alone. Each is set down at home
 * with their work assigned:
 * <ul>
 * <li>a home - a registered building - with a bed planned inside its reserved interior (the registry keeps it for the
 *     interiors of 0.4.4), and a home stage: open ground before its doorway where they arrive home and, by night, go in;</li>
 * <li>a workplace - a registered structure - and a work stage: before its doorway, or in the open place itself (a field,
 *     a yard, a quarry, a mine's mouth); the shop's counter, the watch's post and the trades' sites follow it.</li>
 * </ul>
 * People sharing a doorway stand side by side along it, so no two wait on one cell. The one exception to the home rule
 * is the bounty hunter, who has none.
 */
public final class Census {
    private Census() {}

    /** Makes a person for ShadowVeil alone (the AI system's own maker: their body, look and standing). */
    public interface Maker { Npc make(long id, String name, Npc.Role role, String title, boolean female); }

    /** What the census gave one person. */
    public record Assignment(long id, String name, String title, String kingdom, String homeId, String workId,
                             float[] homeStage, float[] workStage, int[] bed, boolean madeHere) {}

    private static final Map<Long, Assignment> BY_ID = new LinkedHashMap<>();
    public static Map<Long, Assignment> all() { return BY_ID; }
    public static Assignment of(long id) { return BY_ID.get(id); }
    /** (0.4.4) Each person's post inside their workplace {x, y, z}, its kind (teller, bar, altar, station:ANVIL...), and the customer's place before it. */
    private static final Map<Long, float[]> POSTS = new LinkedHashMap<>(), CUSTOMERS = new LinkedHashMap<>();
    private static final Map<Long, String> POST_KINDS = new LinkedHashMap<>();
    public static float[] post(long id) { return POSTS.get(id); }
    public static String postKind(long id) { return POST_KINDS.get(id); }
    public static float[] customerSpot(long id) { return CUSTOMERS.get(id); }
    /** A structure's name (its registry entry's), or its id. */
    public static String placeName(String id) {
        Terrain t = ShadowVeil.terrain();
        Architecture.Structure s = t == null || t.arch == null ? null : t.arch.byId(id);
        return s == null ? id : s.name;
    }

    private static Map<String, Object> data() { return Atlas.get().map("census"); }

    /**
     * Applies the census to the roster (ShadowVeil only): renames and re-dresses the shared people, makes the rest, and
     * sets everyone at home with their work. Called once the transfer rules have placed the roster.
     */
    public static void apply(rpg.Rpg g, List<Npc> npcs, Maker maker) {
        BY_ID.clear(); POSTS.clear(); CUSTOMERS.clear(); POST_KINDS.clear();
        if (!WorldMap.shadowveil()) return;
        java.util.Set<Architecture.Fitting> postsTaken = new java.util.HashSet<>();
        Terrain t = ShadowVeil.terrain();
        Architecture arch = t == null ? null : t.arch;
        Registry reg = arch == null ? null : arch.registry;
        Map<String, Object> stage = Atlas.map(data(), "stage");
        float out = (float) Atlas.d(stage, "out", 2), spacing = (float) Atlas.d(stage, "spacing", 1.4);
        Map<String, Npc> byBody = new LinkedHashMap<>();
        for (Npc n : npcs) byBody.put(Settle.idName(n.id), n);
        Map<String, Integer> slots = new LinkedHashMap<>();
        List<Map<String, Object>> people = everyone();
        for (int k = 0; k < people.size(); k++) {
            Map<String, Object> p = people.get(k);
            String title = Atlas.s(p, "title", ""), kingdom = Atlas.s(p, "kingdom", DevMap.WILDS);
            boolean female = Boolean.TRUE.equals(p.get("female"));
            // (0.4.4, gamefixes: NPCs need not keep a strict mononym - a Sir or a title may go with the name) the honorific
            // their rank or calling carries (the data's), before the one name: Sir Lancelot, Count Dracula, Father Gregory
            String name = honorific(title, Atlas.s(p, "role", ""), female) + Atlas.s(p, "name", "?");
            Npc.Role role = Npc.Role.valueOf(Atlas.s(p, "role", "VILLAGER"));
            String body = Atlas.s(p, "body", null);
            Npc n = body == null ? null : byBody.get(body);
            boolean made = n == null;
            if (made) {
                n = maker.make(Ids.SV_PEOPLE + k, name, role, title, female);
                kit(g, n, Atlas.s(p, "profession", null));
            } else {
                n.name = name;
                // (0.4.7, gamefixes: NPCs still kept the devmap's callings - King Arthur sawing planks as its carpenter) the
                // census's calling stands, but for the roles the quests and the law are built on; a trade that no longer
                // fits the calling is laid down
                if (n.role != role && !KEEP_ROLES.contains(n.role)) {
                    n.role = role;
                    if (n.profession != null && n.profession.family != role) n.profession = null;
                }
                if (n.look != null && n.look.female != female) { n.look = rpg.actor.Appearance.of(rpg.ai.AiSystem.group(n.role), n.id * 7919L + k, female); n.fitBody(); }
            }
            // the calling: the data's profession where it belongs to the person's role (their work follows it)
            String prof = Atlas.s(p, "profession", null);
            if (prof != null) try { Profession pr = Profession.valueOf(prof); if (pr.family == n.role) pr.assign(n); } catch (IllegalArgumentException ignored) { }
            n.title = title + (kingdom.equals("Wilds") ? "" : " of " + kingdom);
            // (0.4.5, gamefixes: the clothing update) what they wear of the outfits
            dress(g, n, p);
            // (0.4.7, gamefixes: King Arthur made his own) a person's own levels, arms and look, over their role's
            own(g, n, p);
            // (0.4.7, gamefixes: NPCs wearing gear must have the stats to wear and use it)
            n.meetGear();
            // home and work, and their stand-ins
            String homeId = (String) p.get("home"), workId = (String) p.get("work");
            Registry.Entry home = reg == null || homeId == null ? null : reg.byId(homeId), work = reg == null || workId == null ? null : reg.byId(workId);
            float[] homeStage = home == null ? null : stageOf(arch, home, slot(slots, home.id), out, spacing);
            float[] workStage = work == null ? homeStage : stageOf(arch, work, slot(slots, work.id + "#work"), out, spacing);
            if (homeStage == null) homeStage = workStage;
            // each stand-in where a body can really stand (the ground clear two high over something solid)
            homeStage = standable(g, homeStage); workStage = standable(g, workStage);
            if (homeStage == null) { float[] w = ShadowVeil.place("waystation"); homeStage = workStage = w != null ? w : new float[]{n.x, n.z}; }
            // (0.4.4, gamefixes: every NPC a home, a bed and a workplace inside, replacing 0.4.3's stand-ins) their own bed in
            // their home's bedrooms, and their post inside their workplace - the counter, the bar, the altar, the anvil
            rpg.objects.Furniture bedF = null;
            Rooms.Site hs = home == null ? null : Rooms.site(home.id);
            if (hs != null) for (rpg.objects.Furniture f : hs.beds) if (f.owner == 0) { bedF = f; break; }
            if (bedF == null && hs != null && !Boolean.FALSE.equals(Atlas.map(data(), "stage").get("guestBedsForResidents"))) for (rpg.objects.Furniture f : hs.guestBeds) if (f.owner == 0 && hs.guestBeds.size() > 1) { bedF = f; hs.guestBeds.remove(f); hs.beds.add(f); break; }
            int[] bed;
            if (bedF != null) { bedF.owner = n.id; bed = new int[]{(int) Math.floor(bedF.x), bedF.floor, (int) Math.floor(bedF.z)}; }
            else bed = home == null ? null : new int[]{(int) Math.floor(homeStage[0]), (int) Math.floor(ShadowVeil.standY(homeStage[0], homeStage[1])), (int) Math.floor(homeStage[1])};
            n.x = homeStage[0]; n.z = homeStage[1]; n.y = ShadowVeil.standY(n.x, n.z);
            if (bedF != null) { n.homeX = bedF.x; n.homeZ = bedF.z; n.homeY = bedF.floor; }
            else { n.homeX = homeStage[0]; n.homeZ = homeStage[1]; n.homeY = Float.NaN; }
            n.postX = workStage[0]; n.postZ = workStage[1]; n.dutyX = workStage[0]; n.dutyZ = workStage[1]; n.postY = Float.NaN;
            Rooms.Site ws = work == null ? null : Rooms.site(work.id);
            // (0.4.7) the keys of the locked doors where they live and where they work
            for (Rooms.Site ks : new Rooms.Site[]{hs, ws}) if (ks != null) for (rpg.objects.Door kd : ks.doors) if (kd.locked) kd.keepers.add(n.id);
            Architecture.Fitting post = ws == null ? null : pickPost(ws, n, p, postsTaken);
            if (post != null) {
                postsTaken.add(post);
                n.postX = post.x(); n.postZ = post.z(); n.postY = post.y(); n.dutyX = post.x(); n.dutyZ = post.z();
                workStage = new float[]{post.x(), post.z()};
                POSTS.put(n.id, new float[]{post.x(), post.y(), post.z()});
                POST_KINDS.put(n.id, post.type().substring(5));
                if (post.useX() != 0 || post.useZ() != 0) CUSTOMERS.put(n.id, new float[]{post.useX(), post.useZ()});
            }
            n.spawnX = n.x; n.spawnZ = n.z; n.chaseOffX = n.x; n.chaseOffZ = n.z;
            // whose law: their kingdom's (the outlaws stay the wilds')
            boolean outlaw = n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE;
            n.homeJurisdiction = outlaw || kingdom.equals("Wilds") || ShadowVeil.realm(kingdom) == null ? ShadowVeil.jurisdiction(n.x, n.z) : kingdom;
            if (outlaw) n.homeJurisdiction = DevMap.WILDS;
            if (reg != null) {
                if (home != null) reg.residents(List.of(n.name), home.id, true);
                if (work != null) work.workers.add(n.name);
                if (home != null && bed != null) home.plannedBeds.add(new int[]{bed[0], bed[1], bed[2]});
            }
            if (bedF != null) bedF.name = n.name + "'s bed";
            BY_ID.put(n.id, new Assignment(n.id, n.name, n.title, kingdom, home == null ? null : home.id, work == null ? null : work.id, homeStage, workStage, bed, made));
        }
    }

    /** (0.4.4) The honorific before a person's name, with its space ("" for none): by their title's words, else by their role. */
    static String honorific(String title, String role, boolean female) {
        Map<String, Object> h = Atlas.map(data(), "honorifics");
        for (var e : Atlas.map(h, "byTitle").entrySet()) if ((" " + title + " ").contains(" " + e.getKey() + " ") && e.getValue() instanceof List<?> l && l.size() == 2) return l.get(female ? 1 : 0) + " ";
        if (Atlas.map(h, "byRole").get(role) instanceof List<?> l && l.size() == 2) return l.get(female ? 1 : 0) + " ";
        return "";
    }

    /** (0.4.7) The devmap roles a census person taking over a body keeps (the quests', the law's and the fence's run on them). */
    static final java.util.Set<Npc.Role> KEEP_ROLES = java.util.EnumSet.of(Npc.Role.QUEST_GIVER, Npc.Role.CONTACT, Npc.Role.THIEF, Npc.Role.UNDERTAKER, Npc.Role.LAWMAN, Npc.Role.FENCE);

    /** (0.4.4) The lore's roll and, after it, the people added to keep the new jails, banks and graveyards. */
    public static List<Map<String, Object>> everyone() {
        List<Map<String, Object>> l = new ArrayList<>(Atlas.list(data(), "people"));
        l.addAll(Atlas.list(data(), "additions"));
        return l;
    }

    /** A census entry's person id: their roster Ids constant's, else their own on ShadowVeil (Ids.SV_PEOPLE + their index). */
    public static long idOf(Map<String, Object> p, int index) {
        String body = Atlas.s(p, "body", null);
        if (body != null) try { return Ids.class.getField(body).getLong(null); } catch (ReflectiveOperationException ignored) { }
        return Ids.SV_PEOPLE + index;
    }

    /** The nearest cell to a point (within six) where a body stands: solid under it, open two high over it. */
    static float[] standable(rpg.Rpg g, float[] p) {
        if (p == null) return null;
        int bx = (int) Math.floor(p[0]), bz = (int) Math.floor(p[1]);
        for (int r = 0; r <= 6; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int x = bx + dx, z = bz + dz;
            int y = (int) Math.floor(ShadowVeil.standY(x + 0.5f, z + 0.5f) + 1e-3);
            int below = g.vox.block(x, y - 1, z), at = g.vox.block(x, y, z), above = g.vox.block(x, y + 1, z);
            if (below == rpg.world.Blocks.AIR || below == rpg.world.Blocks.WATER || at != rpg.world.Blocks.AIR || above != rpg.world.Blocks.AIR) continue;
            return new float[]{x + 0.5f, z + 0.5f};
        }
        return p;
    }

    private static int slot(Map<String, Integer> slots, String key) { int s = slots.getOrDefault(key, 0); slots.put(key, s + 1); return s; }

    /**
     * A structure's stand-in for its people: open ground a little before its main doorway (or its gate), the k-th person
     * sharing it a step to one side or the other; an open place's own middle; a mine's mouth. {x, z}.
     */
    static float[] stageOf(Architecture arch, Registry.Entry e, int k, float out, float spacing) {
        Architecture.Structure s = arch.byId(e.id);
        float lateral = (k == 0 ? 0 : (k % 2 == 1 ? 1 : -1) * ((k + 1) / 2)) * spacing;
        if (!e.entrances.isEmpty()) {
            Registry.Entrance en = e.entrances.get(0);
            float dx = en.facing() == 'e' ? 1 : en.facing() == 'w' ? -1 : 0, dz = en.facing() == 's' ? 1 : en.facing() == 'n' ? -1 : 0;
            float along = (en.width() - 1) / 2f;
            float x = en.x() + 0.5f + (dz != 0 ? along : 0) + dx * out, z = en.z() + 0.5f + (dx != 0 ? along : 0) + dz * out;
            // the side-step runs along the wall the doorway is in
            x += dz != 0 ? lateral : 0; z += dx != 0 ? lateral : 0;
            return ShadowVeil.dry(x, z);
        }
        if (s != null) {
            float[] mouth = s.points.get("mouth");
            if (mouth != null) return ShadowVeil.dry(mouth[0] + lateral, mouth[1]);
            return ShadowVeil.dry(s.cx() + lateral, s.cz() + (k % 3) - 1);
        }
        return ShadowVeil.dry((e.x0 + e.x1 + 1) / 2f + lateral, (e.z0 + e.z1 + 1) / 2f);
    }

    /**
     * (0.4.4) The post a person takes in their workplace: the first free one of the kinds their calling works at (data
     * 'posts': by title words, by calling, by role - a banker the teller's place, a cook the range, a priest the altar,
     * a smith the anvil), or none (their work is out of doors: a field, a mine, the watch's round).
     */
    private static Architecture.Fitting pickPost(Rooms.Site ws, Npc n, Map<String, Object> person, java.util.Set<Architecture.Fitting> taken) {
        Map<String, Object> table = Atlas.map(data(), "posts");
        List<Object> want = new ArrayList<>();
        String title = Atlas.s(person, "title", "").toLowerCase(java.util.Locale.ROOT);
        for (var e : Atlas.map(table, "byTitle").entrySet()) if (title.contains(e.getKey()) && e.getValue() instanceof List<?> l) want.addAll(l);
        String prof = Atlas.s(person, "profession", null);
        if (prof != null) want.addAll(Atlas.arr(Atlas.map(table, "byProfession"), prof));
        want.addAll(Atlas.arr(Atlas.map(table, "byRole"), n.role.name()));
        for (Object w : want) {
            String kind = "post:" + w;
            for (Architecture.Fitting f : ws.posts) if (!taken.contains(f) && (f.type().equals(kind) || kind.endsWith(":") && f.type().startsWith(kind) || f.type().startsWith(kind + ":"))) return f;
        }
        return null;
    }

    /**
     * (0.4.5, gamefixes: the clothing update - NPCs wear the new outfits, and everyone but the townsfolk wears gear that can
     * be seen and looted) The outfit a person wears (the data's 'outfits': by name, by home, by calling, by role, then by
     * kingdom), its pieces put on in every slot the kit left empty or filled only with plain cloth - a guard's helm and a
     * knight's plate stay. Where the outfit has two pieces for a slot (the Desert's legs and robes), the higher ranks take
     * the robes. Every piece is minted: it is in the world, worn, and goes with them if they fall.
     */
    private static void dress(rpg.Rpg g, Npc n, Map<String, Object> person) {
        Map<String, Object> table = Atlas.map(data(), "outfits");
        if (table.isEmpty()) return;
        String id = outfitFor(table, n, person);
        rpg.item.Apparel.Outfit o = id == null ? null : rpg.item.Apparel.outfit(id);
        if (o == null) return;
        boolean robes = Atlas.arr(table, "robesFor").contains(id);
        java.util.Set<ItemDef.Slot> done = java.util.EnumSet.noneOf(ItemDef.Slot.class);
        for (ItemDef d : o.pieces()) {
            ItemDef.Slot s = d.slot;
            if (done.contains(s)) continue;
            // two for one slot: the robes for the ranks that wear them, else the first
            if (s == ItemDef.Slot.LEGS && o.pieces().stream().filter(x -> x.slot == ItemDef.Slot.LEGS).count() > 1 && robes != "robe".equals(rpg.render.Garments.shape(d))) continue;
            ItemDef worn = n.eq.def(s);
            if (worn != null && worn.wear == null && (worn.tier != null || worn.armor >= 1)) continue;   // the kit's armour stays
            n.eq.set(s, new ItemStack(d, 1));
            g.state.mint(d, 1);
            done.add(s);
        }
        n.fitBody();
    }

    /** Which outfit a person wears (the data's order: name, home, calling, role, kingdom), or null. */
    static String outfitFor(Map<String, Object> table, Npc n, Map<String, Object> person) {
        String v = Atlas.s(Atlas.map(table, "people"), Atlas.s(person, "name", ""), null);
        if (v == null) v = Atlas.s(Atlas.map(table, "byHome"), Atlas.s(person, "home", ""), null);
        if (v == null) v = Atlas.s(Atlas.map(table, "byProfession"), Atlas.s(person, "profession", ""), null);
        if (v == null) v = Atlas.s(Atlas.map(table, "byRole"), n.role.name(), null);
        if (v == null && !Atlas.arr(table, "notInArms").contains(n.role.name())) {
            Map<String, Object> k = Atlas.map(Atlas.map(table, "byKingdom"), Atlas.s(person, "kingdom", ""));
            if (!k.isEmpty()) {
                String title = Atlas.s(person, "title", "").toLowerCase(java.util.Locale.ROOT);
                for (var e : Atlas.map(k, "byTitle").entrySet()) if (title.contains(e.getKey())) { v = String.valueOf(e.getValue()); break; }
                if (v == null) v = Atlas.s(Atlas.map(k, "byRole"), n.role.name(), null);
                if (v == null) v = Atlas.s(k, "_default", null);
            }
        }
        return v;
    }

    /**
     * (0.4.7) What the census gives one person over their role's kit: 'levels' (skills), 'wear' (items, each in its
     * slot, replacing what was there) and 'look' (their appearance's fields: hair, hairColor, beard, beardColor, skin,
     * eyeColor, bodyType) - a king who is unmistakably himself.
     */
    private static void own(rpg.Rpg g, Npc n, Map<String, Object> person) {
        for (var e : Atlas.map(person, "levels").entrySet()) { Skill s = Skill.byName(e.getKey()); if (s != null && e.getValue() instanceof Number v) n.skills.setLevel(s, v.intValue()); }
        for (Object o : Atlas.arr(person, "wear")) {
            ItemDef d = Items.find(String.valueOf(o));
            if (d == null || d.slot == null) continue;
            n.eq.set(d.slot, new ItemStack(d, 1)); g.state.mint(d, 1);
        }
        Map<String, Object> look = Atlas.map(person, "look");
        if (!look.isEmpty() && n.look != null) {
            for (var e : look.entrySet()) {
                if (!(e.getValue() instanceof Number v)) continue;
                try { java.lang.reflect.Field f = rpg.actor.Appearance.class.getField(e.getKey()); if (f.getType() == int.class) f.setInt(n.look, v.intValue()); }
                catch (ReflectiveOperationException ignored) { }
            }
            n.fitBody();
        }
    }

    /** A made person's means and abilities, from the data's kits by role (and profession). */
    private static void kit(rpg.Rpg g, Npc n, String profession) {
        Map<String, Object> kits = Atlas.map(data(), "kits");
        Map<String, Object> k = Atlas.map(kits, n.role.name());
        if (k.isEmpty()) k = Atlas.map(kits, "_default");
        for (var e : Atlas.map(k, "levels").entrySet()) { Skill s = Skill.byName(e.getKey()); if (s != null && e.getValue() instanceof Number v) n.skills.setLevel(s, v.intValue()); }
        for (Object o : Atlas.arr(k, "wear")) {
            ItemDef d = Items.find(String.valueOf(o));
            if (d != null && d.slot != null) { n.eq.set(d.slot, new ItemStack(d, 1)); g.state.mint(d, 1); }
        }
        List<?> ammo = Atlas.arr(k, "ammo");
        if (ammo.size() == 2 && Items.find(String.valueOf(ammo.get(0))) instanceof ItemDef a) { int c = Atlas.n(ammo.get(1)); n.eq.set(ItemDef.Slot.AMMO, new ItemStack(a, c)); g.state.mint(a, c); }
        Map<String, Object> give = new LinkedHashMap<>(Atlas.map(k, "give"));
        if (profession != null) give.putAll(Atlas.map(Atlas.map(k, "byProfession"), profession));
        for (var e : give.entrySet()) { ItemDef d = Items.find(e.getKey()); if (d != null && e.getValue() instanceof Number v && v.intValue() > 0) g.grant(n, new ItemStack(d, v.intValue())); }
        n.fitBody();
    }

    /** Everyone the census placed whose stand-ins or bed are missing (the validation's list; empty when all is in order). */
    public static List<String> problems() {
        List<String> out = new ArrayList<>();
        for (Assignment a : BY_ID.values()) {
            boolean exception = a.title().startsWith("Bounty Hunter");
            if (a.homeId() == null && !exception) out.add(a.name() + " has no home");
            if (a.homeId() != null && a.bed() == null) out.add(a.name() + " has no bed planned in " + a.homeId());
            if (a.workStage() == null) out.add(a.name() + " has no work stage");
        }
        return out;
    }
}
