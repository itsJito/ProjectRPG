package astratto.platform;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

/** GLFW window + OpenGL 4.5 core context (3.3 core as a fallback for the legacy renderer). */
public final class Window {
    public long handle;
    public int width, height;
    public final Input input = new Input();
    private boolean cursorLocked;
    /** True when the context is OpenGL 4.5 core: the deferred hybrid-GI renderer can run. */
    public boolean gl45;
    public String glInfo = "";
    /** Shown when made (false: an off-screen window for automated runs - it still has a full GL context). */
    public boolean show = true;
    /** The monitor's refresh rate (Hz), and whether the window fills the monitor (borderless). */
    public int refreshRate = 60;
    public boolean fullscreen;
    private int winX, winY, winW, winH;

    private static boolean env(String name) { return System.getenv("ASTRATTO_" + name) != null || System.getenv("ABS_" + name) != null; }

    public void create(String title, int w, int h) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 0);
        GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vm != null) { w = Math.min(w, vm.width() - 80); h = Math.min(h, vm.height() - 100); refreshRate = Math.max(30, vm.refreshRate()); }
        // The hybrid GI renderer needs OpenGL 4.5 (compute shaders, direct state access). If the driver cannot give
        // one, fall back to a 3.3 context: the game then runs on the legacy forward renderer.
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5);
        boolean forceLegacy = env("LEGACY_GL");
        GLFWErrorCallback.createPrint(new java.io.PrintStream(java.io.OutputStream.nullOutputStream())).set();
        handle = forceLegacy ? NULL : glfwCreateWindow(w, h, title, NULL, NULL);
        GLFWErrorCallback.createPrint(System.err).set();
        gl45 = handle != NULL;
        if (handle == NULL) {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            handle = glfwCreateWindow(w, h, title, NULL, NULL);
            if (handle != NULL) System.err.println("[Boot] OpenGL 4.5 is not available" + (forceLegacy ? " (LEGACY_GL)" : "") + ": using the legacy forward renderer.");
        }
        if (handle == NULL) throw new IllegalStateException("Window creation failed (OpenGL 3.3 or newer required)");
        width = w; height = h;
        if (vm != null) glfwSetWindowPos(handle, (vm.width() - w) / 2, (vm.height() - h) / 2);

        glfwSetKeyCallback(handle, (win, key, sc, action, mods) -> input.onKey(key, action));
        glfwSetMouseButtonCallback(handle, (win, b, action, mods) -> { input.clickMods = action == GLFW_PRESS ? mods : input.clickMods; input.onMouseButton(b, action); });
        glfwSetCursorPosCallback(handle, (win, x, y) -> input.onCursor(x, y));
        glfwSetScrollCallback(handle, (win, sx, sy) -> input.onScroll(sy));
        glfwSetCharCallback(handle, (win, cp) -> input.onChar(cp));
        glfwSetFramebufferSizeCallback(handle, (win, fw, fh) -> { width = fw; height = fh; });

        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        glInfo = glGetString(GL_RENDERER) + "  -  OpenGL " + glGetString(GL_VERSION);
        System.out.println("[Boot] " + glInfo);
        applySwapInterval();
        int[] fw = new int[1], fh = new int[1];
        glfwGetFramebufferSize(handle, fw, fh);
        width = fw[0]; height = fh[0];
        if (show) glfwShowWindow(handle);
        glClearColor(0.05f, 0.05f, 0.06f, 1);
    }

    /**
     * Fills the primary monitor at its own mode (or goes back to the window as it was). A window covering the whole
     * monitor at its refresh rate lets the driver present without the desktop compositor in between: steadier frames.
     */
    public void setFullscreen(boolean on) {
        if (on == fullscreen || handle == NULL) return;
        long mon = glfwGetPrimaryMonitor();
        GLFWVidMode vm = glfwGetVideoMode(mon);
        if (vm == null) return;
        if (on) {
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            glfwGetWindowPos(handle, x, y); glfwGetWindowSize(handle, w, h);
            winX = x[0]; winY = y[0]; winW = w[0]; winH = h[0];
            glfwSetWindowMonitor(handle, mon, 0, 0, vm.width(), vm.height(), vm.refreshRate());
        } else glfwSetWindowMonitor(handle, NULL, winX, winY, winW, winH, GLFW_DONT_CARE);
        fullscreen = on;
        applySwapInterval();
        int[] fw = new int[1], fh = new int[1];
        glfwGetFramebufferSize(handle, fw, fh);
        width = fw[0]; height = fh[0];
    }

    /**
     * Vsync: adaptive where the driver offers it (EXT_swap_control_tear) - in step with the monitor while a frame is on
     * time, shown at once when one is late. Plain vsync would wait for the next refresh after a late frame: on a 164 Hz
     * monitor a 7 ms frame would be held to 82 frames a second. ASTRATTO_NOVSYNC turns it off.
     */
    private void applySwapInterval() {
        if (env("NOVSYNC")) { glfwSwapInterval(0); return; }
        boolean tear = glfwExtensionSupported("WGL_EXT_swap_control_tear") || glfwExtensionSupported("GLX_EXT_swap_control_tear");
        glfwSwapInterval(tear ? -1 : 1);
        adaptiveVsync = tear;
    }

    /** Whether vsync is adaptive (a late frame is shown at once instead of waiting a whole refresh). */
    public boolean adaptiveVsync;

    public void setCursorLocked(boolean locked) {
        if (locked == cursorLocked) return;
        cursorLocked = locked;
        glfwSetInputMode(handle, GLFW_CURSOR, locked ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_HIDDEN); // the UI draws a pixel-art cursor
        if (locked && glfwRawMouseMotionSupported()) glfwSetInputMode(handle, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        input.resetMouseDelta();
    }

    public boolean cursorLocked() { return cursorLocked; }
    public boolean shouldClose() { return glfwWindowShouldClose(handle); }
    public void requestClose() { glfwSetWindowShouldClose(handle, true); }
    /** Takes back a close request (a game going back to its title screen rather than out). */
    public void cancelClose() { glfwSetWindowShouldClose(handle, false); }
    public void swap() { glfwSwapBuffers(handle); }
    public void poll() { glfwPollEvents(); }
    public void setTitle(String t) { glfwSetWindowTitle(handle, t); }

    public void destroy() {
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
}
