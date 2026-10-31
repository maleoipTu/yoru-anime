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
for (const file of ['avatar.mjs', 'effects.mjs', 'frames.mjs']) if (fs.existsSync(path.join(here, file))) sets.push(...(await import('./' + file)).default);
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
  await page.setViewportSize({ width: Math.min(a.w * a.frames, 16000), height: a.h });
  await page.setContent(`<html><body style="margin:0;background:transparent"><div id="s" style="display:flex;width:${a.w * a.frames}px">${frames.join('')}</div></body></html>`);
  await page.locator('#s').screenshot({ path: path.join(raw, a.name + '.png'), omitBackground: true });
  manifest[a.name] = { w: a.w, h: a.h, frames: a.frames, fps: a.fps || 0 };
}
await browser.close();
const prev = fs.existsSync(path.join(out, 'manifest.json')) ? JSON.parse(fs.readFileSync(path.join(out, 'manifest.json'))) : {};
fs.writeFileSync(path.join(out, 'manifest.json'), JSON.stringify({ ...prev, ...manifest }, null, 1));
console.log('rendered', Object.keys(manifest).join(', '));
