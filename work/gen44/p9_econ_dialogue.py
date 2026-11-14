def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
R = 'rpggame/src/main/java/rpg/'
patch(R + 'econ/Economy.java', [
("""            if (!(g.actor(s.merchant) instanceof Npc keeper)) continue;
            s.counterX = keeper.postX; s.counterZ = keeper.postZ + 1.4f; s.jurisdiction = keeper.homeJurisdiction;""",
 """            if (!(g.actor(s.merchant) instanceof Npc keeper)) continue;
            s.counterX = keeper.postX; s.counterZ = keeper.postZ + 1.4f; s.jurisdiction = keeper.homeJurisdiction;
            // (0.4.4) a keeper behind a counter indoors: the customers' place before it
            float[] cust = rpg.world.shadowveil.Census.customerSpot(keeper.id);
            if (cust != null) { s.counterX = cust[0]; s.counterZ = cust[1]; }"""),
])
patch(R + 'ai/Dialogue.java', [
("""        if (g.econ.shopA != null) l.add(new Object[]{"the store", g.econ.shopA.counterX, g.econ.shopA.counterZ});
        if (g.econ.shopB != null) l.add(new Object[]{"the store", g.econ.shopB.counterX, g.econ.shopB.counterZ});
        l.add(new Object[]{"the bank", g.bank.tellerSpot()[0], g.bank.tellerSpot()[1]});
        l.add(new Object[]{"an altar", rpg.world.DevMap.ALTAR[0] + 0.5f, rpg.world.DevMap.ALTAR[1] + 0.5f});
        l.add(new Object[]{"an altar", rpg.world.Expanse.ALTAR[0], rpg.world.Expanse.ALTAR[1]});
        l.add(new Object[]{"the well", rpg.world.DevMap.WELL[0] + 0.5f, rpg.world.DevMap.WELL[1] + 0.5f});""",
 """        if (g.econ.shopA != null) l.add(new Object[]{"the store", g.econ.shopA.counterX, g.econ.shopA.counterZ});
        if (g.econ.shopB != null) l.add(new Object[]{"the store", g.econ.shopB.counterX, g.econ.shopB.counterZ});
        // (0.4.4) every branch of the bank, every church's altar, every gaol (ShadowVeil's own)
        for (var b : g.bank.branches()) l.add(new Object[]{"the bank", b.customerSpot()[0], b.customerSpot()[1]});
        if (rpg.world.WorldMap.shadowveil()) {
            for (var a : g.objects.altars()) l.add(new Object[]{"an altar", a.x, a.z});
            for (var cells : rpg.world.shadowveil.Rooms.cells().values()) if (!cells.isEmpty()) l.add(new Object[]{"the gaol", cells.get(0).releaseX(), cells.get(0).releaseZ()});
            return withPeople(l);
        }
        l.add(new Object[]{"an altar", rpg.world.DevMap.ALTAR[0] + 0.5f, rpg.world.DevMap.ALTAR[1] + 0.5f});
        l.add(new Object[]{"an altar", rpg.world.Expanse.ALTAR[0], rpg.world.Expanse.ALTAR[1]});
        l.add(new Object[]{"the well", rpg.world.DevMap.WELL[0] + 0.5f, rpg.world.DevMap.WELL[1] + 0.5f});
        return withPeople(l);
    }
    private java.util.List<Object[]> withPeople(java.util.List<Object[]> l) {"""),
("""            case CIVIC -> g.bank.isTeller(n) && g.bank.open(n) ? pick(n, "Welcome to the Bank of ASHENWRATH. How may I help?", "Deposits, withdrawals, a new vault? Step up to the counter.", "Good day. Your things are safe with us.")
                    : pick(n, "We're closed just now. Come back when I'm at the counter.", "The counter's shut. The vaults aren't going anywhere.", "Banking hours are over. Your coin is sleeping, as I'd like to be.");""",
 """            // (0.4.4) a banker speaks for the bank; the rest of the civic folk (lords, stewards, clerks) for their own business
            case CIVIC -> !g.bank.isTeller(n) ? pick(n, "Good day. Is there business you've come about?", "Speak plainly; the day has enough in it already.", "If it's a petition, it goes through the proper hands.")
                    : g.bank.open(n) ? pick(n, "Welcome to the Bank of ASHENWRATH. How may I help?", "Deposits, withdrawals, a new vault? Step up to the counter.", "Good day. Your things are safe with us.")
                    : pick(n, "We're closed just now. Come back when I'm at the counter.", "The counter's shut. The vaults aren't going anywhere.", "Banking hours are over. Your coin is sleeping, as I'd like to be.");"""),
])
print('ok')
