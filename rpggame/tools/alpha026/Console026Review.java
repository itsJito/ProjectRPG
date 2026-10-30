package rpg;
import astratto.bridge.*;import astratto.game.*;import astratto.platform.Window;import rpg.actor.*;import rpg.render.*;import rpg.ui.*;import rpg.world.*;
/** Exercises the registered commands and their shared gameplay state under a real renderer. */
public class Console026Review {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[]args)throws Exception{
  var window=new Window();window.show=false;window.create("Console review",640,360);
  var config=new NativeEngine.Config();config.audioDevice=false;config.deterministic=true;
  try(var game=Game.create(config)){
   game.activate(DevMap.build(game.engine()));var g=game.add(new Rpg(game,"Review",new Appearance(),true));
   try(var gfx=new Gfx(game.engine(),640,360)){
    var hud=new Hud(g,gfx);
    g.ui=hud;var console=hud.console;
    console.run("bounty set DevCity 145",false);console.run("bounty set DevTown 225",false);console.run("bounty set DevPalace 310",false);
    check(g.law.bounty(g.player.id,DevMap.ALPHA)==145 && g.law.bounty(g.player.id,DevMap.BETA)==225 && g.law.bounty(g.player.id,DevMap.PALACE)==310,"set each kingdom");
    console.run("bounty reset DevTown",false);check(g.law.bounty(g.player.id,DevMap.BETA)==0 && g.law.bounty(g.player.id,DevMap.ALPHA)==145,"scoped reset");
    console.run("bounty set nowhere 10",false);console.run("bounty set DevCity -1",false);check(g.law.bounty(g.player.id,DevMap.ALPHA)==145,"invalid input must not mutate a case");
    console.run("invisible",false);check(g.notarget() && console.invisible,"invisible enables AI exclusion");
    console.run("reset",false);check(!g.notarget() && g.player.hp==g.player.maxHp() && Math.abs(g.player.x-DevMap.SPAWN_X)<.1,"reset restores body and debugging controls");
    console.run("reset bounty all",false);for(String jurisdiction:new String[]{DevMap.ALPHA,DevMap.BETA,DevMap.PALACE})check(g.law.bounty(g.player.id,jurisdiction)==0,"all cases close");
    if(args.length>0){
     Review026.game=game;Review026.g=g;Review026.gfx=gfx;Review026.out=java.nio.file.Path.of(args[0]);java.nio.file.Files.createDirectories(Review026.out);Review026.showPlayer=true;
     g.state.minutes=600;g.ai.syncAbstractClock();g.cfg.autoScale=false;console.run("ai_disabled 1",false);
     for(var npc:g.npcs)g.ai.unload(npc);
     float[] camera={g.player.x+2.3f,g.player.y+1.5f,g.player.z+2.3f,g.player.x,g.player.y+.9f,g.player.z};
     Review026.shot("visible",camera);console.run("invisible",false);Review026.shot("invisible",camera);
    }
    hud.close();System.out.println("Console review: scoped sets/resets, validation, invisibility and player reset passed");
   }
  }finally{window.destroy();}
 }
}
