package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import rpg.*;
import rpg.actor.*;
import rpg.combat.Spells;
import rpg.item.*;
import rpg.objects.*;
import rpg.skill.Skill;
import rpg.world.*;
import static astratto.bridge.AstrattoNative.*;
import static org.junit.jupiter.api.Assertions.*;

class MaintenanceTest {
    private Game game;
    private Rpg g;
    @BeforeEach void start() {
        var config=new NativeEngine.Config(); config.deterministic=true;
        game=Game.create(config); game.activate(DevMap.build(game.engine()));
        g=game.add(new Rpg(game,"Maintenance",Appearance.henric(),true));
        for(Npc npc:g.npcs)g.ai.unload(npc);
        g.state.minutes=600;g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();
        g.teleport(g.player,60.5f,DevMap.FLOOR,70.5f);game.tick(0);
    }
    @AfterEach void close(){game.close();}
    private void frames(int count){for(int i=0;i<count;i++)assertTrue(game.tick(1f/60));}
    @Test void everyAuthoredDoorIsUpdatedAndAnNpcActuallyWalksThroughTheBarn() {
        assertEquals(g.objects.all().stream().filter(o->o instanceof Door).count(),g.objects.doors().size());
        Door barn=g.objects.doors().stream().filter(d->d.name.equals("Barn door")).findFirst().orElseThrow();
        Npc farmer=(Npc)g.actor(Ids.FARMER);g.ai.reload(farmer);farmer.goal=Brain.Goal.RETURN;farmer.nextThink=Double.MAX_VALUE;
        farmer.spawnX=barn.x;farmer.spawnZ=barn.z+3;
        g.teleport(farmer,barn.x,DevMap.FLOOR,barn.z-3);game.tick(0);
        frames(420);
        assertTrue(barn.open || farmer.z>barn.z+1,"NPC must unlatch the registered door: "+farmer.x+","+farmer.z+" -> "+farmer.gx+","+farmer.gz+" "+farmer.nativeAction+" "+farmer.lastOutcome);
        assertTrue(farmer.z>barn.z+0.8,()->"farmer="+farmer.x+","+farmer.z+" door="+java.util.Arrays.toString(barn.angle)+" "+farmer.lastOutcome);
    }
    @Test void anUnlatchedLeafCanSwingToBothSidesAndCloseFromEither() {
        Door door=g.objects.shedDoor;g.teleport(g.player,60.5f,DevMap.FLOOR,70.5f);
        for(float side:new float[]{1,-1}) {
            door.setOpen(g,true,null,0);
            for(long hinge:door.hinge)game.commands().jointMotor(hinge,side*2.4f,120);
            frames(95);
            for(float angle:door.angle)assertTrue(angle*side>2.4,()->"full swing: "+java.util.Arrays.toString(door.angle));
            door.setOpen(g,false,null,0);frames(180);
            for(float angle:door.angle)assertEquals(0,angle,0.09,"closing must return from the actual side");
        }
    }
    @Test void doorwayRaysUseThePanelInsteadOfBlockingAnEntireCell() {
        Door door=g.objects.shedDoor;float x=door.cells[0][0]+0.5f,z=door.cells[0][1]+0.5f,y=DevMap.FLOOR+1;
        var shut=g.vox.ray(x,y,z-1,0,0,1,2,false);assertTrue(shut.hit);assertEquals(Blocks.DOOR,shut.block);
        door.setOpen(g,true,null,0);frames(95);
        assertTrue(Math.abs(door.angle[0])>0.7);
        var open=g.vox.ray(x,y,z-1,0,0,1,2,false);
        assertFalse(open.hit,()->"phantom doorway block "+open.block+" at "+open.dist);
        assertTrue(g.vox.sight(x,y,z-1,x,y,z+1));
        // A closed panel still blocks bolts and sight, including the part before the virtual cell boundary.
        door.setOpen(g,false,null,0);frames(180);
        assertTrue(g.vox.ray(x,y,z-1,0,0,1,2,false).hit);assertFalse(g.vox.sight(x,y,z-1,x,y,z+1));
    }
    @Test void anNpcStandingInAnOpenDoorwayCanBeFocusedAndHitByAnActualArrow() {
        Door door=g.objects.shedDoor;door.setOpen(g,true,null,0);frames(95);
        float x=door.cells[0][0]+0.5f,z=door.cells[0][1]+0.5f;
        Npc target=(Npc)g.actor(Ids.VILLAGER);g.ai.reload(target);g.teleport(target,x,DevMap.FLOOR,z);
        target.nextThink=Double.MAX_VALUE;target.goal=Brain.Goal.IDLE;target.skills.setLevel(Skill.HEALTH,100);target.restore();
        g.teleport(g.player,x,DevMap.FLOOR,z-2.5f);g.player.eq.set(ItemDef.Slot.MAIN,new ItemStack(Items.SHORTBOW,1));
        g.player.eq.set(ItemDef.Slot.AMMO,new ItemStack(Items.BRONZE_ARROWS,4));game.tick(0);
        g.interact.computeFocus(g.player,x,target.chestY(),z-2.5f,0,0,1);
        assertSame(target,g.interact.focus,"an open door's interaction volume must not swallow the target");
        float pitch=(float)Math.atan2(target.chestY()-g.player.eyeY()+0.15f,2.5f);
        assertTrue(g.combat.shoot(g.player,(float)Math.PI,pitch,1));frames(20);
        assertEquals(g.player.id,target.cs.lastAttacker,"the arrow must pass the open aperture and hit the body");
    }

    @Test void allWildTreesYieldTheirRealWoodAndSurviveSaveLoadAsStumps(@TempDir Path temp) {
        g.player.skills.setLevel(Skill.WOODCUTTING,100);g.player.eq.set(ItemDef.Slot.MAIN,new ItemStack(Items.get("bronze_axe"),1));
        for(int[] at:DevMap.WILD_TREES) {
            Node tree=g.objects.nodeAt(at[0],DevMap.FLOOR+1,at[1]);assertNotNull(tree,"every authored trunk has a node");
            int before=g.player.count(tree.product) + g.objects.all().stream().filter(o->o instanceof GroundItem item && item.stack.def==tree.product).mapToInt(o->((GroundItem)o).stack.amount).sum();int strikes=tree.strikesNeeded(g.player);
            for(int i=0;i<strikes;i++)assertTrue(tree.strike(g,g.player,tree.x,tree.y,tree.z));
            assertTrue(tree.depleted());assertTrue(g.player.count(tree.product) + g.objects.all().stream().filter(o->o instanceof GroundItem item && item.stack.def==tree.product).mapToInt(o->((GroundItem)o).stack.amount).sum()>before,"yield must be carried or dropped, never lost");
        }
        frames(1);var save=new SaveSystem(g);Path file=temp.resolve("trees.json");assertTrue(save.save(file));assertTrue(save.load(file));
        for(int[] at:DevMap.WILD_TREES)assertTrue(g.objects.nodes.stream().anyMatch(n->n.bx==at[0]&&n.bz==at[1]&&n.depleted()));
    }
    @Test void boundAlliesChooseFollowingInsteadOfHunterMealsSleepOrCorpseLooting() {
        assertTrue(g.magic.cast(g.player,Spells.Spell.NECROCALL,0,0));
        Npc ally=g.npcs.stream().filter(n->n.summonOwner==g.player.id).findFirst().orElseThrow();
        ally.hunger=1;ally.fatigue=1;ally.social=1;g.state.minutes=1380;
        g.teleport(g.player,70.5f,DevMap.FLOOR,70.5f);game.tick(0);float before=ally.distTo(g.player);long[] xp=ally.skills.raw();
        frames(360);
        assertEquals(Brain.Goal.FOLLOW,ally.goal);assertTrue(ally.distTo(g.player)<4,()->"bound follower remained at "+ally.x+","+ally.z+" from "+before+": "+ally.lastOutcome);
        assertArrayEquals(xp,ally.skills.raw());assertNull(ally.profession);
    }
    @Test void morphCannotUseFirstPersonAndRestoresThePreferenceWhenItEnds() {
        g.player.firstPerson=true;g.player.morphT=0.6f;g.controller.update(1f/60);
        assertFalse(g.player.firstPerson);g.controller.in.toggleCamera=true;g.controller.update(1f/60);assertFalse(g.player.firstPerson);
        frames(45);assertTrue(g.player.firstPerson);assertFalse(g.player.morphCameraSaved);
    }
    @Test void residualFireKeepsBurningAfterPlayerDeathWithoutReopeningACombatCase() {
        Npc victim=(Npc)g.actor(Ids.VILLAGER);g.ai.reload(victim);g.teleport(victim,63.5f,DevMap.FLOOR,70.5f);
        victim.skills.setLevel(Skill.HEALTH,100);victim.restore();victim.nextThink=Double.MAX_VALUE;victim.goal=Brain.Goal.IDLE;
        game.tick(0);g.chemistry.emit(g.player,CHEM_FIRE,victim.x,victim.chestY(),victim.z,0.5f,4,0.3f);frames(45);
        assertNotEquals(0,g.chemistry.state(victim.id)&CHEM_BURNING);
        g.combat.bleedOutNow(g.player);frames(1);g.respawn();victim.fightTarget=0;victim.cs.lastAttacker=0;victim.nextThink=Double.MAX_VALUE;victim.goal=Brain.Goal.IDLE;
        float before=victim.hp;long revision=g.state.revision;frames(120);
        assertTrue(victim.hp<before,"physical fire remains harmful");assertEquals(0,victim.cs.lastAttacker);assertEquals(0,victim.fightTarget);
        assertFalse(g.state.all().stream().anyMatch(e->e.revision()>revision&&e.type().equals("CrimeCommitted")&&e.actor()==g.player.id));
        assertTrue(game.engine().chemistry().stream().noneMatch(s->s.owner()==g.player.id));
    }
    @Test void aFreshAttackImmediatelyAfterRespawnKeepsItsOwnAttribution() {
        Npc victim=(Npc)g.actor(Ids.VILLAGER);g.ai.reload(victim);g.teleport(victim,63.5f,DevMap.FLOOR,70.5f);
        victim.skills.setLevel(Skill.HEALTH,100);victim.restore();victim.nextThink=Double.MAX_VALUE;victim.goal=Brain.Goal.IDLE;
        game.tick(0);g.chemistry.emit(g.player,CHEM_FIRE,victim.x,victim.chestY(),victim.z,0.5f,4,0.2f);frames(45);
        g.combat.bleedOutNow(g.player);g.respawn();game.tick(0);
        victim.cs.lastAttacker=0;victim.fightTarget=0;victim.nextThink=Double.MAX_VALUE;
        g.chemistry.emit(g.player,CHEM_FIRE,victim.x,victim.chestY(),victim.z,0.5f,4,0.2f);frames(90);
        assertTrue(game.engine().chemistry().stream().anyMatch(sensor->sensor.id()==victim.id&&sensor.owner()==g.player.id));
        assertEquals(g.player.id,victim.cs.lastAttacker,"fresh attacks are not blacklisted with the retired life");
    }

    @Test void savingImmediatelyAfterDeathCannotReattributeOldFireAfterLoad(@TempDir Path temp) {
        Npc victim=(Npc)g.actor(Ids.VILLAGER);g.ai.reload(victim);g.teleport(victim,63.5f,DevMap.FLOOR,70.5f);
        victim.skills.setLevel(Skill.HEALTH,100);victim.restore();victim.nextThink=Double.MAX_VALUE;victim.goal=Brain.Goal.IDLE;
        var fire=g.objects.add(new Fire(g.state.newEntityId(),40.5f,DevMap.FLOOR,68.5f,g.state.minutes+30,g.player.id));
        game.tick(0);g.chemistry.emit(g.player,CHEM_FIRE,victim.x,victim.chestY(),victim.z,0.5f,4,0.2f);frames(45);
        g.combat.bleedOutNow(g.player);
        var save=new SaveSystem(g);Path file=temp.resolve("retired-fire.json");assertTrue(save.save(file));assertTrue(save.load(file));
        frames(30);
        assertTrue(game.engine().chemistry().stream().noneMatch(sensor->sensor.owner()==g.player.id));
        assertTrue(g.objects.get(fire.id).save().containsKey("attributed"));
        assertEquals(false,g.objects.get(fire.id).save().get("attributed"));
    }

    @Test void aPocketStackChangedDuringTheLiftTransfersOnlyWhatRemains() {
        Npc person=(Npc)g.actor(Ids.VILLAGER);g.ai.reload(person);g.teleport(person,62,DevMap.FLOOR,70.5f);person.nextThink=Double.MAX_VALUE;person.goal=Brain.Goal.IDLE;
        g.player.skills.setLevel(Skill.THIEVING,100);person.inv.clear();person.inv.add(new ItemStack(Items.IRON_ARROWS,11,person.id,false,person.id));g.player.inv.clear();game.tick(0);
        assertFalse(g.interact.request(g.player,person.pockets(),"take:0").rejected());
        ItemStack spent=person.inv.take(0,8);assertNotNull(spent);g.objects.drop(spent,person.x,DevMap.FLOOR+0.05f,person.z);
        frames(70);int attempts=0;
        while(g.player.count(Items.IRON_ARROWS)==0 && attempts++<20) {
            g.player.cs.staggered=0;person.fightTarget=0;person.cs.cancel();
            assertFalse(g.interact.request(g.player,person.pockets(),"take:0").rejected());frames(70);
        }
        assertEquals(3,g.player.count(Items.IRON_ARROWS));assertEquals(0,person.count(Items.IRON_ARROWS));
        assertEquals(8,g.objects.all().stream().filter(o->o instanceof GroundItem item && item.stack.def==Items.IRON_ARROWS).mapToInt(o->((GroundItem)o).stack.amount).sum());
    }

    @Test void selectedPocketTheftConservesTheItemAndMarksTheActualTransferredStack() {
        Npc person=(Npc)g.actor(Ids.VILLAGER);g.ai.reload(person);g.teleport(person,62,DevMap.FLOOR,70.5f);person.nextThink=Double.MAX_VALUE;person.goal=Brain.Goal.IDLE;
        g.player.skills.setLevel(Skill.THIEVING,100);person.inv.clear();person.inv.add(new ItemStack(Items.IRON_ARROWS,11,person.id,false,person.id));g.player.inv.clear();game.tick(0);
        int attempts=0;
        while(g.player.count(Items.IRON_ARROWS)==0 && attempts++<20) {
            g.player.cs.staggered=0;person.fightTarget=0;person.cs.cancel();
            assertFalse(g.interact.request(g.player,person.pockets(),"take:0").rejected());frames(70);
        }
        assertEquals(11,g.player.count(Items.IRON_ARROWS));assertEquals(0,person.count(Items.IRON_ARROWS));
        assertTrue(g.player.grids().stream().flatMap(grid->grid.stacks().stream()).anyMatch(s->s.stolen&&s.owner==person.id));
    }
}
