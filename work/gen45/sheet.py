"""Contact sheets: python sheet.py DIR PREFIX [cols] [width] -> DIR/sheet-PREFIX.png (images whose name starts with PREFIX)."""
import sys, os
from PIL import Image, ImageDraw
d, prefix = sys.argv[1], sys.argv[2]
cols = int(sys.argv[3]) if len(sys.argv) > 3 else 4
W = int(sys.argv[4]) if len(sys.argv) > 4 else 480
files = sorted(f for f in os.listdir(d) if f.startswith(prefix) and f.endswith('.png') and not f.startswith('sheet'))
ims = []
for f in files:
    im = Image.open(os.path.join(d, f)).convert('RGB')
    h = int(im.height * W / im.width)
    ims.append((f, im.resize((W, h))))
if not ims: sys.exit('none')
H = max(i.height for _, i in ims)
rows = (len(ims) + cols - 1) // cols
out = Image.new('RGB', (cols * W, rows * (H + 16)), (20, 20, 20))
dr = ImageDraw.Draw(out)
for k, (f, im) in enumerate(ims):
    x, y = (k % cols) * W, (k // cols) * (H + 16)
    out.paste(im, (x, y + 16)); dr.text((x + 4, y + 2), f, fill=(255, 255, 0))
p = os.path.join(d, 'sheet-' + prefix.strip('-') + '.png')
out.save(p); print(p)
