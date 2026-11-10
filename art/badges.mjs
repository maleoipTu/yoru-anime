// Значки рядом с ником: холст 96×96, один кадр. Рисованные эмалевые значки вместо символов шрифта —
// одинаково выглядят на Android и Windows и читаются в размере 15–40 px.
import { f, shadow, lin, rad, sparkle, star5, heartPath, svg } from './lib.mjs';
const S = 96, C = 48;
const base = (defs, body) => svg(S, S, `<defs>${shadow('sh', 2, 2, .5)}${defs}</defs><g filter="url(#sh)">${body}</g>`);
/** Блик: мягкая белая дуга слева сверху. */
const gloss = (d, op = .55, w = 4) => `<path d="${d}" fill="none" stroke="#FFFFFF" stroke-opacity="${op}" stroke-width="${w}" stroke-linecap="round"/>`;

const moon = (a, b, edge, extra = '') => () => base(lin('g', [[0, a], [1, b]], 0, 0, 1, 1),
  `<path d="M62 10 A38 38 0 1 0 86 62 A30 30 0 1 1 62 10Z" fill="url(#g)" stroke="${edge}" stroke-width="3.5" stroke-linejoin="round"/>
   ${gloss('M26 30 A30 30 0 0 0 22 56', .6)}
   <circle cx="34" cy="62" r="4" fill="${edge}" opacity=".28"/><circle cx="46" cy="74" r="2.6" fill="${edge}" opacity=".28"/>${extra}`);

const star = () => base(lin('g', [[0, '#FFF3B0'], [.5, '#FFD15C'], [1, '#F0992B']]),
  `<path d="${star5(C, 50, 40, .5)}" fill="url(#g)" stroke="#9A5A0C" stroke-width="3.5" stroke-linejoin="round"/>
   <path d="${star5(C, 50, 22, .5)}" fill="#FFF7CF" opacity=".55"/>
   ${gloss('M30 40 L42 36', .8, 3.5)}${sparkle(76, 20, 8, '#FFFFFF', .95)}`);

const sakura = () => {
  const petal = i => `<path transform="rotate(${i * 72} ${C} ${C})" d="M48 46 C34 36 32 18 44 10 L48 16 L52 10 C64 18 62 36 48 46Z" fill="url(#g)" stroke="#C2577F" stroke-width="2.6" stroke-linejoin="round"/>`;
  return base(rad('g', [[0, '#FFFFFF'], [.55, '#FFC4DC'], [1, '#FF8FB8']], .5, .9, .9),
    `${[0, 1, 2, 3, 4].map(petal).join('')}<circle cx="${C}" cy="${C}" r="7" fill="#FFD76A" stroke="#C2577F" stroke-width="2"/>
     ${[0, 1, 2, 3, 4].map(i => `<circle transform="rotate(${i * 72 + 36} ${C} ${C})" cx="48" cy="36" r="1.8" fill="#D9487A"/>`).join('')}`);
};

const spark = () => base(lin('g', [[0, '#E6F7FF'], [.5, '#7CCBFF'], [1, '#3E7BFA']]),
  `<path d="M48 6 Q54 42 90 48 Q54 54 48 90 Q42 54 6 48 Q42 42 48 6Z" fill="url(#g)" stroke="#1F4FB8" stroke-width="3.5" stroke-linejoin="round"/>
   <path d="M48 24 Q51 45 72 48 Q51 51 48 72 Q45 51 24 48 Q45 45 48 24Z" fill="#FFFFFF" opacity=".7"/>
   ${sparkle(76, 22, 7, '#FFFFFF', .9)}${sparkle(22, 76, 5, '#CFEBFF', .9)}`);

const blade = () => {
  const sword = flip => `<g transform="translate(${C} ${C}) scale(${flip} 1) rotate(45)">
    <path d="M-4 -40 L0 -46 L4 -40 L4 14 L-4 14Z" fill="url(#steel)" stroke="#4A5068" stroke-width="2.4" stroke-linejoin="round"/>
    <path d="M-1 -38 L-1 12" stroke="#FFFFFF" stroke-opacity=".8" stroke-width="1.6"/>
    <rect x="-13" y="14" width="26" height="6" rx="3" fill="url(#gold)" stroke="#8A5A0E" stroke-width="2"/>
    <rect x="-3.5" y="20" width="7" height="16" rx="2" fill="#7A3B4E" stroke="#4A1F2C" stroke-width="2"/>
    <circle cx="0" cy="39" r="4.5" fill="url(#gold)" stroke="#8A5A0E" stroke-width="2"/></g>`;
  return base(lin('steel', [[0, '#FFFFFF'], [1, '#B4BBD0']], 0, 0, 1, 0) + lin('gold', [[0, '#FFE9A3'], [1, '#E0A12E']]), sword(1) + sword(-1));
};

const balance = () => base(lin('lt', [[0, '#FFFFFF'], [1, '#D9D4EA']]) + lin('dk', [[0, '#4A3F6B'], [1, '#221B38']]),
  `<circle cx="${C}" cy="${C}" r="39" fill="url(#lt)"/>
   <path d="M48 9 A39 39 0 0 1 48 87 A19.5 19.5 0 0 1 48 48 A19.5 19.5 0 0 0 48 9Z" fill="url(#dk)"/>
   <circle cx="48" cy="28.5" r="6.5" fill="url(#dk)"/><circle cx="48" cy="67.5" r="6.5" fill="#FFFFFF"/>
   <circle cx="${C}" cy="${C}" r="39" fill="none" stroke="#2B2344" stroke-width="3.5"/>${gloss('M20 32 A32 32 0 0 1 36 16', .7, 3.5)}`);

const crown = () => base(lin('g', [[0, '#FFF3B0'], [.5, '#FFCB52'], [1, '#E0921F']]) + rad('ruby', [[0, '#FF9AAE'], [1, '#D61F45']], .35, .3, .8),
  `<path d="M12 70 L8 28 L30 46 L48 16 L66 46 L88 28 L84 70Z" fill="url(#g)" stroke="#8A5A0E" stroke-width="3.5" stroke-linejoin="round"/>
   <rect x="12" y="66" width="72" height="14" rx="5" fill="url(#g)" stroke="#8A5A0E" stroke-width="3.5"/>
   <circle cx="8" cy="26" r="5" fill="#FFF3B0" stroke="#8A5A0E" stroke-width="2.4"/><circle cx="88" cy="26" r="5" fill="#FFF3B0" stroke="#8A5A0E" stroke-width="2.4"/><circle cx="48" cy="14" r="6" fill="#FFF3B0" stroke="#8A5A0E" stroke-width="2.4"/>
   <circle cx="48" cy="73" r="5.5" fill="url(#ruby)" stroke="#7A0F26" stroke-width="2"/><circle cx="28" cy="73" r="3.5" fill="#6FC3FF" stroke="#1F4FB8" stroke-width="1.6"/><circle cx="68" cy="73" r="3.5" fill="#6FC3FF" stroke="#1F4FB8" stroke-width="1.6"/>
   ${gloss('M20 42 L26 62', .6, 3)}${sparkle(72, 14, 7, '#FFFFFF', .95)}`);

const finisher = () => base(lin('g', [[0, '#B8F5D6'], [1, '#2FB57C']]),
  `<circle cx="${C}" cy="${C}" r="39" fill="url(#g)" stroke="#11694A" stroke-width="3.5"/>
   <path d="M28 49 L42 63 L69 34" fill="none" stroke="#0E5A3F" stroke-width="13" stroke-linecap="round" stroke-linejoin="round"/>
   <path d="M28 49 L42 63 L69 34" fill="none" stroke="#FFFFFF" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>
   ${gloss('M18 36 A32 32 0 0 1 36 17', .65, 3.5)}`);

const critic = () => base(lin('g', [[0, '#FFE9A3'], [1, '#F0A32B']]) + lin('wood', [[0, '#FFD98A'], [1, '#E8A04A']], 0, 0, 1, 0),
  `<g transform="rotate(45 ${C} ${C})">
    <rect x="38" y="2" width="20" height="14" rx="5" fill="#FF8FA8" stroke="#A8344F" stroke-width="3"/>
    <rect x="38" y="15" width="20" height="8" fill="#D9DCE8" stroke="#5A6078" stroke-width="3"/>
    <rect x="38" y="22" width="20" height="44" fill="url(#g)" stroke="#8A5A0E" stroke-width="3"/>
    <path d="M38 66 L58 66 L48 90Z" fill="url(#wood)" stroke="#8A5A0E" stroke-width="3" stroke-linejoin="round"/>
    <path d="M44 80 L52 80 L48 90Z" fill="#3A2A3A"/>
    <path d="M43 26 L43 62" stroke="#FFFFFF" stroke-opacity=".7" stroke-width="3" stroke-linecap="round"/></g>`);

const heart = () => base(rad('g', [[0, '#FFC2D6'], [.55, '#FF6F9C'], [1, '#D92B63']], .35, .3, .9),
  `<path d="${heartPath(C, 46, 34)}" fill="url(#g)" stroke="#8A1540" stroke-width="3.5" stroke-linejoin="round"/>
   ${gloss('M24 34 C24 26 30 21 36 22', .75)}${sparkle(74, 22, 7, '#FFFFFF', .95)}`);

const pumpkin = () => base(rad('g', [[0, '#FFC069'], [.6, '#FF8A1F'], [1, '#C7540A']], .4, .35, .8),
  `<path d="M46 22 C44 12 50 6 58 6 L60 12 C54 12 52 16 52 22Z" fill="#6FBF4A" stroke="#2F6A1E" stroke-width="2.6" stroke-linejoin="round"/>
   <ellipse cx="26" cy="54" rx="18" ry="30" fill="url(#g)" stroke="#8A3A06" stroke-width="3"/><ellipse cx="70" cy="54" rx="18" ry="30" fill="url(#g)" stroke="#8A3A06" stroke-width="3"/>
   <ellipse cx="${C}" cy="54" rx="22" ry="33" fill="url(#g)" stroke="#8A3A06" stroke-width="3"/>
   <path d="M30 44 L42 48 L32 54Z M66 44 L54 48 L64 54Z" fill="#3A1704"/><path d="M28 64 L36 68 L42 63 L48 69 L54 63 L60 68 L68 64 C62 78 34 78 28 64Z" fill="#3A1704"/>
   ${gloss('M16 40 C17 32 21 27 26 26', .55, 3.5)}`);

const snow = () => {
  const arm = i => `<g transform="rotate(${i * 60} ${C} ${C})"><path d="M48 48 L48 8 M48 20 L38 11 M48 20 L58 11 M48 32 L40 25 M48 32 L56 25" fill="none" stroke-linecap="round" stroke-linejoin="round"/></g>`;
  const arms = [0, 1, 2, 3, 4, 5].map(arm).join('');
  return base(lin('g', [[0, '#FFFFFF'], [1, '#8FD3FF']]),
    `<g stroke="#2E6FA8" stroke-width="10">${arms}</g><g stroke="url(#g)" stroke-width="5.5">${arms}</g>
     <circle cx="${C}" cy="${C}" r="6" fill="#FFFFFF" stroke="#2E6FA8" stroke-width="2.4"/>`);
};

// Значок администрации: строгий щит с полумесяцем, лунное серебро без блёсток.
const admin = () => base(lin('g', [[0, '#F2F5FA'], [1, '#9AA5B8']]) + lin('in', [[0, '#2A2E3A'], [1, '#171922']]),
  `<path d="M48 6 L84 18 L84 46 C84 68 68 82 48 90 C28 82 12 68 12 46 L12 18Z" fill="url(#g)" stroke="#4A5266" stroke-width="3.5" stroke-linejoin="round"/>
   <path d="M48 16 L75 25 L75 46 C75 62 63 73 48 80 C33 73 21 62 21 46 L21 25Z" fill="url(#in)"/>
   <path d="M54 30 A18 18 0 1 0 66 58 A14 14 0 1 1 54 30Z" fill="#DCE3F0"/>`);

const one = (name, draw) => ({ name, w: S, h: S, frames: 1, draw });
export default [
  one('bd_moon', moon('#E4DBFF', '#9F8BF5', '#4B3A9A', sparkle(72, 30, 7, '#FFFFFF', .95))), one('bd_star', star), one('bd_sakura', sakura), one('bd_spark', spark),
  one('bd_blade', blade), one('bd_balance', balance), one('bd_crown', crown), one('bd_finisher', finisher), one('bd_critic', critic),
  one('bd_heart', heart), one('bd_pumpkin', pumpkin), one('bd_snow', snow), one('bd_admin', admin),
];
