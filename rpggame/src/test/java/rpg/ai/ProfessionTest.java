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
    @Test void aCookBuysRealIngredientsInsteadOfSelectingAnUnusableFishingJob() {
        Npc cook=(Npc)g.actor(Ids.CRAFTSMAN);Profession.COOK.assign(cook);g.ai.reload(cook);cook.goal=Brain.Goal.WORK;cook.nextThink=Double.MAX_VALUE;
        Npc merchant=(Npc)g.actor(Ids.MERCHANT_A);g.ai.reload(merchant);merchant.nextThink=Double.MAX_VALUE;merchant.goal=Brain.Goal.WORK;
        cook.inv.clear();cook.eq.clear();cook.inv.add(new ItemStack(Items.COINS,1000));g.econ.shopA.stock.add(new ItemStack(Items.RAW_SHRIMP,1));
        int stock=g.econ.shopA.stock.count(Items.RAW_SHRIMP);long[] xp=cook.skills.raw();
        g.ai.brain.act(cook,1f/60);float[] counter=g.econ.counterSpot(g.econ.shopA);g.teleport(cook,counter[0],DevMap.FLOOR,counter[1]);game.tick(0);
        for(int frame=0;frame<60 && cook.count(Items.RAW_SHRIMP)==0;frame++){g.ai.brain.act(cook,1f/60);game.tick(1f/60);}
        assertEquals(1,cook.count(Items.RAW_SHRIMP),cook.lastOutcome);assertEquals(stock-1,g.econ.shopA.stock.count(Items.RAW_SHRIMP));assertTrue(cook.count(Items.COINS)<1000);assertArrayEquals(xp,cook.skills.raw());
        assertFalse(g.state.all().stream().anyMatch(event->event.type().equals("ResourceGathered") && event.actor()==cook.id));
    }
    @Test void aBlacksmithSmeltsActualOreForItsOwnForgeInputs() {
        Npc smith=(Npc)g.actor(Ids.CRAFTSMAN);g.ai.reload(smith);smith.inv.clear();smith.eq.clear();
        smith.inv.add(new ItemStack(Items.COPPER_ORE,1));smith.inv.add(new ItemStack(Items.TIN_ORE,1));
        g.teleport(smith,DevMap.FURNACE_AT[0]+0.5f,DevMap.FLOOR,DevMap.FURNACE_AT[1]+2);smith.goal=Brain.Goal.WORK;smith.nextThink=Double.MAX_VALUE;game.tick(0);
        long[] xp=smith.skills.raw();
        for(int frame=0;frame<300 && smith.count(Items.BRONZE_BAR)==0;frame++){g.ai.brain.act(smith,1f/60);game.tick(1f/60);}
        assertEquals(1,smith.count(Items.BRONZE_BAR),smith.lastOutcome);assertEquals(0,smith.count(Items.COPPER_ORE));assertEquals(0,smith.count(Items.TIN_ORE));assertArrayEquals(xp,smith.skills.raw());
    }
    @Test void aTailorKeepsWovenClothUntilItBecomesARealGarment() {
        Npc tailor=(Npc)g.actor(Ids.CRAFTSMAN);Profession.TAILOR.assign(tailor);g.ai.reload(tailor);tailor.inv.clear();tailor.eq.clear();tailor.inv.add(new ItemStack(Items.CLOTH,3));
        g.teleport(tailor,DevMap.CRAFT_AT[0]+0.5f,DevMap.FLOOR,DevMap.CRAFT_AT[1]+2);tailor.goal=Brain.Goal.WORK;tailor.nextThink=Double.MAX_VALUE;game.tick(0);
        long[] xp=tailor.skills.raw();
        for(int frame=0;frame<240 && tailor.count(Items.CLOTH_HOOD)==0;frame++){g.ai.brain.act(tailor,1f/60);game.tick(1f/60);}
        assertEquals(1,tailor.count(Items.CLOTH_HOOD),tailor.lastOutcome);assertEquals(2,tailor.count(Items.CLOTH));assertArrayEquals(xp,tailor.skills.raw());
    }

    @Test void aCarpenterReservesPlanksForJoineryInsteadOfSellingTheUnfinishedBatch() {
        Npc carpenter=(Npc)g.actor(Ids.CRAFTSMAN);Profession.CARPENTER.assign(carpenter);carpenter.skills.setLevel(Skill.CONSTRUCTION,25);g.ai.reload(carpenter);
        carpenter.inv.clear();carpenter.eq.clear();carpenter.inv.add(new ItemStack(Items.HAMMER,1));carpenter.inv.add(new ItemStack(Items.PLANK,3));carpenter.inv.add(new ItemStack(Items.LOGS,3));
        g.teleport(carpenter,g.objects.buildSpot.x,DevMap.FLOOR,g.objects.buildSpot.z+1.5f);carpenter.goal=Brain.Goal.WORK;carpenter.nextThink=Double.MAX_VALUE;game.tick(0);
        long[] xp=carpenter.skills.raw();
        for(int frame=0;frame<1800 && !g.objects.buildSpot.built.get("table");frame++){g.ai.brain.act(carpenter,1f/60);game.tick(1f/60);}
        assertTrue(g.objects.buildSpot.built.get("table"),carpenter.lastOutcome);
        assertEquals(carpenter.id,g.objects.buildSpot.pieces.get("table").owner);assertTrue(carpenter.count(Items.PLANK)<3);assertArrayEquals(xp,carpenter.skills.raw());
    }

}
