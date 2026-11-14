package rpg.world;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.objects.Furniture;
import rpg.world.shadowveil.Architecture;
import rpg.world.shadowveil.Census;
import rpg.world.shadowveil.Registry;
import rpg.world.shadowveil.Rooms;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.4) ShadowVeil's interiors, checked in the world itself: every enterable building fitted out and approved on the
 * registry's rubric with no defect; every resident given a real bed indoors and every indoor worker a real post, each
 * reached by the game's own routes from outside the door; every room of every building reached from its way in; every
 * kingdom's jail with its cells, its bank with its banker, its graveyard with its plot and its church with its altar;
 * the ten new people in the world.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class Gamefixes044Test {
    private static Game game;
    private static Rpg g;
    static final String[] KINGDOMS = {"Loxford", "Harlette", "Valgard", "Gorrick", "Azharad", "Severia", "Camelot"};

    @BeforeAll static void grow() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        game = Game.create(cfg);
        game.activate(ShadowVeil.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.4.4", new Appearance(), true));
        game.tick(0);
    }
    @AfterAll static void close() { if (game != null) game.close(); WorldMap.activateDevmap(); }

    private static Registry reg() { return ShadowVeil.terrain().arch.registry; }
    private static Architecture arch() { return ShadowVeil.terrain().arch; }

    @Test @org.junit.jupiter.api.Order(1) void theVersionIsAtLeast044() { assertTrue(rpg.Main.VERSION.startsWith("Alpha 0.4."), rpg.Main.VERSION); }

    @Test @org.junit.jupiter.api.Order(2) void everyEnterableBuildingIsFittedOutAndApproved() {
        List<String> bad = new ArrayList<>();
        int fitted = 0, rooms = 0;
        for (Registry.Entry e : reg().entries) {
            if (!e.enterable || e.underground) continue;
            if (e.fit == null) { bad.add(e.id + " has no interior"); continue; }
            fitted++;
            rooms += e.fit.rooms.size();
            if (!e.approved) bad.add(e.id + " scored " + e.score + " " + e.rubric + " " + e.fit.defects);
            if (e.fit.rooms.isEmpty()) bad.add(e.id + " has no rooms");
            for (var r : e.fit.rooms) if (!r.lit) bad.add(e.id + "'s " + r.use + " is unlit");
            if (e.score < Registry.APPROVE) bad.add(e.id + " is under " + Registry.APPROVE);
        }
        System.out.println("[interiors] " + fitted + " buildings, " + rooms + " rooms");
        assertTrue(fitted >= 500, "every enterable building: " + fitted);
        assertTrue(bad.isEmpty(), () -> bad.size() + ": " + bad.subList(0, Math.min(20, bad.size())));
    }

    @Test @org.junit.jupiter.api.Order(3) void everyoneSleepsInARealBedIndoorsAndWorksAtARealPost() {
        List<String> bad = new ArrayList<>();
        int beds = 0, posts = 0;
        for (var a : Census.all().values()) {
            if (a.homeId() == null || !(g.actor(a.id()) instanceof Npc n)) continue;
            Registry.Entry home = reg().byId(a.homeId());
            if (home == null || home.fit == null) continue;   // a camp's bedroll, a cave's
            Rooms.Site site = Rooms.site(a.homeId());
            Furniture bed = site == null ? null : site.beds.stream().filter(f -> f.owner == a.id()).findFirst().orElse(null);
            if (bed == null) { bad.add(a.name() + " has no bed of their own in " + a.homeId()); continue; }
            beds++;
            if (Math.hypot(n.homeX - bed.x, n.homeZ - bed.z) > 0.6 || !Float.isFinite(n.homeY)) bad.add(a.name() + "'s home is not their bed");
            if (Census.post(a.id()) != null) posts++;
        }
        System.out.println("[census] " + beds + " beds owned, " + posts + " indoor posts");
        assertTrue(bad.isEmpty(), () -> bad.size() + ": " + bad.subList(0, Math.min(20, bad.size())));
        assertTrue(beds >= 150, "beds owned: " + beds);
        assertTrue(posts >= 40, "indoor posts: " + posts);
    }

    /** A walkable cell next to a point on a floor, from which it is reached (a bed's flank, a post itself). */
    private static float[] beside(float x, int y, float z) { return beside(x, y, z, 2); }
    private static float[] beside(float x, int y, float z, int reach) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int r = 0; r <= reach; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            if (g.vox.navigation.validPoint(bx + dx + 0.5f, y, bz + dz + 0.5f, d -> true)) return new float[]{bx + dx + 0.5f, y, bz + dz + 0.5f};
        }
        return null;
    }
    private static boolean routes(float[] from, float[] to, java.util.function.Predicate<rpg.objects.Door> may) {
        if (from == null || to == null) return false;
        return g.vox.navigation.route(from[0], from[1], from[2], to[0], to[1], to[2], may, false, 0, new rpg.ai.RouteBook(), 0) != null;
    }

    @Test @org.junit.jupiter.api.Order(4) void everyoneWalksFromTheirDoorToTheirBedAndTheirPost() {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (var a : Census.all().values()) {
            if (!(g.actor(a.id()) instanceof Npc n) || a.homeStage() == null || !Float.isFinite(n.homeY)) continue;
            float[] out = {a.homeStage()[0], ShadowVeil.standY(a.homeStage()[0], a.homeStage()[1]), a.homeStage()[1]};
            java.util.function.Predicate<rpg.objects.Door> may = d -> !d.locked || d.owner == n.id;
            float[] bed = beside(n.homeX, (int) n.homeY, n.homeZ);
            if (!routes(out, bed, may)) bad.add(a.name() + ": no way from " + (int) out[0] + "," + (int) out[2] + " to the bed at " + (int) n.homeX + "," + (int) n.homeY + "," + (int) n.homeZ);
            float[] post = Census.post(a.id());
            if (post != null) {
                float[] at = beside(post[0], (int) post[1], post[2]);
                if (!routes(out, at, may)) bad.add(a.name() + ": no way to the post at " + (int) post[0] + "," + (int) post[1] + "," + (int) post[2]);
            }
            checked++;
        }
        System.out.println("[routes] " + checked + " people routed to bed and post");
        assertTrue(checked >= 150, "people routed: " + checked);
        assertTrue(bad.isEmpty(), () -> bad.size() + ": " + bad.subList(0, Math.min(25, bad.size())));
    }

    @Test @org.junit.jupiter.api.Order(5) void everyRoomOfEveryBuildingIsReachedFromItsDoor() {
        List<String> bad = new ArrayList<>();
        int rooms = 0;
        for (Registry.Entry e : reg().entries) {
            if (e.fit == null || e.entrances.isEmpty()) continue;
            Architecture.Structure s = arch().byId(e.id);
            var en = e.entrances.get(0);
            float dx = en.facing() == 'e' ? 1 : en.facing() == 'w' ? -1 : 0, dz = en.facing() == 's' ? 1 : en.facing() == 'n' ? -1 : 0;
            float ox = en.x() + 0.5f + dx * 2, oz = en.z() + 0.5f + dz * 2;
            // before the door (a waterside building's landing stage, or the step inside its door where its front is water)
            float[] out = beside(ox, (int) Math.floor(ShadowVeil.standY(ox, oz) + 0.01f), oz, 3);
            if (out == null) out = beside(en.x() + 0.5f, en.y() + 1, en.z() + 0.5f, 1);
            if (out == null) { bad.add(e.id + ": no ground before its door"); continue; }
            for (var r : e.fit.rooms) {
                // a cell of the room someone could stand on
                // the standable cell of the room nearest its middle (not a corner pocket a tower's wall leaves)
                float[] in = null; double bd = 1e9;
                float mx = (r.rect[0] + r.rect[2] + 1) / 2f, mz = (r.rect[1] + r.rect[3] + 1) / 2f;
                for (int x = r.rect[0]; x <= r.rect[2]; x++) for (int z = r.rect[1]; z <= r.rect[3]; z++)
                    if (g.vox.navigation.validPoint(x + 0.5f, r.y, z + 0.5f, d -> true) && Math.hypot(x + 0.5f - mx, z + 0.5f - mz) < bd) { bd = Math.hypot(x + 0.5f - mx, z + 0.5f - mz); in = new float[]{x + 0.5f, r.y, z + 0.5f}; }
                rooms++;
                if (in == null) { bad.add(e.id + "'s " + r.use + " has no floor to stand on"); continue; }
                if (!routes(out, in, d -> true) && !(r.level > 0 && e.fit.ladders > 0)) bad.add(e.id + "'s " + r.use + " (storey " + r.level + ") is not reached from its door: " + (int) out[0] + "," + (int) out[1] + "," + (int) out[2] + " -> " + (int) in[0] + "," + (int) in[1] + "," + (int) in[2]);
            }
            if (s == null) bad.add(e.id + " is not raised");
        }
        System.out.println("[rooms] " + rooms + " rooms routed from their doors");
        assertTrue(bad.isEmpty(), () -> bad.size() + ": " + bad.subList(0, Math.min(25, bad.size())));
    }

    @Test @org.junit.jupiter.api.Order(6) void everyKingdomHasItsJailBankGraveyardAndAltar() {
        List<String> bad = new ArrayList<>();
        Set<String> banks = new HashSet<>();
        for (var b : g.bank.branches()) {
            banks.add(b.jurisdiction());
            if (!(g.actor(b.teller()) instanceof Npc t) || !g.bank.isTeller(t)) bad.add("the branch " + b.name() + " has no banker");
        }
        Set<String> plots = new HashSet<>();
        for (var p : g.graveyards.plots) plots.add(p.jurisdiction());
        for (String k : KINGDOMS) {
            var cell = Jails.forJurisdiction(k);
            if (cell == null) bad.add(k + " has no jail cell");
            else if (g.objects.jailDoor(k) == null || !g.objects.jailDoor(k).locked) bad.add(k + "'s cell door is not locked");
            if (!banks.contains(k)) bad.add(k + " has no bank (" + banks + ")");
            if (!plots.contains(k)) bad.add(k + " has no graveyard plot (" + plots + ")");
            if (Rooms.altarFor(k, 0, 0, 1e9f) == null) bad.add(k + " has no altar");
        }
        assertTrue(bad.isEmpty(), () -> String.valueOf(bad));
    }

    @Test @org.junit.jupiter.api.Order(7) void tenMorePeopleKeepTheNewBuildings() {
        var added = rpg.world.shadowveil.Atlas.list(rpg.world.shadowveil.Atlas.map(rpg.world.shadowveil.Atlas.get().root, "census"), "additions");
        assertTrue(added.size() >= 10, "the ten (and any added since)");
        for (String nm : new String[]{"Bogdan", "Milena", "Lothar", "Hilde", "Gunnar", "Astrid", "Nadim", "Samir", "Emrys", "Rhiannon"})
            assertTrue(added.stream().anyMatch(p -> nm.equals(rpg.world.shadowveil.Atlas.s(p, "name", ""))), nm + " is among the additions");
        for (var p : added) {
            String name = rpg.world.shadowveil.Atlas.s(p, "name", "?");
            assertTrue(g.npcs.stream().anyMatch(n -> n.name.equals(name)), name + " is in the world");
        }
    }
}
