package rpg;
import astratto.bridge.*;import astratto.game.*;import astratto.platform.Window;import rpg.actor.*;import rpg.world.*;import rpg.render.*;import rpg.animal.*;import java.nio.file.*;import javax.imageio.ImageIO;
/** Same native fire exposure on a living animal, its carcass, and a resident's resting body. */
public class Fire026Review {
 public static void main(String[]args)throws Exception{
  Review026.out=Path.of(args[0]);Files.createDirectories(Review026.out);var window=new Window();window.show=false;window.create("Fire review",640,360);
  var config=new NativeEngine.Config();config.deterministic=true;config.audioDevice=false;
  try(var game=Game.create(config)){
   game.activate(DevMap.build(game.engine()));var g=game.add(new Rpg(game,"Review",new Appearance(),true));Review026.game=game;Review026.g=g;g.state.minutes=600;g.ai.syncAbstractClock();g.cfg.autoScale=false;
   for(var n:g.npcs)g.ai.unload(n);for(var a:g.animals.all()){a.loaded=false;g.animals.despawnBody(a);}for(var m:g.monsters.all()){m.loaded=false;g.monsters.despawnBody(m);}
   g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();g.teleport(g.player,61,DevMap.FLOOR,68);
   try(var gfx=new Gfx(game.engine(),640,360)){
    Review026.gfx=gfx;gfx.findLamps(g.vox);float f=DevMap.FLOOR;float[] camera={67,f+1.5f,64.2f,64.5f,f+.6f,66.5f};
    var cow=g.animals.release(Species.COW,false,false,64.5f,66.5f);cow.puppet=true;game.tick(0);Review026.shot("animal-clean",camera);
    float[] center=new float[3];g.chemistry.center(cow,center);g.chemistry.emit(null,AstrattoNative.CHEM_FIRE,center[0],center[1],center[2],.8f,5,2);
    for(int i=0;i<150;i++)game.tick(1f/60);Review026.shot("animal-burning",camera);
    if(cow.alive)g.combat.bleedOutNow(cow);cow.puppet=args.length>1 && args[1].equals("held");for(int i=0;i<150;i++)game.tick(1f/60);Review026.shot("animal-charred",camera);
    var person=(rpg.ai.Npc)g.actor(Ids.VILLAGER);g.ai.reload(person);g.teleport(person,64.5f,f,70.5f);person.nextThink=Double.MAX_VALUE;game.tick(0);g.combat.bleedOutNow(person);for(int i=0;i<180;i++)game.tick(1f/60);
    camera=new float[]{67,f+1.3f,68.2f,64.5f,f+.4f,70.5f};Review026.shot("body-clean",camera);
    g.chemistry.center(person,center);g.chemistry.emit(null,AstrattoNative.CHEM_FIRE,center[0],center[1],center[2],.8f,5,2);for(int i=0;i<240;i++)game.tick(1f/60);Review026.shot("body-charred",camera);
    g.chemistry.center(person,center);g.chemistry.emit(null,AstrattoNative.CHEM_WATER,center[0],center[1],center[2],1.2f,5,1);for(int i=0;i<120;i++)game.tick(1f/60);Review026.shot("body-after-water",camera);
    System.out.println("Native fire: cow marks="+cow.blood.marks.size()+" body marks="+person.blood.marks.size()+" cow char="+cow.charred+" body char="+person.charred);
   }
  }finally{window.destroy();}
 }
}
