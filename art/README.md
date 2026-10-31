# Рисунки украшений

Украшения аватара, частицы эффектов и детали рамок профиля рисуются в SVG кодом (`avatar.mjs`, `effects.mjs`, `frames.mjs`)
и рендерятся в спрайт-листы PNG — горизонтальные ленты кадров с прозрачным фоном:

```
node art/render.mjs        # всё (или: node art/render.mjs pf_ — только имена с этим началом)
python3 art/optimize.py    # пережать в art/assets/decor
```

`art/assets/decor` подключается в Android (assets) и Windows (Assets/decor). `manifest.json` хранит размер кадра, число кадров и частоту.
