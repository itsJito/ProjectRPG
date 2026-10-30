package rpg;
import astratto.bridge.*;
import astratto.game.Game;
import astratto.platform.Window;
import rpg.actor.*;
import rpg.item.*;
import rpg.world.*;
import rpg.render.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
public class Review025  {
    static Window win;
    static Game game;
    static Gfx gfx;
    static Rpg g;
    static Path out;
    static String selection="";
    static void draw(float[] c) {
        float dx=c[3]-c[0],dy=c[4]-c[1],dz=c[5]-c[2],l=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        gfx.camera(c[0],c[1],c[2],dx/l,dy/l,dz/l,640,360,1f/60);
        gfx.scene(g,false,1f/60,g.state.hourOfDay());
        gfx.frame(640,360);
    }
    static BufferedImage pixels() {
        var px=gfx.pixels(640,360);
        var im=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<360;y++)for(int x=0;x<640;x++) {
            int i=((359-y)*640+x)*4;
            im.setRGB(x,y,(px.get(i)&255)<<16|(px.get(i+1)&255)<<8|(px.get(i+2)&255));
        }
        MemoryUtil.memFree(px);
        return im;
    }
    static void shot(String name,float[] c)throws Exception {
        if(!selection.isEmpty() && !name.equals(selection) && !(selection.equals("details") && (name.equals("leaves") || name.equals("city-slab")))) {
            for(int i=0;i<28;i++)game.tick(1f/60);
            return;
        }
        for(int i=0;i<28;i++) {
            game.tick(1f/60);
            draw(c);
        }
        ImageIO.write(pixels(),"png",out.resolve(name+".png").toFile());
        System.out.println("shot "+name);
    }
    public static void main(String[]args)throws Exception {
        selection=args.length>1?args[1]:"";
        out=Path.of(args[0]);
        Files.createDirectories(out);
        win=new Window();
        win.show=false;
        win.create("Release review",640,360);
        var cfg=new NativeEngine.Config();
        cfg.audioDevice=false;
        cfg.deterministic=true;
        try(Game current=Game.create(cfg)) {
            game=current;
            game.activate(DevMap.build(game.engine()));
            g=game.add(new Rpg(game,"Review",new Appearance(),true));
            g.state.minutes=600;
            g.ai.syncAbstractClock();
            g.player.sheathed=true;
            g.cfg.autoScale=false;
            for(var n:g.npcs)g.ai.unload(n);
            for(var a:g.animals.all()) {
                a.loaded=false;
                g.animals.despawnBody(a);
            }
            for(var m:g.monsters.all()) {
                m.loaded=false;
                g.monsters.despawnBody(m);
            }
            try(Gfx view=new Gfx(game.engine(),640,360)) {
                gfx=view;
                gfx.findLamps(g.vox);
                float f=DevMap.FLOOR;
                g.teleport(g.player,140,f,67);
                shot("palace-hedges",new float[]{139,f+4,67,136.5f,f+4,70.5f});
                g.teleport(g.player,111,f,58);
                shot("city-cell",new float[]{111.5f,f+2,58,117,f+1,54.5f});
                g.teleport(g.player,116.5f,f,58.5f);
                shot("city-slab",new float[]{116.0f,f+1.6f,55.5f,117.5f,f+.4f,54.5f});
                var tree=g.objects.nodes.stream().filter(n->n.kind==rpg.objects.Node.Kind.TREE).findFirst().orElseThrow();
                g.teleport(g.player,tree.x+3,f,tree.z-4);
                shot("leaves",new float[]{tree.x+3,f+5,tree.z-4,tree.x,f+5,tree.z});
                if(selection.equals("details"))return;
                g.teleport(g.player,40.5f,f,34.5f);
                for(int i=0;i<240;i++)game.tick(1f/60);
                var plant=g.foliage.chunks.values().stream().flatMap(c->c.plants.stream()).filter(p->p.kind==Foliage.Kind.BUSH&&!p.removed).findFirst().orElse(null);
                if(plant==null)plant=g.foliage.chunks.values().stream().flatMap(c->c.plants.stream()).filter(p->p.kind==Foliage.Kind.SHRUB&&!p.removed).findFirst().orElseThrow();
                shot("bush",new float[]{plant.x+1.5f,f+1.1f,plant.z-1.5f,plant.x,f+.35f,plant.z});
                if(selection.equals("bush"))return;
                g.objects.drop(new ItemStack(Items.get("iron_shield"),1),64.5f,f+1.2f,66.5f);
                g.objects.drop(new ItemStack(Items.get("iron_sword"),1),66.5f,f+1.5f,66.5f);
                g.teleport(g.player,63,f,70);
                for(int i=0;i<120;i++)game.tick(1f/60);
                shot("dropped-gear",new float[]{68,f+3,70,65.5f,f+.4f,66.5f});
                g.teleport(g.player,64.5f,f,65.5f);
                var p=g.player;
                p.eq.clear();
                p.eq.set(ItemDef.Slot.BODY,new ItemStack(Items.get("iron_body"),1));
                p.eq.set(ItemDef.Slot.LEGS,new ItemStack(Items.get("iron_legs"),1));
                p.eq.set(ItemDef.Slot.MAIN,new ItemStack(Items.get("iron_sword"),1));
                p.sheathed=false;
                p.sheatheAt=-99;
                p.poseReset=true;
                g.controller.snapTo((float)Math.PI/2);
                p.stamina=p.maxStamina();
                game.tick(0);
                g.combat.attack(p,false,Actor.Combat.Move.HEAVY);
                var sheet=new BufferedImage(640*4,378*5,BufferedImage.TYPE_INT_RGB);
                var paint=sheet.createGraphics();
                for(int frame=0;frame<60;frame++) {
                    game.tick(1f/60);
                    draw(new float[]{66.2f,f+1.25f,64.3f,64.5f,f+.9f,65.5f});
                    if(frame%3==0) {
                        int k=frame/3,x=k%4*640,y=k/4*378;
                        paint.setColor(java.awt.Color.WHITE);
                        paint.drawString("f"+(frame+1),x+4,y+13);
                        paint.drawImage(pixels(),x,y+18,null);
                    }
                }
                paint.dispose();
                ImageIO.write(sheet,"png",out.resolve("armored-attack.png").toFile());
                System.out.println("shot armored-attack");
                for(int frame=0;frame<50;frame++)game.tick(1f/60);
                p.stamina=p.maxStamina();
                g.combat.dodge(p,1,0);
                var dodge=new BufferedImage(640*4,378*5,BufferedImage.TYPE_INT_RGB);
                var labels=dodge.createGraphics();
                for(int frame=0;frame<60;frame++) {
                    game.tick(1f/60);
                    draw(new float[]{p.x+1.7f,f+1.25f,p.z-1.2f,p.x,f+.9f,p.z});
                    if(frame%3==0) {
                        int k=frame/3,x=k%4*640,y=k/4*378;
                        labels.setColor(java.awt.Color.WHITE);
                        labels.drawString("f"+(frame+1),x+4,y+13);
                        labels.drawImage(pixels(),x,y+18,null);
                    }
                }
                labels.dispose();
                ImageIO.write(dodge,"png",out.resolve("armored-dodge.png").toFile());
                System.out.println("shot armored-dodge");
            }
        }
        finally {
            win.destroy();
        }
    }
}
