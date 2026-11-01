package rpg.render;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class FoliageFrame028Test {
    @Test void rigidWallGrowthDoesNotTurnItsWholeFoliageBatchTranslucent() {
        var cfg=new NativeEngine.Config();cfg.deterministic=true;cfg.audioDevice=false;
        try(var game=Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));var g=game.add(new Rpg(game,"Foliage frame",new Appearance(),true));
            g.teleport(g.player,88.5f,DevMap.FLOOR,12);
            for(int i=0;i<25;i++){g.time+=.26;g.foliage.update();}
            boolean wall=false;
            for(var chunk:g.foliage.chunks.values()) {
                float[] mesh=FoliageModels.chunk(chunk);
                for(int vertex=0;vertex<mesh.length;vertex+=10) {
                    float metadata=mesh[vertex+6];
                    // Renderer::createMesh sends an entire model to the forward pass if any vertex is below this.
                    assertTrue(metadata>=249.5f/255,"bend metadata must preserve opaque batching");
                    wall|=metadata==65538;
                }
            }
            assertTrue(wall,"exercise actual generated Ivy/Moss, rather than only ordinary plants");
        }
    }
}
