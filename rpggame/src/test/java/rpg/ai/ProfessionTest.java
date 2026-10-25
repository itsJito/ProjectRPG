package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.Appearance;
import rpg.item.*;
import rpg.skill.Skill;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class ProfessionTest {
    private Game game;private Rpg g;
    @BeforeEach void start(){var config=new NativeEngine.Config();config.deterministic=true;game=Game.create(config);game.activate(DevMap.build(game.engine()));g=game.add(new Rpg(game,"Professions",Appearance.henric(),true));for(Npc n:g.npcs)g.ai.unload(n);g.state.minutes=600;}
    @AfterEach void close(){game.close();}
    @Test void allFiveFamiliesHaveFiveValidSubtypesButOnlyFiveResidentsWereAdded() {
        assertEquals(28,g.npcs.size());assertEquals(25,Profession.values().length);
        for(var family:new Npc.Role[]{Npc.Role.FISHERMAN,Npc.Role.MINER,Npc.Role.CRAFTSMAN,Npc.Role.KNIGHT,Npc.Role.HUNTER})assertEquals(5,java.util.Arrays.stream(Profession.values()).filter(type->type.family==family).count());
        assertEquals(5,g.npcs.stream().filter(n->n.id>=Ids.FISHERMAN && n.id<=Ids.PREDATOR_HUNTER).count());
        assertThrows(IllegalArgumentException.class,()->Profession.BLACKSMITH.assign((Npc)g.actor(Ids.FISHERMAN)));
        assertEquals(Profession.GAME_HUNTER,((Npc)g.actor(Ids.HUNTER)).profession);
    }
    @Test void aMinerUsesActualStrikesAndRetainsFixedSkillValues() {
        Npc miner=(Npc)g.actor(Ids.MINER);g.ai.reload(miner);
        var node=g.objects.nodes.stream().filter(n->n.def.equals("copper_rock")).findFirst().orElseThrow();
        float[] spot=g.ai.standSpot(miner,node);g.teleport(miner,spot[0],DevMap.FLOOR,spot[1]);miner.nextThink=Double.MAX_VALUE;miner.goal=Brain.Goal.WORK;
        long[] xp=miner.skills.raw();int before=miner.count(Items.COPPER_ORE);game.tick(0);
        for(int frame=0;frame<600 && miner.count(Items.COPPER_ORE)==before;frame++){g.ai.brain.act(miner,1f/60);assertTrue(game.tick(1f/60));}
        assertTrue(miner.count(Items.COPPER_ORE)>before,()->miner.lastOutcome+" at "+miner.x+","+miner.z+" phase="+miner.cs.phase);
        assertArrayEquals(xp,miner.skills.raw());assertTrue(g.state.all().stream().anyMatch(event->event.type().equals("ResourceGathered") && event.actor()==miner.id));
    }
    @Test void aBlacksmithConsumesRealBarsAtAStationWithoutMakingArmor() {
        Npc smith=(Npc)g.actor(Ids.CRAFTSMAN);g.ai.reload(smith);
        g.teleport(smith,DevMap.ANVIL_AT[0]+0.5f,DevMap.FLOOR,DevMap.ANVIL_AT[1]+2);smith.goal=Brain.Goal.WORK;smith.nextThink=Double.MAX_VALUE;
        int bars=smith.count(Items.BRONZE_BAR);long[] xp=smith.skills.raw();game.tick(0);
        for(int frame=0;frame<420 && smith.count(Items.BRONZE_BAR)==bars;frame++){g.ai.brain.act(smith,1f/60);game.tick(1f/60);}
        assertTrue(smith.count(Items.BRONZE_BAR)<bars,()->smith.lastOutcome+" at "+smith.x+","+smith.z);
        assertTrue(smith.inv.stacks().stream().anyMatch(item->item.def.tier!=null && item.def.family!=ItemDef.Family.ARMOR));assertArrayEquals(xp,smith.skills.raw());
        assertTrue(smith.skills.level(Skill.SMITHING)>1);
    }
}
