p = 'src/main/java/rpg/world/shadowveil/Architecture.java'
s = open(p, encoding='utf-8').read()
a = """        public boolean reachable() {"""
b = """        /** (0.4.7) A setting from the structure's data (the guild a hall seats, its rank), or null. */
        public String opt(String key) { Object o = opts.get(key); return o == null ? null : String.valueOf(o); }
        public boolean reachable() {"""
assert a in s
s = s.replace(a, b, 1)
open(p, 'w', encoding='utf-8').write(s)

p = 'src/main/java/rpg/econ/Guilds.java'
s = open(p, encoding='utf-8').read()

a = """    /** What joining costs (G), and how much quicker a member works at their own hall's stations. */"""
b = """    /**
     * (0.4.7, gamefixes: all seven guild halls on ShadowVeil) a guild's seat on the map: its hall (a structure whose data
     * names its guild), whether it is the guild's headquarters or a chapter, and who keeps it - the guildmaster at a
     * headquarters, the chapter's head at a chapter. Read from the architecture (no hall is placed in code).
     */
    public record Seat(Kind guild, String structure, String name, boolean hq, long keeper) { }
    public final List<Seat> seats = new ArrayList<>();
    /** Where a chapter's store is filed (after the Row's halls). */
    static final int CHAPTER_STORES = 100;

    /** What joining costs (G), and how much quicker a member works at their own hall's stations. */"""
assert a in s
s = s.replace(a, b, 1)

a = """            }
        }
    }
    /** The store a merchant keeps (a guildmaster's, the Armory's), or null. */"""
b = """            }
        }
        if (rpg.world.WorldMap.shadowveil()) seatShadowveil(econ);
    }

    /**
     * (0.4.7) ShadowVeil's guild seats: every structure the architecture gives a guild. A headquarters' store is its
     * guildmaster's (the Row's store, moved to the master's counter and named for the hall); a chapter's head keeps a
     * store of their own stocked as the guild's. The Row's halls with no seat on the map keep no store here.
     */
    private void seatShadowveil(Economy econ) {
        seats.clear();
        var t = rpg.world.shadowveil.ShadowVeil.terrain();
        if (t == null || t.arch == null) return;
        int chapters = 0;
        for (var st : t.arch.structures) {
            String gk = st.opt("guild");
            if (gk == null) continue;
            Kind k;
            try { k = Kind.valueOf(gk); } catch (IllegalArgumentException e) { continue; }
            boolean hq = !"chapter".equals(st.opt("seat"));
            long keeper = 0;
            if (hq) for (Hall h : HALLS) if (h.guild() == k) keeper = h.master();
            if (keeper == 0 || !(g.actor(keeper) instanceof Npc)) {
                keeper = 0;
                for (var as : rpg.world.shadowveil.Census.all().values()) if (st.id.equals(as.workId())) { keeper = as.id(); break; }
            }
            if (keeper == 0) continue;
            seats.add(new Seat(k, st.id, st.name, hq, keeper));
            Economy.Shop s = null;
            if (hq) for (var e : stores.entrySet()) if (e.getKey() < CHAPTER_STORES && HALLS[e.getKey()].guild() == k) s = e.getValue();
            if (s == null) {
                s = new Economy.Shop(keeper, st.name + " store", DevMap.jurisdiction(st.cx(), st.cz()), st.cx(), st.cz());
                stores.put(CHAPTER_STORES + chapters++, s);
                stockLike(econ, s, k);
            }
            s.name = st.name + " store";
            if (g.actor(keeper) instanceof Npc n) {
                s.counterX = n.postX; s.counterZ = n.postZ + 1.4f; s.jurisdiction = n.homeJurisdiction;
                float[] cust = rpg.world.shadowveil.Census.customerSpot(keeper);
                if (cust != null) { s.counterX = cust[0]; s.counterZ = cust[1]; }
            }
        }
    }
    /** (0.4.7) A chapter's opening stock: as its guild's headquarters' (built the same way, into its own store). */
    private void stockLike(Economy econ, Economy.Shop s, Kind k) {
        for (int i = 0; i < HALLS.length; i++) if (HALLS[i].guild() == k && stores.get(i) != null)
            for (var stack : stores.get(i).stock.stacks()) econ.stockShop(s, stack.def, Math.max(1, stack.amount / 2), Math.max(2, stack.amount));
    }
    /** (0.4.7) The seat a structure is, or null. */
    public Seat seatAt(float x, float z) {
        var t = rpg.world.shadowveil.ShadowVeil.terrain();
        if (seats.isEmpty() || t == null || t.arch == null) return null;
        var st = t.arch.at((int) Math.floor(x), (int) Math.floor(z));
        if (st == null) return null;
        for (Seat s : seats) if (s.structure().equals(st.id)) return s;
        return null;
    }
    /** (0.4.7) The seat someone keeps, or null. */
    public Seat seatOf(long keeper) { for (Seat s : seats) if (s.keeper() == keeper) return s; return null; }
    /** The store a merchant keeps (a guildmaster's, the Armory's), or null. */"""
assert a in s
s = s.replace(a, b, 1)

# storeOf / hallOfStore / guildOf / storeFor / speed / sell / buy: the seats counted
a = """    public Economy.Shop storeOf(Npc keeper) { for (var e : stores.entrySet()) if (HALLS[e.getKey()].master() == keeper.id) return e.getValue(); return null; }
    public Hall hallOfStore(Economy.Shop s) { for (var e : stores.entrySet()) if (e.getValue() == s) return HALLS[e.getKey()]; return null; }"""
b = """    public Economy.Shop storeOf(Npc keeper) {
        for (var e : stores.entrySet()) if (e.getValue().merchant == keeper.id && (e.getKey() >= CHAPTER_STORES || HALLS[e.getKey()].master() == keeper.id)) return e.getValue();
        return null;
    }
    public Hall hallOfStore(Economy.Shop s) { for (var e : stores.entrySet()) if (e.getValue() == s && e.getKey() < CHAPTER_STORES) return HALLS[e.getKey()]; return null; }
    /** (0.4.7) The guild a store trades for (a Row hall's, or a ShadowVeil seat's), or null. */
    public Kind guildOfStore(Economy.Shop s) {
        Hall h = hallOfStore(s);
        if (h != null && h.guild() != null) return h.guild();
        Seat seat = seatOf(s.merchant);
        return seat == null ? null : seat.guild();
    }"""
assert a in s
s = s.replace(a, b, 1)

a = """    public Kind guildOf(Npc master) { for (Hall h : HALLS) if (h.master() == master.id && h.guild() != null) return h.guild(); return null; }"""
b = """    public Kind guildOf(Npc master) {
        Seat seat = seatOf(master.id);
        if (seat != null) return seat.guild();
        for (Hall h : HALLS) if (h.master() == master.id && h.guild() != null) return h.guild();
        return null;
    }"""
assert a in s
s = s.replace(a, b, 1)

a = """    public float speed(Actor a, Station s) {
        for (Hall h : HALLS)"""
b = """    public float speed(Actor a, Station s) {
        // (0.4.7) on ShadowVeil, at any of the guild's seats
        Seat seat = seatAt(s.x, s.z);
        if (seat != null) return member(a, seat.guild()) ? HALL_SPEED : 1;
        for (Hall h : HALLS)"""
assert a in s
s = s.replace(a, b, 1)

a = """    public float sellShare(Actor seller, Economy.Shop s) { Hall h = hallOfStore(s); return h != null && h.guild() != null && member(seller, h.guild()) ? MEMBER_SELL : 1; }
    public float buyShare(Actor buyer, Economy.Shop s) { Hall h = hallOfStore(s); return h != null && h.guild() != null && member(buyer, h.guild()) ? MEMBER_BUY : 1; }"""
b = """    public float sellShare(Actor seller, Economy.Shop s) { Kind k = guildOfStore(s); return k != null && member(seller, k) ? MEMBER_SELL : 1; }
    public float buyShare(Actor buyer, Economy.Shop s) { Kind k = guildOfStore(s); return k != null && member(buyer, k) ? MEMBER_BUY : 1; }"""
assert a in s
s = s.replace(a, b, 1)

a = """    public Economy.Shop storeFor(Npc n) {
        Hall home = hallOf(n.id);"""
b = """    public Economy.Shop storeFor(Npc n) {
        // (0.4.7) on ShadowVeil a guild's member sells to the nearest of its seats' stores
        if (!seats.isEmpty()) {
            Kind mine = null;
            for (Kind k : Kind.values()) if (member(n, k)) { mine = k; break; }
            if (mine == null) return null;
            Economy.Shop best = null; double bd = Double.MAX_VALUE;
            for (Seat seat : seats) if (seat.guild() == mine) for (var st : stores.values()) if (st.merchant == seat.keeper()) {
                double d = Math.hypot(st.counterX - n.postX, st.counterZ - n.postZ);
                if (d < bd && d < SEAT_TRADE_REACH) { bd = d; best = st; }
            }
            return best;
        }
        Hall home = hallOf(n.id);"""
assert a in s
s = s.replace(a, b, 1)
s = s.replace("""    static final int CHAPTER_STORES = 100;""", """    static final int CHAPTER_STORES = 100;
    /** (0.4.7) How far (m) a member will carry their goods to a seat's store rather than their town's market. */
    static final double SEAT_TRADE_REACH = rpg.Tuning.f("guilds.seatReach", 260f);""")
open(p, 'w', encoding='utf-8').write(s)
print("ok")
