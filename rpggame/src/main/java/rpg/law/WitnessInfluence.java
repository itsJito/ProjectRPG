package rpg.law;

import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.*;
import rpg.interact.Result;
import rpg.item.*;

/** One personal appeal per witness and incident; other eyewitnesses remain independent. */
public final class WitnessInfluence {
    private WitnessInfluence() {}
    public static Memory pending(Rpg g,Npc witness) {
        if(!witness.alive || !witness.loaded || witness.asleep() || witness.reacting || witness.fightTarget!=0
                || witness.goal!=Brain.Goal.REPORT_CRIME && witness.goal!=Brain.Goal.HIDE)return null;
        for(Memory memory:witness.memories)if(memory.subject==g.player.id && memory.crime() && memory.witnessed() && (memory.witness==0 || memory.witness==witness.id) && memory.relays==0
                && !memory.reported && !memory.settled && !memory.withheld && !memory.influenceTried)return memory;
        return null;
    }
    public static int price(Rpg g,Memory memory){return Math.max(10,Math.min(250,g.law.charge(memory)/2));}
    public static Result attempt(Rpg g,Npc witness,Actor actor,String method,long request) {
        if(actor!=g.player || g.state.alreadyCommitted(request))return Result.no("That appeal has already been heard.");
        Memory memory=pending(g,witness);
        if(memory==null)return Result.no("There is no new eyewitness report to discuss.");
        if(actor.distTo(witness)>g.cfg.interactRange || !g.vox.sight(actor.x,actor.eyeY(),actor.z,witness.x,witness.eyeY(),witness.z))return Result.no("You must speak to the witness in person.");
        boolean bribe=method.equals("witness_bribe"),threat=method.equals("witness_threaten");
        int price=price(g,memory);
        if(bribe && (!actor.has(Items.COINS,price) || !witness.canStore(new ItemStack(Items.COINS,price,witness.id,false,actor.id))))return Result.no("You cannot make that offer here.");
        boolean principled=witness.watch() || witness.role==Npc.Role.KNIGHT || witness.role==Npc.Role.PRIEST || witness.standing!=Npc.Standing.COMMON;
        float gravity=Math.min(0.55f,g.law.charge(memory)/800f),opinion=g.ai.rumors.opinion(witness);
        float chance=bribe?(principled?0:0.42f+(witness.role==Npc.Role.THIEF || witness.role==Npc.Role.FENCE || witness.role==Npc.Role.BEGGAR?0.35f:0)-gravity)
                :threat?0.3f+(actor.skills.combatLevel()-witness.skills.combatLevel())*0.012f-(witness.combatant()?0.35f:0)-gravity
                :0.2f+opinion*0.35f+Math.max(0,g.honor.score)*0.003f-gravity-(principled?0.22f:0);
        chance=Math.clamp(chance,0,0.85f);
        memory.influenceTried=true;
        boolean accepted=g.roll(witness.id,memory.eventId^request^method.hashCode())<chance;
        String answer=accepted?(bribe?"Keep your voice down. I won't tell.":threat?"All right. Leave me alone.":"I'll leave this one to your conscience."):(bribe?"You cannot buy my silence. The watch will hear about this offer.":threat?"Threats won't silence me. I'm telling the watch.":"I know what I saw. You must answer for it.");
        if(accepted) {
            memory.withheld=true;
            if(bribe)for(ItemStack coin:actor.takeItems(Items.COINS,price)){coin.owner=witness.id;coin.stolen=false;witness.store(coin);}
            witness.goal=threat?Brain.Goal.HIDE:Brain.Goal.IDLE;witness.nextThink=0;witness.hasGoalPos=false;witness.legs=null;
            if(threat){witness.fearOf=actor.id;witness.fearSeenAt=g.time;witness.fearX=actor.x;witness.fearZ=actor.z;}
            g.ai.reply(witness,answer);
        } else {
            g.ai.reply(witness,answer);
            if(bribe || threat) {
                String kind=bribe?"BRIBERY":"DISORDERLY";
                var event=g.state.commit("CrimeCommitted",actor.id,witness.id,memory.jurisdiction,request,kind+" during an attempt to stop a witness");
                var added=new Memory(kind,actor.id,witness.id,memory.jurisdiction,g.state.minutes,1,"witnessed",event.id(),0,bribe?"offered a bribe to stop a report":"threatened a witness");
                added.witness=witness.id;witness.remember(added,24);g.law.crimes++;g.law.witnessed++;
                g.state.commit("CrimeWitnessed",witness.id,actor.id,memory.jurisdiction,event.id(),kind);
                g.honor.event("WITNESS_INTERFERENCE",witness.id,-2,"attempt:"+request,false,"attempted to silence a witness");
            }
            witness.nextThink=0;
        }
        g.state.commit(request,"WitnessAppeal",actor.id,witness.id,memory.jurisdiction,memory.eventId,method+" "+(accepted?"withheld":"refused"));
        return Result.ok(answer);
    }
}
