package rpg.combat;

import rpg.Chat;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Player;
import rpg.ai.Npc;
import rpg.animal.Animal;
import rpg.combat.Spells.Spell;
import rpg.world.DevMap;
import rpg.world.Voxels;

/**
 * The non-damaging spells (GDD 2.5: Lunar traversal and social magic, Arcane illusion, obscuration, identity and
 * influence), each resolving through the same world as everything else - a step is a real teleport, an illusion a real
 * noise the NPCs' ears hear, a darkness a real drop in the light the stealth system reads, a disguise a witness who cannot
 * name who they saw, a charm a person who stops fighting. Every one is aimed: what the caster looks at is its target.
 */
public final class Magic {
    private final Rpg g;
    private final java.util.Map<Long, Animal> forms = new java.util.HashMap<>();
    /** Globes of magical darkness: where, how big, until when (Stealth reads them). */
    public record Dark(float x, float y, float z, float r, double until) {}
    public final java.util.List<Dark> darkness = new java.util.ArrayList<>();

    public Magic(Rpg g) { this.g = g; }

    /** Casts a utility spell; false (nothing spent) when it has nothing to act on. */
    public boolean cast(Actor a, Spell s, float yaw, float pitch) {
        float dx = (float) (-Math.sin(yaw) * Math.cos(pitch)), dy = (float) Math.sin(pitch), dz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        Actor aimed = target(a, yaw, pitch, 18);
        switch (s) {
            case SPEAK_WITH_ANIMALS -> {
                if (!(aimed instanceof Animal an) || !an.alive) return no(a, "Look at an animal to speak with it.");
                an.trust.merge(a.id, 0.3f, (x, y) -> Math.min(1, x + y));
                an.fear = Math.max(0, an.fear - 0.5f);
                if (g.fx != null) g.fx.text("\"" + thought(an) + "\"", an.x, an.y + an.height + 0.4f, an.z, 0xFFC8F0C0);
                if (a == g.player) g.chat.say("The " + an.kind().toLowerCase() + " seems to say: " + thought(an));
                return true;
            }
            case SPIRITUAL_STEP -> {
                float horizontal = (float)Math.hypot(dx, dz);
                if (horizontal < 0.1f) return no(a, "Look toward a clear landing.");
                float ux = dx / horizontal, uz = dz / horizontal;
                float tx = a.x, tz = a.z, ty = a.y;
                for (float distance = 0.5f; distance <= 6; distance += 0.25f) {
                    float x = a.x + ux * distance, z = a.z + uz * distance;
                    float y = g.vox.groundY(x, a.y + 1.5f, z);
                    if (Math.abs(y - a.y) > 1.5f || !clearLanding(a, x, y, z)) break;
                    if (!g.vox.clear(a.x, a.chestY(), a.z, x, y + a.height * 0.6f, z)) break;
                    tx = x; ty = y; tz = z;
                }
                if (a.distTo(tx,tz) < 0.8f) return no(a, "There is no safe landing ahead.");
                g.chemistry.visual(2,a.x,a.chestY(),a.z,0.12f,100,0.25f,0.8f,1.5f,2.5f,5);
                g.teleport(a, tx, ty, tz);
                return true;
            }
            case GASEOUS_FORM -> { a.gaseousT = 10; return true; }
            case ENHANCE_ABILITY -> { a.enhanceT = 60; return true; }
            case FRIENDS -> {
                if (!(aimed instanceof Npc n) || !n.alive) return no(a, "Look at someone to befriend them.");
                if (n.hostileToPlayer && (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE)) return no(a, n.name + " will have none of it.");
                n.friendUntil = g.time + 120;
                g.ai.say(n, "Oh - it's you! Good to see you, friend.");
                return true;
            }
            case MINOR_ILLUSION -> {
                Voxels.Hit h = g.vox.ray(a.x, a.eyeY(), a.z, dx, dy, dz, 24, false);
                float t = h.hit ? h.dist : 24, px = a.x + dx * t, py = a.eyeY() + dy * t, pz = a.z + dz * t;
                // a sound with no maker: the engine's ears hear it, and the NPCs near go to see
                g.noise(px, py, pz, 12, 0.7f, 0);
                g.ai.stealth.illusion(px, py, pz, 12);
                if (g.sfx != null) g.sfx.play("wood", px, py, pz, 0.8f);
                return true;
            }
            case DARKNESS -> {
                Voxels.Hit h = g.vox.ray(a.x, a.eyeY(), a.z, dx, dy, dz, 16, false);
                float t = h.hit ? Math.max(0, h.dist - 0.5f) : 16;
                darkness.add(new Dark(a.x + dx * t, Math.max(DevMap.FLOOR + 1, a.eyeY() + dy * t), a.z + dz * t, 4.5f, g.time + 20));
                return true;
            }
            case DISGUISE_SELF -> { a.disguiseT = 60; return true; }
            case CHARM_PERSON -> {
                if (!(aimed instanceof Npc n) || !n.alive) return no(a, "Look at someone to charm them.");
                n.charmUntil = g.time + 30;
                if (n.fightTarget == a.id) { n.fightTarget = 0; n.cs.cancel(); n.nextThink = 0; }
                g.ai.say(n, "...why was I angry?");
                return true;
            }
            case VITALMEND -> {
                Actor ally = aimed != null && allied(a,aimed) ? aimed : a;
                var wounds = g.gore.of(ally);
                if (ally.hp >= ally.maxHp() && wounds.bleed <= 0) return no(a,"There is no wound to mend.");
                ally.hp = Math.min(ally.maxHp(), ally.hp + 8 + a.skills.level(rpg.skill.Skill.MAGIC) * 0.12f);
                wounds.bleed = 0; wounds.spurtUntil = g.time;
                g.chemistry.visual(2,ally.x,ally.chestY(),ally.z,0.4f,90,0.4f,1,0.5f,3,1.8f);
                return true;
            }
            case MORPHBEAST -> {
                if (aimed == null || !aimed.alive || aimed.dummy || aimed.reacting || aimed.carryBody != 0 || aimed.summonOwner != 0) return no(a,"Aim at a living target with room to transform.");
                aimed.morphT = 8; aimed.cs.cancel(); aimed.cs.blocking = false;
                g.interact.cancel(aimed,"transformed"); g.traversal.cancel(aimed);
                hostileEffect(a,aimed); form(aimed);
                return true;
            }
            case CHRONOSLOW -> {
                float x = aimed == null ? a.x + dx*7 : aimed.x, z = aimed == null ? a.z + dz*7 : aimed.z;
                boolean affected = false;
                for (Actor actor : g.actors()) if (actor.alive && !allied(a,actor) && actor.distTo(x,z) < 4 && g.vox.sight(a.x,a.eyeY(),a.z,actor.x,actor.chestY(),actor.z)) {
                    actor.chronoT = 6; hostileEffect(a,actor); affected = true;
                    g.chemistry.visual(2,actor.x,actor.chestY(),actor.z,0.35f,45,0.35f,1,2,1,4);
                }
                return affected || no(a,"No visible enemy is in that area.");
            }
            case FORCEWAVE -> {
                boolean affected = false;
                for (Actor actor : g.actors()) {
                    if (!actor.alive || allied(a,actor) || actor.dummy || actor.distTo(a) > 5) continue;
                    float x=actor.x-a.x,z=actor.z-a.z,d=Math.max(0.1f,actor.distTo(a));
                    if ((x*dx+z*dz)/d < 0.65f || !g.vox.sight(a.x,a.eyeY(),a.z,actor.x,actor.chestY(),actor.z)) continue;
                    hostileEffect(a,actor); actor.cs.cancel(); actor.cs.staggered = Math.max(actor.cs.staggered,0.7f);
                    if (actor instanceof Npc || actor == g.player) {
                        if (!actor.reacting) g.reactions.falling(actor,1);
                        g.game.commands().ragdollHit(actor.ragdoll,astratto.bridge.AstrattoNative.RD_PELVIS,x/d,0.15f,z/d,120,24);
                    } else if (actor.body != 0) g.game.commands().moverPlace(actor.body,actor.x,actor.y,actor.z,x/d*4,1,z/d*4);
                    affected = true;
                }
                g.chemistry.emit(a,astratto.bridge.AstrattoNative.CHEM_WIND,a.x,a.chestY(),a.z,5,3,0.3f);
                return affected || no(a,"No enemy is close enough.");
            }
            case NECROCALL -> {
                long count = g.npcs.stream().filter(n -> n.summonOwner == a.id && n.alive).count();
                if (count >= 2) return no(a,"You can sustain only two undead allies.");
                float x = a.x - (float)Math.sin(yaw)*1.8f, z = a.z - (float)Math.cos(yaw)*1.8f;
                if (!clearLanding(a,x,a.y,z)) return no(a,"There is no room for an ally there.");
                Npc ally = new Npc(g.state.newEntityId(),"Bound revenant",Npc.Role.HUNTER,"Conjured ally",x,z);
                ally.y=a.y; ally.look=a.look.copy(); ally.look.topColor=23; ally.look.bottomColor=23; ally.look.skin=9;
                ally.summonOwner=a.id; ally.summonUntil=g.time+30; ally.team=a.team; ally.hostileToPlayer=false;
                int level=Math.min(35,Math.max(8,a.skills.level(rpg.skill.Skill.MAGIC)/2));
                for (var skill : new rpg.skill.Skill[]{rpg.skill.Skill.ATTACK,rpg.skill.Skill.STRENGTH,rpg.skill.Skill.DEFENSE,rpg.skill.Skill.HEALTH}) ally.skills.setLevel(skill,level);
                ally.fitBody(); ally.restore(); g.npcs.add(ally); g.ai.spawnBody(ally);
                g.chemistry.visual(1,x,a.y+0.6f,z,0.35f,80,0.3f,0.8f,0.2f,0.28f,0.35f);
                return true;
            }
            default -> { return false; }
        }
    }

    public Spell combatSpell(Npc caster,Actor target) {
        Spell best=null;
        for(Spell spell:Spell.values()) {
            if(spell.book!=caster.spellBook || spell.level>caster.skills.level(rpg.skill.Skill.MAGIC) || spell.cost>caster.mana)continue;
            boolean useful=!spell.utility || spell==Spell.MORPHBEAST && target.morphT<=0 || spell==Spell.CHRONOSLOW && target.chronoT<=0
                    || spell==Spell.FORCEWAVE && caster.distTo(target)<4 || spell==Spell.NECROCALL && g.npcs.stream().filter(n->n.summonOwner==caster.id).count()<2;
            if(useful && g.combat.castRefusal(caster,spell)==null && (best==null || spell.level>best.level))best=spell;
        }
        return best;
    }

    public Actor target(Actor caster, float yaw, float pitch, float range) {
        float dx=(float)(-Math.sin(yaw)*Math.cos(pitch)), dy=(float)Math.sin(pitch), dz=(float)(-Math.cos(yaw)*Math.cos(pitch));
        Voxels.Hit wall=g.vox.ray(caster.x,caster.eyeY(),caster.z,dx,dy,dz,range,false);
        float nearest=wall.hit?wall.dist:range; Actor best=null;
        for (Actor actor : g.actors()) {
            if (actor==caster || !actor.alive || actor instanceof Npc npc && !npc.loaded) continue;
            float distance=HitVolumes.ray(g,actor,caster.x,caster.eyeY(),caster.z,dx,dy,dz,nearest,0.12f);
            if (distance<nearest) { nearest=distance; best=actor; }
        }
        return best;
    }
    private boolean clearLanding(Actor actor,float x,float y,float z) {
        for (float ox : new float[]{-actor.radius,actor.radius}) for (float oz : new float[]{-actor.radius,actor.radius})
            for (float h=0.1f;h<actor.height;h+=0.35f) if (g.vox.solid((int)Math.floor(x+ox),(int)Math.floor(y+h),(int)Math.floor(z+oz))) return false;
        for (Actor other:g.actors()) if(other!=actor && other.alive && other.distTo(x,z)<actor.radius+other.radius && Math.abs(other.y-y)<actor.height)return false;
        return g.vox.solid((int)Math.floor(x),(int)Math.floor(y-0.1f),(int)Math.floor(z));
    }
    public boolean allied(Actor a,Actor b) { return a==b || a.summonOwner==b.id || b.summonOwner==a.id || a.summonOwner!=0 && a.summonOwner==b.summonOwner || a.team!=0 && a.team==b.team && !g.ai.hostile(a,b); }
    private void hostileEffect(Actor a,Actor target) {
        g.ai.onAttacked(target,a); g.law.onHit(a,target,0);
        target.cs.lastAttacker=a.id; target.cs.lastHurt=g.time;
        if(target instanceof Animal animal)g.animals.struck(animal,0,0,1,false);
    }
    public Animal form(Actor actor) {
        Animal sheep=forms.computeIfAbsent(actor.id,id->new Animal(id,rpg.animal.Species.SHEEP,false,actor.look.female,actor.x,actor.z));
        sheep.x=actor.x; sheep.y=actor.y; sheep.z=actor.z; sheep.yaw=actor.yaw; sheep.vx=actor.vx; sheep.vz=actor.vz; sheep.grounded=actor.grounded; sheep.alive=actor.alive;
        return sheep;
    }
    public void dismiss(Npc ally) {
        forms.remove(ally.id);g.falling.reset(ally);
        g.carrying.drop(ally,false); g.reactions.cancel(ally); g.ai.despawnBody(ally);
        if (ally.ragdoll!=0)g.game.commands().release(ally.ragdoll);
        ally.ragdoll=0; ally.alive=false; g.npcs.remove(ally); g.gore.wounds.remove(ally.id);
        for (var limb : new java.util.ArrayList<>(g.gore.limbs)) if(limb.owner==ally.id) { g.game.commands().release(limb.body);g.gore.limbs.remove(limb); }
    }

    private boolean no(Actor a, String why) { if (a == g.player) g.chat.add(why, Chat.Kind.WARN); return false; }

    private static String thought(Animal an) {
        String r = an.reason == null || an.reason.isEmpty() ? "nothing much" : an.reason;
        return Character.toUpperCase(r.charAt(0)) + r.substring(1) + ".";
    }

    /** How much darker a point is for a darkness spell over it (1 none .. 0.1 deep in a globe). */
    public float dim(float x, float y, float z) {
        float k = 1;
        for (Dark d : darkness) {
            float dd = (float) Math.sqrt((x - d.x) * (x - d.x) + (y - d.y) * (y - d.y) + (z - d.z) * (z - d.z));
            if (dd < d.r) k = Math.min(k, 0.1f + 0.9f * dd / d.r);
        }
        return k;
    }

    public void update(float dt) {
        darkness.removeIf(d -> g.time > d.until);
        for (Npc ally : new java.util.ArrayList<>(g.npcs)) if (ally.summonOwner!=0 && (g.time>ally.summonUntil || g.actor(ally.summonOwner)==null || !g.actor(ally.summonOwner).alive)) dismiss(ally);
        for (Actor a : g.actors()) {
            if (a.morphT>0) { a.morphT=Math.max(0,a.morphT-dt); if(a.morphT==0) { forms.remove(a.id); a.poseReset=true; if(a instanceof Npc n)n.nextThink=0; } }
            a.chronoT=Math.max(0,a.chronoT-dt);
            if (a.gaseousT > 0) a.gaseousT = Math.max(0, a.gaseousT - dt);
            if (a.enhanceT > 0) a.enhanceT = Math.max(0, a.enhanceT - dt);
            if (a.disguiseT > 0) { a.disguiseT = Math.max(0, a.disguiseT - dt); if (a.disguiseT == 0 && a instanceof Player) g.chat.say("Your disguise fades."); }
        }
    }
}
