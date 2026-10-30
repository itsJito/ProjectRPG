package rpg.law;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.SaveSystem;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class PosseKnowledgeTest {
    @Test void sightingsNeedALocalWitnessConversationAndKeepTheirSourceAcrossSaves() throws Exception {
        var config=new NativeEngine.Config(); config.deterministic=true;
        try(Game game=Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g=game.add(new Rpg(game,"Confirmed sightings",new Appearance(),true));
            for(Npc n:g.npcs) {g.ai.unload(n);n.aware.clear();n.playerSighting=null;}
            Npc witness=(Npc)g.actor(Ids.VILLAGER),officer=g.ai.lawmanFor(DevMap.ALPHA);
            g.ai.reload(witness);g.ai.reload(officer);
            g.teleport(witness,70.5f,DevMap.FLOOR,70.5f);g.teleport(officer,100.5f,DevMap.FLOOR,70.5f);
            witness.playerSighting=new Npc.Sighting(witness.id,80,72,1,0,g.time);
            assertFalse(g.law.posses.tell(witness,officer),"a remote NPC cannot transmit a sighting");
            assertNull(officer.playerSighting);
            g.teleport(officer,73.5f,DevMap.FLOOR,70.5f);
            assertTrue(g.law.posses.tell(witness,officer));
            assertEquals(witness.id,officer.playerSighting.witness());
            g.teleport(g.player,24,DevMap.FLOOR,24);
            g.law.all().computeIfAbsent(DevMap.ALPHA,k->new java.util.HashMap<>()).put(g.player.id,1200);
            assertTrue(g.law.posses.form(DevMap.ALPHA,1200,false));
            for(Npc hunter:g.law.posses.members()) {
                assertEquals(80,hunter.hunt.x);assertEquals(72,hunter.hunt.z);assertEquals(witness.id,hunter.hunt.source);
            }
            var file=java.nio.file.Files.createTempFile("sighting",".json");
            assertTrue(new SaveSystem(g).save(file));assertTrue(new SaveSystem(g).load(file));
            assertEquals(witness.id,officer.playerSighting.witness());assertEquals(80,officer.playerSighting.x());
        }
    }

    /** Gamefixes: hunters never appear where the player looks, and they hunt actively - laying snares for the quarry. */
    @Test void huntersFormOutOfViewAndLaySnaresForTheirQuarry() {
        var config=new NativeEngine.Config(); config.deterministic=true;
        try(Game game=Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g=game.add(new Rpg(game,"Snares",new Appearance(),true));
            g.teleport(g.player,64.5f,DevMap.FLOOR,64.5f);
            g.controller.snapTo(0);
            game.tick(0);
            g.law.all().computeIfAbsent(DevMap.ALPHA,k->new java.util.HashMap<>()).put(g.player.id,1200);
            assertTrue(g.law.posses.form(DevMap.ALPHA,1200,false));
            float fx=(float)-Math.sin(g.controller.camYaw),fz=(float)-Math.cos(g.controller.camYaw);
            for(Npc hunter:g.law.posses.members()) {
                float dx=hunter.x-g.player.x,dz=hunter.z-g.player.z,d=(float)Math.hypot(dx,dz);
                assertTrue(d>=20,"a hunter formed "+d+" m away");
                assertTrue((dx*fx+dz*fz)/d<0 || d>=70,"a hunter formed in front of the player");
            }
            // on the trail at the last known location: the snares are laid there
            Npc hunter=g.law.posses.members().get(0);
            g.teleport(hunter,hunter.hunt.x,DevMap.FLOOR,hunter.hunt.z);
            hunter.hunt.reachedLkp=true; hunter.goal=rpg.ai.Brain.Goal.SEARCH; hunter.nextThink=0;
            for(int i=0;i<60*45 && hunter.snaresSet<1;i++) assertTrue(game.tick(1f/60));
            assertTrue(hunter.snaresSet>=1,"the hunter lays a snare: "+hunter.lastOutcome);
            var snare=g.dungeons.traps.stream().filter(t->t.kind==2 && !t.removed).findFirst().orElseThrow();
            assertEquals(hunter.id,snare.owner);
            // it spares its hunters, and holds the quarry fast
            g.teleport(g.player,snare.x,DevMap.FLOOR,snare.z);
            for(int i=0;i<20;i++) assertTrue(game.tick(1f/60));
            assertTrue(g.player.cs.frozenT>0,"the snare holds the player (sprung by "+g.state.all().stream().filter(e->e.type().equals("TrapSprung")).map(e->g.nameOf(e.actor())).toList()+" armed "+snare.armed+" removed "+snare.removed+" sprung "+snare.sprungAt+" grounded "+g.player.grounded+" y "+g.player.y+" at "+g.player.x+","+g.player.z+" snare "+snare.x+","+snare.z+")");
        }
    }
}
