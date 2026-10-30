package rpg.world;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.*;
import rpg.item.*;
import rpg.objects.*;
import static astratto.bridge.AstrattoNative.*;
import static org.junit.jupiter.api.Assertions.*;

class UltimateWorldTest {
    private Game game;private Rpg g;
    @BeforeEach void start(){var config=new NativeEngine.Config();config.deterministic=true;game=Game.create(config);game.activate(DevMap.build(game.engine()));g=game.add(new Rpg(game,"Ultimate world",Appearance.henric(),true));for(Npc npc:g.npcs)g.ai.unload(npc);g.state.minutes=600;g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();g.teleport(g.player,60.5f,DevMap.FLOOR,70.5f);game.tick(0);}
    @AfterEach void close(){game.close();}
    private void frames(int count){for(int frame=0;frame<count;frame++)assertTrue(game.tick(1f/60));}
    @Test void respawningAndHealingCannotRestoreAStaleBurningReceiver(@TempDir Path directory) {
        g.player.skills.setLevel(rpg.skill.Skill.HEALTH,100);g.player.restore();
        g.chemistry.emit(null,CHEM_FIRE,g.player.x,g.player.chestY(),g.player.z,0.6f,4,0.2f);frames(60);
        assertNotEquals(0,g.chemistry.state(g.player.id)&CHEM_BURNING);
        g.chemistry.cleanse(g.player);
        var saves=new SaveSystem(g);Path file=directory.resolve("healed.json");assertTrue(saves.save(file));assertTrue(saves.load(file));frames(60);
        assertEquals(0,g.chemistry.state(g.player.id)&CHEM_BURNING);
        g.respawn();frames(30);assertEquals(0,g.chemistry.state(g.player.id)&CHEM_BURNING);assertEquals(0,g.player.charred);
    }
    @Test void aBurningNpcDropsIntoSydneyAndSmothersTheFireByActualGroundContact() {
        g.teleport(g.player,50.5f,DevMap.FLOOR,70.5f);Npc guard=(Npc)g.actor(Ids.GUARD);g.ai.reload(guard);g.teleport(guard,64.5f,DevMap.FLOOR,70.5f);guard.skills.setLevel(rpg.skill.Skill.HEALTH,100);guard.restore();guard.nextThink=Double.MAX_VALUE;game.tick(0);
        g.chemistry.emit(null,CHEM_FIRE,guard.x,guard.chestY(),guard.z,0.6f,4,0.2f);
        frames(230);assertTrue(guard.reacting,"burning for three seconds must stop ordinary locomotion");assertTrue(guard.charred>0);
        frames(360);assertTrue(guard.alive);assertTrue(g.state.all().stream().anyMatch(event->event.type().equals("FireSmothered") && event.actor()==guard.id),()->"burning="+g.chemistry.state(guard.id)+" contact="+guard.rollContact+" body="+guard.ragdoll);assertEquals(0,g.chemistry.state(guard.id)&CHEM_BURNING,"smothered clothes must not reignite from expired self-emissions");
    }
    @Test void foliageKeepsValidSurfacesAndDestructionAcrossLoadWithoutSerializingDerivedPlants(@TempDir Path directory) throws Exception {
        assertFalse(g.foliage.suitable(Foliage.Kind.TALL_GRASS,64,DevMap.FLOOR,64),"paved debug floor is not soil");
        frames(180);var plant=g.foliage.chunks.values().stream().flatMap(chunk->chunk.plants.stream()).filter(patch->!patch.removed).findFirst().orElseThrow();
        assertTrue(g.foliage.suitable(plant.kind,(int)plant.x,(int)plant.y,(int)plant.z));long id=plant.id;g.foliage.remove(plant,"test cut");
        g.teleport(g.player,plant.x,DevMap.FLOOR,plant.z);game.tick(0);
        var saves=new SaveSystem(g);Path file=directory.resolve("foliage.json");assertTrue(saves.save(file));
        assertFalse(java.nio.file.Files.readString(file).contains("\"kind\":\"Patch\""));assertTrue(saves.load(file));frames(90);
        assertTrue(g.objects.get(id) instanceof Foliage.Patch restored && restored.removed,"derived growth cannot resurrect a cut plant");
        assertTrue(g.foliage.chunks.size()<=144);
    }
    @Test void lakeNetsRetainTheirActualToolAndCatchAcrossLoad(@TempDir Path directory) {
        Npc fisher=(Npc)g.actor(Ids.FISHERMAN);var source=g.objects.nodes.stream().filter(node->node.kind==Node.Kind.FISH && node.needs==Items.FISHING_NET).findFirst().orElseThrow();
        var net=g.objects.add(new FishingNet(g.state.newEntityId(),fisher.id,source.id,source.x,DevMap.FLOOR-0.1f,source.z,g.state.minutes+4));
        for(var tool:fisher.takeItems(Items.FISHING_NET,1))assertTrue(net.contents.add(tool));
        int before=source.stock;g.advanceTime(24,"net catch");net.update(g);
        assertEquals(5,net.contents.count(source.product));assertEquals(1,net.contents.count(Items.FISHING_NET));assertEquals(before,source.stock,"an unlimited fishing source must not underflow its stock");
        var saves=new SaveSystem(g);Path file=directory.resolve("net.json");assertTrue(saves.save(file));assertTrue(saves.load(file));
        var restored=(FishingNet)g.objects.get(net.id);restored.update(g);assertEquals(5,restored.contents.count(source.product));assertEquals(1,restored.contents.count(Items.FISHING_NET));
    }
    @Test void aFallingRigidBodyCreatesASplashOnTheActualWaterSurface() {
        g.teleport(g.player,96.5f,DevMap.FLOOR,70.5f);game.tick(0);
        long pebble=game.reserve(KIND_RIGID);
        game.commands().rigidCreate(pebble,SHAPE_SPHERE,0,0.12f,0.12f,0.12f,1,100.5f,7,72.5f,1,0,0,0,0,-3,0,0,0,0,0.5f,0,0,null);
        frames(1);long before=g.waterContacts.splashes;frames(90);
        assertTrue(g.waterContacts.splashes>before,"rigid items crossing the water must create spray and a ripple");
        game.commands().release(pebble);
    }

}
