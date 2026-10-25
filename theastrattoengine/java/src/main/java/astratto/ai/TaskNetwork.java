package astratto.ai;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** Ordered HTN methods decompose a compound task into a resumable behavior tree. One instance per agent. */
public final class TaskNetwork<C> {
    public record Method<C>(String name, Predicate<C> applicable, Function<C, Behavior.Node<C>> decompose) {}
    private final List<Method<C>> methods;
    private Method<C> method;
    private Behavior.Node<C> execution;
    public TaskNetwork(List<Method<C>> methods) { this.methods = List.copyOf(methods); }
    public String active() { return method == null ? "none" : method.name; }
    public Behavior.Status tick(C context) {
        if (method != null && !method.applicable.test(context)) cancel();
        if (execution == null) for (Method<C> candidate : methods) if (candidate.applicable.test(context)) {
            method = candidate; execution = candidate.decompose.apply(context); break;
        }
        if (execution == null) return Behavior.Status.FAILURE;
        Behavior.Status result = execution.tick(context);
        if (result != Behavior.Status.RUNNING) cancel();
        return result;
    }
    public void cancel() { if (execution != null) execution.reset(); execution = null; method = null; }
}
