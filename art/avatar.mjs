// Украшения аватара: холст 240×240, аватар — круг в центре (120,120) радиусом ~86.
// Рисунок выходит за круг и частично ложится на аватар, как в Discord.
import { TAU, sin, cos, f, shadow, glow, blur, lin, rad, sparkle, star5, heartPath, svg } from './lib.mjs';
const W = 240, C = 120, R = 86, N = 40, FPS = 30;
const at = (deg, r = R) => [C + Math.cos(deg * Math.PI / 180) * r, C + Math.sin(deg * Math.PI / 180) * r];
const twinkles = (t, pts, color = '#FFFFFF') => pts.map(([x, y, s, ph]) => { const a = Math.max(0, sin(t + ph)); return a > .02 ? sparkle(x, y, s * (.4 + .6 * a), color, a) : ''; }).join('');

/** Ушко: основание шириной 2·w по оси X, кончик вверх на высоте h. */
const earPath = (w, h) => `M${-w} 0 C${f(-w * .95)} ${f(-h * .45)} ${f(-w * .45)} ${f(-h * .85)} 0 ${-h} C${f(w * .45)} ${f(-h * .85)} ${f(w * .95)} ${f(-h * .45)} ${w} 0 Q0 ${f(h * .14)} ${-w} 0Z`;

const catEars = t => {
  const twitch = t > .62 && t < .78 ? Math.sin((t - .62) / .16 * Math.PI) * -12 : 0;
  const ear = (deg, tilt, flip) => {
    const [x, y] = at(deg, 72);
    const rot = deg + 90 + tilt;
    return `<g transform="translate(${f(x)} ${f(y)}) rotate(${f(rot)})" filter="url(#sh)">
      <path d="${earPath(36, 66)}" fill="url(#fur)" stroke="#7A3E63" stroke-width="2.6" stroke-linejoin="round"/>
      <path d="${earPath(22, 46)}" transform="translate(0 -6)" fill="url(#inner)"/>
      <path d="M${-30} -8 C-28 -30 -18 -50 -4 -62" fill="none" stroke="#FFFFFF" stroke-opacity=".55" stroke-width="3" stroke-linecap="round"/>
      ${[[-12, -10, 14], [-4, -6, 18], [5, -9, 15], [12, -12, 11]].map(([fx, fy, l]) => `<path d="M${fx} ${fy} q${flip * 2} ${-l * .6} ${fx > 0 ? 4 : -4} ${-l}" fill="none" stroke="#FFF8FB" stroke-width="2.4" stroke-linecap="round" opacity=".95"/>`).join('')}
    </g>`;
  };
  const [bx, by] = at(-126, 82);
  return svg(W, W, `<defs>${shadow('sh', 3, 2.5, .5)}${lin('fur', [[0, '#FFE3EF'], [.5, '#F7B6D2'], [1, '#D97BAA']])}${rad('inner', [[0, '#FFD3E2'], [.7, '#FF8FB4'], [1, '#E0628F']], .5, .75, .7)}
    ${lin('bow', [[0, '#FF7A8E'], [1, '#C72846']])}</defs>
    ${ear(-124, -4 + sin(t) * 1.5, -1)}${ear(-56, 4 + twitch - sin(t) * 1.5, 1)}
    <g transform="translate(${f(bx)} ${f(by)}) rotate(-30)" filter="url(#sh)">
      <path d="M0 0 C-14 -14 -26 -6 -24 4 C-22 14 -10 10 0 0Z M0 0 C14 -14 26 -6 24 4 C22 14 10 10 0 0Z" fill="url(#bow)" stroke="#8A1530" stroke-width="1.6"/>
      <path d="M-20 -2 C-14 -6 -8 -4 -4 -1 M20 -2 C14 -6 8 -4 4 -1" stroke="#FFFFFF" stroke-opacity=".55" stroke-width="2" fill="none" stroke-linecap="round"/>
      <circle r="5.5" fill="#E23A58" stroke="#8A1530" stroke-width="1.4"/><circle cx="-1.5" cy="-1.8" r="1.6" fill="#FFF" opacity=".8"/>
    </g>
    ${twinkles(t, [[bx + 18, by - 14, 7, .1], [bx - 20, by + 6, 5, .6], [176, 30, 6, .35]], '#FFE3EF')}`);
};

const bunny = t => {
  const ear = (deg, tilt, bend, flip) => {
    const [x, y] = at(deg, 66);
    const sway = sin(t + (flip > 0 ? .25 : 0)) * 3;
    return `<g transform="translate(${f(x)} ${f(y)}) rotate(${f(deg + 90 + tilt + sway)})" filter="url(#sh)">
      <path d="M-15 0 C-24 -30 -20 -58 -4 -70 C${f(6 + bend)} ${f(-74 + bend * .5)} ${f(16 + bend)} ${f(-56)} 15 0 Q0 7 -15 0Z" fill="url(#bfur)" stroke="#9B8FA6" stroke-width="2.4" stroke-linejoin="round"/>
      <path d="M-7 -6 C-13 -28 -10 -50 -2 -59 C${f(4 + bend * .6)} ${f(-60)} ${f(9 + bend * .6)} -40 8 -6 Q0 -2 -7 -6Z" fill="url(#binner)"/>
      <path d="M-11 -10 C-15 -30 -13 -50 -5 -62" fill="none" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round" opacity=".8"/>
    </g>`;
  };
  return svg(W, W, `<defs>${shadow('sh', 3, 2.5, .45)}${lin('bfur', [[0, '#FFFFFF'], [.7, '#F3EEF7'], [1, '#D9D0E3']])}${lin('binner', [[0, '#FFB9CF'], [1, '#FF8FB2']])}${rad('bow', [[0, '#9FD8FF'], [1, '#4A8FD6']], .4, .3, .8)}</defs>
    ${ear(-118, -12, 0, -1)}${ear(-62, 16, 10, 1)}
    <g transform="translate(${f(at(-92, 84)[0])} ${f(at(-92, 84)[1])})" filter="url(#sh)">
      <path d="M0 0 C-12 -12 -24 -6 -22 4 C-20 13 -9 9 0 0Z M0 0 C12 -12 24 -6 22 4 C20 13 9 9 0 0Z" fill="url(#bow)" stroke="#2E6AA8" stroke-width="1.6"/>
      <circle r="5" fill="#6FB4F0" stroke="#2E6AA8" stroke-width="1.3"/><circle cx="-1.4" cy="-1.6" r="1.5" fill="#FFF" opacity=".85"/>
      <path d="M-18 -2 C-13 -6 -8 -4 -4 -1 M18 -2 C13 -6 8 -4 4 -1" stroke="#FFFFFF" stroke-opacity=".6" stroke-width="1.8" fill="none" stroke-linecap="round"/>
    </g>
    ${twinkles(t, [[at(-92, 84)[0] + 30, at(-92, 84)[1] - 8, 6, .2], [at(-92, 84)[0] - 32, at(-92, 84)[1] + 2, 5, .7]], '#E6F4FF')}`);
};

const halo = t => {
  const bob = sin(t) * 2.5, y = 36 + bob;
  const ring = `<ellipse cx="${C}" cy="${f(y)}" rx="58" ry="15"`;
  return svg(W, W, `<defs>${glow('g', 5)}${blur('soft', 7)}${lin('gold', [[0, '#FFF7CF'], [.35, '#FFE07A'], [.7, '#F2B635'], [1, '#C9861A']], 0, 0, 0, 1)}${lin('rim', [[0, '#FFFFFF', .95], [.5, '#FFF4B0', .3], [1, '#FFF4B0', 0]], 0, 0, 0, 1)}
    ${lin('cone', [[0, '#FFF2B8', .45], [1, '#FFF2B8', 0]], 0, 0, 0, 1)}${rad('spec', [[0, '#FFFFFF'], [.5, '#FFFBE0', .6], [1, '#FFF4B0', 0]])}</defs>
    <path d="M${C - 50} ${f(y + 6)} L${C + 50} ${f(y + 6)} L${C + 74} 118 L${C - 74} 118Z" fill="url(#cone)" opacity="${f(.5 + .2 * sin(t))}" filter="url(#soft)"/>
    ${ring} fill="none" stroke="#FFD95A" stroke-opacity="${f(.45 + .2 * sin(t))}" stroke-width="14" filter="url(#soft)"/>
    <g transform="rotate(-6 ${C} ${f(y)})">
      ${ring} fill="none" stroke="#8A5A0E" stroke-opacity=".55" stroke-width="12" transform="translate(0 2)"/>
      ${ring} fill="none" stroke="url(#gold)" stroke-width="10"/>
      <ellipse cx="${C}" cy="${f(y - 2)}" rx="54" ry="11" fill="none" stroke="url(#rim)" stroke-width="2.4"/>
      ${(() => { const a = t * TAU, hx = C + Math.cos(a) * 58, hy = y + Math.sin(a) * 15, front = .55 + .45 * Math.sin(a);
        return `<ellipse cx="${f(hx)}" cy="${f(hy)}" rx="${f(9 + 4 * front)}" ry="${f(4 + 1.5 * front)}" fill="url(#spec)" opacity="${f(.5 + .5 * front)}"/>`; })()}
      ${ring} fill="none" stroke="#FFFFFF" stroke-opacity=".25" stroke-width="1"/>
    </g>
    ${[0, .2, .4, .6, .8].map((ph, i) => { const p = (t + ph) % 1, x = 78 + i * 21 + sin(p + i * .3) * 4; return `<circle cx="${f(x)}" cy="${f(y + 10 + p * 46)}" r="${f(1.6 - p)}" fill="#FFF6C8" opacity="${f(Math.sin(p * Math.PI) * .9)}"/>`; }).join('')}
    ${twinkles(t, [[62, y - 10, 9, 0], [182, y + 4, 8, .35], [150, y - 18, 6, .6], [92, y + 14, 5, .8]], '#FFF6C8')}`);
};

const horns = t => {
  const heat = .5 + .5 * sin(t * 2);
  const horn = (deg, flip) => {
    const [x, y] = at(deg, 76);
    const d = `M-15 6 C-17 -16 -6 -42 12 -64 C9 -40 18 -18 15 6 Q0 12 -15 6Z`;
    return `<g transform="translate(${f(x)} ${f(y)}) rotate(${f((deg + 90) * .55)}) scale(${flip < 0 ? -1 : 1} 1)" filter="url(#sh)">
      <path d="${d.replace(/-?\d+(\.\d+)?/g, m => m)}" fill="url(#horn)" stroke="#3A0610" stroke-width="2.2" stroke-linejoin="round"/>
      ${[0, -12, -24, -36, -47].map((yy, i) => `<path d="M${f(-13 + i * 1.6)} ${yy} Q0 ${f(yy - 5 + i * .5)} ${f(13 - i * 2.6)} ${f(yy - 2)}" stroke="#2A0208" stroke-opacity=".5" stroke-width="2" fill="none" stroke-linecap="round"/>`).join('')}
      <path d="M-9 0 C-10 -18 -2 -38 9 -56" fill="none" stroke="#FFB3B3" stroke-opacity=".75" stroke-width="2.6" stroke-linecap="round"/>
      <path d="M4 2 L6 -10 L2 -18 L5 -30 L3 -38" fill="none" stroke="#FFB347" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" opacity="${f(.35 + .55 * heat)}" filter="url(#g)"/>
    </g>`;
  };
  const embers = [0, .25, .5, .75].map((ph, i) => { const p = (t + ph) % 1, x = (i % 2 ? 160 : 80) + sin(p + i) * 8, y = 34 - p * 30; return `<circle cx="${f(x)}" cy="${f(y)}" r="${f(2.6 * (1 - p) + .6)}" fill="#FFB347" opacity="${f(1 - p)}" filter="url(#g)"/>`; }).join('');
  return svg(W, W, `<defs>${shadow('sh', 3, 2.5, .5)}${glow('g', 2.5)}${lin('horn', [[0, '#FF7A7A'], [.45, '#C4142F'], [1, '#4D0410']], 0, 0, 0, 1)}${rad('heat', [[0, '#FF6A2E', .7], [1, '#FF2E3F', 0]])}</defs>
    ${[-122, -58].map(d => { const [x, y] = at(d, 80); return `<ellipse cx="${f(x)}" cy="${f(y)}" rx="22" ry="10" fill="url(#heat)" opacity="${f(.35 + .35 * heat)}"/>`; }).join('')}
    ${horn(-122, -1)}${horn(-58, 1)}${embers}`);
};

const headphones = t => {
  const pulse = .55 + .45 * sin(t);
  const notes = [0, .5].map((ph, i) => { const p = (t + ph) % 1, x = 214 + sin(p * .9 + i) * 8 - p * 6, y = 128 - p * 70; return `<g transform="translate(${f(x)} ${f(y)}) scale(${f(.8 + .2 * (1 - p))})" opacity="${f(Math.min(1, (1 - p) * 1.6))}" filter="url(#sh)"><path d="M0 0 v-18 l12 -4 v16" fill="none" stroke="#FFD08A" stroke-width="2.6" stroke-linecap="round"/><ellipse cx="-3" cy="1" rx="5" ry="4" fill="#FFB347"/><ellipse cx="9" cy="-3" rx="5" ry="4" fill="#FFB347"/></g>`; }).join('');
  const cup = side => {
    const x = C + side * 94;
    return `<g filter="url(#sh)"><rect x="${f(x - 17)}" y="92" width="34" height="62" rx="15" fill="url(#cup)" stroke="#121218" stroke-width="2"/>
      <rect x="${f(x - 11)}" y="100" width="22" height="46" rx="10" fill="#22222C"/>
      <rect x="${f(x - 11)}" y="100" width="22" height="46" rx="10" fill="none" stroke="#FF8A3D" stroke-width="3" stroke-opacity="${f(pulse)}" filter="url(#g)"/>
      <path d="M${f(x - 10 * side)} 98 v30" stroke="#FFFFFF" stroke-opacity=".3" stroke-width="3" stroke-linecap="round"/></g>`;
  };
  return svg(W, W, `<defs>${shadow('sh', 3, 2.5, .5)}${glow('g', 3)}${lin('band', [[0, '#5C5C70'], [.5, '#33333F'], [1, '#1D1D25']])}${lin('cup', [[0, '#4C4C5E'], [1, '#1E1E26']], 0, 0, 1, 0)}</defs>
    <path d="M${C - 94} 112 C${C - 96} 38 ${C + 96} 38 ${C + 94} 112" fill="none" stroke="#101016" stroke-width="17" stroke-linecap="round" filter="url(#sh)"/>
    <path d="M${C - 94} 112 C${C - 96} 38 ${C + 96} 38 ${C + 94} 112" fill="none" stroke="url(#band)" stroke-width="13" stroke-linecap="round"/>
    <path d="M${C - 80} 76 C${C - 60} 46 ${C + 60} 46 ${C + 80} 76" fill="none" stroke="#FFFFFF" stroke-opacity=".28" stroke-width="3" stroke-linecap="round"/>
    ${[-1, 1].flatMap(side => [0, .5].map(ph => { const p = (t * 2 + ph) % 1, r = 20 + p * 22, x = C + side * 104; return `<path d="M${f(x + side * r * .3)} ${f(123 - r * .7)} Q${f(x + side * r * .8)} 123 ${f(x + side * r * .3)} ${f(123 + r * .7)}" stroke="#FFB36B" stroke-width="${f(2.8 - p * 1.6)}" fill="none" stroke-linecap="round" opacity="${f((1 - p) * .7)}"/>`; })).join('')}
    ${cup(-1)}${cup(1)}${notes}`);
};

const crown = t => {
  const shine = t < .45 ? t / .45 : -1;
  const body = 'M-48 8 L-52 -30 L-30 -12 L-14 -44 L0 -18 L14 -44 L30 -12 L52 -30 L48 8 Z';
  return svg(W, W, `<defs>${shadow('sh', 4, 3, .5)}${glow('g', 3)}${lin('gold', [[0, '#FFF3B0'], [.4, '#FFD24A'], [.8, '#E39B12'], [1, '#B36D07']])}${lin('band', [[0, '#F6B730'], [1, '#B8740A']])}
    ${rad('ruby', [[0, '#FFC2CC'], [.35, '#FF3D5E'], [1, '#8A0A22']], .35, .3, .75)}${rad('sapph', [[0, '#D6F3FF'], [.35, '#3DB6FF'], [1, '#0B4A8A']], .35, .3, .75)}
    <clipPath id="cl"><path d="${body}"/><rect x="-50" y="2" width="100" height="16" rx="4"/></clipPath>${lin('sweep', [[0, '#FFFFFF', 0], [.5, '#FFFFFF', .85], [1, '#FFFFFF', 0]], 0, 0, 1, 0)}${rad('aura', [[0, '#FFD24A', .45], [.6, '#FF9A2A', .12], [1, '#FF9A2A', 0]])}</defs>
    <ellipse cx="110" cy="44" rx="${f(56 + 4 * sin(t))}" ry="30" fill="url(#aura)" opacity="${f(.55 + .2 * sin(t))}"/>
    <g transform="translate(112 58) rotate(-12) scale(.9)" filter="url(#sh)">
      <path d="${body}" fill="url(#gold)" stroke="#7A4A05" stroke-width="2.2" stroke-linejoin="round"/>
      <rect x="-50" y="2" width="100" height="16" rx="4" fill="url(#band)" stroke="#7A4A05" stroke-width="2"/>
      <path d="M-44 -24 L-40 0 M-10 -34 L-4 -8" stroke="#FFFBE0" stroke-opacity=".7" stroke-width="2.4" stroke-linecap="round"/>
      ${[[-52, -30], [-14, -44], [14, -44], [52, -30]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="5" fill="#FFF8E6" stroke="#B88A2A" stroke-width="1.4"/><circle cx="${x - 1.5}" cy="${y - 1.5}" r="1.6" fill="#FFF"/>`).join('')}
      <ellipse cx="0" cy="10" rx="8" ry="7" fill="url(#ruby)" stroke="#5E0516" stroke-width="1.4"/>
      <ellipse cx="-28" cy="10" rx="5.5" ry="5" fill="url(#sapph)" stroke="#062E57" stroke-width="1.2"/><ellipse cx="28" cy="10" rx="5.5" ry="5" fill="url(#sapph)" stroke="#062E57" stroke-width="1.2"/>
      <circle cx="-2.5" cy="7" r="2.2" fill="#FFF" opacity=".85"/>
      ${[[0, 10, 0], [-28, 10, .33], [28, 10, .66]].map(([gx, gy, ph]) => { const k = Math.max(0, sin(t + ph)) ** 3; return k > .05 ? sparkle(gx + 2, gy - 3, 3 + 6 * k, '#FFFFFF', k) : ''; }).join('')}
      ${shine >= 0 ? `<g clip-path="url(#cl)"><rect x="${f(-90 + shine * 170)}" y="-60" width="26" height="90" fill="url(#sweep)" transform="rotate(20 ${f(-77 + shine * 170)} -15)"/></g>` : ''}
    </g>
    ${twinkles(t, [[60, 22, 9, 0], [172, 30, 8, .3], [150, 8, 6, .65], [74, 58, 5, .85]], '#FFF2B0')}`);
};

// Орбита: восемь фигур, за цикл каждая проходит ровно одно «место» соседки — петля бесшовная при 20 кадрах.
// Всё, что меняется, зависит от положения на орбите: снизу-слева фигуры крупнее и ярче (ближе к зрителю),
// сверху-справа — мельче и бледнее, как на наклонной орбите. Хвосты — дуги убывающей толщины.
const orbit = (glyph, colors) => t => {
  const RO = 101, items = [], trails = [];
  for (let i = 0; i < 8; i++) {
    const deg = (i + t) * 45, a = deg * Math.PI / 180;
    const depth = .5 + .5 * Math.cos((deg - 135) * Math.PI / 180);
    const x = C + Math.cos(a) * RO, y = C + Math.sin(a) * RO, s = 11 + 6 * depth + 1.4 * Math.sin(deg * 4 * Math.PI / 180), op = .62 + .38 * depth;
    [[26, 1.2, .14], [16, 2.2, .24], [8, 3.2, .38]].forEach(([len, w, o]) => {
      const a0 = (deg - len) * Math.PI / 180;
      trails.push(`<path d="M${f(C + Math.cos(a0) * RO)} ${f(C + Math.sin(a0) * RO)} A${RO} ${RO} 0 0 1 ${f(x)} ${f(y)}" stroke="${colors[1]}" stroke-width="${f(w * (.7 + .5 * depth))}" stroke-linecap="round" fill="none" opacity="${f(o * op)}"/>`);
    });
    const glowR = s * 1.5;
    const shape = glyph === 'heart'
      ? `<path d="${heartPath(x, y, s * .8)}" fill="url(#body)" stroke="${colors[2]}" stroke-width="1.5"/><path d="${heartPath(x, y + s * .12, s * .55)}" fill="url(#inner)" opacity=".55"/>
         <path d="M${f(x - s * .7)} ${f(y - s * .45)} q${f(s * .2)} ${f(-s * .45)} ${f(s * .55)} ${f(-s * .4)}" stroke="#FFF" stroke-opacity=".9" stroke-width="2" fill="none" stroke-linecap="round"/><circle cx="${f(x + s * .38)}" cy="${f(y + s * .1)}" r="${f(s * .1)}" fill="#FFF" opacity=".6"/>`
      : `<g transform="rotate(${f(deg * 2)} ${f(x)} ${f(y)})"><path d="${star5(x, y, s * 1.05)}" fill="url(#body)" stroke="${colors[2]}" stroke-width="1.5" stroke-linejoin="round"/>
         <path d="${star5(x, y, s * .5)}" fill="url(#inner)" opacity=".6"/></g><circle cx="${f(x - s * .25)}" cy="${f(y - s * .3)}" r="${f(s * .17)}" fill="#FFF" opacity=".85"/>`;
    items.push(`<circle cx="${f(x)}" cy="${f(y)}" r="${f(glowR)}" fill="url(#aura)" opacity="${f(.35 + .45 * depth)}"/><g filter="url(#sh)" opacity="${f(op)}">${shape}</g>`);
  }
  return svg(W, W, `<defs>${shadow('sh', 2, 2, .45)}${blur('ring', 4)}${rad('body', [[0, '#FFFFFF'], [.3, colors[0]], [1, colors[2]]], .38, .3, .8)}
    ${rad('inner', [[0, '#FFFFFF', .9], [1, colors[0], 0]], .4, .35, .7)}${rad('aura', [[0, colors[1], .7], [1, colors[1], 0]])}</defs>
    <circle cx="${C}" cy="${C}" r="${RO}" fill="none" stroke="${colors[1]}" stroke-opacity=".3" stroke-width="6" filter="url(#ring)"/>
    <circle cx="${C}" cy="${C}" r="${RO}" fill="none" stroke="${colors[1]}" stroke-opacity=".45" stroke-width="1.1" stroke-dasharray="1 7" stroke-linecap="round" transform="rotate(${f(t * 360 * 80 / (TAU * RO))} ${C} ${C})"/>
    ${trails.join('')}${items.join('')}`);
};

const sakura = t => {
  const flower = (x, y, s, rot) => `<g transform="translate(${f(x)} ${f(y)}) rotate(${f(rot)}) scale(${f(s)})" filter="url(#sh)">
    ${[0, 72, 144, 216, 288].map(r => `<path d="M0 0 C-9 -6 -10 -18 -4 -22 L0 -19 L4 -22 C10 -18 9 -6 0 0Z" transform="rotate(${r})" fill="url(#petal)" stroke="#E07AA0" stroke-width=".9"/>`).join('')}
    <circle r="4.2" fill="#FFE07A" stroke="#E0A52A" stroke-width="1"/>
    ${[0, 60, 120, 180, 240, 300].map(r => `<line x1="0" y1="0" x2="0" y2="-7.5" transform="rotate(${r})" stroke="#C2185B" stroke-width="1"/><circle cx="0" cy="-7.8" r="1.1" fill="#C2185B" transform="rotate(${r})"/>`).join('')}
  </g>`;
  const leaf = (x, y, rot, s = 1) => `<path d="M0 0 C6 -6 16 -6 22 0 C16 6 6 6 0 0Z" transform="translate(${f(x)} ${f(y)}) rotate(${f(rot)}) scale(${s})" fill="url(#leaf)" stroke="#3E7A3A" stroke-width=".9"/>`;
  const branch = [];
  for (let i = 0; i <= 24; i++) { const a = (100 + i * 5.6) * Math.PI / 180, r = 92 + sin(i / 12) * 3; branch.push(`${i ? 'L' : 'M'}${f(C + Math.cos(a) * r)} ${f(C + Math.sin(a) * r)}`); }
  const spots = [[104, 1.15, 0], [128, .8, 20], [150, 1.25, 40], [176, .85, 10], [200, 1.1, 50], [224, .75, 30]];
  const petals = [0, .33, .66].map((ph, i) => { const p = (t + ph) % 1, x = 150 + i * 22 + sin(p * 1.5 + i) * 10, y = 160 + p * 76; return `<path d="M0 0 C-5 -4 -6 -10 -2 -12 L0 -10 L2 -12 C6 -10 5 -4 0 0Z" transform="translate(${f(x)} ${f(y)}) rotate(${f(p * 360 + i * 70)})" fill="url(#petal)" opacity="${f(Math.min(1, (1 - p) * 2))}"/>`; }).join('');
  return svg(W, W, `<defs>${shadow('sh', 2, 2, .4)}${rad('petal', [[0, '#FFFFFF'], [.55, '#FFD1E2'], [1, '#FF8DB6']], .5, .9, .9)}${lin('leaf', [[0, '#9BE08E'], [1, '#4E9A47']])}${lin('wood', [[0, '#8A5A3C'], [1, '#4E2F1E']])}${rad('bloom', [[0, '#FFC2D9', .45], [1, '#FF8DB6', 0]])}</defs>
    ${spots.filter((_, i) => i % 2 === 0).map(([d], i) => { const [x, y] = at(d, 94); return `<circle cx="${f(x)}" cy="${f(y)}" r="${f(26 + 3 * sin(t + i / 3))}" fill="url(#bloom)"/>`; }).join('')}
    <path d="${branch.join(' ')}" fill="none" stroke="#2E1A10" stroke-width="7.5" stroke-linecap="round" opacity=".5" transform="translate(0 2)"/>
    <path d="${branch.join(' ')}" fill="none" stroke="url(#wood)" stroke-width="6" stroke-linecap="round"/>
    ${spots.map(([d, s], i) => { const [x, y] = at(d, 94); return leaf(x, y, d + 140 + (i % 2 ? 40 : -20), .9); }).join('')}
    ${spots.map(([d, s, r]) => { const [x, y] = at(d, 94); return flower(x, y, s * (1 + .04 * sin(t + d / 100)), r + sin(t) * 6); }).join('')}
    ${[[116, 6], [188, 5]].map(([d, s]) => { const [x, y] = at(d, 100); return `<ellipse cx="${f(x)}" cy="${f(y)}" rx="${s * .8}" ry="${s}" fill="#FF9CC0" stroke="#D85C8C" stroke-width="1"/>`; }).join('')}
    ${petals}`);
};

const pumpkin = t => {
  const flick = .75 + .25 * Math.sin(t * TAU * 3) * Math.sin(t * TAU * 2 + 1);
  const batX = 70 + sin(t) * 12, batY = 40 + cos(t * 2) * 6, flap = Math.sin(t * TAU * 4);
  return svg(W, W, `<defs>${shadow('sh', 3, 3, .5)}${glow('g', 4)}${rad('pk', [[0, '#FFC069'], [.55, '#FF8A1F'], [1, '#B4480A']], .4, .35, .8)}${rad('pk2', [[0, '#F59A3A'], [1, '#9A3A06']], .5, .4, .8)}${rad('fire', [[0, '#FFFBD0'], [.5, '#FFD54A'], [1, '#FF7A00']], .5, .5, .6)}${lin('stem', [[0, '#7FB04A'], [1, '#3D6B22']])}${rad('candle', [[0, '#FFB347', .6], [1, '#FF7A00', 0]])}</defs>
    <circle cx="150" cy="60" r="${f(40 + 4 * flick)}" fill="url(#candle)" opacity="${f(.35 + .3 * flick)}"/>
    <g transform="translate(150 60) rotate(14) scale(.85)" filter="url(#sh)">
      <ellipse cx="-30" cy="4" rx="26" ry="27" fill="url(#pk2)" stroke="#6E2A04" stroke-width="2"/>
      <ellipse cx="30" cy="4" rx="26" ry="27" fill="url(#pk2)" stroke="#6E2A04" stroke-width="2"/>
      <ellipse cx="-14" cy="2" rx="22" ry="29" fill="url(#pk)" stroke="#6E2A04" stroke-width="2"/>
      <ellipse cx="14" cy="2" rx="22" ry="29" fill="url(#pk)" stroke="#6E2A04" stroke-width="2"/>
      <ellipse cx="0" cy="0" rx="18" ry="30" fill="url(#pk)" stroke="#6E2A04" stroke-width="2"/>
      <path d="M-8 -28 C-12 -14 -12 10 -6 24" stroke="#FFE0B0" stroke-opacity=".55" stroke-width="2.6" fill="none" stroke-linecap="round"/>
      <path d="M-26 -4 L-16 -16 L-8 -4Z M8 -4 L16 -16 L26 -4Z M-24 8 Q0 26 24 8 L18 10 L14 6 L9 12 L4 7 L0 13 L-4 7 L-9 12 L-14 6 L-18 10Z" fill="#3A1402" stroke="#3A1402" stroke-width="3" stroke-linejoin="round"/>
      <g opacity="${f(flick)}" filter="url(#g)">
        <path d="M-26 -4 L-16 -16 L-8 -4Z M8 -4 L16 -16 L26 -4Z" fill="url(#fire)"/>
        <path d="M-24 8 Q0 26 24 8 L18 10 L14 6 L9 12 L4 7 L0 13 L-4 7 L-9 12 L-14 6 L-18 10Z" fill="url(#fire)"/>
      </g>
      <path d="M0 -28 C-2 -38 2 -46 10 -50" stroke="url(#stem)" stroke-width="7" fill="none" stroke-linecap="round"/>
      <path d="M8 -46 C20 -58 34 -52 34 -42 C24 -42 16 -40 8 -46Z" fill="#6CBF4A" stroke="#2F5E1C" stroke-width="1.5"/>
      <path d="M-2 -36 C-14 -44 -22 -38 -18 -30 C-14 -24 -6 -30 -10 -34" fill="none" stroke="#5E9E3A" stroke-width="2" stroke-linecap="round"/>
    </g>
    <g transform="translate(${f(batX)} ${f(batY)})" filter="url(#sh)">
      <path d="M0 0 Q-6 ${f(-6 - flap * 6)} -16 ${f(-3 - flap * 8)} Q-12 2 -13 6 Q-7 3 -4 7 Q0 4 4 7 Q7 3 13 6 Q12 2 16 ${f(-3 - flap * 8)} Q6 ${f(-6 - flap * 6)} 0 0Z" fill="#2A1638" stroke="#120818" stroke-width="1.2"/>
      <circle cx="-2" cy="1" r="1.2" fill="#FFB347"/><circle cx="2" cy="1" r="1.2" fill="#FFB347"/>
    </g>`);
};

// ───── Набор администрации: «Хранитель ночи» — знак статуса, а не праздник ─────
// Тонкий лунно-белый ореол вокруг аватара с разрывом сверху, в разрыве — строгий серебряный полумесяц.
// Две короткие засечки по бокам, внизу — маленький ромб: единственная деталь цвета зари.
// Движение одно: блик на кончике полумесяца раз в цикл (~6 с) медленно проступает и гаснет, остальное статично.
const arcPath = (a0, a1, r) => { const [x0, y0] = at(a0, r), [x1, y1] = at(a1, r); return `M${f(x0)} ${f(y0)} A${r} ${r} 0 ${Math.abs(a1 - a0) > 180 ? 1 : 0} 1 ${f(x1)} ${f(y1)}`; };
const KEEPER_FRAMES = 48, KEEPER_FPS = 8;
const nightKeeper = t => {
  const RH = 102, gap = 14, mx = C, my = C - RH, mr = 12.5;
  // Блик: плавный подъём и спад в середине цикла, в остальное время его нет.
  const glint = Math.pow(Math.max(0, Math.sin((t - .25) * 2 * Math.PI)), 3);
  const ticks = [0, 180].map(deg => { const [x0, y0] = at(deg, RH - 6), [x1, y1] = at(deg, RH + 6); return `<path d="M${f(x0)} ${f(y0)} L${f(x1)} ${f(y1)}"/>`; }).join('');
  const [dx, dy] = at(90, RH), d = 5.2;
  return svg(W, W, `<defs>${shadow('sh', .8, 1.1, .55)}
    ${lin('silver', [[0, '#F4F7FC'], [.55, '#DCE3F0'], [1, '#9AA4B6']], 0, 0, 1, 1)}
    ${lin('dawn', [[0, '#FF8A3D'], [1, '#FF4D67']], 0, 0, 1, 1)}
    ${rad('spark', [[0, '#FFFFFF', .9], [.4, '#DCE3F0', .3], [1, '#DCE3F0', 0]])}
    <mask id="crescent"><rect width="${W}" height="${W}" fill="#FFF"/><circle cx="${f(mx + 6)}" cy="${f(my - 3.4)}" r="${f(mr * .84)}" fill="#000"/></mask></defs>
    <g filter="url(#sh)">
      <path d="${arcPath(-90 + gap, 270 - gap, RH)}" stroke="#DCE3F0" stroke-opacity=".5" stroke-width="2" fill="none" stroke-linecap="round"/>
      <g stroke="#DCE3F0" stroke-opacity=".8" stroke-width="2" stroke-linecap="round">${ticks}</g>
      <g mask="url(#crescent)" transform="rotate(-24 ${mx} ${my})"><circle cx="${mx}" cy="${my}" r="${mr}" fill="url(#silver)"/></g>
      <path d="M${f(dx)} ${f(dy - d)} L${f(dx + d * .72)} ${f(dy)} L${f(dx)} ${f(dy + d)} L${f(dx - d * .72)} ${f(dy)}Z" fill="url(#dawn)"/>
    </g>
    ${glint > .01 ? `<circle cx="${f(mx - 8.4)}" cy="${f(my - 6.6)}" r="${f(6.5 * glint + 1)}" fill="url(#spark)" opacity="${f(glint)}"/>${sparkle(mx - 8.4, my - 6.6, 4.6 * glint + .8, '#FFFFFF', .9 * glint)}` : ''}`);
};

export default [
  { name: 'cat_ears', w: W, h: W, frames: N, fps: FPS, draw: catEars },
  { name: 'bunny', w: W, h: W, frames: N, fps: FPS, draw: bunny },
  { name: 'halo', w: W, h: W, frames: N, fps: FPS, draw: halo },
  { name: 'horns', w: W, h: W, frames: N, fps: FPS, draw: horns },
  { name: 'headphones', w: W, h: W, frames: N, fps: FPS, draw: headphones },
  { name: 'crown', w: W, h: W, frames: N, fps: FPS, draw: crown },
  { name: 'orbit_hearts', w: W, h: W, frames: N / 2, fps: FPS, draw: orbit('heart', ['#FF6A92', '#FFB3C8', '#B8204A']) },
  { name: 'orbit_stars', w: W, h: W, frames: N / 2, fps: FPS, draw: orbit('star', ['#FFE07A', '#9FD8FF', '#C98A12']) },
  { name: 'sakura_wreath', w: W, h: W, frames: N, fps: FPS, draw: sakura },
  { name: 'pumpkin', w: W, h: W, frames: N, fps: FPS, draw: pumpkin },
  { name: 'night_keeper', w: W, h: W, frames: KEEPER_FRAMES, fps: KEEPER_FPS, draw: nightKeeper },
];
