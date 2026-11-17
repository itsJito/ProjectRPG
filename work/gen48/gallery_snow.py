import os
p = os.path.join('src','main','java','rpg','Gallery.java')
s = open(p, encoding='utf-8').read()
anchor = '        // RPG_REVIEW_AT="x,y,z[,dist];...": four views of each point'
assert anchor in s
block = '''        // (0.4.8) RPG_REVIEW_ONLY=p34snow: deep snow, the player walked across open ground and a cart's worth of props in it
        // (prints pressed in, the snow capping the blocks' edges); then soaked ground in the rain (puddles, wet prints)
        if (want.test("p34snow")) {
            float[] pt = start;
            stand.accept(pt); g.controller.snapTo(map.spawnYaw); g.state.minutes = 11 * 60; g.player.firstPerson = false;
            for (String wx : new String[]{"Snow", "Rain"}) {
                g.weatherSystem.force(wx, g.player.x, g.player.z, 6);
                g.timeSystem.weather.snap();
                if (wx.equals("Snow")) { g.timeSystem.weather.snowCover = 1; g.timeSystem.weather.wetness = 0; }
                else { g.timeSystem.weather.snowCover = 0; g.timeSystem.weather.wetness = 1; }
                stand.accept(pt); frames(30);
                float x0 = g.player.x, z0 = g.player.z;
                for (int i = 0; i < 150; i++) { g.controller.in.forward = 1; frames(1); }
                g.controller.in.forward = 0; frames(20);
                float x1 = g.player.x, z1 = g.player.z, mx = (x0 + x1) / 2, mz = (z0 + z1) / 2, y = rpg.world.WorldMap.standY(mx, mz);
                String tag = wx.toLowerCase();
                view(mx + 4, y + 3.2f, mz + 4, mx, y, mz, "p34-" + tag + "-trail");
                view(x0 - (x1 - x0) * 0.3f, y + 1.7f, z0 - (z1 - z0) * 0.3f, x1, y + 0.3f, z1, "p34-" + tag + "-along");
                view(mx + 8, y + 6, mz - 8, mx - 4, y, mz + 4, "p34-" + tag + "-wide");
            }
        }
'''
s = s.replace(anchor, block + anchor, 1)
open(p, 'w', encoding='utf-8', newline='').write(s)
print('snow review')
