package rpg.ui;

import astratto.bridge.*;
import astratto.game.Game;
import astratto.platform.Window;
import org.junit.jupiter.api.Test;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.*;
import rpg.item.*;
import rpg.render.Gfx;
import rpg.skill.*;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.lwjgl.glfw.GLFW.*;

class PolishUiTest {
    private static int width,height;
    @Test void cursorCelebrationsAndExpandedVaultRenderWithTheirActualState() throws Exception {
        assumeFalse("1".equals(System.getenv("ASTRATTO_HEADLESS")));
        Window window=new Window();window.show=false;window.create("Polish UI checks",1600,900);
        width=window.width;height=window.height;
        try {
            window.setCursorLocked(false);assertEquals(GLFW_CURSOR_HIDDEN,glfwGetInputMode(window.handle,GLFW_CURSOR));
            window.setCursorLocked(true);window.setCursorLocked(false);assertEquals(GLFW_CURSOR_HIDDEN,glfwGetInputMode(window.handle,GLFW_CURSOR));
            var config=new NativeEngine.Config();config.deterministic=true;
            try(Game game=Game.create(config)){
                game.activate(DevMap.build(game.engine()));Rpg g=game.add(new Rpg(game,"Polish UI",Appearance.henric(),true));game.tick(0);
                try(Gfx gfx=new Gfx(game.engine(),width,height)){
                    Ui2D ui=new Ui2D(gfx.renderer());Hud hud=new Hud(g,gfx);g.ui=hud;
                    try {
                        assertSame(ui.body,ui.heading,"Friz is the main interface and celebration face");assertNotSame(ui.body,ui.lore);
                        g.time=7200; // Save/load and pauses make the world and interface clocks different.
                        g.prog.awardRaw(g.player,Skill.ATTACK,XpTable.xpFor(12),"test",0,g.state.newRequestId());
                        g.prog.awardRaw(g.player,Skill.WOODCUTTING,XpTable.xpFor(3),"test",0,g.state.newRequestId());
                        assertEquals(2,g.prog.banners.size());
                        for(int i=0;i<20;i++)paint(gfx,ui,hud,g,0.05f);
                        assertTrue(hud.alphaOf("levelbanner")>0.99);assertEquals(1,g.prog.banners.size(),"later skill award waits behind combat congratulations");
                        shot(gfx,"celebration");
                        for(int i=0;i<60;i++)paint(gfx,ui,hud,g,0.05f);
                        assertEquals(0,g.prog.banners.size());assertTrue(hud.alphaOf("levelbanner")>0.99);
                        var teller=(Npc)g.actor(Ids.TELLER);teller.loaded=true;teller.alive=true;teller.goal=Brain.Goal.WORK;
                        g.teleport(teller,DevMap.BANK_TELLER[0],DevMap.FLOOR,DevMap.BANK_TELLER[1]);
                        float[] at=g.bank.customerSpot();g.teleport(g.player,at[0],DevMap.FLOOR,at[1]);
                        g.give(g.player,Items.COINS,30000);assertTrue(g.bank.openAccount(g.player,g.state.newRequestId()).committed());
                        var vault=g.bank.vaults(g.player.id).get(0);hud.openContainer(vault);
                        for(int i=0;i<6;i++)paint(gfx,ui,hud,g,0.05f);
                        float[] first=hud.cellOnScreen(vault.contents,0,0),next=hud.cellOnScreen(vault.contents,1,0);
                        float originalSize=next[0]-first[0];shot(gfx,"bank-before");
                        for(int i=0;i<4;i++)assertTrue(g.bank.buyVault(g.player,g.state.newRequestId()).committed());
                        paint(gfx,ui,hud,g,0.05f);
                        first=hud.cellOnScreen(vault.contents,0,0);next=hud.cellOnScreen(vault.contents,1,0);
                        assertEquals(originalSize,next[0]-first[0],0.001,"expansion appends slots without shrinking them");
                        ui.mx=first[0]+5;ui.my=first[1]+5;ui.scroll=-1;paint(gfx,ui,hud,g,0.05f);ui.scroll=0;
                        assertTrue(hud.cellOnScreen(vault.contents,0,0)[1]<first[1],"additional rows scroll into the fixed-size viewport");
                        shot(gfx,"bank-expanded");
                        assertEquals(0,hud.alphaOf("equipment"));
                        for(var npc:g.npcs)g.ai.unload(npc);
                        g.teleport(g.player,62.5f,DevMap.FLOOR,76.5f);
                        var corpse=g.monsters.spawn(rpg.monster.MonsterKind.GOBLIN,6,66.5f,70.5f,null);
                        g.combat.bleedOutNow(corpse);
                        for(int i=0;i<120;i++)game.tick(1f/60);
                        for(double age:new double[]{59,61.5,62.8}) {
                            g.time=corpse.diedAt+age;
                            for(int i=0;i<3;i++){
                                gfx.camera(corpse.x,DevMap.FLOOR+2,corpse.z+2.8f,0,-0.45f,-1,width,height,0.05f);
                                gfx.scene(g,false,0,12);assertNotNull(gfx.frame(width,height));
                            }
                            shot(gfx,"corpse-"+age);
                        }

                    } finally {hud.close();}
                }
            }
        } finally {window.destroy();}
    }
    private static void paint(Gfx gfx,Ui2D ui,Hud hud,Rpg g,float dt){
        gfx.camera(g.player.x, g.player.eyeY(),g.player.z+3,0,0,-1,width,height,dt);
        gfx.scene(g,false,dt,12);ui.begin(gfx.cmd);hud.draw(ui,width,height,true,dt);ui.end();assertNotNull(gfx.frame(width,height));
    }
    private static void shot(Gfx gfx,String name)throws Exception{
        var pixels=gfx.pixels(width,height);var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=(y*width+x)*4;image.setRGB(x,height-1-y,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));}
        java.nio.file.Path dir=java.nio.file.Path.of("build/polish-shots");java.nio.file.Files.createDirectories(dir);javax.imageio.ImageIO.write(image,"png",dir.resolve(name+".png").toFile());
    }
}
