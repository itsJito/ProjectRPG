# Alpha 0.2.7 review — Shadow

Base: main `7f83b1b`. Runtime navigation replaces the designer graph as the ordinary route planner. The engine still executes short legs with its existing physical steering and local avoidance. Generated regions retain actual supported heights; edits invalidate local chunks. Saved personal routes and obstruction rumors are revalidated before use.

## Native course

The repeatable runner is `rpggame/tools/alpha026/NavCourse.java`. Both runs use deterministic native physics at 60 Hz, with a 45-second deadline. The doorway sends ten agents in each direction through one opening.

| Course | Main arrivals | Final arrivals | Main last arrival | Final last arrival |
|---|---:|---:|---:|---:|
| Dead-end pocket | 1/1 | 1/1 | 13.40 s | 13.40 s |
| Opposing doorway crowd | 20/20 | 20/20 | 17.98 s | 17.80 s |
| Wall added mid-route | 1/1 | 1/1 | 12.30 s | 12.30 s |

All three final courses have zero stuck events, partial paths, and cap hits. The crowd produces one yield. Detailed requests, travel distances, and expansions are in the attached text files. The geometric point-agent lower bounds are 12.77, 10.96, and 11.71 seconds respectively. They ignore body radius, acceleration, and other agents; they are optimistic bounds, not achievable physical optima. The raw grid reference is not a physical optimum either.

An early personal-noise change to short native paths stranded six doorway agents. Moving personal variation to the gameplay planner and limiting yields to brief stalled encounters restored all arrivals. This small course does not prove every crowd arrangement is solved.

## Verification and visual inspection

Verification on Linux x64 with JDK 21:

| Check | Result |
|---|---|
| Full gameplay sweep and distribution | 324 cases, zero failures or errors, one existing disabled profiling probe |
| Affected gameplay recheck | 57 passed, zero failures or errors |
| Closing movement, monster, and routine recheck | 25 passed, zero failures or errors |
| Native engine checks | 121 passed |
| Java/GL engine checks | 29 passed |
| Focused rendered acceptance: creator, leveling, invariants | 20/20 passed |
| Final distribution build | Successful |

The full sweep is followed by the affected and closing checks for the final refinements. Initial failures were resolved rather than disabled. Surface generation and edit invalidation now use native navigation flags, including passable foliage and water depth. Airborne actors project to supported ground. Native HOLD cancels pending searches, and ladder exits clear the landing before releasing the rungs. New monster regressions require an actual grounded firing perch with verified travel knowledge, and height-aware pursuit of an elevated opponent.

The focused acceptance flow renders every game frame at 800x450 with the native software renderer; `Leveling027.java` reuses the existing acceptance code. Its creator checklist includes the existing eye-color row, and its level check waits for the specific queued award and inspects the current level-banner fragment. These results cover that selected flow, not every acceptance section.

`rpggame/tools/alpha027/Review027.java` captures the title, hedge, stairs/ladder course, repair pose, interruption, and witness chat through the native renderer. Repair contact sheets sample frames 1, 4, 7 through 208; all 70 samples were inspected in six pages under `alpha027/repair`. Reference title and repair captures from main are under `alpha027/reference`. The checked-in close capture shows the existing Hammer and a braced working pose. The title subtitle remains subordinate to the game name. Software-renderer screenshots establish appearance here, not hardware performance.

## Limits

Native work budgets remain bounded (adaptive capped retries up to 48,000 expansions and finite scratch storage). Obstruction penalties expire and personal books retain at most 32 routes. The graph is generated lazily and synchronously; this is not an asynchronous streaming navigation service. High-ground selection checks the best three nearby visible candidates for reachability. Designer hints remain available for landmarks and compatibility. No NPC XP progression is added.
