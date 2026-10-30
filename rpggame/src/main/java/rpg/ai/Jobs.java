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
        if(unload(n))return;
        if(n.profession==Profession.LAKE_NET_FISHERMAN){net(n);return;}
        if(n.profession.family==Npc.Role.KNIGHT) { knight(n); return; }
        if(n.profession.family==Npc.Role.HUNTER) { hunter(n,dt); return; }
        // a carpenter's first work is what has been broken (Repairs): it comes before the day's orders
        if(n.profession==Profession.CARPENTER && brain.mend(n)) return;
        if(n.job==null)n.job=network(n.profession);
        if(n.job.tick(n)==FAILURE) {
            var own=g.econ.shopOf(n);
            if(own!=null && g.econ.rack(own) && !own.stock.isEmpty()) {
                brain.goTo(n,own.counterX,own.counterZ,false,1.2f);
                n.lastOutcome=n.profession.title+": minding the rack ("+own.stock.stacks().size()+" pieces for sale)";
                return;
            }
            g.ai.desire(n,AiSystem.A_HOLD); n.lastOutcome="No eligible work for "+n.profession.title+"; waiting for supplies or a suitable site";
        }
        else n.lastOutcome=n.profession.title+": "+n.job.active();
    }
    private void net(Npc n) {
        ItemDef haul=batch(n);
        if(haul!=null) {if(n.job==null)n.job=new TaskNetwork<>(List.of(new TaskNetwork.Method<Npc>("sell the lake catch",person->person.count(haul)>0,person->sell(person,haul))));n.job.tick(n);return;}

        for(var object:g.objects.all())if(object instanceof rpg.objects.FishingNet net && net.owner==n.id && !net.removed) {
            if(!(g.objects.get(net.source) instanceof Node site))return;
            if(net.contents.count(site.product)==0){brain.goTo(n,n.postX,n.postZ,false,1);n.lastOutcome="net deployed; waiting for a real catch";return;}
            // the net is out on the water: hauled in from the bank, within arm's and rope's reach
            float[] bank=g.ai.standSpot(n,site);
            if(brain.goTo(n,bank[0],bank[1],false,0.8f) && Math.hypot(n.x-net.x,n.z-net.z)<3.5f) {
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

    /**
     * (0.2.2, gamefixes: the fisherman stopped working with a full pack) a worker whose pack is full - or as good as - goes
     * to empty it before anything else: it sells what a store will buy off it at the store's counter, and what no store
     * will take it carries to the bank and lays in its vault (opening an account the first time). It works again once
     * there is room. Tools of its trade, its coin and what it wears never leave it.
     */
    private boolean unload(Npc n) {
        int free=n.inv.freeSlots();
        if(!n.unloading && free>1)return false;
        ItemDef goods=null; int most=0;
        for(ItemStack item:n.inv.stacks())if(cargo(item) && n.count(item.def)>most){most=n.count(item.def);goods=item.def;}
        if(goods==null || n.unloading && free>=6){n.unloading=false;return false;}
        if(!n.unloading)g.state.commit("PackFull",n.id,0,DevMap.jurisdiction(n.x,n.z),0,n.name+" ("+free+" free) goes to unload "+goods.name);
        n.unloading=true;
        // first a store that will buy it
        for(var shop:new rpg.econ.Economy.Shop[]{g.econ.shopA,g.econ.shopB})if(shop!=null && g.econ.buys(shop,goods)) {
            if(brain.goTo(n,shop.counterX,shop.counterZ,false,1.4f)) {
                int sold=g.econ.sellToShop(n,shop,goods,n.count(goods));
                n.lastOutcome=n.profession.title+": pack full, sold "+sold+" "+goods.name.toLowerCase()+" at "+shop.name;
            } else n.lastOutcome=n.profession.title+": pack full, taking "+goods.name.toLowerCase()+" to sell at "+shop.name;
            return true;
        }
        // nobody buys it: the bank
        float[] at=g.bank.customerSpot();
        if(!brain.goTo(n,at[0],at[1],false,1.2f)){n.lastOutcome=n.profession.title+": pack full and no buyer; taking "+goods.name.toLowerCase()+" to the bank";return true;}
        if(!g.bank.open()){g.ai.desire(n,AiSystem.A_HOLD);n.lastOutcome=n.profession.title+": waiting at the bank counter to deposit "+goods.name.toLowerCase();return true;}
        int banked=g.bank.depositGoods(n,goods);
        if(banked<=0){n.unloading=false;n.lastOutcome=n.profession.title+": the bank could not take the "+goods.name.toLowerCase();return false;}
        n.lastOutcome=n.profession.title+": banked "+banked+" "+goods.name.toLowerCase();
        return true;
    }
    /** What a worker can part with to make room: neither coin, nor a tool, nor anything it could wear or wield. */
    private static boolean cargo(ItemStack item) {
        return item.def!=Items.COINS && item.def.toolSkill==null && !item.def.equippable() && item.def!=Items.FISHING_NET;
    }

    private TaskNetwork<Npc> network(Profession occupation) {
        return new TaskNetwork<>(List.of(
            new TaskNetwork.Method<>("deliver a real batch", n->n.profession==occupation && batch(n)!=null, n->sell(n,batch(n))),
            new TaskNetwork.Method<>("set wares out on the rack", n->n.profession==occupation && shelve(n)!=null, n->rack(n,shelve(n))),
            new TaskNetwork.Method<>("make the selected order", n->n.profession==occupation && recipe(n)!=null, n->craft(n,recipe(n))),
            new TaskNetwork.Method<>("gather at a suitable site", n->n.profession==occupation && site(n)!=null, n->gather(n,site(n))),
            new TaskNetwork.Method<>("buy missing inputs", n->n.profession==occupation && purchase(n)!=null, n->buy(n,purchase(n))),
            new TaskNetwork.Method<>("fetch supplies from the producer", n->n.profession==occupation && supplier(n)!=null, n->fetch(n,supplier(n)))
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
        int minimum=n.profession.family==Npc.Role.CRAFTSMAN?1:3;
        boolean joinery=n.profession==Profession.CARPENTER && joineryPending(n);
        for(ItemStack item:n.inv.stacks())if(n.count(item.def)>=minimum && !item.stolen && product(n.profession,item.def)
                && !(n.profession==Profession.TAILOR && item.def==Items.CLOTH) && !joinery && g.econ.buys(g.econ.localShop(n),item.def))return item.def;
        return null;
    }
    /** A craftsman's own finished pieces not yet sold: past a few, more of them is waste (a forge is not a warehouse). */
    static final int WARES = 4;
    private int wares(Npc n) {
        int count=0;
        for(var grid:n.grids())for(ItemStack item:grid.stacks())if(!item.stolen && n.profession.family==Npc.Role.CRAFTSMAN && recipeOutput(n.profession,item.def) && !item.def.stackable && !material(n,item.def))count+=item.amount;
        var own=g.econ.shopOf(n);
        if(own!=null && g.econ.rack(own))for(ItemStack item:own.stock.stacks())if(recipeOutput(n.profession,item.def))count+=item.amount;
        return count;
    }
    /** A finished piece the store would not take, with room on the craftsman's own rack (one of each kind out at a time). */
    private ItemDef shelve(Npc n) {
        var own=g.econ.shopOf(n);
        if(own==null || !g.econ.rack(own))return null;
        for(ItemStack item:n.inv.stacks())if(!item.stolen && recipeOutput(n.profession,item.def) && !item.def.stackable && !material(n,item.def) && own.stock.count(item.def)<2 && own.stock.canAdd(item))return item.def;
        return null;
    }
    /** Something this craftsman works up into a finer piece (a tailor's woven cloth, a carpenter's planks): stock, not wares. */
    private boolean material(Npc n,ItemDef def) {
        for(var recipe:Recipes.ALL)if(accepts(n,recipe))for(var input:recipe.inputs)if(input.item()==def)return true;
        return false;
    }
    private Behavior.Node<Npc> rack(Npc n,ItemDef piece) {
        var own=g.econ.shopOf(n);
        return Behavior.sequence(List.of(approach(own.counterX,own.counterZ,1.4f),person->{
            var taken=person.takeItems(piece,1);
            if(taken==null || taken.isEmpty())return FAILURE;
            for(ItemStack one:taken){one.owner=person.id;own.stock.add(one);own.target.putIfAbsent(piece.id,2);}
            g.state.commit("WaresShelved",person.id,0,own.jurisdiction,0,person.name+" set out a "+piece.name.toLowerCase()+" on "+own.name.toLowerCase());
            return SUCCESS;
        }));
    }
    private boolean joineryPending(Npc n) {
        for(var recipe:Recipes.ALL)if(recipe.furniture!=null && accepts(n,recipe) && station(recipe) instanceof rpg.objects.BuildSpot plot && !plot.built.getOrDefault(recipe.furniture,false))return true;
        return false;
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
        var shop=g.econ.localShop(n);float[] counter=g.econ.counterSpot(shop);
        return Behavior.sequence(List.of(approach(counter[0],counter[1],1.2f), person->g.econ.sellToShop(person,shop,product,person.count(product))>0?SUCCESS:FAILURE));
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
        if (n.unreachable.getOrDefault(node.id,-1.0) > g.time) return false;
        if(node.owner!=0 && node.owner!=n.id || node.depleted())return false;
        if((g.chemistry.state(node.id)&astratto.bridge.AstrattoNative.CHEM_BURNING)!=0)return false;
        if(n.profession==Profession.COAL_MINER) {
            boolean ventilation=g.vox.skyLight(node.bx,(int)node.y+2,node.bz)>0.2f;
            if(!ventilation || rpg.world.Blocks.charred(g.vox.block(node.bx,(int)node.y,node.bz)))return false;
        }
        if(n.profession==Profession.CLAY_DIGGER && g.vox.block(node.bx,(int)node.y,node.bz)==rpg.world.Blocks.WATER)return false;
        return true;
    }
    private Node site(Npc n) {
        Node best=null;float score=Float.MAX_VALUE;
        for(Node node:g.objects.nodes) {
            if(!safe(node,n))continue;
            boolean eligible=n.profession.family==Npc.Role.MINER && node.kind==Node.Kind.ROCK && mineral(n.profession,node.product)
                    || n.profession.family==Npc.Role.FISHERMAN && fishing(n,node);
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
        boolean metalInput=(n.profession==Profession.BLACKSMITH || n.profession==Profession.ARMORER) && recipe.station==Recipes.Station.FURNACE && recipe.outputs.stream().anyMatch(out->out.item().id.endsWith("_bar"));
        return n.profession.family==Npc.Role.CRAFTSMAN && (metalInput || (recipe.furniture!=null ? n.profession==Profession.CARPENTER : recipe.outputs.stream().anyMatch(out->recipeOutput(n.profession,out.item()))))
                && n.skills.level(recipe.skill)>=recipe.level;
    }
    /** (0.2.1) each kind's station, found once: the lookup was a walk over every object in the world for every recipe. */
    private final java.util.EnumMap<Recipes.Station,Station> stations=new java.util.EnumMap<>(Recipes.Station.class);
    private Station station(Recipes.Recipe recipe) {
        Station known=stations.get(recipe.station);
        if(known!=null && !known.removed)return known;
        for(var object:g.objects.all())if(object instanceof Station station && station.kind==recipe.station){stations.put(recipe.station,station);return station;}
        return null;
    }
    private Recipes.Recipe recipe(Npc n) {
        Recipes.Recipe selected=null;int fewest=Integer.MAX_VALUE;
        boolean stocked=wares(n)>=WARES;
        for(var recipe:Recipes.ALL)if(accepts(n,recipe)) {
            Station station=station(recipe);
            if(station==null || station.refusal(recipe,n)!=null)continue;
            ItemDef out=recipe.outputs.isEmpty()?null:recipe.outputs.get(0).item();
            boolean stockOnly=stock(recipe);
            // furniture for the build spot is made in place, never stock on a shelf: the wares cap does not hold it back
            if(stocked && !stockOnly && recipe.furniture==null)continue;
            if(stockOnly && n.count(out)>=12)continue;
            // the order: the piece she holds fewest of, the better metal first when it is a tie (the last listed); a
            // piece of furniture for the build spot (no item comes of it) before anything
            int have=out==null?-1:n.count(out);
            if(have<=fewest){fewest=have;selected=recipe;}
        }
        return selected;
    }
    private ItemDef purchase(Npc n) {
        if(n.profession.family!=Npc.Role.CRAFTSMAN)return null;
        for(var recipe:Recipes.ALL) if(accepts(n,recipe)) for(var input:recipe.inputs)
            if(n.count(input.item())<input.amount() && g.econ.shopA.stock.count(input.item())>0 && n.has(Items.COINS,g.econ.buyPrice(g.econ.shopA,input.item())))return input.item();
        return null;
    }
    /** Who makes what this craftsman lacks and has some to spare (a miner with ore), when the shop has none: the one nearest. */
    private Npc supplier(Npc n) {
        if(n.profession.family!=Npc.Role.CRAFTSMAN)return null;
        boolean stocked=wares(n)>=WARES;
        Npc best=null;float nearest=90;
        for(var recipe:Recipes.ALL) if(accepts(n,recipe) && (!stocked || stock(recipe) && n.count(recipe.outputs.get(0).item())<12)) for(var input:recipe.inputs) {
            if(n.count(input.item())>=input.amount() || g.econ.shopA.stock.count(input.item())>0 || !n.has(Items.COINS,g.econ.sellPrice(g.econ.shopA,input.item())))continue;
            for(Npc o:g.npcs)if(o!=n && o.alive && o.loaded && o.profession!=null && o.goal!=Brain.Goal.FIGHT && o.goal!=Brain.Goal.FLEE && o.count(input.item())>0 && product(o.profession,input.item()) && n.distTo(o)<nearest){nearest=n.distTo(o);best=o;}
        }
        return best;
    }
    /** A recipe that makes stock for later work (bars from ore) rather than a finished piece to sell. */
    private static boolean stock(Recipes.Recipe recipe) { return !recipe.outputs.isEmpty() && recipe.outputs.get(0).item().id.endsWith("_bar"); }
    private Behavior.Node<Npc> fetch(Npc n,Npc from) {
        return Behavior.sequence(List.of(
            person->from.alive && from.loaded?(brain.goTo(person,from.x+0.9f,from.z+0.9f,false,1.6f)?SUCCESS:RUNNING):FAILURE,
            person->{
                int got=0;
                for(var recipe:Recipes.ALL) if(accepts(person,recipe)) for(var input:recipe.inputs)
                    if(person.count(input.item())<input.amount()*4 && from.count(input.item())>0) got+=g.econ.trade(from,person,input.item(),Math.min(from.count(input.item()),input.amount()*4-person.count(input.item())));
                if(got==0)return FAILURE;
                g.ai.say(person,"I'll take what ore you have.");g.ai.reply(from,"Fair price. Here.");
                return SUCCESS;
            }
        ));
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
        if(n.profession==Profession.BORDER_KNIGHT) {
            // an incursion at the border (GDD: reacts to known incursions, calls for reinforcement under actual threat): a
            // monster, or a beast set on people, near the post and in sight is met; any watch officer within call comes too
            for(Actor actor:g.actors()) {
                if(actor==n || !actor.alive || actor.distTo(n.dutyX,n.dutyZ)>18 || !g.ai.canSee(n,actor))continue;
                if(!(actor.domain==astratto.ai.AgentDomain.MONSTER || actor instanceof Animal animal && animal.dangerous()))continue;
                n.fightTarget=actor.id;n.nextThink=0;n.lastOutcome="meeting an incursion at the border: "+actor.name;
                if(g.time-n.lastDialogAt>20){g.ai.say(n,"To arms! Something's come over the border!");n.lastDialogAt=g.time;
                    for(Npc o:g.npcs)if(o.alive && o.loaded && o.watch() && o.distTo(n)<30 && o.fightTarget==0){o.fightTarget=actor.id;o.nextThink=0;}}
                return;
            }
        }
        // the beat: a few paces either way along the border from the post, along whichever line is open there (a beat
        // laid across a wall sent him the long way round the houses on every leg, half the day away from his post)
        float side=(n.patrolIndex&1)==0?1:-1, reach=beat(n);
        boolean alongX=reach>0;
        float bx=n.dutyX+(alongX?side*reach:0), bz=n.dutyZ+(alongX?0:side*-reach);
        if(brain.goTo(n,bx,bz,false,1)) { n.patrolIndex++;n.lastOutcome="inspected the assigned border"; }
    }
    /** How far the border beat runs from the post: positive along x, negative along z, whichever is clear for longer (at most 5 m). */
    private float beat(Npc n) {
        float y=DevMap.FLOOR+1;
        float best=0;
        for(float d=5;d>=1.5f;d-=0.5f) {
            if(g.vox.clear(n.dutyX,y,n.dutyZ,n.dutyX+d,y,n.dutyZ) && g.vox.clear(n.dutyX,y,n.dutyZ,n.dutyX-d,y,n.dutyZ)) { best=d; break; }
        }
        for(float d=5;d>=1.5f;d-=0.5f) {
            if(g.vox.clear(n.dutyX,y,n.dutyZ,n.dutyX,y,n.dutyZ+d) && g.vox.clear(n.dutyX,y,n.dutyZ,n.dutyX,y,n.dutyZ-d)) { if(d>best)return -d; break; }
        }
        return best>0?best:-1.5f;
    }

    private void hunter(Npc n,float dt) {
        if(n.profession==Profession.GAME_HUNTER){brain.huntSmallGame(n,dt);return;}
        if(n.profession==Profession.TRAPPER){g.traps.work(n);return;}
        Actor target=null;float nearest=30;
        for(Actor actor:g.actors()) {
            if(actor==n || !actor.alive || !g.ai.canSee(n,actor))continue;
            boolean eligible=switch(n.profession) {
                case MONSTER_HUNTER -> actor.domain==astratto.ai.AgentDomain.MONSTER;
                // a posse rides on the kingdom's warrant; a resident hunter acts on what they know
                case BOUNTY_HUNTER -> (actor==g.player || actor instanceof Npc) && g.law.bounty(actor.id,n.homeJurisdiction)>0 && (n.posse ? actor==g.player : n.crimeKnown(actor.id)!=null);
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
