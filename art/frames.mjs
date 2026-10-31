// Детали рамок профиля: угловые рисунки привязаны к левому верхнему углу листа (клиент отражает их для других углов),
// пламя — анимация по кадрам (основание внизу по центру), луна и облако — отдельные спрайты.
import { TAU, f, shadow, glow, blur, lin, rad, sparkle, star5, svg } from './lib.mjs';

const cobweb = () => {
  const S = 230, spokes = 7, rings = 6, size = 214, parts = [];
  const pt = (a, r) => [f(Math.cos(a) * r), f(Math.sin(a) * r)];
  for (let k = 0; k < spokes; k++) { const a = Math.PI / 2 * k / (spokes - 1); const [x, y] = pt(a, size); parts.push(`<path d="M0 0 L${x} ${y}" />`); }
  for (let r = 1; r <= rings; r++) {
    const rr = size * r / (rings + .6); let d = '';
    for (let k = 0; k < spokes; k++) {
      const a = Math.PI / 2 * k / (spokes - 1), [x, y] = pt(a, rr);
      if (k === 0) d += `M${x} ${y}`; else { const pa = Math.PI / 2 * (k - .5) / (spokes - 1), [cx, cy] = pt(pa, rr * .84); d += ` Q${cx} ${cy} ${x} ${y}`; }
    }
    parts.push(`<path d="${d}"/>`);
  }
  const drops = [[60, 22, 3.2], [112, 66, 2.6], [40, 96, 3], [150, 40, 2.4], [88, 140, 2.8], [24, 168, 2.2], [176, 104, 2.2]];
  return svg(S, S, `<defs>${lin('silk', [[0, '#FFFFFF', .95], [1, '#C9C9E0', .55]], 0, 0, 1, 1)}${rad('drop', [[0, '#FFFFFF'], [.4, '#DDEBFF', .9], [1, '#7FA8E0', .6]], .35, .3, .8)}${glow('g', 1.2)}</defs>
    <g fill="none" stroke="url(#silk)" stroke-width="1.5" stroke-linecap="round" filter="url(#g)">${parts.join('')}</g>
    ${drops.map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r}" fill="url(#drop)"/><circle cx="${x - r * .35}" cy="${y - r * .35}" r="${f(r * .3)}" fill="#FFF"/>`).join('')}`);
};

const spider = (t, i) => {
  const W = 56, H = 64, x = W / 2, y = 34, wig = Math.sin(t * TAU) * 6;
  const legs = [-1, 1].flatMap(side => [0, 1, 2, 3].map(k => {
    const a = (-55 + k * 32 + (k % 2 ? wig : -wig)) * Math.PI / 180, l = 18 + (k === 1 || k === 2 ? 4 : 0);
    const kx = x + side * Math.cos(a) * l * .55, ky = y + Math.sin(a) * l * .55 - 4, ex = x + side * Math.cos(a) * l, ey = y + Math.sin(a) * l + 6;
    return `<path d="M${x} ${y} L${f(kx)} ${f(ky)} L${f(ex)} ${f(ey)}" />`;
  }));
  return svg(W, H, `<defs>${rad('body', [[0, '#5A4A6A'], [1, '#141018']], .4, .3, .8)}${shadow('sh', 2, 1.6, .5)}</defs>
    <g filter="url(#sh)"><g fill="none" stroke="#120E16" stroke-width="2" stroke-linejoin="round" stroke-linecap="round">${legs.join('')}</g>
    <ellipse cx="${x}" cy="${y + 6}" rx="10" ry="12" fill="url(#body)"/><circle cx="${x}" cy="${y - 7}" r="6.5" fill="url(#body)"/>
    <path d="M${x - 4} ${y + 2} q4 5 8 0" stroke="#FF8A1F" stroke-width="1.6" fill="none"/>
    <circle cx="${x - 2.4}" cy="${y - 8}" r="1.7" fill="#FFFFFF"/><circle cx="${x + 2.4}" cy="${y - 8}" r="1.7" fill="#FFFFFF"/>
    <circle cx="${x - 2.4}" cy="${y - 7.6}" r=".8" fill="#000"/><circle cx="${x + 2.4}" cy="${y - 7.6}" r=".8" fill="#000"/></g>`);
};

const vines = () => {
  const S = 190;
  const leaf = (x, y, r, s = 1, c = 'url(#leaf)') => `<path d="M0 0 C6 -8 18 -9 26 0 C18 9 6 8 0 0Z" transform="translate(${x} ${y}) rotate(${r}) scale(${s})" fill="${c}" stroke="#2E6B2C" stroke-width="1"/><path d="M2 0 L22 0" transform="translate(${x} ${y}) rotate(${r}) scale(${s})" stroke="#2E6B2C" stroke-opacity=".6" stroke-width=".9"/>`;
  const flower = (x, y, s, col) => `<g transform="translate(${x} ${y}) scale(${s})">${[0, 72, 144, 216, 288].map(r => `<ellipse cx="0" cy="-6" rx="4.6" ry="6.5" transform="rotate(${r})" fill="${col}" stroke="#FFFFFF" stroke-opacity=".6" stroke-width=".8"/>`).join('')}<circle r="3.2" fill="#FFE07A" stroke="#D99A1F" stroke-width=".8"/></g>`;
  return svg(S, S, `<defs>${lin('leaf', [[0, '#A8EB8F'], [1, '#3E8E3A']])}${lin('leaf2', [[0, '#7DD07A'], [1, '#2C6E2A']])}${lin('stem', [[0, '#5FA34A'], [1, '#2E5E22']])}${shadow('sh', 2, 2, .45)}</defs>
    <g filter="url(#sh)">
      <path d="M6 186 C10 120 14 70 44 40 C74 12 120 8 186 6" fill="none" stroke="url(#stem)" stroke-width="5" stroke-linecap="round"/>
      <path d="M14 120 C34 104 40 86 34 64 M80 18 C92 36 110 40 128 30" fill="none" stroke="url(#stem)" stroke-width="3" stroke-linecap="round"/>
      <path d="M40 46 c12 2 18 12 12 20 c-6 6 -14 -2 -8 -6" fill="none" stroke="#5FA34A" stroke-width="2" stroke-linecap="round"/>
      ${leaf(10, 150, -80, 1)}${leaf(12, 104, -110, .9, 'url(#leaf2)')}${leaf(22, 74, -60, 1.1)}${leaf(52, 32, -140, 1)}${leaf(70, 20, -20, .9, 'url(#leaf2)')}${leaf(112, 10, 10, 1.1)}${leaf(150, 6, -170, .9)}${leaf(34, 64, 40, .8, 'url(#leaf2)')}${leaf(128, 30, 50, .8)}
      ${flower(36, 44, 1.2, '#FF9CC0')}${flower(98, 16, 1, '#FFFFFF')}${flower(14, 96, .9, '#C7A8FF')}${flower(166, 12, .8, '#FF9CC0')}
    </g>`);
};

const constellation = () => {
  const S = 170, stars = [[18, 22, 7], [64, 14, 5], [110, 36, 8], [40, 66, 5], [86, 92, 6], [20, 124, 4.5], [140, 12, 4], [62, 140, 4]];
  const lines = [[0, 1], [1, 2], [0, 3], [3, 4], [2, 4], [3, 5], [2, 6], [4, 7]];
  return svg(S, S, `<defs>${glow('g', 2.4)}${rad('core', [[0, '#FFFFFF'], [.4, '#DDF2FF', .9], [1, '#9FD8FF', 0]])}</defs>
    <g stroke="#BFE6FF" stroke-opacity=".55" stroke-width="1.2" filter="url(#g)">${lines.map(([a, b]) => `<line x1="${stars[a][0]}" y1="${stars[a][1]}" x2="${stars[b][0]}" y2="${stars[b][1]}"/>`).join('')}</g>
    ${stars.map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r * 2}" fill="url(#core)"/>${sparkle(x, y, r * 1.6, '#FFFFFF')}`).join('')}`);
};

const flame = (t, i) => {
  const W = 72, H = 112, x = W / 2, b = H - 4;
  const sw = Math.sin(t * TAU) * 6, h = 92 + Math.sin(t * TAU * 2) * 8, tip = x + sw;
  const layer = (s, fill) => `<path d="M${f(x - 30 * s)} ${b} C${f(x - 34 * s)} ${f(b - h * s * .45)} ${f(x - 10 * s + sw * .4)} ${f(b - h * s * .7)} ${f(tip)} ${f(b - h * s)} C${f(x + 6 * s + sw * .2)} ${f(b - h * s * .62)} ${f(x + 34 * s)} ${f(b - h * s * .5)} ${f(x + 30 * s)} ${b} Q${x} ${f(b + 6)} ${f(x - 30 * s)} ${b}Z" fill="${fill}"/>`;
  return svg(W, H, `<defs>${lin('o', [[0, '#FF2E3F', .2], [.5, '#FF4D2E'], [1, '#FF8A1F']])}${lin('m', [[0, '#FF8A1F', .4], [1, '#FFC24A']])}${lin('i', [[0, '#FFE58A', .3], [1, '#FFFBE0']])}${blur('bl', 3)}</defs>
    <g filter="url(#bl)" opacity=".7">${layer(1.05, '#FF5A1F')}</g>${layer(1, 'url(#o)')}${layer(.72, 'url(#m)')}${layer(.42, 'url(#i)')}`);
};

const crystal = () => {
  const S = 170;
  const shard = (x, y, len, wid, rot, c1, c2) => `<g transform="translate(${x} ${y}) rotate(${rot})">
    <path d="M0 0 L${-wid} ${-len * .25} L${-wid * .55} ${-len * .9} L0 ${-len} L${wid * .55} ${-len * .9} L${wid} ${-len * .25}Z" fill="url(#${c1})" stroke="#FFFFFF" stroke-opacity=".7" stroke-width="1.2"/>
    <path d="M0 0 L0 ${-len} L${wid * .55} ${-len * .9} L${wid} ${-len * .25}Z" fill="url(#${c2})" opacity=".85"/>
    <path d="M${-wid * .6} ${-len * .3} L${-wid * .3} ${-len * .82}" stroke="#FFFFFF" stroke-opacity=".8" stroke-width="2" stroke-linecap="round"/></g>`;
  return svg(S, S, `<defs>${lin('a', [[0, '#F3E8FF'], [.5, '#B388FF'], [1, '#6A3FC4']])}${lin('b', [[0, '#5A2EAE'], [1, '#2E1470']])}${lin('c', [[0, '#E0FAFF'], [.5, '#7EDCFF'], [1, '#2C8FC4']])}${lin('d', [[0, '#1E78A8'], [1, '#0E3E66']])}${shadow('sh', 3, 3, .5)}${glow('g', 3)}</defs>
    <g filter="url(#sh)">
      ${shard(26, 26, 70, 13, 135, 'a', 'b')}${shard(16, 40, 52, 10, 160, 'c', 'd')}${shard(40, 16, 54, 10, 112, 'c', 'd')}
      ${shard(10, 14, 40, 8, 180, 'a', 'b')}${shard(14, 10, 40, 8, 92, 'a', 'b')}${shard(46, 44, 34, 8, 135, 'a', 'b')}
    </g>
    ${sparkle(78, 74, 8, '#FFFFFF')}${sparkle(30, 92, 5, '#E6F6FF')}${sparkle(96, 30, 5, '#F3E8FF')}`);
};

const moon = () => {
  const S = 150, c = 75, r = 40;
  return svg(S, S, `<defs>${rad('halo', [[0, '#FFE9A8', .55], [.5, '#FFE9A8', .18], [1, '#FFE9A8', 0]])}${rad('m', [[0, '#FFFBEA'], [.6, '#FFE9A8'], [1, '#E8C46A']], .35, .35, .8)}
    <mask id="cut"><rect width="${S}" height="${S}" fill="#FFF"/><circle cx="${c + 22}" cy="${c - 14}" r="${r * .86}" fill="#000"/></mask></defs>
    <circle cx="${c}" cy="${c}" r="${c}" fill="url(#halo)"/>
    <g mask="url(#cut)"><circle cx="${c}" cy="${c}" r="${r}" fill="url(#m)"/>
      <circle cx="${c - 16}" cy="${c + 10}" r="6" fill="#E8C46A" opacity=".6"/><circle cx="${c - 6}" cy="${c + 24}" r="4" fill="#E8C46A" opacity=".5"/><circle cx="${c - 26}" cy="${c - 6}" r="3.5" fill="#E8C46A" opacity=".5"/>
      <path d="M${c - 30} ${c - 22} A${r} ${r} 0 0 0 ${c - 22} ${c + 30}" stroke="#FFFFFF" stroke-opacity=".7" stroke-width="3" fill="none" stroke-linecap="round"/></g>`);
};

const cloud = () => {
  const W = 190, H = 84;
  return svg(W, H, `<defs>${lin('c', [[0, '#5C67C9', .95], [1, '#2C2F6E', .95]])}${blur('b', 1)}</defs>
    <path d="M14 66 C4 66 4 50 18 48 C16 32 36 26 46 36 C52 18 80 14 90 32 C100 20 124 22 128 40 C142 34 160 42 156 56 C172 56 176 70 162 72 Z" fill="url(#c)" filter="url(#b)"/>
    <path d="M22 50 C22 40 34 36 42 42 M54 34 C62 24 80 22 88 34 M98 32 C108 26 120 30 124 40" stroke="#9FA8FF" stroke-opacity=".7" stroke-width="2.4" fill="none" stroke-linecap="round"/>`);
};

export default [
  { name: 'pf_cobweb', w: 230, h: 230, frames: 1, draw: cobweb },
  { name: 'pf_spider', w: 56, h: 64, frames: 16, fps: 20, draw: spider },
  { name: 'pf_vines', w: 190, h: 190, frames: 1, draw: vines },
  { name: 'pf_constellation', w: 170, h: 170, frames: 1, draw: constellation },
  { name: 'pf_flame', w: 72, h: 112, frames: 24, fps: 30, draw: flame },
  { name: 'pf_crystal', w: 170, h: 170, frames: 1, draw: crystal },
  { name: 'pf_moon', w: 150, h: 150, frames: 1, draw: moon },
  { name: 'pf_cloud', w: 190, h: 84, frames: 1, draw: cloud },
];
