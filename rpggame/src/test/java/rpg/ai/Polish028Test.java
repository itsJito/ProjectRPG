package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.*;
import rpg.item.*;
import rpg.world.*;
import static org.junit.jupiter.api.Assertions.*;

class Polish028Test {
    Game game; Rpg g; Npc officer,witness,beggar;
    @BeforeEach void open() {
        var cfg=new NativeEngine.Config();cfg.audioDevice=false;cfg.deterministic=true;
        game=Game.create(cfg);game.activate(DevMap.build(game.engine()));g=game.add(new Rpg(game,"Regression",new Appearance(),true));
        g.state.minutes=600;
        for(var n:g.npcs){g.ai.unload(n);n.memories.clear();}
        officer=place(Ids.LAWMAN_A,100.5f);witness=place(Ids.VILLAGER,102.5f);beggar=place(Ids.BEGGAR,101.5f);
        g.teleport(g.player,101.5f,DevMap.FLOOR,71.5f);game.tick(0);
    }
    Npc place(long id,float x) {
        var n=(Npc)g.actor(id);g.ai.reload(n);g.teleport(n,x,DevMap.FLOOR,70.5f);
        n.goal=Brain.Goal.IDLE;n.nextThink=Double.MAX_VALUE;n.fightTarget=0;n.cs.lastHurt=-99;
        return n;
    }
    Memory crime(Npc n,long event,double minutes) {
        var m=new Memory("THEFT",g.player.id,officer.id,DevMap.ALPHA,minutes,1,"witnessed",event,8,"a stolen loaf");
        m.witness=n.id;n.remember(m,24);return m;
    }
    void give(ItemDef def,int amount){assertTrue(g.player.store(new ItemStack(def,amount)));g.state.mint(def,amount);}
    @AfterEach void close(){game.close();}

    @Test void anotherEyewitnessDisappearsFromPendingNoticesOnceTheIncidentIsReported() {
        var first=crime(witness,28001,600);var second=crime(beggar,28001,600);
        assertTrue(g.law.awaitingReport(first));assertTrue(g.law.awaitingReport(second));
        assertTrue(g.law.report(witness,first,officer).committed());
        assertFalse(g.law.awaitingReport(second));
        beggar.goal=Brain.Goal.REPORT_CRIME;assertNull(rpg.law.WitnessInfluence.pending(g,beggar));
        var saved=g.law.save();g.law.load(saved);assertFalse(g.law.awaitingReport(second));
    }
    @Test void withheldAndSettledMemoriesRemainHistoryWithoutPendingWitnessNotices() {
        var m=crime(witness,28002,600);m.withheld=true;assertFalse(g.law.awaitingReport(m));
        m.withheld=false;m.settled=true;assertFalse(g.law.awaitingReport(m));
        assertNotNull(witness.crimeKnown(g.player.id));
    }
    @Test void fourteenGoldOnTheBooksOverridesAnOlderSettledGreeting() {
        var old=crime(officer,28003,590);g.law.setBounty(g.player,DevMap.ALPHA,14);old.settled=true;
        var dialog=g.dialogue.talk(officer);assertTrue(dialog.text.contains("14"));assertFalse(dialog.text.contains("is settled"));
        assertTrue(dialog.choices.stream().anyMatch(c->c.label().contains("14")));
        give(Items.COINS,14);assertTrue(g.law.payBounty(g.player,officer,g.state.newRequestId()).committed());
        assertEquals(0,g.law.bounty(g.player.id,DevMap.ALPHA));assertFalse(g.law.awaitingReport(old));
    }
    @Test void legacyIncidentsHaveSeparateChargesAndSurviveLoading() {
        var first=crime(witness,0,590);var next=crime(witness,0,591);
        assertTrue(g.law.report(witness,first,officer).committed());int before=g.law.bounty(g.player.id,DevMap.ALPHA);
        assertTrue(g.law.report(witness,next,officer).committed());assertTrue(g.law.bounty(g.player.id,DevMap.ALPHA)>before);
        var copy=crime(beggar,0,590);var saved=g.law.save();g.law.load(saved);assertFalse(g.law.awaitingReport(copy));
    }
    @Test void aGiftIsContextualTransfersOneRealLoafAndCannotReplay() {
        g.player.takeItems(Items.BREAD,g.player.count(Items.BREAD));
        assertFalse(g.dialogue.talk(beggar).choices.stream().anyMatch(c->c.label().contains("loaf")));
        give(Items.BREAD,2);int carried=beggar.count(Items.BREAD),before=g.player.count(Items.BREAD);float hunger=beggar.hunger;
        assertTrue(g.dialogue.talk(beggar).choices.stream().anyMatch(c->c.label().contains("loaf")));
        long request=g.state.newRequestId();assertTrue(g.interact.request(g.player,beggar,"give_bread",request).committed());
        assertEquals(before-1,g.player.count(Items.BREAD));assertEquals(carried+1,beggar.count(Items.BREAD));
        assertEquals(hunger,beggar.hunger,"the recipient eats through the shared needs system");
        assertTrue(g.interact.request(g.player,beggar,"give_bread",request).rejected());assertEquals(before-1,g.player.count(Items.BREAD));
    }
    @Test void giftsRevalidateDistanceReadinessAndRecipientSpace() {
        give(Items.BREAD,1);int before=g.player.count(Items.BREAD);
        g.teleport(g.player,30,4,30);assertTrue(g.interact.request(g.player,beggar,"give_bread").rejected());
        g.teleport(g.player,101.5f,4,71.5f);beggar.goal=Brain.Goal.FLEE;assertTrue(g.interact.request(g.player,beggar,"give_bread").rejected());
        beggar.goal=Brain.Goal.IDLE;beggar.inv.clear();beggar.eq.clear();
        for(int i=0;i<beggar.inv.w*beggar.inv.h;i++)assertTrue(beggar.store(new ItemStack(Items.COINS,1,90000+i,false,0)));
        assertTrue(g.interact.request(g.player,beggar,"give_bread").rejected());assertEquals(before,g.player.count(Items.BREAD));
    }
    @Test void strongerExistingEvidenceKeepsNewAppealStateAndLearnsCaseClosure() {
        var original=crime(witness,28004,600);var rumor=original.rumorFrom(officer);rumor.influenceTried=true;rumor.withheld=true;
        witness.remember(rumor,24);assertTrue(original.influenceTried);assertTrue(original.withheld);assertTrue(original.witnessed());
        var closed=crime(beggar,28004,600);closed.settled=true;
        assertTrue(g.ai.rumors.tell(beggar,witness,closed,false));assertTrue(original.settled);assertTrue(original.witnessed());
        assertTrue(g.ai.rumors.line(witness,original).contains("case is closed"));
    }
    @Test void staleConversationRepliesCannotOperateAcrossWallsOrAfterWalkingAway() {
        var dialog=g.dialogue.talk(beggar);assertTrue(dialog.stands(g));
        g.teleport(g.player,30,4,30);assertFalse(dialog.stands(g));
        g.teleport(g.player,101.5f,4,72.5f);g.setBlock(101,5,71,Blocks.STONE);game.tick(0);assertFalse(dialog.stands(g));
    }
    @Test void treeRootsStayClearAndIvyRequiresItsWholeBackingWall() {
        for(int x=104;x<=108;x++)for(int z=80;z<=84;z++)for(int y=4;y<=7;y++)g.setBlock(x,y,z,Blocks.AIR);
        g.setBlock(106,4,82,Blocks.OAK_LOG);g.setBlock(105,3,82,Blocks.GRASS);game.tick(0);
        for(var kind:rpg.world.Foliage.Kind.values())assertFalse(g.foliage.suitable(kind,105,4,82),kind.toString());
        g.setBlock(106,4,82,Blocks.STONE);game.tick(0);assertFalse(g.foliage.suitable(Foliage.Kind.IVY,105,4,82));
        // (0.2.9) a lone post is not a wall: Ivy needs the face to go on to a side
        g.setBlock(106,5,82,Blocks.STONE);game.tick(0);assertFalse(g.foliage.suitable(Foliage.Kind.IVY,105,4,82));
        g.setBlock(106,4,83,Blocks.STONE);g.setBlock(106,5,83,Blocks.STONE);game.tick(0);assertTrue(g.foliage.suitable(Foliage.Kind.IVY,105,4,82));
    }
    @Test void aWorkingSwingDoesNotTurnBackTowardTheStaleNativeHeading() {
        witness.goal=Brain.Goal.WORK;witness.yaw=0;witness.wantYaw=Float.NaN;witness.agentYaw=(float)Math.PI;witness.nativeAction="hold";
        witness.svx=0;witness.svz=2;witness.cs.phase=Actor.Combat.Phase.WINDUP;witness.cs.move=Actor.Combat.Move.HEAVY;
        witness.cs.windup=1;g.ai.update(1f/60);assertEquals(0,witness.yaw,1e-5f);
    }
    @Test void sharedMiningDownstrokesActuallyContactOreFromEachSideWithoutNpcXp() {
        for(int x=104;x<=108;x++)for(int z=80;z<=84;z++)for(int y=4;y<=7;y++)g.setBlock(x,y,z,Blocks.AIR);
        var ore=new rpg.objects.Node(928000,"review_ore","Copper rock",rpg.objects.Node.Kind.ROCK,rpg.skill.Skill.MINING,1,Items.COPPER_ORE,10,10,100,1,Blocks.COPPER_ROCK,106,82,0,0);
        g.objects.add(ore);g.objects.nodes.add(ore);g.setBlock(106,4,82,Blocks.COPPER_ROCK);game.tick(0);
        witness.eq.clear();witness.eq.set(ItemDef.Slot.MAIN,new ItemStack(Items.get("stone_pickaxe"),1));witness.sheathed=false;witness.sheatheAt=-99;
        long[] xp=witness.skills.raw();
        for(float[] side:new float[][]{{1.25f,0},{-1.25f,0},{0,1.25f},{0,-1.25f}}) {
            g.teleport(witness,106.5f+side[0],4,82.5f+side[1]);witness.cs.cancel();game.tick(0);witness.sheathed=false;witness.sheatheAt=-99;witness.armsUsedAt=g.time;witness.restore();
            float before=ore.struck;var result=g.interact.request(witness,ore,"gather");assertFalse(result.rejected(),result.reason());assertEquals(Actor.Combat.Move.HEAVY,witness.cs.move);
            for(int f=0;f<60;f++){g.time+=1f/60;g.combat.update(1f/60);}
            assertTrue(ore.struck>before,"no mining contact from "+java.util.Arrays.toString(side));
        }
        assertArrayEquals(xp,witness.skills.raw());
    }
}
