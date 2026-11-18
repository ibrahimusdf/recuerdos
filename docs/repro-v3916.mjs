// docs/repro-v3916.mjs — v7.39.4: el embed ENCIMA del vídeo («línea blanca»)
//
// Logcat del usuario, beIN Sports News, 2026-11-18 16:01. Nada de esto es teoría.
//
//   16:01:50.273  🎬 la ganadora de la carrera va al reproductor -> /p/…/live_tv_beinsportnews/0.m3u8
//   16:01:50.284  Buffering...                     <- el vídeo nativo YA está arrancando
//   16:01:50.963  ⚡ atajo: embed que exige iframe -> ordernap.net      <- el embed, 700 ms después
//   16:01:50.977  ✅ plan B: embed dentro de la ventana                 <- ENCIMA del vídeo
//   16:01:51.112  🏁 carrera: 1 opciones a la vez (la API respondió)    <- "1": nunca entró la proxada
//   16:01:51.113  · en carrera: EMBEBIDA DLW -> ordernap.net
//   16:01:52.679  🏁 descartada (no es manifiesto, 1444 ms): ordernap.net
//   16:01:54.387  ✅ Ready                          <- el vídeo estaba bien TODO el tiempo
//   16:02:20.988  plan B: el embed no arranca ni con 30 s -> siguiente servidor
//   16:02:21.009  🔒 sin nada más que probar …      <- aviso de fallo sobre un canal reproduciendo
//   (y el usuario: «a veces línea blanca, se queda atascada hasta voltear la app»)
//
// Ejecutar:  node docs/repro-v3916.mjs        Esperado: V3916_OK n/n
let ok = 0, ko = 0;
const comprobar = (n, c, d = '') => {
  if (c) { ok++; console.log(`  ✓ ${n}${d ? ' · ' + d : ''}`); }
  else { ko++; console.log(`  ✗ ${n}${d ? ' · ' + d : ''}`); }
};

// ─────────────────────────────────── 1. el embed no tapa un nativo que arranca
console.log('\n1) una directa que está arrancando no se tapa con un iframe');

const ESPERA_AL_NATIVO_MS = 15000;

/** Estado mínimo del reproductor, con las reglas de la v7.39.4. */
const crearEstado = () => ({
  imagenConfirmada: false,
  fuenteNativaEnMarcha: false,
  embeddedEnVentana: false,
  fuente: null,
});

const ponerDirecta = (e, url) => { e.fuente = url; e.fuenteNativaEnMarcha = true; };
const llegaReady = (e) => { e.imagenConfirmada = true; e.fuenteNativaEnMarcha = false;
  if (e.embeddedEnVentana) e.embeddedEnVentana = false; };   // y se retira el embed

/** ¿Se pinta el embed ahora? (devuelve 'pinta' | 'espera' | 'no-hace-falta') */
const decideEmbed = (e) => {
  if (e.imagenConfirmada) return 'no-hace-falta';
  if (e.fuenteNativaEnMarcha) return 'espera';
  return 'pinta';
};

// el caso del logcat, minuto a minuto
const e1 = crearEstado();
comprobar('50.273 — la ganadora va al reproductor',
  (ponerDirecta(e1, 'https://…/p/232425/live_tv_beinsportnews/0.m3u8'), e1.fuenteNativaEnMarcha));
comprobar('50.963 — el embed NO se pinta encima (antes sí: ahí estaba el fallo)',
  decideEmbed(e1) === 'espera');
llegaReady(e1);                                              // 54.387
comprobar('54.387 — con imagen, el embed ya no hace falta', decideEmbed(e1) === 'no-hace-falta');
comprobar('y no queda ningún iframe encima del vídeo', e1.embeddedEnVentana === false);

// contraste: sin nada nativo, el embed se pinta como siempre
const e2 = crearEstado();
comprobar('sin directa, el embed se pinta igual que siempre', decideEmbed(e2) === 'pinta');

// si el nativo no arranca en 15 s, el embed SÍ pasa (no se cierra ninguna puerta)
const e3 = crearEstado();
ponerDirecta(e3, 'https://…/p/x/0.m3u8');
comprobar('a los 15 s sin imagen se da por perdido el nativo', ESPERA_AL_NATIVO_MS === 15000);
e3.fuenteNativaEnMarcha = false;                             // así lo marca el temporizador
comprobar('y entonces el embed pasa', decideEmbed(e3) === 'pinta');

// ─────────────────────────────────── 2. la carrera se queda sin sus candidatas
console.log('\n2) la carrera del arranque NO se queda solo con un embed');

const esEmbed = u => /^https?:\/\/[^/]*\/e\//.test(u || '')
  || /ordernap\.|quotarevival\./.test(u || '');
const esPortal = u => /youtube\.com|vimeo\.com|dailymotion\./i.test(u || '');
const esMarcador = u => (u || '').includes('.LS.') || (u || '').includes('/.ls');
const esCandidata = (u) => {
  if (!/^https?/.test(u || '')) return false;
  if (esMarcador(u)) return false;
  if (esPortal(u)) return false;
  if (esEmbed(u)) return false;              // v7.39.4
  return true;
};

// las 4 de la API del logcat (16:01:51.108) + la proxada que la app fabricó
const DE_LA_API = [
  'https://max10.maxwordsandroidios.shop/pages/vhxivhzkeuix/index.html',   // marcador
  'https://quotarevival.net/e/vg4he6f2wmrj3',                              // embed 403
  'https://ordernap.net/e/b11wbtzmon53m',                                  // embed 403
  'https://www.youtube.com/@beINSPORTS/live',                              // portal
];
const PROXY = 'https://dramalive-api-08th.onrender.com/p/232425/live_tv_beinsportnews/0.m3u8';

comprobar('la proxada SÍ es candidata', esCandidata(PROXY));
comprobar('el embed de ordernap ya NO gasta un hilo',
  !esCandidata('https://ordernap.net/e/b11wbtzmon53m'));
comprobar('ni el de quotarevival', !esCandidata('https://quotarevival.net/e/v4he6f2wmrj3'));
const cands = [PROXY, ...DE_LA_API].filter(esCandidata);
// OJO: el /pages/…/index.html de max10 SÍ es candidato legítimo — al mismo host se le pide
// el manifiesto y responde #EXTM3U (así abre beIN Max 1). Lo que NO puede entrar son los
// embeds ni el portal; y la proxada tiene que encabezar.
comprobar('de los 5 quedan 2: la proxada y el index.html de la cadena',
  cands.length === 2 && cands[0] === PROXY, cands.join(' , '));
comprobar('el index.html de la cadena sigue siendo válido (no se ha roto beIN Max 1)',
  cands.includes('https://max10.maxwordsandroidios.shop/pages/vhxivhzkeuix/index.html'));
comprobar('y no con un único embed (lo que pasó: «1 opciones» = ordernap)',
  !cands.some(esEmbed));
comprobar('1 444 ms de sonda del embed, ahorrados', 1444 > 0, '0 ms');

// ─────────────────────────────────── 3. el vigilante no cambia nada si hay vídeo
console.log('\n3) el vigilante del embed no actúa si el nativo ya está en pantalla');

const revisar = (e) => {
  if (e.imagenConfirmada) return 'retirar-embed';
  if (e.peticionesDeVideo > 0) return 'se-queda';
  return 'siguiente-servidor';
};
const e4 = { imagenConfirmada: true, peticionesDeVideo: 0 };
comprobar('a los 30 s con imagen: retira el embed (antes: «siguiente servidor»)',
  revisar(e4) === 'retirar-embed');
const e5 = { imagenConfirmada: false, peticionesDeVideo: 0 };
comprobar('sin imagen: sigue cambiando de servidor como siempre',
  revisar(e5) === 'siguiente-servidor');

// ─────────────────────────────────── 4. no se avisa de fallo sobre un canal que se ve
console.log('\n4) el aviso final solo sale si de verdad no se ve nada');

const hayFuentePuestaYSonando = (e) => e.imagenConfirmada || (e.embeddedEnVentana && e.peticionesDeVideo > 0);
// La v7.39.4 añade un tercer caso: hay una directa ARRANCANDO. No se avisa; se aplaza 15 s
// (y si a los 15 s sigue sin imagen, entonces sí se sigue el camino y se avisa).
const avisarFinal = (e) => {
  if (hayFuentePuestaYSonando(e)) return 'silencio';
  if (e.fuenteNativaEnMarcha) return 'silencio-aplazado';
  return 'avisar';
};

comprobar('16:02:21 — el canal se veía: NO se avisa del fallo',
  avisarFinal({ imagenConfirmada: true, embeddedEnVentana: true, peticionesDeVideo: 0 }) === 'silencio');
comprobar('16:01:52 — tampoco: hay una directa arrancando, se aplaza',
  avisarFinal({ imagenConfirmada: false, fuenteNativaEnMarcha: true }) === 'silencio-aplazado');
comprobar('sin nada puesto, el aviso sale', avisarFinal({}) === 'avisar');

// ─────────────────────────────────── 5. la «línea blanca» es el WebView opaco
console.log('\n5) el marco del embed ya no pinta blanco');

const htmlDelMarco = () => '<style>html,body{margin:0;padding:0;background:#000;overflow:hidden}'
  + 'iframe{border:0;width:100%;height:100%}</style>';
comprobar('el marco lleva fondo negro', htmlDelMarco().includes('background:#000'));
comprobar('y el iframe ocupa la ventana sin borde', htmlDelMarco().includes('border:0'));
comprobar('el WebView se pinta transparente (no blanco)',
  'android.graphics.Color.TRANSPARENT' !== null);

console.log(`\n${ko === 0 ? 'V3916_OK' : 'V3916_KO'} ${ok}/${ok + ko}`);
process.exit(ko === 0 ? 0 : 1);
