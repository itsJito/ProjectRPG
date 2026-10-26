package rpg.render;

import astratto.bridge.AstrattoNative;
import astratto.bridge.NativeEngine;
import astratto.game.Game;
import astratto.platform.Window;
import org.junit.jupiter.api.Test;
import rpg.actor.Appearance;
import rpg.world.DevMap;
import rpg.world.TitleStage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MenuPreviewTest {
    @Test void titleAndCreatorRenderCharactersBeforeAGameExists() {
        assumeFalse("1".equals(System.getenv("ASTRATTO_HEADLESS")), "No GPU: menu preview requires OpenGL");
        Window window = new Window();
        window.show = false;
        window.create("Menu preview regression", 640, 360);
        try {
            assertTrue(window.gl45, window.glInfo);
            var config = new NativeEngine.Config();
            config.deterministic = true;
            try (Game game = Game.create(config)) {
                game.activate(DevMap.build(game.engine()));
                Gfx.Preview preview = new Gfx.Preview();
                preview.world = TitleStage.build(game.engine());
                preview.dungeon = true;
                preview.x = preview.z = 8.5f;
                preview.y = DevMap.FLOOR;
                assertTrue(game.tick(0));
                try (Gfx gfx = new Gfx(game.engine(), window.width, window.height)) {
                    gfx.menuPreferences.quality = AstrattoNative.QUALITY_LOW;
                    Appearance look = Appearance.henric();
                    assertDoesNotThrow(() -> {
                        gfx.title(preview, null, window.width, window.height, 1f / 60, 6);
                        gfx.frame(window.width, window.height);
                        gfx.title(preview, look, window.width, window.height, 1f / 60, 6.1);
                        gfx.frame(window.width, window.height);
                        gfx.creator(preview, look, 0, window.width, window.height, 1f / 60, 6.2);
                        gfx.frame(window.width, window.height);
                    });
                }
            }
        } finally {
            window.destroy();
        }
    }
}
