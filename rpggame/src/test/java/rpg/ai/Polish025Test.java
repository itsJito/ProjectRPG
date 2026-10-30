package rpg.ai;
import astratto.bridge.NativeEngine;
import astratto.bridge.Snapshots;
import astratto.game.Game;
import astratto.util.Json;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import rpg.*;
import rpg.actor.*;
import rpg.item.*;
import rpg.world.*;
import static org.junit.jupiter.api.Assertions.*;
import static astratto.bridge.AstrattoNative.*;
class Polish025Test {
    Game game;
    Rpg g;
    @BeforeEach void open() {
        var cfg=new NativeEngine.Config();
        cfg.deterministic=true;
        cfg.audioDevice=false;
        game=Game.create(cfg);
        game.activate(DevMap.build(game.engine()));
        g=game.add(new Rpg(game,"Workshop",new Appearance(),true));
        g.state.minutes=600;
        for(var n:g.npcs)g.ai.unload(n);
        for(var a:g.animals.all()) {
            a.loaded=false;
            g.animals.despawnBody(a);
        }
        for(var m:g.monsters.all()) {
            m.loaded=false;
            g.monsters.despawnBody(m);
        }
        for(int x=96;x<=108;x++)for(int z=65;z<=76;z++)for(int y=DevMap.FLOOR;y<=DevMap.FLOOR+4;y++)g.setBlock(x,y,z,Blocks.AIR);
        g.teleport(g.player,98.5f,DevMap.FLOOR,74.5f);
        game.tick(0);
    }
    @AfterEach void close() {
        game.close();
    }
    Npc place(long id,float x,float z) {
        Npc n=(Npc)g.actor(id);
        g.ai.reload(n);
        g.teleport(n,x,DevMap.FLOOR,z);
        n.nextThink=Double.MAX_VALUE;
        n.goal=Brain.Goal.WORK;
        n.fightTarget=0;
        return n;
    }
    void observe() {
        for(int i=0;i<g.npcs.size()*2;i++) {
            g.time+=0.1;
            g.repairs.update();
        }
    }
    Repairs.Damage damage(int block) {
        g.repairs.broken(102,DevMap.FLOOR+1,70,block,"fire");
        return g.repairs.open().getFirst();
    }
    @Test void weaponDisplaysAreCautionaryNewsRatherThanGoodDeeds() {
        var witness=place(Ids.VILLAGER,100.5f,73.5f);
        g.teleport(g.player,100.5f,DevMap.FLOOR,70.5f);
        witness.yaw=0;
        game.tick(0);
        witness.memories.clear();
        g.ai.rumors.arms("swinging a sword");
        var memory=witness.memories.stream().filter(m->m.kind.equals("ARMS")).findFirst().orElseThrow();
        assertTrue(Rumors.news(memory));
        assertFalse(witness.memories.stream().anyMatch(m->m.kind.equals("DEED")));
        assertTrue(g.ai.rumors.opinion(witness)<0);
    }
    @SuppressWarnings("unchecked")
    @Test void lawmanReportsCommissionRealWorkAndPayOnlyOnCompletion() {
        var officer=place(Ids.LAWMAN_A,100.5f,70.5f);
        var builder=place(Ids.ARTHUR,104.5f,70.5f);
        var damage=damage(Blocks.FENCE);
        observe();
        g.time+=Repairs.SETTLE;
        g.teleport(officer,103.5f,DevMap.FLOOR,71.5f);
        game.tick(0);
        int officerCoins=officer.count(Items.COINS),workerCoins=builder.count(Items.COINS),planks=builder.count(Items.PLANK);
        assertTrue(g.repairs.report(officer,damage,builder));
        assertEquals(officerCoins,officer.count(Items.COINS));
        var saved=g.repairs.save();
        g.repairs.load(saved);
        assertEquals(builder.id,Json.lng(Json.obj((Map<String,Object>)saved.getFirst(),"order"),"worker",0));
        assertNotNull(g.repairs.claim(builder));
        g.teleport(builder,103.5f,DevMap.FLOOR,70.5f);
        game.tick(0);
        assertTrue(g.ai.brain.mend(builder));
        assertNotNull(g.interact.of(builder));
        for(int i=0;i<240 && g.repairs.contains(damage.key());i++)game.tick(1f/60);
        assertEquals(Blocks.FENCE,g.vox.block(102,DevMap.FLOOR+1,70));
        assertEquals(planks-1,builder.count(Items.PLANK));
        assertEquals(officerCoins-5,officer.count(Items.COINS));
        assertEquals(workerCoins+5,builder.count(Items.COINS));
        assertTrue(g.invariants.check().isEmpty(),g.invariants.check().toString());
    }
    @SuppressWarnings("unchecked")
    @Test void civilianReportDoesNotChargeTheReporterAndCanBeCommissionedByAnOfficer() {
        var civilian=place(Ids.VILLAGER,103.5f,71.5f);
        var officer=place(Ids.LAWMAN_A,103.5f,72.5f);
        var builder=place(Ids.ARTHUR,104.5f,70.5f);
        var damage=damage(Blocks.GLASS);
        observe();
        int coins=civilian.count(Items.COINS);
        assertTrue(g.repairs.report(civilian,damage,builder));
        assertEquals(0,Json.integer(Json.obj((Map<String,Object>)g.repairs.save().getFirst(),"order"),"fee",-1));
        assertNotNull(g.repairs.reportFor(officer));
        assertTrue(g.repairs.report(officer,damage,builder));
        g.time+=Repairs.SETTLE;
        assertTrue(g.repairs.mend(builder,damage));
        assertEquals(coins,civilian.count(Items.COINS));
    }
    @Test void interruptingARepairDoesNotConsumeOrPay() {
        var builder=place(Ids.ARTHUR,103.5f,70.5f);
        var damage=damage(Blocks.FENCE);
        observe();
        g.time+=Repairs.SETTLE;
        int planks=builder.count(Items.PLANK);
        assertTrue(g.ai.brain.mend(builder));
        g.interact.cancel(builder,"danger");
        assertEquals(planks,builder.count(Items.PLANK));
        assertTrue(g.repairs.contains(damage.key()));
        assertEquals(Blocks.AIR,g.vox.block(102,DevMap.FLOOR+1,70));
    }
    @Test void anActiveOpponentTracksVisibleMovementButStillCannotSeeThroughWalls() {
        var fighter=place(Ids.GUARD,100.5f,70.5f);
        g.teleport(g.player,100.5f,DevMap.FLOOR,73.5f);
        fighter.yaw=0;
        fighter.fightTarget=g.player.id;
        game.tick(0);
        assertTrue(g.ai.canSee(fighter,g.player));
        for(int y=DevMap.FLOOR;y<DevMap.FLOOR+4;y++)for(int x=98;x<104;x++)g.setBlock(x,y,72,Blocks.STONE);
        game.tick(0);
        assertFalse(g.ai.canSee(fighter,g.player));
    }
    @Test void aLostMoverRecoversTheSameLivingResidentWithoutReplacingTheirPossessions() {
        var resident=place(Ids.VILLAGER,100.5f,70.5f);
        game.tick(0);
        long old=resident.body;
        var items=ItemIO.inventory(resident.inv);
        game.commands().release(old);
        game.tick(0);
        for(int i=0;i<80;i++)game.tick(1f/60);
        assertTrue(resident.alive);
        assertNotEquals(old,resident.body);
        assertNotNull(g.lastBodies.mover(resident.body));
        assertEquals(items,ItemIO.inventory(resident.inv));
    }
    @Test void broadDroppedGearUsesNativeRotationAndKeepsItsPoseThroughSave(@TempDir Path dir) {
        var shield=new ItemStack(Items.get("iron_shield"),1);
        g.state.mint(shield.def,1);
        var loose=g.objects.drop(shield,100.5f,DevMap.FLOOR+2,70.5f);
        long body=loose.rigid;
        assertNotEquals(0,body);
        float qw=loose.rotation[0];
        for(int i=0;i<120;i++)game.tick(1f/60);
        assertTrue(loose.y<DevMap.FLOOR+1);
        assertNotEquals(qw,loose.rotation[0]);
        var saved=loose.rotation.clone();
        Path file=dir.resolve("gear.json");
        var saves=new SaveSystem(g);
        assertTrue(saves.save(file));
        assertTrue(saves.load(file));
        var restored=(rpg.objects.GroundItem)g.objects.get(loose.id);
        assertArrayEquals(saved,restored.rotation);
        assertTrue(g.invariants.check().isEmpty(),g.invariants.check().toString());
    }
    @Test void retiredEquipmentMigratesWithProvenanceAndConservedLedger(@TempDir Path dir)throws Exception {
        assertNull(Items.find("cursed_blade"));
        g.give(g.player,Items.get("black_sword"),1);
        var save=new SaveSystem(g);
        Path file=dir.resolve("legacy.json");
        assertTrue(save.save(file));
        String text=Files.readString(file).replace("black_sword","cursed_blade");
        Files.writeString(file,text);
        assertTrue(save.load(file));
        assertTrue(g.player.has(Items.get("black_sword"),1));
        assertEquals(0,g.state.ledger("cursed_blade"));
        assertTrue(g.invariants.check().isEmpty(),g.invariants.check().toString());
        assertEquals(Items.get("white_longsword"),ItemIO.stack(Map.of("id","spectral_blade","n",1,"owner",901L,"origin",92L)).def);
    }
    @Test void sentenceExpiryClearsAutomaticRelockingAndTheCitySlabIsInsideItsBars() {
        var door=g.objects.jailDoor(DevMap.ALPHA);
        g.law.all().computeIfAbsent(DevMap.ALPHA,k->new HashMap<>()).put(g.player.id,40);
        g.law.arrest(g.player,DevMap.ALPHA,(Npc)g.actor(Ids.GUARD));
        door.relock=true;
        door.autoOpened=true;
        g.state.minutes+=g.law.sentenceRemaining()+0.1;
        g.law.update();
        assertFalse(door.locked);
        assertFalse(door.relock);
        assertFalse(door.autoOpened);
        var cell=Jails.forJurisdiction(DevMap.ALPHA);
        assertTrue(cell.slab().x0()>cell.wallX());
        assertTrue(cell.slab().z0()>cell.backZ());
        assertEquals(Blocks.FENCE,g.vox.block(cell.wallX(),DevMap.FLOOR+1,54));
        assertEquals(Blocks.PLASTER,g.vox.block(cell.endX(),DevMap.FLOOR+1,54));
    }
    @Test void doorPermissionsApplyToShortcutsAndRefreshWhenTheDoorChanges() {
        var door=g.objects.jailDoor(DevMap.ALPHA);
        float x=door.x,z=door.z;
        assertFalse(Waypoints.walkable(g.vox,x,z-1,x,z+1,d->false));
        assertTrue(Waypoints.walkable(g.vox,x,z-1,x,z+1,d->true));
        long revision=g.vox.navigationRevision;
        g.setBlock(100,DevMap.FLOOR,70,Blocks.STONE);
        game.tick(0);
        assertTrue(g.vox.navigationRevision>revision);
        assertFalse(Waypoints.clear(g.vox,98.5f,70.5f,102.5f,70.5f));
        g.setBlock(100,DevMap.FLOOR,70,Blocks.AIR);
        game.tick(0);
        assertTrue(Waypoints.clear(g.vox,98.5f,70.5f,102.5f,70.5f));
    }
    @Test void fireKnowledgeRequiresVisibleUncontrolledFuelAndChangesByRole() {
        var civilian=place(Ids.VILLAGER,100.5f,70.5f);
        g.setBlock(103,DevMap.FLOOR,70,Blocks.PLANKS);game.tick(0);
        var burning=List.of(new Snapshots.Chemical(90,0,103.5f,DevMap.FLOOR+0.5f,70.5f,0.5f,0,CHEM_BURNING,1,0,1,Blocks.PLANKS,Blocks.PLANKS));
        g.chemistry.active=burning;
        assertNotNull(FireSafety.observe(g,civilian,8));
        g.ai.brain.think(civilian);
        assertEquals(Brain.Goal.FIRE_RESPONSE,civilian.goal);
        for(int y=DevMap.FLOOR;y<DevMap.FLOOR+3;y++)g.setBlock(102,y,70,Blocks.STONE);
        game.tick(0);
        g.chemistry.active=burning;
        assertNull(FireSafety.observe(g,civilian,8));
        g.chemistry.active=List.of(new Snapshots.Chemical(90,0,101.5f,DevMap.FLOOR+0.5f,70.5f,0.5f,0,CHEM_BURNING,1,0,1,Blocks.AIR,Blocks.AIR));
        assertNull(FireSafety.observe(g,civilian,8));
    }
    @Test void aRealBurningWallIsVisibleAtItsSurfaceWithoutSeeingThroughAnotherWall() {
        var civilian=place(Ids.VILLAGER,100.5f,70.5f);
        g.setBlock(103,DevMap.FLOOR,70,Blocks.PLANKS);game.tick(0);
        g.chemistry.emit(g.player,CHEM_FIRE,103.5f,DevMap.FLOOR+0.5f,70.5f,0.3f,3,0.5f);
        for(int frame=0;frame<240 && FireSafety.observe(g,civilian,8)==null;frame++)game.tick(1f/60);
        assertNotNull(FireSafety.observe(g,civilian,8),"a burning solid wall must not hide its own fire");
        for(int y=DevMap.FLOOR;y<=DevMap.FLOOR+3;y++)g.setBlock(102,y,70,Blocks.STONE);
        game.tick(0);
        assertNull(FireSafety.observe(g,civilian,8),"a separate intervening wall still occludes the hazard");
    }
}
