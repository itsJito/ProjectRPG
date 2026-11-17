import os
HERE = os.path.dirname(os.path.abspath(__file__))
p = os.path.join(HERE, '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg', 'render', 'Gfx.java')
s = open(p, encoding='utf-8').read()
def sub(old, new):
    global s
    if old not in s: raise SystemExit('MISSING: ' + old[:90])
    s = s.replace(old, new, 1)

# ---- the stride and carriage of the new kinds
sub('''case WEREWOLF -> run ? 1.9f : 1.3f; case TROLL -> 1.15f; default -> 1.3f; });''',
    '''case WEREWOLF -> run ? 1.9f : 1.3f; case TROLL, ICE_TROLL, RIVER_TROLL -> 1.15f; case LESSER_DEMON -> 1.05f; default -> 1.3f; });''')
sub('''            case TROLL -> { lean = 0.42f + amp * 0.1f; kneeBase = 0.35f; thighBase = 0.2f; armFwd = 0.5f; spread = 0.16f; elbowBase = 0.25f; headX = 0.05f; roll = 0.09f * (float) Math.sin(ph) * Math.max(0.3f, amp); swingArms = 0.45f; }''',
    '''            case TROLL, ICE_TROLL -> { lean = 0.42f + amp * 0.1f; kneeBase = 0.35f; thighBase = 0.2f; armFwd = 0.5f; spread = 0.16f; elbowBase = 0.25f; headX = 0.05f; roll = 0.09f * (float) Math.sin(ph) * Math.max(0.3f, amp); swingArms = 0.45f; }
            // (0.4.8) the river troll: lower and longer, its long arms trailing near the ground, a sidelong slink
            case RIVER_TROLL -> { lean = 0.52f + amp * 0.12f; kneeBase = 0.45f; thighBase = 0.28f; armFwd = 0.62f; spread = 0.22f; elbowBase = 0.18f; headX = -0.08f; roll = 0.12f * (float) Math.sin(ph) * Math.max(0.3f, amp); swingArms = 0.55f; }
            // the lesser demon: hunched and coiled, knees bent, claws out before it, head thrust up in a snarl
            case LESSER_DEMON -> { lean = 0.3f + amp * 0.14f; kneeBase = 0.36f; thighBase = 0.18f; armFwd = 0.38f; spread = 0.32f; elbowBase = 0.62f; headX = -0.16f; roll = 0.06f * (float) Math.sin(ph) * Math.max(0.4f, amp); swingArms = 0.5f; }
            // the greater demon: upright and dominant, the chest out, arms held wide of its bulk, a slow heavy tread
            case GREATER_DEMON -> { lean = 0.02f + amp * 0.08f; kneeBase = 0.1f; thighBase = 0.04f; armFwd = 0.12f; spread = 0.3f; elbowBase = 0.32f; headX = -0.06f; roll = 0.045f * (float) Math.sin(ph) * amp; swingArms = 0.4f; }''')
sub('''        boolean airborne = k == rpg.monster.MonsterKind.IMP && mo.altitude > 0.35f;''',
    '''        boolean airborne = k == rpg.monster.MonsterKind.IMP && mo.altitude > 0.35f;
        // (0.4.8, gamefixes: the greater demon's wings flap and it hovers from time to time - mostly grounded, never in full
        // flight) lifted on its beating wings over the ground for a few seconds: risen, held, set down
        float hover = 0;
        if (mo.hoverUntil > clock && clock > mo.hoverFrom) {
            float span = (float) (mo.hoverUntil - mo.hoverFrom), into = (float) (clock - mo.hoverFrom);
            hover = smooth(Math.min(1, into / 0.6f)) * smooth(Math.min(1, (span - into) / 0.7f));
        }''')
sub('''        float bob = ghost || airborne ? 0 : Math.abs((float) Math.sin(gp)) * amp * (run ? 1.2f : 0.55f) * (k == rpg.monster.MonsterKind.TROLL ? 1.5f : 1);
        mf.identity().translate(mo.x, mo.y, mo.z).rotateY(mo.yaw).scale(vs, vs, vs);''',
    '''        float bob = ghost || airborne ? 0 : Math.abs((float) Math.sin(gp)) * amp * (run ? 1.2f : 0.55f) * (k.troll() ? 1.5f : 1);
        // the hover: up to 'monsters.hoverLift' metres, bobbing on each down-stroke of the wings
        if (hover > 0) bob += hover * (rpg.Tuning.f("monsters.hoverLift", 0.7f) + 0.12f * (float) Math.sin(t * 9)) / vs;
        mf.identity().translate(mo.x, mo.y, mo.z).rotateY(mo.yaw).scale(vs, vs, vs);''')
sub('''        float breath = 0.03f * (float) Math.sin(t * (k == rpg.monster.MonsterKind.TROLL ? 1.2 : 2.1));''',
    '''        float breath = (k == rpg.monster.MonsterKind.GREATER_DEMON ? 0.045f : 0.03f) * (float) Math.sin(t * (k.troll() || k == rpg.monster.MonsterKind.GREATER_DEMON ? 1.2 : 2.1));''')
# legs dangle a little in the hover
sub('''            if (airborne) { thighA = thighBase + 0.15f * (float) Math.sin(t * 2 + side); knee = kneeBase + 0.2f * (float) Math.sin(t * 2.3 + side); foot = 0.5f; }''',
    '''            if (airborne) { thighA = thighBase + 0.15f * (float) Math.sin(t * 2 + side); knee = kneeBase + 0.2f * (float) Math.sin(t * 2.3 + side); foot = 0.5f; }
            if (hover > 0) { thighA += hover * (0.25f + 0.08f * (float) Math.sin(t * 3 + side)); knee += hover * 0.5f; foot += hover * 0.45f; }''')
# the wings: a greater demon's fold back along its body at rest, spread and beat in the hover
sub('''        if (f.wing != null) {
            st.flap += ((airborne ? 1 : 0.15f) - st.flap) * Math.min(1, dt * 5);
            float beat = st.flap * (float) Math.sin(t * 26) * 0.85f;
            for (int side = 0; side < 2; side++) {
                ma.set(mp).translate((side == 0 ? 1 : -1) * f.wingAt[0], f.wingAt[1], f.wingAt[2]);
                if (side == 1) ma.rotateY((float) Math.PI);
                ma.rotateY(side == 0 ? -(0.55f + beat) : 0.55f + beat).rotateZ(-0.15f);
                mpart(ma, f.wing);
            }
        }''', '''        if (f.wing != null) {
            boolean big = k == rpg.monster.MonsterKind.GREATER_DEMON;
            st.flap += ((airborne || hover > 0.05f ? 1 : big && mo.state == rpg.monster.Monster.State.ENGAGE ? 0.25f : 0.15f) - st.flap) * Math.min(1, dt * 5);
            // a greater demon's great wings beat slowly and hard (down fast, up slow); an imp's buzz
            float wt = (float) (t * (big ? 5.2 : 26) % (Math.PI * 2));
            float stroke = big ? (wt < 2.2f ? (float) Math.sin(wt / 2.2f * Math.PI / 2) : (float) Math.cos((wt - 2.2f) / (Math.PI * 2 - 2.2f) * Math.PI / 2)) * 2 - 1 : (float) Math.sin(wt);
            float beat = st.flap * stroke * (big ? 0.7f : 0.85f);
            // folded: drawn in along the back; spread: out to the sides
            float fold = big ? 1.2f - 0.85f * Math.min(1, st.flap * 1.4f) : 0.55f;
            for (int side = 0; side < 2; side++) {
                ma.set(mp).translate((side == 0 ? 1 : -1) * f.wingAt[0], f.wingAt[1], f.wingAt[2]);
                if (side == 1) ma.rotateY((float) Math.PI);
                ma.rotateY(side == 0 ? -(fold + beat * 0.35f) : fold + beat * 0.35f).rotateZ(big ? -0.15f + beat * 0.5f : -0.15f);
                mpart(ma, f.wing);
            }
        }''')
open(p, 'w', encoding='utf-8', newline='').write(s)
print('gfx carriage/hover/wings')
