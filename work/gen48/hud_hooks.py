import os
R = os.path.join('src','main','java','rpg','ui')
def sub(p, old, new):
    s = open(p, encoding='utf-8').read()
    if old not in s: raise SystemExit('MISSING in ' + p + ': ' + old[:70])
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
sub(os.path.join(R,'Hud.java'), '    public Fragment fragment(String id) { return fragments.get(id); }',
 '    public Fragment fragment(String id) { return fragments.get(id); }\n'
 '    /** (0.4.8, reviews and tests) the journal\'s chosen quest, its settings sheet, the world map\'s view. */\n'
 '    public void journalSelect(String id) { if (fragments.get("journal") instanceof Panels.Journal j) j.select(id); }\n'
 '    public void journalSettings(boolean open) { if (fragments.get("journal") instanceof Panels.Journal j) j.settingsOpen(open); }\n'
 '    public void mapView(float x, float z, float zoom) { if (fragments.get("worldmap") instanceof HudFragments.WorldMap m) m.view(x, z, zoom); }')
sub(os.path.join(R,'Panels.java'), '        private float listScroll;\n', '        private float listScroll;\n        void select(String id) { selected = id; tab = 0; }\n        void settingsOpen(boolean open) { settings = open; tab = 0; }\n')
sub(os.path.join(R,'HudFragments.java'), '        private void zoomBy(float k) {', '        void view(float x, float z, float zoom) { cx = x; cz = z; this.zoom = Math.max(1, Math.min(ZOOM_MAX, zoom)); mapWas = -1; keep = true; }\n        private boolean keep;\n        private void zoomBy(float k) {')
sub(os.path.join(R,'HudFragments.java'), '            if (which != mapWas || Float.isNaN(cx)) { mapWas = which; zoom = 1; cx = x0 + spanX / 2; cz = z0 + spanZ / 2; }',
    '            if (which != mapWas || Float.isNaN(cx)) { mapWas = which; if (!keep) { zoom = 1; cx = x0 + spanX / 2; cz = z0 + spanZ / 2; } keep = false; }')
print('hooks')
