package rpg.combat;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.*;
import rpg.ai.*;
import rpg.item.*;
import rpg.objects.Container;
import rpg.skill.Skill;
import rpg.world.*;
import static astratto.bridge.AstrattoNative.*;
import static org.junit.jupiter.api.Assertions.*;

class RequestedFixesTest {
    private Game game;
    private Rpg g;
    @BeforeEach void start() {
        var config=new NativeEngine.Config();config.deterministic=true;
        game=Game.create(config);game.activate(DevMap.build(game.engine()));
        g=game.add(new Rpg(game,"Requested fixes",Appearance.henric(),true));
        for(Npc n:g.npcs)g.ai.unload(n);
        g.state.minutes=600;g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();
        g.teleport(g.player,60.5f,DevMap.FLOOR,70.5f);
        g.player.skills.setLevel(Skill.HEALTH,100);g.player.restore();game.tick(0);
    }
    @AfterEach void close(){game.close();}
    private void frames(int n){for(int i=0;i<n;i++)assertTrue(game.tick(1f/60));}
    private Npc teller() {
        Npc t=(Npc)g.actor(Ids.TELLER);g.ai.reload(t);g.teleport(t,DevMap.BANK_TELLER[0],DevMap.FLOOR,DevMap.BANK_TELLER[1]);
        t.goal=Brain.Goal.WORK;t.nextThink=Double.MAX_VALUE;
        float[] spot=g.bank.customerSpot();g.teleport(g.player,spot[0],DevMap.FLOOR,spot[1]);return t;
    }
    @Test void feesFailWithoutChangingGoldOrVaultAndOffDutyTellerHasNoBankChoices() {
        Npc teller=teller();g.player.takeItems(Items.COINS,g.player.count(Items.COINS));g.give(g.player,Items.COINS,24);
        var denied=g.bank.openAccount(g.player,g.state.newRequestId());
        assertEquals("You don't have enough gold to open an account with us.",denied.reason());
        assertFalse(g.bank.hasAccount(g.player.id));assertEquals(24,g.player.count(Items.COINS));
        g.give(g.player,Items.COINS,1);assertTrue(g.bank.openAccount(g.player,g.state.newRequestId()).committed());
        assertEquals(0,g.player.count(Items.COINS));var vault=g.bank.vaults(g.player.id).get(0);
        assertEquals("You don't have enough gold for more bank space.",g.bank.buyVault(g.player,g.state.newRequestId()).reason());
        assertEquals(80,vault.contents.size());
        teller.goal=Brain.Goal.SOCIALIZE;
        assertEquals(Npc.EmploymentState.FREE,teller.employmentState());assertFalse(g.bank.open());
        assertTrue(g.dialogue.talk(teller).choices.stream().noneMatch(c->c.label().contains("account")||c.label().contains("vault")||c.label().contains("bank work")));
    }
    @Test void oldVaultsMergeWithoutLosingItemPlacementOrProvenance() {
        Container first=g.objects.add(new Container(g.state.newEntityId(),"bank_vault","old1","vault",40,0,40,40,g.player.id));
        Container second=g.objects.add(new Container(g.state.newEntityId(),"bank_vault","old2","vault",40,0,40,40,g.player.id));
        ItemStack blade=new ItemStack(Items.get("iron_sword"),1,Ids.VILLAGER,true,1234);
        assertTrue(second.contents.putAt(blade,2,0,false));
        g.bank.migrateAccounts();assertEquals(1,g.bank.vaults(g.player.id).size());assertEquals(80,first.contents.size());
        ItemStack moved=first.contents.stacks().get(0);assertEquals(1234,moved.origin);assertTrue(moved.stolen);assertTrue(second.contents.isEmpty());
        assertEquals(4,first.contents.y(first.contents.first(blade.def)));
    }
    @Test void healingExtinguishesWithoutSaveReloadAndAllowsLaterFreshFire() {
        g.chemistry.emit(null,CHEM_FIRE,g.player.x,g.player.chestY(),g.player.z,0.6f,4,0.2f);frames(60);
        assertNotEquals(0,g.chemistry.state(g.player.id)&CHEM_BURNING);
        g.player.restore();g.chemistry.cleanse(g.player);frames(120);
        assertEquals(0,g.chemistry.state(g.player.id)&CHEM_BURNING);assertEquals(100,g.player.hp);
        g.chemistry.emit(null,CHEM_FIRE,g.player.x,g.player.chestY(),g.player.z,0.6f,4,0.2f);frames(40);
        assertNotEquals(0,g.chemistry.state(g.player.id)&CHEM_BURNING,"fresh environmental fire still applies");
    }
    @Test void aChargePhysicallyCollapsesThePlayerAndNpcThenLetsThemRecover() {
        Npc n=(Npc)g.actor(Ids.GUARD);g.ai.reload(n);g.teleport(n,63.5f,DevMap.FLOOR,70.5f);n.nextThink=Double.MAX_VALUE;
        long[] levels=n.skills.raw().clone();
        for(Actor actor:new Actor[]{g.player,n})g.chemistry.emit(null,CHEM_ELECTRIC,actor.x,actor.chestY(),actor.z,0.6f,3,0.2f);
        frames(35);assertTrue(g.player.reacting);assertTrue(n.reacting);assertNotEquals(0,g.player.ragdoll);assertNotEquals(0,n.ragdoll);
        assertTrue(g.player.lastSpasm>=0);assertTrue(n.lastSpasm>=0);assertArrayEquals(levels,n.skills.raw());
        frames(900);assertFalse(g.player.reacting,"the player can regain control after the charge expires");
    }
    @Test void anArrowHasANativeFlightBodyAndAContinuousContactStopsIt() {
        g.teleport(g.player,64.5f,DevMap.FLOOR,70.5f);
        for(int y=DevMap.FLOOR;y<DevMap.FLOOR+4;y++)g.setBlock(64,y,64,Blocks.STONE);
        g.player.eq.set(ItemDef.Slot.MAIN,new ItemStack(Items.SHORTBOW,1));g.player.eq.set(ItemDef.Slot.AMMO,new ItemStack(Items.BRONZE_ARROWS,3));
        assertTrue(g.combat.shoot(g.player,0,0,1));var shot=g.combat.projectiles.get(0);
        frames(3);assertNotEquals(0,shot.rigid);assertNotNull(g.lastRigids.body(shot.rigid));assertTrue(shot.z<70,"shot at "+shot.x+","+shot.y+","+shot.z+" v="+shot.vx+","+shot.vy+","+shot.vz);
        frames(30);assertTrue(shot.done);assertEquals(0,shot.rigid,"native body released after world impact");
        assertTrue(g.combat.projectiles.isEmpty());
    }
    @Test void spellsStartAtThePosedHandAndAnimateWithoutAStaff() {
        g.player.skills.setLevel(Skill.MAGIC,100);g.player.restore();g.player.eq.clear();
        g.player.hand[0]=g.player.x+0.3f;g.player.hand[1]=g.player.chestY()+0.1f;g.player.hand[2]=g.player.z-0.4f;g.player.handAt=g.time;
        float mana=g.player.mana;
        assertTrue(g.combat.cast(g.player,Spells.Spell.FIRE_BOLT,0,0));
        var spell=g.combat.projectiles.get(0);
        assertEquals(g.player.hand[0],spell.x);assertEquals(g.player.hand[1],spell.y);assertEquals(g.player.hand[2],spell.z);
        assertTrue(g.player.mana<mana);assertEquals(g.time,g.player.castAt);
        var rig=new rpg.render.Rig();var idle=new rpg.render.Rig.State();
        g.player.castAt=-99;rig.pose(g.player,idle,1f/60,g.time,false);float[] resting=rig.hand[0].clone();
        g.player.castAt=g.time;rig.pose(g.player,idle,1f/60,g.time+0.2,false);
        assertTrue(Math.abs(rig.hand[0][0]-resting[0])+Math.abs(rig.hand[0][1]-resting[1])+Math.abs(rig.hand[0][2]-resting[2])>0.5f);
    }
    @Test void lawmenSearchCredibleDisturbancesWithoutFetchingAnotherGuard() {
        Npc officer=(Npc)g.actor(Ids.LAWMAN_A);g.ai.reload(officer);g.teleport(officer,64.5f,DevMap.FLOOR,70.5f);
        officer.hunt=null;officer.fightTarget=0;officer.goal=Brain.Goal.IDLE;officer.memories.clear();
        var noise=new Stealth.Aware();noise.level=0.65f;noise.heardAt=g.time;noise.x=70.5f;noise.z=70.5f;
        officer.aware.put(g.player.id,noise);
        g.ai.brain.think(officer);g.ai.brain.act(officer,1f/60);
        assertTrue(officer.combatant());assertNotNull(officer.investigation);assertEquals(Investigation.Plan.SEARCH,officer.investigation.plan);
    }
    @Test void headAttachmentsFollowTheArticulatedHeadInsteadOfTheRootCapsule() {
        Actor actor=g.player;actor.posePts=new float[RD_PARTICLES*3];actor.poseAt=g.time;actor.poseReset=false;
        for(int i=0;i<RD_PARTICLES;i++){actor.posePts[i*3]=actor.x;actor.posePts[i*3+1]=actor.y+0.5f;actor.posePts[i*3+2]=actor.z;}
        actor.posePts[RD_NECK*3+1]=actor.y+1.35f;actor.posePts[RD_HEAD*3+1]=actor.y+1.65f;
        var stuck=new CombatSystem.Stuck(new ItemStack(Items.BRONZE_ARROWS,1),0,0.2f,0,0,0,1,g.time,RD_NECK,RD_HEAD);
        float[] before=stuck.world(g,actor);actor.posePts[RD_NECK*3]+=0.5f;actor.posePts[RD_HEAD*3]+=0.5f;
        assertEquals(before[0]+0.5f,stuck.world(g,actor)[0],0.0001f);
        assertTrue(Float.isInfinite(HitVolumes.ray(g,actor,actor.x,actor.y+1.6f,actor.z-2,0,0,1,4,0)),"the old head position has no hit volume");
    }
}
