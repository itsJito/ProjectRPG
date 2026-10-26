package rpg.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HitVolumesTest {
    @Test void aLyingNativeHeadCanBeHitAndTheOldStandingHeadCannot() {
        var cfg = new astratto.bridge.NativeEngine.Config(); cfg.deterministic = true;
        try (var game = astratto.game.Game.create(cfg)) {
            game.activate(rpg.world.DevMap.build(game.engine()));
            var g = game.add(new rpg.Rpg(game, "Articulated contacts", new rpg.actor.Appearance(), true));
            var npc = (rpg.ai.Npc) g.actor(rpg.Ids.BANDIT_1);
            for (var person : g.npcs) g.ai.unload(person);
            npc.ragdoll = game.reserve(astratto.bridge.AstrattoNative.KIND_RAGDOLL);
            game.commands().ragdollCreate(npc.ragdoll, 1, astratto.bridge.AstrattoNative.SYDNEY_HUMAN,
                    astratto.bridge.AstrattoNative.RAGDOLL_STAND, npc.x, npc.y, npc.z, 0, 0, 0, 0);
            game.tick(0);
            float[] lying = g.lastBodies.ragdoll(npc.ragdoll).p.clone();
            for (int i = 0; i < 15; i++) {
                float x = lying[i * 3], y = lying[i * 3 + 1];
                lying[i * 3] = npc.x + y - npc.y; lying[i * 3 + 1] = npc.y + 0.35f - (x - npc.x);
            }
            game.commands().ragdollPose(npc.ragdoll, 0, 0, 0, lying); game.tick(0);
            float[] actual = g.lastBodies.ragdoll(npc.ragdoll).p;
            int head = astratto.bridge.AstrattoNative.RD_HEAD * 3;
            float x = actual[head] + 0.1f, y = actual[head + 1], z = actual[head + 2];
            assertTrue(Float.isFinite(HitVolumes.ray(g, npc, x, y, z - 2, 0, 0, 1, 4, 0.03f)));
            assertEquals(Gore.Part.HEAD, HitVolumes.part(g, npc, actual[head], y, z));
            assertTrue(Float.isInfinite(HitVolumes.ray(g, npc, npc.x, npc.y + 1.7f, npc.z - 2, 0, 0, 1, 4, 0.03f)));
            npc.alive=false;npc.loaded=true;npc.hp=0;
            var corpse=g.objects.add(new rpg.objects.Corpse(g.state.newEntityId(),npc.name,npc.id,npc.x,npc.y,npc.z,npc.id));npc.corpse=corpse.id;
            var shot=new CombatSystem.Projectile();shot.owner=g.player;shot.x=x;shot.y=y;shot.z=z-0.6f;shot.vz=30;shot.life=1;
            g.combat.projectiles.add(shot);g.combat.update(0.05f);
            assertTrue(shot.done,"a live projectile must collide with a dead body's actual head");
            g.gore.of(npc).severed[Gore.Part.HEAD.ordinal()] = true;
            assertTrue(Float.isInfinite(HitVolumes.ray(g, npc, x, y, z - 2, 0, 0, 1, 4, 0.03f)), "a removed head leaves no ghost hitbox");
        }
    }

    private float ray(float ox, float oy, float oz, float dx, float dy, float dz, float limit) {
        return HitVolumes.capsule(ox, oy, oz, dx, dy, dz, 0, 0, 0, 0, 2, 0, 0.2f, limit);
    }
    @Test void continuousContactFindsASmallHeadBetweenDiscreteProjectileSamples() {
        assertEquals(4.8f, ray(-5, 1, 0, 1, 0, 0, 10), 1e-4f);
        assertTrue(Float.isInfinite(ray(-5, 1, 0, 1, 0, 0, 4.7f)), "a wall before the body must win");
    }
    @Test void parallelCapAndInsideStartsRemainStable() {
        assertEquals(0.8f, ray(0, 3, 0, 0, -1, 0, 10), 1e-4f);
        assertEquals(0, ray(0, 1, 0, 1, 0, 0, 10));
        assertTrue(Float.isInfinite(ray(0.3f, 3, 0, 0, -1, 0, 10)));
        assertTrue(Float.isInfinite(ray(-5, 2.3f, 0, 1, 0, 0, 10)));
    }
    @Test void sphereDegeneracyIsAlsoAValidHeadVolume() {
        assertEquals(0.75f, HitVolumes.capsule(0, 0, -1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0.25f, 2), 1e-5f);
    }
}
