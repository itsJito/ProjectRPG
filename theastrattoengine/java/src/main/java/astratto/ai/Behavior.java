package astratto.ai;

import java.util.List;
import java.util.function.Predicate;

/** Small, allocation-free-at-tick control nodes for game-owned tasks driving native agents. */
public final class Behavior {
    private Behavior() {}
    public enum Status { SUCCESS, FAILURE, RUNNING }
    @FunctionalInterface public interface Node<C> {
        Status tick(C context);
        default void reset() {}
    }
    public static <C> Node<C> condition(Predicate<C> condition) { return c -> condition.test(c) ? Status.SUCCESS : Status.FAILURE; }
    /** A memory sequence resumes its running child; completed actions are never repeated on the next frame. */
    public static <C> Node<C> sequence(List<Node<C>> children) {
        List<Node<C>> nodes = List.copyOf(children);
        return new Node<>() {
            private int cursor;
            @Override public Status tick(C context) {
                while (cursor < nodes.size()) {
                    Status result = nodes.get(cursor).tick(context);
                    if (result == Status.RUNNING) return result;
                    if (result == Status.FAILURE) { reset(); return result; }
                    cursor++;
                }
                reset(); return Status.SUCCESS;
            }
            @Override public void reset() { cursor = 0; for (Node<C> node : nodes) node.reset(); }
        };
    }
    /** Re-evaluates priority conditions so urgent needs can preempt a lower branch. */
    public static <C> Node<C> selector(List<Node<C>> children) {
        List<Node<C>> nodes = List.copyOf(children);
        return new Node<>() {
            private int running = -1;
            @Override public Status tick(C context) {
                for (int i=0;i<nodes.size();i++) {
                    Status result = nodes.get(i).tick(context);
                    if (result == Status.FAILURE) continue;
                    if (running != -1 && running != i) nodes.get(running).reset();
                    running = result == Status.RUNNING ? i : -1; return result;
                }
                reset(); return Status.FAILURE;
            }
            @Override public void reset() { for (Node<C> node : nodes) node.reset(); running = -1; }
        };
    }
}
