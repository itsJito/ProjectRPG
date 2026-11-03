package rpg.mount;

import rpg.world.Voxels;

/**
 * (0.3.0, gamefixes: horse riding) The collision controller a ridden animal (or, later, a vehicle) moves by. Its body is
 * a row of circles along its length (a horse is long, not round) of a given height. Each step it tries the requested
 * movement; if that is blocked horizontally it tests whether a permitted step up clears the obstacle - checking the
 * whole body's clearance at the raised height, and ground to stand on - and takes it if so; otherwise it slides along
 * whichever axis is free, and failing that stops against the obstacle. Gravity, landing and walking off edges are its
 * business too. Terrain stepping is separate from jumping: a small rise is taken in the stride.
 */
public final class MountController {
    public float x, y, z, yaw, vy;
    public boolean grounded = true;
    /** The body: half its length (front and rear circle centres), the circles' radius, its height; the step it may take. */
    public float halfLength, radius, height, step = 1.05f;
    /** Whether the last move was stopped (or slowed) by something solid, and whether it stepped up. */
    public boolean blocked, stepped;
    public float gravity = 22f;

    public MountController(float halfLength, float radius, float height) { this.halfLength = halfLength; this.radius = radius; this.height = height; }

    /** Whether the whole body fits with its feet at this height, facing yaw. */
    public boolean fits(Voxels vox, float px, float py, float pz, float yaw) {
        float fx = (float) -Math.sin(yaw), fz = (float) -Math.cos(yaw);
        for (float along : new float[]{-halfLength, 0, halfLength}) {
            float cx = px + fx * along, cz = pz + fz * along;
            for (int bx = (int) Math.floor(cx - radius); bx <= (int) Math.floor(cx + radius); bx++)
                for (int bz = (int) Math.floor(cz - radius); bz <= (int) Math.floor(cz + radius); bz++) {
                    // the circle against the cell's square
                    float nx = Math.max(bx, Math.min(cx, bx + 1)), nz = Math.max(bz, Math.min(cz, bz + 1));
                    if ((nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) > radius * radius) continue;
                    for (int by = (int) Math.floor(py + 0.05f); by <= (int) Math.floor(py + height - 0.05f); by++) if (vox.solid(bx, by, bz)) return false;
                }
        }
        return true;
    }

    /** The highest ground under the body's footprint at or below a height (the top of the solid there), or -inf. */
    public float ground(Voxels vox, float px, float py, float pz, float yaw) {
        float fx = (float) -Math.sin(yaw), fz = (float) -Math.cos(yaw), best = Float.NEGATIVE_INFINITY;
        // (the body's ends are probed too: the leading hooves find a step before the middle of the body is over it)
        float tip = halfLength + radius * 1.02f;
        for (float along : new float[]{-tip, -halfLength, 0, halfLength, tip}) for (float side : new float[]{-0.7f, 0.7f}) {
            float cx = px + fx * along - fz * radius * side, cz = pz + fz * along + fx * radius * side;
            int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz);
            for (int by = (int) Math.floor(py + 0.05f); by >= Math.max(0, (int) Math.floor(py) - 4); by--) if (vox.solid(bx, by, bz)) { best = Math.max(best, by + 1); break; }
        }
        return best;
    }

    /**
     * One step: horizontal movement (world metres this step) and the turn to a new yaw; gravity and landing. Returns the
     * distance actually covered horizontally.
     */
    public float move(Voxels vox, float dx, float dz, float newYaw, float dt) {
        blocked = false; stepped = false;
        // turning in place is checked too: a long body cannot swing round inside a narrow lane
        if (fits(vox, x, y, z, newYaw)) yaw = newYaw;
        float ox = x, oz = z;
        if (dx != 0 || dz != 0) {
            if (fits(vox, x + dx, y, z + dz, yaw)) { x += dx; z += dz; }
            else if (grounded && stepUp(vox, x + dx, z + dz)) { x += dx; z += dz; stepped = true; }
            else {
                blocked = true;
                if (Math.abs(dx) > 1e-5f && fits(vox, x + dx, y, z, yaw)) x += dx;
                else if (Math.abs(dz) > 1e-5f && fits(vox, x, y, z + dz, yaw)) z += dz;
            }
        }
        // gravity, and the ground
        float floor = ground(vox, x, y + (stepped ? 0 : 0.02f), z, yaw);
        if (grounded && y - floor > 0.02f && y - floor <= step * 0.6f && vy <= 0) { y = floor; }     // down a small step in the stride
        else if (y > floor + 0.02f || vy > 0) {
            grounded = false;
            vy -= gravity * dt;
            float ny = y + vy * dt;
            if (vy > 0 && !fits(vox, x, ny, z, yaw)) { vy = 0; ny = y; }          // the head against a ceiling
            if (ny <= floor) { ny = floor; vy = 0; grounded = true; }
            y = ny;
        } else { y = Math.max(y, floor); grounded = true; vy = 0; }
        return (float) Math.hypot(x - ox, z - oz);
    }

    /** Whether a step up (no taller than step) puts the body clear at the new place, with ground under it there. */
    private boolean stepUp(Voxels vox, float nx, float nz) {
        float top = ground(vox, nx, y + step + 0.01f, nz, yaw);
        if (!(top > y + 0.02f) || top - y > step) return false;
        if (!fits(vox, nx, top, nz, yaw) || !fits(vox, x, top, z, yaw)) return false;
        y = top;
        return true;
    }
}
