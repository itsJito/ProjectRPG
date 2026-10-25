package rpg.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.item.ItemDef;

/** Short-lived communicated group orders. Membership requires local perception, shared duty/faction and a common foe. */
final class Squads {
    enum Doctrine { WATCH, RAID, ESCORT }
    enum Duty { ANCHOR, LEFT_FLANK, RIGHT_FLANK, SUPPORT }
    record Order(Duty duty,float x,float z,String call,double until) {}
    private record Key(Doctrine doctrine,String jurisdiction,long principal,long foe) {}
    private record Orders(Map<Long,Order> members,long leader,double until) {}
    private final Map<Key,Orders> orders=new HashMap<>();
    private final Rpg g;
    Squads(Rpg g) { this.g=g; }
    static boolean allied(Npc a,Npc b) {
        Doctrine doctrine=doctrine(a);
        return doctrine!=null && doctrine==doctrine(b) && a.team==b.team
                && (doctrine==Doctrine.RAID ? Math.hypot(a.spawnX-b.spawnX,a.spawnZ-b.spawnZ)<20 : a.homeJurisdiction.equals(b.homeJurisdiction))
                && (doctrine!=Doctrine.ESCORT || a.dutyTarget==b.dutyTarget);
    }
    private static Doctrine doctrine(Npc n) {
        return switch(n.role){case GUARD,LAWMAN->Doctrine.WATCH;case BANDIT,BRUTE->Doctrine.RAID;case KNIGHT->Doctrine.ESCORT;default->null;};
    }
    Order order(Npc caller,Actor foe) {
        Doctrine doctrine=doctrine(caller);if(doctrine==null)return null;
        Key key=new Key(doctrine,caller.homeJurisdiction,doctrine==Doctrine.ESCORT?caller.dutyTarget:0,foe.id);
        orders.entrySet().removeIf(entry->g.time>entry.getValue().until);
        Orders current=orders.get(key);
        if(current!=null) {
            Actor leader=g.actor(current.leader);
            if(leader!=null && leader.alive && caller.distTo(leader)<14 && g.vox.sight(caller.x,caller.eyeY(),caller.z,leader.x,leader.eyeY(),leader.z))return current.members.get(caller.id);
        }
        if(!g.ai.canSee(caller,foe))return null;
        List<Npc> members=new ArrayList<>();members.add(caller);
        for(Npc other:g.npcs)if(other!=caller && other.alive && other.loaded && other.combatant() && other.fightTarget==foe.id && allied(caller,other)
                && caller.distTo(other)<12 && g.ai.canSee(caller,other))members.add(other);
        if(members.size()<2)return null;
        members.sort(Comparator.comparingLong(n->n.id));
        Npc anchor=members.stream().filter(n->!ranged(n)).max(Comparator.comparingDouble(n->n.eq.armor()+n.hp*0.1f)).orElse(members.getFirst());
        float dx=anchor.x-foe.x,dz=anchor.z-foe.z,len=Math.max(0.1f,(float)Math.hypot(dx,dz));dx/=len;dz/=len;
        Map<Long,Order> assigned=new HashMap<>();int flank=0,support=0;
        for(Npc member:members) {
            Duty duty;float x,z;
            if(member==anchor) { duty=Duty.ANCHOR;float stand=doctrine==Doctrine.RAID?2.1f:3.4f;x=foe.x+dx*stand;z=foe.z+dz*stand; }
            else if(ranged(member) || member.hp<member.maxHp()*0.35f) { duty=Duty.SUPPORT;float side=(support++%2==0?1:-1)*2;x=anchor.x+dx*4-dz*side;z=anchor.z+dz*4+dx*side; }
            else { int side=flank++%2==0?1:-1;duty=side>0?Duty.LEFT_FLANK:Duty.RIGHT_FLANK;float width=doctrine==Doctrine.RAID?5.5f:4;x=foe.x+dx*2-dz*width*side;z=foe.z+dz*2+dx*width*side; }
            String call=switch(doctrine){case WATCH->"Hold the line. One around each side!";case RAID->"Press them! Cut off the escape!";case ESCORT->"Keep the principal covered. Take the sides!";};
            assigned.put(member.id,new Order(duty,x,z,call,g.time+3));
        }
        long leader=members.getFirst().id;
        orders.put(key,new Orders(assigned,leader,g.time+3));g.ai.say(members.getFirst(),assigned.get(leader).call);
        return assigned.get(caller.id);
    }
    static boolean ranged(Actor actor) { return actor.main()!=null && (actor.main().family==ItemDef.Family.BOW || actor.main().family==ItemDef.Family.CROSSBOW); }
}
