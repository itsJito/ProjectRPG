import os
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg')
def sub(rel, old, new):
    p = os.path.join(SRC, rel)
    s = open(p, encoding='utf-8').read()
    if old not in s: raise SystemExit('MISSING in ' + rel + ': ' + old[:90])
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))

# ---- the statuses
sub('actor/Actor.java', '        public float frozenT;', '''        public float frozenT;
        /**
         * (0.4.8, gamefixes: scorpions' own attacks) held fast in something's grip (a scorpion's pincer): no step taken
         * until it lets go; poisoned (a sting): so much health lost a second while the venom lasts, and who gave it.
         */
        public float heldT, poisonT, poisonDps, poisonAcc;
        public long poisonBy;''')
sub('combat/CombatSystem.java', '''        c.frozenT = Math.max(0, c.frozenT - dt);
        c.stabilityBonus''', '''        c.frozenT = Math.max(0, c.frozenT - dt);
        c.heldT = Math.max(0, c.heldT - dt);
        // (0.4.8) the venom works while it lasts, a little at a time
        if (c.poisonT > 0) {
            c.poisonT = Math.max(0, c.poisonT - dt);
            c.poisonAcc += c.poisonDps * dt;
            if (c.poisonAcc >= 1) { int n = (int) c.poisonAcc; c.poisonAcc -= n; environmentalDamage(a, n, "poison"); }
            if (c.poisonT == 0) { c.poisonDps = 0; c.poisonAcc = 0; c.poisonBy = 0; }
        }
        c.stabilityBonus''')
sub('actor/PlayerController.java', '        if (c.frozenT > 0) max = 0;', '        if (c.frozenT > 0 || c.heldT > 0) max = 0;')
sub('ai/AiSystem.java', 'n.cs.frozenT > 0 ? 0.001f :', 'n.cs.frozenT > 0 || n.cs.heldT > 0 ? 0.001f :')
sub('animal/Animals.java', 'a.cs.frozenT>0?0.001f:', 'a.cs.frozenT>0||a.cs.heldT>0?0.001f:')
sub('monster/Monsters.java', '(m.cs.frozenT > 0 ? 0 : m.cs.slowT > 0 ? 0.6f : 1)', '(m.cs.frozenT > 0 || m.cs.heldT > 0 ? 0 : m.cs.slowT > 0 ? 0.6f : 1)')
# a shrine or rest clears them
sub('world/Shrines.java', 'a.cs.cancel(); a.cs.slowT = 0; a.cs.frozenT = 0;', 'a.cs.cancel(); a.cs.slowT = 0; a.cs.frozenT = 0; a.cs.heldT = 0; a.cs.poisonT = 0; a.cs.poisonDps = 0;')

# ---- the scorpions' attacks: which, how long the wind-up, what lands
sub('monster/MonsterMind.java', '''                case GIANT_SPIDER -> "bite";
                default -> "bite";''', '''                case GIANT_SPIDER -> "bite";
                // (0.4.8) a scorpion seizes with a pincer, or arches its tail and stings (more often the larger, the closer)
                case SMALL_SCORPION, LARGE_SCORPION -> sys.rnd(m) < (m.kind == MonsterKind.LARGE_SCORPION ? 0.45 : 0.35) ? "sting" : "pincer";
                default -> "bite";''')
sub('monster/MonsterMind.java', '''            m.windupUntil = g.time + switch (m.kind) { case DRAGON -> 0.9; case GIANT_SPIDER -> 0.45; default -> 0.25; };''',
    '''            m.windupUntil = g.time + switch (m.kind) { case DRAGON -> 0.9; case GIANT_SPIDER -> 0.45; case SMALL_SCORPION, LARGE_SCORPION -> what.equals("sting") ? 0.7 : 0.4; default -> 0.25; };''')
sub('monster/MonsterMind.java', '''    static boolean predator(Monster m) { return m.kind == MonsterKind.WEREWOLF || m.kind == MonsterKind.GIANT_SPIDER ||''',
    '''    static boolean predator(Monster m) { return m.kind == MonsterKind.WEREWOLF || m.kind == MonsterKind.GIANT_SPIDER || m.kind.scorpion() || m.kind.troll() || m.kind.demon() ||''')
sub('monster/MonsterMind.java', '''        float keep = m.kind == MonsterKind.GIANT_SPIDER ? 0 : 9;''', '''        float keep = m.kind == MonsterKind.GIANT_SPIDER || m.kind.scorpion() ? 0 : 9;''')
sub('monster/Monster.java', '    public int variant;', '''    public int variant;
    /** (0.4.8) The creature strike it last made ("pincer", "sting"...): the renderer finishes the stroke it began. */
    public String lastStrike;''')
sub('monster/Monsters.java', '''        m.strikeAt = g.time;
        float reach = m.kind.reach * Math.max(0.6f, m.kind.scale()) + (what.equals("tail") ? 1.6f : 0);''', '''        m.strikeAt = g.time;
        m.lastStrike = what;
        float reach = m.kind.reach * Math.max(0.6f, m.kind.scale()) + (what.equals("tail") ? 1.6f : what.equals("sting") ? m.kind.reach * 0.6f : 0);''')
sub('monster/Monsters.java', '''        if (d > reach + 0.3f || facing > (what.equals("tail") ? Math.PI : 1.0f)) { sound(m, "swish", 0.4f); return; }
        float dmg = m.kind.damage * (what.equals("tail") ? 0.8f : what.equals("claw") ? 0.9f : 1);''', '''        if (d > reach + 0.3f || facing > (what.equals("tail") ? Math.PI : 1.0f)) { sound(m, "swish", 0.4f); return; }
        float dmg = m.kind.damage * (what.equals("tail") ? 0.8f : what.equals("claw") ? 0.9f : what.equals("sting") ? 0.6f : 1);''')
sub('monster/Monsters.java', '''        if (m.kind == MonsterKind.GIANT_SPIDER && t.alive) t.cs.slowT = Math.max(t.cs.slowT, 3);   // the venom''', '''        if (m.kind == MonsterKind.GIANT_SPIDER && t.alive) t.cs.slowT = Math.max(t.cs.slowT, 3);   // the venom
        // (0.4.8) a scorpion's pincer closes on its prey and holds it; its sting leaves its venom working in the blood
        if (m.kind.scorpion() && t.alive) {
            if (what.equals("pincer")) { t.cs.heldT = Math.max(t.cs.heldT, rpg.Tuning.f("monsters.pincerHold", 1.3f) * (m.kind == MonsterKind.LARGE_SCORPION ? 1.4f : 1)); sound(m, "hit.blunt", 0.5f); }
            if (what.equals("sting")) {
                float dps = Math.max(0.6f, m.kind.damage * rpg.Tuning.f("monsters.venomPerDamage", 0.35f) * (0.6f + m.level / 60f));
                t.cs.poisonDps = Math.max(t.cs.poisonDps, dps);
                t.cs.poisonT = Math.max(t.cs.poisonT, rpg.Tuning.f("monsters.venomSeconds", 8));
                t.cs.poisonBy = m.id;
                if (t == g.player) g.chat.warn("You have been poisoned.");
            }
        }''')
# ---- the vitals show it
sub('ui/HudFragments.java', '''                ui.rect(x + 27, yy + 17, 198 * Math.max(0, Math.min(1, max[i] <= 0 ? 0 : values[i] / max[i])), 3, Style.fade(colors[i], a));
            }
        }''', '''                ui.rect(x + 27, yy + 17, 198 * Math.max(0, Math.min(1, max[i] <= 0 ? 0 : values[i] / max[i])), 3, Style.fade(i == 0 && p.cs.poisonT > 0 ? 0xFF6AB83A : colors[i], a));
            }
            // (0.4.8) poisoned or held: a word under the bars while it lasts
            float sy = y + 4 * 28;
            if (p.cs.poisonT > 0) { ui.rect(x, sy, 118, 18, 0xC81D2023); ui.text(String.format("Poisoned %.0fs", p.cs.poisonT), x + 8, sy + 2, 11, 0xFF7ACC4A); sy += 20; }
            if (p.cs.heldT > 0) { ui.rect(x, sy, 118, 18, 0xC81D2023); ui.text("Held fast", x + 8, sy + 2, 11, Style.WARN); }
        }''')
sub('ui/HudFragments.java', '''            boolean combat = hud.g.time - p.cs.lastHurt < 6 || p.cs.busy() || p.cs.blocking;''', '''            boolean combat = hud.g.time - p.cs.lastHurt < 6 || p.cs.busy() || p.cs.blocking || p.cs.poisonT > 0 || p.cs.heldT > 0;''')
sub('ui/HudFragments.java', '''        @Override public float height() { return 116; }''', '''        @Override public float height() { return 160; }''')
print('status')
