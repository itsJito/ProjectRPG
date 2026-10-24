package rpg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class PreferencesTest {
    @Test void bindingsSwapConflictsAndSurviveSerialization() {
        var p = new Preferences(); assertTrue(p.bind(Preferences.Action.CROUCH, GLFW_KEY_Q));
        assertEquals(GLFW_KEY_LEFT_CONTROL, p.keys.get(Preferences.Action.DODGE));
        var read = Preferences.from(p.data()); assertEquals(p.keys, read.keys);
        assertFalse(read.bind(Preferences.Action.BACK, GLFW_KEY_F5));
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
}
