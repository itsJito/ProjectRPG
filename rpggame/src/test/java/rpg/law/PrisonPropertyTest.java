package rpg.law;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.item.*;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class PrisonPropertyTest {
    @Test void fineTaxEscrowAndNestedConfiscationSurviveSaveAndRelease(@TempDir Path directory) {
        var cfg=new NativeEngine.Config();cfg.deterministic=true;
        try(Game game=Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));Rpg g=game.add(new Rpg(game,"Prison property",Appearance.henric(),true));
            for(Npc npc:g.npcs)g.ai.unload(npc);
            g.player.inv.clear();g.player.eq.clear();
            var bag=new ItemStack(Items.BACKPACK,1,g.player.id,false,221);
            bag.inner.add(new ItemStack(Items.BREAD,2,Ids.MERCHANT_A,true,222));
            bag.inner.add(new ItemStack(Items.COINS,150,g.player.id,false,223));
            g.player.eq.set(ItemDef.Slot.BACK,bag);
            g.player.eq.set(ItemDef.Slot.MAIN,new ItemStack(Items.WOODEN_SWORD,1,g.player.id,false,224));
            g.law.all().computeIfAbsent(DevMap.ALPHA,k->new java.util.HashMap<>()).put(g.player.id,100);
            int treasury=((Npc)g.actor(Ids.LAWMAN_A)).count(Items.COINS);
            g.law.arrest(g.player,DevMap.ALPHA,(Npc)g.actor(Ids.GUARD));
            assertTrue(g.player.inv.isEmpty());assertTrue(g.player.eq.stacks().isEmpty());assertEquals(0,g.player.count(Items.COINS));
            assertEquals(treasury+103,((Npc)g.actor(Ids.LAWMAN_A)).count(Items.COINS));
            assertEquals(40,g.law.sentenceRemaining(),0.01);
            var saves=new SaveSystem(g);Path file=directory.resolve("custody.json");assertTrue(saves.save(file));assertTrue(saves.load(file));
            g.law.sleepThroughSentence();assertFalse(g.law.inCustody(g.player));
            assertEquals(224,g.player.eq.get(ItemDef.Slot.MAIN).origin);
            assertEquals(221,g.player.eq.get(ItemDef.Slot.BACK).origin);
            assertEquals(47,g.player.count(Items.COINS));assertEquals(0,g.player.count(Items.BREAD));
        }
    }
    @Test void anUnpaidFineAddsTimeAndNpcPossessionsAreReturnedOnRelease() {
        var cfg=new NativeEngine.Config();cfg.deterministic=true;
        try(Game game=Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));Rpg g=game.add(new Rpg(game,"NPC prisoner",Appearance.henric(),true));
            Npc prisoner=(Npc)g.actor(Ids.THIEF);prisoner.inv.clear();prisoner.eq.clear();
            prisoner.inv.add(new ItemStack(Items.COINS,3));prisoner.eq.set(ItemDef.Slot.MAIN,new ItemStack(Items.WOODEN_SWORD,1,prisoner.id,false,99));
            g.law.all().computeIfAbsent(DevMap.ALPHA,k->new java.util.HashMap<>()).put(prisoner.id,100);
            g.law.arrest(prisoner,DevMap.ALPHA,(Npc)g.actor(Ids.GUARD));
            assertTrue(prisoner.eq.stacks().isEmpty());assertEquals(50,prisoner.arrestedUntil-g.state.minutes,0.01);
            g.state.minutes=prisoner.arrestedUntil;g.law.update();assertEquals(99,prisoner.eq.get(ItemDef.Slot.MAIN).origin);
        }
    }
}
