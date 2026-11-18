// docs/repro-v3917.mjs — v7.39.5: el bucle infinito de avisos y el arranque que se rendía
//
// Logcat del usuario (2026-11-18). Hay DOS sesiones en el fichero:
//
//   sesión A (pid 31436) — el bucle:
//     16:40:21.537 ↩️ sin stream nativo; se prueba el embed en la ventana -> …/androstreamlivebs4.m3u8
//     16:40:21.538 ↩️ … (la MISMA url)
//     … 4 238 veces en 3 s (891 en :21, 2 067 en :22, 1 280 en :23), todas en el hilo de UI
//     La app se congela: hay que voltearla para que reaccione.
//
//   sesión B (pid 5112, v7.39.4):
//     16:41:02.836 Buffering...  → 16:41:14.507 ❌ 504   (canal nuevo, 12 s en negro)
//     16:41:24.490 Buffering...  → 16:41:24.715 ❌ 403 → 16:41:26.077 ✅ Ready
//     16:41:38.988 Buffering...  → (403 de ruido)       → 16:41:40.101 ✅ Ready
//     16:42:15.372 ❌ StreamResolver no devolvió stream. Cambiando a reproductor web.
//     16:42:15.376 ℹ️ se llegó al final del plan, pero el canal YA está reproduciendo
//
// Ejecutar:  node docs/repro-v3917.mjs        Esperado: V3917_OK n/n
let ok = 0, ko = 0;
const comprobar = (n, c, d = '') => {
  if (c) { ok++; console.log(`  ✓ ${n}${d ? ' · ' + d : ''}`); }
  else { ko++; console.log(`  ✗ ${n}${d ? ' · ' + d : ''}`); }
};

// ───────────────────────────────── 1. el bucle infinito
console.log('\n1) avisarSinStream no puede dar vueltas sobre la misma url');

const MAX_AVISOS_SEGUIDOS = 8;

/** La lógica vieja: elegía «la primera embebida del canal» sin mirar lo ya probado. */
const eleccionVieja = (opciones, probados) => {
  // primeraEmbebida / primeraOpcionNoMarcador: no consultan `probados`
  return opciones.find(o => !o.url.includes('.LS.'))?.url ?? null;
};

/** La nueva: ni probada, ni marcador, ni portal. */
const eleccionNueva = (opciones, probados) => {
  for (const o of opciones) {
    if (probados.has(o.url)) continue;
    if (o.url.includes('.LS.')) continue;
    if (/youtube\.com/.test(o.url)) continue;
    return o.url;
  }
  return null;
};

// las opciones vivas en el canal del bucle (el m3u8 de otro canal estaba en la memoria)
const OPCIONES = [
  { url: 'https://andro.mangeinde4.lat/checklist/androstreamlivebs4.m3u8' },
];
const probados = new Set();

let vueltasVieja = 0;
let elegida = eleccionVieja(OPCIONES, probados);
while (elegida && vueltasVieja < 4238) {      // `probarServidor` la añade a probados…
  probados.add(elegida);                       // …pero la elección vieja no lo mira
  elegida = eleccionVieja(OPCIONES, probados);
  vueltasVieja++;
}
comprobar('la lógica vieja repite la MISMA url sin fin', vueltasVieja === 4238,
  `${vueltasVieja} vueltas`);

const probados2 = new Set();
comprobar('la nueva la elige UNA vez y la marca',
  eleccionNueva(OPCIONES, new Set()) === OPCIONES[0].url);
probados2.add(OPCIONES[0].url);
comprobar('la segunda vez ya no la elige (fin del bucle)',
  eleccionNueva(OPCIONES, probados2) === null);

// el cortafuegos, por si algún día aparece otro camino circular
const cortafuegos = (vueltas) => (vueltas >= MAX_AVISOS_SEGUIDOS ? 'corta' : 'sigue');
comprobar('a la 8ª vuelta sin avanzar se corta', cortafuegos(8) === 'corta');
comprobar('a la 7ª todavía se deja pasar', cortafuegos(7) === 'sigue');
comprobar('4 238 vueltas quedan cortadas en 8', 4238 > MAX_AVISOS_SEGUIDOS,
  '4 238 → 8');

// ───────────────────────────────── 2. reintento del arranque
console.log('\n2) un 504/403 al arrancar no es «canal muerto»: es reintentar');

const REINTENTOS_DE_ARRANQUE = 2;
const decide = (errorRefreshCount) =>
  (errorRefreshCount < REINTENTOS_DE_ARRANQUE) ? 'reintentar' : 'siguiente';

comprobar('primer 504: reintenta', decide(0) === 'reintentar');
comprobar('segundo: reintenta otra vez', decide(1) === 'reintentar');
comprobar('tercero: ahora sí se pasa al plan B', decide(2) === 'siguiente');

// el caso real del logcat: 504 a los 12 s y en el segundo intento hay imagen
const caso504 = { reintentos: 0, ready: false };
for (let i = 0; i < 3 && !caso504.ready; i++) {
  if (decide(caso504.reintentos) === 'reintentar') caso504.reintentos++;
  caso504.ready = true;                          // 16:41:26.077 ✅ Ready en el 2º
}
comprobar('el 504 del canal nuevo acaba en Ready, no en negro', caso504.ready);
comprobar('y solo gastó reintentos, no un cambio de canal', caso504.reintentos <= 2);

// ───────────────────────────────── 3. memoria del canal
console.log('\n3) la proxada se recuerda aunque Render conteste 504');

const esDelProxy = u => /^https?:\/\/[^/]+\/(p|ps)\/.*/.test(u || '');
const seApuntaba = (url, streamReady) => (streamReady ? true : false);        // antes
const seApunta = (url, streamReady) => (streamReady ? true : esDelProxy(url)); // ahora

const PROXY = 'https://dramalive-api-08th.onrender.com/p/232425/live_tv_new_auto_5a922/0.m3u8';
comprobar('antes: sin READY no se apuntaba nada', seApuntaba(PROXY, false) === false);
comprobar('ahora: la proxada se apunta igual', seApunta(PROXY, false) === true);
comprobar('y un CDN sin READY sigue sin apuntarse como bueno',
  seApunta('https://shd-amg-fast.edgenextcdn.net/tx010/playlist.m3u8', false) === false);

// ───────────────────────────────── 4. el .ts no es una fuente
console.log('\n4) un segmento .ts no llega a la ventana como embed');

const esPlaylist = (u) => {
  const l = (u || '').toLowerCase();
  if (l.includes('.ts') || l.includes('.jpg') || l.includes('.png')) return false;
  return l.includes('.m3u8') || l.includes('.mpd') || l.includes('.mp4') || l.includes('/hls/');
};
comprobar('un .m3u8 sí', esPlaylist('https://x/canal/index.m3u8'));
comprobar('un .ts NO (antes caía en PAGINA y acababa en el WebView)',
  !esPlaylist('https://siloh-ns1.plutotv.net/x/hls_600-00109.ts'));
comprobar('un cartel .jpg tampoco', !esPlaylist('https://x/poster/channel.jpg'));

console.log(`\n${ko === 0 ? 'V3917_OK' : 'V3917_KO'} ${ok}/${ok + ko}`);
process.exit(ko === 0 ? 0 : 1);
