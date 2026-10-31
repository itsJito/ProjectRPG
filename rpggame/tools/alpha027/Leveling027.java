package rpg;
import astratto.bridge.NativeEngine;
import astratto.game.Game;
import astratto.platform.Window;
import rpg.render.Gfx;
import rpg.ui.Ui2D;
import rpg.world.DevMap;
import java.nio.file.*;
/** Run with RPG_ONLY=leveling: the existing acceptance flow rendered at 800x450 for software-renderer review. */
public final class Leveling027 {
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);
        var win=new Window();win.show=false;win.create("Leveling acceptance",800,450);
        int status;
        try {
            var cfg=new NativeEngine.Config();cfg.audioDevice=false;cfg.deterministic=true;
            try(var game=Game.create(cfg)) {
                game.activate(DevMap.build(game.engine()));
                try(var gfx=new Gfx(game.engine(),win.width,win.height)) {
                    var ui=new Ui2D(gfx.renderer());
                    try {status=new Acceptance(win,game,gfx,ui,out.toString()).run();}
                    finally {ui.free();}
                }
            }
        } finally {win.destroy();}
        System.exit(status);
    }
}
