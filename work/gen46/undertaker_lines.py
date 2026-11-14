R = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg'

def patch(path, pairs):
    p = R + path
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) >= 1, (path, a[:70])
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)

patch(r'\ai\Brain.java', [
    ('''g.ai.say(n, n.role == Npc.Role.UNDERTAKER ? "Poor soul. I'll see your belongings safely home." : "Poor soul. I'll see this to Morrow.");''',
     '''g.ai.say(n, n.role == Npc.Role.UNDERTAKER ? "Poor soul. I'll see your belongings safely home." : "Poor soul. I'll take this to " + undertakerName(n) + ".");'''),
    ('''g.ai.say(n, n.role == Npc.Role.UNDERTAKER ? "Rest now. Your things stay with you." : "There. Morrow will see to the rest.");''',
     '''g.ai.say(n, n.role == Npc.Role.UNDERTAKER ? "Rest now. Your things stay with you." : "There. " + capital(undertakerName(n)) + " will see to the rest.");'''),
    ('''    /** The guard's errand: to the grave, a moment gathering it up, then the bundle carried to the undertaker's hut. */''',
     '''    /**
     * (0.4.6, gamefixes: lawmen said they would take a body "to Morrow", the devmap's undertaker, in every kingdom) who the
     * dead go to in a person's own kingdom: its undertaker by name ("the undertaker, Astrid"), or simply the undertaker.
     */
    String undertakerName(Npc n) {
        for (Npc o : g.npcs) if (o.alive && o.role == Npc.Role.UNDERTAKER && o.homeJurisdiction.equals(n.homeJurisdiction)) return "the undertaker, " + o.name;
        return "the undertaker";
    }
    private static String capital(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** The guard's errand: to the grave, a moment gathering it up, then the bundle carried to the undertaker's hut. */'''),
])
patch(r'\ai\Dialogue.java', [
    ('''"The watch carries the fallen to these stones. Morrow keeps the belongings safe. Even a criminal deserves a name and a resting place."''',
     '''"The watch carries the fallen to these stones. The undertaker keeps the belongings safe. Even a criminal deserves a name and a resting place."'''),
    ('''case "BodyBuried" -> "Morrow laid " + pl.replace("'s body", "") + " to rest.";''',
     '''case "BodyBuried" -> (e.actor() == 0 ? "The undertaker" : g.nameOf(e.actor())) + " laid " + pl.replace("'s body", "") + " to rest.";'''),
])
patch(r'\PlayerDeath.java', [
    ('''"Morrow holds your belongings at the Undertaker’s chest.''', '''"The Undertaker holds your belongings in his chest.'''),
])
print('ok')
