p = 'C:/Users/John/Documents/ProjectRPG/rpggame/src/main/java/rpg/world/shadowveil/Kit.java'
s = open(p, encoding='utf-8').read()
old = '''            for (int dx = -thick; dx <= thick && !gap[i]; dx++) for (int dz = -thick; dz <= thick && !gap[i]; dz++) {'''
new = '''            // (0.4.3, gamefixes: map glitches at 246, 55, 607 - a road running beside Valgard's wall lifted fifteen cells
            // of it onto an arch) only a road, river or lake under the wall's own footprint opens a gateway
            for (int dx = 0; dx < thick && !gap[i]; dx++) for (int dz = 0; dz < thick && !gap[i]; dz++) {'''
assert old in s
s = s.replace(old, new)
old = '''        boolean[] wide = gap.clone();
        for (int i = 0; i < line.size(); i++) if (gap[i]) for (int j = Math.max(0, i - 2); j <= Math.min(line.size() - 1, i + 2); j++) wide[j] = true;'''
new = '''        boolean[] wide = gap.clone();
        for (int i = 0; i < line.size(); i++) if (gap[i]) for (int j = Math.max(0, i - 2); j <= Math.min(line.size() - 1, i + 2); j++) wide[j] = true;
        // an opening longer than a gate (a road that runs along the wall's line) is a breach, open to the sky between its
        // towers - never the wall carried on a long bridge over the road
        boolean[] breach = new boolean[line.size()];
        for (int i = 0; i < line.size(); ) {
            if (!wide[i]) { i++; continue; }
            int j = i;
            while (j < line.size() && wide[j]) j++;
            if (j - i > 9) for (int k = i; k < j; k++) breach[k] = true;
            i = j;
        }'''
assert old in s
s = s.replace(old, new)
old = '''                if (wide[i]) {
                    // the gateway: an arch over the road, the wall carried over it'''
new = '''                if (breach[i]) { for (int y = g + 1; y <= g + high + 2; y++) c.set(x, y, z, Blocks.AIR); continue; }
                if (wide[i]) {
                    // the gateway: an arch over the road, the wall carried over it'''
assert old in s
s = s.replace(old, new)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
