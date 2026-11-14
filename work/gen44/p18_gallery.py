def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
G = 'rpggame/src/main/java/rpg/Gallery.java'
patch(G, [
("""        if ("p29".equals(System.getenv("RPG_GALLERY_ONLY"))) { p29Shots(); return 0; }""",
 """        if ("p29".equals(System.getenv("RPG_GALLERY_ONLY"))) { p29Shots(); return 0; }
        // RPG_GALLERY_ONLY=interiors RPG_MAP=shadowveil RPG_REVIEW_ONLY=all|id,id|purpose:x (RPG_REVIEW_HOUR=12): the 0.4.4
        // interior review - every building's entrance, its principal working area and each room's overview, a contact
        // sheet per building, and the registry written with its screenshots
        if ("interiors".equals(System.getenv("RPG_GALLERY_ONLY"))) { interiorShots(); return 0; }"""),
("""    private void p27Shots() {""",
 """    /**
     * (0.4.4, gamefixes: at least three genuine in-engine screenshots of every individual building interior - its
     * entrance, its principal functional area and its overall arrangement - and more for every further room and storey)
     * Each fitted building in turn: from just inside its main door looking in; before its principal working piece (a
     * counter, an altar, a station, a bed, a table) from a pace and a half off; and every room from its high far corner.
     * Each building's shots tiled into one labelled sheet (sheet-id.png); the registry (JSON and report) written beside
     * them with every building's screenshots listed.
     */
    private void interiorShots() {
        var t = rpg.world.shadowveil.ShadowVeil.terrain();
        if (t == null || t.arch == null || t.arch.registry == null) { System.out.println("[interiors] no ShadowVeil registry"); return; }
        String only = System.getenv().getOrDefault("RPG_REVIEW_ONLY", "all");
        java.util.Set<String> ids = new java.util.HashSet<>(java.util.Arrays.asList(only.split(",")));
        float hour = Float.parseFloat(System.getenv().getOrDefault("RPG_REVIEW_HOUR", "12"));
        g.state.minutes = (int) (hour * 60); g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR); g.timeSystem.weather.snap();
        int done = 0;
        long t0 = System.nanoTime();
        for (var e : t.arch.registry.entries) {
            var f = e.fit;
            if (f == null) continue;
            if (!only.equals("all") && !ids.contains(e.id) && !ids.contains("purpose:" + e.purpose) && !ids.contains("kingdom:" + e.kingdom)) continue;
            var s = t.arch.byId(e.id);
            var site = rpg.world.shadowveil.Rooms.site(e.id);
            e.screenshots.clear();
            List<String> names = new ArrayList<>(), labels = new ArrayList<>();
            // the world about the building made ready (meshed, lit) before its first shot
            g.teleport(g.player, s.cx(), rpg.world.WorldMap.standY(s.cx(), s.cz()), s.cz());
            g.player.firstPerson = true;
            gfx.preloadWorld(g, s.cx(), s.cz());
            gfx.preloadChunks(g, s.cx(), s.cz());
            frames(4);
            boolean first = true;
            for (var r : f.rooms) {
                float cx = (r.rect[0] + r.rect[2] + 1) / 2f, cz = (r.rect[1] + r.rect[3] + 1) / 2f, y = r.y;
                float eye = y + Math.min(1.65f, r.clear - 0.4f), high = y + Math.min(r.clear - 0.35f, 3.3f);
                if (first && !e.entrances.isEmpty()) {
                    // 1. the entrance: just inside the main door, looking into the room
                    var en = e.entrances.get(0);
                    float dx = en.facing() == 'e' ? 1 : en.facing() == 'w' ? -1 : 0, dz = en.facing() == 's' ? 1 : en.facing() == 'n' ? -1 : 0;
                    float along = (en.width() - 1) / 2f;
                    float ex = en.x() + 0.5f + (dz != 0 ? along : 0) - dx * 0.6f, ez = en.z() + 0.5f + (dx != 0 ? along : 0) - dz * 0.6f;
                    interiorView(ex, en.y() + 1 + 1.6f, ez, cx - dx * 0, y + 0.9f, cz, e.id + "-1-entrance", names, labels, "entrance");
                    // 2. the principal working area: the room's main piece, from a pace and a half off toward the room's middle
                    float[] main = principal(site, r);
                    if (main != null) {
                        float vx = cx - main[0], vz = cz - main[1], l = (float) Math.hypot(vx, vz);
                        if (l < 0.5f) { vx = -dx; vz = -dz; l = 1; }
                        float px = main[0] + vx / l * 2.3f, pz = main[1] + vz / l * 2.3f;
                        interiorView(px, eye, pz, main[0], y + 0.7f, main[1], e.id + "-2-function-" + r.use, names, labels, "work: " + r.use);
                    }
                    first = false;
                }
                // 3. the room's arrangement: from its high corner farthest from its middle's way in, across to the opposite one
                float[][] corners = {{r.rect[0] + 0.45f, r.rect[1] + 0.45f}, {r.rect[2] + 0.55f, r.rect[1] + 0.45f}, {r.rect[0] + 0.45f, r.rect[3] + 0.55f}, {r.rect[2] + 0.55f, r.rect[3] + 0.55f}};
                int best = 0; float bd = -1;
                for (int k = 0; k < 4; k++) { float d = (float) Math.hypot(corners[k][0] - cx, corners[k][1] - cz) + (g.vox.solid((int) Math.floor(corners[k][0]), (int) Math.floor(high), (int) Math.floor(corners[k][1])) ? -100 : 0); if (d > bd) { bd = d; best = k; } }
                float[] cc = corners[best], oc = corners[3 - best];
                interiorView(cc[0], high, cc[1], oc[0], y + 0.4f, oc[1], e.id + "-3-room" + r.level + "-" + r.use + "-" + names.size(), names, labels, "storey " + r.level + ": " + r.use);
            }
            e.screenshots.addAll(names);
            e.history.add(rpg.Main.VERSION + ": " + names.size() + " screenshots taken (" + (int) hour + ":00)");
            sheet(e.id + " - " + e.name + " (" + e.purpose + ", " + e.kingdom + ") score " + e.score + (e.approved ? " approved" : " NOT approved"), names, labels, "sheet-" + e.id);
            done++;
            if (done % 25 == 0) System.out.printf("[interiors] %d buildings, %.0fs%n", done, (System.nanoTime() - t0) / 1e9);
        }
        try {
            java.nio.file.Files.writeString(new File(shots, "registry.json").toPath(), t.arch.registry.json());
            java.nio.file.Files.writeString(new File(shots, "registry.md").toPath(), t.arch.registry.report());
        } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
        System.out.printf("[interiors] %d buildings shot in %.0fs -> %s%n", done, (System.nanoTime() - t0) / 1e9, shots);
    }

    /** A room's principal piece: its counter, altar, station, throne, desk, bed or table, in that order {x, z}. */
    private static float[] principal(rpg.world.shadowveil.Rooms.Site site, rpg.world.shadowveil.Architecture.RoomRecord r) {
        if (site == null) return null;
        java.util.function.Predicate<float[]> in = p -> p[0] >= r.rect[0] && p[0] <= r.rect[2] + 1 && p[1] >= r.rect[1] && p[1] <= r.rect[3] + 1;
        for (var a : site.altars) if (in.test(new float[]{a.x, a.z})) return new float[]{a.x, a.z};
        rpg.objects.Furniture.Kind[] order = {rpg.objects.Furniture.Kind.COUNTER, rpg.objects.Furniture.Kind.SLAB, rpg.objects.Furniture.Kind.DESK, rpg.objects.Furniture.Kind.DINING_TABLE,
                rpg.objects.Furniture.Kind.FOURPOSTER, rpg.objects.Furniture.Kind.BED, rpg.objects.Furniture.Kind.FIREPLACE, rpg.objects.Furniture.Kind.TABLE, rpg.objects.Furniture.Kind.BENCH};
        for (var k : order) {
            if (k == rpg.objects.Furniture.Kind.DESK) for (var st : site.stations) if (in.test(new float[]{st.x, st.z}) && Math.abs(st.floorY - r.y) < 0.5f) return new float[]{st.x, st.z};
            for (var fu : site.furniture) if (fu.kind == k && fu.floor == r.y && in.test(new float[]{fu.x, fu.z})) return new float[]{fu.x, fu.z};
        }
        return null;
    }

    private void interiorView(float ex, float ey, float ez, float tx, float ty, float tz, String name, List<String> names, List<String> labels, String label) {
        g.teleport(g.player, ex, ey - 1.6f, ez);
        for (int i = 0; i < 12; i++) draw(new float[]{ex, ey, ez, tx, ty, tz}, 1 / 30f);
        Main.shot(gfx, win.width, win.height, new File(shots, name + ".png"));
        names.add(name + ".png"); labels.add(label);
    }

    /** Tiles shots into one sheet (half size, three across), each labelled, a title over them. */
    private void sheet(String title, List<String> names, List<String> labels, String out) {
        try {
            List<BufferedImage> imgs = new ArrayList<>();
            for (String n : names) imgs.add(javax.imageio.ImageIO.read(new File(shots, n)));
            if (imgs.isEmpty()) return;
            int w = imgs.get(0).getWidth() / 2, h = imgs.get(0).getHeight() / 2, cols = Math.min(3, imgs.size()), rows = (imgs.size() + cols - 1) / cols;
            BufferedImage s = new BufferedImage(cols * w, rows * h + 34, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = s.createGraphics();
            g2.setColor(new Color(24, 24, 28)); g2.fillRect(0, 0, s.getWidth(), s.getHeight());
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setFont(new Font("SansSerif", Font.BOLD, 18)); g2.setColor(Color.WHITE); g2.drawString(title, 8, 24);
            for (int i = 0; i < imgs.size(); i++) {
                int x = (i % cols) * w, y = 34 + (i / cols) * h;
                g2.drawImage(imgs.get(i), x, y, w, h, null);
                g2.setColor(new Color(0, 0, 0, 170)); g2.fillRect(x, y, Math.min(w, 12 + labels.get(i).length() * 9), 22);
                g2.setColor(Color.WHITE); g2.setFont(new Font("SansSerif", Font.PLAIN, 15)); g2.drawString(labels.get(i), x + 6, y + 16);
            }
            g2.dispose();
            javax.imageio.ImageIO.write(s, "png", new File(shots, out + ".png"));
        } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }

    private void p27Shots() {"""),
])
patch('rpggame/src/main/java/rpg/Main.java', [
("""    public static final String VERSION = "Alpha 0.4.3";""", """    public static final String VERSION = "Alpha 0.4.4";"""),
])
print('ok')
