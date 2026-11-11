package rpg;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.DevMap;

/** Alpha 0.3.9 gamefixes. */
class Gamefixes039Test {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.3.9", Appearance.henric(), true));
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }

    // ------------------------------------------------------------------ ids: one thing, one id
    @Test void theEstateAndItsPeopleHaveIdsNoOneElseHas() {
        Set<Long> actors = new HashSet<>();
        actors.add(g.player.id);
        for (Npc n : g.npcs) actors.add(n.id);
        for (var a : g.animals.all()) actors.add(a.id);
        List<String> clash = new ArrayList<>();
        for (var o : g.objects.everything()) {
            boolean estate = o.id >= Ids.ESTATE && o.id < Ids.ESTATE_END;
            if (rpg.world.Mansion.onEstate(o.x, o.z) && o instanceof rpg.objects.Furniture && !estate) clash.add("mansion furniture outside the estate band: " + o.id + " " + o.name);
            if (estate && actors.contains(o.id)) clash.add(o.id + " " + o.name + " / " + g.nameOf(o.id));
            if (o.id >= Ids.BARON && o.id <= Ids.MANSION_ORCHARDIST) clash.add("household id taken by " + o.name);
        }
        assertTrue(clash.isEmpty(), "" + clash);
        for (long id = Ids.BARON; id <= Ids.MANSION_ORCHARDIST; id++) assertTrue(g.actor(id) instanceof Npc, "household member " + id);
    }

    @Test void aThingAndAPersonWithTheSameNumberKeepTheirOwnFireSensors() {
        // Eda (505) and a bed of the guild row share a number: each has its own sensor where it really is
        Npc eda = (Npc) g.actor(Ids.VILLAGER);
        g.ai.reload(eda);
        g.teleport(eda, 63.5f, DevMap.FLOOR, 70.5f); g.teleport(g.player, 63.5f, DevMap.FLOOR, 74.5f);
        game.tick(0);
        g.chemistry.emit(g.player, astratto.bridge.AstrattoNative.CHEM_FIRE, eda.x, eda.chestY(), eda.z, 0.5f, 4, 0.2f);
        for (int i = 0; i < 30; i++) game.tick(1f / 60);
        var mine = game.engine().chemistry().stream().filter(s -> s.id() == eda.id).findFirst().orElse(null);
        assertNotNull(mine, "Eda has a sensor");
        assertTrue(Math.hypot(mine.x() - eda.x, mine.z() - eda.z) < 1.5f, "Eda's sensor is on Eda: " + mine.x() + "," + mine.z());
        assertEquals(g.player.id, mine.owner(), "and the fire on her is the player's doing");
    }
}
