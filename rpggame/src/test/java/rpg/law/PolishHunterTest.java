package rpg.law;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.*;
import rpg.item.*;
import rpg.objects.Trap;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class PolishHunterTest {
    @Test void huntersApproachAConfirmedRouteSpendRealSnaresAndOnlyTriggerOnContact() {
        var cfg=new NativeEngine.Config();cfg.deterministic=true;
        try(var game=Game.create(cfg)){
            game.activate(DevMap.build(game.engine()));var g=game.add(new Rpg(game,"Hunters",new Appearance(),true));
            for(var npc:g.npcs)g.ai.unload(npc);game.tick(0);
            g.teleport(g.player,64.5f,DevMap.FLOOR,70.5f);g.controller.snapTo(0);
            assertFalse(g.law.posses.outsideView(64.5f,40.5f));assertTrue(g.law.posses.outsideView(64.5f,101));
            var hunter=new Npc(g.state.newEntityId(),"Hunter",Npc.Role.HUNTER,"Bounty Hunter",68.5f,72.5f);
            hunter.posse=true;hunter.loaded=true;hunter.hunt=new Hunt(g.player.id,"witness account",66.5f,70.5f,g.time,600);
            hunter.hunt.vx=1;hunter.hunt.source=Ids.VILLAGER;g.npcs.add(hunter);g.ai.spawnBody(hunter);
            g.give(hunter,Items.SNARE,1);
            assertTrue(g.traps.hunt(hunter));assertEquals(1,hunter.count(Items.SNARE));
            assertTrue(g.objects.all().stream().noneMatch(o->o instanceof Trap t&&t.owner==hunter.id),"a remote hunter cannot lay a trap instantly");
            g.teleport(hunter,69.5f,DevMap.FLOOR,70.5f);assertTrue(g.traps.hunt(hunter));
            var trap=(Trap)g.objects.all().stream().filter(o->o instanceof Trap t&&t.owner==hunter.id).findFirst().orElseThrow();
            assertEquals(0,hunter.count(Items.SNARE));assertEquals(1,trap.contents.count(Items.SNARE));assertEquals(g.player.id,trap.quarry);
            g.traps.update();assertEquals(0,trap.caught);
            g.teleport(g.player,trap.x,trap.y,trap.z);g.player.grounded=true;g.time+=0.3;g.traps.update();
            assertEquals(g.player.id,trap.caught);assertTrue(g.player.cs.slowT>=6);
        }
    }
    @Test void socialVisitorsUseThePublicSideOfAWorkingMerchantsCounter() {
        var cfg=new NativeEngine.Config();cfg.deterministic=true;
        try(var game=Game.create(cfg)){
            game.activate(DevMap.build(game.engine()));var g=game.add(new Rpg(game,"Counters",new Appearance(),true));
            for(var npc:g.npcs)g.ai.unload(npc);g.state.minutes=600;
            var merchant=(Npc)g.actor(Ids.MERCHANT_A);var visitor=(Npc)g.actor(Ids.VILLAGER);
            g.ai.reload(merchant);g.ai.reload(visitor);g.teleport(merchant,DevMap.SHOP_A_COUNTER[0],DevMap.FLOOR,DevMap.SHOP_A_COUNTER[1]);
            merchant.goal=Brain.Goal.WORK;g.teleport(visitor,91.5f,DevMap.FLOOR,60.5f);visitor.social=1;visitor.hunger=visitor.fatigue=0;
            float[] anchor=g.ai.conversationSpot(visitor,merchant);assertNotNull(anchor);assertTrue(anchor[1]>57.8f,"visitor should not route behind the counter");
            g.ai.brain.think(visitor);assertEquals(Brain.Goal.SOCIALIZE,visitor.goal);
            for(int i=0;i<600 && visitor.social>0.1f;i++)assertTrue(game.tick(1f/60));
            assertTrue(visitor.social<0.1f,"the visit completes: "+visitor.lastOutcome+" at "+visitor.x+","+visitor.z);
        }
    }
}
