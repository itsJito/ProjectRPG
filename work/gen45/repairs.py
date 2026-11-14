import os
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg')
def patch(path, pairs):
    s = open(path, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, (path, old[:90])
        s = s.replace(old, new, 1)
    open(path, 'w', encoding='utf-8').write(s)

patch('world/Repairs.java', [
 ('''        Npc witness=g.npcs.get(Math.floorMod(observer++,g.npcs.size()));
        if(!witness.alive || !witness.loaded || witness.asleep())return;
        for(Damage damage:open.values())if(witness.distTo(damage.x+0.5f,damage.z+0.5f)<20
                && g.vox.sightCell(witness.x,witness.eyeY(),witness.z,damage.x,damage.y,damage.z))
            known.computeIfAbsent(witness.id,k->new java.util.HashSet<>()).add(damage.key);
    }''',
  '''        Npc witness=g.npcs.get(Math.floorMod(observer++,g.npcs.size()));
        if(!witness.alive || witness.asleep())return;
        // (0.4.5, gamefixes: NPCs were not repairing or reporting broken glass) whoever lives or works where the damage is
        // knows of it once the dust has settled - their own window, their own workshop - seen or not, in play or off it; a
        // passer-by in play knows what they see
        for(Damage damage:open.values()) {
            boolean seen=witness.loaded && witness.distTo(damage.x+0.5f,damage.z+0.5f)<20 && g.vox.sightCell(witness.x,witness.eyeY(),witness.z,damage.x,damage.y,damage.z);
            boolean theirs=g.time-damage.at>=SETTLE && (Math.hypot(witness.homeX-damage.x-0.5,witness.homeZ-damage.z-0.5)<OWN_REACH || Math.hypot(witness.postX-damage.x-0.5,witness.postZ-damage.z-0.5)<OWN_REACH);
            if(seen || theirs)known.computeIfAbsent(witness.id,k->new java.util.HashSet<>()).add(damage.key);
        }
    }
    /** (0.4.5) How near their home or post (m) damage is someone's own to know of. */
    public static final double OWN_REACH=rpg.Tuning.f("repairs.ownReach",14f);

    /**
     * (0.4.5) Off stage, the same day's work in short: one who knows of damage tells the nearest who can mend it in its
     * kingdom (the order made as in play), and a mender with the order, the tools and the material mends it at the
     * spot when the place has settled - the same rules, the same costs, the world changed the same way. Returns what was
     * done, for the diary, or null.
     */
    public String offstage(Npc n) {
        if(open.isEmpty() || !n.alive)return null;
        for(long key:new ArrayList<>(known.getOrDefault(n.id,java.util.Set.of()))) {
            Damage damage=open.get(key);
            if(damage==null)continue;
            if(trade(n,damage) && eligible(n,damage)) {
                float[] at=standSpot(n,damage);
                if(at==null)continue;
                n.x=at[0];n.z=at[1];n.y=rpg.world.WorldMap.standY(n.x,n.z);
                return mend(n,damage)?"mended the damage at ("+damage.x+", "+damage.y+", "+damage.z+")":null;
            }
            if(reported.getOrDefault(n.id,java.util.Set.of()).contains(key))continue;
            Npc best=null;double bd=Double.MAX_VALUE;
            for(Npc c:g.npcs) {
                if(c==n || !c.alive || c.summonOwner!=0 || c.hostileToPlayer || !trade(c,damage) || !hammer(c))continue;
                double d=Math.hypot(c.homeX-damage.x,c.homeZ-damage.z)+(c.homeJurisdiction.equals(damage.jurisdiction)?0:200);
                if(d<bd){bd=d;best=c;}
            }
            if(best==null)continue;
            known.computeIfAbsent(best.id,k->new java.util.HashSet<>()).add(key);
            reported.computeIfAbsent(n.id,k->new java.util.HashSet<>()).add(key);
            if(!orders.containsKey(key))orders.put(key,new Order(n.id,best.id,0,0));
            g.state.commit("DamageReported",n.id,best.id,damage.jurisdiction,0,Long.toString(key)+" (off-stage)");
            return "told "+best.name+" of the damage at ("+damage.x+", "+damage.y+", "+damage.z+")";
        }
        return null;
    }'''),
])

patch('ai/Offstage.java', [
 ('''    private void work(Npc n, double minute) {
        Profession p = n.profession;''',
  '''    private void work(Npc n, double minute) {
        Profession p = n.profession;
        // (0.4.5) damage they know of comes first: told to the one who mends it, or mended by them
        if (g.repairs != null) { String did = g.repairs.offstage(n); if (did != null) { note(n, minute, (p == null ? n.title : p.title) + ": " + did); return; } }'''),
 # residents at home in the evening also pass on what they know
 ('''            if (h >= 18) { at(n, n.homeX + (float) Math.cos(n.id) * 3, n.homeZ + (float) Math.sin(n.id) * 3); note(n, minute, "about home for the evening"); continue; }''',
  '''            if (h >= 18) {
                String did = g.repairs == null ? null : g.repairs.offstage(n);
                if (did == null) at(n, n.homeX + (float) Math.cos(n.id) * 3, n.homeZ + (float) Math.sin(n.id) * 3);
                note(n, minute, did != null ? did : "about home for the evening"); continue;
            }'''),
])
print('ok')
