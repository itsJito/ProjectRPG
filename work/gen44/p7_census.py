def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
R = 'rpggame/src/main/java/rpg/'

patch(R + 'ai/Npc.java', [
("""    public float homeY = Float.NaN;""", """    public float homeY = Float.NaN;
    /** (0.4.4) The floor their post is on (NaN: wherever the ground is) - a teller's counter on a keep's top floor. */
    public float postY = Float.NaN;"""),
])
patch(R + 'ai/Brain.java', [
("""    boolean goTo(Npc n,float x,float z,boolean run,float within){return goTo(n,x,Float.NaN,z,run,within);}""",
 """    boolean goTo(Npc n,float x,float z,boolean run,float within){
        // (0.4.4) their post on its own floor (upstairs posts were sought on the ground below them)
        return goTo(n,x,Float.isFinite(n.postY) && x==n.postX && z==n.postZ?n.postY:Float.NaN,z,run,within);
    }"""),
])

C = R + 'world/shadowveil/Census.java'
patch(C, [
("""    private static final Map<Long, Assignment> BY_ID = new LinkedHashMap<>();
    public static Map<Long, Assignment> all() { return BY_ID; }
    public static Assignment of(long id) { return BY_ID.get(id); }""",
"""    private static final Map<Long, Assignment> BY_ID = new LinkedHashMap<>();
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
    }"""),
("""        BY_ID.clear();
        if (!WorldMap.shadowveil()) return;""", """        BY_ID.clear(); POSTS.clear(); CUSTOMERS.clear(); POST_KINDS.clear();
        if (!WorldMap.shadowveil()) return;
        java.util.Set<Architecture.Fitting> postsTaken = new java.util.HashSet<>();"""),
("""            int[] bed = home == null ? null : bed(home, slot(beds, home.id));
            // a camp, a tent or a cave mouth has no room: the bedroll is laid at its stand-in
            if (home != null && bed == null) bed = new int[]{(int) Math.floor(homeStage[0]), (int) Math.floor(ShadowVeil.standY(homeStage[0], homeStage[1])), (int) Math.floor(homeStage[1])};
            n.x = homeStage[0]; n.z = homeStage[1]; n.y = ShadowVeil.standY(n.x, n.z);
            n.homeX = homeStage[0]; n.homeZ = homeStage[1]; n.homeY = Float.NaN;
            n.postX = workStage[0]; n.postZ = workStage[1]; n.dutyX = workStage[0]; n.dutyZ = workStage[1];
            n.spawnX = n.x; n.spawnZ = n.z; n.chaseOffX = n.x; n.chaseOffZ = n.z;
            // a post the world names for this person (the teller at the bank counter in Loxford's keep) stands
            float[] named = body == null ? null : ShadowVeil.post(body);
            if (named != null) { n.postX = named[0]; n.postZ = named[1]; n.dutyX = named[0]; n.dutyZ = named[1]; workStage = named; }""",
"""            // (0.4.4, gamefixes: every NPC a home, a bed and a workplace inside, replacing 0.4.3's stand-ins) their own bed in
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
            Architecture.Fitting post = ws == null ? null : pickPost(ws, n, p, postsTaken);
            if (post != null) {
                postsTaken.add(post);
                n.postX = post.x(); n.postZ = post.z(); n.postY = post.y(); n.dutyX = post.x(); n.dutyZ = post.z();
                workStage = new float[]{post.x(), post.z()};
                POSTS.put(n.id, new float[]{post.x(), post.y(), post.z()});
                POST_KINDS.put(n.id, post.type().substring(5));
                if (post.useX() != 0 || post.useZ() != 0) CUSTOMERS.put(n.id, new float[]{post.useX(), post.useZ()});
            }
            n.spawnX = n.x; n.spawnZ = n.z; n.chaseOffX = n.x; n.chaseOffZ = n.z;"""),
("""                if (home != null && bed != null) home.plannedBeds.add(new int[]{bed[0], bed[1], bed[2]});
            }""", """                if (home != null && bed != null) home.plannedBeds.add(new int[]{bed[0], bed[1], bed[2]});
            }
            if (bedF != null) bedF.name = n.name + "'s bed";"""),
("""    /** The k-th bed planned in a home: inside its largest reserved room, along its walls, upstairs where it has one. {x, y, z} or null. */""",
"""    /**
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

    /** The k-th bed planned in a home: inside its largest reserved room, along its walls, upstairs where it has one. {x, y, z} or null. */"""),
# worksites: none outdoors where the workplace has the station inside
("""                if (!placed.computeIfAbsent(a.workId(), k -> new java.util.HashSet<>()).add(kind)) continue;""",
 """                if (!placed.computeIfAbsent(a.workId(), k -> new java.util.HashSet<>()).add(kind)) continue;
                // (0.4.4) a workplace with the station in its own workroom needs none set out of doors
                Rooms.Site site = Rooms.site(a.workId());
                if (site != null && site.stations.stream().anyMatch(st -> st.kind.name().equals(kind))) continue;"""),
])
print('ok')
