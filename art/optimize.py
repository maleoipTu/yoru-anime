# Пережимает листы из art/build/raw в art/assets/decor (PNG с альфой нужен и Android, и Windows).
# Почти без потерь: альфа округляется до чётной, цвет — с шагом 4 (у полупрозрачных пикселей — 8).
# После наложения ошибка не больше 1–2 уровней яркости из 255 — глазу не видна, а мягкие свечения
# сжимаются на четверть лучше. Полностью прозрачные пиксели обнуляются.
import glob, os
import numpy as np
from PIL import Image
here = os.path.dirname(os.path.abspath(__file__))
for p in glob.glob(os.path.join(here, 'build', 'raw', '*.png')):
    a = np.asarray(Image.open(p).convert('RGBA')).astype(np.int32)
    clear = a[..., 3] == 0
    step = np.where(a[..., 3:4] < 128, 8, 4)
    a[..., :3] = np.minimum(255, a[..., :3] // step * step + step // 2)
    a[..., 3] = np.where(a[..., 3] >= 254, 255, a[..., 3] // 2 * 2)
    a[clear] = 0
    Image.fromarray(a.astype(np.uint8), 'RGBA').save(os.path.join(here, 'assets', 'decor', os.path.basename(p)), optimize=True)
print('ok')
