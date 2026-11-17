    /**
     * (0.4.8, gamefixes: the world map - larger, zoom, scroll, markers, more impressive) The chart of the land in play, as
     * large as the frame allows: the mouse wheel zooms in about the cursor (to twelve times), a drag pans, the buttons zoom,
     * fit the whole land or centre on you. A click marks a destination (and walks you there, as before); a right-click
     * drops a marker of your own (numbered, in its own colour, kept with the save) or picks one up. A grid every hundred
     * metres (fifty, close in) is numbered along the edges; a compass and a scale bar stand in the corners; the realm and
     * landmark names grow with the zoom; under the cursor the region, the realm and the ground's coordinates are read
     * out. The active quest's destination is starred, held to the edge when it lies off the view. Down the right: where
     * you are, the active quest, your markers (each to fly to or remove) and the key to the chart.
     */
    static final class WorldMap extends Fragment {
        WorldMap(Hud h) { super("worldmap"); }
        @Override public float width() { return Hud.FW - 32; }
        @Override public float height() { return Hud.FH - 68; }
        /** The view: metres at its centre, its zoom (1: the whole land fits); a drag under way. */
        private float cx = Float.NaN, cz, zoom = 1;
        private boolean dragging, dragged;
        private float dragMX, dragMY, dragCX, dragCZ;
        private int mapWas = -1;
        static final float ZOOM_MAX = rpg.Tuning.f("map.zoomMax", 12), PANEL = 250;
        static final int[] MARK_COLORS = {0xFFE8C84A, 0xFF58B8E8, 0xFFE86A58, 0xFF7AD06A, 0xFFC888E8, 0xFFF0F0F0};

        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            Minimap map = (Minimap) hud.fragment("minimap");
            if (map.tex == 0 || g.mapDirty) map.build();
            if (g.time - map.markersAt > 2) map.markers();
            var dungeon = rpg.world.DungeonLayout.at(g.player.x, g.player.z);
            float x0 = dungeon == null ? 0 : dungeon.ox, z0 = dungeon == null ? 0 : dungeon.oz;
            float spanX = dungeon == null ? map.S : dungeon.w, spanZ = dungeon == null ? map.S : dungeon.d;
            int which = dungeon == null ? -1 : dungeon.id;
            if (which != mapWas || Float.isNaN(cx)) { mapWas = which; zoom = 1; cx = x0 + spanX / 2; cz = z0 + spanZ / 2; }
            // the chart's window
            float left = x + 8, top = y + 30, cw = width() - PANEL - 34, ch = height() - 40;
            float fit = Math.min(cw / spanX, ch / spanZ);
            ui.text(ui.heading, dungeon == null ? "World Map" : dungeon.name, left, y - 6, 24, Style.ACCENT);
            String place = dungeon == null ? DevMap.jurisdiction(g.player.x, g.player.z) : dungeon.type.title;
            ui.text(place, left + ui.width(ui.heading, dungeon == null ? "World Map" : dungeon.name, 24) + 16, y + 2, 13, Style.DIM);
            // ---- input: the wheel zooms about the cursor, a drag pans, a click marks, a right-click drops or lifts a marker
            boolean over = live && ui.hover(left, top, cw, ch);
            float ppm = fit * zoom;
            if (over && ui.scroll != 0) {
                float wx = cx + (ui.mx - (left + cw / 2)) / ppm, wz = cz + (ui.my - (top + ch / 2)) / ppm;
                zoom = Math.max(1, Math.min(ZOOM_MAX, zoom * (float) Math.pow(1.25, ui.scroll)));
                ppm = fit * zoom;
                cx = wx - (ui.mx - (left + cw / 2)) / ppm; cz = wz - (ui.my - (top + ch / 2)) / ppm;
                ui.scroll = 0;
            }
            if (over && ui.click && !ui.consumed) { dragging = true; dragged = false; dragMX = ui.mx; dragMY = ui.my; dragCX = cx; dragCZ = cz; ui.consumed = true; }
            if (dragging) {
                if (Math.hypot(ui.mx - dragMX, ui.my - dragMY) > 4) dragged = true;
                if (dragged) { cx = dragCX - (ui.mx - dragMX) / ppm; cz = dragCZ - (ui.my - dragMY) / ppm; }
                if (!ui.down) {
                    dragging = false;
                    if (!dragged && dungeon == null) {
                        float tx = cx + (dragMX - (left + cw / 2)) / ppm, tz = cz + (dragMY - (top + ch / 2)) / ppm;
                        if (tx >= 0 && tz >= 0 && tx < spanX && tz < spanZ) { hud.flag = new float[]{tx, tz}; hud.sound("ui.Click", 0.35f); }
                    }
                }
            }
            var marks = g.questLog.mapMarks;
            if (over && ui.rclick && !ui.consumed && dungeon == null) {
                ui.consumed = true;
                float tx = cx + (ui.mx - (left + cw / 2)) / ppm, tz = cz + (ui.my - (top + ch / 2)) / ppm;
                int near = -1;
                for (int i = 0; i < marks.size(); i++) if (Math.hypot(marks.get(i)[0] - tx, marks.get(i)[1] - tz) * ppm < 10) near = i;
                if (near >= 0) marks.remove(near);
                else if (marks.size() < rpg.Tuning.i("map.markersMost", 24)) marks.add(new float[]{tx, tz, g.questLog.nextMark++});
                hud.sound("ui.Click", 0.4f);
            }
            // the view held over the land (at the whole land, the land centred)
            float viewW = cw / ppm, viewH = ch / ppm;
            cx = viewW >= spanX ? x0 + spanX / 2 : Math.max(x0 + viewW / 2, Math.min(x0 + spanX - viewW / 2, cx));
            cz = viewH >= spanZ ? z0 + spanZ / 2 : Math.max(z0 + viewH / 2, Math.min(z0 + spanZ - viewH / 2, cz));
            final float fcx = cx, fcz = cz, fppm = ppm, mx0 = left + cw / 2, my0 = top + ch / 2;
            java.util.function.BiFunction<Float, Float, float[]> at = (wx, wz) -> new float[]{mx0 + (wx - fcx) * fppm, my0 + (wz - fcz) * fppm};
            java.util.function.Predicate<float[]> onChart = m -> m[0] >= left && m[0] <= left + cw && m[1] >= top && m[1] <= top + ch;
            // ---- the chart: a dark mount, the land (only the part in view), a vignette, the gold frame
            ui.rect(left - 6, top - 6, cw + 12, ch + 12, 0xFF0E0B08);
            ui.rect(left, top, cw, ch, 0xFF15120E);
            float vx0 = Math.max(x0, cx - viewW / 2), vz0 = Math.max(z0, cz - viewH / 2), vx1 = Math.min(x0 + spanX, cx + viewW / 2), vz1 = Math.min(z0 + spanZ, cz + viewH / 2);
            float[] a0 = at.apply(vx0, vz0), a1 = at.apply(vx1, vz1);
            float texW = dungeon == null ? map.S : dungeon.w, texD = dungeon == null ? map.S : dungeon.d;
            float u0 = (vx0 - x0) / texW, v0 = (vz0 - z0) / texD, u1 = (vx1 - x0) / texW, v1 = (vz1 - z0) / texD;
            ui.quad(map.texFor(dungeon), new float[]{a0[0], a0[1], a1[0], a0[1], a1[0], a1[1], a0[0], a1[1]}, new float[]{u0, v0, u1, v0, u1, v1, u0, v1}, 0xFFF4E8D0, 0);
            // the grid: a hundred metres apart (fifty close in), numbered along the top and the left
            float step = zoom >= 4 ? 50 : zoom >= 1.6f ? 100 : 200;
            for (float gx = (float) Math.ceil(vx0 / step) * step; gx <= vx1; gx += step) {
                float sx = at.apply(gx, 0f)[0];
                ui.rect(sx, a0[1], 1, a1[1] - a0[1], 0x22000000);
                if (sx > left + 20 && sx < left + cw - 20) ui.text(String.valueOf((int) gx), sx + 3, top + 3, 10, 0x90F4E8D0);
            }
            for (float gz = (float) Math.ceil(vz0 / step) * step; gz <= vz1; gz += step) {
                float sy = at.apply(0f, gz)[1];
                ui.rect(a0[0], sy, a1[0] - a0[0], 1, 0x22000000);
                if (sy > top + 20 && sy < top + ch - 20) ui.text(String.valueOf((int) gz), left + 4, sy + 2, 10, 0x90F4E8D0);
            }
            // the edges darkened a little: the chart reads as paper under a lamp
            for (int i = 0; i < 6; i++) { float k = i * 4; ui.border(left + k, top + k, cw - 2 * k, ch - 2 * k, 4, 0x0E000000); }
            Style.trim(ui, left - 6, top - 6, cw + 12, ch + 12);
            // ---- what is on it
            java.util.Set<String> shown = new java.util.HashSet<>();
            float icon = Math.min(22, 13 + zoom * 1.2f);
            if (dungeon == null) for (Object[] marker : map.markers) {
                float[] m = at.apply((float) marker[1], (float) marker[2]);
                if (!onChart.test(m)) continue;
                ui.img((String) marker[0], m[0] - icon / 2, m[1] - icon / 2, icon, 0xFFFFFFFF);
                shown.add((String) marker[0]);
            }
            if (dungeon == null && rpg.world.WorldMap.shadowveil()) {
                float size = Math.min(17, 10 + zoom * 1.1f);
                for (var label : rpg.world.shadowveil.ShadowVeil.labels().entrySet()) {
                    float[] m = at.apply(label.getValue()[0], label.getValue()[1]);
                    if (!onChart.test(m)) continue;
                    ui.flatCentered(label.getKey(), m[0] + 1, m[1] - 7, size, 0xC0000000);
                    ui.flatCentered(label.getKey(), m[0], m[1] - 8, size, 0xFFF4E8D0);
                }
            }
            for (var door : g.dungeons.doors) if (rpg.world.DungeonLayout.at(door.x, door.z) == dungeon) {
                float[] m = at.apply(door.x, door.z);
                if (onChart.test(m)) { ui.img("Boss_Icon", m[0] - icon / 2, m[1] - icon / 2, icon, 0xFFFFFFFF); shown.add("Boss_Icon"); }
            }
            // local people only: a world map is not remote knowledge of hidden actors
            float dotR = Math.min(4, 2 + zoom * 0.2f);
            for (Npc n : g.npcs) if (n.alive && n.loaded && n.distTo(g.player) < 28 && g.vox.sight(g.player.x, g.player.eyeY(), g.player.z, n.x, n.chestY(), n.z)) {
                float[] m = at.apply(n.x, n.z); if (onChart.test(m)) ui.disc(m[0], m[1], dotR, Minimap.ally(n, g) ? Style.ALLY : n.hostileToPlayer ? Style.NPC_HOSTILE : Style.NPC);
            }
            for (var mo : g.monsters.all()) if (mo.alive && mo.loaded && mo.distTo(g.player) < 28 && g.vox.sight(g.player.x, g.player.eyeY(), g.player.z, mo.x, mo.chestY(), mo.z)) {
                float[] m = at.apply(mo.x, mo.z); if (onChart.test(m)) ui.disc(m[0], m[1], dotR, Style.NPC_HOSTILE);
            }
            for (var grave : g.undertaker.graves()) { float[] m = at.apply(grave.x, grave.z); if (onChart.test(m)) skull(ui, m[0], m[1], 12, Style.TEXT); }
            for (var contract : g.bountyBoard.carried()) {
                float[] m = at.apply(contract.x(), contract.z());
                if (!onChart.test(m)) continue;
                ui.disc(m[0], m[1], 7, 0x70402010); ui.border(m[0] - 5, m[1] - 5, 10, 10, 2, 0xFFFFC05A);
            }
            // your markers: a pin each, numbered, in its colour
            if (dungeon == null) for (float[] mk : marks) {
                float[] m = at.apply(mk[0], mk[1]);
                if (!onChart.test(m)) continue;
                pin(ui, m[0], m[1], MARK_COLORS[(int) mk[2] % MARK_COLORS.length], String.valueOf((int) mk[2] + 1));
            }
            if (hud.flag != null) { float[] m = at.apply(hud.flag[0], hud.flag[1]); if (onChart.test(m)) { ui.disc(m[0], m[1], 5, 0xFF000000); ui.disc(m[0], m[1], 4, Style.BAD); } }
            // the active quest: a gold star with a slow ring, held to the edge when off the view
            var aq = g.questLog.activeEntry();
            if (aq != null && aq.destination() != null && dungeon == null) {
                float[] m = at.apply(aq.destination()[0], aq.destination()[1]);
                boolean in = onChart.test(m);
                m[0] = Math.max(left + 10, Math.min(left + cw - 10, m[0])); m[1] = Math.max(top + 10, Math.min(top + ch - 10, m[1]));
                float pulse = 0.5f + 0.5f * (float) Math.sin(g.time * 3);
                if (in) ui.ring(m[0], m[1], 9 + 4 * pulse, 1.5f, Style.fade(0xFFFFD15E, 1 - pulse * 0.6f));
                ui.disc(m[0], m[1], 8, 0xC0141008);
                Panels.Journal.star(ui, m[0], m[1], 7, 0xFFFFD15E);
            }
            // the player: an arrow along the camera
            float[] pm = at.apply(g.player.x, g.player.z);
            float px = Math.max(left + 4, Math.min(left + cw - 4, pm[0])), py = Math.max(top + 4, Math.min(top + ch - 4, pm[1]));
            float yaw = g.controller.camYaw, fx = -(float) Math.sin(yaw), fz = -(float) Math.cos(yaw), rx = -fz, rz = fx;
            float tip = 9, back = 5;
            ui.disc(px, py, 7, 0x90000000);
            ui.line(px + fx * tip, py + fz * tip, px - fx * back + rx * 5, py - fz * back + rz * 5, 2.5f, Style.ACCENT);
            ui.line(px + fx * tip, py + fz * tip, px - fx * back - rx * 5, py - fz * back - rz * 5, 2.5f, Style.ACCENT);
            ui.line(px - fx * back + rx * 5, py - fz * back + rz * 5, px - fx * 2, py - fz * 2, 2, Style.ACCENT);
            ui.line(px - fx * back - rx * 5, py - fz * back - rz * 5, px - fx * 2, py - fz * 2, 2, Style.ACCENT);
            // ---- the compass and the scale bar
            compass(ui, left + cw - 34, top + 34);
            float target = 100 / (zoom >= 4 ? 2f : 1f) * (zoom < 1.6f ? 2 : 1), bar = target * ppm;
            ui.rect(left + 12, top + ch - 26, bar + 12, 20, 0xA0141008);
            ui.rect(left + 18, top + ch - 14, bar, 3, 0xFFF4E8D0);
            ui.rect(left + 18, top + ch - 18, 1, 7, 0xFFF4E8D0); ui.rect(left + 18 + bar, top + ch - 18, 1, 7, 0xFFF4E8D0);
            ui.text((int) target + " m", left + 22, top + ch - 25, 10, 0xFFF4E8D0);
            // ---- the read-out under the cursor
            if (over) {
                float tx = cx + (ui.mx - mx0) / ppm, tz = cz + (ui.my - my0) / ppm;
                if (tx >= x0 && tz >= z0 && tx < x0 + spanX && tz < z0 + spanZ) {
                    String region = rpg.world.WorldMap.shadowveil() && rpg.world.shadowveil.ShadowVeil.terrain() != null ? rpg.world.shadowveil.ShadowVeil.terrain().regionName((int) tx, (int) tz) : null;
                    String realm = DevMap.jurisdiction(tx, tz);
                    String line = (region == null ? "" : region + "  -  ") + (DevMap.WILDS.equals(realm) ? "the wilds" : realm) + String.format("   %d, %d", (int) tx, (int) tz);
                    float tw = ui.width(line, 12) + 16;
                    float bx = Math.min(left + cw - tw - 4, ui.mx + 14), by = Math.min(top + ch - 26, ui.my + 16);
                    ui.rect(bx, by, tw, 22, 0xE0141008); ui.border(bx, by, tw, 22, 1, 0x60C8A060);
                    ui.text(line, bx + 8, by + 4, 12, 0xFFF4E8D0);
                }
            }
            // ---- the zoom buttons over the chart's foot
            float bxs = left + cw - 4 * 34 - 8, bys = top + ch - 34;
            if (ui.button("+", bxs, bys, 30, 26, zoom < ZOOM_MAX)) zoomBy(1.6f);
            if (ui.button("-", bxs + 34, bys, 30, 26, zoom > 1)) zoomBy(1 / 1.6f);
            if (ui.button("Me", bxs + 68, bys, 30, 26, true)) { cx = g.player.x; cz = g.player.z; if (zoom < 3) zoom = 3; }
            if (ui.button("All", bxs + 102, bys, 34, 26, zoom > 1)) { zoom = 1; cx = x0 + spanX / 2; cz = z0 + spanZ / 2; }
            // ---- the side panel
            side(ui, g, dungeon, left + cw + 22, top - 6, PANEL, ch + 12, shown, marks);
        }

        private void zoomBy(float k) { zoom = Math.max(1, Math.min(ZOOM_MAX, zoom * k)); }

        /** A map pin: a round head of its colour over a dark point, its number in the head. */
        static void pin(Ui2D ui, float x, float y, int col, String n) {
            ui.line(x, y, x, y - 10, 2, 0xFF141008);
            ui.disc(x, y - 14, 8, 0xFF141008);
            ui.disc(x, y - 14, 7, col);
            ui.flatCentered(n, x, y - 20, 10, 0xFF141008);
        }
        /** A compass rose: the four points, north picked out in gold. */
        static void compass(Ui2D ui, float x, float y) {
            ui.disc(x, y, 22, 0xA0141008);
            ui.ring(x, y, 21, 1, 0x80C8A060);
            for (int k = 0; k < 4; k++) {
                double a = -Math.PI / 2 + k * Math.PI / 2;
                float ex = x + (float) Math.cos(a) * 17, ey = y + (float) Math.sin(a) * 17;
                float lx = x + (float) Math.cos(a + Math.PI / 2) * 4, ly = y + (float) Math.sin(a + Math.PI / 2) * 4;
                int col = k == 0 ? 0xFFFFD15E : 0xFFB8AC94;
                ui.line(lx, ly, ex, ey, 2, col); ui.line(2 * x - lx, 2 * y - ly, ex, ey, 2, col);
            }
            ui.flatCentered("N", x, y - 37, 12, 0xFFFFD15E);
        }

        /** Down the right: where you are, the active quest, your markers, the key. */
        private void side(Ui2D ui, Rpg g, rpg.world.DungeonLayout dungeon, float x, float y, float w, float h, java.util.Set<String> shown, java.util.List<float[]> marks) {
            Style.card(ui, x - 10, y, w + 10, h);
            float cy = y + 12, inner = w - 14;
            String place = dungeon == null ? DevMap.jurisdiction(g.player.x, g.player.z) : dungeon.name;
            ui.text(ui.body, DevMap.WILDS.equals(place) ? "The wilds" : place, x, cy, 16, Style.ACCENT);
            cy += 22;
            ui.text((int) g.player.x + ", " + (int) g.player.z + "    " + g.state.clockText(), x, cy, 11, Style.DIM);
            cy += 20;
            Style.rule(ui, x, cy, inner); cy += 10;
            var aq = g.questLog.activeEntry();
            ui.text("Active quest", x, cy, 12, Style.FAINT); cy += 16;
            if (aq == null) { ui.text("None (set one in the Journal)", x, cy, 12, Style.DIM); cy += 18; }
            else {
                Panels.Journal.star(ui, x + 6, cy + 7, 6, 0xFFFFD15E);
                ui.text(aq.title(), x + 18, cy, 13, g.questLog.color(aq.status()));
                cy += 17;
                if (aq.destination() != null) {
                    float d = (float) Math.hypot(aq.destination()[0] - g.player.x, aq.destination()[1] - g.player.z);
                    ui.text(aq.destinationName() + String.format(", %.0f m", d), x + 18, cy, 11, Style.DIM);
                    if (ui.clicked(x, cy - 18, inner, 34)) { cx = aq.destination()[0]; cz = aq.destination()[1]; if (zoom < 3) zoom = 3; }
                    cy += 16;
                }
            }
            cy += 4; Style.rule(ui, x, cy, inner); cy += 10;
            ui.text("Your markers", x, cy, 12, Style.FAINT);
            String hint = marks.isEmpty() ? "right-click the chart" : marks.size() + "";
            ui.text(hint, x + inner - ui.width(hint, 11), cy, 11, Style.FAINT);
            cy += 18;
            for (int i = 0; i < marks.size() && cy < y + h * 0.58f; i++) {
                float[] mk = marks.get(i);
                int col = MARK_COLORS[(int) mk[2] % MARK_COLORS.length];
                boolean hv = ui.hover(x, cy - 2, inner, 18);
                if (hv) ui.rect(x - 2, cy - 2, inner + 4, 18, 0x20FFFFFF);
                ui.disc(x + 6, cy + 7, 5, col);
                ui.text("Marker " + ((int) mk[2] + 1) + String.format("   %d, %d", (int) mk[0], (int) mk[1]), x + 18, cy + 1, 11, Style.TEXT);
                if (ui.clicked(x + inner - 14, cy - 2, 16, 18)) { marks.remove(i); hud.sound("ui.Click", 0.35f); break; }
                ui.text("x", x + inner - 10, cy, 12, hv ? Style.BAD : Style.FAINT);
                if (ui.clicked(x, cy - 2, inner - 16, 18)) { cx = mk[0]; cz = mk[1]; if (zoom < 3) zoom = 3; }
                cy += 18;
            }
            cy += 4; Style.rule(ui, x, cy, inner); cy += 10;
            // the key: what is on the chart in view
            float col = inner / 2, row = 18, stop = y + h - 66;
            int i = 0;
            String[] people = {"You", "People", "Hostiles"};
            int[] colours = {Style.ACCENT, Style.NPC, Style.NPC_HOSTILE};
            for (int k = 0; k < people.length; k++, i++) { float lx = x + (i % 2) * col, ly = cy + (i / 2) * row; ui.disc(lx + 6, ly + 7, 3, colours[k]); ui.text(people[k], lx + 16, ly, 11, Style.TEXT); }
            if (i % 2 == 1) i++;
            for (String[] key : MAP_KEY) {
                if (!shown.contains(key[0])) continue;
                float lx = x + (i % 2) * col, ly = cy + (i / 2) * row;
                if (ly + row > stop) break;
                ui.img(key[0], lx - 1, ly - 1, 14, 0xFFFFFFFF); ui.text(key[1], lx + 16, ly, 11, Style.DIM);
                i++;
            }
            float by = y + h - 56;
            Style.rule(ui, x, by - 6, inner);
            ui.text("Wheel: zoom   Drag: pan", x, by, 11, Style.DIM);
            ui.text("Click: destination   Right-click: marker", x, by + 15, 11, Style.DIM);
            ui.text("M or Esc to return", x, by + 30, 11, Style.FAINT);
        }
    }
