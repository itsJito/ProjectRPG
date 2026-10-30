package rpg;
import astratto.bridge.NativeEngine;
import astratto.game.Game;
import rpg.actor.Appearance;
import rpg.world.DevMap;
import java.util.Arrays;
public class SimulationProfile {
    public static void main(String[]args) {
        var cfg=new NativeEngine.Config();
        cfg.deterministic=true;
        cfg.audioDevice=false;
        try(Game game=Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            var g=game.add(new Rpg(game,"Profile",new Appearance(),true));
            g.state.minutes=600;
            g.player.sheathed=true;
            g.teleport(g.player,100.5f,DevMap.FLOOR,70.5f);
            int frames=3000;
            long[] ns=new long[frames];
            for(int i=0;i<frames+600;i++) {
                long before=System.nanoTime();
                game.tick(1f/60);
                if(i>=600)ns[i-600]=System.nanoTime()-before;
            }
            Arrays.sort(ns);
            System.out.printf("tick ms p50=%.3f p95=%.3f p99=%.3f max=%.3f%n",ns[frames/2]/1e6,ns[(int)(frames*.95)]/1e6,ns[(int)(frames*.99)]/1e6,ns[frames-1]/1e6);
            for(int i=0;i<g.prof.length;i++)System.out.printf("%s %.1fms%n",Rpg.PROF_NAMES[i],g.prof[i]/1e6);
        }
    }
}
