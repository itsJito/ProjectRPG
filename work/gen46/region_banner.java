
    /**
     * (0.4.6, gamefixes: entering a new region shows its name) the name of the kingdom or the wild region the player has
     * come into, top centre: the letters spaced wide over a soft dark band, gilt rules drawing out either side, a smaller
     * line under it saying what the place is, fading in and out. It heads the column of notices at the top (Hud.TOP_STACK),
     * so a bounty reminder that comes with it stands below it, never over it. A border crossed and crossed back within a
     * moment shows nothing.
     */
    static final class RegionBanner extends Fragment {
        RegionBanner(Hud h) { super("regionbanner"); fadeIn = 0.6f; fadeOut = 0.9f; }
        static final float SHOW = rpg.Tuning.f("hud.regionShow", 5f), SETTLE = rpg.Tuning.f("hud.regionSettle", 1.5f);
        private String shown, title = "", caption = "", pending;
        private double pendingAt, at = -99;
        @Override protected boolean active() {
            var g = hud.g;
            if (g.player == null || !rpg.world.WorldMap.shadowveil()) return false;
            String realm = rpg.world.DevMap.jurisdiction(g.player.x, g.player.z);
            var t = rpg.world.shadowveil.ShadowVeil.terrain();
            String region = t == null ? null : t.regionName((int) Math.floor(g.player.x), (int) Math.floor(g.player.z));
            boolean wild = rpg.world.DevMap.WILDS.equals(realm);
            String place = wild ? region : realm;
            if (place != null && !place.equals(shown)) {
                if (!place.equals(pending)) { pending = place; pendingAt = g.time; }
                else if (g.time - pendingAt >= SETTLE) {
                    shown = place; pending = null;
                    title = place;
                    caption = wild ? "The Wilds" : region != null && !region.equals(realm) ? "Kingdom of " + realm + "  -  " + region : "Kingdom of " + realm;
                    at = g.time;
                }
            } else if (place != null) pending = null;
            return g.time >= at && g.time - at < SHOW;
        }
        @Override public float width() { return 640; }
        @Override public float height() { return 92; }
        @Override public void draw(Ui2D ui, float x, float y) {
            float t = (float) (hud.g.time - at), a = Math.min(Math.min(1, t / 0.8f), Math.max(0, (SHOW - t) / 1.0f));
            if (a <= 0) return;
            float cx = x + 320;
            StringBuilder sp = new StringBuilder();
            for (char c : title.toUpperCase(java.util.Locale.ROOT).toCharArray()) { if (sp.length() > 0) sp.append(' '); sp.append(c == ' ' ? "  " : String.valueOf(c)); }
            String spaced = sp.toString();
            float size = Math.min(44, 900f / Math.max(10, spaced.length()));
            float tw = ui.width(ui.body, spaced, size);
            // a soft dark band under the words, widest in the middle
            for (int i = 0; i < 10; i++) { float k = 1 - Math.abs(i - 4.5f) / 5f; ui.rect(cx - tw / 2 - 90, y + 8 + i * 7, tw + 180, 7, Style.fade(0xFF000000, a * 0.32f * k)); }
            // gilt rules drawing out from the middle either side, a lozenge at each end
            float open = ease(t / 1.1f), rl = (tw / 2 + 70) * open;
            ui.rect(cx - rl, y + 62, rl * 2, 1, Style.fade(0xFFE8C878, a * 0.85f));
            ui.rect(cx - rl * 0.8f, y + 65, rl * 1.6f, 1, Style.fade(0xFF8A6A30, a * 0.6f));
            for (float s : new float[]{-1, 1}) { float lx = cx + s * rl; ui.rect(lx - 3, y + 60, 6, 6, Style.fade(0xFFE8C878, a)); ui.rect(lx - 1, y + 58, 2, 10, Style.fade(0xFFF6E2A8, a)); }
            float rise = (1 - ease(t / 0.9f)) * 10;
            ui.text(ui.body, spaced, cx - tw / 2 + 2, y + 16 + rise + 2, size, Style.fade(0xFF000000, a * 0.75f));
            ui.text(ui.body, spaced, cx - tw / 2, y + 16 + rise, size, Style.fade(0xFFF2E2B4, a));
            ui.flatCentered(caption, cx, y + 72, 14, Style.fade(0xFFD8C8A0, a * ease((t - 0.35f) / 0.8f)));
        }
    }
