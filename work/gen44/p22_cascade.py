def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
patch('rpggame/src/main/java/rpg/world/shadowveil/Terrain.java', [
("""    /** (0.4.1) Where a river falls: {x, bottom y, z, height} at each cell of a falls' curtain (the spray is drawn there). */""",
 """    /**
     * (0.4.1) Where a river falls: {x, bottom y, z, height, dx, dz} at each cell below a drop - the lower stretch's cell, its
     * surface's height under the lip, how far the water falls, and the way it pours (from the higher cell toward it).
     */"""),
("""    /**
     * (0.4.1, gamefixes: water does not actually flow) where a river drops - its falls, the steps of its rapids - the water
     * pours over the lip as a curtain: each cell below a higher stretch of the same river is filled from its own surface up
     * to the stretch above, so the drop is a sheet of falling water, not a step of still water with air in front of it.
     */
    private void curtains() {
        for (int x = 1; x < S - 1; x++) for (int z = 1; z < S - 1; z++) {
            int i = idx(x, z);
            if ((flags[i] & RIVER) == 0 || water[i] < 0 || deck[i] >= 0) continue;
            int up = water[i];
            for (int n : new int[]{i - S, i + S, i - 1, i + 1}) if ((flags[n] & RIVER) != 0 && water[n] > up && deck[n] < 0) up = water[n];
            if (up - water[i] < 2) continue;
            int base = i * H;
            for (int y = water[i] + 1; y <= up && y < H; y++) if (cells[base + y] == 0) cells[base + y] = (byte) Blocks.WATER;
            falls.add(new int[]{x, water[i], z, up - water[i]});
        }
    }""",
 """    /**
     * (0.4.1, gamefixes: water does not actually flow; 0.4.4: flowing water shouldn't be a true block) where a river drops -
     * its falls, every step of its rapids - is recorded, cell by cell below the lip with the way the water pours; nothing is
     * built there. The renderer pours each one near the eye as a falling sheet of water (a cascade of drops over the lip,
     * spray and mist where it lands), so the step between two stretches reads as water pouring, not as a column of still
     * blocks standing in the air.
     */
    private void curtains() {
        for (int x = 1; x < S - 1; x++) for (int z = 1; z < S - 1; z++) {
            int i = idx(x, z);
            if ((flags[i] & RIVER) == 0 || water[i] < 0 || deck[i] >= 0) continue;
            int up = water[i], dx = 0, dz = 0;
            int[][] nb = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
            for (int[] d : nb) {
                int n = idx(x + d[0], z + d[1]);
                if ((flags[n] & RIVER) != 0 && water[n] > up && deck[n] < 0) { up = water[n]; dx = -d[0]; dz = -d[1]; }
            }
            if (up - water[i] < 1) continue;
            falls.add(new int[]{x, water[i], z, up - water[i], dx, dz});
        }
    }"""),
])
patch('rpggame/src/main/java/rpg/render/Gfx.java', [
("""        // (0.4.1) spray and mist where a river falls (the curtains' feet), near the eye
        if(rpg.world.WorldMap.shadowveil() && rpg.world.shadowveil.ShadowVeil.terrain()!=null){int sprays=0;
            for(int[] f:rpg.world.shadowveil.ShadowVeil.terrain().falls){
                if(sprays>=6 || Math.hypot(f[0]+0.5f-camX,f[2]+0.5f-camZ)>60 || (f[0]+f[2])%3!=0)continue;
                cmd.emitter(0x7A11L<<20|((long)f[0]<<10)|f[2],1,f[0]+0.5f,f[1]+1.1f,f[2]+0.5f,0.9f,10+f[3]*2,0.35f,1.4f,0.85f,0.9f,0.95f,0,0.6f,0);sprays++;}
        }""",
 """        // (0.4.4, gamefixes: flowing water should be a cheap particle simulation, not a true block) every drop of a river
        // near the eye poured: a sheet of drops over the lip, thrown on the way the river runs and falling under gravity
        // the whole height of the drop, and (for a fall) the spray and mist where it lands - the nearest first, within the
        // renderer's own budget of emitters
        if(rpg.world.WorldMap.shadowveil() && rpg.world.shadowveil.ShadowVeil.terrain()!=null){
            nearFalls.clear();
            for(int[] f:rpg.world.shadowveil.ShadowVeil.terrain().falls){float d=(float)Math.hypot(f[0]+0.5f-camX,f[2]+0.5f-camZ);if(d<=FALL_REACH && inFog(f[0]+0.5f,f[2]+0.5f,4))nearFalls.add(new float[]{d,f[0],f[1],f[2],f[3],f[4],f[5]});}
            nearFalls.sort((p,q)->Float.compare(p[0],q[0]));
            int poured=0,sprays=0;
            for(float[] f:nearFalls){
                if(poured>=FALL_EMITTERS)break;
                float x=f[1]+0.5f,low=f[2]+rpg.world.Fluids.TOP,z=f[3]+0.5f,h=f[4],dx=f[5],dz=f[6];
                float lipX=x-dx*0.5f,lipZ=z-dz*0.5f,lipY=low+h;
                long id=0x7A12L<<24|((long)f[1]<<11)|(long)f[3];
                // the sheet: drops leaving the lip at the river's pace, falling the drop's height (life for that fall)
                float life=Math.min(2.5f,0.25f+(float)Math.sqrt(2*h/9.8f)*1.15f),rate=Math.min(180,55+h*18);
                cmd.emitter(id,8,lipX,lipY-0.04f,lipZ,0.075f,rate,0.42f,life,0.78f,0.88f,0.98f,dx*1.3f,-0.2f,dz*1.3f);poured++;
                if(h>=2 && sprays<FALL_SPRAYS){cmd.emitter(id^0x55L,1,x+dx*0.4f,low+0.2f,z+dz*0.4f,0.7f,Math.min(140,14+h*6),0.45f,1.5f,0.86f,0.9f,0.96f,dx*0.4f,0.7f,dz*0.4f);sprays++;}
            }
        }"""),
("""    private float camX, camY, camZ;""",
 """    private float camX, camY, camZ;
    /** (0.4.4) The river's drops near the eye this frame {distance, x, y, z, height, dx, dz}, and how many are poured. */
    private final java.util.List<float[]> nearFalls = new java.util.ArrayList<>();
    static final float FALL_REACH = rpg.Tuning.f("render.fallReach", 48f);
    static final int FALL_EMITTERS = rpg.Tuning.i("render.fallEmitters", 22), FALL_SPRAYS = rpg.Tuning.i("render.fallSprays", 6);"""),
])
print('ok')
