package rpg.monster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import astratto.bridge.CommandBuffer;
import astratto.bridge.Snapshots;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.animal.Animal;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.monster.Monster.State;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.DevMap;
import rpg.world.DungeonLayout;
import static astratto.bridge.AstrattoNative.*;

/**
 * The world's monsters (gamefixes, GDD 3.14): their lairs and populations, their bodies, and each frame's senses, minds
 * and movement. Populations are simulated, not summoned - there is no timer respawn: a fed and safe lair grows (one new
 * member at a time, to its cap), a starved one shrinks, a cleared one stands empty a long while before something moves
 * in (its own kind, or a neighbour that has outgrown its own lair). Monsters live apart from the townsfolk (they are not
 * in the NPC list: no one makes friends with a goblin), but they are actors - seen, heard, fought, searched - like any.
 */
public final class Monsters {
    private final Rpg g;
    public final MonsterMind mind;
    private final List<Monster> monsters = new ArrayList<>();
    public final List<Lair> lairs = new ArrayList<>();
    private final Map<Long, Double> nextSense = new HashMap<>();
    private double lifeAt;
    public long spawned, killed, born, recolonised;

    public Monsters(Rpg g) { this.g = g; this.mind = new MonsterMind(g, this); }

    /** A lair: the anchor a group returns to, rests, mends, feeds and breeds at (GDD: camp, cave, crypt, nest, roost). */
    public static final class Lair {
        public final long id;
        public final MonsterKind kind;
        public final String name;
        public final float x, z;
        /** The level range its members are born into, and how many it holds at most. */
        public final int minLevel, maxLevel, cap;
        public float morale = 1, food = 3;
        public double clearedAt = -1, quietSince;
        Lair(long id, MonsterKind kind, String name, float x, float z, int minLevel, int maxLevel, int cap) {
            this.id = id; this.kind = kind; this.name = name; this.x = x; this.z = z; this.minLevel = minLevel; this.maxLevel = maxLevel; this.cap = cap;
        }
    }

    // ------------------------------------------------------------------ the devmap's lairs
    /**
     * The wilds hold a few, and no more (gamefixes: monsters spawn in the wilderness, though not too much): a goblin camp in
     * the wood's north-east corner, a bat colony under the den's rock roof, a pair of imps nesting on the interaction
     * shed's roof, a young spider's web in the western wood. Bigger and deadlier kinds keep to the dungeons.
     */
    public void init() {
        Lair camp = addLair(MonsterKind.GOBLIN, "Goblin camp", DevMap.GOBLIN_CAMP[0], DevMap.GOBLIN_CAMP[1], 1, 8, 6);
        for (int i = 0; i < 4; i++) spawn(MonsterKind.GOBLIN, i == 0 ? 8 : 2 + i, camp.x + (i % 2) * 2.4f - 1.2f, camp.z + (i / 2) * 2.4f - 1.2f, camp);
        Lair roost = addLair(MonsterKind.BAT, "Bat roost", DevMap.BAT_ROOST[0], DevMap.BAT_ROOST[1], 1, 5, 7);
        for (int i = 0; i < 5; i++) spawn(MonsterKind.BAT, 1 + i % 4, roost.x - 1.6f + i * 0.8f, roost.z, roost);
        Lair nest = addLair(MonsterKind.IMP, "Imp nest", DevMap.IMP_NEST[0], DevMap.IMP_NEST[1], 1, 6, 3);
        for (int i = 0; i < 2; i++) spawn(MonsterKind.IMP, 2 + i * 2, nest.x + i, nest.z, nest);
        // a young giant spider's web deep in the western wood: abroad by night only, and only ever one or two
        float[] web = clearGround(DevMap.SPIDER_WEB[0], DevMap.SPIDER_WEB[1]);
        Lair spiders = addLair(MonsterKind.GIANT_SPIDER, "Spider's web", web[0], web[1], 1, 12, 2);
        spawn(MonsterKind.GIANT_SPIDER, 6, web[0], web[1], spiders);
    }

    public Lair addLair(MonsterKind kind, String name, float x, float z, int minLevel, int maxLevel, int cap) {
        Lair l = new Lair(g.state.newEntityId(), kind, name, x, z, minLevel, maxLevel, cap);
        lairs.add(l);
        return l;
    }
    public Lair lair(long id) { for (Lair l : lairs) if (l.id == id) return l; return null; }

    /** A monster made in the world (a birth at its lair, a recolonisation, a dungeon's resident): its body, kit, drives. */
    public Monster spawn(MonsterKind kind, int level, float x, float z, Lair lair) {
        level = Math.max(kind.minLevel, Math.min(kind.maxLevel, level));
        Monster m = new Monster(g.state.newEntityId(), kind, level, x, z);
        if (lair != null) { m.lair = lair.id; m.lairX = lair.x; m.lairZ = lair.z; }
        else { m.lairX = x; m.lairZ = z; }
        m.spawnX = x; m.spawnZ = z; m.homeX = m.lairX; m.homeZ = m.lairZ; m.postX = m.lairX; m.postZ = m.lairZ;
        long seed = m.id * 0x9E3779B97F4A7C15L;
        for (int i = 0; i < m.nudge.length; i++) { seed ^= seed >>> 29; seed *= 0xBF58476D1CE4E5B9L; m.nudge[i] = ((seed >>> 40) % 301 - 150) / 1000f; }
        m.hunger = 0.2f + (m.id % 5) * 0.08f;
        m.look = rpg.actor.Appearance.monster(kind, m.id);
        if (kind == MonsterKind.GHOST) m.spellBook = rpg.combat.Spells.Book.FOGISH;
        if (kind == MonsterKind.DRAGON) m.breed = MonsterKind.Breed.values()[(int) Math.floorMod(m.id, MonsterKind.Breed.values().length)];
        kit(m);
        m.restore();
        m.nextThink = g.time + Math.floorMod(m.id * 73, 400) / 1000.0;
        monsters.add(m);
        spawnBody(m);
        spawned++;
        g.state.commit("MonsterSpawned", 0, m.id, DevMap.jurisdiction(x, z), 0, kind.title + " level " + level + (lair == null ? "" : " at " + lair.name));
        return m;
    }

    /**
     * Sets a living monster to the strength it should have (a dungeon matched to who comes into it): the combat level
     * asked for, through its level and so its skills (its skills decide its combat level), never past the cap.
     * Its wounds keep their share.
     */
    public void relevel(Monster m, int level, int combatCap) {
        if (!m.alive) return;
        float share = m.hp / Math.max(1, m.maxHp());
        m.level = Math.max(1, level);
        m.stats();
        // (aimed at the combat level asked for, which its skills give it, then held under the cap)
        while (m.skills.combatLevel() < Math.min(level, combatCap) && m.level < 400) { m.level++; m.stats(); }
        while (m.level > 1 && m.skills.combatLevel() > combatCap) { m.level--; m.stats(); }
        m.hp = Math.max(1, share * m.maxHp());
        m.title = m.kind.title + " (level " + m.level + ")";
    }

    /** What a monster carries: its kind's weapon (or its own claws), a few throwing weapons, a bow and arrows for a skeleton archer. */
    private void kit(Monster m) {
        MonsterKind k = m.kind;
        String weapon = k.carries.length > 0 ? k.carries[(int) Math.floorMod(m.id * 31, k.carries.length)] : null;
        // the level decides the tier a carried weapon is: a low goblin's spear is wooden, a high one's bronze or better
        if (weapon != null && Items.find(weapon) != null) equip(m, Items.get(weapon), 1);
        else if (k.form == MonsterKind.Form.HUMANOID) equip(m, Items.get(k.natural.equals("touch") ? "natural_touch" : k.natural.equals("fists") ? "natural_fists" : "natural_claws"), 1);
        if (k == MonsterKind.SKELETON && m.id % 3 == 0) { equip(m, Items.SHORTBOW, 1); m.eq.set(ItemDef.Slot.AMMO, new ItemStack(Items.BRONZE_ARROWS, 20)); g.state.mint(Items.BRONZE_ARROWS, 20); }
        if (k.ranged != null && Items.find(k.ranged) != null && Items.find(k.ranged).family == ItemDef.Family.THROWN) give(m, Items.get(k.ranged), 2 + (int) (m.id % 3));
        if (k == MonsterKind.GOBLIN && m.id % 3 == 1) { equip(m, Items.get("wooden_shield"), 1); }
    }
    private void equip(Monster m, ItemDef d, int n) {
        ItemStack s = new ItemStack(d, n);
        if (d.slot == ItemDef.Slot.OFF) m.eq.set(ItemDef.Slot.OFF, s); else m.eq.set(ItemDef.Slot.MAIN, s);
        if (!d.natural) g.state.mint(d, n);
    }
    private void give(Monster m, ItemDef d, int n) { if (m.store(new ItemStack(d, n))) g.state.mint(d, n); }

    /** The body: a mover (walkers on the engine's agent, routed on the ground; flyers and floaters steered in the air). */
    public void spawnBody(Monster m) {
        CommandBuffer c = g.game.commands();
        m.body = g.game.reserve(KIND_MOVER);
        int flags = m.kind.flies() ? MOVER_FLY : MOVER_AGENT;
        c.moverCreate(m.body, m.x, m.y + 0.05f, m.z, m.radius, m.height, Math.min(1.2f, m.height * 0.4f), flags, m.height * 0.8f);
        m.agent = 0;
        if (!m.kind.flies()) g.ai.agentFor(m, m.eyeY() - m.y, m.kind.sight);
        c.moverInput(m.body, 0, 0, 0, 0, 30, flags & MOVER_FLY);
        m.loaded = true;
        if (g.chemistry != null) g.chemistry.register(m);
        nextSense.put(m.id, g.time + Math.floorMod(m.id * 97, 250) / 1000.0);
    }

    /** A monster for the gallery to pose: no lair, no mind - it goes where it is sent. */
    public Monster display(MonsterKind kind, int level, float x, float z, float yaw) {
        Monster m = spawn(kind, level, x, z, null);
        m.puppet = true; m.yaw = m.wantYaw = yaw; m.state = State.IDLE;
        return m;
    }
    /** Sends a puppet toward a point, walking or running (a flyer at the height it is given). */
    public void puppetTo(Monster m, float x, float z, boolean run) { moveTo(m, x, z, run, 0.3f); }
    /** Takes a monster out of the world altogether (a gallery puppet). */
    public void remove(Monster m) {
        despawnBody(m);
        if (m.ragdoll != 0) { g.game.commands().release(m.ragdoll); m.ragdoll = 0; }
        monsters.remove(m);
    }

    public void despawnBody(Monster m) {
        if (m.agent != 0) g.game.commands().release(m.agent);
        if (m.body != 0) g.game.commands().release(m.body);
        m.agent = 0; m.body = 0;
    }

    // ------------------------------------------------------------------ queries
    public List<Monster> all() { return monsters; }
    public Monster byId(long id) { for (Monster m : monsters) if (m.id == id) return m; return null; }
    public int living(MonsterKind k) { int n = 0; for (Monster m : monsters) if (m.alive && m.kind == k) n++; return n; }

    List<Monster> kin(Monster m, float within) {
        List<Monster> l = new ArrayList<>();
        for (Monster o : monsters) if (o != m && o.alive && o.loaded && o.kind == m.kind && (o.lair == m.lair || m.lair == 0) && o.distTo(m) < within) l.add(o);
        return l;
    }
    int nearKin(Monster m, float within) { return kin(m, within).size() + 1; }
    float[] kinCentre(Monster m) {
        float x = m.x, z = m.z; int n = 1;
        for (Monster o : kin(m, 30)) { x += o.x; z += o.z; n++; }
        return new float[]{x / n, z / n};
    }
    /** The one its group follows: the highest-levelled of its lair still standing. */
    Monster leaderOf(Monster m) {
        Monster best = null;
        for (Monster o : monsters) if (o.alive && o.lair == m.lair && m.lair != 0 && (best == null || o.level > best.level)) best = o;
        return best;
    }
    long carcassNear(Monster m, float within) {
        long best = 0; float bd = within;
        for (Animal a : g.animals.all()) if (a.carcass() && !a.butchered && a.carriedBy == 0 && a.distTo(m) < bd) { bd = a.distTo(m); best = a.id; }
        return best;
    }
    /** Prey in reach of its senses: an animal (the small ones for the small hunters), the weakest first. */
    Actor preyNear(Monster m) {
        Actor best = null; float bd = Float.MAX_VALUE;
        for (Monster.Known k : m.known.values()) {
            Actor t = g.actor(k.id);
            if (!(t instanceof Animal a) || !a.alive || a.owned() && m.kind != MonsterKind.WEREWOLF && m.kind != MonsterKind.DRAGON) continue;
            if (m.kind == MonsterKind.BAT && a.species.w > 0.45f || m.kind == MonsterKind.GOBLIN && a.species.w > 1.0f) continue;
            float score = (float) Math.hypot(k.x - m.x, k.z - m.z) + a.hp * 0.3f;
            if (score < bd) { bd = score; best = a; }
        }
        return best;
    }
    double rnd(Monster m) { return g.roll(g.state.tick, m.id); }

    /** The nearest cell to a point a big body can stand on (three cells clear round it), searching outward. */
    float[] clearGround(float x, float z) {
        for (int r = 0; r < 12; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int cx = (int) Math.floor(x) + dx, cz = (int) Math.floor(z) + dz;
            boolean ok = true;
            for (int ox = -1; ox <= 1 && ok; ox++) for (int oz = -1; oz <= 1 && ok; oz++)
                ok = g.vox.solid(cx + ox, DevMap.FLOOR - 1, cz + oz) && !g.vox.solid(cx + ox, DevMap.FLOOR, cz + oz) && !g.vox.solid(cx + ox, DevMap.FLOOR + 1, cz + oz) && g.vox.block(cx + ox, DevMap.FLOOR - 1, cz + oz) != Blocks.WATER;
            if (ok) return new float[]{cx + 0.5f, cz + 0.5f};
        }
        return new float[]{x, z};
    }

    /** The nearest open cell to a point (somewhere a body can stand), within a few cells. */
    float[] open(Monster m, float x, float z) {
        x = Math.max(2, Math.min(DevMap.SX - 3, x)); z = Math.max(2, Math.min(DevMap.SZ - 3, z));
        if (m.kind.flies()) return new float[]{x, z};
        for (int r = 0; r < 5; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            int cx = (int) Math.floor(x) + dx, cz = (int) Math.floor(z) + dz;
            if (g.vox.solid(cx, DevMap.FLOOR - 1, cz) && !g.vox.solid(cx, DevMap.FLOOR, cz) && !g.vox.solid(cx, DevMap.FLOOR + 1, cz) && g.vox.block(cx, DevMap.FLOOR - 1, cz) != Blocks.WATER) return new float[]{cx + 0.5f, cz + 0.5f};
        }
        return new float[]{m.x, m.z};
    }

    /** How high a bat hangs at its roost: just under the lowest thing over it (a canopy, a roof, a cave's ceiling). */
    float hangHeight(Monster m) {
        int bx = (int) Math.floor(m.lairX), bz = (int) Math.floor(m.lairZ);
        for (int y = DevMap.FLOOR + 1; y < DevMap.SY - 1; y++) if (g.vox.solid(bx, y, bz)) return Math.max(1.5f, y - DevMap.FLOOR - m.height - 0.1f);
        return 3;
    }

    /** How high an imp perches above the ground at its nest (the roof it nests on). */
    float perchHeight(Monster m) {
        int bx = (int) Math.floor(m.lairX), bz = (int) Math.floor(m.lairZ);
        for (int y = DevMap.SY - 2; y > DevMap.FLOOR; y--) if (g.vox.solid(bx, y, bz)) return y + 1 - DevMap.FLOOR;
        return 0;
    }

    // ------------------------------------------------------------------ moving
    /** Sends it toward a point (walking or running); true once it is within reach of it. Flyers go straight, at their height. */
    boolean moveTo(Monster m, float x, float z, boolean run, float within) {
        if (m.kind.flies()) {
            m.tx = x; m.tz = z; m.hasGoalPos = true;
            m.running = run;
            return Math.hypot(m.x - x, m.z - z) <= within;
        }
        boolean there = g.ai.walkTo(m, x, z, run, within);
        m.running = run;
        return there;
    }
    void hold(Monster m) {
        if (m.kind.flies()) { m.tx = m.x; m.tz = m.z; m.hasGoalPos = false; }
        else g.ai.desire(m, rpg.ai.AiSystem.A_HOLD);
    }

    /** A flyer's own steering: toward its goal at the height it wants, rising over what is in its way (ghosts drift straight through). */
    private void fly(Monster m, float dt) {
        float speed = (m.running ? m.kind.run : m.kind.walk) * (m.cs.frozenT > 0 ? 0 : m.cs.slowT > 0 ? 0.6f : 1);
        float dx = m.hasGoalPos ? m.tx - m.x : 0, dz = m.hasGoalPos ? m.tz - m.z : 0, l = (float) Math.hypot(dx, dz);
        float vx = 0, vz = 0;
        if (l > 0.3f) { vx = dx / l * speed; vz = dz / l * speed; }
        // bats and imps never hang still in the air: a flutter round where they mean to be
        if (m.kind != MonsterKind.GHOST && m.kind != MonsterKind.DRAGON && l < 0.6f && m.altitude > 0.5f && m.state != State.DORMANT && m.state != State.REST) {
            double a = g.time * (m.kind == MonsterKind.BAT ? 4.1 : 2.3) + m.id; vx += (float) Math.sin(a) * 0.9f; vz += (float) Math.cos(a * 1.3) * 0.9f;
        }
        float ground = groundBelow(m);
        float want = ground + m.wantAltitude;
        // never up into the roof: what flies keeps under the ceiling of the space it is in (a cave's, a crypt's, a canopy's)
        float ceiling = ceilingAbove(m, ground);
        want = Math.min(want, Math.max(ground + 0.3f, ceiling - m.height - 0.15f));
        // something ahead: rise over it (the ghost passes through)
        if (m.kind != MonsterKind.GHOST && l > 0.3f) {
            float ax = m.x + dx / l * (m.radius + 0.8f), az = m.z + dz / l * (m.radius + 0.8f);
            for (int up = 0; up < 6; up++) if (g.vox.solid((int) Math.floor(ax), (int) Math.floor(m.y + 0.2f + up), (int) Math.floor(az))) want = Math.max(want, (float) Math.floor(m.y) + up + 1.2f);
        }
        float vy = Math.max(-4, Math.min(4, (want - m.y) * 2.5f));
        if (m.kind == MonsterKind.GHOST) {
            // drifting: no body in the world's way at all - but bound to the place it haunts. A ghost born in a dungeon
            // stays inside that dungeon's rock (it passes through the walls between its halls, never out through the
            // roof to the cliff top, nor over the edge into the town below); one found outside is drawn back home
            float nx = m.x + vx * dt, ny = m.y + vy * dt, nz = m.z + vz * dt;
            DungeonLayout haunt = DungeonLayout.at(m.lairX, m.lairZ);
            if (haunt != null) {
                nx = Math.max(haunt.ox + 0.6f, Math.min(haunt.ox + haunt.w - 0.6f, nx));
                nz = Math.max(haunt.oz + 0.6f, Math.min(haunt.oz + haunt.d - 0.6f, nz));
                ny = Math.max(DevMap.FLOOR, Math.min(DevMap.FLOOR + 6, ny));
                if (DungeonLayout.at(m.x, m.z) != haunt || m.y > DevMap.FLOOR + 8) { nx = m.lairX; nz = m.lairZ; ny = DevMap.FLOOR + 0.8f; }
            }
            g.game.commands().moverPlace(m.body, nx, ny, nz, 0, 0, 0);
            m.vx = vx; m.vz = vz;
        } else g.game.commands().moverInput(m.body, vx, vz, vy, 0, m.kind == MonsterKind.DRAGON ? 6 : 18, MOVER_FLY);
        m.altitude = Math.max(0, m.y - ground);
    }

    /**
     * The floor under a flyer: the top of the first solid below the open space it is in. A ghost inside rock (passing
     * through a wall) takes the floor of the open space under that rock - not the rock it is in, which read as ground
     * under it and lifted it, cell by cell, out through the roof.
     */
    private float groundBelow(Monster m) {
        int bx = (int) Math.floor(m.x), bz = (int) Math.floor(m.z);
        int y = (int) Math.floor(m.y + 0.5f);
        while (y > 0 && g.vox.solid(bx, y, bz)) y--;
        for (; y > 0; y--) if (g.vox.solid(bx, y - 1, bz)) return y;
        return DevMap.FLOOR;
    }

    /** The underside of whatever is over a flyer's floor (the open sky: the top of the world). */
    private float ceilingAbove(Monster m, float ground) {
        int bx = (int) Math.floor(m.x), bz = (int) Math.floor(m.z);
        for (int y = (int) ground; y < DevMap.SY - 1; y++) if (g.vox.solid(bx, y, bz)) return y;
        return DevMap.SY;
    }

    // ------------------------------------------------------------------ the frame
    public void update(float dt) {
        for (Monster m : new ArrayList<>(monsters)) {
            if (!m.alive || !m.loaded || m.body == 0) continue;
            if (m.reacting) continue;
            if (m.puppet) {
                if (m.kind.flies()) fly(m, dt);
                else g.game.commands().moverInput(m.body, 0, 0, 0, 0, 30, MOVER_STANCE_ONLY);
                g.ai.faceAt(m, dt);
                continue;
            }
            g.ai.monsterSenses(m, dt);
            double due = nextSense.getOrDefault(m.id, 0.0);
            if (g.time >= due) { mind.sense(m, 0.25f); nextSense.put(m.id, g.time + 0.25); }
            mind.drives(m, dt);
            if (g.time >= m.nextThink) mind.think(m);
            m.sneaking = m.state == State.STALK;
            if (!m.cs.busy() || m.cs.phase == Actor.Combat.Phase.RECOVER) mind.act(m, dt);
            if (m.kind.flies()) fly(m, dt);
            else g.game.commands().moverInput(m.body, 0, 0, 0, 0, 30, MOVER_STANCE_ONLY | (m.sneaking ? MOVER_CROUCH : 0));
            // regeneration out of the fight (trolls mend fast; fire halts it)
            if (m.kind.regen > 0 && g.time - m.cs.lastHurt > 6 && (g.chemistry.state(m.id) & CHEM_BURNING) == 0) m.hp = Math.min(m.maxHp(), m.hp + m.kind.regen * dt);
            g.ai.faceAt(m, dt);
            if (m.speech != null && g.time > m.speechUntil) m.speech = null;
            if (g.time > m.nextVoice && (m.state == State.IDLE || m.state == State.PATROL)) { m.nextVoice = g.time + 8 + rnd(m) * 14; sound(m, m.kind.idleSound, 0.4f); }
        }
        if (g.time >= lifeAt) { lifeAt = g.time + 5; life(); }
        for (Monster m : new ArrayList<>(monsters)) if (!m.alive && !m.puppet) remains(m);
    }

    /**
     * A monster's remains lie a minute, then go: in a couple of seconds the body darkens to ash, glows at its edges and
     * sinks away in rising motes (gamefixes). What was still on it is not lost with it - it falls where the body lay.
     * Nothing goes while it is being carried.
     */
    public static final float CORPSE_SECONDS = 60, DISSOLVE_SECONDS = 2.5f;
    private void remains(Monster m) {
        if (m.carriedBy != 0) { m.diedAt = Math.max(m.diedAt, g.time - CORPSE_SECONDS + 5); m.dissolve = 0; return; }
        float t = (float) (g.time - m.diedAt) - CORPSE_SECONDS;
        m.dissolve = Math.max(0, Math.min(1, t / DISSOLVE_SECONDS));
        if (t > 0 && t - (float) g.lastDt <= 0 && g.sfx != null) g.sfx.play("monster.dissolve", m.x, m.y + 0.5f, m.z, 0.35f, 1f, 10);
        if (m.dissolve < 1) return;
        float x = m.x, z = m.z;
        if (g.objects.get(m.corpse) instanceof rpg.objects.Corpse c) {
            x = c.x; z = c.z;
            int k = 0;
            for (int i = 0; i < c.contents.size(); i++) {
                ItemStack s = c.contents.get(i);
                if (s == null) continue;
                c.contents.set(i, null);
                g.objects.drop(s, x + (k % 3 - 1) * 0.3f, DevMap.FLOOR + 0.05f, z + (k / 3 - 1) * 0.3f); k++;
            }
            c.removed = true;
        }
        for (ItemDef.Slot s : ItemDef.Slot.values()) { ItemStack w = m.eq.get(s); if (w != null && !w.def.natural) g.objects.drop(w, x, DevMap.FLOOR + 0.05f, z); m.eq.set(s, null); }
        g.state.commit("MonsterRemainsGone", 0, m.id, DevMap.jurisdiction(x, z), 0, m.kind.title);
        if (g.carrying != null) g.carrying.release(m);
        remove(m);
    }

    /** Positions from the engine (and the dead from their ragdolls). */
    public void afterFrame(Snapshots.Bodies bodies) {
        for (Monster m : monsters) {
            if (!m.alive && m.ragdoll != 0) {
                Snapshots.Ragdoll rd = bodies.ragdoll(m.ragdoll);
                if (rd == null) continue;
                if (m.posePts == null || m.posePts.length != rd.p.length) m.posePts = new float[rd.p.length];
                System.arraycopy(rd.p, 0, m.posePts, 0, rd.p.length); m.poseAt = g.time;
                m.x = rd.comX; m.z = rd.comZ;
                float low = rd.p[1]; for (int k = 1; k < RD_PARTICLES; k++) low = Math.min(low, rd.p[k * 3 + 1]);
                m.y = Math.max(0, low - 0.1f);
                if (m.corpse != 0 && g.objects.get(m.corpse) instanceof rpg.objects.Corpse c) { c.x = rd.comX; c.z = rd.comZ; c.y = m.y + 0.2f; }
                if (rd.state != SYDNEY_DEAD && rd.outcome != OUTCOME_DEAD) g.game.commands().ragdollControl(m.ragdoll, CONTROL_DIE, 1);
                continue;
            }
            if (!m.alive || !m.loaded) continue;
            Snapshots.Mover mv = bodies.mover(m.body);
            if (mv != null) { m.x = mv.x; m.y = mv.y; m.z = mv.z; m.vx = mv.vx; m.vz = mv.vz; m.grounded = mv.grounded; }
        }
    }

    /** Lairs live: breeding when fed and safe, morale recovering, empty lairs taken back in time. */
    private void life() {
        for (Lair l : lairs) {
            int alive = 0;
            boolean threatened = false;
            for (Monster m : monsters) if (m.alive && m.lair == l.id) { alive++; if (m.state == State.ENGAGE || m.state == State.FLEE) threatened = true; }
            l.morale = Math.min(1, l.morale + 0.01f);
            if (threatened) l.quietSince = g.time;
            // the night's hunting feeds the colonies that forage on the wing (insects, fruit)
            if ((l.kind == MonsterKind.BAT || l.kind == MonsterKind.IMP) && l.kind.abroad(g.state.hourOfDay())) l.food = Math.min(10, l.food + 0.02f);
            if (alive == 0) {
                if (l.clearedAt < 0) { l.clearedAt = g.state.minutes; g.state.commit("LairCleared", 0, l.id, DevMap.jurisdiction(l.x, l.z), 0, l.name); }
                // a long while empty, then something moves in: its own kind again, a few young of it
                if (g.state.minutes - l.clearedAt > 60 * 24) {
                    l.clearedAt = -1; l.morale = 1; l.food = 2;
                    for (int i = 0; i < Math.max(2, l.kind.groupMin); i++) spawn(l.kind, l.minLevel + i, l.x + i * 1.2f, l.z, l);
                    recolonised++;
                    g.state.commit("LairRecolonised", 0, l.id, DevMap.jurisdiction(l.x, l.z), 0, l.name);
                }
                continue;
            }
            // breeding: fed and left in peace, the lair grows one at a time to its cap
            if (alive < l.cap && l.food >= 2 && g.time - l.quietSince > 120 && rndLair(l) < 0.08) {
                l.food -= 2;
                spawn(l.kind, l.minLevel + (int) (rndLair(l) * (l.maxLevel - l.minLevel + 1) * 0.5), l.x, l.z, l);
                born++;
            }
        }
    }
    private double rndLair(Lair l) { return g.roll(g.state.tick, l.id); }

    // ------------------------------------------------------------------ striking (the creatures' own blows) and ranged attacks
    /** A telegraphed creature strike lands - if the target is still where the blow goes (it may have stepped away). */
    void strike(Monster m, Actor t, String what) {
        float reach = m.kind.reach * Math.max(0.6f, m.kind.scale()) + (what.equals("tail") ? 1.6f : 0);
        float d = m.distTo(t) - t.radius - m.radius;
        float facing = Math.abs(Actor.wrap(MonsterMind.yawTo(m, t.x, t.z) - m.yaw));
        if (d > reach + 0.3f || facing > (what.equals("tail") ? Math.PI : 1.0f)) { sound(m, "swish", 0.4f); return; }
        float dmg = m.kind.damage * (what.equals("tail") ? 0.8f : what.equals("claw") ? 0.9f : 1);
        g.combat.creatureBlow(m, t, dmg, m.kind.impact * (what.equals("tail") ? 1.4f : 1), m.kind.damageType, what);
        if (m.kind == MonsterKind.GIANT_SPIDER && t.alive) t.cs.slowT = Math.max(t.cs.slowT, 3);   // the venom
    }

    /** Its ranged attack, as its kind has one: a throw, a firebolt, a spectral bolt, a boulder, breath, a web, a screech, a war shout. */
    void ranged(Monster m, Actor t) {
        m.nextRanged = g.time + switch (m.kind) { case DRAGON -> 6; case TROLL -> 5; case IMP -> 2.4; default -> 3.5; };
        float yaw = MonsterMind.yawTo(m, t.x, t.z), pitch = (float) Math.atan2(t.chestY() - m.eyeY(), Math.max(0.3f, m.distTo(t)));
        m.wantYaw = yaw;
        switch (m.kind.ranged) {
            // (a creature's own magic: born to it, not learned - no book, no Mana, as strong as the creature is)
            case "firebolt" -> g.combat.castInnate(m, rpg.combat.Spells.Spell.FIRE_STRIKE, yaw, pitch);
            case "spectral" -> g.combat.castInnate(m, rpg.combat.Spells.Spell.ICE_RUSH, yaw, pitch);
            case "shortbow" -> {
                // not through one of its own kind standing in the way
                Actor between = g.combat.inLineOfFire(m, t);
                if (between instanceof Monster mo && mo.kind == m.kind) { m.nextRanged = g.time + 0.6; return; }
                if (m.main() != null && m.main().family == ItemDef.Family.BOW && m.eq.get(ItemDef.Slot.AMMO) != null) g.combat.shoot(m, yaw, pitch + m.distTo(t) * 0.004f, 1);
            }
            case "boulder" -> g.combat.hurl(m, null, "boulder", t.x, t.chestY(), t.z, 16, 14, m.kind.damage * 1.2f, m.kind.impact * 1.2f, ItemDef.DamageType.BLUNT);
            case "web" -> g.combat.hurl(m, null, "web", t.x, t.chestY(), t.z, 18, 9.8f, 1, 4, ItemDef.DamageType.NONE);
            case "screech" -> {
                sound(m, m.kind.alarmSound, 0.9f);
                for (Actor o : g.actors()) if (o != m && o.alive && !(o instanceof Monster mo && mo.kind == m.kind) && o.distTo(m) < 5) o.cs.staggered = Math.max(o.cs.staggered, 0.35f);
            }
            case "breath" -> breath(m, t);
            default -> {
                // a throw from what it carries; an orc may bellow its war shout instead
                if (m.kind == MonsterKind.ORC && rnd(m) < 0.3) {
                    sound(m, "creature_roar", 1f); m.say("WAAAGH!", g.time + 1.5);
                    for (Actor o : g.actors()) if (o != m && o.alive && !(o instanceof Monster mo && mo.kind == m.kind) && o.distTo(m) < 6) o.cs.staggered = Math.max(o.cs.staggered, 0.8f);
                    return;
                }
                ItemDef thrown = Items.find(m.kind.ranged);
                if (thrown == null || !m.has(thrown, 1)) { m.nextRanged = g.time + 30; return; }
                ItemStack one = m.takeItems(thrown, 1).get(0);
                g.combat.hurl(m, one, null, t.x, t.chestY(), t.z, 20, rpg.combat.ThrowSolver.gravityFor(thrown.thrown), thrown.damage / rpg.combat.CombatMath.HP_SCALE * 1.6f, thrown.impact, thrown.damageType);
            }
        }
    }

    /**
     * A dragon's breath (its breed's element): a cone from its jaws, the element let loose along it (the chemistry burns,
     * freezes, shocks, soaks or batters what is there), and everyone in the cone it can see struck.
     */
    private void breath(Monster m, Actor t) {
        var breed = m.breed == null ? MonsterKind.Breed.FIRE : m.breed;
        int element = switch (breed) { case FIRE -> CHEM_FIRE; case ICE -> CHEM_ICE; case ELECTRIC -> CHEM_ELECTRIC; case WATER -> CHEM_WATER; case EARTH -> CHEM_WIND; };
        float fx = (float) -Math.sin(m.yaw), fz = (float) -Math.cos(m.yaw), hy = m.eyeY();
        for (int i = 1; i <= 6; i++) {
            float d = i * 2.4f;
            g.chemistry.emit(m, element, m.x + fx * d, Math.max(DevMap.FLOOR + 0.5f, hy - d * 0.25f), m.z + fz * d, 0.8f + i * 0.35f, 4, 0.6f);
            g.chemistry.visual(breed == MonsterKind.Breed.FIRE ? 0 : 2, m.x + fx * d, hy - d * 0.25f, m.z + fz * d, 0.4f + i * 0.2f, 160, 0.4f, 0.7f, 2, 1.2f, 0.4f);
        }
        sound(m, "creature_roar", 1f);
        for (Actor o : g.actors()) {
            if (o == m || !o.alive || o instanceof Monster mo && mo.kind == MonsterKind.DRAGON) continue;
            float ox = o.x - m.x, oz = o.z - m.z, d = (float) Math.hypot(ox, oz);
            if (d > m.kind.rangedRange || d < 0.5f || (ox * fx + oz * fz) / d < 0.82f) continue;
            if (!g.vox.sight(m.x, hy, m.z, o.x, o.chestY(), o.z)) continue;
            g.combat.creatureBlow(m, o, m.kind.damage * 0.8f * (1 - d / (m.kind.rangedRange * 1.4f)), 40, ItemDef.DamageType.MAGIC, breed.title.toLowerCase() + " breath");
        }
    }

    // ------------------------------------------------------------------ death
    /** Killed: what it leaves (its kind's drop table, rolled once), its group shaken, its natural weapons gone with it. */
    public void onKilled(Monster m, Actor killer) {
        killed++;
        // natural weapons are part of it
        for (ItemDef.Slot s : new ItemDef.Slot[]{ItemDef.Slot.MAIN, ItemDef.Slot.OFF}) { ItemStack w = m.eq.get(s); if (w != null && w.def.natural) m.eq.set(s, null); }
        for (ItemStack s : Loot.roll(g, m)) { if (!m.store(s)) g.objects.drop(s, m.x, m.y + 0.1f, m.z); g.state.mint(s.def, s.amount); }
        Lair l = lair(m.lair);
        Monster lead = l == null ? null : leaderOf(m);
        if (l != null) {
            boolean wasLeader = lead == null || lead.level <= m.level;
            l.morale = Math.max(0, l.morale - (wasLeader ? 0.45f : 0.2f));
            for (Monster o : monsters) if (o != m && o.alive && o.lair == l.id && o.distTo(m) < 20) { o.fear = Math.min(1.5f, o.fear + (wasLeader ? 0.5f : 0.2f)); o.nextThink = g.time + 0.2; }
        }
        sound(m, m.kind.deathSound, 0.9f);
        g.state.commit("MonsterKilled", killer == null ? 0 : killer.id, m.id, DevMap.jurisdiction(m.x, m.z), 0, m.kind.title + " level " + m.level);
    }

    /** Struck (the combat system tells it): remembered against whoever did it. */
    public void onHurt(Monster m, Actor by, float damage) { mind.onHurt(m, by, damage); }

    // ------------------------------------------------------------------ voice, words, saving
    public void sound(Monster m, String key, float vol) {
        if (g.sfx == null || key == null) return;
        String bank = g.sfx.has(key) ? key : g.sfx.has(key + "_01") ? key + "_01" : null;
        if (bank == null) return;
        float pitch = 1.25f - Math.min(0.75f, m.kind.scale() * 0.22f);
        g.sfx.play(bank, m.x, m.eyeY(), m.z, vol, pitch, 18 * vol);
    }

    public String examine(Monster m) {
        String state = switch (m.state) {
            case DORMANT -> "It is still, resting at its lair."; case IDLE -> "It is about its own business."; case PATROL -> "It is roaming its territory.";
            case ALERT -> "It has noticed something, and is watching."; case INVESTIGATE -> "It is creeping toward something it sensed."; case STALK -> "It is shadowing someone.";
            case ENGAGE -> "It is fighting."; case FLEE -> "It is fleeing."; case REGROUP -> "It is falling back to its pack."; case FEED -> "It is feeding.";
            case REST -> "It is resting."; case RETURN -> "It is heading back to its lair.";
        };
        return m.label() + ". " + state;
    }

    public Map<String, Object> save(java.util.function.Function<Actor, Map<String, Object>> record) {
        Map<String, Object> root = new HashMap<>();
        List<Object> ls = new ArrayList<>();
        for (Lair l : lairs) {
            Map<String, Object> o = new HashMap<>();
            o.put("id", l.id); o.put("kind", l.kind.name()); o.put("name", l.name); o.put("x", l.x); o.put("z", l.z); o.put("min", l.minLevel); o.put("max", l.maxLevel); o.put("cap", l.cap);
            o.put("morale", l.morale); o.put("food", l.food); o.put("cleared", l.clearedAt);
            ls.add(o);
        }
        root.put("lairs", ls);
        List<Object> ms = new ArrayList<>();
        for (Monster m : monsters) {
            Map<String, Object> o = record.apply(m);
            o.put("kind", m.kind.name()); o.put("level", m.level); o.put("lair", m.lair); o.put("lairX", m.lairX); o.put("lairZ", m.lairZ);
            o.put("hunger", (double) m.hunger); o.put("fatigue", (double) m.fatigue); o.put("loaded", m.loaded);
            if (m.breed != null) o.put("breed", m.breed.name());
            var rd = !m.alive && m.ragdoll != 0 ? g.lastBodies.ragdoll(m.ragdoll) : null;
            if (rd != null) { List<Object> pose = new ArrayList<>(); for (float v : rd.p) pose.add((double) v); o.put("pose", pose); }
            o.put("corpse", m.corpse);
            if (!m.alive) o.put("deadFor", g.time - m.diedAt);
            ms.add(o);
        }
        root.put("monsters", ms);
        return root;
    }

    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> root, java.util.function.BiConsumer<Actor, Map<String, Object>> apply) {
        for (Monster m : monsters) { despawnBody(m); if (m.ragdoll != 0) { g.game.commands().release(m.ragdoll); m.ragdoll = 0; } }
        monsters.clear(); lairs.clear(); nextSense.clear();
        if (root == null) { init(); return; }
        for (Object o : astratto.util.Json.list(root, "lairs")) {
            Map<String, Object> r = (Map<String, Object>) o;
            Lair l = new Lair(astratto.util.Json.lng(r, "id", 0), MonsterKind.valueOf(astratto.util.Json.str(r, "kind", "GOBLIN")), astratto.util.Json.str(r, "name", "Lair"),
                    (float) astratto.util.Json.num(r, "x", 0), (float) astratto.util.Json.num(r, "z", 0), astratto.util.Json.integer(r, "min", 1), astratto.util.Json.integer(r, "max", 5), astratto.util.Json.integer(r, "cap", 4));
            l.morale = (float) astratto.util.Json.num(r, "morale", 1); l.food = (float) astratto.util.Json.num(r, "food", 2); l.clearedAt = astratto.util.Json.num(r, "cleared", -1);
            lairs.add(l);
        }
        for (Object o : astratto.util.Json.list(root, "monsters")) {
            Map<String, Object> r = (Map<String, Object>) o;
            MonsterKind kind;
            try { kind = MonsterKind.valueOf(astratto.util.Json.str(r, "kind", "")); } catch (IllegalArgumentException e) { continue; }
            Monster m = new Monster(astratto.util.Json.lng(r, "id", 0), kind, astratto.util.Json.integer(r, "level", kind.minLevel), (float) astratto.util.Json.num(r, "x", 0), (float) astratto.util.Json.num(r, "z", 0));
            m.look = rpg.actor.Appearance.monster(kind, m.id);
            apply.accept(m, r);
            m.alive = astratto.util.Json.bool(r, "alive", true);
            m.stats();
            m.lair = astratto.util.Json.lng(r, "lair", 0); m.lairX = (float) astratto.util.Json.num(r, "lairX", m.x); m.lairZ = (float) astratto.util.Json.num(r, "lairZ", m.z);
            m.homeX = m.lairX; m.homeZ = m.lairZ;
            m.hunger = (float) astratto.util.Json.num(r, "hunger", 0.3); m.fatigue = (float) astratto.util.Json.num(r, "fatigue", 0);
            if (r.containsKey("breed")) m.breed = MonsterKind.Breed.valueOf(astratto.util.Json.str(r, "breed", "FIRE"));
            if (kind == MonsterKind.GHOST) m.spellBook = rpg.combat.Spells.Book.FOGISH;
            monsters.add(m);
            if (m.alive && astratto.util.Json.bool(r, "loaded", true)) spawnBody(m);
            else if (!m.alive) {
                m.diedAt = g.time - astratto.util.Json.num(r, "deadFor", 0);
                m.ragdoll = g.game.reserve(KIND_RAGDOLL);
                g.game.commands().ragdollCreate(m.ragdoll, m.ragdollScale(), m.sydneyProfile(), RAGDOLL_ACTIVE, m.x, DevMap.FLOOR, m.z, m.yaw, 0, 0, 0);
                List<Object> pose = astratto.util.Json.list(r, "pose");
                if (pose != null && pose.size() >= RD_PARTICLES * 3) { float[] p = new float[pose.size()]; for (int i = 0; i < p.length; i++) p[i] = ((Number) pose.get(i)).floatValue(); g.game.commands().ragdollPose(m.ragdoll, 0, 0, 0, p); }
                g.game.commands().ragdollControl(m.ragdoll, CONTROL_DIE, 1);
                m.corpse = astratto.util.Json.lng(r, "corpse", 0);
            }
        }
    }
}
