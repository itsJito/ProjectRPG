package astratto.console;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The engine's developer console: one registry of commands and variables that the engine and whatever game runs on it
 * both add to, with the machinery every game would otherwise write again - a line parser (quotes, several commands
 * separated by ';'), aliases, key binds, {@code exec} of scripts from {@code cfg/}, {@code writeconfig}, a scrollback,
 * a prompt with history and Tab completion (of names, and of a command's own arguments when it offers a completer), and
 * near-miss suggestions. It has no notion of cheats or of any other engine's conventions: everything registered is
 * available, under plain names.
 *
 * <p>It costs nothing while closed: no per-frame work at all (a key bound to a command is looked up only when pressed),
 * the scrollback is a fixed ring, and drawing is the host's business (it reads {@link #lines}, {@link #inputText} and
 * {@link #matches} only while the console is shown).
 */
public final class DevConsole {
    /** A console variable: a value with its default and a help line, and what to do when it changes. */
    public static final class Var {
        public final String name, def, help;
        public String value;
        final Consumer<Var> changed;
        Var(String name, String def, String help, Consumer<Var> changed) { this.name = name; this.def = def; this.value = def; this.help = help; this.changed = changed; }
        public float f() { try { return Float.parseFloat(value); } catch (NumberFormatException e) { return 0; } }
        public int i() { return Math.round(f()); }
        public boolean on() { return f() != 0; }
    }

    /** A command: its name, a help line, and what it does with its arguments (the name first). */
    public record Command(String name, String help, Consumer<String[]> run) {}

    /** One line of scrollback. */
    public record Line(String text, int color) {}

    public static final int TEXT = 0xFFE6E6E0, ECHO = 0xFFB8C8D8, WARN = 0xFFFFB050, ERR = 0xFFFF6A5A, GOOD = 0xFF9AE07A, DIM = 0xFF9A9A94;
    private static final int SCROLLBACK = 600, HISTORY = 100;

    private final Map<String, Command> cmds = new TreeMap<>();
    private final Map<String, Var> vars = new TreeMap<>();
    private final Map<String, Function<String, List<String>>> completers = new TreeMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    /** Key name (lower case, as the host names its keys) -> command line. */
    public final Map<String, String> binds = new LinkedHashMap<>();
    private final ArrayDeque<Line> lines = new ArrayDeque<>();
    private final List<String> history = new ArrayList<>();
    private final StringBuilder input = new StringBuilder();
    private int historyAt = -1, scroll, depth;
    public boolean open;
    /** Where scripts are read from and the config written. */
    public Path cfgDir = Path.of("cfg");
    /** Told of every command line run (name, the whole line): a host may log it. */
    public BiConsumer<String, String> onRun;
    /** Told when the console opens or closes (a host may play a sound, pause, release the mouse). */
    public Consumer<Boolean> onToggle;

    public DevConsole(String engineName, String engineVersion, String nativeInfo) {
        builtins(engineName, engineVersion, nativeInfo);
    }

    // ------------------------------------------------------------------ registry
    public Var var(String name, String def, String help) { return var(name, def, help, null); }
    public Var var(String name, String def, String help, Consumer<Var> changed) { Var v = new Var(name, def, help, changed); vars.put(name.toLowerCase(Locale.ROOT), v); return v; }
    public void cmd(String name, String help, Consumer<String[]> run) { cmds.put(name.toLowerCase(Locale.ROOT), new Command(name, help, run)); }
    /** A completer for a command's first argument: given what has been typed of it, the candidates. */
    public void completer(String cmd, Function<String, List<String>> c) { completers.put(cmd.toLowerCase(Locale.ROOT), c); }
    public Var get(String name) { return vars.get(name.toLowerCase(Locale.ROOT)); }
    public float f(String name) { Var v = get(name); return v == null ? 0 : v.f(); }
    public boolean on(String name) { Var v = get(name); return v != null && v.on(); }
    public void set(String name, String value) { Var v = get(name); if (v != null) set(v, value); }
    private void set(Var v, String value) { v.value = value; if (v.changed != null) v.changed.accept(v); }

    public List<String> names() {
        List<String> l = new ArrayList<>(cmds.keySet());
        l.addAll(vars.keySet());
        l.addAll(aliases.keySet());
        Collections.sort(l);
        return l;
    }

    // ------------------------------------------------------------------ the engine's own commands
    private void builtins(String engineName, String engineVersion, String nativeInfo) {
        cmd("help", "help <name>: what a command or variable does.", a -> {
            if (a.length < 2) { print("'commands' lists the commands, 'vars' the variables, 'find <text>' searches both. Tab completes.", TEXT); return; }
            String n = a[1].toLowerCase(Locale.ROOT);
            Command c = cmds.get(n);
            Var v = vars.get(n);
            if (c != null) print(c.name + ": " + c.help, TEXT);
            else if (v != null) print(v.name + " = \"" + v.value + "\" (default \"" + v.def + "\"): " + v.help, TEXT);
            else if (aliases.containsKey(n)) print(n + " is an alias for: " + aliases.get(n), TEXT);
            else print("No such command or variable: " + a[1] + suggestion(n), WARN);
        });
        cmd("commands", "Lists every command.", a -> { for (Command c : cmds.values()) print(String.format("%-22s %s", c.name, c.help), TEXT); print(cmds.size() + " commands", DIM); });
        cmd("vars", "Lists every variable and its value.", a -> { for (Var v : vars.values()) print(String.format("%-22s = %-8s %s", v.name, v.value, v.help), TEXT); print(vars.size() + " variables", DIM); });
        cmd("find", "find <text>: commands and variables whose name or help mention it.", a -> {
            if (a.length < 2) return;
            String t = join(a, 1).toLowerCase(Locale.ROOT);
            for (Command c : cmds.values()) if (c.name.contains(t) || c.help.toLowerCase(Locale.ROOT).contains(t)) print(String.format("%-22s %s", c.name, c.help), TEXT);
            for (Var v : vars.values()) if (v.name.contains(t) || v.help.toLowerCase(Locale.ROOT).contains(t)) print(String.format("%-22s = %-8s %s", v.name, v.value, v.help), ECHO);
        });
        cmd("echo", "echo <text>: prints it.", a -> print(join(a, 1), TEXT));
        cmd("clear", "Clears the scrollback.", a -> { lines.clear(); scroll = 0; });
        cmd("alias", "alias <name> \"<commands>\": a new command made of others. With no arguments, the list.", a -> {
            if (a.length < 2) { for (var e : aliases.entrySet()) print(e.getKey() + " = " + e.getValue(), TEXT); return; }
            if (a.length < 3) { aliases.remove(a[1].toLowerCase(Locale.ROOT)); return; }
            aliases.put(a[1].toLowerCase(Locale.ROOT), join(a, 2));
        });
        cmd("bind", "bind <key> \"<command>\": the key runs the command when pressed. With only a key, what it is bound to.", a -> {
            if (a.length < 2) return;
            String k = a[1].toLowerCase(Locale.ROOT);
            if (a.length < 3) { print(k + " = " + binds.getOrDefault(k, "(nothing)"), TEXT); return; }
            binds.put(k, join(a, 2));
        });
        cmd("unbind", "unbind <key>", a -> { if (a.length > 1) binds.remove(a[1].toLowerCase(Locale.ROOT)); });
        cmd("unbindall", "Clears every bind.", a -> binds.clear());
        cmd("binds", "Lists every bind.", a -> { for (var e : binds.entrySet()) print(e.getKey() + " = " + e.getValue(), TEXT); });
        cmd("exec", "exec <file>: runs a script of console lines from cfg/<file>(.cfg).", a -> {
            if (a.length < 2) return;
            Path p = cfgDir.resolve(a[1].endsWith(".cfg") ? a[1] : a[1] + ".cfg");
            try { for (String l : Files.readAllLines(p, StandardCharsets.UTF_8)) { String t = l.trim(); if (!t.isEmpty() && !t.startsWith("//")) run(t, false); } }
            catch (java.io.IOException e) { print("couldn't exec " + p + ": " + e.getMessage(), ERR); }
        });
        cmd("writeconfig", "Writes the binds, aliases and changed variables to cfg/config.cfg.", a -> writeConfig());
        cmd("toggle", "toggle <var> [values...]: steps a variable through values (default 0 and 1).", a -> {
            if (a.length < 2 || get(a[1]) == null) { print("Usage: toggle <var> [values...]", WARN); return; }
            Var v = get(a[1]);
            String[] vals = a.length > 2 ? java.util.Arrays.copyOfRange(a, 2, a.length) : new String[]{"0", "1"};
            int at = 0;
            for (int i = 0; i < vals.length; i++) if (vals[i].equals(v.value)) { at = (i + 1) % vals.length; break; }
            set(v, vals[at]);
            print(v.name + " = " + v.value, TEXT);
        });
        cmd("increment", "increment <var> <step> [min] [max]: adds a step, kept within bounds.", a -> {
            if (a.length < 3 || get(a[1]) == null) { print("Usage: increment <var> <step> [min] [max]", WARN); return; }
            Var v = get(a[1]);
            float x = v.f() + num(a[2], 0);
            if (a.length > 3) x = Math.max(num(a[3], x), x);
            if (a.length > 4) x = Math.min(num(a[4], x), x);
            set(v, trim(x));
            print(v.name + " = " + v.value, TEXT);
        });
        cmd("version", "The engine build.", a -> { print(engineName + " " + engineVersion, TEXT); if (nativeInfo != null) print(nativeInfo, DIM); });
    }

    // ------------------------------------------------------------------ running
    /** Runs a line (several commands may be separated by ';'). From the prompt it is echoed and remembered. */
    public void run(String line, boolean typed) {
        if (typed) {
            print("] " + line, ECHO);
            if (!line.isBlank() && (history.isEmpty() || !history.get(history.size() - 1).equals(line))) { history.add(line); while (history.size() > HISTORY) history.remove(0); }
            historyAt = -1;
        }
        if (++depth > 32) { print("alias recursion", ERR); depth--; return; }
        try {
            for (String part : split(line)) {
                String[] a = args(part);
                if (a.length == 0) continue;
                String name = a[0].toLowerCase(Locale.ROOT);
                if (aliases.containsKey(name)) { run(aliases.get(name), false); continue; }
                Command c = cmds.get(name);
                Var v = vars.get(name);
                if (c != null) {
                    try { c.run.accept(a); }
                    catch (RuntimeException e) { print(c.name + ": " + e.getMessage(), ERR); }
                    if (onRun != null) onRun.accept(c.name, part.trim());
                } else if (v != null) {
                    if (a.length == 1) { print("\"" + v.name + "\" = \"" + v.value + "\" (default \"" + v.def + "\")", TEXT); print(" - " + v.help, DIM); }
                    else { set(v, join(a, 1)); if (onRun != null) onRun.accept(v.name, part.trim()); }
                } else print("Unknown command \"" + a[0] + "\"" + suggestion(name), WARN);
            }
        } finally { depth--; }
    }

    /** A key was pressed in play: its bind, if any (looked up only now - a closed console does no work). */
    public void key(String name) { String c = binds.get(name); if (c != null) run(c, false); }

    /** The startup scripts, if there are any (cfg/config.cfg, then cfg/autoexec.cfg). */
    public void autoexec() { for (String f : new String[]{"config.cfg", "autoexec.cfg"}) if (Files.isRegularFile(cfgDir.resolve(f))) run("exec " + f, false); }

    private void writeConfig() {
        StringBuilder sb = new StringBuilder("// written by writeconfig\n");
        for (Var v : vars.values()) if (!v.value.equals(v.def)) sb.append(v.name).append(" \"").append(v.value).append("\"\n");
        for (var e : aliases.entrySet()) sb.append("alias ").append(e.getKey()).append(" \"").append(e.getValue()).append("\"\n");
        for (var e : binds.entrySet()) sb.append("bind ").append(e.getKey()).append(" \"").append(e.getValue()).append("\"\n");
        try { Files.createDirectories(cfgDir); Files.writeString(cfgDir.resolve("config.cfg"), sb.toString(), StandardCharsets.UTF_8); print("Wrote " + cfgDir.resolve("config.cfg"), GOOD); }
        catch (java.io.IOException e) { print("couldn't write config: " + e.getMessage(), ERR); }
    }

    public void print(String s, int color) {
        for (String l : s.split("\n")) { lines.addLast(new Line(l, color)); if (lines.size() > SCROLLBACK) lines.removeFirst(); }
        scroll = 0;
    }

    // ------------------------------------------------------------------ the prompt
    public void toggle() { open = !open; if (open) { input.setLength(0); historyAt = -1; } if (onToggle != null) onToggle.accept(open); }
    public void close() { if (open) toggle(); }

    /**
     * The keyboard while open: typed text (the console key never lands in the line), Enter runs, Backspace deletes, Up
     * and Down walk the history, Tab completes, Page Up and Down scroll.
     */
    public void input(String typed, boolean enter, boolean backspace, boolean up, boolean down, boolean tab, int page) {
        for (char c : typed.toCharArray()) if (c != '`' && c != '~' && c >= 32) input.append(c);
        if (backspace && input.length() > 0) input.setLength(input.length() - 1);
        if (up && !history.isEmpty()) { historyAt = historyAt < 0 ? history.size() - 1 : Math.max(0, historyAt - 1); input.setLength(0); input.append(history.get(historyAt)); }
        if (down && historyAt >= 0) { historyAt++; input.setLength(0); if (historyAt < history.size()) input.append(history.get(historyAt)); else historyAt = -1; }
        if (tab) complete();
        if (page != 0) scroll = Math.max(0, Math.min(Math.max(0, lines.size() - 4), scroll + page * 10));
        if (enter) { String l = input.toString(); input.setLength(0); run(l, true); }
    }

    public String inputText() { return input.toString(); }
    public int scroll() { return scroll; }
    /** The scrollback, newest last. */
    public Iterator<Line> newestFirst() { return lines.descendingIterator(); }
    public int lineCount() { return lines.size(); }
    public boolean isVar(String name) { return vars.containsKey(name); }

    /** The names the prompt's first word could become (for the list under the prompt). */
    public List<String> matches() {
        String s = input.toString().trim().toLowerCase(Locale.ROOT);
        List<String> m = new ArrayList<>();
        if (s.isEmpty() || s.contains(" ")) return m;
        for (String n : names()) if (n.startsWith(s)) m.add(n);
        return m;
    }

    private void complete() {
        String s = input.toString();
        int sp = s.indexOf(' ');
        if (sp > 0) {
            // a command's own argument, if it completes them
            var c = completers.get(s.substring(0, sp).toLowerCase(Locale.ROOT));
            if (c == null) return;
            String part = s.substring(sp + 1).trim().toLowerCase(Locale.ROOT);
            List<String> hits = c.apply(part);
            if (hits.size() == 1) { input.setLength(0); input.append(s, 0, sp + 1).append(hits.get(0)).append(' '); }
            else if (!hits.isEmpty()) { input.setLength(0); input.append(s, 0, sp + 1).append(prefix(hits)); print(String.join("  ", hits.subList(0, Math.min(24, hits.size()))), DIM); }
            return;
        }
        List<String> m = matches();
        if (m.size() == 1) { input.setLength(0); input.append(m.get(0)).append(' '); }
        else if (!m.isEmpty()) { input.setLength(0); input.append(prefix(m)); }
    }

    // ------------------------------------------------------------------ helpers (public: hosts use them in their own commands)
    public String suggestion(String name) {
        String best = null; int bd = 3;
        for (String c : names()) { int d = distance(c, name); if (d < bd) { bd = d; best = c; } }
        return best == null ? "" : " - did you mean '" + best + "'?";
    }

    public static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    public static String prefix(List<String> l) {
        String p = l.get(0);
        for (String s : l) while (!s.startsWith(p)) p = p.substring(0, p.length() - 1);
        return p;
    }

    public static String join(String[] a, int from) { return String.join(" ", java.util.Arrays.copyOfRange(a, Math.min(from, a.length), a.length)); }
    public static float num(String s, float def) { try { return Float.parseFloat(s); } catch (NumberFormatException e) { return def; } }
    static String trim(float v) { return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v); }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quote = false;
        for (char c : line.toCharArray()) {
            if (c == '"') quote = !quote;
            if (c == ';' && !quote) { out.add(cur.toString()); cur.setLength(0); } else cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    static String[] args(String part) {
        List<String> a = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quote = false, any = false;
        for (char c : part.trim().toCharArray()) {
            if (c == '"') { quote = !quote; any = true; continue; }
            if (Character.isWhitespace(c) && !quote) { if (any) { a.add(cur.toString()); cur.setLength(0); any = false; } continue; }
            cur.append(c); any = true;
        }
        if (any) a.add(cur.toString());
        return a.toArray(new String[0]);
    }
}
