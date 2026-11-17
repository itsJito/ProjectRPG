    /**
     * (0.4.8, gamefixes: the journal redone) The quest log: down the left the Main and Side quests as a colour-coded list
     * (red not begun, yellow under way, green done, grey failed - rpg.quest.QuestLog's colours), the active one starred;
     * click one and the right shows it whole - what it is, who gives it, what it asks of you (met or not), what to do now,
     * what you have learned of it, where it leads - with "Set Active", which marks its destination on the minimap and the
     * world map. The settings sheet shows or hides quests not begun (hidden at first) and those whose requirements you do
     * not yet meet, and sorts the list. Notes and The Law keep what the old page held: what was learned, and bounties.
     */
    static final class Journal extends Panel {
        Journal(Hud h) { super("journal", 868, 440); }
        private String selected;
        private int tab;                 // 0 quests, 1 notes, 2 the law
        private boolean settings;
        private float listScroll;
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            var log = g.questLog;
            // the tabs along the top
            String[] tabs = {"Quests", "Notes", "The Law"};
            float tx = x;
            for (int i = 0; i < tabs.length; i++) {
                float w = ui.width(ui.body, tabs[i], 15) + 24;
                boolean on = tab == i, hv = ui.hover(tx, y - 6, w, 26);
                ui.rect(tx, y - 6, w, 26, on ? 0xFF3A2E20 : hv ? 0xFF2A2218 : 0x40000000);
                if (on) ui.rect(tx, y + 18, w, 2, Style.GOLD_LIT);
                ui.text(ui.body, tabs[i], tx + 12, y - 2, 15, on ? Style.ACCENT : Style.DIM);
                if (ui.clicked(tx, y - 6, w, 26)) { tab = i; hud.sound("ui.Click", 0.35f); }
                tx += w + 6;
            }
            Style.rule(ui, x, y + 22, pw);
            if (tab == 1) { notes(ui, g, x, y + 34); return; }
            if (tab == 2) { law(ui, g, x, y + 34); return; }
            // ---- the list
            float lw = 300, top = y + 34, bottom = y + ph - 4;
            if (ui.button(settings ? "Done" : "Settings", x + lw - 80, y - 6, 80, 24, true)) { settings = !settings; hud.sound("ui.Click", 0.35f); }
            Panel.sheet(ui, x, top, lw, bottom - top);
            if (settings) settings(ui, log, x + 10, top + 10, lw - 20);
            else list(ui, log, x, top, lw, bottom);
            // ---- the overview
            overview(ui, log, x + lw + 18, top, pw - lw - 18, y + ph);
        }

        private void list(Ui2D ui, rpg.quest.QuestLog log, float x, float top, float lw, float bottom) {
            java.util.List<Object[]> rows = new java.util.ArrayList<>();
            for (var cat : rpg.quest.QuestLog.Category.values()) {
                var list = log.shown(cat);
                rows.add(new Object[]{cat, list.size()});
                if (list.isEmpty()) rows.add(new Object[]{null, cat == rpg.quest.QuestLog.Category.MAIN ? "No main quests to show." : "No side quests to show."});
                for (var e : list) rows.add(new Object[]{e});
                rows.add(new Object[]{null, ""});
            }
            float content = 0;
            for (Object[] r : rows) content += rowH(r);
            if (ui.hover(x, top, lw, bottom - top)) listScroll = Math.max(0, Math.min(Math.max(0, content - (bottom - top - 12)), listScroll - (float) ui.scroll * 36));
            float cy = top + 8 - listScroll;
            for (Object[] r : rows) {
                float h = rowH(r);
                boolean in = cy >= top + 2 && cy + h <= bottom - 2;
                if (r[0] instanceof rpg.quest.QuestLog.Category c) {
                    if (in) {
                        ui.text(ui.heading, c == rpg.quest.QuestLog.Category.MAIN ? "Main Quests" : "Side Quests", x + 10, cy + 4, 17, Style.ACCENT);
                        String n = String.valueOf(r[1]);
                        ui.text(n, x + lw - 14 - ui.width(n, 12), cy + 7, 12, Style.FAINT);
                        ui.rect(x + 10, cy + 25, lw - 20, 1, 0x40D8B46A);
                    }
                } else if (r[0] == null) {
                    if (in && !((String) r[1]).isEmpty()) ui.text((String) r[1], x + 22, cy + 3, 12, Style.FAINT);
                } else if (in) {
                    var e = (rpg.quest.QuestLog.Entry) r[0];
                    boolean sel = e.id().equals(selected), hv = ui.hover(x + 6, cy, lw - 12, 24);
                    int col = log.color(e.status());
                    if (sel || hv) ui.rect(x + 6, cy, lw - 12, 24, sel ? 0x50604A28 : 0x28FFFFFF);
                    if (sel) ui.rect(x + 6, cy, 3, 24, Style.GOLD_LIT);
                    ui.disc(x + 22, cy + 12, 5, col);
                    ui.text(e.title(), x + 34, cy + 4, 13, e.met() ? col : Style.fade(col, 0.55f));
                    if (e.id().equals(log.active)) star(ui, x + lw - 22, cy + 12, 7, Style.GOLD_LIT);
                    if (ui.clicked(x + 6, cy, lw - 12, 24)) { selected = e.id(); hud.sound("ui.Click", 0.3f); }
                }
                cy += h;
            }
        }
        private static float rowH(Object[] r) { return r[0] instanceof rpg.quest.QuestLog.Category ? 30 : r[0] == null ? (((String) r[1]).isEmpty() ? 8 : 22) : 26; }

        private void overview(Ui2D ui, rpg.quest.QuestLog log, float ox, float top, float ow, float end) {
            var e = log.find(selected);
            if (e == null) {
                ui.text(ui.heading, "Quest Journal", ox, top + 6, 22, Style.ACCENT);
                float yy = top + 44;
                for (String l : HudFragments.wrap(ui, "Choose a quest from the list to read it here. Red quests are not yet begun, yellow are under way and green are done. Set one active to mark where it leads on your minimap and world map.", 13, ow))
                    { ui.text(l, ox, yy, 13, Style.DIM); yy += 17; }
                yy += 10;
                for (var st : rpg.quest.QuestLog.Status.values()) {
                    ui.disc(ox + 6, yy + 7, 5, log.color(st));
                    ui.text(statusWord(st), ox + 18, yy, 12, Style.TEXT);
                    yy += 20;
                }
                if (!log.showUnstarted) ui.text("Quests not yet begun are hidden: see Settings.", ox, yy + 8, 12, Style.FAINT);
                return;
            }
            int col = log.color(e.status());
            ui.text(ui.heading, e.title(), ox, top + 4, 24, col);
            ui.text((e.category() == rpg.quest.QuestLog.Category.MAIN ? "Main quest" : "Side quest") + "  -  " + statusWord(e.status()).toLowerCase(), ox, top + 34, 12, Style.DIM);
            boolean isActive = e.id().equals(log.active);
            boolean canSet = e.destination() != null && (e.status() == rpg.quest.QuestLog.Status.ACTIVE || e.status() == rpg.quest.QuestLog.Status.UNSTARTED);
            float bw = 120;
            if (ui.button(isActive ? "Active" : "Set Active", ox + ow - bw, top + 2, bw, 26, canSet || isActive)) { log.toggleActive(e.id()); hud.sound("ui.Click", 0.4f); }
            if (isActive) { ui.border(ox + ow - bw - 1, top + 1, bw + 2, 28, 1, Style.GOLD_LIT); star(ui, ox + ow - bw - 14, top + 15, 7, Style.GOLD_LIT); }
            Style.rule(ui, ox, top + 52, ow);
            float yy = top + 62;
            if (!e.giver().isEmpty()) { ui.text("Given by", ox, yy, 12, Style.FAINT); ui.text(e.giver(), ox + 90, yy, 12, Style.TEXT); yy += 18; }
            if (!e.requires().isEmpty()) { ui.text("Requires", ox, yy, 12, Style.FAINT); ui.text(e.requires(), ox + 90, yy, 12, e.met() ? Style.GOOD : Style.BAD); yy += 18; }
            if (e.destination() != null) { ui.text("Leads to", ox, yy, 12, Style.FAINT); ui.text(e.destinationName() + String.format("  (%.0f, %.0f)", e.destination()[0], e.destination()[1]), ox + 90, yy, 12, Style.TEXT); yy += 18; }
            yy += 6;
            Ui2D.Face was = ui.use(ui.body);
            for (String l : HudFragments.wrap(ui, e.summary(), 13, ow)) { ui.text(l, ox, yy, 13, 0xFFE0D2B0); yy += 17; }
            ui.use(was);
            yy += 10;
            boolean over = e.status() == rpg.quest.QuestLog.Status.COMPLETED || e.status() == rpg.quest.QuestLog.Status.FAILED;
            ui.text(ui.body, over ? "Outcome" : "Current objective", ox, yy, 14, Style.ACCENT);
            yy += 20;
            for (String l : HudFragments.wrap(ui, e.objective(), 13, ow - 10)) { ui.text(l, ox + 10, yy, 13, Style.TEXT); yy += 17; }
            if (!e.notes().isEmpty() && yy < end - 60) {
                yy += 10;
                ui.text(ui.body, "What you have learned", ox, yy, 14, Style.ACCENT);
                yy += 20;
                outer:
                for (String n : e.notes()) for (String l : HudFragments.wrap(ui, n, 12, ow - 10)) { if (yy > end - 16) break outer; ui.text(l, ox + 10, yy, 12, Style.DIM); yy += 15; }
            }
        }
        private static String statusWord(rpg.quest.QuestLog.Status st) {
            return switch (st) { case UNSTARTED -> "Not begun"; case ACTIVE -> "Under way"; case COMPLETED -> "Completed"; case FAILED -> "Failed"; };
        }

        private void settings(Ui2D ui, rpg.quest.QuestLog log, float x, float y, float w) {
            ui.text(ui.body, "Journal settings", x, y, 15, Style.ACCENT);
            float yy = y + 30;
            if (check(ui, "Show quests not yet begun", log.showUnstarted, x, yy, w)) log.showUnstarted = !log.showUnstarted;
            yy += 28;
            if (check(ui, "Show quests whose requirements you do not meet", log.showUnmet, x, yy, w)) log.showUnmet = !log.showUnmet;
            yy += 44;
            ui.text("Sort the list by", x, yy, 12, Style.DIM);
            yy += 20;
            for (var s : rpg.quest.QuestLog.Sort.values()) {
                String label = switch (s) { case STATUS -> "Progress"; case NAME -> "Name"; case CATEGORY -> "Main, then side"; };
                boolean on = log.sort == s, hv = ui.hover(x, yy, w, 22);
                ui.rect(x, yy, w, 22, on ? 0x50604A28 : hv ? 0x20FFFFFF : 0);
                ui.disc(x + 10, yy + 11, 5, on ? Style.GOLD_LIT : 0x50FFFFFF);
                ui.text(label, x + 24, yy + 4, 12, on ? Style.TEXT : Style.DIM);
                if (ui.clicked(x, yy, w, 22)) { log.sort = s; hud.sound("ui.Click", 0.3f); }
                yy += 24;
            }
        }
        private boolean check(Ui2D ui, String label, boolean on, float x, float y, float w) {
            boolean hv = ui.hover(x, y, w, 30);
            ui.rect(x, y + 3, 16, 16, 0xFF15110C); ui.border(x, y + 3, 16, 16, 1, hv ? Style.GOLD_LIT : Style.GOLD);
            if (on) ui.rect(x + 4, y + 7, 8, 8, Style.GOLD_LIT);
            float yy = y + 3;
            for (String l : HudFragments.wrap(ui, label, 12, w - 26)) { ui.text(l, x + 24, yy, 12, on ? Style.TEXT : Style.DIM); yy += 15; }
            boolean c = ui.clicked(x, y, w, 30);
            if (c) hud.sound("ui.Click", 0.3f);
            return c;
        }
        static void star(Ui2D ui, float cx, float cy, float r, int col) {
            for (int k = 0; k < 5; k++) {
                double a0 = -Math.PI / 2 + k * 2 * Math.PI / 5, a1 = a0 + 2 * Math.PI / 5 * 2;
                ui.line(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, 2, col);
            }
        }
        private void notes(Ui2D ui, Rpg g, float x, float y) {
            List<String> j = g.quests.journal;
            float yy = y;
            if (j.isEmpty()) { ui.text("Nothing learned yet.", x, yy, 13, Style.FAINT); return; }
            Ui2D.Face was = ui.use(ui.body);
            outer:
            for (int i = j.size() - 1; i >= 0; i--) for (String l : HudFragments.wrap(ui, j.get(i), 13, pw)) { if (yy > y + ph - 50) break outer; ui.text(l, x, yy, 13, 0xFFE0D2B0); yy += 17; }
            ui.use(was);
        }
        private void law(Ui2D ui, Rpg g, float x, float y) {
            float yy = y;
            boolean any = false;
            for (var e : g.law.all().entrySet()) { Integer b = e.getValue().get(g.player.id); if (b != null && b > 0) { ui.text(e.getKey() + ": bounty " + String.format("%,d", b) + " G", x, yy, 14, Style.BAD); yy += 20; any = true; } }
            if (!any) ui.text("You are not wanted anywhere.", x, yy, 14, Style.GOOD);
        }
    }

