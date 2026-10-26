package rpg.ai;

import rpg.Rpg;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.item.Items;
import rpg.objects.Trap;
import rpg.world.Blocks;
import rpg.world.DevMap;
import static astratto.bridge.AstrattoNative.*;

/** Bounded deployed snares, selected from observed wildlife and triggered by actual contact. */
public final class Traps {
    private final Rpg g;
    private double next;
    public Traps(Rpg g) { this.g=g; }
    public void afterLoad() {next=0;update();}
    public void update() {
        if(g.time<next)return; next=g.time+0.25;
        for(var object:g.objects.all())if(object instanceof Trap trap) {
            g.game.commands().chemSensor(trap.id,trap.owner,trap.x,trap.y+0.1f,trap.z,0.3f,CHEM_FLAMMABLE,0,3,0,0,false);
            if((g.chemistry.state(trap.id)&CHEM_BURNT)!=0) {
                for(var item:trap.contents.stacks())g.state.burn(item.def,item.amount);
                trap.contents.clear();trap.removed=true;g.game.commands().chemRemove(trap.id);
            }
            if (trap.quarry != 0) {
                var quarry = g.actor(trap.quarry);
                if (!trap.removed && trap.caught == 0 && trap.contents.has(Items.SNARE,1) && quarry != null && quarry.alive
                        && quarry.grounded && quarry.distTo(trap.x,trap.z)<quarry.radius+0.2f && Math.abs(quarry.y-trap.y)<0.5f) {
                    trap.caught=quarry.id; quarry.cs.slowT=Math.max(quarry.cs.slowT,6); quarry.cs.staggered=Math.max(quarry.cs.staggered,0.65f);
                    g.state.commit("BountySnareSprung",trap.owner,quarry.id,DevMap.jurisdiction(trap.x,trap.z),0,"confirmed physical contact");
                    if(quarry==g.player)g.chat.warn("A hunter's snare catches your leg!");
                    if(g.fx!=null)g.fx.puff(trap.x,trap.y+0.2f,trap.z);
                }
                continue;
            }
            Animal caught=g.animals.byId(trap.caught);
            if(caught!=null && caught.alive && !trap.removed && trap.contents.has(Items.SNARE,1))caught.trappedBy=trap.id;
            if(caught!=null && !caught.alive)caught.trappedBy=0;
            if(caught!=null && (trap.removed || !trap.contents.has(Items.SNARE,1) || caught.butchered)) { caught.trappedBy=0;trap.caught=0; }
            if(trap.removed || !trap.contents.has(Items.SNARE,1) || trap.caught!=0)continue;
            for(Animal animal:g.animals.all())if(animal.alive && animal.loaded && animal.owner==0 && animal.species==Species.RABBIT && animal.trappedBy==0 && animal.distTo(trap.x,trap.z)<0.4f) {
                trap.caught=animal.id;animal.trappedBy=trap.id;trap.checkedAt=g.state.minutes;
                g.state.commit("AnimalTrapped",trap.owner,animal.id,DevMap.jurisdiction(trap.x,trap.z),0,"physical snare contact");break;
            }
        }
    }
    /** Use only the hunt's communicated or directly perceived clue; walk to the placement and spend a real snare. */
    public boolean hunt(Npc hunter) {
        Hunt clue=hunter.hunt;
        if(!hunter.posse || clue==null || !hunter.has(Items.SNARE,1) || g.time-clue.clueAt>120 || hunter.fightTarget!=0)return false;
        for(var object:g.objects.all())if(object instanceof Trap trap && trap.owner==hunter.id && trap.quarry!=0 && trap.caught==0)return false;
        float speed=(float)Math.hypot(clue.vx,clue.vz),lead=speed>0.2f?Math.min(8,speed*3):0;
        float x=(float)Math.floor(clue.x+(speed>0?clue.vx/speed*lead:0))+0.5f;
        float z=(float)Math.floor(clue.z+(speed>0?clue.vz/speed*lead:0))+0.5f;
        if(!g.vox.solid((int)x,DevMap.FLOOR-1,(int)z)||g.vox.solid((int)x,DevMap.FLOOR,(int)z)||g.vox.solid((int)x,DevMap.FLOOR+1,(int)z))return false;
        // Do not abandon an active search on an inaccessible trap site.
        if(hunter.dutyTarget!=clue.target){hunter.dutyTarget=clue.target;hunter.dutyUntil=g.time+12;}
        if(g.time>hunter.dutyUntil)return false;
        hunter.lastOutcome="approaching an ambush site from the last confirmed heading";
        if(!g.ai.brain.goTo(hunter,x,z,false,0.9f))return true;
        Trap trap=new Trap(g.state.newEntityId(),hunter.id,x,DevMap.FLOOR,z);trap.quarry=clue.target;
        for(var item:hunter.takeItems(Items.SNARE,1))trap.contents.add(item);
        g.objects.add(trap);hunter.dutyUntil=0;
        g.state.commit("BountySnareSet",hunter.id,clue.target,DevMap.jurisdiction(x,z),0,"at "+x+", "+z+" from clue by "+clue.source);
        return true;
    }

    public void work(Npc hunter) {
        Trap mine=null;
        for(var object:g.objects.all())if(object instanceof Trap trap && trap.owner==hunter.id && trap.contents.has(Items.SNARE,1)){mine=trap;break;}
        if(mine!=null) {
            if(!g.ai.brain.goTo(hunter,mine.x,mine.z,false,1.1f))return;
            Animal animal=g.animals.byId(mine.caught);
            if(animal!=null && animal.alive) {
                hunter.wantYaw=(float)Math.atan2(-(animal.x-hunter.x),-(animal.z-hunter.z));
                hunter.pitch=(float)Math.atan2(animal.chestY()-hunter.chestY(),Math.max(0.2f,hunter.distTo(animal)));
                if(!hunter.cs.busy())g.combat.attack(hunter,false,rpg.actor.Actor.Combat.Move.PUNCH);
            } else if(animal!=null && !animal.butchered)g.interact.request(hunter,animal,"butcher");
            else if(g.state.minutes-mine.checkedAt>45) {
                for(var item:mine.contents.stacks())if(!hunter.store(item))g.objects.drop(item,hunter.x,hunter.y,hunter.z);
                mine.contents.clear();mine.removed=true;
            }
            hunter.lastOutcome="checking the actual snare";return;
        }
        if(!hunter.has(Items.SNARE,1)){g.ai.desire(hunter,AiSystem.A_HOLD);hunter.lastOutcome="needs a snare";return;}
        Animal prey=null;float distance=25;
        for(Animal animal:g.animals.all())if(animal.alive && animal.loaded && animal.owner==0 && animal.species==Species.RABBIT && g.ai.canSee(hunter,animal) && hunter.distTo(animal)<distance){prey=animal;distance=hunter.distTo(animal);}
        if(prey==null){g.ai.desire(hunter,AiSystem.A_HOLD);return;}
        if(hunter.dutyUntil<g.time) {hunter.dutyX=(float)Math.floor(prey.x+prey.vx)+0.5f;hunter.dutyZ=(float)Math.floor(prey.z+prey.vz)+0.5f;hunter.dutyUntil=g.time+8;}
        int x=(int)hunter.dutyX,z=(int)hunter.dutyZ;
        if(!g.vox.solid(x,DevMap.FLOOR-1,z)||g.vox.solid(x,DevMap.FLOOR,z)||g.vox.block(x,DevMap.FLOOR,z)==Blocks.WATER){hunter.dutyUntil=0;return;}
        if(g.ai.brain.goTo(hunter,hunter.dutyX,hunter.dutyZ,false,0.9f)) {
            Trap trap=new Trap(g.state.newEntityId(),hunter.id,hunter.dutyX,DevMap.FLOOR,hunter.dutyZ);
            for(var item:hunter.takeItems(Items.SNARE,1))trap.contents.add(item);
            trap.checkedAt=g.state.minutes;g.objects.add(trap);hunter.dutyUntil=0;
        }
    }
}
