package rpg.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.ai.Npc;
import rpg.ai.Profession;
import rpg.item.ItemDef;
import rpg.item.Items;

/**
 * What has been broken and not yet mended (gamefixes: fences, windows and the like could be destroyed but nobody ever put
 * them back). Every pane smashed and every built thing burnt - a fence, a plank wall, a beam, a thatch, cloth - inside a
 * kingdom is remembered here with what stood there before. The world remembers it until someone whose trade it is comes to
 * mend it: a carpenter mends all of it, glass included; a woodcutter can put back timber. Mending wants materials - a
 * plank (or failing that a log) for anything of wood and a Hammer to fit a pane - and a few seconds' work at the spot,
 * and is never begun while the place is still smoking. The wilds are nobody's to mend.
 */
public final class Repairs {
    /** A broken thing: where, what stood there, what broke it (glass, fire), whose kingdom, and when. */
    public record Damage(long key, int x, int y, int z, int original, String cause, String jurisdiction, double at) {
        public boolean glass() { return Blocks.glass(original); }
        public boolean timber() { return !glass() && (Blocks.material(original)==Blocks.Material.WOOD || Blocks.material(original)==Blocks.Material.STRAW || Blocks.material(original)==Blocks.Material.CLOTH || Blocks.dual(original) && (Blocks.material(Blocks.innerOf(original))==Blocks.Material.WOOD || Blocks.material(Blocks.outerOf(original))==Blocks.Material.WOOD)); }
    }
    private final Rpg g;
    private final Map<Long, Damage> open = new LinkedHashMap<>();
    private final Map<Long, Long> claimedBy = new HashMap<>();
    /** Where the one who took each job stands to do it (chosen once, so they do not dither between the two sides of a wall). */
    private final Map<Long, float[]> spots = new HashMap<>();
    private final Map<Long,java.util.Set<Long>> known=new HashMap<>(), reported=new HashMap<>();
    public record Order(long reporter,long worker,long payer,int fee) {}
    private final Map<Long,Order> orders=new HashMap<>();
    private int observer;
    private double nextObserve;
    public long broken, mended;
    /** How long a breakage stands before anyone comes to see to it (seconds): the dust settles, the fire is out. */
    public static final double SETTLE = 40;

    public Repairs(Rpg g) { this.g = g; }

    static long key(int x, int y, int z) { return ((long) x & 0xfffff) << 40 | ((long) y & 0xfffff) << 20 | (long) z & 0xfffff; }

    /** Something built was broken: remembered, if it was in a kingdom and is the kind of thing people mend. */
    public void broken(int x, int y, int z, int original, String cause) {
        String jur = DevMap.jurisdiction(x + 0.5, z + 0.5);
        if (DevMap.WILDS.equals(jur) || !mendable(original)) return;
        long k = key(x, y, z);
        if (open.containsKey(k)) return;
        open.put(k, new Damage(k, x, y, z, original, cause, jur, g.time));
        broken++;
        g.state.commit("DamageNoted", 0, 0, jur, 0, String.format("%s at (%d, %d, %d) by %s", g.vox == null ? "" : "block " + original, x, y, z, cause));
    }
    /** Whether a block is one that people put back: glass, and built wood, thatch and cloth (not trees, leaves or turf). */
    public static boolean mendable(int block) {
        if (Blocks.glass(block)) return true;
        // (0.2.1) a two-faced wall whose wooden half burnt away is mended back to both halves
        if (Blocks.dual(block)) return mendable(Blocks.innerOf(block)) || mendable(Blocks.outerOf(block));
        if (Blocks.isTreeLog(block) || Blocks.isLeaves(block) || block == Blocks.STUMP) return false;
        var m = Blocks.material(block);
        return block == Blocks.FENCE || m == Blocks.Material.WOOD || m == Blocks.Material.STRAW || m == Blocks.Material.CLOTH || switch(block){case Blocks.STONE,Blocks.BRICK,Blocks.PLASTER,Blocks.COBBLE,Blocks.STONE_BRICKS,Blocks.MARBLE,Blocks.LIMESTONE_BRICKS,Blocks.SANDSTONE,Blocks.RED_SANDSTONE->true;default->false;};
    }

    public boolean contains(long key){return open.containsKey(key);}
    /** Whether someone knows of a breakage (the tests', the diary's question). */
    public boolean knows(Npc n,long key){return known.getOrDefault(n.id,java.util.Set.of()).contains(key);}
    public List<Damage> open() { return new ArrayList<>(open.values()); }
    /** (0.4.6) Every broken pane and block back as it was, and nobody's errand to mend them left. Returns how many. */
    public int restoreAll() {
        int n = 0;
        for (Damage d : open.values()) { if (g.vox.block(d.x(), d.y(), d.z()) != d.original()) g.setBlock(d.x(), d.y(), d.z(), d.original()); n++; }
        open.clear(); claimedBy.clear(); spots.clear(); known.clear(); reported.clear(); orders.clear();
        return n;
    }

    /** Knowledge travels by seeing the damage, or by an actual conversation with someone who saw it. */
    public void update() {
        if(open.isEmpty() || g.time<nextObserve || g.npcs.isEmpty())return;
        nextObserve=g.time+0.08;
        Npc witness=g.npcs.get(Math.floorMod(observer++,g.npcs.size()));
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
    }
    public record Report(Damage damage,Npc recipient) {}
    /** (0.3.9) How many of the damages one knows of are weighed in one think. */
    static final int REPORT_WEIGH = 24;
    public Report reportFor(Npc witness) {
        if(!witness.alive || !witness.loaded || witness.summonOwner!=0 || witness.hostileToPlayer || witness.role==Npc.Role.THIEF)return null;
        var seen=known.get(witness.id);if(seen==null)return null;
        // (0.3.9, optimisation: a fire's charred blocks stalled the world for seconds) who could take a report is a short
        // list - the hands with a hammer and the lawmen - gathered once, not the whole roster for every damaged block;
        // and a think weighs a bounded number of the damages it knows of
        java.util.List<Npc> pool=new java.util.ArrayList<>();
        for(Npc candidate:g.npcs) {
            if(candidate==witness || !candidate.alive || !candidate.loaded || candidate.summonOwner!=0 || candidate.hostileToPlayer)continue;
            if(candidate.role==Npc.Role.LAWMAN || hammer(candidate))pool.add(candidate);
        }
        if(pool.isEmpty())return null;
        var mine=reported.getOrDefault(witness.id,java.util.Set.of());
        int weighed=0;
        for(long key:seen) {
            Damage damage=open.get(key);
            if(damage==null || trade(witness,damage) && hammer(witness) || mine.contains(key))continue;
            if(++weighed>REPORT_WEIGH)break;
            Npc recipient=null;float best=Float.MAX_VALUE;
            for(Npc candidate:pool) {
                boolean builder=trade(candidate,damage) && hammer(candidate), officer=candidate.role==Npc.Role.LAWMAN && candidate.homeJurisdiction.equals(damage.jurisdiction);
                if(!builder && !officer)continue;
                if(witness.role==Npc.Role.LAWMAN ? !builder : !builder && !officer)continue;
                if(known.getOrDefault(candidate.id,java.util.Set.of()).contains(key) && (!builder || orders.containsKey(key) && !(witness.role==Npc.Role.LAWMAN && orders.get(key).payer==0)))continue;
                float distance=witness.distTo(candidate)+(officer?-30:0)+(candidate.homeJurisdiction.equals(damage.jurisdiction)?0:12);
                if(candidate.id==witness.damageReportTarget)distance-=100;
                if(distance<best){best=distance;recipient=candidate;}
            }
            if(recipient!=null){witness.damageReportTarget=recipient.id;return new Report(damage,recipient);}
        }
        return null;
    }
    public boolean report(Npc witness,Damage damage,Npc recipient) {
        if(!open.containsKey(damage.key) || !known.getOrDefault(witness.id,java.util.Set.of()).contains(damage.key)
                || !witness.alive || !recipient.alive || witness.distTo(recipient)>3
                || !g.vox.sight(witness.x,witness.eyeY(),witness.z,recipient.x,recipient.eyeY(),recipient.z))return false;
        witness.damageReportTarget=0;
        known.computeIfAbsent(recipient.id,k->new java.util.HashSet<>()).add(damage.key);
        reported.computeIfAbsent(witness.id,k->new java.util.HashSet<>()).add(damage.key);
        if(trade(recipient,damage)) {
            int fee=damage.glass()?3:damage.timber()?5:6;long payer=witness.role==Npc.Role.LAWMAN?witness.id:0;
            var previous=orders.get(damage.key);
            if(previous==null || previous.payer==0 && payer!=0)orders.put(damage.key,new Order(witness.id,recipient.id,payer,payer==0?0:fee));
            g.ai.say(witness,payer==0?"The building is damaged. Can you mend it?":"Please see to the damage. You'll be paid when it is mended.");
            g.ai.reply(recipient,smoking(damage)?"Once the fire is out, I'll take a look.":"I'll see what materials it needs.");
        } else {g.ai.say(witness,"There's damage to a building. Please send a builder.");g.ai.reply(recipient,"I'll find someone who can mend it.");}
        g.state.commit("DamageReported",witness.id,recipient.id,damage.jurisdiction,0,Long.toString(damage.key));
        return true;
    }
    /**
     * (0.4.5, gamefixes: broken glass was never reported) word sent rather than carried: when the one who mends it is
     * off in the world (not in play) or the way to them will not be walked, the witness sends word - the same order made,
     * the mender knowing of it - and the report is done.
     */
    public void sendWord(Npc witness, Damage damage, Npc recipient) {
        if (!open.containsKey(damage.key)) return;
        witness.damageReportTarget = 0;
        known.computeIfAbsent(recipient.id, k -> new java.util.HashSet<>()).add(damage.key);
        reported.computeIfAbsent(witness.id, k -> new java.util.HashSet<>()).add(damage.key);
        if (trade(recipient, damage) && !orders.containsKey(damage.key)) orders.put(damage.key, new Order(witness.id, recipient.id, 0, 0));
        g.state.commit("DamageReported", witness.id, recipient.id, damage.jurisdiction, 0, damage.key + " (word sent)");
    }

    public boolean hasWork(Npc worker) {
        for(long key:known.getOrDefault(worker.id,java.util.Set.of())) {
            Damage damage=open.get(key);
            if(damage!=null && eligible(worker,damage))return true;
        }
        return false;
    }
    private boolean eligible(Npc worker,Damage damage) {
        if(!trade(worker,damage) || g.time-damage.at<SETTLE || smoking(damage) || material(worker,damage)==null
                || worker.unreachable.getOrDefault(damage.key,-1.0)>g.time)return false;
        Order order=orders.get(damage.key);
        if(order==null)return true;
        if(order.worker!=worker.id && g.actor(order.worker) instanceof Npc assigned && assigned.alive && assigned.loaded)return false;
        return order.payer==0 || g.actor(order.payer) instanceof Npc payer && payer.has(Items.COINS,order.fee) && worker.canStore(new rpg.item.ItemStack(Items.COINS,order.fee));
    }

    /** Whether this person's trade covers it: a carpenter everything, a woodcutter timber. */
    public static boolean trade(Npc n, Damage d) {
        if (n.profession == Profession.CARPENTER) return true;
        return n.role == Npc.Role.WORKER && d.timber();
    }
    /** What mending it uses up from the mender's pack: a plank for timber (a log will do), nothing for glass. Null when they have none. */
    private static boolean hammer(Npc n){return n.has(Items.HAMMER,1) || n.main()==Items.HAMMER;}
    public static ItemDef material(Npc n, Damage d) {
        if(!hammer(n))return null;
        if (d.glass()) return Items.HAMMER;
        if(!d.timber())return n.has(Items.get("stone_block"),1)?Items.get("stone_block"):null;
        if (n.has(Items.PLANK, 1)) return Items.PLANK;
        if (n.has(Items.OAK_PLANK, 1)) return Items.OAK_PLANK;
        if (n.has(Items.LOGS, 1)) return Items.LOGS;
        return null;
    }

    /** Keep real supplies for known repairs even while the settling period is still running. */
    public int reserved(Npc worker,ItemDef item) {
        if(item!=Items.PLANK && item!=Items.OAK_PLANK && item!=Items.LOGS && item!=Items.get("stone_block"))return 0;
        int count=0;for(long key:known.getOrDefault(worker.id,java.util.Set.of())) {
            Damage damage=open.get(key);if(damage!=null && trade(worker,damage) && (damage.timber()?item!=Items.get("stone_block"):!damage.glass() && item==Items.get("stone_block")))count++;
        }
        return Math.min(4,count);
    }
    public ItemDef missingSupply(Npc worker) {
        for(long key:known.getOrDefault(worker.id,java.util.Set.of())) {
            Damage damage=open.get(key);if(damage==null || !trade(worker,damage) || g.time-damage.at<SETTLE)continue;
            if(!hammer(worker))return Items.HAMMER;
            if(material(worker,damage)==null)return damage.timber()?Items.PLANK:Items.get("stone_block");
        }
        return null;
    }

    /** The next thing this person could mend now: theirs to do, settled, not smoking, nobody else on it - the nearest. */
    public Damage claim(Npc n) {
        for (var entry : new ArrayList<>(claimedBy.entrySet())) {
            var actor = g.actor(entry.getValue());
            if (!(actor instanceof Npc worker) || !worker.alive || !worker.loaded || worker.goal != rpg.ai.Brain.Goal.WORK) {
                claimedBy.remove(entry.getKey()); spots.remove(entry.getKey());
            }
        }
        Long mine = null;
        for (var e : claimedBy.entrySet()) if (e.getValue() == n.id) mine = e.getKey();
        if (mine != null && open.containsKey(mine) && eligible(n,open.get(mine))) return open.get(mine);
        if(mine!=null){claimedBy.remove(mine);spots.remove(mine);}
        Damage best = null; float nearest = Float.MAX_VALUE;
        for (Damage d : open.values()) {
            if(!known.getOrDefault(n.id,java.util.Set.of()).contains(d.key) || !eligible(n,d))continue;
            if (n.unreachable.getOrDefault(d.key,-1.0) > g.time) continue;
            if (!trade(n, d) || g.time - d.at < SETTLE || claimedBy.containsKey(d.key) && claimedBy.get(d.key) != n.id) continue;
            if (smoking(d)) continue;
            if (d.timber() && material(n, d) == null) continue;
            float dist = (float) Math.hypot(d.x + 0.5 - n.x, d.z + 0.5 - n.z);
            if (dist < nearest) { nearest = dist; best = d; }
        }
        if (best != null) claimedBy.put(best.key, n.id);
        return best;
    }
    public void release(Npc n) { for (var e : new ArrayList<>(claimedBy.entrySet())) if (e.getValue() == n.id) { claimedBy.remove(e.getKey()); spots.remove(e.getKey()); } }
    public void abandon(Npc n) {
        for (var entry : claimedBy.entrySet()) if (entry.getValue() == n.id) n.unreachable.put(entry.getKey(),g.time+45);
        release(n);
    }
    private boolean smoking(Damage d) {
        for (var c : g.chemistry.active) if ((c.state() & astratto.bridge.AstrattoNative.CHEM_BURNING) != 0 && Math.hypot(c.x() - d.x - 0.5, c.z() - d.z - 0.5) < 3) return true;
        return false;
    }

    /** (0.4.5) The spots a mender found no way to, per breakage: the next is looked for elsewhere. */
    private final Map<Long, java.util.Set<Long>> triedSpots = new HashMap<>();
    /** The spot it was making for will not be walked to: another, or the job given up for a while if none is left. */
    public void tryOtherSpot(Npc n, Damage d) {
        float[] at = spots.remove(d.key);
        if (at != null) triedSpots.computeIfAbsent(d.key, k -> new java.util.HashSet<>()).add(((long) Math.floor(at[0]) << 32) ^ (long) Math.floor(at[1]) & 0xffffffffL);
        if (standSpot(n, d) == null) { triedSpots.remove(d.key); abandon(n); }
        n.legs = null; n.hasGoalPos = false; n.failStreak = 0; n.navPauseUntil = 0;
    }

    /** Where to stand to work on it: an open cell beside it at its floor (the window's own storey, inside or out), the nearest to the mender. */
    public float[] standSpot(Npc n, Damage d) {
        float[] fixed = spots.get(d.key);
        if (fixed != null) return fixed;
        float[] best = null; float nearest = Float.MAX_VALUE;
        java.util.Set<Long> tried = triedSpots.getOrDefault(d.key, java.util.Set.of());
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            int x = d.x + o[0], z = d.z + o[1];
            if (tried.contains(((long) x << 32) ^ (long) z & 0xffffffffL)) continue;
            // the floor under the breakage's storey (a window's sill is at most a few cells over its floor), else the ground
            int floor = -1;
            for (int y = d.y; y >= d.y - 3 && floor < 0; y--) if (g.vox.solid(x, y - 1, z) && !g.vox.solid(x, y, z) && !g.vox.solid(x, y + 1, z)) floor = y;
            if (floor < 0) floor = rpg.world.Ground.cell(x, z);
            if (g.vox.solid(x, floor, z) || g.vox.solid(x, floor + 1, z) || !g.vox.solid(x, floor - 1, z)) continue;
            // the outside of a wall first (a window is glazed from the street; indoors may be locked or out of the way)
            boolean roofed = false;
            for (int y = Ground.cell(x, z) + 2; y < Ground.cell(x, z) + 9 && !roofed; y++) roofed = g.vox.solid(x, y, z);
            float dist = (float) Math.hypot(x + 0.5 - n.x, z + 0.5 - n.z) + (roofed ? 40 : 0);
            if (dist < nearest) { nearest = dist; best = new float[]{x + 0.5f, z + 0.5f}; }
        }
        if (best == null) return null;
        float[] spot = best;
        spots.put(d.key, spot);
        return spot;
    }

    /** The work done: the material used, the block put back as it was, the world's record of the burning forgotten. */
    public boolean mend(Npc n, Damage d) {
        if (!open.containsKey(d.key) || !n.alive || !eligible(n,d) || n.distTo(d.x+0.5f,d.z+0.5f)>3) return false;
        ItemDef used = material(n, d);
        if (used == null) return false;
        int now = g.vox.block(d.x, d.y, d.z);
        boolean partial=Blocks.dual(d.original) && (now==Blocks.outerOf(d.original) || now==Blocks.innerOf(d.original));
        if (now != Blocks.AIR && !Blocks.charred(now) && now != Blocks.EMBERS && !partial) { open.remove(d.key); claimedBy.remove(d.key); spots.remove(d.key); orders.remove(d.key); return false; }
        if(used!=Items.HAMMER){n.takeItems(used,1);g.state.burn(used,1);}
        Order order=orders.remove(d.key);
        if(order!=null && order.payer!=0 && g.actor(order.payer) instanceof Npc payer) {
            for(var coin:payer.takeItems(Items.COINS,order.fee)){coin.owner=n.id;coin.stolen=false;n.store(coin);}
            g.state.commit("RepairPaid",payer.id,n.id,d.jurisdiction,0,Integer.toString(order.fee));
        }
        g.chemistry.forget(d.x, d.y, d.z);
        g.setBlock(d.x, d.y, d.z, d.original);
        open.remove(d.key); claimedBy.remove(d.key); spots.remove(d.key);
        known.values().forEach(keys->keys.remove(d.key));reported.values().forEach(keys->keys.remove(d.key));
        mended++;
        g.state.commit("Repaired", n.id, 0, d.jurisdiction, 0, String.format("%s mended (%d, %d, %d) with %s", n.name, d.x, d.y, d.z, used == Items.HAMMER ? "new glass" : used.name.toLowerCase()));
        if (g.sfx != null) g.sfx.play(d.glass() ? "glass.light" : "wood", d.x + 0.5f, d.y + 0.5f, d.z + 0.5f, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------ persistence
    public List<Object> save() {
        List<Object> l = new ArrayList<>();
        for (Damage d : open.values()) {
            Map<String,Object> row=new HashMap<>(Map.of("x",d.x,"y",d.y,"z",d.z,"original",d.original,"cause",d.cause,"age",g.time-d.at));
            row.put("remaining",g.vox.block(d.x,d.y,d.z));
            row.put("known",known.entrySet().stream().filter(entry->entry.getValue().contains(d.key)).map(Map.Entry::getKey).toList());
            row.put("reported",reported.entrySet().stream().filter(entry->entry.getValue().contains(d.key)).map(Map.Entry::getKey).toList());
            Order order=orders.get(d.key);if(order!=null)row.put("order",Map.of("reporter",order.reporter,"worker",order.worker,"payer",order.payer,"fee",order.fee));
            l.add(row);
        }
        return l;
    }
    @SuppressWarnings("unchecked")
    public void load(List<Object> l) {
        clear();
        if (l == null) return;
        for (Object o : l) {
            var m = (Map<String, Object>) o;
            int x = astratto.util.Json.integer(m, "x", 0), y = astratto.util.Json.integer(m, "y", 0), z = astratto.util.Json.integer(m, "z", 0);
            open.put(key(x, y, z), new Damage(key(x, y, z), x, y, z, astratto.util.Json.integer(m, "original", 0), astratto.util.Json.str(m, "cause", ""),
                    DevMap.jurisdiction(x + 0.5, z + 0.5), g.time - astratto.util.Json.num(m, "age", 0)));
            long key=key(x,y,z);
            for(Object id:astratto.util.Json.list(m,"known"))known.computeIfAbsent(((Number)id).longValue(),k->new java.util.HashSet<>()).add(key);
            for(Object id:astratto.util.Json.list(m,"reported"))reported.computeIfAbsent(((Number)id).longValue(),k->new java.util.HashSet<>()).add(key);
            var order=astratto.util.Json.obj(m,"order");if(order!=null && !order.isEmpty())orders.put(key,new Order(astratto.util.Json.lng(order,"reporter",0),astratto.util.Json.lng(order,"worker",0),astratto.util.Json.lng(order,"payer",0),astratto.util.Json.integer(order,"fee",0)));
            // what was broken is broken again in the world as it is loaded (the map is rebuilt whole)
            int current=g.vox.block(x,y,z),original=astratto.util.Json.integer(m,"original",0);
            if(current==original || Blocks.glass(current))g.setBlock(x,y,z,astratto.util.Json.integer(m,"remaining",Blocks.AIR));
        }
    }
    public void clear() { open.clear(); claimedBy.clear(); spots.clear();known.clear();reported.clear();orders.clear();observer=0;nextObserve=0; }
}
