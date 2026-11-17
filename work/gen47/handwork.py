def sub(p, a, b, cnt=1):
    s = open(p, encoding='utf-8').read()
    assert a in s, (p, a[:90])
    s = s.replace(a, b, cnt)
    open(p, 'w', encoding='utf-8').write(s)

R = 'src/main/java/rpg/render/Rig.java'
sub(R, '''    public static boolean repairing(Actor a){return a.working!=null && a.working.action.equals("repair");}''', '''    public static boolean repairing(Actor a){return a.working!=null && a.working.action.equals("repair");}

    /**
     * (0.4.7, gamefixes: every NPC interaction animated) the works done with the hands - milking, shearing, butchering,
     * sowing, harvesting, picking, gathering by hand, burying, lighting a fire, stealing, lifting a purse: how the body
     * takes to each, {crouch, lean, rhythm (rad/s), the hands' stroke (px), the hands' height above the feet (m),
     * alternate hands (1) or together (0)}; null for any other work (a swing, a station, a meal have their own).
     */
    static final java.util.Map<String, float[]> HAND_WORK = java.util.Map.ofEntries(
            java.util.Map.entry("milk", new float[]{6.8f, 0.22f, 7.5f, 1.4f, 0.45f, 1}),
            java.util.Map.entry("shear", new float[]{3.2f, 0.42f, 5.0f, 2.2f, 0.75f, 0}),
            java.util.Map.entry("butcher", new float[]{6.0f, 0.38f, 4.2f, 2.6f, 0.3f, 0}),
            java.util.Map.entry("sow", new float[]{2.4f, 0.48f, 3.6f, 1.6f, 0.15f, 1}),
            java.util.Map.entry("harvest", new float[]{4.5f, 0.5f, 4.0f, 2.0f, 0.25f, 1}),
            java.util.Map.entry("pick", new float[]{-0.4f, -0.12f, 3.0f, 1.8f, 1.85f, 1}),
            java.util.Map.entry("gather", new float[]{4.0f, 0.45f, 4.4f, 1.8f, 0.3f, 1}),
            java.util.Map.entry("bury", new float[]{7.0f, 0.3f, 3.2f, 1.8f, 0.05f, 0}),
            java.util.Map.entry("light", new float[]{7.2f, 0.34f, 9.0f, 0.9f, 0.15f, 0}),
            java.util.Map.entry("steal", new float[]{1.0f, 0.2f, 2.0f, 0.8f, 0.95f, 0}),
            java.util.Map.entry("pickpocket", new float[]{1.2f, 0.12f, 2.5f, 0.6f, 0.95f, 0}));
    static float[] handWork(Actor a) {
        var w = a.working;
        if (w == null) return null;
        String act = w.action.startsWith("sow_") ? "sow" : w.action;
        float[] spec = HAND_WORK.get(act);
        if (spec == null || act.equals("gather") && w.target instanceof rpg.objects.Node n && n.kind == rpg.objects.Node.Kind.FISH) return null;
        return spec;
    }
    /** (0.4.7) Where the hands work: the thing worked on, or (on oneself: a grave, a fire) the ground just ahead. */
    static float[] handWorkAt(Actor a, float[] spec) {
        var w = a.working;
        float fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        boolean self = w.target == null || w.target.id() == a.id;
        float x = self ? a.x + fx * 0.6f : w.target.ax(), z = self ? a.z + fz * 0.6f : w.target.az();
        float d = (float) Math.hypot(x - a.x, z - a.z);
        // no further than an arm's length ahead, whatever the target's middle is (a cow's, a tree's)
        if (d > 0.7f) { x = a.x + (x - a.x) / d * 0.7f; z = a.z + (z - a.z) / d * 0.7f; }
        return new float[]{x, a.y + spec[4], z};
    }''')

sub(R, '''        if (a.working != null && a.working.action.equals("fill") && !firstPerson) {''', '''        float[] hw = firstPerson ? null : handWork(a);
        if (hw != null) {
            float k = smooth(clamp01(a.working.t / 0.35f)) * smooth(clamp01((a.working.duration - a.working.t) / 0.3f));
            float beat = (float) Math.sin(a.working.t * hw[2]);
            crouch += k * (hw[0] + 0.35f * Math.abs(hw[0]) * 0.15f * beat);
            extraLean += k * (hw[1] + 0.04f * beat);
            extraTwist += k * 0.05f * (float) Math.sin(a.working.t * hw[2] * 0.5f);
        }
        if (a.working != null && a.working.action.equals("fill") && !firstPerson) {''')

sub(R, '''            } else if (a.working != null && a.working.action.equals("fill")) {
                // a hand dipped down to the water ahead, held there while it fills''', '''            } else if (handWork(a) != null && !firstPerson) {
                // (0.4.7) the hands at the work: on the udder, the fleece, the carcass, the furrow, the bough, the grave,
                // the kindling, the purse - each stroke in its rhythm, hand after hand or the two together
                var w = a.working;
                float[] spec = handWork(a), at = handWorkAt(a, spec);
                float k = smooth(clamp01(w.t / 0.35f)) * smooth(clamp01((w.duration - w.t) / 0.3f));
                float stroke = (float) Math.sin(w.t * spec[2] + (spec[5] > 0 ? side * Math.PI / 2 : 0));
                float[] spot = toFigure(a, at[0] + (float) Math.cos(a.yaw) * side * 0.14f, at[1], at[2] - (float) Math.sin(a.yaw) * side * 0.14f);
                spot = add(spot, torsoUp, stroke * spec[3]);
                float[] off = sub(spot, shoulder);
                float reach = (L_ARM1 + L_ARM2) * 0.97f, len = (float) Math.sqrt(off[0] * off[0] + off[1] * off[1] + off[2] * off[2]);
                if (len > reach) spot = add(shoulder, off, reach / len);
                target = lerp(rest, spot, k * (isMain || spec[5] > 0 || spec[3] > 1.5f ? 1 : 0.55f));
                dir = norm(lerp(dir, spec[4] > 1.2f ? new float[]{0, 1, -0.3f} : new float[]{0, -1, -0.35f}, k));
            } else if (a.working != null && a.working.action.equals("fill")) {
                // a hand dipped down to the water ahead, held there while it fills''')
print("ok")
