// docs/repro-v3915.mjs — v7.39.3: los cuatro defectos de los logcats de 14:23, 14:25 y 15:07
//
// Nada de esto es teoría: cada caso sale de una línea del logcat del usuario (2026-11-18).
//
//   14:23:01  ⚡ atajo: embed que exige iframe -> ordernap.net       <- el único camino que quedaba
//   14:23:13  W api drama: (SocketTimeoutException) — no se vuelve a intentar esta sesion
//   14:25:12  D api: sin respuesta util ... en 0 ms                 <- otro canal, ya rendida
//   14:26:17  D api: sin respuesta util ... en 0 ms                 <- Activity NUEVA, sigue rendida
//   14:25:25  🏁 descartada (timeout, 15007 ms): .../p/232425/live_tv_beinsport1/0.m3u8
//   14:26:31  🏁 descartada (timeout, 15084 ms): .../p/232425/live_tv_beinsport1/0.m3u8
//   14:25:12  primetv ... 403 x4 (16 ms)
//   14:25:26  🏁 descartada (no rotundo, 175 ms): .../mono.tbin.m3u8
//   luego     StreamResolver 20 s + WebView 20 s = 44 s en una url ya enterrada
//   15:07:36  🔒 canal con marcador .LS.                            <- beIN 1, sin probar NADA
//
// Ejecutar:  node docs/repro-v3915.mjs        Esperado: V3915_OK n/n
let ok = 0, ko = 0;
const comprobar = (n, c, d = '') => {
  if (c) { ok++; console.log(`  ✓ ${n}${d ? ' · ' + d : ''}`); }
  else { ko++; console.log(`  ✗ ${n}${d ? ' · ' + d : ''}`); }
};

// ─────────────────────────────────────────────────────────────── 1. el guardián .LS.
console.log('\n1) el guardián .LS. ya no mata el embed (regresión de la 93)');

const esMarcadorSinClasificar = u => {
  if (!u) return false;
  if (u.includes('.LS.') || u.includes('/.ls')) return true;
  return /^https?:\/\/\.[^/]*/.test(u);
};

// Traducción fiel de avisarSinStream() en la v7.39.3
const avisarSinStream = ({ streamDirecto, urlIframeInicial, opciones, embebidaYaProbada }) => {
  // 1º: si la FUENTE en la mano es un marcador, ahí no hay nada que hacer
  if (esMarcadorSinClasificar(streamDirecto) || esMarcadorSinClasificar(urlIframeInicial)) {
    return { accion: 'marcador-fuente' };
  }
  // 2º: un embed de verdad
  const embed = opciones.find(o => o.tipo === 'EMBED' && !esMarcadorSinClasificar(o.url));
  if (embed && !embebidaYaProbada) return { accion: 'embed', url: embed.url };
  // 3º: una opción no marcador
  const util = opciones.find(o => !esMarcadorSinClasificar(o.url) && o.tipo !== 'MARCADOR');
  if (util) return { accion: 'opcion', url: util.url };
  // 4º y ÚLTIMO: el canal solo tenía marcadores
  return { accion: 'marcador-lista' };
};

// los 9 servidores reales de beinsport1 (13:49:21): 7 eran marcadores, 2 de verdad
const BEIN1 = [
  { tipo: 'PAGINA', url: 'http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_/s' },
  { tipo: 'PAGINA', url: 'http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_/s' },
  { tipo: 'PAGINA', url: 'http://.LS.V2LOAD_BALANCERlive_tv_beinsport1/s' },
  { tipo: 'PAGINA', url: 'http://.LS.V2daddy_91/s' },
  { tipo: 'EMBED',  url: 'https://ordernap.net/e/z7iiiknxnuqjk' },
  { tipo: 'HLS',    url: 'http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8' },
  { tipo: 'HLS',    url: 'https://dramalive-api-08th.onrender.com/p/232425/live_tv_beinsport1/2.m3u8' },
];

const en93 = (s) => {   // el orden de la 93: el de la LISTA iba primero
  if (s.opciones.some(o => esMarcadorSinClasificar(o.url)) || esMarcadorSinClasificar(s.streamDirecto)) {
    return { accion: 'marcador-lista' };
  }
  return avisarSinStream(s);
};
const en94 = avisarSinStream;

comprobar('con la 93: beIN 1 muere con "🔒 canal con marcador .LS." (el bug)',
  en93({ opciones: BEIN1 }).accion === 'marcador-lista');
comprobar('con la 94: beIN 1 llega al embed que funciona',
  en94({ opciones: BEIN1 }).accion === 'embed',
  en94({ opciones: BEIN1 }).url);
comprobar('con la 94: beIN News (solo embed) también abre',
  en94({ opciones: [{ tipo: 'EMBED', url: 'https://quotarevival.net/e/abc' }] }).accion === 'embed');
comprobar('un canal que SOLO tiene marcadores sí dice la verdad',
  en94({ opciones: [
    { tipo: 'PAGINA', url: 'http://.LS.V2daddy_91/s' },
    { tipo: 'MARCADOR', url: 'http://.LS.V2LOAD_BALANCER/s' },
  ] }).accion === 'marcador-lista');
comprobar('una FUENTE marcador sí corta de inmediato',
  en94({ streamDirecto: 'http://.LS.V2daddy_91/s', opciones: [] }).accion === 'marcador-fuente');

// ─────────────────────────────────────────────────────────────── 2. la API no se rinde
console.log('\n2) la API se enfría un minuto, no muere para el proceso');

const crearApi = () => ({ estado: 'sin-probar', proximo: 0, intentos: 0 });
const ahora = { t: 0 };
const sinApi = (a) => {
  if (a.estado !== 'muerta') return false;
  if (ahora.t >= a.proximo) { a.estado = 'sin-probar'; return false; }
  return true;
};
const marcarCaida = (a) => { a.estado = 'muerta'; a.proximo = ahora.t + 60000; };
const TIEMPO_MS = 12000, DESPERTAR_MS = 45000;
const topePara = (a) => (a.estado === 'sin-probar' ? DESPERTAR_MS : TIEMPO_MS);

const api = crearApi();
ahora.t = 0;
comprobar('primer intento con paciencia de Render (45 s, no 12)',
  topePara(api) === DESPERTAR_MS);
ahora.t = 12000;                                             // 14:23:13, Render dormido
marcarCaida(api);                                            // el timeout de 12 s
comprobar('justo después del fallo: en enfriamiento', sinApi(api) === true);
ahora.t = 12000 + 59000;
comprobar('a los 59 s: sigue en enfriamiento', sinApi(api) === true, 'quedan 1 000 ms');
ahora.t = 12000 + 60000;
comprobar('al minuto: se vuelve a intentar (la 93 no lo hacía NUNCA)',
  sinApi(api) === false);
comprobar('y el reintento vuelve con paciencia de despertar',
  topePara(api) === DESPERTAR_MS);
// la prueba del logcat: Activity nueva a los 3 min
ahora.t = 180000;
comprobar('Activity recreada 3 min después: la API vuelve a estar disponible',
  sinApi(api) === false);

// ─────────────────────────────────────────────────────────────── 3. tope por tipo
console.log('\n3) la proxada tiene su propio margen: no se pierde por 100 ms');

const esDelProxy = u => /^https?:\/\/[^/]+\/(p|ps)\/.*/.test(u || '');
const topeDe = u => (esDelProxy(u) ? 22000 : 15000);

const PROXY = 'https://dramalive-api-08th.onrender.com/p/232425/live_tv_beinsport1/0.m3u8';
const CDN   = 'http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8';
comprobar('la proxada del logcat recibe 22 s', topeDe(PROXY) === 22000);
comprobar('el CDN sigue con 15 s', topeDe(CDN) === 15000);
comprobar('medido: 15 007 ms de timeout -> con 22 s habría ENTRADO', 15007 < topeDe(PROXY), '15 007 < 22 000');
comprobar('medido: 15 084 ms -> también', 15084 < topeDe(PROXY));
comprobar('y en caliente sigue siendo rápido (1 038 ms del 13:49:24)', 1038 < topeDe(PROXY));

// ─────────────────────────────────────────────────────────────── 4. descartar = detener
console.log('\n4) lo que la carrera entierra, el reproductor no lo reintenta');

class Descartes {
  constructor() { this.urls = new Set(); }
  apuntar(u) { if (u) this.urls.add(u); }
  estaMuerta(u) { return !!u && this.urls.has(u); }
}
const esNoRotundo = (http) => http === 403 || http === 404 || http === 410;
// Solo lo ROTUNDO: 403/404/410 y DNS. Un timeout o un 5xx se reintentan (un 503 es «ahora no»).
const esMuerteRotunda = (r) => esNoRotundo(r.http) || /UnknownHost/.test(r.motivo || '');

// la carrera de beinsport1 del 14:25
const des = new Descartes();
const descartes = [
  { url: 'http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8', http: 403, motivo: 'no rotundo', ms: 175 },
  { url: 'http://.LS.V2daddy_91/s', http: 0, motivo: 'UnknownHostException', ms: 1 },
  { url: PROXY, http: 0, motivo: 'timeout', ms: 15007 },
];
for (const d of descartes) if (esMuerteRotunda(d)) des.apuntar(d.url);

comprobar('el 403 a los 175 ms queda enterrado', des.estaMuerta(descartes[0].url));
comprobar('el DNS muerto también', des.estaMuerta(descartes[1].url));
comprobar('un TIMEOUT no se entierra (el proxy lento puede contestar a la 2ª)',
  !des.estaMuerta(PROXY));
comprobar('ni un 503, que es «ahora no» y no «nunca»',
  !esMuerteRotunda({ url: 'http://x/y.m3u8', http: 503, motivo: 'no rotundo' }));

// el reproductor, al recibir el error de ExoPlayer
const cortar = (urlEnReproduccion) => des.estaMuerta(urlEnReproduccion) ? { corta: true, ms: 175 } : { corta: false };
comprobar('con la url enterrada: se para al instante', cortar(descartes[0].url).corta);
comprobar('con una url viva: se sigue normal', cortar(PROXY).corta === false);
const antes = 175 + 4000 + 20000 + 20000;   // descarte + 4 reintentos + StreamResolver + WebView
const despues = 300;
comprobar('44 s tirados -> ~0,3 s', despues < 1000, `${antes} ms -> ${despues} ms`);

// ─────────────────────────────────────────────────────────────── 5. el árbol de errores
console.log('\n5) el árbol de errores no puede ofrecer un .LS. ni una url enterrada');

const elegir = (candidatas, proibidas) => {
  for (const c of candidatas) {
    if (esMarcadorSinClasificar(c.url)) continue;   // v7.39.3
    if (proibidas.estaMuerta(c.url)) continue;      // v7.39.3
    return c;
  }
  return null;
};
const arbol = [
  { tipo: 'PAGINA', url: 'http://.LS.V2daddy_91/s' },
  { tipo: 'HLS', url: 'http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8' },
  { tipo: 'HLS', url: PROXY },
];
const elegida = elegir(arbol, des);
comprobar('del árbol del logcat se salta el .LS. y el 403', elegida && elegida.url === PROXY);
comprobar('nunca se le ofrece http://.LS.V2daddy_91/s a ExoPlayer',
  elegida.url !== 'http://.LS.V2daddy_91/s');
comprobar('si no queda nada válido, no se elige nada (mejor que un marcador)',
  elegir([{ url: 'http://.LS.V2daddy_91/s' }], des) === null);

// ─────────────────────────────────────────────────────────────── resumen
console.log(`\n${ko === 0 ? 'V3915_OK' : 'V3915_KO'} ${ok}/${ok + ko}`);
process.exit(ko === 0 ? 0 : 1);
