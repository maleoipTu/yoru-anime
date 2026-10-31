// Частицы эффектов профиля. Кадры листа — это варианты частицы (или кадры анимации для летучей мыши);
// клиент сам расставляет, двигает, масштабирует и поворачивает их поверх карточки.
import { TAU, f, shadow, glow, blur, lin, rad, sparkle, heartPath, svg } from './lib.mjs';

// Искры: шесть вариантов — четырёх- и восьмилучевые, с кольцом-бликом и цветной дифракцией.
const sparkles = (t, i) => {
  const S = 64, c = S / 2;
  const tone = [['#FFFFFF', '#FFE9A8'], ['#FFF7D6', '#FFC94A'], ['#FFE3F0', '#FF8FB8'], ['#E6F6FF', '#8FD3FF'], ['#FFFFFF', '#C7A8FF'], ['#FFF3E0', '#FF9A55']][i];
  const ring = i === 3 || i === 4 ? `<circle cx="${c}" cy="${c}" r="15" fill="none" stroke="${tone[1]}" stroke-opacity=".45" stroke-width="1.2"/><circle cx="${c}" cy="${c}" r="21" fill="none" stroke="${tone[1]}" stroke-opacity=".18" stroke-width="2.4" filter="url(#b)"/>` : '';
  const eight = i % 2 ? `<g transform="rotate(45 ${c} ${c})">${sparkle(c, c, 12, tone[0], .9)}</g>` : '';
  const prism = i === 5 ? `<path d="M${c - 26} ${c} L${c + 26} ${c}" stroke="url(#prism)" stroke-width="1.6" stroke-linecap="round" opacity=".8"/>` : '';
  return svg(S, S, `<defs>${rad('halo', [[0, tone[1], .9], [.3, tone[1], .35], [1, tone[1], 0]])}${blur('b', 1.2)}${lin('prism', [[0, '#FF6A8E', 0], [.3, '#FFB36B'], [.5, '#FFFFFF'], [.7, '#8FD3FF'], [1, '#C7A8FF', 0]], 0, 0, 1, 0)}</defs>
    <circle cx="${c}" cy="${c}" r="${c}" fill="url(#halo)"/>${ring}${prism}
    ${sparkle(c, c, 27, tone[1], .85).replace('/>', ' filter="url(#b)"/>')}
    ${sparkle(c, c, 22, tone[0])}${eight}
    <circle cx="${c}" cy="${c}" r="3.4" fill="#FFFFFF"/>`);
};

// Пузыри: радужная плёнка (переливы по краю), блик-окно и отражённый блик снизу.
const bubbles = (t, i) => {
  const S = 72, c = S / 2, r = 30;
  const tint = [['#9FD8FF', '#FFB3E6', '#B6FFE0'], ['#B6FFE0', '#9FB8FF', '#FFE0A8'], ['#FFE0A8', '#C7A8FF', '#9FD8FF'], ['#FFB3E6', '#9FD8FF', '#FFF2A8']][i];
  return svg(S, S, `<defs>${rad('film', [[0, '#FFFFFF', 0], [.68, tint[0], .04], [.86, tint[1], .3], [.95, tint[2], .6], [1, tint[0], .95]])}
    ${lin('iris', [[0, tint[0], .0], [.3, tint[1], .55], [.6, tint[2], .45], [1, tint[0], 0]], 0, 0, 1, 1)}${lin('shine', [[0, '#FFFFFF', .95], [1, '#FFFFFF', 0]])}${blur('b', 1)}</defs>
    <circle cx="${c}" cy="${c}" r="${r}" fill="url(#film)"/>
    <path d="M${c - 24} ${c + 10} A26 26 0 0 0 ${c + 18} ${c + 20}" stroke="url(#iris)" stroke-width="5" fill="none" stroke-linecap="round" filter="url(#b)"/>
    <circle cx="${c}" cy="${c}" r="${r}" fill="none" stroke="${tint[0]}" stroke-opacity=".85" stroke-width="1.5"/>
    <path d="M${c - 20} ${c - 8} A22 22 0 0 1 ${c - 4} ${c - 22}" stroke="url(#shine)" stroke-width="5" fill="none" stroke-linecap="round"/>
    <rect x="${c - 15}" y="${c - 17}" width="7" height="6" rx="1.5" fill="#FFFFFF" opacity=".55" transform="rotate(-30 ${c - 12} ${c - 14})"/>
    <ellipse cx="${c + 14}" cy="${c + 14}" rx="5" ry="3" transform="rotate(-40 ${c + 14} ${c + 14})" fill="#FFFFFF" opacity=".5"/>`);
};

// Лепестки: шесть форм с прожилкой, загибом и светлым краем; часть повёрнута «ребром» (уже).
const petals = (t, i) => {
  const S = 48, c = S / 2;
  const tone = [['#FFFFFF', '#FFC2D9', '#F37AA6'], ['#FFF5F9', '#FFB0CC', '#E35C8E'], ['#FFFFFF', '#FFD9E8', '#FF9CC0'], ['#FFF0F6', '#F9A3C4', '#D94D80'], ['#FFFFFF', '#FFE3EE', '#FFB0CC'], ['#FFF5F9', '#FFA3C2', '#C93F72']][i];
  const curl = [0, 6, -5, 3, -2, 5][i], squash = [1, 1, .9, .8, .62, .75][i];
  return svg(S, S, `<defs>${rad('p', [[0, tone[0]], [.6, tone[1]], [1, tone[2]]], .5, .85, .9)}${shadow('sh', 1.5, 1.2, .35)}${lin('edge', [[0, '#FFFFFF', .8], [1, '#FFFFFF', 0]], 0, 0, 1, 1)}</defs>
    <g transform="rotate(${[0, 25, -20, 40, 70, -50][i]} ${c} ${c}) translate(${c} ${c}) scale(${squash} 1) translate(${-c} ${-c})" filter="url(#sh)">
      <path d="M${c} ${c + 17} C${c - 16} ${c + 6} ${c - 15 + curl} ${c - 12} ${c - 4} ${c - 17} L${c} ${c - 12} L${c + 4} ${c - 17} C${c + 15 + curl} ${c - 12} ${c + 16} ${c + 6} ${c} ${c + 17}Z" fill="url(#p)" stroke="${tone[2]}" stroke-width=".8"/>
      <path d="M${c - 12} ${c + 2} C${c - 12} ${c - 6} ${c - 9} ${c - 13} ${c - 4} ${c - 15}" stroke="url(#edge)" stroke-width="1.6" fill="none" stroke-linecap="round"/>
      <path d="M${c} ${c + 14} C${c - 2} ${c + 2} ${c - 1} ${c - 6} ${c} ${c - 10}" stroke="${tone[2]}" stroke-opacity=".35" stroke-width="1" fill="none" stroke-linecap="round"/>
      <path d="M${c + 1} ${c + 13} C${c - 1} ${c + 2} ${c} ${c - 6} ${c + 1} ${c - 10}" stroke="#FFFFFF" stroke-opacity=".55" stroke-width="1.2" fill="none" stroke-linecap="round"/>
    </g>`);
};

// Светлячки: яркое ядро, тёплый ореол и слабое кольцо боке; три оттенка.
const fireflies = (t, i) => {
  const S = 64, c = S / 2, col = ['#FFE47A', '#C6FF8A', '#FFB86A'][i];
  return svg(S, S, `<defs>${rad('g', [[0, '#FFFFFF', 1], [.1, col, 1], [.32, col, .38], [.7, col, .08], [1, col, 0]])}</defs>
    <circle cx="${c}" cy="${c}" r="${c}" fill="url(#g)"/><circle cx="${c}" cy="${c}" r="${c - 9}" fill="none" stroke="${col}" stroke-opacity=".12" stroke-width="2"/>`);
};

// Сердечки: глянцевые, с внутренним свечением; четвёртый — с искрой.
const hearts = (t, i) => {
  const S = 56, c = S / 2 + 2, col = [['#FF6A92', '#B8204A'], ['#FF9EBB', '#D9487A'], ['#FF4D67', '#8E0F2C'], ['#FF8FB8', '#C2185B']][i];
  return svg(S, S, `<defs>${rad('h', [[0, '#FFFFFF'], [.28, col[0]], [1, col[1]]], .35, .3, .85)}${rad('in', [[0, '#FFFFFF', .7], [1, col[0], 0]], .5, .45, .55)}${rad('aura', [[0, col[0], .45], [1, col[0], 0]])}${shadow('sh', 2, 1.6, .4)}</defs>
    <circle cx="${S / 2}" cy="${c - 2}" r="${S / 2 - 1}" fill="url(#aura)"/>
    <g filter="url(#sh)"><path d="${heartPath(S / 2, c - 2, 16)}" fill="url(#h)" stroke="${col[1]}" stroke-width="1.4"/>
    <path d="${heartPath(S / 2, c, 10)}" fill="url(#in)" opacity=".7"/>
    <path d="M${S / 2 - 15} ${c - 10} q3 -9 11 -8" stroke="#FFFFFF" stroke-opacity=".9" stroke-width="3" fill="none" stroke-linecap="round"/>
    <circle cx="${S / 2 + 9}" cy="${c + 2}" r="1.6" fill="#FFF" opacity=".6"/></g>
    ${i === 3 ? sparkle(S / 2 + 12, c - 14, 7, '#FFFFFF') : ''}`);
};

// Падающая звезда: двойной хвост (холодный снаружи, тёплый внутри) и пыль из искр.
const starfall = () => {
  const W = 200, H = 48, y = H / 2;
  const dust = [[52, -2, 1.1, .35], [84, 3, 1.4, .5], [112, -4, 1, .45], [138, 2, 1.6, .7], [160, -1, 1.2, .8]].map(([x, dy, r, o]) => sparkle(x, y + dy, r * 2.6, '#E6F6FF', o)).join('');
  return svg(W, H, `<defs>${lin('trail', [[0, '#9FD8FF', 0], [.7, '#CFEFFF', .55], [1, '#FFFFFF', 1]], 0, 0, 1, 0)}${lin('warm', [[0, '#FFE08A', 0], [.75, '#FFE08A', .6], [1, '#FFFFFF', 1]], 0, 0, 1, 0)}${blur('b', 2.6)}${rad('head', [[0, '#FFFFFF'], [.35, '#FFF6D0', .9], [1, '#FFE08A', 0]])}</defs>
    <path d="M4 ${y} L${W - 22} ${y - 5} L${W - 22} ${y + 5}Z" fill="url(#trail)" filter="url(#b)"/>
    <path d="M36 ${y} L${W - 22} ${y - 1.8} L${W - 22} ${y + 1.8}Z" fill="url(#trail)"/>
    <path d="M90 ${y} L${W - 22} ${y - .9} L${W - 22} ${y + .9}Z" fill="url(#warm)"/>
    ${dust}
    <circle cx="${W - 22}" cy="${y}" r="21" fill="url(#head)"/>
    ${sparkle(W - 22, y, 14, '#FFFFFF')}<g transform="rotate(45 ${W - 22} ${y})">${sparkle(W - 22, y, 7, '#FFF6D0', .85)}</g>`);
};

const bats = (t, i) => {
  const W = 104, H = 72, x = W / 2, y = 36, s = 34;
  const flap = Math.sin(t * TAU), lift = s * .55 * flap, fold = 1 - .25 * Math.max(0, -flap);
  const wing = side => { const k = side; return `M${x} ${f(y - 2)} Q${f(x + k * s * .45)} ${f(y - s * .55 - lift)} ${f(x + k * s * 1.35 * fold)} ${f(y - s * .32 - lift)} Q${f(x + k * s * 1.12 * fold)} ${f(y - s * .02)} ${f(x + k * s * 1.18 * fold)} ${f(y + s * .2 - lift * .2)} Q${f(x + k * s * .9)} ${f(y + s * .02)} ${f(x + k * s * .72)} ${f(y + s * .22 - lift * .1)} Q${f(x + k * s * .5)} ${f(y + s * .04)} ${f(x + k * s * .34)} ${f(y + s * .24)} Q${f(x + k * s * .2)} ${f(y + s * .1)} ${x} ${f(y + 4)}Z`; };
  return svg(W, H, `<defs>${lin('w', [[0, '#5B3A7A'], [1, '#1E1030']])}${rad('b', [[0, '#6E4A90'], [1, '#22122F']], .4, .3, .8)}${glow('eye', 1.6)}${shadow('sh', 2, 2, .5)}</defs>
    <g filter="url(#sh)">
      <path d="${wing(-1)}" fill="url(#w)" stroke="#0E0616" stroke-width="1.4" stroke-linejoin="round"/>
      <path d="${wing(1)}" fill="url(#w)" stroke="#0E0616" stroke-width="1.4" stroke-linejoin="round"/>
      <ellipse cx="${x}" cy="${y + 4}" rx="8" ry="11" fill="url(#b)" stroke="#0E0616" stroke-width="1.4"/>
      <path d="M${x - 6} ${y - 4} L${x - 7} ${y - 12} L${x - 2} ${y - 6}Z M${x + 6} ${y - 4} L${x + 7} ${y - 12} L${x + 2} ${y - 6}Z" fill="#2A173B" stroke="#0E0616" stroke-width="1.2"/>
      <circle cx="${x - 3}" cy="${y}" r="1.9" fill="#FFB347" filter="url(#eye)"/><circle cx="${x + 3}" cy="${y}" r="1.9" fill="#FFB347" filter="url(#eye)"/>
      <path d="M${x - 2} ${y + 5} l1 2 l1 -2 M${x + 2} ${y + 5}" stroke="#FFFFFF" stroke-width=".9" fill="none"/>
    </g>`);
};

// Снежинки: шесть разных кристаллов — веточки, пластинка, звезда; ледяной ореол.
const snow = (t, i) => {
  const S = 48, c = S / 2, arms = [];
  const branch = [
    `<path d="M${c - 4} ${c - 14} L${c} ${c - 10} L${c + 4} ${c - 14} M${c - 3} ${c - 7} L${c} ${c - 4} L${c + 3} ${c - 7}" stroke="#E6F6FF" stroke-width="1.6" fill="none" stroke-linecap="round"/>`,
    `<path d="M${c - 5} ${c - 12} L${c} ${c - 8} L${c + 5} ${c - 12}" stroke="#E6F6FF" stroke-width="1.8" fill="none" stroke-linecap="round"/>`,
    `<path d="M${c} ${c - 19} L${c - 3} ${c - 15} L${c} ${c - 11} L${c + 3} ${c - 15}Z" fill="#E6F6FF" opacity=".9"/>`,
    `<path d="M${c - 5} ${c - 15} L${c} ${c - 12} L${c + 5} ${c - 15} M${c - 6} ${c - 9} L${c} ${c - 5} L${c + 6} ${c - 9}" stroke="#E6F6FF" stroke-width="1.3" fill="none" stroke-linecap="round"/><circle cx="${c}" cy="${c - 19}" r="1.6" fill="#FFF"/>`,
    `<path d="M${c - 4} ${c - 8} L${c} ${c - 13} L${c + 4} ${c - 8}" stroke="#E6F6FF" stroke-width="1.5" fill="none" stroke-linecap="round"/>`,
    `<path d="M${c - 3} ${c - 16} L${c} ${c - 13} L${c + 3} ${c - 16} M${c - 4} ${c - 10} L${c} ${c - 7} L${c + 4} ${c - 10}" stroke="#E6F6FF" stroke-width="1.4" fill="none" stroke-linecap="round"/>`,
  ][i];
  for (let k = 0; k < 6; k++) arms.push(`<g transform="rotate(${k * 60} ${c} ${c})"><line x1="${c}" y1="${c}" x2="${c}" y2="${c - 18}" stroke="#FFFFFF" stroke-width="${i === 2 ? 1.6 : 2.2}" stroke-linecap="round"/>${branch}</g>`);
  const plate = i === 2 ? `<path d="M${c} ${c - 7} L${c + 6} ${c - 3.5} L${c + 6} ${c + 3.5} L${c} ${c + 7} L${c - 6} ${c + 3.5} L${c - 6} ${c - 3.5}Z" fill="#DDF0FF" fill-opacity=".55" stroke="#FFFFFF" stroke-width="1"/>` : `<circle cx="${c}" cy="${c}" r="3" fill="#FFFFFF"/>`;
  return svg(S, S, `<defs>${rad('g', [[0, '#CFEBFF', .7], [1, '#CFEBFF', 0]])}${glow('gl', 1.2)}</defs><circle cx="${c}" cy="${c}" r="${c}" fill="url(#g)"/><g filter="url(#gl)" transform="translate(${c} ${c}) rotate(${i * 15}) scale(${[1, .85, .8, .95, .7, .9][i]}) translate(${-c} ${-c})">${arms.join('')}${plate}</g>`);
};

// ───── Набор администрации: «Ночной дозор» — строгое ночное небо ─────
// Варианты частиц (все холодные, лунно-белые #DCE3F0): 0 — звезда с тонким крестом лучей, 1 — точка с мягким ореолом,
// 2 — совсем мелкая точка. Клиент рисует их редко, мелко и тускло.
const night = (t, i) => {
  const S = 32, c = S / 2;
  const body = [
    () => `${rad('h', [[0, '#DCE3F0', .55], [.35, '#DCE3F0', .12], [1, '#DCE3F0', 0]])}</defs><circle cx="${c}" cy="${c}" r="${c}" fill="url(#h)"/>
      <path d="M${c} ${c - 12} L${c} ${c + 12} M${c - 12} ${c} L${c + 12} ${c}" stroke="url(#ray)" stroke-width="1" stroke-linecap="round"/><circle cx="${c}" cy="${c}" r="1.9" fill="#F4F7FC"/>`,
    () => `${rad('h', [[0, '#F4F7FC', .9], [.18, '#DCE3F0', .45], [.5, '#DCE3F0', .08], [1, '#DCE3F0', 0]])}</defs><circle cx="${c}" cy="${c}" r="${c}" fill="url(#h)"/><circle cx="${c}" cy="${c}" r="1.6" fill="#F4F7FC"/>`,
    () => `</defs><circle cx="${c}" cy="${c}" r="1.4" fill="#DCE3F0"/>`,
  ][i];
  return svg(S, S, `<defs>${rad('ray', [[0, '#F4F7FC', .9], [1, '#DCE3F0', 0]], .5, .5, .5)}${body()}`);
};

// Редкая комета: тонкая линия, гаснущая к хвосту, и маленькая головка — без пыли и свечения.
const nightComet = () => {
  const W = 200, H = 12, y = H / 2, hx = W - 8;
  return svg(W, H, `<defs>${lin('tail', [[0, '#DCE3F0', 0], [.7, '#DCE3F0', .35], [1, '#F4F7FC', .9]], 0, 0, 1, 0)}${rad('head', [[0, '#FFFFFF'], [.4, '#DCE3F0', .5], [1, '#DCE3F0', 0]])}</defs>
    <path d="M6 ${y} L${hx} ${y - .9} L${hx} ${y + .9}Z" fill="url(#tail)"/>
    <circle cx="${hx}" cy="${y}" r="5" fill="url(#head)"/><circle cx="${hx}" cy="${y}" r="1.4" fill="#FFFFFF"/>`);
};

export default [
  { name: 'fx_sparkle', w: 64, h: 64, frames: 6, draw: sparkles },
  { name: 'fx_bubble', w: 72, h: 72, frames: 4, draw: bubbles },
  { name: 'fx_petal', w: 48, h: 48, frames: 6, draw: petals },
  { name: 'fx_firefly', w: 64, h: 64, frames: 3, draw: fireflies },
  { name: 'fx_heart', w: 56, h: 56, frames: 4, draw: hearts },
  { name: 'fx_starfall', w: 200, h: 48, frames: 1, draw: starfall },
  { name: 'fx_bat', w: 104, h: 72, frames: 16, fps: 30, draw: bats },
  { name: 'fx_snow', w: 48, h: 48, frames: 6, draw: snow },
  { name: 'fx_night', w: 32, h: 32, frames: 3, draw: night },
  { name: 'fx_night_comet', w: 200, h: 12, frames: 1, draw: nightComet },
];
