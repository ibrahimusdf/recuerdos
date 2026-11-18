// docs/repro-v3912.mjs — LA CARRERA DE OPCIONES (v7.39.0)
//
// Simula el logcat del usuario (2026-11-18 01:09:19 → 01:11:02, live_tv_aljeriaelheddaf):
// cuatro opciones, la primera un host muerto a nivel TCP (4 × 15 s), la proxada [0] que
// contesta en 1 s. Compara la espera EN SERIE (lo que hacía la 7.38.11) con la CARRERA.
//
//   node docs/repro-v3912.mjs
const TOPE_MS = 15000, MAX_A_LA_VEZ = 8;
let ok = 0, ko = 0;
const comprobar = (nombre, cond, detalle = '') => {
  if (cond) { ok++; console.log(`  ✓ ${nombre}${detalle ? ' · ' + detalle : ''}`); }
  else { ko++; console.log(`  ✗ ${nombre}${detalle ? ' · ' + detalle : ''}`); }
};

// ── reglas puras (espejo de CarreraDeOpciones.java) ──────────────────────────
const esNoRotundo = h => h === 403 || h === 404 || h === 410;
const esDashCifrado = (u, http) => {
  if (!u) return false; const l = u.toLowerCase();
  if (!l.includes('.mpd')) return false;
  if (http === 299) return true;
  if (l.includes('starzplay') || l.includes('intigral')) return true;
  return l.includes('cenc') || l.includes('_enc') || l.includes('_drm')
    || l.includes('/dash/enc/') || l.includes('dash_enc');
};
const esHostCadena = u => { if (!u) return false; const l = u.toLowerCase();
  if (l.includes('dramalive')) return !/^https?:\/\/[^/]+\/(p|ps)\//.test(l);
  const f = l.includes('/hd/') || l.includes('/fk/') || l.includes('/sd/') || l.includes('/pages/') || l.includes('/mirsp/');
  return f && l.includes('index.html'); };
const esPortal = u => { if (!u) return false; const l = u.toLowerCase();
  if (esHostCadena(l)) return false;
  return l.includes('youtube.com') || l.includes('youtu.be') || l.includes('vimeo.com')
    || l.includes('dailymotion.com') || l.includes('twitch.tv') || l.includes('facebook.com/watch')
    || l.includes('shoof.') || l.includes('alkass.net/live') || l.includes('aflam4you') || l.includes('dlive.sx/cast'); };
const esAviso = c => { if (!c) return false; const l = c.toLowerCase();
  return l.includes('отсутствует подписка') || l.includes('no subscription')
    || l.includes('subscription required') || l.includes('channel is offline') || l.includes('/error/'); };
const pareceManifiesto = c => {
  if (!c) return false; let s = c.trim(); if (s.startsWith('\uFEFF')) s = s.slice(1).trim();
  if (!s.startsWith('#EXTM3U')) return false; return !esAviso(s);
};
const esCandidata = u => { const l = (u || '').toLowerCase();
  if (!l.startsWith('http')) return false;              // marcadores .LS. y demás
  if (esPortal(u)) return false; if (esDashCifrado(u, 0)) return false; return true; };
const candidatas = plan => {
  const out = [], vistos = new Set();
  for (const u of plan) { if (!esCandidata(u)) continue; if (vistos.has(u)) continue;
    vistos.add(u); out.push(u); if (out.length >= MAX_A_LA_VEZ) break; }
  return out; };

console.log('\n1) qué cuenta como «responde»');
comprobar('un #EXTM3U es manifiesto', pareceManifiesto('#EXTM3U\n#EXT-X-VERSION:3\n'));
comprobar('un HTML no', !pareceManifiesto('<!DOCTYPE html><html></html>'));
comprobar('la tarjeta rusa no gana aunque traiga #EXTM3U',
  !pareceManifiesto('#EXTM3U\n#EXTINF:10,\n/error/otsutstvuet-podpiska.ts\n'));

console.log('\n2) qué descarta (solo el NO rotundo)');
comprobar('403/404/410 descartan', [403, 404, 410].every(esNoRotundo));
comprobar('timeout (0) NO descarta — regla de la 7.38.11', !esNoRotundo(0));
comprobar('500 NO descarta', !esNoRotundo(500));
comprobar('DASH cifrado fuera', esDashCifrado('https://x/cenc.mpd', 200)
  && esDashCifrado('https://x/starzplay_dash_enc/m.mpd', 200)
  && !esDashCifrado('https://x/limpio/manifest.mpd', 200));
comprobar('YouTube fuera, host de la cadena dentro',
  !esCandidata('https://www.youtube.com/@beINSPORTS/live')
  && esCandidata('https://max10.maxwordsandroidios.shop/pages/abc/index.html'));

console.log('\n3) el caso del logcat, entero');
const SERVIDOR = 'https://live.eighty-eight.tv:7770/elheddaf1/index.m3u8';
const API = n => `https://dramalive-api-08th.onrender.com/p/232425/live_tv_aljeriaelheddaf/${n}.m3u8`;
const plan = [SERVIDOR, API(0), API(1), API(2)];
const cands = candidatas(plan);
comprobar('las 4 entran (ninguna se descarta de antemano)', cands.length === 4, `${cands.length}`);
comprobar('el orden del plan no se toca', cands[0] === SERVIDOR);
comprobar('sin duplicados', candidatas([API(0), API(0), API(1)]).length === 2);
comprobar('tope de 8', candidatas(Array.from({ length: 20 }, (_, i) => `https://x/c${i}/i.m3u8`)).length === 8);

// ── la carrera de verdad, con las latencias medidas en el logcat ─────────────
const sondeo = (url) => new Promise(res => {
  if (url === SERVIDOR) return setTimeout(() => res({ url, http: 0, motivo: 'timeout' }), 15000);
  const n = Number(url.slice(-6, -5));
  const ms = 1000 + n * 150;                       // 1,0 s · 1,15 s · 1,3 s
  setTimeout(() => res({ url, http: 200, motivo: 'manifiesto #EXTM3U', ms }), ms);
});

// Lo que hacía la 7.38.11 NO era sondear: entregaba la primera opción al reproductor y
// ExoPlayer reintentaba 4 veces con su timeout de 15 s antes de darse por vencido.
// Eso es literalmente lo del logcat: 01:09:19 -> 01:10:22 = 4 × 15 s por el host muerto.
const REINTENTOS_EXO = 4, TIMEOUT_EXO = 15000;
async function enSerie(lista) {
  const t0 = Date.now();
  for (const u of lista) {
    const r = await sondeoDirecto(u);
    if (r.http === 200) return { r, ms: Date.now() - t0 };
    // host muerto: el reproductor insiste 4 veces antes de pasar al siguiente
    for (let i = 0; i < REINTENTOS_EXO; i++) {
      await new Promise(res => setTimeout(res, TIMEOUT_EXO));
    }
  }
  return { r: null, ms: Date.now() - t0 };
}
const sondeoDirecto = (url) => new Promise(res => {
  if (url === SERVIDOR) return setTimeout(() => res({ url, http: 0, motivo: 'timeout' }), 15000);
  const n = Number(url.slice(-6, -5));
  setTimeout(() => res({ url, http: 200, motivo: 'manifiesto #EXTM3U' }), 1000 + n * 150);
});
async function carrera(lista) {                  // lo que hace la 7.39.0
  const t0 = Date.now();
  const primero = await new Promise(res => {
    let quedan = lista.length, cerrada = false;
    for (const u of lista) sondeo(u).then(r => {
      if (cerrada) return;
      if (r.http === 200) { cerrada = true; res(r); }
      else if (--quedan === 0) res(null);
    });
  });
  return { r: primero, ms: Date.now() - t0 };
}

(async () => {
  console.log('\n4) serie vs carrera (latencias del logcat: 4×15 s muerto, proxada 1 s)');
  const serie = await enSerie(cands);
  const carre = await carrera(cands);
  console.log(`     en serie : ${(serie.ms / 1000).toFixed(1)} s -> ${serie.r ? serie.r.url.split('/').pop() : 'nada'}`);
  console.log(`     carrera  : ${(carre.ms / 1000).toFixed(1)} s -> ${carre.r ? carre.r.url.split('/').pop() : 'nada'}`);
  comprobar('la carrera gana la proxada [0]', carre.r && carre.r.url === API(0));
  comprobar('la carrera tarda ~1 s, no 60', carre.ms < 3000, `${carre.ms} ms`);
  comprobar('la serie se comía >60 s (el defecto del logcat)', serie.ms > 60000, `${(serie.ms / 1000).toFixed(0)} s`);
  comprobar('la carrera es >20× más rápida', serie.ms / carre.ms > 20,
    `${(serie.ms / carre.ms).toFixed(0)}×`);
  comprobar('la carrera NO espera al host muerto', carre.ms < 15000);

  console.log(`\nV3912_OK ${ok}/${ok + ko}`);
  process.exit(ko === 0 ? 0 : 1);
})();
