package rpg;

import astratto.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.ai.Brain;
import rpg.ai.Memory;
import rpg.ai.Npc;
import rpg.interact.WorldObject;
import rpg.item.ItemIO;
import rpg.objects.Container;
import rpg.objects.Fire;
import rpg.objects.GroundItem;
import rpg.state.DomainEvent;
import rpg.world.DevMap;

/**
 * Save and load (GDD 3.8 invariant 7): ids, ownership, items, XP, NPC memory and life, quest facts and failure reasons,
 * shipments, bounties, node and door states, fires, the clock and the event log all survive. Fixtures are rebuilt by
 * id and given their saved state; things made in play are recreated from the save. The schema is versioned.
 */
public final class SaveSystem {
    public static final int SCHEMA = 1;
    private final Rpg g;

    public SaveSystem(Rpg g) { this.g = g; }

    public static Path slot(String name) { return Path.of("saves", name + ".json"); }

    public boolean save(Path file) {
        // a death still playing out is seen through first: the grave made, the player awake at the graveyard
        if (g.death.active) g.death.proceed(false);
        // an arrow or bolt in flight when the game is saved comes down where it is (a save keeps no flights): its ammunition
        // is on the ground in the save, as it would be a moment later, instead of gone from the world
        g.carrying.dropAll();g.waterContacts.reset();
        for (Actor actor : g.actors()) { g.traversal.cancel(actor); if(!(actor instanceof rpg.animal.Animal))g.reactions.cancel(actor); }
        g.combat.landAll();
        Map<String, Object> root = new HashMap<>();
        root.put("schema", SCHEMA);
        root.put("npcTraversalProfiles", 1);
        root.put("revision", g.state.revision);
        // the world state's bookkeeping
        Map<String, Object> st = new HashMap<>();
        List<Object> c = new ArrayList<>(); for (long v : g.state.counters()) c.add(v);
        st.put("counters", c);
        st.put("minutes", g.state.minutes);
        Map<String, Object> led = new HashMap<>(); for (var e : g.state.ledger().entrySet()) led.put(e.getKey(), e.getValue());
        st.put("ledger", led);
        List<Object> log = new ArrayList<>();
        List<DomainEvent> events = g.state.all();
        for (DomainEvent e : events.subList(Math.max(0, events.size() - 300), events.size())) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.id()); m.put("tick", e.tick()); m.put("min", e.gameMinutes()); m.put("rev", e.revision()); m.put("type", e.type());
            m.put("actor", e.actor()); m.put("subject", e.subject()); m.put("jur", e.jurisdiction()); m.put("cause", e.cause()); m.put("payload", e.payload());
            log.add(m);
        }
        st.put("log", log);
        root.put("state", st);
        root.put("player", actor(g.player));
        List<Object> npcs = new ArrayList<>();
        for (Npc n : g.npcs) {
            Map<String, Object> m = actor(n);
            m.put("loaded", n.loaded); m.put("hunger", (double) n.hunger); m.put("fatigue", (double) n.fatigue); m.put("social", (double) n.social);
            if (n.profession != null) m.put("profession",n.profession.name()); m.put("dutyTarget",n.dutyTarget); m.put("dutyPhase",n.dutyPhase); m.put("dutyX",n.dutyX); m.put("dutyZ",n.dutyZ); m.put("dutyLeft",Math.max(0,n.dutyUntil-g.time));
            m.put("arrested", n.arrestedUntil); m.put("arrestJur", n.arrestJurisdiction); m.put("patrol", n.patrolIndex);
            if (n.playerSighting != null) {
                var sight = n.playerSighting;
                m.put("playerSighting", Map.of("witness", sight.witness(), "x", sight.x(), "z", sight.z(), "vx", sight.vx(), "vz", sight.vz(), "age", g.time - sight.at()));
            }
            if (n.posse) { m.put("posse", true); m.put("posseJur", n.homeJurisdiction); }
            if (n.standing != Npc.Standing.COMMON) m.put("standing", n.standing.name());
            List<Object> mem = new ArrayList<>(); for (Memory x : n.memories) mem.add(x.save());
            m.put("memories", mem);
            // a body lies as it lay: its fifteen points, so a load puts it back on the ground (never standing up to fall again)
            var rd = !n.alive && n.ragdoll != 0 ? g.lastBodies.ragdoll(n.ragdoll) : null;
            if (rd != null) { List<Object> pose = new ArrayList<>(); for (float v : rd.p) pose.add((double) v); m.put("pose", pose); }
            npcs.add(m);
        }
        root.put("npcs", npcs);
        List<Object> objs = new ArrayList<>();
        for (WorldObject o : g.objects.everything()) {
            if(o instanceof rpg.world.Foliage.Patch)continue;
            if (o.def.equals("cargo")) continue;   // the economy saves its crates
            if (o.id >= Ids.DYNAMIC && o.removed) continue;
            Map<String, Object> m = o.save();
            m.put("id", o.id); m.put("def", o.def); m.put("kind", o.getClass().getSimpleName());
            if (o instanceof Container ct) {
                m.put("x", ct.x); m.put("y", ct.y); m.put("z", ct.z); m.put("name", ct.name); m.put("ckind", ct.kind); m.put("ownerOnly", ct.ownerOnly);
                // a grave keeps its standing: the seconds it has left, whether it is secured, who killed its owner
                if (ct.kind.equals("grave")) { m.put("graveLeft", Math.max(0, ct.expiresAt - g.time)); m.put("secured", ct.secured); m.put("killer", ct.killer); m.put("honor", ct.honor); }
            }
            if (o instanceof rpg.objects.Corpse cp) m.put("body", cp.body);
            objs.add(m);
        }
        root.put("objects", objs);
        root.put("econ", g.econ.save());
        root.put("repairs", g.repairs.save());
        root.put("animals", g.animals.save());
        root.put("monsters", g.monsters.save(this::actor));
        root.put("dungeons", g.dungeons.save());
        root.put("law", g.law.save());
        root.put("undertaker", g.undertaker.save());
        root.put("honor", g.honor.save());
        root.put("chemistry", g.chemistry.save());root.put("foliage",g.foliage.save());
        root.put("timeSystem",g.timeSystem.save());
        root.put("quests", g.quests.save());
        root.put("invariants", g.invariants.save());
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, Json.write(root), StandardCharsets.UTF_8);
            g.state.commit("GameSaved", g.player.id, 0, null, 0, file.toString() + " at revision " + g.state.revision);
            return true;
        } catch (IOException e) {
            g.chat.warn("Could not save: " + e.getMessage());
            return false;
        }
    }

    private Map<String, Object> actor(Actor a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.id); m.put("name", a.name); m.put("alive", a.alive);
        m.put("x", a.x); m.put("y", a.y); m.put("z", a.z); m.put("yaw", a.yaw);
        m.put("hp", a.hp); m.put("stamina", a.stamina); m.put("faith", a.faith); m.put("mana", a.mana);
        List<Object> xp = new ArrayList<>(); for (long v : a.skills.raw()) xp.add(v);
        m.put("xp", xp);
        m.put("inv", ItemIO.inventory(a.inv)); m.put("invW", a.inv.w);
        m.put("eq", ItemIO.equipment(a.eq));
        List<Object> look = new ArrayList<>(); for (int v : a.look.toArray()) look.add(v);
        m.put("look", look);
        if (a instanceof rpg.actor.Player p) { m.put("spell", p.spell.name()); m.put("fp", p.morphCameraSaved ? p.morphFirstPerson : p.firstPerson); m.put("book", p.book.name());
            java.util.List<Object> quick = new java.util.ArrayList<>(); for (var q : p.quick) quick.add(q == null ? "" : q.item().id); m.put("quick", quick); }
        m.put("manaAcc", (double) a.manaAcc);
        m.put("injuries",a.injuries.save()); m.put("charred",a.charred); m.put("eatenMinutes",a.eatenMinutes); m.put("morphT",a.morphT); m.put("chronoT",a.chronoT); m.put("summonOwner",a.summonOwner); m.put("summonLeft",Math.max(0,a.summonUntil-g.time));
        if(a instanceof Npc npc)m.put("spellBook",npc.spellBook.name());
        if (g.prayers != null) m.put("prayers", g.prayers.save(a));
        return m;
    }

    /** Migrate structured legal keys without touching player names, dialogue or arbitrary item text. */
    @SuppressWarnings("unchecked")
    private static void migrateJurisdictions(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            if (map.containsKey("JUR-ALPHA")) map.put(DevMap.ALPHA, map.remove("JUR-ALPHA"));
            if (map.containsKey("JUR-BETA")) map.put(DevMap.BETA, map.remove("JUR-BETA"));
            for (String key : List.of("jur", "arrestJur", "jurisdiction")) {
                if ("JUR-ALPHA".equals(map.get(key))) map.put(key, DevMap.ALPHA);
                if ("JUR-BETA".equals(map.get(key))) map.put(key, DevMap.BETA);
            }
            for (Object child : map.values()) migrateJurisdictions(child);
        } else if (value instanceof List<?> list) for (Object child : list) migrateJurisdictions(child);
    }

    @SuppressWarnings("unchecked")
    public boolean load(Path file) {
        g.carrying.dropAll();
        for (Actor actor : g.actors()) { g.falling.reset(actor);g.traversal.cancel(actor); if(!(actor instanceof rpg.animal.Animal))g.reactions.cancel(actor); }
        Map<String, Object> root;
        try { root = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8)); }
        catch (IOException | RuntimeException e) { g.chat.warn("Could not load " + file + ": " + e.getMessage()); return false; }
        if (Json.integer(root, "schema", 0) != SCHEMA) { g.chat.warn("That save is from another schema version."); return false; }
        g.chemistry.beforeLoad();
        migrateJurisdictions(root);
        g.interact.work.clear();
        g.combat.projectiles.clear();
        if (g.ui != null) g.ui.closeAll();
        Map<String, Object> st = Json.obj(root, "state");
        List<Object> c = Json.list(st, "counters");
        long[] counters = new long[c.size()];
        for (int i = 0; i < counters.length; i++) counters[i] = ((Number) c.get(i)).longValue();
        g.state.loadCounters(counters);
        g.state.minutes = Json.num(st, "minutes", 480);
        Map<String, Long> led = new HashMap<>();
        for (var e : Json.obj(st, "ledger").entrySet()) led.merge(rpg.item.Items.canonicalSavedId(e.getKey()), ((Number) e.getValue()).longValue(),Long::sum);
        g.state.loadLedger(led);
        List<DomainEvent> log = new ArrayList<>();
        for (Object o : Json.list(st, "log")) {
            Map<String, Object> m = (Map<String, Object>) o;
            log.add(new DomainEvent(Json.lng(m, "id", 0), Json.lng(m, "tick", 0), Json.num(m, "min", 0), Json.lng(m, "rev", 0), Json.str(m, "type", "?"),
                    Json.lng(m, "actor", 0), Json.lng(m, "subject", 0), Json.str(m, "jur", null), Json.lng(m, "cause", 0), Json.str(m, "payload", "")));
        }
        g.state.loadLog(log);
        // the player
        Map<String, Object> pm = Json.obj(root, "player");
        applyActor(g.player, pm);
        try { g.player.spell = rpg.combat.Spells.Spell.of(Json.str(pm, "spell", "WIND_STRIKE")); } catch (IllegalArgumentException e) { g.player.spell = rpg.combat.Spells.Spell.WIND_STRIKE; }
        try { g.player.book = rpg.combat.Spells.Book.of(Json.str(pm, "book", "SUN")); } catch (IllegalArgumentException e) { g.player.book = rpg.combat.Spells.Book.SUN; }
        g.player.firstPerson = Json.bool(pm, "fp", false); g.player.morphCameraSaved = false;
        java.util.Arrays.fill(g.player.quick, null);
        var quick = Json.list(pm, "quick");
        for (int i = 0; i < Math.min(8, quick.size()); i++) if (quick.get(i) instanceof String id && !id.isEmpty() && rpg.item.Items.saved(id) != null)
            g.player.quick[i] = new rpg.actor.Player.QuickSlot(rpg.item.Items.saved(id), 0);
        g.player.refreshQuick();
        g.player.alive = Json.bool(pm, "alive", true);
        if (!g.player.alive) { g.player.alive = true; g.player.restore(); }
        g.player.diedAt = -1;
        g.teleport(g.player, g.player.x, g.player.y, g.player.z);
        for(Npc old:new ArrayList<>(g.npcs))if(old.summonOwner!=0 || old.posse)g.magic.dismiss(old);
        var savedNpcIds=new java.util.HashSet<Long>();
        for(Object record:Json.list(root,"npcs"))savedNpcIds.add(Json.lng((Map<String,Object>)record,"id",-1));
        // NPCs: life and death, bodies, minds, memories
        for (Object o : Json.list(root, "npcs")) {
            Map<String, Object> m = (Map<String, Object>) o;
            Actor a = g.actor(Json.lng(m, "id", -1));
            if(a==null && Json.lng(m,"summonOwner",0)!=0) {
                Npc ally=new Npc(Json.lng(m,"id",-1),Json.str(m,"name","Bound revenant"),Npc.Role.HUNTER,"Conjured ally",(float)Json.num(m,"x",0),(float)Json.num(m,"z",0));
                Actor owner=g.actor(Json.lng(m,"summonOwner",0)); ally.team=owner==null?0:owner.team;
                ally.loaded=false; g.npcs.add(ally); a=ally;
            }
            if(a==null && Json.bool(m,"posse",false)) {
                Npc hunter=new Npc(Json.lng(m,"id",-1),Json.str(m,"name","Bounty hunter"),Npc.Role.HUNTER,"Bounty Hunter",(float)Json.num(m,"x",0),(float)Json.num(m,"z",0));
                hunter.posse=true; hunter.homeJurisdiction=Json.str(m,"posseJur",DevMap.ALPHA);
                hunter.loaded=false; g.npcs.add(hunter); a=hunter;
            }
            if(a instanceof Npc ranked) try { ranked.standing=Npc.Standing.valueOf(Json.str(m,"standing","COMMON")); } catch(IllegalArgumentException invalid) { ranked.standing=Npc.Standing.COMMON; }
            if (!(a instanceof Npc n)) continue;
            boolean alive = Json.bool(m, "alive", true), loaded = Json.bool(m, "loaded", true);
            if (n.alive && n.loaded) g.ai.despawnBody(n);
            if (n.ragdoll != 0) { g.game.commands().release(n.ragdoll); n.ragdoll = 0; }
            n.corpse=0;
            var sight = Json.obj(m, "playerSighting");
            n.playerSighting = sight == null ? null : new Npc.Sighting(Json.lng(sight,"witness",0), (float)Json.num(sight,"x",0), (float)Json.num(sight,"z",0), (float)Json.num(sight,"vx",0), (float)Json.num(sight,"vz",0), g.time-Json.num(sight,"age",600));
            applyActor(n, m);
            if(m.containsKey("profession")) try { rpg.ai.Profession.valueOf(Json.str(m,"profession","")).assign(n); } catch(IllegalArgumentException invalid) { g.chat.warn("Ignored incompatible saved occupation for "+n.name); }
            g.ai.brain.resetJob(n);
            n.dutyPhase=Json.integer(m,"dutyPhase",0); n.dutyTarget=Json.lng(m,"dutyTarget",0); n.dutyX=(float)Json.num(m,"dutyX",n.postX); n.dutyZ=(float)Json.num(m,"dutyZ",n.postZ); n.dutyUntil=g.time+Json.num(m,"dutyLeft",0);
            if (Json.integer(root, "npcTraversalProfiles", 0) == 0 && n.skills.level(rpg.skill.Skill.AGILITY) == 1) n.skills.setLevel(rpg.skill.Skill.AGILITY, Npc.defaultAgility(n.role));
            n.hunger = (float) Json.num(m, "hunger", 0); n.fatigue = (float) Json.num(m, "fatigue", 0); n.social = (float) Json.num(m, "social", 0);
            n.arrestedUntil = Json.num(m, "arrested", -1); n.arrestJurisdiction = Json.str(m, "arrestJur", DevMap.jurisdiction(n.x, n.z)); n.patrolIndex = Json.integer(m, "patrol", 0);
            n.memories.clear();
            List<Object> mem = Json.list(m, "memories");
            if (mem != null) for (Object x : mem) n.remember(Memory.load((Map<String, Object>) x), 24);
            n.goal = Brain.Goal.IDLE; n.nextThink = 0; n.fightTarget = 0; n.cs.lastAttacker = 0; n.hasGoalPos = false; n.speech = null;
            n.alive = alive;
            n.loaded = loaded;
            if (alive && loaded) g.ai.spawnBody(n);
            else if (!alive) {
                n.diedAt = g.time;
                n.ragdoll = g.game.reserve(astratto.bridge.AstrattoNative.KIND_RAGDOLL);
                g.game.commands().ragdollCreate(n.ragdoll, n.ragdollScale(), n.sydneyProfile(), astratto.bridge.AstrattoNative.RAGDOLL_ACTIVE, n.x, DevMap.FLOOR, n.z, n.yaw, 0, 0, 0);
                List<Object> pose = Json.list(m, "pose");
                if (pose != null && pose.size() == astratto.bridge.AstrattoNative.RD_PARTICLES * 3) {
                    float[] p = new float[pose.size()];
                    for (int i = 0; i < p.length; i++) p[i] = ((Number) pose.get(i)).floatValue();
                    n.posePts=p; n.poseAt=g.time;
                    g.game.commands().ragdollPose(n.ragdoll, 0, 0, 0, p);
                }
                g.game.commands().ragdollControl(n.ragdoll, astratto.bridge.AstrattoNative.CONTROL_DIE, 1);
                if (g.gore != null) g.gore.limbsToBody(n);
            }
        }
        // objects: fixtures by id; things made in play recreated
        g.objects.everything().removeIf(o -> o.id >= Ids.DYNAMIC && !o.def.equals("cargo"));
        for (Object o : Json.list(root, "objects")) {
            Map<String, Object> m = (Map<String, Object>) o;
            long id = Json.lng(m, "id", 0);
            if (id < Ids.DYNAMIC) { WorldObject w = g.objects.get(id); if (w != null) w.load(g, m); continue; }
            String kind = Json.str(m, "kind", "");
            float x = (float) Json.num(m, "x", 0), y = (float) Json.num(m, "y", 0), z = (float) Json.num(m, "z", 0);
            switch (kind) {
                case "GroundItem" -> { var s = ItemIO.stack(m.get("stack")); if (s != null) g.objects.add(new GroundItem(id, s, x, y, z)).restorePose(m); }
                case "Fire" -> g.objects.add(new Fire(id, x, y, z, Json.num(m, "outAt", 0), Json.lng(m, "owner", 0))).load(g,m);
                case "FishingNet" -> {
                    var net=g.objects.add(new rpg.objects.FishingNet(id,Json.lng(m,"owner",0),Json.lng(m,"source",0),x,y,z,Json.num(m,"nextCatch",g.state.minutes+4)));net.load(g,m);
                }
                case "Trap" -> {
                    var trap=g.objects.add(new rpg.objects.Trap(id,Json.lng(m,"owner",0),x,y,z));trap.load(g,m);
                }
                case "Container", "Corpse" -> {
                    long body = Json.lng(m, "body", 0);
                    Container ct = body != 0 && g.actor(body) instanceof Npc deadActor && !deadActor.alive
                            ? g.objects.add(new rpg.objects.Corpse(id, g.actor(body).name, body, x, y, z, deadActor instanceof rpg.monster.Monster ? 0 : Json.lng(m, "owner", 0)))
                            : g.objects.add(new Container(id, Json.str(m, "def", "grave"), Json.str(m, "name", "Grave"), Json.str(m, "ckind", "grave"), x, y, z, Json.integer(m, "gridCells", 28), Json.lng(m, "owner", 0)));
                    if (ct instanceof rpg.objects.Corpse cp && g.actor(body) instanceof rpg.ai.Npc dead) dead.corpse = cp.id;
                    ct.ownerOnly = Json.bool(m, "ownerOnly", false);
                    if (ct.kind.equals("grave")) {
                        ct.expiresAt = g.time + Json.num(m, "graveLeft", rpg.law.Undertaker.GRAVE_SECONDS);
                        ct.secured = Json.bool(m, "secured", !DevMap.WILDS.equals(DevMap.jurisdiction(x, z)));
                        ct.killer = Json.lng(m, "killer", 0);
                        ct.honor = (int) Json.lng(m, "honor", 0);
                    }
                    ct.load(g, m);
                }
                default -> { }
            }
        }
        for (Npc dead : g.npcs) if (!dead.alive && dead.corpse == 0 && dead.ragdoll != 0) { g.game.commands().release(dead.ragdoll); dead.ragdoll = 0; }
        g.bank.migrateAccounts();
        // Create newly authored residents after restoring dynamic objects: starter overflow drops must survive migration.
        var addedResidents=g.ai.restoreMissingRoster(savedNpcIds);
        g.econ.load(Json.obj(root, "econ"));
        g.repairs.load(Json.list(root, "repairs"));
        g.animals.load(Json.obj(root, "animals"));
        g.monsters.load(Json.obj(root, "monsters"), this::applyActor);
        g.dungeons.load(Json.obj(root, "dungeons"));
        g.law.load(Json.obj(root, "law"));
        g.undertaker.load(Json.obj(root, "undertaker"));
        g.honor.load(Json.obj(root, "honor"));
        g.chemistry.load(Json.list(root, "chemistry"));g.foliage.load(Json.obj(root,"foliage"));g.traps.afterLoad();
        g.timeSystem.load(Json.obj(root,"timeSystem"));
        g.quests.load(Json.obj(root, "quests"));
        g.invariants.load(Json.obj(root, "invariants"));
        for(Npc resident:addedResidents)g.invariants.rebase(resident);
        g.objects.supportBeds();
        g.ai.syncAbstractClock();
        g.mapDirty = true;
        g.mapColumns.clear();   // (a load redraws the whole map)
        g.state.commit("GameLoaded", g.player.id, 0, null, 0, file + " (saved at revision " + Json.lng(root, "revision", 0) + ")");
        return true;
    }

    private void applyActor(Actor a, Map<String, Object> m) {
        a.x = (float) Json.num(m, "x", a.x); a.y = (float) Json.num(m, "y", a.y); a.z = (float) Json.num(m, "z", a.z); a.yaw = (float) Json.num(m, "yaw", 0);
        List<Object> xp = Json.list(m, "xp");
        long[] v = new long[xp.size()];
        for (int i = 0; i < v.length; i++) v[i] = ((Number) xp.get(i)).longValue();
        a.skills.load(v);
        var carried = Json.list(m, "inv");
        if (Json.integer(m, "invW", 10) == 10) carried = ItemIO.transpose(carried);
        ItemIO.inventory(a.inv, carried);
        ItemIO.equipment(a.eq, Json.list(m, "eq"));
        List<Object> look = Json.list(m, "look");
        // (a townsperson's look is theirs from the roster: an old save's rolled look could give a man a woman's body and cry)
        if (look != null && (!(a instanceof rpg.ai.Npc) || Json.lng(m,"summonOwner",0)!=0)) { int[] l = new int[look.size()]; for (int i = 0; i < l.length; i++) l[i] = ((Number) look.get(i)).intValue(); a.look = Appearance.fromArray(l); }
        a.hp = (float) Json.num(m, "hp", a.maxHp()); a.stamina = (float) Json.num(m, "stamina", a.maxStamina());
        a.faith = (float) Json.num(m, "faith", a.maxFaith()); a.mana = (float) Json.num(m, "mana", a.maxMana());
        a.cs.cancel(); a.cs.staggered = 0;
        a.manaAcc = (float) Json.num(m, "manaAcc", 0);
        a.injuries.load(Json.list(m,"injuries"));a.charred=(float)Json.num(m,"charred",0);a.burnSince=-1;a.rollContact=0;
        a.eatenMinutes=(float)Json.num(m,"eatenMinutes",0);
        a.morphT=(float)Json.num(m,"morphT",0); a.chronoT=(float)Json.num(m,"chronoT",0); a.summonOwner=Json.lng(m,"summonOwner",0); a.summonUntil=g.time+Json.num(m,"summonLeft",0);
        if(a instanceof Npc npc)npc.spellBook=rpg.combat.Spells.Book.valueOf(Json.str(m,"spellBook",npc.spellBook.name()));
        if (g.prayers != null) g.prayers.load(a, Json.obj(m, "prayers"));
    }
}
