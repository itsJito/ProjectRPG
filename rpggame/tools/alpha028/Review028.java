package rpg;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import astratto.platform.Window;
import rpg.actor.*;
import rpg.ai.*;
import rpg.render.*;
import rpg.world.*;
import rpg.item.*;
import rpg.interact.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;

/** Frames 1, 4, 7 ... 58 of shared NPC poses through the native renderer. */
public final class Review028 {
    static final int W=480,H=360;
    static Gfx gfx; static Rpg g; static Npc n; static Path out;static java.util.Set<String> only;
    static BufferedImage pixels() {
        var bytes=gfx.pixels(W,H); var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
            int i=((H-1-y)*W+x)*4;
            image.setRGB(x,y,(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|(bytes.get(i+2)&255));
        }
        MemoryUtil.memFree(bytes); return image;
    }
    static void draw(boolean finish) {
        float cx=n.x+1.7f,cy=n.y+1.4f,cz=n.z-2.2f;
        float dx=n.x-cx,dy=n.y+.95f-cy,dz=n.z-.15f-cz,l=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        gfx.camera(cx,cy,cz,dx/l,dy/l,dz/l,W,H,3f/60);
        boolean alive=g.player.alive;g.player.alive=false;
        gfx.scene(g,false,3f/60,12);g.player.alive=alive;
        if(finish)gfx.frame(W,H);
    }
    static void clip(String name,String weapon,String off,boolean strike,boolean block,boolean repair)throws Exception {
        if(only!=null && !only.contains(name))return;
        n.eq.clear(); n.cs.cancel();n.cs.blocking=block;n.poseReset=true;n.pitch=0;n.sheathed=false;n.sheatheAt=-99;
        if(weapon!=null)n.eq.set(ItemDef.Slot.MAIN,new ItemStack(Items.get(weapon),1));
        if(off!=null)n.eq.set(ItemDef.Slot.OFF,new ItemStack(Items.get(off),1));
        n.working=null;n.lootUntil=-99;n.grabAt=-99;
        if(repair) {
            n.reachX=n.x;n.reachY=n.y+1.15f;n.reachZ=n.z-.7f;
            var site=new WorldObject(999999,"repair_site","Damaged building",n.reachX,n.reachY,n.reachZ) {
                public java.util.List<Option> options(Rpg g,Actor a){return java.util.List.of();}
                public Result perform(Rpg g,Actor a,String s,long req){return Result.ok();}
            };
            n.working=new Work(n,site,"repair","Repairing",3.5f,false,g.time,()->Result.ok());
        }
        int frames=repair?210:60,rows=(frames+11)/12;
        var sheet=new BufferedImage(W*4,(H+20)*rows,BufferedImage.TYPE_INT_RGB);var labels=sheet.createGraphics();
        for(int frame=0;frame<frames;frame++) {
            if(strike) {
                n.cs.move=Actor.Combat.Move.HEAVY;n.cs.offHand=false;n.cs.windup=.3f;n.cs.recover=.3f;
                if(frame<18){n.cs.phase=Actor.Combat.Phase.WINDUP;n.cs.t=frame/60f;}
                else if(frame<42){n.cs.phase=Actor.Combat.Phase.ACTIVE;n.cs.sweep=(frame-18)/23f;}
                else{n.cs.phase=Actor.Combat.Phase.RECOVER;n.cs.t=(frame-42)/60f;}
            }
            if(repair)n.working.t=frame/60f;
            g.time+=1f/60;
            if(frame%3==0)draw(true);
            if(frame%3==0) {
                int k=frame/3,x=k%4*W,y=k/4*(H+20);
                labels.setColor(java.awt.Color.WHITE);labels.drawString(name+" f"+(frame+1),x+5,y+15);
                var image=pixels();labels.drawImage(image,x,y+20,null);
                if(frame==30)ImageIO.write(image,"png",out.resolve(name+"-close.png").toFile());
            }
        }
        labels.dispose();ImageIO.write(sheet,"png",out.resolve(name+"-threes.png").toFile());
        for(int row=0;row<rows;row+=2)ImageIO.write(sheet.getSubimage(0,row*(H+20),W*4,Math.min(2,rows-row)*(H+20)),"png",out.resolve(name+"-page-"+(row/2+1)+".png").toFile());
        System.out.println("captured "+name);
    }
    public static void main(String[] args)throws Exception {
        out=Path.of(args[0]);only=args.length>1?java.util.Set.of(args[1].split(",")):null;Files.createDirectories(out);
        var win=new Window();win.show=false;win.create("Alpha 0.2.8 pose review",W,H);
        var cfg=new NativeEngine.Config();cfg.audioDevice=false;cfg.deterministic=true;
        try(var game=Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));g=game.add(new Rpg(game,"Review",new Appearance(),true));
            g.state.minutes=720;g.cfg.autoScale=false;
            for(var person:g.npcs)g.ai.unload(person);
            n=(Npc)g.actor(Ids.VILLAGER);g.ai.reload(n);g.teleport(n,64.5f,DevMap.FLOOR,64.5f);
            n.nextThink=Double.MAX_VALUE;n.goal=Brain.Goal.IDLE;n.yaw=n.wantYaw=n.aimYaw=0;
            game.tick(0);
            try(var view=new Gfx(game.engine(),W,H)) {
                gfx=view;gfx.findLamps(g.vox);
                clip("pickaxe","bronze_pickaxe",null,true,false,false);
                clip("greatsword","bronze_2hsword",null,true,false,false);
                clip("battleaxe","bronze_battleaxe",null,true,false,false);
                clip("shield-carry","bronze_sword","bronze_shield",false,false,false);
                clip("shield-block","bronze_sword","bronze_shield",false,true,false);
                clip("repair",null,null,false,false,true);
            }
        } finally {win.destroy();}
    }
}
