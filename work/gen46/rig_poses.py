P = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\render\Rig.java'
s = open(P, encoding='utf-8').read()

def rep(a, b):
    global s
    assert s.count(a) == 1, (a[:70], s.count(a))
    s = s.replace(a, b)

rep('''        float headYaw, bowRaise, guardRaise, crouchBlend, seat;''',
    '''        float headYaw, bowRaise, guardRaise, crouchBlend, seat;
        /** (0.4.6, gamefixes: every NPC interaction animated) how far into a kneeling prayer, a talk's gestures and a stance at rest the figure is, and which stance (0 easy, 1 arms folded, 2 hands behind, 3 hands on hips). */
        float pray, talk, rest; int restStyle;'''),

rep('''        if (a.working != null && a.working.action.equals("fill") && !firstPerson) {''',
    '''        // (0.4.6, gamefixes: every NPC interaction animated) at prayer: down on both knees before the altar, the back a
        // little bowed (the hands are put together in front of the chest, below); talking: a hand that moves with the
        // words and a sway of the shoulders; at rest, a stance of their own - arms folded, hands behind the back or on the
        // hips, the weight shifted from foot to foot - changing now and then
        rpg.ai.Npc who = a instanceof rpg.ai.Npc nn ? nn : null;
        float prayWant = who != null && who.goal == rpg.ai.Brain.Goal.PRAY && who.lastOutcome != null && who.lastOutcome.startsWith("praying") ? 1 : 0;
        st.pray += (prayWant - st.pray) * (1 - (float) Math.exp(-3.5f * dt));
        boolean idleHere = Math.hypot(vx, vz) < 0.15f && grounded && a.working == null && c.phase == Phase.IDLE && a.carryBody == 0;
        float talkWant = who != null && idleHere && who.speech != null && who.speechUntil > time ? 1 : 0;
        st.talk += (talkWant - st.talk) * (1 - (float) Math.exp(-5f * dt));
        float restWant = who != null && idleHere && st.seat < 0.05f && prayWant == 0 && who.goal != rpg.ai.Brain.Goal.FIGHT && who.goal != rpg.ai.Brain.Goal.SLEEP ? 1 : 0;
        int style = who == null ? 0 : (int) Math.floorMod(who.id * 7 + (long) (time / 23.0), 4);
        if (style != st.restStyle) { if (st.rest < 0.05f) st.restStyle = style; else restWant = 0; }
        st.rest += (restWant - st.rest) * (1 - (float) Math.exp(-2.2f * dt));
        if (!firstPerson) {
            if (st.pray > 0.001f) { crouch += 7.2f * st.pray; extraLean += 0.18f * st.pray; }
            if (st.talk > 0.001f) { extraTwist += st.talk * 0.06f * (float) Math.sin(time * 2.3 + (who == null ? 0 : who.id)); extraLean += st.talk * 0.03f * (float) Math.sin(time * 3.1); }
            if (st.rest > 0.001f) roll += st.rest * 0.035f * (float) Math.sin(time * 0.45 + (who == null ? 0 : who.id * 1.7));
        }
        if (a.working != null && a.working.action.equals("fill") && !firstPerson) {'''),

rep('''            // seated at a table: the forearms rest on it, a little apart
            if (holds == null && st.seat > 0.001f) rest = lerp(rest, torsoPoint(shoulder, -side * 0.9f, -6.2f, -5.8f), st.seat);''',
    '''            // seated at a table: the forearms rest on it, a little apart
            if (holds == null && st.seat > 0.001f) rest = lerp(rest, torsoPoint(shoulder, -side * 0.9f, -6.2f, -5.8f), st.seat);
            // (0.4.6) the hands of a prayer, a talk and a stance at rest (see the body's above)
            if (holds == null && !firstPerson) {
                if (st.pray > 0.001f) rest = lerp(rest, torsoPoint(shoulder, -side * 1.35f, -4.6f, -3.9f), st.pray);
                if (st.talk > 0.001f && k == 0) {
                    float tw = (float) (time * 2.7 + a.id);
                    rest = lerp(rest, torsoPoint(shoulder, side * 0.5f, -6.0f + 1.1f * (float) Math.sin(tw), -4.6f - 0.9f * (float) Math.sin(tw * 0.7f)), st.talk * 0.85f);
                }
                if (st.rest > 0.001f && st.restStyle != 0) {
                    float[] pose = switch (st.restStyle) {
                        case 1 -> torsoPoint(shoulder, -side * 1.7f, -6.4f, -3.3f);   // arms folded across the chest
                        case 2 -> torsoPoint(shoulder, -side * 0.7f, -8.2f, 2.2f);    // hands clasped behind the back
                        default -> torsoPoint(shoulder, side * 1.0f, -7.6f, -0.4f);   // hands on the hips
                    };
                    rest = lerp(rest, pose, st.rest);
                }
            }'''),
open(P, 'w', encoding='utf-8').write(s)
print('ok')
