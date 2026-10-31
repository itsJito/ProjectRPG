package rpg;
import astratto.bridge.*;
import astratto.game.*;
import astratto.platform.Window;
import rpg.actor.*;
import rpg.ai.*;
import rpg.render.*;
import rpg.ui.*;
import rpg.world.*;
import rpg.item.*;
import rpg.interact.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;

/** Repeatable native-renderer inspection. Animation contact sheets sample frames 1,4,7,...,208. */
public final class Review027 {
 static final int W=800,H=450;static Gfx gfx;static Rpg g;static Game game;static Path out;
 static BufferedImage pixels(){var bytes=gfx.pixels(W,H);var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4;image.setRGB(x,y,(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|(bytes.get(i+2)&255));}MemoryUtil.memFree(bytes);return image;}
 static void draw(float[] c){draw(c,true);}
 static void draw(float[] c,boolean finish){float dx=c[3]-c[0],dy=c[4]-c[1],dz=c[5]-c[2],l=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);gfx.camera(c[0],c[1],c[2],dx/l,dy/l,dz/l,W,H,1f/60);boolean alive=g.player.alive;g.player.alive=false;gfx.scene(g,false,1f/60,10);g.player.alive=alive;if(finish)gfx.frame(W,H);}
 static void shot(String name,float[] c)throws Exception{for(int i=0;i<20;i++)draw(c);ImageIO.write(pixels(),"png",out.resolve(name+".png").toFile());System.out.println("shot "+name);}
 public static void main(String[] args)throws Exception{
  out=Path.of(args[0]);Files.createDirectories(out);boolean baseline=args.length>1&&args[1].equals("baseline");boolean chat=args.length>1&&args[1].equals("chat");
  var win=new Window();win.show=false;win.create("Alpha 0.2.7 review",W,H);var cfg=new NativeEngine.Config();cfg.audioDevice=false;cfg.deterministic=true;
  try(var world=Game.create(cfg)){game=world;game.activate(DevMap.build(game.engine()));g=game.add(new Rpg(game,"Review",new Appearance(),true));g.state.minutes=600;g.ai.syncAbstractClock();g.cfg.autoScale=false;
   for(var n:g.npcs)g.ai.unload(n);for(var a:g.animals.all()){a.loaded=false;g.animals.despawnBody(a);}for(var m:g.monsters.all()){m.loaded=false;g.monsters.despawnBody(m);}game.tick(0);
   try(var view=new Gfx(game.engine(),W,H)){gfx=view;gfx.findLamps(g.vox);float f=DevMap.FLOOR;
    if(args.length>1 && args[1].equals("course")){g.teleport(g.player,160,f,15);shot("stairs-ladder",new float[]{159,f+5,12,165,f+2,19});return;}
    var ui=new Ui2D(gfx.renderer());var preview=new Gfx.Preview();preview.world=TitleStage.build(game.engine());preview.dungeon=true;preview.x=TitleStage.STAND_X;preview.y=f;preview.z=TitleStage.STAND_Z;var title=new TitleScreen(Main.VERSION);
    if(!chat){for(int i=0;i<90;i++){gfx.title(preview,Main.defaultLook(),W,H,1f/60,i/60.);ui.begin(gfx.cmd);title.draw(ui,W,H,1f/60,false,false);ui.end();gfx.frame(W,H);}ImageIO.write(pixels(),"png",out.resolve("title.png").toFile());System.out.println("shot title");
    g.teleport(g.player,140,f,67);shot("hedges",new float[]{139,f+4,67,136.5f,f+4,70.5f});
    if(!baseline){g.teleport(g.player,160,f,15);shot("stairs-ladder",new float[]{159,f+5,12,165,f+2,19});}
    }
    var n=(Npc)g.actor(Ids.VILLAGER);g.ai.reload(n);g.teleport(n,64.5f,f,64.5f);n.nextThink=Double.MAX_VALUE;n.goal=Brain.Goal.WORK;n.yaw=0;n.eq.clear();n.inv.add(new ItemStack(Items.HAMMER,1));n.sheathed=true;g.teleport(g.player,62,f,63);game.tick(0);
    n.reachX=n.x;n.reachY=f+1.2f;n.reachZ=n.z-.7f;
    var site=new WorldObject(999999,"repair_site","Damaged building",n.reachX,n.reachY,n.reachZ){public java.util.List<Option> options(Rpg g,Actor a){return java.util.List.of();}public Result perform(Rpg g,Actor a,String s,long req){return Result.ok();}};
    n.working=new Work(n,site,"repair","Repairing",3.5f,false,g.time,()->Result.ok());
    float[] camera={n.x+2.4f,f+1.7f,n.z-2.1f,n.x,f+1,n.z-.3f};
    var sheet=new BufferedImage(W*4,(H+18)*18,BufferedImage.TYPE_INT_RGB);var labels=sheet.createGraphics();
    for(int frame=0;frame<(chat?0:210);frame++){n.working.t=frame/60f;g.time+=1f/60;draw(camera);if(frame%3==0){int k=frame/3,x=k%4*W,y=k/4*(H+18);labels.setColor(java.awt.Color.WHITE);labels.drawString("f"+(frame+1),x+4,y+13);var image=pixels();labels.drawImage(image,x,y+18,null);if(frame==30)ImageIO.write(image,"png",out.resolve("repair-close.png").toFile());}}
    labels.dispose();if(!chat)ImageIO.write(sheet,"png",out.resolve("repair-threes.png").toFile());n.working=null;if(!chat)shot("repair-interrupted",camera);
    g.ui=new Hud(g,gfx);g.teleport(g.player,n.x+1,f,n.z);game.tick(0);n.goal=Brain.Goal.HIDE;
    var memory=new Memory("THEFT",g.player.id,Ids.LAWMAN_A,DevMap.ALPHA,g.state.minutes,1,"witnessed",99128,0,"theft");memory.witness=n.id;n.remember(memory,24);g.ui.dialog(g.dialogue.talk(n));
    for(int i=0;i<30;i++){draw(camera,false);ui.begin(gfx.cmd);g.ui.draw(ui,W,H,true,1f/60);ui.end();gfx.frame(W,H);}ImageIO.write(pixels(),"png",out.resolve("witness-chat.png").toFile());System.out.println("review complete");
   }
  }finally{win.destroy();}
 }
}
