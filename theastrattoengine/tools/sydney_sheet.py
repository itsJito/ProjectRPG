"""Contact sheet of a Sydney lab run: the body drawn from the side and from the front at every Nth frame.

    python tools/sydney_sheet.py dump.txt out.png [--every 4] [--cols 10] [--from 0] [--to 99]

Each dump line is: time state activity, then the fifteen API points (x y z). The figure is drawn as capsules between
the points, in a fixed frame around where it started, with the floor line and the time and state under each cell.
"""
import sys
from PIL import Image, ImageDraw

STATES = ["STAND", "STEP", "FALL", "DOWN", "GETUP", "DEAD"]
BONES = [(2, 1, 9), (1, 0, 7), (3, 4, 4), (4, 5, 3), (6, 7, 4), (7, 8, 3), (9, 10, 5), (10, 11, 4), (12, 13, 5), (13, 14, 4), (3, 6, 4), (9, 12, 5)]
LEFT = {3, 4, 5, 9, 10, 11}


def main():
    args = sys.argv[1:]
    src, out = args[0], args[1]
    opt = {"--every": 4, "--cols": 10, "--from": 0.0, "--to": 1e9, "--size": 150}
    i = 2
    while i < len(args):
        opt[args[i]] = float(args[i + 1]) if "." in args[i + 1] else int(args[i + 1])
        i += 2
    frames = []
    for line in open(src):
        v = line.split()
        if len(v) < 48:
            continue
        t = float(v[0])
        if t < opt["--from"] or t > opt["--to"]:
            continue
        pts = [(float(v[3 + k * 3]), float(v[4 + k * 3]), float(v[5 + k * 3])) for k in range(15)]
        frames.append((t, int(v[1]), int(v[2]), pts))
    frames = frames[:: int(opt["--every"])]
    if not frames:
        print("no frames")
        return
    floor = min(p[1] for f in frames for p in f[3])
    floor = min(floor, frames[0][3][11][1] - 0.08)
    cx = sum(p[0] for p in frames[0][3]) / 15
    cz = sum(p[2] for p in frames[0][3]) / 15
    size = int(opt["--size"])
    cols = int(opt["--cols"])
    rows = (len(frames) + cols - 1) // cols
    cell_w, cell_h = size * 2, size + 18
    img = Image.new("RGB", (cell_w * cols, cell_h * rows), (246, 244, 238))
    d = ImageDraw.Draw(img)
    scale = size / 2.6  # 2.6 m tall cell

    for n, (t, st, act, pts) in enumerate(frames):
        ox, oy = (n % cols) * cell_w, (n // cols) * cell_h
        for view in range(2):
            vx = ox + view * size
            # side view: z across (forward is -z: drawn to the left), y up; front view: x across
            def proj(p):
                h = (p[2] - cz) if view == 0 else (p[0] - cx)
                return (vx + size / 2 + h * scale, oy + size - 8 - (p[1] - floor) * scale)
            d.line([(vx + 4, oy + size - 8), (vx + size - 4, oy + size - 8)], fill=(120, 110, 100), width=2)
            order = sorted(BONES, key=lambda b: (pts[b[0]][0] if view == 0 else -pts[b[0]][2]))
            for a, b, w in order:
                col = (60, 90, 160) if a in LEFT else (170, 70, 60)
                if a in (2, 1, 3) and b in (1, 0, 6):
                    col = (70, 70, 70)
                d.line([proj(pts[a]), proj(pts[b])], fill=col, width=w)
            hx, hy = proj(pts[0])
            d.ellipse([hx - 7, hy - 7, hx + 7, hy + 7], fill=(70, 70, 70))
            if view == 1:
                d.line([(vx, oy), (vx, oy + size)], fill=(220, 215, 205))
        d.text((ox + 4, oy + size + 2), "%.2fs %s%s" % (t, STATES[st] if 0 <= st < 6 else st, " a%d" % act if act else ""), fill=(40, 40, 40))
    img.save(out)
    print("wrote", out, len(frames), "frames")


main()
