package rpg.objects;

import static astratto.bridge.AstrattoNative.*;
import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.interact.WorldObject;
import rpg.render.Props;
import rpg.world.DevMap;
import rpg.world.Voxels;

/** Structural boxes shared with prop meshes: real rigid contact plus gameplay ray contact. */
public final class PropCollision {
    private record Box(WorldObject owner, long handle, float x, float y, float z, float hx, float hy, float hz) {}
    private final Rpg g;
    private final List<Box> boxes = new ArrayList<>();
    public PropCollision(Rpg g) { this.g = g; }
    public void add(WorldObject object) {
        Props.Layer[] layers;
        if (object instanceof Fixtures.Board) layers = Props.bountyBoard();
        else if (object instanceof rpg.dungeon.Dungeons.Door door && !door.inside) layers = Props.dungeonEntrance(door.layout.theme.ordinal());
        else return;
        // Stone/timber are structural; paper, darkness, grass and glow are decorative.
        for (int layer = 0; layer < Math.min(2, layers.length); layer++) for (float[] b : layers[layer].boxes().list) {
            if (Math.min(b[3], Math.min(b[4], b[5])) < 0.07f) continue;
            long handle = g.game.reserve(KIND_RIGID);
            float x = object.x + b[0], y = DevMap.FLOOR + b[1], z = object.z + b[2];
            g.game.commands().rigidCreate(handle, SHAPE_BOX, RIGID_STATIC, b[3]/2, b[4]/2, b[5]/2, 0,
                    x,y,z,1,0,0,0,0,0,0,0,0,0,0.8f,0,0,null);
            boxes.add(new Box(object,handle,x,y,z,b[3]/2,b[4]/2,b[5]/2));
        }
    }
    public void update() {
        boxes.removeIf(box -> {
            if (!box.owner.removed && g.objects.get(box.owner.id) == box.owner) return false;
            g.game.commands().release(box.handle); return true;
        });
    }
    public boolean overlaps(float x,float y,float z,float radius,float height) {
        for (Box b:boxes) if(!b.owner.removed && x+radius>b.x-b.hx && x-radius<b.x+b.hx && z+radius>b.z-b.hz && z-radius<b.z+b.hz && y+height>b.y-b.hy && y<b.y+b.hy)return true;
        return false;
    }
    public void ray(Voxels.Hit hit, float ox,float oy,float oz,float dx,float dy,float dz,float max) {
        for (Box b:boxes) {
            if(b.owner.removed)continue;
            float enter=0,exit=hit.hit?Math.min(max,hit.dist):max; int normal=0; float sign=0;
            float[] o={ox,oy,oz},d={dx,dy,dz},c={b.x,b.y,b.z},half={b.hx,b.hy,b.hz};
            for(int axis=0;axis<3;axis++) {
                if(Math.abs(d[axis])<1e-7f){if(o[axis]<c[axis]-half[axis]||o[axis]>c[axis]+half[axis]){exit=-1;break;}continue;}
                float a=(c[axis]-half[axis]-o[axis])/d[axis],z=(c[axis]+half[axis]-o[axis])/d[axis];
                float near=Math.min(a,z);if(near>enter){enter=near;normal=axis;sign=-Math.signum(d[axis]);}exit=Math.min(exit,Math.max(a,z));
            }
            if(enter>exit || exit<0)continue;
            hit.hit=true;hit.dist=enter;hit.x=ox+dx*enter;hit.y=oy+dy*enter;hit.z=oz+dz*enter;
            hit.bx=(int)Math.floor(hit.x);hit.by=(int)Math.floor(hit.y);hit.bz=(int)Math.floor(hit.z);
            hit.nx=normal==0?(int)sign:0;hit.ny=normal==1?(int)sign:0;hit.nz=normal==2?(int)sign:0;
            hit.block=b.owner instanceof Fixtures.Board?rpg.world.Blocks.PLANKS:rpg.world.Blocks.STONE;
        }
    }
}
