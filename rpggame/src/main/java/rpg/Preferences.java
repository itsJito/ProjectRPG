package rpg;

import astratto.util.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.lwjgl.glfw.GLFW.*;

/** Machine preferences are separate from character saves. Missing/new keys keep their defaults. */
public final class Preferences {
    public enum Action {
        FORWARD("Forward", GLFW_KEY_W), BACK("Backward", GLFW_KEY_S), LEFT("Left", GLFW_KEY_A), RIGHT("Right", GLFW_KEY_D),
        SPRINT("Sprint", GLFW_KEY_LEFT_SHIFT), CROUCH("Crouch", GLFW_KEY_LEFT_CONTROL), JUMP("Jump / grab", GLFW_KEY_SPACE),
        DODGE("Dodge", GLFW_KEY_Q), INTERACT("Interact", GLFW_KEY_E), CAMERA("Camera", GLFW_KEY_V), MAP("Map", GLFW_KEY_M), INVENTORY("Inventory", GLFW_KEY_I), SHEATHE("Sheathe / draw (hold)", GLFW_KEY_F), ATTACK("Attack / carry", astratto.platform.Input.MOUSE_OFFSET), GUARD("Guard / off hand", astratto.platform.Input.MOUSE_OFFSET + 1);
        public final String title; public final int defaultKey;
        Action(String title, int key) { this.title = title; defaultKey = key; }
    }
    public final Map<Action, Integer> keys = new java.util.EnumMap<>(Action.class);
    public float master = 1, sfx = 1, music = 0.65f, voice = 1, ambience = 0.8f, ui = 0.8f;
    public float sensitivity = 1, fov = 70, renderScale = 1;
    public int quality = 1, targetFps = 60;
    public boolean fullscreen, vsync = true, autoScale = true, reducedMotion, subtitles = true, invertY, toggleCrouch, toggleSprint;
    public Preferences() { for (Action a : Action.values()) keys.put(a, a.defaultKey); }

    /** Rebinding swaps conflicting gameplay keys; reserved interface/debug keys are never captured. */
    public boolean bind(Action action, int key) {
        boolean mouse = key >= astratto.platform.Input.MOUSE_OFFSET && key <= astratto.platform.Input.MOUSE_OFFSET + GLFW_MOUSE_BUTTON_LAST;
        if ((!mouse && (key < GLFW_KEY_SPACE || key > GLFW_KEY_LAST)) || key == GLFW_KEY_ESCAPE || key == GLFW_KEY_TAB || key == GLFW_KEY_GRAVE_ACCENT || key == GLFW_KEY_C || key == GLFW_KEY_K || key == GLFW_KEY_J || key == GLFW_KEY_B || key == GLFW_KEY_R || key == GLFW_KEY_CAPS_LOCK || key >= GLFW_KEY_F1 && key <= GLFW_KEY_F25) return false;
        int previous = keys.get(action);
        for (Action a : Action.values()) if (a != action && keys.get(a) == key) keys.put(a, previous);
        keys.put(action, key); return true;
    }
    public boolean down(astratto.platform.Input in, Action a) { return in.keyDown(keys.get(a)); }
    public boolean pressed(astratto.platform.Input in, Action a) { return in.keyPressed(keys.get(a)); }
    public boolean released(astratto.platform.Input in, Action a) { return in.keyReleased(keys.get(a)); }

    public void apply(Config c) { c.quality = quality; c.targetFps = targetFps; c.renderScale = renderScale; c.autoScale = autoScale; if (reducedMotion) c.motionBlur = false; }
    public void mix(astratto.bridge.CommandBuffer commands) { commands.audioMix(master, false, new float[]{sfx, music, voice, ambience, ui}, 0); }
    public Map<String, Object> data() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String name : new String[]{"master", "sfx", "music", "voice", "ambience", "ui", "sensitivity", "fov", "renderScale", "quality", "targetFps", "fullscreen", "vsync", "autoScale", "reducedMotion", "subtitles", "invertY", "toggleCrouch", "toggleSprint"}) {
            try { m.put(name, getClass().getField(name).get(this)); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        Map<String, Object> bindings = new LinkedHashMap<>(); keys.forEach((a, k) -> bindings.put(a.name(), k)); m.put("keys", bindings); return m;
    }
    public static Preferences from(Map<String, Object> m) {
        Preferences p = new Preferences();
        for (var entry : m.entrySet()) try {
            var f = Preferences.class.getField(entry.getKey()); Object v = entry.getValue();
            if (f.getType() == float.class && v instanceof Number n && Float.isFinite(n.floatValue())) f.setFloat(p, n.floatValue());
            else if (f.getType() == int.class && v instanceof Number n) f.setInt(p, n.intValue());
            else if (f.getType() == boolean.class && v instanceof Boolean b) f.setBoolean(p, b);
        } catch (ReflectiveOperationException ignored) { }
        p.master = clamp(p.master, 0, 1); p.sfx = clamp(p.sfx, 0, 1); p.music = clamp(p.music, 0, 1); p.voice = clamp(p.voice, 0, 1); p.ambience = clamp(p.ambience, 0, 1); p.ui = clamp(p.ui, 0, 1);
        p.sensitivity = clamp(p.sensitivity, 0.1f, 4); p.fov = clamp(p.fov, 50, 110); p.renderScale = clamp(p.renderScale, 0.5f, 1); p.quality = Math.max(0, Math.min(3, p.quality)); p.targetFps = Math.max(30, Math.min(144, p.targetFps));
        Object bindings = m.get("keys");
        if (bindings instanceof Map<?, ?> map) for (Action a : Action.values()) if (map.get(a.name()) instanceof Number n) p.bind(a, n.intValue());
        return p;
    }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    public static Preferences load(Path path) { try { return from(Json.parseObject(Files.readString(path))); } catch (java.io.IOException | RuntimeException e) { return new Preferences(); } }
    public void save(Path path) {
        try { Files.createDirectories(path.toAbsolutePath().getParent()); Path temp = path.resolveSibling(path.getFileName() + ".tmp"); Files.writeString(temp, Json.write(data())); Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        catch (java.io.IOException e) { System.err.println("Could not save preferences: " + e.getMessage()); }
    }
}
