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
}
