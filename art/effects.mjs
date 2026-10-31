// Частицы эффектов профиля. Кадры листа — это варианты частицы (или кадры анимации для летучей мыши);
// клиент сам расставляет, двигает, масштабирует и поворачивает их поверх карточки.
import { TAU, f, shadow, glow, blur, lin, rad, sparkle, heartPath, svg } from './lib.mjs';

const sparkles = (t, i) => {
  const S = 64, c = S / 2;
  const tone = [['#FFFFFF', '#FFE9A8'], ['#FFF7D6', '#FFC94A'], ['#FFE3F0', '#FF8FB8'], ['#E6F6FF', '#8FD3FF']][i];
  return svg(S, S, `<defs>${rad('halo', [[0, tone[1], .9], [.35, tone[1], .35], [1, tone[1], 0]])}${blur('b', 1.2)}</defs>
    <circle cx="${c}" cy="${c}" r="${c}" fill="url(#halo)"/>
    ${sparkle(c, c, 26, tone[1], .9).replace('/>', ' filter="url(#b)"/>')}
    ${sparkle(c, c, 22, tone[0])}
    ${i % 2 ? `<g transform="rotate(45 ${c} ${c})">${sparkle(c, c, 11, tone[0], .9)}</g>` : ''}
    <circle cx="${c}" cy="${c}" r="3.2" fill="#FFFFFF"/>`);
};

const bubbles = (t, i) => {
  const S = 72, c = S / 2, r = 30;
  const tint = [['#9FD8FF', '#FFB3E6'], ['#B6FFE0', '#9FB8FF'], ['#FFE0A8', '#C7A8FF']][i];
  return svg(S, S, `<defs>${rad('film', [[0, '#FFFFFF', 0], [.72, tint[0], .05], [.9, tint[1], .45], [1, tint[0], .9]])}${lin('shine', [[0, '#FFFFFF', .95], [1, '#FFFFFF', 0]])}</defs>
    <circle cx="${c}" cy="${c}" r="${r}" fill="url(#film)"/>
    <circle cx="${c}" cy="${c}" r="${r}" fill="none" stroke="${tint[0]}" stroke-opacity=".85" stroke-width="1.6"/>
    <path d="M${c - 20} ${c - 8} A22 22 0 0 1 ${c - 4} ${c - 22}" stroke="url(#shine)" stroke-width="5" fill="none" stroke-linecap="round"/>
    <ellipse cx="${c + 14}" cy="${c + 14}" rx="5" ry="3" transform="rotate(-40 ${c + 14} ${c + 14})" fill="#FFFFFF" opacity=".55"/>`);
};

const petals = (t, i) => {
  const S = 48, c = S / 2;
  const tone = [['#FFFFFF', '#FFC2D9', '#F37AA6'], ['#FFF5F9', '#FFB0CC', '#E35C8E'], ['#FFFFFF', '#FFD9E8', '#FF9CC0'], ['#FFF0F6', '#F9A3C4', '#D94D80']][i];
  const curl = [0, 6, -5, 3][i];
  return svg(S, S, `<defs>${rad('p', [[0, tone[0]], [.6, tone[1]], [1, tone[2]]], .5, .85, .9)}${shadow('sh', 1.5, 1.2, .35)}</defs>
    <g transform="rotate(${[0, 25, -20, 40][i]} ${c} ${c})" filter="url(#sh)">
      <path d="M${c} ${c + 17} C${c - 16} ${c + 6} ${c - 15 + curl} ${c - 12} ${c - 4} ${c - 17} L${c} ${c - 12} L${c + 4} ${c - 17} C${c + 15 + curl} ${c - 12} ${c + 16} ${c + 6} ${c} ${c + 17}Z" fill="url(#p)" stroke="${tone[2]}" stroke-width=".8"/>
      <path d="M${c} ${c + 14} C${c - 2} ${c + 2} ${c - 1} ${c - 6} ${c} ${c - 10}" stroke="#FFFFFF" stroke-opacity=".6" stroke-width="1.4" fill="none" stroke-linecap="round"/>
    </g>`);
};

const fireflies = (t, i) => {
  const S = 64, c = S / 2, col = ['#FFE47A', '#C6FF8A'][i];
  return svg(S, S, `<defs>${rad('g', [[0, '#FFFFFF', 1], [.12, col, 1], [.4, col, .35], [1, col, 0]])}</defs><circle cx="${c}" cy="${c}" r="${c}" fill="url(#g)"/>`);
};

const hearts = (t, i) => {
  const S = 56, c = S / 2 + 2, col = [['#FF6A92', '#B8204A'], ['#FF9EBB', '#D9487A'], ['#FF4D67', '#8E0F2C']][i];
  return svg(S, S, `<defs>${rad('h', [[0, '#FFFFFF'], [.28, col[0]], [1, col[1]]], .35, .3, .85)}${shadow('sh', 2, 1.6, .4)}</defs>
    <g filter="url(#sh)"><path d="${heartPath(S / 2, c - 2, 16)}" fill="url(#h)" stroke="${col[1]}" stroke-width="1.4"/>
    <path d="M${S / 2 - 15} ${c - 10} q3 -9 11 -8" stroke="#FFFFFF" stroke-opacity=".85" stroke-width="3" fill="none" stroke-linecap="round"/></g>`);
};

const starfall = () => {
  const W = 200, H = 48, y = H / 2;
  return svg(W, H, `<defs>${lin('trail', [[0, '#9FD8FF', 0], [.7, '#CFEFFF', .55], [1, '#FFFFFF', 1]], 0, 0, 1, 0)}${blur('b', 2.4)}${rad('head', [[0, '#FFFFFF'], [.35, '#FFF6D0', .9], [1, '#FFE08A', 0]])}</defs>
    <path d="M6 ${y} L${W - 22} ${y - 4} L${W - 22} ${y + 4}Z" fill="url(#trail)" filter="url(#b)"/>
    <path d="M40 ${y} L${W - 22} ${y - 1.6} L${W - 22} ${y + 1.6}Z" fill="url(#trail)"/>
    <circle cx="${W - 22}" cy="${y}" r="20" fill="url(#head)"/>
    ${sparkle(W - 22, y, 14, '#FFFFFF')}`);
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

const snow = (t, i) => {
  const S = 48, c = S / 2, arms = [];
  for (let k = 0; k < 6; k++) {
    const r = k * 60;
    arms.push(`<g transform="rotate(${r} ${c} ${c})"><line x1="${c}" y1="${c}" x2="${c}" y2="${c - 18}" stroke="#FFFFFF" stroke-width="2.2" stroke-linecap="round"/>
      ${i % 2 ? `<path d="M${c - 5} ${c - 12} L${c} ${c - 8} L${c + 5} ${c - 12}" stroke="#E6F6FF" stroke-width="1.8" fill="none" stroke-linecap="round"/>` : `<path d="M${c - 4} ${c - 14} L${c} ${c - 10} L${c + 4} ${c - 14} M${c - 3} ${c - 7} L${c} ${c - 4} L${c + 3} ${c - 7}" stroke="#E6F6FF" stroke-width="1.6" fill="none" stroke-linecap="round"/>`}</g>`);
  }
  return svg(S, S, `<defs>${rad('g', [[0, '#CFEBFF', .7], [1, '#CFEBFF', 0]])}${glow('gl', 1.2)}</defs><circle cx="${c}" cy="${c}" r="${c}" fill="url(#g)"/><g filter="url(#gl)" transform="translate(${c} ${c}) rotate(${i * 15}) scale(${[1, .85, .7, .95][i]}) translate(${-c} ${-c})">${arms.join('')}<circle cx="${c}" cy="${c}" r="3" fill="#FFFFFF"/></g>`);
};

export default [
  { name: 'fx_sparkle', w: 64, h: 64, frames: 4, draw: sparkles },
  { name: 'fx_bubble', w: 72, h: 72, frames: 3, draw: bubbles },
  { name: 'fx_petal', w: 48, h: 48, frames: 4, draw: petals },
  { name: 'fx_firefly', w: 64, h: 64, frames: 2, draw: fireflies },
  { name: 'fx_heart', w: 56, h: 56, frames: 3, draw: hearts },
  { name: 'fx_starfall', w: 200, h: 48, frames: 1, draw: starfall },
  { name: 'fx_bat', w: 104, h: 72, frames: 8, fps: 16, draw: bats },
  { name: 'fx_snow', w: 48, h: 48, frames: 4, draw: snow },
];
