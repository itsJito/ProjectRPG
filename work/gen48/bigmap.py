import os
R = os.path.join('src','main','java','rpg','ui')
def sub(p, old, new, n=1):
    s = open(p, encoding='utf-8').read()
    if old not in s: raise SystemExit('MISSING in ' + p + ': ' + old[:70])
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, n))
H = os.path.join(R,'Hud.java')
sub(H, 'return Placement.at(Anchor.CENTER, frameShift() - frameWidth() / 2 + fx + f.width() / 2, FRAME_Y - FH / 2 + fy + f.height() / 2);',
       'return Placement.at(Anchor.CENTER, frameShift() - frameWidth() / 2 + fx + f.width() / 2, FRAME_Y - frameHeight() / 2 + fy + f.height() / 2);')
sub(H, '    public float frameWidth() { return vaultOpen() ? VAULT_SIDE : inventoryOnly() ? 684 : FW; }',
       '    public float frameWidth() { return vaultOpen() ? VAULT_SIDE : inventoryOnly() ? 684 : scene == Scene.MAP ? mapFrameW() : FW; }\n'
       '    /** (0.4.8, gamefixes: the world map larger) the map\'s frame takes the screen, less a margin; every other scene\'s is the standard frame. */\n'
       '    public float frameHeight() { return scene == Scene.MAP ? mapFrameH() : FH; }\n'
       '    float mapFrameW() { return Math.max(FW, Math.min(w - 64, 1600)); }\n'
       '    float mapFrameH() { return Math.max(FH, Math.min(h - 150, 900)); }')
sub(H, '        if (scene == Scene.INVENTORY) at.put("radial", Placement.at(Anchor.CENTER, frameWidth() / 2 + 16 + radial.width() / 2, FRAME_Y + 48));',
       '        if (scene == Scene.INVENTORY) at.put("radial", Placement.at(Anchor.CENTER, frameWidth() / 2 + 16 + radial.width() / 2, FRAME_Y + 48));\n'
       '        if (scene == Scene.MAP) at.put("worldmap", inFrame("worldmap", 16, 52));')
P = os.path.join(R,'Panels.java')
sub(P, '        @Override public float width() { return hud.frameWidth(); }', '        @Override public float width() { return hud.frameWidth(); }\n        @Override public float height() { return hud.frameHeight(); }')
sub(P, '            Style.card(ui, x, y, pw, ph);', '            float ph = height();\n            Style.card(ui, x, y, pw, ph);')
F = os.path.join(R,'HudFragments.java')
sub(F, '''        @Override public float width() { return Hud.FW - 32; }
        @Override public float height() { return Hud.FH - 68; }
        /** The view''', '''        @Override public float width() { return hud.frameWidth() - 32; }
        @Override public float height() { return hud.frameHeight() - 68; }
        /** The view''')
# the services' icons only once zoomed in (the whole land reads by its names)
sub(F, '''            if (dungeon == null) for (Object[] marker : map.markers) {
                float[] m = at.apply((float) marker[1], (float) marker[2]);''', '''            if (dungeon == null && zoom >= rpg.Tuning.f("map.iconsFrom", 2.2f)) for (Object[] marker : map.markers) {
                float[] m = at.apply((float) marker[1], (float) marker[2]);''')
print('big')
