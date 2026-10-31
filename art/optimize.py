# Пережимает листы из art/build/raw в art/assets/decor без потерь (PNG с альфой нужен и Android, и Windows).
import glob, os
from PIL import Image
here = os.path.dirname(os.path.abspath(__file__))
for p in glob.glob(os.path.join(here, 'build', 'raw', '*.png')):
    Image.open(p).save(os.path.join(here, 'assets', 'decor', os.path.basename(p)), optimize=True)
print('ok')
