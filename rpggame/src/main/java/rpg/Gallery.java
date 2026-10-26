package rpg;

import astratto.bridge.Snapshots;

import astratto.game.Game;
import astratto.platform.Window;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.lwjgl.system.MemoryUtil;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.render.Gfx;
import rpg.ui.Hud;
import rpg.ui.Ui2D;
import rpg.world.DevMap;
import rpg.world.Blocks;

/**
 * The developer's review of figures, animation and objects (./gradlew gallery): every motion is filmed from a fixed
 * camera and cut into a contact sheet of every third frame (frames 1, 4, 7, 10...; gamefixes' review rule), labelled with
 * the frame and the time, so a cycle can be judged step by step - walking seen from the side and from the front,
 * strafing, backing away, sprinting, starting and stopping, turning on an arc, the quick turn, the light and heavy
 * blows; and the creator's catalogs, every hairstyle, beard, top, legs and shoes on the figure. Then the devmap's
 * objects. Nothing is checked; it is for eyes.
 */
final class Gallery {
    private final Window win;
    private final Game game;
    private final Gfx gfx;
    private final Ui2D ui;
    private final String shots;
    private Rpg g;
    private Hud hud;
    private static final int TILE = 300;
    private int reviewMesh;
    private final astratto.math.Mat4 reviewTransform=new astratto.math.Mat4();

    Gallery(Window win, Game game, Gfx gfx, Ui2D ui, String shots) { this.win = win; this.game = game; this.gfx = gfx; this.ui = ui; this.shots = shots == null ? "shots" : shots; }

    int run(boolean female) {
        Appearance look = Main.defaultLook();
        look.female = female;
        look.bottomColor = 20; look.topColor = 8; look.shoesColor = 16;
        if (female) { look.beard = 0; look.hair = 13; look.bottom = 1; }
        g = game.add(new Rpg(game, "Gallery", look, true));
        hud = new Hud(g, gfx);
        g.ui = hud;
        gfx.findLamps(g.vox);
        // keep the townsfolk out of the picture
        for (var n : g.npcs) g.ai.unload(n);
        String tag = female ? "f" : "m";
        if ("maintenance".equals(System.getenv("RPG_GALLERY_ONLY"))) { maintenanceShots(); return 0; }
        if ("ultimate".equals(System.getenv("RPG_GALLERY_ONLY"))) { ultimateShots(); return 0; }
        if ("release-systems".equals(System.getenv("RPG_GALLERY_ONLY"))) { releaseSystemsShots(); return 0; }
        if ("release-polish".equals(System.getenv("RPG_GALLERY_ONLY"))) { releasePolishShots(); return 0; }
        if ("system-polish".equals(System.getenv("RPG_GALLERY_ONLY"))) { systemPolishShots(); return 0; }
        if ("session-polish".equals(System.getenv("RPG_GALLERY_ONLY"))) { sessionPolishShots(); return 0; }
        if ("pause".equals(System.getenv("RPG_GALLERY_ONLY"))) { pauseShot(); return 0; }
        // RPG_GALLERY_ONLY=gamefixes-ui / gamefixes-blocks: the menus and windows of this pass, and the new blocks where they stand
        if ("gamefixes-ui".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesUi(); return 0; }
        if ("gamefixes-blocks".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesBlocks(); return 0; }
        if ("gamefixes-aim".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesAim(); return 0; }
        if ("gamefixes-foliage".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesFoliage(); return 0; }
        if ("gamefixes-doors".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesDoors(); return 0; }
        if ("gamefixes-bricks".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesBricks(); return 0; }
        if ("gamefixes-carcass".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesCarcass(); return 0; }
        if ("gamefixes-fpdown".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesFirstPersonDown(); return 0; }
        if ("gamefixes-arrows".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesArrows(); return 0; }
        if ("gamefixes-bowpose".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesBowPose(); return 0; }
        if ("gamefixes-grab".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesGrab(); return 0; }
        if ("gamefixes-bank".equals(System.getenv("RPG_GALLERY_ONLY"))) { gamefixesBank(); return 0; }
        if ("field-polish".equals(System.getenv("RPG_GALLERY_ONLY"))) { fieldPolishShots(); menuShots(); return 0; }
        if ("polish".equals(System.getenv("RPG_GALLERY_ONLY"))) { polishShots(); return 0; }
        // A short review of the three locomotion loops, without the unrelated twitch/work/combat galleries.
        if ("locomotion".equals(System.getenv("RPG_GALLERY_ONLY"))) { locomotionShots(tag); return 0; }
        // RPG_GALLERY_ONLY=animals: just the animal sheets (a quick look while working on them)
        if ("animals".equals(System.getenv("RPG_GALLERY_ONLY"))) { if (!female) animalSheets(); return 0; }
        // RPG_GALLERY_ONLY=motion: the locomotion and blows only, each also measured for twitching under uneven frames
        boolean motionOnly = "motion".equals(System.getenv("RPG_GALLERY_ONLY"));
        // RPG_GALLERY_ONLY=daycycle: one view of the street across a whole day, each still after the eye has adapted
        if ("daycycle".equals(System.getenv("RPG_GALLERY_ONLY"))) { dayCycle(); return 0; }
        if ("creator".equals(System.getenv("RPG_GALLERY_ONLY"))) { creatorShots(); return 0; }
        // RPG_GALLERY_ONLY=death: the player cut down in town, the death watched through, then the grave on the map
        if ("death".equals(System.getenv("RPG_GALLERY_ONLY"))) { deathShots(); return 0; }
        if ("npc-deaths".equals(System.getenv("RPG_GALLERY_ONLY"))) { npcDeathShots(); return 0; }
        if ("rumors".equals(System.getenv("RPG_GALLERY_ONLY"))) { rumorShots(); return 0; }
        // RPG_GALLERY_ONLY=moods: townsfolk in the square showing what they feel and are doing, over their heads
        if ("moods".equals(System.getenv("RPG_GALLERY_ONLY"))) { moodShots(); return 0; }
        // RPG_GALLERY_ONLY=animalfall: a wolf killed by a blow and a bear knocked off its feet, filmed as they fall
        if ("animalfall".equals(System.getenv("RPG_GALLERY_ONLY"))) { animalFall(); return 0; }
        // RPG_GALLERY_ONLY=figures: every build of both sexes from the front, three-quarters, side and back, no interface
        if ("figures".equals(System.getenv("RPG_GALLERY_ONLY"))) { figureShots(); return 0; }
        if ("menus".equals(System.getenv("RPG_GALLERY_ONLY"))) { menuShots(); creatorShots(); return 0; }
        if ("gear".equals(System.getenv("RPG_GALLERY_ONLY"))) { gearShots(); heldShots(); return 0; }
        if ("items".equals(System.getenv("RPG_GALLERY_ONLY"))) { itemSheet(); return 0; }
        if ("gore".equals(System.getenv("RPG_GALLERY_ONLY"))) { goreShots(); return 0; }
        // RPG_GALLERY_ONLY=scenery: the grove, the pond and the street at several hours (leaves, shadows, water, haze)
        if ("scenery".equals(System.getenv("RPG_GALLERY_ONLY"))) { scenery(); return 0; }
        // RPG_GALLERY_ONLY=reactions: blows of three strengths on a bandit, Sydney's answer filmed
        if ("reactions".equals(System.getenv("RPG_GALLERY_ONLY"))) { reactionShots(); return 0; }
        // RPG_GALLERY_ONLY=catalogs: only the creator's catalogs on the figure (a quick look at an outfit change)
        boolean catalogsOnly = "catalogs".equals(System.getenv("RPG_GALLERY_ONLY"));
        if (motionOnly) { twitchReport(); }
        if (!motionOnly && !catalogsOnly) {
            skinPng(look, null, tag + "-skin");
            if (!female) itemSheet();
            if (!female) animalSheets();
        }
        if (catalogsOnly) { catalogs(tag, female, look); return 0; }
        // ---- locomotion, filmed on threes
        float x0 = 58.5f, z0 = 66.5f;
        place(x0, z0, (float) -Math.PI / 2);
        frames(30);
        film(tag + "-walk-side", 45, i -> { g.controller.in.forward = 1; }, a -> side(a, 3.6f), 30);
        film(tag + "-stop", 45, i -> { g.controller.in.forward = 0; }, a -> side(a, 3.6f), 0);
        place(64.5f, 58.5f, (float) Math.PI);
        frames(10);
        film(tag + "-start-front", 45, i -> { g.controller.in.forward = 1; }, a -> front(a, 4.2f), 0);
        film(tag + "-walk-front", 45, i -> { g.controller.in.forward = 1; }, a -> front(a, 4.2f), 0);
        place(64.5f, 62.5f, (float) Math.PI);
        frames(10);
        film(tag + "-strafe", 45, i -> { g.controller.in.forward = 0; g.controller.in.strafe = 1; g.controller.in.offDown = false; }, a -> front(a, 4.2f), 15);
        film(tag + "-backpedal", 45, i -> { g.controller.in.strafe = 0; g.controller.in.forward = -1; }, a -> front(a, 4.2f), 15);
        g.controller.in.forward = 0;
        frames(30);
        place(54.5f, 64.5f, (float) -Math.PI / 2);
        frames(10);
        g.controller.jogging = true;
        film(tag + "-jog", 36, i -> { g.controller.in.forward = 1; }, a -> side(a, 4.2f), 30);
        g.controller.jogging = false; g.controller.in.forward = 0;
        frames(40);
        place(54.5f, 64.5f, (float) -Math.PI / 2);
        frames(10);
        // a sprint: Shift held
        film(tag + "-run", 36, i -> { g.controller.in.sprint = true; g.controller.in.forward = 1; }, a -> side(a, 4.6f), 40);
        film(tag + "-sprint-stop", 36, i -> { g.controller.in.sprint = false; g.controller.in.forward = 0; }, a -> side(a, 4.2f), 0);
        frames(30);
        // a turn: walking, then asked to go left; the body carries round an arc
        place(60.5f, 70.5f, 0);
        frames(10);
        film(tag + "-turn", 45, i -> { g.controller.in.forward = 1; if (i == 12) g.controller.camYaw += (float) Math.PI / 2; }, a -> new float[]{60.5f, a.y + 4.5f, 70.5f + 1.5f, 60.5f, a.y, 67.5f}, 6);
        g.controller.in.forward = 0;
        frames(40);
        // the quick turn: back tapped twice
        place(64.5f, 66.5f, (float) Math.PI);
        frames(20);
        film(tag + "-quickturn", 30, i -> { g.controller.in.backTap = i == 1 || i == 5; }, a -> new float[]{a.x + 3.2f, a.y + 1.4f, a.z + 1.5f, a.x, a.y + 0.9f, a.z}, 0);
        frames(30);
        // blows: a light swing and a heavy one
        place(64.5f, 66.5f, (float) Math.PI);
        frames(30);
        film(tag + "-light", 42, i -> { g.controller.in.mainDown = i == 0; }, a -> quarter(a, 3.4f), 0);
        frames(30);
        film(tag + "-heavy", 66, i -> { g.controller.in.mainDown = i < 30; }, a -> quarter(a, 3.4f), 0);
        frames(30);
        if (motionOnly) return 0;
        catalogs(tag, female, look);
        // ---- objects: the workstations, the shed, a house's beds, the archery range, the counter
        view(68.5f, DevMap.FLOOR + 1.6f, 31.5f, 68.5f, DevMap.FLOOR + 0.6f, 26.5f, tag + "-workstations");
        view(54.5f, DevMap.FLOOR + 1.7f, 27f, 54.5f, DevMap.FLOOR + 1f, 21.5f, tag + "-shed-door");
        view(92.5f, DevMap.FLOOR + 2.2f, 88.0f, 92.5f, DevMap.FLOOR + 0.4f, 92f, tag + "-beds");
        view(14.5f, DevMap.FLOOR + 1.6f, 58.5f, 7.5f, DevMap.FLOOR + 1f, 57f, tag + "-targets");
        view(91.5f, DevMap.FLOOR + 1.7f, 61.0f, 91.5f, DevMap.FLOOR + 0.8f, 57f, tag + "-counter");
        // ---- creeping, by day and at one in the morning (the eye given time to adapt to the dark)
        place(64.5f, 66.5f, (float) Math.PI);
        g.controller.in.sneak = true;
        frames(40);
        adapted(quarter(g.player, 3.4f), tag + "-sneak-day");
        if (!female) {
            g.advanceTime(((1 - g.state.hourOfDay()) + 24) % 24 * 60, "night views");
            adapted(quarter(g.player, 3.4f), tag + "-sneak-night");
            adapted(new float[]{64.5f, DevMap.FLOOR + 1.7f, 74f, 64.5f, DevMap.FLOOR + 1.2f, 58f}, tag + "-night-street");
        }
        g.controller.in.sneak = false;
        return 0;
    }

    /** Walk, jog and sprint at steady speed, each a 1.2-second loop filmed on threes. */
    private void locomotionShots(String tag) {
        g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, null);
        g.player.eq.set(rpg.item.ItemDef.Slot.OFF, null);
        for (String gait : new String[]{"walk", "jog", "sprint"}) {
            g.controller.in.forward = 0;
            g.controller.in.sprint = false;
            g.controller.jogging = false;
            place(54.5f, 64.5f, (float) -Math.PI / 2);
            frames(30);
            g.player.stamina = g.player.maxStamina();
            g.controller.jogging = gait.equals("jog");
            // The north-side view passes through the altar as the runner crosses the square.
            // Keep the entire sequence on the clear south side instead of flipping cameras mid-cycle.
            film(tag + "-locomotion-" + gait, 72, i -> {
                g.controller.in.forward = 1;
                g.controller.in.sprint = gait.equals("sprint");
            }, a -> side(a, -4.6f), 60);
        }
        // turning round on the spot: the double-tapped back key's quick turn, and a slower pivot of the view
        g.controller.in.forward = 0; g.controller.in.sprint = false; g.controller.jogging = false;
        place(54.5f, 64.5f, 0);
        frames(40);
        float[] fixed = {g.player.x + 3.3f, g.player.y + 1.5f, g.player.z + 2.6f, g.player.x, g.player.y + 0.85f, g.player.z};
        film(tag + "-locomotion-turnaround", 54, i -> { g.controller.in.backTap = i == 0 || i == 5; }, a -> fixed, 0);
        place(54.5f, 64.5f, 0);
        frames(40);
        film(tag + "-locomotion-pivot", 72, i -> { if (i >= 0 && i < 45) g.controller.snapTo(i * (float) Math.PI / 45); }, a -> fixed, 0);
    }

    /**
     * Films a motion: n frames at 60 a second, the input set each frame by drive; every third frame (1, 4, 7...) is
     * cut, centred on the actor, into a contact sheet. warm: frames run first, unfilmed, to reach a steady state.
     */
    private void film(String name, int n, IntConsumer drive, java.util.function.Function<Actor, float[]> camera, int warm) {
        film(name, n, drive, camera, warm, g.player);
    }

    private void film(String name, int n, IntConsumer drive, java.util.function.Function<Actor, float[]> camera, int warm, Actor focus) {
        for (int i = 0; i < warm; i++) { drive.accept(-1); step(); }
        List<BufferedImage> tiles = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            drive.accept(i);
            game.tick(1f / 60);
            draw(camera.apply(focus), 1f / 60);
            if (i % 3 == 0) {
                tiles.add(captureFrame());
                labels.add(String.format("f%d  %.3fs  %.1fm/s", i + 1, i / 60f, Math.hypot(focus.vx, focus.vz)));
            }
        }
        sheet(name, tiles, labels, 8);
    }

    /**
     * The twitch measure: the figure walked, run, strafed and stopped under the frame times a real machine gives
     * (uneven: 7 to 40 ms, the odd long frame), its knees, ankles and hands sampled every frame in the body's own space.
     * A smooth motion passes through the middle of its neighbours (in time); a twitch does not. Reported per motion as
     * the mean and worst departure from that, in centimetres, and the worst frame-to-frame jump.
     */
    private void twitchReport() {
        java.util.Random rnd = new java.util.Random(7);
        gfx.probeId = g.player.id;
        String[] names = {"idle", "walk", "turn-walk", "strafe", "backpedal", "sprint", "stop", "creep"};
        for (String name : names) {
            place(64.5f, 66.5f, (float) Math.PI);
            frames(20);
            List<float[]> samples = new ArrayList<>();
            List<Float> times = new ArrayList<>();
            float t = 0;
            for (int i = 0; i < 240; i++) {
                var in = g.controller.in;
                in.forward = 0; in.strafe = 0; in.sprint = false; in.sneak = false;
                switch (name) {
                    case "walk" -> in.forward = 1;
                    case "turn-walk" -> { in.forward = 1; g.controller.camYaw += 0.012f; }
                    case "strafe" -> in.strafe = 1;
                    case "backpedal" -> in.forward = -1;
                    case "sprint" -> { in.forward = 1; in.sprint = true; }
                    case "stop" -> in.forward = i < 90 ? 1 : 0;
                    case "creep" -> { in.forward = 1; in.sneak = true; }
                    default -> { }
                }
                float dt = System.getenv("RPG_TWITCH_FIXED") != null ? 1 / 60f : rnd.nextFloat() < 0.04f ? 0.033f + rnd.nextFloat() * 0.007f : 0.007f + rnd.nextFloat() * 0.013f;
                game.tick(dt);
                gfx.probeFresh = false;
                draw(null, dt);
                g.controller.in.backTap = false;
                t += dt;
                if (gfx.probeFresh && i >= 30) { samples.add(java.util.Arrays.copyOf(gfx.probe, 18)); times.add(t); }
                if (System.getenv("RPG_TWITCH_DUMP") != null && name.equals(System.getenv("RPG_TWITCH_DUMP")) && i >= 30 && i < 70)
                    System.out.printf("   %s dt %.4f knee R (%.2f %.2f %.2f) L (%.2f %.2f %.2f) hand R (%.2f %.2f %.2f) pos (%.3f %.4f %.3f) v (%.2f %.2f) yaw %.3f gr %s hip %.2f want %.2f flags %.1f%n", name, dt,
                            gfx.probe[0], gfx.probe[1], gfx.probe[2], gfx.probe[3], gfx.probe[4], gfx.probe[5], gfx.probe[12], gfx.probe[13], gfx.probe[14], g.player.x, g.player.y, g.player.z, g.player.vx, g.player.vz, g.player.yaw, g.player.grounded, gfx.probe[18], gfx.probe[19], gfx.probe[20]);
            }
            double sum = 0, worst = 0, jump = 0;
            int n = 0;
            for (int i = 1; i + 1 < samples.size(); i++) {
                float t0 = times.get(i - 1), t1 = times.get(i), t2 = times.get(i + 1);
                float u = (t1 - t0) / Math.max(1e-5f, t2 - t0);
                float[] a = samples.get(i - 1), b = samples.get(i), c = samples.get(i + 1);
                for (int j = 0; j < 18; j += 3) {
                    double ex = a[j] + (c[j] - a[j]) * u - b[j], ey = a[j + 1] + (c[j + 1] - a[j + 1]) * u - b[j + 1], ez = a[j + 2] + (c[j + 2] - a[j + 2]) * u - b[j + 2];
                    double d = Math.sqrt(ex * ex + ey * ey + ez * ez) * rpg.actor.Body.PX * 100;
                    sum += d; n++;
                    worst = Math.max(worst, d);
                    double jx = b[j] - a[j], jy = b[j + 1] - a[j + 1], jz = b[j + 2] - a[j + 2];
                    jump = Math.max(jump, Math.sqrt(jx * jx + jy * jy + jz * jz) * rpg.actor.Body.PX * 100);
                }
            }
            System.out.printf("[twitch] %-10s mean %.2f cm, worst %.2f cm, worst jump %.2f cm (%d samples)%n", name, n == 0 ? 0 : sum / n, worst, jump, samples.size());
        }
        gfx.probeId = -1;
        g.controller.in.forward = 0; g.controller.in.sneak = false;
    }

    /** The character creator as the player sees it, for a few looks (each shot after the figure has settled). */
    /** A shield and a sword in hand: standing, walking and guarding, from the front, the side, and through the eyes. */
    private void gearShots() {
        for (var n : g.npcs) g.ai.unload(n);
        g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(rpg.item.Items.get("iron_sword"), 1));
        g.player.eq.set(rpg.item.ItemDef.Slot.OFF, new rpg.item.ItemStack(rpg.item.Items.get("iron_shield"), 1));
        place(64.5f, 62.5f, (float) Math.PI);
        frames(30);
        java.util.List<BufferedImage> tiles = new ArrayList<>();
        java.util.List<String> labels = new ArrayList<>();
        String[] poses = {"stand", "guard"};
        for (String pose : poses) {
            g.controller.in.offDown = pose.equals("guard");
            frames(30);
            tiles.add(capture(front(g.player, 2.6f))); labels.add(pose + " front");
            tiles.add(capture(side(g.player, 2.6f))); labels.add(pose + " side");
            tiles.add(capture(quarter(g.player, 2.8f))); labels.add(pose + " quarter");
        }
        g.controller.in.offDown = false;
        g.controller.in.forward = 1;
        frames(40);
        tiles.add(capture(side(g.player, 3f))); labels.add("walk side");
        g.controller.in.forward = 0;
        frames(30);
        sheet("gear-shield", tiles, labels, 4);
        // through the eyes, standing and guarding
        g.player.firstPerson = true;
        for (String pose : poses) {
            g.controller.in.offDown = pose.equals("guard");
            frames(30);
            Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false);
            Main.shot(gfx, win.width, win.height, new File(shots, "gear-fp-" + pose + ".png"));
        }
        g.controller.in.offDown = false;
        g.player.firstPerson = false;
    }

    /** The weapons in hand, family by family and tier by tier, from the side in daylight. */
    private void heldShots() {
        String[] ids = {"wooden_sword", "bronze_sword", "iron_sword", "steel_sword", "cobalt_sword", "dragon_sword", "godly_sword", "iron_2hsword", "godly_2hsword",
                "bronze_axe", "steel_axe", "iron_battleaxe", "dragon_battleaxe", "iron_mace", "black_mace", "steel_dagger", "bronze_pickaxe", "molten_pickaxe",
                "wooden_shield", "bronze_shield", "steel_shield", "dragon_shield", "malevolent_shield", "fishing_rod"};
        java.util.List<BufferedImage> tiles = new ArrayList<>();
        java.util.List<String> labels = new ArrayList<>();
        place(64.5f, 62.5f, (float) Math.PI);
        for (String id : ids) {
            var d = rpg.item.Items.get(id);
            g.player.eq.set(rpg.item.ItemDef.Slot.OFF, null);
            g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, null);
            g.player.eq.set(d.slot == rpg.item.ItemDef.Slot.OFF ? rpg.item.ItemDef.Slot.OFF : rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(d, 1));
            g.controller.in.offDown = d.family == rpg.item.ItemDef.Family.SHIELD;
            frames(24);
            tiles.add(capture(d.family == rpg.item.ItemDef.Family.SHIELD ? quarter(g.player, 2.6f) : side(g.player, 2.4f)));
            labels.add(d.name);
        }
        g.controller.in.offDown = false;
        sheet("gear-held", tiles, labels, 6);
    }

    /** The title screen (empty, and with three profiles) and the console open over the world. */
    private void pauseShot() {
        hud.dialog(Main.menu(win, g));
        for (int i = 0; i < 24; i++) Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false, true);
        Main.shot(gfx, win.width, win.height, new File(shots, "pause.png"));
        hud.closeAll();
    }

    /** Every menu and window of the gamefixes pass, in the real HUD over the real world. */
    private void gamefixesUi() {
        g.state.minutes = 13 * 60;
        place(55.5f, 12.5f, (float) Math.PI);
        frames(30);
        for (Hud.Scene sc : new Hud.Scene[]{Hud.Scene.INVENTORY, Hud.Scene.CHARACTER, Hud.Scene.SKILLS, Hud.Scene.JOURNAL, Hud.Scene.MAGIC, Hud.Scene.MAP}) {
            hud.go(sc);
            reviewUi("ui-" + sc.name().toLowerCase());
            hud.closeAll();
        }
        var chest = g.objects.all().stream().filter(o -> o instanceof rpg.objects.Container c && c.kind.equals("chest")).map(o -> (rpg.objects.Container) o)
                .min(java.util.Comparator.comparingDouble(c -> c.dist(g.player))).orElse(null);
        if (chest != null) { place(chest.x + 1.2f, chest.z + 0.5f, 0); hud.openContainer(chest); reviewUi("ui-container"); hud.closeAll(); }
        rpg.ai.Npc maud = (rpg.ai.Npc) g.actor(Ids.HEALER);
        rpg.ui.Dialog offer = new rpg.ui.Dialog(maud.name, maud.name + " holds out some redberries.");
        offer.npc = 0; offer.offer = new rpg.item.ItemStack(rpg.item.Items.BERRIES, 1);
        offer.closeChoice("Accept", () -> { }); offer.closeChoice("Decline", () -> { });
        hud.dialog(offer); reviewUi("ui-offer"); hud.closeAll();
        rpg.ui.Dialog talk = new rpg.ui.Dialog("Bram - Lawman", "You are wanted in DevCity for 240 coins. Witnesses saw you take a loaf from the stall, and the merchant swears it was you. Pay, explain yourself, or come quietly - but make up your mind before the guards do it for you.");
        talk.choice("Pay 240 coins.", () -> { }); talk.choice("You have the wrong person. I was at the shed all morning, ask anyone who was there - the woodcutter saw me. (lie; risk jail)", () -> { });
        talk.choice("I'll come quietly.", () -> { }); talk.closeChoice("Never! (resist arrest)", () -> { });
        hud.dialog(talk); reviewUi("ui-dialogue"); hud.closeAll();
        for (int i = 0; i < 6; i++) g.chat.say("A long Worldbox report " + i + ": belongings held by the Undertaker exclude stolen goods, confiscated property returns to its rightful owner, and this line should wrap cleanly.");
        reviewUi("ui-chat");
        g.law.reminder = "DevCity: you are wanted for 240 G.";
        hud.events.emit("bountynotice", "");
        reviewUi("ui-bounty-notice");
        pauseShot();
    }

    /** Third-person ranged aiming: the side-on stance with a bow, a crossbow and a throwing javelin, filmed as the draw comes up. */
    private void gamefixesAim() {
        g.state.minutes = 12 * 60;
        g.player.firstPerson = false;
        for (String[] kit : new String[][]{{"shortbow", "bronze_arrows"}, {"iron_crossbow", "iron_bolts"}, {"iron_javelin", null}}) {
            place(54.5f, 64.5f, 0);
            g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(rpg.item.Items.get(kit[0]), kit[1] == null ? 5 : 1));
            g.player.eq.set(rpg.item.ItemDef.Slot.AMMO, kit[1] == null ? null : new rpg.item.ItemStack(rpg.item.Items.get(kit[1]), 30));
            g.player.skills.setLevel(rpg.skill.Skill.ARCHERY, 50);
            frames(30);
            float[] cam = {g.player.x + 3.0f, g.player.y + 1.6f, g.player.z + 1.2f, g.player.x, g.player.y + 1.0f, g.player.z - 0.4f};
            film("aim-" + kit[0], 45, i -> g.controller.in.mainDown = true, a -> cam, 0);
            g.controller.in.mainDown = false;
            frames(30);
        }
        // the throw guide from over the shoulder, a javelin drawn back
        place(64.5f, 70.5f, 0);
        g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(rpg.item.Items.get("iron_javelin"), 5));
        g.controller.camPitch = 0.12f;
        frames(20);
        for (int i = 0; i < 40; i++) { g.controller.in.mainDown = true; frames(1); }
        reviewUi("aim-throw-guide");
        g.controller.in.mainDown = false; frames(30);
        // fishing at the pond: a rod, a net and a cage, each from the bank
        g.player.skills.setLevel(rpg.skill.Skill.FISHING, 60);
        for (rpg.item.ItemDef tool : new rpg.item.ItemDef[]{rpg.item.Items.FISHING_ROD, rpg.item.Items.FISHING_NET, rpg.item.Items.CRAB_CAGE}) {
            var spot = g.objects.nodes.stream().filter(n -> n.kind == rpg.objects.Node.Kind.FISH && n.needs == tool).findFirst().orElse(null);
            if (spot == null) continue;
            float[] bank = g.ai.standSpot((rpg.ai.Npc) g.actor(Ids.FISHERMAN), spot);
            place(bank[0], bank[1], (float) Math.atan2(-(spot.x - bank[0]), -(spot.z - bank[1])));
            g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(tool, 1));
            frames(20);
            float fl = (float) Math.max(0.1, Math.hypot(spot.x - bank[0], spot.z - bank[1])), fx = (spot.x - bank[0]) / fl, fz = (spot.z - bank[1]) / fl;
            float[] cam = {g.player.x - fz * 3.4f - fx * 1.2f, g.player.y + 1.7f, g.player.z + fx * 3.4f - fz * 1.2f, g.player.x + fx * 1.2f, g.player.y + 0.8f, g.player.z + fz * 1.2f};
            var r = g.interact.request(g.player, spot, "gather");
            System.out.println("[fishing] " + tool.id + " -> " + r.status() + " " + r.reason());
            film("fishing-" + tool.id, 90, i -> { }, a -> cam, 0);
            g.interact.cancel(g.player, "review");
            frames(10);
        }
    }

    /** The new blocks where the devmap uses them, each from a fixed camera once the exposure has settled. */
    private void gamefixesBlocks() {
        g.state.minutes = 12 * 60;
        place(64.5f, 68.5f, 0);
        for (var n : g.npcs) g.ai.unload(n);
        Object[][] views = {
                {"block-barn-hay", new float[]{114.5f, 6.2f, 118.6f, 119.6f, 4.7f, 117.6f}},
                {"block-chancery-carpet", new float[]{113.5f, 6.4f, 61.4f, 113.5f, 4.0f, 55f}},
                {"block-house-rugs", new float[]{90f, 6.6f, 92.6f, 90f, 4.0f, 88.6f}},
                {"block-limestone-blue-glass", new float[]{35.5f, 7.0f, 104f, 35.5f, 6.0f, 117f}},
                {"block-store-metals", new float[]{59f, 6.0f, 18.5f, 61f, 4.6f, 21f}},
                {"block-marble-dais", new float[]{62.6f, 6.4f, 65f, 60.5f, 4.0f, 62.5f}},
                {"block-sandstone-cover", new float[]{30f, 6.0f, 46f, 31f, 5.0f, 55f}},
                {"block-granite-den", new float[]{12f, 6.6f, 12.5f, 7.5f, 5.5f, 6f}},
                {"block-pond-banks", new float[]{25f, 8.0f, 27f, 25f, 4.0f, 19.5f}},
                {"block-duck-pond-mud", new float[]{92f, 7.5f, 112.5f, 92f, 4.0f, 117.5f}},
                {"block-town-wall", new float[]{104f, 6.0f, 40f, 98f, 5.5f, 46.5f}},
                {"block-kiosk-birch", new float[]{66.5f, 6.0f, 98.5f, 61f, 5.0f, 103f}},
                {"block-undertaker-white-glass", new float[]{122f, 6.0f, 104f, 122f, 6.0f, 100f}}};
        for (Object[] v : views) adapted((float[]) v[1], (String) v[0]);
    }

    /** Character layout and first-person weapon poses, captured through the normal scene renderer. */
    private void releasePolishShots() {
        var preview = new Gfx.Preview();
        preview.world = rpg.world.TitleStage.build(game.engine()); preview.dungeon = true; preview.x = 8.5f; preview.y = DevMap.FLOOR; preview.z = 8.5f;
        var title = new rpg.ui.TitleScreen("0.1.2");
        for (int i = 0; i < 30; i++) { gfx.title(preview, Main.defaultLook(), win.width, win.height, 1f / 60, i / 60.0); ui.begin(gfx.cmd); title.draw(ui, win.width, win.height, 1f / 60, false, false); ui.end(); gfx.frame(win.width, win.height); }
        Main.shot(gfx, win.width, win.height, new File(shots, "title.png"));
        var creator = new rpg.ui.Creator();
        for (int i = 0; i < 15; i++) { gfx.creator(preview, creator.look, 0, win.width, win.height, 1f / 60, i / 60.0); ui.begin(gfx.cmd); creator.draw(ui, win.width, win.height, java.util.List.of(), false, false); ui.end(); gfx.frame(win.width, win.height); }
        Main.shot(gfx, win.width, win.height, new File(shots, "creator.png"));
        if ("stage".equals(System.getenv("RPG_REVIEW_ONLY"))) return;
        place(61.5f, 62.5f, 0);
        var horse = g.animals.display(rpg.animal.Species.HORSE, false, false, 0, 64.5f, 62.5f, 0);
        frames(20);
        film("horse-idle", 54, i -> {}, a -> new float[]{a.x + 3.6f, a.y + 1.8f, a.z - 3.6f, a.x, a.y + 1.1f, a.z}, 0, horse);
        film("horse-walk", 54, i -> g.animals.puppetTo(horse, horse.x, horse.z - 4, false), a -> new float[]{a.x + 3.6f, a.y + 1.8f, a.z - 3.6f, a.x, a.y + 1.1f, a.z}, 18, horse);
    }

    /** Scoped evidence for the maintenance fixes, using real game state and native rendering. */
    private void maintenanceShots() {
        String only=System.getenv("RPG_REVIEW_ONLY");
        g.state.minutes=600;g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();
        for(var animal:g.animals.all()){animal.loaded=false;g.animals.despawnBody(animal);}
        place(62.5f,70.5f,0);
        if ("ui".equals(only)) {
            g.player.inv.add(new rpg.item.ItemStack(rpg.item.Items.get("bronze_sword"),1));
            g.player.inv.add(new rpg.item.ItemStack(rpg.item.Items.IRON_ARROWS,18));
            g.player.eq.set(rpg.item.ItemDef.Slot.BACK,new rpg.item.ItemStack(rpg.item.Items.BACKPACK,1));
            hud.go(Hud.Scene.INVENTORY);reviewUi("vertical-inventory");hud.closeAll();
            var person=(rpg.ai.Npc)g.actor(Ids.VILLAGER);g.ai.reload(person);g.teleport(person,64,DevMap.FLOOR,70.5f);
            person.goal=rpg.ai.Brain.Goal.IDLE;person.nextThink=Double.MAX_VALUE;g.player.skills.setLevel(rpg.skill.Skill.THIEVING,100);
            person.inv.clear();person.inv.add(new rpg.item.ItemStack(rpg.item.Items.IRON_ARROWS,11,person.id,false,person.id));
            frames(1);hud.openContainer(person.pockets());reviewUi("inspect-pockets");
            int before=g.player.count(rpg.item.Items.IRON_ARROWS);
            for(int attempt=0;attempt<12 && g.player.count(rpg.item.Items.IRON_ARROWS)==before;attempt++) {
                g.player.cs.staggered=0;person.fightTarget=0;person.cs.cancel();
                g.interact.request(g.player,person.pockets(),"take:0");
                if(hud.paused())throw new IllegalStateException("Pocket work still paused");
                for(int i=0;i<75 && g.player.count(rpg.item.Items.IRON_ARROWS)==before;i++)game.tick(1f/60);
            }
            if(g.player.count(rpg.item.Items.IRON_ARROWS)==before)throw new IllegalStateException("No pocket transfer occurred");
            reviewUi("pocket-item-received");hud.closeAll();return;
        }
        if ("motion".equals(only)) {
            film("turn-in-place",120,i->g.controller.snapTo((float)Math.PI*Math.min(1,Math.max(0,i)/90f)),a->new float[]{a.x+2.6f,a.y+1.2f,a.z-2.6f,a.x,a.y+0.8f,a.z},0);
            var door=g.objects.doors().stream().filter(d->d.name.equals("Barn door")).findFirst().orElseThrow();
            var farmer=(rpg.ai.Npc)g.actor(Ids.FARMER);g.ai.reload(farmer);g.teleport(farmer,door.x,DevMap.FLOOR,door.z-2.8f);
            farmer.goal=rpg.ai.Brain.Goal.RETURN;farmer.nextThink=Double.MAX_VALUE;farmer.spawnX=door.x;farmer.spawnZ=door.z+3;
            float[] cam={door.x+3.4f,DevMap.FLOOR+2.2f,door.z-5,door.x,DevMap.FLOOR+0.9f,door.z};
            film("npc-opens-barn",120,i->{},a->cam,0,farmer);
            film("door-push-both-directions",120,i->{
                if(i==0)door.setOpen(g,true,null,0);
                if(i==0 || i==60)for(long hinge:door.hinge)g.game.commands().jointMotor(hinge,i==0?3:-3,120);
            },a->cam,0,farmer);return;
        }
        if ("foliage".equals(only)) {
            var boxes=rpg.render.FoliageModels.model(rpg.world.Foliage.Kind.FERN,37);
            reviewMesh=gfx.renderer().mesh(boxes.vertices(),boxes.count());reviewTransform.identity().translate(64.5f,DevMap.FLOOR,70.5f);
            g.teleport(g.player,64.5f,DevMap.FLOOR,71.4f);frames(1);
            float[] cam={66.3f,DevMap.FLOOR+1.35f,68.5f,64.5f,DevMap.FLOOR+0.28f,70.5f};
            view(cam[0],cam[1],cam[2],cam[3],cam[4],cam[5],"foliage-before-contact");
            g.controller.snapTo(0);
            film("foliage-contact",72,i->g.controller.in.forward=i<40?0.45f:0,a->cam,0);
            g.controller.in.forward=0;g.teleport(g.player,67.5f,DevMap.FLOOR,70.5f);
            view(cam[0],cam[1],cam[2],cam[3],cam[4],cam[5],"foliage-released");return;
        }
        if ("shadows".equals(only)) {
            var guard=(rpg.ai.Npc)g.actor(Ids.GUARD);g.ai.reload(guard);g.teleport(guard,65,DevMap.FLOOR,69.5f);
            guard.goal=rpg.ai.Brain.Goal.IDLE;guard.nextThink=Double.MAX_VALUE;g.teleport(g.player,64,DevMap.FLOOR,69.5f);
            float[] cam={69,DevMap.FLOOR+3.5f,64,64.5f,DevMap.FLOOR+0.5f,69.5f};
            for(int i=0;i<24;i++){game.tick(1f/60);draw(cam,1f/60);}
            Main.shot(gfx,win.width,win.height,new File(shots,"human-day-shadows.png"));
            g.state.minutes=1320;
            g.objects.add(new rpg.objects.Fire(g.state.newEntityId(),62.5f,DevMap.FLOOR,69.5f,g.state.minutes+30,0));
            for(int i=0;i<24;i++){game.tick(1f/60);draw(cam,1f/60);}
            Main.shot(gfx,win.width,win.height,new File(shots,"human-fire-shadows.png"));return;
        }
        throw new IllegalArgumentException("Use RPG_REVIEW_ONLY=ui, motion, foliage or shadows");
    }

    private void ultimateShots() {
        String only=System.getenv("RPG_REVIEW_ONLY");
        g.state.minutes=600;g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();
        for(var animal:g.animals.all())if(!animal.puppet){animal.loaded=false;g.animals.despawnBody(animal);}
        if("surfaces".equals(only)){ultimateSurfaces();return;}
        if("foliage".equals(only)){ultimateFoliage();return;}
        if("fire".equals(only)){ultimateFire();return;}
        if("hud".equals(only)){ultimateHud();return;}
        if ("motion".equals(only)) {
            place(110.5f,106.5f,0);
            var rabbit=g.animals.display(rpg.animal.Species.RABBIT,false,false,0,40.5f,34.5f,0);
            frames(12);
            film("rabbit-hop",72,i->g.animals.puppetTo(rabbit,rabbit.x,rabbit.z-4,true),a->new float[]{a.x+2.3f,a.y+0.7f,a.z-2.3f,a.x,a.y+0.3f,a.z},12,rabbit);
            var horse=g.animals.display(rpg.animal.Species.HORSE,false,false,0,40.5f,34.5f,0);
            frames(12);
            film("horse-attack",72,i->{if(i==3)g.combat.attack(horse,false,Actor.Combat.Move.BITE);},a->new float[]{a.x+3.6f,a.y+1.8f,a.z-3.6f,a.x,a.y+1.1f,a.z},0,horse);
            return;
        }
        var settings=new rpg.ui.SettingsPanel(new Preferences(),p->gfx.menuPreferences=p,false);settings.show();
        var preview=new Gfx.Preview();preview.world=rpg.world.TitleStage.build(game.engine());preview.dungeon=true;preview.x=8.5f;preview.y=DevMap.FLOOR;preview.z=8.5f;
        // Reproduce the consumed click that opened Settings, then exercise next frame's input path.
        ui.consumed=true;Main.mouse(ui,new astratto.platform.Input());ui.mx=1000;ui.my=232;ui.click=true;
        gfx.title(preview,Main.defaultLook(),win.width,win.height,1f/60,g.time);ui.begin(gfx.cmd);settings.draw(ui,win.width,win.height);ui.end();gfx.frame(win.width,win.height);
        if(settings.values.vsync)throw new IllegalStateException("Title settings failed to accept the next frame's click");
        ui.click=false;
        for(int i=0;i<4;i++){gfx.title(preview,Main.defaultLook(),win.width,win.height,1f/60,g.time);ui.begin(gfx.cmd);settings.draw(ui,win.width,win.height);ui.end();gfx.frame(win.width,win.height);}
        Main.shot(gfx,win.width,win.height,new File(shots,"settings-clicked-vsync-off.png"));settings.escape();
    }

    private void ultimateSurfaces() {
        place(40.5f,34.5f,(float)Math.PI);g.player.eq.clear();frames(12);
        List<BufferedImage> tiles=new ArrayList<>();List<String> labels=new ArrayList<>();
        tiles.add(capture(front(g.player,1.8f)));labels.add("Human: intact");
        g.player.hp=g.player.maxHp()*0.3f;
        g.player.injuries.cut(g.player,g.player.x,g.player.y+1.1f,g.player.z+g.player.radius,7);
        for(int i=0;i<180;i++){frames(1);if(gfx.figureReady(g.player))break;}
        tiles.add(capture(front(g.player,1.8f)));labels.add("Human: recessed cut");
        g.player.restore();frames(10);tiles.add(capture(front(g.player,1.8f)));labels.add("Human: healed");
        g.player.charred=0.8f;tiles.add(capture(front(g.player,1.8f)));labels.add("Human: charred cloth/skin");g.player.charred=0;
        place(110.5f,106.5f,0);
        var horse=g.animals.display(rpg.animal.Species.HORSE,false,false,0,40.5f,34.5f,0);frames(8);
        float[] cam={horse.x+2.6f,horse.y+1.5f,horse.z-2.6f,horse.x,horse.y+0.9f,horse.z};
        tiles.add(capture(cam));labels.add("Horse: intact");horse.hp=horse.maxHp()*0.2f;
        horse.injuries.cut(horse,horse.x+horse.radius,horse.y+horse.height*0.6f,horse.z,8);frames(80);
        tiles.add(capture(cam));labels.add("Horse: carved surface");horse.restore();frames(12);
        tiles.add(capture(cam));labels.add("Horse: healed");horse.charred=0.8f;
        tiles.add(capture(cam));labels.add("Horse: charred");g.animals.remove(horse);
        sheet("injuries-and-char",tiles,labels,4);
        tiles.clear();labels.clear();
        long[] residents={Ids.FISHERMAN,Ids.MINER,Ids.CRAFTSMAN,Ids.KNIGHT,Ids.PREDATOR_HUNTER};
        for(long id:residents){var n=(rpg.ai.Npc)g.actor(id);g.ai.reload(n);g.teleport(n,40.5f,DevMap.FLOOR,34.5f);n.yaw=(float)Math.PI;n.nextThink=Double.MAX_VALUE;frames(8);tiles.add(capture(front(n,2.7f)));labels.add(n.name+": "+n.profession.title);g.ai.unload(n);}
        sheet("five-new-residents",tiles,labels,5);
        var net=g.objects.add(new rpg.objects.FishingNet(g.state.newEntityId(),g.player.id,0,89.5f,DevMap.FLOOR-0.1f,30.5f,g.state.minutes+4));
        view(91,DevMap.FLOOR+1,29,89.5f,DevMap.FLOOR,30.5f,"deployed-net");net.removed=true;
        var trap=g.objects.add(new rpg.objects.Trap(g.state.newEntityId(),g.player.id,40.5f,DevMap.FLOOR,34.5f));trap.contents.add(new rpg.item.ItemStack(rpg.item.Items.SNARE,1));
        view(41.3f,DevMap.FLOOR+0.6f,33.5f,40.5f,DevMap.FLOOR+0.08f,34.5f,"deployed-snare");trap.removed=true;
        var stone=g.objects.all().stream().filter(object->object instanceof rpg.objects.Container c && c.kind.equals("memorial")).map(object->(rpg.objects.Container)object).findFirst().orElseThrow();
        view(stone.x+1.4f,DevMap.FLOOR+1.2f,stone.z-2,stone.x,DevMap.FLOOR+0.4f,stone.z,"unfilled-gravestone");stone.buried=true;
        view(stone.x+1.4f,DevMap.FLOOR+1.2f,stone.z-2,stone.x,DevMap.FLOOR+0.4f,stone.z,"filled-gravestone");
    }
    /** The Bank of Gameworld: from the road, inside at the counter, and a vault open beside the inventory. */
    private void gamefixesBank() {
        g.state.minutes = 11 * 60; g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR); g.timeSystem.weather.snap();
        var teller = (rpg.ai.Npc) g.actor(Ids.TELLER);
        g.ai.reload(teller);
        g.teleport(teller, DevMap.BANK_TELLER[0], DevMap.FLOOR, DevMap.BANK_TELLER[1]);
        place(72f, 99.5f, 0); frames(60);
        view(76f, DevMap.FLOOR + 3.2f, 96.5f, 71.5f, DevMap.FLOOR + 2f, 104f, "bank-outside");
        float[] at = g.bank.customerSpot();
        place(at[0], at[1], (float) Math.PI);
        for (int i = 0; i < 120; i++) { teller.goal = rpg.ai.Brain.Goal.WORK; teller.nextThink = g.time + 1; frames(1); }
        view(at[0] - 2.5f, DevMap.FLOOR + 2.4f, DevMap.BANK_Z0 + 1.5f, at[0], DevMap.FLOOR + 1.2f, DevMap.BANK_TELLER[1], "bank-inside");
        g.bank.openAccount(g.player, 1);
        g.give(g.player, rpg.item.Items.COINS, 3000); g.bank.buyVault(g.player, 2);
        var v = g.bank.vaults(g.player.id).get(0);
        v.contents.add(new rpg.item.ItemStack(rpg.item.Items.get("ruby"), 1)); g.state.mint(rpg.item.Items.get("ruby"), 1);
        v.contents.add(new rpg.item.ItemStack(rpg.item.Items.COINS, 500)); g.state.mint(rpg.item.Items.COINS, 500);
        g.ui.openContainer(v); frames(10);
        reviewUi("bank-vault-window");
    }

    /** Picking things up: bare-handed, with a sword and shield (no hand free), and on the move. */
    private void gamefixesGrab() {
        g.state.minutes = 12 * 60; g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR); g.timeSystem.weather.snap();
        g.player.firstPerson = false;
        String[][] kits = {{null, null}, {"wooden_sword", "wooden_shield"}};
        for (String[] kit : kits) for (boolean walking : new boolean[]{false, true}) {
            place(64.5f, 64.5f, 0);
            g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, kit[0] == null ? null : new rpg.item.ItemStack(rpg.item.Items.get(kit[0]), 1));
            g.player.eq.set(rpg.item.ItemDef.Slot.OFF, kit[1] == null ? null : new rpg.item.ItemStack(rpg.item.Items.get(kit[1]), 1));
            frames(20);
            var item = g.objects.drop(new rpg.item.ItemStack(rpg.item.Items.BREAD, 1), g.player.x + 0.35f, DevMap.FLOOR + 0.05f, g.player.z - 0.7f);
            g.state.mint(rpg.item.Items.BREAD, 1);
            final var it2 = item;
            film("grab-" + (kit[0] == null ? "bare" : "sword-shield") + (walking ? "-walking" : ""), 42, i -> { g.controller.in.forward = walking ? 1 : 0; if (i == 3) it2.takeBy(g, g.player, 0, "hand"); }, a -> quarter(a, 2.6f), 0);
            g.controller.in.forward = 0; frames(20);
        }
    }

    /** A bow drawn in third person, seen from the front, the bow side, behind and the draw side: no arm through the body. */
    private void gamefixesBowPose() {
        g.state.minutes = 12 * 60; g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR); g.timeSystem.weather.snap();
        g.player.firstPerson = false;
        String[] kits = {"shortbow", "iron_crossbow"};
        for (String kit : kits) {
            place(64.5f, 64.5f, 0);
            g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(rpg.item.Items.get(kit), 1));
            g.player.eq.set(rpg.item.ItemDef.Slot.OFF, null);
            g.player.eq.set(rpg.item.ItemDef.Slot.AMMO, new rpg.item.ItemStack(rpg.item.Items.get(kit.equals("shortbow") ? "wooden_arrows" : "iron_bolts"), 20));
            g.controller.camPitch = 0; frames(20);
            List<BufferedImage> tiles = new ArrayList<>(); List<String> labels = new ArrayList<>();
            for (int i = 0; i < 70; i++) { if (kit.equals("shortbow")) g.controller.in.mainDown = true; else g.controller.in.offDown = true; frames(1); }
            Actor p = g.player;
            float[][] around = {{0, -3.2f}, {-3.2f, 0}, {0, 3.2f}, {3.2f, 0}, {-2.2f, -2.2f}, {2.2f, 2.2f}};
            String[] names = {"front", "left", "back", "right", "front-left", "back-right"};
            for (int i = 0; i < around.length; i++) {
                float[] cam = {p.x + around[i][0], p.y + 1.5f, p.z + around[i][1], p.x, p.y + 1.2f, p.z};
                g.controller.in.mainDown = kit.equals("shortbow"); g.controller.in.offDown = !kit.equals("shortbow");
                tiles.add(capture(cam)); labels.add(kit + " " + names[i]);
            }
            sheet("bowpose-" + kit, tiles, labels, 3);
            g.controller.in.mainDown = false; g.controller.in.offDown = false; frames(30);
        }
    }

    /** Points the camera so its centre ray passes through a world point (a few frames for the camera to settle). */
    private void aimAt(float x, float y, float z) {
        for (int k = 0; k < 6; k++) {
            float dx = x - g.controller.cx, dy = y - g.controller.cy, dz = z - g.controller.cz;
            g.controller.camYaw = (float) Math.atan2(-dx, -dz);
            g.controller.camPitch = (float) Math.atan2(dy, Math.hypot(dx, dz));
            frames(2);
        }
    }

    /** Draws and looses at a point: the guide while drawn, then the arrow's flight. */
    private void loose(String name, float x, float y, float z) {
        aimAt(x, y, z);
        for (int i = 0; i < 50; i++) { g.controller.in.mainDown = true; if (i == 20) aimAt(x, y, z); frames(1); }
        reviewUi(name + "-drawn");
        g.controller.in.mainDown = false; frames(90);
    }

    /** Arrows (gamefixes): the arc while drawn; one into the ground, one into a target, one off the brick wall, one into a dummy. */
    private void gamefixesArrows() {
        g.state.minutes = 12 * 60; g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR); g.timeSystem.weather.snap();
        place(24.5f, 59.5f, (float) (Math.PI / 2));
        g.player.firstPerson = false; g.player.skills.setLevel(rpg.skill.Skill.ARCHERY, 40);
        g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(rpg.item.Items.SHORTBOW, 1));
        g.player.eq.set(rpg.item.ItemDef.Slot.AMMO, new rpg.item.ItemStack(rpg.item.Items.get("wooden_arrows"), 20));
        Actor dummy = g.dummies.get(0); g.teleport(dummy, 16.5f, DevMap.FLOOR, 62.5f);
        frames(30);
        loose("arrow-ground", 18.5f, DevMap.FLOOR, 57.5f);
        loose("arrow-target", 7.6f, DevMap.FLOOR + 1.1f, 56.9f);
        loose("arrow-wall", 6.2f, DevMap.FLOOR + 2.4f, 59.5f);
        loose("arrow-dummy", dummy.x, DevMap.FLOOR + 1.2f, dummy.z);
        frames(200);
        int lying = 0;
        for (var o : g.objects.all()) if (o instanceof rpg.objects.GroundItem gi && !gi.removed && gi.dir != null) { lying++; System.out.printf("[arrows] %s at (%.2f, %.2f, %.2f) dir (%.2f, %.2f, %.2f) lodged %s%n", gi.stack.def.name, gi.x, gi.y, gi.z, gi.dir[0], gi.dir[1], gi.dir[2], gi.lodged); }
        System.out.println("[arrows] lying " + lying + ", in the dummy " + dummy.stuck.size() + ", quiver " + (g.player.eq.get(rpg.item.ItemDef.Slot.AMMO) == null ? 0 : g.player.eq.get(rpg.item.ItemDef.Slot.AMMO).amount));
        view(20.5f, DevMap.FLOOR + 2.2f, 54.5f, 12, DevMap.FLOOR + 0.8f, 59.5f, "arrows-where-they-lie");
        view(10.5f, DevMap.FLOOR + 1.6f, 58.5f, 7, DevMap.FLOOR + 1.0f, 57f, "arrows-target-close");
        view(dummy.x + 1.6f, DevMap.FLOOR + 1.5f, dummy.z - 1.2f, dummy.x, DevMap.FLOOR + 1.1f, dummy.z, "arrows-dummy-close");
    }

    /** First person, looking straight down: standing and crouched, still and walking. */
    private void gamefixesFirstPersonDown() {
        g.state.minutes=600;g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();
        place(64.5f,62.5f,0);g.player.firstPerson=true;frames(20);
        for(boolean crouch:new boolean[]{false,true})for(float pitch:new float[]{-1.0f,-1.25f,-1.45f}) {
            g.controller.in.sneak=crouch;g.controller.camPitch=pitch;frames(40);
            reviewUi("fp-down-"+(crouch?"crouched":"standing")+"-"+(int)(-pitch*100));
        }
        g.controller.in.sneak=true;g.controller.camPitch=-1.45f;g.controller.in.forward=1;frames(20);reviewUi("fp-down-crouched-walking");
        g.controller.in.forward=0;g.controller.in.sneak=false;
    }

    /** A dead dog picked up and walked off with: whole, then with a leg cut off before it was lifted. */
    private void gamefixesCarcass() {
        g.state.minutes=600;g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();
        for(int round=0;round<2;round++) {
            var dog=g.animals.all().stream().filter(a->(a.species==rpg.animal.Species.DOG || a.species==rpg.animal.Species.FOX) && a.alive && a.adult()).findFirst().orElseThrow();
            g.teleport(g.player,64.5f,DevMap.FLOOR,62.5f);g.controller.snapTo(0);
            dog.puppet=true;g.teleport(dog,64.5f,DevMap.FLOOR,61.4f);frames(10);
            g.combat.bleedOutNow(dog);frames(90);
            if(round==1){g.gore.severAnimal(dog,rpg.combat.Gore.Part.LEG_R,1,0,g.player);frames(60);}
            if(g.carrying.begin(g.player,dog).rejected()){frames(30);g.carrying.begin(g.player,dog);}
            frames(50);
            String tag=round==0?"whole":"severed-leg";
            film("carcass-carry-"+tag,72,i->g.controller.in.forward=i<48?1:0,a->quarter(a,-3.0f),0);
            g.controller.in.forward=0;
            film("carcass-held-"+tag,30,i->{},a->quarter(a,2.6f),0);
            g.carrying.drop(g.player,false);frames(60);
        }
    }

    /** The archery range's brick wall from 34 m, the eye drifting a few centimetres a frame: shots, and how much the wall changes between them. */
    private void gamefixesBricks() {
        g.state.minutes=600;g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();
        place(44,58,0);frames(60);
        java.awt.image.BufferedImage last=null;double total=0;
        for(int i=0;i<8;i++) {
            float ex=40+i*0.03f;
            view(ex,DevMap.FLOOR+1.7f,58.3f,6,DevMap.FLOOR+1.5f,58.5f,"bricks-far-"+i);
            try {
                var img=javax.imageio.ImageIO.read(new File(shots,"bricks-far-"+i+".png"));
                if(last!=null){double d=0;int n=0;int w=img.getWidth(),h=img.getHeight();
                    for(int y=h/3;y<h*2/3;y+=2)for(int x=w/3;x<w*2/3;x+=2){int a=img.getRGB(x,y),b=last.getRGB(x,y);d+=Math.abs(((a>>16)&255)-((b>>16)&255))+Math.abs(((a>>8)&255)-((b>>8)&255))+Math.abs((a&255)-(b&255));n++;}
                    total+=d/n;System.out.printf("[bricks] frame %d change %.2f%n",i,d/n);}
                last=img;
            } catch(java.io.IOException e){throw new RuntimeException(e);}
        }
        System.out.printf("[bricks] mean change %.2f%n",total/7);
    }

    /** The undertaker's hut door at the graveyard: shut, opened by the player from outside, walked through, from inside. */
    private void gamefixesDoors() {
        g.state.minutes=600;g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();
        rpg.objects.Door door=null;
        for(var d:g.objects.doors())for(int[] c:d.cells)if(c[0]==DevMap.UNDERTAKER_X0 && c[1]==96)door=d;
        float dx=door.x,dz=door.z;
        float[] out={dx-4.5f,DevMap.FLOOR+2.2f,dz-2.5f,dx,DevMap.FLOOR+1,dz}, in={dx+3.6f,DevMap.FLOOR+1.8f,dz+1.6f,dx,DevMap.FLOOR+1,dz};
        g.teleport(g.player,dx-1.6f,DevMap.FLOOR,dz);g.controller.snapTo((float)(-Math.PI/2));frames(30);
        view(out[0],out[1],out[2],out[3],out[4],out[5],"door-undertaker-shut-outside");
        view(in[0],in[1],in[2],in[3],in[4],in[5],"door-undertaker-shut-inside");
        var r=door.perform(g,g.player,"open",0);System.out.println("[door] open -> "+r);
        final var d0=door;
        film("door-undertaker-open",60,i->{},a->out,0);
        System.out.println("[door] angles "+java.util.Arrays.toString(d0.angle)+" open "+d0.open);
        film("door-undertaker-walk-in",150,i->{g.controller.in.forward=i<120?1:0;},a->in,0);
        g.controller.in.forward=0;
        System.out.println("[door] player at "+g.player.x+","+g.player.z+" angles "+java.util.Arrays.toString(d0.angle));
        view(in[0],in[1],in[2],in[3],in[4],in[5],"door-undertaker-inside-after");
    }

    /** A real field plant before anyone touches it, with the player standing in it (examining it), and after they step out. */
    private void gamefixesFoliage() {
        g.state.minutes=600;g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();
        place(40.5f,34.5f,0);frames(120);
        for(var kind:new rpg.world.Foliage.Kind[]{rpg.world.Foliage.Kind.TALL_GRASS,rpg.world.Foliage.Kind.FERN,rpg.world.Foliage.Kind.SHRUB}) {
            var plant=g.foliage.chunks.values().stream().flatMap(chunk->chunk.plants.stream()).filter(patch->!patch.removed && patch.kind==kind).findFirst().orElse(null);
            if(plant==null){System.out.println("[foliage] no "+kind);continue;}
            float[] cam={plant.x+1.6f,DevMap.FLOOR+1.2f,plant.z-1.6f,plant.x,DevMap.FLOOR+0.3f,plant.z};
            g.teleport(g.player,plant.x+4,DevMap.FLOOR,plant.z+4);frames(30);
            view(cam[0],cam[1],cam[2],cam[3],cam[4],cam[5],"foliage-"+kind+"-untouched");
            g.teleport(g.player,plant.x+0.05f,DevMap.FLOOR,plant.z+0.05f);g.interact.focus=plant;frames(40);
            view(cam[0],cam[1],cam[2],cam[3],cam[4],cam[5],"foliage-"+kind+"-standing-in");
            g.teleport(g.player,plant.x+4,DevMap.FLOOR,plant.z+4);g.interact.focus=null;frames(200);
            view(cam[0],cam[1],cam[2],cam[3],cam[4],cam[5],"foliage-"+kind+"-recovered");
        }
    }

    private void ultimateFoliage() {
        place(110.5f,106.5f,0);frames(8);
        List<BufferedImage> tiles=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(var kind:rpg.world.Foliage.Kind.values()) {
            boolean wall=kind==rpg.world.Foliage.Kind.IVY || kind==rpg.world.Foliage.Kind.MOSS;
            if(wall)for(int height=0;height<2;height++)g.setBlock(40,DevMap.FLOOR+height,35,Blocks.STONE);
            var boxes=rpg.render.FoliageModels.model(kind,37);reviewMesh=gfx.renderer().mesh(boxes.vertices(),boxes.count());
            reviewTransform.identity().translate(40.5f,DevMap.FLOOR,34.5f);
            float[] cam={41.8f,DevMap.FLOOR+1.25f,32.9f,40.5f,DevMap.FLOOR+0.5f,34.5f};
            tiles.add(capture(cam));labels.add(kind.name().replace('_',' '));
            gfx.renderer().release(astratto.bridge.AstrattoNative.RES_MESH,reviewMesh);reviewMesh=0;
            if(wall)for(int height=0;height<2;height++)g.setBlock(40,DevMap.FLOOR+height,35,Blocks.AIR);
        }
        sheet("ten-foliage-models",tiles,labels,5);
        place(40.5f,34.5f,0);frames(120);
        var plant=g.foliage.chunks.values().stream().flatMap(chunk->chunk.plants.stream()).filter(patch->!patch.removed && (patch.kind==rpg.world.Foliage.Kind.TALL_GRASS || patch.kind==rpg.world.Foliage.Kind.CLOVER) && patch.x>30 && patch.x<54 && patch.z>28 && patch.z<50).findFirst().orElseThrow();
        float[] camera={plant.x+2.2f,DevMap.FLOOR+1.3f,plant.z-2.2f,plant.x,DevMap.FLOOR+0.2f,plant.z};
        g.teleport(g.player,110.5f,DevMap.FLOOR,106.5f);frames(2);view(camera[0],camera[1],camera[2],camera[3],camera[4],camera[5],"procedural-foliage");
        g.timeSystem.weather.set(astratto.weather.Weather.Kind.RAIN);g.timeSystem.weather.snap();
        for(int frame=0;frame<90;frame++){game.tick(1f/60);draw(camera,1f/60);}
        System.out.println("[weather] rain="+g.timeSystem.weather.rain+" wetness="+g.timeSystem.weather.wetness);
        view(camera[0],camera[1],camera[2],camera[3],camera[4],camera[5],"wet-grass-and-rain");
    }
    private void ultimateFire() {
        place(50.5f,70.5f,0);g.player.skills.setLevel(rpg.skill.Skill.HEALTH,100);g.player.restore();
        var guard=(rpg.ai.Npc)g.actor(Ids.GUARD);g.ai.reload(guard);g.teleport(guard,60.5f,DevMap.FLOOR,70.5f);guard.skills.setLevel(rpg.skill.Skill.HEALTH,100);guard.restore();guard.nextThink=Double.MAX_VALUE;frames(10);
        guard.goal=rpg.ai.Brain.Goal.RETURN;
        g.chemistry.emit(null,astratto.bridge.AstrattoNative.CHEM_FIRE,guard.x,guard.chestY(),guard.z,0.6f,4,0.2f);
        film("attached-fire",72,i->{},a->quarter(a,3.3f),0,guard);guard.goal=rpg.ai.Brain.Goal.IDLE;frames(120);
        film("stop-drop-roll",144,i->{},a->new float[]{a.x+2.5f,DevMap.FLOOR+1.6f,a.z-2.5f,a.x,DevMap.FLOOR+0.3f,a.z},0,guard);
        film("ground-roll",144,i->{},a->new float[]{a.x+2.5f,DevMap.FLOOR+1.6f,a.z-2.5f,a.x,DevMap.FLOOR+0.3f,a.z},0,guard);
        frames(100);view(guard.x+2.5f,DevMap.FLOOR+1.7f,guard.z-2.5f,guard.x,DevMap.FLOOR+0.6f,guard.z,"smothered-fire");
    }
    private void ultimateHud() {
        place(60.5f,70.5f,(float)Math.PI);frames(10);
        g.chat.say("A long Worldbox report: belongings held by the Undertaker exclude stolen goods. Confiscated property returns to its rightful owner. Your remaining possessions are protected. This message should wrap cleanly across multiple lines without losing its last words.");
        reviewUi("worldbox-long-text");
        var cole=(rpg.ai.Npc)g.actor(Ids.GUARD);g.ai.reload(cole);g.teleport(cole,60.5f,DevMap.FLOOR,72);cole.nextThink=Double.MAX_VALUE;
        hud.dialog(g.dialogue.talk(cole));reviewUi("chatbox-root");
        hud.choose(0);reviewUi("chatbox-response");hud.continueDialog();reviewUi("chatbox-return-options");hud.closeAll();g.ai.unload(cole);
        g.player.firstPerson=true;g.controller.camPitch=-1.35f;g.controller.jogging=false;
        film("firstperson-down-sprint",48,i->{g.controller.in.forward=1;g.controller.in.sprint=true;g.controller.camPitch=-1.35f;},a->null,12);
        g.controller.in.forward=0;g.controller.in.sprint=false;
    }

    /** Release review through the same HUD, articulated bodies and GPU passes used during play. */
    private void releaseSystemsShots() {
        String only=System.getenv("RPG_REVIEW_ONLY");
        if(only==null || only.equals("ui")) {
            place(64.5f,62.5f,0);frames(8);
            g.honor.changedAt=g.time;g.honor.lastDelta=3;g.honor.lastWhy="Helped a neighbor";g.state.commit("HonorChanged",g.player.id,0,null,0,"Helped a neighbor");
            g.state.commit("QuestStarted",g.player.id,0,null,0,"Courier");
            g.state.commit("LevelUp",g.player.id,0,null,0,"AGILITY 4->5");frames(24);reviewUi("quest-and-honor");
            for(int i=0;i<270;i++) { game.tick(1f/60); if(i%6==0)draw(null,0.1f); }
            reviewUi("level-notice");
            var cole=(rpg.ai.Npc)g.actor(Ids.GUARD);g.teleport(cole,g.player.x+2,g.player.y,g.player.z);cole.nextThink=Double.MAX_VALUE;
            cole.goal=rpg.ai.Brain.Goal.WATCH;
            var dialog=new rpg.ui.Dialog(cole.name,"Keep your eyes open. If you see trouble, come and find the watch.").choice("I'll keep watch.",hud::closeAll);dialog.npc=cole.id;hud.dialog(dialog);
            for(int i=0;i<36;i++) { game.tick(1f/60);draw(null,1f/60);if(i%3==0)Main.shot(gfx,win.width,win.height,new File(shots,"chatbox-f"+(i+1)+".png")); }
            hud.closeAll();
            var settings=new rpg.ui.SettingsPanel(new Preferences(),p->gfx.menuPreferences=p,false);settings.show();
            var preview=new Gfx.Preview();preview.world=rpg.world.TitleStage.build(game.engine());preview.dungeon=true;preview.x=8.5f;preview.y=DevMap.FLOOR;preview.z=8.5f;
            for(int i=0;i<8;i++) { gfx.title(preview,Main.defaultLook(),win.width,win.height,1f/60,g.time);ui.begin(gfx.cmd);settings.draw(ui,win.width,win.height);ui.end();gfx.frame(win.width,win.height); }
            Main.shot(gfx,win.width,win.height,new File(shots,"title-settings.png"));settings.escape();
        }
        if(only==null || only.equals("water") || only.equals("final")) {
            place(96.5f,43f,0);g.state.minutes=720;game.tick(0);
            g.timeSystem.weather.set(astratto.weather.Weather.Kind.RAIN);g.timeSystem.weather.snap();
            g.chemistry.splash(96,4,35);
            float[] cam={97,7,44,96,3.8f,34};
            for(int i=0;i<90;i++) { game.tick(1f/60);draw(cam,1f/60);if(i==30 || i==75)Main.shot(gfx,win.width,win.height,new File(shots,"water-rain-f"+(i+1)+".png")); }
            g.objects.add(new rpg.objects.Fire(g.state.newEntityId(),91.5f,DevMap.FLOOR,21.5f,g.state.minutes+1000,g.player.id));
            g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();
            cam=new float[]{90,6,26,91.5f,5,21.5f};
            for(int i=0;i<60;i++) { game.tick(1f/60);draw(cam,1f/60); }
            Main.shot(gfx,win.width,win.height,new File(shots,"chemical-fire.png"));
        }
        if(only==null || only.equals("carry") || only.equals("final")) {
            var dead=(rpg.ai.Npc)g.actor(Ids.BANDIT_1);g.ai.spawnBody(dead);g.teleport(dead,64.5f,DevMap.FLOOR,70);
            place(60.5f,73.5f,0);gfx.preload(java.util.List.of(dead));dead.alive=false;dead.hp=0;g.onKilled(dead,g.player,0);frames(60);
            var corpse=(rpg.objects.Corpse)g.objects.get(dead.corpse);place(corpse.x,corpse.z+1.2f,0);frames(3);
            if(g.carrying.begin(g.player,corpse).rejected())throw new IllegalStateException("Cannot pick up review body");
            film("body-pickup",48,i->{},a->quarter(a,-3.2f),0);
            g.controller.camPitch=0.25f;
            for(int i=0;i<27;i++) { g.controller.in.mainDown=true;step(); }
            if(g.player.cs.charge<=0 || !g.carrying.ready(g.player))throw new IllegalStateException("Body throw preview has no charged carry");
            reviewUi("body-throw-guide");
            film("body-throw",60,i->g.controller.in.mainDown=i<3,a->side(a,-4),0);
        }
        if(only==null || only.equals("gore") || only.equals("final"))goreShots();
        if("final".equals(only) || "gear".equals(only)) {
            place(64.5f,66.5f,0);g.player.firstPerson=true;
            g.player.eq.set(rpg.item.ItemDef.Slot.OFF,new rpg.item.ItemStack(rpg.item.Items.get("iron_shield"),1));
            g.controller.in.offDown=false;frames(8);reviewUi("shield-rest-firstperson");
            g.controller.in.offDown=true;frames(18);reviewUi("shield-firstperson");
        }
    }

    private void systemPolishShots() {
        String only = System.getenv("RPG_REVIEW_ONLY");
        if ("extras".equals(only)) systemExtrasShots();
        if ("guard".equals(only) || "extras".equals(only)) {
            place(64.5f, 62.5f, 0); g.player.eq.clear(); g.player.firstPerson = true;
            g.controller.in.offDown = true; frames(30); reviewUi("bare-guard-firstperson"); return;
        }
        if (only == null || only.equals("ui")) {
            place(64.5f, 62.5f, 0); frames(10);
            hud.settings.show(); reviewUi("settings-display");
            // Actual tab buttons, hit by the same UI mouse path used during play.
            for (int tab = 1; tab < 4; tab++) {
                ui.mx = win.width / 2f - 460 + 28 + tab * 216 + 80; ui.my = (win.height - 650) / 2f + 100; ui.click = true;
                Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1f / 60, true); ui.click = false;
                reviewUi("settings-" + new String[]{"display", "audio", "controls", "accessibility"}[tab]);
            }
            hud.settings.escape();
            var cole = (rpg.ai.Npc) g.actor(Ids.GUARD); g.teleport(cole, g.player.x + 3, g.player.y, g.player.z);
            hud.dialog(new rpg.ui.Dialog(cole.name, "Ask your question. The watch keeps its own accounts.").choice("Tell me more.", () -> hud.dialog(new rpg.ui.Dialog(cole.name, "Once a bounty is paid, the case is closed. People may remember the quarrel, but the watch will leave you be."))));
            hud.dialog.npc = cole.id; hud.choose(0); reviewUi("chatbox-followup");
            for (int frame = 0; frame < 39; frame++) { Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1f / 60, false); if (frame % 3 == 0) Main.shot(gfx, win.width, win.height, new File(shots, "followup-f" + (frame + 1) + ".png")); }
            hud.closeAll();
            g.player.hp *= 0.6f; g.law.all().computeIfAbsent(DevMap.ALPHA, k -> new java.util.HashMap<>()).put(g.player.id, 150); g.state.commit("BountyChanged", cole.id, g.player.id, DevMap.ALPHA, 0, "review warrant"); reviewUi("wanted-layout"); g.law.all().clear();
            g.player.firstPerson = true; g.player.eq.clear(); g.controller.in.offDown = true; frames(12); reviewUi("bare-guard-firstperson");
            g.controller.in.offDown = false; g.player.firstPerson = false; hud.closeAll();
        }
        if (only == null || only.equals("menus")) menuLatencyShots();
        if (only == null || only.equals("motion")) motionPolishShots();
        if (only == null || only.equals("traversal")) {
            g.player.skills.setLevel(rpg.skill.Skill.AGILITY, 5); g.player.restore(); g.player.eq.clear();
            for (int x = 60; x <= 69; x++) for (int z = 55; z <= 65; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 6; y++) g.setBlock(x, y, z, Blocks.AIR);
            for (int x = 63; x <= 65; x++) for (int y = 0; y < 2; y++) g.setBlock(x, DevMap.FLOOR + y, 59, Blocks.BRICK);
            place(64.5f, 60.8f, 0); frames(4);
            film("ledge-traversal", 72, i -> { if (i == 0 && !g.traversal.begin(g.player, 0)) throw new IllegalStateException("No review ledge"); }, a -> new float[]{68, DevMap.FLOOR + 2.2f, 62.5f, 64.5f, DevMap.FLOOR + 1.5f, 59.7f}, 0);
        }
        if (only == null || only.equals("carry")) {
            var dead = (rpg.ai.Npc) g.actor(Ids.BANDIT_1); g.ai.spawnBody(dead); g.teleport(dead, 64.5f, DevMap.FLOOR, 61);
            place(60.5f, 58.5f, 0); gfx.preload(java.util.List.of(dead));
            dead.alive = false; dead.hp = 0; g.onKilled(dead, g.player, 0); frames(100);
            var corpse = (rpg.objects.Corpse) g.objects.get(dead.corpse);
            place(corpse.x, corpse.z + 1.2f, 0); frames(3);
            if (g.carrying.begin(g.player, corpse).rejected()) throw new IllegalStateException("Cannot pick up review body");
            film("body-pickup", 48, i -> {}, a -> quarter(a, -3.2f), 0);
            film("body-carry", 36, i -> g.controller.in.forward = 1, a -> quarter(a, -3.2f), 0);
            g.controller.in.forward = 0;
            film("body-throw", 48, i -> g.controller.in.mainDown = i < 24, a -> side(a, -3.5f), 0);
        }
        if (only == null || only.equals("loot")) lootPolishShots();
        if (only == null || only.equals("jail")) {
            g.law.arrest(g.player, DevMap.ALPHA, (rpg.ai.Npc) g.actor(Ids.GUARD)); frames(3); reviewUi("jail-before-sleep");
            hud.fadeThen(g.law::sleepThroughSentence);
            for (int frame = 0; frame < 75; frame++) { Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1f / 60, true); if (frame % 3 == 0) Main.shot(gfx, win.width, win.height, new File(shots, "jail-wake-f" + (frame + 1) + ".png")); game.pump(); }
            reviewUi("jail-awake-inside");
        }
    }

    private void systemExtrasShots() {
        var thief = (rpg.ai.Npc) g.actor(Ids.THIEF);
        var witness = (rpg.ai.Npc) g.actor(Ids.GUARD);
        var issuer = (rpg.ai.Npc) g.actor(Ids.LAWMAN_A);
        g.teleport(witness, issuer.x + 1, issuer.y, issuer.z);
        var known = new rpg.ai.Stealth.Aware(); known.x = 85; known.z = 70; known.seenAt = g.time;
        witness.aware.put(thief.id, known);
        var evidence = new rpg.ai.Memory("THEFT", thief.id, witness.id, DevMap.ALPHA, g.state.minutes, 1, "witnessed", 900009, 100, "stolen goods");
        witness.remember(evidence, 24);
        if (g.law.report(witness, evidence, issuer).rejected()) throw new IllegalStateException("Review report rejected");
        hud.dialog(g.bountyBoard.browse(DevMap.ALPHA)); reviewUi("bounty-board-chatbox"); hud.closeAll();
        if (g.bountyBoard.accept(thief, DevMap.ALPHA).rejected()) throw new IllegalStateException("Review warrant rejected");
        hud.go(Hud.Scene.INVENTORY); reviewUi("bounty-scroll-inventory");
        hud.go(Hud.Scene.MAP); reviewUi("bounty-last-known-map"); hud.closeAll();
        var cat = g.animals.display(rpg.animal.Species.CAT, false, false, 0, 64.5f, 62.5f, 0);
        cat.goal = rpg.animal.Animal.Goal.GROOM;
        g.teleport(g.player, 60, DevMap.FLOOR, 60);
        film("cat-grooming", 54, i -> {}, a -> new float[]{a.x + 1.4f, a.y + 0.7f, a.z - 1.4f, a.x, a.y + 0.35f, a.z}, 0, cat);
    }

    private void menuLatencyShots() {
        place(64.5f, 62.5f, 0); g.player.restore(); g.player.inv.clear();
        for (String id : new String[]{"bronze_axe", "iron_pickaxe", "shortbow", "iron_arrows", "trout", "logs", "stone_body", "steel_shield"}) g.player.store(new rpg.item.ItemStack(rpg.item.Items.get(id), 1));
        for (Hud.Scene scene : new Hud.Scene[]{Hud.Scene.INVENTORY, Hud.Scene.SKILLS, Hud.Scene.CHARACTER}) {
            hud.go(scene); double first = 0, worst = 0;
            for (int frame = 0; frame < 24; frame++) {
                var c = g.controller; gfx.camera(c.cx, c.cy, c.cz, c.fx, c.fy, c.fz, win.width, win.height, 0); gfx.scene(g, false, 0, g.state.hourOfDay());
                ui.begin(gfx.cmd); long started = System.nanoTime(); hud.draw(ui, win.width, win.height, true, 1f / 60); ui.end();
                double millis = (System.nanoTime() - started) / 1e6; if (frame == 0) first = millis; worst = Math.max(worst, millis);
                gfx.frame(win.width, win.height);
            }
            System.out.printf(java.util.Locale.ROOT, "[menu-cpu] %s first %.2f ms / worst of 24 %.2f ms%n", scene, first, worst);
            Main.shot(gfx, win.width, win.height, new File(shots, "menu-" + scene.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
        }
        hud.closeAll();
    }

    private void motionPolishShots() {
        place(64.5f, 62.5f, 0);
        g.player.restore();
        g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, null); g.player.eq.set(rpg.item.ItemDef.Slot.OFF, null);
        film("crouch-transition", 54, i -> g.controller.in.sneak = true, a -> side(a, -3.2f), 0);
        film("crouch-walk", 54, i -> { g.controller.in.sneak = true; g.controller.in.forward = 1; }, a -> side(a, -3.2f), 12);
        g.controller.in.sneak = false; g.controller.in.forward = 0;
        film("bare-guard", 36, i -> g.controller.in.offDown = true, a -> quarter(a, 2.8f), 0);
        g.controller.in.offDown = false;
    }

    private void lootPolishShots() {
        var thief = (rpg.ai.Npc) g.actor(Ids.THIEF);
        var dead = (rpg.ai.Npc) g.actor(Ids.BANDIT_1);
        g.ai.spawnBody(dead); g.teleport(dead, 64.5f, DevMap.FLOOR, 61.7f);
        g.teleport(g.player, 60.5f, DevMap.FLOOR, 56.5f);
        gfx.preload(java.util.List.of(thief, dead));
        dead.alive = false; dead.hp = 0; g.onKilled(dead, g.player, 0);
        frames(90);
        g.ai.spawnBody(thief); g.teleport(thief, 64.5f, DevMap.FLOOR, 62.5f);
        thief.goal = rpg.ai.Brain.Goal.LOOT; thief.goalSince = g.time; thief.nextThink = Double.MAX_VALUE;
        thief.lootStarted = g.time; thief.lootUntil = g.time + 0.9; thief.wantYaw = 0;
        film("corpse-looting", 54, i -> {}, a -> side(a, -3.2f), 0, thief);
        g.ai.unload(thief);
    }

    private void sessionPolishShots() {
        if ("loot".equals(System.getenv("RPG_REVIEW_ONLY"))) { lootPolishShots(); return; }
        if ("motion".equals(System.getenv("RPG_REVIEW_ONLY"))) { motionPolishShots(); return; }
        place(64.5f, 62.5f, 0); frames(12);
        g.player.skills.setLevel(rpg.skill.Skill.MAGIC, 25); g.player.skills.setLevel(rpg.skill.Skill.FAITH, 25); g.player.restore();
        g.player.hp *= 0.7f; g.player.stamina *= 0.6f; g.player.mana *= 0.45f; g.player.faith *= 0.8f;
        reviewUi("dynamic-vitals");
        var cole = (rpg.ai.Npc) g.actor(Ids.GUARD);
        g.teleport(cole, g.player.x + 3, DevMap.FLOOR, g.player.z);
        var dialog = new rpg.ui.Dialog(cole.name, "The streets are quiet again. A paid bounty settles the law, but people remember what they saw. Keep your hands clean, traveller.");
        dialog.npc = cole.id;
        dialog.choice("Tell me about this town.", () -> {});
        dialog.choice("How does the law work here?", () -> {});
        dialog.choice("Goodbye.", () -> {});
        hud.dialog(dialog); reviewUi("chatbox");
        for (int frame = 0; frame < 39; frame++) {
            Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1f / 60, false);
            if (frame % 3 == 0) Main.shot(gfx, win.width, win.height, new File(shots, "talk-f" + (frame + 1) + ".png"));
        }
        hud.closeAll();
        if ("ui".equals(System.getenv("RPG_REVIEW_ONLY"))) return;
        motionPolishShots();
        lootPolishShots();
        g.law.arrest(g.player, DevMap.ALPHA, cole); frames(4); reviewUi("jail-sentence");
        view(118.4f, DevMap.FLOOR + 1.7f, 59.8f, 117, DevMap.FLOOR + 0.8f, 54, "jail-cell");
        g.law.sleepThroughSentence();
        gearShots();
    }

    private void reviewUi(String name) {
        for (int i = 0; i < 12; i++) Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1f / 60, false);
        Main.shot(gfx, win.width, win.height, new File(shots, name + ".png"));
    }

    private void polishShots() {
        place(64.5f, 62.5f, (float) Math.PI);
        frames(20);
        hud.go(Hud.Scene.CHARACTER);
        for (int i = 0; i < 20; i++) Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false);
        Main.shot(gfx, win.width, win.height, new File(shots, "character.png"));
        hud.closeAll();
        g.player.firstPerson = true;
        for (String id : new String[]{"iron_sword", "bronze_axe", "oak_shortbow", "bronze_crossbow"}) {
            var item = rpg.item.Items.get(id);
            if (item == null) throw new IllegalArgumentException("review item missing: " + id);
            g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(item, 1));
            g.player.eq.set(rpg.item.ItemDef.Slot.OFF, null);
            for (float pitch : new float[]{-0.7f, 0, 0.7f}) {
                g.controller.camPitch = pitch;
                frames(20);
                Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false);
                Main.shot(gfx, win.width, win.height, new File(shots, id + "-" + pitch + ".png"));
            }
            if (item.family == rpg.item.ItemDef.Family.BOW) {
                g.controller.camPitch = 0;
                g.player.eq.set(rpg.item.ItemDef.Slot.AMMO, new rpg.item.ItemStack(rpg.item.Items.BRONZE_ARROWS, 10));
                g.controller.in.mainDown = true; frames(45);
                Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false);
                Main.shot(gfx, win.width, win.height, new File(shots, "bow-drawn.png"));
                g.controller.in.mainDown = false; frames(1);
            }
        }
    }

    /** Current polish review through the real UI, item and dismemberment render paths. */
    private void fieldPolishShots() {
        place(64.5f, 62.5f, 0); frames(10);
        hud.go(Hud.Scene.MAP);
        for (int i = 0; i < 20; i++) Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1f / 60, false);
        Main.shot(gfx, win.width, win.height, new File(shots, "world-map.png"));
        hud.closeAll();
        for (int i = 0; i < 20; i++) Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1f / 60, false);
        if (hud.alphaOf("honor") > 0.001f) throw new IllegalStateException("Honor is visible without a change");
        for (int delta : new int[]{5, -5}) {
            g.honor.event("Review", g.player.id, delta, null, false, delta > 0 ? "a good deed" : "a harmful deed");
            for (int i = 0; i < 20; i++) Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1f / 60, false);
            Main.shot(gfx, win.width, win.height, new File(shots, delta > 0 ? "honor-gain.png" : "honor-loss.png"));
        }
        for (String id : new String[]{"bronze_axe", "oak_shortbow", "bronze_crossbow"}) {
            var item = rpg.item.Items.get(id);
            g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(item, 1));
            g.player.eq.set(rpg.item.ItemDef.Slot.OFF, null);
            g.player.eq.set(rpg.item.ItemDef.Slot.AMMO, new rpg.item.ItemStack(rpg.item.Items.get(item.family == rpg.item.ItemDef.Family.CROSSBOW ? "bronze_bolts" : "bronze_arrows"), 10));
            for (int frame = 0; frame < 2; frame++) {
                g.player.firstPerson = frame == 1;
                g.player.cs.draw = item.family == rpg.item.ItemDef.Family.BOW ? 0.9f : 0;
                g.player.cs.aim = 1;
                g.controller.update(0);
                for (int i = 0; i < 20; i++) {
                    if (frame == 0) draw(quarter(g.player, 2.8f), 1f / 60);
                    else { g.controller.update(0); Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1f / 60, false); }
                }
                Main.shot(gfx, win.width, win.height, new File(shots, id + (frame == 0 ? "-third.png" : "-aim.png")));
            }
        }
        g.player.cs.cancel(); g.player.firstPerson = false;
        g.controller.update(0);
        g.combat.lastPlayerHit = g.time;
        for (int i = 0; i < 3; i++) Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1f / 60, false);
        Main.shot(gfx, win.width, win.height, new File(shots, "hitmarker-white.png"));
        g.combat.lastPlayerKill = g.time;
        for (int i = 0; i < 3; i++) Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1f / 60, false);
        Main.shot(gfx, win.width, win.height, new File(shots, "hitmarker-kill.png"));
        var npc = (rpg.ai.Npc) g.actor(Ids.BANDIT_1);
        g.ai.reload(npc); g.teleport(npc, 66.5f, DevMap.FLOOR, 62.5f);
        g.gore.sever(npc, rpg.combat.Gore.Part.HEAD, 0, 0, g.player);
        g.ai.unload(npc);
        game.tick(1f / 60);
        var limb = g.gore.limbs.get(g.gore.limbs.size() - 1);
        var rigid = g.lastRigids.body(limb.body);
        if (rigid != null) {
            float[] matrix = new float[16]; rigid.matrix(matrix);
            float x = matrix[12], y = matrix[13], z = matrix[14];
            for (int i = 0; i < 3; i++) draw(new float[]{x + 0.45f, y + 0.05f, z - 0.8f, x, y, z}, 1f / 60);
            Main.shot(gfx, win.width, win.height, new File(shots, "head-eyes-closed.png"));
            for (int i = 0; i < 3; i++) draw(new float[]{x + 0.2f, y - 0.5f, z - 0.4f, x, y - 0.05f, z}, 1f / 60);
            Main.shot(gfx, win.width, win.height, new File(shots, "head-cut-surface.png"));
        }
    }

    private void menuShots() {
        java.nio.file.Path root0 = Profiles.root;
        Profiles.root = java.nio.file.Path.of("build", "gallery-profiles");
        for (int s = 1; s <= Profiles.MAX; s++) Profiles.delete(s);
        Gfx.Preview stage = new Gfx.Preview();
        stage.world = g.game.world(); stage.x = 100.5f; stage.y = DevMap.FLOOR; stage.z = 79.5f;
        for (var n : g.npcs) g.ai.unload(n);
        gfx.findLamps(g.vox);
        for (int k = 0; k < 2; k++) {
            if (k == 1) {
                Profiles.create("Aldric", g.player.look);
                var a = rpg.actor.Appearance.random(42); var p = Profiles.create("Mira", a); p.playSeconds = 7400; p.combatLevel = 31; p.totalLevel = 212; p.lastPlayed = System.currentTimeMillis() - 3_600_000; Profiles.write(p);
                Profiles.create("Old Tom", rpg.actor.Appearance.random(7));
            }
            rpg.ui.TitleScreen t = new rpg.ui.TitleScreen(Main.VERSION);
            for (int i = 0; i < 70; i++) {
                game.tick(1 / 60f);
                gfx.title(stage, t.selected == null ? null : t.selected.look, win.width, win.height, 1 / 60f, g.time);
                ui.begin(gfx.cmd);
                ui.mx = 140; ui.my = 360;
                t.draw(ui, win.width, win.height, 1 / 60f, false, false);
                ui.end();
                gfx.frame(win.width, win.height);
            }
            Main.shot(gfx, win.width, win.height, new File(shots, "title-" + (k == 0 ? "empty" : "profiles") + ".png"));
            if (k == 1) for (int action = 0; action < 2; action++) {
                for (int i = 0; i < 3; i++) {
                    gfx.title(stage, t.selected.look, win.width, win.height, 1f / 60, g.time);
                    ui.begin(gfx.cmd);
                    ui.mx = action == 0 ? 350 : 180; ui.my = action == 0 ? 542 : 320; ui.click = i == 0;
                    t.draw(ui, win.width, win.height, 1f / 60, false, false);
                    ui.end(); gfx.frame(win.width, win.height);
                }
                Main.shot(gfx, win.width, win.height, new File(shots, action == 0 ? "profile-delete-confirm.png" : "profile-deleted.png"));
                ui.click = false;
            }
        }
        for (int s = 1; s <= Profiles.MAX; s++) Profiles.delete(s);
        Profiles.root = root0;
        // the console, open over the world, after a few commands
        rpg.ui.Hud hud = new rpg.ui.Hud(g, gfx);
        g.ui = hud;
        g.teleport(g.player, 96.5f, DevMap.FLOOR, 70.5f);
        hud.console.toggle();
        for (String c : new String[]{"give godly_2hsword 1", "setlevel attack 60", "find gravity", "where", "gravity"}) hud.console.run(c, true);
        hud.console.input("gi", false, false, false, false, false, 0);
        for (int i = 0; i < 30; i++) { game.pump(); Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false, true); }
        Main.shot(gfx, win.width, win.height, new File(shots, "console.png"));
        hud.console.close();
    }

    private void animalFall() {
        for (var n : g.npcs) g.ai.unload(n);
        g.teleport(g.player, 60, DevMap.FLOOR, 60);
        String[] kinds = {"WOLF", "BROWN_BEAR", "COW"};
        for (int k = 0; k < kinds.length; k++) {
            var sp = rpg.animal.Species.valueOf(kinds[k]);
            var an = g.animals.release(sp, false, false, 64.5f, 66.5f);
            an.yaw = (float) Math.PI / 2;
            for (int i = 0; i < 30; i++) { game.tick(1 / 60f); draw(new float[]{68.5f, DevMap.FLOOR + 1.6f, 70.5f, 64.5f, DevMap.FLOOR + 0.5f, 66.5f}, 1 / 60f); }
            boolean knock = sp == rpg.animal.Species.BROWN_BEAR;
            if (knock) g.animals.struck(an, -1, 0, 90, true);
            else { an.blowX = -1; an.blowZ = 0; an.blowImpact = 70; g.combat.bleedOutNow(an); }
            java.util.List<BufferedImage> tiles = new ArrayList<>();
            java.util.List<String> labels = new ArrayList<>();
            for (int i = 0; i < 60 * (knock ? 5 : 2); i++) {
                game.tick(1 / 60f);
                float[] cam = {an.x + 4, DevMap.FLOOR + 1.6f, an.z + 4, an.x, DevMap.FLOOR + 0.5f, an.z};
                if (i % (knock ? 30 : 12) == 0) { tiles.add(capture(cam)); labels.add(String.format("%.2fs %s", i / 60f, an.ragdoll != null ? "ragdoll" : "animated")); }
                else draw(cam, 1 / 60f);
            }
            sheet("fall-" + kinds[k].toLowerCase(), tiles, labels, 5);
            g.animals.remove(an);
        }
    }

    /** Still reviews of actual first-hand and relayed dialogue; the source account is the fixture. */
    private void rumorShots() {
        long[] ids = {Ids.VILLAGER, Ids.WORKER, Ids.CARTER};
        for (int i = 0; i < ids.length; i++) {
            var n = (rpg.ai.Npc) g.actor(ids[i]);
            g.ai.reload(n); g.teleport(n, 99.5f + i * 3, DevMap.FLOOR, 73.5f);
            n.nextThink = Double.MAX_VALUE; n.goal = rpg.ai.Brain.Goal.IDLE;
            n.yaw = n.wantYaw = (i == 0 ? -1 : 1) * (float) Math.PI / 2;
        }
        place(102.5f, 81.5f, 0); g.controller.camPitch = -0.12f;
        for (int i = 0; i < 20; i++) { game.tick(1 / 60f); Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false); }
        var eda = (rpg.ai.Npc) g.actor(ids[0]);
        var dag = (rpg.ai.Npc) g.actor(ids[1]);
        var account = new rpg.ai.Memory("MURDER", g.player.id, Ids.BANDIT_1, DevMap.ALPHA,
                g.state.minutes, 1, "witnessed", 0, 0, "gallery witness account");
        account.reported = true; eda.remember(account, 24); eda.knowsPlayer = true;
        for (int step = 0; step < 2; step++) {
            var from = (rpg.ai.Npc) g.actor(ids[step]);
            var to = (rpg.ai.Npc) g.actor(ids[step + 1]);
            for (long id : ids) ((rpg.ai.Npc) g.actor(id)).speech = null;
            if (step == 1) dag.yaw = -(float) Math.PI / 2;
            if (!g.ai.rumors.tell(from, to, from.memories.get(from.memories.size() - 1), false))
                throw new IllegalStateException("review account did not reach its listener");
            // Hold simulation still while the interface settles, so the two stages can be read separately.
            for (int i = 0; i < 15; i++) Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false);
            Main.shot(gfx, win.width, win.height, new File(shots, step == 0 ? "rumor-witness.png" : "rumor-relay.png"));
        }
    }

    private void moodShots() {
        rpg.ui.Hud hud = new rpg.ui.Hud(g, gfx);
        g.ui = hud;
        g.teleport(g.player, 100.5f, DevMap.FLOOR, 80.5f);
        g.controller.snapTo(0);
        g.controller.camPitch = -0.12f;
        long[] ids = {Ids.MERCHANT_A, Ids.GUARD, Ids.VILLAGER, Ids.WORKER, Ids.CARTER, Ids.FARMER};
        float[][] at = {{96.5f, 72.5f}, {99.5f, 71.5f}, {102.5f, 72.5f}, {104.5f, 73.0f}, {97.5f, 75.5f}, {106.5f, 75.5f}};
        for (int i = 0; i < ids.length; i++) { var n = (rpg.ai.Npc) g.actor(ids[i]); g.ai.reload(n); g.teleport(n, at[i][0], DevMap.FLOOR, at[i][1]); n.yaw = (float) Math.PI; }
        for (int f = 0; f < 90; f++) {
            var ada = (rpg.ai.Npc) g.actor(Ids.MERCHANT_A); ada.goal = rpg.ai.Brain.Goal.HIDE; ada.fearOf = g.player.id; ada.nextThink = g.time + 9;
            var cole = (rpg.ai.Npc) g.actor(Ids.GUARD); cole.goal = rpg.ai.Brain.Goal.WATCH; cole.nextThink = g.time + 9;
            var eda = (rpg.ai.Npc) g.actor(Ids.VILLAGER); eda.goal = rpg.ai.Brain.Goal.INVESTIGATE; eda.nextThink = g.time + 9;
            if (eda.investigation == null) { eda.investigation = new rpg.ai.Investigation(0, 103, 70, rpg.ai.Investigation.Stimulus.SOUND, g.time - 6); eda.investigation.plan = rpg.ai.Investigation.Plan.PEEK; }
            var dag = (rpg.ai.Npc) g.actor(Ids.WORKER); dag.goal = rpg.ai.Brain.Goal.IDLE; dag.gossipUntil = g.time + 5; dag.nextThink = g.time + 9;
            var ivo = (rpg.ai.Npc) g.actor(Ids.CARTER); ivo.goal = rpg.ai.Brain.Goal.REPORT_CRIME; ivo.nextThink = g.time + 9;
            var mara = (rpg.ai.Npc) g.actor(Ids.FARMER); mara.goal = rpg.ai.Brain.Goal.FLEE; mara.nextThink = g.time + 9;
            mara.knowsPlayer = true; ivo.knowsPlayer = true;
            for (long id : ids) { var n = (rpg.ai.Npc) g.actor(id); if (id != Ids.WORKER) n.speech = null; else g.ai.say(n, "Did you hear? Someone robbed Ada."); }
            game.tick(1 / 60f);
            Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false);
        }
        Main.shot(gfx, win.width, win.height, new File(shots, "moods.png"));
    }

    private void deathShots() {
        rpg.ui.Hud hud = new rpg.ui.Hud(g, gfx);
        g.ui = hud;
        g.death.manual = true;
        var bandit = (rpg.ai.Npc) g.actor(Ids.BANDIT_1);
        g.ai.reload(bandit);
        g.teleport(g.player, 101.5f, DevMap.FLOOR, 73.5f);
        g.controller.snapTo((float) Math.PI / 2);
        g.teleport(bandit, 99.6f, DevMap.FLOOR, 73.5f);
        bandit.yaw = (float) -Math.PI / 2;
        g.give(g.player, rpg.item.Items.get("iron_sword"), 1);
        for (int i = 0; i < 40; i++) { game.tick(1 / 60f); Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false); }
        g.player.hp = 1;
        g.combat.resolve(bandit, g.player, 50, 0, rpg.item.ItemDef.DamageType.SLASH, null, g.player.x, g.player.y + 1.2f, g.player.z, "gallery", false);
        double[] at = {0.3, 1.2, 2.4, 3.6, 5.6};
        int k = 0;
        for (int i = 0; i < 60 * 12 && k < at.length; i++) {
            float dt = 1 / 60f * g.death.timeScale();
            game.tick(dt);
            Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, g.death.choosing());
            if (g.death.elapsed() >= at[k]) { Main.shot(gfx, win.width, win.height, new File(shots, "death-seq-" + k + ".png")); k++; }
        }
        g.death.proceed(false);
        for (int i = 0; i < 90; i++) { game.tick(1 / 60f); Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false); }
        Main.shot(gfx, win.width, win.height, new File(shots, "death-grave-map.png"));
        // the grave itself, seen from beside it
        var gr = g.undertaker.graves().isEmpty() ? null : g.undertaker.graves().get(0);
        if (gr != null) { g.teleport(g.player, 60, DevMap.FLOOR, 60); adapted(new float[]{gr.x + 1.6f, DevMap.FLOOR + 1.2f, gr.z - 1.4f, gr.x, DevMap.FLOOR + 0.3f, gr.z + 0.3f}, "death-grave"); }
        // and the graveyard where they woke
        g.teleport(g.player, DevMap.GRAVEYARD_SPAWN[0] - 3, DevMap.FLOOR, DevMap.GRAVEYARD_SPAWN[1] - 3);
        g.controller.camYaw = (float) Math.atan2(-(118 - g.player.x), -(94 - g.player.z)); g.controller.camPitch = -0.25f;
        for (int i = 0; i < 90; i++) { game.tick(1 / 60f); Main.drawGame(g, gfx, ui, hud, win.width, win.height, 1 / 60f, false); }
        Main.shot(gfx, win.width, win.height, new File(shots, "graveyard.png"));
    }

    private void creatorShots() {
        rpg.ui.Creator c = new rpg.ui.Creator();
        Gfx.Preview preview = new Gfx.Preview();
        preview.world = g.game.world(); preview.x = 98.5f; preview.y = DevMap.FLOOR; preview.z = 77.5f;
        for (var n : g.npcs) g.ai.unload(n);
        g.teleport(g.player, 30, DevMap.FLOOR, 30);
        String[] names = {"male", "female", "heavy", "skinny-female", "random1", "random2", "tab-hair", "tab-clothing", "tab-finish"};
        for (int k = 0; k < names.length; k++) {
            switch (k) {
                case 0 -> { }
                case 1 -> { c.look.female = true; c.look.beard = 0; c.look.hair = 13; }
                case 2 -> { c.look.female = false; c.look.bodyType = 1; c.look.beard = 5; }
                case 3 -> { c.look.female = true; c.look.bodyType = 2; c.look.beard = 0; c.look.hair = 15; }
                case 4, 5 -> c.randomise();
                case 6 -> c.tab = 1;
                case 7 -> { c.tab = 2; c.garment = 2; }
                default -> c.tab = 3;
            }
            for (int i = 0; i < 40; i++) {
                game.tick(1 / 60f);
                c.spin(1 / 60f);
                preview.focus = c.focus; preview.aim = c.aim; preview.zoom = c.zoom;
                gfx.creator(preview, c.look, c.turn, win.width, win.height, 1 / 60f, g.time);
                ui.begin(gfx.cmd);
                c.draw(ui, win.width, win.height, java.util.List.of(), false, false);
                ui.end();
                gfx.frame(win.width, win.height);
            }
            Main.shot(gfx, win.width, win.height, new File(shots, "creator-" + names[k] + ".png"));
        }
    }

    /** Each sex and build turned to four angles in the creator's light, for judging the body itself (RPG_FIGURE_TOP etc. pick the clothes). */
    private void figureShots() {
        Gfx.Preview preview = new Gfx.Preview();
        preview.world = g.game.world(); preview.x = 98.5f; preview.y = DevMap.FLOOR; preview.z = 77.5f;
        for (var n : g.npcs) g.ai.unload(n);
        g.teleport(g.player, 30, DevMap.FLOOR, 30);
        Appearance look = new Appearance();
        look.top = Integer.parseInt(System.getenv().getOrDefault("RPG_FIGURE_TOP", "1"));
        look.bottom = Integer.parseInt(System.getenv().getOrDefault("RPG_FIGURE_LEGS", "1"));
        look.hair = Integer.parseInt(System.getenv().getOrDefault("RPG_FIGURE_HAIR", "2"));
        float zoom = Float.parseFloat(System.getenv().getOrDefault("RPG_FIGURE_ZOOM", "0.35"));
        float[] turns = {0, 0.75f, 1.5708f, 3.1416f};
        Gfx.reviewShut = System.getenv("RPG_FIGURE_SHUT") != null;
        float focus = Float.parseFloat(System.getenv().getOrDefault("RPG_FIGURE_FOCUS", "0"));
        for (int sex = 0; sex < 2; sex++) for (int type = 0; type < 3; type++) for (int a = 0; a < turns.length; a++) {
            look.female = sex == 1; look.bodyType = type; look.beard = 0;
            for (int i = 0; i < 30; i++) {
                game.tick(1 / 60f);
                preview.focus = focus; preview.aim = focus; preview.zoom = zoom;
                gfx.creator(preview, look, turns[a], win.width, win.height, 1 / 60f, g.time);
                gfx.frame(win.width, win.height);
            }
            Main.shot(gfx, win.width, win.height, new File(shots, "figure-" + (sex == 1 ? "f" : "m") + type + "-" + a + ".png"));
        }
    }

    /** A killing cut on a bandit in the square: the severed arm, the body, the blood on the ground. */
    private void goreShots() {
        var bandit = (rpg.ai.Npc) g.actor(Ids.BANDIT_1);
        if(!bandit.alive)g.ai.revive(bandit,true);
        g.ai.reload(bandit);
        g.teleport(bandit, 102.5f, DevMap.FLOOR, 74.5f);
        bandit.yaw = 0; bandit.hostileToPlayer = false;bandit.nextThink=Double.MAX_VALUE;
        place(102.5f, 77f, 0);
        frames(5);
        float rx = (float) Math.cos(bandit.yaw), rz = (float) -Math.sin(bandit.yaw);
        g.combat.resolve(g.player, bandit, bandit.maxHp() * 3, 0, rpg.item.ItemDef.DamageType.SLASH, null,
                bandit.x + rx * bandit.radius, bandit.y + bandit.height * 0.66f, bandit.z + rz * bandit.radius, "gallery cut", false);
        float[] cam = {104.5f, DevMap.FLOOR + 2.4f, 78.5f, 102.5f, DevMap.FLOOR + 0.3f, 74.5f};
        for (int i = 0; i < 20; i++) { game.tick(1 / 60f); draw(cam, 1 / 60f); }
        Main.shot(gfx, win.width, win.height, new File(shots, "gore-cut.png"));
        for (int i = 0; i < 120; i++) { game.tick(1 / 60f); draw(cam, 1 / 60f); }
        Main.shot(gfx, win.width, win.height, new File(shots, "gore-after.png"));
    }

    /** Actual killing blows, both from animation and during a physical reaction, filmed on threes. */
    private void npcDeathShots() {
        var bandit = (rpg.ai.Npc) g.actor(Ids.BANDIT_1);
        for (boolean reacting : new boolean[]{false, true}) {
            g.reactions.cancel(bandit);
            g.ai.revive(bandit, true);
            g.teleport(bandit, 102.5f, DevMap.FLOOR, 74.5f);
            bandit.yaw = 0; bandit.hostileToPlayer = false; bandit.nextThink = Double.MAX_VALUE;
            place(102.5f, 80f, 0);
            float[] cam = {106.8f, DevMap.FLOOR + 1.5f, 74f, 102.5f, DevMap.FLOOR + 0.7f, 74.5f};
            for (int i = 0; i < 20; i++) { game.tick(1 / 60f); draw(cam, 1 / 60f); }
            if (reacting) {
                g.reactions.struck(bandit, 0, 0.1f, 1, 160, 60, bandit.x, bandit.y + 1.3f, bandit.z);
                for (int i = 0; i < 24; i++) { game.tick(1 / 60f); draw(cam, 1 / 60f); }
            }
            g.combat.resolve(g.player, bandit, bandit.maxHp() * 3, 0, rpg.item.ItemDef.DamageType.BLUNT,
                    null, bandit.x, bandit.y + 1, bandit.z, "death review", false);
            List<BufferedImage> tiles = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int f = 0; f < 72; f++) {
                game.tick(1 / 60f);
                var rd = g.lastBodies.ragdoll(bandit.ragdoll);
                if (rd == null) throw new IllegalStateException("death review lost its ragdoll");
                cam[0] = rd.comX + 4.3f; cam[2] = rd.comZ - 0.5f;
                cam[3] = rd.comX; cam[5] = rd.comZ;
                draw(cam, 1 / 60f);
                if (f % 3 == 0) {
                    tiles.add(capture(cam));
                    labels.add(String.format("f%d %.2fs state %d/%d", f + 1, f / 60f, rd.state, rd.outcome));
                }
            }
            // Observe the same body at rest without rendering hundreds of redundant intermediate frames.
            for (int f = 0; f < 600; f++) game.tick(1 / 60f);
            tiles.add(capture(cam)); labels.add("after 11.2s");
            sheet(reacting ? "npc-death-reacting" : "npc-death-standing", tiles, labels, 8);
        }
    }

    /**
     * Sydney in the game: a bandit struck from the front by blows of three strengths - one he takes standing, one that
     * staggers him back, one that puts him down - filmed every sixth of a second, side on. Then the sheet of each.
     */
    private void reactionShots() {
        var bandit = (rpg.ai.Npc) g.actor(Ids.BANDIT_1);
        float[] impulses = {35, 65, 160};
        String[] names = {"reaction-light", "reaction-stagger", "reaction-down"};
        for (int k = 0; k < impulses.length; k++) {
            g.reactions.cancel(bandit);
            g.ai.reload(bandit);
            g.teleport(bandit, 102.5f, DevMap.FLOOR, 74.5f);
            bandit.yaw = 0; bandit.hostileToPlayer = false; bandit.alive = true; bandit.hp = bandit.maxHp();
            place(102.5f, 80f, 0);
            float[] cam = {106.8f, DevMap.FLOOR + 1.6f, 74.0f, 102.5f, DevMap.FLOOR + 0.9f, 74.5f};
            for (int i = 0; i < 30; i++) { game.tick(1 / 60f); draw(cam, 1 / 60f); }
            // pushed from the front (toward +z, his back), at the chest
            g.reactions.struck(bandit, 0, 0.1f, 1, impulses[k], impulses[k] * 0.3f, bandit.x, bandit.y + 1.3f, bandit.z - 0.1f);
            List<BufferedImage> tiles = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int f = 0; f < 60 * 8; f++) {
                game.tick(1 / 60f);
                // the camera keeps him in frame, side on, wherever the blow sends him
                Snapshots.Ragdoll at = bandit.ragdoll != 0 ? g.lastBodies.ragdoll(bandit.ragdoll) : null;
                float bx = at != null ? at.comX : bandit.x, bz = at != null ? at.comZ : bandit.z;
                cam[0] += (bx + 4.3f - cam[0]) * 0.08f; cam[2] += (bz - 0.5f - cam[2]) * 0.08f; cam[3] += (bx - cam[3]) * 0.08f; cam[5] += (bz - cam[5]) * 0.08f;
                draw(cam, 1 / 60f);
                if (f % 10 == 0 && tiles.size() < 32) {
                    tiles.add(capture(cam));
                    Snapshots.Ragdoll rd = bandit.ragdoll != 0 ? g.lastBodies.ragdoll(bandit.ragdoll) : null;
                    labels.add(String.format("%.2fs %s", f / 60f, rd == null ? "animated" : new String[]{"stand", "step", "fall", "down", "getup", "dead"}[Math.max(0, Math.min(5, rd.state))]));
                }
                if (!bandit.reacting && f > 60) break;
            }
            sheet(names[k], tiles, labels, 8);
        }
    }

    /** The day, hour by hour, from one place: a contact sheet of the light, the sky, the moon and the lamps. */
    private void dayCycle() {
        float[] hours = {0, 3, 4.5f, 5.25f, 5.75f, 6.25f, 7, 9, 12, 15, 17, 17.75f, 18.25f, 18.75f, 19.5f, 21.5f};
        List<BufferedImage> tiles = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        place(64.5f, 70.5f, 0);
        float[] cam = {64.5f, DevMap.FLOOR + 1.7f, 76f, 70f, DevMap.FLOOR + 3.2f, 58f};
        for (float h : hours) {
            double now = g.state.hourOfDay();
            g.advanceTime(((h - now) % 24 + 24) % 24 * 60, "day cycle view");
            for (int i = 0; i < 150; i++) { game.tick(1 / 60f); draw(cam, 1 / 60f); }
            tiles.add(capture(cam));
            labels.add(String.format("%05.2f h  daylight %.2f", h, rpg.ai.Stealth.daylight(h)));
        }
        sheet("daycycle", tiles, labels, 4);
    }

    /** The renderer's review views: the tree grove close and far, against the light and with it; the pond; the street. */
    private void scenery() {
        float f = DevMap.FLOOR;
        Object[][] views = {
                {12f, new float[]{90f, f + 2.2f, 26f, 96.5f, f + 3.5f, 18.5f}, "grove, noon"},
                {12f, new float[]{93.5f, f + 4.2f, 21.5f, 96.5f, f + 4.8f, 18.5f}, "a crown close, noon"},
                {17.5f, new float[]{101f, f + 2.0f, 23f, 96.5f, f + 3.5f, 17f}, "grove against the low sun"},
                {17.5f, new float[]{88f, f + 2.4f, 45f, 96f, f + 0.6f, 34f}, "pond, evening"},
                {9f, new float[]{88f, f + 2.4f, 45f, 96f, f + 0.6f, 34f}, "pond, morning"},
                {9f, new float[]{64.5f, f + 1.7f, 76f, 70f, f + 3.2f, 58f}, "street, morning (shadows)"},
                {15f, new float[]{60f, f + 1.6f, 66f, 64f, f + 0.4f, 60f}, "a shadow on the ground, afternoon"},
                {19f, new float[]{90f, f + 2.2f, 26f, 96.5f, f + 3.5f, 18.5f}, "grove at dusk"},
                {16f, new float[]{87.5f, f + 1.9f, 26.5f, 91.5f, f + 1.9f, 21.5f}, "a campfire's smoke, afternoon"},
                {21f, new float[]{87.5f, f + 1.9f, 26.5f, 91.5f, f + 1.9f, 21.5f}, "a campfire's smoke, night"},
                {14f, new float[]{60f, f + 2.5f, 33f, 64.5f, f + 3.0f, 26.5f}, "the furnace's plume, afternoon"},
                {12f, new float[]{88f, f + 1.6f, 26f, 96.5f, f + 2.5f, 18.5f}, "depth of field (on for this view)"}};
        g.objects.add(new rpg.objects.Fire(g.state.newEntityId(), 91.5f, g.vox.groundY(91.5f, f + 0.5f, 21.5f), 21.5f, g.state.minutes + 100000, g.player.id));
        List<BufferedImage> tiles = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        place(64.5f, 70.5f, 0);
        for (Object[] v : views) {
            float h = (Float) v[0];
            double now = g.state.hourOfDay();
            g.advanceTime(((h - now) % 24 + 24) % 24 * 60, "scenery view");
            float[] cam = (float[]) v[1];
            g.cfg.depthOfField = ((String) v[2]).startsWith("depth");
            for (int i = 0; i < 150; i++) { game.tick(1 / 60f); draw(cam, 1 / 60f); }
            tiles.add(capture(cam));
            labels.add((String) v[2]);
        }
        sheet("scenery", tiles, labels, 4);
    }

    /** One catalog: every entry on the figure, from the front three-quarters (and the back for hair). */
    /** The creator's catalogs on the figure (armor off: it would hide the clothes), each row restored after. */
    private void catalogs(String tag, boolean female, Appearance look) {
        for (var slot : new rpg.item.ItemDef.Slot[]{rpg.item.ItemDef.Slot.HEAD, rpg.item.ItemDef.Slot.BODY, rpg.item.ItemDef.Slot.LEGS, rpg.item.ItemDef.Slot.FEET, rpg.item.ItemDef.Slot.HANDS})
            g.player.eq.set(slot, null);
        catalog(tag + "-hair", Appearance.HAIRSTYLES, i -> { g.player.look.hair = i; }, female ? Appearance.HAIR_FEMALE : Appearance.HAIR_MALE, true);
        g.player.look.hair = look.hair;
        if (!female) catalog(tag + "-beards", Appearance.BEARDS, i -> { g.player.look.beard = i; }, Appearance.BEARD_NAMES, true);
        g.player.look.beard = look.beard;
        catalog(tag + "-tops", Appearance.TOPS, i -> { g.player.look.top = i; }, Appearance.TOP_NAMES, false);
        g.player.look.top = look.top;
        catalog(tag + "-legs", Appearance.BOTTOMS, i -> { g.player.look.bottom = i; }, Appearance.BOTTOM_NAMES, false);
        g.player.look.bottom = look.bottom;
        catalog(tag + "-shoes", Appearance.SHOES, i -> { g.player.look.shoes = i; }, Appearance.SHOE_NAMES, false);
        g.player.look.shoes = look.shoes;
        catalog(tag + "-bodies", 3, i -> { g.player.look.bodyType = i; }, Appearance.bodyNames(female), false);
        g.player.look.bodyType = look.bodyType;
    }

    private void catalog(String name, int count, IntConsumer set, String[] names, boolean head) {
        place(64.5f, 66.5f, (float) Math.PI);
        frames(5);
        List<BufferedImage> tiles = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            set.accept(i);
            frames(2);
            for (int w = 0; w < 240 && !gfx.figureReady(g.player); w++) frames(1);   // (figures are built off the render thread)
            frames(1);
            Actor a = g.player;
            float fx = (float) -Math.sin(a.yaw + 0.55), fz = (float) -Math.cos(a.yaw + 0.55);
            float[] cam = head ? new float[]{a.x + fx * 1.3f, a.y + 1.75f, a.z + fz * 1.3f, a.x, a.y + 1.55f, a.z}
                    : new float[]{a.x + fx * 3.0f, a.y + 1.15f, a.z + fz * 3.0f, a.x, a.y + 0.9f, a.z};
            tiles.add(capture(cam));
            labels.add((i + 1) + " " + names[i]);
            if (head) {
                float bx = (float) Math.sin(a.yaw - 0.5), bz = (float) Math.cos(a.yaw - 0.5);
                tiles.add(capture(new float[]{a.x + bx * 1.4f, a.y + 1.6f, a.z + bz * 1.4f, a.x, a.y + 1.35f, a.z}));
                labels.add("  (back)");
            }
        }
        sheet(name, tiles, labels, head ? 8 : 5);
    }

    private void step() {
        float fwd = g.controller.in.forward, str = g.controller.in.strafe;
        boolean main = g.controller.in.mainDown, spr = g.controller.in.sprint;
        game.tick(1 / 60f);
        draw(null, 1 / 60f);
        g.controller.in.forward = fwd; g.controller.in.strafe = str; g.controller.in.mainDown = main; g.controller.in.sprint = spr;
        g.controller.in.backTap = false;
    }

    /** The middle of the screen, as the camera sees the actor (the frame is drawn twice on a still camera). */
    private BufferedImage capture(float[] cam) {
        for (int i = 0; i < 2; i++) draw(cam, 0);
        return captureFrame();
    }

    private BufferedImage captureFrame() {
        int w = win.width, h = win.height;
        ByteBuffer px = gfx.pixels(w, h);
        int size = Math.min(w, h) / 2, x0 = (w - size) / 2, y0 = (h - size) / 2;
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < TILE; y++) for (int x = 0; x < TILE; x++) {
            int sx = x0 + x * size / TILE, sy = y0 + y * size / TILE;
            int i = ((h - 1 - sy) * w + sx) * 4;
            img.setRGB(x, y, (px.get(i) & 255) << 16 | (px.get(i + 1) & 255) << 8 | (px.get(i + 2) & 255));
        }
        MemoryUtil.memFree(px);
        return img;
    }

    /**
     * The animals: every species, adult and young, every coat, from the front three-quarters; then the gaits filmed on
     * threes (a walk and a run each for a quadruped, a bird and the rabbit's hop).
     */
    private void animalSheets() {
        var sys = g.animals;
        for (var a : sys.all()) if (!a.puppet) { a.loaded = false; sys.despawnBody(a); }
        place(40.5f, 70.5f, 0);
        List<BufferedImage> tiles = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        float x0 = 64.5f, z0 = 61.5f;
        for (rpg.animal.Species s : rpg.animal.Species.values()) {
            int coats = switch (s) { case COW, DOG, CAT, HORSE, CHICKEN -> 3; case PIG, SHEEP, DUCK -> 2; default -> 1; };
            for (int v = 0; v < coats + 1; v++) {
                boolean baby = v == coats;
                var an = sys.display(s, baby, true, baby ? 0 : v, x0, z0, (float) Math.PI * 0.8f);
                frames(4);
                float h = an.height, d = Math.max(1.2f, Math.max(h, s.w * (s == rpg.animal.Species.HORSE ? 1.6f : 1.2f)) * 2.1f);
                float yaw = an.yaw + 0.75f;
                float[] cam = {an.x - (float) Math.sin(yaw) * d, an.y + h * 0.75f + d * 0.2f, an.z - (float) Math.cos(yaw) * d, an.x, an.y + h * 0.45f, an.z};
                tiles.add(capture(cam));
                labels.add(s.label(baby) + (baby ? "" : coats > 1 ? " " + (v + 1) : ""));
                sys.remove(an);
            }
        }
        sheet("animals", tiles, labels, 8);
        // gaits: each walks then runs past a side-on camera
        for (rpg.animal.Species s : new rpg.animal.Species[]{rpg.animal.Species.HORSE, rpg.animal.Species.DOG, rpg.animal.Species.CHICKEN, rpg.animal.Species.RABBIT, rpg.animal.Species.COW, rpg.animal.Species.WOLF}) {
            for (boolean run : new boolean[]{false, true}) {
                var an = sys.display(s, false, true, 0, 50.5f, 70.5f, (float) -Math.PI / 2);
                frames(3);
                for (int i = 0; i < 40; i++) { sys.puppetTo(an, 90, 70.5f, run); step(); }
                List<BufferedImage> ft = new ArrayList<>();
                List<String> fl = new ArrayList<>();
                for (int i = 0; i < 36; i++) {
                    sys.puppetTo(an, 90, 70.5f, run);
                    step();
                    if (i % 3 == 0) {
                        float d = Math.max(1.6f, an.height * 2.4f);
                        ft.add(capture(new float[]{an.x, an.y + an.height * 0.5f, an.z + d, an.x, an.y + an.height * 0.45f, an.z}));
                        fl.add(String.format("f%d  %.2fm/s", i + 1, Math.hypot(an.vx, an.vz)));
                    }
                }
                sheet("gait-" + s.name().toLowerCase() + (run ? "-run" : "-walk"), ft, fl, 6);
                sys.remove(an);
            }
        }
        for (var a : sys.all()) if (!a.puppet && a.alive) { sys.spawnBody(a); }
    }

    /** Every item's icon, from its own voxel model (the inventory's icons, the same models the world draws). */
    private void itemSheet() {
        List<BufferedImage> tiles = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();
        // RPG_ITEMS=id,id,...: only those, drawn larger (a close look at new models)
        String only = System.getenv("RPG_ITEMS");
        java.util.Set<String> pick = only == null ? null : java.util.Set.of(only.split(","));
        for (rpg.item.ItemDef d : rpg.item.Items.all()) {
            if (pick != null && !pick.contains(d.id)) continue;
            int cell = pick != null ? 96 : 36, w = Math.max(1, d.gw) * cell, h = Math.max(1, d.gh) * cell;
            int[] px = rpg.ui.ItemIcons.raster(d, cell);
            BufferedImage icon = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            icon.setRGB(0, 0, w, h, px, 0, w);
            BufferedImage tile = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_RGB);
            Graphics2D gr = tile.createGraphics();
            gr.setColor(new Color(40, 42, 48));
            gr.fillRect(0, 0, TILE, TILE);
            float sc = Math.min((TILE - 8f) / w, (TILE - 8f) / h);
            gr.drawImage(icon, (int) (TILE - w * sc) / 2, (int) (TILE - h * sc) / 2, (int) (w * sc), (int) (h * sc), null);
            gr.dispose();
            tiles.add(tile);
            labels.add(d.name);
        }
        for (int i = 0; i < tiles.size(); i += 60) sheet("items-" + (i / 60 + 1), tiles.subList(i, Math.min(tiles.size(), i + 60)), labels.subList(i, Math.min(labels.size(), i + 60)), 10);
    }

    private void sheet(String name, List<BufferedImage> tiles, List<String> labels, int cols) {
        int rows = (tiles.size() + cols - 1) / cols;
        BufferedImage out = new BufferedImage(cols * TILE, rows * (TILE + 18), BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = out.createGraphics();
        gr.setColor(new Color(24, 24, 28));
        gr.fillRect(0, 0, out.getWidth(), out.getHeight());
        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int i = 0; i < tiles.size(); i++) {
            int cx = (i % cols) * TILE, cy = (i / cols) * (TILE + 18);
            gr.drawImage(tiles.get(i), cx, cy + 18, null);
            gr.setColor(new Color(230, 220, 190));
            gr.drawString(labels.get(i), cx + 4, cy + 13);
        }
        gr.dispose();
        try { new File(shots).mkdirs(); javax.imageio.ImageIO.write(out, "png", new File(shots, name + ".png")); System.out.println("[sheet] " + name + " (" + tiles.size() + " frames)"); }
        catch (java.io.IOException e) { System.err.println(e); }
    }

    /** The painted 128 x 128 skin, four times its size, holes chequered. */
    private void skinPng(Appearance look, rpg.render.Skin.Armor armor, String name) {
        rpg.render.Skin s = rpg.render.Skin.paint(look, armor);
        int n = rpg.render.Skin.N, k = 4;
        BufferedImage img = new BufferedImage(n * k, n * k, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < n * k; y++) for (int x = 0; x < n * k; x++) {
            int c = s.px[(y / k) * n + x / k];
            img.setRGB(x, y, (c >>> 24) == 0 ? (((x / 8 + y / 8) & 1) == 0 ? 0x303030 : 0x404040) : c & 0xFFFFFF);
        }
        try { new File(shots).mkdirs(); javax.imageio.ImageIO.write(img, "png", new File(shots, name + ".png")); } catch (java.io.IOException e) { System.err.println(e); }
    }

    private void place(float x, float z, float yaw) {
        g.teleport(g.player, x, DevMap.FLOOR, z);
        g.controller.snapTo(yaw);
        g.controller.camPitch = 0;
        g.controller.in.forward = 0; g.controller.in.strafe = 0; g.controller.in.sprint = false; g.controller.in.mainDown = false;
        frames(2);
    }

    /** A camera beside the actor (to its left), level with its chest. */
    private float[] side(Actor a, float d) { float lx = (float) -Math.cos(a.yaw), lz = (float) Math.sin(a.yaw); return new float[]{a.x + lx * d, a.y + 1.1f, a.z + lz * d, a.x, a.y + 0.9f, a.z}; }
    private float[] front(Actor a, float d) { float fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw); return new float[]{a.x + fx * d, a.y + 1.2f, a.z + fz * d, a.x, a.y + 0.9f, a.z}; }
    private float[] quarter(Actor a, float d) {
        float fx = (float) -Math.sin(a.yaw + 0.8), fz = (float) -Math.cos(a.yaw + 0.8);
        return new float[]{a.x + fx * d, a.y + 1.5f, a.z + fz * d, a.x, a.y + 1.1f, a.z};
    }

    private void view(float ex, float ey, float ez, float tx, float ty, float tz, String name) {
        frames(3);
        for (int i = 0; i < 3; i++) draw(new float[]{ex, ey, ez, tx, ty, tz}, 0);
        Main.shot(gfx, win.width, win.height, new File(shots, name + ".png"));
    }

    private void frames(int n) { for (int i = 0; i < n; i++) step(); }

    /** A still from a fixed camera after four seconds of frames, so the exposure has settled to the light. */
    private void adapted(float[] cam, String name) {
        for (int i = 0; i < 240; i++) { game.tick(1 / 60f); draw(cam, 1 / 60f); }
        Main.shot(gfx, win.width, win.height, new File(shots, name + ".png"));
    }

    private void draw(float[] cam, float dt) {
        if (cam == null) { Main.drawGame(g, gfx, ui, hud, win.width, win.height, dt, false); return; }
        float dx = cam[3] - cam[0], dy = cam[4] - cam[1], dz = cam[5] - cam[2], l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        gfx.camera(cam[0], cam[1], cam[2], dx / l, dy / l, dz / l, win.width, win.height, dt);
        gfx.scene(g, false, dt, g.state.hourOfDay());
        if(reviewMesh!=0)gfx.cmd.model(reviewMesh,astratto.bridge.AstrattoNative.MODEL_FOLIAGE,reviewTransform.m,1,1,1,1,0,1,0.95f,0);
        ui.begin(gfx.cmd);
        ui.end();
        gfx.frame(win.width, win.height);
    }
}
