package rpg;
import astratto.bridge.NativeEngine;import astratto.game.Game;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;
import rpg.actor.*;import rpg.ai.*;import rpg.item.*;import rpg.world.*;import rpg.monster.*;import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;import static astratto.bridge.AstrattoNative.*;
class PolishSystemsTest {
 Game game;Rpg g;
 @BeforeEach void open(){var c=new NativeEngine.Config();c.deterministic=true;game=Game.create(c);game.activate(DevMap.build(game.engine()));g=game.add(new Rpg(game,"Polish",new Appearance(),true));for(var n:g.npcs)g.ai.unload(n);}
 @AfterEach void close(){game.close();}
 @Test void jailEscapeRestoresItsSavedBountyOnceAndExpiryReleasesNormally(@TempDir Path dir){
  g.law.all().computeIfAbsent(DevMap.ALPHA,k->new java.util.HashMap<>()).put(g.player.id,40);
  g.law.arrest(g.player,DevMap.ALPHA,(Npc)g.actor(Ids.GUARD));assertTrue(g.law.inCustody(g.player));
  Path file=dir.resolve("custody.json");assertTrue(new SaveSystem(g).save(file));assertTrue(new SaveSystem(g).load(file));
  g.teleport(g.player,100.5f,DevMap.FLOOR,70.5f);g.law.update();
  assertFalse(g.law.inCustody(g.player));assertEquals(115,g.law.bounty(g.player.id,DevMap.ALPHA));g.law.update();assertEquals(115,g.law.bounty(g.player.id,DevMap.ALPHA));
  g.law.arrest(g.player,DevMap.ALPHA,(Npc)g.actor(Ids.GUARD));g.state.minutes+=g.law.sentenceRemaining();
  g.teleport(g.player,100.5f,DevMap.FLOOR,70.5f);g.law.update();assertEquals(0,g.law.bounty(g.player.id,DevMap.ALPHA));
 }
 @Test void fullHealingClearsLimpButDoesNotRestoreASeveredLimb(){
  var wound=g.gore.of(g.player);wound.hurt[rpg.combat.Gore.Part.LEG_R.ordinal()]=0.8f;wound.bleed=0.5f;
  g.player.hp=g.player.maxHp();g.gore.update(1f/60);assertEquals(0,wound.hurt[rpg.combat.Gore.Part.LEG_R.ordinal()]);assertEquals(0,wound.bleed);
  var npc=(Npc)g.actor(Ids.WORKER);var lost=g.gore.of(npc);lost.severed[rpg.combat.Gore.Part.LEG_R.ordinal()]=true;lost.hurt[rpg.combat.Gore.Part.LEG_R.ordinal()]=1;
  npc.restore();assertTrue(g.gore.lost(npc,rpg.combat.Gore.Part.LEG_R));
 }
 @Test void olderCustodyRecordsRecoverTheOriginalBountyFromTheirArrestEvent(){
  g.law.all().computeIfAbsent(DevMap.ALPHA,k->new java.util.HashMap<>()).put(g.player.id,40);
  g.law.arrest(g.player,DevMap.ALPHA,(Npc)g.actor(Ids.GUARD));
  var saved=g.law.save();
  @SuppressWarnings("unchecked") var custody=new java.util.HashMap<>((java.util.Map<String,Object>)saved.get("_custody"));
  custody.remove("bounty");saved.put("_custody",custody);g.law.load(saved);
  g.teleport(g.player,100.5f,DevMap.FLOOR,70.5f);g.law.update();
  assertEquals(115,g.law.bounty(g.player.id,DevMap.ALPHA));
 }
 @Test void rangedAimTracksOnlyVisibleTargetsAndLetsThePlayerBreakAway(){
  var p=g.player;g.teleport(p,64.5f,DevMap.FLOOR,66.5f);p.firstPerson=true;p.headValid=false;
  for(int x=63;x<=66;x++)for(int z=57;z<=67;z++)for(int y=DevMap.FLOOR;y<=DevMap.FLOOR+3;y++)g.setBlock(x,y,z,Blocks.AIR);
  var target=g.monsters.display(MonsterKind.GOBLIN,20,64.8f,58.5f,0);
  game.tick(0);
  p.eq.set(ItemDef.Slot.MAIN,new ItemStack(Items.SHORTBOW,1));p.sheathed=false;p.sheatheAt=-99;
  var c=g.controller;c.camYaw=0;c.camPitch=(float)Math.atan2(target.chestY()-p.eyeY(),8);
  c.in.mainDown=true;c.update(1f/60);assertEquals(target.id,c.softTarget);assertTrue(c.camYaw<0,"aim should ease toward the target");
  c.in.lookYaw=0.4f;c.update(1f/60);assertEquals(0,c.softTarget,"manual look breaks the cone");
  c.camYaw=0;c.update(1f/60);assertEquals(target.id,c.softTarget);
  for(int x=63;x<=66;x++)for(int y=DevMap.FLOOR;y<=DevMap.FLOOR+3;y++)g.setBlock(x,y,62,Blocks.STONE);
  game.tick(0);
  c.update(1f/60);assertEquals(0,c.softTarget,"a wall must break targeting");
  for(int x=63;x<=66;x++)for(int y=DevMap.FLOOR;y<=DevMap.FLOOR+3;y++)g.setBlock(x,y,62,Blocks.AIR);
  game.tick(0);
  c.in.mainDown=false;p.cs.draw=p.cs.aim=0;c.update(1f/60);assertEquals(0,c.softTarget,"lowering the weapon releases targeting");
  p.eq.set(ItemDef.Slot.MAIN,new ItemStack(Items.get("bronze_sword"),1));c.in.mainDown=true;c.update(1f/60);assertEquals(0,c.softTarget,"melee remains free aim");
 }
 @Test void monstersBeginTheirFirstStrikeAndNaturalWeaponsCannotBeSheathed(){
  for(var kind:new MonsterKind[]{MonsterKind.GOBLIN,MonsterKind.SKELETON,MonsterKind.ZOMBIE,MonsterKind.GHOST}){
   var m=g.monsters.display(kind,20,64.5f,64.5f,0);assertFalse(m.sheathed);
   assertTrue(g.combat.attack(m,false,rpg.combat.CombatSystem.lightMove(m,false)),kind.name());assertEquals(Actor.Combat.Phase.WINDUP,m.cs.phase);
   g.monsters.remove(m);
  }
  var ghost=g.monsters.display(MonsterKind.GHOST,20,64.5f,64.5f,0);assertFalse(ghost.sheathable());
 }
 @Test void foliageDefinitionsArePassableAndHedgesKeepTheirLeafRendering(){
  var defs=Blocks.engineDefs();for(int block=1;block<Blocks.COUNT;block++)if(Blocks.material(block)==Blocks.Material.FOLIAGE)assertEquals(0,defs[block].flags&BLOCK_SOLID,"block "+block);
  assertTrue(MonsterKind.GIANT_SPIDER.mw<2);assertTrue(MonsterKind.GIANT_SPIDER.hw<MonsterKind.GIANT_SPIDER.mw);
 }
 @Test void allCellsHaveWallsRoofsAndIronDoors(){
  for(var cell:Jails.CELLS){assertTrue(g.objects.jailDoor(cell.jurisdiction()).cellDoor);
   for(int z=Math.min(cell.frontZ(),cell.backZ());z<=Math.max(cell.frontZ(),cell.backZ());z++){
    assertTrue(g.vox.solid(cell.wallX(),DevMap.FLOOR+1,z));assertTrue(g.vox.solid(cell.endX(),DevMap.FLOOR+1,z));
    for(int x=cell.wallX();x<=cell.endX();x++)assertTrue(g.vox.solid(x,DevMap.FLOOR+3,z));
   }
  }
 }
 @Test void supportResidentsAndTheirMarketsSurviveRepeatedSavesWithoutDuplicates(@TempDir Path dir)throws Exception{
  assertEquals(70,g.npcs.size());assertNotNull(g.econ.shopOf((Npc)g.actor(Ids.TRADER_CITY)));
  var save=new SaveSystem(g);Path file=dir.resolve("roster.json");assertTrue(save.save(file));assertTrue(save.load(file));
  assertEquals(70,g.npcs.size());assertTrue(g.invariants.check().isEmpty(),g.invariants.check().toString());
  assertTrue(save.save(file));assertTrue(save.load(file));assertEquals(70,g.npcs.size());assertTrue(g.invariants.check().isEmpty(),g.invariants.check().toString());
 }
}
