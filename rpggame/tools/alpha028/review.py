#!/usr/bin/env python3
"""Run reproducible Alpha 0.2.8 inspections with JDK 21 and the installed distribution."""
from pathlib import Path
import argparse
import os
import subprocess

root = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("mode", choices=("profile", "poses", "scenes"))
parser.add_argument("--out", type=Path, default=root / "work/alpha028/review")
parser.add_argument("--only", help="Pose names separated by commas, or one scene name")
options = parser.parse_args()
library = root / "rpggame/build/install/rpggame/lib"
if not library.is_dir():
    raise SystemExit("Run sh gradlew installDist from rpggame first")
options.out.mkdir(parents=True, exist_ok=True)
classes = root / "work/alpha028/review-classes"
classes.mkdir(parents=True, exist_ok=True)
name = {"profile": "Profile028", "poses": "Review028", "scenes": "Scene028"}[options.mode]
classpath = os.pathsep.join((str(classes), str(library / "*")))
subprocess.run(["javac", "--release", "21", "-cp", classpath, "-d", str(classes),
                str(Path(__file__).with_name(name + ".java"))], check=True, cwd=root)
command = ["java", "--enable-native-access=ALL-UNNAMED", "-Xms512m", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseZGC", "-cp", classpath, "rpg." + name]
if options.mode != "profile":
    command.append(str(options.out.resolve()))
    if options.only:
        command.append(options.only)
subprocess.run(command, check=True, cwd=root)
