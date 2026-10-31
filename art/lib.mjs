// Общие помощники для рисунков украшений: каждый рисунок — функция (t) => SVG, t ∈ [0,1) — фаза цикла анимации.
export const TAU = Math.PI * 2;
export const sin = t => Math.sin(t * TAU), cos = t => Math.cos(t * TAU);
export const f = n => +n.toFixed(2);
export const defs = (id, body) => `<defs>${body}</defs>`;
export const shadow = (id, dy = 3, blur = 3, op = .45) => `<filter id="${id}" x="-50%" y="-50%" width="200%" height="200%"><feDropShadow dx="0" dy="${dy}" stdDeviation="${blur}" flood-color="#000" flood-opacity="${op}"/></filter>`;
export const glow = (id, blur = 4) => `<filter id="${id}" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="${blur}" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>`;
export const blur = (id, s) => `<filter id="${id}" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="${s}"/></filter>`;
export const lin = (id, stops, x1 = 0, y1 = 0, x2 = 0, y2 = 1) => `<linearGradient id="${id}" x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}">${stops.map(([o, c, a = 1]) => `<stop offset="${o}" stop-color="${c}" stop-opacity="${a}"/>`).join('')}</linearGradient>`;
export const rad = (id, stops, cx = .5, cy = .5, r = .5, fx = cx, fy = cy) => `<radialGradient id="${id}" cx="${cx}" cy="${cy}" r="${r}" fx="${fx}" fy="${fy}">${stops.map(([o, c, a = 1]) => `<stop offset="${o}" stop-color="${c}" stop-opacity="${a}"/>`).join('')}</radialGradient>`;
/** Четырёхлучевая искра. */
export const sparkle = (x, y, s, fill, op = 1) => `<path d="M${f(x)} ${f(y - s)} Q${f(x + s * .16)} ${f(y - s * .16)} ${f(x + s)} ${f(y)} Q${f(x + s * .16)} ${f(y + s * .16)} ${f(x)} ${f(y + s)} Q${f(x - s * .16)} ${f(y + s * .16)} ${f(x - s)} ${f(y)} Q${f(x - s * .16)} ${f(y - s * .16)} ${f(x)} ${f(y - s)}Z" fill="${fill}" opacity="${f(op)}"/>`;
export const star5 = (x, y, r, inner = .45) => { let d = ''; for (let i = 0; i < 10; i++) { const a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 ? r * inner : r; d += (i ? 'L' : 'M') + f(x + Math.cos(a) * rr) + ' ' + f(y + Math.sin(a) * rr); } return d + 'Z'; };
export const heartPath = (x, y, s) => `M${f(x)} ${f(y + s * .95)} C${f(x - s * 1.5)} ${f(y - s * .05)} ${f(x - s * .95)} ${f(y - s * 1.25)} ${f(x)} ${f(y - s * .45)} C${f(x + s * .95)} ${f(y - s * 1.25)} ${f(x + s * 1.5)} ${f(y - s * .05)} ${f(x)} ${f(y + s * .95)}Z`;
export const svg = (w, h, body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${body}</svg>`;
