def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
R = 'rpggame/src/main/java/rpg/'

# ---------------------------------------------------------------- Bank: branches
patch(R + 'econ/Bank.java', [
("""    private final Rpg g;
    public Bank(Rpg g) { this.g = g; }

    public Npc teller() { return g.actor(Ids.TELLER) instanceof Npc n ? n : null; }

    /** Where a customer stands to be served: before the counter, in front of the teller's place. */
    public float[] customerSpot() {
        // (0.4.1) on ShadowVeil the bank is where its world puts it (the architecture's services): Loxford Castle's keep
        if (rpg.world.WorldMap.shadowveil()) { float[] p = rpg.world.shadowveil.ShadowVeil.service("bank_customer"); if (p != null) return p; }
        return new float[]{DevMap.BANK_TELLER[0], DevMap.BANK_COUNTER_Z - 0.9f};
    }
    /** Where the teller works. */
    public float[] tellerSpot() {
        if (rpg.world.WorldMap.shadowveil()) { float[] p = rpg.world.shadowveil.ShadowVeil.service("bank_teller"); if (p != null) return p; }
        return DevMap.BANK_TELLER;
    }

    /** Whether the bank is serving: the teller alive, at the counter, not asleep or running. */
    public boolean open() {
        Npc t = teller();
        return t != null && t.alive && t.loaded && t.working()
                && Math.hypot(t.x - tellerSpot()[0], t.z - tellerSpot()[1]) < 1.6;
    }

    /** Why someone cannot be served now (null: they can). */
    public String refusal(Actor a) {
        if (!open()) return "There's nobody at the bank counter.";
        float[] at = customerSpot();
        if (Math.hypot(a.x - at[0], a.z - at[1]) > 2.6f) return "You need to be at the bank counter.";
        return null;
    }""",
"""    private final Rpg g;
    public Bank(Rpg g) { this.g = g; }

    /**
     * (0.4.4, gamefixes: every kingdom its own bankhouse) A branch of the bank: its banker, where they stand behind the
     * counter and where a customer stands before it, its kingdom. The accounts are one ledger: a vault opened at any
     * branch is reached at every other.
     */
    public record Branch(String name, String jurisdiction, long teller, float[] tellerSpot, float[] customerSpot) {}
    private List<Branch> branches;

    /** Every branch (the devmap's one; on ShadowVeil each banker's counter, from the census's posts). */
    public List<Branch> branches() {
        if (branches != null && !branches.isEmpty()) return branches;
        List<Branch> l = new ArrayList<>();
        if (rpg.world.WorldMap.shadowveil()) {
            for (var a : rpg.world.shadowveil.Census.all().values()) {
                float[] post = rpg.world.shadowveil.Census.post(a.id());
                if (post == null || !"teller".equals(rpg.world.shadowveil.Census.postKind(a.id()))) continue;
                float[] cust = rpg.world.shadowveil.Census.customerSpot(a.id());
                String where = a.workId() == null ? "the bank" : rpg.world.shadowveil.Census.placeName(a.workId());
                l.add(new Branch(where, a.kingdom(), a.id(), new float[]{post[0], post[1]}, cust != null ? cust : new float[]{post[0], post[1] + 2}));
            }
        }
        if (l.isEmpty()) l.add(new Branch("the bank", DevMap.BETA, Ids.TELLER, DevMap.BANK_TELLER, new float[]{DevMap.BANK_TELLER[0], DevMap.BANK_COUNTER_Z - 0.9f}));
        branches = l;
        return l;
    }
    /** Forgets the branches (the census placed its people again). */
    public void reset() { branches = null; }
    /** The branch nearest a point. */
    public Branch branchNear(float x, float z) {
        Branch best = null; double bd = Double.MAX_VALUE;
        for (Branch b : branches()) { double d = Math.hypot(b.customerSpot[0] - x, b.customerSpot[1] - z); if (d < bd) { bd = d; best = b; } }
        return best;
    }
    public Branch branchOf(Actor a) { return branchNear(a.x, a.z); }
    /** Whether someone is a banker at one of the branches. */
    public boolean isTeller(Npc n) { for (Branch b : branches()) if (b.teller == n.id) return true; return false; }
    private Branch branchOfTeller(long id) { for (Branch b : branches()) if (b.teller == id) return b; return null; }

    /** The teller at the branch nearest the player. */
    public Npc teller() { return teller(branchNear(g.player.x, g.player.z)); }
    public Npc teller(Branch b) { return b != null && g.actor(b.teller) instanceof Npc n ? n : null; }

    /** Where a customer stands to be served at the branch nearest the player: before the counter, in front of the teller's place. */
    public float[] customerSpot() { return branchNear(g.player.x, g.player.z).customerSpot; }
    /** Where a customer stands at the branch nearest someone (an NPC's errand goes to its own town's). */
    public float[] customerSpot(Actor a) { return branchOf(a).customerSpot; }
    /** Where the teller works (the branch nearest the player). */
    public float[] tellerSpot() { return branchNear(g.player.x, g.player.z).tellerSpot; }

    /** Whether the branch nearest the player is serving. */
    public boolean open() { return open(branchNear(g.player.x, g.player.z)); }
    /** Whether the branch nearest someone is serving. */
    public boolean open(Actor a) { return open(branchOf(a)); }
    /** Whether a branch is serving: its teller alive, at the counter, not asleep or running. */
    public boolean open(Branch b) {
        Npc t = teller(b);
        return t != null && t.alive && t.loaded && t.working()
                && Math.hypot(t.x - b.tellerSpot[0], t.z - b.tellerSpot[1]) < 1.6;
    }

    /** Why someone cannot be served now (null: they can). */
    public String refusal(Actor a) {
        Branch b = branchOf(a);
        if (!open(b)) return "There's nobody at the bank counter.";
        float[] at = b.customerSpot;
        if (Math.hypot(a.x - at[0], a.z - at[1]) > 2.6f) return "You need to be at the bank counter.";
        return null;
    }"""),
("""        collectFee(ACCOUNT_PRICE);
        vault(a);""", """        collectFee(a, ACCOUNT_PRICE);
        vault(a);"""),
("""        collectFee(price);
        Container vault = vaults(a.id).get(0);""", """        collectFee(a, price);
        Container vault = vaults(a.id).get(0);"""),
("""    private Container vault(Actor a) {
        float[] at = tellerSpot();""", """    private Container vault(Actor a) {
        float[] at = branchOf(a).tellerSpot;"""),
("""    private void collectFee(int amount) {
        Npc t = teller();""", """    private void collectFee(Actor a, int amount) {
        Npc t = teller(branchOf(a));"""),
("""        if (n.id == Ids.TELLER || !n.alive || n.hostileToPlayer || n.posse) return null;""",
 """        if (isTeller(n) || !n.alive || n.hostileToPlayer || n.posse) return null;"""),
("""    public void choices(Npc teller, Dialog d) {
        if (!open() || !teller.working()) return;""", """    public void choices(Npc teller, Dialog d) {
        Branch b = branchOfTeller(teller.id);
        if (b == null || !open(b) || !teller.working()) return;"""),
])

# ---------------------------------------------------------------- callers
patch(R + 'ai/Brain.java', [
("""!farOnShift(n, plan, g.bank.customerSpot())""", """!farOnShift(n, plan, g.bank.customerSpot(n))"""),
("""        float[] at = g.bank.customerSpot();""", """        float[] at = g.bank.customerSpot(n);"""),
("""        if (!g.bank.open()) { n.bankAgain = g.time + 300;""", """        if (!g.bank.open(n)) { n.bankAgain = g.time + 300;"""),
("""            float[] customer = g.bank.customerSpot();""", """            float[] customer = g.bank.customerSpot(n);"""),
])
patch(R + 'ai/Jobs.java', [
("""        float[] at=g.bank.customerSpot();""", """        float[] at=g.bank.customerSpot(n);"""),
("""        if(!g.bank.open()){g.ai.desire""", """        if(!g.bank.open(n)){g.ai.desire"""),
])
patch(R + 'ai/Dialogue.java', [
("""            case CIVIC -> { if (n.id == Ids.TELLER) g.bank.choices(n, d); }""", """            case CIVIC -> { if (g.bank.isTeller(n)) g.bank.choices(n, d); }"""),
("""            case CIVIC -> g.bank.open() ? pick(n,""", """            case CIVIC -> g.bank.isTeller(n) && g.bank.open(n) ? pick(n,"""),
])
print('ok')
