# Контактный лист для проверки: кадры спрайтов поверх условного аватара на тёмном фоне.
import json, sys
from PIL import Image, ImageDraw
base = 'art/build/raw/'
names = sys.argv[1].split(',')
frames = [int(x) for x in sys.argv[2].split(',')] if len(sys.argv) > 2 else [0]
m = json.load(open('art/assets/decor/manifest.json'))
cell = 260
sheet = Image.new('RGBA', (cell * len(frames), cell * len(names)), (14, 14, 17, 255))
for r, n in enumerate(names):
    spec = m[n]; img = Image.open(base + n + '.png').convert('RGBA')
    for c, fr in enumerate(frames):
        x0, y0 = c * cell + (cell - spec['w']) // 2, r * cell + (cell - spec['h']) // 2
        if spec['w'] == 240:
            d = ImageDraw.Draw(sheet); cx, cy = x0 + 120, y0 + 120
            d.ellipse([cx - 86, cy - 86, cx + 86, cy + 86], fill=(255, 138, 61))
            d.ellipse([cx - 78, cy - 78, cx + 78, cy + 78], fill=(120, 160, 140))
        part = img.crop((fr * spec['w'], 0, (fr + 1) * spec['w'], spec['h']))
        sheet.alpha_composite(part, (x0, y0))
sheet.save(sys.argv[3] if len(sys.argv) > 3 else '/tmp/preview.png')
