def sub(p, a, b, cnt=1):
    s = open(p, encoding='utf-8').read()
    assert a in s, (p, a[:90])
    s = s.replace(a, b, cnt)
    open(p, 'w', encoding='utf-8').write(s)

F = 'src/main/java/rpg/objects/Furniture.java'
sub(F, '''    public float yaw() { return facing * (float) Math.PI / 2; }''', '''    public float yaw() { return facing * (float) Math.PI / 2; }
    /**
     * (0.4.7, gamefixes: people pull a chair out to sit and push it back when they rise) how far a chair stands out from
     * its table (0 tucked under, 1 drawn right out: CHAIR_PULL m back from the table), eased toward pullTarget; and who
     * sits in it (0: no one).
     */
    public transient float pull, pullTarget;
    public transient long sitter;
    public static final float CHAIR_PULL = 0.45f;
    /** Whether a person sits on it (a chair, a stool, a bench, an armchair, a settee). */
    public boolean seat() { return kind == Kind.CHAIR || kind == Kind.STOOL || kind == Kind.BENCH || kind == Kind.ARMCHAIR || kind == Kind.SOFA; }
    /** The way a seat faces (toward its table): {x, z}. */
    public float[] front() { return new float[]{(float) -Math.sin(yaw()), (float) -Math.cos(yaw())}; }''')

G = 'src/main/java/rpg/render/Gfx.java'
sub(G, '''            m.identity().translate(fu.x, fu.floor, fu.z).rotateY(fu.yaw());''', '''            // (0.4.7) a chair drawn out from its table as far as it stands
            float pulled = fu.pull * Furniture.CHAIR_PULL;
            m.identity().translate(fu.x + (float) Math.sin(fu.yaw()) * pulled, fu.floor, fu.z + (float) Math.cos(fu.yaw()) * pulled).rotateY(fu.yaw());''')

S = 'src/main/java/rpg/ai/Seating.java'
sub(S, '''    /** Up from the seat and back out to where they came in from (nothing when they are not sitting). */
    public void stand(Npc n) {
        if (!n.seated) return;
        n.seated = false;''', '''    /**
     * (0.4.7, gamefixes: people pull out a chair and sit, push it in, and push it back as they rise) to a piece of
     * furniture and down on it: true once seated. A chair is drawn out from its table first, the sitter eases down into
     * it and the two go in together to the table; a bench, a stool, an armchair or a settee is simply sat on (a bench's
     * place along it by who sits). Held the same way as any seat: whatever wants it must ask every moment.
     */
    public boolean sitOn(Npc n, rpg.objects.Furniture f) {
        if (f == null || !f.exists() || f.sitter != 0 && f.sitter != n.id) return false;
        float[] fr = f.front();
        float x = f.x, z = f.z;
        if (f.kind == rpg.objects.Furniture.Kind.BENCH || f.kind == rpg.objects.Furniture.Kind.SOFA) {
            // along a long seat: a place by who sits, away from its ends
            boolean alongX = f.width() >= f.depth();
            float len = alongX ? f.width() : f.depth(), slot = (Math.floorMod(n.id, Math.max(1, (int) len)) + 0.5f) - len / 2f;
            if (alongX) x += slot; else z += slot;
        }
        boolean chair = f.kind == rpg.objects.Furniture.Kind.CHAIR;
        // the sitter sits back from the seat's middle as far as the chair stands out from its table
        float pulled = chair ? f.pull * rpg.objects.Furniture.CHAIR_PULL : 0;
        float sx = x - fr[0] * pulled, sz = z - fr[1] * pulled;
        if (n.seated && n.seatFurniture == f.id) {
            // in: once down, the chair goes back in toward the table with them in it
            if (chair && g.time - n.seatSince > PULL_TIME + EASE) f.pullTarget = TUCKED;
            n.seatX = sx; n.seatZ = sz; n.seatHeld = g.time; n.seatYaw = f.yaw(); n.wantYaw = f.yaw();
            return true;
        }
        if (n.seated) stand(n);
        float[] in = approach(x, f.floor, z, f.yaw());
        if (in == null) { n.lastOutcome = "no way in to the " + f.kind.title.toLowerCase(); return false; }
        if (!brain.goTo(n, in[0], f.floor, in[1], false, 0.45f)) return false;
        // at its side: the chair drawn out first, then down
        if (chair && f.pull < 0.9f) { f.pullTarget = 1; f.sitter = n.id; brain.ai.desire(n, AiSystem.A_HOLD); n.wantYaw = (float) Math.atan2(-(x - n.x), -(z - n.z)); return false; }
        n.seated = true; n.seatFurniture = f.id; f.sitter = n.id;
        n.seatX = sx; n.seatY = f.floor; n.seatZ = sz; n.standX = n.x; n.standZ = n.z;
        n.seatYaw = f.yaw(); n.wantYaw = f.yaw(); n.seatHeld = n.seatSince = g.time;
        g.ai.desire(n, AiSystem.A_IDLE);
        return true;
    }
    /** How long drawing a chair out takes (s), and how far in it goes with its sitter (a share of the way out). */
    public static final float PULL_TIME = rpg.Tuning.f("seating.pull", 0.45f), TUCKED = rpg.Tuning.f("seating.tucked", 0.3f);
    /** Each frame: every chair's drawing out and pushing in, eased (a chair let go goes back under its table). */
    void chairs(float dt) {
        for (var f : g.objects.furniture) {
            if (f.kind != rpg.objects.Furniture.Kind.CHAIR || f.pull == f.pullTarget) continue;
            float step = dt / PULL_TIME;
            f.pull = f.pull < f.pullTarget ? Math.min(f.pullTarget, f.pull + step) : Math.max(f.pullTarget, f.pull - step);
            if (f.sitter != 0 && !(g.actor(f.sitter) instanceof Npc s && s.seatFurniture == f.id) && f.pull >= 0.99f) { f.sitter = 0; f.pullTarget = 0; }
        }
    }

    /** Up from the seat and back out to where they came in from (nothing when they are not sitting). */
    public void stand(Npc n) {
        if (!n.seated) return;
        n.seated = false;
        // (0.4.7) a chair goes out again as they rise, and back in under the table once they are clear of it
        if (n.seatFurniture != 0 && g.objects.get(n.seatFurniture) instanceof rpg.objects.Furniture f) {
            if (f.kind == rpg.objects.Furniture.Kind.CHAIR) f.pullTarget = 1; else f.sitter = 0;
        }
        n.seatFurniture = 0;''')

N = 'src/main/java/rpg/ai/Npc.java'
sub(N, '''    public float seatX = Float.NaN, seatY, seatZ, standX, standZ;''', '''    public float seatX = Float.NaN, seatY, seatZ, standX, standZ;
    /** (0.4.7) The piece of furniture they sit on (0: a seat that is not one, or none). */
    public long seatFurniture;''')

B = 'src/main/java/rpg/ai/Brain.java'
sub(B, '''    private void home(Npc n) {''', '''    /**
     * (0.4.7, gamefixes: people pull out a chair and sit at home, sit on a bench) a free seat near a point on the same
     * floor - a chair at a table first, then a bench, a stool, an armchair, a settee - within reach, or null.
     */
    rpg.objects.Furniture seatNear(Npc n, float x, float y, float z, float reach, boolean chairsFirst) {
        rpg.objects.Furniture best = null; float bd = Float.MAX_VALUE;
        for (var f : g.objects.furniture) {
            if (!f.seat() || !f.exists() || f.sitter != 0 && f.sitter != n.id || Math.abs(f.floor - y) > 1.2f) continue;
            float d = (float) Math.hypot(f.x - x, f.z - z);
            if (d > reach) continue;
            if (chairsFirst && f.kind == rpg.objects.Furniture.Kind.CHAIR) d -= 3;
            if (d < bd && g.vox.sight(x, y + 1, z, f.x, f.floor + 0.8f, f.z)) { bd = d; best = f; }
        }
        return best;
    }

    private void home(Npc n) {
        // (0.4.7) a meal or a rest at home is taken sitting: a chair drawn out at the table, else whatever seat is to hand
        Schedule.Activity sitPlan = Schedule.now(n, g.state.weekday(), g.state.hourOfDay());
        if ((sitPlan == Schedule.Activity.MEAL || sitPlan == Schedule.Activity.REST) && (n.id + (long) (g.state.minutes / 20)) % 3 != 0) {
            rpg.objects.Furniture seat = n.seatFurniture != 0 && g.objects.get(n.seatFurniture) instanceof rpg.objects.Furniture held ? held : seatNear(n, n.homeX, n.y, n.homeZ, HOME_SEAT_REACH, true);
            if (seat != null && seating.sitOn(n, seat)) { n.lastOutcome = sitPlan == Schedule.Activity.MEAL ? "a meal at the table" : "sitting a while at home"; return; }
        }''')
sub(B, '''    private void socialize(Npc n) {''', '''    /** (0.4.7) How far from home a seat is looked for (m), and from a talk a bench (m). */
    static final float HOME_SEAT_REACH = rpg.Tuning.f("ai.homeSeatReach", 9f), BENCH_REACH = rpg.Tuning.f("ai.benchReach", 7f);

    private void socialize(Npc n) {''')
print("ok")
