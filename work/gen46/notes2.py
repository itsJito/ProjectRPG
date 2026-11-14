import re
add_bugs = """- AE-04624 - Fixed bug where people waiting for a blocked route turned slowly round and round on the spot
- AE-04625 - Fixed bug where a craftsman short of supplies paced back and forth across the workshop, turning on the spot
- AE-04626 - Fixed bug where people stood stuck for several seconds at a single step up or down on their way
- AE-04627 - Fixed bug where a fish turned back and forth against a wall instead of swimming away from it"""
for f in ["gamepatches.md","patchnotes.md"]:
    s=open(f,encoding="utf-8").read()
    a="- AE-04623 - Fixed bug where a guard stood all day trying to reach a patrol point with no way to it"
    assert s.count(a)==1 and "AE-04624" not in s
    s=s.replace(a,a+"\n"+add_bugs,1)
    open(f,"w",encoding="utf-8").write(s)
s=open("changelog.md",encoding="utf-8").read()
i=s.index("## Pass 37"); j=s.index("### Changes",i)
a="""- Spinning (second round, probe yaw history at each spin): `goTo`'s pause after a blocked route aimed its look-about at the heading of the moment (`yaw + sin`), so the body kept turning for the whole pause; it is now taken from the heading the pause began with (`Npc.pauseYaw`). A worker whose step of the trade has just failed holds where they stand `ai.tendHold` 3 s (`Npc.jobFailAt`) before going about the workplace, instead of walking to the next tidying spot and back. Fish turned about only when not already turning. Probe: 12 people with trouble -> 2 (both brief single events).
- Step holds: a stalled body takes the step to its next waypoint after `ai.nudgeAfter` 0.8 s (was the 6 s stall), and where the waypoint is further, the floor one cell on when it is a level up or down with headroom (`Brain.stepToward`); `ai.nudgeRest` 3 s. Native `ai_service`: an agent that has made no headway for 0.6 s hops any rise within its stride (was stair treads only).
"""
s=s[:j]+a+"\n"+s[j:] if not s[:j].endswith("\n\n") else s[:j-1]+a+"\n"+s[j:]
s=s.replace("White ring over the guard","White a ring round the blade's root, Bronze a broad leaf blade",1)
open("changelog.md","w",encoding="utf-8").write(s)
