package rpg.actor;

import rpg.Rpg;
import rpg.actor.Actor.Combat.Move;
import rpg.combat.CombatSystem;
import rpg.combat.Spells;
import rpg.item.ItemDef;
import rpg.item.ItemDef.Family;
import rpg.skill.Skill;
import rpg.world.Voxels;

/**
 * The player's body and hands. Intent comes from the keyboard and mouse - or, in an automated run, from a script -
 * and becomes: movement for the engine's mover (walk, sprint on stamina, jump, dodge, quick turn), the camera (first or
 * third person, free aim with gentle ranged targeting assistance), and independent hands: the main hand on the left button (tap a light swing, hold
 * for a heavy one, draw a bow, cast from a staff) and the off hand on the right (raise a shield or guard, swing an
 * off-hand weapon, cast from a wand, bless with a symbol).
 *
 * <p>The body has weight (gamefixes, Human Walk System): it gathers speed and sheds it rather than starting or stopping
 * at once; a change of direction is carried round an arc whose rate falls with speed, and a sharp reversal at speed
 * brakes first; sprinting is a committed forward run that steers wide and needs a moment to recover from when stopped
 * short; aiming (a guard raised, a bow drawn, a blow charging or under way) slows the body to a deliberate strafe facing
 * the aim and rules out the quick turn; strikes, guards and dodges have start-up and recovery that hold the body back.
 */
public final class PlayerController {
    /** What the player wants this frame. */
    public static final class Intent {
        public float forward, strafe, lookYaw, lookPitch;
        /** sprint: the sprint key held (Shift: a true sprint); jogToggle: Caps Lock was just pressed. */
        /** Space held (noclip's up). */
        public boolean jumpHeld;
        public boolean jogToggle;
        public boolean sprint, sneak, jump, dodge, mainDown, offDown, cast, interact, interactHeld, toggleCamera;
        /** (0.2.2) F was held long enough: sheathe or draw. */
        public boolean sheathe;
        /** The back key was just pressed (two taps of it close together turn the player about). */
        public boolean turnBack;
        public int bless = -1;
        public void clearEdges() { jump = dodge = cast = interact = toggleCamera = turnBack = jogToggle = sheathe = false; bless = -1; lookYaw = lookPitch = 0; }
    }

    private final Rpg g;
    public final Intent in = new Intent();
    public float camYaw, camPitch = -0.12f;
    /** The camera, as last placed, and the aim ray through the crosshair. */
    public float cx, cy, cz, fx, fy, fz;
    public float aimYaw, aimPitch;
    /** A visible target retained only while the player aims a ranged weapon. */
    public long softTarget;
    private boolean mainWas, offWas;
    private float mainHeld;
    /** One near-ready melee release, retained briefly so a recovery frame does not swallow the click. */
    private Move queuedMelee;
    private ItemDef queuedWeapon;
    private double queuedUntil;
    private static final float MELEE_BUFFER = 0.2f;
    private float stepClock;
    /** After a sprint is stopped short, a moment to recover; a quick turn in progress (seconds left) and its heading. */
    private float sprintRecover, quickTurn, quickYaw, quickCam, sprintTime;
    /** When the last sprint was stopped short (the figure plants its feet and leans back: see Rig). */
    public double sprintStopAt = -9;
    private double lastGrounded = -9, lastJump = -9;
    /** The jog kept on (Caps Lock toggles it). */
    public boolean jogging;
    private float jumpBuffer;
    /** Click-to-walk: the route the engine found, followed while no movement key is held. */
    private int[] route;
    private int routeAt, pathRequest = 7000;

    public PlayerController(Rpg g) { this.g = g; }

    public void update(float dt) {
        Player p = g.player;
        camYaw += in.lookYaw;
        camPitch = Math.max(-1.45f, Math.min(1.35f, camPitch + in.lookPitch));
        if (p.morphT > 0) {
            if (!p.morphCameraSaved) { p.morphFirstPerson = p.firstPerson; p.morphCameraSaved = true; }
            p.firstPerson = false; p.headValid = false;
        } else if (p.morphCameraSaved) {
            p.firstPerson = p.morphFirstPerson; p.morphCameraSaved = false; p.headValid = false;
        }
        if (in.toggleCamera && p.morphT <= 0) { p.firstPerson = !p.firstPerson; g.chat.say(p.firstPerson ? "First-person camera." : "Third-person camera."); }
        placeCamera(p);
        assistAim(p, dt);
        if (!p.alive) {
            queuedMelee = null;
            if (g.death.active) { g.death.camera(this); g.game.commands().listener(cx, cy, cz, (float) Math.atan2(-fx, -fz)); }
            g.game.commands().moverInput(p.body, 0, 0, 0, 0, 0, 0); in.clearEdges(); return;
        }
        if (p.reacting) {
            queuedMelee = null; p.cs.cancel();
            g.game.commands().moverInput(p.body, 0, 0, 0, 0, 0, astratto.bridge.AstrattoNative.MOVER_POSED);
            // Keep the camera clear of the tumbling head and retain free look while recovering.
            g.game.commands().listener(cx, cy, cz, camYaw); in.clearEdges(); return;
        }
        p.aimYaw = aimYaw;
        p.pitch = aimPitch;
        hands(p, dt);
        move(p, dt);
        face(p, dt);
        if (in.interact && g.interact.focus != null) g.interact.primary(p, g.interact.focus);
        g.game.commands().listener(cx, cy, cz, camYaw);
        in.clearEdges();
    }

    private void hands(Player p, float dt) {
        if (p.morphT > 0) { queuedMelee = null; p.cs.cancel(); mainWas = offWas = false; return; }
        if (g.traversal.busy(p)) { queuedMelee = null; mainWas = offWas = false; mainHeld = 0; return; }
        if (g.carrying.busy(p)) {
            queuedMelee = null; p.cs.blocking = false; p.cs.draw = p.cs.charge = 0;
            if (in.mainDown) mainHeld += dt;
            if (in.mainDown && mainHeld > 0.12f && g.carrying.ready(p)) p.cs.charge = Math.min(1, (mainHeld - 0.12f) / 0.23f);
            if (!in.mainDown && mainWas && g.carrying.ready(p)) { if (mainHeld >= 0.35f) g.carrying.drop(p, true); else g.carrying.putDown(p); }
            mainWas = in.mainDown; offWas = in.offDown;
            if (!in.mainDown) mainHeld = 0;
            return;
        }
        // (0.2.2) hold F: sheathe or draw. Put away, a press of either hand draws again instead of striking
        if (in.sheathe) g.combat.sheathe(p, !p.sheathed);
        if (p.sheathed && p.sheathable() || !g.combat.armsReady(p)) {
            queuedMelee = null; p.cs.blocking = false; p.cs.draw = p.cs.charge = 0;
            if (p.sheathed && (in.mainDown && !mainWas || in.offDown && !offWas)) g.combat.sheathe(p, false);
            mainWas = in.mainDown; offWas = in.offDown; mainHeld = 0;
            return;
        }
        ItemDef main = p.main(), off = p.off();
        if (queuedMelee != null && (g.time > queuedUntil || main != queuedWeapon || in.offDown || in.dodge)) queuedMelee = null;
        boolean bow = main != null && main.family == Family.BOW, staff = main != null && main.family == Family.STAFF;
        boolean crossbow = main != null && main.family == Family.CROSSBOW;
        float aim = crossbow && in.offDown ? 1 : 0;
        p.cs.aim += (aim - p.cs.aim) * (1 - (float) Math.exp(-16 * dt));
        // main hand
        if (in.mainDown) mainHeld += dt;
        if (bow) {
            if (in.mainDown && !mainWas && g.sfx != null) g.sfx.play("bow.draw", p.x, p.eyeY(), p.z, 0.45f);
            if (in.mainDown && !p.cs.busy()) p.cs.draw = Math.min(1, p.cs.draw + dt / 0.9f);
            if (!in.mainDown && mainWas && p.cs.draw > 0) { g.combat.shoot(p, aimYaw, aimPitch, p.cs.draw); p.cs.draw = 0; }
        } else if (staff) {
            if (in.mainDown && !mainWas) g.combat.cast(p, p.spell, aimYaw, aimPitch);
        } else if (main != null && main.family == Family.CROSSBOW) {
            // a crossbow: press to loose the loaded bolt (it loads itself again)
            if (in.mainDown && !mainWas) g.combat.shootCrossbow(p, aimYaw, aimPitch);
        } else if (main != null && main.family == Family.THROWN) {
            // a throw: hold to wind up (a moment), let go to throw one from the stack
            if (in.mainDown && !p.cs.busy()) p.cs.draw = Math.min(1, p.cs.draw + dt / 0.35f);
            if (!in.mainDown && mainWas && p.cs.draw > 0) { g.combat.throwOne(p, aimYaw, aimPitch, p.cs.draw); p.cs.draw = 0; }
        } else if (main != null && main.toolSkill == rpg.skill.Skill.FISHING) {
            // a net, a rod or a cage is cast, not swung: at the fishing spot the crosshair is on (or the nearest one ahead)
            if (in.mainDown && !mainWas) castLine(p);
        } else {
            p.cs.charge = in.mainDown && !p.cs.busy() ? Math.min(1, mainHeld / 0.45f) : 0;
            if (!in.mainDown && mainWas && !in.offDown) {
                Move move = mainHeld >= 0.32f ? Move.HEAVY : CombatSystem.lightMove(p, false);
                queuedMelee = null;
                if (!g.combat.attack(p, false, move) && p.cs.phase == Actor.Combat.Phase.RECOVER) {
                    queuedMelee = move; queuedWeapon = main; queuedUntil = g.time + MELEE_BUFFER;
                }
            }
            if (queuedMelee != null && !in.mainDown && p.cs.phase == Actor.Combat.Phase.IDLE) {
                Move move = queuedMelee; queuedMelee = null;
                g.combat.attack(p, false, move);
            }
        }
        if (!in.mainDown) mainHeld = 0;
        mainWas = in.mainDown;
        // off hand: guard with a shield (or the main weapon when the off hand is empty), or use what it holds
        boolean guardItem = main == null && off == null || off != null && off.family == Family.SHIELD || off == null && main != null && main.guard > 0 && !bow && !staff && !crossbow;
        if (guardItem) g.combat.setBlock(p, in.offDown);
        else {
            g.combat.setBlock(p, false);
            if (in.offDown && !offWas && off != null) {
                switch (off.family) {
                    case WAND -> g.combat.cast(p, p.spell, aimYaw, aimPitch);
                    case SYMBOL -> g.combat.bless(p, Spells.Prayer.WARD);
                    default -> g.combat.attack(p, true, CombatSystem.lightMove(p, true));
                }
            }
        }
        offWas = in.offDown;
        if (in.cast) g.combat.cast(p, p.spell, aimYaw, aimPitch);
        if (in.bless >= 0 && in.bless < Spells.Prayer.values().length) g.combat.bless(p, Spells.Prayer.values()[in.bless]);
        if (in.dodge) {
            float fx = (float) -Math.sin(camYaw), fz = (float) -Math.cos(camYaw), rx = (float) Math.cos(camYaw), rz = (float) -Math.sin(camYaw);
            float dx = fx * in.forward + rx * in.strafe, dz = fz * in.forward + rz * in.strafe;
            if (dx == 0 && dz == 0) { dx = -fx; dz = -fz; }
            g.combat.dodge(p, dx, dz);
        }
    }

    private void assistAim(Player p, float dt) {
        ItemDef weapon = p.main(), off = p.off();
        boolean ranged = weapon != null && (weapon.family == Family.BOW || weapon.family == Family.CROSSBOW || weapon.family == Family.THROWN || weapon.family == Family.STAFF)
                || off != null && off.family == Family.WAND;
        if (!p.alive || p.reacting || p.sheathed || !ranged || !(in.mainDown || in.offDown || p.cs.draw > 0.01f || p.cs.aim > 0.1f)) { softTarget = 0; return; }
        Actor best = null; float bestScore = Float.MAX_VALUE;
        for (Actor candidate : g.actors()) {
            if (candidate == p || !candidate.alive || candidate.summonOwner == p.id || candidate.team != 0 && candidate.team == p.team) continue;
            float dx = candidate.x - cx, dy = candidate.chestY() - cy, dz = candidate.z - cz;
            float distance = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 1 || distance > 32) continue;
            float dot = (dx * fx + dy * fy + dz * fz) / distance;
            float cone = candidate.id == softTarget ? 0.985f : 0.995f;
            if (dot < cone || !g.vox.sight(cx, cy, cz, candidate.x, candidate.chestY(), candidate.z)
                    || !g.vox.sight(p.x, p.eyeY(), p.z, candidate.x, candidate.chestY(), candidate.z)) continue;
            float score = 1 - dot + distance * 0.00001f - (candidate.id == softTarget ? 0.002f : 0);
            if (score < bestScore) { best = candidate; bestScore = score; }
        }
        softTarget = best == null ? 0 : best.id;
        if (best == null) return;
        float dx = best.x - cx, dy = best.chestY() - cy, dz = best.z - cz;
        float yaw = (float)Math.atan2(-dx, -dz), pitch = (float)Math.atan2(dy, Math.hypot(dx, dz));
        float limit = dt * 0.45f;
        camYaw = Actor.wrap(camYaw + Math.clamp(Actor.wrap(yaw - camYaw) * dt * 5, -limit, limit));
        camPitch += Math.clamp((pitch - camPitch) * dt * 5, -limit, limit);
        placeCamera(p, true);
    }

    /** noclip: the body flies where the camera looks, through everything (Space up, Ctrl down, Shift fast). */
    private void fly(Player p, float dt) {
        float cp = (float) Math.cos(camPitch), sp = (float) Math.sin(camPitch);
        float fx = (float) -Math.sin(camYaw) * cp, fy = sp, fz = (float) -Math.cos(camYaw) * cp, rx = (float) Math.cos(camYaw), rz = (float) -Math.sin(camYaw);
        float speed = in.sprint ? 22 : 8;
        float vx = (fx * in.forward + rx * in.strafe) * speed, vz = (fz * in.forward + rz * in.strafe) * speed;
        float vy = fy * in.forward * speed + (in.jumpHeld ? speed : 0) - (in.sneak ? speed : 0);
        p.x += vx * dt; p.y += vy * dt; p.z += vz * dt;
        g.game.commands().moverPlace(p.body, p.x, p.y + 0.05f, p.z, 0, 0, 0);
        p.vx = 0; p.vz = 0; p.sprinting = false; p.running = false; p.sneaking = false;
    }

    /** Casts the wielded fishing tool at the spot in the sights, or the nearest one in front within a cast's reach. */
    private void castLine(Player p) {
        rpg.objects.Node best = g.interact.focus instanceof rpg.objects.Node n && n.kind == rpg.objects.Node.Kind.FISH ? n : null;
        if (best == null) {
            float bestD = 6.5f;
            float fx = (float) -Math.sin(aimYaw), fz = (float) -Math.cos(aimYaw);
            for (rpg.objects.Node n : g.objects.nodes) {
                if (n.kind != rpg.objects.Node.Kind.FISH) continue;
                float dx = n.x - p.x, dz = n.z - p.z, d = (float) Math.hypot(dx, dz);
                if (d < bestD && (dx * fx + dz * fz) / Math.max(0.1f, d) > 0.5f) { bestD = d; best = n; }
            }
        }
        if (best == null) { g.chat.warn("There's nowhere to fish in front of you."); return; }
        if (g.interact.of(p) != null && g.interact.of(p).target == best) return;
        g.interact.request(p, best, "gather");
    }

    /** Asks the engine for a route to a point (the minimap's click-to-walk). */
    public void walkTo(float x, float z) {
        pathRequest++;
        route = null;
        g.game.commands().pathRequest(pathRequest, 1, g.player.x, g.player.y, g.player.z, x, rpg.world.DevMap.FLOOR, z, 20000);
    }

    public void onPath(int request, int[] cells) {
        if (request != pathRequest) return;
        if (cells == null || cells.length < 3) { g.chat.warn("You can't reach that."); g.ui.flag = null; return; }
        route = cells; routeAt = 0;
    }

    /** Whether the player is aiming or committed to an action: the body faces the aim and moves in a deliberate strafe. */
    public boolean strafing(Player p) { return p.firstPerson || aiming(p); }

    /** Whether a weapon or spell is raised: that, not the first-person camera, is what rules out a sprint. */
    public boolean aiming(Player p) {
        Actor.Combat c = p.cs;
        return c.aim > 0.1f || c.blocking || c.draw > 0 || c.charge > 0 || c.phase != Actor.Combat.Phase.IDLE || c.castCooldown > 0.35f;
    }

    private void move(Player p, float dt) {
        if (g.ui != null && g.ui.console.noclip) { fly(p, dt); return; }
        if (!g.traversal.busy(p) && in.sprint && in.forward > 0.1f && !in.sneak) g.traversal.begin(p, camYaw);
        // (0.2.1) a jump into a fence or a low wall while moving toward it is a vault over it (Agility 2)
        else if (!g.traversal.busy(p) && in.jump && p.grounded && in.forward > 0.1f && !in.sneak && g.traversal.vault(p, camYaw) != null) g.traversal.begin(p, camYaw);
        if (g.traversal.update(p, dt)) return;
        float fwd = in.forward, side = in.strafe;
        float fx = (float) -Math.sin(camYaw), fz = (float) -Math.cos(camYaw), rx = (float) Math.cos(camYaw), rz = (float) -Math.sin(camYaw);
        float dx = fx * fwd + rx * side, dz = fz * fwd + rz * side, l = (float) Math.hypot(dx, dz);
        if (l > 0.1f || in.jump) { route = null; if (g.ui != null) g.ui.flag = null; }
        else if (route != null) {
            while (routeAt * 3 < route.length && Math.hypot(route[routeAt * 3] + 0.5f - p.x, route[routeAt * 3 + 2] + 0.5f - p.z) < 0.45f) routeAt++;
            if (routeAt * 3 >= route.length) { route = null; if (g.ui != null) g.ui.flag = null; }
            else {
                float tx = route[routeAt * 3] + 0.5f - p.x, tz = route[routeAt * 3 + 2] + 0.5f - p.z, tl = (float) Math.hypot(tx, tz);
                dx = tx / tl; dz = tz / tl; l = 1; fwd = 1;
                if (route[routeAt * 3 + 1] > p.y + 0.6f && p.grounded) in.jump = true;
            }
        }
        if (g.time < p.throwFreezeUntil) { dx = 0; dz = 0; l = 0; }   // the feet stay put through a release
        if (l > 1) { dx /= l; dz /= l; l = 1; }
        Actor.Combat c = p.cs;
        boolean strafe = strafing(p);
        // the quick turn: Ctrl+S (crouch and back together), never while aiming
        if (in.turnBack && !strafe && p.grounded && quickTurn <= 0 && c.staggered <= 0 && c.dodgeT <= 0) {
            quickTurn = 0.32f;
            quickYaw = Actor.wrap(p.yaw + (float) Math.PI);
            quickCam = Actor.wrap(camYaw + (float) Math.PI);
        }
        int agility = p.skills.level(Skill.AGILITY);
        boolean moving = l > 0.1f && quickTurn <= 0;
        // the body's velocity as it really is (walls, and whatever it walked into, have had their say)
        float bvx = p.vx, bvz = p.vz;
        float speedNow = (float) Math.hypot(bvx, bvz);
        // the wish relative to the way the body faces: backward and sideways are slower than forward
        float bfx = (float) -Math.sin(p.yaw), bfz = (float) -Math.cos(p.yaw);
        float along = l > 0.1f ? (dx * bfx + dz * bfz) / l : 1;
        boolean wasSprinting = p.sprinting;
        // a sprint goes any way the keys point (gamefixes): in third person the body turns to run that way; in first person
        // the body keeps facing the view, so a sprint to the side or back is slower than one straight ahead
        boolean mayRun = !aiming(p) && !(strafe && !p.firstPerson);
        // Shift held is a sprint (gamefixes: a true sprint by default, not a jog), for as long as the stamina lasts; the
        // jog is a pace kept on with Caps Lock; out of breath, a sprint falls back to the jog until Shift is let go
        if (in.jogToggle) jogging = !jogging;
        boolean sprintKey = in.sprint;
        p.running = (sprintKey || jogging) && moving && mayRun && c.staggered <= 0;
        p.sprinting = p.running && sprintKey && p.stamina > 1 && sprintRecover <= 0;
        sprintTime = p.sprinting ? sprintTime + dt : 0;
        p.sneaking = in.sneak && !p.sprinting && p.alive;
        // stopped short from a run: the feet plant and the body takes a moment to gather itself (the recovery)
        if (wasSprinting && !p.sprinting && speedNow > g.cfg.walkSpeed * 1.15f && !moving) { sprintRecover = 0.45f; sprintStopAt = g.time; }
        sprintRecover = Math.max(0, sprintRecover - dt);
        quickTurn = Math.max(0, quickTurn - dt);
        float max = p.sprinting ? g.cfg.runSpeed * (1 + agility * 0.0015f) : p.running ? g.cfg.jogSpeed * (1 + agility * 0.001f) : g.cfg.walkSpeed;
        if (!p.running) max *= aiming(p) ? 0.75f : along < -0.3f ? 0.8f : 1f;
        else if (p.firstPerson) max *= along > 0.7f ? 1f : along > -0.3f ? 0.82f : 0.66f;
        if (sprintRecover > 0) max *= 0.5f;
        if (p.sneaking || p.crouched) max = Math.min(max, g.cfg.sneakSpeed);
        // committed actions: their start-up and recovery hold the body back
        switch (c.phase) {
            case WINDUP -> max *= 0.45f;
            case ACTIVE -> max *= 0.25f;
            case RECOVER -> max *= 0.5f;
            default -> { }
        }
        if (c.blocking) max *= g.time - c.blockStart < g.cfg.parryWindow ? 0.3f : 0.55f;
        if (c.charge > 0) max *= 0.7f;
        if (c.draw > 0) max *= 0.75f;
        if (c.slowT > 0) max *= 0.6f;
        if (c.frozenT > 0) max = 0;
        // the load: a heavy pack slows every gait, makes the body slower to get going and tires a sprint sooner
        float burden = p.burden();
        max *= burden * p.actionRate();
        if (p.morphT > 0) { max = Math.min(max, 2); p.sneaking = true; }
        if (g.carrying.busy(p)) max *= g.carrying.ready(p) ? 0.55f : 0;
        // (0.2.2, gamefixes) a hurt leg changes how the player walks (Rig's injured gaits), never how fast: only a blow
        // being taken this moment checks the stride (a stun stops it outright, below)
        if (g.time - c.lastHurt < 0.4) max *= 0.7f;
        if (c.staggered > 0) max = 0;
        // (0.2.2, gamefixes: Shift held against a wall still drained stamina) the sprint is paid for in ground actually
        // covered at a sprint: the share of the way from a walk to a full run the body is really making - nothing while
        // it pushes at a wall, a little while it gathers speed
        if (p.sprinting) {
            float covered = Math.max(0, Math.min(1, (speedNow - g.cfg.walkSpeed) / Math.max(0.1f, g.cfg.runSpeed - g.cfg.walkSpeed)));
            float previousStamina = p.stamina;
            p.stamina = Math.max(0, p.stamina - g.cfg.staminaSprint * (1 - agility * 0.004f) / burden * dt * covered);
            if (covered > 0) c.regenDelay = 0.6f;
            g.movementPractice.sprintCost(previousStamina - p.stamina);
        }
        float want = moving ? max : 0;
        // a body gathers speed over a few strides and sheds it in a step or two: weight without the slide (it used to coast
        // a metre or more after a sprint at 7 m/s per second of braking)
        float accel = p.sprinting ? (sprintTime < 0.6f ? 7f : 9f) : p.running ? 12f : 14f;
        float decel = wasSprinting || speedNow > g.cfg.walkSpeed * 1.15f ? 26f : 32f;
        if (c.staggered > 0) decel = 22f;
        accel *= 0.55f + 0.45f * burden;
        // (gamefixes: first person should feel snappier; third person stays as it is) through one's own eyes the body
        // answers the keys at once: it is up to speed in a stride and stops in half of one
        if (p.firstPerson && p.morphT <= 0) { accel *= p.sprinting ? 1.7f : 2.2f; decel *= 1.6f; }
        if (!p.grounded) { accel *= 0.15f; decel *= 0.15f; }
        float nvx, nvz;
        if (speedNow > 0.5f && moving && !strafe) {
            // carried round an arc: the heading turns at a rate that falls as speed rises
            float cur = (float) Math.atan2(bvx, bvz), wantAng = (float) Math.atan2(dx, dz), diff = Actor.wrap(wantAng - cur);
            float rate = p.sprinting ? 4.5f : p.running ? 9f : 14f;
            float ns;
            if (Math.abs(diff) > 2.3f && speedNow > g.cfg.walkSpeed + 0.2f) ns = approach(speedNow, 0, decel * dt);    // reversing at speed: it brakes first
            else {
                cur += Math.max(-rate * dt, Math.min(rate * dt, diff));
                // a sharp turn is paid for in speed (the feet dig in round it), not slid round at full pace
                float turning = Math.min(1, Math.abs(diff) / 1.6f);
                float inTurn = want * (1 - (p.sprinting ? 0.35f : 0.5f) * turning);
                ns = approach(speedNow, inTurn, (inTurn > speedNow ? accel : decel) * dt);
            }
            nvx = (float) Math.sin(cur) * ns; nvz = (float) Math.cos(cur) * ns;
        } else {
            // from rest, or strafing: the velocity itself goes toward the wish, still at the body's acceleration
            float wx = moving ? dx / Math.max(1e-3f, l) * want : 0, wz = moving ? dz / Math.max(1e-3f, l) * want : 0;
            float ex = wx - bvx, ez = wz - bvz, el = (float) Math.hypot(ex, ez);
            float a = (moving && (wx * bvx + wz * bvz) >= 0 ? accel : decel) * dt;
            if (el <= a) { nvx = wx; nvz = wz; } else { nvx = bvx + ex / el * a; nvz = bvz + ez / el * a; }
        }
        if (c.dodgeT > 0) { nvx = c.dodgeX * g.cfg.dodgeSpeed; nvz = c.dodgeZ * g.cfg.dodgeSpeed; }
        // a jump press is held for a moment (pressed just before landing, or while a swing finishes, it still jumps), and
        // the feet count as planted a moment after leaving an edge; the late part of a swing's recovery may be jumped out of
        if (in.jump) jumpBuffer = 0.18f; else jumpBuffer = Math.max(0, jumpBuffer - dt);
        if (p.grounded) lastGrounded = g.time;
        boolean free = c.staggered <= 0 && c.dodgeT <= 0 && (c.phase == Actor.Combat.Phase.IDLE || c.phase == Actor.Combat.Phase.RECOVER && c.t >= c.recover * 0.4f);
        boolean jump = jumpBuffer > 0 && free && (p.grounded || g.time - lastGrounded < 0.12) && g.time - lastJump > 0.3;
        if (jump) { g.movementPractice.jumped(); jumpBuffer = 0; lastJump = g.time; lastGrounded = -9; if (c.phase != Actor.Combat.Phase.IDLE) c.cancel(); }
        boolean onLadder=rpg.world.Blocks.ladder(g.vox.block((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z))) || rpg.world.Blocks.ladder(g.vox.block((int)Math.floor(p.x),(int)Math.floor(p.y)-1,(int)Math.floor(p.z)));
        g.game.commands().moverInput(p.body, nvx, nvz, onLadder && free ? in.forward : 0, jump && !onLadder ? g.cfg.jumpSpeed : 0, 80, p.sneaking ? astratto.bridge.AstrattoNative.MOVER_CROUCH : 0);
        // a footfall each time a foot is planted, in step with the gait the figure walks
        float speed = (float) Math.hypot(nvx, nvz);
        if (speed > Gait.STILL && p.grounded && p.gaseousT <= 0) {
            stepClock += dt * Gait.steps(speed, p.sneaking);
            if (stepClock >= 1) {
                stepClock -= 1;
                if (g.sfx != null) g.sfx.footstep(p, p.sprinting ? 1f : 0.3f + 0.5f * Math.min(1, speed / g.cfg.runSpeed));
                g.noise(p, p.x, p.y, p.z, p.sprinting ? 9 : p.sneaking ? 0.8f : speed > 3 ? 5 : 2, 0.2f);
            }
        } else stepClock = 0.5f;
    }

    /**
     * Where the body faces: the aim while aiming (fast while a blow starts or a guard rises), otherwise the way it goes;
     * standing, it turns to the aim only when the head can no longer follow it.
     */
    private void face(Player p, float dt) {
        float speed = (float) Math.hypot(p.vx, p.vz);
        Actor.Combat c = p.cs;
        if (quickTurn > 0) {
            p.turnToward(quickYaw, (float) Math.PI / 0.28f, dt);
            camYaw = Actor.wrap(camYaw + Actor.wrap(quickCam - camYaw) * Math.min(1, dt / Math.max(dt, quickTurn)));
            return;
        }
        if (strafing(p)) p.turnToward(p.aimYaw, p.firstPerson ? 26f : c.phase == Actor.Combat.Phase.WINDUP || c.blocking ? 16f : 11f, dt);
        else if (speed > 0.4f) p.turnToward((float) Math.atan2(-p.vx, -p.vz), p.sprinting ? 7f : 14f, dt);
        // at work on something (a fire, a forge, a fishing spot): the body turns to face it
        else if (p.working != null && p.working.target instanceof rpg.interact.WorldObject o && Math.hypot(o.x - p.x, o.z - p.z) > 0.2)
            p.turnToward((float) Math.atan2(-(o.x - p.x), -(o.z - p.z)), 6f, dt);
        // Standing in third person, looking moves the head; the feet turn with movement or an action.
    }

    private static float approach(float v, float to, float step) { return Math.abs(to - v) <= step ? to : v + Math.signum(to - v) * step; }

    /** First person at the eyes (as the head carries them through each step); third person behind the right shoulder, pulled in front of walls. Then the aim. */
    private void placeCamera(Player p) { placeCamera(p,true); }
    public void syncCamera() {
        if(g.death.active)g.death.camera(this);else placeCamera(g.player,false);
    }
    private void placeCamera(Player p,boolean focus) {
        float cp = (float) Math.cos(camPitch), sp = (float) Math.sin(camPitch);
        fx = (float) -Math.sin(camYaw) * cp; fy = sp; fz = (float) -Math.cos(camYaw) * cp;
        float ex = p.x, ey = p.alive ? p.eyeY() : p.y + 0.5f, ez = p.z;
        if (p.firstPerson && !p.reacting && p.alive && p.headValid) {
            // the eyes where the rig last put them: the camera rides the head's bob and sway, not a fixed height
            float turn = Actor.wrap(p.yaw - p.headYaw), co = (float) Math.cos(turn), si = (float) Math.sin(turn);
            // looking down, the head tips forward on the neck and the eyes go out over the chest (more when crouched,
            // where the body is folded under them): without it the camera sank into the torso (gamefixes)
            float down = Math.max(0, -sp), ahead = 0.04f + down * down * (p.crouched ? 0.42f : 0.26f);
            float hx = (float) -Math.sin(camYaw), hz = (float) -Math.cos(camYaw);
            float vx = p.headX * co + p.headZ * si + hx * ahead, vz = -p.headX * si + p.headZ * co + hz * ahead;
            float distance = (float) Math.hypot(vx, vz), limit = Math.max(0.42f + down * 0.2f, p.radius - 0.08f);
            float k = distance > limit ? limit / distance : 1;
            cx = p.x + vx * k; cy = Math.max(p.y+0.15f,Math.min(p.y+p.headY,p.y+p.height*(p.crouched?rpg.actor.Body.CROUCH_HEIGHT:1)-0.12f)); cz = p.z + vz * k;
            var wall = g.vox.ray(p.x, cy, p.z, distance > 0 ? vx / distance : 0, 0, distance > 0 ? vz / distance : -1, distance + 0.08f, false);
            if (wall.hit && wall.dist < distance + 0.08f) { k = Math.min(k, Math.max(0, wall.dist - 0.1f) / Math.max(0.001f, distance)); cx = p.x + vx * k; cz = p.z + vz * k; }
        } else if (p.firstPerson && !p.reacting) { cx = ex; cy = ey; cz = ez; }
        else {
            float rx = (float) Math.cos(camYaw), rz = (float) -Math.sin(camYaw);
            float px = ex + rx * 0.55f, py = ey + 0.15f, pz = ez + rz * 0.55f;
            if (!g.vox.clear(ex, ey, ez, px, py, pz)) { px = ex; py = ey + 0.15f; pz = ez; }
            float back = p.camDist;
            Voxels.Hit h = g.vox.ray(px, py, pz, -fx, -fy, -fz, back + 0.3f, false);
            if (h.hit) back = Math.max(0.3f, h.dist - 0.3f);
            cx = px - fx * back; cy = py - fy * back; cz = pz - fz * back;
        }
        // aim: from the eyes at whatever the crosshair falls on
        Voxels.Hit h = g.vox.ray(cx, cy, cz, fx, fy, fz, 80, false);
        float t = h.hit ? h.dist : 80;
        float ax = cx + fx * t - ex, ay = cy + fy * t - ey, az = cz + fz * t - ez;
        if (Math.sqrt(ax * ax + ay * ay + az * az) < 1.5f) { ax = fx; ay = fy; az = fz; }
        aimYaw = (float) Math.atan2(-ax, -az);
        aimPitch = (float) Math.atan2(ay, Math.hypot(ax, az));
        if(focus)g.interact.computeFocus(p, cx, cy, cz, fx, fy, fz);
    }

    /** Menus and respawns must not turn an old held button into a new attack on release. */
    public void cancelHandInput() { queuedMelee = null; mainWas = offWas = false; mainHeld = 0; }

    public void snapTo(float yaw) { camYaw = yaw; g.player.yaw = yaw; g.player.aimYaw = yaw; cancelHandInput(); }
}
