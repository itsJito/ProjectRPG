p = 'C:/Users/John/Documents/ProjectRPG/rpggame/src/main/java/rpg/Gallery.java'
s = open(p, encoding='utf-8').read()
old = '''        if (want.test("devui")) {'''
new = '''        // RPG_REVIEW_AT="x,y,z[,dist];...": four views of each point (from the north, east, south and west, a little above),
        // at noon, the player taken out of the way - for a bug report's coordinates
        String at = System.getenv("RPG_REVIEW_AT");
        if (at != null && !at.isBlank()) for (String spec : at.split(";")) {
            String[] v = spec.trim().split("[, ]+");
            float x = Float.parseFloat(v[0]) + 0.5f, y = Float.parseFloat(v[1]), z = Float.parseFloat(v[2]) + 0.5f, dist = v.length > 3 ? Float.parseFloat(v[3]) : 14;
            g.state.minutes = 12 * 60; g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR); g.timeSystem.weather.snap();
            g.teleport(g.player, x, rpg.world.WorldMap.standY(x, z), z); g.player.firstPerson = true; frames(40);
            String[] dirs = {"n", "e", "s", "w"};
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2;
                float ex = x + (float) Math.sin(a) * dist, ez = z - (float) Math.cos(a) * dist;
                view(ex, Math.max(y + dist * 0.45f, rpg.world.WorldMap.standY(ex, ez) + 2), ez, x, y + 1, z, "at-" + (int) x + "-" + (int) z + "-" + dirs[k]);
            }
            view(x + 0.5f, y + dist * 1.6f, z + 0.5f + dist * 0.2f, x, y, z, "at-" + (int) x + "-" + (int) z + "-top");
        }
        if (want.test("devui")) {'''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
