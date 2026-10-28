package rpg.combat;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import rpg.*;
import rpg.actor.*;
import rpg.ai.Npc;
import rpg.item.*;
import rpg.skill.Skill;
import rpg.world.*;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseSystemsTest {
    private Game game; private Rpg g;
    @BeforeEach void start() {
        var config=new NativeEngine.Config(); config.deterministic=true;
        game=Game.create(config); game.activate(DevMap.build(game.engine()));
        g=game.add(new Rpg(game,"Release checks",Appearance.henric(),true));
        for(Npc n:g.npcs)g.ai.unload(n);
        for(int x=95;x<109;x++)for(int z=62;z<82;z++)for(int y=DevMap.FLOOR;y<35;y++)g.setBlock(x,y,z,Blocks.AIR);
        g.player.skills.setLevel(Skill.HEALTH,100);g.player.skills.setLevel(Skill.MAGIC,100);g.player.restore();
        g.teleport(g.player,100.5f,DevMap.FLOOR,72.5f);game.tick(0);
    }
    @AfterEach void close(){game.close();}
    private Npc target(long id,float x,float z) {
        Npc target=(Npc)g.actor(id);g.ai.reload(target);g.teleport(target,x,DevMap.FLOOR,z);target.nextThink=Double.MAX_VALUE;game.tick(0);return target;
    }
    private float pitch(Actor caster,Actor target){return (float)Math.atan2(target.chestY()-caster.eyeY(),caster.distTo(target));}
    @Test void realLongFallHandsThePlayerToSydneyAndChargesOnlyOneLanding() {
        g.teleport(g.player,100.5f,22,72.5f);
        for(int i=0;i<300;i++)assertTrue(game.tick(1f/60));
        assertTrue(g.reactions.started>0,"a long fall must transfer physical ownership");
        var damage=g.state.all().stream().filter(e->e.type().equals("EnvironmentalDamage")&&e.subject()==g.player.id).toList();
        assertEquals(1,damage.size());assertTrue(g.player.hp<95);assertTrue(g.player.alive);
        assertTrue(g.player.y<8,"the body must land on actual ground");
    }
    @Test void anUnattributedLethalNpcFallMakesOneCorpseWithoutInventingAMurderer() {
        Npc n=target(Ids.VILLAGER,103.5f,70.5f);n.hp=1;
        g.teleport(n,103.5f,20,70.5f);
        for(int i=0;i<240&&n.alive;i++)game.tick(1f/60);
        assertFalse(n.alive);assertNotEquals(0,n.corpse);assertNotEquals(0,n.ragdoll);
        assertEquals(0,g.law.bounty(g.player.id,DevMap.ALPHA));
    }
    @Test void transformationUsesCasterAimAndRetainsIdentitySkillsAndBelongings(@TempDir Path directory) {
        Npc n=target(Ids.VILLAGER,100.5f,68.5f);
        n.inv.add(new ItemStack(Items.BREAD,2,n.id,true,991));var xp=n.skills.raw().clone();
        g.player.book=Spells.Book.MOON;
        assertTrue(g.combat.cast(g.player,Spells.Spell.MORPHBEAST,0,pitch(g.player,n)));
        assertEquals(8,n.morphT);assertFalse(g.combat.attack(n,false,Actor.Combat.Move.PUNCH));
        assertArrayEquals(xp,n.skills.raw());assertTrue(n.inv.stacks().stream().anyMatch(s->s.origin==991&&s.stolen));
        var saves=new SaveSystem(g);var file=directory.resolve("morph.json");assertTrue(saves.save(file));assertTrue(saves.load(file));
        assertEquals(8,n.morphT);g.magic.update(8.1f);assertEquals(0,n.morphT);assertArrayEquals(xp,n.skills.raw());
    }
    @Test void npcHealingUsesItsOwnAimAndNeverAwardsNpcExperience() {
        Npc healer=target(Ids.HEALER,100.5f,72.5f), patient=target(Ids.GUARD,100.5f,68.5f);patient.hp=1;g.teleport(g.player,96.5f,DevMap.FLOOR,74.5f);game.tick(0);
        var xp=healer.skills.raw().clone();float before=g.player.hp;
        assertTrue(g.combat.cast(healer,Spells.Spell.VITALMEND,0,pitch(healer,patient)));
        assertTrue(patient.hp>1);assertEquals(before,g.player.hp);assertArrayEquals(xp,healer.skills.raw());
    }
    @Test void summonsAreBoundedPersistTheirOwnerAndLeaveNoLootOrExperience(@TempDir Path directory) {
        g.player.book=Spells.Book.FOGISH;
        assertTrue(g.combat.cast(g.player,Spells.Spell.NECROCALL,0,0));
        g.player.cs.castCooldown=0;
        assertTrue(g.combat.cast(g.player,Spells.Spell.NECROCALL,(float)Math.PI/2,0));
        g.player.cs.castCooldown=0;float mana=g.player.mana;
        assertFalse(g.combat.cast(g.player,Spells.Spell.NECROCALL,(float)Math.PI,0));assertEquals(mana,g.player.mana);
        var saves=new SaveSystem(g);var file=directory.resolve("summons.json");assertTrue(saves.save(file));assertTrue(saves.load(file));game.tick(0);
        var allies=g.npcs.stream().filter(n->n.summonOwner==g.player.id).toList();assertEquals(2,allies.size());
        assertTrue(allies.stream().allMatch(n->n.team==g.player.team&&n.inv.isEmpty()&&n.eq.stacks().isEmpty()));
        int bodies=g.objects.all().size();for(Npc ally:allies)g.combat.bleedOutNow(ally);
        assertEquals(bodies,g.objects.all().size());assertTrue(g.npcs.stream().noneMatch(n->n.summonOwner!=0));
    }
    @Test void agilityPracticeRequiresRealTravelAndDoesNotRewardRepeatedLandingSpots() {
        long before=g.player.skills.xp(Skill.AGILITY);g.player.sprinting=true;
        g.movementPractice.sprintCost(10);for(int i=0;i<100;i++)g.movementPractice.moved(0);
        assertEquals(before,g.player.skills.xp(Skill.AGILITY));
        for(int i=0;i<41;i++)g.movementPractice.moved(0.5f);
        assertTrue(g.player.skills.xp(Skill.AGILITY)>before);
        g.player.sprinting=false;g.player.grounded=true;long traveled=g.player.skills.xp(Skill.AGILITY);
        g.movementPractice.jumped();g.movementPractice.moved(0);assertEquals(traveled,g.player.skills.xp(Skill.AGILITY));
        g.movementPractice.jumped();g.player.x+=3;g.movementPractice.moved(0);long first=g.player.skills.xp(Skill.AGILITY);assertTrue(first>traveled);
        g.time+=9;g.player.x-=3;g.movementPractice.jumped();g.player.x+=3;g.movementPractice.moved(0);assertEquals(first,g.player.skills.xp(Skill.AGILITY));
    }
    @Test void attributedElementalHarmUsesMagicProtectionAndExperienceRatherThanMelee() {
        Npc victim=target(Ids.VILLAGER,100.5f,68.5f);victim.eq.clear();var npcXp=victim.skills.raw().clone();
        long attack=g.player.skills.xp(Skill.ATTACK),strength=g.player.skills.xp(Skill.STRENGTH);
        g.player.skills.setLevel(Skill.MAGIC,50);long magic=g.player.skills.xp(Skill.MAGIC);
        g.combat.resolve(g.player,victim,2,0,ItemDef.DamageType.MAGIC,null,victim.x,victim.chestY(),victim.z,"burning",false);
        assertEquals(attack,g.player.skills.xp(Skill.ATTACK));assertEquals(strength,g.player.skills.xp(Skill.STRENGTH));
        assertTrue(g.player.skills.xp(Skill.MAGIC)>magic);assertArrayEquals(npcXp,victim.skills.raw());
    }
    @Test void phaseStepStopsBeforeAFullHeightWall() {
        g.player.book=Spells.Book.MOON;
        for(int x=99;x<=101;x++)for(int y=DevMap.FLOOR;y<DevMap.FLOOR+4;y++)g.setBlock(x,y,70,Blocks.STONE);
        game.tick(0);
        g.player.aimYaw=0;g.player.pitch=0;
        var plan=g.player.throwPlan;plan.solve(g,g.player,new float[]{100.5f,5.5f,72.5f},new float[]{100.5f,5.6f,72.5f},20,ThrowSolver.gravityFor("javelin"),true);
        assertTrue(plan.landing[2]>=71f && plan.landing[2]<71.3f,"throw prediction must end on the near face of the same wall: "+plan.landing[2]);
        assertTrue(g.combat.cast(g.player,Spells.Spell.SPIRITUAL_STEP,0,0));
        assertTrue(g.player.z>71,"the capsule must remain in front of the wall");
    }
}
