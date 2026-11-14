import re
P=r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\world\shadowveil\Fitout.java'
s=open(P,encoding='utf-8').read()
def rep(a,b,cnt=1):
    global s
    assert s.count(a)==cnt,(a,s.count(a))
    s=s.replace(a,b)
# helper after reach()
rep('''    /** A bed: its head to a wall, one wide and two long''','''    /**
     * (0.4.6, gamefixes: a church's stair filled the tower's only way through to the nave) how many separate pieces of
     * floor a storey's ways in open onto: a flight, a partition or a piece that leaves every cell reached but cuts one
     * doorway off from another (each half still reached from its own) is no longer taken as leaving the floor whole.
     */
    private int groups(Storey st) {
        int n = 0;
        Set<Long> seen = new HashSet<>();
        for (int[] en : st.entries) {
            if (!st.plan.in(en[0], en[1]) || !walk(st.g[en[0]][en[1]]) || seen.contains(key(en[0], en[1]))) continue;
            n++;
            ArrayDeque<int[]> q = new ArrayDeque<>();
            seen.add(key(en[0], en[1])); q.add(en);
            while (!q.isEmpty()) {
                int[] a = q.poll();
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int u = a[0] + d[0], v = a[1] + d[1];
                    if (st.plan.in(u, v) && walk(st.g[u][v]) && seen.add(key(u, v))) q.add(new int[]{u, v});
                }
            }
        }
        return n;
    }

    /** A bed: its head to a wall, one wide and two long''')
# stair whole()
rep('''        int beforeLo = reach(lo, null).size();
        for (int k = 0; k < rise; k++) lo.g[path[k][0]][path[k][1]] = STAIR;
        int afterLo = reach(lo, null).size();''','''        int beforeLo = reach(lo, null).size(), groupsLo = groups(lo);
        for (int k = 0; k < rise; k++) lo.g[path[k][0]][path[k][1]] = STAIR;
        int afterLo = reach(lo, null).size();
        boolean joined = groups(lo) <= groupsLo;''')
rep('''        return afterLo >= beforeLo - stepsLo && afterUp >= baseUp - voided;''','''        return joined && afterLo >= beforeLo - stepsLo && afterUp >= baseUp - voided;''')
# partition
rep('''        int before = reach(st, null).size();
        int walkLine = 0;''','''        int before = reach(st, null).size(), groupsBefore = groups(st);
        int walkLine = 0;''')
rep('''        boolean whole = reach(st, null).size() >= before - walkLine && !st.entries.isEmpty();''','''        boolean whole = reach(st, null).size() >= before - walkLine && !st.entries.isEmpty() && groups(st) <= groupsBefore;''')
# commit
rep('''        int before = reach(st, null).size();
        byte[] old = new byte[cells.size()];''','''        int before = reach(st, null).size(), groupsBefore = groups(st);
        byte[] old = new byte[cells.size()];''')
rep('''        boolean whole = now.size() >= before - cells.size();
        for (int[] q : useCells)''','''        boolean whole = now.size() >= before - cells.size() && groups(st) <= groupsBefore;
        for (int[] q : useCells)''')
open(P,'w',encoding='utf-8').write(s)
