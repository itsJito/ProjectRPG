R = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg'

def patch(p, pairs):
    s = open(R + p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:70], s.count(a))
        s = s.replace(a, b)
    open(R + p, 'w', encoding='utf-8').write(s)

patch(r'\Rpg.java', [
('''        sfx.ground = a -> vox.block((int) Math.floor(a.x), (int) Math.floor(a.y - 0.05f), (int) Math.floor(a.z));''',
'''        sfx.ground = a -> vox.block((int) Math.floor(a.x), (int) Math.floor(a.y - 0.05f), (int) Math.floor(a.z));
        // (0.4.6) a step in shallow water splashes; open ground the snow has settled on crunches
        sfx.wading = a -> fluids != null && fluids.in(a.x, a.y + 0.1f, a.z) && fluids.depth(a.x, a.y + 0.1f, a.z) < 0.9f;
        sfx.snowy = a -> timeSystem.weather.snowCover > 0.3f && vox.skyLight((int) Math.floor(a.x), (int) Math.floor(a.y) + 1, (int) Math.floor(a.z)) > 0.7f;'''),
])

# the rig counts each foot it sets down: the footfalls follow the feet the figure is drawn walking with
patch(r'\render\Rig.java', [
('''    private static void plant(Actor a, State st, int k) {
        st.stance[k] = true;''',
'''    private static void plant(Actor a, State st, int k) {
        // (0.4.6, gamefixes: footsteps in time with the actual steps of the feet) the figure's foot is down: counted, for the
        // footfall's sound (heard when the game next looks: Actor.footPlants)
        if (!st.stance[k]) { a.footPlants++; a.footPlantSeen = System.nanoTime(); }
        st.stance[k] = true;'''),
])
patch(r'\actor\Actor.java', [
('''    public float ragLastX, ragLastY, ragLastZ, ragCaught;''',
'''    public float ragLastX, ragLastY, ragLastZ, ragCaught;
    /** (0.4.6) Feet the drawn figure has set down, the last time one was (System.nanoTime), and how many have been heard. */
    public int footPlants, footPlantsHeard;
    public long footPlantSeen;
    /** Whether the drawn figure set a foot down lately (its footfalls are its own then, not the clock's): how many new. */
    public int newFootPlants() {
        if (System.nanoTime() - footPlantSeen > 600_000_000L) { footPlantsHeard = footPlants; return -1; }
        int n = footPlants - footPlantsHeard; footPlantsHeard = footPlants; return Math.min(2, Math.max(0, n));
    }'''),
])
patch(r'\actor\PlayerController.java', [
('''        if (speed > Gait.STILL && p.grounded && p.gaseousT <= 0) {
            stepClock += dt * Gait.steps(speed, p.sneaking);
            if (stepClock >= 1) {
                stepClock -= 1;''',
'''        if (speed > Gait.STILL && p.grounded && p.gaseousT <= 0) {
            // (0.4.6) the figure's own feet when it is drawn (third person, a mirror); the gait's clock when it is not
            int planted = p.newFootPlants();
            if (planted < 0) stepClock += dt * Gait.steps(speed, p.sneaking);
            else stepClock = planted > 0 ? 1 : Math.min(stepClock, 0.99f);
            if (stepClock >= 1) {
                stepClock -= 1;'''),
])
patch(r'\ai\AiSystem.java', [
('''        n.stepClock += dt * rpg.actor.Gait.steps(speed, n.sneaking);
        if (n.stepClock >= 1) { n.stepClock -= 1;''',
'''        int planted = n.newFootPlants();
        if (planted < 0) n.stepClock += dt * rpg.actor.Gait.steps(speed, n.sneaking);
        else n.stepClock = planted > 0 ? 1 : Math.min(n.stepClock, 0.99f);
        if (n.stepClock >= 1) { n.stepClock -= 1;'''),
])
# hooves: a horse's or a Pegasus's four feet, in their own beat
patch(r'\animal\Animals.java', [
('''            if (!a.loaded) continue;
            float rate = a.cs.frozenT>0?0.001f:a.actionRate()*(a.cs.slowT>0?0.6f:1)*waterPace(a);''',
'''            if (!a.loaded) continue;
            hooves(a, dt);
            float rate = a.cs.frozenT>0?0.001f:a.actionRate()*(a.cs.slowT>0?0.6f:1)*waterPace(a);'''),
('''    public void update(float dt) {
        float dm = (float) (dt * g.minuteRate());''',
'''    /**
     * (0.4.6, gamefixes: horses' and the Pegasus's footfalls) a horse's hooves near the player: four to a stride, each
     * struck on the ground it is on (a shod hoof rings on cobbles, drums on a bridge), a walk's slow beat, a gallop's
     * quick one; none in the air.
     */
    private void hooves(Animal a, float dt) {
        if (g.sfx == null || a.species != rpg.animal.Species.HORSE && a.species != rpg.animal.Species.PEGASUS) return;
        float speed = (float) Math.hypot(a.vx, a.vz);
        if (!a.grounded || speed < 0.4f || a.distTo(g.player) > HOOF_HEARD) { a.hoofClock = 0.5f; return; }
        // strides a second rise with the pace and level off at the gallop; four footfalls a stride
        a.hoofClock += dt * 4 * Math.min(2.4f, 0.55f + speed * 0.16f);
        if (a.hoofClock >= 1) { a.hoofClock -= 1; g.sfx.footstep(a, Math.min(1, speed / 9f)); }
    }
    static final float HOOF_HEARD = rpg.Tuning.f("audio.hoofHeard", 30f);

    public void update(float dt) {
        float dm = (float) (dt * g.minuteRate());'''),
])
patch(r'\animal\Animal.java', [
])
print('ok')
