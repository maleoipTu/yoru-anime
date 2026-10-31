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
  return svg(S, S, `<defs>${lin('leaf', [[0, '#A8EB8F'], [1, '#3E8E3A']])}${lin('leaf2', [[0, '#7DD07A'], [1, '#2C6E2A']])}${lin('stem', [[0, '#5FA34A'], [1, '#2E5E22']])}${shadow('sh', 2, 2, .45)}${rad('bloom', [[0, '#FFF2C8', .35], [1, '#FFF2C8', 0]])}</defs>
    <g filter="url(#sh)">
      <path d="M6 186 C10 120 14 70 44 40 C74 12 120 8 186 6" fill="none" stroke="url(#stem)" stroke-width="5" stroke-linecap="round"/>
      <path d="M14 120 C34 104 40 86 34 64 M80 18 C92 36 110 40 128 30" fill="none" stroke="url(#stem)" stroke-width="3" stroke-linecap="round"/>
      <path d="M40 46 c12 2 18 12 12 20 c-6 6 -14 -2 -8 -6" fill="none" stroke="#5FA34A" stroke-width="2" stroke-linecap="round"/>
      ${leaf(10, 150, -80, 1)}${leaf(12, 104, -110, .9, 'url(#leaf2)')}${leaf(22, 74, -60, 1.1)}${leaf(52, 32, -140, 1)}${leaf(70, 20, -20, .9, 'url(#leaf2)')}${leaf(112, 10, 10, 1.1)}${leaf(150, 6, -170, .9)}${leaf(34, 64, 40, .8, 'url(#leaf2)')}${leaf(128, 30, 50, .8)}
      <path d="M60 30 c-4 -10 4 -16 10 -12 c4 3 0 8 -3 6 M24 132 c-10 -2 -12 8 -6 12 c4 2 7 -2 4 -4 M146 8 c2 8 10 10 14 6" fill="none" stroke="#7DD07A" stroke-width="1.6" stroke-linecap="round"/>
      ${[[128, 30, '#FFC2D9'], [12, 150, '#FFFFFF'], [84, 22, '#C7A8FF'], [22, 66, '#FFC2D9']].map(([x, y, col]) => `<ellipse cx="${x}" cy="${y}" rx="3.2" ry="4.4" fill="${col}" stroke="#2E6B2C" stroke-width=".8"/><path d="M${x - 3} ${y + 3} Q${x} ${y + 6} ${x + 3} ${y + 3}" fill="#5FA34A"/>`).join('')}
      ${flower(36, 44, 1.2, '#FF9CC0')}${flower(98, 16, 1, '#FFFFFF')}${flower(14, 96, .9, '#C7A8FF')}${flower(166, 12, .8, '#FF9CC0')}
    </g>
    <g>${[[36, 44, 7], [98, 16, 6], [14, 96, 5]].map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r * 2.6}" fill="url(#bloom)"/>`).join('')}
      ${sparkle(46, 30, 4, '#FFFFFF', .9)}${sparkle(110, 6, 3, '#FFFFFF', .8)}${sparkle(6, 110, 3, '#FFFFFF', .8)}
    </g>`);
};

const constellation = () => {
  const S = 170, stars = [[18, 22, 7], [64, 14, 5], [110, 36, 8], [40, 66, 5], [86, 92, 6], [20, 124, 4.5], [140, 12, 4], [62, 140, 4]];
  const lines = [[0, 1], [1, 2], [0, 3], [3, 4], [2, 4], [3, 5], [2, 6], [4, 7]];
  const dust = [[30, 40], [92, 22], [128, 58], [50, 104], [104, 120], [16, 84], [76, 50], [150, 32], [120, 90], [36, 152], [96, 150], [154, 70]];
  return svg(S, S, `<defs>${glow('g', 2.4)}${blur('neb', 10)}${rad('core', [[0, '#FFFFFF'], [.4, '#DDF2FF', .9], [1, '#9FD8FF', 0]])}
    ${rad('n1', [[0, '#6F7BFF', .5], [1, '#6F7BFF', 0]])}${rad('n2', [[0, '#C77DFF', .38], [1, '#C77DFF', 0]])}${rad('n3', [[0, '#7EDCFF', .32], [1, '#7EDCFF', 0]])}</defs>
    <g filter="url(#neb)"><ellipse cx="60" cy="54" rx="58" ry="34" fill="url(#n1)" transform="rotate(-30 60 54)"/><ellipse cx="96" cy="42" rx="40" ry="22" fill="url(#n2)"/><ellipse cx="38" cy="100" rx="30" ry="40" fill="url(#n3)"/></g>
    ${dust.map(([x, y], k) => `<circle cx="${x}" cy="${y}" r="${k % 3 ? .8 : 1.2}" fill="#FFFFFF" opacity="${k % 2 ? .45 : .7}"/>`).join('')}
    <g stroke="#BFE6FF" stroke-opacity=".6" stroke-width="1.2" filter="url(#g)">${lines.map(([a, b]) => `<line x1="${stars[a][0]}" y1="${stars[a][1]}" x2="${stars[b][0]}" y2="${stars[b][1]}"/>`).join('')}</g>
    <path d="M8 160 A150 150 0 0 1 160 8" stroke="#BFE6FF" stroke-opacity=".25" stroke-width="1" stroke-dasharray="1 5" stroke-linecap="round" fill="none"/>
    ${stars.map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r * 2.2}" fill="url(#core)"/>${sparkle(x, y, r * 1.7, '#FFFFFF')}<g transform="rotate(45 ${x} ${y})">${sparkle(x, y, r * .8, '#DDF2FF', .8)}</g>`).join('')}`);
};

const flame = (t, i) => {
  const W = 72, H = 112, x = W / 2, b = H - 4;
  const sw = Math.sin(t * TAU) * 6, h = 92 + Math.sin(t * TAU * 2) * 8, tip = x + sw;
  const layer = (s, fill, dx = 0, ph = 0) => { const sw2 = Math.sin((t + ph) * TAU) * 6 * s, hh = h * (1 + .06 * Math.sin((t + ph) * TAU * 2)), tp = x + dx + sw2;
    return `<path d="M${f(x + dx - 30 * s)} ${b} C${f(x + dx - 34 * s)} ${f(b - hh * s * .45)} ${f(x + dx - 10 * s + sw2 * .4)} ${f(b - hh * s * .7)} ${f(tp)} ${f(b - hh * s)} C${f(x + dx + 6 * s + sw2 * .2)} ${f(b - hh * s * .62)} ${f(x + dx + 34 * s)} ${f(b - hh * s * .5)} ${f(x + dx + 30 * s)} ${b} Q${x + dx} ${f(b + 6)} ${f(x + dx - 30 * s)} ${b}Z" fill="${fill}"/>`; };
  const sparks = [0, .33, .66].map((ph, k) => { const p = (t + ph) % 1, sx = x + Math.sin((p + k) * TAU) * 10 + (k - 1) * 8, sy = b - 30 - p * 70; return `<circle cx="${f(sx)}" cy="${f(sy)}" r="${f(2 * (1 - p) + .5)}" fill="#FFE08A" opacity="${f(Math.sin(p * Math.PI))}"/>`; }).join('');
  return svg(W, H, `<defs>${lin('o', [[0, '#FF2E3F', .2], [.5, '#FF4D2E'], [1, '#FF8A1F']])}${lin('m', [[0, '#FF8A1F', .4], [1, '#FFC24A']])}${lin('i', [[0, '#FFE58A', .3], [1, '#FFFBE0']])}${lin('blue', [[0, '#7EC8FF', 0], [1, '#9FD8FF', .7]])}${blur('bl', 3)}</defs>
    <g filter="url(#bl)" opacity=".7">${layer(1.05, '#FF5A1F')}</g>
    ${layer(.62, 'url(#o)', -12, .3)}${layer(.6, 'url(#o)', 12, .6)}
    ${layer(1, 'url(#o)')}${layer(.72, 'url(#m)')}${layer(.42, 'url(#i)')}
    <ellipse cx="${x}" cy="${b - 4}" rx="12" ry="5" fill="url(#blue)" opacity=".55"/>${sparks}`);
};

const crystal = () => {
  const S = 170;
  const shard = (x, y, len, wid, rot, c1, c2) => `<g transform="translate(${x} ${y}) rotate(${rot})">
    <path d="M0 0 L${-wid} ${-len * .25} L${-wid * .55} ${-len * .9} L0 ${-len} L${wid * .55} ${-len * .9} L${wid} ${-len * .25}Z" fill="url(#${c1})" stroke="#FFFFFF" stroke-opacity=".75" stroke-width="1.2"/>
    <path d="M0 0 L0 ${-len} L${wid * .55} ${-len * .9} L${wid} ${-len * .25}Z" fill="url(#${c2})" opacity=".85"/>
    <path d="M0 ${-len * .35} L${-wid * .5} ${-len * .55} L0 ${-len * .82}" fill="none" stroke="#FFFFFF" stroke-opacity=".35" stroke-width="1"/>
    <path d="M${-wid * .6} ${-len * .3} L${-wid * .3} ${-len * .82}" stroke="#FFFFFF" stroke-opacity=".85" stroke-width="2" stroke-linecap="round"/>
    <path d="M0 ${-len * .2} L0 ${-len * .7}" stroke="url(#inner)" stroke-width="${wid * .8}" stroke-linecap="round" opacity=".55"/></g>`;
  return svg(S, S, `<defs>${lin('a', [[0, '#F3E8FF'], [.5, '#B388FF'], [1, '#6A3FC4']])}${lin('b', [[0, '#5A2EAE'], [1, '#2E1470']])}${lin('c', [[0, '#E0FAFF'], [.5, '#7EDCFF'], [1, '#2C8FC4']])}${lin('d', [[0, '#1E78A8'], [1, '#0E3E66']])}
    ${lin('inner', [[0, '#FFFFFF', 0], [.5, '#FFFFFF', .8], [1, '#FFFFFF', 0]])}${rad('glow', [[0, '#C9A8FF', .6], [.5, '#7EDCFF', .2], [1, '#7EDCFF', 0]])}${shadow('sh', 3, 3, .5)}${glow('g', 3)}</defs>
    <circle cx="30" cy="30" r="62" fill="url(#glow)"/>
    <g filter="url(#sh)">
      ${shard(26, 26, 70, 13, 135, 'a', 'b')}${shard(16, 40, 52, 10, 160, 'c', 'd')}${shard(40, 16, 54, 10, 112, 'c', 'd')}
      ${shard(10, 14, 40, 8, 180, 'a', 'b')}${shard(14, 10, 40, 8, 92, 'a', 'b')}${shard(46, 44, 34, 8, 135, 'a', 'b')}
      ${shard(58, 12, 22, 5, 100, 'c', 'd')}${shard(12, 58, 22, 5, 170, 'c', 'd')}
    </g>
    ${sparkle(78, 74, 9, '#FFFFFF')}${sparkle(30, 92, 5, '#E6F6FF')}${sparkle(96, 30, 5, '#F3E8FF')}${sparkle(60, 56, 4, '#FFFFFF', .8)}`);
};

const moon = () => {
  const S = 150, c = 75, r = 40;
  return svg(S, S, `<defs>${rad('halo', [[0, '#FFE9A8', .55], [.5, '#FFE9A8', .18], [1, '#FFE9A8', 0]])}${rad('m', [[0, '#FFFBEA'], [.6, '#FFE9A8'], [1, '#E8C46A']], .35, .35, .8)}
    ${rad('crater', [[0, '#D9AE52', .65], [.7, '#E8C46A', .35], [1, '#FFF1C4', .6]], .6, .6, .6)}${lin('rim', [[0, '#FFFFFF', .9], [1, '#FFFFFF', 0]], 0, 0, 1, 1)}
    <mask id="cut"><rect width="${S}" height="${S}" fill="#FFF"/><circle cx="${c + 22}" cy="${c - 14}" r="${r * .86}" fill="#000"/></mask></defs>
    <circle cx="${c}" cy="${c}" r="${c}" fill="url(#halo)"/>
    <g mask="url(#cut)"><circle cx="${c}" cy="${c}" r="${r}" fill="url(#m)"/>
      <path d="M${c - 34} ${c + 4} C${c - 30} ${c + 20} ${c - 18} ${c + 32} ${c - 2} ${c + 36} C${c - 14} ${c + 24} ${c - 26} ${c + 14} ${c - 34} ${c + 4}Z" fill="#E8C46A" opacity=".35"/>
      ${[[-16, 10, 6], [-6, 24, 4], [-26, -6, 3.5], [-22, 22, 2.6], [-10, -18, 2.2]].map(([dx, dy, cr]) => `<circle cx="${c + dx}" cy="${c + dy}" r="${cr}" fill="url(#crater)"/>`).join('')}
      <path d="M${c - 30} ${c - 22} A${r} ${r} 0 0 0 ${c - 22} ${c + 30}" stroke="url(#rim)" stroke-width="3" fill="none" stroke-linecap="round"/></g>
    ${sparkle(c + 34, c + 18, 6, '#FFFBEA', .9)}${sparkle(c + 18, c + 36, 3.5, '#FFFFFF', .8)}`);
};

const cloud = () => {
  const W = 190, H = 84;
  const shape = 'M14 66 C4 66 4 50 18 48 C16 32 36 26 46 36 C52 18 80 14 90 32 C100 20 124 22 128 40 C142 34 160 42 156 56 C172 56 176 70 162 72 Z';
  return svg(W, H, `<defs>${lin('c', [[0, '#6E79D9', .96], [.55, '#3E4596', .96], [1, '#262A62', .96]])}${lin('lit', [[0, '#C9CFFF', .9], [1, '#9FA8FF', 0]])}${blur('b', 1)}${blur('b2', 3)}
    <clipPath id="clip"><path d="${shape}"/></clipPath></defs>
    <path d="${shape}" fill="#1A1C44" opacity=".5" transform="translate(4 5)" filter="url(#b2)"/>
    <path d="${shape}" fill="url(#c)" filter="url(#b)"/>
    <g clip-path="url(#clip)"><ellipse cx="70" cy="80" rx="80" ry="18" fill="#1E2150" opacity=".45" filter="url(#b2)"/>
      <ellipse cx="96" cy="40" rx="30" ry="14" fill="#8A93FF" opacity=".2" filter="url(#b2)"/></g>
    <path d="M22 50 C22 40 34 36 42 42 M54 34 C62 24 80 22 88 34 M98 32 C108 26 120 30 124 40 M132 42 C142 38 152 44 152 52" stroke="url(#lit)" stroke-width="2.6" fill="none" stroke-linecap="round"/>`);
};

// ───── Набор администрации: «Полуночная грань» ─────
// Контур и угловые скобки клиент рисует сам тонкой линией (они повторяют скругление карточки при любом размере).
// Здесь — только эмблема по центру верхнего края: тонкое кольцо с серебряным полумесяцем и маленький ромб цвета зари.
// Лист рисуется вдвое мельче (≈48×36), чтобы линии оставались чёткими.
const nightCrest = () => {
  const W = 96, H = 72, cx = W / 2, cy = 26, mr = 11;
  return svg(W, H, `<defs>${lin('silver', [[0, '#F4F7FC'], [.55, '#DCE3F0'], [1, '#9AA4B6']], 0, 0, 1, 1)}
    ${lin('dawn', [[0, '#FF8A3D'], [1, '#FF4D67']], 0, 0, 1, 1)}${shadow('sh', 1, 1.4, .5)}
    <mask id="cut"><rect width="${W}" height="${H}" fill="#FFF"/><circle cx="${cx + 5.4}" cy="${cy - 3}" r="${f(mr * .84)}" fill="#000"/></mask></defs>
    <g filter="url(#sh)">
      <circle cx="${cx}" cy="${cy}" r="19" fill="none" stroke="#DCE3F0" stroke-opacity=".7" stroke-width="2"/>
      <g mask="url(#cut)" transform="rotate(-24 ${cx} ${cy})"><circle cx="${cx}" cy="${cy}" r="${mr}" fill="url(#silver)"/></g>
      <path d="M${cx} ${cy + 26} L${cx + 3.8} ${cy + 31} L${cx} ${cy + 36} L${cx - 3.8} ${cy + 31}Z" fill="url(#dawn)"/>
    </g>`);
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
  { name: 'pf_night_crest', w: 96, h: 72, frames: 1, draw: nightCrest },
];
