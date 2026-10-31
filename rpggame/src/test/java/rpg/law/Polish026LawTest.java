package rpg.law;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.*;
import rpg.item.*;
import rpg.world.*;

class Polish026LawTest {
    Game game;Rpg g;Npc officer,witness;
    @BeforeEach void open(){
        var config=new NativeEngine.Config();config.audioDevice=false;config.deterministic=true;
        game=Game.create(config);game.activate(DevMap.build(game.engine()));g=game.add(new Rpg(game,"Review",new Appearance(),true));g.state.minutes=600;
        for(var npc:g.npcs){g.ai.unload(npc);npc.memories.clear();}
        officer=place(Ids.LAWMAN_A,100.5f,70.5f);witness=place(Ids.VILLAGER,102.5f,70.5f);
        g.teleport(g.player,101.5f,DevMap.FLOOR,71.5f);game.tick(0);
        var coins=new ItemStack(Items.COINS,1000);assertTrue(g.player.store(coins));g.state.mint(Items.COINS,1000);
    }
    Npc place(long id,float x,float z){var n=(Npc)g.actor(id);g.ai.reload(n);g.teleport(n,x,DevMap.FLOOR,z);n.nextThink=Double.MAX_VALUE;n.goal=Brain.Goal.IDLE;n.fightTarget=0;return n;}
    Memory evidence(Npc n,long event){var m=new Memory("THEFT",g.player.id,officer.id,DevMap.ALPHA,g.state.minutes,1,"witnessed",event,0,"theft");m.witness=n.id;n.remember(m,24);return m;}
    @AfterEach void close(){game.close();}
    @Test void paymentClosesOnlyItsKingdomAndCannotChargeTwice(){
        evidence(officer,770);g.law.setBounty(g.player,DevMap.ALPHA,125);g.law.setBounty(g.player,DevMap.BETA,200);
        // A fresh report identifies the crime paid for after the developer setup closed its earlier case.
        evidence(officer,771);int before=g.player.count(Items.COINS),received=officer.count(Items.COINS),honor=g.honor.score;
        long request=g.state.newRequestId();assertTrue(g.law.payBounty(g.player,officer,request).committed());
        assertEquals(before-125,g.player.count(Items.COINS));assertEquals(received+125,officer.count(Items.COINS));assertEquals(0,g.law.bounty(g.player.id,DevMap.ALPHA));assertEquals(200,g.law.bounty(g.player.id,DevMap.BETA));assertTrue(g.honor.score>honor);assertTrue(officer.speech.toLowerCase().contains("theft"));
        assertTrue(g.law.payBounty(g.player,officer,request).rejected());assertEquals(before-125,g.player.count(Items.COINS));assertFalse(g.law.arrestInsteadOfKill(officer,g.player));
    }
    @Test void explicitDefiancePersistsWhilePettyGraceKeepsPayment(@TempDir Path temp){
        g.law.setBounty(g.player,DevMap.ALPHA,300);g.law.resist(g.player,officer);
        assertTrue(g.law.refusesPeace(g.player.id,DevMap.ALPHA));assertTrue(g.law.payBounty(g.player,officer,g.state.newRequestId()).rejected());assertFalse(g.law.arrestInsteadOfKill(officer,g.player));
        var save=new SaveSystem(g);assertTrue(save.save(temp.resolve("case.json")));g.law.clear();assertTrue(save.load(temp.resolve("case.json")));assertTrue(g.law.refusesPeace(g.player.id,DevMap.ALPHA));assertTrue(g.law.resisting(g.player.id,DevMap.ALPHA));assertFalse(g.law.resisting(g.player.id,DevMap.BETA));
        g.law.setBounty(g.player,DevMap.ALPHA,10);g.law.resist(g.player,officer);assertFalse(g.law.refusesPeace(g.player.id,DevMap.ALPHA));assertTrue(g.law.payBounty(g.player,officer,g.state.newRequestId()).committed());
    }
    @Test void rumorsAndSomeoneElsesEyewitnessMemoryCannotBecomePersonalEvidence(){
        var original=evidence(witness,881);
        var rumor=original.rumorFrom(witness);officer.remember(rumor,24);
        assertTrue(g.law.report(officer,rumor,officer).rejected());
        assertTrue(g.law.report(officer,original,officer).rejected());
        assertTrue(g.law.report(witness,original,witness).rejected());
        assertTrue(g.law.report(witness,original,officer).committed());
    }
    @Test void oneAppealBelongsToOneWitnessAndOneIncident(){
        var memory=evidence(witness,991);witness.goal=Brain.Goal.REPORT_CRIME;
        assertTrue(WitnessInfluence.attempt(g,witness,g.player,"witness_persuade",g.state.newRequestId()).committed());assertTrue(memory.influenceTried);
        assertTrue(WitnessInfluence.attempt(g,witness,g.player,"witness_persuade",g.state.newRequestId()).rejected());
        assertTrue(Memory.load(memory.save()).influenceTried);
        var other=evidence(officer,991);officer.goal=Brain.Goal.REPORT_CRIME;assertSame(other,WitnessInfluence.pending(g,officer));
        var next=evidence(witness,992);witness.goal=Brain.Goal.REPORT_CRIME;assertSame(next,WitnessInfluence.pending(g,witness));
    }
    @Test void aPrincipledWitnessReportsTheBribeWithoutTakingIt(){
        var memory=evidence(officer,663);officer.goal=Brain.Goal.REPORT_CRIME;
        int coins=g.player.count(Items.COINS);assertTrue(WitnessInfluence.attempt(g,officer,g.player,"witness_bribe",g.state.newRequestId()).committed());
        assertTrue(memory.influenceTried);assertFalse(memory.withheld);assertEquals(coins,g.player.count(Items.COINS));assertTrue(officer.memories.stream().anyMatch(m->m.kind.equals("BRIBERY") && m.witness==officer.id));
    }
    @Test void retainedNamesAndDamageSurviveOrdinaryEdits(@TempDir Path temp){
        g.rememberName(991234,"Arthur");assertEquals("Arthur",g.nameOf(991234));assertEquals("someone",g.nameOf(991235));
        int x=103,z=73,y=DevMap.FLOOR+1;g.setBlock(x,y,z,Blocks.FENCE);game.tick(0);g.setBlock(x,y,z,Blocks.AIR);game.tick(0);
        assertTrue(g.repairs.open().stream().anyMatch(d->d.x()==x&&d.z()==z&&d.original()==Blocks.FENCE));
        var save=new SaveSystem(g);assertTrue(save.save(temp.resolve("world.json")));g.rememberedNames.clear();assertTrue(save.load(temp.resolve("world.json")));assertEquals("Arthur",g.nameOf(991234));
        assertTrue(g.invariants.check().isEmpty(),g.invariants.check().toString());
    }

    @Test void aFullGuardPurseAcceptsPaymentWithoutRequiringAnotherInventoryCell(){
        officer.inv.clear();officer.eq.clear();
        assertTrue(officer.store(new ItemStack(Items.COINS,10,0,false,0)));
        for(int i=1;i<officer.inv.w*officer.inv.h;i++)assertTrue(officer.store(new ItemStack(Items.COINS,1,90000+i,false,0)));
        assertFalse(officer.canStore(new ItemStack(Items.COINS,5,officer.id,false,0)));
        g.law.setBounty(g.player,DevMap.ALPHA,25);int before=officer.count(Items.COINS);
        assertTrue(g.law.payBounty(g.player,officer,g.state.newRequestId()).committed());assertEquals(before+25,officer.count(Items.COINS));
    }
    @Test void witnessAppealsAreConversationRepliesEvenWhenTheWitnessIsHiding(){
        witness.goal=Brain.Goal.HIDE;evidence(witness,9911);
        var options=witness.options(g,g.player);assertTrue(options.stream().anyMatch(o->o.id().equals("talk")));
        assertFalse(options.stream().anyMatch(o->o.id().startsWith("witness_")));
        var dialog=g.dialogue.talk(witness);assertTrue(dialog.stands(g));assertEquals(witness.id,dialog.npc);
        assertTrue(dialog.choices.stream().anyMatch(c->c.label().contains("don't report")));
    }
    @Test void aLevelEarnedOnTheFatalTickWaitsUntilRespawnAndSurvivesSaving(@TempDir Path temp){
        g.player.hp=0;g.player.alive=false;g.death.active=false;
        g.prog.awardRaw(g.player,rpg.skill.Skill.DEFENSE,100,"fatal defence",0,0);g.time+=1;g.prog.update();
        assertNull(g.prog.banner);assertTrue(g.prog.drops.isEmpty());assertEquals(0,g.player.hp);
        var rows=g.prog.save();assertFalse(rows.isEmpty());g.prog.load(rows);
        g.player.alive=true;g.player.restore();g.time+=1;g.prog.update();assertNotNull(g.prog.banner);
    }
    @Test void repeatedRoutineSpeechCannotRefreshTheSameBubbleEveryFrame(){
        g.ai.say(witness,"Mind the road.");double until=witness.speechUntil;g.time+=.1;g.ai.say(witness,"Mind the road.");assertEquals(until,witness.speechUntil);
        g.ai.reply(witness,"I heard you.");assertEquals("I heard you.",witness.speech);
    }
}
