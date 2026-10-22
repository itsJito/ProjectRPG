"""Random search over Sydney's balance and stepping numbers, scored by the lab's push sweep (the sum of the pushes,
from four sides, after which the body is back standing without having fallen).

    python tools/sydney_tune.py [trials] [workers]

Prints the best settings found as a LAB_CFG string.
"""
import os
import random
import re
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

LAB = os.path.join(os.path.dirname(__file__), "..", "native", "bin", "windows-x64", "astratto_sydney_lab.exe")
SPACE = {
    "stepTime": (0.18, 0.36), "stepOvershoot": (0.0, 0.15), "stepThreshold": (0.5, 2.0), "stepWidth": (0.0, 0.1),
    "shiftTime": (0.0, 0.2), "balanceGain": (0.8, 3.0), "stanceGain": (0.8, 3.0), "standPosture": (0.5, 2.5),
    "stepReach": (0.5, 0.75), "stepLift": (0.08, 0.18), "freqHip": (2.0, 5.0), "freqSpine": (2.0, 5.0),
    "rootMode": (0, 0), "hipStrategy": (0.0, 1.5), "spineStiff": (0.5, 4.0), "hipRollKeep": (0.0, 1.5), "pelvisGain": (0.5, 3.0), "zetaSpine": (0.6, 1.5),
}
DISCRETE = {"rootMode"}


def score(cfg):
    total = 0
    for at in ("pelvis", "neck"):
        env = dict(os.environ, LAB_CFG=",".join("%s=%.4f" % kv for kv in cfg.items()), LAB_J0="20", LAB_J1="90", LAB_JS="10", LAB_AT=at)
        out = subprocess.run([LAB, "sweep"], env=env, capture_output=True, text=True).stdout
        m = re.search(r"score (\d+)", out)
        total += (int(m.group(1)) if m else 0) * (2 if at == "neck" else 1)
    return total


def main():
    trials = int(sys.argv[1]) if len(sys.argv) > 1 else 40
    workers = int(sys.argv[2]) if len(sys.argv) > 2 else 4
    rng = random.Random(7)
    base = {}
    best = (score(base), base)
    print("baseline", best[0], flush=True)
    for rnd in range(trials // workers):
        cands = []
        for _ in range(workers):
            c = dict(best[1])
            for k in rng.sample(list(SPACE), 3):
                lo, hi = SPACE[k]
                c[k] = float(rng.randint(int(lo), int(hi))) if k in DISCRETE else rng.uniform(lo, hi)
            cands.append(c)
        with ThreadPoolExecutor(workers) as ex:
            results = list(ex.map(score, cands))
        for s, c in zip(results, cands):
            if s > best[0]:
                best = (s, c)
                print("better", s, ",".join("%s=%.3f" % kv for kv in sorted(c.items())), flush=True)
    print("best", best[0], ",".join("%s=%.3f" % kv for kv in sorted(best[1].items())))


main()
