package rpg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class PreferencesTest {
    @Test void bindingsSwapConflictsAndSurviveSerialization() {
        var p = new Preferences(); assertTrue(p.bind(Preferences.Action.CROUCH, GLFW_KEY_Q));
        assertEquals(GLFW_KEY_LEFT_CONTROL, p.keys.get(Preferences.Action.DODGE));
        var read = Preferences.from(p.data()); assertEquals(p.keys, read.keys);
        assertTrue(read.bind(Preferences.Action.BACK, GLFW_KEY_F5));
        assertEquals(GLFW_KEY_S, read.keys.get(Preferences.Action.SAVE));
    }
    @Test void attackCanBeReboundToAnotherMouseButtonWithoutChangingUiClicks() {
        var p = new Preferences(); var input = new astratto.platform.Input();
        assertTrue(p.bind(Preferences.Action.ATTACK, astratto.platform.Input.MOUSE_OFFSET + GLFW_MOUSE_BUTTON_RIGHT));
        input.inject(astratto.platform.Input.MOUSE_OFFSET + GLFW_MOUSE_BUTTON_RIGHT, true); input.endFrame();
        assertTrue(p.pressed(input, Preferences.Action.ATTACK)); assertFalse(p.pressed(input, Preferences.Action.GUARD));
    }
    @Test void corruptAndFuturePreferencesKeepSettingsFiniteAndWithinHardwareBounds() {
        var p = Preferences.from(java.util.Map.of("master", -20, "fov", 99999, "sensitivity", Float.NaN, "quality", 50, "future", true));
        assertEquals(0, p.master); assertEquals(110, p.fov); assertEquals(1, p.sensitivity); assertEquals(3, p.quality);
    }
    @Test void turnChordWorksInEitherPressOrderAndKeepsLegacyKeys() {
        var p = Preferences.from(java.util.Map.of("keys", java.util.Map.of("FORWARD",GLFW_KEY_UP)));
        assertEquals("Alt+S",p.label(Preferences.Action.QUICK_TURN));
        var in = new astratto.platform.Input();
        in.inject(GLFW_KEY_S,true);in.endFrame();assertFalse(p.pressed(in,Preferences.Action.QUICK_TURN));
        in.inject(GLFW_KEY_LEFT_ALT,true);in.endFrame();assertTrue(p.pressed(in,Preferences.Action.QUICK_TURN));
        assertTrue(p.bind(Preferences.Action.QUICK_TURN,GLFW_KEY_Q | GLFW_MOD_CONTROL << 16));
        assertEquals(p.keys,Preferences.from(p.data()).keys);
        assertEquals(GLFW_KEY_UP,p.keys.get(Preferences.Action.FORWARD));
    }
    @Test void contextualInventoryKeysDoNotSwapTheCastingKey() {
        var p=new Preferences();assertTrue(p.bind(Preferences.Action.ROTATE,GLFW_KEY_F5));
        assertEquals(GLFW_KEY_R,p.keys.get(Preferences.Action.CAST));assertEquals(GLFW_KEY_F5,p.keys.get(Preferences.Action.SAVE));
        assertFalse(p.bind(Preferences.Action.FORWARD,-1));assertFalse(p.bind(Preferences.Action.FORWARD,GLFW_KEY_A | 64 << 16));
    }
}
