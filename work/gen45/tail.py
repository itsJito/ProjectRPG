import os, json
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main')
p='java/rpg/ai/Npc.java'
s=open(p,encoding='utf-8').read()
old='    public double watchUntil = -999;'
new='''    public double watchUntil = -999;
    /**
     * (0.4.5, gamefixes: NPCs followed the player far too long) the tail budget: when this one began keeping after the
     * player in a goal that is not a fight (watching, looking, a lead, a helping hand) and from where; and until when,
     * having let it go, they will not take it up again (rpg.ai.Brain).
     */
    public double tailSince = -1, tailRestUntil = -999;
    public float tailX, tailZ;'''
assert old in s; s=s.replace(old,new,1); open(p,'w',encoding='utf-8').write(s)

p='java/rpg/ai/Brain.java'
s=open(p,encoding='utf-8').read()
old='''        s.sort(Comparator.comparing(Score::value).reversed());
        // a little inertia: keep the current goal unless another beats it clearly'''
new='''        tailBudget(n, s);
        s.sort(Comparator.comparing(Score::value).reversed());
        // a little inertia: keep the current goal unless another beats it clearly'''
assert old in s; s=s.replace(old,new,1)
old='''    private String workText(Npc n) {'''
new='''    /** (0.4.5) How long (s) and how far (m) anyone keeps after the player short of a fight, and how long they then leave it. */
    static final float TAIL_MAX = rpg.Tuning.f("ai.tailMax", 25f), TAIL_LEASH = rpg.Tuning.f("ai.tailLeash", 28f), TAIL_REST = rpg.Tuning.f("ai.tailRest", 150f);

    /** Whether a goal, as this one holds it, keeps them after the player without a fight (watching, a look, a lead, aid). */
    private boolean afterPlayer(Npc n, Goal goal) {
        long p = g.player.id;
        return switch (goal) {
            case WATCH -> n.watchTarget == p;
            case LOOK -> { var e = ai.stealth.unresolved(n); yield e != null && e.getKey() == p; }
            case INVESTIGATE -> n.investigation != null && n.investigation.subject == p || n.investigation == null && ai.stealth.unresolved(n) != null && ai.stealth.unresolved(n).getKey() == p;
            case AID -> n.aidTarget == p;
            default -> false;
        };
    }

    /**
     * (0.4.5, gamefixes: NPCs sometimes followed the player for way too long) the tail budget: keeping after the player
     * short of a fight - watching them, a look or a lead that is them, a helping hand - lasts at most TAIL_MAX seconds
     * and TAIL_LEASH metres from where it began; then it is let go (the watch eased, the lead settled as nothing more than
     * curious) and none of it is taken up again for TAIL_REST seconds. A fight, an arrest, a hunt for the wanted, a
     * report and a companion's following are not tails and are never cut short by it.
     */
    private void tailBudget(Npc n, List<Score> s) {
        boolean tailing = afterPlayer(n, n.goal);
        if (!tailing) n.tailSince = -1;
        else {
            if (n.tailSince < 0) { n.tailSince = g.time; n.tailX = n.x; n.tailZ = n.z; }
            if (g.time - n.tailSince > TAIL_MAX || Math.hypot(n.x - n.tailX, n.z - n.tailZ) > TAIL_LEASH) {
                n.tailRestUntil = g.time + TAIL_REST;
                n.tailSince = -1;
                if (n.watchTarget == g.player.id) n.watchUntil = Math.min(n.watchUntil, g.time);
                if (n.investigation != null && n.investigation.subject == g.player.id) n.investigation = null;
                Stealth.Aware w = n.aware.get(g.player.id);
                if (w != null) { w.settledAt = g.time; w.level = Math.min(w.level, Stealth.CURIOUS); }
                if (n.aidTarget == g.player.id) n.aidTarget = 0;
                g.state.commit("NpcTailEnded", n.id, g.player.id, DevMap.jurisdiction(n.x, n.z), 0, n.goal + " let go");
            }
        }
        if (g.time < n.tailRestUntil) {
            s.removeIf(sc -> afterPlayer(n, sc.goal));
            if (s.isEmpty()) s.add(new Score(Goal.WANDER, 0.15f, "nothing pressing"));
        }
    }

    private String workText(Npc n) {'''
assert old in s; s=s.replace(old,new,1)
open(p,'w',encoding='utf-8').write(s)
print('ok')
