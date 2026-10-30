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
        DODGE("Dodge", GLFW_KEY_Q), INTERACT("Interact", GLFW_KEY_E), CAMERA("Camera", GLFW_KEY_V), MAP("Map", GLFW_KEY_M), INVENTORY("Inventory", GLFW_KEY_I), SHEATHE("Sheathe / draw (hold)", GLFW_KEY_F), ATTACK("Attack / carry", astratto.platform.Input.MOUSE_OFFSET), GUARD("Guard / off hand", astratto.platform.Input.MOUSE_OFFSET + 1),
        QUICK_TURN("Turn around", GLFW_KEY_S | GLFW_MOD_ALT << 16), JOG("Toggle jogging", GLFW_KEY_CAPS_LOCK),
        CAST("Cast selected spell", GLFW_KEY_R), QUICK_WHEEL("Quick Wheel / cursor", GLFW_KEY_TAB),
        CHARACTER("Character", GLFW_KEY_C), SKILLS("Skills", GLFW_KEY_K), JOURNAL("Journal", GLFW_KEY_J),
        MAGIC("Magic", GLFW_KEY_B), FAITH("Faith", GLFW_KEY_P), SAVE("Quicksave", GLFW_KEY_F5), LOAD("Quickload", GLFW_KEY_F6),
        SCREENSHOT("Screenshot", GLFW_KEY_F12), FULLSCREEN("Toggle fullscreen", GLFW_KEY_ENTER | GLFW_MOD_ALT << 16),
        CONSOLE("Developer console", GLFW_KEY_GRAVE_ACCENT), XP_INSPECTOR("XP inspector", GLFW_KEY_F8),
        AI_INSPECTOR("AI inspector", GLFW_KEY_F9), STATE_INSPECTOR("State inspector", GLFW_KEY_F10), TELEMETRY("Performance overlay", GLFW_KEY_F11),
        INSPECT_PREVIOUS("Previous inspected NPC", GLFW_KEY_PAGE_UP), INSPECT_NEXT("Next inspected NPC", GLFW_KEY_PAGE_DOWN),
        HOTBAR_1("Action slot 1", GLFW_KEY_1), HOTBAR_2("Action slot 2", GLFW_KEY_2), HOTBAR_3("Action slot 3", GLFW_KEY_3),
        HOTBAR_4("Action slot 4", GLFW_KEY_4), HOTBAR_5("Action slot 5", GLFW_KEY_5), HOTBAR_6("Action slot 6", GLFW_KEY_6),
        HOTBAR_7("Action slot 7", GLFW_KEY_7), HOTBAR_8("Action slot 8", GLFW_KEY_8), HOTBAR_9("Action slot 9", GLFW_KEY_9), HOTBAR_0("Action slot 10", GLFW_KEY_0),
        PANEL_CHARACTER("Character (alternate)", GLFW_KEY_F1), PANEL_SKILLS("Skills (alternate)", GLFW_KEY_F2),
        PANEL_JOURNAL("Journal (alternate)", GLFW_KEY_F3), PANEL_INVENTORY("Inventory (alternate)", GLFW_KEY_F4), PANEL_MAP("Map (alternate)", GLFW_KEY_F7),
        MENU("Pause / close menu", GLFW_KEY_ESCAPE), ROTATE("Rotate held inventory item", GLFW_KEY_R, 1),
        CONFIRM("Confirm / continue", GLFW_KEY_ENTER, 1), NAV_UP("Menu / console up", GLFW_KEY_UP, 1), NAV_DOWN("Menu / console down", GLFW_KEY_DOWN, 1),
        BACKSPACE("Delete previous character", GLFW_KEY_BACKSPACE, 1), COMPLETE("Console completion", GLFW_KEY_TAB, 1),
        CHOICE_1("Menu choice 1", GLFW_KEY_1, 1), CHOICE_2("Menu choice 2", GLFW_KEY_2, 1), CHOICE_3("Menu choice 3", GLFW_KEY_3, 1),
        CHOICE_4("Menu choice 4", GLFW_KEY_4, 1), CHOICE_5("Menu choice 5", GLFW_KEY_5, 1), CHOICE_6("Menu choice 6", GLFW_KEY_6, 1),
        CHOICE_7("Menu choice 7", GLFW_KEY_7, 1), CHOICE_8("Menu choice 8", GLFW_KEY_8, 1), CHOICE_9("Menu choice 9", GLFW_KEY_9, 1), CHOICE_0("Menu choice 10", GLFW_KEY_0, 1);
        public final String title; public final int defaultKey, context;
        Action(String title, int key) { this(title, key, 0); }
        Action(String title, int key, int context) { this.title = title; defaultKey = key; this.context = context; }
    }
    public final Map<Action, Integer> keys = new java.util.EnumMap<>(Action.class);
    public float master = 1, sfx = 1, music = 0.65f, voice = 1, ambience = 0.8f, ui = 0.8f;
    public float sensitivity = 1, fov = 70, renderScale = 1;
    public int quality = 1, targetFps = 60;
    public boolean fullscreen, vsync = true, autoScale = true, reducedMotion, subtitles = true, invertY, toggleCrouch, toggleSprint;
    public Preferences() { for (Action a : Action.values()) keys.put(a, a.defaultKey); }

    /** Keyboard and mouse chords are stored as a key plus GLFW modifier bits; legacy key-only saves still work. */
    public boolean bind(Action action, int binding) {
        int key = binding & 0xffff, mods = binding >>> 16;
        boolean mouse = key >= astratto.platform.Input.MOUSE_OFFSET && key <= astratto.platform.Input.MOUSE_OFFSET + GLFW_MOUSE_BUTTON_LAST;
        if ((!mouse && (key < GLFW_KEY_SPACE || key > GLFW_KEY_LAST)) || (key == GLFW_KEY_ESCAPE && action != Action.MENU) || (mods & ~15) != 0) return false;
        int previous = keys.get(action);
        for (Action a : Action.values()) if (a != action && a.context == action.context && keys.get(a) == binding) keys.put(a, previous);
        keys.put(action, binding); return true;
    }
    public static boolean modifier(int key) { return key >= GLFW_KEY_LEFT_SHIFT && key <= GLFW_KEY_RIGHT_SUPER; }
    public static int modifiers(astratto.platform.Input in) {
        return (in.keyDown(GLFW_KEY_LEFT_SHIFT) || in.keyDown(GLFW_KEY_RIGHT_SHIFT) ? GLFW_MOD_SHIFT : 0)
                | (in.keyDown(GLFW_KEY_LEFT_CONTROL) || in.keyDown(GLFW_KEY_RIGHT_CONTROL) ? GLFW_MOD_CONTROL : 0)
                | (in.keyDown(GLFW_KEY_LEFT_ALT) || in.keyDown(GLFW_KEY_RIGHT_ALT) ? GLFW_MOD_ALT : 0)
                | (in.keyDown(GLFW_KEY_LEFT_SUPER) || in.keyDown(GLFW_KEY_RIGHT_SUPER) ? GLFW_MOD_SUPER : 0);
    }
    public boolean down(astratto.platform.Input in, Action a) {
        int binding = keys.get(a), mods = binding >>> 16;
        return in.keyDown(binding & 0xffff) && (modifiers(in) & mods) == mods;
    }
    public boolean pressed(astratto.platform.Input in, Action a) {
        if (!down(in, a)) return false;
        int binding = keys.get(a), mods = binding >>> 16;
        if (in.keyPressed(binding & 0xffff)) return true;
        int[] bits = {GLFW_MOD_SHIFT, GLFW_MOD_CONTROL, GLFW_MOD_ALT, GLFW_MOD_SUPER};
        int[] left = {GLFW_KEY_LEFT_SHIFT, GLFW_KEY_LEFT_CONTROL, GLFW_KEY_LEFT_ALT, GLFW_KEY_LEFT_SUPER};
        int[] right = {GLFW_KEY_RIGHT_SHIFT, GLFW_KEY_RIGHT_CONTROL, GLFW_KEY_RIGHT_ALT, GLFW_KEY_RIGHT_SUPER};
        for (int i = 0; i < bits.length; i++) if ((mods & bits[i]) != 0 && (in.keyPressed(left[i]) || in.keyPressed(right[i]))) return true;
        return false;
    }
    public boolean released(astratto.platform.Input in, Action a) {
        int binding = keys.get(a);
        return in.keyReleased(binding & 0xffff);
    }
    public String label(Action action) { return keyLabel(keys.get(action)); }
    public static String keyLabel(int binding) {
        int key = binding & 0xffff, mods = binding >>> 16;
        String name = key >= astratto.platform.Input.MOUSE_OFFSET ? "Mouse " + (key - astratto.platform.Input.MOUSE_OFFSET + 1)
                : key >= GLFW_KEY_F1 && key <= GLFW_KEY_F25 ? "F" + (key - GLFW_KEY_F1 + 1)
                : switch (key) {
                    case GLFW_KEY_SPACE -> "Space"; case GLFW_KEY_TAB -> "Tab"; case GLFW_KEY_ENTER -> "Enter";
                    case GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
                    case GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT -> "Shift";
                    case GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT -> "Alt";
                    case GLFW_KEY_LEFT_SUPER, GLFW_KEY_RIGHT_SUPER -> "Super";
                    case GLFW_KEY_CAPS_LOCK -> "Caps Lock"; case GLFW_KEY_PAGE_UP -> "Page Up"; case GLFW_KEY_PAGE_DOWN -> "Page Down";
                    case GLFW_KEY_UP -> "Up"; case GLFW_KEY_DOWN -> "Down"; case GLFW_KEY_LEFT -> "Left"; case GLFW_KEY_RIGHT -> "Right";
                    default -> key >= 32 && key <= 96 ? String.valueOf((char)key) : "Key " + key;
                };
        return ((mods & GLFW_MOD_CONTROL) != 0 ? "Ctrl+" : "") + ((mods & GLFW_MOD_ALT) != 0 ? "Alt+" : "")
                + ((mods & GLFW_MOD_SHIFT) != 0 ? "Shift+" : "") + ((mods & GLFW_MOD_SUPER) != 0 ? "Super+" : "") + name;
    }

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
