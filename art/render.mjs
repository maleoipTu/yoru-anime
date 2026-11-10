// Рендер украшений в спрайт-листы: node art/render.mjs → art/build/decor/*.png и manifest.json.
// Каждый лист — горизонтальная лента кадров одинакового размера с прозрачным фоном.
import { createRequire } from 'node:module';
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_PATH || 'playwright');
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.join(here, 'assets', 'decor'), raw = path.join(here, 'build', 'raw');
fs.mkdirSync(out, { recursive: true }); fs.mkdirSync(raw, { recursive: true });
const sets = [];
for (const file of ['avatar.mjs', 'effects.mjs', 'frames.mjs', 'badges.mjs']) if (fs.existsSync(path.join(here, file))) sets.push(...(await import('./' + file)).default);
const only = process.argv[2];
const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 1 });
const manifest = {};
for (const a of sets) {
  if (only && !a.name.startsWith(only)) { continue; }
  const frames = [];
  for (let i = 0; i < a.frames; i++) {
    const t = a.frames > 1 ? i / a.frames : 0;
    const svg = a.draw(t, i).replace(/id="([^"]+)"/g, `id="$1_${i}"`).replace(/url\(#([^)]+)\)/g, `url(#$1_${i})`).replace(/href="#([^"]+)"/g, `href="#$1_${i}"`);
    frames.push(`<div style="width:${a.w}px;height:${a.h}px;flex:none">${svg}</div>`);
  }
  // Длинные анимации укладываются сеткой (cols кадров в ряд): лента шире ~8–16 тыс. px не помещается в текстуру на телефонах.
  const cols = a.cols || a.frames, rows = Math.ceil(a.frames / cols);
  await page.setViewportSize({ width: Math.min(a.w * cols, 16000), height: a.h * rows });
  await page.setContent(`<html><body style="margin:0;background:transparent"><div id="s" style="display:flex;flex-wrap:wrap;width:${a.w * cols}px">${frames.join('')}</div></body></html>`);
  await page.locator('#s').screenshot({ path: path.join(raw, a.name + '.png'), omitBackground: true });
  manifest[a.name] = { w: a.w, h: a.h, frames: a.frames, fps: a.fps || 0, ...(a.cols ? { cols: a.cols } : {}) };
}
await browser.close();
const prev = fs.existsSync(path.join(out, 'manifest.json')) ? JSON.parse(fs.readFileSync(path.join(out, 'manifest.json'))) : {};
fs.writeFileSync(path.join(out, 'manifest.json'), JSON.stringify({ ...prev, ...manifest }, null, 1));
console.log('rendered', Object.keys(manifest).join(', '));
