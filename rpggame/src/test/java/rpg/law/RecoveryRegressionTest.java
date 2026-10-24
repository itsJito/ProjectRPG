package rpg.law;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.*;
import rpg.item.*;
import rpg.objects.Container;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryRegressionTest {
    private Game game;private Rpg g;
    @BeforeEach void start() {
        var cfg=new NativeEngine.Config();cfg.deterministic=true;game=Game.create(cfg);game.activate(DevMap.build(game.engine()));
        g=game.add(new Rpg(game,"Recovery",Appearance.henric(),true));for(Npc n:g.npcs)g.ai.unload(n);
        for(ItemStack item:g.player.inv.stacks())g.state.burn(item.def,item.amount);
        for(ItemStack item:g.player.eq.stacks())g.state.burn(item.def,item.amount);
        g.player.inv.clear();g.player.eq.clear();
    }
    @AfterEach void close(){game.close();}
    @Test void burialPreservesAProtectedFullClaimIncludingStolenProvenanceAcrossLoad(@TempDir Path dir) {
        for(int i=0;i<39;i++)assertTrue(g.player.inv.add(new ItemStack(Items.get("gold_ring"),1,900+i,true,990+i)));
        var pack=new ItemStack(Items.BACKPACK,1,700,true,1400);assertTrue(pack.inner.add(new ItemStack(Items.BREAD,3,701,true,1401)));
        g.player.eq.set(ItemDef.Slot.BACK,pack);g.player.eq.set(ItemDef.Slot.BODY,new ItemStack(Items.get("iron_body"),1,702,true,1402));
        assertTrue(g.player.inv.add(new ItemStack(Items.COINS,100)));
        var possessions=new java.util.ArrayList<>(g.player.inv.stacks());possessions.addAll(g.player.eq.stacks());
        var original=possessions.stream().map(s->s.origin).filter(id->id!=0).sorted().toList();
        Container grave=g.makeGrave(114,90,0,true);assertNotNull(grave);assertEquals((long)original.size(),grave.contents.stacks().stream().filter(s->s.origin!=0).count());
        g.teleport(g.player,grave.x,DevMap.FLOOR,grave.z);game.tick(0);
        assertTrue(grave.perform(g,g.player,"open",g.state.newRequestId()).rejected());
        assertTrue(grave.take(g,g.player,grave.contents.first(Items.COINS),1,g.state.newRequestId()).rejected());
        Npc guard=(Npc)g.actor(Ids.GUARD);g.ai.reload(guard);g.teleport(guard,113,DevMap.FLOOR,89);game.tick(0);
        assertTrue(g.graveyards.bury(grave,guard,g.graveyards.nearest(guard)));g.undertaker.toCustody(grave,"duplicate callback");
        assertEquals(1,g.undertaker.claims.size());int fee=g.undertaker.fee();
        assertTrue(g.objects.custody.contents.add(new ItemStack(Items.get("gold_ring"),1,9911,true,8811)));
        var saves=new SaveSystem(g);var file=dir.resolve("claim.json");assertTrue(saves.save(file));assertTrue(saves.load(file));game.tick(0);
        assertEquals(fee,g.undertaker.fee());assertTrue(g.undertaker.redeem(g.player,(Npc)g.actor(Ids.UNDERTAKER)).committed());
        var recovered=g.player.inv.stacks().stream().filter(s->s.origin!=0).toList();
        // A full pack is returned as original loose items, never silently destroyed.
        var loose=g.objects.all().stream().filter(o->o instanceof rpg.objects.GroundItem item && item.stack.origin!=0).map(o->((rpg.objects.GroundItem)o).stack).toList();
        var origins=new java.util.ArrayList<Long>();for(var s:recovered) {origins.add(s.origin);assertTrue(s.stolen);}for(var s:loose) {origins.add(s.origin);assertTrue(s.stolen);}
        assertEquals(original,origins.stream().sorted().toList());
        var all=new java.util.ArrayList<>(recovered);all.addAll(loose);var recoveredPack=all.stream().filter(s->s.origin==1400).findFirst().orElseThrow();
        assertEquals(3,recoveredPack.inner.count(Items.BREAD));assertEquals(1401,recoveredPack.inner.stacks().getFirst().origin);assertEquals(100-fee,g.player.count(Items.COINS));assertEquals(0,g.undertaker.held());
        assertTrue(g.objects.custody.contents.stacks().stream().anyMatch(s->s.origin==8811),"unrelated evidence is not a death claim");
    }
    @Test void exposedExpiryWaitsForAnActualUndertakerJourneyInsteadOfTeleportingItems() {
        g.graveyards.plots.clear();g.graveyards.plots.add(new Graveyards.Plot("journey",DevMap.WILDS,38.5f,DevMap.FLOOR,87.5f,3,2));
        for(int x=27;x<42;x++)for(int z=84;z<91;z++)for(int y=DevMap.FLOOR;y<DevMap.FLOOR+4;y++)g.setBlock(x,y,z,rpg.world.Blocks.AIR);
        g.player.inv.add(new ItemStack(Items.BREAD,2,g.player.id,false,712));Container grave=g.makeGrave(33.5f,87.5f,0,false);grave.expiresAt=g.time;
        Npc collector=(Npc)g.actor(Ids.UNDERTAKER);g.ai.reload(collector);g.teleport(collector,28.5f,DevMap.FLOOR,87.5f);
        g.teleport(g.player,30.5f,DevMap.FLOOR,90.5f);game.tick(0);
        assertFalse(grave.buried);assertEquals(0,g.undertaker.held());boolean carried=false;
        for(int i=0;i<60*35&&!grave.buried;i++){assertTrue(game.tick(1f/60));carried|=grave.heldBy==collector.id;}
        assertTrue(carried,"the collector must pick up the actual grave");assertTrue(grave.buried,()->collector.goal+" "+collector.lastOutcome+" at "+collector.x+","+collector.z);
        assertEquals(Undertaker.FEE+Undertaker.OUTSIDE_SURCHARGE,g.undertaker.fee());assertEquals(2,g.undertaker.held());assertEquals(2,grave.contents.count(Items.BREAD));
    }
    @Test void olderProfilesAddAuthoredResidentsWithoutDuplicatingTheirStock(@TempDir Path directory) {
        // Model a pre-release save: these authored people and their initial goods do not exist yet.
        for(Npc n:new java.util.ArrayList<>(g.npcs))if(n.id>=Ids.BOBWIN && n.id<=Ids.SCHOLAR) {
            for(ItemStack item:n.inv.stacks())g.state.burn(item.def,item.amount);
            for(ItemStack item:n.eq.stacks())g.state.burn(item.def,item.amount);
            g.npcs.remove(n);
        }
        var saves=new SaveSystem(g);var old=directory.resolve("older.json");assertTrue(saves.save(old));
        assertTrue(saves.load(old));game.tick(0);assertEquals(6,g.npcs.stream().filter(n->n.id>=Ids.BOBWIN&&n.id<=Ids.SCHOLAR).count());
        assertTrue(g.invariants.check().isEmpty(),()->g.invariants.check().toString());
        var bobwin=(Npc)g.actor(Ids.BOBWIN);bobwin.inv.clear();bobwin.eq.clear();
        assertTrue(saves.load(old));game.tick(0);assertTrue(((Npc)g.actor(Ids.BOBWIN)).eq.get(ItemDef.Slot.OFF)!=null);
        assertTrue(g.invariants.check().isEmpty(),()->g.invariants.check().toString());
        var current=directory.resolve("current.json");assertTrue(saves.save(current));assertTrue(saves.load(current));game.tick(0);
        assertTrue(g.invariants.check().isEmpty(),()->g.invariants.check().toString());
    }
    @Test void anOfficersOwnEyesCarryMoreWeightThanRumorAndADisputeIsCommittedOnce() {
        Npc officer=(Npc)g.actor(Ids.GUARD),teller=(Npc)g.actor(Ids.VILLAGER);officer.memories.clear();
        Memory eyewitness=new Memory("ASSAULT",g.player.id,teller.id,DevMap.ALPHA,600,1,"witnessed",88,0,"seen personally");
        officer.memories.add(eyewitness.rumorFrom(teller));float hearsay=g.law.certainty(officer,g.player);
        officer.memories.clear();officer.memories.add(eyewitness);assertTrue(g.law.certainty(officer,g.player)>hearsay);
        g.law.load(java.util.Map.of(DevMap.ALPHA,java.util.Map.of("1",100)));
        long request=g.state.newRequestId();assertTrue(g.law.denyCharge(g.player,officer,request).committed());
        int admissions=g.law.jailAdmissions();assertTrue(g.law.denyCharge(g.player,officer,request).rejected());assertEquals(admissions,g.law.jailAdmissions());
        assertEquals(0,g.law.bounty(g.player.id,DevMap.ALPHA));assertTrue(officer.memories.contains(eyewitness));
    }
}
