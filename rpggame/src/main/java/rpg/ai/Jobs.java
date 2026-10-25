package rpg.ai;

import astratto.ai.Behavior;
import astratto.ai.TaskNetwork;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.animal.Animal;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.item.Recipes;
import rpg.objects.Node;
import rpg.objects.Station;
import rpg.world.DevMap;
import static astratto.ai.Behavior.Status.*;

/** Occupational opportunity queries and small committed task networks. Needs and threats remain the utility brain's job. */
final class Jobs {
    private final Rpg g;
    private final Brain brain;
    Jobs(Rpg g, Brain brain) { this.g=g; this.brain=brain; }
    void tick(Npc n, float dt) {
        if(g.interact.of(n)!=null || n.cs.busy()) { g.ai.desire(n,AiSystem.A_HOLD); return; }
        if(n.profession==Profession.LAKE_NET_FISHERMAN){net(n);return;}
        if(n.profession.family==Npc.Role.KNIGHT) { knight(n); return; }
        if(n.profession.family==Npc.Role.HUNTER) { hunter(n,dt); return; }
        if(n.job==null)n.job=network(n.profession);
        if(n.job.tick(n)==FAILURE) { g.ai.desire(n,AiSystem.A_HOLD); n.lastOutcome="No eligible work for "+n.profession.title+"; waiting for supplies or a suitable site"; }
        else n.lastOutcome=n.profession.title+": "+n.job.active();
    }
    private void net(Npc n) {
        ItemDef haul=batch(n);
        if(haul!=null) {if(n.job==null)n.job=new TaskNetwork<>(List.of(new TaskNetwork.Method<Npc>("sell the lake catch",person->person.count(haul)>0,person->sell(person,haul))));n.job.tick(n);return;}

        for(var object:g.objects.all())if(object instanceof rpg.objects.FishingNet net && net.owner==n.id && !net.removed) {
            if(!(g.objects.get(net.source) instanceof Node site))return;
            if(net.contents.count(site.product)==0){brain.goTo(n,n.postX,n.postZ,false,1);n.lastOutcome="net deployed; waiting for a real catch";return;}
            if(brain.goTo(n,net.x,net.z,false,1.7f)) {
                for(int i=0;i<net.contents.size();i++){ItemStack item=net.contents.get(i);if(item==null || item.def!=site.product || !n.canStore(item))continue;n.store(item);net.contents.set(i,null);}
                n.lootStarted=g.time;n.lootUntil=g.time+0.9;n.reachX=net.x;n.reachY=net.y;n.reachZ=net.z;
            }
            return;
        }
        Node site=site(n);
        if(site==null){g.ai.desire(n,AiSystem.A_HOLD);n.lastOutcome="no eligible lake and net";return;}
        float[] bank=g.ai.standSpot(n,site);
        if(brain.goTo(n,bank[0],bank[1],false,0.8f)) {
            List<ItemStack> tools=n.has(Items.FISHING_NET,1)?n.takeItems(Items.FISHING_NET,1):List.of();
            if(tools.isEmpty() && n.main()==Items.FISHING_NET){tools=List.of(n.eq.get(ItemDef.Slot.MAIN));n.eq.set(ItemDef.Slot.MAIN,null);}
            if(tools.isEmpty())return;
            var net=new rpg.objects.FishingNet(g.state.newEntityId(),n.id,site.id,site.x,DevMap.FLOOR-0.1f,site.z,g.state.minutes+4);
            for(var tool:tools)net.contents.add(tool);g.objects.add(net);n.dutyTarget=net.id;
        }
    }

    private TaskNetwork<Npc> network(Profession occupation) {
        return new TaskNetwork<>(List.of(
            new TaskNetwork.Method<>("deliver a real batch", n->n.profession==occupation && batch(n)!=null, n->sell(n,batch(n))),
            new TaskNetwork.Method<>("make the selected order", n->n.profession==occupation && recipe(n)!=null, n->craft(n,recipe(n))),
            new TaskNetwork.Method<>("gather at a suitable site", n->n.profession==occupation && site(n)!=null, n->gather(n,site(n))),
            new TaskNetwork.Method<>("buy missing inputs", n->n.profession==occupation && purchase(n)!=null, n->buy(n,purchase(n)))
        ));
    }
    private Behavior.Node<Npc> approach(float x,float z,float range) { return n->brain.goTo(n,x,z,false,range)?SUCCESS:RUNNING; }
    private Behavior.Node<Npc> gather(Npc n,Node node) {
        ItemDef tool=node.needs;
        if(node.kind==Node.Kind.ROCK) tool=bestTool(n,rpg.skill.Skill.MINING);
        if(tool!=null && n.main()!=tool)g.items.equipFirst(n,tool,ItemDef.Slot.MAIN,g.state.newRequestId());
        float[] spot=g.ai.standSpot(n,node);
        return Behavior.sequence(List.of(
            Behavior.condition(person->!node.removed && !node.depleted()),
            approach(spot[0],spot[1],0.7f),
            person->{if(!safe(node,person) || node.refusal(person)!=null)return FAILURE;
                person.wantYaw=(float)Math.atan2(-(node.x-person.x),-(node.z-person.z));
                return g.interact.request(person,node,"gather").rejected()?FAILURE:SUCCESS;}
        ));
    }
    private ItemDef bestTool(Npc n,rpg.skill.Skill skill) {
        ItemDef best=null;
        for(var grid:n.grids()) for(ItemStack item:grid.stacks()) if(item.def.toolSkill==skill && (best==null || item.def.toolPower>best.toolPower) && rpg.item.Equipment.refusal(item.def,n.skills)==null)best=item.def;
        if(n.main()!=null && n.main().toolSkill==skill && (best==null || n.main().toolPower>=best.toolPower))best=n.main();
        return best;
    }
    private ItemDef batch(Npc n) {
        for(ItemStack item:n.inv.stacks()) if(n.count(item.def)>=3 && !item.stolen && product(n.profession,item.def))return item.def;
        return null;
    }
    private boolean product(Profession p,ItemDef item) {
        return switch(p.family) {
            case MINER -> mineral(p,item);
            case FISHERMAN -> item.id.startsWith("raw_") && !item.id.equals("raw_meat");
            case CRAFTSMAN -> recipeOutput(p,item);
            default -> false;
        };
    }
    private Behavior.Node<Npc> sell(Npc n,ItemDef product) {
        float[] counter=g.econ.counterSpot(g.econ.shopA);
        return Behavior.sequence(List.of(approach(counter[0],counter[1],1.2f), person->g.econ.sellToShop(person,g.econ.shopA,product,person.count(product))>0?SUCCESS:FAILURE));
    }
    private boolean mineral(Profession p,ItemDef item) {
        return switch(p) {
            case ORE_MINER -> item.id.endsWith("_ore");
            case QUARRY_WORKER -> item.id.equals("stone_block") || item.id.equals("marble_block");
            case GEM_PROSPECTOR -> item.id.startsWith("uncut_");
            case CLAY_DIGGER -> item.id.equals("clay");
            case COAL_MINER -> item==Items.COAL;
            default -> false;
        };
    }
    private boolean safe(Node node,Npc n) {
        if(node.owner!=0 && node.owner!=n.id || node.depleted())return false;
        if((g.chemistry.state(node.id)&astratto.bridge.AstrattoNative.CHEM_BURNING)!=0)return false;
        if(n.profession==Profession.COAL_MINER) {
            boolean ventilation=g.vox.skyLight(node.bx,(int)node.y+2,node.bz)>0.2f;
            if(!ventilation || g.vox.block(node.bx,(int)node.y,node.bz)==rpg.world.Blocks.CHARRED)return false;
        }
        if(n.profession==Profession.CLAY_DIGGER && g.vox.block(node.bx,(int)node.y,node.bz)==rpg.world.Blocks.WATER)return false;
        return true;
    }
    private Node site(Npc n) {
        Node best=null;float score=Float.MAX_VALUE;
        for(Node node:g.objects.nodes) {
            if(!safe(node,n))continue;
            boolean eligible=n.profession.family==Npc.Role.MINER && node.kind==Node.Kind.ROCK && mineral(n.profession,node.product)
                    || n.profession.family==Npc.Role.FISHERMAN && fishing(n,node)
                    || n.profession.family==Npc.Role.CRAFTSMAN && missingIngredient(n,node.product);
            if(!eligible || n.skills.level(node.skill)<node.level)continue;
            float distance=n.distTo(node.x,node.z);
            if(n.profession==Profession.GEM_PROSPECTOR)distance-=Math.min(4,node.stock)*0.4f; // survey promising, undepleted deposits
            if(distance<score){score=distance;best=node;}
        }
        return best;
    }
    private boolean fishing(Npc n,Node node) {
        if(node.kind!=Node.Kind.FISH || node.needs==null || !n.has(node.needs,1) && n.main()!=node.needs)return false;
        float tide=(float)Math.sin(g.state.minutes/180*Math.PI);
        boolean severe=g.timeSystem.weather.rain>0.75f;
        return switch(n.profession) {
            case RIVER_FISHERMAN -> node.waters==Node.Waters.RIVER;
            case COASTAL_FISHERMAN -> node.waters==Node.Waters.COAST && tide>-0.2f && !severe;
            case DEEP_WATER_FISHERMAN -> node.waters==Node.Waters.OFFSHORE && !severe && n.has(Items.BREAD,2);
            case SHELLFISH_GATHERER -> node.waters==Node.Waters.SHORE && tide<0.2f && node.product==Items.RAW_CRAB;
            case LAKE_NET_FISHERMAN -> node.waters==Node.Waters.LAKE && node.needs==Items.FISHING_NET;
            default -> false;
        };
    }
    private boolean recipeOutput(Profession p,ItemDef item) {
        return switch(p) {
            case BLACKSMITH -> item.tier!=null && item.family!=ItemDef.Family.ARMOR && item.family!=ItemDef.Family.SHIELD && (item.damageType!=ItemDef.DamageType.NONE || item.toolSkill!=null);
            case ARMORER -> item.family==ItemDef.Family.ARMOR || item.family==ItemDef.Family.SHIELD;
            case CARPENTER -> item==Items.PLANK || item==Items.OAK_PLANK;
            case COOK -> item.heal>0;
            case TAILOR -> item.id.startsWith("cloth_");
            default -> false;
        };
    }
    private boolean accepts(Npc n,Recipes.Recipe recipe) {
        return n.profession.family==Npc.Role.CRAFTSMAN && (recipe.furniture!=null ? n.profession==Profession.CARPENTER : recipe.outputs.stream().anyMatch(out->recipeOutput(n.profession,out.item())))
                && n.skills.level(recipe.skill)>=recipe.level;
    }
    private Station station(Recipes.Recipe recipe) {
        for(var object:g.objects.all())if(object instanceof Station station && station.kind==recipe.station)return station;
        return null;
    }
    private Recipes.Recipe recipe(Npc n) {
        Recipes.Recipe selected=null;
        for(var recipe:Recipes.ALL)if(accepts(n,recipe)) { Station station=station(recipe);if(station!=null && station.refusal(recipe,n)==null)selected=recipe; }
        return selected;
    }
    private boolean missingIngredient(Npc n,ItemDef item) {
        for(var recipe:Recipes.ALL)if(accepts(n,recipe)) for(var input:recipe.inputs)if(input.item()==item && n.count(item)<input.amount())return true;
        return false;
    }
    private ItemDef purchase(Npc n) {
        if(n.profession.family!=Npc.Role.CRAFTSMAN)return null;
        for(var recipe:Recipes.ALL) if(accepts(n,recipe)) for(var input:recipe.inputs)
            if(n.count(input.item())<input.amount() && g.econ.shopA.stock.count(input.item())>0 && n.has(Items.COINS,g.econ.buyPrice(g.econ.shopA,input.item())))return input.item();
        return null;
    }
    private Behavior.Node<Npc> buy(Npc n,ItemDef item) {
        float[] counter=g.econ.counterSpot(g.econ.shopA);
        return Behavior.sequence(List.of(approach(counter[0],counter[1],1.2f),person->g.econ.buy(person,g.econ.shopA,item,1,g.state.newRequestId()).rejected()?FAILURE:SUCCESS));
    }
    private Behavior.Node<Npc> craft(Npc n,Recipes.Recipe recipe) {
        Station station=station(recipe);
        return Behavior.sequence(List.of(Behavior.condition(person->station!=null && !station.removed),approach(station.x,station.z,1.6f),person->g.interact.request(person,station,"make:"+recipe.id).rejected()?FAILURE:SUCCESS));
    }
    private void knight(Npc n) {
        Actor principal=g.actor(n.dutyTarget);
        if(n.profession==Profession.ESCORT_KNIGHT || n.profession==Profession.HOUSEHOLD_KNIGHT || n.profession==Profession.ORDER_KNIGHT) {
            if(principal==null || !principal.alive){g.ai.desire(n,AiSystem.A_HOLD);n.lastOutcome="awaiting a living principal or commander";return;}
            Actor assailant=g.actor(principal.cs.lastAttacker);
            if(assailant!=null && assailant.alive && g.time-principal.cs.lastHurt<12 && g.ai.canSee(n,principal) && g.ai.canSee(n,assailant) && !AiSystem.alliedWatch(n,assailant)) { n.fightTarget=assailant.id; n.nextThink=0;return; }
            if(n.profession==Profession.ORDER_KNIGHT) {
                if(n.dutyPhase==0) {
                    if(brain.goTo(n,n.dutyX,n.dutyZ,false,1.1f))n.dutyPhase=1;
                    n.lastOutcome="inspecting the commander's assigned site";
                } else if(n.dutyPhase==1) {
                    if(brain.goTo(n,principal.x,principal.z,false,2)) {
                        n.dutyPhase=2;g.ai.say(n,"The assigned site has been inspected.");
                        g.state.commit("KnightDutyReported",n.id,principal.id,n.homeJurisdiction,0,"inspected "+n.dutyX+","+n.dutyZ);
                    }
                    n.lastOutcome="returning the inspection report to "+principal.name;
                } else {brain.goTo(n,principal.x,principal.z,false,3);n.lastOutcome="reported; awaiting a new institutional assignment";}
                return;
            }
            if(n.profession==Profession.HOUSEHOLD_KNIGHT && principal instanceof Npc patron) {
                for(Npc resident:g.npcs)if(resident!=n && resident.alive && Math.hypot(resident.homeX-patron.homeX,resident.homeZ-patron.homeZ)<6 && g.ai.canSee(n,resident)) {
                    Actor attacker=g.actor(resident.cs.lastAttacker);
                    if(attacker!=null && attacker.alive && g.time-resident.cs.lastHurt<12 && g.ai.canSee(n,attacker) && !AiSystem.alliedWatch(n,attacker)) {n.fightTarget=attacker.id;n.nextThink=0;return;}
                }
            }
            float x=n.profession==Profession.ESCORT_KNIGHT?principal.x+1.8f:n.dutyX;
            float z=n.profession==Profession.ESCORT_KNIGHT?principal.z+1.8f:n.dutyZ;
            brain.goTo(n,x,z,false,1.3f);n.lastOutcome="protecting "+principal.name+" under "+n.profession.title+" duties";return;
        }
        if(n.profession==Profession.KNIGHT_ERRANT) {
            for(Npc person:g.npcs)if(person!=n && person.alive && person.loaded && person.hp<person.maxHp()*0.5f && g.ai.canSee(n,person)) {
                n.dutyTarget=person.id;n.aidTarget=person.id;n.goal=Brain.Goal.AID;n.nextThink=g.time+2;n.lastOutcome="accepted a visible request for aid";return;
            }
        }
        float side=(n.patrolIndex&1)==0?1:-1;
        if(brain.goTo(n,n.dutyX+side*5,n.dutyZ,false,1)) { n.patrolIndex++;n.lastOutcome="inspected the assigned border"; }
    }
    private void hunter(Npc n,float dt) {
        if(n.profession==Profession.GAME_HUNTER){brain.huntSmallGame(n,dt);return;}
        if(n.profession==Profession.TRAPPER){g.traps.work(n);return;}
        Actor target=null;float nearest=30;
        for(Actor actor:g.actors()) {
            if(actor==n || !actor.alive || !g.ai.canSee(n,actor))continue;
            boolean eligible=switch(n.profession) {
                case MONSTER_HUNTER -> actor.domain==astratto.ai.AgentDomain.MONSTER;
                case BOUNTY_HUNTER -> (actor==g.player || actor instanceof Npc) && g.law.bounty(actor.id,n.homeJurisdiction)>0 && n.crimeKnown(actor.id)!=null;
                case PREDATOR_HUNTER -> actor instanceof Animal animal && !animal.baby && animal.species.diet==rpg.animal.Species.Diet.HUNTER && threatensPeople(animal,n);
                default -> false;
            };
            if(eligible && n.distTo(actor)<nearest){nearest=n.distTo(actor);target=actor;}
        }
        if(target!=null) {
            if(n.profession==Profession.BOUNTY_HUNTER && target.hp<target.maxHp()*0.3f && n.distTo(target)<2)g.law.arrest(target,n.homeJurisdiction,n);
            else {n.fightTarget=target.id;n.nextThink=0;}
            return;
        }
        brain.goTo(n,n.postX,n.postZ,false,1);n.lastOutcome="no observed eligible target for "+n.profession.title;
    }
    private boolean threatensPeople(Animal predator,Npc observer) {
        for(Actor actor:g.actors())if(!(actor instanceof Animal) && actor.alive && actor.team==observer.team && actor.distTo(predator)<12 && (predator.cs.lastAttacker==actor.id || predator.other==actor.id && (predator.goal==Animal.Goal.HUNT || predator.goal==Animal.Goal.DEFEND)))return true;
        return false;
    }
}
