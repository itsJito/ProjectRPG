package rpg;
import astratto.bridge.NativeEngine;
import astratto.game.Game;
import astratto.platform.Window;
import rpg.actor.*;
import rpg.render.Gfx;
import rpg.world.DevMap;
import java.lang.reflect.*;
import java.util.*;
public class FigureCacheProbe  {
    public static void main(String[] args)throws Exception  {
        var window=new Window();
        window.show=false;
        window.create("Appearance pressure",320,180);
        var config=new NativeEngine.Config();
        config.deterministic=true;
        config.audioDevice=false;
        try(var game=Game.create(config))  {
            game.activate(DevMap.build(game.engine()));
            var g=game.add(new Rpg(game,"Appearance pressure",new Appearance(),true));
            try(var gfx=new Gfx(game.engine(),320,180))  {
                var figure=Gfx.class.getDeclaredMethod("figure",Actor.class,Appearance.class);
                figure.setAccessible(true);
                var clock=Gfx.class.getDeclaredField("clock");
                clock.setAccessible(true);
                var uploads=Gfx.class.getDeclaredField("uploadsThisFrame");
                uploads.setAccessible(true);
                var figures=Gfx.class.getDeclaredField("figures");
                figures.setAccessible(true);
                var building=Gfx.class.getDeclaredField("building");
                building.setAccessible(true);
                gfx.preload(g.npcs);
                gfx.preload(List.of(g.player));
                int initial=((Map<?,?>)figures.get(gfx)).size();
                int pendingMax=0;
                for(int frame=0;frame<100;frame++)  {
                    g.time+=0.5;
                    clock.setDouble(gfx,g.time);
                    uploads.setInt(gfx,0);
                    g.player.blood.add(Blood.TORSO,Blood.FRONT,(frame%9)/9f,(frame%7)/7f,1,g.time);
                    figure.invoke(gfx,g.player,g.player.look);
                    pendingMax=Math.max(pendingMax,((Map<?,?>)building.get(gfx)).size());
                }
                long until=System.nanoTime()+30_000_000_000L;
                while(!gfx.figureReady(g.player)&&System.nanoTime()<until) {
                    uploads.setInt(gfx,0);
                    figure.invoke(gfx,g.player,g.player.look);
                    Thread.sleep(1);
                }
                System.out.printf("initial=%d meshes=%d pendingPeak=%d pendingEnd=%d latestReady=%s%n",initial,((Map<?,?>)figures.get(gfx)).size(),pendingMax,((Map<?,?>)building.get(gfx)).size(),gfx.figureReady(g.player));
            }
        }
        finally {
            window.destroy();
        }
    }
}
