package rpg;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import astratto.platform.Window;
import rpg.actor.*;
import rpg.ai.*;
import rpg.render.*;
import rpg.ui.*;
import rpg.world.*;
import rpg.item.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;

/** Native-rendered environment/UI review, including every offered informational human dialogue. */
public final class Scene028 {
    static final int W=800,H=450;
    static Gfx gfx; static Rpg g;static Ui2D ui;static Path out;static String only;
    static void shot(String name,float[] c,boolean hud)throws Exception {
        if(only!=null && !only.equals(name))return;
        for(int frame=0;frame<(name.equals("wall-growth")?40:12);frame++) {
            float dx=c[3]-c[0],dy=c[4]-c[1],dz=c[5]-c[2],l=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            gfx.camera(c[0],c[1],c[2],dx/l,dy/l,dz/l,W,H,1f/60);
            boolean alive=g.player.alive;if(!hud)g.player.alive=false;
            gfx.scene(g,false,1f/60,g.state.hourOfDay());g.player.alive=alive;
            if(hud){ui.begin(gfx.cmd);g.ui.draw(ui,W,H,true,1f/60);ui.end();}
            gfx.frame(W,H);
        }
        var bytes=gfx.pixels(W,H);var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4;image.setRGB(x,y,(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|(bytes.get(i+2)&255));}
        MemoryUtil.memFree(bytes);ImageIO.write(image,"png",out.resolve(name+".png").toFile());System.out.println("captured "+name);
    }
    static Npc place(long id,float x,float z) {
        var n=(Npc)g.actor(id);g.ai.reload(n);g.teleport(n,x,4,z);n.goal=Brain.Goal.IDLE;n.nextThink=Double.MAX_VALUE;n.fightTarget=0;n.reacting=false;n.cs.lastHurt=-99;return n;
    }
    public static void main(String[] args)throws Exception {
        out=Path.of(args[0]);only=args.length>1?args[1]:null;Files.createDirectories(out);var win=new Window();win.show=false;win.create("Alpha 0.2.8 scene review",W,H);
        var cfg=new NativeEngine.Config();cfg.audioDevice=false;cfg.deterministic=true;
        try(var game=Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));g=game.add(new Rpg(game,"Review",Main.defaultLook(),true));g.state.minutes=720;g.ai.syncAbstractClock();g.cfg.autoScale=false;
            for(var n:g.npcs)g.ai.unload(n);
            try(var view=new Gfx(game.engine(),W,H)) {
                gfx=view;gfx.findLamps(g.vox);ui=new Ui2D(gfx.renderer());g.ui=new Hud(g,gfx);
                g.teleport(g.player,160,4,15);shot("stairs-ladder",new float[]{159,9,12,165,6,19},false);
                g.teleport(g.player,64.5f,4,76.5f);shot("fog-noon",new float[]{64.5f,7,76.5f,130,7,70},false);
                g.teleport(g.player,88.5f,4,12);for(int i=0;i<25;i++){g.time+=.26;g.foliage.update();}game.tick(0);
                shot("grove-roots",new float[]{88.5f,5.3f,12,87.5f,4.7f,16.5f},false);
                var ivy=g.foliage.chunks.values().stream().flatMap(c->c.plants.stream()).filter(p->!p.removed && p.kind==Foliage.Kind.IVY && g.vox.skyLight((int)p.x,(int)p.y+2,(int)p.z)>.9f).sorted(java.util.Comparator.comparingLong(p->p.id)).findFirst().orElseThrow();
                float sx=(float)Math.sin(ivy.facing),sz=(float)Math.cos(ivy.facing);
                shot("wall-growth",new float[]{ivy.x-sx*1.7f+sz*.6f,ivy.y+.9f,ivy.z-sz*1.7f-sx*.6f,ivy.x,ivy.y+.65f,ivy.z},false);
                Npc officer=place(Ids.LAWMAN_A,100.5f,70.5f),witness=place(Ids.VILLAGER,102.5f,70.5f),beggar=place(Ids.BEGGAR,101.5f,70.5f);
                g.teleport(g.player,101.5f,4,71.5f);game.tick(0);g.player.store(new ItemStack(Items.BREAD,2));g.state.mint(Items.BREAD,2);
                float[] camera={104,6,74,101,5,70};
                g.ui.dialog(g.dialogue.talk(beggar));shot("bread-offer",camera,true);g.ui.closeDialog();
                var m=new Memory("THEFT",g.player.id,officer.id,DevMap.ALPHA,g.state.minutes,1,"witnessed",280900,8,"a stolen loaf");m.witness=witness.id;witness.remember(m,24);
                var corroboration=new Memory("THEFT",g.player.id,officer.id,DevMap.ALPHA,g.state.minutes,1,"witnessed",280900,8,"a stolen loaf");corroboration.witness=beggar.id;beggar.remember(corroboration,24);
                g.law.report(witness,m,officer);shot("reported-witness-hud",camera,true);
                g.law.setBounty(g.player,DevMap.ALPHA,14);g.ui.dialog(g.dialogue.talk(officer));shot("fourteen-gold",camera,true);g.ui.closeDialog();
                g.law.setBounty(g.player,DevMap.ALPHA,0);
                int people=0,replies=0;
                for(var n:g.npcs) {
                    if(!n.alive || n.hostileToPlayer || n.role==Npc.Role.MONSTER)continue;
                    g.ai.reload(n);g.teleport(n,102.5f,4,71.5f);n.goal=Brain.Goal.IDLE;n.fightTarget=0;n.reacting=false;n.cs.lastHurt=-99;n.memories.clear();n.arrestedUntil=-1;
                    var root=g.dialogue.talk(n);people++;
                    for(int i=0;i<root.choices.size();i++) {
                        String label=root.choices.get(i).label();
                        if(!(label.startsWith("What ") || label.startsWith("How ") || label.startsWith("Where ") || label.startsWith("Who ") || label.startsWith("Any ") || label.startsWith("When ")))continue;
                        g.ui.dialog(root);g.ui.choose(i);
                        if(g.ui.dialog==null || g.ui.dialog.text.isBlank() || g.ui.dialog.npc!=n.id)throw new AssertionError(n.name+" / "+label);
                        replies++;
                    }
                    g.ui.closeDialog();g.ai.unload(n);
                }
                System.out.println("human dialogue review: "+people+" people, "+replies+" informational replies passed");
            }
        } finally {win.destroy();}
    }
}
