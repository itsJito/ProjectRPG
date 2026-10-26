package rpg;

import java.util.*;
import rpg.actor.Appearance;
import rpg.ai.*;
import rpg.animal.Species;
import rpg.interact.WorldObject;
import rpg.item.*;
import rpg.monster.MonsterKind;
import rpg.objects.*;
import rpg.world.*;

/** Console discovery and spawning use the live content registries, so newly added content appears automatically. */
public final class DebugContent {
    private final Rpg g;
    public DebugContent(Rpg g) { this.g=g; }
    private static String key(Object value) { return value.toString().toLowerCase(Locale.ROOT); }
    public Map<String,String> catalog() {
        Map<String,String> result=new LinkedHashMap<>();
        for(var kind:MonsterKind.values())result.put("monster:"+key(kind),kind.title);
        for(var species:Species.values())result.put("animal:"+key(species),species.label+" [baby]");
        for(var role:Npc.Role.values())if(role!=Npc.Role.MONSTER)result.put("npc:"+key(role),"fixed "+role+" profile");
        for(var profession:Profession.values())result.put("npc:"+key(profession),profession.title);
        for(var npc:g.npcs)if(npc.id<Ids.DYNAMIC)result.put("resident:"+npc.id,npc.name+" - "+npc.title);
        for(var object:g.objects.everything())if(object.id<Ids.DYNAMIC)result.put("object:"+object.id,object.def+" - "+object.name);
        for(var kind:Furniture.Kind.values())result.put("furniture:"+key(kind),kind.title);
        for(var station:Recipes.Station.values())result.put("station:"+key(station),"workstation");
        for(var spell:rpg.combat.Spells.Spell.values())result.put("spell:"+key(spell),"cast "+spell.name());
        for(var prayer:rpg.combat.Spells.Prayer.values())result.put("prayer:"+key(prayer),"toggle "+prayer.name());
        for(var layout:DungeonLayout.all())result.put("dungeon:"+layout.id,layout.name);
        return result;
    }
    /** An explicit category resolves collisions such as an item, a monster and an occupation sharing a name. */
    public String spawn(String spec, boolean baby, int level, float x,float z) {
        String[] parts=spec.toLowerCase(Locale.ROOT).split(":",2);
        String category=parts.length==2?parts[0]:"", name=parts.length==2?parts[1]:parts[0];
        String enumName=name.toUpperCase(Locale.ROOT).replace(' ','_');
        if(category.isEmpty()||category.equals("monster"))for(var kind:MonsterKind.values())if(kind.name().equals(enumName)){
            var monster=g.monsters.spawn(kind,level<=0?kind.minLevel:level,x,z,null);return monster.label()+" #"+monster.id;
        }
        if(category.isEmpty()||category.equals("animal"))for(var species:Species.values())if(species.name().equals(enumName)){
            var animal=g.animals.release(species,baby,(g.state.tick&1)==0,x,z);return animal.kind()+" #"+animal.id;
        }
        Npc.Role role=null;Profession profession=null;Npc source=null;
        if(category.equals("resident")) for(var npc:g.npcs)if(name.equals(Long.toString(npc.id))||npc.name.equalsIgnoreCase(name)){source=npc;role=npc.role;profession=npc.profession;break;}
        if(category.isEmpty()||category.equals("npc")) {
            for(var p:Profession.values())if(p.name().equals(enumName)){profession=p;role=p.family;break;}
            if(role==null)for(var r:Npc.Role.values())if(r.name().equals(enumName)&&r!=Npc.Role.MONSTER){role=r;break;}
        }
        if(role!=null){
            Npc npc=new Npc(g.state.newEntityId(),source==null?"Debug "+(profession==null?role:profession.title):source.name,role,profession==null?role.name():profession.title,x,z);
            if(profession!=null)profession.assign(npc);
            npc.debugSpawn=true;npc.look=source==null?Appearance.random(npc.id,false):source.look.copy();
            if(source!=null) {
                for(var skill:rpg.skill.Skill.ALL)npc.skills.setStat(skill,source.skills.level(skill));
                for(var slot:ItemDef.Slot.values())if(source.eq.get(slot)!=null){var item=source.eq.get(slot).copyWhole();npc.eq.set(slot,item);if(!item.def.natural)g.state.mint(item.def,item.amount);}
            }
            npc.fitBody();npc.restore();g.npcs.add(npc);g.ai.spawnBody(npc);
            g.state.commit("DebugNpcSpawned",g.player.id,npc.id,npc.homeJurisdiction,0,npc.title);
            return npc.name+" #"+npc.id;
        }
        if(category.equals("furniture")){
            var kind=Furniture.Kind.valueOf(enumName);var object=new Furniture(g.state.newEntityId(),kind,(int)Math.floor(x),(int)Math.floor(z),(int)Math.floor(x),(int)Math.floor(z),kind==Furniture.Kind.BOOKCASE?2:1,0,0,true);
            object.debugPrefab="furniture:"+name;g.objects.furniture.add(g.objects.add(object));object.place(g,true);return object.name+" #"+object.id;
        }
        if(category.equals("station")){
            var kind=Recipes.Station.valueOf(enumName);var object=g.objects.add(new Station(g.state.newEntityId(),name,kind.name(),kind,x,DevMap.FLOOR+0.6f,z));
            object.debugPrefab="station:"+name;return object.name+" #"+object.id;
        }
        if(category.isEmpty()||category.equals("object")){
            for(var template:g.objects.everything())if(template.id<Ids.DYNAMIC&&(name.equals(Long.toString(template.id))||template.def.equals(name))){
                var object=copy(template,g.state.newEntityId(),x,z);return object.name+" #"+object.id;
            }
        }
        ItemDef item=Items.find(name);
        if(category.isEmpty()&&item!=null){g.state.mint(item,1);var object=g.objects.drop(new ItemStack(item,1),x,DevMap.FLOOR+0.1f,z);return object.name+" #"+object.id;}
        throw new IllegalArgumentException("Unknown spawn '"+spec+"'. Use features to list content; items lists item IDs.");
    }
    public WorldObject copy(WorldObject template,long id,float x,float z) {
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
        WorldObject object;
        if(template instanceof Furniture furniture){
            var copy=new Furniture(id,furniture.kind,bx,bz,bx+furniture.x1-furniture.x0,bz+furniture.z1-furniture.z0,furniture.h,furniture.facing,0,true);
            g.objects.furniture.add(copy);copy.place(g,true);object=copy;
        }else if(template instanceof Node node){var copy=node.copyAt(id,bx,bz);g.objects.nodes.add(copy);copy.setBlocks(g,true);object=copy;
        }else if(template instanceof rpg.objects.Door door){
            int[][] cells=new int[door.cells.length][2];for(int i=0;i<cells.length;i++){cells[i][0]=bx+door.cells[i][0]-door.cells[0][0];cells[i][1]=bz+door.cells[i][1]-door.cells[0][1];}
            var copy=new rpg.objects.Door(id,door.name,cells,false,door.key,door.lockLevel,0);copy.spawn(g);object=copy;
        }else if(template instanceof rpg.dungeon.Dungeons.Door door){
            var copy=new rpg.dungeon.Dungeons.Door(id,door.layout,door.inside,x,z);copy.toX=door.toX;copy.toZ=door.toZ;copy.toYaw=door.toYaw;object=copy;
        }else if(template instanceof rpg.dungeon.Dungeons.Trap trap){var copy=new rpg.dungeon.Dungeons.Trap(id,trap.kind,trap.threat,trap.level,x,z);g.dungeons.traps.add(copy);object=copy;
        }else if(template instanceof Fixtures.Board){object=new Fixtures.Board(id,x,DevMap.FLOOR+1,z,DevMap.jurisdiction(x,z));
        }else if(template instanceof Fixtures.Altar){object=new Fixtures.Altar(id,x,DevMap.FLOOR+0.6f,z);
        }else if(template instanceof Fixtures.Cabinet){object=new Fixtures.Cabinet(id,x,DevMap.FLOOR+1,z);
        }else if(template instanceof Fixtures.Terminal){object=new Fixtures.Terminal(id,x,DevMap.FLOOR+0.6f,z);
        }else if(template instanceof Fixtures.Kiosk){object=new Fixtures.Kiosk(id,x,DevMap.FLOOR+0.6f,z);
        }else if(template instanceof Fixtures.Stall){object=new Fixtures.Stall(id,x,DevMap.FLOOR+0.8f,z,0);
        }else if(template instanceof BuildSpot){object=new BuildSpot(id,x,DevMap.FLOOR,z);
        }else if(template instanceof Station station){object=new Station(id,station.def,station.name,station.kind,x,DevMap.FLOOR+0.6f,z);
        }else if(template instanceof Fire){object=new Fire(id,x,DevMap.FLOOR,z,g.state.minutes+30,0);
        }else if(template instanceof GroundItem item){var stack=item.stack.copyWhole();g.state.mint(stack.def,stack.amount);object=new GroundItem(id,stack,x,DevMap.FLOOR+0.1f,z);
        }else if(template instanceof Container container){object=new Container(id,container.def,container.name,container.kind,x,DevMap.FLOOR+0.4f,z,container.contents.size(),0);
        }else throw new IllegalArgumentException("No prefab factory for "+template.def);
        object.debugPrefab="object:"+template.id;
        return g.objects.add(object);
    }
    public WorldObject restore(String prefab,long id,float x,float z) {
        if(prefab.startsWith("object:"))return copy(java.util.Objects.requireNonNull(g.objects.get(Long.parseLong(prefab.substring(7)))),id,x,z);
        WorldObject object;
        if(prefab.startsWith("furniture:")){
            var kind=Furniture.Kind.valueOf(prefab.substring(10).toUpperCase(Locale.ROOT));
            var f=new Furniture(id,kind,(int)x,(int)z,(int)x,(int)z,kind==Furniture.Kind.BOOKCASE?2:1,0,0,true);g.objects.furniture.add(f);f.place(g,true);object=f;
        }else {var kind=Recipes.Station.valueOf(prefab.substring(8).toUpperCase(Locale.ROOT));object=new Station(id,key(kind),kind.name(),kind,x,DevMap.FLOOR+0.6f,z);}
        object.debugPrefab=prefab;return g.objects.add(object);
    }
}
