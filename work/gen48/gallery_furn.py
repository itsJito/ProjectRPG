import os
p = os.path.join('src','main','java','rpg','Gallery.java')
s = open(p, encoding='utf-8').read()
anchor = '        if (want.test("crates")) {'
assert anchor in s
block = '''        // (0.4.8) RPG_REVIEW_ONLY=furn48: the fifteen new furnishings in two rows, whole; then each struck hard (Wreckage)
        if (want.test("furn48")) {
            rpg.objects.Furniture.Kind[] kinds = {rpg.objects.Furniture.Kind.BARREL, rpg.objects.Furniture.Kind.KEG, rpg.objects.Furniture.Kind.LECTERN, rpg.objects.Furniture.Kind.SPINNING_WHEEL,
                    rpg.objects.Furniture.Kind.SCROLL_RACK, rpg.objects.Furniture.Kind.THRONE, rpg.objects.Furniture.Kind.CRADLE, rpg.objects.Furniture.Kind.COAT_RACK,
                    rpg.objects.Furniture.Kind.BUTTER_CHURN, rpg.objects.Furniture.Kind.GLOBE, rpg.objects.Furniture.Kind.TOOL_RACK, rpg.objects.Furniture.Kind.MAP_TABLE,
                    rpg.objects.Furniture.Kind.DISPLAY_CABINET, rpg.objects.Furniture.Kind.FOLDING_SCREEN, rpg.objects.Furniture.Kind.SACKS};
            java.util.List<rpg.objects.Furniture> made = new java.util.ArrayList<>();
            float x = 57;
            int row = 0;
            for (var k : kinds) {
                int w = switch (k) { case SCROLL_RACK, MAP_TABLE, DISPLAY_CABINET, FOLDING_SCREEN, SACKS, TOOL_RACK -> 2; default -> 1; };
                int d = k == rpg.objects.Furniture.Kind.MAP_TABLE ? 2 : 1;
                int h = switch (k) { case SCROLL_RACK, DISPLAY_CABINET, THRONE, COAT_RACK, FOLDING_SCREEN -> 2; case TOOL_RACK -> 3; default -> 1; };
                int z = row == 0 ? 60 : 64;
                var fu = g.objects.add(new rpg.objects.Furniture(g.state.newEntityId(), k, (int) x, z, (int) x + w - 1, z + d - 1, h, 2, 0, true));
                g.objects.furniture.add(fu); made.add(fu);
                x += w + 1;
                if (x > 70 && row == 0) { x = 57; row = 1; }
            }
            frames(30);
            adapted(new float[]{63.5f, DevMap.FLOOR + 3.4f, 55.5f, 63.5f, DevMap.FLOOR + 0.6f, 61.5f}, "furn48-row1");
            adapted(new float[]{63.5f, DevMap.FLOOR + 3.4f, 59.5f, 63.5f, DevMap.FLOOR + 0.6f, 65.5f}, "furn48-row2");
            adapted(new float[]{59f, DevMap.FLOOR + 1.6f, 57.5f, 61f, DevMap.FLOOR + 0.8f, 61.5f}, "furn48-close-a");
            adapted(new float[]{67f, DevMap.FLOOR + 1.6f, 57.5f, 67f, DevMap.FLOOR + 0.8f, 61.5f}, "furn48-close-b");
            adapted(new float[]{60f, DevMap.FLOOR + 1.6f, 61.5f, 62f, DevMap.FLOOR + 0.8f, 65.5f}, "furn48-close-c");
            adapted(new float[]{67f, DevMap.FLOOR + 1.6f, 61.5f, 67f, DevMap.FLOOR + 0.8f, 65.5f}, "furn48-close-d");
            for (var fu : made) for (int k = 0; k < 10; k++) g.wreckage.strike(fu, k * 5, fu.x + 0.1f * k % 3, fu.floor + 0.5f + 0.1f * k, fu.z, 7, 0, 0, 1, rpg.item.ItemDef.DamageType.BLUNT, g.player);
            for (int k = 0; k < 50; k++) { game.tick(1 / 60f); draw(new float[]{63.5f, DevMap.FLOOR + 3.4f, 55.5f, 63.5f, DevMap.FLOOR + 0.6f, 61.5f}, 1 / 60f); }
            Main.shot(gfx, win.width, win.height, new File(shots, "furn48-wrecked.png"));
            int wrecked = 0; for (var fu : made) if (g.wreckage.isWrecked(fu)) wrecked++;
            System.out.println("[furn48] wrecked " + wrecked + " of " + made.size());
        }
'''
s = s.replace(anchor, block + anchor, 1)
open(p, 'w', encoding='utf-8', newline='').write(s)
print('ok')
