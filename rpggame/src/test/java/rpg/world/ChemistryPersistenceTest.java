package rpg.world;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import rpg.*;
import rpg.actor.Appearance;
import static astratto.bridge.AstrattoNative.*;
import static org.junit.jupiter.api.Assertions.*;

class ChemistryPersistenceTest {
    private Game game;private Rpg g;
    @BeforeEach void start() {
        var cfg=new NativeEngine.Config();cfg.deterministic=true;game=Game.create(cfg);game.activate(DevMap.build(game.engine()));
        g=game.add(new Rpg(game,"Chemistry recovery",Appearance.henric(),true));for(var n:g.npcs)g.ai.unload(n);
        g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();
    }
    @AfterEach void close(){game.close();}
    private void seconds(float seconds){for(int i=0;i<(int)(seconds*60);i++)assertTrue(game.tick(1f/60));}
    @Test void charredCellsSurviveLoadingAndEarlierSavesRollBackOnlyChemistryEdits(@TempDir Path dir) {
        g.setBlock(100,12,70,Blocks.PLANKS);game.commands().chemMaterial(Blocks.PLANKS,CHEM_FLAMMABLE,Blocks.CHARRED,0.2f);game.tick(0);
        var saves=new SaveSystem(g);Path before=dir.resolve("before.json"),after=dir.resolve("after.json");assertTrue(saves.save(before));
        g.chemistry.emit(g.player,CHEM_FIRE,100.5f,12.5f,70.5f,0.2f,3,0.1f);seconds(1);
        assertEquals(Blocks.CHARRED,g.vox.block(100,12,70));assertTrue(saves.save(after));
        assertTrue(saves.load(before));game.tick(0);assertEquals(Blocks.PLANKS,g.vox.block(100,12,70),()->"after load "+g.chemistry.save());
        assertTrue(saves.load(after));game.tick(0);assertEquals(Blocks.CHARRED,g.vox.block(100,12,70));
    }
    @Test void frozenWaterKeepsItsRemainingThawTimeAcrossLoad(@TempDir Path dir) {
        g.setBlock(104,12,74,Blocks.WATER);game.tick(0);
        g.chemistry.emit(g.player,CHEM_ICE,104.5f,12.5f,74.5f,0.2f,1,0.1f);seconds(2);
        assertEquals(Blocks.ICE,g.vox.block(104,12,74));
        var saves=new SaveSystem(g);var file=dir.resolve("ice.json");assertTrue(saves.save(file));
        seconds(20);assertEquals(Blocks.WATER,g.vox.block(104,12,74),()->"after thaw "+g.chemistry.save());
        assertTrue(saves.load(file));game.tick(0);assertEquals(Blocks.ICE,g.vox.block(104,12,74));
        seconds(12);assertEquals(Blocks.ICE,g.vox.block(104,12,74));seconds(8);assertEquals(Blocks.WATER,g.vox.block(104,12,74),()->"restored thaw "+g.chemistry.save());
    }
}
