#!/usr/bin/env node
/**
 * docs/repro-planb.mjs — v7.38.4
 *
 * Reproduce en node las tres decisiones del plan B de canales de Drama, con el
 * backup REAL de beIN 1. Sirve para dos cosas:
 *
 *   1. Demostrar en ROJO el bug de la 7.38.3 (el `return null` que abortaba la
 *      lista de servidores) sin necesidad de un compilador de Java.
 *   2. Guardar la lógica ya corregida contra la misma entrada, para que el
 *      arreglo quede atado a una medición y no a una promesa.
 *
 * El espejo Java de este fichero es app/src/test/java/dza/fut/paipo/PlanBTest.java.
 * Las dos implementaciones se escriben a mano (no hay transpilador entre Java y
 * node): si alguien cambia `siguiente()` en DramaResolucion.java, tiene que
 * cambiar aquí y el repro se pone rojo.
 *
 *   node docs/repro-planb.mjs
 *
 * Salida esperada:  REPRO_OK · ROJO3873_OK · TODOS_OK · EMBED_OK · BACKUP_OK
 */

const EMBED_BEIN1 = 'https://ordernap.net/e/z7iiiknxnuqjk';
const EMBED_MUERTO = 'https://quotarevival.net/e/yxtmusy4xj8tj';

const URL_BEIN1 =
  'http://.LS.V2live_tv_custom_handler_live_tv_mainevent_description_DL_/s';

const BACKUP_BEIN1 =
  'http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_/s -- redirect' +
  ' -;- http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_/s -- redirect' +
  ' -;- http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_4K_/s -- redirect' +
  ' -;- {"url":"https:\\/\\/ordernap.net\\/e\\/z7iiiknxnuqjk",' +
  '"agent":"Mozilla\\/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit\\/537.36 ' +
  '(KHTML, like Gecko) Chrome\\/156.0.0.0 Safari\\/537.36",' +
  '"iframe":"https:\\/\\/ordernap.net\\/e\\/z7iiiknxnuqjk",' +
  '"headers":{"User-Agent":"Mozilla\\/5.0 (Macintosh; Intel Mac OS X 10_15_7) ' +
  'AppleWebKit\\/537.36 (KHTML, like Gecko) Chrome\\/156.0.0.0 Safari\\/537.36"},' +
  '"description":"DLW","data":" "} -- double_redirect' +
  ' -;- http://.LS.V2LOAD_BALANCERlive_tv_beinsport1/s -- redirect' +
  ' -;- .LS.V2redirectV2_koora10_064762e6357527bc79dfba693c3490df_1_serv:GeneralCrawler; -- redirect' +
  ' -;- .LS.V2redirectV2_livekora_064762e6357527bc79dfba693c3490df_1_serv:GeneralCrawler; -- redirect' +
  ' -;- http://.LS.V2daddy_91/s -- redirect' +
  ' -;- {"url":"https:\\/\\/quotarevival.net\\/e\\/yxtmusy4xj8tj",' +
  '"agent":"Mozilla\\/5.0 (Macintosh) Chrome\\/156.0.0.0",' +
  '"headers":{"Referer":"https:\\/\\/new.aflam4you.net\\/",' +
  '"Origin":"https:\\/\\/new.aflam4you.net"},"description":"SD","data":" "}' +
  ' -- double_redirect' +
  ' -;- http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8 -- Mozilla' +
  ' -;- http://.LS.V2daddy_91/s -- redirect -;- ';

// ─────────────────────────── espejo de la lógica ───────────────────────────

/** DramaSesion.trozosDeBackup (v7.38.4) */
function trozosDeBackup(backup) {
  if (backup == null) return [];
  return backup.split(/\s*-;-\s*/).map(t => t.trim()).filter(t => t.length > 0);
}

/**
 * EL BUG. Copia literal del `siguienteOpcionUtil()` de la 7.38.3:
 * el `return null` está DENTRO del bucle y aborta la lista entera.
 */
function siguienteOpcionUtil_7383(utiles, probados, actual, enCurso) {
  for (const o of utiles) {
    if (actual != null && o.url === actual) continue;
    if (enCurso != null && o.url === enCurso) continue;
    for (const vista of probados) if (o.url === vista) return null;   // ← el bug
    return o;
  }
  return null;
}

/** DramaResolucion.siguiente (v7.38.4): salta, no aborta. */
function siguiente_7384(utiles, probados, actual, enCurso) {
  if (!utiles || utiles.length === 0) return null;
  for (const o of utiles) {
    if (o == null || o.url == null) continue;
    if (actual != null && o.url === actual) continue;
    if (enCurso != null && o.url === enCurso) continue;
    if (probados && probados.has(o.url)) continue;                    // ← el arreglo
    return o;
  }
  return null;
}

/** esEmbebida / esEmbedMuerto / clasificar (subconjunto que importa aquí). */
const esEmbedMuerto = u => String(u).toLowerCase().includes('quotarevival.');
function clasificar(u) {
  const l = String(u).toLowerCase();
  if (l.includes('.ls.')) return 'MARCADOR';
  if (!l.startsWith('http')) return 'MARCADOR';
  if (l.includes('.m3u8') || l.includes('.mpd') || l.includes('.mp4')) return 'DIRECTA';
  if (l.includes('ordernap.') || l.includes('quotarevival.') || l.includes('/e/')
      || l.includes('/embed/') || l.includes('embed.') || l.includes('player.')) return 'EMBEBIDA';
  return 'PAGINA';
}

/** DramaResolucion.utiles: sin marcadores y sin embeds muertos, embebidas primero. */
function utiles(url, backup) {
  const todas = [];
  if (url && url.trim()) todas.push({ url: url.trim(), tipo: clasificar(url.trim()) });
  for (const t of trozosDeBackup(backup)) {
    const trozo = t.startsWith('{') ? t.slice(0, t.lastIndexOf('}') + 1) : t;
    let u = trozo.split(/\s*--\s*/)[0].trim();
    if (!u) continue;
    if (u.startsWith('{')) { try { u = JSON.parse(u).url || u; } catch { /* no es json */ } }
    if (!u.startsWith('http') && !u.startsWith('.LS.')) continue;
    todas.push({ url: u, tipo: clasificar(u) });
  }
  const vistos = new Set();
  const sinDupes = todas.filter(o => (vistos.has(o.url) ? false : (vistos.add(o.url), true)));
  const orden = { EMBEBIDA: 0, DIRECTA: 1, PAGINA: 2, MARCADOR: 3 };
  sinDupes.sort((a, b) => orden[a.tipo] - orden[b.tipo]);
  return sinDupes.filter(o => o.tipo !== 'MARCADOR' && !esEmbedMuerto(o.url));
}

/** DramaSesion.primeraEmbebida versión 7.38.3 (dominio clavado) y 7.38.4. */
function primeraEmbebida_7383(opciones) {
  for (const o of opciones) {
    if (!o.u || !o.u.startsWith('http')) continue;
    if (o.u.toLowerCase().includes('ordernap.')) return o.u;
  }
  return null;
}
function primeraEmbebida_7384(opciones) {
  for (const o of opciones) {
    if (!o.u || !o.u.startsWith('http')) continue;
    if (esEmbedMuerto(o.u)) continue;
    if (clasificar(o.u) === 'EMBEBIDA') return o.u;
  }
  return null;
}

// ─────────────────────────────── las pruebas ───────────────────────────────

let fallos = 0;
function check(nombre, cond, detalle) {
  if (cond) { console.log(`  ✓ ${nombre}`); }
  else { console.log(`  ✗ ${nombre}  ${detalle || ''}`); fallos++; }
}

const lista = utiles(URL_BEIN1, BACKUP_BEIN1);
console.log(`servidores ÚTILES de beIN 1: ${lista.length} (${lista.map(o => o.tipo).join(', ')})`);
console.log('  (los 8 marcadores .LS. del backup NO cuentan: utiles() los filtra,');
console.log('   y con 2 útiles el bug de abajo todavía NO dispara — ver T1b)');
console.log('');

// T1 — CALIBRACIÓN: con 2 servidores el bug NO dispara (el guard `enCurso` lo tapa).
// Esto es una medición incómoda y por eso va primera: el bug que buscaba no alcanza
// a un canal con dos servidores, así que su alcance real es más estrecho de lo que
// parecía. Se deja escrito para no volver a exagerarlo.
const a = lista[0].url;
const t1bViejo = siguienteOpcionUtil_7383(lista, new Set([a]), null, a);
check('T1b: con 2 útiles el código VIEJO acierta (el bug no aplica)',
      t1bViejo !== null, `devolvió ${t1bViejo && t1bViejo.url}`);

// T1c — CON TRES se dispara: embed1 y embed2 ya probados, queda la directa.
const tres = [
  { url: 'https://embed1.example/e/aaa', tipo: 'EMBEBIDA' },
  { url: 'https://embed2.example/e/bbb', tipo: 'EMBEBIDA' },
  { url: 'http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8', tipo: 'DIRECTA' },
];
const probados1 = new Set([tres[0].url, tres[1].url]);
const viejo = siguienteOpcionUtil_7383(tres, probados1, tres[1].url, null);
const nuevo = siguiente_7384(tres, probados1, tres[1].url, null);
check('ROJO3873: con 3 útiles y 2 probados el código viejo devuelve null',
      viejo === null, `devolvió ${viejo && viejo.url}`);
check('REPRO_OK: el código nuevo devuelve el tercero (la directa)',
      nuevo !== null && nuevo.url === tres[2].url, `devolvió ${nuevo && nuevo.url}`);

// T2 — se recorren TODOS y sólo al final da null
const probados2 = new Set();
const recorrido = [];
for (let i = 0; i < 50; i++) {
  const o = siguiente_7384(lista, probados2, null, null);
  if (o === null) break;
  probados2.add(o.url);
  recorrido.push(o.url);
}
check('TODOS_OK: se prueban todos, sin repetir, y al final null',
      recorrido.length === lista.length && new Set(recorrido).size === lista.length
      && siguiente_7384(lista, probados2, null, null) === null,
      `${recorrido.length}/${lista.length}`);

// T3 — embed nuevo (dominio distinto) ya no se descarta; el muerto sigue fuera
const embedNuevo = [{ n: 'SD', u: 'https://embeddesconocido.example/e/abc123' }];
check('EMBED_OK: un embed de dominio nuevo se acepta',
      primeraEmbebida_7383(embedNuevo) === null &&            // rojo en la 7.38.3
      primeraEmbebida_7384(embedNuevo) === 'https://embeddesconocido.example/e/abc123');
check('EMBED_MUERTO_OK: quotarevival sigue excluido',
      primeraEmbebida_7384([{ n: 'SD', u: EMBED_MUERTO }]) === null);
check('EMBED_BUENO_OK: ordernap sigue entrando',
      primeraEmbebida_7384([{ n: 'SD', u: EMBED_BEIN1 }]) === EMBED_BEIN1);

// T4 — el backup con espacios raros no colapsa servidores
const raro = 'http://a.example/1.m3u8 -- Mozilla   -;-\n   http://b.example/2.m3u8 -- Mozilla';
const estrictoViejo = raro.split(' -;- ').length;          // parseador de la 7.38.3
const toleranteNuevo = trozosDeBackup(raro).length;
check('BACKUP_OK: el troceado tolerante ve 2 servidores donde el viejo veía 1',
      estrictoViejo === 1 && toleranteNuevo === 2,
      `viejo=${estrictoViejo} nuevo=${toleranteNuevo}`);

console.log('');
if (fallos === 0) {
  console.log('REPRO_OK · ROJO3873_OK · TODOS_OK · EMBED_OK · BACKUP_OK');
  process.exit(0);
}
console.log(`FALLARON ${fallos} comprobaciones`);
process.exit(1);
