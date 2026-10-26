package rpg;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import rpg.actor.*;
import rpg.ai.*;
import rpg.item.*;
import rpg.monster.*;
import rpg.objects.*;
import rpg.skill.Skill;
import rpg.world.*;
import static astratto.bridge.AstrattoNative.*;
import static org.junit.jupiter.api.Assertions.*;

class PolishRegressionTest {
    Game game; Rpg g;
    @BeforeEach void open() {
        var config=new NativeEngine.Config();config.deterministic=true;
        game=Game.create(config);game.activate(DevMap.build(game.engine()));
        g=game.add(new Rpg(game,"Polish",Appearance.henric(),true));
        for(var npc:g.npcs)g.ai.unload(npc);
        g.state.minutes=600;g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);
        game.tick(0);
    }
    @AfterEach void close(){game.close();}
    void frames(int count){for(int i=0;i<count;i++)assertTrue(game.tick(1f/60));}
    @Test void ghostsStayBelowDungeonCeilingsEvenWhenTheirFlightGoalIsAboveTheRoof() {
        var layout=DungeonLayout.all().stream().filter(l->l.theme==DungeonLayout.Theme.CRYPT).findFirst().orElseThrow();
        var ghost=g.monsters.spawn(MonsterKind.GHOST,50,layout.worldX(layout.entryX)+0.5f,layout.worldZ(layout.entryZ)+0.5f,null);
        ghost.puppet=true;ghost.wantAltitude=20;
        frames(300);
        assertTrue(ghost.y+ghost.height<DungeonLayout.ROCK_TOP-1,"ghost at "+ghost.y);
        assertTrue(ghost.y<DevMap.FLOOR+2,"flight must use the room's floor and ceiling");
    }
    @Test void dungeonEntryConfiguresSkillDerivedLevelsWithoutAwardingNpcXp() {
        for(var skill:Skill.ALL)g.player.skills.setLevel(skill,32);
        int playerLevel=g.player.skills.combatLevel();long awards=g.prog.awards;
        for(var layout:DungeonLayout.all()) {
            g.dungeons.configureEncounter(layout);
            for(var monster:g.monsters.all())if(DungeonLayout.at(monster.lairX,monster.lairZ)==layout){
                assertEquals(monster.level,monster.skills.combatLevel(),monster.label());
                if(layout.type==DungeonLayout.Type.MICRO||layout.type==DungeonLayout.Type.MACRO)assertTrue(monster.level<=playerLevel);
                else assertTrue(monster.level>playerLevel);
            }
        }
        assertEquals(awards,g.prog.awards,"encounter configuration never awards XP");
    }
    @Test void monsterLootIsLawfulAndDecayKeepsOriginalItemsAcrossSave(@TempDir Path dir) {
        var monster=g.monsters.spawn(MonsterKind.GOBLIN,4,66.5f,70.5f,null);
        long id=monster.id;g.combat.bleedOutNow(monster);frames(3);
        var corpse=(Corpse)g.objects.get(monster.corpse);
        assertNotNull(corpse);assertFalse(corpse.isTheft(g.player));
        ItemStack keepsake=new ItemStack(Items.get("uncut_ruby"),1);g.state.mint(keepsake.def,1);assertTrue(corpse.contents.add(keepsake));
        assertEquals(0,Monsters.dissolution(monster,monster.diedAt+59.99));
        assertEquals(0.5f,Monsters.dissolution(monster,monster.diedAt+61.5),0.001);
        g.time=monster.diedAt+61.5;
        var file=dir.resolve("decay.json");assertTrue(new SaveSystem(g).save(file));assertTrue(new SaveSystem(g).load(file));
        var restored=g.monsters.byId(id);assertNotNull(restored);
        assertEquals(0.5f,Monsters.dissolution(restored,g.time),0.01);
        assertInstanceOf(Corpse.class,g.objects.get(restored.corpse));
        assertFalse(((Corpse)g.objects.get(restored.corpse)).isTheft(g.player));
        var errorsBeforeDecay = new java.util.HashSet<>(g.invariants.check());
        frames(100);assertNull(g.monsters.byId(id));
        assertTrue(g.objects.all().stream().anyMatch(o->o instanceof GroundItem item&&item.stack.def==keepsake.def));
        var addedErrors=new java.util.HashSet<>(g.invariants.check());addedErrors.removeAll(errorsBeforeDecay);
        assertTrue(addedErrors.isEmpty(),addedErrors.toString());
    }
    @Test void aStartingPlayerCanRecoverFromShockBesideAWallAndWalkAgain() {
        g.teleport(g.player,64.5f,DevMap.FLOOR,70.5f);
        for(int z=66;z<=75;z++)for(int y=DevMap.FLOOR;y<DevMap.FLOOR+3;y++)g.setBlock(63,y,z,Blocks.STONE);
        g.reactions.shocked(g.player);frames(2);assertTrue(g.player.reacting);
        frames(1000);assertFalse(g.player.reacting,"Sydney must return control");
        assertTrue(g.reactions.nativeRecoveries>0,"must actually regain balance in Sydney, not just time out");
        assertEquals(0,g.player.ragdoll);assertTrue(g.traversal.free(g.player,g.player.x,g.player.y,g.player.z));
        assertEquals(DevMap.FLOOR,g.player.y,0.12,"ankle joint height must not be used as the floor");
        float x=g.player.x,z=g.player.z;g.controller.in.forward=1;frames(120);g.controller.in.forward=0;
        assertTrue(Math.hypot(g.player.x-x,g.player.z-z)>1,"movement works after recovery");
    }
    @Test void visiblePropsHaveMatchingPhysicalAndRayCollision() {
        var board=g.objects.all().stream().filter(o->o instanceof Fixtures.Board).findFirst().orElseThrow();
        assertTrue(g.objects.collisions.overlaps(board.x,DevMap.FLOOR+1.2f,board.z,0.1f,0.3f));
        var hit=g.vox.ray(board.x,DevMap.FLOOR+1.5f,board.z-2,0,0,1,4,false);
        assertTrue(hit.hit);assertTrue(hit.dist<2,"warrant surface blocks projectiles");
        for(var door:g.dungeons.doors)if(!door.inside){
            assertTrue(g.objects.collisions.overlaps(door.x,DevMap.FLOOR+1.5f,door.z+0.7f,0.2f,0.4f),door.name+" roof");
            assertFalse(g.objects.collisions.overlaps(door.x,DevMap.FLOOR+0.4f,door.z-0.9f,0.15f,0.4f),"entrance approach remains open");
        }
    }
    @Test void contentCatalogCoversEveryCreatureOccupationAndFixtureAndSpawnsPersist(@TempDir Path dir) {
        var content=new DebugContent(g);var catalog=content.catalog();
        for(var kind:MonsterKind.values())assertTrue(catalog.containsKey("monster:"+kind.name().toLowerCase()));
        for(var profession:Profession.values())assertTrue(catalog.containsKey("npc:"+profession.name().toLowerCase()));
        for(var object:g.objects.everything())if(object.id<Ids.DYNAMIC)assertTrue(catalog.containsKey("object:"+object.id));
        content.spawn("npc:carpenter",false,0,65.5f,70.5f);
        content.spawn("monster:orc",false,28,67.5f,70.5f);
        content.spawn("furniture:bookcase",false,0,69.5f,70.5f);
        var npc=g.npcs.stream().filter(n->n.debugSpawn).findFirst().orElseThrow();long id=npc.id;
        var furniture=g.objects.all().stream().filter(o->o.debugPrefab!=null).findFirst().orElseThrow();long objectId=furniture.id;
        var file=dir.resolve("spawns.json");assertTrue(new SaveSystem(g).save(file));assertTrue(new SaveSystem(g).load(file));
        assertInstanceOf(Npc.class,g.actor(id));assertEquals(Profession.CARPENTER,((Npc)g.actor(id)).profession);
        assertInstanceOf(Furniture.class,g.objects.get(objectId));
        assertTrue(new SaveSystem(g).load(file));
        assertEquals(1,g.objects.furniture.stream().filter(object->object.id==objectId).count(),"repeated loads must not accumulate invisible prefab registrations");
    }
    @Test void crimeNoticesThrottleByTypeWithoutDroppingDifferentCrimes() {
        Chat chat=new Chat();chat.now=10;chat.crime("THEFT","first theft");chat.crime("THEFT","second theft");chat.crime("ASSAULT","first assault");
        assertEquals(2,chat.total());chat.now=39.99;chat.crime("THEFT","still paused");assertEquals(2,chat.total());
        chat.now=40;chat.crime("THEFT","new theft");assertEquals(3,chat.total());
    }
}
