def sub(p, a, b, cnt=1):
    s = open(p, encoding='utf-8').read()
    assert a in s, (p, a[:90])
    s = s.replace(a, b, cnt)
    open(p, 'w', encoding='utf-8').write(s)

G = 'src/main/java/rpg/render/Gfx.java'
sub(G, '''        if (sp == Species.PEGASUS && a.flying) pitch -= a.flightPitch * 0.8f;''',
       '''        if ((sp == Species.PEGASUS || sp.flies) && a.flying) pitch -= a.flightPitch * 0.8f;''')
sub(G, '''        if (sp == Species.PEGASUS && a.flying) body.rotateZ(-a.flightBank * 0.9f);''',
       '''        if ((sp == Species.PEGASUS || sp.flies) && a.flying) body.rotateZ(-a.flightBank * 0.9f);''')
sub(G, '''            float stepA = (float) Math.sin(ph) * 0.75f * amp;
            float hide = fold > 0.6f ? 0 : 1;''', '''            float stepA = (float) Math.sin(ph) * 0.75f * amp;
            // (0.4.7) a flier aloft tucks its feet up under it
            float hide = fold > 0.6f || a.flying && !dead ? 0 : 1;''')
sub(G, '''            // wings: tucked, beating when it runs or flees
            boolean flapping = running || a.goal == rpg.animal.Animal.Goal.FLEE && speed > 0.5f;
            a.flap += ((flapping ? 1 : 0) - a.flap) * Math.min(1, dt * 6);
            float beat = a.flap * (0.25f + 0.85f * Math.abs((float) Math.sin(time * 21 + a.id)));
            limb(rpg.combat.Gore.Part.ARM_R, f.wing, vs, f.wingAt[0], f.wingAt[1], f.wingAt[2], 0, 0, beat);
            limb(rpg.combat.Gore.Part.ARM_L, f.wing, vs, -f.wingAt[0], f.wingAt[1], f.wingAt[2], 0, 0, -beat);''',
'''            // wings: tucked, beating when it runs or flees
            if (a.flying && !dead) {
                // (0.4.7, gamefixes: birds that fly) aloft the wings are spread: beaten on the climb and the take-off (a
                // crow's steady rowing, a pigeon's quick clatter, a vulture's few slow heavy strokes), held out flat in
                // the glide, a soarer's tips lifted in a shallow V; the beat's phase runs on so the stroke never jumps
                float rate = sp == Species.VULTURE ? 4.2f : sp == Species.PIGEON ? 15f : 10f;
                a.wingPhase += dt * rate * (0.25f + 0.75f * a.flap);
                float stroke = a.flap * (sp == Species.VULTURE ? 0.45f : 0.7f) * (float) Math.sin(a.wingPhase);
                float spread = 1.42f + (sp.soars ? 0.12f : 0) + stroke, sweep = (sp == Species.VULTURE ? 0.05f : 0.15f) * (1 - a.flap);
                limb(rpg.combat.Gore.Part.ARM_R, f.wing, vs, f.wingAt[0], f.wingAt[1], f.wingAt[2], 0, -sweep, spread);
                limb(rpg.combat.Gore.Part.ARM_L, f.wing, vs, -f.wingAt[0], f.wingAt[1], f.wingAt[2], 0, sweep, -spread);
            } else {
                boolean flapping = running || a.goal == rpg.animal.Animal.Goal.FLEE && speed > 0.5f;
                a.flap += ((flapping ? 1 : 0) - a.flap) * Math.min(1, dt * 6);
                float beat = a.flap * (0.25f + 0.85f * Math.abs((float) Math.sin(time * 21 + a.id)));
                limb(rpg.combat.Gore.Part.ARM_R, f.wing, vs, f.wingAt[0], f.wingAt[1], f.wingAt[2], 0, 0, beat);
                limb(rpg.combat.Gore.Part.ARM_L, f.wing, vs, -f.wingAt[0], f.wingAt[1], f.wingAt[2], 0, 0, -beat);
            }''')
print("ok")
